package tech.vartaai.whatsappcrm.util;

/**
 * Shared string helper methods used across service and provider classes.
 */
public final class StringUtils {

    private StringUtils() {
        // utility class
    }

    /**
     * Returns {@code true} if the string is {@code null} or
     * {@linkplain String#isBlank() blank}.
     */
    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Returns the first non-null, non-blank value, or {@code null} if none.
     */
    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
