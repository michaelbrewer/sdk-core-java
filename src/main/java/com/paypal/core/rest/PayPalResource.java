package com.paypal.core.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.paypal.core.APICallPreHandler;
import com.paypal.core.APICallPreHandlerFactory;
import com.paypal.core.ConfigManager;
import com.paypal.core.ConnectionManager;
import com.paypal.core.Constants;
import com.paypal.core.HttpConfiguration;
import com.paypal.core.HttpConnection;
import com.paypal.core.LoggingManager;
import com.paypal.core.SDKUtil;

/**
 * PayPalResource acts as a base class for REST enabled resources. The class
 * operates by using a {@link APICallPreHandler} as an abstraction for making
 * calls.
 */
public abstract class PayPalResource {

	/*
	 * The class uses an implementation APICallPreHandler (here
	 * RESTAPICallPreHandler)to get access to endpoint, HTTP headers, payload.
	 */
	/**
	 * SDK ID used in User-Agent HTTP header
	 */
	public static final String SDK_ID = "rest-sdk-java";

	/**
	 * SDK Version used in User-Agent HTTP header
	 */
	public static final String SDK_VERSION = "0.6.0";

	/**
	 * Map used in dynamic configuration
	 */
	private static Map<String, String> configurationMap;

	/**
	 * Configuration enabled flag
	 */
	private static boolean configInitialized = false;
	
	/**
	 * Default {@link APICallPreHandlerFactory}
	 */
	private static APICallPreHandlerFactory apiCallPreHandlerFactory = new RESTAPICallPreHandlerFactory();

	/**
	 * Last request sent to Service
	 */
	private static final ThreadLocal<String> LASTREQUEST = new ThreadLocal<String>();

	/**
	 * Last response returned form Service
	 */
	private static final ThreadLocal<String> LASTRESPONSE = new ThreadLocal<String>();

	/**
	 * Sets a custom {@link APICallPreHandlerFactory}, the new system  may also want to
	 * override the class level createAPICallPreHandler(...) method 
	 * @param preHandlerFactory the preHandlerFactory to set
	 */
	static void setPreHandlerFactory(APICallPreHandlerFactory apiCallPreHandlerFactory) {
		PayPalResource.apiCallPreHandlerFactory = apiCallPreHandlerFactory;
	}

	/**
	 * Initialize the system using a File(Properties file). The system is
	 * initialized using the given file and if the initialization succeeds the
	 * default 'sdk_config.properties' can only be loaded by calling the method
	 * initializeToDefault()
	 * 
	 * @param file
	 *            File object of a properties entity
	 * @throws PayPalRESTException
	 */
	public static void initConfig(File file) throws PayPalRESTException {
		try {
			if (!file.exists()) {
				throw new FileNotFoundException("File doesn't exist: "
						+ file.getAbsolutePath());
			}
			FileInputStream fis = new FileInputStream(file);
			initConfig(fis);
		} catch (IOException ioe) {
			LoggingManager.severe(PayPalResource.class, ioe.getMessage(), ioe);
			throw new PayPalRESTException(ioe.getMessage(), ioe);
		}

	}

	/**
	 * Initialize using Properties. The system is initialized using the given
	 * properties object and if the initialization succeeds the default
	 * 'sdk_config.properties' can only be loaded by calling the method
	 * initializeToDefault()
	 * 
	 * @param properties
	 *            Properties object
	 */
	public static void initConfig(Properties properties) {
		configurationMap = SDKUtil.constructMap(properties);
		configInitialized = true;
	}

	/**
	 * Initialize using {@link InputStream}(of a Properties file).. The system
	 * is initialized using the given {@link InputStream} and if the
	 * initialization succeeds the default 'sdk_config.properties' can only be
	 * loaded by calling the method initializeToDefault(). The system is
	 * initialized with the information after loading defaults for the
	 * parameters that are not passed as part of the configuration. For defaults
	 * see {@link ConfigManager}
	 * 
	 * @param inputStream
	 *            InputStream
	 * @throws PayPalRESTException
	 */
	public static void initConfig(InputStream inputStream)
			throws PayPalRESTException {
		try {
			Properties properties = new Properties();
			properties.load(inputStream);

			/*
			 * Create a Map instance and combine it with default values
			 */
			configurationMap = SDKUtil.constructMap(properties);
			configInitialized = true;
		} catch (IOException ioe) {
			LoggingManager.severe(PayPalResource.class, ioe.getMessage(), ioe);
			throw new PayPalRESTException(ioe.getMessage(), ioe);
		}
	}

