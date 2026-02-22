package dev.msaad.burp;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe global cache that stores the HTTP status code for each checked
 * URL.
 * Prevents redundant network requests for URLs seen in multiple responses.
 *
 * Uses the Singleton pattern — call {@link #getInstance()} to access.
 */
public final class LinkCache {

    private static final LinkCache INSTANCE = new LinkCache();

    /** URL → HTTP status code mapping */
    private final ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();

    private LinkCache() {
        // Singleton
    }

    public static LinkCache getInstance() {
        return INSTANCE;
    }

    /**
     * Returns true if this URL has already been checked.
     */
    public boolean contains(String url) {
        return cache.containsKey(url);
    }

    /**
     * Stores the result of checking a URL.
     *
     * @param url        the URL that was checked
     * @param statusCode the HTTP status code received
     */
    public void put(String url, int statusCode) {
        cache.put(url, statusCode);
    }

    /**
     * Returns the cached status code, or -1 if not present.
     */
    public int get(String url) {
        return cache.getOrDefault(url, -1);
    }

    /**
     * Returns the number of URLs currently cached.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Clears the entire cache. Useful for extension reload.
     */
    public void clear() {
        cache.clear();
    }
}
