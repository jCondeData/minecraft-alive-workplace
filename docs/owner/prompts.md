# Prompts for building Alive Workplace

These prompts are for the Claude chat that builds the mod, and for the night runs. The rules live in the repo:
`CLAUDE.md` (how to work), `ROADMAP.md` (what to build and what "done" means), `docs/agent/review.md` (review
packages) and `docs/agent/sessions.md` (how the chat and the night runs share the work). So your prompts can stay
short. When you want a rule to last, have the chat write it into one of those files ("add this to CLAUDE.md: …"),
because instructions that only live in a chat are lost when it starts fresh or compacts.

## Contents
1. Start a new building chat
2. Start or continue a session
3. Replying to review packages
4. A new idea
5. A bug
6. Something visual with no clear direction yet
7. Cut a release
8. Health check
9. When the chat gets slow or confused
10. The night shift (scheduled runs)
11. Decisions waiting for you
12. What makes Claude work best
13. Appendix: question for Modrinth

---

## 1. Start a new building chat

The 1.0 plan was installed in the repo on 2026-09-29 by the planning chat. Use a new chat for building, with the same
connections as before (GitHub and your files): a chat that has read the old rules keeps following them. Paste:

```
You're the day chat for Alive Workplace, my Minecraft mod (github.com/jCondeData/minecraft-alive-workplace). I don't
write code and you have full control. Clone the repo, read CLAUDE.md and docs/agent/sessions.md, then follow the
session loop as "chat". Night runs work on the same repo while I sleep; tools/agent/sessions.py keeps you apart.
Show me looks as screenshots and behaviour as GIFs, and ask me only for real decisions.
```

## 2. Start or continue a session

The short version is usually enough, because CLAUDE.md has the rest:

```
Continue Alive Workplace (github.com/jCondeData/minecraft-alive-workplace) with the CLAUDE.md session loop.
```

With a limit, when you'll be away:

```
Continue Alive Workplace with the CLAUDE.md session loop. Work up to 4 items (or until you're blocked), send a
review package after each one, then write a handoff and stop.
```

## 3. Replying to review packages

Each package ends with the item's number. Reply in your main chat, even for packages a night run sent; it records
your reply for everyone:

- `approve 23.3` (several at once: `approve 23.3, 23.4, B4`)
- `veto 23.3: the overlay hides the builder's head; I want it smaller and above the site`
- `change 24.1: the builder's helmet should stay yellow`

You only get packages for things you can see or feel in game. Work with nothing to see (tests, tooling, internal
fixes) is accepted without you and listed in one line in the next message.

Always say why when you veto. The reason teaches Claude your taste. When a reason applies beyond one item, add:
`… and record this as a design decision`.

## 4. A new idea

The goal is 1.0, so new features wait unless they matter for it:

```
New idea: <your idea, in your words>. Don't build it yet. Write it as a roadmap item with a "Done when" (what I'd see
in game), tell me where it fits in the priorities and what it would push back, and wait for my go.
```

## 5. A bug

```
Bug: <what happened> when <what I did> (<where, which version>). Expected: <what should have happened>.
[attach latest.log, the crash report or a screenshot]
Add it with sessions.py bug, write a failing test that shows it first, fix it before the next item, and send a
review package for the fix.
```

## 6. Something visual with no clear direction yet

This is what worked for the tools:

```
For <the texture / outfit / build>, don't choose a direction yourself. Make a pick sheet of 10-12 clearly different
options (preview.py pick for textures, renders for builds), numbered, next to references I like. I'll reply with
numbers, then you do a second round around my picks.
```

## 7. Cut a release

```
Cut a release: check that every item since the last release is approved, run the tester's Full tier, bump the
version on a fresh main, move the CHANGELOG notes, push, and confirm the GitHub release appeared with the jar.
```

## 8. Health check (the Sunday night run does this; ask for it before a release too)

```
Health check: run the tester's Full tier (flake sweep, mutation sweep, pack boot, and the performance benchmark
compared with docs/performance.md) and give me a one-screen report: what's broken, what's risky, what got slower.
Add findings with sessions.py bug.
```

## 9. When the chat gets slow or confused

Start fresh when the chat repeats itself, forgets decisions, or takes much longer than usual. A new chat reads the
repo files and picks up where the old one stopped. Arguing past a second failed correction rarely helps.

In the old chat:

```
Land what's green, pause what isn't, write a handoff with sessions.py, push everything, and stop.
```

In the new chat:

```
You're continuing work on Alive Workplace (github.com/jCondeData/minecraft-alive-workplace). Read CLAUDE.md first,
then follow its session loop as "chat". The latest handoffs are under "Notes / blocked" in ROADMAP.md.
```

## 10. The night shift (scheduled runs)

