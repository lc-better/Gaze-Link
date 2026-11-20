package com.demo.opencv.communication;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.demo.opencv.AI.ChatCompletionClient;
import com.demo.opencv.BuildConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Explicit generation only. The draft decides whether the callback is still relevant. */
public final class SuggestionService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS).build();
    private final ChatCompletionClient client = new ChatCompletionClient(BuildConfig.LLM_API_BASE_URL,
            BuildConfig.LLM_API_KEY, BuildConfig.LLM_PROVIDER, http);
    private Call<ChatCompletionClient.Output> active;
    private long requestSerial;

    public interface Listener { void completed(long version, String result, boolean success); }
    public interface InitialsListener { void completed(long version, List<InitialsCandidate> candidates, String message, boolean success); }
    public boolean configured() { return !BuildConfig.LLM_API_KEY.trim().isEmpty(); }

    public void generate(Draft draft, Listener listener) {
        cancel();
        requestSerial++;
        long version = draft.beginRequest();
        ChatCompletionClient.Message[] messages = {
            new ChatCompletionClient.Message("system", "帮助用户表达。把用户选好的内容整理成一句简短自然的中文。忠实保留原意，不新增症状、需求、程度或事实。只输出句子。"),
            new ChatCompletionClient.Message("user", "原文：" + draft.text() + "\n对话背景：" + draft.context())
        };
        active = client.generate(messages, BuildConfig.LLM_SENTENCE_MODEL);
        active.enqueue(new Callback<ChatCompletionClient.Output>() {
            @Override public void onResponse(@NonNull Call<ChatCompletionClient.Output> call,
                    @NonNull Response<ChatCompletionClient.Output> response) {
                ChatCompletionClient.Output body = response.body();
                boolean success = response.isSuccessful() && body != null && body.choices() != null
                        && !body.choices().isEmpty() && body.choices().get(0).message() != null
                        && body.choices().get(0).message().content() != null;
                String value = success ? body.choices().get(0).message().content().trim()
                        : "生成失败（HTTP " + response.code() + "），可重试或直接朗读原文。";
                main.post(() -> listener.completed(version, value, success));
            }
            @Override public void onFailure(@NonNull Call<ChatCompletionClient.Output> call, @NonNull Throwable error) {
                if (!call.isCanceled()) main.post(() -> listener.completed(version,
                        "网络连接失败，可重试或直接朗读原文。", false));
            }
        });
    }

    /** A shared JSON schema works with DeepSeek and Qwen without provider-specific modes. */
    public void generateInitials(String initials, String context, InitialsListener listener) {
        cancel();
        long request = ++requestSerial;
        String system = "你是中文沟通辅助工具。用户输入的是每个汉字的拼音首字母。"
                + "请根据首字母顺序生成2到3个简短候选。严格核对每个候选的首字母，不能擅自删减或增加字。"
                + "按每个汉字取拼音的第一个字母，zh/ch/sh仅取z/c/s，零声母按拼音首字母a/e/o。"
                + "例如WAN对应我爱你，晚安只有WA，不能作为WAN的候选。优先贴合对话背景的日常表达，"
                + "不要编造症状、数字或事实。无法匹配时少返回或返回空数组。"
                + "只返回JSON，不要解释，格式为{\"candidates\":[{\"text\":\"我爱你\"}]}。";
        ChatCompletionClient.Message[] messages = {
            new ChatCompletionClient.Message("system", system),
            new ChatCompletionClient.Message("user", "输入首字母：" + initials + "\n对话背景：" + (context == null ? "" : context))
        };
        active = client.generate(messages, BuildConfig.LLM_SENTENCE_MODEL);
        active.enqueue(new Callback<ChatCompletionClient.Output>() {
            @Override public void onResponse(@NonNull Call<ChatCompletionClient.Output> call,
                    @NonNull Response<ChatCompletionClient.Output> response) {
                if (request != requestSerial) return;
                ChatCompletionClient.Output body = response.body();
                boolean success = response.isSuccessful() && body != null && body.choices() != null
                        && !body.choices().isEmpty() && body.choices().get(0).message() != null
                        && body.choices().get(0).message().content() != null;
                if (!success) {
                    postInitials(request, new ArrayList<>(), "生成失败（HTTP " + response.code() + "），请重试。", false, listener);
                    return;
                }
                List<InitialsCandidate> candidates = InitialsResponse.parse(body.choices().get(0).message().content(), initials);
                postInitials(request, candidates, candidates.isEmpty() ? "没有符合首字母的候选，请重试或修改输入。" : "已核对首字母，请选择要表达的句子。", !candidates.isEmpty(), listener);
            }
            @Override public void onFailure(@NonNull Call<ChatCompletionClient.Output> call, @NonNull Throwable error) {
                if (!call.isCanceled() && request == requestSerial)
                    postInitials(request, new ArrayList<>(), "网络连接失败，请重试。", false, listener);
            }
        });
    }

    private void postInitials(long version, List<InitialsCandidate> candidates, String message,
            boolean success, InitialsListener listener) {
        main.post(() -> {
            if (version == requestSerial) listener.completed(version, candidates, message, success);
        });
    }

    public void cancel() { requestSerial++; if (active != null) { active.cancel(); active = null; } }
    public void close() { cancel(); main.removeCallbacksAndMessages(null); http.dispatcher().executorService().shutdown(); http.connectionPool().evictAll(); }
}
