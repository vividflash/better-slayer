# Better Slayer

Slayer tweaks in one plugin. Each has its own toggle.

## Mortimer: Task Choice Odds

Mortimer offers a choice of two tasks, three once you have taken 50 of his,
and every offer carries one modifier. One of those modifiers raises the
superior unique chance, by 10% to 300% depending on the monster.

This reads the offer from the game's own task data and shows what each option
is worth per superior spawned.

- Panel over his interface, one line per option, with the unique boost shown
  next to the odds when an option carries one.
- Best option colored green, in the panel and on its name in his list.
- Set to show the panel, the highlight, both, or neither.
- Odds quoted for whichever drop you are after, set in the config.
- Options that cannot be resolved show no odds and are never marked best.

The four settings for "Odds for", listed with what a slayer 95 hydra task
comes to before any modifier:

| Setting | What it counts | Hydra task |
| --- | --- | --- |
| Slayer unique roll | A unique table is rolled at all, the outcome that drops nothing included | 1 in 10 |
| Unique item | Any item off either table, so the two battlestaves as well as the heart and the gem | 1 in 18 |
| Heart or gem | An imbued heart or an eternal gem | 1 in 80 |
| Imbued heart | An imbued heart on its own | 1 in 160 |

The odds are per superior spawned rather than per kill, since superiors spawn
at a flat 1 in 200 kills (1 in 150 with the elite Combat Achievements reward)
whichever task is taken. For comparison against that hydra task, a slayer 55
turoth task is 1 in 832 for a heart, and 1 in 277 if its offer carries a 200%
superior boost.

## Master Rules

Five "every Xth task, use master Y" rules plus a default master. Highest
matching interval wins. Krystilia and Mortimer run their own task streak.

- Recommended master outlined green, the rest red, while you're near them.
  Both colors are configurable.
- Panel near masters showing the next task number, which master to use, points
  now and points after the next task.
- Chat reminder when the next task hits a rule.
- Optionally eat the Assignment click on any master other than the recommended
  one, the default master included.
- Optionally drop the Assignment option from those masters entirely. That one
  applies only while a rule matches.
- Elite Western and elite Kourend diary toggles for the point values.

## Task Sorter

Sorts the slayer task list (the standalone list and the rewards Tasks tab) by
assignment weight or by name, optionally reversed. Weight falls back to
alphabetical when the list shows no odds.

## Nieve instead of Steve

Shows Nieve instead of Steve, covering her world model, name, chathead, menu
entries and dialogue. Steve's backstory boxes and the chat option asking about
the new master are reworded to fit her. With Nieve alive, the gravestone in the
stronghold remembers Glough instead, interface and examine text both.

## Attribution

Several features reimplement the behavior of third-party RuneLite plugins.
Credit to their authors; each plugin below is the behavioral reference for the
matching feature. All four are published under the BSD 2-Clause License, and
each author is credited by name in `LICENSE`.

| Feature | Reference plugin | Author | Repository |
| --- | --- | --- | --- |
| Master Rules (milestone reminder) | konar-milestone-reminder | michael-gutman (Michael Gutman) | https://github.com/michael-gutman/konar-milestone-reminder |
| Master Rules (streak/point math) | slayer-boosting | TheInsomnolent (Matthew Griffiths) | https://github.com/TheInsomnolent/slayer-boosting |
| Task Sorter | slayer-task-sorter | MJHylkema | https://github.com/MJHylkema/slayer-task-sorter |
| Nieve instead of Steve | nievive | claudiodekker (Claudio Dekker) | https://github.com/claudiodekker/runelite-nievive |

## License

BSD 2-Clause. See [LICENSE](LICENSE).
