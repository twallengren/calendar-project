package com.bdc.site;

import com.bdc.diff.CalendarDiff;
import com.bdc.site.ChangelogBuilder.Changelog;
import com.bdc.site.ChangelogBuilder.Release;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Re-keys a {@link Changelog}'s per-release calendar maps into calendar-id order.
 *
 * <p>{@link ChangelogBuilder} finishes each release with {@code Map.copyOf}, and {@code
 * java.util.ImmutableCollections} salts its iteration order with a per-JVM value — so two builds of
 * the same data emit the same calendars in a different sequence. That is invisible in a CLI dump
 * and fatal for a published site: every release page and {@code v1/changelog.json} would churn on
 * every build, and the determinism check in {@code SiteGeneratorTest} would fail.
 *
 * <p>Applied by the site build to the changelog before it is written in either format, so the JSON
 * and the HTML agree on order as well as content.
 */
public final class DeterministicChangelog {

  private DeterministicChangelog() {}

  public static Changelog sorted(Changelog changelog) {
    List<Release> releases =
        changelog.releases().stream().map(DeterministicChangelog::sorted).toList();
    return new Changelog(releases);
  }

  private static Release sorted(Release release) {
    Map<String, CalendarDiff> byId = new TreeMap<>(release.calendars());
    return new Release(
        release.version(),
        release.gitSha(),
        release.timestamp(),
        release.blessed(),
        release.severity(),
        release.totalAdditions(),
        release.totalRemovals(),
        release.totalModifications(),
        byId);
  }
}
