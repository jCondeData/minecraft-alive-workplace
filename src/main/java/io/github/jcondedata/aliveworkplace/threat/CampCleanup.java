package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A broken camp goes (B209). Before, a lair broken up or gone left its tents, fire and chests standing for good. Now,
 * when a lair on land is broken up ({@link Lairs#breakUp}) or leaves ({@link Lairs#leave}), its camp is taken down:
 * {@link #DELAY} ticks after, its blocks go top down over {@link #CLEAR_TICKS} ticks, with smoke. Only the camp's own
 * blocks go, those still as the camp set them down (a block a player put there or changed stays), never its ground
 * layer; a chest's or barrel's items spill out as items. With {@link #RUINS} ({@code campRuins}) the camp stays as ruins
 * instead: its fire put out, nothing removed. Either way the camp is gone from the village's threats, the Defence page and
 * the scouts' reports at once.
 *
 * <p>Then its ground goes back to the land (B225, {@link CampGround}): the levelled pad, the trampled earth and gravel
 * and the dirt filling become what was there before the camp (recorded when it was made), or, for a camp made before
 * that, are blended into the land round it. Ruins keep their tents and the ground under them; the open ground round them
 * goes back too.
 *
 * <p>Where a camp's blocks are: the lair's record keeps only its middle, so the turn it was set down with is found by
 * matching its blueprint against the world there (the right turn matches nearly all of it, the others little). A camp
 * whose ground isn't loaded waits in the list (saved, so a restart mid-clearing carries on).
 *
 * <p>Camps from before this fix: the lair's record was dropped but its camp stood. A world saved before B209 lists every
 * village that ever broke up a lair as unswept ({@link Lairs.Data}); the hall's round then looks for bandit camps
 * {@link #SWEEP_FROM} to {@link #SWEEP_TO} blocks out (a lit or unlit campfire on top, with the camp's blueprint round
 * it) with no living foe of ours near and no running story or bounty using them, and clears them the same way. A village
 * is swept once all that ground was loaded on one round.
 */
public final class CampCleanup {
	/** Whether a broken camp is left as ruins (its fire out) instead of taken down ({@code campRuins}, off). */
	public static boolean RUINS = false;
	/** Ticks after the camp breaks before it starts coming down (the band's poof first). */
	public static final int DELAY = 40;
	/** How long a camp takes to come down, top to bottom. */
	public static final int CLEAR_TICKS = 100;
	/** The share of a camp's blocks (above its ground) that must still stand where its blueprint puts them to know its turn. */
	static final float KNOWN_MATCH = 0.25f;
	/** The same, for a camp found by the sweep of an old world (no record says a camp was there). */
	static final float SWEEP_MATCH = 0.5f;
	/** The ring the sweep of an old world looks in (tests narrow it). */
	public static int SWEEP_FROM = Lairs.NEAR - 16;
	public static int SWEEP_TO = Lairs.FAR + 16;

	/**
	 * A camp coming down: its structure, its middle in the world and that middle's spot in its blueprint, the game time
	 * it starts (or started) to come down, whether it is left as ruins instead, its turn once known (-1: not yet), and the
	 * ground it covered before it was set down (B225; null for a camp made before that: its pad is blended back instead).
	 */
	public record Clearing(ResourceLocation structure, BlockPos middle, BlockPos spot, long began, boolean ruins, int rotation, @Nullable CampGround ground) {
		Clearing turned(int rotation) {
			return new Clearing(structure, middle, spot, began, ruins, rotation, ground);
		}

		StructurePlaceSettings settings() {
			return new StructurePlaceSettings().setRotation(Rotation.values()[rotation]);
		}

		BlockPos origin() {
			return middle.subtract(StructureTemplate.calculateRelativePosition(settings(), spot));
		}

		CompoundTag write() {
			CompoundTag c = new CompoundTag();
			c.putString("structure", structure.toString());
			c.putLong("middle", middle.asLong());
			c.putLong("spot", spot.asLong());
			c.putLong("began", began);
			c.putBoolean("ruins", ruins);
			c.putInt("rotation", rotation);
			if (ground != null) {
				c.put("ground", ground.write());
			}
			return c;
		}

		@Nullable
		static Clearing read(CompoundTag c) {
			ResourceLocation structure = ResourceLocation.tryParse(Nbt.getString(c, "structure"));
			if (structure == null) {
				return null;
			}
			int rotation = Nbt.getInt(c, "rotation");
			return new Clearing(structure, BlockPos.of(Nbt.getLong(c, "middle")), BlockPos.of(Nbt.getLong(c, "spot")), Nbt.getLong(c, "began"),
				Nbt.getBoolean(c, "ruins"), rotation < 0 || rotation > 3 ? -1 : rotation,
				Nbt.has(c, "ground", Tag.TAG_COMPOUND) ? CampGround.read(Nbt.getCompound(c, "ground")) : null);
		}
	}

	public static void init() {
		Platform.get().onLevelTick(CampCleanup::tick);
	}

	// Breaking.

	/** The camp of {@code lair} is done with (broken up, or gone): it comes down, or stays as ruins with {@link #RUINS}. */
	static void broken(ServerLevel level, Lairs.Lair lair) {
		Optional<Culture.Lair> spec = Threats.get(lair.culture()).flatMap(Culture::lair);
		if (spec.flatMap(Culture.Lair::sea).isPresent()) {
			return; // a ship sinks or sails off by itself (32.8)
		}
		ResourceLocation structure = spec.map(Culture.Lair::structure).orElse(Lairs.BANDIT_CAMP);
		StructureTemplate template = level.getStructureManager().get(structure).orElse(null);
		if (template == null) {
			return;
		}
		BlockPos spot = structure.equals(Lairs.BANDIT_CAMP) ? Lairs.FIRE
			: new BlockPos(template.getSize().getX() / 2, spec.flatMap(Culture.Lair::captainAt).map(BlockPos::getY).orElse(1), template.getSize().getZ() / 2);
		CampGround ground = Lairs.Data.get(level).grounds.remove(lair.pos());
		queue(level, new Clearing(structure, lair.pos(), spot, level.getGameTime() + DELAY, RUINS, -1, ground));
	}

	static void queue(ServerLevel level, Clearing clearing) {
		Lairs.Data data = Lairs.Data.get(level);
		data.clearing.add(clearing);
		data.setDirty();
	}

	/** The camps coming down (or waiting to) in the dimension. */
	public static List<Clearing> clearing(ServerLevel level) {
		return List.copyOf(Lairs.Data.get(level).clearing);
	}

	/** Every tick: the camps whose time has come and whose ground is loaded come down a little more. */
	static void tick(ServerLevel level) {
		Lairs.Data data = Lairs.Data.get(level);
		if (data.clearing.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Clearing c : List.copyOf(data.clearing)) {
			if (now < c.began() || !loaded(level, c.middle())) {
				continue; // (it goes on when its ground is loaded again)
			}
			Clearing at = c;
			if (at.rotation() < 0) {
				int turn = at.ground() != null ? at.ground().rotation() : turn(level, at, KNOWN_MATCH); // (the ground's record knows it)
				if (turn < 0) {
					AliveWorkplace.LOG.info("The broken camp at {} ({}) is no longer there to take down", c.middle(), c.structure());
					data.clearing.remove(c);
					data.setDirty();
					continue;
				}
				at = at.turned(turn);
				data.clearing.set(data.clearing.indexOf(c), at);
				data.setDirty();
			}
			if (at.ruins()) {
				ruin(level, at);
				ground(level, at);
				data.clearing.remove(at);
				data.setDirty();
				AliveWorkplace.LOG.info("The broken camp at {} is left as ruins", at.middle());
			} else if (advance(level, at, now)) {
				data.clearing.remove(at);
				data.setDirty();
				AliveWorkplace.LOG.info("The broken camp at {} ({}) was taken down", at.middle(), at.structure());
			}
		}
	}

	/** Whether the ground a camp round {@code middle} could cover is loaded. */
	private static boolean loaded(ServerLevel level, BlockPos middle) {
		return level.isLoaded(middle) && level.isLoaded(middle.offset(16, 0, 16)) && level.isLoaded(middle.offset(-16, 0, -16))
			&& level.isLoaded(middle.offset(16, 0, -16)) && level.isLoaded(middle.offset(-16, 0, 16));
	}

	/** The blocks of the camp's blueprint above its ground layer (air left out). */
	private static List<Blueprint.Entry> standing(ServerLevel level, ResourceLocation structure) {
		Blueprint blueprint = Ships.blueprint(level, structure);
		return blueprint == null ? List.of() : blueprint.blocks().stream().filter(e -> e.pos().getY() >= 1 && !e.state().isAir()).toList();
	}

	/** The turn the camp was set down with: the one whose blueprint matches the world best, if at least {@code least} of it does; else -1. */
	static int turn(ServerLevel level, Clearing c, float least) {
		List<Blueprint.Entry> blocks = standing(level, c.structure());
		if (blocks.isEmpty()) {
			return -1;
		}
		int best = -1;
		int bestCount = 0;
		for (int r = 0; r < 4; r++) {
			Clearing turned = c.turned(r);
			StructurePlaceSettings settings = turned.settings();
			BlockPos origin = turned.origin();
			Rotation rotation = Rotation.values()[r];
			int count = 0;
			for (Blueprint.Entry e : blocks) {
				BlockPos p = origin.offset(StructureTemplate.calculateRelativePosition(settings, e.pos()));
				if (MaterialRules.matches(level.getBlockState(p), e.state().mirror(Mirror.NONE).rotate(rotation))) {
					count++;
				}
			}
			if (count > bestCount) {
				best = r;
				bestCount = count;
			}
		}
		return bestCount >= Math.max(1, (int) Math.ceil(blocks.size() * least)) ? best : -1;
	}

	/** Takes the camp down as far as the time since it began says, top down; true once it is all gone. */
	static boolean advance(ServerLevel level, Clearing c, long now) {
		List<Blueprint.Entry> blocks = standing(level, c.structure());
		if (blocks.isEmpty()) {
			return true;
		}
		int top = blocks.stream().mapToInt(e -> e.pos().getY()).max().orElse(1);
		long elapsed = now - c.began();
		boolean done = elapsed >= CLEAR_TICKS;
		int layers = done ? top : (int) Math.ceil(top * (double) Math.max(1, elapsed) / CLEAR_TICKS);
		int cutoff = top - layers; // every blueprint layer above it goes
		StructurePlaceSettings settings = c.settings();
		BlockPos origin = c.origin();
		Rotation rotation = Rotation.values()[c.rotation()];
		int shown = 0;
		for (Blueprint.Entry e : blocks) {
			if (e.pos().getY() <= cutoff) {
				continue;
			}
			BlockPos p = origin.offset(StructureTemplate.calculateRelativePosition(settings, e.pos()));
			if (!level.isLoaded(p) || !MaterialRules.matches(level.getBlockState(p), e.state().mirror(Mirror.NONE).rotate(rotation))) {
				continue; // not the camp's any more: it stays
			}
			if (level.getBlockEntity(p) instanceof Container container) {
				Containers.dropContents(level, p, container); // (its loot spills out: nothing is lost)
			}
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			if (shown < 6 && level.getRandom().nextInt(4) == 0) {
				shown++;
				level.sendParticles(ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
			}
		}
		if (elapsed % 20 == 0) {
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.middle().getX() + 0.5, c.middle().getY() + 1.0, c.middle().getZ() + 0.5, 3, 2, 0.5, 2, 0.01);
			level.playSound(null, c.middle(), SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
		}
		if (done) {
			ground(level, c); // (in the same tick the clearing ends: a reload before it finds the ground still to do)
		}
		return done;
	}

	/**
	 * The camp's ground goes back to the land (B225): to what it was before the camp, from its record, or, for a camp made
	 * before that, blended into the land round it. A ruined camp's tents and fire stay, and the ground under them.
	 */
	static void ground(ServerLevel level, Clearing c) {
		StructureTemplate template = level.getStructureManager().get(c.structure()).orElse(null);
		Blueprint blueprint = Ships.blueprint(level, c.structure());
		if (template == null || blueprint == null) {
			return;
		}
		StructurePlaceSettings settings = c.settings();
		BlockPos origin = c.origin();
		Rotation rotation = Rotation.values()[c.rotation()];
		java.util.Map<BlockPos, BlockState> camp = new java.util.HashMap<>();
		java.util.Set<Long> keep = new java.util.HashSet<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			if (e.state().isAir()) {
				continue;
			}
			BlockPos p = origin.offset(StructureTemplate.calculateRelativePosition(settings, e.pos()));
			camp.put(p, e.state().mirror(Mirror.NONE).rotate(rotation));
			if (c.ruins() && e.pos().getY() >= 1) {
				keep.add(CampGround.column(p)); // (the ruins stand there)
			}
		}
		if (c.ground() != null) {
			c.ground().restore(level, camp, keep);
		} else {
			CampGround.blend(level, template.getBoundingBox(settings, origin), camp, keep);
		}
	}

	/** Leaves the camp as ruins: its fires put out, all else as it is. */
	static void ruin(ServerLevel level, Clearing c) {
		StructurePlaceSettings settings = c.settings();
		BlockPos origin = c.origin();
		for (Blueprint.Entry e : standing(level, c.structure())) {
			BlockPos p = origin.offset(StructureTemplate.calculateRelativePosition(settings, e.pos()));
			BlockState now = level.getBlockState(p);
			if (now.getBlock() instanceof CampfireBlock && now.getValue(CampfireBlock.LIT)) {
				level.setBlock(p, now.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
			}
		}
	}

	// Old worlds (camps left standing before B209).

	/**
	 * The hall's round: in a world saved before B209, the bandit camps that a village broke up then (their record gone,
	 * their tents still standing) are found and taken down (or left as ruins) like any broken camp now.
	 */
	public static void sweep(ServerLevel level, BlockPos hall) {
		Lairs.Data data = Lairs.Data.get(level);
		if (!data.unswept.contains(hall)) {
			return;
		}
		StructureTemplate template = level.getStructureManager().get(Lairs.BANDIT_CAMP).orElse(null);
		if (template == null) {
			data.unswept.remove(hall);
			data.setDirty();
			return;
		}
		boolean all = true;
		int found = 0;
		for (int dx = -SWEEP_TO; dx <= SWEEP_TO; dx++) {
			for (int dz = -SWEEP_TO; dz <= SWEEP_TO; dz++) {
				int far = dx * dx + dz * dz;
				if (far > SWEEP_TO * SWEEP_TO || far < SWEEP_FROM * SWEEP_FROM) {
					continue;
				}
				BlockPos column = hall.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					all = false;
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
				BlockState fire = level.getBlockState(top);
				if (!(fire.getBlock() instanceof CampfireBlock) || RUINS && !fire.getValue(CampfireBlock.LIT) || inUse(level, top)) {
					continue;
				}
				if (!loaded(level, top) || !level.areEntitiesLoaded(ChunkPos.asLong(top)) || !level.isPositionEntityTicking(top)) {
					all = false; // (its band may not be loaded yet: look again next round)
					continue;
				}
				if (guarded(level, top)) {
					all = false; // (someone's still: look again later)
					continue;
				}
				Clearing c = new Clearing(Lairs.BANDIT_CAMP, top.immutable(), Lairs.FIRE, level.getGameTime(), RUINS, -1, null);
				int turn = turn(level, c, SWEEP_MATCH);
				if (turn >= 0) {
					queue(level, c.turned(turn));
					found++;
				}
			}
		}
		if (found > 0) {
			AliveWorkplace.LOG.info("Found {} bandit camp(s) left standing near the village hall at {} from before B209: clearing them", found, hall);
		}
		if (all) {
			data.unswept.remove(hall);
			data.setDirty();
		}
	}

	/** A standing lair, a camp already coming down, a running story's camp or a bounty's hideout is there. */
	private static boolean inUse(ServerLevel level, BlockPos at) {
		for (Lairs.Lair lair : Lairs.Data.get(level).lairs.values()) {
			if (lair.pos().distSqr(at) <= 24 * 24) {
				return true;
			}
		}
		for (Clearing c : Lairs.Data.get(level).clearing) {
			if (c.middle().distSqr(at) <= 8 * 8) {
				return true;
			}
		}
		for (var running : io.github.jcondedata.aliveworkplace.story.BanditKingStory.running(level)) {
			for (var place : running.state().places) {
				if (near(place.spot, at) || near(place.origin, at)) {
					return true;
				}
			}
		}
		for (var bounty : io.github.jcondedata.aliveworkplace.bounty.Bounties.Data.get(level).bounties.values()) {
			if (bounty.hideout != null && (near(bounty.hideout.spot, at) || near(bounty.hideout.origin, at))) {
				return true;
			}
		}
		return false;
	}

	private static boolean near(@Nullable BlockPos p, BlockPos at) {
		return p != null && p.distSqr(at) <= 24 * 24;
	}

	/** A living foe of ours (a lair's, a bounty's, a story's) is near: the camp is someone's still. */
	private static boolean guarded(ServerLevel level, BlockPos at) {
		return !level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(32, 16, 32),
			m -> m.isAlive() && m instanceof Enemy && m.getTags().stream().anyMatch(t -> t.startsWith(AliveWorkplace.MOD_ID + "_"))).isEmpty();
	}

	private CampCleanup() {
	}
}
