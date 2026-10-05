package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What a village should do next, as the Village Hall sees it (the hall screen's "What next?" page): a builder first, then
 * beds, food, a storehouse, guards, light by the beds, jobs for the jobless, a scholar, decorations, the next upgrade of
 * a building, better homes, and what the next rank still needs — only what's actually lacking, most pressing first.
 */
public final class VillageAdvice {
	/** Upgrades suggested at most. */
	static final int MAX_UPGRADES = 2;
	/** Grown-ups a village needs before better homes are worth suggesting. */
	public static final int MIN_FOR_HOMES = 3;
	/** Meals in store a grown-up should have for the village not to be short of food (two days' eating). */
	public static final int MEALS_PER_ADULT = 2;

	/** One piece of advice: {@code advice.aliveworkplace.<key>} (the title) and {@code <key>.how}, with {@code args}. */
	public record Tip(String key, Item icon, Object... args) {
		public Component title() {
			return Component.translatable("advice.aliveworkplace." + key, args);
		}

		public Component how() {
			return Component.translatable("advice.aliveworkplace." + key + ".how", args);
		}
	}

	public static List<Tip> tips(ServerLevel level, BlockPos hall) {
		VillageHalls.Census census = VillageHalls.census(level, hall);
		// The Seer's dawn foretelling first (29.16): tonight, the next festival and market, tomorrow's guest.
		List<Tip> tips = new ArrayList<>(io.github.jcondedata.aliveworkplace.legend.Seer.tips(level, hall));
		int villagers = census.villagers();
		int adults = villagers - census.children();
		if (!has(census, ModVillagers.BUILDER)) {
			tips.add(new Tip("builder", ModBlocks.BLUEPRINT_TABLE.asItem()));
		}
		if (bedsShort(census) > 0) {
			tips.add(new Tip("beds", Items.RED_BED, bedsShort(census)));
		}
		if (adults > 0 && census.food() < foodWanted(census, MEALS_PER_ADULT)) {
			tips.add(new Tip("food", Items.BREAD, census.food(), foodWanted(census, MEALS_PER_ADULT)));
		}
		if (poiCount(level, hall, ModVillagers.STOREHOUSE_POI) == 0) {
			tips.add(new Tip("storehouse", ModBlocks.STOREHOUSE.asItem()));
		}
		int guardsNeeded = guardsWanted(villagers);
		if (villagers > 0 && census.guards() < guardsNeeded) {
			tips.add(new Tip("guards", Items.GRINDSTONE, guardsNeeded - census.guards()));
		}
		io.github.jcondedata.aliveworkplace.guard.BanditCamps.near(level, hall)
			.ifPresent(camp -> tips.add(new Tip("bandits", Items.CROSSBOW, VillageHallScreen.where(hall, camp.pos()))));
		long ill = ill(level, hall);
		if (ill > 0) {
			tips.add(new Tip("ill", Items.GLISTERING_MELON_SLICE, ill));
		}
		VillageNeeds.Needs needs = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.needs() : null;
		if (needs != null && needs.lit() < needs.villagers()) {
			tips.add(new Tip("dark", Items.LANTERN, needs.villagers() - needs.lit()));
		}
		long jobless = jobless(census);
		if (jobless > 0) {
			tips.add(new Tip("jobless", Items.CRAFTING_TABLE, jobless));
		}
		if (villagers >= 6 && !has(census, ModVillagers.SCHOLAR)) {
			tips.add(new Tip("research", Items.LECTERN));
		}
		if (villagers >= 5 && Decorations.beauty(level, hall) < 3) {
			tips.add(new Tip("beauty", Items.FLOWER_POT));
		}
		List<ResourceLocation> built = BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS).stream()
			.map(BuildSiteManager.Finished::structure).toList();
		if (wantsPokemonCenter(io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon"),
				VillageRanks.of(level, hall), built)) {
			tips.add(new Tip("pokemon_center", ModItems.BLUEPRINT));
		}
		for (BuildSiteManager.Finished f : upgradable(level, hall).stream().limit(MAX_UPGRADES).toList()) {
			tips.add(new Tip("upgrade", ModItems.BLUEPRINT, Blueprints.displayName(f.structure()),
				Blueprints.displayName(BlueprintUpgrades.upgradeOf(f.structure()))));
		}
		// Better homes: most grown-ups sleep in first-tier buildings, or in none a builder put up
		HomeCount homes = homes(level, hall);
		if (homes.grown() >= MIN_FOR_HOMES && homes.plain() * 2L > homes.grown()) {
			tips.add(new Tip("homes", Items.OAK_DOOR, homes.plain(), homes.grown(), Homes.TIER_2_MOOD, Homes.TIER_3_MOOD));
		}
		// A Legend who lacks only one condition (29.4)
		tips.addAll(io.github.jcondedata.aliveworkplace.legend.LegendsPage.tips(level, hall));
		VillageRanks.Rank next = VillageRanks.of(level, hall).next();
		if (next != null) {
			VillageRanks.Score score = VillageRanks.score(level, hall, villagers);
			tips.add(new Tip("rank", Items.BELL, next.title(), Math.max(0, next.villagers - score.villagers()),
				Math.max(0, next.buildings - score.buildings()), Math.max(0, next.research - score.research())));
		}
		return tips;
	}

	/**
	 * ROADMAP 28.7: a Cobblemon village of at least Village rank without a Pokémon Center (either tier, finished near the
	 * hall) is told to build one.
	 */
	public static boolean wantsPokemonCenter(boolean cobblemon, VillageRanks.Rank rank, List<ResourceLocation> built) {
		return cobblemon && rank.ordinal() >= VillageRanks.Rank.VILLAGE.ordinal()
			&& built.stream().noneMatch(id -> id.equals(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.POKEMON_CENTER.id())
				|| id.equals(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.POKEMON_CENTER_2.id()));
	}

	// The numbers the tips go by, shared with the Steward's rules (ROADMAP 27.6) so his desk and these tips agree.

	/** Villagers (children too) more than the village's beds: the "beds" tip. */
	public static int bedsShort(VillageHalls.Census census) {
		return Math.max(0, census.villagers() - census.beds());
	}

	/** Grown-ups in the village. */
	public static int adults(VillageHalls.Census census) {
		return census.villagers() - census.children();
	}

	/** Food the store should hold: {@code mealsPerAdult} for each grown-up (the "food" tip asks {@link #MEALS_PER_ADULT}). */
	public static long foodWanted(VillageHalls.Census census, int mealsPerAdult) {
		return (long) adults(census) * mealsPerAdult;
	}

	/** Grown-ups without work, nitwits aside: no job, or a job but no workstation (the "jobless" tip). */
	public static long jobless(VillageHalls.Census census) {
		return census.jobless().stream().filter(v -> v.getVillagerData().getProfession() != VillagerProfession.NITWIT).count();
	}

	/** Guards a village of {@code villagers} wants: one for every {@link VillageNeeds#VILLAGERS_PER_GUARD} (the "guards" tip). */
	public static int guardsWanted(int villagers) {
		return (villagers + VillageNeeds.VILLAGERS_PER_GUARD - 1) / VillageNeeds.VILLAGERS_PER_GUARD;
	}

	/** Villagers of the village who are ill (the "ill" tip). */
	public static long ill(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && io.github.jcondedata.aliveworkplace.people.Sickness.isIll(v)).size();
	}

	/**
	 * The village's beds (their head halves, as villagers claim them) with less block light than counts as lit
	 * ({@link VillageNeeds#LIT}), darkest first: where the Steward's street lamps go (27.12).
	 */
	public static List<BlockPos> darkBeds(ServerLevel level, BlockPos hall) {
		return level.getPoiManager().findAll(h -> h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), p -> true, hall, VillageHalls.RADIUS,
				PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable)
			.filter(p -> level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p) < VillageNeeds.LIT)
			.sorted(java.util.Comparator.<BlockPos>comparingInt(p -> level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p))
				.thenComparingDouble(p -> p.distSqr(hall)).thenComparingLong(BlockPos::asLong))
			.toList();
	}

	/** Workers of {@code job} in the village (with their workstation). */
	public static long workers(VillageHalls.Census census, VillagerProfession job) {
		return census.workers().stream().filter(v -> v.getVillagerData().getProfession() == job).count();
	}

	/** How many points of interest of {@code type} the village has (the "storehouse" tip asks for one). */
	public static long poiCount(ServerLevel level, BlockPos hall, net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> type) {
		return level.getPoiManager().getCountInRange(h -> h.is(type), hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY);
	}

	/** Finished buildings in the village whose next tier is a blueprint the server has (the "upgrade" tips, first {@link #MAX_UPGRADES}). */
	public static List<BuildSiteManager.Finished> upgradable(ServerLevel level, BlockPos hall) {
		List<BuildSiteManager.Finished> out = new ArrayList<>();
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			ResourceLocation up = BlueprintUpgrades.upgradeOf(f.structure());
			if (!up.equals(f.structure()) && BlueprintLibrary.get(level, up).isPresent()) {
				out.add(f);
			}
		}
		return out;
	}

	/** Grown-ups living in a tier I building or none a builder put up ({@code plain}), of all grown-ups ({@code grown}). */
	public record HomeCount(int plain, int grown) {
	}

	/** The "homes" tip's count: who sleeps in first-tier homes, of the grown-ups in the village. */
	public static HomeCount homes(ServerLevel level, BlockPos hall) {
		List<Villager> grown = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> !v.isBaby() && v.isAlive());
		int plain = (int) grown.stream().filter(v -> Homes.of(level, v).map(h -> h.tier() < 2).orElse(true)).count();
		return new HomeCount(plain, grown.size());
	}

	private static boolean has(VillageHalls.Census census, VillagerProfession job) {
		return workers(census, job) > 0;
	}

	private VillageAdvice() {
	}
}
