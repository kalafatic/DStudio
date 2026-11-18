package eu.kalafatic.dstudio.core.agent;

import java.util.List;
import java.util.Optional;

/**
 * Registry of reusable agent profiles (unified multi-agent dimension). Product-local,
 * persisted through {@link eu.kalafatic.dstudio.core.store.AgentProfileStore}. Profiles
 * are data, not adapters: the registry performs CRUD, clone, default selection and
 * capability-aware compatibility checks against the selected harness / inference engine /
 * model. It never advertises unsupported combinations.
 */
public interface AgentProfileRegistry {

    record CompatibilityResult(boolean compatible, String message) {
        public static CompatibilityResult ok() {
            return new CompatibilityResult(true, "");
        }

        public static CompatibilityResult incompatible(String message) {
            return new CompatibilityResult(false, message == null ? "Incompatible" : message);
        }
    }

    /** Creates a profile; the id and name must not already exist. */
    AgentProfile createProfile(AgentProfile profile);

    /** Updates an existing profile by id. */
    AgentProfile updateProfile(AgentProfile profile);

    /** Clones a profile under a new unique name. */
    AgentProfile cloneProfile(String sourceId, String newName);

    /** Deletes a profile; the default selection is cleared when it pointed at the profile. */
    boolean deleteProfile(String profileId);

    Optional<AgentProfile> findProfile(String profileId);

    List<AgentProfile> listProfiles();

    void selectDefaultProfile(String profileId);

    Optional<String> getDefaultProfileId();

    Optional<AgentProfile> getDefaultProfile();

    /**
     * Capability-aware compatibility check between a profile and a concrete execution
     * environment. Checks (all empty preferences count as wildcards):
     * harness exists+enabled, provider (engine) exists+enabled, harness's supported provider
     * types contain the engine's type, and the preferred model is among the engine's
     * discovered models when the engine is available.
     */
    CompatibilityResult checkCompatibility(String profileId, String harnessId,
            String providerId, String modelId);
}
