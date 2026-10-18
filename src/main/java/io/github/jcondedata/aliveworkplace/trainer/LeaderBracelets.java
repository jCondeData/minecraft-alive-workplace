package io.github.jcondedata.aliveworkplace.trainer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * A Mega Bracelet for beating a realm's Trainer Leaders (ROADMAP 38.6, Mega Showdown). Each hall's round notes whether
 * its village has a Trainer Leader of its own ({@link #round}); a player who beats a village's Leader has that village
 * marked ({@link #beaten}). Once they have beaten the Leader of every village of a realm that has one, the capital's
 * hall sends them a Mega Bracelet: once per player per realm ({@code megaBracelet} in the saved data). Before that, each
 * new village beaten tells them how many of the realm's Leaders are left. Behind {@code villageMegas} and realms; without
 * Mega Showdown nothing here happens (no bracelet item to give, nothing saved).
 */
public final class LeaderBracelets {
	/** Mega Showdown's plain Mega Bracelet. */
	public static final ResourceLocation BRACELET = ResourceLocation.fromNamespaceAndPath("mega_showdown", "mega_bracelet");

	private LeaderBracelets() {
	}

	public static void init() {
		// Nothing to register: the hall round and the trainer battles call in.
	}

	/** Whether bracelets are given: Mega Showdown installed, {@code villageMegas} on, realms on. */
	public static boolean on() {
		return MegaBracelets.extraMegas() && Realms.ENABLED;
	}

	/** A village's own Trainer Leader (not a Stadium visitor or a Cup delegate standing in). */
	public static boolean ownLeader(Villager villager) {
		return villager.isAlive() && Trainers.isLeader(villager) && !StadiumDays.isVisitor(villager) && !CupDays.isDelegate(villager);
	}

	/** The hall's round: notes whether the village round {@code hall} has a Trainer Leader of its own ({@code workers}: its census). */
	public static void round(ServerLevel level, BlockPos hall, List<Villager> workers) {
		if (!MegaBracelets.MOD) {
			return;
		}
		boolean has = workers.stream().anyMatch(LeaderBracelets::ownLeader);
		Data data = Data.get(level.getServer());
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		if (has ? data.leaderHalls.add(at) : data.leaderHalls.remove(at)) {
			data.setDirty();
		}
	}

	/** {@code player} beat {@code leader}: marks the Leader's village, and gives the bracelet when it was the realm's last. */
	public static void beaten(ServerLevel level, Villager leader, ServerPlayer player) {
		if (!on() || !ownLeader(leader)) {
			return;
		}
		Optional<BlockPos> home = VillageHalls.nearest(level, leader.blockPosition());
		if (home.isEmpty()) {
			return;
		}
		MinecraftServer server = level.getServer();
		Data data = Data.get(server);
		GlobalPos at = GlobalPos.of(level.dimension(), home.get());
		boolean news = data.leaderHalls.add(at);
		Beats beats = data.players.computeIfAbsent(player.getUUID(), id -> new Beats(new LinkedHashSet<>(), new LinkedHashSet<>()));
		news |= beats.beaten().add(at);
		if (news) {
			data.setDirty();
		}
		RealmData.Realm realm = Realms.realmOf(level, home.get());
		if (realm == null || beats.given().contains(realm.id())) {
			return;
		}
		List<GlobalPos> leaders = leaderHalls(data, realm);
		long done = leaders.stream().filter(beats.beaten()::contains).count();
		if (done < leaders.size()) {
			if (news) {
				Chat.system(player, Component.translatable("message.aliveworkplace.leader_bracelet.progress", Realms.title(server, realm), done,
					leaders.size()).withStyle(ChatFormatting.AQUA));
			}
			return;
		}
		Optional<Item> bracelet = BuiltInRegistries.ITEM.getOptional(BRACELET);
		if (bracelet.isEmpty()) {
			return;
		}
		beats.given().add(realm.id());
		data.setDirty();
		ItemStack stack = new ItemStack(bracelet.get());
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
		Chat.system(player, Component.translatable("message.aliveworkplace.leader_bracelet.given", Realms.title(server, realm),
			Realms.name(server, realm.capital())).withStyle(ChatFormatting.GOLD));
		AliveWorkplace.LOG.info("Gave {} a Mega Bracelet for beating every Trainer Leader of realm {}", player.getGameProfile().getName(), realm.id());
	}

	/** The realm's villages that have a Trainer Leader, the capital first. */
	static List<GlobalPos> leaderHalls(Data data, RealmData.Realm realm) {
		List<GlobalPos> out = new ArrayList<>();
		for (GlobalPos hall : Realms.members(realm)) {
			if (data.leaderHalls.contains(hall)) {
				out.add(hall);
			}
		}
		return out;
	}

	/** Whether {@code player} has had the bracelet from the realm {@code realm}. */
	public static boolean given(MinecraftServer server, UUID player, UUID realm) {
		Beats beats = Data.get(server).players.get(player);
		return beats != null && beats.given().contains(realm);
	}

	/** The villages whose Leader {@code player} has beaten. */
	public static Set<GlobalPos> beatenBy(MinecraftServer server, UUID player) {
		Beats beats = Data.get(server).players.get(player);
		return beats == null ? Set.of() : Set.copyOf(beats.beaten());
	}

	/** Whether the village at {@code hall} is known to have a Trainer Leader. */
	public static boolean hasLeader(MinecraftServer server, GlobalPos hall) {
		return Data.get(server).leaderHalls.contains(hall);
	}

	/** Forgets everything (tests). */
	public static void forget(MinecraftServer server) {
		Data data = Data.get(server);
		data.leaderHalls.clear();
		data.players.clear();
		data.setDirty();
	}

	/** A player's beaten villages, and the realms whose bracelet they have had ({@code megaBracelet}). */
	record Beats(Set<GlobalPos> beaten, Set<UUID> given) {
	}

	private record Saved(UUID player, List<GlobalPos> beaten, List<UUID> megaBracelet) {
		static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Saved::player),
			GlobalPos.CODEC.listOf().optionalFieldOf("beaten", List.of()).forGetter(Saved::beaten),
			UUIDUtil.CODEC.listOf().optionalFieldOf("megaBracelet", List.of()).forGetter(Saved::megaBracelet)
		).apply(i, Saved::new));
	}

	/** The villages with a Leader, and each player's beats ({@code aliveworkplace_leader_bracelets}, on the overworld). */
	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_leader_bracelets";
		final Set<GlobalPos> leaderHalls = new LinkedHashSet<>();
		final Map<UUID, Beats> players = new HashMap<>();

		public static Data get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			var ops = registries.createSerializationContext(NbtOps.INSTANCE);
			GlobalPos.CODEC.listOf().encodeStart(ops, List.copyOf(leaderHalls)).result().ifPresent(t -> tag.put("leaderHalls", t));
			List<Saved> saved = new ArrayList<>();
			players.forEach((id, b) -> saved.add(new Saved(id, List.copyOf(b.beaten()), List.copyOf(b.given()))));
			Saved.CODEC.listOf().encodeStart(ops, saved).result().ifPresent(t -> tag.put("players", t));
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			var ops = registries.createSerializationContext(NbtOps.INSTANCE);
			Data data = new Data();
			if (tag.get("leaderHalls") != null) {
				GlobalPos.CODEC.listOf().parse(ops, tag.get("leaderHalls")).resultOrPartial(e -> AliveWorkplace.LOG.warn("Leader villages: {}", e))
					.ifPresent(data.leaderHalls::addAll);
			}
			if (tag.get("players") != null) {
				Saved.CODEC.listOf().parse(ops, tag.get("players")).resultOrPartial(e -> AliveWorkplace.LOG.warn("Leaders beaten: {}", e))
					.ifPresent(list -> list.forEach(s -> data.players.put(s.player(),
						new Beats(new LinkedHashSet<>(s.beaten()), new LinkedHashSet<>(s.megaBracelet())))));
			}
			return data;
		}
	}
}
