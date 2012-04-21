package com.urbanairship.octobot;

import java.util.HashMap;

public class Queue {

    public String queueType;
    public String queueName;

    public String host;
    public Integer port;
    public String username;
    public String password;
    public String vhost;

    public Queue(String queueType, String queueName, String host, Integer port,
        String username, String password) {
            this.queueType = queueType.toLowerCase();
            this.queueName = queueName;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
            this.vhost = "/";
    }

    public Queue(String queueType, String queueName, String host, Integer port) {
            this.queueType = queueType.toLowerCase();
            this.queueName = queueName;
            this.host = host;
            this.port = port;
    }

    public Queue(HashMap<String, Object> config) {
        this.queueName = (String) config.get("name");
        this.queueType = ((String) config.get("protocol")).toLowerCase();
        this.host = (String) config.get("host");
        this.vhost = (String) config.get("vhost");
        this.username = (String) config.get("username");
        this.password = (String) config.get("password");

        if (config.get("port") != null)
            this.port = Integer.parseInt(((Long) config.get("port")).toString());
    }


    @Override
    public String toString() {
        return queueType + "/" + queueName + "/" + host + "/" + port + "/" +
            username + "/" + password + "/" + vhost;
    }

}

