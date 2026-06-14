package io.newsworld.dashboard.util;

public final class FlagUtils {

    private FlagUtils() {}

    /** Convertit un code ISO 3166-1 alpha-2 (ex: "FR") en emoji drapeau (ex: "🇫🇷"). */
    public static String flag(String code) {
        if (code == null || code.length() != 2) return "";
        String upper = code.toUpperCase();
        int first  = upper.codePointAt(0) - 'A' + 0x1F1E6;
        int second = upper.codePointAt(1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    /** Convertit une liste "FR, DE, JP" en drapeaux "🇫🇷 🇩🇪 🇯🇵". */
    public static String flags(String codesCsv) {
        if (codesCsv == null || codesCsv.isBlank()) return "";
        String[] codes = codesCsv.split(",\\s*");
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(codes.length, 12);
        for (int i = 0; i < limit; i++) {
            String f = flag(codes[i].trim());
            if (!f.isEmpty()) sb.append(f).append(" ");
        }
        if (codes.length > 12) sb.append("…");
        return sb.toString().trim();
    }
}
