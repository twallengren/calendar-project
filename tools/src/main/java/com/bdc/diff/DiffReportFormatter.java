package com.bdc.diff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DiffReportFormatter {

  private static final int DEFAULT_MAX_DIFFS_PER_SECTION = 15;

  private final ObjectMapper mapper;
  private final int maxDiffsPerSection;

  public DiffReportFormatter() {
    this(DEFAULT_MAX_DIFFS_PER_SECTION);
  }

  public DiffReportFormatter(int maxDiffsPerSection) {
    this.maxDiffsPerSection = maxDiffsPerSection;
    this.mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public String formatAsJson(DiffReport report) {
    try {
      Map<String, Object> json = new LinkedHashMap<>();

      // Summary
      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("total_calendars", report.totalCalendars());
      summary.put("calendars_with_changes", report.calendarsWithChanges());
      summary.put("overall_severity", report.overallSeverity().name());
      summary.put("generated_at", report.generatedAt().toString());
      summary.put("blessed_version", report.blessedVersion());
      summary.put("current_sha", report.currentSha());
      json.put("summary", summary);

      // Calendars
      Map<String, Object> calendars = new LinkedHashMap<>();
      for (var entry : report.calendars().entrySet()) {
        CalendarDiff diff = entry.getValue();
        Map<String, Object> calJson = new LinkedHashMap<>();
        calJson.put("severity", diff.severity().name());
        calJson.put("additions", formatEventDiffs(diff.additions()));
        calJson.put("removals", formatEventDiffs(diff.removals()));
        calJson.put("modifications", formatEventDiffs(diff.modifications()));
        calendars.put(entry.getKey(), calJson);
      }
      json.put("calendars", calendars);

      return mapper.writeValueAsString(json);
    } catch (Exception e) {
      throw new RuntimeException("Failed to format JSON report", e);
    }
  }

  private List<Map<String, Object>> formatEventDiffs(List<EventDiff> diffs) {
    return diffs.stream()
        .map(
            d -> {
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("date", d.date().toString());
              if (d.oldType() != null) map.put("old_type", d.oldType().name());
              if (d.newType() != null) map.put("new_type", d.newType().name());
              if (d.oldDescription() != null) map.put("old_description", d.oldDescription());
              if (d.newDescription() != null) map.put("new_description", d.newDescription());
              map.put("is_historical", d.isHistorical(java.time.LocalDate.now()));
              return map;
            })
        .collect(Collectors.toList());
  }

  public String formatAsMarkdown(DiffReport report) {
    StringBuilder sb = new StringBuilder();

    sb.append("## Calendar Diff Report\n\n");

    // Overall severity badge
    String severityIcon =
        switch (report.overallSeverity()) {
          case NONE -> ":white_check_mark:";
          case MINOR -> ":yellow_circle:";
          case MAJOR -> ":red_circle:";
        };
    sb.append("**Overall Severity:** ")
        .append(severityIcon)
        .append(" ")
        .append(report.overallSeverity().name())
        .append("\n\n");

    // Summary table
    sb.append("### Summary\n");
    sb.append("| Calendar | Severity | Added | Removed | Modified |\n");
    sb.append("|----------|----------|-------|---------|----------|\n");

    for (var entry : report.calendars().entrySet()) {
      CalendarDiff diff = entry.getValue();
      String icon =
          switch (diff.severity()) {
            case NONE -> ":white_check_mark:";
            case MINOR -> ":yellow_circle:";
            case MAJOR -> ":red_circle:";
          };
      sb.append("| ")
          .append(entry.getKey())
          .append(" | ")
          .append(icon)
          .append(" ")
          .append(diff.severity().name())
          .append(" | ")
          .append(diff.additions().size())
          .append(" | ")
          .append(diff.removals().size())
          .append(" | ")
          .append(diff.modifications().size())
          .append(" |\n");
    }
    sb.append("\n");

    // Details for each calendar with changes
    for (var entry : report.calendars().entrySet()) {
      CalendarDiff diff = entry.getValue();
      if (!diff.hasChanges()) continue;

      sb.append("### ").append(entry.getKey()).append(" Changes\n\n");

      if (!diff.removals().isEmpty()) {
        sb.append("#### Removed Events :warning:\n");
        sb.append("| Date | Type | Description | Historical? |\n");
        sb.append("|------|------|-------------|-------------|\n");
        int removalCount = 0;
        for (EventDiff e : diff.removals()) {
          if (removalCount >= maxDiffsPerSection) {
            break;
          }
          sb.append("| ")
              .append(e.date())
              .append(" | ")
              .append(e.oldType())
              .append(" | ")
              .append(e.oldDescription())
              .append(" | ")
              .append(e.isHistorical(diff.cutoffDate()) ? "Yes" : "No")
              .append(" |\n");
          removalCount++;
        }
        if (diff.removals().size() > maxDiffsPerSection) {
          sb.append("\n*...and ")
              .append(diff.removals().size() - maxDiffsPerSection)
              .append(" more removed events*\n");
        }
        sb.append("\n");
      }

      if (!diff.additions().isEmpty()) {
        sb.append("#### Added Events\n");
        sb.append("| Date | Type | Description | Historical? |\n");
        sb.append("|------|------|-------------|-------------|\n");
        int additionCount = 0;
        for (EventDiff e : diff.additions()) {
          if (additionCount >= maxDiffsPerSection) {
            break;
          }
          sb.append("| ")
              .append(e.date())
              .append(" | ")
              .append(e.newType())
              .append(" | ")
              .append(e.newDescription())
              .append(" | ")
              .append(e.isHistorical(diff.cutoffDate()) ? "Yes" : "No")
              .append(" |\n");
          additionCount++;
        }
        if (diff.additions().size() > maxDiffsPerSection) {
          sb.append("\n*...and ")
              .append(diff.additions().size() - maxDiffsPerSection)
              .append(" more added events*\n");
        }
        sb.append("\n");
      }

      if (!diff.modifications().isEmpty()) {
        sb.append("#### Modified Events :warning:\n");
        sb.append("| Date | Old Type | New Type | Old Description | New Description |\n");
        sb.append("|------|----------|----------|-----------------|------------------|\n");
        int modificationCount = 0;
        for (EventDiff e : diff.modifications()) {
          if (modificationCount >= maxDiffsPerSection) {
            break;
          }
          sb.append("| ")
              .append(e.date())
              .append(" | ")
              .append(e.oldType())
              .append(" | ")
              .append(e.newType())
              .append(" | ")
              .append(e.oldDescription())
              .append(" | ")
              .append(e.newDescription())
              .append(" |\n");
          modificationCount++;
        }
        if (diff.modifications().size() > maxDiffsPerSection) {
          sb.append("\n*...and ")
              .append(diff.modifications().size() - maxDiffsPerSection)
              .append(" more modified events*\n");
        }
        sb.append("\n");
      }
    }

    // Footer with instructions
    sb.append("---\n\n");
    if (report.overallSeverity() == DiffSeverity.MAJOR) {
      sb.append(":information_source: **MAJOR changes require explicit approval.** ")
          .append("Add the `calendar-change-approved` label to proceed.\n");
    } else if (report.overallSeverity() == DiffSeverity.MINOR) {
      sb.append(":information_source: **MINOR changes detected (future events only).** ")
          .append("Consider adding the `calendar-change-approved` label.\n");
    }

    return sb.toString();
  }
}
