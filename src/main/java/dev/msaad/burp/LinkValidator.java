package dev.msaad.burp;

import burp.api.montoya.logging.Logging;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * Validates social media URLs using a bounded thread pool with rate limiting.
 *
 * Design choices (adopted from popular Burp extensions):
 * - Bounded thread pool (5 threads) to avoid overwhelming targets
 * - Rate limiter (max 10 req/sec) to avoid IP bans
 * - Proper shutdown() for clean extension unloading
 * - Response body capped at 256 KB for memory efficiency
 */
public class LinkValidator {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final int MAX_BODY_SIZE = 256 * 1024;
    private static final int THREAD_POOL_SIZE = 5;
    private static final long RATE_LIMIT_MS = 100; // ~10 requests/sec

    private final HttpClient httpClient;
    private final Logging logging;
    private final ExecutorService threadPool;
    private final Semaphore rateLimiter;

    // Track last request time for rate limiting
    private volatile long lastRequestTime = 0;

    public LinkValidator(Logging logging) {
        this.logging = logging;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();

        this.threadPool = new ThreadPoolExecutor(
                2, // core threads
                THREAD_POOL_SIZE, // max threads
                30L, TimeUnit.SECONDS, // idle timeout
                new LinkedBlockingQueue<>(50), // bounded queue
                new ThreadPoolExecutor.CallerRunsPolicy() // backpressure
        );

        this.rateLimiter = new Semaphore(THREAD_POOL_SIZE);
    }

    /**
     * Synchronously checks the given URL and returns a LinkResult.
     * Applies rate limiting to avoid overwhelming targets.
     */
    public LinkResult checkUrl(String url) {
        applyRateLimit();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(REQUEST_TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String body = response.body();

            if (body != null && body.length() > MAX_BODY_SIZE) {
                body = body.substring(0, MAX_BODY_SIZE);
            }

            return new LinkResult(statusCode, body);

        } catch (Exception e) {
            logging.logToOutput("[!] Error checking " + url + ": " + e.getMessage());
            return new LinkResult(-1, "");
        }
    }

    /**
     * Asynchronously checks the given URL using the thread pool.
     */
    public CompletableFuture<LinkResult> checkUrlAsync(String url) {
        return CompletableFuture.supplyAsync(() -> checkUrl(url), threadPool);
    }

    /**
     * Simple rate limiter — ensures minimum spacing between requests.
     */
    private void applyRateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < RATE_LIMIT_MS) {
            try {
                Thread.sleep(RATE_LIMIT_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }

    /**
     * Gracefully shuts down the thread pool.
     * Called when the extension is unloaded.
     */
    public void shutdown() {
        logging.logToOutput("[*] Shutting down link validator thread pool...");
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                logging.logToOutput("[!] Thread pool forced shutdown.");
            } else {
                logging.logToOutput("[+] Thread pool shut down gracefully.");
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
