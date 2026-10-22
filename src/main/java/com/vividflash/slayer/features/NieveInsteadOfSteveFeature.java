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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.NPC;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetUtil;
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
@Slf4j
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

    /**
     * The slayer master can spawn under the base id or either shape's id
     * (Nieve's while the swap is pinned), so all three count, as does the
     * Steve guarding the wyvern cave.
     */
    private static final int MASTER_BASE_NPC = NpcID.SLAYER_MASTER_6;
    private static final int STEVE_NPC = NpcID.SLAYER_MASTER_STEVE;
    private static final int NIEVE_NPC = NpcID.SLAYER_MASTER_NIEVE;
    private static final int WYVERN_CAVE_STEVE_NPC = NpcID.WYVERN_CAVE_STEVE;

    /** Game ticks without an open dialogue before a contact counts as over. */
    private static final int CONTACT_IDLE_TICKS = 3;

    /** Value of the current-task master varbit while Nieve or Steve assigned the task. */
    private static final int TASK_MASTER_STEVE = 6;

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

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Texts held in the plain text cache before it starts over; the contact list is the longest set. */
    private static final int PLAIN_TEXT_CACHE_SIZE = 128;

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

    /** Steve npcs currently in the scene, so spawn and despawn order cannot unbalance a count. */
    private final Set<NPC> steveNpcs = new HashSet<>();

    /** Steve is in the scene, so he can be talked to in person. */
    private boolean steveNearby;

    /** The current slayer task was assigned by Steve, so the gems and rings reach him. */
    private boolean steveTask;

    /**
     * An NPC Contact row was clicked and the conversation has not ended yet.
     * A cancelled contact leaves this set, which only costs the dialogue
     * lookups until the next logout.
     */
    private boolean contactPending;

    /** A dialogue has opened since {@link #contactPending} was set. */
    private boolean contactDialogueSeen;

    /** One of the three dialogue groups was open on the last frame checked. */
    private boolean dialogueOpen;

    /** Ticks in a row with no dialogue open after the contact's conversation. */
    private int contactIdleTicks;

    /** The NPC Contact list is open, so its rows are dressed each frame. */
    private boolean contactListOpen;

    /** The scroll popup the grave uses is open, whatever it is showing. */
    private boolean scrollOpen;

    /**
     * Plain forms of widget texts seen recently, keyed by the raw text, so the
     * per-frame matching does not strip tags from the same text every frame.
     */
    private final Map<String, String> plainTexts = new HashMap<>();

    /** The grave's line widgets, looked up again each frame into the same array. */
    private final Widget[] graveLines = new Widget[GRAVE_LINES.length];

    @Override
    public void startUp()
    {
        if (log.isDebugEnabled())
        {
            log.debug("[config] nieve={}", config.nieve());
        }
        eventBus.register(this);
        clientThread.invokeLater(this::reconcileVarbit);
        clientThread.invokeLater(this::refreshSteveState);
        // Either interface may already be open when the plugin starts, and no
        // load event comes for it then.
        clientThread.invokeLater(() ->
        {
            contactListOpen = isOpen(InterfaceID.LunarContactNpc.UNIVERSE);
            scrollOpen = isOpen(InterfaceID.Messagescroll2.MESSAGESCROLL2_CLOSE);
        });
    }

    @Override
    public void shutDown()
    {
        // Off the bus first, so no handler re-pins after the write below.
        eventBus.unregister(this);
        clientThread.invoke(() ->
        {
            unpin();
            // Nothing from this run carries over to the next start, which
            // reads the varbit and the scene again.
            clearSteveState();
            serverValue = UNOBSERVED;
            plainTexts.clear();
        });
    }

    private boolean isOpen(int componentId)
    {
        Widget widget = client.getWidget(componentId);
        return widget != null && !widget.isHidden();
    }

    /** True while the server says Steve and the user wants to see Nieve. */
    private boolean swapActive()
    {
        return serverValue == SHOWING_STEVE && config.nieve();
    }

    /**
     * Files an observed varbit value as the server's own. A write of ours
     * posts no varbit event, so every event carries the server's value. Once
     * the server says Nieve, the pin has nothing left to hold. Unknown values
     * are ignored.
     */
    private void observe(int exposed)
    {
        if (exposed != SHOWING_STEVE && exposed != SHOWING_NIEVE)
        {
            return;
        }

        if (serverValue != exposed && log.isDebugEnabled())
        {
            log.debug("[nieve] server varbit {} -> {}", serverValue, exposed);
        }
        serverValue = exposed;
        if (exposed == SHOWING_NIEVE && pinned)
        {
            pinned = false;
            log.debug("[nieve] unpinned, server shows Nieve");
        }
    }

    /** Pins the client-side varbit to Nieve. */
    private void pin()
    {
        if (!config.nieve() || serverValue != SHOWING_STEVE)
        {
            return;
        }

        if (!pinned && log.isDebugEnabled())
        {
            log.debug("[nieve] pinned to Nieve, server value {}", serverValue);
        }
        pinned = true;
        if (client.getVarbitValue(VarbitID.MM2_SLAYER_MASTER) != SHOWING_NIEVE)
        {
            client.setVarbit(VarbitID.MM2_SLAYER_MASTER, SHOWING_NIEVE);
        }
    }

    /** Hands the varbit back to the value the server actually gave us. */
    private void unpin()
    {
        if (!pinned)
        {
            return;
        }

        pinned = false;
        if (log.isDebugEnabled())
        {
            log.debug("[nieve] unpinned, varbit handed back to {}", serverValue);
        }
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

        // A read taken while the pin is up can return our own value, so it is
        // only filed as the server's while unpinned.
        if (!pinned)
        {
            observe(client.getVarbitValue(VarbitID.MM2_SLAYER_MASTER));
        }
        pin();
    }

    /** Re-reads the task master and the npcs already standing in the scene. */
    private void refreshSteveState()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        steveTask = client.getVarbitValue(VarbitID.SLAYER_MASTER) == TASK_MASTER_STEVE;

        steveNpcs.clear();
        for (NPC npc : client.getTopLevelWorldView().npcs())
        {
            if (isSteve(npc.getId()))
            {
                steveNpcs.add(npc);
            }
        }
        steveNearby = !steveNpcs.isEmpty();
        if (log.isDebugEnabled())
        {
            log.debug("Steve state refreshed: nearby={} task={}", steveNearby, steveTask);
        }
    }

    private static boolean isSteve(int npcId)
    {
        return npcId == MASTER_BASE_NPC || npcId == STEVE_NPC || npcId == NIEVE_NPC
            || npcId == WYVERN_CAVE_STEVE_NPC;
    }

    private void clearSteveState()
    {
        steveNpcs.clear();
        steveNearby = false;
        steveTask = false;
        contactPending = false;
        contactDialogueSeen = false;
        dialogueOpen = false;
        contactIdleTicks = 0;
        contactListOpen = false;
        scrollOpen = false;
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        NPC npc = event.getNpc();
        if (isSteve(npc.getId()))
        {
            steveNpcs.add(npc);
            steveNearby = true;
            if (log.isDebugEnabled())
            {
                log.debug("Steve spawned ({}, shown as {}), nearby", npc.getId(),
                    npc.getTransformedComposition() == null ? -1 : npc.getTransformedComposition().getId());
            }
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        NPC npc = event.getNpc();
        if (isSteve(npc.getId()))
        {
            steveNpcs.remove(npc);
            steveNearby = !steveNpcs.isEmpty();
            if (log.isDebugEnabled())
            {
                log.debug("Steve despawned ({}), nearby={}", npc.getId(), steveNearby);
            }
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (WidgetUtil.componentToInterface(event.getParam1()) == InterfaceID.LUNAR_CONTACT_NPC)
        {
            contactPending = true;
            contactDialogueSeen = false;
            contactIdleTicks = 0;
            log.debug("NPC Contact clicked, dialogue gate open");
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (event.getVarbitId() == VarbitID.SLAYER_MASTER)
        {
            steveTask = event.getValue() == TASK_MASTER_STEVE;
            if (log.isDebugEnabled())
            {
                log.debug("Task master {}, steveTask={}", event.getValue(), steveTask);
            }
            return;
        }

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
        // A short gap between dialogue pages must not end the contact, so it
        // only counts as over after a few ticks with nothing open.
        if (contactPending && contactDialogueSeen)
        {
            contactIdleTicks = dialogueOpen ? 0 : contactIdleTicks + 1;
            if (contactIdleTicks >= CONTACT_IDLE_TICKS)
            {
                contactPending = false;
                contactDialogueSeen = false;
                contactIdleTicks = 0;
                log.debug("NPC Contact conversation over, dialogue gate closed");
            }
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.LUNAR_CONTACT_NPC)
        {
            contactListOpen = true;
        }
        else if (event.getGroupId() == InterfaceID.MESSAGESCROLL2)
        {
            scrollOpen = true;
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (event.getGroupId() == InterfaceID.LUNAR_CONTACT_NPC)
        {
            contactListOpen = false;
        }
        else if (event.getGroupId() == InterfaceID.MESSAGESCROLL2)
        {
            scrollOpen = false;
        }
    }

    @Subscribe
    public void onBeforeRender(BeforeRender event)
    {
        // Steve only speaks in person, through a gem or ring tied to his task,
        // or through NPC Contact, so the dialogue lookups wait for one of those.
        // A group that is not loaded has no root widget, so one lookup each
        // keeps the per-frame cost flat while none of these are open. Every
        // dress step needs the swap active, so nothing is looked up without
        // it; a contact clicked meanwhile stays pending for when it turns on.
        boolean active = swapActive();
        if (active && (contactPending || steveNearby || steveTask))
        {
            boolean left = client.getWidget(InterfaceID.ChatLeft.UNIVERSE) != null;
            boolean right = client.getWidget(InterfaceID.ChatRight.UNIVERSE) != null;
            boolean menu = client.getWidget(InterfaceID.Chatmenu.UNIVERSE) != null;
            if (left)
            {
                dressDialogue();
            }
            if (right)
            {
                dressPlayerDialogue();
            }
            if (menu)
            {
                dressChatOptions();
            }

            dialogueOpen = left || right || menu;
            if (contactPending && dialogueOpen && !contactDialogueSeen)
            {
                contactDialogueSeen = true;
                log.debug("NPC Contact dialogue opened");
            }
        }
        if (!active)
        {
            return;
        }
        if (contactListOpen)
        {
            dressContactList();
        }
        if (scrollOpen)
        {
            rewriteGrave();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        switch (event.getGameState())
        {
            case LOADING:
                // The scene reload fires every spawn again.
                steveNpcs.clear();
                steveNearby = false;
                break;
            case LOGIN_SCREEN:
            case HOPPING:
                clearSteveState();
                // Give the varbit back and forget it. A value from a finished
                // session says nothing about the next one.
                unpin();
                serverValue = UNOBSERVED;
                break;
            case LOGGED_IN:
                clientThread.invokeLater(this::reconcileVarbit);
                clientThread.invokeLater(this::refreshSteveState);
                break;
            default:
                break;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!SlayerConfig.GROUP.equals(event.getGroup()) || !"nieve".equals(event.getKey()))
        {
            return;
        }

        if (log.isDebugEnabled())
        {
            log.debug("[config] nieve -> {}", event.getNewValue());
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

        if (log.isDebugEnabled())
        {
            log.debug("[chat] rewrote message to {}", rewrite);
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
        // Steve is the speaker under either label, the game's own "Steve" or
        // the "Nieve" left behind by a previous frame's rename.
        Widget name = client.getWidget(InterfaceID.ChatLeft.NAME);
        String label = name == null ? "" : plainText(name);
        boolean steveSpeaking = STEVE.equals(label) || NIEVE.equals(label);
        if (name != null && STEVE.equals(label))
        {
            name.setText(NIEVE);
            log.debug("[dialog] speaker renamed to Nieve");
        }

        Widget chathead = client.getWidget(InterfaceID.ChatLeft.HEAD);
        if (chathead != null && chathead.getModelId() == STEVE_CHATHEAD)
        {
            log.debug("[dialog] chathead swapped to Nieve");
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
                if (log.isDebugEnabled())
                {
                    log.debug("[dialog] identity line replaced: {}", plain);
                }
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
            log.debug("[dialog] player option echo reworded");
            text.setText(NEW_MASTER_OPTION_REWRITE);
            return;
        }

        // The branch closes on condolences the rewritten one has no use for.
        if (CONDOLENCE_LINE.equals(plain))
        {
            log.debug("[dialog] condolence line reworded");
            text.setText(CONDOLENCE_REWRITE);
            return;
        }

        String raw = text.getText();
        if (raw != null && raw.contains(STEVE))
        {
            log.debug("[dialog] player line renamed Steve to Nieve");
            text.setText(raw.replace(STEVE, NIEVE));
        }
    }

    /**
     * The chat option that opens the backstory branch reads as if the player
     * doesn't know who she is; reword it while the swap is active.
     */
    private void dressChatOptions()
    {
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
                log.debug("[dialog] chat option reworded");
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

        log.debug("[contact] Steve row relabeled to Nieve");
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
        Widget[] lines = graveLines;
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
                log.debug("[grave] memorial rewritten");
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

    private String plainText(Widget widget)
    {
        String text = widget.getText();
        if (text == null)
        {
            return "";
        }
        String plain = plainTexts.get(text);
        if (plain == null)
        {
            if (plainTexts.size() >= PLAIN_TEXT_CACHE_SIZE)
            {
                plainTexts.clear();
            }
            // Dialogue boxes wrap with <br>; fold the breaks back to spaces so
            // exact-match comparisons see the line as written.
            String folded = Text.removeTags(text.replace("<br>", " "));
            plain = WHITESPACE.matcher(folded).replaceAll(" ").trim();
            plainTexts.put(text, plain);
        }
        return plain;
    }
}
