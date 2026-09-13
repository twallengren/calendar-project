package com.bdc.cli;

import com.bdc.artifact.ReleaseHistoryStore;
import com.bdc.chronology.DateRange;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.resolver.SpecResolver;
import com.bdc.stream.DateStream;
import com.bdc.stream.JointDateStream;
import com.bdc.stream.LazyDateStream;
import com.bdc.stream.OutsideCoverageException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "query",
    description =
        "Query one calendar, or several jointly, for business days, events and settlement dates")
public class QueryCommand implements Callable<Integer> {

  private static final Pattern SETTLEMENT = Pattern.compile("^T?\\+?(\\d+)$");

  @Parameters(
      index = "0",
      arity = "0..1",
      description =
          "Calendar id, or a comma-separated list (US-NYSE,SA-TADAWUL) queried jointly: a date is"
              + " a business day only when every listed calendar trades on it. Optional when the"
              + " query names its calendars itself (--open-in/--closed-in)")
  private String calendarIds;

  @Option(
      names = {"--is-business-day"},
      description = "Check if a date is a business day")
  private LocalDate isBusinessDayDate;

  @Option(
      names = {"--events-on"},
      description = "Get events on a specific date")
  private LocalDate eventsOnDate;

  @Option(
      names = {"--next-business-day"},
      description = "Find the next business day after a date")
  private LocalDate nextBusinessDayDate;

  @Option(
      names = {"--prev-business-day"},
      description = "Find the previous business day before a date")
  private LocalDate prevBusinessDayDate;

  @Option(
      names = {"--business-days-from"},
      description = "Start date for business day count")
  private LocalDate businessDaysFrom;

  @Option(
      names = {"--business-days-to"},
      description = "End date for business day count")
  private LocalDate businessDaysTo;

  @Option(
      names = {"--nth-business-day"},
      description = "Find the nth business day from a date (use with --from)")
  private Integer nthBusinessDay;

  @Option(
      names = {"--settlement"},
      description =
          "Settlement date for a trade date given by --from, as T+N (e.g. T+2): the Nth business"
              + " day after the trade date on the joint calendar, with the closures in between")
  private String settlement;

  @Option(
      names = {"--is-early-close"},
      description = "Check whether a date is a shortened session")
  private LocalDate isEarlyCloseDate;

  @Option(
      names = {"--close-time"},
      description = "Print the early close time for a date, if any")
  private LocalDate closeTimeDate;

  @Option(
      names = {"--status"},
      description = "Print the confidence status for a date (CONFIRMED, PROJECTED, UNKNOWN)")
  private LocalDate statusDate;

  @Option(
      names = {"--verified-through"},
      description = "Print the covered range and the date the data is verified through")
  private boolean verifiedThrough;

  @Option(
      names = {"--open-in"},
      description =
          "With --closed-in, --from and --to: list dates open in these calendars (comma-separated)")
  private String openIn;

  @Option(
      names = {"--closed-in"},
      description = "With --open-in: the calendars that must be closed on those dates")
  private String closedIn;

  @Option(
      names = {"--from", "-f"},
      description =
          "Reference date: trade date for --settlement, start for --nth-business-day and"
              + " --open-in/--closed-in")
  private LocalDate from;

  @Option(
      names = {"--to", "-t"},
      description = "End date for --open-in/--closed-in")
  private LocalDate to;

  @Option(
      names = {"--as-of"},
      description =
          "Answer from a published artifact instead of the current YAML: 'blessed', a release"
              + " version (v10.1.0), or an ISO date/instant (the release current at that time)")
  private String asOf;

  @Option(
      names = {"--blessed-dir"},
      description = "Blessed artifacts directory (for --as-of)",
      defaultValue = "blessed")
  private Path blessedDir;

  @Option(
      names = {"--release-history-dir"},
      description = "Release history directory (for --as-of)",
      defaultValue = "release-history")
  private Path releaseHistoryDir;

  @Option(
      names = {"--calendars-dir"},
      description = "Calendars directory",
      defaultValue = "calendars")
  private Path calendarsDir;

