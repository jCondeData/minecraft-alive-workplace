package io.github.jcondedata.aliveworkplace.store;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The Porter's shift: walk round the other workers of the village (see {@link Village}), take the goods piling up in
 * their chests — what their job makes, never what it needs (see {@link Porters#keeps}) — and carry them to the chests
 * by the Storehouse. The storehouse is part of the village's stock, so builders and everyone else find things there.
 * Restartable at any tick: what's being carried is in the porter's bag, and a porter with a full bag heads home first.
 */
public class PorterWork extends Behavior<Villager> {
	/** A worker's goods have to add up to this many items to be worth a trip. */
	public static final int MIN_LOAD = 16;
	/** How far from the storehouse a porter empties Drop Boxes. */
	public static int DROP_BOX_RANGE = 48;
	/** Stacks a novice carries in one trip; more with each level and each Pokémon partner. */
	public static final int BASE_STACKS = 9;
	public static final int STACKS_PER_LEVEL = 3;
	public static final int STACKS_PER_PARTNER = 3;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;
	private static final int LOOK_EVERY = 100;
	private static final int FULL_WAIT = 400;

	private enum Phase { IDLE, COLLECTING, STORING, FULL, NO_CHESTS }

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private Village.Stash target;
	private int lookTimer;
	private int ownerTimer;
	private Phase phase = Phase.IDLE;

	public PorterWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
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
		target = null;
		lookTimer = 0;
		ownerTimer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos storehouse = Builders.benchPos(villager).orElse(null);
		if (storehouse == null) {
			return;
		}
		if (--ownerTimer <= 0) {
			ownerTimer = 200;
			Porters.answerToOwner(level, villager, storehouse);
		}
		List<BlockPos> store = store(level, storehouse, gameTime);
		if (store.isEmpty()) {
			target = null;
			status(villager, Phase.NO_CHESTS, null);
			return;
		}
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		if (!bag.isEmpty() && target == null) {
			storeAll(level, villager, store, bag);
			return;
		}
		if (target == null) {
			status(villager, phase, null);
			if (--lookTimer > 0) {
				return;
			}
			lookTimer = LOOK_EVERY;
			if (SupplyContainers.freeSlots(level, store) == 0) {
				phase = Phase.FULL;
				return;
			}
			target = choose(level, villager, storehouse);
			if (target == null) {
				phase = Phase.IDLE;
				return;
			}
			walker.reset();
		}
		status(villager, Phase.COLLECTING, target.job());
		if (!walker.walkTo(level, villager, target.chests().get(0), REACH)) {
			return;
		}
		int room = Math.min(capacity(villager), SupplyContainers.freeSlots(level, store));
		int taken = collect(level, target, bag, room);
		if (taken > 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, target.chests().get(0), SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		}
		target = null;
		walker.reset();
	}

	private List<BlockPos> storeChests = List.of();
	private long storeUntil;
	private BlockPos storeAt = BlockPos.ZERO;

	/** The chests by the storehouse (looked up once a second, not every tick). */
	private List<BlockPos> store(ServerLevel level, BlockPos storehouse, long gameTime) {
		if (gameTime >= storeUntil || !storehouse.equals(storeAt)) {
			storeChests = SupplyContainers.find(level, storehouse, null);
			storeAt = storehouse;
			storeUntil = gameTime + 20;
		}
		return storeChests;
	}

	/** Carries the bag home and empties it into the storehouse chests. */
	private void storeAll(ServerLevel level, Villager villager, List<BlockPos> store, BuilderBag bag) {
		if (phase == Phase.FULL && --lookTimer > 0) {
			status(villager, Phase.FULL, null);
			return;
		}
		status(villager, Phase.STORING, null);
		if (villager.getMainHandItem().isEmpty()) {
			hold(villager, bag);
		}
		if (!walker.walkTo(level, villager, store.get(0), REACH)) {
			return;
		}
		int stored = 0;
		int stacks = 0;
		for (ItemStack stack : bag.takeAll()) {
			int count = stack.getCount();
			ItemStack rest = SupplyContainers.insert(level, store, stack);
			stored += count - rest.getCount();
			stacks++;
			if (!rest.isEmpty()) {
				ItemStack back = bag.add(rest);
				if (!back.isEmpty()) {
					Block.popResource(level, villager.blockPosition(), back);
				}
			}
		}
		if (stored > 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, store.get(0), SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5f, 1.0f);
			villager.setAttached(ModAttachments.ITEMS_CARRIED, villager.getAttachedOrElse(ModAttachments.ITEMS_CARRIED, 0) + stored);
			BuilderLevels.addXp(level, villager, Math.max(1, stacks / 2), null);
		}
		villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		if (bag.isEmpty()) {
			phase = Phase.IDLE;
			lookTimer = 0; // straight on to the next worker
		} else {
			phase = Phase.FULL;
			lookTimer = FULL_WAIT;
		}
	}

	/** Shows what's being carried: one of the biggest stack, in hand (just for looks; it never drops). */
	private static void hold(Villager villager, BuilderBag bag) {
		ItemStack biggest = ItemStack.EMPTY;
		for (ItemStack stack : bag.stacks()) {
			if (!stack.isEmpty() && stack.getCount() > biggest.getCount()) {
				biggest = stack;
			}
		}
		if (!biggest.isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, biggest.copyWithCount(1));
			villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
		}
	}

	/** The nearest village-mate with enough goods to be worth the walk, or null. */
	@Nullable
	private static Village.Stash choose(ServerLevel level, Villager villager, BlockPos storehouse) {
		Village.Stash box = dropBox(level, storehouse);
		if (box != null) {
			return box; // what players left for the village first
		}
		for (Village.Stash stash : Village.stashes(level, villager, storehouse, null)) {
			if (carriesNothingFrom(stash.job())) {
				continue;
			}
			long load = goods(level, stash).values().stream().mapToLong(Long::longValue).sum();
			if (load >= MIN_LOAD) {
				return stash;
			}
		}
		return null;
	}

	/** The nearest Drop Box near the storehouse with anything in it, as a stash with no job (everything in it goes). */
	@Nullable
	static Village.Stash dropBox(ServerLevel level, BlockPos storehouse) {
		return level.getPoiManager().findAllClosestFirstWithType(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.DROP_BOX_POI),
				p -> level.getBlockEntity(p) instanceof DropBoxBlockEntity box && !box.isEmpty(), storehouse, DROP_BOX_RANGE,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
			.findFirst().map(pair -> new Village.Stash(pair.getSecond(), VillagerProfession.NONE, List.of(pair.getSecond()))).orElse(null);
	}

	/** Builders, ball smiths, chefs and other porters: there's never anything to carry away from them. */
	private static boolean carriesNothingFrom(VillagerProfession job) {
		return job == io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER || job == io.github.jcondedata.aliveworkplace.registry.ModVillagers.BALL_SMITH
			|| job == io.github.jcondedata.aliveworkplace.registry.ModVillagers.PORTER || job == io.github.jcondedata.aliveworkplace.registry.ModVillagers.CHEF;
	}

	/** What the porter may take from this stash: each kind of goods and how many (beyond what the worker keeps). */
	static Map<Item, Long> goods(ServerLevel level, Village.Stash stash) {
		if (stash.job() == VillagerProfession.NONE) {
			return new LinkedHashMap<>(SupplyContainers.contents(level, stash.chests())); // a Drop Box: all of it
		}
		boolean furnaceNear = !SupplyContainers.furnaces(level, stash.station()).isEmpty();
		Map<Item, Long> out = new LinkedHashMap<>();
		SupplyContainers.contents(level, stash.chests()).forEach((item, count) -> {
			int keep = Porters.keeps(stash.job(), new ItemStack(item), furnaceNear);
			if (keep != Porters.ALL && count > keep) {
				out.put(item, count - keep);
			}
		});
		return out;
	}

	/** Takes up to {@code stacks} stacks of goods from the stash into the bag; returns how many items. */
	static int collect(ServerLevel level, Village.Stash stash, BuilderBag bag, int stacks) {
		int taken = 0;
		int left = stacks;
		for (Map.Entry<Item, Long> e : goods(level, stash).entrySet()) {
			if (left <= 0) {
				break;
			}
			Item item = e.getKey();
			int perStack = Math.max(1, item.getDefaultMaxStackSize());
			int want = (int) Math.min(e.getValue(), (long) left * perStack);
			want = Math.min(want, bag.spaceFor(item));
			if (want <= 0) {
				continue;
			}
			int got = SupplyContainers.extract(level, stash.chests(), item, want);
			if (got > 0) {
				int over = bag.addAll(item, got);
				if (over > 0) {
					SupplyContainers.insert(level, stash.chests(), new ItemStack(item, over)); // didn't fit after all: put it back
				}
				int added = got - over;
				taken += added;
				left -= (added + perStack - 1) / perStack;
			}
		}
		return taken;
	}

	/** Stacks this porter carries in one trip. */
	static int capacity(Villager villager) {
		int stacks = BASE_STACKS + STACKS_PER_LEVEL * (BuilderLevels.level(villager) - 1)
			+ STACKS_PER_PARTNER * Math.min(Partners.max(villager), Partners.helpers(villager).size());
		return Math.min(BuilderBag.SLOTS, stacks);
	}

	private void status(Villager villager, Phase phase, @Nullable VillagerProfession from) {
		Component title = Component.translatable("message.aliveworkplace.porter.title", villager.getAttachedOrElse(ModAttachments.ITEMS_CARRIED, 0));
		Component line = from != null
			? Component.translatable("message.aliveworkplace.porter.state.collecting", Component.translatable("entity.minecraft.villager." + from.name()))
				.withStyle(ChatFormatting.GRAY)
			: Component.translatable("message.aliveworkplace.porter.state." + phase.name().toLowerCase())
				.withStyle(phase == Phase.FULL || phase == Phase.NO_CHESTS ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
		WorkerStatus.set(villager, title, -1f, line);
	}
}
