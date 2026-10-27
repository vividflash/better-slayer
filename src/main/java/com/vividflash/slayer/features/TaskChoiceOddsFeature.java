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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
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

// Computes each of Mortimer's offered tasks' superior unique table rate, which the overlay scales
// to the drop the config asks for. The rate comes from the game's slayer task data, multiplied by
// the superior-unique modifier when a slot carries one. Each task's expected kill count and kills
// per hour are resolved beside it, for the pick modes that weigh a task's time. Unresolvable slots
// show no value.
@Slf4j
@Singleton
public class TaskChoiceOddsFeature implements Feature
{
    // Mortimer's offered tasks, slots 1 to 3.
    private static final int[] CHOICE_TASK_VARBITS = {
        VarbitID.SLAYER_CHOOSE_TASK_1,
        VarbitID.SLAYER_CHOOSE_TASK_2,
        VarbitID.SLAYER_CHOOSE_TASK_3
    };
    // Modifier attached to each slot, given as a row id, a value and a sign.
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

    // Container on the task-choice interface where the entries are built.
    private static final int TASK_CHOICE_CONTENT = InterfaceID.SlayerTaskChoice.CONTENT;

    // Indexes into the task row for stat requirements and display name.
    private static final int TASK_STAT_REQ_COLUMN = 2;
    private static final int TASK_NAME_COLUMN = 10;
    // Stat id of the Slayer skill inside the stat tuples.
    private static final int SLAYER_STAT = 18;

    // The one task whose entry carries no slayer level, and the level it needs.
    private static final int WARPED_CREATURES_TASK = 122;
    private static final int WARPED_CREATURES_LEVEL = 56;

    // Id of the modifier that boosts the unique-table roll by a percentage.
    private static final int MODIFIER_UNIQUE_ID = 4;
    // Id of the modifier that adds or removes a flat number of kills.
    private static final int MODIFIER_QUANTITY_ID = 2;

    // Mortimer's id in the master column of the master task table.
    private static final int MORTIMER_MASTER_ID = 10;

    private static final String RECALCULATE_KEY = "taskChoiceRecalculate";
    private static final String BASELINE_KEY = "taskChoiceBaseline";

    // Color the best option's name takes in Mortimer's interface.
    private static final int BEST_NAME_COLOR = 0x00FF00;

    private static final String UNKNOWN_TASK = "Unknown task";

    // Stands in for a db row the cache would not give up.
    private static final int UNREADABLE_ROW = -1;

    // How long after his window was last open a new task still counts as taken from it.
    private static final Duration TAKEN_WINDOW = Duration.ofSeconds(10);
    // Tiles from Mortimer past which the player has left him.
    private static final int LEFT_DISTANCE = 15;

    private static final int BASILISKS_TASK = 43;
    // Slayer level of a basilisk knight and of its superior.
    private static final int BASILISK_KNIGHT_LEVEL = 60;

    // A panel row: an offer, or its basilisk knight variant, and whether it carries the pick.
    public static class Row
    {
        public final Choice choice;
        public final boolean picked;

        Row(Choice choice, boolean picked)
        {
            this.choice = choice;
            this.picked = picked;
        }
    }

    // One offered task, resolved as far as the cache allowed.
    public static class Choice
    {
        public final String name;
        // The monster's own unique table rate for this slot; NaN when unresolvable.
        public final double uniqueTableChance;
        // Percent boost from a superior-unique modifier, 0 if none.
        public final int uniqueModifierPercent;
        // Middle of the assigned amount, a quantity modifier included; NaN when unreadable.
        public final double expectedQuantity;
        public final int killsPerHour;
        // Whether this is one of the multicombat tasks.
        public final boolean fastTask;

        // Slayer task id, -1 when the slot could not be resolved, or a MortimerTask key for a row
        // that is no offer of its own.
        public final int taskId;

        Choice(String name, double uniqueTableChance, int uniqueModifierPercent,
            double expectedQuantity, int killsPerHour, boolean fastTask)
        {
            this(-1, name, uniqueTableChance, uniqueModifierPercent, expectedQuantity, killsPerHour, fastTask);
        }

