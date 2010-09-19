package com.urbanairship.octobot;

import java.util.List;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.BasicConfigurator;


// The fun starts here!

// This class is the main entry point to the application.
// It initializes (a) queue consumer thread(s) reponsible for
// receiving and passing messages on to tasks for execution.

public class Octobot {

    private static final Logger logger = Logger.getLogger("Octobot");

    public static void main(String[] args) {

        // Initialize logging from a log4j configuration file.
        String configFile = System.getProperty("log4j.configuration");
        if (configFile != null && !configFile.equals("")) {
            PropertyConfigurator.configure(configFile);
        } else {
            BasicConfigurator.configure();
            logger.warn("log4j.configuration not set - logging to stdout.");
        }

        // Force settings to initialize before loading application components.
        Settings.get();

        // If a startup hook is configured, call it before launching workers.
        String startupHook = Settings.get("Octobot", "startup_hook");
        if (startupHook != null && !startupHook.equals(""))
            launchStartupHook(startupHook);

        // If a shutdown hook is configured, register it.
        String shutdownHook = Settings.get("Octobot", "shutdown_hook");
        if (shutdownHook != null && !shutdownHook.equals(""))
            registerShutdownHook(shutdownHook);

        boolean enableEmailErrors = Settings.getAsBoolean("Octobot", "email_enabled");
        if (enableEmailErrors) {
            logger.info("Launching email notification queue...");
            new Thread(MailQueue.get(), "Email Queue").start();
        }

        logger.info("Launching Introspector...");
        new Thread(new Introspector(), "Introspector").start();
        
        logger.info("Launching Workers...");
        List<HashMap<String, Object>> queues = null;
        try {
            queues = getQueues();
        } catch (NullPointerException e) {
            logger.fatal("Error: No valid queues found in Settings. Exiting.");
            throw new Error("Error: No valid queues found in Settings. Exiting.");
        }

        // Start a thread for each queue Octobot is configured to listen on.
        for (HashMap<String, Object> queueConf : queues) {

            // Fetch the number of workers to spawn and their priority.
            int numWorkers = Settings.getIntFromYML(queueConf.get("workers"), 1);
            int priority = Settings.getIntFromYML(queueConf.get("priority"), 5);

            Queue queue = new Queue(queueConf);

            // Spawn worker threads for each queue in our configuration.
            for (int i = 0; i < numWorkers; i++) {
                QueueConsumer consumer = new QueueConsumer(queue);
                Thread worker = new Thread(consumer, "Worker");

                logger.info("Attempting to connect to " + queueConf.get("protocol") +
                    " queue: " + queueConf.get("name") + " with priority " +
                    priority + "/10 " + "(Worker " + (i+1) + "/" + numWorkers + ").");

                worker.setPriority(priority);
                worker.start();
            }
        }

        logger.info("Octobot ready to rock!");
    }


    // Invokes a startup hook registered from the YML config on launch.
    private static void launchStartupHook(String className) {
        logger.info("Calling Startup Hook: " + className);

        try {
            Class<?> startupHook = Class.forName(className);
            Method method = startupHook.getMethod("run", (Class[]) null);
            method.invoke(startupHook.newInstance(), (Object[]) null);
        } catch (ClassNotFoundException e) {
            logger.error("Could not find class: " + className + " for the " +
                "startup hook specified. Please ensure that it exists in your" +
                " classpath and launch Octobot again. Continuing without" +
                " executing this hook...");
        } catch (NoSuchMethodException e) {
            logger.error("Your startup hook: " + className + " does not " +
                " properly implement the Runnable interface. Your startup hook must " +
                " contain a method with the signature: public void run()." +
                " Continuing without executing this hook...");
        } catch (InvocationTargetException e) {
            logger.error("Your startup hook: " + className + " caused an error" +
                " in execution. Please correct this error and re-launch Octobot." +
                " Continuing without executing this hook...", e.getCause());
        } catch (Exception e) {
            logger.error("Your startup hook: " + className + " caused an unknown" +
                " error. Please see the following stacktrace for information.", e);
        }
    }


    // Registers a Runnable to be ran as a shutdown hook when Octobot stops.
    private static void registerShutdownHook(String className) {
        logger.info("Registering Shutdown Hook: " + className);

        try {
            Class startupHook = Class.forName(className);
            Runtime.getRuntime().addShutdownHook(new Thread((Runnable) startupHook.newInstance()));
        } catch (ClassNotFoundException e) {
            logger.error("Could not find class: " + className + " for the " +
                "shutdown hook specified. Please ensure that it exists in your" +
                " classpath and launch Octobot again. Continuing without" +
                " registering this hook...");
        } catch (ClassCastException e) {
            logger.error("Your shutdown hook: " + className + " could not be "
                + "registered due because it does not implement the Runnable "
                + "interface. Continuing without registering this hook...");
        } catch (Exception e) {
            logger.error("Your shutdown hook: " + className + " could not be "
                + "registered due to an unknown error. Please see the " +
                "following stacktrace for debugging information.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<HashMap<String, Object>> getQueues() {
        return (List<HashMap<String, Object>>)
            Settings.configuration.get("Octobot").get("queues");
    }

}

