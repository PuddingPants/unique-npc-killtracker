package com.puddingpants.uniquenpc;

import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class UniqueNPCManager
{
    @Inject
    private UniqueNPCConfig config;

    // Persistent identity (saved)
    @Getter
    private final Map<Long, SpawnRecord> knownSpawns = new HashMap<>();
    private boolean dirty = false;

    // Session identity
    private final Map<Integer, Long> entityToUniqueId = new HashMap<>();
    private final Map<Long, Integer> uniqueIdToEntity = new HashMap<>();

    // Learning helpers
    private final Map<Integer, Long> playerDamagedNpc = new HashMap<>();
    private final Map<Integer, NPC> pendingSpawns = new HashMap<>();

    // Config overrides
    private final Map<Integer, Integer> npcMatchRadiusOverrides = new HashMap<>();

    private static final long PLAYER_DAMAGE_TTL_MS = 15_000;

    // -------------------------------------------------
    // Lifecycle
    // -------------------------------------------------

    public void loadKnownSpawns(Map<Long, SpawnRecord> data)
    {
        knownSpawns.clear();
        if (data != null)
        {
            knownSpawns.putAll(data);
        }
    }

    public void resetRuntimeState()
    {
        entityToUniqueId.clear();
        uniqueIdToEntity.clear();
        pendingSpawns.clear();
        playerDamagedNpc.clear();
        dirty = false;
    }

    public boolean consumeDirty()
    {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    public void reloadConfig()
    {
        npcMatchRadiusOverrides.clear();

        String raw = config.npcMatchRadiusOverrides();
        if (raw == null || raw.trim().isEmpty())
        {
            return;
        }

        for (String entry : raw.split(","))
        {
            String[] parts = entry.split("=");
            if (parts.length == 2)
            {
                try
                {
                    int npcId = Integer.parseInt(parts[0].trim());
                    int radius = Integer.parseInt(parts[1].trim());
                    if (radius > 0)
                    {
                        npcMatchRadiusOverrides.put(npcId, radius);
                    }
                }
                catch (NumberFormatException ignored) {}
            }
        }
    }

    // -------------------------------------------------
    // NPC Tracking
    // -------------------------------------------------

    public void recordPlayerDamage(NPC npc)
    {
        playerDamagedNpc.put(npc.getIndex(), System.currentTimeMillis());
    }

    private boolean wasKilledByPlayer(int entityIndex)
    {
        Long t = playerDamagedNpc.get(entityIndex);
        return t != null && System.currentTimeMillis() - t <= PLAYER_DAMAGE_TTL_MS;
    }

    public void handleNpcSpawn(NPC npc)
    {
        int index = npc.getIndex();
        if (!entityToUniqueId.containsKey(index))
        {
            pendingSpawns.put(index, npc);
        }
    }

    public void handleNpcDespawn(NPC npc)
    {
        int index = npc.getIndex();

        if (wasKilledByPlayer(index))
        {
            WorldPoint deathLoc = npc.getWorldLocation();
            WorldPoint uniqueLoc = findFreeDeathLocation(npc.getId(), deathLoc);
            long spawnKey = SpawnKeyUtil.spawnKey(npc.getId(), uniqueLoc);

            SpawnRecord record = knownSpawns.computeIfAbsent(spawnKey, k -> {
                dirty = true;
                return new SpawnRecord(npc.getId(), uniqueLoc, spawnKey);
            });

            if (!entityToUniqueId.containsKey(index) && !uniqueIdToEntity.containsKey(record.uniqueId))
            {
                entityToUniqueId.put(index, record.uniqueId);
                uniqueIdToEntity.put(record.uniqueId, index);
            }
        }
        pendingSpawns.remove(index);
        playerDamagedNpc.remove(index);
    }

    public void resolvePendingSpawns()
    {
        if (pendingSpawns.isEmpty() || knownSpawns.isEmpty())
        {
            pendingSpawns.clear();
            return;
        }

        for (NPC npc : pendingSpawns.values())
        {
            int index = npc.getIndex();
            if (entityToUniqueId.containsKey(index)) continue;

            SpawnRecord best = findBestMatch(npc);
            int radius = npcMatchRadiusOverrides.getOrDefault(npc.getId(), config.matchRadius());

            if (best != null && best.location.distanceTo(npc.getWorldLocation()) <= radius)
            {
                entityToUniqueId.put(index, best.uniqueId);
                uniqueIdToEntity.put(best.uniqueId, index);
            }
        }

        pendingSpawns.clear();
    }

    private SpawnRecord findBestMatch(NPC npc)
    {
        WorldPoint loc = npc.getWorldLocation();
        SpawnRecord best = null;
        int bestDist = Integer.MAX_VALUE;

        for (SpawnRecord record : knownSpawns.values())
        {
            if (record.npcId != npc.getId()) continue;
            if (uniqueIdToEntity.containsKey(record.uniqueId)) continue;

            int dist = record.location.distanceTo(loc);
            if (dist < bestDist)
            {
                bestDist = dist;
                best = record;
            }
        }

        return best;
    }

    private WorldPoint findFreeDeathLocation(int npcId, WorldPoint base)
    {
        for (int dx = 0; dx <= 5; dx++)
        {
            for (int dy = 0; dy <= 5; dy++)
            {
                WorldPoint candidate = base.dx(dx).dy(dy);
                long key = SpawnKeyUtil.spawnKey(npcId, candidate);
                if (!knownSpawns.containsKey(key))
                {
                    return candidate;
                }
            }
        }
        return base;
    }

    // -------------------------------------------------
    // Query API
    // -------------------------------------------------

    public Long getUniqueId(NPC npc)
    {
        return entityToUniqueId.get(npc.getIndex());
    }

    public Collection<Integer> getTaggedNpcIndices()
    {
        return entityToUniqueId.keySet();
    }

    public boolean isNpcAggressive(NPC npc)
    {
        Actor interacting = npc.getInteracting();

        return (interacting instanceof Player)
                || npc.getAnimation() != -1;
    }

    public int countTaggedNpcsForNpcId(int npcId)
    {
        int count = 0;
        for (Long uniqueId : entityToUniqueId.values())
        {
            for (SpawnRecord record : knownSpawns.values())
            {
                if (record.uniqueId == uniqueId && record.npcId == npcId)
                {
                    count++;
                    break;
                }
            }
        }
        return count;
    }
}