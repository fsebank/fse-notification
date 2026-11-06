package com.fse.fsenotification.modules.message;

/**
 * Counts how many SMS pages a text needs, which is what operators bill.
 * <p>
 * Text using only the GSM 03.38 alphabet fits 160 characters in one page, or 153 per page when split (the rest
 * is the concatenation header); characters from the GSM extension table such as {@code € [ ] { }} count twice.
 * Any other character (e.g. emoji, most non-Latin scripts) switches the whole message to UCS-2: 70 per page,
 * 67 when split.
 */
public final class SmsSegments {

    private static final String GSM_BASIC = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
            + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private static final String GSM_EXTENDED = "^{}\\[~]|€\f";

    private SmsSegments() {
    }

    public static int count(String text) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int septets = gsmLength(text);
        if (septets >= 0) {
            return septets <= 160 ? 1 : ceilDiv(septets, 153);
        }
        int units = text.length(); // UTF-16 code units, as UCS-2 encodes them
        return units <= 70 ? 1 : ceilDiv(units, 67);
    }

    /** @return length in GSM septets, or -1 if the text needs UCS-2 */
    private static int gsmLength(String text) {
        int length = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (GSM_BASIC.indexOf(c) >= 0) {
                length += 1;
            } else if (GSM_EXTENDED.indexOf(c) >= 0) {
                length += 2;
            } else {
                return -1;
            }
        }
        return length;
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
