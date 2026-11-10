package io.github.jcondedata.aliveworkplace.compat.create;

import com.simibubi.create.content.contraptions.StructureTransform;
import io.github.jcondedata.aliveworkplace.build.TurnedBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Turns a Create block entity's data with a building the Mover or a builder puts up turned (B1137), the way Create turns
 * a block entity its own contraptions and schematics carry: {@link StructureTransform#apply(BlockEntity)}, which runs the
 * block entity's registered transformer or its {@code TransformableBlockEntity} code (a Mechanical Arm turns its targets,
 * {@code ArmInteractionPoint.transformPos}). Other block entities are left as they are.
 */
final class CreateTurns implements TurnedBlockEntities {
	@Override
	public void turn(BlockEntity be, Mirror mirror, Rotation rotation) {
		// no offset: the data's positions are relative to the block, which already stands where it goes
		new StructureTransform(BlockPos.ZERO, Direction.Axis.Y, rotation, mirror).apply(be);
	}
}
