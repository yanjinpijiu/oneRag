package com.onerag.chat.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 chat/completions 中 assistant message 的可见文本（兼容 Qwen3 等 reasoning 模型）。
 */
public final class SiliconFlowAssistantText {
    private static final String[] ANSWER_MARKERS = new String[] {
            "Revised Draft 2:",
            "Revised Draft:",
            "Final Answer:",
            "**结论**",
            "结论："
    };
    private static final Pattern CONCLUSION_BLOCK = Pattern.compile("(\\*\\*结论\\*\\*[\\s\\S]*?\\*\\*依据与说明\\*\\*[\\s\\S]*)");
    private static final Pattern TRAILING_ANALYSIS = Pattern.compile(
            "(?is)\\n(?:\\*+\\s*)?(Count\\s*Check|Total\\s*:|Let\\'s\\s*count|I\\s+need\\s+to|Actually,|Wait,).*");

    private SiliconFlowAssistantText() {
    }

    public static String fromMessage(JsonObject message) {
        if (message == null) {
            return "";
        }
        String fromContent = parseContentElement(message.get("content"));
        if (fromContent != null && !fromContent.isBlank()) {
            return fromContent.trim();
        }
        String reasoning = firstNonBlankString(message, "reasoning_content", "reasoning", "thinking");
        return reasoning != null ? reasoning.trim() : "";
    }

    public static String fromMessageContentOnly(JsonObject message) {
        if (message == null) {
            return "";
        }
        String fromContent = parseContentElement(message.get("content"));
        return fromContent == null ? "" : fromContent.trim();
    }

    public static String extractFinalAnswer(String reasoning) {
        if (reasoning == null || reasoning.isBlank()) {
            return "";
        }
        String candidate = extractFinalAnswerForStreaming(reasoning);
        if (!candidate.isBlank()) {
            return cleanupAnswer(candidate);
        }
        return "";
    }

    public static String extractFinalAnswerForStreaming(String reasoning) {
        if (reasoning == null || reasoning.isBlank()) {
            return "";
        }
        String text = reasoning.trim();
        for (String marker : ANSWER_MARKERS) {
            int idx = text.indexOf(marker);
            if (idx >= 0) {
                return text.substring(idx + marker.length()).trim();
            }
        }
        Matcher matcher = CONCLUSION_BLOCK.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private static String firstNonBlankString(JsonObject o, String... keys) {
        for (String k : keys) {
            if (!o.has(k) || o.get(k).isJsonNull()) {
                continue;
            }
            JsonElement e = o.get(k);
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                String s = e.getAsString();
                if (s != null && !s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static String parseContentElement(JsonElement content) {
        if (content == null || content.isJsonNull()) {
            return null;
        }
        if (content.isJsonPrimitive() && content.getAsJsonPrimitive().isString()) {
            return content.getAsString();
        }
        if (content.isJsonArray()) {
            JsonArray arr = content.getAsJsonArray();
            StringBuilder sb = new StringBuilder();
            for (JsonElement part : arr) {
                if (!part.isJsonObject()) {
                    continue;
                }
                JsonObject o = part.getAsJsonObject();
                if (o.has("text") && o.get("text").isJsonPrimitive()) {
                    sb.append(o.get("text").getAsString());
                }
            }
            String s = sb.toString();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    private static String cleanupAnswer(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim();
        Matcher trailing = TRAILING_ANALYSIS.matcher(cleaned);
        if (trailing.find()) {
            cleaned = cleaned.substring(0, trailing.start()).trim();
        }
        return cleaned;
    }
}
