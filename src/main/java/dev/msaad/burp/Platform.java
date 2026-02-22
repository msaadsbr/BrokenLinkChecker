package dev.msaad.burp;

/**
 * Supported social media platforms for broken link detection.
 */
public enum Platform {
    TWITTER("X / Twitter"),
    INSTAGRAM("Instagram"),
    YOUTUBE("YouTube"),
    FACEBOOK("Facebook"),
    LINKEDIN("LinkedIn"),
    GITHUB("GitHub"),
    TIKTOK("TikTok"),
    UNKNOWN("Unknown");

    private final String displayName;

    Platform(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
