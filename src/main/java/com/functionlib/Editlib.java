package com.functionlib;

import com.common.McFunctionParser;
import com.functionlib.db.BookDao;

import javax.swing.JFileChooser;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;


/**
 * 书籍编辑工具库
 * 提供删除、添加、打开浏览、移动 .mcfunction 文件的功能
 */
public class Editlib {

    private static final Scanner CONSOLE = new Scanner(System.in);

    /**
     * 从文件名中提取基础书名（去除世代后缀和重复数字后缀）。
     */
    private static String extractBaseTitle(String fileName) {
        String base = fileName.replaceFirst("\\.mcfunction$", "");
        base = base.replaceAll("[（(][^）)]*[）)]", "");
        base = base.replaceAll("_\\d+$", "");
        return base.trim();
    }

    /**
     * 翻译并打印整本书内容，保持原文件不变。
     */
    private static void translateFullBook(List<String> pages, String targetLang) {
        System.out.println("\n开始翻译整本书，共 " + pages.size() + " 页，目标语言: " + targetLang);
        System.out.println("正在拼接全文并提交翻译，请稍候...");

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
     * 根据名称匹配、去重后选择书籍并进入交互式浏览。
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
                return;
            }

            File targetFile = new File(folder, selection.representative.getFileName());
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

            System.out.println("\n已打开: " + selection.representative.getFileName() + " (共 " + pages.size() + " 页)");
            System.out.println("输入 'page [页码]' 翻页，'translate [目标语言]' 翻译当前页，'translate full [语言]' 翻译全书");
            System.out.println("'move' 移动书籍，'print full' 打印全书，'close' 关闭并返回列表。");

            int currentPage = 1;
            displayPageContent(pages, currentPage);

