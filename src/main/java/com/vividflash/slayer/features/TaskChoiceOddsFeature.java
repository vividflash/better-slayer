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
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Computes each of Mortimer's offered tasks' superior unique table rate, which
 * the overlay scales to the drop the config asks for. The rate comes from the
 * game's slayer task data, multiplied by the superior-unique modifier when a
 * slot carries one. Each task's expected kill count and kills per hour are
 * resolved beside it, for the pick modes that weigh a task's time.
 * Unresolvable slots show no value.
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

    /** The one task whose entry carries no slayer level, and the level it needs. */
    private static final int WARPED_CREATURES_TASK = 122;
    private static final int WARPED_CREATURES_LEVEL = 56;

    /** Id of the modifier that boosts the unique-table roll by a percentage. */
    private static final int MODIFIER_UNIQUE_ID = 4;
    /** Id of the modifier that adds or removes a flat number of kills. */
    private static final int MODIFIER_QUANTITY_ID = 2;

    /** Mortimer's id in the master column of the master task table. */
    private static final int MORTIMER_MASTER_ID = 10;

    private static final String CONFIG_GROUP = "vividflashslayer";
    private static final String RECALCULATE_KEY = "taskChoiceRecalculate";
    private static final String BASELINE_KEY = "taskChoiceBaseline";

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
        /** Middle of the assigned amount, a quantity modifier included; NaN when unreadable. */
        public final double expectedQuantity;
        public final int killsPerHour;
        /** Whether this is one of the multicombat tasks. */
        public final boolean fastTask;

        Choice(String name, double uniqueTableChance, int uniqueModifierPercent,
            double expectedQuantity, int killsPerHour, boolean fastTask)
        {
            this.fastTask = fastTask;
            this.name = name;
            this.uniqueTableChance = uniqueTableChance;
            this.uniqueModifierPercent = uniqueModifierPercent;
            this.expectedQuantity = expectedQuantity;
            this.killsPerHour = killsPerHour;
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

    @Inject
    private ConfigManager configManager;

    private final List<Choice> choices = new ArrayList<>();

    /** The best option's name widget, once found in the open interface. */
    private Widget bestName;
    /** The color that widget had before it was marked. */
    private int bestNameColor;

    @Override
    public void startUp()
    {
        // The recalculate checkbox stands in for a button, so it never stays
        // ticked across a restart.
        configManager.setConfiguration(CONFIG_GROUP, RECALCULATE_KEY, false);
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
        configManager.setConfiguration(CONFIG_GROUP, RECALCULATE_KEY, false);
    }

    public List<Choice> getChoices()
    {
        return choices;
    }

    /** Index into {@link #getChoices()} of the configured mode's pick, or -1. */
    public int getPickIndex()
    {
        return TaskPickScorer.pick(choices, config.taskChoicePickMode(),
            client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED),
            config.taskChoiceOverhead(), baseline());
    }

    /** Hours the choice is expected to take, or NaN when its amount is unreadable. */
    public double estimatedHours(Choice choice)
    {
        return TaskPickScorer.hours(choice.expectedQuantity, choice.killsPerHour,
            config.taskChoiceOverhead());
    }

    /** The stored long-run rate, or the bundled one when none is stored. */
    private double baseline()
    {
        String stored = configManager.getConfiguration(CONFIG_GROUP, BASELINE_KEY);
        if (stored != null)
        {
            try
            {
                double value = Double.parseDouble(stored);
                if (value > 0 && !Double.isInfinite(value))
                {
                    return value;
                }
            }
            catch (NumberFormatException e)
            {
                // falls through to the bundled rate
            }
        }
        return MortimerTaskSpeed.BASELINE;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        int id = event.getVarbitId();
        for (int slot = 0; slot < CHOICE_TASK_VARBITS.length; slot++)
        {
            if (id == CHOICE_TASK_VARBITS[slot] || id == CHOICE_MODIFIER_ID_VARBITS[slot]
                || id == CHOICE_MODIFIER_VALUE_VARBITS[slot]
                || id == CHOICE_MODIFIER_NEGATIVE_VARBITS[slot])
            {
                refresh();
                return;
            }
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!CONFIG_GROUP.equals(event.getGroup()))
        {
            return;
        }
        if (RECALCULATE_KEY.equals(event.getKey()))
        {
            if (Boolean.parseBoolean(event.getNewValue()))
            {
                clientThread.invokeLater(this::recalculateBaseline);
            }
            return;
        }
        clientThread.invokeLater(this::refresh);
    }

    /**
     * Works the long-run rate out again from Mortimer's whole task list, with
     * the player's kills per hour and overhead, and stores it. Nothing is stored
     * when any row is unreadable.
     */
    private void recalculateBaseline()
    {
        try
        {
            List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerMasterTask.ID,
                DBTableID.SlayerMasterTask.COL_MASTER_ID, 0, MORTIMER_MASTER_ID);
            int n = rows.size();
            double[] weights = new double[n];
            double[] rolls = new double[n];
            double[] hours = new double[n];
            for (int i = 0; i < n; i++)
            {
                int row = rows.get(i);
                int taskRow = (Integer) client.getDBTableField(row, DBTableID.SlayerMasterTask.COL_TASK, 0)[0];
                int taskId = (Integer) client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_ID, 0)[0];
                double quantity = amount(row, taskRow);
                int kph = MortimerTaskSpeed.killsPerHour(taskId, config);
                weights[i] = (Integer) client.getDBTableField(row, DBTableID.SlayerMasterTask.COL_WEIGHT, 0)[0];
                rolls[i] = TaskPickScorer.rolls(quantity, baseUniqueChance(taskRow));
                hours[i] = TaskPickScorer.hours(quantity, kph, config.taskChoiceOverhead());
            }

            double rate = n < 3 ? Double.NaN : TaskPickScorer.baseline(weights, rolls, hours);
            if (rate > 0 && !Double.isInfinite(rate))
            {
                configManager.setConfiguration(CONFIG_GROUP, BASELINE_KEY, Double.toString(rate));
                log.debug("Task choice baseline recalculated to {}", rate);
            }
        }
        catch (RuntimeException e)
        {
            log.debug("Task choice baseline not recalculated", e);
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
        clearHighlight();
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
        int quantityDelta = modifierId != MODIFIER_QUANTITY_ID ? 0
            : modifierNegative ? -modifierValue : modifierValue;

        int taskRow = readTaskRow(taskId);
        String name = taskRow == UNREADABLE_ROW ? null : readTaskName(taskRow);
        if (name == null)
        {
            return new Choice(UNKNOWN_TASK, Double.NaN, uniquePercent, Double.NaN, 0, false);
        }

        // The odds are resolved on their own, so a task whose name is readable
        // keeps it even when the rest of its row is not.
        int kph = MortimerTaskSpeed.killsPerHour(taskId, config);
        return new Choice(name, uniqueTableChance(taskRow, uniquePercent), uniquePercent,
            expectedQuantity(taskRow, quantityDelta), kph, MortimerTaskSpeed.isFastTask(taskId));
    }

    /** Kills the task is expected to be assigned for, or NaN when unreadable. */
    private double expectedQuantity(int taskRow, int quantityDelta)
    {
        try
        {
            List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerMasterTask.ID,
                DBTableID.SlayerMasterTask.COL_TASK, 0, taskRow);
            for (int row : rows)
            {
                int master = (Integer) client.getDBTableField(row, DBTableID.SlayerMasterTask.COL_MASTER_ID, 0)[0];
                if (master == MORTIMER_MASTER_ID)
                {
                    return Math.max(1, amount(row, taskRow) + quantityDelta);
                }
            }
            return Double.NaN;
        }
        catch (RuntimeException e)
        {
            return Double.NaN;
        }
    }

    /**
     * Middle of the amount range on a master task row. A task extension the
     * player has bought replaces the range or adds to it, whichever the task
     * row lists for it.
     */
    private double amount(int masterTaskRow, int taskRow)
    {
        int min = (Integer) client.getDBTableField(masterTaskRow, DBTableID.SlayerMasterTask.COL_MIN_AMOUNT, 0)[0];
        int max = (Integer) client.getDBTableField(masterTaskRow, DBTableID.SlayerMasterTask.COL_MAX_AMOUNT, 0)[0];
        try
        {
            if (extensionUnlocked(taskRow, DBTableID.SlayerTask.COL_EXTENSION_MIN_MAX))
            {
                min = (Integer) client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_EXTENSION_MIN_MAX, 1)[0];
                max = (Integer) client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_EXTENSION_MIN_MAX, 2)[0];
            }
            else if (extensionUnlocked(taskRow, DBTableID.SlayerTask.COL_EXTENSION_ADDITIVE))
            {
                min += (Integer) client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_EXTENSION_ADDITIVE, 1)[0];
                max += (Integer) client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_EXTENSION_ADDITIVE, 2)[0];
            }
        }
        catch (RuntimeException e)
        {
            // the plain range stands
        }
        return (min + max) / 2.0;
    }

    /**
     * Whether the task lists an extension in the given column and the player
     * has it. The column leads with the extension's reward row, whose bit
     * number counts across the two reward unlock varps.
     */
    private boolean extensionUnlocked(int taskRow, int column)
    {
        Object[] unlock = client.getDBTableField(taskRow, column, 0);
        if (unlock == null || unlock.length == 0)
        {
            return false;
        }
        int bit = (Integer) client.getDBTableField((Integer) unlock[0], DBTableID.SlayerUnlock.COL_BIT, 0)[0];
        int unlocks = client.getVarpValue(bit < 32
            ? VarPlayerID.SLAYER_REWARDS_UNLOCKS : VarPlayerID.SLAYER_REWARDS_UNLOCKS1);
        return (unlocks >>> (bit % 32) & 1) == 1;
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

    /**
     * The monster's slayer level from its task entry; 1 when none is listed.
     * Warped creatures need 56 Slayer, but their entry lists no level.
     */
    private int slayerLevel(int taskRow)
    {
        Object[] id = client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_ID, 0);
        if (id != null && id.length > 0 && (Integer) id[0] == WARPED_CREATURES_TASK)
        {
            return WARPED_CREATURES_LEVEL;
        }

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
     * the row can write over it. The old color is put back only when the pick
     * moves while the interface is open, since the rows are built fresh each
     * time it opens.
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
            int best = getPickIndex();
            if (best == -1)
            {
                return;
            }
            bestName = findNameWidget(content, choices.get(best).name);
            if (bestName != null)
            {
                bestNameColor = bestName.getTextColor();
            }
        }

        if (bestName != null && bestName.getTextColor() != BEST_NAME_COLOR)
        {
            bestName.setTextColor(BEST_NAME_COLOR);
        }
    }

    private void clearHighlight()
    {
        if (bestName != null && bestName.getTextColor() == BEST_NAME_COLOR)
        {
            bestName.setTextColor(bestNameColor);
        }
        bestName = null;
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
