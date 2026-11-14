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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-c-1110) of ROADMAP 46.4, the Cleric brews and makes the magic blocks, written from the spec: brewing
 * stands, blaze powder, eyes of ender, ender chests, end rods and beacons for builders, besides his potions; "Hire a
 * Cleric to make ..." when none works here; the Librarian (46.1's stand-in) no longer makes them. Everything goes through
 * a builder's request reaching a Cleric employed at a real brewing stand; the lines a player reads are compared word
 * for word with en_us.json.
 */
public class QaClericCrafts1110cGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STAND = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** The blaze rod in the Cleric's own chest, the cobblestone in the builder's: he takes from both and the stand is built. */
	//$ gametest_ticks_batch AREA '2400' '"qaClericOwnRod"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaClericOwnRod")
	public void theBlazeRodFromHisOwnChestMakesTheStand(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BREWING_STAND, "ownrod"), new ItemStack(Items.COBBLESTONE, 3));
		Villager cleric = cleric(helper, new ItemStack(Items.BLAZE_ROD, 1));
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) >= 1, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0));
			Container own = helper.getBlockEntity(STAND.east());
			helper.assertTrue(own.countItem(Items.BLAZE_ROD) == 0, "his blaze rod is still in his chest");
		});
	}

	/** A beacon from five glass, three obsidian and a nether star in the builder's chest: he makes it and it's placed. */
	//$ gametest_ticks_batch AREA '2400' '"qaClericBeacon"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaClericBeacon")
	public void theClericMakesABeacon(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BEACON, "beacon"), new ItemStack(Items.GLASS, 5), new ItemStack(Items.OBSIDIAN, 3),
			new ItemStack(Items.NETHER_STAR, 1));
		Villager cleric = cleric(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) >= 1, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.NETHER_STAR) == 0 && chest.countItem(Items.GLASS) == 0 && chest.countItem(Items.OBSIDIAN) == 0,
				"the beacon wasn't made from the makings: " + chest.countItem(Items.NETHER_STAR) + " star, " + chest.countItem(Items.GLASS) + " glass, "
					+ chest.countItem(Items.OBSIDIAN) + " obsidian left");
		});
	}

	/** Glass and obsidian but no nether star: nothing is taken and the line, word for word, names the star alone. */
	//$ gametest_ticks_batch AREA '1400' '"qaClericNoStar"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaClericNoStar")
	public void aMissingNetherStarIsNamedWordForWord(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BEACON, "nostar"), new ItemStack(Items.GLASS, 5), new ItemStack(Items.OBSIDIAN, 3));
		Villager cleric = cleric(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.GLASS) == 5 && chest.countItem(Items.OBSIDIAN) == 3, "the makings were taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) == 0, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0));
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 1 Nether Star to make 1 Beacon".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Four end rods from one blaze rod and one popped chorus fruit (the game's recipe makes four). */
	//$ gametest_ticks_batch AREA '2400' '"qaClericEndRods"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaClericEndRods")
	public void theClericMakesFourEndRodsFromOneBlazeRod(GameTestHelper helper) {
		prepare(helper);
		for (int x = 0; x < 4; x++) {
			helper.setBlock(ROW.offset(x, 0, 0), Blocks.END_ROD);
		}
		Asker a = asker(helper, capture(helper, ROW, new Vec3i(4, 1, 1), "endrods"), new ItemStack(Items.BLAZE_ROD, 1),
			new ItemStack(Items.POPPED_CHORUS_FRUIT, 1));
		Villager cleric = cleric(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) >= 1, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BLAZE_ROD) == 0 && chest.countItem(Items.POPPED_CHORUS_FRUIT) == 0, "the rods weren't made from the makings");
		});
	}

	/** Cobblestone but no blaze rod: the builder's whole line, word for word. */
	//$ gametest_ticks_batch AREA '1400' '"qaClericShortLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaClericShortLine")
	public void theBlazeRodLineWordForWord(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BREWING_STAND, "short"), new ItemStack(Items.COBBLESTONE, 3));
		cleric(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 1 Blaze Rod to make 1 Brewing Stand".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** A Carpenter at his bench with a blaze rod and cobblestone about never makes the brewing stand; the line names the Cleric. */
	//$ gametest_ticks_batch AREA '1400' '"qaClericCarpenter"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaClericCarpenter")
	public void aCarpenterNeverMakesABrewingStand(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BREWING_STAND, "carp"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.COBBLESTONE, 3));
		helper.setBlock(STAND, ModBlocks.CARPENTERS_BENCH);
		helper.setBlock(STAND.east(), Blocks.CHEST);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STAND.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STAND), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BLAZE_ROD) == 1 && chest.countItem(Items.COBBLESTONE) == 3, "the Carpenter took the makings");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
			helper.assertTrue("Hire a Cleric to make Brewing Stands".equals(line(helper, a)), "the builder's line: " + line(helper, a));
			helper.succeed();
		});
	}

	/** A Nurse at a brewing stand (her other workstation) is no Cleric: she makes no brewing stand, and the line still names the Cleric. */
	//$ gametest_ticks_batch AREA '1400' '"qaClericNurse"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaClericNurse")
	public void aNurseAtABrewingStandMakesNoneOfHis(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BREWING_STAND, "nurse"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.COBBLESTONE, 3));
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		helper.setBlock(STAND.east(), Blocks.CHEST);
		Villager nurse = helper.spawn(EntityType.VILLAGER, STAND.south());
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(STAND), PoiTypes.CLERIC, ModVillagers.NURSE);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			helper.assertTrue(nurse.getVillagerData().getProfession() == ModVillagers.NURSE, "setup: not a Nurse: " + nurse.getVillagerData().getProfession());
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BLAZE_ROD) == 1 && chest.countItem(Items.COBBLESTONE) == 3, "the Nurse took the makings");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(nurse, 0) == 0, "the Nurse made " + ModAttachments.ITEMS_CRAFTED.getOrElse(nurse, 0));
			helper.assertTrue("Hire a Cleric to make Brewing Stands".equals(line(helper, a)), "the builder's line: " + line(helper, a));
			helper.succeed();
		});
	}

	/** No Cleric, a brewing stand and an end rod wanted: one line names him for both, plural. */
	//$ gametest_ticks_batch AREA '1400' '"qaClericHireBoth"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaClericHireBoth")
	public void theHireLineNamesBothOfHisThings(GameTestHelper helper) {
		prepare(helper);
		helper.setBlock(ROW, Blocks.BREWING_STAND);
		helper.setBlock(ROW.east(), Blocks.END_ROD);
		Asker a = asker(helper, capture(helper, ROW, new Vec3i(2, 1, 1), "both"));
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			String text = line(helper, a);
			helper.assertTrue("Hire a Cleric to make Brewing Stands, End Rods".equals(text) || "Hire a Cleric to make End Rods, Brewing Stands".equals(text),
				"the builder's line: " + text);
			helper.succeed();
		});
	}

	/**
	 * The ender chest's chain: the steps are blaze powder, then the eye, then the chest; saved and loaded after the
	 * powder is made (between steps), he carries on and the chest is built.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaClericChainReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaClericChainReload")
	public void aClericLoadedBetweenStepsFinishesTheEnderChest(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.ENDER_CHEST, "chain"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.ENDER_PEARL, 1),
			new ItemStack(Items.OBSIDIAN, 8));
		AtomicReference<Villager> cleric = new AtomicReference<>(cleric(helper));
		AtomicReference<List<Item>> steps = new AtomicReference<>();
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(cleric.get());
			if (job == null || reloaded.get()) {
				return;
			}
			if (steps.get() == null) {
				steps.set(job.steps().stream().map(s -> s.out().getItem()).toList());
			}
			if (job.step() >= 1) {
				reloaded.set(true);
				Villager copy = reload(helper.getLevel(), cleric.get());
				CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(after != null && after.step() == job.step(), "the step wasn't saved: " + job.step() + " -> " + (after == null ? null : after.step()));
				helper.assertTrue(!ModAttachments.BUILDER_BAG.getOrCreate(copy).isEmpty(), "the bag was lost across the reload");
				cleric.set(copy);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(List.of(Items.BLAZE_POWDER, Items.ENDER_EYE, Items.ENDER_CHEST).equals(steps.get()), "the chain's steps: " + steps.get());
			helper.assertTrue(reloaded.get(), "never reloaded between steps");
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(cleric.get()) == null, "the finished job is still saved on the Cleric");
		});
	}

	/**
	 * His brewing stand broken while the makings are in his bag: nothing is lost. After the grace time the rod and the
	 * cobblestone are back in the builder's chest or on the ground, or the stand was made.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaClericStandBroken"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaClericStandBroken")
	public void hisStandBrokenMidJobLosesNothing(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BREWING_STAND, "broken"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.COBBLESTONE, 3));
		Villager cleric = cleric(helper);
		AtomicLong broke = new AtomicLong(-1);
		helper.onEachTick(() -> {
			if (broke.get() >= 0) {
				return;
			}
			if (ModAttachments.CRAFTER_JOB.get(cleric) != null && !ModAttachments.BUILDER_BAG.getOrCreate(cleric).isEmpty()) {
				helper.setBlock(STAND, Blocks.AIR);
				broke.set(helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broke.get() >= 0, "he never had the makings in his bag");
			helper.assertTrue(helper.getTick() > broke.get() + 700, "waiting out the grace time");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(cleric) == null, "the job is still saved on him");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(cleric).isEmpty(), "still in his bag");
			Container chest = helper.getBlockEntity(a.chest());
			int rods = chest.countItem(Items.BLAZE_ROD);
			int cobble = chest.countItem(Items.COBBLESTONE);
			int stands = chest.countItem(Items.BREWING_STAND) + (helper.getBlockState(ROW).is(Blocks.BREWING_STAND) ? 1 : 0);
			AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24);
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
				if (e.getItem().is(Items.BLAZE_ROD)) {
					rods += e.getItem().getCount();
				} else if (e.getItem().is(Items.COBBLESTONE)) {
					cobble += e.getItem().getCount();
				} else if (e.getItem().is(Items.BREWING_STAND)) {
					stands += e.getItem().getCount();
				}
			}
			helper.assertTrue((rods == 1 && cobble == 3) || stands >= 1,
				"lost: " + rods + " rods, " + cobble + " cobblestone and " + stands + " stands found");
		});
	}

	/** Over his head while he makes a stand: "Cleric · 1 made for builders", "making 1 Brewing Stand", "taking 1 Brewing Stand to the builder". */
	//$ gametest_ticks_batch AREA '2400' '"qaClericStatus"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaClericStatus")
	public void theStatusOverHisHeadWordForWord(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BREWING_STAND, "status"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.COBBLESTONE, 3));
		Villager cleric = cleric(helper);
		Set<String> titles = ConcurrentHashMap.newKeySet();
		Set<String> lines = ConcurrentHashMap.newKeySet();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(cleric, helper.getLevel().getGameTime());
			if (e != null) {
				titles.add(e.title().getString());
				lines.add(e.line().getString());
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(titles.contains("Cleric · 1 made for builders"), "titles: " + titles);
			helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("making 1 Brewing Stand")), "lines: " + lines);
			helper.assertTrue(lines.contains("taking 1 Brewing Stand to the builder"), "lines: " + lines);
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

	private static ResourceLocation single(GameTestHelper helper, Block block, String what) {
		helper.setBlock(ROW, block);
		return capture(helper, ROW, new Vec3i(1, 1, 1), what);
	}

	private static ResourceLocation capture(GameTestHelper helper, BlockPos origin, Vec3i size, String what) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qacleric_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(origin).getX());
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

	/** A Cleric at his brewing stand, with a chest by it holding {@code own}. */
	private static Villager cleric(GameTestHelper helper, ItemStack... own) {
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		helper.setBlock(STAND.east(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(STAND.east());
		for (int i = 0; i < own.length; i++) {
			chest.setItem(i, own[i].copy());
		}
		Villager cleric = helper.spawn(EntityType.VILLAGER, STAND.south());
		Jobs.employ(helper.getLevel(), cleric, helper.absolutePos(STAND), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		return cleric;
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
