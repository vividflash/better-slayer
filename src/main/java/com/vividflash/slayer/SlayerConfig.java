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

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("vividflashslayer")
public interface SlayerConfig extends Config
{
    @ConfigSection(
        name = "Slayer Master Rules",
        description = "",
        position = 30,
        closedByDefault = true
    )
    String masterRulesSection = "masterRulesSection";

    @ConfigSection(
        name = "Task Sorter",
        description = "",
        position = 60
    )
    String taskSorterSection = "taskSorterSection";

    @ConfigSection(
        name = "Mortimer Task Choice",
        description = "",
        position = 10
    )
    String taskChoiceSection = "taskChoiceSection";

    @ConfigSection(
        name = "Mortimer Task Speeds",
        description = "Kill speeds the pick is worked out from.",
        position = 20,
        closedByDefault = true
    )
    String taskSpeedSection = "taskSpeedSection";

    @ConfigItem(
        keyName = "taskChoiceOddsDisplay",
        name = "Show odds",
        description = "Panel: each option's odds and estimated time. Highlight: colors the picked option in his list.",
        section = taskChoiceSection,
        position = 10
    )
    default TaskChoiceDisplay taskChoiceOddsDisplay()
    {
        return TaskChoiceDisplay.BOTH;
    }

    @ConfigItem(
        keyName = "taskChoiceOddsMode",
        name = "Odds for",
        description = "Slayer unique roll counts every roll of a unique table. Unique item leaves out the nothing outcome.",
        section = taskChoiceSection,
        position = 20
    )
    default UniqueOddsMode taskChoiceOddsMode()
    {
        return UniqueOddsMode.HEART_OR_GEM;
    }

    @ConfigItem(
        keyName = "taskChoicePickMode",
        name = "Pick",
        description = "Fast + Best: fastest, unless a boosted multicombat task is worth more per hour. Balanced: most unique rolls per hour. Max chance: best odds per superior. Auto: Fast + Best until 50 Mortimer tasks, then Balanced.",
        section = taskChoiceSection,
        position = 30
    )
    default TaskPickMode taskChoicePickMode()
    {
        return TaskPickMode.AUTO;
    }

    @ConfigItem(
        keyName = "taskChoiceShowAreas",
        name = "Show task areas",
        description = "Best area, fastest way there and kill method for each offered task.",
        section = taskChoiceSection,
        position = 40
    )
    default boolean taskChoiceShowAreas()
    {
        return true;
    }

    @ConfigItem(
        keyName = "taskChoiceAreaRetain",
        name = "Retain area duration",
        description = "Counted from when you leave Mortimer.",
        section = taskChoiceSection,
        position = 50
    )
    @Range(min = 10)
    @Units(Units.SECONDS)
    default int taskChoiceAreaRetain()
    {
        return 180;
    }

    @ConfigItem(
        keyName = "taskChoiceOverhead",
        name = "Overhead per task",
        description = "Time added per task for travel, banking and getting the next one.",
        section = taskSpeedSection,
        position = 10
    )
    @Range(min = 0)
    @Units(Units.MINUTES)
    default int taskChoiceOverhead()
    {
        return 2;
    }

    @ConfigItem(
        keyName = "kphCrawlingHands",
        name = "Crawling Hands",
        description = "",
        section = taskSpeedSection,
        position = 20
    )
    @Range(min = 1)
    @Units("/h")
    default int kphCrawlingHands()
    {
        return 1500;
    }

    @ConfigItem(
        keyName = "kphCaveCrawlers",
        name = "Cave Crawlers",
        description = "",
        section = taskSpeedSection,
        position = 30
    )
    @Range(min = 1)
    @Units("/h")
    default int kphCaveCrawlers()
    {
        return 1300;
    }

    @ConfigItem(
        keyName = "kphBanshees",
        name = "Banshees",
        description = "",
        section = taskSpeedSection,
        position = 40
    )
    @Range(min = 1)
    @Units("/h")
    default int kphBanshees()
    {
        return 1300;
    }

