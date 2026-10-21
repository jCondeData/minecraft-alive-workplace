package io.github.jcondedata.aliveworkplace.furnish;

import java.util.List;
import java.util.Locale;
import java.util.Set;
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
public record FurnitureSpot(Kind kind, BlockPos pos, Direction facing, @Nullable String wood, @Nullable String colour, List<Cell> blocks,
							@Nullable Set<Support> support) {
	/** A spot whose support isn't known (a datapack's file without it): every piece may stand there, as before B260. */
	public FurnitureSpot(Kind kind, BlockPos pos, Direction facing, @Nullable String wood, @Nullable String colour, List<Cell> blocks) {
		this(kind, pos, facing, wood, colour, blocks, null);
	}

	/**
	 * What a spot stands on or against in its blueprint (B260): a solid block under it ({@code floor}), behind it
	 * ({@code wall}) or over it ({@code ceiling}), written by the generator. A piece that needs one ({@link
	 * FurnishingTable.Piece#needs}) goes only where the spot has it: a Beautify picture frame, which stands on a table,
	 * never takes a picture's place on a wall, where it would float.
	 */
	public enum Support {
		FLOOR, WALL, CEILING;

		/** The name in a file. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static Support of(String id) {
			for (Support s : values()) {
				if (s.id().equals(id)) {
					return s;
				}
			}
			return null;
		}
	}

	/** Whether a piece that needs {@code need} can stand here (always, when this spot's support isn't known). */
	public boolean supports(@Nullable Support need) {
		return need == null || support == null || support.contains(need);
	}

	/** A vanilla block of the spot: where it is (relative to the spot) and what it is. */
	public record Cell(BlockPos at, BlockState state) {
	}

	/** The kinds of furniture (the design note's table). */
	public enum Kind {
		CHAIR, STOOL, BENCH, COUCH, TABLE, SIDE_TABLE, DESK, COUNTER, CUPBOARD, SHELF, NIGHTSTAND, DRAWER, BED, LAMP, CLOCK,
		PICTURE, BLINDS, CROCKERY,
		/** Workplace decorations (37.5): a lattice on a wall, a pot hanging from a beam. */
		TRELLIS, HANGING_POT;

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
		return new FurnitureSpot(kind, pos, facing, newWood, colour, newBlocks, support);
	}
}
