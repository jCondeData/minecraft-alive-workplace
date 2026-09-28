package io.github.jcondedata.aliveworkplace.mine;

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
	public enum Status { WORKING, NEEDS_PICKAXE, DEPOSITING, DONE }

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

	public long total() {
		return (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
	}

	/** The block at position {@code index} in digging order. */
	public BlockPos at(long index) {
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

	@Nullable
	public BlockPos current() {
		return isDone() ? null : at(cursor);
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
		tag.putUUID("id", id);
		tag.putUUID("owner", owner);
		tag.putString("owner_name", ownerName);
		tag.putString("dimension", dimension.toString());
		tag.putIntArray("box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
		tag.putInt("depth", depth);
		tag.putLong("cursor", cursor);
		tag.putInt("mined", mined);
		tag.putInt("skipped", skipped);
		if (miner != null) {
			tag.putUUID("miner", miner);
		}
		if (bench != null) {
			tag.putLong("bench", bench.asLong());
		}
		tag.putBoolean("stairs", stairs);
		tag.putInt("stair_start", stairStart);
		return tag;
	}

	@Nullable
	public static QuarrySite load(CompoundTag tag) {
		int[] b = tag.getIntArray("box");
		ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dimension"));
		if (b.length != 6 || dim == null || !tag.hasUUID("id") || !tag.hasUUID("owner")) {
			return null;
		}
		QuarrySite site = new QuarrySite(tag.getUUID("id"), tag.getUUID("owner"), tag.getString("owner_name"), dim,
			new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), tag.getInt("depth"));
		site.cursor = tag.getLong("cursor");
		site.mined = tag.getInt("mined");
		site.skipped = tag.getInt("skipped");
		site.miner = tag.hasUUID("miner") ? tag.getUUID("miner") : null;
		site.bench = tag.contains("bench", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("bench")) : null;
		site.fromOldVersion = !tag.contains("stairs");
		site.stairs = tag.getBoolean("stairs"); // false for quarries started before stairs existed
		site.stairStart = tag.getInt("stair_start");
		return site;
	}
}
