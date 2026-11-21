package eu.kalafatic.dstudio.core.contextual;

/**
 * Canonical contextual action ids — Darwin Family Contextual Action Contract v1.0
 * (see docs/contextual-actions/CONTEXTUAL-ACTION-CONTRACT.md in this repository).
 * All four family applications use these identical ids so that equivalent domain
 * operations are addressable the same way on every surface.
 */
public final class ContextActionIds {

    public static final String CONTRACT_VERSION = "1.0";

    // harness-registry
    public static final String HARNESS_REGISTRY_REFRESH = "eu.kalafatic.contextual.harness-registry.refresh";
    public static final String HARNESS_REGISTRY_SCAN = "eu.kalafatic.contextual.harness-registry.scan";
    public static final String HARNESS_REGISTRY_ADD = "eu.kalafatic.contextual.harness-registry.add";

    // harness
    public static final String HARNESS_SELECT = "eu.kalafatic.contextual.harness.select";
    public static final String HARNESS_EDIT = "eu.kalafatic.contextual.harness.edit";
    public static final String HARNESS_CONFIGURE = "eu.kalafatic.contextual.harness.configure";
    public static final String HARNESS_TEST = "eu.kalafatic.contextual.harness.test";
    public static final String HARNESS_ENABLE = "eu.kalafatic.contextual.harness.enable";
    public static final String HARNESS_DISABLE = "eu.kalafatic.contextual.harness.disable";
    public static final String HARNESS_SET_DEFAULT = "eu.kalafatic.contextual.harness.set-default";
    public static final String HARNESS_START = "eu.kalafatic.contextual.harness.start";
    public static final String HARNESS_STOP = "eu.kalafatic.contextual.harness.stop";
    public static final String HARNESS_RESTART = "eu.kalafatic.contextual.harness.restart";
    public static final String HARNESS_VIEW_CAPABILITIES = "eu.kalafatic.contextual.harness.view-capabilities";
    public static final String HARNESS_VIEW_LOGS = "eu.kalafatic.contextual.harness.view-logs";
    public static final String HARNESS_REMOVE = "eu.kalafatic.contextual.harness.remove";

    // provider-registry
    public static final String PROVIDER_REGISTRY_REFRESH = "eu.kalafatic.contextual.provider-registry.refresh";
    public static final String PROVIDER_REGISTRY_SCAN = "eu.kalafatic.contextual.provider-registry.scan";
    public static final String PROVIDER_REGISTRY_ADD = "eu.kalafatic.contextual.provider-registry.add";

    // provider
    public static final String PROVIDER_SELECT = "eu.kalafatic.contextual.provider.select";
    public static final String PROVIDER_EDIT = "eu.kalafatic.contextual.provider.edit";
    public static final String PROVIDER_CONFIGURE = "eu.kalafatic.contextual.provider.configure";
    public static final String PROVIDER_TEST = "eu.kalafatic.contextual.provider.test";
    public static final String PROVIDER_ENABLE = "eu.kalafatic.contextual.provider.enable";
    public static final String PROVIDER_DISABLE = "eu.kalafatic.contextual.provider.disable";
    public static final String PROVIDER_SET_DEFAULT = "eu.kalafatic.contextual.provider.set-default";
    public static final String PROVIDER_DISCOVER_MODELS = "eu.kalafatic.contextual.provider.discover-models";
    public static final String PROVIDER_VIEW_ENDPOINT = "eu.kalafatic.contextual.provider.view-endpoint";
    public static final String PROVIDER_VIEW_LOGS = "eu.kalafatic.contextual.provider.view-logs";
    public static final String PROVIDER_REMOVE = "eu.kalafatic.contextual.provider.remove";

    // model
    public static final String MODEL_SELECT = "eu.kalafatic.contextual.model.select";
    public static final String MODEL_SET_DEFAULT = "eu.kalafatic.contextual.model.set-default";
    public static final String MODEL_DOWNLOAD = "eu.kalafatic.contextual.model.download";
    public static final String MODEL_LOAD = "eu.kalafatic.contextual.model.load";
    public static final String MODEL_UNLOAD = "eu.kalafatic.contextual.model.unload";
    public static final String MODEL_REMOVE = "eu.kalafatic.contextual.model.remove";
    public static final String MODEL_REFRESH = "eu.kalafatic.contextual.model.refresh";
    public static final String MODEL_TEST = "eu.kalafatic.contextual.model.test";
    public static final String MODEL_FAVORITE = "eu.kalafatic.contextual.model.favorite";
    public static final String MODEL_UNFAVORITE = "eu.kalafatic.contextual.model.unfavorite";
    public static final String MODEL_INFO = "eu.kalafatic.contextual.model.info";
    public static final String MODEL_COPY_INFO = "eu.kalafatic.contextual.model.copy-info";

