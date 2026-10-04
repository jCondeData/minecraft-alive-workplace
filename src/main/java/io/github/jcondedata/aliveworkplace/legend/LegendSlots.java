package io.github.jcondedata.aliveworkplace.legend;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Rarities and caps (29.3): who may come where ({@link #whyNot}), the announcements when a Legend comes, and keeping
 * {@link LegendRecord} true through death and the grave, the zombie villager and the cure, and a hall placed again.
 */
public final class LegendSlots {
	static void init() {
		Seasons.onNewDay((overworld, date) -> LegendRecord.get(overworld).newDay(overworld.getServer(), date.day()));
		Platform.get().onMobConversion(LegendSlots::onConversion);
	}

	/** Mythic Legends a village of {@code rank} may hold. */
	public static int mythicCap(VillageRanks.Rank rank) {
		int[] caps = Legends.MYTHIC_CAP;
		return caps.length == 0 ? 0 : caps[Math.min(rank.ordinal(), caps.length - 1)];
	}

	/**
	 * Why {@code legend} may not come to the village round {@code hall} (null: no village), or empty when they may: a Rare
	 * one is one of each per village, a Legendary one one of each per world (any dimension), and a village holds at most
	 * {@link #mythicCap} Mythic ones for its rank (one that falls back a rank keeps those it has). {@code self}, the villager
	 * who would become the Legend, doesn't count against themselves.
	 */
	public static Optional<Component> whyNot(ServerLevel level, @Nullable BlockPos hall, Legend legend, @Nullable UUID self) {
		if (!Legends.ENABLED) {
			return Optional.of(Component.translatable("message.aliveworkplace.legend.off"));
		}
		LegendRecord record = LegendRecord.get(level);
		String dim = level.dimension().location().toString();
		return switch (legend.rarity()) {
			case RARE -> record.holding(e -> !e.villager().equals(self) && e.id().equals(legend.id()) && e.in(dim, hall)) > 0
				? Optional.of(Component.translatable("message.aliveworkplace.legend.refused.rare", legend.titleText())) : Optional.empty();
			case LEGENDARY -> record.holding(e -> !e.villager().equals(self) && e.id().equals(legend.id())) > 0
				? Optional.of(Component.translatable("message.aliveworkplace.legend.refused.legendary", legend.titleText())) : Optional.empty();
			case MYTHIC -> {
				VillageRanks.Rank rank = hall == null ? VillageRanks.Rank.HAMLET : VillageRanks.of(level, hall);
				long have = record.holding(e -> !e.villager().equals(self) && e.rarity() == Rarity.MYTHIC && e.in(dim, hall));
				int cap = mythicCap(rank);
				yield have < cap ? Optional.empty()
					: Optional.of(Component.translatable("message.aliveworkplace.legend.refused.mythic", rank.title(), cap, have));
			}
		};
	}

	/**
	 * Who hears of a Legend coming to the village round {@code hall}: a Mythic one, every player on the server; a Rare or
	 * Legendary one, the players in the village and the hall's owner wherever they are.
	 */
	public static List<ServerPlayer> audience(ServerLevel level, @Nullable BlockPos hall, BlockPos where, Rarity rarity) {
		if (rarity == Rarity.MYTHIC) {
			return List.copyOf(level.getServer().getPlayerList().getPlayers());
		}
		List<ServerPlayer> out = new ArrayList<>(level.getPlayers(p -> VillageHalls.area(hall != null ? hall : where).contains(p.position())));
		if (hall != null && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.owner() != null) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entity.owner());
			if (owner != null && !out.contains(owner)) {
				out.add(owner);
			}
		}
		return out;
	}

	/** The announcement: a Mythic one in gold with the village's direction from spawn, the others in their rarity's colour. */
	public static Component message(ServerLevel level, @Nullable BlockPos hall, BlockPos where, Component name, Legend legend, boolean guest) {
		Component village = hall != null ? VillageHalls.name(level, hall) : Component.translatable("message.aliveworkplace.legend.the_wilds");
		if (legend.rarity() == Rarity.MYTHIC) {
			BlockPos at = hall != null ? hall : where;
			BlockPos spawn = level.getServer().overworld().getSharedSpawnPos();
			double dx = at.getX() - spawn.getX();
			double dz = at.getZ() - spawn.getZ();
			int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
			// 0 = south, counting clockwise in eighths (Minecraft's +z is south), as the hall's map page does.
			int eighth = Math.floorMod((int) Math.round(Math.atan2(-dx, dz) / (Math.PI / 4)), 8);
			String[] directions = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
			return Component.translatable("message.aliveworkplace.legend.mythic." + (guest ? "guest" : "settled"), name, legend.titleText(), village,
				distance, Component.translatable("screen.aliveworkplace.hall.dir." + directions[eighth])).withStyle(ChatFormatting.GOLD);
		}
		return Component.translatable("message.aliveworkplace.legend." + (guest ? "guest" : "settled"), name, legend.titleText(), legend.rarity().title(), village)
			.withStyle(legend.rarity().color);
	}

	/**
	 * Tells the village (or the server) that {@code villager}, the Legend {@code legend}, has come as a guest or settled,
	 * and writes it in the chronicle. Returns the players told.
	 */
	public static List<ServerPlayer> announce(ServerLevel level, @Nullable BlockPos hall, Villager villager, Legend legend, boolean guest) {
		Component message = message(level, hall, villager.blockPosition(), villager.getDisplayName(), legend, guest);
		List<ServerPlayer> told = audience(level, hall, villager.blockPosition(), legend.rarity());
		for (ServerPlayer player : told) {
			Chat.chat(player, message);
			if (legend.rarity() == Rarity.MYTHIC) {
				player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
			}
		}
		if (hall != null) {
			Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.legend." + (guest ? "guest" : "settled"),
				villager.getDisplayName(), legend.titleText(), legend.rarity().title()));
		}
		return told;
	}

	/** A Legend died (a villager, or a zombie villager): their grave keeps their slot; with none, its clock starts. */
	public static void onDeath(ServerLevel level, LivingEntity entity, @Nullable BlockPos grave) {
		if (entity.isRemoved() || !ModAttachments.LEGEND.has(entity)) {
			return; // turned into a zombie villager (onConversion has them), or not a Legend
		}
		LegendRecord.get(level).died(entity.getUUID(), Chronicle.day(level), Optional.ofNullable(grave));
	}

	/** The Legend who was {@code before} came back from their grave as {@code villager}. */
	public static void onRevived(ServerLevel level, UUID before, Villager villager) {
		if (ModAttachments.LEGEND.has(villager)) {
			LegendRecord.get(level).back(before, villager.getUUID());
			LegendPowers.seen(villager);
		}
	}

	/** A Legend turned into a zombie villager stays the Legend inside it, and cured is themselves again. */
	static void onConversion(Entity before, Entity after) {
		LegendData data = ModAttachments.LEGEND.get(before);
		if (data == null || !(after.level() instanceof ServerLevel level)) {
			return;
		}
		ModAttachments.LEGEND.set(after, data);
		LegendRecord record = LegendRecord.get(level);
		if (after instanceof ZombieVillager) {
			record.turned(before.getUUID(), after.getUUID());
		} else if (after instanceof Villager villager) {
			record.back(before.getUUID(), villager.getUUID());
			LegendPowers.seen(villager);
		}
	}

	/**
	 * The hall's round: Legends in its village whose own hall is gone (broken, maybe placed again elsewhere) join the
	 * nearest hall, this one when it is theirs.
	 */
	public static void round(ServerLevel level, BlockPos hall) {
		if (!Legends.ENABLED) {
			return;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && ModAttachments.LEGEND.has(v))) {
			LegendData data = ModAttachments.LEGEND.get(v);
			if (data == null || data.hall().equals(Optional.of(hall))
				|| data.hall().isPresent() && level.getBlockEntity(data.hall().get()) instanceof VillageHallBlockEntity) {
				continue;
			}
			if (!VillageHalls.nearest(level, v.blockPosition()).equals(Optional.of(hall))) {
				continue;
			}
			ModAttachments.LEGEND.set(v, new LegendData(data.id(), data.name(), data.guest(), Optional.of(hall), data.since(), data.lastDay(),
				data.unmet(), data.strikeSince(), data.lastLuxury(), data.way()));
			LegendRecord.get(level).moved(v.getUUID(), hall);
			Legends.get(data.id()).ifPresent(legend -> Chronicle.record(level, hall, Chronicle.Kind.LEGEND,
				Component.translatable("chronicle.aliveworkplace.legend.joined", v.getDisplayName(), legend.titleText())));
		}
	}

	/** A settled Legend not yet on the record (one who settled before 29.3) is put on it. */
	static void onRecord(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data == null || !data.settled() || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		LegendRecord record = LegendRecord.get(level);
		if (record.entry(villager.getUUID()).isEmpty()) {
			Legends.get(data.id()).ifPresent(legend -> record.settled(legend.id(), villager.getUUID(), level.dimension(), data.hall(), legend.rarity(),
				villager.getName().getString(), data.since() + 1));
		}
	}

	private LegendSlots() {
	}
}
