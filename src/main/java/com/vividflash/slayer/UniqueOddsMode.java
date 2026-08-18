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

/**
 * Which superior drop the task choice odds are quoted for. A superior rolls
 * the imbued table and the eternal table once each, both at the monster's own
 * unique table rate. The imbued table always gives an item: a dust battlestaff
 * at 7/16, a mist battlestaff at 7/16 and an imbued heart at 2/16. The eternal
 * table gives an eternal gem at 2/16 and nothing at 14/16. Each option below
 * is a fixed multiple of that rate, so the offered tasks rank the same
 * whichever one is picked. A hydra's rate of 1/20 gives 1 in 160 for a heart
 * and 1 in 46 for a battlestaff, which are the rates the game drops them at.
 * The multiples add
 * the two rolls rather than subtracting the chance of both landing, which is
 * a third of a percent at the steepest rate in the game.
 */
public enum UniqueOddsMode
{
    /** Either table is rolled, the eternal table's nothing outcome included. */
    SLAYER_UNIQUE_ROLL(2),
    /** Any item off either table, so the battlestaves as well as the heart and gem. */
    UNIQUE_ITEM(9 / 8.0),
    /** An imbued heart or an eternal gem. */
    HEART_OR_GEM(1 / 4.0),
    /** An imbued heart on its own. */
    IMBUED_HEART(1 / 8.0);

    private final double perSuperiorFactor;

    UniqueOddsMode(double perSuperiorFactor)
    {
        this.perSuperiorFactor = perSuperiorFactor;
    }

    public double getPerSuperiorFactor()
    {
        return perSuperiorFactor;
    }
}
