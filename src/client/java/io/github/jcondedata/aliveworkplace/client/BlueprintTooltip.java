package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.SupplyReport;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

/** Hold Shift over a blueprint to see what it needs (the same list as the Blueprint Table). */
public final class BlueprintTooltip {
	private static final int MAX_LINES = 10;
	private static final Map<ResourceLocation, List<Map.Entry<Item, Integer>>> CACHE = new HashMap<>();

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			BlueprintData data = BlueprintItem.data(stack).orElse(null);
			if (data == null) {
				return;
			}
			SupplyReport report = stack.get(ModComponents.SUPPLY_REPORT);
			if (report != null && report.bench().isPresent()) {
				lines.addAll(missingLines(report, Screen.hasShiftDown()));
				return;
			}
			if (report != null) {
				lines.add(Component.translatable("tooltip.aliveworkplace.blueprint.no_bench",
					io.github.jcondedata.aliveworkplace.build.Builders.MAX_SITE_DISTANCE).withStyle(ChatFormatting.RED));
			}
			if (!Screen.hasShiftDown()) {
				lines.add(Component.translatable("tooltip.aliveworkplace.blueprint.materials_hint").withStyle(ChatFormatting.DARK_GRAY));
				return;
			}
			lines.addAll(materialLines(data.structure()));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CACHE.clear());
	}

	/** The tooltip lines for the materials of a blueprint ("Loading…" until the server has sent it). */
	public static List<Component> materialLines(ResourceLocation id) {
		List<Map.Entry<Item, Integer>> materials = materials(id);
		if (materials == null) {
			return List.of(Component.translatable("tooltip.aliveworkplace.blueprint.materials_loading").withStyle(ChatFormatting.DARK_GRAY));
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("tooltip.aliveworkplace.blueprint.materials").withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < Math.min(MAX_LINES, materials.size()); i++) {
			Map.Entry<Item, Integer> e = materials.get(i);
			lines.add(Component.translatable("tooltip.aliveworkplace.blueprint.material", e.getValue(), e.getKey().getDescription())
				.withStyle(ChatFormatting.GRAY));
		}
		if (materials.size() > MAX_LINES) {
			lines.add(Component.translatable("tooltip.aliveworkplace.blueprint.more", materials.size() - MAX_LINES).withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	/** A placed blueprint near a Builder's Bench: what the chests there are still short of. */
	public static List<Component> missingLines(SupplyReport report, boolean detailed) {
		if (report.missing().isEmpty()) {
			return List.of(Component.translatable("tooltip.aliveworkplace.blueprint.all_there").withStyle(ChatFormatting.GREEN));
		}
		if (!detailed) {
			return List.of(Component.translatable("tooltip.aliveworkplace.blueprint.missing_summary", report.totalMissing())
				.withStyle(ChatFormatting.YELLOW));
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("tooltip.aliveworkplace.blueprint.missing").withStyle(ChatFormatting.YELLOW));
		for (int i = 0; i < Math.min(MAX_LINES, report.missing().size()); i++) {
			SupplyReport.Missing m = report.missing().get(i);
			lines.add(Component.translatable("tooltip.aliveworkplace.blueprint.material", m.count(), m.item().getDescription())
				.withStyle(ChatFormatting.GRAY));
		}
		if (report.missing().size() > MAX_LINES) {
			lines.add(Component.translatable("tooltip.aliveworkplace.blueprint.more", report.missing().size() - MAX_LINES).withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	private static List<Map.Entry<Item, Integer>> materials(ResourceLocation id) {
		List<Map.Entry<Item, Integer>> cached = CACHE.get(id);
		if (cached != null) {
			return cached;
		}
		List<BlockState> blocks = BlueprintPreviewRenderer.blocks(id);
		if (blocks == null) {
			return null;
		}
		Map<Item, Integer> count = new LinkedHashMap<>();
		for (BlockState state : blocks) {
			if (MaterialRules.classify(state) != MaterialRules.Kind.SKIP) {
				for (MaterialRules.Requirement r : MaterialRules.requirements(state, null)) {
					count.merge(io.github.jcondedata.aliveworkplace.build.MaterialFamilies.key(r.item()), r.count(), Integer::sum);
				}
			}
		}
		List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(count.entrySet());
		sorted.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
		CACHE.put(id, sorted);
		return sorted;
	}

	private BlueprintTooltip() {
	}
}
