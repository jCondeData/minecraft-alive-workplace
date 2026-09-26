package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * Tells nearby players what each builder is doing, once a second, so the client can show it above
 * the builder's head: the build's name and percentage, a progress bar, and one line of status.
 */
public final class BuilderStatusSync {
	public static final int INTERVAL = 20;

	/** S2C: status of one builder. The client forgets it if no update arrives for a few seconds. */
	public record Status(int entityId, Component title, float progress, Component line) implements CustomPacketPayload {
		public static final Type<Status> TYPE = new Type<>(AliveWorkplace.id("builder_status"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Status::entityId,
			ComponentSerialization.STREAM_CODEC, Status::title,
			ByteBufCodecs.FLOAT, Status::progress,
			ComponentSerialization.STREAM_CODEC, Status::line,
			Status::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(Status.TYPE, Status.CODEC);
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			if (level.getGameTime() % INTERVAL == 0 && !level.players().isEmpty()) {
				broadcast(level);
			}
		});
	}

	private static void broadcast(ServerLevel level) {
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (site.builder() == null) {
				continue;
			}
			Entity entity = level.getEntity(site.builder());
			if (!(entity instanceof Villager villager) || villager.isSleeping()) {
				continue;
			}
			Status status = status(level, site, villager);
			if (status == null) {
				continue;
			}
			for (ServerPlayer player : PlayerLookup.tracking(villager)) {
				if (ServerPlayNetworking.canSend(player, Status.TYPE)) {
					ServerPlayNetworking.send(player, status);
				}
			}
		}
	}

	/** The status shown above a builder's head, or null if there is nothing to show. */
	public static Status status(ServerLevel level, BuildSite site, Villager villager) {
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			return null;
		}
		float progress = site.progress(plan);
		Component title = Component.translatable("message.aliveworkplace.overhead.title",
			Blueprints.displayName(site.structure()), Math.round(progress * 100));
		Component line;
		if (site.detail() != null) {
			line = site.detail().copy().withStyle(ChatFormatting.YELLOW);
		} else if (site.status() == BuildSite.Status.WAITING_FOR_MATERIALS && !site.missing().isEmpty()) {
			line = Component.translatable("message.aliveworkplace.status.needs", Builders.formatMissing(site, 2)).withStyle(ChatFormatting.YELLOW);
		} else if (site.status() == BuildSite.Status.FETCHING) {
			line = Component.translatable("message.aliveworkplace.status.state.fetching").withStyle(ChatFormatting.GRAY);
		} else {
			line = Component.translatable("message.aliveworkplace.status.stage." + site.stage().name().toLowerCase()).withStyle(ChatFormatting.GRAY);
		}
		return new Status(villager.getId(), title, progress, line);
	}

	private BuilderStatusSync() {
	}
}
