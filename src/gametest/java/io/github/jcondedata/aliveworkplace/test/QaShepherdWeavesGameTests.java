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
import io.github.jcondedata.aliveworkplace.store.StockOrders;
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
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-c-1110-0654) of ROADMAP 46.2, the Shepherd weaves, written from the spec: at his loom he makes wool from
 * string, dyes it and makes carpets, beds, banners, banner patterns and paintings for builders, with the chain steps
 * (the wool first, then the bed); a missing dye is named on the shortage line; with no Shepherd the line says "Hire a
 * Shepherd to make ...". Everything goes through a builder's request (or a Storehouse stock order for the things a
 * blueprint can't hold) reaching a Shepherd employed at a real loom.
 */
public class QaShepherdWeavesGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	private static final BlockPos STOREHOUSE = new BlockPos(12, 2, 12);
	private static final BlockPos STORE_CHEST = new BlockPos(12, 2, 14);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** Two blue beds with only two blue dyes (white beds dyed blue is the game's own recipe): the dye is enough. */
	//$ gametest_ticks_batch AREA '3000' '"qaWeaveTwoDyes"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaWeaveTwoDyes")
	public void twoBlueBedsNeedOnlyTwoBlueDyes(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.BLUE_BED), new ItemStack(Items.WHITE_WOOL, 6), new ItemStack(Items.OAK_PLANKS, 6),
			new ItemStack(Items.BLUE_DYE, 2));
		Villager shepherd = shepherd(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) >= 1, "the Shepherd made nothing");
		});
	}

	/** Three red carpets from white wool and red dye: the wool dyed, then the carpets, into the builder's build. */
	//$ gametest_ticks_batch AREA '3000' '"qaWeaveCarpet"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaWeaveCarpet")
	public void theShepherdMakesRedCarpetsFromWhiteWoolAndDye(GameTestHelper helper) {
		prepare(helper);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ROW.offset(x, 0, 0), Blocks.RED_CARPET);
		}
		ResourceLocation id = capture(helper, ROW, new Vec3i(3, 1, 1), "carpet");
		Asker a = asker(helper, id, new ItemStack(Items.WHITE_WOOL, 2), new ItemStack(Items.RED_DYE, 2));
		Villager shepherd = shepherd(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) >= 1, "the Shepherd made nothing");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.WHITE_WOOL) == 0, chest.countItem(Items.WHITE_WOOL) + " white wool left: the carpets weren't made from it");
		});
	}

	/**
	 * Two blue wool blocks from white wool and blue dye (dyed wool), with sheep grazing by his loom: the flock's work
	 * doesn't keep him from the builder's request, and none of the sheared wool ends up in the build's count.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaWeaveDyedWool"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaWeaveDyedWool")
	public void dyedWoolWithAFlockAroundTheLoom(GameTestHelper helper) {
		prepare(helper);
		helper.setBlock(ROW, Blocks.BLUE_WOOL);
		helper.setBlock(ROW.east(), Blocks.BLUE_WOOL);
		ResourceLocation id = capture(helper, ROW, new Vec3i(2, 1, 1), "bluewool");
		Asker a = asker(helper, id, new ItemStack(Items.WHITE_WOOL, 2), new ItemStack(Items.BLUE_DYE, 2));
		Villager shepherd = shepherd(helper);
		for (int i = 0; i < 2; i++) {
			Sheep sheep = helper.spawn(EntityType.SHEEP, STATION.offset(-2 - i, 0, 3));
			sheep.setColor(DyeColor.WHITE);
		}
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BLUE_DYE) == 0 && chest.countItem(Items.WHITE_WOOL) == 0,
				"the blue wool wasn't dyed from the chest's makings: " + chest.countItem(Items.WHITE_WOOL) + " wool, " + chest.countItem(Items.BLUE_DYE) + " dye left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) >= 1, "the Shepherd made nothing");
		});
	}

	/** Two white beds from string and planks: the wool spun from the string at the loom first, then the beds. */
	//$ gametest_ticks_batch AREA '3600' '"qaWeaveStringBeds"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaWeaveStringBeds")
	public void whiteBedsSpunFromString(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.WHITE_BED), new ItemStack(Items.STRING, 24), new ItemStack(Items.OAK_PLANKS, 6));
		Villager shepherd = shepherd(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.STRING) == 0, chest.countItem(Items.STRING) + " string left: the wool wasn't spun from it");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) >= 2, "the Shepherd made " + ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0));
		});
	}

	/** Saved and loaded between the chain's steps (the wool spun, the beds not yet made), he carries on and the beds arrive. */
	//$ gametest_ticks_batch AREA '4000' '"qaWeaveReloadChain"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaWeaveReloadChain")
	public void reloadedBetweenTheChainsStepsTheBedsStillArrive(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.WHITE_BED), new ItemStack(Items.STRING, 24), new ItemStack(Items.OAK_PLANKS, 6));
		AtomicReference<Villager> shepherd = new AtomicReference<>(shepherd(helper));
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(shepherd.get());
			if (reloaded.get() || job == null || job.phase() != CrafterJob.Phase.CRAFTING || job.step() < 1) {
				return;
			}
			reloaded.set(true);
			Villager copy = reload(helper.getLevel(), shepherd.get());
			CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
			helper.assertTrue(after != null && after.step() == job.step() && after.target() == Items.WHITE_BED,
				"the job between steps wasn't saved: " + after);
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(copy).count(Items.WHITE_WOOL) > 0,
				"the spun wool wasn't in the bag across the reload");
			shepherd.set(copy);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "the Shepherd never reached the chain's second step");
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(shepherd.get()) == null, "the finished job is still saved on the Shepherd");
		});
	}

	/**
	 * The loom broken while he's weaving: the job is let go and nothing is lost: every wool and plank comes back to the
	 * builder's chest or at his feet, as wool, planks or beds made from them.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaWeaveLoomBroken"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaWeaveLoomBroken")
	public void theLoomBrokenMidJobLosesNothing(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.BLUE_BED), new ItemStack(Items.WHITE_WOOL, 6), new ItemStack(Items.OAK_PLANKS, 6),
			new ItemStack(Items.BLUE_DYE, 6));
		Villager shepherd = shepherd(helper);
		AtomicLong broken = new AtomicLong(-1);
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(shepherd);
			if (broken.get() < 0 && job != null && job.phase() == CrafterJob.Phase.CRAFTING) {
				helper.destroyBlock(STATION);
				broken.set(helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.get() >= 0, "the Shepherd never started weaving");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(shepherd) == null, "the job is still held without a loom");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(shepherd).isEmpty(), "the makings are still in the bag without a loom");
			int wool = count(helper, a, Items.WHITE_WOOL) + count(helper, a, Items.BLUE_WOOL)
				+ 3 * (count(helper, a, Items.BLUE_BED) + count(helper, a, Items.WHITE_BED) + placedBeds(helper));
			int planks = count(helper, a, Items.OAK_PLANKS) + 3 * (count(helper, a, Items.BLUE_BED) + count(helper, a, Items.WHITE_BED) + placedBeds(helper));
			helper.assertTrue(wool >= 6 && planks >= 6, "lost with the loom: " + wool + " of 6 wool and " + planks + " of 6 planks found");
		});
	}

	/** No blue dye at all: the line, word for word, names the dye he's short of and what it's for. */
	//$ gametest_ticks_batch AREA '1400' '"qaWeaveDyeLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaWeaveDyeLine")
	public void theMissingDyeLineWordForWord(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.BLUE_BED), new ItemStack(Items.WHITE_WOOL, 6), new ItemStack(Items.OAK_PLANKS, 6));
		shepherd(helper);
		helper.runAfterDelay(1100, () -> {
			String text = line(helper, a);
			helper.assertTrue(text != null && text.matches("Can't be made yet: short of [1-9][0-9]* Blue Dye to make 2 Blue Beds"), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** No wool and no string: nothing is taken or made, and the line says what the beds are short of. */
	//$ gametest_ticks_batch AREA '1400' '"qaWeaveNoWool"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaWeaveNoWool")
	public void noWoolOrStringIsOnTheLine(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.BLUE_BED), new ItemStack(Items.OAK_PLANKS, 6), new ItemStack(Items.BLUE_DYE, 6));
		Villager shepherd = shepherd(helper);
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 6 && chest.countItem(Items.BLUE_DYE) == 6, "the makings were taken with no wool to weave");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) == 0, "the Shepherd made " + ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0));
			String text = line(helper, a);
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of ") && text.endsWith(" to make 2 Blue Beds")
				&& (text.contains("Wool") || text.contains("String")), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/**
	 * A Carpenter at his bench and no Shepherd: beds and a banner are the Shepherd's now, so the Carpenter leaves them
	 * alone and the line names the job to hire, both things in one line.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaWeaveCarpenter"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaWeaveCarpenter")
	public void aCarpenterDoesNotWeave(GameTestHelper helper) {
		prepare(helper);
		BlockState foot = Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
		for (int x = 0; x < 2; x++) {
			helper.setBlock(ROW.offset(x, 0, 0), foot);
			helper.setBlock(ROW.offset(x, 0, 1), foot.setValue(BedBlock.PART, BedPart.HEAD));
		}
		helper.setBlock(ROW.offset(3, 0, 0), Blocks.WHITE_BANNER);
		ResourceLocation id = capture(helper, ROW, new Vec3i(4, 1, 2), "bedsbanner");
		Asker a = asker(helper, id, new ItemStack(Items.WHITE_WOOL, 12), new ItemStack(Items.OAK_PLANKS, 6), new ItemStack(Items.BLUE_DYE, 6),
			new ItemStack(Items.STICK, 2));
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.WHITE_WOOL) == 12 && chest.countItem(Items.BLUE_DYE) == 6 && chest.countItem(Items.STICK) == 2,
				"the Carpenter took the makings");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
			String text = line(helper, a);
			helper.assertTrue("Hire a Shepherd to make Blue Beds, White Banners".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** A loom with only a nitwit and a baby beside it: neither weaves, and the line still says to hire a Shepherd. */
	//$ gametest_ticks_batch AREA '1400' '"qaWeaveNobody"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaWeaveNobody")
	public void aNitwitOrABabyByTheLoomDoesNotWeave(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.BLUE_BED), new ItemStack(Items.WHITE_WOOL, 6), new ItemStack(Items.OAK_PLANKS, 6),
			new ItemStack(Items.BLUE_DYE, 6));
		helper.setBlock(STATION, Blocks.LOOM);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager nitwit = helper.spawn(EntityType.VILLAGER, STATION.south());
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Villager baby = helper.spawn(EntityType.VILLAGER, STATION.south().west());
		baby.setAge(-24000);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.WHITE_WOOL) == 6 && chest.countItem(Items.BLUE_DYE) == 6, "the makings were taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(nitwit, 0) == 0 && ModAttachments.ITEMS_CRAFTED.getOrElse(baby, 0) == 0,
				"a villager with no job wove");
			String text = line(helper, a);
			helper.assertTrue("Hire a Shepherd to make Blue Beds".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Over his head while he weaves: "Shepherd · N woven" and "making 2 Blue Beds (step x of y)", word for word. */
	//$ gametest_ticks_batch AREA '3000' '"qaWeaveStatus"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaWeaveStatus")
	public void theStatusOverHisHeadWordForWord(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.WHITE_BED), new ItemStack(Items.STRING, 24), new ItemStack(Items.OAK_PLANKS, 6));
		Villager shepherd = shepherd(helper);
		Set<String> titles = ConcurrentHashMap.newKeySet();
		Set<String> lines = ConcurrentHashMap.newKeySet();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(shepherd, helper.getLevel().getGameTime());
			if (e != null) {
				titles.add(e.title().getString());
				lines.add(e.line().getString());
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			// between jobs his flock's own title ("Shepherd · 0 sheared") shows; while weaving, the woven count
			helper.assertTrue(titles.stream().anyMatch(t -> t.matches("Shepherd · [1-9][0-9]* woven"))
				&& titles.stream().allMatch(t -> t.matches("Shepherd · [0-9]+ (woven|sheared)")), "titles: " + titles);
			helper.assertTrue(lines.stream().anyMatch(l -> l.matches("making 2 White Beds \\(step [12] of 2\\)")), "lines: " + lines);
			helper.assertTrue(lines.contains("taking 2 White Beds to the builder"), "lines: " + lines);
		});
	}

	/** A Storehouse's stock orders for paintings and flower banner patterns, filled by the Shepherd from its store. */
	//$ gametest_ticks_batch AREA '4000' '"qaWeaveStock"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaWeaveStock")
	public void paintingsAndBannerPatternsForAStockOrder(GameTestHelper helper) {
		prepare(helper);
		Leftovers.halls(helper);
		int range = StockOrders.RANGE;
		StockOrders.RANGE = 16;
		Leftovers.after(helper, () -> StockOrders.RANGE = range);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		store.setItem(0, new ItemStack(Items.STICK, 64));
		store.setItem(1, new ItemStack(Items.STICK, 64));
		store.setItem(2, new ItemStack(Items.WHITE_WOOL, 16));
		store.setItem(3, new ItemStack(Items.PAPER, 16));
		store.setItem(4, new ItemStack(Items.OXEYE_DAISY, 16));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.PAINTING); // keep 16
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.FLOWER_BANNER_PATTERN); // keep 16
		Villager shepherd = shepherd(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(Items.PAINTING) >= 1, store.countItem(Items.PAINTING) + " paintings in the store");
			helper.assertTrue(store.countItem(Items.FLOWER_BANNER_PATTERN) >= 1, store.countItem(Items.FLOWER_BANNER_PATTERN) + " flower banner patterns in the store");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) >= 2, "the Shepherd made " + ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0));
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

	/** A blueprint of two {@code bed}s side by side, heads to the south. */
	private static ResourceLocation beds(GameTestHelper helper, Block bed) {
		for (int x = 0; x < 2; x++) {
			BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
			helper.setBlock(ROW.offset(x, 0, 0), foot);
			helper.setBlock(ROW.offset(x, 0, 1), foot.setValue(BedBlock.PART, BedPart.HEAD));
		}
		return capture(helper, ROW, new Vec3i(2, 1, 2), "beds");
	}

	/** The blocks at {@code origin} ({@code size}) as a test blueprint, then cleared. */
	private static ResourceLocation capture(GameTestHelper helper, BlockPos origin, Vec3i size, String what) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qaweave_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(origin).getX());
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

	/** A Shepherd at his loom, with a chest by it (where the flock's wool goes). */
	private static Villager shepherd(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.LOOM);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager shepherd = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(STATION), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		return shepherd;
	}

	private static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	/** {@code item} in the builder's chest and lying anywhere in the test area. */
	private static int count(GameTestHelper helper, Asker a, Item item) {
		Container chest = helper.getBlockEntity(a.chest());
		int n = chest.countItem(item);
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(20);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	/** Beds the builder already placed (their foot halves). */
	private static int placedBeds(GameTestHelper helper) {
		int n = 0;
		for (int x = 0; x < 2; x++) {
			BlockState s = helper.getBlockState(ROW.offset(x, 0, 0));
			if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.FOOT) {
				n++;
			}
		}
		return n;
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
