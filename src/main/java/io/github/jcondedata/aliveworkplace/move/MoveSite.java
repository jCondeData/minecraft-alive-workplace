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
 * Its belongings arrive as they were (45.5): each block entity and thing is packed as it is when taken down, a store
 * that spills its items as it goes has them taken back (they're in its data), and a block of a mod removed since reads as
 * air and is left out.
 * A Mover's Crate inside the building stays where it stands (B1120): it's where the Movers pack what they take down,
 * so packing it would leave its Mover nowhere to pack the rest. It isn't counted in the move's blocks.
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
	/** A Mover's Crate: left standing at the old spot, never packed or put up (B1120). */
	static final byte STAYS = 4;

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
	/**
	 * The building's things (frames, paintings, armor stands, furniture entities) as they were when packed (45.5): their
	 * items as the village left them, not as the plan saw them. Null on a site saved before 45.5, which puts the plan's up.
	 */
	@Nullable
	private List<MovePlan.Thing> packedThings;
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

	/** How many blocks of the building move: all but the Mover's Crates in it, which stay where they stand. */
	public int total() {
		int n = 0;
		for (int i = 0; i < where.length; i++) {
			if (!stays(i)) {
				n++;
			}
		}
		return n;
	}

	/** Whether block {@code i} is a Mover's Crate, which stays where it stands (B1120). */
	boolean stays(int i) {
		return stays(plan.blocks().get(i).state());
	}

	/** Whether {@code state} is a Mover's Crate: where Movers pack, so it's never packed itself (B1120). */
	public static boolean stays(BlockState state) {
		return state.is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.MOVERS_CRATE);
	}

	/**
	 * Whether a Mover's Crate of this building is packed while it's coming down: only on a site saved before B1120's fix,
	 * where it left its Mover waiting for good. The next step puts it back up where it stood, so he can carry on.
	 */
	public boolean crateToPutBack() {
		if (stage != Stage.DISMANTLE) {
			return false;
		}
		for (int i = 0; i < where.length; i++) {
			if (where[i] == PACKED && stays(i)) {
				return true;
			}
		}
		return false;
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
				for (int i = 0; i < where.length; i++) {
					if (where[i] == PACKED && stays(i)) { // packed before B1120's fix: back where it stood
						BlockPos pos = oldPos(i);
						put(level, i, pos, plan.blocks().get(i).state(), STAYS);
						return Optional.of(pos);
					}
				}
				for (int i : downOrder()) {
					if (where[i] == OLD && stays(i)) {
						where[i] = STAYS; // his crate stays: nothing to do in the world
						continue;
					}
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
						placeThings(level, newMin, rotation);
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
					placeThings(level, oldMin, Rotation.NONE);
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

	/**
	 * Takes block {@code i} down into the crates, or marks it lost if someone took it first. A block laid whole with its
	 * neighbours (Create's belt, B1128) comes down whole: taking one segment takes the rest of the belt with it, so every
	 * segment still standing is packed with its data (the belt's items are on its controller) before any comes down. So is
	 * a container joined from several blocks (Create's Fluid Tank and Item Vault, B1135, B1136): see {@link #whole}.
	 */
	private void take(ServerLevel level, int i, BlockPos pos, BlockState wanted) {
		boolean atNew = where[i] == NEW; // taken from the new spot when a canceled move comes down again
		List<Integer> group = whole(i, atNew ? NEW : OLD);
		List<Integer> taken = new ArrayList<>();
		List<BlockPos> spills = new ArrayList<>();
		for (int j : group) {
			BlockPos at = j == i ? pos : atNew ? newPos(j) : oldPos(j);
			BlockState want = j == i ? wanted : atNew ? newState(j) : plan.blocks().get(j).state();
			BlockState actual = level.getBlockState(at);
			// a block joined into one container with its neighbours (Create's tank) takes its shape from them: still the block
			if (want.isAir() || !MaterialRules.matches(actual, want) && !(MaterialRules.joined(want) && actual.is(want.getBlock()))) {
				// gone (someone broke it), or a block of a mod no longer installed (its id reads as air): not moved
				where[j] = LOST;
				skipped++;
				continue;
			}
			BlockEntity be = level.getBlockEntity(at);
			if (be != null) {
				// the block entity as it is now (a chest the village used since the plan was made): packed with its items
				packedData.put(j, be.saveWithoutMetadata(level.registryAccess()));
				Clearable.tryClear(be); // so nothing spills when it comes down
				spills.add(at);
			}
			taken.add(j);
		}
		java.util.Set<net.minecraft.world.entity.item.ItemEntity> before = new java.util.HashSet<>();
		for (BlockPos at : spills) {
			before.addAll(loose(level, at));
		}
		for (int j : taken) {
			level.setBlock(j == i ? pos : atNew ? newPos(j) : oldPos(j), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		// 45.5: a modded store that isn't a vanilla Container (nothing to clear) may still spill its items as it goes
		// (Create's crafter, ejector and belt, B1127); they're packed with the block's data, so what it spilled is taken
		// back rather than left to be doubled
		for (BlockPos at : spills) {
			for (net.minecraft.world.entity.item.ItemEntity spilled : loose(level, at)) {
				if (!before.contains(spilled)) {
					spilled.discard();
				}
			}
		}
		for (int j : taken) {
			where[j] = PACKED;
			MoveResidents.taken(this, j);
		}
	}

	/**
	 * Block {@code i} and, when it's laid whole with its neighbours (Create's belt), every other block of the same kind
	 * in the building that is {@code in} the same place: they come down and go up together, as a builder lays a belt.
	 * A block joined into one container with the touching blocks of its kind (Create's Fluid Tank and Item Vault) comes
	 * down with the blocks it touches, and they with theirs (B1135, B1136): every block of the container is packed with its
	 * data as the container stands, before taking one makes Create split it (the rest re-shaped, the block taken losing its
	 * share of the fluid, the others packed as separate containers). They go up one by one, each with the data it had in
	 * the whole container: put back where it stood it is that container again, and put up at the new spot Create sees its
	 * blocks have moved and joins them again, the contents gathered on the new controller.
	 */
	private List<Integer> whole(int i, byte in) {
		List<Integer> group = new ArrayList<>();
		group.add(i);
		BlockState state = plan.blocks().get(i).state();
		if (MaterialRules.laidWhole(state)) {
			for (int j = 0; j < where.length; j++) {
				if (j != i && where[j] == in && plan.blocks().get(j).state().getBlock() == state.getBlock()) {
					group.add(j);
				}
			}
		} else if (MaterialRules.joined(state)) {
			java.util.Map<BlockPos, Integer> kind = new java.util.HashMap<>();
			for (int j = 0; j < where.length; j++) {
				if (where[j] == in && plan.blocks().get(j).state().getBlock() == state.getBlock()) {
					kind.put(plan.blocks().get(j).rel(), j);
				}
			}
			for (int k = 0; k < group.size(); k++) { // the blocks touching the group, until none is left
				BlockPos rel = plan.blocks().get(group.get(k)).rel();
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
					Integer j = kind.get(rel.relative(d));
					if (j != null && !group.contains(j)) {
						group.add(j);
					}
				}
			}
		}
		return group;
	}

	/** Loose items in and round the block at {@code pos}: where a block's contents land if it spills them. */
	private static List<net.minecraft.world.entity.item.ItemEntity> loose(ServerLevel level, BlockPos pos) {
		return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(1));
	}

	/**
	 * Puts block {@code i} up from the crates at {@code pos}, whatever stood there taken out of the way first. A block
	 * laid whole (Create's belt) goes up whole: every packed segment stands before any gets its data, so the belt is never
	 * half there, and each segment's controller is moved to where its controller now stands (B1128).
	 */
	private void put(ServerLevel level, int i, BlockPos pos, BlockState state, byte now) {
		List<Integer> group = (now == NEW || now == OLD) && MaterialRules.laidWhole(plan.blocks().get(i).state()) ? whole(i, PACKED) : List.of(i);
		List<Integer> up = new ArrayList<>();
		List<BlockPos> ats = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		for (int j : group) {
			BlockPos at = j == i ? pos : now == NEW ? newPos(j) : oldPos(j);
			BlockState st = j == i ? state : now == NEW ? newState(j) : plan.blocks().get(j).state();
			if (st.isAir()) {
				// a block of a mod removed since it was packed (its id reads as air): nothing to put up
				where[j] = LOST;
				skipped++;
				continue;
			}
			BlockState there = level.getBlockState(at);
			if (stays(there) && !stays(st)) {
				// B1129: a Mover's Crate stands there (it stays where it stood, B1120; the fit check refuses such a spot, but a
				// move confirmed before that check may still meet one). Never broken: it's a Mover's workstation. Not put up.
				where[j] = LOST;
				skipped++;
				continue;
			}
			if (!there.isAir() && !there.canBeReplaced()) {
				level.destroyBlock(at, true); // something in the way (the fit check cleared the spot; a player built since)
			}
			level.setBlock(at, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			up.add(j);
			ats.add(at);
			states.add(st);
		}
		for (int k = 0; k < up.size(); k++) {
			int j = up.get(k);
			BlockPos at = ats.get(k);
			CompoundTag data = data(j);
			if (data != null) {
				BlockEntity be = level.getBlockEntity(at);
				if (be != null) {
					be.loadWithComponents(group.size() > 1 ? withController(data.copy(), now) : data.copy(), level.registryAccess());
					be.setChanged();
					level.sendBlockUpdated(at, states.get(k), states.get(k), Block.UPDATE_CLIENTS);
				}
			}
			where[j] = now;
			MoveResidents.placed(level, this, j, at, now); // a resident's bed or workstation is theirs again at once (45.4)
		}
	}

	/**
	 * A belt segment's data with its {@code Controller} (a world position) moved along with the building: from the spot
	 * it was packed at (the old one, or the new one when a canceled move puts it back) to where that block now goes up.
	 */
	private CompoundTag withController(CompoundTag data, byte now) {
		net.minecraft.nbt.Tag tag = data.get("Controller");
		BlockPos from = null;
		if (tag instanceof net.minecraft.nbt.IntArrayTag array && array.getAsIntArray().length == 3) {
			int[] p = array.getAsIntArray();
			from = new BlockPos(p[0], p[1], p[2]);
		} else if (tag instanceof CompoundTag xyz && xyz.contains("X") && xyz.contains("Y") && xyz.contains("Z")) {
			from = new BlockPos(io.github.jcondedata.aliveworkplace.mc.Nbt.getInt(xyz, "X"), io.github.jcondedata.aliveworkplace.mc.Nbt.getInt(xyz, "Y"),
				io.github.jcondedata.aliveworkplace.mc.Nbt.getInt(xyz, "Z"));
		}
		if (from == null) {
			return data;
		}
		for (int j = 0; j < where.length; j++) {
			if ((now == NEW ? oldPos(j) : newPos(j)).equals(from)) {
				BlockPos to = now == NEW ? newPos(j) : oldPos(j);
				if (tag instanceof CompoundTag) {
					CompoundTag xyz = new CompoundTag();
					xyz.putInt("X", to.getX());
					xyz.putInt("Y", to.getY());
					xyz.putInt("Z", to.getZ());
					data.put("Controller", xyz);
				} else {
					data.put("Controller", new net.minecraft.nbt.IntArrayTag(new int[] {to.getX(), to.getY(), to.getZ()}));
				}
				break;
			}
		}
		return data;
	}

	/** Packs the building's things (frames, armor stands...) standing in its box at {@code min}. */
	private void packThings(ServerLevel level, BlockPos min, Rotation turned, BlockPos boxMin) {
		net.minecraft.core.Vec3i size = plan.size(turned);
		AABB box = new AABB(boxMin.getX(), boxMin.getY(), boxMin.getZ(), boxMin.getX() + size.getX(), boxMin.getY() + size.getY(), boxMin.getZ() + size.getZ());
		if (turned == Rotation.NONE) {
			// 45.5: packed as they are now (a frame's item, a stand's armor as the village left them), not as the plan saw
			// them, so nothing taken off one since comes back and nothing put on is lost
			List<MovePlan.Thing> now = new ArrayList<>();
			for (Entity e : MovePlan.thingsIn(level, oldBox().moved(boxMin.getX() - oldMin.getX(), boxMin.getY() - oldMin.getY(), boxMin.getZ() - oldMin.getZ()))) {
				now.add(MovePlan.thing(e, min));
				e.discard();
			}
			packedThings = now;
		} else {
			// (only on a move called off after its things went up, which DONE comes before: the plan's go back)
			for (Entity e : level.getEntities((Entity) null, box, MovePlan::movesWithBuilding)) {
				e.discard();
			}
		}
		things = PACKED;
	}

	/** Puts the building's packed things up at {@code min}: as they were packed (45.5), or the plan's on an older save. */
	private void placeThings(ServerLevel level, BlockPos min, Rotation turned) {
		if (packedThings != null) {
			plan.placeThings(level, packedThings, min, turned);
		} else {
			plan.placeThings(level, min, turned);
		}
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
		if (packedThings != null) {
			net.minecraft.nbt.ListTag thingList = new net.minecraft.nbt.ListTag();
			for (MovePlan.Thing t : packedThings) {
				CompoundTag entry = new CompoundTag();
				entry.putDouble("x", t.rel().x);
				entry.putDouble("y", t.rel().y);
				entry.putDouble("z", t.rel().z);
				entry.put("data", t.data().copy());
				thingList.add(entry);
			}
			tag.put("packed_things", thingList);
		}
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
		// a site saved before 45.5 has no packed things: the plan's go up
		if (tag.contains("packed_things", net.minecraft.nbt.Tag.TAG_LIST)) {
			net.minecraft.nbt.ListTag thingList = tag.getList("packed_things", net.minecraft.nbt.Tag.TAG_COMPOUND);
			List<MovePlan.Thing> list = new ArrayList<>();
			for (int k = 0; k < thingList.size(); k++) {
				CompoundTag entry = thingList.getCompound(k);
				list.add(new MovePlan.Thing(new net.minecraft.world.phys.Vec3(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")),
					entry.getCompound("data")));
			}
			site.packedThings = list;
		}
		// a site saved before 45.4 has no residents noted: nobody follows it
		net.minecraft.nbt.ListTag people = tag.getList("residents", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int k = 0; k < people.size(); k++) {
			MoveResidents.Resident.load(people.getCompound(k)).ifPresent(site.residents::add);
		}
		return Optional.of(site);
	}
}
