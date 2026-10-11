package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Dressed by class, in game (34.18): each client is told a villager's class and stage of life in a small packet when it
 * starts tracking them, and again whenever either changes (a household rises or falls a class, a child takes their
 * parents', a villager becomes an elder). The client draws {@link ClassOutfits}' layers from it over the job's outfit.
 * {@code classOutfits} in the config off: nothing is sent and nothing drawn.
 */
public final class ClassLook {
	/** {@code classOutfits} in the config. */
	public static boolean ENABLED = true;

	/**
	 * S2C: villager {@code entityId} is of class {@code cls} (none: not seeded yet), wearing {@code outfit} over their
	 * job's (none: a Peasant), at stage of life {@code stage} (a {@link LifeStages.Stage} ordinal).
	 */
	public record Look(int entityId, Optional<ResourceLocation> cls, Optional<ResourceLocation> outfit, int stage) implements CustomPacketPayload {
		public static final Type<Look> TYPE = new Type<>(AliveWorkplace.id("class_look"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Look> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Look::entityId,
			ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), Look::cls,
			ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), Look::outfit,
			ByteBufCodecs.VAR_INT, Look::stage,
			Look::new);

		/** The stage of life this look was sent for. */
		public LifeStages.Stage lifeStage() {
			LifeStages.Stage[] stages = LifeStages.Stage.values();
			return stage >= 0 && stage < stages.length ? stages[stage] : LifeStages.Stage.GROWN;
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Where looks go: null sends them to the player's client (tests listen here instead, a mock player has no client). */
	@Nullable
	public static BiConsumer<ServerPlayer, Look> listener;

	private ClassLook() {
	}

	public static void init() {
		Platform.get().clientbound(Look.TYPE, Look.CODEC);
		Platform.get().onStartTracking(ClassLook::onStartTracking);
	}

	/** What a client should be told about {@code villager} now. */
	public static Look look(Villager villager) {
		long day = villager.level() instanceof ServerLevel level ? Chronicle.day(level) : 0;
		SocialClasses.SocialClass c = SocialClasses.of(villager);
		return new Look(villager.getId(), Optional.ofNullable(c).map(SocialClasses.SocialClass::id), Optional.ofNullable(ClassOutfits.outfit(c)),
			LifeStages.stage(villager, day).ordinal());
	}

	static void onStartTracking(Entity entity, ServerPlayer player) {
		if (ENABLED && entity instanceof Villager villager) {
			deliver(player, look(villager));
		}
	}

	/** Tells everyone who sees {@code villager} their class and stage of life now (one of them changed). */
	public static void update(Villager villager) {
		if (!ENABLED || villager.level().isClientSide()) {
			return;
		}
		Look look = look(villager);
		for (ServerPlayer player : Platform.get().tracking(villager)) {
			deliver(player, look);
		}
	}

	private static void deliver(ServerPlayer player, Look look) {
		BiConsumer<ServerPlayer, Look> to = listener;
		if (to != null) {
			to.accept(player, look);
		} else if (Platform.get().canSend(player, Look.TYPE)) {
			Platform.get().send(player, look);
		}
	}
}
