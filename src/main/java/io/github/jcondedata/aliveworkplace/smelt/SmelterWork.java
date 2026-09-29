package io.github.jcondedata.aliveworkplace.smelt;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.work.Furnaces;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * An Armorer's shift as the village's smelter: keep the blast furnace (and any furnaces) by their workstation going with
 * the ore in the chests there — raw metal and ore blocks, by the common tags — fed with coal or charcoal, and take the
 * ingots out into those chests. When the chests run out of ore (or fuel), fetch more from the village: the storehouse, and
 * miners and other workers who don't smelt their own (see {@link Porters#keeps}). A porter carries the ingots on to the
 * storehouse. And a guard of the village with nothing in an armor slot (and nothing for it in their chests) gets a piece:
 * one from the chests, or iron made into one with the game's recipe, brought to the chests by their Guard Post.
 * Restartable at any tick: what's being carried is in the bag, and a full bag is emptied at home first.
 */
public class SmelterWork extends Behavior<Villager> {
	/** Stacks of ore fetched in one trip by a novice; one more per level and per Pokémon partner. */
	static final int BASE_STACKS = 3;
	/** Coal or charcoal fetched at a time. */
	static final int FUEL_TRIP = 32;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;
	private static final int LOOK_EVERY = 60;

	private enum Phase { IDLE, SMELTING, FETCHING_ORE, FETCHING_FUEL, STORING, MAKING_ARMOR, DELIVERING_ARMOR, NO_CHEST }

	/** Smelters with work in hand right now (vanilla's armorer routine waits). */
	private static final Set<Villager> BUSY = Collections.newSetFromMap(new WeakHashMap<>());

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private Village.Stash target;
	private boolean fuelRun;
	private Phase phase = Phase.IDLE;
	private int lookTimer;
	@Nullable
	private VillagerProfession from;
	@Nullable
	private ArmorJob armor;

	/** A piece of armor for a guard who has none in that slot: {@code piece} is taken (or made) at home, then delivered. */
	private record ArmorJob(Villager guard, EquipmentSlot slot, Item piece, List<BlockPos> guardChests) {
	}

