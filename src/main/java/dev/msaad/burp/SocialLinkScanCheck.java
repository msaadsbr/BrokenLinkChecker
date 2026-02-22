package dev.msaad.burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.ScanCheck;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static burp.api.montoya.scanner.AuditResult.auditResult;
import static burp.api.montoya.scanner.ConsolidationAction.KEEP_EXISTING;
import static burp.api.montoya.scanner.ConsolidationAction.KEEP_NEW;

/**
 * Passive scan check that extracts social media links from HTTP responses
 * and verifies them using platform-specific broken link detection.
 *
 * Optimizations (from Logger++, ActiveScan++ patterns):
 * - Skips binary responses (images, fonts, CSS, JS)
 * - Skips very large responses (> 500 KB)
 * - Scope-aware: only processes in-scope responses
 * - Feeds results into the ResultsPanel UI tab
 */
public class SocialLinkScanCheck implements ScanCheck {

    private static final int MAX_RESPONSE_SIZE = 500 * 1024; // 500 KB

    /** Content types worth scanning (HTML, JSON, XML, text) */
    private static final String[] SCANNABLE_TYPES = {
            "text/html", "application/json", "application/xml",
            "text/xml", "text/plain", "application/xhtml"
    };

    private final MontoyaApi api;
    private final Logging logging;
    private final LinkValidator validator;
    private final LinkCache cache;
    private final ResultsPanel resultsPanel;
    private final boolean scopeFilterEnabled;

    public SocialLinkScanCheck(MontoyaApi api, Logging logging,
            LinkValidator validator, LinkCache cache,
            ResultsPanel resultsPanel) {
        this.api = api;
        this.logging = logging;
        this.validator = validator;
        this.cache = cache;
        this.resultsPanel = resultsPanel;
        this.scopeFilterEnabled = true;
    }

    @Override
    public AuditResult activeAudit(HttpRequestResponse baseRequestResponse,
            AuditInsertionPoint auditInsertionPoint) {
        return auditResult();
    }

    @Override
    public AuditResult passiveAudit(HttpRequestResponse baseRequestResponse) {
        List<AuditIssue> issues = new ArrayList<>();

        // ── Guard: Scope check ──
        String requestUrl = baseRequestResponse.request().url();
        if (scopeFilterEnabled && !api.scope().isInScope(requestUrl)) {
            return auditResult();
        }

        // ── Guard: Response present ──
        HttpResponse response = baseRequestResponse.response();
        if (response == null) {
            return auditResult();
        }

        // ── Guard: Content-Type check (skip binary) ──
        String contentType = getContentType(response);
        if (!isScannableContentType(contentType)) {
            return auditResult();
        }

        // ── Guard: Size check ──
        String responseBody;
        try {
            responseBody = response.bodyToString();
        } catch (Exception e) {
            return auditResult();
        }

        if (responseBody == null || responseBody.isEmpty()) {
            return auditResult();
        }

        if (responseBody.length() > MAX_RESPONSE_SIZE) {
            return auditResult();
        }

        // ── Extract social media URLs ──
        Set<String> socialUrls = SocialLinkExtractor.extract(responseBody);
        if (socialUrls.isEmpty()) {
            return auditResult();
        }

        logging.logToOutput("[*] Found " + socialUrls.size() +
                " social link(s) on: " + requestUrl);

        // ── Check each URL ──
        for (String url : socialUrls) {
            Platform platform = PlatformDetector.identify(url);

            // Skip if already cached
            if (cache.contains(url)) {
                int cachedStatus = cache.get(url);
                if (cachedStatus == 1) { // 1 = broken
                    String reason = "Previously detected as broken (cached)";
                    issues.add(createIssue(baseRequestResponse, url, platform, reason));
                    resultsPanel.addResult(platform, 0, url, requestUrl, reason);
                }
                continue;
            }

            // Validate the link
            LinkResult result = validator.checkUrl(url);
            boolean broken = PlatformDetector.isBroken(platform, result);

            cache.put(url, broken ? 1 : 0);

            String status = broken ? "BROKEN" : "ALIVE";
            logging.logToOutput("    [" + result.getStatusCode() + "] [" +
                    platform.getDisplayName() + "] [" + status + "] " + url);

            if (broken) {
                String reason = PlatformDetector.getReason(platform, result);
                logging.logToOutput("    [!!!] " + reason);
                issues.add(createIssue(baseRequestResponse, url, platform, reason));
                resultsPanel.addResult(platform, result.getStatusCode(),
                        url, requestUrl, reason);
            }
        }

        return auditResult(issues);
    }

    @Override
    public ConsolidationAction consolidateIssues(AuditIssue newIssue, AuditIssue existingIssue) {
        if (existingIssue.name().equals(newIssue.name())) {
            return KEEP_EXISTING;
        }
        return KEEP_NEW;
    }

    // ===================== Helpers =====================

    private String getContentType(HttpResponse response) {
        try {
            return response.headerValue("Content-Type");
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isScannableContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return true; // If unknown, scan it
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        for (String type : SCANNABLE_TYPES) {
            if (lower.contains(type)) {
                return true;
            }
        }
        return false;
    }

    private AuditIssue createIssue(HttpRequestResponse baseRequestResponse,
            String brokenUrl, Platform platform, String reason) {
        return AuditIssue.auditIssue(
                "Broken Social Media Link [" + platform.getDisplayName() + "]: " + brokenUrl,
                buildDetail(brokenUrl, platform, reason, baseRequestResponse.request().url()),
                buildRemediation(),
                baseRequestResponse.request().url(),
                AuditIssueSeverity.INFORMATION,
                AuditIssueConfidence.CERTAIN,
                buildBackground(),
                buildRemediationBackground(),
                AuditIssueSeverity.INFORMATION,
                baseRequestResponse);
    }

    private String buildDetail(String brokenUrl, Platform platform,
            String reason, String sourceUrl) {
        return "<p>A <b>" + esc(platform.getDisplayName()) +
                "</b> link was found on <b>" + esc(sourceUrl) +
                "</b> and appears to be broken:</p>" +
                "<p><a href=\"" + esc(brokenUrl) + "\">" + esc(brokenUrl) + "</a></p>" +
                "<p><b>Detection:</b> " + esc(reason) + "</p>" +
                "<p>An attacker could register this handle on " +
                esc(platform.getDisplayName()) +
                " to impersonate the organization.</p>";
    }

    private String buildBackground() {
        return "<p><b>Broken Link Hijacking</b> occurs when a website links to a " +
                "social media profile that no longer exists. An attacker can claim the " +
                "abandoned username.</p>" +
                "<p>This check uses <b>platform-specific detection</b> (not just HTTP 404) " +
                "to catch soft-404s.</p>";
    }

    private String buildRemediation() {
        return "<p>Verify the account exists. Update or remove the broken link.</p>";
    }

    private String buildRemediationBackground() {
        return "<p>Regularly audit external social media links on your web properties.</p>";
    }

    private static String esc(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
