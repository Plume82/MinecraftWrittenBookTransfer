package com.functionlib;

import com.common.McFunctionParser;
import com.functionlib.Bookprelib.PreprocessResult;
import com.functionlib.db.BookDao;
import com.functionlib.db.DatabaseManager;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * .mcfunction 数据库处理与分类模块
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

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("========================================");
        System.out.println("     .mcfunction 成书数据库处理模块");
        System.out.println("========================================");

        while (true) {
            if (currentFolder != null) {
                System.out.printf("当前目录: %s\n", currentFolder.getAbsolutePath());
                System.out.printf("共检测到 %d 个成书文件，共 %d 页\n", allBooks.size(), (int) Math.ceil(allBooks.size() / (double) PAGE_SIZE));
                File[] subDirs = currentFolder.listFiles(File::isDirectory);
                if (subDirs != null && subDirs.length > 0) {
                    System.out.println("子文件夹列表：");
                    for (File dir : subDirs) System.out.printf("  📁 %s\n", dir.getName());
                }
            }

            System.out.println("\n1. 打开含.mcfunction的成书文件夹 (GUI)");
            System.out.println("2. 数据库预处理（自动去重）");
            System.out.println("输入 author [作者名] 搜索该作者的书籍");
            System.out.println("输入 authorlist 显示所有作者列表");
            System.out.println("输入 authorpage [页码] 查看对应页的作者列表");
            System.out.println("输入 page [页码] 查看对应页的书籍列表");
            System.out.println("输入 title [书名关键词] 搜索书籍");
            System.out.println("输入 del [文件名] 删除书籍（移入回收站）");
            System.out.println("输入 add 创建新书籍");
            System.out.println("输入 openfolder [序号] 进入子文件夹");
            System.out.println("输入 open [文件名] 浏览书籍内容");
            System.out.println("输入 back 返回上一级文件夹");
            System.out.println("输入 recycle 管理回收站");
            System.out.println("输入 ai filter 全库价值过滤（AI筛选低质量书籍）");
            System.out.println("输入 exit 返回至主菜单");
            System.out.println("========================================");
            System.out.print("请输入命令: ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) break;
            if (input.equals("1")) { openFolder(); continue; }
            if (input.equals("2")) {
                if (currentFolder == null || allBooks.isEmpty()) {
                    System.out.println("请先打开文件夹或确保有书籍数据。");
                } else {
                    Bookprelib.runPreprocess(allBooks, booksByAuthor);
                    preprocessDatabase();
                }
                continue;
            }
            if (input.equalsIgnoreCase("ai filter")) {
                if (currentFolder == null || allBooks.isEmpty()) System.out.println("请先打开文件夹或确保有书籍数据。");
                else handleAiFilterCommand();
                continue;
            }
            if (input.equalsIgnoreCase("back")) {
                if (currentFolder == null) System.out.println("当前没有打开的文件夹。");
                else {
                    File parent = currentFolder.getParentFile();
                    if (parent == null) System.out.println("已经处于根目录，无法返回上一级。");
                    else { currentFolder = parent; scanFolder(currentFolder); }
                }
                continue;
            }
            if (input.toLowerCase().startsWith("open ")) {
                if (currentFolder == null) { System.out.println("请先打开文件夹。"); continue; }
                String target = input.substring(5).trim();
                if (target.isEmpty()) { System.out.println("请指定文件夹名或文件名。"); continue; }
                File subDir = new File(currentFolder, target);
                if (subDir.exists() && subDir.isDirectory()) {
                    currentFolder = subDir;
                    scanFolder(currentFolder);
                } else {
                    Editlib.openBook(currentFolder, target, allBooks);
                }
                continue;
            }
            if (input.toLowerCase().startsWith("openfolder ")) {
                if (currentFolder == null) { System.out.println("请先打开文件夹。"); continue; }
                try {
                    int idx = Integer.parseInt(input.substring(11).trim());
                    File targetDir = folderIndexMap.get(idx);
                    if (targetDir == null) System.out.println("序号无效。");
                    else { currentFolder = targetDir; scanFolder(currentFolder); }
                } catch (NumberFormatException e) { System.out.println("请输入有效的数字序号。"); }
                continue;
            }
            if (input.equalsIgnoreCase("authorlist")) {
                if (currentFolder == null) System.out.println("请先打开文件夹。");
                else handleAuthorListCommand();
                continue;
            }
            if (input.toLowerCase().startsWith("authorpage ")) {
                if (currentFolder == null) System.out.println("请先打开文件夹。");
                else handleAuthorPageCommand(input.substring(11).trim());
                continue;
            }
            if (input.toLowerCase().startsWith("page ")) {
                if (currentFolder == null) System.out.println("请先打开文件夹。");
                else handlePageCommand(input);
                continue;
            }
            if (input.toLowerCase().startsWith("author ")) {
                if (currentFolder == null) System.out.println("请先打开文件夹。");
                else handleAuthorCommand(input.substring(7).trim());
                continue;
            }
            if (input.toLowerCase().startsWith("title ")) {
                if (currentFolder == null) System.out.println("请先打开文件夹。");
                else handleTitleCommand(input.substring(6).trim());
                continue;
            }
            if (input.toLowerCase().startsWith("del ")) {
                if (currentFolder == null) System.out.println("请先打开文件夹。");
                else { Editlib.deleteBook(currentFolder, input.substring(4).trim(), allBooks); preprocessDatabase(); }
                continue;
            }
            if (input.equalsIgnoreCase("add")) {
                if (currentFolder == null) System.out.println("请先打开文件夹。");
                else { Editlib.addBook(currentFolder); preprocessDatabase(); }
                continue;
            }
            if (input.equalsIgnoreCase("recycle") || input.equalsIgnoreCase("rb")) {
                if (currentFolder == null) System.out.println("请先打开文件夹。");
                else manageRecycleBin();
                continue;
            }
            System.out.println("无效命令，请重新输入。");
        }
    }

    private static void openFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择包含 .mcfunction 文件的文件夹");
        String userDir = System.getProperty("user.dir");
        File defaultDir = new File(userDir);
        if (defaultDir.exists() && defaultDir.isDirectory()) chooser.setCurrentDirectory(defaultDir);
        else if (currentFolder != null) chooser.setCurrentDirectory(currentFolder);
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File folder = chooser.getSelectedFile();
        currentFolder = folder;
        initRecycleBin(folder);
        scanFolder(folder);
    }

    public static void initRecycleBin(File databaseRoot) {
        if (rootRecycleBinDir != null) {
            recycleBinDir = rootRecycleBinDir;
            recycleLogFile = rootRecycleLogFile;
            return;
        }
        rootRecycleBinDir = new File(databaseRoot, RECYCLE_FOLDER_NAME);
        rootRecycleBinDir.mkdirs();
        rootRecycleLogFile = new File(rootRecycleBinDir, RECYCLE_LOG_NAME);
        try { if (!rootRecycleLogFile.exists()) rootRecycleLogFile.createNewFile(); } catch (IOException e) { System.err.println("无法创建回收站日志文件: " + e.getMessage()); }
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
        } catch (IOException e) { System.err.println("移动文件到回收站失败: " + e.getMessage()); return false; }
    }

    private static void manageRecycleBin() {
        if (recycleBinDir == null || !recycleBinDir.exists()) { System.out.println("回收站不存在或尚未初始化。"); return; }
        File[] files = recycleBinDir.listFiles(f -> !f.getName().equals(RECYCLE_LOG_NAME));
        if (files == null || files.length == 0) { System.out.println("回收站为空。"); return; }
        Map<String, String> nameToOriginalPath = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(recycleLogFile.toPath());
            for (String line : lines) { String[] parts = line.split("\\|"); if (parts.length >= 2) nameToOriginalPath.put(parts[1], parts[0]); }
        } catch (IOException e) { System.err.println("读取日志失败: " + e.getMessage()); return; }

        System.out.println("\n========== 回收站文件列表 ==========");
        List<File> fileList = Arrays.asList(files);
        fileList.sort(Comparator.comparing(File::getName));
        for (int i = 0; i < fileList.size(); i++) {
            File f = fileList.get(i);
            System.out.printf("[%d] %s (原路径: %s)\n", i + 1, f.getName(), nameToOriginalPath.getOrDefault(f.getName(), "未知"));
        }
        System.out.println("=====================================");
        System.out.println("可用操作: recover [序号], del [序号], del all, back");
        System.out.print("请输入指令: ");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim().toLowerCase();
        if (input.equals("back")) return;
        if (input.equals("del all")) {
            System.out.print("确认清空回收站吗？(y/N): ");
            if (scanner.nextLine().trim().toLowerCase().startsWith("y")) {
                for (File f : fileList) try { Files.deleteIfExists(f.toPath()); } catch (IOException e) {}
                try { Files.write(recycleLogFile.toPath(), new byte[0]); } catch (IOException e) {}
                System.out.println("回收站已清空。");
            }
            return;
        }
        if (input.startsWith("recover ")) {
            try {
                int idx = Integer.parseInt(input.substring(8).trim()) - 1;
                if (idx < 0 || idx >= fileList.size()) { System.out.println("序号无效。"); return; }
                File target = fileList.get(idx);
                String originalPath = nameToOriginalPath.get(target.getName());
                if (originalPath == null) { System.out.println("无法获取原路径。"); return; }
                File originalFile = new File(originalPath);
                File parentDir = originalFile.getParentFile();
                if (!parentDir.exists()) { System.out.print("原目录不存在，是否创建并恢复？(y/N): "); if (!scanner.nextLine().trim().toLowerCase().startsWith("y")) return; parentDir.mkdirs(); }
                Files.move(target.toPath(), originalFile.toPath());
                System.out.println("已恢复到: " + originalPath);
            } catch (Exception e) { System.err.println("恢复失败: " + e.getMessage()); }
        } else if (input.startsWith("del ")) {
            try {
                int idx = Integer.parseInt(input.substring(4).trim()) - 1;
                if (idx < 0 || idx >= fileList.size()) { System.out.println("序号无效。"); return; }
                File target = fileList.get(idx);
                String content = Editlib.readBookContent(target);
                if (content != null) System.out.println("\n书籍内容预览（前500字符）：\n" + content.substring(0, Math.min(500, content.length())));
                System.out.print("确认彻底删除吗？(y/N): ");
                if (scanner.nextLine().trim().toLowerCase().startsWith("y")) { Files.deleteIfExists(target.toPath()); System.out.println("已彻底删除。"); }
            } catch (Exception e) { System.err.println("操作失败: " + e.getMessage()); }
        } else System.out.println("无效指令。");
    }

    private static void scanFolder(File folder) {
        System.err.println("scanFolder called for: " + folder.getAbsolutePath());
        allBooks.clear();
        booksByAuthor.clear();
        File[] mcFiles = folder.listFiles(f -> f.getName().toLowerCase().endsWith(".mcfunction"));
        if (mcFiles != null && mcFiles.length > 0) {
            System.out.println("正在扫描文件，请稍候...");
            int total = mcFiles.length, processed = 0;
            for (File file : mcFiles) {
                try {
                    BookEntry entry = parseBookEntry(file);
                    if (entry != null) {
                        allBooks.add(entry);
                        booksByAuthor.computeIfAbsent(entry.author, k -> new ArrayList<>()).add(entry);
                    }
                } catch (Exception e) { System.err.println("解析失败: " + file.getName() + " - " + e.getMessage()); }
                if (++processed % 50 == 0) System.out.printf("已处理 %d/%d 个文件...\n", processed, total);
            }
            System.out.printf("扫描完成。共找到 %d 个 .mcfunction 文件，解析出 %d 本书籍。\n", total, allBooks.size());
        } else System.out.println("该文件夹中没有 .mcfunction 文件。");

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
        printFolderTree(folder, 0);
        if (!folderIndexMap.isEmpty()) System.out.println("提示：输入 'openfolder 序号' 可进入子文件夹。");
    }

