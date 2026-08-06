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
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Shows the estimated slayer-unique odds for each task Mortimer is offering,
 * while his choice interface is open, and marks the best pick.
 */
@Singleton
public class TaskChoiceOddsOverlay extends OverlayPanel
{
    private final Client client;
    private final SlayerConfig config;
    private final TaskChoiceOddsFeature feature;

    @Inject
    TaskChoiceOddsOverlay(Client client, SlayerConfig config, TaskChoiceOddsFeature feature)
    {
        this.client = client;
        this.config = config;
        this.feature = feature;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.taskChoiceOdds() || feature.getChoices().isEmpty())
        {
            return null;
        }

        Widget content = client.getWidget(TaskChoiceOddsFeature.TASK_CHOICE_CONTENT);
        if (content == null || content.isHidden())
        {
            return null;
        }

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Unique odds per superior")
            .build());

        int best = feature.getBestIndex();
        for (int i = 0; i < feature.getChoices().size(); i++)
        {
            TaskChoiceOddsFeature.Choice choice = feature.getChoices().get(i);

            String odds;
            if (Double.isNaN(choice.uniquePerSuperior))
            {
                odds = "?";
            }
            else if (choice.uniquePerSuperior <= 0)
            {
                odds = "-";
            }
            else
            {
                odds = "1 in " + Math.round(1 / choice.uniquePerSuperior);
            }
            if (choice.uniqueModifierPercent > 0)
            {
                odds += " (+" + choice.uniqueModifierPercent + "%)";
            }

            panelComponent.getChildren().add(LineComponent.builder()
                .left(choice.name)
                .leftColor(i == best ? Color.GREEN : Color.WHITE)
                .right(odds)
                .rightColor(i == best ? Color.GREEN : Color.WHITE)
                .build());
        }

        return super.render(graphics);
    }
}
