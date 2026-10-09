package io.github.jcondedata.aliveworkplace.tailor;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.craft.LuxuryWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Tailor's shift (ROADMAP 34.10): the luxury workshop engine at the loom. While sewing, the loom clacks and snips
 * of thread fly off the cloth. Nothing at all with config {@code tailors} off.
 */
public class TailorWork extends LuxuryWork {
	/** How many times a Tailor has worked the loom since the server started (tests read it). */
	public static int stitches;

	public TailorWork() {
		super("tailor");
	}

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new TailorWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return Tailors.ENABLED && super.checkExtraStartConditions(level, villager);
	}

	@Override
	protected void workEffects(ServerLevel level, BlockPos station) {
		stitches++;
		level.playSound(null, station, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 0.5f, 0.9f + level.random.nextFloat() * 0.2f);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.STRING)), station.getX() + 0.5, station.getY() + 1.05,
			station.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0.02);
	}
}
