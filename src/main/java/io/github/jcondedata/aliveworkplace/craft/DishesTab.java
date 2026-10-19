package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The board's Dishes tab (ROADMAP 38.8a): every dish of the dish lists ({@link CuisineDishes}) by its icon, what its
 * Chef's level must be, how many are waiting and how many the village's store holds (its kitchens' and Storehouses' chests). A click asks the village's Chef for
 * {@link DishRequests#CLICK} more, shift-click for {@link DishRequests#STACK}, a right click takes the request down.
 * Without a Chef in the village every dish is locked and the tab says a Chef is needed; a dish above the Chef's level
 * says which level it needs. The tab shows only while the dishes are loaded ({@link DishRequests#shown}).
 */
public final class DishesTab {
	/** The tab's place in the page's row: the free slot between the treasury and the Routes tab. */
	public static final int SLOT = 3;
	/** The mark of a locked dish (no Chef, or not his level yet), which the hall's screen may draw. */
	public static final String LOCKED = "locked";

	public static void init() {
		TradePage.register(TradePage.Tab.DISHES, DishesTab::tabIcon, DishesTab::fill, DishRequests::shown);
	}

	/** The tab's icon: a bowl of the first dish, and whether the village has a Chef to cook them. */
	static ItemStack tabIcon(ServerLevel level, BlockPos hall) {
		List<CuisineDishes.Dish> dishes = CuisineDishes.dishes();
		ItemStack shown = dishes.isEmpty() ? new ItemStack(Items.BOWL) : new ItemStack(dishes.get(0).item());
		Villager chef = DishRequests.chef(level, hall);
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line("screen.aliveworkplace.dishes.about", ChatFormatting.GRAY));
		if (chef == null) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.dishes.needs_chef", ChatFormatting.RED));
		} else {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.dishes.chef", DishRequests.levelName(BuilderLevels.level(chef))),
				ChatFormatting.GREEN));
		}
		int waiting = DishRequests.get(level).of(hall).values().stream().mapToInt(Integer::intValue).sum();
		if (waiting > 0) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.dishes.waiting_all", waiting), ChatFormatting.YELLOW));
		}
		ItemStack icon = VillageHallScreen.icon(shown, Component.translatable("screen.aliveworkplace.dishes.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, lore.toArray(Component[]::new));
		if (chef == null) {
			TradePage.mark(icon, LOCKED);
		}
		return icon;
	}

	/** The tab's rows: each dish; a click asks for it, a right click takes its request down. */
	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer, Runnable again) {
		Villager chef = DishRequests.chef(level, hall);
		int chefLevel = chef == null ? 0 : BuilderLevels.level(chef);
		Map<Item, Long> store = SupplyContainers.contents(level, io.github.jcondedata.aliveworkplace.hall.VillageNeeds.store(level, hall));
		int slot = VillageHallScreen.FIRST_ROW;
		for (CuisineDishes.Dish dish : CuisineDishes.dishes()) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			menu.button(slot++, dishIcon(level, hall, dish, chefLevel, store.getOrDefault(dish.item(), 0L)), p -> {
				Component said;
				if (menu.rightClicked()) {
					int was = DishRequests.get(level).cancel(hall, dish.id());
					said = was > 0 ? Component.translatable("screen.aliveworkplace.dishes.cancelled", dish.item().getDescription())
						: Component.translatable("screen.aliveworkplace.dishes.none_waiting", dish.item().getDescription());
				} else {
					int count = menu.shiftClicked() ? DishRequests.STACK : DishRequests.CLICK;
					DishRequests.Result result = DishRequests.request(level, hall, dish.item(), count);
					if (result != DishRequests.Result.ASKED) {
						level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1f);
					}
					said = DishRequests.message(result, dish.item(), count, DishRequests.get(level).waiting(hall, dish.id()));
				}
				Chat.actionBar(p, said);
				again.run();
			});
		}
	}

	/**
	 * A dish on the tab: its name, the Chef's level it needs, how many wait and how many the store holds, and what a click
	 * does; locked (red line, {@link #LOCKED} mark) without a Chef ({@code chefLevel} 0) or above his level.
	 */
	public static ItemStack dishIcon(ServerLevel level, BlockPos hall, CuisineDishes.Dish dish, int chefLevel, long inStore) {
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.dishes.needs_level", DishRequests.levelName(dish.level())),
			ChatFormatting.GRAY));
		int waiting = DishRequests.get(level).waiting(hall, dish.id());
		if (waiting > 0) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.dishes.waiting", waiting), ChatFormatting.YELLOW));
		}
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.dishes.in_store", inStore), ChatFormatting.AQUA));
		boolean locked = chefLevel == 0 || dish.level() > chefLevel;
		if (chefLevel == 0) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.dishes.needs_chef", ChatFormatting.RED));
		} else if (dish.level() > chefLevel) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.dishes.chef_too_low", DishRequests.levelName(dish.level())),
				ChatFormatting.RED));
		} else {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.dishes.click", DishRequests.CLICK, DishRequests.STACK),
				ChatFormatting.DARK_GRAY));
			if (waiting > 0) {
				lore.add(VillageHallScreen.line("screen.aliveworkplace.dishes.click_cancel", ChatFormatting.DARK_GRAY));
			}
		}
		ItemStack icon = VillageHallScreen.icon(new ItemStack(dish.item()), dish.item().getDescription().copy(),
			locked ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE, lore.toArray(Component[]::new));
		if (locked) {
			TradePage.mark(icon, LOCKED);
		}
		return icon;
	}

	private DishesTab() {
	}
}
