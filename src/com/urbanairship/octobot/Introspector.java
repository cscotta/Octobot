package com.urbanairship.octobot;

import java.util.HashMap;
import java.util.LinkedList;
import java.net.Socket;
import java.net.ServerSocket;

import java.io.OutputStream;
import java.io.IOException;

import java.lang.management.RuntimeMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;

import org.json.simple.JSONValue;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;


// This class provides some basic instrumentation for Octobot.
// It provides a simple socket server listening on an admin port (1228
// by default). Upon receiving a connection, it prints out a JSON string
// of information such as such as tasks processed per second, total successes
// and failures, successes and failures per task / per queue.

public class Introspector implements Runnable {

    private ServerSocket server = null;
    private RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
    private int port = Settings.getAsInt("Octobot", "metrics_port");

    private static final Logger logger = Logger.getLogger("Introspector");

    public void run() {

        if (port < 1) port = 1228;
        try { server = new ServerSocket(port); }
        catch (IOException e) {
            logger.error("Introspector: Unable to listen on port: " + port +
                    ". Introspector will be unavailable on this instance.");
            return;
        }

        logger.info("Introspector launched on port: " + port);
        
        while (true) {
            try {
                Socket socket = server.accept();
                OutputStream oos = socket.getOutputStream();
                oos.write(introspect().getBytes());
                oos.close();
                socket.close();
            } catch (IOException e) {
                logger.error("Error in accepting Introspector connection. "
                        + "Introspector thread shutting down.", e);
                return;
            }
        }
    }

    // Assembles metrics for each task and returns a JSON string.
    // Warnings suppressed are from building the JSON itself.
    @SuppressWarnings("unchecked")
    public String introspect() {
        HashMap<String, Object> metrics = new HashMap<String, Object>();
        
        // Make a quick copy of our runtime metrics data.
        ArrayList<String> instrumentedTasks;
        HashMap<String, LinkedList<Long>> executionTimes;
        HashMap<String, Integer> taskSuccesses, taskFailures, taskRetries;

        synchronized (Metrics.metricsLock) {
            executionTimes = new HashMap<String, LinkedList<Long>>(Metrics.executionTimes);
            taskSuccesses = new HashMap<String, Integer>(Metrics.taskSuccesses);
            taskFailures = new HashMap<String, Integer>(Metrics.taskFailures);
            taskRetries = new HashMap<String, Integer>(Metrics.taskRetries);
            instrumentedTasks = new ArrayList<String>(Metrics.instrumentedTasks);
        }

        // Build a JSON object for each task we've instrumented.
        for (String taskName : instrumentedTasks) {
            JSONObject task = new JSONObject();
            task.put("successes", taskSuccesses.get(taskName));
            task.put("failures", taskFailures.get(taskName));
            task.put("retries", taskRetries.get(taskName));
            task.put("average_time", average(executionTimes.get(taskName)));
            
            metrics.put("task_" + taskName, task);
        }
        
        metrics.put("tasks_instrumented", instrumentedTasks.size());
        metrics.put("alive_since", mx.getUptime() / (new Long("1000")));
        
        return JSONValue.toJSONString(metrics);
    }

    
    // Calculate and return the mean execution time of our sample.
    private float average(LinkedList<Long> times) {
        if (times == null) return 0;
        
        long timeSum = 0;
        for (long time : times) timeSum += time;

        // Execution time is reported in nanoseconds, so we divide by 1,000,000
        // to get to ms. Guard against a divide by zero if no stats are available.
        float result = (times.size() > 0) ?
            timeSum / times.size() / 1000000f : new Float(0);

        return result;
    }
}

