package com.functionlib;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class DeepSeekAnalyzer {



    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String API_KEY = 
        System.getProperty("DEEPSEEK_API_KEY", System.getenv("DEEPSEEK_API_KEY"));
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // 配置OkHttp客户端：连接池、超时、重试
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)      // 适当增加超时时间
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(200, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .build();

    // 并发线程池：50个并发
    private static final ExecutorService executor = Executors.newFixedThreadPool(200);

    static {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }));
}
// 🆕 添加公共访问方法
public static ExecutorService getExecutor() {
    return executor;
}

    // 限流控制：每秒最多发送10个请求（可根据需要调整）
    private static final RateLimiter rateLimiter = new RateLimiter(200); // 10 permits per second

    /**
     * 调用DeepSeek API进行对话，支持自动重试和指数退避
     */
    public static String chat(String systemPrompt, String userMessage) throws IOException {
        // 获取限流许可
        //rateLimiter.acquire();
        
        int maxRetries = 3;
        IOException lastException = null;
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Map<String, Object> requestBody = Map.of(
                    "model", "deepseek-chat",
                    "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 2000,
                    "response_format", Map.of("type", "json_object")
                );
                
                String json = objectMapper.writeValueAsString(requestBody);
                Request request = new Request.Builder()
                        .url(API_URL)
                        .header("Authorization", "Bearer " + API_KEY)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        return extractContentFromResponse(responseBody);
                    } else {
                        if (response.code() == 429) {
                            // 速率超限：指数退避重试
                            long waitMs = (long) Math.pow(2, attempt) * 1000;
                            System.err.println("触发限流(429)，等待 " + waitMs/1000 + " 秒后重试...");
                            try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            continue;
                        } else if (response.code() == 401) {
                            throw new IOException("API Key无效或已过期（401）");
                        }
                        throw new IOException("API调用失败: " + response.code());
                    }
                }
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxRetries - 1) {
                    long waitMs = (long) Math.pow(2, attempt) * 500;
                    try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw lastException != null ? lastException : new IOException("重试次数耗尽");
    }

    /**
     * 简单限流器：使用令牌桶算法
     */
    private static class RateLimiter {
        private final Semaphore semaphore;
        private final int permitsPerSecond;
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        
        RateLimiter(int permitsPerSecond) {
            this.permitsPerSecond = permitsPerSecond;
            this.semaphore = new Semaphore(permitsPerSecond);
            scheduler.scheduleAtFixedRate(() -> {
                semaphore.release(permitsPerSecond - semaphore.availablePermits());
            }, 1, 1, TimeUnit.SECONDS);
        }
        
        void acquire() {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String extractContentFromResponse(String responseBody) throws IOException {
        var jsonNode = objectMapper.readTree(responseBody);
        return jsonNode.path("choices").get(0).path("message").path("content").asText();
    }

    // ==================== 提示词（保持不变） ====================
  private static final String VALUE_SYSTEM_PROMPT = """
    你是一个专业的 Minecraft 成书内容处理专家。请完成以下两项任务：

    【任务一：内容评估（满分100分）】
    请从以下四个维度对书籍内容进行综合评分（每项满分按标注分值计算）：

    1. 内容完整性（满分25分）
       - 书籍内容表达了一个完整的意思（20-25分）。
       - 内容明显中断、无厘头、纯符号堆砌，给低分（0-8分）。
       - 中间状态（如意思基本完整但有少量无关字符）给9-19分。

    2. 语言表达（满分15分）
       - 语句通顺，无明显错别字或乱码（12-15分）。
       - 存在少量错别字或不通顺之处，但不影响理解（6-11分）。
       - 大量乱码、语句破碎，难以阅读（0-5分）。
       - Minecraft 特有的指令、坐标、符号在合理语境中出现，不视为乱码。

    3. 原创性或趣味性（满分20分）
       - 内容具有创意、幽默感，或能提供情绪价值（如文学、日记、诗歌）或实用价值（如教程、攻略、指令集）。
       - 若内容记录服务器历史、玩家事件、版本变迁等具有史料研究价值的信息，可酌情加分。
       - 综合评估后给出0-20分的总分。

    4. 留存价值（满分40分）
       - 是否值得保存或分享给其他玩家。从以下三个子项综合考量（无需单独列出分数，直接给出0-40分的总分）：
         · 纪念意义：如服务器告别留言、重要事件记录
         · 文化包容性：是否尊重不同信仰和少数群体，避免冒犯性内容
         · 整体感染力：阅读后是否让人印象深刻、愿意推荐

    总分 = 内容完整性得分 + 语言表达得分 + 原创性或趣味性得分 + 留存价值得分（范围0-100分）。

    【任务二：语言判断与翻译】
    - 请判断原文的主要语言。
    - 如果原文**不是简体中文**（例如英文、日文、韩文等），你必须将其**全文**翻译成简体中文，并将翻译结果放入 translation 字段。
    - 如果原文**已经是简体中文**，则 translation 字段填入字符串 "已经是中文，无需翻译"。
    - **注意：translation 字段必须严格按上述规则填写，不得为空。**

    必须严格按照JSON格式输出，不要输出任何JSON之外的文字、注释或解释。
    输出格式：
    {"score": 总分, "comment": "简短评语(不超过30字)", "translation": "按规则填写的翻译内容或提示"}

    示例1（英文书籍）：
    {"score": 96, "comment": "情感真挚的服务器告别信", "translation": "致所有并肩作战的伙伴们：今天，我们的服务器将正式关闭。这段旅程充满了欢笑与泪水……（此处为完整译文）"}

    示例2（中文书籍）：
    {"score": 85, "comment": "有趣的小诗", "translation": "已经是中文，无需翻译"}
    """;


    /**
 * 清理 JSON 字符串中的非法转义字符
 */
/**
 * 清理 AI 返回的 JSON 字符串，确保符合 RFC 8259 标准
 */
private static String sanitizeJsonString(String json) {
    if (json == null || json.isEmpty()) return json;
    
    // 1. 移除JSON之前的非JSON文本（有些AI会在前面加解释）
    int jsonStart = json.indexOf('{');
    if (jsonStart > 0) {
        json = json.substring(jsonStart);
    }
    int jsonEnd = json.lastIndexOf('}');
    if (jsonEnd != -1 && jsonEnd < json.length() - 1) {
        json = json.substring(0, jsonEnd + 1);
    }
    
    StringBuilder sb = new StringBuilder();
    boolean inString = false;
    boolean escaped = false;
    
    for (int i = 0; i < json.length(); i++) {
        char c = json.charAt(i);
        
        if (!inString) {
            if (c == '"') {
                inString = true;
                sb.append(c);
            } else {
                sb.append(c);
            }
        } else {
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                sb.append(c);
                escaped = true;
            } else if (c == '"') {
                sb.append(c);
                inString = false;
            } else if (c == '\n' || c == '\r' || c == '\t') {
                // 将实际控制字符转换为转义序列
                switch (c) {
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                }
            } else {
                sb.append(c);
            }
        }
    }
    // 如果字符串未闭合，补上引号
    if (inString) {
        sb.append('"');
    }
    return sb.toString();
}

// 新增：从原始响应提取评语
private static String extractCommentFromRawResponse(String response) {
    try {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"comment\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(response);
        if (m.find()) {
            return m.group(1);
        }
    } catch (Exception e) { }
    return "评估异常，保留";
}

// 新增：从原始响应提取翻译
private static String extractTranslationFromRawResponse(String response) {
    try {
        // 尝试匹配 translation 字段（可能跨行）
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"translation\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(response);
        if (m.find()) {
            String trans = m.group(1);
            // 反转义
            trans = trans.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
            return trans;
        }
    } catch (Exception e) { }
    return "（翻译缺失）";
}
/**
 * 降级处理：从原始响应中尝试提取分数
 */
private static int extractScoreFromRawResponse(String response) {
    try {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"score\"\\s*:\\s*(\\d+)");
        java.util.regex.Matcher m = p.matcher(response);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
    } catch (Exception e) {
        // 忽略
    }
    return 100; // 默认保守保留
}


    // ==================== 原有串行评估方法（保持不变） ====================
    public static ValueAssessment assessValue(String bookContent) throws IOException {
    String userMessage = "请评估以下书籍内容：\n\n" + truncateContent(bookContent, 4000);
    String jsonResponse = chat(VALUE_SYSTEM_PROMPT, userMessage);
    try {
        String cleanedJson = sanitizeJsonString(jsonResponse);
        var jsonNode = objectMapper.readTree(cleanedJson);
        int score = Math.min(100, jsonNode.path("score").asInt());
        String comment = jsonNode.path("comment").asText();
        String translation = jsonNode.path("translation").asText();
        return new ValueAssessment(score, comment, translation);
    } catch (Exception e) {
        System.err.println("解析价值评估JSON失败: " + e.getMessage());
        // 保存原始响应到临时文件
        try {
            java.nio.file.Files.writeString(java.nio.file.Paths.get("debug_json_response.txt"), jsonResponse);
            System.err.println("原始JSON已保存至 debug_json_response.txt");
        } catch (IOException ex) { }
        // 尝试使用正则提取
        int fallbackScore = extractScoreFromRawResponse(jsonResponse);
        String fallbackComment = extractCommentFromRawResponse(jsonResponse);
        String fallbackTranslation = extractTranslationFromRawResponse(jsonResponse);
        return new ValueAssessment(fallbackScore, fallbackComment, fallbackTranslation);
    }
}

