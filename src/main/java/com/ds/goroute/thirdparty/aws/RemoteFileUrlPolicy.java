package com.ds.goroute.thirdparty.aws;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Rejects URLs that could make the application reach a local or private network resource.
 */
final class RemoteFileUrlPolicy {

    private RemoteFileUrlPolicy() {
    }

    static URI requirePublicHttps(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Remote file URL is required");
        }
        try {
            return requirePublicHttps(URI.create(rawUrl.trim()));
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Remote file URL")) {
                throw exception;
            }
            throw new IllegalArgumentException("Remote file URL is invalid");
        }
    }

    static URI requirePublicHttps(URI uri) {
        if (uri == null || !uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Remote file URL must use HTTPS");
        }
        if (uri.getRawUserInfo() != null || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Remote file URL host is invalid");
        }

        String host = stripIpv6Brackets(uri.getHost());
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0 || hasPrivateOrReservedAddress(addresses)) {
                throw new IllegalArgumentException("Remote file URL must resolve to a public address");
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Remote file URL host cannot be resolved");
        }

        return uri;
    }

    static boolean isSameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && stripIpv6Brackets(first.getHost()).equalsIgnoreCase(stripIpv6Brackets(second.getHost()))
                && effectivePort(first) == effectivePort(second);
    }

    private static boolean hasPrivateOrReservedAddress(InetAddress[] addresses) {
        for (InetAddress address : addresses) {
            if (isPrivateOrReserved(address)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrivateOrReserved(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isPrivateOrReservedIpv4(bytes[0] & 0xff, bytes[1] & 0xff, bytes[2] & 0xff);
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            if (first == 0xfc || first == 0xfd) {
                return true;
            }
            if (isIpv4Mapped(bytes)) {
                return isPrivateOrReservedIpv4(bytes[12] & 0xff, bytes[13] & 0xff, bytes[14] & 0xff);
            }
        }
        return false;
    }

    private static boolean isPrivateOrReservedIpv4(int first, int second, int third) {
        return first == 0
                || first == 10
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 192 && second == 0 && third == 0)
                || (first == 198 && (second == 18 || second == 19))
                || first >= 224;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private static String stripIpv6Brackets(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            return normalizedHost.substring(1, normalizedHost.length() - 1);
        }
        return normalizedHost;
    }
}
