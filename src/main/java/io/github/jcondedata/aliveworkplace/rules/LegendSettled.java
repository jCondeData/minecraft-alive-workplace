package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * {@code legend}: the Legend {@code id} has settled in the village. With a {@code stand_in} list (35.2, a Wonder's
 * Legend), that list holds instead, all of it, while Legends are off (the {@code legends} switch, or M29's gate),
 * {@code wonderLegends} is false, or the Legend's file isn't loaded. Without one it reads exactly as before.
 */
public record LegendSettled(ResourceLocation id, List<Condition> standIn) implements Condition {
	public LegendSettled(ResourceLocation id) {
		this(id, List.of());
	}

	static LegendSettled read(JsonObject json) {
		return new LegendSettled(Conditions.id(json, "id"),
			json.has("stand_in") ? Conditions.parseAll(json.getAsJsonArray("stand_in")) : List.of());
	}

	@Override
	public String type() {
		return "legend";
	}

	/** Whether the stand-in is asked for instead of the Legend just now. */
	public boolean standingIn() {
		return !standIn.isEmpty() && (!Legends.ENABLED || !Wonders.LEGENDS || Legends.get(id).isEmpty());
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		if (standingIn()) {
			int met = 0;
			Component first = null;
			for (Condition c : standIn) {
				Progress p = c.progress(level, hall);
				if (p.met()) {
					met++;
				} else if (first == null) {
					first = p.line();
				}
			}
			Component line = first != null ? first : standIn.get(0).progress(level, hall).line();
			return new Progress(met, standIn.size(), Component.translatable("rule.aliveworkplace.legend_stand_in", title(id), line));
		}
		boolean here = false;
		for (Villager v : Village.villagers(level, hall)) {
			LegendData data = ModAttachments.LEGEND.get(v);
			here |= data != null && data.settled() && data.id().equals(id);
		}
		return Progress.of(type(), here ? 1 : 0, 1, Legends.get(id).map(Legend::titleText).orElse(Component.literal(id.toString())));
	}

	/** The Legend's title ("the Seer"), from the file when it's loaded, else by the lang key its file would use. */
	public static Component title(ResourceLocation id) {
		return Legends.get(id).map(Legend::titleText).orElse(Component.translatable("legend." + id.getNamespace() + "." + id.getPath() + ".title"));
	}
}