            while (true) {
                System.out.print("\n[浏览模式] 请输入指令: ");
                String input = CONSOLE.nextLine().trim().toLowerCase();
                if (input.equals("close")) {
                    System.out.println("关闭书籍，返回列表。");
                    break;
                } else if (input.equals("move")) {
                    boolean success = moveBook(folder, selection.representative, allBooks);
                    if (success) {
                        FunctionlibApp.refreshDatabase();
                        System.out.println("数据库已刷新，返回主菜单。");
                        return;
                    }
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
                } else if (input.equals("print full")) {
                    System.out.println("\n========== 全书内容 ==========");
                    for (int i = 0; i < pages.size(); i++) {
                        System.out.println(pages.get(i));
                        if (i < pages.size() - 1) {
                            System.out.println();
                        }
                    }
                    System.out.println("================================");
                } else if (input.startsWith("translate ")) {
                    String targetLang = input.substring(10).trim();
                    if (targetLang.isEmpty()) {
                        System.out.println("请指定目标语言代码，例如: translate zh");
                        continue;
                    }
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
                    System.out.println("无效指令。可用: page [页码], translate [语言], translate full [语言], move, print full, close");
                }
            }
        }
    }

    /**
     * 直接打开指定书籍并进入交互式浏览（用于从查询结果直接打开）。
     */
    public static void openBookByEntry(File folder, FunctionlibApp.BookEntry book, List<FunctionlibApp.BookEntry> allBooks) {
        File targetFile = new File(folder, book.getFileName());
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

        System.out.println("\n已打开: " + book.getFileName() + " (共 " + pages.size() + " 页)");
        System.out.println("输入 'page [页码]' 翻页，'translate [目标语言]' 翻译当前页，'translate full [语言]' 翻译全书");
        System.out.println("'move' 移动书籍，'print full' 打印全书，'close' 关闭并返回列表。");

        int currentPage = 1;
        displayPageContent(pages, currentPage);

        while (true) {
            System.out.print("\n[浏览模式] 请输入指令: ");
            String input = CONSOLE.nextLine().trim().toLowerCase();
            if (input.equals("close")) {
                System.out.println("关闭书籍。");
                break;
            } else if (input.equals("move")) {
                boolean success = moveBook(folder, book, allBooks);
                if (success) {
                    FunctionlibApp.refreshDatabase();
                    System.out.println("数据库已刷新，返回主菜单。");
                    return;
                }
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
                        System.out.println();
                    }
                }
                System.out.println("================================");
            } else {
                System.out.println("无效指令。可用: page [页码], translate [语言], translate full [语言], move, print full, close");
            }
        }
    }

    /**
     * 根据基础书名从书籍列表中筛选匹配项。
     */
    public static List<FunctionlibApp.BookEntry> filterBooksByBaseTitle(List<FunctionlibApp.BookEntry> allBooks, String pattern) {
        List<FunctionlibApp.BookEntry> result = new ArrayList<>();
        for (FunctionlibApp.BookEntry book : allBooks) {
            String base = extractBaseTitle(book.getFileName());
            if (base.equalsIgnoreCase(pattern) || base.toLowerCase().contains(pattern.toLowerCase())) {
                result.add(book);
            }
        }
        return result;
    }

    /**
     * 按内容合并匹配书籍并让用户选择一个代表书籍条目。
     */
    public static MergedBookSelection mergeAndSelect(List<FunctionlibApp.BookEntry> matched, String action) {
        Map<String, List<FunctionlibApp.BookEntry>> contentGroups = new LinkedHashMap<>();
        for (FunctionlibApp.BookEntry book : matched) {
            String content = readBookContent(book.getFile());
            if (content == null) {
                continue;
            }
            contentGroups.computeIfAbsent(content, k -> new ArrayList<>()).add(book);
        }

        List<MergedBookItem> items = new ArrayList<>();
        for (Map.Entry<String, List<FunctionlibApp.BookEntry>> entry : contentGroups.entrySet()) {
            List<FunctionlibApp.BookEntry> group = entry.getValue();
            items.add(new MergedBookItem(group));
        }

        if (items.isEmpty()) {
            System.out.println("没有可读取内容的书籍。");
            return null;
        }

        items.sort(Comparator.comparing(a -> a.representative.getTitle(), String.CASE_INSENSITIVE_ORDER));

        System.out.println("\n找到 " + items.size() + " 本内容不同的匹配书籍：");
        System.out.println("┌────┬──────────────────────────────────────┬────────────────────┬──────────┬────────┬────────┐");
        System.out.println("│序号│ 书名                                 │ 作者               │ 代表世代 │ 总副本 │ 原稿数 │");
        System.out.println("├────┼──────────────────────────────────────┼────────────────────┼──────────┼────────┼────────┤");
        int idx = 1;
        for (MergedBookItem item : items) {
            System.out.printf("│ %2d │ %-36s │ %-18s │ %-8s │ %6d │ %6d │\n",
                    idx++,
                    truncate(item.representative.getTitle(), 36),
                    truncate(item.representative.getAuthor(), 18),
                    truncate(item.representative.getGeneration(), 8),
                    item.totalCopies,
                    item.originalCount);
        }
        System.out.println("└────┴──────────────────────────────────────┴────────────────────┴──────────┴────────┴────────┘");

        System.out.print("请输入要" + action + "的序号 (输入 0 取消): ");
        int choice;
        try {
            choice = Integer.parseInt(CONSOLE.nextLine().trim());
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
     * 外部调用的静态包装，直接执行合并选择。
     */
    public static MergedBookSelection mergeAndSelectStatic(List<FunctionlibApp.BookEntry> matched, String action) {
        return mergeAndSelect(matched, action);
    }

    /**
     * 选择目标文件夹并移动内容相同的副本文件。
     */
    /**
 * 选择目标文件夹并移动内容相同的副本文件（同步更新数据库）。
 */
public static boolean moveBook(File sourceFolder, FunctionlibApp.BookEntry book,
                               List<FunctionlibApp.BookEntry> allBooks) {
    // 1. 选择目标文件夹
    JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle("选择目标文件夹");
    chooser.setCurrentDirectory(sourceFolder);
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
        System.out.println("已取消移动。");
        return false;
    }
    File targetFolder = chooser.getSelectedFile();
    if (targetFolder.equals(sourceFolder)) {
        System.out.println("目标文件夹与当前文件夹相同，无需移动。");
        return false;
    }

    // 2. 找出所有内容相同的副本（同一本书的不同文件）
    String targetContent = readBookContent(book.getFile());
    if (targetContent == null) {
        System.out.println("无法读取书籍内容，取消移动。");
        return false;
    }

    List<FunctionlibApp.BookEntry> copies = new ArrayList<>();
    for (FunctionlibApp.BookEntry b : allBooks) {
        String content = readBookContent(b.getFile());
        if (targetContent.equals(content)) {
            copies.add(b);
        }
    }

    int movedCount = 0;
    for (FunctionlibApp.BookEntry copy : copies) {
        File sourceFile = copy.getFile();
        File destFile = new File(targetFolder, sourceFile.getName());

        // 3. 移动物理文件
        try {
            Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("移动文件失败: " + sourceFile.getName() + " - " + e.getMessage());
            continue;
        }

        // 4. 从源数据库删除记录
        try {
            BookDao.delete(sourceFolder, sourceFile.getAbsolutePath());
        } catch (SQLException e) {
            System.err.println("源数据库删除失败: " + e.getMessage());
        }

        // 5. 向目标数据库插入新记录
        try {
            FunctionlibApp.BookEntry newEntry = FunctionlibApp.parseBookEntry(destFile);
            BookDao.insertOrUpdate(targetFolder, newEntry);
            movedCount++;
        } catch (Exception e) {
            System.err.println("目标数据库插入失败: " + destFile.getName() + " - " + e.getMessage());
        }
    }

    System.out.printf("移动完成，成功 %d 个文件。\n", movedCount);
    return movedCount > 0;
}
/**
 * 辅助方法：从 File 解析出 BookEntry（复用 FunctionlibApp 中的解析逻辑）
 */
private static FunctionlibApp.BookEntry parseBookEntryForFile(File file) {
    try {
        String content = Files.readString(file.toPath());
        String title = FunctionlibApp.extractField(content, "title");
        String author = FunctionlibApp.extractField(content, "author");
        int generation = FunctionlibApp.extractGeneration(content);
        String generationName = FunctionlibApp.getGenerationName(generation);

        if (title == null || title.isEmpty()) {
            title = file.getName().replace(".mcfunction", "");
        }
        if (author == null || author.isEmpty()) {
            author = "未知";
        }
        List<String> pagesList = McFunctionParser.extractPagesFromFile(file.toPath());
        int pages = pagesList.size();
        int wordCount = 0;
        for (String page : pagesList) {
            wordCount += page.replaceAll("[\\s\\n\\r]+", "").length();
        }
        String fullContent = String.join("\n", pagesList);
        String contentHash = FunctionlibApp.computeSha256(fullContent);
        return new FunctionlibApp.BookEntry(file.getName(), title, author, generationName, file, pages, wordCount, contentHash);
    } catch (Exception e) {
        System.err.println("解析文件失败: " + file.getName() + " - " + e.getMessage());
        return null;
    }
}





    /**
     * 交互式创建新书并保存为 .mcfunction 文件。
     */
    public static void addBook(File folder) {
        if (folder == null || !folder.isDirectory()) {
            System.out.println("当前文件夹无效。");
            return;
        }
        System.out.println("=== 创建新成书 ===");
        System.out.print("请输入书名: ");
        String title = CONSOLE.nextLine().trim();
        if (title.isEmpty()) {
            System.out.println("书名不能为空，取消创建。");
            return;
        }
        System.out.print("请输入作者: ");
        String author = CONSOLE.nextLine().trim();
        if (author.isEmpty()) {
            author = "未知";
        }

        System.out.println("请选择世代: 0-原稿, 1-原稿的副本, 2-副本的副本 (默认0)");
        System.out.print("输入数字: ");
        String genInput = CONSOLE.nextLine().trim();
        int generation = 0;
        if (!genInput.isEmpty()) {
            try {
                generation = Integer.parseInt(genInput);
                if (generation < 0 || generation > 2) {
                    generation = 0;
                }
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
                String line = CONSOLE.nextLine();
                if (line.isEmpty() && !firstLine) {
                    break;
                }
                if (!firstLine) {
                    pageContent.append("\n");
                }
                pageContent.append(line);
                firstLine = false;
            }
            if (pageContent.length() == 0) {
                System.out.println("页面内容为空，请重新输入本页。");
                continue;
            }
            pages.add(pageContent.toString());
            System.out.print("是否继续添加下一页？(Y/n): ");
            String ans = CONSOLE.nextLine().trim().toLowerCase();
            if (ans.equals("n") || ans.equals("no")) {
                break;
            }
            pageNum++;
        }

        String generationName = FunctionlibApp.getGenerationName(generation);
        String baseTitle = title + "（" + generationName + "）";
        String safeFileName = baseTitle.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5（）]", "_");
        if (safeFileName.isEmpty()) {
            safeFileName = "book";
        }
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
                if (i > 0) {
                    cmd.append(",");
                }
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

    /**
     * 删除指定书籍及其所有内容相同的副本（用于查询结果直接操作）。
     */
   /**
 * 根据名称匹配并删除选中的书籍及其所有内容相同的副本。
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
    if (selection == null) {
        return;
    }

    int deletedCount = 0;
    for (FunctionlibApp.BookEntry book : selection.allCopies) {
        File targetFile = new File(folder, book.getFileName());
        if (FunctionlibApp.moveToRecycleBin(targetFile)) {
            deletedCount++;
            // 数据库同步：删除记录
            try {
                BookDao.delete(folder, targetFile.getAbsolutePath());
            } catch (SQLException e) {
                System.err.println("数据库删除失败: " + e.getMessage());
            }
        } else {
            System.err.println("移动失败: " + book.getFileName());
        }
    }
    System.out.printf("已删除书籍《%s》及其 %d 个副本。\n", selection.representative.getTitle(), deletedCount - 1);
}
    
    
/**
 * 删除指定书籍及其所有内容相同的副本（用于查询结果直接操作）。
 */
public static void deleteBookByEntry(File currentFolder, FunctionlibApp.BookEntry book,
                                     List<FunctionlibApp.BookEntry> allBooks) {
    // 1. 读取目标书籍的完整内容
    String targetContent = readBookContent(book.getFile());
    if (targetContent == null) {
        System.out.println("无法读取书籍内容，取消删除。");
        return;
    }

    // 2. 找出所有内容相同的副本
    List<FunctionlibApp.BookEntry> copies = new ArrayList<>();
    for (FunctionlibApp.BookEntry b : allBooks) {
        String content = readBookContent(b.getFile());
        if (targetContent.equals(content)) {
            copies.add(b);
        }
    }

    // 3. 如果没有副本，直接返回
    if (copies.isEmpty()) {
        System.out.println("未找到任何副本。");
        return;
    }

    // ✅ 删除以下所有命令行交互代码：
    // System.out.println("找到 " + copies.size() + " 本内容相同的副本。");
    // System.out.print("确认删除吗？(y/N): ");
    // Scanner scanner = new Scanner(System.in);
    // if (!scanner.nextLine().trim().toLowerCase().startsWith("y")) return;

    // 4. 直接执行删除（移入回收站）
    for (FunctionlibApp.BookEntry copy : copies) {
        File file = copy.getFile();
        FunctionlibApp.moveToRecycleBin(file);
    }

    System.out.println("已将 " + copies.size() + " 个副本移入回收站。");
}
    /**
     * 读取书籍内容并将页面合并为单个字符串。
     */
    public static String readBookContent(File file) {
        try {
            List<String> pages = McFunctionParser.extractPagesFromFile(file.toPath());
            return String.join("\n", pages);
        } catch (Exception e) {
            System.err.println("读取文件失败: " + file.getName() + " - " + e.getMessage());
            return null;
        }
    }

    // ==================== 辅助显示方法 ====================

    private static void displayPageContent(List<String> pages, int page) {
        System.out.println("\n========== 第 " + page + " 页 / 共 " + pages.size() + " 页 ==========");
        System.out.println(pages.get(page - 1));
        System.out.println("========================================");
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

    // ==================== 内部数据类 ====================

    private static class MergedBookItem {
        FunctionlibApp.BookEntry representative;
        List<FunctionlibApp.BookEntry> allCopies;
        int totalCopies;
        int originalCount;

        MergedBookItem(List<FunctionlibApp.BookEntry> group) {
            this.allCopies = group;
            this.representative = selectRepresentative(group);
            this.totalCopies = group.size();
            this.originalCount = (int) group.stream().filter(b -> "原稿".equals(b.getGeneration())).count();
        }

        private static FunctionlibApp.BookEntry selectRepresentative(List<FunctionlibApp.BookEntry> group) {
            List<FunctionlibApp.BookEntry> originals = new ArrayList<>();
            for (FunctionlibApp.BookEntry b : group) {
                if ("原稿".equals(b.getGeneration())) {
                    originals.add(b);
                }
            }
            List<FunctionlibApp.BookEntry> candidates = originals.isEmpty() ? group : originals;

            List<FunctionlibApp.BookEntry> noSuffix = new ArrayList<>();
            Pattern dupPattern = Pattern.compile("_\\d+$");
            for (FunctionlibApp.BookEntry b : candidates) {
                String base = b.getFileName().replaceFirst("\\.mcfunction$", "");
                if (!dupPattern.matcher(base).find()) {
                    noSuffix.add(b);
                }
            }
            if (!noSuffix.isEmpty()) {
                candidates = noSuffix;
            }

            FunctionlibApp.BookEntry keeper = candidates.get(0);
            for (FunctionlibApp.BookEntry b : candidates) {
                if (b.getFileName().length() < keeper.getFileName().length()) {
                    keeper = b;
                } else if (b.getFileName().length() == keeper.getFileName().length() && b.getFileName().compareTo(keeper.getFileName()) < 0) {
                    keeper = b;
                }
            }
            return keeper;
        }
    }

    public static class MergedBookSelection {
        public final FunctionlibApp.BookEntry representative;
        public final List<FunctionlibApp.BookEntry> allCopies;

        MergedBookSelection(FunctionlibApp.BookEntry r, List<FunctionlibApp.BookEntry> copies) {
            this.representative = r;
            this.allCopies = copies;
        }
    }
}