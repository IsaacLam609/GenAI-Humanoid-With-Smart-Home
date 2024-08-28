package com.ubtrobot.mini.speech.framework.demo;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Secrets {
    private static Secrets instance;
    private Properties properties;

    private Secrets() {
        properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("secrets.properties")) {
            if (input == null) {
                Log.d("Secrets","Unable to find secrets.properties");
                return;
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static Secrets getInstance() {
        if (instance == null) {
            instance = new Secrets();
        }
        return instance;
    }

    public static String getSubscriptionKey() {
        return getInstance().properties.getProperty("subscription_key");
    }

    public static String getServiceRegion() {
        return getInstance().properties.getProperty("service_region");
    }

    public static String getMQTTServerUrl() {
        return getInstance().properties.getProperty("mqtt_server_url");
    }
}
