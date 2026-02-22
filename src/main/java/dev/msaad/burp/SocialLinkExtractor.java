package dev.msaad.burp;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts social media profile/page URLs from HTTP response bodies.
 * Supports X/Twitter, Instagram, YouTube, Facebook, LinkedIn, GitHub, and
 * TikTok.
 */
public final class SocialLinkExtractor {

    private SocialLinkExtractor() {
        // Utility class — no instantiation
    }

    /**
     * Master pattern that matches social media profile URLs.
     * Each group captures a full URL from a different platform.
     *
     * Supported formats:
     * https://twitter.com/username
     * https://x.com/username
     * https://instagram.com/username
     * https://youtube.com/@handle or /channel/ID or /c/name
     * https://facebook.com/pagename
     * https://linkedin.com/in/name or /company/name
     * https://github.com/username
     * https://tiktok.com/@handle
     */
    private static final Pattern SOCIAL_URL_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?" + // protocol + optional www
                    "(?:" +
                    "(?:twitter|x)\\.com/[A-Za-z0-9_]{1,50}" + // X / Twitter
                    "|instagram\\.com/[A-Za-z0-9_.]{1,50}" + // Instagram
                    "|youtube\\.com/(?:@[A-Za-z0-9_.-]{1,50}" + // YouTube @handle
                    "|channel/[A-Za-z0-9_-]{1,50}" + // YouTube channel ID
                    "|c/[A-Za-z0-9_.-]{1,50})" + // YouTube custom URL
                    "|facebook\\.com/[A-Za-z0-9_.]{1,80}" + // Facebook
                    "|linkedin\\.com/(?:in|company)/[A-Za-z0-9_.-]{1,80}" + // LinkedIn
                    "|github\\.com/[A-Za-z0-9_.-]{1,50}" + // GitHub
                    "|tiktok\\.com/@[A-Za-z0-9_.]{1,50}" + // TikTok
                    ")",
            Pattern.CASE_INSENSITIVE);

    /**
     * Extracts all unique social media URLs from the given text.
     *
     * @param responseBody the HTTP response body to scan
     * @return a set of unique social media URLs found
     */
    public static Set<String> extract(String responseBody) {
        Set<String> urls = new LinkedHashSet<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return urls;
        }

        Matcher matcher = SOCIAL_URL_PATTERN.matcher(responseBody);
        while (matcher.find()) {
            String url = matcher.group();

            // Normalize: ensure https and strip trailing slashes/punctuation
            url = normalizeUrl(url);

            if (url != null) {
                urls.add(url);
            }
        }

        return urls;
    }

    /**
     * Cleans up the matched URL by:
     * - Upgrading http to https
     * - Stripping trailing quote/bracket/punctuation characters
     * - Filtering out generic paths (e.g., just "twitter.com" with no username)
     */
    private static String normalizeUrl(String url) {
        if (url == null)
            return null;

        // Upgrade to HTTPS
        if (url.startsWith("http://")) {
            url = "https://" + url.substring(7);
        }

        // Strip common trailing garbage from HTML/JS contexts
        url = url.replaceAll("[\"'<>()\\]\\[},;\\s]+$", "");

        // Strip trailing slash
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }
}
