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
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Computes each of Mortimer's offered tasks' chance of an imbued heart or
 * eternal gem per superior spawned. The monster's own rate comes from the
 * game's slayer task data, multiplied by the superior-unique modifier when a
 * slot carries one. Kill counts don't factor in, since the superior spawn
 * chance per kill is constant. Unresolvable slots show no value.
 */
@Singleton
public class TaskChoiceOddsFeature implements Feature
{
    /** Mortimer's offered tasks, slots 1 to 3. */
    private static final int[] CHOICE_TASK_VARBITS = {
        VarbitID.SLAYER_CHOOSE_TASK_1,
        VarbitID.SLAYER_CHOOSE_TASK_2,
        VarbitID.SLAYER_CHOOSE_TASK_3
    };
    /** Modifier attached to each slot, given as a row id, a value and a sign. */
    private static final int[] CHOICE_MODIFIER_ID_VARBITS = {
        VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_ID,
        VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_ID,
        VarbitID.SLAYER_CHOOSE_TASK_3_MODIFIER_ID
    };
    private static final int[] CHOICE_MODIFIER_VALUE_VARBITS = {
        VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_VALUE,
        VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_VALUE,
        VarbitID.SLAYER_CHOOSE_TASK_3_MODIFIER_VALUE
    };
    private static final int[] CHOICE_MODIFIER_NEGATIVE_VARBITS = {
        VarbitID.SLAYER_CHOOSE_TASK_1_MODIFIER_NEGATIVE,
        VarbitID.SLAYER_CHOOSE_TASK_2_MODIFIER_NEGATIVE,
        VarbitID.SLAYER_CHOOSE_TASK_3_MODIFIER_NEGATIVE
    };

    /** Container on the task-choice interface where the entries are built. */
    static final int TASK_CHOICE_CONTENT = InterfaceID.SlayerTaskChoice.CONTENT;

    /** Index of the per-master assignment row's task link. */
    private static final int MASTER_TASK_TASK_COLUMN = 1;
    /** Indexes into the task row for stat requirements and display name. */
    private static final int TASK_STAT_REQ_COLUMN = 2;
    private static final int TASK_NAME_COLUMN = 10;
    /** Stat id of the Slayer skill inside the stat tuples. */
    private static final int SLAYER_STAT = 18;

    /**
     * Modifier-table row that boosts the unique-table roll by a percentage,
     * despite its superior-spawn-chance name.
     */
    private static final int MODIFIER_UNIQUE_ROW = 7206;

    private static final String UNKNOWN_TASK = "Unknown task";

    /** Stands in for a db row the cache would not give up. */
    private static final int UNREADABLE_ROW = -1;

    /** One offered task, resolved as far as the cache allowed. */
    public static class Choice
    {
        public final String name;
        /** Chance of a heart/gem per superior spawned; NaN when unresolvable. */
        public final double uniquePerSuperior;
        /** Percent boost from a superior-unique modifier, 0 if none. */
        public final int uniqueModifierPercent;

        Choice(String name, double uniquePerSuperior, int uniqueModifierPercent)
        {
            this.name = name;
            this.uniquePerSuperior = uniquePerSuperior;
            this.uniqueModifierPercent = uniqueModifierPercent;
        }
    }

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private EventBus eventBus;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private TaskChoiceOddsOverlay overlay;

    private final List<Choice> choices = new ArrayList<>();

    @Override
    public void startUp()
    {
        eventBus.register(this);
        overlayManager.add(overlay);
        clientThread.invokeLater(this::refresh);
    }

    @Override
    public void shutDown()
    {
        overlayManager.remove(overlay);
        choices.clear();
        eventBus.unregister(this);
    }

    public List<Choice> getChoices()
    {
        return choices;
    }

    /** Index into {@link #getChoices()} of the best resolvable option, or -1. */
    public int getBestIndex()
    {
        int best = -1;
        for (int i = 0; i < choices.size(); i++)
        {
            double value = choices.get(i).uniquePerSuperior;
            if (!Double.isNaN(value)
                && (best == -1 || value > choices.get(best).uniquePerSuperior))
            {
                best = i;
            }
        }
        return best;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        for (int varbit : CHOICE_TASK_VARBITS)
        {
            if (event.getVarbitId() == varbit)
            {
                refresh();
                return;
            }
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.SLAYER_TASK_CHOICE)
        {
            clientThread.invokeLater(this::refresh);
        }
    }

