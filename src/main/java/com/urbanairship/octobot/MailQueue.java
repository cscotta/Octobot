package com.urbanairship.octobot;

import org.apache.log4j.Logger;
import java.util.concurrent.ArrayBlockingQueue;

// E-mail Imports
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;


// This singleton class provides an internal queue allowing us to asynchronously
// send email notifications rather than processing them in main app loop.

public class MailQueue implements Runnable {

    private static final Logger logger = Logger.getLogger("Email Queue");
    private static String from = Settings.get("Octobot", "email_from");
    private static String recipient = Settings.get("Octobot", "email_to");
    private static String server = Settings.get("Octobot", "email_server");
    private static String username = Settings.get("Octobot", "email_username");
    private static String password = Settings.get("Octobot", "email_password");
    private static Integer port = Settings.getAsInt("Octobot", "email_port");
    private static Boolean useSSL = Settings.getAsBoolean("Octobot", "email_ssl");
    private static Boolean useAuth = Settings.getAsBoolean("Octobot", "email_auth");

    // This internal queue is backed by an ArrayBlockingQueue. By specifying the
    // number of messages to be held here before the queue blocks (below), we
    // provide ourselves a safety threshold in terms of how many messages could
    // be backed up before we force the delivery of all current waiting messages.

    private static ArrayBlockingQueue<String> messages;

    // Initialize the queue's singleton instance.
    private static final MailQueue INSTANCE = new MailQueue();

    private MailQueue() {
        messages = new ArrayBlockingQueue<String>(100);
    }

    public static MailQueue get() {
        return INSTANCE;
    }

    public static void put(String message) throws InterruptedException {
        messages.put(message);
    }

    public static int size() {
        return messages.size();
    }

    public static int remainingCapacity() {
        return messages.remainingCapacity();
    }

    // As this thread runs, it consumes messages from the internal queue and
    // delivers each to the recipients configured in the YML file.
    public void run() {
        
        if (!validSettings()) {
            logger.error("Email settings invalid; check your configuration.");
            return;
        }

        while (true) {
            try {
                String message = messages.take();
                deliverMessage(message);
            } catch (InterruptedException e) {
                // Pass
            }
        }
    }

    // Delivers email error notificiations.
    private void deliverMessage(String message) {

        logger.info("Sending error notification to: " + recipient);

        try {
            MimeMessage email = prepareEmail();
            email.setFrom(new InternetAddress(from));
            email.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));

            email.setSubject("Task Error Notification");
            email.setText(message);

            // Send message
            Transport.send(email);
            logger.info("Sent error e-mail to " + recipient + ". "
                + "Message: \n\n" + message);

        } catch (MessagingException e) {
            logger.error("Error delivering error notification.", e);
        }
    }


    // Prepares a sendable email object based on Octobot's SMTP, SSL, and
    // Authentication configuration.
    private MimeMessage prepareEmail() {
        // Prepare our configuration.
        Properties properties = System.getProperties();
        properties.setProperty("mail.smtp.host", server);
        properties.put("mail.smtp.auth", "true");
        Session session = null;

        // Configure SSL.
        if (useSSL) {
            properties.put("mail.smtp.socketFactory.port", port);
            properties.put("mail.smtp.starttls.enable","true");
            properties.put("mail.smtp.socketFactory.fallback", "false");
            properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }

        // Configure authentication.
        if (useAuth) {
            properties.setProperty("mail.smtp.submitter", username);
            Authenticator authenticator = new Authenticator(username, password);
            session = Session.getInstance(properties, authenticator);
        } else {
            session = Session.getDefaultInstance(properties);
        }

        return new MimeMessage(session);
    }


    // Provides an SMTP authenticator for messages sent with auth.
    private class Authenticator extends javax.mail.Authenticator {
        private PasswordAuthentication authentication;

        public Authenticator(String user, String pass) {
            String username = user;
            String password = pass;
            authentication = new PasswordAuthentication(username, password);
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return authentication;
        }
    }

    private boolean validSettings() {
        boolean result = true;

        Object[] settings = new Object[]{from, recipient, server, port};

        // Validate base settings.
        for (Object setting : settings)
            if (setting == null) result = false;

        // Validate authentication.
        if (useAuth && (username == null || password == null))
            result = false;

        return result;
    }

}

