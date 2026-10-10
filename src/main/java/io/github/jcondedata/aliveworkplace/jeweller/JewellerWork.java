package io.github.jcondedata.aliveworkplace.jeweller;

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
 * The Jeweller's shift (ROADMAP 34.12): the luxury workshop engine at the stonecutter. While they cut and set a piece,
 * the stonecutter's blade rings and filings of gold fly off it. Nothing at all with config {@code jewellers} off.
 */
public class JewellerWork extends LuxuryWork {
	/** How many times a Jeweller has worked the stonecutter since the server started (tests read it). */
	public static int cuts;

	public JewellerWork() {
		super("jeweller");
	}

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new JewellerWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return Jewellers.ENABLED && super.checkExtraStartConditions(level, villager);
	}

	@Override
	protected void workEffects(ServerLevel level, BlockPos station) {
		cuts++;
		level.playSound(null, station, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.5f, 1.3f + level.random.nextFloat() * 0.2f);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), station.getX() + 0.5, station.getY() + 0.65,
			station.getZ() + 0.5, 3, 0.15, 0.05, 0.15, 0.02);
	}
}
