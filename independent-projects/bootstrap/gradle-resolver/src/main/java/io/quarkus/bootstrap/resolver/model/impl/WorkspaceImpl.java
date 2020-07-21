package io.quarkus.bootstrap.resolver.model.impl;

import io.quarkus.bootstrap.resolver.model.ArtifactCoords;
import io.quarkus.bootstrap.resolver.model.Workspace;
import io.quarkus.bootstrap.resolver.model.WorkspaceModule;
import java.io.Serializable;
import java.util.Set;

public class WorkspaceImpl implements Workspace, Serializable {

    public ArtifactCoords mainModuleKey;
    public Set<WorkspaceModule> modules;

    public WorkspaceImpl(ArtifactCoords mainModuleKey, Set<WorkspaceModule> modules) {
        this.mainModuleKey = mainModuleKey;
        this.modules = modules;
    }

    @Override
    public WorkspaceModule getMainModule() {
        return getModule(mainModuleKey);
    }

    @Override
    public Set<WorkspaceModule> getAllModules() {
        return modules;
    }

    @Override
    public WorkspaceModule getModule(ArtifactCoords key) {
        for (WorkspaceModule module : modules) {
            if (module.getArtifactCoords().equals(key)) {
                return module;
            }
        }
        return null;
    }
}
