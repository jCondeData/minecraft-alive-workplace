package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Miners dig deeper as they level up (ROADMAP 36.22): every miner digs 4 to 64 deep, a Journeyman 96, an Expert 128 and
 * a Master down to just above bedrock. The test world is a superflat one with the tests standing on its floor, so a
 * deep pit is a column above the test area: the upper layers already dug (air, with the steps of the spiral stair left
 * standing), the lowest few solid rock that the miner really digs, handed over with the Quarry Marker as a player does.
 */
public class DeepQuarryGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	/** The pit, 3 × 3 (the smallest with stairs), x and z. */
	private static final int LO = 10;
	private static final int HI = 12;

	private record Deep(ServerLevel level, Villager miner, ServerPlayer player, List<Component> seen) {
	}

	/** A miner of villager level {@code villagerLevel} at a blast furnace with an iron pickaxe in the chest, and its owner. */
	private static Deep miner(GameTestHelper helper, int villagerLevel) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Miners.employ(level, villager, helper.absolutePos(BENCH));
		villager.setVillagerData(villager.getVillagerData().setLevel(villagerLevel));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		return new Deep(level, villager, player, seen);
	}

	/** Hands the miner a marker of the pit from the top layer {@code top} (relative), {@code depth} deep, as a player does. */
	private static QuarrySite handOver(GameTestHelper helper, Deep d, int top, int depth) {
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(d.level().dimension().location()),
			Optional.of(helper.absolutePos(new BlockPos(LO, top, LO))), Optional.of(helper.absolutePos(new BlockPos(HI, top, HI))), depth));
		Miners.assign(d.player(), d.miner(), marker);
		QuarrySite site = Miners.activeSite(d.level(), d.miner());
		helper.assertTrue(site != null, "the miner didn't take the marker: " + QaStageFourGameTests.keys(d.seen()));
		Leftovers.after(helper, () -> {
			QuarrySiteManager.get(d.level()).remove(site.id());
			// the column stands above the test area, where nothing is cleared between batches
			for (BlockPos p : BlockPos.betweenClosed(LO - 1, -1, LO - 1, HI + 1, top + 1, HI + 1)) {
				helper.getLevel().setBlock(helper.absolutePos(p), Blocks.AIR.defaultBlockState(), 2);
			}
		});
		return site;
	}

	/**
	 * Builds the pit's column: rock from {@code rockFrom} up to {@code rockTo} (with a coal ore in the middle of the
	 * lowest layer, which is never a step), already dug above it up to {@code top} except the steps the miner left, and
	 * a block of rock under {@code rockFrom} that is not in the quarry.
	 */
	private static void column(GameTestHelper helper, QuarrySite site, int rockFrom, int rockTo, int top) {
		for (int y = rockFrom - 1; y <= top; y++) {
			for (int x = LO; x <= HI; x++) {
				for (int z = LO; z <= HI; z++) {
					BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
					boolean rock = y <= rockTo || site.isStep(abs);
					helper.getLevel().setBlock(abs, (rock ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
				}
			}
		}
		// the pit's walls round the rock (outside the quarry, never dug): the miner digs in a pit, not on a pillar
		for (BlockPos p : BlockPos.betweenClosed(LO - 1, rockFrom - 1, LO - 1, HI + 1, rockTo + 2, HI + 1)) {
			if (p.getX() == LO - 1 || p.getX() == HI + 1 || p.getZ() == LO - 1 || p.getZ() == HI + 1) {
				helper.getLevel().setBlock(helper.absolutePos(p), Blocks.STONE.defaultBlockState(), 2);
			}
		}
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(LO + 1, rockFrom, LO + 1)), Blocks.COAL_ORE.defaultBlockState(), 2);
	}

	/** Every layer of the pit from {@code from} to {@code to} (relative) is dug out but for its step, which stands. */
	private static void assertDugWithSteps(GameTestHelper helper, QuarrySite site, int from, int to) {
		for (int y = from; y <= to; y++) {
			int steps = 0;
			for (int x = LO; x <= HI; x++) {
				for (int z = LO; z <= HI; z++) {
					BlockPos rel = new BlockPos(x, y, z);
					if (site.isStep(helper.absolutePos(rel))) {
						steps++;
						helper.assertTrue(helper.getBlockState(rel).isCollisionShapeFullBlock(helper.getLevel(), helper.absolutePos(rel)),
							"the step at " + rel + " is gone: " + helper.getBlockState(rel));
					} else {
						helper.assertBlockPresent(Blocks.AIR, rel);
					}
				}
			}
			helper.assertTrue(steps == 1, steps + " steps in layer " + y);
		}
	}

	/** Every layer from {@code from} to {@code to} (relative) is still whole rock: nothing was dug there. */
	private static void assertUntouched(GameTestHelper helper, int from, int to) {
		for (int y = from; y <= to; y++) {
			for (int x = LO; x <= HI; x++) {
				for (int z = LO; z <= HI; z++) {
					BlockPos rel = new BlockPos(x, y, z);
					helper.assertTrue(!helper.getBlockState(rel).isAir(), "dug below the miner's depth at " + rel);
				}
			}
		}
	}

	/**
	 * The spiral stair reaches the bottom: one step in every layer from the top down to the floor, each a block along the
	 * wall from the one above it.
	 */
	private static void assertStairToTheBottom(GameTestHelper helper, QuarrySite site) {
		BoundingBox box = site.box();
		BlockPos last = null;
		for (int y = box.maxY(); y >= box.minY(); y--) {
			BlockPos step = null;
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					if (site.isStep(new BlockPos(x, y, z))) {
						helper.assertTrue(step == null, "two steps at y=" + y);
						step = new BlockPos(x, y, z);
					}
				}
			}
			helper.assertTrue(step != null, "no step at y=" + y + ": the stair stops short of the bottom (" + box.minY() + ")");
			helper.assertTrue(helper.getLevel().getBlockState(step).isCollisionShapeFullBlock(helper.getLevel(), step), "the step at y=" + y + " isn't standing");
			if (last != null) {
				int dx = Math.abs(step.getX() - last.getX());
				int dz = Math.abs(step.getZ() - last.getZ());
				helper.assertTrue(dx + dz == 1, "the step at y=" + y + " isn't next to the one above it");
			}
			last = step;
		}
	}

	private static boolean finished(ServerLevel level, QuarrySite site) {
		return QuarrySiteManager.get(level).get(site.id()) == null;
	}

	private static String progress(GameTestHelper helper, Deep d, QuarrySite site) {
		return "still digging: " + Math.round(site.progress() * 100) + "% (" + site.status() + ", " + site.mined() + " dug, "
			+ site.skipped() + " left, at " + (site.current() == null ? "-" : helper.relativePos(site.current())) + "), miner at "
			+ helper.relativePos(d.miner().blockPosition());
	}

	/**
	 * A Master handed a "down to bedrock" marker takes the whole pit, 101 blocks deep down to just above the world's
	 * floor, without a word about levels, and digs the last 8 layers, 94 to 101 blocks down, keeping the steps; the
	 * block under the floor is left, the ore comes back to the chest, and the stair reaches the bottom.
	 */
	//$ gametest_ticks_batch AREA '4000' '"deepQuarryMaster"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "deepQuarryMaster")
	public void masterDigsPastSixtyFourDownToBedrock(GameTestHelper helper) {
		Deep d = miner(helper, 5);
		int top = 100;
		QuarrySite site = handOver(helper, d, top, QuarryData.BEDROCK);
		int floor = d.level().getMinBuildHeight() + 5;
		helper.assertTrue(site.box().minY() == floor && site.box().maxY() == helper.absolutePos(new BlockPos(0, top, 0)).getY(),
			"a Master's bedrock quarry should go from the marked top to Y=" + floor + ", not " + site.box());
		helper.assertTrue(site.box().getYSpan() > QuarryData.ANY_LEVEL_DEPTH && !site.wantsDeeper() && site.depth() == QuarryData.BEDROCK,
			"the whole pit for a Master: " + site.box().getYSpan() + " deep, wants deeper " + site.wantsDeeper());
		helper.assertFalse(QaStageFourGameTests.saw(d.seen(), "message.aliveworkplace.quarry.too_deep"), "a Master was told they can't dig that deep");
		int bottom = helper.relativePos(new BlockPos(0, floor, 0)).getY();
		column(helper, site, bottom, bottom + 7, top);
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(d.level(), site), progress(helper, d, site));
			assertDugWithSteps(helper, site, bottom, bottom + 7);
			assertUntouched(helper, bottom - 1, bottom - 1);
			assertStairToTheBottom(helper, site);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COAL) >= 1, "the coal from 101 blocks down isn't in the chest");
			helper.assertTrue(d.miner().isAlive(), "the miner didn't make it");
		});
	}

	/**
	 * A Novice handed a 128-deep marker (128 down to the world's floor) is told plainly that they'll stop 64 blocks down
	 * and that all 128 needs an Expert; they dig to 64 and not a block further, and when they finish they say they
	 * stopped short.
	 */
	//$ gametest_ticks_batch AREA '4000' '"deepQuarryNovice"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "deepQuarryNovice")
	public void noviceHandedA128MarkerStopsAt64(GameTestHelper helper) {
		Deep d = miner(helper, 1);
		// 128 deep down to the world's floor, the top within the 128 blocks of the blast furnace a quarry may be
		int top = helper.relativePos(new BlockPos(0, d.level().getMinBuildHeight() + 5, 0)).getY() + 127;
		QuarrySite site = handOver(helper, d, top, 128);
		helper.assertTrue(site.box().getYSpan() == 64 && site.wantsDeeper() && site.box().maxY() - site.wantedBottom() + 1 == 128 && site.depth() == 128,
			"a Novice's quarry should be cut off 64 down, wanting 128: " + site.box() + ", wanted bottom " + site.wantedBottom());
		Component told = QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.too_deep");
		helper.assertTrue(told != null, "the player wasn't told the miner's level isn't enough: " + QaStageFourGameTests.keys(d.seen()));
		QaStageFourGameTests.assertRendered(helper, told, "Novice", "64", "128", "Expert");
		int cut = top - 63; // the lowest layer a Novice digs
		column(helper, site, cut - 4, cut + 3, top);
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(d.level(), site), progress(helper, d, site));
			assertDugWithSteps(helper, site, cut, cut + 3);
			assertUntouched(helper, cut - 5, cut - 1);
			Component shortly = QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.stopped_short");
			helper.assertTrue(shortly != null, "finished short of the marker without saying so: " + QaStageFourGameTests.keys(d.seen()));
			QaStageFourGameTests.assertRendered(helper, shortly, "64", "128", "Expert");
			helper.assertTrue(d.miner().isAlive(), "the miner didn't make it");
		});
	}

	/**
	 * Saved and loaded deep in a quarry a Novice was handed a 96 marker for (68 deep down to the world's floor): the
	 * quarry keeps its place, its floor and the depth it wants. Then the miner reaches Journeyman, the owner hears they'll
	 * dig on, and they take it down to the floor the marker asked for, the stair with them.
	 */
	//$ gametest_ticks_batch AREA '4000' '"deepQuarryReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "deepQuarryReload")
	public void deepQuarrySurvivesSaveAndLevelUp(GameTestHelper helper) {
		Deep d = miner(helper, 1);
		int floor = d.level().getMinBuildHeight() + 5;
		int bottom = helper.relativePos(new BlockPos(0, floor, 0)).getY();
		int top = bottom + 67;
		QuarrySite first = handOver(helper, d, top, 96);
		helper.assertTrue(first.box().getYSpan() == 64 && first.wantedBottom() == floor,
			"a Novice's quarry should stop 64 down and want the world's floor: " + first.box() + ", wanted bottom " + first.wantedBottom());
		QaStageFourGameTests.assertRendered(helper, QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.too_deep"), "Journeyman", "68");
		column(helper, first, bottom, bottom + 9, top);
		QuarrySite[] site = {first};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (!reloaded[0] && site[0].mined() >= 4) {
				reloaded[0] = true;
				long before = site[0].cursor();
				BlockPos at = site[0].current();
				CompoundTag tag = site[0].save();
				helper.assertTrue(tag.contains("wanted_bottom"), "the floor the marker asked for isn't saved");
				QuarrySiteManager manager = QuarrySiteManager.get(d.level());
				manager.remove(site[0].id());
				QuarrySite loaded = QuarrySite.load(tag);
				helper.assertTrue(loaded != null && loaded.box().equals(site[0].box()) && loaded.wantedBottom() == floor && loaded.wantsDeeper()
					&& loaded.depth() == 96 && loaded.cursor() == before && java.util.Objects.equals(loaded.current(), at) && loaded.hasStairs(),
					"the quarry changed in a save and reload: " + (loaded == null ? null : loaded.box() + ", wanted " + loaded.wantedBottom()));
				manager.restore(loaded);
				site[0] = loaded;
				// they reach Journeyman, as a miner does from the XP of what they dig
				for (int i = 0; i < 10 && BuilderLevels.level(d.miner()) < 3; i++) {
					BuilderLevels.addXp(d.level(), d.miner(), 80, d.player().getUUID());
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reloaded: " + progress(helper, d, site[0]));
			helper.assertTrue(BuilderLevels.level(d.miner()) >= 3, "the miner didn't reach Journeyman");
			helper.assertTrue(finished(d.level(), site[0]), progress(helper, d, site[0]));
			helper.assertTrue(site[0].box().minY() == floor && !site[0].wantsDeeper(), "the quarry didn't go deeper: " + site[0].box());
			Component deeper = QaStageFourGameTests.find(d.seen(), "message.aliveworkplace.quarry.deeper");
			helper.assertTrue(deeper != null, "the owner wasn't told the miner digs on: " + QaStageFourGameTests.keys(d.seen()));
			QaStageFourGameTests.assertRendered(helper, deeper, "Journeyman", "68");
			helper.assertFalse(QaStageFourGameTests.saw(d.seen(), "message.aliveworkplace.quarry.stopped_short"), "said they stopped short");
			assertDugWithSteps(helper, site[0], bottom, bottom + 9);
			assertUntouched(helper, bottom - 1, bottom - 1);
			assertStairToTheBottom(helper, site[0]);
			helper.assertTrue(d.miner().isAlive(), "the miner didn't make it");
		});
	}

	/**
	 * The depth rules, the marker's words and the stair over a 128-deep pit and one down to bedrock, opened as a
	 * Novice's 64 and taken down as the miner levels up; old saves keep their depth.
	 */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void deeperChoicesFollowTheMinersLevel(GameTestHelper helper) {
		helper.assertTrue(QuarryData.levelFor(4) == 1 && QuarryData.levelFor(64) == 1 && QuarryData.levelFor(QuarryData.STRIP_MINE) == 1
			&& QuarryData.levelFor(96) == 3 && QuarryData.levelFor(128) == 4 && QuarryData.levelFor(QuarryData.BEDROCK) == 5, "the levels each depth needs");
		helper.assertTrue(QuarryData.maxDepth(1) == 64 && QuarryData.maxDepth(2) == 64 && QuarryData.maxDepth(3) == 96 && QuarryData.maxDepth(4) == 128
			&& QuarryData.maxDepth(5) == Integer.MAX_VALUE, "how deep each level digs");
		var dim = Optional.of(helper.getLevel().dimension().location());
		QuarryData bedrock = new QuarryData(dim, Optional.of(new BlockPos(0, 100, 0)), Optional.of(new BlockPos(2, 100, 2)), QuarryData.BEDROCK);
		helper.assertTrue(bedrock.area(-59).orElseThrow().minY() == -59 && bedrock.area().orElseThrow().minY() == QuarryData.OVERWORLD_FLOOR
			&& bedrock.area(5).orElseThrow().minY() == 5, "down to bedrock is down to the world's floor");
		helper.assertTrue(bedrock.next().isStripMine() && new QuarryData(dim, Optional.empty(), Optional.empty(), 64).next().depth() == 96,
			"the cycle: 64, 96, 128, bedrock, then the strip mines");
		// the marker's words name the level the deeper choices need
		String said128 = io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem.depthChoice(128, "message.aliveworkplace.quarry.depth").getString();
		String saidBedrock = io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem.depthChoice(QuarryData.BEDROCK, "tooltip.aliveworkplace.quarry.depth").getString();
		String said64 = io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem.depthChoice(64, "message.aliveworkplace.quarry.depth").getString();
		helper.assertTrue(said128.contains("128") && said128.contains("Expert") && saidBedrock.contains("bedrock") && saidBedrock.contains("Master")
			&& said64.equals("Dig 64 blocks deep."), "the marker says: " + said128 + " / " + saidBedrock + " / " + said64);

		// A Novice's 64 of a 128 marker, cut off and taken down to an Expert's 128, then a Master's bedrock
		BoundingBox full = new BoundingBox(0, -59, 0, 2, 100, 2);
		BoundingBox novice = Miners.withinLevel(full, 1);
		helper.assertTrue(novice.minY() == 37 && novice.maxY() == 100 && Miners.withinLevel(full, 3).minY() == 5
			&& Miners.withinLevel(full, 4).minY() == -27 && Miners.withinLevel(full, 5).equals(full), "cut off by level: " + novice);
		QuarrySite site = new QuarrySite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "", dim.get(), novice, QuarryData.BEDROCK);
		site.planStairs(new BlockPos(-3, 100, 0));
		site.setWantedBottom(full.minY());
		for (int i = 0; i < 9 * 60; i++) {
			site.advance(true, false);
		}
		BlockPos at = site.current();
		List<BlockPos> order = new ArrayList<>();
		for (long i = 0; i < 9L * 64; i++) {
			order.add(site.at(i));
		}
		helper.assertTrue(!site.deepenTo(QuarryData.lowestFor(2, 100)), "an Apprentice takes it no deeper");
		helper.assertTrue(site.deepenTo(QuarryData.lowestFor(4, 100)) && site.box().minY() == -27 && site.wantsDeeper(), "an Expert takes it to 128: " + site.box());
		helper.assertTrue(site.current().equals(at), "deepening moved the miner's place");
		for (long i = 0; i < order.size(); i++) {
			helper.assertTrue(site.at(i).equals(order.get((int) i)), "deepening changed the digging order at " + i);
		}
		helper.assertTrue(site.deepenTo(Integer.MIN_VALUE) && site.box().minY() == -59 && !site.wantsDeeper(), "a Master takes it to bedrock: " + site.box());
		helper.assertTrue(!site.deepenTo(Integer.MIN_VALUE), "never below the floor the marker asked for");
		BlockPos last = null;
		for (int y = 100; y >= -59; y--) {
			BlockPos step = null;
			for (BlockPos p : BlockPos.betweenClosed(0, y, 0, 2, y, 2)) {
				if (site.isStep(p)) {
					helper.assertTrue(step == null, "two steps at y=" + y);
					step = p.immutable();
				}
			}
			helper.assertTrue(step != null, "the stair stops at y=" + y);
			helper.assertTrue(last == null || Math.abs(step.getX() - last.getX()) + Math.abs(step.getZ() - last.getZ()) == 1, "the stair breaks at y=" + y);
			last = step;
		}

		// Saves: the wanted floor kept, and a quarry saved before 36.22 keeps its own depth
		QuarrySite cut = new QuarrySite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "", dim.get(), novice, 128);
		cut.setWantedBottom(-27);
		QuarrySite back = QuarrySite.load(cut.save());
		helper.assertTrue(back != null && back.wantedBottom() == -27 && back.wantsDeeper() && back.box().equals(novice), "the wanted floor wasn't saved");
		CompoundTag old = cut.save();
		old.remove("wanted_bottom");
		QuarrySite before = QuarrySite.load(old);
		helper.assertTrue(before != null && !before.wantsDeeper() && before.box().equals(novice) && !before.deepenTo(Integer.MIN_VALUE),
			"an old save went deeper than it was");
		QuarrySite strip = new QuarrySite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "", dim.get(), new BoundingBox(0, 10, 0, 8, 11, 8), 2);
		strip.setStripMine(true);
		strip.setWantedBottom(-50);
		helper.assertTrue(!strip.deepenTo(Integer.MIN_VALUE), "a strip mine never deepens");
		helper.succeed();
	}
}
