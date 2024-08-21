package com.ubtrobot.mini.speech.framework.demo;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MqttHomeAssistant {
    private static final String MqttClientTag = "MQTT";

    public static String publishAndSubscribe(String payload) {
        try {
            String brokerUrl = "tcp://ia.ic.polyu.edu.hk:1883";
            String publishTopic = "/alphamini/sendtoha";
            String subscribeTopic = "/alphamini/getfromha";

            // Create MQTT client with auto-generated client ID
            MqttClient client = new MqttClient(brokerUrl, UUID.randomUUID().toString(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            client.connect(options);

            // Publish message
            publishMessage(client, publishTopic, payload);

            // Listen for message on subscribe topic
            String receivedMessage = listenForMessage(client, subscribeTopic);

            // Disconnect client
            client.disconnect();

            return receivedMessage;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void publishMessage(MqttClient client, String topic, String payload) throws Exception {
        MqttMessage message = new MqttMessage(payload.getBytes());
        client.publish(topic, message);
        Log.i(MqttClientTag, "Published message: " + payload);
    }

    private static String listenForMessage(MqttClient client, String topic) throws Exception {
        // Use a CountDownLatch to wait for the message
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder receivedMessage = new StringBuilder();

        client.subscribe(topic, (t, msg) -> {
            String payload = new String(msg.getPayload());
            Log.i(MqttClientTag, "Received message: " + payload);
            receivedMessage.append(payload);
            latch.countDown(); // Countdown the latch to signal that a message was received
        });

        // Wait for up to 10 seconds for a message to be received
        latch.await(10, TimeUnit.SECONDS);

        return receivedMessage.toString();
    }
}