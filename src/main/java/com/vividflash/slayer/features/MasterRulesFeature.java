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

import com.vividflash.slayer.RuleMaster;
import com.vividflash.slayer.SlayerConfig;
import com.vividflash.slayer.SlayerMaster;
import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
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
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;

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
@Slf4j
@Singleton
public class MasterRulesFeature implements Feature
{
    /** Green for the milestone reminder, apart from the red task-complete lines and legible on either chatbox background. */
    private static final Color REMINDER_MESSAGE_COLOR = new Color(0x00A000);

    /** Bright red for the blocked-click message. */
    private static final Color BLOCKED_MESSAGE_COLOR = new Color(0xEF1020);

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

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
    private final Map<NPC, SlayerMaster> nearbyMastersView = Collections.unmodifiableMap(nearbyMasters);
    private int lastAnnouncedTaskNumber = -1;
    /** The master whose Assignment entry the last menu sort removed, so a hover logs once. */
    private SlayerMaster lastMenuRemovedMaster;
    /** Volatile because startUp and shutDown run off the client thread. */
    private volatile boolean running;

    /**
     * Set by every input the cached values depend on (the slayer varbits and
     * varp, a config change, a game state change); the next read rebuilds
     * them. Volatile because config changes arrive off the client thread.
     */
    private volatile boolean stale = true;
    private Recommendation recommendation;
    private int nextTaskNumber;
    private int projectedGain;
    private int currentPoints;
    private int mortimerTasksCompleted;

    /**
     * The recommended master plus whether it came from a rule (vs the default).
     * The master is null when no rule matches and the default is None.
     */
    public static class Recommendation
    {
        public final SlayerMaster master;
        public final boolean fromRule;

        Recommendation(SlayerMaster master, boolean fromRule)
        {
            this.master = master;
            this.fromRule = fromRule;
        }

        /**
         * True for a master other than the recommended one. On a rule task
         * separate-streak masters are exempt, their tasks can't cost a
         * normal-streak milestone.
         */
        public boolean isWrong(SlayerMaster other)
        {
            return master != null && other != master && !(fromRule && other.hasSeparateStreak());
        }
    }

    private static final class Rule
    {
        final boolean enabled;
        final int interval;
        final RuleMaster master;

        Rule(boolean enabled, int interval, RuleMaster master)
        {
            this.enabled = enabled;
            this.interval = interval;
            this.master = master;
        }
    }

    @Override
    public void startUp()
    {
        if (log.isDebugEnabled())
        {
            log.debug("[config] masterRules={} default={} block={} hide={} milestoneChat={} "
                    + "rule1={}@{}->{} rule2={}@{}->{} rule3={}@{}->{} rule4={}@{}->{} rule5={}@{}->{}",
                config.masterRules(), config.defaultMaster(), config.blockWrongMasters(),
                config.hideWrongMastersOnMilestone(), config.milestoneChatMessage(),
                config.rule1Enabled(), config.rule1Interval(), config.rule1Master(),
                config.rule2Enabled(), config.rule2Interval(), config.rule2Master(),
                config.rule3Enabled(), config.rule3Interval(), config.rule3Master(),
                config.rule4Enabled(), config.rule4Interval(), config.rule4Master(),
                config.rule5Enabled(), config.rule5Interval(), config.rule5Master());
        }
        running = true;
        stale = true;
        eventBus.register(this);
        overlayManager.add(masterRulesOverlay);
        overlayManager.add(masterHighlightOverlay);
        clientThread.invokeLater(this::trackSceneMasters);
    }

    @Override
    public void shutDown()
    {
        // Off the bus first, then the state is reset on the client thread,
        // where the handlers and the other features read it.
        running = false;
        eventBus.unregister(this);
        overlayManager.remove(masterRulesOverlay);
        overlayManager.remove(masterHighlightOverlay);
        clientThread.invoke(() ->
        {
            nearbyMasters.clear();
            lastAnnouncedTaskNumber = -1;
            stale = true;
        });
    }

