package com.xseth.homey.voice;

/**
 * Abstract class representing parsed voice intents
 */
public abstract class ParsedIntent {
    
    /**
     * Get the type of intent
     */
    public abstract String getType();

    /**
     * Light on intent
     */
    public static class LightOn extends ParsedIntent {
        private String room;
        private String deviceName;
        private String deviceId;

        public LightOn(String room, String deviceName, String deviceId) {
            this.room = room;
            this.deviceName = deviceName;
            this.deviceId = deviceId;
        }

        @Override
        public String getType() {
            return "LIGHT_ON";
        }

        public String getRoom() { return room; }
        public String getDeviceName() { return deviceName; }
        public String getDeviceId() { return deviceId; }
    }

    /**
     * Light off intent
     */
    public static class LightOff extends ParsedIntent {
        private String room;
        private String deviceName;
        private String deviceId;

        public LightOff(String room, String deviceName, String deviceId) {
            this.room = room;
            this.deviceName = deviceName;
            this.deviceId = deviceId;
        }

        @Override
        public String getType() {
            return "LIGHT_OFF";
        }

        public String getRoom() { return room; }
        public String getDeviceName() { return deviceName; }
        public String getDeviceId() { return deviceId; }
    }

    /**
     * Dim intent
     */
    public static class Dim extends ParsedIntent {
        private String room;
        private String deviceName;
        private String deviceId;
        private int level;

        public Dim(String room, String deviceName, String deviceId, int level) {
            this.room = room;
            this.deviceName = deviceName;
            this.deviceId = deviceId;
            this.level = level;
        }

        @Override
        public String getType() {
            return "DIM";
        }

        public String getRoom() { return room; }
        public String getDeviceName() { return deviceName; }
        public String getDeviceId() { return deviceId; }
        public int getLevel() { return level; }
    }

    /**
     * All off intent
     */
    public static class AllOff extends ParsedIntent {
        private String room;

        public AllOff(String room) {
            this.room = room;
        }

        @Override
        public String getType() {
            return "ALL_OFF";
        }

        public String getRoom() { return room; }
    }

    /**
     * Scene activation intent
     */
    public static class SceneActivate extends ParsedIntent {
        private String sceneName;

        public SceneActivate(String sceneName) {
            this.sceneName = sceneName;
        }

        @Override
        public String getType() {
            return "SCENE_ACTIVATE";
        }

        public String getSceneName() { return sceneName; }
    }

    /**
     * Temperature control intent
     */
    public static class Temperature extends ParsedIntent {
        private String room;
        private double degrees;
        private boolean isRelative;

        public Temperature(String room, double degrees, boolean isRelative) {
            this.room = room;
            this.degrees = degrees;
            this.isRelative = isRelative;
        }

        @Override
        public String getType() {
            return "TEMPERATURE";
        }

        public String getRoom() { return room; }
        public double getDegrees() { return degrees; }
        public boolean isRelative() { return isRelative; }
    }

    /**
     * Unknown intent
     */
    public static class Unknown extends ParsedIntent {
        private String originalText;

        public Unknown(String originalText) {
            this.originalText = originalText;
        }

        @Override
        public String getType() {
            return "UNKNOWN";
        }

        public String getOriginalText() { return originalText; }
    }
}