	/**
	 * Initialize to default properties
	 * 
	 * @throws PayPalRESTException
	 */
	public static void initializeToDefault() throws PayPalRESTException {
		configurationMap = SDKUtil.combineDefaultMap(ConfigManager
				.getInstance().getConfigurationMap());
	}

	/**
	 * Returns the last request sent to the Service
	 * 
	 * @return Last request sent to the server
	 */
	public static String getLastRequest() {
		return LASTREQUEST.get();
	}

	/**
	 * Returns the last response returned by the Service
	 * 
	 * @return Last response got from the Service
	 */
	public static String getLastResponse() {
		return LASTRESPONSE.get();
	}

	/**
	 * Configures and executes REST call: Supports JSON
	 * 
	 * @deprecated
	 * @param <T>
	 *            Response Type for de-serialization
	 * @param accessToken
	 *            AccessToken to be used for the call.
	 * @param httpMethod
	 *            Http Method verb
	 * @param resourcePath
	 *            Resource URI path
	 * @param payLoad
	 *            Payload to Service
	 * @param clazz
	 *            {@link Class} object used in De-serialization
	 * @return T
	 * @throws PayPalRESTException
	 */
	public static <T> T configureAndExecute(String accessToken,
			HttpMethod httpMethod, String resourcePath, String payLoad,
			Class<T> clazz) throws PayPalRESTException {
		return configureAndExecute(null, accessToken, httpMethod, resourcePath,
				null, payLoad, null, clazz);
	}

	/**
	 * Configures and executes REST call: Supports JSON
	 * 
	 * @param <T>
	 *            Response Type for de-serialization
	 * @param apiContext
	 *            {@link APIContext} to be used for the call.
	 * @param httpMethod
	 *            Http Method verb
	 * @param resource
	 *            Resource URI path
	 * @param payLoad
	 *            Payload to Service
	 * @param clazz
	 *            {@link Class} object used in De-serialization
	 * @return T
	 * @throws PayPalRESTException
	 */
	public static <T> T configureAndExecute(APIContext apiContext,
			HttpMethod httpMethod, String resourcePath, String payLoad,
			Class<T> clazz) throws PayPalRESTException {
		Map<String, String> cMap = null;
		String accessToken = null;
		String requestId = null;
		if (apiContext != null) {
			cMap = apiContext.getConfigurationMap();
			accessToken = apiContext.getAccessToken();
			requestId = apiContext.getRequestId();
		}
		return configureAndExecute(cMap, accessToken, httpMethod, resourcePath,
				null, payLoad, requestId, clazz);
	}

	/**
	 * Configures and executes REST call: Supports JSON
	 * 
	 * @param <T>
	 * @param apiContext
	 *            {@link APIContext} to be used for the call.
	 * @param httpMethod
	 *            Http Method verb
	 * @param resourcePath
	 *            Resource URI path
	 * @param headersMap
	 *            Optional headers Map
	 * @param payLoad
	 *            Payload to Service
	 * @param clazz
	 *            {@link Class} object used in De-serialization
	 * @return T
	 * @throws PayPalRESTException
	 */
	public static <T> T configureAndExecute(APIContext apiContext,
			HttpMethod httpMethod, String resourcePath,
			Map<String, String> headersMap, String payLoad, Class<T> clazz)
			throws PayPalRESTException {
		Map<String, String> cMap = null;
		if (apiContext != null) {
			cMap = apiContext.getConfigurationMap();
		}
		return configureAndExecute(cMap, null, httpMethod, resourcePath,
				headersMap, payLoad, null, clazz);
	}

	private static <T> T configureAndExecute(
			Map<String, String> configurationMap, String accessToken,
			HttpMethod httpMethod, String resourcePath,
			Map<String, String> headersMap, String payLoad, String requestId,
			Class<T> clazz) throws PayPalRESTException {
		T t = null;
		Map<String, String> cMap = null;

		/*
		 * Check for null before combining with default
		 */
		if (configurationMap != null) {
			cMap = SDKUtil.combineDefaultMap(configurationMap);
		} else {
			if (!configInitialized) {
				initializeToDefault();
			}

			/*
			 * The Map returned here is already combined with default values
			 */
			cMap = new HashMap<String, String>(PayPalResource.configurationMap);
		}

		/*
		 * Search for Content-Type header passed as a part of headersMap remove
		 * the header and pass it to HttpConfiguration object with creates the
		 * base connection
		 */
		String contentType = (headersMap != null && headersMap
				.containsKey(Constants.HTTP_CONTENT_TYPE_HEADER)) ? headersMap
				.remove(Constants.HTTP_CONTENT_TYPE_HEADER) : null;

		APICallPreHandler apiCallPreHandler = createAPICallPreHandler(cMap,
				payLoad, resourcePath, headersMap, accessToken, requestId);
		HttpConfiguration httpConfiguration = createHttpConfiguration(cMap,
				httpMethod, contentType, apiCallPreHandler);
		t = execute(apiCallPreHandler, httpConfiguration, clazz);
		return t;

	}

