package eu.kalafatic.dstudio.core.service;

import eu.kalafatic.dstudio.core.model.ChangeReview;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ReviewService {

    ChangeReview createReview(String executionId, List<String> modifiedFiles,
            Map<String, String> diffs);

    Optional<ChangeReview> getReview(String reviewId);

    Optional<ChangeReview> getReviewForExecution(String executionId);

    ChangeReview setFileDecision(String reviewId, String path,
            eu.kalafatic.dstudio.core.model.ReviewDecision decision);
}
