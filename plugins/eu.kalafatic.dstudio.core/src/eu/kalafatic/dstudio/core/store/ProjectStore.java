package eu.kalafatic.dstudio.core.store;

import eu.kalafatic.dstudio.core.model.Project;
import java.util.List;
import java.util.Optional;

public interface ProjectStore {

    void saveProject(Project project);

    void removeProject(String projectId);

    Optional<Project> findProject(String projectId);

    Optional<Project> findProjectByRootPath(String rootPath);

    List<Project> listProjects();
}
