package com.puddingpants.uniquenpc;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;

public class UniqueNPCOverlay extends Overlay
{
    private final Client client;
    private final UniqueNPCManager manager;
    private final UniqueNPCConfig config;

    @Inject
    public UniqueNPCOverlay(Client client, UniqueNPCManager manager, UniqueNPCConfig config)
    {
        this.client = client;
        this.manager = manager;
        this.config = config;

        setLayer(OverlayLayer.ABOVE_SCENE);
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!config.idNpc() && !config.tagNpc() && !config.tagAggressiveNpc())
        {
            return null;
        }

        for (NPC npc : client.getTopLevelWorldView().npcs())
        {
            Long uniqueId = manager.getUniqueId(npc);
            if (uniqueId == null) continue;

            boolean isAggressive = manager.isNpcAggressive(npc);

            if (config.tagNpc())
            {
                renderTag(g, npc, isAggressive);
            }

            if (config.tagAggressiveNpc() && isAggressive)
            {
                renderAggressiveTag(g, npc);
            }

            if (config.idNpc())
            {
                renderIdText(g, npc, uniqueId, isAggressive);
            }
        }

        return null;
    }

    // Tag NPC (visual mask/highlight for all tagged NPCs)

    private void renderTag(Graphics2D g, NPC npc, boolean isAggressive)
    {
        if (client.getLocalPlayer().getInteracting() == npc) return;

        Shape hull = npc.getConvexHull();
        if (hull == null) return;

        Composite oldComposite = g.getComposite();

        if (isAggressive)
        {
            g.setColor(Color.GRAY);
            g.setStroke(new BasicStroke(1));
            g.draw(hull);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g.setColor(new Color(151, 139, 139, 100));
            g.fill(hull);
        }
        else
        {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            g.setColor(config.tagColor());
            g.fill(hull);
        }

        g.setComposite(oldComposite);
    }

    // Tag only aggressive NPCs (separate highlighting)

    private void renderAggressiveTag(Graphics2D g, NPC npc)
    {
        Shape hull = npc.getConvexHull();
        if (hull == null) return;

        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(1));
        g.draw(hull);

        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g.setColor(new Color(246, 232, 232, 120));
        g.fill(hull);
        g.setComposite(oldComposite);
    }

    // Display ID number

    private void renderIdText(Graphics2D g, NPC npc, long uniqueId, boolean isAggressive)
    {
        LocalPoint lp = npc.getLocalLocation();
        if (lp == null) return;

        String displayText = isAggressive ? uniqueId + " [AGG]" : String.valueOf(uniqueId);

        Point textLocation = Perspective.getCanvasTextLocation(
                client,
                g,
                lp,
                displayText,
                npc.getLogicalHeight() + 20
        );

        if (textLocation == null) return;

        g.setColor(isAggressive ? Color.RED : config.tagColor());
        g.drawString(displayText, textLocation.getX(), textLocation.getY());
    }
}