	/**
	 * Returns a implementation of {@link APICallPreHandler} for the underlying
	 * layer.
	 * 
	 * @param configurationMap
	 *            configuration Map
	 * @param payLoad
	 *            Raw payload
	 * @param resourcePath
	 *            URI part of the resource operated on
	 * @param headersMap
	 *            Custom HTTP headers map
	 * @param accessToken
	 *            OAuth Token
	 * @param requestId
	 *            PayPal Request Id
	 * @return
	 */
	protected static APICallPreHandler createAPICallPreHandler(
			Map<String, String> configurationMap, String payLoad,
			String resourcePath, Map<String, String> headersMap,
			String accessToken, String requestId) {
		
		/*
		 * Override this method in the subclass level to return 
		 * any implementation of APICallPreHandler for the
		 * system
		 */
		if (apiCallPreHandlerFactory == null) {
			setPreHandlerFactory(new RESTAPICallPreHandlerFactory());
		}
		RESTAPICallPreHandlerFactory restAPICallPreHandlerFactory = (RESTAPICallPreHandlerFactory) apiCallPreHandlerFactory;
		restAPICallPreHandlerFactory.setConfigurationMap(configurationMap);
		restAPICallPreHandlerFactory.setHeadersMap(headersMap);
		restAPICallPreHandlerFactory.setResourcePath(resourcePath);
		restAPICallPreHandlerFactory.setRequestId(requestId);
		restAPICallPreHandlerFactory.setAuthorizationToken(accessToken);
		restAPICallPreHandlerFactory.setPayLoad(payLoad);
		return apiCallPreHandlerFactory.createAPICallPreHandler();
	}

	/**
	 * Execute the API call and return response
	 * 
	 * @param <T>
	 *            Generic Type for response object construction
	 * @param apiCallPreHandler
	 *            Implementation of {@link APICallPreHandler}
	 * @param httpConfiguration
	 *            {@link HttpConfiguration}
	 * @param clazz
	 *            Response Object class
	 * @return Response Type
	 * @throws PayPalRESTException
	 */
	private static <T> T execute(APICallPreHandler apiCallPreHandler,
			HttpConfiguration httpConfiguration, Class<T> clazz)
			throws PayPalRESTException {
		T t = null;
		ConnectionManager connectionManager;
		HttpConnection httpConnection;
		Map<String, String> headers;
		String responseString;
		try {

			// REST Headers
			headers = apiCallPreHandler.getHeaderMap();

			// HttpConnection Initialization
			connectionManager = ConnectionManager.getInstance();
			httpConnection = connectionManager.getConnection(httpConfiguration);
			httpConnection.createAndconfigureHttpConnection(httpConfiguration);

			LASTREQUEST.set(apiCallPreHandler.getPayLoad());
			responseString = httpConnection.execute(null,
					apiCallPreHandler.getPayLoad(), headers);
			LASTRESPONSE.set(responseString);
			if (clazz != null) {
				t = JSONFormatter.fromJSON(responseString, clazz);
			}
		} catch (Exception e) {
			throw new PayPalRESTException(e.getMessage(), e);
		}
		return t;
	}

