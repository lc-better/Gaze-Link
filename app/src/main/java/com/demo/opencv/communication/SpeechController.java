package com.demo.opencv.communication;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

/** One local speaker for the whole communication flow, with visible state and stop/replay. */
public final class SpeechController {
    private final TextToSpeech tts;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.function.Consumer<String> listener;
    private boolean ready, closed, initializationFailed;
    private String queued = "", lastText = "";
    private float speed = .9f;
    private int serial;
    public SpeechController(Context context, java.util.function.Consumer<String> listener) {
        this.listener = listener;
        tts = new TextToSpeech(context, result -> main.post(() -> initialized(result)));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { report(id, "正在朗读"); }
            @Override public void onDone(String id) { report(id, "朗读完成"); }
            @Override public void onError(String id) { report(id, "朗读失败，请到手机的文字转语音设置检查中文语音包"); }
        });
    }
    private void initialized(int result) {
        if (closed) return;
        ready = result == TextToSpeech.SUCCESS;
        initializationFailed = !ready;
        listener.accept(ready ? "语音已就绪，可点击试听" : "语音引擎不可用，请在手机设置启用文字转语音，再重新打开应用");
        if (ready && !queued.isEmpty()) { String text = queued; queued = ""; speak(text); }
    }
    private void report(String id, String message) {
        main.post(() -> { if (!closed && id.equals(Integer.toString(serial))) listener.accept(message); });
    }
    public void setSpeed(float value) { speed = value; }
    public void speak(String text) {
        if (text.trim().isEmpty()) { listener.accept("请先选择要朗读的内容"); return; }
        lastText = text;
        if (initializationFailed) { listener.accept("语音引擎不可用，请在手机设置启用文字转语音，再重新打开应用"); return; }
        if (!ready) { queued = text; listener.accept("正在准备语音引擎…"); return; }
        Locale language = text.matches(".*[\\p{IsHan}].*") ? Locale.SIMPLIFIED_CHINESE : Locale.getDefault();
        int result = tts.setLanguage(language);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            listener.accept("缺少对应语音包。请在手机“文字转语音”设置中安装，完成后重新试听。"); return;
        }
        tts.setSpeechRate(speed);
        serial++;
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, Integer.toString(serial)) == TextToSpeech.ERROR)
            listener.accept("朗读未启动，请检查手机的文字转语音设置");
    }
    public void replay() { speak(lastText); }
    public void stop() { serial++; queued = ""; tts.stop(); listener.accept("朗读已停止"); }
    public void close() { closed = true; main.removeCallbacksAndMessages(null); tts.stop(); tts.shutdown(); }
}