        Choice(int taskId, String name, double uniqueTableChance, int uniqueModifierPercent,
            double expectedQuantity, int killsPerHour, boolean fastTask)
        {
            this.taskId = taskId;
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
    private TaskChoiceGuideOverlay guideOverlay;

    @Inject
    private SlayerConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private MasterRulesFeature masterRulesFeature;

    private final List<Choice> choices = new ArrayList<>();
    private final List<Choice> choicesView = Collections.unmodifiableList(choices);

    // The offers as his window last showed them, kept past its closing.
    private List<Choice> lastOffered = new ArrayList<>();
    // Set when the offers are read again, until his open window has copied them.
    private boolean offersChanged;
    // Stamped on each game tick his window is shown, and when it loads.
    private Instant lastOpen = Instant.EPOCH;
    // The offer the player took, for the area panel to keep showing.
    private Choice taken;
    // When the player left Mortimer with that task; null while still beside him.
    private Instant leftAt;
    // The taken task's panel rows while the area panel is to show them, kept per tick.
    private List<Row> takenRows = Collections.emptyList();
    // The taken task those rows were built for; null while there are none.
    private Choice takenRowsFor;
    // The offers as panel rows and the pick among them; null until built after a change.
    private List<Row> offerRows;
    private int pickIndex;

    // The best option's name widget, once found in the open interface.
    private Widget bestName;
    // Set when his interface loads; cleared once its widgets are gone, on logout and on shutdown.
    private boolean choiceInterfaceLoaded;
    // Whether his window was shown on the last client tick.
    private boolean choiceShown;
    // The color that widget had before it was marked.
    private int bestNameColor;
    // The search for that widget found nothing this game tick; it is tried again on the next.
    private boolean nameSearchFailed;

    // Volatile because startUp and shutDown run off the client thread.
    private volatile boolean running;
    // A refresh is queued on the client thread and has not run yet.
    private final AtomicBoolean refreshQueued = new AtomicBoolean();

    @Override
    public void startUp()
    {
        if (log.isDebugEnabled())
        {
            log.debug("[config] display={} oddsMode={} pickMode={} overhead={} showAreas={} areaRetain={} knightsForPick={}",
                config.taskChoiceOddsDisplay(), config.taskChoiceOddsMode(), config.taskChoicePickMode(),
                config.taskChoiceOverhead(), config.taskChoiceShowAreas(), config.taskChoiceAreaRetain(),
                config.basiliskKnightsForPick());
        }
        // The recalculate checkbox stands in for a button, so it never stays
        // ticked across a restart.
        configManager.setConfiguration(SlayerConfig.GROUP, RECALCULATE_KEY, false);
        running = true;
        eventBus.register(this);
        overlayManager.add(overlay);
        overlayManager.add(guideOverlay);
        clientThread.invokeLater(() ->
        {
            if (!running)
            {
                return;
            }
            // His interface may already be open, which loads no widget again;
            // the next client tick clears this when it is not.
            choiceInterfaceLoaded = true;
            refresh();
        });
    }

    @Override
    public void shutDown()
    {
        // Off the bus first, then the state is reset on the client thread,
        // where the handlers and the overlays read it.
        running = false;
        eventBus.unregister(this);
        overlayManager.remove(overlay);
        overlayManager.remove(guideOverlay);
        clientThread.invoke(() ->
        {
            choices.clear();
            lastOffered = new ArrayList<>();
            offersChanged = false;
            lastOpen = Instant.EPOCH;
            taken = null;
            takenRows = Collections.emptyList();
            takenRowsFor = null;
            offerRows = null;
            choiceInterfaceLoaded = false;
            choiceShown = false;
        });
        configManager.setConfiguration(SlayerConfig.GROUP, RECALCULATE_KEY, false);
    }

    // Reads the offers again once the current batch of changes is in. Several slot varbits change
    // together when the offers change, and one read covers them all.
    private void scheduleRefresh()
    {
        if (!refreshQueued.compareAndSet(false, true))
        {
            return;
        }
        clientThread.invokeLater(() ->
        {
            refreshQueued.set(false);
            if (running)
            {
                refresh();
            }
        });
    }

    public List<Choice> getChoices()
    {
        return choicesView;
    }

    // Index into getChoices() of the configured mode's pick, or -1.
    public int getPickIndex()
    {
        if (offerRows == null)
        {
            buildOfferRows();
        }
        return pickIndex;
    }

    // The offered tasks as panel rows, the pick marked.
    public List<Row> getOfferRows()
    {
        if (offerRows == null)
        {
            buildOfferRows();
        }
        return offerRows;
    }

    // Works the pick and the rows out again once after the offers, the tasks completed count or a
    // setting changed.
    private void buildOfferRows()
    {
        pickIndex = scorePick();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < choices.size(); i++)
        {
            addRows(rows, choices.get(i), i == pickIndex);
        }
        offerRows = rows;
        if (log.isDebugEnabled())
        {
            log.debug("[pick] offer rows built, offers={} pick={}", choices.size(), pickIndex + 1);
        }
    }

