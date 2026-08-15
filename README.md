# Better Slayer

Slayer tweaks in one plugin. Each has its own toggle.

## Mortimer: Task Choice Odds

Mortimer offers a choice of tasks. This shows each option's chance of a slayer
unique (imbued heart or eternal gem) per superior spawned, and marks the best
pick green. Options it can't resolve are listed without odds.

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
the new master are reworded to fit her. His grave text is flipped the same way,
interface and examine text both.

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
