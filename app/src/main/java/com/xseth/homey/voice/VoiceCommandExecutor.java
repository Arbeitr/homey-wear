package com.xseth.homey.voice;

import com.xseth.homey.homey.HomeyAPI;
import com.xseth.homey.homey.models.Device;
import com.xseth.homey.homey.models.Flow;
import com.xseth.homey.homey.models.Zone;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Executes parsed voice intents with multi-device logic
 */
public class VoiceCommandExecutor {

    // Temperature constants
    private static final double DEFAULT_TEMPERATURE = 20.0;
    private static final double MIN_TEMPERATURE = 5.0;
    private static final double MAX_TEMPERATURE = 30.0;

    /**
     * Result of command execution
     */
    public static class Result {
        private boolean success;
        private String message;
        private int affectedDevices;

        public Result(boolean success, String message, int affectedDevices) {
            this.success = success;
            this.message = message;
            this.affectedDevices = affectedDevices;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getAffectedDevices() { return affectedDevices; }
    }

    private HomeyAPI api;
    private Map<String, Device> allDevices;
    private Map<String, Zone> allZones;
    private Map<String, Flow> allFlows;

    public VoiceCommandExecutor(HomeyAPI api) {
        this.api = api;
        // Wait for HomeyAPI to be authenticated
        api.waitForHomeyAPI();
        loadData();
    }

