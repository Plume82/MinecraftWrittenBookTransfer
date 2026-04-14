package com.bookextractor;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.common.McFunctionParser;
import com.booktypesetting.BookConfig;
import com.booktypesetting.TextFormatter;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import javax.swing.JOptionPane;

import java.text.Collator;
/**
 * 成书报告生成器
 * 扫描 _books_functions 文件夹，统计成书信息，生成 Word 报告
 */
public class BookReportGenerator {

    static class BookInfo {
        String fileName;           // 文件名（不含扩展名）
        String title;              // 书名（去除世代后缀）
        String generation;         // 世代名称（原稿/原稿的副本/副本的副本）
        String author;             // 作者（从文件内容中提取，若无法提取则为 "未知"）
        int pages;                 // 页数（原始页面数量）
        int wordCount;             // 字数（不含空格和特殊符号）
        List<String> rawPages;     // 每页的原始纯文本内容（未分行）
        boolean isDuplicate;       // 是否为重复书籍（文件名带 _数字 后缀）
        String baseName;           // 去重后的基础名称
        int duplicateCount = 1;    // 该内容变体的副本总数（含自身）

        BookInfo(File file) throws IOException {
            this.fileName = file.getName().replace(".mcfunction", "");
            parseFileName();
            parseContent(file);
        }

        // 拷贝构造函数
        BookInfo(BookInfo other) {
            this.fileName = other.fileName;
            this.title = other.title;
            this.generation = other.generation;
            this.author = other.author;
            this.pages = other.pages;
            this.wordCount = other.wordCount;
            this.rawPages = new ArrayList<>(other.rawPages);
            this.isDuplicate = other.isDuplicate;
            this.baseName = other.baseName;
            this.duplicateCount = other.duplicateCount;
        }

        private void parseFileName() {
            if (fileName.contains("（原稿）")) {
                generation = "原稿";
            } else if (fileName.contains("（原稿的副本）")) {
                generation = "原稿的副本";
            } else if (fileName.contains("（副本的副本）")) {
                generation = "副本的副本";
            } else {
                generation = "未知";
            }

            title = fileName.replaceAll("[（(].*[）)]", "").trim();
            if (title.isEmpty()) title = fileName;

            Pattern dupPattern = Pattern.compile("_\\d+$");
            Matcher m = dupPattern.matcher(title);
            isDuplicate = m.find();
            baseName = isDuplicate ? title.replaceAll("_\\d+$", "") : title;
        }

        private void parseContent(File file) throws IOException {
            // 读取文件全部内容（用于提取作者）
            String fullContent = new String(Files.readAllBytes(file.toPath()), "UTF-8");
            // 提取作者：匹配 "author":"xxx" 或 'author':'xxx'

            // 尝试多种可能的 author 字段格式
            String author = null;
            // 格式1：标准 JSON "author":"xxx"
            Pattern p1 = Pattern.compile("author\"\\s*:\\s*\"([^\"]*)\"");
            Matcher m1 = p1.matcher(fullContent);
            if (m1.find()) {
                author = m1.group(1);
            }
            // 格式2：单引号 'author':'xxx'
            if (author == null) {
                Pattern p2 = Pattern.compile("author'\\s*:\\s*'([^']*)'");
                Matcher m2 = p2.matcher(fullContent);
                if (m2.find()) {
                    author = m2.group(1);
                }
            }
            // 格式3：无引号的纯文本（罕见，但容错）
            if (author == null) {
                Pattern p3 = Pattern.compile("author\\s*:\\s*([^,}\\]]+)");
                Matcher m3 = p3.matcher(fullContent);
                if (m3.find()) {
                    author = m3.group(1).trim();
                }
            }
            this.author = (author != null && !author.isEmpty()) ? author : "未知";
            // 原有页面提取逻辑
            List<String> pages = McFunctionParser.extractPagesFromFile(file.toPath());
            this.rawPages = pages;
            this.pages = pages.size();

            int totalWords = 0;
            for (String page : pages) {
                String cleaned = page.replaceAll("[\\s\\n\\r]+", "");
                totalWords += cleaned.length();
            }
            this.wordCount = totalWords;
        }
    }

    static class BookDetail {
        String title;
        String author;             
        int totalCopies;
        int countOriginal;
        int countCopy;
        int countCopyOfCopy;
        int pages;
        int wordCount;
    }

