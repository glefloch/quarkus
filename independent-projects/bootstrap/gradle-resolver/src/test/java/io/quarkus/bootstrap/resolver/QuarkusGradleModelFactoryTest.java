package io.quarkus.bootstrap.resolver;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.bootstrap.resolver.model.QuarkusModel;
import io.quarkus.bootstrap.resolver.model.SourceSet;
import io.quarkus.bootstrap.resolver.model.Workspace;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import org.junit.jupiter.api.Test;

public class QuarkusGradleModelFactoryTest {

    @Test
    public void shouldLoadSimpleModuleModel() throws URISyntaxException {
        File projectDir = getResourcesProject("simple-module-project");
        final QuarkusModel quarkusModel = QuarkusGradleModelFactory.create(projectDir, "TEST");

        assertNotNull(quarkusModel);
        Workspace workspace = quarkusModel.getWorkspace();
        assertWorkspace(workspace, projectDir);
        assertTrue(quarkusModel.getAdditionalWorkspace().isEmpty());
    }

    @Test
    public void shouldLoadMultiModuleModel() throws URISyntaxException {
        File projectDir = getResourcesProject("multi-module-project/application");
        final QuarkusModel quarkusModel = QuarkusGradleModelFactory.create(projectDir, "TEST");

        assertNotNull(quarkusModel);
        Workspace workspace = quarkusModel.getWorkspace();
        assertWorkspace(workspace, projectDir);
        assertEquals(1, quarkusModel.getAdditionalWorkspace().size());
        assertWorkspace(quarkusModel.getAdditionalWorkspace().iterator().next(), new File(projectDir.getParent(), "common"));
    }

    private void assertWorkspace(Workspace workspace, File projectDir) {
        assertNotNull(workspace);
        assertEquals(projectDir, workspace.getProjectRoot());
        assertEquals(new File(projectDir, "build"), workspace.getBuildDir());
        final SourceSet sourceSet = workspace.getSourceSet();
        assertNotNull(sourceSet);
        assertEquals(new File(projectDir, "build/resources/main"), sourceSet.getResourceDirectory());
        assertEquals(1, sourceSet.getSourceDirectories().size());
        assertEquals(new File(projectDir, "build/classes/java/main"), sourceSet.getSourceDirectories().iterator().next());
        final SourceSet sourceSourceSet = workspace.getSourceSourceSet();
        assertEquals(new File(projectDir, "src/main/resources"), sourceSourceSet.getResourceDirectory());
        assertEquals(1, sourceSourceSet.getSourceDirectories().size());
        assertEquals(new File(projectDir, "src/main/java"), sourceSourceSet.getSourceDirectories().iterator().next());
    }

    private File getResourcesProject(String projectName) throws URISyntaxException {
        final URL basedirUrl = Thread.currentThread().getContextClassLoader().getResource(projectName);
        assertNotNull(basedirUrl);
        return new File(basedirUrl.toURI());
    }

}
