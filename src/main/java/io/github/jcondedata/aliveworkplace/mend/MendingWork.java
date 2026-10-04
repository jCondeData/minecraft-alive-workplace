package io.github.jcondedata.aliveworkplace.mend;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.craft.CrafterWork;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A Weaponsmith mending the village's worn gear at their grindstone, the way an anvil does: each ingot (plank, diamond,
 * leather... whatever the item is mended with) puts back a quarter of its durability. Worn tools, weapons and armor are
 * found in the chests by the grindstone (a player's drop-off), by the guards' posts and at the other workers; the
 * materials come from the grindstone's chests, the storehouse and the smelters. The piece goes back where it was found.
 */
public class MendingWork extends Behavior<Villager> {
	/** Worn: at least this share of the durability used up. */
	static final float WORN = 0.25f;
	/** Durability one unit of material puts back, as a share of the full amount (an anvil's rule). */
	static final float PER_UNIT = 0.25f;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;
	private static final int LOOK_EVERY = 100;
	private static final int MEND_TICKS = 40;

	private enum Phase { IDLE, FETCHING, MATERIAL, MENDING, RETURNING, TIDYING }

	/** Mending {@code item} (as found) from {@code from} with {@code units} of {@code material} out of {@code materialChest}. */
	private record Job(ItemStack item, BlockPos from, Item material, int units, BlockPos materialChest) {
	}

	private static final Set<Villager> BUSY = Collections.newSetFromMap(new WeakHashMap<>());

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private Job job;
	private Phase phase = Phase.IDLE;
	private int lookTimer;
	private int timer;

	public MendingWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isBusy(Villager villager) {
		synchronized (BUSY) {
			return BUSY.contains(villager);
		}
	}

	private static void busy(Villager villager, boolean busy) {
		synchronized (BUSY) {
			if (busy) {
				BUSY.add(villager);
			} else {
				BUSY.remove(villager);
			}
		}
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && !villager.isBaby() && Builders.benchPos(villager).isPresent()
			&& (isBusy(villager) || !CrafterWork.isBusy(villager));
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
		// The piece (if any) is in the bag: tidied into our chests next time if the job is gone by then.
		busy(villager, false);
	}

	/** Whether {@code stack} is worn enough to be worth mending. */
	static boolean isWorn(ItemStack stack) {
		return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() * WORN;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (station == null) {
			return;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		if (job == null) {
			if (!bag.isEmpty()) {
				tidy(level, villager, station, bag);
				return;
			}
			busy(villager, false);
			if (--lookTimer > 0) {
				return;
			}
			lookTimer = LOOK_EVERY;
			job = choose(level, villager, station);
			if (job == null) {
				return;
			}
			phase = Phase.FETCHING;
			walker.reset();
		}
		busy(villager, true);
		switch (phase) {
			case FETCHING -> fetch(level, villager, bag);
			case MATERIAL -> material(level, villager, bag);
			case MENDING -> mend(level, villager, station, bag);
			default -> giveBack(level, villager, bag);
		}
	}

	/** Takes the worn piece from where it was found. */
	private void fetch(ServerLevel level, Villager villager, BuilderBag bag) {
		status(villager, Phase.FETCHING);
		if (!walker.walkTo(level, villager, job.from(), REACH)) {
			return;
		}
		ItemStack found = SupplyContainers.takeOne(level, List.of(job.from()), s -> isWorn(s) && ItemStack.isSameItem(s, job.item()));
		if (found.isEmpty()) {
			job = null; // gone already
			return;
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, job.from(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		ItemStack rest = bag.add(found);
		if (!rest.isEmpty()) {
			SupplyContainers.insert(level, List.of(job.from()), rest); // no room to carry it
			job = null;
			return;
		}
		phase = Phase.MATERIAL;
		walker.reset();
	}

	/** Fetches the material to mend it with. */
	private void material(ServerLevel level, Villager villager, BuilderBag bag) {
		status(villager, Phase.MATERIAL);
		if (!walker.walkTo(level, villager, job.materialChest(), REACH)) {
			return;
		}
		int got = SupplyContainers.extract(level, List.of(job.materialChest()), job.material(), job.units());
		int over = bag.addAll(job.material(), got);
		if (over > 0) {
			SupplyContainers.insert(level, List.of(job.materialChest()), new ItemStack(job.material(), over));
		}
		villager.swing(InteractionHand.MAIN_HAND);
		phase = got - over > 0 ? Phase.MENDING : Phase.RETURNING;
		timer = -1;
		walker.reset();
	}

	/** At the grindstone: the material goes into the piece. */
	private void mend(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag) {
		status(villager, Phase.MENDING);
		if (!walker.walkTo(level, villager, station, 2.5)) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(station));
		if (timer < 0) {
			timer = Math.max(20, BuilderLevels.delay(MEND_TICKS, villager));
			// A Steel or Fighting partner holds the worn piece at the grindstone (ROADMAP 28.5).
			io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, "mend", station, job.item());
		}
		if (timer % 10 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, station, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5f, 0.9f + level.random.nextFloat() * 0.2f);
		}
		if (--timer > 0) {
			return;
		}
		int units = bag.remove(job.material(), job.units());
		ItemStack piece = bag.takeFirst(this::isThePiece);
		if (!piece.isEmpty() && units > 0) {
			int per = Math.max(1, (int) (piece.getMaxDamage() * PER_UNIT));
			piece.setDamageValue(Math.max(0, piece.getDamageValue() - units * per));
			level.playSound(null, station, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.1f);
			ModAttachments.ITEMS_MENDED.set(villager, ModAttachments.ITEMS_MENDED.getOrElse(villager, 0) + 1);
			BuilderLevels.addXp(level, villager, 2, null);
		}
		if (!piece.isEmpty()) {
			bag.add(piece);
		}
		phase = Phase.RETURNING;
		walker.reset();
	}

	/** Takes the piece back to where it came from. */
	private void giveBack(ServerLevel level, Villager villager, BuilderBag bag) {
		status(villager, Phase.RETURNING);
		if (!walker.walkTo(level, villager, job.from(), REACH)) {
			return;
		}
		ItemStack piece = bag.takeFirst(this::isThePiece);
		if (!piece.isEmpty()) {
			ItemStack rest = SupplyContainers.insert(level, List.of(job.from()), piece);
			if (!rest.isEmpty()) {
				ItemStack back = bag.add(rest); // no room: it goes home with us
				if (!back.isEmpty()) {
					Block.popResource(level, villager.blockPosition(), back);
				}
			}
			level.playSound(null, job.from(), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.0f);
		}
		job = null;
		lookTimer = 20;
		walker.reset();
	}

	/** The piece being mended, in the bag (never the material). */
	private boolean isThePiece(ItemStack stack) {
		return job != null && ItemStack.isSameItem(stack, job.item()) && stack.isDamageableItem();
	}

	/** Puts what's left in the bag in the chests by the grindstone. */
	private void tidy(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag) {
		status(villager, Phase.TIDYING);
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		BlockPos target = own.isEmpty() ? station : own.get(0);
		if (!walker.walkTo(level, villager, target, REACH)) {
			return;
		}
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = own.isEmpty() ? stack : SupplyContainers.insert(level, own, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, station.above(), rest);
			}
		}
		walker.reset();
	}

	/**
	 * The first worn piece there's material for: in the chests by the grindstone, then by the guards' posts, then at the
	 * other workers of the village. Null if there's nothing to mend (or nothing to mend it with).
	 */
	@Nullable
	private static Job choose(ServerLevel level, Villager villager, BlockPos station) {
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		List<BlockPos> materials = new ArrayList<>(own);
		Set<BlockPos> places = new LinkedHashSet<>(own);
		if (Village.RADIUS > 0) {
			var boss = ModAttachments.BUILDER_EMPLOYER.get(villager);
			for (Villager guard : level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(Village.RADIUS),
					v -> v.isAlive() && Guards.isGuard(v) && Village.sameSide(level, ModAttachments.BUILDER_EMPLOYER.get(v), boss))) {
				Builders.benchPos(guard).ifPresent(post -> places.addAll(SupplyContainers.find(level, post, null)));
			}
			for (Village.Stash stash : Village.stashes(level, villager, station, null)) {
				VillagerProfession job = stash.job();
				if (job == ModVillagers.PORTER || job == VillagerProfession.ARMORER) {
					materials.addAll(stash.chests());
				}
				places.addAll(stash.chests());
			}
		}
		Map<Item, Long> stock = null;
		for (BlockPos chest : places) {
			for (ItemStack worn : SupplyContainers.peekMatching(level, chest, MendingWork::isWorn)) {
				if (stock == null) {
					stock = SupplyContainers.contents(level, materials);
				}
				for (Map.Entry<Item, Long> e : stock.entrySet()) {
					if (!worn.getItem().isValidRepairItem(worn, new ItemStack(e.getKey()))) {
						continue;
					}
					int per = Math.max(1, (int) (worn.getMaxDamage() * PER_UNIT));
					int units = (int) Math.min(e.getValue(), Math.min(4, (worn.getDamageValue() + per - 1) / per));
					BlockPos from = SupplyContainers.firstWith(level, materials, e.getKey());
					if (units > 0 && from != null) {
						return new Job(worn.copy(), chest, e.getKey(), units, from);
					}
				}
			}
		}
		return null;
	}

	private void status(Villager villager, Phase phase) {
		Component title = Component.translatable("message.aliveworkplace.weaponsmith.title", ModAttachments.ITEMS_MENDED.getOrElse(villager, 0));
		Component line = job != null && phase != Phase.TIDYING
			? Component.translatable("message.aliveworkplace.weaponsmith.state." + phase.name().toLowerCase(), job.item().getHoverName())
			: Component.translatable("message.aliveworkplace.weaponsmith.state." + phase.name().toLowerCase());
		WorkerStatus.set(villager, title, -1f, line.copy().withStyle(ChatFormatting.GRAY));
	}
}
