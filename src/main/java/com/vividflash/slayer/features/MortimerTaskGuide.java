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

import java.util.HashMap;
import java.util.Map;

/**
 * Where each task Mortimer assigns is usually done, how to get there and the
 * method the kills per hour in {@link MortimerTaskSpeed} assume, keyed by the
 * slayer task id his offer varbits carry.
 */
final class MortimerTaskGuide
{
    static final class Entry
    {
        final String area;
        final String travel;
        final String method;

        Entry(String area, String travel, String method)
        {
            this.area = area;
            this.travel = travel;
            this.method = method;
        }
    }

    /** Key of the basilisk knight entry, which is no task of its own. */
    static final int BASILISK_KNIGHTS = -43;

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();

    static
    {
        put(39, "Slayer Tower", "Slayer Ring", "Blowpipe"); // crawling hands
        put(37, "Fremennik Slayer Dungeon", "Slayer Ring", "Blowpipe"); // cave crawlers
        put(38, "Slayer Tower", "Slayer Ring", "Blowpipe"); // banshees
        put(51, "Fremennik Slayer Dungeon", "Fairy Ring AJR", "Blowpipe / Brine Sabre"); // rockslugs
        put(44, "Fremennik Slayer Dungeon", "Fairy Ring AJR", "Ranged / Melee"); // cockatrice
        put(47, "Fremennik Slayer Dungeon", "Fairy Ring AJR", "Blowpipe"); // pyrefiends
        put(40, "Slayer Tower", "Slayer Ring", "Blowpipe"); // infernal mages
        put(43, "Fremennik Slayer Dungeon", "Slayer Ring", "Melee (Crush)"); // basilisks
        put(BASILISK_KNIGHTS, "Jormungand's Prison", "Fremennik Boots Tele, Isle of Stone", "Ranged"); // basilisk knights
        put(48, "Meiyerditch Laboratories", "Drakan's Medallion", "Cannon / Venator Bow"); // bloodveld
        put(128, "Eastern Gryphon Dungeon", "Fairy Ring CJQ", "Cannon"); // gryphons
        put(50, "Catacombs of Kourend", "Xeric's Talisman: Heart", "Barrage"); // jellies
        put(126, "Stalker Den", "Fairy Ring AIS", "Cannon + Barrage"); // custodian stalkers
        put(36, "Fremennik Slayer Dungeon", "Fairy Ring AJR", "Leaf Bladed"); // turoth
        put(122, "Poison Waste Dungeon", "Spirit Tree", "Cannon"); // warped creatures
        put(80, "Mos Le'Harmless Cave", "Trouble Brewing Tele, North Shortcut", "Cannon"); // cave horrors
        put(41, "Stronghold Slayer Cave", "Slayer Ring", "Cannon"); // aberrant spectres
        put(111, "Wyrmscraig", "Next to Mortimer", "Cannon / Venator Bow"); // wyrms
        put(49, "Catacombs of Kourend", "Xeric's Talisman: Heart", "Barrage"); // dust devils
        put(45, "Fremennik Slayer Dungeon", "Fairy Ring AJR", "Leaf Bladed"); // kurask
        // The ring sits on the route line: it is the best ring here and no other task needs it.
        put(131, "Vampyrium Hunting Grounds", "Drakan's Medallion, Portal, Bring Efaritay's Aid", "Sunspear"); // venators
        put(46, "Slayer Tower Basement", "Slayer Ring", "Granite Hammer"); // gargoyles
        put(129, "Ynysdail Cavern", "Prifddinas Tele, Boat North", "Ranged"); // aquanites
        put(52, "Catacombs of Kourend", "Xeric's Talisman: Heart", "Barrage"); // nechryael
        put(112, "Karuulm Slayer Dungeon", "Fairy Ring CIR", "Cannon"); // drakes
        put(42, "Catacombs of Kourend", "Xeric's Talisman: Heart", "Barrage"); // abyssal demons
        put(66, "Mourner Tunnels", "Slayer Ring", "Cannon"); // dark beasts
        put(124, "Morytania Spider Cave", "Spider Cave Tele", "Cannon + Barrage"); // araxytes
        put(95, "Smoke Devil Dungeon", "Fairy Ring BKP", "Cannon + Barrage"); // smoke devils
        put(113, "Karuulm Slayer Dungeon", "Fairy Ring CIR", "Cannon"); // hydras
    }

    private MortimerTaskGuide()
    {
    }

    private static void put(int taskId, String area, String travel, String method)
    {
        ENTRIES.put(taskId, new Entry(area, travel, method));
    }

    /** The entry for a task, or null when this table has none. */
    static Entry forTask(int taskId)
    {
        return ENTRIES.get(taskId);
    }
}