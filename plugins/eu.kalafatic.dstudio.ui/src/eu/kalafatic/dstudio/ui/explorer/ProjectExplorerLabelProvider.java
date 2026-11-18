package eu.kalafatic.dstudio.ui.explorer;

import eu.kalafatic.dstudio.core.model.Project;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

public class ProjectExplorerLabelProvider extends LabelProvider {

    @Override
    public String getText(Object element) {
        if (element instanceof Project project) {
            return project.name();
        }
        if (element instanceof ProjectExplorerContentProvider.ProjectFileEntry entry) {
            return entry.file().getName();
        }
        return super.getText(element);
    }

    @Override
    public Image getImage(Object element) {
        if (element instanceof Project) {
            return PlatformUI.getWorkbench().getSharedImages()
                    .getImage(ISharedImages.IMG_OBJ_PROJECT);
        }
        if (element instanceof ProjectExplorerContentProvider.ProjectFileEntry entry) {
            if (entry.file().isDirectory()) {
                return PlatformUI.getWorkbench().getSharedImages()
                        .getImage(ISharedImages.IMG_OBJ_FOLDER);
            }
            return PlatformUI.getWorkbench().getSharedImages()
                    .getImage(ISharedImages.IMG_OBJ_FILE);
        }
        return super.getImage(element);
    }
}
