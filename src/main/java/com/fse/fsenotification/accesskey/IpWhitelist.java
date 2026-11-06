package com.fse.fsenotification.accesskey;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Validation and matching of whitelist entries. Entries are IPv4/IPv6 literals, optionally with a CIDR
 * suffix (e.g. 10.0.0.5, 196.46.20.0/24, 2001:db8::/32).
 */
public final class IpWhitelist {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private IpWhitelist() {
    }

    /**
     * Only literal addresses are accepted; this matters because IpAddressMatcher resolves its input via
     * InetAddress, which would otherwise perform DNS lookups for host names.
     */
    public static boolean isValidEntry(String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        String[] parts = entry.trim().split("/", -1);
        if (parts.length > 2) {
            return false;
        }
        String address = parts[0];
        boolean v4 = IPV4.matcher(address).matches();
        boolean v6 = !v4 && address.contains(":") && IPV6.matcher(address).matches();
        if (!v4 && !v6) {
            return false;
        }
        if (parts.length == 2) {
            try {
                int prefix = Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > (v4 ? 32 : 128)) {
                    return false;
                }
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        try {
            new IpAddressMatcher(entry.trim());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** True if the whitelist is empty (no restriction) or the client IP matches at least one entry. */
    public static boolean isAllowed(Collection<String> whitelist, String clientIp) {
        if (whitelist == null || whitelist.isEmpty()) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        for (String entry : whitelist) {
            try {
                if (new IpAddressMatcher(entry).matches(clientIp)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // malformed stored entry or client IP; treat as non-matching
            }
        }
        return false;
    }
}
