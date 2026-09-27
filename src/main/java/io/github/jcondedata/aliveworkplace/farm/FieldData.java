package io.github.jcondedata.aliveworkplace.farm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** What a Field Marker holds: two opposite corners of a field, in one dimension. */
public record FieldData(Optional<ResourceLocation> dimension, Optional<BlockPos> first, Optional<BlockPos> second) {
	/** Largest side of a field, so one farmer's job stays a sensible size. */
	public static final int MAX_SIDE = 32;

	public static final FieldData EMPTY = new FieldData(Optional.empty(), Optional.empty(), Optional.empty());

	public static final Codec<FieldData> CODEC = RecordCodecBuilder.create(i -> i.group(
		ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(FieldData::dimension),
		BlockPos.CODEC.optionalFieldOf("first").forGetter(FieldData::first),
		BlockPos.CODEC.optionalFieldOf("second").forGetter(FieldData::second)
	).apply(i, FieldData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, FieldData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public boolean isComplete() {
		return dimension.isPresent() && first.isPresent() && second.isPresent();
	}

	/** The blocks between the two corners (the ground of the field), or empty until both are set. */
	public Optional<BoundingBox> area() {
		return isComplete() ? Optional.of(BoundingBox.fromCorners(first.get(), second.get())) : Optional.empty();
	}
}
