package io.github.jcondedata.aliveworkplace.wonder.powers;

import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * A Wonder's bells (35.10, the Great Cathedral's belfries): while a Wonder with the {@code weddings} power (and its
 * {@code bells}) stands, every bell of its blueprint still in place rings, as a player's hit rings it (raiders near it
 * glow), for a wedding ({@code people/Couples}), a festival's feast ({@code hall/Festivals}) and a raid beginning
 * ({@code guard/VillageRaids}). Where its bells are is worked out once per Wonder from its blueprint.
 */
public final class WonderBells {
	/** The bells of each finished Wonder's blueprint, by site. */
	private static final Map<UUID, List<BlockPos>> BELLS = new ConcurrentHashMap<>();

	/** Why the bells ring. */
	public enum Reason { WEDDING, FESTIVAL, RAID }

	/**
	 * Rings the bells of the village round {@code hall}'s standing Wonders that have them, those loaded and still in
	 * place; returns how many rang (none: no such Wonder, or its belfries are down or out of reach).
	 */
	public static int ring(ServerLevel level, BlockPos hall, Reason why) {
		int rang = 0;
		for (Wonders.Granted g : Wonders.powers(level, hall, WeddingsPower.TYPE)) {
			if (!g.home() || !(g.power() instanceof WeddingsPower w) || !w.bells()) {
				continue;
			}
			for (BlockPos bell : bells(level, g)) {
				if (level.isLoaded(bell) && level.getBlockState(bell).is(Blocks.BELL)
					&& ((BellBlock) Blocks.BELL).attemptToRing(level, bell, null)) {
					rang++;
				}
			}
			if (rang > 0 && why == Reason.WEDDING) {
				for (BlockPos bell : bells(level, g)) {
					level.playSound(null, bell, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.5f, 1.2f); // the wedding peal
				}
			}
		}
		return rang;
	}

	/** Where the Wonder {@code g}'s bells are, as its blueprint has them. */
	public static List<BlockPos> bells(ServerLevel level, Wonders.Granted g) {
		return BELLS.computeIfAbsent(g.site().id(), id -> {
			List<BlockPos> out = new ArrayList<>();
			for (WonderUpkeep.Block b : WonderUpkeep.blocks(level, g.site())) {
				if (b.state().is(Blocks.BELL)) {
					out.add(b.pos());
				}
			}
			return List.copyOf(out);
		});
	}

	/** Forgets where the bells are (a Wonder moved, a test's). */
	public static void forget() {
		BELLS.clear();
	}

	private WonderBells() {
	}
}
