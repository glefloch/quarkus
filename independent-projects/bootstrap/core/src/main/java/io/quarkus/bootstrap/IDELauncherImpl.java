package io.quarkus.bootstrap;

import io.quarkus.bootstrap.app.AdditionalDependency;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.resolver.model.QuarkusModel;
import io.quarkus.bootstrap.resolver.model.Workspace;
import io.quarkus.bootstrap.util.QuarkusModelHelper;
import io.quarkus.bootstrap.utils.BuildToolHelper;
import java.nio.file.Path;
import java.util.Map;

/**
 * IDE entry point.
 * 
 * This is launched from the core/launcher module. To avoid any shading issues core/launcher unpacks all its dependencies
 * into the jar file, then uses a custom class loader load them.
 * 
 */
@SuppressWarnings("unused")
public class IDELauncherImpl {

    public static void launch(Path projectRoot, Map<String, Object> context) {

        try {
            //todo : proper support for everything
            final QuarkusBootstrap.Builder builder = QuarkusBootstrap.builder()
                    .setApplicationRoot(projectRoot)
                    .setBaseClassLoader(IDELauncherImpl.class.getClassLoader())
                    .setProjectRoot(projectRoot)
                    .setIsolateDeployment(true)
                    .setMode(QuarkusBootstrap.Mode.DEV);

            if (!BuildToolHelper.isMavenProject(projectRoot)) {
                final QuarkusModel quarkusModel = BuildToolHelper.enableGradleAppModelForDevMode(projectRoot);
                // Gradle uses a different output directory for classes, we override the one used by the IDE
                projectRoot = QuarkusModelHelper.getClassPath(quarkusModel.getWorkspace());
                for (Workspace additionalWorkspace : quarkusModel.getAdditionalWorkspace()) {
                    builder.addAdditionalApplicationArchive(new AdditionalDependency(
                            QuarkusModelHelper.toPathsCollection(additionalWorkspace.getSourceSet().getSourceDirectories()),
                            true, false));
                    builder.addAdditionalApplicationArchive(new AdditionalDependency(
                            additionalWorkspace.getSourceSet().getResourceDirectory().toPath(), true, false));
                }

            }

            CuratedApplication app = builder
                    .build().bootstrap();

            app.runInAugmentClassLoader("io.quarkus.deployment.dev.IDEDevModeMain", context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
