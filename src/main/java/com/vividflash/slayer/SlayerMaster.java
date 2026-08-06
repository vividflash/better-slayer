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

import net.runelite.api.NPC;
import net.runelite.api.gameval.NpcID;

/**
 * Slayer masters with their base reward points per completed task. The point
 * values feed the normal-streak recommendation math. They go unused for the
 * separate-streak masters, which no rule can target.
 */
public enum SlayerMaster
{
    TURAEL("Turael", 0),
    SPRIA("Spria", 0),
    AYA("Aya", 0),
    MAZCHNA("Mazchna", 6),
    ACHTRYN("Achtryn", 6),
    VANNAKA("Vannaka", 8),
    CHAELDAR("Chaeldar", 10),
    KONAR("Konar quo Maten", 18),
    NIEVE("Nieve", 12, "Steve"),
    DURADEL("Duradel", 15),
    KRYSTILIA("Krystilia", 25, true),
    MORTIMER("Mortimer", 0, true);

    private final String displayName;
    private final int basePoints;
    /**
     * True for masters whose tasks run on their own streak (Krystilia's
     * Wilderness streak, Mortimer's own task count); taking one never
     * consumes a normal-streak milestone, so the wrong-master guards must
     * leave them alone.
     */
    private final boolean separateStreak;
    private final String[] extraNpcNames;

    SlayerMaster(String displayName, int basePoints, String... extraNpcNames)
    {
        this(displayName, basePoints, false, extraNpcNames);
    }

    SlayerMaster(String displayName, int basePoints, boolean separateStreak, String... extraNpcNames)
    {
        this.displayName = displayName;
        this.basePoints = basePoints;
        this.separateStreak = separateStreak;
        this.extraNpcNames = extraNpcNames;
    }

    public boolean hasSeparateStreak()
    {
        return separateStreak;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * Base points per task, adjusted for the elite diary bonuses the player
     * has ticked in config. Western Provinces takes Nieve from 12 to 15, and
     * Kourend &amp; Kebos takes Konar from 18 to 20.
     */
    public int getBasePoints(boolean eliteWesternDiary, boolean eliteKourendDiary)
    {
        if (this == NIEVE && eliteWesternDiary)
        {
            return 15;
        }
        if (this == KONAR && eliteKourendDiary)
        {
            return 20;
        }
        return basePoints;
    }

    public boolean matchesNpcName(String npcName)
    {
        if (npcName == null)
        {
            return false;
        }
        if (displayName.equalsIgnoreCase(npcName))
        {
            return true;
        }
        for (String extra : extraNpcNames)
        {
            if (extra.equalsIgnoreCase(npcName))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Mortimer is matched by id, everyone else by name.
     */
    public static SlayerMaster forNpc(NPC npc)
    {
        if (npc == null)
        {
            return null;
        }
        int id = npc.getId();
        if (id == NpcID.SLAYER_MASTER_MORTIMER || id == NpcID.SLAYER_MASTER_MORTIMER_VIS)
        {
            return MORTIMER;
        }
        return forNpc(npc.getName());
    }

    public static SlayerMaster forNpc(String npcName)
    {
        for (SlayerMaster master : values())
        {
            if (master.matchesNpcName(npcName))
            {
                return master;
            }
        }
        return null;
    }

    /**
     * Milestone multiplier for a given task number. Every 1000th task pays
     * 50x base, 250th 35x, 100th 25x, 50th 15x and 10th 5x, with the highest
     * applicable one winning, otherwise 1x. No points at all before the
     * fifth task.
     */
    public static int milestoneMultiplier(int taskNumber)
    {
        if (taskNumber % 1000 == 0)
        {
            return 50;
        }
        if (taskNumber % 250 == 0)
        {
            return 35;
        }
        if (taskNumber % 100 == 0)
        {
            return 25;
        }
        if (taskNumber % 50 == 0)
        {
            return 15;
        }
        if (taskNumber % 10 == 0)
        {
            return 5;
        }
        return 1;
    }

    public int getPointsForTask(int taskNumber, boolean eliteWesternDiary, boolean eliteKourendDiary)
    {
        if (taskNumber < 5)
        {
            return 0;
        }
        return getBasePoints(eliteWesternDiary, eliteKourendDiary) * milestoneMultiplier(taskNumber);
    }
}
