package io.github.jcondedata.aliveworkplace.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.mail.Mail;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Trade pacts and alliances between villages of different owners (ROADMAP 33.18). On the hall's Pacts tab an owner
 * offers a pact or an alliance to another player's village within caravan range ({@link #offer}); a letter reaches its
 * owner and an Accept and a Decline line wait on that village's Pacts tab for {@link #LAPSE} ticks (3 days). A caravan
 * route to another owner's village needs a pact ({@link #mayRoute}); routes saved before 1.7 become pacts when the world
 * loads ({@link #migrate}). Pact caravans are paid for what the other waits for, unless the sending side gives
 * ({@link #gives}). An alliance also brings relief riders to each other's raids ({@link #allies}, {@code Relief.call}).
 * Either side ends a pact at once, an alliance with a day's notice ({@link #end}), by letter; the chronicles note each.
 * Configs {@code tradePacts}, {@code alliances}.
 */
public final class Pacts {
	/** Config {@code tradePacts}: off, routes to anyone's village need no pact and no offers are made; pacts stay saved. */
	public static boolean ENABLED = true;
	/** Config {@code alliances}: off, no alliance is offered and allies send no riders; alliances stay saved. */
	public static boolean ALLIANCES = true;
	/** How long an offer waits: 3 days. */
	public static final long LAPSE = 3 * 24000L;
	/** An alliance ends a day after notice. */
	public static final long NOTICE = 24000L;

	/** A pact between {@code a} (who offered) and {@code b}; {@code endsAt} is -1 unless an alliance's notice runs. */
	public record Pact(GlobalPos a, GlobalPos b, boolean alliance, long since, long endsAt, boolean giftsA, boolean giftsB) {
		public static final Codec<Pact> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("a").forGetter(Pact::a),
			GlobalPos.CODEC.fieldOf("b").forGetter(Pact::b),
			Codec.BOOL.optionalFieldOf("alliance", false).forGetter(Pact::alliance),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Pact::since),
			Codec.LONG.optionalFieldOf("endsAt", -1L).forGetter(Pact::endsAt),
			Codec.BOOL.optionalFieldOf("giftsA", false).forGetter(Pact::giftsA),
			Codec.BOOL.optionalFieldOf("giftsB", false).forGetter(Pact::giftsB)
		).apply(i, Pact::new));

		public boolean has(GlobalPos v) {
			return a.equals(v) || b.equals(v);
		}

		public GlobalPos other(GlobalPos v) {
			return a.equals(v) ? b : a;
		}

		public boolean between(GlobalPos x, GlobalPos y) {
			return a.equals(x) && b.equals(y) || a.equals(y) && b.equals(x);
		}

		/** Whether {@code v}'s side gives its caravans' cargo. */
		public boolean gives(GlobalPos v) {
			return a.equals(v) ? giftsA : giftsB;
		}
	}

	/** An offer from the village at {@code from} to the one at {@code to}, owned by {@code owner}, sent at game time {@code sent}. */
	public record Offer(GlobalPos from, GlobalPos to, boolean alliance, UUID owner, long sent) {
		public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("from").forGetter(Offer::from),
			GlobalPos.CODEC.fieldOf("to").forGetter(Offer::to),
			Codec.BOOL.optionalFieldOf("alliance", false).forGetter(Offer::alliance),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Offer::owner),
			Codec.LONG.optionalFieldOf("sent", 0L).forGetter(Offer::sent)
		).apply(i, Offer::new));
	}

	/** What came of a click: ok, or why not, and the line for the player. */
	public record Result(boolean ok, Component message) {
	}

	/** The pacts and the offers, kept with the overworld. */
	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_pacts";
		final List<Pact> pacts = new ArrayList<>();
		final List<Offer> offers = new ArrayList<>();
		/** Whether the routes saved before 1.7 have been made pacts (older saves: false). */
		boolean migrated;

		public static Data get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		public List<Pact> pacts() {
			return List.copyOf(pacts);
		}

		public List<Offer> offers() {
			return List.copyOf(offers);
		}

		@Nullable
		public Pact pact(GlobalPos x, GlobalPos y) {
			return pacts.stream().filter(p -> p.between(x, y)).findFirst().orElse(null);
		}

		@Nullable
		public Offer offer(GlobalPos from, GlobalPos to) {
			return offers.stream().filter(o -> o.from().equals(from) && o.to().equals(to)).findFirst().orElse(null);
		}

		/** Adds an offer as it stands (tests: one sent days ago). */
		public void addOffer(Offer offer) {
			offers.add(offer);
			setDirty();
		}

		/** Writes a pact, in place of any between the same two villages. */
		public void put(Pact pact) {
			pacts.removeIf(p -> p.between(pact.a(), pact.b()));
			pacts.add(pact);
			setDirty();
		}

		void remove(Pact pact) {
			pacts.removeIf(p -> p.between(pact.a(), pact.b()));
			setDirty();
		}

		void removeOffers(GlobalPos x, GlobalPos y) {
			offers.removeIf(o -> o.from().equals(x) && o.to().equals(y) || o.from().equals(y) && o.to().equals(x));
			setDirty();
		}

		/** Empties the lists (tests). */
		public void clear() {
			pacts.clear();
			offers.clear();
			setDirty();
		}

		/** Marks the pre-1.7 routes as not yet made pacts (tests of the migration). */
		public void setMigrated(boolean migrated) {
			this.migrated = migrated;
			setDirty();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			var ops = registries.createSerializationContext(NbtOps.INSTANCE);
			Pact.CODEC.listOf().encodeStart(ops, pacts).result().ifPresent(t -> tag.put("pacts", t));
			Offer.CODEC.listOf().encodeStart(ops, offers).result().ifPresent(t -> tag.put("offers", t));
			tag.putBoolean("migrated", migrated);
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			var ops = registries.createSerializationContext(NbtOps.INSTANCE);
			Data data = new Data();
			read(Pact.CODEC, tag.get("pacts"), ops, data.pacts, "pacts");
			read(Offer.CODEC, tag.get("offers"), ops, data.offers, "pact offers");
			data.migrated = Nbt.getBoolean(tag, "migrated");
			return data;
		}

		private static <T> void read(Codec<T> codec, @Nullable Tag tag, com.mojang.serialization.DynamicOps<Tag> ops, List<T> into, String what) {
			if (tag != null) {
				codec.listOf().parse(ops, tag).resultOrPartial(e -> AliveWorkplace.LOG.warn("Couldn't read the {} in {}: {}", what, NAME, e))
					.ifPresent(into::addAll);
			}
		}
	}

	public static void init() {
		Platform.get().onServerTick(Pacts::serverTick);
		TradePage.register(TradePage.Tab.PACTS, PactsTab::icon, PactsTab::fill, () -> ENABLED || Feuds.ENABLED);
	}

	static void serverTick(MinecraftServer server) {
		if (server.overworld().getGameTime() % 20 == 0) {
			settle(server);
		}
	}

	/** Each second: pre-1.7 routes become pacts (once), offers lapse, alliances whose day's notice ran out end. */
	public static void settle(MinecraftServer server) {
		Data data = Data.get(server);
		if (!data.migrated) {
			migrate(server);
		}
		if (!ENABLED) {
			return;
		}
		long now = now(server);
		for (Offer offer : data.offers()) {
			if (now - offer.sent() > LAPSE) {
				data.offers.remove(offer);
				data.setDirty();
				Component them = Realms.name(server, offer.to());
				chronicle(server, offer.from(), Component.translatable("chronicle.aliveworkplace.pact.lapsed", them));
				tell(server, offer.from(), null, Component.translatable("message.aliveworkplace.pact.lapsed", them).withStyle(ChatFormatting.YELLOW));
			}
		}
		for (Pact pact : data.pacts()) {
			if (pact.endsAt() >= 0 && now >= pact.endsAt()) {
				close(server, pact);
			}
		}
	}

	/** Every caravan route between villages of different owners (not friends) becomes a trade pact: saved before 1.7, nothing stops. */
	public static int migrate(MinecraftServer server) {
		Data data = Data.get(server);
		int made = 0;
		for (ServerLevel level : server.getAllLevels()) {
			Caravans.Data caravans = Caravans.Data.get(level);
			for (Caravans.Village v : caravans.villages()) {
				for (BlockPos to : caravans.routesFrom(v.hall())) {
					GlobalPos a = GlobalPos.of(level.dimension(), v.hall());
					GlobalPos b = GlobalPos.of(level.dimension(), to);
					if (strangers(server, a, b) && data.pact(a, b) == null) {
						data.put(new Pact(a, b, false, now(server), -1, false, false));
						made++;
					}
				}
			}
		}
		data.migrated = true;
		data.setDirty();
		return made;
	}

	/** Whether {@code a} and {@code b} are owned by players who aren't the same or friends (both owned). */
	public static boolean strangers(MinecraftServer server, GlobalPos a, GlobalPos b) {
		UUID x = Realms.ownerOf(server, a);
		UUID y = Realms.ownerOf(server, b);
		if (x == null || y == null || x.equals(y)) {
			return false;
		}
		Friends friends = Friends.get(server);
		return !friends.mayDirect(x, y) && !friends.mayDirect(y, x);
	}

	/** Whether the village at {@code from} may send caravans to the one at {@code to}: the same owner (or friends), or a pact, or pacts off. */
	public static boolean mayRoute(ServerLevel level, BlockPos from, BlockPos to) {
		MinecraftServer server = level.getServer();
		GlobalPos a = GlobalPos.of(level.dimension(), from);
		GlobalPos b = GlobalPos.of(level.dimension(), to);
		if (Feuds.feuding(server, a, b)) { // 33.20: no caravans between feuding sides
			return false;
		}
		if (!ENABLED) {
			return true;
		}
		return !strangers(server, a, b) || Data.get(server).pact(a, b) != null;
	}

	/** The pact between the villages at {@code from} and {@code to} in {@code level}, or null (or with pacts off). */
	@Nullable
	public static Pact pact(ServerLevel level, BlockPos from, BlockPos to) {
		return ENABLED ? Data.get(level.getServer()).pact(GlobalPos.of(level.dimension(), from), GlobalPos.of(level.dimension(), to)) : null;
	}

	/** Whether the village at {@code from} gives its caravans' cargo to {@code to} (a pact with its gifts on). */
	public static boolean gives(ServerLevel level, BlockPos from, BlockPos to) {
		Pact pact = pact(level, from, to);
		return pact != null && pact.gives(GlobalPos.of(level.dimension(), from));
	}

	/** The villages allied with {@code at} (alliances on). */
	public static List<GlobalPos> allies(MinecraftServer server, GlobalPos at) {
		if (!ENABLED || !ALLIANCES) {
			return List.of();
		}
		return Data.get(server).pacts.stream().filter(p -> p.alliance() && p.has(at)).map(p -> p.other(at)).toList();
	}

	/** The pacts of {@code at}. */
	public static List<Pact> of(MinecraftServer server, GlobalPos at) {
		return Data.get(server).pacts.stream().filter(p -> p.has(at)).toList();
	}

	/** The offers waiting at {@code at}, oldest first. */
	public static List<Offer> to(MinecraftServer server, GlobalPos at) {
		long now = now(server);
		return Data.get(server).offers.stream().filter(o -> o.to().equals(at) && now - o.sent() <= LAPSE).toList();
	}

	/** {@code player} (who rules the village at {@code hall}) offers {@code target}'s owner a trade pact, or an alliance. */
	public static Result offer(ServerLevel level, BlockPos hall, BlockPos target, boolean alliance, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos from = GlobalPos.of(level.dimension(), hall);
		GlobalPos to = GlobalPos.of(level.dimension(), target);
		Component us = Realms.name(server, from);
		Component them = Realms.name(server, to);
		Data data = Data.get(server);
		UUID owner = Realms.ownerOf(server, to);
		String key = !ENABLED || alliance && !ALLIANCES ? "off"
			: !Realms.mayRule(server, from, player) ? "not_yours"
			: owner == null || !strangers(server, from, to) ? "not_stranger"
			: hall.distSqr(target) > (double) range(level, hall) * range(level, hall) ? "too_far"
			: Feuds.feuding(server, from, to) ? "feud"
			: data.pact(from, to) != null && (!alliance || data.pact(from, to).alliance()) ? "already"
			: data.offer(from, to) != null ? "offered" : null;
		if (key != null) {
			return new Result(false, Component.translatable("message.aliveworkplace.pact.refused_" + key, them, blocks(level, hall)).withStyle(ChatFormatting.RED));
		}
		data.offers.add(new Offer(from, to, alliance, owner, now(server)));
		data.setDirty();
		String kind = alliance ? "alliance" : "pact";
		String ownerName = Realms.ownerName(server, to);
		String text = Component.translatable("message.aliveworkplace.pact.letter_" + kind, player.getGameProfile().getName(), us, them, LAPSE / 24000).getString();
		Mail.sendLetter(server, player.getUUID(), player.getGameProfile().getName(), owner, ownerName, text, from);
		ServerPlayer theirs = server.getPlayerList().getPlayer(owner);
		if (theirs != null) {
			Chat.chat(theirs, Component.translatable("message.aliveworkplace.pact.offered_told_" + kind, us, them).withStyle(ChatFormatting.GOLD));
		}
		chronicle(server, from, Component.translatable("chronicle.aliveworkplace.pact.offered_" + kind, them, ownerName));
		return new Result(true, Component.translatable("message.aliveworkplace.pact.offered_" + kind, them, ownerName, LAPSE / 24000).withStyle(ChatFormatting.GREEN));
	}

	/**
	 * How far the village at {@code hall} deals: its caravan range, the same the tab lists neighbors by (twice as far with
	 * the Lighthouse, 35.12; B595).
	 */
	static int range(ServerLevel level, BlockPos hall) {
		return io.github.jcondedata.aliveworkplace.wonder.LighthousePowers.caravanRange(level, hall);
	}

	/** {@link #range} as a player reads it: "2,048". */
	static String blocks(ServerLevel level, BlockPos hall) {
		return String.format(java.util.Locale.ROOT, "%,d", range(level, hall));
	}

	/** {@code player} accepts, for the village at {@code hall}, the offer from {@code from}. */
	public static Result accept(ServerLevel level, BlockPos hall, GlobalPos from, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		Data data = Data.get(server);
		Offer offer = data.offer(from, at);
		Component them = Realms.name(server, from);
		Component us = Realms.name(server, at);
		String key = !ENABLED ? "off" : offer == null || now(server) - offer.sent() > LAPSE ? "no_offer"
			: !Realms.mayRule(server, at, player) ? "not_yours" : offer.alliance() && !ALLIANCES ? "off" : null;
		if (key != null) {
			return new Result(false, Component.translatable("message.aliveworkplace.pact.refused_" + key, them, blocks(level, hall)).withStyle(ChatFormatting.RED));
		}
		data.removeOffers(from, at);
		data.put(new Pact(from, at, offer.alliance(), now(server), -1, false, false));
		String kind = offer.alliance() ? "alliance" : "pact";
		chronicle(server, from, Component.translatable("chronicle.aliveworkplace.pact.made_" + kind, us));
		chronicle(server, at, Component.translatable("chronicle.aliveworkplace.pact.made_" + kind, them));
		tell(server, from, player, Component.translatable("message.aliveworkplace.pact.accepted_told_" + kind, us, them).withStyle(ChatFormatting.GREEN));
		return new Result(true, Component.translatable("message.aliveworkplace.pact.accepted_" + kind, them).withStyle(ChatFormatting.GREEN));
	}

	/** {@code player} declines, for the village at {@code hall}, the offer from {@code from}: nothing changes. */
	public static Result decline(ServerLevel level, BlockPos hall, GlobalPos from, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		Data data = Data.get(server);
		Offer offer = data.offer(from, at);
		Component them = Realms.name(server, from);
		Component us = Realms.name(server, at);
		String key = offer == null ? "no_offer" : !Realms.mayRule(server, at, player) ? "not_yours" : null;
		if (key != null) {
			return new Result(false, Component.translatable("message.aliveworkplace.pact.refused_" + key, them, blocks(level, hall)).withStyle(ChatFormatting.RED));
		}
		data.offers.remove(offer);
		data.setDirty();
		chronicle(server, from, Component.translatable("chronicle.aliveworkplace.pact.declined", us));
		tell(server, from, player, Component.translatable("message.aliveworkplace.pact.declined_told", us).withStyle(ChatFormatting.YELLOW));
		return new Result(true, Component.translatable("message.aliveworkplace.pact.declined", them).withStyle(ChatFormatting.YELLOW));
	}

	/**
	 * {@code player}, for the village at {@code hall}, ends its pact with {@code other}: a trade pact at once (its routes
	 * both ways stop), an alliance after a day's notice; a letter tells the other owner.
	 */
	public static Result end(ServerLevel level, BlockPos hall, GlobalPos other, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		Data data = Data.get(server);
		Pact pact = data.pact(at, other);
		Component them = Realms.name(server, other);
		Component us = Realms.name(server, at);
		String key = pact == null ? "no_pact" : !Realms.mayRule(server, at, player) ? "not_yours" : pact.endsAt() >= 0 ? "ending" : null;
		if (key != null) {
			return new Result(false, Component.translatable("message.aliveworkplace.pact.refused_" + key, them, blocks(level, hall)).withStyle(ChatFormatting.RED));
		}
		UUID owner = Realms.ownerOf(server, other);
		String kind = pact.alliance() ? "alliance" : "pact";
		if (owner != null) {
			String text = Component.translatable("message.aliveworkplace.pact.end_letter_" + kind, player.getGameProfile().getName(), us, them).getString();
			Mail.sendLetter(server, player.getUUID(), player.getGameProfile().getName(), owner, Realms.ownerName(server, other), text, at);
		}
		if (pact.alliance()) {
			data.put(new Pact(pact.a(), pact.b(), true, pact.since(), now(server) + NOTICE, pact.giftsA(), pact.giftsB()));
			chronicle(server, at, Component.translatable("chronicle.aliveworkplace.pact.notice", them));
			chronicle(server, other, Component.translatable("chronicle.aliveworkplace.pact.notice_got", us));
			return new Result(true, Component.translatable("message.aliveworkplace.pact.notice", them).withStyle(ChatFormatting.YELLOW));
		}
		close(server, pact);
		return new Result(true, Component.translatable("message.aliveworkplace.pact.ended", them).withStyle(ChatFormatting.YELLOW));
	}

	/** Turns the gifts of {@code hall}'s side of its pact with {@code other} on or off; returns whether they're on. */
	public static boolean toggleGifts(ServerLevel level, BlockPos hall, GlobalPos other, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		Data data = Data.get(server);
		Pact p = data.pact(at, other);
		if (p == null || !Realms.mayRule(server, at, player)) {
			return false;
		}
		boolean a = p.a().equals(at);
		Pact next = new Pact(p.a(), p.b(), p.alliance(), p.since(), p.endsAt(), a ? !p.giftsA() : p.giftsA(), a ? p.giftsB() : !p.giftsB());
		data.put(next);
		return next.gives(at);
	}

	/** The pact ends: off the list, the routes between the two stop, each chronicle notes it, the owners hear. */
	static void close(MinecraftServer server, Pact pact) {
		Data data = Data.get(server);
		data.remove(pact);
		ServerLevel level = server.getLevel(pact.a().dimension());
		if (level != null && pact.a().dimension().equals(pact.b().dimension())) {
			Caravans.Data caravans = Caravans.Data.get(level);
			if (caravans.routesFrom(pact.a().pos()).contains(pact.b().pos())) {
				caravans.toggleRoute(pact.a().pos(), pact.b().pos());
			}
			if (caravans.routesFrom(pact.b().pos()).contains(pact.a().pos())) {
				caravans.toggleRoute(pact.b().pos(), pact.a().pos());
			}
		}
		String kind = pact.alliance() ? "alliance" : "pact";
		Component a = Realms.name(server, pact.a());
		Component b = Realms.name(server, pact.b());
		chronicle(server, pact.a(), Component.translatable("chronicle.aliveworkplace.pact.ended_" + kind, b));
		chronicle(server, pact.b(), Component.translatable("chronicle.aliveworkplace.pact.ended_" + kind, a));
		tell(server, pact.a(), null, Component.translatable("message.aliveworkplace.pact.ended_told_" + kind, b).withStyle(ChatFormatting.YELLOW));
		tell(server, pact.b(), null, Component.translatable("message.aliveworkplace.pact.ended_told_" + kind, a).withStyle(ChatFormatting.YELLOW));
	}

	static void chronicle(MinecraftServer server, GlobalPos at, Component line) {
		ServerLevel level = server.getLevel(at.dimension());
		if (level != null) {
			Chronicle.atHall(level, at.pos(), Chronicle.Kind.REALM, line);
		}
	}

	/** Tells the owner of {@code at}, if online and not {@code skip}. */
	public static void tell(MinecraftServer server, GlobalPos at, @Nullable ServerPlayer skip, Component line) {
		UUID owner = Realms.ownerOf(server, at);
		ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (player != null && player != skip) {
			Chat.chat(player, line);
		}
	}

	static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** The Pacts tab on the hall's trade page. */
	static final class PactsTab {
		static ItemStack icon(ServerLevel level, BlockPos hall) {
			GlobalPos at = GlobalPos.of(level.dimension(), hall);
			int pacts = of(level.getServer(), at).size();
			int offers = to(level.getServer(), at).size();
			int feuds = Feuds.of(level.getServer(), at).size();
			return VillageHallScreen.icon(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.pact.tab"), ChatFormatting.GOLD,
				VillageHallScreen.line(io.github.jcondedata.aliveworkplace.work.Words.countedTwo("screen.aliveworkplace.pact.tab_count", pacts, offers, pacts, offers), ChatFormatting.GRAY),
				VillageHallScreen.line(io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.feud.tab_count", feuds, feuds), ChatFormatting.GRAY));
		}

		static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer, Runnable again) {
			MinecraftServer server = level.getServer();
			GlobalPos at = GlobalPos.of(level.dimension(), hall);
			int slot = VillageHallScreen.FIRST_ROW;
			Set<GlobalPos> shown = new LinkedHashSet<>();
			Set<GlobalPos> feuding = new LinkedHashSet<>();
			slot = Feuds.fillTab(menu, level, hall, again, slot, feuding); // feuds first: peace, war (33.20)
			for (Offer offer : ENABLED ? to(server, at) : List.<Offer>of()) { // Accept and Decline
				if (slot + 1 >= ChoiceMenu.SIZE) {
					break;
				}
				Component them = Realms.name(server, offer.from());
				String kind = offer.alliance() ? "alliance" : "pact";
				long left = Math.max(0, LAPSE - (now(server) - offer.sent()));
				Component wait = left < 1000 ? Component.translatable("screen.aliveworkplace.pact.waiting.within_hour") // B596: not "0 days and 0 hours"
					: io.github.jcondedata.aliveworkplace.work.Words.countedTwo("screen.aliveworkplace.pact.waiting", left / 24000, left % 24000 / 1000, left / 24000, left % 24000 / 1000);
				menu.button(slot++, VillageHallScreen.icon(Items.LIME_DYE, Component.translatable("screen.aliveworkplace.pact.accept_" + kind, them), ChatFormatting.GREEN,
					VillageHallScreen.line(Component.translatable("screen.aliveworkplace.pact.from", Realms.ownerName(server, offer.from())), ChatFormatting.GRAY),
					VillageHallScreen.line(wait, ChatFormatting.DARK_GRAY)), p -> {
						Chat.actionBar(p, accept(level, hall, offer.from(), p).message());
						again.run();
					});
				menu.button(slot++, VillageHallScreen.icon(Items.BARRIER, Component.translatable("screen.aliveworkplace.pact.decline", them), ChatFormatting.RED), p -> {
					Chat.actionBar(p, decline(level, hall, offer.from(), p).message());
					again.run();
				});
				shown.add(offer.from());
			}
			for (Pact pact : ENABLED ? of(server, at) : List.<Pact>of()) { // the pact (a click: gifts on or off) and End
				if (slot + 1 >= ChoiceMenu.SIZE) {
					break;
				}
				GlobalPos other = pact.other(at);
				Component them = Realms.name(server, other);
				String kind = pact.alliance() ? "alliance" : "pact";
				List<Component> lore = new ArrayList<>();
				lore.add(VillageHallScreen.line(Component.translatable(pact.gives(at) ? "screen.aliveworkplace.pact.gifts_on" : "screen.aliveworkplace.pact.gifts_off"),
					pact.gives(at) ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
				if (pact.endsAt() >= 0) {
					long left = Math.max(0, pact.endsAt() - now(server));
					lore.add(VillageHallScreen.line(left < 1000 ? Component.translatable("screen.aliveworkplace.pact.ending.within_hour") // B596: not "Ends in 0 hours"
						: io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.pact.ending", left / 1000, left / 1000), ChatFormatting.YELLOW));
				}
				menu.button(slot++, VillageHallScreen.icon(pact.alliance() ? Items.SHIELD : Items.EMERALD, Component.translatable("screen.aliveworkplace.pact." + kind, them),
					ChatFormatting.AQUA, lore.toArray(Component[]::new)), p -> {
						boolean on = toggleGifts(level, hall, other, p);
						Chat.actionBar(p, Component.translatable(on ? "message.aliveworkplace.pact.gifts_on" : "message.aliveworkplace.pact.gifts_off", them));
						again.run();
					});
				menu.button(slot++, VillageHallScreen.icon(Items.FLINT, Component.translatable("screen.aliveworkplace.pact.end_" + kind, them), ChatFormatting.RED), p -> {
					Chat.actionBar(p, end(level, hall, other, p).message());
					again.run();
				});
				shown.add(other);
			}
			for (Caravans.Village v : Caravans.neighbours(level, hall)) { // offers to make
				GlobalPos other = GlobalPos.of(level.dimension(), v.hall());
				if (feuding.contains(other) || !strangers(server, at, other) || slot + 1 >= ChoiceMenu.SIZE) {
					continue;
				}
				if (Feuds.ENABLED && !Feuds.feuding(server, at, other)) {
					Feuds.declareButton(menu, slot++, level, hall, v, again);
				}
				if (!ENABLED || Data.get(server).offer(at, other) != null || slot + 1 >= ChoiceMenu.SIZE) {
					continue;
				}
				// B582: a trade-pact partner (listed above) can still be offered an alliance; accepting turns the pact into one
				Pact pact = Data.get(server).pact(at, other);
				boolean partner = pact != null && !pact.alliance() && to(server, at).stream().noneMatch(o -> o.from().equals(other));
				if (shown.contains(other) && !partner) {
					continue;
				}
				Component owner = Component.literal(Realms.ownerName(server, other));
				if (!partner) {
					menu.button(slot++, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.pact.offer_pact", v.name()), ChatFormatting.WHITE,
						VillageHallScreen.line(Component.translatable("screen.aliveworkplace.pact.owner", owner), ChatFormatting.GRAY)), p -> {
							Chat.actionBar(p, offer(level, hall, v.hall(), false, p).message());
							again.run();
						});
				}
				if (ALLIANCES && slot < ChoiceMenu.SIZE) {
					menu.button(slot++, VillageHallScreen.icon(Items.SHIELD, Component.translatable("screen.aliveworkplace.pact.offer_alliance", v.name()), ChatFormatting.WHITE,
						VillageHallScreen.line(Component.translatable("screen.aliveworkplace.pact.owner", owner), ChatFormatting.GRAY)), p -> {
							Chat.actionBar(p, offer(level, hall, v.hall(), true, p).message());
							again.run();
						});
				}
			}
			if (slot == VillageHallScreen.FIRST_ROW) {
				menu.button(slot, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.pact.none"), ChatFormatting.GRAY,
					VillageHallScreen.line(Component.translatable("screen.aliveworkplace.pact.none_hint", blocks(level, hall)), ChatFormatting.DARK_GRAY)), null);
			}
		}
	}

	private Pacts() {
	}
}
