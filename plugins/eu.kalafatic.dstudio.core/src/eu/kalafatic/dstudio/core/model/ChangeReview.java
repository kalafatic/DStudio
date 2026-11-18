package eu.kalafatic.dstudio.core.model;

import java.util.ArrayList;
import java.util.List;

public class ChangeReview {

    public enum ReviewState { OPEN, DECIDED }

    public record ReviewFile(String path, String diff, ReviewDecision decision) {
    }

    private final String id;
    private final String executionId;
    private final List<ReviewFile> files;
    private volatile ReviewState state;

    public ChangeReview(String id, String executionId, List<ReviewFile> files) {
        this.id = id;
        this.executionId = executionId;
        this.files = new ArrayList<>(files);
        this.state = ReviewState.OPEN;
    }

    public String getId() { return id; }
    public String getExecutionId() { return executionId; }
    public List<ReviewFile> getFiles() { return List.copyOf(files); }
    public ReviewState getState() { return state; }

    public void setDecision(String path, ReviewDecision decision) {
        for (int i = 0; i < files.size(); i++) {
            ReviewFile entry = files.get(i);
            if (entry.path().equals(path)) {
                files.set(i, new ReviewFile(entry.path(), entry.diff(), decision));
                return;
            }
        }
    }

    public void updateState() {
        boolean allDecided = files.stream()
                .allMatch(e -> e.decision() != ReviewDecision.PENDING);
        this.state = allDecided ? ReviewState.DECIDED : ReviewState.OPEN;
    }
}
