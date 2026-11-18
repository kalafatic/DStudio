package eu.kalafatic.dstudio.core.service;

import eu.kalafatic.dstudio.core.model.Project;
import eu.kalafatic.dstudio.core.store.ProjectStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DefaultProjectService implements ProjectService {

    private final ProjectStore store;

    public DefaultProjectService(ProjectStore store) {
        this.store = store;
    }

    @Override
    public Project openProject(String rootPath) {
        Path root = Path.of(rootPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Project root is not a directory: " + root);
        }
        Optional<Project> existing = store.findProjectByRootPath(root.toString());
        if (existing.isPresent()) {
            return existing.get();
        }
        String id = "proj-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String name = root.getFileName() == null ? root.toString() : root.getFileName().toString();
        Project project = new Project(id, name, root.toString());
        store.saveProject(project);
        return project;
    }

    @Override
    public void closeProject(String projectId) {
        store.removeProject(projectId);
    }

    @Override
    public Optional<Project> findProject(String projectId) {
        return store.findProject(projectId);
    }

    @Override
    public List<Project> listProjects() {
        return store.listProjects();
    }
}
