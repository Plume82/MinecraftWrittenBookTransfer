package com.booktypesetting;

import java.util.ArrayList;
import java.util.List;

public class TextFormatter {

    private final BookConfig config;

    public TextFormatter(BookConfig config) {
        this.config = config;
    }

    private boolean isChinese(char ch) {
        if (config.getChinesePunctuation().contains(String.valueOf(ch))) {
            return true;
        }
        return ch >= '\u4e00' && ch <= '\u9fff';
    }

    private double getCharWidth(char ch) {
        if (config.getCharWidths().containsKey(ch)) {
            return config.getCharWidths().get(ch);
        }
        if (isChinese(ch)) {
            return 4.5;
        }
        return 3.0;
    }

    public List<String> splitIntoLines(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        double currentWidth = 0.0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            double charWidth = getCharWidth(ch);

            if (ch == '\n') {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
                currentWidth = 0.0;
                continue;
            }

            if (currentWidth + charWidth > config.getMaxLineWidth()) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
                currentLine.append(ch);
                currentWidth = charWidth;
            } else {
                currentLine.append(ch);
                currentWidth += charWidth;
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return processTrailingNewlines(text, lines);
    }

    private List<String> processTrailingNewlines(String original, List<String> lines) {
        if (original.endsWith("\n")) {
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            if (!lines.isEmpty()) {
                String last = lines.get(lines.size() - 1);
                lines.set(lines.size() - 1, last.replaceAll("\\n$", ""));
            }
        }
        return lines;
    }

    public List<String> formatPages(List<String> lines) {
        List<String> pages = new ArrayList<>();
        List<String> currentPage = new ArrayList<>();

        for (String line : lines) {
            currentPage.add(line);
            if (currentPage.size() >= config.getLinesPerPage()) {
                pages.add(String.join("\n", currentPage));
                currentPage.clear();
            }
        }

        if (!currentPage.isEmpty()) {
            pages.add(String.join("\n", currentPage));
        }

        return pages;
    }
}