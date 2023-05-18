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
import com.whichlicense.metadata.identification.license.LicenseIdentifier;
import com.whichlicense.metadata.identification.license.LicenseMatch;
import com.whichlicense.metadata.identification.license.internal.HashingAlgorithm;
import com.whichlicense.metadata.seeker.MetadataMatch;
import com.whichlicense.metadata.seeker.MetadataSeeker;
import com.whichlicense.metadata.sourcing.MetadataSourceResolverProvider;
import picocli.AutoComplete.GenerateCompletion;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.fasterxml.jackson.databind.cfg.EnumFeature.WRITE_ENUMS_TO_LOWERCASE;
import static com.whichlicense.cli.HashingAlgorithms.GAOYA;
import static com.whichlicense.cli.simplesbom.DependencyScope.COMPILE;
import static com.whichlicense.cli.simplesbom.DependencyScope.TEST;
import static com.whichlicense.metadata.seeker.MetadataSourceType.FILE;
import static java.lang.System.exit;
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
        "WhichLicense CLI 0.2.0 (Preview)", "JVM: ${java.version} (${java.vendor} ${java.vm.name} ${java.vm.version})",
        "OS: ${os.name} ${os.version} ${os.arch}"}, usageHelpAutoWidth = true,
        showEndOfOptionsDelimiterInUsageHelp = true, mixinStandardHelpOptions = true, showAtFileInUsageHelp = true,
        requiredOptionMarker = '*', subcommands = GenerateCompletion.class)
public class Entrypoint implements Runnable {
    @Option(names = "--hashing-algorithm", paramLabel = "HASH_ALGO", description = "Change the hashing algorithm. (default: gaoya)")
    private HashingAlgorithms hashingAlgorithm = GAOYA;
    @Option(names = "--no-logging", negatable = true, description = "Enable or disable the application logging. (default: enabled)")
    private boolean logging = true;
    @Option(names = "--log-dir", paramLabel = "LOG_DIR", description = "Change the log output directory. (default: cwd)")
    private Path logDir = Paths.get(".");
    @SuppressWarnings("unused")
    @Parameters(index = "0", defaultValue = ".", paramLabel = "INPUT_SRC", description = "The path to the directory or zip file to search in. (default: cwd)")
    private String inputPath = ".";
    @SuppressWarnings("unused")
    @Option(names = {"-o", "--output"}, paramLabel = "OUTPUT_DST", description = "Change the output destination. (default: stdout)")
    private Path outputPath;

    /*@Option(names = {"--ignore-paths"}, paramLabel = "IGNORED_PATHS", description = "The ignored paths during the discovery process.")
    private Path[] ignorePaths;
    @Option(names = {"--ignore-path-patterns"}, paramLabel = "IGNORED_PATH_PATTERNS", description = "The ignored path patterns during the discovery process.")
    private String[] ignorePathPatterns;
    @Option(names = {"--include-paths"}, paramLabel = "INCLUDED_PATHS", description = "The included paths during the discovery process.")
    private Path[] includePaths;
    @Option(names = {"--include-path-patterns"}, paramLabel = "INCLUDED_PATH_PATTERNS", description = "The included path patterns during the discovery process.")
    private String[] includePathPatterns;
    @Option(names = {"--path-pattern-processor"}, paramLabel = "PATH_PATTERN_PROCESSOR", description = "Change the path pattern processor. (default: glob)")
    private PathPatternProcessor pathPatternProcessor = GLOB;*/

    /**
     * The primary CLI entry point.
     *
     * @param args The commands, subcommands, options, flags and arguments supplied to the CLI.
     * @since 0.0.0
     */
    public static void main(String[] args) {
        exit(new CommandLine(new Entrypoint()).setCaseInsensitiveEnumValuesAllowed(true).execute(args));
    }

    static Function<Path, Optional<MetadataMatch>> createMatcher(String glob, MetadataSeeker seeker, Path root) {
        var compiled = root.getFileSystem().getPathMatcher("glob:" + glob);
        return path -> compiled.matches(path) ? of(new MetadataMatch.FileMatch(root.relativize(path), seeker.getClass())) : empty();
    }

    static Stream<Function<Path, Optional<MetadataMatch>>> createMatchers(MetadataSeeker seeker, Path root) {
        return seeker.globs().stream().map(glob -> createMatcher(glob, seeker, root));
    }

