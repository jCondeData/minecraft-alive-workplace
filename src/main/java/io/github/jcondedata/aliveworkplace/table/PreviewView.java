package io.github.jcondedata.aliveworkplace.table;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * B1020: the small "level" a build preview hands to block tint providers. Minecraft's own single-block renderer asks
 * {@code BlockColors} with no level at all, and some mods' tinted blocks (Cozy Home's chairs and sofas) assume one and
 * crash the game. This view holds the preview's own blocks, answers biome tints from one biome (the viewer's, or
 * untinted white without one), is fully lit, and never has block entities, so a provider that looks around gets sane
 * answers instead of a null.
 */
public final class PreviewView implements BlockAndTintGetter {
	private static final int MIN_Y = -64;
	private static final int HEIGHT = 384;

	private final Map<BlockPos, BlockState> blocks = new HashMap<>();
	private final @Nullable Biome biome;
	private @Nullable LevelLightEngine light;

	public PreviewView(@Nullable Biome biome) {
		this.biome = biome;
	}

	/** Adds a block of the build at its position in the preview. */
	public void put(BlockPos pos, BlockState state) {
		blocks.put(pos.immutable(), state);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	/** Like an overworld client level's shading: the top full, the bottom half, the sides in between. */
	@Override
	public float getShade(Direction direction, boolean shade) {
		if (!shade) {
			return 1f;
		}
		return switch (direction) {
			case DOWN -> 0.5f;
			case UP -> 1f;
			case NORTH, SOUTH -> 0.8f;
			case EAST, WEST -> 0.6f;
		};
	}

	/** The biome's color for this resolver (grass, foliage, water), or white (no tint) without a biome. */
	@Override
	public int getBlockTint(BlockPos pos, ColorResolver resolver) {
		return biome == null ? 0xFFFFFF : resolver.getColor(biome, pos.getX(), pos.getZ());
	}

	@Override
	public LevelLightEngine getLightEngine() {
		if (light == null) {
			PreviewView self = this;
			light = new LevelLightEngine(new LightChunkGetter() {
				@Override
				public @Nullable LightChunk getChunkForLighting(int x, int z) {
					return null;
				}

				@Override
				public BlockGetter getLevel() {
					return self;
				}
			}, false, false);
		}
		return light;
	}

	/** Everything in a preview is in full light. */
	@Override
	public int getBrightness(LightLayer layer, BlockPos pos) {
		return 15;
	}

	@Override
	public int getRawBrightness(BlockPos pos, int darkening) {
		return 15;
	}

	@Override
	public boolean canSeeSky(BlockPos pos) {
		return true;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public int getMinBuildHeight() {
		return MIN_Y;
	}
}
