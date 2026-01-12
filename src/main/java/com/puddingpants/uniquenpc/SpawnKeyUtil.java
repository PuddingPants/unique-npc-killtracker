package com.puddingpants.uniquenpc;

import net.runelite.api.coords.WorldPoint;

public final class SpawnKeyUtil
{
    private SpawnKeyUtil() {}

    public static long spawnKey(int npcId, WorldPoint p)
    {
        // Normalize instance coordinates
        WorldPoint base = WorldPoint.fromRegion(
                p.getRegionID(),
                p.getRegionX(),
                p.getRegionY(),
                p.getPlane()
        );

        return (((long) npcId) << 32)
                | (((long) base.getX() & 0x7FFF) << 15)
                | ((long) base.getY() & 0x7FFF);
    }
}
