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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Outlines nearby slayer masters. The recommended one takes the "correct"
 * color and every other master the "wrong" color.
 */
@Singleton
public class MasterHighlightOverlay extends Overlay
{
    private final SlayerConfig config;
    private final MasterRulesFeature feature;

    @Inject
    MasterHighlightOverlay(SlayerConfig config, MasterRulesFeature feature)
    {
        this.config = config;
        this.feature = feature;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.masterRules())
        {
            return null;
        }

        SlayerMaster recommended = feature.getRecommendation().master;
        for (Map.Entry<NPC, SlayerMaster> entry : feature.getNearbyMasters().entrySet())
        {
            boolean correct = entry.getValue() == recommended;
            if (!correct && entry.getValue().hasSeparateStreak())
            {
                // Separate-streak masters are never "wrong". Their tasks
                // can't cost a normal-streak milestone.
                continue;
            }
            if (correct && !config.highlightCorrectMaster())
            {
                continue;
            }
            if (!correct && !config.highlightWrongMasters())
            {
                continue;
            }

            Shape hull = entry.getKey().getConvexHull();
            if (hull != null)
            {
                OverlayUtil.renderPolygon(graphics, hull,
                    correct ? config.correctMasterColor() : config.wrongMasterColor());
            }
        }

        return null;
    }
}