	/**
	 * Utility method that creates a {@link HttpConfiguration} object from the
	 * passed information
	 * 
	 * @param configurationMap
	 *            Configuration to base the construction upon.
	 * @param httpMethod
	 *            HTTP Method
	 * @param contentType
	 *            Content-Type header
	 * @param apiCallPreHandler
	 *            {@link APICallPreHandler} for retrieving EndPoint
	 * @return
	 */
	private static HttpConfiguration createHttpConfiguration(
			Map<String, String> configurationMap, HttpMethod httpMethod,
			String contentType, APICallPreHandler apiCallPreHandler) {
		HttpConfiguration httpConfiguration = new HttpConfiguration();
		httpConfiguration.setHttpMethod(httpMethod.toString());
		httpConfiguration.setEndPointUrl(apiCallPreHandler.getEndPoint());
		httpConfiguration.setContentType((contentType != null && contentType
				.trim().length() > 0) ? (contentType)
				: Constants.HTTP_CONTENT_TYPE_JSON);
		httpConfiguration
				.setGoogleAppEngine(Boolean.parseBoolean(configurationMap
						.get(Constants.GOOGLE_APP_ENGINE)));
		if (Boolean.parseBoolean(configurationMap
				.get((Constants.USE_HTTP_PROXY)))) {
			httpConfiguration.setProxyPort(Integer.parseInt(configurationMap
					.get((Constants.HTTP_PROXY_PORT))));
			httpConfiguration.setProxyHost(configurationMap
					.get((Constants.HTTP_PROXY_HOST)));
			httpConfiguration.setProxyUserName(configurationMap
					.get((Constants.HTTP_PROXY_USERNAME)));
			httpConfiguration.setProxyPassword(configurationMap
					.get((Constants.HTTP_PROXY_PASSWORD)));
		}
		httpConfiguration.setConnectionTimeout(Integer
				.parseInt(configurationMap
						.get(Constants.HTTP_CONNECTION_TIMEOUT)));
		httpConfiguration.setMaxRetry(Integer.parseInt(configurationMap
				.get(Constants.HTTP_CONNECTION_RETRY)));
		httpConfiguration.setReadTimeout(Integer.parseInt(configurationMap
				.get(Constants.HTTP_CONNECTION_READ_TIMEOUT)));
		httpConfiguration.setMaxHttpConnection(Integer
				.parseInt(configurationMap
						.get(Constants.HTTP_CONNECTION_MAX_CONNECTION)));
		httpConfiguration.setIpAddress(configurationMap
				.get(Constants.DEVICE_IP_ADDRESS));
		return httpConfiguration;
	}

	/**
	 * Implementation of {@link APICallPreHandlerFactory} that returns an instance of
	 * {@link RESTAPICallPreHandler}
	 * @author kjayakumar
	 *
	 */
	private static class RESTAPICallPreHandlerFactory implements
			APICallPreHandlerFactory {
		
		/**
		 * Configuration map
		 */
		Map<String, String> configurationMap = null;
		
		/**
		 * Raw Payload
		 */
		String payLoad = null;
		
		/**
		 * Resource URI path
		 */
		String resourcePath = null;
		
		/**
		 * Custom HTTP headers map
		 */
		Map<String, String> headersMap = null;
		
		/**
		 * Authorization Token
		 */
		String authorizationToken = null;
		
		/**
		 * Request Id
		 */
		String requestId = null;
		
		RESTAPICallPreHandlerFactory() {
			
		}

		/**
		 * @param configurationMap the configurationMap to set
		 */
		void setConfigurationMap(Map<String, String> configurationMap) {
			this.configurationMap = configurationMap;
		}

		/**
		 * @param payLoad the payLoad to set
		 */
		void setPayLoad(String payLoad) {
			this.payLoad = payLoad;
		}

		/**
		 * @param resourcePath the resourcePath to set
		 */
		void setResourcePath(String resourcePath) {
			this.resourcePath = resourcePath;
		}

		/**
		 * @param headersMap the headersMap to set
		 */
		void setHeadersMap(Map<String, String> headersMap) {
			this.headersMap = headersMap;
		}

		/**
		 * @param authorizationToken the authorizationToken to set
		 */
		void setAuthorizationToken(String authorizationToken) {
			this.authorizationToken = authorizationToken;
		}

		/**
		 * @param requestId the requestId to set
		 */
		void setRequestId(String requestId) {
			this.requestId = requestId;
		}

		public APICallPreHandler createAPICallPreHandler() {
			APICallPreHandler apiCallPreHandler = null;
			RESTAPICallPreHandler restAPICallPreHandler = new RESTAPICallPreHandler(
					configurationMap, headersMap);
			restAPICallPreHandler.setResourcePath(resourcePath);
			restAPICallPreHandler.setRequestId(requestId);
			restAPICallPreHandler.setAuthorizationToken(authorizationToken);
			restAPICallPreHandler.setPayLoad(payLoad);
			apiCallPreHandler = restAPICallPreHandler;
			return apiCallPreHandler;
		}

	}

}
