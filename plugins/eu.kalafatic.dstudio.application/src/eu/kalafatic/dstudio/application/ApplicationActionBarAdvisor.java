package eu.kalafatic.dstudio.application;

import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleCoordinator;
import eu.kalafatic.dstudio.core.config.DStudioConfiguration;
import eu.kalafatic.dstudio.core.model.Project;
import eu.kalafatic.dstudio.teip.server.DStudioServer;
import java.util.List;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;

/**
 * Standard professional-IDE menu bar: File / Edit / Navigate / Search / Models /
 * Harness / Window / Help from the workbench action factory plus the DStudio family
 * actions (web interface, terminal, model and harness surfaces). Project, Run and AI
 * menus are contributed by the UI bundle through org.eclipse.ui.menus so the layering
 * stays ui → application.
 */
public class ApplicationActionBarAdvisor extends ActionBarAdvisor {

    private static final String SITES_PREFERENCE_PAGE =
            "org.eclipse.equinox.internal.p2.ui.sdk.SitesPreferencePage";
    private static final String COMMAND_INSTALLATION_DETAILS =
            "org.eclipse.equinox.p2.ui.sdk.installationDetails";

    // View ids from the UI bundle's plugin.xml (layering ui → application: only the
    // ids are referenced here, never UI-bundle classes).
    private static final String INFERENCE_VIEW_ID =
            "eu.kalafatic.dstudio.ui.view.inference";
    private static final String HARNESS_VIEW_ID =
            "eu.kalafatic.dstudio.ui.view.harness";
    // Commands contributed by the UI bundle (runtime-resolved).
    private static final String RUN_HARNESS_COMMAND =
            "eu.kalafatic.dstudio.command.runHarness";
    private static final String REFRESH_INFERENCE_MODELS_COMMAND =
            "eu.kalafatic.dstudio.command.refreshInferenceModels";

    private static final String ACTION_OPEN_WEB_INTERFACE =
            "dstudio.openWebInterface";
    private static final String ACTION_OPEN_TERMINAL = "dstudio.openTerminal";
    private static final String ACTION_SHOW_INFERENCE_VIEW = "dstudio.showInferenceView";
    private static final String ACTION_REFRESH_MODELS = "dstudio.refreshModels";
    private static final String ACTION_SHOW_HARNESS_VIEW = "dstudio.showHarnessView";
    private static final String ACTION_RUN_HARNESS = "dstudio.runHarness";

    private IWorkbenchWindow window;

    public ApplicationActionBarAdvisor(IActionBarConfigurer configurer) {
        super(configurer);
    }

    @Override
    protected void makeActions(IWorkbenchWindow window) {
        this.window = window;
        register(ActionFactory.SAVE.create(window));
        register(ActionFactory.SAVE_AS.create(window));
        register(ActionFactory.SAVE_ALL.create(window));
        register(ActionFactory.REVERT.create(window));
        register(ActionFactory.IMPORT.create(window));
        register(ActionFactory.EXPORT.create(window));
        register(ActionFactory.CLOSE.create(window));
        register(ActionFactory.CLOSE_ALL.create(window));
        register(ActionFactory.QUIT.create(window));
        register(ActionFactory.UNDO.create(window));
        register(ActionFactory.REDO.create(window));
        register(ActionFactory.CUT.create(window));
        register(ActionFactory.COPY.create(window));
        register(ActionFactory.PASTE.create(window));
        register(ActionFactory.DELETE.create(window));
        register(ActionFactory.SELECT_ALL.create(window));
        register(ActionFactory.BACKWARD_HISTORY.create(window));
        register(ActionFactory.FORWARD_HISTORY.create(window));
        register(ActionFactory.FORWARD.create(window));
        register(ActionFactory.FIND.create(window));
        register(ActionFactory.OPEN_NEW_WINDOW.create(window));
        register(ActionFactory.RESET_PERSPECTIVE.create(window));
        register(ActionFactory.PREFERENCES.create(window));
        register(ActionFactory.ABOUT.create(window));

        register(action(ACTION_OPEN_WEB_INTERFACE, "Open &Web Interface",
                this::openWebInterface));
        register(action(ACTION_OPEN_TERMINAL, "Open &Terminal", this::openTerminal));
        register(action(ACTION_SHOW_INFERENCE_VIEW, "Show &Inference View",
                () -> showView(INFERENCE_VIEW_ID)));
        register(action(ACTION_REFRESH_MODELS, "&Refresh Models",
                () -> executeCommand(REFRESH_INFERENCE_MODELS_COMMAND)));
        register(action(ACTION_SHOW_HARNESS_VIEW, "Show &Harness View",
                () -> showView(HARNESS_VIEW_ID)));
        register(action(ACTION_RUN_HARNESS, "&Run Harness…",
                () -> executeCommand(RUN_HARNESS_COMMAND)));
    }

    private org.eclipse.jface.action.Action action(String id, String text, Runnable body) {
        org.eclipse.jface.action.Action action = new org.eclipse.jface.action.Action(text,
                IAction.AS_PUSH_BUTTON) {
            @Override
            public void run() {
                body.run();
            }
        };
        action.setId(id);
        return action;
    }

