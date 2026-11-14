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
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (qa-1110c) of ROADMAP 46.6, the Glassblower, and the corners of 46.4, the Cleric's magic blocks, left after
 * {@code GlassblowerGameTests}, {@code ClericCraftsGameTests} and {@code QaClericCrafts1110cGameTests}. Written from the
 * spec ("a coal for every eight", stained glass with the Florist's dyes, tinted glass, "not a plain furnace") and the
 * wiki's lines ("A missing dye is named as the dye"), all through a builder's real request reaching a worker employed at
 * a real workstation, or a villager taking a workstation by himself.
 */
public class QaJobCrafts1110cGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	// ---- the Glassblower: fuel ----

	/** Exactly eight sand on one coal: eight glass, the one coal burned and the spare coal left (the boundary from below). */
	//$ gametest_ticks_batch AREA '3600' '"qaGlassEight"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaGlassEight")
	public void eightSandTakeExactlyOneCoal(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 4, 2, "eight"), new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 2));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SAND) == 0, chest.countItem(Items.SAND) + " sand left");
			helper.assertTrue(chest.countItem(Items.COAL) == 1, "coal left (8 fired, 1 burned): " + chest.countItem(Items.COAL));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	/**
	 * Nine sand and only one coal (the boundary from above): the ninth glass needs a second coal, so the build can't be
	 * finished, the builder's line names one coal short, and no sand vanishes on the way.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaGlassNineOneCoal"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaGlassNineOneCoal")
	public void nineSandOnOneCoalNamesTheSecondCoal(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS, 3, 3, "nineone"), new ItemStack(Items.SAND, 9), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1300, () -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) != null, "nine glass were built on one coal");
			Container chest = helper.getBlockEntity(a.chest());
			int placed = 0;
			for (int x = 0; x < 3; x++) {
				for (int z = 0; z < 3; z++) {
					placed += helper.getBlockState(ROW.offset(x, 0, z)).is(Blocks.GLASS) ? 1 : 0;
				}
			}
			int sandAndGlass = chest.countItem(Items.SAND) + chest.countItem(Items.GLASS) + placed
				+ bag(blower, Items.SAND) + bag(blower, Items.GLASS) + bag(a.builder(), Items.SAND) + bag(a.builder(), Items.GLASS);
			helper.assertTrue(sandAndGlass == 9, "sand and glass found: " + sandAndGlass + " of 9 (" + placed + " placed)");
			String text = line(helper, a);
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of 1 Coal to make ") && text.contains("Glass") && !text.contains("Sand"),
				"the builder's line: " + text);
			helper.succeed();
		});
	}

	// ---- the Glassblower: what he makes ----

	/** A blue stained glass pane from sand, a coal and the Florist's blue dye: glass fired, dyed and cut to panes. */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassStainedPane"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassStainedPane")
	public void theGlassblowerMakesStainedPanes(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.BLUE_STAINED_GLASS_PANE, 1, 1, "spane"), new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 1),
			new ItemStack(Items.BLUE_DYE, 1));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BLUE_DYE) == 0, "the dye wasn't used: " + chest.countItem(Items.BLUE_DYE) + " left");
			helper.assertTrue(chest.countItem(Items.SAND) < 8, "no sand was fired: " + chest.countItem(Items.SAND) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	/** Sand and coal but no red dye: nothing is taken and the line names the dye ("A missing dye is named as the dye"). */
	//$ gametest_ticks_batch AREA '1400' '"qaGlassNoDye"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaGlassNoDye")
	public void aMissingDyeIsNamedOnTheLine(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.RED_STAINED_GLASS, 1, 1, "nodye"), new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SAND) == 8 && chest.countItem(Items.COAL) == 1,
				"the makings were taken: " + chest.countItem(Items.SAND) + " sand, " + chest.countItem(Items.COAL) + " coal left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) == 0, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 1 Red Dye to make 1 Red Stained Glass".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Glass and three amethyst shards (four needed): nothing taken, the line names the one shard short word for word. */
	//$ gametest_ticks_batch AREA '1400' '"qaGlassTintedShort"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaGlassTintedShort")
	public void tintedGlassOneShardShortIsNamed(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.TINTED_GLASS, 1, 1, "tshort"), new ItemStack(Items.GLASS, 1), new ItemStack(Items.AMETHYST_SHARD, 3));
		Villager blower = glassblower(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.GLASS) == 1 && chest.countItem(Items.AMETHYST_SHARD) == 3, "the makings were taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) == 0, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 1 Amethyst Shard to make 1 Tinted Glass".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Over his head while he makes the panes: "Glassblower · 16 made" (en_us.json's title, counting the panes) and what he's making. */
	//$ gametest_ticks_batch AREA '3600' '"qaGlassStatus"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaGlassStatus")
	public void theGlassblowersStatusWordForWord(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS_PANE, 1, 1, "status"), new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper);
		Set<String> titles = ConcurrentHashMap.newKeySet();
		Set<String> lines = ConcurrentHashMap.newKeySet();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(blower, helper.getLevel().getGameTime());
			if (e != null) {
				titles.add(e.title().getString());
				lines.add(e.line().getString());
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			// the count is of things made (the sixteen panes), as the Cleric's counts his one stand
			helper.assertTrue(titles.contains("Glassblower · 16 made"), "titles: " + titles);
			helper.assertTrue(titles.stream().allMatch(t -> t.matches("Glassblower · \\d+ made")), "titles: " + titles);
			helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("making ")), "lines: " + lines);
			helper.assertTrue(titles.stream().noneMatch(t -> t.contains("aliveworkplace.")) && lines.stream().noneMatch(l -> l.contains("aliveworkplace.")),
				"an untranslated key: " + titles + " " + lines);
		});
	}

	// ---- the Glassblower: broken, saved, taken ----

	/**
	 * His Glass Kiln broken while the sand is in his bag, then put back: nothing is lost and the panes still reach the
	 * builder (the rest of the sixteen in the chest).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaGlassKilnBroken"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaGlassKilnBroken")
	public void theKilnBrokenMidJobLosesNothingAndThePanesCome(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.GLASS_PANE, 1, 1, "broken"), new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper);
		AtomicLong broke = new AtomicLong(-1);
		AtomicBoolean replaced = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (broke.get() < 0) {
				if (ModAttachments.CRAFTER_JOB.get(blower) != null && !ModAttachments.BUILDER_BAG.getOrCreate(blower).isEmpty()) {
					helper.setBlock(STATION, Blocks.AIR);
					broke.set(helper.getTick());
				}
			} else if (!replaced.get() && helper.getTick() > broke.get() + 200) {
				helper.setBlock(STATION, ModBlocks.GLASS_KILN);
				replaced.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broke.get() >= 0, "he never had the sand in his bag");
			helper.assertTrue(replaced.get(), "the kiln isn't back yet");
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.GLASS_PANE) == 15, "the rest of the panes in the chest: " + chest.countItem(Items.GLASS_PANE));
			helper.assertTrue(blower.getVillagerData().getProfession() == ModVillagers.GLASSBLOWER, "he is a " + blower.getVillagerData().getProfession());
		});
	}

	/**
	 * Red stained glass: the chain is glass first, then the stained glass; saved and loaded between the two steps, he
	 * carries on with the glass and the dye in his bag and it's built.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassStainedReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassStainedReload")
	public void aGlassblowerLoadedBetweenStepsFinishesTheStainedGlass(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.RED_STAINED_GLASS, 1, 1, "sreload"), new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 1),
			new ItemStack(Items.RED_DYE, 1));
		AtomicReference<Villager> blower = new AtomicReference<>(glassblower(helper));
		AtomicReference<List<Item>> steps = new AtomicReference<>();
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(blower.get());
			if (job == null || reloaded.get()) {
				return;
			}
			if (steps.get() == null) {
				steps.set(job.steps().stream().map(s -> s.out().getItem()).toList());
			}
			if (job.step() >= 1) {
				reloaded.set(true);
				Villager copy = reload(helper.getLevel(), blower.get());
				CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(after != null && after.step() == job.step(), "the step wasn't saved: " + job.step() + " -> " + (after == null ? null : after.step()));
				helper.assertTrue(!ModAttachments.BUILDER_BAG.getOrCreate(copy).isEmpty(), "the bag was lost across the reload");
				helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.GLASSBLOWER, "the job didn't load: " + copy.getVillagerData().getProfession());
				blower.set(copy);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(List.of(Items.GLASS, Items.RED_STAINED_GLASS).equals(steps.get()), "the chain's steps: " + steps.get());
			helper.assertTrue(reloaded.get(), "never reloaded between steps");
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.RED_STAINED_GLASS) == 7, "the rest of the stained glass: " + chest.countItem(Items.RED_STAINED_GLASS));
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(blower.get()) == null, "the finished job is still saved on him");
		});
	}

	/** A nitwit and a baby by a free Glass Kiln never take it, and a Mason at work beside it stays a Mason. */
	//$ gametest_ticks_batch AREA '1800' '"qaGlassKilnWho"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaGlassKilnWho")
	public void onlyAJoblessGrownVillagerTakesTheKiln(GameTestHelper helper) {
		prepare(helper);
		BlockPos kiln = new BlockPos(8, 2, 8);
		helper.setBlock(kiln, ModBlocks.GLASS_KILN);
		Villager nitwit = helper.spawn(EntityType.VILLAGER, kiln.north());
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Villager baby = helper.spawn(EntityType.VILLAGER, kiln.south());
		baby.setAge(-24000);
		helper.setBlock(STATION, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		helper.runAfterDelay(1500, () -> {
			helper.assertTrue(nitwit.getVillagerData().getProfession() == VillagerProfession.NITWIT, "the nitwit became a " + nitwit.getVillagerData().getProfession());
			helper.assertTrue(baby.getVillagerData().getProfession() == VillagerProfession.NONE, "the baby became a " + baby.getVillagerData().getProfession());
			helper.assertTrue(mason.getVillagerData().getProfession() == VillagerProfession.MASON, "the Mason became a " + mason.getVillagerData().getProfession());
			helper.succeed();
		});
	}

	/** The Glass Kiln broken with a pickaxe drops itself, so a player who moves it keeps it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGlassKilnDropsItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState state = ModBlocks.GLASS_KILN.defaultBlockState();
		List<ItemStack> drops = Block.getDrops(state, level, helper.absolutePos(BlockPos.ZERO), null, null, new ItemStack(Items.IRON_PICKAXE));
		helper.assertTrue(drops.size() == 1 && drops.get(0).is(ModBlocks.GLASS_KILN.asItem()) && drops.get(0).getCount() == 1, "the kiln drops " + drops);
		helper.succeed();
	}

	// ---- the Cleric ----

	/** An ender chest with a blaze rod and a pearl but seven obsidian of eight: nothing taken, the line names the one obsidian. */
	//$ gametest_ticks_batch AREA '1400' '"qaClericObsidianShort"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaClericObsidianShort")
	public void anEnderChestOneObsidianShortIsNamed(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.ENDER_CHEST, 1, 1, "obshort"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.ENDER_PEARL, 1),
			new ItemStack(Items.OBSIDIAN, 7));
		Villager cleric = cleric(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BLAZE_ROD) == 1 && chest.countItem(Items.ENDER_PEARL) == 1 && chest.countItem(Items.OBSIDIAN) == 7,
				"the makings were taken: " + chest.countItem(Items.BLAZE_ROD) + " rod, " + chest.countItem(Items.ENDER_PEARL) + " pearl, "
					+ chest.countItem(Items.OBSIDIAN) + " obsidian left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) == 0, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0));
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 1 Obsidian to make 1 Ender Chest".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Blaze powder already in the chest (no rod): he makes the eye from it and the ender chest is built; no rod is asked for. */
	//$ gametest_ticks_batch AREA '3600' '"qaClericPowder"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaClericPowder")
	public void anEnderChestFromBlazePowderNeedsNoRod(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.ENDER_CHEST, 1, 1, "powder"), new ItemStack(Items.BLAZE_POWDER, 1), new ItemStack(Items.ENDER_PEARL, 1),
			new ItemStack(Items.OBSIDIAN, 8));
		Villager cleric = cleric(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BLAZE_POWDER) == 0 && chest.countItem(Items.ENDER_PEARL) == 0 && chest.countItem(Items.OBSIDIAN) == 0,
				"the chest wasn't made from the makings: " + chest.countItem(Items.BLAZE_POWDER) + " powder left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) >= 1, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0));
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

	private static int bag(Villager v, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
	}

	private static String line(GameTestHelper helper, Asker a) {
		Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	/** A blueprint of {@code dx} x {@code dz} {@code block}s on the floor at ROW. */
	private static ResourceLocation area(GameTestHelper helper, Block block, int dx, int dz, String what) {
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				helper.setBlock(ROW.offset(x, 0, z), block);
			}
		}
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qajobcrafts_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(dx, 1, dz), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				helper.setBlock(ROW.offset(x, 0, z), Blocks.AIR);
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

	/** A Glassblower at his Glass Kiln, with a chest by it. */
	private static Villager glassblower(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.GLASS_KILN);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), ModVillagers.GLASS_KILN_POI, ModVillagers.GLASSBLOWER);
		return v;
	}

	/** A Cleric at his brewing stand, with an empty chest by it. */
	private static Villager cleric(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.BREWING_STAND);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		return v;
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
