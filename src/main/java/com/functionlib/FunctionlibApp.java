package com.functionlib;

import com.common.McFunctionParser;
import com.functionlib.Bookprelib.PreprocessResult;
import com.functionlib.db.BookDao;
import com.functionlib.db.DatabaseManager;

import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * .mcfunction 数据库处理与分类模块（GUI 专用，命令行代码已移除）
 */
public class FunctionlibApp {

    private static File currentFolder = null;
    private static List<BookEntry> allBooks = new ArrayList<>();
    private static Map<String, List<BookEntry>> booksByAuthor = new LinkedHashMap<>();
    private static Map<Integer, File> folderIndexMap = new LinkedHashMap<>();
    private static File recycleBinDir = null;
    private static File recycleLogFile = null;
    private static final String RECYCLE_FOLDER_NAME = ".recycle_bin";
    private static final String RECYCLE_LOG_NAME = "recycle_log.txt";
    private static final int PAGE_SIZE = 50;

    private static File rootRecycleBinDir = null;
    private static File rootRecycleLogFile = null;

    // ==================== 回收站核心 ====================
    public static void initRecycleBin(File databaseRoot) {
        if (rootRecycleBinDir != null) {
            recycleBinDir = rootRecycleBinDir;
            recycleLogFile = rootRecycleLogFile;
            return;
        }
        rootRecycleBinDir = new File(databaseRoot, RECYCLE_FOLDER_NAME);
        rootRecycleBinDir.mkdirs();
        rootRecycleLogFile = new File(rootRecycleBinDir, RECYCLE_LOG_NAME);
        try {
            if (!rootRecycleLogFile.exists()) rootRecycleLogFile.createNewFile();
        } catch (IOException e) {
            System.err.println("无法创建回收站日志文件: " + e.getMessage());
        }
        recycleBinDir = rootRecycleBinDir;
        recycleLogFile = rootRecycleLogFile;
    }

