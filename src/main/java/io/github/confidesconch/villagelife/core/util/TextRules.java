package io.github.confidesconch.villagelife.core.util;

/** Cleaning of names typed by players (villages, waypoints, zones): no colour codes, no control characters, limited length. */
public final class TextRules {
    private TextRules() {}

    /** Removes formatting codes and control characters, trims, and cuts to {@code maxLength}. */
    public static String cleanName(String name, int maxLength) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "").trim();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength).trim() : cleaned;
    }
}
