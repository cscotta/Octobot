package com.urbanairship.octobot.tasks;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

// This is a sample test used for execution verification in the test suite.

public class SampleTask {

    private static final Logger logger = Logger.getLogger("Sample Task");

    // Stores a notification for later delivery.
    public static void run(JSONObject task) {
        logger.info("== Successfully ran SampleTask.");
    }
}

