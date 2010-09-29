package com.urbanairship.octobot

// AMQP Support
import java.io.IOException
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.QueueingConsumer

// Beanstalk Support
import com.surftools.BeanstalkClient.BeanstalkException
import com.surftools.BeanstalkClient.Job
import com.surftools.BeanstalkClientImpl.ClientImpl
import java.io.PrintWriter
import java.io.StringWriter

import org.json.JSONObject
import org.json.JSONTokener
import org.apache.log4j.Logger
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPubSub


// This thread opens a streaming connection to a queue, which continually
// pushes messages to Octobot queue workers. The tasks contained within these
// messages are invoked, then acknowledged and removed from the queue.

class QueueConsumer(val queue: Queue) extends Runnable {

    var channel: Channel = null
    var connection: Connection = null
    var consumer: QueueingConsumer = null

    val logger = Logger.getLogger("Queue Consumer")
    val enableEmailErrors = Settings.getAsBoolean("Octobot", "email_enabled")

    // Fire up the appropriate queue listener and begin invoking tasks!.
    override def run() {
        if (queue.queueType.equals("amqp")) {
            channel = getAMQPChannel(queue)
            consumeFromAMQP()
        } else if (queue.queueType.equals("beanstalk")) {
            consumeFromBeanstalk()
        } else if (queue.queueType.equals("redis")) {
            consumeFromRedis()
        } else {
            logger.error("Invalid queue type specified: " + queue.queueType)
        }
    }


    // Attempts to register to receive streaming messages from RabbitMQ.
    // In the event that RabbitMQ is unavailable the call to getChannel()
    // will attempt to reconnect. If it fails, the loop simply repeats.
    def consumeFromAMQP() {

        while (true) {
            var task: QueueingConsumer.Delivery = null
            try { task = consumer.nextDelivery() }
            catch {
              case ex: Exception => {
                logger.error("Error in AMQP connection reconnecting.", ex)
                channel = getAMQPChannel(queue)
              }
            }

            // If we've got a message, fetch the body and invoke the task.
            // Then, send an acknowledgement back to RabbitMQ that we got it.
            if (task != null && task.getBody() != null) {
                invokeTask(new String(task.getBody()))
                try { channel.basicAck(task.getEnvelope().getDeliveryTag(), false) }
                catch { 
                  case ex: IOException => { logger.error("Error ack'ing message.", ex) }
                }
            }
        }
    }


    // Attempt to register to receive messages from Beanstalk and invoke tasks.
    def consumeFromBeanstalk() {
        var beanstalkClient = new ClientImpl(queue.host, queue.port)
        beanstalkClient.watch(queue.queueName)
        beanstalkClient.useTube(queue.queueName)
        logger.info("Connected to Beanstalk waiting for jobs.")

        while (true) {
            var job: Job = null
            try { job = beanstalkClient.reserve(1) }
            catch {
              case ex: BeanstalkException => {
                logger.error("Beanstalk connection error.", ex)
                beanstalkClient = Beanstalk.getBeanstalkChannel(queue.host, 
                        queue.port, queue.queueName)                
              }
            }

            if (job != null) {
                val message = new String(job.getData())

                try { invokeTask(message) }
                catch {
                  case ex: Exception => {
                    logger.error("Error handling message.", ex)
                  }
                }

                try { beanstalkClient.delete(job.getJobId()) }
                catch {
                  case ex: BeanstalkException => {
                    logger.error("Error sending message receipt.", ex)
                    beanstalkClient = Beanstalk.getBeanstalkChannel(queue.host, 
                        queue.port, queue.queueName) 
                  }
                }
            }
        }
    }


    def consumeFromRedis() {
        logger.info("Connecting to Redis...")
        var jedis = new Jedis(queue.host, queue.port)
        try {
            jedis.connect()
        } catch {
          case ex: IOException => {
            logger.error("Unable to connect to Redis.", ex)
          }
        }

        logger.info("Connected to Redis.")

        jedis.subscribe(new JedisPubSub() {
    	    override def onMessage(channel: String, message: String) {
    		    invokeTask(message)
    	    }

          override def onPMessage(string: String, string1: String, string2: String) {
              logger.info("onPMessage Triggered - Not implemented.")
          }

          override def onSubscribe(string: String, i: Int) {
            logger.info("onSubscribe called - Not implemented.")
          }

          override def onUnsubscribe(string: String, i: Int) {
            logger.info("onUnsubscribe Called - Not implemented.")
          }

          override def onPUnsubscribe(string: String, i: Int) {
            logger.info("onPUnsubscribe called - Not implemented.")
          }

          override def onPSubscribe(string: String, i: Int) {
            logger.info("onPSubscribe Triggered - Not implemented.")
          }
	    }, queue.queueName)
    }


