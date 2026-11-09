# Status cards: the Nurse and the Fossil Scientist

An empty hand on a Nurse or a Fossil Scientist opens a small card in the Village Hall's look instead of acting at once:
the Nurse's shows your health and your party's, with **Heal**; the Fossil Scientist's shows how far along each of your
fossils is, with **Hand over**. Each button does exactly what the click did before. The card is the shared piece the
job-status cards of 42.5 reuse. Part of Milestone 43 (One look for every screen, Pokémon).

Roadmap items: 43.3

## What a player sees

**The Nurse.** Right-click her with an empty hand. A small dark card with a slate-blue edge opens: her name in gold,
"Nurse · Expert" on the right, and a gray line saying what she does and how often ("Heals you and your whole team, once
every 42 seconds."; at a Healing Machine, "Puts your team in her Healing Machine…"). Under **Your health and your
party**: a line for you with your head, "14 / 20 HP" and a health bar (green when full, orange when hurt), a red line
for each bad effect she'd clear ("Poison: to be cleared"), and with Cobblemon a line per Pokémon with its model, name
and level, its HP and a bar ("Fainted" in red). **Heal** (gold) treats you as the click did: health back, bad effects
cleared, the party healed (or put in her Healing Machine), her words, the hearts and the chime. When she'd refuse, Heal
is gray and her reason is under it: asleep, you're in a battle, "Joy just treated you. Come back in 42 seconds.", or
her Healing Machine is busy with another team. **Close** closes. Without Cobblemon the card shows only your own lines.

**The Fossil Scientist** (with Cobblemon). Right-click them with an empty hand: their name and "Fossil Scientist ·
Novice", then "A revival costs 800 CobbleDollars. Working on 2 of 3 fossils." Under **Your fossils**: a line per fossil
you handed over, with its icon, its name and how far along it is ("Dome Fossil: 40%") over a gold bar, or "Ready" in
green. With none, the old hint: "Hand them a fossil to revive it, for 8 emeralds." **Hand over** brings you every
Pokémon that is ready (into your party, or your PC when it's full), as the empty-hand click did. It is gray while
nothing of yours is ready ("Nothing of yours is ready yet. It comes to you by itself when it is.") or you have handed
over nothing ("Hold a fossil and right-click them to hand it over."). Holding a fossil, a right-click still hands it
over at once, with every answer as before (the other half of a Galar fossil, a full queue, too little money).

A sneak-click on either still opens their trades. Without Cobblemon, an empty hand on a Fossil Scientist still says they
need it.

## How it works

**One card for every villager.** `StatusCard` is what a card shows (a title and a tag, a gray line, a heading over
labeled lines with an icon, a value in its color and a bar, a line for when there are none, buttons with the reason
each is grayed, a footer hint), worked out on the server. `StatusCardMenu.open` opens the menu type
`aliveworkplace:status_card` with it (the platform's menu-with-data, as the Trainer screen) and an action for its
buttons; the client's `StatusCardScreen` draws it with `TableStyle`'s small-card pieces (the header, a line and a bar,
in their own section) at the size of `docs/design/M42/trainer_c.png`, as tall as its lines. A button press
closes the card and runs the action; Close only closes.

**The Nurse.** `NurseCard` builds her card; Heal calls `Nurses.treat`, unchanged. `Nurses.refusal` asks the same checks
as `treat`, in the same order, without changing anything (her wait is not moved, the machine is not touched), so Heal
is grayed out with the very words a click would say. The Pokémon lines and the busy machine come from Cobblemon's side
through the `PokemonHealing` extension point.

**The Fossil Scientist.** `FossilCard` builds their card from the saved queue (`fossil_revivals`); Hand over calls
`FossilScientists.check`, unchanged.

## Switches

none

## Saved data

none (the cards read the Fossil Scientist's saved queue and save nothing)

## Items, blocks, jobs, commands

- `aliveworkplace:status_card`: the card's menu type.
- New text: `screen.aliveworkplace.card.tag`, `screen.aliveworkplace.card.close`, `screen.aliveworkplace.card.nurse.*`
  (you, hp, fainted, pokemon, effect, the three subtitles, the two headings, heal, hint) and
  `screen.aliveworkplace.card.fossil.*` (subtitle, heading, ready, percent, hand_over, none, not_ready, hint).

## Decisions

- 43.3: 42.5's card wasn't built yet, so this item built the card as a shared piece (`StatusCard`, `StatusCardMenu`,
  `StatusCardScreen` and `TableStyle`'s small-card section) for 42.5's job cards to reuse.
- 43.3: the card opens on an empty-hand click, as the Trainer screen; a fossil in hand is still handed over at once
  (the card would only add a click), and a sneak-click still trades.
- 43.3: "Hand over" is the scientist handing over what's ready, the empty-hand click's job; the Pokémon also comes by
  itself when it's done, so "Ready" shows only for a fossil finished while its owner was away.
- 43.3: a button the card shows gray still does what the click did when a client sends it anyway (her answer, no
  treatment), so nothing depends on the client.

## Known limits

- The card is a snapshot: a fossil's progress and the party's HP are as they were when it opened.

## Proof

`NurseCardGameTests.nurseCardOpensOnAClickAndHealTreats`, `NurseCardGameTests.nurseCardGraysHealDuringTheWait`,
`NurseCardGameTests.nurseCardGraysHealWhileSheSleeps`, `NurseCardGameTests.nurseCardNotOnASneakClick`,
`NurseCardGameTests.nurseCardAfterASaveAndReload`, `StatusCardCompatTests.nurseCardShowsThePartyAndHealHealsIt`,
`StatusCardCompatTests.nurseCardGraysHealWhileHerMachineIsBusy`, `StatusCardCompatTests.fossilCardShowsProgressAndHandOverDelivers`,
`StatusCardCompatTests.fossilCardWithNothingHandedOver`, `StatusCardCompatTests.fossilCardAfterASaveAndReload` (Cobblemon),
the click tests of `QaNurseWait1103dGameTests`, `QaNurseMachine1102dCompatTests` and `QaFossilScientist1102dCompatTests`
(now through the card's button) and `QaEveryMenuGameTests.everyServerMenuAndItsClientCopyAgreeSlotForSlot`.

QA (lane D, 2026-11-09): `QaPokemonScreens1109dCompatTests` (cards part, 9): Heal with everyone at full health; Heal
pressed after the Nurse fell asleep with the card open (refused, nothing healed); Heal from a player who walked away;
Heal pressed twice heals once; Hand over with something else in hand, with another fossil, twice in a row and with a
full party loses and duplicates nothing; a fossil's progress near and after its end. Mutant killed: the Nurse's asleep
check skipped.

Showcase scenes: `nurse`, `fossil`
