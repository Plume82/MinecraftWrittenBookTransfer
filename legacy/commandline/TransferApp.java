package com.writtenbooktransfer;

import com.common.McFunctionParser;
import com.booktypesetting.BookConfig;
import com.booktypesetting.TextFormatter;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * writtenbooktransfer 模块独立入口
 * 负责选择 .mcfunction 或 .txt 文件，并自动输入到 Minecraft 游戏中
 */
public class TransferApp {

    private static Path selectedFilePath = null;
    private static List<String> originalPages = new ArrayList<>();
    private static boolean isTextFile = false;  // 标记当前加载的是否为 .txt 文件

    private static int linesPerPage = 14;
    private static double maxLineWidth = 57.0;

    private static int offsetX = -50;
    private static int offsetY = -50;
    private static int pageLimit = 0;

    public static void main(String[] args) {
        if (args.length > 0) {
            selectedFilePath = Paths.get(args[0]);
            if (Files.exists(selectedFilePath)) {
                loadFile(selectedFilePath);
                if (!originalPages.isEmpty()) {
                    startTransfer();
                }
            } else {
                System.err.println("文件不存在: " + args[0]);
            }
            return;
        }
        showTransferMenu();
    }

    /**
     * 根据文件扩展名加载文件内容到 originalPages
     */
    private static void loadFile(Path file) {
        selectedFilePath = file;
        String fileName = file.toString().toLowerCase();

        try {
            if (fileName.endsWith(".mcfunction")) {
                isTextFile = false;
                originalPages = McFunctionParser.extractPagesFromFile(file);
                System.out.println("成功加载 " + originalPages.size() + " 页内容。");
            } else if (fileName.endsWith(".txt")) {
                isTextFile = true;
                String content = Files.readString(file);

                // 询问是否应用默认段落编排
                Scanner scanner = new Scanner(System.in);
                System.out.print("是否对文本进行默认段落编排？(段落之间空一行，首尾不空行) [Y/n]: ");
                String ans = scanner.nextLine().trim().toLowerCase();
                if (ans.isEmpty() || ans.equals("y") || ans.equals("yes")) {
                    content = applyDefaultParagraphFormatting(content);
                    System.out.println("已应用默认段落编排。");
                }

                BookConfig config = new BookConfig(linesPerPage, maxLineWidth, null, null);
                TextFormatter formatter = new TextFormatter(config);
                List<String> lines = formatter.splitIntoLines(content);
                originalPages = formatter.formatPages(lines);
                System.out.println("成功加载文本，已按当前排版配置分为 " + originalPages.size() + " 页。");
            } else {
                System.err.println("不支持的文件类型，请选择 .mcfunction 或 .txt 文件。");
                originalPages = new ArrayList<>();
            }
        } catch (IOException e) {
            System.err.println("读取文件失败：" + e.getMessage());
            originalPages = new ArrayList<>();
        }
    }

