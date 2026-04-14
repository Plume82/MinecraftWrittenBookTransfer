package com.functionlib;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * 本地翻译服务调用工具
 * 依赖本地 LibreTranslate 服务 (默认 http://localhost:5001)
 */
public class TranslateLib {

    private static final String API_URL = "http://localhost:5001/translate";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new Gson();

    /**
     * 翻译单段文本
     * @param text 原文
     * @param sourceLang 源语言代码 (如 "en", "zh", "auto" 自动检测)
     * @param targetLang 目标语言代码 (如 "zh", "en")
     * @return 翻译后的文本，失败返回 null
     */
    public static String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) return text;

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("q", text);
            payload.addProperty("source", sourceLang);
            payload.addProperty("target", targetLang);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject result = GSON.fromJson(response.body(), JsonObject.class);
                if (result.has("translatedText")) {
                    return result.get("translatedText").getAsString();
                }
            }
            System.err.println("翻译失败，状态码: " + response.statusCode());
        } catch (Exception e) {
            System.err.println("翻译服务调用异常: " + e.getMessage());
        }
        return null;
    }

    /**
     * 自动检测语言并翻译为目标语言
     */
    public static String translateAuto(String text, String targetLang) {
        return translate(text, "auto", targetLang);
    }
}