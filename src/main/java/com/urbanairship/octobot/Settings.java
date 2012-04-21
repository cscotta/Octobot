package com.urbanairship.octobot;

import org.jvyaml.YAML;
import java.util.HashMap;
import java.io.FileReader;

import org.apache.log4j.Logger;


// This class is responsible for loading in configuration data for the
// application. By default, it searches for a YAML file located at
// /usr/local/octobot/octobot.yml, unless the JVM environment variable
// "-DconfigFile=/absolute/path" is specified. These values are accessed
// as a standard map by calling Settings.get("Octobot", "queues").

// Implemented as a singleton to avoid reading the file in multiple times.
// Changes to application configuration require a restart to take effect.

public class Settings {

    private static final Logger logger = Logger.getLogger("Settings");
    public static HashMap<String, HashMap<String, Object>> configuration = null;

    // Load the settings once on initialization, and hang onto them.
    private static final Settings INSTANCE = new Settings();

    @SuppressWarnings("unchecked")
    private Settings() {
        String settingsFile = System.getProperty("configFile");
        if (settingsFile == null) settingsFile = "/usr/local/octobot/octobot.yml";

        try {
            configuration = (HashMap<String,HashMap<String, Object>>)
                    YAML.load(new FileReader(settingsFile));
        } catch (Exception e) {
            // Logging to Stdout here because Log4J not yet initialized.
            logger.warn("Warning: No valid config at " + settingsFile);
            logger.warn("Please create this file, or set the " +
                    "-DconfigFile=/foo/bar/octobot.yml JVM variable to its location.");
            logger.warn("Continuing launch with internal defaults.");
        }

    }

    public static Settings get() {
        return INSTANCE;
    }


    /**
     * Fetches a setting from YAML configuration.
     * If unset in YAML, use the default value specified above.
     *
     * @param category Category to retrieve setting from (eg PosgreSQL)
     * @param key      Actual setting to retrieve
     * @return value of setting as a String or null
     */
    public static String get(String category, String key) {
        String result = null;

        try {
            HashMap configCategory = configuration.get(category);
            result = configCategory.get(key).toString();
        } catch (NullPointerException e) {
            logger.warn("Warning - unable to load " + category + " / " +
                key + " from configuration file.");
        }

        return result;
    }

    /**
     * Fetches a setting from YAML configuration.
     *
     * @param category Category to retrieve setting from (eg PosgreSQL)
     * @param key      Actual setting to retrieve
     * @param defaultValue value to return if setting doesn't exist
     * @return value of setting as a String or null
     */
    public static String get(String category, String key, String defaultValue) {
        String result = get(category, key);

        if (result == null) {
            return defaultValue;
        } else {
            return result;
        }
    }

    /**
     * Fetches a setting from YAML config and converts it to an integer. No
     * integer settings are autodetected, so that logic is not needed here.
     * 
     * @param category Category to retrieve setting from (eg PosgreSQL)
     * @param key      Actual setting to retrieve
     * @return value of setting as an Integer or null
     */
    public static Integer getAsInt(String category, String key) {
        Integer result = null;
        Object value = null;
        HashMap configCategory = null;

        try {
            configCategory = configuration.get(category);
            value = configCategory.get(key);

            if (value instanceof Long) {
                result = ((Long) configCategory.get(key)).intValue();
            } else if (value instanceof Integer) {
                result = (Integer) configCategory.get(key);
            }

        } catch (NullPointerException e) {
            logger.warn("Warning - unable to load " + category + " / " + key +
                " from config file, autodetection, or default settings.");
        }

        return result;
    }
    
    /** 
     * Fetches a setting from YAML config and converts it to an integer.
     * No integer settings are autodetected, so that logic is not needed here.
     * 
     * @param category Category to retrieve setting from (eg PosgreSQL)
     * @param key Actual setting to retrieve
     * @param defaultValue value to return if setting doesn't exist
     * @return value of setting as an Integer or defaultValue
     */
    public static Integer getAsInt(String category, String key, int defaultValue) {
        Integer result = getAsInt(category, key);
        if (result == null) {
            return defaultValue;
        } else {
            return result;
        }
    }

    // Fetches a value from settings as an integer, with a default value.
    public static Integer getIntFromYML(Object obj, Integer defaultValue) {
        int result = defaultValue;

        try { result = Integer.parseInt(obj.toString()); }
        catch (NumberFormatException e) { logger.info("Error reading settings."); }

        return result;
    }

    // Fetches a setting from YAML config and converts it to a boolean.
    // No boolean settings are autodetected, so that logic is not needed here.
    public static boolean getAsBoolean(String category, String key) {
        return Boolean.valueOf(get(category, key)).booleanValue();
    }

}

