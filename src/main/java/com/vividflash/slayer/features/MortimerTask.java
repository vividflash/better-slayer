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
import java.util.function.ToIntFunction;

/**
 * Each task Mortimer assigns, keyed by the slayer task id his offer varbits
 * carry: the estimated kills per hour at the task's quickest usual method,
 * whether it is a multicombat task, the config entry where a player can change
 * the speed, and where the task is usually done, how to get there and the
 * method the kills per hour assume. Each speed is the default of its own config
 * entry.
 */
public enum MortimerTask
{
    CRAWLING_HANDS(39, 1500, false, SlayerConfig::kphCrawlingHands,
        "Slayer Tower", "Slayer Ring", "Blowpipe"),
    CAVE_CRAWLERS(37, 1300, false, SlayerConfig::kphCaveCrawlers,
        "Fremennik Slayer Dungeon", "Slayer Ring", "Blowpipe"),
    BANSHEES(38, 1300, false, SlayerConfig::kphBanshees,
        "Slayer Tower", "Slayer Ring", "Blowpipe"),
    ROCKSLUGS(51, 600, false, SlayerConfig::kphRockslugs,
        "Fremennik Slayer Dungeon", "Fairy Ring AJR", "Blowpipe / Brine Sabre"),
    COCKATRICE(44, 770, false, SlayerConfig::kphCockatrice,
        "Fremennik Slayer Dungeon", "Fairy Ring AJR", "Ranged / Melee"),
    PYREFIENDS(47, 630, false, SlayerConfig::kphPyrefiends,
        "Fremennik Slayer Dungeon", "Fairy Ring AJR", "Blowpipe"),
    INFERNAL_MAGES(40, 475, false, SlayerConfig::kphInfernalMages,
        "Slayer Tower", "Slayer Ring", "Blowpipe"),
    BASILISKS(43, 200, false, SlayerConfig::kphBasilisks,
        "Fremennik Slayer Dungeon", "Slayer Ring", "Melee (Crush)"),
    /** No task of its own: a basilisk task spent on basilisk knights, keyed apart from it. */
    BASILISK_KNIGHTS(-43, 92, false, SlayerConfig::kphBasiliskKnights,
        "Jormungand's Prison", "Fremennik Boots Tele, Isle of Stone", "Ranged"),
    BLOODVELD(48, 700, true, SlayerConfig::kphBloodveld,
        "Meiyerditch Laboratories", "Drakan's Medallion", "Cannon / Venator Bow"),
    GRYPHONS(128, 455, true, SlayerConfig::kphGryphons,
        "Eastern Gryphon Dungeon", "Fairy Ring CJQ", "Cannon"),
    JELLIES(50, 400, true, SlayerConfig::kphJellies,
        "Catacombs of Kourend", "Xeric's Talisman: Heart", "Barrage"),
    CUSTODIAN_STALKERS(126, 400, true, SlayerConfig::kphCustodianStalkers,
        "Stalker Den", "Fairy Ring AIS", "Cannon + Barrage"),
    TUROTH(36, 183, false, SlayerConfig::kphTuroth,
        "Fremennik Slayer Dungeon", "Fairy Ring AJR", "Leaf Bladed"),
    WARPED_CREATURES(122, 365, true, SlayerConfig::kphWarpedCreatures,
        "Poison Waste Dungeon", "Spirit Tree", "Cannon"),
    CAVE_HORRORS(80, 455, false, SlayerConfig::kphCaveHorrors,
        "Mos Le'Harmless Cave", "Trouble Brewing Tele, North Shortcut", "Cannon"),
    ABERRANT_SPECTRES(41, 390, false, SlayerConfig::kphAberrantSpectres,
        "Stronghold Slayer Cave", "Slayer Ring", "Cannon"),
    WYRMS(111, 815, true, SlayerConfig::kphWyrms,
        "Wyrmscraig", "Next to Mortimer", "Cannon / Venator Bow"),
    DUST_DEVILS(49, 800, true, SlayerConfig::kphDustDevils,
        "Catacombs of Kourend", "Xeric's Talisman: Heart", "Barrage"),
    KURASK(45, 180, false, SlayerConfig::kphKurask,
        "Fremennik Slayer Dungeon", "Fairy Ring AJR", "Leaf Bladed"),
    // The ring sits on the route line: it is the best ring here and no other task needs it.
    VENATORS(131, 85, false, SlayerConfig::kphVenators,
        "Vampyrium Hunting Grounds", "Drakan's Medallion, Portal, Bring Efaritay's Aid", "Sunspear"),
    GARGOYLES(46, 285, false, SlayerConfig::kphGargoyles,
        "Slayer Tower Basement", "Slayer Ring", "Granite Hammer"),
    AQUANITES(129, 165, false, SlayerConfig::kphAquanites,
        "Ynysdail Cavern", "Prifddinas Tele, Boat North", "Ranged"),
    NECHRYAEL(52, 475, true, SlayerConfig::kphNechryael,
        "Catacombs of Kourend", "Xeric's Talisman: Heart", "Barrage"),
    DRAKES(112, 150, false, SlayerConfig::kphDrakes,
        "Karuulm Slayer Dungeon", "Fairy Ring CIR", "Cannon"),
    ABYSSAL_DEMONS(42, 535, true, SlayerConfig::kphAbyssalDemons,
        "Catacombs of Kourend", "Xeric's Talisman: Heart", "Barrage"),
    DARK_BEASTS(66, 190, false, SlayerConfig::kphDarkBeasts,
        "Mourner Tunnels", "Slayer Ring", "Cannon"),
    ARAXYTES(124, 600, true, SlayerConfig::kphAraxytes,
        "Morytania Spider Cave", "Spider Cave Tele", "Cannon + Barrage"),
    SMOKE_DEVILS(95, 810, true, SlayerConfig::kphSmokeDevils,
        "Smoke Devil Dungeon", "Fairy Ring BKP", "Cannon + Barrage"),
    HYDRAS(113, 100, false, SlayerConfig::kphHydras,
        "Karuulm Slayer Dungeon", "Fairy Ring CIR", "Cannon");

