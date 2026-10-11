package io.github.jcondedata.aliveworkplace.realm;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The realm treasury (ROADMAP 33.13). Each member pays the realm's levy (0, 5, 10 or 20% of its daily takings, set by
 * the capital's owner on the Realm tab) into it before the rest goes into its own treasury ({@link #levy}, from
 * {@code Treasury.round}). It is kept on the realm in {@link RealmData}, so it never needs the capital loaded, and holds
 * up to 256 emeralds for a Barony, twice as many a rank up to 2,048 for a Kingdom ({@link #cap}); a levy that doesn't
 * fit stays with the village. Anyone may give to it (click its icon with emeralds: one, shift: the stack; or, with
 * CobbleDollars, an emerald's or a stack's worth from their balance); only the capital's owner, their friends and
 * operators take from it (right-click: one emerald, shift: a stack). Later items pay from it with {@link #spend}. Its
 * icon lists the last {@link #MOVEMENTS} movements with the day and the reason.
 */
public final class RealmTreasury {
	/** The levies the capital's owner may choose, in percent, in the order a click steps through them. */
	public static final int[] LEVIES = {0, 5, 10, 20};
	/** Movements the treasury remembers. */
	public static final int MOVEMENTS = 20;

	/** The Realm tab's slots: the treasury and the levy, in the title's row. */
	public static final int TREASURY = VillageHallScreen.FIRST_ROW + 2;
	public static final int LEVY = VillageHallScreen.FIRST_ROW + 4;

	/** Most emeralds the treasury of a realm of {@code rank} holds: 256 for a Barony, doubling each rank up. */
	public static int cap(Realms.Rank rank) {
		return 256 << rank.ordinal();
	}

	/** Most the treasury of {@code realm} holds, in hundredths of an emerald. */
	public static long capCents(MinecraftServer server, RealmData.Realm realm) {
		return cap(server, realm) * 100L;
	}

	/** Most emeralds {@code realm}'s treasury holds: its rank's cap, doubled by a finished Royal Hall by the capital's hall, tripled by a Royal Hall II (33.14). */
	public static int cap(MinecraftServer server, RealmData.Realm realm) {
		return cap(Realms.Rank.parse(realm.rank())) * RoyalHalls.multiplier(server, realm);
	}

	/** The levy on {@code takings} at {@code percent}, in hundredths of an emerald (rounded down). */
	public static long levyOn(long takings, int percent) {
		return Math.max(0, takings) * Math.max(0, percent) / 100;
	}

	/**
	 * The hall's daily takings of {@code takings} (hundredths of an emerald) go in: if its village is in a realm (and
	 * realms are on), the realm's levy on them goes into the realm treasury, as far as it has room. What was taken.
	 */
	public static long levy(ServerLevel level, BlockPos hall, long takings) {
		if (!Realms.ENABLED || takings <= 0) {
			return 0;
		}
		MinecraftServer server = level.getServer();
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(GlobalPos.of(level.dimension(), hall));
		if (realm == null) {
			return 0;
		}
		long cut = Math.min(levyOn(takings, realm.levy()), Math.max(0, capCents(server, realm) - realm.treasury()));
		if (cut <= 0) {
			return 0;
		}
		move(server, realm, cut, Component.translatable("screen.aliveworkplace.realm.treasury.levy_from", VillageHalls.name(level, hall).getString()));
		return cut;
	}

	/**
	 * Pays {@code cents} out of {@code realm}'s treasury for {@code reason} (realm research, relief riders, a member's
	 * colony short of money, edicts, M35's Wonders), noted in its movements. False, and nothing paid, if it holds less.
	 */
	public static boolean spend(MinecraftServer server, RealmData.Realm realm, long cents, Component reason) {
		RealmData.Realm now = RealmData.get(server).realm(realm.id());
		if (now == null || cents < 0 || now.treasury() < cents) {
			return false;
		}
		if (cents > 0) {
			move(server, now, -cents, reason);
		}
		return true;
	}

	/** {@code cents} in (or out, negative) of {@code realm}'s treasury, with a movement for {@code reason} today. */
	static RealmData.Realm move(MinecraftServer server, RealmData.Realm realm, long cents, Component reason) {
		List<RealmData.Movement> ledger = new ArrayList<>(realm.ledger());
		ledger.add(new RealmData.Movement(Chronicle.day(server.overworld()), cents, reason));
		while (ledger.size() > MOVEMENTS) {
			ledger.remove(0);
		}
		RealmData.Realm kept = realm.withTreasury(Math.max(0, realm.treasury() + cents), ledger);
		RealmData.get(server).putRealm(kept);
		return kept;
	}

	/** Whether {@code player} may take from {@code realm}'s treasury: the capital's owner, their friends and operators. */
	public static boolean mayTake(MinecraftServer server, RealmData.Realm realm, Player player) {
		return Realms.mayRule(server, realm.capital(), player);
	}

	/**
	 * {@code player} gives from the Realm tab of the member at {@code hall}: {@code carried} emeralds (one, or with
	 * {@code all} the stack, as far as the treasury has room), or with nothing carried and CobbleDollars in use, an
	 * emerald's or a stack's worth from their balance.
	 */
	public static Realms.Result give(ServerLevel level, BlockPos hall, ServerPlayer player, ItemStack carried, boolean all) {
		MinecraftServer server = level.getServer();
		RealmData.Realm realm = Realms.realmOf(level, hall);
		if (!Realms.ENABLED) {
			return Realms.refused(Realms.Refusal.OFF, Component.translatable("message.aliveworkplace.realm.off"));
		}
		if (realm == null) {
			return Realms.refused(Realms.Refusal.NOT_MEMBER, Component.translatable("message.aliveworkplace.realm.treasury.no_realm", VillageHalls.name(level, hall)));
		}
		boolean emeralds = carried.is(Items.EMERALD);
		if (!emeralds && !(carried.isEmpty() && Money.cobbleDollars())) {
			return Realms.refused(Realms.Refusal.NO_MONEY, Component.translatable("message.aliveworkplace.realm.treasury.give_how"));
		}
		int room = (int) Math.min(Integer.MAX_VALUE, (capCents(server, realm) - realm.treasury()) / 100);
		if (room <= 0) {
			return Realms.refused(Realms.Refusal.FULL, Component.translatable("message.aliveworkplace.realm.treasury.full", Realms.title(server, realm),
				cap(server, realm)));
		}
		int count;
		if (emeralds) {
			count = Math.min(room, all ? carried.getCount() : 1);
			carried.shrink(count);
		} else {
			count = Math.min(room, all ? 64 : 1);
			if (!Money.charge(player, (long) count * Money.DOLLARS_PER_EMERALD, count)) {
				return Realms.refused(Realms.Refusal.NO_MONEY, Component.translatable("message.aliveworkplace.realm.treasury.cant_afford",
					Money.describe((long) count * Money.DOLLARS_PER_EMERALD, count)));
			}
		}
		RealmData.Realm kept = move(server, realm, count * 100L, Component.translatable("screen.aliveworkplace.realm.treasury.given_by", player.getGameProfile().getName()));
		level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.4f);
		return new Realms.Result(null, Component.translatable("message.aliveworkplace.realm.treasury.given",
			Money.describe((long) count * Money.DOLLARS_PER_EMERALD, count), Realms.title(server, kept), emeralds(kept.treasury())).withStyle(ChatFormatting.GREEN));
	}

	/** {@code player} takes one emerald (with {@code all}, up to a stack) from the treasury, if they may; paid in CobbleDollars with the pack. */
	public static Realms.Result take(ServerLevel level, BlockPos hall, ServerPlayer player, boolean all) {
		MinecraftServer server = level.getServer();
		RealmData.Realm realm = Realms.realmOf(level, hall);
		if (!Realms.ENABLED) {
			return Realms.refused(Realms.Refusal.OFF, Component.translatable("message.aliveworkplace.realm.off"));
		}
		if (realm == null) {
			return Realms.refused(Realms.Refusal.NOT_MEMBER, Component.translatable("message.aliveworkplace.realm.treasury.no_realm", VillageHalls.name(level, hall)));
		}
		if (!mayTake(server, realm, player)) {
			return Realms.refused(Realms.Refusal.NOT_YOURS, Component.translatable("message.aliveworkplace.realm.treasury.not_yours",
				Realms.ownerName(server, realm.capital()), Realms.title(server, realm)));
		}
		int count = (int) Math.min(all ? 64 : 1, realm.treasury() / 100);
		if (count <= 0) {
			return Realms.refused(Realms.Refusal.NO_MONEY, Component.translatable("message.aliveworkplace.realm.treasury.empty", Realms.title(server, realm)));
		}
		RealmData.Realm kept = move(server, realm, -count * 100L, Component.translatable("screen.aliveworkplace.realm.treasury.taken_by", player.getGameProfile().getName()));
		Money.pay(player, (long) count * Money.DOLLARS_PER_EMERALD, count);
		level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.0f);
		return new Realms.Result(null, Component.translatable("message.aliveworkplace.realm.treasury.taken",
			Money.describe((long) count * Money.DOLLARS_PER_EMERALD, count), Realms.title(server, kept), emeralds(kept.treasury())).withStyle(ChatFormatting.GREEN));
	}

	/** The capital's owner steps the levy to the next of {@link #LEVIES} (from the capital's tab). */
	public static Realms.Result nextLevy(ServerLevel level, BlockPos capitalHall, ServerPlayer player) {
		RealmData.Realm realm = Realms.realmOf(level, capitalHall);
		int now = realm == null ? RealmData.Realm.DEFAULT_LEVY : realm.levy();
		int next = LEVIES[0];
		for (int i = 0; i < LEVIES.length; i++) {
			if (LEVIES[i] == now) {
				next = LEVIES[(i + 1) % LEVIES.length];
			}
		}
		return setLevy(level, capitalHall, player, next);
	}

	/** The capital's owner sets the levy to {@code percent} (one of {@link #LEVIES}). */
	public static Realms.Result setLevy(ServerLevel level, BlockPos capitalHall, ServerPlayer player, int percent) {
		MinecraftServer server = level.getServer();
		GlobalPos capital = GlobalPos.of(level.dimension(), capitalHall);
		RealmData.Realm realm = RealmData.get(server).realmOf(capital);
		Component village = VillageHalls.name(level, capitalHall);
		Realms.Refusal refusal = Realms.refusalToRule(server, realm, capital, player);
		if (refusal != null) {
			return Realms.refused(refusal, Realms.ruleRefusal(server, refusal, realm, capital, village));
		}
		int levy = java.util.Arrays.stream(LEVIES).anyMatch(l -> l == percent) ? percent : RealmData.Realm.DEFAULT_LEVY;
		RealmData.Realm kept = realm.withLevy(levy);
		RealmData.get(server).putRealm(kept);
		java.util.Set<java.util.UUID> told = new java.util.HashSet<>(List.of(player.getUUID())); // the other members' owners online hear of it
		for (RealmData.Member m : kept.members()) {
			java.util.UUID owner = Realms.ownerOf(server, m.hall());
			ServerPlayer them = owner == null || !told.add(owner) ? null : server.getPlayerList().getPlayer(owner);
			if (them != null) {
				io.github.jcondedata.aliveworkplace.mc.Chat.chat(them, Component.translatable("message.aliveworkplace.realm.treasury.levy_set", Realms.title(server, kept), levy)
					.withStyle(ChatFormatting.GOLD));
			}
		}
		return new Realms.Result(null, Component.translatable("message.aliveworkplace.realm.treasury.levy_set", Realms.title(server, kept), levy).withStyle(ChatFormatting.GREEN));
	}

	/** "12.30" emeralds, from hundredths. */
	public static String emeralds(long cents) {
		return String.format(Locale.ROOT, "%.2f", cents / 100.0);
	}

	/** The treasury's icon: an emerald block; what it holds of its cap, the levy, the last movements and how to give or take. */
	static ItemStack icon(MinecraftServer server, RealmData.Realm realm, @Nullable ServerPlayer viewer) {
		List<Component> lore = new ArrayList<>();
		int cap = cap(server, realm);
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.treasury.holds", emeralds(realm.treasury()), cap), ChatFormatting.GREEN));
		int times = RoyalHalls.multiplier(server, realm);
		if (times > 1) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.treasury.royal_hall", times), ChatFormatting.GOLD));
		}
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.treasury.levy_line", realm.levy()), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line("screen.aliveworkplace.realm.treasury.pays_for", ChatFormatting.GRAY));
		if (realm.ledger().isEmpty()) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.realm.treasury.no_movements", ChatFormatting.DARK_GRAY));
		} else {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.realm.treasury.movements", ChatFormatting.GOLD));
			List<RealmData.Movement> ledger = realm.ledger();
			for (int i = ledger.size() - 1; i >= 0; i--) { // newest first
				lore.add(movementLine(ledger.get(i)));
			}
		}
		lore.add(VillageHallScreen.line(Money.cobbleDollars() ? Component.translatable("screen.aliveworkplace.realm.treasury.give_dollars", Money.DOLLARS_PER_EMERALD,
			64 * Money.DOLLARS_PER_EMERALD) : Component.translatable("screen.aliveworkplace.realm.treasury.give_emeralds"), ChatFormatting.DARK_GRAY));
		lore.add(VillageHallScreen.line(viewer != null && mayTake(server, realm, viewer) ? "screen.aliveworkplace.realm.treasury.take_how"
			: "screen.aliveworkplace.realm.treasury.take_who", ChatFormatting.DARK_GRAY));
		return VillageHallScreen.icon(Items.EMERALD_BLOCK, Component.translatable("screen.aliveworkplace.realm.treasury.name"), ChatFormatting.GREEN,
			lore.toArray(Component[]::new));
	}

	/** "Day 12: +0.30 Levy from Thornholm", green in, red out. */
	public static Component movementLine(RealmData.Movement m) {
		String amount = (m.cents() >= 0 ? "+" : "-") + emeralds(Math.abs(m.cents()));
		return VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.treasury.movement", m.day(), amount, m.reason()),
			m.cents() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	/** The levy's icon: a hopper; the levy, what it means, and for the capital's owner, how to change it. */
	static ItemStack levyIcon(RealmData.Realm realm, boolean mayChange) {
		return VillageHallScreen.icon(Items.HOPPER, Component.translatable("screen.aliveworkplace.realm.treasury.levy", realm.levy()), ChatFormatting.GOLD,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.treasury.levy_about", realm.levy()), ChatFormatting.GRAY),
			VillageHallScreen.line(mayChange ? "screen.aliveworkplace.realm.treasury.levy_how" : "screen.aliveworkplace.realm.treasury.levy_capital", ChatFormatting.DARK_GRAY));
	}

	private RealmTreasury() {
	}
}
