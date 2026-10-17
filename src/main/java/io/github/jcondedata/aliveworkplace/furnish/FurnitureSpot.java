package io.github.jcondedata.aliveworkplace.furnish;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One piece of vanilla furniture a blueprint draws (ROADMAP 37.2, design note M37, "The furniture spot format"): its
 * kind, its position in the template, the way a person using it faces, its wood and/or colour, and the vanilla blocks it
 * is drawn with, relative to {@link #pos}. Read from {@code data/<namespace>/blueprint_furniture/<blueprint>.json} by
 * {@link BlueprintFurniture}; 37.3 turns each into the piece of an installed furniture mod.
 */
public record FurnitureSpot(Kind kind, BlockPos pos, Direction facing, @Nullable String wood, @Nullable String colour, List<Cell> blocks) {
	/** A vanilla block of the spot: where it is (relative to the spot) and what it is. */
	public record Cell(BlockPos at, BlockState state) {
	}

	/** The kinds of furniture (the design note's table). */
	public enum Kind {
		CHAIR, STOOL, BENCH, COUCH, TABLE, SIDE_TABLE, DESK, COUNTER, CUPBOARD, SHELF, NIGHTSTAND, DRAWER, BED, LAMP, CLOCK,
		PICTURE, BLINDS, CROCKERY;

		/** The name in a spots file ({@code side_table}). */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The kind named {@code id} in a file, or null. */
		@Nullable
		public static Kind of(String id) {
			for (Kind kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			return null;
		}
	}

	/** The template position of each of its blocks. */
	public List<BlockPos> positions() {
		return blocks.stream().map(c -> pos.offset(c.at())).toList();
	}

	/** This spot with another wood (a style's swap, {@link BlueprintFurniture#styled}). */
	public FurnitureSpot withWood(@Nullable String newWood, List<Cell> newBlocks) {
		return new FurnitureSpot(kind, pos, facing, newWood, colour, newBlocks);
	}
}
