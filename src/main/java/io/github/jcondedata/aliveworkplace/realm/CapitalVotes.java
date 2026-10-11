package io.github.jcondedata.aliveworkplace.realm;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Choosing the capital (ROADMAP 33.12). A member that's a Town or better can be put forward as capital by its owner
 * ({@link #putForward}): if one player owns every member, it moves at once; otherwise every member's owner votes on the
 * Realm tab for a day ({@link #vote}), one ballot per village they own. When the day is up ({@link #settle}) the move
 * needs more villages for it than against; a village that didn't vote counts for the current capital, and a tie keeps it.
 * The new capital takes the realm with everything on it ({@link Realms#moveCapital}). The realm's decisions stay with
 * whoever rules its capital.
 */
public final class CapitalVotes {
	/** How long a capital vote runs: a day. */
	public static final long DAY = 24000L;

	/** For and against, counted over the realm's members as they are (a village that didn't vote is against). */
	public record Tally(int yes, int no) {
	}

	/** {@code player} puts the village at {@code hall} forward as its realm's capital (its Realm tab's button): they must rule it. */
	public static Realms.Result putForward(ServerLevel level, BlockPos hall, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		RealmData data = RealmData.get(server);
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		RealmData.Realm realm = data.realmOf(at);
		Component village = VillageHalls.name(level, hall);
		Realms.Refusal refusal = refusal(server, data, realm, at, player);
		if (refusal != null) {
			return Realms.refused(refusal, switch (refusal) {
				case NOT_MEMBER -> Component.translatable("message.aliveworkplace.realm.not_member", village);
				case CAPITAL -> Component.translatable("message.aliveworkplace.realm.already_capital", village, Realms.title(server, realm));
				case NOT_YOURS -> Component.translatable("message.aliveworkplace.realm.put_forward_not_yours", Realms.ownerName(server, at), village);
				case RANK -> Component.translatable("message.aliveworkplace.realm.capital_rank", village, Realms.FOUNDING_RANK.title(), Realms.rankOf(server, at).title());
				case VOTING -> Component.translatable("message.aliveworkplace.realm.voting", Realms.name(server, data.vote(realm.id()).candidate()));
				default -> Realms.common(refusal, village);
			});
		}
		UUID owner = Realms.ownerOf(server, at);
		if (owners(server, realm).equals(Set.of(owner))) {
			Realms.moveCapital(server, realm, at); // one player owns every member: no vote
			return new Realms.Result(null, Component.translatable("message.aliveworkplace.realm.moved_at_once", village).withStyle(ChatFormatting.GOLD));
		}
		RealmData.Vote vote = new RealmData.Vote(realm.id(), at, RealmInvites.now(server), List.of());
		for (RealmData.Member m : realm.members()) {
			if (owner.equals(Realms.ownerOf(server, m.hall()))) {
				vote = vote.cast(m.hall(), true); // the proposer's villages are for it
			}
		}
		data.putVote(vote);
		Component title = Realms.title(server, realm);
		Component capital = Realms.name(server, realm.capital());
		Realms.tellOwners(server, realm, Component.translatable("message.aliveworkplace.realm.vote_called", village, title, capital).withStyle(ChatFormatting.GOLD));
		Realms.chronicle(server, realm, Component.translatable("chronicle.aliveworkplace.realm.vote_called", village, capital));
		return new Realms.Result(null, Component.translatable("message.aliveworkplace.realm.put_forward", village).withStyle(ChatFormatting.GREEN));
	}

	/** Why {@code player} can't put {@code at} forward: realms off, not a member, the capital already, not theirs, too small, a vote running. */
	@Nullable
	static Realms.Refusal refusal(MinecraftServer server, RealmData data, @Nullable RealmData.Realm realm, GlobalPos at, ServerPlayer player) {
		return !Realms.ENABLED ? Realms.Refusal.OFF : realm == null ? Realms.Refusal.NOT_MEMBER : realm.capital().equals(at) ? Realms.Refusal.CAPITAL
			: Realms.ownerOf(server, at) == null ? Realms.Refusal.NO_OWNER : !Realms.mayRule(server, at, player) ? Realms.Refusal.NOT_YOURS
			: Realms.rankOf(server, at).ordinal() < Realms.FOUNDING_RANK.ordinal() ? Realms.Refusal.RANK
			: data.vote(realm.id()) != null ? Realms.Refusal.VOTING : null;
	}

	/** The owners of {@code realm}'s members (null for one with none). */
	static Set<UUID> owners(MinecraftServer server, RealmData.Realm realm) {
		Set<UUID> out = new HashSet<>();
		for (RealmData.Member m : realm.members()) {
			out.add(Realms.ownerOf(server, m.hall()));
		}
		return out;
	}

	/** The members of {@code realm} that {@code player} owns: their ballots. */
	public static List<GlobalPos> ballotsOf(MinecraftServer server, RealmData.Realm realm, UUID player) {
		List<GlobalPos> out = new ArrayList<>();
		for (RealmData.Member m : realm.members()) {
			if (player.equals(Realms.ownerOf(server, m.hall()))) {
				out.add(m.hall());
			}
		}
		return out;
	}

	/** {@code player} votes, from any member's Realm tab, for ({@code yes}) or against moving the capital: one ballot for each member they own. */
	public static Realms.Result vote(ServerLevel level, BlockPos hall, ServerPlayer player, boolean yes) {
		MinecraftServer server = level.getServer();
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(GlobalPos.of(level.dimension(), hall));
		Component village = VillageHalls.name(level, hall);
		RealmData.Vote vote = realm == null ? null : data.vote(realm.id());
		List<GlobalPos> mine = realm == null ? List.of() : ballotsOf(server, realm, player.getUUID());
		Realms.Refusal refusal = !Realms.ENABLED ? Realms.Refusal.OFF : realm == null ? Realms.Refusal.NOT_MEMBER : vote == null ? Realms.Refusal.NO_VOTE
			: mine.isEmpty() ? Realms.Refusal.NOT_VOTER : null;
		if (refusal != null) {
			return Realms.refused(refusal, switch (refusal) {
				case NOT_MEMBER -> Component.translatable("message.aliveworkplace.realm.not_member", village);
				case NO_VOTE -> Component.translatable("message.aliveworkplace.realm.no_vote");
				case NOT_VOTER -> Component.translatable("message.aliveworkplace.realm.not_voter", Realms.title(server, realm));
				default -> Realms.common(refusal, village);
			});
		}
		for (GlobalPos g : mine) {
			vote = vote.cast(g, yes);
		}
		data.putVote(vote);
		Component candidate = Realms.name(server, vote.candidate());
		return new Realms.Result(null, Component.translatable(yes ? "message.aliveworkplace.realm.voted_for" : "message.aliveworkplace.realm.voted_against",
			candidate, mine.size()).withStyle(ChatFormatting.GREEN));
	}

	/** The count of {@code vote} over {@code realm}'s members now. */
	public static Tally tally(RealmData.Realm realm, RealmData.Vote vote) {
		int yes = 0;
		for (RealmData.Member m : realm.members()) {
			if (Boolean.TRUE.equals(vote.ballot(m.hall()))) {
				yes++;
			}
		}
		return new Tally(yes, realm.members().size() - yes);
	}

	/** Ends every vote whose day is up by {@code now}. */
	static void settle(MinecraftServer server, long now) {
		for (RealmData.Vote vote : RealmData.get(server).votes()) {
			if (now - vote.started() >= DAY) {
				end(server, vote);
			}
		}
	}

	/** The day is up: more for than against moves the capital (if the village still can be it); otherwise it stays. */
	static void end(MinecraftServer server, RealmData.Vote vote) {
		RealmData data = RealmData.get(server);
		data.removeVote(vote.realm());
		RealmData.Realm realm = data.realm(vote.realm());
		if (realm == null) {
			return;
		}
		Component candidate = Realms.name(server, vote.candidate());
		Tally tally = tally(realm, vote);
		if (!realm.has(vote.candidate()) || realm.capital().equals(vote.candidate())
			|| Realms.rankOf(server, vote.candidate()).ordinal() < Realms.FOUNDING_RANK.ordinal()) {
			Realms.tellOwners(server, realm, Component.translatable("message.aliveworkplace.realm.vote_void", candidate, Realms.title(server, realm)).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (tally.yes() > tally.no()) {
			Realms.tellOwners(server, realm, Component.translatable("message.aliveworkplace.realm.vote_won", candidate, tally.yes(), tally.no()).withStyle(ChatFormatting.GOLD));
			Realms.moveCapital(server, realm, vote.candidate());
			return;
		}
		Component capital = Realms.name(server, realm.capital());
		Realms.tellOwners(server, realm, Component.translatable("message.aliveworkplace.realm.vote_lost", candidate, tally.yes(), tally.no(), capital).withStyle(ChatFormatting.YELLOW));
		Realms.chronicle(server, realm, Component.translatable("chronicle.aliveworkplace.realm.vote_lost", candidate, capital));
	}

	private CapitalVotes() {
	}
}