    /**
     * Load devices, zones, and flows from API
     */
    private void loadData() {
        try {
            allDevices = api.getDevices();
            allZones = api.getZones();
            allFlows = api.getFlows();
            
            // Map zone names to devices
            if (allZones != null) {
                for (Device device : allDevices.values()) {
                    if (device.getZoneId() != null) {
                        Zone zone = allZones.get(device.getZoneId());
                        if (zone != null) {
                            device.setZoneName(zone.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Timber.e(e, "Failed to load data for voice commands");
        }
    }

    /**
     * Execute parsed intent
     */
    public Result execute(ParsedIntent intent) {
        if (intent == null) {
            return new Result(false, "Kein Befehl erkannt", 0);
        }

        try {
            switch (intent.getType()) {
                case "LIGHT_ON":
                    return executeLightOn((ParsedIntent.LightOn) intent);
                case "LIGHT_OFF":
                    return executeLightOff((ParsedIntent.LightOff) intent);
                case "DIM":
                    return executeDim((ParsedIntent.Dim) intent);
                case "ALL_OFF":
                    return executeAllOff((ParsedIntent.AllOff) intent);
                case "SCENE_ACTIVATE":
                    return executeSceneActivate((ParsedIntent.SceneActivate) intent);
                case "TEMPERATURE":
                    return executeTemperature((ParsedIntent.Temperature) intent);
                default:
                    return new Result(false, "Befehl nicht verstanden", 0);
            }
        } catch (Exception e) {
            Timber.e(e, "Error executing voice command");
            return new Result(false, "Fehler beim Ausführen", 0);
        }
    }

    /**
     * Execute light on command
     */
    private Result executeLightOn(ParsedIntent.LightOn intent) {
        List<Device> targetDevices = findTargetDevices(intent.getRoom(), intent.getDeviceName(), "onoff");
        
        if (targetDevices.isEmpty()) {
            return new Result(false, "Keine Geräte gefunden", 0);
        }

        int successCount = 0;
        for (Device device : targetDevices) {
            try {
                if (!device.isOn()) {
                    api.setCapabilityValue(device.getId(), "onoff", true).execute();
                    successCount++;
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to turn on device: " + device.getName());
            }
        }

        if (successCount > 0) {
            String message = successCount == 1 ? 
                "Licht eingeschaltet" : 
                successCount + " Lichter eingeschaltet";
            return new Result(true, message, successCount);
        } else {
            return new Result(false, "Fehler beim Einschalten", 0);
        }
    }

    /**
     * Execute light off command
     */
    private Result executeLightOff(ParsedIntent.LightOff intent) {
        List<Device> targetDevices = findTargetDevices(intent.getRoom(), intent.getDeviceName(), "onoff");
        
        if (targetDevices.isEmpty()) {
            return new Result(false, "Keine Geräte gefunden", 0);
        }

        int successCount = 0;
        for (Device device : targetDevices) {
            try {
                if (device.isOn()) {
                    api.setCapabilityValue(device.getId(), "onoff", false).execute();
                    successCount++;
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to turn off device: " + device.getName());
            }
        }

        if (successCount > 0) {
            String message = successCount == 1 ? 
                "Licht ausgeschaltet" : 
                successCount + " Lichter ausgeschaltet";
            return new Result(true, message, successCount);
        } else {
            return new Result(false, "Fehler beim Ausschalten", 0);
        }
    }

    /**
     * Execute dim command
     */
    private Result executeDim(ParsedIntent.Dim intent) {
        List<Device> targetDevices = findTargetDevices(intent.getRoom(), intent.getDeviceName(), "dim");
        
        if (targetDevices.isEmpty()) {
            return new Result(false, "Keine dimmbaren Geräte gefunden", 0);
        }

        int successCount = 0;
        double dimValue = intent.getLevel() / 100.0; // Convert percentage to 0-1 range
        
        for (Device device : targetDevices) {
            try {
                api.setCapabilityValue(device.getId(), "dim", dimValue).execute();
                successCount++;
            } catch (Exception e) {
                Timber.e(e, "Failed to dim device: " + device.getName());
            }
        }

        if (successCount > 0) {
            String message = "Helligkeit auf " + intent.getLevel() + "% gesetzt";
            return new Result(true, message, successCount);
        } else {
            return new Result(false, "Fehler beim Dimmen", 0);
        }
    }

    /**
     * Execute all off command
     */
    private Result executeAllOff(ParsedIntent.AllOff intent) {
        List<Device> targetDevices = findTargetDevices(intent.getRoom(), null, "onoff");
        
        if (targetDevices.isEmpty()) {
            return new Result(false, "Keine Geräte gefunden", 0);
        }

        int successCount = 0;
        for (Device device : targetDevices) {
            try {
                if (device.isOn()) {
                    api.setCapabilityValue(device.getId(), "onoff", false).execute();
                    successCount++;
                }
            } catch (Exception e) {
                Timber.e(e, "Failed to turn off device: " + device.getName());
            }
        }

        if (successCount > 0) {
            String message = "Alles ausgeschaltet (" + successCount + " Geräte)";
            return new Result(true, message, successCount);
        } else {
            return new Result(false, "Fehler beim Ausschalten", 0);
        }
    }

    /**
     * Execute scene activation command
     */
    private Result executeSceneActivate(ParsedIntent.SceneActivate intent) {
        if (allFlows == null || allFlows.isEmpty()) {
            return new Result(false, "Keine Szenen gefunden", 0);
        }

        // Find matching flow using fuzzy matching
        List<String> flowNames = new ArrayList<>();
        List<String> flowIds = new ArrayList<>();
        
        for (Map.Entry<String, Flow> entry : allFlows.entrySet()) {
            Flow flow = entry.getValue();
            if (flow.isEnabled() && flow.isTriggerable()) {
                flowNames.add(flow.getName());
                flowIds.add(flow.getId());
            }
        }

        FuzzyMatcher.MatchResult match = FuzzyMatcher.findBestMatch(
            intent.getSceneName(), flowNames, flowIds
        );

        if (match == null) {
            return new Result(false, "Szene nicht gefunden", 0);
        }

        try {
            api.triggerFlow(match.getMatchedId()).execute();
            Flow matchedFlow = allFlows.get(match.getMatchedId());
            return new Result(true, "Szene aktiviert: " + matchedFlow.getName(), 1);
        } catch (Exception e) {
            Timber.e(e, "Failed to trigger flow");
            return new Result(false, "Fehler beim Aktivieren", 0);
        }
    }

    /**
     * Execute temperature command
     */
    private Result executeTemperature(ParsedIntent.Temperature intent) {
        List<Device> targetDevices = findTargetDevices(intent.getRoom(), null, "target_temperature");
        
        if (targetDevices.isEmpty()) {
            return new Result(false, "Keine Heizgeräte gefunden", 0);
        }

        int successCount = 0;
        for (Device device : targetDevices) {
            try {
                double targetTemp;
                
                if (intent.isRelative()) {
                    // Relative temperature change
                    Double currentTemp = device.getCachedTargetTemperature();
                    if (currentTemp == null) {
                        // Fetch current temperature
                        Device updatedDevice = api.getDevice(device.getId());
                        if (updatedDevice != null) {
                            currentTemp = updatedDevice.getCachedTargetTemperature();
                        }
                        if (currentTemp == null) {
                            currentTemp = DEFAULT_TEMPERATURE;
                        }
                    }
                    targetTemp = currentTemp + intent.getDegrees();
                } else {
                    // Absolute temperature
                    targetTemp = intent.getDegrees();
                }

                // Clamp temperature to reasonable range
                targetTemp = Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, targetTemp));
                
                api.setCapabilityValue(device.getId(), "target_temperature", targetTemp).execute();
                device.setCachedTargetTemperature(targetTemp);
                successCount++;
            } catch (Exception e) {
                Timber.e(e, "Failed to set temperature: " + device.getName());
            }
        }

        if (successCount > 0) {
            String message = intent.isRelative() ? 
                "Temperatur angepasst" : 
                "Temperatur auf " + String.format("%.1f", intent.getDegrees()) + "°C gesetzt";
            return new Result(true, message, successCount);
        } else {
            return new Result(false, "Fehler beim Einstellen", 0);
        }
    }

    /**
     * Find target devices based on room and device name with fuzzy matching
     */
    private List<Device> findTargetDevices(String room, String deviceName, String capability) {
        List<Device> targets = new ArrayList<>();

        if (allDevices == null || allDevices.isEmpty()) {
            return targets;
        }

        // If explicit device name is provided, match by name
        if (deviceName != null && !deviceName.isEmpty()) {
            List<String> deviceNames = new ArrayList<>();
            List<String> deviceIds = new ArrayList<>();
            
            for (Device device : allDevices.values()) {
                if (hasCapability(device, capability)) {
                    deviceNames.add(device.getName());
                    deviceIds.add(device.getId());
                }
            }

            FuzzyMatcher.MatchResult match = FuzzyMatcher.findBestMatch(deviceName, deviceNames, deviceIds);
            
            if (match != null) {
                Device matchedDevice = allDevices.get(match.getMatchedId());
                if (matchedDevice != null) {
                    targets.add(matchedDevice);
                }
            }
            
            return targets;
        }

        // If room is specified, find all matching zones (fuzzy match)
        List<String> targetZoneIds = new ArrayList<>();
        
        if (room != null && !room.isEmpty() && allZones != null) {
            List<String> zoneNames = new ArrayList<>();
            List<String> zoneIds = new ArrayList<>();
            
            for (Zone zone : allZones.values()) {
                zoneNames.add(zone.getName());
                zoneIds.add(zone.getId());
            }

            // Fuzzy match to find all zones that match the room name
            for (int i = 0; i < zoneNames.size(); i++) {
                if (FuzzyMatcher.matches(room, zoneNames.get(i))) {
                    targetZoneIds.add(zoneIds.get(i));
                }
            }
        }

        // Collect all devices in target zones with the required capability
        for (Device device : allDevices.values()) {
            if (hasCapability(device, capability)) {
                if (room == null || room.isEmpty()) {
                    // No room specified, add all devices with capability
                    targets.add(device);
                } else if (device.getZoneId() != null && targetZoneIds.contains(device.getZoneId())) {
                    // Device is in one of the matching zones
                    targets.add(device);
                }
            }
        }

        return targets;
    }

    /**
     * Check if device has capability
     */
    private boolean hasCapability(Device device, String capability) {
        if (device == null || capability == null) return false;
        
        // Check if device capability matches or contains the required capability
        String deviceCap = device.getCapability();
        if (deviceCap == null) return false;
        
        if (deviceCap.equals(capability)) return true;
        
        // For onoff, also check if device has button or speaker_playing
        if (capability.equals("onoff") && (deviceCap.equals("button") || deviceCap.equals("speaker_playing"))) {
            return true;
        }
        
        return false;
    }
}
