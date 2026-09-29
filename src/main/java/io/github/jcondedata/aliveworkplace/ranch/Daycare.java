package io.github.jcondedata.aliveworkplace.ranch;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * The daycare (with Cobblemon): a Rancher looks after players' Pokémon — right-click them with an empty hand, leave up
 * to {@link #MAX_PER_PLAYER} from your party, and they gain experience while they're in the rancher's care (a point
 * every second at Novice, twice that at Master); collect them for {@link #price} emeralds (or their worth in
 * CobbleDollars), one plus one for every level they gained, as in the games. If the rancher dies, the Pokémon go to their
 * trainers' PCs. The screen and the Pokémon live in {@code compat/cobblemon/CobblemonDaycare}.
 */
public final class Daycare {
	public static final boolean COBBLEMON = Platform.get().isModLoaded("cobblemon");
	public static final int MAX_PER_PLAYER = 2;
	/** Most Pokémon one rancher looks after. */
	public static final int MAX_BOARDERS = 8;
	public static final String XP_SOURCE = "aliveworkplace_daycare";

	/** A Pokémon in a rancher's care: whose, the Pokémon as it was left, and since when (game time). */
	public record Boarder(UUID owner, String ownerName, CompoundTag pokemon, long since) {
		public static final Codec<Boarder> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Boarder::owner),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Boarder::ownerName),
			CompoundTag.CODEC.fieldOf("pokemon").forGetter(Boarder::pokemon),
			Codec.LONG.fieldOf("since").forGetter(Boarder::since)
		).apply(i, Boarder::new));
	}

	public static List<Boarder> boarders(Villager rancher) {
		return ModAttachments.DAYCARE.getOrElse(rancher, List.of());
	}

	public static void setBoarders(Villager rancher, List<Boarder> boarders) {
		if (boarders.isEmpty()) {
			ModAttachments.DAYCARE.remove(rancher);
		} else {
			ModAttachments.DAYCARE.set(rancher, List.copyOf(boarders));
		}
	}

	/** Experience a Pokémon gains in {@code rancher}'s care over {@code ticks}. */
	public static int experience(Villager rancher, long ticks) {
		double perSecond = 1 + 0.25 * (Math.max(1, rancher.getVillagerData().getLevel()) - 1);
		return (int) Math.min(Integer.MAX_VALUE / 2, Math.max(0, ticks) / 20 * perSecond);
	}

	/** What collecting a Pokémon costs, in emeralds: one, and one for every level it gained. */
	public static int price(int levelsGained) {
		return 1 + Math.max(0, levelsGained);
	}

	/** Whether {@code villager} keeps a daycare: a rancher, or anyone still holding Pokémon for someone. */
	public static boolean keepsDaycare(Villager villager) {
		return COBBLEMON && (RancherWork.isRancher(villager) || !boarders(villager).isEmpty());
	}

	public static void open(ServerPlayer player, Villager rancher) {
		if (COBBLEMON) {
			DaycareDesk.EXTENSION.run(desk -> desk.open(player, rancher));
		}
	}

	/** The rancher died: every Pokémon in their care goes to its trainer's PC (with what it gained, free). */
	public static void onDeath(ServerLevel level, Villager villager) {
		if (COBBLEMON && !boarders(villager).isEmpty()) {
			DaycareDesk.EXTENSION.run(desk -> desk.returnAll(level, villager));
		}
	}

	private Daycare() {
	}
}
