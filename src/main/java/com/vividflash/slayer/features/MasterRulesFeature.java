/*
 * Copyright (c) 2026, vividflash
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.vividflash.slayer.features;

import com.vividflash.slayer.SlayerConfig;
import com.vividflash.slayer.SlayerMaster;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Recommends which slayer master to take the next task from, based on
 * "every Xth task -> master Y" rules (highest matching interval wins, else
 * the default master). Highlights nearby masters accordingly, shows a point
 * projection overlay, posts a chat reminder on milestone tasks, and can
 * optionally consume the Assignment option on the wrong masters, or on
 * milestone tasks remove it from their menus entirely.
 *
 * Task streak and points come from server-synced varbits.
 */
@Singleton
public class MasterRulesFeature implements Feature
{
    @Inject
    private Client client;

    @Inject
    private EventBus eventBus;

    @Inject
    private SlayerConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MasterRulesOverlay masterRulesOverlay;

    @Inject
    private MasterHighlightOverlay masterHighlightOverlay;

    private final Map<NPC, SlayerMaster> nearbyMasters = new LinkedHashMap<>();
    private int lastAnnouncedTaskNumber = -1;

    /** The recommended master plus whether it came from a rule (vs the default). */
    public static class Recommendation
    {
        public final SlayerMaster master;
        public final boolean fromRule;

        Recommendation(SlayerMaster master, boolean fromRule)
        {
            this.master = master;
            this.fromRule = fromRule;
        }
    }

    @Override
    public void startUp()
    {
        eventBus.register(this);
        overlayManager.add(masterRulesOverlay);
        overlayManager.add(masterHighlightOverlay);
    }

    @Override
    public void shutDown()
    {
        overlayManager.remove(masterRulesOverlay);
        overlayManager.remove(masterHighlightOverlay);
        nearbyMasters.clear();
        lastAnnouncedTaskNumber = -1;
        eventBus.unregister(this);
    }

    public Map<NPC, SlayerMaster> getNearbyMasters()
    {
        return nearbyMasters;
    }

    public int getCompletedTasks()
    {
        return client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
    }

    public int getNextTaskNumber()
    {
        return getCompletedTasks() + 1;
    }

    public int getCurrentPoints()
    {
        return client.getVarbitValue(VarbitID.SLAYER_POINTS);
    }

    public Recommendation getRecommendation()
    {
        int next = getNextTaskNumber();
        SlayerMaster best = config.defaultMaster().getMaster();
        int bestInterval = 0;

        int rule1 = interval(config.rule1Interval());
        if (config.rule1Enabled() && next % rule1 == 0 && rule1 > bestInterval)
        {
            best = config.rule1Master().getMaster();
            bestInterval = rule1;
        }
        int rule2 = interval(config.rule2Interval());
        if (config.rule2Enabled() && next % rule2 == 0 && rule2 > bestInterval)
        {
            best = config.rule2Master().getMaster();
            bestInterval = rule2;
        }
        int rule3 = interval(config.rule3Interval());
        if (config.rule3Enabled() && next % rule3 == 0 && rule3 > bestInterval)
        {
            best = config.rule3Master().getMaster();
            bestInterval = rule3;
        }
        int rule4 = interval(config.rule4Interval());
        if (config.rule4Enabled() && next % rule4 == 0 && rule4 > bestInterval)
        {
            best = config.rule4Master().getMaster();
            bestInterval = rule4;
        }
        int rule5 = interval(config.rule5Interval());
        if (config.rule5Enabled() && next % rule5 == 0 && rule5 > bestInterval)
        {
            best = config.rule5Master().getMaster();
            bestInterval = rule5;
        }

        return new Recommendation(best, bestInterval > 0);
    }

    /**
     * The spinner range binds the config panel, not a stored value, so an
     * interval is clamped before it is used as a divisor.
     */
    private static int interval(int configured)
    {
        return Math.max(1, configured);
    }

    public int getProjectedGain()
    {
        return getRecommendation().master.getPointsForTask(
            getNextTaskNumber(), config.eliteWesternDiary(), config.eliteKourendDiary());
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        NPC npc = event.getNpc();
        SlayerMaster master = SlayerMaster.forNpc(npc);
        if (master != null)
        {
            nearbyMasters.put(npc, master);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        nearbyMasters.remove(event.getNpc());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
        {
            nearbyMasters.clear();
            lastAnnouncedTaskNumber = -1;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (event.getVarbitId() != VarbitID.SLAYER_TASKS_COMPLETED)
        {
            return;
        }

        if (!config.masterRules() || !config.milestoneChatMessage())
        {
            return;
        }

        int next = event.getValue() + 1;
        if (next == lastAnnouncedTaskNumber)
        {
            return;
        }

        Recommendation recommendation = getRecommendation();
        if (!recommendation.fromRule)
        {
            return;
        }

        lastAnnouncedTaskNumber = next;
        int gain = recommendation.master.getPointsForTask(
            next, config.eliteWesternDiary(), config.eliteKourendDiary());
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[Better Slayer] Task #" + next + " matches a rule: take it from "
                + recommendation.master.getDisplayName() + " (+" + gain + " points).", null);
    }

    @Subscribe
    public void onPostMenuSort(PostMenuSort event)
    {
        if (!config.masterRules() || !config.hideWrongMastersOnMilestone() || client.isMenuOpen())
        {
            return;
        }

        Recommendation recommendation = getRecommendation();
        if (!recommendation.fromRule)
        {
            return;
        }

        Menu root = client.getMenu();
        for (MenuEntry entry : root.getMenuEntries())
        {
            if (!"Assignment".equals(entry.getOption()))
            {
                continue;
            }

            NPC npc = entry.getNpc();
            if (npc == null)
            {
                continue;
            }

            SlayerMaster master = SlayerMaster.forNpc(npc);
            if (master != null && master != recommendation.master && !master.hasSeparateStreak())
            {
                root.removeMenuEntry(entry);
            }
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!config.masterRules() || !config.blockWrongMasters())
        {
            return;
        }

        if (!"Assignment".equals(event.getMenuOption()))
        {
            return;
        }

        NPC npc = event.getMenuEntry().getNpc();
        if (npc == null)
        {
            return;
        }

        SlayerMaster clicked = SlayerMaster.forNpc(npc);
        if (clicked == null || clicked.hasSeparateStreak())
        {
            return;
        }

        Recommendation recommendation = getRecommendation();
        if (clicked == recommendation.master)
        {
            return;
        }

        event.consume();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "[Better Slayer] Wrong master: take task #" + getNextTaskNumber() + " from "
                + recommendation.master.getDisplayName() + ".", null);
    }
}
