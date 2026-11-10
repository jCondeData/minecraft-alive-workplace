package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-c-1110) of ROADMAP 46.3, the Florist makes pots and dyes, written from the spec: flower pots (the Mason
 * fires the bricks if there are only clay balls, and only while a Florist works near the builder), decorated pots, dyes
 * from the flowers she grows (the Shepherd waits for hers only while she works near; with none he dyes himself), moss
 * carpet, and she still fills empty pots. Everything goes through a builder's request reaching a Florist employed at a
 * real workstation; the lines a player reads are compared word for word with en_us.json.
 */
public class QaFloristPots1110cGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STAND = new BlockPos(14, 2, 2);
	private static final BlockPos MASON = new BlockPos(14, 2, 12);
	private static final BlockPos LOOM = new BlockPos(10, 2, 12);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** Hired the guide's way, at a composter (her other workstation), she still makes the builder's flower pot from bricks. */
	//$ gametest_ticks_batch AREA '2400' '"qaFloristComposter"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFloristComposter")
	public void aFloristAtAComposterMakesAFlowerPot(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.FLOWER_POT.defaultBlockState(), "pot"), new ItemStack(Items.BRICK, 3));
		helper.setBlock(STAND, Blocks.COMPOSTER);
		helper.setBlock(STAND.east(), Blocks.CHEST);
		Villager florist = helper.spawn(EntityType.VILLAGER, STAND.south());
		Jobs.employ(helper.getLevel(), florist, helper.absolutePos(STAND), PoiTypes.FARMER, ModVillagers.FLORIST);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0) >= 1, "the Florist made " + ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0));
		});
	}

	/** A decorated pot for a builder from four bricks: she makes it and it's placed. */
	//$ gametest_ticks_batch AREA '2400' '"qaFloristDecorated"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFloristDecorated")
	public void theFloristMakesADecoratedPotFromBricks(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.DECORATED_POT.defaultBlockState(), "decorated"), new ItemStack(Items.BRICK, 4));
		Villager florist = florist(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0) >= 1, "the Florist made " + ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BRICK) == 0, chest.countItem(Items.BRICK) + " bricks left: the decorated pot wasn't made from them");
		});
	}

	/** Three moss carpets from two moss blocks (the game's recipe makes three). */
	//$ gametest_ticks_batch AREA '2400' '"qaFloristMoss"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFloristMoss")
	public void theFloristMakesMossCarpet(GameTestHelper helper) {
		prepare(helper);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ROW.offset(x, 0, 0), Blocks.MOSS_CARPET);
		}
		ResourceLocation id = capture(helper, ROW, new Vec3i(3, 1, 1), "moss");
		Asker a = asker(helper, id, new ItemStack(Items.MOSS_BLOCK, 2));
		Villager florist = florist(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0) >= 1, "the Florist made " + ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.MOSS_BLOCK) == 0, chest.countItem(Items.MOSS_BLOCK) + " moss blocks left");
		});
	}

	/**
	 * Clay and coal, a Mason with a furnace, but no Florist: the Mason fires no bricks (made with nobody to use them they'd
	 * only fill the chest), the clay stays, and the line says "Hire a Florist to make Flower Pots".
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaFloristNoFloristMason"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaFloristNoFloristMason")
	public void withNoFloristTheMasonFiresNoBricks(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.FLOWER_POT.defaultBlockState(), "pot"), new ItemStack(Items.CLAY_BALL, 3), new ItemStack(Items.COAL, 1));
		Villager mason = mason(helper, true);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 3, chest.countItem(Items.CLAY_BALL) + " clay left: the Mason fired it for nobody");
			helper.assertTrue(chest.countItem(Items.BRICK) == 0, chest.countItem(Items.BRICK) + " bricks fired for nobody");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) == 0, "the Mason made " + ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0));
			helper.assertTrue("Hire a Florist to make Flower Pots".equals(line(helper, a)), "the builder's line: " + line(helper, a));
			helper.succeed();
		});
	}

	/** Missing bricks with no Mason: the builder's whole line, word for word ("Can't be made yet: short of 3 Bricks to make 1 Flower Pot"). */
	//$ gametest_ticks_batch AREA '1400' '"qaFloristShortLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaFloristShortLine")
	public void theShortageLineWordForWord(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.FLOWER_POT.defaultBlockState(), "pot"), new ItemStack(Items.CLAY_BALL, 3));
		florist(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 3 Bricks to make 1 Flower Pot".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** A Carpenter at his bench with three bricks about never makes the flower pot now; the line names the Florist. */
	//$ gametest_ticks_batch AREA '1400' '"qaFloristCarpenter"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaFloristCarpenter")
	public void aCarpenterNeverMakesAFlowerPot(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.FLOWER_POT.defaultBlockState(), "pot"), new ItemStack(Items.BRICK, 3));
		helper.setBlock(STAND, ModBlocks.CARPENTERS_BENCH);
		helper.setBlock(STAND.east(), Blocks.CHEST);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STAND.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STAND), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BRICK) == 3, "the Carpenter took the bricks: " + chest.countItem(Items.BRICK) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
			helper.assertTrue("Hire a Florist to make Flower Pots".equals(line(helper, a)), "the builder's line: " + line(helper, a));
			helper.succeed();
		});
	}

	/** No Florist, a decorated pot and moss carpet wanted: one line names her for both, plural, word for word. */
	//$ gametest_ticks_batch AREA '1400' '"qaFloristHireBoth"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaFloristHireBoth")
	public void theHireLineNamesBothOfHerThings(GameTestHelper helper) {
		prepare(helper);
		helper.setBlock(ROW, Blocks.DECORATED_POT);
		helper.setBlock(ROW.east(), Blocks.MOSS_CARPET);
		ResourceLocation id = capture(helper, ROW, new Vec3i(2, 1, 1), "both");
		Asker a = asker(helper, id);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			String text = line(helper, a);
			helper.assertTrue("Hire a Florist to make Decorated Pots, Moss Carpets".equals(text) || "Hire a Florist to make Moss Carpets, Decorated Pots".equals(text),
				"the builder's line: " + text);
			helper.succeed();
		});
	}

	/** With no Florist about the Shepherd makes the red dye for the bed himself from the poppies in the builder's chest (46.2 kept). */
	//$ gametest_ticks_batch AREA '4000' '"qaFloristShepherdAlone"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaFloristShepherdAlone")
	public void withNoFloristTheShepherdDyesHimself(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, bed(helper, Blocks.RED_BED), new ItemStack(Items.WHITE_WOOL, 3), new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(Items.POPPY, 2));
		Villager shepherd = shepherd(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) >= 1, "the Shepherd made " + ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.POPPY) < 2, "the dye wasn't made from the poppies: " + chest.countItem(Items.POPPY) + " left");
		});
	}

	/** A blue bed, cornflowers in the Florist's chest: she makes the blue dye for the Shepherd, who never makes it himself. */
	//$ gametest_ticks_batch AREA '4000' '"qaFloristBlueDye"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaFloristBlueDye")
	public void theFloristMakesBlueDyeFromCornflowersForABlueBed(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, bed(helper, Blocks.BLUE_BED), new ItemStack(Items.WHITE_WOOL, 3), new ItemStack(Items.OAK_PLANKS, 3));
		Villager florist = florist(helper, new ItemStack(Items.CORNFLOWER, 4));
		Villager shepherd = shepherd(helper);
		AtomicBoolean shepherdDyed = new AtomicBoolean();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(shepherd);
			if (job != null && job.steps().stream().anyMatch(s -> s.out().is(Items.BLUE_DYE))) {
				shepherdDyed.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(shepherdDyed.get(), "the Shepherd made the blue dye himself with a Florist at work nearby");
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0) >= 1, "the Florist made " + ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0));
			Container own = helper.getBlockEntity(STAND.east());
			helper.assertTrue(own.countItem(Items.CORNFLOWER) < 4, "the dye wasn't made from her cornflowers: " + own.countItem(Items.CORNFLOWER) + " left");
		});
	}

	/** Regression: with poppies in her chest and no builder waiting, she still puts a flower in an empty pot near her stand. */
	//$ gametest_ticks_batch AREA '2400' '"qaFloristFillsPots"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFloristFillsPots")
	public void sheStillFillsEmptyPots(GameTestHelper helper) {
		prepare(helper);
		helper.setBlock(ROW, Blocks.FLOWER_POT);
		florist(helper, new ItemStack(Items.POPPY, 8), new ItemStack(Items.BONE_MEAL, 8));
		helper.succeedWhen(() -> {
			BlockState state = helper.getBlockState(ROW);
			helper.assertTrue(state.getBlock() instanceof FlowerPotBlock && !state.is(Blocks.FLOWER_POT), "the pot is still " + state);
		});
	}

	/**
	 * She makes the builder's flower pot and has poppies to fill pots with: the build still finishes (her filling the pot
	 * the builder just placed doesn't leave the site stuck), and a pot stands there.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaFloristMakesAndFills"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaFloristMakesAndFills")
	public void herOwnPotFilledDoesNotStallTheBuild(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.FLOWER_POT.defaultBlockState(), "pot"), new ItemStack(Items.BRICK, 3));
		florist(helper, new ItemStack(Items.POPPY, 8));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null,
				"still building: status=" + a.site().status() + " missing=" + a.site().missing());
			helper.assertTrue(helper.getBlockState(ROW).getBlock() instanceof FlowerPotBlock, "no pot at the site: " + helper.getBlockState(ROW));
		});
	}

	/** Mid-chain: the Mason saved and loaded while he fires the bricks for her pot; the bricks still come and the pot is built. */
	//$ gametest_ticks_batch AREA '4500' '"qaFloristMasonReload"'
	@GameTest(template = AREA, timeoutTicks = 4500, batch = "qaFloristMasonReload")
	public void aMasonLoadedMidFiringStillFeedsThePot(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.FLOWER_POT.defaultBlockState(), "pot"), new ItemStack(Items.CLAY_BALL, 3), new ItemStack(Items.COAL, 1));
		florist(helper);
		AtomicReference<Villager> mason = new AtomicReference<>(mason(helper, true));
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (reloaded.get() || ModAttachments.CRAFTER_JOB.get(mason.get()) == null) {
				return;
			}
			reloaded.set(true);
			Villager copy = reload(helper.getLevel(), mason.get());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(copy) != null, "the Mason's job wasn't saved");
			mason.set(copy);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "the Mason never started firing");
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 0, chest.countItem(Items.CLAY_BALL) + " clay left");
		});
	}

	/**
	 * Her stand broken while the bricks are in her bag: nothing is lost. After the grace time the bricks go back to the
	 * builder's chest (or the pot she finished, or they lie at her feet), three bricks' worth in all.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaFloristStandBroken"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaFloristStandBroken")
	public void herStandBrokenMidJobLosesNothing(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.FLOWER_POT.defaultBlockState(), "pot"), new ItemStack(Items.BRICK, 3));
		Villager florist = florist(helper);
		AtomicLong broke = new AtomicLong(-1);
		helper.onEachTick(() -> {
			if (broke.get() >= 0) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(florist);
			if (job != null && !ModAttachments.BUILDER_BAG.getOrCreate(florist).isEmpty()) {
				helper.setBlock(STAND, Blocks.AIR);
				broke.set(helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broke.get() >= 0, "she never had the bricks in her bag");
			helper.assertTrue(helper.getTick() > broke.get() + 700, "waiting out the grace time");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(florist) == null, "the job is still saved on her");
			Container chest = helper.getBlockEntity(a.chest());
			int bricks = chest.countItem(Items.BRICK);
			int pots = chest.countItem(Items.FLOWER_POT) + (helper.getBlockState(ROW).getBlock() instanceof FlowerPotBlock ? 1 : 0);
			AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24);
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
				if (e.getItem().is(Items.BRICK)) {
					bricks += e.getItem().getCount();
				} else if (e.getItem().is(Items.FLOWER_POT)) {
					pots += e.getItem().getCount();
				}
			}
			bricks += ModAttachments.BUILDER_BAG.getOrCreate(florist).isEmpty() ? 0 : 1000;
			helper.assertTrue(bricks == 3 || pots >= 1, "lost: " + bricks + " bricks and " + pots + " pots found (1000+ means still in her bag)");
		});
	}

	/** Over her head while she makes a pot: "Florist · 1 made for builders", "making 1 Flower Pot", "taking 1 Flower Pot to the builder". */
	//$ gametest_ticks_batch AREA '2400' '"qaFloristStatus"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFloristStatus")
	public void theStatusOverHerHeadWordForWord(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.FLOWER_POT.defaultBlockState(), "pot"), new ItemStack(Items.BRICK, 3));
		Villager florist = florist(helper);
		Set<String> titles = ConcurrentHashMap.newKeySet();
		Set<String> lines = ConcurrentHashMap.newKeySet();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(florist, helper.getLevel().getGameTime());
			if (e != null) {
				titles.add(e.title().getString());
				lines.add(e.line().getString());
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(titles.contains("Florist · 1 made for builders"), "titles: " + titles);
			helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("making 1 Flower Pot")), "lines: " + lines);
			helper.assertTrue(lines.contains("taking 1 Flower Pot to the builder"), "lines: " + lines);
		});
	}

	// ---- helpers ----

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
	}

	private static String line(GameTestHelper helper, Asker a) {
		Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	private static ResourceLocation single(GameTestHelper helper, BlockState state, String what) {
		helper.setBlock(ROW, state);
		return capture(helper, ROW, new Vec3i(1, 1, 1), what);
	}

	private static ResourceLocation bed(GameTestHelper helper, Block bed) {
		BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
		helper.setBlock(ROW, foot);
		helper.setBlock(ROW.offset(0, 0, 1), foot.setValue(BedBlock.PART, BedPart.HEAD));
		return capture(helper, ROW, new Vec3i(1, 1, 2), "bed");
	}

	private static ResourceLocation capture(GameTestHelper helper, BlockPos origin, Vec3i size, String what) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qaflorist_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(origin).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < size.getX(); x++) {
			for (int y = 0; y < size.getY(); y++) {
				for (int z = 0; z < size.getZ(); z++) {
					helper.setBlock(origin.offset(x, y, z), Blocks.AIR);
				}
			}
		}
		return id;
	}

	private static Asker asker(GameTestHelper helper, ResourceLocation id, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return new Asker(builder, site, plan, CHEST);
	}

	/** A Florist at her Flower Stand, with a chest by it holding {@code own}. */
	private static Villager florist(GameTestHelper helper, ItemStack... own) {
		helper.setBlock(STAND, ModBlocks.FLOWER_STAND);
		helper.setBlock(STAND.east(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(STAND.east());
		for (int i = 0; i < own.length; i++) {
			chest.setItem(i, own[i].copy());
		}
		Villager florist = helper.spawn(EntityType.VILLAGER, STAND.south());
		Jobs.employ(helper.getLevel(), florist, helper.absolutePos(STAND), ModVillagers.FLOWER_STAND_POI, ModVillagers.FLORIST);
		return florist;
	}

	private static Villager mason(GameTestHelper helper, boolean furnace) {
		helper.setBlock(MASON, Blocks.STONECUTTER);
		if (furnace) {
			helper.setBlock(MASON.east(), Blocks.FURNACE);
		}
		helper.setBlock(MASON.west(), Blocks.CHEST);
		Villager mason = helper.spawn(EntityType.VILLAGER, MASON.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(MASON), PoiTypes.MASON, VillagerProfession.MASON);
		return mason;
	}

	private static Villager shepherd(GameTestHelper helper) {
		helper.setBlock(LOOM, Blocks.LOOM);
		helper.setBlock(LOOM.west(), Blocks.CHEST);
		Villager shepherd = helper.spawn(EntityType.VILLAGER, LOOM.south());
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(LOOM), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		return shepherd;
	}

	private static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}
