package eu.kalafatic.dstudio.ui.contextual;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Registers a new runtime provider instance through the shared inference-engine factory
 * (OLLAMA, ENR, OPENAI_COMPATIBLE, ANTHROPIC), mirroring the web
 * {@code POST /api/inference/engines} operation.
 */
public class AddProviderDialog extends Dialog {

    private static final String[] TYPES = {
            "OLLAMA", "ENR", "OPENAI_COMPATIBLE", "ANTHROPIC"
    };

    private Combo typeCombo;
    private Text idText;
    private Text nameText;
    private Text endpointText;
    private String type = "OLLAMA";
    private String id = "";
    private String name = "";
    private String endpoint = "";

    public AddProviderDialog(Shell parentShell) {
        super(parentShell);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Add Provider");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(2, false));
        new Label(container, SWT.NONE).setText("Type:");
        typeCombo = new Combo(container, SWT.DROP_DOWN | SWT.READ_ONLY);
        typeCombo.setItems(TYPES);
        typeCombo.select(0);
        new Label(container, SWT.NONE).setText("Id:");
        idText = new Text(container, SWT.BORDER);
        idText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        new Label(container, SWT.NONE).setText("Name:");
        nameText = new Text(container, SWT.BORDER);
        nameText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        new Label(container, SWT.NONE).setText("Endpoint:");
        endpointText = new Text(container, SWT.BORDER);
        endpointText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        endpointText.setText("http://127.0.0.1:11434");
        return container;
    }

    @Override
    protected void okPressed() {
        this.type = typeCombo.getText().trim().toUpperCase();
        this.id = idText.getText().trim();
        this.name = nameText.getText().trim();
        if (name.isBlank()) {
            name = id;
        }
        this.endpoint = endpointText.getText().trim();
        super.okPressed();
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getConfiguration() {
        Map<String, String> configuration = new LinkedHashMap<>();
        if (!endpoint.isBlank()) {
            configuration.put("endpoint", endpoint);
        }
        return configuration;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL,
                false);
    }
}