    @ConfigItem(
        keyName = "kphRockslugs",
        name = "Rockslugs",
        description = "",
        section = taskSpeedSection,
        position = 50
    )
    @Range(min = 1)
    @Units("/h")
    default int kphRockslugs()
    {
        return 600;
    }

    @ConfigItem(
        keyName = "kphCockatrice",
        name = "Cockatrice",
        description = "",
        section = taskSpeedSection,
        position = 60
    )
    @Range(min = 1)
    @Units("/h")
    default int kphCockatrice()
    {
        return 770;
    }

    @ConfigItem(
        keyName = "kphPyrefiends",
        name = "Pyrefiends",
        description = "",
        section = taskSpeedSection,
        position = 70
    )
    @Range(min = 1)
    @Units("/h")
    default int kphPyrefiends()
    {
        return 630;
    }

    @ConfigItem(
        keyName = "kphInfernalMages",
        name = "Infernal Mages",
        description = "",
        section = taskSpeedSection,
        position = 80
    )
    @Range(min = 1)
    @Units("/h")
    default int kphInfernalMages()
    {
        return 475;
    }

    @ConfigItem(
        keyName = "kphBasilisks",
        name = "Basilisks",
        description = "",
        section = taskSpeedSection,
        position = 90
    )
    @Range(min = 1)
    @Units("/h")
    default int kphBasilisks()
    {
        return 200;
    }

    @ConfigItem(
        keyName = "kphBasiliskKnights",
        name = "Basilisk Knights",
        description = "",
        section = taskSpeedSection,
        position = 100
    )
    @Range(min = 1)
    @Units("/h")
    default int kphBasiliskKnights()
    {
        return 92;
    }

    @ConfigItem(
        keyName = "basiliskKnightsForPick",
        name = "Pick by Basilisk Knights",
        description = "",
        section = taskSpeedSection,
        position = 110
    )
    default boolean basiliskKnightsForPick()
    {
        return false;
    }

    @ConfigItem(
        keyName = "kphBloodveld",
        name = "Bloodveld",
        description = "",
        section = taskSpeedSection,
        position = 120
    )
    @Range(min = 1)
    @Units("/h")
    default int kphBloodveld()
    {
        return 700;
    }

    @ConfigItem(
        keyName = "kphGryphons",
        name = "Gryphons",
        description = "",
        section = taskSpeedSection,
        position = 130
    )
    @Range(min = 1)
    @Units("/h")
    default int kphGryphons()
    {
        return 455;
    }

    @ConfigItem(
        keyName = "kphJellies",
        name = "Jellies",
        description = "",
        section = taskSpeedSection,
        position = 140
    )
    @Range(min = 1)
    @Units("/h")
    default int kphJellies()
    {
        return 400;
    }

    @ConfigItem(
        keyName = "kphCustodianStalkers",
        name = "Custodian Stalkers",
        description = "",
        section = taskSpeedSection,
        position = 150
    )
    @Range(min = 1)
    @Units("/h")
    default int kphCustodianStalkers()
    {
        return 400;
    }

    @ConfigItem(
        keyName = "kphTuroth",
        name = "Turoth",
        description = "",
        section = taskSpeedSection,
        position = 160
    )
    @Range(min = 1)
    @Units("/h")
    default int kphTuroth()
    {
        return 183;
    }

    @ConfigItem(
        keyName = "kphWarpedCreatures",
        name = "Warped Creatures",
        description = "",
        section = taskSpeedSection,
        position = 170
    )
    @Range(min = 1)
    @Units("/h")
    default int kphWarpedCreatures()
    {
        return 365;
    }

    @ConfigItem(
        keyName = "kphCaveHorrors",
        name = "Cave Horrors",
        description = "",
        section = taskSpeedSection,
        position = 180
    )
    @Range(min = 1)
    @Units("/h")
    default int kphCaveHorrors()
    {
        return 455;
    }

