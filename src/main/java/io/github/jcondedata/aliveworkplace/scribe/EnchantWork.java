package io.github.jcondedata.aliveworkplace.scribe;

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
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A Librarian with an Enchanting Table near their lectern enchants the village's gear: a guard's weapon, bow and armor,
 * a miner's pickaxe, a lumberjack's axe — whatever a worker has in hand or on that isn't enchanted yet — walking up to
 * them with the lapis from their chests (or the storehouse). How strong the enchantment is goes by the librarian's
 * level: {@link #BASE_LEVEL} plus {@link #PER_LEVEL} per level (a Master enchants like a full enchanting table).
 */
public class EnchantWork extends Behavior<Villager> {
	static final int BASE_LEVEL = 5;
	static final int PER_LEVEL = 5;
	private static final float SPEED = 0.55f;
	private static final double REACH = 2.5;
	private static final int LOOK_EVERY = 200;

	private record Job(Villager worker, EquipmentSlot slot) {
	}

	private static final Set<Villager> BUSY = Collections.newSetFromMap(new WeakHashMap<>());

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private Job job;
	private int lookTimer;

	public EnchantWork() {
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

	/** Lapis an enchantment of this strength takes (an enchanting table's 1–3). */
	static int lapisFor(int strength) {
		return Math.max(1, Math.min(3, (strength + 9) / 10));
	}

	/** How strong this librarian's enchantments are. */
	static int strength(Villager villager) {
		return BASE_LEVEL + PER_LEVEL * BuilderLevels.level(villager);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN
			&& Builders.benchPos(villager).isPresent() && (isBusy(villager) || !CrafterWork.isBusy(villager));
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
		busy(villager, false);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (station == null) {
			return;
		}
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		if (job == null) {
			busy(villager, false);
			if (--lookTimer > 0) {
				return;
			}
			lookTimer = LOOK_EVERY;
			if (!hasTable(level, station) || Village.RADIUS <= 0) {
				return;
			}
			job = choose(level, villager, station);
			if (job == null) {
				return;
			}
			walker.reset();
		}
		busy(villager, true);
		int lapis = lapisFor(strength(villager));
		if (bag.count(Items.LAPIS_LAZULI) < lapis) {
			status(villager, "fetching");
			List<BlockPos> sources = sources(level, villager, station);
			BlockPos chest = SupplyContainers.firstWith(level, sources, Items.LAPIS_LAZULI);
			if (chest == null) {
				job = null;
				return;
			}
			if (walker.walkTo(level, villager, chest, 3.0)) {
				int got = SupplyContainers.extract(level, List.of(chest), Items.LAPIS_LAZULI, lapis - bag.count(Items.LAPIS_LAZULI));
				bag.addAll(Items.LAPIS_LAZULI, got);
				walker.reset();
			}
			return;
		}
		Villager worker = job.worker();
		ItemStack gear = worker.getItemBySlot(job.slot());
		if (!worker.isAlive() || !enchantable(gear)) {
			job = null;
			return;
		}
		status(villager, "enchanting");
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(worker, true));
		if (villager.distanceToSqr(worker) > REACH * REACH) {
			walker.walkTo(level, villager, worker.blockPosition(), REACH - 0.5);
			return;
		}
		var tag = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE);
		net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(level.random, gear, strength(villager), level.registryAccess(), tag);
		worker.setItemSlot(job.slot(), gear);
		bag.remove(Items.LAPIS_LAZULI, lapis);
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, worker.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1f, 1f);
		level.sendParticles(ParticleTypes.ENCHANT, worker.getX(), worker.getY() + 1.2, worker.getZ(), 30, 0.4, 0.6, 0.4, 0.5);
		villager.setAttached(ModAttachments.ITEMS_ENCHANTED, villager.getAttachedOrElse(ModAttachments.ITEMS_ENCHANTED, 0) + 1);
		BuilderLevels.addXp(level, villager, 3, null);
		job = null;
		lookTimer = 40;
		walker.reset();
	}

	/** An Enchanting Table near the lectern. */
	static boolean hasTable(ServerLevel level, BlockPos station) {
		int r = SupplyContainers.RADIUS;
		for (BlockPos p : BlockPos.betweenClosed(station.offset(-r, -2, -r), station.offset(r, 2, r))) {
			if (level.getBlockState(p).is(Blocks.ENCHANTING_TABLE)) {
				return true;
			}
		}
		return false;
	}

	/** Gear worth enchanting: something enchantable that isn't enchanted yet. */
	static boolean enchantable(ItemStack stack) {
		return !stack.isEmpty() && stack.isEnchantable() && !stack.isEnchanted();
	}

	/** Where the lapis comes from: our chests, then the storehouse. */
	private static List<BlockPos> sources(ServerLevel level, Villager villager, BlockPos station) {
		List<BlockPos> out = new ArrayList<>(SupplyContainers.find(level, station, null));
		for (Village.Stash stash : Village.stashes(level, villager, station, null)) {
			if (stash.job() == ModVillagers.PORTER) {
				out.addAll(stash.chests());
			}
		}
		return out;
	}

	/** Guards first (weapon, armor, bow), then the other workers' tools in hand; null if there's no lapis or nothing to do. */
	@Nullable
	private static Job choose(ServerLevel level, Villager villager, BlockPos station) {
		if (SupplyContainers.firstWith(level, sources(level, villager, station), Items.LAPIS_LAZULI) == null) {
			return null;
		}
		var boss = villager.getAttached(ModAttachments.BUILDER_EMPLOYER);
		List<Villager> near = level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(Village.RADIUS),
			v -> v != villager && v.isAlive() && !v.isBaby() && Village.sameSide(level, v.getAttached(ModAttachments.BUILDER_EMPLOYER), boss));
		for (Villager guard : near) {
			if (!Guards.isGuard(guard)) {
				continue;
			}
			for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS,
					EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) {
				if (enchantable(guard.getItemBySlot(slot))) {
					return new Job(guard, slot);
				}
			}
		}
		for (Villager worker : near) {
			if (Village.takesPart(worker) && enchantable(worker.getItemBySlot(EquipmentSlot.MAINHAND))) {
				return new Job(worker, EquipmentSlot.MAINHAND);
			}
		}
		return null;
	}

	private static void status(Villager villager, String state) {
		Component title = Component.translatable("message.aliveworkplace.scribe.title", villager.getAttachedOrElse(ModAttachments.ITEMS_ENCHANTED, 0));
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.scribe.state." + state).withStyle(ChatFormatting.GRAY));
	}
}
