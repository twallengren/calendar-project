package com.bdc.cli;

import com.bdc.chronology.ChronologyProviders;
import com.bdc.chronology.NativeDate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "convert",
    description = "Convert exact native civil dates using a named compiler chronology profile",
    mixinStandardHelpOptions = true)
public final class ConvertCommand implements Callable<Integer> {
  @Option(names = "--from-chronology", required = true)
  private String from;

  @Option(names = "--to-chronology", defaultValue = "ISO")
  private String to;

  @Option(names = "--year", required = true)
  private int year;

  @Option(names = "--month-code", required = true)
  private String monthCode;

  @Option(names = "--day", required = true)
  private int day;

  @Option(names = "--format", defaultValue = "text", description = "text or json")
  private String format;

  @Spec private CommandSpec spec;

  public Integer call() throws Exception {
    if (!format.equals("text") && !format.equals("json"))
      throw new IllegalArgumentException("--format must be text or json");
    var source = ChronologyProviders.get(from);
    var target = ChronologyProviders.get(to);
    var input = new NativeDate(from, year, monthCode, day);
    var iso = source.toIso(input);
    var result = target.fromIso(iso);
    if (format.equals("json")) {
      spec.commandLine()
          .getOut()
          .println(
              new ObjectMapper()
                  .setPropertyNamingStrategy(
                      com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                  .writeValueAsString(
                      Map.of(
                          "input",
                          input,
                          "output",
                          result,
                          "iso_date",
                          iso.toString(),
                          "source_profile",
                          source.descriptor().profile(),
                          "source_provider",
                          source.descriptor().provider(),
                          "target_profile",
                          target.descriptor().profile(),
                          "target_provider",
                          target.descriptor().provider())));
    } else {
      spec.commandLine().getOut().println(to.equalsIgnoreCase("ISO") ? iso : result);
    }
    return 0;
  }
}
