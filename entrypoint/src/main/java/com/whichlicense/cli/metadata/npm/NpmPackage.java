/*
 * Copyright (c) 2023 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository https://github.com/whichlicense/cli.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.whichlicense.cli.metadata.npm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

//Needs additional attributes for: dev dependencies, peerDependencies, peerDependenciesMeta resolved, integrity, funding, bin, engines
//Data type for engines can be either an object or array
@JsonIgnoreProperties(ignoreUnknown = true)
public record NpmPackage(String version, String license, boolean dev, Map<String, String> dependencies, Map<String, String> devDependencies) {
}
