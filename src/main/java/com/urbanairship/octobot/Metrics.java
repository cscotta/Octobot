package com.urbanairship.octobot;

import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class Metrics {

    protected static final MetricsRegistry registry = new MetricsRegistry();

    // Updates internal metrics following task execution.
    public static void update(String task, long time, boolean status, int retries) {
        updateExecutionTimes(task, time);
        updateTaskRetries(task, retries);
        updateTaskResults(task, status);
    }


    // Update the list of execution times, keeping the last 10,000 per task.
    private static void updateExecutionTimes(String task, long time) {
        MetricName timerName = new MetricName("Octobot", "Metrics", task + "Timer");
        Timer timer = registry.newTimer(timerName, TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
        timer.update(time, TimeUnit.MILLISECONDS);
    }


    // Update the number of times this task has been retried.
    private static void updateTaskRetries(String task, int retries) {
        MetricName counterRetriesName = new MetricName("Octobot", "Metrics", task + "Retries");
        Counter counterRetries = registry.newCounter(counterRetriesName);
        counterRetries.inc();
    }


    // Update the number of times this task has succeeded or failed.
    private static void updateTaskResults(String task, boolean status) {
        if (status == true) {
            MetricName counterSuccessName = new MetricName("Octobot", "Metrics", task + "Success");
            Counter counterSuccess = registry.newCounter(counterSuccessName);
            counterSuccess.inc();
        } else {
            MetricName counterFailureName = new MetricName("Octobot", "Metrics", task + "Failure");
            Counter counterFailure = registry.newCounter(counterFailureName);
            counterFailure.inc();
        }
    }
    
}

