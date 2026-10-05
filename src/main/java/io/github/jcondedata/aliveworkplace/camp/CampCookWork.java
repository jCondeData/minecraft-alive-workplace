package io.github.jcondedata.aliveworkplace.camp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Camp Cook's shift (ROADMAP 28.8). She cooks in the Campfire Pot itself: the next dish on her menu the chests by the
 * pot are short of (or a Storehouse's stock order wants, or a nearby worker asks for), its makings from those chests into
 * the pot's grid and seasonings into its top row through the pot's container, then the lid shut (as redstone shuts it)
 * and the pot's own cooking time. The dish comes out of the result slot into her bag and goes to the chests by the pot,
 * or to the Storehouse that ordered it. Restartable at any tick: the pot and her bag (saved) are all she goes by.
 */
public class CampCookWork extends Behavior<Villager> {
	private static final int LOOK_EVERY = 40;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;
	static final int RESULT_SLOT = 0;
	static final int GRID_FIRST = 1;
	static final int GRID_SIZE = 9;
	static final int SEASONING_FIRST = 10;
	static final int SEASONINGS = 3;

	private final Walker walker = new Walker(SPEED);
	private int lookTimer;
	private int stuck;

	public CampCookWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	/** The Camp Cook's WORK activity. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new CampCookWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && CampCooks.isCook(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		lookTimer = 0;
		stuck = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (station == null) {
			return;
		}
		CampCooks.Pot pot = CampCooks.pot();
		if (pot == null || !CampCooks.ENABLED) {
			status(villager, "off");
			return;
		}
		BlockState state = level.getBlockState(station);
		if (!pot.isPot(state) || !(level.getBlockEntity(station) instanceof Container container)) {
			status(villager, "no_pot");
			return;
		}
		// The chests by the pot (the pot is a container too, but never one of them).
		List<BlockPos> chests = SupplyContainers.find(level, station, null).stream().filter(p -> !p.equals(station)).toList();
		if (chests.isEmpty()) {
			status(villager, "no_chest");
			return;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		// 1. What she carries goes where it's wanted: a Storehouse's order, or the chests by the pot.
		if (!bag.isEmpty()) {
			deliver(level, villager, station, chests, bag);
			return;
		}
		if (!reach(level, villager, station)) {
			return;
		}
		// 2. A dish in the result slot comes out.
		ItemStack result = container.getItem(RESULT_SLOT);
		if (!result.isEmpty()) {
			container.setItem(RESULT_SLOT, ItemStack.EMPTY);
			container.setChanged();
			int cooked = ModAttachments.DISHES_COOKED.getOrElse(villager, 0) + result.getCount();
			ModAttachments.DISHES_COOKED.set(villager, cooked);
			BuilderLevels.addXp(level, villager, 1, null);
			ItemStack rest = bag.add(result);
			if (!rest.isEmpty()) {
				SupplyContainers.insert(level, chests, rest);
			}
			status(villager, "serving", result.getHoverName());
			return;
		}
		boolean gridEmpty = gridEmpty(container);
		// 3. The pot is cooking: she minds it.
		if (pot.lidShut(state)) {
			if (!gridEmpty && pot.cooks(level, station)) {
				status(villager, "minding");
				return;
			}
			// Done (or what's in it makes nothing): the lid comes off, and leftovers go back to the chests next.
			pot.setLid(level, station, false);
			return;
		}
		// 4. Makings left in an open pot (a restart, a recipe that's gone): back to the chests.
		if (!gridEmpty || !seasoningsEmpty(container)) {
			for (int slot = GRID_FIRST; slot < SEASONING_FIRST + SEASONINGS; slot++) {
				ItemStack left = container.getItem(slot);
				if (!left.isEmpty()) {
					container.setItem(slot, SupplyContainers.insert(level, chests, left.copy()));
				}
			}
			container.setChanged();
			return;
		}
		if (--lookTimer > 0) {
			return;
		}
		lookTimer = LOOK_EVERY;
		// 5. The next dish: makings in, seasonings on top, lid shut.
		Choice choice = choose(level, villager, station, pot, chests);
		if (choice == null) {
			return;
		}
		for (int i = 0; i < GRID_SIZE; i++) {
			Item item = choice.grid().get(i);
			if (item != null) {
				container.setItem(GRID_FIRST + i, SupplyContainers.takeOne(level, chests, s -> s.is(item)));
			}
		}
		if (choice.recipe().seasoning() != null) {
			// Seasoned as asked (a Habitat Keeper's lure), only with those berries; otherwise with whatever seasons it.
			java.util.function.Predicate<ItemStack> seasons = choice.seasonWith().isEmpty() ? s -> s.is(choice.recipe().seasoning())
				: s -> s.is(choice.recipe().seasoning()) && choice.seasonWith().contains(s.getItem());
			for (int i = 0; i < SEASONINGS; i++) {
				ItemStack seasoning = SupplyContainers.takeOne(level, chests, seasons);
				if (seasoning.isEmpty()) {
					break;
				}
				container.setItem(SEASONING_FIRST + i, seasoning);
			}
		}
		container.setChanged();
		pot.setLid(level, station, true);
		PartnerShows.cue(villager, "cook", station);
		status(villager, "cooking", dishName(choice.dish()));
	}

	/** A dish to cook now, with the recipe and the items for each grid slot (null: empty). */
	record Choice(CampCooks.Dish dish, CampCooks.PotRecipe recipe, List<Item> grid, java.util.Set<Item> seasonWith) {
	}

	@Nullable
	private Choice choose(ServerLevel level, Villager villager, BlockPos station, CampCooks.Pot pot, List<BlockPos> chests) {
		Map<Item, Long> stock = SupplyContainers.contents(level, chests);
		CampCooks.Dish missing = null;
		for (CampCooks.Dish dish : CampCooks.menu()) {
			Item item = BuiltInRegistries.ITEM.getOptional(dish.item()).orElse(null);
			if (item == null) {
				continue;
			}
			// Asked seasoned a particular way (28.10): only the dishes seasoned so count, and only those berries season it.
			java.util.Set<Item> asked = new java.util.LinkedHashSet<>();
			CampCooks.askedSeasonings(level, station, dish).forEach(id -> BuiltInRegistries.ITEM.getOptional(id).ifPresent(asked::add));
			if (asked.isEmpty() ? !wanted(level, station, dish, item, stock)
				: seasonedCount(level, chests, pot, item, asked) >= dish.keep() || !CampCooks.When.ASKED.equals(dish.when())) {
				continue;
			}
			for (CampCooks.PotRecipe recipe : pot.recipes(level, item)) {
				List<Item> grid = makings(recipe, stock);
				if (grid != null && (asked.isEmpty() || recipe.seasoning() != null
					&& asked.stream().anyMatch(a -> stock.getOrDefault(a, 0L) > 0 && new ItemStack(a).is(recipe.seasoning())))) {
					return new Choice(dish, recipe, grid, asked);
				}
			}
			if (missing == null) {
				missing = dish;
			}
		}
		if (missing != null) {
			status(villager, "needs", dishName(missing));
		} else {
			status(villager, "waiting");
		}
		return null;
	}

	/** How many of {@code item} in the chests were seasoned with one of {@code seasonings}. */
	static long seasonedCount(ServerLevel level, List<BlockPos> chests, CampCooks.Pot pot, Item item, java.util.Set<Item> seasonings) {
		long n = 0;
		for (BlockPos chest : chests) {
			for (ItemStack stack : SupplyContainers.peekMatching(level, chest, s -> s.is(item))) {
				java.util.Set<net.minecraft.resources.ResourceLocation> with = pot.seasonings(stack);
				if (seasonings.stream().anyMatch(s -> with.contains(BuiltInRegistries.ITEM.getKey(s)))) {
					n += stack.getCount();
				}
			}
		}
		return n;
	}

	/** Whether the menu wants {@code dish} cooked now. */
	static boolean wanted(ServerLevel level, BlockPos station, CampCooks.Dish dish, Item item, Map<Item, Long> stock) {
		return switch (dish.when()) {
			case ALWAYS -> stock.getOrDefault(item, 0L) < dish.keep();
			case ASKED -> stock.getOrDefault(item, 0L) < dish.keep() && asked(level, station, dish);
			case ORDER -> orderShort(level, station, item) != null;
		};
	}

	/** Whether a worker of one of the jobs that ask for {@code dish} works near the pot. */
	private static boolean asked(ServerLevel level, BlockPos station, CampCooks.Dish dish) {
		return !level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(CampCooks.ASK_RANGE),
			v -> dish.askedBy().contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()))).isEmpty();
	}

	/** The nearest Storehouse in range with a stock order for {@code item} that its store is short of, or null. */
	@Nullable
	static BlockPos orderShort(ServerLevel level, BlockPos station, Item item) {
		List<BlockPos> storehouses = level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> true, station,
				StockOrders.RANGE, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(Comparator.comparingDouble(p -> p.distSqr(station))).toList();
		for (BlockPos storehouse : storehouses) {
			Integer keep = StockOrders.of(level, storehouse).get(item);
			if (keep == null) {
				continue;
			}
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			if (!store.isEmpty() && SupplyContainers.count(level, store, item) < keep) {
				return storehouse;
			}
		}
		return null;
	}

	/** The item for each grid slot of {@code recipe} from {@code stock}, or null if the chests are short of the makings. */
	@Nullable
	static List<Item> makings(CampCooks.PotRecipe recipe, Map<Item, Long> stock) {
		Map<Item, Long> left = new HashMap<>(stock);
		List<Item> sorted = new ArrayList<>(left.keySet());
		sorted.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
		List<Item> grid = new ArrayList<>();
		for (Ingredient ingredient : recipe.grid()) {
			if (ingredient.isEmpty()) {
				grid.add(null);
				continue;
			}
			Item found = null;
			for (Item item : sorted) {
				if (left.getOrDefault(item, 0L) > 0 && ingredient.test(new ItemStack(item))) {
					found = item;
					break;
				}
			}
			if (found == null) {
				return null;
			}
			left.merge(found, -1L, Long::sum);
			grid.add(found);
		}
		while (grid.size() < GRID_SIZE) {
			grid.add(null);
		}
		return grid;
	}

	private void deliver(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> chests, BuilderBag bag) {
		ItemStack first = bag.stacks().stream().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
		BlockPos storehouse = first.isEmpty() ? null : orderShort(level, station, first.getItem());
		List<BlockPos> to = storehouse != null ? SupplyContainers.find(level, storehouse, null) : chests;
		BlockPos target = storehouse != null ? storehouse : station;
		status(villager, "delivering", first.getHoverName());
		if (!reach(level, villager, target)) {
			return;
		}
		for (ItemStack stack : new ArrayList<>(bag.stacks())) {
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack rest = SupplyContainers.insert(level, to, stack.copy());
			bag.remove(stack.getItem(), stack.getCount() - rest.getCount());
			if (!rest.isEmpty() && to != chests) {
				ItemStack still = SupplyContainers.insert(level, chests, rest);
				bag.remove(stack.getItem(), rest.getCount() - still.getCount());
			}
		}
		walker.reset();
	}

	private boolean reach(ServerLevel level, Villager villager, BlockPos pos) {
		if (walker.reach(level, villager, pos, REACH)) {
			stuck = 0;
			return true;
		}
		if (walker.noSpot() || ++stuck > 600) {
			stuck = 0;
			walker.reset();
		}
		return false;
	}

	private static boolean gridEmpty(Container container) {
		return firstFilled(container) < 0;
	}

	private static int firstFilled(Container container) {
		for (int slot = GRID_FIRST; slot < GRID_FIRST + GRID_SIZE; slot++) {
			if (!container.getItem(slot).isEmpty()) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean seasoningsEmpty(Container container) {
		for (int slot = SEASONING_FIRST; slot < SEASONING_FIRST + SEASONINGS; slot++) {
			if (!container.getItem(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static Component dishName(CampCooks.Dish dish) {
		return BuiltInRegistries.ITEM.getOptional(dish.item()).map(i -> new ItemStack(i).getHoverName())
			.orElse(Component.literal(dish.item().toString()));
	}

	private static void status(Villager villager, String state, Component... args) {
		Component title = Component.translatable("message.aliveworkplace.camp_cook.title", ModAttachments.DISHES_COOKED.getOrElse(villager, 0));
		boolean warn = state.equals("no_chest") || state.equals("needs") || state.equals("no_pot") || state.equals("off");
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.camp_cook.state." + state, (Object[]) args)
			.withStyle(warn ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
