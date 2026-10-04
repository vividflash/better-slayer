# Better Slayer

Get your imbued heart.\
Assists with Mortimer and normal Masters.\
Helps you pick the best tasks and the right master, and **brings back
Nieve** while we're at it.\
Each part can be toggled.

## Mortimer: Task Choice Odds

Shows what each task Mortimer offers is worth per superior spawned.

- Panel over his interface, with the unique boost shown next to the odds when
  an option carries one.
- Estimated task time next to the odds.
- Picked option colored green, in the panel and on its name in his list.
- Set to show the panel, the highlight, both, or neither.
- Odds quoted for whichever drop you are after.

The four settings for **Odds for**, each with what a slayer 95 hydra task
comes to before any modifier:

- **Slayer unique roll** `1 in 10`: a unique table is rolled at all, the
  outcome that drops nothing included.
- **Unique item** `1 in 18`: any item off either table, so the two
  battlestaves as well as the heart and the gem.
- **Heart or gem** `1 in 80`: an imbued heart or an eternal gem.
- **Imbued heart** `1 in 160`: an imbued heart on its own.

For comparison against that hydra task, a slayer 55 turoth task is `1 in 832`
for a heart, and `1 in 277` if its offer carries a 200% superior boost.

Which option gets picked is set by **Pick**:

- **Fastest**: the shortest task.
- **Fast + Best**: the shortest task, unless a boosted multicombat task is
  worth more per hour.
- **Balanced**: the most unique rolls per hour.
- **Max chance**: the best odds per superior.
- **Auto** (default): Fast + Best below 50 Mortimer tasks, Balanced from 50
  on.

Kills per hour for each task and the overhead per task are set under
**Task Choice speeds**.

## Master Rules

Five "every Xth task, use master Y" rules plus default. Highest matching
interval wins.\
Krystilia and Mortimer have their own task streak.

- **Block wrong masters**: consumes the `Assignment` click on any master
  other than the selected one.
- **Elite Kourend diary** and **Elite Western diary**: used for the point
  values.
- **Hide wrong masters on milestone**: removes `Assignment` from those masters
  while a rule matches.
- **Highlight correct master** and **Highlight wrong masters**: selected
  master outlined green, the rest red, while you're near them. Both colors
  are configurable.
- **Milestone chat reminder**: chat message when the next task hits a rule.
- **Show overlay near masters**: panel showing the next task number, which
  master to use, points now and points after the next task.

## Task Sorter

Sorts the slayer task list (the standalone list and the rewards Tasks tab) by
assignment weight or by name, optionally reversed. Weight falls back to
alphabetical when the list shows no odds.

## Nieve instead of Steve

Shows Nieve instead of Steve, covering her world model, name, chathead, menu
entries and dialogue. Steve's backstory boxes and the chat option asking about
the new master are reworded to fit her. With Nieve alive, the gravestone in the
stronghold remembers Glough instead, interface and examine text both.
