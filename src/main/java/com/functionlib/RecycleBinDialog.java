package com.functionlib;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * 回收站图形化管理对话框
 */
public class RecycleBinDialog extends JDialog {

    private final File recycleBinDir;
    private final File recycleLogFile;
    private final JTable fileTable;
    private final RecycleTableModel tableModel;
    private final JTextArea previewArea;
    private final JButton recoverButton;
    private final JButton deleteButton;
    private final JButton clearAllButton;
    private final JButton refreshButton;
    private final Runnable onExternalRefresh;

    private List<RecycleEntry> entries = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public RecycleBinDialog(Frame owner, File recycleBinDir, File recycleLogFile, Runnable onExternalRefresh) {
        super(owner, "回收站管理", true);
        this.recycleBinDir = recycleBinDir;
        this.recycleLogFile = recycleLogFile;
        this.onExternalRefresh = onExternalRefresh;

        setSize(950, 650);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // 顶部信息栏
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel infoLabel = new JLabel("回收站位置：" + recycleBinDir.getAbsolutePath());
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        topPanel.add(infoLabel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // 中央分割面板
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.65);
        splitPane.setDividerLocation(400);

        // 表格区域
        tableModel = new RecycleTableModel();
        fileTable = new JTable(tableModel);
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(280);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(400);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        fileTable.getColumnModel().getColumn(4).setPreferredWidth(80);

        JScrollPane tableScroll = new JScrollPane(fileTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("回收站文件列表"));
        splitPane.setTopComponent(tableScroll);

        // 预览区域
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(new Font("宋体", Font.PLAIN, 13));
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);
        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createTitledBorder("文件内容预览（前2000字符）"));
        splitPane.setBottomComponent(previewScroll);

        add(splitPane, BorderLayout.CENTER);

        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));

        recoverButton = new JButton("恢复选中文件");
        deleteButton = new JButton("彻底删除");
        clearAllButton = new JButton("清空回收站");
        refreshButton = new JButton("刷新");
        JButton closeButton = new JButton("关闭");

        recoverButton.setEnabled(false);
        deleteButton.setEnabled(false);

        buttonPanel.add(recoverButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(clearAllButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // 事件绑定
        fileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = fileTable.getSelectedRow();
                boolean selected = row >= 0;
                recoverButton.setEnabled(selected);
                deleteButton.setEnabled(selected);
                if (selected) {
                    RecycleEntry entry = entries.get(row);
                    previewFile(entry.file);
                } else {
                    previewArea.setText("");
                }
            }
        });

        recoverButton.addActionListener(this::recoverSelected);
        deleteButton.addActionListener(this::deleteSelected);
        clearAllButton.addActionListener(this::clearAll);
        refreshButton.addActionListener(e -> refreshList());
        closeButton.addActionListener(e -> dispose());

        // 加载数据
        refreshList();

        setVisible(true);
    }

    private void refreshList() {
        entries.clear();
        if (recycleBinDir.exists() && recycleBinDir.isDirectory()) {
            File[] files = recycleBinDir.listFiles(f -> !f.getName().equals("recycle_log.txt"));
            if (files != null) {
                Map<String, String> nameToOriginal = loadLogMapping();
                for (File file : files) {
                    String originalPath = nameToOriginal.getOrDefault(file.getName(), "未知");
                    long timestamp = extractTimestamp(file.getName());
                    entries.add(new RecycleEntry(file, originalPath, timestamp));
                }
                entries.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
            }
        }
        tableModel.fireTableDataChanged();
        if (entries.isEmpty()) {
            previewArea.setText("回收站为空。");
        }
        clearAllButton.setEnabled(!entries.isEmpty());
    }

    private Map<String, String> loadLogMapping() {
        Map<String, String> map = new HashMap<>();
        if (recycleLogFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(recycleLogFile.toPath());
                for (String line : lines) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 2) {
                        map.put(parts[1], parts[0]);
                    }
                }
            } catch (IOException ignored) {}
        }
        return map;
    }

    private long extractTimestamp(String fileName) {
        int idx = fileName.lastIndexOf('_');
        if (idx > 0) {
            try {
                return Long.parseLong(fileName.substring(idx + 1));
            } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    private void previewFile(File file) {
        try {
            String content = Files.readString(file.toPath());
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "\n\n... (内容过长，已截断)";
            }
            previewArea.setText(content);
            previewArea.setCaretPosition(0);
        } catch (IOException e) {
            previewArea.setText("无法读取文件内容：" + e.getMessage());
        }
    }

     private void recoverSelected(ActionEvent e) {
        int row = fileTable.getSelectedRow();
        if (row < 0) return;
        RecycleEntry entry = entries.get(row);
        File target = entry.file;
        String originalPath = entry.originalPath;

        File originalFile = new File(originalPath);
        File parentDir = originalFile.getParentFile();
        if (!parentDir.exists()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "原目录不存在：\n" + parentDir.getAbsolutePath() + "\n\n是否创建目录并恢复？",
                    "目录不存在", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
            parentDir.mkdirs();
        }

        if (originalFile.exists()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "目标位置已存在同名文件，是否覆盖？",
                    "文件已存在", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        try {
            Files.move(target.toPath(), originalFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            removeLogEntry(target.getName());
            refreshList();
            JOptionPane.showMessageDialog(this, "文件已恢复到：\n" + originalPath, "恢复成功", JOptionPane.INFORMATION_MESSAGE);
            
            // ✅ 恢复成功后，触发外部刷新
            if (onExternalRefresh != null) {
                SwingUtilities.invokeLater(onExternalRefresh);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "恢复失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected(ActionEvent e) {
        int row = fileTable.getSelectedRow();
        if (row < 0) return;
        RecycleEntry entry = entries.get(row);
        int choice = JOptionPane.showConfirmDialog(this,
                "确定要彻底删除以下文件吗？\n" + entry.file.getName() + "\n\n此操作不可恢复！",
                "确认删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        try {
            Files.deleteIfExists(entry.file.toPath());
            removeLogEntry(entry.file.getName());
            refreshList();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "删除失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearAll(ActionEvent e) {
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "回收站已空。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "确定要清空回收站吗？\n所有文件将被永久删除！",
                "确认清空", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        for (RecycleEntry entry : entries) {
            try {
                Files.deleteIfExists(entry.file.toPath());
            } catch (IOException ignored) {}
        }
        try {
            Files.write(recycleLogFile.toPath(), new byte[0]);
        } catch (IOException ignored) {}
        refreshList();
        JOptionPane.showMessageDialog(this, "回收站已清空。", "完成", JOptionPane.INFORMATION_MESSAGE);
    }

    private void removeLogEntry(String fileName) {
        if (!recycleLogFile.exists()) return;
        try {
            List<String> lines = Files.readAllLines(recycleLogFile.toPath());
            lines.removeIf(line -> line.split("\\|").length >= 2 && line.split("\\|")[1].equals(fileName));
            Files.write(recycleLogFile.toPath(), lines);
        } catch (IOException ignored) {}
    }

    // 内部数据类
    private static class RecycleEntry {
        final File file;
        final String originalPath;
        final long timestamp;

        RecycleEntry(File file, String originalPath, long timestamp) {
            this.file = file;
            this.originalPath = originalPath;
            this.timestamp = timestamp;
        }
    }

    // 表格模型
    private class RecycleTableModel extends AbstractTableModel {
        private final String[] columns = {"序号", "文件名", "原路径", "删除时间", "大小(KB)"};

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RecycleEntry entry = entries.get(rowIndex);
            switch (columnIndex) {
                case 0: return rowIndex + 1;
                case 1: return entry.file.getName();
                case 2: return entry.originalPath;
                case 3: return entry.timestamp > 0 ? dateFormat.format(new Date(entry.timestamp)) : "未知";
                case 4: return entry.file.length() / 1024;
                default: return "";
            }
        }
    }
}