    /** Used for a task this table has no entry for. */
    static final int DEFAULT_KILLS_PER_HOUR = 300;

    /**
     * Long-run unique table rolls per hour with three choices, worked out from
     * the speeds above at the default overhead by {@link TaskPickScorer#baseline}.
     */
    static final double BASELINE = 0.04587723422611158;

    private static final Map<Integer, MortimerTask> BY_ID = new HashMap<>();

    static
    {
        for (MortimerTask task : values())
        {
            BY_ID.put(task.id, task);
        }
    }

    final int id;
    /** The bundled kills per hour, the default of the task's config entry. */
    public final int defaultKph;
    /**
     * One of the multicombat tasks, quick through burst and barrage spells, the
     * venator bow or a cannon. Only these are taken over a shorter task when
     * boosted.
     */
    final boolean fast;
    final ToIntFunction<SlayerConfig> configKph;
    final String area;
    final String travel;
    final String method;

    MortimerTask(int id, int defaultKph, boolean fast, ToIntFunction<SlayerConfig> configKph,
        String area, String travel, String method)
    {
        this.id = id;
        this.defaultKph = defaultKph;
        this.fast = fast;
        this.configKph = configKph;
        this.area = area;
        this.travel = travel;
        this.method = method;
    }

    /** The task with this id, or null when this table has none. */
    static MortimerTask forId(int taskId)
    {
        return BY_ID.get(taskId);
    }

    static boolean isFastTask(int taskId)
    {
        MortimerTask task = forId(taskId);
        return task != null && task.fast;
    }

    /** A task's kills per hour as set in the config, which starts out at the bundled figure. */
    static int killsPerHour(int taskId, SlayerConfig config)
    {
        MortimerTask task = forId(taskId);
        return task == null ? DEFAULT_KILLS_PER_HOUR : task.configKph.applyAsInt(config);
    }
}
