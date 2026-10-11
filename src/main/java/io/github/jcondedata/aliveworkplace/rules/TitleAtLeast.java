package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * {@code title_at_least} (ROADMAP 31.12): some player holds at least {@code title} (friend, hero, lord) in the village;
 * read for one player (a personal request's), that player does. With {@code reputation} off nobody holds a title, and
 * the condition holds, so a Legend's visit doesn't wait on titles that can't be earned.
 */
public record TitleAtLeast(Reputation.Title title) implements Condition {
	static TitleAtLeast read(JsonObject json) {
		String name = json.has("title") ? json.get("title").getAsString() : "";
		for (Reputation.Title t : Reputation.Title.values()) {
			if (t != Reputation.Title.STRANGER && t.id().equals(name.toLowerCase(Locale.ROOT))) {
				return new TitleAtLeast(t);
			}
		}
		throw new IllegalArgumentException("'title' must be friend, hero or lord, not '" + name + "'");
	}

	@Override
	public String type() {
		return "title_at_least";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		Reputation.Title best = Reputation.ENABLED ? Reputation.best(level, hall) : title;
		return Progress.of(type(), best.ordinal(), title.ordinal(), best.label(), title.label());
	}

	@Override
	public boolean met(ServerLevel level, BlockPos hall, @Nullable Villager giver, @Nullable ServerPlayer player) {
		if (player == null || !Reputation.ENABLED) {
			return met(level, hall);
		}
		return Reputation.title(level, hall, player.getUUID()).atLeast(title);
	}
}