    @Override
    protected void fillMenuBar(IMenuManager menuBar) {
        menuBar.add(fileMenu());
        menuBar.add(editMenu());
        menuBar.add(navigateMenu());
        menuBar.add(searchMenu());
        menuBar.add(modelsMenu());
        menuBar.add(harnessMenu());
        // "Project", "Run" and "AI" menus are contributed by the UI bundle via
        // org.eclipse.ui.menus (menu:org.eclipse.ui.main.menu)
        menuBar.add(windowMenu());
        menuBar.add(helpMenu());
    }

    private MenuManager fileMenu() {
        MenuManager menu = new MenuManager("&File", "file");
        menu.add(getAction(ActionFactory.SAVE.getId()));
        menu.add(getAction(ActionFactory.SAVE_AS.getId()));
        menu.add(getAction(ActionFactory.SAVE_ALL.getId()));
        menu.add(getAction(ActionFactory.REVERT.getId()));
        menu.add(new org.eclipse.jface.action.Separator());
        menu.add(getAction(ActionFactory.IMPORT.getId()));
        menu.add(getAction(ActionFactory.EXPORT.getId()));
        menu.add(new org.eclipse.jface.action.Separator());
        menu.add(getAction(ACTION_OPEN_WEB_INTERFACE));
        menu.add(getAction(ACTION_OPEN_TERMINAL));
        menu.add(new org.eclipse.jface.action.Separator());
        menu.add(getAction(ActionFactory.CLOSE.getId()));
        menu.add(getAction(ActionFactory.CLOSE_ALL.getId()));
        menu.add(new org.eclipse.jface.action.Separator());
        menu.add(getAction(ActionFactory.QUIT.getId()));
        return menu;
    }

    private MenuManager editMenu() {
        MenuManager menu = new MenuManager("&Edit", "edit");
        menu.add(getAction(ActionFactory.UNDO.getId()));
        menu.add(getAction(ActionFactory.REDO.getId()));
        menu.add(new org.eclipse.jface.action.Separator());
        menu.add(getAction(ActionFactory.CUT.getId()));
        menu.add(getAction(ActionFactory.COPY.getId()));
        menu.add(getAction(ActionFactory.PASTE.getId()));
        menu.add(getAction(ActionFactory.DELETE.getId()));
        menu.add(getAction(ActionFactory.SELECT_ALL.getId()));
        return menu;
    }

    private MenuManager navigateMenu() {
        MenuManager menu = new MenuManager("&Navigate", "navigate");
        menu.add(getAction(ActionFactory.BACKWARD_HISTORY.getId()));
        menu.add(getAction(ActionFactory.FORWARD_HISTORY.getId()));
        menu.add(new org.eclipse.jface.action.Separator());
        menu.add(getAction(ActionFactory.FORWARD.getId()));
        return menu;
    }

    private MenuManager searchMenu() {
        MenuManager menu = new MenuManager("&Search", "search");
        menu.add(getAction(ActionFactory.FIND.getId()));
        return menu;
    }

    private MenuManager modelsMenu() {
        MenuManager menu = new MenuManager("&Models", "models");
        menu.add(getAction(ACTION_SHOW_INFERENCE_VIEW));
        menu.add(getAction(ACTION_REFRESH_MODELS));
        return menu;
    }

    private MenuManager harnessMenu() {
        MenuManager menu = new MenuManager("&Harness", "harness");
        menu.add(getAction(ACTION_SHOW_HARNESS_VIEW));
        menu.add(getAction(ACTION_RUN_HARNESS));
        return menu;
    }

    private MenuManager windowMenu() {
        MenuManager menu = new MenuManager("&Window", "window");
        menu.add(getAction(ActionFactory.OPEN_NEW_WINDOW.getId()));
        menu.add(getAction(ActionFactory.RESET_PERSPECTIVE.getId()));
        menu.add(new org.eclipse.jface.action.Separator());
        menu.add(getAction(ActionFactory.PREFERENCES.getId()));
        return menu;
    }