    /**
     * Picks up masters that spawned before startUp registered the feature.
     * NPCs stay in the client after logout, so the scene is only read while
     * logged in.
     */
    private void trackSceneMasters()
    {
        if (!running || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        for (NPC npc : client.getTopLevelWorldView().npcs())
        {
            SlayerMaster master = SlayerMaster.forNpc(npc);
            if (master != null)
            {
                nearbyMasters.put(npc, master);
            }
        }
        if (log.isDebugEnabled())
        {
            log.debug("[master] scene scan, tracked={}", nearbyMasters.size());
        }
    }

    public Map<NPC, SlayerMaster> getNearbyMasters()
    {
        return nearbyMastersView;
    }

    public int getNextTaskNumber()
    {
        refreshIfStale();
        return nextTaskNumber;
    }

    public int getMortimerTasksCompleted()
    {
        refreshIfStale();
        return mortimerTasksCompleted;
    }

    public int getCurrentPoints()
    {
        refreshIfStale();
        return currentPoints;
    }

    public Recommendation getRecommendation()
    {
        refreshIfStale();
        return recommendation;
    }

    public int getProjectedGain()
    {
        refreshIfStale();
        return projectedGain;
    }

    /**
     * The flag is cleared before the inputs are read, so a change that lands
     * during the rebuild marks the result stale again.
     */
    private void refreshIfStale()
    {
        if (!stale)
        {
            return;
        }
        stale = false;
        nextTaskNumber = client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED) + 1;
        currentPoints = client.getVarbitValue(VarbitID.SLAYER_POINTS);
        mortimerTasksCompleted = client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
        recommendation = computeRecommendation(nextTaskNumber);
        projectedGain = recommendation.master == null ? 0 : recommendation.master.getPointsForTask(
            nextTaskNumber, config.eliteWesternDiary(), config.eliteKourendDiary());
    }

    private Recommendation computeRecommendation(int next)
    {
        Rule[] rules = {
            new Rule(config.rule1Enabled(), config.rule1Interval(), config.rule1Master()),
            new Rule(config.rule2Enabled(), config.rule2Interval(), config.rule2Master()),
            new Rule(config.rule3Enabled(), config.rule3Interval(), config.rule3Master()),
            new Rule(config.rule4Enabled(), config.rule4Interval(), config.rule4Master()),
            new Rule(config.rule5Enabled(), config.rule5Interval(), config.rule5Master())
        };

        SlayerMaster best = config.defaultMaster().getMaster();
        int bestInterval = 0;
        for (Rule rule : rules)
        {
            int every = interval(rule.interval);
            if (rule.enabled && next % every == 0 && every > bestInterval)
            {
                best = rule.master.getMaster();
                bestInterval = every;
            }
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

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        NPC npc = event.getNpc();
        SlayerMaster master = SlayerMaster.forNpc(npc);
        if (master != null)
        {
            nearbyMasters.put(npc, master);
            if (log.isDebugEnabled())
            {
                log.debug("[master] {} nearby, tracked={}", master, nearbyMasters.size());
            }
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
        stale = true;
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
        {
            nearbyMasters.clear();
            if (log.isDebugEnabled())
            {
                log.debug("[state] game state {}, nearby masters cleared", event.getGameState());
            }
        }
        // The varps sync again after a hop, which must not repeat the reminder;
        // a logout may be followed by another account.
        if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            lastAnnouncedTaskNumber = -1;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (event.getVarbitId() == VarbitID.SLAYER_POINTS
            || event.getVarpId() == VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED)
        {
            stale = true;
            return;
        }
        if (event.getVarbitId() != VarbitID.SLAYER_TASKS_COMPLETED)
        {
            return;
        }
        stale = true;

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
        if (log.isDebugEnabled())
        {
            log.debug("[milestone] reminder task={} master={} gain={}", next, recommendation.master, gain);
        }
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            ColorUtil.wrapWithColorTag("Task #" + next + ": use "
                + recommendation.master.getDisplayName() + " (+" + gain + " points).", REMINDER_MESSAGE_COLOR), null);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (SlayerConfig.GROUP.equals(event.getGroup()))
        {
            stale = true;
        }
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
        SlayerMaster removedMaster = null;
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
            if (master != null && recommendation.isWrong(master))
            {
                root.removeMenuEntry(entry);
                removedMaster = master;
            }
        }

        if (removedMaster != lastMenuRemovedMaster)
        {
            if (removedMaster != null && log.isDebugEnabled())
            {
                log.debug("[menu] removed Assignment on {}, recommended={} nextTask={}",
                    removedMaster, recommendation.master, getNextTaskNumber());
            }
            lastMenuRemovedMaster = removedMaster;
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
        if (clicked == null)
        {
            return;
        }

        Recommendation recommendation = getRecommendation();
        if (!recommendation.isWrong(clicked))
        {
            return;
        }

        event.consume();
        if (log.isDebugEnabled())
        {
            log.debug("[click] consumed Assignment on {}, recommended={} fromRule={} nextTask={}",
                clicked, recommendation.master, recommendation.fromRule, getNextTaskNumber());
        }
        String name = recommendation.master.getDisplayName();
        String message = recommendation.fromRule
            ? "Milestone Task: use " + name + " for +" + getProjectedGain() + " points"
            : "Wrong master: use " + name;
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            ColorUtil.wrapWithColorTag(message, BLOCKED_MESSAGE_COLOR), null);
    }
}
