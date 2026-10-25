package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Marching on the lair (ROADMAP 32.13), from the Defence page.
 * <ul>
 * <li>{@link #muster}: three in four of the village's guards (at most {@link RallyBannerItem#MAX_GUARDS}; the rest keep
 * the walls) are enlisted on the Rally Banner in the player's inventory, it is raised and they gather by the hall.</li>
 * <li>{@link #hire}: the Siege-breakers, {@link #BAND} hired swords (two crossbows, two knights, a medic) for
 * {@link #PRICE_EMERALDS} emeralds, 3 less a level of Commerce. They follow the player who hired them and stay until the
 * second dawn, or the dawn after the lair falls if that comes first.</li>
 * <li>At the lair the band turns out to meet a war party, {@link #WAVE} at a time ({@link Lairs#turnOut}).</li>
 * <li>Guards earn double experience for kills at a lair. When a lair breaks with a war party there: the chronicle names
 * the player and every guard who killed there, none comes for {@link #REST_DAYS} days instead of 5, and the village is
 * {@link #MOOD} happier for two days ("the warband is broken").</li>
 * </ul>
 * Open while lairs are {@link Lairs#live}. Who is enlisted lives on the banner; the siege-breakers' stay on them
 * ({@code MERCENARY_UNTIL}) and their tags; the kills and the good mood in {@link Data}.
 */
public final class WarParties {
	/** Of every four guards, three march. */
	public static final int SHARE = 3;
	public static final int BAND = 5;
	public static final int PRICE_EMERALDS = 30;
	/** How many of a lair's band turn out at once. */
	public static final int WAVE = 6;
	/** How near a lair a war party is "at" it. */
	public static final double AT_LAIR = 48;
	public static final int REST_DAYS = 10;
	public static final int MOOD = 10;
	public static final long MOOD_TICKS = 48000;
	static final int SCAN = 20;
	public static final String SIEGE_TAG = "aliveworkplace_siege_breaker";
	static final String HIRED_BY = "aliveworkplace_hired_by.";

	public static void init() {
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % SCAN == 7) {
				tick(server);
			}
		});
		Lairs.onBroken(WarParties::broken);
	}

	// The muster.

	public enum Muster { MUSTERED, NO_BANNER, NO_GUARDS, CLOSED }

	/** The guards who could march from the village round {@code hall}: three in four of them, at most 12, nearest first. */
	public static List<Villager> marchers(ServerLevel level, BlockPos hall) {
		List<Villager> guards = new ArrayList<>(level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall),
			v -> v.isAlive() && Guards.isGuard(v) && !Mercenaries.isMercenary(v) && !Scouts.out(v)));
		guards.sort(Comparator.comparingDouble(v -> v.distanceToSqr(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5)));
		int marching = Math.min(RallyBannerItem.MAX_GUARDS, guards.size() * SHARE / 4);
		return List.copyOf(guards.subList(0, marching));
	}

	/** The first Rally Banner in {@code player}'s inventory, or an empty stack. */
	public static ItemStack banner(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(ModItems.RALLY_BANNER)) {
				return inventory.getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}

	/** {@code player} musters a war party at the hall at {@code hall}. */
	public static Muster muster(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!Lairs.live()) {
			return Muster.CLOSED;
		}
		ItemStack banner = banner(player);
		if (banner.isEmpty()) {
			return Muster.NO_BANNER;
		}
		List<Villager> marching = marchers(level, hall);
		if (marching.isEmpty()) {
			return Muster.NO_GUARDS;
		}
		List<UUID> ids = new ArrayList<>(marching.stream().map(Villager::getUUID).toList());
		// Siege-breakers already on the banner stay on it while there's room.
		for (UUID kept : RallyBannerItem.rally(banner).guards()) {
			if (!ids.contains(kept) && ids.size() < RallyBannerItem.MAX_GUARDS && level.getEntity(kept) instanceof Villager v && isSiegeBreaker(v)) {
				ids.add(kept);
			}
		}
		banner.set(ModComponents.RALLY, new RallyBannerItem.Rally(List.copyOf(ids), true));
		for (Villager guard : marching) {
			BlockPos spot = Walker.standingSpot(level, hall, hall.south(2), 6.0);
			guard.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot != null ? spot : hall, 0.7f, 2));
		}
		level.playSound(null, hall, SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
		Escorts.scan(level.getServer());
		return Muster.MUSTERED;
	}

	/** What to tell {@code player} after {@link #muster}. */
	public static Component said(ServerLevel level, BlockPos hall, Muster result, int guards) {
		return switch (result) {
			case MUSTERED -> {
				int all = (int) level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && Guards.isGuard(v) && !Mercenaries.isMercenary(v)).size();
				int staying = Math.max(0, all - guards);
				yield Words.countedTwo("message.aliveworkplace.war_party.mustered", guards, staying, guards, staying).withStyle(ChatFormatting.GOLD);
			}
			case NO_BANNER -> Component.translatable("message.aliveworkplace.war_party.no_banner").withStyle(ChatFormatting.YELLOW);
			case NO_GUARDS -> Component.translatable("message.aliveworkplace.war_party.no_guards").withStyle(ChatFormatting.YELLOW);
			case CLOSED -> Component.empty();
		};
	}

	// The Siege-breakers.

	public static boolean isSiegeBreaker(Villager villager) {
		return villager.getTags().contains(SIEGE_TAG) && Mercenaries.isMercenary(villager);
	}

	/** The player a siege-breaker follows: the one who hired them. */
	public static Optional<UUID> hirer(Villager villager) {
		for (String tag : villager.getTags()) {
			if (tag.startsWith(HIRED_BY)) {
				try {
					return Optional.of(UUID.fromString(tag.substring(HIRED_BY.length())));
				} catch (IllegalArgumentException e) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	/** What the Siege-breakers cost at the hall at {@code hall}: 3 emeralds less a level of Commerce. */
	public static int price(ServerLevel level, BlockPos hall) {
		return Math.max(1, PRICE_EMERALDS - 3 * Research.at(level, hall).level(Research.Topic.COMMERCE));
	}

	/** Ticks from now to the next dawn (a whole day at dawn itself). */
	static long toDawn(ServerLevel level) {
		long left = (24000 - level.getDayTime() % 24000) % 24000;
		return left == 0 ? 24000 : left;
	}

	/** {@code player} hires the Siege-breakers at the hall at {@code hall}; returns what to tell them. */
	public static Component hire(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!Mercenaries.near(level, hall).isEmpty()) {
			return Component.translatable("message.aliveworkplace.mercenaries.already").withStyle(ChatFormatting.YELLOW);
		}
		int price = price(level, hall);
		long dollars = (long) price * Money.DOLLARS_PER_EMERALD;
		if (!Money.charge(player, dollars, price)) {
			return Component.translatable("message.aliveworkplace.siege_breakers.cant_afford", Money.describe(dollars, price)).withStyle(ChatFormatting.RED);
		}
		long until = level.getGameTime() + toDawn(level) + 24000; // the second dawn
		ItemStack banner = banner(player);
		List<UUID> joined = new ArrayList<>();
		for (int i = 0; i < BAND; i++) {
			Villager merc = EntityType.VILLAGER.create(level);
			if (merc == null) {
				continue;
			}
			BlockPos spot = Walker.standingSpot(level, hall, hall.offset(i - 2, 0, 2), 6.0);
			BlockPos at = spot != null ? spot : hall.above();
			merc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
			merc.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
			merc.setVillagerData(merc.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(3));
			merc.setVillagerXp(70); // keeps the profession without a guard post
			merc.refreshBrain(level); // a guard's brain: fighting in its CORE activity (B117)
			ModAttachments.MERCENARY_UNTIL.set(merc, until);
			merc.addTag(SIEGE_TAG);
			merc.addTag(HIRED_BY + player.getUUID());
			merc.setCustomName(Component.translatable("entity.aliveworkplace.siege_breaker"));
			equip(level, hall, merc, i);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				merc.setDropChance(slot, 0f);
			}
			level.addFreshEntityWithPassengers(merc);
			Guards.updateHealth(merc);
			level.sendParticles(ParticleTypes.CLOUD, merc.getX(), merc.getY() + 0.5, merc.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
			joined.add(merc.getUUID());
		}
		// On the player's Rally Banner too, while it has room.
		if (!banner.isEmpty()) {
			RallyBannerItem.Rally rally = RallyBannerItem.rally(banner);
			List<UUID> guards = new ArrayList<>(rally.guards());
			for (UUID id : joined) {
				if (guards.size() < RallyBannerItem.MAX_GUARDS) {
					guards.add(id);
				}
			}
			banner.set(ModComponents.RALLY, new RallyBannerItem.Rally(List.copyOf(guards), rally.raised()));
		}
		level.playSound(null, hall, SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 0.6f, 1.0f);
		Chronicle.record(level, hall, Chronicle.Kind.RAID, Component.translatable("chronicle.aliveworkplace.siege_breakers", player.getDisplayName()));
		Escorts.scan(level.getServer());
		return Component.translatable("message.aliveworkplace.siege_breakers.hired", BAND).withStyle(ChatFormatting.GREEN);
	}

	/** Two crossbows, two knights and a medic, all in iron; the knights' shields in the village's colours. */
	private static void equip(ServerLevel level, BlockPos hall, Villager merc, int i) {
		merc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		merc.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		merc.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		switch (i) {
			case 0, 1 -> merc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.CROSSBOW));
			case 2, 3 -> {
				merc.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				merc.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
				ItemStack shield = new ItemStack(Items.SHIELD);
				VillageBanners.Colours colours = VillageBanners.of(level, hall);
				if (colours != null) {
					VillageBanners.paint(shield, colours);
				}
				merc.setItemSlot(EquipmentSlot.OFFHAND, shield);
			}
			default -> {
				merc.setItemSlot(EquipmentSlot.OFFHAND, PotionContents.createItemStack(Items.POTION, Potions.HEALING));
				for (int p = 0; p < 4; p++) {
					ModAttachments.BUILDER_BAG.getOrCreate(merc).add(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
				}
			}
		}
	}

	// At the lair.

	/** Whether {@code villager} marches: a guard following a raised Rally Banner, or a siege-breaker. */
	public static boolean marching(Villager villager) {
		return villager.isAlive() && (Escorts.isEscorting(villager) || isSiegeBreaker(villager));
	}

	/** The war party at {@code lair} (within {@link #AT_LAIR} blocks of it), the players leading it among them. */
	public static List<LivingEntity> at(ServerLevel level, Lairs.Lair lair) {
		List<LivingEntity> party = new ArrayList<>(level.getEntitiesOfClass(Villager.class, new AABB(lair.pos()).inflate(AT_LAIR, 24, AT_LAIR), WarParties::marching));
		List<LivingEntity> leaders = new ArrayList<>();
		for (LivingEntity member : party) {
			if (member instanceof Villager v) {
				Escorts.leader(v).filter(p -> !leaders.contains(p) && p.distanceToSqr(lair.pos().getCenter()) < AT_LAIR * AT_LAIR).ifPresent(leaders::add);
			}
		}
		party.addAll(leaders);
		return party;
	}

	/** Every second: a lair with a war party at it turns its band out to meet it. */
	public static void tick(MinecraftServer server) {
		if (!Lairs.live()) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Lairs.Lair lair : Lairs.all(level)) {
				if (!level.isLoaded(lair.pos())) {
					continue;
				}
				List<LivingEntity> party = at(level, lair);
				if (!party.isEmpty()) {
					Lairs.turnOut(level, lair, WAVE, party);
				}
			}
		}
	}

	/**
	 * Every death: a guard who kills one of a lair's band at the lair earns the kill's experience again, and is remembered
	 * for the chronicle. A kill by a guard who marches (on a raised banner, or a siege-breaker) also marks a war party as
	 * having been there, so a party that lost every guard still earns its rewards; a lone guard on watch doesn't (B418).
	 */
	public static void onDeath(ServerLevel level, LivingEntity dead, DamageSource source) {
		if (!Lairs.live() || !dead.getTags().contains(Lairs.TAG) || dead.getTags().contains(Threats.RAIDER_TAG)
				|| !(source.getEntity() instanceof Villager guard) || !Guards.isGuard(guard)) {
			return;
		}
		for (Lairs.Lair lair : Lairs.all(level)) {
			if (lair.pos().closerToCenterThan(dead.position(), AT_LAIR)) {
				BuilderLevels.addXp(level, guard, GuardCombat.XP_PER_KILL, null); // double: the kill already paid once
				Data.get(level).killed(lair.hall(), guard.getDisplayName().getString(), marching(guard));
				return;
			}
		}
	}

	/**
	 * A lair broken: with a war party there (or war-party guards who killed there), the war party's rewards. A kill by a
	 * guard who wasn't marching names him in the chronicle of a war party's break but makes no war party (B418).
	 */
	static void broken(ServerLevel level, Lairs.Lair lair, @Nullable Entity by) {
		if (!Lairs.live()) {
			return;
		}
		Data data = Data.get(level);
		List<String> killers = data.killers.getOrDefault(lair.hall(), List.of());
		boolean marched = data.marched.contains(lair.hall());
		List<LivingEntity> party = at(level, lair);
		data.forget(lair.hall());
		if (party.isEmpty() && !marched) {
			return;
		}
		Lairs.rest(level, lair.hall(), REST_DAYS);
		data.cheer(lair.hall(), level.getGameTime() + MOOD_TICKS);
		io.github.jcondedata.aliveworkplace.people.Moods.forget();
		// The siege-breakers leave at the next dawn.
		long dawn = level.getGameTime() + toDawn(level);
		java.util.Set<Villager> mercs = new java.util.LinkedHashSet<>(level.getEntitiesOfClass(Villager.class, VillageHalls.area(lair.hall()), WarParties::isSiegeBreaker));
		mercs.addAll(level.getEntitiesOfClass(Villager.class, new AABB(lair.pos()).inflate(AT_LAIR, 24, AT_LAIR), WarParties::isSiegeBreaker));
		for (Villager merc : mercs) {
			Long until = ModAttachments.MERCENARY_UNTIL.get(merc);
			if (until == null || until > dawn) {
				ModAttachments.MERCENARY_UNTIL.set(merc, dawn);
			}
		}
		Component leader = leader(level, party, by);
		Component cultureName = Lairs.named(lair) ? Lairs.captainName(lair) : Lairs.cultureName(lair.culture());
		Chronicle.record(level, lair.hall(), Chronicle.Kind.RAID, killers.isEmpty()
			? Component.translatable("chronicle.aliveworkplace.war_party.broken_none", leader, cultureName)
			: Component.translatable("chronicle.aliveworkplace.war_party.broken", leader, cultureName, String.join(", ", killers)));
		Component peace = Component.translatable("message.aliveworkplace.war_party.broken", VillageHalls.name(level, lair.hall()), REST_DAYS)
			.withStyle(ChatFormatting.GREEN);
		java.util.Set<ServerPlayer> breakers = new java.util.LinkedHashSet<>();
		if (by instanceof ServerPlayer striker) {
			breakers.add(striker);
		}
		for (LivingEntity member : party) {
			if (member instanceof ServerPlayer player) {
				io.github.jcondedata.aliveworkplace.mc.Chat.chat(player, peace);
				breakers.add(player);
			}
		}
		// 36.3: the questbook's "Take the fight to them" completes for the player who struck the last blow and the party.
		io.github.jcondedata.aliveworkplace.hall.VillageMoments.grant(level, io.github.jcondedata.aliveworkplace.hall.VillageMoments.CAMP_BROKEN, breakers);
	}

	/** Who led the war party: the player who struck the last blow, else whoever the banner or the siege-breakers follow. */
	private static Component leader(ServerLevel level, List<LivingEntity> party, @Nullable Entity by) {
		if (by instanceof ServerPlayer player) {
			return player.getDisplayName();
		}
		for (LivingEntity member : party) {
			if (member instanceof ServerPlayer player) {
				return player.getDisplayName();
			}
		}
		for (LivingEntity member : party) {
			if (member instanceof Villager v) {
				Optional<UUID> hirer = hirer(v);
				if (hirer.isPresent() && level.getPlayerByUUID(hirer.get()) != null) {
					return level.getPlayerByUUID(hirer.get()).getDisplayName();
				}
			}
		}
		return Component.translatable("chronicle.aliveworkplace.war_party.the_village");
	}

	/** The mood of a villager whose village broke a warband in the last two days, or null. */
	@Nullable
	public static LegendPowers.MoodReason mood(ServerLevel level, Villager villager) {
		Map<BlockPos, Long> cheer = Data.get(level).cheer;
		if (cheer.isEmpty()) {
			return null;
		}
		long now = level.getGameTime();
		for (Map.Entry<BlockPos, Long> e : cheer.entrySet()) {
			if (e.getValue() > now && VillageHalls.area(e.getKey()).contains(villager.position())) {
				return new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.warband_broken"), MOOD);
			}
		}
		return null;
	}

	/** The guards who killed at the lair of the village round {@code hall}, by name (tests, the page). */
	public static List<String> killers(ServerLevel level, BlockPos hall) {
		return List.copyOf(Data.get(level).killers.getOrDefault(hall, List.of()));
	}

	/** Forgets every kill and good mood (tests). */
	public static void forget(ServerLevel level) {
		Data data = Data.get(level);
		data.killers.clear();
		data.marched.clear();
		data.cheer.clear();
		data.setDirty();
	}

	/**
	 * The guards who killed at each village's lair (by name, once each), the villages whose war party killed there, and
	 * how long each village is glad.
	 */
	public static final class Data extends SavedData {
		public static final String NAME = "aliveworkplace_war_parties";
		final Map<BlockPos, List<String>> killers = new LinkedHashMap<>();
		final Map<BlockPos, Long> cheer = new LinkedHashMap<>();
		/** The halls whose lair saw a kill by a marching guard (B418). */
		final java.util.Set<BlockPos> marched = new java.util.LinkedHashSet<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		void killed(BlockPos hall, String name, boolean marching) {
			List<String> names = killers.computeIfAbsent(hall.immutable(), h -> new ArrayList<>());
			if (!names.contains(name)) {
				names.add(name);
				setDirty();
			}
			if (marching && marched.add(hall.immutable())) {
				setDirty();
			}
		}

		void forget(BlockPos hall) {
			if (killers.remove(hall) != null | marched.remove(hall)) {
				setDirty();
			}
		}

		void cheer(BlockPos hall, long until) {
			cheer.put(hall.immutable(), until);
			setDirty();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			for (Map.Entry<BlockPos, List<String>> e : killers.entrySet()) {
				CompoundTag c = new CompoundTag();
				c.putLong("hall", e.getKey().asLong());
				ListTag names = new ListTag();
				e.getValue().forEach(n -> names.add(StringTag.valueOf(n)));
				c.put("names", names);
				list.add(c);
			}
			tag.put("killers", list);
			ListTag glad = new ListTag();
			for (Map.Entry<BlockPos, Long> e : cheer.entrySet()) {
				CompoundTag c = new CompoundTag();
				c.putLong("hall", e.getKey().asLong());
				c.putLong("until", e.getValue());
				glad.add(c);
			}
			tag.put("cheer", glad);
			tag.putLongArray("marched", marched.stream().mapToLong(BlockPos::asLong).toArray());
			return tag;
		}

		static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			data.read(tag);
			return data;
		}

		/** Replaces what this holds with what {@code tag} holds; anything missing is empty. */
		public void read(CompoundTag tag) {
			killers.clear();
			marched.clear();
			cheer.clear();
			ListTag list = Nbt.getList(tag, "killers", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag c = Nbt.compoundAt(list, i);
				ListTag names = Nbt.getList(c, "names", Tag.TAG_STRING);
				List<String> out = new ArrayList<>();
				for (int n = 0; n < names.size(); n++) {
					out.add(names.getString(n));
				}
				killers.put(BlockPos.of(Nbt.getLong(c, "hall")), out);
			}
			ListTag glad = Nbt.getList(tag, "cheer", Tag.TAG_COMPOUND);
			for (int i = 0; i < glad.size(); i++) {
				CompoundTag c = Nbt.compoundAt(glad, i);
				cheer.put(BlockPos.of(Nbt.getLong(c, "hall")), Nbt.getLong(c, "until"));
			}
			// Saved before B418, a kill there meant a war party there: keep every village with kills marked.
			if (tag.contains("marched")) {
				for (long hall : Nbt.getLongArray(tag, "marched")) {
					marched.add(BlockPos.of(hall));
				}
			} else {
				marched.addAll(killers.keySet());
			}
			setDirty();
		}

		/** Saves this and reads it back, as a restart does (tests). */
		public void roundTrip(HolderLookup.Provider registries) {
			read(save(new CompoundTag(), registries));
		}
	}

	private WarParties() {
	}
}
