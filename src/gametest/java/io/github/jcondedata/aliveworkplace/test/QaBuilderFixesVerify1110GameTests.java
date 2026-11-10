package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA (2026-11-10) verifying four landed builder fixes from their specs, not their code:
 * <ul>
 * <li>B1111/B1112: crafters serve a builder by a full Storehouse (the Mason his stone brick slabs, the Carpenter a chest,
 * ladder, a non-oak door, a bed and a banner) when the ingredients they take free room, and when nothing fits the builder's
 * line says so in the en_us.json sentence, never "Nobody here makes"; one free slot is enough, none is said.</li>
 * <li>B1113: workplaceFoundationDepth lowered (not only raised) and workplaceLevelGround switched on mid-build take
 * effect at once (game-rules.md), and a change made live still holds after a save and reload.</li>
 * <li>B1119: a soul fire on soul sand or soul soil (and every other spot a worker won't stand in) is no path spot; a
 * builder's route goes round a fire on its causeway; a Steward's road goes through the one gap in a burning band.</li>
 * </ul>
 * Not duplicating QaStalledCrafting1109b*, QaBuilderRules1109* or QaBuilderBugs1109b*.
 */
public class QaBuilderFixesVerify1110GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = QaStalledCrafting1109bGameTests.BIG;
	private static final String HUGE = QaBuilderConfig1102GameTests.HUGE;
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STORE_CHEST = QaStalledCrafting1109bGameTests.STORE_CHEST;

	// ------------------------------------------------------------------------------------------------ B1111 / B1112

	/**
	 * B1112 with a birch door: the Storehouse by the builder's table is full to the last slot, holding exactly 8 oak planks
	 * (one chest), 7 sticks (one batch of ladders) and 6 birch planks (one batch of doors). Each job's ingredients empty
	 * their own slot, so the Carpenter makes the chest, the ladder and the birch door and the builder lays all three.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaVerify1110Birch"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaVerify1110Birch")
	public void aFullStorehouseStillGetsAChestLadderAndBirchDoorFromExactIngredients(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		blocks.put(new BlockPos(0, 0, 1), Blocks.STONE_BRICKS.defaultBlockState());
		blocks.put(new BlockPos(0, 0, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		blocks.put(new BlockPos(2, 0, 0), Blocks.CHEST.defaultBlockState());
		blocks.put(new BlockPos(4, 0, 0), Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		blocks.put(new BlockPos(4, 1, 0), Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "v1110birch", blocks, false, true,
			List.of(new ItemStack(Items.OAK_PLANKS, 8), new ItemStack(Items.STICK, 7), new ItemStack(Items.BIRCH_PLANKS, 6),
				new ItemStack(Items.STONE_BRICKS, 1)), true, false, false);
		QaStalledCrafting1109bGameTests.madeOrSaid(helper, run, Map.of(Blocks.CHEST, 1, Blocks.LADDER, 1, Blocks.BIRCH_DOOR, 1), Items.CHEST,
			Integer.MAX_VALUE);
	}

	/**
	 * B1112's bed and banner from the owner's list: a full Storehouse holding exactly 3 blue wool and 3 oak planks (one
	 * bed) and 6 green wool and 1 stick (one banner). Beds and banners are the Shepherd's (shepherd.md, "Weaving for
	 * builders"; carpenter.md: a banner is another job's): the Shepherd at her loom makes both and the builder sets them up.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaVerify1110BedBanner"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaVerify1110BedBanner")
	public void aFullStorehouseStillGetsABlueBedAndAGreenBanner(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		blocks.put(new BlockPos(0, 0, 0), Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		blocks.put(new BlockPos(0, 0, 1), Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		blocks.put(new BlockPos(3, 0, 0), Blocks.GREEN_BANNER.defaultBlockState());
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "v1110bed", blocks, false, false,
			List.of(new ItemStack(Items.BLUE_WOOL, 3), new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(Items.GREEN_WOOL, 6), new ItemStack(Items.STICK, 1)),
			true, false, false);
		BlockPos loom = QaStalledCrafting1109bGameTests.CARPENTER_BENCH;
		helper.setBlock(loom, Blocks.LOOM);
		Villager shepherd = helper.spawn(EntityType.VILLAGER, loom.north());
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(loom), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		run.crafters().add(shepherd);
		QaStalledCrafting1109bGameTests.madeOrSaid(helper, run, Map.of(Blocks.BLUE_BED, 1, Blocks.GREEN_BANNER, 1), Items.BLUE_BED, Integer.MAX_VALUE);
	}

	/**
	 * B1111 with only stone: the full Storehouse holds exactly 2 stone (no bricks) in its last free-able slot; the Mason
	 * cuts the 4 stone brick slabs from it (the stone taken frees the slot) and the builder lays them.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaVerify1110StoneOnly"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaVerify1110StoneOnly")
	public void aFullStorehouseWithOnlyTwoStoneStillGetsTheSlabsCut(GameTestHelper helper) {
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "v1110stone", QaStalledCrafting1109bGameTests.slabRow(),
			true, false, List.of(new ItemStack(Items.STONE, 2)), true, false, false);
		QaStalledCrafting1109bGameTests.madeOrSaid(helper, run, Map.of(Blocks.STONE_BRICK_SLAB, 4), Items.STONE_BRICK_SLAB, Integer.MAX_VALUE);
	}

	/**
	 * B1111's boundary from both sides: the Storehouse is full (16 stone bricks among cobblestone, nothing a job takes
	 * frees a slot). With zero free slots nothing is made or dropped and the builder's line reads exactly the en_us.json
	 * sentence "No room to bring Stone Brick Slab: the chests he takes from are full". Then exactly one slot is emptied: the
	 * Mason cuts the slabs into it and the builder lays all four.
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaVerify1110OneSlot"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaVerify1110OneSlot")
	public void zeroFreeSlotsIsSaidAndOneFreeSlotGetsTheSlabsMade(GameTestHelper helper) {
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "v1110slot", QaStalledCrafting1109bGameTests.slabRow(),
			true, false, List.of(new ItemStack(Items.STONE_BRICKS, 16)), true, false, false);
		noRoomThenRoom(helper, run, Blocks.STONE_BRICK_SLAB, 4, Items.STONE_BRICK_SLAB, "No room to bring Stone Brick Slab: the chests he takes from are full");
	}

	/**
	 * B1112's "say so when nothing fits": a builder waits for a chest, the full Storehouse holds 32 oak planks and 16
	 * sticks (taking 8 planks frees no slot). The line says the real reason in the en_us.json sentence, never "Nobody here
	 * makes", nothing is made or dropped; once the player empties one slot the Carpenter makes the chest and it is built.
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaVerify1110ChestNoRoom"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaVerify1110ChestNoRoom")
	public void aChestWithNoRoomAnywhereIsSaidPlainlyThenMadeOnceASlotIsFree(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		blocks.put(new BlockPos(0, 0, 0), Blocks.CHEST.defaultBlockState());
		QaStalledCrafting1109bGameTests.Run run = QaStalledCrafting1109bGameTests.setUp(helper, "v1110chest", blocks, false, true,
			List.of(new ItemStack(Items.OAK_PLANKS, 32), new ItemStack(Items.STICK, 16)), true, false, false);
		noRoomThenRoom(helper, run, Blocks.CHEST, 1, Items.CHEST, "No room to bring Chest: the chests he takes from are full");
	}

	/**
	 * Until the line reads {@code expected} (and at least 400 ticks passed) nothing of {@code probe} is made or lies on
	 * the ground and the line never says "Nobody here makes"; then the store's last slot is emptied and {@code want} of
	 * {@code block} must stand on the site.
	 */
	private static void noRoomThenRoom(GameTestHelper helper, QaStalledCrafting1109bGameTests.Run run, Block block, int want, Item probe, String expected) {
		String lang = Component.translatable("message.aliveworkplace.job_chain.store_full", probe.getDescription()).getString();
		if (!lang.equals(expected)) {
			throw new GameTestAssertException("en_us.json's store_full sentence renders as '" + lang + "', the spec's is '" + expected + "'");
		}
		AtomicReference<String> seen = new AtomicReference<>();
		long[] freedAt = {-1};
		helper.onEachTick(() -> {
			if (freedAt[0] >= 0) {
				return;
			}
			int made = QaStalledCrafting1109bGameTests.made(helper, probe);
			int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(probe)).size();
			if (made > 0 || dropped > 0 || QaStalledCrafting1109bGameTests.placed(helper, block) > 0) {
				throw new GameTestAssertException("with no free slot anywhere " + made + " " + probe + " were made, " + dropped + " dropped (tick " + run.tick() + ")");
			}
			BuildSite live = BuildSiteManager.get(run.level()).get(run.site().id());
			if (live == null || run.tick() % 20 != 0) {
				return;
			}
			Component line = JobShortages.line(run.level(), live.id(), live.missing());
			String text = line == null ? null : line.getString();
			if (text != null && text.contains("Nobody here makes")) {
				throw new GameTestAssertException("the builder's line blames nobody making it: " + text);
			}
			if (expected.equals(text)) {
				seen.set(text);
			}
			if (seen.get() != null && run.tick() >= 400) {
				Container chest = (Container) helper.getBlockEntity(STORE_CHEST);
				chest.setItem(chest.getContainerSize() - 1, ItemStack.EMPTY);
				freedAt[0] = run.tick();
				QaBasementGround1104GameTests.LOGGER.info("qa verify 1110: said '{}' at tick {}, one slot freed", text, run.tick());
			} else if (run.tick() >= 1500) {
				throw new GameTestAssertException("by tick 1500 the builder's line never read '" + expected + "'; it reads: " + text + ", missing " + live.missing());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(freedAt[0] >= 0, "slot not freed yet");
			int placed = QaStalledCrafting1109bGameTests.placed(helper, block);
			BuildSite live = BuildSiteManager.get(run.level()).get(run.site().id());
			helper.assertTrue(placed >= want, "one free slot since tick " + freedAt[0] + ", yet " + placed + "/" + want + " " + block.getName().getString()
				+ " stand; made " + QaStalledCrafting1109bGameTests.made(helper, probe) + ", site " + (live == null ? "gone" : live.status() + " " + live.missing()));
		});
	}

	// ------------------------------------------------------------------------------------------------ B1113

	/**
	 * game-rules.md: rules "take effect at once". workplaceFoundationDepth lowered from 12 to 0 while the Builder clears
	 * the site: "0: no foundations", so the dip under the hut stays empty, the hut is still built as drawn, and the
	 * cobblestone a foundation would have used stays in the chest (31 given, 25 for the hut: 6 left).
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaVerify1110DepthOff"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaVerify1110DepthOff")
	public void foundationDepthLoweredToZeroWhileClearingLeavesTheDipEmpty(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		QaBuilderRules1109GameTests.setInt(level, ModGameRules.FOUNDATION_DEPTH, 12);
		QaBuilderRules1109GameTests.setInt(level, ModGameRules.LEVEL_GROUND, 0);
		QaBuilderRules1109GameTests.lawn(helper);
		List<BlockPos> dip = QaBuilderRules1109GameTests.dip(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderRules1109GameTests.BENCH, QaBuilderRules1109GameTests.CHEST,
			QaBuilderRules1109GameTests.HUT, new ItemStack(Items.COBBLESTONE, 31), new ItemStack(Items.OAK_PLANKS, 61), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH));
		boolean[] off = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (!off[0] && site != null && site.stage() == BuildPlan.Stage.CLEAR) {
				QaBuilderRules1109GameTests.setInt(level, ModGameRules.FOUNDATION_DEPTH, 0);
				off[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0], "setup: never saw CLEAR: " + QaBuilderConfig1102GameTests.where(helper, r));
			// the plan drawn at the start (depth 12) counted the dip as foundation; the rule took it out
			QaBuilderConfig1102GameTests.assertBuilt(helper, r, dip);
			long filled = QaBuilderRules1109GameTests.filled(helper, dip);
			helper.assertTrue(filled == 0, "workplaceFoundationDepth was lowered to 0 while clearing, yet " + filled + " of the dip's " + dip.size()
				+ " spots were filled: " + QaBuilderRules1109GameTests.show(helper, dip));
			int cobble = QaBuilderConfig1102GameTests.held(helper, r.villager(), QaBuilderRules1109GameTests.CHEST).get(Items.COBBLESTONE);
			helper.assertTrue(cobble == 6, "cobblestone left " + cobble + ", expected 6 (31 given, 25 in the hut, no foundation)");
			QaBuilderRules1109GameTests.noneOnTheGround(helper);
		});
	}

	/**
	 * workplaceFoundationDepth lowered from 12 to 0 halfway through FOUNDATION (after the first spots of the dip are
	 * filled): no more spot is filled after the change, the hut still ends as drawn and nothing is dropped.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaVerify1110DepthHalf"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaVerify1110DepthHalf")
	public void foundationDepthLoweredHalfwayThroughFoundationFillsNoMore(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		QaBuilderRules1109GameTests.setInt(level, ModGameRules.FOUNDATION_DEPTH, 12);
		QaBuilderRules1109GameTests.setInt(level, ModGameRules.LEVEL_GROUND, 0);
		QaBuilderRules1109GameTests.lawn(helper);
		List<BlockPos> dip = QaBuilderRules1109GameTests.dip(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderRules1109GameTests.BENCH, QaBuilderRules1109GameTests.CHEST,
			QaBuilderRules1109GameTests.HUT, new ItemStack(Items.COBBLESTONE, 31), new ItemStack(Items.OAK_PLANKS, 61), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH));
		long[] atChange = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (atChange[0] < 0 && site != null && site.stage() == BuildPlan.Stage.FOUNDATION) {
				long n = QaBuilderRules1109GameTests.filled(helper, dip);
				if (n >= 1 && n < dip.size()) {
					QaBuilderRules1109GameTests.setInt(level, ModGameRules.FOUNDATION_DEPTH, 0);
					atChange[0] = n;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(atChange[0] >= 0, "setup: never saw FOUNDATION part way through the dip: " + QaBuilderConfig1102GameTests.where(helper, r)
				+ "; dip " + QaBuilderRules1109GameTests.show(helper, dip));
			QaBuilderConfig1102GameTests.assertBuilt(helper, r, dip);
			long now = QaBuilderRules1109GameTests.filled(helper, dip);
			helper.assertTrue(now == atChange[0], "workplaceFoundationDepth set to 0 with " + atChange[0] + " of " + dip.size()
				+ " dip spots filled, yet " + now + " are filled at the end: " + QaBuilderRules1109GameTests.show(helper, dip));
			QaBuilderRules1109GameTests.noneOnTheGround(helper);
		});
	}

	/**
	 * workplaceLevelGround switched on (0 to 2) in the middle of STRUCTURE, the build having started with it off: the
	 * finished hut is leveled 2 out, so every pit in the ring is filled from the chest's dirt.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaVerify1110GroundOn"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaVerify1110GroundOn")
	public void levelGroundSwitchedOnMidStructureLevelsEveryPit(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		QaBuilderRules1109GameTests.setInt(level, ModGameRules.LEVEL_GROUND, 0);
		QaBuilderRules1109GameTests.lawn(helper);
		List<BlockPos> pits = QaBuilderRules1109GameTests.pits(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderRules1109GameTests.BENCH, QaBuilderRules1109GameTests.CHEST,
			QaBuilderRules1109GameTests.HUT, QaBuilderRules1109GameTests.hutAndDirt(16));
		boolean[] on = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (!on[0] && site != null && site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() >= 5) {
				QaBuilderRules1109GameTests.setInt(level, ModGameRules.LEVEL_GROUND, 2);
				on[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(on[0], "setup: never reached STRUCTURE cursor 5: " + QaBuilderConfig1102GameTests.where(helper, r));
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
			long filled = QaBuilderRules1109GameTests.filled(helper, pits);
			helper.assertTrue(filled == pits.size(), "workplaceLevelGround switched on to 2 mid-STRUCTURE, yet only " + filled + " of the " + pits.size()
				+ " pits round the hut were filled: " + QaBuilderRules1109GameTests.show(helper, pits));
			QaBuilderRules1109GameTests.noneOnTheGround(helper);
		});
	}

	/**
	 * Consistent across restarts: workplaceLevelGround set to 0 live mid-STRUCTURE, then the server saved and loaded the
	 * site and the Builder (a restart) a little later, still in the build: the pits stay pits, the hut ends as drawn.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaVerify1110GroundOffReload"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaVerify1110GroundOffReload")
	public void levelGroundSetToZeroLiveThenARestartStillLeavesThePits(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		QaBuilderRules1109GameTests.setInt(level, ModGameRules.LEVEL_GROUND, 2);
		QaBuilderRules1109GameTests.lawn(helper);
		List<BlockPos> pits = QaBuilderRules1109GameTests.pits(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, QaBuilderRules1109GameTests.BENCH, QaBuilderRules1109GameTests.CHEST,
			QaBuilderRules1109GameTests.HUT, QaBuilderRules1109GameTests.hutAndDirt(16));
		long[] offAt = {-1};
		boolean[] restarted = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (site == null) {
				return;
			}
			if (offAt[0] < 0 && site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() >= 5) {
				QaBuilderRules1109GameTests.setInt(level, ModGameRules.LEVEL_GROUND, 0);
				offAt[0] = level.getGameTime();
			} else if (offAt[0] >= 0 && !restarted[0] && level.getGameTime() - offAt[0] >= 40) {
				QaBuilderConfig1102GameTests.restart(helper, r);
				restarted[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], "setup: never restarted mid-build: " + QaBuilderConfig1102GameTests.where(helper, r));
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
			long filled = QaBuilderRules1109GameTests.filled(helper, pits);
			helper.assertTrue(filled == 0, "workplaceLevelGround 0 (set live, then a restart), yet " + filled + " of " + pits.size()
				+ " pits filled: " + QaBuilderRules1109GameTests.show(helper, pits));
			QaBuilderRules1109GameTests.noneOnTheGround(helper);
		});
	}

	// ------------------------------------------------------------------------------------------------ B1119

	/**
	 * B1119's "(or soul fire)" and the fix's "anything a worker won't stand in": a soul fire on soul sand and on soul soil,
	 * a cobweb, a sweet berry bush and a wither rose on grass, and powder snow each make their spot neither a worker's
	 * standing spot nor a path spot; the same spots, emptied, are both.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aSoulFireOrAnyTrapIsNoPathSpotButTheEmptySpotIs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Block[][] cases = {
			{Blocks.SOUL_SAND, Blocks.SOUL_FIRE},
			{Blocks.SOUL_SOIL, Blocks.SOUL_FIRE},
			{Blocks.GRASS_BLOCK, Blocks.COBWEB},
			{Blocks.GRASS_BLOCK, Blocks.SWEET_BERRY_BUSH},
			{Blocks.GRASS_BLOCK, Blocks.WITHER_ROSE},
			{Blocks.STONE, Blocks.POWDER_SNOW},
		};
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < cases.length; i++) {
			BlockPos ground = new BlockPos(1, 2, 1 + i);
			helper.setBlock(ground, cases[i][0]);
			helper.setBlock(ground.above(), cases[i][1]);
			BlockPos feet = helper.absolutePos(ground.above());
			if (!level.getBlockState(feet).is(cases[i][1])) {
				throw new GameTestAssertException("(setup) " + cases[i][1] + " didn't stay on " + cases[i][0] + ": " + level.getBlockState(feet));
			}
			if (Walker.canStand(level, feet)) {
				wrong.add("a worker stands in " + cases[i][1].getName().getString());
			}
			if (feet.equals(Paths.walkable(level, feet))) {
				wrong.add("a path leads into " + cases[i][1].getName().getString() + " on " + cases[i][0].getName().getString());
			}
			helper.setBlock(ground.above(), Blocks.AIR);
			if (!feet.equals(Paths.walkable(level, feet))) {
				wrong.add("(control) the empty spot on " + cases[i][0].getName().getString() + " isn't a path spot: " + Paths.walkable(level, feet));
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * B1119 through the builder's own route (Paths.route): a raised soul soil causeway with a soul fire burning in the
	 * middle. With the fire the only way the route finds no way; with a detour beside it the route goes round, never
	 * through the fire; with the fire put out it walks straight over that spot.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aBuildersRouteGoesRoundASoulFireOnItsCauseway(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 1; x <= 9; x++) {
			helper.setBlock(new BlockPos(x, 4, 3), Blocks.SOUL_SOIL);
		}
		BlockPos fire = new BlockPos(5, 5, 3);
		helper.setBlock(fire, Blocks.SOUL_FIRE);
		BlockPos fireAbs = helper.absolutePos(fire);
		helper.assertTrue(level.getBlockState(fireAbs).is(Blocks.SOUL_FIRE), "(setup) no soul fire");
		BlockPos start = helper.absolutePos(new BlockPos(1, 5, 3));
		BlockPos goal = helper.absolutePos(new BlockPos(9, 5, 3));
		List<BlockPos> only = Paths.route(level, start, goal, null);
		helper.assertTrue(only.isEmpty(), "with the soul fire the only way, the route goes through it: " + only);
		for (int x = 4; x <= 6; x++) {
			helper.setBlock(new BlockPos(x, 4, 4), Blocks.SOUL_SOIL);
		}
		List<BlockPos> round = Paths.route(level, start, goal, null);
		helper.assertFalse(round.isEmpty(), "no route round the soul fire, though a detour is there");
		helper.assertFalse(round.contains(fireAbs), "the route leads into the soul fire: " + round);
		helper.setBlock(fire, Blocks.AIR);
		helper.setBlock(new BlockPos(5, 4, 4), Blocks.AIR);
		List<BlockPos> straight = Paths.route(level, start, goal, null);
		helper.assertTrue(straight.contains(fireAbs), "(control) with the fire out the route doesn't cross its spot: " + straight);
		helper.succeed();
	}

	/**
	 * B1119 for the Steward's roads (Paths.Search with Roads.clear, as Roads routes them): a band of fire burning on the
	 * grass right across the way, with a single gap. The road never leads into a fire; it goes through the gap. Control:
	 * with no fire the road crosses the band straight.
	 */
	//$ gametest_batch HUGE '"qaVerify1110Road"'
	@GameTest(template = HUGE, batch = "qaVerify1110Road")
	public void aStewardsRoadGoesThroughTheGapInABandOfFire(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
			}
		}
		List<BlockPos> plain = road(helper, level);
		BlockPos straightSpot = helper.absolutePos(new BlockPos(15, 2, 12));
		helper.assertTrue(plain.contains(straightSpot), "(control) with no fire the road doesn't cross straight at x 15: " + plain);
		for (int x = 0; x < 30; x++) {
			if (x != 22) {
				helper.setBlock(new BlockPos(x, 2, 12), Blocks.FIRE);
			}
		}
		helper.assertTrue(level.getBlockState(straightSpot).is(Blocks.FIRE), "(setup) no fire on the grass: " + level.getBlockState(straightSpot));
		List<BlockPos> burning = road(helper, level);
		List<BlockPos> inFire = burning.stream().filter(p -> level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock).toList();
		for (int x = 0; x < 30; x++) {
			helper.setBlock(new BlockPos(x, 2, 12), Blocks.AIR);
		}
		helper.assertTrue(inFire.isEmpty(), "a Steward's road leads into the fire at " + inFire + ": " + burning);
		helper.assertTrue(burning.contains(helper.absolutePos(new BlockPos(22, 2, 12))), "the road doesn't go through the one gap in the fire: " + burning);
		helper.succeed();
	}

	private static List<BlockPos> road(GameTestHelper helper, ServerLevel level) {
		Paths.Search search = new Paths.Search(level, helper.absolutePos(new BlockPos(15, 2, 5)), helper.absolutePos(new BlockPos(15, 2, 22)),
			new Paths.Options(0, List.of(), 24, Roads::clear));
		search.step(20000);
		return search.result();
	}
}
