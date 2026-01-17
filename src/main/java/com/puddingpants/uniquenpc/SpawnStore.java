package com.puddingpants.uniquenpc;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;

@Singleton
public class SpawnStore
{
    private static final String GROUP = "uniquenpc";
    private static final String KEY = "spawnRecords";

    private static final Type TYPE =
            new TypeToken<Map<Long, SpawnRecord>>() {}.getType();

    @Inject
    private Gson gson;

    @Inject
    private ConfigManager configManager;

    public void save(Map<Long, SpawnRecord> data)
    {
        if (data == null || data.isEmpty())
        {
            configManager.unsetConfiguration(GROUP, KEY);
            return;
        }

        String json = gson.toJson(data, TYPE);
        configManager.setConfiguration(GROUP, KEY, json);
    }

    public Map<Long, SpawnRecord> load()
    {
        String json = configManager.getConfiguration(GROUP, KEY);

        if (json == null || json.isEmpty())
        {
            return Collections.emptyMap();
        }

        return gson.fromJson(json, TYPE);
    }

    public void clear()
    {
        configManager.unsetConfiguration(GROUP, KEY);
    }
}