    private int scorePick()
    {
        List<Choice> scored = choices;
        if (config.basiliskKnightsForPick())
        {
            scored = new ArrayList<>();
            for (Choice choice : choices)
            {
                Choice knights = knightsVariant(choice);
                scored.add(knights != null ? knights : choice);
            }
        }
        return TaskPickScorer.pick(scored, config.taskChoicePickMode(),
            client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED),
            config.taskChoiceOverhead(), baseline());
    }

    // Whether his choice interface is loaded; its widgets may still be hidden.
    public boolean isChoiceInterfaceLoaded()
    {
        return choiceInterfaceLoaded;
    }

    // Whether his window was shown on the last client tick.
    public boolean isChoiceShown()
    {
        return choiceShown;
    }

    // Whether the current task is one taken from his window this session.
    public boolean isTakenTaskActive()
    {
        return taken != null;
    }

    // The task the player took, as panel rows, while the area panel is still to show it. The time
    // runs from when the player leaves Mortimer.
    public List<Row> getTakenRows()
    {
        return takenRows;
    }

    // Builds the taken task's rows again when the task or whether it is still shown has changed, or
    // always when asked to, after a setting change.
    private void updateTakenRows(boolean rebuild)
    {
        Choice shown = taken != null && (leftAt == null
            || Instant.now().isBefore(leftAt.plusSeconds(Math.max(10, config.taskChoiceAreaRetain()))))
            ? taken : null;
        if (!rebuild && shown == takenRowsFor)
        {
            return;
        }
        takenRowsFor = shown;
        if (shown == null)
        {
            if (!takenRows.isEmpty())
            {
                log.debug("[taken] area rows cleared");
            }
            takenRows = Collections.emptyList();
            return;
        }
        List<Row> rows = new ArrayList<>();
        // A lone row needs no mark; of the two basilisk rows one does.
        addRows(rows, shown, knightsVariant(shown) != null);
        takenRows = rows;
        if (log.isDebugEnabled())
        {
            log.debug("[taken] area rows built for {}", shown.name);
        }
    }

    // Of a basilisk offer's two rows, the pick goes to the one it was worked out from.
    private void addRows(List<Row> rows, Choice choice, boolean picked)
    {
        Choice knights = knightsVariant(choice);
        boolean knightsPicked = knights != null && config.basiliskKnightsForPick();
        rows.add(new Row(choice, picked && !knightsPicked));
        if (knights != null)
        {
            rows.add(new Row(knights, picked && knightsPicked));
        }
    }

    // Hours the choice is expected to take, or NaN when its amount is unreadable.
    public double estimatedHours(Choice choice)
    {
        return TaskPickScorer.hours(choice.expectedQuantity, choice.killsPerHour,
            config.taskChoiceOverhead());
    }

    // A basilisk task can be spent on basilisk knights, whose superior rolls at their own slayer
    // level. Returns that variant of a basilisk offer for display beside it, or null for any other
    // task. The pick goes by it only when the player has ticked that.
    public Choice knightsVariant(Choice choice)
    {
        if (choice.taskId != BASILISKS_TASK)
        {
            return null;
        }
        return new Choice(MortimerTask.BASILISK_KNIGHTS.id, "Basilisk Knights",
            baseUniqueChanceAt(BASILISK_KNIGHT_LEVEL) * (1 + choice.uniqueModifierPercent / 100.0),
            choice.uniqueModifierPercent, choice.expectedQuantity, config.kphBasiliskKnights(), false);
    }

    // The stored long-run rate, or the bundled one when none is stored.
    private double baseline()
    {
        String stored = configManager.getConfiguration(SlayerConfig.GROUP, BASELINE_KEY);
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
        return MortimerTask.BASELINE;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        int varp = event.getVarpId();
        if (varp == VarPlayerID.SLAYER_TARGET || varp == VarPlayerID.SLAYER_COUNT)
        {
            noteTakenTask();
        }
        if (varp == VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED)
        {
            // The pick weighs the tasks completed count, which runs no refresh.
            offerRows = null;
        }

        int id = event.getVarbitId();
        for (int slot = 0; slot < CHOICE_TASK_VARBITS.length; slot++)
        {
            if (id == CHOICE_TASK_VARBITS[slot] || id == CHOICE_MODIFIER_ID_VARBITS[slot]
                || id == CHOICE_MODIFIER_VALUE_VARBITS[slot]
                || id == CHOICE_MODIFIER_NEGATIVE_VARBITS[slot])
            {
                scheduleRefresh();
                return;
            }
        }
    }

