package dev.msaad.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;

/**
 * Broken Link Hijacker — Burp Suite Extension
 *
 * Passively scans HTTP responses for social media links and checks
 * if they are broken, indicating a Broken Link Hijacking opportunity.
 *
 * Features (adopted from Logger++, Autorize, ActiveScan++):
 * - Custom UI tab with results table and CSV export
 * - Bounded thread pool with rate limiting
 * - Burp scope filtering
 * - Platform-specific broken link detection (soft-404s)
 * - Clean extension unload with resource cleanup
 *
 * @author Muhammad Saad Sabir
 */
public class BrokenLinkHijacker implements BurpExtension {

    private static final String EXTENSION_NAME = "Broken Link Hijacker";

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName(EXTENSION_NAME);
        Logging logging = api.logging();

        logging.logToOutput("===========================================");
        logging.logToOutput("  Broken Link Hijacker v2.0.0");
        logging.logToOutput("  by Muhammad Saad Sabir");
        logging.logToOutput("===========================================");
        logging.logToOutput("[*] Initializing...");

        // Initialize components
        LinkValidator validator = new LinkValidator(logging);
        LinkCache cache = LinkCache.getInstance();
        ResultsPanel resultsPanel = new ResultsPanel();

        // Register the custom UI tab
        api.userInterface().registerSuiteTab(EXTENSION_NAME, resultsPanel);
        logging.logToOutput("[+] UI tab registered.");

        // Register the passive scan check
        SocialLinkScanCheck scanCheck = new SocialLinkScanCheck(
                api, logging, validator, cache, resultsPanel);
        api.scanner().registerScanCheck(scanCheck);
        logging.logToOutput("[+] Passive scan check registered.");

        // Register unload handler for clean shutdown
        api.extension().registerUnloadingHandler(() -> {
            logging.logToOutput("[*] Unloading Broken Link Hijacker...");
            validator.shutdown();
            cache.clear();
            logging.logToOutput("[+] Cleanup complete. Goodbye!");
        });

        logging.logToOutput("[+] Settings:");
        logging.logToOutput("    Thread pool:    2-5 threads");
        logging.logToOutput("    Rate limit:     ~10 req/sec");
        logging.logToOutput("    Scope filter:   ON (in-scope only)");
        logging.logToOutput("    Max body scan:  500 KB");
        logging.logToOutput("[*] Ready! Browse your target to find broken links.");
    }
}
