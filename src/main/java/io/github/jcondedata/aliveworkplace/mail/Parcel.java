package io.github.jcondedata.aliveworkplace.mail;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Items one player posted to another, on their way. */
public final class Parcel {
	public enum Status {
		/** In the sender's mailbox until a postman picks it up. */
		AWAITING_PICKUP,
		/** In a postman's bag. */
		CARRIED,
		/** Handed in at a Postal Desk for somewhere out of the postman's round: arrives at the next dawn. */
		IN_TRANSIT
	}

	private final UUID id;
	private final UUID from;
	private final String fromName;
	private final UUID to;
	private final String toName;
	private final GlobalPos origin;
	private final long sentAt;
	private List<ItemStack> items;
	private Status status;
	@Nullable
	private UUID carrier;
	/** Game time the carrier claimed it (claims by a postman who never came expire). */
	private long claimedAt;

	public Parcel(UUID id, UUID from, String fromName, UUID to, String toName, GlobalPos origin, long sentAt, List<ItemStack> items, Status status) {
		this.id = id;
		this.from = from;
		this.fromName = fromName;
		this.to = to;
		this.toName = toName;
		this.origin = origin;
		this.sentAt = sentAt;
		this.items = new ArrayList<>(items);
		this.status = status;
	}

	public UUID id() {
		return id;
	}

	public UUID from() {
		return from;
	}

	public String fromName() {
		return fromName;
	}

	public UUID to() {
		return to;
	}

	public String toName() {
		return toName;
	}

	public GlobalPos origin() {
		return origin;
	}

	public long sentAt() {
		return sentAt;
	}

	public List<ItemStack> items() {
		return items;
	}

	public void setItems(List<ItemStack> items) {
		this.items = new ArrayList<>(items);
	}

	public int count() {
		return items.stream().mapToInt(ItemStack::getCount).sum();
	}

	public Status status() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	@Nullable
	public UUID carrier() {
		return carrier;
	}

	public long claimedAt() {
		return claimedAt;
	}

	public void claim(@Nullable UUID postman, long gameTime) {
		this.carrier = postman;
		this.claimedAt = gameTime;
	}

	CompoundTag save(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		Nbt.putUuid(tag, "id", id);
		Nbt.putUuid(tag, "from", from);
		tag.putString("fromName", fromName);
		Nbt.putUuid(tag, "to", to);
		tag.putString("toName", toName);
		GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, origin).result().ifPresent(t -> tag.put("origin", t));
		tag.putLong("sentAt", sentAt);
		ListTag list = new ListTag();
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				list.add(stack.save(registries));
			}
		}
		tag.put("items", list);
		tag.putString("status", status.name());
		if (carrier != null) {
			Nbt.putUuid(tag, "carrier", carrier);
			tag.putLong("claimedAt", claimedAt);
		}
		return tag;
	}

	@Nullable
	static GlobalPos readPos(CompoundTag tag, String key) {
		Tag t = tag.get(key);
		return t == null ? null : GlobalPos.CODEC.parse(NbtOps.INSTANCE, t).result().orElse(null);
	}

	@Nullable
	static Parcel load(CompoundTag tag, HolderLookup.Provider registries) {
		if (!Nbt.hasUuid(tag, "id") || !Nbt.hasUuid(tag, "from") || !Nbt.hasUuid(tag, "to")) {
			return null;
		}
		GlobalPos origin = readPos(tag, "origin");
		if (origin == null) {
			return null;
		}
		List<ItemStack> items = new ArrayList<>();
		for (Tag t : Nbt.getList(tag, "items", Tag.TAG_COMPOUND)) {
			ItemStack.parse(registries, t).ifPresent(items::add);
		}
		Status status;
		try {
			status = Status.valueOf(Nbt.getString(tag, "status"));
		} catch (IllegalArgumentException e) {
			status = Status.IN_TRANSIT;
		}
		Parcel parcel = new Parcel(Nbt.getUuid(tag, "id"), Nbt.getUuid(tag, "from"), Nbt.getString(tag, "fromName"), Nbt.getUuid(tag, "to"), Nbt.getString(tag, "toName"),
			origin, Nbt.getLong(tag, "sentAt"), items, status);
		if (Nbt.hasUuid(tag, "carrier")) {
			parcel.claim(Nbt.getUuid(tag, "carrier"), Nbt.getLong(tag, "claimedAt"));
		}
		return parcel;
	}
}
