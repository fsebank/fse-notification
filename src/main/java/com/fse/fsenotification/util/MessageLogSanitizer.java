package com.fse.fsenotification.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageLogSanitizer {
    private static final Pattern EMAIL_ADDRESS =
            Pattern.compile("[A-Z0-9._%+-]+@([A-Z0-9.-]+\\.[A-Z]{2,})", Pattern.CASE_INSENSITIVE);

    private MessageLogSanitizer() {
    }

    public static String maskRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "***";
        }

        int at = recipient.lastIndexOf('@');
        if (at > 0 && at < recipient.length() - 1) {
            return "***@" + recipient.substring(at + 1);
        }

        String digits = recipient.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "***";
        }
        return "***" + digits.substring(Math.max(0, digits.length() - 4));
    }

    public static List<String> maskRecipients(List<String> recipients) {
        return recipients.stream().map(MessageLogSanitizer::maskRecipient).toList();
    }

    public static String escape(String value) {
        if (value == null) {
            return "<null>";
        }

        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\r' -> escaped.append("\\r");
                case '\n' -> escaped.append("\\n");
                case '\t' -> escaped.append("\\t");
                case '\u2028' -> escaped.append("\\u2028");
                case '\u2029' -> escaped.append("\\u2029");
                default -> {
                    if (Character.isISOControl(character)) {
                        escaped.append('?');
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    public static String sanitizeResponse(String value, List<String> recipients) {
        String sanitized = escape(value);
        for (String recipient : recipients) {
            if (recipient != null && !recipient.isBlank()) {
                sanitized = Pattern.compile(Pattern.quote(escape(recipient)), Pattern.CASE_INSENSITIVE)
                        .matcher(sanitized)
                        .replaceAll(Matcher.quoteReplacement(maskRecipient(recipient)));
            }
        }
        return EMAIL_ADDRESS.matcher(sanitized).replaceAll("***@$1");
    }
}
