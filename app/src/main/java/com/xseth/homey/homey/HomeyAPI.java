package com.xseth.homey.homey;

import com.xseth.homey.BuildConfig;
import com.xseth.homey.homey.models.Device;
import com.xseth.homey.homey.models.Flow;
import com.xseth.homey.homey.models.Homey;
import com.xseth.homey.homey.models.Token;
import com.xseth.homey.homey.models.User;
import com.xseth.homey.homey.models.Zone;
import com.xseth.homey.homey.services.CloudService;
import com.xseth.homey.homey.services.HomeyService;
import com.xseth.homey.utils.TokenInterceptor;

import java.io.IOException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;



public class HomeyAPI {

    // Athom Homey client ID for accessing API
    public static final String CLIENT_ID = BuildConfig.ATHOM_CLIENT_ID;
    // Athom Homey client secret for accessing API
    public static final String CLIENT_SECRET = BuildConfig.ATHOM_CLIENT_SECRET;
    // ReturnURL for OAuth2
    public static final String RETURN_URL = "https://wear.googleapis.com/3p_auth/com.xseth.homey";
    // URL for Icon CDN
    public static final String ICON_URL = "https://icons-cdn.athom.com/";
    // List of OAuth2 scopes used in Athom Homey API
    public static final String[] SCOPES = {
            "account.homeys.readonly",
            "homey.user.self",
            "homey.device.readonly",
            "homey.device.control",
            "homey.flow.start",
            "homey.flow.readonly"
    };
    // List of supported capabilities
    public static final String[] CAPABILITIES = {
            "onoff",
            "button",
            "speaker_playing"
    };

    // Instance HomeyAPI for singleton
    private static volatile HomeyAPI INSTANCE;
    // Service pointing to the AthomCloudAPI
    private CloudService cloudService;
    // Service pointing to the HomeyAPI
    private HomeyService homeyService;
    // Athom User object
    private User user;
    // HTTP interceptor to handle authentication cloudService
    private TokenInterceptor tokenInterceptor;
    // HTTP interceptor to handle authentication for homeyService
    private TokenInterceptor homeyTokenInterceptor;
    // Instance for HTTPLogger
    private HttpLoggingInterceptor httpLogger;

    /**
     * Get HomeyAPI instance
     * @return instance of HomeyAPI
     */
    public synchronized static HomeyAPI getAPI() {
        if(INSTANCE == null)
            INSTANCE = new HomeyAPI();

        return INSTANCE;
    }

    /**
     * HomeyAPI constructor
     */
    public HomeyAPI(){
        // Create Interceptor for Bearer token and load, if existing, previous saved bearer token
        tokenInterceptor = new TokenInterceptor();
        tokenInterceptor.setSessionToken(Token.load());

        // Add logging interceptor
        httpLogger = new HttpLoggingInterceptor(message -> Timber.tag("OkHttp").v(message));

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(tokenInterceptor)
                .addInterceptor(httpLogger)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("https://api.athom.com/")
                .build();

        cloudService = retrofit.create(CloudService.class);

        // Set level BASIC only in debugging mode
        if (BuildConfig.DEBUG)
            httpLogger.setLevel(HttpLoggingInterceptor.Level.BASIC);
    }

    /**
     * Verify whether there is an authorized session
     * @return if there is an authorized session
     */
    public synchronized Boolean isLoggedIn() throws IOException {
        Call<User> call = cloudService.getUser();

        user = call.execute().body();

        return (user != null);
    }

    /**
     * Verify whether there is authorization for the HomeyAPI
     * @return if there is an authorized HomeyAPI
     */
    public synchronized Boolean isHomeyAuthenticated(){
        return this.homeyService != null;
    }

