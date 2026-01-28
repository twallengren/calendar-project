package com.bdc.cli;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import com.bdc.stream.DateStream;
import com.bdc.stream.LazyDateStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "query", description = "Query a calendar for business day information")
public class QueryCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "The calendar ID to query")
  private String calendarId;

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
      names = {"--from", "-f"},
      description = "Reference date for nth-business-day query")
  private LocalDate from;

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

  @Override
  public Integer call() {
    try {
      SpecRegistry registry = new SpecRegistry();
      registry.loadCalendarsFromDirectory(calendarsDir);
      registry.loadModulesFromDirectory(modulesDir);

      SpecResolver resolver = new SpecResolver(registry);
      ResolvedSpec spec = resolver.resolve(calendarId);
      DateStream stream = new LazyDateStream(spec);

      boolean anyQuery = false;

      if (isBusinessDayDate != null) {
        anyQuery = true;
        boolean isBusiness = stream.isBusinessDay(isBusinessDayDate);
        System.out.println(
            isBusinessDayDate + " is " + (isBusiness ? "a business day" : "NOT a business day"));

        if (!isBusiness) {
          // Show why it's not a business day
          List<Event> events = stream.eventsOn(isBusinessDayDate);
          if (!events.isEmpty()) {
            System.out.println(
                "  Reason: " + events.get(0).description() + " (" + events.get(0).type() + ")");
          } else {
            System.out.println("  Reason: Weekend");
          }
        }
      }

      if (eventsOnDate != null) {
        anyQuery = true;
        List<Event> events = stream.eventsOn(eventsOnDate);
        if (events.isEmpty()) {
          System.out.println("No events on " + eventsOnDate);
        } else {
          System.out.println("Events on " + eventsOnDate + ":");
          for (Event event : events) {
            System.out.println("  - " + event.description() + " (" + event.type() + ")");
          }
        }
      }

      if (nextBusinessDayDate != null) {
        anyQuery = true;
        LocalDate next = stream.nextBusinessDay(nextBusinessDayDate);
        System.out.println("Next business day after " + nextBusinessDayDate + ": " + next);
      }

      if (prevBusinessDayDate != null) {
        anyQuery = true;
        LocalDate prev = stream.prevBusinessDay(prevBusinessDayDate);
        System.out.println("Previous business day before " + prevBusinessDayDate + ": " + prev);
      }

      if (businessDaysFrom != null && businessDaysTo != null) {
        anyQuery = true;
        long count = stream.businessDaysInRange(businessDaysFrom, businessDaysTo);
        System.out.println(
            "Business days from " + businessDaysFrom + " to " + businessDaysTo + ": " + count);
      }

      if (nthBusinessDay != null) {
        anyQuery = true;
        LocalDate refDate = from != null ? from : LocalDate.now();
        LocalDate nth = stream.nthBusinessDay(refDate, nthBusinessDay);
        String direction = nthBusinessDay > 0 ? "after" : "before";
        System.out.println(
            Math.abs(nthBusinessDay) + " business days " + direction + " " + refDate + ": " + nth);
      }

      if (!anyQuery) {
        System.out.println("No query specified. Use one of:");
        System.out.println("  --is-business-day <date>     Check if a date is a business day");
        System.out.println("  --events-on <date>           Get events on a specific date");
        System.out.println("  --next-business-day <date>   Find the next business day");
        System.out.println("  --prev-business-day <date>   Find the previous business day");
        System.out.println(
            "  --business-days-from <date> --business-days-to <date>  Count business days");
        System.out.println("  --nth-business-day <n> --from <date>  Find nth business day");
      }

      return 0;
    } catch (Exception e) {
      System.err.println("Query failed: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }
}
