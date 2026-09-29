package io.github.jcondedata.aliveworkplace.tutor;

import io.github.jcondedata.aliveworkplace.work.Extension;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** A Move Tutor's lessons (see {@link Tutors}). Filled in by {@code compat/cobblemon}. */
public interface MoveLessons {
	Extension<MoveLessons> EXTENSION = new Extension<>("move lessons");

	/** Opens {@code tutor}'s lessons for {@code player}. */
	void open(ServerPlayer player, Villager tutor);
}
