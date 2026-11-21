package eu.kalafatic.dstudio.ui.workbench;

import eu.kalafatic.dstudio.core.model.Project;
import eu.kalafatic.dstudio.core.service.ProjectService;
import eu.kalafatic.dstudio.ui.DStudioUiPlugin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Single terminal-launch implementation shared by the Tools menu, the Project Explorer
 * context menu and the family contextual action {@code eu.kalafatic.contextual.terminal
 * .open}. Probes the family terminal convention (x-terminal-emulator, gnome-terminal,
 * konsole, xterm) and reports failures through a message dialog.
 *
 * <p>The working directory is resolved from the given directory, falling back to the
 * active project (workbench selection, then the single registered project) and finally
 * to the user home directory.</p>
 */
public final class TerminalLauncher {

    private TerminalLauncher() {
    }

    /**
     * Opens a terminal in the given working directory. When {@code workingDirectory} is
     * null, empty or not an existing directory, the active project directory (or the
     * user home directory as the last resort) is used.
     */
    public static void open(String workingDirectory) {
        String directory = workingDirectory;
        if (directory == null || directory.isBlank()
                || !Files.isDirectory(Path.of(directory))) {
            directory = resolveDefaultDirectory();
        }
        String[] command = terminalCommand(directory);
        if (command == null) {
            MessageDialog.openError(shell(), "Open Terminal",
                    "No supported terminal emulator found (tried x-terminal-emulator, "
                            + "gnome-terminal, konsole, xterm).");
            return;
        }
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            MessageDialog.openError(shell(), "Open Terminal",
                    "Failed to open terminal: " + e.getMessage());
        }
    }

    /**
     * Resolves the directory used when no specific one was requested: first the
     * workbench selection (a selected project, or a file entry inside one, carries the
     * project root), then the single registered project, then the user home directory.
     */
    private static String resolveDefaultDirectory() {
        String directory = activeProjectDirectory();
        if (directory == null || directory.isBlank()
                || !Files.isDirectory(Path.of(directory))) {
            directory = System.getProperty("user.home", ".");
        }
        return directory;
    }

    private static String activeProjectDirectory() {
        IWorkbenchWindow window = PlatformUI.getWorkbench() == null ? null
                : PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window != null && window.getSelectionService() != null) {
            ISelection selection = window.getSelectionService().getSelection();
            if (selection instanceof IStructuredSelection structured
                    && !structured.isEmpty()) {
                Object element = structured.getFirstElement();
                if (element instanceof Project project) {
                    return project.rootPath();
                }
                if (element instanceof eu.kalafatic.dstudio.ui.explorer
                        .ProjectExplorerContentProvider.ProjectFileEntry entry) {
                    return entry.project().rootPath();
                }
            }
        }
        ProjectService projectService = service();
        if (projectService != null) {
            List<Project> projects = projectService.listProjects();
            if (projects.size() == 1) {
                return projects.get(0).rootPath();
            }
        }
        return null;
    }

    private static ProjectService service() {
        DStudioUiPlugin plugin = DStudioUiPlugin.getInstance();
        return plugin == null ? null : plugin.projectService();
    }

    private static Shell shell() {
        IWorkbenchWindow window = PlatformUI.getWorkbench() == null ? null
                : PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        return window == null ? null : window.getShell();
    }

    static String[] terminalCommand(String directory) {
        if (hasCommand("x-terminal-emulator")) {
            return new String[] {"x-terminal-emulator",
                "--working-directory=" + directory};
        }
        if (hasCommand("gnome-terminal")) {
            return new String[] {"gnome-terminal", "--working-directory=" + directory};
        }
        if (hasCommand("konsole")) {
            return new String[] {"konsole", "--workdir", directory};
        }
        if (hasCommand("xterm")) {
            return new String[] {"xterm", "-e",
                "cd " + shellQuote(directory) + " && $SHELL"};
        }
        return null;
    }

    static boolean hasCommand(String command) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(":")) {
            if (Files.isExecutable(Path.of(dir, command))) {
                return true;
            }
        }
        return false;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
