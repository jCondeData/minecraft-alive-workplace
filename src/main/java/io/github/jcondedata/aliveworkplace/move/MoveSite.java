package io.github.jcondedata.aliveworkplace.move;

import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * One building on the move (ROADMAP 45.3): its {@link MovePlan} captured when the Mover started, where it stood and where
 * it goes, and where each of its blocks is now. Every block is in one of four places: still standing at the old spot,
 * packed in the Mover's crates (the site's saved record, with its block entity's data: a chest packed with its items
 * kept apart from every other), standing at the new spot, or lost (broken by someone before he got to it; whoever broke
 * it has it). The stages follow the Builder's: DISMANTLE (top-down, the Builder's CLEAR) and then FOUNDATION, STRUCTURE
 * and DECORATION (bottom-up) to DONE. Everything is saved, so a Mover picks up at any tick from what's here; each step
 * looks at the world first, so a step half done before a restart is never done twice. Canceled half way, the site
 * takes down what it put up at the new spot and puts everything back up where it was (RESTORE_DOWN, RESTORE_UP).
 */
public final class MoveSite {
	public enum Stage {
		DISMANTLE, FOUNDATION, STRUCTURE, DECORATION, DONE, RESTORE_DOWN, RESTORE_UP;

		public boolean rebuilding() {
			return this == FOUNDATION || this == STRUCTURE || this == DECORATION;
		}
	}

	static final byte OLD = 0;
	static final byte PACKED = 1;
	static final byte NEW = 2;
	static final byte LOST = 3;

	private final BlockPos hall;
	private final BlockPos from;
	private final MovePlan plan;
	private final BlockPos oldMin;
	private final BlockPos newMin;
	private final Rotation rotation;
	@Nullable
	private final UUID by;
	private final byte[] where;
	/** Each packed block entity's data as it was taken down (a chest's items as the village left them), by block index. */
	private final java.util.Map<Integer, CompoundTag> packedData = new java.util.HashMap<>();
	/** Who lives or works in it (45.4): their beds and workstations follow it ({@link MoveResidents}). */
	private final List<MoveResidents.Resident> residents = new ArrayList<>();
	private byte things;
	private Stage stage;
	private int skipped;
	private boolean canceled;
	@Nullable
	private String waiting;
	@Nullable
	private transient List<Integer> downOrder;
	@Nullable
	private transient List<Integer> upOrder;

	MoveSite(BlockPos hall, BlockPos from, MovePlan plan, BlockPos oldMin, BlockPos newMin, Rotation rotation, @Nullable UUID by,
			byte[] where, byte things, Stage stage, int skipped) {
		this.hall = hall.immutable();
		this.from = from.immutable();
		this.plan = plan;
		this.oldMin = oldMin.immutable();
		this.newMin = newMin.immutable();
		this.rotation = rotation;
		this.by = by;
		this.where = where;
		this.things = things;
		this.stage = stage;
		this.skipped = skipped;
	}

	static MoveSite start(BlockPos hall, BlockPos from, MovePlan plan, BlockPos oldMin, BlockPos newMin, Rotation rotation, @Nullable UUID by) {
		return new MoveSite(hall, from, plan, oldMin, newMin, rotation, by, new byte[plan.blocks().size()], OLD, Stage.DISMANTLE, 0);
	}

	public BlockPos hall() {
		return hall;
	}

	/** The building's placement origin before the move: the key the Hall and {@link PendingMoves} know it by. */
	public BlockPos from() {
		return from;
	}

	public MovePlan plan() {
		return plan;
	}

	public BlockPos oldMin() {
		return oldMin;
	}

	public BlockPos newMin() {
		return newMin;
	}

	public Rotation rotation() {
		return rotation;
	}

	@Nullable
	public UUID by() {
		return by;
	}

	public Stage stage() {
		return stage;
	}

	/** Blocks someone broke before the Mover got to them (they have them): not put up again. */
	public int skipped() {
		return skipped;
	}

	/** Why the Mover waits (a translation key), or null when he's working. */
	@Nullable
	public String waiting() {
		return waiting;
	}

