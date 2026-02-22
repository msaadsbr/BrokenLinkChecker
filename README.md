# Broken Link Hijacker (Burp Suite Extension)

A high-performance Burp Suite extension written in **Java** using the **Montoya API**. This tool automatically scans HTTP responses for social media links (X, Instagram, YouTube, etc.) and verifies their status to identify potential **Broken Link Hijacking** vulnerabilities.

## 🚀 Key Features

- **Passive Monitoring:** Watches traffic as you browse; no manual intervention needed.
- **Asynchronous Validation:** Uses multi-threading to check links without lagging the Burp UI.
- **Smart Filtering:** Uses a global cache to avoid redundant requests to the same URL.
- **Target Scope Aware:** (Planned) Focuses only on URLs within your defined Burp scope.

## 🛠️ Building from Source

### Prerequisites

- **Java 17+**
- **Gradle** (the wrapper is included)

### Build

```bash
./gradlew shadowJar
```

The output JAR will be at: `build/libs/BrokenLinkHijacker.jar`

## 📦 Installation

1. Build the JAR (see above) or download the latest release.
2. Open Burp Suite.
3. Go to **Extensions** → **Installed** → **Add**.
4. Select **Java** as the extension type and load `BrokenLinkHijacker.jar`.

## 📝 Usage

Simply browse your target. The extension will:

1. Passively scan every HTTP response for social media links.
2. Check each link against the target platform (HEAD request with GET fallback).
3. Report any **404** responses as "Broken Social Media Link" issues in the **Dashboard**.

### Supported Platforms

| Platform  | URL Pattern                            |
| --------- | -------------------------------------- |
| X/Twitter | `twitter.com/*`, `x.com/*`             |
| Instagram | `instagram.com/*`                      |
| YouTube   | `youtube.com/@*`, `/channel/*`, `/c/*` |
| Facebook  | `facebook.com/*`                       |
| LinkedIn  | `linkedin.com/in/*`, `/company/*`      |
| GitHub    | `github.com/*`                         |
| TikTok    | `tiktok.com/@*`                        |

## 🛡️ For Researchers

Finding a broken social link on a high-traffic domain can lead to an easy account takeover bug. This tool automates the "boring" part of that discovery.

## 📁 Project Structure

```
src/main/java/dev/msaad/burp/
├── BrokenLinkHijacker.java      # Extension entry point
├── SocialLinkScanCheck.java     # Passive scan check implementation
├── SocialLinkExtractor.java     # Regex-based link extractor
├── LinkValidator.java           # HTTP link checker
└── LinkCache.java               # Thread-safe URL cache
```

---

**Developed by [msaadsbr](https://linkedin.com/in/msaadsbr)**
