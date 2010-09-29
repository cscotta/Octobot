package com.urbanairship.octobot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class Metrics {

    // Keep track of all tasks we've seen executed.
    protected static final ArrayList<String> instrumentedTasks = new ArrayList<String>();
    
    // Keep track of average task throughput (last 10k runs per task).
    protected static final HashMap<String, LinkedList<Long>> executionTimes =
        new HashMap<String, LinkedList<Long>>();

    // Keep track of total successes by task.
    protected static final HashMap<String, Integer> taskSuccesses =
        new HashMap<String, Integer>();

    // Keep track of total failures by task.
    protected static final HashMap<String, Integer> taskFailures =
        new HashMap<String, Integer>();
    
    // Keep track of total retries by task.
    protected static final HashMap<String, Integer> taskRetries =
        new HashMap<String, Integer>();

    protected static final Object metricsLock = new Object();


    // Updates internal metrics following task execution.
    public static void update(String task, long time,
            boolean status, int retries) {

        synchronized(metricsLock) {
            if (!instrumentedTasks.contains(task)) instrumentedTasks.add(task);
            
            updateExecutionTimes(task, time);
            updateTaskRetries(task, retries);
            updateTaskResults(task, status);
        }
    }


    // Update the list of execution times, keeping the last 10,000 per task.
    private static void updateExecutionTimes(String task, long time) {
        if (!executionTimes.containsKey(task)) {
            LinkedList<Long> timeList = new LinkedList<Long>();
            timeList.addFirst(time);
            executionTimes.put(task, timeList);
        } else {
            LinkedList<Long> timeList = executionTimes.get(task);
            if (timeList.size() == 10000) timeList.removeLast();
            timeList.addFirst(time);
            executionTimes.put(task, timeList);
        }
    }


    // Update the number of times this task has been retried.
    private static void updateTaskRetries(String task, int retries) {
        if (retries > 0) {
            if (!taskRetries.containsKey(task)) {
                taskRetries.put(task, retries);
            } else {
                int retriesForTask = taskRetries.get(task);
                retriesForTask += retries;
                taskRetries.put(task, retriesForTask);
            }
        }   
    }


    // Update the number of times this task has succeeded or failed.
    private static void updateTaskResults(String task, boolean status) {
        if (status == true) {
            if (!taskSuccesses.containsKey(task)) {
                taskSuccesses.put(task, 1);
            } else {
                int success = taskSuccesses.get(task);
                taskSuccesses.put(task, success + 1);
            }
        } else {
            if (!taskFailures.containsKey(task)) {
                taskFailures.put(task, 1);
            } else {
                int failure = taskFailures.get(task);
                taskFailures.put(task, failure + 1);
            }
        }
    }

}

