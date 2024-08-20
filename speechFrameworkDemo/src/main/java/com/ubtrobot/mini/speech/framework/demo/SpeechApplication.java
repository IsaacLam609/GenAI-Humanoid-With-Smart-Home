package com.ubtrobot.mini.speech.framework.demo;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.msc.util.log.DebugLog;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.ServicePropertyChannel;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.ubtech.utilcode.utils.thread.ThreadPool;
import com.ubtechinc.skill.SkillHelper;
import com.ubtrobot.master.context.BaseContext;
import com.ubtrobot.master.context.MasterContext;
import com.ubtrobot.master.log.InfrequentLoggerFactory;
import com.ubtrobot.master.service.MasterService;
import com.ubtrobot.master.skill.MasterSkill;
import com.ubtrobot.master.skill.SkillStopCause;
import com.ubtrobot.mini.speech.framework.AbstractSpeechApplication;
import com.ubtrobot.mini.sysevent.SysEventApi;
import com.ubtrobot.mini.sysevent.event.HeadEvent;
import com.ubtrobot.mini.sysevent.event.base.KeyEvent;
import com.ubtrobot.mini.sysevent.receiver.SingleClickReceiver;
import com.ubtrobot.mini.voice.MiniMediaPlayer;
import com.ubtrobot.mini.voice.VoiceException;
import com.ubtrobot.mini.voice.VoicePool;
import com.ubtrobot.mini.voice.protos.VoiceProto;
import com.ubtrobot.service.ServiceModules;
import com.ubtrobot.speech.SpeechApi;
import com.ubtrobot.speech.SpeechService;
import com.ubtrobot.speech.SpeechSettings;
import com.ubtrobot.speech.protos.Speech;
import com.ubtrobot.speech.receivers.WakeupReceiver;
import com.ubtrobot.transport.message.CallException;
import com.ubtrobot.transport.message.Request;
import com.ubtrobot.transport.message.Response;
import com.ubtrobot.transport.message.ResponseCallback;
import com.ubtrobot.ulog.FwLoggerFactory2;
import com.ubtrobot.ulog.logger.android.AndroidLoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
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

    // <Azure Speech to Text>
    private static final String SpeechSubscriptionKey = "your-key";
    private static final String SpeechRegion = "japaneast";
    private static final String InitialSilenceTimeoutMs = "5000";
    private static final String SegmentationSilenceTimeoutMs = "500";   // duration of silence before end of phrase

    private MicrophoneStream microphoneStream;
    private MicrophoneStream createMicrophoneStream() {
        this.releaseMicrophoneStream();

        microphoneStream = new MicrophoneStream();
        return microphoneStream;
    }
    private void releaseMicrophoneStream() {
        if (microphoneStream != null) {
            microphoneStream.close();
            microphoneStream = null;
        }
    }
    // </Azure Speech to Text>

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


        // <Azure Speech to Text>

        // create config
        final SpeechConfig speechConfig;
        try {
            speechConfig = SpeechConfig.fromSubscription(SpeechSubscriptionKey, SpeechRegion);
            speechConfig.setSpeechRecognitionLanguage("zh-HK");
            speechConfig.setProperty(PropertyId.SpeechServiceConnection_InitialSilenceTimeoutMs, InitialSilenceTimeoutMs);
            speechConfig.setProperty(PropertyId.Speech_SegmentationSilenceTimeoutMs, SegmentationSilenceTimeoutMs);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return;
        }


        //Wakeup event receiver
        SpeechApi speechApi = SpeechApi.get();
        WakeupReceiver wakeupReceiver = new WakeupReceiver() {
            private boolean continuousListeningStarted = false;
            private SpeechRecognizer reco = null;
            private AudioConfig audioInput = null;

            @Override
            public void onWakeup(Speech.WakeupParam data) {
                Log.i("Speech Recognition", "Wakeup.");

                if (continuousListeningStarted) {
                    if (reco != null) {
                        final Future<Void> task = reco.stopContinuousRecognitionAsync();
                        setOnTaskCompletedListener(task, result -> {
                            Log.i("Speech Recognition", "Continuous recognition stopped.");
                            continuousListeningStarted = false;
                        });
                    } else {
                        continuousListeningStarted = false;
                    }
                    return;
                }

                try {
                    audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
                    reco = new SpeechRecognizer(speechConfig, audioInput);

                    reco.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String recognitionResult = speechRecognitionResultEventArgs.getResult().getText();
                        Log.i("Speech Recognition", "Intermediate result received: " + recognitionResult);

                    });

                    reco.recognized.addEventListener((o, speechRecognitionResultEventArgs) -> {
                        final String recognitionResult = speechRecognitionResultEventArgs.getResult().getText();
                        Log.i("Speech Recognition", "Final result received: " + recognitionResult);

                        // stop continuous recognition (if not it keeps recognizing infinitely)
                        final Future<Void> task = reco.stopContinuousRecognitionAsync();
                        setOnTaskCompletedListener(task, result -> {
                            Log.i("Speech Recognition", "Continuous recognition stopped.");
                            continuousListeningStarted = false;
                        });

                        if (!Objects.equals(recognitionResult, "")) {
                            // text generation
                            // tts and play
                        }

                    });

                    final Future<Void> task = reco.startContinuousRecognitionAsync();
                    setOnTaskCompletedListener(task, result -> {
                        continuousListeningStarted = true;
                    });
                } catch (Exception ex) {
                    System.out.println(ex.getMessage());
                }
            }
        };
        speechApi.subscribeEvent(wakeupReceiver);

        // </Azure Speech To Text>

    }


    // <Azure Speech to Text>
    private <T> void setOnTaskCompletedListener(Future<T> task, OnTaskCompletedListener<T> listener) {
        s_executorService.submit(() -> {
            T result = task.get();
            listener.onCompleted(result);
            return null;
        });
    }

    private interface OnTaskCompletedListener<T> {
        void onCompleted(T taskResult);
    }

    private static ExecutorService s_executorService;
    static {
        s_executorService = Executors.newCachedThreadPool();
    }
    // </Azure Speech to Text>
}