    public static void generateReport(File functionsDir, File outputDocx) throws IOException {
    if (!functionsDir.exists() || !functionsDir.isDirectory()) {
        throw new IllegalArgumentException("无效的文件夹路径");
    }

    File[] mcFiles = functionsDir.listFiles(f -> f.getName().endsWith(".mcfunction"));
    if (mcFiles == null || mcFiles.length == 0) {
        throw new IllegalArgumentException("文件夹中没有 .mcfunction 文件");
    }

    // 解析所有书籍信息
    List<BookInfo> allBooks = new ArrayList<>();
    for (File f : mcFiles) {
        try {
            allBooks.add(new BookInfo(f));
        } catch (Exception e) {
            System.err.println("解析失败: " + f.getName() + " - " + e.getMessage());
        }
    }

    // 按基础名称分组
    Map<String, List<BookInfo>> groupedByBaseName = new LinkedHashMap<>();
    for (BookInfo book : allBooks) {
        groupedByBaseName.computeIfAbsent(book.baseName, k -> new ArrayList<>()).add(book);
    }

    // 去重并识别独立书籍
    List<BookInfo> uniqueBooksForOutput = new ArrayList<>();
    int totalUniqueContent = 0;
    int totalDuplicateCopies = 0;
    Map<String, Integer> generationCount = new HashMap<>();

    for (Map.Entry<String, List<BookInfo>> entry : groupedByBaseName.entrySet()) {
        List<BookInfo> sameNameBooks = entry.getValue();

        Map<String, List<BookInfo>> byFirstPage = new LinkedHashMap<>();
        for (BookInfo book : sameNameBooks) {
            String firstPage = book.rawPages.isEmpty() ? "" : book.rawPages.get(0);
            byFirstPage.computeIfAbsent(firstPage, k -> new ArrayList<>()).add(book);
        }

        int variantIndex = 0;
        for (Map.Entry<String, List<BookInfo>> contentEntry : byFirstPage.entrySet()) {
            List<BookInfo> identicalBooks = contentEntry.getValue();
            BookInfo representative = identicalBooks.get(0);

            if (variantIndex > 0) {
                representative = new BookInfo(representative);
                representative.title = representative.baseName + "_" + variantIndex;
                representative.fileName = representative.title;
                representative.isDuplicate = false;
            }
            variantIndex++;

            int copies = identicalBooks.size();
            representative.duplicateCount = copies;

            uniqueBooksForOutput.add(representative);
            totalUniqueContent++;
            totalDuplicateCopies += (copies - 1);

            for (BookInfo book : identicalBooks) {
                generationCount.merge(book.generation, 1, Integer::sum);
            }
        }
    }

        // 构建明细数据
        List<BookDetail> details = new ArrayList<>();
        int grandTotalFiles = 0, grandTotalOriginal = 0, grandTotalCopy = 0, grandTotalCopyOfCopy = 0;

        for (BookInfo book : uniqueBooksForOutput) {
            BookDetail detail = new BookDetail();
            detail.title = book.title;
            detail.author = book.author;
            detail.totalCopies = book.duplicateCount;
            detail.pages = book.pages;
            detail.wordCount = book.wordCount;

            String targetFirstPage = book.rawPages.isEmpty() ? "" : book.rawPages.get(0);
            for (BookInfo b : allBooks) {
                String bFirstPage = b.rawPages.isEmpty() ? "" : b.rawPages.get(0);
                if (bFirstPage.equals(targetFirstPage) && b.baseName.equals(book.baseName)) {
                    switch (b.generation) {
                        case "原稿": detail.countOriginal++; break;
                        case "原稿的副本": detail.countCopy++; break;
                        case "副本的副本": detail.countCopyOfCopy++; break;
                    }
                }
            }
            details.add(detail);

            grandTotalFiles += detail.totalCopies;
            grandTotalOriginal += detail.countOriginal;
            grandTotalCopy += detail.countCopy;
            grandTotalCopyOfCopy += detail.countCopyOfCopy;
        }

                // ========== 排序规则：作者（英文先，中文后），同作者按书名（英文先，中文后） ==========
        Collator collator = Collator.getInstance(Locale.CHINA);
        collator.setStrength(Collator.PRIMARY);
        details.sort((d1, d2) -> {
            boolean a1Chinese = d1.author.matches(".*[\\u4e00-\\u9fa5].*");
            boolean a2Chinese = d2.author.matches(".*[\\u4e00-\\u9fa5].*");
            if (a1Chinese && !a2Chinese) return 1;
            if (!a1Chinese && a2Chinese) return -1;
            int authorCmp = collator.compare(d1.author, d2.author);
            if (authorCmp != 0) return authorCmp;

            boolean t1Chinese = d1.title.matches(".*[\\u4e00-\\u9fa5].*");
            boolean t2Chinese = d2.title.matches(".*[\\u4e00-\\u9fa5].*");
            if (t1Chinese && !t2Chinese) return 1;
            if (!t1Chinese && t2Chinese) return -1;
            return collator.compare(d1.title, d2.title);
        });

        // 分组：中文 vs 其他语言
        List<BookDetail> chineseDetails = new ArrayList<>();
        List<BookDetail> otherDetails = new ArrayList<>();
        for (BookDetail d : details) {
            boolean titleChinese = d.title.matches(".*[\\u4e00-\\u9fa5].*");
            boolean authorChinese = d.author.matches(".*[\\u4e00-\\u9fa5].*");
            if (titleChinese || authorChinese) {
                chineseDetails.add(d);
            } else {
                otherDetails.add(d);
            }
        }

        String archiveName = functionsDir.getName().replace("_books_functions", "");
        String basePath = outputDocx.getAbsolutePath();
        String baseName = basePath.substring(0, basePath.lastIndexOf('.'));

        // 分别生成两组报告
        generateOverviewFiles(chineseDetails, baseName + "_总览_中文", archiveName, totalUniqueContent, 
                              grandTotalFiles, grandTotalOriginal, grandTotalCopy, grandTotalCopyOfCopy);
        generateOverviewFiles(otherDetails, baseName + "_总览_其他语言", archiveName, totalUniqueContent,
                              grandTotalFiles, grandTotalOriginal, grandTotalCopy, grandTotalCopyOfCopy);

        // 内容详情文档（不分语言，沿用原逻辑）
        File contentDocx = new File(baseName + "_内容.docx");
        generateContentDocx(uniqueBooksForOutput, contentDocx, archiveName);

        JOptionPane.showMessageDialog(null,
            "报告生成成功！\n" +
            "中文总览 Word：" + baseName + "_总览_中文.docx\n" +
            "中文总览 Excel：" + baseName + "_总览_中文.xlsx\n" +
            "其他语言总览 Word：" + baseName + "_总览_其他语言.docx\n" +
            "其他语言总览 Excel：" + baseName + "_总览_其他语言.xlsx\n" +
            "内容详情：" + contentDocx.getAbsolutePath());
    }

