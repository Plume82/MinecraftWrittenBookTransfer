package com.writtenbooktransfer;

import com.booktypesetting.BookConfig;
import com.booktypesetting.TextFormatter;
import com.common.McFunctionParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import java.awt.Point;

/**
 * 书籍传输核心服务（供 GUI 调用）
 */
public class BookTransferService {

    private List<String> pages;
    private boolean isMcFunction;
    private Path sourceFile;
    private int linesPerPage = 14;
    private double maxLineWidth = 57.0;
    private int offsetX = -50;
    private int offsetY = -50;
    private int pageLimit = 0;

    public BookTransferService() {}

    /**
     * 从 .mcfunction 文件加载页面
     */
    public void loadMcFunction(Path file) throws IOException {
        this.sourceFile = file;
        this.isMcFunction = true;
        this.pages = McFunctionParser.extractPagesFromFile(file);
    }

    /**
     * 从 .txt 文件加载内容，可选是否应用默认段落编排
     */
    public void loadTextFile(Path file, boolean applyDefaultFormat) throws IOException {
        this.sourceFile = file;
        this.isMcFunction = false;
        String content = Files.readString(file);
        if (applyDefaultFormat) {
            content = applyDefaultParagraphFormatting(content);
        }
        BookConfig config = new BookConfig(linesPerPage, maxLineWidth, null, null);
        TextFormatter formatter = new TextFormatter(config);
        List<String> lines = formatter.splitIntoLines(content);
        this.pages = formatter.formatPages(lines);
    }

    /**
     * 直接设置页面内容（用于从现有 BookEntry 加载）
     */
    public void setPagesFromContent(String fullText) {
        this.isMcFunction = false;
        BookConfig config = new BookConfig(linesPerPage, maxLineWidth, null, null);
        TextFormatter formatter = new TextFormatter(config);
        List<String> lines = formatter.splitIntoLines(fullText);
        this.pages = formatter.formatPages(lines);
    }

    /**
     * 对当前页面内容应用默认段落编排并重新分页
     */
    public void applyDefaultFormattingToCurrent() {
        if (pages == null || pages.isEmpty()) return;
        String fullText = String.join("\n", pages);
        String formatted = applyDefaultParagraphFormatting(fullText);
        BookConfig config = new BookConfig(linesPerPage, maxLineWidth, null, null);
        TextFormatter formatter = new TextFormatter(config);
        List<String> lines = formatter.splitIntoLines(formatted);
        this.pages = formatter.formatPages(lines);
    }

    private String applyDefaultParagraphFormatting(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        String compressed = normalized.replaceAll("\\n{3,}", "\n\n");
        String trimmed = compressed.replaceAll("^\\n+", "").replaceAll("\\n+$", "");
        return trimmed.replaceAll("(?<!\n)\n(?!\n)", "\n\n");
    }

    public List<String> getPages() {
        return pages;
    }

    public int getPageCount() {
        return pages == null ? 0 : pages.size();
    }

    public String getPage(int index) {
        if (pages == null || index < 0 || index >= pages.size()) return "";
        return pages.get(index);
    }

    // 排版参数 getter/setter
    public int getLinesPerPage() { return linesPerPage; }
    public void setLinesPerPage(int linesPerPage) { this.linesPerPage = linesPerPage; }
    public double getMaxLineWidth() { return maxLineWidth; }
    public void setMaxLineWidth(double maxLineWidth) { this.maxLineWidth = maxLineWidth; }
    public int getOffsetX() { return offsetX; }
    public void setOffsetX(int offsetX) { this.offsetX = offsetX; }
    public int getOffsetY() { return offsetY; }
    public void setOffsetY(int offsetY) { this.offsetY = offsetY; }
    public int getPageLimit() { return pageLimit; }
    public void setPageLimit(int pageLimit) { this.pageLimit = pageLimit; }

