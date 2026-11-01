package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlans;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1101-0654) of the fixes for B744, B723, B745, B747, B748, B749 and B750, written from each bug's Expected
 * text. The fixes' own tests check one side of each boundary; these check the other: zero and eleven next to one, a
 * single item next to two, a piston one step further out and a row of blocks, the owner's piston at the very edge, a
 * waiting Wonder listed first and a house one column clear of it, a plan beside a house being built, an older wagon-bed
 * list's real camp bed after a save and reload, and the protected hall after its block entity is saved and loaded again.
 */
public class QaLaneC1101GameTests implements FabricGameTest {
	private static final String BUILD = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// ----------------------------------------------------------------------------------------------------------------
	// B744: counted sentences at 0, 1 and 11

	/**
	 * B744 from the player's side: a player sets the Village Hall down (its founding is the chronicle's first entry). The
	 * hall's Chronicle button reads "(0 entries)" before and "(1 entry)" after; never "(1 entries)".
	 */
	//$ gametest_ticks_batch BUILD '100' '"qaC1101HallFounded"'
	@GameTest(template = BUILD, timeoutTicks = 100, batch = "qaC1101HallFounded")
	public void qaAHallFoundedByAPlayerReadsOneEntry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		BlockPos at = new BlockPos(3, 2, 3);
		BlockPos hall = helper.absolutePos(at);
		player.moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5, 180f, 0f);
		ItemStack held = new ItemStack(ModBlocks.VILLAGE_HALL.asItem());
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		BlockPos floor = hall.below();
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(helper.getBlockState(at).is(ModBlocks.VILLAGE_HALL), "set-up: the player couldn't set the hall down: " + helper.getBlockState(at));
			VillageHallBlockEntity entity = helper.getBlockEntity(at);
			int n = entity.chronicle().size();
			List<String> lines = RealmGameTests.lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.CHRONICLE));
			String expected = n == 1 ? "What has happened here (1 entry): click to read" : "What has happened here (" + n + " entries): click to read";
			helper.assertTrue(lines.contains(expected), "a hall just founded (" + n + " chronicle entries): its button reads " + lines + ", expected \"" + expected + "\"");
			helper.assertTrue(lines.stream().noneMatch(l -> l.contains("(1 entries)")), "\"(1 entries)\" on the hall's button: " + lines);
			helper.succeed();
		});
	}

	/** B744's other boundary: an empty chronicle reads "(0 entries)", plural, as English counts zero. */
	//$ gametest_ticks_batch BUILD '100' '"qaC1101HallEmpty"'
	@GameTest(template = BUILD, timeoutTicks = 100, batch = "qaC1101HallEmpty")
	public void qaAnEmptyChronicleReadsZeroEntries(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		BlockPos at = new BlockPos(3, 2, 3);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL); // set by the test, not a player: nothing founded it in the chronicle
		BlockPos hall = helper.absolutePos(at);
		player.setPos(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		VillageHallBlockEntity entity = helper.getBlockEntity(at);
		entity.setOwner(player.getUUID(), "Owner");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(entity.chronicle().isEmpty(), "set-up: the chronicle holds " + entity.chronicle().size() + " entries");
			List<String> lines = RealmGameTests.lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.CHRONICLE));
			helper.assertTrue(lines.contains("What has happened here (0 entries): click to read"), "an empty chronicle: the hall's button reads " + lines);
			helper.succeed();
		});
	}

	/** B744's work_pace line, exactly, at one trade, eleven trades and none ("everyone"). */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaC1101Pace"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaC1101Pace")
	public void qaAWorkPaceCountsItsTradesExactly(GameTestHelper helper) {
		List<ResourceLocation> jobs = new ArrayList<>();
		for (int i = 0; i < 11; i++) {
			jobs.add(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "job" + i));
		}
		String one = EdictBook.describe(new CivicEffects.WorkPace(50, jobs.subList(0, 1))).getString();
		String eleven = EdictBook.describe(new CivicEffects.WorkPace(25, jobs)).getString();
		String none = EdictBook.describe(new CivicEffects.WorkPace(10, List.of())).getString();
		helper.assertTrue(one.equals("1 trade works 50% faster"), "one trade: \"" + one + "\"");
		helper.assertTrue(eleven.equals("11 trades work 25% faster"), "eleven trades: \"" + eleven + "\"");
		helper.assertTrue(none.equals("everyone works 10% faster"), "no trade named: \"" + none + "\"");
		helper.succeed();
	}

	// ----------------------------------------------------------------------------------------------------------------
	// B723: building materials in the plural (and still singular at one)

	/** B723's other side: one of each building material reads in the singular on the desk and the board. */
	//$ gametest_ticks_batch HUGE '40' '"qaC1101ShopSingular"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaC1101ShopSingular")
	public void qaOneOfEachBlueprintMaterialReadsInTheSingular(GameTestHelper helper) {
		QaShoppingPlurals1031GameTests.Village v = QaShoppingPlurals1031GameTests.village(helper, helper.makeMockServerPlayerInLevel());
		Map<Item, Integer> missing = new LinkedHashMap<>();
		missing.put(Items.SPRUCE_LOG, 1);
		missing.put(Items.GLASS_PANE, 1);
		missing.put(Items.OAK_DOOR, 1);
		missing.put(Items.LADDER, 1);
		QaShoppingPlurals1031GameTests.waitingSite(helper, v, missing);
		List<String> desk = QaShoppingPlurals1031GameTests.deskLines(v);
		String line = "Shopping list: 1 Spruce Log, 1 Glass Pane, 1 Oak Door, 1 Ladder";
		helper.assertTrue(desk.contains(line), "the desk doesn't read \"" + line + "\": " + desk);
		List<String> board = QaShoppingPlurals1031GameTests.boardLines(helper, v);
		List<String> expected = List.of("1 Spruce Log", "1 Glass Pane", "1 Oak Door", "1 Ladder");
		helper.assertTrue(board.equals(expected), "the Storehouse's board lists " + board + ", not " + expected);
		helper.succeed();
	}

	/** B723's kin named in its Expected: dark oak and birch logs, fences, trapdoors, chests, lanterns, bookshelves. */
	//$ gametest_ticks_batch HUGE '40' '"qaC1101ShopKin"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaC1101ShopKin")
	public void qaTheMaterialsKinReadInThePlural(GameTestHelper helper) {
		QaShoppingPlurals1031GameTests.Village v = QaShoppingPlurals1031GameTests.village(helper, helper.makeMockServerPlayerInLevel());
		Map<Item, Integer> missing = new LinkedHashMap<>();
		missing.put(Items.DARK_OAK_LOG, 9);
		missing.put(Items.BIRCH_LOG, 8);
		missing.put(Items.OAK_FENCE, 7);
		missing.put(Items.SPRUCE_TRAPDOOR, 6);
		missing.put(Items.CHEST, 5);
		missing.put(Items.LANTERN, 4);
		missing.put(Items.BOOKSHELF, 3);
		QaShoppingPlurals1031GameTests.waitingSite(helper, v, missing);
		List<String> board = QaShoppingPlurals1031GameTests.boardLines(helper, v);
		List<String> expected = List.of("9 Dark Oak Logs", "8 Birch Logs", "7 Oak Fences", "6 Spruce Trapdoors", "5 Chests", "4 Lanterns", "3 Bookshelves");
		helper.assertTrue(board.equals(expected), "the Storehouse's board lists " + board + ", not " + expected);
		helper.succeed();
	}

	/**
	 * B723's Expected over every blueprint the mod ships: each countable material placed by them (a log, door, fence,
	 * gate, trapdoor, pane, ladder, chest, lantern, bed, slab, wall, button, plate, carpet, banner, candle, pot, barrel,
	 * torch, sign) has a plural for counts above one.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '400' '"qaC1101ShopSweep"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qaC1101ShopSweep")
	public void qaEveryCountableBlueprintMaterialHasAPlural(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> countable = List.of(" Log", " Door", " Fence", " Fence Gate", " Trapdoor", " Pane", "Ladder", "Chest", "Lantern", " Bed",
			" Slab", " Wall", " Button", " Pressure Plate", " Carpet", " Banner", "Candle", "Flower Pot", "Barrel", "Torch", " Sign");
		Set<Item> used = new LinkedHashSet<>();
		int blueprints = 0;
		for (ResourceLocation id : BlueprintLibrary.list(level.getServer(), true)) {
			// the mod's shipped blueprints only: not structures other tests saved into the world's generated folder
			if (!id.getNamespace().equals(AliveWorkplace.MOD_ID) || level.getServer().getResourceManager()
				.getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt")).isEmpty()) {
				continue;
			}
			Optional<Blueprint> b = BlueprintLibrary.get(level, id);
			if (b.isEmpty()) {
				continue;
			}
			blueprints++;
			for (Blueprint.Entry e : b.get().blocks()) {
				Item item = e.state().getBlock().asItem();
				if (item != Items.AIR && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) {
					used.add(item);
				}
			}
		}
		helper.assertTrue(blueprints > 10 && used.size() > 50, "set-up: read " + blueprints + " blueprints, " + used.size() + " materials");
		List<String> singular = new ArrayList<>();
		int checked = 0;
		for (Item item : used) {
			String name = item.getDescription().getString();
			if (countable.stream().noneMatch(name::endsWith)) {
				continue;
			}
			checked++;
			if (Words.itemName(item, 2).getString().equals(name)) {
				singular.add(BuiltInRegistries.ITEM.getKey(item) + " (\"2 " + name + "\")");
			}
		}
		helper.assertTrue(checked > 20, "set-up: only " + checked + " countable materials found");
		helper.assertTrue(singular.isEmpty(), "of " + checked + " countable blueprint materials, these still read in the singular at 2: " + singular);
		helper.succeed();
	}

	// ----------------------------------------------------------------------------------------------------------------
	// B745: pistons at a protected village's edge. Hall at (11, 2, 11), reach 16 (ProtectionSpecGameTests.protectedHall).
	// On row z = 24 (13 south of the hall) x = 20 is the village's last column and x = 21 is outside.

	private static final int ROW = 24;

	private static void piston(GameTestHelper helper, Block block, BlockPos pos, Direction facing) {
		helper.setBlock(pos, block.defaultBlockState().setValue(PistonBaseBlock.FACING, facing));
	}

	private static void edgeIs(GameTestHelper helper, ServerPlayer stranger, BlockPos inside, BlockPos outside) {
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger, helper.absolutePos(inside)).isPresent(),
			"set-up: " + inside + " should be inside the protected village");
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger, helper.absolutePos(outside)).isEmpty(),
			"set-up: " + outside + " should be outside the protected village");
	}

	/** A piston two steps out pushes a block lying just outside into the village's edge: refused, nothing moves. */
	//$ gametest_ticks_batch HUGE '60' '"qaC1101PistonTwoOut"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaC1101PistonTwoOut")
	public void qaAPistonTwoOutCantPushABlockAcrossTheEdge(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		edgeIs(helper, ProtectionSpecGameTests.player(helper), new BlockPos(20, 2, ROW), new BlockPos(21, 2, ROW));
		BlockPos block = new BlockPos(21, 2, ROW);
		helper.setBlock(block, Blocks.OAK_PLANKS);
		piston(helper, Blocks.PISTON, block.east(), Direction.WEST);
		helper.setBlock(block.east(2), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(block.west()).isAir() && helper.getBlockState(block).is(Blocks.OAK_PLANKS),
				"a piston outside pushed a block into the village: at the edge " + helper.getBlockState(block.west()) + ", outside " + helper.getBlockState(block));
			helper.succeed();
		});
	}

	/** The control: a piston further out moving a block that stays outside works as in vanilla. */
	//$ gametest_ticks_batch HUGE '60' '"qaC1101PistonOutsideOnly"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaC1101PistonOutsideOnly")
	public void qaAPistonOutsideMovingOnlyOutsideBlocksStillWorks(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		BlockPos block = new BlockPos(22, 2, ROW); // pushed to 21, still outside
		helper.setBlock(block, Blocks.OAK_PLANKS);
		piston(helper, Blocks.PISTON, block.east(), Direction.WEST);
		helper.setBlock(block.east(2), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(block.west()).is(Blocks.OAK_PLANKS) && helper.getBlockState(block).is(Blocks.PISTON_HEAD),
				"a piston outside moving a block that stays outside was stopped: at 21 " + helper.getBlockState(block.west()) + ", at 22 " + helper.getBlockState(block));
			helper.succeed();
		});
	}

	/** A row of four blocks outside, the last of them shoved into the village: refused, the whole row stays. */
	//$ gametest_ticks_batch HUGE '60' '"qaC1101PistonRow"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaC1101PistonRow")
	public void qaAPistonCantShoveARowOfBlocksIntoTheVillage(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		for (int x = 21; x <= 24; x++) {
			helper.setBlock(new BlockPos(x, 2, ROW), Blocks.COBBLESTONE);
		}
		piston(helper, Blocks.PISTON, new BlockPos(25, 2, ROW), Direction.WEST);
		helper.setBlock(new BlockPos(26, 2, ROW), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(new BlockPos(20, 2, ROW)).isAir(),
				"a row pushed from outside put a block in the village's edge: " + helper.getBlockState(new BlockPos(20, 2, ROW)));
			for (int x = 21; x <= 24; x++) {
				helper.assertTrue(helper.getBlockState(new BlockPos(x, 2, ROW)).is(Blocks.COBBLESTONE), "the row moved: at x " + x + " " + helper.getBlockState(new BlockPos(x, 2, ROW)));
			}
			helper.succeed();
		});
	}

	/** The owner's piston on the village's last column pushes outward, out of the village, as before (row z = 11). */
	//$ gametest_ticks_batch HUGE '60' '"qaC1101PistonEdgeOut"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaC1101PistonEdgeOut")
	public void qaAPistonInsideAtTheEdgeStillPushesOut(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		BlockPos at = new BlockPos(26, 2, 11); // inside; its block at 27 (the last column) goes to 28, outside
		piston(helper, Blocks.PISTON, at, Direction.EAST);
		helper.setBlock(at.east(), Blocks.OAK_PLANKS);
		helper.setBlock(at.west(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(at.east(2)).is(Blocks.OAK_PLANKS) && helper.getBlockState(at.east()).is(Blocks.PISTON_HEAD),
				"the village's own piston at its edge didn't push out: at 27 " + helper.getBlockState(at.east()) + ", at 28 " + helper.getBlockState(at.east(2)));
			helper.succeed();
		});
	}

	/** The owner's sticky piston at the edge still pulls a block in from outside. */
	//$ gametest_ticks_batch HUGE '80' '"qaC1101StickyEdgeIn"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaC1101StickyEdgeIn")
	public void qaAStickyPistonInsideAtTheEdgeStillPullsIn(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		BlockPos at = new BlockPos(26, 2, 11);
		piston(helper, Blocks.STICKY_PISTON, at, Direction.EAST);
		helper.setBlock(at.west(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(at.east()).is(Blocks.PISTON_HEAD), "set-up: the sticky piston didn't extend: " + helper.getBlockState(at.east()));
			helper.setBlock(at.east(2), Blocks.OAK_PLANKS); // outside, against its head
			helper.setBlock(at.west(), Blocks.AIR);
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(helper.getBlockState(at.east()).is(Blocks.OAK_PLANKS) && helper.getBlockState(at.east(2)).isAir(),
					"the village's own sticky piston didn't pull the block in: at 27 " + helper.getBlockState(at.east()) + ", at 28 " + helper.getBlockState(at.east(2)));
				helper.succeed();
			});
		});
	}

	/** A hall whose owner hasn't switched protection on: an outside piston pushes in as in vanilla. */
	//$ gametest_ticks_batch HUGE '60' '"qaC1101PistonUnprotected"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaC1101PistonUnprotected")
	public void qaAPistonOutsideAnUnprotectedVillagePushesIn(GameTestHelper helper) {
		VillageHallBlockEntity hall = ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		hall.setProtected(false);
		BlockPos wall = new BlockPos(27, 2, 11);
		helper.setBlock(wall, Blocks.OAK_PLANKS);
		piston(helper, Blocks.PISTON, wall.east(), Direction.WEST);
		helper.setBlock(wall.east(2), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(wall.west()).is(Blocks.OAK_PLANKS) && helper.getBlockState(wall).is(Blocks.PISTON_HEAD),
				"an unprotected village stopped an outside piston: at 26 " + helper.getBlockState(wall.west()) + ", at 27 " + helper.getBlockState(wall));
			helper.succeed();
		});
	}

	/** Save and reload: the hall's block entity saved and loaded again (as a chunk reload does) still stops the piston. */
	//$ gametest_ticks_batch HUGE '60' '"qaC1101PistonReload"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaC1101PistonReload")
	public void qaAReloadedProtectedHallStillStopsAnOutsidePiston(GameTestHelper helper) {
		VillageHallBlockEntity hall = ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		ServerLevel level = helper.getLevel();
		BlockPos hallPos = hall.getBlockPos();
		CompoundTag saved = hall.saveWithFullMetadata(level.registryAccess());
		level.removeBlockEntity(hallPos);
		BlockEntity loaded = BlockEntity.loadStatic(hallPos, level.getBlockState(hallPos), saved, level.registryAccess());
		helper.assertTrue(loaded instanceof VillageHallBlockEntity, "set-up: the hall didn't load back: " + loaded);
		level.setBlockEntity(loaded);
		VillageHallBlockEntity back = (VillageHallBlockEntity) level.getBlockEntity(hallPos);
		Leftovers.after(helper, () -> back.setProtected(false));
		helper.assertTrue(back != null && back.isProtected(), "set-up: the reloaded hall isn't protected");
		BlockPos wall = new BlockPos(27, 2, 11);
		helper.setBlock(wall, Blocks.OAK_PLANKS);
		piston(helper, Blocks.PISTON, wall.east(), Direction.WEST);
		helper.setBlock(wall.east(2), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getBlockState(wall).is(Blocks.OAK_PLANKS) && helper.getBlockState(wall.west()).isAir(),
				"after the hall was saved and loaded, an outside piston shoved its edge block in: at 27 " + helper.getBlockState(wall) + ", at 26 " + helper.getBlockState(wall.west()));
			helper.succeed();
		});
	}

	// ----------------------------------------------------------------------------------------------------------------
	// B747: the Steward's plots and laid-out Wonders (set-up as in QaWonderPlots1101BugTests)

	private static final BlockPos PLOT_HALL = new BlockPos(2, 2, 14);
	private static final BlockPos PLOT_TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	/** The Well's spot nearest the hall: box x 8..14, z 10..16. */
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);

	private static BlockPos plotGround(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 22; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(PLOT_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(PLOT_TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(PLOT_HALL);
		Leftovers.wonderGround(helper, hall);
		return hall;
	}

	private static CityPlan homes(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(PLOT_HALL);
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		return CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells);
	}

	private static Plots.Verdict atSpot(GameTestHelper helper, CityPlan plan) {
		return Plots.check(helper.getLevel(), helper.absolutePos(PLOT_HALL), plan, "homes", WELL, helper.absolutePos(SPOT),
			Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
	}

	private static WonderSite layOut(GameTestHelper helper, BlockPos hall, BlockPos helperOrigin, boolean finished) {
		ServerLevel level = helper.getLevel();
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, hall, null, "", 1);
		Leftovers.after(helper, () -> manager.remove(site.id()));
		BlockPos origin = helper.absolutePos(helperOrigin);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(0, 0, -1));
		if (finished) {
			manager.finish(site.id(), 2);
		}
		return manager.site(site.id()).orElseThrow();
	}

	/**
	 * B747's CLEAR margin from both sides, for a waiting Wonder east of the Well's spot (x 8..14): stage I (9 wide) from
	 * x 16 leaves one block of gap and is refused as BUILD; from x 17 it leaves {@link Plots#CLEAR} (2) and the spot fits.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaC1101PlotsMargin"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaC1101PlotsMargin")
	public void qaAWaitingWondersClearMarginHoldsFromBothSides(GameTestHelper helper) {
		BlockPos hall = plotGround(helper);
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		CityPlan plan = homes(helper);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(atSpot(helper, plan).plot().isPresent(), "set-up: the Well doesn't fit on the open ground: " + atSpot(helper, plan));
			WonderSite far = layOut(helper, hall, new BlockPos(17, 2, 9), false);
			helper.assertTrue(far.goingUp() && far.parts().isEmpty(), "set-up: a Wonder waiting for its goods");
			Plots.Verdict clear = atSpot(helper, plan);
			helper.assertTrue(clear.plot().isPresent(), "a waiting Wonder " + Plots.CLEAR + " blocks clear of the Well's spot refuses it: " + clear);
			WonderManager.get(helper.getLevel()).remove(far.id());
			layOut(helper, hall, new BlockPos(16, 2, 9), false);
			Plots.Verdict near = atSpot(helper, plan);
			helper.assertTrue(near.plot().isEmpty() && near.reason().equals(Optional.of(Plots.Reason.BUILD)),
				"a waiting Wonder one block from the Well's spot: expected refused (BUILD), got " + near);
			helper.succeed();
		});
	}

	/** Two Wonders: a finished one elsewhere listed first, a waiting one over the spot second. Still refused, then free. */
	//$ gametest_ticks_batch HUGE '40' '"qaC1101PlotsSecond"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaC1101PlotsSecond")
	public void qaAWaitingWonderListedSecondStillHoldsItsGround(GameTestHelper helper) {
		BlockPos hall = plotGround(helper);
		CityPlan plan = homes(helper);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(atSpot(helper, plan).plot().isPresent(), "set-up: the Well doesn't fit on the open ground: " + atSpot(helper, plan));
			layOut(helper, hall, new BlockPos(18, 2, 18), true); // finished, well away from the spot
			WonderSite waiting = layOut(helper, hall, new BlockPos(7, 2, 9), false);
			Plots.Verdict over = atSpot(helper, plan);
			helper.assertTrue(over.plot().isEmpty() && over.reason().equals(Optional.of(Plots.Reason.BUILD)),
				"the Well's spot under a waiting Wonder (listed after a finished one): expected refused (BUILD), got " + over);
			WonderManager.get(helper.getLevel()).remove(waiting.id());
			helper.assertTrue(atSpot(helper, plan).plot().isPresent(), "the waiting Wonder is gone, the spot is still refused: " + atSpot(helper, plan));
			helper.succeed();
		});
	}

	/** Save and reload: the Wonder list saved and loaded again keeps the waiting Wonder's layout, which still refuses the spot. */
	//$ gametest_ticks_batch HUGE '40' '"qaC1101PlotsReload"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaC1101PlotsReload")
	public void qaAWaitingWonderStillHoldsItsGroundAfterASave(GameTestHelper helper) {
		BlockPos hall = plotGround(helper);
		CityPlan plan = homes(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			WonderManager live = WonderManager.get(level);
			WonderSite waiting = layOut(helper, hall, new BlockPos(7, 2, 9), false);
			WonderManager reloaded = WonderManager.load(live.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			WonderSite back = reloaded.site(waiting.id()).orElse(null);
			helper.assertTrue(back != null && back.goingUp() && !back.done() && back.placement().equals(waiting.placement()),
				"the waiting Wonder didn't come back from a save with its layout: " + back);
			level.getDataStorage().set(WonderManager.NAME, reloaded);
			Leftovers.after(helper, () -> level.getDataStorage().set(WonderManager.NAME, live));
			Plots.Verdict over = atSpot(helper, plan);
			helper.assertTrue(over.plot().isEmpty() && over.reason().equals(Optional.of(Plots.Reason.BUILD)),
				"after a save and reload, the Well's spot under the waiting Wonder: expected refused (BUILD), got " + over);
			reloaded.remove(waiting.id());
			live.remove(waiting.id());
			helper.succeed();
		});
	}

	// ----------------------------------------------------------------------------------------------------------------
	// B748: a Wonder Plan over a house being built (set-up as in WonderPlanOverSiteGameTests)

	private record Town(ServerLevel level, ServerPlayer player, List<Component> seen, Villager builder, ItemStack plan, BlockPos centre, BoundingBox box) {
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}

	private static String refusal(ServerLevel level, ItemStack plan, BlockPos at) {
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), size, at, Rotation.NONE);
		return WonderPlans.placeRefusal(level, WonderPlanItem.plan(plan).get(), placement, size).map(Component::getString).orElse("none");
	}

	private static Town town(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean legends = Wonders.LEGENDS;
		Wonders.LEGENDS = false;
		Leftovers.after(helper, () -> Wonders.LEGENDS = legends);
		BlockPos hallAt = new BlockPos(8, 2, 8);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaC48_"));
		VillageHallBlockEntity entity = helper.getBlockEntity(hallAt);
		entity.setOwner(c.player().getUUID(), c.player().getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.TOWN);
		BlockPos hall = helper.absolutePos(hallAt);
		Leftovers.wonderGround(helper, hall);
		BlockPos tableAt = new BlockPos(3, 2, 2);
		helper.setBlock(tableAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = villager(helper, new BlockPos(3, 2, 3), ModVillagers.BUILDER, 5);
		Builders.employ(level, builder, helper.absolutePos(tableAt));
		villager(helper, new BlockPos(13, 2, 3), VillagerProfession.FARMER, 1);
		villager(helper, new BlockPos(8, 2, 13), VillagerProfession.FARMER, 1);
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(level, hall, folly, Component.literal("Testholm"));
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlockPos centre = hall.offset(-24, 0, -24).offset(size.getX() / 2, 0, size.getZ() / 2);
		helper.assertTrue(refusal(level, plan, centre).equals("none"), "set-up: the open ground isn't free: " + refusal(level, plan, centre));
		BoundingBox box = BlueprintOutline.bounds(BlueprintItem.placementAt(level.dimension().location(), size, centre, Rotation.NONE), size);
		return new Town(level, c.player(), c.seen(), builder, plan, centre, box);
	}

	private static BuildSite hutAt(GameTestHelper helper, Town t, UUID owner, BlockPos origin) {
		BlueprintData.Placement hut = new BlueprintData.Placement(t.level().dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(t.level(), t.builder(), owner, "Someone", HUT, hut);
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(t.level()).remove(site.id());
			BuildSiteManager.get(t.level()).forgetFinished(hut);
		});
		return site;
	}

	/**
	 * B748's edge: a stranger's hut being built just east of the plan's footprint (no column shared) doesn't stop it; the
	 * same hut one column further west, under the plan's edge, does, with "It would stand over the Test Hut, a build in
	 * progress".
	 */
	//$ gametest_batch BUILD '"qaC1101PlanEdge"'
	@GameTest(template = BUILD, batch = "qaC1101PlanEdge")
	public void qaAPlanBesideAHouseBeingBuiltGoesDownOneColumnOverItDoesNot(GameTestHelper helper) {
		Town t = town(helper);
		BuildSite beside = hutAt(helper, t, UUID.randomUUID(), new BlockPos(t.box().maxX() + 1, t.box().minY(), t.box().minZ()));
		String besideWords = refusal(t.level(), t.plan(), t.centre());
		helper.assertTrue(besideWords.equals("none"), "a hut beside the plan (no column shared) refuses it: " + besideWords);
		Builders.cancel(t.level(), beside);
		hutAt(helper, t, UUID.randomUUID(), new BlockPos(t.box().maxX(), t.box().minY(), t.box().minZ()));
		String over = refusal(t.level(), t.plan(), t.centre());
		helper.assertTrue(over.equals("It would stand over the Test Hut, a build in progress"),
			"a stranger's hut being built under the plan's edge column: expected \"It would stand over the Test Hut, a build in progress\", got \"" + over + "\"");
		helper.succeed();
	}

	/**
	 * B748 through the player's own click: the owner right-clicks the ground with the plan where a house is being built.
	 * His action bar says why in red, and the plan keeps no placement there.
	 */
	//$ gametest_batch BUILD '"qaC1101PlanClick"'
	@GameTest(template = BUILD, batch = "qaC1101PlanClick")
	public void qaThePlayerSetsAPlanDownOverAHouseBeingBuiltAndIsTold(GameTestHelper helper) {
		Town t = town(helper);
		hutAt(helper, t, t.player().getUUID(), t.centre().offset(-2, 0, -2));
		ServerPlayer p = t.player();
		p.moveTo(t.centre().getX() + 0.5, t.centre().getY(), t.centre().getZ() + 6.5, 180f, 0f);
		ItemStack plan = t.plan().copy();
		p.setItemInHand(InteractionHand.MAIN_HAND, plan);
		int before = t.seen().size();
		BlockPos ground = t.centre().below();
		p.gameMode.useItemOn(p, t.level(), plan, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(ground).relative(Direction.UP, 0.5), Direction.UP, ground, false));
		List<String> told = t.seen().subList(before, t.seen().size()).stream().map(Component::getString).toList();
		helper.assertTrue(told.contains("It would stand over the Test Hut, a build in progress"), "the player was told " + told);
		ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
		helper.assertTrue(BlueprintItem.data(held).flatMap(BlueprintData::placement).isEmpty(), "the refused plan kept a placement: " + BlueprintItem.data(held));
		helper.succeed();
	}

	// ----------------------------------------------------------------------------------------------------------------
	// B750: a house over a Wonder going up (set-up as in QaWonderHouseTwoWonders1101BugTests)

	private record Yard(ServerLevel level, BlockPos hall, Villager builder, QaStageFiveGameTests.Client client) {
	}

	private static Yard yard(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> VillageProtection.ENABLED = protection);
		BlockPos hallAt = new BlockPos(24, 2, 24);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallAt);
		Leftovers.wonderGround(helper, hall);
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		BlockPos benchAt = new BlockPos(26, 2, 4);
		helper.setBlock(benchAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, benchAt.south());
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(benchAt));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaC50_"));
		return new Yard(level, hall, builder, c);
	}

	/** Hands the builder a Test Hut at helper {@code at}; what he said, and the site he took (cleaned up after). */
	private static List<String> handHut(GameTestHelper helper, Yard y, BlockPos at) {
		Vec3i size = BlueprintLibrary.get(y.level(), HUT).orElseThrow().size();
		ItemStack blueprint = BlueprintItem.create(HUT, size);
		BlueprintData.Placement over = new BlueprintData.Placement(y.level().dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(over)));
		y.client().player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		int before = y.client().seen().size();
		QaStageSixGameTests.clickVillager(y.client().player(), y.builder(), y.client().seen());
		BuildSite taken = Builders.activeSite(y.level(), y.builder());
		if (taken != null) {
			Leftovers.after(helper, () -> BuildSiteManager.get(y.level()).remove(taken.id()));
		}
		return y.client().seen().subList(before, y.client().seen().size()).stream().map(Component::getString).toList();
	}

	/**
	 * B750 with the order turned round: the waiting Wonder (x 2..10) is listed FIRST and the finished one (x 11..19)
	 * second; a hut on x 8..12 over both is refused with "That spot overlaps another build in progress (Test Folly)."
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaC1101HouseWaitingFirst"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaC1101HouseWaitingFirst")
	public void qaABuilderRefusesAHouseOverAWaitingWonderListedFirst(GameTestHelper helper) {
		Yard y = yard(helper);
		layOut(helper, y.hall(), new BlockPos(2, 2, 2), false);
		layOut(helper, y.hall(), new BlockPos(11, 2, 2), true);
		List<String> said = handHut(helper, y, new BlockPos(8, 2, 4));
		helper.assertTrue(Builders.activeSite(y.level(), y.builder()) == null, "the builder took a house over a waiting Wonder (listed first); he said " + said);
		helper.assertTrue(said.contains("That spot overlaps another build in progress (Test Folly)."), "the player was told " + said);
		helper.succeed();
	}

	/**
	 * B750's column boundary: a waiting Wonder on x 11..19. A hut on x 6..10 (one column clear of it, on open ground) is
	 * taken; the same hut on x 7..11 (sharing its first column) is refused.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaC1101HouseColumn"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaC1101HouseColumn")
	public void qaAHouseOneColumnClearOfAWaitingWonderIsTakenOneOverIsNot(GameTestHelper helper) {
		Yard y = yard(helper);
		layOut(helper, y.hall(), new BlockPos(11, 2, 2), false);
		List<String> over = handHut(helper, y, new BlockPos(7, 2, 4));
		helper.assertTrue(Builders.activeSite(y.level(), y.builder()) == null, "the builder took a hut sharing a column with a waiting Wonder; he said " + over);
		helper.assertTrue(over.contains("That spot overlaps another build in progress (Test Folly)."), "over its first column the player was told " + over);
		List<String> clear = handHut(helper, y, new BlockPos(6, 2, 4));
		helper.assertTrue(Builders.activeSite(y.level(), y.builder()) != null, "the builder refused a hut one column clear of the waiting Wonder; he said " + clear);
		helper.succeed();
	}

	// ----------------------------------------------------------------------------------------------------------------
	// B749: an older wagon-bed list's real camp bed stays a camp bed; a cabin bed beside it stays a home

	private static final BlockPos BED_HALL = new BlockPos(15, 2, 20);

	private static BlockPos bed(GameTestHelper helper, BlockPos foot, Block bed) {
		helper.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	/**
	 * B749 / B730 from the other side, across a save and reload: on a server whose list was saved before the fix (this
	 * version, no "complete" mark), a camp set down now lists its brown bedroll. After the list is saved and loaded again,
	 * that bedroll is still a wagon bed, while a green bed the player placed beside the same campfire, hay and Blueprint
	 * Table is a home: its resident keeps it at the hall's round and the census counts one bed and one wagon bed.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaC1101WagonOldListReload"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaC1101WagonOldListReload")
	public void qaAnOlderListsCampBedStaysACampBedAfterAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 18;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		CampBeds.Data live = CampBeds.Data.get(level);
		Leftovers.after(helper, () -> {
			CampBeds.Data.get(level).forget(area);
			level.getDataStorage().set(CampBeds.Data.NAME, live);
		});
		CompoundTag saved = CampBeds.Data.load(new CompoundTag(), level.registryAccess()).save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.contains("version") && !saved.contains("complete"), "set-up: not an older list: " + saved);
		level.getDataStorage().set(CampBeds.Data.NAME, CampBeds.Data.load(saved, level.registryAccess()));
		helper.assertTrue(!CampBeds.Data.get(level).complete() && !CampBeds.Data.get(level).looks(), "set-up: not an older list saved since B715");

		BlockPos roll = bed(helper, new BlockPos(6, 2, 7), Blocks.BROWN_BED);
		helper.setBlock(new BlockPos(10, 2, 6), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(12, 2, 9), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(12, 2, 4), ModBlocks.BLUEPRINT_TABLE);
		int listed = CampBeds.record(level, BoundingBox.fromCorners(roll.offset(-1, -1, -2), roll.offset(1, 1, 1)));
		helper.assertTrue(listed == 1 && CampBeds.isWagonBed(level, roll), "set-up: the camp's bedroll wasn't listed (" + listed + ")");
		CampBeds.Data reloaded = CampBeds.Data.load(CampBeds.Data.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(CampBeds.Data.NAME, reloaded);
		helper.assertTrue(reloaded.has(roll) && CampBeds.isWagonBed(level, roll), "after a save and reload the camp's own bedroll is no wagon bed");

		BlockPos cabin = bed(helper, new BlockPos(8, 2, 7), Blocks.GREEN_BED);
		Villager resident = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 10));
		resident.setNoAi(true);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(level, resident, cabin), "set-up: couldn't take the new bed");
			helper.setBlock(BED_HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(CampBeds.isWagonBed(level, roll), "at the hall's round the camp's own bedroll stopped being a wagon bed");
				helper.assertTrue(!CampBeds.isWagonBed(level, cabin) && cabin.equals(QaVillageGuards1031GameTests.home(resident)),
					"the cabin bed beside the camp: wagon " + CampBeds.isWagonBed(level, cabin) + ", home " + QaVillageGuards1031GameTests.home(resident));
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(BED_HALL));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == 1, "census: beds " + census.beds() + ", wagon " + census.wagonBeds());
				helper.succeed();
			});
		});
	}
}
