package io.github.jcondedata.aliveworkplace.mine;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * A quarry being dug out. Blocks are visited layer by layer from the top, in a back-and-forth
 * pattern within each layer; {@code cursor} is how far along that order the miner is. Saved with
 * the world in {@link QuarrySiteManager}, so the miner picks up where it left off.
 */
public final class QuarrySite {
	public enum Status { WORKING, NEEDS_PICKAXE, NEEDS_LADDERS, DEPOSITING, DONE }

	/**
	 * Where the cursor is: down the ladder shaft (strip mines at a set height), in the quarry itself, or putting the
	 * last ladders up at the foot of the shaft.
	 */
	public enum Phase { SHAFT, PIT, LADDERS }

	/** No ladder shaft (see {@link #setShaft}). */
	private static final int NO_SHAFT = Integer.MIN_VALUE;

	private final UUID id;
	private final UUID owner;
	private final String ownerName;
	private final ResourceLocation dimension;
	private final BoundingBox box;
	private final int depth;
	private long cursor;
	private int mined;
	private int skipped;
	@Nullable
	private UUID miner;
	@Nullable
	private BlockPos bench;

	/** Leave a staircase of steps spiralling down the pit walls (quarries from before 0.45 don't). */
	private boolean stairs;
	/** Where along the walls the top step is (see {@link #wallIndex}). */
	private int stairStart;
	/** Tunnels with rock between them instead of an open pit (see {@link #isTunnel}). */
	private boolean stripMine;
	/** Top of the ladder shaft down to a strip mine, or {@link #NO_SHAFT}. */
	private int shaftTop = NO_SHAFT;
	/** Saved by a version before 0.45.0, which could stretch the quarry over its bench (see {@link #looksStretched}). */
	private boolean fromOldVersion;

	private Status status = Status.WORKING;
	private long lastNotified = Long.MIN_VALUE / 2;
	private Runnable onChange = () -> {
	};

	public QuarrySite(UUID id, UUID owner, String ownerName, ResourceLocation dimension, BoundingBox box, int depth) {
		this.id = id;
		this.owner = owner;
		this.ownerName = ownerName;
		this.dimension = dimension;
		this.box = box;
		this.depth = depth;
	}

	/** Every step: the shaft (if any), each block of the quarry, and the two ladders at the foot of the shaft. */
	public long total() {
		return shaftLength() + boxVolume() + (hasShaft() ? 2 : 0);
	}

