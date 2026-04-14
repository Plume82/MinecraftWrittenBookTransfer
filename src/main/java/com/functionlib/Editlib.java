package com.functionlib;

import com.common.McFunctionParser;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 书籍编辑工具库
 * 提供删除、添加、打开浏览 .mcfunction 文件的功能
 */
public class Editlib {

    /**
     * 从文件名中提取基础书名（去除世代后缀和重复数字后缀）
     */
    private static String extractBaseTitle(String fileName) {
        String base = fileName.replaceFirst("\\.mcfunction$", "");
        base = base.replaceAll("[（(][^）)]*[）)]", "");
        base = base.replaceAll("_\\d+$", "");
        return base.trim();
    }
    /**
 * 翻译整本书并输出完整译文（不修改原文件）
 * @param pages 原始页面列表
 * @param targetLang 目标语言代码
 */
/**
 * 翻译整本书并输出完整译文（不修改原文件）
 * 将所有页面拼接为一个整体后一次性翻译，保证语义连贯
 * @param pages 原始页面列表
 * @param targetLang 目标语言代码
 */
private static void translateFullBook(List<String> pages, String targetLang) {
    System.out.println("\n开始翻译整本书，共 " + pages.size() + " 页，目标语言: " + targetLang);
    System.out.println("正在拼接全文并提交翻译，请稍候...");

    // 将所有页面拼接为一个完整字符串，页面之间用两个换行符分隔（模拟自然段落间距）
    String fullText = String.join("\n\n", pages);
    
    String translated = TranslateLib.translateAuto(fullText, targetLang);
    
    if (translated != null) {
        System.out.println("\n========== 全书译文 ==========");
        System.out.println(translated);
        System.out.println("==========================================");
        System.out.println("翻译完成。");
    } else {
        System.out.println("翻译失败，请检查翻译服务是否运行。");
    }
}
    /**
     * 删除书籍（智能匹配基础书名，内容去重后展示合并表格供选择）
     */
    public static void deleteBook(File folder, String namePattern, List<FunctionlibApp.BookEntry> allBooks) {
        if (folder == null || !folder.isDirectory()) {
            System.out.println("当前文件夹无效。");
            return;
        }
        if (namePattern.isEmpty()) {
            System.out.println("请输入文件名。示例: del 我的书");
            return;
        }

        List<FunctionlibApp.BookEntry> matched = filterBooksByBaseTitle(allBooks, namePattern);
        if (matched.isEmpty()) {
            System.out.println("未找到匹配的书籍: " + namePattern);
            return;
        }

        MergedBookSelection selection = mergeAndSelect(matched, "删除");
        if (selection == null) return;

        // 删除该代表书籍对应的所有副本文件
        int deletedCount = 0;
        for (FunctionlibApp.BookEntry book : selection.allCopies) {
            File targetFile = new File(folder, book.fileName);
            try {
                Files.deleteIfExists(targetFile.toPath());
                deletedCount++;
            } catch (IOException e) {
                System.err.println("删除失败: " + book.fileName + " - " + e.getMessage());
            }
        }
        System.out.printf("已删除书籍《%s》及其 %d 个副本。\n", selection.representative.title, deletedCount - 1);
    }

