package com.bookextractor;


import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * 我的世界存档成书提取工具 (适配1.21.8) - 多线程版，支持暂停/进度保存/进度条
 * 从存档的region文件夹中提取所有成书(written_book)，支持掉落物、箱子、木桶、讲台等。
 * 为每本成书生成一个give命令的.mcfunction文件。

 */
public class MinecraftBookExtractor {

    // 线程控制
    private static ExecutorService executor;
    private static final Object pauseLock = new Object();
    private static volatile boolean paused = false;
    private static volatile boolean stopped = false;
    private static final AtomicBoolean processing = new AtomicBoolean(false);

    // 进度跟踪
    private static int totalPendingFiles = 0;
    private static final AtomicInteger completedFiles = new AtomicInteger(0);
    private static JProgressBar progressBar;
    private static JLabel progressLabel;

    // UI 组件引用（用于更新按钮状态）
    private static JButton reportButton;
    private static JTextField outputPathField; // 需要提升为类成员以便报告生成时获取路径
    private static JButton extractButton;
    private static JButton pauseButton;
    private static JButton resumeButton;
    private static JButton saveExitButton;
    private static JButton browseButton;
    private static JButton outputBrowseButton;
    private static JTextArea logArea;
    private static JFrame frame;
    

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Minecraft 成书提取工具 (多线程版)");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(750, 700);
            frame.setLocationRelativeTo(null);

            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 10, 10, 10);

            // 第一行：存档选择
            JLabel label = new JLabel("请选择Minecraft存档文件夹（包含 region 目录）");
            gbc.gridx = 0;
            gbc.gridy = 0;
            panel.add(label, gbc);

            JTextField pathField = new JTextField(35);
            setDefaultSavePath(pathField);
            gbc.gridx = 0;
            gbc.gridy = 1;
            panel.add(pathField, gbc);

            browseButton = new JButton("浏览...");
            gbc.gridx = 1;
            gbc.gridy = 1;
            panel.add(browseButton, gbc);

            // 第二行：输出目录选择
            JLabel outputLabel = new JLabel("输出目录（默认为程序根目录）");
            gbc.gridx = 0;
            gbc.gridy = 2;
            panel.add(outputLabel, gbc);

            outputPathField = new JTextField(35);
            setDefaultOutputPath(outputPathField);
            gbc.gridx = 0;
            gbc.gridy = 3;
            panel.add(outputPathField, gbc);

            outputBrowseButton = new JButton("浏览...");
            gbc.gridx = 1;
            gbc.gridy = 3;
            panel.add(outputBrowseButton, gbc);

            // 按钮面板
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

            extractButton = new JButton("开始提取");
            pauseButton = new JButton("暂停");
            resumeButton = new JButton("继续");
            saveExitButton = new JButton("保存并退出");

            pauseButton.setEnabled(false);
            resumeButton.setEnabled(false);
            saveExitButton.setEnabled(false);

            buttonPanel.add(extractButton);
            buttonPanel.add(pauseButton);
            buttonPanel.add(resumeButton);
            buttonPanel.add(saveExitButton);
            // 打印报告按钮
            reportButton = new JButton("打印报告");
            reportButton.setEnabled(true);
            buttonPanel.add(reportButton);

            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.gridwidth = 2;
            panel.add(buttonPanel, gbc);

            // 进度条区域
            JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
            progressLabel = new JLabel("就绪");
            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressPanel.add(progressLabel, BorderLayout.NORTH);
            progressPanel.add(progressBar, BorderLayout.CENTER);

            gbc.gridx = 0;
            gbc.gridy = 5;
            gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            panel.add(progressPanel, gbc);

            // 日志区域
            logArea = new JTextArea(12, 60);
            logArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(logArea);
            gbc.gridx = 0;
            gbc.gridy = 6;
            gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            panel.add(scrollPane, gbc);

            frame.add(panel);

            // 存档浏览按钮
            browseButton.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("选择存档文件夹");
                String currentPath = pathField.getText().trim();
                if (!currentPath.isEmpty()) {
                    File initialDir = new File(currentPath);
                    if (initialDir.exists() && initialDir.isDirectory()) {
                        chooser.setCurrentDirectory(initialDir);
                    }
                }
                if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    pathField.setText(chooser.getSelectedFile().getAbsolutePath());
                }
            });

            // 输出目录浏览按钮
            outputBrowseButton.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("选择输出目录");
                String currentPath = outputPathField.getText().trim();
                if (!currentPath.isEmpty()) {
                    File initialDir = new File(currentPath);
                    if (initialDir.exists() && initialDir.isDirectory()) {
                        chooser.setCurrentDirectory(initialDir);
                    }
                }
                if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    outputPathField.setText(chooser.getSelectedFile().getAbsolutePath());
                }
            });

            // 开始提取按钮
            extractButton.addActionListener(e -> {
                String savesPath = pathField.getText().trim();
                String outputPath = outputPathField.getText().trim();

                if (savesPath.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "请先选择存档文件夹", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (outputPath.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "请指定输出目录", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                File savesDir = new File(savesPath);
                File outputDir = new File(outputPath);

                if (!savesDir.exists() || !savesDir.isDirectory()) {
                    JOptionPane.showMessageDialog(frame, "所选存档路径不存在或不是文件夹", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (!outputDir.exists()) {
                    boolean created = outputDir.mkdirs();
                    if (!created) {
                        JOptionPane.showMessageDialog(frame, "无法创建输出目录", "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                // 重置状态
                paused = false;
                stopped = false;
                processing.set(true);
                completedFiles.set(0);

                // 更新按钮状态
                extractButton.setEnabled(false);
                pauseButton.setEnabled(true);
                resumeButton.setEnabled(false);
                saveExitButton.setEnabled(true);
                browseButton.setEnabled(false);
                outputBrowseButton.setEnabled(false);

                logArea.setText("");
                progressBar.setValue(0);
                progressLabel.setText("正在计算待处理文件...");

                // 启动处理线程
                new Thread(() -> {
                    try {
                        processSaveFolder(savesDir, outputDir);
                        logToUI("\n处理完成！\n");
                        SwingUtilities.invokeLater(() -> progressLabel.setText("完成"));
                    } catch (Exception ex) {
                        logToUI("\n发生错误：" + ex.getMessage() + "\n");
                        ex.printStackTrace();
                        SwingUtilities.invokeLater(() -> progressLabel.setText("错误"));
                    } finally {
                        processing.set(false);
                        SwingUtilities.invokeLater(() -> {
                            extractButton.setEnabled(true);
                            pauseButton.setEnabled(false);
                            resumeButton.setEnabled(false);
                            saveExitButton.setEnabled(false);
                            browseButton.setEnabled(true);
                            outputBrowseButton.setEnabled(true);
                        });
                        shutdownExecutor();
                    }
                }).start();
            });

            // 暂停按钮
            pauseButton.addActionListener(e -> {
                paused = true;
                pauseButton.setEnabled(false);
                resumeButton.setEnabled(true);
                logToUI("\n[暂停中... 请点击“继续”恢复处理]\n");
            });

            // 继续按钮
            resumeButton.addActionListener(e -> {
                synchronized (pauseLock) {
                    paused = false;
                    pauseLock.notifyAll();
                }
                pauseButton.setEnabled(true);
                resumeButton.setEnabled(false);
                logToUI("\n[继续处理...]\n");
            });

            // 保存并退出按钮
            saveExitButton.addActionListener(e -> {
                int choice = JOptionPane.showConfirmDialog(frame,
                        "确定要保存进度并退出吗？\n进度将保存至各存档目录下的 extract_progress.json 文件。",
                        "确认退出", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    stopped = true;
                    synchronized (pauseLock) {
                        pauseLock.notifyAll();
                    }
                    shutdownExecutor();
                    logToUI("\n[已保存进度，程序退出]\n");
                    javax.swing.Timer timer = new javax.swing.Timer(1000, evt -> System.exit(0));
                    timer.setRepeats(false);
                    timer.start();
                }
            });

                // 打印报告按钮监听器
            reportButton.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("选择成书输出文件夹（如 xxx_books_functions）");
                String currentPath = outputPathField.getText().trim();
                if (!currentPath.isEmpty()) {
                    File initialDir = new File(currentPath);
                    if (initialDir.exists() && initialDir.isDirectory()) {
                        chooser.setCurrentDirectory(initialDir);
                    }
                }
                if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    File selectedDir = chooser.getSelectedFile();
                    File reportFile = new File(selectedDir.getParent(), selectedDir.getName() + "_报告.docx");
                    JFileChooser saveChooser = new JFileChooser();
                    saveChooser.setSelectedFile(reportFile);
                    saveChooser.setDialogTitle("保存报告文件");
                    if (saveChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                        File outputFile = saveChooser.getSelectedFile();
                        new Thread(() -> {
                            try {
                                BookReportGenerator.generateReport(selectedDir, outputFile);
                                JOptionPane.showMessageDialog(frame, "报告生成成功！\n" + outputFile.getAbsolutePath());
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(frame, "报告生成失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                                ex.printStackTrace();
                            }
                        }).start();
                    }
                }
            });

            frame.setVisible(true);
        });
    }

    private static void shutdownExecutor() {
    if (executor != null && !executor.isShutdown()) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("线程池未能完全终止");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

    private static void setDefaultSavePath(JTextField pathField) {
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isEmpty()) {
            pathField.setText(userDir);
        } else {
            try {
                java.net.URL url = MinecraftBookExtractor.class.getProtectionDomain().getCodeSource().getLocation();
                String path = java.net.URLDecoder.decode(url.getPath(), "UTF-8");
                File jarFile = new File(path);
                File programDir = jarFile.isFile() ? jarFile.getParentFile() : jarFile;
                if (programDir != null && programDir.exists()) {
                    pathField.setText(programDir.getAbsolutePath());
                }
            } catch (Exception ignored) {}
        }
    }

    private static void setDefaultOutputPath(JTextField pathField) {
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isEmpty()) {
            pathField.setText(userDir);
        } else {
            try {
                java.net.URL url = MinecraftBookExtractor.class.getProtectionDomain().getCodeSource().getLocation();
                String path = java.net.URLDecoder.decode(url.getPath(), "UTF-8");
                File jarFile = new File(path);
                File programDir = jarFile.isFile() ? jarFile.getParentFile() : jarFile;
                if (programDir != null && programDir.exists()) {
                    pathField.setText(programDir.getAbsolutePath());
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * 线程安全的日志输出
     */
    private static void logToUI(String msg) {
        System.out.println(msg);
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> logArea.append(msg));
        }
    }

    /**
     * 更新进度条
     */
    private static void updateProgress(int completed, int total) {
        SwingUtilities.invokeLater(() -> {
            if (total > 0) {
                int percent = (int) ((completed * 100L) / total);
                progressBar.setValue(percent);
                progressLabel.setText(String.format("进度：%d / %d 文件 (%d%%)", completed, total, percent));
            } else {
                progressBar.setValue(0);
                progressLabel.setText("没有待处理文件");
            }
        });
    }

    /**
     * 处理存档文件夹，支持多线程、暂停、进度保存
     */
    /**
 * 处理单个存档文件夹，支持多线程、暂停、进度保存
 */
private static void processSaveFolder(File saveFolder, File outputBaseDir) throws Exception {
    // 检查 region 文件夹是否存在
    File regionFolder = new File(saveFolder, "region");
    if (!regionFolder.exists() || !regionFolder.isDirectory()) {
        logToUI("所选文件夹内未找到 region 目录，请确认这是一个有效的 Minecraft 存档。\n");
        return;
    }

    File[] mcaFiles = regionFolder.listFiles((dir, name) -> name.endsWith(".mca"));
    if (mcaFiles == null || mcaFiles.length == 0) {
        logToUI("region 文件夹中没有 .mca 文件。\n");
        return;
    }

    // 加载进度，过滤已完成文件
    ExtractionProgress progress = loadProgress(saveFolder);
    Set<String> completedSet = new HashSet<>(progress.completedMcas);

    List<File> pendingFiles = new ArrayList<>();
    for (File mcaFile : mcaFiles) {
        if (!completedSet.contains(mcaFile.getAbsolutePath())) {
            pendingFiles.add(mcaFile);
        }
    }

    totalPendingFiles = pendingFiles.size();
    completedFiles.set(0);

    SwingUtilities.invokeLater(() -> {
        if (totalPendingFiles == 0) {
            progressLabel.setText("所有文件均已处理完毕");
            progressBar.setValue(100);
        } else {
            progressLabel.setText("待处理文件总数：" + totalPendingFiles);
            progressBar.setValue(0);
        }
    });

    if (totalPendingFiles == 0) {
        logToUI("该存档的 .mca 文件均已处理完毕，无需提取。\n");
        return;
    }

    // 创建线程池
    int threads = 10;
    executor = Executors.newFixedThreadPool(threads);

    File outputDir = new File(outputBaseDir, saveFolder.getName() + "_books_functions");
    if (!outputDir.exists()) outputDir.mkdir();

    logToUI("正在处理存档：" + saveFolder.getName() + "\n");
    logToUI("  待处理文件数：" + pendingFiles.size() + "\n");

    int totalBooks = progress.totalBooksExtracted;

    // 提交任务
    List<Future<McaResult>> futures = new ArrayList<>();
    for (File mcaFile : pendingFiles) {
        if (stopped) break;
        Future<McaResult> future = executor.submit(() -> processMcaFile(mcaFile, outputDir, saveFolder));
        futures.add(future);
    }

    // 收集结果
    for (Future<McaResult> f : futures) {
        if (stopped) break;
        try {
            McaResult res = f.get();
            totalBooks += res.bookCount;
            progress.completedMcas.add(res.mcaPath);
            progress.totalBooksExtracted = totalBooks;
            saveProgress(progress, saveFolder);

            int completed = completedFiles.incrementAndGet();
            updateProgress(completed, totalPendingFiles);

            logToUI("  已完成：" + new File(res.mcaPath).getName() + " (本书：" + res.bookCount + ")\n");
        } catch (InterruptedException | ExecutionException e) {
            if (!stopped) {
                logToUI("  处理文件时出错: " + e.getMessage() + "\n");
            }
        }
    }

    logToUI("存档 " + saveFolder.getName() + " 处理完毕，共提取 " + totalBooks + " 本成书\n");

    executor.shutdown();
    if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
        executor.shutdownNow();
    }
    executor = null;
}

    // 进度数据结构
static class ExtractionProgress {
    Set<String> completedMcas = new HashSet<>();
    int totalBooksExtracted = 0;
}

// 辅助结果类
static class McaResult {
    String mcaPath;
    int bookCount;
    McaResult(String path, int count) { mcaPath = path; bookCount = count; }
}

private static void saveProgress(ExtractionProgress progress, File saveFolder) {
    File progressFile = new File(saveFolder, "extract_progress.properties");
    try (Writer writer = new FileWriter(progressFile)) {
        Properties props = new Properties();
        props.setProperty("totalBooksExtracted", String.valueOf(progress.totalBooksExtracted));
        props.setProperty("completedMcas", String.join(";", progress.completedMcas));
        props.store(writer, "Minecraft Book Extractor Progress");
    } catch (IOException e) {
        logToUI("警告：保存进度失败 - " + e.getMessage() + "\n");
    }
}

private static ExtractionProgress loadProgress(File saveFolder) {
    File progressFile = new File(saveFolder, "extract_progress.properties");
    if (!progressFile.exists()) return new ExtractionProgress();
    try (Reader reader = new FileReader(progressFile)) {
        Properties props = new Properties();
        props.load(reader);
        ExtractionProgress progress = new ExtractionProgress();
        progress.totalBooksExtracted = Integer.parseInt(props.getProperty("totalBooksExtracted", "0"));
        String mcas = props.getProperty("completedMcas", "");
        if (!mcas.isEmpty()) {
            progress.completedMcas = new HashSet<>(Arrays.asList(mcas.split(";")));
        }
        return progress;
    } catch (Exception e) {
        return new ExtractionProgress();
    }
}



private static List<WrittenBook> extractBooksFromChunk(NBTTag chunkTag, JTextArea logArea) {
    List<WrittenBook> books = new ArrayList<>();

    // 注意：这里直接使用传入的 chunkTag 进行解析，无需再读取文件头

    // 1. 解析实体（掉落物）
    List<NBTTag> entities = chunkTag.getList("Entities");
    if (entities != null) {
        for (NBTTag e : entities) {
            if (e instanceof NBTTag.CompoundTag) {
                books.addAll(extractBookFromEntity((NBTTag.CompoundTag) e, logArea));
            }
        }
    }

    // 2. 解析方块实体（容器、讲台等）
    List<NBTTag> blockEntities = chunkTag.getList("block_entities");
    if (blockEntities == null) blockEntities = chunkTag.getList("BlockEntities");
    if (blockEntities != null) {
        for (NBTTag be : blockEntities) {
            if (!(be instanceof NBTTag.CompoundTag)) continue;
            NBTTag.CompoundTag beComp = (NBTTag.CompoundTag) be;
            String id = beComp.getString("id");
            if (id == null) id = beComp.getString("Id");
            if (id == null) continue;

            String lowerId = id.toLowerCase();
            if (lowerId.contains("chest") || lowerId.contains("barrel")) {
                books.addAll(extractBooksFromContainer(beComp, logArea));
            } else if (lowerId.contains("lectern")) {
                books.addAll(extractBookFromLectern(beComp, logArea));
            }
        }
    }

    return books;
}


    /**
     * 处理单个 .mca 文件，支持暂停检查
     */
    private static McaResult processMcaFile(File mcaFile, File outputDir, File saveFolder) throws Exception {
        int bookCount = 0;
        List<WrittenBook> books = new ArrayList<>();

        // 暂停检查点
        checkPause();

        try (RandomAccessFile raf = new RandomAccessFile(mcaFile, "r")) {
            byte[] header = new byte[8192];
            raf.read(header);

            for (int i = 0; i < 1024; i++) {
                // 暂停检查点
                checkPause();

                int offset = ((header[i * 4] & 0xFF) << 16) | ((header[i * 4 + 1] & 0xFF) << 8) | (header[i * 4 + 2] & 0xFF);
                int sectorCount = header[i * 4 + 3] & 0xFF;
                if (offset == 0 || sectorCount == 0) continue;

                raf.seek(offset * 4096L);
                int length = raf.readInt();
                byte compressionType = raf.readByte();
                byte[] chunkData = new byte[length - 1];
                raf.readFully(chunkData);

                InputStream decompressedStream;
                if (compressionType == 1) {
                    decompressedStream = new GZIPInputStream(new ByteArrayInputStream(chunkData));
                } else if (compressionType == 2) {
                    decompressedStream = new InflaterInputStream(new ByteArrayInputStream(chunkData));
                } else if (compressionType == 3) {
                    decompressedStream = new ByteArrayInputStream(chunkData);
                } else {
                    throw new IOException("不支持的压缩类型: " + compressionType);
                }

                DataInputStream dis = new DataInputStream(decompressedStream);
                NBTTag chunkTag = NBTTag.readFromStream(dis);
                dis.close();

                books.addAll(extractBooksFromChunk(chunkTag, null));
            }
        }

        // 生成函数文件（线程安全写入）
        synchronized (outputDir) {
            for (WrittenBook book : books) {
                generateFunctionFile(book, outputDir);
                bookCount++;
            }
        }

        return new McaResult(mcaFile.getAbsolutePath(), bookCount);
    }

    /**
     * 暂停检查，若 paused 为 true 则等待，若 stopped 为 true 则抛出异常终止
     */
    private static void checkPause() throws InterruptedException {
        if (stopped) {
            throw new InterruptedException("任务被用户终止");
        }
        synchronized (pauseLock) {
            while (paused && !stopped) {
                pauseLock.wait();
            }
        }
        if (stopped) {
            throw new InterruptedException("任务被用户终止");
        }
    }


    // ========== 以下方法保持不变（extractBooksFromMCA、log、getGenerationName 等） ==========
    // 为节省篇幅，此处省略未变动的所有方法（与原始代码完全一致）

  private static List<WrittenBook> extractBooksFromMCA(File mcaFile, JTextArea logArea) throws Exception {
        List<WrittenBook> books = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(mcaFile, "r")) {
            byte[] header = new byte[8192];
            raf.read(header);

            log("正在处理文件: " + mcaFile.getName(), logArea);
            boolean rootPrinted = false;

            for (int i = 0; i < 1024; i++) {
                int offset = ((header[i * 4] & 0xFF) << 16) | ((header[i * 4 + 1] & 0xFF) << 8) | (header[i * 4 + 2] & 0xFF);
                int sectorCount = header[i * 4 + 3] & 0xFF;
                if (offset == 0 || sectorCount == 0) continue;

                raf.seek(offset * 4096L);
                int length = raf.readInt();
                byte compressionType = raf.readByte();
                byte[] chunkData = new byte[length - 1];
                raf.readFully(chunkData);

                InputStream decompressedStream;
                if (compressionType == 1) {
                    decompressedStream = new GZIPInputStream(new ByteArrayInputStream(chunkData));
                } else if (compressionType == 2) {
                    decompressedStream = new InflaterInputStream(new ByteArrayInputStream(chunkData));
                } else if (compressionType == 3) {
                    decompressedStream = new ByteArrayInputStream(chunkData);
                } else {
                    throw new IOException("不支持的压缩类型: " + compressionType);
                }

                DataInputStream dis = new DataInputStream(decompressedStream);
                NBTTag chunkTag = NBTTag.readFromStream(dis);
                dis.close();

                if (!rootPrinted && chunkTag instanceof NBTTag.CompoundTag) {
                    NBTTag.CompoundTag root = (NBTTag.CompoundTag) chunkTag;
                    log("  根标签键名: " + root.keySet(), logArea);
                    rootPrinted = true;
                }

                // 1. 解析实体（掉落物）
                List<NBTTag> entities = chunkTag.getList("Entities");
                if (entities != null) {
                    for (NBTTag e : entities) {
                        if (e instanceof NBTTag.CompoundTag) {
                            books.addAll(extractBookFromEntity((NBTTag.CompoundTag) e, logArea));
                        }
                    }
                }

                // 2. 解析方块实体（容器、讲台等）
                List<NBTTag> blockEntities = chunkTag.getList("block_entities");
                if (blockEntities == null) blockEntities = chunkTag.getList("BlockEntities");
                if (blockEntities != null) {
                    for (NBTTag be : blockEntities) {
                        if (!(be instanceof NBTTag.CompoundTag)) continue;
                        NBTTag.CompoundTag beComp = (NBTTag.CompoundTag) be;
                        String id = beComp.getString("id");
                        if (id == null) id = beComp.getString("Id");
                        if (id == null) continue;

                        String lowerId = id.toLowerCase();
                        if (lowerId.contains("chest") || lowerId.contains("barrel")) {
                            books.addAll(extractBooksFromContainer(beComp, logArea));
                        } else if (lowerId.contains("lectern")) {
                            books.addAll(extractBookFromLectern(beComp, logArea));
                        }
                    }
                }
            }
        }

        log("文件 " + mcaFile.getName() + " 提取完成，共 " + books.size() + " 本成书", logArea);
        return books;
    }

    private static void log(String msg, JTextArea logArea) {
        System.out.println(msg);
        if (logArea != null) {
            logArea.append(msg + "\n");
        }
    }

    private static String getGenerationName(int generation) {
    switch (generation) {
        case 0: return "原稿";
        case 1: return "原稿的副本";
        case 2: return "副本的副本";
        case 3: return "破烂不堪";
        default: return "未知世代";
    }
}

    private static List<WrittenBook> extractBookFromEntity(NBTTag.CompoundTag entity, JTextArea logArea) {
    List<WrittenBook> books = new ArrayList<>();
    String id = entity.getString("id");
    if (!"minecraft:item".equalsIgnoreCase(id) && !"Item".equalsIgnoreCase(id)) return books;

    NBTTag itemTag = entity.getCompound("Item");
    if (itemTag == null) return books;

    String itemId = itemTag.getString("id");
    if (!"minecraft:written_book".equalsIgnoreCase(itemId) && !"written_book".equalsIgnoreCase(itemId)) return books;

    NBTTag tag = itemTag.getCompound("tag");
    if (tag != null) {
        WrittenBook book = parseBookFromTag(tag);
        if (book != null) {
            log("      发现掉落物成书：《" + book.title + "》 (" + getGenerationName(book.generation) + ")", logArea);
            books.add(book);
        }
    }
    return books;
}

    private static List<WrittenBook> extractBookFromLectern(NBTTag.CompoundTag lectern, JTextArea logArea) {
    List<WrittenBook> books = new ArrayList<>();
    
    NBTTag bookTag = lectern.getCompound("Book");
    if (bookTag == null) bookTag = lectern.getCompound("book");
    if (bookTag == null) {
        log("      讲台上没有书", logArea);
        return books;
    }

    String itemId = bookTag.getString("id");
    if (itemId == null) itemId = bookTag.getString("Id");
    if (itemId == null || !itemId.contains("written_book")) return books;

    log("      讲台上的书 ID: " + itemId, logArea);

    WrittenBook book = null;
    
    // 1. 尝试新版 components 结构
    NBTTag components = bookTag.getCompound("components");
    if (components != null && components instanceof NBTTag.CompoundTag) {
        NBTTag.CompoundTag compTag = (NBTTag.CompoundTag) components;
        log("        讲台书的 components 键: " + compTag.keySet(), logArea);
        
        String[] possibleKeys = {
            "minecraft:written_book_content",
            "written_book_content",
            "minecraft:written_book",
            "written_book"
        };
        for (String key : possibleKeys) {
            NBTTag contentTag = compTag.getCompound(key);
            if (contentTag != null) {
                log("        使用内容组件键: " + key, logArea);
                // 传入完整 compTag 以读取 custom_name
                book = parseBookFromContentComponent(contentTag, compTag, logArea);
                if (book != null) break;
            }
        }
    }
    
    // 2. 回退：旧版 tag 结构
    if (book == null) {
        NBTTag tag = bookTag.getCompound("tag");
        if (tag != null) {
            log("        使用旧版 tag 结构", logArea);
            book = parseBookFromTag(tag);
        }
    }
    
    if (book != null) {
        log("        -> 发现讲台上的成书：《" + book.title + "》 (" + getGenerationName(book.generation) + ")", logArea);
        books.add(book);
    } else {
        log("        -> 解析讲台成书失败", logArea);
    }
    
    return books;
}

    private static List<WrittenBook> extractBooksFromContainer(NBTTag.CompoundTag container, JTextArea logArea) {
    List<WrittenBook> books = new ArrayList<>();
    List<NBTTag> items = container.getList("Items");
    if (items == null) items = container.getList("items");
    if (items == null) return books;

    log("      容器内有 " + items.size() + " 个物品", logArea);
    for (NBTTag item : items) {
        if (!(item instanceof NBTTag.CompoundTag)) continue;
        NBTTag.CompoundTag itemComp = (NBTTag.CompoundTag) item;

        String itemId = itemComp.getString("id");
        if (itemId == null) itemId = itemComp.getString("Id");

        byte count = itemComp.getByte("Count");
        if (count == 0) count = itemComp.getByte("count");

        log("        物品 ID: " + itemId + " (数量: " + count + ")", logArea);

        if (itemId != null && itemId.contains("written_book")) {
            NBTTag components = itemComp.getCompound("components");
            WrittenBook book = null;
            
            if (components != null && components instanceof NBTTag.CompoundTag) {
                NBTTag.CompoundTag compTag = (NBTTag.CompoundTag) components;
                log("          components 键: " + compTag.keySet(), logArea);
                
                String[] possibleKeys = {
                    "minecraft:written_book_content",
                    "written_book_content",
                    "minecraft:written_book",
                    "written_book"
                };
                
                for (String key : possibleKeys) {
                    NBTTag contentTag = compTag.getCompound(key);
                    if (contentTag != null) {
                        log("            内容组件键: " + compTag.keySet(), logArea);
                        // 注意：这里传入了完整的 compTag 以便读取 custom_name
                        book = parseBookFromContentComponent(contentTag, compTag, logArea);
                        if (book != null) break;
                    }
                }
            }
            
            // 回退：旧版 tag 方式
            if (book == null) {
                NBTTag tag = itemComp.getCompound("tag");
                if (tag != null) {
                    book = parseBookFromTag(tag);
                }
            }
            
            if (book != null) {
                log("          -> 发现成书：《" + book.title + "》 (" + getGenerationName(book.generation) + ")", logArea);
                books.add(book);
            } else {
                log("          -> 解析成书失败（数据结构未知）", logArea);
            }
        }
    }
    return books;
}

    private static WrittenBook parseBookFromContentComponent(NBTTag contentTag, NBTTag.CompoundTag componentsTag, JTextArea logArea) {
    if (!(contentTag instanceof NBTTag.CompoundTag)) return null;
    NBTTag.CompoundTag comp = (NBTTag.CompoundTag) contentTag;

    log("            内容组件键: " + comp.keySet(), logArea);

    // --- 优先级1：从 custom_name 获取标题（铁砧重命名）---
    String title = null;
    NBTTag customNameTag = componentsTag.getCompound("minecraft:custom_name");
    if (customNameTag != null) {
        title = extractTextFromJsonComponent(customNameTag, logArea);
        if (title != null && !title.isEmpty()) {
            log("            使用铁砧重命名 (custom_name): " + title, logArea);
        }
    }

    // --- 优先级2：回退到 written_book_content 的 title（署名标题）---
    if (title == null || title.isEmpty()) {
        String originalTitle = extractTextFromJsonComponent(comp.get("title"), logArea);
        if (originalTitle != null && !originalTitle.isEmpty()) {
            title = originalTitle;
            log("            回退使用署名标题 (title): " + title, logArea);
        }
    }

    // --- 优先级3：最后尝试 item_name 组件（物品默认名称）---
    if (title == null || title.isEmpty()) {
        NBTTag itemNameTag = componentsTag.getCompound("minecraft:item_name");
        if (itemNameTag != null) {
            title = extractTextFromJsonComponent(itemNameTag, logArea);
            if (title != null && !title.isEmpty()) {
                log("            使用物品默认名称 (item_name): " + title, logArea);
            }
        }
    }

    // 最终保底
    if (title == null || title.isEmpty()) {
        title = "无标题";
    }

    // 解析作者（不受铁砧影响，始终从 written_book_content 读取）
    String author = extractTextFromJsonComponent(comp.get("author"), logArea);
    if (author == null || author.isEmpty()) {
        author = "未知";
    }

    // 读取 generation（副本等级）
    int generation = comp.getInt("generation");

    // 解析页面
    List<NBTTag> pagesList = comp.getList("pages");
    if (pagesList == null || pagesList.isEmpty()) {
        log("            没有页面数据", logArea);
        return null;
    }

    List<String> pages = new ArrayList<>();
    for (NBTTag pageTag : pagesList) {
        if (pageTag instanceof NBTTag.StringTag) {
            pages.add(((NBTTag.StringTag) pageTag).getValue());
        } else if (pageTag instanceof NBTTag.CompoundTag) {
            String pageText = extractTextFromJsonComponent(pageTag, logArea);
            if (pageText != null && !pageText.isEmpty()) {
                pages.add(pageText);
            }
        }
    }

    if (pages.isEmpty()) return null;
    return new WrittenBook(title, author, pages, generation);
}

private static String extractTextFromJsonComponent(NBTTag tag, JTextArea logArea) {
    if (tag == null) return null;

    // 1. 纯字符串直接返回
    if (tag instanceof NBTTag.StringTag) {
        return ((NBTTag.StringTag) tag).getValue();
    }

    // 2. 复合标签（JSON 组件）
    if (tag instanceof NBTTag.CompoundTag) {
        NBTTag.CompoundTag comp = (NBTTag.CompoundTag) tag;
        
        // 调试输出键名
        if (logArea != null) {
        log("            JSON组件键: " + comp.keySet(), logArea);
        }
        
        // 尝试 raw 字段（1.21.8 新格式）
        String raw = comp.getString("raw");
        if (raw != null && !raw.isEmpty()) return raw;
        
        // 尝试 text 字段（旧格式兼容）
        String text = comp.getString("text");
        if (text != null && !text.isEmpty()) return text;
        
        // 尝试嵌套 extra 列表
        List<NBTTag> extraList = comp.getList("extra");
        if (extraList != null) {
            StringBuilder sb = new StringBuilder();
            for (NBTTag extraTag : extraList) {
                String part = extractTextFromJsonComponent(extraTag, null);
                if (part != null) sb.append(part);
            }
            if (sb.length() > 0) return sb.toString();
        }
        
        // 其他情况返回 null
        return null;
    }

    // 3. 列表标签取第一个元素
    if (tag instanceof NBTTag.ListTag) {
        List<NBTTag> list = ((NBTTag.ListTag) tag).getValue();
        if (!list.isEmpty()) {
            return extractTextFromJsonComponent(list.get(0), logArea);
        }
    }

    return null;
}


    private static WrittenBook parseBookFromTag(NBTTag tag) {
    if (!(tag instanceof NBTTag.CompoundTag)) return null;
    NBTTag.CompoundTag compound = (NBTTag.CompoundTag) tag;

    String title = compound.getString("title");
    String author = compound.getString("author");
    int generation = compound.getInt("generation");  // 读取 generation
    List<NBTTag> pagesList = compound.getList("pages");

    if (pagesList == null || pagesList.isEmpty()) return null;

    List<String> pages = new ArrayList<>();
    for (NBTTag pageTag : pagesList) {
        if (pageTag instanceof NBTTag.StringTag) {
            pages.add(((NBTTag.StringTag) pageTag).getValue());
        }
    }

    if (title == null || title.isEmpty()) title = "无标题";
    return new WrittenBook(title, author, pages, generation);
}

    private static void generateFunctionFile(WrittenBook book, File outputDir) throws IOException {
    synchronized (outputDir) {
        String generationName = getGenerationName(book.generation);
        String baseTitle = book.title + "（" + generationName + "）";
        String safeFileName = baseTitle.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5（）]", "_");
        if (safeFileName.isEmpty()) safeFileName = "book";

        String baseName = safeFileName;
        int counter = 1;
        File functionFile = new File(outputDir, baseName + ".mcfunction");
        while (functionFile.exists()) {
            baseName = safeFileName + "_" + counter;
            functionFile = new File(outputDir, baseName + ".mcfunction");
            counter++;
        }
        if (counter > 1) {
            System.out.println("  注意：标题重复，已重命名为 " + functionFile.getName());
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(functionFile), "UTF-8"))) {
            StringBuilder cmd = new StringBuilder();
            // 改为成书 (written_book) 并包含完整元数据
            cmd.append("give @p minecraft:written_book[");
            cmd.append("minecraft:written_book_content={");
            // 书名
            cmd.append("title:\"").append(escapeJsonString(book.title)).append("\",");
            // 作者
            cmd.append("author:\"").append(escapeJsonString(book.author)).append("\",");
            // 世代
            cmd.append("generation:").append(book.generation).append(",");
            // 页面数组
            cmd.append("pages:[");
            for (int i = 0; i < book.pages.size(); i++) {
                if (i > 0) cmd.append(",");
                String page = book.pages.get(i)
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");
                cmd.append("\"").append(page).append("\"");
            }
            cmd.append("]}]");
            writer.write(cmd.toString());
        }
    }
}

