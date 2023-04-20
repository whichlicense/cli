/*
 * Copyright (c) 2023 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository https://github.com/whichlicense/cli.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.whichlicense.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.whichlicense.cli.metadata.npm.NpmPackageLock;
import com.whichlicense.cli.simplesbom.SimpleDependency;
import com.whichlicense.cli.simplesbom.SimpleSBOM;
import com.whichlicense.integration.jackson.identity.WhichLicenseIdentityModule;
import com.whichlicense.internal.spectra.LocalIdentitySpectra;
import com.whichlicense.logging.Logging;
import com.whichlicense.metadata.seeker.MetadataMatch;
import com.whichlicense.metadata.seeker.MetadataSeeker;
import picocli.AutoComplete.GenerateCompletion;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.fasterxml.jackson.databind.cfg.EnumFeature.WRITE_ENUMS_TO_LOWERCASE;
import static com.whichlicense.cli.simplesbom.DependencyScope.COMPILE;
import static com.whichlicense.cli.simplesbom.DependencyScope.TEST;
import static com.whichlicense.metadata.seeker.MetadataSourceType.FILE;
import static java.lang.System.exit;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.time.Instant.now;
import static java.time.ZoneOffset.UTC;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.logging.Logger.getLogger;
import static java.util.stream.Collectors.toSet;

/**
 * The WhichLicense platform CLI base command.
 *
 * @author David Greven
 * @version 0
 * @since 0.0.0
 */
@Command(name = "whichlicense", description = "WhichLicense platform CLI", version = {
        "WhichLicense CLI 0.1.3 (Preview)", "JVM: ${java.version} (${java.vendor} ${java.vm.name} ${java.vm.version})",
        "OS: ${os.name} ${os.version} ${os.arch}"}, usageHelpAutoWidth = true,
        showEndOfOptionsDelimiterInUsageHelp = true, mixinStandardHelpOptions = true, showAtFileInUsageHelp = true,
        requiredOptionMarker = '*', subcommands = GenerateCompletion.class)
public class Entrypoint implements Runnable {
    @Option(names = "--no-logging", negatable = true, description = "Enable or disable the application logging. (default: enabled)")
    private boolean logging = true;
    @Option(names = "--log-dir", paramLabel = "LOG_DIR", description = "Change the log output directory. (default: cwd)")
    private Path logDir = Paths.get(".");
    @SuppressWarnings("unused")
    @Parameters(index = "0", defaultValue = ".", paramLabel = "PATH", description = "The path to the directory or zip file to search in. (default: cwd)")
    private String inputPath = ".";
    @SuppressWarnings("unused")
    @Option(names = {"-o", "--output"}, paramLabel = "OUTPUT_DST", description = "Change the output destination. (default: stdout)")
    private Path outputPath;

    /**
     * The primary CLI entry point.
     *
     * @param args The commands, subcommands, options, flags and arguments supplied to the CLI.
     * @since 0.0.0
     */
    public static void main(String[] args) {
        exit(new CommandLine(new Entrypoint()).execute(args));
    }

    static Function<Path, Optional<MetadataMatch>> createMatcher(String glob, MetadataSeeker seeker, Path root) {
        var compiled = root.getFileSystem().getPathMatcher("glob:" + glob);
        return path -> compiled.matches(path) ? of(new MetadataMatch.FileMatch(root.relativize(path), seeker.getClass())) : empty();
    }

    static Stream<Function<Path, Optional<MetadataMatch>>> createMatchers(MetadataSeeker seeker, Path root) {
        return seeker.globs().stream().map(glob -> createMatcher(glob, seeker, root));
    }

