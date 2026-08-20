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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Computes each of Mortimer's offered tasks' superior unique table rate, which
 * the overlay scales to the drop the config asks for. The rate comes from the
 * game's slayer task data, multiplied by the superior-unique modifier when a
 * slot carries one. Kill counts don't factor in, since the superior spawn
 * chance per kill is constant. Unresolvable slots show no value.
 */
@Slf4j
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

    /** Indexes into the task row for stat requirements and display name. */
    private static final int TASK_STAT_REQ_COLUMN = 2;
    private static final int TASK_NAME_COLUMN = 10;
    /** Stat id of the Slayer skill inside the stat tuples. */
    private static final int SLAYER_STAT = 18;

    /** Id of the modifier that boosts the unique-table roll by a percentage. */
    private static final int MODIFIER_UNIQUE_ID = 4;

    /** Color the best option's name takes in Mortimer's interface. */
    private static final int BEST_NAME_COLOR = 0x00FF00;

    private static final String UNKNOWN_TASK = "Unknown task";

    /** Stands in for a db row the cache would not give up. */
    private static final int UNREADABLE_ROW = -1;

    /** One offered task, resolved as far as the cache allowed. */
    public static class Choice
    {
        public final String name;
        /** The monster's own unique table rate for this slot; NaN when unresolvable. */
        public final double uniqueTableChance;
        /** Percent boost from a superior-unique modifier, 0 if none. */
        public final int uniqueModifierPercent;

        Choice(String name, double uniqueTableChance, int uniqueModifierPercent)
        {
            this.name = name;
            this.uniqueTableChance = uniqueTableChance;
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

    @Inject
    private SlayerConfig config;

    private final List<Choice> choices = new ArrayList<>();

    /** The best option's name widget, once found in the open interface. */
    private Widget bestName;

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
            double value = choices.get(i).uniqueTableChance;
            if (!Double.isNaN(value)
                && (best == -1 || value > choices.get(best).uniqueTableChance))
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
        bestName = null;
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        for (int slot = 0; slot < CHOICE_TASK_VARBITS.length; slot++)
        {
            int taskId = client.getVarbitValue(CHOICE_TASK_VARBITS[slot]);
            if (taskId <= 0)
            {
                continue;
            }
            Choice choice = resolveChoice(slot, taskId);
            log.debug("Task choice {}: task id {} is {}", slot + 1, taskId, choice.name);
            choices.add(choice);
        }
    }

    private Choice resolveChoice(int slot, int taskId)
    {
        int modifierId = client.getVarbitValue(CHOICE_MODIFIER_ID_VARBITS[slot]);
        int modifierValue = client.getVarbitValue(CHOICE_MODIFIER_VALUE_VARBITS[slot]);
        boolean modifierNegative = client.getVarbitValue(CHOICE_MODIFIER_NEGATIVE_VARBITS[slot]) != 0;
        int uniquePercent = modifierId == MODIFIER_UNIQUE_ID && !modifierNegative ? modifierValue : 0;

        int taskRow = readTaskRow(taskId);
        String name = taskRow == UNREADABLE_ROW ? null : readTaskName(taskRow);
        if (name == null)
        {
            return new Choice(UNKNOWN_TASK, Double.NaN, uniquePercent);
        }

        // The odds are resolved on their own, so a task whose name is readable
        // keeps it even when the rest of its row is not.
        return new Choice(name, uniqueTableChance(taskRow, uniquePercent), uniquePercent);
    }

    /**
     * Row of the task a slot names by id, or {@link #UNREADABLE_ROW}. The
     * varbit carries the id the task table indexes, not a row of its own.
     */
    private int readTaskRow(int taskId)
    {
        try
        {
            List<Integer> rows = client.getDBRowsByValue(
                DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
            return rows.isEmpty() ? UNREADABLE_ROW : rows.get(0);
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

    /** The slot's unique table rate, or NaN when the row is unreadable. */
    private double uniqueTableChance(int taskRow, int uniquePercent)
    {
        try
        {
            return baseUniqueChance(taskRow) * (1 + uniquePercent / 100.0);
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
     * The monster's own rate of rolling a unique table, computed from its
     * slayer task entry. The squared term divides down as an integer, which
     * the game's own drop rates confirm: a crushing hand at slayer 5 lands on
     * 1/172, since 60 squared over 125 truncates to 28 rather than 28.8, and
     * its imbued heart is eight times that at the 1/1376 the game drops it
     * at. A hydra at slayer 95 lands on 1/20 and its heart on 1/160.
     */
    private double baseUniqueChance(int taskRow)
    {
        int scaled = slayerLevel(taskRow) + 55;
        int denominator = 200 - scaled * scaled / 125;
        return denominator <= 1 ? 1 : 1.0 / denominator;
    }

    /**
     * Colors the best option's name inside Mortimer's interface. The rows are
     * script-built, so the name is found by its text rather than by a fixed
     * child index. That search runs once per opening and the component is kept
     * for the color to be reapplied from, since the interface's own handling of
     * the row can write over it. Nothing is restored afterwards, since the rows
     * are built fresh each time it opens.
     */
    @Subscribe
    public void onClientTick(ClientTick event)
    {
        Widget content = client.getWidget(TASK_CHOICE_CONTENT);
        if (content == null || content.isHidden())
        {
            bestName = null;
            return;
        }

        if (!config.taskChoiceOddsDisplay().showsHighlight())
        {
            return;
        }

        if (bestName == null)
        {
            int best = getBestIndex();
            if (best == -1)
            {
                return;
            }
            bestName = findNameWidget(content, choices.get(best).name);
        }

        if (bestName != null && bestName.getTextColor() != BEST_NAME_COLOR)
        {
            bestName.setTextColor(BEST_NAME_COLOR);
        }
    }

    /** The widget carrying a task's name, searched depth first, or null. */
    private Widget findNameWidget(Widget parent, String taskName)
    {
        Widget[][] groups = {
            parent.getStaticChildren(),
            parent.getDynamicChildren(),
            parent.getNestedChildren()
        };

        for (Widget[] group : groups)
        {
            if (group == null)
            {
                continue;
            }
            for (Widget child : group)
            {
                if (child == null)
                {
                    continue;
                }
                if (matchesTaskName(child.getText(), taskName))
                {
                    return child;
                }
                Widget found = findNameWidget(child, taskName);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Whether a widget's text names the given task. The interface wraps longer
     * names across two lines and can word them differently from the table, so
     * the comparison drops markup and lets either side be the longer one.
     */
    private static boolean matchesTaskName(String text, String taskName)
    {
        String shown = withoutMarkup(text);
        String task = withoutMarkup(taskName);
        return !shown.isEmpty() && !task.isEmpty()
            && (shown.startsWith(task) || task.startsWith(shown));
    }

    /** Text with markup, spacing and case stripped, for comparison. */
    private static String withoutMarkup(String text)
    {
        if (text == null)
        {
            return "";
        }
        // Tag removal leaves no space where a line break sat, so spacing is
        // dropped on both sides instead of normalized.
        return Text.standardize(text).replace(" ", "");
    }
}
