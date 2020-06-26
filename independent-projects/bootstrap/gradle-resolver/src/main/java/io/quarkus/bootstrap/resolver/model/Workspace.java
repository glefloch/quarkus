package io.quarkus.bootstrap.resolver.model;

import java.io.File;

public interface Workspace {

    ArtifactCoords getArtifactCoords();

    File getProjectRoot();

    File getBuildDir();

    SourceSet getSourceSet();

    SourceSet getSourceSourceSet();
}
