package io.github.jcondedata.aliveworkplace.mine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What a Quarry Marker holds: the two corners the player clicked (in one dimension) and how deep
 * to dig. The quarry is the rectangle between the corners, from the higher corner down {@code depth} blocks.
 */
public record QuarryData(Optional<ResourceLocation> dimension, Optional<BlockPos> first, Optional<BlockPos> second, int depth) {
	public static final List<Integer> DEPTHS = List.of(4, 8, 16, 32, 64);
	public static final int DEFAULT_DEPTH = 16;
	/** Largest side of a quarry, so a miner's job stays a sensible size. */
	public static final int MAX_SIDE = 32;

	public static final QuarryData EMPTY = new QuarryData(Optional.empty(), Optional.empty(), Optional.empty(), DEFAULT_DEPTH);

	public static final Codec<QuarryData> CODEC = RecordCodecBuilder.create(i -> i.group(
		ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(QuarryData::dimension),
		BlockPos.CODEC.optionalFieldOf("first").forGetter(QuarryData::first),
		BlockPos.CODEC.optionalFieldOf("second").forGetter(QuarryData::second),
		Codec.INT.optionalFieldOf("depth", DEFAULT_DEPTH).forGetter(QuarryData::depth)
	).apply(i, QuarryData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, QuarryData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public boolean isComplete() {
		return dimension.isPresent() && first.isPresent() && second.isPresent();
	}

	/** The block volume to dig out, or empty until both corners are set. */
	public Optional<BoundingBox> area() {
		if (!isComplete()) {
			return Optional.empty();
		}
		BlockPos a = first.get();
		BlockPos b = second.get();
		int top = Math.max(a.getY(), b.getY());
		return Optional.of(new BoundingBox(Math.min(a.getX(), b.getX()), top - depth + 1, Math.min(a.getZ(), b.getZ()),
			Math.max(a.getX(), b.getX()), top, Math.max(a.getZ(), b.getZ())));
	}

	public QuarryData withDepth(int newDepth) {
		return new QuarryData(dimension, first, second, newDepth);
	}

	public int nextDepth() {
		int i = DEPTHS.indexOf(depth);
		return DEPTHS.get((i + 1) % DEPTHS.size());
	}
}
