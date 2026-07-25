package in.phamvu.cloudshareapi.util;

import org.springframework.util.StringUtils;

public final class UserAgentParser {

    private UserAgentParser() {
    }

    public record ParsedUserAgent(String os, String browser) {
    }

    public static ParsedUserAgent parse(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return new ParsedUserAgent("Unknown", "Unknown");
        }

        return new ParsedUserAgent(parseOs(userAgent), parseBrowser(userAgent));
    }

    private static String parseOs(String ua) {
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) {
            return "iOS";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return "Unknown";
    }

    private static String parseBrowser(String ua) {
        if (ua.contains("Edg/")) {
            return "Edge " + extractVersion(ua, "Edg/");
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera " + extractVersion(ua, "OPR/");
        }
        if (ua.contains("Chrome/")) {
            return "Chrome " + extractVersion(ua, "Chrome/");
        }
        if (ua.contains("Firefox/")) {
            return "Firefox " + extractVersion(ua, "Firefox/");
        }
        if (ua.contains("Safari/") && ua.contains("Version/")) {
            return "Safari " + extractVersion(ua, "Version/");
        }
        return "Unknown";
    }

    private static String extractVersion(String ua, String marker) {
        int start = ua.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = start;
        while (end < ua.length() && (Character.isDigit(ua.charAt(end)) || ua.charAt(end) == '.')) {
            end++;
        }
        return ua.substring(start, end).trim();
    }
}
