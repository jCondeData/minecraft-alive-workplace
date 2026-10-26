package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * Bed ▾ in the hall's People table (ROADMAP 40.4): a list of the village's beds under the villager's row. Free beds come
 * first, each with where it is (the building's name and which of its beds, when a builder put it up, else the distance
 * and direction from the hall: "free · 14 blocks east"), then the beds others sleep in ("swap with Jem"), and "Short of
 * beds? Plan a house ›" last, which opens the Buildings section. A click makes the bed theirs at once, through the game's
 * own home memory and bed claim ({@link MemoryModuleType#HOME}, the bed's point of interest): the old bed is freed, a
 * swap moves both, and they sleep there that night. The table and the census read the same memory
 * ({@link VillageNeeds#bed}, B362: a broken bed houses nobody), so both show it straight away. Only the beds in the
 * village ({@link VillageHalls#beds}); a wagon's bedrolls are no homes ({@link CampBeds}) and cradles hold the children
 * of the house by their own rule ({@link Cradles}), as the list's last line says.
 * <p>
 * As Job ▾ (40.3), no new packet: menu-button clicks ({@link #OPEN} + the person's slot, {@link #PICK} + the line's
 * number, {@link #CLOSE}), and the list rides in the open person's button as a container of one stack per line. Nothing
 * is saved but the bed itself, which is vanilla's: the open list lives with the screen.
 */
public final class HallBeds {
	/** A menu-button click of OPEN + slot opens the Bed list of the person in that slot. */
	public static final int OPEN = 300_000;
	/** A menu-button click of CLOSE closes the open list. */
	public static final int CLOSE = 399_999;
	/** A menu-button click of PICK + n picks the bed on line n of the list laid out last. */
	public static final int PICK = 400_000;
	/** The mark on each line of the list. */
	public static final String MARK = "aliveworkplace:bed_option";

	/** What a line of the list is. */
	public enum State {
		/** The lines over the list: whose, and where they sleep now. */
		HEADER,
		/** A bed nobody has: can be picked. */
		FREE,
		/** The bed they have now. */
		CURRENT,
		/** A bed another villager of the village has: picking it swaps the two. */
		SWAP,
		/** A bed someone has that can't be swapped (they have no bed to give, or its sleeper isn't in the village). */
		TAKEN,
		/** No beds in the village at all. */
		NONE,
		/** "Short of beds? Plan a house ›": opens the Buildings section. */
		PLAN,
		/** What isn't listed and why (wagon beds, cradles). */
		NOTE;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public static State of(String id) {
			try {
				return valueOf(id.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return NOTE;
			}
		}

		/** Whether a click on the line does something. */
		public boolean pickable() {
			return this == FREE || this == SWAP || this == PLAN;
		}
	}

	/** A bed on the list: where, who sleeps there (null: nobody, or someone not in the village), and its place's words. */
	public record Option(BlockPos bed, @Nullable Villager sleeper, boolean taken, Component where, boolean inBuilding) {
	}

	/** The list open on a hall screen: whose, and the beds in the order its lines showed them. */
	private record Open(UUID villager, List<Shown> beds) {
	}

	/** A bed line as it was shown: the bed, and who slept there then (null: it was free). */
	private record Shown(BlockPos bed, @Nullable UUID sleeper) {
	}

	private static final Map<ChoiceMenu, Open> OPEN_LISTS = new WeakHashMap<>();

	/** The bed {@code villager} has now in this dimension, the point of interest still there (B362), or null. */
	@Nullable
	public static BlockPos bed(ServerLevel level, Villager villager) {
		return VillageNeeds.bed(level, villager);
	}

	/** The village's beds for {@code villager}'s list: free ones first (nearest the hall), then the taken ones. */
	public static List<Option> options(ServerLevel level, BlockPos hall) {
		PoiManager poi = level.getPoiManager();
		Map<BlockPos, Villager> sleepers = new HashMap<>();
		for (Villager v : VillageHalls.villagers(level, hall)) {
			BlockPos home = bed(level, v);
			if (home != null) {
				sleepers.put(home, v);
			}
		}
		List<Homes.Building> buildings = Homes.all(level);
		List<BlockPos> beds = VillageHalls.beds(level, hall).map(BlockPos::immutable).filter(b -> !CampBeds.isWagonBed(level, b))
			.sorted(Comparator.comparingDouble((BlockPos b) -> b.distSqr(hall)).thenComparingLong(BlockPos::asLong)).toList();
		List<Option> free = new ArrayList<>();
		List<Option> taken = new ArrayList<>();
		for (BlockPos b : beds) {
			Optional<Homes.Building> building = Homes.in(buildings, b);
			Component where;
			if (building.isPresent()) {
				List<BlockPos> inIt = beds.stream().filter(o -> building.get().box().isInside(o)).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
				where = Component.translatable("screen.aliveworkplace.hall.bed_list.in_building", building.get().home().name(), inIt.indexOf(b) + 1);
			} else {
				where = VillageHallScreen.where(hall, b);
			}
			Villager sleeper = sleepers.get(b);
			boolean isTaken = sleeper != null || poi.getFreeTickets(b) <= 0;
			(isTaken ? taken : free).add(new Option(b, sleeper, isTaken, where, building.isPresent()));
		}
		free.addAll(taken);
		return free;
	}

	/** Whether the list is open for {@code villager} on {@code menu}. */
	public static boolean isOpen(ChoiceMenu menu, Villager villager) {
		Open open = OPEN_LISTS.get(menu);
		return open != null && open.villager().equals(villager.getUUID());
	}

	/** Closes {@code menu}'s list (Job ▾ opening its own list). */
	static void close(ChoiceMenu menu) {
		OPEN_LISTS.remove(menu);
	}

	/** Puts the list on {@code icon}, {@code villager}'s button, when it's open for them (as the main page is laid out). */
	static void attach(ChoiceMenu menu, ItemStack icon, ServerLevel level, BlockPos hall, Villager villager) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(HallPeople.MARK)) {
			return;
		}
		boolean open = isOpen(menu, villager);
		CompoundTag tag = data.copyTag();
		CompoundTag row = tag.getCompound(HallPeople.MARK);
		row.putBoolean("beds_open", open);
		tag.put(HallPeople.MARK, row);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		if (open) {
			List<Option> order = new ArrayList<>();
			icon.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(lines(level, hall, villager, order)));
			OPEN_LISTS.put(menu, new Open(villager.getUUID(), order.stream()
				.map(o -> new Shown(o.bed(), o.sleeper() == null ? (o.taken() ? new UUID(0, 0) : null) : o.sleeper().getUUID())).toList()));
		}
	}

	/** The list's lines as stacks; {@code order} gets each bed line's bed, in the order of their numbers. */
	public static List<ItemStack> lines(ServerLevel level, BlockPos hall, Villager villager, List<Option> order) {
		List<ItemStack> out = new ArrayList<>();
		Component who = villager.getDisplayName();
		List<Option> options = options(level, hall);
		BlockPos mine = bed(level, villager);
		Option current = options.stream().filter(o -> o.bed().equals(mine)).findFirst().orElse(null);
		List<Component> header = new ArrayList<>();
		header.add((current != null ? Component.translatable("screen.aliveworkplace.hall.bed_list.now", current.where())
			: mine != null ? Component.translatable("screen.aliveworkplace.hall.bed_list.now", VillageHallScreen.where(hall, mine))
			: Component.translatable("screen.aliveworkplace.hall.bed_list.now_none", who)).withStyle(ChatFormatting.GRAY));
		out.add(line(Items.PAPER, Component.translatable("screen.aliveworkplace.hall.bed_list.title", who), State.HEADER, -1, header));
		if (options.isEmpty()) {
			out.add(line(Items.BARRIER, Component.translatable("screen.aliveworkplace.hall.bed_list.none"), State.NONE, -1,
				List.of(Component.translatable("screen.aliveworkplace.hall.bed_list.none_hint").withStyle(ChatFormatting.GRAY))));
		}
		boolean anyFree = false;
		for (Option option : options) {
			int n = order.size();
			order.add(option);
			List<Component> tip = new ArrayList<>();
			Item item = level.getBlockState(option.bed()).getBlock().asItem();
			if (item == Items.AIR) {
				item = Items.RED_BED;
			}
			if (option.bed().equals(mine)) {
				out.add(line(item, Component.translatable("screen.aliveworkplace.hall.bed_list.current", option.where()), State.CURRENT, n, tip));
			} else if (!option.taken()) {
				anyFree = true;
				tip.add(Component.translatable("screen.aliveworkplace.hall.bed_list.free_hint", who).withStyle(ChatFormatting.GREEN));
				out.add(line(item, option.inBuilding() ? Component.translatable("screen.aliveworkplace.hall.bed_list.free_in", option.where())
					: Component.translatable("screen.aliveworkplace.hall.bed_list.free_at", option.where()), State.FREE, n, tip));
			} else if (option.sleeper() != null && mine != null) {
				Component them = option.sleeper().getDisplayName();
				tip.add(Component.translatable("screen.aliveworkplace.hall.bed_list.swap_hint", who, them).withStyle(ChatFormatting.GREEN));
				out.add(line(item, Component.translatable("screen.aliveworkplace.hall.bed_list.swap", option.where(), them), State.SWAP, n, tip));
			} else if (option.sleeper() != null) {
				Component them = option.sleeper().getDisplayName();
				tip.add(Component.translatable("screen.aliveworkplace.hall.bed_list.no_swap", who, them).withStyle(ChatFormatting.GRAY));
				out.add(line(item, Component.translatable("screen.aliveworkplace.hall.bed_list.taken_by", option.where(), them), State.TAKEN, n, tip));
			} else {
				tip.add(Component.translatable("screen.aliveworkplace.hall.bed_list.taken_hint").withStyle(ChatFormatting.GRAY));
				out.add(line(item, Component.translatable("screen.aliveworkplace.hall.bed_list.taken", option.where()), State.TAKEN, n, tip));
			}
		}
		if (!anyFree) {
			out.add(line(Items.BRICKS, Component.translatable("screen.aliveworkplace.hall.bed_list.plan"), State.PLAN, -1,
				List.of(Component.translatable("screen.aliveworkplace.hall.bed_list.plan_hint").withStyle(ChatFormatting.GRAY))));
		}
		out.add(line(Items.BOOK, Component.translatable("screen.aliveworkplace.hall.bed_list.note"), State.NOTE, -1,
			List.of(Component.translatable("screen.aliveworkplace.hall.bed_list.note_more").withStyle(ChatFormatting.GRAY))));
		return out;
	}

	private static ItemStack line(Item item, Component label, State state, int n, List<Component> tip) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, label.copy().withStyle(s -> s.withItalic(false)));
		stack.set(DataComponents.LORE, new ItemLore(tip.stream().map(c -> (Component) c.copy().withStyle(s -> s.withItalic(false))).toList()));
		CompoundTag mark = new CompoundTag();
		mark.putString("state", state.id());
		mark.putInt("id", n);
		CompoundTag tag = new CompoundTag();
		tag.put(MARK, mark);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** The list's lines on a person's button (empty: the list isn't open for them). */
	public static List<ItemStack> lines(ItemStack personIcon) {
		CompoundTag row = HallPeople.row(personIcon);
		if (row == null || !row.getBoolean("beds_open")) {
			return List.of();
		}
		ItemContainerContents contents = personIcon.get(DataComponents.CONTAINER);
		return contents == null ? List.of() : contents.nonEmptyStream().toList();
	}

	/** A line's mark: its state and its number. */
	@Nullable
	public static CompoundTag mark(ItemStack line) {
		CustomData data = line.get(DataComponents.CUSTOM_DATA);
		return data == null || !data.contains(MARK) ? null : data.copyTag().getCompound(MARK);
	}

	// --- moving in -------------------------------------------------------------------------------------------------

	/**
	 * {@code villager}'s home is {@code bed} from now on: their old bed freed, the new one claimed (its place taken, their
	 * home remembered), as vanilla gives a villager a bed. False if somebody had it already.
	 */
	public static boolean moveIn(ServerLevel level, Villager villager, BlockPos bed) {
		Optional<BlockPos> took = level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
		if (took.isEmpty()) {
			return false;
		}
		wake(villager);
		villager.releasePoi(MemoryModuleType.HOME); // the old bed is free for someone else
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
		return true;
	}

	/** {@code a} and {@code b} swap beds: each takes the other's (both stay claimed, so nobody else can slip in). */
	public static void swap(ServerLevel level, Villager a, BlockPos bedA, Villager b, BlockPos bedB) {
		wake(a);
		wake(b);
		a.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bedB));
		b.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bedA));
	}

	private static void wake(Villager villager) {
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
	}

	// --- the clicks -------------------------------------------------------------------------------------------------

	/** Whether {@code id} is one of the list's menu-button clicks. */
	public static boolean handles(int id) {
		return id >= OPEN && id < PICK + 100_000;
	}

	/** A menu-button click on the hall's screen ({@link #handles}): opens, closes or picks from the list. */
	static boolean click(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer player, int id) {
		if (id == CLOSE) {
			OPEN_LISTS.remove(menu);
			relayout(menu, level, hall);
			return true;
		}
		if (id < CLOSE) {
			int slot = id - OPEN;
			Villager villager = slot >= 0 && slot < ChoiceMenu.SIZE ? personIn(menu, level, slot) : null;
			if (villager == null || HallPeople.gone(level, hall, villager) || isOpen(menu, villager)) {
				OPEN_LISTS.remove(menu); // Bed ▾ again closes it
			} else {
				HallJobs.close(menu); // one list at a time
				OPEN_LISTS.put(menu, new Open(villager.getUUID(), List.of()));
			}
			relayout(menu, level, hall);
			return true;
		}
		Open open = OPEN_LISTS.get(menu);
		Villager villager = open == null ? null : level.getEntity(open.villager()) instanceof Villager v ? v : null;
		int n = id - PICK;
		if (villager == null || HallPeople.gone(level, hall, villager) || n < 0 || n >= open.beds().size()) {
			if (villager == null || HallPeople.gone(level, hall, villager)) {
				OPEN_LISTS.remove(menu);
			}
			relayout(menu, level, hall);
			return true;
		}
		Shown shown = open.beds().get(n);
		BlockPos bed = shown.bed();
		Component who = villager.getDisplayName();
		Option option = options(level, hall).stream().filter(o -> o.bed().equals(bed)).findFirst().orElse(null);
		BlockPos mine = bed(level, villager);
		UUID sleeperNow = option == null ? null : option.sleeper() != null ? option.sleeper().getUUID() : option.taken() ? new UUID(0, 0) : null;
		if (option != null && !bed.equals(mine) && !java.util.Objects.equals(sleeperNow, shown.sleeper())) {
			// Someone claimed it (or left it) since the list was laid out: nothing changes, the list shows it as it is now.
			Chat.actionBar(player, Component.translatable(shown.sleeper() == null ? "message.aliveworkplace.hall.beds.taken_first" : "message.aliveworkplace.hall.beds.changed")
				.withStyle(ChatFormatting.YELLOW));
			no(level, player);
		} else if (option == null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall.beds.gone").withStyle(ChatFormatting.YELLOW));
			no(level, player);
		} else if (bed.equals(mine)) {
			return true;
		} else if (!option.taken()) {
			if (moveIn(level, villager, bed)) {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall.beds.moved", who, option.where()).withStyle(ChatFormatting.GREEN));
				level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
				OPEN_LISTS.remove(menu);
			} else {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall.beds.taken_first").withStyle(ChatFormatting.YELLOW));
				no(level, player);
			}
		} else if (option.sleeper() != null && mine != null && option.sleeper() != villager) {
			swap(level, villager, mine, option.sleeper(), bed);
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall.beds.swapped", who, option.sleeper().getDisplayName())
				.withStyle(ChatFormatting.GREEN));
			level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
			OPEN_LISTS.remove(menu);
		} else {
			// Taken since the list was laid out (somebody claimed it first), or by someone who can't be swapped with.
			Chat.actionBar(player, option.sleeper() != null && mine == null
				? Component.translatable("screen.aliveworkplace.hall.bed_list.no_swap", who, option.sleeper().getDisplayName()).withStyle(ChatFormatting.YELLOW)
				: Component.translatable("message.aliveworkplace.hall.beds.taken_first").withStyle(ChatFormatting.YELLOW));
			no(level, player);
		}
		relayout(menu, level, hall);
		return true;
	}

	private static void no(ServerLevel level, ServerPlayer player) {
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
	}

	/** The villager whose row is in {@code slot} (null: none, or not loaded). */
	@Nullable
	private static Villager personIn(ChoiceMenu menu, ServerLevel level, int slot) {
		CompoundTag row = HallPeople.row(menu.icon(slot));
		if (row == null || row.getString("uuid").isEmpty()) {
			return null;
		}
		try {
			return level.getEntity(UUID.fromString(row.getString("uuid"))) instanceof Villager v ? v : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Lays the main page out again where the table is, so the rows (and the open list) show what's true now. */
	private static void relayout(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		VillageHallScreen.renderFrom(menu, level, hall, menu.state(HallSections.PEOPLE_FIRST));
		menu.broadcastChanges();
	}

	/** Forgets every open list (tests). */
	public static void forget() {
		OPEN_LISTS.clear();
	}

	private HallBeds() {
	}
}
