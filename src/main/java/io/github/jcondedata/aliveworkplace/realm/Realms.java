package io.github.jcondedata.aliveworkplace.realm;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageBannerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;

/**
 * Realms (ROADMAP 33.11, design note M33): a Town or better founds a realm with itself as the capital, named after it
 * ("the Barony of Thornholm"). The capital's owner (their friends, operators: {@link VillageProtection#mayRule}) adds
 * any village within {@link #RANGE} blocks in the same dimension that they or a friend own ({@link #add}); a colony
 * joins its mother's realm by itself ({@link #colonyFounded}); a member's owner may {@link #leave} and the capital may
 * {@link #letGo} a member. The rank comes from the members ({@link #count}): a Barony, a County (3, the capital a Town),
 * a Duchy (5, the capital a City), a Kingdom (8, the capital and one more a City), at most {@link #MAX_MEMBERS}; a rank
 * up brings fireworks over the capital, a chat line to every member's owner and the new title. A broken member hall
 * leaves; a broken capital passes the crown to the biggest member that's a Town, or the realm ends ({@link #hallGone}).
 * Everything is kept in {@link RealmData}; what the tab shows of a village that isn't loaded is what its last round saw
 * ({@link RealmData.Known}), so nothing is loaded for it.
 *
 * <p>A realm of one (just founded) is already a Barony: the spec names the founded realm "the Barony of Thornholm", so
 * the Barony is the floor and the first rank up is to a County (ROADMAP Notes, 33.11). The arms ({@link #arms}) are kept
 * for 33.17's relief riders' shields ({@link VillageBanners#paint}) and the map banners and caravans of later items.
 */
public final class Realms {
	/** {@code realms} in the config. Off: no Realm tab, nothing founded or joined; realms stay saved and come back. */
	public static boolean ENABLED = true;
	/** {@code realmMaxMembers}: villages a realm may hold. */
	public static int MAX_MEMBERS = 12;
	/** How far from the capital a village may be added, in blocks. */
	public static final int RANGE = 2048;
	/** The rank a village needs to found a realm, and that a new capital needs. */
	public static final VillageRanks.Rank FOUNDING_RANK = VillageRanks.Rank.TOWN;

	/** The Realm tab's slots: the realm's title, its arms, the way out; the members fill row 4 on, then villages to add. */
	public static final int FOUND = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 4;
	public static final int TITLE = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW;
	public static final int ARMS = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 1;
	public static final int LEAVE = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 8;
	public static final int MEMBERS = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 9;
	/** 33.12: a member's "Put forward as capital", and a capital vote's line with its two buttons. */
	public static final int PUT_FORWARD = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 3;
	public static final int VOTE = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 5;
	public static final int VOTE_FOR = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 6;
	public static final int VOTE_AGAINST = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 7;
	/** 33.12: outside a realm, the invitations from row 4 on, each an Accept and a Decline button side by side. */
	public static final int INVITES = MEMBERS;

	/** A realm's rank, from its members. */
	public enum Rank {
		BARONY(2), COUNTY(3), DUCHY(5), KINGDOM(8);

		/** Villages it takes. */
		public final int members;

		Rank(int members) {
			this.members = members;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** "Barony". */
		public Component title() {
			return Component.translatable("realm.aliveworkplace.rank." + id());
		}

		/** "the Barony of Thornholm". */
		public Component title(Component place) {
			return Component.translatable("realm.aliveworkplace.title." + id(), place);
		}

		/** "The Barony of Thornholm", for the start of a sentence (B577). */
		public Component titleStart(Component place) {
			return Component.translatable("realm.aliveworkplace.title_start." + id(), place);
		}

		static Rank parse(String id) {
			for (Rank r : values()) {
				if (r.id().equals(id)) {
					return r;
				}
			}
			return BARONY;
		}
	}

	/** Why something wasn't done. */
	public enum Refusal {
		OFF, NO_HALL, NO_OWNER, NOT_YOURS, RANK, IN_REALM, NOT_CAPITAL, OTHER_WORLD, TOO_FAR, STRANGER, TAKEN, FULL, NOT_MEMBER, CAPITAL, NO_NAME,
		NO_BANNER, INVITED, NO_INVITE, LAPSED, VOTING, NO_VOTE, NOT_VOTER, NO_MONEY
	}

	/** What came of a click: the refusal (null: done) and what to tell the player. */
	public record Result(@Nullable Refusal refusal, Component message) {
		public boolean ok() {
			return refusal == null;
		}
	}

	public static void init() {
		TradePage.register(TradePage.Tab.REALM, Realms::tabIcon, Realms::tab, () -> ENABLED);
		io.github.jcondedata.aliveworkplace.platform.Platform.get().onServerTick(server -> {
			if (ENABLED && server.getTickCount() % 100 == 0) {
				settle(server, server.overworld().getGameTime());
			}
		});
	}

