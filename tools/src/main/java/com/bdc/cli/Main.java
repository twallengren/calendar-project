package com.bdc.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "bdc",
    description = "Business Day Calendar tool for generating calendar artifacts",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    subcommands = {
        ValidateCommand.class,
        ResolveCommand.class,
        GenerateCommand.class,
        QueryCommand.class,
        HistoryCommand.class,
        DiffCommand.class
    }
)
public class Main implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
