/*
 * Copyright (c) 2023 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository https://github.com/whichlicense/cli.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.whichlicense.cli.pattern.processor;

/**
 * The PathPatternProcessor enumeration represents the different types of
 * pattern processing strategies that can be used when working with file paths.
 * <p>
 * The following options are the available:
 * <ul>
 *     <li>REGEX: Uses regular expressions to match patterns in file paths.</li>
 *     <li>GLOB: Uses glob patterns to match patterns in file paths.</li>
 *     <li>GREP: Uses grep patterns to match patterns in file paths.</li>
 *     <li>AWK: Uses awk patterns to match patterns in file paths.</li>
 * </ul>
 */
public enum PathPatternProcessor {
    REGEX, GLOB, GREP, AWK
}
