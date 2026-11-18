package eu.kalafatic.dstudio.core.store;

import eu.kalafatic.dstudio.core.agent.AgentProfile;
import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for agent profiles (Phase 9). Implemented by the SQLite store in
 * the infrastructure bundle; the default selection is persisted as part of the profile
 * state so it survives restarts deterministically.
 */
public interface AgentProfileStore {

    void saveProfile(AgentProfile profile);

    Optional<AgentProfile> findProfile(String id);

    List<AgentProfile> listProfiles();

    boolean deleteProfile(String id);

    void saveDefaultProfileId(String profileId);

    Optional<String> findDefaultProfileId();

    void clearDefaultProfileId();
}
