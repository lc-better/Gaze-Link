package com.demo.opencv.AI;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Response;

import static org.junit.Assert.*;

public class ChatCompletionClientTest {
    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test(timeout = 10000)
    public void qwenPreservesBasePathAndDisablesThinking() throws Exception {
        RecordedRequest request = send("qwen", "/compatible-mode/v1/", "qwen3.8-flash");
        assertEquals("/compatible-mode/v1/chat/completions", request.getPath());
        JsonObject body = JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
        assertFalse(body.get("enable_thinking").getAsBoolean());
        assertFalse(body.has("thinking"));
    }

    @Test(timeout = 10000)
    public void deepseekUsesItsOwnThinkingParameterAndNormalizesBaseUrl() throws Exception {
        RecordedRequest request = send("deepseek", "/", "deepseek-flash");
        assertEquals("/chat/completions", request.getPath());
        JsonObject body = JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("disabled", body.getAsJsonObject("thinking").get("type").getAsString());
        assertFalse(body.has("enable_thinking"));
    }

    @Test(timeout = 10000)
    public void openaiOmitsProviderSpecificParameters() throws Exception {
        RecordedRequest request = send("openai", "/v1/", "gpt-4o-mini");
        assertEquals("/v1/chat/completions", request.getPath());
        JsonObject body = JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
        assertFalse(body.has("thinking"));
        assertFalse(body.has("enable_thinking"));
    }

    @Test(timeout = 10000)
    public void authenticationFailureRemainsAnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"error\":{\"message\":\"Invalid API key\"}}"));
        Response<ChatCompletionClient.Output> response = client("qwen", "/compatible-mode/v1/")
                .generate(new ChatCompletionClient.Message[]{
                        new ChatCompletionClient.Message("user", "water")}, "qwen3.8-flash").execute();
        assertFalse(response.isSuccessful());
        assertEquals(401, response.code());
        assertNull(response.body());
        assertNotNull(response.errorBody());
        response.errorBody().close();
    }

    private RecordedRequest send(String provider, String path, String model) throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"我想喝水。\",\"reasoning_content\":\"not spoken\"}}]}"));
        Response<ChatCompletionClient.Output> response = client(provider, path)
                .generate(new ChatCompletionClient.Message[]{
                        new ChatCompletionClient.Message("system", "Only output a short sentence."),
                        new ChatCompletionClient.Message("user", "Keywords: water")}, model).execute();
        assertTrue(response.isSuccessful());
        assertEquals("我想喝水。", response.body().choices().get(0).message().content());
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("Bearer test-key", request.getHeader("Authorization"));
        JsonObject body = JsonParser.parseString(request.getBody().clone().readUtf8()).getAsJsonObject();
        assertEquals(model, body.get("model").getAsString());
        assertEquals("system", body.getAsJsonArray("messages").get(0)
                .getAsJsonObject().get("role").getAsString());
        assertEquals("Keywords: water", body.getAsJsonArray("messages").get(1)
                .getAsJsonObject().get("content").getAsString());
        return request;
    }

    private ChatCompletionClient client(String provider, String path) {
        String baseUrl = server.url(path).toString();
        // Exercise the normalizer as well as nested base paths.
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        return new ChatCompletionClient(baseUrl, "test-key", provider, new OkHttpClient());
    }
}
