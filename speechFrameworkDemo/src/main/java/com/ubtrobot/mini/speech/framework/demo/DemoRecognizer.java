package com.ubtrobot.mini.speech.framework.demo;

import static com.ubtechinc.mini.weinalib.TencentVadRecorder.STATE_IDLE;
import static com.ubtechinc.mini.weinalib.TencentVadRecorder.STATE_RECORDING;
import static com.ubtechinc.mini.weinalib.TencentVadRecorder.STATE_SPEAK_END;

import android.util.Log;

import com.ubtechinc.mini.weinalib.TencentVadRecorder;
import com.ubtrobot.master.service.MasterService;
import com.ubtrobot.master.skill.MasterSkill;
import com.ubtrobot.master.skill.SkillStopCause;
import com.ubtrobot.mini.voice.MiniMediaPlayer;
import com.ubtrobot.mini.voice.VoiceException;
import com.ubtrobot.mini.voice.protos.VoiceProto;
import com.ubtrobot.speech.AbstractRecognizer;
import com.ubtrobot.speech.AudioRecordListener;
import com.ubtrobot.speech.AudioRecordStateListener;
import com.ubtrobot.speech.RecognitionOption;
import com.ubtrobot.speech.RecordException;
import com.ubtrobot.speech.vad.VadDetector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class DemoRecognizer extends AbstractRecognizer {
  private final TencentVadRecorder recorder;
  private ByteArrayOutputStream byteArrayOutputStream;
  private static final String AUDIO_DIR = "/data/user/0/com.ubtrobot.mini.speech.framework.demo/files";
  private static final String AUDIO_FILE_NAME = "recorded_audio.pcm";

  public DemoRecognizer(TencentVadRecorder recorder) {
    this.recorder = recorder;

    //register state listener for Tencent Vad Recorder
    recorder.registerStateListener(new AudioRecordStateListener() {
      //this function will be called on state change
      @Override
      public void onStateChanged(int i, RecordException e) {
        //int i: new state

        if (recorder.getState() == STATE_SPEAK_END) {
          Log.i("Demo Recognizer", "Finished Speaking.");
          stopRecognizing();
          if (recorder.isBeamformingEnabled()) {
            boolean stopBeamForming = recorder.disableBeamforming();
          }
        }
        if ((recorder.getState() == STATE_RECORDING) && (!recorder.isBeamformingEnabled())) {
          boolean startBeamForming = recorder.enableBeamforming();
        }
      }
    });

    byteArrayOutputStream = new ByteArrayOutputStream();
    recorder.registerRecordListener(new AudioRecordListener() {
      //this method is called when TencentVadRecorder detects someone speaking
      @Override public void onRecord(byte[] asrData, int length) {
        //asrData: pcm, 16000 sampleRate, 8bit
        //****************** BITS PER SAMPLE IS IN FACT 16 BITS NOT 8 BITS ******************
        //Receive the recording data of microphone output in line here

        Log.i("Demo Recognizer", "Demo Recognizer - onRecord.");
        //append the incoming asrData to byteArrayOutputStream
        byteArrayOutputStream.write(asrData, 0, length);
      }
    }, null, null);
  }

  @Override protected void startRecognizing(RecognitionOption recognitionOption) {
    recorder.start();
    Log.i("Demo Recognizer", "Demo Recognizer - Start Recognizing.");
  }

  @Override protected void stopRecognizing() {

    if (recorder.getState() != STATE_IDLE) {
      recorder.stop();

      Log.i("Demo Recognizer", "Stop Recognizing.");

      byte[] recordedData = byteArrayOutputStream.toByteArray();
      PcmToWavConverter.convertPcmToWav(recordedData);
      //clear the content of byteArrayOutputStream
      byteArrayOutputStream.reset();

    } else {

      //stopRecognizing() will be called on second wake up by default
      Log.i("Demo Recognizer", "State already idle, no need to stop the recorder");
    }

  //    //play audio file
  //    MasterSkill tempskill = new MasterSkill() {
  //      @Override
  //      protected void onSkillStart() {
  //      }
  //      @Override
  //      protected void onSkillStop(SkillStopCause skillStopCause) {
  //      }
  //    };
  //
  //    VoiceProto.Source sourceValue = VoiceProto.Source.forNumber(2);
  //    try {
  //      MiniMediaPlayer player = MiniMediaPlayer.create(tempskill, sourceValue);
  //      Log.i("Demo Recognizer", "1");
  //      player.setDataSource("/data/user/0/com.ubtrobot.mini.speech.framework.demo/files/audiofile.wav");
  //      Log.i("Demo Recognizer", "2");
  //      player.prepare();
  //      Log.i("Demo Recognizer", "3");
  //      player.start();
  //      Log.i("Demo Recognizer", "Audio file played successfully");
  //    } catch (VoiceException | IOException e) {
  ////      } catch (VoiceException e) {
  //      throw new RuntimeException(e);
  //    }

  }

}
