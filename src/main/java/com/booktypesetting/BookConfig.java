package com.booktypesetting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookConfig {

    private final int linesPerPage;
    private final double maxLineWidth;
    private final Map<Character, Double> charWidths;
    private final List<String> chinesePunctuation;

    public BookConfig(int linesPerPage, double maxLineWidth,
                      Map<Character, Double> charWidths,
                      List<String> chinesePunctuation) {
        this.linesPerPage = linesPerPage;
        this.maxLineWidth = maxLineWidth;
        this.charWidths = (charWidths != null) ? charWidths : defaultCharWidths();
        this.chinesePunctuation = (chinesePunctuation != null) ? chinesePunctuation : defaultChinesePunctuation();
    }

    public int getLinesPerPage() { return linesPerPage; }
    public double getMaxLineWidth() { return maxLineWidth; }
    public Map<Character, Double> getCharWidths() { return charWidths; }
    public List<String> getChinesePunctuation() { return chinesePunctuation; }

    private static Map<Character, Double> defaultCharWidths() {
        Map<Character, Double> map = new HashMap<>();
        map.put('`', 1.5);
        map.put('[', 2.0); map.put(']', 2.0); map.put('(', 2.0); map.put(')', 2.0);
        map.put('"', 2.0); map.put('{', 2.0); map.put('}', 2.0); map.put('*', 2.0);
        map.put(' ', 2.0);
        map.put('.', 1.0); map.put(',', 1.0); map.put(';', 1.0); map.put(':', 1.0);
        map.put('\'', 1.0); map.put('!', 1.0); map.put('|', 1.0);
        map.put('<', 2.5); map.put('>', 2.5);
        map.put('→', 4.0); map.put('~', 4.0);
        map.put('—', 4.5);
        map.put('\n', -1.0);
        return map;
    }

    private static List<String> defaultChinesePunctuation() {
        return List.of(
                "，", "。", "、", "？", "！", "】", "【", "（", "）",
                "·", "；", "：", "“", "‘", "《", "》", "…"
        );
    }
}