    /**
     * 打开书籍浏览（智能匹配基础书名，内容去重后展示合并表格供选择）
     */
    public static void openBook(File folder, String namePattern, List<FunctionlibApp.BookEntry> allBooks) {
        if (folder == null || !folder.isDirectory()) {
            System.out.println("当前文件夹无效。");
            return;
        }
        if (namePattern.isEmpty()) {
            System.out.println("请输入文件名。示例: open 我的书");
            return;
        }

        while (true) {
            List<FunctionlibApp.BookEntry> matched = filterBooksByBaseTitle(allBooks, namePattern);
            if (matched.isEmpty()) {
                System.out.println("未找到匹配的书籍: " + namePattern);
                return;
            }

            MergedBookSelection selection = mergeAndSelect(matched, "打开");
            if (selection == null) {
                // 用户选择取消（输入0）或无效输入，询问是否退出
               return;
            }

            // 打开代表书籍进行浏览
            File targetFile = new File(folder, selection.representative.fileName);
            List<String> pages;
            try {
                pages = McFunctionParser.extractPagesFromFile(targetFile.toPath());
            } catch (IOException e) {
                System.err.println("读取文件失败: " + e.getMessage());
                return;
            }
            if (pages.isEmpty()) {
                System.out.println("该书籍没有页面内容。");
                return;
            }

            System.out.println("\n已打开: " + selection.representative.fileName + " (共 " + pages.size() + " 页)");
            System.out.println("输入 'page [页码]' 翻页，'translate [目标语言]' 翻译当前页，'close' 关闭并返回列表。");

            
            int currentPage = 1;
            displayPageContent(pages, currentPage);

            Scanner scanner = new Scanner(System.in);
                        while (true) {
                System.out.print("\n[浏览模式] 请输入指令 (page [页码], translate [语言], translate full [语言], close): ");
                String input = scanner.nextLine().trim().toLowerCase();
                if (input.equals("close")) {
                    System.out.println("关闭书籍，返回列表。");
                    break;
                } else if (input.startsWith("page ")) {
                    try {
                        int page = Integer.parseInt(input.substring(5).trim());
                        if (page < 1 || page > pages.size()) {
                            System.out.printf("页码超出范围，有效页码 1-%d\n", pages.size());
                        } else {
                            currentPage = page;
                            displayPageContent(pages, currentPage);
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("页码格式错误，示例: page 2");
                    }
                } else if (input.startsWith("translate full ")) {
                    String targetLang = input.substring(15).trim();
                    if (targetLang.isEmpty()) {
                        System.out.println("请指定目标语言代码，例如: translate full zh");
                        continue;
                    }
                    translateFullBook(pages, targetLang);
                }else if (input.equals("print full")) {
                    System.out.println("\n========== 全书内容 ==========");
                    for (int i = 0; i < pages.size(); i++) {
                        System.out.println(pages.get(i));
                        if (i < pages.size() - 1) {
                            System.out.println(); // 页面之间空一行，保持可读性
                        }
                    }
                    System.out.println("================================");
                }else if (input.startsWith("translate ")) {
                    String targetLang = input.substring(10).trim();
                    if (targetLang.isEmpty()) {
                        System.out.println("请指定目标语言代码，例如: translate zh");
                        continue;
                    }
                    // 翻译当前页
                    String originalText = pages.get(currentPage - 1);
                    System.out.println("\n正在翻译第 " + currentPage + " 页 (目标语言: " + targetLang + ")...");
                    String translated = TranslateLib.translateAuto(originalText, targetLang);
                    if (translated != null) {
                        System.out.println("\n========== 第 " + currentPage + " 页 译文 ==========");
                        System.out.println(translated);
                        System.out.println("==========================================");
                    } else {
                        System.out.println("翻译失败，请检查翻译服务是否运行。");
                    }
                } else if (input.equals("translate")) {
                    System.out.println("请指定目标语言，例如: translate zh");
                } else {
                    System.out.println("无效指令。可用: page [页码], translate [语言代码], translate full [语言代码], close");
                }
            }
            // 浏览结束，回到外层 while 循环，重新显示书籍列表
        }
    }

    /**
     * 根据基础书名过滤书籍列表
     */
    private static List<FunctionlibApp.BookEntry> filterBooksByBaseTitle(List<FunctionlibApp.BookEntry> allBooks, String pattern) {
        List<FunctionlibApp.BookEntry> result = new ArrayList<>();
        for (FunctionlibApp.BookEntry book : allBooks) {
            String base = extractBaseTitle(book.fileName);
            if (base.equalsIgnoreCase(pattern) || base.toLowerCase().contains(pattern.toLowerCase())) {
                result.add(book);
            }
        }
        return result;
    }

    /**
     * 将匹配到的原始书籍按内容合并去重，展示表格并让用户选择一个代表书籍。
     * @return 选中的代表书籍及其所有副本的列表，若取消则返回 null
     */
    private static MergedBookSelection mergeAndSelect(List<FunctionlibApp.BookEntry> matched, String action) {
        // 按完整内容分组
        Map<String, List<FunctionlibApp.BookEntry>> contentGroups = new LinkedHashMap<>();
        for (FunctionlibApp.BookEntry book : matched) {
            String content = readBookContent(book.file);
            if (content == null) continue;
            contentGroups.computeIfAbsent(content, k -> new ArrayList<>()).add(book);
        }

        // 构建合并条目
        List<MergedBookItem> items = new ArrayList<>();
        for (Map.Entry<String, List<FunctionlibApp.BookEntry>> entry : contentGroups.entrySet()) {
            List<FunctionlibApp.BookEntry> group = entry.getValue();
            items.add(new MergedBookItem(group));
        }

        if (items.isEmpty()) {
            System.out.println("没有可读取内容的书籍。");
            return null;
        }

        // 按书名排序
        items.sort(Comparator.comparing(a -> a.representative.title, String.CASE_INSENSITIVE_ORDER));

        // 展示表格
        System.out.println("\n找到 " + items.size() + " 本内容不同的匹配书籍：");
        System.out.println("┌────┬──────────────────────────────────────┬────────────────────┬──────────┬────────┬────────┐");
        System.out.println("│序号│ 书名                                 │ 作者               │ 代表世代 │ 总副本 │ 原稿数 │");
        System.out.println("├────┼──────────────────────────────────────┼────────────────────┼──────────┼────────┼────────┤");
        int idx = 1;
        for (MergedBookItem item : items) {
            System.out.printf("│ %2d │ %-36s │ %-18s │ %-8s │ %6d │ %6d │\n",
                    idx++,
                    truncate(item.representative.title, 36),
                    truncate(item.representative.author, 18),
                    truncate(item.representative.generation, 8),
                    item.totalCopies,
                    item.originalCount);
        }
        System.out.println("└────┴──────────────────────────────────────┴────────────────────┴──────────┴────────┴────────┘");

        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入要" + action + "的序号 (输入 0 取消): ");
        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("输入无效，取消操作。");
            return null;
        }
        if (choice == 0) {
            System.out.println("已取消。");
            return null;
        }
        if (choice < 1 || choice > items.size()) {
            System.out.println("序号超出范围，取消操作。");
            return null;
        }

        MergedBookItem selected = items.get(choice - 1);
        return new MergedBookSelection(selected.representative, selected.allCopies);
    }


