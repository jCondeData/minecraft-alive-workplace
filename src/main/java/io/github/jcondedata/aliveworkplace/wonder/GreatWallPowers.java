package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.wonder.powers.RaidBarrierPower;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The Great Wall's barrier (ROADMAP 35.16), {@code raid_barrier}: while a finished, standing Wonder with it guards a
 * village ({@link Wonders#power}: damaged, or Wonders off, it is down), no raid gathers behind it. Seen from the hall the
 * Wonder covers an arc of the compass ({@link #arcs}: from its footprint's corners), widened by the power's margin either
 * side; {@code guard/VillageRaids} picks its gathering points' bearings outside it ({@link #bearing}), for our night
 * raids and the bandits' alike, and a siege's ladders never go up against it ({@link #shields}: {@code threat/Ladders}).
 * The rams never break its Great Gate: they only go for the village's own gate builds ({@code threat/Sieges.isDefence}).
 *
 * <p>Its Great Gate shuts at nightfall and opens in the morning with the village's gates ({@code guard/Gates}, the
 * hall's round: {@link #shutGates}), and stays shut through a siege. The Wonder's fence gates are worked out once per
 * site; nothing new is saved.
 */
public final class GreatWallPowers {
	/** A bearing from the hall the Wall covers: from {@code from} turning by {@code width} (radians, as {@code Math.atan2(dz, dx)}). */
	public record Arc(double from, double width) {
		/** Whether {@code angle} lies within it. */
		public boolean covers(double angle) {
			return width >= TAU || norm(angle - from) <= width;
		}
	}

	private static final double TAU = Math.PI * 2;
	/** Each site's footprint as placed (its corners' box), worked out once. */
	private static final Map<UUID, BoundingBox> FOOTPRINTS = new ConcurrentHashMap<>();
	/** Each site's fence gates as placed (its Great Gate), worked out once. */
	private static final Map<UUID, List<BlockPos>> GATES = new ConcurrentHashMap<>();

	/** Forgets what was worked out (the files were read again, or a test moved a site). */
	public static void forget() {
		FOOTPRINTS.clear();
		GATES.clear();
	}

	/** The box the placed {@code site} covers, over every stage's parts; empty when it isn't placed or a drawing is missing. */
	public static Optional<BoundingBox> footprint(ServerLevel level, WonderSite site) {
		BoundingBox known = FOOTPRINTS.get(site.id());
		if (known != null) {
			return Optional.of(known);
		}
		Wonder wonder = Wonders.loaded().get(site.wonder());
		if (wonder == null || site.placement().isEmpty()) {
			return Optional.empty();
		}
		BlueprintData.Placement at = site.placement().get();
		BoundingBox box = null;
		for (Wonder.Stage stage : wonder.stages()) {
			for (Wonder.Part part : stage.parts()) {
				Optional<Blueprint> blueprint = BlueprintLibrary.get(level, part.part());
				if (blueprint.isEmpty()) {
					return Optional.empty();
				}
				BlockPos low = part.offset();
				BlockPos high = low.offset(blueprint.get().size()).offset(-1, -1, -1);
				for (BlockPos corner : new BlockPos[] {low, high, new BlockPos(low.getX(), low.getY(), high.getZ()), new BlockPos(high.getX(), low.getY(), low.getZ())}) {
					BlockPos world = at.origin().offset(StructureTemplate.transform(corner, at.mirror(), at.rotation(), BlockPos.ZERO));
					box = box == null ? new BoundingBox(world) : box.encapsulate(world);
				}
			}
		}
		if (box == null) {
			return Optional.empty();
		}
		FOOTPRINTS.put(site.id(), box);
		return Optional.of(box);
	}

	/**
	 * The arcs the village round {@code hall} is walled on: one for each finished, standing Wonder of it with
	 * {@code raid_barrier}, from the bearings of its footprint's corners as seen from the hall and the power's margin
	 * either side. None while Wonders are off, or a Wonder whose footprint takes in the hall itself.
	 */
	public static List<Arc> arcs(ServerLevel level, BlockPos hall) {
		List<Arc> out = new ArrayList<>();
		for (Wonders.Granted g : Wonders.powers(level, hall, RaidBarrierPower.TYPE)) {
			if (!g.home() || !(g.power() instanceof RaidBarrierPower barrier)) {
				continue;
			}
			footprint(level, g.site()).flatMap(box -> arc(hall, box, Math.toRadians(barrier.marginDegrees()))).ifPresent(out::add);
		}
		return out;
	}

	/** The arc {@code box} covers seen from {@code hall}, widened by {@code margin} either side; empty if the hall is inside it. */
	public static Optional<Arc> arc(BlockPos hall, BoundingBox box, double margin) {
		if (hall.getX() >= box.minX() && hall.getX() <= box.maxX() && hall.getZ() >= box.minZ() && hall.getZ() <= box.maxZ()) {
			return Optional.empty();
		}
		double cx = hall.getX() + 0.5;
		double cz = hall.getZ() + 0.5;
		double[] corners = new double[4];
		int i = 0;
		for (double x : new double[] {box.minX(), box.maxX() + 1}) {
			for (double z : new double[] {box.minZ(), box.maxZ() + 1}) {
				corners[i++] = norm(Math.atan2(z - cz, x - cx));
			}
		}
		java.util.Arrays.sort(corners);
		// the arc is the circle less its widest gap between the corners' bearings
		double gap = corners[0] + TAU - corners[3];
		double from = corners[0];
		double width = corners[3] - corners[0];
		for (int k = 1; k < 4; k++) {
			double g = corners[k] - corners[k - 1];
			if (g > gap) {
				gap = g;
				from = corners[k];
				width = TAU - g;
			}
		}
		double wide = Math.min(TAU, width + 2 * margin);
		return Optional.of(new Arc(norm(from - margin), wide));
	}

	/** Whether the bearing {@code angle} from the hall is behind one of its walls ({@link #arcs}). */
	public static boolean walled(List<Arc> arcs, double angle) {
		for (Arc arc : arcs) {
			if (arc.covers(angle)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A bearing for raiders gathering about {@code toward}, give or take {@code spread}, outside every arc in {@code arcs}:
	 * as {@code toward + (random * 2 - 1) * spread} with no walls (the same draw), else drawn evenly over the open part of
	 * that window; when the walls cover all of it, the open bearing nearest {@code toward}.
	 */
	public static double bearing(List<Arc> arcs, double toward, double spread, RandomSource random) {
		double r = random.nextDouble();
		double plain = toward + (r * 2 - 1) * spread;
		if (arcs.isEmpty()) {
			return plain;
		}
		// the open stretches of the window, in steps of a tenth of a degree
		double from = spread >= Math.PI ? toward - Math.PI : toward - spread;
		double width = spread >= Math.PI ? TAU : 2 * spread;
		int steps = Math.max(1, (int) Math.ceil(width / Math.toRadians(0.1)));
		List<Double> open = new ArrayList<>();
		for (int s = 0; s <= steps; s++) {
			double a = from + width * s / steps;
			if (!walled(arcs, a)) {
				open.add(a);
			}
		}
		if (!open.isEmpty()) {
			return open.get(Math.min(open.size() - 1, (int) (r * open.size())));
		}
		double best = Double.NaN;
		double bestOff = Double.MAX_VALUE;
		for (Arc arc : arcs) {
			for (double edge : new double[] {arc.from() - Math.toRadians(1), arc.from() + arc.width() + Math.toRadians(1)}) {
				double off = Math.abs(norm(edge - toward + Math.PI) - Math.PI);
				if (!walled(arcs, edge) && off < bestOff) {
					best = edge;
					bestOff = off;
				}
			}
		}
		return Double.isNaN(best) ? plain : best;
	}

	/**
	 * Whether {@code pos} is part of a finished, standing Wonder with {@code raid_barrier} of the village round
	 * {@code hall} (within its footprint): a siege's ladders never go up against it.
	 */
	public static boolean shields(ServerLevel level, BlockPos hall, BlockPos pos) {
		for (Wonders.Granted g : Wonders.powers(level, hall, RaidBarrierPower.TYPE)) {
			if (g.home() && footprint(level, g.site()).map(box -> box.inflatedBy(1).isInside(pos)).orElse(false)) {
				return true;
			}
		}
		return false;
	}

	/** The fence gates of the placed {@code site} as drawn (its Great Gate), worked out once. */
	public static List<BlockPos> gates(ServerLevel level, WonderSite site) {
		List<BlockPos> known = GATES.get(site.id());
		if (known != null) {
			return known;
		}
		List<BlockPos> out = new ArrayList<>();
		List<WonderUpkeep.Block> blocks = WonderUpkeep.blocks(level, site);
		if (blocks.isEmpty()) {
			return List.of(); // its drawing isn't loaded: looked for again next round
		}
		for (WonderUpkeep.Block b : blocks) {
			if (b.state().getBlock() instanceof FenceGateBlock) {
				out.add(b.pos().immutable());
			}
		}
		List<BlockPos> gates = List.copyOf(out);
		GATES.put(site.id(), gates);
		return gates;
	}

	/**
	 * Shuts ({@code shut}) or opens the Great Gate of each finished Wonder with {@code raid_barrier} of the village round
	 * {@code hall} (damaged or not: a gate is a gate), the fence gates standing loaded. Returns how many moved.
	 */
	public static int shutGates(ServerLevel level, BlockPos hall, boolean shut) {
		if (!Wonders.ENABLED) {
			return 0;
		}
		int moved = 0;
		for (WonderSite site : WonderManager.get(level).in(hall)) {
			Wonder wonder = Wonders.loaded().get(site.wonder());
			if (!site.done() || wonder == null || wonder.powers().stream().noneMatch(p -> p.type().equals(RaidBarrierPower.TYPE))) {
				continue;
			}
			BlockPos first = null;
			for (BlockPos pos : gates(level, site)) {
				if (!level.isLoaded(pos)) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN) == shut) {
					level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, !shut), 10);
					moved++;
					first = first == null ? pos : first;
				}
			}
			if (first != null) {
				level.playSound(null, first, shut ? SoundEvents.FENCE_GATE_CLOSE : SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1f, 0.9f);
			}
		}
		return moved;
	}

	static double norm(double a) {
		double r = a % TAU;
		return r < 0 ? r + TAU : r;
	}

	private GreatWallPowers() {
	}
}
