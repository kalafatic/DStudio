package eu.kalafatic.dstudio.ui.agent;

import eu.kalafatic.dstudio.core.agent.AgentProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Create/edit dialog for agent profiles. Fields: name, description, role, system
 * instructions, tool permissions (comma separated), restrictions (comma separated),
 * preferred harness, preferred provider, preferred model and execution parameters
 * (key=value, comma separated).
 */
public class AgentConfigDialog extends Dialog {

    private final AgentProfile existing;
    private Text nameText;
    private Text descriptionText;
    private Text roleText;
    private Text instructionsText;
    private Text permissionsText;
    private Text restrictionsText;
    private Text harnessText;
    private Text providerText;
    private Text modelText;
    private Text parametersText;
    private AgentProfile result;

    public AgentConfigDialog(Shell parent, AgentProfile existing) {
        super(parent);
        this.existing = existing;
    }

    public AgentProfile getProfile() {
        return result;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        getShell().setText(existing == null ? "Create Agent Profile" : "Edit Agent Profile");
        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(container);

        nameText = field(container, "Name:", existing == null ? "" : existing.getName());
        descriptionText = field(container, "Description:",
                existing == null ? "" : existing.getDescription());
        roleText = field(container, "Role:", existing == null ? "" : existing.getRole());
        instructionsText = field(container, "System Instructions:",
                existing == null ? "" : existing.getSystemInstructions(), true);
        permissionsText = field(container, "Tool Permissions (comma separated):",
                existing == null ? "" : String.join(",", existing.getToolPermissions()));
        restrictionsText = field(container, "Restrictions (comma separated):",
                existing == null ? "" : String.join(",", existing.getRestrictions()));
        harnessText = field(container, "Preferred Harness (id, empty = any):",
                existing == null ? "" : existing.getPreferredHarnessId());
        providerText = field(container, "Preferred Provider (engine id, empty = any):",
                existing == null ? "" : existing.getPreferredProviderId());
        modelText = field(container, "Preferred Model (empty = any):",
                existing == null ? "" : existing.getPreferredModel());
        parametersText = field(container, "Execution Parameters (key=value, comma separated):",
                existing == null ? "" : joinParameters(existing.getExecutionParameters()));
        return container;
    }

    private Text field(Composite parent, String label, String initial) {
        return field(parent, label, initial, false);
    }

    private Text field(Composite parent, String label, String initial, boolean multiline) {
        Label labelControl = new Label(parent, SWT.NONE);
        labelControl.setText(label);
        Text text = new Text(parent, multiline ? (SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL)
                : SWT.BORDER);
        text.setText(initial == null ? "" : initial);
        GridDataFactory.fillDefaults().grab(true, false).hint(380,
                multiline ? 90 : SWT.DEFAULT).applyTo(text);
        return text;
    }

    private static String joinParameters(Map<String, String> parameters) {
        List<String> parts = new ArrayList<>();
        parameters.forEach((k, v) -> parts.add(k + "=" + v));
        return String.join(",", parts);
    }

    @Override
    protected void okPressed() {
        String name = nameText.getText().trim();
        if (name.isBlank()) {
            org.eclipse.jface.dialogs.MessageDialog.openWarning(getShell(),
                    "Agent Profile", "A profile name is required.");
            return;
        }
        String id = existing == null
                ? "agent-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : existing.getId();
        Instant now = Instant.now();
        result = new AgentProfile(id, name, descriptionText.getText().trim(),
                roleText.getText().trim(), instructionsText.getText().trim(),
                splitList(permissionsText.getText()), splitList(restrictionsText.getText()),
                harnessText.getText().trim(), providerText.getText().trim(),
                modelText.getText().trim(), parseParameters(parametersText.getText()),
                existing == null ? now : existing.getCreatedAt(), now);
        super.okPressed();
    }

    private static List<String> splitList(String text) {
        List<String> result = new ArrayList<>();
        for (String part : text.split(",")) {
            String token = part.trim();
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    private static Map<String, String> parseParameters(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : text.split(",")) {
            String token = part.trim();
            int equals = token.indexOf('=');
            if (equals > 0) {
                result.put(token.substring(0, equals).trim(),
                        token.substring(equals + 1).trim());
            }
        }
        return result;
    }

    @Override
    protected boolean isResizable() {
        return true;
    }
}
