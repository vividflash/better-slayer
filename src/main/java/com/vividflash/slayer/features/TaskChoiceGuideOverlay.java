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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Shows where each task Mortimer is offering is usually done, how to get
 * there and the method, while his choice interface is open, and the task
 * taken for a while after it closes.
 */
@Singleton
public class TaskChoiceGuideOverlay extends OverlayPanel
{
    private final Client client;
    private final SlayerConfig config;
    private final TaskChoiceOddsFeature feature;

    @Inject
    TaskChoiceGuideOverlay(Client client, SlayerConfig config, TaskChoiceOddsFeature feature)
    {
        this.client = client;
        this.config = config;
        this.feature = feature;
        setPosition(OverlayPosition.TOP_LEFT);
        // His interface covers the viewport in fixed mode, so the panel has to sit above it.
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.taskChoiceShowAreas())
        {
            return null;
        }

        // With his window closed, the task just taken stays up for a while.
        Widget content = client.getWidget(TaskChoiceOddsFeature.TASK_CHOICE_CONTENT);
        boolean open = content != null && !content.isHidden() && !feature.getChoices().isEmpty();

        FontMetrics metrics = graphics.getFontMetrics();
        int width = ComponentConstants.STANDARD_WIDTH;
        for (TaskChoiceOddsFeature.Row row : open ? feature.getOfferRows() : feature.getTakenRows())
        {
            width = Math.max(width, addEntry(metrics, row.choice, row.picked ? Color.GREEN : Color.WHITE));
        }

        if (panelComponent.getChildren().isEmpty())
        {
            return null;
        }

        panelComponent.setPreferredSize(new Dimension(width, 0));
        return super.render(graphics);
    }

    /**
     * Adds a task's three rows, under the title if they are the first, and
     * returns the panel width they need, or 0 for a task with no entry.
     */
    private int addEntry(FontMetrics metrics, TaskChoiceOddsFeature.Choice choice, Color nameColor)
    {
        MortimerTaskGuide.Entry entry = MortimerTaskGuide.forTask(choice.taskId);
        if (entry == null)
        {
            return 0;
        }
        if (panelComponent.getChildren().isEmpty())
        {
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Task Areas")
                .build());
        }
        int width = addLine(metrics, choice.name, entry.method, nameColor);
        width = Math.max(width, addLine(metrics, entry.area, "", Color.LIGHT_GRAY));
        return Math.max(width, addLine(metrics, entry.travel, "", Color.LIGHT_GRAY));
    }

    /**
     * Adds a row and returns the panel width that keeps it on one line.
     */
    private int addLine(FontMetrics metrics, String left, String right, Color color)
    {
        panelComponent.getChildren().add(LineComponent.builder()
            .left(left)
            .leftColor(color)
            .right(right)
            .rightColor(color)
            .build());
        return metrics.stringWidth(left) + metrics.stringWidth(right) + 4 * ComponentConstants.STANDARD_BORDER;
    }
}