  @Option(
      names = {"--modules-dir"},
      description = "Modules directory",
      defaultValue = "modules")
  private Path modulesDir;

  private SpecResolver resolver;
  private ReleaseHistoryStore store;
  private DateStream mainStream;

  @Override
  public Integer call() {
    try {
      boolean anyQuery = false;

      if (isBusinessDayDate != null) {
        anyQuery = true;
        DateStream stream = stream();
        boolean isBusiness = stream.isBusinessDay(isBusinessDayDate);
        System.out.println(
            isBusinessDayDate + " is " + (isBusiness ? "a business day" : "NOT a business day"));
        if (!isBusiness) {
          List<Event> events = stream.eventsOn(isBusinessDayDate);
          if (!events.isEmpty()) {
            System.out.println(
                "  Reason: " + events.get(0).description() + " (" + events.get(0).type() + ")");
          } else {
            System.out.println("  Reason: Weekend");
          }
          if (stream instanceof JointDateStream) {
            System.out.println(
                "  Closed in: " + String.join(", ", closedMemberIds(stream, isBusinessDayDate)));
          }
        }
      }

      if (eventsOnDate != null) {
        anyQuery = true;
        List<Event> events = stream().eventsOn(eventsOnDate);
        if (events.isEmpty()) {
          System.out.println("No events on " + eventsOnDate);
        } else {
          System.out.println("Events on " + eventsOnDate + ":");
          for (Event event : events) {
            System.out.println(
                "  - "
                    + event.description()
                    + " ("
                    + event.type()
                    + (event.sourceModule() != null ? ", " + event.sourceModule() : "")
                    + ")");
          }
        }
      }

      if (nextBusinessDayDate != null) {
        anyQuery = true;
        LocalDate next = stream().nextBusinessDay(nextBusinessDayDate);
        System.out.println("Next business day after " + nextBusinessDayDate + ": " + next);
      }

      if (prevBusinessDayDate != null) {
        anyQuery = true;
        LocalDate prev = stream().prevBusinessDay(prevBusinessDayDate);
        System.out.println("Previous business day before " + prevBusinessDayDate + ": " + prev);
      }

      if (businessDaysFrom != null && businessDaysTo != null) {
        anyQuery = true;
        long count = stream().businessDaysInRange(businessDaysFrom, businessDaysTo);
        System.out.println(
            "Business days from " + businessDaysFrom + " to " + businessDaysTo + ": " + count);
      }

      if (nthBusinessDay != null) {
        anyQuery = true;
        LocalDate refDate = from != null ? from : LocalDate.now();
        LocalDate nth = stream().nthBusinessDay(refDate, nthBusinessDay);
        String direction = nthBusinessDay > 0 ? "after" : "before";
        System.out.println(
            Math.abs(nthBusinessDay) + " business days " + direction + " " + refDate + ": " + nth);
      }

      if (settlement != null) {
        anyQuery = true;
        printSettlement(stream());
      }

      if (isEarlyCloseDate != null) {
        anyQuery = true;
        DateStream stream = stream();
        boolean early = stream.isEarlyClose(isEarlyCloseDate);
        System.out.println(
            isEarlyCloseDate + " is " + (early ? "an early close" : "NOT an early close"));
        if (early) {
          System.out.println("  Closes at: " + stream.closeTime(isEarlyCloseDate).orElseThrow());
        }
      }

      if (closeTimeDate != null) {
        anyQuery = true;
        DateStream stream = stream();
        Optional<LocalTime> closeTime = stream.closeTime(closeTimeDate);
        if (closeTime.isPresent()) {
          System.out.println("Close time on " + closeTimeDate + ": " + closeTime.get());
        } else if (stream.isBusinessDay(closeTimeDate)) {
          System.out.println("Close time on " + closeTimeDate + ": regular session");
        } else {
          System.out.println("Close time on " + closeTimeDate + ": closed");
        }
      }

      if (statusDate != null) {
        anyQuery = true;
        DateStream stream = stream();
        EventStatus status = stream.status(statusDate);
        StringBuilder line =
            new StringBuilder("Status of " + statusDate + " on " + stream.calendarId() + ": ")
                .append(status);
        if (status == EventStatus.PROJECTED
            && stream.verifiedThrough().map(statusDate::isAfter).orElse(false)) {
          line.append(" (after verified_through ")
              .append(stream.verifiedThrough().orElseThrow())
              .append(")");
        } else if (status == EventStatus.UNKNOWN) {
          line.append(" (outside ").append(formatRange(stream.range())).append(")");
        }
        System.out.println(line);
      }

      if (verifiedThrough) {
        anyQuery = true;
        DateStream stream = stream();
        System.out.println(stream.calendarId() + " covered range: " + formatRange(stream.range()));
        System.out.println(
            stream.calendarId()
                + " verified through: "
                + stream.verifiedThrough().map(LocalDate::toString).orElse("not declared"));
      }

      if (openIn != null || closedIn != null) {
        anyQuery = true;
        printOpenClosed();
      }

      if (!anyQuery) {
        printUsage();
      }

      return 0;
    } catch (OutsideCoverageException e) {
      System.err.println("Query failed: " + e.getMessage());
      return 1;
    } catch (IllegalArgumentException | IllegalStateException e) {
      System.err.println("Query failed: " + e.getMessage());
      return 1;
    } catch (Exception e) {
      System.err.println("Query failed: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }

  // === Stream construction ===

  /** The stream for the positional calendar argument, built once on first use. */
  private DateStream stream() throws Exception {
    if (mainStream == null) {
      if (calendarIds == null) {
        throw new IllegalArgumentException(
            "This query needs a calendar id (or a comma-separated list) as the first argument");
      }
      mainStream = build(calendarIds);
    }
    return mainStream;
  }

  /** Builds one stream for a comma-separated calendar list, jointly when more than one is given. */
  private DateStream build(String ids) throws Exception {
    List<DateStream> members = new ArrayList<>();
    for (String id : splitIds(ids)) {
      members.add(member(id));
    }
    return JointDateStream.joint(members);
  }

  private static List<String> splitIds(String ids) {
    List<String> result = new ArrayList<>();
    for (String part : ids.split(",")) {
      String id = part.strip();
      if (!id.isEmpty()) {
        result.add(id);
      }
    }
    if (result.isEmpty()) {
      throw new IllegalArgumentException("No calendar id given");
    }
    return result;
  }

  private DateStream member(String id) throws Exception {
    if (asOf != null) {
      if (store == null) {
        store = new ReleaseHistoryStore(releaseHistoryDir, blessedDir);
      }
      ReleaseHistoryStore.Snapshot snapshot =
          store
              .resolve(id, asOf)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "No published artifact of " + id + " matches '" + asOf + "'"));
      System.out.println(
          "Using artifact "
              + snapshot.calendarId()
              + " "
              + snapshot.id()
              + " (v"
              + snapshot.version()
              + ", archived "
              + snapshot.archivedAt()
              + ")");
      return store.stream(snapshot);
    }
    if (resolver == null) {
      SpecRegistry registry = new SpecRegistry();
      registry.loadCalendarsFromDirectory(calendarsDir);
      registry.loadModulesFromDirectory(modulesDir);
      registry.assertNoLoadErrors();
      resolver = new SpecResolver(registry);
    }
    return new LazyDateStream(resolver.resolve(id));
  }

  // === Composite queries ===

  private void printSettlement(DateStream stream) {
    if (from == null) {
      throw new IllegalArgumentException("--settlement needs a trade date: --from <date>");
    }
    Matcher m = SETTLEMENT.matcher(settlement.strip().toUpperCase(Locale.ROOT));
    if (!m.matches()) {
      throw new IllegalArgumentException(
          "--settlement must look like T+2 (or T+0), got: " + settlement);
    }
    int n = Integer.parseInt(m.group(1));
    LocalDate settles = stream.nthBusinessDay(from, n);
    System.out.println(
        "Trade date "
            + from
            + " ("
            + dayOfWeek(from)
            + ") on "
            + stream.calendarId()
            + ", T+"
            + n
            + " settles "
            + settles
            + " ("
            + dayOfWeek(settles)
            + ")");
    int counted = 0;
    for (LocalDate d = from.plusDays(1); !d.isAfter(settles); d = d.plusDays(1)) {
      List<String> closed = closedMemberIds(stream, d);
      if (closed.isEmpty()) {
        counted++;
        System.out.println(
            "  " + d + " " + dayOfWeek(d) + ": business day " + counted + " of " + n);
      } else {
        System.out.println(
            "  " + d + " " + dayOfWeek(d) + ": closed in " + String.join(", ", closed));
      }
    }
  }

  private void printOpenClosed() throws Exception {
    if (openIn == null || closedIn == null || from == null || to == null) {
      throw new IllegalArgumentException(
          "--open-in needs --closed-in, --from and --to (e.g. --open-in US-NYSE --closed-in"
              + " SA-TADAWUL --from 2026-01-01 --to 2026-01-31)");
    }
    DateStream open = build(openIn);
    DateStream closed = build(closedIn);
    System.out.println(
        "Dates open in "
            + open.calendarId()
            + " and closed in "
            + closed.calendarId()
            + ", "
            + from
            + " to "
            + to
            + ":");
    long count = 0;
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      if (!open.isBusinessDay(d) || closed.isBusinessDay(d)) {
        continue;
      }
      count++;
      List<Event> events = closed.eventsOn(d);
      String reason =
          events.isEmpty()
              ? "weekend"
              : events.get(0).description() + " (" + events.get(0).type() + ")";
      System.out.println("  " + d + " " + dayOfWeek(d) + ": " + reason);
    }
    System.out.println("  " + count + " date" + (count == 1 ? "" : "s"));
  }