private static void printFolderTree(File folder, int indent) {
    File[] subDirs = folder.listFiles(file -> {
        if (!file.isDirectory()) return false;
        String name = file.getName();
        // ✅ 过滤特殊目录
        return !(".recycle_bin".equals(name) || ".dbbook".equals(name));
    });

    if (subDirs == null || subDirs.length == 0) {
        return;
    }

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
            if (indent < 1) {
                printFolderTree(dir, indent + 1);
            }
        }
    }
}

    public static String computeSha256(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) { String hex = Integer.toHexString(0xff & b); if (hex.length() == 1) hexString.append('0'); hexString.append(hex); }
            return hexString.toString();
        } catch (Exception e) { return ""; }
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

    private static String readBookContent(File file) {
        try { return String.join("\n", McFunctionParser.extractPagesFromFile(file.toPath())); } catch (Exception e) { return null; }
    }

    private static void handlePageCommand(String input) {
        try {
            int page = Integer.parseInt(input.substring(5).trim());
            int totalPages = (int) Math.ceil(allBooks.size() / (double) PAGE_SIZE);
            if (page < 1 || page > totalPages) System.out.printf("页数超出范围，有效页数为 1-%d\n", totalPages);
            else displayPage(page);
        } catch (NumberFormatException e) { System.out.println("页数格式错误，示例: page 1"); }
    }

    private static void handleAuthorCommand(String authorName) {
        if (authorName.isEmpty()) { System.out.println("作者名不能为空。示例: author Steve"); return; }
        List<BookEntry> authorBooks = allBooks.stream().filter(b -> b.author.toLowerCase().contains(authorName.toLowerCase())).collect(Collectors.toList());
        if (authorBooks.isEmpty()) { System.out.println("未找到该作者的书籍。"); return; }
        Map<String, List<BookEntry>> contentGroups = new LinkedHashMap<>();
        for (BookEntry book : authorBooks) {
            String content = readBookContent(book.file);
            if (content != null) contentGroups.computeIfAbsent(content, k -> new ArrayList<>()).add(book);
        }
        List<MergedBookEntry> mergedList = contentGroups.values().stream().map(MergedBookEntry::new).collect(Collectors.toList());
        mergedList.sort((a, b) -> {
            int cmp = compareWithChineseLast(a.author, b.author);
            return cmp != 0 ? cmp : compareWithChineseLast(a.representative.title, b.representative.title);
        });
        System.out.println("\n作者 [" + authorName + "] 的书籍（内容去重合并，共 " + mergedList.size() + " 个独立内容）：");
        System.out.println("┌────┬──────────────────────────────────────┬────────────────────┬──────────┬────────┬──────────┬────────┬────────┐");
        System.out.println("│序号│ 书名                                 │ 作者               │ 代表世代 │ 总副本 │ 原稿数量 │ 页数   │ 字数   │");
        System.out.println("├────┼──────────────────────────────────────┼────────────────────┼──────────┼────────┼──────────┼────────┼────────┤");
        int idx = 1;
        for (MergedBookEntry m : mergedList) {
            System.out.printf("│ %2d │ %-36s │ %-18s │ %-8s │ %6d │ %8d │ %6d │ %6d │\n",
                    idx++, truncate(m.representative.title, 36), truncate(m.author, 18), truncate(m.representative.generation, 8),
                    m.totalCopies, m.originalCount, m.pages, m.wordCount);
        }
        System.out.println("└────┴──────────────────────────────────────┴────────────────────┴──────────┴────────┴──────────┴────────┴────────┘");
        interactiveBookOperations(mergedList);
    }

    private static void interactiveBookOperations(List<MergedBookEntry> mergedList) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n可用操作: open [序号], del [序号], translate [序号] [语言], back 返回主菜单");
            System.out.print("请输入指令: ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("back")) break;
            if (input.startsWith("open ")) {
                try {
                    int num = Integer.parseInt(input.substring(5).trim());
                    if (num < 1 || num > mergedList.size()) { System.out.println("序号超出范围。"); continue; }
                    Editlib.openBookByEntry(currentFolder, mergedList.get(num - 1).representative, allBooks);
                } catch (NumberFormatException e) { System.out.println("格式错误，示例: open 1"); }
            } else if (input.startsWith("del ")) {
                try {
                    int num = Integer.parseInt(input.substring(4).trim());
                    if (num < 1 || num > mergedList.size()) { System.out.println("序号超出范围。"); continue; }
                    Editlib.deleteBookByEntry(currentFolder, mergedList.get(num - 1).representative, allBooks);
                    preprocessDatabase();
                    System.out.println("数据库已更新，返回主菜单。");
                    break;
                } catch (NumberFormatException e) { System.out.println("格式错误，示例: del 1"); }
            } else if (input.startsWith("translate ")) {
                String[] parts = input.substring(10).trim().split("\\s+");
                if (parts.length < 2) { System.out.println("格式错误，示例: translate 1 zh"); continue; }
                try {
                    int num = Integer.parseInt(parts[0]);
                    if (num < 1 || num > mergedList.size()) { System.out.println("序号超出范围。"); continue; }
                    BookEntry selected = mergedList.get(num - 1).representative;
                    List<String> pages = McFunctionParser.extractPagesFromFile(selected.file.toPath());
                    String fullText = String.join("\n\n", pages);
                    System.out.println("正在翻译《" + selected.title + "》...");
                    String translated = TranslateLib.translateAuto(fullText, parts[1]);
                    if (translated != null) System.out.println("\n========== 全书译文 ==========\n" + translated + "\n===============================");
                    else System.out.println("翻译失败。");
                } catch (Exception e) { System.out.println("操作失败: " + e.getMessage()); }
            } else System.out.println("无效指令。");
        }
    }

    private static void handleAiFilterCommand() {
        System.out.println("\n========================================");
        System.out.println("        AI 全库价值过滤");
        System.out.println("========================================");
        System.out.print("确定开始吗？(y/N): ");
        Scanner scanner = new Scanner(System.in);
        if (!scanner.nextLine().trim().toLowerCase().startsWith("y")) { System.out.println("已取消。"); return; }

        List<BookEntry> keptBooks = new ArrayList<>(), toDelete = new ArrayList<>();
        Map<BookEntry, DeepSeekAnalyzer.ValueAssessment> assessmentMap = new LinkedHashMap<>();
        int total = allBooks.size();
        System.out.println("\n开始评估，共 " + total + " 本书...（评估过程中可输入 'exit' 中断）");

        AtomicBoolean userRequestedExit = new AtomicBoolean(false);
        Thread inputListener = new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            while (!userRequestedExit.get()) if (sc.hasNextLine() && "exit".equals(sc.nextLine().trim().toLowerCase())) userRequestedExit.set(true);
        });
        inputListener.setDaemon(true);
        inputListener.start();

        Map<BookEntry, CompletableFuture<DeepSeekAnalyzer.ValueAssessment>> futures = new LinkedHashMap<>();
        for (BookEntry book : allBooks) {
            futures.put(book, CompletableFuture.supplyAsync(() -> {
                String content = readBookContent(book.file);
                if (content == null) return new DeepSeekAnalyzer.ValueAssessment(100, "读取失败，保留", "");
                try { return DeepSeekAnalyzer.assessValue(content); } catch (IOException e) { return new DeepSeekAnalyzer.ValueAssessment(100, "评估失败: " + e.getMessage(), ""); }
            }, DeepSeekAnalyzer.getExecutor()));
        }

        int processed = 0;
        for (Map.Entry<BookEntry, CompletableFuture<DeepSeekAnalyzer.ValueAssessment>> entry : futures.entrySet()) {
            if (userRequestedExit.get()) { futures.values().forEach(f -> f.cancel(true)); break; }
            processed++;
            System.out.printf("\n[%d/%d] 等待评估结果：《%s》...\n", processed, total, entry.getKey().title);
            try {
                DeepSeekAnalyzer.ValueAssessment assessment = entry.getValue().get();
                assessmentMap.put(entry.getKey(), assessment);
                System.out.printf("  📊 评分: %d/100  | 评语: %s\n", assessment.score, assessment.comment);
                if (assessment.shouldKeep()) keptBooks.add(entry.getKey());
                else { toDelete.add(entry.getKey()); System.out.println("  ❌ 低价值，建议删除。"); }
            } catch (InterruptedException | ExecutionException e) { System.err.println("  ❌ 获取评估结果失败: " + e.getMessage()); }
        }
        userRequestedExit.set(true);
        inputListener.interrupt();

        if (toDelete.isEmpty()) { System.out.println("\n🎉 所有已评估书籍均通过AI价值评估，无需删除！"); return; }

        System.out.println("\n========================================");
        System.out.println("评估完成。共发现 " + toDelete.size() + " 本低价值书籍：");
        System.out.println("┌────┬──────────────────────────────────────┬────────────────────┬──────┬────────────────────────────┐");
        System.out.println("│序号│ 书名                                 │ 作者               │ 评分 │ 评语                       │");
        System.out.println("├────┼──────────────────────────────────────┼────────────────────┼──────┼────────────────────────────┤");
        int idx = 1;
        for (BookEntry b : toDelete) {
            DeepSeekAnalyzer.ValueAssessment va = assessmentMap.get(b);
            System.out.printf("│ %2d │ %-36s │ %-18s │ %3d  │ %-26s │\n", idx++, truncate(b.title, 36), truncate(b.author, 18), va.score, truncate(va.comment, 26));
        }
        System.out.println("└────┴──────────────────────────────────────┴────────────────────┴──────┴────────────────────────────┘");
        System.out.print("\n确认将以上书籍移入回收站吗？(y/N): ");
        if (!scanner.nextLine().trim().toLowerCase().startsWith("y")) { System.out.println("已取消。"); return; }

        Map<String, String> contentMap = new HashMap<>();
        for (BookEntry b : toDelete) contentMap.put(b.fileName, readBookContent(b.file));
        for (BookEntry b : keptBooks) contentMap.put(b.fileName, readBookContent(b.file));

        int deleted = 0;
        List<BookEntry> successfullyDeleted = new ArrayList<>();
        for (BookEntry b : toDelete) if (moveToRecycleBin(new File(currentFolder, b.fileName))) { deleted++; successfullyDeleted.add(b); }
        System.out.printf("已移入回收站 %d 个文件。\n", deleted);

        if (!successfullyDeleted.isEmpty()) {
            System.out.println("正在生成删除报告...");
            String reportFileName = "已删除的成书报告_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".docx";
            File reportFile = new File(currentFolder.getParent(), reportFileName);
            try {
                AiDeletedReportGenerator.generateFullReport(successfullyDeleted, keptBooks, assessmentMap, contentMap, reportFile);
                System.out.println("已生成删除报告: " + reportFile.getAbsolutePath());
            } catch (IOException e) { System.err.println("生成删除报告失败: " + e.getMessage()); }
        }
        preprocessDatabase();
        System.out.println("AI 价值过滤完成。");
    }

    private static void handleTitleCommand(String keyword) {
        if (keyword.isEmpty()) { System.out.println("书名关键词不能为空。"); return; }
        List<BookEntry> matched = allBooks.stream().filter(b -> b.title.toLowerCase().contains(keyword.toLowerCase())).collect(Collectors.toList());
        if (matched.isEmpty()) { System.out.println("未找到匹配的书籍。"); return; }
        Map<String, List<BookEntry>> contentGroups = new LinkedHashMap<>();
        for (BookEntry book : matched) {
            String content = readBookContent(book.file);
            if (content != null) contentGroups.computeIfAbsent(content, k -> new ArrayList<>()).add(book);
        }
        List<MergedBookEntry> mergedList = contentGroups.values().stream().map(MergedBookEntry::new).collect(Collectors.toList());
        mergedList.sort((a, b) -> {
            int cmp = compareWithChineseLast(a.author, b.author);
            return cmp != 0 ? cmp : compareWithChineseLast(a.representative.title, b.representative.title);
        });
        System.out.println("\n书名包含 [" + keyword + "] 的书籍（内容去重合并，共 " + mergedList.size() + " 个独立内容）：");
        System.out.println("┌────────────────────┬──────────────────────────────────────┬──────────┬────────┬──────────┬────────┬────────┐");
        System.out.println("│ 作者               │ 书名                                 │ 代表世代 │ 总副本 │ 原稿数量 │ 页数   │ 字数   │");
        System.out.println("├────────────────────┼──────────────────────────────────────┼──────────┼────────┼──────────┼────────┼────────┤");
        for (MergedBookEntry m : mergedList) {
            System.out.printf("│ %-18s │ %-36s │ %-8s │ %6d │ %8d │ %6d │ %6d │\n",
                    truncate(m.author, 18), truncate(m.representative.title, 36), truncate(m.representative.generation, 8),
                    m.totalCopies, m.originalCount, m.pages, m.wordCount);
        }
        System.out.println("└────────────────────┴──────────────────────────────────────┴──────────┴────────┴──────────┴────────┴────────┘");
    }

    private static void handleAuthorListCommand() {
        if (booksByAuthor.isEmpty()) { System.out.println("暂无作者信息。"); return; }
        List<String> authors = new ArrayList<>(booksByAuthor.keySet());
        int totalPages = (int) Math.ceil(authors.size() / (double) PAGE_SIZE);
        System.out.printf("\n作者列表（共 %d 位作者，%d 页）\n", authors.size(), totalPages);
        System.out.print("请输入页码（回车查看第1页）: ");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();
        int page = 1;
        if (!input.isEmpty()) try { page = Integer.parseInt(input); } catch (NumberFormatException e) {}
        if (page < 1 || page > totalPages) page = 1;
        displayAuthorPage(page, authors, totalPages);
    }

    private static void handleAuthorPageCommand(String pageStr) {
        if (booksByAuthor.isEmpty()) { System.out.println("暂无作者信息。"); return; }
        try {
            int page = Integer.parseInt(pageStr);
            List<String> authors = new ArrayList<>(booksByAuthor.keySet());
            int totalPages = (int) Math.ceil(authors.size() / (double) PAGE_SIZE);
            if (page < 1 || page > totalPages) System.out.printf("页码超出范围，有效页数为 1-%d。\n", totalPages);
            else displayAuthorPage(page, authors, totalPages);
        } catch (NumberFormatException e) { System.out.println("页码格式错误。"); }
    }

    private static void displayAuthorPage(int page, List<String> authors, int totalPages) {
        int start = (page - 1) * PAGE_SIZE, end = Math.min(start + PAGE_SIZE, authors.size());
        System.out.println("\n┌──────────────────────────────────────────────┐");
        System.out.printf("│ 第 %d 页 / 共 %d 页 (作者列表)              │\n", page, totalPages);
        System.out.println("├────────────────────┬─────────────────────────┤");
        System.out.println("│ 作者               │ 书籍数量                │");
        System.out.println("├────────────────────┼─────────────────────────┤");
        for (int i = start; i < end; i++) {
            String author = authors.get(i);
            System.out.printf("│ %-18s │ %-23d │\n", truncate(author, 18), booksByAuthor.get(author).size());
        }
        System.out.println("└────────────────────┴─────────────────────────┘");
    }

    private static void displayPage(int page) {
        int start = (page - 1) * PAGE_SIZE, end = Math.min(start + PAGE_SIZE, allBooks.size());
        System.out.println("\n┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.printf("│ 第 %d 页 / 共 %d 页                                                          │\n", page, (int) Math.ceil(allBooks.size() / (double) PAGE_SIZE));
        System.out.println("├────────────┬──────────────────────────────────────┬────────────────────┬──────┤");
        System.out.println("│ 序号       │ 书名                                 │ 作者               │ 类型 │");
        System.out.println("├────────────┼──────────────────────────────────────┼────────────────────┼──────┤");
        for (int i = start; i < end; i++) {
            BookEntry book = allBooks.get(i);
            System.out.printf("│ %-10d │ %-36s │ %-18s │ %-4s │\n", i + 1, truncate(book.title, 36), truncate(book.author, 18), book.generation);
        }
        System.out.println("└────────────┴──────────────────────────────────────┴────────────────────┴──────┘");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    private static void preprocessDatabase() { refreshDatabase(); }

    private static void sortWithChineseLast(List<String> list) { list.sort((a, b) -> compareWithChineseLast(a, b)); }

    private static int compareWithChineseLast(String s1, String s2) {
        boolean c1 = containsChinese(s1), c2 = containsChinese(s2);
        if (c1 && !c2) return 1;
        if (!c1 && c2) return -1;
        return Collator.getInstance(Locale.CHINA).compare(s1, s2);
    }

    private static boolean containsChinese(String s) { return s.matches(".*[\\u4e00-\\u9fa5].*"); }

    public static class BookEntry {
        private String fileName, title, author, generation, contentHash;
        private int pages, wordCount;
        private File file;
        public BookEntry(String fileName, String title, String author, String generation, File file, int pages, int wordCount, String contentHash) {
            this.fileName = fileName; this.title = title; this.author = author; this.generation = generation;
            this.file = file; this.pages = pages; this.wordCount = wordCount; this.contentHash = contentHash;
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
        BookEntry representative; String author; int totalCopies, originalCount, pages, wordCount;
        MergedBookEntry(List<BookEntry> group) {
            this.representative = selectRepresentative(group);
            this.author = representative.author;
            this.totalCopies = group.size();
            this.originalCount = (int) group.stream().filter(b -> "原稿".equals(b.generation)).count();
            this.pages = representative.pages;
            this.wordCount = representative.wordCount;
        }
        private static BookEntry selectRepresentative(List<BookEntry> group) {
            List<BookEntry> originals = group.stream().filter(b -> "原稿".equals(b.generation)).collect(Collectors.toList());
            List<BookEntry> candidates = originals.isEmpty() ? group : originals;
            List<BookEntry> noSuffix = candidates.stream().filter(b -> !Pattern.compile("_\\d+$").matcher(b.fileName.replaceFirst("\\.mcfunction$", "")).find()).collect(Collectors.toList());
            if (!noSuffix.isEmpty()) candidates = noSuffix;
            return candidates.stream().min(Comparator.comparingInt((BookEntry b) -> b.fileName.length()).thenComparing(b -> b.fileName)).orElse(candidates.get(0));
        }
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
    // 扫描文件系统，填充 allBooks
    scanFolder(folder);
    // 保存到数据库
    batchSaveToDatabase(folder, allBooks);
}

    public static void scanAndSaveToDatabase(File folder) {
    // 临时保存原 currentFolder（如果必要）
    File old = currentFolder;
    currentFolder = folder;
    scanFolder(folder);
     batchSaveToDatabase(folder, allBooks);
    currentFolder = old;  // 恢复
}

    private static void rebuildBooksByAuthor() {
        booksByAuthor.clear();
        for (BookEntry book : allBooks) booksByAuthor.computeIfAbsent(book.getAuthor(), k -> new ArrayList<>()).add(book);
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

    // FunctionlibApp.java

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
    // 跳过回收站和数据库文件目录（防止将 .dbbook 当作目录进入）
    if (".recycle_bin".equals(folderName) || ".dbbook".equals(folderName)) {
        System.out.println("跳过特殊目录: " + folder.getAbsolutePath());
        return;
    }

    // 从数据库加载当前文件夹的书籍
    try {
        List<BookEntry> books = BookDao.loadAll(folder);
        collector.addAll(books);
    } catch (SQLException e) {
        System.err.println("读取数据库失败: " + folder.getAbsolutePath() + " - " + e.getMessage());
    }

    // 递归子目录，但必须过滤掉特殊名称
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
    public static List<BookEntry> getAllBooks() { return new ArrayList<>(allBooks); }

    public static void refreshDatabase() {
    if (currentFolder == null) return;

    // 删除 H2 数据库物理文件（确保彻底重建）
    File dbFile = new File(currentFolder, ".dbbook.mv.db");
    if (dbFile.exists()) {
        boolean deleted = dbFile.delete();
        System.out.println("删除旧数据库文件: " + dbFile.getAbsolutePath() + " -> " + deleted);
    }

    // 重新扫描并保存
    scanFolder(currentFolder);
     batchSaveToDatabase(currentFolder, allBooks);  // 注意传入参数
}

    public static void runAiFilterFromGUI(File folder) {
        currentFolder = folder;
        if (allBooks.isEmpty()) scanFolder(folder);
        handleAiFilterCommand();
    }

    public static Map<String, List<BookEntry>> groupByAuthor(List<BookEntry> books) {
        Map<String, List<BookEntry>> map = new LinkedHashMap<>();
        for (BookEntry book : books) map.computeIfAbsent(book.getAuthor(), k -> new ArrayList<>()).add(book);
        return map;
    }

    public static PreprocessResult runPreprocessFromGUI(File rootFolder) {
        List<BookEntry> all = getAllBooksFromFolder(rootFolder);
        return Bookprelib.analyzeDuplicates(groupByAuthor(all));
    }

    public static void manageRecycleBinFromGUI(File folder) {
        currentFolder = folder;
        initRecycleBin(folder);
        manageRecycleBin();
    }
    /**
 * 递归清理所有名为 .dbbook 的非法目录
 * @param root 起始扫描的根目录
 */
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
                // 正常目录，继续递归清理
                cleanInvalidDbbookDirs(child);
            }
        }
        // 文件忽略（.dbbook.mv.db 等正常数据库文件保留）
    }
}

/**
 * 递归删除目录及其下所有内容
 */
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
}