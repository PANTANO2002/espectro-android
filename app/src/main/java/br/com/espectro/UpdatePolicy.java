package br.com.espectro;

import java.net.URI;
import java.util.Locale;

/** Pure-Java checks shared by network code and host-side regression tests. */
final class UpdatePolicy {
    private UpdatePolicy() { }
    static boolean allowedUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String host = uri.getHost();
            return "api.github.com".equalsIgnoreCase(host) || "github.com".equalsIgnoreCase(host)
                || "release-assets.githubusercontent.com".equalsIgnoreCase(host) || "objects.githubusercontent.com".equalsIgnoreCase(host);
        } catch (Exception e) { return false; }
    }
    static boolean releaseAssetUrl(String value, String repo) {
        if (!allowedUrl(value)) return false;
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            return "github.com".equalsIgnoreCase(uri.getHost()) && path != null
                && path.startsWith("/" + repo + "/releases/download/")
                && !path.contains("..") && !path.toLowerCase(Locale.ROOT).contains("%2e")
                && !path.toLowerCase(Locale.ROOT).contains("%2f") && !path.toLowerCase(Locale.ROOT).contains("%5c") && uri.getRawQuery() == null;
        } catch (Exception e) { return false; }
    }
    static boolean sha256(String value) { return value != null && value.matches("[a-fA-F0-9]{64}"); }
    static boolean apkName(String value) { return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,100}\\.apk"); }
    static boolean newer(long incoming, long installed) { return incoming > installed && incoming <= Integer.MAX_VALUE; }
}