    /**
     * 启动传输过程（由调用方在后台线程执行）
     */
public void startTransfer(TransferCallback callback) {
    if (pages == null || pages.isEmpty()) {
        callback.onError("没有可传输的内容");
        return;
    }

    callback.onStatus("准备传输，共 " + pages.size() + " 页。\n请将鼠标悬停在游戏内书本的翻页按钮上，然后按下 Ctrl 键开始...");

    InputSimulator simulator = null;
    GlobalKeyListener listener = null;

    try {
        // 创建模拟器（可能抛出 AWTException）
        simulator = new InputSimulator(offsetX, offsetY, 100);

        // 添加监听器
        listener = new GlobalKeyListener();
        listener.addToGlobalScreen();
        callback.onStatus("等待 Ctrl 键按下...");
        listener.waitForCtrl();

        // 再次检查 pages 是否在等待期间被意外修改
        if (pages == null || pages.isEmpty()) {
            callback.onError("页面数据丢失");
            return;
        }

        // 获取鼠标位置
        Point originalPos = simulator.getCurrentMousePosition();

        callback.onStatus("开始传输，按 ESC 可中止");
        GlobalKeyListener.clearEscapeFlag();

        int totalPages = pages.size();
        int countSinceResume = 0;

        for (int idx = 0; idx < totalPages; idx++) {
            if (GlobalKeyListener.isEscapePressed()) {
                callback.onStatus("传输已被用户中止");
                break;
            }

            String pageText = pages.get(idx);
            callback.onPageStart(idx + 1, totalPages, pageText);

            simulator.clickFocusArea(originalPos);

            if (GlobalKeyListener.isEscapePressed()) {
                callback.onStatus("传输已被用户中止");
                break;
            }

            simulator.pasteText(pageText);
            callback.onPageComplete(idx + 1);
            countSinceResume++;

            if (idx < totalPages - 1) {
                if (GlobalKeyListener.isEscapePressed()) {
                    break;
                }
                simulator.clickNextPageButton(originalPos);
            }

            if (pageLimit > 0 && countSinceResume >= pageLimit && idx < totalPages - 1) {
                callback.onStatus("已输入 " + countSinceResume + " 页（上限 " + pageLimit + "），暂停。按 Ctrl 继续，ESC 取消。");
                listener.waitForCtrl();
                countSinceResume = 0;
                GlobalKeyListener.clearEscapeFlag();
            }
        }

        if (GlobalKeyListener.isEscapePressed()) {
            callback.onStatus("传输已被用户中止");
        } else {
            callback.onStatus("所有内容输入完成");
        }

    } catch (Exception e) {
        e.printStackTrace();
        callback.onError("传输过程发生错误：" + e.getMessage());
    } finally {
        if (listener != null) {
            listener.removeFromGlobalScreen();
        }
        // 注意：不再调用 GlobalKeyListener.unregister()，保留全局钩子
        GlobalKeyListener.clearEscapeFlag();
        callback.onFinished();
    }
}

// 添加到 BookTransferService 类中

/**
 * 智能交互式重新排版（GUI 版本）
 * 将命令行版的 reformatCurrentContent 逻辑移植过来，使用对话框交互。
 * 
 * @param parent 父窗口，用于对话框居中
 * @return 新的页面列表，如果用户取消操作则返回 null
 */
public List<String> interactiveReformat(java.awt.Component parent) {
    if (pages == null || pages.isEmpty()) {
        JOptionPane.showMessageDialog(parent, "没有可排版的内容。", "提示", JOptionPane.WARNING_MESSAGE);
        return null;
    }

    // 确认对话框
    int confirm = JOptionPane.showConfirmDialog(parent,
            "智能排版将重新分析全文，识别章节标题并询问是否另起一页。\n继续吗？",
            "智能排版", JOptionPane.OK_CANCEL_OPTION);
    if (confirm != JOptionPane.OK_OPTION) {
        return null;
    }

    // 复制当前页面列表，避免直接修改
    List<String> currentPages = new ArrayList<>(this.pages);
    int total = currentPages.size();

    // 辅助函数：统计末尾换行数
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
        int trailing = countTrailing.apply(currentPages.get(i));
        int leading = countLeading.apply(currentPages.get(i + 1));
        if (trailing + leading == 0) {
            dropSeparator[i] = true;
        }
    }

    // ===== 阶段2：拼接全文 =====
    List<String> cleanedPages = new ArrayList<>();
    for (int i = 0; i < total; i++) {
        String page = currentPages.get(i);
        if (i > 0 && dropSeparator[i - 1] && page.startsWith("\n")) {
            page = page.substring(1);
        }
        if (i < total - 1 && dropSeparator[i] && page.endsWith("\n")) {
            page = page.substring(0, page.length() - 1);
        }
        cleanedPages.add(page);
    }

