package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Scaffolding (ROADMAP 35.6). A builder reaches about four blocks and can't stand on a roof, so a raised arm, a spire's
 * tip or a dome's crown had nowhere to be built from and was skipped. Now, when a step has nowhere to stand within reach
 * ({@link BuilderWork#findStandingSpot} finds nothing), the builder puts up a column of vanilla scaffolding beside it,
 * from the ground or the nearest floor and only where the drawing leaves air or nothing, climbing it as it goes up,
 * and works from the top. Every scaffold is recorded in the {@link BuildSite} (saved), taken down when the stage is
 * done (the builder climbs up and takes it down from the top, back into its bag and so the chests) and, when the build
 * is cancelled or ends any other way, taken down at once into the supply chests. With none in the chests it's asked for
 * like any material (the requests board; a carpenter makes it from bamboo and string). Config {@code builderScaffolding}.
 *
 * <p>One instance per builder's work behaviour holds the column being put up (not saved: after a restart the builder
 * looks again, and a half-built column is found and built on).
 */
public final class Scaffolds {
	/** Config {@code builderScaffolding} (M35's gate): off, nothing is put up and such steps are skipped as before. */
	public static volatile boolean ENABLED = io.github.jcondedata.aliveworkplace.Expansions.on(io.github.jcondedata.aliveworkplace.Expansions.M35);
	/** The tallest column put up at once (blocks). */
	static final int MAX_HEIGHT = 32;
	/** How far a builder climbs a column in a tick (blocks). */
	private static final double CLIMB = 0.3;
	/** Ticks a builder walks to a column's foot before hopping there (and sooner with no path at all). */
	private static final int WALK_TICKS = 160;
	private static final int NO_PATH_TICKS = 40;
	/** How close (blocks, across) a builder must be to a column's middle to count as on it. */
	private static final double ON_COLUMN = 0.3;

	/** A column to put up: its cells bottom-up (each air now), and where the builder stands on its top. */
	record Column(List<BlockPos> cells, BlockPos stand) {
	}

	@Nullable
	private BlockPos jobTarget;
	@Nullable
	private BuildPlan.Stage jobStage;
	@Nullable
	private Column job;
	private int timer;
	private int walkTicks;
	private int retryWait;
	/** Where this builder was last put while climbing (gravity pulls it back a little each tick). */
	private double climbY = Double.NaN;

	// --- putting up --------------------------------------------------------------------------

	/**
	 * Called where the builder would give up on {@code target} (stuck, and nowhere to stand within reach, not even on a
	 * spot the drawing fills later): on the step's second go, at the end of its stage, when it would be skipped, plans a
	 * column to work from. The first time round the builder leaves it for later as it always has (what's built meanwhile
	 * often gives it somewhere to stand). True if there's a column to put up.
	 */
	boolean plan(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BlockPos target, double reach) {
		if (!ENABLED || !site.isRetrying() || site.stage() == BuildPlan.Stage.LANDSCAPE) {
			return false;
		}
		jobTarget = target;
		jobStage = site.stage();
		walkTicks = 0;
		job = column(level, plan, target, villager.blockPosition(), reach - 0.5);
		return job != null && next(level) != null;
	}

	/** Whether there's a column to put up (or finish) for {@code target} before the builder can work on it. */
	boolean needed(ServerLevel level, BuildSite site, BlockPos target) {
		if (job == null || !ENABLED || !target.equals(jobTarget) || site.stage() != jobStage) {
			job = null;
			return false;
		}
		if (next(level) == null) {
			job = null; // finished, or something's been put where it goes: the builder carries on as it would
			return false;
		}
		return true;
	}

	/** Scaffolds still to put up in the column being built (0 for none). */
	int toPlace(ServerLevel level) {
		if (job == null) {
			return 0;
		}
		int n = 0;
		for (BlockPos cell : job.cells()) {
			n += level.getBlockState(cell).is(Blocks.SCAFFOLDING) ? 0 : 1;
		}
		return n;
	}

	/** {@code missing} with the scaffolding this builder is short of (beyond its bag and {@code supplies}) added. */
	Map<Item, Integer> addMissing(ServerLevel level, Map<Item, Integer> missing, BuilderBag bag, List<BlockPos> supplies) {
		int need = toPlace(level) - bag.count(Items.SCAFFOLDING) - (int) Math.min(Integer.MAX_VALUE, SupplyContainers.count(level, supplies, Items.SCAFFOLDING));
		if (need <= 0 || missing.getOrDefault(Items.SCAFFOLDING, 0) >= need) {
			return missing;
		}
		Map<Item, Integer> out = new LinkedHashMap<>(missing);
		out.put(Items.SCAFFOLDING, need);
		return out;
	}

	/** The first cell not yet scaffolding, or null when the column is up (or can't be: something's there now). */
	@Nullable
	private BlockPos next(ServerLevel level) {
		if (job == null) {
			return null;
		}
		for (BlockPos cell : job.cells()) {
			BlockState there = level.getBlockState(cell);
			if (there.is(Blocks.SCAFFOLDING)) {
				continue;
			}
			return there.isAir() ? cell : null;
		}
		return null;
	}

	/** One tick of putting the column up: walk to its foot, then a scaffold under its feet at a time, stepping up on it. */
	void erect(ServerLevel level, Villager villager, BuildSite site, BuilderBag bag, boolean free) {
		BlockPos next = next(level);
		if (next == null || job == null) {
			return;
		}
		BlockPos bottom = foot(level, job.cells().get(0));
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SCAFFOLDING));
		if (!onColumn(villager, bottom, job.stand())) {
			approach(level, villager, bottom);
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(job.stand().above()));
		if (villager.getY() < next.getY() - 0.2) {
			climb(villager, next.getY()); // up the part already standing
			return;
		}
		put(villager, next.getY());
		if (timer > 0) {
			timer--;
			return;
		}
		if (!BuildBudget.take(level)) {
			return;
		}
		timer = BuilderLevels.delay(level, villager);
		if (!free) {
			if (bag.remove(Items.SCAFFOLDING, 1) < 1) {
				return;
			}
			MaterialLedger.used(Items.SCAFFOLDING, 1);
		}
		level.setBlock(next, scaffold(level, next), Block.UPDATE_ALL);
		SoundType sound = SoundType.SCAFFOLDING;
		level.playSound(null, next, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
		site.addScaffold(next);
		put(villager, next.getY() + 1); // stands on what it just put down
	}

	/** Scaffolding as it settles at {@code pos}: standing on what's below it. */
	private static BlockState scaffold(Level level, BlockPos pos) {
		return Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, ScaffoldingBlock.getDistance(level, pos))
			.setValue(ScaffoldingBlock.BOTTOM, false);
	}

	/**
	 * Where to put up a column for {@code target}: the place to stand within {@code maxEyeDistance} of it that takes the
	 * fewest new scaffolds (then the nearest {@code from}), on the ground, a floor or a column already standing. Every
	 * scaffold and the builder's feet and head go only where the world is air and the drawing leaves air or nothing.
	 */
	@Nullable
	static Column column(ServerLevel level, BuildPlan plan, BlockPos target, BlockPos from, double maxEyeDistance) {
		Column best = null;
		double bestScore = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = -5; dy <= 1; dy++) {
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					p.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
					Vec3 eye = new Vec3(p.getX() + 0.5, p.getY() + 1.62, p.getZ() + 0.5);
					if (eye.distanceToSqr(Vec3.atCenterOf(target)) > maxEyeDistance * maxEyeDistance) {
						continue;
					}
					AABB body = new AABB(p.getX() + 0.2, p.getY(), p.getZ() + 0.2, p.getX() + 0.8, p.getY() + 1.9, p.getZ() + 0.8);
					if (body.intersects(new AABB(target)) || !free(level, plan, p) || !free(level, plan, p.above())) {
						continue;
					}
					List<BlockPos> cells = new ArrayList<>();
					BlockPos cell = p.below();
					boolean based = false;
					while (cells.size() < MAX_HEIGHT && cell.getY() >= level.getMinBuildHeight()) {
						BlockState there = level.getBlockState(cell);
						if (there.is(Blocks.SCAFFOLDING) || there.isFaceSturdy(level, cell, Direction.UP)) {
							based = true;
							break;
						}
						if (!free(level, plan, cell)) {
							break;
						}
						cells.add(0, cell);
						cell = cell.below();
					}
					if (!based || cells.isEmpty()) {
						continue;
					}
					double score = cells.size() * 1000.0 + p.distSqr(from);
					if (score < bestScore) {
						bestScore = score;
						best = new Column(List.copyOf(cells), p.immutable());
					}
				}
			}
		}
		return best;
	}

	/** Air now, and the drawing leaves it air or nothing. */
	private static boolean free(ServerLevel level, BuildPlan plan, BlockPos pos) {
		return level.getBlockState(pos).isAir() && !plan.wantsBlockAt(pos);
	}

	// --- climbing ----------------------------------------------------------------------------

	/**
	 * The builder is to stand on {@code stand}, on top of one of this site's columns: climbs it (walking to its foot
	 * first). False once it's there, or if {@code stand} isn't on one of the site's columns.
	 */
	boolean climbTo(ServerLevel level, Villager villager, BuildSite site, BlockPos stand) {
		BlockPos below = stand.below();
		if (!level.getBlockState(below).is(Blocks.SCAFFOLDING) || !site.scaffolds().contains(below)) {
			return false;
		}
		BlockPos bottom = foot(level, below);
		if (!onColumn(villager, bottom, stand)) {
			approach(level, villager, bottom);
			return true;
		}
		if (Math.abs(villager.getY() - stand.getY()) < 0.05) {
			climbY = Double.NaN;
			return false;
		}
		climb(villager, stand.getY());
		return true;
	}

	/**
	 * The builder on one of the site's columns is going somewhere else: it climbs down rather than step off the top (a
	 * fall from a tall column would hurt it). True while it's on its way down.
	 */
	static boolean climbDown(Villager villager, BlockPos dest) {
		BlockPos cell = columnAt(villager);
		if (cell == null || dest.getX() == cell.getX() && dest.getZ() == cell.getZ()) {
			return false;
		}
		BlockPos bottom = foot(villager.level(), cell);
		if (villager.getY() <= bottom.getY() + 0.05) {
			return false; // at its foot: walks off
		}
		set(villager, cell.getX() + 0.5, Math.max(bottom.getY(), villager.getY() - CLIMB * 1.5), cell.getZ() + 0.5);
		return true;
	}

	/** Puts a builder on a column straight down at its foot (it stops work: night, a panic), so it never steps off a top. */
	static void toFoot(Villager villager) {
		BlockPos cell = columnAt(villager);
		if (cell != null) {
			BlockPos bottom = foot(villager.level(), cell);
			if (villager.getY() > bottom.getY() + 0.05) {
				set(villager, cell.getX() + 0.5, bottom.getY(), cell.getZ() + 0.5);
			}
		}
	}

	/** The scaffold the villager stands in or on, if it's on a column (in the middle of it), else null. */
	@Nullable
	private static BlockPos columnAt(Villager villager) {
		Level level = villager.level();
		BlockPos feet = villager.blockPosition();
		BlockPos cell = level.getBlockState(feet).is(Blocks.SCAFFOLDING) ? feet
			: level.getBlockState(feet.below()).is(Blocks.SCAFFOLDING) ? feet.below() : null;
		if (cell == null || Math.abs(villager.getX() - (cell.getX() + 0.5)) > ON_COLUMN || Math.abs(villager.getZ() - (cell.getZ() + 0.5)) > ON_COLUMN) {
			return null;
		}
		return cell;
	}

	/** The lowest scaffold of the stack {@code cell} is in (or {@code cell} itself, if nothing's under it). */
	private static BlockPos foot(Level level, BlockPos cell) {
		BlockPos p = cell;
		while (level.getBlockState(p.below()).is(Blocks.SCAFFOLDING) && p.getY() > level.getMinBuildHeight()) {
			p = p.below();
		}
		return p;
	}

	private static boolean onColumn(Villager villager, BlockPos bottom, BlockPos top) {
		return Math.abs(villager.getX() - (bottom.getX() + 0.5)) <= ON_COLUMN && Math.abs(villager.getZ() - (bottom.getZ() + 0.5)) <= ON_COLUMN
			&& villager.getY() >= bottom.getY() - 0.1 && villager.getY() <= top.getY() + 0.5;
	}

	/** Walks to the column's foot and steps into it; hops there if it can't get there. */
	private void approach(ServerLevel level, Villager villager, BlockPos bottom) {
		climbY = Double.NaN;
		double dx = villager.getX() - (bottom.getX() + 0.5);
		double dz = villager.getZ() - (bottom.getZ() + 0.5);
		boolean noPath = villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		if (dx * dx + dz * dz <= 1.6 * 1.6 && Math.abs(villager.getY() - bottom.getY()) <= 1.2) {
			set(villager, bottom.getX() + 0.5, bottom.getY(), bottom.getZ() + 0.5);
			walkTicks = 0;
			return;
		}
		if (++walkTicks > WALK_TICKS || noPath && walkTicks > NO_PATH_TICKS) {
			level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.5, villager.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
			set(villager, bottom.getX() + 0.5, bottom.getY(), bottom.getZ() + 0.5);
			level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.5, villager.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
			walkTicks = 0;
			return;
		}
		retryWait = io.github.jcondedata.aliveworkplace.work.Walker.requestWalk(villager, bottom, 0.6f, 1, retryWait);
	}

	/** A step up (or down) the column towards feet at {@code y}. */
	private void climb(Villager villager, double y) {
		double from = Double.isNaN(climbY) || Math.abs(climbY - villager.getY()) > 1 ? villager.getY() : Math.max(climbY, villager.getY());
		double to = y > from ? Math.min(y, from + CLIMB) : Math.max(y, villager.getY() - CLIMB);
		climbY = to;
		set(villager, Math.floor(villager.getX()) + 0.5, to, Math.floor(villager.getZ()) + 0.5);
	}

	/** Keeps the builder in the column's middle with its feet at {@code y}. */
	private void put(Villager villager, double y) {
		climbY = Double.NaN;
		set(villager, Math.floor(villager.getX()) + 0.5, y, Math.floor(villager.getZ()) + 0.5);
	}

	private static void set(Villager villager, double x, double y, double z) {
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.setDeltaMovement(Vec3.ZERO);
		villager.resetFallDistance();
		villager.moveTo(x, y, z, villager.getYRot(), villager.getXRot());
	}

	// --- taking down -------------------------------------------------------------------------

	/**
	 * One tick of taking the site's scaffolding down at the end of a stage: the top of the column the builder is on (or
	 * the nearest), from where it stands if in reach, else climbing up to it. Each scaffold goes into the bag (the chests
	 * get it with the bag's next trip, or when the build is finished). False when there's none left.
	 */
	boolean takeDown(ServerLevel level, Villager villager, BuildSite site, BuilderBag bag, BlockPos bench, boolean free) {
		for (BlockPos p : List.copyOf(site.scaffolds())) {
			if (!level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
				site.removeScaffold(p); // broken by someone else, or burnt
			}
		}
		if (site.scaffolds().isEmpty()) {
			climbY = Double.NaN;
			return false;
		}
		BlockPos top = null;
		double best = Double.MAX_VALUE;
		for (BlockPos p : site.scaffolds()) {
			if (site.scaffolds().contains(p.above())) {
				continue;
			}
			double across = Math.pow(villager.getX() - (p.getX() + 0.5), 2) + Math.pow(villager.getZ() - (p.getZ() + 0.5), 2);
			double score = across * 1000 - p.getY();
			if (score < best) {
				best = score;
				top = p;
			}
		}
		if (top == null) {
			return false;
		}
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SCAFFOLDING));
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(top));
		if (villager.getEyePosition().distanceTo(Vec3.atCenterOf(top)) > BuilderWork.REACH) {
			BlockPos bottom = foot(level, top);
			if (onColumn(villager, bottom, top.above())) {
				climb(villager, top.getY() + 1);
			} else {
				approach(level, villager, bottom);
			}
			return true;
		}
		if (timer > 0) {
			timer--;
			return true;
		}
		if (!BuildBudget.take(level)) {
			return true;
		}
		timer = BuilderLevels.delay(level, villager);
		remove(level, top);
		site.removeScaffold(top);
		if (!free) {
			MaterialLedger.gained(Items.SCAFFOLDING, 1);
			int rest = bag.addAll(Items.SCAFFOLDING, 1);
			if (rest > 0) {
				Builders.dropNear(level, bench, new ItemStack(Items.SCAFFOLDING, rest));
			}
		}
		climbY = Double.NaN;
		return true;
	}

	private static void remove(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.levelEvent(2001, pos, Block.getId(state)); // break particles and sound
	}

	/**
	 * Takes all of {@code site}'s scaffolding down at once, into the supply chests by its bench (else onto the ground
	 * there): the build was cancelled, or ended some other way, with scaffolds still up. Its builder, if on a column, is
	 * first put down at the column's foot.
	 */
	public static void dismantle(ServerLevel level, BuildSite site) {
		if (site.scaffolds().isEmpty()) {
			return;
		}
		if (site.builder() != null && level.getEntity(site.builder()) instanceof Villager builder) {
			toFoot(builder);
		}
		List<BlockPos> all = new ArrayList<>(site.scaffolds());
		all.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
		int taken = 0;
		for (BlockPos p : all) {
			if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
				remove(level, p);
				taken++;
			}
			site.removeScaffold(p);
		}
		if (taken == 0 || Rules.on(level, ModGameRules.FREE_MATERIALS)) {
			return;
		}
		MaterialLedger.gained(Items.SCAFFOLDING, taken);
		BlockPos at = site.bench() != null ? site.bench() : all.get(all.size() - 1);
		List<BlockPos> chests = site.bench() != null ? SupplyContainers.find(level, site.bench(), null) : List.of();
		ItemStack rest = SupplyContainers.insert(level, chests, new ItemStack(Items.SCAFFOLDING, taken));
		if (!rest.isEmpty()) {
			Builders.dropNear(level, at, rest);
		}
	}
}
