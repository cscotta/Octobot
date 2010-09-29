package com.urbanairship.octobot

import java.util.HashMap
import java.util.LinkedList
import java.net.Socket
import java.net.ServerSocket

import java.io.OutputStream
import java.io.IOException

import java.lang.management.RuntimeMXBean
import java.lang.management.ManagementFactory
import java.util.ArrayList

import org.json.JSONObject
import org.json.JSONTokener

import org.apache.log4j.Logger
import scala.collection.JavaConversions._


// This class provides some basic instrumentation for Octobot.
// It provides a simple socket server listening on an admin port (1228
// by default). Upon receiving a connection, it prints out a JSON string
// of information such as such as tasks processed per second, total successes
// and failures, successes and failures per task / per queue.

class Introspector() extends Runnable {

    var server: ServerSocket = null
    val mx = ManagementFactory.getRuntimeMXBean()
    var port = Settings.getAsInt("Octobot", "metrics_port")
    val logger = Logger.getLogger("Introspector")

    override def run() {
        if (port < 1) port = 1228
        try { server = new ServerSocket(port) }
        catch {
          case ex: IOException => {
            logger.error("Introspector: Unable to listen on port: " + port +
                    ". Introspector will be unavailable on this instance.")
            return
          }
        }

        logger.info("Introspector launched on port: " + port)
        
        while (true) {
          try {
              val socket = server.accept()
              val oos = socket.getOutputStream()
              oos.write(introspect().getBytes())
              oos.close()
              socket.close()
          } catch {
            case ex: IOException => {
              logger.error("Error in accepting Introspector connection. "
                      + "Introspector thread shutting down.", ex)
              return
            }
          }
        }
    }

    // Assembles metrics for each task and returns a JSON string.
    // Warnings suppressed are from building the JSON itself.
    def introspect() : String = {
        val metrics = new HashMap[String, Object]
        
        // Make a quick copy of our runtime metrics data.
        var instrumentedTasks: ArrayList[String] = null
        var executionTimes: HashMap[String, LinkedList[Long]] = null
        var taskSuccesses: HashMap[String, Int] = null
        var taskFailures: HashMap[String, Int] = null
        var taskRetries: HashMap[String, Int] = null

        Metrics.metricsLock.synchronized {
            executionTimes = new HashMap[String, LinkedList[Long]](Metrics.executionTimes)
            taskSuccesses = new HashMap[String, Int](Metrics.taskSuccesses)
            taskFailures = new HashMap[String, Int](Metrics.taskFailures)
            taskRetries = new HashMap[String, Int](Metrics.taskRetries)
            instrumentedTasks = new ArrayList[String](Metrics.instrumentedTasks)
        }

        // Build a JSON object for each task we've instrumented.
        instrumentedTasks.foreach { taskName =>
          var task = new JSONObject()
          task.put("successes", taskSuccesses.get(taskName))
          task.put("failures", taskFailures.get(taskName))
          task.put("retries", taskRetries.get(taskName))
          task.put("average_time", average(executionTimes.get(taskName)))
          metrics.put("task_" + taskName, task)
        }

        metrics.put("tasks_instrumented", instrumentedTasks.size().asInstanceOf[AnyRef])
        metrics.put("alive_since", (mx.getUptime() / 1000).asInstanceOf[AnyRef])
        
        return new JSONObject(metrics).toString
    }

    
    // Calculate and return the mean execution time of our sample.
    def average(times: LinkedList[Long]) : Float = {
        if (times == null) return 0.toFloat
        
        var timeSum: Long = 0
        times.foreach(t => timeSum += t)

        // Execution time is reported in nanoseconds, so we divide by 1,000,000
        // to get to ms. Guard against a divide by zero if no stats are available.
        val result = if (times.size() > 0)
            timeSum / times.size() / 1000000f else 0.toFloat

        return result
    }
}

