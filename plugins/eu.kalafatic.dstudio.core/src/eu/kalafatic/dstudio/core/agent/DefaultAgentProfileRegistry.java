package eu.kalafatic.dstudio.core.agent;

import eu.kalafatic.dstudio.core.harness.HarnessCapabilityDescriptor;
import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.inference.InferenceEngineDescriptor;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;
import eu.kalafatic.dstudio.core.model.HarnessDescriptor;
import eu.kalafatic.dstudio.core.store.AgentProfileStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default agent-profile registry over the persistent {@link AgentProfileStore}. Performs
 * capability-aware compatibility validation between profiles and concrete execution
 * environments (harness x inference engine x model) so unsupported combinations are never
 * advertised or silently executed.
 */
public class DefaultAgentProfileRegistry implements AgentProfileRegistry {

    private final AgentProfileStore store;
    private final HarnessRegistry harnessRegistry;
    private final InferenceEngineRegistry engineRegistry;

    public DefaultAgentProfileRegistry(AgentProfileStore store,
            HarnessRegistry harnessRegistry, InferenceEngineRegistry engineRegistry) {
        this.store = store;
        this.harnessRegistry = harnessRegistry;
        this.engineRegistry = engineRegistry;
    }

    @Override
    public synchronized AgentProfile createProfile(AgentProfile profile) {
        if (profile == null || profile.getId() == null || profile.getId().isBlank()) {
            throw new IllegalArgumentException("Agent profile id is required");
        }
        if (store.findProfile(profile.getId()).isPresent()) {
            throw new IllegalArgumentException("Agent profile already exists: " + profile.getId());
        }
        if (findByName(profile.getName()).isPresent()) {
            throw new IllegalArgumentException("Agent profile name already exists: " + profile.getName());
        }
        store.saveProfile(profile);
        return profile;
    }