	private long boxVolume() {
		return (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
	}

	/** The block at position {@code index} in digging order. */
	public BlockPos at(long index) {
		int shaft = shaftLength();
		if (index < shaft) {
			BlockPos column = shaftColumn();
			return new BlockPos(column.getX(), shaftTop - (int) index, column.getZ());
		}
		index -= shaft;
		if (index >= boxVolume()) {
			BlockPos column = shaftColumn();
			return new BlockPos(column.getX(), box.maxY() - (int) (index - boxVolume()), column.getZ());
		}
		int w = box.getXSpan();
		int d = box.getZSpan();
		long layerSize = (long) w * d;
		int layer = (int) (index / layerSize);
		int within = (int) (index % layerSize);
		int row = within / w;
		int col = within % w;
		if (layer % 2 == 1) {
			row = d - 1 - row; // come back the other way on the next layer: less walking
		}
		int x = row % 2 == 0 ? box.minX() + col : box.maxX() - col;
		return new BlockPos(x, box.maxY() - layer, box.minZ() + row);
	}

	public boolean isDone() {
		return cursor >= total();
	}

	public Phase phase() {
		if (cursor < shaftLength()) {
			return Phase.SHAFT;
		}
		return cursor < shaftLength() + boxVolume() ? Phase.PIT : Phase.LADDERS;
	}

	// --- ladder shaft ------------------------------------------------------------------------

	/** Gives this strip mine a ladder shaft down from {@code top} (the marked corners' height). */
	public void setShaft(int top) {
		shaftTop = top;
		onChange.run();
	}

	public boolean hasShaft() {
		return shaftTop != NO_SHAFT && shaftTop > box.maxY();
	}

	/** Shaft blocks above the tunnels (the two in the tunnel are dug with it). */
	public int shaftLength() {
		return hasShaft() ? shaftTop - box.maxY() : 0;
	}

	public int shaftTop() {
		return shaftTop;
	}

	/**
	 * Where the shaft comes down (any y): the corner of the area where the first tunnel meets the cross tunnel, on
	 * the bench's side, so the way up is the way home.
	 */
	public BlockPos shaftColumn() {
		boolean alongX = box.getXSpan() >= box.getZSpan();
		int along = crossTunnelAt();
		return alongX ? new BlockPos(box.minX() + along, box.maxY(), box.minZ()) : new BlockPos(box.minX(), box.maxY(), box.minZ() + along);
	}

	/** The side of the shaft the ladders hang on: out of the area, across the tunnels (rock the miner never digs). */
	public net.minecraft.core.Direction ladderWall() {
		return box.getXSpan() >= box.getZSpan() ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.WEST;
	}

	/** Whether {@code pos} is in the shaft, from its top down to the tunnel floor. */
	public boolean inShaft(BlockPos pos) {
		if (!hasShaft()) {
			return false;
		}
		BlockPos column = shaftColumn();
		return pos.getX() == column.getX() && pos.getZ() == column.getZ() && pos.getY() >= box.minY() && pos.getY() <= shaftTop;
	}

	/** The shaft step being worked on: it gets its ladder once dug (so digging it doesn't move the cursor on). */
	public boolean awaitsLadder(BlockPos pos) {
		return phase() != Phase.PIT && pos.equals(current());
	}

	/** Gives up on the shaft (something in the way): the miner makes its own way down, the tunnels still get dug. */
	public void skipShaft() {
		if (phase() == Phase.SHAFT) {
			cursor = shaftLength();
			skipped++;
			onChange.run();
		}
	}

	@Nullable
	public BlockPos current() {
		return isDone() ? null : at(cursor);
	}

	/** A block dug without moving on (a shaft step, which gets its ladder next). */
	public void countMined() {
		mined++;
		onChange.run();
	}

	public void advance(boolean dug, boolean skip) {
		cursor++;
		if (dug) {
			mined++;
		}
		if (skip) {
			skipped++;
		}
		onChange.run();
	}

	// --- stairs ------------------------------------------------------------------------------

	/** Smallest pit that gets stairs: 3 × 3 across and 3 deep (anything shallower you can climb out of). */
	public static final int STAIRS_MIN = 3;

	/** Plans the stairs: on if the pit is big enough, starting at the wall nearest {@code bench} (the way out). */
	public void planStairs(@Nullable BlockPos bench) {
		stairs = box.getXSpan() >= STAIRS_MIN && box.getZSpan() >= STAIRS_MIN && box.getYSpan() >= STAIRS_MIN;
		stairStart = 0;
		if (bench != null) {
			double best = Double.MAX_VALUE;
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					int i = wallIndex(x, z);
					double dist = i < 0 ? Double.MAX_VALUE : Math.pow(x - bench.getX(), 2) + Math.pow(z - bench.getZ(), 2);
					if (dist < best) {
						best = dist;
						stairStart = i;
					}
				}
			}
		}
		onChange.run();
	}

	/**
	 * Before 0.45.0, keeping a quarry loaded grew its box to take in the ground within the supply radius of the bench
	 * (bench included). A quarry from then whose box holds its own bench was almost certainly stretched like that.
	 */
	public boolean looksStretched() {
		return fromOldVersion && bench != null && box.isInside(bench);
	}

	public boolean isStripMine() {
		return stripMine;
	}

	/** Makes this a strip mine (no stairs: it's only as deep as a tunnel). */
	public void setStripMine(boolean stripMine) {
		this.stripMine = stripMine;
		if (stripMine) {
			stairs = false;
		}
		onChange.run();
	}

	/**
	 * In a strip mine, whether {@code pos} is part of a tunnel: tunnels run along the longer side of the area, every
	 * third row (two rows of rock between them, each touching a tunnel), joined by a cross tunnel at the end nearest
	 * the bench. The rock between them is left, apart from the ores in it.
	 */
	public boolean isTunnel(BlockPos pos) {
		boolean alongX = box.getXSpan() >= box.getZSpan();
		int across = alongX ? pos.getZ() - box.minZ() : pos.getX() - box.minX();
		if (across % 3 == 0) {
			return true;
		}
		int along = alongX ? pos.getX() - box.minX() : pos.getZ() - box.minZ();
		return along == crossTunnelAt();
	}

	/** How far along the tunnels the cross tunnel is: the end nearest the bench. */
	private int crossTunnelAt() {
		boolean alongX = box.getXSpan() >= box.getZSpan();
		int length = alongX ? box.getXSpan() : box.getZSpan();
		if (bench == null) {
			return 0;
		}
		int b = alongX ? bench.getX() : bench.getZ();
		int lo = alongX ? box.minX() : box.minZ();
		return Math.abs(b - lo) <= Math.abs(b - (lo + length - 1)) ? 0 : length - 1;
	}

	public boolean hasStairs() {
		return stairs;
	}

	public void setStairs(boolean stairs) {
		this.stairs = stairs;
		onChange.run();
	}

	/** Blocks around the edge of one layer. */
	private int wallLength() {
		return 2 * (box.getXSpan() - 1) + 2 * (box.getZSpan() - 1);
	}

	/** Where (x, z) is along the edge of the pit, going round: 0 at the north-west corner, then east; -1 inside. */
	int wallIndex(int x, int z) {
		int w = box.getXSpan();
		int d = box.getZSpan();
		int lx = x - box.minX();
		int lz = z - box.minZ();
		if (lx < 0 || lz < 0 || lx >= w || lz >= d) {
			return -1;
		}
		if (lz == 0) {
			return lx;
		}
		if (lx == w - 1) {
			return (w - 1) + lz;
		}
		if (lz == d - 1) {
			return (w - 1) + (d - 1) + (w - 1 - lx);
		}
		if (lx == 0) {
			return 2 * (w - 1) + (d - 1) + (d - 1 - lz);
		}
		return -1;
	}

	/**
	 * Whether the block at {@code pos} is a step of the stairs: one block per layer, each one along the wall from the
	 * one above and a block lower, so the steps spiral down the pit walls. Steps are left standing (or filled in).
	 */
	public boolean isStep(BlockPos pos) {
		if (!stairs || !box.isInside(pos)) {
			return false;
		}
		int i = wallIndex(pos.getX(), pos.getZ());
		return i >= 0 && i == Math.floorMod(stairStart + (box.maxY() - pos.getY()), wallLength());
	}

	public float progress() {
		return total() == 0 ? 1f : Math.min(1f, cursor / (float) total());
	}

	public UUID id() {
		return id;
	}

	public UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public ResourceLocation dimension() {
		return dimension;
	}

	public BoundingBox box() {
		return box;
	}

	public int depth() {
		return depth;
	}

	public int mined() {
		return mined;
	}

	public int skipped() {
		return skipped;
	}

	@Nullable
	public UUID miner() {
		return miner;
	}

	public void setMiner(@Nullable UUID miner) {
		this.miner = miner;
		onChange.run();
	}

	@Nullable
	public BlockPos bench() {
		return bench;
	}

	public void setBench(@Nullable BlockPos bench) {
		this.bench = bench;
		onChange.run();
	}

	public Status status() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public boolean shouldNotify(long gameTime, long interval) {
		if (gameTime - lastNotified < interval) {
			return false;
		}
		lastNotified = gameTime;
		return true;
	}

	void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		Nbt.putUuid(tag, "id", id);
		Nbt.putUuid(tag, "owner", owner);
		tag.putString("owner_name", ownerName);
		tag.putString("dimension", dimension.toString());
		tag.putIntArray("box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
		tag.putInt("depth", depth);
		tag.putLong("cursor", cursor);
		tag.putInt("mined", mined);
		tag.putInt("skipped", skipped);
		if (miner != null) {
			Nbt.putUuid(tag, "miner", miner);
		}
		if (bench != null) {
			tag.putLong("bench", bench.asLong());
		}
		tag.putBoolean("stairs", stairs);
		if (stripMine) {
			tag.putBoolean("strip_mine", true);
		}
		tag.putInt("stair_start", stairStart);
		if (shaftTop != NO_SHAFT) {
			tag.putInt("shaft_top", shaftTop);
		}
		return tag;
	}

	@Nullable
	public static QuarrySite load(CompoundTag tag) {
		int[] b = Nbt.getIntArray(tag, "box");
		ResourceLocation dim = ResourceLocation.tryParse(Nbt.getString(tag, "dimension"));
		if (b.length != 6 || dim == null || !Nbt.hasUuid(tag, "id") || !Nbt.hasUuid(tag, "owner")) {
			return null;
		}
		QuarrySite site = new QuarrySite(Nbt.getUuid(tag, "id"), Nbt.getUuid(tag, "owner"), Nbt.getString(tag, "owner_name"), dim,
			new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), Nbt.getInt(tag, "depth"));
		site.cursor = Nbt.getLong(tag, "cursor");
		site.mined = Nbt.getInt(tag, "mined");
		site.skipped = Nbt.getInt(tag, "skipped");
		site.miner = Nbt.hasUuid(tag, "miner") ? Nbt.getUuid(tag, "miner") : null;
		site.bench = Nbt.has(tag, "bench", Tag.TAG_LONG) ? BlockPos.of(Nbt.getLong(tag, "bench")) : null;
		site.fromOldVersion = !tag.contains("stairs");
		site.stairs = Nbt.getBoolean(tag, "stairs"); // false for quarries started before stairs existed
		site.stairStart = Nbt.getInt(tag, "stair_start");
		site.stripMine = Nbt.getBoolean(tag, "strip_mine");
		site.shaftTop = Nbt.has(tag, "shaft_top", Tag.TAG_INT) ? Nbt.getInt(tag, "shaft_top") : NO_SHAFT;
		return site;
	}
}