    // A task that starts right after his window was open, and is one of the offers it showed, is
    // the offer the player took. It is dropped again once that task is finished or replaced.
    private void noteTakenTask()
    {
        int count = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
        int target = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
        if (taken != null && (count <= 0 || target != taken.taskId))
        {
            if (log.isDebugEnabled())
            {
                log.debug("[taken] dropped {}, count={} target={}", taken.name, count, target);
            }
            taken = null;
            updateTakenRows(false);
        }
        if (taken != null || count <= 0 || Instant.now().isAfter(lastOpen.plus(TAKEN_WINDOW)))
        {
            return;
        }
        for (Choice choice : lastOffered)
        {
            if (choice.taskId == target)
            {
                taken = choice;
                leftAt = null;
                if (log.isDebugEnabled())
                {
                    log.debug("[taken] {} taken from the window, count={}", choice.name, count);
                }
                updateTakenRows(false);
                return;
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        nameSearchFailed = false;
        if (choiceShown)
        {
            lastOpen = Instant.now();
        }
        if (taken != null && leftAt == null && !isMortimerNear())
        {
            leftAt = Instant.now();
            if (log.isDebugEnabled())
            {
                log.debug("[taken] left Mortimer with {}", taken.name);
            }
        }
        updateTakenRows(false);
    }

    private boolean isMortimerNear()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return false;
        }
        for (Map.Entry<NPC, SlayerMaster> entry : masterRulesFeature.getNearbyMasters().entrySet())
        {
            if (entry.getValue() == SlayerMaster.MORTIMER
                && entry.getKey().getWorldLocation().distanceTo(player.getWorldLocation()) <= LEFT_DISTANCE)
            {
                return true;
            }
        }
        return false;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
        {
            // The varps are sent again on login, which must not count as a
            // task taken from a window open just before the hop.
            lastOpen = Instant.EPOCH;
            choiceShown = false;
        }
        if (state == GameState.LOGIN_SCREEN)
        {
            log.debug("[state] login screen, taken task cleared");
            taken = null;
            choiceInterfaceLoaded = false;
            offerRows = null;
            updateTakenRows(false);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!SlayerConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }
        if (RECALCULATE_KEY.equals(event.getKey()))
        {
            if (Boolean.parseBoolean(event.getNewValue()))
            {
                log.debug("[baseline] recalculation requested");
                clientThread.invokeLater(() ->
                {
                    if (running)
                    {
                        recalculateBaseline();
                    }
                });
            }
            return;
        }
        if (log.isDebugEnabled())
        {
            log.debug("[config] {} -> {}", event.getKey(), event.getNewValue());
        }
        scheduleRefresh();
    }