    // agent-registry / agent
    public static final String AGENT_REGISTRY_CREATE = "eu.kalafatic.contextual.agent-registry.create";
    public static final String AGENT_REGISTRY_IMPORT = "eu.kalafatic.contextual.agent-registry.import";
    public static final String AGENT_SELECT = "eu.kalafatic.contextual.agent.select";
    public static final String AGENT_EDIT = "eu.kalafatic.contextual.agent.edit";
    public static final String AGENT_CLONE = "eu.kalafatic.contextual.agent.clone";
    public static final String AGENT_SET_DEFAULT = "eu.kalafatic.contextual.agent.set-default";
    public static final String AGENT_CONFIGURE_INSTRUCTIONS = "eu.kalafatic.contextual.agent.configure-instructions";
    public static final String AGENT_CONFIGURE_TOOLS = "eu.kalafatic.contextual.agent.configure-tools";
    public static final String AGENT_CONFIGURE_PERMISSIONS = "eu.kalafatic.contextual.agent.configure-permissions";
    public static final String AGENT_EXPORT = "eu.kalafatic.contextual.agent.export";
    public static final String AGENT_COMPATIBILITY = "eu.kalafatic.contextual.agent.compatibility";
    public static final String AGENT_DELETE = "eu.kalafatic.contextual.agent.delete";

    // session / task / execution
    public static final String SESSION_OPEN = "eu.kalafatic.contextual.session.open";
    public static final String SESSION_SELECT = "eu.kalafatic.contextual.session.select";
    public static final String SESSION_SAVE = "eu.kalafatic.contextual.session.save";
    public static final String SESSION_DUPLICATE = "eu.kalafatic.contextual.session.duplicate";
    public static final String SESSION_RESUME = "eu.kalafatic.contextual.session.resume";
    public static final String SESSION_PAUSE = "eu.kalafatic.contextual.session.pause";
    public static final String SESSION_CANCEL = "eu.kalafatic.contextual.session.cancel";
    public static final String SESSION_ARCHIVE = "eu.kalafatic.contextual.session.archive";
    public static final String SESSION_EXPORT = "eu.kalafatic.contextual.session.export";
    public static final String SESSION_DELETE = "eu.kalafatic.contextual.session.delete";
    public static final String SESSION_HISTORY = "eu.kalafatic.contextual.session.history";
    public static final String SESSION_DETAILS = "eu.kalafatic.contextual.session.details";
    public static final String SESSION_ARTIFACTS = "eu.kalafatic.contextual.session.artifacts";

    // project / repository
    public static final String PROJECT_OPEN = "eu.kalafatic.contextual.project.open";
    public static final String PROJECT_SELECT = "eu.kalafatic.contextual.project.select";
    public static final String PROJECT_SCAN = "eu.kalafatic.contextual.project.scan";
    public static final String PROJECT_ANALYZE = "eu.kalafatic.contextual.project.analyze";
    public static final String PROJECT_BUILD = "eu.kalafatic.contextual.project.build";
    public static final String PROJECT_TEST = "eu.kalafatic.contextual.project.test";
    public static final String PROJECT_GIT_STATUS = "eu.kalafatic.contextual.project.git-status";
    public static final String PROJECT_COMMIT = "eu.kalafatic.contextual.project.commit";
    public static final String PROJECT_PUSH = "eu.kalafatic.contextual.project.push";
    public static final String PROJECT_DIAGNOSTICS = "eu.kalafatic.contextual.project.diagnostics";
    public static final String PROJECT_REFRESH = "eu.kalafatic.contextual.project.refresh";
    public static final String PROJECT_ASSIGN_AGENT = "eu.kalafatic.contextual.project.assign-agent";
    public static final String PROJECT_CREATE_SESSION = "eu.kalafatic.contextual.project.create-session";
    public static final String PROJECT_CLOSE = "eu.kalafatic.contextual.project.close";

    // family surfaces (project-level launcher actions, shared ids across the family)
    public static final String TERMINAL_OPEN = "eu.kalafatic.contextual.terminal.open";
    public static final String WEB_OPEN = "eu.kalafatic.contextual.web.open";

    private ContextActionIds() {
    }
}