    private void refresh()
    {
        choices.clear();
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        for (int slot = 0; slot < CHOICE_TASK_VARBITS.length; slot++)
        {
            int taskRow = client.getVarbitValue(CHOICE_TASK_VARBITS[slot]);
            if (taskRow <= 0)
            {
                continue;
            }
            choices.add(resolveChoice(slot, taskRow));
        }
    }

    private Choice resolveChoice(int slot, int masterTaskRow)
    {
        int modifierId = client.getVarbitValue(CHOICE_MODIFIER_ID_VARBITS[slot]);
        int modifierValue = client.getVarbitValue(CHOICE_MODIFIER_VALUE_VARBITS[slot]);
        boolean modifierNegative = client.getVarbitValue(CHOICE_MODIFIER_NEGATIVE_VARBITS[slot]) != 0;
        int uniquePercent = modifierId == MODIFIER_UNIQUE_ROW && !modifierNegative ? modifierValue : 0;

        int taskRow = readTaskRow(masterTaskRow);
        String name = taskRow == UNREADABLE_ROW ? null : readTaskName(taskRow);
        if (name == null)
        {
            return new Choice(UNKNOWN_TASK, Double.NaN, uniquePercent);
        }

        // The odds are resolved on their own, so a task whose name is readable
        // keeps it even when the rest of its row is not.
        return new Choice(name, uniquePerSuperior(taskRow, uniquePercent), uniquePercent);
    }

    /**
     * Row of the task itself, linked from the master's assignment row, or
     * {@link #UNREADABLE_ROW}. Row numbers start at 1, so a zero coming back
     * from the cache is no more of a row than a failed read is.
     */
    private int readTaskRow(int masterTaskRow)
    {
        try
        {
            int row = (Integer) client.getDBTableField(masterTaskRow, MASTER_TASK_TASK_COLUMN, 0)[0];
            return row > 0 ? row : UNREADABLE_ROW;
        }
        catch (RuntimeException e)
        {
            return UNREADABLE_ROW;
        }
    }

    /** Display name on a task row, or null when the cache doesn't hand one over. */
    private String readTaskName(int taskRow)
    {
        try
        {
            String name = (String) client.getDBTableField(taskRow, TASK_NAME_COLUMN, 0)[0];
            return name == null || name.isEmpty() ? null : name;
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    /** Heart-or-gem chance per superior spawned, or NaN when the row is unreadable. */
    private double uniquePerSuperior(int taskRow, int uniquePercent)
    {
        try
        {
            // A superior gets one roll on the unique tables. The first table
            // holds an imbued heart at 1/8 and the second an eternal gem at
            // 1/8, so heart-or-gem is 1 - (7/8)^2, which is 15/64.
            return baseUniqueChance(taskRow) * (1 + uniquePercent / 100.0) * 15 / 64;
        }
        catch (RuntimeException e)
        {
            return Double.NaN;
        }
    }

    /** The monster's slayer level from its task entry; 1 when none is listed. */
    private int slayerLevel(int taskRow)
    {
        Object[] levels = client.getDBTableField(taskRow, TASK_STAT_REQ_COLUMN, 0);
        Object[] stats = client.getDBTableField(taskRow, TASK_STAT_REQ_COLUMN, 1);
        if (levels == null || stats == null)
        {
            return 1;
        }
        for (int i = 0; i < Math.min(levels.length, stats.length); i++)
        {
            if ((Integer) stats[i] == SLAYER_STAT)
            {
                return (Integer) levels[i];
            }
        }
        return 1;
    }

    /**
     * The monster's own chance per superior kill of rolling a unique drop
     * table, computed from its slayer task entry. Hydra superiors land on
     * 1/20, and a bloodveld at level 50 on 1/111.8, since 200 - 105^2/125
     * is 111.8.
     */
    private double baseUniqueChance(int taskRow)
    {
        double denominator = 200 - Math.pow(slayerLevel(taskRow) + 55, 2) / 125;
        return denominator <= 1 ? 1 : 1 / denominator;
    }
}
