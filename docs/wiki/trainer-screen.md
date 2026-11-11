# The Trainer screen

An empty hand on a Trainer or a Trainer Leader opens a page in the Village Hall's look instead of starting the battle
at once: who the trainer is, your record against them, the rules, the prize, their team, your party, and the buttons
Challenge, Trade and Close. Cobblemon's own battle screen is unchanged. Part of Milestone 43 (One look for every screen,
Pokémon).

Roadmap items: 43.1, B1091, B1102, B1104, B1122, B1131, B1157, B473

## What a player sees

Right-click a Trainer (or the Trainer Leader) with an empty hand. A dark panel with a slate-blue edge opens. On the
left: the trainer's name in gold, "Pokémon Trainer" or "Trainer Leader", their rank and village ("Expert · Oakbrook"),
the villager's portrait (it follows the mouse), **Your record** ("3 won · 1 lost", your own battles with this trainer),
**Today** ("You haven't battled", "You battled today", "You won today", or for a Leader "Today's challenge is used")
and **Rules** ("Singles · level cap 45" with Radical Cobblemon Trainers, "Singles · no level cap" without).

On the right: "Challenge <name>" and the prize ("Win to earn 500 CobbleDollars.", emeralds without CobbleDollars; once
you've had today's prize it says so). Then the trainer's team, one framed cell each with the real Pokémon model and its
level, as you'd meet it (brought down to your level cap with RCT: such a level is orange and its tooltip says so).
Until you beat the trainer once, only the first half of the team shows; the rest are "???" cells and a line says "Beat
<name> to see the rest of the team." Under it, **Your party**: a line per Pokémon with its level and state: "Ready" in
green, "Fainted: healed to battle" in red (the battle heals your party, as it always has), or "Over the cap of 45" in
orange with RCT.

Then the buttons. **Challenge** (gold) starts the battle exactly as a click did before; the screen closes and
Cobblemon's battle opens. When the trainer can't battle you now, Challenge is gray and the reason sits under it: away
at the Cup (or a Cup delegate), asleep, busy with another player, you're already in a battle, you have no Pokémon, not
the village's Leader (it names the one to challenge), one challenge a day (Leaders), or no Cobblemon. Only a
challenge that starts a battle uses up a Leader's one a day: a refused one (busy, already in a battle, no Pokémon)
leaves it for later the same day. A Leader's day turns over at sunrise (day time a multiple of 24000).
**Trade** opens
the trainer's trades, as a sneak-click does; it is gray while they're asleep or have nothing to sell. **Close** closes.
Sneak-click still goes straight to the trades. The three buttons always stay inside the panel (B1131): with room to
spare they keep their roomy width, in a small window or with longer translated labels they share out the room, and
when even that doesn't fit they wrap onto a second row (the status card's layout, `StatusCard.buttonRow`, B1103).

## How it works

**A menu with no slots.** `TrainerScreens.open` works out a `TrainerView` on the server (everything the page shows) and
opens the menu type `aliveworkplace:trainer` with it (the platform's menu-with-data, as the Mailbox's). The client's
`TrainerScreen` draws it with `TableStyle`'s pieces; its buttons are vanilla menu-button clicks, which
`TrainerMenu.clickMenuButton` turns into `Trainers.challenge` (Challenge), the villager's own interaction (Trade) or
closing (Close).

**The refusals, without side effects.** `Trainers.refusal` asks the same checks `Trainers.challenge` makes, in the same
order, and changes nothing; the Pokémon half (the player or trainer already battling, no party) comes from Cobblemon's
side through the `TrainerBattles` extension point, which also fills the team and the party (`TrainerView.Sides`). The
team is the one `CobblemonTrainers.challenge` would field, scaled to the player's RCT cap the same way.

**The same team every time** (B1104, B1122). A trainer's team is built afresh each time it's shown or fielded, from
the trainer's UUID: the species and levels, and since B1104 also each Pokémon's gender, shininess (at Cobblemon's
shiny rate) and random form, such as Wormadam's cloak (`CobblemonTrainers.settle`, each member with its own seeded
random). The form decides physical or special in training, so the held item, nature and moves follow it. So the
screen shows the same models on every opening and the battle fields exactly those; a Mega Stone holder brought in
for a Mega-Evolving trainer gets the same treatment. Nothing new is saved: an existing trainer's team just stops
changing (it may look different once, from the first opening after the update). This was also B473's rare flake:
a City Leader with a Wormadam fielded another cloak, and so another held item, from one look to the next
(`MegaTrainerCompatTests.aLeaderWithAWormadamIsTheSameAtEveryLook`). The form's choices are read through
`examples()`, which Cobblemon 1.7.3 and 1.8 both have (`CobblemonDaycareKeeper.choicesOf`, shared with the Daycare);
1.8.1 has no `ChoiceSpeciesFeatureProvider.getChoices()`, which threw `NoSuchMethodError` on the first team and
switched trainer battles and Mega trainers off (B1157). A choice feature whose default or value a version can't read
keeps the form Cobblemon rolled.

**The record.** Every battle with a trainer adds a win or a loss and the day to that player's record on the trainer
(`Trainers.battleOver`). One win shows the whole team for good.

## Switches

none

## Saved data

- On each Trainer and Trainer Leader: `trainer_records`, each player's wins, losses and the day of their last battle
  with them (new; missing on older saves, which read as no battles yet).

## Items, blocks, jobs, commands

