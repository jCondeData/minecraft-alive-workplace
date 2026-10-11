package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * A Hero's say in edicts (ROADMAP 31.12): a Hero of a village may propose an edict from its Book of Edicts; the hall's
 * owner gets it in chat with [Approve] and [Refuse] ({@code /workplace proposal approve|refuse <id>}), and the Hero
 * is told the answer. An approved edict is proclaimed as if the owner had clicked it. Proposals wait only while the
 * server runs (not saved): a proposal is a question, and an owner who was away is asked again.
 */
public final class EdictProposals {
	/** A proposal waiting for the owner. */
	public record Proposal(UUID id, ResourceKey<Level> dimension, BlockPos hall, ResourceLocation edict, UUID by, String byName, UUID owner) {
	}

	private static final Map<UUID, Proposal> PENDING = new ConcurrentHashMap<>();

	/** {@code by} proposes {@code edict} to the owner of the hall at {@code hall}; empty (and {@code by} told why) when the owner isn't here. */
	public static Optional<Proposal> propose(ServerLevel level, BlockPos hall, ServerPlayer by, Edicts.Edict edict) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || entity.owner() == null) {
			return Optional.empty();
		}
		Component village = VillageHalls.name(level, hall);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entity.owner());
		if (owner == null) {
			Chat.chat(by, Component.translatable("message.aliveworkplace.edict.proposal_away", entity.ownerName(), village).withStyle(ChatFormatting.RED));
			return Optional.empty();
		}
		Proposal p = new Proposal(UUID.randomUUID(), level.dimension(), hall.immutable(), edict.id(), by.getUUID(), by.getGameProfile().getName(), owner.getUUID());
		PENDING.put(p.id(), p);
		Chat.chat(owner, Component.translatable("message.aliveworkplace.edict.proposal", p.byName(), edict.name(), village,
			button("approve", p.id(), ChatFormatting.GREEN), button("refuse", p.id(), ChatFormatting.RED)).withStyle(ChatFormatting.YELLOW));
		Chat.chat(by, Component.translatable("message.aliveworkplace.edict.proposal_sent", edict.name(), entity.ownerName()).withStyle(ChatFormatting.GOLD));
		return Optional.of(p);
	}

	private static MutableComponent button(String action, UUID id, ChatFormatting colour) {
		return Component.translatable("message.aliveworkplace.edict.proposal_" + action).withStyle(s -> s.withColor(colour)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/workplace proposal " + action + " " + id))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.edict.proposal_" + action + "_hover"))));
	}

	/** {@code /workplace proposal approve|refuse <id>}: the owner answers. Returns whether the edict is now in force. */
	public static boolean answer(ServerPlayer owner, UUID id, boolean approve) {
		Proposal p = PENDING.get(id);
		if (p == null || !p.owner().equals(owner.getUUID())) {
			Chat.chat(owner, Component.translatable("message.aliveworkplace.edict.proposal_gone").withStyle(ChatFormatting.GRAY));
			return false;
		}
		PENDING.remove(id);
		MinecraftServer server = owner.getServer();
		ServerLevel level = server == null ? null : server.getLevel(p.dimension());
		Optional<Edicts.Edict> edict = Edicts.get(p.edict());
		if (level == null || edict.isEmpty()) {
			Chat.chat(owner, Component.translatable("message.aliveworkplace.edict.proposal_gone").withStyle(ChatFormatting.GRAY));
			return false;
		}
		ServerPlayer by = server.getPlayerList().getPlayer(p.by());
		if (!approve) {
			Chat.chat(owner, Component.translatable("message.aliveworkplace.edict.proposal_refused_owner", edict.get().name(), p.byName()).withStyle(ChatFormatting.GRAY));
			if (by != null) {
				Chat.chat(by, Component.translatable("message.aliveworkplace.edict.proposal_refused", owner.getGameProfile().getName(), edict.get().name())
					.withStyle(ChatFormatting.RED));
			}
			return false;
		}
		Edicts.Result result = Edicts.proclaim(level, p.hall(), owner, edict.get());
		if (!result.told().contains(owner)) {
			Chat.chat(owner, result.message());
		}
		if (by != null && !result.told().contains(by)) {
			Chat.chat(by, result.done()
				? Component.translatable("message.aliveworkplace.edict.proposal_approved", owner.getGameProfile().getName(), edict.get().name()).withStyle(ChatFormatting.GOLD)
				: result.message());
		}
		return result.done();
	}

	/** Proposals waiting (tests). */
	public static Map<UUID, Proposal> pending() {
		return Map.copyOf(PENDING);
	}

	private EdictProposals() {
	}
}
