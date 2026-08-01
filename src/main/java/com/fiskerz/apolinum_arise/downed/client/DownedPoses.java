package com.fiskerz.apolinum_arise.downed.client;

import java.io.Reader;
import java.util.Arrays;
import java.util.Optional;

import com.google.gson.JsonObject;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

/**
 * Client-side registry of the 3 downed poses, loaded from resource files and mutable live via the tuning
 * command. {@code loaded} holds the file values (refreshed on resource reload); {@code overrides} holds
 * in-session live edits that take precedence until reloaded. A preview variant lets the tester see a pose
 * on their own model without actually being downed.
 */
public final class DownedPoses {
    private DownedPoses() {}

    public static final int COUNT = 3;

    private static final DownedPose[] LOADED = new DownedPose[COUNT];
    private static final DownedPose[] OVERRIDES = new DownedPose[COUNT];
    private static int previewVariant = -1; // -1 = not previewing

    /** Reload from files (called by the resource reload listener and the /downedpose reload command). */
    public static void reload(ResourceManager resourceManager) {
        for (int variant = 0; variant < COUNT; variant++) {
            LOADED[variant] = loadOne(resourceManager, variant);
        }
        Arrays.fill(OVERRIDES, null); // discard live edits on a file reload
    }

    /** Effective pose for a variant: live override if present, else the file value, else a safe default. */
    public static DownedPose get(int variant) {
        variant = Mth.clamp(variant, 0, COUNT - 1);
        if (OVERRIDES[variant] != null) {
            return OVERRIDES[variant];
        }
        if (LOADED[variant] != null) {
            return LOADED[variant];
        }
        return defaultPose(variant);
    }

    /** Editable live override for a variant (seeded from the current effective pose on first edit). */
    public static DownedPose editable(int variant) {
        variant = Mth.clamp(variant, 0, COUNT - 1);
        if (OVERRIDES[variant] == null) {
            OVERRIDES[variant] = get(variant).copy();
        }
        return OVERRIDES[variant];
    }

    /** Discard the live override for one variant, reverting to the file (or default) value. */
    public static void clearOverride(int variant) {
        variant = Mth.clamp(variant, 0, COUNT - 1);
        OVERRIDES[variant] = null;
    }

    public static int previewVariant() {
        return previewVariant;
    }

    public static void setPreviewVariant(int variant) {
        previewVariant = (variant < 0 || variant >= COUNT) ? -1 : variant;
    }

    private static DownedPose loadOne(ResourceManager resourceManager, int variant) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                Apolinumarise.MODID, "downed_poses/pose_" + variant + ".json");
        Optional<Resource> resource = resourceManager.getResource(location);
        if (resource.isPresent()) {
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                return DownedPose.fromJson(json);
            } catch (Exception exception) {
                Apolinumarise.LOGGER.warn("Failed to load downed pose {}: {}", location, exception.toString());
            }
        }
        return defaultPose(variant);
    }

    // Safe fallback if a file is missing/broken: just lay the model flat on its back.
    private static DownedPose defaultPose(int variant) {
        DownedPose pose = new DownedPose();
        pose.rootRotation[0] = -90.0F;
        pose.rootOffset[2] = -0.9F;
        return pose;
    }
}
