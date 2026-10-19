package io.github.jcondedata.aliveworkplace.grave;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Where a village's graves rest (B222): the Undertaker moves each grave that lies about the village (a street, a field, a
 * doorstep) to the village's Graveyard build, in rows inside its wall, and revives from there as from anywhere. A village
 * without a Graveyard gets a small plot near the hall: three rows of four, outdoors, on the first open ground round the
 * hall. Nothing is saved for this: the rows are worked out from the Graveyard's placement (or the ground round the hall)
 * each time, so old saves' scattered graves are moved the same way.
 */
public final class Graveyards {
	/** Whether Undertakers move graves to the graveyard ({@code gravesToGraveyard}, on). Off: graves stay where they fall. */
	public static boolean ENABLED = true;
	/** The Graveyard blueprint's size (13 × 11 × 13, {@code StarterBlueprints.GRAVEYARD}). */
	static final Vec3i SIZE = new Vec3i(13, 11, 13);
	/** The hall plot: rows of this many graves, this many rows, a grave every other block. */
	static final int PLOT_ROW = 4, PLOT_ROWS = 3, PLOT_STEP = 2;
	/** How near and how far from the hall the plot may lie. */
	static final int PLOT_MIN = 6, PLOT_MAX = 20;
	/** The Graveyard's grave slots, in its own coordinates (y 0, the yard's ground; z 0 is the gate side), in the order filled. */
	static final List<BlockPos> YARD_SLOTS = yardSlots();

	private static List<BlockPos> yardSlots() {
		List<BlockPos> out = new ArrayList<>();
		int[] row = {1, 2, 3, 4, 8, 9, 10, 11}; // either side of the way in (x 5 to 7)
		for (int z : new int[] {6, 1}) {
			for (int x : row) {
				out.add(new BlockPos(x, 0, z));
			}
		}
		for (int z = 2; z <= 5; z++) { // along the walls and the way in, beside the old headstones
			for (int x : new int[] {1, 4, 8, 11}) {
				out.add(new BlockPos(x, 0, z));
			}
		}
		return List.copyOf(out);
	}

	/** Where the graves of the village round {@code pos} rest: its Graveyards (nearest the hall first), then the hall plot. */
	public record Resting(List<BoundingBox> yards, List<BlockPos> slots) {
		public boolean holds(BlockPos grave) {
			return slots.contains(grave) || yards.stream().anyMatch(b -> b.isInside(grave));
		}
	}

	/** Whether {@code id} is a Graveyard (any tier or style). */
	public static boolean isGraveyard(ResourceLocation id) {
		ResourceLocation base = BlueprintStyles.parse(id).map(BlueprintStyles.Styled::base).orElse(id);
		String path = base.getPath();
		String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("_\\d+$", "");
		return name.equals("graveyard");
	}

	/**
	 * The resting places of the village {@code from} is in (its hall's, or round {@code from} without a hall): every slot
	 * of its finished Graveyards, and the hall plot's when there is a hall and no Graveyard.
	 */
	public static Resting resting(ServerLevel level, BlockPos from) {
		return resting(level, from, true);
	}

