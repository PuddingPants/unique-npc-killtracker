package com.puddingpants.uniquenpc;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("uniquenpc")
public interface UniqueNPCConfig extends Config
{
	@ConfigItem(
			keyName = "tagColor",
			name = "Tag Color",
			description = "Color used to mask hidden NPCs"
	)
	default Color tagColor()
	{
		return new Color(0, 0, 0, 255);
	}

	@ConfigItem(
			keyName = "idNpc",
			name = "Show Unique ID",
			description = "Display unique ID numbers above NPCs",
			position = 1
	)
	default boolean idNpc()
	{
		return true;
	}

	@ConfigItem(
			keyName = "tagNpc",
			name = "Tag NPCs",
			description = "Visually overlays hulls on NPCs that have unique IDs"
	)
	default boolean tagNpc()
	{
		return false;
	}

	@ConfigItem(
			keyName = "tagAggressiveNpc",
			name = "Tag Aggressive NPCs",
			description = "Visually overlays hulls on Aggressive NPCs that have unique IDs"
	)
	default boolean tagAggressiveNpc()
	{
		return false;
	}

	@ConfigItem(
			keyName = "matchRadius",
			name = "Spawn match radius",
			description = "Maximum distance to match an NPC to a known spawn"
	)
	default int matchRadius()
	{
		return 50;
	}

	@ConfigItem(
			keyName = "resetSpawnIds",
			name = "Reset Unique NPC IDs",
			description = "Toggle ON to reset all learned NPC IDs"
	)
	default boolean resetSpawnIds()
	{
		return false;
	}

	@ConfigItem(
			keyName = "disableNpcInteractions",
			name = "Disable NPC interactions",
			description = "Remove attack/interact options for NPCs with a unique ID"
	)
	default boolean disableNpcInteractions()
	{
		return false;
	}
}