    /**
     * Wait in thread for authorized HomeyAPI
     */
    public synchronized void waitForHomeyAPI(){
        if (!this.isHomeyAuthenticated()) {
            synchronized (this) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Set an OAUTH2 session token
     * @param code OAUTH2 session token
     */
    public void setToken(String code) throws IOException {
        Timber.i("Stored OAuth token");

        Call call = cloudService.authenticate(
                CLIENT_ID,
                CLIENT_SECRET,
                "authorization_code",
                code
        );

        Token token = (Token) call.execute().body();
        token.save();

        tokenInterceptor.setSessionToken(token);

        // Authenticate Homey
        authenticateHomey();
    }

    /**
     * Retrieve new OAUTH2 session token via token refresh
     * @param refreshToken OAUTH2 refresh token
     */
    public void refreshToken(String refreshToken){
        // Set token to null to signify refresh
        tokenInterceptor.setSessionToken(null);

        Call<Token> call = cloudService.refreshToken(
                CLIENT_ID,
                CLIENT_SECRET,
                "refresh_token",
                refreshToken
        );

        try {
            Token token = call.execute().body();
            token.save();

            tokenInterceptor.setSessionToken(token);
        } catch (IOException e) {
            Timber.e(e, "Failed to refresh token");
        }
    }

    /**
     * Get LoginURL used by OAUTH2
     * @return login url used by OAUTH2
     */
    public String getLoginURL() {
        // Use StringJoiner instead of `String.join` to support API25
        StringJoiner sj = new StringJoiner(",");
        for(String scope : SCOPES) sj.add(scope);

        Request request = cloudService.getLoginURL(
                            CLIENT_ID,
                            RETURN_URL,
                            sj.toString()
        ).request();

        return request.url().toString();
    }

    /**
     * Authenticate against the Homey
     */
    public synchronized void authenticateHomey() throws IOException {
        // if homeyService already exists, skip
        if (this.homeyService != null)
            return;

        Token token = new Token();
        Map<String, String> jsonParams = new HashMap<>();
        jsonParams.put("audience", "homey");

        Timber.i("Start authenticating homey");

        if(this.user == null && !this.isLoggedIn())
            throw new IOException("Failed to retrieve user");

        // Get delegationToken from AthomCloudAPI
        Call<String> call = cloudService.authenticateHomey(jsonParams);
        String delegationToken = call.execute().body();

        homeyTokenInterceptor = new TokenInterceptor();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(homeyTokenInterceptor)
                .addInterceptor(httpLogger)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(this.user.getFirstHomey().getRemoteUrl())
                .build();

        // Create Service for homeyAPI
        homeyService = retrofit.create(HomeyService.class);

        jsonParams = new HashMap<>();
        jsonParams.put("token", delegationToken);

        // Login via delegationToken to retrieve sessionToken
        call = homeyService.login(jsonParams);
        String homeyToken = call.execute().body();

        // Set sessionToken for accessing rest of Homey APIs
        token.setAccessToken(homeyToken);
        homeyTokenInterceptor.setSessionToken(token);

        Timber.i("Successfully authenticated against homey");

        // Notify all threads that the homeyAPI is authenticated
        synchronized (this){
            this.notifyAll();
        }
    }

    /**
     * Get a list of favorite devices
     * @return list of favorite devices
     */
    public Map<String, Device> getDevices(){
        // LinkedHashMap keeps order of keys
        Map<String, Device> newList = new LinkedHashMap<>();

        try {
            Call<Map<String, Device>> call = homeyService.getDevices();
            Map<String, Device> devices = call.execute().body();

            Call<User> userCall = homeyService.getUser();
            User user = userCall.execute().body();

            for(String id : user.getDeviceFavorites()) {
                Device device = devices.get(id);
                device.setCapability(); // Configure capability and onoff value
                newList.put(id, device);
            }
        } catch (IOException ioe){
            Timber.e(ioe, "Failed to retrieve devices");
        }

        return newList;
    }

    /**
     * Get all devices (not just favorites)
     * @return map of all devices
     */
    public Map<String, Device> getAllDevices() {
        // LinkedHashMap keeps order of keys
        Map<String, Device> allDevices = new LinkedHashMap<>();

        try {
            Call<Map<String, Device>> call = homeyService.getDevices();
            Map<String, Device> devices = call.execute().body();

            if (devices != null) {
                for (Map.Entry<String, Device> entry : devices.entrySet()) {
                    Device device = entry.getValue();
                    device.setCapability(); // Configure capability and onoff value
                    allDevices.put(entry.getKey(), device);
                }
            }
        } catch (IOException ioe){
            Timber.e(ioe, "Failed to retrieve all devices");
        }

        return allDevices;
    }

    /**
     * Turn device on or off
     * @param device device to turn on or off
     */
    public Call<Map<String, Object>> turnOnOff(Device device){
        Map<String, Boolean> jsonParams = new HashMap<>();

        // Set new value, onoff is opposite of current value
        jsonParams.put("value", !device.isOn());

        return homeyService.setCapability(
                device.getId(),
                device.getCapability(),
                jsonParams
        );
    }

    /**
     * Get all zones
     * @return map of zones
     */
    public Map<String, Zone> getZones() {
        try {
            Call<Map<String, Zone>> call = homeyService.getZones();
            Map<String, Zone> zones = call.execute().body();
            return zones != null ? zones : new HashMap<>();
        } catch (IOException ioe) {
            Timber.e(ioe, "Failed to retrieve zones");
            return new HashMap<>();
        }
    }

    /**
     * Get all flows
     * @return map of flows
     */
    public Map<String, Flow> getFlows() {
        try {
            Call<Map<String, Flow>> call = homeyService.getFlows();
            Map<String, Flow> flows = call.execute().body();
            return flows != null ? flows : new HashMap<>();
        } catch (IOException ioe) {
            Timber.e(ioe, "Failed to retrieve flows");
            return new HashMap<>();
        }
    }

    /**
     * Trigger a flow
     * @param flowId flow ID to trigger
     */
    public Call<Void> triggerFlow(String flowId) {
        return homeyService.triggerFlow(flowId);
    }

    /**
     * Get a specific device
     * @param deviceId device ID
     * @return device
     */
    public Device getDevice(String deviceId) {
        try {
            Call<Device> call = homeyService.getDevice(deviceId);
            return call.execute().body();
        } catch (IOException ioe) {
            Timber.e(ioe, "Failed to retrieve device");
            return null;
        }
    }

    /**
     * Set capability value for a device
     * @param deviceId device ID
     * @param capabilityId capability ID
     * @param value value to set
     * @return result
     */
    public Call<Map<String, Object>> setCapabilityValue(String deviceId, String capabilityId, Object value) {
        Map<String, Object> jsonParams = new HashMap<>();
        jsonParams.put("value", value);
        return homeyService.setCapabilityValue(deviceId, capabilityId, jsonParams);
    }
}
