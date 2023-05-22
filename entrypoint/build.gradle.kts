/*
 * Copyright (c) 2023 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository https://github.com/whichlicense/cli.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("java")
    id("org.graalvm.buildtools.native") version "0.9.22"
    id("maven-publish")
    id("signing")
}

group = "com.whichlicense.cli"
version = "0.3.4"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
    withJavadocJar()
    withSourcesJar()
}

repositories {
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
    mavenCentral()
}

configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor(10, "minutes")
}

dependencies {
    implementation("info.picocli:picocli:4.7.3")
    annotationProcessor("info.picocli:picocli-codegen:4.7.3")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.0")
    implementation("com.whichlicense:logging:0.2.0-SNAPSHOT")
    implementation("com.whichlicense:sourcing:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.sourcing:github:0.2.0-SNAPSHOT")
    implementation("com.whichlicense:identity:0.2.0-SNAPSHOT")
    implementation("com.whichlicense:seeker:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.seeker:npm:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.seeker:yarn:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.seeker:license:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.seeker:notice:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.seeker:readme:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.seeker:rat:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.seeker:gitignore:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.seeker:gitattributes:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.seeker:gitmodules:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.seeker:gitrepo:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.jackson:identity:0.2.0-SNAPSHOT")
    implementation("com.whichlicense.identification:license:0.3.4-SNAPSHOT")
    implementation("com.whichlicense.identification.license.backend:wasm:0.3.5-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.3")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

graalvmNative {
    metadataRepository {
        enabled.set(true)
    }
    binaries {
        named("main") {
            imageName.set("whichlicense")
            mainClass.set("com.whichlicense.cli.Entrypoint")
            useFatJar.set(false) // This option (set to true) breaks the ServiceLoader because of duplicate resource names
            buildArgs.set(arrayListOf(
                "--enable-url-protocols=http",
                "--enable-url-protocols=https",
                "--no-fallback",
                //"-H:Log=registerResource:5",
                //"-H:+UseServiceLoaderFeature",
                //"-H:+TraceServiceLoaderFeature",
                "-H:+InstallExitHandlers",
                "-H:+ReportExceptionStackTraces",
                "--enable-preview",
                "--language:js",
                "--language:wasm"
            ))
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(19))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("entrypoint") {
            artifactId = "entrypoint"
            from(components["java"])
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name.set("WhichLicense cli/entrypoint")
                description.set("The WhichLicense platform CLI entry point.")
                url.set("https://github.com/whichlicense/cli/entrypoint")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("grevend")
                        name.set("David Greven")
                        email.set("david.greven@whichlicense.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/whichlicense/cli.git")
                    developerConnection.set("scm:git:git@github.com:whichlicense/cli.git")
                    url.set("https://github.com/whichlicense/cli")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/whichlicense/cli")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

signing {
    if (project.hasProperty("CI")) {
        val signingKey = System.getenv("PKG_SIGNING_KEY")
        val signingPassword = System.getenv("PKG_SIGNING_PW")
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["entrypoint"])
    }
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}
