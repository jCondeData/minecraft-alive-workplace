package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.SupplyReport;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * "What's still missing" for a placed blueprint: the materials it needs (blocks already standing in
 * place don't count) minus what's in the chests by the nearest Builder's Bench — the same count a
 * builder makes when it waits for materials. Kept on the item while it's in a player's inventory, so
 * its tooltip can show it.
 */
public final class BlueprintSupplies {
	/** Ticks between checks for a blueprint in someone's inventory. */
	public static final int EVERY = 40;
	/** Blueprints bigger than this aren't checked (the list would be stale by the time it's done anyway). */
	private static final int MAX_STEPS = 60_000;

	/** Called every tick for a blueprint in a player's inventory (server side). */
	public static void tick(ServerLevel level, ItemStack stack, int slot) {
		if ((level.getGameTime() + slot) % EVERY != 0) {
			return;
		}
		SupplyReport report = BlueprintItem.data(stack).flatMap(d -> check(level, d)).orElse(null);
		if (!Objects.equals(report, stack.get(ModComponents.SUPPLY_REPORT))) {
			if (report == null) {
				stack.remove(ModComponents.SUPPLY_REPORT);
			} else {
				stack.set(ModComponents.SUPPLY_REPORT, report);
			}
		}
	}

	/** The report for a blueprint placed in this dimension (empty when it isn't placed here). */
	public static Optional<SupplyReport> check(ServerLevel level, BlueprintData data) {
		if (data.placement().isEmpty() || !data.placement().get().dimension().equals(Ids.of(level.dimension()))) {
			return Optional.empty();
		}
		BlueprintData.Placement placement = data.placement().get();
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, data.structure());
		if (blueprint.isEmpty() || blueprint.get().blocks().size() > MAX_STEPS) {
			return Optional.empty();
		}
		BlockPos anchor = BlueprintItem.anchorWorld(placement, blueprint.get().size());
		Optional<BlockPos> bench = level.getPoiManager().findClosest(h -> h.is(ModVillagers.BUILDERS_BENCH_POI), anchor,
			Builders.MAX_SITE_DISTANCE, PoiManager.Occupancy.ANY);
		if (bench.isEmpty()) {
			return Optional.of(new SupplyReport(Optional.empty(), 0, List.of()));
		}
		BuildPlan plan = BuildPlan.create(blueprint.get(), placement, level, Rules.number(level, ModGameRules.FOUNDATION_DEPTH));
		List<BlockPos> supplies = SupplyContainers.find(level, bench.get(), plan.bounds());
		if (Rules.on(level, ModGameRules.FREE_MATERIALS)) {
			return Optional.of(new SupplyReport(bench, supplies.size(), List.of()));
		}
		Map<Item, Integer> need = new LinkedHashMap<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				// Never load a chunk just for a tooltip: an unloaded block counts as not built yet.
				if (level.isLoaded(step.pos()) && MaterialRules.matches(level.getBlockState(step.pos()), step.state())) {
					continue;
				}
				for (MaterialRules.Requirement r : step.requirements()) {
					need.merge(MaterialFamilies.key(r.item()), r.count(), Integer::sum);
				}
			}
		}
		for (BuildPlan.EntityStep entity : plan.entities()) {
			if (!BuildEntities.isPresent(level, entity)) {
				need.merge(entity.cost(), 1, Integer::sum);
			}
		}
		List<SupplyReport.Missing> missing = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : need.entrySet()) {
			long have = 0;
			for (Item member : MaterialFamilies.accepted(e.getKey())) {
				have += SupplyContainers.count(level, supplies, member);
			}
			if (have < e.getValue()) {
				missing.add(new SupplyReport.Missing(e.getKey(), (int) (e.getValue() - have)));
			}
		}
		missing.sort((a, b) -> Integer.compare(b.count(), a.count()));
		return Optional.of(new SupplyReport(bench, supplies.size(), List.copyOf(missing)));
	}

	private BlueprintSupplies() {
	}
}