    /**
     * 合并条目（用于表格展示）
     */
    private static class MergedBookItem {
        FunctionlibApp.BookEntry representative;
        List<FunctionlibApp.BookEntry> allCopies;
        int totalCopies;
        int originalCount;

        MergedBookItem(List<FunctionlibApp.BookEntry> group) {
            this.allCopies = group;
            this.representative = selectRepresentative(group);
            this.totalCopies = group.size();
            this.originalCount = (int) group.stream().filter(b -> "原稿".equals(b.generation)).count();
        }

        private static FunctionlibApp.BookEntry selectRepresentative(List<FunctionlibApp.BookEntry> group) {
            // 优先原稿
            List<FunctionlibApp.BookEntry> originals = new ArrayList<>();
            for (FunctionlibApp.BookEntry b : group) {
                if ("原稿".equals(b.generation)) originals.add(b);
            }
            List<FunctionlibApp.BookEntry> candidates = originals.isEmpty() ? group : originals;

            // 其次文件名不带 _数字 后缀
            List<FunctionlibApp.BookEntry> noSuffix = new ArrayList<>();
            Pattern dupPattern = Pattern.compile("_\\d+$");
            for (FunctionlibApp.BookEntry b : candidates) {
                String base = b.fileName.replaceFirst("\\.mcfunction$", "");
                if (!dupPattern.matcher(base).find()) {
                    noSuffix.add(b);
                }
            }
            if (!noSuffix.isEmpty()) candidates = noSuffix;

            // 最后选文件名最短/字母序
            FunctionlibApp.BookEntry keeper = candidates.get(0);
            for (FunctionlibApp.BookEntry b : candidates) {
                if (b.fileName.length() < keeper.fileName.length()) {
                    keeper = b;
                } else if (b.fileName.length() == keeper.fileName.length() && b.fileName.compareTo(keeper.fileName) < 0) {
                    keeper = b;
                }
            }
            return keeper;
        }
    }

    private static class MergedBookSelection {
        FunctionlibApp.BookEntry representative;
        List<FunctionlibApp.BookEntry> allCopies;
        MergedBookSelection(FunctionlibApp.BookEntry r, List<FunctionlibApp.BookEntry> copies) {
            this.representative = r;
            this.allCopies = copies;
        }
    }

