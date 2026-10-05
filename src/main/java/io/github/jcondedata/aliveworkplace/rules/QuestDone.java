package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.story.Stories;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** {@code quest_done}: someone has finished the quest file {@code quest} in this village. */
public record QuestDone(ResourceLocation quest) implements Condition {
	static QuestDone read(JsonObject json) {
		return new QuestDone(Conditions.id(json, "quest"));
	}

	@Override
	public String type() {
		return "quest_done";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		return Progress.of(type(), Stories.finished(level, hall, quest) ? 1 : 0, 1, quest.toString());
	}
}
