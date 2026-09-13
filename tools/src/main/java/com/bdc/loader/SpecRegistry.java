package com.bdc.loader;

import com.bdc.model.CalendarSpec;
import com.bdc.model.ModuleSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Holds every calendar and module spec loaded from disk.
 *
 * <p>Files that fail to load are not silently skipped: the failure is recorded in {@link
 * #loadErrors()} (and echoed to stderr) so that {@code validate} can report it and other commands
 * can refuse to run against an incomplete registry.
 */
public class SpecRegistry {

  /** A file that could not be loaded, or an id declared by more than one file. */
  public record LoadError(Path path, String message) {
    @Override
    public String toString() {
      return path + ": " + message;
    }
  }

  private final Map<String, CalendarSpec> calendars = new HashMap<>();
  private final Map<String, ModuleSpec> modules = new HashMap<>();
  private final Map<String, Path> calendarPaths = new HashMap<>();
  private final Map<String, Path> modulePaths = new HashMap<>();
  private final List<LoadError> loadErrors = new ArrayList<>();
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
          .sorted()
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
          .sorted()
          .forEach(this::loadModuleSafe);
    }
  }

  private void loadCalendarSafe(Path path) {
    try {
      CalendarSpec spec = loader.loadCalendar(path);
      Path previous = calendarPaths.put(spec.id(), path);
      if (previous != null) {
        recordError(path, "duplicate calendar id '" + spec.id() + "' (also in " + previous + ")");
      }
      calendars.put(spec.id(), spec);
    } catch (IOException | RuntimeException e) {
      recordError(path, "failed to load calendar: " + rootMessage(e));
    }
  }

  private void loadModuleSafe(Path path) {
    try {
      ModuleSpec spec = loader.loadModule(path);
      Path previous = modulePaths.put(spec.id(), path);
      if (previous != null) {
        recordError(path, "duplicate module id '" + spec.id() + "' (also in " + previous + ")");
      }
      modules.put(spec.id(), spec);
    } catch (IOException | RuntimeException e) {
      recordError(path, "failed to load module: " + rootMessage(e));
    }
  }

  private void recordError(Path path, String message) {
    loadErrors.add(new LoadError(path, message));
    System.err.println("Warning: " + path + ": " + message);
  }

  private static String rootMessage(Throwable e) {
    Throwable t = e;
    while (t.getCause() != null && t.getCause() != t) {
      t = t.getCause();
    }
    String msg = t.getMessage() != null ? t.getMessage() : e.getMessage();
    if (msg == null) {
      return e.getClass().getSimpleName();
    }
    // Jackson appends a long location suffix; keep the first line
    int nl = msg.indexOf('\n');
    return nl > 0 ? msg.substring(0, nl) : msg;
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

  /** The file a calendar was loaded from, if known. */
  public Optional<Path> calendarPath(String id) {
    return Optional.ofNullable(calendarPaths.get(id));
  }

  /** The file a module was loaded from, if known. */
  public Optional<Path> modulePath(String id) {
    return Optional.ofNullable(modulePaths.get(id));
  }

  /** Files that failed to load and duplicate ids, in load order. */
  public List<LoadError> loadErrors() {
    return List.copyOf(loadErrors);
  }

  /** Throws if any spec file failed to load. */
  public void assertNoLoadErrors() {
    if (!loadErrors.isEmpty()) {
      StringBuilder sb = new StringBuilder("Spec files failed to load:");
      for (LoadError error : loadErrors) {
        sb.append("\n  ").append(error);
      }
      throw new IllegalStateException(sb.toString());
    }
  }

  public YamlLoader getLoader() {
    return loader;
  }
}
