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

import com.vividflash.slayer.TaskPickMode;
import java.util.List;

/**
 * Ranks Mortimer's offered tasks for a {@link TaskPickMode}. A task's worth is
 * the unique table rolls it is expected to give, its cost the hours it takes.
 * The baseline is the rolls per hour a player averages over many tasks, which
 * is what an hour spent on a weak task gives up.
 */
final class TaskPickScorer
{
    /** Chance of a superior per kill. */
    static final double SUPERIOR_RATE = 1 / 200.0;

    /** Completed tasks at which Mortimer offers a third choice. */
    static final int THIRD_CHOICE_TASKS = 50;

    private static final int BASELINE_ROUNDS = 50;

    private TaskPickScorer()
    {
    }

    /** Hours a task takes, the fixed overhead included. */
    static double hours(double quantity, double killsPerHour, double overheadMinutes)
    {
        return overheadMinutes / 60.0 + quantity / killsPerHour;
    }

    /** Unique table rolls a task is expected to give. */
    static double rolls(double quantity, double uniqueTableChance)
    {
        return quantity * SUPERIOR_RATE * uniqueTableChance;
    }

    /**
     * Index of the pick among the choices, or -1 when none is resolvable. The
     * speed-based modes need every choice's quantity and chance; a set missing
     * either is ranked by chance alone.
     */
    static int pick(List<TaskChoiceOddsFeature.Choice> choices, TaskPickMode mode,
        int tasksCompleted, double overheadMinutes, double baseline)
    {
        if (mode == TaskPickMode.AUTO)
        {
            mode = tasksCompleted < THIRD_CHOICE_TASKS
                ? TaskPickMode.FASTEST_WITH_EXCEPTION : TaskPickMode.BALANCED;
        }
        if (mode == TaskPickMode.MAX_CHANCE || !allTimed(choices))
        {
            return highestChance(choices);
        }

        int pick = -1;
        double pickScore = 0;
        boolean pickBoosted = false;
        for (int i = 0; i < choices.size(); i++)
        {
            TaskChoiceOddsFeature.Choice choice = choices.get(i);
            double hours = hours(choice.expectedQuantity, choice.killsPerHour, overheadMinutes);
            double rolls = rolls(choice.expectedQuantity, choice.uniqueTableChance);

            double score;
            boolean boosted = false;
            if (mode == TaskPickMode.BALANCED)
            {
                score = rolls - baseline * hours;
            }
            else
            {
                boosted = mode == TaskPickMode.FASTEST_WITH_EXCEPTION
                    && choice.fastTask && choice.uniqueModifierPercent > 0
                    && rolls / hours >= baseline;
                // A boosted fast task worth its time outranks every other offer, and
                // is compared with other such offers by its rolls per hour.
                score = boosted ? rolls / hours : -hours;
            }

            if (pick == -1 || (boosted && !pickBoosted)
                || (boosted == pickBoosted && score > pickScore))
            {
                pick = i;
                pickScore = score;
                pickBoosted = boosted;
            }
        }
        return pick;
    }

    private static boolean allTimed(List<TaskChoiceOddsFeature.Choice> choices)
    {
        for (TaskChoiceOddsFeature.Choice choice : choices)
        {
            if (Double.isNaN(choice.uniqueTableChance) || Double.isNaN(choice.expectedQuantity)
                || choice.killsPerHour <= 0)
            {
                return false;
            }
        }
        return !choices.isEmpty();
    }

    private static int highestChance(List<TaskChoiceOddsFeature.Choice> choices)
    {
        int best = -1;
        for (int i = 0; i < choices.size(); i++)
        {
            double value = choices.get(i).uniqueTableChance;
            if (!Double.isNaN(value)
                && (best == -1 || value > choices.get(best).uniqueTableChance))
            {
                best = i;
            }
        }
        return best;
    }

    /**
     * Rolls per hour of a player who is offered three tasks, drawn by weight
     * without repeats, and always takes the one that leaves them furthest
     * ahead of this same rate. The rate is found by starting from zero and
     * recomputing it from the picks it leads to until it settles. Modifiers are
     * left out, so the tasks enter at their plain quantity and chance.
     */
    static double baseline(double[] weights, double[] rolls, double[] hours)
    {
        int n = weights.length;
        double totalWeight = 0;
        for (double weight : weights)
        {
            totalWeight += weight;
        }

        double rate = 0;
        for (int round = 0; round < BASELINE_ROUNDS; round++)
        {
            double expectedRolls = 0;
            double expectedHours = 0;
            for (int a = 0; a < n; a++)
            {
                double pa = weights[a] / totalWeight;
                for (int b = 0; b < n; b++)
                {
                    if (b == a)
                    {
                        continue;
                    }
                    double pb = pa * weights[b] / (totalWeight - weights[a]);
                    for (int c = 0; c < n; c++)
                    {
                        if (c == a || c == b)
                        {
                            continue;
                        }
                        double p = pb * weights[c] / (totalWeight - weights[a] - weights[b]);
                        int best = a;
                        if (rolls[b] - rate * hours[b] > rolls[best] - rate * hours[best])
                        {
                            best = b;
                        }
                        if (rolls[c] - rate * hours[c] > rolls[best] - rate * hours[best])
                        {
                            best = c;
                        }
                        expectedRolls += p * rolls[best];
                        expectedHours += p * hours[best];
                    }
                }
            }

            double next = expectedHours > 0 ? expectedRolls / expectedHours : Double.NaN;
            if (Double.isNaN(next) || Math.abs(next - rate) < 1e-12)
            {
                return next;
            }
            rate = next;
        }
        return rate;
    }
}
