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
        name = "Nieve instead of Steve",
        description = "Nieve restoration tweaks.",
        position = 4
    )
    String nieveSection = "nieveSection";

    @ConfigSection(
        name = "Master Rules",
        description = "Which slayer master to use for which task number, with milestone reminders and point projections.",
        position = 2
    )
    String masterRulesSection = "masterRulesSection";

    @ConfigSection(
        name = "Task Sorter",
        description = "Sorting for the slayer task list interface.",
        position = 3
    )
    String taskSorterSection = "taskSorterSection";

    @ConfigSection(
        name = "Task Choice",
        description = "Helpers for Mortimer's task choice.",
        position = 0
    )
    String taskChoiceSection = "taskChoiceSection";

    @ConfigSection(
        name = "Task Choice speeds",
        description = "Kill speeds the task choice pick is worked out from.",
        position = 1,
        closedByDefault = true
    )
    String taskSpeedSection = "taskSpeedSection";

    @ConfigItem(
        keyName = "taskChoiceOddsDisplay",
        name = "Show slayer-unique odds",
        description = "While Mortimer offers tasks, show what each option is worth per superior. The panel lists every option with its estimated time, the highlight colors the picked option's name in his list.",
        section = taskChoiceSection,
        position = 0
    )
    default TaskChoiceDisplay taskChoiceOddsDisplay()
    {
        return TaskChoiceDisplay.BOTH;
    }

    @ConfigItem(
        keyName = "taskChoiceOddsMode",
        name = "Odds for",
        description = "Which superior drop the odds are quoted for. A slayer unique roll counts every roll of either unique table, a unique item counts everything but its nothing outcome. The best pick is the same under all four.",
        section = taskChoiceSection,
        position = 1
    )
    default UniqueOddsMode taskChoiceOddsMode()
    {
        return UniqueOddsMode.HEART_OR_GEM;
    }

    @ConfigItem(
        keyName = "taskChoicePickMode",
        name = "Pick",
        description = "Fastest: the shortest task. Fast + Best: the shortest, unless a boosted multicombat task is worth more per hour. Balanced: the most unique rolls per hour. Max chance: the best odds per superior. Auto: Fast + Best until 50 Mortimer tasks, Balanced after.",
        section = taskChoiceSection,
        position = 2
    )
    default TaskPickMode taskChoicePickMode()
    {
        return TaskPickMode.AUTO;
    }

    @ConfigItem(
        keyName = "taskChoiceOverhead",
        name = "Overhead per task",
        description = "Time added to every task for travel, banking and getting the next one.",
        section = taskSpeedSection,
        position = 0
    )
    @Range(min = 0)
    @Units(Units.MINUTES)
    default int taskChoiceOverhead()
    {
        return 2;
    }

    @ConfigItem(
        keyName = "kphCrawlingHands",
        name = "Crawling hands",
        description = "",
        section = taskSpeedSection,
        position = 1
    )
    @Range(min = 1)
    @Units("/h")
    default int kphCrawlingHands()
    {
        return 1500;
    }

    @ConfigItem(
        keyName = "kphCaveCrawlers",
        name = "Cave crawlers",
        description = "",
        section = taskSpeedSection,
        position = 2
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
        position = 3
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
        position = 4
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
        position = 5
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
        position = 6
    )
    @Range(min = 1)
    @Units("/h")
    default int kphPyrefiends()
    {
        return 630;
    }

    @ConfigItem(
        keyName = "kphInfernalMages",
        name = "Infernal mages",
        description = "",
        section = taskSpeedSection,
        position = 7
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
        position = 8
    )
    @Range(min = 1)
    @Units("/h")
    default int kphBasilisks()
    {
        return 200;
    }

    @ConfigItem(
        keyName = "kphBloodveld",
        name = "Bloodveld",
        description = "",
        section = taskSpeedSection,
        position = 9
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
        position = 10
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
        position = 11
    )
    @Range(min = 1)
    @Units("/h")
    default int kphJellies()
    {
        return 400;
    }

    @ConfigItem(
        keyName = "kphCustodianStalkers",
        name = "Custodian stalkers",
        description = "",
        section = taskSpeedSection,
        position = 12
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
        position = 13
    )
    @Range(min = 1)
    @Units("/h")
    default int kphTuroth()
    {
        return 210;
    }

    @ConfigItem(
        keyName = "kphWarpedCreatures",
        name = "Warped creatures",
        description = "",
        section = taskSpeedSection,
        position = 14
    )
    @Range(min = 1)
    @Units("/h")
    default int kphWarpedCreatures()
    {
        return 365;
    }

    @ConfigItem(
        keyName = "kphCaveHorrors",
        name = "Cave horrors",
        description = "",
        section = taskSpeedSection,
        position = 15
    )
    @Range(min = 1)
    @Units("/h")
    default int kphCaveHorrors()
    {
        return 455;
    }

    @ConfigItem(
        keyName = "kphAberrantSpectres",
        name = "Aberrant spectres",
        description = "",
        section = taskSpeedSection,
        position = 16
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
        position = 17
    )
    @Range(min = 1)
    @Units("/h")
    default int kphWyrms()
    {
        return 815;
    }

    @ConfigItem(
        keyName = "kphDustDevils",
        name = "Dust devils",
        description = "",
        section = taskSpeedSection,
        position = 18
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
        position = 19
    )
    @Range(min = 1)
    @Units("/h")
    default int kphKurask()
    {
        return 205;
    }

    @ConfigItem(
        keyName = "kphVenators",
        name = "Venators",
        description = "",
        section = taskSpeedSection,
        position = 20
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
        position = 21
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
        position = 22
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
        position = 23
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
        position = 24
    )
    @Range(min = 1)
    @Units("/h")
    default int kphDrakes()
    {
        return 150;
    }

    @ConfigItem(
        keyName = "kphAbyssalDemons",
        name = "Abyssal demons",
        description = "",
        section = taskSpeedSection,
        position = 25
    )
    @Range(min = 1)
    @Units("/h")
    default int kphAbyssalDemons()
    {
        return 535;
    }

    @ConfigItem(
        keyName = "kphDarkBeasts",
        name = "Dark beasts",
        description = "",
        section = taskSpeedSection,
        position = 26
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
        position = 27
    )
    @Range(min = 1)
    @Units("/h")
    default int kphAraxytes()
    {
        return 600;
    }

    @ConfigItem(
        keyName = "kphSmokeDevils",
        name = "Smoke devils",
        description = "",
        section = taskSpeedSection,
        position = 28
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
        position = 29
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
        position = 30
    )
    default boolean taskChoiceRecalculate()
    {
        return false;
    }

    @ConfigItem(
        keyName = "nieve",
        name = "Replace Steve with Nieve",
        description = "Show Nieve instead of Steve, including her model, dialogue name and related text.",
        section = nieveSection,
        position = 0
    )
    default boolean nieve()
    {
        return true;
    }

    @ConfigItem(
        keyName = "masterRules",
        name = "Enable master rules",
        description = "Recommend a slayer master per task number, highlight the right/wrong masters, and show point projections.",
        section = masterRulesSection,
        position = 0
    )
    default boolean masterRules()
    {
        return false;
    }

    @ConfigItem(
        keyName = "defaultMaster",
        name = "Default master",
        description = "The master to use for any task number no rule matches.",
        section = masterRulesSection,
        position = 1
    )
    default RuleMaster defaultMaster()
    {
        return RuleMaster.MAZCHNA;
    }

    @ConfigItem(
        keyName = "rule1Enabled",
        name = "Rule 1",
        description = "Enable rule 1.",
        section = masterRulesSection,
        position = 2
    )
    default boolean rule1Enabled()
    {
        return true;
    }

    @Range(min = 1)
    @ConfigItem(
        keyName = "rule1Interval",
        name = "Rule 1: every Xth task",
        description = "Rule 1 applies when the next task number is a multiple of this.",
        section = masterRulesSection,
        position = 3
    )
    default int rule1Interval()
    {
        return 10;
    }

    @ConfigItem(
        keyName = "rule1Master",
        name = "Rule 1: use master",
        description = "The master to take rule-1 tasks from.",
        section = masterRulesSection,
        position = 4
    )
    default RuleMaster rule1Master()
    {
        return RuleMaster.KONAR;
    }

    @ConfigItem(
        keyName = "rule2Enabled",
        name = "Rule 2",
        description = "Enable rule 2.",
        section = masterRulesSection,
        position = 5
    )
    default boolean rule2Enabled()
    {
        return false;
    }

    @Range(min = 1)
    @ConfigItem(
        keyName = "rule2Interval",
        name = "Rule 2: every Xth task",
        description = "Rule 2 applies when the next task number is a multiple of this.",
        section = masterRulesSection,
        position = 6
    )
    default int rule2Interval()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "rule2Master",
        name = "Rule 2: use master",
        description = "The master to take rule-2 tasks from.",
        section = masterRulesSection,
        position = 7
    )
    default RuleMaster rule2Master()
    {
        return RuleMaster.KONAR;
    }

    @ConfigItem(
        keyName = "rule3Enabled",
        name = "Rule 3",
        description = "Enable rule 3.",
        section = masterRulesSection,
        position = 8
    )
    default boolean rule3Enabled()
    {
        return false;
    }

    @Range(min = 1)
    @ConfigItem(
        keyName = "rule3Interval",
        name = "Rule 3: every Xth task",
        description = "Rule 3 applies when the next task number is a multiple of this.",
        section = masterRulesSection,
        position = 9
    )
    default int rule3Interval()
    {
        return 100;
    }

    @ConfigItem(
        keyName = "rule3Master",
        name = "Rule 3: use master",
        description = "The master to take rule-3 tasks from.",
        section = masterRulesSection,
        position = 10
    )
    default RuleMaster rule3Master()
    {
        return RuleMaster.KONAR;
    }

    @ConfigItem(
        keyName = "rule4Enabled",
        name = "Rule 4",
        description = "Enable rule 4.",
        section = masterRulesSection,
        position = 11
    )
    default boolean rule4Enabled()
    {
        return false;
    }

    @Range(min = 1)
    @ConfigItem(
        keyName = "rule4Interval",
        name = "Rule 4: every Xth task",
        description = "Rule 4 applies when the next task number is a multiple of this.",
        section = masterRulesSection,
        position = 12
    )
    default int rule4Interval()
    {
        return 250;
    }

    @ConfigItem(
        keyName = "rule4Master",
        name = "Rule 4: use master",
        description = "The master to take rule-4 tasks from.",
        section = masterRulesSection,
        position = 13
    )
    default RuleMaster rule4Master()
    {
        return RuleMaster.KONAR;
    }

    @ConfigItem(
        keyName = "rule5Enabled",
        name = "Rule 5",
        description = "Enable rule 5.",
        section = masterRulesSection,
        position = 14
    )
    default boolean rule5Enabled()
    {
        return false;
    }

    @Range(min = 1)
    @ConfigItem(
        keyName = "rule5Interval",
        name = "Rule 5: every Xth task",
        description = "Rule 5 applies when the next task number is a multiple of this.",
        section = masterRulesSection,
        position = 15
    )
    default int rule5Interval()
    {
        return 1000;
    }

    @ConfigItem(
        keyName = "rule5Master",
        name = "Rule 5: use master",
        description = "The master to take rule-5 tasks from.",
        section = masterRulesSection,
        position = 16
    )
    default RuleMaster rule5Master()
    {
        return RuleMaster.KONAR;
    }

    @ConfigItem(
        keyName = "eliteWesternDiary",
        name = "Elite Western diary done",
        description = "Tick if the elite Western Provinces diary is complete (Nieve tasks pay 15 base points instead of 12).",
        section = masterRulesSection,
        position = 17
    )
    default boolean eliteWesternDiary()
    {
        return false;
    }

    @ConfigItem(
        keyName = "eliteKourendDiary",
        name = "Elite Kourend diary done",
        description = "Tick if the elite Kourend & Kebos diary is complete (Konar tasks pay 20 base points instead of 18).",
        section = masterRulesSection,
        position = 18
    )
    default boolean eliteKourendDiary()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show overlay near masters",
        description = "Show a panel with the next task number, recommended master, current points, and projected points while a slayer master is nearby.",
        section = masterRulesSection,
        position = 19
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightCorrectMaster",
        name = "Highlight correct master",
        description = "Outline the recommended master.",
        section = masterRulesSection,
        position = 20
    )
    default boolean highlightCorrectMaster()
    {
        return true;
    }

    @ConfigItem(
        keyName = "correctMasterColor",
        name = "Correct master color",
        description = "Outline color for the recommended master.",
        section = masterRulesSection,
        position = 21
    )
    default Color correctMasterColor()
    {
        return Color.GREEN;
    }

    @ConfigItem(
        keyName = "highlightWrongMasters",
        name = "Highlight wrong masters",
        description = "Outline the masters to avoid for the next task.",
        section = masterRulesSection,
        position = 22
    )
    default boolean highlightWrongMasters()
    {
        return true;
    }

    @ConfigItem(
        keyName = "wrongMasterColor",
        name = "Wrong master color",
        description = "Outline color for the wrong masters.",
        section = masterRulesSection,
        position = 23
    )
    default Color wrongMasterColor()
    {
        return Color.RED;
    }

    @ConfigItem(
        keyName = "milestoneChatMessage",
        name = "Milestone chat reminder",
        description = "Post a chat message when your next task matches a rule, naming the master to visit.",
        section = masterRulesSection,
        position = 24
    )
    default boolean milestoneChatMessage()
    {
        return true;
    }

    @ConfigItem(
        keyName = "blockWrongMasters",
        name = "Block wrong masters",
        description = "Consume the Assignment option on masters other than the recommended one (a chat message explains the block).",
        section = masterRulesSection,
        position = 25
    )
    default boolean blockWrongMasters()
    {
        return false;
    }

    @ConfigItem(
        keyName = "hideWrongMastersOnMilestone",
        name = "Hide wrong masters on milestone",
        description = "While your next task matches a rule, remove the Assignment option from masters other than the recommended one.",
        section = masterRulesSection,
        position = 26
    )
    default boolean hideWrongMastersOnMilestone()
    {
        return false;
    }

    @ConfigItem(
        keyName = "taskSorter",
        name = "Sort task list",
        description = "Sort the slayer task list interface (opened from the slayer rewards screen).",
        section = taskSorterSection,
        position = 0
    )
    default boolean taskSorter()
    {
        return false;
    }

    @ConfigItem(
        keyName = "taskSortMethod",
        name = "Sort by",
        description = "Weight sorts by the assignment odds shown in the list; falls back to alphabetical when the list shows none.",
        section = taskSorterSection,
        position = 1
    )
    default TaskSortMethod taskSortMethod()
    {
        return TaskSortMethod.WEIGHT;
    }

    @ConfigItem(
        keyName = "taskSortReversed",
        name = "Reverse order",
        description = "Reverse the chosen sort order.",
        section = taskSorterSection,
        position = 2
    )
    default boolean taskSortReversed()
    {
        return false;
    }
}
