package eu.kalafatic.dstudio.ui.harness;

import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.model.HarnessDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class HarnessConfigDialog extends Dialog {

    private final HarnessDescriptor descriptor;
    private final HarnessRegistry registry;
    private final Map<String, Text> fields = new LinkedHashMap<>();
    private Map<String, String> result = Map.of();

    public HarnessConfigDialog(Shell parentShell, HarnessDescriptor descriptor,
            HarnessRegistry registry) {
        super(parentShell);
        this.descriptor = descriptor;
        this.registry = registry;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Configure " + descriptor.getName());
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        GridLayout layout = new GridLayout(2, false);
        container.setLayout(layout);
        Map<String, String> current = registry.getConfiguration(descriptor.getId());
        String[] keys = switch (descriptor.getId()) {
            case "opencode" -> new String[] {"binary", "agent", "model", "autoApprove",
                "timeoutSeconds", "streamFormat"};
            case "aider" -> new String[] {"binary", "model", "ollamaBaseUrl",
                "openaiApiBase", "timeoutSeconds", "mapRefresh"};
            default -> current.keySet().toArray(String[]::new);
        };
        for (String key : keys) {
            Label label = new Label(container, SWT.NONE);
            label.setText(key);
            Text text = new Text(container, SWT.BORDER);
            text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            text.setText(current.getOrDefault(key, ""));
            fields.put(key, text);
        }
        return container;
    }

    @Override
    protected void okPressed() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, Text> entry : fields.entrySet()) {
            String value = entry.getValue().getText().trim();
            if (!value.isBlank()) {
                values.put(entry.getKey(), value);
            }
        }
        this.result = values;
        super.okPressed();
    }

    public Map<String, String> getConfiguration() {
        return result;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL,
                false);
    }
}
