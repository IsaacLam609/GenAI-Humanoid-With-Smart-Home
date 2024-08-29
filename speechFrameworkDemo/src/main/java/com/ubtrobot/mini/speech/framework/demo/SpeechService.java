// Generates and plays the response audio based on user's speech.
// Allows continuous conversation on AlphaMini.

package com.ubtrobot.mini.speech.framework.demo;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.microsoft.cognitiveservices.speech.AudioDataStream;
import com.microsoft.cognitiveservices.speech.Connection;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.ubtechinc.skill.SkillHelper;
import com.ubtrobot.speech.SpeechApi;
import com.ubtrobot.speech.protos.Speech;
import com.ubtrobot.speech.receivers.WakeupReceiver;
import com.ubtrobot.mini.sysevent.SysEventApi;
import com.ubtrobot.mini.sysevent.event.HeadEvent;
import com.ubtrobot.mini.sysevent.event.base.KeyEvent;
import com.ubtrobot.mini.sysevent.receiver.SingleClickReceiver;
import com.ubtrobot.transport.message.CallException;
import com.ubtrobot.transport.message.Request;
import com.ubtrobot.transport.message.Response;
import com.ubtrobot.transport.message.ResponseCallback;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A class which includes the three processes of speech service
 * (speech recognition, text generation and speech synthesis).
 */
public class SpeechService{

    // Azure speech service subscription
    /** Azure Speech Service subscription key. */
    private static final String SpeechSubscriptionKey = Secrets.getSubscriptionKey();
    /** Azure Speech Service region */
    private static final String ServiceRegion = Secrets.getServiceRegion();

    // Azure speech recognition
    /** A duration of detected silence, measured in milliseconds, after which speech recognition
     * will determine nothing has been spoken and returns a no match. */
    private static final String InitialSilenceTimeoutMs = "5000";
    /** A duration of detected silence, measured in milliseconds, after which speech recognition
     * will determine a spoken phrase has ended and generate a final Recognized result. */
    private static final String SegmentationSilenceTimeoutMs = "500";
    /** Speech recognition language. */
    private static final String RecognitionLanguage = "zh-HK";
    private SpeechRecognizer recognizer = null;
    private AudioConfig audioInput = null;
    private SpeechConfig recognizerSpeechConfig;
    private final String SpeechRecognitionTag = "Speech Recognition";

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

    // Azure speech synthesis
    /** Speech synthesis voice name. */
    private final String SynthesisVoiceName = "zh-HK-HiuMaanNeural";
    private SpeechConfig synthesisSpeechConfig;
    private SpeechSynthesizer synthesizer;
    private Connection synthesisConnection;
    private AudioTrack synthesisAudioTrack;

    private SpeakingRunnable speakingRunnable;
    private ExecutorService singleThreadExecutor;
    private final Object synchronizedObj = new Object();
    private boolean synthesisStopped = false;
    private final String SpeechSynthesisTag = "Speech Synthesis";
    private boolean continuousListeningStarted = false;

    /**
     * Start the speech service (speech recognition, text generation and speech synthesis) on wake up.
     */
    public void subscribeToWakeup() {

        // initialize Azure speech synthesis
        initializeSpeechSynthesizer();

        // configure Azure speech recognition
        try {
            recognizerSpeechConfig = SpeechConfig.fromSubscription(SpeechSubscriptionKey, ServiceRegion);
            recognizerSpeechConfig.setSpeechRecognitionLanguage(RecognitionLanguage);
            recognizerSpeechConfig.setProperty(PropertyId.SpeechServiceConnection_InitialSilenceTimeoutMs, InitialSilenceTimeoutMs);
            recognizerSpeechConfig.setProperty(PropertyId.Speech_SegmentationSilenceTimeoutMs, SegmentationSilenceTimeoutMs);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return;
        }

        SpeechApi speechApi = SpeechApi.get();
        WakeupReceiver wakeupReceiver = new WakeupReceiver() {
            @Override
            public void onWakeup(Speech.WakeupParam data) {
                Log.i(SpeechRecognitionTag, "Wakeup.");
                // Get a speech response on wakeup
                getSpeechResponse();
            }
        };
        speechApi.subscribeEvent(wakeupReceiver);


        HeadEvent event = HeadEvent.newInstance();
        SingleClickReceiver receiver = new SingleClickReceiver(){
            // Stop synthesizing the speech and playing the audio on a short head tap.
            @Override
            public boolean onSingleClick(KeyEvent event){
                Log.d("SingleClickReceiver","onSingleClicked:"+event);
                stopSynthesizing();
                return true;
            }

            // Destroy resources before terminating the app by long head tap (eg to run an updated version of the app).
            // If not it may cause an error when the app is being terminated.
            // For development purpose only.
            @Override
            public boolean onLongClick(KeyEvent event){
                Log.d("SingleClickReceiver","onLongClicked:"+event);
                destroyResources();
                return true;
            }
        };
        SysEventApi.get().subscribe(event, receiver);

    }

    /**
     * Records user's speech, then generate and play the response audio.
     */
    public void getSpeechResponse() {
        preEstablishSynthesizerConnection();

        if (continuousListeningStarted) {
            if (recognizer != null) {
                final Future<Void> task = recognizer.stopContinuousRecognitionAsync();
                setOnTaskCompletedListener(task, result -> {
                    Log.i(SpeechRecognitionTag, "Continuous recognition stopped.");
                    continuousListeningStarted = false;
                });
            } else {
                continuousListeningStarted = false;
            }
            return;
        }

        try {
            audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
            recognizer = new SpeechRecognizer(recognizerSpeechConfig, audioInput);

            // event listeners

            recognizer.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                final String recognitionResult = speechRecognitionResultEventArgs.getResult().getText();
                Log.i(SpeechRecognitionTag, "Intermediate result received: " + recognitionResult);

            });