	void setWaiting(@Nullable String key) {
		waiting = key;
	}

	/** Whether the move was canceled (it's being, or has been, put back). */
	public boolean canceled() {
		return canceled;
	}

	/** How many blocks are packed in the crates now. */
	public int packed() {
		int n = 0;
		for (byte b : where) {
			if (b == PACKED) {
				n++;
			}
		}
		return n;
	}

	/** How many blocks of the building there are. */
	public int total() {
		return where.length;
	}

	/** Packed blocks that hold items (a chest, a barrel...): each kept in its own crate with its own contents. */
	public List<MovePlan.Block> packedContainers() {
		List<MovePlan.Block> out = new ArrayList<>();
		for (int i = 0; i < where.length; i++) {
			MovePlan.Block b = plan.blocks().get(i);
			CompoundTag data = data(i);
			if (where[i] == PACKED && data != null && data.contains("Items")) {
				out.add(new MovePlan.Block(b.rel(), b.state(), data));
			}
		}
		return out;
	}

	/** Who lives or works in the building (ROADMAP 45.4), noted as the move started: their beds and workstations follow it. */
	public List<MoveResidents.Resident> residents() {
		return residents;
	}

	/** The box the building stood in before the move. */
	public BoundingBox oldBox() {
		net.minecraft.core.Vec3i size = plan.size(Rotation.NONE);
		return new BoundingBox(oldMin.getX(), oldMin.getY(), oldMin.getZ(), oldMin.getX() + size.getX() - 1, oldMin.getY() + size.getY() - 1,
			oldMin.getZ() + size.getZ() - 1);
	}

	/** The box it stands in at the new spot, turned. */
	public BoundingBox newBox() {
		net.minecraft.core.Vec3i size = plan.size(rotation);
		return new BoundingBox(newMin.getX(), newMin.getY(), newMin.getZ(), newMin.getX() + size.getX() - 1, newMin.getY() + size.getY() - 1,
			newMin.getZ() + size.getZ() - 1);
	}

	/** Where a position of the building (relative to its old lowest corner) is once the move is over: back where it was if called off. */
	public BlockPos at(BlockPos rel) {
		return canceled ? oldMin.offset(rel) : plan.at(rel, newMin, rotation);
	}

	/** Cancels the move: what was put up at the new spot comes down again and everything goes back up where it was. */
	void cancel() {
		if (stage == Stage.DONE || canceled) {
			return;
		}
		canceled = true;
		stage = Stage.RESTORE_DOWN;
	}

	// ---- work

	private BlockPos oldPos(int i) {
		return oldMin.offset(plan.blocks().get(i).rel());
	}

	private BlockPos newPos(int i) {
		return plan.at(plan.blocks().get(i).rel(), newMin, rotation);
	}

	private BlockState newState(int i) {
		return MaterialRules.transformed(plan.blocks().get(i).state(), net.minecraft.world.level.block.Mirror.NONE, rotation);
	}

	/** Which rebuilding stage block {@code i} goes up in. */
	Stage stageOf(int i) {
		MovePlan.Block b = plan.blocks().get(i);
		if (b.rel().getY() == 0) {
			return Stage.FOUNDATION;
		}
		return b.state().canOcclude() ? Stage.STRUCTURE : Stage.DECORATION;
	}

	private List<Integer> downOrder() {
		if (downOrder == null) {
			List<Integer> order = new ArrayList<>();
			for (int i = 0; i < where.length; i++) {
				order.add(i);
			}
			// top-down; at each height what hangs on others (torches, doors, beds) first
			order.sort(Comparator.<Integer>comparingInt(i -> -plan.blocks().get(i).rel().getY())
				.thenComparing(i -> plan.blocks().get(i).state().canOcclude()));
			downOrder = order;
		}
		return downOrder;
	}

