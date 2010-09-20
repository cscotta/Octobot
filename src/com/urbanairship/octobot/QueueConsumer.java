package com.urbanairship.octobot;

// AMQP Support
import java.io.IOException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.QueueingConsumer;

// Beanstalk Support
import com.surftools.BeanstalkClient.BeanstalkException;
import com.surftools.BeanstalkClient.Job;
import com.surftools.BeanstalkClientImpl.ClientImpl;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;


// This thread opens a streaming connection to a queue, which continually
// pushes messages to Octobot queue workers. The tasks contained within these
// messages are invoked, then acknowledged and removed from the queue.

public class QueueConsumer implements Runnable {

    Queue queue = null;
    Channel channel = null;
    Connection connection = null;
    QueueingConsumer consumer = null;

    private final Logger logger = Logger.getLogger("Queue Consumer");
    private boolean enableEmailErrors = Settings.getAsBoolean("Octobot", "email_enabled");

    // Initialize the consumer with a queue object (AMQP, Beanstalk, or Redis).
    public QueueConsumer(Queue queue) {
        this.queue = queue;
    }

    // Fire up the appropriate queue listener and begin invoking tasks!.
    public void run() {
        if (queue.queueType.equals("amqp")) {
            channel = getAMQPChannel(queue);
            consumeFromAMQP();
        } else if (queue.queueType.equals("beanstalk")) {
            consumeFromBeanstalk();
        } else if (queue.queueType.equals("redis")) {
            consumeFromRedis();
        } else {
            logger.error("Invalid queue type specified: " + queue.queueType);
        }
    }


    // Attempts to register to receive streaming messages from RabbitMQ.
    // In the event that RabbitMQ is unavailable the call to getChannel()
    // will attempt to reconnect. If it fails, the loop simply repeats.
    private void consumeFromAMQP() {

        while (true) {
            QueueingConsumer.Delivery task = null;
            try { task = consumer.nextDelivery(); }
            catch (Exception e){
                logger.error("Error in AMQP connection; reconnecting.", e);
                channel = getAMQPChannel(queue);
                continue;
            }

            // If we've got a message, fetch the body and invoke the task.
            // Then, send an acknowledgement back to RabbitMQ that we got it.
            if (task != null && task.getBody() != null) {
                invokeTask(new String(task.getBody()));
                try { channel.basicAck(task.getEnvelope().getDeliveryTag(), false); }
                catch (IOException e) { logger.error("Error ack'ing message.", e); }
            }
        }
    }


    // Attempt to register to receive messages from Beanstalk and invoke tasks.
    private void consumeFromBeanstalk() {
        ClientImpl beanstalkClient = new ClientImpl(queue.host, queue.port);
        beanstalkClient.watch(queue.queueName);
        beanstalkClient.useTube(queue.queueName);
        logger.info("Connected to Beanstalk; waiting for jobs.");

        while (true) {
            Job job = null;
            try { job = beanstalkClient.reserve(1); }
            catch (BeanstalkException e) {
                logger.error("Beanstalk connection error.", e);
                beanstalkClient = Beanstalk.getBeanstalkChannel(queue.queueName);
                continue;
            }

            if (job != null) {
                String message = new String(job.getData());

                try { invokeTask(message); }
                catch (Exception e) { logger.error("Error handling message.", e); }

                try { beanstalkClient.delete(job.getJobId()); }
                catch (BeanstalkException e) {
                    logger.error("Error sending message receipt.", e);
                    beanstalkClient = Beanstalk.getBeanstalkChannel(queue.queueName);
                }
            }
        }
    }


