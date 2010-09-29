package com.urbanairship.octobot

import java.util.HashMap
import java.lang.reflect.Method
import org.json.JSONObject

object TaskExecutor {

  val taskCache = new HashMap[String, Method]

  def execute(taskName: String, message: JSONObject) {
    var method: Method = null

    if (taskCache.containsKey(taskName)) {
      method = taskCache.get(taskName)
    } else {
      val task = Class.forName(taskName)
      val klass = new JSONObject().getClass
      method = task.getMethod("run", klass)
      taskCache.put(taskName, method)
    }
    
    method.invoke(null, message)
  }

}
