package io.github.jcondedata.aliveworkplace.travel;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** Every Travel Post on the server, and which ones each player has found. Stored once per server. */
public final class TravelNetwork extends SavedData {
	private static final String NAME = "aliveworkplace_travel";

	public record Post(UUID id, GlobalPos pos, String name) {
	}

	private final Map<UUID, Post> posts = new LinkedHashMap<>();
	private final Map<UUID, Set<UUID>> visited = new HashMap<>();

	public static TravelNetwork get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TravelNetwork::new, TravelNetwork::load, null), NAME);
	}

	public Post add(GlobalPos pos, String name) {
		Post existing = at(pos);
		if (existing != null) {
			posts.remove(existing.id());
		}
		Post post = new Post(existing != null ? existing.id() : UUID.randomUUID(), pos, name);
		posts.put(post.id(), post);
		setDirty();
		return post;
	}

	public void remove(GlobalPos pos) {
		Post post = at(pos);
		if (post != null) {
			posts.remove(post.id());
			setDirty();
		}
	}

	@Nullable
	private static final String[] NAME_START = {"Oak", "Willow", "Maple", "Birch", "Stone", "River", "Sun", "Moon", "Fern", "Amber",
		"Pine", "Clover", "Honey", "Misty", "Silver", "Copper", "Apple", "Berry", "Cedar", "Hazel", "Elder", "Lark", "Robin", "Thistle"};
	private static final String[] NAME_END = {"brook", "field", "ford", "haven", "hollow", "ridge", "dale", "wick", "stead", "mere",
		"vale", "crest", "bury", "ton", "port", "moor"};

	/** A name for a post nobody named (one that came with a village): the same every time for the same spot. */
	public static String villageName(BlockPos pos) {
		long seed = pos.asLong() * 0x9E3779B97F4A7C15L;
		java.util.Random random = new java.util.Random(seed);
		return NAME_START[random.nextInt(NAME_START.length)] + NAME_END[random.nextInt(NAME_END.length)];
	}

	/** The post at {@code pos}, adding it under a village name if the block is there but not yet on the network. */
	@org.jetbrains.annotations.Nullable
	public Post atOrAdd(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
		GlobalPos where = GlobalPos.of(level.dimension(), pos);
		Post post = at(where);
		if (post == null && level.getBlockState(pos).getBlock() instanceof TravelPostBlock) {
			post = add(where, villageName(pos));
		}
		return post;
	}

	public Post at(GlobalPos pos) {
		for (Post p : posts.values()) {
			if (p.pos().equals(pos)) {
				return p;
			}
		}
		return null;
	}

	@Nullable
	public Post post(UUID id) {
		return posts.get(id);
	}

	/** Records that {@code player} found the post; true if it's new to them. */
	public boolean visit(UUID player, Post post) {
		boolean added = visited.computeIfAbsent(player, p -> new HashSet<>()).add(post.id());
		if (added) {
			setDirty();
		}
		return added;
	}

	/** Posts the player has found, other than {@code except}. */
	public List<Post> known(UUID player, @Nullable Post except) {
		Set<UUID> seen = visited.getOrDefault(player, Set.of());
		List<Post> out = new ArrayList<>();
		for (Post p : posts.values()) {
			if (seen.contains(p.id()) && (except == null || !p.id().equals(except.id()))) {
				out.add(p);
			}
		}
		return out;
	}

	/** The nearest post within {@code range} blocks of {@code pos} in the same dimension. */
	@Nullable
	public Post near(GlobalPos pos, int range) {
		Post best = null;
		double bestDistance = (double) range * range;
		for (Post p : posts.values()) {
			if (p.pos().dimension() != pos.dimension()) {
				continue;
			}
			double d = p.pos().pos().distSqr(pos.pos());
			if (d <= bestDistance) {
				best = p;
				bestDistance = d;
			}
		}
		return best;
	}

	/** Fare in emeralds: one per 256 blocks (at least 1, at most 16); 8 to another dimension. */
	public static int fare(GlobalPos from, GlobalPos to) {
		if (from.dimension() != to.dimension()) {
			return 8;
		}
		BlockPos a = from.pos();
		BlockPos b = to.pos();
		double distance = Math.sqrt(a.distSqr(b));
		return (int) Math.max(1, Math.min(16, Math.ceil(distance / 256.0)));
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Post p : posts.values()) {
			CompoundTag e = new CompoundTag();
			Nbt.putUuid(e, "id", p.id());
			GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, p.pos()).result().ifPresent(t -> e.put("pos", t));
			e.putString("name", p.name());
			list.add(e);
		}
		tag.put("posts", list);
		ListTag seen = new ListTag();
		visited.forEach((player, ids) -> {
			for (UUID id : ids) {
				CompoundTag e = new CompoundTag();
				Nbt.putUuid(e, "player", player);
				Nbt.putUuid(e, "post", id);
				seen.add(e);
			}
		});
		tag.put("visited", seen);
		return tag;
	}

	private static TravelNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
		TravelNetwork out = new TravelNetwork();
		for (Tag t : Nbt.getList(tag, "posts", Tag.TAG_COMPOUND)) {
			CompoundTag e = (CompoundTag) t;
			Tag posTag = e.get("pos");
			GlobalPos pos = posTag == null ? null : GlobalPos.CODEC.parse(NbtOps.INSTANCE, posTag).result().orElse(null);
			if (Nbt.hasUuid(e, "id") && pos != null) {
				out.posts.put(Nbt.getUuid(e, "id"), new Post(Nbt.getUuid(e, "id"), pos, Nbt.getString(e, "name")));
			}
		}
		for (Tag t : Nbt.getList(tag, "visited", Tag.TAG_COMPOUND)) {
			CompoundTag e = (CompoundTag) t;
			if (Nbt.hasUuid(e, "player") && Nbt.hasUuid(e, "post")) {
				out.visited.computeIfAbsent(Nbt.getUuid(e, "player"), p -> new HashSet<>()).add(Nbt.getUuid(e, "post"));
			}
		}
		return out;
	}
}
