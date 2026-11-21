package eu.kalafatic.dstudio.inference.ui.model;

import java.time.Instant;

/** One session as shown in the session navigator. */
public record SessionInfo(String id, String title, String state,
                          Instant updatedAt) {

    public static SessionInfo of(String id, String title, String state, Instant updatedAt) {
        return new SessionInfo(id, title, state == null ? "" : state, updatedAt);
    }
}
