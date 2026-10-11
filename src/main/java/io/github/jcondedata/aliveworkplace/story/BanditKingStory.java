package io.github.jcondedata.aliveworkplace.story;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Bandit King's story (ROADMAP 31.16), what {@code arcs/bandit_king.json} can't say in effects alone: the count of
 * bandit camps broken up near a village (its trigger), the war banners flown over the arc's three camps and taken, the
 * three allies (the neighbours' pledge, swords for hire, Pell the deserter and his postern), the storm (allies who follow
 * a Rally Banner raised near the fort, raids every second night, the King's death), the King's tribute when he outlasts
 * the storm, and the ending (Pell joins the village, the band is gone, the allies go home). Everything it keeps is in the
 * arc's flags and roles, so it carries on after a restart.
 */
public final class BanditKingStory {
	public static final ResourceLocation ARC = AliveWorkplace.id("bandit_king");
	/** The arc's place keys: its first camp (where Pell waits) and the other two ({@code camp}, {@code camp#2}). */
	public static final String FIRST_CAMP = "first_camp";
	public static final String CAMP = "camp";
	public static final String FORT = "fort";
	/** Flags. */
	public static final String BANNERS_TAKEN = "banners_taken";
	public static final String ALLY_NEIGHBOURS = "ally_neighbours";
	public static final String ALLY_MERCENARIES = "ally_mercenaries";
	public static final String ALLY_PELL = "ally_pell";
	public static final String KING_DEAD = "king_dead";
	static final String RAID_NIGHT = "raid_night";
	/** The role holding the pledging village's name (no one plays it). */
	static final String NEIGHBOURS_ROLE = "neighbours";
	public static final String PELL_ROLE = "pell";
	/** The arc's inline quests. */
	public static final ResourceLocation SWORDS = AliveWorkplace.id("arc/bandit_king/swords_for_hire");
	public static final ResourceLocation DESERTER = AliveWorkplace.id("arc/bandit_king/deserter");
	/** Every ally who comes for the storm carries this. */
	public static final String ALLY_TAG = "aliveworkplace_king_ally";
	public static final String WAR_BANNER = "block.aliveworkplace.war_banner";
	public static final int NEIGHBOUR_GUARDS = 3;
	public static final int MERCENARIES = 6;
	public static final int BREAD = 16;
	/** How near the fort a raised Rally Banner calls the allies, and how near a camp a broken war banner counts. */
	public static final int RALLY_RANGE = 48;
	static final int BANNER_RANGE = 20;
	/** Allies stay this long at most (the storm's ten days and a little). */
	static final long ALLY_STAY = 11 * 24000L;

	public static void init() {
		Lairs.onBroken((level, lair, by) -> {
			if (lair.culture().equals(BanditCamps.CULTURE)) {
				Stories.banditCampBroken(level, lair.hall());
			}
		});
		Platform.get().allowBreakBlock((level, player, pos, state) -> {
			if (level instanceof ServerLevel server) {
				onBreak(server, player, pos, state);
			}
			return true;
		});
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> onUse(player, level, hand, entity));
		Platform.get().afterDeath((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				onDeath(level, entity, source);
			}
		});
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % Arcs.CHECK_EVERY == 7) {
				for (ServerLevel level : server.getAllLevels()) {
					tick(level);
				}
			}
		});
	}

	static void validate(String step) {
		if (!List.of("storm", "tribute", "ending").contains(step)) {
			throw new IllegalArgumentException("bandit_king: 'step' must be storm, tribute or ending");
		}
	}

	/** One running Bandit King arc: its village and state. */
	public record Running(BlockPos hall, Stories.Entry entry, ArcState state) {
	}

	/** The Bandit King arcs running in {@code level}. */
	public static List<Running> running(ServerLevel level) {
		List<Running> out = new ArrayList<>();
		for (Map.Entry<BlockPos, Stories.Entry> en : Stories.Data.get(level).halls.entrySet()) {
			ArcState s = en.getValue().arc;
			if (s != null && s.id.equals(ARC.toString())) {
				out.add(new Running(en.getKey(), en.getValue(), s));
			}
		}
		return out;
	}

	@Nullable
	public static Running running(ServerLevel level, BlockPos hall) {
		return running(level).stream().filter(r -> r.hall().equals(hall)).findFirst().orElse(null);
	}

	static boolean isCamp(String key) {
		return key.equals(FIRST_CAMP) || key.equals(CAMP) || key.startsWith(CAMP + "#");
	}

	static List<ArcState.ArcPlace> camps(ArcState s) {
		return s.places.stream().filter(p -> isCamp(p.key)).toList();
	}

	/** The fort's place, if it was chosen. */
	@Nullable
	static ArcState.ArcPlace fort(ArcState s) {
		return s.place(FORT);
	}

	@Nullable
	static BanditFort.Fort fortOf(ArcState.ArcPlace p) {
		return p == null || p.origin == null ? null : new BanditFort.Fort(p.origin, Rotation.values()[Math.floorMod(p.rotation, 4)]);
	}

	private static void tell(ServerLevel level, BlockPos hall, String key, ChatFormatting colour, Object... args) {
		for (ServerPlayer player : ArcEffects.audience(level, hall)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.bandit_king." + key, args).withStyle(colour));
		}
	}

	private static void chronicle(ServerLevel level, BlockPos hall, String key, Object... args) {
		Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.bandit_king." + key, args));
	}

	// ---- chapter 1: the war banners --------------------------------------------------------------------------------

	/** The King's war banner: black, a red skull, named. */
	public static ItemStack warBanner(ServerLevel level) {
		ItemStack stack = new ItemStack(Items.BLACK_BANNER);
		var registry = Lookup.registry(level.registryAccess(), Registries.BANNER_PATTERN);
		stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
			.add(Lookup.holderOrThrow(registry, BannerPatterns.SKULL), DyeColor.RED)
			.add(Lookup.holderOrThrow(registry, BannerPatterns.BORDER), DyeColor.RED).build());
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable(WAR_BANNER).withStyle(ChatFormatting.DARK_RED));
		return stack;
	}

	/** Whether the banner at {@code pos} is a war banner of the King's. */
	public static boolean isWarBanner(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof BannerBlockEntity banner && banner.getCustomName() != null
			&& banner.getCustomName().getContents() instanceof TranslatableContents t && t.getKey().equals(WAR_BANNER);
	}

	/** A place of the arc was built: a camp flies the King's war banner by its fire. */
	static void built(ServerLevel level, ArcState s, ArcState.ArcPlace p) {
		if (!s.id.equals(ARC.toString()) || !isCamp(p.key) || p.placed != 1) {
			return;
		}
		raiseBanner(level, p.spot);
	}

	/** Sets a war banner on the first open ground beside {@code spot}; where it stands, or null. */
	@Nullable
	public static BlockPos raiseBanner(ServerLevel level, BlockPos spot) {
		for (int r = 2; r <= 6; r++) {
			for (int[] d : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}}) {
				BlockPos column = spot.offset(d[0], 0, d[1]);
				BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				if (Math.abs(at.getY() - spot.getY()) > 2 || !level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()
					|| !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
					continue;
				}
				level.setBlock(at, Blocks.BLACK_BANNER.defaultBlockState(), 3);
				if (level.getBlockEntity(at) instanceof BannerBlockEntity banner) {
					banner.fromItem(warBanner(level), DyeColor.BLACK);
					banner.setChanged();
					level.sendBlockUpdated(at, level.getBlockState(at), level.getBlockState(at), 3);
				}
				return at;
			}
		}
		return null;
	}

	/** A player breaks a block: a war banner by one of the arc's camps counts (it drops as a named banner). */
	public static void onBreak(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		if (!state.is(BlockTags.BANNERS) || !isWarBanner(level, pos)) {
			return;
		}
		for (Running r : running(level)) {
			for (ArcState.ArcPlace p : camps(r.state())) {
				if (p.placed > 0 && p.spot.distSqr(pos) <= BANNER_RANGE * BANNER_RANGE) {
					bannerTaken(level, r, p, player);
					return;
				}
			}
		}
	}

	static void bannerTaken(ServerLevel level, Running r, ArcState.ArcPlace camp, @Nullable Player player) {
		ArcState s = r.state();
		if (s.flags.containsKey("banner_" + camp.key)) {
			return;
		}
		s.flags.put("banner_" + camp.key, ArcState.FOREVER);
		if (player instanceof ServerPlayer sp) {
			s.helpers.merge(sp.getUUID(), 1, Integer::sum);
			s.chapterHelpers.merge(sp.getUUID(), 1, Integer::sum);
			s.last = sp.getUUID();
		}
		long taken = camps(s).stream().filter(p -> s.flags.containsKey("banner_" + p.key)).count();
		int of = camps(s).size();
		tell(level, r.hall(), "banner_taken", ChatFormatting.GREEN, taken, of);
		chronicle(level, r.hall(), "banner_taken", player != null ? player.getDisplayName() : Component.translatable("chronicle.aliveworkplace.someone"), taken, of);
		Stories.Data.get(level).setDirty();
		checkBanners(level, r);
	}

	/** All the camps' banners are taken (a camp never built counts as taken): the first chapter is done. */
	static void checkBanners(ServerLevel level, Running r) {
		ArcState s = r.state();
		List<ArcState.ArcPlace> camps = camps(s);
		if (s.chapter != 0 || !s.started || camps.isEmpty() || s.flags.containsKey(BANNERS_TAKEN)) {
			return;
		}
		if (camps.stream().allMatch(p -> p.skipped || s.flags.containsKey("banner_" + p.key))) {
			s.flags.put(BANNERS_TAKEN, ArcState.FOREVER);
			Stories.Data.get(level).setDirty();
			Arcs.advance(level, r.hall(), r.entry(), s);
		}
	}

	// ---- chapter 2: the allies -------------------------------------------------------------------------------------

	/** A quest was finished in the village round {@code at}: a village at war with the King within caravan range has a pledge. */
	public static void questFinished(ServerLevel level, BlockPos at) {
		if (!(level.getBlockEntity(at) instanceof VillageHallBlockEntity)) {
			return;
		}
		for (Running r : running(level)) {
			ArcState s = r.state();
			if (r.hall().equals(at) || s.chapter != 1 || s.flags.containsKey(ALLY_NEIGHBOURS)
				|| r.hall().distSqr(at) > (double) Caravans.RANGE * Caravans.RANGE) {
				continue;
			}
			Component name = VillageHalls.name(level, at);
			s.flags.put(ALLY_NEIGHBOURS, ArcState.FOREVER);
			s.roles.put(NEIGHBOURS_ROLE, new ArcState.Role(Optional.empty(), name));
			tell(level, r.hall(), "ally_neighbours", ChatFormatting.GREEN, name, NEIGHBOUR_GUARDS);
			chronicle(level, r.hall(), "ally_neighbours", name);
			Stories.Data.get(level).setDirty();
		}
	}

	/** One of the arc's quests was done: swords for hire, or Pell's bread (he tells of the postern). */
	static void arcQuestDone(ServerLevel level, ArcState s, Quest quest) {
		if (!s.id.equals(ARC.toString())) {
			return;
		}
		BlockPos hall = Stories.Data.get(level).halls.entrySet().stream().filter(en -> en.getValue().arc == s).map(Map.Entry::getKey).findFirst().orElse(null);
		if (hall == null) {
			return;
		}
		if (quest.file.equals(SWORDS)) {
			s.flags.put(ALLY_MERCENARIES, ArcState.FOREVER);
			tell(level, hall, "ally_mercenaries", ChatFormatting.GREEN, MERCENARIES);
			chronicle(level, hall, "ally_mercenaries", MERCENARIES);
		} else if (quest.file.equals(DESERTER)) {
			s.flags.put(ALLY_PELL, ArcState.FOREVER);
			s.flags.put(BanditFort.POSTERN_FLAG, ArcState.FOREVER); // the keep's postern is left open (31.15 reads it)
			BanditFort.Fort fort = fortOf(fort(s));
			if (fort != null && fort(s).placed >= BanditFort.STAGES.size()) {
				BanditFort.setPostern(level, fort, true); // the keep stands already: it's left open from now on
			}
			tell(level, hall, "ally_pell", ChatFormatting.GREEN);
			chronicle(level, hall, "ally_pell");
		}
	}

	/** A right-click on Pell: without 16 bread he asks for it; with it, the bread is his and the talk counts. */
	public static InteractionResult onUse(Player player, Level world, InteractionHand hand, Entity entity) {
		if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND
			|| !entity.getTags().contains(ArcEffects.roleTag(PELL_ROLE)) || !entity.getTags().contains(ArcEffects.arcTag(ARC.toString()))) {
			return InteractionResult.PASS;
		}
		for (Running r : running(level)) {
			ArcState s = r.state();
			boolean asking = s.quests.stream().anyMatch(q -> q.file().equals(DESERTER.toString()) && !s.done.contains(q.id()));
			if (!asking) {
				continue;
			}
			if (player.getInventory().countItem(Items.BREAD) < BREAD) {
				Chat.chat(sp, Component.translatable("message.aliveworkplace.arc.says", entity.getDisplayName(),
					Component.translatable("arc.aliveworkplace.bandit_king.pell_asks", BREAD)).withStyle(ChatFormatting.WHITE));
				return InteractionResult.SUCCESS;
			}
			int left = BREAD;
			for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(Items.BREAD)) {
					int take = Math.min(left, stack.getCount());
					stack.shrink(take);
					left -= take;
				}
			}
			return InteractionResult.PASS; // (the arc's talk now counts, and Pell says his line)
		}
		return InteractionResult.PASS;
	}

	// ---- chapter 3: the storm --------------------------------------------------------------------------------------

	/** An arc effect {@code bandit_king}: {@code storm}, {@code tribute} or {@code ending}. */
	static void effect(ServerLevel level, BlockPos hall, ArcState s, String step) {
		switch (step) {
			case "storm" -> storm(level, hall, s);
			case "tribute" -> tribute(level, hall, s);
			case "ending" -> ending(level, hall, s);
			default -> {
			}
		}
	}

	/** The storm begins: the allies won come to the hall. */
	static void storm(ServerLevel level, BlockPos hall, ArcState s) {
		if (s.flags.containsKey(ALLY_NEIGHBOURS)) {
			ArcState.Role role = s.roles.get(NEIGHBOURS_ROLE);
			Component village = role != null ? role.name() : Component.translatable("chronicle.aliveworkplace.someone");
			for (int i = 0; i < NEIGHBOUR_GUARDS; i++) {
				ally(level, hall, Component.translatable("entity.aliveworkplace.king_ally.neighbour", village), i);
			}
		}
		if (s.flags.containsKey(ALLY_MERCENARIES)) {
			for (int i = 0; i < MERCENARIES; i++) {
				ally(level, hall, Component.translatable("entity.aliveworkplace.mercenary"), i);
			}
		}
		if (s.flags.containsKey(ALLY_PELL)) {
			BanditFort.Fort fort = fortOf(fort(s));
			if (fort != null) {
				BanditFort.setPostern(level, fort, true);
			}
		}
		int allies = allies(level, hall).size();
		tell(level, hall, "storm", ChatFormatting.GOLD, allies);
		chronicle(level, hall, "storm", allies);
	}

	/** One ally for the storm: a guard in iron, at the hall, who stays until the storm is over (then goes home). */
	@Nullable
	public static Villager ally(ServerLevel level, BlockPos hall, Component name, int i) {
		Villager v = EntityType.VILLAGER.create(level);
		if (v == null) {
			return null;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.offset(i % 3 - 1, 0, 2 + i / 3));
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(3));
		v.setVillagerXp(70); // keeps the profession without a guard post
		ModAttachments.MERCENARY_UNTIL.set(v, level.getGameTime() + ALLY_STAY);
		v.setCustomName(name);
		v.addTag(ALLY_TAG);
		v.addTag(allyTag(hall));
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			v.setDropChance(slot, 0f);
		}
		level.addFreshEntityWithPassengers(v);
		Guards.updateHealth(v);
		level.sendParticles(ParticleTypes.CLOUD, v.getX(), v.getY() + 0.5, v.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		return v;
	}

	static String allyTag(BlockPos hall) {
		return ALLY_TAG + "." + hall.asLong();
	}

	/** The storm's allies of the village round {@code hall} about now. */
	public static List<Villager> allies(ServerLevel level, BlockPos hall) {
		String tag = allyTag(hall);
		return level.getEntitiesOfClass(Villager.class, new AABB(hall).inflate(BanditFort.FAR + 64, 96, BanditFort.FAR + 64),
			v -> v.isAlive() && v.getTags().contains(tag));
	}

	/** The allies go home: they leave at the next villager tick. */
	static void sendHome(ServerLevel level, BlockPos hall) {
		for (Villager v : allies(level, hall)) {
			ModAttachments.MERCENARY_UNTIL.set(v, level.getGameTime());
		}
	}

	/** Every 40 ticks: banners counted, raids every second night of the storm, the allies under a raised Rally Banner. */
	public static void tick(ServerLevel level) {
		for (Running r : running(level)) {
			ArcState s = r.state();
			checkBanners(level, r);
			if (s.chapter != 2 || !s.started || s.flags.containsKey(KING_DEAD)) {
				continue;
			}
			raidNight(level, r);
			ArcState.ArcPlace fort = fort(s);
			if (fort == null) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (player.blockPosition().distSqr(fort.spot) <= (double) RALLY_RANGE * RALLY_RANGE) {
					follow(level, r.hall(), player);
				}
			}
		}
	}

	/** {@code player} raised a Rally Banner near the fort: the allies are enlisted under it and follow. */
	public static void follow(ServerLevel level, BlockPos hall, ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!(stack.getItem() instanceof RallyBannerItem)) {
				continue;
			}
			RallyBannerItem.Rally rally = RallyBannerItem.rally(stack);
			if (!rally.raised()) {
				continue;
			}
			List<UUID> guards = new ArrayList<>(rally.guards());
			boolean added = false;
			for (Villager ally : allies(level, hall)) {
				if (!guards.contains(ally.getUUID())) {
					guards.add(ally.getUUID());
					added = true;
				}
			}
			if (added) {
				stack.set(ModComponents.RALLY, new RallyBannerItem.Rally(List.copyOf(guards), true));
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.bandit_king.allies_follow").withStyle(ChatFormatting.GOLD));
			}
			return;
		}
	}

	/** Every second night of the storm (its first, third...), the King's raiders come: the village's bandit raid, half again as big. */
	static void raidNight(ServerLevel level, Running r) {
		ArcState s = r.state();
		long today = Chronicle.day(level);
		if ((today - s.chapterDay) % 2 != 0 || level.getDayTime() % 24000 < VillageRaids.DUSK || s.flag(RAID_NIGHT, today)
			|| !VillageRaids.ENABLED || VillageRaids.active(r.hall()).isPresent()) {
			return;
		}
		s.flags.put(RAID_NIGHT, today);
		Stories.Data.get(level).setDirty();
		raid(level, r.hall());
	}

	/** The King's raid on the village round {@code hall}: bandits, sized as for half again as many villagers. */
	@Nullable
	public static VillageRaids.Raid raid(ServerLevel level, BlockPos hall) {
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive);
		int guards = (int) villagers.stream().filter(Guards::isGuard).count();
		var culture = Threats.get(Threats.BANDITS).orElse(null);
		if (culture == null) {
			return null;
		}
		// The plain raid is 3 + villagers / 4; half again as big is 3 + (6 + 1.5 * villagers) / 4.
		VillageRaids.Raid raid = VillageRaids.start(level, hall, 6 + villagers.size() * 3 / 2, guards, Double.NaN, culture, level.random);
		if (raid != null) {
			tell(level, hall, "raid", ChatFormatting.RED);
			chronicle(level, hall, "raid");
		}
		return raid;
	}

	/** Every death: the King's (the storm is won), and the fort's men a player kills (they fought in it). */
	static void onDeath(ServerLevel level, LivingEntity entity, DamageSource source) {
		boolean king = entity.getTags().contains(BanditKing.TAG);
		if (!king && !entity.getTags().contains(BanditFort.TAG)) {
			return;
		}
		for (Running r : running(level)) {
			ArcState s = r.state();
			ArcState.ArcPlace fort = fort(s);
			if (fort == null || fort.spot.distSqr(entity.blockPosition()) > 64 * 64) {
				continue;
			}
			ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;
			if (killer != null) {
				s.helpers.merge(killer.getUUID(), 1, Integer::sum);
				s.chapterHelpers.merge(killer.getUUID(), 1, Integer::sum);
				s.last = killer.getUUID();
			}
			if (king && s.chapter == 2) {
				for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, entity.getBoundingBox().inflate(RALLY_RANGE), ServerPlayer::isAlive)) {
					s.chapterHelpers.merge(p.getUUID(), 1, Integer::sum); // everyone at the storm fought in it
					s.helpers.merge(p.getUUID(), 1, Integer::sum);
				}
				s.flags.put(KING_DEAD, ArcState.FOREVER);
				chronicle(level, r.hall(), "king_falls", killer != null ? killer.getDisplayName() : Component.translatable("chronicle.aliveworkplace.someone"));
				Stories.Data.get(level).setDirty();
				Arcs.advance(level, r.hall(), r.entry(), s);
			}
			Stories.Data.get(level).setDirty();
			return;
		}
	}

	// ---- the endings -----------------------------------------------------------------------------------------------

	/** The King outlasted the storm: he takes a third of the treasury and rides off with his band; the fort is left a ruin. */
	static void tribute(ServerLevel level, BlockPos hall, ArcState s) {
		int taken = 0;
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			taken = entity.treasury() / 3;
			entity.setTreasury(entity.treasury() - taken);
		}
		ArcState.ArcPlace fort = fort(s);
		if (fort != null) {
			for (Mob man : BanditFort.men(level, fort.spot, 64)) {
				level.sendParticles(ParticleTypes.POOF, man.getX(), man.getY() + 0.5, man.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
				man.discard();
			}
			ruin(level, fort);
		}
		sendHome(level, hall);
		level.playSound(null, hall, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1f, 0.7f);
		tell(level, hall, "tribute", ChatFormatting.RED, taken / 100);
		chronicle(level, hall, "tribute", taken / 100);
	}

	/** The fort is left a ruin: a share of what it was built of falls (fixed by where it stands, so it reads the same twice). */
	static void ruin(ServerLevel level, ArcState.ArcPlace fort) {
		if (fort.placed == 0 || fort.origin == null) {
			return;
		}
		RandomSource random = RandomSource.create(fort.spot.asLong());
		BlockPos c = fort.spot;
		int half = BanditFort.HALF + 1;
		for (BlockPos p : BlockPos.betweenClosed(c.getX() - half, c.getY() + 1, c.getZ() - half, c.getX() + half, c.getY() + BanditFort.HEIGHT, c.getZ() + half)) {
			BlockState state = level.getBlockState(p);
			if (state.isAir() || state.is(BlockTags.DIRT) || state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
				continue;
			}
			if (state.is(Blocks.CAMPFIRE) || random.nextFloat() < 0.4f) {
				level.setBlock(p, state.is(Blocks.CAMPFIRE) || random.nextInt(4) > 0 ? Blocks.AIR.defaultBlockState() : Blocks.COBWEB.defaultBlockState(), 2);
			}
		}
	}

	/** The King fell: his band is gone (the treasure chest is the village's), Pell joins the village, the allies go home. */
	static void ending(ServerLevel level, BlockPos hall, ArcState s) {
		ArcState.ArcPlace fort = fort(s);
		if (fort != null) {
			for (Mob man : BanditFort.men(level, fort.spot, 64)) {
				level.sendParticles(ParticleTypes.POOF, man.getX(), man.getY() + 0.5, man.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
				man.discard();
			}
		}
		if (s.flags.containsKey(ALLY_PELL)) {
			pellJoins(level, hall, s);
		}
		sendHome(level, hall);
		chronicle(level, hall, "ending");
	}

	/** Pell stays: no longer the arc's, a villager of the village with no job, at the hall. */
	@Nullable
	static Villager pellJoins(ServerLevel level, BlockPos hall, ArcState s) {
		ArcState.Role role = s.roles.get(PELL_ROLE);
		Entity found = role == null ? null : role.who().map(level::getEntity).orElse(null);
		s.mobs.removeIf(m -> m.key.equals(PELL_ROLE)); // (so the arc's end doesn't send him off)
		s.roles.remove(PELL_ROLE);
		if (!(found instanceof Villager pell) || !pell.isAlive()) {
			return null;
		}
		for (String tag : List.copyOf(pell.getTags())) {
			if (tag.startsWith(Arcs.TAG) || tag.startsWith("aliveworkplace_role_")) {
				pell.removeTag(tag);
			}
		}
		pell.clearRestriction();
		pell.setVillagerData(pell.getVillagerData().setProfession(VillagerProfession.NONE));
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.offset(2, 0, -2));
		pell.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pell.getX(), pell.getY() + 1, pell.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
		tell(level, hall, "pell_joins", ChatFormatting.GREEN, pell.getDisplayName());
		chronicle(level, hall, "pell_joins", pell.getDisplayName());
		return pell;
	}

	private BanditKingStory() {
	}
}
