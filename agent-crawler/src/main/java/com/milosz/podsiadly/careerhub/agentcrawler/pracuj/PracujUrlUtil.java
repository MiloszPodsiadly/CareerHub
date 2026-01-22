package com.milosz.podsiadly.careerhub.agentcrawler.pracuj;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PracujUrlUtil {
    private PracujUrlUtil() {}

    private static final Pattern OFFER_ID = Pattern.compile("(?:,oferta,|%2Coferta%2C)(\\d+)");

    public static String extractOfferId(String url) {
        if (url == null) return null;
        Matcher m = OFFER_ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static String toAbs(String href) {
        if (href == null || href.isBlank()) return null;
        String h = href.trim();
        if (h.startsWith("http")) return h;
        if (h.startsWith("/")) return "https://it.pracuj.pl" + h;
        return "https://it.pracuj.pl/" + h;
    }

    public static String normalize(String url) {
        if (url == null) return null;
        String u = url.trim();

        int q = u.indexOf('?');

        if (q >= 0) u = u.substring(0, q);
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.contains("%2C") && u.contains(",")) {
            u = u.replace(",", "%2C");
        }

        return u;
    }
}
