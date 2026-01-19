package com.bdc.cli;

import com.bdc.emitter.CsvEmitter;
import com.bdc.emitter.MetadataEmitter;
import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "generate",
    description = "Generate calendar events for a date range"
)
public class GenerateCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "The calendar ID to generate")
    private String calendarId;

    @Option(names = {"--from", "-f"}, description = "Start date (ISO format)", required = true)
    private LocalDate from;

    @Option(names = {"--to", "-t"}, description = "End date (ISO format)", required = true)
    private LocalDate to;

    @Option(names = {"--out", "-o"}, description = "Output directory", required = true)
    private Path outputDir;

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

            SpecResolver resolver = new SpecResolver(registry);
            ResolvedSpec resolved = resolver.resolve(calendarId);

            EventGenerator generator = new EventGenerator();
            List<Event> events = generator.generate(resolved, from, to);

            // Ensure output directory exists
            Files.createDirectories(outputDir);

            // Emit CSV
            CsvEmitter csvEmitter = new CsvEmitter();
            Path csvPath = outputDir.resolve("events.csv");
            csvEmitter.emit(events, csvPath);

            // Emit metadata
            MetadataEmitter metadataEmitter = new MetadataEmitter();
            Path metadataPath = outputDir.resolve("metadata.json");
            metadataEmitter.emit(resolved, events, from, to, metadataPath);

            System.out.println("Generated " + events.size() + " events");
            System.out.println("  CSV: " + csvPath);
            System.out.println("  Metadata: " + metadataPath);

            return 0;
        } catch (Exception e) {
            System.err.println("Generation failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
