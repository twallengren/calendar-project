package com.bdc.loader;

import com.bdc.model.CalendarSpec;
import com.bdc.model.ModuleSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class SpecRegistry {

  private final Map<String, CalendarSpec> calendars = new HashMap<>();
  private final Map<String, ModuleSpec> modules = new HashMap<>();
  private final YamlLoader loader;

  public SpecRegistry() {
    this.loader = new YamlLoader();
  }

  public void loadCalendarsFromDirectory(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      paths
          .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
          .filter(Files::isRegularFile)
          .forEach(this::loadCalendarSafe);
    }
  }

  public void loadModulesFromDirectory(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(dir)) {
      paths
          .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
          .filter(Files::isRegularFile)
          .forEach(this::loadModuleSafe);
    }
  }

  private void loadCalendarSafe(Path path) {
    try {
      CalendarSpec spec = loader.loadCalendar(path);
      calendars.put(spec.id(), spec);
    } catch (IOException e) {
      System.err.println("Warning: Failed to load calendar from " + path + ": " + e.getMessage());
    }
  }

  private void loadModuleSafe(Path path) {
    try {
      ModuleSpec spec = loader.loadModule(path);
      modules.put(spec.id(), spec);
    } catch (IOException e) {
      System.err.println("Warning: Failed to load module from " + path + ": " + e.getMessage());
    }
  }

  public Optional<CalendarSpec> getCalendar(String id) {
    return Optional.ofNullable(calendars.get(id));
  }

  public Optional<ModuleSpec> getModule(String id) {
    return Optional.ofNullable(modules.get(id));
  }

  public Map<String, CalendarSpec> getAllCalendars() {
    return Map.copyOf(calendars);
  }

  public Map<String, ModuleSpec> getAllModules() {
    return Map.copyOf(modules);
  }

  public YamlLoader getLoader() {
    return loader;
  }
}