    private static void showTransferMenu() {
    Scanner scanner = new Scanner(System.in);
    System.out.println("\n✨ 输入 'tutorial' 或 'help' 查看使用教程 ✨");

    while (true) {
        System.out.println("\n--- .mcfunction导入Minecraft模块 ---");
        System.out.println("当前选中文件: " + (selectedFilePath != null ? selectedFilePath.getFileName() : "无"));
        System.out.println("1. 选择 .mcfunction 文件（若无.mcfunction文件，请查看使用教程）");
        System.out.println("2. 选择 .txt 文本文件（功能有限，不建议使用）");
        System.out.println("3. 开始传输");
        System.out.println("4. 设置传输参数（调试选项，一般无需使用）");
        if (!isTextFile && !originalPages.isEmpty()) {
            System.out.println("5. 重新排版当前内容（使用成书编辑器生成的必选，使用存档导出功能的请不要选择）");
        }
        System.out.println("0. 返回主菜单");
        System.out.print("请选择: ");

        String choice = scanner.nextLine().trim();
        
        // 🆕 处理教程命令
        if (choice.equalsIgnoreCase("tutorial") || choice.equalsIgnoreCase("help")) {
            showTutorial();
            continue;
        }

        switch (choice) {
            case "1":
                selectMcFunctionFile();
                break;
            case "2":
                selectTextFile();
                break;
            case "3":
                if (selectedFilePath == null || originalPages.isEmpty()) {
                    System.out.println("请先选择文件！");
                } else {
                    startTransfer();
                }
                break;
            case "4":
                showSettingsMenu();
                break;
            case "5":
                if (!isTextFile && !originalPages.isEmpty()) {
                    reformatCurrentContent();
                } else {
                    System.out.println("无效选项");
                }
                break;
            case "0":
                return;
            default:
                System.out.println("无效选项");
        }
    }
}

/**
 * 显示传输模块的使用教程（小白友好版）
 */
private static void showTutorial() {
    System.out.println("\n========================================");
    System.out.println("   .mcfunction导入Minecraft模块 · 使用教程");
    System.out.println("========================================");
    System.out.println("本模块能将书本内容自动输入到 Minecraft 游戏中。");
    System.out.println();
    System.out.println("【如何使用成书编辑器（written_book_editor）获取.mcfunction文件】");
    System.out.println("  1. 在成书编辑器（written_book_editor）中打开 .txt 文件，");
    System.out.println("     勾选“编辑单页”后，对文本进行排版编辑。");
    System.out.println();
    System.out.println("  2. 完成编辑后，点击菜单栏 文件 → 导出，");
    System.out.println("     文件类型选择“mc函数文件”，");
    System.out.println("     MC版本选择“>=1.21”，");
    System.out.println("     导出路径设置为本程序的根目录。");
    System.out.println();
    System.out.println("  3. 将生成的 .mcfunction 文件转移至本程序根目录下，");
    System.out.println("     即可被“书本自动传输”模块直接加载。");
    System.out.println();
    System.out.println("【文件类型说明】");
    System.out.println("  • .mcfunction 文件：已包含分页信息的命令文件，加载后直接按原样传输。");
    System.out.println("  • .txt 纯文本文件：系统会自动按当前排版参数（每页行数、行宽）进行分行分页。");
    System.out.println("    加载 .txt 时会询问是否应用“默认段落编排”（段落间空一行，首尾不空行）。");
    System.out.println();
    System.out.println("【菜单功能详解】");
    System.out.println("  1. 选择 .mcfunction 文件");
    System.out.println("     - 可选「打开对话框」或「自动扫描根目录」");
    System.out.println("  2. 选择 .txt 文本文件");
    System.out.println("     - 同上，加载后可立即查看分页结果");
    System.out.println("  3. 开始传输");
    System.out.println("     - 请确保：游戏窗口在最前，鼠标悬停在书本的「下一页」箭头上");
    System.out.println("     - 按下 Ctrl 后开始自动粘贴，按 ESC 随时中止");
    System.out.println("  4. 设置传输参数（一般不需调整）");
    System.out.println("     - 每页行数（默认14）：控制一页最多显示多少行");
    System.out.println("     - 最大行宽（默认57.0）：控制一行最多多宽（像素）");
    System.out.println("     - 鼠标偏移：调整点击输入框的位置（通常无需修改）");
    System.out.println("     - 页数限制：每连续输入多少页后暂停一次（0=不限）");
    System.out.println("     - 重新加载当前文件：修改参数后刷新分页结果");
    System.out.println("  5. 重新排版当前内容（仅加载 .mcfunction 时出现）");
    System.out.println("     - 将原有分页打散，按当前参数重新分行分页");
    System.out.println("     - 过程中会智能识别章节标题，并询问你是否要另起一页");
    System.out.println("     - 你也可以添加自定义分页标记（如罗马数字、特殊符号）");
    System.out.println();
    System.out.println("【小技巧】");
    System.out.println("  • 传输过程中终端会打印每一页的内容，方便核对。");
    System.out.println("  • 如果发现排版不对，先按 ESC 中止，再选「5.重新排版」调整。");
    System.out.println("  • 鼠标偏移一般无需改动，除非点击输入框位置不准。");
    System.out.println("  • 页数限制功能适合长篇小说分批输入，防止误操作。");
    System.out.println();
    System.out.println("【常见问题】");
    System.out.println("  Q: 粘贴后文字乱码？");
    System.out.println("  A: 检查游戏内输入法是否已关闭。");
    System.out.println("  Q: 第一页总是漏掉？");
    System.out.println("  A: 启动时确保鼠标完全静止放在翻页按钮上，等待1秒再按 Ctrl。");
    System.out.println("========================================\n");
}

