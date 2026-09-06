package com.fiskerz.apolinum_arise.dream;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.fiskerz.apolinum_arise.util.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

/**
 * One data-driven dream, loaded from {@code data/apolinumarise/dreams/<id>.json}.
 *
 * <pre>
 * {
 *   "anchor": { "type": "player" },
 *   //  or   { "type": "structure", "structure": "apolinumarise:shrine",
 *   //         "searchChunkRadius": 8, "fallbackToPlayer": true },
 *   "waypoints": [
 *     { "offset": [0, 3, 0], "yaw": 0,  "pitch": 20, "durationTicks": 0,   "easing": "linear" },
 *     { "offset": [8, 6, 4], "yaw": 90, "pitch": 10, "durationTicks": 100, "easing": "ease_in_out" }
 *   ],
 *   "subtitles": [ { "atTick": 20, "durationTicks": 60, "translate": "dream.apolinumarise.test.line1" } ],
 *   "sounds":    [ { "atTick": 0, "sound": "minecraft:ambient.cave", "volume": 1.0, "pitch": 1.0 } ]
 * }
 * </pre>
 *
 * <p>Waypoint offsets are RELATIVE to the resolved anchor, so the same script plays correctly wherever the
 * player happens to sleep. The first waypoint is the start position; its durationTicks is ignored.
 */
public record DreamScript(Anchor anchor, List<Waypoint> waypoints, List<Subtitle> subtitles, List<SoundCue> sounds) {

    public enum Easing {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT;

        /** Shape a 0..1 progress value. */
        public float apply(float t) {
            float clamped = Mth.clamp(t, 0.0F, 1.0F);
            return switch (this) {
                case LINEAR -> clamped;
                case EASE_IN -> clamped * clamped;
                case EASE_OUT -> 1.0F - (1.0F - clamped) * (1.0F - clamped);
                case EASE_IN_OUT -> clamped < 0.5F
                        ? 2.0F * clamped * clamped
                        : 1.0F - 2.0F * (1.0F - clamped) * (1.0F - clamped);
            };
        }

        static Easing parse(String raw) {
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return LINEAR;
            }
        }
    }

    /** Where the offsets are measured from. Resolved fresh every time the dream plays. */
    public record Anchor(boolean structureAnchored, ResourceKey<Structure> structure, int searchChunkRadius,
                         boolean fallbackToPlayer) {
        public static Anchor player() {
            return new Anchor(false, null, 0, true);
        }

        /** Resolve to a world position, or empty when a required structure could not be found nearby. */
        public Optional<Vec3> resolve(ServerLevel level, ServerPlayer player) {
            if (!structureAnchored) {
                return Optional.of(player.position());
            }
            Optional<BlockPos> found = StructureLocator.findNearestCenter(
                    level, structure, player.blockPosition(), searchChunkRadius);
            if (found.isPresent()) {
                return Optional.of(Vec3.atBottomCenterOf(found.get()));
            }
            return fallbackToPlayer ? Optional.of(player.position()) : Optional.empty();
        }

        static Anchor fromJson(JsonObject json) {
            String type = GsonHelper.getAsString(json, "type", "player");
            if (!type.equalsIgnoreCase("structure")) {
                return player();
            }
            ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(json, "structure"));
            return new Anchor(true, ResourceKey.create(Registries.STRUCTURE, id),
                    GsonHelper.getAsInt(json, "searchChunkRadius", 8),
                    GsonHelper.getAsBoolean(json, "fallbackToPlayer", true));
        }
    }

    /** One camera stop. durationTicks is the travel time FROM the previous waypoint TO this one. */
    public record Waypoint(Vec3 offset, float yaw, float pitch, int durationTicks, Easing easing) {
        static Waypoint fromJson(JsonObject json) {
            JsonArray offset = GsonHelper.getAsJsonArray(json, "offset");
            return new Waypoint(
                    new Vec3(offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble()),
                    GsonHelper.getAsFloat(json, "yaw", 0.0F),
                    GsonHelper.getAsFloat(json, "pitch", 0.0F),
                    Math.max(0, GsonHelper.getAsInt(json, "durationTicks", 0)),
                    Easing.parse(GsonHelper.getAsString(json, "easing", "linear")));
        }
    }

    /** Text overlay shown at atTick for durationTicks. */
    public record Subtitle(int atTick, int durationTicks, Component text) {
        static Subtitle fromJson(JsonObject json) {
            Component component = json.has("translate")
                    ? Component.translatable(GsonHelper.getAsString(json, "translate"))
                    : Component.literal(GsonHelper.getAsString(json, "text", ""));
            return new Subtitle(Math.max(0, GsonHelper.getAsInt(json, "atTick", 0)),
                    Math.max(1, GsonHelper.getAsInt(json, "durationTicks", 60)), component);
        }
    }

    public record SoundCue(int atTick, ResourceLocation sound, float volume, float pitch) {
        static SoundCue fromJson(JsonObject json) {
            return new SoundCue(Math.max(0, GsonHelper.getAsInt(json, "atTick", 0)),
                    ResourceLocation.parse(GsonHelper.getAsString(json, "sound")),
                    GsonHelper.getAsFloat(json, "volume", 1.0F),
                    GsonHelper.getAsFloat(json, "pitch", 1.0F));
        }
    }

    /** Total playback length: the sum of all travel durations. */
    public int totalTicks() {
        int total = 0;
        for (int i = 1; i < waypoints.size(); i++) {
            total += waypoints.get(i).durationTicks();
        }
        return Math.max(1, total);
    }

    public static DreamScript fromJson(JsonObject json) {
        Anchor anchor = json.has("anchor")
                ? Anchor.fromJson(GsonHelper.getAsJsonObject(json, "anchor"))
                : Anchor.player();

        List<Waypoint> waypoints = new ArrayList<>();
        for (var element : GsonHelper.getAsJsonArray(json, "waypoints")) {
            waypoints.add(Waypoint.fromJson(element.getAsJsonObject()));
        }
        if (waypoints.isEmpty()) {
            throw new IllegalArgumentException("A dream needs at least one waypoint");
        }

        List<Subtitle> subtitles = new ArrayList<>();
        if (json.has("subtitles")) {
            for (var element : GsonHelper.getAsJsonArray(json, "subtitles")) {
                subtitles.add(Subtitle.fromJson(element.getAsJsonObject()));
            }
        }
        List<SoundCue> sounds = new ArrayList<>();
        if (json.has("sounds")) {
            for (var element : GsonHelper.getAsJsonArray(json, "sounds")) {
                sounds.add(SoundCue.fromJson(element.getAsJsonObject()));
            }
        }
        return new DreamScript(anchor, List.copyOf(waypoints), List.copyOf(subtitles), List.copyOf(sounds));
    }
}
