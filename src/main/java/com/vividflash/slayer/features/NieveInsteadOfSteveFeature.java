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
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.Text;

/**
 * Puts Nieve back where the game shows Steve.
 *
 * A single varbit decides which of the two the server has on display. While
 * that varbit says Steve, this feature holds a client-side copy of it at the
 * Nieve value and rewrites the parts the varbit does not reach. Those parts
 * are the NPC Contact row, the dialogue name and chathead, menu options, the
 * grave's memorial text and two chat messages.
 *
 * The value the server sent is tracked apart from the value on display. It is
 * written back when the feature is switched off, when the plugin stops and
 * when the session ends.
 */
@Singleton
public class NieveInsteadOfSteveFeature implements Feature
{
    private static final String STEVE = "Steve";
    private static final String NIEVE = "Nieve";

    /** Varbit value the server uses while Steve is the one standing there. */
    private static final int SHOWING_STEVE = 1;
    /** Varbit value the server uses while Nieve is the one standing there. */
    private static final int SHOWING_NIEVE = 0;
    /** No value observed yet this session. */
    private static final int UNOBSERVED = -1;

    /**
     * Npc ids as a chathead widget carries them. A chathead is only written
     * after it has been read back as Steve's, so a widget holding anything
     * else keeps whatever it has.
     */
    private static final int NIEVE_CHATHEAD = NpcID.SLAYER_MASTER_NIEVE;
    private static final int STEVE_CHATHEAD = NpcID.SLAYER_MASTER_STEVE;

    /** How far ahead of a contact row's label its chathead can sit. */
    private static final int CONTACT_CHATHEAD_LOOKBACK = 2;

    /**
     * Substring the untouched grave dedication carries. It also marks the
     * popup as the grave rather than something else on the same group, so it
     * is matched before anything is written.
     */
    private static final String GRAVE_DEDICATION_MARKER = "Nieve's honour";
    /**
     * Nothing on this stone is dedicated to anyone, so the kill counts credit
     * the creatures to the one who made them. The phrase matches the length of
     * the one it replaces, which keeps the break the game puts in that line;
     * the rows do not wrap on their own. Missing it leaves the line as the
     * game wrote it, which is why the marker above is the shorter match.
     */
    private static final String GRAVE_DEDICATION_ORIGINAL = "in Nieve's honour";
    private static final String GRAVE_DEDICATION_SWAP = "of Glough's making";

    /**
     * Every text line the grave popup is built from, top to bottom. The lines
     * are matched by the wording the game puts on them rather than by
     * position, so nothing is written to a line that holds something else.
     */
    private static final int[] GRAVE_LINES = {
        InterfaceID.Messagescroll2.MS1,
        InterfaceID.Messagescroll2.MS2,
        InterfaceID.Messagescroll2.MS3,
        InterfaceID.Messagescroll2.MS4,
        InterfaceID.Messagescroll2.MS5,
        InterfaceID.Messagescroll2.MS6,
        InterfaceID.Messagescroll2.MS7,
        InterfaceID.Messagescroll2.MS8,
        InterfaceID.Messagescroll2.MS9,
        InterfaceID.Messagescroll2.MS10,
        InterfaceID.Messagescroll2.MS11,
        InterfaceID.Messagescroll2.MS12,
    };

    /**
     * With Nieve standing in the cave, the memorial belongs to the one the
     * quest actually buried, on the stone the gnomes had already cut for her.
     * Hers lists the honours she held; his lists the offices he held and what
     * he nearly did with them, in the same rows. Each line replaces a line the
     * game already writes, so the layout stays the game's own.
     */
    private static final String GRAVE_TITLE_ORIGINAL = "Nieve";
    private static final String GRAVE_TITLE_REWRITE = "Glough";
    private static final String GRAVE_MEMORIAL_MARKER = "In Loving memory of";
    private static final String GRAVE_MEMORIAL_REWRITE = "This stone was cut for Nieve, but she got better.";
    private static final String GRAVE_EPITAPH_FIRST_ORIGINAL = "Shield of the Gnomes";
    private static final String GRAVE_EPITAPH_FIRST_REWRITE = "Chief Tree Guardian, Former Head of the Royal Guard";
    private static final String GRAVE_EPITAPH_SECOND_ORIGINAL = "Master of Creatures";
    private static final String GRAVE_EPITAPH_SECOND_REWRITE = "Nearly the End of Gnomekind itself";

    /**
     * One replacement line for any of Steve's identity/backstory dialogue
     * boxes (the cousin/takeover lines); it must not itself contain a
     * word {@link #mentionsIdentity} matches, or it would be rewritten
     * forever.
     */
    private static final String DIALOGUE_BODY_REWRITE = "Enough about me. Those monsters<br>won't slay themselves.";

