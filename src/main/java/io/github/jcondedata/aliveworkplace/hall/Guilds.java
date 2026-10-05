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
	public static final ResourceLocation TOOL_WEAR = AliveWorkplace.id("tool_wear");
	public static final ResourceLocation MEND_PER_UNIT = AliveWorkplace.id("mend_per_unit");
	public static final ResourceLocation WORK_REACH = AliveWorkplace.id("work_reach");
	public static final ResourceLocation HERD_SIZE = AliveWorkplace.id("herd_size");
	public static final ResourceLocation RESEARCH_COST = AliveWorkplace.id("research_cost");
	public static final ResourceLocation RECOVERY_DAYS = AliveWorkplace.id("recovery_days");
	public static final ResourceLocation WORK_RADIUS = AliveWorkplace.id("work_radius");
	public static final ResourceLocation HIRE_PRICE = AliveWorkplace.id("hire_price");
	public static final ResourceLocation CARRY = AliveWorkplace.id("carry");
	public static final ResourceLocation TRAIN_UP_TO = AliveWorkplace.id("train_up_to");
	public static final ResourceLocation STRENGTH = AliveWorkplace.id("strength");
	public static final ResourceLocation LESSON_PRICE = AliveWorkplace.id("lesson_price");
	public static final ResourceLocation TRAINER_XP = AliveWorkplace.id("trainer_xp");

	/** {@code build_helpers}: up to {@code max} idle builders help at one build (not {@link Builders#MAX_HELPERS}). */
	public record BuildHelpers(int max, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return BUILD_HELPERS;
		}
	}

	/**
	 * {@code tool_wear} (30.18): the tools members work with (pickaxes, axes, rods, the netherworker's gear) wear
	 * {@code percent} faster (-50: half as fast). Read where the work wears them ({@link #hurt}).
	 */
	public record ToolWear(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return TOOL_WEAR;
		}

		/** The wear this makes, as a share of the usual (-50%: 0.5). */
		public float factor() {
			return Math.max(0f, 1f + percent / 100f);
		}
	}

	/**
	 * {@code mend_per_unit} (30.18): each unit of material a member mends with puts back {@code share} of the piece's
	 * durability (an anvil's quarter otherwise; read by {@code mend/MendingWork}). The best share in force counts.
	 */
	public record MendPerUnit(float share, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return MEND_PER_UNIT;
		}
	}

	/**
	 * {@code work_reach} (30.19): the work members do around their workstation reaches {@code blocks} further (the
	 * Harvest Guild: the farm a village farmer takes on by their composter, and the orchard keeper's rounds, 16 -> 24).
	 */
	public record WorkReach(int blocks, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return WORK_REACH;
		}
	}

	/**
	 * {@code herd_size} (30.19): every herd members keep may be {@code extra} bigger (shepherds, ranchers and herders
	 * breed up to 12 of a kind instead of 8; a hired herder keeps 14 instead of 10).
	 */
	public record HerdSize(int extra, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return HERD_SIZE;
		}
	}

	/**
	 * {@code research_cost} (30.19): the village's research levels cost {@code percent} more paper, books and emeralds
	 * (-25: a quarter less, rounded up). Village-wide once the guild is founded, like {@code build_helpers}.
	 */
	public record ResearchCost(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return RESEARCH_COST;
		}
	}

	/** {@code recovery_days} (30.20): the village's ill get well {@code days} days sooner (or later) without a nurse; village-wide once founded. */
	public record RecoveryDays(int days, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return RECOVERY_DAYS;
		}
	}

	/** {@code work_radius} (30.20): members look {@code blocks} further for those they serve (nurses' patients, undertakers' graves: 32 -> 48). */
	public record WorkRadius(int blocks, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return WORK_RADIUS;
		}
	}

	/** {@code hire_price} (30.20): travellers at the village's inns cost {@code percent} more to hire (-25: a quarter less, rounded up); village-wide. */
	public record HirePrice(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return HIRE_PRICE;
		}
	}

	/** {@code carry} (30.20): members carry {@code stacks} more stacks a trip (porters). */
	public record Carry(int stacks, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return CARRY;
		}
	}

	/** {@code train_up_to} (30.20): members train at a Training Dummy up to {@code level} (5: Master) instead of Expert. */
	public record TrainUpTo(int level, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return TRAIN_UP_TO;
		}
	}

	/** {@code strength} (30.20): members hit {@code percent} harder. */
	public record Strength(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return STRENGTH;
		}
	}

	/** {@code lesson_price} (30.20): what members charge for lessons and revivals changes by {@code percent} (-20: a fifth less, rounded up). */
	public record LessonPrice(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return LESSON_PRICE;
		}
	}

	/** {@code trainer_xp} (30.20): members earn {@code percent} more experience from battles (25: rank up a quarter faster). */
	public record TrainerXp(int percent, List<ResourceLocation> jobs) implements CivicEffects.Effect {
		@Override
		public ResourceLocation type() {
			return TRAINER_XP;
		}
	}

	static {
		CivicEffects.register(RECOVERY_DAYS, RecordCodecBuilder.<RecoveryDays>mapCodec(i -> i.group(
			Codec.intRange(-30, 30).fieldOf("days").forGetter(RecoveryDays::days),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(RecoveryDays::jobs)
		).apply(i, RecoveryDays::new)));
		CivicEffects.register(WORK_RADIUS, RecordCodecBuilder.<WorkRadius>mapCodec(i -> i.group(
			Codec.intRange(-64, 64).fieldOf("blocks").forGetter(WorkRadius::blocks),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(WorkRadius::jobs)
		).apply(i, WorkRadius::new)));
		CivicEffects.register(HIRE_PRICE, RecordCodecBuilder.<HirePrice>mapCodec(i -> i.group(
			Codec.intRange(-100, 1000).fieldOf("percent").forGetter(HirePrice::percent),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(HirePrice::jobs)
		).apply(i, HirePrice::new)));
		CivicEffects.register(CARRY, RecordCodecBuilder.<Carry>mapCodec(i -> i.group(
			Codec.intRange(-27, 27).fieldOf("stacks").forGetter(Carry::stacks),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(Carry::jobs)
		).apply(i, Carry::new)));
		CivicEffects.register(TRAIN_UP_TO, RecordCodecBuilder.<TrainUpTo>mapCodec(i -> i.group(
			Codec.intRange(1, 5).fieldOf("level").forGetter(TrainUpTo::level),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(TrainUpTo::jobs)
		).apply(i, TrainUpTo::new)));
		CivicEffects.register(STRENGTH, RecordCodecBuilder.<Strength>mapCodec(i -> i.group(
			Codec.intRange(-100, 1000).fieldOf("percent").forGetter(Strength::percent),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(Strength::jobs)
		).apply(i, Strength::new)));
		CivicEffects.register(LESSON_PRICE, RecordCodecBuilder.<LessonPrice>mapCodec(i -> i.group(
			Codec.intRange(-100, 1000).fieldOf("percent").forGetter(LessonPrice::percent),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(LessonPrice::jobs)
		).apply(i, LessonPrice::new)));
		CivicEffects.register(TRAINER_XP, RecordCodecBuilder.<TrainerXp>mapCodec(i -> i.group(
			Codec.intRange(-100, 1000).fieldOf("percent").forGetter(TrainerXp::percent),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(TrainerXp::jobs)
		).apply(i, TrainerXp::new)));
		CivicEffects.register(WORK_REACH, RecordCodecBuilder.<WorkReach>mapCodec(i -> i.group(
			Codec.intRange(-64, 64).fieldOf("blocks").forGetter(WorkReach::blocks),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(WorkReach::jobs)
		).apply(i, WorkReach::new)));
		CivicEffects.register(HERD_SIZE, RecordCodecBuilder.<HerdSize>mapCodec(i -> i.group(
			Codec.intRange(-64, 64).fieldOf("extra").forGetter(HerdSize::extra),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(HerdSize::jobs)
		).apply(i, HerdSize::new)));
		CivicEffects.register(RESEARCH_COST, RecordCodecBuilder.<ResearchCost>mapCodec(i -> i.group(
			Codec.intRange(-100, 1000).fieldOf("percent").forGetter(ResearchCost::percent),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(ResearchCost::jobs)
		).apply(i, ResearchCost::new)));
		CivicEffects.register(TOOL_WEAR, RecordCodecBuilder.<ToolWear>mapCodec(i -> i.group(
			Codec.intRange(-100, 1000).fieldOf("percent").forGetter(ToolWear::percent),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(ToolWear::jobs)
		).apply(i, ToolWear::new)));
		CivicEffects.register(MEND_PER_UNIT, RecordCodecBuilder.<MendPerUnit>mapCodec(i -> i.group(
			Codec.floatRange(0.01f, 1f).fieldOf("share").forGetter(MendPerUnit::share),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(MendPerUnit::jobs)
		).apply(i, MendPerUnit::new)));
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
		if (object.has("enabled") && !object.get("enabled").getAsBoolean() || !conditionsMet(object)) {
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

	/** The share of the usual wear {@code villager}'s tools take ({@code tool_wear} perks of their founded guild; 1 without). */
	public static float toolWear(Villager villager) {
		float f = 1f;
		for (Guild g : inForce(villager)) {
			for (CivicEffects.Effect e : g.perks()) {
				if (e instanceof ToolWear wear && CivicEffects.reaches(e, villager)) {
					f *= wear.factor();
				}
			}
		}
		return f;
	}

	/** What wear is left over below one point, per villager (so half the wear is every other block, not none). */
	private static final Map<Villager, Float> WEAR_LEFT = new WeakHashMap<>();

	/**
	 * The durability {@code amount} points of wear take from {@code villager}'s tool after their guild's
	 * {@code tool_wear}; fractions carry over to the next use.
	 */
	public static int wear(Villager villager, int amount) {
		float f = toolWear(villager);
		if (f == 1f || amount <= 0) {
			return amount;
		}
		synchronized (WEAR_LEFT) {
			float total = WEAR_LEFT.getOrDefault(villager, 0f) + amount * f;
			int whole = (int) Math.floor(total + 1e-4f);
			WEAR_LEFT.put(villager, Math.max(0f, total - whole));
			return whole;
		}
	}

	/** Wears {@code stack} (held in {@code slot}) by {@code amount} after {@code villager}'s guild ({@link #wear}). */
	public static void hurt(Villager villager, ItemStack stack, int amount, net.minecraft.world.entity.EquipmentSlot slot) {
		int n = wear(villager, amount);
		if (n > 0 && !stack.isEmpty()) {
			stack.hurtAndBreak(n, villager, slot);
		}
	}

	/** The share of a piece's durability one unit of material puts back when {@code villager} mends: {@code usual}, or a founded guild's better {@code mend_per_unit}. */
	public static float mendShare(Villager villager, float usual) {
		float share = usual;
		for (Guild g : inForce(villager)) {
			for (CivicEffects.Effect e : g.perks()) {
				if (e instanceof MendPerUnit m && CivicEffects.reaches(e, villager)) {
					share = Math.max(share, m.share());
				}
			}
		}
		return share;
	}

	/** How far {@code villager}'s work reaches: {@code usual}, plus a founded guild's {@code work_reach} (never under 1). */
	public static int reach(Villager villager, int usual) {
		int reach = usual;
		for (Guild g : inForce(villager)) {
			for (CivicEffects.Effect e : g.perks()) {
				if (e instanceof WorkReach r && CivicEffects.reaches(e, villager)) {
					reach += r.blocks();
				}
			}
		}
		return Math.max(1, reach);
	}

	/** How big {@code villager} lets a herd grow: {@code usual}, plus a founded guild's {@code herd_size} (never under 2). */
	public static int herd(Villager villager, int usual) {
		int size = usual;
		for (Guild g : inForce(villager)) {
			for (CivicEffects.Effect e : g.perks()) {
				if (e instanceof HerdSize h && CivicEffects.reaches(e, villager)) {
					size += h.extra();
				}
			}
		}
		return Math.max(2, size);
	}

	/**
	 * What {@code amount} of a research level's paper, books or emeralds costs in {@code hall}'s village: changed by its
	 * founded guilds' {@code research_cost} (-25: a quarter less, rounded up; never under 0).
	 */
	public static int researchCost(ServerLevel level, @Nullable VillageHallBlockEntity hall, int amount) {
		if (!ENABLED || hall == null || amount <= 0) {
			return amount;
		}
		int percent = 0;
		for (Charter c : hall.guilds()) {
			Guild g = get(c.id());
			if (g != null && founded(level, hall, c.id())) {
				for (CivicEffects.Effect e : g.perks()) {
					if (e instanceof ResearchCost r) {
						percent += r.percent();
					}
				}
			}
		}
		if (percent == 0) {
			return amount;
		}
		long scaled = (long) amount * Math.max(0, 100 + percent);
		return (int) Math.min(Integer.MAX_VALUE, (scaled + 99) / 100);
	}

	/**
	 * Whether a guild file's {@code fabric:load_conditions} hold (30.20: the Trainers' Guild only with Cobblemon). Read
	 * here, not by the loader, since guild files aren't a vanilla JSON folder: {@code fabric:all_mods_loaded},
	 * {@code fabric:any_mods_loaded}, {@code fabric:not}, {@code fabric:true}; any other condition doesn't hold.
	 */
	static boolean conditionsMet(JsonObject object) {
		JsonElement list = object.get("fabric:load_conditions");
		if (list == null) {
			return true;
		}
		for (JsonElement c : list.isJsonArray() ? list.getAsJsonArray() : java.util.List.of(list)) {
			if (!condition(c.getAsJsonObject())) {
				return false;
			}
		}
		return true;
	}

	private static boolean condition(JsonObject c) {
		String type = c.has("condition") ? c.get("condition").getAsString() : "";
		switch (type) {
			case "fabric:true":
				return true;
			case "fabric:not":
				return !condition(c.getAsJsonObject("value"));
			case "fabric:all_mods_loaded", "fabric:any_mods_loaded": {
				boolean all = type.equals("fabric:all_mods_loaded");
				boolean any = false;
				for (JsonElement v : c.getAsJsonArray("values")) {
					boolean loaded = Platform.get().isModLoaded(v.getAsString());
					if (all && !loaded) {
						return false;
					}
					any |= loaded;
				}
				return all || any;
			}
			default:
				AliveWorkplace.LOG.warn("Unknown guild load condition {}", type);
				return false;
		}
	}

	/** Every number the founded guilds of {@code villager}'s trade put on effects of {@code kind}, summed by {@code number}. */
	private static <T extends CivicEffects.Effect> int memberSum(Villager villager, Class<T> kind, java.util.function.ToIntFunction<T> number) {
		int sum = 0;
		for (Guild g : inForce(villager)) {
			for (CivicEffects.Effect e : g.perks()) {
				if (kind.isInstance(e) && CivicEffects.reaches(e, villager)) {
					sum += number.applyAsInt(kind.cast(e));
				}
			}
		}
		return sum;
	}

	/** Every number the founded guilds of {@code hall}'s village (any trade) put on effects of {@code kind}, summed. */
	private static <T extends CivicEffects.Effect> int villageSum(ServerLevel level, @Nullable VillageHallBlockEntity hall, Class<T> kind,
																	  java.util.function.ToIntFunction<T> number) {
		if (!ENABLED || hall == null) {
			return 0;
		}
		int sum = 0;
		for (Charter c : hall.guilds()) {
			Guild g = get(c.id());
			if (g != null && founded(level, hall, c.id())) {
				for (CivicEffects.Effect e : g.perks()) {
					if (kind.isInstance(e)) {
						sum += number.applyAsInt(kind.cast(e));
					}
				}
			}
		}
		return sum;
	}

	/** {@code amount} changed by {@code percent}, rounded up (never under 0). */
	public static int byPercent(int amount, int percent) {
		if (percent == 0 || amount <= 0) {
			return amount;
		}
		long scaled = (long) amount * Math.max(0, 100 + percent);
		return (int) Math.min(Integer.MAX_VALUE, (scaled + 99) / 100);
	}

	/** How many days {@code villager}'s village's founded guilds take off an illness ({@code recovery_days}; negative: sooner). */
	public static int recoveryDays(Villager villager) {
		return villager.level() instanceof ServerLevel level ? villageSum(level, CivicEffects.hallOf(villager), RecoveryDays.class, RecoveryDays::days) : 0;
	}

	/** How far {@code villager} looks for those they serve: {@code usual}, plus a founded guild's {@code work_radius} (never under 1). */
	public static int radius(Villager villager, int usual) {
		return Math.max(1, usual + memberSum(villager, WorkRadius.class, WorkRadius::blocks));
	}

	/** What hiring a traveller for {@code emeralds} costs in {@code guest}'s village after its founded guilds' {@code hire_price}. */
	public static int hirePrice(Villager guest, int emeralds) {
		return guest.level() instanceof ServerLevel level ? byPercent(emeralds, villageSum(level, CivicEffects.hallOf(guest), HirePrice.class, HirePrice::percent)) : emeralds;
	}

	/** How many stacks {@code villager} carries a trip: {@code usual}, plus a founded guild's {@code carry}. */
	public static int carry(Villager villager, int usual) {
		return Math.max(1, usual + memberSum(villager, Carry.class, Carry::stacks));
	}

	/** The level {@code villager} trains up to at a Training Dummy: {@code usual}, or a founded guild's higher {@code train_up_to}. */
	public static int trainUpTo(Villager villager, int usual) {
		int to = usual;
		for (Guild g : inForce(villager)) {
			for (CivicEffects.Effect e : g.perks()) {
				if (e instanceof TrainUpTo t && CivicEffects.reaches(e, villager)) {
					to = Math.max(to, t.level());
				}
			}
		}
		return to;
	}

	/** How much harder {@code villager} hits after their founded guild's {@code strength} (1 without). */
	public static float strength(Villager villager) {
		return Math.max(0f, 1f + memberSum(villager, Strength.class, Strength::percent) / 100f);
	}

	/** What {@code villager} charges for a lesson or revival of {@code emeralds}, after their founded guild's {@code lesson_price}. */
	public static int lessonPrice(Villager villager, int emeralds) {
		return byPercent(emeralds, memberSum(villager, LessonPrice.class, LessonPrice::percent));
	}

	/** The battle experience {@code xp} earns {@code villager} after their founded guild's {@code trainer_xp} (rounded up). */
	public static int trainerXp(Villager villager, int xp) {
		return byPercent(xp, memberSum(villager, TrainerXp.class, TrainerXp::percent));
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
		synchronized (WEAR_LEFT) {
			WEAR_LEFT.clear();
		}
	}

	private Guilds() {
	}
}
