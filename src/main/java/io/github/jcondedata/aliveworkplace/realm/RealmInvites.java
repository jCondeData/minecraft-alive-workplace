package io.github.jcondedata.aliveworkplace.realm;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mail.Mail;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import java.util.List;
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
 * Inviting other players' villages into a realm (ROADMAP 33.12). The capital's owner (their friends, operators) invites
 * a village within reach that belongs to someone else, not a friend ({@link #invite}): a letter goes to that owner
 * (their mailbox, their hands, or the post office's desk, {@link Mail#sendLetter}) and an Accept and a Decline line wait
 * on the village's Realm tab for {@link #LAPSE} ticks (3 days). Accepting joins it, with its colonies, as an added
 * village would ({@link #accept}); declining or letting it lapse leaves it out, and the capital's owner hears either way.
 */
public final class RealmInvites {
	/** How long an invitation waits for an answer: 3 days. */
	public static final long LAPSE = 3 * 24000L;

	/** {@code player} invites the village at {@code target} into the realm whose capital is {@code capitalHall} (a click on the capital's Realm tab). */
	public static Realms.Result invite(ServerLevel level, BlockPos capitalHall, GlobalPos target, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		RealmData data = RealmData.get(server);
		GlobalPos capital = GlobalPos.of(level.dimension(), capitalHall);
		RealmData.Realm realm = data.realmOf(capital);
		Component village = VillageHalls.name(level, capitalHall);
		Component them = Realms.name(server, target);
		Realms.Refusal refusal = Realms.refusalToRule(server, realm, capital, player);
		UUID owner = Realms.ownerOf(server, target);
		if (refusal == null) {
			refusal = reach(server, data, realm, target);
			if (refusal == null) {
				refusal = owner == null ? Realms.Refusal.NO_OWNER
					: data.invite(realm.id(), target) != null && !lapsed(data.invite(realm.id(), target), now(server)) ? Realms.Refusal.INVITED : null;
			}
		}
		if (refusal == null && Friends.get(server).mayDirect(owner, player.getUUID())) {
			return Realms.add(level, capitalHall, target, player); // theirs or a friend's: it joins at once
		}
		if (refusal != null) {
			return Realms.refused(refusal, switch (refusal) {
				case NOT_CAPITAL -> Component.translatable("message.aliveworkplace.realm.not_capital", village);
				case NOT_YOURS -> Component.translatable("message.aliveworkplace.realm.rule_not_yours", Realms.ownerName(server, capital),
					realm == null ? village : Realms.title(server, realm));
				case OTHER_WORLD -> Component.translatable("message.aliveworkplace.realm.other_world", them);
				case NO_HALL -> Component.translatable("message.aliveworkplace.realm.hall_gone", them);
				case TOO_FAR -> Component.translatable("message.aliveworkplace.realm.too_far", them, Realms.RANGE);
				case TAKEN -> Component.translatable("message.aliveworkplace.realm.taken", them, Realms.title(server, data.realmOf(target)));
				case FULL -> Component.translatable("message.aliveworkplace.realm.full", Realms.titleStart(server, realm), Realms.MAX_MEMBERS);
				case INVITED -> Component.translatable("message.aliveworkplace.realm.already_invited", them);
				default -> Realms.common(refusal, them);
			});
		}
		data.putInvite(new RealmData.Invite(realm.id(), target, owner, now(server)));
		Component title = Realms.title(server, realm);
		String ownerName = Realms.ownerName(server, target);
		String text = Component.translatable("message.aliveworkplace.realm.invite_letter", player.getGameProfile().getName(), title, them, village, LAPSE / 24000).getString();
		Mail.sendLetter(server, player.getUUID(), player.getGameProfile().getName(), owner, ownerName, text, capital);
		ServerPlayer theirs = server.getPlayerList().getPlayer(owner);
		if (theirs != null) {
			Chat.chat(theirs, Component.translatable("message.aliveworkplace.realm.invited_told", title, them).withStyle(ChatFormatting.GOLD));
		}
		Realms.chronicle(server, capital, Component.translatable("chronicle.aliveworkplace.realm.invited", them, ownerName));
		return new Realms.Result(null, Component.translatable("message.aliveworkplace.realm.invited", them, ownerName, LAPSE / 24000).withStyle(ChatFormatting.GREEN));
	}

	/** Why {@code target} can't be asked into {@code realm} (dimension, hall, reach, already in a realm, room), or null. */
	@Nullable
	static Realms.Refusal reach(MinecraftServer server, RealmData data, RealmData.Realm realm, GlobalPos target) {
		GlobalPos capital = realm.capital();
		double dx = target.pos().getX() - capital.pos().getX();
		double dz = target.pos().getZ() - capital.pos().getZ();
		return !target.dimension().equals(capital.dimension()) ? Realms.Refusal.OTHER_WORLD
			: Realms.gone(server, target) || Realms.loaded(server, target) == null && data.known(target) == null ? Realms.Refusal.NO_HALL
			: dx * dx + dz * dz > (double) Realms.RANGE * Realms.RANGE ? Realms.Refusal.TOO_FAR
			: data.realmOf(target) != null ? Realms.Refusal.TAKEN
			: realm.members().size() >= Realms.MAX_MEMBERS ? Realms.Refusal.FULL : null;
	}

	/** {@code player} accepts the invitation of realm {@code realmId} for the village at {@code hall} (its Realm tab's button): they must rule it. */
	public static Realms.Result accept(ServerLevel level, BlockPos hall, UUID realmId, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		RealmData data = RealmData.get(server);
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		Component village = VillageHalls.name(level, hall);
		RealmData.Invite invite = data.invite(realmId, at);
		RealmData.Realm realm = data.realm(realmId);
		Realms.Refusal refusal = !Realms.ENABLED ? Realms.Refusal.OFF : invite == null || realm == null ? Realms.Refusal.NO_INVITE
			: !Realms.mayRule(server, at, player) ? Realms.Refusal.NOT_YOURS : lapsed(invite, now(server)) ? Realms.Refusal.LAPSED : null;
		if (refusal == null) {
			refusal = reach(server, data, realm, at);
		}
		if (refusal != null) {
			if (refusal == Realms.Refusal.LAPSED) {
				lapse(server, invite);
			}
			return Realms.refused(refusal, switch (refusal) {
				case NO_INVITE -> Component.translatable("message.aliveworkplace.realm.no_invite", village);
				case NOT_YOURS -> Component.translatable("message.aliveworkplace.realm.answer_not_yours", Realms.ownerName(server, at), village);
				case LAPSED -> Component.translatable("message.aliveworkplace.realm.lapsed", Realms.title(server, realm), village);
				case TAKEN -> Component.translatable("message.aliveworkplace.realm.in_realm", village, Realms.title(server, data.realmOf(at)));
				case FULL -> Component.translatable("message.aliveworkplace.realm.full", Realms.titleStart(server, realm), Realms.MAX_MEMBERS);
				case TOO_FAR -> Component.translatable("message.aliveworkplace.realm.too_far", village, Realms.RANGE);
				default -> Realms.common(refusal, village);
			});
		}
		long day = Chronicle.day(level);
		realm = Realms.join(server, realm, at, day); // takes every invitation to it off the list
		realm = Realms.joinColonies(server, realm, at, day);
		Component title = Realms.title(server, realm);
		Realms.recount(server, realm);
		tellCapital(server, realm, player, Component.translatable("message.aliveworkplace.realm.accepted", village, title).withStyle(ChatFormatting.GREEN));
		return new Realms.Result(null, Component.translatable("message.aliveworkplace.realm.added", village, title).withStyle(ChatFormatting.GREEN));
	}

	/** {@code player} declines the invitation of realm {@code realmId} for the village at {@code hall}: they must rule it. */
	public static Realms.Result decline(ServerLevel level, BlockPos hall, UUID realmId, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		RealmData data = RealmData.get(server);
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		Component village = VillageHalls.name(level, hall);
		RealmData.Invite invite = data.invite(realmId, at);
		RealmData.Realm realm = data.realm(realmId);
		Realms.Refusal refusal = !Realms.ENABLED ? Realms.Refusal.OFF : invite == null || realm == null ? Realms.Refusal.NO_INVITE
			: !Realms.mayRule(server, at, player) ? Realms.Refusal.NOT_YOURS : null;
		if (refusal != null) {
			return Realms.refused(refusal, switch (refusal) {
				case NO_INVITE -> Component.translatable("message.aliveworkplace.realm.no_invite", village);
				case NOT_YOURS -> Component.translatable("message.aliveworkplace.realm.answer_not_yours", Realms.ownerName(server, at), village);
				default -> Realms.common(refusal, village);
			});
		}
		data.removeInvite(realmId, at);
		Component title = Realms.title(server, realm);
		Realms.chronicle(server, realm.capital(), Component.translatable("chronicle.aliveworkplace.realm.declined", village));
		tellCapital(server, realm, player, Component.translatable("message.aliveworkplace.realm.declined_told", village, title).withStyle(ChatFormatting.YELLOW));
		return new Realms.Result(null, Component.translatable("message.aliveworkplace.realm.declined", title).withStyle(ChatFormatting.YELLOW));
	}

	/** The invitations to the village at {@code village} still open at {@code now}, oldest first (realms that still stand). */
	public static List<RealmData.Invite> to(MinecraftServer server, GlobalPos village, long now) {
		RealmData data = RealmData.get(server);
		return data.invites().stream().filter(i -> i.village().equals(village) && !lapsed(i, now) && data.realm(i.realm()) != null).toList();
	}

	/** Whether {@code invite} has had its 3 days by {@code now}. */
	public static boolean lapsed(RealmData.Invite invite, long now) {
		return now - invite.sent() > LAPSE;
	}

	/** Takes every lapsed invitation off the list; the capital's owner is told and its chronicle notes it. */
	static void settle(MinecraftServer server, long now) {
		for (RealmData.Invite invite : RealmData.get(server).invites()) {
			if (lapsed(invite, now)) {
				lapse(server, invite);
			}
		}
	}

	static void lapse(MinecraftServer server, RealmData.Invite invite) {
		RealmData data = RealmData.get(server);
		data.removeInvite(invite.realm(), invite.village());
		RealmData.Realm realm = data.realm(invite.realm());
		if (realm == null) {
			return;
		}
		Component village = Realms.name(server, invite.village());
		Realms.chronicle(server, realm.capital(), Component.translatable("chronicle.aliveworkplace.realm.lapsed", village));
		tellCapital(server, realm, null, Component.translatable("message.aliveworkplace.realm.lapsed", Realms.title(server, realm), village).withStyle(ChatFormatting.YELLOW));
	}

	/** Tells the capital's owner, if online and not {@code skip}. */
	static void tellCapital(MinecraftServer server, RealmData.Realm realm, @Nullable ServerPlayer skip, Component line) {
		UUID owner = Realms.ownerOf(server, realm.capital());
		ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (player != null && player != skip) {
			Chat.chat(player, line);
		}
	}

	static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private RealmInvites() {
	}
}
