package com.ubtrobot.mini.speech.framework.demo;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A class to send and receive MQTT messages from Home Assistant.
 */
public class MqttHomeAssistant {
    private static final String MqttClientTag = "MQTT";
    /** MQTT server URL. */
    private static final String ServerUrl = Secrets.getMQTTServerUrl();
    /** Topic for publishing MQTT messages. */
    private static final String PublishTopic = "/alphamini/sendtoha";
    /** Topic for receiving MQTT messages from Home Assistant. */
    private static final String SubscribeTopic = "/alphamini/getfromha";
    /** Duration in seconds to wait for an MQTT message response from Home Assistant. */
    private static final Integer ResponseTimeout = 15;

    /**
     * Publish an MQTT message and wait for a response message from Home Assistant.
     * @param payload the message to be sent to Home Assistant
     * @return the message received as a string
     */
    public static String publishAndSubscribe(String payload) {
        try {
            // Create MQTT client with auto-generated client ID
            MqttClient client = new MqttClient(ServerUrl, UUID.randomUUID().toString(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            client.connect(options);

            publishMessage(client, PublishTopic, payload);
            String receivedMessage = listenForMessage(client, SubscribeTopic);
            client.disconnect();

            return receivedMessage;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Publish an MQTT message to a topic.
     * @param client MQTT client for talking to an MQTT server
     * @param topic where the message is being published
     * @param payload the message to be published
     */
    private static void publishMessage(MqttClient client, String topic, String payload) throws Exception {
        MqttMessage message = new MqttMessage(payload.getBytes());
        client.publish(topic, message);
        Log.i(MqttClientTag, "Published message: " + payload);
    }

    /**
     * Wait for a message to be published to the topic.
     * @param client MQTT client for talking to an MQTT server
     * @param topic where the message is being published
     * @return the message received as a string
     */
    private static String listenForMessage(MqttClient client, String topic) throws Exception {
        // Use a CountDownLatch to wait for the message
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder receivedMessage = new StringBuilder();

        client.subscribe(topic, (t, msg) -> {
            String payload = new String(msg.getPayload());
            Log.i(MqttClientTag, "Received message: " + payload);
            receivedMessage.append(payload);
            latch.countDown();
        });

        boolean timeout = !latch.await(ResponseTimeout, TimeUnit.SECONDS);
        if (timeout) {
            Log.d(MqttClientTag, "Response timeout - no message received from Home Assistant.");
            receivedMessage.append("我收唔到訊息啊，你再講多次吖");
        }

        return receivedMessage.toString();
    }
}