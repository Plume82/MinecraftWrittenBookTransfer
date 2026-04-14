package com.bookexport;

import com.common.McFunctionParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * .mcfunction 导出为.txt纯文本
 */
public class McFunctionExporter {

    /**
     * 将 .mcfunction 中的成书内容导出为单个 .txt 文件
     * @param mcFunctionFile 输入文件
     * @param outputFile 输出文件
     * @return 是否成功
     * @throws IOException 文件读写异常
     */
    public static boolean exportToTextFile(Path mcFunctionFile, Path outputFile) throws IOException {
        List<String> pages = McFunctionParser.extractPagesFromFile(mcFunctionFile);
        if (pages.isEmpty()) {
            return false;
        }

        // 合并所有页面文本
        String fullText = String.join("", pages);
        Files.writeString(outputFile, fullText);
        return true;
    }
}