/**
 * 纯翻译功能：将文本翻译为目标语言（默认简体中文）
 * @param text 待翻译文本
 * @param targetLang 目标语言代码，如 "zh", "en"，若为 null 则默认中文
 * @return 翻译后的文本，失败返回 null
 */
/**
 * 纯翻译功能：将文本翻译为目标语言（默认简体中文）
 * 要求模型输出 JSON 格式，以兼容原有的 chat 方法（强制 json_object）
 */
public static String translateText(String text, String targetLang) throws IOException {
    if (text == null || text.trim().isEmpty()) {
        return "";
    }
    String lang = (targetLang == null || targetLang.isEmpty()) ? "简体中文" : targetLang;
    String systemPrompt = String.format(
        "你是一个专业的翻译引擎。请将用户提供的文本翻译成%s。\n\n" +
        "你必须严格按照以下JSON格式输出，不要添加任何额外的解释、注释或文字：\n" +
        "{\"translation\": \"你的翻译结果\"}\n\n" +
        "注意：如果原文已经是目标语言，则 translation 字段填入字符串 \"已经是目标语言，无需翻译\"。",
        lang
    );
    String userMessage = "请翻译以下内容：\n\n" + truncateContent(text, 4000);
    
    // 调用原有的 chat 方法（强制 json_object）
    String jsonResponse = chat(systemPrompt, userMessage);
    
    // 解析 JSON 提取译文
    try {
        String cleaned = sanitizeJsonString(jsonResponse);
        var jsonNode = objectMapper.readTree(cleaned);
        return jsonNode.path("translation").asText();
    } catch (Exception e) {
        System.err.println("解析翻译JSON失败，尝试降级提取: " + e.getMessage());
        // 降级：正则提取
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"translation\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(jsonResponse);
        if (m.find()) {
            String trans = m.group(1);
            return trans.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return "翻译结果解析失败，请重试";
    }
}





        // ==================== 摘要与标签方法（补充） ====================
    private static final String SUMMARY_SYSTEM_PROMPT = """
        你是一个专业的书籍内容分析师。请阅读用户提供的Minecraft成书内容，生成一段简洁的摘要。
        要求：
        1. 摘要长度控制在100字以内。
        2. 只返回摘要文本，不要包含任何JSON格式或额外解释。
        """;

    private static final String TAGS_SYSTEM_PROMPT = """
        你是一个专业的书籍内容分类专家。请阅读用户提供的Minecraft成书内容，提取3-5个最能概括其主题的标签。
        标签示例："红石", "建筑", "生存", "指令", "故事", "教程"
        要求：
        1. 必须严格按照JSON格式输出，格式为：{"tags": ["标签1", "标签2", ...]}
        2. 不要输出任何JSON之外的文字、标记或解释。
        """;

    /**
     * 生成书籍摘要
     */
    public static String generateSummary(String bookContent) throws IOException {
        return chat(SUMMARY_SYSTEM_PROMPT, "请分析以下书籍内容：\n\n" + truncateContent(bookContent, 4000));
    }

    /**
     * 提取书籍标签
     */
    public static List<String> extractTags(String bookContent) throws IOException {
        String jsonResponse = chat(TAGS_SYSTEM_PROMPT, "请分析以下书籍内容：\n\n" + truncateContent(bookContent, 4000));
        try {
            var jsonNode = objectMapper.readTree(jsonResponse);
            var tagsNode = jsonNode.path("tags");
            List<String> tags = new ArrayList<>();
            for (var tag : tagsNode) {
                tags.add(tag.asText());
            }
            return tags;
        } catch (Exception e) {
            System.err.println("解析标签JSON失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }




    private static String truncateContent(String content, int maxChars) {
        if (content == null) return "";
        if (content.length() <= maxChars) return content;
        return content.substring(0, maxChars) + "\n\n...(内容过长已截断)";
    }

    // ==================== 🆕 真正的并发评估（推荐） ====================
    /**
     * 并发评估多本书籍，所有请求同时发出，统一等待完成
     * 使用 CompletableFuture.allOf() 实现真正的并发
     */
    public static Map<FunctionlibApp.BookEntry, ValueAssessment> assessValuesConcurrent(
            List<FunctionlibApp.BookEntry> books) {
        Map<FunctionlibApp.BookEntry, CompletableFuture<ValueAssessment>> futures = new LinkedHashMap<>();
        
        // 提交所有任务，立即返回 Future
        for (FunctionlibApp.BookEntry book : books) {
            CompletableFuture<ValueAssessment> future = CompletableFuture.supplyAsync(() -> {
                String content = Editlib.readBookContent(book.getFile());
                if (content == null) {
                    return new ValueAssessment(0, "读取失败", "bookContent");
                }
                try {
                    return assessValue(content);
                } catch (IOException e) {
                    System.err.println("评估《" + book.getTitle() + "》时出错: " + e.getMessage());
                    return new ValueAssessment(0, "评估失败: " + e.getMessage(), "bookContent");
                }
            }, executor);
            futures.put(book, future);
        }
        
        // 等待所有任务完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.values().toArray(new CompletableFuture[0]));
        allFutures.join();   // <-- 关键：阻塞直到所有任务完成
        // 收集结果
        Map<FunctionlibApp.BookEntry, ValueAssessment> results = new LinkedHashMap<>();
        for (Map.Entry<FunctionlibApp.BookEntry, CompletableFuture<ValueAssessment>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get());
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("获取《" + entry.getKey().getTitle() + "》结果失败: " + e.getMessage());
                results.put(entry.getKey(), new ValueAssessment(0, "评估异常", "bookContent"));
            }
        }
        return results;
    }

    // 保留旧方法兼容性，但标记为已弃用
    @Deprecated
    public static Map<FunctionlibApp.BookEntry, CompletableFuture<ValueAssessment>> assessValuesConcurrently(
            List<FunctionlibApp.BookEntry> books, boolean dummy) {
        // 兼容旧调用，内部调用新方法，但返回类型不同
        Map<FunctionlibApp.BookEntry, CompletableFuture<ValueAssessment>> futures = new LinkedHashMap<>();
        for (FunctionlibApp.BookEntry book : books) {
            futures.put(book, CompletableFuture.completedFuture(null));
        }
        return futures;
    }

    public static void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 内部数据类（保持不变） ====================
    public static class ValueAssessment {
        
        public final int score;
        public final String comment;
        public final String translation;

         public ValueAssessment(int score, String comment, String translation) {
            this.score = score;
            this.comment = comment;
            this.translation = translation;
    
        }
        public boolean shouldKeep() { return score >= 60; }
    }
}