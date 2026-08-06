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
import com.vividflash.slayer.TaskSortMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.Text;

/**
 * Sorts the slayer task list interface (the "View tasks" list from the
 * slayer rewards screen) by displayed assignment weight or alphabetically,
 * with an optional reversed order.
 *
 * The list rows are dynamic children; they are grouped by their y position,
 * sorted, and re-laid-out onto the original row slots. Both the visual layer
 * and the clickable layer are moved the same way so clicks stay aligned.
 * If the interface doesn't look like the expected row structure, nothing is
 * touched.
 */
@Singleton
public class TaskSorterFeature implements Feature
{
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
    // The list has shown task odds as "1/8"-style fractions as well as
    // percentages; accept either as a row weight.
    private static final Pattern ODDS_FRACTION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    /** Scrollable task list on the Slayer Rewards "Tasks" tab. */
    private static final int REWARDS_TASKS_LIST = InterfaceID.SlayerRewards.TASKS_CONTENTS_SCROLLABLE;

    /**
     * Under three rows there is no second gap to check the first one against,
     * so the row pitch proves nothing and the container is left alone.
     */
    private static final int MIN_TASK_ROWS = 3;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private EventBus eventBus;

    @Inject
    private SlayerConfig config;

    @Override
    public void startUp()
    {
        eventBus.register(this);
    }

    @Override
    public void shutDown()
    {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        int group = event.getGroupId();
        if ((group != InterfaceID.SLAYER_REWARDS_TASK_LIST && group != InterfaceID.SLAYER_REWARDS)
            || !config.taskSorter())
        {
            return;
        }

        // Let the game scripts finish building the list first.
        clientThread.invokeLater(this::sortAllLists);
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // The rewards interface swaps tabs and rebuilds rows via scripts with
        // no further WidgetLoaded, so keep whichever list is open sorted from
        // here.
        if (config.taskSorter())
        {
            sortAllLists();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"vividflashslayer".equals(event.getGroup()))
        {
            return;
        }

        if (("taskSorter".equals(event.getKey()) || "taskSortMethod".equals(event.getKey())
            || "taskSortReversed".equals(event.getKey())) && config.taskSorter())
        {
            clientThread.invokeLater(this::sortAllLists);
        }
    }

    private void sortAllLists()
    {
        sortTaskList();
        sortRewardsTaskTab();
    }

    /**
     * Sorts the scrollable list on the rewards interface's Tasks tab. Rows
     * there are single-layer (no separate clickable overlay), so the row
     * widgets themselves are moved.
     */
    private void sortRewardsTaskTab()
    {
        Widget container = client.getWidget(REWARDS_TASKS_LIST);
        if (container == null || container.isHidden())
        {
            return;
        }

        Widget[] children = container.getDynamicChildren();
        if (children == null || children.length == 0)
        {
            children = container.getChildren();
        }

        applySort(groupIntoRows(children), new ArrayList<>());
    }

    /** One visual row of the list, the widgets sharing a base y with its sort keys. */
    private static class Row
    {
        final int baseY;
        final List<Widget> widgets = new ArrayList<>();
        String name = "";
        double weight = -1;

        Row(int baseY)
        {
            this.baseY = baseY;
        }
    }

    private void sortTaskList()
    {
        Widget drawable = client.getWidget(InterfaceID.SlayerRewardsTaskList.DRAWABLE);
        Widget clickable = client.getWidget(InterfaceID.SlayerRewardsTaskList.CLICKABLE);
        if (drawable == null || drawable.isHidden() || clickable == null || clickable.isHidden())
        {
            return;
        }

        List<Row> clickRows = groupIntoRows(clickable.getDynamicChildren());
        if (clickRows.isEmpty())
        {
            return;
        }

        applySort(groupIntoRows(drawable.getDynamicChildren()), clickRows);
    }

    private void applySort(List<Row> rows, List<Row> lockstep)
    {
        // Clusters without any text (dividers, lone icons) are not task rows,
        // so they keep their slots instead of joining the sort.
        for (Row row : rows)
        {
            extractSortKeys(row);
        }
        rows.removeIf(row -> row.widgets.stream()
            .noneMatch(w -> w.getText() != null && !Text.removeTags(w.getText()).trim().isEmpty()));

        if (rows.size() < MIN_TASK_ROWS || !looksLikeTaskRows(rows) || !linesUpWith(rows, lockstep))
        {
            return;
        }

        Comparator<Row> comparator;
        boolean anyWeighted = rows.stream().anyMatch(r -> r.weight >= 0);
        if (config.taskSortMethod() == TaskSortMethod.WEIGHT && anyWeighted)
        {
            // Heaviest (most likely) task first. A row whose odds couldn't be
            // read sinks to the end instead of knocking the whole list back
            // to alphabetical.
            comparator = Comparator.comparingDouble((Row r) -> r.weight >= 0 ? -r.weight : Double.MAX_VALUE)
                .thenComparing(r -> r.name, String.CASE_INSENSITIVE_ORDER);
        }
        else
        {
            comparator = Comparator.comparing(r -> r.name, String.CASE_INSENSITIVE_ORDER);
        }
        if (config.taskSortReversed())
        {
            comparator = comparator.reversed();
        }

        List<Integer> slotYs = sortedBaseYs(rows);
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(comparator);

        for (int i = 0; i < sorted.size(); i++)
        {
            Row row = sorted.get(i);
            int targetY = slotYs.get(i);
            moveRow(row, targetY);

            Row partner = findByBaseY(lockstep, row.baseY);
            if (partner != null)
            {
                moveRow(partner, targetY);
            }
        }
    }

