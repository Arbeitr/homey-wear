package com.xseth.homey.homey.services;

import com.xseth.homey.homey.models.Device;
import com.xseth.homey.homey.models.Flow;
import com.xseth.homey.homey.models.User;
import com.xseth.homey.homey.models.Zone;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface HomeyService {

    @POST("api/manager/users/login")
    Call<String> login(@Body Map<String, String> body);

    @GET("api/manager/users/user/me")
    Call<User> getUser();

    @GET("api/manager/devices/device/")
    Call<Map<String, Device>> getDevices();

    @PUT("api/manager/devices/device/{deviceId}/capability/{capabilityId}")
    Call<Map<String, Object>> setCapability(@Path("deviceId") String deviceId, @Path("capabilityId") String
            capabilityId, @Body Map<String, Boolean> body);

    @GET("api/manager/zones/zone/")
    Call<Map<String, Zone>> getZones();

    @GET("api/manager/flow/flow/")
    Call<Map<String, Flow>> getFlows();

    @POST("api/manager/flow/flow/{flowId}/trigger")
    Call<Void> triggerFlow(@Path("flowId") String flowId);

    @PUT("api/manager/devices/device/{deviceId}/capability/{capabilityId}")
    Call<Map<String, Object>> setCapabilityValue(@Path("deviceId") String deviceId,
        @Path("capabilityId") String capabilityId, @Body Map<String, Object> body);

    @GET("api/manager/devices/device/{deviceId}")
    Call<Device> getDevice(@Path("deviceId") String deviceId);

}
