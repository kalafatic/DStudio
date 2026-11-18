package eu.kalafatic.dstudio.core.service;

import eu.kalafatic.dstudio.core.model.Project;
import java.util.List;
import java.util.Optional;

public interface ProjectService {

    Project openProject(String rootPath);

    void closeProject(String projectId);

    Optional<Project> findProject(String projectId);

    List<Project> listProjects();
}
