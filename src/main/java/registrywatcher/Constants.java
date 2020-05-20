package registrywatcher;

public class Constants {
	public static final String WEBHOOK_NAMESPACE = "hypercloud-system";
	public static final String ISSUER = "Tmax-ProAuth-WebHook";
	public static final String ACCESS_TOKEN_SECRET_KEY = "Access-Token-Secret-Key";
	public static final String REFRESH_TOKEN_SECRET_KEY = "Refresh-Token-Secret-Key";
	
	public static final String CUSTOM_OBJECT_GROUP = "tmax.co.kr";
	public static final String CUSTOM_OBJECT_VERSION = "v1";
	public static final String CUSTOM_OBJECT_PLURAL_USER = "users";
	public static final String CUSTOM_OBJECT_PLURAL_TOKEN = "tokens";
	public static final String CUSTOM_OBJECT_PLURAL_REGISTRY = "registries";
	
//	public static final String MASTER_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJUbWF4LVByb0F1dGgtV2ViSG9vayIsImlkIjoid3ltaW4tdG1heC5jby5rciIsImV4cCI6MTU4MzEyMTQ5M30.hjvrlaLDFuSjchJKarGKbuWOuafhsuCQgBDo-pqsZvg";
	public static final int ACCESS_TOKEN_EXP_TIME = 3600; // 1 hour
	public static final int REFRESH_TOKEN_EXP_TIME = 604800; // 7 days
	
	public static final String CLAIM_USER_ID = "id";
	public static final String CLAIM_TOKEN_ID = "tokenId";
	

	public static final String K8S_PREFIX = "hpcd-";
	public static final String K8S_REGISTRY_PREFIX = "registry-";
	public static final String REGISTRY_CPU_STRING = "0.2";
	public static final String REGISTRY_MEMORY_STRING = "512Mi";

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
	
	// Shared Option
	public static final int SHARE_ONLY_THIS_DOMAIN = 0;
	public static final int SHARE_OTHER_DOMAINS = 1;
	 
	// Secret Type
	public static final String K8S_SECRET_TYPE_DOCKER_CONFIG_JSON = "kubernetes.io/dockerconfigjson";

	// Event Type
	public static final String EVENT_TYPE_ADDED = "ADDED";
	public static final String EVENT_TYPE_MODIFIED = "MODIFIED";
	public static final String EVENT_TYPE_DELETED = "DELETED";
}
