package com.donutah;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidades puras (sin dependencias de Minecraft) para leer y formatear precios.
 */
public final class PriceParser {

    /**
     * Busca "$1,200,000", "$1.5M", "$250K"...
     * El sufijo solo cuenta si NO va seguido de otra letra: asi "$50 buy now" o
     * "$1,200 total" no se interpretan como 50 billones / 1.200 trillones.
     */
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "\\$\\s*([0-9]+(?:[.,][0-9]+)*)(?:\\s?([KkMmBbTt])(?![A-Za-z]))?");

    private static final Pattern HUMAN_PATTERN = Pattern.compile(
            "^\\$?\\s*([0-9]+(?:[.,][0-9]+)*)\\s*([KkMmBbTt])?$");

    private static final Pattern PRICE_LABEL = Pattern.compile("(?i)\\b(price|precio|cost|coste|buy)\\b");

    private PriceParser() {}

    /** Devuelve el primer precio de la linea o -1 si no hay. */
    public static double findPrice(String line) {
        if (line == null || line.indexOf('$') < 0) return -1.0;
        Matcher m = PRICE_PATTERN.matcher(line);
        while (m.find()) {
            double v = parseNumber(m.group(1), m.group(2));
            if (v > 0.0) return v;
        }
        return -1.0;
    }

    /** True si la linea parece ser la etiqueta de precio ("Price: $..."). */
    public static boolean hasPriceLabel(String line) {
        return line != null && PRICE_LABEL.matcher(line).find();
    }

    /** Parsea entradas del usuario: "50000", "250K", "1.5M", "$2B", "1,5M". */
    public static double parseHuman(String input) {
        if (input == null || input.isBlank()) return -1.0;
        Matcher m = HUMAN_PATTERN.matcher(input.trim());
        if (!m.matches()) return -1.0;
        return parseNumber(m.group(1), m.group(2));
    }

    static double parseNumber(String num, String suffix) {
        try {
            double multiplier = 1.0;
            if (suffix != null) {
                switch (suffix.toUpperCase(Locale.ROOT)) {
                    case "K" -> multiplier = 1_000.0;
                    case "M" -> multiplier = 1_000_000.0;
                    case "B" -> multiplier = 1_000_000_000.0;
                    case "T" -> multiplier = 1_000_000_000_000.0;
                    default -> { }
                }
            }
            return Double.parseDouble(normalize(num, suffix != null)) * multiplier;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    /** Decide que separador es decimal y cual es de miles. */
    private static String normalize(String num, boolean hasSuffix) {
        int lastDot = num.lastIndexOf('.');
        int lastComma = num.lastIndexOf(',');

        if (lastDot < 0 && lastComma < 0) return num;

        if (lastDot >= 0 && lastComma >= 0) {
            // Ambos presentes: el ultimo es el decimal.
            if (lastDot > lastComma) return num.replace(",", "");
            return num.replace(".", "").replace(',', '.');
        }

        char sep = lastDot >= 0 ? '.' : ',';
        int count = 0;
        for (int i = 0; i < num.length(); i++) {
            if (num.charAt(i) == sep) count++;
        }
        int digitsAfter = num.length() - num.lastIndexOf(sep) - 1;
        boolean grouping = count > 1 || (!hasSuffix && digitsAfter == 3);

        return grouping ? num.replace(String.valueOf(sep), "") : num.replace(sep, '.');
    }

    public static String formatMoney(double amount) {
        if (amount >= 1_000_000_000_000.0) return fmt(amount / 1_000_000_000_000.0, "T");
        if (amount >= 1_000_000_000.0) return fmt(amount / 1_000_000_000.0, "B");
        if (amount >= 1_000_000.0) return fmt(amount / 1_000_000.0, "M");
        if (amount >= 1_000.0) return fmt(amount / 1_000.0, "K");
        return fmt(amount, "");
    }

    private static String fmt(double value, String suffix) {
        String s = String.format(Locale.US, "%.2f", value);
        if (s.indexOf('.') >= 0) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '0') end--;
            if (end > 0 && s.charAt(end - 1) == '.') end--;
            s = s.substring(0, end);
        }
        return "$" + s + suffix;
    }
}