    private void consumeFromRedis() {
        logger.info("Connecting to Redis...");
        Jedis jedis = new Jedis(queue.host, queue.port);
        try {
            jedis.connect();
        } catch (IOException e) {
            logger.error("Unable to connect to Redis.", e);
        }

        logger.info("Connected to Redis.");

        jedis.subscribe(new JedisPubSub() {
            @Override
    	    public void onMessage(String channel, String message) {
    		    invokeTask(message);
    	    }

            @Override
            public void onPMessage(String string, String string1, String string2) {
                logger.info("onPMessage Triggered");
            }

            @Override
            public void onSubscribe(String string, int i) {
                logger.info("onSubscribe called.");
            }

            @Override
            public void onUnsubscribe(String string, int i) {
                logger.info("onUnsubscribe Called");
            }

            @Override
            public void onPUnsubscribe(String string, int i) {
                logger.info("onPUnsubscribe called.");
            }

            @Override
            public void onPSubscribe(String string, int i) {
                logger.info("onPSubscribe Triggered");
            }
	    }, queue.queueName);

    }


// Invokes a task based on the name of the task passed in the message via
// reflection, accounting for non-existent tasks and errors while running.
public boolean invokeTask(String rawMessage) {
    String taskName = "";
    JSONObject message;
    int retryCount = 0;
    long retryTimes = 0;

    long startedAt = System.nanoTime();
    String errorMessage = null;
    Throwable lastException = null;
    boolean executedSuccessfully = false;

    while (retryCount < retryTimes + 1) {
        if (retryCount > 0)
            logger.info("Retrying task. Attempt " + retryCount + " of " + retryTimes);

        try {
            message = (JSONObject) JSONValue.parse(rawMessage);
            taskName = (String) message.get("task");
            if (message.containsKey("retries"))
                retryTimes = (Long) message.get("retries");
        } catch (Exception e) {
            logger.error("Error: Invalid message received: " + rawMessage);
            return executedSuccessfully;
        }

        // Locate the task, then invoke it, supplying our message.
        // Cache methods after lookup to avoid unnecessary reflection lookups.
        try {

            TaskExecutor.execute(taskName, message);
            executedSuccessfully = true;

        } catch (ClassNotFoundException e) {
            lastException = e;
            errorMessage = "Error: Task requested not found: " + taskName;
            logger.error(errorMessage);
        } catch (NoSuchMethodException e) {
            lastException = e;
            errorMessage = "Error: Task requested does not have a static run method.";
            logger.error(errorMessage);
        } catch (Exception e) {
            lastException = e;
            errorMessage = "An error occurred while running the task.";
            logger.error(errorMessage, e);
        }
        
        if (executedSuccessfully) break;
        else retryCount++;
    }

    // Deliver an e-mail error notification if enabled.
    if (enableEmailErrors && !executedSuccessfully) {
        String email = "Error running task: " + taskName + ".\n\n"
            + "Attempted executing " + retryCount + " times as specified.\n\n"
            + "The original input was: \n\n" + rawMessage + "\n\n"
            + "Here's the error that resulted while running the task:\n\n"
            + stackToString(lastException);

        try { MailQueue.put(email); }
        catch (InterruptedException e) { }
    }

    long finishedAt = System.nanoTime();
    Metrics.update(taskName, finishedAt - startedAt, executedSuccessfully, retryCount);
    
    return executedSuccessfully;
}

    // Opens up a connection to RabbitMQ, retrying every five seconds
    // if the queue server is unavailable.
    private Channel getAMQPChannel(Queue queue) {
        int attempts = 0;
        logger.info("Opening connection to AMQP / " + queue.queueName + "...");

        while (true) {
            attempts++;
            logger.debug("Attempt #" + attempts);
            try {
                connection = new RabbitMQ(queue).getConnection();
                channel = connection.createChannel();
                consumer = new QueueingConsumer(channel);
                channel.exchangeDeclare(queue.queueName, "direct", true);
                channel.queueDeclare(queue.queueName, true, false, false, null);
                channel.queueBind(queue.queueName, queue.queueName, queue.queueName);
                channel.basicConsume(queue.queueName, false, consumer);
                logger.info("Connected to RabbitMQ");
                return channel;
            } catch (Exception e) {
                logger.error("Cannot connect to AMQP. Retrying in 5 sec.", e);
                try { Thread.sleep(1000 * 5); }
                catch (InterruptedException ex) { }
            }
        }
    }

    // Converts a stacktrace from task invocation to a string for error logging.
    public String stackToString(Throwable e) {
        if (e == null) return "(Null)";

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        e.printStackTrace(printWriter);
        return stringWriter.toString();
    }
}

