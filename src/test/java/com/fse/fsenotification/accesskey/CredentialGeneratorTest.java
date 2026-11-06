package com.fse.fsenotification.accesskey;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialGeneratorTest {

    private final CredentialGenerator generator = new CredentialGenerator();

    @Test
    void generatesPrefixedUniqueCredentials() {
        assertThat(generator.newClientId()).startsWith("fse_").hasSize(28);
        assertThat(generator.newClientSecret()).startsWith("fsk_").hasSize(47);
        assertThat(generator.newClientSecret()).isNotEqualTo(generator.newClientSecret());
    }

    @Test
    void matchesOnlyTheOriginalSecret() {
        String secret = generator.newClientSecret();
        String hash = generator.hashSecret(secret);

        assertThat(hash).doesNotContain(secret);
        assertThat(generator.matches(secret, hash)).isTrue();
        assertThat(generator.matches(secret + "x", hash)).isFalse();
        assertThat(generator.matches(secret, "not-hex")).isFalse();
    }
}
