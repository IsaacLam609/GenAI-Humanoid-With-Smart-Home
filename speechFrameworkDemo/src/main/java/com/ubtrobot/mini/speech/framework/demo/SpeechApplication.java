package com.ubtrobot.mini.speech.framework.demo;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.msc.util.log.DebugLog;
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

//        HeadEvent event = HeadEvent.newInstance();
//        SingleClickReceiver receiver = new SingleClickReceiver() {
//            @Override
//            public boolean onSingleClick(KeyEvent event) {
//                Log.d("SingleClickReceiver", "onSingleClicked:" + event);
//                Mqtt.publishMessage("testing");
//                return true;
//            }
//        };
//        SysEventApi.get().subscribe(event, receiver);

    }
}