            recognizer.recognized.addEventListener((o, speechRecognitionResultEventArgs) -> {
                final String recognitionResult = speechRecognitionResultEventArgs.getResult().getText();
                Log.i(SpeechRecognitionTag, "Final result received: " + recognitionResult);

                // stop continuous recognition (if not it keeps recognizing infinitely)
                final Future<Void> task = recognizer.stopContinuousRecognitionAsync();
                setOnTaskCompletedListener(task, result -> {
                    Log.i(SpeechRecognitionTag, "Continuous recognition stopped.");
                    continuousListeningStarted = false;
                });

                if (!Objects.equals(recognitionResult, "")) {    // ie if the user speaks something
                    // text generation
                    String generatedText = MqttHomeAssistant.publishAndSubscribe(recognitionResult);
                    // speech synthesis
                    synthesizeSpeech(generatedText);
                }

            });

            final Future<Void> task = recognizer.startContinuousRecognitionAsync();
            setOnTaskCompletedListener(task, result -> {
                continuousListeningStarted = true;
            });
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

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

    /**
     * Initialize speech synthesizer and create corresponding event listeners.
     */
    public void initializeSpeechSynthesizer() {
        singleThreadExecutor = Executors.newSingleThreadExecutor();
        speakingRunnable = new SpeakingRunnable();
        synthesisAudioTrack = new AudioTrack(
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

        if (synthesizer != null) {
            synthesisSpeechConfig.close();
            synthesizer.close();
            synthesisConnection.close();
        }

        synthesisSpeechConfig = SpeechConfig.fromSubscription(SpeechSubscriptionKey, ServiceRegion);
        synthesisSpeechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Raw24Khz16BitMonoPcm);
        // Set voice name.
        synthesisSpeechConfig.setSpeechSynthesisVoiceName(SynthesisVoiceName);
        synthesizer = new SpeechSynthesizer(synthesisSpeechConfig, null);
        synthesisConnection = Connection.fromSpeechSynthesizer(synthesizer);

        synthesisConnection.connected.addEventListener((o, e) -> {
            Log.i(SpeechSynthesisTag,"Connection established.");
        });

        synthesisConnection.disconnected.addEventListener((o, e) -> {
            Log.i(SpeechSynthesisTag,"Disconnected.");
        });

        synthesizer.SynthesisStarted.addEventListener((o, e) -> {
            Log.i(SpeechSynthesisTag,String.format(
                    "Synthesis started. Result Id: %s.",
                    e.getResult().getResultId()));
            e.close();
        });

        // synthesizing event listener removed to prevent JNI ERROR (app bug): local reference table overflow (max=512)
//        synthesizer.Synthesizing.addEventListener((o, e) -> {
//            Log.i(SpeechSynthesisTag,String.format(current,
//                    "Synthesizing. received %d bytes.",
//                    e.getResult().getAudioLength()));
//            e.close();
//        });

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
    }

    /**
     * Pre-establish the connection to the speech synthesis service before the text is available
     * to lower the latency.
     */
    public void preEstablishSynthesizerConnection() {
        if (synthesisConnection == null) {
            Log.i(SpeechSynthesisTag,"Please initialize the speech synthesizer first");
            return;
        }
        synthesisConnection.openConnection(true);
        Log.i(SpeechSynthesisTag,"Opening connection.");
    }

    /**
     * Synthesize and play the audio based on the given text.
     * @param text the text to be converted to speech
     */
    public void synthesizeSpeech(String text) {
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
                synthesisAudioTrack.play();
                synchronized (synchronizedObj) {
                    synthesisStopped = false;
                }

                SpeechSynthesisResult result = synthesizer.StartSpeakingTextAsync(content).get();
                AudioDataStream audioDataStream = AudioDataStream.fromResult(result);

                // Set the chunk size to 50 ms. 24000 * 16 * 0.05 / 8 = 2400
                byte[] buffer = new byte[2400];
                while (!synthesisStopped) {
                    long len = audioDataStream.readData(buffer);
                    if (len == 0) {
                        break;
                    }
                    synthesisAudioTrack.write(buffer, 0, (int) len);
                }

                // close the stream after response audio finishes playing
                audioDataStream.close();

                // get a new response if the user continues speaking
                getSpeechResponse();

            } catch (Exception ex) {
                Log.e("Speech Synthesis Demo", "unexpected " + ex.getMessage());
                ex.printStackTrace();
                assert(false);
            }
        }
    }

    /**
     * Stop speech synthesis before it finishes.
     */
    private void stopSynthesizing() {
        Log.i(SpeechSynthesisTag,"Stop synthesizing speech.");

        if (synthesizer != null) {
            synthesizer.StopSpeakingAsync();
        }
        if (synthesisAudioTrack != null) {
            synchronized (synchronizedObj) {
                synthesisStopped = true;
            }
            synthesisAudioTrack.pause();
            synthesisAudioTrack.flush();
        }
    }

    /**
     * Destroy resources used in speech service.
     */
    private void destroyResources() {
        Log.i(SpeechSynthesisTag,"Destroying Resources.");

        // speech recognition resources
        releaseMicrophoneStream();

        // speech synthesis resources
        if (synthesizer != null) {
            synthesizer.close();
            synthesisConnection.close();
        }
        if (synthesisSpeechConfig != null) {
            synthesisSpeechConfig.close();
        }

        if (synthesisAudioTrack != null) {
            singleThreadExecutor.shutdownNow();
            synthesisAudioTrack.flush();
            synthesisAudioTrack.stop();
            synthesisAudioTrack.release();
        }
    }
}
