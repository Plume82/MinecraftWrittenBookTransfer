package com.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * .mcfunction 文件解析工具（纯字符串版，状态机提取页面）
 */
public class McFunctionParser {

    public static List<String> extractPagesFromFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        return extractPagesFromContent(content);
    }

    public static List<String> extractPagesFromContent(String content) {
        List<String> result = new ArrayList<>();

        // 1. 定位 pages:[ 位置
        int startIdx = content.indexOf("pages:");
        if (startIdx == -1) {
            startIdx = content.indexOf("pages :");
        }
        if (startIdx == -1) {
            System.err.println("在文件中未找到 pages 数据格式！");
            return result;
        }

        // 找到 pages 后的 '['
        int bracketStart = content.indexOf('[', startIdx);
        if (bracketStart == -1) {
            System.err.println("pages 格式错误：找不到 '['");
            return result;
        }

        // 2. 提取页面数组内的每个字符串（处理转义引号）
        List<String> pageStrings = extractPageStrings(content, bracketStart + 1);
        
        // 3. 对每个页面字符串提取纯文本
        for (String pageStr : pageStrings) {
            String plain = extractPlainTextFromPageString(pageStr);
            if (!plain.isEmpty() && !isJsonSyntaxGarbage(plain)) {
                result.add(plain);
            }
        }

        return result;
    }

    /**
     * 从 '[' 之后开始解析，提取每个页面元素的原始字符串（不含外层引号）
     */
    private static List<String> extractPageStrings(String content, int start) {
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        char stringChar = 0;

        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);

            if (!inString) {
                if (c == '"' || c == '\'') {
                    // 开始一个字符串
                    inString = true;
                    stringChar = c;
                    current.setLength(0);
                } else if (c == ']') {
                    // 数组结束
                    break;
                }
                // 忽略其他字符（逗号、空格等）
                continue;
            }

            // 在字符串内部
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == stringChar) {
                // 字符串结束
                inString = false;
                // 将提取的字符串（已去除转义影响）保存
                pages.add(unescape(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        return pages;
    }

    /**
     * 从一个页面原始字符串中提取最终纯文本。
     * 该字符串可能为：
     *   - 纯文本（无JSON）：直接返回
     *   - JSON 对象字符串：{"text":"内容"} 或 转义后的 "{\"text\":\"内容\"}"
     */
    private static String extractPlainTextFromPageString(String pageStr) {
        // 首先处理双重转义：如果 pageStr 本身看起来像一个 JSON 对象字符串（以 '{' 开头）
        // 并且包含 \" ，则它可能是被整体字符串包裹的 JSON。
        // 我们尝试将其作为 JSON 字符串解析，提取 text 字段。
        // 由于我们不用 JSON 库，采用正则提取所有可能的 "text":"..." 值，并拼接。

        // 策略：提取所有出现的 "text":"..." 或 'text':'...' 中的内容
        // 这样即使有 extra 数组也能合并。
        StringBuilder resultText = new StringBuilder();
        java.util.regex.Pattern textPattern = java.util.regex.Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        java.util.regex.Matcher m = textPattern.matcher(pageStr);
        while (m.find()) {
            String text = unescape(m.group(1));
            resultText.append(text);
        }

        if (resultText.length() > 0) {
            return resultText.toString();
        }

        // 如果没有匹配到 text 字段，但 pageStr 看起来像纯字符串（不是以 { 开头），直接返回它
        String trimmed = pageStr.trim();
        if (!trimmed.startsWith("{")) {
            return pageStr;
        }

        // 最后回退：尝试提取第一个字符串值
        // 极简情况：{"text":"内容"} 用简单截取
        int textIdx = pageStr.indexOf("\"text\"");
        if (textIdx != -1) {
            int colon = pageStr.indexOf(':', textIdx);
            if (colon != -1) {
                int firstQuote = pageStr.indexOf('"', colon + 1);
                if (firstQuote != -1) {
                    int secondQuote = pageStr.indexOf('"', firstQuote + 1);
                    if (secondQuote != -1) {
                        return unescape(pageStr.substring(firstQuote + 1, secondQuote));
                    }
                }
            }
        }

        return pageStr; // 实在无法提取，返回原字符串
    }

    /**
     * 反转义字符串中的常见转义序列
     */
    private static String unescape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); i++; break;
                    case 'r': sb.append('\r'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    case '"': sb.append('"'); i++; break;
                    case '\'': sb.append('\''); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    default: sb.append(c); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 判断字符串是否仅由 JSON 语法垃圾组成
     */
    private static boolean isJsonSyntaxGarbage(String s) {
        String trimmed = s.trim();
        return trimmed.matches("[\\{\\}\\[\\]:,\\\\\"]+");
    }
}