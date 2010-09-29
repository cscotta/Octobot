package com.urbanairship.octobot

import org.jvyaml.YAML
import java.util.HashMap
import java.io.FileReader
import org.apache.log4j.Logger


// This class is responsible for loading in configuration data for the
// application. By default, it searches for a YAML file located at
// /usr/local/octobot/octobot.yml, unless the JVM environment variable
// "-DconfigFile=/absolute/path" is specified. These values are accessed
// as a standard map by calling Settings.get("Octobot", "queues").

// Implemented as a singleton to avoid reading the file in multiple times.
// Changes to application configuration require a restart to take effect.

object Settings {

    val logger = Logger.getLogger("Settings")
    var configuration:HashMap[String, HashMap[String, Any]] = null

    // Load the settings once on initialization, and hang onto them.
    var settingsFile = System.getProperty("configFile")
    if (settingsFile == null) settingsFile = "/usr/local/octobot/octobot.yml"

    try {
        configuration = YAML.load(new FileReader(settingsFile)).asInstanceOf[HashMap[String, HashMap[String, Any]]];
    } catch {
      case ex: Exception => {
        logger.warn("Warning: No valid config at " + settingsFile)
        logger.warn("Please create this file, or set the " +
                "-DconfigFile=/foo/bar/octobot.yml JVM variable to its location.")
        logger.warn("Continuing launch with internal defaults.")
      }
    }

    def get(category: String, key: String) : String = {
      var result = ""
      try {
        val configCategory = configuration.get(category)
        result = configCategory.get(key).toString
      } catch {
        case ex: NullPointerException => {
          logger.warn("Warning - unable to load " + category + " / " +
              key + " from configuration file.")
        }
      }
      
      return result
    }
    
    // Fetches a setting from YAML config and converts it to an integer.
    // No integer settings are autodetected, so that logic is not needed here.
    def getAsInt(category: String, key: String) : Int = {
      var result = 0
      var value : Any = null
      var configCategory : HashMap[String, Any] = null
      
      try {
        configCategory = configuration.get(category)
        value = configCategory.get(key)
        if (value != null) result = value.asInstanceOf[Long].intValue
      } catch {
        case ex: NullPointerException => {
          logger.warn("Warning - unable to load " + category + " / " +
              key + " from configuration file.")
        }
      }
      
      return result
    }

    def getIntFromYML(obj: Any, defaultValue : Int) : Int = {
      var result = defaultValue

      try { 
        return obj.toString.toInt
      } catch {
        case ex: Exception => {
          logger.info("Error reading settings.")
          return defaultValue
        }
      }
    }


    // Fetches a setting from YAML config and converts it to a boolean.
    // No boolean settings are autodetected, so that logic is not needed here.
    def getAsBoolean(category: String, key: String) : Boolean = {
      return get(category, key).toBoolean
    }
}

