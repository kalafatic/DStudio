package eu.kalafatic.dstudio.harness.aider;

import eu.kalafatic.dstudio.core.harness.HarnessAdapter;
import eu.kalafatic.dstudio.core.harness.HarnessCapabilityDescriptor;
import eu.kalafatic.dstudio.core.harness.HarnessConnectionTestResult;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionRequest;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionResult;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionSink;
import eu.kalafatic.dstudio.core.model.ExecutionEventType;
import eu.kalafatic.dstudio.core.service.WorkspaceDiffService;
import eu.kalafatic.dstudio.harness.ContainmentConstraints;
import eu.kalafatic.dstudio.infrastructure.process.ProcessExecutionSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AiderHarnessAdapter implements HarnessAdapter {

    public static final String HARNESS_ID = "aider";
    public static final String HARNESS_NAME = "Aider CLI";
    public static final String HARNESS_TYPE = "AIDER";
    public static final String CONFIG_BINARY = "binary";
    public static final String CONFIG_MODEL = "model";
    public static final String CONFIG_OLLAMA_BASE_URL = "ollamaBaseUrl";
    public static final String CONFIG_OPENAI_API_BASE = "openaiApiBase";
    public static final String CONFIG_TIMEOUT_SECONDS = "timeoutSeconds";
    public static final String CONFIG_MAP_REFRESH = "mapRefresh";
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://127.0.0.1:11434";

    private final WorkspaceDiffService diffService;
    private final Map<String, String> config = new ConcurrentHashMap<>();

    public AiderHarnessAdapter(WorkspaceDiffService diffService) {
        this.diffService = diffService;
        this.config.put(CONFIG_BINARY, "aider");
        this.config.put(CONFIG_MODEL, "");
        this.config.put(CONFIG_OLLAMA_BASE_URL, DEFAULT_OLLAMA_BASE_URL);
        this.config.put(CONFIG_OPENAI_API_BASE, "");
        this.config.put(CONFIG_TIMEOUT_SECONDS, "900");
        this.config.put(CONFIG_MAP_REFRESH, "files");
    }

    @Override
    public String getHarnessId() {
        return HARNESS_ID;
    }

    @Override
    public String getHarnessName() {
        return HARNESS_NAME;
    }

    @Override
    public String getHarnessType() {
        return HARNESS_TYPE;
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("CODE_IMPLEMENTATION");
    }

    @Override
    public HarnessCapabilityDescriptor getCapabilityDescriptor() {
        List<HarnessCapabilityDescriptor.ModelDescriptor> models = new ArrayList<>();
        String model = config.getOrDefault(CONFIG_MODEL, "");
        if (!model.isBlank()) {
            models.add(new HarnessCapabilityDescriptor.ModelDescriptor(model, model,
                    "Aider", true, 0, true));
        }
        return new HarnessCapabilityDescriptor(HARNESS_ID, HARNESS_NAME, HARNESS_TYPE,
                List.of("CODE_IMPLEMENTATION"), List.of("DEFAULT"), models, List.of(),
                Map.of("asyncExecution", true, "eventStreaming", true, "cancellation", true),
                List.of("OLLAMA", "OPENAI_COMPATIBLE", "ANTHROPIC"));
    }

    @Override
    public boolean isAvailable() {
        return testConnection().success();
    }

    @Override
    public HarnessConnectionTestResult testConnection() {
        String binary = config.getOrDefault(CONFIG_BINARY, "aider");
        ProcessExecutionSupport.ProcessExecutionResult result =
                ProcessExecutionSupport.run(List.of(binary, "--version"), null,
                        Map.of(), Map.of(), null, null, () -> false, 10000);
        if (result.timedOut()) {
            return HarnessConnectionTestResult.error("Aider version probe timed out");
        }
        if (result.success()) {
            String version = result.stdoutLines().isEmpty() ? ""
                    : result.stdoutLines().get(0);
            return HarnessConnectionTestResult.ok("Aider CLI available: " + version);
        }
        return HarnessConnectionTestResult.error("Aider CLI not found (" + binary
                + "): " + result.stderrTail(2));
    }

    @Override
    public void updateConfiguration(Map<String, String> configuration) {
        if (configuration != null) {
            config.putAll(configuration);
        }
    }

    @Override
    public Map<String, String> getConnectionConfiguration() {
        return Map.copyOf(config);
    }

    @Override
    public HarnessExecutionResult execute(HarnessExecutionRequest request,
            HarnessExecutionSink sink) {
        Path workingDirectory = request.workingDirectory();
        if (workingDirectory == null || !Files.isDirectory(workingDirectory)) {
            return HarnessExecutionResult.error(-1,
                    "Working directory does not exist: " + workingDirectory);
        }
        String binary = config.getOrDefault(CONFIG_BINARY, "aider");
        // The explicit per-task/per-session model wins; the configured model is only a
        // fallback default. No silent override of the requested model.
        String requestedModel = request.model() != null && !request.model().isBlank()
                ? request.model() : config.getOrDefault(CONFIG_MODEL, "");
        String model = normalizeModel(requestedModel);
        if (model.isBlank()) {
            return HarnessExecutionResult.error(-1,
                    "Aider requires a model (request model or harness config key 'model')");
        }
        long timeoutSeconds;
        try {
            timeoutSeconds = Long.parseLong(
                    config.getOrDefault(CONFIG_TIMEOUT_SECONDS, "900"));
        } catch (NumberFormatException e) {
            timeoutSeconds = 900;
        }
        long timeoutMillis = request.timeout() != null
                ? Math.min(request.timeout().toMillis(), timeoutSeconds * 1000L)
                : timeoutSeconds * 1000L;
        String effectivePrompt = request.prompt() != null && !request.prompt().isBlank()
                ? request.prompt()
                : request.instruction() != null && !request.instruction().isBlank()
                        ? request.instruction()
                        : "Perform the assigned task.";
        effectivePrompt = ContainmentConstraints.appendConstraints(effectivePrompt,
                workingDirectory);
        List<String> command = new ArrayList<>();
        command.add(binary);
        command.add("--no-check-update");
        command.add("--model");
        command.add(model);
        command.add("--message");
        command.add(effectivePrompt);
        command.add("--yes-always");
        command.add("--no-auto-commits");
        command.add("--analytics-disable");
        command.add("--no-show-release-notes");
        command.add("--map-refresh");
        command.add(config.getOrDefault(CONFIG_MAP_REFRESH, "files"));
        String openaiApiBase = config.getOrDefault(CONFIG_OPENAI_API_BASE, "");
        if (!openaiApiBase.isBlank()) {
            command.add("--openai-api-base");
            command.add(openaiApiBase);
        }
        Map<String, String> env = new LinkedHashMap<>();
        env.put("OLLAMA_API_BASE",
                config.getOrDefault(CONFIG_OLLAMA_BASE_URL, DEFAULT_OLLAMA_BASE_URL));
        env.put("CI", "1");
        env.put("NO_COLOR", "1");
        Object snapshot = null;
        if (diffService != null && diffService.isGitRepository(workingDirectory)) {
            snapshot = diffService.takeSnapshot(workingDirectory);
        }
        sink.emit(ExecutionEventType.EXECUTION_STARTED,
                "Aider execution started in " + workingDirectory);
        ProcessExecutionSupport.ProcessExecutionResult result =
                ProcessExecutionSupport.run(command, workingDirectory, env, Map.of(),
                        line -> sink.log(stripAnsi(line)), line -> sink.log(stripAnsi(line)),
                        request.cancellationSupplier(), timeoutMillis);
        if (result.cancelled()) {
            return HarnessExecutionResult.cancelled("Aider execution cancelled");
        }
        if (result.timedOut()) {
            return HarnessExecutionResult.timedOut("Aider execution timed out after "
                    + timeoutMillis + " ms");
        }
        if (!result.success()) {
            return HarnessExecutionResult.error(result.exitCode(),
                    "Aider exited with code " + result.exitCode() + ": "
                            + result.stderrTail(5));
        }
        List<String> modifiedFiles = new ArrayList<>();
        if (diffService != null && snapshot != null) {
            for (String file : diffService.collectChangedFiles(snapshot,
                    workingDirectory)) {
                if (isAiderArtifact(file)) {
                    continue;
                }
                Path resolved = workingDirectory.resolve(file).normalize();
                if (!resolved.startsWith(workingDirectory.normalize())) {
                    return HarnessExecutionResult.error(-1,
                            "Aider workspace containment violation: "
                                    + "change outside workspace: " + file);
                }
                modifiedFiles.add(file);
            }
        }
        StringBuilder allDiffs = new StringBuilder();
        if (diffService != null && snapshot != null) {
            for (String file : modifiedFiles) {
                allDiffs.append(diffService.diffText(snapshot, workingDirectory, file))
                        .append('\n');
            }
        }
        sink.emit(ExecutionEventType.EXECUTION_COMPLETED,
                "Aider execution completed; modified files: " + modifiedFiles.size());
        return HarnessExecutionResult.ok("Aider execution completed in "
                + workingDirectory.getFileName() + "; modified files: "
                + modifiedFiles.size(), modifiedFiles, allDiffs.toString());
    }

    public static String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return "";
        }
        String trimmed = model.trim();
        if (trimmed.contains("/")) {
            return trimmed;
        }
        return "ollama/" + trimmed;
    }

    public static boolean isAiderArtifact(String path) {
        return path.startsWith(".aider") || path.contains("/.aider");
    }

    public static String stripAnsi(String line) {
        if (line == null) {
            return "";
        }
        return line.replaceAll("\\u001B\\[[0-9;]*[A-Za-z]", "").replaceAll("\\r", "");
    }
}
