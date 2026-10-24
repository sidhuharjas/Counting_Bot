package com.countingbot.util;

import java.util.Locale;

public final class Format {

    private Format() {
    }

    public static String commas(long value) {
        return String.format(Locale.ROOT, "{:,}", value);
    }

    public static String points(double value) {
        long whole = (long) value;
        double frac = value - whole;
        String fracPart = "";
        if (frac != 0.0) {
            String raw = String.valueOf(frac);
            fracPart = "." + raw.substring(raw.indexOf('.') + 1);
        }
        String sign = whole < 0 ? "-" : "";
        return sign + commas(Math.abs(whole)) + fracPart;
    }

    public static String saves(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    public static String uptime(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb + " (" + commas((millis / 1000)) + "s)";
    }

    public static String cooldown(long remainingMs) {
        if (remainingMs >= 3600_000L) {
            long h = (remainingMs + 3600_000L - 1) / 3600_000L;
            return h + (h == 1 ? " hour" : " hours");
        }
        long m = (remainingMs + 60_000L - 1) / 60_000L;
        if (m < 1) m = 1;
        return m + (m == 1 ? " minute" : " minutes");
    }
}
