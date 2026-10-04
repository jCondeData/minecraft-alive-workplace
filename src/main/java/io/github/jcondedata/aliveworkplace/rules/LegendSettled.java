package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** {@code legend}: the Legend {@code id} has settled in the village. */
public record LegendSettled(ResourceLocation id) implements Condition {
	static LegendSettled read(JsonObject json) {
		return new LegendSettled(Conditions.id(json, "id"));
	}

	@Override
	public String type() {
		return "legend";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		boolean here = false;
		for (Villager v : Village.villagers(level, hall)) {
			LegendData data = ModAttachments.LEGEND.get(v);
			here |= data != null && data.settled() && data.id().equals(id);
		}
		return Progress.of(type(), here ? 1 : 0, 1, io.github.jcondedata.aliveworkplace.legend.Legends.get(id).map(io.github.jcondedata.aliveworkplace.legend.Legend::titleText).orElse(Component.literal(id.toString())));
	}
}