	/** {@link #resting(ServerLevel, BlockPos)}, without the hall plot's slots unless {@code withPlot} (finding it reads the ground). */
	public static Resting resting(ServerLevel level, BlockPos from, boolean withPlot) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, from);
		BlockPos centre = hall.orElse(from);
		List<BuildSiteManager.Finished> yards = new ArrayList<>();
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, centre, VillageHalls.RADIUS + 16)) {
			if (isGraveyard(f.structure())) {
				yards.add(f);
			}
		}
		yards.sort(Comparator.comparingDouble(f -> f.placement().origin().distSqr(centre)));
		List<BoundingBox> boxes = new ArrayList<>();
		List<BlockPos> slots = new ArrayList<>();
		for (BuildSiteManager.Finished f : yards) {
			boxes.add(BlueprintOutline.bounds(f.placement(), SIZE));
			for (BlockPos local : YARD_SLOTS) {
				slots.add(toWorld(f.placement(), local));
			}
		}
		if (withPlot && yards.isEmpty() && hall.isPresent()) {
			slots.addAll(plot(level, hall.get()));
		}
		return new Resting(boxes, slots);
	}

	static BlockPos toWorld(BlueprintData.Placement placement, BlockPos local) {
		return placement.origin().offset(StructureTemplate.transform(local, placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	/**
	 * The hall plot's slots: the first spot (nearest the hall, {@link #PLOT_MIN} to {@link #PLOT_MAX} blocks off) where
	 * {@link #PLOT_ROWS} rows of {@link #PLOT_ROW} graves fit on open ground under the sky, each slot free or a grave
	 * already. The same ground gives the same plot, so the graves already laid in it keep it. Empty if none fits.
	 */
	public static List<BlockPos> plot(ServerLevel level, BlockPos hall) {
		List<BlockPos> anchors = new ArrayList<>();
		for (int dx = -PLOT_MAX; dx <= PLOT_MAX; dx++) {
			for (int dz = -PLOT_MAX; dz <= PLOT_MAX; dz++) {
				int d = Math.max(Math.abs(dx), Math.abs(dz));
				if (d >= PLOT_MIN && d <= PLOT_MAX) {
					anchors.add(new BlockPos(hall.getX() + dx, hall.getY(), hall.getZ() + dz));
				}
			}
		}
		anchors.sort(Comparator.<BlockPos>comparingDouble(p -> p.distSqr(hall)).thenComparingLong(BlockPos::asLong));
		for (BlockPos anchor : anchors) {
			List<BlockPos> slots = new ArrayList<>();
			boolean fits = true;
			for (int row = 0; row < PLOT_ROWS && fits; row++) {
				for (int i = 0; i < PLOT_ROW && fits; i++) {
					BlockPos slot = ground(level, anchor.offset(i * PLOT_STEP, 0, row * PLOT_STEP));
					fits = slot != null;
					if (fits) {
						slots.add(slot);
					}
				}
			}
			if (fits) {
				return slots;
			}
		}
		return List.of();
	}

	/** The open ground in {@code column} within 4 blocks of its height: a grave already, or free, on solid ground, under the sky. */
	@Nullable
	private static BlockPos ground(ServerLevel level, BlockPos column) {
		for (int dy = 4; dy >= -4; dy--) {
			BlockPos p = column.above(dy);
			BlockState state = level.getBlockState(p);
			if (state.is(ModBlocks.GRAVE)) {
				return p;
			}
			if (free(level, p) && level.canSeeSky(p)) {
				return p;
			}
		}
		return null;
	}

	/** Whether a grave can be laid at {@code p}: nothing there (or grass), no water, solid ground below, room above. */
	static boolean free(ServerLevel level, BlockPos p) {
		BlockState state = level.getBlockState(p);
		return (state.isAir() || state.canBeReplaced()) && level.getFluidState(p).isEmpty()
			&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
			&& !level.getBlockState(p.below()).is(ModBlocks.GRAVE)
			&& (level.getBlockState(p.above()).isAir() || level.getBlockState(p.above()).canBeReplaced());
	}

	/** The next free slot of {@code resting}, in order, or null when they're all taken (or there's nowhere). */
	@Nullable
	public static BlockPos nextSlot(ServerLevel level, Resting resting) {
		for (BlockPos slot : resting.slots()) {
			if (level.isLoaded(slot) && free(level, slot)) {
				return slot;
			}
		}
		return null;
	}

	/** The graves round {@code table} within {@code radius} that don't rest in the village's graveyard yet, nearest first. */
	public static List<BlockPos> strays(ServerLevel level, List<BlockPos> graves, Resting resting) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos g : graves) {
			if (!resting.holds(g)) {
				out.add(g);
			}
		}
		return out;
	}

	/** The graves that rest in the village's graveyard (to be revived from there as from anywhere). */
	public static List<BlockPos> resting(ServerLevel level, Resting resting) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos slot : resting.slots()) {
			if (level.isLoaded(slot) && level.getBlockState(slot).is(ModBlocks.GRAVE)) {
				out.add(slot);
			}
		}
		return out;
	}

	/**
	 * Moves the grave at {@code from} to {@code to}, everything in it kept (the villager as they were: name, job, level,
	 * trades, partner; the epitaph; the day they died). The grave is never lost: it's laid at {@code to} before
	 * {@code from} is cleared. Returns whether it moved.
	 */
	public static boolean move(ServerLevel level, BlockPos from, BlockPos to) {
		BlockState state = level.getBlockState(from);
		BlockEntity old = level.getBlockEntity(from);
		if (!state.is(ModBlocks.GRAVE) || !(old instanceof GraveBlockEntity) || !free(level, to)) {
			return false;
		}
		CompoundTag tag = old.saveWithoutMetadata(level.registryAccess());
		level.setBlockAndUpdate(to, state);
		if (!(level.getBlockEntity(to) instanceof GraveBlockEntity laid)) {
			level.removeBlock(to, false);
			return false;
		}
		laid.loadWithComponents(tag, level.registryAccess());
		laid.setChanged();
		level.removeBlock(from, false);
		level.sendParticles(ParticleTypes.SOUL, from.getX() + 0.5, from.getY() + 0.6, from.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
		level.sendParticles(ParticleTypes.SOUL, to.getX() + 0.5, to.getY() + 0.6, to.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, to, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
		return true;
	}

	private Graveyards() {
	}
}
