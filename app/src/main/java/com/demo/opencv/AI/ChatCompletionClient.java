package com.demo.opencv.AI;

import java.util.List;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/** Shared Chat Completions transport for OpenAI, DeepSeek and Qwen. */
public class ChatCompletionClient {
    private final Service service;
    private final String authorization;
    private final String provider;

    public ChatCompletionClient(String baseUrl, String apiKey, String provider, OkHttpClient client) {
        this.provider = provider;
        authorization = "Bearer " + apiKey;
        service = new Retrofit.Builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(Service.class);
    }

    public Call<Output> generate(Message[] messages, String model) {
        // Short communication prompts benefit from a direct answer without reasoning output.
        Boolean enableThinking = "qwen".equals(provider) ? Boolean.FALSE : null;
        Thinking thinking = "deepseek".equals(provider) ? new Thinking("disabled") : null;
        return service.generate(authorization, new Input(messages, model, enableThinking, thinking));
    }

    interface Service {
        @Headers("Content-Type: application/json")
        @POST("chat/completions")
        Call<Output> generate(@Header("Authorization") String authorization, @Body Input input);
    }

    // Ordinary data classes also work with the project's existing Gson version on Android.
    public static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String content() { return content; }
    }

    private static class Thinking {
        private final String type;

        Thinking(String type) { this.type = type; }
    }

    private static class Input {
        private final Message[] messages;
        private final String model;
        private final Boolean enable_thinking;
        private final Thinking thinking;

        Input(Message[] messages, String model, Boolean enableThinking, Thinking thinking) {
            this.messages = messages;
            this.model = model;
            this.enable_thinking = enableThinking;
            this.thinking = thinking;
        }
    }

    public static class Choice {
        private Message message;

        public Message message() { return message; }
    }

    public static class Output {
        private List<Choice> choices;

        public List<Choice> choices() { return choices; }
    }
}