    @ConfigItem(
        keyName = "kphAberrantSpectres",
        name = "Aberrant Spectres",
        description = "",
        section = taskSpeedSection,
        position = 190
    )
    @Range(min = 1)
    @Units("/h")
    default int kphAberrantSpectres()
    {
        return 390;
    }

    @ConfigItem(
        keyName = "kphWyrms",
        name = "Wyrms",
        description = "",
        section = taskSpeedSection,
        position = 200
    )
    @Range(min = 1)
    @Units("/h")
    default int kphWyrms()
    {
        return 815;
    }

    @ConfigItem(
        keyName = "kphDustDevils",
        name = "Dust Devils",
        description = "",
        section = taskSpeedSection,
        position = 210
    )
    @Range(min = 1)
    @Units("/h")
    default int kphDustDevils()
    {
        return 800;
    }

    @ConfigItem(
        keyName = "kphKurask",
        name = "Kurask",
        description = "",
        section = taskSpeedSection,
        position = 220
    )
    @Range(min = 1)
    @Units("/h")
    default int kphKurask()
    {
        return 180;
    }

    @ConfigItem(
        keyName = "kphVenators",
        name = "Venators",
        description = "",
        section = taskSpeedSection,
        position = 230
    )
    @Range(min = 1)
    @Units("/h")
    default int kphVenators()
    {
        return 85;
    }

    @ConfigItem(
        keyName = "kphGargoyles",
        name = "Gargoyles",
        description = "",
        section = taskSpeedSection,
        position = 240
    )
    @Range(min = 1)
    @Units("/h")
    default int kphGargoyles()
    {
        return 285;
    }

    @ConfigItem(
        keyName = "kphAquanites",
        name = "Aquanites",
        description = "",
        section = taskSpeedSection,
        position = 250
    )
    @Range(min = 1)
    @Units("/h")
    default int kphAquanites()
    {
        return 165;
    }

    @ConfigItem(
        keyName = "kphNechryael",
        name = "Nechryael",
        description = "",
        section = taskSpeedSection,
        position = 260
    )
    @Range(min = 1)
    @Units("/h")
    default int kphNechryael()
    {
        return 475;
    }

    @ConfigItem(
        keyName = "kphDrakes",
        name = "Drakes",
        description = "",
        section = taskSpeedSection,
        position = 270
    )
    @Range(min = 1)
    @Units("/h")
    default int kphDrakes()
    {
        return 150;
    }

    @ConfigItem(
        keyName = "kphAbyssalDemons",
        name = "Abyssal Demons",
        description = "",
        section = taskSpeedSection,
        position = 280
    )
    @Range(min = 1)
    @Units("/h")
    default int kphAbyssalDemons()
    {
        return 535;
    }

    @ConfigItem(
        keyName = "kphDarkBeasts",
        name = "Dark Beasts",
        description = "",
        section = taskSpeedSection,
        position = 290
    )
    @Range(min = 1)
    @Units("/h")
    default int kphDarkBeasts()
    {
        return 190;
    }

    @ConfigItem(
        keyName = "kphAraxytes",
        name = "Araxytes",
        description = "",
        section = taskSpeedSection,
        position = 300
    )
    @Range(min = 1)
    @Units("/h")
    default int kphAraxytes()
    {
        return 600;
    }

    @ConfigItem(
        keyName = "kphSmokeDevils",
        name = "Smoke Devils",
        description = "",
        section = taskSpeedSection,
        position = 310
    )
    @Range(min = 1)
    @Units("/h")
    default int kphSmokeDevils()
    {
        return 810;
    }

