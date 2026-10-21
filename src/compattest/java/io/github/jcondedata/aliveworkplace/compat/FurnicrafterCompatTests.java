package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.FurnicrafterWork;
import io.github.jcondedata.aliveworkplace.craft.FurnitureRecipes;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 38.12 with the real CobbleFurnies: the Furnicrafter. A jobless villager by CobbleFurnies' Furni Crafter takes it
 * as their job by themselves (not with {@code furnicrafters} off); the furniture recipes are read from CobbleFurnies'
 * own recipe files; a Furnicrafter makes a piece the storehouse orders from the store's materials (sticks cut from the
 * planks first), and a builder waiting for a CobbleFurnies chair gets one made from the store's planks and builds it.
 */
public class FurnicrafterCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CRAFTER = new BlockPos(8, 2, 2);
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BENCH_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STOREHOUSE = new BlockPos(2, 2, 13);
	private static final BlockPos STORE_CHEST = new BlockPos(2, 2, 15);
	private static final BlockPos CHAIR_AT = new BlockPos(11, 2, 11);
	private static final BlockPos TEMPLATE_SOURCE = new BlockPos(14, 2, 14);

	private static Block furni(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblefurnies", path));
	}

	private static Item furniItem(String path) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblefurnies", path));
	}

	private static Villager jobless(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		return villager;
	}

	/** CobbleFurnies' Furni Crafter is a job site of ours, and a jobless villager by it becomes the Furnicrafter by themselves. */
	//$ gametest_ticks_batch AREA '1800' '"furnicrafter_hire"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "furnicrafter_hire")
	public void aJoblessVillagerTakesTheFurnicrafter(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.assertTrue(ModVillagers.FURNICRAFTER != null, "no Furnicrafter profession with CobbleFurnies installed");
		helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.FURNICRAFTER).toString().equals("aliveworkplace:furnicrafter"),
			"the Furnicrafter's id is " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.FURNICRAFTER));
		PoiType poi = helper.getLevel().registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).get(ModVillagers.FURNICRAFTER_ID);
		helper.assertTrue(poi != null && poi.is(furni("furnicrafter").defaultBlockState()), "CobbleFurnies' Furni Crafter isn't a Furnicrafter's job block");
		helper.setBlock(CRAFTER, furni("furnicrafter"));
		Villager villager = jobless(helper, new BlockPos(5, 2, 5));
		helper.setDayTime(2000);
		helper.succeedWhen(() -> helper.assertTrue(ModVillagers.isFurnicrafter(villager.getVillagerData().getProfession()),
			"villager is still " + villager.getVillagerData().getProfession() + " at " + helper.relativePos(villager.blockPosition())));
	}

	/** Config {@code furnicrafters} off: a jobless villager doesn't see the Furni Crafter as a workstation and stays jobless. */
	//$ gametest_ticks_batch AREA '1200' '"furnicrafter_off"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "furnicrafter_off")
	public void withFurnicraftersOffTheFurniCrafterIsNoJob(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CRAFTER, furni("furnicrafter"));
		Villager villager = jobless(helper, new BlockPos(5, 2, 5));
		boolean before = FurnicrafterWork.ENABLED;
		FurnicrafterWork.ENABLED = false;
		PartnerShowsCompatTests.after(helper, () -> FurnicrafterWork.ENABLED = before);
		var station = level.getPoiManager().getType(helper.absolutePos(CRAFTER)).orElseThrow();
		WorkerLimits.thinking(villager);
		long seen;
		try {
			seen = WorkerLimits.hideFull(java.util.stream.Stream.of(com.mojang.datafixers.util.Pair.of(station, helper.absolutePos(CRAFTER)))).count();
		} finally {
			WorkerLimits.thinking(null);
		}
		helper.assertTrue(seen == 0, "furnicrafters off: a jobless villager still sees the Furni Crafter as a workstation");
		helper.setDayTime(2000);
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
				"furnicrafters off, and the villager took a job: " + villager.getVillagerData().getProfession());
			helper.succeed();
		});
	}

	/**
	 * Every one of CobbleFurnies' Furni Crafter recipes the server loaded is read from its file, with its materials (an Oak
	 * Chair: an oak plank and two sticks); a plan from three planks cuts the sticks first and leaves two over, and two
	 * planks aren't enough.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRecipesAreReadFromCobbleFurniesFiles(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long loaded = 0;
		for (RecipeHolder<?> holder : Recipes.all(Recipes.manager(level))) {
			ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
			if (type != null && type.toString().equals("cobblefurnies:furni_crafting")) {
				loaded++;
			}
		}
		List<FurnitureRecipes.Recipe> all = FurnitureRecipes.all(level.getServer());
		helper.assertTrue(loaded >= 300 && all.size() == loaded, all.size() + " furniture recipes read of " + loaded + " loaded");
		List<FurnitureRecipes.Recipe> chair = FurnitureRecipes.making(level.getServer(), furniItem("oak_chair"));
		helper.assertTrue(chair.size() == 1 && chair.get(0).materials().equals(Map.of(Items.OAK_PLANKS, 1, Items.STICK, 2)),
			"the Oak Chair's recipe: " + chair.stream().map(FurnitureRecipes.Recipe::materials).toList());
		Crafting.Plan plan = FurnicrafterWork.plan(level, furniItem("oak_chair"), 1, Map.of(Items.OAK_PLANKS, 3L));
		helper.assertTrue(plan != null && plan.count() == 1 && plan.takes().equals(Map.of(Items.OAK_PLANKS, 3))
			&& plan.makes().getOrDefault(Items.STICK, 0) == 2, "an Oak Chair from 3 planks: " + plan);
		helper.assertTrue(FurnicrafterWork.plan(level, furniItem("oak_chair"), 1, Map.of(Items.OAK_PLANKS, 2L)) == null,
			"an Oak Chair planned from 2 planks");
		Crafting.Plan lamps = FurnicrafterWork.plan(level, furniItem("red_lamp"), 4,
			Map.of(Items.RED_WOOL, 2L, Items.STICK, 9L, Items.REDSTONE, 9L));
		helper.assertTrue(lamps != null && lamps.count() == 2, "4 Red Lamps wanted, wool for 2: " + lamps);
		helper.assertTrue(!FurnitureRecipes.makes(level.getServer(), Items.OAK_STAIRS), "oak stairs count as furniture");
		helper.succeed();
	}

	/** A storehouse orders Oak Chairs: the Furnicrafter makes one from the three planks in its store and puts it there. */
	//$ gametest_ticks_batch AREA '2400' '"furnicrafter_store"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "furnicrafter_store")
	public void aPieceIsMadeFromTheStoresMaterials(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ownStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
		StockOrders.cycle(level, helper.absolutePos(STOREHOUSE), furniItem("oak_chair"));
		Villager crafter = furnicrafter(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(furniItem("oak_chair")) == 1, store.countItem(furniItem("oak_chair")) + " Oak Chairs in the store; planks left "
				+ store.countItem(Items.OAK_PLANKS) + ", crafter " + crafter.getVillagerData().getProfession());
			helper.assertTrue(store.countItem(Items.OAK_PLANKS) == 0 && store.countItem(Items.STICK) == 2,
				"the store has " + store.countItem(Items.OAK_PLANKS) + " planks and " + store.countItem(Items.STICK) + " sticks");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) >= 1, "nothing counted as made");
		});
	}

	/**
	 * A builder builds an Oak Chair with none in its chest: the Furnicrafter makes one from the village store's planks
	 * and takes it to the builder's chest, and the builder builds it.
	 */
	//$ gametest_ticks_batch AREA '3600' '"furnicrafter_builder"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "furnicrafter_builder")
	public void aBuilderBuildsTheFurnicraftersPiece(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ownStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		ResourceLocation chairBuild = template(helper, furni("oak_chair"), "furnicrafter_chair_" + Long.toHexString(level.getGameTime()));
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		store.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, chairBuild,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(CHAIR_AT), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the chair's build didn't start");
		Villager crafter = furnicrafter(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.status() + " " + site.stage() + " missing " + site.missing() + "; made " + ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0));
			helper.assertBlockPresent(furni("oak_chair"), CHAIR_AT);
			helper.assertTrue(store.countItem(Items.OAK_PLANKS) == 0, store.countItem(Items.OAK_PLANKS) + " planks still in the store");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) >= 1, "the chair wasn't the Furnicrafter's");
		});
	}

	/**
	 * A Furnicrafter piece for a waiting builder, from logs (37.14a: the jobs' whole chains): with only an oak log in the
	 * store, the Furnicrafter saws planks and sticks and makes the Oak Chair at its Furni Crafter, step by step (the job
	 * saved with its steps), and the builder builds it.
	 */
	//$ gametest_ticks_batch AREA '3600' '"furnicrafter_chain"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "furnicrafter_chain")
	public void theFurnicrafterMakesAPieceFromLogsForAWaitingBuilder(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ownStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		ResourceLocation chairBuild = template(helper, furni("oak_chair"), "furnicrafter_log_chair_" + Long.toHexString(level.getGameTime()));
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		store.setItem(0, new ItemStack(Items.OAK_LOG, 1));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, chairBuild,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(CHAIR_AT), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the chair's build didn't start");
		Villager crafter = furnicrafter(helper);
		java.util.concurrent.atomic.AtomicInteger steps = new java.util.concurrent.atomic.AtomicInteger();
		helper.onEachTick(() -> {
			io.github.jcondedata.aliveworkplace.craft.CrafterJob job = ModAttachments.CRAFTER_JOB.get(crafter);
			if (job != null) {
				steps.set(Math.max(steps.get(), job.steps().size()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.status() + " " + site.stage() + " missing " + site.missing() + "; made " + ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0));
			helper.assertBlockPresent(furni("oak_chair"), CHAIR_AT);
			helper.assertTrue(store.countItem(Items.OAK_LOG) == 0, store.countItem(Items.OAK_LOG) + " log still in the store");
			helper.assertTrue(steps.get() >= 2, "the Furnicrafter's saved job had " + steps.get() + " steps, not a chain from the log");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) >= 1, "the chair wasn't the Furnicrafter's");
		});
	}

	/** The Furnicrafter trades at every level: planks and wool bought, CobbleFurnies pieces sold. */
	//$ gametest_batch AREA '"furnicrafter_trades"'
	@GameTest(template = AREA, batch = "furnicrafter_trades")
	public void theFurnicrafterTrades(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		var levels = VillagerTrades.TRADES.get(ModVillagers.FURNICRAFTER);
		helper.assertTrue(levels != null, "the Furnicrafter has no trades");
		Villager villager = furnicrafter(helper);
		villager.setNoAi(true);
		List<Item> sold = new ArrayList<>();
		RandomSource random = RandomSource.create(7L);
		for (int lvl = 1; lvl <= 5; lvl++) {
			VillagerTrades.ItemListing[] listings = levels.get(lvl);
			helper.assertTrue(listings != null && listings.length > 0, "no Furnicrafter trades at level " + lvl);
			for (VillagerTrades.ItemListing listing : listings) {
				MerchantOffer offer = listing.getOffer(villager, random);
				helper.assertTrue(offer != null && !offer.getResult().isEmpty() && !offer.getCostA().isEmpty(), "a level " + lvl + " trade is empty: " + offer);
				sold.add(offer.getResult().getItem());
			}
		}
		for (String piece : List.of("oak_chair", "red_lamp", "oak_table", "white_sofa", "poke_ball_desk")) {
			helper.assertTrue(furniItem(piece) != Items.AIR && sold.contains(furniItem(piece)), "the Furnicrafter doesn't sell " + piece + ": " + sold);
		}
		helper.succeed();
	}

	/**
	 * Removes the Storehouses an earlier batch left standing beside this test's area (never cleared): a builder would
	 * fetch from their stores, and a Furnicrafter fill their orders. As the regular suite's {@code Leftovers.village}.
	 */
	static void ownStoreOnly(GameTestHelper helper) {
		net.minecraft.world.phys.AABB box = helper.getBounds();
		int reach = 48 + (int) Math.ceil(Math.max(box.getXsize(), box.getZsize()));
		for (BlockPos storehouse : helper.getLevel().getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI),
			p -> !box.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), BlockPos.containing(box.getCenter()), reach,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			helper.getLevel().removeBlock(storehouse, false);
		}
	}

	/** A Furnicrafter at a Furni Crafter by the store. */
	private static Villager furnicrafter(GameTestHelper helper) {
		helper.setBlock(CRAFTER, furni("furnicrafter"));
		Villager villager = helper.spawn(EntityType.VILLAGER, CRAFTER.south());
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(CRAFTER), ModVillagers.FURNICRAFTER);
		helper.assertTrue(FurnicrafterWork.isFurnicrafter(villager), "no Furnicrafter at the Furni Crafter");
		return villager;
	}

	/** A one-block build of {@code block}, saved as a template from the corner of the area and cleared again. */
	private static ResourceLocation template(GameTestHelper helper, Block block, String name) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(TEMPLATE_SOURCE, block);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", name);
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(TEMPLATE_SOURCE), new Vec3i(1, 1, 1), false,
			Blocks.STRUCTURE_VOID);
		helper.setBlock(TEMPLATE_SOURCE, Blocks.AIR);
		return id;
	}
}