	private List<Integer> upOrder() {
		if (upOrder == null) {
			List<Integer> order = new ArrayList<>();
			for (int i = 0; i < where.length; i++) {
				order.add(i);
			}
			order.sort(Comparator.<Integer>comparingInt(i -> stageOf(i).ordinal()).thenComparingInt(i -> plan.blocks().get(i).rel().getY()));
			upOrder = order;
		}
		return upOrder;
	}

	/**
	 * Does one step of the move: one block taken down or put up, or the stage moved on. Returns the position worked on (for
	 * the Mover to stand near and swing at), or empty when the stage moved on or the move is over.
	 */
	Optional<BlockPos> step(ServerLevel level) {
		switch (stage) {
			case DISMANTLE -> {
				if (things == OLD) {
					packThings(level, oldMin, Rotation.NONE, oldMin);
					return Optional.empty();
				}
				for (int i : downOrder()) {
					if (where[i] == OLD) {
						BlockPos pos = oldPos(i);
						take(level, i, pos, plan.blocks().get(i).state());
						return Optional.of(pos);
					}
				}
				stage = Stage.FOUNDATION;
			}
			case FOUNDATION, STRUCTURE, DECORATION -> {
				for (int i : upOrder()) {
					if (where[i] == PACKED && stageOf(i) == stage) {
						BlockPos pos = newPos(i);
						put(level, i, pos, newState(i), NEW);
						return Optional.of(pos);
					}
				}
				if (stage == Stage.DECORATION) {
					if (things == PACKED) {
						plan.placeThings(level, newMin, rotation);
						things = NEW;
					}
					stage = Stage.DONE;
				} else {
					stage = Stage.values()[stage.ordinal() + 1];
				}
			}
			case RESTORE_DOWN -> {
				if (things == NEW) {
					packThings(level, newMin, rotation, newMin);
					return Optional.empty();
				}
				for (int i : downOrder()) {
					if (where[i] == NEW) {
						BlockPos pos = newPos(i);
						take(level, i, pos, newState(i));
						return Optional.of(pos);
					}
				}
				stage = Stage.RESTORE_UP;
			}
			case RESTORE_UP -> {
				for (int i : upOrder()) {
					if (where[i] == PACKED) {
						BlockPos pos = oldPos(i);
						put(level, i, pos, plan.blocks().get(i).state(), OLD);
						return Optional.of(pos);
					}
				}
				if (things == PACKED) {
					plan.placeThings(level, oldMin, Rotation.NONE);
					things = OLD;
				}
				stage = Stage.DONE;
			}
			case DONE -> {
			}
		}
		return Optional.empty();
	}

	@Nullable
	private CompoundTag data(int i) {
		CompoundTag packed = packedData.get(i);
		return packed != null ? packed : plan.blocks().get(i).data();
	}

