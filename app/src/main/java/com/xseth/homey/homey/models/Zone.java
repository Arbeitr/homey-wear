package com.xseth.homey.homey.models;

import androidx.annotation.NonNull;
import com.google.gson.annotations.SerializedName;

public class Zone {

    @NonNull
    @SerializedName("id")
    private String id;

    @NonNull
    @SerializedName("name")
    private String name;

    @SerializedName("parent")
    private String parentId;

    @SerializedName("icon")
    private String icon;

    @SerializedName("order")
    private int order;

    @SerializedName("active")
    private boolean active;

    public Zone(String id, String name) {
        this.id = id;
        this.name = name;
        this.active = true;
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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
