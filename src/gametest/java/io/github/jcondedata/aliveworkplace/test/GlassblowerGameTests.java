package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 46.6, the Glassblower: a new job at his own Glass Kiln, who fires glass from sand (a coal or charcoal for every
 * eight) and makes the builders' panes, stained glass with the Florist's dyes, tinted glass and bottles. Through a
 * builder's real request (or a Storehouse stock order for bottles, which no builder asks for): panes from sand; nine glass
 * from sand on two coal; red stained glass from sand and a red dye; tinted glass; bottles; with no coal the builder's line
 * names it word for word; with no Glassblower the line says to hire one; saved and loaded mid-job he carries on. A jobless
 * villager by a plain furnace never becomes one (a player's furnace is never his), and by a free Glass Kiln does. The
 * kiln's recipe and creative tab, and the Glassblower's Workshop in the Blueprint Table and the Steward's rules.
 */
public class GlassblowerGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	private static final BlockPos STOREHOUSE = new BlockPos(12, 2, 12);
	private static final BlockPos STORE_CHEST = new BlockPos(12, 2, 14);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** His list is his own, no one's stand-in: glass, panes, stained glass, tinted glass and bottles; the Mason fires none of it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGlassblowersListIsHisOwn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CraftJobs.Job.GLASSBLOWER.craftsNow(), "the Glassblower still has a stand-in");
		helper.assertTrue(CraftJobs.Job.GLASSBLOWER.profession() == ModVillagers.GLASSBLOWER, "the table's Glassblower isn't the job");
		for (Item item : List.of(Items.GLASS, Items.GLASS_PANE, Items.RED_STAINED_GLASS, Items.BLUE_STAINED_GLASS_PANE, Items.TINTED_GLASS,
				Items.GLASS_BOTTLE)) {
			helper.assertTrue(CraftJobs.maker(level, item) == CraftJobs.Job.GLASSBLOWER, item + " is made by " + CraftJobs.maker(level, item));
			helper.assertFalse(CraftJobs.makes(level, CraftJobs.Job.MASON, item), "the Mason still makes " + item);
		}
		helper.assertTrue(ModVillagers.isWorker(ModVillagers.GLASSBLOWER), "the Glassblower doesn't get the long shift");
		helper.succeed();
	}

	/** A glass pane for a builder from six sand and a coal in his chest: the glass fired first, then the panes, taken to him. */
	//$ gametest_ticks_batch AREA '3600' '"glassblowerPanes"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "glassblowerPanes")
	public void theGlassblowerMakesPanesFromSand(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS_PANE, 1, "panes"), new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SAND) == 0 && chest.countItem(Items.COAL) == 0,
				"the panes weren't made from the sand and coal: " + chest.countItem(Items.SAND) + " sand, " + chest.countItem(Items.COAL) + " coal left");
			helper.assertTrue(chest.countItem(Items.GLASS_PANE) == 15, "the rest of the panes in the chest: " + chest.countItem(Items.GLASS_PANE));
		});
	}

	/** Nine glass blocks from nine sand take two coal (one for every eight fired), not one and not nine: the third is left. */
	//$ gametest_ticks_batch AREA '3600' '"glassblowerCoal"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "glassblowerCoal")
	public void glassTakesACoalForEveryEight(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS, 9, "coal"), new ItemStack(Items.SAND, 9), new ItemStack(Items.COAL, 3));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SAND) == 0, chest.countItem(Items.SAND) + " sand left");
			helper.assertTrue(chest.countItem(Items.COAL) == 1, "coal left (9 fired, 2 burned): " + chest.countItem(Items.COAL));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	/** Red stained glass from eight sand, a coal and the Florist's red dye: the glass fired, then dyed. */
	//$ gametest_ticks_batch AREA '3600' '"glassblowerStained"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "glassblowerStained")
	public void theGlassblowerMakesStainedGlassWithADye(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.RED_STAINED_GLASS, 1, "stained"), new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 1),
			new ItemStack(Items.RED_DYE, 1));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.RED_DYE) == 0 && chest.countItem(Items.SAND) == 0,
				"the stained glass wasn't made from the makings: " + chest.countItem(Items.RED_DYE) + " dye, " + chest.countItem(Items.SAND) + " sand left");
			helper.assertTrue(chest.countItem(Items.RED_STAINED_GLASS) == 7, "the rest of the stained glass: " + chest.countItem(Items.RED_STAINED_GLASS));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	/** Tinted glass from a glass block and four amethyst shards. */
	//$ gametest_ticks_batch AREA '2400' '"glassblowerTinted"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "glassblowerTinted")
	public void theGlassblowerMakesTintedGlass(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.TINTED_GLASS, 1, "tinted"), new ItemStack(Items.GLASS, 1), new ItemStack(Items.AMETHYST_SHARD, 4));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.AMETHYST_SHARD) == 0 && chest.countItem(Items.GLASS) == 0,
				"the tinted glass wasn't made from the makings: " + chest.countItem(Items.AMETHYST_SHARD) + " shards left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	/** Glass bottles for the Storehouse's stock order, blown from sand fired on the store's coal. */
	//$ gametest_ticks_batch AREA '3600' '"glassblowerBottles"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "glassblowerBottles")
	public void theGlassblowerBlowsBottlesForAStockOrder(GameTestHelper helper) {
		prepare(helper);
		Container store = store(helper, new ItemStack(Items.SAND, 3), new ItemStack(Items.COAL, 1));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.GLASS_BOTTLE);
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(Items.GLASS_BOTTLE) >= 3, store.countItem(Items.GLASS_BOTTLE) + " bottles in the store");
			helper.assertTrue(store.countItem(Items.SAND) == 0, store.countItem(Items.SAND) + " sand left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	/** Sand but no coal or charcoal: he takes nothing, makes nothing, and the builder's line names the coal word for word. */
	//$ gametest_ticks_batch AREA '1400' '"glassblowerNoFuel"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "glassblowerNoFuel")
	public void noFuelIsNamedOnTheShortageLine(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS_PANE, 1, "nofuel"), new ItemStack(Items.SAND, 6));
		Villager blower = glassblower(helper);
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SAND) == 6, "the sand was taken: " + chest.countItem(Items.SAND) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) == 0, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 1 Coal to make 1 Glass Pane".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** No Glassblower anywhere near: the sand and coal stay in the chest and the line says whom to hire. */
	//$ gametest_ticks_batch AREA '1400' '"glassblowerAbsent"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "glassblowerAbsent")
	public void withNoGlassblowerTheLineSaysHireOne(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS_PANE, 1, "absent"), new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1));
		// a Mason at work by the builder fires none of it any more
		helper.setBlock(STATION, Blocks.STONECUTTER);
		helper.setBlock(STATION.east(), Blocks.FURNACE);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SAND) == 6 && chest.countItem(Items.COAL) == 1, "the sand or coal was taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) == 0, "the Mason made " + ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0));
			String text = line(helper, a);
			helper.assertTrue("Hire a Glassblower to make Glass Panes".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Saved and loaded just as he starts making the panes, the Glassblower carries on with the makings in his bag. */
	//$ gametest_ticks_batch AREA '3600' '"glassblowerReload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "glassblowerReload")
	public void aGlassblowerLoadedMidJobFinishesThePanes(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS_PANE, 1, "reload"), new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1));
		AtomicReference<Villager> blower = new AtomicReference<>(glassblower(helper));
		AtomicReference<CrafterJob> saved = new AtomicReference<>();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(blower.get());
			if (saved.get() != null || job == null || job.phase() != CrafterJob.Phase.CRAFTING) {
				return;
			}
			saved.set(job);
			Villager copy = reload(helper.getLevel(), blower.get());
			helper.assertTrue(!ModAttachments.BUILDER_BAG.getOrCreate(copy).isEmpty(), "the makings should be in the bag across the reload");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(copy) != null, "the job wasn't saved on the Glassblower");
			helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.GLASSBLOWER, "the job didn't load: " + copy.getVillagerData().getProfession());
			blower.set(copy);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(saved.get() != null, "the Glassblower never started making the panes");
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(blower.get()) == null, "the finished job is still saved on the Glassblower");
		});
	}

	/** A jobless villager by a plain furnace (a player's), with no Glass Kiln about, never takes it and stays jobless. */
	//$ gametest_ticks_batch AREA '1800' '"glassblowerFurnace"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "glassblowerFurnace")
	public void aPlainFurnaceIsNeverHisWorkstation(GameTestHelper helper) {
		prepare(helper);
		helper.assertTrue(PoiTypes.forState(Blocks.FURNACE.defaultBlockState()).isEmpty(), "a furnace is a workstation");
		BlockPos furnace = new BlockPos(3, 2, 3);
		helper.setBlock(furnace, Blocks.FURNACE);
		Villager byFurnace = helper.spawn(EntityType.VILLAGER, furnace.south());
		helper.runAfterDelay(1500, () -> {
			helper.assertTrue(byFurnace.getVillagerData().getProfession() == VillagerProfession.NONE,
				"the villager by the furnace became a " + byFurnace.getVillagerData().getProfession());
			helper.succeed();
		});
	}

	/** A jobless villager by a free Glass Kiln takes it by himself and becomes a Glassblower. */
	//$ gametest_ticks_batch AREA '1800' '"glassblowerKilnHire"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "glassblowerKilnHire")
	public void aJoblessVillagerTakesAFreeGlassKiln(GameTestHelper helper) {
		prepare(helper);
		helper.assertTrue(PoiTypes.forState(ModBlocks.GLASS_KILN.defaultBlockState()).map(h -> h.is(ModVillagers.GLASS_KILN_POI)).orElse(false),
			"the Glass Kiln isn't his workstation");
		BlockPos kiln = new BlockPos(8, 2, 8);
		helper.setBlock(kiln, ModBlocks.GLASS_KILN);
		Villager byKiln = helper.spawn(EntityType.VILLAGER, kiln.north());
		helper.succeedWhen(() -> helper.assertTrue(byKiln.getVillagerData().getProfession() == ModVillagers.GLASSBLOWER,
			"the villager by the Glass Kiln is a " + byKiln.getVillagerData().getProfession()));
	}

	/** The Glass Kiln: crafted from bricks, a furnace and an iron ingot, named, and shown in the mod's creative tab. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGlassKilnHasARecipeAndIsInTheCreativeTab(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item kiln = ModBlocks.GLASS_KILN.asItem();
		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(ModBlocks.GLASS_KILN).equals(AliveWorkplace.id("glass_kiln")), "the kiln's id");
		RecipeHolder<?> recipe = Recipes.manager(level).byKey(AliveWorkplace.id("glass_kiln")).orElse(null);
		helper.assertTrue(recipe != null, "no Glass Kiln recipe");
		helper.assertTrue(Recipes.result(recipe.value(), level.registryAccess()).is(kiln), "the recipe makes " + Recipes.result(recipe.value(), level.registryAccess()));
		List<Item> makings = Recipes.ingredients(recipe.value()).stream().map(Recipes::options).filter(o -> o.length > 0).map(o -> o[0].getItem()).toList();
		helper.assertTrue(makings.contains(Items.BRICKS) && makings.contains(Items.FURNACE) && makings.contains(Items.IRON_INGOT),
			"the recipe's makings: " + makings);
		helper.assertTrue("Glass Kiln".equals(ModBlocks.GLASS_KILN.getName().getString()), "named " + ModBlocks.GLASS_KILN.getName().getString());
		helper.assertTrue("Glassblower".equals(io.github.jcondedata.aliveworkplace.work.Stations.name(ModVillagers.GLASSBLOWER).getString()),
			"the job is named " + io.github.jcondedata.aliveworkplace.work.Stations.name(ModVillagers.GLASSBLOWER).getString());
		CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess());
		boolean shown = false;
		for (Map.Entry<net.minecraft.resources.ResourceKey<CreativeModeTab>, CreativeModeTab> e : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
			if (e.getKey().location().getNamespace().equals(AliveWorkplace.MOD_ID)) {
				e.getValue().buildContents(params);
				shown |= e.getValue().getDisplayItems().stream().anyMatch(s -> s.is(kiln));
			}
		}
		helper.assertTrue(shown, "the Glass Kiln isn't in the mod's creative tab");
		helper.succeed();
	}

	/**
	 * The Glassblower's Workshop: in the Blueprint Table, named, the size its entry says, the Glass Kiln in it once and no
	 * furnace, a workshop on the Village Map, and in the Steward's rules for a village with no Glassblower.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGlassblowersWorkshopIsOfferedAndPlanned(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry e = StarterBlueprints.GLASSBLOWERS_WORKSHOP;
		helper.assertTrue(BlueprintLibrary.list(level.getServer(), false).contains(e.id()), e.id() + " isn't in the Blueprint Table");
		helper.assertTrue(StarterBlueprints.ONE_TIER.contains(e), e.id() + " isn't a starter blueprint");
		helper.assertTrue(Language.getInstance().has("blueprint.aliveworkplace." + e.id().getPath()), "no name for " + e.id());
		Blueprint b = BlueprintLibrary.get(level, e.id()).orElseThrow();
		helper.assertTrue(b.size().equals(e.size()), e.id() + " is " + b.size());
		helper.assertTrue(count(b, ModBlocks.GLASS_KILN) == 1, "Glass Kilns in it: " + count(b, ModBlocks.GLASS_KILN));
		helper.assertTrue(count(b, Blocks.FURNACE) == 0, "a furnace in it");
		helper.assertTrue(VillageMaps.kindOf(e.id()).orElse(null) == VillageMaps.Kind.WORKSHOPS, "not a workshop on the map");
		long rules = StewardRules.all().stream().filter(r -> r.effect().blueprint().map(e.id()::equals).orElse(false)).count();
		helper.assertTrue(rules >= 2, "the Steward's rules that build it: " + rules);
		helper.succeed();
	}

	// ---- helpers ----

	private static long count(Blueprint b, Block block) {
		return b.blocks().stream().filter(info -> info.state().is(block)).count();
	}

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

	/** A blueprint of {@code n} (1 or 9) {@code block}s on the floor: one, or a 3 x 3 square. */
	private static ResourceLocation blueprint(GameTestHelper helper, Block block, int n, String what) {
		int side = n == 1 ? 1 : 3;
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				helper.setBlock(ROW.offset(x, 0, z), block);
			}
		}
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"glassblower_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(side, 1, side), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				helper.setBlock(ROW.offset(x, 0, z), Blocks.AIR);
			}
		}
		return id;
	}

	/** A Storehouse and its chest holding {@code items}, its stock orders reaching this test's workers. */
	private static Container store(GameTestHelper helper, ItemStack... items) {
		Leftovers.halls(helper);
		int range = StockOrders.RANGE;
		StockOrders.RANGE = 16;
		Leftovers.after(helper, () -> StockOrders.RANGE = range);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < items.length; i++) {
			store.setItem(i, items[i].copy());
		}
		return store;
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
