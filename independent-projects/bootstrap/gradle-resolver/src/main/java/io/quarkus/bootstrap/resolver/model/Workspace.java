package io.quarkus.bootstrap.resolver.model;

import java.util.Set;

public interface Workspace {

    WorkspaceModule getMainModule();

    Set<WorkspaceModule> getAllModules();

    WorkspaceModule getModule(ArtifactCoords key);

}