    // Works the long-run rate out again from Mortimer's whole task list, with the player's kills
    // per hour and overhead, and stores it. Nothing is stored when any row is unreadable.
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
                boolean knights = taskId == BASILISKS_TASK && config.basiliskKnightsForPick();
                int kph = knights ? config.kphBasiliskKnights() : MortimerTask.killsPerHour(taskId, config);
                weights[i] = (Integer) client.getDBTableField(row, DBTableID.SlayerMasterTask.COL_WEIGHT, 0)[0];
                rolls[i] = TaskPickScorer.rolls(quantity,
                    knights ? baseUniqueChanceAt(BASILISK_KNIGHT_LEVEL) : baseUniqueChance(taskRow));
                hours[i] = TaskPickScorer.hours(quantity, kph, config.taskChoiceOverhead());
            }

            double rate = n < 3 ? Double.NaN : TaskPickScorer.baseline(weights, rolls, hours);
            if (rate > 0 && !Double.isInfinite(rate))
            {
                configManager.setConfiguration(SlayerConfig.GROUP, BASELINE_KEY, Double.toString(rate));
                if (log.isDebugEnabled())
                {
                    log.debug("[baseline] recalculated to {}", rate);
                }
            }
        }
        catch (RuntimeException e)
        {
            if (log.isDebugEnabled())
            {
                log.debug("[baseline] not recalculated", e);
            }
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.SLAYER_TASK_CHOICE)
        {
            choiceInterfaceLoaded = true;
            lastOpen = Instant.now();
            if (log.isDebugEnabled())
            {
                log.debug("[choice] interface loaded, offers={}", choices.size());
            }
            scheduleRefresh();
        }
    }

    private void refresh()
    {
        choices.clear();
        offerRows = null;
        offersChanged = true;
        clearHighlight();
        // Settings feed the taken task's rows too, and a change ends up here.
        updateTakenRows(true);
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
            if (log.isDebugEnabled())
            {
                log.debug("[choice] slot {}: task id {} is {}", slot + 1, taskId, choice.name);
            }
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
        int kph = MortimerTask.killsPerHour(taskId, config);
        return new Choice(taskId, name, uniqueTableChance(taskRow, uniquePercent), uniquePercent,
            expectedQuantity(taskRow, quantityDelta), kph, MortimerTask.isFastTask(taskId));
    }

    // Kills the task is expected to be assigned for, or NaN when unreadable.
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

    // Middle of the amount range on a master task row. A task extension the player has bought
    // replaces the range or adds to it, whichever the task row lists for it.
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

    // Whether the task lists an extension in the given column and the player has it. The column
    // leads with the extension's reward row, whose bit number counts across the two reward unlock
    // varps.
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

    // Row of the task a slot names by id, or UNREADABLE_ROW. The varbit carries the id the task
    // table indexes, not a row of its own.
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

    // Display name on a task row, or null when the cache doesn't hand one over.
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

    // The slot's unique table rate, or NaN when the row is unreadable.
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

    // The monster's slayer level from its task entry; 1 when none is listed. Warped creatures need
    // 56 Slayer, but their entry lists no level.
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

    // The monster's own rate of rolling a unique table, computed from its slayer task entry. The
    // squared term divides down as an integer, which the game's own drop rates confirm: a crushing
    // hand at slayer 5 lands on 1/172, since 60 squared over 125 truncates to 28 rather than 28.8,
    // and its imbued heart is eight times that at the 1/1376 the game drops it at. A hydra at
    // slayer 95 lands on 1/20 and its heart on 1/160.
    private double baseUniqueChance(int taskRow)
    {
        return baseUniqueChanceAt(slayerLevel(taskRow));
    }

    static double baseUniqueChanceAt(int slayerLevel)
    {
        int scaled = slayerLevel + 55;
        int denominator = 200 - scaled * scaled / 125;
        return denominator <= 1 ? 1 : 1.0 / denominator;
    }

    // Colors the best option's name inside Mortimer's interface. The rows are script-built, so the
    // name is found by its text rather than by a fixed child index. That search runs once per
    // opening, or once per game tick while it finds nothing, and the component is kept for the
    // color to be reapplied from, since the interface's own handling of the row can write over it.
    // The old color is put back whenever the offers or settings are read again; the rows are built
    // fresh each time it opens.
    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (!choiceInterfaceLoaded)
        {
            return;
        }

        Widget content = client.getWidget(TASK_CHOICE_CONTENT);
        if (content == null)
        {
            choiceInterfaceLoaded = false;
        }
        choiceShown = content != null && !content.isHidden();
        if (!choiceShown)
        {
            bestName = null;
            return;
        }

        if (offersChanged)
        {
            offersChanged = false;
            if (!choices.isEmpty())
            {
                lastOffered = new ArrayList<>(choices);
            }
        }

        if (!config.taskChoiceOddsDisplay().showsHighlight())
        {
            return;
        }

        if (bestName == null)
        {
            int best = getPickIndex();
            if (best == -1 || nameSearchFailed)
            {
                return;
            }
            bestName = findNameWidget(content, choices.get(best).name);
            if (bestName != null)
            {
                bestNameColor = bestName.getTextColor();
                if (log.isDebugEnabled())
                {
                    log.debug("[highlight] marked pick {} ({})", choices.get(best).name, best + 1);
                }
            }
            else
            {
                nameSearchFailed = true;
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
        nameSearchFailed = false;
    }

    // The widget carrying a task's name, searched depth first, or null.
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

    // Whether a widget's text names the given task. The interface wraps longer names across two
    // lines and can word them differently from the table, so the comparison drops markup and lets
    // either side be the longer one.
    private static boolean matchesTaskName(String text, String taskName)
    {
        String shown = withoutMarkup(text);
        String task = withoutMarkup(taskName);
        return !shown.isEmpty() && !task.isEmpty()
            && (shown.startsWith(task) || task.startsWith(shown));
    }

    // Text with markup, spacing and case stripped, for comparison.
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