    private MenuManager helpMenu() {
        MenuManager menu = new MenuManager("&Help", IWorkbenchActionConstants.M_HELP);
        menu.add(new org.eclipse.jface.action.GroupMarker(IWorkbenchActionConstants.HELP_START));

        // Check for Updates and Install New Software... are contributed by the
        // standard Eclipse p2 UI (org.eclipse.equinox.p2.ui.sdk) into the
        // additions group below.

        menu.add(new org.eclipse.jface.action.Separator());

        org.eclipse.jface.action.IAction sitesAction = new org.eclipse.jface.action.Action(
                "Manage Software &Sites...") {
            @Override
            public void run() {
                org.eclipse.ui.dialogs.PreferencesUtil.createPreferenceDialogOn(
                        window.getShell(), SITES_PREFERENCE_PAGE, null, null).open();
            }
        };
        menu.add(sitesAction);

        org.eclipse.ui.menus.CommandContributionItemParameter detailsParameter =
                new org.eclipse.ui.menus.CommandContributionItemParameter(
                        window, null, COMMAND_INSTALLATION_DETAILS,
                        org.eclipse.ui.menus.CommandContributionItem.STYLE_PUSH);
        detailsParameter.label = "&Installation Details...";
        menu.add(new org.eclipse.ui.menus.CommandContributionItem(detailsParameter));

        menu.add(new org.eclipse.jface.action.Separator());

        menu.add(getAction(ActionFactory.ABOUT.getId()));

        menu.add(new org.eclipse.jface.action.GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
        menu.add(new org.eclipse.jface.action.GroupMarker(IWorkbenchActionConstants.HELP_END));
        return menu;
    }

    // ---------------------------------------------------------- DStudio family actions

    /**
     * Opens the DStudio web interface in the system browser using the port the backend
     * server is actually bound to; falls back to the configured TEIP port and finally
     * to the family default. The port is never hardcoded.
     */
    private void openWebInterface() {
        int port = -1;
        DStudioLifecycleCoordinator coordinator = DStudioRuntime.getCoordinator();
        if (coordinator != null) {
            DStudioServer server = coordinator.getServer();
            if (server != null && server.isRunning()) {
                port = server.getBoundPort();
            }
            if (port <= 0 && coordinator.getConfiguration() != null) {
                port = coordinator.getConfiguration().teipPort();
            }
        }
        if (port <= 0) {
            port = DStudioConfiguration.DEFAULT_TEIP_PORT;
        }
        org.eclipse.swt.program.Program.launch("http://127.0.0.1:" + port + "/");
    }

    /**
     * Opens a system terminal in the active project directory (falling back to the
     * user home directory when no project is open). Terminal detection mirrors the
     * family convention (x-terminal-emulator, gnome-terminal, konsole, xterm).
     */
    private void openTerminal() {
        String directory = activeProjectDirectory();
        if (directory == null || directory.isBlank()
                || !java.nio.file.Files.isDirectory(java.nio.file.Path.of(directory))) {
            directory = System.getProperty("user.home", ".");
        }
        String[] command = terminalCommand(directory);
        if (command == null) {
            org.eclipse.jface.dialogs.MessageDialog.openError(window.getShell(),
                    "Open Terminal",
                    "No supported terminal emulator found (tried x-terminal-emulator, "
                            + "gnome-terminal, konsole, xterm).");
            return;
        }
        try {
            new ProcessBuilder(command).start();
        } catch (java.io.IOException e) {
            org.eclipse.jface.dialogs.MessageDialog.openError(window.getShell(),
                    "Open Terminal", "Failed to open terminal: " + e.getMessage());
        }
    }

    /**
     * Resolves the active project directory: first the workbench selection (the
     * Project Explorer registers its viewer as its selection provider; a selected
     * project, or a file entry inside one, carries the project root), then the single
     * registered project. Null when no active project can be determined.
     */
    private String activeProjectDirectory() {
        ISelection selection = window.getSelectionService().getSelection();
        if (selection instanceof IStructuredSelection structured
                && !structured.isEmpty()) {
            Object element = structured.getFirstElement();
            if (element instanceof Project project) {
                return project.rootPath();
            }
            // ProjectExplorerContentProvider.ProjectFileEntry is a UI-bundle record and
            // the application bundle must not compile against the UI bundle (layering
            // ui → application): read its stable project() accessor reflectively.
            try {
                Object owner = element.getClass().getMethod("project").invoke(element);
                if (owner instanceof Project project) {
                    return project.rootPath();
                }
            } catch (ReflectiveOperationException ignored) {
                // not a project file entry; fall through
            }
        }
        DStudioLifecycleCoordinator coordinator = DStudioRuntime.getCoordinator();
        if (coordinator != null && coordinator.getProjectService() != null) {
            List<Project> projects = coordinator.getProjectService().listProjects();
            if (projects.size() == 1) {
                return projects.get(0).rootPath();
            }
        }
        return null;
    }

    private void showView(String viewId) {
        try {
            window.getActivePage().showView(viewId);
        } catch (org.eclipse.ui.PartInitException e) {
            org.eclipse.jface.dialogs.MessageDialog.openError(window.getShell(),
                    "DStudio", "Could not open the view: " + e.getMessage());
        }
    }

    private void executeCommand(String commandId) {
        org.eclipse.ui.handlers.IHandlerService handlerService =
                window.getService(org.eclipse.ui.handlers.IHandlerService.class);
        if (handlerService != null) {
            try {
                handlerService.executeCommand(commandId, null);
            } catch (Exception e) {
                org.eclipse.jface.dialogs.MessageDialog.openError(window.getShell(),
                        "DStudio", "Command failed: " + e.getMessage());
            }
        }
    }

    private static String[] terminalCommand(String directory) {
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

    private static boolean hasCommand(String command) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(":")) {
            if (java.nio.file.Files.isExecutable(java.nio.file.Path.of(dir, command))) {
                return true;
            }
        }
        return false;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