    /**
     * 添加新书（交互式创建），保持原有逻辑不变
     */
    public static void addBook(File folder) {
        if (folder == null || !folder.isDirectory()) {
            System.out.println("当前文件夹无效。");
            return;
        }
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== 创建新成书 ===");
        System.out.print("请输入书名: ");
        String title = scanner.nextLine().trim();
        if (title.isEmpty()) {
            System.out.println("书名不能为空，取消创建。");
            return;
        }
        System.out.print("请输入作者: ");
        String author = scanner.nextLine().trim();
        if (author.isEmpty()) author = "未知";

        System.out.println("请选择世代: 0-原稿, 1-原稿的副本, 2-副本的副本 (默认0)");
        System.out.print("输入数字: ");
        String genInput = scanner.nextLine().trim();
        int generation = 0;
        if (!genInput.isEmpty()) {
            try {
                generation = Integer.parseInt(genInput);
                if (generation < 0 || generation > 2) generation = 0;
            } catch (NumberFormatException e) {
                generation = 0;
            }
        }

        List<String> pages = new ArrayList<>();
        System.out.println("请逐页输入内容。每页输入完成后按回车，输入空行结束该页。");
        int pageNum = 1;
        while (true) {
            System.out.println("\n--- 第 " + pageNum + " 页 ---");
            System.out.println("输入内容（可多行，输入空行结束本页）:");
            StringBuilder pageContent = new StringBuilder();
            boolean firstLine = true;
            while (true) {
                String line = scanner.nextLine();
                if (line.isEmpty() && !firstLine) break;
                if (!firstLine) pageContent.append("\n");
                pageContent.append(line);
                firstLine = false;
            }
            if (pageContent.length() == 0) {
                System.out.println("页面内容为空，请重新输入本页。");
                continue;
            }
            pages.add(pageContent.toString());
            System.out.print("是否继续添加下一页？(Y/n): ");
            String ans = scanner.nextLine().trim().toLowerCase();
            if (ans.equals("n") || ans.equals("no")) break;
            pageNum++;
        }

        String generationName = getGenerationName(generation);
        String baseTitle = title + "（" + generationName + "）";
        String safeFileName = baseTitle.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5（）]", "_");
        if (safeFileName.isEmpty()) safeFileName = "book";
        File outputFile = new File(folder, safeFileName + ".mcfunction");
        int counter = 1;
        while (outputFile.exists()) {
            outputFile = new File(folder, safeFileName + "_" + counter + ".mcfunction");
            counter++;
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"))) {
            StringBuilder cmd = new StringBuilder();
            cmd.append("give @p minecraft:written_book[");
            cmd.append("minecraft:written_book_content={");
            cmd.append("title:\"").append(escapeJsonString(title)).append("\",");
            cmd.append("author:\"").append(escapeJsonString(author)).append("\",");
            cmd.append("generation:").append(generation).append(",");
            cmd.append("pages:[");
            for (int i = 0; i < pages.size(); i++) {
                if (i > 0) cmd.append(",");
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
            System.err.println("创建文件失败: " + e.getMessage());
            return;
        }
        System.out.println("成功创建书籍: " + outputFile.getName());
    }

    private static void displayPageContent(List<String> pages, int page) {
        System.out.println("\n========== 第 " + page + " 页 / 共 " + pages.size() + " 页 ==========");
        System.out.println(pages.get(page - 1));
        System.out.println("========================================");
    }

    private static String getGenerationName(int generation) {
        switch (generation) {
            case 0: return "原稿";
            case 1: return "原稿的副本";
            case 2: return "副本的副本";
            default: return "未知";
        }
    }

    private static String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
        // ========== 以下为新增方法 ==========

