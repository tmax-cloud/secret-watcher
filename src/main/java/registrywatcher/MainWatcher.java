package registrywatcher;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.Config;

public class MainWatcher {

	//	public static Logger logger = LoggerFactory.getLogger("RegWatcher");

	// gSecretMap: To also delete the certificate directory when the registry is cleared
	public static Map<String, List<String>> gSecretMap = new HashMap<>();	// <registryName, ipPort>

	private static ApiClient k8sClient;
	private static CoreV1Api api;
	public static Logger logger = LoggerFactory.getLogger("K8SOperator");

	public static void main(String[] args) {
		while(true) {
			CertSecretWatcher certSecretWatcher = null;

			logger.info("Secret Main Watcher Start");
			System.out.println("Secret Main Watcher Start");
			System.out.println("The log file exists in /home/tmax/secretwatcher/logs/operator.log");

			try { 
				k8sClient = Config.fromCluster();
				k8sClient.setConnectTimeout(0);
				k8sClient.setReadTimeout(0);
				k8sClient.setWriteTimeout(0);
				Configuration.setDefaultApiClient(k8sClient);

				api = new CoreV1Api();

				// Get Latest Resource Version & Create Cert Files
				V1SecretList certSecretList = null;
				try {
					certSecretList = api.listSecretForAllNamespaces(null, null, null, "secret=cert", null, null, null, null, Boolean.FALSE);
				}catch(ApiException e) {
					logger.info("call listSecretForAllNamespaces failed: " + e.getResponseBody());
					throw e;
				}
				int certSecretLatestResourceVersion = 0;
				deleteBaseDirectory();
				createBaseDirectory();
				for(V1Secret secret : certSecretList.getItems()) {
					int secretResourceVersion = Integer.parseInt(secret.getMetadata().getResourceVersion());
					certSecretLatestResourceVersion = (certSecretLatestResourceVersion >= secretResourceVersion) ? certSecretLatestResourceVersion : secretResourceVersion;

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
					gSecretMap.put(secret.getMetadata().getName(), domainList);
					logger.info("cert.d directory list after create");
					for(String key : MainWatcher.gSecretMap.keySet()) {
						List<String> list = MainWatcher.gSecretMap.get(key);

						logger.info("\t" + key + "=" + Arrays.toString(list.toArray()));
					}
				}


				// Watch Cert Secret
				logger.info("Cert Secret Watcher Run (Latest Resource Version: " + certSecretLatestResourceVersion + ")");
				certSecretWatcher = new CertSecretWatcher(k8sClient, api, certSecretLatestResourceVersion);
				certSecretWatcher.start();


				// Check Watcher Threads are Alive every 10sec
				while(true) {
					if(!certSecretWatcher.isAlive()) {
						certSecretLatestResourceVersion = CertSecretWatcher.getLatestResourceVersion();
						logger.info("Cert Secret Watcher is not Alive. Restart Cert Watcher! (Latest Resource Version: " + certSecretLatestResourceVersion + ")");
						certSecretWatcher.interrupt();
						certSecretWatcher = new CertSecretWatcher(k8sClient, api, certSecretLatestResourceVersion);
						certSecretWatcher.start();
					}

					Thread.sleep(10000);
				}
			} catch (Exception e) {
				logger.info("Main Watcher Exception: " + e.getMessage());
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.info(sw.toString());
				System.exit(1);
			}
		}
	}

	private static void deleteBaseDirectory() throws IOException {
		// Delete Image Directory
		Path path = Paths.get(Constants.DOCKER_CERT_DIR);
		if (path != null && deleteFile(path.toFile())) {
			logger.info("Directory deleted: " + Constants.DOCKER_CERT_DIR);
		} else {
			logger.info("Directory doesn't exist: " + Constants.DOCKER_CERT_DIR);
		}
	}

	private static String createBaseDirectory() throws IOException {
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


	public static boolean deleteFile(File file) throws IOException{
		if(file.isDirectory()){
			//directory is empty, then delete it
			if(file.list().length==0) {
				file.delete();

			}else{

				//list all the directory contents
				String files[] = file.list();

				for (String temp : files) {
					//construct the file structure
					File fileDelete = new File(file, temp);

					//recursive delete
					deleteFile(fileDelete);
				}

				//check the directory again, if empty then delete it
				if(file.list().length==0){
					file.delete();
				}
			}

		}else{
			//if file, then delete it
			file.delete();
		}

		return true;
	}

}
