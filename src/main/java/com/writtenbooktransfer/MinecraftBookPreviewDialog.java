package com.writtenbooktransfer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Minecraft 成书风格预览对话框
 */
public class MinecraftBookPreviewDialog extends JDialog {

    private List<String> pages;
    private int currentPage = 0;
    private JTextArea pageArea;
    private JLabel pageLabel;
    private JButton prevButton, nextButton;
    private JButton defaultFormatButton;
    private boolean confirmed = false;

    public MinecraftBookPreviewDialog(Frame owner, List<String> pages, String title) {
        super(owner, "成书预览 - " + title, true);
        this.pages = pages;
        initUI();
        displayPage(0);
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setSize(500, 400);
        setLayout(new BorderLayout(10, 10));

        // 顶部说明
        JLabel infoLabel = new JLabel("<html><center><b>Minecraft 成书预览</b><br>"
                + "此工具可将内容自动输入到游戏中。请确保游戏窗口在前，鼠标悬停在翻页按钮上，按下 Ctrl 开始。</center></html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(infoLabel, BorderLayout.NORTH);

        // 中央书页区域
        pageArea = new JTextArea();
        pageArea.setEditable(false);
        pageArea.setLineWrap(true);
        pageArea.setWrapStyleWord(true);
        pageArea.setFont(new Font("Serif", Font.PLAIN, 14));
        pageArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY, 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        JScrollPane scrollPane = new JScrollPane(pageArea);
        add(scrollPane, BorderLayout.CENTER);

        // 底部控制栏
        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        prevButton = new JButton("◀ 上一页");
        nextButton = new JButton("下一页 ▶");
        pageLabel = new JLabel();

        prevButton.addActionListener(e -> {
            if (currentPage > 0) displayPage(currentPage - 1);
        });
        nextButton.addActionListener(e -> {
            if (currentPage < pages.size() - 1) displayPage(currentPage + 1);
        });

        navPanel.add(prevButton);
        navPanel.add(pageLabel);
        navPanel.add(nextButton);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        defaultFormatButton = new JButton("默认排版");
        defaultFormatButton.setToolTipText("应用段落间空一行、首尾不空行的默认格式");
        JButton confirmButton = new JButton("确认传输");
        JButton cancelButton = new JButton("取消");

        confirmButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(defaultFormatButton);
        buttonPanel.add(confirmButton);
        buttonPanel.add(cancelButton);

        bottomPanel.add(navPanel, BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void displayPage(int index) {
        if (pages == null || pages.isEmpty()) {
            pageArea.setText("（无内容）");
            pageLabel.setText("0 / 0");
            return;
        }
        currentPage = index;
        pageArea.setText(pages.get(index));
        pageLabel.setText((index + 1) + " / " + pages.size());
        pageArea.setCaretPosition(0);
        prevButton.setEnabled(index > 0);
        nextButton.setEnabled(index < pages.size() - 1);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * 设置“默认排版”按钮的动作
     */
    public void setDefaultFormatAction(Runnable action) {
        // 移除原有监听器，避免重复
        for (ActionListener al : defaultFormatButton.getActionListeners()) {
            defaultFormatButton.removeActionListener(al);
        }
        defaultFormatButton.addActionListener(e -> action.run());
    }

    /**
     * 刷新预览内容（排版变化后调用）
     */
    public void refreshPages(List<String> newPages) {
        this.pages = newPages;
        displayPage(0);
    }
}