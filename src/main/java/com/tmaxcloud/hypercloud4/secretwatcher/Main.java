package com.tmaxcloud.hypercloud4.secretwatcher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.util.*;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;

public class Main {
    final static Logger logger = LoggerFactory.getLogger(Main.class);

    final static String DOCKER_CONF_BASE = "/etc/docker";
    final static String DOCKER_CERT_BASE = "/etc/docker/certs.d";

    final static String CERT_KEY_FILENAME = "localhub.key";
    final static String CERT_CRT_FILENAME = "localhub.crt";
    final static String CERT_CERT_FILENAME = "localhub.cert";

    public static void main(String[] args) throws Exception {

        createBaseDockerCertDirectory();

        logger.info("init client...");
        ApiClient apiClient = Config.defaultClient();
        OkHttpClient httpClient = apiClient.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
        apiClient.setHttpClient(httpClient);

        Configuration.setDefaultApiClient(apiClient);
        CoreV1Api coreV1Api = new CoreV1Api();

        logger.info("init informer...");
        SharedInformerFactory factory = new SharedInformerFactory();
        SharedIndexInformer<V1Secret> secretInformer =
                factory.sharedIndexInformerFor(
                        (CallGeneratorParams params) -> {
                            return coreV1Api.listSecretForAllNamespacesCall(
                                    null,
                                    null,
                                    null,
                                    "secret=cert",
                                    null,
                                    null,
                                    params.resourceVersion,
                                    null,
                                    params.timeoutSeconds,
                                    params.watch,
                                    null);
                        },
                        V1Secret.class,
                        V1SecretList.class);

        secretInformer.addEventHandler(
                new ResourceEventHandler<V1Secret>() {
                    @Override
                    public void onAdd(V1Secret secret) {
                        List<String> hosts = getHostsFrom(secret);
                        Map<String, String> certs = getCertsFrom(secret);
                        try {
                            removeDockerCertFiles(hosts);
                            createDockerCertFiles(hosts, certs);
                        } catch (IOException e) {
                            logger.error("Failed to create certs");
                            e.printStackTrace();
                        }
                        logger.info(String.format("added certs for %s", hosts));
                    }

                    @Override
                    public void onUpdate(V1Secret oldSecret, V1Secret newSecret) {
                        logger.info(String.format(
                                "%s => %s secret updated!\n",
                                oldSecret.getMetadata().getName(), newSecret.getMetadata().getName()));
                    }

                    @Override
                    public void onDelete(V1Secret secret, boolean deletedFinalStateUnknown) {
                        List<String> hosts = getHostsFrom(secret);
                        try {
                            removeDockerCertFiles(hosts);
                        } catch (IOException e) {
                            logger.error("Failed to remove certs");
                            e.printStackTrace();
                        }
                        logger.info(String.format("removed certs for %s", hosts));
                    }
                });

        logger.info("start informer...");
        factory.startAllRegisteredInformers();
    }

    private static void createBaseDockerCertDirectory() throws IOException {
        logger.info(String.format("refresh base directory for docker cert(%s)...", DOCKER_CERT_BASE));
        Path dockerBaseDir = Paths.get(DOCKER_CONF_BASE);
        Path dockerCertDir = Paths.get(DOCKER_CERT_BASE);

        if (!Files.exists(dockerBaseDir)) {
            Files.createDirectory(dockerBaseDir);
        }

        if (!Files.exists(dockerCertDir)) {
            Files.createDirectory(dockerCertDir);
            logger.trace("created: " + DOCKER_CERT_BASE);
        }
    }

    public static List<String> getHostsFrom(V1Secret secret) {
        List<String> hosts = new ArrayList<>();

        if (secret.getData().containsKey("REGISTRY_IP_PORT")) {
            hosts.add(new String(secret.getData().get("REGISTRY_IP_PORT")));
        }
        if (secret.getData().containsKey("PORT") && secret.getData().containsKey("CLUSTER_IP")) {
            hosts.add(new String(secret.getData().get("CLUSTER_IP")) + ":" + new String(secret.getData().get("PORT")));
        }
        if (secret.getData().containsKey("PORT") && secret.getData().containsKey("LB_IP")) {
            hosts.add(new String(secret.getData().get("LB_IP")) + ":" + new String(secret.getData().get("PORT")));
        }
        if (secret.getData().containsKey("PORT") && secret.getData().containsKey("DOMAIN_NAME")) {
            hosts.add(new String(secret.getData().get("DOMAIN_NAME")) + ":" + new String(secret.getData().get("PORT")));
        }

        return hosts;
    }

    public static Map<String, String> getCertsFrom(V1Secret secret) {
        Map<String, String> certs = new HashMap<>();

        certs.put(CERT_KEY_FILENAME, new String(secret.getData().get(CERT_KEY_FILENAME)));
        certs.put(CERT_CERT_FILENAME, new String(secret.getData().get(CERT_CERT_FILENAME)));
        certs.put(CERT_CRT_FILENAME, new String(secret.getData().get(CERT_CRT_FILENAME)));

        return certs;
    }

    public static void createDockerCertFiles(List<String> hosts, Map<String, String> certs) throws IOException {
        for (String host : hosts) {
            Path dirPath = Paths.get(DOCKER_CERT_BASE, host);
            if (!Files.exists(dirPath)) {
                Files.createDirectory(dirPath);
            }

            Path keyPath = Paths.get(dirPath.toString(), CERT_KEY_FILENAME);
            Path certPath = Paths.get(dirPath.toString(), CERT_CERT_FILENAME);
            Path crtPath = Paths.get(dirPath.toString(), CERT_CRT_FILENAME);

            BufferedWriter keywriter = new BufferedWriter(new FileWriter(keyPath.toString()));
            BufferedWriter certwriter = new BufferedWriter(new FileWriter(certPath.toString()));
            BufferedWriter crtwriter = new BufferedWriter(new FileWriter(crtPath.toString()));

            keywriter.write(certs.get(CERT_KEY_FILENAME));
            certwriter.write(certs.get(CERT_CERT_FILENAME));
            crtwriter.write(certs.get(CERT_CRT_FILENAME));

            keywriter.flush();
            certwriter.flush();
            crtwriter.flush();
        }
    }

    public static void removeDockerCertFiles(List<String> hosts) throws IOException {
        for (String host : hosts) {
            Path dirPath = Paths.get(DOCKER_CERT_BASE, host);
            if (!Files.exists(dirPath)) {
                continue;
            }
            File dir = dirPath.toFile();
            if (!dir.isDirectory()) {
                logger.warn(String.format("target path(%s) is not directory."), dir);
            }
            for (String fname : dir.list()) {
                new File(dir, fname).delete();
            }
            if (!dir.delete()) {
                logger.warn(String.format("failed to remove directory(%s)", dir));
            }
        }
    }
}
