package com.urbanairship.octobot

import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedList

object Metrics {

    // Keep track of all tasks we've seen executed.
    val instrumentedTasks = new ArrayList[String]()
    
    // Keep track of average task throughput (last 10k runs per task).
    val executionTimes = new HashMap[String, LinkedList[Long]]()

    // Keep track of total successes by task.
    val taskSuccesses = new HashMap[String, Int]()

    // Keep track of total failures by task.
    val taskFailures = new HashMap[String, Int]()
    
    // Keep track of total retries by task.
    val taskRetries = new HashMap[String, Int]()

    val metricsLock = new Object()

    
    // Updates internal metrics following task execution.
    def update(task: String, time: Long, status: Boolean, retries: Int) {
        metricsLock.synchronized {
            if (!instrumentedTasks.contains(task)) instrumentedTasks.add(task)

            updateExecutionTimes(task, time)
            updateTaskRetries(task, retries)
            updateTaskResults(task, status)
        }
    }


    // Update the list of execution times, keeping the last 10,000 per task.
    def updateExecutionTimes(task: String, time: Long) {
        if (!executionTimes.containsKey(task)) {
            val timeList = new LinkedList[Long]()
            timeList.addFirst(time)
            executionTimes.put(task, timeList)
        } else {
            val timeList = executionTimes.get(task)
            if (timeList.size() == 10000) timeList.removeLast()
            timeList.addFirst(time)
            executionTimes.put(task, timeList)
        }
    }


    // Update the number of times this task has been retried.
    def updateTaskRetries(task: String, retries: Int) {
        if (retries > 0) {
            if (!taskRetries.containsKey(task)) {
                taskRetries.put(task, retries)
            } else {
                var retriesForTask = taskRetries.get(task)
                retriesForTask += retries
                taskRetries.put(task, retriesForTask)
            }
        }   
    }


    // Update the number of times this task has succeeded or failed.
    def updateTaskResults(task: String, status: Boolean) {
        if (status == true) {
            if (!taskSuccesses.containsKey(task)) {
                taskSuccesses.put(task, 1)
            } else {
                var success = taskSuccesses.get(task)
                taskSuccesses.put(task, success + 1)
            }
        } else {
            if (!taskFailures.containsKey(task)) {
                taskFailures.put(task, 1)
            } else {
                var failure = taskFailures.get(task)
                taskFailures.put(task, failure + 1)
            }
        }
    }

}

