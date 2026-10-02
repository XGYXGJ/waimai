package com.waimai.common.util;

import java.util.HashMap;
import java.util.Map;

/**
 * DFA 敏感词过滤（内置小词表，可扩展）。
 */
public class SensitiveWordUtil {

    private static final String[] WORDS = {
            "傻逼", "妈的", "他妈的", "滚蛋", "废物", "垃圾人", "去死", "脑残",
            "加微信", "加qq", "兼职", "刷单", "代购", "低价出售", "私聊", "货到付款",
            "赌博", "博彩", "色情", "毒品"
    };

    private static final Map<Character, Object> TRIE = new HashMap<>();
    private static final Map<Object, Boolean> END = new HashMap<>();

    static {
        for (String w : WORDS) {
            Map<Character, Object> node = TRIE;
            for (int i = 0; i < w.length(); i++) {
                char c = w.charAt(i);
                @SuppressWarnings("unchecked")
                Map<Character, Object> next = (Map<Character, Object>) node.get(c);
                if (next == null) {
                    next = new HashMap<>();
                    node.put(c, next);
                }
                node = next;
                if (i == w.length() - 1) END.put(node, true);
            }
        }
    }

    /** 是否包含敏感词 */
    public static boolean contains(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            @SuppressWarnings("unchecked")
            Map<Character, Object> node = (Map<Character, Object>) TRIE.get(text.charAt(i));
            if (node == null) continue;
            Map<Character, Object> cur = node;
            int j = i + 1;
            if (END.containsKey(cur)) return true;
            while (j < text.length()) {
                @SuppressWarnings("unchecked")
                Map<Character, Object> next = (Map<Character, Object>) cur.get(text.charAt(j));
                if (next == null) break;
                cur = next;
                j++;
                if (END.containsKey(cur)) return true;
            }
        }
        return false;
    }

    /** 将敏感词替换为 * */
    public static String filter(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text);
        for (int i = 0; i < text.length(); i++) {
            @SuppressWarnings("unchecked")
            Map<Character, Object> node = (Map<Character, Object>) TRIE.get(text.charAt(i));
            if (node == null) continue;
            Map<Character, Object> cur = node;
            int j = i + 1;
            if (END.containsKey(cur)) { sb.setCharAt(i, '*'); continue; }
            while (j < text.length()) {
                @SuppressWarnings("unchecked")
                Map<Character, Object> next = (Map<Character, Object>) cur.get(text.charAt(j));
                if (next == null) break;
                cur = next;
                j++;
                if (END.containsKey(cur)) {
                    for (int k = i; k < j; k++) sb.setCharAt(k, '*');
                    break;
                }
            }
        }
        return sb.toString();
    }
}
