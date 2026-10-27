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
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
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

// Sorts the slayer task list interface (the "View tasks" list from the slayer rewards screen) by
// displayed assignment weight or alphabetically, with an optional reversed order.
//
// The list rows are dynamic children; they are grouped by their y position, sorted, and re-laid-out
// onto the original row slots. Both the visual layer and the clickable layer are moved the same way
// so clicks stay aligned. If the interface doesn't look like the expected row structure, nothing is
// touched.
@Slf4j
@Singleton
public class TaskSorterFeature implements Feature
{
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
    // The list has shown task odds as "1/8"-style fractions as well as
    // percentages; accept either as a row weight.
    private static final Pattern ODDS_FRACTION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    // Volatile because startUp and shutDown run off the client thread.
    private volatile boolean running;

    // Set when the task list interface loads; cleared once it is gone.
    private boolean rewardsLoaded;

    // A rejected row layout is logged once per opening, not on every tick it is rechecked.
    private boolean rejectLogged;

    // Layout of the list as we last left it: every row widget's text and y in child order. Our own
    // sort changes the y values, so it is taken again after sorting; a game rebuild (new texts, or
    // rows put back at their original y) no longer matches it and the list is sorted again.
    private static final long NO_SIGNATURE = 0;
    private long taskListSignature = NO_SIGNATURE;

    // Under three rows there is no second gap to check the first one against, so the row pitch
    // proves nothing and the container is left alone.
    private static final int MIN_TASK_ROWS = 3;

    // Every widget our sort moved: the game's y and the y we last put it at, so switching the
    // sorter off can put the rows back. A widget the game has moved itself since is recorded afresh
    // on the next sort.
    private final Map<Widget, int[]> movedWidgets = new IdentityHashMap<>();

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
        if (log.isDebugEnabled())
        {
            log.debug("[config] taskSorter={} method={} reversed={}", config.taskSorter(),
                config.taskSortMethod(), config.taskSortReversed());
        }
        running = true;
        eventBus.register(this);

