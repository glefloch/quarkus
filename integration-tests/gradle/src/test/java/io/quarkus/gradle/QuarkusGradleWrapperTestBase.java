package io.quarkus.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class QuarkusGradleWrapperTestBase extends QuarkusGradleTestBase {

    private static final String GRADLE_WRAPPER_WINDOWS = "gradlew.bat";
    private static final String GRADLE_WRAPPER_UNIX = "./gradlew";
    private static final String GRADLE_NO_DAEMON = "--no-daemon";

    public BuildResult runGradleWrapper(File projectDir, String... args) throws IOException, InterruptedException {
        List<String> command = new LinkedList<>();
        command.add(getGradleWrapperCommand());
        command.add(GRADLE_NO_DAEMON);
        command.add("--stacktrace");
        command.addAll(Arrays.asList(args));
        ProcessBuilder pBuilder = new ProcessBuilder()
                .directory(projectDir)
                .command(command);
        final Map<String, String> environment = pBuilder.environment();
        environment.put("GRADLE_HOME", projectDir.getPath());
        Process p = pBuilder.start();

        p.waitFor(5, TimeUnit.MINUTES);
        return BuildResult.of(p.getInputStream());
    }

    private String getGradleWrapperCommand() {
        return Paths.get(getGradleWrapperName()).toAbsolutePath().toString();
    }

    private String getGradleWrapperName() {
        if (System.getProperty("os.name").toLowerCase(Locale.ENGLISH).indexOf("windows") >= 0) {
            return GRADLE_WRAPPER_WINDOWS;
        }
        return GRADLE_WRAPPER_UNIX;
    }
}
