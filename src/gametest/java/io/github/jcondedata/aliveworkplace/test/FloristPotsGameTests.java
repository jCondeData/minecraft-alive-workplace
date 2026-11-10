package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * ROADMAP 46.3, the Florist makes pots and dyes: at the Flower Stand she makes flower pots and decorated pots, dyes from
 * the flowers she grows and moss carpet for builders, step by step like every crafting job. Through a builder's request:
 * a flower pot from bricks; from clay, the Mason firing the bricks first; the red dye for the Shepherd's bed; the missing
 * bricks named on the builder's line; a Florist saved and loaded mid-job carrying on; and with no Florist, the old dyer
 * leaving the pot alone and the line saying "Hire a Florist".
 */
public class FloristPotsGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STAND = new BlockPos(14, 2, 2);
	private static final BlockPos MASON = new BlockPos(14, 2, 12);
	private static final BlockPos LOOM = new BlockPos(10, 2, 12);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** The Florist's list is her own now: no stand-in makes it, and the Leatherworker keeps only leather goods. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFloristsListIsHerOwn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CraftJobs.Job.FLORIST.craftsNow(), "the Florist still has a stand-in");
		for (Item item : List.of(Items.FLOWER_POT, Items.DECORATED_POT, Items.MOSS_CARPET, Items.RED_DYE, Items.BLUE_DYE, Items.WHITE_DYE)) {
			helper.assertTrue(CraftJobs.maker(level, item) == CraftJobs.Job.FLORIST, item + " is made by " + CraftJobs.maker(level, item));
			helper.assertFalse(CraftJobs.makes(level, CraftJobs.Job.LEATHERWORKER, item), "the Leatherworker still makes " + item);
		}
		// the bricks stay the Mason's, candles the Beekeeper's stand-in's until 46.5
		helper.assertTrue(CraftJobs.maker(level, Items.BRICK) == CraftJobs.Job.MASON, "brick: " + CraftJobs.maker(level, Items.BRICK));
		helper.assertTrue(CraftJobs.maker(level, Items.CANDLE) == CraftJobs.Job.LEATHERWORKER, "candle: " + CraftJobs.maker(level, Items.CANDLE));
		helper.succeed();
	}

	/** A flower pot for a builder from three bricks in his chest: the Florist makes it at her stand and takes it to him. */
	//$ gametest_ticks_batch AREA '2400' '"floristPotBricks"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "floristPotBricks")
	public void theFloristMakesAFlowerPotFromBricks(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, pot(helper), new ItemStack(Items.BRICK, 3));
		Villager florist = florist(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0) >= 1, "the Florist made " + ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BRICK) == 0, chest.countItem(Items.BRICK) + " bricks left: the pot wasn't made from them");
		});
	}

	/**
	 * Only clay balls (and coal) in the builder's chest: the Mason fires the bricks at the furnace by his stonecutter and
	 * takes them to the builder's chest, then the Florist makes the pot from them.
	 */
	//$ gametest_ticks_batch AREA '4000' '"floristPotClay"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "floristPotClay")
	public void aMasonFiresTheBricksForAFlowerPotFromClay(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, pot(helper), new ItemStack(Items.CLAY_BALL, 3), new ItemStack(Items.COAL, 1));
		Villager florist = florist(helper);
		Villager mason = mason(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) >= 1, "the Mason fired " + ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0) >= 1, "the Florist made " + ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 0, chest.countItem(Items.CLAY_BALL) + " clay left: the bricks weren't fired from it");
		});
	}

	/**
	 * A red bed for a builder, with white wool and planks in his chest and poppies in the Florist's: she makes the red dye
	 * from her flowers for the Shepherd, who waits for it (he leaves the dyes to her) and makes the bed at his loom.
	 */
	//$ gametest_ticks_batch AREA '4000' '"floristDyeForBed"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "floristDyeForBed")
	public void theFloristMakesTheDyeForTheShepherdsBed(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, bed(helper, Blocks.RED_BED), new ItemStack(Items.WHITE_WOOL, 3), new ItemStack(Items.OAK_PLANKS, 3));
		Villager florist = florist(helper, new ItemStack(Items.POPPY, 6));
		Villager shepherd = shepherd(helper);
		java.util.concurrent.atomic.AtomicBoolean shepherdDyed = new java.util.concurrent.atomic.AtomicBoolean();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(shepherd);
			if (job != null && job.steps().stream().anyMatch(s -> s.out().is(Items.RED_DYE))) {
				shepherdDyed.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(shepherdDyed.get(), "the Shepherd made the dye himself, with a Florist at work nearby");
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0) >= 1, "the Florist made " + ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) >= 1, "the Shepherd made " + ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0));
			Container own = helper.getBlockEntity(STAND.east());
			helper.assertTrue(own.countItem(Items.POPPY) < 6, "the dye wasn't made from the Florist's poppies: " + own.countItem(Items.POPPY) + " left");
		});
	}

	/**
	 * Clay but no bricks and no Mason: the Florist makes nothing, takes nothing, and the builder's line names the bricks
	 * ("short of 3 Bricks to make 1 Flower Pot"), not the clay that makes them.
	 */
	//$ gametest_ticks_batch AREA '1400' '"floristNoBricks"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "floristNoBricks")
	public void missingBricksAreNamedOnTheShortageLine(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, pot(helper), new ItemStack(Items.CLAY_BALL, 3));
		Villager florist = florist(helper);
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 3, "the clay was taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0) == 0, "the Florist made " + ModAttachments.ITEMS_CRAFTED.getOrElse(florist, 0));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue(text != null && text.contains("Brick") && text.contains("to make") && text.contains("Flower Pot") && !text.contains("Clay"),
				"the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Saved and loaded just as she starts making the pot, the Florist carries on with the bricks in her bag and it arrives. */
	//$ gametest_ticks_batch AREA '3600' '"floristReload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "floristReload")
	public void aFloristLoadedMidJobFinishesThePot(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, pot(helper), new ItemStack(Items.BRICK, 3));
		AtomicReference<Villager> florist = new AtomicReference<>(florist(helper));
		AtomicReference<CrafterJob> saved = new AtomicReference<>();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(florist.get());
			if (saved.get() != null || job == null || job.phase() != CrafterJob.Phase.CRAFTING) {
				return;
			}
			saved.set(job);
			Villager copy = reload(helper.getLevel(), florist.get());
			helper.assertTrue(!ModAttachments.BUILDER_BAG.getOrCreate(copy).isEmpty(), "the bricks should be in the bag across the reload");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(copy) != null, "the job wasn't saved on the Florist");
			florist.set(copy);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(saved.get() != null, "the Florist never started making the pot");
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(florist.get()) == null, "the finished job is still saved on the Florist");
		});
	}

	/**
	 * No Florist, only the Leatherworker who made pots before 46.3: he leaves the pot alone (the bricks stay in the chest)
	 * and the builder's line says "Hire a Florist to make Flower Pots".
	 */
	//$ gametest_ticks_batch AREA '1400' '"floristAbsent"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "floristAbsent")
	public void withNoFloristTheLineSaysHireOne(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, pot(helper), new ItemStack(Items.BRICK, 3));
		helper.setBlock(STAND, Blocks.CAULDRON);
		Villager dyer = helper.spawn(EntityType.VILLAGER, STAND.south());
		Jobs.employ(helper.getLevel(), dyer, helper.absolutePos(STAND), PoiTypes.LEATHERWORKER, VillagerProfession.LEATHERWORKER);
		helper.runAfterDelay(200, () -> JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH)));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BRICK) == 3, "the Leatherworker took the bricks");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(dyer, 0) == 0, "the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(dyer, 0));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue("Hire a Florist to make Flower Pots".equals(text), "the builder's line: " + text);
			helper.succeed();
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

	/** A blueprint of one empty flower pot, standing on the floor. */
	private static ResourceLocation pot(GameTestHelper helper) {
		helper.setBlock(ROW, Blocks.FLOWER_POT);
		return capture(helper, ROW, new Vec3i(1, 1, 1), "pot");
	}

	/** A blueprint of one {@code bed}, its head to the south. */
	private static ResourceLocation bed(GameTestHelper helper, net.minecraft.world.level.block.Block bed) {
		BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
		helper.setBlock(ROW, foot);
		helper.setBlock(ROW.offset(0, 0, 1), foot.setValue(BedBlock.PART, BedPart.HEAD));
		return capture(helper, ROW, new Vec3i(1, 1, 2), "bed");
	}

	/** The blocks at {@code origin} ({@code size}) as a test blueprint, then cleared. */
	private static ResourceLocation capture(GameTestHelper helper, BlockPos origin, Vec3i size, String what) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"florist_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(origin).getX());
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

	/** A Florist at her Flower Stand, with a chest by it holding {@code own} (the flowers she grew). */
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

	/** A Mason at his stonecutter with a furnace by it (his kiln), and a chest. */
	private static Villager mason(GameTestHelper helper) {
		helper.setBlock(MASON, Blocks.STONECUTTER);
		helper.setBlock(MASON.east(), Blocks.FURNACE);
		helper.setBlock(MASON.west(), Blocks.CHEST);
		Villager mason = helper.spawn(EntityType.VILLAGER, MASON.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(MASON), PoiTypes.MASON, VillagerProfession.MASON);
		return mason;
	}

	/** A Shepherd at his loom, with a chest by it. */
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
