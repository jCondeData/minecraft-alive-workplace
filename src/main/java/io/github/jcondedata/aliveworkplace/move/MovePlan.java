package io.github.jcondedata.aliveworkplace.move;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildEntities;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A building's snapshot for moving it (ROADMAP 45.1): every block state in its box with each block entity's full data
 * (a chest's items, a sign's text, a banner's patterns, a modded block's own data, all by id, never by class), the
 * entities that belong to the building (item frames, paintings, armor stands, modded furniture entities: anything that
 * isn't alive, an item, a projectile or a vehicle), its front, and its residents with their beds and workstations.
 *
 * <p>Positions are kept relative to the box's lowest corner, unturned. {@link #placed} and {@link #place} put the plan up
 * at another lowest corner, turned by a {@link Rotation} about the building's own footprint: the turned footprint's
 * lowest corner is the corner given. Saved as NBT ({@link #save}, {@link #load}) with a {@link #VERSION} so a move's site
 * can keep it over a save.
 */
public final class MovePlan {
	public static final int VERSION = 1;

	/** One block of the building: where (relative), its state and its block entity's data (null: none). */
	public record Block(BlockPos rel, BlockState state, @Nullable CompoundTag data) {
	}

	/** One entity of the building: where (relative, the entity's own position) and its saved data (with its id, no UUID). */
	public record Thing(Vec3 rel, CompoundTag data) {
	}

	/** A block of the plan put at its new spot: the world position, the turned state and its block entity's data. */
	public record Placed(BlockPos pos, BlockState state, @Nullable CompoundTag data) {
	}

	@Nullable
	private final ResourceLocation structure;
	private final Vec3i size;
	private final Direction front;
	private final List<Block> blocks;
	private final List<Thing> things;
	private final List<BlockPos> beds;
	private final List<BlockPos> workstations;
	private final List<UUID> residents;

	private MovePlan(@Nullable ResourceLocation structure, Vec3i size, Direction front, List<Block> blocks, List<Thing> things,
					 List<BlockPos> beds, List<BlockPos> workstations, List<UUID> residents) {
		this.structure = structure;
		this.size = size;
		this.front = front;
		this.blocks = List.copyOf(blocks);
		this.things = List.copyOf(things);
		this.beds = List.copyOf(beds);
		this.workstations = List.copyOf(workstations);
		this.residents = List.copyOf(residents);
	}

	/** The blueprint the building was built from (null: a building with none, a player's own house). */
	@Nullable
	public ResourceLocation structure() {
		return structure;
	}

	/** The box's size, unturned. */
	public Vec3i size() {
		return size;
	}

	/** The way the building's front faces, unturned. */
	public Direction front() {
		return front;
	}

	/** The way the front faces once turned by {@code rotation}. */
	public Direction front(Rotation rotation) {
		return front.getAxis().isHorizontal() ? rotation.rotate(front) : front;
	}

	public List<Block> blocks() {
		return blocks;
	}

	public List<Thing> things() {
		return things;
	}

	/** The head halves of its beds, relative. */
	public List<BlockPos> beds() {
		return beds;
	}

	/** Its residents' workstations (job sites inside the box), relative. */
	public List<BlockPos> workstations() {
		return workstations;
	}

	/** The villagers whose bed or workstation is inside it. */
	public List<UUID> residents() {
		return residents;
	}

	// ---- capture

	/**
	 * The plan of a finished building the Village Hall knows (built from a blueprint by a builder or grown by the village's
	 * Steward), or empty if its blueprint is gone or its box holds nothing.
	 */
	public static Optional<MovePlan> capture(ServerLevel level, BuildSiteManager.Finished building) {
		Vec3i size = size(level, building.structure());
		if (size == null) {
			return Optional.empty();
		}
		BoundingBox box = BlueprintOutline.bounds(building.placement(), size);
		// the template's z=0 side is the front and faces north before the placement turns it (BlueprintItem)
		return capture(level, box, building.placement().rotation().rotate(Direction.NORTH), building.structure());
	}

	/** The finished building the Village Hall at {@code hall} knows that holds {@code pos}, captured; empty if none. */
	public static Optional<MovePlan> captureAt(ServerLevel level, BlockPos hall, BlockPos pos) {
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedInVillage(level, hall)) {
			Vec3i size = size(level, f.structure());
			if (size != null && BlueprintOutline.bounds(f.placement(), size).isInside(pos)) {
				return capture(level, f);
			}
		}
		return Optional.empty();
	}

	/**
	 * The plan of whatever stands in {@code box} (a building with no blueprint, a player's own house), its front facing
	 * {@code front}; empty if the box holds no block.
	 */
	public static Optional<MovePlan> capture(ServerLevel level, BoundingBox box, Direction front, @Nullable ResourceLocation structure) {
		BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
		List<Block> blocks = new ArrayList<>();
		List<BlockPos> beds = new ArrayList<>();
		List<BlockPos> workstations = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(min, new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			BlockPos rel = pos.subtract(min).immutable();
			BlockEntity be = level.getBlockEntity(pos);
			CompoundTag data = be == null ? null : be.saveWithoutMetadata(level.registryAccess());
			blocks.add(new Block(rel, state, data));
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				beds.add(rel);
			}
		}
		if (blocks.isEmpty()) {
			return Optional.empty();
		}
		AABB area = AABB.of(box);
		List<Thing> things = new ArrayList<>();
		for (Entity entity : thingsIn(level, box)) {
			things.add(thing(entity, min));
		}
		List<UUID> residents = new ArrayList<>();
		for (Villager villager : level.getEntitiesOfClass(Villager.class, area.inflate(128))) {
			boolean lives = inside(level, box, villager.getBrain().getMemory(MemoryModuleType.HOME));
			Optional<GlobalPos> job = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
			boolean works = inside(level, box, job);
			if (works) {
				BlockPos rel = job.get().pos().subtract(min);
				if (!workstations.contains(rel)) {
					workstations.add(rel);
				}
			}
			if (lives || works) {
				residents.add(villager.getUUID());
			}
		}
		Vec3i size = new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan());
		return Optional.of(new MovePlan(structure, size, front, blocks, things, beds, workstations, residents));
	}

	/**
	 * The things that belong to the building standing in {@code box}: every one {@link #movesWithBuilding} inside it, and
	 * (45.5) a frame or painting hanging on one of its blocks from outside, on an outer wall, which would fall off with it.
	 */
	public static List<Entity> thingsIn(ServerLevel level, BoundingBox box) {
		AABB area = AABB.of(box);
		List<Entity> out = new ArrayList<>();
		for (Entity entity : level.getEntities((Entity) null, area.inflate(1), MovePlan::movesWithBuilding)) {
			boolean hangsOnIt = entity instanceof net.minecraft.world.entity.decoration.HangingEntity hanging
				&& box.isInside(hanging.getPos().relative(hanging.getDirection().getOpposite()));
			if (hangsOnIt || area.contains(entity.position())) {
				out.add(entity);
			}
		}
		return out;
	}

	/** A thing as the plan keeps it: by id, without its UUID or riders, its position relative to {@code min}. */
	public static Thing thing(Entity entity, BlockPos min) {
		CompoundTag data = entity.saveWithoutId(new CompoundTag());
		data.putString("id", EntityType.getKey(entity.getType()).toString());
		data.remove("UUID");
		data.remove("Passengers");
		return new Thing(entity.position().subtract(min.getX(), min.getY(), min.getZ()), data);
	}

	private static boolean inside(ServerLevel level, BoundingBox box, Optional<GlobalPos> pos) {
		return pos.isPresent() && pos.get().dimension() == level.dimension() && box.isInside(pos.get().pos());
	}

	/** Whether an entity in the box is part of the building: decorations and furniture, never anything alive or loose. */
	public static boolean movesWithBuilding(Entity entity) {
		if (entity instanceof ArmorStand) {
			return entity.isAlive();
		}
		return entity.isAlive() && !(entity instanceof LivingEntity) && !(entity instanceof Player) && !(entity instanceof ItemEntity)
			&& !(entity instanceof ExperienceOrb) && !(entity instanceof Projectile) && !(entity instanceof AbstractMinecart)
			&& !(entity instanceof Boat) && !(entity instanceof PrimedTnt) && !(entity instanceof FallingBlockEntity)
			&& !(entity instanceof LightningBolt) && !entity.isPassenger();
	}

	/** The box a finished building stands in (its blueprint's size at its placement), or null if its blueprint is gone. */
	@Nullable
	public static BoundingBox box(ServerLevel level, BuildSiteManager.Finished building) {
		Vec3i size = size(level, building.structure());
		return size == null ? null : BlueprintOutline.bounds(building.placement(), size);
	}

	@Nullable
	private static Vec3i size(ServerLevel level, ResourceLocation id) {
		try {
			return level.getServer().getStructureManager().get(BlueprintStyles.base(id)).map(StructureTemplate::getSize).orElse(null);
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ---- turning and placing

	/** The box's size once turned by {@code rotation}. */
	public Vec3i size(Rotation rotation) {
		return quarter(rotation) ? new Vec3i(size.getZ(), size.getY(), size.getX()) : size;
	}

	private static boolean quarter(Rotation rotation) {
		return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
	}

	/** A relative block position turned by {@code rotation} within the footprint (lowest corner stays the lowest corner). */
	public BlockPos turn(BlockPos rel, Rotation rotation) {
		int x = rel.getX(), z = rel.getZ(), sx = size.getX(), sz = size.getZ();
		return switch (rotation) {
			case NONE -> rel;
			case CLOCKWISE_90 -> new BlockPos(sz - 1 - z, rel.getY(), x);
			case CLOCKWISE_180 -> new BlockPos(sx - 1 - x, rel.getY(), sz - 1 - z);
			case COUNTERCLOCKWISE_90 -> new BlockPos(z, rel.getY(), sx - 1 - x);
		};
	}

	/** A relative entity position turned the same way as {@link #turn(BlockPos, Rotation)} turns blocks. */
	public Vec3 turn(Vec3 rel, Rotation rotation) {
		double x = rel.x, z = rel.z, sx = size.getX(), sz = size.getZ();
		return switch (rotation) {
			case NONE -> rel;
			case CLOCKWISE_90 -> new Vec3(sz - z, rel.y, x);
			case CLOCKWISE_180 -> new Vec3(sx - x, rel.y, sz - z);
			case COUNTERCLOCKWISE_90 -> new Vec3(z, rel.y, sx - x);
		};
	}

	/** Every block of the plan at its new spot, lowest corner {@code min}, turned by {@code rotation}. */
	public List<Placed> placed(BlockPos min, Rotation rotation) {
		List<Placed> out = new ArrayList<>(blocks.size());
		for (Block b : blocks) {
			BlockState state = io.github.jcondedata.aliveworkplace.build.MaterialRules.transformed(b.state(), Mirror.NONE, rotation);
			out.add(new Placed(min.offset(turn(b.rel(), rotation)), state, b.data() == null ? null : b.data().copy()));
		}
		return out;
	}

	/** Where a relative position of the plan (a bed, a workstation) ends up at {@code min}, turned by {@code rotation}. */
	public BlockPos at(BlockPos rel, BlockPos min, Rotation rotation) {
		return min.offset(turn(rel, rotation));
	}

	/**
	 * Puts the whole plan up at once at {@code min}, turned by {@code rotation}: every block (no neighbour updates while
	 * it goes up, so a door's or a bed's halves and wall torches stand as they did), its block entities' data (items
	 * included: the move carries them, it doesn't copy them, so the old spot must be emptied by the caller) and then its
	 * entities. Returns how many entities couldn't be put back (nothing to hang on, an unknown id).
	 */
	public int place(ServerLevel level, BlockPos min, Rotation rotation) {
		for (Placed p : placed(min, rotation)) {
			level.setBlock(p.pos(), p.state(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
		}
		for (Placed p : placed(min, rotation)) {
			if (p.data() == null) {
				continue;
			}
			BlockEntity be = level.getBlockEntity(p.pos());
			if (be != null) {
				be.loadWithComponents(p.data(), level.registryAccess());
				be.setChanged();
				level.sendBlockUpdated(p.pos(), p.state(), p.state(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			}
		}
		return placeThings(level, min, rotation);
	}

	/** Puts the plan's entities (frames, armor stands, furniture) up at {@code min}; how many couldn't be (see {@link #place}). */
	public int placeThings(ServerLevel level, BlockPos min, Rotation rotation) {
		return placeThings(level, things, min, rotation);
	}

	/** Puts {@code list} (things of this plan, unturned, relative to its lowest corner) up at {@code min}; how many couldn't be. */
	public int placeThings(ServerLevel level, List<Thing> list, BlockPos min, Rotation rotation) {
		int left = 0;
		for (Thing t : list) {
			Vec3 pos = turn(t.rel(), rotation).add(min.getX(), min.getY(), min.getZ());
			Optional<Entity> made = BuildEntities.create(level, new BuildPlan.EntityStep(pos, t.data(), Items.AIR, rotation, Mirror.NONE));
			if (made.isEmpty()) {
				left++;
				continue;
			}
			level.addFreshEntity(made.get());
		}
		return left;
	}

	// ---- saving

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("version", VERSION);
		if (structure != null) {
			tag.putString("structure", structure.toString());
		}
		tag.putIntArray("size", new int[] {size.getX(), size.getY(), size.getZ()});
		tag.putString("front", front.getName());
		ListTag palette = new ListTag();
		List<BlockState> states = new ArrayList<>();
		ListTag list = new ListTag();
		for (Block b : blocks) {
			int index = states.indexOf(b.state());
			if (index < 0) {
				index = states.size();
				states.add(b.state());
				palette.add(NbtUtils.writeBlockState(b.state()));
			}
			CompoundTag entry = new CompoundTag();
			entry.putLong("pos", b.rel().asLong());
			entry.putInt("state", index);
			if (b.data() != null) {
				entry.put("data", b.data().copy());
			}
			list.add(entry);
		}
		tag.put("palette", palette);
		tag.put("blocks", list);
		ListTag thingList = new ListTag();
		for (Thing t : things) {
			CompoundTag entry = new CompoundTag();
			entry.putDouble("x", t.rel().x);
			entry.putDouble("y", t.rel().y);
			entry.putDouble("z", t.rel().z);
			entry.put("data", t.data().copy());
			thingList.add(entry);
		}
		tag.put("things", thingList);
		tag.putLongArray("beds", beds.stream().mapToLong(BlockPos::asLong).toArray());
		tag.putLongArray("workstations", workstations.stream().mapToLong(BlockPos::asLong).toArray());
		ListTag people = new ListTag();
		for (UUID id : residents) {
			people.add(StringTag.valueOf(id.toString()));
		}
		tag.put("residents", people);
		return tag;
	}

	/** A saved plan, or empty if it's from a newer version or broken. */
	public static Optional<MovePlan> load(CompoundTag tag) {
		int version = Nbt.getInt(tag, "version");
		int[] s = Nbt.getIntArray(tag, "size");
		if (version < 1 || version > VERSION || s.length != 3) {
			return Optional.empty();
		}
		ResourceLocation structure = Nbt.has(tag, "structure", Tag.TAG_STRING) ? ResourceLocation.tryParse(Nbt.getString(tag, "structure")) : null;
		Direction front = Direction.byName(Nbt.getString(tag, "front"));
		ListTag palette = Nbt.getList(tag, "palette", Tag.TAG_COMPOUND);
		List<BlockState> states = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			states.add(NbtUtils.readBlockState(Lookup.lookup(BuiltInRegistries.BLOCK), Nbt.compoundAt(palette, i)));
		}
		List<Block> blocks = new ArrayList<>();
		ListTag list = Nbt.getList(tag, "blocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = Nbt.compoundAt(list, i);
			if (!Nbt.has(entry, "state", Tag.TAG_INT) || !Nbt.has(entry, "pos", Tag.TAG_LONG)) {
				continue; // a damaged entry: not a block of the building (it would read as the palette's first state at 0, 0, 0)
			}
			int index = Nbt.getInt(entry, "state");
			if (index < 0 || index >= states.size()) {
				continue;
			}
			CompoundTag data = Nbt.has(entry, "data", Tag.TAG_COMPOUND) ? Nbt.getCompound(entry, "data") : null;
			blocks.add(new Block(BlockPos.of(Nbt.getLong(entry, "pos")), states.get(index), data));
		}
		List<Thing> things = new ArrayList<>();
		ListTag thingList = Nbt.getList(tag, "things", Tag.TAG_COMPOUND);
		for (int i = 0; i < thingList.size(); i++) {
			CompoundTag entry = Nbt.compoundAt(thingList, i);
			things.add(new Thing(new Vec3(Nbt.getDouble(entry, "x"), Nbt.getDouble(entry, "y"), Nbt.getDouble(entry, "z")), Nbt.getCompound(entry, "data")));
		}
		List<UUID> residents = new ArrayList<>();
		ListTag people = Nbt.getList(tag, "residents", Tag.TAG_STRING);
		for (int i = 0; i < people.size(); i++) {
			try {
				residents.add(UUID.fromString(Nbt.stringAt(people, i)));
			} catch (IllegalArgumentException ignored) {
				// a broken id: that resident is left out
			}
		}
		return Optional.of(new MovePlan(structure, new Vec3i(s[0], s[1], s[2]), front == null ? Direction.NORTH : front, blocks, things,
			positions(Nbt.getLongArray(tag, "beds")), positions(Nbt.getLongArray(tag, "workstations")), residents));
	}

	private static List<BlockPos> positions(long[] packed) {
		List<BlockPos> out = new ArrayList<>(packed.length);
		for (long p : packed) {
			out.add(BlockPos.of(p));
		}
		return out;
	}
}
