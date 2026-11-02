package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-02, lane-b-1102-1838's tester) of a Builder whose world changes under him mid-job, written from the spec
 * (CLAUDE.md "How the builder works": all progress lives in the saved site, restartable at any tick; docs/wiki/builder.md
 * "a Builder can stop at any tick (server stopped, chunk unloaded, Builder asleep) and carry on exactly where it was",
 * "a hired builder killed with a build ... drops ... the blueprint where he fell", "a block a player puts mid-build where
 * the drawing has another is taken out into the builder's chests", "nothing a build leaves is broken"; ROADMAP 23.1
 * "every build finishes; no item is duplicated or lost"), not from the code:
 * <ul>
 * <li>the builder killed mid-STRUCTURE, his drops gathered into the chest and his blueprint handed to a new villager at
 * the same bench: the hut finishes once, nothing doubled or lost;</li>
 * <li>the bench mined mid-STRUCTURE and put straight back: the same builder finishes;</li>
 * <li>a glass block put inside the room, where the drawing has air, mid-STRUCTURE: the build still ends, the glass kept
 * or handed back, never lost or made twice;</li>
 * <li>two saves and loads three ticks apart mid-DECORATION;</li>
 * <li>the builder put to bed at midnight mid-STRUCTURE builds and takes nothing asleep, and finishes in the morning;</li>
 * <li>two huts whose boxes touch, built at once on flat ground (one on a stone pad: QaBuilderMidJob1102BugTests).</li>
 * </ul>
 * Reuses QaBuilderTerrain1102GameTests' layout (the test hut from the chest and barrels by a Builder's Bench, its list
 * plus EXTRA of each, free materials off).
 */