    @Override
    public synchronized AgentProfile updateProfile(AgentProfile profile) {
        if (profile == null || profile.getId() == null || profile.getId().isBlank()) {
            throw new IllegalArgumentException("Agent profile id is required");
        }
        AgentProfile existing = store.findProfile(profile.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown agent profile: " + profile.getId()));
        Optional<AgentProfile> sameName = findByName(profile.getName());
        if (sameName.isPresent() && !sameName.get().getId().equals(profile.getId())) {
            throw new IllegalArgumentException("Agent profile name already exists: " + profile.getName());
        }
        existing.update(profile.getName(), profile.getDescription(), profile.getRole(),
                profile.getSystemInstructions(), profile.getToolPermissions(),
                profile.getRestrictions(), profile.getPreferredHarnessId(),
                profile.getPreferredProviderId(), profile.getPreferredModel(),
                profile.getExecutionParameters());
        store.saveProfile(existing);
        return existing;
    }

    @Override
    public synchronized AgentProfile cloneProfile(String sourceId, String newName) {
        AgentProfile source = store.findProfile(sourceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown agent profile: " + sourceId));
        String name = (newName == null || newName.isBlank()) ? source.getName() + " (copy)" : newName;
        String unique = name;
        int counter = 2;
        while (findByName(unique).isPresent()) {
            unique = name + " " + counter++;
        }
        AgentProfile clone = source.copyWithId("agent-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12));
        clone.update(unique, source.getDescription(), source.getRole(),
                source.getSystemInstructions(), source.getToolPermissions(),
                source.getRestrictions(), source.getPreferredHarnessId(),
                source.getPreferredProviderId(), source.getPreferredModel(),
                source.getExecutionParameters());
        store.saveProfile(clone);
        return clone;
    }

    @Override
    public synchronized boolean deleteProfile(String profileId) {
        if (!store.deleteProfile(profileId)) {
            return false;
        }
        if (store.findDefaultProfileId().filter(profileId::equals).isPresent()) {
            store.clearDefaultProfileId();
        }
        return true;
    }

    @Override
    public Optional<AgentProfile> findProfile(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return Optional.empty();
        }
        return store.findProfile(profileId);
    }

    @Override
    public List<AgentProfile> listProfiles() {
        return store.listProfiles();
    }

    @Override
    public synchronized void selectDefaultProfile(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            store.clearDefaultProfileId();
            return;
        }
        if (store.findProfile(profileId).isEmpty()) {
            throw new IllegalArgumentException("Unknown agent profile: " + profileId);
        }
        store.saveDefaultProfileId(profileId);
    }

    @Override
    public Optional<String> getDefaultProfileId() {
        return store.findDefaultProfileId();
    }

    @Override
    public Optional<AgentProfile> getDefaultProfile() {
        return store.findDefaultProfileId().flatMap(store::findProfile);
    }

    private Optional<AgentProfile> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        for (AgentProfile profile : store.listProfiles()) {
            if (name.equals(profile.getName())) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    @Override
    public CompatibilityResult checkCompatibility(String profileId, String harnessId,
            String providerId, String modelId) {
        AgentProfile profile = findProfile(profileId).orElse(null);
        if (profile == null) {
            return CompatibilityResult.incompatible("Unknown agent profile: " + profileId);
        }
        if (harnessRegistry == null || engineRegistry == null) {
            return CompatibilityResult.ok();
        }
        if (harnessId != null && !harnessId.isBlank()) {
            Optional<HarnessDescriptor> harness = harnessRegistry.findHarness(harnessId);
            if (harness.isEmpty()) {
                return CompatibilityResult.incompatible("Unknown harness: " + harnessId);
            }
            if (!harness.get().isEnabled()) {
                return CompatibilityResult.incompatible("Harness is disabled: " + harnessId);
            }
            if (!profile.getPreferredHarnessId().isBlank()
                    && !profile.getPreferredHarnessId().equals(harnessId)) {
                return CompatibilityResult.incompatible("Agent profile prefers harness '"
                        + profile.getPreferredHarnessId() + "' but '" + harnessId
                        + "' was selected");
            }
        }
        String effectiveProvider = providerId;
        if ((effectiveProvider == null || effectiveProvider.isBlank())
                && !profile.getPreferredProviderId().isBlank()) {
            effectiveProvider = profile.getPreferredProviderId();
        }
        if (effectiveProvider != null && !effectiveProvider.isBlank()) {
            Optional<InferenceEngineDescriptor> engine = engineRegistry.findEngine(effectiveProvider);
            if (engine.isEmpty()) {
                return CompatibilityResult.incompatible("Unknown provider: " + effectiveProvider);
            }
            if (!engine.get().isEnabled()) {
                return CompatibilityResult.incompatible("Provider is disabled: " + effectiveProvider);
            }
            if (!profile.getPreferredProviderId().isBlank()
                    && !profile.getPreferredProviderId().equals(effectiveProvider)) {
                return CompatibilityResult.incompatible("Agent profile prefers provider '"
                        + profile.getPreferredProviderId() + "' but '" + effectiveProvider
                        + "' was selected");
            }
            if (harnessId != null && !harnessId.isBlank()) {
                List<HarnessCapabilityDescriptor> capabilities =
                        harnessRegistry.aggregateCapabilities(harnessId);
                Optional<HarnessCapabilityDescriptor> descriptor = capabilities.stream()
                        .filter(c -> c.id().equals(harnessId)).findFirst();
                if (descriptor.isPresent() && descriptor.get().supportedProviderTypes() != null
                        && !descriptor.get().supportedProviderTypes().isEmpty()) {
                    String engineType = engine.get().getType() == null ? "" : engine.get().getType();
                    if (!descriptor.get().supportedProviderTypes().contains(engineType)) {
                        return CompatibilityResult.incompatible("Harness '" + harnessId
                                + "' does not support provider type '" + engineType + "'");
                    }
                }
            }
        }
        if (modelId != null && !modelId.isBlank() && !profile.getPreferredModel().isBlank()
                && !profile.getPreferredModel().equals(modelId)) {
            return CompatibilityResult.incompatible("Agent profile prefers model '"
                    + profile.getPreferredModel() + "' but '" + modelId + "' was selected");
        }
        return CompatibilityResult.ok();
    }
}