    /**
     * 生成指定分组的总览文件（Word + Excel）
     */
    private static void generateOverviewFiles(List<BookDetail> details, String baseName, 
                                              String archiveName, int totalUniqueContent,
                                              int grandTotalFiles, int grandTotalOriginal, 
                                              int grandTotalCopy, int grandTotalCopyOfCopy) throws IOException {
        if (details.isEmpty()) {
            return; // 如果该分组没有书籍，不生成空文件
        }

        // 计算该分组的汇总数据
        int groupTotalFiles = 0, groupOriginal = 0, groupCopy = 0, groupCopyOfCopy = 0;
        for (BookDetail d : details) {
            groupTotalFiles += d.totalCopies;
            groupOriginal += d.countOriginal;
            groupCopy += d.countCopy;
            groupCopyOfCopy += d.countCopyOfCopy;
        }
        int groupUniqueContent = details.size();

        // 计算作者列合并信息
        Map<Integer, int[]> mergeInfo = new LinkedHashMap<>();
        if (!details.isEmpty()) {
            String currentAuthor = details.get(0).author;
            int startIdx = 0;
            int count = 1;
            for (int i = 1; i < details.size(); i++) {
                if (details.get(i).author.equals(currentAuthor)) {
                    count++;
                } else {
                    mergeInfo.put(startIdx, new int[]{count});
                    currentAuthor = details.get(i).author;
                    startIdx = i;
                    count = 1;
                }
            }
            mergeInfo.put(startIdx, new int[]{count});
        }

        File overviewDocx = new File(baseName + ".docx");
        File overviewXlsx = new File(baseName + ".xlsx");

        // 生成 Word 总览
        try (XWPFDocument doc = new XWPFDocument()) {
            if (doc.getStyles() == null) doc.createStyles();

            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("《" + archiveName + "》成书提取报告（总览）");
            titleRun.setBold(true);
            titleRun.setFontSize(24);
            titleRun.setFontFamily("宋体");

            doc.createParagraph();
            XWPFParagraph tableTitle = doc.createParagraph();
            XWPFRun tableTitleRun = tableTitle.createRun();
            tableTitleRun.setText("统计总览");
            tableTitleRun.setBold(true);
            tableTitleRun.setFontSize(16);
            tableTitleRun.setFontFamily("宋体");

            XWPFTable table = doc.createTable(details.size() + 2, 9);
            table.setWidth("100%");
            setTableStyle(table);

            String[] headers = {"序号", "书名", "作者", "总副本数", "原稿", "原稿的副本", "副本的副本", "页数", "字数"};
            XWPFTableRow headerRow = table.getRow(0);
            for (int i = 0; i < headers.length; i++) {
                setCellText(headerRow.getCell(i), headers[i], true);
            }

            int rowIdx = 1, seq = 1;
            for (BookDetail d : details) {
                XWPFTableRow row = table.getRow(rowIdx++);
                setCellText(row.getCell(0), String.valueOf(seq++), false);
                setCellText(row.getCell(1), d.title, false);
                setCellText(row.getCell(2), d.author, false);
                setCellText(row.getCell(3), String.valueOf(d.totalCopies), false);
                setCellText(row.getCell(4), String.valueOf(d.countOriginal), false);
                setCellText(row.getCell(5), String.valueOf(d.countCopy), false);
                setCellText(row.getCell(6), String.valueOf(d.countCopyOfCopy), false);
                setCellText(row.getCell(7), String.valueOf(d.pages), false);
                setCellText(row.getCell(8), String.valueOf(d.wordCount), false);
            }

            // 作者列合并
            for (Map.Entry<Integer, int[]> entry : mergeInfo.entrySet()) {
                int startRow = entry.getKey() + 1;
                int rowCount = entry.getValue()[0];
                if (rowCount > 1) {
                    XWPFTableCell startCell = table.getRow(startRow).getCell(2);
                    CTTc ctTc = startCell.getCTTc();
                    if (ctTc.getTcPr() == null) ctTc.addNewTcPr();
                    CTVMerge vMerge = ctTc.getTcPr().addNewVMerge();
                    vMerge.setVal(STMerge.RESTART);
                    for (int r = 1; r < rowCount; r++) {
                        XWPFTableCell continueCell = table.getRow(startRow + r).getCell(2);
                        CTTc ctTcContinue = continueCell.getCTTc();
                        if (ctTcContinue.getTcPr() == null) ctTcContinue.addNewTcPr();
                        CTVMerge vMergeContinue = ctTcContinue.getTcPr().addNewVMerge();
                        vMergeContinue.setVal(STMerge.CONTINUE);
                    }
                }
            }

            XWPFTableRow totalRow = table.getRow(rowIdx);
            setCellText(totalRow.getCell(0), "合计", true);
            setCellText(totalRow.getCell(1), "本组独立书籍数：" + groupUniqueContent, true);
            setCellText(totalRow.getCell(2), "—", true);
            setCellText(totalRow.getCell(3), String.valueOf(groupTotalFiles), true);
            setCellText(totalRow.getCell(4), String.valueOf(groupOriginal), true);
            setCellText(totalRow.getCell(5), String.valueOf(groupCopy), true);
            setCellText(totalRow.getCell(6), String.valueOf(groupCopyOfCopy), true);
            setCellText(totalRow.getCell(7), "—", true);
            setCellText(totalRow.getCell(8), "—", true);

            try (FileOutputStream out = new FileOutputStream(overviewDocx)) {
                doc.write(out);
            }
        }

        // 生成 Excel 总览
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("成书统计总览");
            Row headerRow = sheet.createRow(0);
            String[] headers = {"序号", "书名", "作者", "总副本数", "原稿", "原稿的副本", "副本的副本", "页数", "字数"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowNum = 1, seq = 1;
            for (BookDetail d : details) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(seq++);
                row.createCell(1).setCellValue(d.title);
                row.createCell(2).setCellValue(d.author);
                row.createCell(3).setCellValue(d.totalCopies);
                row.createCell(4).setCellValue(d.countOriginal);
                row.createCell(5).setCellValue(d.countCopy);
                row.createCell(6).setCellValue(d.countCopyOfCopy);
                row.createCell(7).setCellValue(d.pages);
                row.createCell(8).setCellValue(d.wordCount);
            }

            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(0).setCellValue("合计");
            totalRow.createCell(1).setCellValue("本组独立书籍数：" + groupUniqueContent);
            totalRow.createCell(2).setCellValue("—");
            totalRow.createCell(3).setCellValue(groupTotalFiles);
            totalRow.createCell(4).setCellValue(groupOriginal);
            totalRow.createCell(5).setCellValue(groupCopy);
            totalRow.createCell(6).setCellValue(groupCopyOfCopy);
            totalRow.createCell(7).setCellValue("—");
            totalRow.createCell(8).setCellValue("—");

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream out = new FileOutputStream(overviewXlsx)) {
                workbook.write(out);
            }
        }
    }

    /**
     * 生成内容详情文档（不分语言）
     */
    private static void generateContentDocx(List<BookInfo> uniqueBooksForOutput, File outputDocx, String archiveName) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            if (doc.getStyles() == null) doc.createStyles();

            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("《" + archiveName + "》成书内容详情");
            titleRun.setBold(true);
            titleRun.setFontSize(24);
            titleRun.setFontFamily("宋体");

            doc.createParagraph().setPageBreak(true);

            BookConfig config = new BookConfig(14, 57.0, null, null);
            TextFormatter formatter = new TextFormatter(config);

            int bookIndex = 1;
            for (BookInfo book : uniqueBooksForOutput) {
                XWPFParagraph bookTitlePara = doc.createParagraph();
                bookTitlePara.setStyle("Heading1");
                bookTitlePara.getCTP().getPPr().addNewOutlineLvl().setVal(java.math.BigInteger.valueOf(0));
                XWPFRun bookTitleRun = bookTitlePara.createRun();
                String displayTitle = bookIndex + ". 《" + book.title + "》";
                if (book.duplicateCount > 1) {
                    displayTitle += " [共 " + book.duplicateCount + " 本相同内容]";
                }
                bookTitleRun.setText(displayTitle);
                bookTitleRun.setBold(true);
                bookTitleRun.setFontSize(14);
                bookTitleRun.setFontFamily("宋体");

                XWPFTable infoTable = doc.createTable(6, 2);
                infoTable.setWidth("80%");
                setCellText(infoTable.getRow(0).getCell(0), "文件名", true);
                setCellText(infoTable.getRow(0).getCell(1), book.fileName + ".mcfunction", false);
                setCellText(infoTable.getRow(1).getCell(0), "作者", true);
                setCellText(infoTable.getRow(1).getCell(1), book.author, false);
                setCellText(infoTable.getRow(2).getCell(0), "类型", true);
                setCellText(infoTable.getRow(2).getCell(1), book.generation, false);
                setCellText(infoTable.getRow(3).getCell(0), "页数", true);
                setCellText(infoTable.getRow(3).getCell(1), String.valueOf(book.pages), false);
                setCellText(infoTable.getRow(4).getCell(0), "字数", true);
                setCellText(infoTable.getRow(4).getCell(1), String.valueOf(book.wordCount), false);
                setCellText(infoTable.getRow(5).getCell(0), "相同副本数", true);
                setCellText(infoTable.getRow(5).getCell(1), String.valueOf(book.duplicateCount), false);
                doc.createParagraph();

                for (int p = 0; p < book.rawPages.size(); p++) {
                    XWPFParagraph pagePara = doc.createParagraph();
                    XWPFRun pageRun = pagePara.createRun();
                    pageRun.setText("======== 第 " + (p + 1) + " 页 ========");
                    pageRun.setBold(true);
                    pageRun.setFontSize(10);
                    pageRun.setColor("4F81BD");

                    String rawPage = book.rawPages.get(p);
                    List<String> lines = formatter.splitIntoLines(rawPage);
                    int start = 0, end = lines.size();
                    while (start < end && lines.get(start).trim().isEmpty()) start++;
                    while (end > start && lines.get(end - 1).trim().isEmpty()) end--;
                    List<String> trimmedLines = lines.subList(start, end);

                    for (String line : trimmedLines) {
                    String trimmedLine = line.trim();
                    // 过滤掉视觉垃圾行：单独的反斜杠、括号等
                    if (trimmedLine.matches("^[\\\\{}\\[\\]:,]+$")) {
                        continue;
                    }
                    XWPFParagraph linePara = doc.createParagraph();
                    linePara.setSpacingAfter(0);
                    linePara.setSpacingBefore(0);
                    XWPFRun lineRun = linePara.createRun();
                    lineRun.setText(line);
                    lineRun.setFontSize(10);
                    lineRun.setFontFamily("宋体");
                    }
                }

                if (bookIndex < uniqueBooksForOutput.size()) {
                    doc.createParagraph().setPageBreak(true);
                }
                bookIndex++;
            }

            try (FileOutputStream out = new FileOutputStream(outputDocx)) {
                doc.write(out);
            }
        }
    }


    private static void setCellText(XWPFTableCell cell, String text, boolean bold) {
        XWPFParagraph para = cell.getParagraphs().get(0);
        para.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontSize(10);
        run.setFontFamily("宋体");
    }

    private static void setTableStyle(XWPFTable table) {
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() == null ? ctTbl.addNewTblPr() : ctTbl.getTblPr();
        CTTblBorders borders = tblPr.addNewTblBorders();
        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.addNewInsideV().setVal(STBorder.SINGLE);
    }
}