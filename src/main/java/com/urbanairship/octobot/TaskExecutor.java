package com.urbanairship.octobot;

import java.util.HashMap;
import java.lang.reflect.Method;
import org.json.simple.JSONObject;
import java.lang.reflect.InvocationTargetException;

public class TaskExecutor {

    private static final HashMap<String, Method> taskCache =
            new HashMap<String, Method>();

    @SuppressWarnings("unchecked")
    public static void execute(String taskName, JSONObject message) 
            throws ClassNotFoundException,
                   NoSuchMethodException,
                   IllegalAccessException,
                   InvocationTargetException {

        Method method = null;

        if (taskCache.containsKey(taskName)) {
            method = taskCache.get(taskName);
        } else {
            Class task = Class.forName(taskName);
            method = task.getMethod("run", new Class[]{ JSONObject.class });
            taskCache.put(taskName, method);
        }

        method.invoke(null, new Object[]{ message });
    }

}