    @ConfigItem(
        keyName = "kphHydras",
        name = "Hydras",
        description = "",
        section = taskSpeedSection,
        position = 320
    )
    @Range(min = 1)
    @Units("/h")
    default int kphHydras()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "taskChoiceRecalculate",
        name = "Save and recalculate",
        description = "Recalculates the average that Balanced and Fast + Best compare against.",
        section = taskSpeedSection,
        position = 330
    )
    default boolean taskChoiceRecalculate()
    {
        return false;
    }

    @ConfigItem(
        keyName = "nieve",
        name = "Replace Steve with Nieve",
        description = "Model, name, chathead, menu entries and dialogue.",
        position = 50
    )
    default boolean nieve()
    {
        return true;
    }

    @ConfigItem(
        keyName = "masterRules",
        name = "Enable master rules",
        description = "",
        section = masterRulesSection,
        position = 10
    )
    default boolean masterRules()
    {
        return true;
    }

    @ConfigItem(
        keyName = "defaultMaster",
        name = "Default master",
        description = "Used when no rule matches.",
        section = masterRulesSection,
        position = 20
    )
    default DefaultMaster defaultMaster()
    {
        return DefaultMaster.MAZCHNA;
    }

    @ConfigItem(
        keyName = "rule1Enabled",
        name = "Rule 1",
        description = "",
        section = masterRulesSection,
        position = 30
    )
    default boolean rule1Enabled()
    {
        return true;
    }

    @Range(min = 1)
    @ConfigItem(
        keyName = "rule1Interval",
        name = "Rule 1: every Xth task",
        description = "",
        section = masterRulesSection,
        position = 40
    )
    default int rule1Interval()
    {
        return 10;
    }

    @ConfigItem(
        keyName = "rule1Master",
        name = "Rule 1: use master",
        description = "",
        section = masterRulesSection,
        position = 50
    )
    default RuleMaster rule1Master()
    {
        return RuleMaster.DURADEL;
    }

    @ConfigItem(
        keyName = "rule2Enabled",
        name = "Rule 2",
        description = "",
        section = masterRulesSection,
        position = 60
    )
    default boolean rule2Enabled()
    {
        return true;
    }

    @Range(min = 1)
    @ConfigItem(
        keyName = "rule2Interval",
        name = "Rule 2: every Xth task",
        description = "",
        section = masterRulesSection,
        position = 70
    )
    default int rule2Interval()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "rule2Master",
        name = "Rule 2: use master",
        description = "",
        section = masterRulesSection,
        position = 80
    )
    default RuleMaster rule2Master()
    {
        return RuleMaster.KONAR;
    }

    @ConfigItem(
        keyName = "rule3Enabled",
        name = "Rule 3",
        description = "",
        section = masterRulesSection,
        position = 90
    )
    default boolean rule3Enabled()
    {
        return false;
    }

    @Range(min = 1)
    @ConfigItem(
        keyName = "rule3Interval",
        name = "Rule 3: every Xth task",
        description = "",
        section = masterRulesSection,
        position = 100
    )
    default int rule3Interval()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "rule3Master",
        name = "Rule 3: use master",
        description = "",
        section = masterRulesSection,
        position = 110
    )
    default RuleMaster rule3Master()
    {
        return RuleMaster.KONAR;
    }

    @ConfigItem(
        keyName = "rule4Enabled",
        name = "Rule 4",
        description = "",
        section = masterRulesSection,
        position = 120
    )
    default boolean rule4Enabled()
    {
        return false;
    }

    @Range(min = 1)
    @ConfigItem(
        keyName = "rule4Interval",
        name = "Rule 4: every Xth task",
        description = "",
        section = masterRulesSection,
        position = 130
    )
    default int rule4Interval()
    {
        return 250;
    }

    @ConfigItem(
        keyName = "rule4Master",
        name = "Rule 4: use master",
        description = "",
        section = masterRulesSection,
        position = 140
    )
    default RuleMaster rule4Master()
    {
        return RuleMaster.KONAR;
    }

    @ConfigItem(
        keyName = "rule5Enabled",
        name = "Rule 5",
        description = "",
        section = masterRulesSection,
        position = 150
    )
    default boolean rule5Enabled()
    {
        return false;
    }

    @Range(min = 1)
    @ConfigItem(
        keyName = "rule5Interval",
        name = "Rule 5: every Xth task",
        description = "",
        section = masterRulesSection,
        position = 160
    )
    default int rule5Interval()
    {
        return 1000;
    }

    @ConfigItem(
        keyName = "rule5Master",
        name = "Rule 5: use master",
        description = "",
        section = masterRulesSection,
        position = 170
    )
    default RuleMaster rule5Master()
    {
        return RuleMaster.KONAR;
    }

    @ConfigItem(
        keyName = "eliteWesternDiary",
        name = "Elite Western diary",
        description = "Nieve tasks pay 15 base points instead of 12.",
        section = masterRulesSection,
        position = 190
    )
    default boolean eliteWesternDiary()
    {
        return false;
    }

    @ConfigItem(
        keyName = "eliteKourendDiary",
        name = "Elite Kourend diary",
        description = "Konar tasks pay 20 base points instead of 18.",
        section = masterRulesSection,
        position = 180
    )
    default boolean eliteKourendDiary()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay near masters",
        description = "Next task number, selected master, points now and after the next task.",
        section = masterRulesSection,
        position = 200
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "hideOverlayDuringMortimerTask",
        name = "Hide overlay during Mortimer task",
        description = "",
        section = masterRulesSection,
        position = 210
    )
    default boolean hideOverlayDuringMortimerTask()
    {
        return false;
    }

    @ConfigItem(
        keyName = "highlightCorrectMaster",
        name = "Highlight correct master",
        description = "",
        section = masterRulesSection,
        position = 220
    )
    default boolean highlightCorrectMaster()
    {
        return true;
    }

    @ConfigItem(
        keyName = "correctMasterColor",
        name = "Correct master color",
        description = "",
        section = masterRulesSection,
        position = 230
    )
    default Color correctMasterColor()
    {
        return Color.GREEN;
    }

    @ConfigItem(
        keyName = "highlightWrongMasters",
        name = "Highlight wrong masters",
        description = "",
        section = masterRulesSection,
        position = 240
    )
    default boolean highlightWrongMasters()
    {
        return true;
    }

    @ConfigItem(
        keyName = "wrongMasterColor",
        name = "Wrong master color",
        description = "",
        section = masterRulesSection,
        position = 250
    )
    default Color wrongMasterColor()
    {
        return Color.RED;
    }

    @ConfigItem(
        keyName = "milestoneChatMessage",
        name = "Milestone chat reminder",
        description = "Chat message when your next task matches a rule.",
        section = masterRulesSection,
        position = 260
    )
    default boolean milestoneChatMessage()
    {
        return true;
    }

    @ConfigItem(
        keyName = "blockWrongMasters",
        name = "Block wrong masters",
        description = "Consumes Assignment on masters other than the selected one.",
        section = masterRulesSection,
        position = 270
    )
    default boolean blockWrongMasters()
    {
        return false;
    }

    @ConfigItem(
        keyName = "hideWrongMastersOnMilestone",
        name = "Hide wrong masters on milestone",
        description = "Removes Assignment from the other masters while a rule matches.",
        section = masterRulesSection,
        position = 280
    )
    default boolean hideWrongMastersOnMilestone()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showTaskArea",
        name = "Konar task area in chat",
        description = "",
        position = 40
    )
    default boolean showTaskArea()
    {
        return true;
    }

    @ConfigItem(
        keyName = "taskSorter",
        name = "Sort task list",
        description = "",
        section = taskSorterSection,
        position = 10
    )
    default boolean taskSorter()
    {
        return true;
    }

    @ConfigItem(
        keyName = "taskSortMethod",
        name = "Sort by",
        description = "Weight falls back to alphabetical when the list shows no odds.",
        section = taskSorterSection,
        position = 20
    )
    default TaskSortMethod taskSortMethod()
    {
        return TaskSortMethod.WEIGHT;
    }

    @ConfigItem(
        keyName = "taskSortReversed",
        name = "Reverse order",
        description = "",
        section = taskSorterSection,
        position = 30
    )
    default boolean taskSortReversed()
    {
        return false;
    }
}
