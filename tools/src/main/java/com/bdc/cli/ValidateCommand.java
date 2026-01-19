package com.bdc.cli;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.CalendarSpec;
import com.bdc.resolver.SpecResolver;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "validate",
    description = "Validate a calendar specification"
)
public class ValidateCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "The calendar ID to validate")
    private String calendarId;

    @Option(names = {"--calendars-dir"}, description = "Calendars directory", defaultValue = "calendars")
    private Path calendarsDir;

    @Option(names = {"--modules-dir"}, description = "Modules directory", defaultValue = "modules")
    private Path modulesDir;

    @Override
    public Integer call() {
        try {
            SpecRegistry registry = new SpecRegistry();
            registry.loadCalendarsFromDirectory(calendarsDir);
            registry.loadModulesFromDirectory(modulesDir);

            CalendarSpec spec = registry.getCalendar(calendarId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar not found: " + calendarId));

            SpecResolver resolver = new SpecResolver(registry);
            resolver.resolve(calendarId);

            System.out.println("Calendar '" + calendarId + "' is valid.");
            System.out.println("  Name: " + (spec.metadata() != null ? spec.metadata().name() : "N/A"));
            System.out.println("  Extends: " + spec.extendsList());
            System.out.println("  Uses: " + spec.uses());
            System.out.println("  Event sources: " + spec.eventSources().size());

            return 0;
        } catch (Exception e) {
            System.err.println("Validation failed: " + e.getMessage());
            return 1;
        }
    }
}
