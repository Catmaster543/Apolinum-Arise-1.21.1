package com.fiskerz.apolinum_arise.dream;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.config.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative dream playback engine and queue (Phase 10b).
 *
 * <h2>Camera attachment</h2>
 * A dream attaches the player's view to a {@link DreamCameraEntity} by sending vanilla's
 * {@link ClientboundSetCameraPacket} - the exact packet spectator mode uses - DIRECTLY, rather than calling
 * {@code ServerPlayer.setCamera}. That is deliberate and load-bearing: {@code setCamera} teleports the
 * player to the camera and its {@code teleportTo} explicitly calls {@code stopSleepInBed}, and
 * {@code ServerPlayer.tick} then drags the body to the camera every tick. Any of those would take the
 * dreamer out of bed, so they would stop counting for vanilla's night skip. Sending the packet alone
 * attaches the view and leaves the body asleep in the bed, which is exactly what this phase requires.
 * See the Phase 10b notes for the trade-off this implies for far-away anchors.
 */
public final class DreamManager {
    private DreamManager() {}

    private static final int TICKS_PER_SECOND = 20;
    private static final String ACTIONBAR_KEY = "message.apolinumarise.dream.incoming";

    // Transient per-player runtime; never persisted (a pending delay should not survive a relog).
    private static final Map<UUID, Playback> PLAYING = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PENDING_DELAY = new ConcurrentHashMap<>();

    private static final class Playback {
        final DreamScript script;
        final DreamCameraEntity camera;
        final Vec3 origin;
        final int totalTicks;
        int tick;

        Playback(DreamScript script, DreamCameraEntity camera, Vec3 origin) {
            this.script = script;
            this.camera = camera;
            this.origin = origin;
            this.totalTicks = script.totalTicks();
        }
    }

    // ---------------------------------------------------------------- queue API

    /** Queue one dream for one player. This is what the FTB Quests "Play Dream" reward calls. */
    public static void queueDream(Player player, String dreamId) {
        if (dreamId == null || dreamId.isBlank()) {
            return;
        }
        DreamData data = player.getData(DreamAttachments.DREAMS);
        player.setData(DreamAttachments.DREAMS, data.withQueued(dreamId));
        Apolinumarise.LOGGER.debug("[Dream] Queued '{}' for {} (queue size now {}).",
                dreamId, player.getGameProfile().getName(), data.queue().size() + 1);
    }

