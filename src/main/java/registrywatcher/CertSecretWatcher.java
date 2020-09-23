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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;

import com.google.gson.reflect.TypeToken;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.Watch;
import models.StateCheckInfo;

public class CertSecretWatcher extends Thread {
	private Watch<V1Secret> watch;
	private ExecutorService executorService;

	private static int latestResourceVersion = 0;
	private CoreV1Api api = null;
	ApiClient client;
	StateCheckInfo sci = new StateCheckInfo();
	private static Logger logger = MainWatcher.logger;

	CertSecretWatcher(ApiClient client, CoreV1Api api, int resourceVersion) throws Exception {
		this.api = api;
		this.client = client;
		try {
			this.watch = Watch.createWatch(client, api.listSecretForAllNamespacesCall(null, null, null, "secret=cert",
					null, null, null, null, Boolean.TRUE, null), new TypeToken<Watch.Response<V1Secret>>() {
			}.getType());
		} catch (ApiException e) {
			logger.info("createWatch failed: " + e.getResponseBody());
			System.exit(1);
		}
		this.executorService = Executors.newCachedThreadPool();

		latestResourceVersion = resourceVersion;
	}

	@Override
	public void run() {
		try {
			while(true) {
				sci.checkThreadState();
				logger.info("[cert-secret-watcher] run()");
				watch.forEach(response -> {
					try {
						if(Thread.interrupted()) {
							logger.info("[cert-secret-watcher] Interrupted!");
							this.watch.close(); executorService.shutdown();
						}
					} catch(Exception e) {
						StringWriter sw = new StringWriter();
						e.printStackTrace(new PrintWriter(sw));
						logger.info("[cert-secret-watcher] error: " + sw.toString());
						System.exit(1);
					}

					try {

						V1Secret secret = response.object;
						if( secret != null ) {
							logger.info(secret.toString());

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
											logger.info("write filename: " + CERT_DIR + "/" + Constants.CERT_KEY_FILE);
											try ( BufferedWriter writer = new BufferedWriter(new FileWriter(CERT_DIR + "/" + Constants.CERT_KEY_FILE)) ) {
												writer.write(new String(secretMap.get(Constants.CERT_KEY_FILE)));
											}
											logger.info("write filename: " + CERT_DIR + "/" + Constants.CERT_CERT_FILE);
											try ( BufferedWriter writer = new BufferedWriter(new FileWriter(CERT_DIR + "/" + Constants.CERT_CERT_FILE)) ) {
												writer.write(new String(secretMap.get(Constants.CERT_CERT_FILE)));
											}
											logger.info("write filename: " + CERT_DIR + "/" + Constants.CERT_CRT_FILE);
											try ( BufferedWriter writer = new BufferedWriter(new FileWriter(CERT_DIR + "/" + Constants.CERT_CRT_FILE)) ) {
												writer.write(new String(secretMap.get(Constants.CERT_CRT_FILE)));
											}

										} catch (IOException e) {
											StringWriter sw = new StringWriter();
											e.printStackTrace(new PrintWriter(sw));
											logger.info(sw.toString());
											throw e;
										}
									}


									// To delete a secret directory
									MainWatcher.gSecretMap.put(secret.getMetadata().getName(), domainList);

									logger.info("cert.d directory list after create");
									for(String key : MainWatcher.gSecretMap.keySet()) {
										List<String> list = MainWatcher.gSecretMap.get(key);

										logger.info("\t" + key + "=" + Arrays.toString(list.toArray()));
									}

									break;

								case Constants.EVENT_TYPE_DELETED :
									logger.info("Delete Cert Directory");
									try {
										List<String> delDirList = new ArrayList<>();
										delDirList = MainWatcher.gSecretMap.get(secret.getMetadata().getName());

										for( String domain: delDirList) {
											deleteDirectory( Constants.DOCKER_CERT_DIR + "/" + domain );
										}
									} catch (IOException e) {
										StringWriter sw = new StringWriter();
										e.printStackTrace(new PrintWriter(sw));
										logger.info(sw.toString());
									}

									MainWatcher.gSecretMap.remove(secret.getMetadata().getName());
									logger.info("cert.d directory list after delete");
									for(String key : MainWatcher.gSecretMap.keySet()) {
										List<String> list = MainWatcher.gSecretMap.get(key);

										logger.info("\t" + key + "=" + Arrays.toString(list.toArray()));
									}
									break;

								}
							}
						}
					} catch (Exception e) {
						logger.info("Exception: " + e.getMessage());
						StringWriter sw = new StringWriter();
						e.printStackTrace(new PrintWriter(sw));
						logger.info(sw.toString());
					}
				});
				logger.info("=============== Cert Secret 'For Each' END ===============");
				try {
					watch = Watch.createWatch(
							client, 
							api.listSecretForAllNamespacesCall(null, null, null, "secret=cert", null, null, null, null, Boolean.TRUE, null),
							new TypeToken<Watch.Response<V1Secret>>(){}.getType()
							);
				}catch(ApiException e) {
					logger.info("createWatch failed: " + e.getResponseBody());
				}
			}
		} catch(Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			logger.info("[cert-secret-watcher] error from watching. \n" + sw.toString());			
			if( e.getMessage().equals("abnormal") ) {
				logger.info("Catch abnormal conditions!! Exit process");
			}
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
			logger.info("Directory created: " + Constants.DOCKER_DIR);
		}

		Path dockerCertDir = Paths.get(Constants.DOCKER_CERT_DIR);
		if (!Files.exists(dockerCertDir)) {
			Files.createDirectory(dockerCertDir);
			logger.info("Directory created: " + Constants.DOCKER_CERT_DIR);
		}

		return dockerCertDir.toString();
	}

	private static String createDirectory(String dirPath) throws IOException {
		Path dir = Paths.get(dirPath);
		if (!Files.exists(dir)) {
			Files.createDirectory(dir);
			logger.info("Directory created: " + dirPath);
		}

		return dirPath;
	}

	private static void deleteDirectory(String dirPath) throws IOException {
		// Delete Directory
		Path path = Paths.get(dirPath);
		if (path != null && MainWatcher.deleteFile(path.toFile())) {
			logger.info("Directory deleted: " + dirPath);
		} else {
			logger.info("Directory doesn't exist: " + dirPath);
		}
	}
}
