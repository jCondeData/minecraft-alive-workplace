package io.github.jcondedata.aliveworkplace.nether;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Pace;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * A Netherworker's shift at the Nether Brazier: with {@link Netherworkers#RATIONS} rations in the chests by the brazier
 * (and whatever gear is there: a pickaxe, an axe, a sword, a chestplate, a fire resistance potion), they pack, walk to
 * the Nether portal nearby and step through ({@link Netherworkers#setOut}); back from the trip, they put what they found
 * (and their gear) in the chests, and rest a while before the next one.
 */
public class NetherworkerWork extends Behavior<Villager> {
	/** The rest between expeditions. */
	public static int REST_TICKS = 1200;

	/** Ticks this netherworker rests after a trip: {@link #REST_TICKS} at their pace (partners, Expeditions...). */
	public static int restTicks(Villager villager) {
		return Pace.ticks(REST_TICKS, villager);
	}
	private static final int LOOK_EVERY = 100;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;

	private enum Phase { IDLE, PACKING, TO_PORTAL }

	private final Walker walker = new Walker(SPEED);
	private Phase phase = Phase.IDLE;
	private BlockPos portal;
	private int lookTimer;
	private long restUntil;

	public NetherworkerWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	/** The Netherworker's WORK activity. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new NetherworkerWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Netherworkers.isNetherworker(villager) && !Netherworkers.isAway(villager)
			&& Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		lookTimer = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		phase = Phase.IDLE; // what's in the bag is put away next shift
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos brazier = Builders.benchPos(villager).orElse(null);
		if (brazier == null) {
			return;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		switch (phase) {
			case PACKING -> pack(level, villager, brazier, bag);
			case TO_PORTAL -> toPortal(level, villager, bag, gameTime);
			default -> {
				if (!bag.isEmpty()) {
					unpack(level, villager, brazier, bag, gameTime);
				} else {
					idle(level, villager, brazier, gameTime);
				}
			}
		}
	}

	/** At the brazier: when rested, with a portal near and food in the chests, get ready to go. */
	private void idle(ServerLevel level, Villager villager, BlockPos brazier, long now) {
		if (--lookTimer > 0) {
			return;
		}
		lookTimer = LOOK_EVERY;
		if (now < restUntil) {
			status(villager, "resting", false);
			return;
		}
		if (io.github.jcondedata.aliveworkplace.hall.Curfew.noTrips(villager)) {
			status(villager, "curfew", false); // home by dusk (30.9)
			return;
		}
		portal = Netherworkers.portalNear(level, brazier);
		if (portal == null) {
			status(villager, "no_portal", true);
			return;
		}
		List<BlockPos> own = SupplyContainers.find(level, brazier, null);
		long rations = 0;
		for (BlockPos chest : own) {
			for (ItemStack stack : SupplyContainers.peekMatching(level, chest, Netherworkers::isRation)) {
				rations += stack.getCount();
			}
		}
		if (rations < Netherworkers.RATIONS) {
			status(villager, "needs_food", true, Netherworkers.RATIONS);
			Requests.post(villager, new ItemStack(Items.BREAD), Netherworkers.RATIONS, Items.BREAD.getDescription(), Netherworkers::isRation);
			return;
		}
		Requests.clear(villager);
		phase = Phase.PACKING;
		walker.reset();
	}

	/** At the chests: the rations and one of each kind of gear. */
	private void pack(ServerLevel level, Villager villager, BlockPos brazier, BuilderBag bag) {
		status(villager, "packing", false);
		List<BlockPos> own = SupplyContainers.find(level, brazier, null);
		BlockPos chest = SupplyContainers.firstMatching(level, own, Netherworkers::isRation);
		if (chest == null) {
			phase = Phase.IDLE;
			return;
		}
		if (!walker.walkTo(level, villager, chest, REACH)) {
			return;
		}
		int packed = 0;
		while (packed < Netherworkers.RATIONS) {
			ItemStack food = SupplyContainers.takeOne(level, own, Netherworkers::isRation);
			if (food.isEmpty()) {
				break;
			}
			bag.add(food);
			packed++;
		}
		for (Predicate<ItemStack> gear : List.<Predicate<ItemStack>>of(Netherworkers::isPickaxe, Netherworkers::isAxe, Netherworkers::isSword,
				Netherworkers::isArmor, Netherworkers::isFireResistance)) {
			ItemStack taken = SupplyContainers.takeOne(level, own, gear);
			if (!taken.isEmpty()) {
				bag.add(taken);
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		phase = packed >= Netherworkers.RATIONS ? Phase.TO_PORTAL : Phase.IDLE; // (IDLE: put it all back)
		walker.reset();
	}

	/** To the portal, and through it. */
	private void toPortal(ServerLevel level, Villager villager, BuilderBag bag, long now) {
		status(villager, "to_portal", false);
		if (portal == null || !level.getBlockState(portal).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) {
			phase = Phase.IDLE; // the portal's gone out: unpack
			return;
		}
		if (!walker.reach(level, villager, portal, 2.5)) {
			if (walker.noSpot()) {
				phase = Phase.IDLE;
			}
			return;
		}
		phase = Phase.IDLE;
		if (!Netherworkers.setOut(level, villager, portal)) {
			return;
		}
		restUntil = 0;
	}

	/** Back at the chests: everything in the bag goes in them (the finds, and the gear for next time). */
	private void unpack(ServerLevel level, Villager villager, BlockPos brazier, BuilderBag bag, long now) {
		status(villager, "unpacking", false);
		List<BlockPos> own = SupplyContainers.find(level, brazier, null);
		BlockPos target = own.isEmpty() ? brazier : own.get(0);
		if (!walker.walkTo(level, villager, target, REACH)) {
			return;
		}
		boolean back = bag.stacks().stream().anyMatch(s -> !Netherworkers.isRation(s) && !Netherworkers.isGear(s));
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = own.isEmpty() ? stack : SupplyContainers.insert(level, own, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, brazier.above(), rest);
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, target, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.0f);
		if (back) {
			restUntil = now + restTicks(villager);
		}
		lookTimer = 0;
		walker.reset();
	}

	private static void status(Villager villager, String state, boolean warn, Object... args) {
		WorkerStatus.set(villager, Netherworkers.title(villager), -1f, Component.translatable("message.aliveworkplace.netherworker.state." + state, args)
			.withStyle(warn ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
