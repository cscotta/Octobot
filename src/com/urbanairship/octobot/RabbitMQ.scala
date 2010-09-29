package com.urbanairship.octobot

import org.apache.log4j.Logger
import com.rabbitmq.client.Channel
import java.io.IOException
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory


// This class handles all interfacing with AMQP / RabbitMQ in Octobot.
// It provides basic connection management and returns task channels
// for placing messages into a remote queue.

class RabbitMQ(val host: String, val port: Int, val username: String, val password: String, val vhost: String) {

    val logger = Logger.getLogger("RabbitMQ")
    val factory = new ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername(username)
    factory.setPassword(password)
    factory.setVirtualHost(vhost)
    
    def this(queue: Queue) {
        this(queue.host, queue.port, queue.username, queue.password, queue.vhost)
    }

    // Returns a new connection to an AMQP queue.
    def getConnection() : Connection = {
        return factory.newConnection()
    }

    // Returns a live channel for publishing messages.
    def getTaskChannel() : Channel ={
        var taskChannel: Channel = null

        var attempts = 0
        while (true) {
            attempts += 1
            logger.info("Attempting to connect to queue: attempt " + attempts)
            try {
                val connection = getConnection()
                taskChannel = connection.createChannel()
                return taskChannel
            } catch {
              case ex: IOException => {
                logger.error("Error creating AMQP channel, retrying in 5 sec", ex)
                Thread.sleep(1000 * 5)                
              }
            }
        }

        // This statement will never be reached, but makes the compiler happy.
        return taskChannel
    }
}

