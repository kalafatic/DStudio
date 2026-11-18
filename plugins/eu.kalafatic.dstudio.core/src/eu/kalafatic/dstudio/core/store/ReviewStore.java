package eu.kalafatic.dstudio.core.store;

import eu.kalafatic.dstudio.core.model.ChangeReview;
import java.util.Optional;

public interface ReviewStore {

    void saveReview(ChangeReview review);

    Optional<ChangeReview> findReview(String reviewId);

    Optional<ChangeReview> findReviewByExecution(String executionId);

    void clearAll();
}
