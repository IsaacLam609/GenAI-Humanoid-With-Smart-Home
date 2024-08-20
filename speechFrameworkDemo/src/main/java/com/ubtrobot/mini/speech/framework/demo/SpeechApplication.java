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

    // Azure Speech Service
    private static final String SpeechSubscriptionKey = "your-key";
    private static final String ServiceRegion = "japaneast";

    // <Azure Speech to Text>
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

    // <Azure Text to Speech>
    private SpeechConfig ttsSpeechConfig;
    private SpeechSynthesizer synthesizer;
    private Connection connection;
    private AudioTrack audioTrack;

    private SpeakingRunnable speakingRunnable;
    private ExecutorService singleThreadExecutor;
    private final Object synchronizedObj = new Object();
    private boolean ttsStopped = false;
    private final String SpeechSynthesisTag = "Speech Synthesis";
    // </Azure Text to Speech>

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

        // <Azure Text to Speech>
        singleThreadExecutor = Executors.newSingleThreadExecutor();
        speakingRunnable = new SpeakingRunnable();

        audioTrack = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(24000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                AudioTrack.getMinBufferSize(
                        24000,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT) * 2,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE);

        initializeSpeechSynthesis();
        // </Azure Text to Speech>

        // <Azure Speech to Text>
        // create config
        final SpeechConfig sttSpeechConfig;
        try {
            sttSpeechConfig = SpeechConfig.fromSubscription(SpeechSubscriptionKey, ServiceRegion);
            sttSpeechConfig.setSpeechRecognitionLanguage("zh-HK");
            sttSpeechConfig.setProperty(PropertyId.SpeechServiceConnection_InitialSilenceTimeoutMs, InitialSilenceTimeoutMs);
            sttSpeechConfig.setProperty(PropertyId.Speech_SegmentationSilenceTimeoutMs, SegmentationSilenceTimeoutMs);
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

                preEstablishSynthesiserConnection();        // Azure Text to Speech

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
                    reco = new SpeechRecognizer(sttSpeechConfig, audioInput);

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
                            synthesiseSpeech(recognitionResult);
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

    // <Azure Text to Speech>
    public void initializeSpeechSynthesis() {
        if (synthesizer != null) {
            ttsSpeechConfig.close();
            synthesizer.close();
            connection.close();
        }

        // Reuse the synthesizer to lower the latency.
        // i.e. create one synthesizer and speak many times using it.

        ttsSpeechConfig = SpeechConfig.fromSubscription(SpeechSubscriptionKey, ServiceRegion);
        // Use 24k Hz format for higher quality.
        ttsSpeechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Raw24Khz16BitMonoPcm);
        // Set voice name.
        ttsSpeechConfig.setSpeechSynthesisVoiceName("zh-HK-HiuMaanNeural");
        synthesizer = new SpeechSynthesizer(ttsSpeechConfig, null);
        connection = Connection.fromSpeechSynthesizer(synthesizer);

        Locale current = getResources().getConfiguration().locale;

        connection.connected.addEventListener((o, e) -> {
            Log.i(SpeechSynthesisTag,"Connection established.");
        });

        connection.disconnected.addEventListener((o, e) -> {
            Log.i(SpeechSynthesisTag,"Disconnected.");
        });

        synthesizer.SynthesisStarted.addEventListener((o, e) -> {
            Log.i(SpeechSynthesisTag,String.format(current,
                    "Synthesis started. Result Id: %s.",
                    e.getResult().getResultId()));
            e.close();
        });

        synthesizer.Synthesizing.addEventListener((o, e) -> {
            Log.i(SpeechSynthesisTag,String.format(current,
                    "Synthesizing. received %d bytes.",
                    e.getResult().getAudioLength()));
            e.close();
        });

        synthesizer.SynthesisCompleted.addEventListener((o, e) -> {
            Log.i(SpeechSynthesisTag,"Synthesis finished.");
            Log.i(SpeechSynthesisTag,"First byte latency: " + e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_SynthesisFirstByteLatencyMs) + " ms.");
            Log.i(SpeechSynthesisTag,"Finish latency: " + e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_SynthesisFinishLatencyMs) + " ms.");
            e.close();
        });

        synthesizer.SynthesisCanceled.addEventListener((o, e) -> {
            String cancellationDetails =
                    SpeechSynthesisCancellationDetails.fromResult(e.getResult()).toString();
            Log.i(SpeechSynthesisTag,"Error synthesizing. Result ID: " + e.getResult().getResultId() +
                            ". Error detail: " + System.lineSeparator() + cancellationDetails +
                            System.lineSeparator() + "Did you update the subscription info?");
            e.close();
        });

        synthesizer.WordBoundary.addEventListener((o, e) -> {
            Log.i(SpeechSynthesisTag,String.format(current,
                    "Word boundary. Text offset %d, length %d; audio offset %d ms.",
                    e.getTextOffset(),
                    e.getWordLength(),
                    e.getAudioOffset() / 10000));

        });
    }

    public void preEstablishSynthesiserConnection() {
        // This method could pre-establish the connection to service to lower the latency
        // This method is useful when you want to synthesize audio in a short time, but the text is
        // not available. E.g. for speech bot, you can warm up the TTS connection when the user is speaking;
        // then call speak() when dialogue utterance is ready.
        // We can for example call this on wakeup.
        if (connection == null) {
            Log.i(SpeechSynthesisTag,"Please initialize the speech synthesizer first");
            return;
        }
        connection.openConnection(true);
        Log.i(SpeechSynthesisTag,"Opening connection.");
    }

    public void synthesiseSpeech(String text) {
        // This method should be called after text generation.
        if (synthesizer == null) {
            Log.i(SpeechSynthesisTag,"Please initialize the speech synthesizer first");
            return;
        }

        speakingRunnable.setContent(text);
        singleThreadExecutor.execute(speakingRunnable);
    }


    class SpeakingRunnable implements Runnable {
        private String content;

        public void setContent(String content) {
            this.content = content;
        }

        @Override
        public void run() {
            try {
                audioTrack.play();
                synchronized (synchronizedObj) {
                    ttsStopped = false;
                }

                SpeechSynthesisResult result = synthesizer.StartSpeakingTextAsync(content).get();
                AudioDataStream audioDataStream = AudioDataStream.fromResult(result);

                // Set the chunk size to 50 ms. 24000 * 16 * 0.05 / 8 = 2400
                byte[] buffer = new byte[2400];
                while (!ttsStopped) {
                    long len = audioDataStream.readData(buffer);
                    if (len == 0) {
                        break;
                    }
                    audioTrack.write(buffer, 0, (int) len);
                }

                audioDataStream.close();
            } catch (Exception ex) {
                Log.e("Speech Synthesis Demo", "unexpected " + ex.getMessage());
                ex.printStackTrace();
                assert(false);
            }
        }
    }

    private void stopSynthesizing() {
        if (synthesizer != null) {
            synthesizer.StopSpeakingAsync();
        }
        if (audioTrack != null) {
            synchronized (synchronizedObj) {
                ttsStopped = true;
            }
            audioTrack.pause();
            audioTrack.flush();
        }
    }
    // </Azure Text to Speech>
}