public class QaBuilderMidJob1102GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = QaBuilderTerrain1102GameTests.LOGGER;
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final int EXTRA = QaBuilderTerrain1102GameTests.EXTRA;

	// ------------------------------------------------------------------------------------------- the builder killed

	/**
	 * The builder is killed mid-STRUCTURE with materials in his bag. builder.md: the build ends and its blueprint drops
	 * where he fell. A player gathers what he dropped into the chest, a new villager is made a builder at the same bench,
	 * and the player hands him the blueprint: he carries on at the same spot, the hut ends as drawn, and every material
	 * adds up (no block built twice, nothing the dead builder carried made again or lost).
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaMidJobKilled"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaMidJobKilled")
	public void aBuilderKilledMidStructureIsFollowedAtHisBenchWithNothingDoubled(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		QaBuilderTerrain1102GameTests.Run first = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.Run[] next = new QaBuilderTerrain1102GameTests.Run[1];
		BlueprintData.Placement placement = first.site().placement();
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaMjK"));
		ServerPlayer p = c.player();
		long[] killedAt = {-1};
		midStructureWithABag(helper, first, () -> {
			Villager dead = first.builder()[0];
			String at = QaBuilderTerrain1102GameTests.state(helper, first) + ", bag " + bag(dead, first.plan());
			dead.kill();
			killedAt[0] = helper.getTick();
			helper.assertTrue(first.site() == null, "the builder died but his site is still open: " + at);
			LOGGER.info("qa midjob killed: the builder killed with {}", at);
		});
		helper.onEachTick(() -> {
			if (killedAt[0] < 0 || next[0] != null || helper.getTick() - killedAt[0] < 5) {
				return;
			}
			// the player gathers what fell into the chest, and takes the blueprint
			ItemStack blueprint = ItemStack.EMPTY;
			Container chest = helper.getBlockEntity(first.supplies().get(0));
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
				ItemStack s = e.getItem();
				if (s.is(ModItems.BLUEPRINT)) {
					helper.assertTrue(blueprint.isEmpty(), "more than one blueprint fell when the builder died");
					blueprint = s.copy();
				} else if (first.plan().materials().containsKey(s.getItem())) {
					put(helper, chest, s.copy());
				}
				e.discard();
			}
			helper.assertTrue(!blueprint.isEmpty(), "the dead builder dropped no blueprint");
			// the spec doesn't say the dropped blueprint stays placed; if not, the player places it again on the same spot
			BlueprintData data = BlueprintItem.data(blueprint).orElseThrow(() -> new GameTestAssertException("the dropped blueprint holds no build"));
			helper.assertTrue(data.structure().equals(TEST_HUT), "the dropped blueprint is of " + data.structure() + ", not the hut");
			LOGGER.info("qa midjob killed: the dropped blueprint is {}", data.placement().map(Object::toString).orElse("not placed (placed again)"));
			if (data.placement().isEmpty()) {
				blueprint.set(ModComponents.BLUEPRINT, data.withPlacement(java.util.Optional.of(placement)));
			}
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
			Builders.employ(helper.getLevel(), v, helper.absolutePos(new BlockPos(2, 2, 2)));
			p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
			QaStageSixGameTests.clickVillager(p, v, c.seen());
			BuildSite again = Builders.activeSite(helper.getLevel(), v);
			helper.assertTrue(again != null, "the blueprint handed to the new builder started nothing: "
				+ c.seen().stream().map(net.minecraft.network.chat.Component::getString).toList());
			helper.assertTrue(again.placement().equals(placement), "the new builder builds somewhere else: " + again.placement() + " vs " + placement);
			UUID id = again.id();
			Leftovers.after(helper, () -> {
				if (BuildSiteManager.get(helper.getLevel()).get(id) != null) {
					BuildSiteManager.get(helper.getLevel()).remove(id);
				}
			});
			next[0] = new QaBuilderTerrain1102GameTests.Run(first.level(), new Villager[] {v}, id, first.plan(), 2, first.supplies(), new String[1]);
		});
		// the new builder's death watch (a tick callback can't add another)
		helper.onEachTick(() -> {
			QaBuilderTerrain1102GameTests.Run r = next[0];
			if (r != null && r.died()[0] == null && !r.builder()[0].isAlive()) {
				r.died()[0] = QaBuilderTerrain1102GameTests.state(helper, r) + " (last hurt by " + r.builder()[0].getLastDamageSource() + ")";
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(next[0] != null, "the builder isn't killed and followed yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, next[0]);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, next[0]);
		});
	}

	// ----------------------------------------------------------------------------------------------- the bench mined

	/**
	 * The builder's bench is mined mid-STRUCTURE (he carries materials) and the player puts it straight back on the same
	 * spot, 10 ticks later. It's the same bench in the same place: the builder carries on, the hut ends as drawn and every
	 * material adds up, the bench item the player picked up and placed again included.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaMidJobBench"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaMidJobBench")
	public void theBenchMinedAndPutStraightBackMidStructureFinishesTheHut(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		BlockPos bench = new BlockPos(2, 2, 2);
		boolean[] back = {false};
		midStructureWithABag(helper, r, () -> {
			String at = QaBuilderTerrain1102GameTests.state(helper, r) + ", bag " + bag(r.builder()[0], r.plan());
			r.level().destroyBlock(helper.absolutePos(bench), true);
			LOGGER.info("qa midjob bench: mined with {}", at);
			helper.runAfterDelay(10, () -> {
				int benches = 0;
				for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
					if (e.getItem().is(ModBlocks.BUILDERS_BENCH.asItem())) {
						benches += e.getItem().getCount();
						e.discard();
					}
				}
				helper.assertTrue(benches == 1, "the mined bench dropped " + benches + " benches");
				helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
				QaBuilderDisruptionGameTests.ensurePoi(r.level(), helper.absolutePos(bench));
				back[0] = true;
				LOGGER.info("qa midjob bench: put back, builder {}, {}", r.builder()[0].getVillagerData().getProfession(), QaBuilderTerrain1102GameTests.state(helper, r));
			});
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(back[0], "the bench isn't mined and put back yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// --------------------------------------------------------------------------------- a block where the drawing has air

	/**
	 * Mid-STRUCTURE, after CLEAR, the player puts a glass block in the middle of the room, where the drawing has air. The
	 * build still ends (it isn't held open by it), every drawn block stands, and the glass is either still standing or
	 * handed back into the chests: exactly one glass in all, never lost to a pickaxe nor made twice.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaMidJobAirSpot"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaMidJobAirSpot")
	public void aGlassBlockPutInTheRoomMidStructureNeitherHoldsTheBuildNorIsLost(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		BlockPos room = new BlockPos(8, 3, 8);
		helper.assertTrue(helper.getBlockState(room).isAir(), "setup: the middle of the room isn't air");
		boolean[] put = {false};
		midStructureWithABag(helper, r, () -> {
			helper.assertTrue(helper.getBlockState(room).isAir(), "mid-STRUCTURE the middle of the room isn't air: " + helper.getBlockState(room));
			helper.setBlock(room, Blocks.GLASS);
			put[0] = true;
			LOGGER.info("qa midjob air spot: glass put in the room, {}", QaBuilderTerrain1102GameTests.state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(put[0], "the glass isn't put yet");
			helper.assertTrue(r.died()[0] == null, "the builder died on the site: " + r.died()[0]);
			helper.assertTrue(r.site() == null, "still building: " + QaBuilderTerrain1102GameTests.state(helper, r));
			// the spec says what becomes of a player's block where the drawing has ANOTHER block, not where it has air: the
			// glass may stay, so its spot is left out of "as drawn"
			BlockPos glassAt = helper.absolutePos(room);
			List<BlockPos> wrong = r.plan().unfinished(r.level()).stream().filter(p -> !p.equals(glassAt)).toList();
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " drawn block(s) not as drawn after the build, e.g. "
				+ wrong.stream().limit(10).map(p -> helper.relativePos(p).toShortString() + "=" + r.level().getBlockState(p)).toList());
			LOGGER.info("qa midjob air spot: done with {} at the glass's spot", helper.getBlockState(room));
			int standing = QaBuilderTerrain1102GameTests.count(r, s -> s.is(Blocks.GLASS));
			int loose = QaBuilderTerrain1102GameTests.loose(helper, r, Items.GLASS);
			helper.assertTrue(standing + loose == 1, "the player's glass: " + standing + " standing in the hut and " + loose
				+ " in the chests, bags and on the ground (wanted one in all)");
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// ----------------------------------------------------------------------------------------- two reloads in a row

	/**
	 * Mid-DECORATION the world is saved and loaded (sites and builder), and three ticks later again, as a chunk unloaded
	 * and loaded twice in quick succession. The hut ends as drawn and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaMidJobTwoReloads"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaMidJobTwoReloads")
	public void twoReloadsThreeTicksApartMidDecorationDoubleNothing(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		int[] reloads = {0};
		long[] firstAt = {-1};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (reloads[0] >= 2 || s == null) {
				return;
			}
			if (reloads[0] == 0 && s.stage() == BuildPlan.Stage.DECORATION) {
				LOGGER.info("qa midjob reloads: first at {}, bag {}", QaBuilderTerrain1102GameTests.state(helper, r), bag(r.builder()[0], r.plan()));
				r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, r.level(), r.builder()[0]);
				firstAt[0] = helper.getTick();
				reloads[0] = 1;
			} else if (reloads[0] == 1 && helper.getTick() - firstAt[0] >= 3) {
				LOGGER.info("qa midjob reloads: second at {}, bag {}", QaBuilderTerrain1102GameTests.state(helper, r), bag(r.builder()[0], r.plan()));
				r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, r.level(), r.builder()[0]);
				reloads[0] = 2;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloads[0] == 2, "reloaded " + reloads[0] + " times so far (the hut may have no DECORATION stage: "
				+ QaBuilderTerrain1102GameTests.state(helper, r) + ")");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// -------------------------------------------------------------------------------------------------- night and bed

	/**
	 * The builder has a bed by the bench, and works slowly (workplaceBuildDelay 40). Mid-STRUCTURE midnight comes (the
	 * clock held at 18000): he goes to bed with the hut unfinished ("Builder asleep", carry on exactly where it was). Once
	 * 1300 ticks have passed (a vanilla villager's work stint runs to its end; that he builds on into the night at all is
	 * QaBuilderMidJob1102BugTests), no block of the hut changes for the rest of the night (he asleep for 300 ticks), nor
	 * does his bag. In the morning he finishes: the hut ends as drawn and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaMidJobNight"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaMidJobNight")
	public void aBuilderAsleepMidStructureBuildsNothingAndFinishesInTheMorning(GameTestHelper helper) {
		night(helper, 1300);
	}

	/** The night above, with {@code grace} ticks after midnight comes before the hut must stand still. */
	static void night(GameTestHelper helper, int grace) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(40, level.getServer());
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		Beds.home(r.builder()[0], helper.absolutePos(new BlockPos(1, 2, 8)));
		// 0 day, 1 night (grace, then watched), 2 asleep (watched), 3 morning
		int[] phase = {0};
		long[] since = {-1};
		long[] asleepAt = {-1};
		int[] unbuilt = {-1, -1};
		String[] bagAt = {null};
		helper.onEachTick(() -> {
			Villager b = r.builder()[0];
			if (phase[0] == 0) {
				helper.setDayTime(2000);
				return;
			}
			if (phase[0] == 3) {
				if (helper.getTick() % 20 == 0) {
					helper.setDayTime(2000);
				}
				return;
			}
			helper.setDayTime(18000);
			long night = helper.getTick() - since[0];
			int now = r.plan().unfinished(r.level()).size();
			if (night == grace) {
				unbuilt[1] = now;
				bagAt[0] = bag(b, r.plan()).toString();
				LOGGER.info("qa midjob night: {} ticks into the night {} unbuilt (at nightfall {}), asleep={}", grace, now, unbuilt[0], b.isSleeping());
			} else if (night > grace) {
				helper.assertTrue(now == unbuilt[1], "the hut changed at night, " + night + " ticks after midnight came: " + unbuilt[1]
					+ " unbuilt, then " + now + " (asleep=" + b.isSleeping() + ", day time " + helper.getLevel().getDayTime() % 24000 + ", activity "
					+ b.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("none") + ", schedule "
					+ net.minecraft.core.registries.BuiltInRegistries.SCHEDULE.getKey(b.getBrain().getSchedule()) + ", "
					+ QaBuilderTerrain1102GameTests.state(helper, r) + ")");
				helper.assertTrue(bag(b, r.plan()).toString().equals(bagAt[0]), "the builder's bag changed at night: " + bagAt[0] + " then " + bag(b, r.plan()));
			}
			if (phase[0] == 1 && b.isSleeping()) {
				phase[0] = 2;
				asleepAt[0] = helper.getTick();
				helper.assertTrue(r.site() != null, "setup: the hut was finished before its builder went to bed");
				LOGGER.info("qa midjob night: asleep {} ticks after midnight came, {}", night, QaBuilderTerrain1102GameTests.state(helper, r));
			} else if (phase[0] == 1 && night > grace + 1500) {
				helper.fail((grace + 1500) + " ticks after midnight came the builder isn't in bed: " + QaBuilderTerrain1102GameTests.state(helper, r));
			} else if (phase[0] == 2) {
				helper.assertTrue(b.isSleeping(), "the builder got up in the night: " + QaBuilderTerrain1102GameTests.state(helper, r));
				if (night > grace && helper.getTick() - asleepAt[0] >= 300) {
					phase[0] = 3;
					helper.setDayTime(1000);
					LOGGER.info("qa midjob night: morning, {}", QaBuilderTerrain1102GameTests.state(helper, r));
				}
			}
		});
		midStructureWithABag(helper, r, () -> {
			phase[0] = 1;
			since[0] = helper.getTick();
			unbuilt[0] = r.plan().unfinished(r.level()).size();
			LOGGER.info("qa midjob night: midnight comes at {}, {} unbuilt", QaBuilderTerrain1102GameTests.state(helper, r), unbuilt[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 3, "the night isn't over yet (phase " + phase[0] + ")");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// ------------------------------------------------------------------------------------------- two sites side by side

	/**
	 * Two huts on flat ground whose boxes touch (hut A at x 6-10, hut B at x 11-15), each with its own builder, bench and
	 * chests, built at once. Neither undoes the other's: both end as drawn and every material of both adds up. (With
	 * hut B on a stone pad: QaBuilderMidJob1102BugTests.)
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaMidJobNeighborsFlat"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaMidJobNeighborsFlat")
	public void twoTouchingHutsOnFlatGroundBothFinishWithNothingLost(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		QaBuilderTerrain1102GameTests.Run a = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.Run b = startAt(helper, new BlockPos(24, 2, 8), new BlockPos(11, 2, 6));
		helper.assertTrue(a.plan().bounds().maxX() + 1 == b.plan().bounds().minX(), "setup: the huts' boxes don't touch: " + a.plan().bounds() + " " + b.plan().bounds());
		QaBuilderTerrain1102GameTests.watchDeath(helper, a);
		QaBuilderTerrain1102GameTests.watchDeath(helper, b);
		helper.succeedWhen(() -> {
			QaBuilderTerrain1102GameTests.assertFinished(helper, a);
			QaBuilderTerrain1102GameTests.assertFinished(helper, b);
			Map<Item, Integer> off = new HashMap<>();
			for (Item item : a.plan().materials().keySet()) {
				int got = QaBuilderTerrain1102GameTests.loose(helper, a, item) + supplies(helper, b, item);
				if (got != 2 * EXTRA) {
					off.put(item, got);
				}
			}
			helper.assertTrue(off.isEmpty(), "materials made or lost by the two huts: left loose " + off + ", wanted " + 2 * EXTRA + " of each");
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	/** QaBuilderTerrain1102GameTests.start's layout with the bench at {@code bench} (chests and barrels two blocks south of it), ground y 2. */
	static QaBuilderTerrain1102GameTests.Run startAt(GameTestHelper helper, BlockPos bench, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BlockPos chest = bench.south(2);
		List<BlockPos> supplies = List.of(chest, chest.west(), chest.east(), chest.east(2), chest.east(3));
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		for (BlockPos p : supplies) {
			helper.setBlock(p, p.equals(chest) ? Blocks.CHEST : Blocks.BARREL);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.east());
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement);
		if (site == null) {
			throw new GameTestAssertException("the second hut didn't start");
		}
		BuildPlan plan = site.plan(level);
		UUID id = site.id();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(id) != null) {
				BuildSiteManager.get(level).remove(id);
			}
		});
		List<ItemStack> stock = new ArrayList<>();
		plan.materials().forEach((item, n) -> {
			int left = n + EXTRA;
			while (left > 0) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		for (int i = 0; i < stock.size(); i++) {
			Container c = helper.getBlockEntity(supplies.get(supplies.size() - 1 - i / 27));
			c.setItem(i % 27, stock.get(i));
		}
		return new QaBuilderTerrain1102GameTests.Run(level, new Villager[] {builder}, id, plan, 2, supplies, new String[1]);
	}

	static int supplies(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, Item item) {
		int n = 0;
		for (BlockPos p : r.supplies()) {
			n += ((Container) helper.getBlockEntity(p)).countItem(item);
		}
		return n;
	}

	/** The hut's materials in the builder's bag. */
	private static Map<Item, Integer> bag(Villager v, BuildPlan plan) {
		Map<Item, Integer> out = new HashMap<>();
		for (Item item : plan.materials().keySet()) {
			int n = ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			if (n > 0) {
				out.put(item, n);
			}
		}
		return out;
	}

	/** Runs {@code then} once, mid-STRUCTURE: four or more blocks of the hut built since STRUCTURE began, and materials in the bag. */
	private static void midStructureWithABag(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, Runnable then) {
		int[] atStart = {-1};
		boolean[] done = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (done[0] || s == null || s.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			int unbuilt = r.plan().unfinished(r.level()).size();
			if (atStart[0] < 0) {
				atStart[0] = unbuilt;
			}
			if (atStart[0] - unbuilt < 4 || bag(r.builder()[0], r.plan()).isEmpty()) {
				return;
			}
			done[0] = true;
			then.run();
		});
	}

	private static void put(GameTestHelper helper, Container c, ItemStack stack) {
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack in = c.getItem(i);
			if (in.isEmpty()) {
				c.setItem(i, stack.copy());
				stack.setCount(0);
			} else if (ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
				int k = Math.min(stack.getCount(), in.getMaxStackSize() - in.getCount());
				in.grow(k);
				stack.shrink(k);
			}
		}
		c.setChanged();
		helper.assertTrue(stack.isEmpty(), "no room in the chest for what the builder dropped");
	}
}
