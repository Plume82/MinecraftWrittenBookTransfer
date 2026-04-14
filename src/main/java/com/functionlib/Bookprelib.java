package com.functionlib;

import com.common.McFunctionParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 成书预处理库
 * 负责在同一作者内，按内容去重，保留最原始的版本（优先原稿，其次无重复后缀）
 */
public class Bookprelib {

    // 扫描到的书籍信息
    private static List<BookEntry> allBooks;
    // 作者 -> 书籍列表
    private static Map<String, List<BookEntry>> booksByAuthor;
    // 待删除的文件列表
    private static List<File> filesToDelete = new ArrayList<>();
    // 保留的书籍列表（用于展示）
    private static List<BookEntry> keptBooks = new ArrayList<>();
    // 删除的书籍列表（用于展示）
    private static List<BookEntry> removedBooks = new ArrayList<>();

    /**
     * 入口方法：执行预处理
     * @param allBooks     已解析的全部书籍列表
     * @param booksByAuthor 按作者分组的书籍映射
     */
    public static void runPreprocess(List<FunctionlibApp.BookEntry> allBooks,
                                     Map<String, List<FunctionlibApp.BookEntry>> booksByAuthor) {
        // 类型转换：将 FunctionlibApp.BookEntry 转为 Bookprelib.BookEntry
        List<BookEntry> convertedBooks = new ArrayList<>();
        Map<String, List<BookEntry>> convertedMap = new LinkedHashMap<>();

        for (FunctionlibApp.BookEntry entry : allBooks) {
            BookEntry be = new BookEntry(
                    entry.fileName,
                    entry.title,
                    entry.author,
                    entry.generation,
                    entry.file
            );
            convertedBooks.add(be);
        }
        for (Map.Entry<String, List<FunctionlibApp.BookEntry>> mapEntry : booksByAuthor.entrySet()) {
            List<BookEntry> list = new ArrayList<>();
            for (FunctionlibApp.BookEntry entry : mapEntry.getValue()) {
                list.add(new BookEntry(
                        entry.fileName,
                        entry.title,
                        entry.author,
                        entry.generation,
                        entry.file
                ));
            }
            convertedMap.put(mapEntry.getKey(), list);
        }

        Bookprelib.allBooks = convertedBooks;
        Bookprelib.booksByAuthor = convertedMap;

        Scanner scanner = new Scanner(System.in);
        System.out.println("\n========================================");
        System.out.println("         成书预处理去重工具");
        System.out.println("========================================");
        System.out.println("预处理逻辑：");
        System.out.println("  • 在同一作者内，按书籍完整内容进行比对（忽略标题和世代）。");
        System.out.println("  • 内容完全相同的书籍，只保留最原始的版本：");
        System.out.println("    - 优先保留“原稿”，其次保留文件名不带 _数字 后缀的手稿。");
        System.out.println("  • 其余重复副本将被标记删除。");
        System.out.println("========================================");
        System.out.print("确定开始预处理吗？(y/N): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (!confirm.equals("y") && !confirm.equals("yes")) {
            System.out.println("已取消预处理，返回上级菜单。");
            return;
        }

        System.out.println("正在分析书籍内容，请稍候...");
        filesToDelete.clear();
        keptBooks.clear();
        removedBooks.clear();

        int totalRemoved = 0;
        int totalAuthors = Bookprelib.booksByAuthor.size();
        int processedAuthors = 0;

        for (Map.Entry<String, List<BookEntry>> entry : Bookprelib.booksByAuthor.entrySet()) {
            String author = entry.getKey();
            List<BookEntry> books = entry.getValue();
            processedAuthors++;
            System.out.printf("正在处理作者 [%s] (%d/%d)...\n", author, processedAuthors, totalAuthors);

            // 按内容分组
            Map<String, List<BookEntry>> contentGroups = new LinkedHashMap<>();
            for (BookEntry book : books) {
                String content = readFullContent(book.file);
                if (content == null) continue;
                contentGroups.computeIfAbsent(content, k -> new ArrayList<>()).add(book);
            }

            // 对每组内容完全相同的书籍，保留最原始的一本
            for (Map.Entry<String, List<BookEntry>> groupEntry : contentGroups.entrySet()) {
                List<BookEntry> group = groupEntry.getValue();
                if (group.size() <= 1) {
                    // 单本无需处理，直接加入保留列表
                    keptBooks.add(group.get(0));
                    continue;
                }

                BookEntry keeper = selectKeeper(group);
                keptBooks.add(keeper);
                for (BookEntry book : group) {
                    if (book != keeper) {
                        filesToDelete.add(book.file);
                        removedBooks.add(book);
                        totalRemoved++;
                    }
                }
            }
        }

        System.out.println("分析完成。共发现 " + filesToDelete.size() + " 个重复副本需要处理。");

        if (filesToDelete.isEmpty()) {
            System.out.println("没有需要处理的重复文件。");
            return;
        }

        // ========== 以表格形式展示保留的书籍 ==========
        System.out.println("\n┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                              保留的书籍（原始版本）                          │");
        System.out.println("├────────────────────┬──────────────────────────────────────┬───────────────────┤");
        System.out.println("│ 作者               │ 书名                                 │ 世代              │");
        System.out.println("├────────────────────┼──────────────────────────────────────┼───────────────────┤");
        for (BookEntry book : keptBooks) {
            System.out.printf("│ %-18s │ %-36s │ %-17s │\n",
                    truncate(book.author, 18),
                    truncate(book.title, 36),
                    book.generation);
        }
        System.out.println("└────────────────────┴──────────────────────────────────────┴───────────────────┘");

        // ========== 以表格形式展示待删除的书籍 ==========
        System.out.println("\n┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                              待删除的书籍（重复副本）                        │");
        System.out.println("├────────────────────┬──────────────────────────────────────┬───────────────────┤");
        System.out.println("│ 作者               │ 书名                                 │ 世代              │");
        System.out.println("├────────────────────┼──────────────────────────────────────┼───────────────────┤");
        for (BookEntry book : removedBooks) {
            System.out.printf("│ %-18s │ %-36s │ %-17s │\n",
                    truncate(book.author, 18),
                    truncate(book.title, 36),
                    book.generation);
        }
        System.out.println("└────────────────────┴──────────────────────────────────────┴───────────────────┘");

        System.out.print("\n确认删除以上重复文件吗？(y/N): ");
        String deleteConfirm = scanner.nextLine().trim().toLowerCase();
        if (!deleteConfirm.equals("y") && !deleteConfirm.equals("yes")) {
            System.out.println("操作已取消，未删除任何文件。");
            return;
        }

        int deletedCount = 0;
        for (File file : filesToDelete) {
            try {
                boolean deleted = Files.deleteIfExists(file.toPath());
                if (deleted) deletedCount++;
            } catch (IOException e) {
                System.err.println("删除失败: " + file.getName() + " - " + e.getMessage());
            }
        }
        System.out.printf("删除完成。成功删除 %d 个文件，失败 %d 个。\n", deletedCount, filesToDelete.size() - deletedCount);
        System.out.println("返回上级菜单。");
    }