  // Invokes a task based on the name of the task passed in the message via
  // reflection, accounting for non-existent tasks and errors while running.
  def invokeTask(rawMessage: String) : Boolean = {
      var taskName = ""
      var message : JSONObject = null
      var retryCount = 0
      var retryTimes = 0

      val startedAt = System.nanoTime()
      var errorMessage: String = null
      var lastException: Throwable = null
      var executedSuccessfully = false

      while (!executedSuccessfully && retryCount < retryTimes + 1) {
          if (retryCount > 0)
              logger.info("Retrying task. Attempt " + retryCount + " of " + retryTimes)

          try {
              message = new JSONObject(new JSONTokener(rawMessage))
              taskName = message.get("task").asInstanceOf[String]
              if (message.has("retries"))
                  retryTimes = message.get("retries").asInstanceOf[Int]
          } catch {
            case ex: Exception => {
              logger.error("Error: Invalid message received: " + rawMessage, ex)
              return executedSuccessfully              
            }
          }

          // Locate the task, then invoke it, supplying our message.
          // Cache methods after lookup to avoid unnecessary reflection lookups.
          try {
              TaskExecutor.execute(taskName, message)
              executedSuccessfully = true
          } catch {
            case ex: ClassNotFoundException => {
              lastException = ex
              errorMessage = "Error: Task requested not found: " + taskName
              logger.error(errorMessage)              
            } case ex: NoClassDefFoundError => {
              lastException = ex
              errorMessage = "Error: Task requested not found: " + taskName
              logger.error(errorMessage, ex)              
            } case ex: NoSuchMethodException => {
              lastException = ex
              errorMessage = "Error: Task requested does not have a static run method."
              logger.error(errorMessage, ex)
            } case ex: Throwable => {
              lastException = ex
              errorMessage = "An error occurred while running the task."
              logger.error(errorMessage, ex)              
            }
          }
        
          if (!executedSuccessfully) retryCount += 1
      }

      // Deliver an e-mail error notification if enabled.
      if (enableEmailErrors && !executedSuccessfully) {
          val email = "Error running task: " + taskName + ".\n\n" +
              "Attempted executing " + retryCount.toString + " times as specified.\n\n" +
              "The original input was: \n\n" + rawMessage + "\n\n" +
              "Here's the error that resulted while running the task:\n\n" +
              stackToString(lastException)

          MailQueue.put(email)
      }

      val finishedAt = System.nanoTime()
      Metrics.update(taskName, finishedAt - startedAt, executedSuccessfully, retryCount)
    
      return executedSuccessfully
  }

    // Opens up a connection to RabbitMQ, retrying every five seconds
    // if the queue server is unavailable.
    def getAMQPChannel(queue: Queue) : Channel = {
        var attempts = 0
        var channel: Channel = null
        logger.info("Opening connection to AMQP " + queue.vhost + " "  + queue.queueName + "...")

        while (true) {
            attempts += 1
            logger.debug("Attempt #" + attempts)
            try {
                connection = new RabbitMQ(queue).getConnection()
                channel = connection.createChannel()
                consumer = new QueueingConsumer(channel)
                channel.exchangeDeclare(queue.queueName, "direct", true)
                channel.queueDeclare(queue.queueName, true, false, false, null)
                channel.queueBind(queue.queueName, queue.queueName, queue.queueName)
                channel.basicConsume(queue.queueName, false, consumer)
                logger.info("Connected to RabbitMQ")
                return channel
            } catch {
              case ex: Exception => {
                logger.error("Cannot connect to AMQP. Retrying in 5 sec.", ex)
                Thread.sleep(1000 * 5)
              }
            }
        }

        return channel
    }

    // Converts a stacktrace from task invocation to a string for error logging.
    def stackToString(e: Throwable) : String = {
        if (e == null) return "(Null)"

        val stringWriter = new StringWriter()
        val printWriter = new PrintWriter(stringWriter)

        e.printStackTrace(printWriter)
        return stringWriter.toString()
    }
}