- `aliveworkplace:trainer`: the Trainer screen's menu type.
- New text: `screen.aliveworkplace.trainer.challenge_heading`, `screen.aliveworkplace.trainer.reward`,
  `screen.aliveworkplace.trainer.reward.paid`, `screen.aliveworkplace.trainer.team`, `screen.aliveworkplace.trainer.team.hidden`,
  `screen.aliveworkplace.trainer.party`, `screen.aliveworkplace.trainer.party.ready`,
  `screen.aliveworkplace.trainer.party.fainted`, `screen.aliveworkplace.trainer.party.over_cap`,
  `screen.aliveworkplace.trainer.today.none`, `screen.aliveworkplace.trainer.today.won`,
  `screen.aliveworkplace.trainer.rules.cap`, `screen.aliveworkplace.trainer.button.challenge`,
  `screen.aliveworkplace.trainer.hint` and the refusal `message.aliveworkplace.trainer.in_battle`.

## Decisions

- Option A, the Hall page, for the Trainer (owner, 2026-11-08, from `docs/design/M42/trainer_a.png`).
- 43.1: "the ones not seen yet" are the second half of the team (rounded down) until the player's first win: the first
  half is what a trainer leads with. Tracking each Pokémon sent out in a battle would need Cobblemon's battle events
  for a small gain.
- 43.1: the party's "over the cap" line names the cap but doesn't claim the player's Pokémon are scaled: the mod's
  battles never scaled them (only the trainer's team comes down to the cap). A fainted Pokémon is no refusal, because
  the battle heals the party as it always did; its line says so.
- 43.1: the record is the player's own against this trainer (the mockup's "Record"), not the trainer's against everyone.
- 43.1: a player already in a battle used to get nothing from a click; the screen now names it ("You're already in a
  battle.").
- B1102: a trainer counts toward the village's battle quest and battle reform steps only when the player wins the
  battle (`Trainers.battleOver`), a Trainer Leader like any other trainer. Pressing Challenge, refused or not, counts
  nothing; each win counts once.

## Known limits

- With RCT, the team and the cap are worked out when the screen opens; a cap that rises while it is open shows on the
  next open.
- A very small window (a big GUI scale) cuts the longer lines with "…".

## Proof

`TrainerScreenCompatTests.clickOpensTheScreenInsteadOfABattle`, `TrainerScreenCompatTests.challengeStartsTheBattle`,
`TrainerScreenCompatTests.theTeamIsRevealedAfterAWin`, `TrainerScreenCompatTests.tradeOpensTheTrades`,
`TrainerScreenCompatTests.refusedWhileAsleep`, `TrainerScreenCompatTests.refusedWithNoTeam`,
`TrainerScreenCompatTests.refusedWhileBusyOrInABattle`, `TrainerScreenCompatTests.refusedWhenNotTheLeader`,
`TrainerScreenCompatTests.refusedUntilTomorrowByALeader`, `TrainerScreenCompatTests.refusedAwayAtTheCup`,
`TrainerScreenCompatTests.recordAndRevealSurviveAReload` (Cobblemon), `QaTrainerScreen1109dBugCompatTests.theTeamsModelsStayTheSameBetweenOpenings`,
`QaMegaTrainersBug1109dCompatTests.aTrainersTeamIsTheSameEveryTime` (Cobblemon), `TrainerScreenGameTests.withoutCobblemonTheScreenSaysSo`,
`TrainerScreenButtonsGameTests.trainerButtonsStayInsideThePanel`,
`QaEveryMenuGameTests.everyServerMenuAndItsClientCopyAgreeSlotForSlot`,
`QaLeaderBeatenCompatTests.aLeaderCountsOnlyWhenBeatenInBattle` (Cobblemon), `QaLeaderBeatenGameTests.aLeaderCountsOnlyWhenBeaten`
and `QaLeaderBeatenGameTests.anOrdinaryTrainerCountsWhenBeaten`.

QA (lane D, 2026-11-09): `QaPokemonScreens1109dCompatTests` (trainer part, 9): a screen left open while the trainer
fell asleep, started a battle with another player or died, or after the player walked away, starts no battle when
Challenge is pressed; Challenge pressed twice starts one battle; each party line says the Pokémon's state (no false
"over the cap" without RCT); the team a player revealed by winning stays hidden for another player; a Leader's rules
line reads "Singles · no level cap" without RCT; every refusal reads as English, no raw key. Mutant killed: the early
refusal (asleep, away, busy) skipped on Challenge.

Showcase scenes: `leader`, `battle`

QA (lane D, 2026-11-09, second run): `QaTrainerScreen1109dCompatTests` (11): only an empty main hand opens the
screen (an off-hand torch is not used); the away-at-the-Cup refusal only for a Leader who entered another village's
Cup today; the senior Leader may challenge; come back tomorrow ends the next day with the record kept; a reveal
outlasts the day and stays with its own trainer; hidden slots carry no model, level or scaled mark; forged, stale,
grayed-out and far-off button presses do nothing; the screen closes when the player walks 30 blocks off or the
trainer dies; a Leader's day and a loss survive a reload; the screen's labels are American English.

QA (lane D, 2026-11-09, third run, verifying B1091 and B1102): `QaLeaderVerify1109dCompatTests` (4) and
`QaLeaderVerify1109dGameTests` (3): each refusal on its own (no Pokémon, already in a battle with another trainer, the
Leader busy with another player) writes nothing on the Leader, even after a reload, and a later press the same day
starts the battle; after a battle a press on the day's last tick is refused with "Come back tomorrow!" and one on the
next day's first tick starts; presses, refusals and losses count 0 for the battle quest, each win 1, still 2 for two
wins two seconds later; a win counts for the trainer's own village only, and for none when the trainer stands outside
every village; two wins over an ordinary trainer in a day count 2; without Cobblemon a press spends and counts nothing.
Mutants killed 3/3: the day spent on any press, a loss counted, the day boundary one tick early.
