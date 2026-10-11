package io.github.jcondedata.aliveworkplace.herb;

import com.mojang.serialization.MapCodec;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One of the far herbs (ROADMAP 31.17): a plant like vanilla's flowers that never generates and never spreads (it has
 * no random tick and takes no bone meal); only a story arc's {@code herb_patch} plants it. It stands on the blocks of
 * its ground tag, {@code aliveworkplace:herb_ground/<herb>}, so datapacks can add ground. A shaded herb (the Glowcap)
 * also needs something above it that blocks the sky: a canopy, a roof.
 */
public class HerbBlock extends BushBlock {
	public static final MapCodec<HerbBlock> CODEC = simpleCodec(HerbBlock::new);
	private static final VoxelShape FLOWER = Block.box(5, 0, 5, 11, 10, 11);
	private static final VoxelShape MUSHROOM = Block.box(5, 0, 5, 11, 6, 11);

	private final boolean shaded;
	private TagKey<Block> ground;

	public HerbBlock(Properties properties) {
		this(properties, false);
	}

	public HerbBlock(Properties properties, boolean shaded) {
		super(properties);
		this.shaded = shaded;
	}

	@Override
	protected MapCodec<? extends HerbBlock> codec() {
		return CODEC;
	}

	/** Whether it grows only in shade (the Glowcap). */
	public boolean shaded() {
		return shaded;
	}

	/** The blocks it stands on: {@code aliveworkplace:herb_ground/<its id's path>}. */
	public TagKey<Block> ground() {
		if (ground == null) {
			ground = TagKey.create(Registries.BLOCK, AliveWorkplace.id("herb_ground/" + BuiltInRegistries.BLOCK.getKey(this).getPath()));
		}
		return ground;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(ground());
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (!super.canSurvive(state, level, pos)) {
			return false;
		}
		// In shade: something that blocks the sky stands above it (leaves count, as a dark forest's canopy).
		return !shaded || level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) > pos.getY() + 1;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Vec3 offset = state.getOffset(level, pos);
		return (shaded ? MUSHROOM : FLOWER).move(offset.x, offset.y, offset.z);
	}
}
