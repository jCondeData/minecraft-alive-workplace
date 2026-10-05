package io.github.jcondedata.aliveworkplace.legend;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * A villager's strange mood (the {@code STRANGE_MOOD} attachment, 29.10): the Legend it leads to, the workstation they
 * claimed, their hall, the three materials they asked for, how many of each were last seen in the chest by the
 * workstation, how many each player brought (by UUID), the Chronicle day it began; or, after a failed mood, the day
 * their sulking ends ({@code sulkUntil}, -1 while the mood is on). Every field but the Legend and the workstation has a
 * default, so a later field never breaks an older save.
 */
public record StrangeMood(ResourceLocation legend, BlockPos station, Optional<BlockPos> hall, List<ResourceLocation> materials,
						  Map<String, Integer> seen, Map<String, Integer> givers, long started, long sulkUntil) {
	public static final Codec<StrangeMood> CODEC = RecordCodecBuilder.create(i -> i.group(
		ResourceLocation.CODEC.fieldOf("legend").forGetter(StrangeMood::legend),
		BlockPos.CODEC.fieldOf("station").forGetter(StrangeMood::station),
		BlockPos.CODEC.optionalFieldOf("hall").forGetter(StrangeMood::hall),
		ResourceLocation.CODEC.listOf().optionalFieldOf("materials", List.of()).forGetter(StrangeMood::materials),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("seen", Map.of()).forGetter(StrangeMood::seen),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("givers", Map.of()).forGetter(StrangeMood::givers),
		Codec.LONG.optionalFieldOf("started", 0L).forGetter(StrangeMood::started),
		Codec.LONG.optionalFieldOf("sulk_until", -1L).forGetter(StrangeMood::sulkUntil)
	).apply(i, StrangeMood::new));

	/** A new mood, begun on {@code day}. */
	public static StrangeMood begin(ResourceLocation legend, BlockPos station, Optional<BlockPos> hall, List<ResourceLocation> materials, long day) {
		return new StrangeMood(legend, station.immutable(), hall, List.copyOf(materials), Map.of(), Map.of(), day, -1);
	}

	/** Whether the mood is on (not sulking after it). */
	public boolean inMood() {
		return sulkUntil < 0;
	}

	/** Whether they're sulking on {@code today} (a Chronicle day). */
	public boolean sulking(long today) {
		return sulkUntil >= 0 && today < sulkUntil;
	}

	/** The last day materials may come: the mood fails at the start of the day after. */
	public long lastDay() {
		return started + StrangeMoods.DAYS - 1;
	}

	/** The same mood with what the chest holds now and who brought what. */
	public StrangeMood counted(Map<String, Integer> seenNow, Map<String, Integer> giversNow) {
		return new StrangeMood(legend, station, hall, materials, Map.copyOf(seenNow), Map.copyOf(giversNow), started, sulkUntil);
	}

	/** The failed mood: sulking until {@code until}. */
	public StrangeMood sulkingUntil(long until) {
		return new StrangeMood(legend, station, hall, materials, seen, givers, started, until);
	}
}