**Yes, the night runs and your chat can work at the same time.** Each item is claimed first, and git refuses the
second of two simultaneous claims, so nothing is done twice. Two sessions never work in the same milestone at once,
so they don't edit the same code. Each works on its own branch and only lands on `main` after merging the newest
`main` in and building green, so `main` never breaks. A run that gets cut off loses nothing it pushed: the next one
continues from its branch. `docs/agent/sessions.md` has the details.

**The schedule.** The scheduled task "Alive Workplace nightly build" runs three times a night, at 1:45, 3:45 and
5:45 AM Central (`CRON_TZ=America/Chicago 45 1,3,5 * * *`), with the prompt below. A run has stopped at exactly 60
minutes before, so each is planned as about an hour. Each run uses your plan's usage like a chat would: if you run
short during the day, drop to one or two runs; if you have room, add 11:45 PM. The GitHub test workflow runs at
about 10 PM on GitHub's machines, free, so the night runs start from fresh results.

To change it, tell any chat with the scheduled-task tools (the planning chat has them): "Update the scheduled task
Alive Workplace nightly build: <new times or prompt>."

```
Night run for Alive Workplace, Jesse's Minecraft mod (Fabric 1.21.1, for his Cobbleverse friends server). Jesse is
asleep and has given you full control: work on your own, don't wait for him, and leave the project better and green.

1. Call add_repo with owner "jCondeData", repo "minecraft-alive-workplace", access "push", and clone it as that tool
   says. Run `date -u +%m%d-%H%M` and call yourself night-<that>.
2. Read CLAUDE.md, then "The night shift" in docs/agent/sessions.md, and follow them. Jesse's chat or another night
   run may be working at the same time: use tools/agent/sessions.py for every claim, landing and roadmap change,
   and never push code straight to main.
3. You can be cut off at any time: push your branch at least every 30 minutes, and wrap up after about 45 minutes.
4. Never publish to Modrinth or CurseForge, change the license, bump the version, force-push, or delete anything on
   GitHub other than your own item branch.
5. Jesse judges looks from screenshots and behaviour from GIFs. Send each review package (SendUserFile) as soon as
   its item lands, not at the end. Work with nothing to see lands with --no-review and gets one line in your last
   message.
6. End with the short morning message docs/agent/sessions.md describes.
```

## 11. Decisions waiting for you

The chat will ask for these when it reaches them. You can answer early:

- **Camels** (21.3): the game counts a saddled camel as a horse, so guards ride camels too, but the changelog only
  promises horses, donkeys and mules. Keep camels, or leave them out?
- **Release channel** (26.1): see the appendix. Worth deciding early, because it changes whether 23.9 (your own
  signature builds) matters.
- **Far from players** (23.6): does a village keep working when no player is nearby (it keeps its chunks loaded), or
  does it pause?
- **Performance targets** (25.2): the chat proposes them after the first measurement.

## 12. What makes Claude work best

This is from Anthropic's own guidance, and from what worked while building this mod.

- **Say what "done" looks like in game**, not how to code it. A "Done when" is the most useful sentence in any request.
- **Give the reason with the rule** ("…because players get stuck when…"). Claude follows reasons better than orders,
  and capital-letter warnings make it over-cautious rather than more careful.
- **One thing at a time.** Big changes go better when the chat writes a plan first and you approve it.
- **Pictures beat words.** Judge from review packages. When giving visual feedback, attach a screenshot or a
  reference image, the way the weapon sheet worked for the tools.
- **Let another agent check the work.** The chat that wrote a change isn't the one to judge it; the tester skill
  exists for that.
- **Ask for evidence, not reassurance.** "Make sure there are no bugs" can't be promised. Ask for the tester's verdict
  and its "not tested" list.
- **Keep CLAUDE.md short.** Add a rule only when the chat makes the same mistake twice, and remove rules that no longer
  apply. A long file gets followed less.
- **Start fresh rather than argue.** After two failed corrections, a new chat with a clearer prompt beats a third
  correction.

## 13. Appendix: question for Modrinth

Modrinth's AI rules have been enforced since 2026-09-27:
- a project made "entirely or almost entirely" with generative AI, with "little-to-no human input beyond prompting or
  testing", can only be unlisted, not shown in search;
- AI-assisted projects with significant human work are allowed, with the "Contains AI-generated content" disclosure;
- no page image may be "created or derived from generative AI output".

CurseForge only asks for a disclaimer on AI-altered showcase images that could mislead. If you want to ask Modrinth
before deciding, you could send (edit it so it's accurate):

```
Hi! I'm preparing to publish a Fabric 1.21.1 mod, Alive Workplace (villagers with jobs; open source, GPL-3.0,
github.com/jCondeData/minecraft-alive-workplace). Most of the code and the textures are written by an AI assistant
under my direction. I design every feature and make every decision, review and approve each change from in-game
screenshots, and [build the flagship buildings myself / other human work]. Under your AI policy, can this be
published publicly with the "Contains AI-generated content" disclosure, or only unlisted? And may the gallery show
in-game screenshots when the textures in them were drawn with AI help?
```
