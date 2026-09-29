package io.github.jcondedata.aliveworkplace.brew;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A Cleric's shift as the village's alchemist, at their brewing stand: brew the potions the guards need — healing,
 * regeneration, strength — from the chests by the stand (nether wart, glistering melon, ghast tears, blaze powder, and
 * water bottles, or glass bottles filled at water within {@link SupplyContainers#RADIUS} blocks), with blaze powder as
 * fuel, until there are {@link #KEEP} of each; the finished potions go in the chests, and a guard of the village with
 * fewer than {@link #GUARD_KEEP} gets one brought to the chests by their Guard Post.
 */
public class AlchemistWork extends Behavior<Villager> {
	/** Potions brewed, and kept in the chests up to this many each. */
	static final List<Holder<Potion>> TARGETS = List.of(Potions.HEALING, Potions.REGENERATION, Potions.STRENGTH);
	static final int KEEP = 3;
	/** A guard is brought potions while they have fewer than this many (carried and in their chests). */
	public static final int GUARD_KEEP = 2;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;
	private static final int LOOK_EVERY = 40;

	private enum Phase { IDLE, BREWING, FILLING, DELIVERING, NO_CHEST }

	private static final Set<Villager> BUSY = Collections.newSetFromMap(new WeakHashMap<>());

	private final Walker walker = new Walker(SPEED);
	private Phase phase = Phase.IDLE;
	@Nullable
	private BlockPos water;
	@Nullable
	private Villager guard;
	@Nullable
	private List<BlockPos> guardChests;
	private int lookTimer;

	public AlchemistWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isAlchemist(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.CLERIC;
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

	/** A potion a guard drinks: healing, regeneration or strength (plain, strong or long). */
	public static boolean isGuardPotion(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (!stack.is(Items.POTION) || contents == null || contents.potion().isEmpty()) {
			return false;
		}
		Holder<Potion> potion = contents.potion().get();
		return potion.is(Potions.HEALING) || potion.is(Potions.STRONG_HEALING) || potion.is(Potions.REGENERATION)
			|| potion.is(Potions.STRONG_REGENERATION) || potion.is(Potions.LONG_REGENERATION) || potion.is(Potions.STRENGTH)
			|| potion.is(Potions.STRONG_STRENGTH) || potion.is(Potions.LONG_STRENGTH);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && isAlchemist(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		phase = Phase.IDLE;
		lookTimer = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		busy(villager, false);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (station == null || !(level.getBlockEntity(station) instanceof BrewingStandBlockEntity stand)) {
			return;
		}
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		if (own.isEmpty()) {
			busy(villager, false);
			status(villager, Phase.NO_CHEST);
			return;
		}
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		switch (phase) {
			case BREWING -> {
				status(villager, Phase.BREWING);
				if (walker.walkTo(level, villager, station, REACH)) {
					tend(level, villager, stand, own, bag);
					phase = Phase.IDLE;
					walker.reset();
				}
				return;
			}
			case FILLING -> {
				status(villager, Phase.FILLING);
				fill(level, villager, own, bag);
				return;
			}
			case DELIVERING -> {
				status(villager, Phase.DELIVERING);
				deliver(level, villager, own, bag);
				return;
			}
			default -> {
			}
		}
		status(villager, stand.getItem(0).isEmpty() && stand.getItem(1).isEmpty() && stand.getItem(2).isEmpty() ? Phase.IDLE : Phase.BREWING);
		if (--lookTimer > 0) {
			return;
		}
		lookTimer = LOOK_EVERY;
		busy(villager, false);
		if (brewing(stand)) {
			busy(villager, true);
			return; // wait for it
		}
		// Something to do at the stand: potions to take out, an ingredient to put in, fuel, bottles to load.
		if (standNeeds(level, stand, own, bag)) {
			phase = Phase.BREWING;
			busy(villager, true);
			walker.reset();
			return;
		}
		// Bottles to fill first?
		if (wantsWater(level, stand, own, bag) && bag.count(Items.GLASS_BOTTLE) + SupplyContainers.count(level, own, Items.GLASS_BOTTLE) > 0) {
			water = findWater(level, station);
			if (water != null) {
				phase = Phase.FILLING;
				busy(villager, true);
				walker.reset();
				return;
			}
		}
		// A guard short of potions?
		if (chooseGuard(level, villager, station, own)) {
			phase = Phase.DELIVERING;
			busy(villager, true);
			walker.reset();
		}
	}

	private static boolean brewing(BrewingStandBlockEntity stand) {
		return ((io.github.jcondedata.aliveworkplace.mixin.BrewingStandAccessor) stand).aliveworkplace$brewTime() > 0;
	}

	/** The potion in a bottle slot (null if empty or not a potion). */
	@Nullable
	private static Holder<Potion> potionIn(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return stack.is(Items.POTION) && contents != null ? contents.potion().orElse(null) : null;
	}

	/** Potions of {@code target} in the chests. */
	private static int stocked(ServerLevel level, List<BlockPos> own, Holder<Potion> target) {
		return (int) SupplyContainers.countMatching(level, own, PotionContents.createItemStack(Items.POTION, target));
	}

	/** What to put in next for these bottles: an ingredient from the chests that brings them a step closer to a potion that's short. */
	@Nullable
	private static Item nextIngredient(ServerLevel level, Holder<Potion> bottles, List<BlockPos> own) {
		PotionBrewing brewing = level.potionBrewing();
		ItemStack bottle = PotionContents.createItemStack(Items.POTION, bottles);
		Map<Item, Long> stock = SupplyContainers.contents(level, own);
		for (Holder<Potion> target : TARGETS) {
			if (stocked(level, own, target) >= KEEP) {
				continue;
			}
			for (Item ingredient : stock.keySet()) {
				ItemStack in = new ItemStack(ingredient);
				if (!brewing.hasMix(bottle, in)) {
					continue;
				}
				Holder<Potion> result = potionIn(brewing.mix(in, bottle));
				if (result != null && (result.is(target) || bottles.is(Potions.WATER) && result.is(Potions.AWKWARD) && canFinish(level, target, stock))) {
					return ingredient;
				}
			}
		}
		return null;
	}

	/** Whether there's an ingredient in stock that turns awkward potions into {@code target}. */
	private static boolean canFinish(ServerLevel level, Holder<Potion> target, Map<Item, Long> stock) {
		ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
		for (Item ingredient : stock.keySet()) {
			ItemStack in = new ItemStack(ingredient);
			if (level.potionBrewing().hasMix(awkward, in)) {
				Holder<Potion> result = potionIn(level.potionBrewing().mix(in, awkward));
				if (result != null && result.is(target)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Whether the stand wants tending now (see {@link #tend}). */
	private static boolean standNeeds(ServerLevel level, BrewingStandBlockEntity stand, List<BlockPos> own, BuilderBag bag) {
		Holder<Potion> bottles = potionIn(stand.getItem(0));
		if (bottles != null) {
			for (Holder<Potion> target : TARGETS) {
				if (bottles.is(target)) {
					return true; // done: out they come
				}
			}
			return stand.getItem(3).isEmpty() && nextIngredient(level, bottles, own) != null;
		}
		// Empty: load water bottles if there's something worth brewing.
		return wantsWater(level, stand, own, bag) && (bag.count(Items.POTION) > 0 || SupplyContainers.firstMatching(level, own, AlchemistWork::isWater) != null);
	}

	/** An empty stand, and ingredients for a potion that's short, starting from water. */
	private static boolean wantsWater(ServerLevel level, BrewingStandBlockEntity stand, List<BlockPos> own, BuilderBag bag) {
		return stand.getItem(0).isEmpty() && stand.getItem(1).isEmpty() && stand.getItem(2).isEmpty()
			&& nextIngredient(level, Potions.WATER, own) != null;
	}

	static boolean isWater(ItemStack stack) {
		Holder<Potion> potion = potionIn(stack);
		return potion != null && potion.is(Potions.WATER);
	}

	/** At the stand: finished potions out, then fuel, bottles and the next ingredient in. */
	private void tend(ServerLevel level, Villager villager, BrewingStandBlockEntity stand, List<BlockPos> own, BuilderBag bag) {
		boolean changed = false;
		Holder<Potion> bottles = potionIn(stand.getItem(0));
		if (bottles != null && TARGETS.stream().anyMatch(bottles::is)) {
			for (int i = 0; i < 3; i++) {
				ItemStack potion = stand.getItem(i);
				if (!potion.isEmpty()) {
					ItemStack rest = SupplyContainers.insert(level, own, potion);
					stand.setItem(i, rest);
					if (rest.isEmpty()) {
						villager.setAttached(ModAttachments.POTIONS_BREWED, villager.getAttachedOrElse(ModAttachments.POTIONS_BREWED, 0) + 1);
					}
				}
			}
			BuilderLevels.addXp(level, villager, 2, null);
			changed = true;
			bottles = potionIn(stand.getItem(0));
		}
		// Fuel.
		if (stand.getItem(4).isEmpty() && ((io.github.jcondedata.aliveworkplace.mixin.BrewingStandAccessor) stand).aliveworkplace$fuel() <= 1) {
			int got = SupplyContainers.extract(level, own, Items.BLAZE_POWDER, 1);
			if (got > 0) {
				stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
				changed = true;
			}
		}
		// Water bottles into an empty stand.
		if (stand.getItem(0).isEmpty() && stand.getItem(1).isEmpty() && stand.getItem(2).isEmpty()) {
			for (int i = 0; i < 3; i++) {
				ItemStack water = bag.takeFirst(AlchemistWork::isWater);
				if (water.isEmpty()) {
					water = SupplyContainers.takeOne(level, own, AlchemistWork::isWater);
				}
				if (water.isEmpty()) {
					break;
				}
				stand.setItem(i, water.split(1));
				if (!water.isEmpty()) {
					bag.add(water);
				}
				changed = true;
			}
			bottles = potionIn(stand.getItem(0));
		}
		// The next ingredient.
		if (bottles != null && stand.getItem(3).isEmpty()) {
			Item ingredient = nextIngredient(level, bottles, own);
			if (ingredient != null && SupplyContainers.extract(level, own, ingredient, 1) == 1) {
				stand.setItem(3, new ItemStack(ingredient));
				changed = true;
			}
		}
		if (changed) {
			stand.setChanged();
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, stand.getBlockPos(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 1.0f);
		}
	}

	/** Water within reach of the workstation's chests: a source block or a filled cauldron. */
	@Nullable
	private static BlockPos findWater(ServerLevel level, BlockPos station) {
		int r = SupplyContainers.RADIUS;
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(station.offset(-r, -2, -r), station.offset(r, 2, r))) {
			BlockState state = level.getBlockState(p);
			boolean water = level.getFluidState(p).is(Fluids.WATER) && level.getFluidState(p).isSource()
				|| state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON);
			if (water && (best == null || p.distSqr(station) < best.distSqr(station))) {
				best = p.immutable();
			}
		}
		return best;
	}

	/** Takes glass bottles, fills them at the water and brings them back as water bottles (in the bag). */
	private void fill(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (water == null) {
			phase = Phase.IDLE;
			return;
		}
		if (bag.count(Items.GLASS_BOTTLE) == 0 && bag.count(Items.POTION) == 0) {
			if (walker.walkTo(level, villager, own.get(0), REACH)) {
				int got = SupplyContainers.extract(level, own, Items.GLASS_BOTTLE, 3);
				bag.addAll(Items.GLASS_BOTTLE, got);
				walker.reset();
				if (got == 0) {
					phase = Phase.IDLE;
				}
			}
			return;
		}
		if (!walker.walkTo(level, villager, water, REACH)) {
			return;
		}
		BlockState state = level.getBlockState(water);
		int n = bag.count(Items.GLASS_BOTTLE);
		for (int i = 0; i < n; i++) {
			if (state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) {
				if (!state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) {
					break; // emptied
				}
				LayeredCauldronBlock.lowerFillLevel(state, level, water);
				state = level.getBlockState(water);
			} else if (!level.getFluidState(water).is(Fluids.WATER)) {
				break;
			}
			bag.remove(Items.GLASS_BOTTLE, 1);
			bag.add(PotionContents.createItemStack(Items.POTION, Potions.WATER));
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, water, SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1f, 1f);
		phase = Phase.IDLE;
		lookTimer = 0;
		walker.reset();
	}

	/** A guard of the village short of potions, with one in our chests for them. */
	private boolean chooseGuard(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own) {
		if (Village.RADIUS <= 0 || SupplyContainers.firstMatching(level, own, AlchemistWork::isGuardPotion) == null) {
			return false;
		}
		var boss = villager.getAttached(ModAttachments.BUILDER_EMPLOYER);
		for (Villager g : level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(Village.RADIUS),
				v -> v.isAlive() && Guards.isGuard(v) && Village.sameSide(level, v.getAttached(ModAttachments.BUILDER_EMPLOYER), boss))) {
			BlockPos post = Builders.benchPos(g).orElse(null);
			if (post == null) {
				continue;
			}
			List<BlockPos> theirs = SupplyContainers.find(level, post, null);
			if (theirs.isEmpty()) {
				continue;
			}
			int have = Guards.potions(g);
			for (BlockPos chest : theirs) {
				have += SupplyContainers.peekMatching(level, chest, AlchemistWork::isGuardPotion).size();
			}
			if (have < GUARD_KEEP) {
				guard = g;
				guardChests = theirs;
				return true;
			}
		}
		return false;
	}

	/** Takes a potion from our chests to the guard's. */
	private void deliver(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (guard == null || guardChests == null || !guard.isAlive()) {
			phase = Phase.IDLE;
			return;
		}
		ItemStack carried = bag.takeFirst(AlchemistWork::isGuardPotion);
		if (carried.isEmpty()) {
			if (walker.walkTo(level, villager, own.get(0), REACH)) {
				ItemStack potion = SupplyContainers.takeOne(level, own, AlchemistWork::isGuardPotion);
				if (potion.isEmpty()) {
					phase = Phase.IDLE;
				} else {
					bag.add(potion);
				}
				walker.reset();
			}
			return;
		}
		bag.add(carried);
		if (!walker.walkTo(level, villager, guardChests.get(0), REACH)) {
			return;
		}
		ItemStack potion = bag.takeFirst(AlchemistWork::isGuardPotion);
		ItemStack rest = SupplyContainers.insert(level, guardChests, potion);
		if (!rest.isEmpty()) {
			Block.popResource(level, guardChests.get(0).above(), rest);
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, guardChests.get(0), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.0f);
		phase = Phase.IDLE;
		lookTimer = 0;
		walker.reset();
	}

	private void status(Villager villager, Phase phase) {
		Component title = Component.translatable("message.aliveworkplace.alchemist.title", villager.getAttachedOrElse(ModAttachments.POTIONS_BREWED, 0));
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.alchemist.state." + phase.name().toLowerCase())
			.withStyle(phase == Phase.NO_CHEST ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
