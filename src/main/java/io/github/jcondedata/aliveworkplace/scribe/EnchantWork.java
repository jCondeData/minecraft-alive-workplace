package io.github.jcondedata.aliveworkplace.scribe;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.craft.CrafterWork;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
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
 * level: {@link #BASE_LEVEL} plus {@link #PER_LEVEL} per level (a Master enchants like a full enchanting table), and one
 * more for every {@link #SHELVES_PER_LEVEL} bookshelves round the table, placed the way a player's table wants them (up
 * to {@link #MAX_STRENGTH}): a Library III's ring of fifteen gives a Novice the power of a Journeyman.
 */
public class EnchantWork extends Behavior<Villager> {
	static final int BASE_LEVEL = 5;
	static final int PER_LEVEL = 5;
	static final int SHELVES_PER_LEVEL = 3;
	static final int MAX_STRENGTH = 30;
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

	/** How strong this librarian's enchantments are, with {@code shelves} bookshelves round the table. */
	public static int strength(Villager villager, int shelves) {
		return strength(BuilderLevels.level(villager), shelves);
	}

	static int strength(int level, int shelves) {
		return Math.min(MAX_STRENGTH, BASE_LEVEL + PER_LEVEL * level + Math.min(15, shelves) / SHELVES_PER_LEVEL);
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
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		if (job == null) {
			busy(villager, false);
			if (--lookTimer > 0) {
				return;
			}
			lookTimer = LOOK_EVERY;
			if (table(level, station) == null || Village.RADIUS <= 0) {
				return;
			}
			job = choose(level, villager, station);
			if (job == null) {
				return;
			}
			walker.reset();
		}
		busy(villager, true);
		BlockPos table = table(level, station);
		if (table == null) {
			job = null;
			return;
		}
		int strength = strength(villager, shelves(level, table));
		int lapis = lapisFor(strength);
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
		var tag = Lookup.tag(Lookup.registry(level.registryAccess(), Registries.ENCHANTMENT), EnchantmentTags.IN_ENCHANTING_TABLE);
		net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(level.random, gear, strength, level.registryAccess(), tag);
		worker.setItemSlot(job.slot(), gear);
		bag.remove(Items.LAPIS_LAZULI, lapis);
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, worker.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1f, 1f);
		level.sendParticles(ParticleTypes.ENCHANT, worker.getX(), worker.getY() + 1.2, worker.getZ(), 30, 0.4, 0.6, 0.4, 0.5);
		ModAttachments.ITEMS_ENCHANTED.set(villager, ModAttachments.ITEMS_ENCHANTED.getOrElse(villager, 0) + 1);
		BuilderLevels.addXp(level, villager, 3, null);
		job = null;
		lookTimer = 40;
		walker.reset();
	}

	/** The Enchanting Table near the lectern (the one with the most bookshelves), or null. */
	@Nullable
	public static BlockPos table(ServerLevel level, BlockPos station) {
		int r = SupplyContainers.RADIUS;
		BlockPos best = null;
		int bestShelves = -1;
		for (BlockPos p : BlockPos.betweenClosed(station.offset(-r, -2, -r), station.offset(r, 2, r))) {
			if (level.getBlockState(p).is(Blocks.ENCHANTING_TABLE)) {
				int shelves = shelves(level, p);
				if (shelves > bestShelves) {
					best = p.immutable();
					bestShelves = shelves;
				}
			}
		}
		return best;
	}

	/** Bookshelves powering the table at {@code table}, counted the way a player's table counts them. */
	public static int shelves(ServerLevel level, BlockPos table) {
		int n = 0;
		for (BlockPos offset : net.minecraft.world.level.block.EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			if (net.minecraft.world.level.block.EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
				n++;
			}
		}
		return n;
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
		var boss = ModAttachments.BUILDER_EMPLOYER.get(villager);
		List<Villager> near = level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(Village.RADIUS),
			v -> v != villager && v.isAlive() && !v.isBaby() && Village.sameSide(level, ModAttachments.BUILDER_EMPLOYER.get(v), boss));
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
		Component title = Component.translatable("message.aliveworkplace.scribe.title", ModAttachments.ITEMS_ENCHANTED.getOrElse(villager, 0));
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.scribe.state." + state).withStyle(ChatFormatting.GRAY));
	}
}
