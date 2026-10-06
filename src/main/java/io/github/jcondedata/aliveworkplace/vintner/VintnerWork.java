package io.github.jcondedata.aliveworkplace.vintner;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.craft.LuxuryWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.npc.Villager;
import org.joml.Vector3f;

/**
 * The Vintner's shift (ROADMAP 34.9): the luxury workshop engine with the cauldron as the vat. While pressing, purple
 * splashes jump out of the vat and the fruit squelches. Nothing at all with config {@code vintners} off.
 */
public class VintnerWork extends LuxuryWork {
	/** The wine's purple, for the splashes. */
	private static final DustParticleOptions SPLASH = new DustParticleOptions(new Vector3f(0.45f, 0.08f, 0.35f), 1.0f);
	/** How many times a Vintner has pressed at their vat since the server started (tests read it). */
	public static int pressings;

	public VintnerWork() {
		super("vintner");
	}

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new VintnerWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return Vintners.ENABLED && super.checkExtraStartConditions(level, villager);
	}

	@Override
	protected void workEffects(ServerLevel level, BlockPos station) {
		pressings++;
		level.playSound(null, station, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.6f, 0.8f + level.random.nextFloat() * 0.2f);
		level.sendParticles(SPLASH, station.getX() + 0.5, station.getY() + 1.0, station.getZ() + 0.5, 6, 0.25, 0.15, 0.25, 0.0);
	}
}
