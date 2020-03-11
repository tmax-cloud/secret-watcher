package registrywatcher;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

public class CertSecretWatcher extends Thread {
	private final Watch<V1Secret> watch;
	private ExecutorService executorService;
	
	private static Logger logger = MainWatcher.logger;
	private static int latestResourceVersion = 0;
	private CoreV1Api api = null;
	
	CertSecretWatcher(ApiClient client, CoreV1Api api, int resourceVersion) throws Exception {
		this.api = api;
		this.watch = Watch.createWatch(
				client, 
				api.listSecretForAllNamespacesCall(null, null, null, "secret=cert", null, null, null, null, null, null),
				new TypeToken<Watch.Response<V1Secret>>(){}.getType()
				);
		this.executorService = Executors.newCachedThreadPool();
		
		latestResourceVersion = resourceVersion;
	}
	
	@Override
	public void run() {
		try {
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
				}
				
				V1Secret secret = response.object;
				logger.info(secret.toString());
				if(response.object.getMetadata().getNamespace().startsWith(Constants.K8S_PREFIX)
        				&& (Integer.parseInt(secret.getMetadata().getResourceVersion()) > latestResourceVersion)) {
					
					latestResourceVersion = Integer.parseInt(secret.getMetadata().getResourceVersion());	
					
					boolean isDeleted = false;
					
					// Check if Secret Exists
					try {
						api.readNamespacedSecret(secret.getMetadata().getName(), secret.getMetadata().getNamespace(), null, null, null);
					} catch (ApiException e) {
						logger.info(e.getResponseBody());
						logger.info("ApiException Code: " + e.getCode());
						if( e.getCode() == 404 ) {
							logger.info("Delete Cert Directory");
							isDeleted = true;
							try {
								deleteDirectory(Constants.DOCKER_CERT_DIR + "/" + MainWatcher.gSecretMap.get(secret.getMetadata().getName()));
							} catch (IOException e1) {
								e1.printStackTrace();
							}
						}
					}
					
					if( !isDeleted ) {
						Map<String, byte[]> secretMap = secret.getData();
						
						String ipPort = new String(secretMap.get("REGISTRY_IP_PORT"));
						final String CERT_DIR = Constants.DOCKER_CERT_DIR + "/" + ipPort;
						
						try {
							createBaseDirectory();
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
							
							MainWatcher.gSecretMap.put(secret.getMetadata().getName(), ipPort);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					
					
					
				}
			});
		} catch(Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			logger.info("[cert-secret-watcher] error from watching. \n" + sw.toString());			
			
			executorService.shutdown();
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
		// Delete Image Directory
		Path path = Paths.get(dirPath);
		if (path != null && MainWatcher.deleteFile(path.toFile())) {
			logger.info("Directory deleted: " + dirPath);
		} else {
			logger.info("Directory doesn't exist: " + dirPath);
		}
	}
}
