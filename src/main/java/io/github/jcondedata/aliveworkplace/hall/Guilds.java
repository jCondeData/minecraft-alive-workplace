package io.github.jcondedata.aliveworkplace.hall;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Guilds (ROADMAP 30.17, docs/design/M30.md): a Master given a Guild Charter (sneak-right-click) leads their trade's
 * guild in a village of Village rank or more, one guild per trade and {@link #PER_RANK} per rank above Hamlet. Guilds
 * are data ({@code data/<namespace>/guilds/<id>.json}: name, icon, trades and perks as {@link CivicEffects}), so a pack
 * adds its own or switches ours off with {@code "enabled": false}.
 *
 * <p>A guild is founded once a finished Guildhall (any build whose id starts with {@code guildhall}) stands in the
 * village: the oldest chartered guild without one claims the next one finished, and each needs its own. Until then,
 * and while its Guildhall is no longer finished, its perks wait. Perks reach only members (villagers of its trades in
 * the village). When the Guild Master dies, the most experienced member takes over; with none left, the guild is
 * dissolved. Charters live on the hall ({@code guilds}); the master carries {@link ModAttachments#GUILD_MASTER}.
 */
public final class Guilds implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("guilds");
	public static final String FOLDER = "guilds";
	/** {@code guilds} in the config: off, charters are refused and perks are off (guilds stay saved). */
	public static boolean ENABLED = true;
	/** {@code guildsPerRank} in the config: guilds per rank above Hamlet. */
	public static int PER_RANK = 1;
	/** The most helpers any {@code build_helpers} effect may allow (its codec's cap). */
	public static final int MOST_HELPERS = 8;

	public static final ResourceLocation BUILD_HELPERS = AliveWorkplace.id("build_helpers");

	/** {@code build_helpers}: up to {@code max} idle builders help at one build (not {@link Builders#MAX_HELPERS}). */
	public record BuildHelpers(int max, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return BUILD_HELPERS;
		}
	}

	static {
		CivicEffects.register(BUILD_HELPERS, RecordCodecBuilder.<BuildHelpers>mapCodec(i -> i.group(
			Codec.intRange(1, MOST_HELPERS).fieldOf("max").forGetter(BuildHelpers::max),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(BuildHelpers::jobs)
		).apply(i, BuildHelpers::new)));
	}

	/** One guild: its id (the file's), icon, name, the trades (profession ids) it gathers and its perks. */
	public record Guild(ResourceLocation id, ResourceLocation icon, Component name, Component perk, List<ResourceLocation> trades,
						List<CivicEffects.Effect> perks) {
		public boolean gathers(Villager villager) {
			return trades.contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()));
		}
	}

	/** A guild chartered in a village: its id, master (id and name), the day chartered and its Guildhall's origin. */
	public record Charter(ResourceLocation id, UUID master, String masterName, long day, Optional<BlockPos> guildhall) {
		public static final Codec<Charter> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("id").forGetter(Charter::id),
			UUIDUtil.CODEC.fieldOf("master").forGetter(Charter::master),
			Codec.STRING.optionalFieldOf("masterName", "").forGetter(Charter::masterName),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Charter::day),
			BlockPos.CODEC.optionalFieldOf("guildhall").forGetter(Charter::guildhall)
		).apply(i, Charter::new));

		Charter withMaster(UUID who, String name) {
			return new Charter(id, who, name, day, guildhall);
		}

		Charter withGuildhall(BlockPos origin) {
			return new Charter(id, master, masterName, day, Optional.of(origin));
		}
	}

	/** What the {@code guild_master} attachment holds: the guild and the hall of its village. */
	public record Master(ResourceLocation guild, BlockPos hall) {
		public static final Codec<Master> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("guild").forGetter(Master::guild),
			BlockPos.CODEC.fieldOf("hall").forGetter(Master::hall)
		).apply(i, Master::new));
	}

	/** How a charter offered to a villager went. */
	public enum Outcome { GRANTED, DISABLED, NO_HALL, NOT_A_MASTER, NO_GUILD, HAMLET, FULL, TAKEN, ALREADY }

	public record Offer(Outcome outcome, Component message) {
	}

	private record Body(ResourceLocation icon, Component name, Component perk, List<ResourceLocation> trades, List<CivicEffects.Effect> perks) {
	}

	private static final MapCodec<Body> BODY = RecordCodecBuilder.mapCodec(i -> i.group(
		ResourceLocation.CODEC.optionalFieldOf("icon", ResourceLocation.withDefaultNamespace("paper")).forGetter(Body::icon),
		ComponentSerialization.CODEC.fieldOf("name").forGetter(Body::name),
		ComponentSerialization.CODEC.optionalFieldOf("perk", Component.empty()).forGetter(Body::perk),
		ResourceLocation.CODEC.listOf().fieldOf("trades").forGetter(Body::trades),
		CivicEffects.CODEC.listOf().optionalFieldOf("perks", List.of()).forGetter(Body::perks)
	).apply(i, Body::new));

	private static volatile Map<ResourceLocation, Guild> guilds = Map.of();
	/** The guilds founded in each village (worked out at the hall's round and whenever charters change; not saved). */
	private static final Map<VillageHallBlockEntity, Set<ResourceLocation>> FOUNDED = new WeakHashMap<>();

	public static void init() {
		Platform.get().onDataReload(ID, new Guilds());
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> use(player, level, hand, entity));
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")));
	}

	/** Reads every guild file (the top one per path); a broken file is skipped with a warning. */
	public static void load(Map<ResourceLocation, Resource> files) {
		List<Guild> found = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
			String path = e.getKey().getPath();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),
				path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
			try (Reader reader = e.getValue().openAsReader()) {
				Guild guild = read(id, JsonParser.parseReader(reader));
				if (guild != null) {
					found.add(guild);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping guild {}: {}", e.getKey(), ex.getMessage());
			}
		}
		found.sort(Comparator.comparing(g -> g.id().toString()));
		Map<ResourceLocation, Guild> out = new LinkedHashMap<>();
		found.forEach(g -> out.put(g.id(), g));
		guilds = Collections.unmodifiableMap(out);
	}

	/** Reads one guild; null if it is switched off ({@code "enabled": false}); throws naming the bad field. */
	@Nullable
	public static Guild read(ResourceLocation id, JsonElement json) {
		JsonObject object = json.getAsJsonObject();
		if (object.has("enabled") && !object.get("enabled").getAsBoolean()) {
			return null;
		}
		Body body = BODY.codec().parse(JsonOps.INSTANCE, object).getOrThrow(IllegalArgumentException::new);
		return new Guild(id, body.icon(), body.name(), body.perk(), body.trades(), body.perks());
	}

	/** Every guild there is. */
	public static Map<ResourceLocation, Guild> all() {
		return guilds;
	}

	@Nullable
	public static Guild get(ResourceLocation id) {
		return guilds.get(id);
	}

	/** The guild that gathers {@code villager}'s trade, or null. */
	@Nullable
	public static Guild forTrade(Villager villager) {
		for (Guild g : guilds.values()) {
			if (g.gathers(villager)) {
				return g;
			}
		}
		return null;
	}

	/** Guilds a village of {@code rank} may have: {@link #PER_RANK} per rank above Hamlet. */
	public static int cap(VillageRanks.Rank rank) {
		return rank.ordinal() * PER_RANK;
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator() || !(entity instanceof Villager villager)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(ModItems.GUILD_CHARTER)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		offer((ServerPlayer) player, villager, held);
		return InteractionResult.SUCCESS;
	}

	/** {@code player} grants {@code villager} a charter: used up (unless in creative) when granted, else refused with the reason. */
	public static Offer offer(ServerPlayer player, Villager villager, ItemStack stack) {
		Offer offer = grant((ServerLevel) villager.level(), villager);
		if (offer.outcome() == Outcome.GRANTED) {
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
		} else {
			villager.playSound(SoundEvents.VILLAGER_NO, 1f, villager.getVoicePitch());
		}
		Chat.actionBar(player, offer.message());
		return offer;
	}

	/** Makes {@code villager} the Guild Master of their trade's guild in their village, or says why not. */
	public static Offer grant(ServerLevel level, Villager villager) {
		Component who = villager.getDisplayName();
		if (!ENABLED) {
			return refused(Outcome.DISABLED, Component.translatable("message.aliveworkplace.guild.disabled"));
		}
		if (ModAttachments.GUILD_MASTER.has(villager)) {
			return refused(Outcome.ALREADY, Component.translatable("message.aliveworkplace.guild.already", who));
		}
		Optional<BlockPos> hallPos = VillageHalls.nearest(level, villager.blockPosition());
		if (hallPos.isEmpty() || !(level.getBlockEntity(hallPos.get()) instanceof VillageHallBlockEntity hall)) {
			return refused(Outcome.NO_HALL, Component.translatable("message.aliveworkplace.guild.no_hall", who));
		}
		if (villager.getVillagerData().getLevel() < 5) {
			return refused(Outcome.NOT_A_MASTER, Component.translatable("message.aliveworkplace.guild.not_master", who));
		}
		Guild guild = forTrade(villager);
		if (guild == null) {
			return refused(Outcome.NO_GUILD, Component.translatable("message.aliveworkplace.guild.no_guild", who));
		}
		VillageRanks.Rank rank = hall.rank();
		Component village = VillageHalls.name(level, hallPos.get());
		if (rank == VillageRanks.Rank.HAMLET) {
			return refused(Outcome.HAMLET, Component.translatable("message.aliveworkplace.guild.hamlet", village));
		}
		for (Charter c : hall.guilds()) {
			if (c.id().equals(guild.id())) {
				return refused(Outcome.TAKEN, Component.translatable("message.aliveworkplace.guild.taken", village, guild.name(), c.masterName()));
			}
		}
		if (hall.guilds().size() >= cap(rank)) {
			return refused(Outcome.FULL, Component.translatable("message.aliveworkplace.guild.full", village, rank.title(), cap(rank)));
		}
		List<Charter> list = new ArrayList<>(hall.guilds());
		list.add(new Charter(guild.id(), villager.getUUID(), who.getString(), Chronicle.day(level), Optional.empty()));
		hall.setGuilds(list);
		ModAttachments.GUILD_MASTER.set(villager, new Master(guild.id(), hallPos.get()));
		sellGuildhalls(villager);
		Component told = Component.translatable("message.aliveworkplace.guild.chartered", who, guild.name(), village);
		Chronicle.record(level, hallPos.get(), Chronicle.Kind.GUILD, Component.translatable("chronicle.aliveworkplace.guild.chartered", who, guild.name()), true);
		tell(level, hallPos.get(), told);
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		round(level, hallPos.get(), hall);
		return new Offer(Outcome.GRANTED, told.copy().withStyle(ChatFormatting.GREEN));
	}

	private static Offer refused(Outcome outcome, Component message) {
		return new Offer(outcome, message.copy().withStyle(ChatFormatting.RED));
	}

	private static void tell(ServerLevel level, BlockPos hall, Component message) {
		for (ServerPlayer p : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= (double) VillageHalls.RADIUS * VillageHalls.RADIUS)) {
			Chat.chat(p, message.copy().withStyle(ChatFormatting.GOLD));
		}
	}

	/** Whether {@code structure} is a Guildhall (ours in any tier or style, or a pack's named so). */
	public static boolean isGuildhall(ResourceLocation structure) {
		String path = structure.getPath();
		int slash = path.lastIndexOf('/');
		return path.substring(slash + 1).startsWith("guildhall");
	}

	/**
	 * The hall's round: guilds without a Guildhall claim the finished ones nobody has (oldest guild first), and the
	 * founded set is worked out again (a Guildhall no longer finished: its guild waits).
	 */
	public static void round(ServerLevel level, BlockPos pos, VillageHallBlockEntity hall) {
		List<BuildSiteManager.Finished> halls = BuildSiteManager.get(level).finishedNear(level, pos, VillageHalls.RADIUS).stream()
			.filter(f -> isGuildhall(f.structure())).toList();
		Set<BlockPos> standing = new HashSet<>();
		halls.forEach(f -> standing.add(f.placement().origin()));
		Set<BlockPos> claimed = new HashSet<>();
		hall.guilds().forEach(c -> c.guildhall().ifPresent(claimed::add));
		List<Charter> list = new ArrayList<>(hall.guilds());
		boolean changed = false;
		for (int i = 0; i < list.size(); i++) {
			Charter c = list.get(i);
			if (c.guildhall().isPresent()) {
				continue;
			}
			for (BuildSiteManager.Finished f : halls) {
				BlockPos origin = f.placement().origin();
				if (!claimed.contains(origin)) {
					claimed.add(origin);
					list.set(i, c.withGuildhall(origin));
					changed = true;
					frameCharter(level, f);
					Guild guild = get(c.id());
					if (guild != null) {
						Chronicle.record(level, pos, Chronicle.Kind.GUILD, Component.translatable("chronicle.aliveworkplace.guild.founded", guild.name()), true);
						tell(level, pos, Component.translatable("message.aliveworkplace.guild.founded", guild.name(), VillageHalls.name(level, pos)));
					}
					break;
				}
			}
		}
		if (changed) {
			hall.setGuilds(list);
		}
		Set<ResourceLocation> founded = new HashSet<>();
		for (Charter c : list) {
			if (c.guildhall().isPresent() && standing.contains(c.guildhall().get())) {
				founded.add(c.id());
			}
		}
		synchronized (FOUNDED) {
			FOUNDED.put(hall, founded);
		}
	}

	/** The charter goes up in the Guildhall's empty item frames (over the hearth: builders don't copy frame contents). */
	private static void frameCharter(ServerLevel level, BuildSiteManager.Finished f) {
		io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, f.structure()).ifPresent(b -> {
			net.minecraft.world.level.levelgen.structure.BoundingBox box =
				io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(f.placement(), b.size());
			for (net.minecraft.world.entity.decoration.ItemFrame frame : level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
				net.minecraft.world.phys.AABB.of(box), fr -> fr.getItem().isEmpty())) {
				frame.setItem(new ItemStack(ModItems.GUILD_CHARTER), false);
			}
		});
	}

	/** Whether {@code id}'s guild is founded in {@code hall}'s village (worked out at its first round if not yet). */
	public static boolean founded(ServerLevel level, VillageHallBlockEntity hall, ResourceLocation id) {
		Set<ResourceLocation> set;
		synchronized (FOUNDED) {
			set = FOUNDED.get(hall);
		}
		if (set == null) {
			round(level, hall.getBlockPos(), hall);
			synchronized (FOUNDED) {
				set = FOUNDED.getOrDefault(hall, Set.of());
			}
		}
		return set.contains(id);
	}

	/** The guilds whose perks are in force for {@code villager} (founded, of their trade, in their village). */
	private static List<Guild> inForce(Villager villager) {
		if (!ENABLED || !(villager.level() instanceof ServerLevel level)) {
			return List.of();
		}
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		if (hall == null || hall.guilds().isEmpty()) {
			return List.of();
		}
		List<Guild> out = new ArrayList<>();
		for (Charter c : hall.guilds()) {
			Guild g = get(c.id());
			if (g != null && g.gathers(villager) && founded(level, hall, c.id())) {
				out.add(g);
			}
		}
		return out;
	}

	/** The work time {@code villager}'s founded guild makes ({@code work_pace} perks; a source of {@code work/Pace}). */
	public static float pace(Villager villager) {
		float f = 1f;
		for (Guild g : inForce(villager)) {
			for (CivicEffects.Effect e : g.perks()) {
				if (e instanceof CivicEffects.WorkPace pace && CivicEffects.reaches(e, villager)) {
					f *= pace.factor();
				}
			}
		}
		return f;
	}

	/** How the guild's pace reads in the status line: "the Builders' Guild". */
	public static Component paceLabel(Villager villager) {
		List<Guild> list = inForce(villager);
		return list.isEmpty() ? Component.empty() : Component.translatable("pace.aliveworkplace.source.guild", list.get(0).name());
	}

	/** How many idle builders may help at a build near {@code pos}: {@link Builders#MAX_HELPERS}, more with a founded guild's {@code build_helpers}. */
	public static int helpers(ServerLevel level, BlockPos pos) {
		int max = Builders.MAX_HELPERS;
		if (!ENABLED) {
			return max;
		}
		Optional<BlockPos> hallPos = VillageHalls.nearest(level, pos);
		if (hallPos.isEmpty() || !(level.getBlockEntity(hallPos.get()) instanceof VillageHallBlockEntity hall)) {
			return max;
		}
		for (Charter c : hall.guilds()) {
			Guild g = get(c.id());
			if (g != null && founded(level, hall, c.id())) {
				for (CivicEffects.Effect e : g.perks()) {
					if (e instanceof BuildHelpers h) {
						max = Math.max(max, h.max());
					}
				}
			}
		}
		return max;
	}

	/** "Guild Master of the Builders' Guild", for the hall's list and the villager's status; null if they lead none. */
	@Nullable
	public static Component masterLine(Villager villager) {
		Master m = ModAttachments.GUILD_MASTER.get(villager);
		Guild g = m == null ? null : get(m.guild());
		return g == null ? null : Component.translatable("status.aliveworkplace.guild_master", g.name());
	}

	/** A villager died: if they led a guild, its most experienced member takes over (or it is dissolved). */
	public static void onDeath(ServerLevel level, Villager dead) {
		Master m = ModAttachments.GUILD_MASTER.remove(dead);
		if (m == null || !(level.getBlockEntity(m.hall()) instanceof VillageHallBlockEntity hall)) {
			return;
		}
		List<Charter> list = new ArrayList<>(hall.guilds());
		int index = -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id().equals(m.guild()) && list.get(i).master().equals(dead.getUUID())) {
				index = i;
			}
		}
		Guild guild = get(m.guild());
		if (index < 0) {
			return;
		}
		Component name = guild != null ? guild.name() : Component.literal(m.guild().toString());
		Villager heir = null;
		if (guild != null) {
			for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(m.hall()), v -> v != dead && v.isAlive() && !v.isBaby() && guild.gathers(v))) {
				if (heir == null || experience(v) > experience(heir)) {
					heir = v;
				}
			}
		}
		if (heir == null) {
			list.remove(index);
			hall.setGuilds(list);
			Chronicle.record(level, m.hall(), Chronicle.Kind.GUILD, Component.translatable("chronicle.aliveworkplace.guild.dissolved", name), true);
		} else {
			list.set(index, list.get(index).withMaster(heir.getUUID(), heir.getDisplayName().getString()));
			hall.setGuilds(list);
			ModAttachments.GUILD_MASTER.set(heir, new Master(m.guild(), m.hall()));
			sellGuildhalls(heir);
			Chronicle.record(level, m.hall(), Chronicle.Kind.GUILD,
				Component.translatable("chronicle.aliveworkplace.guild.succession", heir.getDisplayName(), name, dead.getDisplayName()), true);
			tell(level, m.hall(), Component.translatable("message.aliveworkplace.guild.succession", heir.getDisplayName(), name));
		}
		round(level, m.hall(), hall);
	}

	/** Emeralds the Guildhall blueprints cost from a Guild Master (I, then II). */
	public static final int[] GUILDHALL_PRICES = {12, 24};

	/** A Guild Master sells the Guildhall's blueprints (I and II), added once to their trades. */
	public static void sellGuildhalls(Villager villager) {
		io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.Entry[] halls = {
			io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.GUILDHALL, io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.GUILDHALL_2};
		for (int i = 0; i < halls.length; i++) {
			ItemStack sold = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.create(halls[i].id(), halls[i].size());
			boolean has = villager.getOffers().stream().anyMatch(o -> ItemStack.isSameItemSameComponents(o.getResult(), sold));
			if (!has) {
				villager.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(
					new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD, GUILDHALL_PRICES[i]), sold, 4, 10, 0.05f));
			}
		}
	}

	/** Level first, then experience within it. */
	private static long experience(Villager v) {
		return v.getVillagerData().getLevel() * 1_000_000L + v.getVillagerXp();
	}

	/** Forget the worked-out founded sets (tests, config changes). */
	public static void forget() {
		synchronized (FOUNDED) {
			FOUNDED.clear();
		}
	}

	private Guilds() {
	}
}
