package com.ubtrobot.mini.speech.framework.demo;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;


import com.ubt.speecharray.DataCallback;
import com.ubt.speecharray.MicArrayUtils;
import com.ubtechinc.mini.weinalib.WeiNaMicApi;
import com.ubtrobot.mini.sysevent.SysEventApi;
import com.ubtrobot.mini.sysevent.event.HeadEvent;
import com.ubtrobot.mini.sysevent.event.base.KeyEvent;
import com.ubtrobot.mini.sysevent.receiver.SingleClickReceiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class MainActivity extends AppCompatActivity {

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

//    micArrayTest();
  }


  //for uploading audio file to flask server (laptop) in background
  public static class FileUploadTask extends AsyncTask<String, Void, Boolean> {
    @Override
    protected void onPreExecute() {
      // Show a progress indicator or other UI updates
      showProgressDialog("Uploading file...");
    }

    @Override
    protected Boolean doInBackground(String... params) {
      String filePath = params[0];
      String serverUrl = params[1];

      try {
        uploadFile(filePath, serverUrl);
        return true;
      } catch (Exception e) {
        e.printStackTrace();
        return false;
      }
    }

    @Override
    protected void onPostExecute(Boolean result) {
      // Hide the progress indicator and handle the upload result
      hideProgressDialog();
    }
  }

  private static void showProgressDialog(String message) {
    // Implement the logic to show a progress dialog
  }

  private static void hideProgressDialog() {
    // Implement the logic to hide the progress dialog
  }

  private static void uploadFile(String filePath, String serverUrl) {
    File file = new File(filePath);
    byte[] bytes = new byte[(int) file.length()];

    try (FileInputStream fis = new FileInputStream(file)) {
      fis.read(bytes);
    } catch (IOException e) {
      Log.e("", "Error reading file: " + e.getMessage());
      return;
    }

    try {
      URL url = new URL(serverUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "application/octet-stream");
      connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));

      try (OutputStream outputStream = connection.getOutputStream()) {
        outputStream.write(bytes);
      }

      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        Log.d("", "File uploaded successfully");
      } else {
        Log.e("", "File upload failed with response code: " + responseCode);
      }
    } catch (IOException e) {
      Log.e("", "Error uploading file: " + e.getMessage());
    }
  }


//  this is the method provided in the demo for acquiring original audio data of microphone
//      private void micArrayTest() {
//        final MicArrayUtils micArrayUtils = new MicArrayUtils(this.getApplicationContext(),16000,16,512);
//        //init
//        micArrayUtils.init();
//        //set Callback to receive audio data
//        micArrayUtils.setDataCallback(new DataCallback() {
//            @Override
//            public void onAudioData(byte[] bytes) {
//                // 6 channels data, 1-4: mic data, 5-6: ref data
//                Log.d("Main Activity","onAudioData---bytes.length = "+bytes.length);
//                byte[][] spliteData = MicArrayUtils.spliteData(bytes);
//            }
//        });
//        //start mic Array
//        micArrayUtils.startRecord();
//        Log.i("Main Activity","Start Record");
//
//        //save pcm data in sdcard (path : /sdcard/micdata/)
//        micArrayUtils.setSaveOriginalAudio(true);
//        Log.i("Main Activity","Save Recording");
//
//        //stop mic Array
////        micArrayUtils.stopRecord();
////        Log.i("Main Activity","Stop Recording");
//
//    }
}
