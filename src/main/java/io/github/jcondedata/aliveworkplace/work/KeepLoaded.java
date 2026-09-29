package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps builds and quarries running while the player who ordered them is online but far away: the
 * chunks with the site, the workstation, the supply chests and the worker stay loaded (gamerule
 * {@code workplaceKeepWorkLoaded}). Nothing is kept loaded for players who are offline.
 */
public final class KeepLoaded {
	private static final int EVERY = 100;
	/** Tickets expire unless renewed, so a finished or cancelled job lets its chunks go by itself. */
	private static final TicketType<ChunkPos> WORK = TicketType.create("aliveworkplace_work", Comparator.comparingLong(ChunkPos::toLong), EVERY * 3);
	/** Ticket distance 2 makes the chunk entity-ticking, so the worker keeps moving in it. */
	private static final int DISTANCE = 2;
	/** Never keep more than this many chunks per job (a very spread-out job only gets its core). */
	private static final int MAX_CHUNKS_PER_JOB = 36;

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % EVERY == 0 && Rules.on(level, ModGameRules.KEEP_WORK_LOADED)) {
				for (ChunkPos chunk : chunksToKeep(level)) {
					level.getChunkSource().addRegionTicket(WORK, chunk, DISTANCE, chunk);
				}
			}
		});
	}

	/** The chunks this level should keep loaded right now. */
	public static Set<ChunkPos> chunksToKeep(ServerLevel level) {
		Set<ChunkPos> out = new HashSet<>();
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (site.builder() == null || site.isQueued() || site.isDone() || !online(level, site.owner())) {
				continue;
			}
			BoundingBox area = BlueprintLibrary.get(level, site.structure())
				.map(b -> BlueprintOutline.bounds(site.placement(), b.size())).orElse(new BoundingBox(site.placement().origin()));
			add(out, level, area, site.bench(), site.builder());
		}
		for (QuarrySite quarry : QuarrySiteManager.get(level).all()) {
			if (quarry.miner() == null || quarry.isDone() || !online(level, quarry.owner())) {
				continue;
			}
			add(out, level, quarry.box(), quarry.bench(), quarry.miner());
		}
		return out;
	}

	private static void add(Set<ChunkPos> out, ServerLevel level, BoundingBox area, @Nullable BlockPos bench, UUID worker) {
		// A new box: BoundingBox.encapsulate changes the box it's called on, and `area` may be a quarry's own box
		// (before 0.45.0 this grew every quarry of an online owner to take in the ground around the Miner's Bench).
		BoundingBox box = area;
		if (bench != null) {
			int r = SupplyContainers.RADIUS;
			box = new BoundingBox(Math.min(area.minX(), bench.getX() - r), area.minY(), Math.min(area.minZ(), bench.getZ() - r),
				Math.max(area.maxX(), bench.getX() + r), area.maxY(), Math.max(area.maxZ(), bench.getZ() + r));
		}
		Set<ChunkPos> job = new HashSet<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				job.add(new ChunkPos(cx, cz));
			}
		}
		Entity entity = level.getEntity(worker);
		if (entity != null) {
			job.add(entity.chunkPosition()); // wherever they wandered off to (their bed, a far chest)
		}
		if (job.size() > MAX_CHUNKS_PER_JOB) {
			BlockPos center = area.getCenter();
			job.removeIf(c -> Math.abs(c.x - (center.getX() >> 4)) > 2 || Math.abs(c.z - (center.getZ() >> 4)) > 2);
			if (entity != null) {
				job.add(entity.chunkPosition());
			}
		}
		out.addAll(job);
	}

	private static boolean online(ServerLevel level, UUID player) {
		return level.getServer().getPlayerList().getPlayer(player) != null;
	}

	private KeepLoaded() {
	}
}
