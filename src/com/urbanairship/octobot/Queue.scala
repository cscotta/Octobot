package com.urbanairship.octobot

import java.util.HashMap

class Queue(val queueType: String, val queueName: String, val host: String, val port: Int,
            val username: String, val password: String, val vhost: String) {

  def this(queueType: String, queueName: String, host: String,
    port: Int, username: String, password: String) {
      this(queueType, queueName, host, port, username, password, "/")
    }

  def this(queueType: String, queueName: String, host: String, port: Int) {
    this(queueType.toLowerCase, queueName, host, port, null, null, null)
  }

  def this(config: HashMap[String, Any]) {
    this(config.get("protocol").asInstanceOf[String].toLowerCase,
      config.get("name").asInstanceOf[String],
      config.get("host").asInstanceOf[String],
      config.get("port").toString.toInt,
      config.get("username").asInstanceOf[String],
      config.get("password").asInstanceOf[String],
      config.get("vhost").asInstanceOf[String])
  }

  override def toString() : String = {
    queueType + "/" + queueName + "/" + host + "/" + port + "/" +
      username + "/" + password + "/" + vhost
  }
 
}
