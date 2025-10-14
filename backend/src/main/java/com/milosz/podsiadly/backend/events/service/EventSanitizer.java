package com.milosz.podsiadly.backend.events.service;

import java.net.URI;
import java.text.Normalizer;
import java.util.*;

public final class EventSanitizer {
    private EventSanitizer(){}

    public static String canonicalizeUrl(String in) {
        if (in == null || in.isBlank()) return null;
        try {
            var uri = new URI(in.trim());
            String scheme = optLower(uri.getScheme(), "https");
            String host   = optLower(uri.getHost(), null);
            int port      = uri.getPort();
            boolean defaultPort = port == -1 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
            String path   = uri.getPath() == null ? "" : uri.getPath();
            if (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length()-1);

            String query = uri.getRawQuery();
            if (query != null && !query.isBlank()) {
                var parts = new ArrayList<>(Arrays.asList(query.split("&")));
                parts.sort(String::compareTo);
                query = String.join("&", parts);
            }

            var sb = new StringBuilder();
            sb.append(scheme).append("://");
            if (host != null) sb.append(host);
            if (!defaultPort && port != -1) sb.append(":").append(port);
            sb.append(path);
            if (query != null && !query.isBlank()) sb.append("?").append(query);
            return sb.toString();
        } catch (Exception e) {
            return in.trim().replaceAll("\\s+", "");
        }
    }

    public static String canonicalExternalId(String source, String externalId, String url) {
        String base = (externalId != null && !externalId.isBlank()) ? externalId.trim() : canonicalizeUrl(url);
        return base == null ? null : canonicalizeUrl(base);
    }

    public static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static String titleCase(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFC).trim().replaceAll("\\s{2,}", " ");
        StringBuilder out = new StringBuilder(n.length());
        boolean start = true;
        for (int i=0;i<n.length();i++) {
            char c = n.charAt(i);
            if (start && Character.isLetter(c)) { out.append(Character.toUpperCase(c)); start=false; }
            else out.append(Character.toLowerCase(c));
            if (c==' ' || c=='-' || c=='\'' || c=='/') start = true;
        }
        return out.toString();
    }

    public static String squash(String s) {
        if (s == null) return null;
        return s.trim().replaceAll("\\s{2,}", " ");
    }

    private static String optLower(String s, String def) { return s != null ? s.toLowerCase(Locale.ROOT) : def; }
}
