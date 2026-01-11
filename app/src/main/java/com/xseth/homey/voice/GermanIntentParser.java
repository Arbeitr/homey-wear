package com.xseth.homey.voice;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for German voice commands
 */
public class GermanIntentParser {

    // Room aliases mapping
    private static final Map<String, String> ROOM_ALIASES = new HashMap<String, String>() {{
        put("wohnzimmer", "wohnzimmer");
        put("schlafzimmer", "schlafzimmer");
        put("küche", "küche");
        put("kueche", "küche");
        put("bad", "bad");
        put("badezimmer", "bad");
        put("flur", "flur");
        put("büro", "büro");
        put("buero", "büro");
        put("keller", "keller");
        put("garage", "garage");
        put("kinderzimmer", "kinderzimmer");
        put("esszimmer", "esszimmer");
        put("garten", "garten");
    }};

    /**
     * Parse German voice command into intent
     */
    public static ParsedIntent parse(String command) {
        if (command == null || command.trim().isEmpty()) {
            return new ParsedIntent.Unknown("");
        }

        String normalizedCommand = command.toLowerCase().trim();

        // Extract room from command
        String room = extractRoom(normalizedCommand);

        // Check for "all off" command
        if (isAllOffCommand(normalizedCommand)) {
            return new ParsedIntent.AllOff(room);
        }

        // Check for scene/flow activation
        ParsedIntent sceneIntent = parseSceneActivation(normalizedCommand);
        if (sceneIntent != null) {
            return sceneIntent;
        }

        // Check for temperature commands
        ParsedIntent tempIntent = parseTemperature(normalizedCommand, room);
        if (tempIntent != null) {
            return tempIntent;
        }

        // Check for dimming commands
        ParsedIntent dimIntent = parseDimming(normalizedCommand, room);
        if (dimIntent != null) {
            return dimIntent;
        }

        // Check for light on/off commands
        ParsedIntent lightIntent = parseLightCommand(normalizedCommand, room);
        if (lightIntent != null) {
            return lightIntent;
        }

        return new ParsedIntent.Unknown(command);
    }

    /**
     * Extract room from command
     */
    private static String extractRoom(String command) {
        // Pattern: "im/in der/in [room]"
        Pattern roomPattern = Pattern.compile("(?:im|in der|in)\\s+(\\w+)");
        Matcher matcher = roomPattern.matcher(command);
        
        if (matcher.find()) {
            String extractedRoom = matcher.group(1).toLowerCase();
            return ROOM_ALIASES.getOrDefault(extractedRoom, extractedRoom);
        }

        // Check if command ends with a room name
        for (String room : ROOM_ALIASES.keySet()) {
            if (command.endsWith(room) || command.contains(room + " ")) {
                return ROOM_ALIASES.get(room);
            }
        }

        return null;
    }

    /**
     * Check if command is "all off"
     */
    private static boolean isAllOffCommand(String command) {
        return command.matches(".*(alles aus|alle aus|komplett aus).*");
    }

    /**
     * Parse scene activation command
     */
    private static ParsedIntent parseSceneActivation(String command) {
        Pattern scenePattern = Pattern.compile("(?:aktiviere|starte|scene|szene)\\s+(.+)");
        Matcher matcher = scenePattern.matcher(command);
        
        if (matcher.find()) {
            String sceneName = matcher.group(1).trim();
            // Remove room references from scene name
            sceneName = sceneName.replaceAll("(?:im|in der|in)\\s+\\w+", "").trim();
            return new ParsedIntent.SceneActivate(sceneName);
        }

        return null;
    }

    /**
     * Parse temperature command
     */
    private static ParsedIntent parseTemperature(String command, String room) {
        // Absolute temperature: "auf 21 Grad", "Temperatur 20", "Heizung auf 19"
        Pattern absolutePattern = Pattern.compile("(?:auf|temperatur)\\s+(\\d+)(?:\\s*grad)?");
        Matcher absMatcher = absolutePattern.matcher(command);
        
        if (absMatcher.find()) {
            double degrees = Double.parseDouble(absMatcher.group(1));
            return new ParsedIntent.Temperature(room, degrees, false);
        }

        // Relative temperature: "wärmer", "2 Grad mehr", "kälter", "1 Grad weniger"
        if (command.matches(".*(wärmer|waermer).*")) {
            double degrees = extractNumber(command);
            if (degrees == 0) degrees = 1.0; // Default increment
            return new ParsedIntent.Temperature(room, degrees, true);
        }

        if (command.matches(".*(kälter|kaelter|kühler|kuehler).*")) {
            double degrees = extractNumber(command);
            if (degrees == 0) degrees = -1.0; // Default decrement
            else degrees = -degrees;
            return new ParsedIntent.Temperature(room, degrees, true);
        }

        Pattern relativePattern = Pattern.compile("(\\d+)\\s*grad\\s*(mehr|weniger)");
        Matcher relMatcher = relativePattern.matcher(command);
        
        if (relMatcher.find()) {
            double degrees = Double.parseDouble(relMatcher.group(1));
            if (relMatcher.group(2).equals("weniger")) {
                degrees = -degrees;
            }
            return new ParsedIntent.Temperature(room, degrees, true);
        }

        return null;
    }

    /**
     * Parse dimming command
     */
    private static ParsedIntent parseDimming(String command, String room) {
        // Pattern: "auf 50%", "Helligkeit 70", "dimme auf 30"
        Pattern dimPattern = Pattern.compile("(?:auf|helligkeit|dimme auf|dimmen auf)\\s*(\\d+)\\s*(?:%|prozent)?");
        Matcher matcher = dimPattern.matcher(command);
        
        if (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            // Clamp between 0 and 100
            level = Math.max(0, Math.min(100, level));
            
            // Extract device name if present
            String deviceName = extractDeviceName(command);
            
            return new ParsedIntent.Dim(room, deviceName, null, level);
        }

        return null;
    }

    /**
     * Parse light on/off command
     */
    private static ParsedIntent parseLightCommand(String command, String room) {
        boolean isOn = false;
        
        // Light ON patterns
        if (command.matches(".*(licht an|licht ein|schalte licht ein|mach.*licht an|licht anmachen).*")) {
            isOn = true;
        }
        // Light OFF patterns
        else if (command.matches(".*(licht aus|schalte licht aus|mach.*licht aus|licht ausmachen).*")) {
            isOn = false;
        } else {
            return null;
        }

        // Extract device name if present
        String deviceName = extractDeviceName(command);

        if (isOn) {
            return new ParsedIntent.LightOn(room, deviceName, null);
        } else {
            return new ParsedIntent.LightOff(room, deviceName, null);
        }
    }

    /**
     * Extract device name from command
     */
    private static String extractDeviceName(String command) {
        // Remove common command words to isolate device name
        String cleaned = command.replaceAll("(?:licht|an|aus|ein|schalte|mach|auf|helligkeit|dimme|im|in der|in)\\s*", "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        
        // If something remains and it's not just a room, it might be a device name
        if (cleaned.length() > 2 && !ROOM_ALIASES.containsKey(cleaned)) {
            return cleaned;
        }
        
        return null;
    }

    /**
     * Extract number from command
     */
    private static double extractNumber(String command) {
        Pattern numberPattern = Pattern.compile("(\\d+)");
        Matcher matcher = numberPattern.matcher(command);
        
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        
        return 0;
    }

    /**
     * Normalize room name
     */
    public static String normalizeRoom(String room) {
        if (room == null) return null;
        String normalized = room.toLowerCase().trim();
        return ROOM_ALIASES.getOrDefault(normalized, normalized);
    }
}
