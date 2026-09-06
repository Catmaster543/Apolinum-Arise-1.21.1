package com.fiskerz.apolinum_arise.dream;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;

/**
 * Server-side registry of dream scripts, loaded from {@code data/apolinumarise/dreams/*.json} on every
 * datapack reload. Scripts are addressed by their file name without the extension - that string is the
 * {@code dreamId} used everywhere else (queueing, the FTB Quests reward, the debug command).
 */
public final class DreamScripts implements ResourceManagerReloadListener {
    public static final DreamScripts INSTANCE = new DreamScripts();

    private static final String DIRECTORY = "dreams";
    private static final String EXTENSION = ".json";

    private Map<String, DreamScript> scripts = Map.of();

    private DreamScripts() {}

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Map<String, DreamScript> loaded = new HashMap<>();
        Map<ResourceLocation, Resource> found = resourceManager.listResources(DIRECTORY,
                location -> location.getNamespace().equals(Apolinumarise.MODID) && location.getPath().endsWith(EXTENSION));

        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1, path.length() - EXTENSION.length());
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                loaded.put(id, DreamScript.fromJson(json));
            } catch (Exception exception) {
                Apolinumarise.LOGGER.error("[Dream] Failed to load dream script {}: {}", entry.getKey(), exception.toString());
            }
        }
        scripts = Map.copyOf(loaded);
        Apolinumarise.LOGGER.info("[Dream] Loaded {} dream script(s): {}", scripts.size(), scripts.keySet());
    }

    public Optional<DreamScript> get(String dreamId) {
        return Optional.ofNullable(scripts.get(dreamId));
    }

    public boolean exists(String dreamId) {
        return scripts.containsKey(dreamId);
    }

    public Set<String> ids() {
        return scripts.keySet();
    }
}