    // ---------- 选择 .mcfunction 文件 ----------
    private static void selectMcFunctionFile() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n--- 选择 .mcfunction 文件 ---");
        System.out.println("o - 打开文件选择对话框");
        System.out.println("a - 自动扫描当前目录");
        System.out.print("请选择 (o/a): ");
        String cmd = scanner.nextLine().trim().toLowerCase();

        Path file = null;
        if (cmd.equals("o")) {
            file = openFileChooser("mcfunction", "Minecraft Function 文件");
        } else if (cmd.equals("a")) {
            file = autoSelectFromCurrentDir(".mcfunction");
        } else {
            System.out.println("无效选项。");
            return;
        }

        if (file != null) {
            loadFile(file);  // 统一使用 loadFile 方法
        }
    }

    // ---------- 选择 .txt 文本文件 ----------
    private static void selectTextFile() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n--- 选择 .txt 文本文件 ---");
        System.out.println("o - 打开文件选择对话框");
        System.out.println("a - 自动扫描当前目录");
        System.out.print("请选择 (o/a): ");
        String cmd = scanner.nextLine().trim().toLowerCase();

        Path file = null;
        if (cmd.equals("o")) {
            file = openFileChooser("txt", "文本文件");
        } else if (cmd.equals("a")) {
            file = autoSelectFromCurrentDir(".txt");
        } else {
            System.out.println("无效选项。");
            return;
        }

        if (file != null) {
            loadFile(file);  // 统一使用 loadFile 方法
        }
    }

    private static String applyDefaultParagraphFormatting(String text) {
    String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
    // 将连续3个及以上换行压缩为两个换行（一个空行）
    String compressed = normalized.replaceAll("\\n{3,}", "\n\n");
    // 去除首尾空行
    String trimmed = compressed.replaceAll("^\\n+", "").replaceAll("\\n+$", "");
    // 强制将单个换行符扩展为两个换行符（段落间空一行）
    return trimmed.replaceAll("(?<!\n)\n(?!\n)", "\n\n");
}

    // ---------- 通用文件选择器 ----------
    private static Path openFileChooser(String extension, String description) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("选择 " + description + " 文件");
        chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().toPath();
        }
        return null;
    }

    private static Path autoSelectFromCurrentDir(String extension) {
        try {
            Path baseDir = Paths.get("").toAbsolutePath();
            List<Path> files = Files.list(baseDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(extension))
                    .collect(Collectors.toList());
            if (files.isEmpty()) {
                System.out.println("当前目录下没有任何 " + extension + " 文件！");
                return null;
            }
            if (files.size() == 1) return files.get(0);
            System.out.println("\n发现多个 " + extension + " 文件：");
            for (int i = 0; i < files.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + files.get(i).getFileName());
            }
            Scanner sc = new Scanner(System.in);
            while (true) {
                System.out.print("请输入序号: ");
                try {
                    int idx = Integer.parseInt(sc.nextLine()) - 1;
                    if (idx >= 0 && idx < files.size()) return files.get(idx);
                } catch (NumberFormatException ignored) {}
                System.out.println("输入无效。");
            }
        } catch (IOException e) {
            System.err.println("扫描目录出错：" + e.getMessage());
            return null;
        }
    }

    // 重新加载当前文件（用于排版参数改变后）
    private static void reloadCurrentFile() {
        if (selectedFilePath == null) return;
        try {
            if (isTextFile) {
                String content = Files.readString(selectedFilePath);
                BookConfig config = new BookConfig(linesPerPage, maxLineWidth, null, null);
                TextFormatter formatter = new TextFormatter(config);
                List<String> lines = formatter.splitIntoLines(content);
                originalPages = formatter.formatPages(lines);
                System.out.println("已按新参数重新分页，共 " + originalPages.size() + " 页。");
            } else {
                originalPages = McFunctionParser.extractPagesFromFile(selectedFilePath);
                System.out.println("已重新加载文件，共 " + originalPages.size() + " 页。");
            }
        } catch (IOException e) {
            System.err.println("重新加载失败：" + e.getMessage());
        }
    }

    private static void reformatCurrentContent() {
    if (originalPages.isEmpty()) {
        System.out.println("没有可排版的内容。");
        return;
    }
    System.out.println("正在准备重新排版，当前参数：每页 " + linesPerPage + " 行，最大行宽 " + maxLineWidth);

    int total = originalPages.size();
    List<String> pages = new ArrayList<>(originalPages);
    Scanner scanner = new Scanner(System.in);

    java.util.function.Function<String, Integer> countTrailing = s -> {
        int c = 0;
        for (int i = s.length() - 1; i >= 0 && s.charAt(i) == '\n'; i--) c++;
        return c;
    };
    java.util.function.Function<String, Integer> countLeading = s -> {
        int c = 0;
        for (int i = 0; i < s.length() && s.charAt(i) == '\n'; i++) c++;
        return c;
    };

    // ===== 阶段1：自动合并无换行页面 =====
    boolean[] dropSeparator = new boolean[total - 1];
    for (int i = 0; i < total - 1; i++) {
        int trailing = countTrailing.apply(pages.get(i));
        int leading = countLeading.apply(pages.get(i + 1));
        if (trailing + leading == 0) {
            dropSeparator[i] = true;
            System.out.println("第 " + (i + 1) + " 页与第 " + (i + 2) + " 页之间无空行，已自动合并。");
        }
    }

    // ===== 阶段2：拼接全文 =====
    List<String> cleanedPages = new ArrayList<>();
    for (int i = 0; i < total; i++) {
        String page = pages.get(i);
        if (i > 0 && dropSeparator[i - 1] && page.startsWith("\n")) page = page.substring(1);
        if (i < total - 1 && dropSeparator[i] && page.endsWith("\n")) page = page.substring(0, page.length() - 1);
        cleanedPages.add(page);
    }

    StringBuilder fullTextBuilder = new StringBuilder();
    for (int i = 0; i < total; i++) {
        fullTextBuilder.append(cleanedPages.get(i));
        if (i < total - 1 && !dropSeparator[i]) fullTextBuilder.append("\n");
    }
    String fullText = fullTextBuilder.toString();

    fullText = fullText.replaceAll("^\\n+", "").replaceAll("\\n+$", "").replaceAll("\\n{3,}", "\n\n");
    System.out.println("已规范化全文换行。");

    // ===== 阶段3：拆分为实际行（考虑宽度） =====
    BookConfig config = new BookConfig(linesPerPage, maxLineWidth, null, null);
    TextFormatter formatter = new TextFormatter(config);
    List<String> actualLines = formatter.splitIntoLines(fullText);

    // ===== 阶段4：展示内置规则 + 收集用户自定义标记 =====
    System.out.println("\n系统内置的章节/序号识别规则：");
    System.out.println("  • 阿拉伯数字 + . 或空格 (如 1. / 2 )");
    System.out.println("  • 中文数字 + . 或 、 或空格 (如一. / 十二、)");
    System.out.println("  • 第x章 / 第xx章 (x为阿拉伯或中文数字)");
    System.out.println("----------------------------------------");

    List<String> customMarkers = new ArrayList<>();
    System.out.println("您可以添加额外的分页标记（如罗马数字、特殊符号开头等）。");
    System.out.println("请输入标记内容，每行一个，输入空行结束：");
    while (true) {
        System.out.print("> ");
        String marker = scanner.nextLine().trim();
        if (marker.isEmpty()) break;
        customMarkers.add(marker);
    }

    if (!customMarkers.isEmpty()) {
        System.out.println("已记录 " + customMarkers.size() + " 个自定义标记。");
    }

    // ===== 阶段5：扫描所有候选行（内置 + 自定义） =====
    List<Integer> candidateLineIndices = new ArrayList<>();
    for (int i = 0; i < actualLines.size(); i++) {
        String line = actualLines.get(i);
        if (isChapterOrNumberedStart(line) || matchesCustomMarker(line, customMarkers)) {
            candidateLineIndices.add(i);
        }
    }

    // ===== 阶段6：逐行询问用户是否强制分页 =====
    boolean[] manualPageBreakBeforeLine = new boolean[actualLines.size()];
    if (!candidateLineIndices.isEmpty()) {
        System.out.println("\n检测到 " + candidateLineIndices.size() + " 处可能的章节/分页位置。");
        for (int idx : candidateLineIndices) {
            String line = actualLines.get(idx);
            System.out.println("\n----------------------------------------");
            System.out.println("候选分页行内容：");
            String preview = line.length() > 70 ? line.substring(0, 70) + "..." : line;
            System.out.println(preview);
            System.out.println("----------------------------------------");
            System.out.print("是否从该行开始新的一页？(y/N): ");
            String ans = scanner.nextLine().trim().toLowerCase();
            if (ans.equals("y") || ans.equals("yes")) {
                manualPageBreakBeforeLine[idx] = true;
                System.out.println("已标记：从该行开始新页。");
            } else {
                System.out.println("保持连续排版。");
            }
        }
    } else {
        System.out.println("\n未检测到任何候选分页行。");
    }

    // ===== 阶段7：根据手动分页标记和行数限制生成最终页面 =====
    List<String> newPages = new ArrayList<>();
    List<String> currentPage = new ArrayList<>();
    int currentLineCount = 0;

    for (int i = 0; i < actualLines.size(); i++) {
        String line = actualLines.get(i);

        if (manualPageBreakBeforeLine[i] && !currentPage.isEmpty()) {
            newPages.add(String.join("\n", currentPage));
            currentPage.clear();
            currentLineCount = 0;
        }

        if (currentLineCount + 1 > linesPerPage) {
            newPages.add(String.join("\n", currentPage));
            currentPage.clear();
            currentLineCount = 0;
        }

        currentPage.add(line);
        currentLineCount++;
    }

    if (!currentPage.isEmpty()) {
        newPages.add(String.join("\n", currentPage));
    }

    originalPages = newPages;
    System.out.println("\n重新排版完成！新页数：" + originalPages.size() + " 页。");
    System.out.println("（可继续调整排版参数并再次选择此项以预览效果）");
}

