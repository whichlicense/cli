/*
 * Copyright (c) 2023 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository https://github.com/whichlicense/cli.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import com.whichlicense.metadata.seeker.MetadataSeeker;

module whichlicense.cli {
    requires java.logging;
    requires info.picocli;
    requires whichlicense.logging;
    requires whichlicense.sourcing;
    requires whichlicense.sourcing.github;
    requires whichlicense.seeker.npm;
    requires whichlicense.seeker.yarn;
    requires whichlicense.seeker.license;
    requires whichlicense.seeker.notice;
    requires whichlicense.seeker.readme;
    requires whichlicense.seeker.gitignore;
    requires whichlicense.seeker.gitattributes;
    requires whichlicense.seeker.gitmodules;
    requires whichlicense.seeker.gitrepo;
    requires whichlicense.seeker.rat;
    requires whichlicense.integration.jackson.identity;
    requires whichlicense.identification.license;
    requires whichlicense.identification.license.wasm;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.datatype.jsr310;
    opens com.whichlicense.cli to info.picocli;
    opens com.whichlicense.cli.metadata.npm to com.fasterxml.jackson.databind;
    opens com.whichlicense.cli.simplesbom to com.fasterxml.jackson.databind;
    uses MetadataSeeker;
}
