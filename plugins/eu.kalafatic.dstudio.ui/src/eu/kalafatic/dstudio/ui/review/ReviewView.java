package eu.kalafatic.dstudio.ui.review;

import eu.kalafatic.dstudio.core.model.ChangeReview;
import eu.kalafatic.dstudio.core.model.ExecutionTask;
import eu.kalafatic.dstudio.core.model.ReviewDecision;
import eu.kalafatic.dstudio.core.service.ExecutionService;
import eu.kalafatic.dstudio.core.service.ReviewService;
import eu.kalafatic.dstudio.core.service.WorkspaceDiffService;
import eu.kalafatic.dstudio.ui.DStudioUiPlugin;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

public class ReviewView extends ViewPart {

    public static final String VIEW_ID = "eu.kalafatic.dstudio.ui.view.review";

    private Combo executionCombo;
    private TableViewer viewer;
    private Button acceptButton;
    private Button rejectButton;
    private Button resetButton;
    private final List<ExecutionTask> reviewableTasks = new ArrayList<>();

    @Override
    public void createPartControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(3).equalWidth(true).applyTo(container);
        executionCombo = new Combo(container, SWT.READ_ONLY);
        GridDataFactory.fillDefaults().grab(true, false).span(3, 1)
                .applyTo(executionCombo);
        viewer = new TableViewer(container, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, true).span(3, 1)
                .applyTo(viewer.getTable());
        TableViewerColumn pathColumn = new TableViewerColumn(viewer, SWT.NONE);
        pathColumn.getColumn().setText("File");
        pathColumn.getColumn().setWidth(420);
        pathColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((ChangeReview.ReviewFile) element).path();
            }
        });
        TableViewerColumn decisionColumn = new TableViewerColumn(viewer, SWT.NONE);
        decisionColumn.getColumn().setText("Decision");
        decisionColumn.getColumn().setWidth(110);
        decisionColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((ChangeReview.ReviewFile) element).decision().name();
            }
        });
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        viewer.addDoubleClickListener(new IDoubleClickListener() {
            @Override
            public void doubleClick(DoubleClickEvent event) {
                openCompare();
            }
        });
        acceptButton = new Button(container, SWT.PUSH);
        acceptButton.setText("Accept");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(acceptButton);
        rejectButton = new Button(container, SWT.PUSH);
        rejectButton.setText("Reject");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(rejectButton);
        resetButton = new Button(container, SWT.PUSH);
        resetButton.setText("Reset to Pending");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(resetButton);
        acceptButton.addListener(SWT.Selection, e -> onDecision(ReviewDecision.ACCEPTED));
        rejectButton.addListener(SWT.Selection, e -> onDecision(ReviewDecision.REJECTED));
        resetButton.addListener(SWT.Selection, e -> onDecision(ReviewDecision.PENDING));
        executionCombo.addListener(SWT.Selection, e -> refreshFiles());
        refresh();
    }

    private ChangeReview currentReview() {
        int index = executionCombo.getSelectionIndex();
        if (index < 0 || index >= reviewableTasks.size()) {
            return null;
        }
        ExecutionTask task = reviewableTasks.get(index);
        ReviewService reviewService = DStudioUiPlugin.getInstance().reviewService();
        if (reviewService == null) {
            return null;
        }
        Optional<ChangeReview> review = reviewService.getReviewForExecution(task.getId());
        return review.orElse(null);
    }

    private ChangeReview.ReviewFile selectedFile() {
        IStructuredSelection selection = viewer.getStructuredSelection();
        return selection.isEmpty() ? null
                : (ChangeReview.ReviewFile) selection.getFirstElement();
    }

    private void onDecision(ReviewDecision decision) {
        ChangeReview review = currentReview();
        ChangeReview.ReviewFile file = selectedFile();
        ReviewService reviewService = DStudioUiPlugin.getInstance().reviewService();
        if (review == null || file == null || reviewService == null) {
            return;
        }
        reviewService.setFileDecision(review.getId(), file.path(), decision);
        refreshFiles();
    }

    private void openCompare() {
        ChangeReview review = currentReview();
        ChangeReview.ReviewFile file = selectedFile();
        ExecutionService executionService = DStudioUiPlugin.getInstance().executionService();
        WorkspaceDiffService diffService = DStudioUiPlugin.getInstance().diffService();
        if (review == null || file == null || executionService == null
                || diffService == null) {
            return;
        }
        Optional<ExecutionTask> task = executionService.getTask(review.getExecutionId());
        if (task.isEmpty()) {
            return;
        }
        Path workingDirectory = Path.of(task.get().getWorkingDirectory());
        Object snapshot = diffService.isGitRepository(workingDirectory)
                ? diffService.takeSnapshot(workingDirectory) : null;
        String base = snapshot == null ? ""
                : diffService.baseContent(snapshot, workingDirectory, file.path());
        String current = readCurrent(workingDirectory, file.path());
        CompareConfiguration configuration = new CompareConfiguration();
        configuration.setLeftLabel("Before execution");
        configuration.setRightLabel("After execution (" + file.path() + ")");
        CompareEditorInput input = new CompareEditorInput(configuration) {
            @Override
            protected Object prepareInput(IProgressMonitor monitor) {
                return new DiffNode(null, Differencer.CHANGE, null,
                        new TextElement(file.path() + " (before)", base),
                        new TextElement(file.path() + " (after)", current));
            }
        };
        CompareUI.openCompareEditor(input);
    }

    private String readCurrent(Path workingDirectory, String path) {
        try {
            return Files.readString(workingDirectory.resolve(path).normalize());
        } catch (Exception e) {
            return "";
        }
    }

    private record TextElement(String name, String content)
            implements ITypedElement, IStreamContentAccessor {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getType() {
            return ITypedElement.TEXT_TYPE;
        }

        @Override
        public org.eclipse.swt.graphics.Image getImage() {
            return null;
        }

        @Override
        public InputStream getContents() {
            return new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    public void refresh() {
        if (executionCombo == null || executionCombo.isDisposed()) {
            return;
        }
        ExecutionService executionService = DStudioUiPlugin.getInstance().executionService();
        ReviewService reviewService = DStudioUiPlugin.getInstance().reviewService();
        reviewableTasks.clear();
        executionCombo.removeAll();
        if (executionService != null && reviewService != null) {
            List<ExecutionTask> tasks = new ArrayList<>(executionService.listTasks());
            tasks.sort(Comparator.comparing(ExecutionTask::getCreatedAt).reversed());
            for (ExecutionTask task : tasks) {
                if (reviewService.getReviewForExecution(task.getId()).isPresent()) {
                    reviewableTasks.add(task);
                    executionCombo.add(task.getId() + " (" + task.getStatus().name()
                            + ", " + task.getHarnessId() + ")");
                }
            }
        }
        if (!reviewableTasks.isEmpty()) {
            executionCombo.select(0);
        }
        refreshFiles();
    }

    private void refreshFiles() {
        ChangeReview review = currentReview();
        if (viewer != null && !viewer.getControl().isDisposed()) {
            viewer.setInput(review == null ? List.of() : review.getFiles());
        }
    }

    @Override
    public void setFocus() {
        if (executionCombo != null) {
            executionCombo.setFocus();
        }
    }
}
