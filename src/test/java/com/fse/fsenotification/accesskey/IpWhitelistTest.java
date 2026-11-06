package com.fse.fsenotification.accesskey;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IpWhitelistTest {

    @Test
    void acceptsLiteralAddressesAndCidr() {
        assertThat(IpWhitelist.isValidEntry("10.0.0.5")).isTrue();
        assertThat(IpWhitelist.isValidEntry("196.46.20.0/24")).isTrue();
        assertThat(IpWhitelist.isValidEntry("2001:db8::/32")).isTrue();
        assertThat(IpWhitelist.isValidEntry("::1")).isTrue();
    }

    @Test
    void rejectsHostnamesAndMalformedEntries() {
        assertThat(IpWhitelist.isValidEntry("example.com")).isFalse();
        assertThat(IpWhitelist.isValidEntry("256.1.1.1")).isFalse();
        assertThat(IpWhitelist.isValidEntry("10.0.0.0/33")).isFalse();
        assertThat(IpWhitelist.isValidEntry("10.0.0.0/abc")).isFalse();
        assertThat(IpWhitelist.isValidEntry("10.0.0.0/8/8")).isFalse();
        assertThat(IpWhitelist.isValidEntry(" ")).isFalse();
    }

    @Test
    void emptyWhitelistAllowsAnyIp() {
        assertThat(IpWhitelist.isAllowed(Set.of(), "8.8.8.8")).isTrue();
    }

    @Test
    void matchesExactAndCidrEntries() {
        Set<String> whitelist = Set.of("10.0.0.5", "196.46.20.0/24");
        assertThat(IpWhitelist.isAllowed(whitelist, "10.0.0.5")).isTrue();
        assertThat(IpWhitelist.isAllowed(whitelist, "196.46.20.77")).isTrue();
        assertThat(IpWhitelist.isAllowed(whitelist, "196.46.21.1")).isFalse();
        assertThat(IpWhitelist.isAllowed(whitelist, "10.0.0.6")).isFalse();
    }
}
