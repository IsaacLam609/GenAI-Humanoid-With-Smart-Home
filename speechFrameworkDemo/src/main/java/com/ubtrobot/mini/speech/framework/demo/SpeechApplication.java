package com.ubtrobot.mini.speech.framework.demo;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.msc.util.log.DebugLog;
import com.microsoft.cognitiveservices.speech.AudioDataStream;
import com.microsoft.cognitiveservices.speech.Connection;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.ubtech.utilcode.utils.thread.ThreadPool;
import com.ubtrobot.master.log.InfrequentLoggerFactory;
import com.ubtrobot.mini.speech.framework.AbstractSpeechApplication;
import com.ubtrobot.service.ServiceModules;
import com.ubtrobot.speech.SpeechApi;
import com.ubtrobot.speech.SpeechService;
import com.ubtrobot.speech.SpeechSettings;
import com.ubtrobot.speech.protos.Speech;
import com.ubtrobot.speech.receivers.WakeupReceiver;
import com.ubtrobot.ulog.FwLoggerFactory2;
import com.ubtrobot.ulog.logger.android.AndroidLoggerFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SpeechApplication extends AbstractSpeechApplication {

    private static SpeechApplication instance;

    public static SpeechApplication getInstance() {
        return instance;
    }

    public static Context getSpeechApplicationContext() {
        return instance.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        StringBuffer param = new StringBuffer();
        param.append("appid=" + getString(R.string.app_id));
        param.append(",");
        param.append(SpeechConstant.ENGINE_MODE + "=" + SpeechConstant.MODE_MSC);
        SpeechUtility.createUtility(this, param.toString());
        DebugLog.setLogLevel(BuildConfig.DEBUG ? DebugLog.LOG_LEVEL.none : DebugLog.LOG_LEVEL.none);
        FwLoggerFactory2.setup(
                BuildConfig.DEBUG ? new AndroidLoggerFactory() : new InfrequentLoggerFactory());
        startService(new Intent(this, DemoMasterService.class));
        ServiceModules.declare(SpeechSettings.class,
                (aClass, moduleCreatedNotifier) -> moduleCreatedNotifier.notifyModuleCreated(
                        DemoSpeech.INSTANCE.createSpeechSettings()));

        ServiceModules.declare(SpeechService.class,
                (aClass, moduleCreatedNotifier) -> ThreadPool.runOnNonUIThread(() -> {
                    while (DemoSpeech.INSTANCE.createSpeechService() == null) {
                        SystemClock.sleep(5);
                    }
                    Log.d("Logic", "Speech Service create ok..");
                    moduleCreatedNotifier.notifyModuleCreated(DemoSpeech.INSTANCE.createSpeechService());
                }));

        // speech service
        com.ubtrobot.mini.speech.framework.demo.SpeechService speechService = new com.ubtrobot.mini.speech.framework.demo.SpeechService();
        speechService.subscribeToWakeup();
    }
}
