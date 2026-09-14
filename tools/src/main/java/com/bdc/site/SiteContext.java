package com.bdc.site;

import java.time.Instant;

/**
 * Build-wide settings shared by every page renderer: where the site will be served from, what it is
 * called, and which release it was built from.
 *
 * <p>Internal links are always <em>relative</em> so the output works unchanged at a domain root, in
 * a GitHub Pages project subpath, or opened straight off disk with {@code file://}. {@code baseUrl}
 * is used only where an absolute URL is required by the format: canonical links, the sitemap,
 * {@code robots.txt} and the {@code webcal://} subscribe link.
 */
public record SiteContext(
    String baseUrl,
    String siteName,
    String repoUrl,
    String releaseVersion,
    String releaseGitSha,
    String releaseDate,
    Instant generatedAt) {

  public SiteContext {
    baseUrl = baseUrl == null || baseUrl.isBlank() ? "/" : baseUrl;
    if (!baseUrl.endsWith("/")) {
      baseUrl = baseUrl + "/";
    }
  }

  /** {@code ""}, {@code "../"}, {@code "../../"} … for a page nested {@code depth} levels down. */
  public static String rootPrefix(int depth) {
    return "../".repeat(depth);
  }

  /** The absolute canonical URL for a site-relative path such as {@code "US-NYSE/2027/"}. */
  public String canonical(String relativePath) {
    return baseUrl + relativePath;
  }

  /**
   * The {@code webcal://} form of a site-relative path, or null when {@code --base-url} is not an
   * absolute {@code http(s)} URL (a subscribe link needs an absolute host to be useful).
   */
  public String webcal(String relativePath) {
    String url = canonical(relativePath);
    if (url.startsWith("https://")) {
      return "webcal://" + url.substring("https://".length());
    }
    if (url.startsWith("http://")) {
      return "webcal://" + url.substring("http://".length());
    }
    return null;
  }

  /** Link into the repository, e.g. {@code blob("blessed/US-NYSE/events.csv")}. */
  public String repoBlob(String path) {
    return repoUrl + "/blob/main/" + path;
  }

  /** Link to a repository directory listing, e.g. {@code tree("sources")}. */
  public String repoTree(String path) {
    return repoUrl + "/tree/main/" + path;
  }
}
