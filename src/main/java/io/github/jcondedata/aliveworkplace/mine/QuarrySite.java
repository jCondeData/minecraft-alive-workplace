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
		return site;
	}
}
