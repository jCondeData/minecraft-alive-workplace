package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mixin.StructureTemplatePoolAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.AABB;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/** Our houses in other mods' villages (Repurposed Structures), and the Pokémon jobs' village houses (ROADMAP 28.15). */
public class VillageCompatTests implements FabricGameTest {
	/** Every Repurposed Structures village we support has our workshop and staffed houses in its house pool, in the right style. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void repurposedStructuresVillagesGetOurHouses(GameTestHelper helper) {
		var pools = helper.getLevel().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		for (var entry : VillageHouses.MODDED_HOUSE_POOLS.entrySet()) {
			StructureTemplatePool pool = pools.get(entry.getKey());
			helper.assertTrue(pool != null, "no pool " + entry.getKey() + " (did Repurposed Structures rename it?)");
			String style = entry.getValue();
			java.util.List<String> houses = new java.util.ArrayList<>(VillageHouses.houseNames());
			houses.add("builders_workshop");
			helper.assertTrue(houses.contains("leaders_hall") && houses.contains("school") && houses.contains("trade_hall") && houses.contains("ball_workshop") && houses.contains("fossil_lab")
				&& houses.containsAll(VillageHouses.POKEMON_JOB_HOUSES),
				"Cobblemon is installed: the Pokémon houses should be in: " + houses);
			for (String house : houses) {
				String id = "aliveworkplace:village/" + style + "_" + house;
				boolean found = ((StructureTemplatePoolAccessor) pool).aliveworkplace$templates().stream().anyMatch(e -> e.toString().contains(id));
				helper.assertTrue(found, id + " missing from " + entry.getKey());
			}
		}
		// Cobblemon replaces the vanilla plains house pool with its own file: ours must still be in it.
		for (String style : VillageHouses.STYLES) {
			StructureTemplatePool pool = pools.get(VillageHouses.housePool(style));
			for (String house : VillageHouses.houseNames()) {
				String id = "aliveworkplace:village/" + style + "_" + house;
				helper.assertTrue(((StructureTemplatePoolAccessor) pool).aliveworkplace$templates().stream().anyMatch(e -> e.toString().contains(id)),
					id + " missing from the vanilla " + style + " village houses (with Cobblemon's pools)");
			}
		}
		// Nether and ocean villages are left alone.
		StructureTemplatePool crimson = pools.get(ResourceLocation.fromNamespaceAndPath("repurposed_structures", "villages/crimson/houses"));
		helper.assertTrue(crimson == null || ((StructureTemplatePoolAccessor) crimson).aliveworkplace$templates().stream()
			.noneMatch(e -> e.toString().contains("aliveworkplace:")), "our houses turned up in a nether village");
		helper.succeed();
	}

	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";

	/** Config {@code pokemonVillageHouses} off: the five Pokémon jobs' houses aren't among the houses villages grow; on, they are. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pokemonVillageHousesSwitchOffLeavesThemOut(GameTestHelper helper) {
		boolean was = VillageHouses.POKEMON_JOBS;
		try {
			VillageHouses.POKEMON_JOBS = false;
			List<String> off = VillageHouses.houseNames();
			helper.assertTrue(VillageHouses.POKEMON_JOB_HOUSES.stream().noneMatch(off::contains) && off.contains("trainers_house"),
				"switched off, villages still grow: " + off);
			VillageHouses.POKEMON_JOBS = true;
			helper.assertTrue(VillageHouses.houseNames().containsAll(VillageHouses.POKEMON_JOB_HOUSES), "switched on, villages don't grow them");
		} finally {
			VillageHouses.POKEMON_JOBS = was;
		}
		helper.succeed();
	}

	/** ROADMAP 28.15: the Pokémon Center (plains) comes with its nurse, who takes its Healing Machine. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"villagePokemonCenter"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "villagePokemonCenter")
	public void aVillagePokemonCenterComesWithItsNurse(GameTestHelper helper) {
		pokemonHouse(helper, "plains_pokemon_center", ModVillagers.NURSE, null);
	}

	/** The Camp Kitchen (desert) comes with its Camp Cook at a Campfire Pot that has its pot on. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"villageCampKitchen"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "villageCampKitchen")
	public void aVillageCampKitchenComesWithItsCook(GameTestHelper helper) {
		pokemonHouse(helper, "desert_camp_kitchen", ModVillagers.CAMP_COOK, (level, job) -> {
			var entity = level.getBlockEntity(job);
			helper.assertTrue(entity != null && entity.saveWithoutMetadata(level.registryAccess()).contains("PotComponent"),
				"the Campfire Pot has no pot on it: " + (entity == null ? "no block entity" : entity.saveWithoutMetadata(level.registryAccess())));
		});
	}

	/** The Berry Nursery (savanna) comes with its Berry Breeder at the composter, her beds wet. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"villageBerryNursery"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "villageBerryNursery")
	public void aVillageBerryNurseryComesWithItsBreeder(GameTestHelper helper) {
		pokemonHouse(helper, "savanna_berry_nursery", ModVillagers.BERRY_BREEDER, (level, job) -> {
			int beds = 0;
			for (BlockPos p : BlockPos.betweenClosed(job.offset(-4, -1, -4), job.offset(4, -1, 1))) {
				if (level.getBlockState(p).is(Blocks.FARMLAND)) {
					beds++;
					helper.assertTrue(level.getBlockState(p).getValue(net.minecraft.world.level.block.FarmBlock.MOISTURE) == 7, "a dry bed at " + p);
				}
			}
			helper.assertTrue(beds == 6, beds + " farmland beds, expected 6");
		});
	}

	/** The Daycare (snowy) comes with its Daycare Keeper at the Pasture Block. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"villageDaycare"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "villageDaycare")
	public void aVillageDaycareComesWithItsKeeper(GameTestHelper helper) {
		pokemonHouse(helper, "snowy_daycare", ModVillagers.DAYCARE_KEEPER, null);
	}

	/** The Gem Grotto (taiga) comes with its Gem Grower at the stonecutter; its heat is magma behind glass, no lava. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"villageGemGrotto"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "villageGemGrotto")
	public void aVillageGemGrottoComesWithItsGrower(GameTestHelper helper) {
		pokemonHouse(helper, "taiga_gem_grotto", ModVillagers.GEM_GROWER, (level, job) -> {
			int magma = 0;
			int budding = 0;
			for (BlockPos p : BlockPos.betweenClosed(job.offset(-3, -1, -3), job.offset(6, 4, 6))) {
				helper.assertFalse(level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA), "lava in the house at " + p);
				magma += level.getBlockState(p).is(Blocks.MAGMA_BLOCK) ? 1 : 0;
				budding += level.getBlockState(p).is(Blocks.BUDDING_AMETHYST) ? 1 : 0;
			}
			helper.assertTrue(magma == 2 && budding == 1, magma + " magma blocks and " + budding + " budding amethyst");
		});
	}

	/**
	 * Places the village house {@code name} as a village does (the legacy pool element with the empty processor list,
	 * its villager brought along) and waits for its one villager to have {@code job}, work at the house's one job block
	 * and, after 100 ticks at least, stand in open space inside the house, unhurt (B6). {@code more} checks the house's own.
	 */
	private static void pokemonHouse(GameTestHelper helper, String name, VillagerProfession job,
			java.util.function.BiConsumer<ServerLevel, BlockPos> more) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
		AABB area = new AABB(origin).inflate(12);
		level.getEntitiesOfClass(Villager.class, area).forEach(Villager::discard); // a neighbouring test's villager
		helper.setDayTime(2000);
		var empty = level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(
			ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty")));
		BoundingBox box = BoundingBox.fromCorners(origin.offset(-1, 0, -1), origin.offset(10, 11, 11));
		var element = StructurePoolElement.legacy(AliveWorkplace.id("village/" + name).toString(), empty).apply(StructureTemplatePool.Projection.RIGID);
		helper.assertTrue(element.place(level.getStructureManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
			origin, origin, Rotation.NONE, box, RandomSource.create(1), LiquidSettings.APPLY_WATERLOGGING, false), name + " did not place");
		List<BlockPos> sites = new java.util.ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (PoiTypes.forState(level.getBlockState(p)).filter(h -> BuiltInRegistries.VILLAGER_PROFESSION.stream()
					.anyMatch(prof -> prof != VillagerProfession.NONE && prof.heldJobSite().test(h))).isPresent()) {
				sites.add(p.immutable());
			}
		}
		helper.assertTrue(sites.size() == 1, name + " has " + sites.size() + " job blocks: " + sites);
		BlockPos site = sites.get(0);
		helper.assertTrue(level.getPoiManager().getType(site).map(job.heldJobSite()::test).orElse(false),
			"a " + job + " can't work at " + name + "'s " + level.getBlockState(site));
		if (more != null) {
			more.accept(level, site);
		}
		// Its chest has the house's loot, a table that loads (only with Cobblemon: its items are Cobblemon's)
		int chests = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
				chests++;
				var table = chest.getLootTable();
				helper.assertTrue(table != null && level.getServer().reloadableRegistries().getLootTable(table) != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
					name + "'s chest has no loot table that loads: " + table);
			}
		}
		helper.assertTrue(chests == (name.endsWith("pokemon_center") ? 0 : 1), name + " has " + chests + " chests");
		int[] ticks = {0};
		helper.onEachTick(() -> ticks[0]++);
		helper.succeedWhen(() -> {
			List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
			helper.assertTrue(villagers.size() == 1, villagers.size() + " villagers by the house");
			Villager worker = villagers.get(0);
			helper.assertTrue(worker.getVillagerData().getProfession() == job, "a " + worker.getVillagerData().getProfession() + ", not a " + job);
			helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(site)).orElse(false),
				"works at " + worker.getBrain().getMemory(MemoryModuleType.JOB_SITE) + ", not the house's " + site);
			helper.assertTrue(!worker.getTags().contains(io.github.jcondedata.aliveworkplace.work.Stations.HOUSE_WORKER_TAG),
				"the " + job + " still carries the house worker tag with a job site");
			helper.assertTrue(ticks[0] >= 100, "settling");
			BlockPos at = worker.blockPosition();
			helper.assertTrue(worker.isAlive() && !worker.isInWall() && worker.getHealth() == worker.getMaxHealth(),
				String.format("the %s is stuck or hurt at %s (health %.1f)", job, at.subtract(origin).toShortString(), worker.getHealth()));
			// Open space: nothing the villager's body overlaps (standing on a chest or a hay bale of the fit-out is fine)
			helper.assertTrue(level.noCollision(worker, worker.getBoundingBox().deflate(1.0E-4)),
				"the " + job + " overlaps a block at " + at.subtract(origin).toShortString() + ": " + level.getBlockState(at) + " / " + level.getBlockState(at.above()));
			helper.assertTrue(box.isInside(at) && at.getY() <= origin.getY() + 2, "the " + job + " left the house: " + at.subtract(origin).toShortString());
		});
	}
}
