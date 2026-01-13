package com.xseth.homey.homey.models;

import androidx.annotation.NonNull;
import com.google.gson.annotations.SerializedName;

public class Flow {

    @NonNull
    @SerializedName("id")
    private String id;

    @NonNull
    @SerializedName("name")
    private String name;

    @SerializedName("enabled")
    private boolean enabled;

    @SerializedName("triggerable")
    private boolean triggerable;

    @SerializedName("folder")
    private String folder;

    public Flow(String id, String name) {
        this.id = id;
        this.name = name;
        this.enabled = true;
        this.triggerable = true;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTriggerable() {
        return triggerable;
    }

    public void setTriggerable(boolean triggerable) {
        this.triggerable = triggerable;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }
}