	/** Invitations lapse and capital votes end when their time is up (every five seconds, and from GameTests with a given time). */
	public static void settle(MinecraftServer server, long now) {
		if (!ENABLED) {
			return; // off: invitations and votes wait, as the realms do
		}
		RealmInvites.settle(server, now);
		CapitalVotes.settle(server, now);
	}

	/**
	 * Called with the realm as it was and as it is when its capital moves (a vote, or one owner's choice; 33.12). Every
	 * part of a realm's state kept on {@link RealmData.Realm} (name, arms, rank, and the treasury, research and edicts of
	 * 33.13 on, which belong there) goes with it by itself; a part kept per village registers here to move along.
	 */
	public interface CapitalMoved {
		void moved(MinecraftServer server, RealmData.Realm was, RealmData.Realm now);
	}

	private static final List<CapitalMoved> CAPITAL_MOVED = new ArrayList<>();

	public static void onCapitalMoved(CapitalMoved listener) {
		CAPITAL_MOVED.add(listener);
	}

	/**
	 * Moves {@code realm}'s capital to its member {@code to} (33.12): the realm keeps its id and everything on it, the new
	 * capital heads the list; fireworks over it, a chat line to every member's owner online and a chronicle line in every
	 * loaded member. The rank is counted again (a smaller capital can lower it).
	 */
	static RealmData.Realm moveCapital(MinecraftServer server, RealmData.Realm realm, GlobalPos to) {
		Component from = name(server, realm.capital());
		Component was = title(server, realm);
		List<RealmData.Member> members = new ArrayList<>();
		realm.members().stream().filter(m -> m.hall().equals(to)).forEach(members::add);
		realm.members().stream().filter(m -> !m.hall().equals(to)).forEach(members::add);
		RealmData.Realm kept = realm.withCapital(to).withMembers(members);
		RealmData.get(server).putRealm(kept);
		kept = recount(server, kept);
		for (CapitalMoved listener : CAPITAL_MOVED) {
			listener.moved(server, realm, kept);
		}
		Component now = title(server, kept);
		fireworks(server, to);
		tellOwners(server, kept, Component.translatable("message.aliveworkplace.realm.capital_moved", was, name(server, to), now).withStyle(ChatFormatting.GOLD));
		chronicle(server, kept, Component.translatable("chronicle.aliveworkplace.realm.capital_moved", from, name(server, to), now));
		return kept;
	}

	// ---- what a village is, loaded or not -------------------------------------------------------------------------------

	/** The hall at {@code at} if its chunk is loaded (nothing is loaded for it), or null. */
	@Nullable
	static VillageHallBlockEntity loaded(MinecraftServer server, GlobalPos at) {
		ServerLevel level = server.getLevel(at.dimension());
		if (level == null || !level.isLoaded(at.pos())) {
			return null;
		}
		return level.getBlockEntity(at.pos()) instanceof VillageHallBlockEntity e ? e : null;
	}

	/** Whether the hall at {@code at} is known to be gone: its chunk is loaded and it isn't there. */
	static boolean gone(MinecraftServer server, GlobalPos at) {
		ServerLevel level = server.getLevel(at.dimension());
		return level == null || level.isLoaded(at.pos()) && !level.getBlockState(at.pos()).is(ModBlocks.VILLAGE_HALL);
	}

	/** The village's name: the hall's if loaded, else what its last round or the caravans saw. */
	public static Component name(MinecraftServer server, GlobalPos at) {
		ServerLevel level = server.getLevel(at.dimension());
		if (level != null && loaded(server, at) != null) {
			return VillageHalls.name(level, at.pos());
		}
		RealmData.Known known = RealmData.get(server).known(at);
		if (known != null && !known.name().getString().isEmpty()) {
			return known.name();
		}
		Caravans.Village village = level == null ? null : Caravans.Data.get(level).village(at.pos());
		return village != null ? village.name() : VillageHalls.madeUpName(at.pos());
	}

	/** The village's rank: the hall's if loaded, else its last round's (a Hamlet if never seen). */
	public static VillageRanks.Rank rankOf(MinecraftServer server, GlobalPos at) {
		VillageHallBlockEntity entity = loaded(server, at);
		if (entity != null) {
			return entity.rank();
		}
		RealmData.Known known = RealmData.get(server).known(at);
		if (known != null) {
			for (VillageRanks.Rank r : VillageRanks.Rank.values()) {
				if (r.name().toLowerCase(Locale.ROOT).equals(known.rank())) {
					return r;
				}
			}
		}
		return VillageRanks.Rank.HAMLET;
	}

	/** The village's owner: the hall's if loaded, else its last round's; null for none. */
	@Nullable
	public static UUID ownerOf(MinecraftServer server, GlobalPos at) {
		VillageHallBlockEntity entity = loaded(server, at);
		if (entity != null) {
			return entity.owner();
		}
		RealmData.Known known = RealmData.get(server).known(at);
		return known == null ? null : known.owner().orElse(null);
	}

