package eu.kalafatic.dstudio.ui.explorer;

import eu.kalafatic.dstudio.core.model.Project;
import eu.kalafatic.dstudio.core.service.ProjectService;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jface.viewers.ITreeContentProvider;

public class ProjectExplorerContentProvider implements ITreeContentProvider {

    private ProjectService projectService;

    public ProjectExplorerContentProvider(ProjectService projectService) {
        this.projectService = projectService;
    }

    public void setProjectService(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public Object[] getElements(Object inputElement) {
        if (projectService == null) {
            return new Object[0];
        }
        return projectService.listProjects().toArray();
    }

    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof Project project) {
            File root = new File(project.rootPath());
            File[] children = root.listFiles();
            if (children == null) {
                return new Object[0];
            }
            List<Object> visible = new ArrayList<>();
            Arrays.stream(children).sorted((a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            }).forEach(f -> {
                if (!f.isHidden()) {
                    visible.add(new ProjectFileEntry(project, f));
                }
            });
            return visible.toArray();
        }
        if (parentElement instanceof ProjectFileEntry entry) {
            File[] children = entry.file().listFiles();
            if (children == null) {
                return new Object[0];
            }
            List<Object> visible = new ArrayList<>();
            Arrays.stream(children).sorted((a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            }).forEach(f -> {
                if (!f.isHidden()) {
                    visible.add(new ProjectFileEntry(entry.project(), f));
                }
            });
            return visible.toArray();
        }
        return new Object[0];
    }

    @Override
    public Object getParent(Object element) {
        if (element instanceof ProjectFileEntry entry) {
            File parent = entry.file().getParentFile();
            if (parent == null || parent.toPath().normalize().toString()
                    .equals(PathOf(entry.project()))) {
                return entry.project();
            }
            return new ProjectFileEntry(entry.project(), parent);
        }
        return null;
    }

    private String PathOf(Project project) {
        return java.nio.file.Path.of(project.rootPath()).toAbsolutePath().normalize()
                .toString();
    }

    @Override
    public boolean hasChildren(Object element) {
        return element instanceof Project || element instanceof ProjectFileEntry;
    }

    public record ProjectFileEntry(Project project, File file) {
    }
}
