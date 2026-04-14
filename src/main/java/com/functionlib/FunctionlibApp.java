package com.functionlib;

import com.common.McFunctionParser;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Collator;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * .mcfunction 数据库处理与分类模块
 * 负责批量导入、索引、分类、预处理 .mcfunction 文件
 */
public class FunctionlibApp {

    // 当前打开的文件夹
    private static File currentFolder = null;
    // 解析出的所有书籍信息
    private static List<BookEntry> allBooks = new ArrayList<>();
    // 作者 -> 书籍列表的映射（用于分类）
    private static Map<String, List<BookEntry>> booksByAuthor = new LinkedHashMap<>();
    // 每页显示条目数
    private static final int PAGE_SIZE = 50;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("========================================");
        System.out.println("     .mcfunction 成书数据库处理模块");
        System.out.println("========================================");

        while (true) {
            // 显示当前状态
            if (currentFolder != null) {
                System.out.printf("已打开文件夹: %s, 共检测到 %d 个.mcfunction成书文件，共 %d 页\n",
                        currentFolder.getAbsolutePath(), allBooks.size(), (int) Math.ceil(allBooks.size() / (double) PAGE_SIZE));
            }

            System.out.println("1. 打开含.mcfunction的成书文件夹");
            System.out.println("2. 数据库预处理（自动去重）");
            System.out.println("输入 author [作者名] 搜索该作者的书籍");
            System.out.println("输入 authorlist 显示所有作者列表");
            System.out.println("输入 authorpage [页码] 查看对应页的作者列表");
            System.out.println("输入 page [页码] 查看对应页的书籍列表");
            System.out.println("输入 title [书名关键词] 搜索书籍");
            System.out.println("输入 del [文件名] 删除书籍");
            System.out.println("输入 add 创建新书籍");
            System.out.println("输入 open [文件名] 浏览书籍");
            System.out.println("输入 exit 返回至主菜单");
            System.out.println("========================================");
            System.out.print("请输入命令: ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("返回主菜单。");
                break;
            }

            // 处理数字命令
            if (input.equals("1")) {
                openFolder();
                continue;
            }
            if (input.equals("2")) {
                if (currentFolder == null) {
                    System.out.println("请先打开文件夹。");
                    continue;
                }
                if (allBooks.isEmpty()) {
                    System.out.println("当前没有已加载的书籍数据。");
                    continue;
                }
                Bookprelib.runPreprocess(allBooks, booksByAuthor);  // 执行去重
                preprocessDatabase();                               // 刷新列表
                continue;
            }

            // 处理 authorlist 命令
            if (input.equalsIgnoreCase("authorlist")) {
                if (currentFolder == null) {
                    System.out.println("请先打开文件夹。");
                    continue;
                }
                handleAuthorListCommand();
                continue;
            }

            // 处理 authorpage 命令
            if (input.toLowerCase().startsWith("authorpage ")) {
                if (currentFolder == null) {
                    System.out.println("请先打开文件夹。");
                    continue;
                }
                String pageStr = input.substring(11).trim();  // "authorpage " 长度为11
                handleAuthorPageCommand(pageStr);
                continue;
            }

            // 处理 page 命令
            if (input.toLowerCase().startsWith("page ")) {
                if (currentFolder == null) {
                    System.out.println("请先打开文件夹。");
                    continue;
                }
                handlePageCommand(input);
                continue;
            }

            // 处理 author [作者名] 命令
            if (input.toLowerCase().startsWith("author ")) {
                if (currentFolder == null) {
                    System.out.println("请先打开文件夹。");
                    continue;
                }
                String authorName = input.substring(7).trim();
                handleAuthorCommand(authorName);
                continue;
            }

            // 处理 title [关键词] 命令
            if (input.toLowerCase().startsWith("title ")) {
                if (currentFolder == null) {
                    System.out.println("请先打开文件夹。");
                    continue;
                }
                String keyword = input.substring(6).trim();
                handleTitleCommand(keyword);
                continue;
            }

            // 处理 del 指令
            if (input.toLowerCase().startsWith("del ")) {
                if (currentFolder == null) {
                    System.out.println("请先打开文件夹。");
                    continue;
                }
                String fileName = input.substring(4).trim();
                Editlib.deleteBook(currentFolder, fileName, allBooks);
                preprocessDatabase();  // 刷新列表
                continue;
            }

            // 处理 add 指令
            if (input.equalsIgnoreCase("add")) {
                if (currentFolder == null) {
                    System.out.println("请先打开文件夹。");
                    continue;
                }
                Editlib.addBook(currentFolder);
                preprocessDatabase();
                continue;
            }
            // 处理 open 指令
            if (input.toLowerCase().startsWith("open ")) {
                if (currentFolder == null) {
                    System.out.println("请先打开文件夹。");
                    continue;
                }
                String fileName = input.substring(5).trim();
                Editlib.openBook(currentFolder, fileName, allBooks);
                continue;
            }
            System.out.println("无效命令，请重新输入。");
        }
    }

    private static void openFolder() {
        System.out.println("DEBUG: openFolder() 已执行，正在尝试弹出文件选择器...");
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择包含 .mcfunction 文件的文件夹");
        // 设置默认目录为程序根目录
        String userDir = System.getProperty("user.dir");
        File defaultDir = new File(userDir);
        if (defaultDir.exists() && defaultDir.isDirectory()) {
            chooser.setCurrentDirectory(defaultDir);
        } else if (currentFolder != null) {
            chooser.setCurrentDirectory(currentFolder);
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File folder = chooser.getSelectedFile();
        currentFolder = folder;
        scanFolder(folder);
    }

    /**
     * 扫描文件夹，解析所有 .mcfunction 文件
     */
    private static void scanFolder(File folder) {
        allBooks.clear();
        booksByAuthor.clear();

        File[] mcFiles = folder.listFiles(f -> f.getName().toLowerCase().endsWith(".mcfunction"));
        if (mcFiles == null || mcFiles.length == 0) {
            System.out.println("该文件夹中没有 .mcfunction 文件。");
            return;
        }

        System.out.println("正在扫描文件，请稍候...");
        int total = mcFiles.length;
        int processed = 0;
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
            processed++;
            if (processed % 50 == 0) {
                System.out.printf("已处理 %d/%d 个文件...\n", processed, total);
            }
        }

        // 对作者列表排序（字母优先，中文在后）
        List<String> sortedAuthors = new ArrayList<>(booksByAuthor.keySet());
        sortWithChineseLast(sortedAuthors);
        Map<String, List<BookEntry>> sortedMap = new LinkedHashMap<>();
        for (String author : sortedAuthors) {
            List<BookEntry> entries = booksByAuthor.get(author);
            entries.sort((a, b) -> compareWithChineseLast(a.title, b.title));
            sortedMap.put(author, entries);
        }
        booksByAuthor = sortedMap;

        // 整体书籍列表也按作者、书名排序
        allBooks.sort((a, b) -> {
            int cmp = compareWithChineseLast(a.author, b.author);
            if (cmp != 0) return cmp;
            return compareWithChineseLast(a.title, b.title);
        });

        System.out.printf("扫描完成。共找到 %d 个 .mcfunction 文件，解析出 %d 本书籍。\n", total, allBooks.size());
    }

    /**
     * 从 .mcfunction 文件中提取书名、作者、世代信息
     */
    private static BookEntry parseBookEntry(File file) throws IOException {
        String content = Files.readString(file.toPath());
        String title = extractField(content, "title");
        String author = extractField(content, "author");
        int generation = extractGeneration(content);
        String generationName = getGenerationName(generation);

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

        return new BookEntry(file.getName(), title, author, generationName, file, pages, wordCount);
    }

    private static String extractField(String content, String fieldName) {
        Pattern pattern = Pattern.compile(fieldName + "[\"']\\s*:\\s*[\"']([^\"']+)[\"']");
        Matcher m = pattern.matcher(content);
        if (m.find()) return m.group(1);
        // 尝试不带引号的版本
        pattern = Pattern.compile(fieldName + "\\s*:\\s*([^,}\\]]+)");
        m = pattern.matcher(content);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private static int extractGeneration(String content) {
        Pattern p = Pattern.compile("generation\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(content);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    private static String getGenerationName(int generation) {
        switch (generation) {
            case 0: return "原稿";
            case 1: return "原稿的副本";
            case 2: return "副本的副本";
            case 3: return "破烂不堪";
            default: return "未知";
        }
    }

    /**
     * 处理 page 命令，显示表格页
     */
    private static void handlePageCommand(String input) {
        int page;
        try {
            page = Integer.parseInt(input.substring(5).trim());
        } catch (NumberFormatException e) {
            System.out.println("页数格式错误，示例: page 1");
            return;
        }
        int totalPages = (int) Math.ceil(allBooks.size() / (double) PAGE_SIZE);
        if (page < 1 || page > totalPages) {
            System.out.printf("页数超出范围，有效页数为 1-%d\n", totalPages);
            return;
        }
        displayPage(page);
    }

    /**
     * 处理 author [作者名] 命令，显示该作者的书籍（内容去重合并后）
     */
    private static void handleAuthorCommand(String authorName) {
    if (authorName.isEmpty()) {
        System.out.println("作者名不能为空。示例: author Steve");
        return;
    }

    List<BookEntry> authorBooks = allBooks.stream()
            .filter(b -> b.author.toLowerCase().contains(authorName.toLowerCase()))
            .collect(Collectors.toList());

    if (authorBooks.isEmpty()) {
        System.out.println("未找到该作者的书籍。");
        return;
    }

    // 按完整内容分组（使用 Editlib 的公共方法）
    Map<String, List<BookEntry>> contentGroups = new LinkedHashMap<>();
    for (BookEntry book : authorBooks) {
        String content = Editlib.readBookContent(book.file);
        if (content == null) continue;
        contentGroups.computeIfAbsent(content, k -> new ArrayList<>()).add(book);
    }

    List<MergedBookEntry> mergedList = new ArrayList<>();
    for (Map.Entry<String, List<BookEntry>> entry : contentGroups.entrySet()) {
        List<BookEntry> group = entry.getValue();
        MergedBookEntry merged = new MergedBookEntry(group);
        mergedList.add(merged);
    }

    mergedList.sort((a, b) -> {
        int cmp = compareWithChineseLast(a.author, b.author);
        if (cmp != 0) return cmp;
        return compareWithChineseLast(a.representative.title, b.representative.title);
    });

    // 展示表格
    System.out.println("\n作者 [" + authorName + "] 的书籍（内容去重合并，共 " + mergedList.size() + " 个独立内容）：");
    System.out.println("┌────┬──────────────────────────────────────┬────────────────────┬──────────┬────────┬──────────┬────────┬────────┐");
    System.out.println("│序号│ 书名                                 │ 作者               │ 代表世代 │ 总副本 │ 原稿数量 │ 页数   │ 字数   │");
    System.out.println("├────┼──────────────────────────────────────┼────────────────────┼──────────┼────────┼──────────┼────────┼────────┤");
    int idx = 1;
    for (MergedBookEntry m : mergedList) {
        System.out.printf("│ %2d │ %-36s │ %-18s │ %-8s │ %6d │ %8d │ %6d │ %6d │\n",
                idx++,
                truncate(m.representative.title, 36),
                truncate(m.author, 18),
                truncate(m.representative.generation, 8),
                m.totalCopies,
                m.originalCount,
                m.pages,
                m.wordCount);
    }
    System.out.println("└────┴──────────────────────────────────────┴────────────────────┴──────────┴────────┴──────────┴────────┴────────┘");

    // 子交互循环
    Scanner scanner = new Scanner(System.in);
    while (true) {
        System.out.println("\n可用操作: open [序号], del [序号], translate [序号] [语言], back 返回主菜单");
        System.out.print("请输入指令: ");
        String input = scanner.nextLine().trim().toLowerCase();
        if (input.equals("back")) {
            break;
        } else if (input.startsWith("open ")) {
            try {
                int num = Integer.parseInt(input.substring(5).trim());
                if (num < 1 || num > mergedList.size()) {
                    System.out.println("序号超出范围。");
                    continue;
                }
                BookEntry selected = mergedList.get(num - 1).representative;
                Editlib.openBookByEntry(currentFolder, selected);
            } catch (NumberFormatException e) {
                System.out.println("格式错误，示例: open 1");
            }
        } else if (input.startsWith("del ")) {
            try {
                int num = Integer.parseInt(input.substring(4).trim());
                if (num < 1 || num > mergedList.size()) {
                    System.out.println("序号超出范围。");
                    continue;
                }
                BookEntry selected = mergedList.get(num - 1).representative;
                Editlib.deleteBookByEntry(currentFolder, selected, allBooks);
                preprocessDatabase();
                System.out.println("数据库已更新，返回主菜单。");
                break;
            } catch (NumberFormatException e) {
                System.out.println("格式错误，示例: del 1");
            }
        } else if (input.startsWith("translate ")) {
            String[] parts = input.substring(10).trim().split("\\s+");
            if (parts.length < 2) {
                System.out.println("格式错误，示例: translate 1 zh");
                continue;
            }
            try {
                int num = Integer.parseInt(parts[0]);
                String lang = parts[1];
                if (num < 1 || num > mergedList.size()) {
                    System.out.println("序号超出范围。");
                    continue;
                }
                BookEntry selected = mergedList.get(num - 1).representative;
                List<String> pages = McFunctionParser.extractPagesFromFile(selected.file.toPath());
                String fullText = String.join("\n\n", pages);
                System.out.println("正在翻译《" + selected.title + "》...");
                String translated = TranslateLib.translateAuto(fullText, lang);
                if (translated != null) {
                    System.out.println("\n========== 全书译文 ==========");
                    System.out.println(translated);
                    System.out.println("===============================");
                } else {
                    System.out.println("翻译失败。");
                }
            } catch (NumberFormatException e) {
                System.out.println("序号格式错误。");
            } catch (IOException e) {
                System.out.println("读取书籍失败: " + e.getMessage());
            }
        } else {
            System.out.println("无效指令。");
        }
    }
}

    /**
     * 读取书籍文件的完整内容字符串（用于比对）
     */
    private static String readBookContent(File file) {
        try {
            List<String> pages = McFunctionParser.extractPagesFromFile(file.toPath());
            return String.join("\n", pages);
        } catch (Exception e) {
            System.err.println("读取文件失败: " + file.getName() + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 合并后的书籍条目（用于展示去重合并后的信息）
     */
    static class MergedBookEntry {
        BookEntry representative;   // 代表书籍（按保留规则选取）
        String author;
        int totalCopies;          // 总副本数
        int originalCount;        // 原稿数量
        int pages;                // 页数（取自代表书籍）
        int wordCount;            // 字数（取自代表书籍）

        MergedBookEntry(List<BookEntry> group) {
            this.representative = selectRepresentative(group);
            this.author = representative.author;
            this.totalCopies = group.size();
            this.originalCount = (int) group.stream().filter(b -> "原稿".equals(b.generation)).count();
            this.pages = representative.pages;
            this.wordCount = representative.wordCount;
        }

        /**
         * 从一组内容完全相同的书籍中选择代表书籍。
         * 规则：优先“原稿”，其次文件名不带 _数字 后缀，最后选文件名最短/字母序。
         */
        private static BookEntry selectRepresentative(List<BookEntry> group) {
            // 优先原稿
            List<BookEntry> originals = new ArrayList<>();
            for (BookEntry b : group) {
                if ("原稿".equals(b.generation)) {
                    originals.add(b);
                }
            }
            List<BookEntry> candidates = originals.isEmpty() ? group : originals;

            // 其次文件名不带 _数字 后缀
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

            // 最终选文件名最短/字母序
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
    }

    /**
     * 处理 title [关键词] 命令，显示书名包含关键词的书籍（内容去重合并后）
     */
    private static void handleTitleCommand(String keyword) {
        if (keyword.isEmpty()) {
            System.out.println("书名关键词不能为空。示例: title 指南");
            return;
        }

        // 筛选书名包含关键词的所有书籍（原始列表）
        List<BookEntry> matchedBooks = allBooks.stream()
                .filter(b -> b.title.toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());

        if (matchedBooks.isEmpty()) {
            System.out.println("未找到匹配的书籍。");
            return;
        }

        // 按完整内容分组
        Map<String, List<BookEntry>> contentGroups = new LinkedHashMap<>();
        for (BookEntry book : matchedBooks) {
            String content = readBookContent(book.file);
            if (content == null) continue;
            contentGroups.computeIfAbsent(content, k -> new ArrayList<>()).add(book);
        }

        // 构建合并后的统计条目列表
        List<MergedBookEntry> mergedList = new ArrayList<>();
        for (Map.Entry<String, List<BookEntry>> entry : contentGroups.entrySet()) {
            List<BookEntry> group = entry.getValue();
            MergedBookEntry merged = new MergedBookEntry(group);
            mergedList.add(merged);
        }

        // 按作者、书名排序（与整体排序规则一致）
        mergedList.sort((a, b) -> {
            int cmp = compareWithChineseLast(a.author, b.author);
            if (cmp != 0) return cmp;
            return compareWithChineseLast(a.representative.title, b.representative.title);
        });

        // 输出合并后的表格
        System.out.println("\n书名包含 [" + keyword + "] 的书籍（内容去重合并，共 " + mergedList.size() + " 个独立内容）：");
        System.out.println("┌────────────────────┬──────────────────────────────────────┬──────────┬────────┬──────────┬────────┬────────┐");
        System.out.println("│ 作者               │ 书名                                 │ 代表世代 │ 总副本 │ 原稿数量 │ 页数   │ 字数   │");
        System.out.println("├────────────────────┼──────────────────────────────────────┼──────────┼────────┼──────────┼────────┼────────┤");
        for (MergedBookEntry m : mergedList) {
            System.out.printf("│ %-18s │ %-36s │ %-8s │ %6d │ %8d │ %6d │ %6d │\n",
                    truncate(m.author, 18),
                    truncate(m.representative.title, 36),
                    truncate(m.representative.generation, 8),
                    m.totalCopies,
                    m.originalCount,
                    m.pages,
                    m.wordCount);
        }
        System.out.println("└────────────────────┴──────────────────────────────────────┴──────────┴────────┴──────────┴────────┴────────┘");
    }

    /**
     * 显示所有作者列表，分页展示，每页最多 50 个作者
     */
    private static void handleAuthorListCommand() {
        if (booksByAuthor.isEmpty()) {
            System.out.println("暂无作者信息。");
            return;
        }

        // 获取已排序的作者列表（booksByAuthor 的 keySet 已排序）
        List<String> authors = new ArrayList<>(booksByAuthor.keySet());
        int totalAuthors = authors.size();
        int totalPages = (int) Math.ceil(totalAuthors / (double) PAGE_SIZE);

        System.out.printf("\n作者列表（共 %d 位作者，%d 页）\n", totalAuthors, totalPages);
        System.out.println("输入 'authorpage [页码]' 查看指定页，或直接回车查看第 1 页：");
        System.out.print("请输入页码: ");
        Scanner scanner = new Scanner(System.in);
        String pageInput = scanner.nextLine().trim();

        int page = 1;
        if (!pageInput.isEmpty()) {
            try {
                page = Integer.parseInt(pageInput);
            } catch (NumberFormatException e) {
                System.out.println("页码格式错误，将显示第 1 页。");
                page = 1;
            }
        }

        if (page < 1 || page > totalPages) {
            System.out.printf("页码超出范围，有效页数为 1-%d，将显示第 1 页。\n", totalPages);
            page = 1;
        }

        displayAuthorPage(page, authors, totalPages);
    }

    private static void handleAuthorPageCommand(String pageStr) {
        if (booksByAuthor.isEmpty()) {
            System.out.println("暂无作者信息。");
            return;
        }
        int page;
        try {
            page = Integer.parseInt(pageStr);
        } catch (NumberFormatException e) {
            System.out.println("页码格式错误。");
            return;
        }
        List<String> authors = new ArrayList<>(booksByAuthor.keySet());
        int totalPages = (int) Math.ceil(authors.size() / (double) PAGE_SIZE);
        if (page < 1 || page > totalPages) {
            System.out.printf("页码超出范围，有效页数为 1-%d。\n", totalPages);
            return;
        }
        displayAuthorPage(page, authors, totalPages);
    }

    /**
     * 显示指定页的作者列表
     */
    private static void displayAuthorPage(int page, List<String> authors, int totalPages) {
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, authors.size());

        System.out.println("\n┌──────────────────────────────────────────────┐");
        System.out.printf("│ 第 %d 页 / 共 %d 页 (作者列表)              │\n", page, totalPages);
        System.out.println("├────────────────────┬─────────────────────────┤");
        System.out.println("│ 作者               │ 书籍数量                │");
        System.out.println("├────────────────────┼─────────────────────────┤");
        for (int i = start; i < end; i++) {
            String author = authors.get(i);
            int count = booksByAuthor.get(author).size();
            System.out.printf("│ %-18s │ %-23d │\n", truncate(author, 18), count);
        }
        System.out.println("└────────────────────┴─────────────────────────┘");
    }

    /**
     * 以表格形式显示书籍列表
     */
    private static void displayBookTable(List<BookEntry> books) {
        System.out.println("┌────────────┬──────────────────────────────────────┬────────────────────┬──────┐");
        System.out.println("│ 序号       │ 书名                                 │ 作者               │ 类型 │");
        System.out.println("├────────────┼──────────────────────────────────────┼────────────────────┼──────┤");
        int idx = 1;
        for (BookEntry b : books) {
            System.out.printf("│ %-10d │ %-36s │ %-18s │ %-4s │\n",
                    idx++,
                    truncate(b.title, 36),
                    truncate(b.author, 18),
                    b.generation);
        }
        System.out.println("└────────────┴──────────────────────────────────────┴────────────────────┴──────┘");
    }

    private static void displayPage(int page) {
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allBooks.size());
        System.out.println("\n┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.printf("│ 第 %d 页 / 共 %d 页                                                          │\n", page, (int) Math.ceil(allBooks.size() / (double) PAGE_SIZE));
        System.out.println("├────────────┬──────────────────────────────────────┬────────────────────┬──────┤");
        System.out.println("│ 序号       │ 书名                                 │ 作者               │ 类型 │");
        System.out.println("├────────────┼──────────────────────────────────────┼────────────────────┼──────┤");
        for (int i = start; i < end; i++) {
            BookEntry book = allBooks.get(i);
            System.out.printf("│ %-10d │ %-36s │ %-18s │ %-4s │\n",
                    i + 1,
                    truncate(book.title, 36),
                    truncate(book.author, 18),
                    book.generation);
        }
        System.out.println("└────────────┴──────────────────────────────────────┴────────────────────┴──────┘");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    private static void preprocessDatabase() {
        if (currentFolder == null) {
            System.out.println("请先打开文件夹。");
            return;
        }
        System.out.println("正在刷新文件列表...");
        scanFolder(currentFolder);
        System.out.println("列表已更新。");
    }

    // ---------- 排序工具方法 ----------
    private static void sortWithChineseLast(List<String> list) {
        list.sort((a, b) -> compareWithChineseLast(a, b));
    }

    private static int compareWithChineseLast(String s1, String s2) {
        boolean c1 = containsChinese(s1);
        boolean c2 = containsChinese(s2);
        if (c1 && !c2) return 1;
        if (!c1 && c2) return -1;
        return Collator.getInstance(Locale.CHINA).compare(s1, s2);
    }

    private static boolean containsChinese(String s) {
        return s.matches(".*[\\u4e00-\\u9fa5].*");
    }

    // ---------- 内部数据类 ----------
    static class BookEntry {
        String fileName;
        String title;
        String author;
        String generation;
        int pages;
        int wordCount;
        File file;

        BookEntry(String fileName, String title, String author, String generation, File file, int pages, int wordCount) {
            this.fileName = fileName;
            this.title = title;
            this.author = author;
            this.generation = generation;
            this.file = file;
            this.pages = pages;
            this.wordCount = wordCount;
        }
    }
}