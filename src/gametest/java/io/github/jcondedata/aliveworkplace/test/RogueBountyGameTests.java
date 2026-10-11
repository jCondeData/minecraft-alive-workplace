package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.RoguePokemon;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** Rogue Pokémon bounties (ROADMAP 31.14) without Cobblemon: the biome groups load, and no rogue is ever posted. */
public class RogueBountyGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);

	private static BlockPos village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean enabled = Bounties.ENABLED;
		boolean rogues = Rogues.ENABLED;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		Bounties.forget(level);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			Bounties.ENABLED = enabled;
			Rogues.ENABLED = rogues;
			Bounties.forget(level);
			level.setDayTime(time);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		return helper.absolutePos(HALL);
	}

	private static ResourceLocation id(String s) {
		return ResourceLocation.parse(s);
	}

	/** The eight biome groups load with the roadmap's species, and a biome finds its group (its own name before a tag). */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void rogueGroupsLoad(GameTestHelper helper) {
		Map<ResourceLocation, Rogues.Group> groups = Rogues.groups();
		helper.assertTrue(groups.size() == 8, "groups: " + groups.keySet());
		Map<String, List<String>> want = Map.of(
			"forests", List.of("ursaring", "scyther", "pinsir"),
			"plains", List.of("tauros", "kangaskhan", "rapidash"),
			"mountains", List.of("onix", "golem", "aggron"),
			"deserts", List.of("sandslash", "krookodile", "flygon"),
			"swamps", List.of("tangrowth", "toxicroak", "swampert"),
			"beaches", List.of("kingler", "crawdaunt", "barbaracle"),
			"snowy", List.of("mamoswine", "abomasnow", "beartic"),
			"savannas", List.of("donphan", "rhydon", "lycanroc"));
		want.forEach((group, species) -> {
			Rogues.Group g = groups.get(id("aliveworkplace:" + group));
			helper.assertTrue(g != null, "no group " + group);
			helper.assertTrue(g.species().stream().map(ResourceLocation::getPath).toList().equals(species), group + ": " + g.species());
		});
		var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
		Map<ResourceKey<Biome>, String> where = Map.of(Biomes.DARK_FOREST, "forests", Biomes.MEADOW, "plains", Biomes.JAGGED_PEAKS, "mountains",
			Biomes.BADLANDS, "deserts", Biomes.MANGROVE_SWAMP, "swamps", Biomes.STONY_SHORE, "beaches", Biomes.SNOWY_SLOPES, "snowy",
			Biomes.OLD_GROWTH_PINE_TAIGA, "savannas");
		where.forEach((biome, group) -> {
			Rogues.Group g = Rogues.group(biomes.getHolderOrThrow(biome));
			helper.assertTrue(g != null && g.id().getPath().equals(group), biome.location() + " is in " + (g == null ? "none" : g.id()) + ", not " + group);
		});
		helper.assertTrue(Rogues.group(biomes.getHolderOrThrow(Biomes.DEEP_OCEAN)) == null, "the deep ocean has rogues");
		helper.assertTrue(Rogues.level(VillageRanks.Rank.VILLAGE) == 35 && Rogues.level(VillageRanks.Rank.TOWN) == 50
			&& Rogues.level(VillageRanks.Rank.CITY) == 65, "levels by rank");
		helper.succeed();
	}

	/** Without Cobblemon no rogue is posted, by the hall's round or asked for, and a rogue bounty (from a save) spawns nothing. */
	//$ gametest_ticks_batch AREA '40' '"rogueNoCobblemon"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "rogueNoCobblemon")
	public void neverPostedWithoutCobblemon(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!RoguePokemon.present(), "a rogue filling without Cobblemon");
		helper.assertTrue(!Rogues.available(), "rogues available without Cobblemon");
		helper.assertTrue(Rogues.post(level, hall, RandomSource.create(14)) == null, "a rogue posted without Cobblemon");
		boolean auto = Bounties.AUTO;
		Bounties.AUTO = true;
		try {
			for (int day = 0; day < 6; day++) {
				level.setDayTime((200L + day * Bounties.EVERY_DAYS) * 24000L + 1000);
				Bounties.round(level, hall);
			}
		} finally {
			Bounties.AUTO = auto;
		}
		helper.assertTrue(!Bounties.open(level, hall).isEmpty(), "the round posted nothing at all");
		helper.assertTrue(Bounties.open(level, hall).stream().noneMatch(b -> b.rogue != null), "the round posted a rogue without Cobblemon");
		// A rogue bounty from a save with Cobblemon (since removed): it's named, saved and loaded, and nothing spawns.
		Bounties.forget(level);
		Bounties.Bounty b = Rogues.post(level, hall, id("cobblemon:ursaring"), hall.offset(12, 0, 12));
		helper.assertTrue(b.name(level).getString().equals("Rogue Ursaring"), "its name: " + b.name(level).getString());
		helper.assertTrue(b.reward == 20 && b.rogueLevel == 35, "16 emeralds times the Village factor at level 35: " + b.reward + ", " + b.rogueLevel);
		CompoundTag saved = Bounties.Data.get(level).save(new CompoundTag(), level.registryAccess());
		Bounties.Bounty loaded = Bounties.Data.load(saved, level.registryAccess()).bounties.get(b.id);
		helper.assertTrue(loaded != null && id("cobblemon:ursaring").equals(loaded.rogue) && loaded.rogueLevel == 35, "the rogue lost in a save");
		helper.assertTrue(Rogues.spawn(level, b, null) == null && b.captain == null, "a rogue spawned without Cobblemon");
		Bounties.check(level);
		helper.assertTrue(b.captain == null, "the check spawned a rogue without Cobblemon");
		helper.succeed();
	}
}