    /**
     * 读取 .mcfunction 文件的完整页面内容，拼接为单一字符串用于比对
     */
    private static String readFullContent(File file) {
        try {
            List<String> pages = McFunctionParser.extractPagesFromFile(file.toPath());
            return String.join("\n", pages);
        } catch (Exception e) {
            System.err.println("读取文件失败: " + file.getName() + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 从一组内容完全相同的书籍中选择保留哪一本。
     */
    private static BookEntry selectKeeper(List<BookEntry> group) {
        // 优先：有原稿则只考虑原稿
        List<BookEntry> originals = new ArrayList<>();
        for (BookEntry b : group) {
            if ("原稿".equals(b.generation)) {
                originals.add(b);
            }
        }
        List<BookEntry> candidates = originals.isEmpty() ? group : originals;

        // 其次：文件名不带 _数字 后缀（最原始手稿）
        List<BookEntry> noSuffix = new ArrayList<>();
        Pattern dupPattern = Pattern.compile("_\\d+$");
        for (BookEntry b : candidates) {
            String base = b.fileName.replaceFirst("\\.mcfunction$", "");
            Matcher m = dupPattern.matcher(base);
            if (!m.find()) {
                noSuffix.add(b);
            }
        }
        if (!noSuffix.isEmpty()) {
            candidates = noSuffix;
        }

        // 最终若还有多个，选文件名长度最小的，若相同则按字母序
        BookEntry keeper = candidates.get(0);
        for (BookEntry b : candidates) {
            if (b.fileName.length() < keeper.fileName.length()) {
                keeper = b;
            } else if (b.fileName.length() == keeper.fileName.length() && b.fileName.compareTo(keeper.fileName) < 0) {
                keeper = b;
            }
        }
        return keeper;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    // ---------- 内部数据类 ----------
    static class BookEntry {
        String fileName;
        String title;
        String author;
        String generation;
        File file;

        BookEntry(String fileName, String title, String author, String generation, File file) {
            this.fileName = fileName;
            this.title = title;
            this.author = author;
            this.generation = generation;
            this.file = file;
        }
    }
}