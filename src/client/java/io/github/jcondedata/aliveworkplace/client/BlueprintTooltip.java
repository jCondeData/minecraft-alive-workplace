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

/**
 * Hold Shift over a blueprint to see what it needs (the same list as the Blueprint Table), packed several to a line
 * ("64 Oak Planks · 32 Cobblestone") so even a big build fits in about 20 lines.
 */
public final class BlueprintTooltip {
	/** Lines of packed materials at most; with the heading and the "…and N more" line that's 20. */
	private static final int MAX_LINES = 18;
	/** How wide a packed line may get, in GUI pixels. */
	private static final int MAX_WIDTH = 240;
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
		List<Component> pieces = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : materials) {
			pieces.add(piece(e.getValue(), e.getKey()));
		}
		pack(pieces, lines, "tooltip.aliveworkplace.blueprint.more_at_table");
		return lines;
	}

	private static Component piece(int count, Item item) {
		return Component.translatable("tooltip.aliveworkplace.blueprint.material", count, item.getDescription());
	}

	/**
	 * Packs {@code pieces} into lines no wider than {@link #MAX_WIDTH}, joined by " · ", at most {@link #MAX_LINES} of
	 * them; what doesn't fit is counted in a last {@code moreKey} line.
	 */
	public static void pack(List<Component> pieces, List<Component> out, String moreKey) {
		net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
		int lines = 0;
		int i = 0;
		while (i < pieces.size() && lines < MAX_LINES) {
			net.minecraft.network.chat.MutableComponent line = Component.empty().append(pieces.get(i));
			int width = font.width(pieces.get(i));
			i++;
			while (i < pieces.size()) {
				int next = font.width(SEPARATOR) + font.width(pieces.get(i));
				if (width + next > MAX_WIDTH) {
					break;
				}
				line.append(SEPARATOR).append(pieces.get(i));
				width += next;
				i++;
			}
			out.add(line.withStyle(ChatFormatting.GRAY));
			lines++;
		}
		if (i < pieces.size()) {
			out.add(Component.translatable(moreKey, pieces.size() - i).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static final Component SEPARATOR = Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY);

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
		List<Component> pieces = new ArrayList<>();
		for (SupplyReport.Missing m : report.missing()) {
			pieces.add(piece(m.count(), m.item()));
		}
		pack(pieces, lines, "tooltip.aliveworkplace.blueprint.more_missing");
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
