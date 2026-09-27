package io.github.jcondedata.aliveworkplace.mail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The mail system, once per server: every player's mailbox, the parcels on their way, and where
 * postmen work. Parcels a postman can't carry to the door (another village, another dimension)
 * are handed in at their desk and arrive at the next dawn.
 */
public final class PostOffice extends SavedData {
	private static final String NAME = "aliveworkplace_mail";
	/** A postman's round: mailboxes this close to their Postal Desk. */
	public static final int ROUND = 64;
	/** A desk counts as staffed if its postman worked within this many ticks. */
	private static final long DESK_FRESH = 24000;

	private final Map<UUID, GlobalPos> mailboxes = new HashMap<>();
	private final List<Parcel> parcels = new ArrayList<>();
	/** Postal desk → last game time its postman worked. */
	private final Map<GlobalPos, Long> desks = new HashMap<>();
	private long lastDay = -1;

	public static PostOffice get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(PostOffice::new, PostOffice::load, null), NAME);
	}

	// --- mailboxes ------------------------------------------------------------------------------

	@Nullable
	public GlobalPos mailboxOf(UUID player) {
		return mailboxes.get(player);
	}

	/** The newest mailbox a player puts up is where their mail goes. */
	public void register(UUID player, GlobalPos pos) {
		mailboxes.put(player, pos);
		setDirty();
	}

	public void unregister(UUID player, GlobalPos pos) {
		if (pos.equals(mailboxes.get(player))) {
			mailboxes.remove(player);
			setDirty();
		}
	}

	// --- parcels --------------------------------------------------------------------------------

	public Parcel post(UUID from, String fromName, UUID to, String toName, GlobalPos origin, long gameTime, List<ItemStack> items) {
		Parcel parcel = new Parcel(UUID.randomUUID(), from, fromName, to, toName, origin, gameTime, items, Parcel.Status.AWAITING_PICKUP);
		parcels.add(parcel);
		setDirty();
		return parcel;
	}

	public List<Parcel> parcels() {
		return parcels;
	}

	@Nullable
	public Parcel parcel(UUID id) {
		for (Parcel p : parcels) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return null;
	}

	public void remove(Parcel parcel) {
		parcels.remove(parcel);
		setDirty();
	}

	public void changed() {
		setDirty();
	}

	// --- postmen --------------------------------------------------------------------------------

	public void deskWorked(GlobalPos desk, long gameTime) {
		Long before = desks.put(desk, gameTime);
		if (before == null || gameTime - before > 1200) {
			setDirty();
		}
	}

	/** True if a postman worked recently at a desk whose round covers {@code pos}. */
	public boolean isServed(GlobalPos pos, long gameTime) {
		for (Map.Entry<GlobalPos, Long> e : desks.entrySet()) {
			if (e.getKey().dimension() == pos.dimension() && gameTime - e.getValue() < DESK_FRESH
				&& e.getKey().pos().closerThan(pos.pos(), ROUND)) {
				return true;
			}
		}
		return false;
	}

	// --- the night mail -------------------------------------------------------------------------

	/** Called every tick: at each new dawn the parcels in transit reach their mailboxes. */
	public void tick(MinecraftServer server) {
		long day = server.overworld().getDayTime() / 24000L;
		if (day == lastDay) {
			return;
		}
		boolean first = lastDay < 0;
		lastDay = day;
		setDirty();
		if (!first) {
			dawn(server);
		}
	}

	/** Delivers every parcel in transit whose recipient has a mailbox. */
	public void dawn(MinecraftServer server) {
		Iterator<Parcel> it = parcels.iterator();
		List<Parcel> delivered = new ArrayList<>();
		while (it.hasNext()) {
			Parcel parcel = it.next();
			if (parcel.status() != Parcel.Status.IN_TRANSIT) {
				continue;
			}
			if (deliver(server, parcel)) {
				delivered.add(parcel);
			}
		}
		parcels.removeAll(delivered);
		setDirty();
	}

	/**
	 * Puts the parcel's items into the recipient's mailbox (loading it if need be). Returns true when all
	 * of it fit; what didn't stays in the parcel.
	 */
	public boolean deliver(MinecraftServer server, Parcel parcel) {
		GlobalPos to = mailboxes.get(parcel.to());
		if (to == null) {
			return false;
		}
		ServerLevel level = server.getLevel(to.dimension());
		if (level == null || !(level.getBlockEntity(to.pos()) instanceof MailboxBlockEntity mailbox)) {
			return false;
		}
		List<ItemStack> left = new ArrayList<>();
		for (ItemStack stack : parcel.items()) {
			ItemStack rest = mailbox.receive(stack);
			if (!rest.isEmpty()) {
				left.add(rest);
			}
		}
		parcel.setItems(left);
		setDirty();
		if (!left.isEmpty()) {
			return false;
		}
		ServerPlayer recipient = server.getPlayerList().getPlayer(parcel.to());
		if (recipient != null) {
			BlockPos p = to.pos();
			recipient.sendSystemMessage(Component.translatable("message.aliveworkplace.mail.arrived", parcel.fromName(), p.getX(), p.getY(), p.getZ())
				.withStyle(ChatFormatting.GOLD));
		}
		ServerPlayer sender = server.getPlayerList().getPlayer(parcel.from());
		if (sender != null && !parcel.from().equals(parcel.to())) {
			sender.sendSystemMessage(Component.translatable("message.aliveworkplace.mail.delivered", parcel.toName()).withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	// --- saving ---------------------------------------------------------------------------------

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag boxes = new ListTag();
		mailboxes.forEach((player, pos) -> {
			CompoundTag e = new CompoundTag();
			e.putUUID("player", player);
			GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result().ifPresent(t -> e.put("pos", t));
			boxes.add(e);
		});
		tag.put("mailboxes", boxes);
		ListTag list = new ListTag();
		for (Parcel p : parcels) {
			list.add(p.save(registries));
		}
		tag.put("parcels", list);
		ListTag deskList = new ListTag();
		desks.forEach((pos, time) -> {
			CompoundTag e = new CompoundTag();
			GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result().ifPresent(t -> e.put("pos", t));
			e.putLong("seen", time);
			deskList.add(e);
		});
		tag.put("desks", deskList);
		tag.putLong("lastDay", lastDay);
		return tag;
	}

	private static PostOffice load(CompoundTag tag, HolderLookup.Provider registries) {
		PostOffice out = new PostOffice();
		for (Tag t : tag.getList("mailboxes", Tag.TAG_COMPOUND)) {
			CompoundTag e = (CompoundTag) t;
			GlobalPos pos = Parcel.readPos(e, "pos");
			if (e.hasUUID("player") && pos != null) {
				out.mailboxes.put(e.getUUID("player"), pos);
			}
		}
		for (Tag t : tag.getList("parcels", Tag.TAG_COMPOUND)) {
			Parcel p = Parcel.load((CompoundTag) t, registries);
			if (p != null) {
				out.parcels.add(p);
			}
		}
		for (Tag t : tag.getList("desks", Tag.TAG_COMPOUND)) {
			CompoundTag e = (CompoundTag) t;
			GlobalPos pos = Parcel.readPos(e, "pos");
			if (pos != null) {
				out.desks.put(pos, e.getLong("seen"));
			}
		}
		out.lastDay = tag.contains("lastDay") ? tag.getLong("lastDay") : -1;
		return out;
	}
}
