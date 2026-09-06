package com.fiskerz.apolinum_arise.dream;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fiskerz.apolinum_arise.Apolinumarise;

import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Makes terrain far from the dreamer's body visible to that dreamer ALONE, without moving their entity
 * (Phase 10c). Three cooperating vanilla mechanisms, all verified against the 1.21.1 sources:
 *
 * <ol>
 *   <li><b>Keeping the chunks loaded:</b> {@code ServerChunkCache.addRegionTicket(TicketType, ChunkPos,
 *       distance, value)} with our own ticket type. Distance 2 is the radius vanilla forceload uses, which
 *       brings the chunk to full ticking status. The chunk system then loads or generates it on its own
 *       worker threads.</li>
 *   <li><b>Getting them to the client:</b> {@code player.connection.chunkSender.markChunkPendingToSend} -
 *       {@code PlayerChunkSender.collectChunksToSend} only SORTS by distance from the player and gates on
 *       {@code ChunkMap.getChunkToSend}, which merely requires the chunk to be loaded. There is no
 *       view-distance filter, so a forced chunk anywhere is deliverable.</li>
 *   <li><b>Getting the client to ACCEPT them:</b> the necessary trick.
 *       {@code ClientChunkCache.replaceWithPacketData} drops anything outside its storage window
 *       ("Ignoring chunk since it's not in the view range"). That window is re-centred by
 *       {@code ClientboundSetChunkCacheCenterPacket}, so we move the dreamer's window to the dream and
 *       back again afterwards.</li>
 * </ol>
 *
 * <p>Only packets sent to the dreaming player are involved, so nothing about their body changes for
 * anyone else - other clients keep seeing them lying in the bed exactly as before.
 *
 * <p><b>Cost of the re-centre:</b> a client has exactly ONE chunk window. While it points at the dream,
 * the terrain around the dreamer's own body is out of range client-side. That is invisible during a dream
 * (the camera is elsewhere) and is repaired on cleanup, which re-centres and re-sends the body's
 * surroundings.
 *
 * <h2>Two phases: pre-warm, then stream (Phase 11 item 1)</h2>
 * Chunk loading is asynchronous, so terrain used to fill in over a second or two AFTER the camera had
 * already arrived. A session now starts in the PRE-WARM phase ({@code streaming == false}) the moment the
 * lie-down delay begins: tickets go in and the chunk system generates on its own workers during the
 * 30-120 second wait, but NOT ONE PACKET is sent. That restraint is the whole point - re-centring the
 * window while the player is still awake and looking at their own bedroom would blank their surroundings
 * for the entire wait. {@link #begin} then promotes the SAME session to streaming, which is when the
 * window moves and the (by then already generated) chunks go out in one go. Standing up mid-wait calls
 * {@link #cancelPrewarm}, which releases the tickets leaving no client-visible trace, because none was
 * ever made.
 */
public final class DreamChunkLoader {
    private DreamChunkLoader() {}

    /** Our own ticket type, so a dream can never disturb or be disturbed by /forceload. */
    private static final TicketType<ChunkPos> DREAM_TICKET =
            TicketType.create(Apolinumarise.MODID + "_dream", Comparator.comparingLong(ChunkPos::toLong));

    /** Vanilla forceload uses radius 2, which brings the chunk to full ticking status. */
    private static final int TICKET_DISTANCE = 2;
    /** Upper bound on the streamed radius; a dream is cinematic, not a second render distance. */
    private static final int MAX_RADIUS = 6;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static final class Session {
        final ServerLevel level;
        final int radius;
        final Set<ChunkPos> ticketed = new HashSet<>();
        final Set<ChunkPos> sent = new HashSet<>();
        ChunkPos center;
        /** False while pre-warming: tickets are held, but the client is neither re-centred nor sent to. */
        boolean streaming;

        Session(ServerLevel level, int radius) {
            this.level = level;
            this.radius = radius;
        }
    }

    /** Test-only accessor so a gametest can exercise the same ticket type this system uses. */
    public static TicketType<ChunkPos> ticketTypeForTest() {
        return DREAM_TICKET;
    }

    public static boolean isActive(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** True while this player holds a pre-warm session that has not been promoted to streaming yet. */
    public static boolean isPrewarming(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && !session.streaming;
    }

    /** Chunks currently held by this player's session - for leak checking. */
    public static int heldChunkCount(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? 0 : session.ticketed.size();
    }

    /** How many chunks we have actually pushed to this client. Zero for the whole pre-warm phase. */
    public static int sentChunkCount(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? 0 : session.sent.size();
    }

    /** How many held chunks have finished loading - i.e. how far along a pre-warm is. */
    public static int readyChunkCount(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return 0;
        }
        int ready = 0;
        for (ChunkPos pos : session.ticketed) {
            if (session.level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                ready++;
            }
        }
        return ready;
    }

    /**
     * Start holding the dream's chunks WITHOUT touching the client, so generation happens during the
     * lie-down delay instead of after the camera has already moved. Idempotent for the same target: a
     * repeat call with the same level and centre leaves the warm session alone.
     */
    public static void prewarm(ServerPlayer player, ServerLevel level, ChunkPos center) {
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null && existing.streaming) {
            return; // a dream is already playing; never disturb its live session
        }
        if (existing != null && existing.level == level && center.equals(existing.center)) {
            return; // already warming exactly this area
        }
        end(player);
        Session session = new Session(level, radiusFor(player));
        SESSIONS.put(player.getUUID(), session);
        recenter(player, session, center);
        Apolinumarise.LOGGER.debug("[DreamChunks] Pre-warming {} chunk(s) around {} for {} (no packets sent yet).",
                session.ticketed.size(), center, player.getGameProfile().getName());
    }

    /** Drop a pre-warm that never became a dream. A live streaming session is left strictly alone. */
    public static void cancelPrewarm(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && !session.streaming) {
            int held = session.ticketed.size();
            end(player);
            Apolinumarise.LOGGER.debug("[DreamChunks] Cancelled a pre-warm of {} chunk(s) for {} (left the bed).",
                    held, player.getGameProfile().getName());
        }
    }

    /**
     * Begin streaming the area around {@code center} to this player only. When {@link #prewarm} already
     * holds this area the chunks have finished generating by now, so the terrain appears at once;
     * otherwise this still works, it just fills in over a second or two as the chunk system catches up.
     */
    public static void begin(ServerPlayer player, ServerLevel level, ChunkPos center) {
        Session session = SESSIONS.get(player.getUUID());
        boolean warmed = session != null && !session.streaming && session.level == level;
        if (!warmed) {
            end(player); // never stack sessions, and never inherit one from another level
            session = new Session(level, radiusFor(player));
            SESSIONS.put(player.getUUID(), session);
        }
        ChunkPos warmedAt = session.center;
        session.streaming = true;
        // Re-centre unconditionally: for a fresh session this is the first placement, and for a warmed one
        // it is what finally sends ClientboundSetChunkCacheCenterPacket (suppressed during the pre-warm).
        // Clearing the centre first also covers the case where the anchor resolved slightly differently
        // now than it did when the pre-warm started - the window simply shifts and re-tickets.
        session.center = null;
        recenter(player, session, center);
        pump(player); // everything already generated goes out immediately; the rest streams in
        Apolinumarise.LOGGER.debug("[DreamChunks] Streaming r={} around {} to {} ({} ticketed, {} sent immediately, "
                        + "pre-warmed at {}).",
                session.radius, center, player.getGameProfile().getName(), session.ticketed.size(),
                session.sent.size(), warmed ? String.valueOf(warmedAt) : "no");
    }

    /** Follow the camera: no-op unless it has crossed into a different chunk. */
    public static void follow(ServerPlayer player, ChunkPos cameraChunk) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && !cameraChunk.equals(session.center)) {
            recenter(player, session, cameraChunk);
        }
    }

    /**
     * Ship out every ticketed chunk that has finished loading. Called each tick while a dream is playing,
     * so terrain fills in as it becomes ready instead of the server freezing to generate it all up front.
     * A no-op during a pre-warm - that phase deliberately sends nothing.
     */
    public static void pump(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.streaming) {
            return;
        }
        for (ChunkPos pos : session.ticketed) {
            if (session.sent.contains(pos)) {
                continue;
            }
            LevelChunk chunk = session.level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null) {
                player.connection.chunkSender.markChunkPendingToSend(chunk);
                session.sent.add(pos);
            }
        }
    }

    // Move the window: ticket what is newly in range, release what fell out, and (only once we are
    // actually streaming) tell the client where its window now lives so it stops rejecting the chunks.
    private static void recenter(ServerPlayer player, Session session, ChunkPos center) {
        Set<ChunkPos> wanted = new HashSet<>();
        for (int dx = -session.radius; dx <= session.radius; dx++) {
            for (int dz = -session.radius; dz <= session.radius; dz++) {
                wanted.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        session.center = center;
        if (session.streaming) {
            // The client must be told first, or it discards everything that follows as out of range.
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(center.x, center.z));
        }

        for (ChunkPos pos : wanted) {
            if (session.ticketed.add(pos)) {
                // Ticket ONLY - deliberately not level.getChunk(), which generates synchronously on the
                // server thread. A measured cold generate costs ~150 ms for a single chunk, so doing that
                // for a whole window would stall the server for seconds. The ticket makes the chunk system
                // load/generate it on its own workers; pump() ships each one out as it becomes ready.
                session.level.getChunkSource().addRegionTicket(DREAM_TICKET, pos, TICKET_DISTANCE, pos);
            }
        }
        // Release anything that is now far behind the camera.
        session.ticketed.removeIf(pos -> {
            if (wanted.contains(pos)) {
                return false;
            }
            release(session, pos);
            dropFromClient(player, session, pos);
            return true;
        });
    }

    /**
     * Tear the session down: drop every manually-sent chunk from the client, release every ticket, then
     * hand the client's window back to the player's real position and re-send their own surroundings.
     * Safe to call repeatedly and safe when no session exists. A session that never streamed skips the
     * client half entirely - nothing was ever sent, so there is nothing to undo.
     */
    public static void end(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        int released = session.ticketed.size();
        for (ChunkPos pos : session.ticketed) {
            release(session, pos);
        }
        for (ChunkPos pos : Set.copyOf(session.sent)) {
            dropFromClient(player, session, pos);
        }
        session.ticketed.clear();
        session.sent.clear();
        if (!session.streaming) {
            Apolinumarise.LOGGER.debug("[DreamChunks] Released {} pre-warm ticket(s) for {}; the client was never "
                    + "touched, so there is no view to restore.", released, player.getGameProfile().getName());
            return;
        }

        // Put the client's window back over the body and re-send what belongs there. The vanilla ChunkMap
        // still believes it already sent those chunks (the player never moved), so it will not do this
        // for us - without it the dreamer would wake to a hole around themselves until they walked a chunk.
        ChunkPos home = player.chunkPosition();
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(home.x, home.z));
        int radius = Math.max(2, player.server.getPlayerList().getViewDistance());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = player.serverLevel().getChunkSource().getChunkNow(home.x + dx, home.z + dz);
                if (chunk != null) {
                    player.connection.chunkSender.markChunkPendingToSend(chunk);
                }
            }
        }
        Apolinumarise.LOGGER.debug("[DreamChunks] Released {} chunk ticket(s) for {} and restored their view.",
                released, player.getGameProfile().getName());
    }

    private static int radiusFor(ServerPlayer player) {
        return Math.min(MAX_RADIUS, Math.max(2, player.server.getPlayerList().getViewDistance()));
    }

    private static void release(Session session, ChunkPos pos) {
        session.level.getChunkSource().removeRegionTicket(DREAM_TICKET, pos, TICKET_DISTANCE, pos);
    }

    private static void dropFromClient(ServerPlayer player, Session session, ChunkPos pos) {
        if (session.sent.remove(pos)) {
            player.connection.chunkSender.dropChunk(player, pos);
        }
    }
}
