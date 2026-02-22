package dev.msaad.burp;

import java.util.Locale;

/**
 * Platform-specific broken link detection.
 *
 * Instead of relying solely on HTTP 404, this class inspects the response
 * body for platform-specific "not found" indicators — handling soft-404s,
 * anti-bot responses, and login-wall redirects.
 *
 * Detection matrix:
 *
 * Platform | HTTP Code Check | Body Content Check
 * ------------|---------------------|--------------------------------------------
 * X/Twitter | 404 | "This account doesn't exist" / "User not found"
 * Instagram | 404 OR 200+body | "Sorry, this page isn't available"
 * YouTube | 404 | "This page isn't available"
 * Facebook | 404, 302→login | "This content isn't available" / "Page Not Found"
 * LinkedIn | 404, 999 | "page doesn't exist" / "Page not found"
 * GitHub | 404 | (clean 404, no body check needed)
 * TikTok | 404 OR 200+body | "Couldn't find this account"
 */
public final class PlatformDetector {

    private PlatformDetector() {
        // Utility class
    }

    /**
     * Determines which social media platform a URL belongs to.
     */
    public static Platform identify(String url) {
        if (url == null)
            return Platform.UNKNOWN;
        String lower = url.toLowerCase(Locale.ROOT);

        if (lower.contains("twitter.com/") || lower.contains("x.com/"))
            return Platform.TWITTER;
        if (lower.contains("instagram.com/"))
            return Platform.INSTAGRAM;
        if (lower.contains("youtube.com/"))
            return Platform.YOUTUBE;
        if (lower.contains("facebook.com/"))
            return Platform.FACEBOOK;
        if (lower.contains("linkedin.com/"))
            return Platform.LINKEDIN;
        if (lower.contains("github.com/"))
            return Platform.GITHUB;
        if (lower.contains("tiktok.com/"))
            return Platform.TIKTOK;

        return Platform.UNKNOWN;
    }

    /**
     * Returns true if the link is broken, using platform-specific heuristics.
     *
     * @param platform the detected platform
     * @param result   the HTTP response (status + body)
     * @return true if the link appears to be broken/claimable
     */
    public static boolean isBroken(Platform platform, LinkResult result) {
        int code = result.getStatusCode();
        String body = result.getBody().toLowerCase(Locale.ROOT);

        switch (platform) {
            case TWITTER:
                return isBrokenTwitter(code, body);
            case INSTAGRAM:
                return isBrokenInstagram(code, body);
            case YOUTUBE:
                return isBrokenYouTube(code, body);
            case FACEBOOK:
                return isBrokenFacebook(code, body);
            case LINKEDIN:
                return isBrokenLinkedIn(code, body);
            case GITHUB:
                return isBrokenGitHub(code, body);
            case TIKTOK:
                return isBrokenTikTok(code, body);
            default:
                // Fallback: pure 404 check
                return code == 404;
        }
    }

    /**
     * Returns a human-readable reason why the link was flagged as broken.
     */
    public static String getReason(Platform platform, LinkResult result) {
        int code = result.getStatusCode();
        String body = result.getBody().toLowerCase(Locale.ROOT);

        switch (platform) {
            case TWITTER:
                if (code == 404)
                    return "HTTP 404 — Account does not exist";
                if (containsAny(body, "this account doesn't exist", "user not found",
                        "account is suspended", "doesn't exist"))
                    return "Soft-404 — Page body indicates account doesn't exist";
                break;
            case INSTAGRAM:
                if (code == 404)
                    return "HTTP 404 — Profile not found";
                if (containsAny(body, "sorry, this page isn't available",
                        "the link you followed may be broken"))
                    return "Soft-404 (HTTP " + code + ") — Body says page isn't available";
                break;
            case YOUTUBE:
                if (code == 404)
                    return "HTTP 404 — Channel/user not found";
                if (containsAny(body, "this page isn't available", "this channel does not exist"))
                    return "Soft-404 (HTTP " + code + ") — Body says page isn't available";
                break;
            case FACEBOOK:
                if (code == 404)
                    return "HTTP 404 — Page not found";
                if (containsAny(body, "this content isn't available",
                        "page not found", "the link you followed may have been broken"))
                    return "Soft-404 (HTTP " + code + ") — Body indicates content unavailable";
                if (code == 302 || code == 301)
                    return "Redirect (HTTP " + code + ") — Likely redirect to login (page missing)";
                break;
            case LINKEDIN:
                if (code == 404)
                    return "HTTP 404 — Profile not found";
                if (code == 999)
                    return "HTTP 999 — LinkedIn anti-bot (profile likely inaccessible)";
                if (containsAny(body, "page doesn't exist", "page not found",
                        "this profile is not available"))
                    return "Soft-404 (HTTP " + code + ") — Body indicates profile doesn't exist";
                break;
            case GITHUB:
                if (code == 404)
                    return "HTTP 404 — User/org not found";
                break;
            case TIKTOK:
                if (code == 404)
                    return "HTTP 404 — Account not found";
                if (containsAny(body, "couldn't find this account",
                        "account not found", "user_not_found"))
                    return "Soft-404 (HTTP " + code + ") — Body says account not found";
                break;
            default:
                if (code == 404)
                    return "HTTP 404 — Resource not found";
                break;
        }
        return "HTTP " + code + " — Flagged as broken";
    }

    // ===================== Per-platform detection =====================

    private static boolean isBrokenTwitter(int code, String body) {
        if (code == 404)
            return true;
        return containsAny(body,
                "this account doesn't exist",
                "user not found",
                "account is suspended",
                "\"screen_name\":null",
                "doesn't exist");
    }

    private static boolean isBrokenInstagram(int code, String body) {
        if (code == 404)
            return true;
        // Instagram often returns 200 with a soft-404 page
        return containsAny(body,
                "sorry, this page isn't available",
                "the link you followed may be broken",
                "httperrorpage",
                "\"is_private\":false,\"edge_owner_to_timeline_media\":{\"count\":0");
    }

    private static boolean isBrokenYouTube(int code, String body) {
        if (code == 404)
            return true;
        return containsAny(body,
                "this page isn't available",
                "this channel does not exist",
                "\"reason\":\"not found\"");
    }

    private static boolean isBrokenFacebook(int code, String body) {
        if (code == 404)
            return true;
        // Facebook redirects to login for missing pages
        if ((code == 302 || code == 301) && body.length() < 500)
            return true;
        return containsAny(body,
                "this content isn't available",
                "page not found",
                "the link you followed may have been broken",
                "this page isn't available");
    }

    private static boolean isBrokenLinkedIn(int code, String body) {
        if (code == 404)
            return true;
        // LinkedIn uses 999 for anti-bot, but also for truly missing pages
        // We flag 999 as "needs manual review" — still report it
        if (code == 999)
            return true;
        return containsAny(body,
                "page doesn't exist",
                "page not found",
                "this profile is not available",
                "profile not found");
    }

    private static boolean isBrokenGitHub(int code, String body) {
        // GitHub reliably returns 404 for missing users/repos
        return code == 404;
    }

    private static boolean isBrokenTikTok(int code, String body) {
        if (code == 404)
            return true;
        // TikTok sometimes returns 200 with "Couldn't find this account"
        return containsAny(body,
                "couldn't find this account",
                "account not found",
                "user_not_found",
                "\"statuscode\":10202");
    }

    // ===================== Helpers =====================

    /**
     * Returns true if the body contains ANY of the given markers.
     */
    private static boolean containsAny(String body, String... markers) {
        for (String marker : markers) {
            if (body.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
