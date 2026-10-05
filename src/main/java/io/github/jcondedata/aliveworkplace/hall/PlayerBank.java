package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.legend.BankPower;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The hall's bank page (29.17, its button on the hall's second row; the Merchant Prince's {@code bank}): players put emeralds in, up to {@link #MAX_EMERALDS}
 * (10 stacks) each, kept per player in the Village Hall; deposits earn {@link #WEEKLY_PERCENT}% a week while the
 * village has a bank, and come out any time, even after the Prince is gone (in CobbleDollars at the usual rate when the
 * pack has them, like the treasury).
 */
public final class PlayerBank {
	public static final String PAGE = "bank";
	/** Most emeralds one player keeps in one hall: ten stacks. */
	public static final int MAX_EMERALDS = 640;
	public static final int WEEKLY_PERCENT = 5;
	static final int WEEK = 7;
	/** Weeks the hall wasn't loaded that still earn. */
	static final int MAX_WEEKS = 4;

	/** What each player holds in one hall, in hundredths of an emerald, and the day the bank's week started. */
	public static final class State {
		private final Map<UUID, Long> cents = new HashMap<>();
		/** {@link Long#MIN_VALUE}: not counted yet (an early world's day less 7 can be below 0). */
		private long weekStart = Long.MIN_VALUE;

		public long cents(UUID player) {
			return cents.getOrDefault(player, 0L);
		}

		public int emeralds(UUID player) {
			return (int) (cents(player) / 100);
		}

		void set(UUID player, long value) {
			if (value <= 0) {
				cents.remove(player);
			} else {
				cents.put(player, Math.min(MAX_EMERALDS * 100L, value));
			}
		}

		public long weekStart() {
			return weekStart;
		}

		public void setWeekStart(long day) {
			weekStart = day;
		}

		public static State load(CompoundTag tag) {
			State s = new State();
			s.weekStart = tag.contains("bankWeek") ? Nbt.getLong(tag, "bankWeek") : Long.MIN_VALUE;
			ListTag list = Nbt.getList(tag, "bankDeposits", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag e = Nbt.compoundAt(list, i);
				if (Nbt.hasUuid(e, "player")) {
					s.set(Nbt.getUuid(e, "player"), Nbt.getLong(e, "cents"));
				}
			}
			return s;
		}

		public void save(CompoundTag tag) {
			tag.putLong("bankWeek", weekStart);
			ListTag list = new ListTag();
			cents.forEach((player, value) -> {
				CompoundTag e = new CompoundTag();
				Nbt.putUuid(e, "player", player);
				e.putLong("cents", value);
				list.add(e);
			});
			tag.put("bankDeposits", list);
		}
	}

	/** The bank's button on the hall's second row, right of "What next?". */
	public static final int BUTTON = 17;
	/** The page: drawn like the page row's pages (back, header, divider), opened from {@link #BUTTON}. */
	static final HallPages.Page PAGE_DEF = new HallPages.Page(PAGE, PlayerBank::tab, PlayerBank::header, PlayerBank::fill);

	/** Opens the bank page in {@code menu} for {@code viewer}. */
	public static void open(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		VillageHallScreen.renderPage(menu, level, hall, PAGE_DEF, viewer);
		menu.broadcastChanges();
	}

	/** Whether the village round {@code hall} has a bank (a Merchant Prince settled there). */
	public static boolean open(ServerLevel level, BlockPos hall) {
		return BankPower.of(level, hall).isPresent();
	}

	/** The hall's round: a week's interest on every deposit, once a week, while the village has a bank. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		State state = entity.playerBank();
		long day = Chronicle.day(level);
		if (state.weekStart == Long.MIN_VALUE) {
			state.weekStart = day;
			entity.setChanged();
			return;
		}
		if (day - state.weekStart < WEEK) {
			return;
		}
		long weeks = (day - state.weekStart) / WEEK;
		state.weekStart += weeks * WEEK;
		if (open(level, hall)) {
			for (long w = 0; w < Math.min(weeks, MAX_WEEKS); w++) {
				for (Map.Entry<UUID, Long> e : Map.copyOf(state.cents).entrySet()) {
					state.set(e.getKey(), e.getValue() + e.getValue() * WEEKLY_PERCENT / 100);
				}
			}
		}
		entity.setChanged();
	}

	/** {@code player} puts {@code emeralds} in (no more than they have, nor past the 10 stacks); what to tell them. */
	public static Component deposit(ServerLevel level, BlockPos hall, ServerPlayer player, int emeralds) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Component.empty();
		}
		if (!open(level, hall)) {
			return Component.translatable("message.aliveworkplace.bank.closed").withStyle(ChatFormatting.GRAY);
		}
		State state = entity.playerBank();
		int room = MAX_EMERALDS - (int) ((state.cents(player.getUUID()) + 99) / 100);
		int amount = Math.min(emeralds, room);
		if (!Money.cobbleDollars()) {
			amount = Math.min(amount, player.getInventory().countItem(Items.EMERALD));
		}
		if (room <= 0) {
			return Component.translatable("message.aliveworkplace.bank.full", MAX_EMERALDS / 64).withStyle(ChatFormatting.RED);
		}
		boolean paid = amount > 0 && (Money.cobbleDollars() ? Money.charge(player, (long) amount * Money.DOLLARS_PER_EMERALD, amount)
			: player.getInventory().clearOrCountMatchingItems(st -> st.is(Items.EMERALD), amount, player.inventoryMenu.getCraftSlots()) == amount);
		if (!paid) {
			return Component.translatable("message.aliveworkplace.bank.no_money").withStyle(ChatFormatting.RED);
		}
		state.set(player.getUUID(), state.cents(player.getUUID()) + amount * 100L);
		entity.setChanged();
		level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.9f);
		return Component.translatable("message.aliveworkplace.bank.deposited", Money.describe((long) amount * Money.DOLLARS_PER_EMERALD, amount),
			state.emeralds(player.getUUID())).withStyle(ChatFormatting.GREEN);
	}

	/** {@code player} takes out up to {@code emeralds} whole emeralds (or their worth in CobbleDollars); what to tell them. */
	public static Component withdraw(ServerLevel level, BlockPos hall, ServerPlayer player, int emeralds) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Component.empty();
		}
		State state = entity.playerBank();
		int amount = Math.min(emeralds, state.emeralds(player.getUUID()));
		if (amount <= 0) {
			return Component.translatable("message.aliveworkplace.bank.empty").withStyle(ChatFormatting.GRAY);
		}
		state.set(player.getUUID(), state.cents(player.getUUID()) - amount * 100L);
		entity.setChanged();
		Money.pay(player, (long) amount * Money.DOLLARS_PER_EMERALD, amount);
		level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
		return Component.translatable("message.aliveworkplace.bank.withdrawn", Money.describe((long) amount * Money.DOLLARS_PER_EMERALD, amount),
			state.emeralds(player.getUUID())).withStyle(ChatFormatting.GREEN);
	}

	static ItemStack tab(ServerLevel level, BlockPos hall) {
		boolean open = open(level, hall);
		return VillageHallScreen.icon(Items.GOLD_INGOT, Component.translatable("screen.aliveworkplace.bank.tab"), ChatFormatting.GOLD,
			VillageHallScreen.line(open ? Component.translatable("screen.aliveworkplace.bank.open", WEEKLY_PERCENT)
				: Component.translatable("screen.aliveworkplace.bank.closed"), open ? ChatFormatting.GREEN : ChatFormatting.GRAY));
	}

	static ItemStack header(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(Items.GOLD_INGOT, Component.translatable("screen.aliveworkplace.bank.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.bank.about", MAX_EMERALDS / 64, WEEKLY_PERCENT), ChatFormatting.GRAY),
			VillageHallScreen.line(open(level, hall) ? Component.translatable("screen.aliveworkplace.bank.open", WEEKLY_PERCENT)
				: Component.translatable("screen.aliveworkplace.bank.closed"), ChatFormatting.GRAY));
	}

	/** Slots of the page's buttons (tests press them): the balance, then put in 1 / 16 / 64, then take out 1 / 16 / all. */
	public static final int BALANCE = VillageHallScreen.FIRST_ROW + 4;
	public static final int DEPOSIT = VillageHallScreen.FIRST_ROW + 9 + 1;
	public static final int WITHDRAW = VillageHallScreen.FIRST_ROW + 9 + 5;
	private static final int[] AMOUNTS = {1, 16, 64};

	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		int held = entity.playerBank().emeralds(viewer.getUUID());
		menu.button(BALANCE, VillageHallScreen.icon(new ItemStack(Items.EMERALD, Math.max(1, Math.min(64, held))),
			Component.translatable("screen.aliveworkplace.bank.balance", held, MAX_EMERALDS), ChatFormatting.GREEN,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.bank.you_have", Money.balance(viewer)), ChatFormatting.GRAY)), null);
		boolean open = open(level, hall);
		for (int i = 0; i < AMOUNTS.length; i++) {
			int n = AMOUNTS[i];
			menu.button(DEPOSIT + i, VillageHallScreen.icon(new ItemStack(Items.EMERALD, n), Component.translatable("screen.aliveworkplace.bank.deposit", n),
				open ? ChatFormatting.GREEN : ChatFormatting.GRAY,
				VillageHallScreen.line(open ? "screen.aliveworkplace.bank.deposit_hint" : "screen.aliveworkplace.bank.closed", ChatFormatting.GRAY)), p -> {
					p.sendSystemMessage(deposit(level, hall, p, n));
					refill(menu, level, hall, p);
				});
			boolean all = i == AMOUNTS.length - 1;
			menu.button(WITHDRAW + i, VillageHallScreen.icon(new ItemStack(Items.GOLD_NUGGET, n),
				all ? Component.translatable("screen.aliveworkplace.bank.withdraw_all") : Component.translatable("screen.aliveworkplace.bank.withdraw", n),
				ChatFormatting.YELLOW, VillageHallScreen.line("screen.aliveworkplace.bank.withdraw_hint", ChatFormatting.GRAY)), p -> {
					p.sendSystemMessage(withdraw(level, hall, p, all ? MAX_EMERALDS : n));
					refill(menu, level, hall, p);
				});
		}
	}

	private static void refill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		open(menu, level, hall, viewer);
	}

	private PlayerBank() {
	}
}
