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
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Point-projection panel shown while a slayer master is nearby. Its four
 * lines are the next task number, which master to take it from, current
 * points, and points after the next task.
 */
@Singleton
public class MasterRulesOverlay extends OverlayPanel
{
    private final SlayerConfig config;
    private final MasterRulesFeature feature;

    @Inject
    MasterRulesOverlay(SlayerConfig config, MasterRulesFeature feature)
    {
        this.config = config;
        this.feature = feature;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.masterRules() || !config.showOverlay() || feature.getNearbyMasters().isEmpty())
        {
            return null;
        }

        MasterRulesFeature.Recommendation recommendation = feature.getRecommendation();
        int points = feature.getCurrentPoints();
        int gain = feature.getProjectedGain();

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Slayer Masters")
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Next task:")
            .right("#" + feature.getNextTaskNumber())
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Take from:")
            .right(recommendation.master.getDisplayName())
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Points:")
            .right(Integer.toString(points))
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("After next:")
            .right(points + gain + " (+" + gain + ")")
            .build());

        return super.render(graphics);
    }
}