    public static boolean moveToRecycleBin(File sourceFile) {
        if (recycleBinDir == null) return false;
        String uniqueName = sourceFile.getName() + "_" + System.currentTimeMillis();
        File destFile = new File(recycleBinDir, uniqueName);
        try {
            Files.move(sourceFile.toPath(), destFile.toPath());
            String logEntry = String.format("%s|%s|%d%n", sourceFile.getAbsolutePath(), uniqueName, System.currentTimeMillis());
            Files.write(recycleLogFile.toPath(), logEntry.getBytes(), StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            System.err.println("移动文件到回收站失败: " + e.getMessage());
            return false;
        }
    }

    public static File getRecycleBinDir() {
        return recycleBinDir;
    }

    public static File getRecycleLogFile() {
        return recycleLogFile;
    }

    // ==================== 扫描与解析 ====================
    private static void scanFolder(File folder) {
        allBooks.clear();
        booksByAuthor.clear();
        File[] mcFiles = folder.listFiles(f -> f.getName().toLowerCase().endsWith(".mcfunction"));
        if (mcFiles != null && mcFiles.length > 0) {
            System.out.println("正在扫描文件，请稍候...");
            int total = mcFiles.length;
            for (File file : mcFiles) {
                try {
                    BookEntry entry = parseBookEntry(file);
                    if (entry != null) {
                        allBooks.add(entry);
                        booksByAuthor.computeIfAbsent(entry.author, k -> new ArrayList<>()).add(entry);
                    }
                } catch (Exception e) {
                    System.err.println("解析失败: " + file.getName() + " - " + e.getMessage());
                }
            }
            System.out.printf("扫描完成。共找到 %d 个 .mcfunction 文件，解析出 %d 本书籍。\n", total, allBooks.size());
        } else {
            System.out.println("该文件夹中没有 .mcfunction 文件。");
        }

        // 排序（GUI 表格也需要排序数据）
        List<String> sortedAuthors = new ArrayList<>(booksByAuthor.keySet());
        sortWithChineseLast(sortedAuthors);
        Map<String, List<BookEntry>> sortedMap = new LinkedHashMap<>();
        for (String author : sortedAuthors) {
            List<BookEntry> entries = booksByAuthor.get(author);
            entries.sort((a, b) -> compareWithChineseLast(a.title, b.title));
            sortedMap.put(author, entries);
        }
        booksByAuthor = sortedMap;
        allBooks.sort((a, b) -> {
            int cmp = compareWithChineseLast(a.author, b.author);
            return cmp != 0 ? cmp : compareWithChineseLast(a.title, b.title);
        });

        // 打印子文件夹列表（控制台输出，不影响 GUI）
        printFolderTree(folder, 0);
        if (!folderIndexMap.isEmpty()) {
            System.out.println("提示：输入 'openfolder 序号' 可进入子文件夹。");
        }
    }

    public static BookEntry parseBookEntry(File file) throws IOException {
        String content = Files.readString(file.toPath());
        String title = extractField(content, "title");
        String author = extractField(content, "author");
        int generation = extractGeneration(content);
        String generationName = getGenerationName(generation);
        if (title == null || title.isEmpty()) title = file.getName().replace(".mcfunction", "");
        if (author == null || author.isEmpty()) author = "未知";
        List<String> pagesList = McFunctionParser.extractPagesFromFile(file.toPath());
        int pages = pagesList.size();
        int wordCount = pagesList.stream().mapToInt(p -> p.replaceAll("[\\s\\n\\r]+", "").length()).sum();
        String fullContent = String.join("\n", pagesList);
        String contentHash = computeSha256(fullContent);
        return new BookEntry(file.getName(), title, author, generationName, file, pages, wordCount, contentHash);
    }

    public static String extractField(String content, String fieldName) {
        Pattern pattern = Pattern.compile(fieldName + "[\"']\\s*:\\s*[\"']([^\"']+)[\"']");
        Matcher m = pattern.matcher(content);
        if (m.find()) return m.group(1);
        pattern = Pattern.compile(fieldName + "\\s*:\\s*([^,}\\]]+)");
        m = pattern.matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }

    public static int extractGeneration(String content) {
        Matcher m = Pattern.compile("generation\\s*:\\s*(\\d+)").matcher(content);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    public static String getGenerationName(int generation) {
        switch (generation) {
            case 0: return "原稿";
            case 1: return "原稿的副本";
            case 2: return "副本的副本";
            case 3: return "破烂不堪";
            default: return "未知";
        }
    }

    public static String computeSha256(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String readBookContent(File file) {
        try {
            return String.join("\n", McFunctionParser.extractPagesFromFile(file.toPath()));
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== AI 过滤（GUI 调用） ====================
    private static void handleAiFilterCommand() {
        // 该方法仍包含命令行交互，但 GUI 直接调用它，为保持功能暂时保留
        System.out.println("\n========================================");
        System.out.println("        AI 全库价值过滤");
        System.out.println("========================================");
        System.out.print("确定开始吗？(y/N): ");
        Scanner scanner = new Scanner(System.in);
        if (!scanner.nextLine().trim().toLowerCase().startsWith("y")) {
            System.out.println("已取消。");
            return;
        }

        List<BookEntry> keptBooks = new ArrayList<>(), toDelete = new ArrayList<>();
        Map<BookEntry, DeepSeekAnalyzer.ValueAssessment> assessmentMap = new LinkedHashMap<>();
        int total = allBooks.size();
        System.out.println("\n开始评估，共 " + total + " 本书...（评估过程中可输入 'exit' 中断）");

        AtomicBoolean userRequestedExit = new AtomicBoolean(false);
        Thread inputListener = new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            while (!userRequestedExit.get())
                if (sc.hasNextLine() && "exit".equals(sc.nextLine().trim().toLowerCase()))
                    userRequestedExit.set(true);
        });
        inputListener.setDaemon(true);
        inputListener.start();

        Map<BookEntry, CompletableFuture<DeepSeekAnalyzer.ValueAssessment>> futures = new LinkedHashMap<>();
        for (BookEntry book : allBooks) {
            futures.put(book, CompletableFuture.supplyAsync(() -> {
                String content = readBookContent(book.file);
                if (content == null) return new DeepSeekAnalyzer.ValueAssessment(100, "读取失败，保留", "");
                try {
                    return DeepSeekAnalyzer.assessValue(content);
                } catch (IOException e) {
                    return new DeepSeekAnalyzer.ValueAssessment(100, "评估失败: " + e.getMessage(), "");
                }
            }, DeepSeekAnalyzer.getExecutor()));
        }

        int processed = 0;
        for (Map.Entry<BookEntry, CompletableFuture<DeepSeekAnalyzer.ValueAssessment>> entry : futures.entrySet()) {
            if (userRequestedExit.get()) {
                futures.values().forEach(f -> f.cancel(true));
                break;
            }
            processed++;
            System.out.printf("\n[%d/%d] 等待评估结果：《%s》...\n", processed, total, entry.getKey().title);
            try {
                DeepSeekAnalyzer.ValueAssessment assessment = entry.getValue().get();
                assessmentMap.put(entry.getKey(), assessment);
                System.out.printf("  📊 评分: %d/100  | 评语: %s\n", assessment.score, assessment.comment);
                if (assessment.shouldKeep()) keptBooks.add(entry.getKey());
                else {
                    toDelete.add(entry.getKey());
                    System.out.println("  ❌ 低价值，建议删除。");
                }
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("  ❌ 获取评估结果失败: " + e.getMessage());
            }
        }
        userRequestedExit.set(true);
        inputListener.interrupt();

        if (toDelete.isEmpty()) {
            System.out.println("\n🎉 所有已评估书籍均通过AI价值评估，无需删除！");
            return;
        }

        System.out.println("\n========================================");
        System.out.println("评估完成。共发现 " + toDelete.size() + " 本低价值书籍：");
        System.out.println("┌────┬──────────────────────────────────────┬────────────────────┬──────┬────────────────────────────┐");
        System.out.println("│序号│ 书名                                 │ 作者               │ 评分 │ 评语                       │");
        System.out.println("├────┼──────────────────────────────────────┼────────────────────┼──────┼────────────────────────────┤");
        int idx = 1;
        for (BookEntry b : toDelete) {
            DeepSeekAnalyzer.ValueAssessment va = assessmentMap.get(b);
            System.out.printf("│ %2d │ %-36s │ %-18s │ %3d  │ %-26s │\n",
                    idx++, truncate(b.title, 36), truncate(b.author, 18), va.score, truncate(va.comment, 26));
        }
        System.out.println("└────┴──────────────────────────────────────┴────────────────────┴──────┴────────────────────────────┘");
        System.out.print("\n确认将以上书籍移入回收站吗？(y/N): ");
        if (!scanner.nextLine().trim().toLowerCase().startsWith("y")) {
            System.out.println("已取消。");
            return;
        }

        Map<String, String> contentMap = new HashMap<>();
        for (BookEntry b : toDelete) contentMap.put(b.fileName, readBookContent(b.file));
        for (BookEntry b : keptBooks) contentMap.put(b.fileName, readBookContent(b.file));

        int deleted = 0;
        List<BookEntry> successfullyDeleted = new ArrayList<>();
        for (BookEntry b : toDelete)
            if (moveToRecycleBin(new File(currentFolder, b.fileName))) {
                deleted++;
                successfullyDeleted.add(b);
            }
        System.out.printf("已移入回收站 %d 个文件。\n", deleted);

        if (!successfullyDeleted.isEmpty()) {
            System.out.println("正在生成删除报告...");
            String reportFileName = "已删除的成书报告_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".docx";
            File reportFile = new File(currentFolder.getParent(), reportFileName);
            try {
                AiDeletedReportGenerator.generateFullReport(successfullyDeleted, keptBooks, assessmentMap, contentMap, reportFile);
                System.out.println("已生成删除报告: " + reportFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("生成删除报告失败: " + e.getMessage());
            }
        }
        refreshDatabase(); // 替代原来的 preprocessDatabase()
        System.out.println("AI 价值过滤完成。");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    // ==================== GUI 支持方法 ====================
    public static void openFolderFromGUI(File folder) {
        currentFolder = folder;
        initRecycleBin(folder);
        try {
            if (!BookDao.isDatabaseEmpty(folder)) {
                allBooks = BookDao.loadAll(folder);
                rebuildBooksByAuthor();
                System.out.println("从数据库加载了 " + allBooks.size() + " 本书籍");
                return;
            }
        } catch (SQLException e) {
            System.err.println("数据库加载失败，回退到文件扫描: " + e.getMessage());
        }
        scanFolder(folder);
        batchSaveToDatabase(folder, allBooks);
    }

    public static void scanAndSaveToDatabase(File folder) {
        File old = currentFolder;
        currentFolder = folder;
        scanFolder(folder);
        batchSaveToDatabase(folder, allBooks);
        currentFolder = old;
    }

    public static void openRecycleBinGUI(Frame owner, File databaseRoot, Runnable onRefresh) {
        initRecycleBin(databaseRoot);
        new RecycleBinDialog(owner, recycleBinDir, recycleLogFile, onRefresh);
    }

    private static void rebuildBooksByAuthor() {
        booksByAuthor.clear();
        for (BookEntry book : allBooks)
            booksByAuthor.computeIfAbsent(book.getAuthor(), k -> new ArrayList<>()).add(book);
        booksByAuthor.values().forEach(list -> list.sort((a, b) -> compareWithChineseLast(a.getTitle(), b.getTitle())));
    }

    private static void batchSaveToDatabase(File folder, List<BookEntry> books) {
        if (folder == null || books.isEmpty()) return;
        String sql = "MERGE INTO books KEY(path) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection(folder);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (BookEntry book : books) {
                ps.setString(1, book.getFile().getAbsolutePath().replace("\\", "/"));
                ps.setString(2, book.getTitle());
                ps.setString(3, book.getAuthor());
                ps.setString(4, book.getGeneration());
                ps.setInt(5, book.getPages());
                ps.setInt(6, book.getWordCount());
                ps.setString(7, book.getContentHash());
                ps.setLong(8, book.getFile().lastModified());
                ps.addBatch();
            }
            ps.executeBatch();
            System.out.println("批量保存 " + books.size() + " 本书籍到 " + folder.getAbsolutePath());
        } catch (SQLException e) {
            System.err.println("批量保存失败: " + e.getMessage());
        }
    }

    public static List<BookEntry> getAllBooksFromFolder(File rootFolder) {
        List<BookEntry> all = new ArrayList<>();
        long start = System.currentTimeMillis();
        collectBooksRecursively(rootFolder, all);
        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("[getAllBooksFromFolder] 扫描完成，共 %d 本书，耗时 %.2f 秒\n", all.size(), elapsed / 1000.0);
        return all;
    }

    private static void collectBooksRecursively(File folder, List<BookEntry> collector) {
        if (folder == null || !folder.isDirectory()) return;
        String folderName = folder.getName();
        if (".recycle_bin".equals(folderName) || ".dbbook".equals(folderName)) {
            System.out.println("跳过特殊目录: " + folder.getAbsolutePath());
            return;
        }
        try {
            List<BookEntry> books = BookDao.loadAll(folder);
            collector.addAll(books);
        } catch (SQLException e) {
            System.err.println("读取数据库失败: " + folder.getAbsolutePath() + " - " + e.getMessage());
        }
        File[] subDirs = folder.listFiles(file -> {
            if (!file.isDirectory()) return false;
            String name = file.getName();
            return !(".recycle_bin".equals(name) || ".dbbook".equals(name));
        });
        if (subDirs != null) {
            for (File sub : subDirs) {
                collectBooksRecursively(sub, collector);
            }
        }
    }

    public static List<BookEntry> getAllBooks() {
        return new ArrayList<>(allBooks);
    }

    public static void refreshDatabase() {
        if (currentFolder == null) return;
        File dbFile = new File(currentFolder, ".dbbook.mv.db");
        if (dbFile.exists()) {
            boolean deleted = dbFile.delete();
            System.out.println("删除旧数据库文件: " + dbFile.getAbsolutePath() + " -> " + deleted);
        }
        scanFolder(currentFolder);
        batchSaveToDatabase(currentFolder, allBooks);
    }

    public static void runAiFilterFromGUI(File folder) {
        currentFolder = folder;
        if (allBooks.isEmpty()) scanFolder(folder);
        handleAiFilterCommand();
    }

    public static Map<String, List<BookEntry>> groupByAuthor(List<BookEntry> books) {
        Map<String, List<BookEntry>> map = new LinkedHashMap<>();
        for (BookEntry book : books)
            map.computeIfAbsent(book.getAuthor(), k -> new ArrayList<>()).add(book);
        return map;
    }

    public static PreprocessResult runPreprocessFromGUI(File rootFolder) {
        List<BookEntry> all = getAllBooksFromFolder(rootFolder);
        return Bookprelib.analyzeDuplicates(groupByAuthor(all));
    }

    // 兼容旧版 GUI 调用（已不推荐）
    public static void manageRecycleBinFromGUI(File folder) {
        currentFolder = folder;
        initRecycleBin(folder);
        // 不再实现具体逻辑，直接打开图形对话框的方式请使用 openRecycleBinGUI
        System.out.println("请使用 openRecycleBinGUI 方法打开回收站。");
    }

    public static void cleanInvalidDbbookDirs(File root) {
        if (root == null || !root.isDirectory()) return;
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                if (".dbbook".equals(child.getName())) {
                    System.out.println("删除非法目录: " + child.getAbsolutePath());
                    deleteDirectoryRecursively(child);
                } else {
                    cleanInvalidDbbookDirs(child);
                }
            }
        }
    }

    private static void deleteDirectoryRecursively(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectoryRecursively(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    // ==================== 排序与打印辅助（scanFolder 依赖） ====================
    private static void sortWithChineseLast(List<String> list) {
        list.sort((a, b) -> compareWithChineseLast(a, b));
    }

    private static int compareWithChineseLast(String s1, String s2) {
        boolean c1 = containsChinese(s1), c2 = containsChinese(s2);
        if (c1 && !c2) return 1;
        if (!c1 && c2) return -1;
        return Collator.getInstance(Locale.CHINA).compare(s1, s2);
    }

    private static boolean containsChinese(String s) {
        return s.matches(".*[\\u4e00-\\u9fa5].*");
    }

    private static void printFolderTree(File folder, int indent) {
        File[] subDirs = folder.listFiles(file -> {
            if (!file.isDirectory()) return false;
            String name = file.getName();
            return !(".recycle_bin".equals(name) || ".dbbook".equals(name));
        });
        if (subDirs == null || subDirs.length == 0) return;
        List<File> dirList = Arrays.asList(subDirs);
        dirList.sort((a, b) -> compareWithChineseLast(a.getName(), b.getName()));
        if (indent == 0) {
            System.out.println("\n📁 子文件夹列表：");
            folderIndexMap.clear();
            int index = 1;
            for (File dir : dirList) {
                folderIndexMap.put(index, dir);
                System.out.printf("  [%d] 📁 %s\n", index++, dir.getName());
            }
        } else {
            for (File dir : dirList) {
                String prefix = String.format("%" + (indent * 4) + "s", "");
                System.out.println(prefix + "└── 📁 " + dir.getName());
                if (indent < 1) printFolderTree(dir, indent + 1);
            }
        }
    }

    // ==================== 数据类 ====================
    public static class BookEntry {
        private String fileName, title, author, generation, contentHash;
        private int pages, wordCount;
        private File file;

        public BookEntry(String fileName, String title, String author, String generation, File file,
                         int pages, int wordCount, String contentHash) {
            this.fileName = fileName;
            this.title = title;
            this.author = author;
            this.generation = generation;
            this.file = file;
            this.pages = pages;
            this.wordCount = wordCount;
            this.contentHash = contentHash;
        }

        public String getFileName() { return fileName; }
        public String getTitle() { return title; }
        public String getAuthor() { return author; }
        public String getGeneration() { return generation; }
        public int getPages() { return pages; }
        public int getWordCount() { return wordCount; }
        public File getFile() { return file; }
        public String getContentHash() { return contentHash; }
    }

    static class MergedBookEntry {
        BookEntry representative;
        String author;
        int totalCopies, originalCount, pages, wordCount;

        MergedBookEntry(List<BookEntry> group) {
            this.representative = selectRepresentative(group);
            this.author = representative.author;
            this.totalCopies = group.size();
            this.originalCount = (int) group.stream().filter(b -> "原稿".equals(b.generation)).count();
            this.pages = representative.pages;
            this.wordCount = representative.wordCount;
        }

        private static BookEntry selectRepresentative(List<BookEntry> group) {
            List<BookEntry> originals = group.stream()
                    .filter(b -> "原稿".equals(b.generation))
                    .collect(Collectors.toList());
            List<BookEntry> candidates = originals.isEmpty() ? group : originals;
            List<BookEntry> noSuffix = candidates.stream()
                    .filter(b -> !Pattern.compile("_\\d+$")
                            .matcher(b.fileName.replaceFirst("\\.mcfunction$", "")).find())
                    .collect(Collectors.toList());
            if (!noSuffix.isEmpty()) candidates = noSuffix;
            return candidates.stream()
                    .min(Comparator.comparingInt((BookEntry b) -> b.fileName.length())
                            .thenComparing(b -> b.fileName))
                    .orElse(candidates.get(0));
        }
    }
}