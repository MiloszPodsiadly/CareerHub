package com.milosz.podsiadly.backend.ingest.parser;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NfjHtmlParser {

    private static final Pattern DATE = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public LocalDate extractValidTo(String html) {
        if (html == null || html.isBlank()) return null;

        String text = Jsoup.parse(html).text();
        int idx = text.indexOf("Oferta ważna do:");
        if (idx < 0) idx = text.indexOf("Oferta wazna do:");
        if (idx < 0) return null;

        String tail = text.substring(idx);
        Matcher m = DATE.matcher(tail);
        if (!m.find()) return null;

        try {
            return LocalDate.parse(m.group(1), FMT);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean isExpired(String html) {
        if (html == null || html.isBlank()) return false;

        String t = Jsoup.parse(html).text().toLowerCase(Locale.ROOT);

        if (t.contains("oferta pracy") && t.contains("wygas")) return true;
        if (t.contains("wygasła") || t.contains("wygasla") || t.contains("wygasło") || t.contains("wygaslo")) {
            return true;
        }

        return false;
    }
}
