package io.github.jcondedata.aliveworkplace.people;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Couples: in a village with a Village Hall, now and then two grown villagers who aren't family start courting, and
 * {@link #COURTSHIP_DAYS} days later they marry — a wedding at the village's bell, with fireworks, and everyone there in
 * a better mood (as after a festival). Couples are happier near each other, a married couple are the first to have a
 * baby when there's a free bed, and when one of them dies the other mourns for {@link #MOURNING_DAYS} days. The hall's
 * list says who's with whom. {@code villagerCouples} in the config turns it off.
 */
public final class Couples {
	public static boolean ENABLED = true;
	/** The chance a day that two singles start courting, in a village with at least two. */
	public static final float DAILY_CHANCE = 0.3f;
	public static final int COURTSHIP_DAYS = 2;
	public static final int MOURNING_DAYS = 5;

	/** Who a villager is with, since which day, and whether they're married yet. */
	public record Partner(UUID id, Component name, long since, boolean married) {
		public static final Codec<Partner> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Partner::id),
			ComponentSerialization.CODEC.fieldOf("name").forGetter(Partner::name),
			Codec.LONG.fieldOf("since").forGetter(Partner::since),
			Codec.BOOL.optionalFieldOf("married", false).forGetter(Partner::married)
		).apply(i, Partner::new));
	}

	@Nullable
	public static Partner partner(Villager villager) {
		return ModAttachments.PARTNER.get(villager);
	}

	public static boolean isMourning(ServerLevel level, Villager villager) {
		Long day = ModAttachments.WIDOWED_DAY.get(villager);
		return day != null && Chronicle.day(level) - day < MOURNING_DAYS;
	}

	/** Grown, not with anyone, and staying (not a mercenary or a passing traveller). */
	static boolean single(Villager villager) {
		return villager.isAlive() && !villager.isBaby() && partner(villager) == null && !Mercenaries.isMercenary(villager)
			&& !io.github.jcondedata.aliveworkplace.inn.Innkeepers.isTraveller(villager);
	}

	/** Parent and child, or brother and sister (by the names the families remember). */
	static boolean related(Villager a, Villager b) {
		Families.Parents pa = Families.parents(a);
		Families.Parents pb = Families.parents(b);
		String na = a.getDisplayName().getString();
		String nb = b.getDisplayName().getString();
		if (pa != null && (pa.mother().getString().equals(nb) || pa.father().getString().equals(nb))) {
			return true;
		}
		if (pb != null && (pb.mother().getString().equals(na) || pb.father().getString().equals(na))) {
			return true;
		}
		return pa != null && pb != null && (pa.mother().getString().equals(pb.mother().getString()) || pa.father().getString().equals(pb.father().getString()));
	}

	/** The hall's round: weddings that are due; now and then two singles start courting. */
	public static void round(ServerLevel level, BlockPos hall) {
		if (!ENABLED) {
			return;
		}
		List<Villager> village = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive);
		long today = Chronicle.day(level);
		long time = level.getDayTime() % VillageNeeds.DAY;
		for (Villager villager : village) {
			Partner p = partner(villager);
			if (p != null && !p.married() && today - p.since() >= COURTSHIP_DAYS && time >= 6000 && time < 11000
				&& level.getEntity(p.id()) instanceof Villager other && other.isAlive() && village.contains(other)) {
				wed(level, hall, villager, other);
			}
		}
		int rounds = (int) Math.max(1, VillageNeeds.DAY / VillageNeeds.CHECK_EVERY);
		if (level.random.nextFloat() < DAILY_CHANCE / rounds) {
			court(level, hall, village);
		}
	}

	/** Two singles in {@code village} (the nearest pair who aren't family) start courting; returns them, or empty. */
	public static List<Villager> court(ServerLevel level, BlockPos hall, List<Villager> village) {
		List<Villager> singles = new ArrayList<>(village.stream().filter(Couples::single).toList());
		if (singles.size() < 2) {
			return List.of();
		}
		Villager first = singles.get(level.random.nextInt(singles.size()));
		Villager second = singles.stream().filter(v -> v != first && !related(first, v)).min(Comparator.comparingDouble(first::distanceToSqr)).orElse(null);
		if (second == null) {
			return List.of();
		}
		long today = Chronicle.day(level);
		ModAttachments.PARTNER.set(first, new Partner(second.getUUID(), second.getDisplayName(), today, false));
		ModAttachments.PARTNER.set(second, new Partner(first.getUUID(), first.getDisplayName(), today, false));
		for (Villager v : List.of(first, second)) {
			level.sendParticles(ParticleTypes.HEART, v.getX(), v.getEyeY() + 0.4, v.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		}
		Chronicle.record(level, hall, Chronicle.Kind.WEDDING, Component.translatable("chronicle.aliveworkplace.courting", first.getDisplayName(),
			second.getDisplayName()));
		return List.of(first, second);
	}

	/** {@code a} and {@code b} marry: at the bell, fireworks, everyone in the village the happier for it. */
	public static void wed(ServerLevel level, BlockPos hall, Villager a, Villager b) {
		long today = Chronicle.day(level);
		ModAttachments.PARTNER.set(a, new Partner(b.getUUID(), b.getDisplayName(), today, true));
		ModAttachments.PARTNER.set(b, new Partner(a.getUUID(), a.getDisplayName(), today, true));
		BlockPos square = venue(level, hall);
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)) {
			ModAttachments.FESTIVAL_DAY.set(v, today);
		}
		for (Villager v : List.of(a, b)) {
			level.sendParticles(ParticleTypes.HEART, v.getX(), v.getEyeY() + 0.4, v.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
		}
		for (int i = 0; i < 3; i++) {
			BlockPos column = square.offset(level.random.nextInt(7) - 3, 0, level.random.nextInt(7) - 3);
			Festivals.launch(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
		}
		level.playSound(null, square, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1f);
		level.playSound(null, square, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1f);
		Component name = VillageHalls.name(level, hall);
		double r = VillageHalls.RADIUS + 32;
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.wedding", a.getDisplayName(), b.getDisplayName(), name)
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		Chronicle.record(level, hall, Chronicle.Kind.WEDDING, Component.translatable("chronicle.aliveworkplace.wedding", a.getDisplayName(),
			b.getDisplayName()));
	}

	/** Where weddings are held: the village's finished Chapel, else its bell. */
	public static BlockPos venue(ServerLevel level, BlockPos hall) {
		return io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS).stream()
			.filter(f -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.base(f.structure())
				.equals(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.CHAPEL.id()))
			.map(f -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(f.placement(),
				io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.CHAPEL.size()))
			.map(box -> new BlockPos(box.getCenter().getX(), box.minY() + 1, box.getCenter().getZ()))
			.findFirst().orElseGet(() -> Festivals.square(level, hall));
	}

	/** A villager died: their partner, if any, is left to mourn. */
	public static void onDeath(ServerLevel level, Villager villager) {
		Partner p = partner(villager);
		if (p == null) {
			return;
		}
		Entity other = level.getEntity(p.id());
		if (other instanceof Villager partner && ModAttachments.PARTNER.has(partner)) {
			ModAttachments.PARTNER.remove(partner);
			ModAttachments.WIDOWED_DAY.set(partner, Chronicle.day(level));
		}
	}

	/** Couples courting (not married yet) in the village round {@code hall}: one for every two villagers courting, rounded up. */
	public static long courting(ServerLevel level, BlockPos hall) {
		long courting = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && partner(v) != null && !partner(v).married()).size();
		return (courting + 1) / 2;
	}

	/** The married couple (both in the village) nearest {@code at}, or empty: the first to have a baby. */
	public static List<Villager> coupleNear(ServerLevel level, BlockPos hall, BlockPos at) {
		List<Villager> village = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby() && !v.isSleeping());
		return village.stream()
			.filter(v -> partner(v) != null && partner(v).married())
			.sorted(Comparator.comparingDouble(v -> v.distanceToSqr(at.getCenter())))
			.map(v -> level.getEntity(partner(v).id()) instanceof Villager other && village.contains(other) ? List.of(v, other) : List.<Villager>of())
			.filter(pair -> !pair.isEmpty())
			.findFirst().orElse(List.of());
	}

	private Couples() {
	}
}
