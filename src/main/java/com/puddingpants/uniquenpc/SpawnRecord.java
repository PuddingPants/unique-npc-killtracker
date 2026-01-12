package com.puddingpants.uniquenpc;

import net.runelite.api.coords.WorldPoint;

public class SpawnRecord
{
    public final int npcId;
    public final WorldPoint location;
    public final long uniqueId;

    public SpawnRecord(int npcId, WorldPoint location, long uniqueId)
    {
        this.npcId = npcId;
        this.location = location;
        this.uniqueId = uniqueId;
    }
}