package eu.kalafatic.dstudio.infrastructure.persistence;

import eu.kalafatic.dstudio.core.model.ChangeReview;
import eu.kalafatic.dstudio.core.model.ReviewDecision;
import eu.kalafatic.dstudio.core.store.ReviewStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteReviewStore implements ReviewStore {

    private final Connection connection;

    public SqliteReviewStore(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void saveReview(ChangeReview review) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO reviews (id, execution_id, state) VALUES (?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET state=excluded.state")) {
            ps.setString(1, review.getId());
            ps.setString(2, review.getExecutionId());
            ps.setString(3, review.getState().name());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save review: " + e.getMessage(), e);
        }
        for (ChangeReview.ReviewFile file : review.getFiles()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO review_files (review_id, path, diff, decision) "
                    + "VALUES (?, ?, ?, ?) ON CONFLICT(review_id, path) "
                    + "DO UPDATE SET diff=excluded.diff, decision=excluded.decision")) {
                ps.setString(1, review.getId());
                ps.setString(2, file.path());
                ps.setString(3, file.diff());
                ps.setString(4, file.decision().name());
                ps.executeUpdate();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to save review file: "
                        + e.getMessage(), e);
            }
        }
    }

    @Override
    public Optional<ChangeReview> findReview(String reviewId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, execution_id, state FROM reviews WHERE id = ?")) {
            ps.setString(1, reviewId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find review: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ChangeReview> findReviewByExecution(String executionId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, execution_id, state FROM reviews WHERE execution_id = ? "
                + "ORDER BY rowid DESC LIMIT 1")) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find review by execution: "
                    + e.getMessage(), e);
        }
    }

    private ChangeReview map(ResultSet rs) throws Exception {
        String reviewId = rs.getString("id");
        String executionId = rs.getString("execution_id");
        List<ChangeReview.ReviewFile> files = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT path, diff, decision FROM review_files WHERE review_id = ? "
                + "ORDER BY path")) {
            ps.setString(1, reviewId);
            try (ResultSet fileRs = ps.executeQuery()) {
                while (fileRs.next()) {
                    files.add(new ChangeReview.ReviewFile(fileRs.getString("path"),
                            fileRs.getString("diff"),
                            ReviewDecision.valueOf(fileRs.getString("decision"))));
                }
            }
        }
        ChangeReview review = new ChangeReview(reviewId, executionId, files);
        review.updateState();
        return review;
    }

    @Override
    public void clearAll() {
        try (var statement = connection.createStatement()) {
            statement.execute("DELETE FROM review_files");
            statement.execute("DELETE FROM reviews");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clear reviews: " + e.getMessage(), e);
        }
    }
}
