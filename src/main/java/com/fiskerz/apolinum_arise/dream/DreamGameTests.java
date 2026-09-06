package com.fiskerz.apolinum_arise.dream;

import com.fiskerz.apolinum_arise.Apolinumarise;
import com.fiskerz.apolinum_arise.infection.InfectionAttachments;
import com.fiskerz.apolinum_arise.infection.InfectionData;

import com.google.gson.JsonObject;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Apolinumarise.MODID)
@PrefixGameTestTemplate(false)
public class DreamGameTests {

    private static final String SCRIPT = """
            {
              "anchor": { "type": "player" },
              "waypoints": [
                { "offset": [0, 0, 0],  "yaw": 0,  "pitch": 0, "durationTicks": 0,  "easing": "linear" },
                { "offset": [10, 0, 0], "yaw": 90, "pitch": 0, "durationTicks": 20, "easing": "linear" }
              ],
              "subtitles": [ { "atTick": 5, "durationTicks": 20, "text": "hello" } ],
              "sounds": [ { "atTick": 0, "sound": "minecraft:ambient.cave", "volume": 1.0, "pitch": 1.0 } ]
            }
            """;

    // The queue is FIFO, popped on play rather than on queueing, and survives as plain data.
    @GameTest(template = "empty_3x3", batch = "dream_queue")
    public static void queue_is_fifo_and_pops_from_the_head(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);

        DreamManager.queueDream(player, "first");
        DreamManager.queueDream(player, "second");
        DreamData data = player.getData(DreamAttachments.DREAMS);
        helper.assertValueEqual(data.queue().size(), 2, "Both dreams queued");
        helper.assertValueEqual(data.queue().get(0), "first", "FIFO order");

