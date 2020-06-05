package registrywatcher;

public class Constants {
	public static final String K8S_PREFIX = "hpcd-";
	public static final String K8S_REGISTRY_PREFIX = "registry-";

	// OpenSSL Certificate Home Directory
	public static final String OPENSSL_HOME_DIR = "/openssl";

	// OpenSSL Cert File Name
	public static final String GEN_CERT_SCRIPT_FILE = "genCert.sh";
	public static final String CERT_KEY_FILE = "localhub.key";
	public static final String CERT_CRT_FILE = "localhub.crt";
	public static final String CERT_CERT_FILE = "localhub.cert";
	public static final String DOCKER_DIR = "/etc/docker";
	public static final String DOCKER_CERT_DIR = "/etc/docker/certs.d";
	
	// Docker Login Config
	public static final String DOCKER_LOGIN_HOME_DIR = "/root/.docker";
	public static final String DOCKER_CONFIG_FILE = "config.json";
	public static final String DOCKER_CONFIG_JSON_FILE = ".dockerconfigjson";
	
	// Secret Type
	public static final String K8S_SECRET_TYPE_DOCKER_CONFIG_JSON = "kubernetes.io/dockerconfigjson";

	// Event Type
	public static final String EVENT_TYPE_ADDED = "ADDED";
	public static final String EVENT_TYPE_MODIFIED = "MODIFIED";
	public static final String EVENT_TYPE_DELETED = "DELETED";
}
