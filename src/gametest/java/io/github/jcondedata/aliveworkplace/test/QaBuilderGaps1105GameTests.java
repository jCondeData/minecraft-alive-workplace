package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.BENCH;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.LOGGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ORIGIN;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.TALL_AREA;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.VILLAGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.assertBuilt;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.assertNoFluidInBasement;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.cottageFour;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.fill;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ground;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.oldBuild;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.rules;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.stacks;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.watchDeath;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.Extent;
import io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.Setup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-05), adversarial gaps in four recent builder fixes, written from the wiki: docs/wiki/basements.md (water,
 * lava and waterlogged blocks beside a basement or a Smithy's "Dig the cellar", B923/B924/B958) and docs/wiki/builder.md
 * (what a struck builder leaves out of the bolt's reach and the spot the message names, B952/B964; blocks over the
 * world's top, B937/B965). Setup and helpers are {@link QaBasementGround1104GameTests}'s and
 * {@link QaStruckAndTop1104GameTests}'s.
 */
public class QaBuilderGaps1105GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int PLANKS = 20;
	private static final int COBBLE = 7;
	private static final Pattern DROPPED_AT = Pattern.compile("dropped at (-?\\d+) (-?\\d+) (-?\\d+)\\.");

	// ------------------------------------------------------------------------------------------ Smithy cellar (B958)

	/**
	 * A Smithy finished before cellars; in the earth beside its cellar a waterlogged stone-brick stair (west), seagrass
	 * (east), kelp (south) and a waterlogged stair under the cellar's floor. Its builder's "Dig the cellar": when done,
	 * the whole Smithy is as drawn, no water in the cellar, the builder alive, the stairs drained and kept, the seagrass
	 * and kelp plugged (no water left at their spots).
	 */
	//$ gametest_ticks_batch TALL_AREA '20000' '"qaGapsCellarLogged"'
	@GameTest(template = TALL_AREA, timeoutTicks = 20000, batch = "qaGapsCellarLogged")
	public void waterloggedBlocksBesideASmithysCellarDigLeaveItDryAndAsDrawn(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		ResourceLocation id = StarterBlueprints.SMITHY.id();
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, ORIGIN);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.WATERLOGGED, true);
		List<BlockPos> spots = logged(helper, blueprint, List.of(stair, Blocks.SEAGRASS.defaultBlockState(), Blocks.KELP.defaultBlockState(), stair));
		rules(helper);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		Cellars.offer(level, villager);
		MerchantOffer trade = villager.getOffers().stream().filter(o -> BlueprintItem.data(o.getResult())
				.map(d -> d.structure().equals(id) && d.placement().equals(Optional.of(placement))).orElse(false))
			.findFirst().orElseThrow(() -> new GameTestAssertException("setup: no \"Dig the cellar\" trade for the old Smithy"));
		List<ItemStack> stock = new ArrayList<>();
		BuildPlan.create(Cellars.cellarOnly(blueprint), placement).materials().forEach((item, count) -> stock.addAll(stacks(item, count)));
		fill(helper, stock);
		BlueprintData data = BlueprintItem.data(trade.getResult()).orElseThrow();
		BuildSite site = Builders.start(level, villager, null, data.structure(), data.placement().orElseThrow());
		helper.assertTrue(site.isCellarOnly(), "setup: the job's blueprint didn't start a cellar dig");
		Setup s = new Setup(level, villager, site, site.plan(level), blueprint);
		watchDeath(helper, s);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			String state = QaBasementFluids1104fBugTests.basement(s);
			helper.assertTrue(state.startsWith("0 wrong, 0 wet"), "the cellar is dug, but: " + state);
			assertNoFluidInBasement(helper, s);
			List<BlockPos> wrong = BuildPlan.create(blueprint, placement).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the whole Smithy wrong after the dig, e.g. "
				+ wrong.stream().limit(4).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(villager.isAlive(), "the builder died");
			assertDrainedOrPlugged(helper, spots);
			LOGGER.info("qa gaps cellar logged: dug at tick {}, beside the cellar now {}", helper.getTick(),
				spots.stream().map(r -> helper.getBlockState(ORIGIN.offset(r)).toString()).toList());
		});
	}

	// ------------------------------------------------------------------------------- columns beside the basement wall

	/**
	 * A bubble column (soul sand under four water sources, the whole basement's depth) in the earth west of the
	 * basement's wall, and a kelp column as deep east of it: when done, the build is as drawn, no water in the basement
	 * and the builder alive.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaGapsColumns"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaGapsColumns")
	public void aBubbleColumnAndAKelpColumnBesideTheBasementLeaveItDry(GameTestHelper helper) {
		List<BlockPos> column = new ArrayList<>();
		Setup s = cottageFour(helper, b -> {
			Extent ex = Extent.of(b);
			Set<BlockPos> drawn = new HashSet<>();
			b.blocks().forEach(e -> drawn.add(e.pos()));
			int west = ex.minX() - 1;
			int east = ex.maxX() + 1;
			int z = ex.midZ();
			helper.setBlock(ORIGIN.offset(west, ex.minY() - 1, z), Blocks.SOUL_SAND);
			for (int y = ex.minY(); y <= -1; y++) {
				BlockPos w = new BlockPos(west, y, z);
				BlockPos e = new BlockPos(east, y, z);
				helper.assertFalse(drawn.contains(w) || drawn.contains(e), "setup: the column spots are drawn");
				helper.setBlock(ORIGIN.offset(w), Blocks.WATER);
				helper.setBlock(ORIGIN.offset(e), y == -1 ? Blocks.KELP.defaultBlockState() : Blocks.KELP_PLANT.defaultBlockState());
				column.add(w);
				column.add(e);
			}
		});
		for (BlockPos rel : column) {
			BlockState there = helper.getBlockState(ORIGIN.offset(rel));
			helper.assertTrue(there.getFluidState().is(FluidTags.WATER), "setup: no water in the column at " + rel.toShortString() + ": " + there);
		}
		boolean[] bubbles = {false};
		helper.onEachTick(() -> {
			for (BlockPos rel : column) {
				bubbles[0] |= helper.getBlockState(ORIGIN.offset(rel)).is(Blocks.BUBBLE_COLUMN);
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			String state = QaBasementFluids1104fBugTests.basement(s);
			helper.assertTrue(state.startsWith("0 wrong, 0 wet"), "finished, but: " + state);
			assertNoFluidInBasement(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			LOGGER.info("qa gaps columns: built at tick {}, a bubble column formed {}, the columns now {}", helper.getTick(), bubbles[0],
				column.stream().map(r -> helper.getBlockState(ORIGIN.offset(r)).getBlock().getName().getString()).toList());
		});
	}

	// ------------------------------------------------------------------------------------ water in the basement's walls

	/**
	 * Water sources in the earth where the basement's own west and east walls and its floor go (spots the drawing
	 * fills with blocks): when done, every wall and floor block is there as drawn, no water and no stray dirt in the
	 * basement, the builder alive.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaGapsWallWater"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaGapsWallWater")
	public void waterWhereTheBasementsWallsAndFloorGoIsBuiltAsDrawn(GameTestHelper helper) {
		List<BlockPos> spots = new ArrayList<>();
		Setup s = cottageFour(helper, b -> {
			Extent ex = Extent.of(b);
			Map<BlockPos, BlockState> drawn = new HashMap<>();
			b.blocks().forEach(e -> drawn.put(e.pos(), e.state()));
			for (BlockPos rel : List.of(new BlockPos(ex.minX(), -2, ex.midZ()), new BlockPos(ex.maxX(), -3, ex.midZ()), new BlockPos(ex.midX() + 1, ex.minY(), ex.midZ()))) {
				BlockState want = drawn.get(rel);
				helper.assertTrue(want != null && !want.isAir(), "setup: " + rel.toShortString() + " isn't a block of the basement: " + want);
				helper.setBlock(ORIGIN.offset(rel), Blocks.WATER);
				spots.add(rel);
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			String state = QaBasementFluids1104fBugTests.basement(s);
			helper.assertTrue(state.startsWith("0 wrong, 0 wet"), "finished, but: " + state);
			assertNoFluidInBasement(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			LOGGER.info("qa gaps wall water: built at tick {}, the spots now {}", helper.getTick(),
				spots.stream().map(r -> helper.getBlockState(ORIGIN.offset(r)).getBlock().getName().getString()).toList());
		});
	}

	// --------------------------------------------------------------------------- waterlogged earth and a restart

	/**
	 * A waterlogged stair (west), seagrass (east), kelp (south) and a waterlogged stair under the floor beside the
	 * Cottage IV's basement, and the build sites saved and loaded from NBT mid-CLEAR, once the room's middle at y -2 is
	 * dug: the build still finishes as drawn with no water in the basement, the stairs drained and kept, the seagrass
	 * and kelp plugged.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaGapsLoggedRestart"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaGapsLoggedRestart")
	public void waterloggedEarthBesideTheBasementWithARestartMidDigLeavesItDry(GameTestHelper helper) {
		List<BlockPos> spots = new ArrayList<>();
		BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.WATERLOGGED, true);
		Setup s = cottageFour(helper, b -> spots.addAll(logged(helper, b, List.of(stair, Blocks.SEAGRASS.defaultBlockState(), Blocks.KELP.defaultBlockState(), stair))));
		ServerLevel level = s.level();
		int[] restartedAt = {-1};
		BlockPos dugProbe = s.site().placement().origin().offset(QaBasementFluids1104fGameTests.ROOM_MIDDLE);
		helper.onEachTick(() -> {
			if (restartedAt[0] < 0) {
				BuildSite live = BuildSiteManager.get(level).get(s.site().id());
				if (live != null && live.stage() == BuildPlan.Stage.CLEAR && level.getBlockState(dugProbe).isAir()) {
					QaBasementFluids1104fGameTests.restart(helper);
					restartedAt[0] = (int) helper.getTick();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restartedAt[0] >= 0, "the server never restarted mid-CLEAR (the room's middle at y -2 never dug)");
			assertBuilt(helper, s);
			String state = QaBasementFluids1104fBugTests.basement(s);
			helper.assertTrue(state.startsWith("0 wrong, 0 wet"), "finished, but: " + state);
			assertNoFluidInBasement(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			assertDrainedOrPlugged(helper, spots);
			LOGGER.info("qa gaps logged restart: restarted at tick {}, built at tick {}, beside the basement now {}", restartedAt[0], helper.getTick(),
				spots.stream().map(r -> helper.getBlockState(ORIGIN.offset(r)).toString()).toList());
		});
	}

	// -------------------------------------------------------------------------------- struck builders (B952, B964)

	/**
	 * The builder's Blueprint Table exactly 4 blocks east of the bolt (the nearest the wiki allows): his bag and blueprint
	 * go on top of it, and the owner's message names that spot.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaGapsBenchFour"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaGapsBenchFour")
	public void aBenchFourBlocksFromTheBoltGetsTheBagOnTopAndTheMessageNamesIt(GameTestHelper helper) {
		BlockPos bench = new BlockPos(14, 2, 10);
		struck(helper, bench, new BlockPos(10, 2, 10), h -> {
		}, h -> {
		}, (told, lying) -> helper.assertTrue(told.equals(helper.absolutePos(bench).above()),
			"the bench is 4 blocks from the bolt, but the message names " + helper.relativePos(told).toShortString() + ", not the bench's top "
				+ bench.above().toShortString()));
	}

	/**
	 * The Table only 3 blocks east of the bolt (in its reach): nothing goes on top of it; it all lies together on open
	 * ground at least 4 blocks from the bolt, where the message says.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaGapsBenchThree"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaGapsBenchThree")
	public void aBenchThreeBlocksFromTheBoltGetsNothingOnTopAndTheMessageNamesTheGround(GameTestHelper helper) {
		BlockPos bench = new BlockPos(13, 2, 10);
		struck(helper, bench, new BlockPos(10, 2, 10), h -> {
		}, h -> {
		}, (told, lying) -> helper.assertFalse(told.equals(helper.absolutePos(bench).above()),
			"the bench is in the bolt's reach (3 blocks), but the message names its top"));
	}

	/**
	 * A builder atop a 3 by 3 tower five high, his Table at its foot 3 blocks from him (B964): everything lies at the
	 * tower's foot out of the bolt's reach, and the message names exactly where it lies.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaGapsTowerTold"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaGapsTowerTold")
	public void aBuilderAtopATowerIsToldTheSpotAtItsFootWhereHisBagLies(GameTestHelper helper) {
		struck(helper, new BlockPos(15, 2, 12), new BlockPos(15, 7, 15), h -> {
			for (int x = 14; x <= 16; x++) {
				for (int z = 14; z <= 16; z++) {
					for (int y = 2; y <= 6; y++) {
						h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
					}
				}
			}
		}, h -> {
		}, (told, lying) -> helper.assertTrue(helper.relativePos(told).getY() <= 3,
			"the message names " + helper.relativePos(told).toShortString() + ", not the ground at the tower's foot"));
	}

	/**
	 * A builder standing in a shallow pond (one deep, 9 by 9, sunk in the ground), his Table in the pond 2 blocks from
	 * him: nothing lies in the water; it all lies on the dry bank beyond, out of the bolt's reach, where the message says.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaGapsShallow"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaGapsShallow")
	public void aBuilderInAShallowPondGetsHisBagOnDryGroundTheMessageNames(GameTestHelper helper) {
		struck(helper, new BlockPos(12, 2, 10), new BlockPos(10, 2, 10), h -> {
			// solid earth under the test area's floor too (put back after): its bottom layer is a gap a spot could be found in
			Map<BlockPos, BlockState> was = new HashMap<>();
			for (int x = 0; x <= 22; x++) {
				for (int z = 0; z <= 22; z++) {
					for (int y = -2; y <= 0; y++) {
						BlockPos abs = h.absolutePos(new BlockPos(x, y, z));
						was.put(abs, h.getLevel().getBlockState(abs));
						h.getLevel().setBlock(abs, Blocks.STONE.defaultBlockState(), 2);
					}
				}
			}
			Leftovers.after(h, () -> was.forEach((abs, state) -> h.getLevel().setBlock(abs, state, 2)));
			// a pond one deep sunk in the ground (its banks at y 2, the dry ground on top of them at y 3), so it can't spread
			for (int x = 0; x <= 22; x++) {
				for (int z = 0; z <= 22; z++) {
					h.setBlock(new BlockPos(x, 2, z), x >= 6 && x <= 14 && z >= 6 && z <= 14 ? Blocks.WATER : Blocks.STONE);
				}
			}
		}, h -> {
		}, (told, lying) -> helper.assertTrue(helper.getLevel().getFluidState(told).isEmpty() && helper.getLevel().getFluidState(told.below()).isEmpty()
				&& helper.relativePos(told).getY() == 3,
			"the message names " + helper.relativePos(told).toShortString() + ", not the dry ground (y 3) beside the pond"));
	}

	/**
	 * On Peaceful a real bolt on a builder (the storm's rain putting his fire out) makes no witch and drops nothing: he
	 * is still a builder with his bag, his site and no item on the ground.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaGapsPeaceful"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaGapsPeaceful")
	public void onPeacefulARealBoltOnABuilderMakesNoWitchAndDropsNothing(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.difficulty(helper, Difficulty.PEACEFUL);
		noFireSpread(helper);
		BlockPos bench = new BlockPos(14, 2, 10);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 10));
		v.setNoAi(true);
		Builders.employ(level, v, helper.absolutePos(bench));
		BuildSite site = hut(helper, v, null);
		ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.OAK_PLANKS, PLANKS));
		ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.COBBLESTONE, COBBLE));
		helper.runAfterDelay(2, () -> {
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
			bolt.moveTo(Vec3.atBottomCenterOf(v.blockPosition()));
			level.addFreshEntity(bolt);
		});
		helper.onEachTick(() -> {
			v.clearFire();
			QaBuilderEdges1104bGameTests.calmWitches(helper);
		});
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(level.getEntitiesOfClass(LightningBolt.class, helper.getBounds().inflate(16)).isEmpty(), "the bolt still flashes");
			helper.assertTrue(level.getEntitiesOfClass(Witch.class, helper.getBounds().inflate(16)).isEmpty(), "a witch on Peaceful");
			helper.assertTrue(v.isAlive(), "the builder is gone on Peaceful");
			List<String> lying = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16)).stream().map(e -> e.getItem().toString()).toList();
			helper.assertTrue(lying.isEmpty(), "on Peaceful a bolt made the builder drop " + lying);
			int planks = ModAttachments.BUILDER_BAG.getOrCreate(v).count(Items.OAK_PLANKS);
			int cobble = ModAttachments.BUILDER_BAG.getOrCreate(v).count(Items.COBBLESTONE);
			helper.assertTrue(planks == PLANKS && cobble == COBBLE, "his bag after a bolt on Peaceful: " + planks + " planks, " + cobble + " cobblestone");
			BuildSite still = Builders.activeSite(level, v);
			helper.assertTrue(still != null && still.id().equals(site.id()), "on Peaceful the bolt took the builder's site");
			helper.succeed();
		});
	}

	// --------------------------------------------------------------------------------------- the top of the world

	/**
	 * B937 from the other side: a hut whose roof is exactly the world's top layer (y 319), on a mountain top: it is built
	 * whole, nothing skipped, and exactly its list is used.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaGapsTopFits"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaGapsTopFits")
	public void aHutWhoseRoofIsTheWorldsTopLayerIsBuiltWholeWithNothingSkipped(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.protectionOff(helper);
		int max = level.getMaxBuildHeight();
		int g = QaBuilderEdges1104bGameTests.top(helper, 4);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, g, new BlockPos(6, g, 6));
		helper.assertTrue(r.plan().bounds().maxY() == max - 1, "setup: the hut's roof isn't the world's top layer: " + r.plan().bounds() + ", limit " + max);
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		BuildSite[] last = {r.site()};
		helper.onEachTick(() -> {
			if (r.site() != null) {
				last[0] = r.site();
			}
		});
		helper.succeedWhen(() -> {
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			helper.assertTrue(last[0].skipped() == 0, last[0].skipped() + " block(s) skipped by a hut whose roof is the world's top layer");
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
			LOGGER.info("qa gaps top fits: built at tick {}, bounds {}", helper.getTick(), r.plan().bounds());
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	/** {@code states} at the blueprint's {@link Extent#wetSpots} (west, east, south, under the floor), each holding water. */
	static List<BlockPos> logged(GameTestHelper helper, Blueprint blueprint, List<BlockState> states) {
		Set<BlockPos> drawn = new HashSet<>();
		blueprint.blocks().forEach(e -> drawn.add(e.pos()));
		List<BlockPos> rels = Extent.of(blueprint).wetSpots();
		for (int i = 0; i < rels.size(); i++) {
			BlockPos rel = rels.get(i);
			helper.assertFalse(drawn.contains(rel), "setup: " + rel.toShortString() + " is a spot of the build");
			helper.setBlock(ORIGIN.offset(rel), states.get(i));
		}
		for (BlockPos rel : rels) {
			BlockState there = helper.getBlockState(ORIGIN.offset(rel));
			helper.assertTrue(there.getFluidState().is(FluidTags.WATER) && there.getFluidState().isSource(), "setup: " + rel.toShortString() + " holds no water: " + there);
		}
		return rels;
	}

	/**
	 * Per the wiki: a waterlogged stair is drained and kept, seagrass and kelp plugged; a spot the dig never opened beside
	 * (sealed by full blocks all round) may stay as it was. No spot holds water next to an opening.
	 */
	static void assertDrainedOrPlugged(GameTestHelper helper, List<BlockPos> spots) {
		List<String> bad = new ArrayList<>();
		for (BlockPos rel : spots) {
			BlockState there = helper.getBlockState(ORIGIN.offset(rel));
			if (there.getFluidState().isEmpty()) {
				continue;
			}
			// still wet: fine only when sealed in the earth (every neighbour a full block), so nothing can run out
			List<String> open = new ArrayList<>();
			for (Direction d : Direction.values()) {
				BlockPos n = helper.absolutePos(ORIGIN.offset(rel)).relative(d);
				BlockState ns = helper.getLevel().getBlockState(n);
				if (!ns.isCollisionShapeFullBlock(helper.getLevel(), n)) {
					open.add(d + "=" + ns.getBlock().getName().getString());
				}
			}
			if (!open.isEmpty()) {
				bad.add(rel.toShortString() + " still holds water (" + there + ") beside " + open);
			}
		}
		helper.assertTrue(bad.isEmpty(), "beside the dig: " + bad);
		for (int i : new int[] {0, 3}) {
			BlockState there = helper.getBlockState(ORIGIN.offset(spots.get(i)));
			helper.assertTrue(there.is(Blocks.STONE_BRICK_STAIRS), "the waterlogged stair at " + spots.get(i).toShortString() + " wasn't kept: " + there);
		}
	}

	private static void noFireSpread(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean fire = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer()));
	}

	/** A test hut of his to build far off (east), started for {@code owner}; closed after the test. */
	private static BuildSite hut(GameTestHelper helper, Villager v, net.minecraft.world.entity.player.Player owner) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(22, 2, 22)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, v, owner, QaBuilderEdges1104bGameTests.TEST_HUT, placement);
		helper.assertTrue(site != null, "setup: the builder's hut didn't start");
		UUID id = site.id();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(id) != null) {
				BuildSiteManager.get(level).remove(id);
			}
		});
		return site;
	}

	/**
	 * A builder of the bench at {@code bench}, standing still at {@code stand} with {@value #PLANKS} oak planks and
	 * {@value #COBBLE} cobblestone in his bag, building for an owner who is online (a spectator far off); {@code terrain}
	 * shapes the ground first and {@code beforeBolt} runs a tick before a real bolt strikes him. Once the bolt is gone:
	 * he is a witch, the owner was told "dropped at X Y Z" once, and the whole bag and one blueprint lie on that spot (each
	 * item's block), out of the bolt's reach (4+ blocks on x or z), not in a fluid or a block, resting on a solid floor or
	 * on the bench; then {@code check(told, lying)}.
	 */
	static void struck(GameTestHelper helper, BlockPos bench, BlockPos stand, Consumer<GameTestHelper> terrain, Consumer<GameTestHelper> beforeBolt,
					   java.util.function.BiConsumer<BlockPos, List<ItemEntity>> check) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.difficulty(helper, Difficulty.NORMAL);
		noFireSpread(helper);
		terrain.accept(helper);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SPECTATOR, QaStageSixGameTests.name("qaGS"));
		c.player().moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(28, 6, 28))));
		Villager v = helper.spawn(EntityType.VILLAGER, stand);
		v.setNoAi(true);
		Builders.employ(level, v, helper.absolutePos(bench));
		hut(helper, v, c.player());
		ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.OAK_PLANKS, PLANKS));
		ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.COBBLESTONE, COBBLE));
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(stand));
		helper.runAfterDelay(1, () -> beforeBolt.accept(helper));
		helper.runAfterDelay(2, () -> {
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
			bolt.moveTo(at);
			level.addFreshEntity(bolt);
		});
		helper.onEachTick(() -> QaBuilderEdges1104bGameTests.calmWitches(helper));
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(!v.isAlive(), "setup: the bolt didn't turn the builder");
			helper.assertTrue(level.getEntitiesOfClass(LightningBolt.class, helper.getBounds().inflate(16)).isEmpty(), "the bolt still flashes");
			List<String> texts = QaBuilderEdges1104bGameTests.texts(c.seen()).stream().filter(t -> t.startsWith("Lightning turned your builder")).toList();
			helper.assertTrue(texts.size() == 1, "the owner wasn't told once where it lies: " + QaBuilderEdges1104bGameTests.texts(c.seen()));
			Matcher m = DROPPED_AT.matcher(texts.get(0));
			helper.assertTrue(m.find(), "the message names no spot: " + texts.get(0));
			BlockPos told = new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
			List<ItemEntity> lying = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16));
			Map<Item, Integer> ground = new HashMap<>();
			List<String> where = new ArrayList<>();
			List<String> elsewhere = new ArrayList<>();
			for (ItemEntity e : lying) {
				ground.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
				BlockPos p = e.blockPosition();
				where.add(e.getItem().getCount() + " " + e.getItem().getItem() + " at " + helper.relativePos(p).toShortString() + (e.onGround() ? "" : " (falling)"));
				if (!p.equals(told)) {
					elsewhere.add(e.getItem() + " at " + helper.relativePos(p).toShortString());
				}
			}
			LOGGER.info("qa gaps struck: bench {}, stood {}: told {} (under it {}) ({}), on the ground {}", bench.toShortString(), stand.toShortString(),
				helper.relativePos(told).toShortString(), level.getBlockState(told.below()), texts.get(0), where);
			helper.assertTrue(ground.getOrDefault(ModItems.BLUEPRINT, 0) == 1 && ground.getOrDefault(Items.OAK_PLANKS, 0) == PLANKS
				&& ground.getOrDefault(Items.COBBLESTONE, 0) == COBBLE, "after a real bolt (bag " + PLANKS + " planks, " + COBBLE
				+ " cobblestone, a blueprint) the ground holds " + ground + ": " + where);
			helper.assertTrue(elsewhere.isEmpty(), "the message says " + helper.relativePos(told).toShortString() + ", but " + elsewhere + " lie elsewhere");
			helper.assertTrue(Math.abs(told.getX() - Math.floor(at.x)) >= 4 || Math.abs(told.getZ() - Math.floor(at.z)) >= 4,
				"the spot named, " + helper.relativePos(told).toShortString() + ", is in the bolt's reach of " + stand.toShortString());
			helper.assertTrue(level.getFluidState(told).isEmpty() && level.getBlockState(told).getCollisionShape(level, told).isEmpty(),
				"the spot named is in a fluid or a block: " + level.getBlockState(told));
			helper.assertTrue(level.getBlockState(told.below()).isFaceSturdy(level, told.below(), Direction.UP),
				"nothing to rest on under the spot named " + helper.relativePos(told).toShortString() + ": " + level.getBlockState(told.below()));
			check.accept(told, lying);
			helper.succeed();
		});
	}
}
