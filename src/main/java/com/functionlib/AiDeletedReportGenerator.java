package com.functionlib;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import com.booktypesetting.BookConfig;
import com.booktypesetting.TextFormatter;

import java.io.*;
import java.util.*;

/**
 * AI 价值过滤完整报告生成器
 * 同时记录被 AI 判定为低价值（已删除）和高价值（保留）的书籍，包含评分、评语及全文内容
 */
public class AiDeletedReportGenerator {

    /**
     * 生成完整报告（包含已删除和保留的书籍）
     * @param deletedBooks  被删除的书籍列表
     * @param keptBooks     保留的书籍列表
     * @param assessments   每本书对应的 AI 评估结果
     * @param contentMap    预先读取的书籍内容映射（键为文件名）
     * @param outputFile    输出的 Word 文件
     */
    public static void generateFullReport(List<FunctionlibApp.BookEntry> deletedBooks,
                                          List<FunctionlibApp.BookEntry> keptBooks,
                                          Map<FunctionlibApp.BookEntry, DeepSeekAnalyzer.ValueAssessment> assessments,
                                          Map<String, String> contentMap,
                                          File outputFile) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            // 标题
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("AI 价值过滤完整报告");
            titleRun.setBold(true);
            titleRun.setFontSize(24);
            titleRun.setFontFamily("宋体");

