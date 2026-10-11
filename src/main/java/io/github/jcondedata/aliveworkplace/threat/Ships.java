package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Lairs on the sea (ROADMAP 32.8): the drowned pirates' Pirate Ship, and any culture whose lair has a {@code waterline}
 * ({@link Culture.Sea}).
 * <ul>
 * <li><b>Where it anchors</b> ({@link #site}): on water at least {@code depth} deep under all of it, {@code near} to
 * {@code far} blocks from the hall (40 to 70 for the ship), with its bow (the template's z = 0 side) to the village
 * ({@link #facing}).</li>
 * <li><b>How it is set down</b> ({@link #place}): with the sea's top block at the template's {@code waterline}; what
 * the template leaves out (structure void: everything round the hull) stays sea, only its hold is dry.</li>
 * <li><b>Where its crew comes ashore</b> ({@link #beach}, {@link #wading}): the village's beach nearest the ship (dry
 * ground at the water's edge within the village), wading in from the water just off it.</li>
 * <li><b>How it goes</b> ({@link #sink}): when its captain falls the loot chest is put on a raft of logs at the
 * waterline between the ship and the village, and the ship burns and sinks: its own blocks go, top down, over
 * {@link #SINK_TICKS} ticks, with smoke and bubbles. A block that isn't the ship's any more (one a player put there,
 * or a ship's block a player changed) stays. What sank below the waterline turns to sea.</li>
 * </ul>
 * Saved per dimension as {@code aliveworkplace_ships}: where each village's ship lies and the ships sinking now, so a
 * restart mid-sinking carries on.
 */
public final class Ships {
	/** How long a ship takes to sink: 10 seconds. */
	public static final int SINK_TICKS = 200;
	/** How far above the hall a beach may be (looking down from there; the sea lies below a village, not over it). */
	static final int BEACH_ABOVE = 8;
	/** How far past the bow the raft with the loot chest floats. */
	static final int RAFT_OUT = 3;

	/** A ship set down: its structure, its origin and turn, and the y of the sea's top block it lies in. */
	public record Placed(ResourceLocation structure, BlockPos origin, Rotation rotation, int waterTop) {
		StructurePlaceSettings settings() {
			return new StructurePlaceSettings().setRotation(rotation);
		}
	}

	/** A ship sinking: which, and the game time it began. */
	public record Sinking(Placed ship, long began) {
	}

	private static final Map<ResourceLocation, Blueprint> BLUEPRINTS = new ConcurrentHashMap<>();

	public static void init() {
		Platform.get().onLevelTick(Ships::tick);
		Platform.get().onServerStarting(server -> BLUEPRINTS.clear());
	}

	// Where it anchors.

	/** The turn that points a template's bow (its z = 0 side, north) from {@code from} to {@code to}. */
	public static Rotation facing(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
		}
		return dz < 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
	}

	/**
	 * The sea's top block in the column of {@code at}: from {@code at} itself when it's water, up to the water's top;
	 * else the column's highest block (which is the sea's top if the column is open sea).
	 */
	public static BlockPos waterTop(ServerLevel level, BlockPos at) {
		BlockPos p = level.getFluidState(at).is(FluidTags.WATER) ? at : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, at).below();
		while (level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p.above()).is(FluidTags.WATER)) {
			p = p.above();
		}
		return p;
	}

	/**
	 * Where to stand in the column of {@code column}, looking down from {@code fromY}: on the first block that isn't air
	 * (on the water, if that's water); {@code fromY} itself if there's nothing within 40 blocks.
	 */
	public static BlockPos standAt(ServerLevel level, BlockPos column, int fromY) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(column.getX(), fromY, column.getZ());
		for (int i = 0; i < 40; i++) {
			if (!level.getBlockState(p).isAir() || !level.getFluidState(p).isEmpty()) {
				return p.above().immutable();
			}
			p.move(Direction.DOWN);
		}
		return new BlockPos(column.getX(), fromY, column.getZ());
	}

	/** How deep the water is from {@code top} down (at most 64). */
	public static int depth(ServerLevel level, BlockPos top) {
		int depth = 0;
		BlockPos p = top;
		while (depth < 64 && level.getFluidState(p).is(FluidTags.WATER)) {
			depth++;
			p = p.below();
		}
		return depth;
	}

	/**
	 * Somewhere on the sea for a ship of {@code template} by the village round {@code hall}: loaded, {@code sea.near()}
	 * to {@code sea.far()} blocks out, with water at least {@code sea.depth()} deep under every part of it as it would lie
	 * (bow to the village); the sea's top block in the middle, or null if there's nowhere.
	 */
	@Nullable
	public static BlockPos site(ServerLevel level, BlockPos hall, Culture.Sea sea, StructureTemplate template, RandomSource random) {
		for (int tries = 0; tries < 16; tries++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int distance = sea.near() + random.nextInt(sea.far() - sea.near() + 1);
			BlockPos column = hall.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
			if (!level.isLoaded(column) || !level.isLoaded(column.offset(16, 0, 16)) || !level.isLoaded(column.offset(-16, 0, -16))) {
				continue;
			}
			BlockPos top = waterTop(level, column);
			if (Math.abs(top.getY() - hall.getY()) > 32 || !fits(level, top, hall, sea, template)) {
				continue;
			}
			if (!VillageHalls.nearest(level, top).map(other -> other.equals(hall) || other.distSqr(top) > (double) sea.near() * sea.near()).orElse(true)) {
				continue; // another village's water
			}
			return top;
		}
		return null;
	}

	/** Whether a ship of {@code template} anchored with its middle at {@code top} (the sea's top block) lies on deep enough water. */
	public static boolean fits(ServerLevel level, BlockPos top, BlockPos hall, Culture.Sea sea, StructureTemplate template) {
		if (depth(level, top) < sea.depth()) {
			return false;
		}
		BoundingBox box = template.getBoundingBox(new StructurePlaceSettings().setRotation(facing(top, hall)), origin(template, top, facing(top, hall), sea));
		for (int x = box.minX(); x <= box.maxX(); x += 2) {
			for (int z = box.minZ(); z <= box.maxZ(); z += 2) {
				BlockPos column = new BlockPos(x, top.getY(), z);
				if (!level.isLoaded(column) || !level.getFluidState(column).is(FluidTags.WATER) || waterTop(level, column).getY() != top.getY()
					|| depth(level, column) < sea.depth()) {
					return false;
				}
			}
		}
		return true;
	}

	/** Where a ship of {@code template} turned {@code rotation} goes for its middle to lie at {@code top} on the waterline. */
	static BlockPos origin(StructureTemplate template, BlockPos top, Rotation rotation, Culture.Sea sea) {
		var size = template.getSize(rotation);
		return template.getZeroPositionWithTransform(top.offset(-size.getX() / 2, -sea.waterline(), -size.getZ() / 2), Mirror.NONE, rotation);
	}

	// Setting it down.

	/**
	 * Sets the ship {@code structure} down at anchor with its middle on the sea at {@code at} (the water's top in that
	 * column is found), its bow to {@code hall}, and remembers it for the village round {@code hall}.
	 */
	public static Placed place(ServerLevel level, BlockPos hall, BlockPos at, ResourceLocation structure, StructureTemplate template, Culture.Sea sea) {
		BlockPos top = waterTop(level, at);
		Rotation rotation = facing(top, hall);
		BlockPos origin = origin(template, top, rotation, sea);
		Placed ship = new Placed(structure, origin, rotation, top.getY());
		template.placeInWorld(level, origin, origin, ship.settings(), level.getRandom(), Block.UPDATE_CLIENTS);
		Data.get(level).put(hall.immutable(), ship);
		return ship;
	}

	/** The ship lying by the village round {@code hall}, if it has one (and it isn't sinking). */
	public static Optional<Placed> of(ServerLevel level, BlockPos hall) {
		return Optional.ofNullable(Data.get(level).ships.get(hall));
	}

	/** The ships sinking now in the dimension. */
	public static List<Sinking> sinking(ServerLevel level) {
		return List.copyOf(Data.get(level).sinking);
	}

	/** The blocks of {@code ship}'s blueprint, where they are in the world, and what each should be there now. */
	public static Map<BlockPos, BlockState> blocks(ServerLevel level, Placed ship) {
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		Blueprint blueprint = blueprint(level, ship.structure());
		if (blueprint == null) {
			return out;
		}
		StructurePlaceSettings settings = ship.settings();
		for (Blueprint.Entry e : blueprint.blocks()) {
			out.put(ship.origin().offset(StructureTemplate.calculateRelativePosition(settings, e.pos())), e.state().mirror(Mirror.NONE).rotate(ship.rotation()));
		}
		return out;
	}

	@Nullable
	private static Blueprint blueprint(ServerLevel level, ResourceLocation structure) {
		Blueprint known = BLUEPRINTS.get(structure);
		if (known != null) {
			return known;
		}
		StructureTemplate template = level.getStructureManager().get(structure).orElse(null);
		if (template == null) {
			return null;
		}
		Blueprint blueprint = Blueprint.fromTemplate(structure, template, Lookup.lookup(BuiltInRegistries.BLOCK));
		BLUEPRINTS.put(structure, blueprint);
		return blueprint;
	}

	// The crew coming ashore.

	/**
	 * The village's beach nearest {@code from} (the ship): the open, dry ground at the water's edge within the village
	 * round {@code hall} (from {@link #BEACH_ABOVE} blocks above the hall to 24 below it) that is closest to it; where to
	 * stand on it, or null if the village has no shore.
	 */
	@Nullable
	public static BlockPos beach(ServerLevel level, BlockPos hall, BlockPos from) {
		int r = VillageHalls.RADIUS;
		BlockPos best = null;
		double bestFar = Double.MAX_VALUE;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				BlockPos column = hall.offset(dx, 0, dz);
				double far = horizontal(column, from);
				if (far >= bestFar || !level.isLoaded(column)) {
					continue;
				}
				BlockPos stand = standAt(level, column, hall.getY() + BEACH_ABOVE);
				if (stand.getY() < hall.getY() - 24 || !level.getFluidState(stand.below()).isEmpty() || level.getBlockState(stand.below()).isAir()
					|| !level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()) {
					continue;
				}
				for (Direction side : Direction.Plane.HORIZONTAL) {
					BlockPos next = stand.below().relative(side);
					if (level.isLoaded(next) && level.getFluidState(next).is(FluidTags.WATER)) {
						best = stand;
						bestFar = far;
						break;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Where the crew wades in to {@code beach}: on the water just off it, up to three blocks out towards {@code from} (the
	 * ship); the beach itself if there's no water that way.
	 */
	public static BlockPos wading(ServerLevel level, BlockPos beach, BlockPos from) {
		double far = Math.max(1, horizontal(beach, from));
		double ux = (from.getX() - beach.getX()) / far;
		double uz = (from.getZ() - beach.getZ()) / far;
		BlockPos wade = beach;
		for (int out = 1; out <= 3; out++) {
			BlockPos column = beach.offset((int) Math.round(ux * out), -1, (int) Math.round(uz * out));
			if (!level.isLoaded(column) || !level.getFluidState(column).is(FluidTags.WATER) || !level.getBlockState(column.above()).isAir()) {
				break;
			}
			wade = column.above();
		}
		return wade;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// Sinking.

	/**
	 * The ship by the village round {@code hall} burns and sinks: its loot chest goes onto a raft of logs at the
	 * waterline between it and the village (when the chest is still there), and its blocks go, top down, over
	 * {@link #SINK_TICKS} ticks. Nothing happens if the village has no ship.
	 */
	/**
	 * The ship by the village round {@code hall} weighs anchor and sails off (its lair left the village in peace, 32.21):
	 * its blocks go as a sinking ship's do, but nothing burns and no raft is left. Nothing happens if the village has no ship.
	 */
	public static void sailAway(ServerLevel level, BlockPos hall) {
		Data data = Data.get(level);
		Placed ship = data.ships.remove(hall);
		if (ship == null) {
			return;
		}
		data.sinking.add(new Sinking(ship, level.getGameTime()));
		data.setDirty();
		level.playSound(null, middle(level, ship), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 2f, 0.8f);
	}

	public static void sink(ServerLevel level, BlockPos hall) {
		Data data = Data.get(level);
		Placed ship = data.ships.remove(hall);
		if (ship == null) {
			return;
		}
		raft(level, hall, ship);
		data.sinking.add(new Sinking(ship, level.getGameTime()));
		data.setDirty();
		BlockPos middle = middle(level, ship);
		level.playSound(null, middle, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2f, 0.7f);
		AliveWorkplace.LOG.info("The ship {} at {} burns and sinks", ship.structure(), ship.origin());
	}

	/** The middle of the ship's box, at the waterline. */
	static BlockPos middle(ServerLevel level, Placed ship) {
		StructureTemplate template = level.getStructureManager().get(ship.structure()).orElse(null);
		if (template == null) {
			return new BlockPos(ship.origin().getX(), ship.waterTop(), ship.origin().getZ());
		}
		BoundingBox box = template.getBoundingBox(ship.settings(), ship.origin());
		return new BlockPos(box.getCenter().getX(), ship.waterTop(), box.getCenter().getZ());
	}

	/**
	 * Moves the ship's loot chest (its first chest still standing) onto a 3 x 3 raft of logs on the water, {@link #RAFT_OUT}
	 * blocks past the bow (or past a side, if the bow's water is taken); where the chest went, or null if it was gone.
	 */
	@Nullable
	static BlockPos raft(ServerLevel level, BlockPos hall, Placed ship) {
		ChestBlockEntity chest = null;
		BlockPos from = null;
		for (Map.Entry<BlockPos, BlockState> e : blocks(level, ship).entrySet()) {
			if (e.getValue().getBlock() instanceof ChestBlock && level.getBlockEntity(e.getKey()) instanceof ChestBlockEntity found) {
				chest = found;
				from = e.getKey();
				break;
			}
		}
		if (chest == null) {
			return null;
		}
		StructureTemplate template = level.getStructureManager().get(ship.structure()).orElse(null);
		BoundingBox box = template.getBoundingBox(ship.settings(), ship.origin());
		BlockPos middle = middle(level, ship);
		int half = Math.max(box.getXSpan(), box.getZSpan()) / 2;
		int across = Math.min(box.getXSpan(), box.getZSpan()) / 2;
		Direction bow = ship.rotation().rotate(Direction.NORTH);
		List<BlockPos> spots = new ArrayList<>();
		for (int out = 0; out < 6; out++) {
			spots.add(middle.relative(bow, half + RAFT_OUT + out));
		}
		for (Direction side : List.of(bow.getClockWise(), bow.getCounterClockWise(), bow.getOpposite())) {
			int reach = side.getAxis() == bow.getAxis() ? half : across;
			spots.add(middle.relative(side, reach + RAFT_OUT));
		}
		BlockPos at = spots.get(0);
		for (BlockPos spot : spots) {
			if (raftFits(level, spot)) {
				at = spot;
				break;
			}
		}
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, bow.getClockWise().getAxis());
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(at.offset(dx, 0, dz), log, Block.UPDATE_ALL);
			}
		}
		BlockPos to = at.above();
		level.setBlock(to, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, bow.getOpposite()), Block.UPDATE_ALL);
		if (level.getBlockEntity(to) instanceof ChestBlockEntity raft) {
			if (chest.getLootTable() != null) {
				raft.setLootTable(chest.getLootTable(), chest.getLootTableSeed());
				chest.setLootTable(null);
			} else {
				for (int i = 0; i < chest.getContainerSize(); i++) {
					raft.setItem(i, chest.getItem(i).copy());
				}
			}
			raft.setChanged();
		}
		chest.clearContent();
		level.setBlock(from, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		return to;
	}

	private static boolean raftFits(ServerLevel level, BlockPos at) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p = at.offset(dx, 0, dz);
				if (!level.isLoaded(p) || !level.getFluidState(p).is(FluidTags.WATER) || !level.getBlockState(p.above()).isAir()) {
					return false;
				}
			}
		}
		return true;
	}

	/** Every tick: the ships sinking take their next layers down. */
	static void tick(ServerLevel level) {
		Data data = Data.get(level);
		if (data.sinking.isEmpty()) {
			return;
		}
		for (Sinking sinking : List.copyOf(data.sinking)) {
			if (!level.isLoaded(sinking.ship().origin())) {
				continue; // (it goes on when its ground is loaded again)
			}
			if (advance(level, sinking)) {
				data.sinking.remove(sinking);
				data.setDirty();
			}
		}
	}

	/** Takes {@code sinking}'s ship down as far as the time since it began says; true once it's all gone. */
	static boolean advance(ServerLevel level, Sinking sinking) {
		Placed ship = sinking.ship();
		Map<BlockPos, BlockState> blocks = blocks(level, ship);
		if (blocks.isEmpty()) {
			return true;
		}
		int bottom = Integer.MAX_VALUE;
		int top = Integer.MIN_VALUE;
		for (BlockPos p : blocks.keySet()) {
			bottom = Math.min(bottom, p.getY());
			top = Math.max(top, p.getY());
		}
		long elapsed = level.getGameTime() - sinking.began();
		boolean done = elapsed >= SINK_TICKS;
		int layers = done ? top - bottom + 1 : (int) Math.ceil((top - bottom + 1) * (double) Math.max(0, elapsed) / SINK_TICKS);
		int cutoff = top - layers; // everything above it goes
		RandomSource random = level.getRandom();
		int shown = 0;
		for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
			BlockPos p = e.getKey();
			if (p.getY() <= cutoff || !level.isLoaded(p)) {
				continue;
			}
			BlockState now = level.getBlockState(p);
			boolean below = p.getY() <= ship.waterTop();
			if (e.getValue().isAir()) {
				if (below && now.isAir()) {
					level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); // the hold floods
				}
				continue;
			}
			if (!MaterialRules.matches(now, e.getValue())) {
				continue; // not the ship's any more: it stays
			}
			if (level.getBlockEntity(p) instanceof Container container) {
				container.clearContent(); // (the gunports and the barrels: nothing spills)
			}
			level.setBlock(p, below ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			if (shown < 6 && random.nextInt(4) == 0) {
				shown++;
				if (below) {
					level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.05);
				} else {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.02);
					level.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
				}
			}
		}
		BlockPos middle = middle(level, ship);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, middle.getX() + 0.5, Math.max(cutoff, ship.waterTop()) + 1.0, middle.getZ() + 0.5, 3, 3, 1, 3, 0.02);
		level.sendParticles(ParticleTypes.BUBBLE, middle.getX() + 0.5, ship.waterTop() + 0.8, middle.getZ() + 0.5, 12, 4, 0.2, 4, 0.1);
		if (elapsed % 20 == 0) {
			level.playSound(null, middle, done ? SoundEvents.GENERIC_SPLASH : SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 2f, 0.8f);
		}
		return done;
	}

	/** Forgets every ship and every sinking (tests). */
	public static void forget(ServerLevel level) {
		Data data = Data.get(level);
		data.ships.clear();
		data.sinking.clear();
		data.setDirty();
	}

	/** The ships of one dimension: where each village's lies, and those sinking now. */
	public static final class Data extends SavedData {
		public static final String NAME = "aliveworkplace_ships";
		final Map<BlockPos, Placed> ships = new LinkedHashMap<>();
		final List<Sinking> sinking = new ArrayList<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		void put(BlockPos hall, Placed ship) {
			ships.put(hall, ship);
			setDirty();
		}

		private static CompoundTag write(Placed ship) {
			CompoundTag c = new CompoundTag();
			c.putString("structure", ship.structure().toString());
			c.putLong("origin", ship.origin().asLong());
			c.putString("rotation", ship.rotation().name());
			c.putInt("water", ship.waterTop());
			return c;
		}

		@Nullable
		private static Placed readShip(CompoundTag c) {
			ResourceLocation structure = ResourceLocation.tryParse(Nbt.getString(c, "structure"));
			if (structure == null) {
				return null;
			}
			Rotation rotation = Rotation.NONE;
			for (Rotation r : Rotation.values()) {
				if (r.name().equals(Nbt.getString(c, "rotation"))) {
					rotation = r;
				}
			}
			return new Placed(structure, BlockPos.of(Nbt.getLong(c, "origin")), rotation, Nbt.getInt(c, "water"));
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			ships.forEach((hall, ship) -> {
				CompoundTag c = write(ship);
				c.putLong("hall", hall.asLong());
				list.add(c);
			});
			tag.put("ships", list);
			ListTag going = new ListTag();
			for (Sinking s : sinking) {
				CompoundTag c = write(s.ship());
				c.putLong("began", s.began());
				going.add(c);
			}
			tag.put("sinking", going);
			return tag;
		}

		static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			data.read(tag);
			return data;
		}

		/** Replaces what this holds with what {@code tag} holds (a load). */
		public void read(CompoundTag tag) {
			ships.clear();
			sinking.clear();
			ListTag list = Nbt.getList(tag, "ships", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag c = Nbt.compoundAt(list, i);
				Placed ship = readShip(c);
				if (ship != null) {
					ships.put(BlockPos.of(Nbt.getLong(c, "hall")), ship);
				}
			}
			ListTag going = Nbt.getList(tag, "sinking", Tag.TAG_COMPOUND);
			for (int i = 0; i < going.size(); i++) {
				CompoundTag c = Nbt.compoundAt(going, i);
				Placed ship = readShip(c);
				if (ship != null) {
					sinking.add(new Sinking(ship, Nbt.getLong(c, "began")));
				}
			}
			setDirty();
		}

		/** Saves this and reads it back, as a restart does (tests). */
		public void roundTrip(HolderLookup.Provider registries) {
			read(save(new CompoundTag(), registries));
		}
	}

	private Ships() {
	}
}
