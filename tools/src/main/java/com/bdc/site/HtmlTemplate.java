package com.bdc.site;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The whole templating layer for the static site: Java text blocks with named {@code {{slots}}},
 * and nothing else. No template engine, no runtime dependency, no partial evaluation.
 *
 * <p>Two slot forms:
 *
 * <ul>
 *   <li>{@code {{name}}} — the bound value is HTML-escaped before substitution. This is the default
 *       and is what every piece of calendar-derived text (descriptions, module ids, names) uses.
 *   <li>{@code {{{name}}}} — the bound value is inserted verbatim. Only for HTML this package built
 *       itself (a rendered table, a grid, a nav block).
 * </ul>
 *
 * <p>Rendering is strict in both directions: a slot with no bound value and a bound value with no
 * slot are both errors, so a typo fails the build instead of silently emitting {@code {{tilte}}}
 * into a published page.
 */
public final class HtmlTemplate {

  private static final Pattern SLOT =
      Pattern.compile("\\{\\{\\{([A-Za-z0-9_]+)\\}\\}\\}|\\{\\{([A-Za-z0-9_]+)\\}\\}");

  private final String template;

  private HtmlTemplate(String template) {
    this.template = template;
  }

  public static HtmlTemplate of(String template) {
    return new HtmlTemplate(template);
  }

  /**
   * Renders the template from alternating {@code name, value} pairs.
   *
   * @throws IllegalArgumentException if the arguments are not pairs, a slot has no value, or a
   *     value has no slot
   */
  public String render(String... nameValuePairs) {
    if (nameValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException(
          "Expected alternating name/value pairs, got " + nameValuePairs.length + " arguments");
    }
    Map<String, String> values = new LinkedHashMap<>();
    for (int i = 0; i < nameValuePairs.length; i += 2) {
      String name = nameValuePairs[i];
      String value = nameValuePairs[i + 1];
      if (values.put(name, value == null ? "" : value) != null) {
        throw new IllegalArgumentException("Duplicate value bound for slot: " + name);
      }
    }
    return render(values);
  }

  /** Renders the template from a map of slot name to value. */
  public String render(Map<String, String> values) {
    Set<String> unused = new HashSet<>(values.keySet());
    Matcher matcher = SLOT.matcher(template);
    StringBuilder out = new StringBuilder(template.length() + 256);
    while (matcher.find()) {
      boolean raw = matcher.group(1) != null;
      String name = raw ? matcher.group(1) : matcher.group(2);
      if (!values.containsKey(name)) {
        throw new IllegalArgumentException("No value bound for slot: " + name);
      }
      unused.remove(name);
      String value = values.get(name);
      matcher.appendReplacement(out, Matcher.quoteReplacement(raw ? value : escape(value)));
    }
    matcher.appendTail(out);
    if (!unused.isEmpty()) {
      throw new IllegalArgumentException("Values bound to slots that do not exist: " + unused);
    }
    return out.toString();
  }

  /**
   * Escapes text for both element content and double-quoted attribute values. Single quotes are
   * escaped too so the same method is safe in single-quoted attributes.
   */
  public static String escape(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }
}
