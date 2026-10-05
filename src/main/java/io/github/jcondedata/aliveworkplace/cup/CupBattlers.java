package io.github.jcondedata.aliveworkplace.cup;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The Pokémon of the Festival Cup's bouts (ROADMAP 28.18). Filled in by {@code compat/cobblemon}; without it a Cup never starts. */
public interface CupBattlers {
	Extension<CupBattlers> EXTENSION = new Extension<>("cup bouts");

	/** The trainer's team for a Cup with this theme: its seeded pool filtered by the theme, as many as it brings, at its level. */
	List<CupBout.Fighter> team(UUID trainer, int tier, CupThemes.Theme theme);

	/** Sends {@code fighter} out at {@code at}, facing {@code facing}: a real Pokémon that can't be caught, hurt or battled. */
	@Nullable
	Entity sendOut(ServerLevel level, CupBout.Fighter fighter, Vec3 at, Vec3 facing);

	/** Shows {@code attacker} using a move of {@code type} (physical or special) on {@code target}. */
	void useMove(ServerLevel level, Entity attacker, Entity target, String type, boolean physical);

	/** Calls a Pokémon back (with its animation); it's gone soon after. */
	void recall(ServerLevel level, Entity pokemon);

	/** Every bout's Pokémon in {@code box}. */
	List<Entity> inBox(ServerLevel level, AABB box);
}
