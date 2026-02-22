package dev.msaad.burp;

/**
 * Holds the result of an HTTP link validation, including both
 * the status code and the response body for content-based analysis.
 */
public class LinkResult {

    private final int statusCode;
    private final String body;

    public LinkResult(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body != null ? body : "";
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    /**
     * Returns a truncated body (first N chars) suitable for logging.
     */
    public String getBodyPreview(int maxLength) {
        if (body.length() <= maxLength)
            return body;
        return body.substring(0, maxLength) + "...";
    }
}
