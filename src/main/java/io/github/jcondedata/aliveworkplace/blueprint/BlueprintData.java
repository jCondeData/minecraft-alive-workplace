package io.github.jcondedata.aliveworkplace.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Data carried by a blueprint item: which structure it describes and, once the player has
 * chosen a spot, where and how it is placed.
 *
 * @param structure id of a structure template (datapack structure, structure-block save or imported file)
 * @param size      template size, filled in by the server the first time it sees the blueprint
 * @param placement where the build goes; empty until the player right-clicks the ground
 * @param levelGround whether the builder levels the natural ground around it (right-click the air to switch)
 * @param mirrored  whether it's built flipped left to right (chosen on the style screen)
 */
public record BlueprintData(ResourceLocation structure, Optional<Vec3i> size, Optional<Placement> placement, boolean levelGround, boolean mirrored) {
	public static final Codec<BlueprintData> CODEC = RecordCodecBuilder.create(i -> i.group(
		ResourceLocation.CODEC.fieldOf("structure").forGetter(BlueprintData::structure),
		Vec3i.CODEC.optionalFieldOf("size").forGetter(BlueprintData::size),
		Placement.CODEC.optionalFieldOf("placement").forGetter(BlueprintData::placement),
		Codec.BOOL.optionalFieldOf("level_ground", true).forGetter(BlueprintData::levelGround),
		Codec.BOOL.optionalFieldOf("mirrored", false).forGetter(BlueprintData::mirrored)
	).apply(i, BlueprintData::new));

	public BlueprintData(ResourceLocation structure, Optional<Vec3i> size, Optional<Placement> placement) {
		this(structure, size, placement, true, false);
	}

	public BlueprintData withLevelGround(boolean level) {
		return new BlueprintData(structure, size, placement, level, mirrored);
	}

	public BlueprintData withMirrored(boolean flip) {
		return new BlueprintData(structure, size, placement, levelGround, flip);
	}

	/** How a new placement of it is mirrored: flipped left to right (the front stays the front), or not. */
	public Mirror mirror() {
		return mirrored ? Mirror.FRONT_BACK : Mirror.NONE;
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public BlueprintData withPlacement(Optional<Placement> newPlacement) {
		return new BlueprintData(structure, size, newPlacement, levelGround, mirrored);
	}

	public BlueprintData withStructure(ResourceLocation newStructure) {
		return new BlueprintData(newStructure, size, placement, levelGround, mirrored);
	}

	public BlueprintData withSize(Vec3i newSize) {
		return new BlueprintData(structure, Optional.of(newSize), placement, levelGround, mirrored);
	}

	/**
	 * @param origin world position of template (0,0,0) after rotation/mirroring around it
	 */
	public record Placement(ResourceLocation dimension, BlockPos origin, Rotation rotation, Mirror mirror) {
		public static final Codec<Placement> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("dimension").forGetter(Placement::dimension),
			BlockPos.CODEC.fieldOf("origin").forGetter(Placement::origin),
			Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(Placement::rotation),
			Mirror.CODEC.optionalFieldOf("mirror", Mirror.NONE).forGetter(Placement::mirror)
		).apply(i, Placement::new));

		public ResourceKey<Level> dimensionKey() {
			return ResourceKey.create(Registries.DIMENSION, dimension);
		}
	}
}
