package com.booktypesetting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypesettingApp {
    public static void main(String[] args) {
    System.out.println("排版工具模块开发中...");
    }
    /**
     * 启动排版工具（由 Main 调用）
     * @param filePath 文件路径
     * @param initLinesPerPage 初始每页行数
     * @param initMaxWidth 初始最大行宽
     */
    public static void launch(String filePath, int initLinesPerPage, double initMaxWidth) {
        List<String> originalPages = extractPagesFromFile(filePath);
        if (originalPages.isEmpty()) {
            System.out.println("未能从文件中提取到任何书本内容。");
            return;
        }

        int linesPerPage = initLinesPerPage;
        double maxLineWidth = initMaxWidth;
        BookConfig config = new BookConfig(linesPerPage, maxLineWidth, null, null);
        TextFormatter formatter = new TextFormatter(config);
        Scanner scanner = new Scanner(System.in);

        while (true) {
            // 每次循环重新计算排版（基于当前配置）
            String fullText = String.join("\n", originalPages);
            List<String> lines = formatter.splitIntoLines(fullText);
            List<String> pages = formatter.formatPages(lines);

            System.out.println("\n--- 文本排版工具 ---");
            System.out.println("当前配置: 每页 " + linesPerPage + " 行, 最大行宽 " + maxLineWidth);
            System.out.println("重新分页后共 " + pages.size() + " 页。");
            System.out.println("----------------------------------------");
            System.out.println("p - 预览分页结果");
            System.out.println("l - 修改每页行数");
            System.out.println("w - 修改最大行宽");
            System.out.println("r - 恢复默认配置 (14行, 57.0宽)");
            System.out.println("s - 保存当前排版结果（覆盖原始数据，用于传输）");
            System.out.println("q - 返回主菜单");
            System.out.print("请选择: ");

            String opt = scanner.nextLine().trim().toLowerCase();
            switch (opt) {
                case "p":
                    previewPages(pages);
                    break;
                case "l":
                    System.out.print("请输入新的每页行数 (当前 " + linesPerPage + "): ");
                    try {
                        int newLines = Integer.parseInt(scanner.nextLine());
                        if (newLines > 0) {
                            linesPerPage = newLines;
                            config = new BookConfig(linesPerPage, maxLineWidth, null, null);
                            formatter = new TextFormatter(config);
                        } else {
                            System.out.println("行数必须大于0。");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("输入无效。");
                    }
                    break;
                case "w":
                    System.out.print("请输入新的最大行宽 (当前 " + maxLineWidth + "): ");
                    try {
                        double newWidth = Double.parseDouble(scanner.nextLine());
                        if (newWidth > 0) {
                            maxLineWidth = newWidth;
                            config = new BookConfig(linesPerPage, maxLineWidth, null, null);
                            formatter = new TextFormatter(config);
                        } else {
                            System.out.println("宽度必须大于0。");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("输入无效。");
                    }
                    break;
                case "r":
                    linesPerPage = 14;
                    maxLineWidth = 57.0;
                    config = new BookConfig(linesPerPage, maxLineWidth, null, null);
                    formatter = new TextFormatter(config);
                    System.out.println("已恢复默认配置。");
                    break;
                case "s":
                    originalPages = new ArrayList<>(pages);
                    System.out.println("排版结果已保存，将用于后续传输。");
                    break;
                case "q":
                    return;
                default:
                    System.out.println("无效选项。");
            }
        }
    }

    private static void previewPages(List<String> pages) {
        System.out.println("\n========== 分页预览 ==========");
        Scanner sc = new Scanner(System.in);
        for (int i = 0; i < pages.size(); i++) {
            System.out.println("--- 第 " + (i + 1) + " 页 ---");
            System.out.println(pages.get(i));
            System.out.println();
            if ((i + 1) % 5 == 0 && i + 1 < pages.size()) {
                System.out.print("按 Enter 继续预览，输入 q 退出预览: ");
                String in = sc.nextLine().trim().toLowerCase();
                if (in.equals("q")) break;
            }
        }
        System.out.println("========== 预览结束 ==========");
    }

    private static List<String> extractPagesFromFile(String filePath) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            return extractPagesFromMcFunction(content);
        } catch (IOException e) {
            System.err.println("读取文件失败：" + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static List<String> extractPagesFromMcFunction(String content) {
        List<String> result = new ArrayList<>();
        Pattern pattern = Pattern.compile("pages\\s*:\\s*\\[(.*)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            System.err.println("在文件中未找到 pages 数据格式！");
            return result;
        }
        String pagesRaw = matcher.group(1);
        List<String> rawStrings = new ArrayList<>();
        Pattern quotePattern = Pattern.compile("'(.*?)'");
        Matcher quoteMatcher = quotePattern.matcher(pagesRaw);
        while (quoteMatcher.find()) {
            rawStrings.add(quoteMatcher.group(1));
        }
        if (rawStrings.isEmpty()) {
            quotePattern = Pattern.compile("\"(.*?)\"");
            quoteMatcher = quotePattern.matcher(pagesRaw);
            while (quoteMatcher.find()) {
                rawStrings.add(quoteMatcher.group(1));
            }
        }
        for (String raw : rawStrings) {
            try {
                String clean = raw.replace("\\\\", "\\");
                JsonObject obj = JsonParser.parseString(clean).getAsJsonObject();
                String text = obj.has("text") ? obj.get("text").getAsString() : "";
                text = text.replace("\r", "");
                result.add(text);
            } catch (Exception e) {
                String fallback = raw.replace("\\n", "\n")
                                     .replace("\\r", "")
                                     .replace("\\\"", "\"");
                result.add(fallback);
            }
        }
        return result;
    }
}