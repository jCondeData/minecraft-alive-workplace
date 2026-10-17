package io.github.jcondedata.aliveworkplace.work;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The bucket brigade (ROADMAP 32.16): a villager a Journeyman firewarden's bell called off their day
 * ({@link VillageFire#ring}) runs to the nearest fire of that village and puts out a fire block every
 * {@link VillageFire#BRIGADE_EVERY} ticks until the fire is over. In every villager's CORE activity; it does nothing
 * for anyone not called.
 */
public class FireBrigade extends Behavior<Villager> {
	private static final float SPEED = 0.7f;

	private static final class State {
		final Walker walker = new Walker(SPEED);
		BlockPos fire;
		long next;
	}

	private static final Map<Villager, State> STATES = new WeakHashMap<>();

	public FireBrigade() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 2400);
	}

	private static State state(Villager villager) {
		synchronized (STATES) {
			return STATES.computeIfAbsent(villager, v -> new State());
		}
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		boolean called = VillageFire.brigadeOf(villager).isPresent();
		if (!called && ModAttachments.BRIGADE_HELD.has(villager)) {
			giveBack(villager); // the world was saved mid-call and the fire was over by the time it loaded
		}
		return called && target(level, villager);
	}

	/**
	 * Puts what the villager held before the call back in their hand in place of the bucket (a hired fisher's rod, a
	 * tool: B152). If something else is in their hand by now, the kept item drops at their feet rather than vanish.
	 */
	static void giveBack(Villager villager) {
		ItemStack held = ModAttachments.BRIGADE_HELD.get(villager);
		ModAttachments.BRIGADE_HELD.remove(villager);
		ItemStack hand = villager.getMainHandItem();
		boolean bucket = hand.is(Items.WATER_BUCKET);
		if (held == null || held.isEmpty()) {
			if (bucket) {
				villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
			return;
		}
		if (bucket || hand.isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, held);
		} else {
			villager.spawnAtLocation(held);
		}
	}

	private static boolean target(ServerLevel level, Villager villager) {
		Optional<BlockPos> hall = VillageFire.brigadeOf(villager);
		if (hall.isEmpty()) {
			return false;
		}
		State state = state(villager);
		state.fire = VillageFire.nearestFire(level, villager.blockPosition(), VillageFire.BRIGADE_REACH * 2).orElse(null);
		return state.fire != null;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		ItemStack hand = villager.getMainHandItem();
		if (!hand.isEmpty() && !(hand.is(Items.WATER_BUCKET) && ModAttachments.BRIGADE_HELD.has(villager))) {
			// Kept (and saved) to give back when the fire is out: the bucket used to replace it for good (B152).
			ModAttachments.BRIGADE_HELD.set(villager, hand.copy());
		}
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WATER_BUCKET));
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return VillageFire.brigadeOf(villager).isPresent() && state(villager).fire != null;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		State state = state(villager);
		if (state.fire == null || !level.getBlockState(state.fire).is(net.minecraft.tags.BlockTags.FIRE) || gameTime % 20 == 0) {
			if (!target(level, villager)) {
				return;
			}
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(state.fire));
		if (state.walker.reach(level, villager, state.fire, FirewardenWork.THROW) && gameTime >= state.next) {
			state.next = gameTime + VillageFire.BRIGADE_EVERY;
			villager.swing(InteractionHand.MAIN_HAND, true);
			level.playSound(null, villager, SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.8f, 1.1f);
			VillageFire.douse(level, state.fire, 0, null);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		giveBack(villager);
		State state = state(villager);
		state.walker.reset();
		state.fire = null;
		if (VillageFire.brigadeOf(villager).isEmpty()) {
			VillageFire.release(villager);
		}
	}
}