	public SmelterWork() {
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
		return !villager.isSleeping() && Smelters.isSmelter(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		target = null;
		armor = null;
		lookTimer = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		busy(villager, false);
		target = null;
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (station == null) {
			return;
		}
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		if (own.isEmpty()) {
			busy(villager, false);
			status(villager, Phase.NO_CHEST);
			return;
		}
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		if (armor != null) {
			armor(level, villager, own, bag);
			return;
		}
		// Carrying something: home with it first.
		if (target == null && !bag.isEmpty()) {
			busy(villager, true);
			status(villager, Phase.STORING);
			if (walker.walkTo(level, villager, own.get(0), REACH)) {
				for (ItemStack stack : bag.takeAll()) {
					ItemStack rest = SupplyContainers.insert(level, own, stack);
					if (!rest.isEmpty()) {
						Block.popResource(level, villager.blockPosition(), rest);
					}
				}
				villager.swing(InteractionHand.MAIN_HAND);
				level.playSound(null, own.get(0), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.0f);
				walker.reset();
				lookTimer = 0;
			}
			return;
		}
		// Fetching from a village-mate.
		if (target != null) {
			status(villager, fuelRun ? Phase.FETCHING_FUEL : Phase.FETCHING_ORE);
			if (walker.walkTo(level, villager, target.chests().get(0), REACH)) {
				int got = fuelRun ? take(level, target, bag, Smelters::isFuel, FUEL_TRIP)
					: take(level, target, bag, Furnaces::isOre, stacks(villager) * 64);
				if (got > 0) {
					villager.swing(InteractionHand.MAIN_HAND);
					level.playSound(null, target.chests().get(0), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
				}
				target = null;
				walker.reset();
			}
			return;
		}
		if (phase == Phase.SMELTING) {
			// At the furnaces.
			status(villager, Phase.SMELTING);
			if (walker.walkTo(level, villager, station, REACH)) {
				int ingots = waiting(level, station);
				Furnaces.tend(level, station, own, Furnaces::isOre);
				int collected = ingots - waiting(level, station);
				if (collected > 0) {
					villager.swing(InteractionHand.MAIN_HAND);
					level.playSound(null, station, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8f, 1.0f);
					villager.setAttached(ModAttachments.INGOTS_SMELTED, villager.getAttachedOrElse(ModAttachments.INGOTS_SMELTED, 0) + collected);
					BuilderLevels.addXp(level, villager, Math.max(1, collected / 8), null);
				}
				phase = Phase.IDLE;
				walker.reset();
			}
			return;
		}
		status(villager, phase);
		if (--lookTimer > 0) {
			return;
		}
		lookTimer = LOOK_EVERY;
		boolean oreHere = SupplyContainers.firstMatching(level, own, s -> Furnaces.isOre(s.getItem())) != null;
		boolean smelting = anySmelting(level, station);
		if (oreHere || smelting || waiting(level, station) > 0) {
			boolean fuelHere = SupplyContainers.firstMatching(level, own, s -> Smelters.isFuel(s.getItem())) != null;
			if (oreHere && !fuelHere && !fuelled(level, station)) {
				target = find(level, villager, station, Smelters::isFuel);
				if (target != null) {
					fuelRun = true;
					from = target.job();
					busy(villager, true);
					return;
				}
			}
			phase = Phase.SMELTING;
			busy(villager, true);
			return;
		}
		// A guard without armor: make them a piece.
		armor = armorJob(level, villager, station, own);
		if (armor != null) {
			busy(villager, true);
			walker.reset();
			return;
		}
		// Nothing to smelt here: fetch ore from the village.
		target = find(level, villager, station, Furnaces::isOre);
		if (target != null) {
			fuelRun = false;
			from = target.job();
			busy(villager, true);
			return;
		}
		phase = Phase.IDLE;
		busy(villager, false);
	}

	/** Armor slots in the order a smelter fills them: the chestplate protects the most. */
	private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	/** The iron piece a smelter makes for each slot. */
	static Item ironPiece(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> Items.IRON_HELMET;
			case CHEST -> Items.IRON_CHESTPLATE;
			case LEGS -> Items.IRON_LEGGINGS;
			default -> Items.IRON_BOOTS;
		};
	}

	/**
	 * A guard of the village (one this smelter shares with) with nothing in an armor slot and nothing for it in their
	 * chests, that the smelter has a piece for — in the chests already, or iron enough to make one. Null if none.
	 */
	@Nullable
	private static ArmorJob armorJob(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own) {
		if (Village.RADIUS <= 0) {
			return null;
		}
		Map<Item, Long> stock = null;
		var boss = villager.getAttached(ModAttachments.BUILDER_EMPLOYER);
		for (Villager guard : level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(Village.RADIUS),
				v -> v.isAlive() && Guards.isGuard(v) && Village.sameSide(level, v.getAttached(ModAttachments.BUILDER_EMPLOYER), boss))) {
			BlockPos post = Builders.benchPos(guard).orElse(null);
			if (post == null) {
				continue;
			}
			List<BlockPos> theirs = SupplyContainers.find(level, post, null);
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				if (!guard.getItemBySlot(slot).isEmpty()
						|| SupplyContainers.firstMatching(level, theirs, s -> s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot) != null) {
					continue;
				}
				BlockPos ready = SupplyContainers.firstMatching(level, own, s -> s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot);
				if (ready != null) {
					for (ItemStack stack : SupplyContainers.peekMatching(level, ready, s -> s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot)) {
						return new ArmorJob(guard, slot, stack.getItem(), theirs);
					}
				}
				if (stock == null) {
					stock = SupplyContainers.contents(level, own);
				}
				if (Crafting.plan(level, Crafting.Kind.CRAFTING, ironPiece(slot), 1, stock) != null) {
					return new ArmorJob(guard, slot, ironPiece(slot), theirs);
				}
			}
		}
		return null;
	}

	/** Takes (or makes) the piece at home, then brings it to the guard's chests (or the guard, with no chests). */
	private void armor(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		ArmorJob job = armor;
		if (!job.guard().isAlive()) {
			armor = null;
			return;
		}
		if (bag.count(job.piece()) == 0) {
			status(villager, Phase.MAKING_ARMOR);
			if (!walker.walkTo(level, villager, own.get(0), REACH)) {
				return;
			}
			ItemStack piece = SupplyContainers.takeOne(level, own, s -> s.is(job.piece()));
			if (piece.isEmpty()) {
				Crafting.Plan plan = Crafting.plan(level, Crafting.Kind.CRAFTING, job.piece(), 1, SupplyContainers.contents(level, own));
				if (plan == null || !takeAll(level, own, plan.takes())) {
					armor = null;
					walker.reset();
					return;
				}
				piece = new ItemStack(job.piece());
				level.playSound(null, villager.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.0f);
				villager.setAttached(ModAttachments.ARMOR_MADE, villager.getAttachedOrElse(ModAttachments.ARMOR_MADE, 0) + 1);
				BuilderLevels.addXp(level, villager, 3, null);
			}
			villager.swing(InteractionHand.MAIN_HAND);
			bag.add(piece);
			walker.reset();
			return;
		}
		status(villager, Phase.DELIVERING_ARMOR);
		BlockPos to = job.guardChests().isEmpty() ? job.guard().blockPosition() : job.guardChests().get(0);
		if (!walker.walkTo(level, villager, to, REACH)) {
			return;
		}
		int n = bag.remove(job.piece(), 1);
		if (n > 0) {
			ItemStack piece = new ItemStack(job.piece());
			if (job.guardChests().isEmpty() && job.guard().getItemBySlot(job.slot()).isEmpty()) {
				job.guard().setItemSlot(job.slot(), piece);
				job.guard().setDropChance(job.slot(), 1f);
			} else {
				ItemStack rest = SupplyContainers.insert(level, job.guardChests(), piece);
				if (!rest.isEmpty()) {
					bag.add(rest); // no room: it goes back home
				}
			}
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, to, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.8f, 1.0f);
		}
		armor = null;
		walker.reset();
	}

	/** Takes everything in {@code takes} from the chests, or nothing (putting back what was taken) if something's short. */
	private static boolean takeAll(ServerLevel level, List<BlockPos> chests, Map<Item, Integer> takes) {
		Map<Item, Integer> taken = new java.util.HashMap<>();
		for (Map.Entry<Item, Integer> e : takes.entrySet()) {
			int got = SupplyContainers.extract(level, chests, e.getKey(), e.getValue());
			taken.put(e.getKey(), got);
			if (got < e.getValue()) {
				taken.forEach((item, count) -> {
					if (count > 0) {
						SupplyContainers.insert(level, chests, new ItemStack(item, count));
					}
				});
				return false;
			}
		}
		return true;
	}

	/** Stacks of ore this smelter fetches in one trip. */
	static int stacks(Villager villager) {
		return Math.min(BuilderBag.SLOTS, BASE_STACKS + BuilderLevels.level(villager) - 1 + Math.min(Partners.MAX, Partners.helpers(villager).size()));
	}

	/** Items waiting in the output slots of the furnaces by the workstation. */
	private static int waiting(ServerLevel level, BlockPos station) {
		int n = 0;
		for (BlockPos pos : SupplyContainers.furnaces(level, station)) {
			if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
				n += furnace.getItem(2).getCount();
			}
		}
		return n;
	}

	/** Whether a furnace by the workstation has ore in it. */
	private static boolean anySmelting(ServerLevel level, BlockPos station) {
		for (BlockPos pos : SupplyContainers.furnaces(level, station)) {
			if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace && Furnaces.isOre(furnace.getItem(0).getItem())) {
				return true;
			}
		}
		return false;
	}

	/** Whether a furnace by the workstation still has fuel in it. */
	private static boolean fuelled(ServerLevel level, BlockPos station) {
		for (BlockPos pos : SupplyContainers.furnaces(level, station)) {
			if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace && !furnace.getItem(1).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** The nearest village-mate's stash with some of {@code what} to spare, or null. */
	@Nullable
	private static Village.Stash find(ServerLevel level, Villager villager, BlockPos station, Predicate<Item> what) {
		for (Village.Stash stash : Village.stashes(level, villager, station, null)) {
			if (!spare(level, stash, what).isEmpty()) {
				return stash;
			}
		}
		return null;
	}

	/**
	 * What of {@code what} a stash can spare: all of it at the storehouse, never anything from another smelter, and from
	 * other workers what their job doesn't keep (a miner with a furnace smelts their own ore).
	 */
	static Map<Item, Long> spare(ServerLevel level, Village.Stash stash, Predicate<Item> what) {
		Map<Item, Long> out = new java.util.LinkedHashMap<>();
		if (stash.job() == VillagerProfession.ARMORER) {
			return out;
		}
		boolean storehouse = stash.job() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.PORTER;
		boolean furnaceNear = !storehouse && !SupplyContainers.furnaces(level, stash.station()).isEmpty();
		SupplyContainers.contents(level, stash.chests()).forEach((item, count) -> {
			if (!what.test(item)) {
				return;
			}
			int keep = storehouse ? 0 : Porters.keeps(stash.job(), new ItemStack(item), furnaceNear);
			if (keep != Porters.ALL && count > keep) {
				out.put(item, count - keep);
			}
		});
		return out;
	}

	/** Takes up to {@code max} items of {@code what} the stash can spare into the bag; returns how many. */
	private static int take(ServerLevel level, Village.Stash stash, BuilderBag bag, Predicate<Item> what, int max) {
		int taken = 0;
		for (Map.Entry<Item, Long> e : spare(level, stash, what).entrySet()) {
			int want = (int) Math.min(e.getValue(), Math.min(max - taken, bag.spaceFor(e.getKey())));
			if (want <= 0) {
				continue;
			}
			int got = SupplyContainers.extract(level, stash.chests(), e.getKey(), want);
			int over = bag.addAll(e.getKey(), got);
			if (over > 0) {
				SupplyContainers.insert(level, stash.chests(), new ItemStack(e.getKey(), over));
			}
			taken += got - over;
		}
		return taken;
	}

	private void status(Villager villager, Phase phase) {
		Component title = Component.translatable("message.aliveworkplace.smelter.title", villager.getAttachedOrElse(ModAttachments.INGOTS_SMELTED, 0));
		if (phase == Phase.MAKING_ARMOR || phase == Phase.DELIVERING_ARMOR) {
			ArmorJob job = armor;
			WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.smelter.state." + phase.name().toLowerCase(),
				Component.translatable(job != null ? job.piece().getDescriptionId() : "item.minecraft.iron_chestplate"),
				job != null ? job.guard().getDisplayName() : Component.empty()).withStyle(ChatFormatting.GRAY));
			return;
		}
		Component line = (phase == Phase.FETCHING_ORE || phase == Phase.FETCHING_FUEL) && from != null
			? Component.translatable("message.aliveworkplace.smelter.state." + phase.name().toLowerCase(),
				Component.translatable("entity.minecraft.villager." + from.name())).withStyle(ChatFormatting.GRAY)
			: Component.translatable("message.aliveworkplace.smelter.state." + phase.name().toLowerCase())
				.withStyle(phase == Phase.NO_CHEST ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
		WorkerStatus.set(villager, title, -1f, line);
	}
}
