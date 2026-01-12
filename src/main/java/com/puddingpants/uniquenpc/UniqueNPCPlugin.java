package com.puddingpants.uniquenpc;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import net.runelite.api.*;
import net.runelite.api.events.*;

import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.events.ConfigChanged;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
		name = "Unique NPC",
		description = "Unique NPC ID Creation and Assignment")

public class UniqueNPCPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(UniqueNPCPlugin.class);

	@Inject	private OverlayManager overlayManager;
	@Inject	private UniqueNPCOverlay overlay;
	@Inject	private UniqueNPCManager manager;
	@Inject	private UniqueNPCConfig config;
	@Inject	private Client client;
	@Inject	private ChatMessageManager chatMessageManager;
	@Inject	private SpawnStore spawnStore;
	@Inject private ConfigManager configManager;

	private final Set<Integer> npcsPreviouslyHidden = new HashSet<>();

	@Provides
	UniqueNPCConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UniqueNPCConfig.class);
	}

	// Lifecycle

	@Override
	protected void startUp()
	{
		manager.reloadConfig();
		Map<Long, SpawnRecord> loadedSpawns = spawnStore.load();
		manager.loadKnownSpawns(loadedSpawns);
		manager.resetRuntimeState();
		overlayManager.add(overlay);

		log.info(
				"NPC Identity: loaded {} known spawns",
				loadedSpawns == null ? 0 : loadedSpawns.size());
	}

	@Override
	protected void shutDown()
	{
		spawnStore.save(manager.getKnownSpawns());

		// Reset all hidden NPCs when plugin shuts down
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			npc.setDead(false);
		}

		npcsPreviouslyHidden.clear();
		manager.resetRuntimeState();
		overlayManager.remove(overlay);

		log.info("NPC Identity: saved {} known spawns", manager.getKnownSpawns().size());
	}

	// NPC Events

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		manager.handleNpcSpawn(npc);

		if (shouldHideNpc(npc))
		{
			npc.setDead(true);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{

		manager.handleNpcDespawn(event.getNpc());
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (event.getActor() instanceof NPC && event.getHitsplat().isMine())
		{
			manager.recordPlayerDamage((NPC) event.getActor());
		}
	}

	// Game tick driver

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		manager.resolvePendingSpawns();

		if (manager.consumeDirty())
		{
			spawnStore.save(manager.getKnownSpawns());
		}
		updateNpcVisibility();
	}

	private void updateNpcVisibility()
	{
		Set<Integer> currentlyHidden = new HashSet<>();

		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			int index = npc.getIndex();
			boolean shouldHide = shouldHideNpc(npc);

			if (shouldHide)
			{
				currentlyHidden.add(index);
				if (!npcsPreviouslyHidden.contains(index))
				{
					npc.setDead(true);
				}
			}
			else if (npcsPreviouslyHidden.contains(index))
			{
				npc.setDead(false);
			}
		}

		npcsPreviouslyHidden.clear();
		npcsPreviouslyHidden.addAll(currentlyHidden);
	}

	private boolean shouldHideNpc(NPC npc)
	{
		return config.disableNpcInteractions()
				&& manager.getTaggedNpcIndices().contains(npc.getIndex())
				&& !manager.isNpcAggressive(npc);
	}

	// Config

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"uniquenpc".equals(event.getGroup()))
		{
			return;
		}

		if ("resetSpawnIds".equals(event.getKey()) && config.resetSpawnIds())
		{
			log.info("Reset toggle activated - clearing all data");

			manager.getKnownSpawns().clear();
			manager.resetRuntimeState();

			spawnStore.clear();

			npcsPreviouslyHidden.clear();
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				npc.setDead(false);
			}

			configManager.setConfiguration("uniquenpc", "resetSpawnIds", false);

			log.info("Reset complete - all spawn data cleared");
			return;
		}

		manager.reloadConfig();
		npcsPreviouslyHidden.clear();
		updateNpcVisibility();
	}

	// Menu filtering

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.disableNpcInteractions())
		{
			return;
		}

		MenuEntry menuEntry = event.getMenuEntry();
		int type = event.getType();

		if (type != MenuAction.NPC_FIRST_OPTION.getId()
				&& type != MenuAction.NPC_SECOND_OPTION.getId()
				&& type != MenuAction.NPC_THIRD_OPTION.getId()
				&& type != MenuAction.NPC_FOURTH_OPTION.getId()
				&& type != MenuAction.NPC_FIFTH_OPTION.getId())
		{
			return;
		}

		if (!manager.getTaggedNpcIndices().contains(event.getIdentifier()))
		{
			return;
		}

		menuEntry.setDeprioritized(true);
	}

	// Chat Commands

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		String command = event.getCommand();
		String[] args = event.getArguments();

		if (command.equalsIgnoreCase("npccount"))
		{
			handleNpcCountCommand(args);
		}
		else if (command.equalsIgnoreCase("npcstats"))
		{
			handleNpcStatsCommand();
		}
	}

	private void handleNpcCountCommand(String[] args)
	{
		if (args.length != 1)
		{
			sendChatMessage("Usage: ::npccount <npcId>");
			return;
		}

		try
		{
			int npcId = Integer.parseInt(args[0]);
			int savedSpawns = countSpawnsForNpcId(npcId);
			int taggedNpcs = manager.countTaggedNpcsForNpcId(npcId);

			sendChatMessage("NPC ID " + npcId + ":");
			sendChatMessage("  Unique IDs assigned: " + savedSpawns);
			sendChatMessage("  Currently tagged in world: " + taggedNpcs);
		}
		catch (NumberFormatException e)
		{
			sendChatMessage("Invalid NPC ID. Usage: ::npccount <npcId>");
		}
	}

	private void handleNpcStatsCommand()
	{
		int totalSpawns = manager.getKnownSpawns().size();
		int taggedNpcs = manager.getTaggedNpcIndices().size();

		sendChatMessage("Total saved spawns: " + totalSpawns);
		sendChatMessage("Currently tagged NPCs: " + taggedNpcs);
	}

	private int countSpawnsForNpcId(int npcId)
	{
		int count = 0;
		for (SpawnRecord record : manager.getKnownSpawns().values())
		{
			if (record.npcId == npcId)
			{
				count++;
			}
		}
		return count;
	}

	private void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(
						new ChatMessageBuilder()
								.append(ChatColorType.HIGHLIGHT)
								.append("[NPC Identity] ")
								.append(ChatColorType.NORMAL)
								.append(message)
								.build()
				)
				.build());
	}
}