    /** Player option that opens the backstory branch, and its replacement text. */
    private static final String NEW_MASTER_OPTION = "I see you're the new Slayer Master here.";
    private static final String NEW_MASTER_OPTION_REWRITE = "Oh, hi Nieve. Good to see you're still around.";
    /** Her answer to it, which replaces the cousin/takeover backstory box. */
    private static final String DEMISE_LINE = "My demise was... greatly exaggerated.";

    /**
     * The player's sign-off from that branch. It commiserates over a Nieve who
     * is standing right there, so it is answered to the rewritten branch
     * instead. Steve's is the only script in the game that carries the line,
     * so it needs no further qualification.
     */
    private static final String CONDOLENCE_LINE = "How sad for you.";
    private static final String CONDOLENCE_REWRITE = "Good to have you back.";

    private static final String EXAMINE_ORIGINAL = "In memory of Nieve, she looks rich and dead.";
    private static final String EXAMINE_REWRITE = "In memory of Glough, he looks misled and dead.";
    private static final String OFF_TASK_ORIGINAL = "Steve wants you to stick to your Slayer assignments.";
    private static final String OFF_TASK_REWRITE = "Nieve wants you to stick to your Slayer assignments.";

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private EventBus eventBus;

    @Inject
    private SlayerConfig config;

    /** The value the server last pushed, kept apart from whatever the client is currently showing. */
    private int serverValue = UNOBSERVED;

    /** Set while the client-side varbit is being pinned to {@link #SHOWING_NIEVE} by this feature. */
    private boolean pinned;

    /** Set between a write of our own and the event that carries it back. */
    private boolean echoPending;

    @Override
    public void startUp()
    {
        eventBus.register(this);
        clientThread.invokeLater(this::reconcileVarbit);
    }

    @Override
    public void shutDown()
    {
        // Off the bus first, so the write below cannot re-pin through our own
        // varbit handler.
        eventBus.unregister(this);
        clientThread.invoke(this::unpin);
    }

    /** True while the server says Steve and the user wants to see Nieve. */
    private boolean swapActive()
    {
        return config.nieve() && serverValue == SHOWING_STEVE;
    }

    /**
     * Files an observed varbit value as the server's own. Each write of ours
     * comes back through the same event, so one echo is swallowed per write
     * and everything after it counts as the server's. Unknown values are
     * ignored.
     */
    private void observe(int exposed)
    {
        if (exposed != SHOWING_STEVE && exposed != SHOWING_NIEVE)
        {
            return;
        }

        if (echoPending && exposed == SHOWING_NIEVE)
        {
            echoPending = false;
            return;
        }

        serverValue = exposed;
    }

    /** Pins the client-side varbit to Nieve, and keeps re-pinning it if it drifts back. */
    private void pin()
    {
        if (!config.nieve() || serverValue != SHOWING_STEVE)
        {
            return;
        }

        pinned = true;
        if (client.getVarbitValue(VarbitID.MM2_SLAYER_MASTER) != SHOWING_NIEVE)
        {
            echoPending = true;
            client.setVarbit(VarbitID.MM2_SLAYER_MASTER, SHOWING_NIEVE);
        }
    }

    /** Hands the varbit back to the value the server actually gave us. */
    private void unpin()
    {
        echoPending = false;
        if (!pinned)
        {
            return;
        }

        pinned = false;
        if (serverValue == SHOWING_STEVE || serverValue == SHOWING_NIEVE)
        {
            client.setVarbit(VarbitID.MM2_SLAYER_MASTER, serverValue);
        }
    }

    /** Re-reads the varbit and re-applies the pin; idempotent. */
    private void reconcileVarbit()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        // A read taken while the pin is up returns our own value, so only the
        // varbit event can tell us what the server holds from then on.
        if (!pinned)
        {
            observe(client.getVarbitValue(VarbitID.MM2_SLAYER_MASTER));
        }
        pin();
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (event.getVarbitId() != VarbitID.MM2_SLAYER_MASTER)
        {
            return;
        }

