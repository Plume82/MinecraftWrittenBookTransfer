package com.bookexport;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * bookexport 模块独立入口
 * 将 .mcfunction 文件中的成书内容导出为可读的纯文本 .txt 文件
 */
public class BookExportApp {

    private static Path selectedFilePath = null;
    private static Path outputDir = Paths.get("").toAbsolutePath(); // 默认输出到当前目录

    public static void main(String[] args) {
        // 如果有命令行参数，直接处理
        if (args.length >= 1) {
            selectedFilePath = Paths.get(args[0]);
            if (args.length >= 2) {
                outputDir = Paths.get(args[1]);
            }
            if (Files.exists(selectedFilePath)) {
                try {
                    exportFile();
                } catch (IOException e) {
                    System.err.println("导出失败: " + e.getMessage());
                }
            } else {
                System.err.println("文件不存在: " + args[0]);
            }
            return;
        }

        // 交互式菜单
        showExportMenu();
    }

    private static void showExportMenu() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- 成书导出为文本模块 ---");
            System.out.println("当前选中文件: " + (selectedFilePath != null ? selectedFilePath.getFileName() : "无"));
            System.out.println("输出目录: " + outputDir.toAbsolutePath());
            System.out.println("1. 选择 .mcfunction 文件");
            System.out.println("2. 设置输出目录");
            System.out.println("3. 开始导出");
            System.out.println("0. 返回主菜单");
            System.out.print("请选择: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    selectFileInteractively();
                    break;
                case "2":
                    setOutputDirectory();
                    break;
                case "3":
                    if (selectedFilePath == null) {
                        System.out.println("请先选择文件！");
                    } else {
                        try {
                            exportFile();
                        } catch (IOException e) {
                            System.err.println("导出失败: " + e.getMessage());
                        }
                    }
                    break;
                case "0":
                    return;
                default:
                    System.out.println("无效选项");
            }
        }
    }

    private static void selectFileInteractively() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n--- 选择文件 ---");
        System.out.println("o - 打开文件选择对话框");
        System.out.println("a - 自动扫描当前目录");
        System.out.print("请选择 (o/a): ");
        String cmd = scanner.nextLine().trim().toLowerCase();

        Path file = null;
        if (cmd.equals("o")) {
            file = openFileChooser();
        } else if (cmd.equals("a")) {
            file = autoSelectFromCurrentDir();
        } else {
            System.out.println("无效选项。");
            return;
        }

        if (file != null) {
            selectedFilePath = file;
            System.out.println("已选择文件: " + selectedFilePath.getFileName());
        }
    }

    private static Path openFileChooser() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("选择 .mcfunction 文件");
        chooser.setFileFilter(new FileNameExtensionFilter("Minecraft Function 文件", "mcfunction"));
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().toPath();
        }
        return null;
    }

    private static Path autoSelectFromCurrentDir() {
        try {
            Path baseDir = Paths.get("").toAbsolutePath();
            List<Path> files = Files.list(baseDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".mcfunction"))
                    .collect(Collectors.toList());
            if (files.isEmpty()) {
                System.out.println("当前目录下没有任何 .mcfunction 文件！");
                return null;
            }
            if (files.size() == 1) return files.get(0);
            System.out.println("\n发现多个 .mcfunction 文件：");
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

    private static void setOutputDirectory() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n--- 设置输出目录 ---");
        System.out.println("当前输出目录: " + outputDir.toAbsolutePath());
        System.out.println("c - 使用目录选择对话框");
        System.out.println("m - 手动输入路径");
        System.out.print("请选择 (c/m): ");
        String cmd = scanner.nextLine().trim().toLowerCase();

        if (cmd.equals("c")) {
            JFileChooser chooser = new JFileChooser(outputDir.toFile());
            chooser.setDialogTitle("选择输出目录");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                outputDir = chooser.getSelectedFile().toPath();
                System.out.println("输出目录已设置为: " + outputDir.toAbsolutePath());
            }
        } else if (cmd.equals("m")) {
            System.out.print("请输入输出目录路径: ");
            String pathStr = scanner.nextLine().trim();
            Path path = Paths.get(pathStr);
            if (Files.isDirectory(path)) {
                outputDir = path.toAbsolutePath();
                System.out.println("输出目录已设置为: " + outputDir);
            } else {
                System.out.println("无效的目录路径。");
            }
        } else {
            System.out.println("无效选项。");
        }
    }

    private static void exportFile() throws IOException {
        if (selectedFilePath == null) {
            System.out.println("没有选中文件！");
            return;
        }

        // 生成输出文件名
        String inputName = selectedFilePath.getFileName().toString();
        String outputName = inputName.replace(".mcfunction", ".txt");
        Path outputFile = outputDir.resolve(outputName);

        // 避免覆盖：若文件已存在，添加序号
        if (Files.exists(outputFile)) {
            String baseName = outputName.replace(".txt", "");
            int counter = 1;
            while (Files.exists(outputDir.resolve(baseName + "_" + counter + ".txt"))) {
                counter++;
            }
            outputFile = outputDir.resolve(baseName + "_" + counter + ".txt");
        }

        boolean success = McFunctionExporter.exportToTextFile(selectedFilePath, outputFile);
        if (success) {
            System.out.println("✅ 导出成功！");
            System.out.println("输出文件: " + outputFile.toAbsolutePath());
        } else {
            System.out.println("❌ 导出失败：未能提取到页面内容。");
        }
    }
}