package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
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
 * a building, and what the next rank still needs — only what's actually lacking, most pressing first.
 */
public final class VillageAdvice {
	/** Upgrades suggested at most. */
	static final int MAX_UPGRADES = 2;

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
		List<Tip> tips = new ArrayList<>();
		int villagers = census.villagers();
		int adults = villagers - census.children();
		PoiManager poi = level.getPoiManager();
		if (!has(census, ModVillagers.BUILDER)) {
			tips.add(new Tip("builder", ModBlocks.BUILDERS_BENCH.asItem()));
		}
		if (villagers > census.beds()) {
			tips.add(new Tip("beds", Items.RED_BED, villagers - census.beds()));
		}
		if (adults > 0 && census.food() < adults * 2L) {
			tips.add(new Tip("food", Items.BREAD, census.food(), adults * 2L));
		}
		if (poi.getCountInRange(h -> h.is(ModVillagers.STOREHOUSE_POI), hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY) == 0) {
			tips.add(new Tip("storehouse", ModBlocks.STOREHOUSE.asItem()));
		}
		int guardsNeeded = (villagers + VillageNeeds.VILLAGERS_PER_GUARD - 1) / VillageNeeds.VILLAGERS_PER_GUARD;
		if (villagers > 0 && census.guards() < guardsNeeded) {
			tips.add(new Tip("guards", ModBlocks.GUARD_POST.asItem(), guardsNeeded - census.guards()));
		}
		io.github.jcondedata.aliveworkplace.guard.BanditCamps.near(level, hall)
			.ifPresent(camp -> tips.add(new Tip("bandits", Items.CROSSBOW, VillageHallScreen.where(hall, camp.pos()))));
		long ill = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), io.github.jcondedata.aliveworkplace.people.Sickness::isIll).size();
		if (ill > 0) {
			tips.add(new Tip("ill", Items.GLISTERING_MELON_SLICE, ill));
		}
		VillageNeeds.Needs needs = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.needs() : null;
		if (needs != null && needs.lit() < needs.villagers()) {
			tips.add(new Tip("dark", Items.LANTERN, needs.villagers() - needs.lit()));
		}
		long jobless = census.jobless().stream().filter(v -> v.getVillagerData().getProfession() != VillagerProfession.NITWIT).count();
		if (jobless > 0) {
			tips.add(new Tip("jobless", Items.CRAFTING_TABLE, jobless));
		}
		if (villagers >= 6 && !has(census, ModVillagers.SCHOLAR)) {
			tips.add(new Tip("research", ModBlocks.SCHOLARS_DESK.asItem()));
		}
		if (villagers >= 5 && Decorations.beauty(level, hall) < 3) {
			tips.add(new Tip("beauty", Items.FLOWER_POT));
		}
		int upgrades = 0;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			if (upgrades >= MAX_UPGRADES) {
				break;
			}
			ResourceLocation up = BlueprintUpgrades.upgradeOf(f.structure());
			if (!up.equals(f.structure()) && BlueprintLibrary.get(level, up).isPresent()) {
				tips.add(new Tip("upgrade", ModItems.BLUEPRINT, Blueprints.displayName(f.structure()), Blueprints.displayName(up)));
				upgrades++;
			}
		}
		VillageRanks.Rank next = VillageRanks.of(level, hall).next();
		if (next != null) {
			VillageRanks.Score score = VillageRanks.score(level, hall, villagers);
			tips.add(new Tip("rank", Items.BELL, next.title(), Math.max(0, next.villagers - score.villagers()),
				Math.max(0, next.buildings - score.buildings()), Math.max(0, next.research - score.research())));
		}
		return tips;
	}

	private static boolean has(VillageHalls.Census census, VillagerProfession job) {
		return census.workers().stream().anyMatch(v -> v.getVillagerData().getProfession() == job);
	}

	private VillageAdvice() {
	}
}