	static String ownerName(MinecraftServer server, GlobalPos at) {
		VillageHallBlockEntity entity = loaded(server, at);
		if (entity != null) {
			return entity.ownerName();
		}
		RealmData.Known known = RealmData.get(server).known(at);
		return known == null ? "" : known.ownerName();
	}

	static int villagers(MinecraftServer server, GlobalPos at) {
		RealmData.Known known = RealmData.get(server).known(at);
		return known == null ? 0 : known.villagers();
	}

	/** {@link VillageProtection#mayRule} for a village that needn't be loaded: its owner (as last seen), their friends, operators. */
	public static boolean mayRule(MinecraftServer server, GlobalPos at, Player player) {
		VillageHallBlockEntity entity = loaded(server, at);
		ServerLevel level = server.getLevel(at.dimension());
		if (entity != null && level != null) {
			return VillageProtection.mayRule(level, entity, player);
		}
		UUID owner = ownerOf(server, at);
		return owner != null && (owner.equals(player.getUUID()) || player.hasPermissions(2) || Friends.get(server).mayDirect(owner, player.getUUID()));
	}

	// ---- the realm's words ------------------------------------------------------------------------------------------------

	/** The realm {@code hall} is in, or null (whether realms are on or not). */
	@Nullable
	public static RealmData.Realm realmOf(ServerLevel level, BlockPos hall) {
		return RealmData.get(level.getServer()).realmOf(GlobalPos.of(level.dimension(), hall));
	}

	/** The realm's place name: its own (a Name Tag's), else its capital's. */
	public static Component place(MinecraftServer server, RealmData.Realm realm) {
		return realm.name().orElseGet(() -> name(server, realm.capital()));
	}

	/** "the Barony of Thornholm". */
	public static Component title(MinecraftServer server, RealmData.Realm realm) {
		return Rank.parse(realm.rank()).title(place(server, realm));
	}

	/** {@link #title} capitalized, for a sentence that starts with it. */
	public static Component titleStart(MinecraftServer server, RealmData.Realm realm) {
		return Rank.parse(realm.rank()).titleStart(place(server, realm));
	}

	/** The realm's villages' halls, the capital first (M35's Wonders, 33.13 on). */
	public static List<GlobalPos> members(RealmData.Realm realm) {
		return realm.members().stream().map(RealmData.Member::hall).toList();
	}

	/**
	 * The arms of the realm the village at {@code hall} is in, or null (none set, no realm, or realms off). 33.17's relief
	 * riders paint their shields with them ({@link VillageBanners#paint}); map banners and caravans use them too.
	 */
	@Nullable
	public static VillageBanners.Colours arms(MinecraftServer server, GlobalPos hall) {
		if (!ENABLED) {
			return null;
		}
		RealmData.Realm realm = RealmData.get(server).realmOf(hall);
		return realm == null ? null : realm.arms().orElse(null);
	}

	/** The hall's name icon's line: "In the Barony of Thornholm" or "Capital of the Barony of Thornholm"; null outside a realm or with realms off. */
	@Nullable
	public static Component hallLine(ServerLevel level, BlockPos hall) {
		if (!ENABLED) {
			return null;
		}
		RealmData.Realm realm = realmOf(level, hall);
		if (realm == null) {
			return null;
		}
		boolean capital = realm.capital().equals(GlobalPos.of(level.dimension(), hall));
		return Component.translatable(capital ? "screen.aliveworkplace.hall.realm_capital" : "screen.aliveworkplace.hall.realm_member",
			title(level.getServer(), realm));
	}

	/** Whether "What next?" should suggest founding a realm here: realms on, a Town or better with an owner, in none yet. */
	public static boolean suggest(ServerLevel level, BlockPos hall) {
		return ENABLED && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.owner() != null
			&& entity.rank().ordinal() >= FOUNDING_RANK.ordinal() && realmOf(level, hall) == null;
	}

	// ---- the rank -------------------------------------------------------------------------------------------------------

	/** The rank of a realm of {@code members} villages whose capital is a {@code capital} and that holds {@code cities} Cities (the capital too). */
	public static Rank count(int members, VillageRanks.Rank capital, int cities) {
		boolean capitalCity = capital == VillageRanks.Rank.CITY;
		if (members >= Rank.KINGDOM.members && capitalCity && cities >= 2) {
			return Rank.KINGDOM;
		}
		if (members >= Rank.DUCHY.members && capitalCity) {
			return Rank.DUCHY;
		}
		if (members >= Rank.COUNTY.members && capital.ordinal() >= VillageRanks.Rank.TOWN.ordinal()) {
			return Rank.COUNTY;
		}
		return Rank.BARONY;
	}

	/** The rank {@code realm} has now, from its members as they are (or were last seen). */
	public static Rank count(MinecraftServer server, RealmData.Realm realm) {
		int cities = 0;
		for (RealmData.Member m : realm.members()) {
			if (rankOf(server, m.hall()) == VillageRanks.Rank.CITY) {
				cities++;
			}
		}
		return count(realm.members().size(), rankOf(server, realm.capital()), cities);
	}

