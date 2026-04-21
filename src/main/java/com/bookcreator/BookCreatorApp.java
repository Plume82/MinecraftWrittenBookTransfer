package com.bookcreator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 手动创建 .mcfunction 成书文件
 * 用户逐页输入文本，可预览，最终输出标准 give 命令文件
 */
public class BookCreatorApp {

    private static final String OUTPUT_DIR = ".";  // 输出到当前目录
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("\n========================================");
        System.out.println("   Minecraft 成书手动创建工具");
        System.out.println("========================================");
        System.out.println("您可以逐页输入书的内容。");
        System.out.println("每页输入完成后按回车，然后选择操作：");
        System.out.println("  • 直接按回车继续输入下一页");
        System.out.println("  • 输入 'preview' 预览当前所有页面");
        System.out.println("  • 输入 'output' 生成 .mcfunction 文件并退出");
        System.out.println("  • 输入 'quit' 放弃并退出");
        System.out.println("----------------------------------------");

        List<String> pages = new ArrayList<>();
        int pageNumber = 1;

        while (true) {
            System.out.println("\n---------- 第 " + pageNumber + " 页 ----------");
            System.out.println("请输入本页内容（可包含换行，输入完成后按回车）：");
            System.out.print("> ");
            String line = scanner.nextLine();

            // 处理特殊命令（必须在一行开头输入）
            String trimmed = line.trim().toLowerCase();
            if (trimmed.equals("preview")) {
                previewPages(pages);
                continue;
            } else if (trimmed.equals("output")) {
                if (pages.isEmpty()) {
                    System.out.println("⚠️ 当前没有任何页面内容，无法输出。");
                    continue;
                }
                outputMcFunction(pages);
                return;
            } else if (trimmed.equals("quit")) {
                System.out.println("已取消创建，未保存任何内容。");
                return;
            }

            // 正常输入：读取多行直到遇到空行（以连续两个回车结束）
            // 但由于用户可能在单行内输入换行符，我们采用更简单的策略：
            // 直接以单行作为一页内容（用户可以手动输入 \n 作为换行符）
            // 为更友好，我们允许用户输入多行，直到输入空行结束本页
            if (line.isEmpty()) {
                // 如果用户第一行就是空的，则跳过本页
                if (pageNumber > pages.size() + 1) {
                    // 已经输入过内容，空行表示结束本页输入
                } else {
                    System.out.println("页面内容不能为空，请重新输入。");
                    continue;
                }
            }

            StringBuilder pageContent = new StringBuilder(line);
            System.out.println("（可继续输入本页剩余内容，直接按回车结束本页）");
            while (true) {
                System.out.print("  → ");
                String nextLine = scanner.nextLine();
                if (nextLine.isEmpty()) {
                    break;
                }
                pageContent.append("\n").append(nextLine);
            }

            pages.add(pageContent.toString());
            System.out.println("✅ 第 " + pageNumber + " 页已保存。");
            pageNumber++;
        }
    }

    /**
     * 预览当前所有页面
     */
    private static void previewPages(List<String> pages) {
        if (pages.isEmpty()) {
            System.out.println("\n暂无任何页面内容。");
            return;
        }
        System.out.println("\n========== 当前预览（共 " + pages.size() + " 页）==========");
        for (int i = 0; i < pages.size(); i++) {
            System.out.println("======第 " + (i + 1) + " 页======");
            System.out.println(pages.get(i));
        }
    }

    /**
     * 生成 .mcfunction 文件
     */
    private static void outputMcFunction(List<String> pages) {
        System.out.print("请输入输出文件名（不含扩展名，默认为 'created_book'）: ");
        String fileName = scanner.nextLine().trim();
        if (fileName.isEmpty()) {
            fileName = "created_book";
        }
        // 移除可能输入的 .mcfunction 后缀
        fileName = fileName.replaceAll("\\.mcfunction$", "");

        File outputFile = new File(OUTPUT_DIR, fileName + ".mcfunction");
        // 处理重名
        if (outputFile.exists()) {
            System.out.print("文件已存在，是否覆盖？(y/N): ");
            String ans = scanner.nextLine().trim().toLowerCase();
            if (!ans.equals("y") && !ans.equals("yes")) {
                System.out.println("已取消输出。");
                return;
            }
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            StringBuilder cmd = new StringBuilder();
            cmd.append("give @p minecraft:writable_book[");
            cmd.append("minecraft:writable_book_content={pages:[");

            for (int i = 0; i < pages.size(); i++) {
                if (i > 0) cmd.append(",");
                // 转义特殊字符
                String page = pages.get(i)
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");
                cmd.append("\"").append(page).append("\"");
            }
            cmd.append("]}]");
            writer.write(cmd.toString());
        } catch (IOException e) {
            System.err.println("写入文件失败: " + e.getMessage());
            return;
        }

        System.out.println("✅ 成功生成文件: " + outputFile.getAbsolutePath());
        System.out.println("您现在可以在游戏中使用 /function 或通过传输模块加载此文件。");
    }
}