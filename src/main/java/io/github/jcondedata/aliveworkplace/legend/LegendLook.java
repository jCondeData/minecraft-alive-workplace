package io.github.jcondedata.aliveworkplace.legend;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * How a Legend looks (29.4), told to each client in a small packet when it starts tracking the villager (and again
 * when someone becomes a Legend or stops being one): whether they are one and the outfit drawn over their trade's
 * ({@code textures/entity/villager/legend/<id>.png}, or the file's {@code outfit}). The client draws the outfit and
 * their name in gold over their head; the server adds a soft end-rod sparkle every 10 seconds.
 */
public final class LegendLook {
	/** S2C: {@code entityId} is a Legend ({@code legend}) wearing {@code outfit}; false: an ordinary villager again. */
	public record Look(int entityId, boolean legend, ResourceLocation outfit) implements CustomPacketPayload {
		public static final Type<Look> TYPE = new Type<>(AliveWorkplace.id("legend_look"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Look> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Look::entityId,
			ByteBufCodecs.BOOL, Look::legend,
			ResourceLocation.STREAM_CODEC, Look::outfit,
			Look::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The stand-in outfit the client draws for a Legend whose own outfit texture isn't there. */
	public static final ResourceLocation PLACEHOLDER = AliveWorkplace.id("textures/entity/villager/legend/placeholder.png");
	/** Particles in one sparkle. */
	static final int SPARKS = 5;

	static void init() {
		Platform.get().clientbound(Look.TYPE, Look.CODEC);
		Platform.get().onStartTracking(LegendLook::onStartTracking);
	}

	/** The outfit a Legend wears over their trade's: the file's {@code outfit}, else {@code <ns>:textures/entity/villager/legend/<id>.png}. */
	public static ResourceLocation outfit(Legend legend) {
		return legend.outfit().orElse(ResourceLocation.fromNamespaceAndPath(legend.id().getNamespace(),
			"textures/entity/villager/legend/" + legend.id().getPath() + ".png"));
	}

	/** What a client should be told about {@code villager}: their look, or null if they are no Legend (or Legends are off). */
	public static Look look(Villager villager) {
		return Legends.of(villager).map(l -> new Look(villager.getId(), true, outfit(l))).orElse(null);
	}

	static void onStartTracking(Entity entity, ServerPlayer player) {
		if (entity instanceof Villager villager && ModAttachments.LEGEND.has(villager)) {
			Look look = look(villager);
			if (look != null && Platform.get().canSend(player, Look.TYPE)) {
				Platform.get().send(player, look);
			}
		}
	}

	/** Tells everyone who sees {@code villager} how they look now (they became a Legend, or stopped being one). */
	public static void update(Villager villager) {
		Look look = look(villager);
		Look send = look != null ? look : new Look(villager.getId(), false, PLACEHOLDER);
		for (ServerPlayer player : Platform.get().tracking(villager)) {
			if (Platform.get().canSend(player, Look.TYPE)) {
				Platform.get().send(player, send);
			}
		}
	}

	/** A soft end-rod sparkle round the Legend (every 10 seconds, from {@link Legends#tick}). */
	public static void sparkle(Villager villager) {
		if (villager.level() instanceof ServerLevel level && !villager.isInvisible()) {
			level.sendParticles(ParticleTypes.END_ROD, villager.getX(), villager.getY() + villager.getBbHeight() * 0.6, villager.getZ(), SPARKS,
				0.35, 0.5, 0.35, 0.01);
		}
	}

	private LegendLook() {
	}
}
