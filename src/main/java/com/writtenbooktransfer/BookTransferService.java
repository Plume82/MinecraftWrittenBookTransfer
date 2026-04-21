package com.writtenbooktransfer;

import com.booktypesetting.BookConfig;
import com.booktypesetting.TextFormatter;
import com.common.McFunctionParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    GlobalKeyListener.forceCleanup();
    callback.onStatus("准备传输，共 " + pages.size() + " 页。\n请将鼠标悬停在游戏内书本的翻页按钮上，然后按下 Ctrl 键开始...");

    InputSimulator simulator = null;
    GlobalKeyListener listener = null;

    try {
        simulator = new InputSimulator(offsetX, offsetY, 100);
        // 1. 注册全局钩子
        GlobalKeyListener.register();
        // 2. 创建监听器并添加到全局屏幕
        listener = new GlobalKeyListener();
        listener.addToGlobalScreen();
        // 3. 短暂延时确保钩子就绪（可选）
        Thread.sleep(50);

        callback.onStatus("等待 Ctrl 键按下...");
        // 4. 阻塞等待 Ctrl
        listener.waitForCtrl();

        // ... 后续传输逻辑不变

        // 后续传输逻辑保持不变...
        Point originalPos = simulator.getCurrentMousePosition();
        // ...
    } catch (Exception e) {
        callback.onError("传输过程发生错误：" + e.getMessage());
        e.printStackTrace();
    } finally {
        if (listener != null) listener.removeFromGlobalScreen();
        GlobalKeyListener.unregister();
        GlobalKeyListener.clearEscapeFlag();
        callback.onFinished();
    }
}

    public interface TransferCallback {
        void onStatus(String message);
        void onPageStart(int current, int total, String content);
        void onPageComplete(int current);
        void onError(String error);
        void onFinished();
    }
}