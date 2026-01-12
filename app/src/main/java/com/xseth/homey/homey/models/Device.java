package com.xseth.homey.homey.models;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;
import com.xseth.homey.homey.HomeyAPI;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import timber.log.Timber;

@Entity(tableName = "devices")
public class Device {

    // Device ID
    @PrimaryKey
    @NonNull
    @SerializedName("id")
    private String id;

    // Device Name
    @NonNull
    @SerializedName("name")
    private String name;

    // Device on or off
    @NonNull
    private Boolean on;

    // Device icon
    public Bitmap iconImage;

    // Capability which is modified
    @NonNull
    public String capability;

    // Zone ID where this device is located
    @SerializedName("zone")
    private String zoneId;

    // Zone name (mapped from zones)
    private String zoneName;

    // Cached target temperature for relative changes
    private Double cachedTargetTemperature;

    // Capabilities Object returned by API
    @Ignore
    @SerializedName("capabilitiesObj")
    private Map<String, Map<String, Object>> capabilitiesObj;

    // Icon Object returned by API, containing icon IDs
    @Ignore
    @SerializedName("iconObj")
    private Map<String, String> iconObj;

    /**
     * Device constructor
     * @param id device ID
     * @param name device name
     */
    public Device(String id, String name){
        this.id = id;
        this.name = name;
        this.on = true;
    }

    /**
     * Get device ID
     * @return device ID
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * Get the device name
     * @return device name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Get the Icon bitmap
     * @return icon of device
     */
    public Bitmap getIconImage(){
        return this.iconImage;
    }

    /**
     * Get capability which is used
     * @return capability which is used
     */
    @NonNull
    public String getCapability() { return this.capability; }

    /**
     * Set device ID
     * @param id id to set
     */
    public void setId(@NonNull String id) {
        this.id = id;
    }

    /**
     * Set name
     * @param name name to set
     */
    public void setName(@NonNull String name) {
        this.name = name;
    }

    /**
     * Determine which capability of devices is used by this app. Get latest status of capability
     *
     * Available options are listed in HomeyAPI. Default value is onoff
     */
    public void setCapability(){
        List<String> capabilities = Arrays.asList(HomeyAPI.CAPABILITIES);

        for(String capability : this.capabilitiesObj.keySet()){
            if(capabilities.contains(capability)) {
                this.capability = capability;

                if(!capability.equals("button")) {
                    // Get the capability value object
                    Map<String, Object> capabilityData = this.capabilitiesObj.get(capability);
                    if (capabilityData != null && capabilityData.containsKey("value")) {
                        Object valueObj = capabilityData.get("value");
                        if (valueObj != null) {
                            this.on = Boolean.parseBoolean(valueObj.toString());
                        } else {
                            Timber.w("Device %s: capability %s has null value", this.name, capability);
                            this.on = false;
                        }
                    } else {
                        Timber.w("Device %s: capability %s missing value field", this.name, capability);
                        this.on = false;
                    }
                } else {
                    // Button contains no value, so default to true
                    this.on = true;
                }
            }
        }

        // onoff is fallback capability
        if(this.capability == null) {
            this.capability = "onoff";
            this.on = true;
        }
    }

    /**
     * download the icon in bitmap form
     */
    public void fetchIconImage() {
        if (this.iconObj == null) {
            Timber.w("Device %s: iconObj is null, cannot fetch icon", this.name);
            return;
        }
        
        String iconId = this.iconObj.get("id");
        if (iconId == null || iconId.isEmpty()) {
            Timber.w("Device %s: iconId is null or empty", this.name);
            return;
        }
        
        final String strUrl = HomeyAPI.ICON_URL + iconId + "-128.png";

        try{
            URL url = new URL(strUrl);
            URLConnection conn = url.openConnection();

            this.iconImage = BitmapFactory.decodeStream(conn.getInputStream());
            if (this.iconImage == null) {
                Timber.w("Device %s: Failed to decode icon from %s", this.name, strUrl);
            }
        } catch (MalformedURLException mue) {
            Timber.e(mue, "Device %s: Invalid iconUrl %s", this.name, strUrl);
        } catch (IOException ioe) {
            Timber.e(ioe, "Device %s: Error downloading icon from %s", this.name, strUrl);
        }
    }

    /**
     * Set the device on or off status
     * @param on on value to set
     */
    public void setOn(Boolean on){ this.on = on; }

    /**
     * Verify whether this device is turned on or off
     * @return whether onoff is different than currently in device
     */
    public boolean verifyOnOff(boolean onoff){
        if (onoff != this.on){
            this.setOn(onoff);
            return true;
        }

        return false;
    }

    /**
     * Return whether this device is on or off
     * @return if device is on
     */
    public Boolean isOn(){
        return this.on;
    }

    /**
     * Return whether this device contains button capability
     * @return if device is a button
     */
    public boolean isButton(){
        return this.getCapability().equals("button");
    }

    /**
     * Get zone ID
     * @return zone ID where device is located
     */
    public String getZoneId() {
        return zoneId;
    }

    /**
     * Set zone ID
     * @param zoneId zone ID to set
     */
    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    /**
     * Get zone name
     * @return zone name
     */
    public String getZoneName() {
        return zoneName;
    }

    /**
     * Set zone name
     * @param zoneName zone name to set
     */
    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    /**
     * Get cached target temperature
     * @return cached target temperature
     */
    public Double getCachedTargetTemperature() {
        return cachedTargetTemperature;
    }

    /**
     * Set cached target temperature
     * @param cachedTargetTemperature target temperature to cache
     */
    public void setCachedTargetTemperature(Double cachedTargetTemperature) {
        this.cachedTargetTemperature = cachedTargetTemperature;
    }

    /**
     * Turn device on or off based on on value
     */
    public Call turnOnOff() {
        HomeyAPI api = HomeyAPI.getAPI();
        // Wait if HomeyAPI is not yet authenticated
        api.waitForHomeyAPI();

        return api.turnOnOff(this);
    }

    /**
     * Get the value (on|off) of capability specified by ID
     * @param id capability ID to get value from
     * @return boolean value whether capability is on|off
     */
    public boolean getCapabilityValue(String id){
        Map<String, Object> capability = this.capabilitiesObj.get(id);

        // If capability is not found or if button, fallback is true
        if(capability == null || this.isButton())
            return true;

        return (Boolean) capability.get("value");
    }
}