            // 生成时间
            doc.createParagraph();
            XWPFParagraph timePara = doc.createParagraph();
            XWPFRun timeRun = timePara.createRun();
            timeRun.setText("生成时间：" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            timeRun.setFontSize(12);
            timeRun.setFontFamily("宋体");
            doc.createParagraph();

            // ==================== 一、已删除书籍统计总览 ====================
            XWPFParagraph deletedTableTitle = doc.createParagraph();
            XWPFRun deletedTableTitleRun = deletedTableTitle.createRun();
            deletedTableTitleRun.setText("一、已删除书籍统计总览");
            deletedTableTitleRun.setBold(true);
            deletedTableTitleRun.setFontSize(16);
            deletedTableTitleRun.setFontFamily("宋体");

            XWPFTable deletedOverviewTable = doc.createTable(deletedBooks.size() + 2, 7);
            deletedOverviewTable.setWidth("100%");
            setTableStyle(deletedOverviewTable);

            String[] deletedHeaders = {"序号", "书名", "作者", "世代", "AI评分", "AI评语", "状态"};
            XWPFTableRow deletedHeaderRow = deletedOverviewTable.getRow(0);
            for (int i = 0; i < deletedHeaders.length; i++) {
                setCellText(deletedHeaderRow.getCell(i), deletedHeaders[i], true);
            }

            int rowIdx = 1;
            int seq = 1;
            for (FunctionlibApp.BookEntry book : deletedBooks) {
                XWPFTableRow row = deletedOverviewTable.getRow(rowIdx++);
                DeepSeekAnalyzer.ValueAssessment va = assessments.get(book);
                setCellText(row.getCell(0), String.valueOf(seq++), false);
                setCellText(row.getCell(1), truncate(book.getTitle(), 20), false);
                setCellText(row.getCell(2), truncate(book.getAuthor(), 15), false);
                setCellText(row.getCell(3), book.getGeneration(), false);
                setCellText(row.getCell(4), va != null ? String.valueOf(va.score) : "N/A", false);
                setCellText(row.getCell(5), va != null ? truncate(va.comment, 30) : "", false);
                setCellText(row.getCell(6), "已删除", false);
            }

            XWPFTableRow deletedTotalRow = deletedOverviewTable.getRow(rowIdx);
            setCellText(deletedTotalRow.getCell(0), "合计", true);
            setCellText(deletedTotalRow.getCell(1), "共 " + deletedBooks.size() + " 本", true);
            setCellText(deletedTotalRow.getCell(2), "—", true);
            setCellText(deletedTotalRow.getCell(3), "—", true);
            setCellText(deletedTotalRow.getCell(4), "—", true);
            setCellText(deletedTotalRow.getCell(5), "—", true);
            setCellText(deletedTotalRow.getCell(6), "—", true);

            doc.createParagraph().setPageBreak(true);

            // ==================== 二、保留书籍统计总览 ====================
            XWPFParagraph keptTableTitle = doc.createParagraph();
            XWPFRun keptTableTitleRun = keptTableTitle.createRun();
            keptTableTitleRun.setText("二、保留书籍统计总览");
            keptTableTitleRun.setBold(true);
            keptTableTitleRun.setFontSize(16);
            keptTableTitleRun.setFontFamily("宋体");

            XWPFTable keptOverviewTable = doc.createTable(keptBooks.size() + 2, 7);
            keptOverviewTable.setWidth("100%");
            setTableStyle(keptOverviewTable);

            String[] keptHeaders = {"序号", "书名", "作者", "世代", "AI评分", "AI评语", "状态"};
            XWPFTableRow keptHeaderRow = keptOverviewTable.getRow(0);
            for (int i = 0; i < keptHeaders.length; i++) {
                setCellText(keptHeaderRow.getCell(i), keptHeaders[i], true);
            }

            rowIdx = 1;
            seq = 1;
            for (FunctionlibApp.BookEntry book : keptBooks) {
                XWPFTableRow row = keptOverviewTable.getRow(rowIdx++);
                DeepSeekAnalyzer.ValueAssessment va = assessments.get(book);
                setCellText(row.getCell(0), String.valueOf(seq++), false);
                setCellText(row.getCell(1), truncate(book.getTitle(), 20), false);
                setCellText(row.getCell(2), truncate(book.getAuthor(), 15), false);
                setCellText(row.getCell(3), book.getGeneration(), false);
                setCellText(row.getCell(4), va != null ? String.valueOf(va.score) : "N/A", false);
                setCellText(row.getCell(5), va != null ? truncate(va.comment, 30) : "", false);
                setCellText(row.getCell(6), "保留", false);
            }

            XWPFTableRow keptTotalRow = keptOverviewTable.getRow(rowIdx);
            setCellText(keptTotalRow.getCell(0), "合计", true);
            setCellText(keptTotalRow.getCell(1), "共 " + keptBooks.size() + " 本", true);
            setCellText(keptTotalRow.getCell(2), "—", true);
            setCellText(keptTotalRow.getCell(3), "—", true);
            setCellText(keptTotalRow.getCell(4), "—", true);
            setCellText(keptTotalRow.getCell(5), "—", true);
            setCellText(keptTotalRow.getCell(6), "—", true);

            doc.createParagraph().setPageBreak(true);

            // ==================== 三、书籍内容详情 ====================
            XWPFParagraph detailTitle = doc.createParagraph();
            XWPFRun detailRun = detailTitle.createRun();
            detailRun.setText("三、书籍内容详情");
            detailRun.setBold(true);
            detailRun.setFontSize(16);
            detailRun.setFontFamily("宋体");
            doc.createParagraph();

            BookConfig config = new BookConfig(14, 57.0, null, null);
            TextFormatter formatter = new TextFormatter(config);

            // 合并两个列表，先输出已删除，再输出保留（或按任意顺序）
            List<FunctionlibApp.BookEntry> allProcessedBooks = new ArrayList<>();
            allProcessedBooks.addAll(deletedBooks);
            allProcessedBooks.addAll(keptBooks);

            int bookIndex = 1;
            for (FunctionlibApp.BookEntry book : allProcessedBooks) {
                boolean isDeleted = deletedBooks.contains(book);
                String statusLabel = isDeleted ? "【已删除】" : "【保留】";
                
                // 书籍标题（导航窗格）
                XWPFParagraph bookTitlePara = doc.createParagraph();
                bookTitlePara.setStyle("Heading1");
                bookTitlePara.getCTP().getPPr().addNewOutlineLvl().setVal(java.math.BigInteger.valueOf(0));
                XWPFRun bookTitleRun = bookTitlePara.createRun();
                DeepSeekAnalyzer.ValueAssessment va = assessments.get(book);
                String displayTitle = bookIndex + ". 《" + book.getTitle() + "》 " + statusLabel;
                if (va != null) {
                    displayTitle += " [AI评分: " + va.score + "]";
                }
                bookTitleRun.setText(displayTitle);
                bookTitleRun.setBold(true);
                bookTitleRun.setFontSize(14);
                bookTitleRun.setFontFamily("宋体");

                // 书籍信息表格
                XWPFTable infoTable = doc.createTable(5, 2);
                infoTable.setWidth("80%");
                setCellText(infoTable.getRow(0).getCell(0), "文件名", true);
                setCellText(infoTable.getRow(0).getCell(1), book.getFileName(), false);
                setCellText(infoTable.getRow(1).getCell(0), "作者", true);
                setCellText(infoTable.getRow(1).getCell(1), book.getAuthor(), false);
                setCellText(infoTable.getRow(2).getCell(0), "世代", true);
                setCellText(infoTable.getRow(2).getCell(1), book.getGeneration(), false);
                setCellText(infoTable.getRow(3).getCell(0), "AI 评分", true);
                setCellText(infoTable.getRow(3).getCell(1), va != null ? String.valueOf(va.score) : "N/A", false);
                setCellText(infoTable.getRow(4).getCell(0), "AI 评语", true);
                setCellText(infoTable.getRow(4).getCell(1), va != null ? va.comment : "", false);
                doc.createParagraph();

                // 输出全文内容（从缓存中获取）
                String fullContent = contentMap.get(book.getFileName());
                if (fullContent == null || fullContent.isEmpty()) {
                    fullContent = "（内容缺失）";
                }

                List<String> lines = formatter.splitIntoLines(fullContent);
                int start = 0, end = lines.size();
                while (start < end && lines.get(start).trim().isEmpty()) start++;
                while (end > start && lines.get(end - 1).trim().isEmpty()) end--;
                List<String> trimmedLines = lines.subList(start, end);

                for (String line : trimmedLines) {
                    String trimmedLine = line.trim();
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

                // 输出译文（如果存在）
                if (va != null && va.translation != null && !va.translation.isEmpty()) {
                    doc.createParagraph();   // 空行分隔
                    XWPFParagraph transTitlePara = doc.createParagraph();
                    XWPFRun transTitleRun = transTitlePara.createRun();
                    transTitleRun.setText("【AI 翻译】");
                    transTitleRun.setBold(true);
                    transTitleRun.setFontSize(11);
                    transTitleRun.setFontFamily("宋体");
                    transTitleRun.setColor("4F81BD");

                    List<String> transLines = formatter.splitIntoLines(va.translation);
                    for (String line : transLines) {
                        XWPFParagraph linePara = doc.createParagraph();
                        linePara.setSpacingAfter(0);
                        linePara.setSpacingBefore(0);
                        XWPFRun lineRun = linePara.createRun();
                        lineRun.setText(line);
                        lineRun.setFontSize(10);
                        lineRun.setFontFamily("宋体");
                        lineRun.setColor("4F81BD");
                    }
                }

                if (bookIndex < allProcessedBooks.size()) {
                    doc.createParagraph().setPageBreak(true);
                }
                bookIndex++;
            }

            try (FileOutputStream out = new FileOutputStream(outputFile)) {
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

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}