        data = data.withHeadPopped();
        helper.assertValueEqual(data.queue().size(), 1, "Head popped");
        helper.assertValueEqual(data.queue().get(0), "second", "Second is now the head");
        helper.succeed();
    }

    // The headline requirement: a broadcast issued while the player is HEALTHY must still reach them once
    // they LATER become INFECTED - and must never be handed out twice, even if they change back.
    @GameTest(template = "empty_3x3", batch = "dream_broadcast")
    public static void broadcast_catches_a_player_who_joins_the_category_later(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DreamBroadcasts.get(level).clearForTest();

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(DreamAttachments.DREAMS, DreamData.EMPTY);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE); // HEALTHY

        // Issue an INFECTED-only broadcast while this player is healthy.
        DreamBroadcasts.get(level).add(DreamCategory.INFECTED, "infected_reveal");
        DreamManager.deliverBroadcasts(player);
        helper.assertValueEqual(player.getData(DreamAttachments.DREAMS).queue().size(), 0,
                "A healthy player must not receive an INFECTED broadcast");

        // They become infected later - the category-change check must hand it over now.
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        DreamManager.onCategoryChanged(player);
        DreamData afterInfection = player.getData(DreamAttachments.DREAMS);
        helper.assertValueEqual(afterInfection.queue().size(), 1, "Broadcast delivered on entering the category");
        helper.assertValueEqual(afterInfection.queue().get(0), "infected_reveal", "The right dream was delivered");

        // Re-checking must not duplicate it.
        DreamManager.onCategoryChanged(player);
        helper.assertValueEqual(player.getData(DreamAttachments.DREAMS).queue().size(), 1,
                "The same broadcast must never be delivered twice");

        // Going back to healthy and infected again must still not repeat it.
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE);
        DreamManager.onCategoryChanged(player);
        player.setData(InfectionAttachments.INFECTION, InfectionData.NONE.becomeInfected());
        DreamManager.onCategoryChanged(player);
        helper.assertValueEqual(player.getData(DreamAttachments.DREAMS).queue().size(), 1,
                "Received broadcasts never repeat across category changes");

        DreamBroadcasts.get(level).clearForTest();
        helper.succeed();
    }

    // Broadcasts are permanent world state and de-duplicate on the same category+dream pair.
    @GameTest(template = "empty_3x3", batch = "dream_broadcast")
    public static void broadcasts_persist_and_deduplicate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DreamBroadcasts broadcasts = DreamBroadcasts.get(level);
        broadcasts.clearForTest();

        helper.assertTrue(broadcasts.add(DreamCategory.HEALTHY, "a"), "First add succeeds");
        helper.assertFalse(broadcasts.add(DreamCategory.HEALTHY, "a"), "Duplicate add is rejected");
        helper.assertTrue(broadcasts.add(DreamCategory.INFECTED, "a"), "Same dream, other category, is distinct");
        helper.assertValueEqual(broadcasts.entries().size(), 2, "Two distinct broadcasts");

        broadcasts.clearForTest();
        helper.succeed();
    }

    // Script parsing, total length, and waypoint interpolation with the anchor applied.
    @GameTest(template = "empty_3x3", batch = "dream_script")
    public static void script_parses_and_interpolates_against_its_anchor(GameTestHelper helper) {
        JsonObject json = GsonHelper.parse(SCRIPT);
        DreamScript script = DreamScript.fromJson(json);

        helper.assertValueEqual(script.waypoints().size(), 2, "Two waypoints");
        helper.assertValueEqual(script.subtitles().size(), 1, "One subtitle");
        helper.assertValueEqual(script.sounds().size(), 1, "One sound cue");
        helper.assertValueEqual(script.totalTicks(), 20, "Total length is the sum of travel durations");

        // Offsets are relative to the resolved anchor, so a non-zero origin shifts the whole path.
        Vec3 origin = new Vec3(100.0D, 64.0D, -50.0D);
        DreamManager.Pose start = DreamManager.poseAt(script, origin, 0);
        DreamManager.Pose middle = DreamManager.poseAt(script, origin, 10);
        DreamManager.Pose end = DreamManager.poseAt(script, origin, 20);

        helper.assertTrue(start.position().distanceTo(origin) < 0.001D, "Starts at the anchor");
        helper.assertTrue(Math.abs(middle.position().x - (origin.x + 5.0D)) < 0.001D,
                "Linear easing is halfway across at the halfway tick, got " + middle.position().x);
        helper.assertTrue(Math.abs(end.position().x - (origin.x + 10.0D)) < 0.001D,
                "Ends on the final waypoint, got " + end.position().x);
        helper.succeed();
    }

    // Phase 10c item 1: a region ticket keeps an arbitrary chunk loaded, and asking for a chunk that
    // has never existed GENERATES it on demand. Also measures the synchronous cost of that generation.
    @GameTest(template = "empty_3x3", batch = "dream_chunks", timeoutTicks = 600)
    public static void region_ticket_forces_and_generates_a_remote_chunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Far enough from the test area to be well outside any player/spawn tracking.
        ChunkPos here = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ChunkPos remote = new ChunkPos(here.x + 600, here.z + 600);

        helper.assertTrue(level.getChunkSource().getChunkNow(remote.x, remote.z) == null,
                "The remote chunk must not already be loaded");

        long start = System.currentTimeMillis();
        level.getChunkSource().addRegionTicket(DreamChunkLoader.ticketTypeForTest(), remote, 2, remote);
        LevelChunk generated = level.getChunk(remote.x, remote.z);
        long elapsedMs = System.currentTimeMillis() - start;

        helper.assertTrue(generated != null, "getChunk must generate the chunk on demand");
        helper.assertTrue(level.getChunkSource().getChunkNow(remote.x, remote.z) != null,
                "The ticket must keep it loaded afterwards");
        Apolinumarise.LOGGER.info("[DreamChunks][test] Forced + generated {} in {} ms.", remote, elapsedMs);

        // Releasing the ticket must not throw and must leave no ticket of ours behind.
        level.getChunkSource().removeRegionTicket(DreamChunkLoader.ticketTypeForTest(), remote, 2, remote);
        helper.succeed();
    }

    // Phase 11 item 1: the pre-warm phase holds the dream's chunks WITHOUT sending the client anything, so
    // generation happens during the lie-down wait instead of after the camera has moved. The distinction
    // matters: a client has one chunk window, so sending during the wait would blank the player's own
    // surroundings while they are still awake and looking at them.
    // NOTE ON THE TEST PLAYER: this builds a ServerPlayer WITHOUT putting it in the player list, which
    // GameTestHelper.makeMockServerPlayerInLevel would do. A listed mock player has no real client behind
    // its embedded channel, so the first per-tick system that reads a SYNCED attachment on it crashes the
    // server with "Payload neoforge:sync_attachments may not be sent to the client!" - which this mod does
    // every tick. An unlisted player is enough here precisely because the pre-warm phase never touches
    // player.connection: that is the property under test. The streaming half (begin/pump/follow, and the
    // client-restoring half of end) does send packets and so is out of reach headlessly - it is covered by
    // the in-game /dream peek check instead.
    @GameTest(template = "empty_3x3", batch = "dream_chunks", timeoutTicks = 600)
    public static void prewarm_holds_chunks_without_touching_the_client(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "prewarm-test"),
                ClientInformation.createDefault());
        try {
            ChunkPos here = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            ChunkPos remote = new ChunkPos(here.x + 700, here.z + 700);

            DreamChunkLoader.prewarm(player, level, remote);
            helper.assertTrue(DreamChunkLoader.heldChunkCount(player) > 0,
                    "Pre-warming must ticket the dream's chunks");
            helper.assertTrue(DreamChunkLoader.isPrewarming(player), "The session is in the pre-warm phase");
            helper.assertValueEqual(DreamChunkLoader.sentChunkCount(player), 0,
                    "A pre-warm must not send the client a single chunk");

            // Even an explicit pump stays silent while pre-warming - that is the whole guarantee, and the
            // reason the player's own surroundings survive the 30-120 second wait.
            DreamChunkLoader.pump(player);
            helper.assertValueEqual(DreamChunkLoader.sentChunkCount(player), 0,
                    "pump() during a pre-warm must still send nothing");

            // Re-warming the same area is idempotent: no ticket churn while the delay ticks down.
            int held = DreamChunkLoader.heldChunkCount(player);
            DreamChunkLoader.prewarm(player, level, remote);
            helper.assertValueEqual(DreamChunkLoader.heldChunkCount(player), held,
                    "Re-warming the same centre must not re-ticket");

            // Standing up mid-wait releases everything, leaving no client-visible trace to undo.
            DreamChunkLoader.cancelPrewarm(player);
            helper.assertValueEqual(DreamChunkLoader.heldChunkCount(player), 0, "Cancelling releases every ticket");
            helper.assertFalse(DreamChunkLoader.isActive(player), "No session survives a cancel");

            // And a pre-warm torn down by the generic path (logout, /dream stop) leaks nothing either.
            DreamChunkLoader.prewarm(player, level, remote);
            DreamChunkLoader.end(player);
            helper.assertValueEqual(DreamChunkLoader.heldChunkCount(player), 0, "Ending releases every ticket");
        } finally {
            DreamChunkLoader.end(player);
            player.discard();
        }
        helper.succeed();
    }

    // Easing curves stay inside 0..1 and hit their endpoints exactly.
    @GameTest(template = "empty_3x3", batch = "dream_script")
    public static void easing_curves_are_well_formed(GameTestHelper helper) {
        for (DreamScript.Easing easing : DreamScript.Easing.values()) {
            helper.assertTrue(Math.abs(easing.apply(0.0F)) < 0.001F, easing + " starts at 0");
            helper.assertTrue(Math.abs(easing.apply(1.0F) - 1.0F) < 0.001F, easing + " ends at 1");
            float mid = easing.apply(0.5F);
            helper.assertTrue(mid >= 0.0F && mid <= 1.0F, easing + " stays in range");
        }
        helper.succeed();
    }
}
