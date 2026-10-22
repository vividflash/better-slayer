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
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ColorUtil;

/**
 * Posts the area a task is restricted to, which only Konar assigns. The
 * area comes from a server-synced varp resolved against the slayer area
 * table, so nothing is parsed from her dialogue.
 */
@Slf4j
@Singleton
public class TaskAreaFeature implements Feature
{
    /** Same green as the milestone reminder. */
    private static final Color AREA_MESSAGE_COLOR = new Color(0x00A000);

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private EventBus eventBus;

    @Inject
    private SlayerConfig config;

    /** Set while the varps sync after a login or hop, so a task taken earlier is not announced again. */
    private boolean loginSync;

    @Override
    public void startUp()
    {
        if (log.isDebugEnabled())
        {
            log.debug("[config] showTaskArea={}", config.showTaskArea());
        }
        eventBus.register(this);
    }

    @Override
    public void shutDown()
    {
        // Off the bus first, then the flag is reset on the client thread,
        // where the handlers read it.
        eventBus.unregister(this);
        clientThread.invoke(() -> loginSync = false);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGGING_IN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
        {
            loginSync = true;
            if (log.isDebugEnabled())
            {
                log.debug("[state] game state {}, area announce paused until next tick", state);
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        loginSync = false;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (event.getVarpId() != VarPlayerID.SLAYER_AREA)
        {
            return;
        }

        if (loginSync || !config.showTaskArea())
        {
            if (log.isDebugEnabled())
            {
                log.debug("[area] varp changed, not announced loginSync={} showTaskArea={}", loginSync, config.showTaskArea());
            }
            return;
        }

        String areaName = lookupArea();
        if (areaName == null)
        {
            if (log.isDebugEnabled())
            {
                log.debug("[area] varp changed to {}, no area name resolved", client.getVarpValue(VarPlayerID.SLAYER_AREA));
            }
            return;
        }

        if (log.isDebugEnabled())
        {
            log.debug("[area] announced id={} name={}", client.getVarpValue(VarPlayerID.SLAYER_AREA), areaName);
        }

        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "Task area: " + ColorUtil.wrapWithColorTag(areaName, AREA_MESSAGE_COLOR) + ".", null);
    }

    private String lookupArea()
    {
        int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
        if (areaId <= 0)
        {
            return null;
        }

        try
        {
            List<Integer> rows = client.getDBRowsByValue(
                DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
            if (rows.isEmpty())
            {
                return null;
            }

            Object[] field = client.getDBTableField(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0);
            return field.length > 0 && field[0] instanceof String ? (String) field[0] : null;
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }
}