	/** Takes block {@code i} down into the crates, or marks it lost if someone took it first. */
	private void take(ServerLevel level, int i, BlockPos pos, BlockState wanted) {
		BlockState actual = level.getBlockState(pos);
		if (!MaterialRules.matches(actual, wanted)) {
			where[i] = LOST;
			skipped++;
			return;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be != null) {
			// the block entity as it is now (a chest the village used since the plan was made): packed with its items
			packedData.put(i, be.saveWithoutMetadata(level.registryAccess()));
			Clearable.tryClear(be); // so nothing spills when it comes down
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		where[i] = PACKED;
		MoveResidents.taken(this, i);
	}

	/** Puts block {@code i} up from the crates at {@code pos}, whatever stood there taken out of the way first. */
	private void put(ServerLevel level, int i, BlockPos pos, BlockState state, byte now) {
		BlockState there = level.getBlockState(pos);
		if (!there.isAir() && !there.canBeReplaced()) {
			level.destroyBlock(pos, true); // something in the way (the fit check cleared the spot; a player built since)
		}
		level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		CompoundTag data = data(i);
		if (data != null) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be != null) {
				be.loadWithComponents(data.copy(), level.registryAccess());
				be.setChanged();
				level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
			}
		}
		where[i] = now;
		MoveResidents.placed(level, this, i, pos, now); // a resident's bed or workstation is theirs again at once (45.4)
	}

	/** Packs the building's things (frames, armor stands...) standing in its box at {@code min}. */
	private void packThings(ServerLevel level, BlockPos min, Rotation turned, BlockPos boxMin) {
		net.minecraft.core.Vec3i size = plan.size(turned);
		AABB box = new AABB(boxMin.getX(), boxMin.getY(), boxMin.getZ(), boxMin.getX() + size.getX(), boxMin.getY() + size.getY(), boxMin.getZ() + size.getZ());
		for (Entity e : level.getEntities((Entity) null, box, MovePlan::movesWithBuilding)) {
			e.discard(); // they're in the plan, and go up with it
		}
		things = PACKED;
	}

	// ---- saving

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.put("hall", NbtUtils.writeBlockPos(hall));
		tag.put("from", NbtUtils.writeBlockPos(from));
		tag.put("plan", plan.save());
		tag.put("old_min", NbtUtils.writeBlockPos(oldMin));
		tag.put("new_min", NbtUtils.writeBlockPos(newMin));
		tag.putString("rotation", rotation.getSerializedName());
		if (by != null) {
			tag.putUUID("by", by);
		}
		tag.putByteArray("where", where.clone());
		tag.putByte("things", things);
		tag.putString("stage", stage.name());
		tag.putInt("skipped", skipped);
		tag.putBoolean("canceled", canceled);
		net.minecraft.nbt.ListTag packed = new net.minecraft.nbt.ListTag();
		packedData.forEach((i, data) -> {
			CompoundTag entry = new CompoundTag();
			entry.putInt("i", i);
			entry.put("data", data.copy());
			packed.add(entry);
		});
		tag.put("packed_data", packed);
		net.minecraft.nbt.ListTag people = new net.minecraft.nbt.ListTag();
		for (MoveResidents.Resident r : residents) {
			people.add(r.save());
		}
		tag.put("residents", people);
		return tag;
	}

	static Optional<MoveSite> load(CompoundTag tag) {
		Optional<MovePlan> plan = MovePlan.load(tag.getCompound("plan"));
		Optional<BlockPos> hall = NbtUtils.readBlockPos(tag, "hall");
		Optional<BlockPos> from = NbtUtils.readBlockPos(tag, "from");
		Optional<BlockPos> oldMin = NbtUtils.readBlockPos(tag, "old_min");
		Optional<BlockPos> newMin = NbtUtils.readBlockPos(tag, "new_min");
		if (plan.isEmpty() || hall.isEmpty() || from.isEmpty() || oldMin.isEmpty() || newMin.isEmpty()) {
			return Optional.empty();
		}
		byte[] where = tag.getByteArray("where");
		if (where.length != plan.get().blocks().size()) {
			return Optional.empty();
		}
		Rotation rotation = Rotation.NONE;
		for (Rotation r : Rotation.values()) {
			if (r.getSerializedName().equals(tag.getString("rotation"))) {
				rotation = r;
			}
		}
		Stage stage;
		try {
			stage = Stage.valueOf(tag.getString("stage"));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
		MoveSite site = new MoveSite(hall.get(), from.get(), plan.get(), oldMin.get(), newMin.get(), rotation,
			tag.hasUUID("by") ? tag.getUUID("by") : null, where, tag.getByte("things"), stage, tag.getInt("skipped"));
		site.canceled = tag.getBoolean("canceled");
		net.minecraft.nbt.ListTag packed = tag.getList("packed_data", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int k = 0; k < packed.size(); k++) {
			CompoundTag entry = packed.getCompound(k);
			int i = entry.getInt("i");
			if (i >= 0 && i < where.length) {
				site.packedData.put(i, entry.getCompound("data"));
			}
		}
		// a site saved before 45.4 has no residents noted: nobody follows it
		net.minecraft.nbt.ListTag people = tag.getList("residents", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int k = 0; k < people.size(); k++) {
			MoveResidents.Resident.load(people.getCompound(k)).ifPresent(site.residents::add);
		}
		return Optional.of(site);
	}
}
