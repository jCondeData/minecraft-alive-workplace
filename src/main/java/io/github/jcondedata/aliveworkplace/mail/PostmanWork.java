package io.github.jcondedata.aliveworkplace.mail;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The postman's round: collect parcels from mailboxes near the Postal Desk, carry each to its
 * recipient's mailbox if that is on the round too, and hand the rest in at the desk, from where they
 * arrive at the next dawn.
 */
public class PostmanWork extends Behavior<Villager> {
	private static final float SPEED = 0.6f;
	/** A pickup claim by a postman who never came is dropped after this long. */
	private static final long CLAIM_TIMEOUT = 2400;
	private static final int DELIVERIES_PER_XP = 2;

	private enum Mode { PICKUP, DELIVER, HAND_IN }

	/** Postmen with a parcel to see to (the idle stroll only runs for the others). */
	private static final Set<Villager> BUSY = Collections.newSetFromMap(new WeakHashMap<>());

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private UUID parcelId;
	private Mode mode = Mode.PICKUP;
	@Nullable
	private BlockPos target;
	private int searchTimer;

	/** A courier run in progress (only when there's no mail to see to). */
	private enum HaulPhase { PICKUP, DROP, RETURN }

	@Nullable
	private RouteData haul;
	private HaulPhase haulPhase = HaulPhase.PICKUP;
	private int nextRoute;

	public PostmanWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isBusy(Villager villager) {
		return BUSY.contains(villager);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		parcelId = null;
		searchTimer = 0;
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		BUSY.remove(villager);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos desk = Builders.benchPos(villager).orElse(null);
		if (desk == null) {
			return;
		}
		PostOffice office = PostOffice.get(level.getServer());
		office.deskWorked(GlobalPos.of(level.dimension(), desk), gameTime);

		Parcel parcel = parcelId == null ? null : office.parcel(parcelId);
		if (parcel == null || !stillMine(parcel, villager)) {
			parcelId = null;
			if (haul != null) {
				haulTick(level, villager);
				return;
			}
			if (--searchTimer > 0) {
				idle(villager);
				return;
			}
			searchTimer = 40;
			if (!choose(level, villager, desk, office, gameTime)) {
				// No mail: run a courier route if one has something to carry.
				if (startHaul(level, villager, desk)) {
					haulTick(level, villager);
				} else {
					idle(villager);
				}
				return;
			}
			parcel = office.parcel(parcelId);
			if (parcel == null) {
				return;
			}
		}
		BUSY.add(villager);
		status(villager, mode, parcel);
		if (!walker.walkTo(level, villager, target, 2.5)) {
			return;
		}
		villager.swing(InteractionHand.MAIN_HAND);
		switch (mode) {
			case PICKUP -> {
				parcel.setStatus(Parcel.Status.CARRIED);
				parcel.claim(villager.getUUID(), gameTime);
				level.playSound(null, target, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1f, 1.2f);
			}
			case DELIVER -> {
				if (office.deliver(level.getServer(), parcel)) {
					office.remove(parcel);
					level.playSound(null, target, SoundEvents.BOOK_PUT, SoundSource.NEUTRAL, 1f, 1f);
					int delivered = villager.getAttachedOrElse(ModAttachments.MAIL_DELIVERED, 0) + 1;
					villager.setAttached(ModAttachments.MAIL_DELIVERED, delivered);
					if (delivered % DELIVERIES_PER_XP == 0) {
						BuilderLevels.addXp(level, villager, 1, null);
					}
				} else {
					// Mailbox full or gone: it waits at the post office and goes out with the dawn mail.
					parcel.setStatus(Parcel.Status.IN_TRANSIT);
					parcel.claim(null, gameTime);
				}
			}
			case HAND_IN -> {
				if (!io.github.jcondedata.aliveworkplace.work.Partners.helpers(villager).isEmpty() && office.deliver(level.getServer(), parcel)) {
					// Air mail: a Flying-type partner by the desk takes it there straight away.
					office.remove(parcel);
					level.playSound(null, target, SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 0.8f, 1.4f);
					villager.setAttached(ModAttachments.MAIL_DELIVERED, villager.getAttachedOrElse(ModAttachments.MAIL_DELIVERED, 0) + 1);
				} else {
					parcel.setStatus(Parcel.Status.IN_TRANSIT);
					parcel.claim(null, gameTime);
					level.playSound(null, target, SoundEvents.BOOK_PUT, SoundSource.NEUTRAL, 1f, 0.9f);
				}
			}
		}
		office.changed();
		parcelId = null;
		searchTimer = 0;
		holdLetters(villager, office);
	}