    /**
     * Register a PERMANENT, non-expiring broadcast: every player who is ever in {@code category} from now
     * on - including players who join later or only enter the category later - receives {@code dreamId}
     * exactly once. Players currently in the category get it immediately.
     */
    public static void queueDreamForCategory(ServerLevel level, DreamCategory category, String dreamId) {
        if (dreamId == null || dreamId.isBlank()) {
            return;
        }
        DreamBroadcasts.get(level).add(category, dreamId);
        // Anyone already in the category collects it right away; everyone else is caught by their next
        // category-change or login check.
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            deliverBroadcasts(player);
        }
    }

    /**
     * Hand this player every broadcast that applies to their CURRENT category and that they have not
     * received before. Called at every category-change point: login, infection completion, and cure.
     */
    public static void deliverBroadcasts(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        DreamCategory category = DreamCategory.of(player);
        DreamData data = player.getData(DreamAttachments.DREAMS);
        boolean changed = false;

        for (DreamBroadcasts.Entry entry : DreamBroadcasts.get(level).entries()) {
            if (entry.category() != category || data.hasReceived(entry.key())) {
                continue;
            }
            data = data.withQueued(entry.dreamId()).withReceived(entry.key());
            changed = true;
            Apolinumarise.LOGGER.debug("[Dream] Delivered broadcast {} to {} (now {}).",
                    entry.key(), player.getGameProfile().getName(), category);
        }
        if (changed) {
            player.setData(DreamAttachments.DREAMS, data);
        }
    }

    /** Category-change entry point (infection completion / cure). */
    public static void onCategoryChanged(Player player) {
        deliverBroadcasts(player);
    }

    public static void onLogin(ServerPlayer player) {
        deliverBroadcasts(player);
    }

    public static void onLogout(ServerPlayer player) {
        stop(player, false);
        // stop() returns early when there is no playback, so release any chunk session (e.g. a
        // /dream peek left open) unconditionally - otherwise its tickets would outlive the player.
        DreamChunkLoader.end(player);
        PENDING_DELAY.remove(player.getUUID());
    }

    public static boolean isDreaming(ServerPlayer player) {
        return PLAYING.containsKey(player.getUUID());
    }

    // ---------------------------------------------------------------- tick

    public static void onServerTick(MinecraftServer server) {
        if (!Config.ENABLE_MOD.get()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Playback playback = PLAYING.get(uuid);

        if (playback != null) {
            // A dream only ends early if the player somehow stopped sleeping (e.g. the bed was destroyed).
            if (!player.isSleeping()) {
                stop(player, true);
                return;
            }
            advance(player, playback);
            return;
        }

        if (!player.isSleeping()) {
            // Getting up cancels a pending attempt WITHOUT touching the queue - and drops the chunks that
            // were being warmed for the dream that is now not going to happen.
            clearPending(player);
            return;
        }
        if (!player.getData(DreamAttachments.DREAMS).hasQueued()) {
            clearPending(player);
            return;
        }
        Integer remaining = PENDING_DELAY.get(uuid);
        if (remaining == null) {
            PENDING_DELAY.put(uuid, randomDelayTicks(player));
            // Phase 11 item 1: start loading the dream's terrain NOW, at the head of the 30-120 second
            // wait, so the ~1-2 s of pop-in happens while the player is still lying there with nothing to
            // see. Tickets only - the pre-warm sends the client nothing, so their own surroundings stay put.
            prewarmNextDream(player);
            return;
        }
        if (remaining > 1) {
            PENDING_DELAY.put(uuid, remaining - 1);
            return;
        }
        PENDING_DELAY.remove(uuid);
        start(player);
    }

    /** Forget a pending attempt and release anything that was being pre-warmed for it. */
    private static void clearPending(ServerPlayer player) {
        if (PENDING_DELAY.remove(player.getUUID()) != null) {
            DreamChunkLoader.cancelPrewarm(player);
        }
    }

    /**
     * Resolve where the NEXT queued dream will open and start loading that area, without popping the queue
     * and without sending the client anything. Best-effort: an unknown script or an anchor that cannot be
     * resolved yet simply means no pre-warm, and {@link #start} handles it exactly as it always did.
     */
    private static void prewarmNextDream(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        DreamData data = player.getData(DreamAttachments.DREAMS);
        if (!data.hasQueued()) {
            return;
        }
        startPosOf(player, level, data.queue().get(0)).ifPresent(startPos ->
                DreamChunkLoader.prewarm(player, level, new ChunkPos(BlockPos.containing(startPos))));
    }

    /** Where a dream's camera will sit on its first tick: the resolved anchor plus the first waypoint. */
    private static Optional<Vec3> startPosOf(ServerPlayer player, ServerLevel level, String dreamId) {
        return DreamScripts.INSTANCE.get(dreamId)
                .flatMap(script -> script.anchor().resolve(level, player)
                        .map(origin -> origin.add(script.waypoints().get(0).offset())));
    }

    private static int randomDelayTicks(ServerPlayer player) {
        int min = Config.DREAM_INITIAL_DELAY_MIN_SECONDS.get();
        int max = Math.max(min, Config.DREAM_INITIAL_DELAY_MAX_SECONDS.get());
        int seconds = min == max ? min : min + player.getRandom().nextInt(max - min + 1);
        return Math.max(1, seconds * TICKS_PER_SECOND);
    }

    // ---------------------------------------------------------------- playback

    private static void start(ServerPlayer player) {
        DreamData data = player.getData(DreamAttachments.DREAMS);
        if (!data.hasQueued() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Popped on PLAY, not on queueing.
        String dreamId = data.queue().get(0);
        player.setData(DreamAttachments.DREAMS, data.withHeadPopped());

        Optional<DreamScript> script = DreamScripts.INSTANCE.get(dreamId);
        if (script.isEmpty()) {
            Apolinumarise.LOGGER.warn("[Dream] '{}' is queued for {} but no such script is loaded - skipped.",
                    dreamId, player.getGameProfile().getName());
            return;
        }
        Optional<Vec3> origin = script.get().anchor().resolve(level, player);
        if (origin.isEmpty()) {
            Apolinumarise.LOGGER.warn("[Dream] '{}' could not resolve its anchor for {} - skipped.",
                    dreamId, player.getGameProfile().getName());
            return;
        }

        // Announced immediately before the dream starts.
        player.displayClientMessage(Component.translatable(ACTIONBAR_KEY), true);

        Vec3 startPos = origin.get().add(script.get().waypoints().get(0).offset());
        DreamCameraEntity camera = new DreamCameraEntity(DreamRegistry.DREAM_CAMERA.get(), level);
        camera.moveTo(startPos.x, startPos.y, startPos.z,
                script.get().waypoints().get(0).yaw(), script.get().waypoints().get(0).pitch());
        level.addFreshEntity(camera);

        // The vanilla camera-attachment packet, sent directly - see the class javadoc for why not setCamera.
        // Phase 10c: force-load and stream the dream terrain to this player before the view moves there,
        // so the camera never looks at unloaded void.
        DreamChunkLoader.begin(player, level, new ChunkPos(
                BlockPos.containing(startPos)));
        player.connection.send(new ClientboundSetCameraPacket(camera));

        PLAYING.put(player.getUUID(), new Playback(script.get(), camera, origin.get()));
        Apolinumarise.LOGGER.info("[Dream] Playing '{}' for {} ({} ticks, anchor {}).",
                dreamId, player.getGameProfile().getName(), script.get().totalTicks(), origin.get());
    }

    private static void advance(ServerPlayer player, Playback playback) {
        DreamScript script = playback.script;
        int tick = playback.tick;

        // Camera position/rotation for this tick.
        Pose pose = poseAt(script, playback.origin, tick);
        playback.camera.moveTo(pose.position.x, pose.position.y, pose.position.z, pose.yaw, pose.pitch);
        // Keep the streamed window with the camera as it travels.
        DreamChunkLoader.follow(player, new ChunkPos(
                BlockPos.containing(pose.position)));
        // Ship out whatever finished generating since last tick (loading is asynchronous by design).
        DreamChunkLoader.pump(player);

        for (DreamScript.Subtitle subtitle : script.subtitles()) {
            if (subtitle.atTick() == tick) {
                showSubtitle(player, subtitle);
            }
        }
        for (DreamScript.SoundCue cue : script.sounds()) {
            if (cue.atTick() == tick) {
                playCue(player, pose.position, cue);
            }
        }

        playback.tick++;
        if (playback.tick > playback.totalTicks) {
            stop(player, true);
        }
    }

    record Pose(Vec3 position, float yaw, float pitch) {}

    /** Interpolate the waypoint path at {@code tick}, applying each segment's easing. */
    static Pose poseAt(DreamScript script, Vec3 origin, int tick) {
        List<DreamScript.Waypoint> waypoints = script.waypoints();
        if (waypoints.size() == 1) {
            DreamScript.Waypoint only = waypoints.get(0);
            return new Pose(origin.add(only.offset()), only.yaw(), only.pitch());
        }
        int elapsed = 0;
        for (int i = 1; i < waypoints.size(); i++) {
            DreamScript.Waypoint from = waypoints.get(i - 1);
            DreamScript.Waypoint to = waypoints.get(i);
            int duration = to.durationTicks();
            boolean last = i == waypoints.size() - 1;
            if (tick <= elapsed + duration || last) {
                float raw = duration <= 0 ? 1.0F : (tick - elapsed) / (float) duration;
                float eased = to.easing().apply(raw);
                Vec3 position = origin.add(lerp(from.offset(), to.offset(), eased));
                float yaw = Mth.rotLerp(eased, from.yaw(), to.yaw());
                float pitch = Mth.lerp(eased, from.pitch(), to.pitch());
                return new Pose(position, yaw, pitch);
            }
            elapsed += duration;
        }
        DreamScript.Waypoint last = waypoints.get(waypoints.size() - 1);
        return new Pose(origin.add(last.offset()), last.yaw(), last.pitch());
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, float t) {
        return new Vec3(Mth.lerp(t, from.x, to.x), Mth.lerp(t, from.y, to.y), Mth.lerp(t, from.z, to.z));
    }

    private static void showSubtitle(ServerPlayer player, DreamScript.Subtitle subtitle) {
        // Vanilla title system: an empty title with a subtitle renders as a centered caption.
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, subtitle.durationTicks(), 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle.text()));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
    }

    private static void playCue(ServerPlayer player, Vec3 at, DreamScript.SoundCue cue) {
        ResourceLocation id = cue.sound();
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(id);
        if (event == null) {
            Apolinumarise.LOGGER.warn("[Dream] Unknown sound '{}' in a dream script.", id);
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, at.x, at.y, at.z, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event),
                    SoundSource.AMBIENT, cue.volume(), cue.pitch());
        }
    }

    /**
     * End the current dream: detach the view, remove the camera, and decide whether the next queued dream
     * chains immediately, waits another delay, or the sequence simply stops.
     */
    public static void stop(ServerPlayer player, boolean considerChaining) {
        Playback playback = PLAYING.remove(player.getUUID());
        if (playback == null) {
            return;
        }
        // Hand the view back to the player's own body.
        player.connection.send(new ClientboundSetCameraPacket(player));
        // Release tickets, forget the streamed chunks, and restore this client to its own surroundings.
        // Runs on BOTH the natural end and every interruption, since every path funnels through stop().
        DreamChunkLoader.end(player);
        playback.camera.discard();

        if (!considerChaining || !player.isSleeping()
                || !player.getData(DreamAttachments.DREAMS).hasQueued()) {
            // Getting up between dreams stops the sequence; anything still queued stays queued.
            PENDING_DELAY.remove(player.getUUID());
            return;
        }
        if (player.getRandom().nextDouble() < Config.DREAM_CHAIN_IMMEDIATE_CHANCE.get()) {
            start(player); // chains straight into the next one
        } else {
            // Another wait, so pre-warm the next one through it exactly as the first wait did.
            PENDING_DELAY.put(player.getUUID(), randomDelayTicks(player));
            prewarmNextDream(player);
        }
    }

    /** Test/debug helper: how many dreams are waiting for this player. */
    public static int queuedCount(ServerPlayer player) {
        return player.getData(DreamAttachments.DREAMS).queue().size();
    }

    /** Test/debug helper: force-start the next queued dream now, skipping the delay. */
    public static boolean startNow(ServerPlayer player) {
        if (isDreaming(player) || !player.getData(DreamAttachments.DREAMS).hasQueued()) {
            return false;
        }
        PENDING_DELAY.remove(player.getUUID());
        start(player);
        return isDreaming(player);
    }

    /** Transient state only - safe to call on world unload. */
    public static void clearRuntime() {
        PLAYING.clear();
        PENDING_DELAY.clear();
    }

    static Map<UUID, Playback> playingForTest() {
        return new HashMap<>(PLAYING);
    }
}
