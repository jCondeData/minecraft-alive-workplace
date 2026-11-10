package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.work.Extension;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Turns a block entity's data along with the building it stands in (B1137). A block's state turns by itself
 * ({@link MaterialRules#transformed}), but some modded block entities keep positions or sides in their data, relative
 * to the block: Create's Mechanical Arm keeps its targets ({@code InteractionPoints}) as offsets from the arm. When the
 * Mover puts a building up turned, or a builder builds a blueprint turned, that data has to turn too, or the machine
 * aims at the wrong spots. A mod that knows its data fills this in ({@code compat/create}: Create's own transform, as its
 * contraptions use); without one the data goes in as it was, which is right for every vanilla block.
 */
public interface TurnedBlockEntities {
	Extension<TurnedBlockEntities> EXTENSION = new Extension<>("block data turned with a building");

	/**
	 * Turns {@code be}, whose data was just loaded as it was saved in the unturned building, by {@code mirror} then
	 * {@code rotation} (as {@link net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate#transform}).
	 */
	void turn(BlockEntity be, Mirror mirror, Rotation rotation);

	/** Turns {@code be}'s freshly loaded data with the building: nothing when the building isn't turned or nobody fills this in. */
	static void apply(BlockEntity be, Mirror mirror, Rotation rotation) {
		if (mirror == Mirror.NONE && rotation == Rotation.NONE) {
			return;
		}
		try {
			EXTENSION.run(t -> t.turn(be, mirror, rotation));
		} catch (RuntimeException e) {
			// odd data (an imported blueprint) never stops a build or a move: the block stands with its data unturned
			AliveWorkplace.LOG.warn("Alive Workplace: couldn't turn the data of {} at {}: {}", be.getType(), be.getBlockPos(), e.toString());
		}
	}

	/** The turn that undoes {@code rotation}. */
	static Rotation inverse(Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
			case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
			default -> rotation;
		};
	}
}
