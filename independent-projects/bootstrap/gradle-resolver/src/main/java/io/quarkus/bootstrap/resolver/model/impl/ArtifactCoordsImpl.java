package io.quarkus.bootstrap.resolver.model.impl;

import io.quarkus.bootstrap.resolver.model.ArtifactCoords;
import java.io.Serializable;

public class ArtifactCoordsImpl implements ArtifactCoords, Serializable {

    public static final String TYPE_JAR = "jar";

    private final String groupId;
    private final String artifactId;
    private final String classifier;
    private final String version;
    private final String type;

    public ArtifactCoordsImpl(String groupId, String artifactId, String version) {
        this(groupId, artifactId, "", version, TYPE_JAR);
    }

    public ArtifactCoordsImpl(String groupId, String artifactId, String classifier, String version, String type) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classifier = classifier;
        this.version = version;
        this.type = type;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @Override
    public String getClassifier() {
        return classifier;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getType() {
        return type;
    }
}
