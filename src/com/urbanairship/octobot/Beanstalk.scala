package com.urbanairship.octobot

import com.surftools.BeanstalkClientImpl.ClientImpl
import org.apache.log4j.Logger

// This class handles all interfacing with a Beanstalk in Octobot.
// It is responsible for connection initialization and management.

object Beanstalk {

    val logger = Logger.getLogger("Beanstalk")

    def getBeanstalkChannel(host: String, port: Int, tube: String) : ClientImpl = {
        var attempts = 0
        var client: ClientImpl = null
        logger.info("Opening connection to Beanstalk tube: '" + tube + "'...")

        while (true) {
            attempts += 1
            logger.debug("Attempt #" + attempts)
            try {
                client = new ClientImpl(host, port)
                client.useTube(tube)
                client.watch(tube)
                logger.info("Connected to Beanstalk")
                return client
            } catch {
              case ex: Exception => {
                logger.error("Unable to connect to Beanstalk. Retrying in 5 seconds", ex)
                Thread.sleep(1000 * 5)                
              }
            }
        }
        
        // This code can never be reached, but the compiler wants it.
        return client
    }

}

