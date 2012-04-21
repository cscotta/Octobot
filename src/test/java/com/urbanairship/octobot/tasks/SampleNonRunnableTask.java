package com.urbanairship.octobot.tasks;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

// This is a sample test used for execution verification in the test suite.

public class SampleNonRunnableTask {

    private static final Logger logger = Logger.getLogger("Sample Unexecutable Task");

    // Stores a notification for later delivery.
    public static void fun(JSONObject task) {
        logger.info("There is no run method here!");
    }
}

