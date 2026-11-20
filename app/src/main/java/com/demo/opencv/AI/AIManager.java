package com.demo.opencv.AI;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.demo.opencv.BuildConfig;
import com.demo.opencv.UserDataManager;
import com.demo.opencv.other.Mp3Player;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AIManager {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Mp3Player mp3Player = new Mp3Player();
    private ChatCompletionClient textClient;
    private OkHttpClient httpClient;
    private Context mContext;
    private TextToSpeech systemSpeech;
    private boolean speechReady;

    public void initialize(Context context) {
        mContext = context.getApplicationContext();
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);
        httpClient = new OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();
        textClient = new ChatCompletionClient(BuildConfig.LLM_API_BASE_URL,
                BuildConfig.LLM_API_KEY, BuildConfig.LLM_PROVIDER, httpClient);
        systemSpeech = new TextToSpeech(mContext,
                status -> speechReady = status == TextToSpeech.SUCCESS);
    }

    public boolean isConfigured() {
        return !BuildConfig.LLM_API_KEY.trim().isEmpty();
    }

    private void sendMessage(String tag, String content, boolean error) {
        Intent intent = new Intent("textGenerationEvent");
        intent.putExtra("message", tag + "-" + content);
        intent.putExtra("error", error);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }

    public void generateText(String tag, String prompt, String language) {
        ChatCompletionClient.Message user = new ChatCompletionClient.Message("user", prompt);
        ChatCompletionClient.Message[] messages;
        String model;
        if ("SP".equals(tag)) {
            String instruction;
            if ("Chinese".equals(language)) {
                instruction = "你帮助渐冻症患者表达需求。根据语境和关键词（可能是拼音）生成一句简短、自然的中文句子。"
                        + "忠实保留关键词的意思，不添加新的需求。只输出句子，不解释、不加引号。";
            } else {
                instruction = "Help a patient communicate. Use the keywords and context to produce one short, natural sentence in "
                        + language + ". Preserve the intended meaning without adding new needs. Output only the sentence, without explanations or quotes.";
            }
            messages = new ChatCompletionClient.Message[]{
                    new ChatCompletionClient.Message("system", instruction), user};
            model = BuildConfig.LLM_SENTENCE_MODEL;
        } else {
            messages = new ChatCompletionClient.Message[]{user};
            model = BuildConfig.LLM_GENERAL_MODEL;
        }

        textClient.generate(messages, model).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ChatCompletionClient.Output> call,
                                   @NonNull Response<ChatCompletionClient.Output> response) {
                ChatCompletionClient.Output body = response.body();
                if (response.isSuccessful() && body != null && !body.choices().isEmpty()
                        && body.choices().get(0).message().content() != null) {
                    sendMessage(tag, body.choices().get(0).message().content().trim(), false);
                } else {
                    reportError(tag, "HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ChatCompletionClient.Output> call, @NonNull Throwable t) {
                Log.w("TextGeneration", "Chat request failed", t);
                reportError(tag, "Network / response error");
            }
        });
    }

    private void reportError(String tag, String message) {
        Log.w("TextGeneration", message);
        sendMessage(tag, message, true);
    }

    public void speechGenerationService(String text) {
        if (text.trim().isEmpty()) return;
        if ("openai".equals(BuildConfig.TTS_PROVIDER) && !BuildConfig.MY_OPENAI_KEY.trim().isEmpty()) {
            generateOpenAiSpeech(text);
        } else {
            mainHandler.post(() -> speakWithSystem(text));
        }
    }

    private void speakWithSystem(String text) {
        String language = ((UserDataManager) mContext).getLanguage();
        Locale locale = "Chinese".equals(language) ? Locale.SIMPLIFIED_CHINESE
                : "Spanish".equals(language) ? new Locale("es", "ES") : Locale.US;
        if (!speechReady) {
            showSpeechError(language);
            return;
        }
        int result = systemSpeech.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            showSpeechError(language);
            return;
        }
        systemSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gaze-link");
    }

    private void showSpeechError(String language) {
        Toast.makeText(mContext, "Chinese".equals(language)
                ? "请在手机设置中启用文字转语音引擎，并安装对应语言的语音包。"
                : "Enable a text-to-speech engine and install its language data in phone settings.",
                Toast.LENGTH_LONG).show();
    }

    private void generateOpenAiSpeech(String text) {
        JSONObject json = new JSONObject();
        try {
            json.put("voice", "onyx");
            json.put("input", text);
            json.put("model", "tts-1");
            json.put("speed", 0.9);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .post(RequestBody.create(json.toString(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + BuildConfig.MY_OPENAI_KEY)
                .build();
        executorService.submit(() -> {
            try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    mp3Player.playMp3FromBytes(response.body().bytes(), mContext);
                } else {
                    throw new IOException("Speech request failed: HTTP " + response.code());
                }
            } catch (IOException e) {
                Log.w("SpeechGeneration", "Using system speech after request failure", e);
                mainHandler.post(() -> speakWithSystem(text));
            }
        });
    }

    public void close() {
        systemSpeech.shutdown();
        executorService.shutdownNow();
    }
}
