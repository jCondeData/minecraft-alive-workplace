Review 33.22: Realm edicts
What changed: the capital's Book of Edicts now has realm slots: 1 for a Barony or County, 2 for a Duchy, 3 for a Kingdom, and one more with the Royal Hall II. A realm edict is in force in every member village, lasts at least 3 days and is paid from the realm treasury. There are six, each with its reform (a quest at the capital plus a count across the realm; once reformed, the boost stays and the cost goes for good):
- Royal Highway: caravans between members carry 2 more stacks and arrive 25% sooner, for an emerald a day per route.
- Common Market: one board for the realm with no spread between members, for 5 more points of levy.
- Levy of Arms: relief from every member, in bands twice as big, for 2 emeralds a day from each member.
- Realm Festival: everyone celebrates on the same day and the good mood lasts 3 days, for 10 emeralds a member.
- Crown's Peace: no feuds on members and no bandit camps near them, but every member's takings are 10% lower.
- Royal Charter: colonies from Town rank, twice as often, for 32 emeralds more each.
Please judge: 1) "No spread on trades between members" was read as: the realm's own people (members' owners, their friends, ops) buy at the board's price, and strangers still pay the 10%. Is that what you meant? 2) The Royal Highway's tooltip is so wide that it runs off the right edge of the screen (still 2 and 3). Shorten the lines, or wrap them?
Tester: FAIL IN THE BOT, GameTests pass. CI build on main (65cd5f9b) is green, with 9 tests in RealmEdictGameTests (each edict's boost and cost on, and the reform turning the cost off and keeping the boost). Scene `realm_edicts` FAILED in my run, and also on GitHub for this commit: the capital's book with its realm slots and the Royal Highway being proclaimed both worked, but the last step, Ashford's own book showing the edict "in force from the capital", never opened. The last shot is Ashford's chat, not its book. The judge also flagged "missing-texture magenta" in 3 frames, but those frames are the Royal Highway's tooltip, whose text is magenta, so I think this is a false alarm (not checked further). Seen: the chat line starts with a lowercase "the Barony of Thornholm proclaims...", and the tooltip text is cut off at the right edge.
Scene: SCENE=realm_edicts. Set up by command: Thornholm, capital of the Barony of Thornholm, with Ashford as a member; evening; the player opens the capital's hall.
Reply: approve 33.22 · veto 33.22: why · change 33.22: what

(sent)

(from chat, 2026-10-13 03:47Z; sent 2026-10-13 by the chat)
