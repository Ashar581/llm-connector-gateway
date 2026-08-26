package com.an.llm.connector.gateway.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class BootstrapLauncher {

    private BootstrapLauncher() {}

    public static void run() {

        Path projectRoot = findProjectRoot();

        Path script = projectRoot
                .resolve("bootstrap")
                .resolve("bootstrap.sh");

        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException(
                    "Bootstrap script not found: " + script
            );
        }

        System.out.println();
        System.out.println("============================================================");
        System.out.println("        Preparing LLM Connector Gateway");
        System.out.println("============================================================");
        System.out.println();

        try {

            Process process = new ProcessBuilder(
                    "bash",
                    script.toAbsolutePath().toString()
            )
                    .directory(projectRoot.toFile())
                    .inheritIO()
                    .start();

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IllegalStateException(
                        "Bootstrap failed with exit code: " + exitCode
                );
            }

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Unable to execute bootstrap.sh. " +
                            "Make sure Bash is installed.",
                    e
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Bootstrap process was interrupted.",
                    e
            );
        }

        System.out.println();
        System.out.println("Environment ready.");
        System.out.println();
    }


    private static Path findProjectRoot() {

        /*
         * First try the current working directory.
         *
         * This covers:
         *
         * ./mvnw spring-boot:run
         * IntelliJ
         * VS Code
         * ./gradlew bootRun
         */
        Path current = Paths
                .get(System.getProperty("user.dir"))
                .toAbsolutePath()
                .normalize();

        Path result = searchUpwards(current);

        if (result != null) {
            return result;
        }


        /*
         * Then try the location from which the application/JAR
         * was loaded.
         *
         * This helps when running:
         *
         * java -jar ...
         */
        try {

            Path applicationLocation = Paths.get(
                            BootstrapLauncher.class
                                    .getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation()
                                    .toURI()
                    )
                    .toAbsolutePath()
                    .normalize();

            if (Files.isRegularFile(applicationLocation)) {
                applicationLocation =
                        applicationLocation.getParent();
            }

            result = searchUpwards(applicationLocation);

            if (result != null) {
                return result;
            }

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to determine application location.",
                    e
            );
        }


        throw new IllegalStateException(
                "Could not find the project root containing " +
                        "bootstrap/bootstrap.sh."
        );
    }


    private static Path searchUpwards(Path start) {

        Path current = start;

        while (current != null) {

            Path bootstrapScript = current
                    .resolve("bootstrap")
                    .resolve("bootstrap.sh");

            if (Files.isRegularFile(bootstrapScript)) {
                return current;
            }

            current = current.getParent();
        }

        return null;
    }
}
