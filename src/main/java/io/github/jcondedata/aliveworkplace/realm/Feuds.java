package io.github.jcondedata.aliveworkplace.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mail.Mail;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Feuds, and war where PvP is on (ROADMAP 33.20). On the hall's Pacts tab an owner declares a feud on another owner's
 * village within caravan range ({@link #declare}); when either village is in a realm, its whole realm is that side. A
 * letter tells the other owner; the pacts and routes between the two end; each side's board pays 25% less to the
 * rival's people for their goods and charges them 25% more ({@link #boardPercent}); villagers grumble (the chatter topic
 * {@code feud}); the chronicles note it. Either side offers peace by letter ({@link #offerPeace}) and the other accepts
 * ({@link #acceptPeace}); a feud without a war fades after {@link #FADE} ticks (7 days).
 *
 * <p>War: only when the server allows PvP and {@code feudWars} is on. The declaring side raises the feud to war once it
 * has stood {@link #WAR_AFTER} (a day), by letter ({@link #raiseWar}); it starts at the next dawn and lasts until peace
 * is accepted or {@link #WAR_DAYS} days pass. In a war each side's guards count the other side's owners and their friends
 * as foes inside their own village ({@link #warFoe}, through {@code Guards.isFoe}), and those players may fight that
 * village's guards ({@link #mayFight}, through {@code VillageProtection}); blocks, chests, other villagers, animals and
 * the treasury stay protected. Relief riders answer raids only, never a war. When it ends both chronicles record the
 * guards each side lost; no treasury changes hands. Configs {@code feuds} (off: existing feuds end quietly) and
 * {@code feudWars}.
 */
public final class Feuds {
	/** Config {@code feuds}: off, nothing can be declared and existing feuds end quietly. */
	public static boolean ENABLED = true;
	/** Config {@code feudWars}: off, no feud becomes a war, even with PvP on. */
	public static boolean WARS = true;
	/** A feud without a war fades after 7 days. */
	public static final long FADE = 7 * 24000L;
	/** A feud must stand a day before it can be raised to war. */
	public static final long WAR_AFTER = 24000L;
	/** How many days a war lasts unless peace comes first. */
	public static final int WAR_DAYS = 3;
	/** Percent a feuding board pays a rival's people for their goods. */
	public static final int RIVAL_PAYS = 75;
	/** Percent a feuding board charges a rival's people. */
	public static final int RIVAL_CHARGES = 125;

	/**
	 * A feud declared by the village at {@code by} on the one at {@code on}, at game time {@code since}. A war, once
	 * raised, runs from {@code warStarts} to {@code warEnds} ({@code -1}: none); {@code warDone} once it has been
	 * recorded as over. {@code lostBy}, {@code lostOn}: guards each side lost in the war. {@code peace}: who offered peace
	 * (0 nobody, 1 the declaring side, 2 the other).
	 */
	public record Feud(GlobalPos by, GlobalPos on, long since, long warStarts, long warEnds, boolean warDone, int lostBy, int lostOn, int peace) {
		public static final Codec<Feud> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("by").forGetter(Feud::by),
			GlobalPos.CODEC.fieldOf("on").forGetter(Feud::on),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Feud::since),
			Codec.LONG.optionalFieldOf("warStarts", -1L).forGetter(Feud::warStarts),
			Codec.LONG.optionalFieldOf("warEnds", -1L).forGetter(Feud::warEnds),
			Codec.BOOL.optionalFieldOf("warDone", false).forGetter(Feud::warDone),
			Codec.INT.optionalFieldOf("lostBy", 0).forGetter(Feud::lostBy),
			Codec.INT.optionalFieldOf("lostOn", 0).forGetter(Feud::lostOn),
			Codec.INT.optionalFieldOf("peace", 0).forGetter(Feud::peace)
		).apply(i, Feud::new));

		/** A new feud, no war. */
		public Feud(GlobalPos by, GlobalPos on, long since) {
			this(by, on, since, -1, -1, false, 0, 0, 0);
		}

		public boolean warRaised() {
			return warStarts >= 0;
		}

		/** Whether its war is on at game time {@code now} (raised, begun, not over): the switches and PvP aside. */
		public boolean warOn(long now) {
			return warRaised() && !warDone && now >= warStarts && now < warEnds;
		}

		Feud withWar(long starts, long ends) {
			return new Feud(by, on, since, starts, ends, warDone, lostBy, lostOn, peace);
		}

		Feud withWarDone() {
			return new Feud(by, on, since, warStarts, warEnds, true, lostBy, lostOn, peace);
		}

		Feud withLost(boolean bySide) {
			return new Feud(by, on, since, warStarts, warEnds, warDone, lostBy + (bySide ? 1 : 0), lostOn + (bySide ? 0 : 1), peace);
		}

		Feud withPeace(int side) {
			return new Feud(by, on, since, warStarts, warEnds, warDone, lostBy, lostOn, side);
		}
	}

	/** The feuds, kept with the overworld. */
	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_feuds";
		final List<Feud> feuds = new ArrayList<>();

		public static Data get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		public List<Feud> feuds() {
			return List.copyOf(feuds);
		}

		/** Writes a feud, in place of any between the same two villages. */
		public void put(Feud feud) {
			feuds.removeIf(f -> same(f, feud));
			feuds.add(feud);
			setDirty();
		}

		void remove(Feud feud) {
			feuds.removeIf(f -> same(f, feud));
			setDirty();
		}

		private static boolean same(Feud a, Feud b) {
			return a.by().equals(b.by()) && a.on().equals(b.on()) || a.by().equals(b.on()) && a.on().equals(b.by());
		}

		/** Empties the list (tests). */
		public void clear() {
			feuds.clear();
			setDirty();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			Feud.CODEC.listOf().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), feuds).result().ifPresent(t -> tag.put("feuds", t));
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			if (tag.contains("feuds")) {
				Feud.CODEC.listOf().parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("feuds"))
					.resultOrPartial(e -> AliveWorkplace.LOG.warn("Couldn't read the feuds in {}: {}", NAME, e)).ifPresent(data.feuds::addAll);
			}
			return data;
		}
	}

	public static void init() {
		Platform.get().onServerTick(server -> {
			if (server.overworld().getGameTime() % 20 == 10) {
				settle(server);
			}
		});
		Platform.get().afterDeath(Feuds::onDeath);
	}

	// ---- sides ----------------------------------------------------------------------------------------------------------

	/** Whether {@code v} is on the side of {@code anchor}: the village itself, or one of its realm's. */
	static boolean onSide(MinecraftServer server, GlobalPos anchor, GlobalPos v) {
		if (anchor.equals(v)) {
			return true;
		}
		RealmData.Realm realm = RealmData.get(server).realmOf(anchor);
		return realm != null && realm.has(v);
	}

	/** The villages on {@code anchor}'s side. */
	static List<GlobalPos> side(MinecraftServer server, GlobalPos anchor) {
		RealmData.Realm realm = RealmData.get(server).realmOf(anchor);
		return realm == null ? List.of(anchor) : Realms.members(realm);
	}

	/** The side's name: its realm's title ("the Barony of Thornholm"), else the village's. */
	public static Component sideName(MinecraftServer server, GlobalPos anchor) {
		RealmData.Realm realm = RealmData.get(server).realmOf(anchor);
		return realm != null && Realms.ENABLED ? Realms.title(server, realm) : Realms.name(server, anchor);
	}

	/** Whether {@code player} is one of the owners of {@code anchor}'s side, or a friend of one. */
	static boolean ofSide(MinecraftServer server, GlobalPos anchor, Player player) {
		Friends friends = Friends.get(server);
		for (GlobalPos v : side(server, anchor)) {
			UUID owner = Realms.ownerOf(server, v);
			if (owner != null && friends.mayDirect(owner, player.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** The feud between the sides of {@code x} and {@code y}, or null (or with feuds off). */
	@Nullable
	public static Feud feud(MinecraftServer server, GlobalPos x, GlobalPos y) {
		if (!ENABLED) {
			return null;
		}
		for (Feud f : Data.get(server).feuds) {
			if (onSide(server, f.by(), x) && onSide(server, f.on(), y) || onSide(server, f.by(), y) && onSide(server, f.on(), x)) {
				return f;
			}
		}
		return null;
	}

	public static boolean feuding(MinecraftServer server, GlobalPos x, GlobalPos y) {
		return feud(server, x, y) != null;
	}

	/** The feuds {@code at}'s side is in. */
	public static List<Feud> of(MinecraftServer server, GlobalPos at) {
		if (!ENABLED) {
			return List.of();
		}
		return Data.get(server).feuds.stream().filter(f -> onSide(server, f.by(), at) || onSide(server, f.on(), at)).toList();
	}

	/** Whether {@code at} is on the declaring side of {@code feud}. */
	static boolean declarer(MinecraftServer server, Feud feud, GlobalPos at) {
		return onSide(server, feud.by(), at);
	}

	/** The anchor of the side {@code at} is feuding with in {@code feud}. */
	static GlobalPos rival(MinecraftServer server, Feud feud, GlobalPos at) {
		return declarer(server, feud, at) ? feud.on() : feud.by();
	}

	/** The name of a side the village at {@code hall} is feuding with, for its villagers to grumble about, or null. */
	@Nullable
	public static Component rivalName(ServerLevel level, BlockPos hall) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		List<Feud> feuds = of(server, at);
		return feuds.isEmpty() ? null : sideName(server, rival(server, feuds.get(0), at));
	}

	/** Whether {@code player} belongs to a side the village at {@code at} feuds with (and doesn't rule {@code at}). */
	public static boolean isRival(MinecraftServer server, GlobalPos at, Player player) {
		if (!ENABLED || Realms.mayRule(server, at, player)) {
			return false;
		}
		for (Feud f : of(server, at)) {
			if (ofSide(server, rival(server, f, at), player)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * What the board of the village at {@code hall} pays ({@code buying} false: the player sells to it) or charges
	 * ({@code buying}: the player buys), in percent of its price, to {@code player}: 75 or 125 for a rival's people, else 100.
	 */
	public static int boardPercent(ServerLevel level, BlockPos hall, @Nullable Player player, boolean buying) {
		if (player == null || !isRival(level.getServer(), GlobalPos.of(level.dimension(), hall), player)) {
			return 100;
		}
		return buying ? RIVAL_CHARGES : RIVAL_PAYS;
	}

	// ---- war ------------------------------------------------------------------------------------------------------------

	/** Why no war can be raised on this server now, or null: {@code wars_off} (the switch) or {@code pvp} (PvP off). */
	@Nullable
	public static String warBlocked(MinecraftServer server) {
		return !WARS ? "wars_off" : !server.isPvpAllowed() ? "pvp" : null;
	}

	/** Whether {@code feud}'s war is being fought now: begun, not over, the switches on and PvP allowed. */
	public static boolean fighting(MinecraftServer server, Feud feud) {
		return ENABLED && warBlocked(server) == null && feud.warOn(now(server));
	}

	/** The village (hall) {@code guard} serves: the nearest hall. */
	@Nullable
	static GlobalPos villageOf(ServerLevel level, Entity guard) {
		return VillageHalls.nearest(level, guard.blockPosition()).map(h -> GlobalPos.of(level.dimension(), h)).orElse(null);
	}

	/** The war feud in which {@code guard}'s village fights {@code player}'s side, with the player inside that village, or null. */
	@Nullable
	static Feud war(Villager guard, Player player) {
		if (!ENABLED || !(guard.level() instanceof ServerLevel level) || player.isCreative() || player.isSpectator()) {
			return null;
		}
		MinecraftServer server = level.getServer();
		if (warBlocked(server) != null || Data.get(server).feuds.stream().noneMatch(f -> f.warOn(now(server)))) {
			return null;
		}
		GlobalPos village = villageOf(level, guard);
		if (village == null || Realms.mayRule(server, village, player)) {
			return null;
		}
		long dx = village.pos().getX() - player.getBlockX();
		long dz = village.pos().getZ() - player.getBlockZ();
		if (player.level() != level || dx * dx + dz * dz > (long) VillageHalls.RADIUS * VillageHalls.RADIUS) {
			return null;
		}
		for (Feud f : of(server, village)) {
			if (fighting(server, f) && ofSide(server, rival(server, f, village), player)) {
				return f;
			}
		}
		return null;
	}

	/** In a war, whether {@code guard} should go for {@code entity}: a player of the enemy side inside the guard's village. */
	public static boolean warFoe(LivingEntity entity, Villager guard) {
		return entity instanceof Player player && entity.isAlive() && Guards.isGuard(guard) && war(guard, player) != null;
	}

	/** Whether {@code player} may fight {@code entity} in a protected village: one of its guards, in a war with the player's side. */
	public static boolean mayFight(Player player, Entity entity) {
		return entity instanceof Villager guard && Guards.isGuard(guard) && war(guard, player) != null;
	}

	/** A guard fallen to an enemy player in a war counts as lost for its side. */
	static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Villager guard) || !(source.getEntity() instanceof Player player) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Feud feud = war(guard, player);
		GlobalPos village = villageOf(level, guard);
		if (feud != null && village != null) {
			Data.get(level.getServer()).put(feud.withLost(declarer(level.getServer(), feud, village)));
		}
	}

	// ---- what owners do --------------------------------------------------------------------------------------------------

	/** {@code player} (who rules the village at {@code hall}) declares a feud on {@code target}'s side. */
	public static Pacts.Result declare(ServerLevel level, BlockPos hall, BlockPos target, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos from = GlobalPos.of(level.dimension(), hall);
		GlobalPos to = GlobalPos.of(level.dimension(), target);
		Component them = sideName(server, to);
		UUID owner = Realms.ownerOf(server, to);
		String key = !ENABLED ? "off" : !Realms.mayRule(server, from, player) ? "not_yours"
			: owner == null || !Pacts.strangers(server, from, to) || onSide(server, from, to) ? "not_stranger"
			: hall.distSqr(target) > (double) Caravans.RANGE * Caravans.RANGE ? "too_far"
			: feuding(server, from, to) ? "already"
			: RealmEdicts.noFeuds(server, to) ? "crowns_peace" : null; // the Crown's Peace (33.22)
		if (key != null) {
			return refused(key, them);
		}
		Data.get(server).put(new Feud(from, to, now(server)));
		Component us = sideName(server, from);
		// every pact, offer and route between the two sides ends
		Pacts.Data pacts = Pacts.Data.get(server);
		for (Pacts.Pact pact : pacts.pacts()) {
			if (onSide(server, from, pact.a()) && onSide(server, to, pact.b()) || onSide(server, from, pact.b()) && onSide(server, to, pact.a())) {
				Pacts.close(server, pact);
			}
		}
		for (GlobalPos a : side(server, from)) {
			for (GlobalPos b : side(server, to)) {
				pacts.removeOffers(a, b);
				stopRoutes(server, a, b);
			}
		}
		String ownerName = Realms.ownerName(server, to);
		String text = Component.translatable("message.aliveworkplace.feud.letter", player.getGameProfile().getName(), us, them, FADE / 24000).getString();
		Mail.sendLetter(server, player.getUUID(), player.getGameProfile().getName(), owner, ownerName, text, from);
		Pacts.tell(server, to, player, Component.translatable("message.aliveworkplace.feud.declared_told", us).withStyle(ChatFormatting.RED));
		chronicle(server, from, Component.translatable("chronicle.aliveworkplace.feud.declared", them));
		chronicle(server, to, Component.translatable("chronicle.aliveworkplace.feud.declared_on", us));
		return new Pacts.Result(true, Component.translatable("message.aliveworkplace.feud.declared", them, ownerName).withStyle(ChatFormatting.RED));
	}

	private static void stopRoutes(MinecraftServer server, GlobalPos a, GlobalPos b) {
		ServerLevel level = server.getLevel(a.dimension());
		if (level == null || !a.dimension().equals(b.dimension())) {
			return;
		}
		Caravans.Data caravans = Caravans.Data.get(level);
		if (caravans.routesFrom(a.pos()).contains(b.pos())) {
			caravans.toggleRoute(a.pos(), b.pos());
		}
		if (caravans.routesFrom(b.pos()).contains(a.pos())) {
			caravans.toggleRoute(b.pos(), a.pos());
		}
	}

	/** {@code player}, for the village at {@code hall}, offers peace to the side of {@code other}, by letter. */
	public static Pacts.Result offerPeace(ServerLevel level, BlockPos hall, GlobalPos other, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		Feud feud = feud(server, at, other);
		Component them = sideName(server, other);
		int side = feud == null ? 0 : declarer(server, feud, at) ? 1 : 2;
		String key = feud == null ? "no_feud" : !Realms.mayRule(server, at, player) ? "not_yours"
			: feud.peace() == side ? "peace_offered" : null;
		if (key != null) {
			return refused(key, them);
		}
		if (feud.peace() != 0) { // they offered already: this is acceptance
			return acceptPeace(level, hall, other, player);
		}
		Data.get(server).put(feud.withPeace(side));
		GlobalPos to = rival(server, feud, at);
		Component us = sideName(server, at);
		UUID owner = Realms.ownerOf(server, to);
		if (owner != null) {
			String text = Component.translatable("message.aliveworkplace.feud.peace_letter", player.getGameProfile().getName(), us, them).getString();
			Mail.sendLetter(server, player.getUUID(), player.getGameProfile().getName(), owner, Realms.ownerName(server, to), text, at);
		}
		Pacts.tell(server, to, player, Component.translatable("message.aliveworkplace.feud.peace_offered_told", us).withStyle(ChatFormatting.GOLD));
		chronicle(server, at, Component.translatable("chronicle.aliveworkplace.feud.peace_offered", them));
		return new Pacts.Result(true, Component.translatable("message.aliveworkplace.feud.peace_offered", them).withStyle(ChatFormatting.GOLD));
	}

	/** {@code player}, for the village at {@code hall}, accepts the peace the side of {@code other} offered: the feud (and any war) ends. */
	public static Pacts.Result acceptPeace(ServerLevel level, BlockPos hall, GlobalPos other, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		Feud feud = feud(server, at, other);
		Component them = sideName(server, other);
		int theirs = feud == null ? 0 : declarer(server, feud, at) ? 2 : 1;
		String key = feud == null ? "no_feud" : !Realms.mayRule(server, at, player) ? "not_yours" : feud.peace() != theirs ? "no_peace" : null;
		if (key != null) {
			return refused(key, them);
		}
		end(server, feud, "peace", player);
		return new Pacts.Result(true, Component.translatable("message.aliveworkplace.feud.peace_made", them).withStyle(ChatFormatting.GREEN));
	}

	/** {@code player}, for the village at {@code hall} on the declaring side, raises the feud with {@code other} to war, from the next dawn. */
	public static Pacts.Result raiseWar(ServerLevel level, BlockPos hall, GlobalPos other, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		Feud feud = feud(server, at, other);
		Component them = sideName(server, other);
		String blocked = warBlocked(server);
		long now = now(server);
		String key = feud == null ? "no_feud" : !Realms.mayRule(server, at, player) ? "not_yours" : blocked != null ? blocked
			: !declarer(server, feud, at) ? "not_declarer" : feud.warRaised() ? "war_raised"
			: now - feud.since() < WAR_AFTER ? "too_soon" : null;
		if (key != null) {
			long left = feud == null ? 0 : Math.max(0, WAR_AFTER - (now - feud.since()));
			long hours = (left + 999) / 1000;
			return new Pacts.Result(false, (key.equals("too_soon") ? io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.feud.refused_" + key, hours, them, hours)
				: Component.translatable("message.aliveworkplace.feud.refused_" + key, them, hours)).withStyle(ChatFormatting.RED));
		}
		long starts = now + Math.floorMod(-server.overworld().getDayTime(), 24000L);
		Data.get(server).put(feud.withWar(starts, starts + WAR_DAYS * 24000L));
		GlobalPos to = rival(server, feud, at);
		Component us = sideName(server, at);
		UUID owner = Realms.ownerOf(server, to);
		if (owner != null) {
			String text = Component.translatable("message.aliveworkplace.feud.war_letter", player.getGameProfile().getName(), us, them, WAR_DAYS).getString();
			Mail.sendLetter(server, player.getUUID(), player.getGameProfile().getName(), owner, Realms.ownerName(server, to), text, at);
		}
		Pacts.tell(server, to, player, Component.translatable("message.aliveworkplace.feud.war_told", us).withStyle(ChatFormatting.DARK_RED));
		chronicle(server, at, Component.translatable("chronicle.aliveworkplace.feud.war_declared", them));
		chronicle(server, to, Component.translatable("chronicle.aliveworkplace.feud.war_declared_on", us));
		return new Pacts.Result(true, Component.translatable("message.aliveworkplace.feud.war_raised", them, WAR_DAYS).withStyle(ChatFormatting.DARK_RED));
	}

	private static Pacts.Result refused(String key, Component them) {
		return new Pacts.Result(false, Component.translatable("message.aliveworkplace.feud.refused_" + key, them, Caravans.RANGE).withStyle(ChatFormatting.RED));
	}

	/** Each second: wars that began are announced, wars whose days ran out end, feuds without a war fade; with feuds off, they end quietly. */
	public static void settle(MinecraftServer server) {
		Data data = Data.get(server);
		if (data.feuds.isEmpty()) {
			return;
		}
		if (!ENABLED) {
			data.clear();
			return;
		}
		long now = now(server);
		for (Feud f : data.feuds()) {
			if (f.warRaised() && !f.warDone() && now >= f.warEnds()) {
				Feud done = f.withWarDone();
				data.put(done);
				recordWar(server, done);
			} else if ((!f.warRaised() || f.warDone()) && now - (f.warDone() ? f.warEnds() : f.since()) >= FADE) {
				end(server, f, "faded", null);
			}
		}
	}

	/** Both chronicles record the war's end and the guards each side lost; both owners hear. No treasury moves. */
	static void recordWar(MinecraftServer server, Feud f) {
		Component by = sideName(server, f.by());
		Component on = sideName(server, f.on());
		chronicle(server, f.by(), Component.translatable("chronicle.aliveworkplace.feud.war_over", on, f.lostBy(), f.lostOn()));
		chronicle(server, f.on(), Component.translatable("chronicle.aliveworkplace.feud.war_over", by, f.lostOn(), f.lostBy()));
		Pacts.tell(server, f.by(), null, Component.translatable("message.aliveworkplace.feud.war_over", on, f.lostBy(), f.lostOn()).withStyle(ChatFormatting.YELLOW));
		Pacts.tell(server, f.on(), null, Component.translatable("message.aliveworkplace.feud.war_over", by, f.lostOn(), f.lostBy()).withStyle(ChatFormatting.YELLOW));
	}

	/** The feud ends ({@code why}: {@code peace} or {@code faded}); a war still on ends with it and is recorded. */
	static void end(MinecraftServer server, Feud f, String why, @Nullable ServerPlayer by) {
		Data.get(server).remove(f);
		if (f.warRaised() && !f.warDone() && now(server) >= f.warStarts()) {
			recordWar(server, f);
		}
		Component a = sideName(server, f.by());
		Component b = sideName(server, f.on());
		chronicle(server, f.by(), Component.translatable("chronicle.aliveworkplace.feud." + why, b));
		chronicle(server, f.on(), Component.translatable("chronicle.aliveworkplace.feud." + why, a));
		Pacts.tell(server, f.by(), by, Component.translatable("message.aliveworkplace.feud." + why + "_told", b).withStyle(ChatFormatting.GREEN));
		Pacts.tell(server, f.on(), by, Component.translatable("message.aliveworkplace.feud." + why + "_told", a).withStyle(ChatFormatting.GREEN));
	}

	static void chronicle(MinecraftServer server, GlobalPos at, Component line) {
		ServerLevel level = server.getLevel(at.dimension());
		if (level != null) {
			Chronicle.atHall(level, at.pos(), Chronicle.Kind.FEUD, line);
		}
	}

	static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	// ---- the Pacts tab ----------------------------------------------------------------------------------------------------

	/** The feuds of the village at {@code hall} on its Pacts tab, from {@code slot}: each with its peace and war buttons. Returns the next free slot. */
	static int fillTab(ChoiceMenu menu, ServerLevel level, BlockPos hall, Runnable again, int slot, Set<GlobalPos> shown) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		long now = now(server);
		for (Feud f : of(server, at)) {
			if (slot + 2 >= ChoiceMenu.SIZE) {
				break;
			}
			GlobalPos other = rival(server, f, at);
			Component them = sideName(server, other);
			boolean ours = declarer(server, f, at);
			List<Component> lore = new ArrayList<>();
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.feud.board"), ChatFormatting.GRAY));
			if (fighting(server, f)) {
				long left = f.warEnds() - now;
				lore.add(VillageHallScreen.line(left < 1000 ? Component.translatable("screen.aliveworkplace.feud.at_war.within_hour") // B637/B672: not "0 days and 0 hours"
					: io.github.jcondedata.aliveworkplace.work.Words.countedTwo("screen.aliveworkplace.feud.at_war", left / 24000, left % 24000 / 1000, left / 24000, left % 24000 / 1000), ChatFormatting.DARK_RED));
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.feud.lost", ours ? f.lostBy() : f.lostOn(), ours ? f.lostOn() : f.lostBy()), ChatFormatting.GRAY));
			} else if (f.warRaised() && !f.warDone() && now < f.warStarts()) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.feud.war_at_dawn"), ChatFormatting.RED));
			} else if (!f.warRaised() || f.warDone()) {
				long left = Math.max(0, FADE - (now - (f.warDone() ? f.warEnds() : f.since())));
				lore.add(VillageHallScreen.line(left < 1000 ? Component.translatable("screen.aliveworkplace.feud.fades.within_hour") // B637/B672: not "0 days and 0 hours"
					: io.github.jcondedata.aliveworkplace.work.Words.countedTwo("screen.aliveworkplace.feud.fades", left / 24000, left % 24000 / 1000, left / 24000, left % 24000 / 1000), ChatFormatting.DARK_GRAY));
			}
			menu.button(slot++, VillageHallScreen.icon(Items.IRON_SWORD, Component.translatable("screen.aliveworkplace.feud.feud", them), ChatFormatting.RED,
				lore.toArray(Component[]::new)), null);
			int mine = ours ? 1 : 2;
			if (f.peace() != 0 && f.peace() != mine) {
				menu.button(slot++, VillageHallScreen.icon(Items.WHITE_BANNER, Component.translatable("screen.aliveworkplace.feud.accept_peace", them), ChatFormatting.GREEN,
					VillageHallScreen.line(Component.translatable("screen.aliveworkplace.feud.accept_peace_hint"), ChatFormatting.GRAY)), p -> {
						Chat.actionBar(p, acceptPeace(level, hall, other, p).message());
						again.run();
					});
			} else {
				menu.button(slot++, VillageHallScreen.icon(Items.WHITE_BANNER, Component.translatable(f.peace() == mine
						? "screen.aliveworkplace.feud.peace_waiting" : "screen.aliveworkplace.feud.offer_peace", them), ChatFormatting.WHITE,
					VillageHallScreen.line(Component.translatable("screen.aliveworkplace.feud.offer_peace_hint"), ChatFormatting.GRAY)), p -> {
						Chat.actionBar(p, offerPeace(level, hall, other, p).message());
						again.run();
					});
			}
			if (ours && !f.warRaised()) {
				menu.button(slot++, warButton(server, f, them), p -> {
					Chat.actionBar(p, raiseWar(level, hall, other, p).message());
					again.run();
				});
			}
			shown.addAll(side(server, other));
		}
		return slot;
	}

	/** The Raise to war button: what it does, or why it can't be pressed (PvP off, wars off, too soon). */
	static net.minecraft.world.item.ItemStack warButton(MinecraftServer server, Feud f, Component them) {
		String blocked = warBlocked(server);
		long left = Math.max(0, WAR_AFTER - (now(server) - f.since()));
		Component why = blocked != null ? Component.translatable("screen.aliveworkplace.feud.war_" + blocked)
			: left > 0 ? io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.feud.war_too_soon", (left + 999) / 1000, (left + 999) / 1000)
			: Component.translatable("screen.aliveworkplace.feud.war_hint", WAR_DAYS);
		return VillageHallScreen.icon(Items.TNT, Component.translatable("screen.aliveworkplace.feud.raise_war", them),
			blocked != null || left > 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.DARK_RED,
			VillageHallScreen.line(why, blocked != null || left > 0 ? ChatFormatting.RED : ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.feud.war_rules"), ChatFormatting.DARK_GRAY));
	}

	/** The Declare a feud button for a neighbour (another owner's village within caravan range). */
	static void declareButton(ChoiceMenu menu, int slot, ServerLevel level, BlockPos hall, Caravans.Village v, Runnable again) {
		MinecraftServer server = level.getServer();
		Component owner = Component.literal(Realms.ownerName(server, GlobalPos.of(level.dimension(), v.hall())));
		menu.button(slot, VillageHallScreen.icon(Items.IRON_SWORD, Component.translatable("screen.aliveworkplace.feud.declare", sideName(server, GlobalPos.of(level.dimension(), v.hall()))),
			ChatFormatting.RED,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.pact.owner", owner), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.feud.declare_hint"), ChatFormatting.DARK_GRAY)), p -> {
				Chat.actionBar(p, declare(level, hall, v.hall(), p).message());
				again.run();
			});
	}

	private Feuds() {
	}
}
