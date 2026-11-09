package io.github.jcondedata.aliveworkplace.move;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Who moves buildings (ROADMAP 45.2, ahead of the Mover job of 45.3): a villager with the {@link #PROFESSION} job. The
 * Hall's Move page asks {@link #hired} before it takes a move; the job itself is registered by 45.3 under this id, so a
 * village has no Mover until then and every move waits for one.
 */
public final class Movers {
	/** The Mover's profession id (registered by the Mover job, 45.3). */
	public static final ResourceLocation PROFESSION = AliveWorkplace.id("mover");

	/** Whether {@code villager} is a Mover: by profession id, so this needs nothing of the job's classes. */
	public static Predicate<Villager> isMover = villager -> PROFESSION.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()));

	/** Whether a Mover works in the village round {@code hall}. */
	public static boolean hired(ServerLevel level, BlockPos hall) {
		return !VillageHalls.villagers(level, hall, villager -> !villager.isBaby() && isMover.test(villager)).isEmpty();
	}

	private Movers() {
	}
}
