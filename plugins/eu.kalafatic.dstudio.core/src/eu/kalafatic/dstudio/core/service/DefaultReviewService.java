package eu.kalafatic.dstudio.core.service;

import eu.kalafatic.dstudio.core.model.ChangeReview;
import eu.kalafatic.dstudio.core.model.ReviewDecision;
import eu.kalafatic.dstudio.core.store.ReviewStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DefaultReviewService implements ReviewService {

    private final ReviewStore store;

    public DefaultReviewService(ReviewStore store) {
        this.store = store;
    }

    @Override
    public ChangeReview createReview(String executionId, List<String> modifiedFiles,
            Map<String, String> diffs) {
        List<ChangeReview.ReviewFile> files = new ArrayList<>();
        for (String path : modifiedFiles) {
            files.add(new ChangeReview.ReviewFile(path,
                    diffs.getOrDefault(path, ""), ReviewDecision.PENDING));
        }
        String reviewId = "review-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12);
        ChangeReview review = new ChangeReview(reviewId, executionId, files);
        store.saveReview(review);
        return review;
    }

    @Override
    public Optional<ChangeReview> getReview(String reviewId) {
        return store.findReview(reviewId);
    }

    @Override
    public Optional<ChangeReview> getReviewForExecution(String executionId) {
        return store.findReviewByExecution(executionId);
    }

    @Override
    public ChangeReview setFileDecision(String reviewId, String path,
            ReviewDecision decision) {
        ChangeReview review = store.findReview(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown review: " + reviewId));
        review.setDecision(path, decision);
        review.updateState();
        store.saveReview(review);
        return review;
    }
}
