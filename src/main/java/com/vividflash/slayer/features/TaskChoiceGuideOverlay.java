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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

// Shows where each task Mortimer is offering is usually done, how to get there and the method,
// while his choice interface is open, and the task taken for a while after it closes.
@Singleton
public class TaskChoiceGuideOverlay extends OverlayPanel
{
    private final SlayerConfig config;
    private final TaskChoiceOddsFeature feature;

    // The panel width, measured once for the rows and font it was worked out from.
    private List<TaskChoiceOddsFeature.Row> widthRows;
    private Font widthFont;
    private int width;

    @Inject
    TaskChoiceGuideOverlay(SlayerConfig config, TaskChoiceOddsFeature feature)
    {
        this.config = config;
        this.feature = feature;
        setPosition(OverlayPosition.TOP_LEFT);
        // His interface covers the viewport in fixed mode, so the panel has to sit above it.
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Past the retain time the taken rows are empty, and with his window
        // closed there is nothing else to show.
        if (feature.getTakenRows().isEmpty()
            && (feature.getChoices().isEmpty() || !feature.isChoiceInterfaceLoaded()))
        {
            return null;
        }

        if (!config.taskChoiceShowAreas())
        {
            return null;
        }

        // With his window closed, the task just taken stays up for a while.
        boolean open = feature.isChoiceShown() && !feature.getChoices().isEmpty();

        List<TaskChoiceOddsFeature.Row> rows = open ? feature.getOfferRows() : feature.getTakenRows();
        for (TaskChoiceOddsFeature.Row row : rows)
        {
            addEntry(row.choice, row.picked ? Color.GREEN : Color.WHITE);
        }

        if (panelComponent.getChildren().isEmpty())
        {
            return null;
        }

        FontMetrics metrics = graphics.getFontMetrics();
        if (rows != widthRows || !metrics.getFont().equals(widthFont))
        {
            width = measure(metrics, rows);
            widthRows = rows;
            widthFont = metrics.getFont();
        }
        panelComponent.setPreferredSize(new Dimension(width, 0));
        return super.render(graphics);
    }

    // Adds a task's three rows, under the title if they are the first; a task with no entry adds
    // nothing.
    private void addEntry(TaskChoiceOddsFeature.Choice choice, Color nameColor)
    {
        MortimerTask entry = MortimerTask.forId(choice.taskId);
        if (entry == null)
        {
            return;
        }
        if (panelComponent.getChildren().isEmpty())
        {
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Task Areas")
                .build());
        }
        addLine(choice.name, entry.method, nameColor);
        addLine(entry.area, "", Color.LIGHT_GRAY);
        addLine(entry.travel, "", Color.LIGHT_GRAY);
    }

    private void addLine(String left, String right, Color color)
    {
        panelComponent.getChildren().add(LineComponent.builder()
            .left(left)
            .leftColor(color)
            .right(right)
            .rightColor(color)
            .build());
    }

    // The panel width that keeps every row of the given tasks on one line.
    private static int measure(FontMetrics metrics, List<TaskChoiceOddsFeature.Row> rows)
    {
        int width = ComponentConstants.STANDARD_WIDTH;
        for (TaskChoiceOddsFeature.Row row : rows)
        {
            MortimerTask entry = MortimerTask.forId(row.choice.taskId);
            if (entry == null)
            {
                continue;
            }
            width = Math.max(width, lineWidth(metrics, row.choice.name, entry.method));
            width = Math.max(width, lineWidth(metrics, entry.area, ""));
            width = Math.max(width, lineWidth(metrics, entry.travel, ""));
        }
        return width;
    }

    private static int lineWidth(FontMetrics metrics, String left, String right)
    {
        return metrics.stringWidth(left) + metrics.stringWidth(right) + 4 * ComponentConstants.STANDARD_BORDER;
    }
}