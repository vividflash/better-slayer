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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Estimated kills per hour for each task Mortimer assigns, at the task's
 * quickest usual method, keyed by the slayer task id his offer varbits carry.
 * Each is the default of its own config entry, where a player can change it.
 */
final class MortimerTaskSpeed
{
    /** Used for a task this table has no entry for. */
    static final int DEFAULT_KILLS_PER_HOUR = 300;

    /**
     * Long-run unique table rolls per hour with three choices, worked out from
     * the table below at the default overhead by {@link TaskPickScorer#baseline}.
     */
    static final double BASELINE = 0.045938708571972985;

    private static final Map<Integer, Integer> KILLS_PER_HOUR = new HashMap<>();

    static
    {
        KILLS_PER_HOUR.put(39, 1500); // crawling hands
        KILLS_PER_HOUR.put(37, 1300); // cave crawlers
        KILLS_PER_HOUR.put(38, 1300); // banshees
        KILLS_PER_HOUR.put(51, 600); // rockslugs
        KILLS_PER_HOUR.put(44, 770); // cockatrice
        KILLS_PER_HOUR.put(47, 630); // pyrefiends
        KILLS_PER_HOUR.put(40, 475); // infernal mages
        KILLS_PER_HOUR.put(43, 200); // basilisks
        KILLS_PER_HOUR.put(48, 700); // bloodveld
        KILLS_PER_HOUR.put(128, 455); // gryphons
        KILLS_PER_HOUR.put(50, 400); // jellies
        KILLS_PER_HOUR.put(126, 400); // custodian stalkers
        KILLS_PER_HOUR.put(36, 210); // turoth
        KILLS_PER_HOUR.put(122, 365); // warped creatures
        KILLS_PER_HOUR.put(80, 455); // cave horrors
        KILLS_PER_HOUR.put(41, 390); // aberrant spectres
        KILLS_PER_HOUR.put(111, 815); // wyrms
        KILLS_PER_HOUR.put(49, 800); // dust devils
        KILLS_PER_HOUR.put(45, 205); // kurask
        KILLS_PER_HOUR.put(131, 85); // venators
        KILLS_PER_HOUR.put(46, 285); // gargoyles
        KILLS_PER_HOUR.put(129, 165); // aquanites
        KILLS_PER_HOUR.put(52, 475); // nechryael
        KILLS_PER_HOUR.put(112, 150); // drakes
        KILLS_PER_HOUR.put(42, 535); // abyssal demons
        KILLS_PER_HOUR.put(66, 190); // dark beasts
        KILLS_PER_HOUR.put(124, 600); // araxytes
        KILLS_PER_HOUR.put(95, 810); // smoke devils
        KILLS_PER_HOUR.put(113, 100); // hydras
    }

    /**
     * The multicombat tasks, quick through burst and barrage spells, the
     * venator bow or a cannon. Only these are taken over a shorter task when
     * boosted.
     */
    private static final Set<Integer> FAST_TASKS = Set.of(
        48, 128, 50, 126, 122, 111, 49, 52, 42, 124, 95);

    private MortimerTaskSpeed()
    {
    }

    static boolean isFastTask(int taskId)
    {
        return FAST_TASKS.contains(taskId);
    }

    /** The bundled kills per hour for a task. */
    static int killsPerHour(int taskId)
    {
        return KILLS_PER_HOUR.getOrDefault(taskId, DEFAULT_KILLS_PER_HOUR);
    }

    /** A task's kills per hour as set in the config, which starts out at the bundled figure. */
    static int killsPerHour(int taskId, SlayerConfig config)
    {
        switch (taskId)
        {
            case 39:
                return config.kphCrawlingHands();
            case 37:
                return config.kphCaveCrawlers();
            case 38:
                return config.kphBanshees();
            case 51:
                return config.kphRockslugs();
            case 44:
                return config.kphCockatrice();
            case 47:
                return config.kphPyrefiends();
            case 40:
                return config.kphInfernalMages();
            case 43:
                return config.kphBasilisks();
            case 48:
                return config.kphBloodveld();
            case 128:
                return config.kphGryphons();
            case 50:
                return config.kphJellies();
            case 126:
                return config.kphCustodianStalkers();
            case 36:
                return config.kphTuroth();
            case 122:
                return config.kphWarpedCreatures();
            case 80:
                return config.kphCaveHorrors();
            case 41:
                return config.kphAberrantSpectres();
            case 111:
                return config.kphWyrms();
            case 49:
                return config.kphDustDevils();
            case 45:
                return config.kphKurask();
            case 131:
                return config.kphVenators();
            case 46:
                return config.kphGargoyles();
            case 129:
                return config.kphAquanites();
            case 52:
                return config.kphNechryael();
            case 112:
                return config.kphDrakes();
            case 42:
                return config.kphAbyssalDemons();
            case 66:
                return config.kphDarkBeasts();
            case 124:
                return config.kphAraxytes();
            case 95:
                return config.kphSmokeDevils();
            case 113:
                return config.kphHydras();
            default:
                return DEFAULT_KILLS_PER_HOUR;
        }
    }
}