    //TODO return error codes by using core-libs@problem
    //TODO group all discovered resource per directory
    @Override
    public void run() {
        Logging.configure(logging, logDir);

        Logger SEEKER_LOGGER = getLogger("whichlicense.seeker");
        Logger MATCHES_LOGGER = getLogger("whichlicense.matches");
        Logger DISCOVERY_LOGGER = getLogger("whichlicense.discovery");
        Logger EXTRACTING_LOGGER = getLogger("whichlicense.extracting");
        Logger DEPENDENCIES_LOGGER = getLogger("whichlicense.dependencies");
        Logger IDENTIFICATION_LOGGER = getLogger("whichlicense.identification");

        final var source = MetadataSourceResolverProvider.loadChain().resolve(inputPath).get().path();

        var seekers = ServiceLoader.load(MetadataSeeker.class);
        seekers.iterator().forEachRemaining(seeker -> SEEKER_LOGGER.finest("Registered "
                + seeker.toString().replace("[]", "")));

        var matchers = StreamSupport.stream(seekers.spliterator(), false)
                .filter(seeker -> Objects.equals(seeker.type(), FILE))
                .flatMap(seeker -> createMatchers(seeker, source))
                .toList();

        var discoveredFiles = new ArrayList<Path>();

        //TODO allow to limit the depth
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    DISCOVERY_LOGGER.finest("Check " + source.relativize(file));
                    for (var matcher : matchers) {
                        var match = matcher.apply(file);
                        if (match.isPresent()) {
                            MATCHES_LOGGER.info("Discovered " + match.get());
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

        var licenseFileGlob = source.getFileSystem().getPathMatcher("glob:**/LICENSE");
        var lockfileGlob = source.getFileSystem().getPathMatcher("glob:**/package-lock.json");
        Optional<LicenseMatch> discoveredLicense = Optional.empty();

        final var hashingAlgo = hashingAlgorithm == GAOYA ? HashingAlgorithm.GAOYA : HashingAlgorithm.FUZZY;

        for (var file : discoveredFiles) {
            if (licenseFileGlob.matches(file)) {
                IDENTIFICATION_LOGGER.finest("Identify LICENSE");
                try {
                    discoveredLicense = LicenseIdentifier.identifyLicense(hashingAlgo, Files.readString(file));
                    IDENTIFICATION_LOGGER.finest(discoveredLicense.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        for (var file : discoveredFiles) {
            if (lockfileGlob.matches(file)) {
                EXTRACTING_LOGGER.finest("Extracting package-lock.json");
                try (var inputStream = Files.newInputStream(file)) {
                    var packageLock = mapper.readValue(inputStream, NpmPackageLock.class);
                    DEPENDENCIES_LOGGER.info("Identified library " + packageLock.name() + "#" + packageLock.version());
                    var packageMetadata = packageLock.packages().get("");

                    var directDependencyNames = Stream.of(packageMetadata.dependencies(), packageMetadata.devDependencies()).flatMap(d -> d.keySet().stream()).collect(toSet());

                    var partitionedDependencies = packageLock.packages().entrySet().stream().filter(entry -> !entry.getKey().isBlank()).map(entry -> {
                        var name = entry.getKey().substring(entry.getKey().lastIndexOf("/") + 1);
                        var metadata = entry.getValue();
                        DEPENDENCIES_LOGGER.finest("Identified dependency " + name + "#" + metadata.version());
                        return new SimpleDependency(name, metadata.version(), LocalIdentitySpectra.generate(), metadata.license(), null, "library", metadata.dev() ? TEST : COMPILE, "npm", source.relativize(file).toString(), metadata.dependencies() == null ? Collections.emptyMap() : metadata.dependencies()); //also add the dev dependencies here in the future
                    }).collect(Collectors.partitioningBy(d -> directDependencyNames.contains(d.name())));

                    var simpleSBOM = new SimpleSBOM(packageLock.name(), packageLock.version(), LocalIdentitySpectra.generate(),
                            packageMetadata.license().toLowerCase(), null, discoveredLicense.map(LicenseMatch::license)
                            .map(l -> l.replaceFirst(".LICENSE", "").toLowerCase()).orElse(null),
                            null, "library", List.of("npm"), source.relativize(file).toString(),
                            now().atZone(UTC), partitionedDependencies.get(true), partitionedDependencies.get(false));

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