  // === Helpers ===

  private static List<String> closedMemberIds(DateStream stream, LocalDate date) {
    if (stream instanceof JointDateStream joint) {
      return joint.closedMembers(date).stream().map(DateStream::calendarId).toList();
    }
    return stream.isBusinessDay(date) ? List.of() : List.of(stream.calendarId());
  }

  private static String dayOfWeek(LocalDate date) {
    return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
  }

  private static String formatRange(DateRange range) {
    String start = range.start().equals(LocalDate.MIN) ? "unbounded" : range.start().toString();
    String end = range.end().equals(LocalDate.MAX) ? "unbounded" : range.end().toString();
    return start + " to " + end;
  }

  private static void printUsage() {
    System.out.println("No query specified. Use one of:");
    System.out.println("  --is-business-day <date>     Check if a date is a business day");
    System.out.println("  --events-on <date>           Get events on a specific date");
    System.out.println("  --next-business-day <date>   Find the next business day");
    System.out.println("  --prev-business-day <date>   Find the previous business day");
    System.out.println(
        "  --business-days-from <date> --business-days-to <date>  Count business days");
    System.out.println("  --nth-business-day <n> --from <date>  Find nth business day");
    System.out.println(
        "  --settlement T+N --from <trade date>  Settlement date and the closures in between");
    System.out.println("  --is-early-close <date>      Check for a shortened session");
    System.out.println("  --close-time <date>          Early close time, if any");
    System.out.println("  --status <date>              CONFIRMED, PROJECTED or UNKNOWN");
    System.out.println("  --verified-through           Covered range and verified-through date");
    System.out.println(
        "  --open-in <cals> --closed-in <cals> --from <date> --to <date>  Dates open in one"
            + " calendar and closed in another");
    System.out.println("  --as-of <blessed|vX.Y.Z|date>  Answer from a published artifact");
    System.out.println();
    System.out.println(
        "The calendar argument accepts a comma-separated list (US-NYSE,SA-TADAWUL): a date is a"
            + " business day only when every listed calendar trades on it.");
  }
}
