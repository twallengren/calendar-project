package com.bdc.cli;

import com.bdc.loader.SpecRegistry;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "resolve",
    description = "Resolve a calendar specification (flatten extends/uses)"
)
public class ResolveCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "The calendar ID to resolve")
    private String calendarId;

    @Option(names = {"--out", "-o"}, description = "Output file path")
    private Path outputPath;

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

            ObjectMapper yamlMapper = new ObjectMapper(
                new YAMLFactory()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            )
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            String yaml = yamlMapper.writeValueAsString(resolved);

            if (outputPath != null) {
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, yaml);
                System.out.println("Resolved spec written to: " + outputPath);
            } else {
                System.out.println(yaml);
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Resolution failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
