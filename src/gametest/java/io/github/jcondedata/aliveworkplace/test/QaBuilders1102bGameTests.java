package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (2026-11-02, builders and builds), from the spec, not the code (bug tests: QaBuilders1102bBugTests):
 * <ul>
 *   <li>docs/guide/builder.md: "Stock the chests within 8 blocks"; docs/guide/builder-s-bench.md: "Chests and barrels
 *   within 8 blocks are the worker's supply chests"; ROADMAP 23.1 "every build finishes; no item is duplicated or
 *   lost".</li>
 *   <li>B508 / wiki builder.md "Unfinished blocks are never claimed done": a block the builder placed that a player
 *   breaks mid-build is back before DONE.</li>
 *   <li>docs/guide/scan-tool.md: "Villagers, animals, dropped items and minecarts aren't scanned"; "turns it into a
 *   blueprint, so the village's builders can build it again"; docs/guide/builder.md "Water and lava are poured from
 *   buckets in the chests".</li>
 * </ul>
 */
public class QaBuilders1102bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	/** The disruption run's chest (its four barrels sit beside it at x = 1, 3, 4, 5). */
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final List<BlockPos> BARRELS = List.of(new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4));
	private static final int EXTRA = 2;
	private static final BlockPos SRC = new BlockPos(18, 2, 18);
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 5);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SUPPLY = new BlockPos(2, 2, 4);
	/** Ticks the builder may wait for a plain item before the wait counts as a refusal of the special one. */
	private static final int REFUSED_AFTER = 400;

	// --------------------------------------------------------------------------------------- the supply chest changes

	/**
	 * Mid-STRUCTURE the player swaps the supply chest for a barrel on the same spot and puts everything that was in the
	 * chest into the barrel. Barrels are supply chests too (builder-s-bench.md), so the hut finishes as drawn and every
	 * item is accounted for (23.1).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1102bBarrelSwap"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1102bBarrelSwap")
	public void theSupplyChestSwappedForABarrelMidStructureStillFinishesTheHut(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		boolean upkeep = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = upkeep);
		boolean[] swapped = new boolean[1];
		helper.onEachTick(() -> {
			if (swapped[0] || r.site().stage() != BuildPlan.Stage.STRUCTURE || QaBuilderDisruptionGameTests.placed(r).size() < 3) {
				return;
			}
			swapped[0] = true;
			Container chest = helper.getBlockEntity(CHEST);
			List<ItemStack> held = new ArrayList<>();
			for (int i = 0; i < chest.getContainerSize(); i++) {
				held.add(chest.getItem(i).copy());
			}
			chest.clearContent();
			helper.setBlock(CHEST, Blocks.AIR);
			helper.setBlock(CHEST, Blocks.BARREL);
			Container barrel = helper.getBlockEntity(CHEST);
			for (int i = 0; i < held.size(); i++) {
				barrel.setItem(i, held.get(i));
			}
			LOGGER.info("qa 1102b barrel swap: the chest became a barrel, {}", QaBuilderDisruptionGameTests.state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(swapped[0], "setup: the chest was never swapped (" + QaBuilderDisruptionGameTests.state(helper, r) + ")");
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of());
			QaBuilderDisruptionGameTests.assertNothingMadeOrLost(helper, r, Map.of());
			LOGGER.info("qa 1102b barrel swap: the hut finished from the barrel, nothing made or lost");
		});
	}

	/**
	 * Mid-STRUCTURE the player puts a second chest beside the supply chest, making it a double chest, and moves
	 * everything into the new half. The new half is within 8 blocks, so the builder finishes the hut from it, and
	 * nothing is made or lost.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1102bDoubleChest"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1102bDoubleChest")
	public void theStockMovedIntoTheNewHalfOfADoubleChestMidStructureFinishesTheHut(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		boolean upkeep = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = upkeep);
		BlockPos half = CHEST.south();
		boolean[] moved = new boolean[1];
		helper.onEachTick(() -> {
			if (moved[0] || r.site().stage() != BuildPlan.Stage.STRUCTURE || QaBuilderDisruptionGameTests.placed(r).size() < 3) {
				return;
			}
			moved[0] = true;
			BlockState west = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST);
			// Facing west, a RIGHT half joins the chest to its south and a LEFT half the one to its north
			r.level().setBlock(helper.absolutePos(CHEST), west.setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
			r.level().setBlock(helper.absolutePos(half), west.setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
			Container old = helper.getBlockEntity(CHEST);
			Container now = helper.getBlockEntity(half);
			int slot = 0;
			for (int i = 0; i < old.getContainerSize(); i++) {
				if (!old.getItem(i).isEmpty()) {
					now.setItem(slot++, old.getItem(i).copy());
				}
			}
			old.clearContent();
			helper.assertTrue(ChestBlock.getBlockType(r.level().getBlockState(helper.absolutePos(CHEST))) != net.minecraft.world.level.block.DoubleBlockCombiner.BlockType.SINGLE,
				"setup: the two chests aren't one double chest");
			LOGGER.info("qa 1102b double chest: {} stacks moved into the new half, {}", slot, QaBuilderDisruptionGameTests.state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(moved[0], "setup: the stock was never moved (" + QaBuilderDisruptionGameTests.state(helper, r) + ")");
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of());
			Container now = helper.getBlockEntity(half);
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int got = QaBuilderDisruptionGameTests.loose(helper, e.getKey()) + now.countItem(e.getKey());
				helper.assertTrue(got == EXTRA, e.getKey() + ": " + got + " left in the chests, bags and on the ground, wanted " + EXTRA);
			}
			LOGGER.info("qa 1102b double chest: the hut finished from the new half, nothing made or lost");
		});
	}

	/**
	 * Mid-STRUCTURE the player lays one of the hut's own planks himself, in a wall spot the builder hasn't reached.
	 * The hut finishes as drawn and the planks add up: the chest ends with its spare plus the one plank the builder
	 * didn't need (nothing made from the player's block, nothing lost).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1102bHelpingHand"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1102bHelpingHand")
	public void aPlankThePlayerLaysInTheWallHimselfIsKeptAndTheChestKeepsOneMore(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		boolean upkeep = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = upkeep);
		BlockPos[] laid = new BlockPos[1];
		helper.onEachTick(() -> {
			if (laid[0] != null || r.site().stage() != BuildPlan.Stage.STRUCTURE || QaBuilderDisruptionGameTests.placed(r).size() < 2) {
				return;
			}
			for (BuildPlan.Step s : QaBuilderDisruptionGameTests.open(r)) {
				if (s.state().is(Blocks.OAK_PLANKS)) {
					laid[0] = s.pos().immutable();
					r.level().setBlock(laid[0], s.state(), 3);
					LOGGER.info("qa 1102b helping hand: the player laid a plank at {}, {}", helper.relativePos(laid[0]), QaBuilderDisruptionGameTests.state(helper, r));
					return;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(laid[0] != null, "setup: no open plank spot was ever found (" + QaBuilderDisruptionGameTests.state(helper, r) + ")");
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of());
			QaBuilderDisruptionGameTests.assertNothingMadeOrLost(helper, r, Map.of(Items.OAK_PLANKS, 1));
			LOGGER.info("qa 1102b helping hand: the player's plank was kept and the chest has one plank more");
		});
	}

	// ------------------------------------------------------------------------------------- a door broken mid-DECORATION

	/**
	 * B508 ("Unfinished blocks are never claimed done"): the door is a decoration of two blocks. A player breaks it right
	 * after the builder hangs it, while the site is still decorating; when the site ends, the door stands whole again
	 * (both halves), not a hole in the front wall called done. Repairs are off, so only the build itself can put it back.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1102bDoorBroken"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1102bDoorBroken")
	public void aDoorBrokenMidDecorationStandsWholeWhenTheBuildEnds(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		boolean upkeep = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = upkeep);
		BlockPos door = null;
		BuildPlan.Stage doorStage = null;
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			for (BuildPlan.Step s : r.plan().steps(stage)) {
				if (s.state().getBlock() instanceof DoorBlock && s.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
					door = s.pos().immutable();
					doorStage = stage;
				}
			}
		}
		if (door == null) {
			throw new GameTestAssertException("setup: the test hut has no door");
		}
		BlockPos at = door;
		boolean[] broken = new boolean[1];
		helper.onEachTick(() -> {
			if (broken[0] || r.site().stage() != BuildPlan.Stage.DECORATION || !(r.level().getBlockState(at).getBlock() instanceof DoorBlock)) {
				return;
			}
			broken[0] = true;
			r.level().destroyBlock(at, false);
			LOGGER.info("qa 1102b door: the door at {} broken mid-decoration, {}", helper.relativePos(at), QaBuilderDisruptionGameTests.state(helper, r));
		});
		BuildPlan.Stage drawnIn = doorStage;
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "setup: the door (a " + drawnIn + " step) never stood while the site was decorating ("
				+ QaBuilderDisruptionGameTests.state(helper, r) + ")");
			helper.assertFalse(QaBuilderDisruptionGameTests.building(r), "still building: " + QaBuilderDisruptionGameTests.state(helper, r));
			BlockState lower = r.level().getBlockState(at);
			BlockState upper = r.level().getBlockState(at.above());
			helper.assertTrue(lower.getBlock() instanceof DoorBlock && upper.getBlock() instanceof DoorBlock,
				"the build ended (" + QaBuilderDisruptionGameTests.state(helper, r) + ") with the door the player broke mid-decoration still gone: "
					+ lower + " / " + upper);
			LOGGER.info("qa 1102b door: the door is whole again at the end");
		});
	}

	// ------------------------------------------------------------------------------------------------- the Scan Tool

	/**
	 * scan-tool.md: "Villagers, animals, dropped items and minecarts aren't scanned", while frames come along. A room
	 * with a cow, a minecart and a dropped diamond in it, plus an item frame, is scanned and built again elsewhere: the
	 * new room has its frame, and no cow, minecart or diamond appear there.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102bScanEntities"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102bScanEntities")
	public void aScannedRoomsCowMinecartAndDroppedItemAreNotBuiltAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> room = room();
		room.forEach((p, s) -> helper.setBlock(SRC.offset(p), s));
		BlockPos frameAt = new BlockPos(3, 2, 3);
		net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(level, helper.absolutePos(SRC.offset(frameAt)), Direction.NORTH);
		helper.assertTrue(frame.survives(), "setup: the frame doesn't hang on the back wall");
		level.addFreshEntity(frame);
		var cow = helper.spawn(EntityType.COW, SRC.offset(1, 1, 1));
		cow.setNoAi(true);
		var cart = helper.spawn(EntityType.MINECART, SRC.offset(4, 1, 1));
		ItemEntity diamond = new ItemEntity(level, helper.absolutePos(SRC.offset(2, 1, 2)).getX() + 0.5, helper.absolutePos(SRC).getY() + 1.1,
			helper.absolutePos(SRC.offset(2, 1, 2)).getZ() + 0.5, new ItemStack(Items.DIAMOND, 3));
		diamond.setPickUpDelay(32767);
		diamond.setUnlimitedLifetime();
		diamond.setDeltaMovement(0, 0, 0);
		level.addFreshEntity(diamond);

		ResourceLocation id = scan(helper, SRC, SRC.offset(5, 3, 4));
		Built b = buildBack(helper, id, Rotation.NONE, Mirror.NONE);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			AABB box = new AABB(b.origin()).expandTowards(6, 4, 5).inflate(0.5);
			BlockPos frameThere = b.origin().offset(frameAt);
			boolean hangs = !level.getEntities(EntityType.ITEM_FRAME, new AABB(frameThere).inflate(0.5), Entity::isAlive).isEmpty();
			helper.assertTrue(hangs, "setup: the scanned room's frame wasn't built again, so the scan's entities weren't built at all");
			int cows = level.getEntities(EntityType.COW, box, Entity::isAlive).size();
			int carts = level.getEntities(EntityType.MINECART, box, Entity::isAlive).size();
			int diamonds = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.DIAMOND)).size();
			helper.assertTrue(cows == 0 && carts == 0 && diamonds == 0, "the room built from the scan has " + cows + " cow(s), " + carts
				+ " minecart(s) and " + diamonds + " dropped diamond(s) in it; the scan guide says they aren't scanned");
			helper.assertTrue(cow.isAlive() && cart.isAlive(), "the scanned room's own cow and minecart are gone");
			LOGGER.info("qa 1102b scan entities: frame built again, no cow, minecart or dropped item copied");
		});
	}

	/**
	 * A waterlogged stair and a waterlogged slab, each in a pit of the floor so the water stays put, are scanned and
	 * built again. The scan guide promises the build again, so the stair (facing as scanned) and the slab stand; the
	 * mod places blueprint blocks dry ("no free water", MaterialRules.forPlacement), and no stray water ends up in the
	 * new room. (Whether a waterlogged block should be built under water is a design question, not promised.)
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102bScanWater"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102bScanWater")
	public void aScannedRoomsWaterloggedStairAndSlabAreBuiltAndLeakNoWater(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> room = room();
		BlockPos stairAt = new BlockPos(2, 0, 2);
		BlockPos slabAt = new BlockPos(4, 0, 2);
		room.put(stairAt, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH).setValue(BlockStateProperties.WATERLOGGED, true));
		room.put(slabAt, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(BlockStateProperties.WATERLOGGED, true));
		room.forEach((p, s) -> helper.setBlock(SRC.offset(p), s));
		helper.assertTrue(level.getBlockState(helper.absolutePos(SRC.offset(stairAt))).getValue(BlockStateProperties.WATERLOGGED), "setup: the stair isn't waterlogged");

		ResourceLocation id = scan(helper, SRC, SRC.offset(5, 3, 4));
		Built b = buildBack(helper, id, Rotation.NONE, Mirror.NONE);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			BlockState stair = level.getBlockState(b.origin().offset(stairAt));
			BlockState slab = level.getBlockState(b.origin().offset(slabAt));
			helper.assertTrue(stair.is(Blocks.OAK_STAIRS) && slab.is(Blocks.STONE_BRICK_SLAB), "setup: the stair and slab weren't built: " + stair + " / " + slab
				+ " (" + b.site().skipped() + " skipped)");
			helper.assertTrue(stair.getValue(StairBlock.FACING) == Direction.SOUTH && slab.getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
				"the scanned stair and slab were built turned or flipped: " + stair + " / " + slab);
			int wet = 0;
			for (BlockPos p : BlockPos.betweenClosed(b.origin(), b.origin().offset(5, 3, 4))) {
				if (!level.getFluidState(p).isEmpty()) {
					wet++;
				}
			}
			helper.assertTrue(wet == 0, wet + " block(s) of the room built from the scan hold water");
			LOGGER.info("qa 1102b scan water: the stair and slab were built (waterlogged={}/{}), no stray water",
				stair.getValue(BlockStateProperties.WATERLOGGED), slab.getValue(BlockStateProperties.WATERLOGGED));
		});
	}

	/**
	 * The smallest scan: both corners on the same block. scan-tool.md promises nothing smaller than a block, so a
	 * 1 × 1 × 1 box with a block in it saves ("Saved ... (1 × 1 × 1)") and a builder builds that one block.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qa1102bScanOne"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qa1102bScanOne")
	public void aScanOfOneBlockSavesOneByOneByOneAndIsBuilt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.MOSSY_STONE_BRICKS);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, SRC);
		QaStageThreeGameTests.click(helper, player, SRC);
		String marked = QaStageThreeGameTests.last(seen);
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		Component said = seen.isEmpty() ? Component.empty() : seen.get(seen.size() - 1);
		helper.assertTrue(QaStageThreeGameTests.key(said).equals("message.aliveworkplace.scan.saved"), "a one-block scan said "
			+ QaStageThreeGameTests.last(seen) + " (" + said.getString() + "), after the second click: " + marked);
		helper.assertTrue(said.getString().contains("(1 × 1 × 1)"), "the one-block scan's line reads: " + said.getString());
		ResourceLocation id = found(player);
		Built b = buildBack(helper, id, Rotation.NONE, Mirror.NONE);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			helper.assertTrue(level.getBlockState(b.origin()).is(Blocks.MOSSY_STONE_BRICKS), "the one-block scan built " + level.getBlockState(b.origin()));
			LOGGER.info("qa 1102b scan one: {} -> built", said.getString());
		});
	}

	/**
	 * B434/B536 (builder.md "Builders never copy ... what a block holds in its look"; QaScanLooks "the material list asks
	 * for each piece, so breaking the new build gives back no more than was paid") on a piece that test didn't scan: five
	 * layers of snow ask five snow. And the other way round: a door (two blocks) and a bed (two blocks) are one item
	 * each, so the list doesn't ask the player for two. (The lichen and the candle cake of the same row are
	 * QaBuilders1102bBugTests.)
	 */
	//$ gametest_ticks_batch HUGE '400' '"qa1102bScanPieces"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa1102bScanPieces")
	public void aScansFiveSnowLayersAskForFiveAndADoorOrABedForOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(14, 2, 24);
		for (int dx = 0; dx < 8; dx++) {
			helper.setBlock(at.east(dx), Blocks.STONE);
		}
		helper.setBlock(at.above(), Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 5));
		helper.setBlock(at.east(1).above(), Blocks.CANDLE_CAKE);
		BlockPos lichen = at.east(3).above();
		helper.setBlock(lichen.north(), Blocks.STONE);
		helper.setBlock(lichen.east(), Blocks.STONE);
		helper.setBlock(lichen, Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.DOWN, true)
			.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.EAST, true));
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		helper.setBlock(at.east(5).above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(at.east(5).above(2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST);
		helper.setBlock(at.east(6).above(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
		helper.setBlock(at.east(7).above(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		helper.assertTrue(level.getBlockState(helper.absolutePos(lichen)).is(Blocks.GLOW_LICHEN) && level.getBlockState(helper.absolutePos(at.east(7).above())).is(Blocks.RED_BED),
			"setup: the lichen or bed didn't stay");

		ResourceLocation id = scan(helper, at.north(), at.east(7).above(2));
		Built b = buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		BuildPlan plan = b.site().plan(level);
		Map<Item, Integer> asked = plan.materials();
		List<String> wrong = new ArrayList<>();
		if (asked.getOrDefault(Items.SNOW, 0) < 5) {
			wrong.add(asked.getOrDefault(Items.SNOW, 0) + " snow for 5 layers");
		}
		if (asked.getOrDefault(Items.OAK_DOOR, 0) != 1) {
			wrong.add(asked.getOrDefault(Items.OAK_DOOR, 0) + " oak doors for one door");
		}
		if (asked.getOrDefault(Items.RED_BED, 0) != 1) {
			wrong.add(asked.getOrDefault(Items.RED_BED, 0) + " red beds for one bed");
		}
		helper.assertTrue(wrong.isEmpty(), "the scan's material list " + asked + " asks " + wrong);
		LOGGER.info("qa 1102b scan pieces: {}", asked);
		helper.succeed();
	}

	// ------------------------------------------------------------------- a player's special item as the only material

	/**
	 * A scanned plain decorated pot, and in the supply chest only the player's pot with four sherds. Built from that
	 * pot, the four sherds must still be there (on the placed pot or handed back), not turned into a plain pot.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102bSherdPot"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102bSherdPot")
	public void thePlayersSherdPotIsNeverTurnedIntoAPlainPotAndKeepsItsSherds(GameTestHelper helper) {
		Item sherd = Items.ANGLER_POTTERY_SHERD;
		ItemStack pot = new ItemStack(Items.DECORATED_POT);
		pot.set(DataComponents.POT_DECORATIONS, new PotDecorations(Optional.of(sherd), Optional.of(sherd), Optional.of(sherd), Optional.of(sherd)));
		oneBlockFromThePlayersItem(helper, Blocks.DECORATED_POT, pot, "sherds", QaBuilders1102bGameTests::sherds, be -> be instanceof DecoratedPotBlockEntity p
			? (int) p.getDecorations().ordered().stream().filter(i -> i.getDefaultInstance().is(ItemTags.DECORATED_POT_SHERDS)).count() : 0);
	}

	/**
	 * A scanned empty purple shulker box, and in the supply chest only the player's purple shulker box holding 64
	 * diamonds. Built from it, the 64 diamonds must still be there (in the placed box or handed back).
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102bShulkerDiamonds"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102bShulkerDiamonds")
	public void thePlayersFullShulkerBoxIsNeverTurnedIntoAnEmptyOneAndKeepsItsDiamonds(GameTestHelper helper) {
		ItemStack box = new ItemStack(Items.PURPLE_SHULKER_BOX);
		box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));
		oneBlockFromThePlayersItem(helper, Blocks.PURPLE_SHULKER_BOX, box, "diamonds", QaBuilders1102bGameTests::diamonds, be -> {
			int n = 0;
			if (be instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					n += diamonds(c.getItem(i));
				}
			}
			return n;
		});
	}

	// --------------------------------------------------------------------------------------------------------- helpers

	/**
	 * Scans {@code block} alone, puts {@code special} (the only item for it) and dirt in the supply chest and has a
	 * builder build the scan without free materials. Passes when the builder either waits for a plain one with
	 * {@code special} untouched, or finishes with every bit of {@code worth} still somewhere.
	 */
	static void oneBlockFromThePlayersItem(GameTestHelper helper, Block block, ItemStack special, String what, ToIntFunction<ItemStack> worth,
		ToIntFunction<BlockEntity> placedWorth) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC.below(), Blocks.STONE);
		helper.setBlock(SRC, block);
		ResourceLocation id = scan(helper, SRC, SRC);
		helper.setBlock(SRC, Blocks.AIR);
		int start = worth.applyAsInt(special);
		helper.assertTrue(start > 0, "setup: the special " + special + " carries no " + what);
		helper.setBlock(SUPPLY, Blocks.CHEST);
		Container chest = helper.getBlockEntity(SUPPLY);
		chest.setItem(0, special.copy());
		chest.setItem(1, new ItemStack(Items.DIRT, 64));
		long begun = level.getGameTime();
		Built b = buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		helper.succeedWhen(() -> {
			boolean building = BuildSiteManager.get(level).get(b.site().id()) != null;
			int inChest = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				inChest += worth.applyAsInt(chest.getItem(i));
			}
			if (building) {
				helper.assertTrue(b.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS && level.getGameTime() - begun > REFUSED_AFTER,
					"still building: " + b.site().stage() + " " + b.site().status() + " missing " + b.site().missing());
				helper.assertTrue(inChest == start, "the builder waits, but the chest's " + what + " went from " + start + " to " + inChest);
				helper.assertTrue(b.site().missing().containsKey(special.getItem()), "the builder waits for " + b.site().missing() + ", not the "
					+ special.getHoverName().getString());
				LOGGER.info("qa 1102b special {}: the builder refused the player's {} and waits for a plain one ({} {} kept)", what, special.getItem(), inChest, what);
				return;
			}
			BlockEntity be = level.getBlockEntity(b.origin());
			int inPlaced = be == null ? 0 : placedWorth.applyAsInt(be);
			int inBags = 0;
			AABB around = helper.getBounds().inflate(16);
			for (Villager v : level.getEntitiesOfClass(Villager.class, around)) {
				for (ItemStack s : ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()) {
					inBags += worth.applyAsInt(s);
				}
			}
			int onGround = 0;
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, around)) {
				onGround += worth.applyAsInt(e.getItem());
			}
			int all = inChest + inPlaced + inBags + onGround;
			helper.assertTrue(all == start, "the builder built the plain " + block.getName().getString() + " from the player's " + special.getHoverName().getString()
				+ " and " + (start - all) + " of its " + start + " " + what + " are gone (" + inChest + " in the chest, " + inPlaced + " on the placed block, "
				+ inBags + " in bags, " + onGround + " on the ground; placed " + level.getBlockState(b.origin()) + ")");
			LOGGER.info("qa 1102b special {}: built and all {} kept ({} chest, {} placed, {} bags, {} ground)", what, start, inChest, inPlaced, inBags, onGround);
		});
	}

	/** Sherds on a decorated pot item. */
	static int sherds(ItemStack stack) {
		PotDecorations d = stack.get(DataComponents.POT_DECORATIONS);
		if (d == null) {
			return 0;
		}
		return (int) d.ordered().stream().filter(i -> i.getDefaultInstance().is(ItemTags.DECORATED_POT_SHERDS)).count() * stack.getCount();
	}

	/** Diamonds in {@code stack}, counting those inside a shulker box item. */
	static int diamonds(ItemStack stack) {
		int n = stack.is(Items.DIAMOND) ? stack.getCount() : 0;
		ItemContainerContents in = stack.get(DataComponents.CONTAINER);
		if (in != null) {
			for (ItemStack s : in.nonEmptyItems()) {
				n += diamonds(s) * stack.getCount();
			}
		}
		return n;
	}

	record Built(BuildSite site, BlockPos origin) {
	}

	/** Scans the box between two relative corners with real clicks and returns the saved blueprint's id. */
	static ResourceLocation scan(GameTestHelper helper, BlockPos a, BlockPos b) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, a);
		QaStageThreeGameTests.click(helper, player, b);
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		return found(player);
	}

	private static ResourceLocation found(ServerPlayer player) {
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				return BlueprintItem.data(s).orElseThrow().structure();
			}
		}
		throw new GameTestAssertException("the scan gave no blueprint");
	}

	/** A builder at a bench builds the blueprint at ORIGIN with free materials. */
	static Built buildBack(GameTestHelper helper, ResourceLocation id, Rotation turn, Mirror mirror) {
		return buildBack(helper, id, turn, mirror, true);
	}

	/** The same, with free materials {@code freeGoods}. */
	static Built buildBack(GameTestHelper helper, ResourceLocation id, Rotation turn, Mirror mirror, boolean freeGoods) {
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(freeGoods, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlockPos origin = helper.absolutePos(ORIGIN);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, turn, mirror);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the scanned blueprint " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return new Built(site, origin);
	}

	/** A hand-built room, 6 wide (x), 4 tall, 5 deep (z), from its corner: a floor and a back wall; air left out. */
	static Map<BlockPos, BlockState> room() {
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 5; z++) {
				out.put(new BlockPos(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
			}
			for (int y = 1; y < 4; y++) {
				out.put(new BlockPos(x, y, 4), Blocks.COBBLESTONE.defaultBlockState());
			}
		}
		return out;
	}
}
