Review 35.6: Scaffolding
What changed: when a builder has nowhere to stand within reach of a block (a raised arm, a spire's tip, a dome's crown), they put up a column of vanilla scaffolding from the chests, climb it and build from the top. When the stage is done, or the build is cancelled, they take it all down and it goes back into the chests. With no scaffolding in the chests, it is asked for on the requests board (a carpenter makes it from bamboo and string). The build never waits for it: if there is none, the block is skipped as before. This works on every build, with a switch, "Builders Use Scaffolding" (on by default). The building style guide now allows raised parts.
Please judge: 1) Should builders wait for scaffolding instead of skipping the block when none is in the chests? (Right now they skip it, so builds never get stuck.) 2) Does the clip look like believable building to you?
Tester: PASS WITH RISKS. CI build on main (65cd5f9b) is green, with 8 tests in ScaffoldingGameTests: the 14-high pillar with an overhanging cap and no skipped blocks (no scaffolding left after, the chests hold as much as before), cancelling half-way, save and reload with scaffolds up, old saves, scaffolding missing and asked for, the carpenter's recipe, the switch off, and the folly's dome. Scene `scaffolding` PASSED in my run (and on GitHub). Seen in the pictures: the scaffolding itself is hard to make out at the clip's size, and partway through, the pillar has a gap in its middle while the builder is already on the cap. It's filled by the end, so the "Done" shot is whole, but you can see it in the clip. Also on GitHub's settings-screen scene: the label "Builders Use Scaffolding: OFF" is wider than its button.
Scene: SCENE=scaffolding. Set up by command: a builder, the test pillar's blueprint and a chest with the stone and the scaffolding.
Reply: approve 35.6 · veto 35.6: why · change 35.6: what

(sent)

(from chat, 2026-10-13 03:48Z; not yet sent)