        // A task list opened before startUp fired no WidgetLoaded
        // for us, so pick it up here.
        clientThread.invokeLater(() ->
        {
            if (!running)
            {
                return;
            }
            if (!rewardsInterfaceOpen())
            {
                return;
            }
            rewardsLoaded = true;
            if (log.isDebugEnabled())
            {
                log.debug("[sort] interface already open at startUp, taskSorter={}", config.taskSorter());
            }
            if (config.taskSorter())
            {
                sortAllLists();
            }
        });
    }

    @Override
    public void shutDown()
    {
        // Off the bus first, then the state is reset on the client thread,
        // where the handlers read it.
        running = false;
        eventBus.unregister(this);
        clientThread.invoke(() ->
        {
            restoreOrder();
            rewardsLoaded = false;
            rejectLogged = false;
            resetSignatures();
        });
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        int group = event.getGroupId();
        if (group != InterfaceID.SLAYER_REWARDS_TASK_LIST)
        {
            return;
        }

        rewardsLoaded = true;
        rejectLogged = false;
        resetSignatures();
        movedWidgets.clear();
        if (log.isDebugEnabled())
        {
            log.debug("[sort] interface group {} loaded, taskSorter={}", group, config.taskSorter());
        }
        if (!config.taskSorter())
        {
            return;
        }

        // Let the game scripts finish building the list first.
        clientThread.invokeLater(this::sortAllListsIfRunning);
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // The game rebuilds the rows via scripts with no further
        // WidgetLoaded, so the open list is checked from here.
        // It is only sorted again when its layout signature changed.
        if (!rewardsLoaded || !config.taskSorter())
        {
            return;
        }

        if (!rewardsInterfaceOpen())
        {
            rewardsLoaded = false;
            resetSignatures();
            movedWidgets.clear();
            log.debug("[sort] interface closed");
            return;
        }

        sortAllLists();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!SlayerConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        if (!"taskSorter".equals(event.getKey()) && !"taskSortMethod".equals(event.getKey())
            && !"taskSortReversed".equals(event.getKey()))
        {
            return;
        }

        if (config.taskSorter() && log.isDebugEnabled())
        {
            log.debug("[config] {} -> {}", event.getKey(), event.getNewValue());
        }
        // The signatures are reset on the client thread, so a sort running
        // there cannot store its own over the reset.
        clientThread.invokeLater(() ->
        {
            if (!running)
            {
                return;
            }
            resetSignatures();
            if (config.taskSorter())
            {
                sortAllLists();
            }
            else
            {
                restoreOrder();
            }
        });
    }

    private boolean rewardsInterfaceOpen()
    {
        return client.getWidget(InterfaceID.SlayerRewardsTaskList.UNIVERSE) != null;
    }

    // Puts every row we moved back at the game's y. Only widgets still in an open list are touched;
    // anything the game has rebuilt or moved since is left as it is. The record and the signatures
    // are cleared either way.
    private void restoreOrder()
    {
        if (!movedWidgets.isEmpty() && rewardsInterfaceOpen())
        {
            Set<Widget> live = Collections.newSetFromMap(new IdentityHashMap<>());
            addChildren(live, client.getWidget(InterfaceID.SlayerRewardsTaskList.DRAWABLE));
            addChildren(live, client.getWidget(InterfaceID.SlayerRewardsTaskList.CLICKABLE));

            int restored = 0;
            for (Map.Entry<Widget, int[]> entry : movedWidgets.entrySet())
            {
                Widget widget = entry.getKey();
                int[] ys = entry.getValue();
                if (live.contains(widget) && widget.getOriginalY() == ys[1])
                {
                    widget.setOriginalY(ys[0]);
                    widget.revalidate();
                    restored++;
                }
            }
            if (log.isDebugEnabled())
            {
                log.debug("[sort] restored game order, {} of {} widgets", restored, movedWidgets.size());
            }
        }
        movedWidgets.clear();
        resetSignatures();
    }

    private static void addChildren(Set<Widget> into, Widget container)
    {
        if (container == null)
        {
            return;
        }
        for (Widget[] children : new Widget[][]{container.getDynamicChildren(), container.getChildren()})
        {
            if (children != null)
            {
                Collections.addAll(into, children);
            }
        }
    }

    private void sortAllListsIfRunning()
    {
        if (running)
        {
            sortAllLists();
        }
    }

    private void sortAllLists()
    {
        sortTaskList();
    }

    private void resetSignatures()
    {
        taskListSignature = NO_SIGNATURE;
    }

    // Hash of each visible child's y and text in child order. Child order does not follow our sort,
    // so a list we sorted hashes the same until the game changes a text or moves a row.
    private static long layoutSignature(Widget[]... layers)
    {
        long hash = 1;
        for (Widget[] children : layers)
        {
            if (children == null)
            {
                hash = 31 * hash + 7;
                continue;
            }
            hash = 31 * hash + children.length;
            for (Widget child : children)
            {
                if (child == null || child.isSelfHidden())
                {
                    hash = 31 * hash + 3;
                    continue;
                }
                hash = 31 * hash + child.getOriginalY();
                hash = 31 * hash + Objects.hashCode(child.getText());
            }
        }
        return hash == NO_SIGNATURE ? 1 : hash;
    }

    // One visual row of the list, the widgets sharing a base y with its sort keys.
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
            taskListSignature = NO_SIGNATURE;
            return;
        }

        Widget[] drawn = drawable.getDynamicChildren();
        Widget[] clicks = clickable.getDynamicChildren();
        if (layoutSignature(drawn, clicks) == taskListSignature)
        {
            return;
        }

        Map<Integer, Row> clickRows = groupIntoRows(clicks);
        if (!clickRows.isEmpty())
        {
            applySort(new ArrayList<>(groupIntoRows(drawn).values()), clickRows);
        }
        taskListSignature = layoutSignature(drawn, clicks);
    }

    // Sorts the rows onto their own slots. Partners are the clickable layer's rows by base y, moved
    // along with the visual row at the same y.
    private void applySort(List<Row> rows, Map<Integer, Row> partners)
    {
        // Clusters without any text (dividers, lone icons) are not task rows,
        // so they keep their slots instead of joining the sort.
        for (Row row : rows)
        {
            extractSortKeys(row);
        }
        rows.removeIf(row -> row.widgets.stream()
            .noneMatch(w -> w.getText() != null && !Text.removeTags(w.getText()).trim().isEmpty()));

        if (rows.size() < MIN_TASK_ROWS || !looksLikeTaskRows(rows) || !linesUpWith(rows, partners))
        {
            if (!rejectLogged)
            {
                rejectLogged = true;
                if (log.isDebugEnabled())
                {
                    log.debug("[sort] left alone, layout is not a task list rows={} lockstep={}", rows.size(), partners.size());
                }
            }
            return;
        }

        Comparator<Row> comparator;
        boolean anyWeighted = rows.stream().anyMatch(r -> r.weight >= 0);
        if (config.taskSortMethod() == TaskSortMethod.WEIGHT && anyWeighted)
        {
            // Heaviest (most likely) task first. A row whose odds couldn't be
            // read sinks to the end in either order instead of knocking the
            // whole list back to alphabetical.
            Comparator<Row> byWeight = Comparator.comparingDouble((Row r) -> -r.weight)
                .thenComparing(r -> r.name, String.CASE_INSENSITIVE_ORDER);
            if (config.taskSortReversed())
            {
                byWeight = byWeight.reversed();
            }
            comparator = Comparator.comparing((Row r) -> r.weight < 0).thenComparing(byWeight);
        }
        else
        {
            comparator = Comparator.comparing(r -> r.name, String.CASE_INSENSITIVE_ORDER);
            if (config.taskSortReversed())
            {
                comparator = comparator.reversed();
            }
        }

        List<Integer> slotYs = sortedBaseYs(rows);
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(comparator);

        int moved = 0;
        for (int i = 0; i < sorted.size(); i++)
        {
            if (sorted.get(i).baseY != slotYs.get(i))
            {
                moved++;
            }
        }
        if (moved > 0 && log.isDebugEnabled())
        {
            log.debug("[sort] reordered {} of {} rows method={} reversed={}", moved, sorted.size(),
                config.taskSortMethod(), config.taskSortReversed());
        }

        for (int i = 0; i < sorted.size(); i++)
        {
            Row row = sorted.get(i);
            int targetY = slotYs.get(i);
            moveRow(row, targetY);

            Row partner = partners.get(row.baseY);
            if (partner != null)
            {
                moveRow(partner, targetY);
            }
        }
    }

    // A container is only sorted when its rows carry the shape a task list has, which is a name on
    // every row, the same widget count on each, and a constant vertical pitch across the whole run.
    // Headers, captions and other furniture sharing the container break one of the three, and the
    // list is left as the game built it. The caller has already ruled out runs too short for the
    // pitch to mean anything.
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

    // Every row about to move needs its own partner on the clickable layer. Moving the visual rows
    // without it would point the clicks at the old order. Base y values are unique within a layer,
    // so a clickable row with no partner here belongs to a cluster the visual side dropped, sits at
    // a y no sort target uses, and stays where it is.
    private static boolean linesUpWith(List<Row> rows, Map<Integer, Row> partners)
    {
        for (Row row : rows)
        {
            if (!partners.containsKey(row.baseY))
            {
                return false;
            }
        }
        return true;
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

    // Rows by base y, in child order of their first widget.
    private static Map<Integer, Row> groupIntoRows(Widget[] children)
    {
        // Rows are reconstructed by clustering children on their y position.
        // Each child belongs to the row whose base y it shares.
        Map<Integer, Row> byY = new LinkedHashMap<>();
        if (children == null)
        {
            return byY;
        }

        for (Widget child : children)
        {
            if (child == null || child.isSelfHidden())
            {
                continue;
            }
            byY.computeIfAbsent(child.getOriginalY(), Row::new).widgets.add(child);
        }
        return byY;
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

    private void moveRow(Row row, int targetY)
    {
        int delta = targetY - row.baseY;
        for (Widget widget : row.widgets)
        {
            // A widget no longer at the y we put it at was moved by the game,
            // so its current y is the game's again.
            int[] ys = movedWidgets.get(widget);
            if (ys != null && ys[1] != widget.getOriginalY())
            {
                movedWidgets.remove(widget);
                ys = null;
            }
            if (delta == 0)
            {
                continue;
            }
            if (ys == null)
            {
                ys = new int[]{widget.getOriginalY(), 0};
                movedWidgets.put(widget, ys);
            }
            ys[1] = widget.getOriginalY() + delta;
            widget.setOriginalY(ys[1]);
            widget.revalidate();
        }
    }
}