    @Override
    public void run() {
        Logging.configure(logging, logDir);

        Logger SOURCE_LOGGER = getLogger("whichlicense.source");
        Logger SEEKER_LOGGER = getLogger("whichlicense.seeker");
        Logger MATCHES_LOGGER = getLogger("whichlicense.matches");
        Logger DISCOVERY_LOGGER = getLogger("whichlicense.discovery");
        Logger PARSING_LOGGER = getLogger("whichlicense.parsing");
        Logger DEPENDENCIES_LOGGER = getLogger("whichlicense.dependencies");

        URL url = null;
        Path path;

        try {
            url = new URL(inputPath);
            SOURCE_LOGGER.finest("Input source is remote: " + url);
        } catch (Exception ignored) {
            // Not a URL, continue
        }

        if (url != null && inputPath.endsWith(".zip")) {
            try {
                var tempDir = createTempDirectory("whichlicense");
                var tempZipFile = tempDir.resolve(url.getFile().substring(url.getFile().lastIndexOf("/") + 1));
                copy(url.openStream(), tempZipFile, REPLACE_EXISTING);
                SOURCE_LOGGER.finest("Input source temporarily downloaded to: " + tempZipFile);
                path = tempZipFile;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            path = Paths.get(inputPath);
        }

        // If the path is a zip file, read it as a file system
        if (inputPath.endsWith(".zip")) {
            try {
                SOURCE_LOGGER.finest("Reading from archive input source : " + path);
                var zipFS = FileSystems.newFileSystem(path, (ClassLoader) null);
                path = zipFS.getPath("/");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        var seekers = ServiceLoader.load(MetadataSeeker.class);
        seekers.iterator().forEachRemaining(seeker -> SEEKER_LOGGER.finest("Registered metadata seeker: " + seeker));

        Path finalPath1 = path;
        var matchers = StreamSupport.stream(seekers.spliterator(), false)
                .filter(seeker -> Objects.equals(seeker.type(), FILE))
                .flatMap(seeker -> createMatchers(seeker, finalPath1))
                .toList();

        var discoveredFiles = new ArrayList<Path>();

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    for (var matcher : matchers) {
                        var match = matcher.apply(file);
                        if (match.isPresent()) {
                            MATCHES_LOGGER.info("Found metadata source: " + match.get());
                            discoveredFiles.add(file);
                            break;
                        }
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new WhichLicenseIdentityModule());
        mapper.configure(WRITE_ENUMS_TO_LOWERCASE, true);
        var lockfileGlob = path.getFileSystem().getPathMatcher("glob:**/package-lock.json");

        for (var file : discoveredFiles) {
            DISCOVERY_LOGGER.finest("Check file: {}" + file);
            if (lockfileGlob.matches(file)) {
                PARSING_LOGGER.finest("Parsing package-lock.json");
                try (var inputStream = Files.newInputStream(file)) {
                    var packageLock = mapper.readValue(inputStream, NpmPackageLock.class);
                    DEPENDENCIES_LOGGER.info("Found library: " + packageLock.name() + "::" + packageLock.version());
                    var packageMetadata = packageLock.packages().get("");

                    var directDependencyNames = Stream.of(packageMetadata.dependencies(), packageMetadata.devDependencies()).flatMap(d -> d.keySet().stream()).collect(toSet());

                    final var finalPath = path;
                    var partitionedDependencies = packageLock.packages().entrySet().stream().filter(entry -> !entry.getKey().isBlank()).map(entry -> {
                        var name = entry.getKey().substring(entry.getKey().lastIndexOf("/") + 1);
                        var metadata = entry.getValue();
                        DEPENDENCIES_LOGGER.finest("Depends on: " + name + "::" + metadata.version());
                        return new SimpleDependency(name, metadata.version(), LocalIdentitySpectra.generate(), metadata.license(), null, "library", metadata.dev() ? TEST : COMPILE, "npm", finalPath.relativize(file).toString(), metadata.dependencies() == null ? Collections.emptyMap() : metadata.dependencies()); //also add the dev dependencies here in the future
                    }).collect(Collectors.partitioningBy(d -> directDependencyNames.contains(d.name())));

                    var simpleSBOM = new SimpleSBOM(packageLock.name(), packageLock.version(), LocalIdentitySpectra.generate(), packageMetadata.license(), null, "library", List.of("npm"), finalPath.relativize(file).toString(), now().atZone(UTC), partitionedDependencies.get(true), partitionedDependencies.get(false));

                    if (outputPath == null) {
                        mapper.writeValue(System.out, simpleSBOM);
                    } else {
                        mapper.writeValue(outputPath.toFile(), simpleSBOM);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
