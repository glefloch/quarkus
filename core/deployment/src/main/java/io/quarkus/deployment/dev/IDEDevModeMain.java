package io.quarkus.deployment.dev;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.jboss.logging.Logger;

import io.quarkus.bootstrap.BootstrapGradleException;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.resolver.AppModelResolverException;
import io.quarkus.bootstrap.resolver.QuarkusGradleModelFactory;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;
import io.quarkus.bootstrap.resolver.model.QuarkusModel;
import io.quarkus.bootstrap.resolver.model.Workspace;
import io.quarkus.bootstrap.util.QuarkusModelHelper;
import io.quarkus.bootstrap.utils.BuildToolHelper;

@SuppressWarnings("unused")
public class IDEDevModeMain implements BiConsumer<CuratedApplication, Map<String, Object>> {

    private static final Logger log = Logger.getLogger(IDEDevModeMain.class.getName());

    @Override
    public void accept(CuratedApplication curatedApplication, Map<String, Object> stringObjectMap) {
        Path appClasses = (Path) stringObjectMap.get("app-classes");
        DevModeContext devModeContext = new DevModeContext();
        devModeContext.setArgs((String[]) stringObjectMap.get("args"));
        try {
            if (BuildToolHelper.isMavenProject(appClasses)) {
                LocalProject project = LocalProject.loadWorkspace(appClasses);
                DevModeContext.ModuleInfo root = toModule(project);
                devModeContext.setApplicationRoot(root);
                for (Map.Entry<AppArtifactKey, LocalProject> module : project.getWorkspace().getProjects().entrySet()) {
                    if (module.getKey().equals(project.getKey())) {
                        continue;
                    }
                    devModeContext.getAdditionalModules().add(toModule(module.getValue()));
                }
            } else {
                // TODO find a way to reuse the previously model instead of building a new one.
                QuarkusModel quarkusModel = QuarkusGradleModelFactory.createForTasks(
                        BuildToolHelper.getBuildFile(appClasses, BuildToolHelper.BuildTool.GRADLE).toFile(),
                        QuarkusModelHelper.DEVMODE_REQUIRED_TASKS);
                DevModeContext.ModuleInfo root = toModule(quarkusModel.getWorkspace());
                devModeContext.setApplicationRoot(root);
                for (Workspace additionalWorkspace : quarkusModel.getAdditionalWorkspace()) {
                    devModeContext.getAdditionalModules().add(toModule(additionalWorkspace));
                }
            }

        } catch (AppModelResolverException e) {
            log.error("Failed to load workspace, hot reload will not be available", e);
        }

        new IsolatedDevModeMain().accept(curatedApplication,
                Collections.singletonMap(DevModeContext.class.getName(), devModeContext));
    }

    private DevModeContext.ModuleInfo toModule(Workspace model) throws BootstrapGradleException {
        AppArtifactKey key = new AppArtifactKey(model.getArtifactCoords().getGroupId(),
                model.getArtifactCoords().getArtifactId(), model.getArtifactCoords().getClassifier());

        Set<String> sourceDirectories = new HashSet<>();
        Set<String> sourceParents = new HashSet<>();
        for (File srcDir : model.getSourceSourceSet().getSourceDirectories()) {
            sourceDirectories.add(srcDir.getPath());
            sourceParents.add(srcDir.getParent());
        }

        return new DevModeContext.ModuleInfo(key,
                model.getArtifactCoords().getArtifactId(),
                model.getProjectRoot().getPath(),
                sourceDirectories,
                QuarkusModelHelper.getClassPath(model).toAbsolutePath().toString(),
                model.getSourceSourceSet().getResourceDirectory().toString(),
                model.getSourceSet().getResourceDirectory().getPath(),
                sourceParents,
                model.getBuildDir().toPath().resolve("generated-sources").toAbsolutePath().toString(),
                model.getBuildDir().toString());
    }

    private DevModeContext.ModuleInfo toModule(LocalProject project) {
        return new DevModeContext.ModuleInfo(project.getKey(), project.getArtifactId(),
                project.getDir().toAbsolutePath().toString(),
                Collections.singleton(project.getSourcesSourcesDir().toAbsolutePath().toString()),
                project.getClassesDir().toAbsolutePath().toString(),
                project.getResourcesSourcesDir().toAbsolutePath().toString(),
                project.getSourcesDir().toString(),
                project.getCodeGenOutputDir().toString(),
                project.getOutputDir().toString());
    }
}