    StringBuilder fullTextBuilder = new StringBuilder();
    for (int i = 0; i < total; i++) {
        fullTextBuilder.append(cleanedPages.get(i));
        if (i < total - 1 && !dropSeparator[i]) {
            fullTextBuilder.append("\n");
        }
    }
    String fullText = fullTextBuilder.toString();

    // 规范化换行
    fullText = fullText.replaceAll("^\\n+", "")
                       .replaceAll("\\n+$", "")
                       .replaceAll("\\n{3,}", "\n\n");

    // ===== 阶段3：拆分为实际行 =====
    BookConfig config = new BookConfig(linesPerPage, maxLineWidth, null, null);
    TextFormatter formatter = new TextFormatter(config);
    List<String> actualLines = formatter.splitIntoLines(fullText);

    // ===== 阶段4：收集自定义标记（简化版，弹出一个输入框） =====
    String customInput = JOptionPane.showInputDialog(parent,
            "可添加自定义分页标记（如罗马数字、特殊符号开头），多个用英文逗号分隔：\n"
            + "例如：I.,II.,【,§",
            "自定义分页标记");
    List<String> customMarkers = new ArrayList<>();
    if (customInput != null && !customInput.trim().isEmpty()) {
        for (String marker : customInput.split(",")) {
            String trimmed = marker.trim();
            if (!trimmed.isEmpty()) {
                customMarkers.add(trimmed);
            }
        }
    }

    // ===== 阶段5：扫描候选分页行 =====
    List<Integer> candidateLineIndices = new ArrayList<>();
    for (int i = 0; i < actualLines.size(); i++) {
        String line = actualLines.get(i);
        if (isChapterOrNumberedStart(line) || matchesCustomMarker(line, customMarkers)) {
            candidateLineIndices.add(i);
        }
    }

    // ===== 阶段6：逐个询问是否分页（图形化） =====
    boolean[] manualPageBreakBeforeLine = new boolean[actualLines.size()];
    if (!candidateLineIndices.isEmpty()) {
        String msg = "检测到 " + candidateLineIndices.size() + " 处可能的章节/分页位置。\n"
                   + "接下来将逐个显示候选行，请确认是否从该行开始新的一页。";
        JOptionPane.showMessageDialog(parent, msg, "智能分页", JOptionPane.INFORMATION_MESSAGE);

        for (int idx : candidateLineIndices) {
            String line = actualLines.get(idx);
            String preview = line.length() > 70 ? line.substring(0, 70) + "..." : line;
            int choice = JOptionPane.showConfirmDialog(parent,
                    "候选分页行内容：\n\"" + preview + "\"\n\n是否从该行开始新的一页？",
                    "确认分页 (" + (candidateLineIndices.indexOf(idx) + 1) + "/" + candidateLineIndices.size() + ")",
                    JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                manualPageBreakBeforeLine[idx] = true;
            }
        }
    } else {
        JOptionPane.showMessageDialog(parent, "未检测到任何候选分页行。", "智能分页", JOptionPane.INFORMATION_MESSAGE);
    }

    // ===== 阶段7：根据标记和行数限制生成最终页面 =====
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

    // 更新内部 pages
    this.pages = newPages;
    return newPages;
}

// 以下两个辅助方法从 TransferApp 移植
private boolean isChapterOrNumberedStart(String paragraph) {
    if (paragraph == null || paragraph.isEmpty()) return false;
    String trimmed = paragraph.trim();
    String pattern = "^(\\d+[\\.\\s]|" +
                     "[一二三四五六七八九十百千万]+[\\.\\s、]|" +
                     "第[\\d一二三四五六七八九十百千万]+章)";
    return trimmed.matches(pattern + ".*");
}

private boolean matchesCustomMarker(String line, List<String> customMarkers) {
    if (line == null || line.isEmpty()) return false;
    String trimmed = line.trim();
    for (String marker : customMarkers) {
        if (trimmed.startsWith(marker)) {
            return true;
        }
    }
    return false;
}
    public interface TransferCallback {
        void onStatus(String message);
        void onPageStart(int current, int total, String content);
        void onPageComplete(int current);
        void onError(String error);
        void onFinished();
    }
}