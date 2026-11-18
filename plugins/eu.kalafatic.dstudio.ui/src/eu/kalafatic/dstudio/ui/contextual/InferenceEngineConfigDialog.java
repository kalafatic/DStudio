package eu.kalafatic.dstudio.ui.contextual;

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

/**
 * Simple key/value editor for inference-provider configuration, mirroring the existing
 * harness configuration dialog. Sensitive values are read back from the registry masked;
 * leaving a masked value untouched keeps the previously stored secret.
 */
public class InferenceEngineConfigDialog extends Dialog {

    private final String engineName;
    private final Map<String, String> current;
    private final Map<String, Text> fields = new LinkedHashMap<>();
    private Map<String, String> result = Map.of();

    public InferenceEngineConfigDialog(Shell parentShell, String engineName,
            Map<String, String> current) {
        super(parentShell);
        this.engineName = engineName;
        this.current = current == null ? Map.of() : current;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Configure " + engineName);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(2, false));
        for (Map.Entry<String, String> entry : current.entrySet()) {
            Label label = new Label(container, SWT.NONE);
            label.setText(entry.getKey());
            Text text = new Text(container, SWT.BORDER);
            text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            text.setText(entry.getValue());
            fields.put(entry.getKey(), text);
        }
        return container;
    }

    @Override
    protected void okPressed() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, Text> entry : fields.entrySet()) {
            String value = entry.getValue().getText().trim();
            if (!value.isBlank() && !"••••••••".equals(value)) {
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