	// --- courier routes ---------------------------------------------------------------------------

	private boolean startHaul(ServerLevel level, Villager villager, BlockPos desk) {
		java.util.List<RouteData> routes = Postmen.routes(villager);
		for (int i = 0; i < routes.size(); i++) {
			RouteData route = routes.get((nextRoute + i) % routes.size());
			if (!route.isComplete() || !route.from().get().closerThan(desk, PostOffice.ROUND) || !route.to().get().closerThan(desk, PostOffice.ROUND)) {
				continue;
			}
			if (io.github.jcondedata.aliveworkplace.work.Pastures.containers(level, route.from().get()).stream()
				.anyMatch(c -> io.github.jcondedata.aliveworkplace.build.SupplyContainers.hasMatching(level, c, route::carries))) {
				nextRoute = (nextRoute + i + 1) % routes.size();
				haul = route;
				haulPhase = HaulPhase.PICKUP;
				walker.reset();
				return true;
			}
		}
		return false;
	}

	private void haulTick(ServerLevel level, Villager villager) {
		RouteData route = haul;
		if (route == null) {
			return;
		}
		BUSY.add(villager);
		io.github.jcondedata.aliveworkplace.build.BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		BlockPos from = route.from().get();
		BlockPos to = route.to().get();
		// A pasture stands for the chests around it (Cobbleworkers' Pokémon fill those): go to the one in use.
		java.util.List<BlockPos> sources = io.github.jcondedata.aliveworkplace.work.Pastures.containers(level, from);
		java.util.List<BlockPos> targets = io.github.jcondedata.aliveworkplace.work.Pastures.containers(level, to);
		BlockPos target = switch (haulPhase) {
			case PICKUP -> sources.stream().filter(c -> io.github.jcondedata.aliveworkplace.build.SupplyContainers.hasMatching(level, c, route::carries))
				.findFirst().orElse(from);
			case DROP -> targets.isEmpty() ? to : targets.get(0);
			case RETURN -> sources.isEmpty() ? from : sources.get(0);
		};
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.postman.title", villager.getAttachedOrElse(ModAttachments.MAIL_DELIVERED, 0)), -1f,
			Component.translatable("message.aliveworkplace.postman.state.haul", to.getX(), to.getY(), to.getZ()).withStyle(ChatFormatting.GRAY));
		if (!walker.walkTo(level, villager, target, 3.0)) {
			if (walker.noSpot()) {
				haul = null;
			}
			return;
		}
		villager.swing(InteractionHand.MAIN_HAND);
		switch (haulPhase) {
			case PICKUP -> {
				java.util.List<ItemStack> taken = new java.util.ArrayList<>();
				for (BlockPos source : sources) {
					int room = Math.max(1, bag.freeSlots() - 1) - taken.size();
					if (room <= 0) {
						break;
					}
					taken.addAll(io.github.jcondedata.aliveworkplace.build.SupplyContainers.takeMatching(level, source, route::carries, room));
				}
				for (ItemStack stack : taken) {
					ItemStack rest = bag.add(stack);
					if (!rest.isEmpty()) {
						io.github.jcondedata.aliveworkplace.build.SupplyContainers.insert(level, sources, rest);
					}
				}
				level.playSound(null, target, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
				haulPhase = HaulPhase.DROP;
				if (taken.isEmpty()) {
					haul = null;
				}
			}
			case DROP -> {
				java.util.List<ItemStack> left = new java.util.ArrayList<>();
				for (ItemStack stack : bag.takeAll()) {
					ItemStack rest = io.github.jcondedata.aliveworkplace.build.SupplyContainers.insert(level, targets, stack);
					if (!rest.isEmpty()) {
						left.add(rest);
					}
				}
				level.playSound(null, target, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
				left.forEach(bag::add);
				if (left.isEmpty()) {
					haul = null;
					BuilderLevels.addXp(level, villager, 1, null);
				} else {
					haulPhase = HaulPhase.RETURN; // destination full: take the rest back
				}
			}
			case RETURN -> {
				for (ItemStack stack : bag.takeAll()) {
					ItemStack rest = io.github.jcondedata.aliveworkplace.build.SupplyContainers.insert(level, sources, stack);
					if (!rest.isEmpty()) {
						villager.spawnAtLocation(rest);
					}
				}
				haul = null;
			}
		}
		walker.reset();
		if (haul == null) {
			searchTimer = 100; // give the next run a moment
		}
	}

	private static boolean stillMine(Parcel parcel, Villager villager) {
		return parcel.status() != Parcel.Status.IN_TRANSIT && villager.getUUID().equals(parcel.carrier());
	}

	/** Picks the nearest thing to do on the round. */
	private boolean choose(ServerLevel level, Villager villager, BlockPos desk, PostOffice office, long gameTime) {
		double best = Double.MAX_VALUE;
		Parcel chosen = null;
		Mode chosenMode = null;
		BlockPos chosenTarget = null;
		UUID me = villager.getUUID();
		for (Parcel p : office.parcels()) {
			Mode m;
			BlockPos t;
			if (p.status() == Parcel.Status.CARRIED && me.equals(p.carrier())) {
				GlobalPos dest = office.mailboxOf(p.to());
				if (dest != null && dest.dimension() == level.dimension() && dest.pos().closerThan(desk, PostOffice.ROUND)) {
					m = Mode.DELIVER;
					t = dest.pos();
				} else {
					m = Mode.HAND_IN;
					t = desk;
				}
			} else if (p.status() == Parcel.Status.AWAITING_PICKUP && p.origin().dimension() == level.dimension()
				&& p.origin().pos().closerThan(desk, PostOffice.ROUND)
				&& (p.carrier() == null || me.equals(p.carrier()) || gameTime - p.claimedAt() > CLAIM_TIMEOUT)) {
				m = Mode.PICKUP;
				t = p.origin().pos();
			} else {
				continue;
			}
			double d = t.distSqr(villager.blockPosition());
			if (d < best) {
				best = d;
				chosen = p;
				chosenMode = m;
				chosenTarget = t;
			}
		}
		if (chosen == null) {
			return false;
		}
		if (chosenMode == Mode.PICKUP) {
			chosen.claim(me, gameTime);
			office.changed();
		}
		parcelId = chosen.id();
		mode = chosenMode;
		target = chosenTarget;
		walker.reset();
		return true;
	}

	private static void idle(Villager villager) {
		BUSY.remove(villager);
		int delivered = villager.getAttachedOrElse(ModAttachments.MAIL_DELIVERED, 0);
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.postman.title", delivered), -1f,
			Component.translatable("message.aliveworkplace.postman.state.idle").withStyle(ChatFormatting.GRAY));
	}

	/** Postmen carry a letter in hand while they have mail on them. */
	private static void holdLetters(Villager villager, PostOffice office) {
		boolean carrying = office.parcels().stream().anyMatch(p -> p.status() == Parcel.Status.CARRIED && villager.getUUID().equals(p.carrier()));
		ItemStack hand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		if (carrying && hand.isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.PAPER));
		} else if (!carrying && hand.is(Items.PAPER)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	private static void status(Villager villager, Mode mode, Parcel parcel) {
		int delivered = villager.getAttachedOrElse(ModAttachments.MAIL_DELIVERED, 0);
		Component line = switch (mode) {
			case PICKUP -> Component.translatable("message.aliveworkplace.postman.state.pickup", parcel.fromName());
			case DELIVER -> Component.translatable("message.aliveworkplace.postman.state.deliver", parcel.toName());
			case HAND_IN -> Component.translatable("message.aliveworkplace.postman.state.hand_in", parcel.toName());
		};
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.postman.title", delivered), -1f,
			line.copy().withStyle(ChatFormatting.GRAY));
	}
}
