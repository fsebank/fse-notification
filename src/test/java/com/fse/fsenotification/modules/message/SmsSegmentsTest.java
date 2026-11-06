package com.fse.fsenotification.modules.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmsSegmentsTest {

    @Test
    void gsmTextUses160Then153PerPage() {
        assertThat(SmsSegments.count("a".repeat(160))).isEqualTo(1);
        assertThat(SmsSegments.count("a".repeat(161))).isEqualTo(2);
        assertThat(SmsSegments.count("a".repeat(306))).isEqualTo(2);
        assertThat(SmsSegments.count("a".repeat(307))).isEqualTo(3);
    }

    @Test
    void extendedGsmCharactersCountTwice() {
        assertThat(SmsSegments.count("€".repeat(80))).isEqualTo(1);
        assertThat(SmsSegments.count("€".repeat(81))).isEqualTo(2);
    }

    @Test
    void unicodeTextUses70Then67PerPage() {
        assertThat(SmsSegments.count("₦".repeat(70))).isEqualTo(1);
        assertThat(SmsSegments.count("₦".repeat(71))).isEqualTo(2);
        assertThat(SmsSegments.count("Hello 👋")).isEqualTo(1);
    }
}
