package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.habitat.VillageHabitats;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * ROADMAP 28.14 with the real Cobblemon: an Expert Habitat Keeper founds the village's own Habitat Block under the
 * finished Habitat Garden's centre stone (Cobblemon 1.8.1), and nothing on 1.7.3, where her page says why.
 */
public class VillageHabitatCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** The garden's mossy centre stone. */
	private static final BlockPos CENTRE = new BlockPos(6, 2, 6);
	private static final BlockPos HALL = new BlockPos(1, 2, 8);

	/** A finished Habitat Garden whose centre stone is {@link #CENTRE}, remembered as the builders remember one. */
	private static BlueprintData.Placement garden(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(CENTRE).subtract(StarterBlueprints.HABITAT_GARDEN_CENTRE);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		helper.setBlock(CENTRE, Blocks.MOSS_BLOCK);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.HABITAT_GARDEN.id(), placement, UUID.randomUUID());
		PartnerShowsCompatTests.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return placement;
	}

	private static Villager expert(GameTestHelper helper) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setVillagerData(keeper.getVillagerData().setLevel(VillageHabitats.EXPERT));
		return keeper;
	}

	/**
	 * The Done when: on 1.8.1 the Expert keeper places one natural-mode Habitat Block for the biome's pool, mimicking
	 * the moss it replaces, kept on the hall; the hall's list then shows its phase today. On 1.7.3 nothing is placed
	 * and her page says the village habitat needs Cobblemon 1.8.
	 */
	//$ gametest_ticks_batch AREA '600' '"village_habitat"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "village_habitat")
	public void anExpertKeeperFoundsTheVillageHabitat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		garden(helper);
		Villager keeper = expert(helper);
		BlockPos centre = helper.absolutePos(CENTRE);
		// (a Habitat Block left standing would send later batches' keepers on their daily round to it)
		PartnerShowsCompatTests.after(helper, () -> {
			level.removeBlockEntity(centre);
			level.setBlock(centre, Blocks.AIR.defaultBlockState(), 3);
		});
		if (!PokemonFeatures.HABITATS.available()) {
			helper.runAfterDelay(300, () -> {
				helper.assertTrue(level.getBlockState(centre).is(Blocks.MOSS_BLOCK), "Cobblemon 1.7: the centre stone became " + level.getBlockState(centre));
				List<Component> lines = VillageHabitats.hallLines(keeper);
				helper.assertTrue(lines.size() == 1 && lines.get(0).getString().contains("Cobblemon 1.8"), "her page says " + lines);
				helper.succeed();
			});
			return;
		}
		ResourceLocation pool = VillageHabitats.poolFor(level, centre);
		helper.assertTrue(pool != null, "no village_habitats file for the biome here");
		helper.succeedWhen(() -> {
			helper.assertTrue(VillageHabitats.isNatural(level.getBlockState(centre)), "no natural Habitat Block yet: " + level.getBlockState(centre));
			CompoundTag settings = VillageHabitats.settings(level, centre);
			helper.assertTrue(settings.getString("PoolId").equals(pool.toString()), "its pool is " + settings.getString("PoolId") + ", not " + pool);
			helper.assertTrue(settings.getString("MimicId").equals("minecraft:moss_block"), "it mimics " + settings.getString("MimicId"));
			helper.assertTrue(VillageHabitats.NATURAL.equals(settings.getString("SpawningStyle")), "its style is " + settings.getString("SpawningStyle"));
			VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
			helper.assertTrue(centre.equals(hall.habitat()), "the hall keeps " + hall.habitat());
			helper.assertTrue(VillageHabitats.tended(level, helper.absolutePos(new BlockPos(2, 2, 2))).contains(centre), "not among the tended habitats");
			String line = VillageHabitats.todayLine(level, centre).getString();
			helper.assertTrue(line.contains("today:") && !line.contains("cobblemon.species"), "the hall line reads " + line);
			helper.assertTrue(VillageHabitats.foundingSpot(level, keeper, helper.absolutePos(new BlockPos(2, 2, 2))) == null, "a second habitat would be founded");
		});
	}

	/** The pool files: every biome named in 28.14 has its pool, and anywhere else is the Zen Garden. */
	//$ gametest 'AREA'
	@GameTest(template = AREA)
	public void everyBiomeHasItsPool(GameTestHelper helper) {
		var files = VillageHabitats.files(helper.getLevel().getServer().getResourceManager());
		helper.assertTrue(files.size() == 20, "expected 20 village_habitats files, found " + files.size() + ": " + files.keySet());
		long fallbacks = files.values().stream().filter(f -> !f.has("biomes")).count();
		helper.assertTrue(fallbacks == 1, fallbacks + " files without biomes");
		if (PokemonFeatures.HABITATS.available()) {
			for (var e : files.entrySet()) {
				ResourceLocation pool = ResourceLocation.parse(e.getValue().get("pool").getAsString());
				helper.assertTrue(!VillageHabitats.species(helper.getLevel().getServer().getResourceManager(), pool, 0).isEmpty(),
					e.getKey() + ": Cobblemon has no pool " + pool);
			}
		}
		helper.succeed();
	}

	/**
	 * The Done when's forced spawn round: Cobblemon 1.8.1's own spawner runs over a zone round the village's Habitat
	 * Block (what {@code /forcespawn} does round a player, the zone placed on the block) and brings a Pokémon from the
	 * block's pool within its range. Cobblemon 1.8's spawning classes are reached by reflection, with the signatures
	 * read from the 1.8.1 jar: {@code PlayerSpawnerAccessor.getPlayerSpawner()}, {@code SpawnCause(Spawner, Entity)},
	 * {@code SpawningZoneInput(SpawnCause, ServerLevel, baseX, baseY, baseZ, length, height, width)} and
	 * {@code BasicSpawner.runForArea(SpawningZoneInput, Integer)}; the mod compiles against 1.7.3, which has none of them.
	 */
	//$ gametest_ticks_batch AREA '400' '"village_habitat_spawn"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "village_habitat_spawn")
	public void aForcedSpawnRoundBringsAPokemonFromThePool(GameTestHelper helper) {
		if (!PokemonFeatures.HABITATS.available()) {
			helper.succeed();
			return;
		}
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		garden(helper);
		Villager keeper = expert(helper);
		BlockPos centre = helper.absolutePos(CENTRE);
		PartnerShowsCompatTests.after(helper, () -> {
			level.removeBlockEntity(centre);
			level.setBlock(centre, Blocks.AIR.defaultBlockState(), 3);
			PartnerShowsCompatTests.clearLeftovers(helper);
		});
		helper.assertTrue(VillageHabitats.found(level, keeper, centre), "couldn't found it");
		ResourceLocation pool = ResourceLocation.parse(VillageHabitats.settings(level, centre).getString("PoolId"));
		List<String> species = VillageHabitats.species(level.getServer().getResourceManager(), pool, 0);
		helper.assertTrue(!species.isEmpty(), "no species in " + pool);
		keeper.discard();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 6.5)));
		Object[] spawn = new Object[3];
		try {
			Object spawner = player.getClass().getMethod("getPlayerSpawner").invoke(player);
			Class<?> spawnerType = Class.forName("com.cobblemon.mod.common.api.spawning.spawner.Spawner");
			Class<?> causeType = Class.forName("com.cobblemon.mod.common.api.spawning.SpawnCause");
			Class<?> inputType = Class.forName("com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput");
			Object cause = causeType.getConstructor(spawnerType, Entity.class).newInstance(spawner, player);
			// a 9 x 5 x 9 zone with the Habitat Block in its middle: every spot in it is within its 12-block range
			spawn[0] = spawner;
			// the mock player's own ticking spawner would fill the 48 blocks round it with ordinary wild Pokémon
			spawner.getClass().getMethod("setActive", boolean.class).invoke(spawner, false);
			spawn[1] = inputType.getConstructor(causeType, ServerLevel.class, int.class, int.class, int.class, int.class, int.class, int.class)
				.newInstance(cause, level, centre.getX() - 4, centre.getY() - 1, centre.getZ() - 4, 9, 5, 9);
			spawn[2] = spawner.getClass().getMethod("runForArea", inputType, Integer.class);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Cobblemon 1.8.1's spawner isn't what its jar showed: " + e, e);
		}
		// Cobblemon spawns only where the entities 48 blocks round the zone are loaded, as round a real player; a mock
		// player loads no chunks, so the test keeps them loaded. They stay loaded to the end of the run: unloading
		// them again as the test ended once hung the test server in ChunkMap.processUnloads.
		ChunkPos middle = new ChunkPos(centre);
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				level.setChunkForced(middle.x + dx, middle.z + dz, true);
			}
		}
		int[] rounds = {0};
		List<String> others = new java.util.ArrayList<>();
		helper.succeedWhen(() -> {
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					helper.assertTrue(level.areEntitiesLoaded(ChunkPos.asLong(middle.x + dx, middle.z + dz)), "waiting for the chunks round the zone to load");
				}
			}
			// Cobblemon spawns nothing while 9 chunks' worth of Pokémon stand within 48 blocks (pokemonPerChunk), and the
			// chunks just loaded bring back earlier tests' Pokémon: every Pokémon there but the pool's goes (and is noted).
			List<PokemonEntity> near = new java.util.ArrayList<>();
			for (PokemonEntity e : level.getEntitiesOfClass(PokemonEntity.class, new AABB(centre).inflate(64, 1000, 64))) {
				String name = e.getPokemon().getSpecies().getResourceIdentifier().getPath();
				if (species.contains(name) && e.distanceToSqr(centre.getCenter()) <= VillageHabitats.RANGE_OF_INFLUENCE * VillageHabitats.RANGE_OF_INFLUENCE) {
					near.add(e);
				} else {
					if (rounds[0] > 0 && e.distanceToSqr(centre.getCenter()) <= 16 * 16) {
						others.add(name);
					}
					e.discard();
				}
			}
			// a round may pick a bucket with nobody in it this phase and hour: one round a tick, as the ticking spawner would
			if (near.isEmpty() && rounds[0] < 40) {
				try {
					((Method) spawn[2]).invoke(spawn[0], spawn[1], 1);
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException("Cobblemon 1.8.1's runForArea failed: " + (e.getCause() == null ? e : e.getCause()), e);
				}
				rounds[0]++;
			}
			helper.assertTrue(!near.isEmpty(), "no Pokémon from " + pool + " within range after " + rounds[0] + " rounds (others there: " + others + ")"
				+ (rounds[0] >= 40 ? "; " + diagnose(spawn) + habitatDiag(level.getBlockEntity(centre), spawn[0]) : ""));
		});
	}

	static String diagnose(Object[] spawn) {
		StringBuilder out = new StringBuilder();
		try {
			Object spawner = spawn[0];
			Object input = spawn[1];
			Object cobblemon = Class.forName("com.cobblemon.mod.common.Cobblemon").getField("INSTANCE").get(null);
			Object config = cobblemon.getClass().getMethod("getConfig").invoke(cobblemon);
			out.append("perChunk=").append(config.getClass().getMethod("getPokemonPerChunk").invoke(config));
			out.append(" maxPerChunk=").append(spawner.getClass().getMethod("getMaxPokemonPerChunk").invoke(spawner));
			Object world = input.getClass().getMethod("getWorld").invoke(input);
			net.minecraft.world.phys.Vec3 c = (net.minecraft.world.phys.Vec3) input.getClass().getMethod("getCenter").invoke(input);
			out.append(" nearby=").append(((ServerLevel) world).getEntitiesOfClass(PokemonEntity.class, AABB.ofSize(c, 96, 2000, 96)).size());
			Object constrained = find(spawner, "constrainArea", 1).invoke(spawner, input);
			out.append(" constrained=").append(constrained != null);
			Object generator = spawner.getClass().getMethod("getGenerator").invoke(spawner);
			Object zone = find(generator, "generate", 2).invoke(generator, spawner, input);
			Object resolver = spawner.getClass().getMethod("getResolver").invoke(spawner);
			Object companion = Class.forName("com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator").getField("Companion").get(null);
			Object calcs = companion.getClass().getMethod("getPrioritizedAreaCalculators").invoke(companion);
			List<?> positions = (List<?>) find(resolver, "resolve", 3).invoke(resolver, spawner, calcs, zone);
			out.append(" positions=").append(positions.size());
			if (!positions.isEmpty()) {
				out.append(" first=").append(positions.get(0).getClass().getSimpleName());
			}
			Object influences = spawner.getClass().getMethod("getInfluences").invoke(spawner);
			out.append(" influences=").append(influences);
			out.append(" unconditional=").append(zone.getClass().getMethod("getUnconditionalInfluences").invoke(zone));
		} catch (Throwable e) {
			out.append(" diag failed: ").append(e.getCause() == null ? e : e.getCause());
		}
		return out.toString();
	}

	static String habitatDiag(Object be, Object spawner) {
		StringBuilder out = new StringBuilder(" be=" + (be == null ? null : be.getClass().getSimpleName()));
		try {
			out.append(" init=").append(be.getClass().getMethod("getInitialized").invoke(be));
			Object style = be.getClass().getMethod("getSpawningStyle").invoke(be);
			out.append(" style=").append(style == null ? null : style.getClass().getSimpleName());
			out.append(" overworld=").append(style.getClass().getMethod("getAffectsOverworld").invoke(style));
			out.append(" replace=").append(style.getClass().getMethod("getReplaceSpawns").invoke(style));
			out.append(" details=").append(((List<?>) be.getClass().getMethod("getSpawnDetails").invoke(be)).size());
			Object pool = be.getClass().getMethod("getPool").invoke(be);
			out.append(" pool=").append(pool.getClass().getMethod("getId").invoke(pool));
			out.append(" poolSpawns=").append(((List<?>) pool.getClass().getMethod("getSpawns").invoke(pool)).size());
			Object pools = Class.forName("com.cobblemon.mod.common.api.habitats.HabitatPools").getField("INSTANCE").get(null);
			out.append(" registry=").append(((java.util.Map<?, ?>) pools.getClass().getMethod("getHabitatPoolsById").invoke(pools)).size());
			out.append(" range=").append(find(be, "getInfluentialRange", 1).invoke(be, spawner));
		} catch (Throwable e) {
			out.append(" diag failed: ").append(e.getCause() == null ? e : e.getCause());
		}
		return out.toString();
	}

	private static Method find(Object on, String name, int params) throws NoSuchMethodException {
		for (Method m : on.getClass().getMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == params) {
				return m;
			}
		}
		throw new NoSuchMethodException(on.getClass() + "." + name);
	}

	/** Taking the garden down removes its Habitat Block, with no drop, and the village may found another. */
	//$ gametest_ticks_batch AREA '100' '"village_habitat_down"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "village_habitat_down")
	public void takingTheGardenDownRemovesTheHabitat(GameTestHelper helper) {
		if (!PokemonFeatures.HABITATS.available()) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = garden(helper);
		Villager keeper = expert(helper);
		BlockPos centre = helper.absolutePos(CENTRE);
		helper.assertTrue(VillageHabitats.found(level, keeper, centre), "couldn't found it");
		helper.assertTrue(VillageHabitats.isHabitat(level.getBlockState(centre)), "not placed");
		VillageHabitats.onTakenDown(level, StarterBlueprints.HABITAT_GARDEN.id(), placement);
		helper.assertTrue(level.getBlockState(centre).isAir(), "after the take-down: " + level.getBlockState(centre));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		helper.assertTrue(hall.habitat() == null, "the hall still keeps " + hall.habitat());
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2)).isEmpty(), "something dropped");
			helper.succeed();
		});
	}
}
