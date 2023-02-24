/*
 * Copyright (c) 2023 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository https://github.com/whichlicense/cli.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.whichlicense.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import static java.lang.System.exit;

/**
 * The WhichLicense platform CLI base command.
 *
 * @author David Greven
 * @version 0
 * @since 0.0.0
 */
@Command(name = "whichlicense", description = "WhichLicense platform CLI", version = "0.0.0",
        usageHelpAutoWidth = true, showEndOfOptionsDelimiterInUsageHelp = true,
        mixinStandardHelpOptions = true, showAtFileInUsageHelp = true, requiredOptionMarker = '*')
public class Entrypoint implements Runnable {
    /**
     * The primary CLI entry point.
     *
     * @param args The commands, subcommands, options, flags and arguments supplied to the CLI.
     * @since 0.0.0
     */
    public static void main(String[] args) {
        exit(new CommandLine(new Entrypoint()).execute(args));
    }

    @Override
    public void run() {
        System.out.println("Coming Soon");
    }
}
