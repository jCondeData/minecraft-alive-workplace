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
		Optional<BlockPos> bench = level.getPoiManager().findClosest(h -> h.is(ModVillagers.BLUEPRINT_TABLE_POI) || h.is(ModVillagers.BUILDERS_BENCH_POI), anchor,
			Builders.MAX_SITE_DISTANCE, PoiManager.Occupancy.ANY);
		if (bench.isEmpty()) {
			return Optional.of(new SupplyReport(Optional.empty(), 0, List.of()));
		}
		BuildPlan plan = BuildPlan.create(blueprint.get(), placement, level, Rules.number(level, ModGameRules.FOUNDATION_DEPTH));
		List<BlockPos> supplies = SupplyContainers.find(level, bench.get(), plan.bounds());
		if (Rules.on(level, ModGameRules.FREE_MATERIALS)) {
			return Optional.of(new SupplyReport(bench, supplies.size(), List.of()));
		}
		Map<Item, Integer> need = need(level, plan);
		List<SupplyReport.Missing> missing = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : need.entrySet()) {
			long have = have(level, supplies, e.getKey());
			if (have < e.getValue()) {
				missing.add(new SupplyReport.Missing(e.getKey(), (int) (e.getValue() - have)));
			}
		}
		missing.sort((a, b) -> Integer.compare(b.count(), a.count()));
		return Optional.of(new SupplyReport(bench, supplies.size(), List.copyOf(missing)));
	}

	/** What the plan's steps not yet standing need, by family key (an unloaded block counts as not built yet). */
	private static Map<Item, Integer> need(ServerLevel level, BuildPlan plan) {
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
		return need;
	}

	/** How many of {@code key}'s family the chests hold. */
	private static long have(ServerLevel level, List<BlockPos> supplies, Item key) {
		long have = 0;
		for (Item member : MaterialFamilies.accepted(key)) {
			have += SupplyContainers.count(level, supplies, member);
		}
		return have;
	}

	/** One line of a material list: what the build needs and how much of it isn't in the chests yet. */
	public record Line(Item item, int need, int toBring) {
	}

	/**
	 * The material list to take away (23.4): every material the blueprint needs, biggest first, with what the chests by
	 * the nearest Blueprint Table or Builder's Bench hold already taken off ({@code bench} is where). Not placed here, or
	 * no table near: the whole list, nothing taken off. Empty only for an unknown or too big blueprint.
	 */
	public record Checklist(Optional<BlockPos> bench, List<Line> lines) {
		public int toBring() {
			return lines.stream().mapToInt(Line::toBring).sum();
		}
	}

	public static Optional<Checklist> checklist(ServerLevel level, BlueprintData data) {
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, data.structure());
		if (blueprint.isEmpty() || blueprint.get().blocks().size() > MAX_STEPS) {
			return Optional.empty();
		}
		List<Line> lines = new ArrayList<>();
		Optional<BlockPos> bench = Optional.empty();
		boolean here = data.placement().isPresent() && data.placement().get().dimension().equals(Ids.of(level.dimension()));
		if (here) {
			BlueprintData.Placement placement = data.placement().get();
			bench = level.getPoiManager().findClosest(h -> h.is(ModVillagers.BLUEPRINT_TABLE_POI) || h.is(ModVillagers.BUILDERS_BENCH_POI),
				BlueprintItem.anchorWorld(placement, blueprint.get().size()), Builders.MAX_SITE_DISTANCE, PoiManager.Occupancy.ANY);
		}
		if (bench.isPresent()) {
			BuildPlan plan = BuildPlan.create(blueprint.get(), data.placement().get(), level, Rules.number(level, ModGameRules.FOUNDATION_DEPTH));
			List<BlockPos> supplies = SupplyContainers.find(level, bench.get(), plan.bounds());
			boolean free = Rules.on(level, ModGameRules.FREE_MATERIALS);
			need(level, plan).forEach((item, n) -> lines.add(new Line(item, n, free ? 0 : (int) Math.max(0, n - have(level, supplies, item)))));
		} else {
			Map<Item, Integer> need = new LinkedHashMap<>();
			for (Blueprint.Entry entry : blueprint.get().blocks()) {
				if (MaterialRules.classify(entry.state(), entry.nbt()) != MaterialRules.Kind.SKIP) {
					for (MaterialRules.Requirement r : MaterialRules.requirements(entry.state(), entry.nbt())) {
						need.merge(MaterialFamilies.key(r.item()), r.count(), Integer::sum);
					}
				}
			}
			for (Blueprint.EntityEntry entity : blueprint.get().entities()) { // item frames, paintings, armour stands: as the plan counts them
				Item cost = io.github.jcondedata.aliveworkplace.blueprint.BlueprintEntities.cost(entity.nbt());
				if (cost != null && cost != net.minecraft.world.item.Items.AIR) {
					need.merge(MaterialFamilies.key(cost), 1, Integer::sum);
				}
			}
			need.forEach((item, n) -> lines.add(new Line(item, n, n)));
		}
		// Still to bring first, biggest first; then what's all there, biggest first.
		lines.sort(java.util.Comparator.comparing((Line l) -> l.toBring() == 0).thenComparing(Line::need, java.util.Comparator.reverseOrder())
			.thenComparing(l -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(l.item())));
		return Optional.of(new Checklist(bench, List.copyOf(lines)));
	}

	private BlueprintSupplies() {
	}
}
