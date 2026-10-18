package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * Who Mega Evolves in the village (ROADMAP 38.5, Mega Showdown). A trainer's team holds a Mega Stone when the trainer is
 * a Master (as before), and with {@code villageMegas} on also when they are a Trainer Leader of a City-rank village, a
 * visiting trainer at a Stadium Day, or a trainer in a Festival Cup's final. Every one of them wears a Mega Bracelet over
 * their outfit: the server tells each client who wears one ({@link Bracelet}), checked every {@link #CHECK_EVERY} ticks
 * of a trainer's life and when a player starts seeing them. Without Mega Showdown nothing here happens: no extra
 * stones, no bracelets, no packets.
 */
public final class MegaBracelets {
	/** Whether Mega Showdown is installed. */
	public static final boolean MOD = Platform.get().isModLoaded("mega_showdown");
	/** {@code villageMegas} and its expansion's gate (set by the config). */
	public static boolean ENABLED = false;
	/** Ticks between a trainer's checks. */
	static final int CHECK_EVERY = 40;
	/** What each tracked trainer's clients were last told (the server side's memory, so a change is sent once). */
	private static final Map<Villager, Boolean> TOLD = new WeakHashMap<>();

	/** S2C: {@code entityId} wears a Mega Bracelet ({@code wears}), or no longer does. */
	public record Bracelet(int entityId, boolean wears) implements CustomPacketPayload {
		public static final Type<Bracelet> TYPE = new Type<>(AliveWorkplace.id("mega_bracelet"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Bracelet> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Bracelet::entityId,
			ByteBufCodecs.BOOL, Bracelet::wears,
			Bracelet::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private MegaBracelets() {
	}

	public static void init() {
		Platform.get().clientbound(Bracelet.TYPE, Bracelet.CODEC);
		Platform.get().onStartTracking(MegaBracelets::onStartTracking);
	}

	/** Whether the village's extra Mega trainers (Leaders of a City, Cup finalists, Stadium visitors) are on. */
	public static boolean extraMegas() {
		return MOD && ENABLED;
	}

	/** Whether {@code villager}'s team can Mega Evolve: one of its Pokémon holds its Mega Stone. */
	public static boolean canMegaEvolve(Villager villager) {
		if (!MOD || villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		boolean delegate = CupDays.isDelegate(villager);
		if (!Trainers.isTrainer(villager) && !delegate) {
			return false;
		}
		if (delegate) { // a Cup delegate fields the Cup's themed team: a Mega Stone only in the final
			return ENABLED && CupDays.inFinal(level, villager);
		}
		if (Trainers.tier(villager) >= 5) {
			return true; // a Master, as before 38.5
		}
		return ENABLED && (cityLeader(level, villager) || StadiumDays.isVisitor(villager) || CupDays.inFinal(level, villager));
	}

	/** A Trainer Leader whose village (the nearest Village Hall's) is a City. */
	public static boolean cityLeader(ServerLevel level, Villager villager) {
		return Trainers.isLeader(villager) && !StadiumDays.isVisitor(villager) && VillageHalls.nearest(level, villager.blockPosition())
			.map(hall -> VillageRanks.of(level, hall) == VillageRanks.Rank.CITY).orElse(false);
	}

	/** Whether {@code villager} wears the bracelet: their team can Mega Evolve and the village's Megas are on. */
	public static boolean wears(Villager villager) {
		return extraMegas() && canMegaEvolve(villager);
	}

	/** Every {@link #CHECK_EVERY} ticks of a villager's life (from the villager's server tick): tells its watchers if that changed. */
	public static void tick(Villager villager) {
		if (!MOD || (villager.tickCount + villager.getId()) % CHECK_EVERY != 0 || !(villager.level() instanceof ServerLevel)) {
			return;
		}
		boolean now = wears(villager);
		Boolean before = TOLD.get(villager);
		if (before == null ? now : before != now) {
			update(villager, now);
		}
	}

	/** What this villager's clients were last told (tests). */
	public static boolean told(Villager villager) {
		return TOLD.getOrDefault(villager, false);
	}

	/** Checks {@code villager} now and tells its watchers (tests, and a rank or Cup change that shouldn't wait). */
	public static void refresh(Villager villager) {
		update(villager, wears(villager));
	}

	static void update(Villager villager, boolean wears) {
		TOLD.put(villager, wears);
		Bracelet bracelet = new Bracelet(villager.getId(), wears);
		for (ServerPlayer player : Platform.get().tracking(villager)) {
			if (Platform.get().canSend(player, Bracelet.TYPE)) {
				Platform.get().send(player, bracelet);
			}
		}
	}

	static void onStartTracking(Entity entity, ServerPlayer player) {
		if (MOD && entity instanceof Villager villager && wears(villager) && Platform.get().canSend(player, Bracelet.TYPE)) {
			TOLD.put(villager, true);
			Platform.get().send(player, new Bracelet(villager.getId(), true));
		}
	}

	/** Forgets what was told (tests). */
	public static void forget() {
		TOLD.clear();
	}
}