    /**
     * A container is only sorted when its rows carry the shape a task list
     * has, which is a name on every row, the same widget count on each, and a
     * constant vertical pitch across the whole run. Headers, captions and
     * other furniture sharing the container break one of the three, and the
     * list is left as the game built it. The caller has already ruled out
     * runs too short for the pitch to mean anything.
     */
    private static boolean looksLikeTaskRows(List<Row> rows)
    {
        int widgetCount = rows.get(0).widgets.size();
        for (Row row : rows)
        {
            if (row.name.isEmpty() || row.widgets.size() != widgetCount)
            {
                return false;
            }
        }

        List<Integer> ys = sortedBaseYs(rows);
        int pitch = ys.get(1) - ys.get(0);
        if (pitch <= 0)
        {
            return false;
        }
        for (int i = 2; i < ys.size(); i++)
        {
            if (ys.get(i) - ys.get(i - 1) != pitch)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Every row about to move needs its own partner on the clickable layer.
     * Moving the visual rows without it would point the clicks at the old
     * order. Base y values are unique within a layer, so a clickable row with
     * no partner here belongs to a cluster the visual side dropped, sits at a
     * y no sort target uses, and stays where it is. An empty lockstep list
     * means the caller has a single-layer list.
     */
    private static boolean linesUpWith(List<Row> rows, List<Row> lockstep)
    {
        if (lockstep.isEmpty())
        {
            return true;
        }
        for (Row row : rows)
        {
            if (findByBaseY(lockstep, row.baseY) == null)
            {
                return false;
            }
        }
        return true;
    }

    private static Row findByBaseY(List<Row> rows, int baseY)
    {
        for (Row row : rows)
        {
            if (row.baseY == baseY)
            {
                return row;
            }
        }
        return null;
    }

    private static List<Integer> sortedBaseYs(List<Row> rows)
    {
        List<Integer> ys = new ArrayList<>();
        for (Row row : rows)
        {
            ys.add(row.baseY);
        }
        ys.sort(Comparator.naturalOrder());
        return ys;
    }

    private static List<Row> groupIntoRows(Widget[] children)
    {
        // Rows are reconstructed by clustering children on their y position.
        // Each child belongs to the row whose base y it shares.
        Map<Integer, Row> byY = new LinkedHashMap<>();
        if (children == null)
        {
            return new ArrayList<>(byY.values());
        }

        for (Widget child : children)
        {
            if (child == null || child.isSelfHidden())
            {
                continue;
            }
            byY.computeIfAbsent(child.getOriginalY(), Row::new).widgets.add(child);
        }
        return new ArrayList<>(byY.values());
    }

    private static void extractSortKeys(Row row)
    {
        for (Widget widget : row.widgets)
        {
            String text = widget.getText();
            if (text == null || text.isEmpty())
            {
                continue;
            }
            String stripped = Text.removeTags(text).trim();
            if (stripped.isEmpty())
            {
                continue;
            }

            if (row.weight < 0)
            {
                Matcher weight = WEIGHT_PATTERN.matcher(stripped);
                if (weight.find())
                {
                    row.weight = Double.parseDouble(weight.group(1));
                }
                else
                {
                    Matcher odds = ODDS_FRACTION_PATTERN.matcher(stripped);
                    if (odds.find() && Double.parseDouble(odds.group(2)) > 0)
                    {
                        row.weight = 100 * Double.parseDouble(odds.group(1))
                            / Double.parseDouble(odds.group(2));
                    }
                }
            }

            if (row.name.isEmpty() && stripped.chars().anyMatch(Character::isLetter))
            {
                row.name = stripped;
            }
        }
    }

    private static void moveRow(Row row, int targetY)
    {
        int delta = targetY - row.baseY;
        if (delta == 0)
        {
            return;
        }
        for (Widget widget : row.widgets)
        {
            widget.setOriginalY(widget.getOriginalY() + delta);
            widget.revalidate();
        }
    }
}