// 在 generateFunctionFile 方法之后添加以下辅助方法
private static String escapeJsonString(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
}

    private static class WrittenBook {
    String title, author;
    List<String> pages;
    int generation;  // 0=原稿, 1=原稿的副本, 2=副本的副本

    WrittenBook(String t, String a, List<String> p) {
        this(t, a, p, 0);  // 默认 generation 为 0
    }

    WrittenBook(String t, String a, List<String> p, int g) {
        title = t;
        author = a;
        pages = p;
        generation = g;
    }
}

    // ==================== 完整的 NBT 解析器 ====================
    private static abstract class NBTTag {
        
        static final int TAG_END = 0, TAG_BYTE = 1, TAG_SHORT = 2, TAG_INT = 3, TAG_LONG = 4,
                TAG_FLOAT = 5, TAG_DOUBLE = 6, TAG_BYTE_ARRAY = 7, TAG_STRING = 8,
                TAG_LIST = 9, TAG_COMPOUND = 10, TAG_INT_ARRAY = 11, TAG_LONG_ARRAY = 12;

        public static NBTTag readFromStream(DataInputStream dis) throws IOException {
            int type = dis.readUnsignedByte();
            if (type == TAG_END) return new EndTag();
            String name = dis.readUTF();
            return readPayload(type, name, dis);
        }

        private static NBTTag readPayload(int type, String name, DataInputStream dis) throws IOException {
            switch (type) {
                case TAG_BYTE: return new ByteTag(name, dis.readByte());
                case TAG_SHORT: return new ShortTag(name, dis.readShort());
                case TAG_INT: return new IntTag(name, dis.readInt());
                case TAG_LONG: return new LongTag(name, dis.readLong());
                case TAG_FLOAT: return new FloatTag(name, dis.readFloat());
                case TAG_DOUBLE: return new DoubleTag(name, dis.readDouble());
                case TAG_BYTE_ARRAY:
                    int len = dis.readInt();
                    byte[] b = new byte[len];
                    dis.readFully(b);
                    return new ByteArrayTag(name, b);
                case TAG_STRING:
                    return new StringTag(name, dis.readUTF());
                case TAG_LIST:
                    int listType = dis.readUnsignedByte();
                    int size = dis.readInt();
                    List<NBTTag> list = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) list.add(readPayload(listType, "", dis));
                    return new ListTag(name, list, listType);
                case TAG_COMPOUND:
                    CompoundTag comp = new CompoundTag(name);
                    NBTTag t;
                    while ((t = readFromStream(dis)) != null && !(t instanceof EndTag))
                        comp.put(t.getName(), t);
                    return comp;
                case TAG_INT_ARRAY:
                    int ilen = dis.readInt();
                    int[] ints = new int[ilen];
                    for (int i = 0; i < ilen; i++) ints[i] = dis.readInt();
                    return new IntArrayTag(name, ints);
                case TAG_LONG_ARRAY:
                    int llen = dis.readInt();
                    long[] longs = new long[llen];
                    for (int i = 0; i < llen; i++) longs[i] = dis.readLong();
                    return new LongArrayTag(name, longs);
                default: throw new IOException("未知NBT类型: " + type);
            }
        }

        public abstract String getName();

        public NBTTag getCompound(String key) { return null; }
        public List<NBTTag> getList(String key) { return null; }
        public String getString(String key) { return null; }
        public byte getByte(String key) { return 0; }
        public short getShort(String key) { return 0; }
        public int getInt(String key) { return 0; }
        public long getLong(String key) { return 0L; }
        public float getFloat(String key) { return 0f; }
        public double getDouble(String key) { return 0d; }

        // 具体实现类
        static class EndTag extends NBTTag { public String getName() { return ""; } }

        static class ByteTag extends NBTTag {
            private final String name; private final byte value;
            ByteTag(String n, byte v) { name = n; value = v; }
            public String getName() { return name; }
            public byte getValue() { return value; }
            @Override public byte getByte(String key) { return value; }
        }

        static class ShortTag extends NBTTag {
            private final String name; private final short value;
            ShortTag(String n, short v) { name = n; value = v; }
            public String getName() { return name; }
            @Override public short getShort(String key) { return value; }
        }

        static class IntTag extends NBTTag {
            private final String name; private final int value;
            IntTag(String n, int v) { name = n; value = v; }
            public String getName() { return name; }
            @Override public int getInt(String key) { return value; }
        }

        static class LongTag extends NBTTag {
            private final String name; private final long value;
            LongTag(String n, long v) { name = n; value = v; }
            public String getName() { return name; }
            @Override public long getLong(String key) { return value; }
        }

        static class FloatTag extends NBTTag {
            private final String name; private final float value;
            FloatTag(String n, float v) { name = n; value = v; }
            public String getName() { return name; }
            @Override public float getFloat(String key) { return value; }
        }

        static class DoubleTag extends NBTTag {
            private final String name; private final double value;
            DoubleTag(String n, double v) { name = n; value = v; }
            public String getName() { return name; }
            @Override public double getDouble(String key) { return value; }
        }

        static class ByteArrayTag extends NBTTag {
            private final String name; private final byte[] value;
            ByteArrayTag(String n, byte[] v) { name = n; value = v; }
            public String getName() { return name; }
        }

        static class StringTag extends NBTTag {
            private final String name; private final String value;
            StringTag(String n, String v) { name = n; value = v; }
            public String getName() { return name; }
            public String getValue() { return value; }
            @Override public String getString(String key) { return value; }
        }

        static class ListTag extends NBTTag {
            private final String name; private final List<NBTTag> value; private final int elemType;
            ListTag(String n, List<NBTTag> v, int t) { name = n; value = v; elemType = t; }
            public String getName() { return name; }
            public List<NBTTag> getValue() { return value; }
        }

        static class CompoundTag extends NBTTag {
            private final String name;
            private final java.util.Map<String, NBTTag> value = new java.util.HashMap<>();
            CompoundTag(String n) { name = n; }
            public String getName() { return name; }
            public void put(String k, NBTTag v) { value.put(k, v); }
            public NBTTag get(String k) { return value.get(k); }

            public java.util.Set<String> keySet() {
            return value.keySet();
            }

            @Override public NBTTag getCompound(String key) { return value.get(key); }
            @Override public List<NBTTag> getList(String key) {
                NBTTag t = value.get(key);
                return (t instanceof ListTag) ? ((ListTag) t).getValue() : null;
            }
            @Override public String getString(String key) {
                NBTTag t = value.get(key);
                return (t instanceof StringTag) ? ((StringTag) t).getValue() : null;
            }
            @Override public byte getByte(String key) {
                NBTTag t = value.get(key);
                return (t instanceof ByteTag) ? ((ByteTag) t).getValue() : 0;
            }
            @Override public short getShort(String key) {
                NBTTag t = value.get(key);
                return (t instanceof ShortTag) ? ((ShortTag) t).getShort(key) : 0;
            }
            @Override public int getInt(String key) {
                NBTTag t = value.get(key);
                return (t instanceof IntTag) ? ((IntTag) t).getInt(key) : 0;
            }
            @Override public long getLong(String key) {
                NBTTag t = value.get(key);
                return (t instanceof LongTag) ? ((LongTag) t).getLong(key) : 0L;
            }
            @Override public float getFloat(String key) {
                NBTTag t = value.get(key);
                return (t instanceof FloatTag) ? ((FloatTag) t).getFloat(key) : 0f;
            }
            @Override public double getDouble(String key) {
                NBTTag t = value.get(key);
                return (t instanceof DoubleTag) ? ((DoubleTag) t).getDouble(key) : 0d;
            }
        }

        static class IntArrayTag extends NBTTag {
            private final String name; private final int[] value;
            IntArrayTag(String n, int[] v) { name = n; value = v; }
            public String getName() { return name; }
        }

        static class LongArrayTag extends NBTTag {
            private final String name; private final long[] value;
            LongArrayTag(String n, long[] v) { name = n; value = v; }
            public String getName() { return name; }
        }
    }
}