package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBlueprintUnlocksGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaBlueprintUnlocksGameTests.HALL;
import static io.github.jcondedata.aliveworkplace.test.QaBlueprintUnlocksGameTests.TABLE;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintNames;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.State;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-b-1027-0638): adversarial checks of six landed builder fixes, written from each bug's spec (ROADMAP
 * B419, B438, B441, B448, B458), not from the fixes' code. Each goes through the player's own entry points where there
 * is one (a right-click on the Table, the Scan Tool's clicks, a builder at his bench building the scan) and covers what
 * the bug's own test doesn't: a stack in the off hand, a blueprint already known, a mirrored and turned build with a
 * restart mid-build, glow frames and the material list, every mod facing block mirrored, and names that sanitizing
 * would lose entirely.
 */
public class QaBuilderFixes1027GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SRC = new BlockPos(18, 2, 18);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	// ---- B419: a found Blueprint in the off hand at the Blueprint Table ----

	/**
	 * B419, a stack: two found Market Stall Blueprints in the off hand (main hand empty) unlock the build once, say so
	 * once, and the Table keeps nothing: both stay in the off hand.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1027OffHandStack"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1027OffHandStack")
	public void b419TwoFoundBlueprintsInTheOffHandUnlockOnceAndStayInHand(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			ServerPlayer p = c.player();
			ItemStack two = BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), StarterBlueprints.MARKET_STALL.size());
			two.setCount(2);
			p.setItemInHand(InteractionHand.OFF_HAND, two);
			helper.assertTrue(p.getMainHandItem().isEmpty(), "the main hand isn't empty");
			QaBlueprintUnlocksGameTests.clickTable(helper, p, TABLE);
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, HALL, StarterBlueprints.MARKET_STALL) == State.UNLOCKED,
				"two found Market Stalls in the off hand didn't unlock it");
			helper.assertTrue(QaBlueprintUnlocksGameTests.linesOf(c.seen(), QaBlueprintUnlocksGameTests.NEW_LINE) == 1,
				"'new blueprint' said " + QaBlueprintUnlocksGameTests.linesOf(c.seen(), QaBlueprintUnlocksGameTests.NEW_LINE) + " times");
			helper.assertTrue(p.getOffhandItem().is(ModItems.BLUEPRINT) && p.getOffhandItem().getCount() == 2,
				"the off hand holds " + p.getOffhandItem() + ", not the two blueprints");
			helper.succeed();
		});
	}

	/**
	 * B419, a build the village knows already: unlocked from the main hand, the same blueprint then used from the off hand
	 * opens the Table once with it listed unlocked and says nothing new (no second "new blueprint" line).
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1027OffHandKnown"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1027OffHandKnown")
	public void b419AnAlreadyKnownBlueprintInTheOffHandOpensTheTableAndSaysNothingNew(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			ServerPlayer p = c.player();
			ItemStack fountain = BlueprintItem.create(StarterBlueprints.FOUNTAIN.id(), StarterBlueprints.FOUNTAIN.size());
			p.setItemInHand(InteractionHand.MAIN_HAND, fountain.copy());
			QaBlueprintUnlocksGameTests.clickTable(helper, p, TABLE);
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, HALL, StarterBlueprints.FOUNTAIN) == State.UNLOCKED, "the main hand didn't unlock the Fountain");
			p.containerMenu = p.inventoryMenu;
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, fountain.copy());
			c.sent();
			QaBlueprintUnlocksGameTests.clickTable(helper, p, TABLE);
			helper.assertTrue(QaBlueprintUnlocksGameTests.linesOf(c.seen(), QaBlueprintUnlocksGameTests.NEW_LINE) == 1,
				"a known Fountain in the off hand said 'new blueprint' again: " + QaStageFourGameTests.keys(c.seen()));
			List<TablePayloads.Open> opened = c.sent(TablePayloads.Open.class);
			helper.assertTrue(opened.size() == 1, "the Table screen opened " + opened.size() + " times");
			helper.assertTrue(opened.get(0).entries().stream().anyMatch(e -> e.id().equals(StarterBlueprints.FOUNTAIN.id()) && e.unlocked()),
				"the Table doesn't list the Fountain unlocked");
			helper.assertTrue(p.getOffhandItem().is(ModItems.BLUEPRINT), "the Table took the off hand's blueprint");
			helper.succeed();
		});
	}

	// ---- B438: mirrored double chests ----

	/**
	 * B438 through a scan: a room with a double chest facing east (with a diamond in it) and a trapped double chest facing
	 * west is scanned, then built by a builder turned a quarter and mirrored front-back, with a restart (the build sites
	 * read back from their save) mid-build. Both come out as double chests, halves joined, built empty, nothing skipped.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qa1027MirroredChests"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1027MirroredChests")
	public void b438AScannedRoomsDoubleChestsStayWholeMirroredTurnedAndRestarted(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockState east = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST).setValue(ChestBlock.TYPE, ChestType.LEFT);
		BlockState west = Blocks.TRAPPED_CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST).setValue(ChestBlock.TYPE, ChestType.LEFT);
		BlockPos eastAt = new BlockPos(1, 1, 1);
		BlockPos westAt = new BlockPos(3, 1, 1);
		Map<BlockPos, BlockState> drawn = new HashMap<>();
		drawn.put(eastAt, east);
		drawn.put(eastAt.relative(ChestBlock.getConnectedDirection(east)), east.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		drawn.put(westAt, west);
		drawn.put(westAt.relative(ChestBlock.getConnectedDirection(west)), west.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		ResourceLocation id = scanRoom(helper, null, src -> {
			drawn.forEach((rel, state) -> helper.setBlock(src.offset(rel), state));
			((Container) helper.getBlockEntity(src.offset(eastAt))).setItem(0, new ItemStack(Items.DIAMOND));
		});
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		long pairs = blueprint.blocks().stream().filter(e -> e.state().getBlock() instanceof ChestBlock && e.state().getValue(ChestBlock.TYPE) != ChestType.SINGLE).count();
		helper.assertTrue(pairs == 4, "the scan kept " + pairs + " double chest halves, not 4");
		Rotation turn = Rotation.CLOCKWISE_90;
		Mirror mirror = Mirror.FRONT_BACK;
		Build b = startBuild(helper, id, turn, mirror, true);
		AtomicBoolean restarted = restartMidBuild(helper, b);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			helper.assertTrue(restarted.get(), "the build never got to its walls, so there was no restart mid-build");
			for (BlockPos half : List.of(eastAt, westAt)) {
				BlockPos a = b.world(half);
				BlockPos other = b.world(half.relative(ChestBlock.getConnectedDirection(drawn.get(half))));
				BlockState sa = level.getBlockState(a);
				BlockState sb = level.getBlockState(other);
				helper.assertTrue(sa.getBlock() instanceof ChestBlock && sb.getBlock() instanceof ChestBlock, "no chest built at " + a + " / " + other + ": " + sa + ", " + sb);
				helper.assertTrue(sa.getValue(ChestBlock.TYPE) != ChestType.SINGLE && sb.getValue(ChestBlock.TYPE) == sa.getValue(ChestBlock.TYPE).getOpposite()
					&& a.relative(ChestBlock.getConnectedDirection(sa)).equals(other),
					"the mirrored, turned " + sa.getBlock() + " came out as two chests: " + sa + " / " + sb);
				Direction expected = turn.rotate(mirror.mirror(drawn.get(half).getValue(ChestBlock.FACING)));
				helper.assertTrue(sa.getValue(ChestBlock.FACING) == expected, sa.getBlock() + " faces " + sa.getValue(ChestBlock.FACING) + ", expected " + expected);
				helper.assertTrue(((Container) level.getBlockEntity(a)).isEmpty() && ((Container) level.getBlockEntity(other)).isEmpty(),
					"a built chest came with something in it (builders never copy contents)");
			}
			helper.assertTrue(b.skipped().get() == 0, b.skipped().get() + " step(s) skipped");
		});
	}

	// ---- B441: scans keep item frames, paintings, armor stands ----

	/**
	 * B441: a scanned room with a glow item frame holding a diamond, an item frame holding an emerald, a painting and an
	 * armor stand in an iron chestplate. The blueprint's material list (not placed) asks for one glow item frame, one item
	 * frame, one painting and one armor stand, and never the diamond, emerald or chestplate.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1027ScanList"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1027ScanList")
	public void b441AScannedRoomsMaterialListAsksForItsFramesPaintingAndStandButNotTheirContents(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ItemStack[] blueprintOut = new ItemStack[1];
		scanRoom(helper, blueprintOut, src -> hangDecorations(helper, src, true));
		BlueprintData data = BlueprintItem.data(blueprintOut[0]).orElseThrow();
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data).orElseThrow();
		Map<Item, Integer> need = new HashMap<>();
		list.lines().forEach(l -> need.merge(l.item(), l.need(), Integer::sum));
		for (Item wanted : List.of(Items.GLOW_ITEM_FRAME, Items.ITEM_FRAME, Items.PAINTING, Items.ARMOR_STAND)) {
			helper.assertTrue(need.getOrDefault(wanted, 0) == 1, "the material list asks for " + need.getOrDefault(wanted, 0) + " " + wanted + ": " + need);
		}
		for (Item free : List.of(Items.DIAMOND, Items.EMERALD, Items.IRON_CHESTPLATE)) {
			helper.assertTrue(!need.containsKey(free), "the material list asks for the " + free + " a frame or stand held: " + need);
		}
		helper.succeed();
	}

	/**
	 * B441, built mirrored: the scanned room built turned a quarter and mirrored left-right, from a chest with the
	 * frames, the painting and an armor stand, with a restart mid-build. The glow frame and the frame go up empty, once
	 * each (no duplicate after the restart), facing as the mirrored, turned wall does; the painting goes up; the stand goes
	 * up bare, facing its drawn facing mirrored and turned; the chest pays for each; no diamond or emerald appears.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qa1027ScanMirrored"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1027ScanMirrored")
	public void b441AScannedRoomBuiltMirroredHasItsFramesPaintingAndStandEmptyOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = scanRoom(helper, null, src -> hangDecorations(helper, src, true));
		Rotation turn = Rotation.CLOCKWISE_90;
		Mirror mirror = Mirror.LEFT_RIGHT;
		Build b = startBuild(helper, id, turn, mirror, false, new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.GLOW_ITEM_FRAME), new ItemStack(Items.ITEM_FRAME), new ItemStack(Items.PAINTING), new ItemStack(Items.ARMOR_STAND));
		Container chest = helper.getBlockEntity(CHEST);
		AtomicBoolean restarted = restartMidBuild(helper, b);
		Direction facing = turn.rotate(mirror.mirror(Direction.NORTH));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			helper.assertTrue(restarted.get(), "no restart mid-build");
			AABB area = new AABB(b.placement().origin()).inflate(7);
			List<GlowItemFrame> glows = level.getEntitiesOfClass(GlowItemFrame.class, area);
			List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, area, f -> !(f instanceof GlowItemFrame));
			List<Painting> paintings = level.getEntitiesOfClass(Painting.class, area);
			helper.assertTrue(glows.size() == 1, glows.size() + " glow item frames built");
			helper.assertTrue(frames.size() == 1, frames.size() + " item frames built");
			helper.assertTrue(paintings.size() == 1, paintings.size() + " paintings built");
			helper.assertTrue(glows.get(0).getItem().isEmpty() && frames.get(0).getItem().isEmpty(),
				"a built frame holds " + glows.get(0).getItem() + " / " + frames.get(0).getItem());
			helper.assertTrue(new AABB(b.world(GLOW_AT)).inflate(0.7).contains(glows.get(0).position()), "the glow frame is at " + glows.get(0).position()
				+ ", expected by " + b.world(GLOW_AT));
			helper.assertTrue(glows.get(0).getDirection() == facing && frames.get(0).getDirection() == facing && paintings.get(0).getDirection() == facing,
				"the mirrored, turned wall faces " + facing + "; the glow frame faces " + glows.get(0).getDirection() + ", the frame "
					+ frames.get(0).getDirection() + ", the painting " + paintings.get(0).getDirection());
			List<net.minecraft.world.entity.decoration.ArmorStand> stands = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, area);
			helper.assertTrue(stands.size() == 1, stands.size() + " armor stands built");
			helper.assertTrue(stands.get(0).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "the stand came with "
				+ stands.get(0).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
			Direction standFacing = turn.rotate(mirror.mirror(Direction.SOUTH));
			helper.assertTrue(Direction.fromYRot(stands.get(0).getYRot()) == standFacing, "the stand drawn facing south faces "
				+ Direction.fromYRot(stands.get(0).getYRot()) + " (yaw " + stands.get(0).getYRot() + "), expected " + standFacing);
			helper.assertTrue(chest.countItem(Items.GLOW_ITEM_FRAME) == 0 && chest.countItem(Items.ITEM_FRAME) == 0 && chest.countItem(Items.PAINTING) == 0
				&& chest.countItem(Items.ARMOR_STAND) == 0, "not paid for from the chest");
			helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area,
				e -> e.getItem().is(Items.DIAMOND) || e.getItem().is(Items.EMERALD)).isEmpty(), "a framed diamond or emerald came with the build");
		});
	}

	private static final BlockPos GLOW_AT = new BlockPos(1, 2, 2);
	private static final BlockPos FRAME_AT = new BlockPos(3, 1, 2);
	private static final BlockPos PAINTING_AT = new BlockPos(3, 2, 2);
	private static final BlockPos STAND_AT = new BlockPos(1, 1, 1);

	/** On the room's back wall, facing north: a glow frame with a diamond, a frame with an emerald, a painting; a dressed armor stand if {@code stand}. */
	private static void hangDecorations(GameTestHelper helper, BlockPos src, boolean stand) {
		ServerLevel level = helper.getLevel();
		List<net.minecraft.world.entity.Entity> made = new ArrayList<>();
		GlowItemFrame glow = new GlowItemFrame(level, helper.absolutePos(src.offset(GLOW_AT)), Direction.NORTH);
		glow.setItem(new ItemStack(Items.DIAMOND));
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(src.offset(FRAME_AT)), Direction.NORTH);
		frame.setItem(new ItemStack(Items.EMERALD));
		Painting painting = new Painting(level, helper.absolutePos(src.offset(PAINTING_AT)), Direction.NORTH,
			io.github.jcondedata.aliveworkplace.mc.Lookup.holderOrThrow(io.github.jcondedata.aliveworkplace.mc.Lookup.registry(level.registryAccess(),
				Registries.PAINTING_VARIANT), PaintingVariants.KEBAB));
		helper.assertTrue(glow.survives() && frame.survives() && painting.survives(), "a frame or the painting doesn't hang on the back wall");
		made.add(glow);
		made.add(frame);
		made.add(painting);
		if (stand) {
			net.minecraft.world.entity.decoration.ArmorStand armor = EntityType.ARMOR_STAND.create(level);
			BlockPos at = helper.absolutePos(src.offset(STAND_AT));
			armor.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			armor.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			made.add(armor);
		}
		made.forEach(level::addFreshEntity);
		Leftovers.after(helper, () -> made.forEach(net.minecraft.world.entity.Entity::discard));
	}

	// ---- B448: the mod's own facing blocks turn and mirror with the build ----

	/**
	 * B448 through a scan, mirrored: a room with a Storehouse, a Mailbox, a Shop Counter and a Grave, each facing a
	 * different way, is scanned and built by a builder turned a quarter and mirrored front-back, with a restart mid-build.
	 * Each faces its drawn facing mirrored and turned, as vanilla's facing blocks do, and nothing is skipped.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qa1027MirroredModBlocks"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1027MirroredModBlocks")
	public void b448AScannedRoomsModFacingBlocksTurnAndMirrorWithTheBuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> drawn = new HashMap<>();
		drawn.put(new BlockPos(0, 1, 1), ModBlocks.STOREHOUSE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
		drawn.put(new BlockPos(1, 1, 2), ModBlocks.MAILBOX.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
		drawn.put(new BlockPos(3, 1, 1), ModBlocks.SHOP_COUNTER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
		drawn.put(new BlockPos(4, 1, 2), ModBlocks.GRAVE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
		ResourceLocation id = scanRoom(helper, null, src -> drawn.forEach((rel, state) -> helper.setBlock(src.offset(rel), state)));
		Rotation turn = Rotation.CLOCKWISE_90;
		Mirror mirror = Mirror.FRONT_BACK;
		Build b = startBuild(helper, id, turn, mirror, true);
		AtomicBoolean restarted = restartMidBuild(helper, b);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			helper.assertTrue(restarted.get(), "no restart mid-build");
			List<String> wrong = new ArrayList<>();
			drawn.forEach((rel, state) -> {
				BlockState there = level.getBlockState(b.world(rel));
				Direction expected = turn.rotate(mirror.mirror(state.getValue(HorizontalDirectionalBlock.FACING)));
				if (!there.is(state.getBlock()) || there.getValue(HorizontalDirectionalBlock.FACING) != expected) {
					wrong.add(state.getBlock() + " drawn facing " + state.getValue(HorizontalDirectionalBlock.FACING) + " is " + there + ", expected facing " + expected);
				}
			});
			helper.assertTrue(wrong.isEmpty(), "mod blocks not turned and mirrored with the build: " + wrong);
			helper.assertTrue(b.skipped().get() == 0, b.skipped().get() + " step(s) skipped");
		});
	}

	/**
	 * B448's second fix: only the five data-holding blocks (Village Hall, Storehouse, Mailbox, Shop Counter, Grave) count as
	 * built whatever way they face (an old, unturned one stays standing); a vanilla facing block facing the wrong way still
	 * doesn't, so a build never takes a wrong-facing furnace or stairs as done.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void b448OnlyTheFiveDataBlocksCountAsBuiltFacingAnyWay(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Block block : List.<Block>of(ModBlocks.VILLAGE_HALL, ModBlocks.STOREHOUSE, ModBlocks.MAILBOX, ModBlocks.SHOP_COUNTER, ModBlocks.GRAVE)) {
			for (Direction want : Direction.Plane.HORIZONTAL) {
				for (Direction have : Direction.Plane.HORIZONTAL) {
					BlockState wanted = block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, want);
					BlockState actual = block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, have);
					if (!MaterialRules.matches(actual, wanted)) {
						wrong.add(block + " facing " + have + " isn't taken as built where facing " + want + " is planned");
					}
				}
			}
		}
		BlockState furnace = Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
		if (MaterialRules.matches(furnace, furnace.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST))) {
			wrong.add("a furnace facing north is taken as built where one facing east is planned");
		}
		BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
		if (MaterialRules.matches(stairs.setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH),
			stairs.setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH))) {
			wrong.add("stairs facing north are taken as built where stairs facing south are planned");
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong.subList(0, Math.min(4, wrong.size())));
		helper.succeed();
	}

	// ---- B458: a Scan Tool's anvil name kept as typed ----

	/**
	 * B458, a name sanitizing loses entirely: a Scan Tool renamed "日本の家" (no a-z or 0-9 at all) saves a build that is
	 * called "日本の家" (a " 2" if the plain id was taken) on the blueprint, in the saved line and wherever the id is
	 * named, and the name survives the world's names being saved and read back; its id stays plain.
	 */
	//$ gametest_batch AREA '"qa1027ScanNameUnicode"'
	@GameTest(template = AREA, batch = "qa1027ScanNameUnicode")
	public void b458AnAllUnicodeAnvilNameIsKeptAsTyped(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		String typed = "日本の家";
		ItemStack blueprint = scanPlank(helper, player, typed);
		ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();
		helper.assertTrue(id.getPath().matches("[a-z0-9_/]+"), "the id isn't plain: " + id);
		String shown = Blueprints.displayName(id).getString();
		helper.assertTrue(shown.equals(typed) || shown.matches(java.util.regex.Pattern.quote(typed) + " [0-9]+"), "the build is called " + shown);
		helper.assertTrue(blueprint.getHoverName().getString().contains(shown), "the blueprint item is called " + blueprint.getHoverName().getString());
		String line = BlueprintNamesGameTests.lastText(seen);
		helper.assertTrue(line.startsWith("message.aliveworkplace.scan.saved") && line.contains(shown), "the saved line: " + line);
		var registries = helper.getLevel().registryAccess();
		var loaded = BlueprintNames.Data.load(BlueprintNames.Data.get(helper.getLevel().getServer()).save(new CompoundTag(), registries), registries);
		helper.assertTrue(loaded.name(id).orElse("").equals(shown), "after a reload it's called " + loaded.name(id));
		helper.succeed();
	}

	/**
	 * B458, "kept exactly": a Scan Tool renamed "cozy den N" (lower case) is called "cozy den N", not the id-made
	 * "Cozy Den N"; one renamed "Cozy Den M", the same as its id-made name, is called that too.
	 */
	//$ gametest_batch AREA '"qa1027ScanNameCase"'
	@GameTest(template = AREA, batch = "qa1027ScanNameCase")
	public void b458ALowerCaseAnvilNameIsKeptExactly(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		long stamp = helper.getLevel().getGameTime();
		String lower = "cozy den " + stamp;
		ResourceLocation one = BlueprintItem.data(scanPlank(helper, player, lower)).orElseThrow().structure();
		helper.assertTrue(Blueprints.displayName(one).getString().equals(lower), "'" + lower + "' is called " + Blueprints.displayName(one).getString());
		String title = "Cozy Den " + (stamp + 1);
		ResourceLocation two = BlueprintItem.data(scanPlank(helper, player, title)).orElseThrow().structure();
		helper.assertTrue(Blueprints.displayName(two).getString().equals(title), "'" + title + "' is called " + Blueprints.displayName(two).getString());
		helper.succeed();
	}

	/** Scans the plank at (3,2,3) with a Scan Tool renamed {@code name} through the tool's own clicks; returns the blueprint. */
	private static ItemStack scanPlank(GameTestHelper helper, ServerPlayer player, String name) {
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.OAK_PLANKS);
		ItemStack tool = new ItemStack(ModItems.SCAN_TOOL);
		tool.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT));
		QaStageThreeGameTests.click(helper, player, new BlockPos(3, 2, 3));
		QaStageThreeGameTests.click(helper, player, new BlockPos(4, 3, 4));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		for (ItemStack stack : player.getInventory().items) {
			if (stack.is(ModItems.BLUEPRINT)) {
				ItemStack found = stack.copy();
				player.getInventory().removeItem(stack);
				return found;
			}
		}
		throw new GameTestAssertException("the scan named " + name + " gave no blueprint");
	}

	// ---- shared: a scanned room built by a builder ----

	/** A started build and where its blueprint's spots are in the world. */
	private record Build(BuildSite site, BlueprintData.Placement placement, AtomicInteger skipped) {
		BlockPos world(BlockPos local) {
			return placement.origin().offset(StructureTemplate.transform(local, placement.mirror(), placement.rotation(), BlockPos.ZERO));
		}
	}

	/**
	 * Builds a room at {@link #SRC} (a 5x4 stone brick floor, a cobblestone back wall 3 high on its far side,
	 * z = 3), lets {@code furnish} add to it (given the room's corner, relative), and scans it from the corner to (4,3,3)
	 * with the Scan Tool's own clicks. Returns the blueprint's id; {@code out[0]} gets the blueprint item if asked.
	 */
	private static ResourceLocation scanRoom(GameTestHelper helper, ItemStack[] out, Consumer<BlockPos> furnish) {
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 4; z++) {
				helper.setBlock(SRC.offset(x, 0, z), Blocks.STONE_BRICKS);
			}
			for (int y = 1; y < 4; y++) {
				helper.setBlock(SRC.offset(x, y, 3), Blocks.COBBLESTONE);
			}
		}
		furnish.accept(SRC);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, SRC);
		QaStageThreeGameTests.click(helper, player, SRC.offset(4, 3, 3));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				if (out != null) {
					out[0] = s.copy();
				}
				return BlueprintItem.data(s).orElseThrow().structure();
			}
		}
		throw new GameTestAssertException("the scan gave no blueprint");
	}

	/** A builder at the bench starts {@code id} turned and mirrored with its north-west corner at {@link #ORIGIN}, from a chest of {@code items}. */
	private static Build startBuild(GameTestHelper helper, ResourceLocation id, Rotation turn, Mirror mirror, boolean free, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(wasFree, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i]);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		BlockPos corner = helper.absolutePos(ORIGIN);
		BoundingBox box = BuildPlan.create(blueprint, new BlueprintData.Placement(level.dimension().location(), corner, turn, mirror)).bounds();
		BlockPos origin = corner.offset(corner.getX() - box.minX(), 0, corner.getZ() - box.minZ());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, turn, mirror);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the scanned blueprint " + id);
		}
		return new Build(site, placement, new AtomicInteger());
	}

	/** Once the build reaches its walls, its sites are read back from their save, as a restart does; tracks its skipped count. */
	private static AtomicBoolean restartMidBuild(GameTestHelper helper, Build build) {
		ServerLevel level = helper.getLevel();
		AtomicBoolean restarted = new AtomicBoolean();
		helper.onEachTick(() -> {
			BuildSite live = BuildSiteManager.get(level).get(build.site().id());
			if (live == null) {
				return;
			}
			build.skipped().set(live.skipped());
			if (!restarted.get() && live.stage() == BuildPlan.Stage.STRUCTURE) {
				restarted.set(true);
				var registries = level.registryAccess();
				level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			}
		});
		return restarted;
	}
}
