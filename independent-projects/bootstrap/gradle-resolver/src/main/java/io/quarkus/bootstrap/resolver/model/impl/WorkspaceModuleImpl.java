package io.quarkus.bootstrap.resolver.model.impl;

import io.quarkus.bootstrap.resolver.model.ArtifactCoords;
import io.quarkus.bootstrap.resolver.model.SourceSet;
import io.quarkus.bootstrap.resolver.model.WorkspaceModule;
import java.io.File;
import java.io.Serializable;

public class WorkspaceModuleImpl implements WorkspaceModule, Serializable {

    private final ArtifactCoords artifactCoords;
    private final File projectRoot;
    private final File buildDir;
    private final SourceSet sourceSourceSet;
    private final SourceSet sourceSet;

    public WorkspaceModuleImpl(ArtifactCoords artifactCoords, File projectRoot, File buildDir, SourceSet sourceSourceSet,
            SourceSet sourceSet) {
        this.artifactCoords = artifactCoords;
        this.projectRoot = projectRoot;
        this.buildDir = buildDir;
        this.sourceSourceSet = sourceSourceSet;
        this.sourceSet = sourceSet;
    }

    @Override
    public ArtifactCoords getArtifactCoords() {
        return artifactCoords;
    }

    @Override
    public File getProjectRoot() {
        return projectRoot;
    }

    @Override
    public File getBuildDir() {
        return buildDir;
    }

    @Override
    public SourceSet getSourceSet() {
        return sourceSet;
    }

    @Override
    public SourceSet getSourceSourceSet() {
        return sourceSourceSet;
    }
}
