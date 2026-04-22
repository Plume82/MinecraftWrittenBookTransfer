package com.functionlib;

import com.common.McFunctionParser;
import com.functionlib.FunctionlibApp.BookEntry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 成书预处理库（纯业务逻辑 + 命令行交互兼容）
 */
public class Bookprelib {

    /**
     * 去重分析结果
     */
    public static class PreprocessResult {
        public final List<BookEntry> keptBooks;
        public final List<BookEntry> removedBooks;
        public final List<File> filesToDelete;

        public PreprocessResult(List<BookEntry> kept, List<BookEntry> removed, List<File> toDelete) {
            this.keptBooks = kept;
            this.removedBooks = removed;
            this.filesToDelete = toDelete;
        }
    }

    /**
     * 命令行交互式预处理（兼容原有调用）
     */
    public static void runPreprocess(List<BookEntry> allBooks,
                                     Map<String, List<BookEntry>> booksByAuthor) {
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
        PreprocessResult result = analyzeDuplicates(booksByAuthor);

        if (result.filesToDelete.isEmpty()) {
            System.out.println("没有需要处理的重复文件。");
            return;
        }

        // 展示保留的书籍表格
        System.out.println("\n┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                              保留的书籍（原始版本）                          │");
        System.out.println("├────────────────────┬──────────────────────────────────────┬───────────────────┤");
        System.out.println("│ 作者               │ 书名                                 │ 世代              │");
        System.out.println("├────────────────────┼──────────────────────────────────────┼───────────────────┤");
        for (BookEntry book : result.keptBooks) {
            System.out.printf("│ %-18s │ %-36s │ %-17s │\n",
                    truncate(book.getAuthor(), 18),
                    truncate(book.getTitle(), 36),
                    book.getGeneration());
        }
        System.out.println("└────────────────────┴──────────────────────────────────────┴───────────────────┘");

        // 展示待删除的书籍表格
        System.out.println("\n┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                              待删除的书籍（重复副本）                        │");
        System.out.println("├────────────────────┬──────────────────────────────────────┬───────────────────┤");
        System.out.println("│ 作者               │ 书名                                 │ 世代              │");
        System.out.println("├────────────────────┼──────────────────────────────────────┼───────────────────┤");
        for (BookEntry book : result.removedBooks) {
            System.out.printf("│ %-18s │ %-36s │ %-17s │\n",
                    truncate(book.getAuthor(), 18),
                    truncate(book.getTitle(), 36),
                    book.getGeneration());
        }
        System.out.println("└────────────────────┴──────────────────────────────────────┴───────────────────┘");

        System.out.print("\n确认删除以上重复文件吗？(y/N): ");
        String deleteConfirm = scanner.nextLine().trim().toLowerCase();
        if (!deleteConfirm.equals("y") && !deleteConfirm.equals("yes")) {
            System.out.println("操作已取消，未删除任何文件。");
            return;
        }

        int deletedCount = 0;
        for (File file : result.filesToDelete) {
            try {
                if (Files.deleteIfExists(file.toPath())) deletedCount++;
            } catch (IOException e) {
                System.err.println("删除失败: " + file.getName() + " - " + e.getMessage());
            }
        }
        System.out.printf("删除完成。成功删除 %d 个文件，失败 %d 个。\n",
                deletedCount, result.filesToDelete.size() - deletedCount);
        System.out.println("返回上级菜单。");
    }

    /**
     * 分析重复书籍，返回去重建议（不执行实际删除）
     */
    public static PreprocessResult analyzeDuplicates(Map<String, List<BookEntry>> booksByAuthor) {
        List<BookEntry> kept = new ArrayList<>();
        List<BookEntry> removed = new ArrayList<>();
        List<File> toDelete = new ArrayList<>();

        for (Map.Entry<String, List<BookEntry>> entry : booksByAuthor.entrySet()) {
            List<BookEntry> authorBooks = entry.getValue();

            // 按内容分组（使用内容哈希）
            Map<String, List<BookEntry>> contentGroups = new LinkedHashMap<>();
            for (BookEntry book : authorBooks) {
                String content = readFullContent(book.getFile());
                if (content == null) continue;
                String hash = FunctionlibApp.computeSha256(content);
                contentGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(book);
            }

            for (List<BookEntry> group : contentGroups.values()) {
                if (group.size() == 1) {
                    kept.add(group.get(0));
                } else {
                    BookEntry keeper = selectKeeper(group);
                    kept.add(keeper);
                    for (BookEntry book : group) {
                        if (book != keeper) {
                            removed.add(book);
                            toDelete.add(book.getFile());
                        }
                    }
                }
            }
        }
        return new PreprocessResult(kept, removed, toDelete);
    }

    private static String readFullContent(File file) {
        try {
            List<String> pages = McFunctionParser.extractPagesFromFile(file.toPath());
            return String.join("\n", pages);
        } catch (Exception e) {
            System.err.println("读取文件失败: " + file.getName() + " - " + e.getMessage());
            return null;
        }
    }

    private static BookEntry selectKeeper(List<BookEntry> group) {
        // 优先：原稿
        List<BookEntry> originals = group.stream()
                .filter(b -> "原稿".equals(b.getGeneration()))
                .collect(Collectors.toList());
        List<BookEntry> candidates = originals.isEmpty() ? group : originals;

        // 其次：文件名不带 _数字 后缀
        Pattern dupPattern = Pattern.compile("_\\d+$");
        List<BookEntry> noSuffix = candidates.stream()
                .filter(b -> {
                    String base = b.getFileName().replaceFirst("\\.mcfunction$", "");
                    return !dupPattern.matcher(base).find();
                })
                .collect(Collectors.toList());
        if (!noSuffix.isEmpty()) candidates = noSuffix;

        // 最终选文件名长度最小，若相同按字典序
        return candidates.stream()
                .min(Comparator.comparingInt((BookEntry b) -> b.getFileName().length())
                        .thenComparing(BookEntry::getFileName))
                .orElse(candidates.get(0));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}