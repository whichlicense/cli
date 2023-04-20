/*
 * Copyright (c) 2023 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository https://github.com/whichlicense/cli.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.whichlicense.cli.simplesbom;

import java.time.ZonedDateTime;
import java.util.List;

public record SimpleSBOM(String name, String version, long identity, String license, String licenseClass, String type, List<String> ecosystems, String source, ZonedDateTime generated, List<SimpleDependency> directDependencies, List<SimpleDependency> transitiveDependencies) {
}
