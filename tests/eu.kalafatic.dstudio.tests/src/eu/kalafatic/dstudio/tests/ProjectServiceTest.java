package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.model.Project;
import eu.kalafatic.dstudio.core.service.DefaultProjectService;
import eu.kalafatic.dstudio.core.service.ProjectService;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteProjectStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectServiceTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("project-test.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        projectService = new DefaultProjectService(
                new SqliteProjectStore(databaseManager.connection()));
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    void openProjectCreatesPersistedProject() throws Exception {
        Path root = tempDir.resolve("sample-project");
        Files.createDirectories(root);
        Project project = projectService.openProject(root.toString());
        assertEquals("sample-project", project.name());
        assertTrue(project.rootPath().endsWith("sample-project"));
        assertEquals(1, projectService.listProjects().size());
        assertTrue(projectService.findProject(project.id()).isPresent());
    }

    @Test
    void openingSameRootReturnsExistingProject() throws Exception {
        Path root = tempDir.resolve("sample-project");
        Files.createDirectories(root);
        Project first = projectService.openProject(root.toString());
        Project second = projectService.openProject(root.toString());
        assertEquals(first.id(), second.id());
        assertEquals(1, projectService.listProjects().size());
    }

    @Test
    void nonDirectoryRootRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> projectService.openProject(tempDir.resolve("missing").toString()));
    }

    @Test
    void closeProjectRemovesIt() throws Exception {
        Path root = tempDir.resolve("sample-project");
        Files.createDirectories(root);
        Project project = projectService.openProject(root.toString());
        projectService.closeProject(project.id());
        assertTrue(projectService.listProjects().isEmpty());
        assertFalse(projectService.findProject(project.id()).isPresent());
    }
}