        observe(event.getValue());
        pin();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // Backstop in case the varbit change never surfaces as its own event.
        reconcileVarbit();
    }

    @Subscribe
    public void onBeforeRender(BeforeRender event)
    {
        dressDialogue();
        dressPlayerDialogue();
        dressChatOptions();
        dressContactList();
        rewriteGrave();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        switch (event.getGameState())
        {
            case LOGIN_SCREEN:
            case HOPPING:
                // Give the varbit back and forget it. A value from a finished
                // session says nothing about the next one.
                unpin();
                serverValue = UNOBSERVED;
                break;
            case LOGGED_IN:
                clientThread.invokeLater(this::reconcileVarbit);
                break;
            default:
                break;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"vividflashslayer".equals(event.getGroup()) || !"nieve".equals(event.getKey()))
        {
            return;
        }

        clientThread.invoke(() ->
        {
            if (config.nieve())
            {
                reconcileVarbit();
            }
            else
            {
                unpin();
            }
        });
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!swapActive())
        {
            return;
        }

        MenuEntry entry = event.getMenuEntry();
        if (STEVE.equals(entry.getOption()))
        {
            entry.setOption(NIEVE);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!swapActive())
        {
            return;
        }

        String rewrite;
        if (EXAMINE_ORIGINAL.equals(event.getMessage()))
        {
            rewrite = EXAMINE_REWRITE;
        }
        else if (OFF_TASK_ORIGINAL.equals(event.getMessage()))
        {
            rewrite = OFF_TASK_REWRITE;
        }
        else
        {
            return;
        }

        MessageNode node = event.getMessageNode();
        if (node == null)
        {
            return;
        }

        node.setValue(rewrite);
        // The chatbox repaints on its own schedule, so request a refresh for
        // the edit to show immediately.
        client.refreshChat();
    }

    /**
     * Swaps a dialogue box away from Steve.
     */
    private void dressDialogue()
    {
        if (!swapActive())
        {
            return;
        }

        // Steve is the speaker under either label, the game's own "Steve" or
        // the "Nieve" left behind by a previous frame's rename.
        Widget name = client.getWidget(InterfaceID.ChatLeft.NAME);
        String label = name == null ? "" : plainText(name);
        boolean steveSpeaking = STEVE.equals(label) || NIEVE.equals(label);
        if (name != null && STEVE.equals(label))
        {
            name.setText(NIEVE);
        }

        Widget chathead = client.getWidget(InterfaceID.ChatLeft.HEAD);
        if (chathead != null && chathead.getModelId() == STEVE_CHATHEAD)
        {
            chathead.setModelId(NIEVE_CHATHEAD);
        }

        // Steve's identity lines (the cousin/takeover backstory) do not fit a
        // speaker labelled Nieve, so the whole box is swapped. The check on
        // the speaker leaves other NPCs mentioning either name alone.
        Widget body = client.getWidget(InterfaceID.ChatLeft.TEXT);
        if (body != null && steveSpeaking)
        {
            String plain = plainText(body);
            if (mentionsIdentity(plain))
            {
                // The cousin/takeover box answers the reworded chat option;
                // any other identity line gets the generic deflection.
                body.setText(plain.toLowerCase().contains("cousin")
                    ? DEMISE_LINE
                    : DIALOGUE_BODY_REWRITE);
            }
        }
    }

    /**
     * Rewrites the player's side of the conversation. While the swap is
     * active the player should not be saying "Steve" either.
     */
    private void dressPlayerDialogue()
    {
        if (!swapActive())
        {
            return;
        }

        Widget text = client.getWidget(InterfaceID.ChatRight.TEXT);
        if (text == null)
        {
            return;
        }

        // The game echoes a picked chat option back as a player-spoken box.
        // Keep the reworded option's wording there too.
        String plain = plainText(text);
        if (NEW_MASTER_OPTION.equals(plain))
        {
            text.setText(NEW_MASTER_OPTION_REWRITE);
            return;
        }

        // The branch closes on condolences the rewritten one has no use for.
        if (CONDOLENCE_LINE.equals(plain))
        {
            text.setText(CONDOLENCE_REWRITE);
            return;
        }

        String raw = text.getText();
        if (raw != null && raw.contains(STEVE))
        {
            text.setText(raw.replace(STEVE, NIEVE));
        }
    }

    /**
     * The chat option that opens the backstory branch reads as if the player
     * doesn't know who she is; reword it while the swap is active.
     */
    private void dressChatOptions()
    {
        if (!swapActive())
        {
            return;
        }

        Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
        if (options == null)
        {
            return;
        }

        Widget[] children = options.getDynamicChildren();
        if (children == null)
        {
            return;
        }

        for (Widget child : children)
        {
            if (child != null && NEW_MASTER_OPTION.equals(plainText(child)))
            {
                child.setText(NEW_MASTER_OPTION_REWRITE);
            }
        }
    }

    private static boolean mentionsIdentity(String plain)
    {
        String lower = plain.toLowerCase();
        return lower.contains("steve") || lower.contains("nieve") || lower.contains("cousin");
    }

    /**
     * Relabels Steve's row on the NPC Contact interface and puts Nieve's
     * chathead back on it. The row is found by its label rather than by a
     * fixed position, so the entry order in the interface can move without
     * breaking this.
     */
    private void dressContactList()
    {
        if (!swapActive())
        {
            return;
        }

        Widget root = client.getWidget(InterfaceID.LunarContactNpc.UNIVERSE);
        if (root == null || root.isHidden())
        {
            return;
        }

        Widget[] entries = contactEntries();
        if (entries == null)
        {
            return;
        }

        int labelIndex = -1;
        for (int i = 0; i < entries.length; i++)
        {
            Widget entry = entries[i];
            if (entry != null && STEVE.equals(plainText(entry)))
            {
                labelIndex = i;
                break;
            }
        }
        if (labelIndex < 0)
        {
            return;
        }

        Widget chathead = findContactChathead(entries, labelIndex);
        if (chathead == null)
        {
            // Leave the row alone unless both halves of it can be swapped.
            return;
        }

        entries[labelIndex].setText(NIEVE);
        chathead.setModelId(NIEVE_CHATHEAD);
    }

    /**
     * The contact rows are built as dynamic children of the scroll layer, or
     * of the contents layer when the list is not scrolling.
     */
    private Widget[] contactEntries()
    {
        Widget[] entries = dynamicChildren(InterfaceID.LunarContactNpc.SCROLLLAYER);
        if (entries == null || entries.length == 0)
        {
            entries = dynamicChildren(InterfaceID.LunarContactNpc.CONTENTS);
        }
        return entries == null || entries.length == 0 ? null : entries;
    }

    private Widget[] dynamicChildren(int componentId)
    {
        Widget widget = client.getWidget(componentId);
        return widget == null ? null : widget.getDynamicChildren();
    }

    /**
     * The chathead belonging to the row whose label sits at the given index,
     * identified by Steve's own npc id sitting on it. A widget that does not
     * carry that id is some other part of the interface, so it is passed over
     * and nothing is written to it.
     */
    private static Widget findContactChathead(Widget[] entries, int labelIndex)
    {
        int lowest = Math.max(0, labelIndex - CONTACT_CHATHEAD_LOOKBACK);
        for (int i = labelIndex - 1; i >= lowest; i--)
        {
            Widget widget = entries[i];
            if (widget != null
                && widget.getModelType() == WidgetModelType.NPC_CHATHEAD
                && widget.getModelId() == STEVE_CHATHEAD)
            {
                return widget;
            }
        }
        return null;
    }

    /**
     * Reworks the memorial so it mourns Glough rather than the Nieve standing
     * in the cave. Widget group 221 is a generic scroll popup shared with
     * unrelated content, so the dedication line has to look like the grave's
     * before anything is written. Each line is then matched by its own
     * wording, which leaves the blank lines blank, leaves the kill counts
     * alone and makes every write a no-op once it has happened.
     */
    private void rewriteGrave()
    {
        if (!swapActive())
        {
            return;
        }

        Widget[] lines = new Widget[GRAVE_LINES.length];
        Widget dedication = null;
        String dedicationText = null;
        for (int i = 0; i < GRAVE_LINES.length; i++)
        {
            lines[i] = client.getWidget(GRAVE_LINES[i]);
            String text = lines[i] == null ? null : lines[i].getText();
            if (text != null && text.contains(GRAVE_DEDICATION_MARKER))
            {
                dedication = lines[i];
                dedicationText = text;
            }
        }

        if (dedication == null)
        {
            // The grave is closed, or some other popup is borrowing the group.
            return;
        }

        for (Widget line : lines)
        {
            if (line == null || line == dedication)
            {
                continue;
            }

            String plain = plainText(line);
            if (GRAVE_TITLE_ORIGINAL.equals(plain))
            {
                line.setText(GRAVE_TITLE_REWRITE);
            }
            else if (plain.startsWith(GRAVE_MEMORIAL_MARKER))
            {
                line.setText(GRAVE_MEMORIAL_REWRITE);
            }
            else if (GRAVE_EPITAPH_FIRST_ORIGINAL.equals(plain))
            {
                line.setText(GRAVE_EPITAPH_FIRST_REWRITE);
            }
            else if (GRAVE_EPITAPH_SECOND_ORIGINAL.equals(plain))
            {
                line.setText(GRAVE_EPITAPH_SECOND_REWRITE);
            }
        }

        dedication.setText(dedicationText.replace(GRAVE_DEDICATION_ORIGINAL, GRAVE_DEDICATION_SWAP));
    }

    private static String plainText(Widget widget)
    {
        String text = widget.getText();
        if (text == null)
        {
            return "";
        }
        // Dialogue boxes wrap with <br>; fold the breaks back to spaces so
        // exact-match comparisons see the line as written.
        return Text.removeTags(text.replace("<br>", " ")).replaceAll("\\s+", " ").trim();
    }
}