	/**
	 * Counts {@code realm}'s rank again and keeps it: a rank up is celebrated ({@link #celebrate}); a rank down just
	 * changes the title. Returns the realm as kept.
	 */
	public static RealmData.Realm recount(MinecraftServer server, RealmData.Realm realm) {
		Rank before = Rank.parse(realm.rank());
		Rank now = count(server, realm);
		if (now == before) {
			return realm;
		}
		Component was = title(server, realm);
		RealmData.Realm kept = realm.withRank(now.id());
		RealmData.get(server).putRealm(kept);
		if (now.ordinal() > before.ordinal()) {
			celebrate(server, kept, was);
		}
		return kept;
	}

	/** A rank up: fireworks over the capital, a chat line to every member's owner online, a line in every loaded member's chronicle. */
	static void celebrate(MinecraftServer server, RealmData.Realm realm, Component was) {
		Component now = title(server, realm);
		fireworks(server, realm.capital());
		Component line = Component.translatable("message.aliveworkplace.realm.rank_up", was, now).withStyle(ChatFormatting.GOLD);
		tellOwners(server, realm, line);
		chronicle(server, realm, Component.translatable("chronicle.aliveworkplace.realm.rank_up", now));
	}

	/** Three rockets in gold and purple over the capital's hall, if it is loaded. */
	static void fireworks(MinecraftServer server, GlobalPos capital) {
		ServerLevel level = server.getLevel(capital.dimension());
		if (level == null || !level.isLoaded(capital.pos())) {
			return;
		}
		BlockPos hall = capital.pos();
		level.playSound(null, hall, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1f, 1f);
		for (int i = 0; i < 3; i++) {
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			rocket.set(DataComponents.FIREWORKS, new net.minecraft.world.item.component.Fireworks(1 + i % 2, List.of(
				new net.minecraft.world.item.component.FireworkExplosion(net.minecraft.world.item.component.FireworkExplosion.Shape.STAR,
					it.unimi.dsi.fastutil.ints.IntList.of(0xF5C542, 0x8E44AD), it.unimi.dsi.fastutil.ints.IntList.of(0xFFFFFF), true, true))));
			level.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, hall.getX() + 0.5 + (i - 1) * 2,
				hall.getY() + 1.5, hall.getZ() + 0.5 + (i - 1), rocket));
		}
	}

	/** Tells every member's owner who is online (once each). */
	static void tellOwners(MinecraftServer server, RealmData.Realm realm, Component line) {
		List<UUID> told = new ArrayList<>();
		for (RealmData.Member m : realm.members()) {
			UUID owner = ownerOf(server, m.hall());
			if (owner != null && !told.contains(owner)) {
				told.add(owner);
				ServerPlayer player = server.getPlayerList().getPlayer(owner);
				if (player != null) {
					Chat.chat(player, line);
				}
			}
		}
	}

	/** Writes {@code line} into every loaded member's chronicle. */
	static void chronicle(MinecraftServer server, RealmData.Realm realm, Component line) {
		for (RealmData.Member m : realm.members()) {
			chronicle(server, m.hall(), line);
		}
	}

	static void chronicle(MinecraftServer server, GlobalPos hall, Component line) {
		ServerLevel level = server.getLevel(hall.dimension());
		if (level != null && loaded(server, hall) != null) {
			Chronicle.atHall(level, hall.pos(), Chronicle.Kind.REALM, line);
		}
	}

	// ---- founding, adding, leaving ---------------------------------------------------------------------------------------

	/**
	 * {@code player} founds a realm with the village at {@code hall} as its capital (the tab's button): it must be a Town
	 * or better, owned, ruled by them, and in no realm yet. The realm starts as a Barony named after the village, with the
	 * village's colours as its arms if it has any; the village's colonies join it at once.
	 */
	public static Result found(ServerLevel level, BlockPos hall, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		Component village = VillageHalls.name(level, hall);
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		Refusal refusal = !ENABLED ? Refusal.OFF : entity == null ? Refusal.NO_HALL : entity.owner() == null ? Refusal.NO_OWNER
			: !VillageProtection.mayRule(level, entity, player) ? Refusal.NOT_YOURS : realmOf(level, hall) != null ? Refusal.IN_REALM
			: entity.rank().ordinal() < FOUNDING_RANK.ordinal() ? Refusal.RANK : null;
		if (refusal != null) {
			return refused(refusal, refusal == Refusal.RANK ? Component.translatable("message.aliveworkplace.realm.rank", village, FOUNDING_RANK.title(), entity.rank().title())
				: refusal == Refusal.NOT_YOURS ? Component.translatable("message.aliveworkplace.realm.found_not_yours", entity.ownerName(), village)
				: refusal == Refusal.IN_REALM ? Component.translatable("message.aliveworkplace.realm.in_realm", village, title(server, realmOf(level, hall)))
				: common(refusal, village));
		}
		GlobalPos capital = GlobalPos.of(level.dimension(), hall);
		long day = Chronicle.day(level);
		RealmData.Realm realm = new RealmData.Realm(UUID.randomUUID(), Optional.empty(), capital, List.of(new RealmData.Member(capital, day)),
			Optional.ofNullable(VillageBanners.of(entity)), Rank.BARONY.id(), day);
		RealmData data = RealmData.get(server);
		data.putRealm(realm);
		see(level, hall, entity, -1);
		Component title = title(server, realm);
		Chronicle.atHall(level, hall, Chronicle.Kind.REALM, Component.translatable("chronicle.aliveworkplace.realm.founded", title, player.getDisplayName()));
		fireworks(server, capital);
		realm = joinColonies(server, data.realm(realm.id()), capital, day);
		recount(server, realm);
		io.github.jcondedata.aliveworkplace.hall.VillageMoments.award(level, hall, io.github.jcondedata.aliveworkplace.hall.VillageMoments.REALM, player); // 36.2
		return new Result(null, Component.translatable("message.aliveworkplace.realm.founded", title, village).withStyle(ChatFormatting.GOLD));
	}

	/**
	 * {@code player} adds the village at {@code target} to the realm whose capital is {@code capitalHall} (a click on the
	 * Realm tab): they must rule the capital, and the village must stand in the same dimension within {@link #RANGE}
	 * blocks, be in no realm, and belong to them or to someone who has made them a friend; the realm must have room.
	 */
	public static Result add(ServerLevel level, BlockPos capitalHall, GlobalPos target, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		RealmData data = RealmData.get(server);
		GlobalPos capital = GlobalPos.of(level.dimension(), capitalHall);
		RealmData.Realm realm = data.realmOf(capital);
		Component village = VillageHalls.name(level, capitalHall);
		Component them = name(server, target);
		Refusal refusal = refusalToRule(server, realm, capital, player);
		if (refusal == null) {
			UUID owner = ownerOf(server, target);
			double dx = target.pos().getX() - capitalHall.getX();
			double dz = target.pos().getZ() - capitalHall.getZ();
			refusal = !target.dimension().equals(capital.dimension()) ? Refusal.OTHER_WORLD
				: gone(server, target) || loaded(server, target) == null && data.known(target) == null ? Refusal.NO_HALL
				: dx * dx + dz * dz > (double) RANGE * RANGE ? Refusal.TOO_FAR
				: data.realmOf(target) != null ? Refusal.TAKEN
				: owner == null || !Friends.get(server).mayDirect(owner, player.getUUID()) ? Refusal.STRANGER
				: realm.members().size() >= MAX_MEMBERS ? Refusal.FULL : null;
		}
		if (refusal != null) {
			return refused(refusal, switch (refusal) {
				case NOT_CAPITAL -> Component.translatable("message.aliveworkplace.realm.not_capital", village);
				case NOT_YOURS -> Component.translatable("message.aliveworkplace.realm.rule_not_yours", ownerName(server, capital), realm == null ? village : title(server, realm));
				case OTHER_WORLD -> Component.translatable("message.aliveworkplace.realm.other_world", them);
				case NO_HALL -> Component.translatable("message.aliveworkplace.realm.hall_gone", them);
				case TOO_FAR -> Component.translatable("message.aliveworkplace.realm.too_far", them, RANGE);
				case TAKEN -> Component.translatable("message.aliveworkplace.realm.taken", them, title(server, data.realmOf(target)));
				case STRANGER -> Component.translatable("message.aliveworkplace.realm.stranger", them);
				case FULL -> Component.translatable("message.aliveworkplace.realm.full", titleStart(server, realm), MAX_MEMBERS);
				default -> common(refusal, village);
			});
		}
		long day = Chronicle.day(level);
		realm = join(server, realm, target, day);
		realm = joinColonies(server, realm, target, day);
		Component title = title(server, realm);
		realm = recount(server, realm);
		return new Result(null, Component.translatable("message.aliveworkplace.realm.added", them, title).withStyle(ChatFormatting.GREEN));
	}

	/** Puts {@code hall} in {@code realm} and notes it in both chronicles. */
	static RealmData.Realm join(MinecraftServer server, RealmData.Realm realm, GlobalPos hall, long day) {
		RealmData.get(server).removeInvitesTo(hall); // in a realm now: other realms' invitations are moot (33.12)
		List<RealmData.Member> members = new ArrayList<>(realm.members());
		members.add(new RealmData.Member(hall, day));
		RealmData.Realm kept = realm.withMembers(members);
		RealmData.get(server).putRealm(kept);
		Component title = title(server, kept);
		chronicle(server, hall, Component.translatable("chronicle.aliveworkplace.realm.joined", title));
		if (!hall.equals(kept.capital())) {
			chronicle(server, kept.capital(), Component.translatable("chronicle.aliveworkplace.realm.member_joined", name(server, hall), title));
		}
		return kept;
	}

	/** The colonies {@code mother} founded (and theirs) join {@code realm} while there's room: same dimension, in no realm, hall not gone. */
	static RealmData.Realm joinColonies(MinecraftServer server, RealmData.Realm realm, GlobalPos mother, long day) {
		RealmData data = RealmData.get(server);
		for (RealmData.Founded f : data.foundedBy(mother)) {
			if (realm.members().size() >= MAX_MEMBERS) {
				break;
			}
			if (f.hall().dimension().equals(realm.capital().dimension()) && data.realmOf(f.hall()) == null && !gone(server, f.hall())) {
				realm = join(server, realm, f.hall(), day);
				realm = joinColonies(server, realm, f.hall(), day);
			}
		}
		return realm;
	}

	/** A colony of {@code mother} was founded at {@code colony} (33.10): it joins its mother's realm, if she is in one with room. */
	public static void colonyFounded(ServerLevel level, GlobalPos mother, GlobalPos colony) {
		if (!ENABLED) {
			return;
		}
		MinecraftServer server = level.getServer();
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(mother);
		if (realm == null || data.realmOf(colony) != null || realm.members().size() >= MAX_MEMBERS || !colony.dimension().equals(realm.capital().dimension())) {
			return;
		}
		realm = join(server, realm, colony, Chronicle.day(level));
		recount(server, realm);
	}

	/**
	 * {@code player} takes the village at {@code hall} out of its realm (its tab's button): they must rule it. The capital
	 * leaving gives up the crown: it passes as if its hall were broken ({@link #passCrown}).
	 */
	public static Result leave(ServerLevel level, BlockPos hall, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		RealmData.Realm realm = RealmData.get(server).realmOf(at);
		Component village = VillageHalls.name(level, hall);
		if (!ENABLED || realm == null) {
			return refused(!ENABLED ? Refusal.OFF : Refusal.NOT_MEMBER, !ENABLED ? common(Refusal.OFF, village)
				: Component.translatable("message.aliveworkplace.realm.not_member", village));
		}
		if (!mayRule(server, at, player)) {
			return refused(Refusal.NOT_YOURS, Component.translatable("message.aliveworkplace.realm.leave_not_yours", ownerName(server, at), village));
		}
		Component title = title(server, realm);
		if (realm.capital().equals(at)) {
			passCrown(server, realm, at);
			chronicle(server, at, Component.translatable("chronicle.aliveworkplace.realm.left", title));
			return new Result(null, Component.translatable("message.aliveworkplace.realm.abdicated", village, title).withStyle(ChatFormatting.YELLOW));
		}
		remove(server, realm, at);
		chronicle(server, at, Component.translatable("chronicle.aliveworkplace.realm.left", title));
		RealmData.Realm now = RealmData.get(server).realm(realm.id());
		if (now != null) {
			chronicle(server, now.capital(), Component.translatable("chronicle.aliveworkplace.realm.member_left", village, title));
		}
		return new Result(null, Component.translatable("message.aliveworkplace.realm.left", village, title).withStyle(ChatFormatting.YELLOW));
	}

	/** {@code player} lets the village at {@code member} go from the realm whose capital is {@code capitalHall}: they must rule the capital. */
	public static Result letGo(ServerLevel level, BlockPos capitalHall, GlobalPos member, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos capital = GlobalPos.of(level.dimension(), capitalHall);
		RealmData.Realm realm = RealmData.get(server).realmOf(capital);
		Component village = VillageHalls.name(level, capitalHall);
		Component them = name(server, member);
		Refusal refusal = refusalToRule(server, realm, capital, player);
		if (refusal == null) {
			refusal = member.equals(capital) ? Refusal.CAPITAL : !realm.has(member) ? Refusal.NOT_MEMBER : null;
		}
		if (refusal != null) {
			return refused(refusal, switch (refusal) {
				case NOT_CAPITAL -> Component.translatable("message.aliveworkplace.realm.not_capital", village);
				case NOT_YOURS -> Component.translatable("message.aliveworkplace.realm.rule_not_yours", ownerName(server, capital), realm == null ? village : title(server, realm));
				case CAPITAL -> Component.translatable("message.aliveworkplace.realm.let_go_capital");
				case NOT_MEMBER -> Component.translatable("message.aliveworkplace.realm.not_member", them);
				default -> common(refusal, village);
			});
		}
		Component title = title(server, realm);
		remove(server, realm, member);
		chronicle(server, member, Component.translatable("chronicle.aliveworkplace.realm.let_go", title));
		chronicle(server, capital, Component.translatable("chronicle.aliveworkplace.realm.member_let_go", them, title));
		UUID owner = ownerOf(server, member);
		ServerPlayer theirs = owner == null || owner.equals(player.getUUID()) ? null : server.getPlayerList().getPlayer(owner);
		if (theirs != null) {
			Chat.chat(theirs, Component.translatable("message.aliveworkplace.realm.let_go_told", them, title).withStyle(ChatFormatting.YELLOW));
		}
		return new Result(null, Component.translatable("message.aliveworkplace.realm.let_go", them, title).withStyle(ChatFormatting.YELLOW));
	}

	/** Takes {@code hall} (not the capital) out of {@code realm} and counts the rank again. */
	static void remove(MinecraftServer server, RealmData.Realm realm, GlobalPos hall) {
		RealmData.Realm kept = realm.withMembers(realm.members().stream().filter(m -> !m.hall().equals(hall)).toList());
		RealmData.get(server).putRealm(kept);
		recount(server, kept);
	}

	/**
	 * The capital at {@code leaving} is lost (its hall broken, or it gave up the crown): the crown passes to the biggest
	 * other member that's a Town or better (by rank, then villagers), or, with none, the realm ends and every member is
	 * free. Every member's owner online is told.
	 */
	static void passCrown(MinecraftServer server, RealmData.Realm realm, GlobalPos leaving) {
		RealmData data = RealmData.get(server);
		Component was = title(server, realm);
		List<RealmData.Member> rest = realm.members().stream().filter(m -> !m.hall().equals(leaving)).toList();
		RealmData.Member heir = rest.stream()
			.filter(m -> rankOf(server, m.hall()).ordinal() >= FOUNDING_RANK.ordinal())
			.max(Comparator.<RealmData.Member>comparingInt(m -> rankOf(server, m.hall()).ordinal()).thenComparingInt(m -> villagers(server, m.hall())))
			.orElse(null);
		if (heir == null) {
			data.removeRealm(realm.id());
			RealmData.Realm ended = realm.withMembers(rest);
			Component line = Component.translatable("message.aliveworkplace.realm.ended", was).withStyle(ChatFormatting.RED);
			tellOwners(server, realm, line);
			chronicle(server, ended, Component.translatable("chronicle.aliveworkplace.realm.ended", was));
			return;
		}
		List<RealmData.Member> members = new ArrayList<>();
		members.add(heir);
		rest.stream().filter(m -> m != heir).forEach(members::add);
		RealmData.Realm kept = realm.withCapital(heir.hall()).withMembers(members);
		data.putRealm(kept);
		kept = recount(server, kept);
		Component line = Component.translatable("message.aliveworkplace.realm.crown_passed", was, name(server, heir.hall()), title(server, kept))
			.withStyle(ChatFormatting.GOLD);
		tellOwners(server, realm, line);
		chronicle(server, kept, Component.translatable("chronicle.aliveworkplace.realm.crown_passed", name(server, heir.hall()), title(server, kept)));
	}

	/** A hall was broken (the hall block's removal): its village leaves its realm; a capital passes the crown or ends the realm. */
	public static void hallGone(ServerLevel level, BlockPos hall) {
		MinecraftServer server = level.getServer();
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(at);
		if (realm != null) {
			if (realm.capital().equals(at)) {
				passCrown(server, realm, at);
			} else {
				Component title = title(server, realm);
				Component name = name(server, at);
				remove(server, realm, at);
				chronicle(server, realm.capital(), Component.translatable("chronicle.aliveworkplace.realm.member_lost", name, title));
				UUID owner = ownerOf(server, realm.capital());
				ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
				if (player != null) {
					Chat.chat(player, Component.translatable("message.aliveworkplace.realm.member_lost", name, title).withStyle(ChatFormatting.YELLOW));
				}
			}
		}
		data.removeInvitesTo(at);
		data.forgetKnown(at);
	}

	/** The hall's round: what it sees of its village is kept for the tab, and its realm's rank counted again. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, int villagers) {
		see(level, hall, entity, villagers);
		if (ENABLED) {
			RealmData.Realm realm = realmOf(level, hall);
			if (realm != null) {
				recount(level.getServer(), realm);
				RealmResearch.round(level, hall, entity);
			}
		}
	}

	/** Keeps what the hall shows of its village now ({@code villagers} -1: as last seen). */
	static void see(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, int villagers) {
		RealmData data = RealmData.get(level.getServer());
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		RealmData.Known was = data.known(at);
		int count = villagers >= 0 ? villagers : was == null ? 0 : was.villagers();
		int wellbeing = entity.needs() == null ? (was == null ? 0 : was.wellbeing()) : Math.round(entity.needs().wellbeing() * 100);
		data.putKnown(new RealmData.Known(at, VillageHalls.name(level, hall), Optional.ofNullable(entity.owner()), entity.ownerName(),
			entity.rank().name().toLowerCase(Locale.ROOT), count, wellbeing, level.getGameTime()));
	}

	// ---- the realm's name and arms ---------------------------------------------------------------------------------------

	/** {@code player} renames the realm whose capital is {@code capitalHall} with the named Name Tag {@code tag} (one is used up, as on a mob). */
	public static Result rename(ServerLevel level, BlockPos capitalHall, ServerPlayer player, ItemStack tag) {
		MinecraftServer server = level.getServer();
		GlobalPos capital = GlobalPos.of(level.dimension(), capitalHall);
		RealmData.Realm realm = RealmData.get(server).realmOf(capital);
		Component village = VillageHalls.name(level, capitalHall);
		Refusal refusal = refusalToRule(server, realm, capital, player);
		if (refusal == null && (!tag.is(Items.NAME_TAG) || !tag.has(DataComponents.CUSTOM_NAME))) {
			refusal = Refusal.NO_NAME;
		}
		if (refusal != null) {
			return refused(refusal, ruleRefusal(server, refusal, realm, capital, village));
		}
		Component was = title(server, realm);
		RealmData.Realm kept = realm.withName(Optional.of(Component.literal(tag.getHoverName().getString())));
		RealmData.get(server).putRealm(kept);
		tag.consume(1, player);
		Component now = title(server, kept);
		chronicle(server, kept, Component.translatable("chronicle.aliveworkplace.realm.renamed", was, now));
		return new Result(null, Component.translatable("message.aliveworkplace.realm.renamed", was, now).withStyle(ChatFormatting.GREEN));
	}

	/** {@code player} sets the arms of the realm whose capital is {@code capitalHall} from the banner {@code banner} (a vanilla one or a Village Banner; it isn't used up). */
	public static Result setArms(ServerLevel level, BlockPos capitalHall, ServerPlayer player, ItemStack banner) {
		MinecraftServer server = level.getServer();
		GlobalPos capital = GlobalPos.of(level.dimension(), capitalHall);
		RealmData.Realm realm = RealmData.get(server).realmOf(capital);
		Component village = VillageHalls.name(level, capitalHall);
		Refusal refusal = refusalToRule(server, realm, capital, player);
		VillageBanners.Colours arms = design(banner);
		if (refusal == null && arms == null) {
			refusal = Refusal.NO_BANNER;
		}
		if (refusal != null) {
			return refused(refusal, ruleRefusal(server, refusal, realm, capital, village));
		}
		RealmData.Realm kept = realm.withArms(Optional.of(arms));
		RealmData.get(server).putRealm(kept);
		level.playSound(null, player.blockPosition(), SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1f);
		chronicle(server, kept, Component.translatable("chronicle.aliveworkplace.realm.arms", title(server, kept)));
		return new Result(null, Component.translatable("message.aliveworkplace.realm.arms", title(server, kept)).withStyle(ChatFormatting.GREEN));
	}

	/** The design of a banner (vanilla's, or a Village Banner), or null for anything else. */
	@Nullable
	public static VillageBanners.Colours design(ItemStack stack) {
		BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		if (stack.getItem() instanceof BannerItem banner) {
			return new VillageBanners.Colours(banner.getColor(), patterns);
		}
		if (stack.getItem() instanceof VillageBannerItem) {
			return new VillageBanners.Colours(VillageBannerItem.base(stack), patterns);
		}
		return null;
	}

	// ---- refusals ----------------------------------------------------------------------------------------------------

	/** Whether {@code player} may act for {@code realm} from {@code capital}: realms on, the village is the capital, they rule it. */
	@Nullable
	static Refusal refusalToRule(MinecraftServer server, @Nullable RealmData.Realm realm, GlobalPos capital, Player player) {
		return !ENABLED ? Refusal.OFF : realm == null || !realm.capital().equals(capital) ? Refusal.NOT_CAPITAL
			: !mayRule(server, capital, player) ? Refusal.NOT_YOURS : null;
	}

	static Component ruleRefusal(MinecraftServer server, Refusal refusal, @Nullable RealmData.Realm realm, GlobalPos capital, Component village) {
		return switch (refusal) {
			case NOT_CAPITAL -> Component.translatable("message.aliveworkplace.realm.not_capital", village);
			case NOT_YOURS -> Component.translatable("message.aliveworkplace.realm.rule_not_yours", ownerName(server, capital), realm == null ? village : title(server, realm));
			case NO_NAME -> Component.translatable("message.aliveworkplace.realm.no_name");
			case NO_BANNER -> Component.translatable("message.aliveworkplace.realm.no_banner");
			default -> common(refusal, village);
		};
	}

	static Component common(Refusal refusal, Component village) {
		return switch (refusal) {
			case OFF -> Component.translatable("message.aliveworkplace.realm.off");
			case NO_OWNER -> Component.translatable("message.aliveworkplace.realm.no_owner", village);
			default -> Component.translatable("message.aliveworkplace.realm.hall_gone", village);
		};
	}

	static Result refused(Refusal refusal, Component message) {
		return new Result(refusal, message.copy().withStyle(ChatFormatting.RED));
	}

	// ---- the Realm tab (in RealmTab) -------------------------------------------------------------------------------------

	static ItemStack tabIcon(ServerLevel level, BlockPos hall) {
		return RealmTab.icon(level, hall);
	}

	static void tab(io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer, Runnable again) {
		RealmTab.fill(menu, level, hall, viewer, again);
	}

	private Realms() {
	}
}
