package com.writtenbooktransfer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 独立小程序：将 .mcfunction 中的成书每一页 text 独立输出到终端
 * 
 * 使用方法：
 *   java BookPageViewer [文件路径]
 *   若不提供路径，则自动扫描当前目录下的 .mcfunction 文件并让用户选择。
 * 
 * 依赖：Gson (com.google.gson)
 */
public class BookPageViewer {

    public static void main(String[] args) {
        Path filePath = null;

        if (args.length > 0) {
            filePath = Paths.get(args[0]);
            if (!Files.exists(filePath)) {
                System.err.println("文件不存在: " + args[0]);
                return;
            }
        } else {
            filePath = selectFileFromCurrentDir();
            if (filePath == null) {
                System.err.println("未选择任何文件，程序退出。");
                return;
            }
        }

        try {
            List<String> pages = extractPages(filePath);
            displayPages(pages);
        } catch (IOException e) {
            System.err.println("读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 从 .mcfunction 文件中提取所有页面的纯文本
     */
    private static List<String> extractPages(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        List<String> result = new ArrayList<>();

        // 1. 匹配 pages:[ ... ] 块
        Pattern pattern = Pattern.compile("pages\\s*:\\s*\\[(.*)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            System.err.println("在文件中未找到 pages 数据格式！");
            return result;
        }

        String pagesRaw = matcher.group(1);

        // 2. 匹配单引号或双引号内的每一页内容
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

        // 3. 解析每个原始字符串
        for (String raw : rawStrings) {
            try {
                String clean = raw.replace("\\\\", "\\");
                JsonObject obj = JsonParser.parseString(clean).getAsJsonObject();
                String text = obj.has("text") ? obj.get("text").getAsString() : "";
                text = text.replace("\r", "");
                result.add(text);
            } catch (Exception e) {
                // JSON 解析失败时，进行基础转义处理
                String fallback = raw.replace("\\n", "\n")
                                     .replace("\\r", "")
                                     .replace("\\\"", "\"");
                result.add(fallback);
            }
        }
        return result;
    }

    /**
     * 在终端清晰显示每一页的内容
     */
    private static void displayPages(List<String> pages) {
        if (pages.isEmpty()) {
            System.out.println("没有提取到任何页面内容。");
            return;
        }

        System.out.println("\n========================================");
        System.out.println("  成书内容预览（共 " + pages.size() + " 页）");
        System.out.println("========================================\n");

        for (int i = 0; i < pages.size(); i++) {
            System.out.println("---------- 第 " + (i + 1) + " 页 ----------");
            System.out.println(pages.get(i));
            System.out.println("----------------------------------------\n");
        }
    }

    /**
     * 交互式选择当前目录下的 .mcfunction 文件
     */
    private static Path selectFileFromCurrentDir() {
        try {
            Path currentDir = Paths.get("").toAbsolutePath();
            List<Path> files = Files.list(currentDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".mcfunction"))
                    .toList();

            if (files.isEmpty()) {
                System.out.println("当前目录下没有任何 .mcfunction 文件。");
                return null;
            }

            if (files.size() == 1) {
                return files.get(0);
            }

            System.out.println("\n发现多个 .mcfunction 文件：");
            for (int i = 0; i < files.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + files.get(i).getFileName());
            }
            System.out.print("请选择序号 (1-" + files.size() + "): ");
            Scanner scanner = new Scanner(System.in);
            int choice = Integer.parseInt(scanner.nextLine()) - 1;
            if (choice >= 0 && choice < files.size()) {
                return files.get(choice);
            } else {
                System.out.println("无效序号。");
                return null;
            }
        } catch (IOException e) {
            System.err.println("扫描目录出错: " + e.getMessage());
            return null;
        }
    }
}