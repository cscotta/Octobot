package com.urbanairship.octobot

import java.util.List
import java.util.HashMap
import java.lang.reflect.Method
import java.lang.reflect.InvocationTargetException

import org.apache.log4j.Logger
import org.apache.log4j.PropertyConfigurator
import org.apache.log4j.BasicConfigurator
import scala.collection.JavaConversions._

// The fun starts here!

// This class is the main entry point to the application.
// It initializes (a) queue consumer thread(s) reponsible for
// receiving and passing messages on to tasks for execution.

object Octobot extends Application {

    val logger = Logger.getLogger("Octobot")

    override def main(args : Array[String]) {

        // Initialize logging from a log4j configuration file.
        val configFile = System.getProperty("log4j.configuration")
        if (configFile != null && !configFile.equals("")) {
            PropertyConfigurator.configure(configFile)
        } else {
            BasicConfigurator.configure()
            logger.warn("log4j.configuration not set - logging to stdout.")
        }

        // If a startup hook is configured, call it before launching workers.
        val startupHook = Settings.get("Octobot", "startup_hook")
        if (startupHook != null && !startupHook.equals(""))
            launchStartupHook(startupHook)

        // If a shutdown hook is configured, register it.
        val shutdownHook = Settings.get("Octobot", "shutdown_hook")
        if (shutdownHook != null && !shutdownHook.equals(""))
            registerShutdownHook(shutdownHook)

        val enableEmailErrors = Settings.getAsBoolean("Octobot", "email_enabled")
        if (enableEmailErrors) {
            logger.info("Launching email notification queue...")
            new Thread(MailQueue, "Email Queue").start()
        }

        logger.info("Launching Introspector...")
        new Thread(new Introspector(), "Introspector").start()
        
        logger.info("Launching Workers...")
        var queues: List[HashMap[String, Any]] = null
        try {
            queues = getQueues()
        } catch {
          case ex : NullPointerException => {
            logger.fatal("Error: No valid queues found in Settings. Exiting.")
            throw new Error("Error: No valid queues found in Settings. Exiting.")            
          }
        }

        // Start a thread for each queue Octobot is configured to listen on.
        queues.foreach { queueConf => 
          // Fetch the number of workers to spawn and their priority.
          val numWorkers = Settings.getIntFromYML(queueConf.get("workers"), 1)
          val priority = Settings.getIntFromYML(queueConf.get("priority"), 5)

          val queue = new Queue(queueConf)

          // Spawn worker threads for each queue in our configuration.
          for (i <- 0 until numWorkers) {
            var consumer = new QueueConsumer(queue)
            var worker = new Thread(consumer, "Worker")

            logger.info("Attempting to connect to " + queueConf.get("protocol") +
                " queue: " + queueConf.get("name") + " with priority " +
                priority + "/10 " + "(Worker " + (i+1) + "/" + numWorkers + ").")

            worker.setPriority(priority)
            worker.start()
          }
        }
        
        logger.info("Octobot ready to rock!")
    }


    // Invokes a startup hook registered from the YML config on launch.
    def launchStartupHook(className: String) {
      logger.info("Calling Startup Hook: " + className)

      try {
          val startupHook = Class.forName(className)
          val method = startupHook.getMethod("run")
          method.invoke(startupHook.newInstance(), null)
      } catch {
        case ex: ClassNotFoundException => {
          logger.error("Could not find class: " + className + " for the " +
              "startup hook specified. Please ensure that it exists in your" +
              " classpath and launch Octobot again. Continuing without" +
              " executing this hook...")            
        } case ex: NoSuchMethodException => {
          logger.error("Your startup hook: " + className + " does not " +
              " properly implement the Runnable interface. Your startup hook must " +
              " contain a method with the signature: public void run()." +
              " Continuing without executing this hook...")            
        } case ex: InvocationTargetException => {
          logger.error("Your startup hook: " + className + " caused an error" +
              " in execution. Please correct this error and re-launch Octobot." +
              " Continuing without executing this hook...", ex.getCause())
        } case ex: Exception => {
          logger.error("Your startup hook: " + className + " caused an unknown" +
              " error. Please see the following stacktrace for information.", ex)
        }
      }
    }


    // Registers a Runnable to be ran as a shutdown hook when Octobot stops.
    def registerShutdownHook(className: String) {
      logger.info("Registering Shutdown Hook: " + className)

      try {
          val startupHook = Class.forName(className)
          Runtime.getRuntime().addShutdownHook(new Thread(startupHook.newInstance().asInstanceOf[Runnable]))
      } catch {
        case ex: ClassNotFoundException => {
          logger.error("Could not find class: " + className + " for the " +
              "shutdown hook specified. Please ensure that it exists in your" +
              " classpath and launch Octobot again. Continuing without" +
              " registering this hook...")            
        } case ex: ClassCastException => {
          logger.error("Your shutdown hook: " + className + " could not be "
              + "registered due because it does not implement the Runnable "
              + "interface. Continuing without registering this hook...")            
        } case ex: Exception => {
          logger.error("Your shutdown hook: " + className + " could not be "
              + "registered due to an unknown error. Please see the " +
              "following stacktrace for debugging information.", ex)
        }
      }
    }

    def getQueues() : List[HashMap[String, Any]] = {
        Settings.configuration.get("Octobot").get("queues").asInstanceOf[List[HashMap[String, Any]]]
    }
}

