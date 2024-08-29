package com.ubtrobot.mini.speech.framework.demo;

import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class HttpClient {
    private static final String BASE_URL = "your-url";
    private static final String HttpClientTag = "Http Client";
    private static final int POLLING_INTERVAL = 100; // 5 seconds

    private Timer timer;
    private TimerTask pollingTask;

    public void startPolling() {
        timer = new Timer();
        pollingTask = new TimerTask() {
            @Override
            public void run() {
                getTextFromServer();
            }
        };
        timer.schedule(pollingTask, 0, POLLING_INTERVAL);
    }

    public void stopPolling() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
    }

    public static void sendTextToServer(String text) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/send-text");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");

                try (OutputStream outputStream = connection.getOutputStream()) {
                    byte[] input = text.getBytes("utf-8");
                    outputStream.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(HttpClientTag, "Text uploaded successfully");
                } else {
                    Log.e(HttpClientTag, "Text upload failed with response code: " + responseCode);
                }
            } catch (IOException e) {
                Log.e(HttpClientTag, "Error uploading text: " + e.getMessage());
            }
        }).start();
    }

    public static void getTextFromServer() {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/get-text");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Handle the successful response
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String responseText = in.readLine();
                    // Process the response text
                    Log.i(HttpClientTag, "Received text: " + responseText);
                } else {
                    // Handle the error response
                    Log.d(HttpClientTag, "Text uploaded successfully");
                }
            } catch (IOException e) {
                // Handle the exception
            }
        }).start();
    }
}
