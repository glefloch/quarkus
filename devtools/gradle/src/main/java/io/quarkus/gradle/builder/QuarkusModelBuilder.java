package io.quarkus.gradle.builder;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.resolver.model.ArtifactCoords;
import io.quarkus.bootstrap.resolver.model.Dependency;
import io.quarkus.bootstrap.resolver.model.ModelParameter;
import io.quarkus.bootstrap.resolver.model.QuarkusModel;
import io.quarkus.bootstrap.resolver.model.Workspace;
import io.quarkus.bootstrap.resolver.model.impl.ArtifactCoordsImpl;
import io.quarkus.bootstrap.resolver.model.impl.DependencyImpl;
import io.quarkus.bootstrap.resolver.model.impl.ModelParameterImpl;
import io.quarkus.bootstrap.resolver.model.impl.QuarkusModelImpl;
import io.quarkus.bootstrap.resolver.model.impl.SourceSetImpl;
import io.quarkus.bootstrap.resolver.model.impl.WorkspaceImpl;
import io.quarkus.bootstrap.util.QuarkusModelHelper;
import io.quarkus.gradle.tasks.QuarkusGradleUtils;
import io.quarkus.runtime.LaunchMode;

public class QuarkusModelBuilder implements ParameterizedToolingModelBuilder {