    /**
     * 直接打开指定的书籍（跳过搜索和选择）
     */
    public static void openBookByEntry(File folder, FunctionlibApp.BookEntry book) {
        File targetFile = new File(folder, book.fileName);
        List<String> pages;
        try {
            pages = McFunctionParser.extractPagesFromFile(targetFile.toPath());
        } catch (IOException e) {
            System.err.println("读取文件失败: " + e.getMessage());
            return;
        }
        if (pages.isEmpty()) {
            System.out.println("该书籍没有页面内容。");
            return;
        }
        System.out.println("\n已打开: " + book.fileName + " (共 " + pages.size() + " 页)");
        System.out.println("输入 'page [页码]' 翻页，'translate [目标语言]' 翻译当前页，'translate full [语言]' 翻译全书");
        System.out.println("'print full' 打印全书，'close' 关闭并返回列表。");
        int currentPage = 1;
        displayPageContent(pages, currentPage);
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\n[浏览模式] 请输入指令: ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("close")) {
                System.out.println("关闭书籍。");
                break;
            } else if (input.startsWith("page ")) {
                try {
                    int page = Integer.parseInt(input.substring(5).trim());
                    if (page < 1 || page > pages.size()) {
                        System.out.printf("页码超出范围，有效页码 1-%d\n", pages.size());
                    } else {
                        currentPage = page;
                        displayPageContent(pages, currentPage);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("页码格式错误，示例: page 2");
                }
            } else if (input.startsWith("translate full ")) {
                String targetLang = input.substring(15).trim();
                if (targetLang.isEmpty()) {
                    System.out.println("请指定目标语言代码，例如: translate full zh");
                    continue;
                }
                translateFullBook(pages, targetLang);
            } else if (input.startsWith("translate ")) {
                String targetLang = input.substring(10).trim();
                if (targetLang.isEmpty()) {
                    System.out.println("请指定目标语言代码，例如: translate zh");
                    continue;
                }
                String originalText = pages.get(currentPage - 1);
                System.out.println("\n正在翻译第 " + currentPage + " 页...");
                String translated = TranslateLib.translateAuto(originalText, targetLang);
                if (translated != null) {
                    System.out.println("\n========== 第 " + currentPage + " 页 译文 ==========");
                    System.out.println(translated);
                    System.out.println("==========================================");
                } else {
                    System.out.println("翻译失败，请检查翻译服务是否运行。");
                }
            } else if (input.equals("translate")) {
                System.out.println("请指定目标语言，例如: translate zh");
            } else if (input.equals("print full")) {
                System.out.println("\n========== 全书内容 ==========");
                for (int i = 0; i < pages.size(); i++) {
                    System.out.println(pages.get(i));
                    if (i < pages.size() - 1) {
                        System.out.println(); // 页面之间空一行，保持可读性
                    }
                }
                System.out.println("================================");
            } else {
                System.out.println("无效指令。可用: page [页码], translate [语言], translate full [语言], close");
            }
        }
    }

    /**
     * 直接删除指定的书籍（及其所有内容相同的副本）
     */
    public static void deleteBookByEntry(File folder, FunctionlibApp.BookEntry book, List<FunctionlibApp.BookEntry> allBooks) {
        String targetContent = readBookContent(book.file);
        if (targetContent == null) {
            System.out.println("无法读取书籍内容，取消删除。");
            return;
        }
        List<FunctionlibApp.BookEntry> copies = new ArrayList<>();
        for (FunctionlibApp.BookEntry b : allBooks) {
            String content = readBookContent(b.file);
            if (targetContent.equals(content)) {
                copies.add(b);
            }
        }
        System.out.printf("找到 %d 本内容相同的副本。\n", copies.size());
        System.out.print("确认删除吗？(y/N): ");
        Scanner scanner = new Scanner(System.in);
        if (!scanner.nextLine().trim().toLowerCase().startsWith("y")) {
            System.out.println("取消删除。");
            return;
        }
        int deleted = 0;
        for (FunctionlibApp.BookEntry b : copies) {
            File f = new File(folder, b.fileName);
            try {
                Files.deleteIfExists(f.toPath());
                deleted++;
            } catch (IOException e) {
                System.err.println("删除失败: " + b.fileName);
            }
        }
        System.out.printf("已删除 %d 个文件。\n", deleted);
    }

    // 将原有的 private static String readBookContent(File) 改为 public static
    public static String readBookContent(File file) {
        try {
            List<String> pages = McFunctionParser.extractPagesFromFile(file.toPath());
            return String.join("\n", pages);
        } catch (Exception e) {
            System.err.println("读取文件失败: " + file.getName() + " - " + e.getMessage());
            return null;
        }
    }
}