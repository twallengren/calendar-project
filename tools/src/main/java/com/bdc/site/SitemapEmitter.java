package com.bdc.site;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes {@code sitemap.xml} and {@code robots.txt}.
 *
 * <p>The URL list is discovered by walking the output for {@code index.html} files rather than
 * being accumulated by the renderers, so a page added later cannot silently fall out of the sitemap
 * — including the changelog pages, which a different renderer writes.
 */
public final class SitemapEmitter {

  private final SiteContext context;

  public SitemapEmitter(SiteContext context) {
    this.context = context;
  }

  /** Returns the site-relative directory paths that were listed, in the order written. */
  public List<String> write(Path siteDir) throws IOException {
    List<String> paths = discover(siteDir);

    StringBuilder xml = new StringBuilder(64 * 1024);
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    for (String path : paths) {
      xml.append("  <url><loc>")
          .append(HtmlTemplate.escape(context.canonical(path)))
          .append("</loc></url>\n");
    }
    xml.append("</urlset>\n");
    Files.writeString(siteDir.resolve("sitemap.xml"), xml.toString());

    Files.writeString(
        siteDir.resolve("robots.txt"),
        "User-agent: *\nAllow: /\nSitemap: " + context.canonical("sitemap.xml") + "\n");
    return paths;
  }

  /** Every {@code index.html} under {@code siteDir}, as a site-relative directory path. */
  public static List<String> discover(Path siteDir) throws IOException {
    List<String> paths = new ArrayList<>();
    try (Stream<Path> files = Files.walk(siteDir)) {
      files
          .filter(path -> path.getFileName().toString().equals("index.html"))
          .map(path -> siteDir.relativize(path.getParent()).toString().replace('\\', '/'))
          .map(path -> path.isEmpty() ? "" : path + "/")
          .sorted()
          .forEach(paths::add);
    }
    return paths;
  }
}
