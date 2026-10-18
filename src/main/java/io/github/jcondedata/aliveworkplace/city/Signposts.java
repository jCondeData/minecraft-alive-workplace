package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Signpost decoration's boards say something (36.4). When a builder finishes a Signpost (any style), each of its
 * wall signs gets words, waxed so a passer-by can't edit them, the way the caravan road milestones are written
 * ({@link CaravanRoads#signText}): the board facing a direction names the nearest other village whose hall lies that
 * way (within {@link #VILLAGE_RANGE} blocks: "To", its name, "N blocks"); with none that way, the nearest finished
 * build that way (its display name and distance); with nothing at all, the village's own name and "Welcome". The hall's
 * dawn round writes them again when what they name changed (a village renamed, a new neighbour, a build pulled down).
 */
public final class Signposts {
	/** How far a board looks for another village's hall, or a finished build. */
	public static final int VILLAGE_RANGE = 1000;
	/** How far round a finished signpost's origin its boards are looked for. */
	private static final int REACH = 4;
	private static final int HEIGHT = 6;

	/** The day each hall's signposts were last looked at (not saved: the first round after a load looks again). */
	private static final Map<VillageHallBlockEntity, Long> LOOKED = new WeakHashMap<>();

	/** Whether {@code structure} is the Signpost, in any style. */
	public static boolean isSignpost(ResourceLocation structure) {
		return BlueprintStyles.base(structure).equals(StarterBlueprints.SIGNPOST.id());
	}

	/** A build was finished at {@code placement}: a Signpost's boards are written. Returns how many boards were written. */
	public static int finished(ServerLevel level, ResourceLocation structure, BlueprintData.Placement placement) {
		return isSignpost(structure) ? write(level, placement.origin()) : 0;
	}

	/**
	 * The hall's round, once a day: every finished signpost of the village round {@code hall} (loaded) gets its boards
	 * written again where what they should say changed. Returns how many boards changed.
	 */
	public static int round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		long today = Chronicle.day(level);
		synchronized (LOOKED) {
			Long last = LOOKED.get(entity);
			if (last != null && last == today) {
				return 0;
			}
			LOOKED.put(entity, today);
		}
		int changed = 0;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			if (isSignpost(f.structure()) && level.isLoaded(f.placement().origin())) {
				changed += write(level, f.placement().origin());
			}
		}
		return changed;
	}

	/** Forgets which halls looked today (tests run several "dawns" on one day). */
	public static void forget() {
		synchronized (LOOKED) {
			LOOKED.clear();
		}
	}

	/** The signpost's boards round {@code origin}: each wall sign within its few blocks. */
	public static List<BlockPos> boards(ServerLevel level, BlockPos origin) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-REACH, 0, -REACH), origin.offset(REACH, HEIGHT, REACH))) {
			if (level.getBlockState(p).getBlock() instanceof WallSignBlock && level.getBlockEntity(p) instanceof SignBlockEntity) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** Writes every board of the signpost at {@code origin} whose words changed; returns how many it wrote. */
	static int write(ServerLevel level, BlockPos origin) {
		List<BlockPos> boards = boards(level, origin);
		if (boards.isEmpty()) {
			return 0;
		}
		// The post: the middle of the boards (they stand round it).
		double cx = 0, cz = 0;
		for (BlockPos b : boards) {
			cx += b.getX() + 0.5;
			cz += b.getZ() + 0.5;
		}
		cx /= boards.size();
		cz /= boards.size();
		BlockPos post = BlockPos.containing(cx, origin.getY(), cz);
		BlockPos own = ownHall(level, post);
		int written = 0;
		for (BlockPos board : boards) {
			BlockState state = level.getBlockState(board);
			Direction facing = state.getValue(WallSignBlock.FACING);
			SignText text = text(level, post, own, facing, origin);
			SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(board);
			if (sign.isWaxed() && same(sign.getFrontText(), text)) {
				continue;
			}
			sign.setText(text, true);
			sign.setWaxed(true);
			sign.setChanged();
			level.sendBlockUpdated(board, state, state, 3);
			written++;
		}
		return written;
	}

	/**
	 * The village the post stands in: the nearest hall within {@link VillageHalls#RADIUS}, by its point-of-interest record
	 * or, for a hall whose record isn't filed yet (B186: placed inside a server task, vanilla files it a moment later),
	 * by the caravans' list, where the hall is really there. Its own village is never named on a board as somewhere to go.
	 */
	@Nullable
	static BlockPos ownHall(ServerLevel level, BlockPos post) {
		BlockPos best = VillageHalls.nearest(level, post).orElse(null);
		double bestSq = best != null ? best.distSqr(post) : (double) VillageHalls.RADIUS * VillageHalls.RADIUS;
		for (Caravans.Village v : Caravans.Data.get(level).villages()) {
			double d = v.hall().distSqr(post);
			if (d < bestSq && level.isLoaded(v.hall()) && level.getBlockEntity(v.hall()) instanceof VillageHallBlockEntity) {
				bestSq = d;
				best = v.hall();
			}
		}
		return best;
	}

	/** What the board facing {@code facing} at the post {@code post} says. */
	static SignText text(ServerLevel level, BlockPos post, @Nullable BlockPos ownHall, Direction facing, BlockPos origin) {
		// The nearest other village that way, within range.
		Caravans.Village village = null;
		double best = Double.MAX_VALUE;
		for (Caravans.Village v : Caravans.Data.get(level).villages()) {
			if (v.hall().equals(ownHall) || !that(post, v.hall(), facing)) {
				continue;
			}
			double d = horizontal(post, v.hall());
			if (d <= VILLAGE_RANGE && d < best) {
				best = d;
				village = v;
			}
		}
		if (village != null) {
			Component name = level.isLoaded(village.hall()) && level.getBlockEntity(village.hall()) instanceof VillageHallBlockEntity
				? VillageHalls.name(level, village.hall()) : village.name();
			return board(Component.translatable("sign.aliveworkplace.milestone.to"), name,
				Component.translatable("sign.aliveworkplace.milestone.distance", (int) Math.round(best)));
		}
		// Else the nearest finished build that way.
		BuildSiteManager.Finished build = null;
		best = Double.MAX_VALUE;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, post, VILLAGE_RANGE)) {
			BlockPos at = f.placement().origin();
			if (at.equals(origin) || isSignpost(f.structure()) || !that(post, at, facing)) {
				continue;
			}
			double d = horizontal(post, at);
			if (d < best) {
				best = d;
				build = f;
			}
		}
		if (build != null) {
			return board(Component.translatable("sign.aliveworkplace.milestone.to"), Blueprints.displayName(build.structure()),
				Component.translatable("sign.aliveworkplace.milestone.distance", (int) Math.round(best)));
		}
		Component home = ownHall != null ? VillageHalls.name(level, ownHall) : Component.empty();
		return board(Component.empty(), home, Component.translatable("sign.aliveworkplace.signpost.welcome"));
	}

	/**
	 * Whether {@code target} lies the way {@code facing} points from {@code post}: within 45° of it. A target exactly
	 * on the diagonal goes to the north or south board only (B186), so no place is named on two boards.
	 */
	static boolean that(BlockPos post, BlockPos target, Direction facing) {
		int dx = target.getX() - post.getX();
		int dz = target.getZ() - post.getZ();
		int along = dx * facing.getStepX() + dz * facing.getStepZ();
		int across = Math.abs(dx * facing.getStepZ()) + Math.abs(dz * facing.getStepX());
		return along > 0 && (along > across || along == across && facing.getAxis() == Direction.Axis.Z);
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static SignText board(Component first, Component second, Component third) {
		return new SignText().setMessage(0, first).setMessage(1, second.copy()).setMessage(2, third).setMessage(3, Component.empty());
	}

	/** Whether a sign already says {@code text} (by what a player reads). */
	private static boolean same(SignText now, SignText text) {
		for (int i = 0; i < 4; i++) {
			if (!now.getMessage(i, false).getString().equals(text.getMessage(i, false).getString())) {
				return false;
			}
		}
		return true;
	}

	private Signposts() {
	}
}
