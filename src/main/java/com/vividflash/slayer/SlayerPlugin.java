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
package com.vividflash.slayer;

import com.google.inject.Provides;
import com.vividflash.slayer.features.Feature;
import com.vividflash.slayer.features.MasterRulesFeature;
import com.vividflash.slayer.features.NieveInsteadOfSteveFeature;
import com.vividflash.slayer.features.TaskChoiceOddsFeature;
import com.vividflash.slayer.features.TaskSorterFeature;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
    name = "Better Slayer",
    description = "Mortimer task-choice unique odds, per-task slayer master rules, slayer task list sorting, Nieve restored",
    tags = {"slayer", "nieve", "mortimer", "task", "boost"}
)
public class SlayerPlugin extends Plugin
{
    private static final String CONFIG_GROUP = "vividflashslayer";
    private static final String LAST_SEEN_VERSION_KEY = "lastSeenVersion";

    /** Keep in sync with build.gradle and runelite-plugin.properties on every release. */
    private static final String VERSION = "1.2";
    private static final String UPDATE_MESSAGE =
        "Patch 1.1+1.2; Mortimer fixed, Dropdown for Slayer items in settings, "
            + "Gravestone updated, gl on the heart grind";

    /** Dark red for the one-time notice, legible on either chatbox background. */
    private static final Color UPDATE_MESSAGE_COLOR = new Color(0x8B0000);

    @Inject
    private ConfigManager configManager;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private NieveInsteadOfSteveFeature nieveInsteadOfSteveFeature;

    @Inject
    private MasterRulesFeature masterRulesFeature;

    @Inject
    private TaskSorterFeature taskSorterFeature;

    @Inject
    private TaskChoiceOddsFeature taskChoiceOddsFeature;

    private final List<Feature> features = new ArrayList<>();

    @Override
    protected void startUp()
    {
        features.add(nieveInsteadOfSteveFeature);
        features.add(masterRulesFeature);
        features.add(taskSorterFeature);
        features.add(taskChoiceOddsFeature);
        features.forEach(Feature::startUp);
    }

    @Override
    protected void shutDown()
    {
        features.forEach(Feature::shutDown);
        features.clear();
    }

    /**
     * Shows the update notice once, on the first login after the version
     * changes. The stored version gates it, so no per-login flag is needed.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN
            || VERSION.equals(configManager.getConfiguration(CONFIG_GROUP, LAST_SEEN_VERSION_KEY)))
        {
            return;
        }

        configManager.setConfiguration(CONFIG_GROUP, LAST_SEEN_VERSION_KEY, VERSION);
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(new ChatMessageBuilder()
                .append(UPDATE_MESSAGE_COLOR, UPDATE_MESSAGE)
                .build())
            .build());
    }

    @Provides
    SlayerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SlayerConfig.class);
    }
}