/**
 * 检查行首是否匹配用户自定义的任意标记
 */
private static boolean matchesCustomMarker(String line, List<String> customMarkers) {
    if (line == null || line.isEmpty()) return false;
    String trimmed = line.trim();
    for (String marker : customMarkers) {
        if (trimmed.startsWith(marker)) {
            return true;
        }
    }
    return false;
}

private static boolean isChapterOrNumberedStart(String paragraph) {
    if (paragraph == null || paragraph.isEmpty()) return false;
    String trimmed = paragraph.trim();
    String pattern = "^(\\d+[\\.\\s]|" +
                     "[一二三四五六七八九十百千万]+[\\.\\s、]|" +
                     "第[\\d一二三四五六七八九十百千万]+章)";
    return trimmed.matches(pattern + ".*");
}

private static List<String> paginateWithManualBreaks(List<String> paragraphs, boolean[] pageBreakBefore, TextFormatter formatter) {
    List<String> resultPages = new ArrayList<>();
    List<String> currentPageLines = new ArrayList<>();
    int currentLineCount = 0;

    for (int i = 0; i < paragraphs.size(); i++) {
        String para = paragraphs.get(i);
        List<String> paraLines = formatter.splitIntoLines(para);
        System.out.println("📄 段落 " + (i + 1) + " 拆分后行数：" + paraLines.size());

        if (pageBreakBefore[i] && !currentPageLines.isEmpty()) {
            resultPages.add(String.join("\n", currentPageLines));
            System.out.println("   ↳ 用户要求在此分页，保存前一页（" + currentLineCount + " 行）。");
            currentPageLines.clear();
            currentLineCount = 0;
        }

        if (paraLines.size() > linesPerPage) {
            if (!currentPageLines.isEmpty()) {
                resultPages.add(String.join("\n", currentPageLines));
                System.out.println("   ↳ 当前页已保存（段落过长，先清空）。");
                currentPageLines.clear();
                currentLineCount = 0;
            }
            for (int j = 0; j < paraLines.size(); j += linesPerPage) {
                int end = Math.min(j + linesPerPage, paraLines.size());
                resultPages.add(String.join("\n", paraLines.subList(j, end)));
                System.out.println("   ↳ 段落超长，拆分为子页 " + (j / linesPerPage + 1));
            }
            continue;
        }

        if (currentLineCount + paraLines.size() > linesPerPage) {
            resultPages.add(String.join("\n", currentPageLines));
            System.out.println("   ↳ 空间不足（当前 " + currentLineCount + " + " + paraLines.size() + " > " + linesPerPage + "），保存当前页并新开一页。");
            currentPageLines = new ArrayList<>(paraLines);
            currentLineCount = paraLines.size();
        } else {
            currentPageLines.addAll(paraLines);
            currentLineCount += paraLines.size();
            System.out.println("   ↳ 追加到当前页，当前页行数：" + currentLineCount);
        }
    }

    if (!currentPageLines.isEmpty()) {
        resultPages.add(String.join("\n", currentPageLines));
        System.out.println("📑 保存最后一页（" + currentLineCount + " 行）。");
    }

    return resultPages;
}


    private static void showSettingsMenu() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n--- 传输参数设置 ---");
        System.out.println("当前每页行数: " + linesPerPage);
        System.out.println("当前最大行宽: " + maxLineWidth);
        System.out.println("当前偏移: X=" + offsetX + ", Y=" + offsetY);
        System.out.println("页数限制 (0=不限): " + pageLimit);
        System.out.println("1. 修改每页行数");
        System.out.println("2. 修改最大行宽");
        System.out.println("3. 修改鼠标偏移");
        System.out.println("4. 修改页数限制");
        System.out.println("5. 重新加载当前文件（应用新排版参数）");
        System.out.println("0. 返回");
        System.out.print("请选择: ");
        String opt = scanner.nextLine().trim();
        switch (opt) {
            case "1":
                System.out.print("请输入每页行数 (当前 " + linesPerPage + "): ");
                try {
                    linesPerPage = Integer.parseInt(scanner.nextLine());
                } catch (NumberFormatException e) {
                    System.out.println("输入无效，保持原值。");
                }
                break;
            case "2":
                System.out.print("请输入最大行宽 (当前 " + maxLineWidth + "): ");
                try {
                    maxLineWidth = Double.parseDouble(scanner.nextLine());
                } catch (NumberFormatException e) {
                    System.out.println("输入无效，保持原值。");
                }
                break;
            case "3":
                System.out.print("请输入 X 偏移 (当前 " + offsetX + "): ");
                try {
                    offsetX = Integer.parseInt(scanner.nextLine());
                } catch (NumberFormatException e) {
                    System.out.println("输入无效，保持原值。");
                }
                System.out.print("请输入 Y 偏移 (当前 " + offsetY + "): ");
                try {
                    offsetY = Integer.parseInt(scanner.nextLine());
                } catch (NumberFormatException e) {
                    System.out.println("输入无效，保持原值。");
                }
                break;
            case "4":
                System.out.print("请输入页数限制 (0=不限): ");
                try {
                    pageLimit = Integer.parseInt(scanner.nextLine());
                } catch (NumberFormatException e) {
                    System.out.println("输入无效，保持原值。");
                }
                break;
            case "5":
                reloadCurrentFile();
                break;
            case "0":
                return;
            default:
                System.out.println("无效选项");
        }
        System.out.println("设置已保存。");
    }

    private static void startTransfer() {
        
    if (originalPages.isEmpty()) {
        System.out.println("没有可传输的内容！");
        return;
    }
     // 强制清理可能残留的钩子（重要！）
    GlobalKeyListener.forceCleanup();
    
    System.out.println("共 " + originalPages.size() + " 页文本。");
    System.out.println("切换到游戏窗口，将鼠标悬停在翻页按钮上，然后按 Ctrl 开始...");
    System.out.println("传输过程中按 ESC 可中止并返回菜单。");

    InputSimulator simulator = null;
    GlobalKeyListener listener = null;

    try {
        simulator = new InputSimulator(offsetX, offsetY, 100);

        GlobalKeyListener.register();
        listener = new GlobalKeyListener();
        listener.addToGlobalScreen();

        listener.waitForCtrl();

        Point originalPos = simulator.getCurrentMousePosition();
        System.out.println("记录鼠标位置：" + originalPos.x + ", " + originalPos.y);

        GlobalKeyListener.clearEscapeFlag();

        int totalPages = originalPages.size();
        int countSinceResume = 0;

        for (int idx = 0; idx < totalPages; idx++) {
            if (GlobalKeyListener.isEscapePressed()) {
                System.out.println("\n[ESC] 用户请求中止传输。");
                break;
            }

            String pageText = originalPages.get(idx);

            // ========== 调试输出：打印即将粘贴的页面内容 ==========
            System.out.println("\n====第 " + (idx + 1) + " 页====");
            System.out.println(pageText);
            System.out.println("================");

            simulator.clickFocusArea(originalPos);

            if (GlobalKeyListener.isEscapePressed()) {
                System.out.println("\n[ESC] 用户请求中止传输。");
                break;
            }

            simulator.pasteText(pageText);
            System.out.println("已输入第 " + (idx + 1) + "/" + totalPages + " 页");

            countSinceResume++;

            if (idx < totalPages - 1) {
                if (GlobalKeyListener.isEscapePressed()) {
                    System.out.println("\n[ESC] 用户请求中止传输。");
                    break;
                }
                simulator.clickNextPageButton(originalPos);
            }

            if (pageLimit > 0 && countSinceResume >= pageLimit && idx < totalPages - 1) {
                System.out.println("已输入 " + countSinceResume + " 页（上限 " + pageLimit + "），暂停。按 Ctrl 继续，ESC 取消。");
                listener.waitForCtrl();
                countSinceResume = 0;
                GlobalKeyListener.clearEscapeFlag();
            }
        }

        if (GlobalKeyListener.isEscapePressed()) {
            System.out.println("传输已被用户中止。");
        } else {
            System.out.println("所有内容输入完成。");
        }

    } catch (Exception e) {
        System.err.println("传输过程发生错误：" + e.getMessage());
        e.printStackTrace();
    } finally {
        if (listener != null) {
            listener.removeFromGlobalScreen();
        }
        GlobalKeyListener.unregister();
        GlobalKeyListener.clearEscapeFlag();
    }

    System.out.println("已返回传输模块菜单。");
}
}
