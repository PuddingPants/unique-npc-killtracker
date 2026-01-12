package com.puddingpants.uniquenpc;

import com.puddingpants.uniquenpc.UniqueNPCPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(UniqueNPCPlugin.class);
		RuneLite.main(args);
	}
}