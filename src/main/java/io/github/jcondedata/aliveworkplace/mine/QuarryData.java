package io.github.jcondedata.aliveworkplace.mine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What a Quarry Marker holds: the two corners the player clicked (in one dimension) and how deep
 * to dig. The quarry is the rectangle between the corners, from the higher corner down {@code depth} blocks.
 * A strip mine can instead be dug at a set height ({@code stripLevel}, the tunnels' floor) below the corners,
 * down a ladder shaft from them.
 */
public record QuarryData(Optional<ResourceLocation> dimension, Optional<BlockPos> first, Optional<BlockPos> second, int depth,
						 Optional<Integer> stripLevel) {
	/** The last choice, 2, is a strip mine: 2-high tunnels with 2 blocks of rock between them (see {@link #isStripMine}). */
	public static final List<Integer> DEPTHS = List.of(4, 8, 16, 32, 64, 2);
	public static final int STRIP_MINE = 2;
	/** Heights a strip mine can be dug at down a ladder shaft (the tunnels' floor): iron, redstone/gold/lapis, diamonds. */
	public static final List<Integer> STRIP_LEVELS = List.of(16, -16, -53);
	public static final int DEFAULT_DEPTH = 16;
	/** Largest side of a quarry, so a miner's job stays a sensible size. */
	public static final int MAX_SIDE = 32;
	/** A strip mine's tunnels can be longer: up to this along the longer side (the shorter one stays at most {@link #MAX_SIDE}). */
	public static final int MAX_TUNNEL = 64;

	public static final QuarryData EMPTY = new QuarryData(Optional.empty(), Optional.empty(), Optional.empty(), DEFAULT_DEPTH);

	public QuarryData(Optional<ResourceLocation> dimension, Optional<BlockPos> first, Optional<BlockPos> second, int depth) {
		this(dimension, first, second, depth, Optional.empty());
	}

	public static final Codec<QuarryData> CODEC = RecordCodecBuilder.create(i -> i.group(
		ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(QuarryData::dimension),
		BlockPos.CODEC.optionalFieldOf("first").forGetter(QuarryData::first),
		BlockPos.CODEC.optionalFieldOf("second").forGetter(QuarryData::second),
		Codec.INT.optionalFieldOf("depth", DEFAULT_DEPTH).forGetter(QuarryData::depth),
		Codec.INT.optionalFieldOf("strip_level").forGetter(QuarryData::stripLevel)
	).apply(i, QuarryData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, QuarryData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	/** A strip mine instead of an open pit: tunnels at the height of the marked blocks and the one below (or at {@link #stripLevel}). */
	public boolean isStripMine() {
		return depth == STRIP_MINE;
	}

	public boolean isComplete() {
		return dimension.isPresent() && first.isPresent() && second.isPresent();
	}

	/** The block volume to dig out, or empty until both corners are set. */
	public Optional<BoundingBox> area() {
		return area(Integer.MIN_VALUE);
	}

	/** {@link #area()}, with a strip mine's set height kept at or above {@code floor} (the lowest a miner digs). */
	public Optional<BoundingBox> area(int floor) {
		if (!isComplete()) {
			return Optional.empty();
		}
		BlockPos a = first.get();
		BlockPos b = second.get();
		int top = Math.max(a.getY(), b.getY());
		OptionalInt shaft = shaftTop(floor);
		int maxY = shaft.isPresent() ? tunnelFloor(floor) + 1 : top;
		return Optional.of(new BoundingBox(Math.min(a.getX(), b.getX()), maxY - depth + 1, Math.min(a.getZ(), b.getZ()),
			Math.max(a.getX(), b.getX()), maxY, Math.max(a.getZ(), b.getZ())));
	}

	/**
	 * For a strip mine at a set height below the marked corners: where the ladder shaft starts (the height of the
	 * higher corner). Empty for everything else, and when that height isn't below the corners.
	 */
	public OptionalInt shaftTop(int floor) {
		if (!isStripMine() || stripLevel.isEmpty() || !isComplete()) {
			return OptionalInt.empty();
		}
		int top = Math.max(first.get().getY(), second.get().getY());
		return tunnelFloor(floor) + 1 < top ? OptionalInt.of(top) : OptionalInt.empty();
	}

	private int tunnelFloor(int floor) {
		return Math.max(floor, stripLevel.orElse(Integer.MIN_VALUE));
	}

	/** Whether a {@code w} × {@code d} area is small enough: at most {@link #MAX_SIDE} a side, a strip mine up to {@link #MAX_TUNNEL} long. */
	public static boolean fits(int w, int d, boolean stripMine) {
		return Math.min(w, d) <= MAX_SIDE && Math.max(w, d) <= (stripMine ? MAX_TUNNEL : MAX_SIDE);
	}

	/** A strip mine at height {@code y} (typed in with {@code /workplace strip <height>}). */
	public QuarryData withStripLevel(int y) {
		return new QuarryData(dimension, first, second, STRIP_MINE, Optional.of(y));
	}

	public QuarryData withDepth(int newDepth) {
		return new QuarryData(dimension, first, second, newDepth, newDepth == STRIP_MINE ? stripLevel : Optional.empty());
	}

	/** The next choice on the marker: deeper pits, a strip mine at the marked height, then strip mines at each set height. */
	public QuarryData next() {
		if (!isStripMine()) {
			int i = DEPTHS.indexOf(depth);
			return new QuarryData(dimension, first, second, DEPTHS.get((i + 1) % DEPTHS.size()), Optional.empty());
		}
		int i = stripLevel.map(STRIP_LEVELS::indexOf).orElse(-1);
		if (i + 1 < STRIP_LEVELS.size()) {
			return new QuarryData(dimension, first, second, STRIP_MINE, Optional.of(STRIP_LEVELS.get(i + 1)));
		}
		return new QuarryData(dimension, first, second, DEPTHS.get(0), Optional.empty());
	}
}
