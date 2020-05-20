package registrywatcher;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.reflect.TypeToken;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.Watch;

public class CertSecretWatcher extends Thread {
	private Watch<V1Secret> watch;
	private ExecutorService executorService;
	
//	private static Logger logger = MainWatcher.logger;
	private static int latestResourceVersion = 0;
	private CoreV1Api api = null;
	
	CertSecretWatcher(ApiClient client, CoreV1Api api, int resourceVersion) throws Exception {
		this.api = api;
		try {
			this.watch = Watch.createWatch(
					client, 
					api.listSecretForAllNamespacesCall(null, null, null, "secret=cert", null, null, null, null, Boolean.TRUE, null),
					new TypeToken<Watch.Response<V1Secret>>(){}.getType()
					);
		} catch(ApiException e) {
			System.out.println("createWatch failed: " + e.getResponseBody());
			System.exit(1);
		}
		this.executorService = Executors.newCachedThreadPool();
		
		latestResourceVersion = resourceVersion;
	}
	
	@Override
	public void run() {
		try {
			System.out.println("[cert-secret-watcher] run()");
			watch.forEach(response -> {
				try {
					if(Thread.interrupted()) {
						System.out.println("[cert-secret-watcher] Interrupted!");
						this.watch.close(); executorService.shutdown();
					}
				} catch(Exception e) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					System.out.println("[cert-secret-watcher] error: " + sw.toString());
					System.exit(1);
				}

				V1Secret secret = response.object;
				if( secret != null ) {
					System.out.println(secret.toString());

					if( Integer.parseInt(secret.getMetadata().getResourceVersion()) > latestResourceVersion ) {
						latestResourceVersion = Integer.parseInt(secret.getMetadata().getResourceVersion());	
						
						List<String> domainList = new ArrayList<>();
						Map<String, byte[]> secretMap = secret.getData();
						String port = null;
						
						if(secretMap.get("REGISTRY_IP_PORT") != null) {
							// 이전 버전 호환
							domainList.add( new String(secretMap.get("REGISTRY_IP_PORT")) );
						}
						else {
							if(secretMap.get("PORT") != null) {
								port = new String(secretMap.get("PORT"));
							}
							if(secretMap.get("CLUSTER_IP") != null) {
								domainList.add( new String(secretMap.get("CLUSTER_IP")) + ":" + port );
							}
							if(secretMap.get("LB_IP") != null) {
								domainList.add( new String(secretMap.get("LB_IP")) + ":" + port );
							}
							if(secretMap.get("DOMAIN_NAME") != null) {
								domainList.add( new String(secretMap.get("DOMAIN_NAME")) + ":" + port );
							}
						}
						
						String eventType = response.type.toString();
						
						switch(eventType) {
						case Constants.EVENT_TYPE_ADDED :
						case Constants.EVENT_TYPE_MODIFIED :
							for( String domain : domainList ) {
								final String CERT_DIR = Constants.DOCKER_CERT_DIR + "/" + domain;

								try {
									createDirectory(CERT_DIR);
									System.out.println("write filename: " + CERT_DIR + "/" + Constants.CERT_KEY_FILE);
									try ( BufferedWriter writer = new BufferedWriter(new FileWriter(CERT_DIR + "/" + Constants.CERT_KEY_FILE)) ) {
										writer.write(new String(secretMap.get(Constants.CERT_KEY_FILE)));
									}
									System.out.println("write filename: " + CERT_DIR + "/" + Constants.CERT_CERT_FILE);
									try ( BufferedWriter writer = new BufferedWriter(new FileWriter(CERT_DIR + "/" + Constants.CERT_CERT_FILE)) ) {
										writer.write(new String(secretMap.get(Constants.CERT_CERT_FILE)));
									}
									System.out.println("write filename: " + CERT_DIR + "/" + Constants.CERT_CRT_FILE);
									try ( BufferedWriter writer = new BufferedWriter(new FileWriter(CERT_DIR + "/" + Constants.CERT_CRT_FILE)) ) {
										writer.write(new String(secretMap.get(Constants.CERT_CRT_FILE)));
									}

								} catch (IOException e) {
									e.printStackTrace();
								}
							}

							// To delete a secret directory
							MainWatcher.gSecretMap.put(secret.getMetadata().getName(), domainList);
							
							break;

						case Constants.EVENT_TYPE_DELETED :
							System.out.println("Delete Cert Directory");
							try {
								List<String> delDirList = new ArrayList<>();
								delDirList = MainWatcher.gSecretMap.get(secret.getMetadata().getName());
								
								for( String domain: delDirList) {
									deleteDirectory( Constants.DOCKER_CERT_DIR + "/" + domain );
								}
							} catch (IOException e1) {
								e1.printStackTrace();
							}
							
							break;
							
						}
					}
				}
			});
		} catch(Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			System.out.println("[cert-secret-watcher] error from watching. \n" + sw.toString());			
			
			executorService.shutdown();
			System.exit(1);
		}
	}
	
	public static int getLatestResourceVersion() {
		return latestResourceVersion;
	}

	private String createBaseDirectory() throws IOException {
		Path dockerHome = Paths.get(Constants.DOCKER_DIR);
		if (!Files.exists(dockerHome)) {
			Files.createDirectory(dockerHome);
			System.out.println("Directory created: " + Constants.DOCKER_DIR);
		}
		
		Path dockerCertDir = Paths.get(Constants.DOCKER_CERT_DIR);
		if (!Files.exists(dockerCertDir)) {
			Files.createDirectory(dockerCertDir);
			System.out.println("Directory created: " + Constants.DOCKER_CERT_DIR);
		}
		
		return dockerCertDir.toString();
	}
	
	private static String createDirectory(String dirPath) throws IOException {
		Path dir = Paths.get(dirPath);
		if (!Files.exists(dir)) {
			Files.createDirectory(dir);
			System.out.println("Directory created: " + dirPath);
		}
		
		return dirPath;
	}
	
	private static void deleteDirectory(String dirPath) throws IOException {
		// Delete Directory
		Path path = Paths.get(dirPath);
		if (path != null && MainWatcher.deleteFile(path.toFile())) {
			System.out.println("Directory deleted: " + dirPath);
		} else {
			System.out.println("Directory doesn't exist: " + dirPath);
		}
	}
}