    private static final List<String> scannedConfigurations = new LinkedList();

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(QuarkusModel.class.getName());
    }

    @Override
    public Class getParameterType() {
        return ModelParameter.class;
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        final ModelParameterImpl modelParameter = new ModelParameterImpl();
        modelParameter.setMode(LaunchMode.DEVELOPMENT.toString());
        return buildAll(modelName, modelParameter, project);
    }

    @Override
    public Object buildAll(String modelName, Object parameter, Project project) {
        LaunchMode mode = LaunchMode.valueOf(((ModelParameter) parameter).getMode());

        if (LaunchMode.TEST.equals(mode)) {
            scannedConfigurations.add(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        } else {
            scannedConfigurations.add(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        }

        if (LaunchMode.DEVELOPMENT.equals(mode)) {
            scannedConfigurations.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME);
        }

        final Collection<org.gradle.api.artifacts.Dependency> directExtensionDependencies = getDirectExtension(project);

        final Set<Dependency> appDependencies = new HashSet<>();
        for (String configurationName : scannedConfigurations) {
            final ResolvedConfiguration configuration = project.getConfigurations().getByName(configurationName)
                    .getResolvedConfiguration();
            appDependencies.addAll(collectDependencies(configuration, configurationName, mode, project));
            directExtensionDependencies
                    .addAll(getDirectExtensionDependencies(configuration.getFirstLevelModuleDependencies(), appDependencies));
        }

        final Set<Dependency> extensionDependencies = collectExtensionDependencies(project, directExtensionDependencies);

        ArtifactCoords appArtifactCoords = new ArtifactCoordsImpl(project.getGroup().toString(), project.getName(),
                project.getVersion().toString());

        return new QuarkusModelImpl(getWorkspace(project),
                getAdditionalWorkspaces(project.getRootProject(), project.getName()),
                appDependencies,
                extensionDependencies);
    }

    private Workspace getWorkspace(Project project) {
        ArtifactCoords appArtifactCoords = new ArtifactCoordsImpl(project.getGroup().toString(), project.getName(),
                project.getVersion().toString());
        final SourceSet mainSourceSet = QuarkusGradleUtils.getSourceSet(project, SourceSet.MAIN_SOURCE_SET_NAME);

        return new WorkspaceImpl(appArtifactCoords, project.getProjectDir().getAbsoluteFile(),
                project.getBuildDir().getAbsoluteFile(), getSourceSourceSet(mainSourceSet), convert(mainSourceSet));
    }

    private Set<Workspace> getAdditionalWorkspaces(Project project, String mainProjectName) {
        Set<Workspace> workspaces = new HashSet<>();
        for (Project subproject : project.getSubprojects()) {

            if (subproject.getName().equals(mainProjectName)) {
                continue;
            }
            final Convention convention = subproject.getConvention();
            JavaPluginConvention javaConvention = convention.findPlugin(JavaPluginConvention.class);
            if (javaConvention == null) {
                continue;
            }

            workspaces.add(getWorkspace(subproject));
        }
        return workspaces;
    }

    private Set<org.gradle.api.artifacts.Dependency> getDirectExtension(Project project) {
        final Set<org.gradle.api.artifacts.Dependency> directExtension = new HashSet<>();
        // collect enforced platforms
        final Configuration impl = project.getConfigurations()
                .getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);
        for (org.gradle.api.artifacts.Dependency d : impl.getAllDependencies()) {
            if (!(d instanceof ModuleDependency)) {
                continue;
            }
            final ModuleDependency module = (ModuleDependency) d;
            final Category category = module.getAttributes().getAttribute(Category.CATEGORY_ATTRIBUTE);
            if (category != null && Category.ENFORCED_PLATFORM.equals(category.getName())) {
                directExtension.add(d);
            }
        }
        return directExtension;
    }

    private Set<org.gradle.api.artifacts.Dependency> getDirectExtensionDependencies(Set<ResolvedDependency> dependencies,
            Collection<Dependency> appDependencies) {
        Set<org.gradle.api.artifacts.Dependency> extensions = new HashSet<>();
        for (ResolvedDependency d : dependencies) {
            appDependencies.stream()
                    .filter(dep -> dep.getGroupId().equals(d.getModuleGroup()) && dep.getName().equals(d.getModuleName()))
                    .map(this::getDeploymentArtifact)
                    .filter(Objects::nonNull)
                    .forEach(extensions::add);
            final Set<ResolvedDependency> resolvedChildren = d.getChildren();
            if (!resolvedChildren.isEmpty()) {
                extensions.addAll(getDirectExtensionDependencies(resolvedChildren, appDependencies));
            }
        }
        return extensions;
    }

    private org.gradle.api.artifacts.Dependency getDeploymentArtifact(Dependency dependency) {
        for (File file : dependency.getPaths()) {
            if (!file.exists()) {
                continue;
            }
            Properties depsProperties;
            if (file.isDirectory()) {
                Path quarkusDescr = file.toPath()
                        .resolve(BootstrapConstants.META_INF)
                        .resolve(BootstrapConstants.DESCRIPTOR_FILE_NAME);
                if (!Files.exists(quarkusDescr)) {
                    continue;
                }
                depsProperties = QuarkusModelHelper.resolveDescriptor(quarkusDescr);
            } else {
                try (FileSystem artifactFs = FileSystems.newFileSystem(file.toPath(), getClass().getClassLoader())) {
                    Path quarkusDescr = artifactFs.getPath(BootstrapConstants.META_INF)
                            .resolve(BootstrapConstants.DESCRIPTOR_FILE_NAME);
                    if (!Files.exists(quarkusDescr)) {
                        continue;
                    }
                    depsProperties = QuarkusModelHelper.resolveDescriptor(quarkusDescr);
                } catch (IOException e) {
                    throw new GradleException("Failed to process " + file, e);
                }
            }
            String value = depsProperties.getProperty(BootstrapConstants.PROP_DEPLOYMENT_ARTIFACT);
            String[] split = value.split(":");
            return new DefaultExternalModuleDependency(split[0], split[1], split[2], null);
        }
        return null;
    }

    private Set<Dependency> collectExtensionDependencies(Project project,
            Collection<org.gradle.api.artifacts.Dependency> extensions) {
        final Set<Dependency> platformDependencies = new HashSet<>();

        final Configuration deploymentConfig = project.getConfigurations()
                .detachedConfiguration(extensions.toArray(new org.gradle.api.artifacts.Dependency[0]));
        final ResolvedConfiguration rc = deploymentConfig.getResolvedConfiguration();
        for (ResolvedArtifact a : rc.getResolvedArtifacts()) {
            if (!isDependency(a)) {
                continue;
            }

            final Dependency dependency = toDependency(a, deploymentConfig.getName());
            platformDependencies.add(dependency);
        }

        return platformDependencies;
    }

    private Collection<Dependency> collectDependencies(ResolvedConfiguration configuration, String configurationName,
            LaunchMode mode, Project project) {
        Collection<Dependency> modelDependencies = new LinkedList<>();
        for (ResolvedArtifact a : configuration.getResolvedArtifacts()) {
            if (!isDependency(a)) {
                continue;
            }
            if (LaunchMode.DEVELOPMENT.equals(mode) &&
                    a.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier) {
                Project projectDep = project.getRootProject()
                        .findProject(((ProjectComponentIdentifier) a.getId().getComponentIdentifier()).getProjectPath());
                modelDependencies.add(toDependency(a, configurationName, projectDep));
            } else {
                modelDependencies.add(toDependency(a, configurationName));
            }
        }

        return modelDependencies;
    }

    private SourceSetImpl convert(SourceSet sourceSet) {
        return new SourceSetImpl(
                sourceSet.getOutput().getClassesDirs().getFiles(),
                sourceSet.getOutput().getResourcesDir());
    }

    private io.quarkus.bootstrap.resolver.model.SourceSet getSourceSourceSet(SourceSet sourceSet) {
        return new SourceSetImpl(sourceSet.getAllJava().getSrcDirs(),
                sourceSet.getResources().getSourceDirectories().getSingleFile());
    }

    private static boolean isDependency(ResolvedArtifact a) {
        return BootstrapConstants.JAR.equalsIgnoreCase(a.getExtension()) || "exe".equalsIgnoreCase(a.getExtension()) ||
                a.getFile().isDirectory();
    }

    private DependencyImpl toDependency(ResolvedArtifact a, String configuration, Project project) {
        final String[] split = a.getModuleVersion().toString().split(":");
        final DependencyImpl dependency = new DependencyImpl(split[1], split[0], split.length > 2 ? split[2] : null,
                configuration, a.getType(), a.getClassifier());
        final JavaPluginConvention javaConvention = project.getConvention().findPlugin(JavaPluginConvention.class);
        if (javaConvention != null) {
            SourceSet mainSourceSet = javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            final File classesDir = new File(QuarkusGradleUtils.getClassesDir(mainSourceSet, project.getBuildDir(), false));
            if (classesDir.exists()) {
                dependency.addPath(classesDir);
            }
            for (File resourcesDir : mainSourceSet.getResources().getSourceDirectories()) {
                if (resourcesDir.exists()) {
                    dependency.addPath(resourcesDir);
                }
            }
        }
        return dependency;
    }

    static DependencyImpl toDependency(ResolvedArtifact a, String configuration) {
        final String[] split = a.getModuleVersion().toString().split(":");

        final DependencyImpl dependency = new DependencyImpl(split[1], split[0], split.length > 2 ? split[2] : null,
                configuration, a.getType(), a.getClassifier());
        dependency.addPath(a.getFile());
        return dependency;
    }

}
