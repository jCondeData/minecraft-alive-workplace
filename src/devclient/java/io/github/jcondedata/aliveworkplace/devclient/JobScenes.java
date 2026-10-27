package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Scenes for the jobs and screens the first harness scenes didn't cover (ROADMAP 22.3, 22.4), each staged like its
 * job's GameTest and judged by what the job visibly did. Two kinds:
 * <ul>
 *   <li>a job scene: stage the workstation, chests and whatever the job works on; film "01_start", "work_NNNNN" every
 *       two seconds and "03_done" once {@link Job#done} holds (optionally a screen after, "04_..."). It fails if the
 *       work isn't done within {@link Job#maxTicks};</li>
 *   <li>a screen scene: stage, open a menu, point at a slot or two and film each ("01_...", "02_..."), then check it.</li>
 * </ul>
 * The scene names are the tools/showcase/scenes.py catalog's; the stills' names too.
 */
final class JobScenes {
	/** Where a job scene's workstation goes; its chest is two blocks west. The superflat ground is y = -61. */
	static final BlockPos STATION = new BlockPos(0, -60, 0);
	/** The camera of most job scenes: close in front of the station, what it works on behind it (negative z). */
	private static final Vec3 CAMERA = new Vec3(3.4, -57.6, 5);
	private static final Vec3 TARGET = new Vec3(0, -59.5, -0.8);

	/** What a job scene watches for, on the server: the job visibly did its work. */
	interface Done {
		boolean test(ServerLevel level);
	}

	/** Builds a job scene's set on the server and says what "done" means. */
	interface Stage {
		Done stage(ServerLevel level, ServerPlayer player);
	}

	/** A screen shown after a job scene is done: opened on the server with the player moved close (menus close past 8 blocks). */
	record After(String shot, BiConsumer<ServerLevel, ServerPlayer> open, int slot, int rows) {
	}

	record Job(String what, int maxTicks, Vec3 camera, Vec3 target, Stage stage, After after) {
	}

	/** Stills a job scene's server side asks for (named like any still), taken on the client's next tick. */
	/** What a job scene's check last found missing (set on the server by checks that say), added to its timeout message. */
	static volatile String timeoutWhy;

	static final java.util.concurrent.ConcurrentLinkedQueue<String> SHOTS = new java.util.concurrent.ConcurrentLinkedQueue<>();

	/** One step of a screen scene: optionally act on the server, then point at {@code slot} (-1: nowhere) and shoot. */
	/** B329: what a Golem Smith's golem is up to, for a scene's timeout message. */
	private static String golemState(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.animal.IronGolem g) {
		if (g == null) {
			return "no golem";
		}
		String goals = ((io.github.jcondedata.aliveworkplace.mixin.MobAccessor) g).aliveworkplace$goals().getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
			.map(w -> w.getGoal().getClass().getSimpleName()).collect(java.util.stream.Collectors.joining("+"));
		return io.github.jcondedata.aliveworkplace.legend.GolemSmith.role(g) + (g.isAlive() ? "" : " (dead)") + " at " + g.blockPosition().toShortString()
			+ " running [" + goals + "] target " + (g.getTarget() == null ? "none" : g.getTarget().getType().getDescriptionId())
			+ " load " + io.github.jcondedata.aliveworkplace.store.HaulerGolems.load(g);
	}

	/** gifted_born's family (29.7): a still villager facing the camera; a parent is a schooled Master farmer. */
	private static Villager bornSceneVillager(ServerLevel level, BlockPos at, String name, boolean master) {
		Villager v = EntityType.VILLAGER.spawn(level, at, MobSpawnType.COMMAND);
		v.setNoAi(true);
		v.setYRot(0);
		v.setYHeadRot(0);
		v.setCustomName(net.minecraft.network.chat.Component.literal(name));
		if (master) {
			v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(5));
			io.github.jcondedata.aliveworkplace.registry.ModAttachments.SCHOOLED.set(v, true);
		}
		return v;
	}

	/**
	 * The {@code price_board} scene's markets (33.4): Thornholm (the hall at the station) known for Timber and Wool and
	 * short of Bread, with prices that rose, fell and stood still since yesterday, and two villages on its routes, Ashford
	 * (where Timber is dear) and Ridgeway (where it's cheap). Written for today, so the hall's round doesn't recount it.
	 */
	private static void priceBoard(ServerLevel level) {
		io.github.jcondedata.aliveworkplace.hall.Caravans.Data data = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level);
		long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
		BlockPos ashford = STATION.east(180);
		BlockPos ridgeway = STATION.north(220);
		if (data.village(STATION) == null) {
			data.setWants(STATION, Component.literal("Thornholm"), List.of());
		}
		if (data.village(ashford) == null) {
			data.setWants(ashford, Component.literal("Ashford"), List.of());
			data.setWants(ridgeway, Component.literal("Ridgeway"), List.of());
			data.toggleRoute(STATION, ashford);
			data.toggleRoute(ridgeway, STATION);
		}
		java.util.function.Function<String, net.minecraft.resources.ResourceLocation> id = io.github.jcondedata.aliveworkplace.AliveWorkplace::id;
		// what moved since yesterday, in hundredths of an emerald: {now, yesterday}; every other good stands at its base
		java.util.Map<String, int[]> moved = java.util.Map.of("timber", new int[]{80, 90}, "wool", new int[]{62, 70}, "bread", new int[]{168, 150},
			"fish", new int[]{131, 124}, "iron", new int[]{112, 104}, "gold", new int[]{92, 100}, "stone", new int[]{84, 91}, "coal", new int[]{118, 110});
		java.util.Map<net.minecraft.resources.ResourceLocation, io.github.jcondedata.aliveworkplace.trade.Market.Price> ours = new java.util.LinkedHashMap<>();
		java.util.Map<net.minecraft.resources.ResourceLocation, io.github.jcondedata.aliveworkplace.trade.Market.Price> dear = new java.util.LinkedHashMap<>();
		java.util.Map<net.minecraft.resources.ResourceLocation, io.github.jcondedata.aliveworkplace.trade.Market.Price> cheap = new java.util.LinkedHashMap<>();
		for (io.github.jcondedata.aliveworkplace.trade.TradeGoods.Good good : io.github.jcondedata.aliveworkplace.trade.TradeGoods.all()) {
			int[] m = moved.get(good.id().getPath());
			int now = m != null ? m[0] * good.basePrice() / 100 : good.basePrice();
			int yesterday = m != null ? m[1] * good.basePrice() / 100 : good.basePrice();
			ours.put(good.id(), new io.github.jcondedata.aliveworkplace.trade.Market.Price(now, yesterday, 0));
			dear.put(good.id(), new io.github.jcondedata.aliveworkplace.trade.Market.Price(good.basePrice() * 140 / 100, good.basePrice() * 140 / 100, 0));
			cheap.put(good.id(), new io.github.jcondedata.aliveworkplace.trade.Market.Price(good.basePrice() * 72 / 100, good.basePrice() * 72 / 100, 0));
		}
		data.setMarket(STATION, new io.github.jcondedata.aliveworkplace.trade.Market(List.of(id.apply("timber"), id.apply("wool")), List.of(id.apply("bread")),
			ours, today, List.of()));
		data.setMarket(ashford, new io.github.jcondedata.aliveworkplace.trade.Market(List.of(), List.of(id.apply("timber")), dear, today, List.of()));
		data.setMarket(ridgeway, new io.github.jcondedata.aliveworkplace.trade.Market(List.of(id.apply("timber")), List.of(), cheap, today, List.of()));
	}

	/** 33.23: Dunmore, in caravan range of Thornholm but on none of its routes, pays twice the base for everything. */
	private static void merchantPrinceBoard(ServerLevel level) {
		io.github.jcondedata.aliveworkplace.hall.Caravans.Data data = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level);
		BlockPos dunmore = STATION.west(260);
		if (data.village(dunmore) == null) {
			data.setWants(dunmore, Component.literal("Dunmore"), List.of());
		}
		java.util.Map<net.minecraft.resources.ResourceLocation, io.github.jcondedata.aliveworkplace.trade.Market.Price> dear = new java.util.LinkedHashMap<>();
		for (io.github.jcondedata.aliveworkplace.trade.TradeGoods.Good good : io.github.jcondedata.aliveworkplace.trade.TradeGoods.all()) {
			dear.put(good.id(), new io.github.jcondedata.aliveworkplace.trade.Market.Price(good.basePrice() * 2, good.basePrice() * 2, 0));
		}
		data.setMarket(dunmore, new io.github.jcondedata.aliveworkplace.trade.Market(List.of(), List.of(), dear,
			io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level), List.of()));
	}

	/**
	 * The {@code caravan_trade} scene's start (33.6), whatever the halls' own rounds did meanwhile: {@link #priceBoard}'s
	 * markets (Thornholm known for Timber at 0.8 emeralds, Ashford short of it at 1.4), both villages on the list by
	 * name with the route to Ashford on, nothing on the road, 48 logs in Thornholm's chest and none in Ashford's,
	 * 12.4 emeralds in Thornholm's treasury and 20 in Ashford's, and today's own caravan marked as gone so the hall's
	 * round doesn't send it before the scene does.
	 */
	private static void caravanTrade(ServerLevel level) {
		BlockPos ashford = STATION.east(180);
		io.github.jcondedata.aliveworkplace.hall.Caravans.Data data = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level);
		data.setWants(STATION, Component.literal("Thornholm"), List.of());
		data.setWants(ashford, Component.literal("Ashford"), List.of());
		priceBoard(level);
		if (!data.routesFrom(STATION).contains(ashford)) {
			data.toggleRoute(STATION, ashford);
		}
		data.clearRoad(STATION);
		data.clearRoad(ashford);
		data.sent(STATION, io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level));
		Container ours = (Container) level.getBlockEntity(new BlockPos(5, -60, -3));
		Container theirs = (Container) level.getBlockEntity(ashford.east(5));
		ours.clearContent();
		ours.setItem(0, new ItemStack(Items.OAK_LOG, 48));
		ours.setItem(1, new ItemStack(Items.BREAD, 12));
		theirs.clearContent();
		((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION)).setTreasury(1240);
		((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(ashford)).setTreasury(2000);
	}

	/** A raid den crystal (38.2) with its states set by name, as Cobblemon Raid Dens saves them. */
	static net.minecraft.world.level.block.state.BlockState denState(net.minecraft.world.level.block.Block block, String tier, String type, boolean active) {
		net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
		state = denValue(state, "raid_tier", tier);
		state = denValue(state, "raid_type", type);
		return denValue(state, "is_active", Boolean.toString(active));
	}

	/**
	 * A glowing den's raid as the scene shows it: Cobblemon Raid Dens rolls an active crystal's raid (its tier and type) on
	 * the crystal's first tick, so it's set once that's done, keeping the crystal and the raid it rolled.
	 */
	static void denRolled(ServerLevel level, BlockPos pos, String tier, String type) {
		level.setBlockAndUpdate(pos, denState(level.getBlockState(pos).getBlock(), tier, type, true));
	}

	private static net.minecraft.world.level.block.state.BlockState denValue(net.minecraft.world.level.block.state.BlockState state, String name, String value) {
		net.minecraft.world.level.block.state.properties.Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
		return property == null ? state : denSet(state, property, value);
	}

	private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState denSet(net.minecraft.world.level.block.state.BlockState state,
			net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}

	record Step(String shot, int slot, int rows, BiConsumer<ServerLevel, ServerPlayer> before, int hold) {
	}

	/**
	 * Runs {@code action} on the client's Village Hall screen, again every 50 ms (at most {@code tries} times) until it
	 * returns true. A step that opens the hall on the server and clicks in it in the same tick found no screen yet, or a
	 * screen without the section's rows, so the click never happened (hall_people_job, hall_people_bed: lane C's own 40.3
	 * and 40.4 scenes, failing since they landed). The action must be safe to repeat until it succeeds.
	 */
	/** Scrolls the hall's table to the open row's Job/Bed list a little later, once the list has arrived: the stills show it. */
	private static void scrollHallLater() {
		java.util.concurrent.CompletableFuture.delayedExecutor(700, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> {
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> {
				if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen s) {
					s.scrollToLists(); // the Job/Bed buttons on top, the list under them
				}
			});
		});
	}

	private static void onHallScreen(java.util.function.Predicate<io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen> action, int tries) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen s && action.test(s)) {
				return;
			}
			if (tries > 1) {
				java.util.concurrent.CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
					.execute(() -> onHallScreen(action, tries - 1));
			}
		});
	}

	/** The wonder_crew scene's ten builders (35.5). */
	private static final List<Villager> crewBuilders = new java.util.ArrayList<>();

	/**
	 * B314: who of the wonder_crew scene's builders is not on {@code site}'s crew, and why: where they are, what their brain
	 * is doing, whether their work is running, their job and their workplace.
	 */
	private static String crewMissing(ServerLevel level, io.github.jcondedata.aliveworkplace.wonder.WonderSite site, List<java.util.UUID> crew) {
		StringBuilder out = new StringBuilder();
		for (Villager v : crewBuilders) {
			if (crew.contains(v.getUUID())) {
				continue;
			}
			var job = io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_JOB.get(v);
			var part = job == null ? null : io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).get(job.siteId());
			out.append(out.isEmpty() ? "; missing: " : "; ").append(v.getName().getString())
				.append(v.isAlive() ? "" : " dead (" + v.getRemovalReason() + ")")
				.append(" at ").append(v.blockPosition().toShortString())
				.append(" doing ").append(v.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("nothing"))
				.append(v.isSleeping() ? " asleep" : "")
				.append(java.util.Optional.ofNullable(io.github.jcondedata.aliveworkplace.legend.Gifted.of(v)).map(g -> " (gift " + g.id() + ")").orElse(""))
				.append(v.getLastHurtByMob() != null || v.hurtTime > 0 ? " hurt" : "")
				.append(", running ").append(v.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).toList())
				.append(", job ").append(job == null ? "none" : (site.parts().contains(job.siteId()) ? "this Wonder's part" : "another site")
					+ (job.helper() ? " as a helper" : " as lead") + (part == null ? " (gone)" : part.isDone() ? " (done)" : part.isQueued() ? " (queued)" : ""))
				.append(v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
					.map(g -> ", workplace " + g.pos().toShortString() + " (" + level.getBlockState(g.pos()).getBlock().getName().getString() + ", poi "
						+ level.getPoiManager().getType(g.pos()).flatMap(h -> h.unwrapKey()).map(k -> k.location().toString()).orElse("none") + ")")
					.orElse(", no workplace"))
				.append(", ").append(v.getVillagerData().getProfession()).append(v.isBaby() ? " baby" : "")
				.append(", schedule says ").append(v.getBrain().getSchedule().getActivityAt((int) (level.getDayTime() % 24000L)));
		}
		return out.toString();
	}
	/** The wonder_site scene's builder (35.4). */
	private static final Villager[] wonderBuilder = new Villager[1];
	/** The test folly (35.3), the wonder scenes' Wonder. */
	private static final net.minecraft.resources.ResourceLocation WONDER_FOLLY = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");

	/**
	 * The wonder scenes' staged Town. The hall's round counts the rank again from its villagers and buildings (its first
	 * tick, then every {@code VillageNeeds.CHECK_EVERY}), and a hall with a builder or two counts a Hamlet, which holds no
	 * Wonder ("Wonders: 0 of 0 for a Hamlet"). So the scene keeps the Town it stages: a count on record (the first round
	 * waits for the next check) and the rank set again right before anything reads it.
	 */
	static void holdWonderTown(ServerLevel level) {
		holdTown(level, STATION);
	}

	/** {@link #holdWonderTown} for the hall at {@code at} (B129: the Wonder time-lapses' hall, the bounty scenes'). */
	static void holdTown(ServerLevel level, BlockPos at) {
		if (level.getBlockEntity(at) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall) {
			if (hall.needs() == null) {
				hall.setNeeds(new io.github.jcondedata.aliveworkplace.hall.VillageNeeds.Needs(12, 12, 12, 12, 1, 0, 0, 1f));
			}
			hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN);
		}
	}

	/** The lost_caravan scene's carter (31.20). */
	private static final String LOST_CARAVAN_TOBIN = "aliveworkplace_scene_tobin";
	/** The reform_story scene's hall and Toolsmith (31.22). */
	private static final BlockPos REFORM_HALL = new BlockPos(0, -60, -16);
	private static final String REFORM_SMITH = "aliveworkplace_showcase_reform_smith";

	/** The caravan_trail scene's builds (31.19): where each one's middle goes. */
	private static final BlockPos CARAVAN_WRECK = new BlockPos(-16, -61, -22);
	private static final BlockPos CARAVAN_MARK = new BlockPos(2, -61, -22);
	private static final BlockPos CARAVAN_STOCKADE = new BlockPos(24, -61, -24);

	/** One of the trail's builds set down as its arc sets it down (fit ground round its spot). */
	private static io.github.jcondedata.aliveworkplace.story.ArcState.ArcPlace caravanPiece(ServerLevel level, net.minecraft.resources.ResourceLocation template, BlockPos middle) {
		var s = new io.github.jcondedata.aliveworkplace.story.ArcState("aliveworkplace:lost_caravan");
		var p = new io.github.jcondedata.aliveworkplace.story.ArcState.ArcPlace("piece", middle, List.of(template.toString()), 0);
		s.places.add(p);
		io.github.jcondedata.aliveworkplace.story.ArcEffects.build(level, s, p);
		return p;
	}

	/** Puts the camera at {@code camera} looking at {@code target}. */
	private static void aim(ServerLevel level, ServerPlayer player, Vec3 camera, Vec3 target) {
		Vec3 d = target.subtract(camera);
		float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
		float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
		player.teleportTo(level, camera.x, camera.y, camera.z, yaw, pitch);
	}

	/** The bandit_fort scene's fort: its middle on the flat ground in front of the camera. */
	private static final BlockPos BANDIT_FORT_GROUND = new BlockPos(0, -61, -20);

	private static io.github.jcondedata.aliveworkplace.guard.BanditFort.Fort banditFort() {
		return io.github.jcondedata.aliveworkplace.guard.BanditFort.at(BANDIT_FORT_GROUND, net.minecraft.world.level.block.Rotation.NONE);
	}

	/** The herbs scene's pads (31.17): each herb with its ground; the first four in front, the rest behind. */
	private static final Object[][] HERB_PADS = {
		{ModBlocks.SUNTHISTLE, net.minecraft.world.level.block.Blocks.RED_SAND}, {ModBlocks.FROSTBLOOM, net.minecraft.world.level.block.Blocks.SNOW_BLOCK},
		{ModBlocks.PEAKMOSS, net.minecraft.world.level.block.Blocks.STONE}, {ModBlocks.GLOWCAP, net.minecraft.world.level.block.Blocks.PODZOL},
		{ModBlocks.SEA_LAVENDER, net.minecraft.world.level.block.Blocks.GRAVEL}, {ModBlocks.MIREWORT, net.minecraft.world.level.block.Blocks.MUD},
		{ModBlocks.JUNGLE_ORCHID, net.minecraft.world.level.block.Blocks.GRASS_BLOCK}, {ModBlocks.PETAL_BALM, net.minecraft.world.level.block.Blocks.GRASS_BLOCK},
	};

	/** The middle of herb pad {@code i} (where its herbs stand): 9 blocks apart, so no patch reaches the next pad. */
	private static BlockPos herbPad(int i) {
		return new BlockPos(-13 + 9 * (i % 4), -60, i < 4 ? -6 : -15);
	}

	/** The sickness scene (31.18): the nurse's brewing stand, the villagers' and the nurse's tags, the villagers' names. */
	private static final BlockPos SICKNESS_STAND = new BlockPos(8, -60, -10);
	private static final String SICKNESS_TAG = "aliveworkplace_scene_sickness";
	private static final String SICKNESS_NURSE = "aliveworkplace_scene_sickness_nurse";
	private static final String[] SICKNESS_NAMES = {"Bram", "Ilse", "Odo", "Mira", "Tobin", "Wenna", "Jory", "Elda"};

	/** The sickness scene's coughing villagers cough again (grey smoke); how many cough. */
	private static int sicknessCough(ServerLevel level) {
		int n = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-24, -64, -28, 24, -50, 8), v -> v.getTags().contains(SICKNESS_TAG))) {
			if (io.github.jcondedata.aliveworkplace.people.Sickness.isGrey(v)) {
				io.github.jcondedata.aliveworkplace.people.Sickness.cough(level, v);
				n++;
			}
		}
		return n;
	}

	@org.jetbrains.annotations.Nullable
	private static Villager sicknessNurse(ServerLevel level) {
		return level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-24, -64, -28, 24, -50, 8), v -> v.getTags().contains(SICKNESS_NURSE))
			.stream().findFirst().orElse(null);
	}

	/** The nurse takes the tonic round until {@code upTo} of the coughing are well; how many she gave it to. */
	private static int sicknessCure(ServerLevel level, int upTo) {
		Villager nurse = sicknessNurse(level);
		if (nurse == null) {
			return 0;
		}
		nurse.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ModItems.THREE_HERB_TONIC));
		int given = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-24, -64, -28, 24, -50, 8), v -> v.getTags().contains(SICKNESS_TAG))) {
			if (given >= upTo || !io.github.jcondedata.aliveworkplace.people.Sickness.isGrey(v)) {
				continue;
			}
			nurse.teleportTo(v.getX() + 1, v.getY(), v.getZ());
			io.github.jcondedata.aliveworkplace.story.SicknessStory.give(level, nurse, v);
			given++;
		}
		io.github.jcondedata.aliveworkplace.work.WorkerStatus.set(nurse, net.minecraft.network.chat.Component.translatable("message.aliveworkplace.sickness.status.round"),
			1f - sicknessCough(level) / 4f, net.minecraft.network.chat.Component.translatable("message.aliveworkplace.sickness.status.round_line", sicknessCough(level)));
		return given;
	}

	/** The bandit_king scene's front: the ground before the fort's gate, where the banners fly and the allies stand. */
	private static final BlockPos BANDIT_KING_FRONT = new BlockPos(0, -60, -2);

	/** The fort's men hold still for the camera. */
	private static void stillFortMen(ServerLevel level) {
		for (var mob : io.github.jcondedata.aliveworkplace.guard.BanditFort.men(level, BANDIT_FORT_GROUND, 40)) {
			mob.setNoAi(true);
		}
	}

	record Screen(String what, Vec3 camera, Vec3 target, BiConsumer<ServerLevel, ServerPlayer> stage, List<Step> steps,
				  BiPredicate<ServerLevel, ServerPlayer> ok) {
	}

	/** The quest the {@code quest_journal} scene tracks (31.3), from staging to its step. */
	private static final java.util.UUID[] SCENE_QUEST = new java.util.UUID[1];

	/** Villagers the scenes hand from staging to a later step (the scholar, the screen scenes' villager). */
	private static volatile Villager scholar;
	/** The printer scene's chest, for the Gazette the player takes out and reads (34.11). */
	private static volatile Container printerChest;
	private static volatile Villager subject;
	/**
	 * personal_request (B287): the villager whose ask is watched, and how far from the player they were when they asked
	 * (-1 before). Once asked they are free to walk on, so a distance measured later says nothing about the walk-up.
	 */
	private static volatile Villager askWatch;
	private static volatile double askedFrom = -1;
	private static boolean askHooked;

	/** Watches {@code villager}'s personal request: the tick they ask, their distance is kept and they stay put beside the player. */
	private static synchronized void watchAsk(Villager villager) {
		askedFrom = -1;
		askWatch = villager;
		if (!askHooked) {
			askHooked = true;
			net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
				Villager v = askWatch;
				if (v == null || server.getPlayerList().getPlayers().isEmpty()) {
					return;
				}
				var offer = io.github.jcondedata.aliveworkplace.story.PersonalRequests.offer(v);
				if (offer != null && offer.asked) {
					askedFrom = Math.sqrt(v.distanceToSqr(server.getPlayerList().getPlayers().get(0)));
					v.setNoAi(true);
					askWatch = null;
				}
			});
		}
	}
	/** The cuisine_kitchen scene's hall (38.8a), for the board's Dishes tab shown after. */
	private static volatile BlockPos cuisineHall;
	/** classes (34.6): the couple first, then three Peasants. */
	private static final List<Villager> classPeople = new java.util.concurrent.CopyOnWriteArrayList<>();
	/** curfew: the villagers who go to bed at dusk. */
	private static final List<Villager> curfewSleepers = new java.util.concurrent.CopyOnWriteArrayList<>();
	/** Each raider's health and last attacker, and each conscript's health, fight and nearest raider: why a strike is missing. */
	private static String conscriptionState() {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < conscriptionRaiders.size(); i++) {
			var z = conscriptionRaiders.get(i);
			var by = z.getLastHurtByMob();
			out.append("raider ").append(i).append(z.isAlive() ? " hp " + z.getHealth() : " dead").append(" hit by ")
				.append(by instanceof Villager v ? v.getVillagerData().getProfession().toString() : by == null ? "none" : by.getType().toShortString())
				.append(" at ").append(z.blockPosition().toShortString()).append("; ");
		}
		for (Villager v : conscripts) {
			double near = conscriptionRaiders.stream().filter(net.minecraft.world.entity.LivingEntity::isAlive)
				.mapToDouble(z -> Math.sqrt(z.distanceToSqr(v))).min().orElse(-1);
			out.append(v.getVillagerData().getProfession()).append(conscriptsStruck.contains(v) ? " struck" : " never struck").append(v.isAlive() ? " hp " + v.getHealth() : " dead")
				.append(io.github.jcondedata.aliveworkplace.guard.MilitiaCombat.isFighting(v) ? " fighting" : " not fighting")
				.append(" activity ").append(v.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("none"))
				.append(" nearest raider ").append(String.format("%.1f", near)).append("; ");
		}
		return out.toString();
	}

	/** B324: what each conscript holds in each hand, whether they fight and are called up, and their activity. */
	private static String conscriptHands() {
		StringBuilder out = new StringBuilder();
		for (Villager v : conscripts) {
			out.append(v.getVillagerData().getProfession()).append(" holds ").append(describeHeld(v.getMainHandItem()))
				.append(", off hand ").append(describeHeld(v.getOffhandItem()))
				.append(v.isAlive() ? "" : ", dead")
				.append(io.github.jcondedata.aliveworkplace.hall.Conscription.militia(v) != null ? ", called up" : ", not called up")
				.append(io.github.jcondedata.aliveworkplace.guard.MilitiaCombat.isFighting(v) ? ", fighting" : ", not fighting")
				.append(", activity ").append(v.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("none")).append("; ");
		}
		return out.toString();
	}

	private static String describeHeld(ItemStack stack) {
		if (stack.isEmpty()) {
			return "nothing";
		}
		return io.github.jcondedata.aliveworkplace.hall.Conscription.isMilitiaSword(stack) ? "the militia's sword"
			: net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	/** conscription: the villagers called up, and the raiders they beat back. */
	private static final List<Villager> conscripts = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static final List<net.minecraft.world.entity.monster.Zombie> conscriptionRaiders = new java.util.concurrent.CopyOnWriteArrayList<>();
	/**
	 * The conscripts who landed a blow on a raider, recorded as each blow lands (B275): a raider's last attacker was the
	 * old measure, but the guard's iron sword often dealt the killing blow and hid the conscripts' strikes. Fabric's
	 * AFTER_DAMAGE skips a blow that kills (AFTER_DEATH fires instead), so a conscript whose only blows were killing
	 * blows read "never struck" (B322: the librarian finished two raiders); both events are watched.
	 */
	private static final java.util.Set<Villager> conscriptsStruck = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static boolean conscriptBlowsWatched;

	private static void watchConscriptBlows() {
		if (conscriptBlowsWatched) {
			return;
		}
		conscriptBlowsWatched = true;
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof Villager v && conscripts.contains(v) && conscriptionRaiders.contains(entity)) {
				conscriptsStruck.add(v);
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof Villager v && conscripts.contains(v) && conscriptionRaiders.contains(entity)) {
				conscriptsStruck.add(v);
			}
		});
	}
	/** smith_orders: the slot where the Poké Ball turned up (found on the server). */
	private static volatile int pickedSlot = -1;
	/** A screen a job scene films while its job gets going (the Drop Box's). */
	private static volatile BiConsumer<ServerLevel, ServerPlayer> openDuring;
	private static volatile BiConsumer<ServerLevel, ServerPlayer> afterDuring;

	static final Map<String, Job> SCENES = new LinkedHashMap<>();
	static final Map<String, Screen> SCREENS = new LinkedHashMap<>();

	static boolean has(String scene) {
		return SCENES.containsKey(scene) || SCREENS.containsKey(scene);
	}

	// --- Staging helpers -------------------------------------------------------------------------------------------

	/** Puts {@code block} at {@code pos}, facing the camera (south) if it has a facing. */
	static BlockState place(ServerLevel level, BlockPos pos, Block block) {
		BlockState state = ScreenshotHarness.standing(block.defaultBlockState());
		level.setBlockAndUpdate(pos, state);
		return state;
	}

	/** A chest at {@code pos} holding {@code items}. */
	static Container chest(ServerLevel level, BlockPos pos, ItemStack... items) {
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		Container chest = (Container) level.getBlockEntity(pos);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i]);
		}
		return chest;
	}

	/** A villager given the job at {@code station} (the block goes there), standing just in front of it. */
	static Villager worker(ServerLevel level, BlockPos station, Block block, ResourceKey<PoiType> poi, VillagerProfession job) {
		return worker(level, station, place(level, station, block), poi, job);
	}

	/** The same, with the workstation in a given state (already placed). */
	static Villager worker(ServerLevel level, BlockPos station, BlockState state, ResourceKey<PoiType> poi, VillagerProfession job) {
		level.setBlockAndUpdate(station, state);
		Villager v = EntityType.VILLAGER.spawn(level, station.south(), MobSpawnType.COMMAND);
		Jobs.employ(level, v, station, poi, job);
		return v;
	}

	/** One of the jobs' saved counters (ModAttachments). */
	static int n(io.github.jcondedata.aliveworkplace.platform.Attachment<Integer> a, Villager v) {
		return a.getOrElse(v, 0);
	}

	/**
	 * A builder at {@code anchor} building {@code entry}, with every material in barrels by the bench except
	 * {@code without} and with {@code plus} added: what a crafter makes for them. Returns the build site's id.
	 */
	static java.util.UUID builderSite(ServerLevel level, StarterBlueprints.Entry entry, BlockPos anchor, List<Item> without, Map<Item, Integer> plus) {
		Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), blueprint.size(), anchor,
			BlueprintItem.rotationFacing(Direction.SOUTH));
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		Map<Item, Integer> stock = new LinkedHashMap<>(plan.materials());
		without.forEach(stock::remove);
		plus.forEach((item, n) -> stock.merge(item, n, Integer::sum));
		List<ItemStack> stacks = new ArrayList<>();
		stock.forEach((item, total) -> {
			for (int left = total; left > 0; left -= item.getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		});
		BlockPos bench = anchor.offset(3, 0, 4);
		level.setBlockAndUpdate(bench, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
		for (int b = 0; b * 27 < stacks.size(); b++) {
			BlockPos barrelPos = bench.offset(1 + b, 0, 0);
			level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
			Container barrel = (Container) level.getBlockEntity(barrelPos);
			for (int slot = 0; slot < 27 && b * 27 + slot < stacks.size(); slot++) {
				barrel.setItem(slot, stacks.get(b * 27 + slot));
			}
		}
		Villager builder = EntityType.VILLAGER.spawn(level, anchor.offset(0, 0, 3), MobSpawnType.COMMAND);
		Builders.employ(level, builder, bench);
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		return site == null ? null : site.id();
	}

	/** The build is finished, or at least {@code fraction} built. */
	static boolean built(ServerLevel level, java.util.UUID site, float fraction) {
		BuildSite s = site == null ? null : BuildSiteManager.get(level).get(site);
		return s == null || s.isDone() || s.progress(s.plan(level)) >= fraction;
	}

	/**
	 * A villager given a job the way a player does since 21.1a: {@code block} goes at {@code station}, the villager
	 * stands in front of it and is sneak-right-clicked with {@code item} (work/Stations).
	 */
	static Villager picked(ServerLevel level, ServerPlayer player, BlockPos station, Block block, Item item) {
		return picked(level, player, station, place(level, station, block), item);
	}

	static Villager picked(ServerLevel level, ServerPlayer player, BlockPos station, BlockState state, Item item) {
		level.setBlockAndUpdate(station, state);
		Villager v = EntityType.VILLAGER.spawn(level, station.south(), MobSpawnType.COMMAND);
		// B274: no gift by the UUID roll. Gifts are off in GameTests but rolled here; one villager in 240 is a Night Owl, who
		// idles through the scenes' fixed morning (2500) and wanders off instead of working (the sifter's failing GIF).
		io.github.jcondedata.aliveworkplace.legend.Gifted.set(v, null);
		// Staging runs as a server task, and the game files a new block's workstation in a task of its own after this
		// one: hand the item over once it's there, as a player's click always comes after the block is placed.
		level.getServer().execute(() -> {
			if (io.github.jcondedata.aliveworkplace.work.Stations.choose(player, v, new ItemStack(item)) != net.minecraft.world.InteractionResult.SUCCESS) {
				Showcase.check(false, "the villager took the job " + item + " picks at the " + level.getBlockState(station).getBlock().getName().getString());
			}
		});
		return v;
	}

	/** A guard at a post with a chest of its own; holding {@code weapon} if there is one. */
	static Villager guard(ServerLevel level, BlockPos post, ItemStack weapon) {
		Villager g = picked(level, level.getServer().getPlayerList().getPlayers().get(0), post, Blocks.GRINDSTONE.defaultBlockState()
			.setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Items.IRON_SWORD);
		chest(level, post.east(2));
		if (!weapon.isEmpty()) {
			g.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}
		return g;
	}

	/** The mod's professions, by id. */
	static List<VillagerProfession> ourProfessions() {
		return net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.entrySet().stream()
			.filter(e -> e.getKey().location().getNamespace().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.MOD_ID))
			.sorted(java.util.Comparator.comparing(e -> e.getKey().location().getPath()))
			.map(java.util.Map.Entry::getValue).toList();
	}

	private static final String OUTFIT_TAG = "aliveworkplace_outfit";
	/** How many of the outfits scene's mobs were checked so far: there, still of their job, not burning. */
	private static final java.util.concurrent.atomic.AtomicInteger OUTFITS_SEEN = new java.util.concurrent.atomic.AtomicInteger();

	/** Where the {@code n}th of a shot's five outfits stands: in a row 2.5 blocks apart (so long names don't overlap),
	 * facing the camera (south). The zombies stand 60 blocks east, under a roof: zombies burn in daylight. */
	static Vec3 outfitSpot(int n, boolean zombie) {
		return new Vec3((zombie ? 60 : 0) + (n - 2) * 2.5 + 0.5, -60, 0.5);
	}

	static Vec3 outfitCamera(boolean zombie) {
		return new Vec3((zombie ? 60 : 0) + 0.5, -58.7, 6.5);
	}

	static Vec3 outfitTarget(boolean zombie) {
		return new Vec3((zombie ? 60 : 0) + 0.5, -59.0, 0.5);
	}

	/** Puts {@code mob} at {@code at}, still, facing south, named by {@code job} over its head. */
	static void outfit(ServerLevel level, net.minecraft.world.entity.Mob mob, Vec3 at, VillagerProfession job) {
		mob.moveTo(at.x, at.y, at.z, 0, 0);
		mob.setYHeadRot(0);
		mob.setYBodyRot(0);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		mob.addTag(OUTFIT_TAG);
		mob.setCustomName(io.github.jcondedata.aliveworkplace.work.Stations.name(job));
		mob.setCustomNameVisible(true);
		level.addFreshEntity(mob);
	}

	/** Counts the shot's mobs that are still standing in their job and not burning, then removes them. */
	static void outfitsDone(ServerLevel level) {
		var box = new net.minecraft.world.phys.AABB(-20, -62, -5, 80, -55, 5);
		for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, m -> m.getTags().contains(OUTFIT_TAG))) {
			var data = mob instanceof net.minecraft.world.entity.npc.VillagerDataHolder h ? h.getVillagerData() : null;
			if (mob.isAlive() && !mob.isOnFire() && data != null && ourProfessions().contains(data.getProfession())) {
				OUTFITS_SEEN.incrementAndGet();
			}
			mob.discard();
		}
	}

	/** The outfits scene's shots: every job five to a shot as villagers ({@code 01_villagers_1}...), then as zombies. */
	private static List<Step> outfitSteps() {
		List<Step> steps = new java.util.ArrayList<>();
		int shots = (ourProfessions().size() + 4) / 5;
		for (boolean zombie : new boolean[] {false, true}) {
			for (int g = 0; g < shots; g++) {
				steps.add(outfitStep(String.format("%02d_%s_%d", steps.size() + 1, zombie ? "zombies" : "villagers", g + 1), g * 5, 5, zombie));
			}
		}
		return steps;
	}

	/** A shot of the outfits scene: the jobs {@code from} to {@code from + count}, as villagers or as zombie villagers. */
	private static Step outfitStep(String shot, int from, int count, boolean zombie) {
		return new Step(shot, -1, 0, (level, player) -> {
			outfitsDone(level);
			List<VillagerProfession> jobs = ourProfessions();
			for (int n = 0; n < count && from + n < jobs.size(); n++) {
				VillagerProfession job = jobs.get(from + n);
				if (zombie) {
					var z = EntityType.ZOMBIE_VILLAGER.create(level);
					z.setVillagerData(z.getVillagerData().setProfession(job).setLevel(1));
					outfit(level, z, outfitSpot(n, true), job);
				} else {
					Villager v = EntityType.VILLAGER.create(level);
					v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(1));
					outfit(level, v, outfitSpot(n, false), job);
				}
			}
			ScreenshotHarness.hoverLookingAt(player, outfitCamera(zombie), outfitTarget(zombie));
			player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR); // no hand, no hotbar
		}, 40);
	}

	/** Every item of ours that isn't a block, in the order of the owner's picker page. */
	static List<Item> itemIcons() {
		return List.of(ModItems.BLUEPRINT, ModItems.BLANK_BLUEPRINT, ModItems.SCAN_TOOL, ModItems.SHAPE_PLANNER, ModItems.VILLAGE_LEDGER, ModItems.CITY_PLAN,
			ModItems.PATROL_MAP, ModItems.RALLY_BANNER, ModItems.QUARRY_MARKER, ModItems.FIELD_MARKER, ModItems.DELIVERY_NOTE,
			ModItems.PRICE_TAG, ModItems.TRAVEL_TICKET, ModItems.SETTLERS_WAGON);
	}

	/** A step of the items scene: the chest closed, {@code item} held in the main hand (first person, with the hotbar). */
	private static Step held(String shot, Item item) {
		return new Step(shot, -1, 0, (level, player) -> {
			player.closeContainer();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		}, 30);
	}

	static Item cobblemonItem(String id) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", id));
	}

	static void party(ServerPlayer player, String... specs) {
		var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String spec : specs) {
			party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(spec, " ", "=").create());
		}
	}

	/** One half of Cobblemon's Pasture Block ({@code part} bottom or top) in {@code state}'s facing. */
	static BlockState pastureHalf(BlockState state, String part) {
		for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
			if (property.getName().equals("part")) {
				return withValue(state, property, part);
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState withValue(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property,
			String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}

	private static Job job(String what, int maxTicks, Stage stage) {
		return new Job(what, maxTicks, CAMERA, TARGET, stage, null);
	}

	private static BlockPos chestPos() {
		return STATION.west(2);
	}

	/**
	 * B243: villagers work only the village's own Create machines and chests, those inside a building the builders
	 * finished. Records the ground round the station (x -4 to 5, z -7 to 2, four high) as such a building, with an empty
	 * blueprint, so a scene's millstone and store chest are the village's.
	 */
	private static void villageWorkshop(ServerLevel level, net.minecraft.world.entity.player.Player player) {
		net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase/village_workshop");
		BlockPos from = STATION.offset(-4, 0, -7);
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, from, new net.minecraft.core.Vec3i(10, 4, 10), false, Blocks.AIR);
		BuildSiteManager.get(level).recordFinished(id, new BlueprintData.Placement(level.dimension().location(), from,
			net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
	}

	/**
	 * A village of {@code player}'s for the hall_waystones scene (41.2): a hall named {@code name} at {@code hall}, owned by
	 * the player and known as a hall round knows it, and the Waystone Plaza set down at {@code plaza} as the builder
	 * leaves it, recorded finished; the hall's round raises its stone, named after the village. Returns the hall.
	 */
	private static BlockPos waystoneVillage(ServerLevel level, ServerPlayer player, BlockPos hall, String name, BlockPos plaza) {
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState()
			.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
		var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setCustomName(Component.literal(name));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		// B534: the scene is set up inside a server task, where the game files a placed block's point of interest only at
		// the next task; until then VillageHalls.nearest finds no hall here and the round below raised no stone. Filed now,
		// as the game does a moment later (and a hall standing for a while in play always has it).
		if (level.getPoiManager().getType(hall).isEmpty()) {
			net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(level.getBlockState(hall))
				.ifPresent(type -> level.getPoiManager().add(hall.immutable(), type));
		}
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		io.github.jcondedata.aliveworkplace.realm.RealmData.get(level.getServer()).putKnown(new io.github.jcondedata.aliveworkplace.realm.RealmData.Known(
			net.minecraft.core.GlobalPos.of(level.dimension(), hall), Component.literal(name), java.util.Optional.of(player.getUUID()),
			player.getGameProfile().getName(), "hamlet", 0, 0, level.getGameTime()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), plaza,
			net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
		BlueprintLibrary.get(level, io.github.jcondedata.aliveworkplace.hall.VillageWaystones.PLAZA).ifPresent(blueprint -> {
			BuildPlan plan = BuildPlan.create(blueprint, placement);
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					level.setBlock(step.pos(), step.state(), 2);
					if (step.secondaryPos() != null && step.secondaryState() != null) {
						level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
					}
				}
			}
		});
		BuildSiteManager.get(level).recordFinished(io.github.jcondedata.aliveworkplace.hall.VillageWaystones.PLAZA, placement, player.getUUID());
		io.github.jcondedata.aliveworkplace.hall.VillageWaystones.round(level, hall);
		return hall;
	}

	// --- The scenes ------------------------------------------------------------------------------------------------

	static {
		SCENES.put("beekeeper", job("the beekeeper harvested the full hive into the chest", 2400, (level, player) -> {
			// The beehive is the workstation (on a lit campfire, so the bees stay calm).
			BlockPos hive = STATION.above();
			level.setBlockAndUpdate(STATION, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
			Container c = chest(level, chestPos(), new ItemStack(Items.GLASS_BOTTLE, 2), new ItemStack(Items.DANDELION, 4));
			Villager v = picked(level, player, hive, Blocks.BEEHIVE.defaultBlockState().setValue(BlockStateProperties.LEVEL_HONEY, 5)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Items.GLASS_BOTTLE);
			v.moveTo(0.5, -60, 1.5);
			return l -> n(ModAttachments.HIVES_HARVESTED, v) >= 1 && l.getBlockState(hive).getValue(BlockStateProperties.LEVEL_HONEY) == 0
				&& c.countItem(Items.HONEY_BOTTLE) >= 1;
		}));
		SCENES.put("florist", job("the florist grew and picked flowers", 2400, (level, player) -> {
			Villager v = picked(level, player, STATION, Blocks.COMPOSTER, Items.POPPY);
			chest(level, chestPos(), new ItemStack(Items.BONE_MEAL, 16));
			return l -> n(ModAttachments.FLOWERS_GROWN, v) >= 3;
		}));
		SCENES.put("scholar", new Job("the scholar finished a level of research", 2400, CAMERA, TARGET, (level, player) -> {
			BlockPos hall = new BlockPos(4, -60, -5);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState()
				.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
			Villager v = picked(level, player, STATION, Blocks.LECTERN, Items.PAPER);
			chest(level, chestPos(), new ItemStack(Items.PAPER, 20), new ItemStack(Items.EMERALD, 5));
			var hallBe = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			io.github.jcondedata.aliveworkplace.research.Research.POINTS = 200;
			hallBe.setResearch(io.github.jcondedata.aliveworkplace.research.Research.State.EMPTY
				.choose(io.github.jcondedata.aliveworkplace.research.Research.Topic.SWIFT_HANDS));
			scholar = v;
			return l -> hallBe.research().level(io.github.jcondedata.aliveworkplace.research.Research.Topic.SWIFT_HANDS) >= 1;
		}, new After("04_research_screen", (level, player) -> {
			ScreenshotHarness.hover(player, scholar.position().add(1.5, 1.2, 3), 160, 15);
			io.github.jcondedata.aliveworkplace.research.ResearchScreen.open(player, scholar);
		}, 19, 6)));
		SCENES.put("vintner", job("the vintner pressed cider in the cauldron, purple splashes and all", 2400, (level, player) -> {
			// ROADMAP 34.9: a cauldron picked with an apple; apples and glass bottles in the chest. The Novice presses Cider
			// at the vat (purple splashes, a squelch) and puts the bottles in the chest.
			picked(level, player, STATION, Blocks.CAULDRON, Items.APPLE);
			Container c = chest(level, chestPos(), new ItemStack(Items.APPLE, 9), new ItemStack(Items.GLASS_BOTTLE, 3));
			return l -> c.countItem(io.github.jcondedata.aliveworkplace.registry.ModItems.CIDER) >= 2;
		}));
		SCENES.put("tailor", job("the tailor sewed work clothes at the loom", 2400, (level, player) -> {
			// ROADMAP 34.10: a loom picked with string; wool of three colours, leather and string in the chest. The Novice
			// sews Work Clothes at the loom (it clacks, snips of thread fly) and puts them in the chest.
			picked(level, player, STATION, Blocks.LOOM, Items.STRING);
			Container c = chest(level, chestPos(), new ItemStack(Items.WHITE_WOOL, 5), new ItemStack(Items.BROWN_WOOL, 3),
				new ItemStack(Items.LIGHT_GRAY_WOOL, 1), new ItemStack(Items.LEATHER, 6), new ItemStack(Items.STRING, 3));
			return l -> c.countItem(io.github.jcondedata.aliveworkplace.registry.ModItems.WORK_CLOTHES) >= 2;
		}));
		SCENES.put("printer", new Job("the printer printed the Village Gazette at the cartography table", 2400, CAMERA, TARGET, (level, player) -> {
			// ROADMAP 34.11: a cartography table picked with an ink sac, by a hall with a little news in its chronicle; paper
			// and ink sacs in the chest. The Novice prints Gazettes at the table (pages turn, scraps of paper fly) and puts
			// them in the chest; then the player takes one and reads it.
			BlockPos hall = new BlockPos(4, -60, -5);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState()
				.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
			long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			io.github.jcondedata.aliveworkplace.hall.Chronicle.atHall(level, hall, io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.BUILT,
				net.minecraft.network.chat.Component.literal("The new bakery opened its doors"), today);
			io.github.jcondedata.aliveworkplace.hall.Chronicle.atHall(level, hall, io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.WEDDING,
				net.minecraft.network.chat.Component.literal("Bea and Cal were wed under the old oak"), today);
			picked(level, player, STATION, Blocks.CARTOGRAPHY_TABLE, Items.INK_SAC);
			Container c = chest(level, chestPos(), new ItemStack(Items.PAPER, 6), new ItemStack(Items.INK_SAC, 2));
			printerChest = c;
			return l -> c.countItem(io.github.jcondedata.aliveworkplace.registry.ModItems.GAZETTE) >= 2;
		}, new After("04_gazette_read", (level, player) -> {
			for (int i = 0; i < printerChest.getContainerSize(); i++) {
				if (printerChest.getItem(i).is(io.github.jcondedata.aliveworkplace.registry.ModItems.GAZETTE)) {
					ItemStack paper = printerChest.removeItem(i, 1);
					player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, paper);
					paper.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND); // a right-click: the Gazette opens
					break;
				}
			}
		}, 0, 6)));
		SCENES.put("sifter", job("the sifter sifted gravel into loot", 1800, (level, player) -> {
			Villager v = picked(level, player, STATION, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3), Items.GRAVEL);
			chest(level, chestPos(), new ItemStack(Items.GRAVEL, 40));
			return l -> {
				// B274: on a timeout, say what the sifter was doing
				timeoutWhy = "the sifter: " + v.getVillagerData().getProfession() + ", sifted " + n(ModAttachments.BLOCKS_SIFTED, v)
					+ ", doing " + v.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("nothing")
					+ ", schedule " + net.minecraft.core.registries.BuiltInRegistries.SCHEDULE.getKey(v.getBrain().getSchedule())
					+ ", at " + v.blockPosition().toShortString();
				return n(ModAttachments.BLOCKS_SIFTED, v) >= 3;
			};
		}));
		SCENES.put("tinkerer", job("the tinkerer mended the iron golem", 2000, (level, player) -> {
			Villager v = picked(level, player, STATION, Blocks.SMITHING_TABLE, Items.REDSTONE);
			chest(level, chestPos(), new ItemStack(Items.IRON_INGOT, 5));
			var golem = EntityType.IRON_GOLEM.spawn(level, new BlockPos(3, -60, -4), MobSpawnType.COMMAND);
			golem.setNoAi(true);
			golem.setHealth(30f);
			return l -> golem.getHealth() >= golem.getMaxHealth() - 0.5f && n(ModAttachments.GOLEM_REPAIRS, v) >= 1;
		}));
		SCENES.put("steward", new Job("the steward walked his morning rounds and came back to the hall", 2400,
			new Vec3(6.5, -54, 9), new Vec3(0, -60, -4), (level, player) -> {
			// The hall, three zones on its plan a few cells off, and a villager appointed with the City Plan (27.5).
			level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState());
			var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
			var plan = io.github.jcondedata.aliveworkplace.city.CityPlan.EMPTY;
			BlockPos[] spots = {STATION.offset(9, 0, -6), STATION.offset(-9, 0, -6), STATION.offset(0, 0, -13)};
			String[] names = {"Market", "Homes", "Gardens"};
			for (int i = 0; i < spots.length; i++) {
				java.util.BitSet cells = new java.util.BitSet();
				cells.set(io.github.jcondedata.aliveworkplace.city.CityPlan.cellAt(STATION, spots[i]));
				plan = plan.addZone("homes", names[i], "").paint(i, cells);
				level.setBlockAndUpdate(spots[i], Blocks.OAK_FENCE.defaultBlockState()); // a marker post where he stops
			}
			hall.setPlan(plan);
			ItemStack cityPlan = new ItemStack(ModItems.CITY_PLAN);
			io.github.jcondedata.aliveworkplace.city.CityPlanItem.bind(level, player, cityPlan, STATION);
			Villager v = EntityType.VILLAGER.spawn(level, STATION.south(2), MobSpawnType.COMMAND);
			v.setVillagerData(v.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER).setLevel(io.github.jcondedata.aliveworkplace.city.Stewards.MIN_BUILDER_LEVEL)); v.setVillagerXp(70); // a seasoned Builder (27.1a)
			ScreenshotHarness.appointSteward(player, v, cityPlan);
			return l -> io.github.jcondedata.aliveworkplace.city.StewardWork.roundDone(l, v)
				&& v.distanceToSqr(STATION.getCenter()) < 16 && !io.github.jcondedata.aliveworkplace.city.StewardWork.holdsPlan(v);
		}, null));
		// Sieges I (ROADMAP 32.4): raiders who ram gates lay siege to a palisaded village. The gates are shut; the ravager ram
		// walks at the Palisade Gate and breaks its middle gate blow by blow, the cracks showing, then turns on the next one.
		SCENES.put("siege_gate", new Job("the ravager ram broke a gate block of the Palisade Gate, the cracks showing, and turned on the next", 1200,
			new Vec3(3.0, -58.2, -8.0), new Vec3(0.5, -59.2, -2.8), (level, player) -> {
			// (The camera stands inside the village: the gate's cracks face it, the ram's head comes over the gate.)
			BlockPos hall = STATION.north(12);
			BuildSiteManager sites = BuildSiteManager.get(level);
			// A Palisade Gate with a Palisade either side, all finished builds; the outside is the south, the camera's side.
			BlockPos origin = STATION.offset(-3, 0, -4);
			Map<BlockPos, StarterBlueprints.Entry> builds = new LinkedHashMap<>();
			builds.put(origin.west(7), StarterBlueprints.PALISADE);
			builds.put(origin, StarterBlueprints.PALISADE_GATE);
			builds.put(origin.east(7), StarterBlueprints.PALISADE);
			builds.forEach((at, entry) -> {
				level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, at, at,
					new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
				sites.recordFinished(entry.id(), new BlueprintData.Placement(level.dimension().location(), at,
					net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			});
			Villager villager = EntityType.VILLAGER.spawn(level, STATION.offset(-1, 0, -5), MobSpawnType.COMMAND);
			villager.setNoAi(true);
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.forget(hall);
			io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level).forgetSiege(hall);
			io.github.jcondedata.aliveworkplace.threat.Culture culture = io.github.jcondedata.aliveworkplace.threat.Culture.read(
				io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_besiegers"), com.google.gson.JsonParser.parseString(
					"{\"roster\": [{\"entity\": \"minecraft:ravager\", \"share\": 1, \"role\": \"ram\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
			io.github.jcondedata.aliveworkplace.threat.Culture.Member member = culture.roster().get(0);
			net.minecraft.world.entity.Mob ram = io.github.jcondedata.aliveworkplace.threat.Threats.create(level, member);
			ram.moveTo(0.5, STATION.getY(), 5.5, 180f, 0f);
			ram.finalizeSpawn(level, level.getCurrentDifficultyAt(ram.blockPosition()), MobSpawnType.EVENT, null);
			io.github.jcondedata.aliveworkplace.threat.Threats.outfit(level, ram, culture, member);
			ram.setTarget(villager);
			level.addFreshEntityWithPassengers(ram);
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.track(level, hall, culture, List.of(ram));
			BlockPos middle = origin.offset(3, 0, 1);
			BlockPos next = origin.offset(2, 0, 1);
			Showcase.check(io.github.jcondedata.aliveworkplace.threat.Sieges.siege(level, hall).map(s -> middle.equals(s.breach)).orElse(false),
				"the raid on the palisaded village is a siege, and its breach is the Palisade Gate");
			return l -> l.getBlockState(middle).isAir() && ram.isAlive()
				&& io.github.jcondedata.aliveworkplace.threat.Sieges.hitPointsLeft(l, hall, next) < io.github.jcondedata.aliveworkplace.threat.Sieges.FENCE_GATE_HP;
		}, null));
		// Pillager warbands (ROADMAP 32.7): a Town behind a Palisade, the warband's war camp out to the south with its
		// named Warlord and band; the warband's raid from it (its ravager ram with it) lays siege to the Palisade Gate.
		SCENES.put("warband", new Job("a pillager warband made its war camp south of the Town and laid siege to its Palisade Gate: the ravager ram broke a gate block", 1500,
			new Vec3(0.5, -49.0, -17.0), new Vec3(0.5, -59.0, 14.0), (level, player) -> {
			BlockPos hall = STATION.north(12);
			BuildSiteManager sites = BuildSiteManager.get(level);
			BlockPos origin = STATION.offset(-3, 0, -4);
			Map<BlockPos, StarterBlueprints.Entry> builds = new LinkedHashMap<>();
			builds.put(origin.west(14), StarterBlueprints.PALISADE);
			builds.put(origin.west(7), StarterBlueprints.PALISADE);
			builds.put(origin, StarterBlueprints.PALISADE_GATE);
			builds.put(origin.east(7), StarterBlueprints.PALISADE);
			builds.put(origin.east(14), StarterBlueprints.PALISADE);
			builds.forEach((at, entry) -> {
				level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, at, at,
					new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
				sites.recordFinished(entry.id(), new BlueprintData.Placement(level.dimension().location(), at,
					net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			});
			Villager villager = EntityType.VILLAGER.spawn(level, STATION.offset(-1, 0, -8), MobSpawnType.COMMAND);
			villager.setNoAi(true);
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.forget(hall);
			io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level).forgetSiege(hall);
			io.github.jcondedata.aliveworkplace.threat.Lairs.forget(level);
			// The hall of a Town, for as long as the camp is made and the raid sets out (its own round would end a raid by day).
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN);
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("pillager_warband");
			// The camp stands past where the raid gathers (0.6 of the hall's radius out, about z 26, a few blocks each way), as a
			// real one does (80 to 104 out, B130): at z 34 its ring of logs (z 24 to 44, turned at random) took the gathering
			// ground in, the ravager was put down inside it and never got out to the gate.
			io.github.jcondedata.aliveworkplace.threat.Lairs.Lair lair = io.github.jcondedata.aliveworkplace.threat.Lairs.found(level, hall, STATION.offset(0, -1, 52), id);
			Showcase.check(lair != null && io.github.jcondedata.aliveworkplace.threat.Lairs.named(lair)
				&& io.github.jcondedata.aliveworkplace.threat.Lairs.captainName(lair).getString().startsWith("Warlord "), "the warband made its war camp, with a named Warlord");
			io.github.jcondedata.aliveworkplace.threat.Culture culture = io.github.jcondedata.aliveworkplace.threat.Threats.get(id).orElseThrow();
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.Raid raid = io.github.jcondedata.aliveworkplace.guard.VillageRaids.start(level, hall, 24, 0, Math.PI / 2,
				culture, net.minecraft.util.RandomSource.create(7L));
			level.setBlockAndUpdate(hall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			List<net.minecraft.world.entity.Mob> raiders = io.github.jcondedata.aliveworkplace.guard.VillageRaids.raiders(level, hall);
			Showcase.check(raid != null && raiders.stream().filter(m -> m.getType() == EntityType.RAVAGER).count() == 1,
				"the warband's raid set out with one ravager ram, as at a Town");
			BlockPos middle = origin.offset(3, 0, 1);
			Showcase.check(io.github.jcondedata.aliveworkplace.threat.Sieges.siege(level, hall).map(s -> middle.equals(s.breach)).orElse(false),
				"the raid on the palisaded Town is a siege, and its breach is the Palisade Gate");
			return l -> l.getBlockState(middle).isAir();
		}, null));
		// Drowned pirates (ROADMAP 32.8): a Town on the beach at dusk, the Pirate Ship at anchor out on the sea with its bow to
		// the village; its drowned crew wades ashore at the beach nearest it; then Captain Silas Brine falls in his cabin and
		// the ship burns and sinks, top down, its loot chest left on a log raft.
		SCENES.put("pirates", new Job("the pirate ship anchored off the Town at dusk, its drowned crew waded ashore, and when its captain fell it burned and sank, leaving its chest on a raft", 900, // (done at 510 of 520 locally: room for a slower runner, B130)
			new Vec3(25.5, -50.0, 0.5), new Vec3(0.5, -58.0, 20.0), (level, player) -> {
			BlockPos hall = STATION.north(6);
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.forget(hall);
			io.github.jcondedata.aliveworkplace.threat.Lairs.forget(level);
			level.setDayTime(13000);
			// The sea: three deep down to the bedrock, from the beach (z 3) out past the ship.
			for (int x = -30; x <= 30; x++) {
				for (int z = 4; z <= 62; z++) {
					for (int y = -63; y <= -61; y++) {
						level.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			for (int x = -30; x <= 30; x++) {
				for (int z = 0; z <= 3; z++) {
					level.setBlock(new BlockPos(x, -61, z), net.minecraft.world.level.block.Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			for (int i = 0; i < 3; i++) {
				Villager villager = EntityType.VILLAGER.spawn(level, STATION.offset(-3 + 3 * i, 0, -3), MobSpawnType.COMMAND);
				villager.setNoAi(true);
			}
			// The hall of a Town, for as long as the ship anchors and the crew comes ashore (its own round would end the raid by dusk).
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN);
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("drowned_pirates");
			io.github.jcondedata.aliveworkplace.threat.Lairs.Lair lair = io.github.jcondedata.aliveworkplace.threat.Lairs.found(level, hall, new BlockPos(0, -61, 30), id);
			Showcase.check(lair != null && io.github.jcondedata.aliveworkplace.threat.Lairs.captainName(lair).getString().equals("Captain Silas Brine")
				&& io.github.jcondedata.aliveworkplace.threat.Ships.of(level, hall).map(s -> s.rotation() == net.minecraft.world.level.block.Rotation.NONE).orElse(false),
				"the Pirate Ship anchored with its bow to the Town, under Captain Silas Brine");
			io.github.jcondedata.aliveworkplace.threat.Culture culture = io.github.jcondedata.aliveworkplace.threat.Threats.get(id).orElseThrow();
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.Raid raid = io.github.jcondedata.aliveworkplace.guard.VillageRaids.start(level, hall, 24, 0, Double.NaN,
				culture, net.minecraft.util.RandomSource.create(7L));
			level.setBlockAndUpdate(hall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			List<net.minecraft.world.entity.Mob> raiders = io.github.jcondedata.aliveworkplace.guard.VillageRaids.raiders(level, hall);
			BlockPos beach = new BlockPos(0, -60, 3);
			Showcase.check(raid != null && !raiders.isEmpty() && raiders.stream().allMatch(m -> m.blockPosition().distManhattan(beach) <= 8),
				"the crew came ashore at the beach nearest the ship");
			java.util.UUID captain = lair.captain();
			long began = level.getGameTime();
			BlockPos masthead = new BlockPos(0, -63 + 19, 30 + 2);
			return l -> {
				// (asked every 10 frames, so ">=": "==" missed the moment and the captain never fell, B130)
				if (l.getGameTime() - began >= 160 && l.getEntity(captain) instanceof net.minecraft.world.entity.LivingEntity brine && brine.isAlive()) {
					brine.hurt(l.damageSources().fellOutOfWorld(), 1000f); // the captain falls: the ship burns and sinks
				}
				return l.getGameTime() - began > 160 && io.github.jcondedata.aliveworkplace.threat.Ships.sinking(l).isEmpty()
					&& io.github.jcondedata.aliveworkplace.threat.Lairs.near(l, hall).isEmpty() && l.getBlockState(masthead).isAir()
					&& l.getBlockState(new BlockPos(0, -60, 14)).is(net.minecraft.world.level.block.Blocks.CHEST);
			};
		}, null));
		// Village fire (ROADMAP 32.16): a Starter Cottage the builders finished, its roof struck by lightning and catching; the
		// Firewarden at the grindstone (picked with a water bucket) runs over and throws bucket after bucket on it, steam
		// rising, refilling at the pond by the grindstone, until it's out and the chronicle says so.
		SCENES.put("fire", new Job("the Starter Cottage's roof caught fire and the Firewarden ran over from the grindstone and put it out with buckets of water", 1600,
			new Vec3(10.5, -52.0, 9.5), new Vec3(-1.0, -56.0, -9.0), (level, player) -> {
			io.github.jcondedata.aliveworkplace.threat.VillageFire.forget();
			BlockPos hall = STATION.offset(0, 0, -26);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			BlockPos origin = STATION.offset(-5, 0, -15);
			level.getStructureManager().get(StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow().placeInWorld(level, origin, origin,
				new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.STARTER_COTTAGE.id(), new BlueprintData.Placement(level.dimension().location(),
				origin, net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			// The pond the firewarden fills the buckets at, beside the grindstone.
			for (int x = 3; x <= 5; x++) {
				for (int z = 1; z <= 3; z++) {
					level.setBlockAndUpdate(STATION.offset(x, -1, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
				}
			}
			Villager warden = picked(level, player, STATION, Blocks.GRINDSTONE.defaultBlockState()
				.setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Items.WATER_BUCKET);
			io.github.jcondedata.aliveworkplace.work.FirewardenWork.setWater(warden, 1);
			long began = level.getGameTime();
			boolean[] lit = {false};
			return l -> {
				if (!lit[0] && l.getGameTime() - began >= 60) {
					lit[0] = true;
					l.getServer().execute(() -> {
						// The strike that sets it off (only the flash: the roof is lit here, as the strike would, so the scene
						// doesn't wait on the 1 in 3).
						net.minecraft.world.entity.LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
						bolt.moveTo(Vec3.atBottomCenterOf(origin.offset(5, 9, 5)));
						bolt.setVisualOnly(true);
						l.addFreshEntity(bolt);
						Showcase.check(io.github.jcondedata.aliveworkplace.threat.VillageFire.igniteNear(l, hall, origin.offset(5, 8, 5),
							net.minecraft.util.RandomSource.create(3L)).isPresent(), "the cottage's roof caught fire");
						for (int i = 0; i < 2; i++) {
							io.github.jcondedata.aliveworkplace.threat.VillageFire.igniteNear(l, hall, origin.offset(2 + 6 * i, 8, 4),
								net.minecraft.util.RandomSource.create(5L + i));
						}
					});
				}
				return lit[0] && l.getGameTime() - began > 200 && io.github.jcondedata.aliveworkplace.threat.VillageFire.incident(l, hall).isEmpty()
					&& warden.getVillagerXp() > 1;
			};
		}, null));
		// Threats spare the builders' work (ROADMAP 32.16a): a raider's creeper hisses beside a Starter Cottage the builders
		// finished and blows up; the player's dirt mound beside it is blown away, every block of the cottage still stands.
		SCENES.put("threat_spared", new Job("a raider's creeper blew up right beside the Starter Cottage: the dirt mound a player had left there went, every block the builders placed still stood",
			900, new Vec3(10.5, -52.0, 9.5), new Vec3(-1.0, -56.0, -9.0), (level, player) -> {
			BlockPos origin = STATION.offset(-5, 0, -15);
			net.minecraft.core.Vec3i size = StarterBlueprints.STARTER_COTTAGE.size();
			level.getStructureManager().get(StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow().placeInWorld(level, origin, origin,
				new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.STARTER_COTTAGE.id(), new BlueprintData.Placement(level.dimension().location(),
				origin, net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			io.github.jcondedata.aliveworkplace.threat.VillageFire.forgetBuilds();
			List<BlockPos> cottage = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size).offset(-1, -1, -1))) {
				if (!level.getBlockState(p).isAir()) {
					cottage.add(p.immutable());
				}
			}
			// The player's dirt mound, two blocks out from the cottage's east wall.
			int mid = size.getZ() / 2;
			List<BlockPos> mound = new ArrayList<>();
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos at = origin.offset(size.getX() + 2, dy, mid + dz);
					level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
					mound.add(at);
				}
			}
			net.minecraft.world.entity.monster.Creeper creeper = EntityType.CREEPER.create(level);
			BlockPos stand = origin.offset(size.getX(), 0, mid);
			creeper.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 90f, 0f);
			creeper.addTag(io.github.jcondedata.aliveworkplace.threat.Threats.RAIDER_TAG);
			creeper.setNoAi(true);
			level.addFreshEntity(creeper);
			long began = level.getGameTime();
			return l -> {
				// (asked every 10 frames, so ">=": "==" missed the moment and the creeper never lit, B130)
				if (l.getGameTime() - began >= 60 && creeper.isAlive() && !creeper.isIgnited()) {
					creeper.ignite();
				}
				if (l.getGameTime() - began < 160 || creeper.isAlive()) {
					return false;
				}
				long standing = cottage.stream().filter(p -> !l.getBlockState(p).isAir()).count();
				Showcase.check(standing == cottage.size(), "every block of the cottage still stands (" + standing + " of " + cottage.size() + ")");
				Showcase.check(mound.stream().anyMatch(p -> l.getBlockState(p).isAir()), "the player's dirt mound was blown away");
				return true;
			};
		}, null));
		// Drought (ROADMAP 32.17): a wheat field gone dry (farmland at moisture 0, dead bushes round it) in a village under
		// a drought; the Well Keeper at the cauldron (picked with a shovel) fills a bucket at the pond and carries it to the
		// field, bucket after bucket, each wetting 9 farmland dark again in a splash.
		SCENES.put("drought", new Job("the fields were dry in the drought, and the Well Keeper carried water from the pond to them, bucket after bucket", 1600,
			new Vec3(11.5, -53.0, 5.5), new Vec3(0.0, -60.0, -7.0), (level, player) -> {
			level.setDayTime(2000);
			BlockPos hall = STATION.offset(0, 0, -26);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			// The field: 9 x 5 farmland dried right out, wheat of all ages on it, dead bushes on coarse dirt round it.
			net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7L);
			List<BlockPos> field = new ArrayList<>();
			for (int x = -4; x <= 4; x++) {
				for (int z = -11; z <= -7; z++) {
					BlockPos soil = STATION.offset(x, -1, z);
					level.setBlockAndUpdate(soil, net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState()
						.setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 0));
					level.setBlockAndUpdate(soil.above(), ((net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.WHEAT)
						.getStateForAge(random.nextInt(4)));
					field.add(soil);
				}
			}
			for (int[] d : new int[][] {{-6, -12}, {-6, -8}, {6, -10}, {6, -6}, {-2, -13}, {3, -13}}) {
				BlockPos at = STATION.offset(d[0], -1, d[1]);
				level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.COARSE_DIRT.defaultBlockState());
				level.setBlockAndUpdate(at.above(), net.minecraft.world.level.block.Blocks.DEAD_BUSH.defaultBlockState());
			}
			// The pond the keeper fills the bucket at, beside the cauldron.
			for (int x = 3; x <= 5; x++) {
				for (int z = 1; z <= 3; z++) {
					level.setBlockAndUpdate(STATION.offset(x, -1, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
				}
			}
			io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			io.github.jcondedata.aliveworkplace.threat.Disaster drought = io.github.jcondedata.aliveworkplace.threat.Drought.get().orElse(null);
			Showcase.check(drought != null && entity != null, "the drought is loaded and the hall stands");
			if (drought != null && entity != null) {
				entity.setDisaster(null, -1, -1, -100);
				io.github.jcondedata.aliveworkplace.threat.Disasters.start(level, hall, entity, drought,
					io.github.jcondedata.aliveworkplace.hall.VillageHalls.census(level, hall), net.minecraft.util.RandomSource.create(1L));
			}
			Villager keeper = picked(level, player, STATION, Blocks.CAULDRON, Items.IRON_SHOVEL);
			return l -> {
				long watered = field.stream().filter(p -> io.github.jcondedata.aliveworkplace.threat.Drought.watered(l, p)).count();
				return watered >= 18 && keeper.getVillagerXp() > 1 && io.github.jcondedata.aliveworkplace.threat.Drought.in(l, STATION.offset(0, -1, -9));
			};
		}, null));
		// Plague (ROADMAP 32.18): three villagers ill with the plague, each by the bed of their own house up the street; the
		// Physician at the brewing stand (picked with a glistering melon slice) takes a Remedy from the chest and goes house
		// to house: two Remedies cure two of them in a puff of hearts, the third is seen and sent home to bed. Then the
		// hall's list of the village, where the one still ill shows "Ill with the plague: seen by the Physician, kept at home".
		SCENES.put("plague", new Job("a plague broke out; the Physician took Remedies from the chest and went house to house: two of the ill were cured, the third was seen and sent home to bed, as the hall's list showed",
			1800, new Vec3(0.5, -55.0, 6.5), new Vec3(0.0, -60.0, -8.0), (level, player) -> {
			level.setDayTime(2000);
			BlockPos hall = STATION.offset(0, 0, -26);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			Villager physician = picked(level, player, STATION, Blocks.BREWING_STAND, Items.GLISTERING_MELON_SLICE);
			chest(level, chestPos(), new ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.REMEDY, 2), new ItemStack(Items.GLASS_BOTTLE, 3));
			List<Villager> ill = new ArrayList<>();
			int[][] houses = {{-7, -8}, {0, -13}, {7, -8}};
			for (int[] h : houses) {
				BlockPos foot = STATION.offset(h[0], 0, h[1]);
				level.setBlockAndUpdate(foot, Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH)
					.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
				level.setBlockAndUpdate(foot.north(), Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH)
					.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
				Villager v = EntityType.VILLAGER.spawn(level, foot.south(), MobSpawnType.COMMAND);
				v.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME, net.minecraft.core.GlobalPos.of(level.dimension(), foot.north()));
				ill.add(v);
			}
			io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			io.github.jcondedata.aliveworkplace.threat.Disaster plague = io.github.jcondedata.aliveworkplace.threat.Disasters.get(io.github.jcondedata.aliveworkplace.threat.Plague.ID).orElse(null);
			Showcase.check(plague != null && entity != null, "the plague is loaded and the hall stands");
			for (Villager v : ill) {
				io.github.jcondedata.aliveworkplace.people.Sickness.catchPlague(level, v);
			}
			if (plague != null && entity != null) {
				entity.setDisaster(null, -1, -1, -100);
				io.github.jcondedata.aliveworkplace.threat.Disasters.start(level, hall, entity, plague,
					io.github.jcondedata.aliveworkplace.hall.VillageHalls.census(level, hall), net.minecraft.util.RandomSource.create(1L));
			}
			Showcase.check(ill.stream().allMatch(io.github.jcondedata.aliveworkplace.people.Sickness::isPlagueIll), "three villagers are ill with the plague");
			return l -> {
				long cured = ill.stream().filter(v -> !io.github.jcondedata.aliveworkplace.people.Sickness.isIll(v)).count();
				long seen = ill.stream().filter(io.github.jcondedata.aliveworkplace.threat.Plague::seen).count();
				return cured >= 2 && seen >= 1 && physician.getVillagerXp() > 1;
			};
		}, new After("04_hall_list_of_the_ill", (level, player) -> {
			ScreenshotHarness.hover(player, Vec3.atCenterOf(STATION.offset(0, 0, -24)), 180, 10);
			io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION.offset(0, 0, -26));
		}, io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON, 6)));
		// The Mechanic (ROADMAP 39.2, filmed with Create): a Mechanic at a Create Toolbox, the store chest beside it with
		// shafts, a cogwheel and wheat. Behind them a finished drive line (a stone-brick footing, shafts and a cogwheel)
		// has lost a shaft and its cogwheel, and a creative motor turns a millstone. The Mechanic fetches the parts and puts
		// them back from the build's blueprint, shaft first, then fills the turning millstone with wheat and brings its flour
		// back to the chest.
		SCENES.put("mechanic", job("the Mechanic put back the shaft and the cogwheel missing from the finished drive line, fed the turning millstone the store's wheat and brought its flour back to the chest",
			2400, (level, player) -> {
			level.setDayTime(2000);
			java.util.function.Function<String, Block> create = path -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", path));
			java.util.function.Function<String, Item> createItem = path -> net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", path));
			Showcase.check(ModVillagers.MECHANIC != null, "Create is loaded and the Mechanic is a job");
			if (ModVillagers.MECHANIC == null) {
				return l -> true;
			}
			villageWorkshop(level, player);
			Container c = chest(level, chestPos(), new ItemStack(createItem.apply("shaft"), 2), new ItemStack(createItem.apply("cogwheel"), 1),
				new ItemStack(Items.WHEAT, 16));
			Villager mechanic = worker(level, STATION, create.apply("brown_toolbox"), ModVillagers.TOOLBOX_POI, ModVillagers.MECHANIC);
			BlockPos line = STATION.offset(-2, 0, -5);
			BlockState shaft = create.apply("shaft").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
			BlockState cog = create.apply("cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
			for (int x = 0; x < 5; x++) {
				level.setBlockAndUpdate(line.offset(x, 0, 0), Blocks.STONE_BRICKS.defaultBlockState());
				level.setBlockAndUpdate(line.offset(x, 1, 0), x < 4 ? shaft : cog);
			}
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase/mechanic_line");
			level.getStructureManager().getOrCreate(id).fillFromWorld(level, line, new net.minecraft.core.Vec3i(5, 2, 1), false, Blocks.STRUCTURE_VOID);
			BuildSiteManager.get(level).recordFinished(id, new BlueprintData.Placement(level.dimension().location(), line,
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			BlockPos gap = line.offset(1, 1, 0);
			BlockPos cogAt = line.offset(4, 1, 0);
			level.setBlockAndUpdate(gap, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(cogAt, Blocks.AIR.defaultBlockState());
			BlockPos mill = STATION.offset(3, 1, -2);
			level.setBlockAndUpdate(mill.below(), create.apply("creative_motor").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
			level.setBlockAndUpdate(mill, create.apply("millstone").defaultBlockState());
			return l -> l.getBlockState(gap).is(create.apply("shaft")) && l.getBlockState(cogAt).is(create.apply("cogwheel"))
				&& c.countItem(createItem.apply("wheat_flour")) > 0 && mechanic.isAlive();
		}));
		// Villagers work Create's machines (ROADMAP 39.3, filmed with Create): a Farmer at a composter, the store chest beside
		// it with wheat, and a millstone on a creative motor a few steps away. The farmer takes wheat from the chest, pours it
		// into the turning millstone and brings the flour it grinds back to the chest.
		SCENES.put("create_mill", job("the Farmer took wheat from the chest to the turning millstone and brought the flour it ground back to the chest",
			2400, (level, player) -> {
			level.setDayTime(2000);
			java.util.function.Function<String, Block> create = path -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", path));
			java.util.function.Function<String, Item> createItem = path -> net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", path));
			Showcase.check(!io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles.all().isEmpty(), "Create is loaded and the machine-work files are read");
			villageWorkshop(level, player);
			Container c = chest(level, chestPos(), new ItemStack(Items.WHEAT, 32));
			Villager farmer = worker(level, STATION, Blocks.COMPOSTER, net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER,
				net.minecraft.world.entity.npc.VillagerProfession.FARMER);
			BlockPos mill = STATION.offset(3, 1, -2);
			level.setBlockAndUpdate(mill.below(), create.apply("creative_motor").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
			level.setBlockAndUpdate(mill, create.apply("millstone").defaultBlockState());
			return l -> c.countItem(createItem.apply("wheat_flour")) > 0 && farmer.isAlive();
		}));
		// The Create-powered builds (ROADMAP 39.4, filmed with Create): the Watermill, the Windmill and the Workshop set down
		// side by side as a builder leaves them, facing the camera. Water falls from the header tanks through the two wheels,
		// the windmill's bearing is started as the builder starts it, and every machine turns: both millstones and the press.
		SCENES.put("create_builds", new Job("the Watermill's wheel, the Windmill's sails and the Workshop's wheel and belt all turned, and with them both millstones and the press",
			900, new Vec3(0.5, -49.0, -16.5), new Vec3(0.5, -55.0, 8.0), (level, player) -> {
			level.setDayTime(2000);
			Showcase.check(io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("create"), "Create is loaded");
			Showcase.check(contraptionBufferTakesVertices(), "Create's contraption buffer takes a quad written vertex by vertex, as Sodium writes one with shaders on (B231)");
			List<BlockPos> machines = new java.util.ArrayList<>();
			int x = -20;
			for (StarterBlueprints.Entry entry : StarterBlueprints.FIRST_CREATE_BUILDS) {
				BlockPos at = STATION.offset(x, 0, 4);
				level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, at, at,
					new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
				BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), at,
					net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
				BuildSiteManager.get(level).recordFinished(entry.id(), placement, player.getUUID());
				io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds.finished(level, entry.id(), placement, player);
				machines.add(at.offset(entry == StarterBlueprints.WATERMILL ? new BlockPos(4, 2, 4)
					: entry == StarterBlueprints.WINDMILL ? new BlockPos(4, 2, 5) : new BlockPos(4, 3, 2)));
				x += entry.size().getX() + 3;
			}
			return l -> machines.stream().allMatch(m -> io.github.jcondedata.aliveworkplace.mechanic.MachineWork.turning(l, m, 0.01f));
		}, null));
		// A waystone by every Village Hall (ROADMAP 41.1, filmed with Waystones): a hall named Stonebridge, a builder at
		// his bench with the Waystone Plaza's materials in barrels; he builds the plaza in front of the camera, and its
		// stone becomes a working waystone of the mod's named after the village.
		SCENES.put("village_waystone", new Job("the builder raised the Waystone Plaza by the Village Hall, and its stone became a working waystone named Stonebridge",
			2400, new Vec3(0.5, -53.0, -14.5), new Vec3(0.5, -57.0, -5.5), (level, player) -> {
			level.setDayTime(2000);
			Showcase.check(io.github.jcondedata.aliveworkplace.hall.VillageWaystones.offered(), "Waystones is loaded and the plaza is offered (villageWaystones)");
			BlockPos hall = STATION.offset(-6, 0, 2);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).setCustomName(net.minecraft.network.chat.Component.literal("Stonebridge"));
			BlockPos bench = STATION.offset(5, 0, 1);
			level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
			Villager builder = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
			Builders.employ(level, builder, bench);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), STATION.offset(-3, 0, -9),
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
			BuildSite site = Builders.start(level, builder, null, io.github.jcondedata.aliveworkplace.hall.VillageWaystones.PLAZA, placement);
			Showcase.check(site != null, "the builder took the Waystone Plaza on");
			if (site == null) {
				return l -> false;
			}
			BuildPlan plan = site.plan(level);
			List<ItemStack> stacks = new ArrayList<>();
			plan.materials().forEach((item, total) -> {
				for (int left = total; left > 0; left -= item.getDefaultMaxStackSize()) {
					stacks.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
				}
			});
			for (int b = 0; b * 27 < stacks.size(); b++) {
				BlockPos at = bench.offset(1, 0, b);
				level.setBlockAndUpdate(at, Blocks.BARREL.defaultBlockState());
				Container barrel = (Container) level.getBlockEntity(at);
				for (int slot = 0; slot < 27 && b * 27 + slot < stacks.size(); slot++) {
					barrel.setItem(slot, stacks.get(b * 27 + slot));
				}
			}
			BlockPos stone = placement.origin().offset(3, 2, 3);
			return l -> {
				if (BuildSiteManager.get(l).get(site.id()) != null) {
					return false;
				}
				java.util.Optional<String> name = io.github.jcondedata.aliveworkplace.hall.VillageWaystones.EXTENSION.call(
					w -> w.nameAt(l, stone), java.util.Optional.<String>empty());
				Showcase.check(name.equals(java.util.Optional.of("Stonebridge")), "the plaza's stone is a working waystone named Stonebridge (" + name + ")");
				return name.isPresent();
			};
		}, null));
		// More Create builds (ROADMAP 39.8, filmed with Create): the Brass Foundry, the Sawmill and the Crushing Mill in
		// front, the Greenhouse, the Clock Tower and the Pump House behind them, set down as a builder leaves them, facing
		// the camera. Water falls from each header tank through its wheel, and every machine turns: the mixer, the saws,
		// the crushing wheels, the greenhouse's belt, the cuckoo clock and the pump.
		SCENES.put("create_builds_more", new Job("the six builds' wheels turned, and with them the Brass Foundry's mixer, the Sawmill's saws, the Crushing Mill's wheels, the Greenhouse's belt, the Clock Tower's cuckoo clock and the Pump House's pump",
			900, new Vec3(0.5, -40.0, -22.5), new Vec3(0.5, -54.0, 14.0), (level, player) -> {
			level.setDayTime(2000);
			Showcase.check(io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("create"), "Create is loaded");
			java.util.Map<StarterBlueprints.Entry, BlockPos> machine = java.util.Map.of(StarterBlueprints.BRASS_FOUNDRY, new BlockPos(8, 4, 8),
				StarterBlueprints.SAWMILL, new BlockPos(4, 1, 4), StarterBlueprints.CRUSHING_MILL, new BlockPos(5, 2, 5),
				StarterBlueprints.GREENHOUSE, new BlockPos(6, 1, 9), StarterBlueprints.CLOCK_TOWER, new BlockPos(6, 10, 1),
				StarterBlueprints.PUMP_HOUSE, new BlockPos(6, 1, 4));
			List<BlockPos> machines = new java.util.ArrayList<>();
			for (int row = 0; row < 2; row++) {
				List<StarterBlueprints.Entry> builds = StarterBlueprints.MORE_CREATE_BUILDS.subList(row * 3, row * 3 + 3);
				int x = -(builds.stream().mapToInt(e -> e.size().getX() + 3).sum() - 3) / 2;
				for (StarterBlueprints.Entry entry : builds) {
					BlockPos at = STATION.offset(x, 0, 4 + row * 16);
					level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, at, at,
						new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), at,
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
					BuildSiteManager.get(level).recordFinished(entry.id(), placement, player.getUUID());
					io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds.finished(level, entry.id(), placement, player);
					machines.add(at.offset(machine.get(entry)));
					x += entry.size().getX() + 3;
				}
			}
			return l -> machines.stream().allMatch(m -> io.github.jcondedata.aliveworkplace.mechanic.MachineWork.turning(l, m, 0.01f));
		}, null));
		// Trains link villages (ROADMAP 39.7 and 39.7a, filmed with Create): two Train Stations end to end, Create track laid
		// between them, a Village Hall behind each (Thornholm west, Ashford east) with a storehouse chest and a jobless
		// villager by its station. Create's railway knows both stations; the village whose turn it is puts its own train
		// together at its station (a bogey, train casing, the caravan's chest, controls both ways and its villager in the
		// seat as conductor) and Create runs it on its schedule between the two. Thornholm's caravan of oak logs waits at
		// its station, rides the train and comes off at Ashford's, unloaded there (B193: both stations noted before the
		// caravan leaves, and a wait long enough for a slow runner).
		// 39.7b: Thornholm's builder builds the train from its blueprint out of the bench's barrel (the bogey from train
		// casing), and the caravan's logs ride in the carriage's chest: loaded at Thornholm, taken out at Ashford (what the
		// chest holds is shown over the hotbar the whole time).
		// 39.7d: Thornholm has a Conductor (maroon cap and tunic) at its station as well as the jobless villager: he takes
		// the seat, and calls the stop when the train comes in at Ashford.
		SCENES.put("village_train", new Job("Thornholm's builder built the train, its Conductor took the seat, and the caravan's 32 oak logs filled its chest at Thornholm and came out of it at Ashford",
			4000, new Vec3(2.5, -50.0, 34.0), new Vec3(2.5, -59.0, 6.0), (level, player) -> {
			level.setDayTime(2000);
			Showcase.check(io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("create")
				&& io.github.jcondedata.aliveworkplace.city.CaravanTrains.live()
				&& io.github.jcondedata.aliveworkplace.city.CaravanTrains.RAILWAY.present(), "Create is loaded and village trains run");
			BlockPos west = STATION.offset(-16, 0, 4);
			BlockPos east = STATION.offset(8, 0, 4);
			for (BlockPos at : List.of(west, east)) {
				level.getStructureManager().get(StarterBlueprints.TRAIN_STATION.id()).orElseThrow().placeInWorld(level, at, at,
					new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
			}
			BlockState track = level.getBlockState(west.offset(0, 1, 6));
			for (int x = west.getX() + 13; x < east.getX(); x++) { // the track laid between the two stations
				level.setBlockAndUpdate(new BlockPos(x, west.getY(), west.getZ() + 6), Blocks.GRAVEL.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(x, west.getY() + 1, west.getZ() + 6), track);
			}
			BlockPos thornholm = STATION.offset(-10, 0, -6);
			BlockPos ashford = STATION.offset(14, 0, -6);
			io.github.jcondedata.aliveworkplace.hall.Caravans.Data data = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level);
			for (BlockPos hall : List.of(thornholm, ashford)) {
				level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
				level.setBlockAndUpdate(hall.east(3), ModBlocks.STOREHOUSE.defaultBlockState());
				level.setBlockAndUpdate(hall.east(3).north(2), Blocks.CHEST.defaultBlockState());
				data.remove(hall);
			}
			for (BlockPos platform : List.of(west.offset(4, 1, 3), east.offset(8, 1, 3))) { // a villager waiting on each platform
				Villager waiting = EntityType.VILLAGER.spawn(level, platform, MobSpawnType.COMMAND);
				waiting.setNoAi(true);
			}
			((Container) level.getBlockEntity(thornholm.east(3).north(2))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
			data.setWants(thornholm, Component.literal("Thornholm"), List.of());
			data.setWants(ashford, Component.literal("Ashford"), List.of(new io.github.jcondedata.aliveworkplace.hall.Caravans.Want(Items.OAK_LOG, 32)));
			data.toggleRoute(thornholm, ashford);
			data.sent(thornholm, io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level)); // today's caravan goes once the train runs
			BlockPos meet = STATION.offset(2, 0, -6); // the road between them, built and met in the middle, its track laid
			data.setHalf(thornholm, ashford, new io.github.jcondedata.aliveworkplace.hall.Caravans.Half(meet, true, false, true));
			data.setHalf(ashford, thornholm, new io.github.jcondedata.aliveworkplace.hall.Caravans.Half(meet, true, false, true));
			data.setTracked(thornholm, ashford, true);
			data.setTracked(ashford, thornholm, true);
			io.github.jcondedata.aliveworkplace.city.CaravanTrains.noteStations(level, thornholm, data); // both stations known (B193)
			io.github.jcondedata.aliveworkplace.city.CaravanTrains.noteStations(level, ashford, data);
			Showcase.check(io.github.jcondedata.aliveworkplace.city.CaravanTrains.rides(data, thornholm, ashford), "the two villages are on a railway");
			Villager conductor = EntityType.VILLAGER.spawn(level, west.offset(6, 1, 3), MobSpawnType.COMMAND); // 39.7d: Thornholm's Conductor
			conductor.setNoAi(true);
			io.github.jcondedata.aliveworkplace.work.Stations.assign(level, conductor, data.stations(thornholm).get(0),
				io.github.jcondedata.aliveworkplace.registry.ModVillagers.CONDUCTOR);
			Showcase.check(io.github.jcondedata.aliveworkplace.city.CaravanTrains.isConductor(conductor), "Thornholm's station has a Conductor");
			io.github.jcondedata.aliveworkplace.city.CaravanTrains.lastCall = null;
			Container chest = (Container) level.getBlockEntity(ashford.east(3).north(2));
			Container home = (Container) level.getBlockEntity(thornholm.east(3).north(2));
			BlockPos bench = thornholm.offset(-3, 0, 2); // Thornholm's builder, with the train's parts in the barrel by the bench
			level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
			level.setBlockAndUpdate(bench.west(), Blocks.BARREL.defaultBlockState());
			Container parts = (Container) level.getBlockEntity(bench.west());
			java.util.function.Function<String, net.minecraft.world.item.Item> create = path -> net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", path));
			parts.setItem(0, new ItemStack(create.apply("railway_casing"), 3));
			parts.setItem(4, new ItemStack(Items.SPRUCE_FENCE, 3)); // 39.7c: the drawn train's posts, canopy and lantern
			parts.setItem(5, new ItemStack(Items.SPRUCE_SLAB, 4));
			parts.setItem(6, new ItemStack(Items.LANTERN, 1));
			parts.setItem(1, new ItemStack(create.apply("controls"), 2));
			parts.setItem(2, new ItemStack(create.apply("brown_seat"), 1));
			parts.setItem(3, new ItemStack(Items.CHEST, 1));
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(3, level.getServer());
			Villager trainBuilder = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
			Builders.employ(level, trainBuilder, bench);
			java.util.UUID owner = io.github.jcondedata.aliveworkplace.city.CaravanTrains.railwayId(thornholm, ashford);
			int[] tick = {0};
			boolean[] sent = {false};
			boolean[] aboard = {false};
			boolean[] filled = {false};
			return l -> {
				if (tick[0] % 20 == 0) { // what the train's chest holds, over the hotbar
					int logs = io.github.jcondedata.aliveworkplace.city.CaravanTrains.RAILWAY.call(r -> r.cargo(l, owner), List.<ItemStack>of()).stream()
						.filter(st -> st.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum();
					player.displayClientMessage(Component.literal("Train's chest: " + logs + " oak logs"), true);
				}
				if (++tick[0] % 10 == 0) {
					for (BlockPos hall : List.of(thornholm, ashford)) {
						io.github.jcondedata.aliveworkplace.city.CaravanTrains.serve(l, hall, (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) l.getBlockEntity(hall));
					}
				}
				if (!sent[0]) {
					if (io.github.jcondedata.aliveworkplace.city.CaravanTrains.realTrain(l, thornholm, ashford)) {
						Showcase.check(true, "the village put its own Create train together at its station");
						data.sent(thornholm, -1);
						data.setWants(thornholm, Component.literal("Thornholm"), List.of()); // the halls' own rounds count their villagers' wants
						data.setWants(ashford, Component.literal("Ashford"), List.of(new io.github.jcondedata.aliveworkplace.hall.Caravans.Want(Items.OAK_LOG, 32)));
						io.github.jcondedata.aliveworkplace.hall.Caravans.round(l, thornholm, null);
						io.github.jcondedata.aliveworkplace.hall.Caravans.Shipment s = data.onTheRoad().stream().filter(x -> x.to().equals(ashford)).findFirst().orElse(null);
						Showcase.check(s != null && s.byTrain() && io.github.jcondedata.aliveworkplace.city.CaravanTrains.waiting(s.arrives()),
							"Thornholm's caravan waits at its station for the train");
						sent[0] = true;
					}
					return false;
				}
				io.github.jcondedata.aliveworkplace.hall.Caravans.Shipment s = data.onTheRoad().stream().filter(x -> x.to().equals(ashford)).findFirst().orElse(null);
				if (s != null && io.github.jcondedata.aliveworkplace.city.CaravanTrains.aboard(s.arrives()) && !aboard[0]) {
					aboard[0] = true;
					Showcase.check(true, "the caravan boarded the train");
				}
				if (s != null && s.arrives() == io.github.jcondedata.aliveworkplace.city.CaravanTrains.IN_CHEST && !filled[0]) {
					filled[0] = true;
					Showcase.check(io.github.jcondedata.aliveworkplace.city.CaravanTrains.RAILWAY.call(r -> r.cargo(l, owner), List.<ItemStack>of()).stream()
						.filter(st -> st.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum() == 32, "the caravan's 32 logs filled the train's chest at Thornholm");
				}
				// both storehouses are in each hall's reach, so the logs may go into either chest: all 64 there, the caravan in
				boolean in = s == null && aboard[0] && chest.countItem(Items.OAK_LOG) + home.countItem(Items.OAK_LOG) == 64;
				if (in) {
					Showcase.check(filled[0] && io.github.jcondedata.aliveworkplace.city.CaravanTrains.RAILWAY.call(r -> r.cargo(l, owner), List.<ItemStack>of()).isEmpty(),
						"the train came in at Ashford's station and its chest emptied into Ashford's storehouse");
					Showcase.check(l.getEntity(conductor.getUUID()) instanceof Villager rider && rider.isPassenger(), "the village's Conductor rode the train, not the jobless villager");
					Showcase.check(io.github.jcondedata.aliveworkplace.city.CaravanTrains.lastCall != null, "the Conductor called the stop as the train came in");
				}
				return in;
			};
		}, null));
		// Builders build contraptions (ROADMAP 39.5, filmed with Create): a player's own drive (a water wheel under a header
		// tank, two shafts, a three-block belt and two cogwheels) stands behind the camera, is saved as a blueprint the way a
		// Structure Block saves it and taken away, and a builder puts it up in front of the camera from the barrels: what
		// holds it up first, then the shafts, the cogwheels and the belt last (the belt finding its controller in the new
		// place). Once it's done the builder checks it, and every part turns.
		SCENES.put("create_contraption", new Job("the builder put up the saved contraption, its shafts, cogwheels and belt last, and checked it: every part turned",
			7000, new Vec3(5.5, -54.5, 9.5), new Vec3(-0.5, -59.0, -2.5), (level, player) -> {
			level.setDayTime(2000);
			Showcase.check(io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("create")
				&& io.github.jcondedata.aliveworkplace.mechanic.Contraptions.active(), "Create is loaded and builders build contraptions");
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(3, level.getServer());
			java.util.function.Function<String, BlockState> block = text -> {
				try {
					return net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
						io.github.jcondedata.aliveworkplace.mc.Lookup.lookup(net.minecraft.core.registries.BuiltInRegistries.BLOCK), text, false).blockState();
				} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
					throw new IllegalStateException(text, e);
				}
			};
			// The drive, from its corner (the same one ContraptionCompatTests builds)
			Map<BlockPos, BlockState> drive = new LinkedHashMap<>();
			for (int x = 0; x < 8; x++) {
				for (int z = 2; z < 9; z++) {
					if (x != 1 || z < 4 || z > 7) {
						drive.put(new BlockPos(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
					}
				}
			}
			for (int y = 1; y <= 2; y++) {
				drive.put(new BlockPos(0, y, 3), Blocks.STONE_BRICKS.defaultBlockState());
				drive.put(new BlockPos(2, y, 3), Blocks.STONE_BRICKS.defaultBlockState());
			}
			for (int x = 0; x <= 2; x++) {
				for (int z = 3; z <= 5; z++) {
					if (x != 1 || z != 4) {
						drive.put(new BlockPos(x, 3, z), Blocks.STONE_BRICKS.defaultBlockState());
					}
				}
			}
			drive.put(new BlockPos(1, 4, 4), block.apply("minecraft:stone_brick_slab[type=bottom]"));
			drive.put(new BlockPos(1, 1, 5), block.apply("create:water_wheel[facing=east]"));
			drive.put(new BlockPos(2, 1, 5), block.apply("create:shaft[axis=x]"));
			drive.put(new BlockPos(3, 1, 5), block.apply("create:shaft[axis=x]"));
			for (String[] belt : new String[][] {{"5", "start"}, {"4", "middle"}, {"3", "end"}}) {
				drive.put(new BlockPos(4, 1, Integer.parseInt(belt[0])),
					block.apply("create:belt[facing=north,slope=horizontal,casing=false,part=" + belt[1] + "]"));
			}
			drive.put(new BlockPos(5, 1, 3), block.apply("create:cogwheel[axis=x]"));
			drive.put(new BlockPos(5, 2, 3), block.apply("create:cogwheel[axis=x]"));
			BlockPos original = STATION.offset(-4, 0, 30); // behind the camera
			drive.forEach((p, state) -> level.setBlockAndUpdate(original.offset(p), state));
			level.setBlockAndUpdate(original.offset(1, 3, 4), Blocks.WATER.defaultBlockState());
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("players/drive_line");
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), STATION.offset(-4, 0, -7),
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
			BlockPos bench = STATION.offset(5, 0, 1);
			int[] tick = {0};
			BuildSite[] site = {null};
			return l -> {
				if (++tick[0] == 80) { // the belt has worked out its length, the water runs: saved, taken away, built here
					net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = l.getStructureManager().getOrCreate(id);
					template.fillFromWorld(l, original, new net.minecraft.core.Vec3i(8, 6, 9), false, Blocks.STRUCTURE_VOID);
					for (int y = 5; y >= 0; y--) {
						for (int x = 0; x < 8; x++) {
							for (int z = 0; z < 9; z++) {
								l.setBlock(original.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
							}
						}
					}
					l.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
					Villager builder = EntityType.VILLAGER.spawn(l, bench.south(), MobSpawnType.COMMAND);
					Builders.employ(l, builder, bench);
					site[0] = Builders.start(l, builder, null, id, placement);
					Showcase.check(site[0] != null && site[0].kineticOrder(), "the builder took the contraption on, to build in a contraption's order");
					if (site[0] == null) {
						return false;
					}
					List<ItemStack> stacks = new ArrayList<>();
					site[0].plan(l).materials().forEach((item, total) -> {
						for (int left = total; left > 0; left -= item.getDefaultMaxStackSize()) {
							stacks.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
						}
					});
					for (int b = 0; b * 27 < stacks.size(); b++) {
						BlockPos at = bench.offset(1, 0, b);
						l.setBlockAndUpdate(at, Blocks.BARREL.defaultBlockState());
						Container barrel = (Container) l.getBlockEntity(at);
						for (int slot = 0; slot < 27 && b * 27 + slot < stacks.size(); slot++) {
							barrel.setItem(slot, stacks.get(b * 27 + slot));
						}
					}
				}
				if (site[0] == null) {
					return false;
				}
				java.util.Optional<List<BlockPos>> still = BuildSiteManager.get(l).notTurning(placement);
				if (still.isEmpty()) {
					return false;
				}
				Showcase.check(still.get().isEmpty(), "the builder's check found every part turning (not: " + still.get() + ")");
				return true;
			};
		}, null));
		// Create schematics at the Blueprint Table (ROADMAP 39.6, filmed with Create): the player's own kiosk, uploaded with
		// Create's Schematic Table (its file in the server's schematics/uploaded/<player>/, one block in it from a mod that
		// isn't installed), and the written Schematic in hand used on the Blueprint Table: the file is imported (the odd
		// block counted and named), and a builder puts the kiosk up from the barrels in front of the camera.
		SCENES.put("create_schematic", new Job("the written Schematic used on the Blueprint Table imported its uploaded file, and the builder put the schematic's kiosk up",
			6000, new Vec3(4.5, -55.5, 6.5), new Vec3(-1.0, -59.0, -4.0), (level, player) -> {
			level.setDayTime(2000);
			Showcase.check(io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("create")
				&& io.github.jcondedata.aliveworkplace.table.CreateSchematics.active(), "Create is loaded and the table takes its schematics");
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(3, level.getServer());
			java.util.function.Function<String, BlockState> block = text -> {
				try {
					return net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
						io.github.jcondedata.aliveworkplace.mc.Lookup.lookup(net.minecraft.core.registries.BuiltInRegistries.BLOCK), text, false).blockState();
				} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
					throw new IllegalStateException(text, e);
				}
			};
			// The kiosk, 5 × 5 × 5: a stone brick floor, spruce corners and planks, a window each side, a millstone and a
			// cogwheel inside, a slab roof; the doorway in the front (z = 0)
			Map<BlockPos, BlockState> kiosk = new LinkedHashMap<>();
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					kiosk.put(new BlockPos(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
					kiosk.put(new BlockPos(x, 4, z), block.apply("minecraft:spruce_slab[type=bottom]"));
					boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
					boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
					for (int y = 1; y <= 3; y++) {
						if (corner) {
							kiosk.put(new BlockPos(x, y, z), Blocks.SPRUCE_LOG.defaultBlockState());
						} else if (wall && !(z == 0 && x == 2 && y <= 2)) {
							boolean window = y == 2 && (x == 2 || z == 2);
							kiosk.put(new BlockPos(x, y, z), window ? block.apply(x == 2 ? "minecraft:glass_pane[east=true,west=true]"
								: "minecraft:glass_pane[north=true,south=true]") : Blocks.SPRUCE_PLANKS.defaultBlockState());
						}
					}
				}
			}
			kiosk.put(new BlockPos(2, 1, 3), block.apply("create:millstone"));
			kiosk.put(new BlockPos(1, 1, 3), block.apply("create:andesite_casing"));
			kiosk.put(new BlockPos(1, 2, 3), block.apply("create:cogwheel[axis=y]"));
			kiosk.put(new BlockPos(3, 1, 3), block.apply("minecraft:barrel[facing=up]"));
			net.minecraft.nbt.CompoundTag file = new net.minecraft.nbt.CompoundTag();
			net.minecraft.nbt.ListTag size = new net.minecraft.nbt.ListTag();
			for (int v : new int[] {5, 5, 5}) {
				size.add(net.minecraft.nbt.IntTag.valueOf(v));
			}
			file.put("size", size);
			net.minecraft.nbt.ListTag palette = new net.minecraft.nbt.ListTag();
			net.minecraft.nbt.ListTag blocks = new net.minecraft.nbt.ListTag();
			Map<BlockState, Integer> index = new java.util.HashMap<>();
			java.util.function.BiConsumer<BlockPos, Integer> add = (pos, state) -> {
				net.minecraft.nbt.CompoundTag b = new net.minecraft.nbt.CompoundTag();
				net.minecraft.nbt.ListTag at = new net.minecraft.nbt.ListTag();
				at.add(net.minecraft.nbt.IntTag.valueOf(pos.getX()));
				at.add(net.minecraft.nbt.IntTag.valueOf(pos.getY()));
				at.add(net.minecraft.nbt.IntTag.valueOf(pos.getZ()));
				b.put("pos", at);
				b.putInt("state", state);
				blocks.add(b);
			};
			kiosk.forEach((pos, state) -> add.accept(pos, index.computeIfAbsent(state, s -> {
				palette.add(net.minecraft.nbt.NbtUtils.writeBlockState(s));
				return palette.size() - 1;
			})));
			net.minecraft.nbt.CompoundTag gizmo = new net.minecraft.nbt.CompoundTag(); // a block of a mod the server doesn't have
			gizmo.putString("Name", "steamworks:brass_gauge");
			palette.add(gizmo);
			add.accept(new BlockPos(3, 2, 3), palette.size() - 1);
			file.put("palette", palette);
			file.put("blocks", blocks);
			file.put("entities", new net.minecraft.nbt.ListTag());
			file.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
			String owner = player.getGameProfile().getName();
			java.nio.file.Path path = io.github.jcondedata.aliveworkplace.table.CreateSchematics.uploadedFolder(level.getServer()).resolve(owner)
				.resolve("Riverside Kiosk.nbt");
			try {
				java.nio.file.Files.createDirectories(path.getParent());
				net.minecraft.nbt.NbtIo.writeCompressed(file, path);
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
			// The written Schematic, as Create's Schematic Table hands it back (every component, its bounds too: Create's
			// client reads them while it is in hand, B189), used on the Blueprint Table
			BlockPos table = STATION.offset(3, 0, 1);
			level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
			ItemStack schematic = io.github.jcondedata.aliveworkplace.table.CreateSchematics.written(owner, "Riverside Kiosk.nbt",
				new net.minecraft.core.Vec3i(5, 5, 5));
			player.setItemInHand(InteractionHand.MAIN_HAND, schematic);
			level.getBlockState(table).useItemOn(schematic, level, player, InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.table.CreateSchematics.idFor(owner, "Riverside Kiosk.nbt");
			Showcase.check(BlueprintLibrary.get(level.getServer(), id).isPresent()
				&& io.github.jcondedata.aliveworkplace.table.TableServer.listing(level.getServer()).stream().anyMatch(e -> e.id().equals(id)),
				"the written Schematic imported its file, and the table lists it (" + id + ")");
			// A builder puts it up in front of the camera
			BlockPos bench = STATION.offset(5, 0, 1);
			level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
			Villager builder = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
			Builders.employ(level, builder, bench);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), STATION.offset(-3, 0, -7),
				net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
			BuildSite site = Builders.start(level, builder, null, id, placement);
			Showcase.check(site != null, "the builder took the imported schematic on");
			if (site == null) {
				return l -> false;
			}
			BuildPlan plan = site.plan(level);
			List<ItemStack> stacks = new ArrayList<>();
			plan.materials().forEach((item, total) -> {
				for (int left = total; left > 0; left -= item.getDefaultMaxStackSize()) {
					stacks.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
				}
			});
			for (int b = 0; b * 27 < stacks.size(); b++) {
				BlockPos at = bench.offset(1, 0, b);
				level.setBlockAndUpdate(at, Blocks.BARREL.defaultBlockState());
				Container barrel = (Container) level.getBlockEntity(at);
				for (int slot = 0; slot < 27 && b * 27 + slot < stacks.size(); slot++) {
					barrel.setItem(slot, stacks.get(b * 27 + slot));
				}
			}
			return l -> {
				if (BuildSiteManager.get(l).get(site.id()) != null) {
					return false;
				}
				Showcase.check(plan.unfinished(l).isEmpty(), "the kiosk stands as the schematic has it");
				Showcase.check(l.getBlockState(placement.origin().offset(2, 1, 3)).is(block.apply("create:millstone").getBlock()),
					"the schematic's millstone stands in the kiosk");
				return true;
			};
		}, null));
		// Desert raiders (ROADMAP 32.9): a Town behind three Stone Walls at the desert's edge, the raiders' Tomb Camp out to
		// the west with its Pharaoh and his husks. A husk climber piles a sand ramp against the middle wall, a block at a
		// time, one up for one out, until it is level with the top, and walks up and over; then one of the Pharaoh's husks
		// falls by his tomb and gets up again three seconds later in a burst of sand.
		SCENES.put("desert_raiders", new Job("the desert raiders' tomb camp stood west of the Town; a husk piled a sand ramp against the Stone Wall and climbed over, and a husk that fell near the Pharaoh got up again", 1500,
			new Vec3(20.5, -48.0, 22.5), new Vec3(-4.5, -58.0, -2.0), (level, player) -> {
			BlockPos hall = STATION.north(12);
			BuildSiteManager sites = BuildSiteManager.get(level);
			BlockPos origin = STATION.offset(-3, 0, -4);
			for (BlockPos at : List.of(origin.west(7), origin, origin.east(7))) {
				level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, at, at,
					new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
				sites.recordFinished(StarterBlueprints.STONE_WALL.id(), new BlueprintData.Placement(level.dimension().location(), at,
					net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			}
			for (int y = 0; y < 4; y++) {
				for (int z = -18; z <= -3; z++) {
					level.setBlock(STATION.offset(-10, y, z), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(STATION.offset(10, y, z), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				for (int x = -10; x <= 10; x++) {
					level.setBlock(STATION.offset(x, y, -18), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			// The desert outside the walls.
			for (int x = -40; x <= 24; x++) {
				for (int z = -2; z <= 30; z++) {
					level.setBlock(STATION.offset(x, -1, z), net.minecraft.world.level.block.Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			Villager villager = EntityType.VILLAGER.spawn(level, STATION.offset(0, 0, -10), MobSpawnType.COMMAND);
			villager.setNoAi(true);
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.forget(hall);
			io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level).forgetSiege(hall);
			io.github.jcondedata.aliveworkplace.threat.Lairs.forget(level);
			io.github.jcondedata.aliveworkplace.threat.Risings.forget();
			level.setDayTime(1000);
			// The Tomb Camp, west of the walls (the hall of a Town for as long as the camp is made).
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN);
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("desert_raiders");
			io.github.jcondedata.aliveworkplace.threat.Lairs.Lair lair = io.github.jcondedata.aliveworkplace.threat.Lairs.found(level, hall, STATION.offset(-26, -1, 14), id);
			level.setBlockAndUpdate(hall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			Showcase.check(lair != null && io.github.jcondedata.aliveworkplace.threat.Lairs.captainName(lair).getString().startsWith("Pharaoh "),
				"the desert raiders opened their Tomb Camp, under a named Pharaoh");
			java.util.UUID pharaoh = lair.captain();
			Showcase.check(level.getEntity(pharaoh) instanceof net.minecraft.world.entity.Mob p && p.getTags().contains(io.github.jcondedata.aliveworkplace.threat.Risings.RAISER_TAG),
				"the Pharaoh raises the fallen");
			// The siege: two husk climbers outside the middle wall.
			io.github.jcondedata.aliveworkplace.threat.Culture culture = io.github.jcondedata.aliveworkplace.threat.Threats.get(id).orElseThrow();
			io.github.jcondedata.aliveworkplace.threat.Culture.Member member = culture.roster().stream()
				.filter(m -> m.role() == io.github.jcondedata.aliveworkplace.threat.Culture.Role.CLIMBER).findFirst().orElseThrow();
			List<net.minecraft.world.entity.Mob> husks = new java.util.ArrayList<>();
			for (double[] at : new double[][] {{0.5, 8.5}, {2.5, 11.5}}) {
				net.minecraft.world.entity.Mob husk = io.github.jcondedata.aliveworkplace.threat.Threats.create(level, member);
				husk.moveTo(at[0], STATION.getY(), at[1], 180f, 0f);
				husk.finalizeSpawn(level, level.getCurrentDifficultyAt(husk.blockPosition()), MobSpawnType.EVENT, null);
				io.github.jcondedata.aliveworkplace.threat.Threats.outfit(level, husk, culture, member);
				husk.setTarget(villager);
				level.addFreshEntityWithPassengers(husk);
				husks.add(husk);
			}
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.track(level, hall, culture, husks);
			Showcase.check(io.github.jcondedata.aliveworkplace.threat.Sieges.siege(level, hall).isPresent(), "the desert raid on the walled Town is a siege");
			long[] fell = {-1};
			return l -> {
				boolean over = husks.stream().anyMatch(h -> io.github.jcondedata.aliveworkplace.threat.SandRamps.over(l, hall, h.getUUID()));
				if (over && fell[0] < 0 && l.getEntity(pharaoh) instanceof net.minecraft.world.entity.Mob p) {
					// One of his husks falls by the tomb.
					List<net.minecraft.world.entity.Mob> band = io.github.jcondedata.aliveworkplace.threat.Lairs.band(l, lair).stream()
						.filter(m -> m != p && m.getType() == EntityType.HUSK && m.distanceTo(p) < 20).toList();
					if (!band.isEmpty()) {
						band.get(0).hurt(l.damageSources().fellOutOfWorld(), 1000f);
						fell[0] = l.getGameTime();
					}
				}
				return over && fell[0] >= 0 && l.getGameTime() - fell[0] > 80 && !l.getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class,
					new net.minecraft.world.phys.AABB(STATION).inflate(60), h -> h.isAlive() && h.getTags().contains(io.github.jcondedata.aliveworkplace.threat.Risings.RISEN_TAG)).isEmpty();
			};
		}, null));
		// Piglin incursions (ROADMAP 32.10): the piglins' Blackstone Outpost west of a Town, its portal lit. At dusk a raid
		// steps out of that portal and makes for the villagers; on the way a piglin passes the village's chests of gold and
		// carries a stack off on its head.
		SCENES.put("piglins", new Job("the piglins' blackstone outpost stood west of the Town; a raid stepped out of its portal and a piglin passing the village's chests carried off a stack of gold", 1500,
			new Vec3(-6.5, -50.0, 19.5), new Vec3(-20.5, -58.0, 0.5), (level, player) -> {
			BlockPos hall = STATION.north(12);
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.forget(hall);
			io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level).forgetSiege(hall);
			io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level).forgetPlunder(hall);
			io.github.jcondedata.aliveworkplace.threat.Lairs.forget(level);
			level.setDayTime(12800);
			// The village's chests of gold, on the piglins' way in, and its villagers further east.
			for (int z : new int[] {-2, 2}) {
				BlockPos at = STATION.offset(-11, 0, z);
				level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
				if (level.getBlockEntity(at) instanceof net.minecraft.world.Container chest) {
					chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 64));
					chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_BLOCK, 8));
					chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 12));
				}
			}
			for (int i = 0; i < 3; i++) {
				Villager villager = EntityType.VILLAGER.spawn(level, STATION.offset(2 + i * 2, 0, -1 + i), MobSpawnType.COMMAND);
				villager.setNoAi(true);
			}
			// The Blackstone Outpost (the hall of a Town for as long as it is made).
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN);
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("piglin_incursion");
			io.github.jcondedata.aliveworkplace.threat.Lairs.Lair lair = io.github.jcondedata.aliveworkplace.threat.Lairs.found(level, hall, STATION.offset(-32, -1, 0), id);
			Showcase.check(lair != null && io.github.jcondedata.aliveworkplace.threat.Lairs.captainName(lair).getString().startsWith("Warlord "),
				"the piglins raised their Blackstone Outpost, under a named Warlord");
			Showcase.check(lair != null && io.github.jcondedata.aliveworkplace.threat.Piglins.portal(level, lair) != null, "the outpost's portal is lit");
			io.github.jcondedata.aliveworkplace.threat.Culture culture = io.github.jcondedata.aliveworkplace.threat.Threats.get(id).orElseThrow();
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.Raid raid = io.github.jcondedata.aliveworkplace.guard.VillageRaids.start(level, hall, 12, 0,
				Double.NaN, culture, net.minecraft.util.RandomSource.create(3L));
			level.setBlockAndUpdate(hall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			Showcase.check(raid != null && raid.raiders() > 0, "a raid stepped out of the outpost's portal");
			long[] took = {-1};
			return l -> {
				List<net.minecraft.world.entity.Mob> carrying = l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(STATION).inflate(60),
					m -> m.isAlive() && !io.github.jcondedata.aliveworkplace.threat.Plunder.carried(m).isEmpty());
				if (took[0] < 0 && !carrying.isEmpty()) {
					took[0] = l.getGameTime();
				}
				return took[0] >= 0 && l.getGameTime() - took[0] > 100 && !carrying.isEmpty();
			};
		}, null));
		// Scouts (ROADMAP 32.12): a guard with a spyglass (a Scout) at their grindstone, a bandit camp 40 blocks north and a
		// grassy rise between. Sent to scout it, the scout walks up the rise, raises the spyglass (in hand, where a villager
		// shows what they hold) and comes home; the Scouting Report is in the chest by the grindstone, and the player holds
		// it: a map with a red X on the camp.
		SCENES.put("scouts", new Job("the scout walked up the rise, raised the spyglass at the bandit camp and came home with the Scouting Report; the player held its map, the camp marked with a red X", 2400,
			new Vec3(15.5, -54.0, -10.5), new Vec3(-1.5, -58.5, -24.5), (level, player) -> {
			level.setDayTime(1000);
			BlockPos hall = STATION.south(4);
			io.github.jcondedata.aliveworkplace.threat.Lairs.forget(level);
			io.github.jcondedata.aliveworkplace.guard.Scouts.forget(level);
			// The rise: a grassy mound, stepped so a villager walks up it.
			BlockPos top = STATION.north(18);
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					int h = 4 - Math.max(Math.abs(dx), Math.abs(dz));
					for (int y = 0; y < Math.min(3, h); y++) {
						level.setBlockAndUpdate(top.offset(dx, y, dz), (y == Math.min(3, h) - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState());
					}
				}
			}
			io.github.jcondedata.aliveworkplace.threat.Lairs.Lair lair = io.github.jcondedata.aliveworkplace.threat.Lairs.found(level, hall, STATION.offset(0, -1, -40),
				io.github.jcondedata.aliveworkplace.threat.Threats.BANDITS);
			Showcase.check(lair != null, "the bandits made camp north of the village");
			Villager scout = guard(level, STATION, new ItemStack(Items.IRON_SWORD));
			scout.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
			boolean[] sent = {false};
			boolean[] raised = {false};
			return l -> {
				if (!sent[0]) {
					if (io.github.jcondedata.aliveworkplace.guard.Guards.isGuard(scout)) {
						sent[0] = io.github.jcondedata.aliveworkplace.guard.Scouts.send(l, hall) == io.github.jcondedata.aliveworkplace.guard.Scouts.Sent.SENT;
						Showcase.check(sent[0], "the scout was sent to the bandit camp");
					}
					return false;
				}
				if (scout.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.SPYGLASS) && !raised[0]) {
					raised[0] = true;
					Showcase.check(scout.getY() > STATION.getY() + 1.5, "the scout raised the spyglass on the rise");
				}
				return raised[0] && !io.github.jcondedata.aliveworkplace.guard.Scouts.out(scout)
					&& io.github.jcondedata.aliveworkplace.guard.Scouts.known(l, hall).isPresent();
			};
		}, new After("04_report_in_hand", (level, player) -> {
			Container chest = (Container) level.getBlockEntity(STATION.east(2));
			for (int i = 0; chest != null && i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).is(Items.FILLED_MAP)) {
					ItemStack report = chest.removeItem(i, 1);
					Showcase.check(report.getHoverName().getString().startsWith("Scouting Report"), "the Scouting Report was in the chest by the grindstone");
					player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, report);
					player.setXRot(60f); // looking down at the map in both hands
					player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), 60f);
					break;
				}
			}
		}, -1, 0)));
		// Marching on the lair (ROADMAP 32.13): a City's hall, four guards and a pillager war camp 30 blocks south. The player
		// musters a war party on the Defence page (three of the four under the Rally Banner) and hires the Siege-breakers,
		// then leads them south into the camp; the band turns out six at a time, the Warlord fights at the front (a City)
		// and falls, and the last shot looks at the camp's loot chest.
		SCENES.put("war_party", new Job("the player led three guards and the five Siege-breakers into the pillagers' war camp; the band turned out to meet them, the Warlord fell at the front, and the camp's loot chest was theirs", 1600,
			new Vec3(14.5, -52.0, 4.0), new Vec3(0.5, -59.0, 24.0), (level, player) -> {
			level.setDayTime(1000);
			BlockPos hall = STATION.north(6);
			io.github.jcondedata.aliveworkplace.threat.Lairs.forget(level);
			io.github.jcondedata.aliveworkplace.guard.WarParties.forget(level);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.CITY);
			io.github.jcondedata.aliveworkplace.threat.Lairs.Lair lair = io.github.jcondedata.aliveworkplace.threat.Lairs.found(level, hall, STATION.offset(0, -1, 30),
				io.github.jcondedata.aliveworkplace.AliveWorkplace.id("pillager_warband"));
			Showcase.check(lair != null, "the pillager warband made its war camp south of the City");
			for (int i = 0; i < 4; i++) {
				guard(level, STATION.offset(-6 + 4 * i, 0, -2), new ItemStack(Items.IRON_SWORD));
			}
			// B130: the hall's and the guards' records now, not in vanilla's next task: the muster below reads them
			ScreenshotHarness.fileRecords(level, hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS);
			io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
			player.getInventory().add(new ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.RALLY_BANNER));
			player.getInventory().add(new ItemStack(Items.EMERALD, io.github.jcondedata.aliveworkplace.guard.WarParties.PRICE_EMERALDS));
			player.connection.teleport(STATION.getX() + 0.5, STATION.getY(), STATION.getZ() + 0.5, 0f, 0f);
			int[] ticks = {0};
			boolean[] turned = {false};
			long[] turnedAt = {-1};
			boolean[] felled = {false};
			long start = level.getGameTime();
			return l -> {
				// B130: this check is asked once every 10 client ticks, so ticks[0] counts checks, not ticks: the walk and
				// the Warlord's fall go by the game time (counting checks put the fall at about 3000 ticks, past the limit)
				ticks[0]++;
				long t = l.getGameTime() - start;
				if (ticks[0] == 5) {
					// B130: mustered a few ticks in: the guards take their jobs in a server task after the staging one (picked)
					var mustered = io.github.jcondedata.aliveworkplace.guard.WarParties.muster(l, hall, player);
					int rallied = io.github.jcondedata.aliveworkplace.guard.RallyBannerItem.rally(io.github.jcondedata.aliveworkplace.guard.WarParties.banner(player)).guards().size();
					Showcase.check(mustered == io.github.jcondedata.aliveworkplace.guard.WarParties.Muster.MUSTERED && rallied == 3,
						"three of the four guards mustered under the raised Rally Banner" + (rallied == 3 ? "" : " (" + mustered + ", " + rallied + " on the banner, guards by the hall: "
							+ l.getEntitiesOfClass(Villager.class, io.github.jcondedata.aliveworkplace.hall.VillageHalls.area(hall),
								v -> io.github.jcondedata.aliveworkplace.guard.Guards.isGuard(v)).size() + ")"));
					io.github.jcondedata.aliveworkplace.guard.WarParties.hire(l, hall, player);
					Showcase.check(io.github.jcondedata.aliveworkplace.guard.Mercenaries.near(l, hall).stream()
						.filter(io.github.jcondedata.aliveworkplace.guard.WarParties::isSiegeBreaker).count() == io.github.jcondedata.aliveworkplace.guard.WarParties.BAND,
						"the five Siege-breakers were hired");
				}
				var standing = io.github.jcondedata.aliveworkplace.threat.Lairs.near(l, hall);
				if (standing.isEmpty()) {
					return ticks[0] > 40;
				}
				// The player walks south at the head of the war party, stopping short of the camp's fire.
				if (player.getZ() < STATION.getZ() + 20) {
					player.connection.teleport(player.getX(), player.getY(), Math.min(STATION.getZ() + 20.5, STATION.getZ() + 0.5 + 0.12 * t), 0f, 0f);
				}
				if (!turned[0] && io.github.jcondedata.aliveworkplace.threat.Lairs.band(l, standing.get()).size() > 1
						&& !io.github.jcondedata.aliveworkplace.guard.WarParties.at(l, standing.get()).isEmpty()) {
					turned[0] = true;
					turnedAt[0] = t;
					Showcase.check(true, "the band turned out to meet the war party");
				}
				// The Warlord falls at the front (a scene can't wait out a whole battle): 300 ticks after the band turned out,
				// or at 500 if it never did (B130: at a fixed 900 a slow runner at about 10 ticks a second hit the 80-second limit first).
				boolean fall = !felled[0] && (turnedAt[0] >= 0 ? t >= turnedAt[0] + 300 : t >= 500);
				felled[0] |= fall;
				if (fall && l.getEntity(standing.get().captain()) instanceof net.minecraft.world.entity.LivingEntity warlord) {
					warlord.hurt(l.damageSources().playerAttack(player), 10000f);
				} else if (fall) {
					Showcase.check(false, "the Warlord at the front: captain " + l.getEntity(standing.get().captain()) + ", band "
						+ io.github.jcondedata.aliveworkplace.threat.Lairs.band(l, standing.get()).size() + ", war party at the camp "
						+ io.github.jcondedata.aliveworkplace.guard.WarParties.at(l, standing.get()).size());
				}
				return false;
			};
		}, new After("04_loot", (level, player) -> {
			BlockPos camp = STATION.offset(0, -1, 30);
			for (BlockPos p : BlockPos.betweenClosed(camp.offset(-14, -2, -14), camp.offset(14, 8, 14))) {
				if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
					Showcase.check(true, "the war camp's loot chest was left standing");
					player.connection.teleport(p.getX() + 0.5, p.getY() + 1, p.getZ() - 1.5, 0f, 40f);
					return;
				}
			}
			// B209: the broken camp comes down a few seconds after, its chests' loot spilling out
			var loot = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(camp).inflate(14, 8, 14));
			Showcase.check(!loot.isEmpty(), "the war camp came down and its loot spilled out");
			if (!loot.isEmpty()) {
				player.connection.teleport(loot.get(0).getX(), loot.get(0).getY() + 1, loot.get(0).getZ() - 1.5, 0f, 40f);
			}
		}, -1, 0)));
		// B209: a broken bandit camp comes down. The camp stands south of the village; its chief falls, the camp is gone from
		// the village's records at once, and a couple of seconds later its tents, fire and chests come down top first, the
		// loot spilling out on its trampled ground. B225: the camp is made on a grassy rise, and once it is down the rise is
		// back as it was (its slope and grass, no levelled pad, trampled earth or gravel).
		SCENES.put("camp_cleared", new Job("the bandit chief fell; his camp was forgotten by the village at once and came down top first, its loot spilling out, and the grassy rise it had levelled came back as it was", 900,
			new Vec3(12.5, -53.0, 10.0), new Vec3(0.5, -60.0, 24.0), (level, player) -> {
			level.setDayTime(1000);
			BlockPos hall = STATION.north(6);
			io.github.jcondedata.aliveworkplace.threat.Lairs.forget(level);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			BlockPos ground = STATION.offset(0, -1, 24);
			// A grassy rise across the site, higher to the east (B225): the camp levels it, and puts it back when it goes.
			java.util.Map<BlockPos, Integer> land = new java.util.HashMap<>();
			for (int dx = -12; dx <= 12; dx++) {
				for (int dz = -11; dz <= 11; dz++) {
					int rise = Math.max(0, Math.min(3, (dx + 6) / 4 - Math.max(0, Math.abs(dz) - 8)));
					BlockPos column = ground.offset(dx, 0, dz);
					for (int y = 0; y <= rise; y++) {
						level.setBlock(column.above(y), (y == rise ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 2);
					}
					for (int y = rise + 1; y <= 8; y++) {
						level.setBlock(column.above(y), Blocks.AIR.defaultBlockState(), 2);
					}
					land.put(column, ground.getY() + rise);
				}
			}
			io.github.jcondedata.aliveworkplace.threat.Lairs.Lair lair = io.github.jcondedata.aliveworkplace.threat.Lairs.found(level, hall, ground.above(land.get(ground) - ground.getY()), io.github.jcondedata.aliveworkplace.threat.Threats.BANDITS);
			Showcase.check(lair != null, "the bandits made camp south of the village");
			player.connection.teleport(STATION.getX() + 0.5, STATION.getY(), STATION.getZ() + 0.5, 0f, 0f);
			long start = level.getGameTime();
			boolean[] fallen = {false};
			return l -> {
				long t = l.getGameTime() - start;
				if (lair == null) {
					return t > 40;
				}
				if (!fallen[0]) {
					if (t < 100) {
						return false; // (the camp standing, its band by the fire)
					}
					fallen[0] = true;
					if (l.getEntity(lair.captain()) instanceof net.minecraft.world.entity.LivingEntity chief) {
						chief.hurt(l.damageSources().playerAttack(player), 10000f);
					}
					Showcase.check(io.github.jcondedata.aliveworkplace.threat.Lairs.near(l, hall).isEmpty(), "the chief fell and the village forgot the camp at once");
					return false;
				}
				if (!io.github.jcondedata.aliveworkplace.threat.CampCleanup.clearing(l).isEmpty()) {
					return false;
				}
				boolean gone = !(l.getBlockState(lair.pos()).getBlock() instanceof net.minecraft.world.level.block.CampfireBlock)
					&& !l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(lair.pos()).inflate(10)).isEmpty();
				Showcase.check(gone, "the camp came down and its loot spilled out");
				int off = 0;
				StringBuilder which = new StringBuilder();
				for (var e : land.entrySet()) {
					BlockPos top = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getKey()).below();
					if (top.getY() != e.getValue() || !l.getBlockState(top).is(Blocks.GRASS_BLOCK)) {
						off++;
						if (off <= 3) { // (B288: which column, and what is on it)
							which.append("; ").append(e.getKey().getX()).append(",").append(e.getKey().getZ()).append(" top ")
								.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(top).getBlock()).getPath())
								.append(" at y ").append(top.getY()).append(", was grass at y ").append(e.getValue());
						}
					}
				}
				Showcase.check(off == 0, "the rise the camp levelled is back as it was, grass on top, no trampled earth (" + off + " columns off" + which + ")");
				return true;
			};
		}, null));
		// Witch covens (ROADMAP 32.11): the Coven Hut on its stilts in the swamp north of a village, at night. The coven hexes
		// a villager or two (a swirl of witch-light over them); then its witches come out and stand off the village's
		// palisade, 12 to 20 blocks out, throwing their potions over it at the villagers behind.
		SCENES.put("coven", new Job("the witches' coven hut stood in the swamp north of the village at night; villagers were hexed in a swirl of witch-light, and the witches stood off the palisade throwing their potions over it", 1400,
			new Vec3(15.5, -51.0, 6.5), new Vec3(-1.5, -59.0, -15.5), (level, player) -> {
			BlockPos hall = STATION.south(9);
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.forget(hall);
			io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level).forgetSiege(hall);
			io.github.jcondedata.aliveworkplace.threat.Lairs.forget(level);
			io.github.jcondedata.aliveworkplace.threat.Covens.Data.get(level).forget(hall);
			level.setDayTime(13600);
			// The village's palisade, finished, and three villagers behind it.
			BlockPos wall = STATION.offset(-3, 0, 0);
			level.getStructureManager().get(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.PALISADE.id()).orElseThrow()
				.placeInWorld(level, wall, wall, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
			io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.PALISADE.id(),
				new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), wall,
					net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), java.util.UUID.randomUUID());
			List<Villager> people = new java.util.ArrayList<>();
			for (int i = 0; i < 3; i++) {
				Villager villager = EntityType.VILLAGER.spawn(level, STATION.offset(-2 + i * 2, 0, 4), MobSpawnType.COMMAND);
				villager.setNoAi(true);
				people.add(villager);
			}
			// The Coven Hut (the hall of a Village for as long as the raid is made).
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.VILLAGE);
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("witch_coven");
			io.github.jcondedata.aliveworkplace.threat.Lairs.Lair lair = io.github.jcondedata.aliveworkplace.threat.Lairs.found(level, hall, STATION.offset(0, -1, -36), id);
			Showcase.check(lair != null && io.github.jcondedata.aliveworkplace.threat.Lairs.captainName(lair).getString().startsWith("Coven Mother "),
				"the witches raised their Coven Hut, under a named Coven Mother");
			Showcase.check(lair != null && io.github.jcondedata.aliveworkplace.threat.Covens.cauldron(level, lair) != null, "the hut has the coven's cauldron");
			List<Villager> hexed = lair == null ? List.of() : io.github.jcondedata.aliveworkplace.threat.Covens.hexNight(level, lair, net.minecraft.util.RandomSource.create(2L));
			Showcase.check(!hexed.isEmpty() && hexed.stream().allMatch(io.github.jcondedata.aliveworkplace.people.Sickness::isHexed), "the coven hexed " + hexed.size() + " villagers");
			io.github.jcondedata.aliveworkplace.threat.Culture culture = io.github.jcondedata.aliveworkplace.threat.Threats.get(id).orElseThrow();
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.Raid raid = io.github.jcondedata.aliveworkplace.guard.VillageRaids.start(level, hall, 12, 0,
				Double.NaN, culture, net.minecraft.util.RandomSource.create(4L));
			level.setBlockAndUpdate(hall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			Showcase.check(raid != null && raid.raiders() > 0, "the coven's witches came out to raid");
			List<BlockPos> walls = io.github.jcondedata.aliveworkplace.threat.Sieges.wallBlocks(level, hall);
			int[] potions = {0};
			java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
			long[] began = {-1};
			return l -> {
				if (began[0] < 0) {
					began[0] = l.getGameTime();
				}
				for (net.minecraft.world.entity.projectile.ThrownPotion p : l.getEntitiesOfClass(net.minecraft.world.entity.projectile.ThrownPotion.class,
					new net.minecraft.world.phys.AABB(STATION).inflate(40), p -> p.getOwner() instanceof net.minecraft.world.entity.monster.Witch)) {
					if (seen.add(p.getUUID())) {
						potions[0]++;
					}
				}
				List<net.minecraft.world.entity.Mob> witches = l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(STATION).inflate(60),
					m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Witch && m.getTags().contains(io.github.jcondedata.aliveworkplace.threat.Threats.RAIDER_TAG));
				boolean off = !witches.isEmpty() && witches.stream().allMatch(m -> io.github.jcondedata.aliveworkplace.threat.Covens.wallDistance(m.getX(), m.getZ(), walls) >= 10);
				if (potions[0] >= 4 && off && l.getGameTime() - began[0] > 300) {
					Showcase.check(true, "the witches stood off the palisade and threw " + potions[0] + " potions over it");
					return true;
				}
				return false;
			};
		}, null));
		// Sieges II (ROADMAP 32.5): pillagers who set ladders lay siege to a village behind Stone Walls with no gate. They
		// set a ladder up the wall's outer face, rung by rung; the first climbs over and drops inside; then the guard on the
		// walkway comes along the wall and throws the ladder down. (The scene only times his walk: he stands still, as the
		// scenes' villagers do, and is moved along the walkway once the first pillager is over.)
		SCENES.put("siege_ladders", new Job("pillagers set a ladder against the Stone Wall and one climbed over; the guard on the walkway threw the ladder down", 1200,
			new Vec3(8.5, -56.0, 7.5), new Vec3(0.5, -57.5, -2.0), (level, player) -> {
			BlockPos hall = STATION.north(12);
			BuildSiteManager sites = BuildSiteManager.get(level);
			// Three Stone Walls in a row, all finished builds, their battlements to the south (the camera's side); plain
			// stone brick walls close the yard behind them, so the only way in is over.
			BlockPos origin = STATION.offset(-3, 0, -4);
			for (BlockPos at : List.of(origin.west(7), origin, origin.east(7))) {
				level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, at, at,
					new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
				sites.recordFinished(StarterBlueprints.STONE_WALL.id(), new BlueprintData.Placement(level.dimension().location(), at,
					net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			}
			for (int y = 0; y < 4; y++) {
				for (int z = -18; z <= -3; z++) {
					level.setBlock(STATION.offset(-10, y, z), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(STATION.offset(10, y, z), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				for (int x = -10; x <= 10; x++) {
					level.setBlock(STATION.offset(x, y, -18), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			Villager villager = EntityType.VILLAGER.spawn(level, STATION.offset(0, 0, -10), MobSpawnType.COMMAND);
			villager.setNoAi(true);
			// The guard, on the walkway (the wall's inner row, four blocks up), five blocks along from where the ladder goes.
			Villager guard = EntityType.VILLAGER.spawn(level, STATION.offset(5, 4, -3), MobSpawnType.COMMAND);
			guard.setVillagerData(guard.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD));
			guard.setNoAi(true);
			guard.setYRot(90);
			guard.setYHeadRot(90);
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.forget(hall);
			io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level).forgetSiege(hall);
			io.github.jcondedata.aliveworkplace.threat.Culture culture = io.github.jcondedata.aliveworkplace.threat.Culture.read(
				io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_climbers"), com.google.gson.JsonParser.parseString(
					"{\"roster\": [{\"entity\": \"minecraft:pillager\", \"share\": 1, \"role\": \"climber\"}], \"tactics\": [\"ladders\"]}").getAsJsonObject());
			io.github.jcondedata.aliveworkplace.threat.Culture.Member member = culture.roster().get(0);
			List<net.minecraft.world.entity.Mob> pillagers = new java.util.ArrayList<>();
			for (double[] at : new double[][] {{0.5, 5.5}, {-1.5, 9.5}, {2.5, 13.5}}) {
				net.minecraft.world.entity.Mob pillager = io.github.jcondedata.aliveworkplace.threat.Threats.create(level, member);
				pillager.moveTo(at[0], STATION.getY(), at[1], 180f, 0f);
				pillager.finalizeSpawn(level, level.getCurrentDifficultyAt(pillager.blockPosition()), MobSpawnType.EVENT, null);
				io.github.jcondedata.aliveworkplace.threat.Threats.outfit(level, pillager, culture, member);
				pillager.setTarget(villager);
				level.addFreshEntityWithPassengers(pillager);
				pillagers.add(pillager);
			}
			// The scene's own raid: this culture is no datapack's, so it is put among the loaded ones for the director to find.
			Map<net.minecraft.resources.ResourceLocation, io.github.jcondedata.aliveworkplace.threat.Culture> loaded = new LinkedHashMap<>();
			io.github.jcondedata.aliveworkplace.threat.Threats.all().forEach(c -> loaded.put(c.id(), c));
			loaded.put(culture.id(), culture);
			io.github.jcondedata.aliveworkplace.threat.Threats.setForTest(loaded);
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.track(level, hall, culture, pillagers);
			Showcase.check(io.github.jcondedata.aliveworkplace.threat.Sieges.siege(level, hall).isPresent(), "the raid on the village behind Stone Walls is a siege");
			return l -> {
				boolean over = pillagers.stream().anyMatch(p -> io.github.jcondedata.aliveworkplace.threat.Ladders.over(l, hall, p.getUUID()));
				if (over && guard.getX() > STATION.getX() + 1.5) {
					guard.moveTo(guard.getX() - 0.15, guard.getY(), guard.getZ(), 90f, 0f); // along the walkway, to the ladder's top
				}
				return over && io.github.jcondedata.aliveworkplace.threat.Ladders.thrown(l, hall) > 0
					&& io.github.jcondedata.aliveworkplace.threat.Ladders.rungs(l, hall).isEmpty();
			};
		}, null));
		// Sieges III (ROADMAP 32.6): raiders with axes lay siege to a Gatehouse with a Wall Tower beside it. The two archers
		// of the Guard Posts inside leave them for their battle stations, one on each build, and shoot down at the raiders
		// coming at the portcullis. (These guards think for themselves: the walk, the climb and the shooting are the feature.)
		// B320: a raider at the portcullis stands under the Gatehouse's walkway, out of sight of every station (only murder
		// holes would see him), so the archers shoot the raiders on their way in: the raiders start 30 blocks out and wait
		// there (out of the archers' reach) until both archers are on their stations, or for 30 seconds at most.
		SCENES.put("battle_stations", new Job("the two archers took battle stations on the Gatehouse and the Wall Tower and shot down at the raiders coming at the portcullis", 1500,
			new Vec3(14.5, -51.0, 12.5), new Vec3(1.5, -56.5, -3.0), (level, player) -> {
			BlockPos hall = STATION.north(14);
			BuildSiteManager sites = BuildSiteManager.get(level);
			// The Gatehouse and, east of it, the Wall Tower, both finished builds; the outside is the south, the camera's side.
			Map<BlockPos, StarterBlueprints.Entry> builds = new LinkedHashMap<>();
			builds.put(STATION.offset(-5, 0, -6), StarterBlueprints.GATEHOUSE);
			builds.put(STATION.offset(6, 0, -6), StarterBlueprints.WALL_TOWER);
			builds.forEach((at, entry) -> {
				level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, at, at,
					new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
				sites.recordFinished(entry.id(), new BlueprintData.Placement(level.dimension().location(), at,
					net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			});
			List<Villager> archers = new java.util.ArrayList<>();
			for (int x : new int[] {0, 4}) {
				BlockPos post = STATION.offset(x, 0, -13); // (a Guard Post takes one guard)
				level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
				Villager archer = EntityType.VILLAGER.spawn(level, STATION.offset(x, 0, -11), MobSpawnType.COMMAND);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, archer, post, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD_POST_POI,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD);
				archer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
				archer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(net.minecraft.world.item.Items.BOW));
				archers.add(archer);
			}
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.forget(hall);
			io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level).forgetSiege(hall);
			io.github.jcondedata.aliveworkplace.guard.BattleStations.forget();
			io.github.jcondedata.aliveworkplace.threat.Culture culture = io.github.jcondedata.aliveworkplace.threat.Culture.read(
				io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_axemen"), com.google.gson.JsonParser.parseString(
					"{\"roster\": [{\"entity\": \"minecraft:vindicator\", \"share\": 1, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
			io.github.jcondedata.aliveworkplace.threat.Culture.Member member = culture.roster().get(0);
			List<net.minecraft.world.entity.Mob> raiders = new java.util.ArrayList<>();
			for (double[] at : new double[][] {{0.5, 26.5}, {-2.5, 28.5}, {3.5, 28.5}, {6.5, 27.5}}) {
				net.minecraft.world.entity.Mob raider = io.github.jcondedata.aliveworkplace.threat.Threats.create(level, member);
				raider.moveTo(at[0], STATION.getY(), at[1], 180f, 0f);
				raider.finalizeSpawn(level, level.getCurrentDifficultyAt(raider.blockPosition()), MobSpawnType.EVENT, null);
				io.github.jcondedata.aliveworkplace.threat.Threats.outfit(level, raider, culture, member);
				raider.setNoAi(true); // waiting out of reach until the archers are up (B320)
				level.addFreshEntityWithPassengers(raider);
				raiders.add(raider);
			}
			Map<net.minecraft.resources.ResourceLocation, io.github.jcondedata.aliveworkplace.threat.Culture> loaded = new LinkedHashMap<>();
			io.github.jcondedata.aliveworkplace.threat.Threats.all().forEach(c -> loaded.put(c.id(), c));
			loaded.put(culture.id(), culture);
			io.github.jcondedata.aliveworkplace.threat.Threats.setForTest(loaded);
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.track(level, hall, culture, raiders);
			Showcase.check(io.github.jcondedata.aliveworkplace.threat.Sieges.siege(level, hall).isPresent(), "the raid on the village behind the Gatehouse is a siege");
			List<BlockPos> stations = io.github.jcondedata.aliveworkplace.guard.BattleStations.stations(level, hall);
			Showcase.check(stations.stream().anyMatch(s -> s.getX() < STATION.getX() + 6) && stations.stream().anyMatch(s -> s.getX() >= STATION.getX() + 6),
				"the Gatehouse and the Wall Tower both have battle stations");
			int[] ticks = {0};
			float[] healthAtStart = {-1f};
			return l -> {
				boolean posted = archers.stream().allMatch(io.github.jcondedata.aliveworkplace.guard.BattleStations::onStation);
				if (healthAtStart[0] < 0 && (posted || ++ticks[0] >= 600)) {
					// The archers are up (or have had 30 seconds): the raiders come on. Only hurt from now on counts.
					healthAtStart[0] = 0f;
					for (net.minecraft.world.entity.Mob r : raiders) {
						r.setNoAi(false);
						healthAtStart[0] += r.isAlive() ? r.getHealth() : 0f;
					}
				}
				// B252: on a timeout, say where each archer stood, their station and what they were shooting at
				StringBuilder why = new StringBuilder(healthAtStart[0] < 0 ? "the raiders were still held back (the archers never both on station); " : "");
				for (Villager a : archers) {
					net.minecraft.world.entity.LivingEntity foe = io.github.jcondedata.aliveworkplace.guard.GuardCombat.foe(a);
					why.append(why.length() == 0 ? "" : "; ").append("archer at ").append(String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", a.getX(), a.getY(), a.getZ()))
						.append(a.isAlive() ? "" : " (dead)").append(", station ")
						.append(io.github.jcondedata.aliveworkplace.guard.BattleStations.post(a).map(BlockPos::toShortString).orElse("none"))
						.append(io.github.jcondedata.aliveworkplace.guard.BattleStations.onStation(a) ? " (on it)" : " (not on it)")
						.append(", guard ").append(io.github.jcondedata.aliveworkplace.guard.Guards.isGuard(a))
						.append(", fighting ").append(io.github.jcondedata.aliveworkplace.guard.GuardCombat.isFighting(a))
						.append(", target ").append(foe == null ? "none" : String.format(java.util.Locale.ROOT, "%s at %.1f %.1f %.1f, %.1f off, %s", foe.getType().toShortString(),
							foe.getX(), foe.getY(), foe.getZ(), Math.sqrt(a.distanceToSqr(foe)), a.hasLineOfSight(foe) ? "in sight" : "out of sight"));
				}
				for (net.minecraft.world.entity.Mob r : raiders) {
					why.append("; raider at ").append(String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f, health %.0f/%.0f%s, seen by %d", r.getX(), r.getY(), r.getZ(),
						r.getHealth(), r.getMaxHealth(), r.isAlive() ? "" : " (dead)", archers.stream().filter(a -> a.hasLineOfSight(r)).count()))
						// under the walkway, at the portcullis, no station sees him: he had to be shot on his way in
						.append(r.isAlive() && r.getZ() < STATION.getZ() - 1.5 && r.getZ() > STATION.getZ() - 4 ? " (at the portcullis, under the walkway: out of every station's sight)" : "");
				}
				timeoutWhy = why.toString();
				boolean both = archers.stream().anyMatch(a -> a.getX() < STATION.getX() + 6) && archers.stream().anyMatch(a -> a.getX() >= STATION.getX() + 6);
				float health = 0f;
				for (net.minecraft.world.entity.Mob r : raiders) {
					health += r.isAlive() ? r.getHealth() : 0f;
				}
				return healthAtStart[0] >= 0 && posted && both && health < healthAtStart[0];
			};
		}, null));
		// A day's warning (ROADMAP 32.14): a Gatehouse with a Wall Tower beside it and a Village Hall behind, an archer
		// posted by the tower. The clock has set tonight's attack from the south (the camera's side); the archer saw it
		// coming, so the chat says so. At noon the gates shut and the portcullis drops; at dusk the archer climbs the tower.
		SCENES.put("warning", new Job("the archer posted by the Wall Tower saw the raid coming and the chat said so; at noon the gates shut and the portcullis dropped, and at dusk the archer was on the tower before any raider came", 1500,
			new Vec3(14.5, -51.0, 12.5), new Vec3(1.5, -56.5, -3.0), (level, player) -> {
			long base = (level.getDayTime() / 24000) * 24000;
			level.setDayTime(base + 5800);
			BlockPos hall = STATION.north(14);
			BuildSiteManager sites = BuildSiteManager.get(level);
			Map<BlockPos, StarterBlueprints.Entry> builds = new LinkedHashMap<>();
			builds.put(STATION.offset(-5, 0, -6), StarterBlueprints.GATEHOUSE);
			builds.put(STATION.offset(6, 0, -6), StarterBlueprints.WALL_TOWER);
			builds.forEach((at, entry) -> {
				level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, at, at,
					new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
				sites.recordFinished(entry.id(), new BlueprintData.Placement(level.dimension().location(), at,
					net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			});
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			if (level.getBlockEntity(hall) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity entity) {
				entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			}
			BlockPos post = STATION.offset(4, 0, -13);
			level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
			// The hall's and the post's records now, not in vanilla's next task, so the archer finds his village and his post (B130).
			ScreenshotHarness.fileRecords(level, hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS);
			io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
			Villager archer = EntityType.VILLAGER.spawn(level, STATION.offset(4, 0, -11), MobSpawnType.COMMAND);
			io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, archer, post, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD_POST_POI,
				io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD);
			archer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
			archer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(net.minecraft.world.item.Items.BOW));
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.forget(hall);
			io.github.jcondedata.aliveworkplace.threat.ThreatData data = io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level);
			data.forgetClock(hall);
			data.forgetSiege(hall);
			io.github.jcondedata.aliveworkplace.guard.BattleStations.forget();
			long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			data.rolled(hall, today, new io.github.jcondedata.aliveworkplace.threat.ThreatData.Attack(today, io.github.jcondedata.aliveworkplace.threat.Threats.MONSTERS,
				Math.PI / 2, 17000)); // from the south, late in the night
			Showcase.check(io.github.jcondedata.aliveworkplace.guard.Warnings.warn(level, hall, 10, 1), "the archer by the Wall Tower warned the village");
			Showcase.check(!io.github.jcondedata.aliveworkplace.threat.Sieges.portcullisDown(level, hall), "the portcullis is up in the morning");
			int[] phase = {0};
			return l -> {
				io.github.jcondedata.aliveworkplace.guard.Gates.round(l, hall, 1);
				io.github.jcondedata.aliveworkplace.guard.Warnings.round(l, hall, 10, 1);
				// B130: job scenes freeze the daylight cycle (tick 20), so the morning is run on to noon here, 10 ticks a tick
				if (phase[0] == 0 && l.getDayTime() % 24000 < io.github.jcondedata.aliveworkplace.guard.Warnings.NOON) {
					l.setDayTime(Math.min(l.getDayTime() + 10, base + io.github.jcondedata.aliveworkplace.guard.Warnings.NOON));
				}
				if (phase[0] == 0 && io.github.jcondedata.aliveworkplace.threat.Sieges.portcullisDown(l, hall)) {
					Showcase.check(io.github.jcondedata.aliveworkplace.threat.Sieges.status(l, hall).open() == 0, "the gates shut at noon");
					phase[0] = 1;
				} else if (phase[0] >= 1 && phase[0] < 10) {
					phase[0]++; // a few seconds with the gates shut in the sun (asked every 10 frames: 100 was 50 seconds, B130)
				} else if (phase[0] == 10) {
					l.setDayTime(base + io.github.jcondedata.aliveworkplace.guard.VillageRaids.DUSK + 20);
					phase[0] = 101;
				}
				return phase[0] == 101 && io.github.jcondedata.aliveworkplace.guard.BattleStations.onStation(archer)
					&& io.github.jcondedata.aliveworkplace.threat.ThreatData.get(l).raid(hall).isEmpty();
			};
		}, null));
		SCENES.put("roads", new Job("the builder laid the approved Stonework street between the two houses, 3 wide, segment by segment", 9000,
			new Vec3(0.5, -45, -26), new Vec3(0.5, -60, -6), (level, player) -> {
			// Two cottages (stamped, on the books as built), the hall south of them, a builder with Stonework in his chest,
			// a Steward, and a street drawn on the plan along the cottages' fronts (27.15).
			BlockPos hallAt = STATION.offset(0, 0, 6);
			var cottage = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level,
				io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
			for (BlockPos origin : List.of(STATION.offset(-18, 0, -2), STATION.offset(8, 0, -2))) {
				for (var e : cottage.blocks()) {
					level.setBlock(origin.offset(e.pos()), e.state(), Block.UPDATE_CLIENTS);
				}
				io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(cottage.id(),
					new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(io.github.jcondedata.aliveworkplace.mc.Ids.of(level.dimension()),
						origin, net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			}
			level.setBlockAndUpdate(hallAt, ModBlocks.VILLAGE_HALL.defaultBlockState());
			var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hallAt);
			hall.setOwner(player.getUUID(), player.getGameProfile().getName());
			BlockPos a = STATION.offset(-16, 0, -7).subtract(hallAt);
			BlockPos b = STATION.offset(16, 0, -7).subtract(hallAt);
			hall.setPlan(io.github.jcondedata.aliveworkplace.city.CityPlan.EMPTY.addRoad(new io.github.jcondedata.aliveworkplace.city.CityPlan.Road(
				List.of(new BlockPos(a.getX(), 0, a.getZ()), new BlockPos(b.getX(), 0, b.getZ())),
				io.github.jcondedata.aliveworkplace.city.CityPlan.Road.STREET, "stonework", true)));
			BlockPos table = hallAt.offset(4, 0, 0);
			level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
			chest(level, table.east(), new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(Items.STONE_BRICKS, 64),
				new ItemStack(Items.CRACKED_STONE_BRICKS, 32), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 32),
				new ItemStack(Items.STONE_BRICK_STAIRS, 16),
				// the street's lamp posts
				new ItemStack(Items.STONE_BRICK_WALL, 16), new ItemStack(Items.CHISELED_STONE_BRICKS, 8), new ItemStack(Items.DARK_OAK_FENCE, 24),
				new ItemStack(Items.DARK_OAK_TRAPDOOR, 8), new ItemStack(Items.LANTERN, 8));
			Villager builder = EntityType.VILLAGER.spawn(level, table.south(), MobSpawnType.COMMAND);
			ScreenshotHarness.fileRecords(level, table, 1); // the table's record now, not in vanilla's next task, so the builder keeps his job
			io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, table);
			ItemStack cityPlan = new ItemStack(ModItems.CITY_PLAN);
			io.github.jcondedata.aliveworkplace.city.CityPlanItem.bind(level, player, cityPlan, hallAt);
			Villager steward = EntityType.VILLAGER.spawn(level, hallAt.south(2), MobSpawnType.COMMAND);
			steward.setVillagerData(steward.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER)
				.setLevel(io.github.jcondedata.aliveworkplace.city.Stewards.MIN_BUILDER_LEVEL));
			steward.setVillagerXp(70); // a seasoned Builder (27.1a)
			ScreenshotHarness.appointSteward(player, steward, cityPlan);
			Showcase.check(io.github.jcondedata.aliveworkplace.city.Roads.ENABLED, "stewardRoads is on");
			return l -> {
				var road = hall.plan().roads().get(0);
				return road.finished() && road.segments() >= 2
					&& io.github.jcondedata.aliveworkplace.city.Roads.openSegments(l, hallAt).isEmpty();
			};
		}, null));
		SCENES.put("bridges", new Job("the builder bridged the river 9 wide (2 pillars, rails, a stair up at each end) and lit the street with Street Lamps",
			14000, new Vec3(14.5, -50, -24), new Vec3(0.5, -61, -7), (level, player) -> {
			// Flat ground with a river 9 wide and 2 deep across it, the hall south of it, a builder with Stonework (and the
			// lamps' blocks) in his chest, a Steward, and a 48-block street drawn over the river (27.16).
			for (int x = -27; x <= 27; x++) {
				for (int z = -16; z <= 3; z++) {
					boolean water = Math.abs(x) <= 4;
					level.setBlock(STATION.offset(x, -3, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(STATION.offset(x, -2, z), water ? Blocks.WATER.defaultBlockState() : Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(STATION.offset(x, -1, z), water ? Blocks.WATER.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					for (int y = 0; y < 8; y++) {
						level.setBlock(STATION.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			BlockPos hallAt = STATION.offset(0, 0, 6);
			level.setBlockAndUpdate(hallAt, ModBlocks.VILLAGE_HALL.defaultBlockState());
			var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hallAt);
			hall.setOwner(player.getUUID(), player.getGameProfile().getName());
			BlockPos a = STATION.offset(-24, 0, -7).subtract(hallAt);
			BlockPos b = STATION.offset(24, 0, -7).subtract(hallAt);
			hall.setPlan(io.github.jcondedata.aliveworkplace.city.CityPlan.EMPTY.addRoad(new io.github.jcondedata.aliveworkplace.city.CityPlan.Road(
				List.of(new BlockPos(a.getX(), 0, a.getZ()), new BlockPos(b.getX(), 0, b.getZ())),
				io.github.jcondedata.aliveworkplace.city.CityPlan.Road.STREET, "stonework", true)));
			BlockPos table = hallAt.offset(6, 0, 0);
			level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
			chest(level, table.east(), new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(Items.STONE_BRICKS, 64),
				new ItemStack(Items.CRACKED_STONE_BRICKS, 32), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 32),
				new ItemStack(Items.STONE_BRICK_STAIRS, 32), new ItemStack(Items.STONE_BRICK_WALL, 32), new ItemStack(Items.CHISELED_STONE_BRICKS, 8),
				new ItemStack(Items.DARK_OAK_FENCE, 16), new ItemStack(Items.DARK_OAK_TRAPDOOR, 4), new ItemStack(Items.LANTERN, 8));
			Villager builder = EntityType.VILLAGER.spawn(level, table.south(), MobSpawnType.COMMAND);
			ScreenshotHarness.fileRecords(level, table, 1); // the table's record now, not in vanilla's next task, so the builder keeps his job
			io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, table);
			ItemStack cityPlan = new ItemStack(ModItems.CITY_PLAN);
			io.github.jcondedata.aliveworkplace.city.CityPlanItem.bind(level, player, cityPlan, hallAt);
			Villager steward = EntityType.VILLAGER.spawn(level, hallAt.south(2), MobSpawnType.COMMAND);
			steward.setVillagerData(steward.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER)
				.setLevel(io.github.jcondedata.aliveworkplace.city.Stewards.MIN_BUILDER_LEVEL));
			steward.setVillagerXp(70); // a seasoned Builder (27.1a)
			ScreenshotHarness.appointSteward(player, steward, cityPlan);
			Showcase.check(io.github.jcondedata.aliveworkplace.city.Roads.ENABLED, "stewardRoads is on");
			return l -> {
				var road = hall.plan().roads().get(0);
				boolean done = road.finished() && road.gap() == 0 && io.github.jcondedata.aliveworkplace.city.Roads.openSegments(l, hallAt).isEmpty();
				if (done) {
					l.setDayTime(18000); // midnight: the street lit by its lamps
				}
				return done && road.lamps() >= 2;
			};
		}, null));
		SCENES.put("caravan_road", new Job("each village's builder laid its half of the Stonework road to the other, and the halves met halfway",
			16000, new Vec3(-44.5, -44, 16.5), new Vec3(16.5, -61, -7), (level, player) -> {
			// Two villages 64 blocks apart with a trade route (27.17): each a hall in a Stonework zone, a Steward, and a
			// builder with Stonework (and the lamps' blocks) in his chest. Each plans its half of a road to the other.
			BlockPos[] halls = {STATION.offset(-32, 0, 0), STATION.offset(32, 0, 0)};
			String[] names = {"Ashford", "Bramble"};
			for (int x = -48; x <= 48; x++) {
				for (int z = -18; z <= 10; z++) {
					level.setBlock(STATION.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					for (int y = 0; y < 8; y++) {
						level.setBlock(STATION.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			var data = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level);
			for (int i = 0; i < 2; i++) {
				BlockPos hallAt = halls[i];
				level.setBlockAndUpdate(hallAt, ModBlocks.VILLAGE_HALL.defaultBlockState());
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hallAt);
				hall.setOwner(player.getUUID(), player.getGameProfile().getName());
				java.util.BitSet cells = new java.util.BitSet();
				cells.set(io.github.jcondedata.aliveworkplace.city.CityPlan.cellAt(hallAt, hallAt));
				hall.setPlan(io.github.jcondedata.aliveworkplace.city.CityPlan.EMPTY.addZone("homes", names[i], "stonework").paint(0, cells));
				BlockPos table = hallAt.offset(0, 0, 5);
				level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
				// Enough for a 32-block half with its lamps (B94: one stack of Cobblestone and 8 Dark Oak Fences ran out mid-road)
				chest(level, table.east(), new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(Items.STONE_BRICKS, 64),
					new ItemStack(Items.CRACKED_STONE_BRICKS, 32), new ItemStack(Items.CRACKED_STONE_BRICKS, 32), new ItemStack(Items.COBBLESTONE, 64),
					new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.STONE_BRICK_STAIRS, 32),
					new ItemStack(Items.STONE_BRICK_SLAB, 32), new ItemStack(Items.STONE_BRICK_WALL, 32), new ItemStack(Items.COBBLESTONE_WALL, 16),
					new ItemStack(Items.CHISELED_STONE_BRICKS, 8), new ItemStack(Items.DARK_OAK_FENCE, 32), new ItemStack(Items.DARK_OAK_TRAPDOOR, 8),
					new ItemStack(Items.LANTERN, 16));
				Villager builder = EntityType.VILLAGER.spawn(level, table.south(), MobSpawnType.COMMAND);
				ScreenshotHarness.fileRecords(level, table, 1); // the table's record now, not in vanilla's next task, so the builder keeps his job
				io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, table);
				ItemStack cityPlan = new ItemStack(ModItems.CITY_PLAN);
				io.github.jcondedata.aliveworkplace.city.CityPlanItem.bind(level, player, cityPlan, hallAt);
				Villager steward = EntityType.VILLAGER.spawn(level, hallAt.south(2), MobSpawnType.COMMAND);
				steward.setVillagerData(steward.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER)
					.setLevel(io.github.jcondedata.aliveworkplace.city.Stewards.MIN_BUILDER_LEVEL));
				steward.setVillagerXp(70); // a seasoned Builder (27.1a)
				ScreenshotHarness.appointSteward(player, steward, cityPlan);
				data.setWants(hallAt, Component.literal(names[i]), List.of());
			}
			data.toggleRoute(halls[0], halls[1]);
			Showcase.check(io.github.jcondedata.aliveworkplace.city.Roads.ENABLED && io.github.jcondedata.aliveworkplace.city.CaravanRoads.ENABLED,
				"stewardRoads and caravanRoads are on");
			return l -> data.roadFinished(halls[0], halls[1])
				&& io.github.jcondedata.aliveworkplace.city.Roads.openSegments(l, halls[0]).isEmpty()
				&& io.github.jcondedata.aliveworkplace.city.Roads.openSegments(l, halls[1]).isEmpty();
		}, null));
		SCENES.put("walls", new Job("the builders raised the village's palisade along the wall line (towers at its corners, a gate on the road) "
			+ "and the guards shut the gate at nightfall", 18000, new Vec3(0.5, -42, -30), new Vec3(0.5, -58, -4), (level, player) -> {
			// A small village: the hall, a cottage, three builders with the palisade's blocks, a Steward, a street out
			// to the north and a wall line 11 blocks round the hall, already approved for the Palisade kit (27.18).
			for (int x = -20; x <= 20; x++) {
				for (int z = -20; z <= 16; z++) {
					level.setBlock(STATION.offset(x, -2, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(STATION.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
					for (int y = 0; y < 10; y++) {
						level.setBlock(STATION.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			BlockPos hallAt = STATION;
			var cottage = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level,
				io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
			BlockPos cottageAt = STATION.offset(-4, 0, 4);
			for (var e : cottage.blocks()) {
				level.setBlock(cottageAt.offset(e.pos()), e.state(), Block.UPDATE_CLIENTS);
			}
			io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(cottage.id(),
				new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(io.github.jcondedata.aliveworkplace.mc.Ids.of(level.dimension()),
					cottageAt, net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			level.setBlockAndUpdate(hallAt, ModBlocks.VILLAGE_HALL.defaultBlockState());
			var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hallAt);
			hall.setOwner(player.getUUID(), player.getGameProfile().getName());
			hall.setLastRaidDay(io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level)); // raided today: he wants a wall
			var wall = new io.github.jcondedata.aliveworkplace.city.CityPlan.Wall(
				List.of(new BlockPos(-11, 0, -11), new BlockPos(11, 0, -11), new BlockPos(11, 0, 11), new BlockPos(-11, 0, 11)), true);
			hall.setPlan(io.github.jcondedata.aliveworkplace.city.CityPlan.EMPTY
				.addRoad(new io.github.jcondedata.aliveworkplace.city.CityPlan.Road(List.of(new BlockPos(0, 0, -4), new BlockPos(0, 0, -18)),
					io.github.jcondedata.aliveworkplace.city.CityPlan.Road.STREET, "", true))
				.withWall(wall.approvedWith("palisade")));
			for (int i = 0; i < 3; i++) {
				// East of the hall, clear of the cottage (B94: at z=10 the tables and their builders stood inside its floor)
				BlockPos table = STATION.offset(7, 0, -7 + i * 5);
				level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
				chest(level, table.south(), new ItemStack(Items.SPRUCE_LOG, 64), new ItemStack(Items.SPRUCE_LOG, 64),
					new ItemStack(Items.STRIPPED_SPRUCE_LOG, 64), new ItemStack(Items.SPRUCE_FENCE, 64), new ItemStack(Items.SPRUCE_SLAB, 64),
					new ItemStack(Items.SPRUCE_PLANKS, 64), new ItemStack(Items.SPRUCE_FENCE_GATE, 16), new ItemStack(Items.DARK_OAK_STAIRS, 64),
					new ItemStack(Items.DARK_OAK_SLAB, 16), new ItemStack(Items.DARK_OAK_PLANKS, 16), new ItemStack(Items.LADDER, 32),
					new ItemStack(Items.LANTERN, 16), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.STONE, 64),
					new ItemStack(Items.ANDESITE, 64), new ItemStack(Items.DIRT, 64),
					// and the street's (B94: the builders waited for its Coarse Dirt, Gravel and Cobblestone Stairs)
					new ItemStack(Items.COARSE_DIRT, 64), new ItemStack(Items.GRAVEL, 32), new ItemStack(Items.COBBLESTONE_STAIRS, 16),
					new ItemStack(Items.COBBLESTONE_SLAB, 16));
				Villager builder = EntityType.VILLAGER.spawn(level, table.east(), MobSpawnType.COMMAND);
				ScreenshotHarness.fileRecords(level, table, 1); // the table's record now, not in vanilla's next task, so the builder keeps his job
				io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, table);
			}
			ItemStack cityPlan = new ItemStack(ModItems.CITY_PLAN);
			io.github.jcondedata.aliveworkplace.city.CityPlanItem.bind(level, player, cityPlan, hallAt);
			Villager steward = EntityType.VILLAGER.spawn(level, hallAt.south(2), MobSpawnType.COMMAND);
			steward.setVillagerData(steward.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER)
				.setLevel(io.github.jcondedata.aliveworkplace.city.Stewards.MIN_BUILDER_LEVEL));
			steward.setVillagerXp(70); // a seasoned Builder (27.1a)
			ScreenshotHarness.appointSteward(player, steward, cityPlan);
			Showcase.check(io.github.jcondedata.aliveworkplace.city.Walls.ENABLED, "stewardWalls is on");
			Showcase.check(!io.github.jcondedata.aliveworkplace.city.Walls.pieces(level, hallAt).isEmpty(), "the wall line has pieces to build");
			return l -> {
				var pieces = io.github.jcondedata.aliveworkplace.city.Walls.pieces(l, hallAt);
				long standing = pieces.stream().filter(p -> io.github.jcondedata.aliveworkplace.city.Walls.stands(l, p)).count();
				var gate = pieces.stream().filter(p -> p.kind() == io.github.jcondedata.aliveworkplace.city.Walls.Kind.GATE).findFirst();
				boolean gateUp = gate.isPresent() && io.github.jcondedata.aliveworkplace.city.Walls.stands(l, gate.get());
				boolean towerUp = pieces.stream().anyMatch(p -> p.kind() == io.github.jcondedata.aliveworkplace.city.Walls.Kind.TOWER
					&& io.github.jcondedata.aliveworkplace.city.Walls.stands(l, p));
				if (!(gateUp && towerUp && standing >= 6)) {
					return false;
				}
				l.setDayTime(18000); // midnight: the guards shut the gate
				io.github.jcondedata.aliveworkplace.guard.Gates.round(l, hallAt, 1);
				for (BlockPos p : BlockPos.betweenClosed(gate.get().box().getCenter().offset(-4, -1, -4), gate.get().box().getCenter().offset(4, 3, 4))) {
					var state = l.getBlockState(p);
					if (state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
						&& state.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN)) {
						return false; // a gate still standing open at night
					}
				}
				return true;
			};
		}, null));
		SCENES.put("composter", job("the composter made bone meal", 1800, (level, player) -> {
			Villager v = picked(level, player, STATION, Blocks.COMPOSTER, Items.BONE_MEAL);
			Container c = chest(level, chestPos(), new ItemStack(Items.PUMPKIN_PIE, 10), new ItemStack(Items.WHEAT_SEEDS, 16));
			return l -> n(ModAttachments.BONE_MEAL_MADE, v) >= 1 && c.countItem(Items.BONE_MEAL) >= 1;
		}));
		SCENES.put("netherworker", new Job("the netherworker came back from the Nether with loot", 3000,
			new Vec3(4.5, -56.3, 6.5), new Vec3(2.5, -58.3, -2), (level, player) -> {
			Villager v = picked(level, player, STATION, Blocks.CARTOGRAPHY_TABLE, Items.NETHERRACK);
			chest(level, chestPos(), new ItemStack(Items.BREAD, 3), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SWORD),
				new ItemStack(Items.IRON_CHESTPLATE));
			// A portal behind the brazier, so the camera sees the worker walk into it and come back.
			for (int x = 3; x <= 6; x++) {
				level.setBlockAndUpdate(new BlockPos(x, -60, -5), Blocks.OBSIDIAN.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(x, -56, -5), Blocks.OBSIDIAN.defaultBlockState());
			}
			for (int y = -59; y <= -57; y++) {
				level.setBlockAndUpdate(new BlockPos(3, y, -5), Blocks.OBSIDIAN.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(6, y, -5), Blocks.OBSIDIAN.defaultBlockState());
			}
			net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(level, new BlockPos(4, -59, -5), Direction.Axis.X)
				.ifPresent(net.minecraft.world.level.portal.PortalShape::createPortalBlocks);
			io.github.jcondedata.aliveworkplace.nether.Netherworkers.TRIP_TICKS = 200;
			return l -> n(ModAttachments.NETHER_TRIPS, v) >= 1 && !io.github.jcondedata.aliveworkplace.nether.Netherworkers.isAway(v);
		}, null));
		SCENES.put("undertaker", new Job("the undertaker brought Mira back from her grave", 2000,
			new Vec3(4.5, -56, 8), new Vec3(-2, -59.5, 1.5), (level, player) -> {
			BlockPos bench = new BlockPos(-8, -60, -3);
			level.setBlockAndUpdate(bench, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
			Villager mira = EntityType.VILLAGER.spawn(level, new BlockPos(-4, -60, 4), MobSpawnType.COMMAND);
			Jobs.employ(level, mira, bench, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
			mira.setVillagerData(mira.getVillagerData().setLevel(3));
			mira.setVillagerXp(80);
			mira.setCustomName(Component.literal("Mira"));
			java.util.UUID id = mira.getUUID();
			mira.kill();
			Villager u = picked(level, player, STATION, Blocks.BREWING_STAND, Items.GOLDEN_APPLE);
			chest(level, STATION.east(2), new ItemStack(Items.GOLDEN_APPLE, 2));
			return l -> n(ModAttachments.VILLAGERS_REVIVED, u) >= 1 && l.getEntity(id) instanceof Villager back && back.isAlive();
		}, null));
		// B222: Mira dies in the street; with no golden apple, the undertaker lays her grave in the plot by the hall (no Graveyard yet)
		SCENES.put("undertaker_graveyard", new Job("the undertaker laid Mira's grave from the street in the plot by the hall", 2400,
			new Vec3(4.5, -54, 12), new Vec3(-2, -59.5, 0), (level, player) -> {
			BlockPos hall = new BlockPos(-3, -60, -8);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			BlockPos bench = new BlockPos(-8, -60, -3);
			level.setBlockAndUpdate(bench, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
			Villager mira = EntityType.VILLAGER.spawn(level, new BlockPos(6, -60, 6), MobSpawnType.COMMAND);
			Jobs.employ(level, mira, bench, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
			mira.setVillagerData(mira.getVillagerData().setLevel(3));
			mira.setCustomName(Component.literal("Mira"));
			mira.kill();
			// B236: the grave's record is filed in vanilla's next task, so file it now or the street grave reads as none
			ScreenshotHarness.fileRecords(level, new BlockPos(6, -60, 6), 4);
			List<BlockPos> street = io.github.jcondedata.aliveworkplace.grave.Graves.near(level, new BlockPos(6, -60, 6), 4);
			Showcase.check(!street.isEmpty(), "Mira's grave lies in the street");
			picked(level, player, STATION, Blocks.BREWING_STAND, Items.GOLDEN_APPLE);
			chest(level, STATION.east(2));
			return l -> !street.isEmpty() && !l.getBlockState(street.get(0)).is(ModBlocks.GRAVE)
				&& io.github.jcondedata.aliveworkplace.grave.Graveyards.plot(l, hall).stream().anyMatch(p -> l.getBlockState(p).is(ModBlocks.GRAVE));
		}, null));
		// 36.16: Mira, the village's Farmer, dies; the village is told, Tom (her husband) and Pip (her son) mourn, and the
		// undertaker leads her funeral at the grave (no golden apple in the chest: nobody brings her back here)
		SCENES.put("village_mourns", new Job("the undertaker led Mira's funeral and the village mourned her", 2400,
			new Vec3(4.5, -54, 12), new Vec3(-2, -59.5, 0), (level, player) -> {
			BlockPos hall = new BlockPos(-3, -60, -8);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			Villager mira = EntityType.VILLAGER.spawn(level, new BlockPos(6, -60, 6), MobSpawnType.COMMAND);
			mira.setVillagerData(mira.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(3));
			mira.setCustomName(Component.literal("Mira"));
			io.github.jcondedata.aliveworkplace.people.LifeStages.setAdultSince(mira, today - 22L * io.github.jcondedata.aliveworkplace.people.Mourning.yearDays());
			Villager tom = EntityType.VILLAGER.spawn(level, new BlockPos(3, -60, 6), MobSpawnType.COMMAND);
			tom.setVillagerData(tom.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(2));
			tom.setCustomName(Component.literal("Tom"));
			Villager pip = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 7), MobSpawnType.COMMAND);
			pip.setCustomName(Component.literal("Pip"));
			ModAttachments.PARTNER.set(mira, new io.github.jcondedata.aliveworkplace.people.Couples.Partner(tom.getUUID(), tom.getDisplayName().copy(), today - 40, true));
			ModAttachments.PARTNER.set(tom, new io.github.jcondedata.aliveworkplace.people.Couples.Partner(mira.getUUID(), mira.getDisplayName().copy(), today - 40, true));
			ModAttachments.PARENTS.set(pip, new io.github.jcondedata.aliveworkplace.people.Families.Parents(Component.literal("Mira"), Component.literal("Tom"), "", "", true));
			Villager u = picked(level, player, STATION, Blocks.BREWING_STAND, Items.GOLDEN_APPLE);
			u.setCustomName(Component.literal("Ulric"));
			chest(level, STATION.east(2));
			// B268: the hall's record now, not in vanilla's next task: Mira dies this tick, and without it her death finds no
			// village (no one told, nobody grieving, no funeral waiting)
			ScreenshotHarness.fileRecords(level, hall, 1);
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
			mira.kill();
			// B236: the grave's record is filed in vanilla's next task, so file it now
			ScreenshotHarness.fileRecords(level, new BlockPos(6, -60, 6), 4);
			List<BlockPos> graves = io.github.jcondedata.aliveworkplace.grave.Graves.near(level, new BlockPos(6, -60, 6), 4);
			Showcase.check(!graves.isEmpty() && io.github.jcondedata.aliveworkplace.people.Mourning.funeralDue(level, graves.get(0)), "Mira's grave waits for her funeral");
			Showcase.check(io.github.jcondedata.aliveworkplace.people.Mourning.mourning(level, tom)
				&& io.github.jcondedata.aliveworkplace.people.Mourning.mourning(level, pip), "Tom and Pip mourn her");
			return l -> l.getBlockEntity(hall) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity e
				&& e.chronicle().stream().anyMatch(c -> c.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.FUNERAL);
		}, null));
		// 36.17: a block several jobs share gives one at random. Four composters, four jobless villagers who take them by
		// themselves: each becomes one of the composter's jobs (Farmer, Orchard Keeper, Florist, Composter), likelier the
		// fewer the village has, so not all farmers. Each is named by the job they took, for the picture.
		SCENES.put("random_job", new Job("four jobless villagers took four composters by themselves, and not all became farmers", 1800,
			new Vec3(0.5, -57.2, 6.5), new Vec3(0.5, -59.5, 0), (level, player) -> {
			level.setDayTime(2000);
			List<BlockPos> composters = List.of(STATION.offset(-3, 0, 0), STATION.offset(-1, 0, 0), STATION.offset(1, 0, 0), STATION.offset(3, 0, 0));
			List<Villager> villagers = new ArrayList<>();
			for (BlockPos at : composters) {
				level.setBlockAndUpdate(at, Blocks.COMPOSTER.defaultBlockState());
				villagers.add(EntityType.VILLAGER.spawn(level, at.south(2), MobSpawnType.COMMAND));
			}
			io.github.jcondedata.aliveworkplace.work.Stations.Station station = io.github.jcondedata.aliveworkplace.work.Stations.at(Blocks.COMPOSTER).orElseThrow();
			return l -> {
				for (Villager v : villagers) {
					net.minecraft.world.entity.npc.VillagerProfession job = v.getVillagerData().getProfession();
					if (job != net.minecraft.world.entity.npc.VillagerProfession.NONE && !v.hasCustomName()) {
						v.setCustomName(io.github.jcondedata.aliveworkplace.work.Stations.name(job));
					}
				}
				if (!villagers.stream().allMatch(v -> v.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.NONE)) {
					return false;
				}
				Showcase.check(villagers.stream().allMatch(v -> station.has(v.getVillagerData().getProfession())), "each took one of the composter's jobs");
				Showcase.check(villagers.stream().map(v -> v.getVillagerData().getProfession()).distinct().count() >= 2,
					"not every one became a farmer: " + villagers.stream().map(v -> v.getVillagerData().getProfession().name()).toList());
				return true;
			};
		}, null));
		SCENES.put("innkeeper", new Job("a traveller came to stay at the inn", 1200, CAMERA, TARGET, (level, player) -> {
			for (int x = 3; x <= 5; x += 2) {
				level.setBlockAndUpdate(new BlockPos(x, -60, -3), Blocks.RED_BED.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
					.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
				level.setBlockAndUpdate(new BlockPos(x, -60, -4), Blocks.RED_BED.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
					.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
			}
			picked(level, player, STATION, ModBlocks.SHOP_COUNTER, Items.RED_BED);
			chest(level, chestPos());
			return l -> !io.github.jcondedata.aliveworkplace.inn.Innkeepers.guests(l, STATION).isEmpty();
		}, new After("04_hire_screen", (level, player) -> {
			Villager guest = io.github.jcondedata.aliveworkplace.inn.Innkeepers.guests(level, STATION).get(0);
			guest.setNoAi(true);
			ScreenshotHarness.hover(player, guest.position().add(1.5, 1.2, 3), 160, 15);
			io.github.jcondedata.aliveworkplace.inn.Innkeepers.openHire(player, guest);
		}, 15, 6)));
		SCENES.put("teacher", job("the teacher schooled both children", 2400, (level, player) -> {
			Villager t = picked(level, player, STATION, Blocks.LECTERN, Items.BOOK);
			chest(level, chestPos());
			io.github.jcondedata.aliveworkplace.school.Schools.LESSONS_NEEDED = 200;
			for (BlockPos p : List.of(new BlockPos(3, -60, -3), new BlockPos(-3, -60, -2))) {
				Villager kid = EntityType.VILLAGER.spawn(level, p, MobSpawnType.COMMAND);
				kid.setAge(-24000);
			}
			return l -> n(ModAttachments.PUPILS_TAUGHT, t) >= 2;
		}));
		SCENES.put("rancher", job("the rancher tamed and saddled the horse", 2400, (level, player) -> {
			picked(level, player, STATION, Blocks.SMOKER, Items.SADDLE);
			chest(level, chestPos(), new ItemStack(Items.SADDLE));
			var horse = EntityType.HORSE.spawn(level, new BlockPos(3, -60, -3), MobSpawnType.COMMAND);
			return l -> horse.isTamed() && horse.isSaddled();
		}));
		SCENES.put("mason", new Job("the mason cut the stone brick stairs and walls the builder needed", 6000,
			new Vec3(5.5, -52, 10.5), new Vec3(-6, -55, -5), (level, player) -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
			java.util.UUID site = builderSite(level, StarterBlueprints.LOOKOUT_TOWER, new BlockPos(-8, -60, -3),
				List.of(Items.STONE_BRICK_STAIRS, Items.STONE_BRICK_WALL), Map.of(Items.STONE, 96));
			Villager m = worker(level, STATION, Blocks.STONECUTTER, PoiTypes.MASON, VillagerProfession.MASON);
			return l -> n(ModAttachments.ITEMS_CRAFTED, m) >= 1 && built(l, site, 0.6f);
		}, null));
		SCENES.put("dyer", new Job("the dyer made the red wool and carpets the builder needed", 6000,
			new Vec3(4.5, -54.5, 8.5), new Vec3(-5, -58, -4), (level, player) -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
			java.util.UUID site = builderSite(level, StarterBlueprints.MARKET_STALL, new BlockPos(-8, -60, -3),
				List.of(Items.RED_WOOL, Items.RED_CARPET, Items.WHITE_CARPET), Map.of(Items.WHITE_WOOL, 48, Items.POPPY, 32));
			Villager d = worker(level, STATION, Blocks.CAULDRON, PoiTypes.LEATHERWORKER, VillagerProfession.LEATHERWORKER);
			return l -> n(ModAttachments.ITEMS_CRAFTED, d) >= 1 && built(l, site, 0.6f);
		}, null));
		SCENES.put("crew", new Job("four builders built the stone house together, each placing a share of it", 6000,
			new Vec3(6.5, -51, 12.5), new Vec3(-5, -56, -6), (level, player) -> {
			// 23.1a: the builder and three helpers from benches nearby, at the normal build speed, from exactly the house's
			// material list: about a third of the time it takes alone, everyone placing a fair share.
			java.util.UUID site = builderSite(level, StarterBlueprints.STONE_HOUSE, new BlockPos(-8, -60, -3), List.of(), Map.of());
			List<java.util.UUID> crew = new ArrayList<>();
			BuildSite s = BuildSiteManager.get(level).get(site);
			if (s != null && s.builder() != null) {
				crew.add(s.builder());
			}
			for (int i = 0; i < 3; i++) {
				BlockPos bench = new BlockPos(-1 + 2 * i, -60, 3);
				level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
				Villager mate = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
				Builders.employ(level, mate, bench);
				crew.add(mate.getUUID());
			}
			int[] placed = new int[crew.size()];
			return l -> {
				BuildSite now = BuildSiteManager.get(l).get(site);
				if (now != null && !now.isDone()) {
					for (int i = 0; i < crew.size(); i++) {
						placed[i] = Math.max(placed[i], now.placedBy(crew.get(i), false));
					}
					return false;
				}
				int total = java.util.Arrays.stream(placed).sum();
				// Each placed at least half an even share (the last few blocks after the final look don't count).
				return java.util.Arrays.stream(placed).allMatch(n -> n * crew.size() * 2 >= total * 0.9);
			};
		}, null));
		SCENES.put("scaffolding", new Job("the builder put up scaffolding to finish the pillar and its cap, then took it all down into the chest", 9000,
			new Vec3(4.5, -54.5, 8.5), new Vec3(-4, -54, -2), (level, player) -> {
			// 35.6: the test pillar, 14 high with a 5 x 5 cap overhanging it by two: nowhere near its top to stand.
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(4, level.getServer());
			net.minecraft.resources.ResourceLocation pillar = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
			Blueprint blueprint = BlueprintLibrary.get(level, pillar).orElseThrow();
			BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), blueprint.size(), new BlockPos(-6, -60, -4),
				BlueprintItem.rotationFacing(Direction.SOUTH));
			BlockPos bench = new BlockPos(-1, -60, 2);
			level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
			Container box = chest(level, bench.east(), new ItemStack(Items.STONE_BRICKS, 14), new ItemStack(Items.POLISHED_ANDESITE, 25),
				new ItemStack(Items.SCAFFOLDING, 64));
			Villager builder = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
			Builders.employ(level, builder, bench);
			BuildSite site = Builders.start(level, builder, null, pillar, placement);
			// 35.6a: the camera stays close: about 7 blocks from the scaffolding, at the builder's height as it climbs, so
			// the scaffolding and the builder on it fill the shot (from 19 blocks away none of it showed).
			net.minecraft.world.level.levelgen.structure.BoundingBox bounds = site == null ? null : site.plan(level).bounds();
			Vec3 middle = bounds == null ? new Vec3(-4, -60, -2)
				: new Vec3((bounds.minX() + bounds.maxX()) / 2.0 + 0.5, bounds.minY(), (bounds.minZ() + bounds.maxZ()) / 2.0 + 0.5);
			ScreenshotHarness.hoverLookingAt(player, middle.add(5, 6, 8), middle.add(0, 5, 0));
			int[] most = {0};
			double[] cameraY = {Double.NaN};
			boolean[] shot = {false, false};
			return l -> {
				if (site == null) {
					return false;
				}
				int up = site.scaffolds().size();
				if (up > 0 && l.getEntity(builder.getUUID()) instanceof Villager v) {
					BlockPos column = site.scaffolds().get(0);
					Vec3 away = new Vec3(column.getX() + 0.5 - middle.x, 0, column.getZ() + 0.5 - middle.z);
					away = away.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : away.normalize();
					Vec3 side = new Vec3(-away.z, 0, away.x);
					double y = Math.max(middle.y + 2, v.getY() + 1);
					if (Double.isNaN(cameraY[0]) || Math.abs(y - cameraY[0]) > 1.5) {
						cameraY[0] = y;
						Vec3 at = new Vec3(column.getX() + 0.5, y, column.getZ() + 0.5);
						ScreenshotHarness.hoverLookingAt(player, at.add(away.scale(6)).add(side.scale(3)).add(0, 1, 0), new Vec3(at.x, y - 0.5, at.z));
					}
					boolean onIt = l.getBlockState(v.blockPosition().below()).is(Blocks.SCAFFOLDING) || l.getBlockState(v.blockPosition()).is(Blocks.SCAFFOLDING);
					if (!shot[0] && onIt && v.getY() >= middle.y + 7) {
						shot[0] = true; // up its scaffolding, high on the pillar
						SHOTS.add("02_on_scaffolding");
					}
					if (shot[0] && !shot[1] && up < most[0] - 1) {
						shot[1] = true;
						SHOTS.add("02_taking_down");
					}
				}
				most[0] = Math.max(most[0], up);
				int left = 0;
				for (int i = 0; i < box.getContainerSize(); i++) {
					left += box.getItem(i).is(Items.SCAFFOLDING) ? box.getItem(i).getCount() : 0;
				}
				boolean done = BuildSiteManager.get(l).get(site.id()) == null && most[0] > 0 && site.scaffolds().isEmpty() && left == 64;
				if (done) {
					Showcase.check(shot[0], "the builder stood on its scaffolding high up the pillar");
					Showcase.check(most[0] >= 6, "a column of at least 6 scaffolds stood beside the pillar (most at once: " + most[0] + ")");
				}
				return done;
			};
		}, null));
		SCENES.put("nurse", job("the nurse healed the hurt villager and cured the ill one", 2000, (level, player) -> {
			Villager nurse = picked(level, player, STATION, Blocks.BREWING_STAND, Items.HONEY_BOTTLE);
			chest(level, chestPos(), new ItemStack(Items.HONEY_BOTTLE));
			Villager hurt = EntityType.VILLAGER.spawn(level, new BlockPos(2, -60, -3), MobSpawnType.COMMAND);
			hurt.setNoAi(true);
			hurt.setHealth(4f);
			Villager ill = EntityType.VILLAGER.spawn(level, new BlockPos(-2, -60, -3), MobSpawnType.COMMAND);
			ill.setNoAi(true);
			io.github.jcondedata.aliveworkplace.people.Sickness.fallIll(level, ill);
			return l -> hurt.getHealth() >= hurt.getMaxHealth() - 0.5f && !io.github.jcondedata.aliveworkplace.people.Sickness.isIll(ill)
				&& n(ModAttachments.VILLAGERS_CURED, nurse) >= 1;
		}));
		SCENES.put("smelter", job("the armorer smelted the raw iron into ingots", 2400, (level, player) -> {
			Villager a = worker(level, STATION, Blocks.BLAST_FURNACE, PoiTypes.ARMORER, VillagerProfession.ARMORER);
			Container c = chest(level, chestPos(), new ItemStack(Items.RAW_IRON, 4), new ItemStack(Items.COAL, 2));
			return l -> n(ModAttachments.INGOTS_SMELTED, a) >= 4 && c.countItem(Items.IRON_INGOT) >= 4;
		}));
		SCENES.put("toolsmith", new Job("the toolsmith made the axe the lumberjack asked for", 3000,
			new Vec3(6.5, -53, 13.5), new Vec3(6.5, -59.5, 0.5), (level, player) -> {
			Villager t = worker(level, STATION, Blocks.SMITHING_TABLE, PoiTypes.TOOLSMITH, VillagerProfession.TOOLSMITH);
			chest(level, chestPos(), new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.OAK_LOG, 1));
			BlockPos block = new BlockPos(14, -60, 0);
			Villager lumberjack = picked(level, player, block, Blocks.FLETCHING_TABLE, Items.IRON_AXE);
			Container theirs = chest(level, block.east(2));
			return l -> n(ModAttachments.ITEMS_CRAFTED, t) >= 1
				&& (theirs.countItem(Items.IRON_AXE) >= 1 || lumberjack.getMainHandItem().is(Items.IRON_AXE));
		}, null));
		SCENES.put("miner_pickaxe", job("the miner's stone pickaxe wore out on the fourth block; she made a new one at the blast furnace from the cobblestone she had dug and dug on to the end",
			2400, (level, player) -> {
			level.setBlockAndUpdate(STATION, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
			Container c = chest(level, chestPos(), new ItemStack(Items.STICK, 2), new ItemStack(Items.TORCH, 4));
			BlockPos min = new BlockPos(-1, -60, -5);
			BlockPos max = new BlockPos(1, -59, -3);
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
			}
			Villager m = EntityType.VILLAGER.spawn(level, STATION.south(), MobSpawnType.COMMAND);
			m.setCustomName(Component.literal("Dara"));
			io.github.jcondedata.aliveworkplace.mine.Miners.employ(level, m, STATION);
			ItemStack worn = new ItemStack(Items.STONE_PICKAXE);
			worn.setDamageValue(worn.getMaxDamage() - 4);
			m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, worn);
			io.github.jcondedata.aliveworkplace.mine.QuarrySite site = io.github.jcondedata.aliveworkplace.mine.Miners.start(level, m, player,
				net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(min, max), 2);
			site.setStairs(false);
			boolean[] making = {false};
			return l -> {
				var now = io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager.get(l).get(site.id());
				if (!making[0] && now != null && now.status() == io.github.jcondedata.aliveworkplace.mine.QuarrySite.Status.CRAFTING_PICKAXE) {
					making[0] = true;
					SHOTS.add("04_making_pickaxe");
				}
				return making[0] && now == null && c.countItem(Items.STONE_PICKAXE) >= 1 && c.countItem(Items.STICK) == 0;
			};
		}));
		// The Quarry build (36.12): the pit's top four layers dug already, the Miner at the shed's blast furnace takes the
		// pit by themselves and digs on down, and what they dug grows back from the bottom up (fast, for the film).
		SCENES.put("quarry_build", new Job("the Miner at the Quarry's blast furnace took its pit without a marker and dug, and the mined rock grew back from the bottom up",
			4800, new Vec3(0.5, -41, 5.5), new Vec3(1.5, -53, -10), (level, player) -> {
			BlockPos origin = new BlockPos(-4, -49, -16);
			io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(
				level.dimension().location(), origin, net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
			net.minecraft.world.level.levelgen.structure.BoundingBox pit = io.github.jcondedata.aliveworkplace.mine.QuarryBuilds.pit(placement);
			net.minecraft.util.RandomSource rock = net.minecraft.util.RandomSource.create(3612);
			// A hill of rock to dig the pit in (the superflat ground is too thin), grass on top round the pit
			for (BlockPos p : BlockPos.betweenClosed(origin.getX() - 2, -60, origin.getZ() - 1, origin.getX() + 17, origin.getY() - 1, origin.getZ() + 12)) {
				boolean inPit = pit.isInside(p);
				BlockState state;
				if (inPit) {
					state = p.getY() > pit.maxY() - 4 ? Blocks.AIR.defaultBlockState()
						: io.github.jcondedata.aliveworkplace.mine.QuarryBuilds.pick(rock, p.getY() < pit.minY() + 3);
				} else {
					state = (p.getY() == origin.getY() - 1 ? Blocks.GRASS_BLOCK : p.getY() > origin.getY() - 4 ? Blocks.DIRT : Blocks.STONE).defaultBlockState();
				}
				level.setBlock(p, state, 2);
			}
			level.getStructureManager().get(io.github.jcondedata.aliveworkplace.mine.QuarryBuilds.BLUEPRINT).orElseThrow().placeInWorld(level,
				origin.below(5), origin.below(5), new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), level.getRandom(), 2);
			io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(io.github.jcondedata.aliveworkplace.mine.QuarryBuilds.BLUEPRINT,
				placement, player.getUUID());
			io.github.jcondedata.aliveworkplace.mine.QuarryBuilds.PER_DAY = 1200; // a block every second, for the film
			BlockPos furnace = io.github.jcondedata.aliveworkplace.mine.QuarryBuilds.furnace(placement);
			Villager m = EntityType.VILLAGER.spawn(level, furnace.west(), MobSpawnType.COMMAND);
			m.setCustomName(Component.literal("Dara"));
			io.github.jcondedata.aliveworkplace.mine.Miners.employ(level, m, furnace);
			m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
			return l -> {
				var site = io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager.get(l).all().stream().filter(s -> origin.equals(s.refillBuild())).findFirst();
				return site.isPresent() && site.get().mined() >= 12 && site.get().grownTotal() >= 8;
			};
		}, null));
		// Village-wide reach (36.20): a quarry marked 110 blocks east of the Miner's blast furnace, far outside Stonebrook (a hall
		// radius of 64): out of the old 64 reach, inside the new 128. The miner takes it and walks out to it, and digs.
		SCENES.put("far_site", new Job("the miner took a quarry marked 110 blocks out, far outside the village, walked out to it and dug it",
			3600, new Vec3(116.5, -54, 6.5), new Vec3(70, -60, 0), (level, player) -> {
			BlockPos hall = STATION.offset(-4, 0, 3);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState()
				.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
			var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setCustomName(Component.literal("Stonebrook"));
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			if (level.getPoiManager().getType(hall).isEmpty()) { // filed now, as in waystoneVillage (B534)
				PoiTypes.forState(level.getBlockState(hall)).ifPresent(type -> level.getPoiManager().add(hall.immutable(), type));
			}
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
			level.setBlockAndUpdate(STATION, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
			chest(level, chestPos(), new ItemStack(Items.TORCH, 4));
			BlockPos min = new BlockPos(109, -60, -1);
			BlockPos max = new BlockPos(111, -59, 1);
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
			}
			Villager m = EntityType.VILLAGER.spawn(level, STATION.south(), MobSpawnType.COMMAND);
			m.setCustomName(Component.literal("Dara"));
			io.github.jcondedata.aliveworkplace.mine.Miners.employ(level, m, STATION);
			m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
			// handed over as the player does: the Quarry Marker with both corners, right-clicked on the miner
			ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
			marker.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.QUARRY, new io.github.jcondedata.aliveworkplace.mine.QuarryData(
				java.util.Optional.of(level.dimension().location()), java.util.Optional.of(min.above()), java.util.Optional.of(max), 2));
			io.github.jcondedata.aliveworkplace.mine.Miners.assign(player, m, marker);
			var site = io.github.jcondedata.aliveworkplace.mine.Miners.activeSite(level, m);
			Showcase.check(site != null, "the miner took the quarry 110 blocks out, outside the village");
			Showcase.check(io.github.jcondedata.aliveworkplace.work.Reach.village(level, STATION).isPresent()
				&& !io.github.jcondedata.aliveworkplace.work.Reach.inSameVillage(level, STATION, min), "the blast furnace is in Stonebrook and the quarry outside it");
			boolean[] there = {false};
			return l -> {
				if (!there[0] && m.blockPosition().getX() >= 100) {
					there[0] = true;
					SHOTS.add("04_far_site_arrived");
				}
				var now = site == null ? null : io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager.get(l).get(site.id());
				return there[0] && site != null && (now == null || now.mined() >= 9);
			};
		}, null));
		SCENES.put("weaponsmith", job("the weaponsmith mended the worn pickaxe", 1800, (level, player) -> {
			Villager w = worker(level, STATION, Blocks.GRINDSTONE.defaultBlockState()
				.setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), PoiTypes.WEAPONSMITH, VillagerProfession.WEAPONSMITH);
			ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
			pick.setDamageValue(200);
			chest(level, chestPos(), pick, new ItemStack(Items.IRON_INGOT, 3));
			return l -> n(ModAttachments.ITEMS_MENDED, w) >= 1;
		}));
		SCENES.put("fletcher", new Job("the fletcher made a bow for the guard", 2400,
			new Vec3(6.5, -53, 13.5), new Vec3(6, -59.5, 0.5), (level, player) -> {
			Villager f = worker(level, STATION, Blocks.FLETCHING_TABLE, PoiTypes.FLETCHER, VillagerProfession.FLETCHER);
			chest(level, chestPos(), new ItemStack(Items.STICK, 3), new ItemStack(Items.STRING, 3));
			BlockPos post = new BlockPos(12, -60, 0);
			Villager g = guard(level, post, new ItemStack(Items.IRON_SWORD));
			Container postChest = (Container) level.getBlockEntity(post.east(2));
			return l -> n(ModAttachments.ITEMS_CRAFTED, f) >= 1
				&& (g.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.BOW) || postChest.countItem(Items.BOW) >= 1);
		}, null));
		SCENES.put("shepherd", job("the shepherd sheared both sheep", 2000, (level, player) -> {
			Villager s = worker(level, STATION, Blocks.LOOM, PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
			chest(level, chestPos(), new ItemStack(Items.SHEARS));
			List<net.minecraft.world.entity.animal.Sheep> sheep = new ArrayList<>();
			for (BlockPos p : List.of(new BlockPos(3, -60, -3), new BlockPos(-3, -60, -4))) {
				var one = EntityType.SHEEP.spawn(level, p, MobSpawnType.COMMAND);
				one.setNoAi(true);
				one.setColor(sheep.isEmpty() ? net.minecraft.world.item.DyeColor.WHITE : net.minecraft.world.item.DyeColor.BROWN);
				sheep.add(one);
			}
			Container shears = (Container) level.getBlockEntity(chestPos());
			return l -> {
				// B311: on a timeout, say what the shepherd was doing
				var status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(s, l.getGameTime());
				timeoutWhy = "the shepherd: " + s.getVillagerData().getProfession() + (s.isAlive() ? "" : " (dead)") + ", sheared " + n(ModAttachments.ANIMALS_SHEARED, s)
					+ ", sheep sheared " + sheep.stream().map(one -> String.valueOf(one.isSheared())).toList()
					+ ", holding " + s.getMainHandItem().getItem() + ", shears in the chest " + shears.countItem(Items.SHEARS)
					+ ", job site " + s.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).map(g -> g.pos().toShortString()).orElse("none")
					+ ", doing " + s.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("nothing")
					+ ", busy " + io.github.jcondedata.aliveworkplace.ranch.RanchWork.isBusy(s)
					+ ", status " + (status == null ? "none" : status.title().getString() + " / " + status.line().getString())
					+ ", at " + s.blockPosition().toShortString();
				return sheep.stream().allMatch(net.minecraft.world.entity.animal.Sheep::isSheared) && n(ModAttachments.ANIMALS_SHEARED, s) >= 2;
			};
		}));
		SCENES.put("herder", job("the herder milked the cow", 2000, (level, player) -> {
			Villager h = worker(level, STATION, Blocks.SMOKER, PoiTypes.BUTCHER, VillagerProfession.BUTCHER);
			Container c = chest(level, chestPos(), new ItemStack(Items.BUCKET, 2));
			var cow = EntityType.COW.spawn(level, new BlockPos(3, -60, -3), MobSpawnType.COMMAND);
			cow.setNoAi(true);
			return l -> n(ModAttachments.MILK_COLLECTED, h) >= 1 && c.countItem(Items.MILK_BUCKET) >= 1;
		}));
		SCENES.put("alchemist", job("the alchemist brewed three healing potions", 3600, (level, player) -> {
			Villager cl = worker(level, STATION, Blocks.BREWING_STAND, PoiTypes.CLERIC, VillagerProfession.CLERIC);
			level.setBlockAndUpdate(STATION.east(3), Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			chest(level, chestPos(), new ItemStack(Items.GLASS_BOTTLE, 3), new ItemStack(Items.NETHER_WART), new ItemStack(Items.GLISTERING_MELON_SLICE),
				new ItemStack(Items.BLAZE_POWDER));
			return l -> n(ModAttachments.POTIONS_BREWED, cl) >= 3;
		}));
		SCENES.put("scribe", new Job("the librarian enchanted the guard's sword", 2400,
			new Vec3(5, -55.5, 8), new Vec3(3.5, -59.3, -1.5), (level, player) -> {
			Villager lib = worker(level, STATION, Blocks.LECTERN, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			level.setBlockAndUpdate(new BlockPos(2, -60, -2), Blocks.ENCHANTING_TABLE.defaultBlockState());
			chest(level, chestPos(), new ItemStack(Items.LAPIS_LAZULI, 3));
			Villager g = guard(level, new BlockPos(7, -60, -3), new ItemStack(Items.IRON_SWORD));
			g.setNoAi(true);
			return l -> g.getItemBySlot(EquipmentSlot.MAINHAND).isEnchanted() && n(ModAttachments.ITEMS_ENCHANTED, lib) >= 1;
		}, null));
		SCENES.put("explorer", new Job("the explorer came back from an expedition with finds", 4000,
			new Vec3(6.5, -54, 9), new Vec3(0, -59.5, -0.5), (level, player) -> {
			Villager v = worker(level, STATION, Blocks.CARTOGRAPHY_TABLE, PoiTypes.CARTOGRAPHER, VillagerProfession.CARTOGRAPHER);
			chest(level, chestPos(), new ItemStack(Items.BREAD, 4));
			io.github.jcondedata.aliveworkplace.explore.ExplorerWork.RANGE = 8;
			io.github.jcondedata.aliveworkplace.explore.ExplorerWork.MIN_HOP = 3;
			io.github.jcondedata.aliveworkplace.explore.ExplorerWork.MAX_HOP = 6;
			io.github.jcondedata.aliveworkplace.explore.ExplorerWork.SEARCH_TICKS = 30;
			io.github.jcondedata.aliveworkplace.explore.ExplorerWork.REST_TICKS = 100;
			return l -> n(ModAttachments.EXPEDITIONS, v) >= 1;
		}, null));
		SCENES.put("legend", job("a villager became a Legend, a Master who speeds up the builder beside them", 1200, (level, player) -> {
			// ROADMAP 29.2: no Legend ships yet, so the scene loads one of its own (the plain Legend outfit, a pace power)
			// and makes the villager beside the builder that Legend five seconds in.
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_legend");
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.read(id,
				com.google.gson.JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\", \"title\": \"entity.minecraft.villager.legend\","
					+ " \"lore\": \"entity.minecraft.villager.legend\", \"powers\": [{\"type\": \"pace\", \"trades\": [\"aliveworkplace:builder\"],"
					+ " \"radius\": 16, \"factor\": 2.0}, {\"type\": \"mood\", \"points\": 5, \"radius\": 16}]}").getAsJsonObject());
			io.github.jcondedata.aliveworkplace.legend.Legends.setForTest(Map.of(id, legend));
			Villager builder = worker(level, STATION, io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH,
				io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDERS_BENCH_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER);
			Villager hero = EntityType.VILLAGER.spawn(level, STATION.south().east(2), MobSpawnType.COMMAND);
			hero.setNoAi(true);
			hero.setYRot(180);
			hero.setYHeadRot(180);
			long start = level.getGameTime();
			return l -> {
				if (!ModAttachments.LEGEND.has(hero) && l.getGameTime() >= start + 100) {
					io.github.jcondedata.aliveworkplace.legend.Legends.make(l, hero, legend, "showcase");
				}
				return ModAttachments.LEGEND.has(hero) && hero.getVillagerData().getLevel() == 5
					&& io.github.jcondedata.aliveworkplace.legend.LegendPowers.pace(builder) == 2f;
			};
		}));
		SCENES.put("legend_strike", new Job("a Legend on strike left her stonecutter and picketed by the Village Hall under a red line", 1600,
			new Vec3(3.5, -57.2, 6.5), new Vec3(-3, -59.4, -1.5), (level, player) -> {
			// ROADMAP 29.5: a Mason Legend whose bed is in no home of her own, two days unmet and on strike. By day her
			// WORK activity is a picket: she walks from her stonecutter to the hall, and the red line over her head says
			// what she wants. The scene loads a Legend of its own (the stand-in outfit).
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(3000); // mid-morning: work time
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_strike");
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.read(id,
				com.google.gson.JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \"entity.minecraft.villager.legend\","
					+ " \"lore\": \"entity.minecraft.villager.legend\", \"needs\": {\"luxury\": \"jewels\"},"
					+ " \"powers\": [{\"type\": \"pace\", \"trades\": [\"minecraft:mason\"], \"radius\": 16, \"factor\": 2.0}]}").getAsJsonObject());
			io.github.jcondedata.aliveworkplace.legend.Legends.setForTest(Map.of(id, legend));
			BlockPos hall = STATION.offset(-6, 0, -3);
			place(level, hall, ModBlocks.VILLAGE_HALL);
			Villager mason = worker(level, STATION, Blocks.STONECUTTER, PoiTypes.MASON, VillagerProfession.MASON);
			io.github.jcondedata.aliveworkplace.legend.Legends.make(level, mason, legend, "showcase");
			long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			io.github.jcondedata.aliveworkplace.legend.LegendData data = ModAttachments.LEGEND.get(mason);
			ModAttachments.LEGEND.set(mason, new io.github.jcondedata.aliveworkplace.legend.LegendData(data.id(), data.name(), false,
				java.util.Optional.of(hall), today - 6, -1, Map.of("home", 3), today, today, data.way(), today));
			return l -> mason.blockPosition().closerThan(hall, 7)
				&& io.github.jcondedata.aliveworkplace.legend.LegendNeeds.striking(mason)
				&& io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(mason, l.getGameTime()) != null;
		}, null));
		SCENES.put("strange_mood", job("a Master cleric taken by a strange mood claimed her brewing stand, the chest was filled, and she made a Masterwork and became a Legend", 1800,
			(level, player) -> {
			// ROADMAP 29.10: a Master cleric in a village with a hall is seized by a strange mood (the Legend's file is the
			// scene's own). She claims her brewing stand under a purple line; a little later the three materials she asked
			// for go into the chest beside it one by one, she makes the Masterwork, becomes the Legend, and the Masterwork
			// is hung in an item frame by her stand.
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(3000); // mid-morning: work time
			net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_mood");
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.read(id,
				com.google.gson.JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:cleric\", \"title\": \"entity.minecraft.villager.legend\","
					+ " \"lore\": \"entity.minecraft.villager.legend\", \"arrive\": [{\"way\": \"inspired\", \"trades\": [\"minecraft:cleric\"]}],"
					+ " \"masterwork\": {\"item\": \"minecraft:totem_of_undying\", \"materials\": [\"minecraft:diamond\", \"minecraft:blaze_rod\","
					+ " \"minecraft:echo_shard\", \"minecraft:amethyst_shard\"]}}").getAsJsonObject());
			io.github.jcondedata.aliveworkplace.legend.Legends.setForTest(Map.of(id, legend));
			io.github.jcondedata.aliveworkplace.legend.StrangeMoods.ENABLED = true;
			BlockPos hall = STATION.offset(-6, 0, -3);
			place(level, hall, ModBlocks.VILLAGE_HALL);
			Villager cleric = worker(level, STATION, Blocks.BREWING_STAND, PoiTypes.CLERIC, VillagerProfession.CLERIC);
			cleric.setVillagerData(cleric.getVillagerData().setLevel(5));
			Container c = chest(level, chestPos());
			BlockPos wall = STATION.offset(2, 0, -1);
			place(level, wall, Blocks.POLISHED_ANDESITE);
			long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			io.github.jcondedata.aliveworkplace.legend.StrangeMood mood = io.github.jcondedata.aliveworkplace.legend.StrangeMoods.start(level, hall, cleric,
				legend, today, net.minecraft.util.RandomSource.create(29L));
			long began = level.getGameTime();
			boolean[] hung = {false};
			java.util.function.Function<Container, ItemStack> masterworkIn = box -> {
				for (int i = 0; i < box.getContainerSize(); i++) {
					if (io.github.jcondedata.aliveworkplace.legend.StrangeMoods.isMasterwork(box.getItem(i))) {
						return box.getItem(i);
					}
				}
				return ItemStack.EMPTY;
			};
			return l -> {
				long t = l.getGameTime() - began;
				for (int i = 0; i < mood.materials().size(); i++) {
					net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(mood.materials().get(i));
					if (t >= 300 + 60L * i && c.countItem(item) == 0 && io.github.jcondedata.aliveworkplace.legend.StrangeMoods.claiming(cleric)) {
						c.setItem(i, new ItemStack(item)); // the chest filled, one material at a time
					}
				}
				if (!hung[0] && ModAttachments.LEGEND.has(cleric)) {
					ItemStack work = c.countItem(Items.TOTEM_OF_UNDYING) > 0 ? masterworkIn.apply(c) : masterworkIn.apply(player.getInventory());
					if (!work.isEmpty()) {
						net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(l, wall.south(), Direction.SOUTH);
						frame.setItem(work.copy(), false);
						l.addFreshEntity(frame);
						hung[0] = true;
						Showcase.check(io.github.jcondedata.aliveworkplace.legend.StrangeMoods.isMasterwork(work)
							&& work.getHoverName().getString().startsWith("The "), "the Masterwork has its name: " + work.getHoverName().getString());
					}
				}
				return hung[0] && t >= 600;
			};
		}));
		SCENES.put("legend_architect", new Job("the Master Architect handed a builder the Stone House redrawn in the Grand style, and it was rebuilt", 7000,
			new Vec3(5.5, -52, 10.5), new Vec3(-5, -56, -6), (level, player) -> {
			// ROADMAP 29.12: a finished Stone House the village's builder put up, a builder at his bench with the Grand
			// style's materials in barrels, and the Master Architect settled beside them. A building's next tier comes
			// first, so the scene stages the top tier, stone_house_4 (since 26.15), which has none: his round hands the
			// builder the house redrawn in the Grand style, and only the blocks that differ are taken down and placed.
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
			StarterBlueprints.Entry entry = StarterBlueprints.STONE_HOUSE_4;
			BlockPos anchor = new BlockPos(-8, -60, -3);
			Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
			BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), blueprint.size(), anchor,
				BlueprintItem.rotationFacing(Direction.SOUTH));
			BlockPos file = placement.origin().below(entry.basement()); // its wine cellar is at the bottom of its file (26.15)
			level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, file, file,
				new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(placement.rotation())
					.setMirror(placement.mirror()), level.getRandom(), 2);
			BuildSiteManager.get(level).recordFinished(entry.id(), placement, player.getUUID());
			place(level, STATION.offset(6, 0, 2), ModBlocks.VILLAGE_HALL);
			net.minecraft.resources.ResourceLocation grandId = io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.styled(entry.id(), "grand");
			Map<Item, Integer> stock = new LinkedHashMap<>(BuildPlan.create(BlueprintLibrary.get(level, grandId).orElseThrow(), placement).materials());
			List<ItemStack> stacks = new ArrayList<>();
			stock.forEach((item, total) -> {
				for (int left = total; left > 0; left -= item.getDefaultMaxStackSize()) {
					stacks.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
				}
			});
			BlockPos bench = STATION.offset(2, 0, 3);
			level.setBlockAndUpdate(bench, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
			for (int b = 0; b * 27 < stacks.size(); b++) {
				BlockPos barrelPos = bench.offset(1 + b, 0, 0);
				level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
				Container barrel = (Container) level.getBlockEntity(barrelPos);
				for (int slot = 0; slot < 27 && b * 27 + slot < stacks.size(); slot++) {
					barrel.setItem(slot, stacks.get(b * 27 + slot));
				}
			}
			// the hall's and table's records now, not in vanilla's next task: the builder keeps his job, the Architect has a village (B88)
			ScreenshotHarness.fileRecords(level, STATION, 10);
			Villager builder = EntityType.VILLAGER.spawn(level, bench.south(2), MobSpawnType.COMMAND);
			Builders.employ(level, builder, bench);
			Villager architect = EntityType.VILLAGER.spawn(level, bench.offset(-2, 0, 2), MobSpawnType.COMMAND);
			architect.setNoAi(true);
			architect.setYRot(200);
			architect.setYHeadRot(200);
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(
				io.github.jcondedata.aliveworkplace.AliveWorkplace.id("master_architect")).orElse(null);
			Showcase.check(legend != null, "the Master Architect's file loaded");
			if (legend == null) {
				return l -> true;
			}
			io.github.jcondedata.aliveworkplace.legend.Legends.make(level, architect, legend, "showcase");
			BuildSite site = io.github.jcondedata.aliveworkplace.legend.GrandRebuild.round(level, architect, ModAttachments.LEGEND.get(architect),
				legend.powers(io.github.jcondedata.aliveworkplace.legend.GrandRebuildPower.class).get(0));
			Showcase.check(site != null && site.structure().equals(grandId), "the Architect handed over the Stone House in the Grand style");
			java.util.UUID id = site == null ? null : site.id();
			return l -> built(l, id, 1f);
		}, null));
		SCENES.put("basement", new Job("a builder dug a root cellar under a finished Starter Cottage III and built the Level 4 over it, then the camera went down the ladder into it", 16000,
			new Vec3(9.5, -50, 9.5), new Vec3(0, -56, -7), (level, player) -> {
			// ROADMAP 26.15: ground raised four blocks over the superflat (its bedrock is too close under the grass for a
			// cellar), a finished Starter Cottage III on it facing the camera, and a builder at his bench with what
			// Cottage IV still needs in barrels. He digs the root cellar out of the earth, walls it, puts in the ladder,
			// the bins and the lights; then the camera climbs down into it.
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(3000);
			for (int x = -18; x <= 18; x++) {
				for (int z = -22; z <= 10; z++) {
					for (int y = -60; y <= -58; y++) {
						level.setBlock(new BlockPos(x, y, z), (y == -58 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 2);
					}
				}
			}
			StarterBlueprints.Entry three = StarterBlueprints.STARTER_COTTAGE_3;
			StarterBlueprints.Entry four = StarterBlueprints.STARTER_COTTAGE_4;
			BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), three.size(), new BlockPos(0, -57, -2),
				BlueprintItem.rotationFacing(Direction.SOUTH));
			level.getStructureManager().get(three.id()).orElseThrow().placeInWorld(level, placement.origin(), placement.origin(),
				new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(placement.rotation())
					.setMirror(placement.mirror()), level.getRandom(), 2);
			BuildSiteManager.get(level).recordFinished(three.id(), placement, player.getUUID());
			BlockPos bench = new BlockPos(8, -57, 2);
			level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
			// all Cottage IV takes (what the cottage already has is left over in the barrels)
			Map<Item, Integer> stock = new LinkedHashMap<>(BuildPlan.create(BlueprintLibrary.get(level, four.id()).orElseThrow(), placement).materials());
			List<ItemStack> stacks = new ArrayList<>();
			stock.forEach((item, total) -> {
				for (int left = total; left > 0; left -= item.getDefaultMaxStackSize()) {
					stacks.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
				}
			});
			for (int b = 0; b * 27 < stacks.size(); b++) {
				BlockPos barrelPos = bench.offset(1 + b % 3, 0, b / 3);
				level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
				Container barrel = (Container) level.getBlockEntity(barrelPos);
				for (int slot = 0; slot < 27 && b * 27 + slot < stacks.size(); slot++) {
					barrel.setItem(slot, stacks.get(b * 27 + slot));
				}
			}
			Villager builder = EntityType.VILLAGER.spawn(level, bench.south(2), MobSpawnType.COMMAND);
			Builders.employ(level, builder, bench);
			BuildSite site = Builders.start(level, builder, null, four.id(), placement);
			Showcase.check(site != null && !stacks.isEmpty(), "the builder took on Starter Cottage IV, its materials in the barrels");
			java.util.UUID id = site == null ? null : site.id();
			java.util.function.Function<Vec3, Vec3> world = rel -> net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
				rel, placement.mirror(), placement.rotation(), BlockPos.ZERO).add(Vec3.atLowerCornerOf(placement.origin()));
			long[] since = {-1};
			int[] step = {0};
			return l -> {
				if (since[0] < 0) {
					if (!built(l, id, 1f)) {
						return false;
					}
					since[0] = l.getGameTime();
					BlockPos floor = BlockPos.containing(world.apply(new Vec3(5.5, -4, 5.5)));
					BlockPos room = BlockPos.containing(world.apply(new Vec3(4.5, -2, 4.5)));
					BlockPos ladder = BlockPos.containing(world.apply(new Vec3(8.5, -2, 3.5)));
					Showcase.check(l.getBlockState(floor).is(Blocks.PACKED_MUD) && l.getBlockState(room).isAir() && l.getBlockState(ladder).is(Blocks.LADDER),
						"the root cellar was dug out under the cottage, floored and laddered");
					aim(l, player, world.apply(new Vec3(3.5, -3 + 0.1, 4.5)), world.apply(new Vec3(6.5, -2.5, 7.5)));
					return false;
				}
				long t = l.getGameTime() - since[0];
				// each step once, when its time has come (the check isn't run every tick)
				if (t >= 40 && step[0] == 0) {
					step[0]++;
					SHOTS.add("02_root_cellar");
				} else if (t >= 70 && step[0] == 1) {
					step[0]++;
					aim(l, player, world.apply(new Vec3(7.5, -3 + 0.1, 6.5)), world.apply(new Vec3(8.5, -1, 3.5)));
				} else if (t >= 110 && step[0] == 2) {
					step[0]++;
					SHOTS.add("02_ladder_up");
				} else if (t >= 140 && step[0] == 3) {
					step[0]++;
					aim(l, player, new Vec3(9.5, -50, 9.5), new Vec3(0, -56, -7));
				}
				return step[0] == 4 && t >= 180;
			};
		}, null));
		SCENES.put("cellars", new Job("the Inn III stood cut away to its ale and wine cellar, and a builder dug the cellar under an old Smithy built before it had one", 16000,
			new Vec3(0.5, -48, 14.5), new Vec3(0, -58, -6), (level, player) -> {
			// ROADMAP 36.7: ground raised four blocks over the superflat (as for "basement"). On the left the Inn III,
			// finished and cut away down the middle, ground and all, so its cellar shows in section; on the right a Smithy
			// as it stood before 26.15 (its coal store still earth), its builder at his bench. He offers "Dig the cellar"
			// (a blueprint of the Smithy lined up over it); given back to him, he digs and fits out only the cellar.
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(3000);
			for (int x = -24; x <= 24; x++) {
				for (int z = -24; z <= 12; z++) {
					for (int y = -60; y <= -58; y++) {
						level.setBlock(new BlockPos(x, y, z), (y == -58 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 2);
					}
				}
			}
			// The Inn III, whole, then its front half taken away down to its cellar's floor
			StarterBlueprints.Entry inn = StarterBlueprints.INN_3;
			BlueprintData.Placement innAt = BlueprintItem.placementAt(level.dimension().location(), inn.size(), new BlockPos(-11, -57, -4),
				BlueprintItem.rotationFacing(Direction.SOUTH));
			BlockPos innFile = innAt.origin().below(inn.basement());
			level.getStructureManager().get(inn.id()).orElseThrow().placeInWorld(level, innFile, innFile,
				new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(innAt.rotation())
					.setMirror(innAt.mirror()), level.getRandom(), 2);
			for (int x = 0; x < inn.size().getX(); x++) {
				for (int z = -6; z < 6; z++) {
					for (int y = -inn.basement(); y < inn.size().getY(); y++) {
						BlockPos p = innAt.origin().offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
							new BlockPos(x, y, z), innAt.mirror(), innAt.rotation(), BlockPos.ZERO));
						level.removeBlockEntity(p);
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				}
			}
			// The old Smithy: as drawn above the ground, earth where its coal store goes, a floor block over the hatch
			StarterBlueprints.Entry smithy = StarterBlueprints.SMITHY;
			BlueprintData.Placement smithyAt = BlueprintItem.placementAt(level.dimension().location(), smithy.size(), new BlockPos(11, -57, -4),
				BlueprintItem.rotationFacing(Direction.SOUTH));
			BlockPos smithyFile = smithyAt.origin().below(smithy.basement());
			level.getStructureManager().get(smithy.id()).orElseThrow().placeInWorld(level, smithyFile, smithyFile,
				new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(smithyAt.rotation())
					.setMirror(smithyAt.mirror()), level.getRandom(), 2);
			Blueprint smithyPrint = BlueprintLibrary.get(level, smithy.id()).orElseThrow();
			java.util.function.Function<BlockPos, BlockPos> atSmithy = rel -> smithyAt.origin().offset(
				net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(rel, smithyAt.mirror(), smithyAt.rotation(), BlockPos.ZERO));
			java.util.Set<Long> columns = new java.util.HashSet<>();
			smithyPrint.blocks().stream().filter(e -> e.pos().getY() < 0).forEach(e -> columns.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
			double[] cellar = new double[4];
			int[] cellarFloor = {Integer.MAX_VALUE}; // the lowest air of the coal store: the camera stands on its floor (B188)
			for (Blueprint.Entry e : smithyPrint.blocks()) {
				BlockPos p = atSmithy.apply(e.pos());
				if (e.pos().getY() < 0) {
					level.removeBlockEntity(p);
					level.setBlock(p, (e.pos().getY() >= -2 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), 2 | 16);
					if (e.state().isAir()) {
						cellar[0] += p.getX() + 0.5;
						cellar[1] += p.getY();
						cellar[2] += p.getZ() + 0.5;
						cellar[3]++;
						cellarFloor[0] = Math.min(cellarFloor[0], p.getY());
					}
				} else if (e.pos().getY() == 0 && columns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()))
						&& (e.state().is(net.minecraft.tags.BlockTags.TRAPDOORS) || e.state().is(Blocks.LADDER) || e.state().isAir())) {
					level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2 | 16);
				}
			}
			BuildSiteManager.get(level).recordFinished(smithy.id(), smithyAt, player.getUUID());
			BuildSiteManager.get(level).recordFinished(inn.id(), innAt, player.getUUID());
			BlockPos bench = new BlockPos(4, -57, 6);
			level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
			Map<Item, Integer> stock = new LinkedHashMap<>(BuildPlan.create(io.github.jcondedata.aliveworkplace.build.Cellars.cellarOnly(smithyPrint), smithyAt).materials());
			List<ItemStack> stacks = new ArrayList<>();
			stock.forEach((item, total) -> {
				for (int left = total; left > 0; left -= item.getDefaultMaxStackSize()) {
					stacks.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
				}
			});
			for (int b = 0; b * 27 < stacks.size(); b++) {
				BlockPos barrelPos = bench.offset(1 + b, 0, 0);
				level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
				Container barrel = (Container) level.getBlockEntity(barrelPos);
				for (int slot = 0; slot < 27 && b * 27 + slot < stacks.size(); slot++) {
					barrel.setItem(slot, stacks.get(b * 27 + slot));
				}
			}
			Villager builder = EntityType.VILLAGER.spawn(level, bench.south(2), MobSpawnType.COMMAND);
			Builders.employ(level, builder, bench);
			List<BuildSiteManager.Finished> offered = io.github.jcondedata.aliveworkplace.build.Cellars.offer(level, builder);
			Showcase.check(offered.size() == 1 && offered.get(0).structure().equals(smithy.id()), "the builder offers \"Dig the cellar\" for the old Smithy alone: " + offered);
			ItemStack job = builder.getOffers().stream().map(o -> o.getResult())
				.filter(r -> BlueprintItem.data(r).map(d -> d.structure().equals(smithy.id()) && d.placement().isPresent()).orElse(false))
				.findFirst().orElse(ItemStack.EMPTY);
			BlueprintData data = BlueprintItem.data(job).orElse(null);
			BuildSite site = data == null ? null : Builders.start(level, builder, null, data.structure(), data.placement().orElseThrow());
			Showcase.check(site != null && site.isCellarOnly(), "the job's blueprint, given back to the builder, started a cellar dig");
			java.util.UUID id = site == null ? null : site.id();
			Vec3 middle = cellar[3] == 0 ? Vec3.atCenterOf(smithyAt.origin()) : new Vec3(cellar[0] / cellar[3], cellar[1] / cellar[3], cellar[2] / cellar[3]);
			long[] since = {-1};
			int[] step = {0};
			return l -> {
				long t = l.getGameTime();
				if (step[0] == 0 && since[0] < 0) {
					since[0] = t;
				}
				if (step[0] == 0 && t - since[0] >= 40) {
					step[0]++;
					aim(l, player, Vec3.atCenterOf(innAt.origin()).add(-6, 2, 9), Vec3.atCenterOf(innAt.origin()).add(-6, -3, -4));
				} else if (step[0] == 1 && t - since[0] >= 80) {
					step[0]++;
					SHOTS.add("02_inn_cellar");
				} else if (step[0] == 2 && t - since[0] >= 110) {
					step[0]++;
					aim(l, player, new Vec3(0.5, -48, 14.5), new Vec3(0, -58, -6));
				} else if (step[0] == 3 && built(l, id, 1f)) {
					step[0]++;
					since[0] = t;
					BlockPos floor = BlockPos.containing(middle.x, middle.y - 0.5, middle.z);
					Showcase.check(!io.github.jcondedata.aliveworkplace.build.Cellars.needsDigging(l, smithy.id(), smithyAt) && l.getBlockState(BlockPos.containing(middle)).isAir(),
						"the coal store was dug out under the old Smithy and fitted out (floor " + l.getBlockState(floor) + ")");
					// B188: the camera's feet on the cellar floor (its eye 1.62 above), not the middle's height plus 0.6, which put the eye
					// in the Smithy's floor (a blank picture on GitHub); night vision for the unlit store.
					double feet = cellarFloor[0] == Integer.MAX_VALUE ? middle.y : cellarFloor[0];
					player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, -1, 0, false, false));
					aim(l, player, new Vec3(middle.x - 1.5, feet, middle.z - 1.5), new Vec3(middle.x + 1.5, feet + 0.5, middle.z + 1.5));
				} else if (step[0] == 4 && t - since[0] >= 40) {
					step[0]++;
					SHOTS.add("02_smithy_cellar");
				} else if (step[0] == 5 && t - since[0] >= 70) {
					step[0]++;
					player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
					aim(l, player, new Vec3(0.5, -48, 14.5), new Vec3(0, -58, -6));
				}
				return step[0] == 6 && t - since[0] >= 110;
			};
		}, null));
		SCENES.put("legend_pathfinder", new Job("the Pathfinder led the player through a forest to a staged Stronghold and planted a banner at its entrance", 1600,
			new Vec3(9.5, -55, 9.5), new Vec3(0, -59.5, 10), (level, player) -> {
			// ROADMAP 29.13: the Pathfinder settled at a cartography table with 8 bread in the chest, between rows of oaks.
			// The scene starts an expedition with the player (hovering at the camera, within 24 blocks of the whole path,
			// so they never wait) to a Stronghold staged 20 blocks south; they walk the forest path and plant the banner.
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(3000);
			for (int z = -2; z <= 24; z += 5) {
				for (int x : new int[] {-4, 4}) {
					BlockPos trunk = STATION.offset(x, 0, z + (x > 0 ? 2 : 0));
					for (int y = 0; y < 5; y++) {
						place(level, trunk.above(y), Blocks.OAK_LOG);
					}
					for (BlockPos leaf : BlockPos.betweenClosed(trunk.offset(-2, 3, -2), trunk.offset(2, 5, 2))) {
						if (level.getBlockState(leaf).isAir() && leaf.distManhattan(trunk.above(4)) <= 3) {
							level.setBlockAndUpdate(leaf, Blocks.OAK_LEAVES.defaultBlockState()
								.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
						}
					}
				}
			}
			BlockPos hall = STATION.offset(-2, 0, -4);
			place(level, hall, ModBlocks.VILLAGE_HALL);
			Villager pathfinder = worker(level, STATION, Blocks.CARTOGRAPHY_TABLE, PoiTypes.CARTOGRAPHER, VillagerProfession.CARTOGRAPHER);
			chest(level, chestPos(), new ItemStack(Items.BREAD, 8));
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(
				io.github.jcondedata.aliveworkplace.AliveWorkplace.id("pathfinder")).orElse(null);
			Showcase.check(legend != null, "the Pathfinder's file loaded");
			if (legend == null) {
				return l -> true;
			}
			io.github.jcondedata.aliveworkplace.legend.Legends.make(level, pathfinder, legend, "showcase");
			BlockPos target = STATION.offset(0, 0, 20);
			boolean out = io.github.jcondedata.aliveworkplace.legend.Pathfinder.start(level, player, pathfinder, "stronghold", target, null);
			Showcase.check(out, "the Pathfinder set out with the player");
			return l -> io.github.jcondedata.aliveworkplace.legend.Pathfinder.state(pathfinder).arrived()
				&& l.getBlockState(target).is(Blocks.LIGHT_BLUE_BANNER);
		}, null));
		SCENES.put("legend_seer", new Job("the Seer came to the Chapel at midnight under a full moon, and at dawn foretold the night, the festival, the market and the next guest in chat",
			900, new Vec3(6.5, -55.5, 11.5), new Vec3(0, -54, -9), (level, player) -> {
			// ROADMAP 29.16: a finished Chapel facing the camera, the Village Hall beside it and six villagers out front,
			// at midnight under a full moon (day 81). The hall's own round rolls the Chapel's guest (a fixed seed whose roll
			// is under the Seer's 1 in 2); they come out to the door, end-rod motes round them. Then it's dawn: they settle
			// and the hall's dawn round has them foretell, in the player's chat.
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(80L * 24000L + 17800L);
			StarterBlueprints.Entry entry = StarterBlueprints.CHAPEL;
			BlockPos anchor = new BlockPos(0, -60, -4);
			Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
			BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), blueprint.size(), anchor,
				BlueprintItem.rotationFacing(Direction.SOUTH));
			level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, placement.origin(), placement.origin(),
				new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(placement.rotation())
					.setMirror(placement.mirror()), level.getRandom(), 2);
			BuildSiteManager.get(level).recordFinished(entry.id(), placement, player.getUUID());
			BlockPos hall = STATION.offset(9, 0, 1);
			for (int i = 0; i < 6; i++) {
				Villager v = EntityType.VILLAGER.spawn(level, new BlockPos(-4 + (i % 3) * 4, -60, 3 + (i / 3) * 2), MobSpawnType.COMMAND);
				v.setNoAi(true);
				v.setYRot(180);
				v.setYHeadRot(180);
			}
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(
				io.github.jcondedata.aliveworkplace.legend.Seer.ID).orElse(null);
			Showcase.check(legend != null, "the Seer's file loaded");
			Showcase.check(level.getMoonPhase() == 0, "a full moon");
			long began = level.getGameTime();
			Villager[] seer = {null};
			boolean[] told = {false};
			boolean[] rolled = {false};
			boolean[] dawn = {false};
			return l -> {
				long t = l.getGameTime() - began;
				if (t >= 40 && !rolled[0] && legend != null) {
					rolled[0] = true;
					place(l, hall, ModBlocks.VILLAGE_HALL); // here, so its own first round doesn't roll the night first
					// B130: the hall's (and the Chapel's) records now, not in vanilla's next task: the round below reads them
					ScreenshotHarness.fileRecords(l, hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS);
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					// B130: a Rare Legend's visit asks for a Friend of the village since 31.12 (the Seer's chapel way): the player is one
					ServerPlayer friend = l.getServer().getPlayerList().getPlayers().get(0);
					io.github.jcondedata.aliveworkplace.story.Reputation.add(l, hall, friend, 50 - io.github.jcondedata.aliveworkplace.story.Reputation.points(l, hall, friend.getUUID()));
					io.github.jcondedata.aliveworkplace.legend.LegendGuests.round(l, hall, net.minecraft.util.RandomSource.create(4242L));
					seer[0] = io.github.jcondedata.aliveworkplace.legend.LegendGuests.guest(l, hall);
					Showcase.check(seer[0] != null && ModAttachments.LEGEND.get(seer[0]).id().equals(legend.id()), "the Seer came to the Chapel at midnight"
						+ (seer[0] != null ? "" : " (candidates " + io.github.jcondedata.aliveworkplace.legend.LegendGuests.candidates(l, hall, "chapel",
							io.github.jcondedata.aliveworkplace.hall.Chronicle.day(l)).stream().map(g -> g.id().toString()).toList()
							+ ", slots " + io.github.jcondedata.aliveworkplace.legend.LegendSlots.whyNot(l, hall, legend, null).map(Component::getString).orElse("free")
							+ ", ways met " + legend.ways("visit").stream().filter(w -> io.github.jcondedata.aliveworkplace.legend.LegendGuests.wayMet(l, hall, w)).count()
							+ " of " + legend.ways("visit").size() + ", time " + l.getDayTime() % 24000 + ", moon " + l.getMoonPhase() + ")"));
					if (seer[0] != null) {
						seer[0].setNoAi(true);
						seer[0].moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 1.5, 180f, 0f);
						seer[0].setYHeadRot(180f);
					}
				}
				if (seer[0] != null) {
					io.github.jcondedata.aliveworkplace.legend.Legends.tick(seer[0]); // (no AI to tick it: the motes at night)
				}
				if (t >= 420 && !dawn[0] && seer[0] != null) {
					dawn[0] = true;
					l.setDayTime(81L * 24000L + 300L); // dawn
					io.github.jcondedata.aliveworkplace.legend.Legends.make(l, seer[0], legend, "showcase");
					io.github.jcondedata.aliveworkplace.legend.LegendGuests.round(l, hall, net.minecraft.util.RandomSource.create(29L));
					var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) l.getBlockEntity(hall);
					told[0] = entity.seer().toldDay() == io.github.jcondedata.aliveworkplace.hall.Chronicle.day(l);
					Showcase.check(told[0], "the Seer foretold at dawn");
				}
				return told[0] && t >= 600;
			};
		}, null));
		SCENES.put("legend_grand_chef", new Job("the Grand Chef called the village to a banquet: everyone gathered round the hall and at supper each grown-up ate two meals of the eight kinds in the store",
			900, new Vec3(1.5, -54.5, 12), new Vec3(0, -60, 0), (level, player) -> {
			// ROADMAP 29.18: the Village Hall, a kitchen (a smoker with the store's chest beside it, eight kinds of meal) with
			// the Grand Chef at it in the tall toque, and six villagers about the place. After work the banquet is called
			// and the hall's banquet gathering walks them to the hall; at supper the feast: two meals each.
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(9100);
			BlockPos hall = STATION;
			place(level, hall, ModBlocks.VILLAGE_HALL);
			BlockPos smoker = STATION.offset(5, 0, -3);
			Villager chef = worker(level, smoker, Blocks.SMOKER, PoiTypes.BUTCHER, io.github.jcondedata.aliveworkplace.registry.ModVillagers.CHEF);
			chest(level, smoker.east(), new ItemStack(Items.BREAD, 16), new ItemStack(Items.BAKED_POTATO, 16), new ItemStack(Items.COOKED_BEEF, 16),
				new ItemStack(Items.PUMPKIN_PIE, 16), new ItemStack(Items.COOKED_SALMON, 16), new ItemStack(Items.MUSHROOM_STEW, 1),
				new ItemStack(Items.COOKED_CHICKEN, 16), new ItemStack(Items.COOKIE, 16), new ItemStack(Items.MUSHROOM_STEW, 1),
				new ItemStack(Items.MUSHROOM_STEW, 1));
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(
				io.github.jcondedata.aliveworkplace.AliveWorkplace.id("grand_chef")).orElse(null);
			Showcase.check(legend != null, "the Grand Chef's file loaded");
			if (legend == null) {
				return l -> true;
			}
			io.github.jcondedata.aliveworkplace.legend.Legends.make(level, chef, legend, "showcase");
			io.github.jcondedata.aliveworkplace.legend.LegendPowers.forget();
			for (int i = 0; i < 6; i++) {
				EntityType.VILLAGER.spawn(level, STATION.offset(-9 + 3 * i, 0, i % 2 == 0 ? 6 : -6), MobSpawnType.COMMAND);
			}
			place(level, STATION.offset(-2, 0, 3), Blocks.CAKE);
			long began = level.getGameTime();
			boolean[] called = {false};
			int[] meals = {-1};
			return l -> {
				long t = l.getGameTime() - began;
				var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) l.getBlockEntity(hall);
				if (t >= 20 && !called[0] && entity != null) {
					called[0] = true;
					io.github.jcondedata.aliveworkplace.hall.Banquets.round(l, hall, entity);
					Showcase.check(entity.banquetDay() == io.github.jcondedata.aliveworkplace.hall.Chronicle.day(l), "the Grand Chef called a banquet");
				}
				if (t >= 400 && meals[0] < 0 && entity != null) {
					l.setDayTime(io.github.jcondedata.aliveworkplace.hall.Banquets.SUPPER + 100);
					int grown = l.getEntitiesOfClass(Villager.class, io.github.jcondedata.aliveworkplace.hall.VillageHalls.area(hall),
						v -> v.isAlive() && !v.isBaby() && !v.isSleeping()).size();
					io.github.jcondedata.aliveworkplace.hall.Banquets.round(l, hall, entity);
					meals[0] = l.getEntitiesOfClass(Villager.class, io.github.jcondedata.aliveworkplace.hall.VillageHalls.area(hall),
						v -> io.github.jcondedata.aliveworkplace.registry.ModAttachments.BANQUET.has(v)).size();
					Showcase.check(entity.banquetEaten() && meals[0] >= grown && grown >= 7, "everyone came to the feast (" + meals[0] + " of " + grown + ")");
				}
				return meals[0] >= 0 && t >= 640;
			};
		}, null));
		SCENES.put("legend_bard", new Job("the Bard Laureate settled and composed the village's anthem, which rang out over the hall in note-block notes; then they sang a work song among the busiest workers, notes rising round them",
			900, new Vec3(1.5, -54.5, 12), new Vec3(0, -60, 0), (level, player) -> {
			// ROADMAP 29.19: the Village Hall, the Bard Laureate (green doublet, lute on the back, laurel wreath) by it and
			// six workers at their trades in a knot to the east. The hall's round composes the anthem from the village's
			// name and plays it (16 notes, the note particles over the hall); mid-morning the Laureate walks to the knot of
			// workers and sings, notes rising round them, the workers there 25% faster.
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(2900);
			BlockPos hall = STATION;
			place(level, hall, ModBlocks.VILLAGE_HALL);
			io.github.jcondedata.aliveworkplace.hall.Anthems.forget();
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(
				io.github.jcondedata.aliveworkplace.legend.BardLaureate.ID).orElse(null);
			Showcase.check(legend != null, "the Bard Laureate's file loaded");
			if (legend == null) {
				return l -> true;
			}
			Villager bard = EntityType.VILLAGER.spawn(level, STATION.offset(-3, 0, 3), MobSpawnType.COMMAND);
			bard.setVillagerData(bard.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BARD).setLevel(5));
			bard.setVillagerXp(250);
			io.github.jcondedata.aliveworkplace.legend.Legends.make(level, bard, legend, "showcase");
			io.github.jcondedata.aliveworkplace.legend.LegendPowers.forget();
			net.minecraft.world.entity.npc.VillagerProfession[] trades = {net.minecraft.world.entity.npc.VillagerProfession.FARMER,
				net.minecraft.world.entity.npc.VillagerProfession.MASON, net.minecraft.world.entity.npc.VillagerProfession.FLETCHER,
				net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN, net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH,
				net.minecraft.world.entity.npc.VillagerProfession.SHEPHERD};
			Villager[] workers = new Villager[trades.length];
			for (int i = 0; i < trades.length; i++) {
				workers[i] = EntityType.VILLAGER.spawn(level, STATION.offset(5 + (i % 3) * 2, 0, -1 + (i / 3) * 3), MobSpawnType.COMMAND);
				workers[i].setVillagerData(workers[i].getVillagerData().setProfession(trades[i]).setLevel(2));
				workers[i].setVillagerXp(20);
				workers[i].setNoAi(true);
				workers[i].setYRot(180);
				workers[i].setYHeadRot(180);
			}
			long began = level.getGameTime();
			boolean[] composed = {false};
			boolean[] played = {false};
			boolean[] sang = {false};
			// the check runs every 10 client ticks, so a moment is "at or after", never one exact tick (B88)
			return l -> {
				long t = l.getGameTime() - began;
				var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) l.getBlockEntity(hall);
				if (t >= 40 && !composed[0] && entity != null) {
					composed[0] = true;
					io.github.jcondedata.aliveworkplace.hall.Anthems.round(l, hall, entity);
					Showcase.check(entity.anthem().isPresent(), "the anthem was composed");
				}
				if (t >= 200 && composed[0] && !played[0]) {
					played[0] = true;
					Showcase.check(io.github.jcondedata.aliveworkplace.hall.Anthems.notesPlayed(hall) == 16,
						"the anthem played 16 notes (" + io.github.jcondedata.aliveworkplace.hall.Anthems.notesPlayed(hall) + ")");
					l.setDayTime(3000);
				}
				if (t >= 600 && played[0] && !sang[0]) {
					sang[0] = true;
					Showcase.check(io.github.jcondedata.aliveworkplace.legend.BardLaureate.singing(bard), "the Bard Laureate is singing a work song");
					Showcase.check(io.github.jcondedata.aliveworkplace.legend.LegendPowers.pace(workers[0]) > 1.2f,
						"the workers near the song work faster (" + io.github.jcondedata.aliveworkplace.legend.LegendPowers.pace(workers[0]) + "×)");
				}
				return sang[0] && t >= 700;
			};
		}, null));
		SCENES.put("legend_beastmaster", new Job("the Beastmaster tamed a war dog for the guard with bones from the chest and fitted it with wolf armour from the scutes; the dog followed its guard and fought the zombies beside them",
			900, new Vec3(1.5, -54.5, 12), new Vec3(0, -60, 0), (level, player) -> {
			// ROADMAP 29.20: the Village Hall, the Beastmaster (furs, a wolf-pelt hood) at their feed trough with bones and
			// armadillo scutes in the chest beside it, a wild wolf, and a guard at a Guard Post to the east. The hall's round
			// tames the wolf for the guard (hearts, wolf armour, "<guard>'s War Dog"); then three zombies come at the guard
			// and the dog goes for them at the guard's side.
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(2000);
			BlockPos hall = STATION;
			place(level, hall, ModBlocks.VILLAGE_HALL);
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(
				io.github.jcondedata.aliveworkplace.legend.Beastmaster.ID).orElse(null);
			Showcase.check(legend != null, "the Beastmaster's file loaded");
			if (legend == null) {
				return l -> true;
			}
			Villager beastmaster = worker(level, STATION.offset(-5, 0, 2), ModBlocks.FEED_TROUGH, ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
			beastmaster.setVillagerData(beastmaster.getVillagerData().setLevel(5));
			beastmaster.setVillagerXp(250);
			beastmaster.setNoAi(true);
			beastmaster.setYRot(180);
			beastmaster.setYHeadRot(180);
			io.github.jcondedata.aliveworkplace.legend.Legends.make(level, beastmaster, legend, "showcase");
			io.github.jcondedata.aliveworkplace.legend.LegendPowers.forget();
			chest(level, STATION.offset(-6, 0, 2), new ItemStack(Items.BONE, 6), new ItemStack(Items.ARMADILLO_SCUTE, 6));
			Villager guard = worker(level, STATION.offset(5, 0, 2), ModBlocks.GUARD_POST, ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			net.minecraft.world.entity.animal.Wolf wolf = EntityType.WOLF.spawn(level, STATION.offset(-1, 0, 5), MobSpawnType.COMMAND);
			long began = level.getGameTime();
			boolean[] tamed = {false};
			net.minecraft.world.entity.monster.Zombie[] zombies = new net.minecraft.world.entity.monster.Zombie[3];
			boolean[] bit = {false};
			boolean[] came = {false};
			// the check runs every 10 client ticks, so a moment is "at or after", never one exact tick (B88)
			return l -> {
				long t = l.getGameTime() - began;
				if (t >= 60 && !tamed[0]) {
					tamed[0] = true;
					io.github.jcondedata.aliveworkplace.legend.Beastmaster.round(l, hall);
					Showcase.check(io.github.jcondedata.aliveworkplace.legend.Beastmaster.dog(l, guard) == wolf, "the wolf was tamed as the guard's war dog");
					Showcase.check(wolf.hasArmor(), "the war dog wears wolf armour");
				}
				if (t >= 220 && tamed[0] && !came[0]) {
					came[0] = true;
					for (int i = 0; i < zombies.length; i++) {
						zombies[i] = EntityType.ZOMBIE.spawn(l, STATION.offset(2 + i * 3, 0, -6), MobSpawnType.COMMAND);
						if (zombies[i] != null) {
							zombies[i].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning by day
						}
					}
				}
				for (net.minecraft.world.entity.monster.Zombie z : zombies) {
					if (z != null && wolf.getTarget() == z) {
						bit[0] = true;
					}
				}
				boolean cleared = came[0] && java.util.Arrays.stream(zombies).allMatch(z -> z == null || !z.isAlive());
				if (cleared && t >= 400 || t >= 880) {
					Showcase.check(bit[0], "the war dog went for the guard's zombies");
					Showcase.check(cleared, "the guard and the dog beat the zombies");
					return true;
				}
				return false;
			};
		}, null));
		SCENES.put("legend_founder", new Job("the Founder asked for a statue, and the village's builder raised it by the hall from the materials in the barrels: a stepped plinth, a copper plaque and the Founder in stone with a hand raised",
			6000, new Vec3(3.5, -53.5, 10.5), new Vec3(-5, -58, -5), (level, player) -> {
			// ROADMAP 29.23: the Village Hall, the Founder (a burgundy mantle, a gold chain of office) by it, and a builder
			// at a Blueprint Table with barrels holding the statue's materials. The hall's round hands the builder the
			// Founder's statue on open ground near the hall, and the GIF shows it going up.
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(2000);
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
			BlockPos hall = STATION;
			place(level, hall, ModBlocks.VILLAGE_HALL);
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(
				io.github.jcondedata.aliveworkplace.legend.Founder.ID).orElse(null);
			Showcase.check(legend != null, "the Founder's file loaded");
			if (legend == null) {
				return l -> true;
			}
			BlockPos bench = STATION.offset(6, 0, 4);
			level.setBlockAndUpdate(bench, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
			// the hall's and table's records now, not in vanilla's next task: the builder keeps his job, the Founder has a village (B88)
			ScreenshotHarness.fileRecords(level, STATION, 10);
			Villager builder = EntityType.VILLAGER.spawn(level, bench.offset(-1, 0, 1), MobSpawnType.COMMAND);
			Builders.employ(level, builder, bench);
			Villager founder = EntityType.VILLAGER.spawn(level, STATION.offset(2, 0, 2), MobSpawnType.COMMAND);
			founder.setVillagerData(founder.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(5));
			founder.setNoAi(true);
			founder.setYRot(180);
			founder.setYHeadRot(180);
			io.github.jcondedata.aliveworkplace.legend.Legends.make(level, founder, legend, "inspired");
			io.github.jcondedata.aliveworkplace.legend.LegendPowers.forget();
			BuildSite site = io.github.jcondedata.aliveworkplace.legend.Founder.statue(level, hall);
			Showcase.check(site != null, "the statue was handed to the builder");
			if (site == null) {
				return l -> true;
			}
			List<ItemStack> stacks = new ArrayList<>();
			site.plan(level).materials().forEach((item, total) -> {
				for (int left = total; left > 0; left -= item.getDefaultMaxStackSize()) {
					stacks.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
				}
			});
			for (int b = 0; b * 27 < stacks.size(); b++) {
				BlockPos barrelPos = bench.offset(1, 0, b);
				level.setBlockAndUpdate(barrelPos, Blocks.BARREL.defaultBlockState());
				Container barrel = (Container) level.getBlockEntity(barrelPos);
				for (int slot = 0; slot < 27 && b * 27 + slot < stacks.size(); slot++) {
					barrel.setItem(slot, stacks.get(b * 27 + slot));
				}
			}
			java.util.UUID id = site.id();
			return l -> {
				// B246: the statue counts once the builder has handed the job in (Builders.finish files it among the
				// village's buildings and drops the site). built(…, 1f) is already true from the LANDSCAPE stage on, a
				// few work ticks before that, and on GitHub's slower runner the checks landed in that gap.
				if (BuildSiteManager.get(l).get(id) != null) {
					return false;
				}
				io.github.jcondedata.aliveworkplace.legend.Founder.forgetStands();
				Showcase.check(io.github.jcondedata.aliveworkplace.legend.Founder.stands(l, hall), "the statue stands");
				Showcase.check(io.github.jcondedata.aliveworkplace.legend.Founder.beauty(l, hall) == 5, "the statue is worth 5 beauty");
				return true;
			};
		}, null));
		SCENES.put("legend_golem_smith", new Job("the Golem Smith forged a Hauler Golem, and the hauler, a farmhand and a wall sentry went to work",
			2400, new Vec3(1.5, -51.5, 11), new Vec3(0, -60, -5), (level, player) -> {
			// ROADMAP 29.15: the Golem Smith at a smithing table, the costs of a Hauler in the chest beside it and five
			// villagers round the hall: the forge builds the hauler at once. A farmhand and a wall sentry are already out:
			// the hauler empties a full Drop Box into the Storehouse's chest, the farmhand harvests and replants a ripe
			// wheat field into its farmer's chest, and the sentry holds the first point of its Patrol Map against zombies.
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			level.setDayTime(3000);
			BlockPos hall = STATION.offset(-3, 0, 3);
			place(level, hall, ModBlocks.VILLAGE_HALL);
			Villager smith = worker(level, STATION, Blocks.SMITHING_TABLE, PoiTypes.TOOLSMITH, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TINKERER);
			io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(
				io.github.jcondedata.aliveworkplace.legend.GolemSmith.ID).orElse(null);
			Showcase.check(legend != null, "the Golem Smith's file loaded");
			if (legend == null) {
				return l -> true;
			}
			io.github.jcondedata.aliveworkplace.legend.Legends.make(level, smith, legend, "showcase");
			Container costs = chest(level, STATION.east(), new ItemStack(Items.IRON_BLOCK, 4), new ItemStack(Items.CARVED_PUMPKIN), new ItemStack(Items.CHEST));
			for (int i = 0; i < 4; i++) {
				Villager v = EntityType.VILLAGER.spawn(level, STATION.offset(-6 + 2 * i, 0, 4), MobSpawnType.COMMAND);
				v.setNoAi(true);
			}
			// The hauler's round: a full Drop Box out east, the Storehouse and its chest out west.
			BlockPos box = STATION.offset(5, 0, -3);
			place(level, box, ModBlocks.DROP_BOX);
			Container drop = (Container) level.getBlockEntity(box);
			for (int i = 0; i < 9; i++) {
				drop.setItem(i, new ItemStack(i % 3 == 0 ? Items.COBBLESTONE : i % 3 == 1 ? Items.OAK_LOG : Items.COAL, 64));
			}
			place(level, STATION.offset(-6, 0, -3), ModBlocks.STOREHOUSE);
			Container store = chest(level, STATION.offset(-5, 0, -3));
			// The farmhand's field: 5x5 ripe wheat behind, its farmer's composter and chest beside it.
			for (int x = -2; x <= 2; x++) {
				for (int z = -12; z <= -8; z++) {
					level.setBlockAndUpdate(new BlockPos(x, -61, z), Blocks.FARMLAND.defaultBlockState());
					level.setBlockAndUpdate(new BlockPos(x, -60, z), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
				}
			}
			BlockPos composter = new BlockPos(4, -60, -10);
			Villager farmer = worker(level, composter, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
			farmer.setNoAi(true);
			ModAttachments.FARM_FIELD.set(farmer, new io.github.jcondedata.aliveworkplace.farm.FieldJob(
				net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(new BlockPos(-2, -61, -12), new BlockPos(2, -61, -8))));
			Container field = chest(level, composter.east());
			// The wall: a stone-brick walk west with the sentry's post on it, and three zombies (helmeted against the sun)
			// coming from beyond it.
			BlockPos post = STATION.offset(-8, 0, -7);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					level.setBlockAndUpdate(post.offset(dx, -1, dz), Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
			net.minecraft.world.entity.animal.IronGolem hauler = io.github.jcondedata.aliveworkplace.legend.GolemSmith.forgeOne(level, hall, smith);
			Showcase.check(hauler != null && costs.isEmpty(), "the Golem Smith forged a Hauler Golem from the chest");
			net.minecraft.world.entity.animal.IronGolem farmhand = io.github.jcondedata.aliveworkplace.legend.GolemSmith.build(level, STATION.offset(2, 0, -5),
				io.github.jcondedata.aliveworkplace.legend.GolemSmith.Role.FARMHAND);
			net.minecraft.world.entity.animal.IronGolem sentry = io.github.jcondedata.aliveworkplace.legend.GolemSmith.build(level, STATION.offset(-4, 0, -2),
				io.github.jcondedata.aliveworkplace.legend.GolemSmith.Role.SENTRY);
			if (sentry != null) {
				ModAttachments.GOLEM_POST.set(sentry, post);
			}
			List<net.minecraft.world.entity.monster.Zombie> zombies = new java.util.ArrayList<>();
			for (int i = 0; i < 3; i++) {
				var zombie = EntityType.ZOMBIE.spawn(level, post.offset(-7, 0, -2 + 2 * i), MobSpawnType.COMMAND);
				zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				zombie.setTarget(sentry);
				zombies.add(zombie);
			}
			return l -> {
				if (farmhand == null || sentry == null) {
					timeoutWhy = "a golem wasn't built (farmhand " + farmhand + ", sentry " + sentry + ")";
					return false;
				}
				int stored = store.countItem(Items.COBBLESTONE) + store.countItem(Items.OAK_LOG) + store.countItem(Items.COAL);
				int wheat = field.countItem(Items.WHEAT);
				long alive = zombies.stream().filter(net.minecraft.world.entity.Entity::isAlive).count();
				double fromPost = sentry.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(post));
				// B329: the timeout names every part's state, so a failure says which golem it waited on and what it was doing
				timeoutWhy = "stored " + stored + "/" + 9 * 64 + ", drop box holds " + (9 * 64 - stored) + ", " + golemState(hauler)
					+ "; wheat " + wheat + "/25, " + golemState(farmhand) + "; zombies alive " + alive + ", sentry " + String.format("%.1f", fromPost)
					+ " from its post, " + golemState(sentry);
				return stored >= 9 * 64 && wheat >= 25 && alive == 0 && fromPost < 3;
			};
		}, null));
		SCENES.put("bard", job("the bard played a record at the Music Stand", 1200, (level, player) -> {
			Villager b = picked(level, player, STATION, Blocks.JUKEBOX, Items.MUSIC_DISC_CAT);
			chest(level, chestPos(), new ItemStack(Items.MUSIC_DISC_CAT));
			return l -> io.github.jcondedata.aliveworkplace.bard.BardWork.playing(b).isPresent();
		}));
		SCENES.put("fossil", job("the fossil scientist revived a Kabuto from the Dome Fossil", 2400, (level, player) -> {
			// at Cobblemon's Fossil Analyzer, the job's workstation since ROADMAP 21.1c
			Villager sci = picked(level, player, STATION, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
				io.github.jcondedata.aliveworkplace.registry.ModVillagers.FOSSIL_ANALYZER_BLOCK), cobblemonItem("dome_fossil"));
			chest(level, chestPos());
			io.github.jcondedata.aliveworkplace.fossil.FossilScientists.REVIVE_TICKS = 300;
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.teleportTo(level, 1.5, -60, 3.5, 180, 10);
			io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(player, 10_000);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(cobblemonItem("dome_fossil")));
			io.github.jcondedata.aliveworkplace.fossil.FossilScientists.handOver(player, sci);
			ScreenshotHarness.hoverLookingAt(player, CAMERA, TARGET);
			return l -> n(ModAttachments.FOSSILS_REVIVED, sci) >= 1;
		}));
		SCENES.put("camp_cook", job("the camp cook cooked a Poké Snack in the Campfire Pot", 2400, (level, player) -> {
			// ROADMAP 28.8: Cobblemon's campfire with a red pot on it, picked with Hearty Grains; the makings of a Poké
			// Snack in the chest beside it. She fills the pot, shuts the lid, and the snack comes out into the chest.
			net.minecraft.world.level.block.Block campfire = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
				io.github.jcondedata.aliveworkplace.registry.ModVillagers.CAMPFIRE_POT_BLOCK);
			level.setBlockAndUpdate(STATION, campfire.defaultBlockState());
			if (level.getBlockEntity(STATION) instanceof com.cobblemon.mod.common.block.entity.CampfireBlockEntity pot) {
				pot.setPotItem(new ItemStack(cobblemonItem("campfire_pot_red")));
			}
			Villager cook = picked(level, player, STATION, level.getBlockState(STATION), cobblemonItem("hearty_grains"));
			Container c = chest(level, chestPos(), new ItemStack(cobblemonItem("moomoo_milk"), 3), new ItemStack(Items.HONEY_BOTTLE, 2),
				new ItemStack(cobblemonItem("vivichoke"), 1), new ItemStack(cobblemonItem("hearty_grains"), 3),
				new ItemStack(cobblemonItem("oran_berry"), 3));
			return l -> c.countItem(cobblemonItem("poke_snack")) > 0 && n(ModAttachments.DISHES_COOKED, cook) >= 1;
		}));
		SCENES.put("cuisine_kitchen", new Job("asked at the hall board's Dishes tab, the Journeyman Chef cooked 4 Mixed Vegetable Salads and 2 Beanburger Curries "
			+ "from their own recipes into the chest; the Fruit Punch (no Calcium in the store) is still waiting and the cake is locked for his level", 2400, CAMERA, TARGET, (level, player) -> {
			// ROADMAP 38.8a (Cobblemon and Cobblecuisine): a Village Hall, a Journeyman Chef at a Kitchen Stove, and in the
			// chest beside it the makings of 4 Mixed Vegetable Salads (a level-1 dish) and of 2 Beanburger Curries (level 3).
			// He cooks Cobblecuisine's dishes only when asked at the hall's board: the player asks on the Dishes tab for the
			// salads, the curries and a Fruit Punch (whose Calcium the store lacks). He cooks the first two into the chest; the
			// last still waits on the board afterwards, beside the cakes, locked for a Journeyman. A sack of potatoes (B180).
			java.util.function.Function<String, Item> cc = id -> net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblecuisine", id));
			Showcase.check(io.github.jcondedata.aliveworkplace.craft.CuisineDishes.of(cc.apply("beanburger_curry")) != null,
				"Cobblecuisine's dishes are on the board (Cobblecuisine, cuisineDishes)");
			BlockPos hall = new BlockPos(4, -60, -5);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState()
				.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
			cuisineHall = hall;
			Villager chef = worker(level, STATION, ModBlocks.KITCHEN_STOVE, ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
			chef.setVillagerData(chef.getVillagerData().setLevel(3));
			chef.setVillagerXp(150);
			Container c = chest(level, chestPos(), new ItemStack(cc.apply("salad_mix"), 4), new ItemStack(cobblemonItem("bold_mint"), 4),
				new ItemStack(cc.apply("green_bean"), 4), new ItemStack(Items.CARROT, 4), new ItemStack(cc.apply("red_bean"), 2),
				new ItemStack(Items.BREAD, 2), new ItemStack(Items.POTATO, 16), new ItemStack(cobblemonItem("charti_berry"), 2),
				new ItemStack(Items.COOKED_CHICKEN, 2));
			Showcase.check(io.github.jcondedata.aliveworkplace.craft.Chefs.menu(level, chef).stream()
				.noneMatch(i -> io.github.jcondedata.aliveworkplace.craft.CuisineDishes.of(i) != null), "no Cobblecuisine dish on the Chef's own menu");
			for (String[] ask : new String[][] {{"mixed_vegetable_salad", "4"}, {"beanburger_curry", "2"}, {"fruit_punch", "4"}}) {
				Showcase.check(io.github.jcondedata.aliveworkplace.craft.DishRequests.request(level, hall, cc.apply(ask[0]), Integer.parseInt(ask[1]))
					== io.github.jcondedata.aliveworkplace.craft.DishRequests.Result.ASKED, "the board took the request for " + ask[0]);
			}
			Showcase.check(io.github.jcondedata.aliveworkplace.craft.DishRequests.request(level, hall, cc.apply("fruit_honey_cake"), 1)
				== io.github.jcondedata.aliveworkplace.craft.DishRequests.Result.LEVEL, "the cake is locked for a Journeyman");
			return l -> c.countItem(cc.apply("mixed_vegetable_salad")) >= 4 && c.countItem(cc.apply("beanburger_curry")) >= 2
				&& io.github.jcondedata.aliveworkplace.craft.DishRequests.get(l).waiting(hall, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cc.apply("fruit_punch"))) == 4;
		}, new After("04_board_dishes", (level, player) -> {
			ScreenshotHarness.hover(player, Vec3.atCenterOf(cuisineHall.south(2)), 180, 10);
			io.github.jcondedata.aliveworkplace.work.ChoiceMenu.openHall(player, net.minecraft.network.chat.Component.translatable("screen.aliveworkplace.hall.title",
				io.github.jcondedata.aliveworkplace.hall.VillageHalls.name(level, cuisineHall)), p -> true,
				menu -> io.github.jcondedata.aliveworkplace.trade.TradePage.render(menu, level, cuisineHall,
					io.github.jcondedata.aliveworkplace.trade.TradePage.Tab.DISHES, null, player));
		}, io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, 6)));
		SCENES.put("campfire_pot", job("the Chef cooked EXP Candies and Berry Sweets from the Campfire Pot's recipes into the chest", 2400, (level, player) -> {
			// ROADMAP 38.11 (Cobblemon): a Chef at a Kitchen Stove, and in the chest beside it the makings of two of the
			// Campfire Pot's recipes, found by recipe type: EXP Candy XS (sculk and honeycomb) and the Berry Sweet (blue
			// dye, a honey bottle and sugar). He cooks both into the chest, with no pot of his own.
			Showcase.check(io.github.jcondedata.aliveworkplace.craft.Chefs.potDishes(level).contains(cobblemonItem("exp_candy_xs")),
				"the Campfire Pot's recipes are on the Chef's menu (Cobblemon, cuisineDishes)");
			worker(level, STATION, ModBlocks.KITCHEN_STOVE, ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
			Container c = chest(level, chestPos(), new ItemStack(Items.SCULK, 4), new ItemStack(Items.HONEYCOMB, 4),
				new ItemStack(Items.BLUE_DYE, 2), new ItemStack(Items.HONEY_BOTTLE, 2), new ItemStack(Items.SUGAR, 2));
			return l -> c.countItem(cobblemonItem("exp_candy_xs")) >= 1 && c.countItem(cobblemonItem("berry_sweet")) >= 1;
		}));
		SCENES.put("furnicrafter", job("the Furnicrafter made the Oak Chairs, Red Lamps and Poke Ball Desk the storehouse ordered from the planks, wool, redstone and concrete in its store", 2400, (level, player) -> {
			// ROADMAP 38.12 (CobbleFurnies): a Furnicrafter at CobbleFurnies' Furni Crafter, and by it a Storehouse whose
			// chest holds planks, red wool, redstone and red and white concrete, with stock orders for Oak Chairs, Red Lamps
			// and Poke Ball Desks. She makes each from the store's materials (the chairs' sticks cut from the planks first)
			// and puts them in the store.
			java.util.function.Function<String, Item> cf = id -> net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblefurnies", id));
			Block crafter = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ModVillagers.FURNICRAFTER_BLOCK);
			Showcase.check(ModVillagers.FURNICRAFTER != null && io.github.jcondedata.aliveworkplace.craft.FurnicrafterWork.ENABLED
				&& io.github.jcondedata.aliveworkplace.craft.FurnitureRecipes.makes(level.getServer(), cf.apply("oak_chair")),
				"the Furnicrafter's job and CobbleFurnies' recipes are there (CobbleFurnies, furnicrafters)");
			BlockPos storehouse = chestPos().west();
			level.setBlockAndUpdate(storehouse, ModBlocks.STOREHOUSE.defaultBlockState());
			Container c = chest(level, chestPos(), new ItemStack(Items.OAK_PLANKS, 6), new ItemStack(Items.RED_WOOL, 2),
				new ItemStack(Items.REDSTONE, 2), new ItemStack(Items.RED_CONCRETE, 1), new ItemStack(Items.WHITE_CONCRETE, 1));
			for (String piece : java.util.List.of("oak_chair", "red_lamp", "poke_ball_desk")) {
				io.github.jcondedata.aliveworkplace.store.StockOrders.cycle(level, storehouse, cf.apply(piece));
			}
			worker(level, STATION, crafter, ModVillagers.FURNICRAFTER_POI, ModVillagers.FURNICRAFTER);
			return l -> c.countItem(cf.apply("oak_chair")) >= 1 && c.countItem(cf.apply("red_lamp")) >= 1 && c.countItem(cf.apply("poke_ball_desk")) >= 1;
		}));
		SCENES.put("bean_farm", job("the farmer cut the ripe Cobblecuisine beans into the chest, left the green ones and sowed the bare rows", 2400, (level, player) -> {
			// ROADMAP 38.9 (Cobblemon and Cobblecuisine): a farmer's 5x3 field by her composter, watered down the middle,
			// a row of ripe beans, a row still green and a row of bare farmland; bean seeds in the chest and no beans, so
			// the menu's dishes want some. She cuts only the ripe row (the beans into the chest), sows the bare row and
			// the cut one, and leaves the green row to grow.
			java.util.function.Function<String, Item> cc = id -> net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblecuisine", id));
			Block crop = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblecuisine", "bean_crop"));
			net.minecraft.world.level.block.state.properties.IntegerProperty age =
				(net.minecraft.world.level.block.state.properties.IntegerProperty) crop.getStateDefinition().getProperty("age");
			int ripe = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
			Showcase.check(io.github.jcondedata.aliveworkplace.farm.BeanFarms.ENABLED
				&& !io.github.jcondedata.aliveworkplace.farm.BeanFarms.wanted(level).isEmpty(),
				"the menu's dishes want beans (Cobblecuisine, cuisineDishes, beanFarms)");
			BlockPos water = new BlockPos(3, -61, 2);
			java.util.List<BlockPos> green = new java.util.ArrayList<>();
			java.util.List<BlockPos> spots = new java.util.ArrayList<>();
			for (int x = 1; x <= 5; x++) {
				for (int z = 1; z <= 3; z++) {
					BlockPos ground = new BlockPos(x, -61, z);
					if (ground.equals(water)) {
						level.setBlockAndUpdate(ground, Blocks.WATER.defaultBlockState());
						continue;
					}
					level.setBlockAndUpdate(ground, Blocks.FARMLAND.defaultBlockState());
					spots.add(ground.above());
					if (z == 1) {
						level.setBlockAndUpdate(ground.above(), crop.defaultBlockState().setValue(age, ripe));
					} else if (z == 2) {
						level.setBlockAndUpdate(ground.above(), crop.defaultBlockState().setValue(age, 2));
						green.add(ground.above());
					}
				}
			}
			Villager farmer = worker(level, STATION, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
			io.github.jcondedata.aliveworkplace.farm.Fields.start(level, farmer,
				net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(new BlockPos(1, -61, 1), new BlockPos(5, -61, 3)));
			Container c = chest(level, chestPos(), new ItemStack(cc.apply("bean_seeds"), 8));
			net.minecraft.tags.TagKey<Item> beans = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblecuisine", "beans"));
			return l -> {
				int got = 0;
				for (int i = 0; i < c.getContainerSize(); i++) {
					got += c.getItem(i).is(beans) ? c.getItem(i).getCount() : 0;
				}
				for (BlockPos p : spots) {
					if (!l.getBlockState(p).is(crop)) {
						return false;
					}
				}
				for (BlockPos p : green) {
					if (l.getBlockState(p).getValue(age) >= ripe) {
						return false;
					}
				}
				return got >= 4;
			};
		}));
		SCENES.put("partner_treats", job("the builder's Machop ate a Poké Puff from the storehouse chest as its treat of the day", 600, (level, player) -> {
			// ROADMAP 38.10 (Cobblemon and Cobblecuisine): a builder at his bench with a Machop in the Pasture Block beside
			// it, and a storehouse chest holding four Sweet Poké Puffs. When the builder's day at work is counted, the
			// Machop eats one puff from the store (hearts, more friendship): three are left, and no more that day.
			Item puff = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblecuisine", "sweet_pokepuff"));
			Showcase.check(io.github.jcondedata.aliveworkplace.craft.CuisineDishes.ENABLED && puff != Items.AIR,
				"Cobblecuisine's puffs and cuisineDishes on");
			Villager builder = worker(level, STATION, io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH,
				io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDERS_BENCH_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER);
			builder.setNoAi(true);
			BlockPos pasture = STATION.east(3);
			level.setBlockAndUpdate(pasture, ScreenshotHarness.with(ScreenshotHarness.with(ScreenshotHarness.cobblemonBlock("pasture"), "waterlogged", false),
				"part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.BOTTOM));
			level.setBlockAndUpdate(pasture.above(), ScreenshotHarness.with(ScreenshotHarness.with(ScreenshotHarness.cobblemonBlock("pasture"), "waterlogged", false),
				"part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.TOP));
			var machop = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("machop level=20", " ", "=").create();
			com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player).add(machop);
			if (level.getBlockEntity(pasture) instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity pen) {
				pen.tether(player, machop, Direction.SOUTH);
			}
			level.setBlockAndUpdate(chestPos().west(), io.github.jcondedata.aliveworkplace.registry.ModBlocks.STOREHOUSE.defaultBlockState());
			Container c = chest(level, chestPos(), new ItemStack(puff, 4));
			return l -> {
				builder.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.WORK);
				io.github.jcondedata.aliveworkplace.work.Partners.countDay(builder); // the Workers' Cup's count, once a day
				return c.countItem(puff) == 3;
			};
		}));
		SCENES.put("gem_grower", job("the gem grower picked the ripe amethyst cluster and left the budding amethyst", 2400, (level, player) -> {
			// ROADMAP 28.11 (no Cobblemon needed): a stonecutter picked with an amethyst shard; behind it a budding amethyst
			// with a full cluster on top and a small bud on its side. She finds the bed, picks the full cluster (its shards
			// into her chest) and leaves the budding block and the small bud to grow.
			BlockPos budding = STATION.offset(2, 0, -2);
			level.setBlockAndUpdate(budding, Blocks.BUDDING_AMETHYST.defaultBlockState());
			level.setBlockAndUpdate(budding.above(), Blocks.AMETHYST_CLUSTER.defaultBlockState()
				.setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, Direction.UP));
			level.setBlockAndUpdate(budding.west(), Blocks.SMALL_AMETHYST_BUD.defaultBlockState()
				.setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, Direction.WEST));
			Villager grower = picked(level, player, STATION, Blocks.STONECUTTER, Items.AMETHYST_SHARD);
			Container c = chest(level, chestPos(), new ItemStack(Items.GLASS, 2));
			return l -> !l.getBlockState(budding.above()).is(Blocks.AMETHYST_CLUSTER) && l.getBlockState(budding).is(Blocks.BUDDING_AMETHYST)
				&& c.countItem(Items.AMETHYST_SHARD) >= 4 && io.github.jcondedata.aliveworkplace.gem.GemGrowers.isGrower(grower);
		}));
		SCENES.put("jeweller", job("the jeweller made amethyst rings at the stonecutter", 2400, (level, player) -> {
			// ROADMAP 34.12: a stonecutter picked with a gold nugget; amethyst shards and copper ingots in the chest. The
			// Novice makes Amethyst Rings at the stonecutter (it rings, gold filings fly) and puts them in the chest.
			picked(level, player, STATION, Blocks.STONECUTTER, Items.GOLD_NUGGET);
			Container c = chest(level, chestPos(), new ItemStack(Items.AMETHYST_SHARD, 6), new ItemStack(Items.COPPER_INGOT, 6));
			return l -> c.countItem(io.github.jcondedata.aliveworkplace.registry.ModItems.AMETHYST_RING) >= 2;
		}));
		SCENES.put("mega_jeweller", job("the jeweller cut a Mega Stone for the village's Master from a Mega Crystal, gold and emeralds", 2400, (level, player) -> {
			// ROADMAP 38.6 (Cobblemon and Mega Showdown): a Journeyman Jeweller with a Mega Crystal, 2 gold ingots and 4
			// emeralds in the chest (and the store's rings, brooches and circlets already made), and a Master trainer of
			// the village beside the stonecutter. The Jeweller cuts the Mega Stone of a Pokémon on the Master's team.
			// 38.6a: Mega makings count only in the village store, so the chest is a Storehouse's (the Storehouse beside it).
			Villager jeweller = picked(level, player, STATION, Blocks.STONECUTTER, Items.GOLD_NUGGET);
			jeweller.setVillagerData(jeweller.getVillagerData().setLevel(3));
			jeweller.setVillagerXp(150);
			Villager master = EntityType.VILLAGER.create(level);
			if (master != null) {
				master.moveTo(STATION.getX() + 2.5, STATION.getY(), STATION.getZ() - 1.5, 200f, 0f);
				master.setVillagerData(master.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINER).setLevel(5));
				master.setNoAi(true);
				level.addFreshEntity(master);
			}
			level.setBlockAndUpdate(chestPos().west(), ModBlocks.STOREHOUSE.defaultBlockState());
			Showcase.check(io.github.jcondedata.aliveworkplace.jeweller.MegaStones.on(), "the village's Mega Stones are on (Mega Showdown, villageMegas, Jewelers)");
			Showcase.check(master != null && !io.github.jcondedata.aliveworkplace.jeweller.MegaStones.stonesOf(master).isEmpty(), "the Master's team has a Mega Stone to cut");
			net.minecraft.tags.TagKey<net.minecraft.world.item.Item> stones = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
				io.github.jcondedata.aliveworkplace.AliveWorkplace.id("mega_stones"));
			Container c = chest(level, chestPos(), new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
					net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mega_showdown", "mega_stone_crystal"))),
				new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.EMERALD, 4), new ItemStack(ModItems.AMETHYST_RING, 8),
				new ItemStack(ModItems.EMERALD_BROOCH, 8), new ItemStack(ModItems.GOLD_CIRCLET, 8));
			return l -> {
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (c.getItem(i).is(stones)) {
						return true;
					}
				}
				return false;
			};
		}));
		SCENES.put("mega_shrine", new Job("on a festival day the village trainer walked up to the Mega Shrine's pedestal and Mega Evolved their ace, and the villagers watching cheered (+3 mood)", 2800,
			new Vec3(4.5, -55.0, -17.5), new Vec3(0.5, -57.5, -5.5), (level, player) -> {
			// ROADMAP 38.7 (Mega Showdown): a finished Mega Shrine north of the station, a Village Hall south of it and the
			// village's bell; a Trainer below the dais and five villagers off work (six: a village that holds festivals).
			// B191: the festival comes the way it does in a real world: the morning of the village's festival day, its hall's
			// round plans it, and once it is on the festival's own tick holds the show (the scene used to call the show's
			// tick by hand, so the festival never ran). The trainer climbs to the pedestal, the others gather to watch.
			BlockPos hall = STATION.offset(0, 0, 6);
			level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
			level.setBlockAndUpdate(STATION.offset(5, 0, 4), Blocks.BELL.defaultBlockState());
			BlockPos origin = STATION.offset(-3, 0, -8);
			level.getStructureManager().get(StarterBlueprints.MEGA_SHRINE.id()).orElseThrow().placeInWorld(level, origin, origin,
				new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.MEGA_SHRINE.id(), new BlueprintData.Placement(level.dimension().location(),
				origin, net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
			var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall);
			long festival = io.github.jcondedata.aliveworkplace.hall.Festivals.nextDay(level, hall, entity);
			level.setDayTime((festival - 1) * 24000L + io.github.jcondedata.aliveworkplace.hall.Festivals.START - 300); // the festival day, before it starts
			level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, level.getServer()); // and the day goes on into it
			Showcase.check(io.github.jcondedata.aliveworkplace.trainer.MegaShrines.live(), "the shrine's show is on (Mega Showdown, villageMegas)");
			Villager trainer = EntityType.VILLAGER.create(level);
			trainer.moveTo(STATION.getX() + 3.5, STATION.getY(), STATION.getZ() - 11.5, 0f, 0f);
			trainer.setVillagerData(trainer.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRAINER).setLevel(5));
			level.addFreshEntity(trainer);
			List<Villager> watchers = new java.util.ArrayList<>();
			for (int i = 0; i < 5; i++) {
				Villager v = EntityType.VILLAGER.create(level);
				v.moveTo(STATION.getX() - 4.5 + 2 * i, STATION.getY(), STATION.getZ() - 13.5, 180f, 0f);
				level.addFreshEntity(v);
				watchers.add(v);
			}
			return l -> io.github.jcondedata.aliveworkplace.hall.Festivals.isOn(l, hall)
				&& watchers.stream().allMatch(v -> io.github.jcondedata.aliveworkplace.trainer.MegaShrines.mood(l, v) == io.github.jcondedata.aliveworkplace.trainer.MegaShrines.MOOD);
		}, null));
		SCENES.put("habitat_keeper", job("the habitat keeper set out a snack, slathered the log and spotted a shiny Eevee", 2400, (level, player) -> {
			// ROADMAP 28.10: Cobblemon's Pasture Block picked with a honey bottle; Poké Snacks and a honey bottle in the
			// chest; a lure spot marked behind the pasture, a Saccharine log beside it, and a shiny wild Eevee nearby. She
			// sets the snack out, slathers the log and tells the village of the Eevee in chat.
			net.minecraft.world.level.block.Block pasture = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
				io.github.jcondedata.aliveworkplace.registry.ModVillagers.PASTURE_BLOCK);
			BlockState bottom = pastureHalf(ScreenshotHarness.standing(pasture.defaultBlockState()), "bottom");
			// B72: the Pasture Block's default state is waterlogged (a player's placement clears it on dry ground): set
			// that way, both halves were water sources that flooded the ground, the lure spot with it, so no snack went out
			if (bottom.hasProperty(BlockStateProperties.WATERLOGGED)) {
				bottom = bottom.setValue(BlockStateProperties.WATERLOGGED, false);
			}
			BlockPos spot = STATION.offset(-2, 0, -3);
			BlockPos log = STATION.offset(2, 0, -2);
			level.setBlockAndUpdate(log, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "saccharine_log")).defaultBlockState());
			level.setBlockAndUpdate(log.above(), net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "saccharine_log")).defaultBlockState());
			Villager keeper = picked(level, player, STATION, bottom, Items.HONEY_BOTTLE);
			level.setBlockAndUpdate(STATION.above(), pastureHalf(bottom, "top"));
			chest(level, chestPos(), new ItemStack(cobblemonItem("poke_snack"), 2), new ItemStack(Items.HONEY_BOTTLE, 2));
			io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.addSpot(keeper, spot);
			WildPokemon.spawnStill(level, "eevee shiny=yes level=12", STATION.getX() + 4.5, STATION.getY(), STATION.getZ() - 4.5);
			return l -> {
				// B72: name which of the three is missing (and what she is doing) if the scene times out
				boolean snack = io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.snacks() != null
					&& io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.snacks().isSnackBlock(l.getBlockState(spot));
				boolean slathered = io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.isSlathered(l.getBlockState(log));
				boolean seen = !io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.sightings(keeper).isEmpty();
				io.github.jcondedata.aliveworkplace.work.WorkerStatus.Entry status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(keeper, l.getGameTime());
				timeoutWhy = "missing:" + (snack ? "" : " snack (spot holds " + l.getBlockState(spot).getBlock() + ")")
					+ (slathered ? "" : " slathered log (log is " + l.getBlockState(log).getBlock() + ")")
					+ (seen ? "" : " sighting (wild eevees near: " + l.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(STATION).inflate(24),
						e -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("cobblemon")).size() + " cobblemon entities)")
					+ "; keeper " + keeper.getVillagerData().getProfession() + (keeper.isAlive() ? "" : " dead") + " at " + keeper.blockPosition().toShortString()
					+ ", status " + (status == null ? "none" : status.line().getString())
					+ ", spots " + io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.spots(keeper);
				return snack && slathered && seen;
			};
		}));
		SCENES.put("berry_breeder", new Job("the berry breeder bred a Lum Berry from Oran and Cheri", 2400, CAMERA, TARGET, (level, player) -> {
			// ROADMAP 28.9: a composter picked with an Oran Berry; Oran, Cheri and Surprise Mulch in the chest; two rows of
			// farmland east of the composter (x 2 and 3), so the parents go in side by side; a hall to note the find.
			Villager breeder = picked(level, player, STATION, Blocks.COMPOSTER, cobblemonItem("oran_berry"));
			Container c = chest(level, chestPos(), new ItemStack(cobblemonItem("oran_berry"), 6), new ItemStack(cobblemonItem("cheri_berry"), 6),
				new ItemStack(cobblemonItem("surprise_mulch"), 6));
			place(level, new BlockPos(-4, -60, -3), ModBlocks.VILLAGE_HALL);
			// B292: watered farmland, as on a real berry farm. Dry farmland with nothing on it turns to dirt at a random
			// tick (about a minute), so a row she hadn't planted yet could dry up and the plot never filled.
			level.setBlockAndUpdate(new BlockPos(4, -61, -1), Blocks.WATER.defaultBlockState());
			for (int x = 2; x <= 3; x++) {
				for (int z = -2; z <= 0; z++) {
					level.setBlockAndUpdate(new BlockPos(x, -61, z), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
				}
			}
			io.github.jcondedata.aliveworkplace.berry.BerryBreeders.setGoal(breeder, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "lum_berry"));
			boolean[] ripened = {false};
			return l -> {
				var data = io.github.jcondedata.aliveworkplace.berry.BerryBreeders.data();
				if (data == null) {
					return false;
				}
				int planted = 0;
				StringBuilder beds = new StringBuilder();
				for (int x = 2; x <= 3; x++) {
					for (int z = -2; z <= 0; z++) {
						var plant = data.plantOf(l.getBlockState(new BlockPos(x, -60, z)));
						planted += plant != null ? 1 : 0;
						beds.append(' ').append(x).append(',').append(z).append('=')
							.append(plant != null ? plant.getPath() : l.getBlockState(new BlockPos(x, -61, z)).getBlock().getDescriptionId());
					}
				}
				// B292: on a timeout, say what the plot and the breeder were doing
				var status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(breeder, l.getGameTime());
				timeoutWhy = "planted " + planted + " of 6 (" + beds.toString().trim() + "), ripened " + ripened[0]
					+ ", status " + (status == null ? "none" : status.line().getString()) + ", chest lum " + c.countItem(cobblemonItem("lum_berry"));
				if (planted == 6 && !ripened[0]) {
					// Cobblemon's growth takes in-game days: the plot is grown on the spot, with a mutation, so the scene ends.
					ripened[0] = true;
					for (int z = -2; z <= 0; z++) {
						data.ripen(l, new BlockPos(2, -60, z), new net.minecraft.world.level.levelgen.SingleThreadedRandomSource(z) {
							@Override
							public int nextInt(int bound) {
								return 0;
							}
						});
					}
				}
				return io.github.jcondedata.aliveworkplace.berry.BerryBreeders.found(l, breeder)
					.contains(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "lum_berry"))
					&& c.countItem(cobblemonItem("lum_berry")) > 0;
			};
		}, new After("04_berry_book", (level, player) -> {
			Villager breeder = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(STATION).inflate(12),
				io.github.jcondedata.aliveworkplace.berry.BerryBreeders::isBreeder).get(0);
			breeder.setNoAi(true);
			ScreenshotHarness.hover(player, breeder.position().add(1.5, 1.2, 3), 160, 15);
			io.github.jcondedata.aliveworkplace.berry.BerryBreeders.open(player, breeder);
		}, 49, 6)));
		SCENES.put("dropbox", new Job("the porter emptied the Drop Box into the store", 2000, CAMERA, TARGET, (level, player) -> {
			place(level, STATION, ModBlocks.STOREHOUSE);
			Container store = chest(level, STATION.west(2));
			Villager porter = EntityType.VILLAGER.spawn(level, STATION.south(), MobSpawnType.COMMAND);
			// B251: no gift by the UUID roll (one villager in 240 is a Night Owl, who rests through the scene's morning)
			io.github.jcondedata.aliveworkplace.legend.Gifted.set(porter, null);
			// The porter starts once the Drop Box's screen has been filmed (the runner calls this), while it's still full.
			afterDuring = (l, p) -> io.github.jcondedata.aliveworkplace.store.Porters.employ(l, porter, STATION);
			BlockPos box = new BlockPos(3, -60, -3);
			place(level, box, ModBlocks.DROP_BOX);
			Container drop = (Container) level.getBlockEntity(box);
			drop.setItem(0, new ItemStack(Items.IRON_SWORD));
			drop.setItem(1, new ItemStack(Items.BREAD, 16));
			drop.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
			// Its screen, before the porter comes for it (from close by: a chest screen closes past 8 blocks).
			openDuring = (l, p) -> {
				ScreenshotHarness.hoverLookingAt(p, new Vec3(3.5, -58.3, 0.5), new Vec3(3.5, -60, -3));
				p.openMenu((net.minecraft.world.MenuProvider) drop);
			};
			return l -> {
				// B251: on a timeout, say what the porter was doing
				var status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(porter, l.getGameTime());
				timeoutWhy = "the porter: " + porter.getVillagerData().getProfession() + ", job site "
					+ porter.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).map(g -> g.pos().toShortString()).orElse("none")
					+ ", potential " + porter.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.POTENTIAL_JOB_SITE).map(g -> g.pos().toShortString()).orElse("none")
					+ ", doing " + porter.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("nothing")
					+ ", schedule " + net.minecraft.core.registries.BuiltInRegistries.SCHEDULE.getKey(porter.getBrain().getSchedule())
					+ ", time " + l.getDayTime() % 24000 + ", at " + porter.blockPosition().toShortString()
					+ ", status " + (status == null ? "none" : status.title().getString() + " / " + status.line().getString())
					+ ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(porter).stacks().stream().filter(s -> !s.isEmpty()).map(s -> s.getCount() + " " + s.getItem()).toList()
					+ ", carried " + n(ModAttachments.ITEMS_CARRIED, porter) + ", box empty " + drop.isEmpty() + ", store bread " + store.countItem(Items.BREAD)
					+ ", free store tickets " + l.getPoiManager().getFreeTickets(STATION);
				return drop.isEmpty() && n(ModAttachments.ITEMS_CARRIED, porter) >= 1 && store.countItem(Items.BREAD) >= 1;
			};
		}, null));
		// Caravans you can see (33.7): Thornholm's caravan leaves for Ashford, then Ashford's comes in, unloads and goes back.
		SCENES.put("caravan", new Job("Thornholm's carter led two llamas with chests and red carpets from the Storehouse toward Ashford and was gone at the village's edge; "
			+ "then Ashford's caravan came in from that edge, its llamas stood by the Storehouse while the logs were unloaded, and it walked back out", 4800,
			new Vec3(16, -48, 13), new Vec3(16, -60, -4), (level, player) -> {
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 28; // a small village: the whole walk to its edge is in the picture
			level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
				.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
			place(level, new BlockPos(4, -60, -3), ModBlocks.STOREHOUSE);
			Container ours = chest(level, new BlockPos(5, -60, -3), new ItemStack(Items.BREAD, 40));
			var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
			hall.setCustomName(Component.literal("Thornholm"));
			// Thornholm flies a red banner; Ashford has none, so its carpets take the colour its hall's position picks
			hall.setColours(new io.github.jcondedata.aliveworkplace.hall.VillageBanners.Colours(net.minecraft.world.item.DyeColor.RED,
				net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY));
			// Ashford, 180 blocks east: a hall, a Storehouse and a chest of logs, kept loaded so its caravan can load
			BlockPos ashford = STATION.east(180);
			level.setChunkForced(ashford.getX() >> 4, ashford.getZ() >> 4, true);
			level.setChunkForced(ashford.east(5).getX() >> 4, ashford.getZ() >> 4, true);
			level.setBlockAndUpdate(ashford, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
			place(level, ashford.east(4), ModBlocks.STOREHOUSE);
			chest(level, ashford.east(5), new ItemStack(Items.OAK_LOG, 48));
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(ashford)).setCustomName(Component.literal("Ashford"));
			io.github.jcondedata.aliveworkplace.hall.Caravans.Data data = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level);
			long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			List<io.github.jcondedata.aliveworkplace.hall.Caravans.Want> logs = List.of(new io.github.jcondedata.aliveworkplace.hall.Caravans.Want(Items.OAK_LOG, 32));
			data.setWants(STATION, Component.literal("Thornholm"), logs);
			data.setWants(ashford, Component.literal("Ashford"), List.of(new io.github.jcondedata.aliveworkplace.hall.Caravans.Want(Items.BREAD, 16)));
			if (!data.routesFrom(STATION).contains(ashford)) {
				data.toggleRoute(STATION, ashford);
			}
			if (!data.routesFrom(ashford).contains(STATION)) {
				data.toggleRoute(ashford, STATION);
			}
			data.clearRoad(STATION);
			data.clearRoad(ashford);
			data.sent(ashford, today); // Ashford's caravan waits for the scene to send it
			int[] act = {0, 0};
			boolean[] sent = {false};
			return l -> {
				if (!sent[0]) {
					// Today's caravan leaves Thornholm with the bread: its party stands by the Storehouse for the "Start" still.
					// B132: sent on the first look (tick 70, the world still frozen), not while staging: the game files a new
					// block's point of interest a moment after it's placed (ServerLevel.onBlockStateChange queues it), so a
					// round in the same moment found no Storehouse, packed nothing and sent no party, and the scene waited out
					// its four minutes. (A real village's Storehouse stands long before its caravan's day.)
					sent[0] = true;
					data.sent(STATION, -1);
					io.github.jcondedata.aliveworkplace.hall.Caravans.round(l, STATION, null);
				}
				io.github.jcondedata.aliveworkplace.hall.CaravanSights.Party party = io.github.jcondedata.aliveworkplace.hall.CaravanSights.party(l, STATION);
				if (act[0] == 0 && party != null && party.phase() == io.github.jcondedata.aliveworkplace.hall.CaravanSights.Phase.LEAVING) {
					act[0] = 1; // seen setting out
				} else if (act[0] == 1 && party == null) {
					// gone at the edge: now Ashford's caravan brings the logs Thornholm waits for (its hall's own round forgot the wait)
					data.setWants(STATION, Component.literal("Thornholm"), logs);
					data.sent(ashford, -1);
					io.github.jcondedata.aliveworkplace.hall.Caravans.round(l, ashford, null);
					data.hurry(STATION);
					io.github.jcondedata.aliveworkplace.hall.Caravans.round(l, STATION, null);
					act[0] = 2;
				} else if (act[0] == 2 && party != null && party.phase() == io.github.jcondedata.aliveworkplace.hall.CaravanSights.Phase.UNLOADING
					&& party.carter().getCustomName() != null && party.carter().getCustomName().getString().equals("Ashford's caravan")) {
					act[0] = 3; // standing by the Storehouse, unloading
				} else if (act[0] == 3 && party == null) {
					act[0] = 4; // and gone again
				}
				if (act[0] != act[1]) {
					act[1] = act[0]; // B132: which part of the trip a timeout stopped at, in the scene's log
					System.out.println("[showcase] caravan act " + act[0] + " at game tick " + l.getGameTime());
				}
				return act[0] == 4 && ours.countItem(Items.OAK_LOG) == 32 && ours.countItem(Items.BREAD) == 24;
			};
		}, null));
		// Caravans travel by waystone (41.3, filmed with Waystones): Thornholm and Ashford, 180 blocks apart, each with its
		// Waystone Plaza. Thornholm's caravan walks from the Storehouse to its stone and steps in (the mod's portal sound and
		// particles); then Ashford's steps out of that stone, unloads at the Storehouse and walks back into it.
		SCENES.put("waystone_caravan", new Job("Thornholm's carter led his two llamas from the Storehouse to the village's waystone and stepped into it; "
			+ "then Ashford's caravan stepped out of the same stone, unloaded its logs at the Storehouse and walked back into the waystone", 4800,
			new Vec3(16, -48, 13), new Vec3(12, -60, -5), (level, player) -> {
			level.setDayTime(2000);
			Showcase.check(io.github.jcondedata.aliveworkplace.hall.VillageWaystones.offered(), "Waystones is loaded and village waystones are on");
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 28; // a small village, all of it in the picture
			waystoneVillage(level, player, STATION, "Thornholm", STATION.offset(12, 0, -8));
			place(level, new BlockPos(4, -60, -3), ModBlocks.STOREHOUSE);
			Container ours = chest(level, new BlockPos(5, -60, -3), new ItemStack(Items.BREAD, 40));
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION)).setColours(
				new io.github.jcondedata.aliveworkplace.hall.VillageBanners.Colours(net.minecraft.world.item.DyeColor.RED,
					net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY));
			// Ashford, 180 blocks east, kept loaded: a hall, its plaza, a Storehouse and a chest of logs
			BlockPos ashford = STATION.east(180);
			level.setChunkForced(ashford.getX() >> 4, ashford.getZ() >> 4, true);
			waystoneVillage(level, player, ashford, "Ashford", ashford.offset(2, 0, 4));
			place(level, ashford.east(4), ModBlocks.STOREHOUSE);
			chest(level, ashford.east(5), new ItemStack(Items.OAK_LOG, 48));
			Showcase.check(io.github.jcondedata.aliveworkplace.hall.VillageWaystones.linked(level, STATION, ashford),
				"both villages' waystones stand (" + io.github.jcondedata.aliveworkplace.hall.VillageWaystones.stoneOf(level, STATION) + ", "
					+ io.github.jcondedata.aliveworkplace.hall.VillageWaystones.stoneOf(level, ashford) + ")");
			io.github.jcondedata.aliveworkplace.hall.Caravans.Data data = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level);
			long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			List<io.github.jcondedata.aliveworkplace.hall.Caravans.Want> logs = List.of(new io.github.jcondedata.aliveworkplace.hall.Caravans.Want(Items.OAK_LOG, 32));
			data.setWants(STATION, Component.literal("Thornholm"), logs);
			data.setWants(ashford, Component.literal("Ashford"), List.of(new io.github.jcondedata.aliveworkplace.hall.Caravans.Want(Items.BREAD, 16)));
			if (!data.routesFrom(STATION).contains(ashford)) {
				data.toggleRoute(STATION, ashford);
			}
			if (!data.routesFrom(ashford).contains(STATION)) {
				data.toggleRoute(ashford, STATION);
			}
			data.clearRoad(STATION);
			data.clearRoad(ashford);
			data.sent(ashford, today); // Ashford's caravan waits for the scene to send it
			int[] act = {0, 0};
			boolean[] sent = {false};
			return l -> {
				if (!sent[0]) {
					sent[0] = true; // (on the first look, once the Storehouse's point of interest is filed: B132)
					data.sent(STATION, -1);
					io.github.jcondedata.aliveworkplace.hall.Caravans.round(l, STATION, null);
					Showcase.check(data.onTheRoad().stream().anyMatch(s -> s.from().equals(STATION) && s.byWaystone()), "Thornholm's caravan goes by waystone");
				}
				io.github.jcondedata.aliveworkplace.hall.CaravanSights.Party party = io.github.jcondedata.aliveworkplace.hall.CaravanSights.party(l, STATION);
				if (act[0] == 0 && party != null && party.phase() == io.github.jcondedata.aliveworkplace.hall.CaravanSights.Phase.LEAVING) {
					Showcase.check(party.waystone(), "the party walks to the waystone, not the village's edge (" + party.edge() + ")");
					act[0] = 1;
				} else if (act[0] == 1 && party == null) {
					// stepped into the stone: now Ashford's caravan brings the logs, out of the same stone
					data.setWants(STATION, Component.literal("Thornholm"), logs);
					data.sent(ashford, -1);
					io.github.jcondedata.aliveworkplace.hall.Caravans.round(l, ashford, null);
					Showcase.check(data.onTheRoad().stream().anyMatch(s -> s.from().equals(ashford) && s.byWaystone()), "Ashford's caravan goes by waystone");
					data.hurry(STATION);
					io.github.jcondedata.aliveworkplace.hall.Caravans.round(l, STATION, null);
					act[0] = 2;
				} else if (act[0] == 2 && party != null && party.phase() == io.github.jcondedata.aliveworkplace.hall.CaravanSights.Phase.UNLOADING
					&& party.carter().getCustomName() != null && party.carter().getCustomName().getString().equals("Ashford's caravan")) {
					Showcase.check(party.waystone(), "Ashford's caravan came out of the waystone");
					act[0] = 3;
				} else if (act[0] == 3 && party == null) {
					act[0] = 4; // back into the stone
				}
				if (act[0] != act[1]) {
					act[1] = act[0];
					System.out.println("[showcase] waystone_caravan act " + act[0] + " at game tick " + l.getGameTime());
				}
				return act[0] == 4 && ours.countItem(Items.OAK_LOG) == 32 && ours.countItem(Items.BREAD) == 24;
			};
		}, null));
	}

	// --- Screens ---------------------------------------------------------------------------------------------------

	private static Step step(String shot, int slot, int rows) {
		return new Step(shot, slot, rows, null, 25);
	}

	/** A step of the stations scene: the player sneak-right-clicks the villager with {@code item}. */
	private static Step hand(String shot, Item item) {
		return new Step(shot, -1, 0, (level, player) -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
			io.github.jcondedata.aliveworkplace.work.Stations.choose(player, subject, player.getMainHandItem());
		}, 40);
	}

	static {
		// Fewer job blocks (ROADMAP 21.1a): one composter, and the item in hand picks the job; wheat brings the farmer back.
		// Every profession's outfit (ROADMAP 21.1): the mod's jobs as villagers, then as zombie villagers, five to a shot,
		// each named over its head.
		SCREENS.put("outfits", new Screen("every profession of ours shows its outfit, as a villager and as a zombie villager",
			outfitCamera(false), outfitTarget(false), (level, player) -> {
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
				level.setDayTime(6000);
				OUTFITS_SEEN.set(0);
				for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(outfitSpot(0, true)).offset(-3, 4, -3),
						BlockPos.containing(outfitSpot(4, true)).offset(3, 4, 3))) {
					level.setBlockAndUpdate(p, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				}
				player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR); // no hand, no hotbar, from the start
			},
			outfitSteps(),
			(level, player) -> {
				outfitsDone(level);
				return !ourProfessions().isEmpty() && OUTFITS_SEEN.get() == 2 * ourProfessions().size();
			}));
		// The item icons (ROADMAP 21.1b, the owner's picks): all 13 in a chest at GUI scale 2, then four held in hand.
		SCREENS.put("items", new Screen("every item's icon shows in a chest, in the hotbar and in hand", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				List<Item> items = itemIcons();
				for (int i = 0; i < 9; i++) {
					player.getInventory().setItem(i, new ItemStack(items.get(i)));
				}
				chest(level, STATION, items.stream().map(ItemStack::new).toArray(ItemStack[]::new));
				player.openMenu((net.minecraft.world.MenuProvider) level.getBlockEntity(STATION));
			},
			List.of(new Step("01_items_chest", -1, 3, null, 25), new Step("02_items_tooltip", 4, 3, null, 25),
				held("03_hand_blueprint", ModItems.BLUEPRINT), held("04_hand_rally_banner", ModItems.RALLY_BANNER),
				held("05_hand_scan_tool", ModItems.SCAN_TOOL), held("06_hand_quarry_marker", ModItems.QUARRY_MARKER),
				held("07_hand_field_marker", ModItems.FIELD_MARKER)),
			(level, player) -> player.getMainHandItem().is(ModItems.FIELD_MARKER)
				&& level.getBlockEntity(STATION) instanceof Container c && itemIcons().stream().allMatch(i -> c.countItem(i) == 1)));
		SCREENS.put("stations", new Screen("the villager took each job its item picks at the composter", new Vec3(2.2, -58.6, 3.6), TARGET,
			(level, player) -> {
				place(level, STATION, Blocks.COMPOSTER);
				subject = EntityType.VILLAGER.spawn(level, STATION.south(), MobSpawnType.COMMAND);
				subject.setNoAi(true); // stands still for the camera (their job still shows on their clothes)
				subject.setYRot(160);
				subject.setYHeadRot(160);
			},
			List.of(new Step("01_jobless", -1, 0, null, 25), hand("02_orchard_keeper", Items.SWEET_BERRIES), hand("03_florist", Items.POPPY),
				hand("04_composter", Items.BONE_MEAL), hand("05_farmer", Items.WHEAT)),
			(level, player) -> subject.getVillagerData().getProfession() == VillagerProfession.FARMER
				&& subject.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
					.map(g -> g.pos().equals(STATION)).orElse(false)));
		// Retraining (ROADMAP 26.6): a Journeyman Librarian who works at a lectern, by a Blueprint Table. The first
		// sneak-right-click with a Blank Blueprint shows the price in chat; the second pays, and she's a Novice Builder.
		SCREENS.put("retrain", new Screen("the Journeyman Librarian was shown the price, then paid for and retrained as a Novice Builder at the Blueprint Table",
			new Vec3(2.2, -58.6, 3.6), TARGET,
			(level, player) -> {
				place(level, STATION, io.github.jcondedata.aliveworkplace.registry.ModBlocks.BLUEPRINT_TABLE);
				BlockPos lectern = STATION.east(3);
				place(level, lectern, Blocks.LECTERN);
				subject = EntityType.VILLAGER.spawn(level, STATION.south(), MobSpawnType.COMMAND);
				subject.setNoAi(true); // stands still for the camera (their job still shows on their clothes)
				subject.setYRot(160);
				subject.setYHeadRot(160);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, subject, lectern, net.minecraft.world.entity.ai.village.poi.PoiTypes.LIBRARIAN,
					VillagerProfession.LIBRARIAN);
				subject.setVillagerData(subject.getVillagerData().setLevel(3));
				subject.setVillagerXp(150);
				subject.setCustomName(net.minecraft.network.chat.Component.literal("Dara"));
				subject.getOffers(); // she has her trades
				player.getInventory().setItem(8, new ItemStack(Items.EMERALD, 32));
			},
			List.of(new Step("01_librarian", -1, 0, null, 25), hand("02_offer", io.github.jcondedata.aliveworkplace.registry.ModItems.BLANK_BLUEPRINT),
				hand("03_builder", io.github.jcondedata.aliveworkplace.registry.ModItems.BLANK_BLUEPRINT)),
			(level, player) -> subject.getVillagerData().getProfession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER
				&& subject.getVillagerData().getLevel() == 1
				&& subject.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
					.map(g -> g.pos().equals(STATION)).orElse(false)));
		SCREENS.put("daycare", new Screen("the daycare screen opened with a Charmander boarding", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				subject = picked(level, player, STATION, Blocks.SMOKER, Items.SADDLE);
				subject.setNoAi(true);
				chest(level, chestPos());
				party(player, "bulbasaur level=10", "charmander level=5");
				var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
				io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare.leave(player, subject, party.get(1));
				var b = io.github.jcondedata.aliveworkplace.ranch.Daycare.boarders(subject).get(0);
				io.github.jcondedata.aliveworkplace.ranch.Daycare.setBoarders(subject, List.of(new io.github.jcondedata.aliveworkplace.ranch.Daycare.Boarder(
					b.owner(), b.ownerName(), b.pokemon(), b.since() - 3 * 24000)));
				io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(player, 5000);
				io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare.open(player, subject);
			},
			List.of(step("01_daycare_screen", io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare.INFO, 6),
				step("02_daycare_boarder", io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare.FIRST_BOARDER_SLOT, 6)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu && io.github.jcondedata.aliveworkplace.ranch.Daycare.boarders(subject).size() == 1));
		SCREENS.put("daycare_keeper", new Screen("the daycare keeper took a pair of Eevee and two eggs were collected", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				// ROADMAP 28.12: Cobblemon's Pasture Block picked with an egg; two Eevee (mother and father) and a Bulbasaur
				// in the party. The screen: pick the mother, then the father; two dawns later, collect the eggs.
				net.minecraft.world.level.block.Block pasture = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.PASTURE_BLOCK);
				BlockState bottom = pastureHalf(ScreenshotHarness.standing(pasture.defaultBlockState()), "bottom");
				subject = picked(level, player, STATION, bottom, Items.EGG);
				level.setBlockAndUpdate(STATION.above(), pastureHalf(bottom, "top"));
				subject.setNoAi(true);
				party(player, "bulbasaur level=10", "eevee gender=female level=20", "eevee gender=male level=20");
				io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.add(player, 5000);
				io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.open(player, subject);
			},
			List.of(step("01_daycare_keeper_screen", io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 1, 6),
				new Step("02_daycare_keeper_picked", io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 2, 6,
					(level, player) -> {
						if (player.containerMenu instanceof ChoiceMenu m) {
							m.press(io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 1, player);
						}
					}, 25),
				new Step("03_daycare_keeper_pair", io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper.GET_ALONG, 6,
					(level, player) -> {
						if (player.containerMenu instanceof ChoiceMenu m) {
							m.press(io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 2, player);
						}
					}, 25),
				new Step("04_daycare_keeper_eggs", io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper.EGGS, 6,
					(level, player) -> {
						// Two dawns found two eggs
						var pairs = io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.pairs(subject);
						if (!pairs.isEmpty()) {
							io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.setPairs(subject, List.of(pairs.get(0).withEggs(2, pairs.get(0).day())));
						}
						io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.open(player, subject);
					}, 25),
				new Step("05_daycare_keeper_collected", -1, 6,
					(level, player) -> {
						if (player.containerMenu instanceof ChoiceMenu m) {
							m.press(io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper.EGGS, player);
						}
					}, 25)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu && io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.pairs(subject).size() == 1
				&& io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.pairs(subject).get(0).eggs() == 0));
		SCREENS.put("smith_orders", new Screen("the orders screen opened and a Poké Ball was picked", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				subject = picked(level, player, STATION, Blocks.SMITHING_TABLE, cobblemonItem("red_apricorn"));
				subject.setNoAi(true);
				chest(level, chestPos(), new ItemStack(cobblemonItem("red_apricorn"), 16), new ItemStack(Items.COPPER_INGOT, 8));
				io.github.jcondedata.aliveworkplace.smith.BallSmiths.openOrders(player, subject);
			},
			List.of(step("01_orders_screen", 4, 6),
				new Step("02_orders_picked", -2, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						for (int s = io.github.jcondedata.aliveworkplace.smith.BallSmiths.FIRST_BALL_SLOT; s < ChoiceMenu.SIZE; s++) {
							if (m.icon(s).is(cobblemonItem("poke_ball"))) {
								m.press(s, player);
								pickedSlot = s;
								break;
							}
						}
					}
				}, 25)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu && !io.github.jcondedata.aliveworkplace.smith.BallSmiths.orders(subject).isEmpty()));
		SCREENS.put("ferry_menu", new Screen("the ferryman's ticket screen opened with the Far Shore's fare", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				subject = worker(level, STATION, ModBlocks.TRAVEL_POST, ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);
				subject.setNoAi(true);
				var network = io.github.jcondedata.aliveworkplace.travel.TravelNetwork.get(level.getServer());
				network.add(net.minecraft.core.GlobalPos.of(level.dimension(), STATION), "Harbor");
				var far = network.add(net.minecraft.core.GlobalPos.of(level.dimension(), new BlockPos(0, -60, 600)), "Far Shore");
				network.visit(player.getUUID(), far);
				player.getInventory().add(new ItemStack(Items.EMERALD, 20));
				io.github.jcondedata.aliveworkplace.travel.Ferrymen.openMenu(player, subject);
			},
			List.of(step("01_ferry_screen", io.github.jcondedata.aliveworkplace.travel.Ferrymen.FIRST_DESTINATION_SLOT, 6),
				new Step("02_ferry_confirm", io.github.jcondedata.aliveworkplace.travel.Ferrymen.FIRST_DESTINATION_SLOT, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.travel.Ferrymen.FIRST_DESTINATION_SLOT, player);
					}
				}, 25)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m
				&& m.icon(io.github.jcondedata.aliveworkplace.travel.Ferrymen.FIRST_DESTINATION_SLOT).is(ModItems.TRAVEL_TICKET)));
		SCREENS.put("shapes", new Screen("the Shape Planner opened with stone bricks to build in", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				player.getInventory().setItem(1, new ItemStack(Items.STONE_BRICKS, 64));
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
				io.github.jcondedata.aliveworkplace.blueprint.Shapes.open(player, InteractionHand.MAIN_HAND);
			},
			List.of(step("01_shapes_screen", io.github.jcondedata.aliveworkplace.blueprint.Shapes.FIRST_KIND + 1, 6),
				step("02_shapes_info", io.github.jcondedata.aliveworkplace.blueprint.Shapes.INFO, 6)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m
				&& m.icon(io.github.jcondedata.aliveworkplace.blueprint.Shapes.FIRST_MATERIAL).is(Items.STONE_BRICKS)));
		SCREENS.put("style_menu", new Screen("the style picker opened, dark oak was chosen and the build mirrored", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.STONE_HOUSE.id(), StarterBlueprints.STONE_HOUSE.size()));
				io.github.jcondedata.aliveworkplace.blueprint.StylePicker.open(player, InteractionHand.MAIN_HAND);
			},
			List.of(step("01_style_screen", io.github.jcondedata.aliveworkplace.blueprint.StylePicker.slot(0), 6),
				new Step("02_style_chosen", io.github.jcondedata.aliveworkplace.blueprint.StylePicker.slot(3), 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.blueprint.StylePicker.slot(3), player);
					}
				}, 25),
				// The Mirror button flips the build left to right (ROADMAP 22.3).
				new Step("03_style_mirrored", io.github.jcondedata.aliveworkplace.blueprint.StylePicker.MIRROR, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.blueprint.StylePicker.MIRROR, player);
					}
				}, 25)),
			(level, player) -> BlueprintItem.data(player.getMainHandItem()).map(d -> d.mirrored() && d.structure().equals(
				io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE.id(), "dark_oak"))).orElse(false)));
		// The Scan Tool (ROADMAP 22.3): two corners of a little hut marked (the purple box shows while it's held), then
		// sneak-use saves it as a blueprint.
		SCREENS.put("scan", new Screen("a hut was marked with the Scan Tool and saved as a blueprint", new Vec3(6.5, -56.2, 5.5), new Vec3(0, -58.5, -4),
			(level, player) -> {
				for (BlockPos p : BlockPos.betweenClosed(-2, -60, -6, 2, -57, -2)) {
					boolean wall = p.getX() == -2 || p.getX() == 2 || p.getZ() == -6 || p.getZ() == -2;
					level.setBlockAndUpdate(p, wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
				for (BlockPos p : BlockPos.betweenClosed(-2, -56, -6, 2, -56, -2)) {
					level.setBlockAndUpdate(p, Blocks.SPRUCE_SLAB.defaultBlockState());
				}
				level.setBlockAndUpdate(new BlockPos(0, -60, -2), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(0, -59, -2), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(2, -59, -4), Blocks.GLASS_PANE.defaultBlockState());
				ItemStack tool = new ItemStack(ModItems.SCAN_TOOL);
				tool.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Little Hut"));
				player.setItemInHand(InteractionHand.MAIN_HAND, tool);
				for (BlockPos corner : List.of(new BlockPos(-2, -60, -6), new BlockPos(2, -56, -2))) {
					tool.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
						new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(corner), Direction.UP, corner, false)));
				}
			},
			List.of(new Step("01_scan_marked", -1, 6, null, 40),
				new Step("02_scan_saved", -1, 6, (level, player) -> {
					var data = io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem.data(player.getMainHandItem());
					Showcase.check(data.isComplete(), "both corners are marked");
					player.setShiftKeyDown(true);
					player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
					player.setShiftKeyDown(false);
				}, 30)),
			(level, player) -> player.getInventory().items.stream().anyMatch(stack -> BlueprintItem.data(stack)
				.map(d -> d.structure().getPath().startsWith("scans/")).orElse(false))));
		SCREENS.put("counter", new Screen("the Price Tag's screen opened", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				place(level, STATION, ModBlocks.SHOP_COUNTER);
				chest(level, STATION.east(2), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.BREAD, 24));
				var counter = (io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity) level.getBlockEntity(STATION);
				counter.setOwner(player.getUUID(), "Jesse");
				int columns = io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity.COLUMNS;
				counter.setItem(0, new ItemStack(Items.OAK_LOG, 16));
				counter.setItem(columns, new ItemStack(Items.EMERALD, 2));
				counter.setItem(1, new ItemStack(Items.BREAD, 6));
				ItemStack tag = new ItemStack(ModItems.PRICE_TAG);
				io.github.jcondedata.aliveworkplace.shop.PriceTagItem.setPrice(tag, 250);
				counter.setItem(columns + 1, tag);
				// Two earlier sales for the owner's log (ROADMAP 22.3).
				long day = level.getDayTime() / 24000L;
				counter.logSale(new io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity.Sale(day, "Ana", new ItemStack(Items.BREAD, 6), 0,
					new ItemStack(Items.EMERALD, 1)));
				counter.logSale(new io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity.Sale(day, "Clover", new ItemStack(Items.OAK_LOG, 16), 0,
					new ItemStack(Items.EMERALD, 2)));
				player.openMenu(counter);
			},
			List.of(step("01_counter_screen", 0, 2),
				step("02_counter_price", io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity.COLUMNS + 1, 2),
				// The owner's sales log in chat (sneak-right-click on the counter).
				new Step("03_counter_sales", -1, 6, (level, player) -> {
					Showcase.check(player.containerMenu instanceof ChestMenu m && m.getRowCount() == 2, "the Shop Counter's price list opened");
					player.closeContainer();
					var counter = (io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity) level.getBlockEntity(STATION);
					Showcase.check(counter.sales().size() == 2, "the owner's sales log lists both sales");
					io.github.jcondedata.aliveworkplace.shop.Shops.sendLog(player, counter);
				}, 30),
				new Step("04_price_tag", 22, 6, (level, player) -> {
					player.closeContainer();
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG));
					ModItems.PRICE_TAG.use(level, player, InteractionHand.MAIN_HAND);
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		// The hall's sidebar (ROADMAP 40.1): each of the ten sections opened from the sidebar, at GUI scale 2, then at 3.
		SCREENS.put("hall_sidebar", new Screen("the Village Hall's sidebar opened each of its ten sections at GUI scales 2 and 3", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				guard(level, new BlockPos(4, -60, -3), new ItemStack(Items.IRON_SWORD));
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				io.github.jcondedata.aliveworkplace.hall.HallSections.forget();
			},
			java.util.stream.IntStream.range(0, 2 * io.github.jcondedata.aliveworkplace.hall.HallSections.Section.values().length).mapToObj(i -> {
				var sections = io.github.jcondedata.aliveworkplace.hall.HallSections.Section.values();
				var section = sections[i % sections.length];
				int scale = i < sections.length ? 2 : 3;
				return new Step(String.format("%02d_%s_scale%d", i + 1, section.id(), scale), -1, 6, (level, player) -> {
					if (i % sections.length == 0) {
						Minecraft mc = Minecraft.getInstance();
						mc.execute(() -> {
							mc.options.guiScale().set(scale);
							mc.resizeDisplay();
						});
					}
					if (!(player.containerMenu instanceof ChoiceMenu)) {
						io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					}
					if (player.containerMenu instanceof ChoiceMenu m) {
						io.github.jcondedata.aliveworkplace.hall.HallSections.choose(m, level, STATION, player, section); // the sidebar's click
						Showcase.check(io.github.jcondedata.aliveworkplace.hall.HallSections.current(m) == section
							&& !io.github.jcondedata.aliveworkplace.hall.HallSections.slots(m, section).isEmpty(), section.id() + " opened with its buttons");
					}
				}, 25);
			}).toList(),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		SCREENS.put("hall_people", new Screen("the Village Hall's People table, then one villager's row opened", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				String[] names = {"Dara", "Bram", "Wren", "Tomas", "Ilse", "Pip", "Jem"};
				List<Villager> folk = new java.util.ArrayList<>();
				folk.add(worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER));
				folk.add(guard(level, new BlockPos(4, -60, -3), new ItemStack(Items.IRON_SWORD)));
				folk.add(worker(level, new BlockPos(-6, -60, 2), Blocks.STONECUTTER, PoiTypes.MASON, VillagerProfession.MASON));
				folk.add(worker(level, new BlockPos(6, -60, 2), Blocks.LECTERN, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN));
				Villager jobless = EntityType.VILLAGER.spawn(level, new BlockPos(-2, -60, 5), MobSpawnType.COMMAND);
				folk.add(jobless);
				Villager nitwit = EntityType.VILLAGER.spawn(level, new BlockPos(2, -60, 5), MobSpawnType.COMMAND);
				nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
				folk.add(nitwit);
				Villager child = EntityType.VILLAGER.spawn(level, new BlockPos(0, -60, 6), MobSpawnType.COMMAND);
				child.setAge(-24000);
				folk.add(child);
				for (int i = 0; i < folk.size(); i++) {
					folk.get(i).setCustomName(Component.literal(names[i]));
				}
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				io.github.jcondedata.aliveworkplace.hall.HallSections.forget();
			},
			List.of(new Step("01_people_table", -1, 6, (level, player) -> {
					if (!(player.containerMenu instanceof ChoiceMenu)) {
						io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					}
					if (player.containerMenu instanceof ChoiceMenu m) {
						io.github.jcondedata.aliveworkplace.hall.HallSections.choose(m, level, STATION, player,
							io.github.jcondedata.aliveworkplace.hall.HallSections.Section.PEOPLE); // the sidebar's click
						Showcase.check(m.state(io.github.jcondedata.aliveworkplace.hall.HallSections.PEOPLE_TOTAL) >= 7, "the table lists the village's seven");
					}
				}, 30),
				new Step("02_people_row_open", -1, 6, (level, player) -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> {
						if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen s) {
							s.openRow(0); // a click on the first row: their details open right under it
						}
					});
				}, 40),
				new Step("03_people_story", -1, 6, (level, player) -> {
					// Story on the open row: their life story opens, as shift-click did in the old list (B547)
					onHallScreen(s -> s.pressStory(), 40);
				}, 40)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		SCREENS.put("hall_people_job", new Screen("the People table's Job list: every job, the free ones with how many, the price, then a librarian made a Mason",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				Villager dara = worker(level, new BlockPos(6, -60, 2), Blocks.LECTERN, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
				dara.setVillagerData(dara.getVillagerData().setLevel(3));
				dara.setVillagerXp(210);
				dara.setCustomName(Component.literal("Dara"));
				Villager bram = worker(level, new BlockPos(-6, -60, 2), Blocks.FLETCHING_TABLE, PoiTypes.FLETCHER, VillagerProfession.FLETCHER);
				bram.setCustomName(Component.literal("Bram"));
				Villager wren = EntityType.VILLAGER.spawn(level, new BlockPos(-2, -60, 5), MobSpawnType.COMMAND);
				wren.setCustomName(Component.literal("Wren"));
				place(level, new BlockPos(-4, -60, -3), Blocks.STONECUTTER); // two free stonecutters and a free Blueprint Table
				place(level, new BlockPos(4, -60, -3), Blocks.STONECUTTER);
				place(level, new BlockPos(0, -60, -4), ModBlocks.BLUEPRINT_TABLE);
				player.getInventory().add(new ItemStack(Items.EMERALD, 32));
				io.github.jcondedata.aliveworkplace.hall.HallSections.forget();
				io.github.jcondedata.aliveworkplace.hall.HallJobs.forget();
			},
			List.of(new Step("01_job_list", -1, 6, (level, player) -> {
					if (!(player.containerMenu instanceof ChoiceMenu)) {
						io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					}
					if (player.containerMenu instanceof ChoiceMenu m) {
						io.github.jcondedata.aliveworkplace.hall.HallSections.choose(m, level, STATION, player,
							io.github.jcondedata.aliveworkplace.hall.HallSections.Section.PEOPLE); // the sidebar's click
					}
					// Job ▾ on Dara's row: the list of every job opens under it (once the screen and its rows have arrived)
					onHallScreen(s -> s.openRowNamed("Dara") && s.pressJob(), 40);
					scrollHallLater();
				}, 40),
				new Step("02_job_picked", -1, 6, (level, player) -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> {
						if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen s) {
							s.pickJob(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getId(VillagerProfession.MASON)); // once: the price
						}
					});
					scrollHallLater();
				}, 40),
				new Step("03_job_changed", -1, 6, (level, player) -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> {
						if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen s) {
							s.pickJob(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getId(VillagerProfession.MASON)); // again: paid
						}
					});
					scrollHallLater();
				}, 40),
				new Step("04_job_after", -1, 6, (level, player) -> {
					boolean mason = !level.getEntities(EntityType.VILLAGER, v -> v.hasCustomName() && v.getCustomName().getString().equals("Dara")
						&& v.getVillagerData().getProfession() == VillagerProfession.MASON).isEmpty();
					Showcase.check(mason, "Dara the librarian was made a Mason from the People table's Job list");
				}, 20)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		SCREENS.put("hall_people_bed", new Screen("the People table's Bed list: the village's free beds with where they are, a swap, then a bed given",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				for (BlockPos foot : List.of(new BlockPos(-8, -60, -6), new BlockPos(9, -60, -8), new BlockPos(-10, -60, 6))) {
					level.setBlockAndUpdate(foot, Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
						.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
					level.setBlockAndUpdate(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
						.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
				}
				Villager dara = EntityType.VILLAGER.spawn(level, new BlockPos(5, -60, 3), MobSpawnType.COMMAND);
				dara.setCustomName(Component.literal("Dara"));
				dara.setNoAi(true);
				Villager jem = EntityType.VILLAGER.spawn(level, new BlockPos(-5, -60, 3), MobSpawnType.COMMAND);
				jem.setCustomName(Component.literal("Jem"));
				jem.setNoAi(true);
				io.github.jcondedata.aliveworkplace.hall.HallSections.forget();
				io.github.jcondedata.aliveworkplace.hall.HallBeds.forget();
			},
			List.of(new Step("01_bed_list", -1, 6, (level, player) -> {
					// Jem sleeps in the bed to the south-west (the beds' records are filed by now, a step after staging).
					level.getEntities(EntityType.VILLAGER, v -> v.hasCustomName() && v.getCustomName().getString().equals("Jem")).forEach(jem ->
						io.github.jcondedata.aliveworkplace.camp.CampBeds.claim(level, jem, new BlockPos(-10, -60, 7)));
					if (!(player.containerMenu instanceof ChoiceMenu)) {
						io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					}
					if (player.containerMenu instanceof ChoiceMenu m) {
						io.github.jcondedata.aliveworkplace.hall.HallSections.choose(m, level, STATION, player,
							io.github.jcondedata.aliveworkplace.hall.HallSections.Section.PEOPLE); // the sidebar's click
					}
					// Bed ▾ on Dara's row: the village's beds open under it (once the screen and its rows have arrived)
					onHallScreen(s -> s.openRowNamed("Dara") && s.pressBed(), 40);
					scrollHallLater();
				}, 40),
				new Step("02_bed_given", -1, 6, (level, player) -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> {
						if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen s) {
							s.pickBed(0); // the nearest free bed: hers from now on
						}
					});
					scrollHallLater();
				}, 40),
				new Step("03_bed_after", -1, 6, (level, player) -> {
					boolean housed = !level.getEntities(EntityType.VILLAGER, v -> v.hasCustomName() && v.getCustomName().getString().equals("Dara")
						&& io.github.jcondedata.aliveworkplace.hall.VillageNeeds.bed(level, v) != null).isEmpty();
					Showcase.check(housed, "Dara was given a free bed from the People table's Bed list");
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> {
						if (mc.screen instanceof io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen s) {
							s.pressBed(); // open again: hers on top as "their bed now", Jem's to swap
						}
					});
					scrollHallLater();
				}, 40)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		SCREENS.put("hall_pages", new Screen("the Village Hall's chronicle and trade-route pages opened", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				guard(level, new BlockPos(4, -60, -3), new ItemStack(Items.IRON_SWORD));
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
			},
			List.of(new Step("01_hall_chronicle", -1, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.CHRONICLE, player);
					}
				}, 30),
				new Step("02_hall_routes", -1, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.ROUTES, player);
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU)); // the hall's own screen (30.4a)
		// Free job blocks (ROADMAP 36.9): the hall's page lists a Storehouse nobody works at in a finished Storehouse and a
		// bench in the open, with the distance and who could take each; the Storehouse's page lists Dara and Odo, and picking
		// Dara makes her the Porter, who walks over.
		SCREENS.put("hall_jobs", new Screen("the hall's Free job blocks page listed the empty Storehouse (in the Storehouse, its distance, two villagers who could take it) and a bench; "
			+ "Dara was picked from the Storehouse's page and became the Porter",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				BlockPos store = new BlockPos(-4, -60, -3);
				place(level, store, ModBlocks.STOREHOUSE);
				place(level, new BlockPos(4, -60, -3), ModBlocks.BUILDERS_BENCH);
				io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.STOREHOUSE.id(),
					new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), new BlockPos(-12, -60, -7),
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
				classPeople.clear();
				String[] names = {"Dara", "Odo"};
				for (int i = 0; i < names.length; i++) {
					Villager v = EntityType.VILLAGER.create(level);
					// The page lists who could take a block nearest the hall first: Dara stands nearer, so she's first and the
					// pointer, resting on the first row, picks her.
					v.moveTo(1.5 - i * 4, -60, 3.5, 180, 0);
					v.setNoAi(true); // jobless villagers would take a free block on their own before the camera sees it
					v.setCustomName(Component.literal(names[i]));
					v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
					level.addFreshEntity(v);
					classPeople.add(v);
				}
			},
			List.of(new Step("01_free_jobs", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.FreeJobsPage.PAGE), player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW).is(ModBlocks.STOREHOUSE.asItem()),
							"the empty Storehouse first on the page: " + m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW));
					}
				}, 40),
				new Step("02_who_takes_it", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, player);
						String first = m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW).getHoverName().getString();
						String second = m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 1).getHoverName().getString();
						Showcase.check(first.startsWith("Dara") && second.startsWith("Odo"), "Dara and Odo could take it: " + first + ", " + second);
					}
				}, 40),
				new Step("03_dara_porter", -1, 0, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, player);
					}
					player.closeContainer();
					Villager dara = classPeople.get(0);
					Showcase.check(dara.getVillagerData().getProfession() == ModVillagers.PORTER, "Dara is the Porter: " + dara.getVillagerData().getProfession());
					dara.setNoAi(false); // and walks to her Storehouse
				}, 60)),
			(level, player) -> !classPeople.isEmpty() && classPeople.get(0).getVillagerData().getProfession() == ModVillagers.PORTER
				&& classPeople.get(0).getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
					.map(g -> g.pos().equals(new BlockPos(-4, -60, -3))).orElse(false)));
		// Change job from the hall (ROADMAP 36.18): Dara, a Journeyman Librarian, is right-clicked in the residents list; her page
		// has Change job, which lists the empty Storehouse (and the Builder, with no Blueprint Table free) with her price;
		// a click shows the price, a second pays, and she's the Porter at once and walks over.
		SCREENS.put("hall_retrain", new Screen("Dara, a Journeyman Librarian, was right-clicked in the hall's list; Change job listed the free Storehouse "
			+ "for 20 emeralds and the Builder; two clicks made her the Porter at once, her lectern left free",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				BlockPos store = new BlockPos(-4, -60, -3);
				place(level, store, ModBlocks.STOREHOUSE);
				io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.STOREHOUSE.id(),
					new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), new BlockPos(-12, -60, -7),
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
				BlockPos lectern = new BlockPos(4, -60, 2);
				place(level, lectern, Blocks.LECTERN);
				classPeople.clear();
				Villager dara = EntityType.VILLAGER.create(level);
				dara.moveTo(3.5, -60, 3.5, 180, 0);
				dara.setNoAi(true); // stands by her lectern while the camera looks
				dara.setCustomName(Component.literal("Dara"));
				level.addFreshEntity(dara);
				io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, dara, lectern, net.minecraft.world.entity.ai.village.poi.PoiTypes.LIBRARIAN,
					VillagerProfession.LIBRARIAN);
				dara.setVillagerData(dara.getVillagerData().setLevel(3));
				dara.setVillagerXp(150);
				classPeople.add(dara);
				player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 32));
			},
			List.of(new Step("01_worker_page", io.github.jcondedata.aliveworkplace.hall.HallRetraining.CHANGE, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						for (int s = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON; s < ChoiceMenu.SIZE; s++) {
							if (m.icon(s).getHoverName().getString().startsWith("Dara")) {
								m.clicked(s, 1, net.minecraft.world.inventory.ClickType.PICKUP, player); // a right-click, as the client sends it
								break;
							}
						}
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.HallRetraining.CHANGE).is(net.minecraft.world.item.Items.EMERALD),
							"Dara's page has Change job: " + m.icon(io.github.jcondedata.aliveworkplace.hall.HallRetraining.CHANGE));
					}
				}, 40),
				new Step("02_price", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.HallRetraining.CHANGE, player);
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW).is(ModBlocks.STOREHOUSE.asItem()),
							"the Storehouse first, with the price: " + m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW));
					}
				}, 40),
				new Step("03_dara_porter", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, player);
					}
					Villager dara = classPeople.get(0);
					Showcase.check(dara.getVillagerData().getProfession() == ModVillagers.PORTER, "Dara is the Porter: " + dara.getVillagerData().getProfession());
				}, 40),
				new Step("04_walking", -1, 0, (level, player) -> {
					player.closeContainer();
					classPeople.get(0).setNoAi(false); // and walks to her Storehouse
				}, 60)),
			(level, player) -> !classPeople.isEmpty() && classPeople.get(0).getVillagerData().getProfession() == ModVillagers.PORTER
				&& classPeople.get(0).getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
					.map(g -> g.pos().equals(new BlockPos(-4, -60, -3))).orElse(false)));
		// The Builder asks before repairing a house (ROADMAP 36.15): a Starter Cottage the player's builder finished has five
		// blocks knocked out of its front wall; the builder asks in chat ("Repair it? [Yes] [Not now]"), the hall's Repairs
		// page lists it waiting for an answer, its view has Yes and Not now, and Yes sets the builder to it.
		SCREENS.put("repair_ask", new Screen("the builder asked before repairing the Starter Cottage: the question in chat, the hall's Repairs page listing it "
			+ "waiting for an answer with 5 blocks missing, its Yes and Not now, and after Yes the builder repairing it",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				BlockPos origin = STATION.offset(-5, 0, -15);
				net.minecraft.core.Vec3i size = StarterBlueprints.STARTER_COTTAGE.size();
				level.getStructureManager().get(StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow().placeInWorld(level, origin, origin,
					new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
				BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin,
					net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
				BuildSiteManager.get(level).recordFinished(StarterBlueprints.STARTER_COTTAGE.id(), placement, player.getUUID());
				// five blocks of the front wall knocked out (a creeper's hole, say: nothing a threat is counted for)
				int knocked = 0;
				// (a row of the front at one height has the door and windows in it: take the wall's rows from the floor up
				// until five solid blocks are out)
				for (int y = 1; y < size.getY() && knocked < 5; y++) {
					for (int x = 0; x < size.getX() && knocked < 5; x++) {
						BlockPos at = origin.offset(x, y, 0);
						if (level.getBlockState(at).canOcclude() && level.getBlockEntity(at) == null) {
							level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
							knocked++;
						}
					}
				}
				Showcase.check(knocked == 5, "five blocks knocked out of the cottage's front: " + knocked);
				BlockPos bench = new BlockPos(4, -60, -3);
				place(level, bench, ModBlocks.BUILDERS_BENCH);
				chest(level, bench.east(), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_LOG, 64),
					new ItemStack(Items.GLASS_PANE, 16), new ItemStack(Items.SPRUCE_PLANKS, 64));
				Villager builder = EntityType.VILLAGER.create(level);
				builder.moveTo(4.5, -60, -1.5, 180, 0);
				builder.setCustomName(Component.literal("Bram"));
				level.addFreshEntity(builder);
				io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, bench);
				io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_EMPLOYER.set(builder,
					new io.github.jcondedata.aliveworkplace.build.Employer(player.getUUID(), player.getGameProfile().getName()));
				classPeople.clear();
				classPeople.add(builder);
				io.github.jcondedata.aliveworkplace.build.Upkeep.look(level, builder); // finds the hole and asks
				var ask = BuildSiteManager.get(level).repairAsk(placement);
				Showcase.check(ask != null && ask.state() == io.github.jcondedata.aliveworkplace.build.RepairAsks.State.ASKED,
					"the builder asked about the cottage: " + ask);
			},
			List.of(new Step("01_repair_question", -1, 0, (level, player) -> player.closeContainer(), 30),
				new Step("02_repairs_page", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.RepairsPage.PAGE), player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW).is(Items.CRACKED_STONE_BRICKS),
							"the cottage first on the Repairs page, waiting: " + m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW));
					}
				}, 40),
				new Step("03_yes_or_not_now", io.github.jcondedata.aliveworkplace.hall.RepairsPage.YES, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.RepairsPage.YES).is(Items.LIME_DYE)
							&& m.icon(io.github.jcondedata.aliveworkplace.hall.RepairsPage.NOT_NOW).is(Items.RED_DYE), "the cottage's Yes and Not now");
					}
				}, 40),
				new Step("04_repairing", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.RepairsPage.YES, player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW).is(Items.SCAFFOLDING),
							"after Yes the page shows the cottage being repaired: " + m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW));
					}
				}, 40)),
			(level, player) -> !classPeople.isEmpty() && io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_JOB.has(classPeople.get(0))));
		// Bring an old house up to its latest look (ROADMAP 36.21): a Stone House built from its first drawing stands north of
		// the hall; the Repairs page offers "Refresh to the latest look", a click shows what changes, the button sends Bram,
		// and the last still shows the house once he's done.
		SCREENS.put("refresh_old_house", new Screen("a Stone House built from its first drawing was offered a refresh on the hall's Repairs page, "
			+ "showed what changes, and Bram brought it up to its latest look",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				BlockPos origin = STATION.offset(-5, 0, -15);
				net.minecraft.resources.ResourceLocation house = StarterBlueprints.STONE_HOUSE.id();
				BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin,
					net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
				var first = io.github.jcondedata.aliveworkplace.build.Refreshes.olderDrawing(level.getServer(), house, "25c521dcd");
				Showcase.check(first.isPresent(), "the Stone House's first drawing is shipped");
				if (first.isEmpty()) {
					return;
				}
				for (var e : io.github.jcondedata.aliveworkplace.build.BuildPlan.create(first.get(), placement).targets().entrySet()) {
					level.setBlock(e.getKey(), e.getValue(), 2 | 16);
				}
				BuildSiteManager.get(level).recordFinished(house, placement, player.getUUID());
				BlockPos bench = new BlockPos(4, -60, -3);
				place(level, bench, ModBlocks.BUILDERS_BENCH);
				Villager builder = EntityType.VILLAGER.create(level);
				builder.moveTo(4.5, -60, -1.5, 180, 0);
				builder.setCustomName(Component.literal("Bram"));
				level.addFreshEntity(builder);
				io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, bench);
				io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_EMPLOYER.set(builder,
					new io.github.jcondedata.aliveworkplace.build.Employer(player.getUUID(), player.getGameProfile().getName()));
				classPeople.clear();
				classPeople.add(builder);
				var change = BuildSiteManager.get(level).finishedIn(level).stream().filter(f -> f.placement().equals(placement)).findFirst()
					.flatMap(f -> io.github.jcondedata.aliveworkplace.build.Refreshes.change(level, f));
				Showcase.check(change.isPresent(), "the old Stone House can be refreshed");
				// what the new pieces take, in the chests by the bench
				List<ItemStack> stock = new java.util.ArrayList<>();
				var current = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, house).orElseThrow();
				for (var need : io.github.jcondedata.aliveworkplace.build.BuildPlan.create(current, placement).materials().entrySet()) {
					for (int left = need.getValue(); left > 0; left -= need.getKey().getDefaultMaxStackSize()) {
						stock.add(new ItemStack(need.getKey(), Math.min(left, need.getKey().getDefaultMaxStackSize())));
					}
				}
				for (int i = 0; i * 27 < stock.size(); i++) {
					chest(level, bench.east(1 + 2 * i), stock.subList(i * 27, Math.min(stock.size(), i * 27 + 27)).toArray(ItemStack[]::new));
				}
			},
			List.of(new Step("01_old_house", -1, 0, (level, player) -> player.closeContainer(), 30),
				new Step("02_repairs_page", -2, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.RepairsPage.PAGE), player);
						pickedSlot = -1;
						for (int slot = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
							if (m.icon(slot).is(Items.PAINTING)) {
								pickedSlot = slot;
							}
						}
						Showcase.check(pickedSlot >= 0 && m.icon(pickedSlot).is(Items.PAINTING), "the Stone House on the Repairs page, to refresh");
					}
				}, 40),
				new Step("03_what_changes", io.github.jcondedata.aliveworkplace.hall.RepairsPage.REFRESH, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m && pickedSlot >= 0) {
						m.press(pickedSlot, player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.RepairsPage.REFRESH).is(Items.BRUSH), "the house's Refresh to the latest look");
					}
				}, 40),
				new Step("04_after", -1, 0, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.RepairsPage.REFRESH, player);
					}
					player.closeContainer();
				}, 3600)),
			(level, player) -> BuildSiteManager.get(level).finishedIn(level).stream()
				.filter(f -> f.structure().equals(StarterBlueprints.STONE_HOUSE.id()) && f.placement().origin().equals(STATION.offset(-5, 0, -15)))
				.findFirst().map(f -> io.github.jcondedata.aliveworkplace.build.Refreshes.siteOf(level, f.placement()) == null
					&& io.github.jcondedata.aliveworkplace.build.Refreshes.change(level, f).isEmpty()).orElse(false)));
		// Upgrade a built home from the Village Hall (ROADMAP 36.14): a finished Starter Cottage and a finished Stone House II
		// stand north of the hall; the hall's Upgrades page lists the cottage (level I, next the Starter Cottage II, its cost,
		// the space free) and the Stone House II (next level III, blocked by a garden fence where its new back rooms go, with
		// where); a click on the cottage sends Bram to build its level II over it.
		SCREENS.put("hall_upgrade", new Screen("the hall's Upgrades page listed the Starter Cottage (level I, next the Starter Cottage II, its cost, the space free) "
			+ "and the Stone House II blocked by a garden fence where its level III goes; a click on the cottage sent Bram to build it",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				BuildSiteManager sites = BuildSiteManager.get(level);
				BlockPos cottage = STATION.offset(-8, 0, -15);
				BlockPos house = STATION.offset(6, 0, -16);
				for (var built : List.of(java.util.Map.entry(StarterBlueprints.STARTER_COTTAGE.id(), cottage), java.util.Map.entry(StarterBlueprints.STONE_HOUSE_2.id(), house))) {
					level.getStructureManager().get(built.getKey()).orElseThrow().placeInWorld(level, built.getValue(), built.getValue(),
						new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
					sites.recordFinished(built.getKey(), new BlueprintData.Placement(level.dimension().location(), built.getValue(),
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), player.getUUID());
				}
				// a garden fence behind the Stone House II, where its level III's back rooms go
				for (int x = 2; x <= 8; x++) {
					level.setBlockAndUpdate(house.offset(x, 0, 11), Blocks.OAK_FENCE.defaultBlockState());
				}
				BlockPos bench = new BlockPos(4, -60, -3);
				place(level, bench, ModBlocks.BUILDERS_BENCH);
				chest(level, bench.east(), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_LOG, 64),
					new ItemStack(Items.GLASS_PANE, 32), new ItemStack(Items.SPRUCE_PLANKS, 64));
				Villager builder = EntityType.VILLAGER.create(level);
				builder.moveTo(4.5, -60, -1.5, 180, 0);
				builder.setCustomName(Component.literal("Bram"));
				level.addFreshEntity(builder);
				io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, bench);
				io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_EMPLOYER.set(builder,
					new io.github.jcondedata.aliveworkplace.build.Employer(player.getUUID(), player.getGameProfile().getName()));
				classPeople.clear();
				classPeople.add(builder);
			},
			List.of(new Step("01_upgrades_page", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.UpgradesPage.PAGE), player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW).is(Items.BRICKS),
							"the cottage first on the Upgrades page, its space free: " + m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW));
					}
				}, 40),
				new Step("02_blocked_by_a_fence", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 1, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 1).is(Items.BARRIER),
							"the Stone House II blocked by the fence: " + m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW + 1));
					}
				}, 40),
				new Step("03_bram_builds_it", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW).is(Items.SCAFFOLDING),
							"after the click the cottage is being upgraded: " + m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW));
					}
				}, 40)),
			(level, player) -> !classPeople.isEmpty() && io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_JOB.has(classPeople.get(0))));
		// The price board (ROADMAP 33.4): Thornholm's Trade page on Prices (stars on Timber and Wool, a red mark on Bread,
		// arrows on what moved), Timber's tooltip naming the dearer and the cheaper village on its routes, and the hall's
		// name icon with "Known for: Timber, Wool. Short of: Bread".
		SCREENS.put("price_board", new Screen("the hall's Trade page showed Thornholm's prices: Timber and Wool starred, Bread marked short, arrows on what moved, "
			+ "Timber's tooltip with the dearer and the cheaper village, and the name icon's Known for line", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION)).setCustomName(Component.literal("Thornholm"));
				priceBoard(level);
			},
			// the pointer rests on the way back (slot 0), clear of the goods, so the whole board shows
			List.of(new Step("01_price_board", 0, 6, (level, player) -> {
					priceBoard(level); // again, should the hall's round have counted the village in between
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					pickedSlot = -1;
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.ROUTES, player);
						Showcase.check(io.github.jcondedata.aliveworkplace.trade.TradePage.marks(m.icon(io.github.jcondedata.aliveworkplace.trade.TradePage.Tab.ROUTES.slot()))
							.contains(io.github.jcondedata.aliveworkplace.trade.TradePage.OPEN), "the minecart opens the Trade page on its Routes tab");
						m.press(io.github.jcondedata.aliveworkplace.trade.TradePage.Tab.PRICES.slot(), player);
						int goods = 0;
						for (int slot = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
							java.util.Set<String> marks = io.github.jcondedata.aliveworkplace.trade.TradePage.marks(m.icon(slot));
							String name = m.icon(slot).getHoverName().getString();
							goods += m.icon(slot).isEmpty() ? 0 : 1;
							if (name.equals("Timber")) {
								pickedSlot = slot;
								Showcase.check(marks.contains(io.github.jcondedata.aliveworkplace.trade.TradePage.STAR)
									&& marks.contains(io.github.jcondedata.aliveworkplace.trade.TradePage.DOWN), "Timber is starred and its price fell: " + marks);
							} else if (name.equals("Bread")) {
								Showcase.check(marks.contains(io.github.jcondedata.aliveworkplace.trade.TradePage.SHORT)
									&& marks.contains(io.github.jcondedata.aliveworkplace.trade.TradePage.UP), "Bread is marked short and its price rose: " + marks);
							}
						}
						Showcase.check(goods == io.github.jcondedata.aliveworkplace.trade.TradeGoods.all().size() && goods >= 25, "the Prices tab lists every good: " + goods);
						Showcase.check(pickedSlot >= 0, "Timber is on the Prices tab");
					}
				}, 30),
				// Timber hovered: both prices, down since yesterday, known for it, dearer in Ashford, cheaper in Ridgeway
				new Step("02_price_tooltip", -2, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m && pickedSlot >= 0) {
						List<String> lore = new ArrayList<>();
						m.icon(pickedSlot).getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY)
							.lines().forEach(l -> lore.add(l.getString()));
						Showcase.check(lore.stream().anyMatch(l -> l.startsWith("Dearer in Ashford: ")) && lore.stream().anyMatch(l -> l.startsWith("Cheaper in Ridgeway: ")),
							"Timber's tooltip names the dearer and the cheaper village: " + lore);
					}
				}, 30),
				// Back at the hall, the name tag (slot 0) hovered: "Known for: Timber, Wool. Short of: Bread"
				new Step("03_hall_known_for", 0, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					Component line = io.github.jcondedata.aliveworkplace.trade.TradePage.knownAndShort(level, STATION);
					Showcase.check(line != null && line.getString().equals("Known for: Timber, Wool. Short of: Bread"),
						"the name icon says what Thornholm is known for and short of: " + (line == null ? null : line.getString()));
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		// The Merchant Prince's board (ROADMAP 33.23): Thornholm with the Prince settled by its hall; its Prices tab
		// (sells at 105%, half the usual spread) and Timber's tooltip naming Dunmore, a village in caravan range that isn't
		// one of its partners, as the dearest.
		SCREENS.put("merchant_prince_board", new Screen("the Merchant Prince settled by Thornholm's hall; its Prices tab sells at only 5% over, and Timber's tooltip "
			+ "names Dunmore, in caravan range but no partner, as where it's dearest", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION)).setCustomName(Component.literal("Thornholm"));
				priceBoard(level);
				merchantPrinceBoard(level);
				var legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("merchant_prince")).orElseThrow();
				subject = EntityType.VILLAGER.spawn(level, STATION.south(2).east(2), MobSpawnType.COMMAND);
				subject.setNoAi(true);
				io.github.jcondedata.aliveworkplace.legend.Legends.make(level, subject, legend, "showcase", java.util.Optional.of(STATION));
				io.github.jcondedata.aliveworkplace.legend.LegendPowers.forget();
			},
			List.of(new Step("01_prince_prices", 0, 6, (level, player) -> {
					priceBoard(level);
					merchantPrinceBoard(level);
					Showcase.check(io.github.jcondedata.aliveworkplace.trade.TradePage.sellPercent(level, STATION) == 105, "the Prince halves the spread: "
						+ io.github.jcondedata.aliveworkplace.trade.TradePage.sellPercent(level, STATION));
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					pickedSlot = -1;
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.ROUTES, player);
						m.press(io.github.jcondedata.aliveworkplace.trade.TradePage.Tab.PRICES.slot(), player);
						for (int slot = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
							if (m.icon(slot).getHoverName().getString().equals("Timber")) {
								pickedSlot = slot;
							}
						}
						Showcase.check(pickedSlot >= 0, "Timber is on the Prices tab");
					}
				}, 30),
				new Step("02_prince_dunmore", -2, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m && pickedSlot >= 0) {
						List<String> lore = new ArrayList<>();
						m.icon(pickedSlot).getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY)
							.lines().forEach(l -> lore.add(l.getString()));
						Showcase.check(lore.stream().anyMatch(l -> l.startsWith("Dearer in Dunmore: ")), "Timber's tooltip names Dunmore, no partner, as the dearest: " + lore);
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		// Trading at the board (ROADMAP 33.5): Thornholm's Prices tab with its treasury (12.4 emeralds), a click on Timber
		// buying a bundle out of the Storehouse's chest (0.88 emeralds: charged 1), and the treasury after (13.4).
		SCREENS.put("board_trade", new Screen("a click on Timber on the hall's Prices tab bought a bundle of 16 logs out of the Storehouse's chest for 1 emerald, "
			+ "and the treasury on the page went from 12.4 to 13.4 emeralds", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				place(level, new BlockPos(4, -60, -3), ModBlocks.STOREHOUSE);
				chest(level, new BlockPos(5, -60, -3), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.WHITE_WOOL, 48),
					new ItemStack(Items.BREAD, 12));
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION)).setCustomName(Component.literal("Thornholm"));
				priceBoard(level);
				// a buyer with emeralds and no logs, in survival so the emerald is really paid
				player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				player.getInventory().clearContent();
				player.getInventory().add(new ItemStack(Items.EMERALD, 8));
			},
			// 1: the Prices tab, the pointer on the treasury (12.4 emeralds, its cap, who collects)
			List.of(new Step("01_board_before", io.github.jcondedata.aliveworkplace.trade.TradePage.TREASURY, 6, (level, player) -> {
					priceBoard(level); // again, should the hall's round have counted the village in between
					((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION)).setTreasury(1240);
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					pickedSlot = -1;
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.ROUTES, player);
						m.press(io.github.jcondedata.aliveworkplace.trade.TradePage.Tab.PRICES.slot(), player);
						for (int slot = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
							if (m.icon(slot).getHoverName().getString().equals("Timber")) {
								pickedSlot = slot;
							}
						}
						Showcase.check(pickedSlot >= 0, "Timber is on the Prices tab");
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.trade.TradePage.TREASURY).getHoverName().getString().equals("Treasury: 12.4 emeralds"),
							"the page shows the treasury before: " + m.icon(io.github.jcondedata.aliveworkplace.trade.TradePage.TREASURY).getHoverName().getString());
						if (pickedSlot >= 0) {
							List<String> lore = new ArrayList<>();
							m.icon(pickedSlot).getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY)
								.lines().forEach(l -> lore.add(l.getString()));
							Showcase.check(lore.contains("We sell: 0.88 emeralds") && lore.contains("Bundles to spare in the Storehouse: 5")
								&& lore.contains("Click: buy a bundle. Shift-click: all you can"), "Timber's tooltip says what a click does: " + lore);
						}
					}
				}, 40),
				// 2: the click on Timber; its tooltip says what was bought and for how much
				new Step("02_board_bought", -2, 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m && pickedSlot >= 0) {
						m.press(pickedSlot, player);
						List<String> lore = new ArrayList<>();
						m.icon(pickedSlot).getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY)
							.lines().forEach(l -> lore.add(l.getString()));
						Showcase.check(!lore.isEmpty() && lore.get(0).equals("You buy 16 × Timber from Thornholm for 1 emerald."), "the tooltip says what was bought: " + lore);
						Showcase.check(player.getInventory().countItem(Items.OAK_LOG) == 16 && player.getInventory().countItem(Items.EMERALD) == 7,
							"the player got 16 logs for 1 emerald: " + player.getInventory().countItem(Items.OAK_LOG) + " logs, "
							+ player.getInventory().countItem(Items.EMERALD) + " emeralds");
						Container chest = (Container) level.getBlockEntity(new BlockPos(5, -60, -3));
						Showcase.check(chest != null && chest.countItem(Items.OAK_LOG) == 80, "the logs came out of the Storehouse's chest: "
							+ (chest == null ? null : chest.countItem(Items.OAK_LOG)));
					}
				}, 40),
				// 3: the treasury after: one emerald more
				new Step("03_board_after", io.github.jcondedata.aliveworkplace.trade.TradePage.TREASURY, 6, (level, player) -> {
					int treasury = ((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION)).treasury();
					Showcase.check(treasury == 1340, "the treasury kept the emerald: " + treasury);
					if (player.containerMenu instanceof ChoiceMenu m) {
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.trade.TradePage.TREASURY).getHoverName().getString().equals("Treasury: 13.4 emeralds"),
							"the page shows the treasury after: " + m.icon(io.github.jcondedata.aliveworkplace.trade.TradePage.TREASURY).getHoverName().getString());
					}
				}, 40)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		// Caravans that trade (ROADMAP 33.6): Thornholm's Routes tab saying what its caravan sells in Ashford and what it
		// earns ("Timber ×2 for 2.77 emeralds": Ashford is short of Timber and pays 1.4 a bundle), then the caravan sent
		// and sold there (Ashford is a real second hall 180 blocks east, with its own Storehouse and treasury), and
		// Thornholm's chronicle with "Sold 32 Timber to Ashford for 2.77 emeralds".
		SCREENS.put("caravan_trade", new Screen("the hall's Routes tab said the caravan to Ashford sells Timber ×2 for 2.77 emeralds, the caravan sold it there, "
			+ "and the chronicle says Sold 32 Timber to Ashford for 2.77 emeralds", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				place(level, new BlockPos(4, -60, -3), ModBlocks.STOREHOUSE);
				chest(level, new BlockPos(5, -60, -3), new ItemStack(Items.OAK_LOG, 48), new ItemStack(Items.BREAD, 12));
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION)).setCustomName(Component.literal("Thornholm"));
				// Ashford: a hall of its own with a Storehouse and an empty chest, kept loaded so its treasury can pay
				BlockPos ashford = STATION.east(180);
				level.setChunkForced(ashford.getX() >> 4, ashford.getZ() >> 4, true);
				level.setChunkForced(ashford.east(5).getX() >> 4, ashford.getZ() >> 4, true);
				level.setBlockAndUpdate(ashford, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				place(level, ashford.east(4), ModBlocks.STOREHOUSE);
				chest(level, ashford.east(5));
				((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(ashford)).setCustomName(Component.literal("Ashford"));
				priceBoard(level);
			},
			// 1: the Routes tab, the pointer on Ashford: what the caravan sells there and what it earns
			List.of(new Step("01_routes_earnings", -2, 6, (level, player) -> {
					caravanTrade(level);
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					pickedSlot = -1;
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.ROUTES, player);
						for (int slot = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
							if (m.icon(slot).getHoverName().getString().equals("Ashford")) {
								pickedSlot = slot;
							}
						}
						Showcase.check(pickedSlot >= 0, "Ashford is on the Routes tab");
						if (pickedSlot >= 0) {
							List<String> lore = new ArrayList<>();
							m.icon(pickedSlot).getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY)
								.lines().forEach(l -> lore.add(l.getString()));
							Showcase.check(lore.contains("Our caravan sells there:") && lore.contains("Timber ×2 for 2.77 emeralds"),
								"the Routes tab says what goes to Ashford and what it earns: " + lore);
						}
					}
				}, 50),
				// 2: the caravan goes and sells in Ashford; Thornholm's chronicle, the pointer on the sale
				new Step("02_chronicle_sold", -2, 6, (level, player) -> {
					BlockPos ashford = STATION.east(180);
					io.github.jcondedata.aliveworkplace.hall.Caravans.Data data = io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level);
					data.sent(STATION, -1);
					io.github.jcondedata.aliveworkplace.hall.Caravans.round(level, STATION, null); // today's caravan: 32 logs to sell
					data.hurry(ashford);
					io.github.jcondedata.aliveworkplace.hall.Caravans.round(level, ashford, null); // it arrives: Ashford's treasury pays Thornholm's
					io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity ours = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity theirs = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(ashford);
					Showcase.check(ours.treasury() == 1240 + 277 && theirs.treasury() == 2000 - 277,
						"Ashford's treasury paid Thornholm's 2.77 emeralds: ours " + ours.treasury() + ", theirs " + theirs.treasury());
					Container sold = (Container) level.getBlockEntity(ashford.east(5));
					Container kept = (Container) level.getBlockEntity(new BlockPos(5, -60, -3));
					Showcase.check(sold != null && sold.countItem(Items.OAK_LOG) == 32 && kept != null && kept.countItem(Items.OAK_LOG) == 16,
						"32 logs went from Thornholm's Storehouse (which keeps 16) to Ashford's: " + (sold == null ? null : sold.countItem(Items.OAK_LOG))
						+ " there, " + (kept == null ? null : kept.countItem(Items.OAK_LOG)) + " here");
					int oursNow = io.github.jcondedata.aliveworkplace.trade.Prices.at(level, STATION, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("timber"));
					int theirsNow = io.github.jcondedata.aliveworkplace.trade.Prices.at(level, ashford, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("timber"));
					Showcase.check(oursNow == 84 && theirsNow == 134, "both boards moved 2% a bundle: ours " + oursNow + ", theirs " + theirsNow);
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					pickedSlot = -1;
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.CHRONICLE, player);
						List<String> lines = new ArrayList<>();
						for (int slot = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
							lines.add(m.icon(slot).getHoverName().getString());
							if (m.icon(slot).getHoverName().getString().equals("Sold 32 Timber to Ashford for 2.77 emeralds")) {
								pickedSlot = slot;
							}
						}
						Showcase.check(pickedSlot >= 0, "the chronicle says what was sold: " + lines);
					}
				}, 50)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		SCREENS.put("legend_announce", new Screen("a Mythic Legend's coming was announced in chat and written in the chronicle", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				// ROADMAP 29.3: a Mythic Legend of the scene's own settles by the hall; everyone on the server hears it, in gold.
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
			},
			List.of(new Step("01_legend_chat", -1, 6, (level, player) -> {
					net.minecraft.resources.ResourceLocation id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_mythic");
					io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.read(id,
						com.google.gson.JsonParser.parseString("{\"rarity\": \"mythic\", \"job\": \"aliveworkplace:legend\", \"title\": \"entity.minecraft.villager.legend\","
							+ " \"lore\": \"entity.minecraft.villager.legend\"}").getAsJsonObject());
					io.github.jcondedata.aliveworkplace.legend.Legends.setForTest(Map.of(id, legend));
					Villager hero = EntityType.VILLAGER.spawn(level, STATION.south(2).east(2), MobSpawnType.COMMAND);
					hero.setNoAi(true);
					hero.setYRot(180);
					hero.setYHeadRot(180);
					hero.setCustomName(net.minecraft.network.chat.Component.literal("Aurelia"));
					// make announces it (in gold, to every player) and writes the chronicle line, as a real settling does
					io.github.jcondedata.aliveworkplace.legend.Legends.make(level, hero, legend, "showcase");
					Showcase.check(io.github.jcondedata.aliveworkplace.legend.LegendSlots.audience(level, STATION, hero.blockPosition(),
						legend.rarity()).contains(player), "the player heard the Mythic Legend's announcement");
				}, 60),
				new Step("02_legend_chronicle", -1, 6, (level, player) -> {
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					Showcase.check(hall.chronicle().stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.LEGEND),
						"the chronicle has the Legend's line");
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.CHRONICLE, player);
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		SCREENS.put("gifted_born", new Screen("a child of two schooled Masters grew up Gifted, and the chronicle says so", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				// ROADMAP 29.7: Dara and Tom, schooled Master farmers, and their daughter Wren, still a child, by the hall.
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				Villager dara = bornSceneVillager(level, new BlockPos(-1, -60, 2), "Dara", true);
				Villager tom = bornSceneVillager(level, new BlockPos(1, -60, 2), "Tom", true);
				subject = bornSceneVillager(level, new BlockPos(0, -60, 2), "Wren", false);
				subject.setAge(-24000);
				io.github.jcondedata.aliveworkplace.people.Families.born(subject, dara, tom);
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
			},
			List.of(new Step("01_wren_grown_up", -1, 6, (level, player) -> {
					// She grows up; the hall's round throws the born dice (fixed here: the Legend die misses, the gift die hits).
					Villager wren = subject;
					wren.setAge(0);
					var parents = io.github.jcondedata.aliveworkplace.people.Families.parents(wren);
					Showcase.check(parents != null && parents.motherSchooledMaster() && parents.fatherSchooledMaster(),
						"Wren's parents are on record as schooled Masters");
					io.github.jcondedata.aliveworkplace.people.Families.round(level, STATION, wren, net.minecraft.util.RandomSource.create(1L));
					Showcase.check(io.github.jcondedata.aliveworkplace.legend.Gifted.of(wren) != null, "Wren grew up Gifted");
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, wren.getX(), wren.getY() + 1.2, wren.getZ(), 30, 0.4, 0.6, 0.4, 0.15);
				}, 40),
				new Step("02_born_chronicle", -1, 6, (level, player) -> {
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					Showcase.check(hall.chronicle().stream().anyMatch(e -> e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
						&& t.getKey().equals("chronicle.aliveworkplace.grown_up_gifted")), "the chronicle says Wren has grown up Gifted");
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.CHRONICLE, player);
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		SCREENS.put("legend_guest", new Screen("a Legend came to the inn as a guest, showed their terms, and settled once a home was ready", new Vec3(2.5, -57.4, 7.5), TARGET,
			(level, player) -> {
				// ROADMAP 29.8: a village with a hall and an inn (an innkeeper at her counter, a free bed), and a showcase Legend
				// who visits the inn (chance 1, so she comes this morning), a Mason who wants a home of her own and a happy village.
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.read(
					io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_stonewright"), com.google.gson.JsonParser.parseString(
						"{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \"Master Stonewright\","
						+ " \"lore\": \"Her walls have stood for three hundred winters.\", \"names\": [\"Ada Stonewright\"],"
						+ " \"arrive\": [{\"way\": \"visit\", \"place\": \"inn\", \"chance\": 1.0}],"
						+ " \"powers\": [{\"type\": \"pace\", \"trades\": [\"minecraft:mason\"], \"radius\": 32, \"factor\": 2.0}]}").getAsJsonObject());
				io.github.jcondedata.aliveworkplace.legend.Legends.setForTest(java.util.Map.of(legend.id(), legend));
				BlockPos counter = new BlockPos(-3, -60, -2);
				level.setBlockAndUpdate(counter, ModBlocks.INN_COUNTER.defaultBlockState());
				Villager keeper = EntityType.VILLAGER.spawn(level, new BlockPos(-3, -60, -1), MobSpawnType.COMMAND);
				keeper.setNoAi(true);
				ScreenshotHarness.fileRecords(level, counter, 1); // the counter's record now, not in vanilla's next task, so she takes the job
				Jobs.employ(level, keeper, counter, ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
				scholar = keeper;
				for (int x : new int[] {-6, -5}) {
					level.setBlockAndUpdate(new BlockPos(x, -60, -4), Blocks.RED_BED.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
					level.setBlockAndUpdate(new BlockPos(x, -60, -5), Blocks.RED_BED.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
				}
				level.setDayTime(level.getDayTime() / 24000L * 24000L + 24000L + 1000L); // the next morning
				// A happy village (moods are off for the scene, so its wellbeing decides).
				io.github.jcondedata.aliveworkplace.people.Moods.ENABLED = false;
				((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION))
					.setNeeds(new io.github.jcondedata.aliveworkplace.hall.VillageNeeds.Needs(1, 1, 1, 1, 1, 0, 0, 0.9f));
			},
			List.of(new Step("01_guest_arrives", -1, 6, (level, player) -> {
					// The inn's morning: instead of a traveller, the Legend walks in, announced in chat and the chronicle.
					io.github.jcondedata.aliveworkplace.inn.Innkeepers.tend(level, scholar, new BlockPos(-3, -60, -2));
					subject = io.github.jcondedata.aliveworkplace.legend.LegendGuests.guest(level, STATION);
					Showcase.check(subject != null, "a Legend came to the inn as a guest");
					Showcase.check(io.github.jcondedata.aliveworkplace.inn.Innkeepers.guests(level, new BlockPos(-3, -60, -2)).isEmpty(),
						"no traveller came the same morning");
					subject.setNoAi(true);
					subject.moveTo(0.5, -60, 2.5, 180f, 0f);
					subject.setYHeadRot(180f);
				}, 40),
				new Step("02_terms", io.github.jcondedata.aliveworkplace.legend.LegendGuests.WANTS_SLOT, 6, (level, player) -> {
					// Right-clicked: who she is, what she'd bring, what she wants (no home yet: a cross), the days left.
					io.github.jcondedata.aliveworkplace.legend.LegendGuests.openTerms(player, subject);
					Showcase.check(player.containerMenu instanceof ChoiceMenu, "the terms screen opened");
					var data = io.github.jcondedata.aliveworkplace.registry.ModAttachments.LEGEND.get(subject);
					Showcase.check(data != null && data.guest() && !data.unmet().isEmpty(), "she wants a home first");
				}, 40),
				new Step("03_settled", -1, 6, (level, player) -> {
					// A tier III home is finished beside the hall: at the next round she claims its bed and settles, a Master Mason.
					player.closeContainer();
					net.minecraft.resources.ResourceLocation home = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_guest_manor_3");
					BlockPos origin = new BlockPos(3, -60, -6);
					level.getStructureManager().getOrCreate(home).fillFromWorld(level, origin.above(60), new net.minecraft.core.Vec3i(5, 4, 5), false, Blocks.AIR);
					io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(home,
						new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), origin,
							net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), java.util.UUID.randomUUID());
					level.setBlockAndUpdate(new BlockPos(5, -60, -4), Blocks.BLUE_BED.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
					level.setBlockAndUpdate(new BlockPos(5, -60, -5), Blocks.BLUE_BED.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
					ScreenshotHarness.fileRecords(level, new BlockPos(5, -60, -5), 1); // the new bed's record now, so the round finds the home free
					// B285: the hall counts its needs again every VillageNeeds.CHECK_EVERY ticks, and this staged village (an
					// innkeeper and a guest) counts unhappy, so a count landing between the setup and here undid the happy
					// village the scene stages. Set it again right before the round reads it, as holdTown does the rank.
					((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION))
						.setNeeds(new io.github.jcondedata.aliveworkplace.hall.VillageNeeds.Needs(1, 1, 1, 1, 1, 0, 0, 0.9f));
					io.github.jcondedata.aliveworkplace.legend.LegendGuests.tend(level, STATION);
					var data = io.github.jcondedata.aliveworkplace.registry.ModAttachments.LEGEND.get(subject);
					Showcase.check(data != null && data.settled(), "she settled once the home was ready"
						+ (data != null && data.settled() ? "" : " (waiting on " + (data == null ? "no Legend data" : data.unmet().keySet()
							+ ", free home " + io.github.jcondedata.aliveworkplace.legend.LegendGuests.freeHome(level, STATION)) + ")"));
					Showcase.check(subject.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.MASON
						&& subject.getVillagerData().getLevel() == 5, "she is a Master Mason");
				}, 40),
				new Step("04_settled_chronicle", -1, 6, (level, player) -> {
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					Showcase.check(hall.chronicle().stream().anyMatch(e -> e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
						&& t.getKey().equals("chronicle.aliveworkplace.legend.settled")), "the chronicle says she settled");
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.CHRONICLE, player);
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		SCREENS.put("legend_merchant_prince", new Screen("the Merchant Prince settled by the hall in his crimson coat, the hall's bank page with emeralds put in, and a trade fair",
			new Vec3(0.5, -58.0, 6.5), new Vec3(0.5, -59.5, 0.5),
			(level, player) -> {
				// ROADMAP 29.17: a Village Hall with the Merchant Prince settled beside it, so its bank is open.
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getGameProfile().getName());
				var legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("merchant_prince")).orElseThrow();
				subject = EntityType.VILLAGER.spawn(level, STATION.south(2), MobSpawnType.COMMAND);
				subject.setNoAi(true);
				subject.setYRot(0);
				subject.setYHeadRot(0);
				io.github.jcondedata.aliveworkplace.legend.Legends.make(level, subject, legend, "showcase");
				io.github.jcondedata.aliveworkplace.legend.LegendPowers.forget();
				level.setDayTime(level.getDayTime() / 24000L * 24000L + 6000L);
			},
			List.of(new Step("01_merchant_prince", -1, 6, (level, player) ->
					Showcase.check(io.github.jcondedata.aliveworkplace.hall.PlayerBank.open(level, STATION), "the Prince settled and the bank is open"), 40),
				new Step("02_bank_page", io.github.jcondedata.aliveworkplace.hall.PlayerBank.BALANCE, 6, (level, player) -> {
					// The player puts in 64 emeralds, then 16, on the hall's bank page.
					player.getInventory().add(new ItemStack(Items.EMERALD, 64));
					player.getInventory().add(new ItemStack(Items.EMERALD, 64));
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.PlayerBank.BUTTON, player);
						m.press(io.github.jcondedata.aliveworkplace.hall.PlayerBank.DEPOSIT + 2, player);
						m.press(io.github.jcondedata.aliveworkplace.hall.PlayerBank.DEPOSIT + 1, player);
					}
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					Showcase.check(hall.playerBank().emeralds(player.getUUID()) == 80, "80 emeralds in the player's account ("
						+ hall.playerBank().emeralds(player.getUUID()) + ")");
				}, 40),
				new Step("03_trade_fair", -1, 6, (level, player) -> {
					// The fair: six traders round the hall (no Market Square here), red and yellow bunting round them, fireworks overhead.
					player.closeContainer();
					var power = io.github.jcondedata.aliveworkplace.legend.TradeFairPower.of(level, STATION).orElseThrow();
					var traders = io.github.jcondedata.aliveworkplace.hall.TradeFairs.hold(level, STATION, power);
					Showcase.check(traders.size() == 6, "six traders came to the fair (" + traders.size() + ")");
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					Showcase.check(hall.fairBunting().size() >= 8, "the fair's bunting is up (" + hall.fairBunting().size() + " banners)");
				}, 80)),
			(level, player) -> true));
		SCREENS.put("legend_sites", new Screen("the three camps of Legends found in the world, and a prisoner freed from the outpost cage",
			new Vec3(0.5, -53.0, 13.5), new Vec3(0.5, -59.5, -4.5),
			(level, player) -> {
				// ROADMAP 29.9: the camps a qualifying player finds at a ruined portal, a pillager outpost and a shipwreck's beach,
				// set down side by side (a traveller's camp, a prisoner's cage, a castaway's camp), each with its Legend waiting.
				java.util.Map<net.minecraft.resources.ResourceLocation, io.github.jcondedata.aliveworkplace.legend.Legend> map = new java.util.LinkedHashMap<>();
				String[][] sites = {{"ruined_portal", "Wren the Wayfarer"}, {"outpost", "Bram the Captive"}, {"shipwreck", "Old Tamsin"}};
				int[] xs = {-11, 0, 11};
				for (int i = 0; i < 3; i++) {
					io.github.jcondedata.aliveworkplace.legend.Legend legend = io.github.jcondedata.aliveworkplace.legend.Legends.read(
						io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_found_" + sites[i][0]), com.google.gson.JsonParser.parseString(
							"{\"rarity\": \"rare\", \"job\": \"minecraft:cartographer\", \"title\": \"Pathfinder\", \"lore\": \"Found in the wild.\","
							+ " \"names\": [\"" + sites[i][1] + "\"], \"arrive\": [{\"way\": \"found\", \"site\": \"" + sites[i][0] + "\"}]}").getAsJsonObject());
					map.put(legend.id(), legend);
					Villager v = io.github.jcondedata.aliveworkplace.legend.LegendSites.place(level, io.github.jcondedata.aliveworkplace.legend.LegendSites.site(sites[i][0]),
						new io.github.jcondedata.aliveworkplace.legend.LegendSites.Match(STATION, legend), new BlockPos(xs[i], -61, -4),
						net.minecraft.util.RandomSource.create(4));
					Showcase.check(v != null, "the " + sites[i][0] + " camp was set down");
					v.setYHeadRot(180f);
					if (i == 1) {
						subject = v;
					}
				}
				io.github.jcondedata.aliveworkplace.legend.Legends.setForTest(map);
				level.setDayTime(level.getDayTime() / 24000L * 24000L + 6000L);
			},
			List.of(new Step("01_three_camps", -1, 6, (level, player) -> {
					Showcase.check(io.github.jcondedata.aliveworkplace.legend.LegendSites.isCaptive(subject), "the prisoner waits in the cage");
				}, 40),
				new Step("02_cage_opened", -1, 6, (level, player) -> {
					// A bar of the cage broken: the prisoner is free, thanks the player and walks off to the village.
					player.teleportTo(0.5, -60, 2.5);
					BlockPos bar = null;
					for (BlockPos p : BlockPos.betweenClosed(subject.blockPosition().offset(-3, -1, -3), subject.blockPosition().offset(3, 3, 3))) {
						if (level.getBlockState(p).is(Blocks.IRON_BARS) && p.getZ() > subject.getZ()) {
							bar = p.immutable();
							break;
						}
					}
					Showcase.check(bar != null, "the cage has a bar facing the camera");
					player.gameMode.destroyBlock(bar);
					Showcase.check(!io.github.jcondedata.aliveworkplace.legend.LegendSites.isCaptive(subject), "breaking a bar freed the prisoner");
				}, 60)),
			(level, player) -> true));
		SCREENS.put("legend_professor", new Screen("the Pokémon Professor in a white lab coat by the hall, hints about the player's party, and the Pokédex research tab",
			new Vec3(0.5, -58.0, 6.5), new Vec3(0.5, -59.5, 0.5),
			(level, player) -> {
				// ROADMAP 29.21: a Village Hall with the Pokémon Professor settled beside it; 22 species already in the
				// village Pokédex, and three Pokémon in the player's party.
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getGameProfile().getName());
				var legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(io.github.jcondedata.aliveworkplace.legend.PokemonProfessor.ID).orElseThrow();
				subject = EntityType.VILLAGER.spawn(level, STATION.south(2), MobSpawnType.COMMAND);
				subject.setNoAi(true);
				subject.setYRot(0);
				subject.setYHeadRot(0);
				// the hall's record is filed only in vanilla's next task: filed now, the Legend is recorded as this village's
				ScreenshotHarness.fileRecords(level, STATION, 8);
				io.github.jcondedata.aliveworkplace.legend.Legends.make(level, subject, legend, "showcase");
				io.github.jcondedata.aliveworkplace.legend.LegendPowers.forget();
				List<String> species = new java.util.ArrayList<>();
				for (String s : List.of("bulbasaur", "charmander", "squirtle", "pikachu", "eevee", "machop", "geodude", "pidgey", "rattata", "oddish",
					"psyduck", "growlithe", "abra", "magnemite", "gastly", "onix", "cubone", "chansey", "miltank", "mareep", "wooper", "snorlax")) {
					species.add("cobblemon:" + s);
				}
				hall.logPokedex(species);
				party(player, "bulbasaur level=12 nature=adamant ability=chlorophyll", "pikachu level=8 nature=timid", "eevee level=5 nature=hardy");
				level.setDayTime(level.getDayTime() / 24000L * 24000L + 6000L);
			},
			List.of(new Step("01_professor", -1, 6, (level, player) ->
					Showcase.check(io.github.jcondedata.aliveworkplace.legend.PokemonProfessor.isProfessor(subject), "the Professor settled by the hall"), 40),
				new Step("02_professor_hints", io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonProfessor.FIRST_PARTY_SLOT, 6, (level, player) -> {
					// Right-clicked with an empty hand: the party, the Bulbasaur's hints in its tooltip
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
					io.github.jcondedata.aliveworkplace.legend.PokemonProfessor.open(player, subject);
					Showcase.check(player.containerMenu instanceof ChoiceMenu m
						&& m.icon(io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonProfessor.FIRST_PARTY_SLOT).get(net.minecraft.core.component.DataComponents.LORE) != null,
						"the hints screen shows the party with its hints");
				}, 40),
				new Step("03_pokedex_tab", io.github.jcondedata.aliveworkplace.research.ResearchScreen.TOPIC_SLOTS[1], 6, (level, player) -> {
					// The Pokédex tab: Field Notes I done, Kinship Studies being researched (22 species: both open)
					var tree = io.github.jcondedata.aliveworkplace.research.ResearchTrees.get(io.github.jcondedata.aliveworkplace.legend.PokemonProfessor.TREE).orElseThrow();
					var state = new io.github.jcondedata.aliveworkplace.research.Research.State(Map.of(tree.key() + "/field_notes", 1),
						java.util.Optional.empty(), 0, false);
					state = io.github.jcondedata.aliveworkplace.research.ResearchTrees.choose(state, tree, tree.topic("kinship_studies").orElseThrow());
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					hall.setResearch(io.github.jcondedata.aliveworkplace.research.ResearchTrees.withProgress(state, tree, 40));
					io.github.jcondedata.aliveworkplace.research.ResearchScreen.openForLegend(player, subject);
					Showcase.check(player.containerMenu instanceof ChoiceMenu m
						&& m.icon(io.github.jcondedata.aliveworkplace.research.ResearchScreen.TAB_SLOTS[1]).is(Items.SPYGLASS), "the Pokédex opened on its own tab");
				}, 40)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		// The Professor's Thesis (ROADMAP 31.21, Cobblemon): an Expert Scholar, Professor Alder, by the hall asks for field
		// notes on Life in the Cold; a Snorunt caught in the snow counts; Thessaly beaten, the thesis is published as a book.
		SCREENS.put("professors_thesis", new Screen("the Professor's Thesis: Professor Alder by the hall asking for help with a thesis on Life in the Cold, the chapter's intro in chat; a Snorunt caught in a snowy biome going in the field notes; then the published thesis, the book 'Professor Alder: Life in the Cold' open on its cover page with the player as co-author",
			new Vec3(0.5, -58.0, 6.5), new Vec3(0.5, -59.5, 0.5),
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getGameProfile().getName());
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN);
				io.github.jcondedata.aliveworkplace.story.Arcs.stopAll(level, STATION);
				subject = EntityType.VILLAGER.spawn(level, STATION.south(2), MobSpawnType.COMMAND);
				subject.setNoAi(true);
				subject.setYRot(0);
				subject.setYHeadRot(0);
				subject.setVillagerData(subject.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.SCHOLAR).setLevel(4));
				subject.setCustomName(Component.literal("Alder"));
				level.setDayTime(level.getDayTime() / 24000L * 24000L + 6000L);
			},
			List.of(new Step("01_request", -1, 0, (level, player) -> {
					io.github.jcondedata.aliveworkplace.story.ProfessorsThesis.FORCE_TOPIC = "cold";
					var arc = io.github.jcondedata.aliveworkplace.story.Arcs.get(io.github.jcondedata.aliveworkplace.story.ProfessorsThesis.ARC).orElse(null);
					var s = arc == null ? null : io.github.jcondedata.aliveworkplace.story.Arcs.start(level, STATION, arc);
					io.github.jcondedata.aliveworkplace.story.ProfessorsThesis.FORCE_TOPIC = null;
					Showcase.check(s != null && s.quests.size() == 3, "Professor Alder asks for three field notes");
				}, 60),
				new Step("02_catch", -1, 0, (level, player) -> {
					party(player, "snorunt level=14");
					var snorunt = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player).get(0);
					var real = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonThesis.info(level, snorunt, STATION.south(4), false);
					var info = new io.github.jcondedata.aliveworkplace.story.ProfessorsThesis.CatchInfo(real.uuid(), real.species(), real.name(), real.types(), real.pos(),
						level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS),
						real.dayTime(), real.moonPhase(), real.day(), false);
					Showcase.check(io.github.jcondedata.aliveworkplace.story.ProfessorsThesis.onCatch(level, player, info), "the Snorunt caught in the snow counts");
				}, 50),
				new Step("03_published", -1, 0, (level, player) -> {
					var s = io.github.jcondedata.aliveworkplace.story.Arcs.running(level, STATION);
					if (s != null) {
						io.github.jcondedata.aliveworkplace.story.Arcs.next(level, STATION);
						io.github.jcondedata.aliveworkplace.story.Arcs.next(level, STATION);
						var thessaly = io.github.jcondedata.aliveworkplace.story.ProfessorsThesis.thessaly(level, s);
						if (thessaly != null) {
							io.github.jcondedata.aliveworkplace.trainer.Trainers.battleOver(level.getServer(), thessaly.getUUID(), player.getUUID(), true);
						}
					}
					ItemStack book = ItemStack.EMPTY;
					for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
						if (player.getInventory().getItem(i).is(Items.WRITTEN_BOOK)) {
							book = player.getInventory().getItem(i).copy();
						}
					}
					player.setItemInHand(InteractionHand.MAIN_HAND, book);
					if (!book.isEmpty()) {
						player.openItemGui(book, InteractionHand.MAIN_HAND);
					}
					Showcase.check(!book.isEmpty() && io.github.jcondedata.aliveworkplace.story.Arcs.running(level, STATION) == null,
						"the thesis is published as a book");
				}, 60)),
			(level, player) -> true));
		SCREENS.put("legend_ranger", new Screen("the Pokémon Ranger in a red vest and capture styler by the hall, an Alpha calmed with sparkles, and a wild Pikachu befriended into the owner's pasture",
			new Vec3(0.5, -57.5, 7.5), new Vec3(0.5, -59.5, -1.5),
			(level, player) -> {
				// ROADMAP 29.22: a Village Hall owned by the player with the Pokémon Ranger settled beside it, a wild
				// level-60 Machamp (the Alpha stand-in) to the east, a Pasture Block to the west and a wild Pikachu by it.
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getGameProfile().getName());
				net.minecraft.world.level.block.Block pasture = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.PASTURE_BLOCK);
				BlockState bottom = pastureHalf(ScreenshotHarness.standing(pasture.defaultBlockState()), "bottom");
				BlockPos pen = STATION.offset(-4, 0, 1);
				level.setBlockAndUpdate(pen, bottom);
				level.setBlockAndUpdate(pen.above(), pastureHalf(bottom, "top"));
				var legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(io.github.jcondedata.aliveworkplace.legend.PokemonRanger.ID).orElseThrow();
				subject = EntityType.VILLAGER.spawn(level, STATION.south(2), MobSpawnType.COMMAND);
				subject.setNoAi(true);
				subject.setYRot(0);
				subject.setYHeadRot(0);
				io.github.jcondedata.aliveworkplace.legend.Legends.make(level, subject, legend, "showcase");
				io.github.jcondedata.aliveworkplace.legend.LegendPowers.forget();
				WildPokemon.spawnStill(level, "machamp level=60", STATION.getX() + 4.5, STATION.getY(), STATION.getZ() + 2.5);
				WildPokemon.spawnStill(level, "pikachu level=10", STATION.getX() - 2.5, STATION.getY(), STATION.getZ() + 3.5);
				level.setDayTime(level.getDayTime() / 24000L * 24000L + 2000L);
			},
			List.of(new Step("01_ranger", -1, 0, (level, player) ->
					Showcase.check(io.github.jcondedata.aliveworkplace.legend.PokemonRanger.working(subject), "the Ranger settled by the hall"), 40),
				new Step("02_alpha_calmed", -1, 0, (level, player) -> {
					// The Ranger beside the Machamp: sparkles and a calm chime, and it's calmed for good
					var alpha = io.github.jcondedata.aliveworkplace.legend.PokemonRanger.alphaToCalm(level, STATION, subject).orElseThrow();
					subject.teleportTo(alpha.getX() - 1.5, alpha.getY(), alpha.getZ());
					io.github.jcondedata.aliveworkplace.legend.PokemonRanger.calm(level, STATION, subject, alpha);
					Showcase.check(io.github.jcondedata.aliveworkplace.legend.PokemonRanger.calmed(alpha), "the Alpha was calmed");
				}, 40),
				new Step("03_befriended", -1, 0, (level, player) -> {
					// The Ranger leads the wild Pikachu to the Pasture Block: the player's Pokémon now, in the pasture
					BlockPos pen = STATION.offset(-4, 0, 1);
					net.minecraft.world.entity.Entity pikachu = WildPokemon.nearest(level, STATION, 8, "pikachu");
					subject.teleportTo(pen.getX() + 1.5, pen.getY(), pen.getZ() + 1.5);
					var result = io.github.jcondedata.aliveworkplace.legend.PokemonRanger.befriend(level, STATION, subject, pikachu, pen);
					Showcase.check(result == io.github.jcondedata.aliveworkplace.legend.WildPokemon.Befriended.PASTURED, "the Pikachu went into the pasture: " + result);
				}, 60)),
			(level, player) -> true));
		SCREENS.put("legend_sage", new Screen("the Old Sage's hut, a riddle asked and a wrong answer refused with a hint, and the Ancient Lore tab",
			new Vec3(0.5, -53.0, 13.5), new Vec3(0.5, -59.5, -6.5),
			(level, player) -> {
				// ROADMAP 29.14: the hermit's hut set down for the player's village, with the Old Sage waiting in it.
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getGameProfile().getName());
				subject = io.github.jcondedata.aliveworkplace.legend.OldSage.placeHut(level, STATION, new BlockPos(0, -61, -8),
					net.minecraft.util.RandomSource.create(14));
				Showcase.check(subject != null, "the hermit's hut was set down with the Old Sage in it");
				if (subject != null) {
					ModAttachments.SAGE_RIDDLES.set(subject, new io.github.jcondedata.aliveworkplace.legend.OldSage.Riddles(List.of(2, 4, 8), 0, 0));
				}
				level.setDayTime(level.getDayTime() / 24000L * 24000L + 6000L);
			},
			List.of(new Step("01_hermit_hut", -1, 6, (level, player) ->
					Showcase.check(subject != null && io.github.jcondedata.aliveworkplace.legend.LegendSites.isCaptive(subject), "the Sage waits in the hut"), 40),
				new Step("02_riddle", -1, 6, (level, player) -> {
					// The player asks for the riddle, then offers a stick twice: a shake of the head, then a hint.
					player.teleportTo(subject.getX(), subject.getY(), subject.getZ() + 3.5);
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
					io.github.jcondedata.aliveworkplace.legend.LegendSites.use(player, subject, InteractionHand.MAIN_HAND);
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
					io.github.jcondedata.aliveworkplace.legend.LegendSites.use(player, subject, InteractionHand.MAIN_HAND);
					io.github.jcondedata.aliveworkplace.legend.LegendSites.use(player, subject, InteractionHand.MAIN_HAND);
					Showcase.check(ModAttachments.SAGE_RIDDLES.get(subject).misses() == 2 && ModAttachments.SAGE_RIDDLES.get(subject).solved() == 0,
						"two wrong answers refused (the hint is given)");
				}, 60),
				new Step("03_ancient_lore_tab", io.github.jcondedata.aliveworkplace.research.ResearchScreen.TOPIC_SLOTS[1], 6, (level, player) -> {
					// The Sage settled in the village works the Ancient Lore: Old Tongues done, Star Charts I, Herb Lore in progress.
					var legend = io.github.jcondedata.aliveworkplace.legend.Legends.get(io.github.jcondedata.aliveworkplace.legend.OldSage.ID).orElseThrow();
					Villager sage = EntityType.VILLAGER.spawn(level, STATION.south(2).east(2), MobSpawnType.COMMAND);
					sage.setNoAi(true);
					io.github.jcondedata.aliveworkplace.legend.Legends.make(level, sage, legend, "showcase");
					var tree = io.github.jcondedata.aliveworkplace.research.ResearchTrees.get(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("ancient_lore")).orElseThrow();
					var state = new io.github.jcondedata.aliveworkplace.research.Research.State(Map.of(tree.key() + "/old_tongues", 1, tree.key() + "/star_charts", 1),
						java.util.Optional.empty(), 0, false);
					state = io.github.jcondedata.aliveworkplace.research.ResearchTrees.choose(state, tree, tree.topic("herb_lore").orElseThrow());
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					hall.setResearch(io.github.jcondedata.aliveworkplace.research.ResearchTrees.withProgress(state, tree, 30));
					io.github.jcondedata.aliveworkplace.research.ResearchScreen.openForLegend(player, sage);
					Showcase.check(player.containerMenu instanceof ChoiceMenu m
						&& m.icon(io.github.jcondedata.aliveworkplace.research.ResearchScreen.TAB_SLOTS[1]).is(Items.LECTERN), "the Ancient Lore opened on its own tab");
				}, 40)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		SCREENS.put("hall_quests", new Screen("the Village Hall's quests, advice, village map, mercenaries and festival opened", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				guard(level, new BlockPos(4, -60, -3), new ItemStack(Items.IRON_SWORD));
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setQuests(List.of(
					new io.github.jcondedata.aliveworkplace.hall.VillageQuests.Quest(java.util.UUID.randomUUID(),
						io.github.jcondedata.aliveworkplace.hall.VillageQuests.Kind.BRING, "minecraft:bread", 16, 4, 5, level.getGameTime(), "Ana",
						java.util.Optional.empty()),
					new io.github.jcondedata.aliveworkplace.hall.VillageQuests.Quest(java.util.UUID.randomUUID(),
						io.github.jcondedata.aliveworkplace.hall.VillageQuests.Kind.SLAY, "minecraft:air", 6, 1, 8, level.getGameTime(), "",
						java.util.Optional.empty())));
				player.getInventory().add(new ItemStack(Items.BREAD, 12));
			},
			List.of(new Step("01_hall_quests", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUEST_SLOTS[0], 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUESTS, player);
						Showcase.check(!m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUEST_SLOTS[1]).isEmpty(), "the quests page shows both quests");
					}
				}, 30),
				new Step("02_hall_advice", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.ADVICE, player);
					}
				}, 30),
				new Step("03_hall_map", -1, 0, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.MAP, player);
					}
					player.closeContainer();
					// Hold the map the hall handed over, so the picture shows the village drawn on it.
					var inventory = player.getInventory();
					for (int s = 0; s < inventory.getContainerSize(); s++) {
						if (inventory.getItem(s).is(Items.FILLED_MAP)) {
							ItemStack map = inventory.removeItemNoUpdate(s);
							player.setItemInHand(InteractionHand.MAIN_HAND, map);
							break;
						}
					}
					Showcase.check(player.getMainHandItem().is(Items.FILLED_MAP), "the hall's MAP button handed over a village map");
					player.teleportTo(level, player.getX(), player.getY(), player.getZ(), player.getYRot(), 70f); // (look down at it)
				}, 30),
				new Step("04_hall_mercenaries", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.MERCENARIES, 6, (level, player) ->
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION), 30),
				step("05_hall_festival", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FESTIVAL, 6)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU)); // the hall's own screen (30.4a)
		// The waystone network on the hall's Map (ROADMAP 41.2, filmed with Waystones): three villages of the player's, each
		// with its Waystone Plaza as the builder leaves it: Ashford (here, its stone discovered), Brookmere to the north-east
		// (not discovered yet) and Coldwater to the west (its stone broken). The hall's Map button lists all three.
		SCREENS.put("hall_waystones", new Screen("the hall's Map listed the waystone network: Ashford here (discovered), Brookmere to the north-east "
			+ "(not discovered yet) and Coldwater to the west (its waystone broken)", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				Showcase.check(io.github.jcondedata.aliveworkplace.hall.VillageWaystones.EXTENSION.present(), "Waystones is loaded (the waystones extension is filled)");
				BlockPos ashford = waystoneVillage(level, player, STATION, "Ashford", STATION.offset(-12, 0, -12));
				BlockPos brookmere = waystoneVillage(level, player, STATION.offset(140, 0, -110), "Brookmere", STATION.offset(128, 0, -122));
				BlockPos coldwater = waystoneVillage(level, player, STATION.offset(-180, 0, 10), "Coldwater", STATION.offset(-192, 0, -2));
				BlockPos stone = io.github.jcondedata.aliveworkplace.hall.VillageWaystones.stoneOf(level, ashford).orElse(null);
				Showcase.check(stone != null && io.github.jcondedata.aliveworkplace.hall.VillageWaystones.stoneOf(level, brookmere).isPresent()
					&& io.github.jcondedata.aliveworkplace.hall.VillageWaystones.stoneOf(level, coldwater).isPresent(), "each village raised its waystone");
				// The player discovered Ashford's stone (using it, as Waystones does); Coldwater's is broken.
				if (stone != null && io.github.jcondedata.aliveworkplace.hall.VillageWaystones.EXTENSION.present()) {
					ShowcaseWaystones.discover(player, level, stone); // (its own class: Waystones' classes load only here)
				}
				io.github.jcondedata.aliveworkplace.hall.VillageWaystones.stoneOf(level, coldwater).ifPresent(s -> {
					level.setBlockAndUpdate(s.above(), Blocks.AIR.defaultBlockState());
					level.setBlockAndUpdate(s, Blocks.AIR.defaultBlockState());
				});
			},
			List.of(new Step("01_waystone_plaza", -1, 0, (level, player) -> {
					// B532: first the village's Waystone Plaza by the hall, its stone named after the village, then the Map.
					player.closeContainer();
					BlockPos stone = io.github.jcondedata.aliveworkplace.hall.VillageWaystones.stoneOf(level, STATION).orElse(null);
					Showcase.check(stone != null && io.github.jcondedata.aliveworkplace.hall.VillageWaystones.standing(level, STATION),
						"Ashford's plaza stone stands as a working waystone (" + stone + ")");
					if (stone != null) {
						Vec3 eye = Vec3.atBottomCenterOf(stone).add(3.5, 1.6, 4.5);
						Vec3 look = Vec3.atBottomCenterOf(stone).add(0, 1.2, 0).subtract(eye);
						float yaw = (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90);
						float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
						player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
					}
				}, 30),
				new Step("02_hall_waystones", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.MAP, 6, (level, player) -> {
				io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
				List<io.github.jcondedata.aliveworkplace.hall.VillageWaystones.Village> network = io.github.jcondedata.aliveworkplace.hall.VillageWaystones.network(level, STATION, player);
				Showcase.check(network.size() == 3 && network.get(0).state() == io.github.jcondedata.aliveworkplace.hall.VillageWaystones.State.DISCOVERED
					&& network.stream().anyMatch(v -> v.state() == io.github.jcondedata.aliveworkplace.hall.VillageWaystones.State.UNDISCOVERED)
					&& network.stream().anyMatch(v -> v.state() == io.github.jcondedata.aliveworkplace.hall.VillageWaystones.State.BROKEN),
					"the network: Ashford discovered, Brookmere not yet, Coldwater broken (" + network + ")");
			}, 40)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		// The quest journal (ROADMAP 31.3): the four tabs, a tracked quest's bar on screen, a quest map in hand.
		SCREENS.put("quest_journal", new Screen("the quest journal's four tabs, a tracked quest's bar at the top of the screen and a quest map in hand",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				long now = level.getGameTime();
				long due = now + 3 * io.github.jcondedata.aliveworkplace.hall.VillageNeeds.DAY;
				var slay = new io.github.jcondedata.aliveworkplace.story.Quest(java.util.UUID.randomUUID(), io.github.jcondedata.aliveworkplace.AliveWorkplace.id("daily/slay"),
					"hall", java.util.Optional.empty(), "Ana", now, due, List.of(new io.github.jcondedata.aliveworkplace.story.Objectives.Kill("monster", 6, false)),
					new int[] {2}, List.of(new io.github.jcondedata.aliveworkplace.story.Rewards.Money_(8, 0, 0, false)));
				var spot = io.github.jcondedata.aliveworkplace.story.Places.point(STATION.offset(120, 0, -80));
				var scout = new io.github.jcondedata.aliveworkplace.story.Quest(java.util.UUID.randomUUID(), io.github.jcondedata.aliveworkplace.AliveWorkplace.id("daily/scout"),
					"hall", java.util.Optional.empty(), "Bram", now, due, List.of(new io.github.jcondedata.aliveworkplace.story.Objectives.Reach(spot, 16),
					new io.github.jcondedata.aliveworkplace.story.Objectives.Bring("minecraft:iron_ingot", 4, "hall", java.util.Optional.empty())),
					new int[] {0, 0}, List.of(new io.github.jcondedata.aliveworkplace.story.Rewards.MapReward(spot)));
				io.github.jcondedata.aliveworkplace.story.Stories.post(level, STATION, slay);
				io.github.jcondedata.aliveworkplace.story.Stories.post(level, STATION, scout);
				SCENE_QUEST[0] = slay.id;
			},
			List.of(new Step("01_journal_tabs", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUEST_SLOTS[1], 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUESTS, player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.QuestJournal.Tab.BOUNTIES.slot).is(Items.CROSSBOW)
							&& !m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUEST_SLOTS[1]).isEmpty(), "the journal shows its four tabs and both quests");
					}
				}, 30),
				new Step("02_journal_tracked", -1, 0, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUESTS, player);
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUEST_SLOTS[0], player); // a click tracks the slaying quest
					}
					player.closeContainer();
					Showcase.check(io.github.jcondedata.aliveworkplace.story.QuestTracker.isTracked(player, SCENE_QUEST[0])
						&& io.github.jcondedata.aliveworkplace.story.QuestTracker.bar(player) != null, "the slaying quest is tracked and its bar is up");
				}, 40),
				new Step("03_quest_map", -1, 0, (level, player) -> {
					var spot = io.github.jcondedata.aliveworkplace.story.Places.point(STATION.offset(120, 0, -80));
					player.setItemInHand(InteractionHand.MAIN_HAND, io.github.jcondedata.aliveworkplace.story.Places.map(level, spot));
					Showcase.check(player.getMainHandItem().is(Items.FILLED_MAP), "a quest map in hand");
					player.teleportTo(level, player.getX(), player.getY(), player.getZ(), player.getYRot(), 70f); // (look down at it)
				}, 40)),
			(level, player) -> true));
		// Friendship (ROADMAP 31.5): a trade and a quest done for Dara put hearts on, shown in the action bar while the
		// player looks at her, with a puff of hearts; the hall's tooltip shows your hearts and her best friends.
		SCREENS.put("friendship", new Screen("a trade and a quest done for Dara raised the player's hearts in the action bar, and the hall's tooltip shows them",
			// B130: the camera is the player's feet and its pitch is worked out from there, while the hearts line follows the
			// look from the eyes (1.6 up): aimed at y -58.8 that look passed over Dara's head, so nobody was looked at
			new Vec3(1.5, -60, 4.5), new Vec3(1.5, -60.2, 1.5),
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				Villager dara = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 1), MobSpawnType.COMMAND);
				dara.setNoAi(true);
				dara.setYRot(0);
				dara.setYHeadRot(0);
				dara.setYBodyRot(0);
				dara.setVillagerData(dara.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
				dara.setCustomName(Component.literal("Dara"));
				subject = dara;
				ScreenshotHarness.fileRecords(level, STATION, 1); // B130: the hall's record now, so Dara's hall is found
			},
			List.of(new Step("01_trade_hearts", -1, 0, (level, player) -> {
					// The hall's POI is only registered after the staging tick: look Dara's hall up again now (B72).
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					Villager dara = subject;
					io.github.jcondedata.aliveworkplace.story.Friendship.add(dara, player, 255); // the days before
					io.github.jcondedata.aliveworkplace.story.Friendship.add(dara, java.util.UUID.nameUUIDFromBytes("Ana".getBytes()), "Ana", 430);
					dara.setTradingPlayer(player);
					dara.notifyTrade(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 1),
						new ItemStack(Items.BREAD, 6), 12, 1, 0.05f));
					dara.setTradingPlayer(null);
					Component line = io.github.jcondedata.aliveworkplace.story.Friendship.lookLine(player);
					Showcase.check(io.github.jcondedata.aliveworkplace.story.Friendship.points(dara, player.getUUID()) == 260
						&& line != null && line.getString().equals("Dara ♥♥♡♡♡♡♡♡♡♡"), "a trade with Dara: +5, two hearts in the action bar: " + line
						+ (line != null ? "" : " (points " + io.github.jcondedata.aliveworkplace.story.Friendship.points(dara, player.getUUID()) + ", looked at "
							+ io.github.jcondedata.aliveworkplace.story.Friendship.lookedAt(player) + ", her hall "
							+ io.github.jcondedata.aliveworkplace.hall.CivicEffects.hallOf(dara) + ", eye " + player.getEyePosition() + ", look " + player.getViewVector(1f) + ")"));
				}, 40),
				new Step("02_quest_hearts", -1, 0, (level, player) -> {
					Villager dara = subject;
					long now = level.getGameTime();
					var quest = new io.github.jcondedata.aliveworkplace.story.Quest(java.util.UUID.randomUUID(),
						io.github.jcondedata.aliveworkplace.AliveWorkplace.id("daily/want_bread"), "hall", java.util.Optional.empty(), "Dara", now,
						now + 3 * io.github.jcondedata.aliveworkplace.hall.VillageNeeds.DAY,
						List.of(new io.github.jcondedata.aliveworkplace.story.Objectives.Bring("minecraft:bread", 4, "hall", java.util.Optional.empty())),
						new int[1], List.of(new io.github.jcondedata.aliveworkplace.story.Rewards.Money_(3, 0, 0, false)));
					io.github.jcondedata.aliveworkplace.story.Stories.post(level, STATION, quest);
					player.getInventory().add(new ItemStack(Items.BREAD, 4));
					int given = io.github.jcondedata.aliveworkplace.story.Stories.handIn(player, STATION, quest.id);
					Component line = io.github.jcondedata.aliveworkplace.story.Friendship.lookLine(player);
					Showcase.check(given == 4 && io.github.jcondedata.aliveworkplace.story.Friendship.points(dara, player.getUUID()) == 310
						&& line != null && line.getString().equals("Dara ♥♥♥♡♡♡♡♡♡♡"), "Dara's bread quest done: +50, three hearts and a puff: " + line);
				}, 40),
				new Step("03_hall_tooltip", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					boolean shown = player.containerMenu instanceof ChoiceMenu m && m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON)
						.getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream()
						.anyMatch(l -> l.getString().equals("Your hearts: ♥♥♥♡♡♡♡♡♡♡"));
					Showcase.check(shown, "the hall's tooltip for Dara shows your hearts and her best friends");
				}, 30)),
			(level, player) -> subject != null && io.github.jcondedata.aliveworkplace.story.Friendship.points(subject, player.getUUID()) >= 310));
		// What friendship and titles are worth (ROADMAP 31.12): at a festival Bo the mason (7 hearts) and Dara the farmer
		// (6 hearts) each bring the player their family's gift, Ana (3 hearts) doesn't; then at ten hearts, and a Hero of the
		// village, Dara's 20-emerald trade costs 12 (30% for the hearts and 10% for the title).
		SCREENS.put("festival_gifts", new Screen("at the festival Bo and Dara brought their gifts (16 stone bricks, 6 apples) and Ana, with 3 hearts, didn't; then Dara's trade screen for a Hero with ten hearts: 20 emeralds cost 12",
			new Vec3(1.5, -60, 4.5), new Vec3(1.5, -58.8, 1.5),
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				classPeople.clear();
				String[] names = {"Bo", "Dara", "Ana"};
				VillagerProfession[] jobs = {VillagerProfession.MASON, VillagerProfession.FARMER, VillagerProfession.LIBRARIAN};
				for (int i = 0; i < 3; i++) {
					Villager v = EntityType.VILLAGER.spawn(level, new BlockPos(-1 + 2 * i, -60, 1), MobSpawnType.COMMAND);
					v.setNoAi(true);
					v.setYRot(0);
					v.setYHeadRot(0);
					v.setYBodyRot(0);
					v.setVillagerData(v.getVillagerData().setProfession(jobs[i]).setLevel(2));
					v.setCustomName(Component.literal(names[i]));
					classPeople.add(v);
				}
				Villager dara = classPeople.get(1);
				var offers = new net.minecraft.world.item.trading.MerchantOffers();
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 20),
					new ItemStack(Items.GOLDEN_CARROT, 3), 12, 5, 0.05f));
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 5),
					new ItemStack(Items.PUMPKIN_PIE, 4), 12, 5, 0.05f));
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 1),
					new ItemStack(Items.BREAD, 6), 12, 1, 0.05f));
				dara.setOffers(offers);
				subject = dara;
			},
			List.of(new Step("01_festival_gifts", -1, 0, (level, player) -> {
					// The hall's POI is only registered after the staging tick: look the villagers' hall up again now (B72).
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					int[] hearts = {7, 6, 3};
					for (int i = 0; i < 3; i++) {
						io.github.jcondedata.aliveworkplace.story.Friendship.add(classPeople.get(i), player, hearts[i] * 100);
					}
					int brought = io.github.jcondedata.aliveworkplace.story.FriendPerks.festivalGifts(level, STATION, player);
					int again = io.github.jcondedata.aliveworkplace.story.FriendPerks.festivalGifts(level, STATION, player);
					Showcase.check(brought == 2 && again == 0, "two friends with 6 hearts or more brought a gift, once: " + brought + ", then " + again);
				}, 50),
				new Step("02_discounted_trade", -1, 0, (level, player) -> {
					Villager dara = subject;
					io.github.jcondedata.aliveworkplace.story.Friendship.add(dara, player, 400); // ten hearts
					io.github.jcondedata.aliveworkplace.story.Reputation.add(level, STATION, player, 300); // a Hero of the village
					dara.mobInteract(player, InteractionHand.MAIN_HAND);
					var offers = dara.getOffers();
					Showcase.check(offers.get(0).getCostA().getCount() == 12 && offers.get(1).getCostA().getCount() == 3
						&& offers.get(2).getCostA().getCount() == 1, "a Hero with ten hearts: 12, 3 and 1 emeralds, got " + offers.get(0).getCostA().getCount()
						+ ", " + offers.get(1).getCostA().getCount() + ", " + offers.get(2).getCostA().getCount());
				}, 40)),
			(level, player) -> player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu));
		// The bounty board and bandit captains (ROADMAP 31.13): the board with its posters, the Wanted Poster's tooltip
		// in the journal's Bounties tab, the captain at his hideout, and the insignia handed in.
		SCREENS.put("bounty", new Screen("a Bounty Board by the hall showing two posters; the Bounties tab with Grimwald the Red's poster (name, quirk, where he was seen, the reward); Ironhide himself in iron with his band; his insignia handed in for 15 emeralds",
			new Vec3(1.5, -60, 4.5), new Vec3(1.5, -58.8, 1.5),
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION))
					.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.VILLAGE);
				level.setBlockAndUpdate(new BlockPos(2, -60, 1), ModBlocks.BOUNTY_BOARD.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.bounty.BountyBoardBlock.FACING, Direction.SOUTH));
				io.github.jcondedata.aliveworkplace.bounty.Bounties.forget(level);
			},
			List.of(new Step("01_board", -1, 0, (level, player) -> {
					var b = io.github.jcondedata.aliveworkplace.bounty.Bounties.post(level, STATION, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("ironhide"), 0, 0,
						STATION.offset(110, 0, -110));
					level.setDayTime(level.getDayTime() + 3 * io.github.jcondedata.aliveworkplace.hall.VillageNeeds.DAY);
					io.github.jcondedata.aliveworkplace.bounty.Bounties.post(level, STATION, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("hexer"), 8, 3,
						STATION.offset(-150, 0, 40));
					io.github.jcondedata.aliveworkplace.bounty.Bounties.boardPlaced(level, new BlockPos(2, -60, 1));
					Showcase.check(b != null && level.getBlockState(new BlockPos(2, -60, 1)).getValue(io.github.jcondedata.aliveworkplace.bounty.BountyBoardBlock.POSTERS) == 2,
						"two bounties posted, two posters on the board");
				}, 40),
				new Step("02_poster", io.github.jcondedata.aliveworkplace.bounty.BountyPage.ROW, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.bounty.BountyPage.openAtBoard(player, level, new BlockPos(2, -60, 1));
					Showcase.check(player.containerMenu instanceof ChoiceMenu m
						&& m.icon(io.github.jcondedata.aliveworkplace.bounty.BountyPage.ROW).is(ModItems.WANTED_POSTER), "the Bounties tab shows the poster");
				}, 40),
				new Step("03_captain", -1, 0, (level, player) -> {
					player.closeContainer();
					var b = io.github.jcondedata.aliveworkplace.bounty.Bounties.open(level, STATION).get(0);
					boolean up = io.github.jcondedata.aliveworkplace.bounty.Bounties.raise(level, b, new BlockPos(1, -60, -3), new BlockPos(1, -60, -5));
					for (var mob : io.github.jcondedata.aliveworkplace.bounty.Bounties.band(level, b)) {
						mob.setNoAi(true);
					}
					Showcase.check(up, "Grimwald the Red stands with his band");
				}, 40),
				new Step("04_claim", -1, 0, (level, player) -> {
					var b = io.github.jcondedata.aliveworkplace.bounty.Bounties.open(level, STATION).get(0);
					if (level.getEntity(b.captain) instanceof net.minecraft.world.entity.LivingEntity captain) {
						captain.hurt(level.damageSources().playerAttack(player), 1000f);
					}
					player.getInventory().add(io.github.jcondedata.aliveworkplace.bounty.Bounties.insignia(level, b));
					var claim = io.github.jcondedata.aliveworkplace.bounty.Bounties.claimAny(player, level, STATION);
					Showcase.check(claim == io.github.jcondedata.aliveworkplace.bounty.Bounties.Claim.PAID, "the insignia paid: " + claim);
				}, 40)),
			(level, player) -> true));
		// Rogue Pokémon bounties (ROADMAP 31.14, Cobblemon): the poster on the board, the huge Rogue Ursaring glowing for
		// the player who took its bounty, and the bounty paid when it's beaten.
		SCREENS.put("bounty_rogue", new Screen("a Town's Bounty Board with a poster on a Rogue Ursaring (level 50, where it was last seen, 24 emeralds); the rogue itself, half again as big and glowing for the player who took its bounty; beaten, and the bounty paid",
			new Vec3(1.5, -59, 5.5), new Vec3(0.5, -58.5, -2.5),
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				holdWonderTown(level); // B129: the hall's first round would count a Hamlet and post a Hamlet's rogue
				level.setBlockAndUpdate(new BlockPos(2, -60, 1), ModBlocks.BOUNTY_BOARD.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.bounty.BountyBoardBlock.FACING, Direction.SOUTH));
				io.github.jcondedata.aliveworkplace.bounty.Bounties.forget(level);
			},
			List.of(new Step("01_poster", io.github.jcondedata.aliveworkplace.bounty.BountyPage.ROW, 6, (level, player) -> {
					holdWonderTown(level); // the staged Town, right before the rogue's level and reward are read from it
					var b = io.github.jcondedata.aliveworkplace.bounty.Rogues.post(level, STATION, net.minecraft.resources.ResourceLocation.parse("cobblemon:ursaring"),
						new BlockPos(-1, -60, -4));
					io.github.jcondedata.aliveworkplace.bounty.Bounties.boardPlaced(level, new BlockPos(2, -60, 1));
					io.github.jcondedata.aliveworkplace.bounty.BountyPage.openAtBoard(player, level, new BlockPos(2, -60, 1));
					Showcase.check(b.name(level).getString().equals("Rogue Ursaring") && b.rogueLevel == 50 && b.reward == 24,
						"a Town posts a level-50 Rogue Ursaring for 24 emeralds: " + b.name(level).getString() + ", " + b.rogueLevel + ", " + b.reward);
					Showcase.check(player.containerMenu instanceof ChoiceMenu m
						&& m.icon(io.github.jcondedata.aliveworkplace.bounty.BountyPage.ROW).is(ModItems.WANTED_POSTER), "the Bounties tab shows the rogue's poster");
				}, 40),
				new Step("02_rogue", -1, 0, (level, player) -> {
					player.closeContainer();
					var b = io.github.jcondedata.aliveworkplace.bounty.Bounties.open(level, STATION).get(0);
					io.github.jcondedata.aliveworkplace.bounty.Bounties.take(player, level, b);
					var rogue = io.github.jcondedata.aliveworkplace.bounty.Rogues.spawn(level, b, player);
					if (rogue instanceof net.minecraft.world.entity.Mob mob) {
						mob.setNoAi(true);
						mob.setYRot(0);
						mob.setYHeadRot(0);
					}
					io.github.jcondedata.aliveworkplace.bounty.Rogues.glow(level);
					Showcase.check(rogue != null && io.github.jcondedata.aliveworkplace.bounty.Rogues.glowingFor(b).contains(player.getUUID()),
						"the Rogue Ursaring stands and glows for the player who took its bounty");
				}, 60),
				new Step("03_claim", -1, 0, (level, player) -> {
					var b = io.github.jcondedata.aliveworkplace.bounty.Bounties.open(level, STATION).get(0);
					var rogue = io.github.jcondedata.aliveworkplace.bounty.Rogues.rogue(level, b);
					boolean paid = io.github.jcondedata.aliveworkplace.bounty.Rogues.beaten(level.getServer(), b.id, player, false);
					if (rogue != null) {
						level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, rogue.getX(), rogue.getY() + 1, rogue.getZ(), 20, 0.6, 0.8, 0.6, 0.02);
						rogue.discard();
					}
					Showcase.check(paid && io.github.jcondedata.aliveworkplace.bounty.Bounties.open(level, STATION).isEmpty(), "the win paid the bounty once");
				}, 40)),
			(level, player) -> true));
		// The Bandit King's fort (ROADMAP 31.15): its three stages set down one after another, as the arc does a stage a
		// dawn: the palisade, fire and tents; the watchtowers and stable; the King's keep with his banner over the door.
		SCREENS.put("bandit_fort", new Screen("the Bandit King's fort rising a stage at a time: a palisade of sharpened logs round a yard with a fire, tents and a gate; then two watchtowers and a stable; then the King's keep with his war banner over the door, the King inside",
			new Vec3(24.5, -46, 10.5), new Vec3(0.5, -57, -20.5),
			(level, player) -> {
				for (var mob : io.github.jcondedata.aliveworkplace.guard.BanditFort.men(level, BANDIT_FORT_GROUND, 40)) {
					mob.discard();
				}
			},
			List.of(new Step("01_palisade", -1, 0, (level, player) -> {
					boolean placed = io.github.jcondedata.aliveworkplace.guard.BanditFort.place(level, banditFort(), 0, false);
					stillFortMen(level);
					Showcase.check(placed && io.github.jcondedata.aliveworkplace.guard.BanditFort.men(level, BANDIT_FORT_GROUND, 40).size() == 4,
						"the palisade, fire and tents stand, four bandits in the yard");
				}, 50),
				new Step("02_towers", -1, 0, (level, player) -> {
					boolean placed = io.github.jcondedata.aliveworkplace.guard.BanditFort.place(level, banditFort(), 1, false);
					stillFortMen(level);
					Showcase.check(placed && level.getBlockState(banditFort().at(new BlockPos(1, 3, 2))).is(net.minecraft.world.level.block.Blocks.LADDER),
						"the watchtowers (with ladders) and the stable stand");
				}, 50),
				new Step("03_keep", -1, 0, (level, player) -> {
					boolean placed = io.github.jcondedata.aliveworkplace.guard.BanditFort.place(level, banditFort(), 2, false);
					stillFortMen(level);
					long kings = io.github.jcondedata.aliveworkplace.guard.BanditFort.men(level, BANDIT_FORT_GROUND, 40).stream()
						.filter(m -> m.getTags().contains(io.github.jcondedata.aliveworkplace.guard.BanditKing.TAG)).count();
					Showcase.check(placed && kings == 1 && level.getBlockState(banditFort().at(new BlockPos(14, 4, 16))).is(net.minecraft.world.level.block.Blocks.BLACK_WALL_BANNER),
						"the keep stands with the war banner over its door, the King inside");
				}, 60)),
			(level, player) -> true));
		// The Lost Caravan, I: the wreck and the trail (ROADMAP 31.19): the three kinds of build the arc lays along the trail,
		// each set down the arc's way (ArcEffects.build: fit ground, a hump cut and a hollow filled): the settlers' wagon on its
		// side, a mark (the torn banner) with the action bar pointing on, and the raiders' stockade with its cage.
		SCREENS.put("caravan_trail", new Screen("the Lost Caravan's trail: the settlers' covered wagon on its side, a wheel broken off, crates and barrels spilled, a lantern in the grass; a mark of the trail, a torn white banner on a fence post, with 'The tracks go on to the east' over the hotbar; the raiders' stockade, a palisade round a tent, a fire, a loot chest and an iron-barred cage",
			new Vec3(-26.5, -54, -33.5), new Vec3(-16.5, -60, -22.5),
			(level, player) -> {
				for (BlockPos p : BlockPos.betweenClosed(-32, -61, -44, 38, -44, -4)) {
					level.setBlock(p, p.getY() == -61 ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState()
						: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
				}
				for (BlockPos p : BlockPos.betweenClosed(-13, -60, -19, -12, -60, -18)) { // a hump the wreck's ground is cut from
					level.setBlock(p, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				}
				io.github.jcondedata.aliveworkplace.city.PlayerBuilt.get(level).forget(new net.minecraft.world.level.levelgen.structure.BoundingBox(-32, -64, -44, 38, -44, -4));
				level.setDayTime(5000);
			},
			List.of(new Step("01_wreck", -1, 0, (level, player) -> {
					var p = caravanPiece(level, io.github.jcondedata.aliveworkplace.story.CaravanTrail.WRECK, CARAVAN_WRECK);
					aim(level, player, new Vec3(-26.5, -54, -33.5), new Vec3(-16.5, -60, -22.5));
					Showcase.check(p.placed == 1 && level.getBlockState(p.origin.offset(2, 1, 5)).is(net.minecraft.world.level.block.Blocks.WHITE_WOOL),
						"the wagon lies on its side, its canvas torn");
				}, 50),
				new Step("02_mark", -1, 0, (level, player) -> {
					var p = caravanPiece(level, io.github.jcondedata.aliveworkplace.story.CaravanTrail.MARKS.get(2), CARAVAN_MARK);
					aim(level, player, new Vec3(-2.5, -58, -27.5), new Vec3(1.5, -59.5, -21.5));
					io.github.jcondedata.aliveworkplace.mc.Chat.actionBar(player, Component.translatable("message.aliveworkplace.trail.next",
						io.github.jcondedata.aliveworkplace.story.CaravanTrail.direction(p.spot, CARAVAN_STOCKADE)).withStyle(net.minecraft.ChatFormatting.GOLD));
					Showcase.check(p.placed == 1 && level.getBlockState(p.origin.offset(1, 3, 1)).is(net.minecraft.world.level.block.Blocks.WHITE_BANNER),
						"the mark: a torn white banner on a fence post, the tracks pointing on");
				}, 40),
				new Step("03_stockade", -1, 0, (level, player) -> {
					var p = caravanPiece(level, io.github.jcondedata.aliveworkplace.story.CaravanTrail.STOCKADE, CARAVAN_STOCKADE);
					aim(level, player, new Vec3(24.5, -48, -46.5), new Vec3(24.5, -60, -24.5));
					Showcase.check(p.placed == 1 && level.getBlockState(p.origin.offset(io.github.jcondedata.aliveworkplace.story.CaravanTrail.CAGE_DOOR))
						.is(net.minecraft.world.level.block.Blocks.IRON_DOOR), "the stockade stands, its cage shut");
				}, 50)),
			(level, player) -> true));
		// The Lost Caravan, II: the story (ROADMAP 31.20): the wreck found, a mark of the trail pointing on, the carter
		// caged in the raiders' stockade (set down by the arc's own build, which cages him and fills the chest with the
		// lost goods) and the cage opened, then the carter following the player home.
		SCREENS.put("lost_caravan", new Screen("the Lost Caravan: the settlers' wagon on its side with 'The caravan's wreck: tracks lead away from the road' over the hotbar; a torn banner on the trail with the tracks pointing on; Tobin the carter behind the iron gate of the raiders' cage, the gate swung open and 'You came for me! Take me home' in chat; Tobin walking after the player out of the stockade",
			new Vec3(-26.5, -54, -33.5), new Vec3(-16.5, -60, -22.5),
			(level, player) -> {
				for (BlockPos p : BlockPos.betweenClosed(-32, -61, -44, 38, -44, -4)) {
					level.setBlock(p, p.getY() == -61 ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState()
						: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
				}
				for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-32, -64, -44, 38, -44, -4))) {
					v.discard();
				}
				io.github.jcondedata.aliveworkplace.city.PlayerBuilt.get(level).forget(new net.minecraft.world.level.levelgen.structure.BoundingBox(-32, -64, -44, 38, -44, -4));
				level.setDayTime(5000);
			},
			List.of(new Step("01_wreck", -1, 0, (level, player) -> {
					var p = caravanPiece(level, io.github.jcondedata.aliveworkplace.story.CaravanTrail.WRECK, CARAVAN_WRECK);
					aim(level, player, new Vec3(-26.5, -54, -33.5), new Vec3(-16.5, -60, -22.5));
					io.github.jcondedata.aliveworkplace.mc.Chat.actionBar(player, Component.translatable("message.aliveworkplace.lost_caravan.wreck_found")
						.withStyle(net.minecraft.ChatFormatting.GOLD));
					Showcase.check(p.placed == 1, "the wreck lies by the road");
				}, 50),
				new Step("02_trail", -1, 0, (level, player) -> {
					var p = caravanPiece(level, io.github.jcondedata.aliveworkplace.story.CaravanTrail.MARKS.get(2), CARAVAN_MARK);
					aim(level, player, new Vec3(-2.5, -58, -27.5), new Vec3(1.5, -59.5, -21.5));
					io.github.jcondedata.aliveworkplace.mc.Chat.actionBar(player, Component.translatable("message.aliveworkplace.trail.next",
						io.github.jcondedata.aliveworkplace.story.CaravanTrail.direction(p.spot, CARAVAN_STOCKADE)).withStyle(net.minecraft.ChatFormatting.GOLD));
					Showcase.check(p.placed == 1, "a mark of the trail, the tracks pointing on");
				}, 40),
				new Step("03_freed", -1, 0, (level, player) -> {
					var s = new io.github.jcondedata.aliveworkplace.story.ArcState(io.github.jcondedata.aliveworkplace.story.LostCaravanStory.ARC.toString());
					io.github.jcondedata.aliveworkplace.story.LostCaravanStory.hold(s, new BlockPos(400, -60, 0),
						List.of(new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.BREAD, 12)));
					var p = new io.github.jcondedata.aliveworkplace.story.ArcState.ArcPlace(io.github.jcondedata.aliveworkplace.story.LostCaravanStory.STOCKADE,
						CARAVAN_STOCKADE, List.of(io.github.jcondedata.aliveworkplace.story.CaravanTrail.STOCKADE.toString()), 0);
					s.places.add(p);
					io.github.jcondedata.aliveworkplace.story.ArcEffects.build(level, s, p); // (the arc's own build fills the chest with the lost goods)
					BlockPos door = p.origin.offset(io.github.jcondedata.aliveworkplace.story.CaravanTrail.CAGE_DOOR);
					// The arc's carter belongs to a running arc (this scene's isn't one, so he leaves as he loads): one stands in for him.
					Villager tobin = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
					if (tobin != null) {
						tobin.moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 2.5, 0f, 0f);
						tobin.setCustomName(io.github.jcondedata.aliveworkplace.people.Names.name(65)); // Tobin
						tobin.setCustomNameVisible(true);
						tobin.addTag(LOST_CARAVAN_TOBIN);
						tobin.setNoAi(true);
						level.addFreshEntity(tobin);
					}
					var chest = level.getBlockEntity(p.origin.offset(io.github.jcondedata.aliveworkplace.story.CaravanTrail.LOOT_CHEST));
					for (BlockPos d : List.of(door, door.above())) {
						var st = level.getBlockState(d);
						if (st.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)) {
							level.setBlock(d, st.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true), 2);
						}
					}
					aim(level, player, Vec3.atCenterOf(door).add(0.5, 1.4, -4.5), Vec3.atCenterOf(door).add(0, 0, 2));
					if (tobin != null) {
						io.github.jcondedata.aliveworkplace.mc.Chat.chat(player, Component.translatable("message.aliveworkplace.arc.says", tobin.getDisplayName(),
							Component.translatable("message.aliveworkplace.lost_caravan.freed")).withStyle(net.minecraft.ChatFormatting.WHITE));
					}
					Showcase.check(tobin != null && level.getBlockState(door).is(net.minecraft.world.level.block.Blocks.IRON_DOOR)
						&& chest instanceof net.minecraft.world.Container box && box.countItem(Items.OAK_LOG) == 32,
						"Tobin in the raiders' cage, its gate open, the lost goods in the raiders' chest");
				}, 50),
				new Step("04_home", -1, 0, (level, player) -> {
					Villager tobin = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-32, -64, -44, 38, -44, -4),
						v -> v.getTags().contains(LOST_CARAVAN_TOBIN)).stream().findFirst().orElse(null);
					aim(level, player, new Vec3(CARAVAN_STOCKADE.getX() + 0.5, -58.4, CARAVAN_STOCKADE.getZ() - 14.5), new Vec3(CARAVAN_STOCKADE.getX() + 2.5, -60, CARAVAN_STOCKADE.getZ() + 4.5));
					if (tobin != null) {
						tobin.setNoAi(false);
						tobin.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
							new net.minecraft.world.entity.ai.memory.WalkTarget(new net.minecraft.world.entity.ai.behavior.EntityTracker(player, false), 0.6f, 2));
					}
					Showcase.check(tobin != null, "Tobin follows the player out of the stockade, home");
				}, 80)),
			(level, player) -> true));
		// The eight reforms as stories (ROADMAP 31.22): The Shift Bell, from the Toolsmith's ask to the bell ringing the work
		// day in. Long Shifts is in force; the hall's round starts the story, the Toolsmith asks, M30's three steps are
		// handed in a day apart, and the ending sets the Shift Bell on the grass beside the hall; at the start of the work
		// day it rings.
		SCREENS.put("reform_story", new Screen("The Shift Bell as a story: the Toolsmith by the hall asking for a bell to ring the long shifts in and out (his words in chat); the three steps (clocks, gold, bread) handed in; the ending: the Shift Bell, a bell under a little roof between two dark oak posts on the grass beside the hall, ringing at the start of the work day",
			new Vec3(0.5, -57.5, -1.5), new Vec3(1.5, -59.5, -13.5),
			(level, player) -> {
				for (BlockPos p : BlockPos.betweenClosed(-20, -61, -34, 20, -50, 4)) {
					level.setBlock(p, p.getY() == -61 ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState()
						: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
				}
				for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-90, -64, -100, 90, -40, 70))) {
					v.discard();
				}
				level.setBlock(REFORM_HALL, io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
				level.setBlock(REFORM_HALL.offset(4, 0, 1), net.minecraft.world.level.block.Blocks.SMITHING_TABLE.defaultBlockState(), 3);
				Villager smith = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
				if (smith != null) {
					smith.moveTo(REFORM_HALL.getX() + 2.5, REFORM_HALL.getY(), REFORM_HALL.getZ() + 3.5, 180f, 0f);
					smith.setVillagerData(smith.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.TOOLSMITH).setLevel(2));
					smith.setVillagerXp(20);
					smith.setNoAi(true);
					smith.addTag(REFORM_SMITH);
					level.addFreshEntity(smith);
				}
				level.setDayTime(1000);
			},
			List.of(new Step("01_ask", -1, 0, (level, player) -> {
					var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(REFORM_HALL);
					String edict = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("long_shifts").toString();
					entity.setReforms(List.of());
					entity.setEdicts(List.of(new io.github.jcondedata.aliveworkplace.hall.Edicts.InForce(edict,
						io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level))));
					io.github.jcondedata.aliveworkplace.story.Arcs.stopAll(level, REFORM_HALL);
					boolean auto = io.github.jcondedata.aliveworkplace.story.Arcs.AUTO;
					io.github.jcondedata.aliveworkplace.story.Arcs.AUTO = true;
					io.github.jcondedata.aliveworkplace.hall.Reforms.round(level, REFORM_HALL, entity);
					io.github.jcondedata.aliveworkplace.story.Arcs.AUTO = auto;
					Villager smith = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(REFORM_HALL).inflate(8),
						v -> v.getTags().contains(REFORM_SMITH)).stream().findFirst().orElse(null);
					aim(level, player, new Vec3(REFORM_HALL.getX() + 2.5, REFORM_HALL.getY() + 1.6, REFORM_HALL.getZ() + 7.5),
						new Vec3(REFORM_HALL.getX() + 2.5, REFORM_HALL.getY() + 1.4, REFORM_HALL.getZ() + 3.5));
					boolean talked = smith != null && io.github.jcondedata.aliveworkplace.story.ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith)
						== net.minecraft.world.InteractionResult.SUCCESS;
					Showcase.check(io.github.jcondedata.aliveworkplace.story.Arcs.side(level, REFORM_HALL).size() == 1 && talked,
						"Long Shifts in force: The Shift Bell begins and the Toolsmith asks for it");
				}, 60),
				new Step("02_steps", -1, 0, (level, player) -> {
					var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(REFORM_HALL);
					long day = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
					for (int n = 0; n < 3; n++) {
						if (n > 0) {
							level.setDayTime((day + n - 1) * 24000L + 1000);
							io.github.jcondedata.aliveworkplace.story.Arcs.round(level, REFORM_HALL, entity);
						}
						for (var q : io.github.jcondedata.aliveworkplace.story.Stories.open(level, REFORM_HALL)) {
							if (q.arc != null && q.objectives.get(0) instanceof io.github.jcondedata.aliveworkplace.story.Objectives.Bring b) {
								var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(b.item()));
								for (int left = b.count(); left > 0; left -= 64) {
									player.getInventory().add(new ItemStack(item, Math.min(64, left)));
								}
								io.github.jcondedata.aliveworkplace.story.Stories.handIn(player, REFORM_HALL, q.id);
							}
						}
					}
					aim(level, player, new Vec3(0.5, -57.5, -1.5), new Vec3(1.5, -59.5, -13.5));
					Showcase.check(io.github.jcondedata.aliveworkplace.hall.Reforms.reformed(entity, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("long_shifts").toString()),
						"the three steps handed in: Long Shifts reformed");
				}, 60),
				new Step("03_bell", -1, 0, (level, player) -> {
					BlockPos bell = io.github.jcondedata.aliveworkplace.story.ReformStories.bell(level, REFORM_HALL);
					if (bell != null) {
						aim(level, player, Vec3.atCenterOf(bell).add(3.5, 0.5, 5.5), Vec3.atCenterOf(bell).add(0, -1, 0));
					}
					Showcase.check(bell != null && level.getBlockState(bell).is(net.minecraft.world.level.block.Blocks.BELL), "the Shift Bell stands beside the hall");
				}, 40),
				new Step("04_ringing", -1, 0, (level, player) -> {
					BlockPos bell = io.github.jcondedata.aliveworkplace.story.ReformStories.bell(level, REFORM_HALL);
					int rings = io.github.jcondedata.aliveworkplace.story.ReformStories.RINGS.get();
					level.setDayTime((level.getDayTime() / 24000L + 1) * 24000L + io.github.jcondedata.aliveworkplace.story.ReformStories.RING_START);
					io.github.jcondedata.aliveworkplace.story.ReformStories.tick(level);
					if (bell != null) {
						aim(level, player, Vec3.atCenterOf(bell).add(3.5, 0.5, 5.5), Vec3.atCenterOf(bell).add(0, -1, 0));
					}
					Showcase.check(io.github.jcondedata.aliveworkplace.story.ReformStories.RINGS.get() > rings, "the bell rings the work day in");
				}, 50)),
			(level, player) -> true));
		// The Sickness, I: eight far herbs (ROADMAP 31.17): a 5 x 5 pad of each herb's ground in a field of coarse dirt (no
		// herb's ground), a herb_patch planted on each, the Glowcap's under a leaf canopy; then the Three-Herb Tonic in hand.
		SCREENS.put("herbs", new Screen("eight patches of the far herbs, each on its own ground: Sunthistle on red sand, Frostbloom on snow, Peakmoss on stone, Glowcap on podzol under leaves (in front); Sea Lavender on gravel, Mirewort on mud, Jungle Orchid and Petal Balm on grass (behind); then the Three-Herb Tonic in hand",
			new Vec3(0.5, -58.4, 4.5), new Vec3(0.5, -60.6, -12),
			(level, player) -> {
				for (BlockPos p : BlockPos.betweenClosed(-18, -61, -21, 19, -54, 0)) {
					level.setBlock(p, p.getY() == -61 ? net.minecraft.world.level.block.Blocks.COARSE_DIRT.defaultBlockState()
						: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
				}
				for (int i = 0; i < HERB_PADS.length; i++) {
					BlockPos middle = herbPad(i);
					for (BlockPos p : BlockPos.betweenClosed(middle.offset(-2, -1, -2), middle.offset(2, -1, 2))) {
						level.setBlock(p, ((net.minecraft.world.level.block.Block) HERB_PADS[i][1]).defaultBlockState(), 2);
					}
				}
				BlockPos glow = herbPad(3);
				for (BlockPos p : BlockPos.betweenClosed(glow.offset(-3, 3, -3), glow.offset(3, 3, 3))) { // the Glowcap's shade
					level.setBlock(p, net.minecraft.world.level.block.Blocks.DARK_OAK_LEAVES.defaultBlockState()
						.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
				}
				level.setDayTime(6000);
			},
			List.of(new Step("01_patches", -1, 0, (level, player) -> {
					var random = net.minecraft.util.RandomSource.create(3117);
					StringBuilder counts = new StringBuilder();
					boolean all = true;
					for (int i = 0; i < HERB_PADS.length; i++) {
						var herb = (io.github.jcondedata.aliveworkplace.herb.HerbBlock) HERB_PADS[i][0];
						int n = io.github.jcondedata.aliveworkplace.herb.Herbs.patch(level, herbPad(i), herb, random);
						counts.append(' ').append(n);
						all &= n >= io.github.jcondedata.aliveworkplace.herb.Herbs.LEAST && n <= io.github.jcondedata.aliveworkplace.herb.Herbs.MOST;
					}
					Showcase.check(all, "5 to 9 of each herb planted on its pad:" + counts);
				}, 50),
				new Step("02_tonic", -1, 0, (level, player) -> {
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.THREE_HERB_TONIC));
					Showcase.check(player.getMainHandItem().is(ModItems.THREE_HERB_TONIC), "the Three-Herb Tonic in hand");
				}, 40)),
			(level, player) -> true));
		// The Sickness, II: the Grey Cough (ROADMAP 31.18): eight villagers, four of them coughing grey; a patch of Sea
		// Lavender, one of the far herbs, on its gravel; the nurse brewing the Three-Herb Tonic at her brewing stand; then
		// the cure going round, villager by villager, until everyone is well.
		SCREENS.put("sickness", new Screen("the Grey Cough: four of eight villagers coughing grey smoke; a patch of Sea Lavender, a far herb, on its gravel; Nurse Ada brewing the Three-Herb Tonic at her brewing stand (Brewing the cure 40%); then the tonic going round until everyone is well",
			new Vec3(0.5, -58.4, 4.5), new Vec3(0.5, -60.6, -12),
			(level, player) -> {
				for (BlockPos p : BlockPos.betweenClosed(-18, -61, -21, 19, -54, 0)) {
					level.setBlock(p, p.getY() == -61 ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState()
						: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
				}
				for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-24, -64, -28, 24, -50, 8))) {
					v.discard();
				}
				for (BlockPos p : BlockPos.betweenClosed(-10, -61, -15, -6, -61, -11)) {
					level.setBlock(p, net.minecraft.world.level.block.Blocks.GRAVEL.defaultBlockState(), 2);
				}
				level.setBlock(SICKNESS_STAND, net.minecraft.world.level.block.Blocks.BREWING_STAND.defaultBlockState(), 2);
				level.setDayTime(6000);
			},
			List.of(new Step("01_coughing", -1, 0, (level, player) -> {
					int[][] at = {{-5, -6}, {-2, -7}, {1, -6}, {4, -7}, {-4, -10}, {-1, -9}, {2, -10}, {5, -9}};
					for (int i = 0; i < at.length; i++) {
						Villager v = EntityType.VILLAGER.spawn(level, new BlockPos(at[i][0], -60, at[i][1]), MobSpawnType.COMMAND);
						v.setNoAi(true);
						v.setYRot(0);
						v.setYHeadRot(0);
						v.setCustomName(net.minecraft.network.chat.Component.literal(SICKNESS_NAMES[i]));
						v.addTag(SICKNESS_TAG);
						if (i % 2 == 0) {
							io.github.jcondedata.aliveworkplace.people.Sickness.greyCough(level, v);
						}
					}
					Villager nurse = EntityType.VILLAGER.spawn(level, SICKNESS_STAND.offset(-1, 0, 0), MobSpawnType.COMMAND);
					nurse.setNoAi(true);
					nurse.setYRot(0);
					nurse.setYHeadRot(0);
					nurse.setVillagerData(nurse.getVillagerData().setProfession(ModVillagers.NURSE).setLevel(2));
					nurse.setVillagerXp(70);
					nurse.setCustomName(net.minecraft.network.chat.Component.literal("Nurse Ada"));
					nurse.addTag(SICKNESS_NURSE);
					int n = io.github.jcondedata.aliveworkplace.herb.Herbs.patch(level, new BlockPos(-8, -60, -13), ModBlocks.SEA_LAVENDER,
						net.minecraft.util.RandomSource.create(3118));
					Showcase.check(sicknessCough(level) == 4, "four of the eight villagers cough the Grey Cough");
					Showcase.check(n >= io.github.jcondedata.aliveworkplace.herb.Herbs.LEAST, "a patch of " + n + " Sea Lavender, a far herb, on its gravel");
				}, 60),
				new Step("02_brewing", -1, 0, (level, player) -> {
					Villager nurse = sicknessNurse(level);
					if (nurse != null) {
						io.github.jcondedata.aliveworkplace.work.WorkerStatus.set(nurse, net.minecraft.network.chat.Component.translatable("message.aliveworkplace.sickness.status.brewing"), 0.4f,
							net.minecraft.network.chat.Component.translatable("message.aliveworkplace.sickness.status.brewing_line"));
						level.sendParticles(net.minecraft.core.particles.ParticleTypes.EFFECT, SICKNESS_STAND.getX() + 0.5, SICKNESS_STAND.getY() + 1.1, SICKNESS_STAND.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.01);
					}
					sicknessCough(level);
					Showcase.check(nurse != null && io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(nurse, level.getGameTime()) != null, "Nurse Ada brewing the cure at her stand");
				}, 50),
				new Step("03_cure_round", -1, 0, (level, player) -> {
					Showcase.check(sicknessCure(level, 2) == 2, "the tonic goes round: two villagers well, two still coughing");
				}, 60),
				new Step("04_all_well", -1, 0, (level, player) -> {
					sicknessCure(level, 4);
					Villager nurse = sicknessNurse(level);
					if (nurse != null) {
						nurse.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
						nurse.teleportTo(SICKNESS_STAND.getX() - 0.5, SICKNESS_STAND.getY(), SICKNESS_STAND.getZ() + 0.5);
					}
					Showcase.check(sicknessCough(level) == 0, "everyone is well again");
				}, 50)),
			(level, player) -> true));
		// The Bandit King, II: the story (ROADMAP 31.16): the three camps' war banners (black, a red skull), taken; the
		// fort rising a stage a day; the storm with the allies won (3 guards of a neighbour, 6 mercenaries) and the postern
		// Pell told of left open; the King falling.
		SCREENS.put("bandit_king", new Screen("the Bandit King's story: three war banners, black with a red skull, flown over his camps and taken; his fort rising a stage a day; the storm with the allies won (3 guards of the neighbours, 6 mercenaries) and the keep's postern open; the King falling",
			new Vec3(24.5, -46, 10.5), new Vec3(0.5, -57, -20.5),
			(level, player) -> {
				for (var mob : io.github.jcondedata.aliveworkplace.guard.BanditFort.men(level, BANDIT_FORT_GROUND, 40)) {
					mob.discard();
				}
				for (var v : io.github.jcondedata.aliveworkplace.story.BanditKingStory.allies(level, BANDIT_KING_FRONT)) {
					v.discard();
				}
			},
			List.of(new Step("01_banners", -1, 0, (level, player) -> {
					int raised = 0;
					for (int x : new int[] {-8, 0, 8}) {
						BlockPos at = io.github.jcondedata.aliveworkplace.story.BanditKingStory.raiseBanner(level, BANDIT_KING_FRONT.offset(x, 0, 2));
						if (at != null && io.github.jcondedata.aliveworkplace.story.BanditKingStory.isWarBanner(level, at)) {
							raised++;
						}
					}
					Showcase.check(raised == 3, "three war banners, black with a red skull, fly over the camps");
				}, 50),
				new Step("02_banners_taken", -1, 0, (level, player) -> {
					int taken = 0;
					for (BlockPos p : BlockPos.betweenClosed(BANDIT_KING_FRONT.offset(-12, -2, -4), BANDIT_KING_FRONT.offset(12, 3, 8))) {
						if (io.github.jcondedata.aliveworkplace.story.BanditKingStory.isWarBanner(level, p)) {
							level.destroyBlock(p.immutable(), true, player);
							taken++;
						}
					}
					Showcase.check(taken == 3, "the three war banners are taken");
				}, 40),
				new Step("03_fort_day_1", -1, 0, (level, player) -> {
					boolean placed = io.github.jcondedata.aliveworkplace.guard.BanditFort.place(level, banditFort(), 0, false);
					stillFortMen(level);
					Showcase.check(placed, "the next dawn: the fort's palisade, fire and tents");
				}, 40),
				new Step("04_fort_day_2", -1, 0, (level, player) -> {
					boolean placed = io.github.jcondedata.aliveworkplace.guard.BanditFort.place(level, banditFort(), 1, false);
					stillFortMen(level);
					Showcase.check(placed, "a day on: the watchtowers and the stable");
				}, 40),
				new Step("05_fort_day_3", -1, 0, (level, player) -> {
					boolean placed = io.github.jcondedata.aliveworkplace.guard.BanditFort.place(level, banditFort(), 2, true);
					stillFortMen(level);
					Showcase.check(placed && level.getBlockState(banditFort().at(io.github.jcondedata.aliveworkplace.guard.BanditFort.POSTERN))
						.getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "a day on: the King's keep, its postern left open as Pell told");
				}, 40),
				new Step("06_storm", -1, 0, (level, player) -> {
					for (int i = 0; i < 3; i++) {
						io.github.jcondedata.aliveworkplace.story.BanditKingStory.ally(level, BANDIT_KING_FRONT,
							Component.translatable("entity.aliveworkplace.king_ally.neighbour", "Thornholm"), i);
					}
					for (int i = 3; i < 9; i++) {
						io.github.jcondedata.aliveworkplace.story.BanditKingStory.ally(level, BANDIT_KING_FRONT, Component.translatable("entity.aliveworkplace.mercenary"), i);
					}
					var allies = io.github.jcondedata.aliveworkplace.story.BanditKingStory.allies(level, BANDIT_KING_FRONT);
					allies.forEach(v -> v.setNoAi(true));
					Showcase.check(allies.size() == 9, "the storm: 3 guards of the neighbours and 6 mercenaries before the gate");
				}, 50),
				new Step("07_king_falls", -1, 0, (level, player) -> {
					var king = io.github.jcondedata.aliveworkplace.guard.BanditFort.men(level, BANDIT_FORT_GROUND, 40).stream()
						.filter(m -> m.getTags().contains(io.github.jcondedata.aliveworkplace.guard.BanditKing.TAG)).findFirst().orElse(null);
					if (king != null) {
						king.setNoAi(false);
						king.hurt(level.damageSources().playerAttack(player), 10000f);
					}
					Showcase.check(king != null && !king.isAlive(), "the Bandit King falls");
				}, 40)),
			(level, player) -> true));
		// Gifts (ROADMAP 31.6): Gift Wrap and a Golden Carrot on the crafting grid make a Gift; Dara the farmer unwraps
		// it (her line over her head and in chat, bits of carrot, hearts), her hearts go up in the action bar, and the
		// hall's tooltip says when her name day is.
		SCREENS.put("gifts", new Screen("a Golden Carrot wrapped on the crafting grid and given to Dara: she loved it, her hearts went up, and the hall's tooltip says when her name day is",
			new Vec3(1.5, -60, 4.5), new Vec3(1.5, -60.2, 1.5), // B130: aimed from the feet, so the eyes' look meets Dara (friendship)
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(new BlockPos(3, -60, 3), net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
				Villager dara = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 1), MobSpawnType.COMMAND);
				dara.setNoAi(true);
				dara.setYRot(0);
				dara.setYHeadRot(0);
				dara.setYBodyRot(0);
				dara.setVillagerData(dara.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
				dara.setCustomName(Component.literal("Dara"));
				subject = dara;
				ScreenshotHarness.fileRecords(level, STATION, 1); // B130: the hall's record now, so Dara's hall is found
			},
			List.of(new Step("01_wrapping", -1, 0, (level, player) -> {
					// The hall's POI is only registered after the staging tick: look Dara's hall up again now (B72).
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					io.github.jcondedata.aliveworkplace.story.Friendship.add(subject, player, 255); // the days before
					BlockPos table = new BlockPos(3, -60, 3);
					player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> new net.minecraft.world.inventory.CraftingMenu(id, inventory,
						net.minecraft.world.inventory.ContainerLevelAccess.create(level, table)), Component.translatable("container.crafting")));
					boolean wrapped = false;
					if (player.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu menu) {
						menu.getSlot(1).set(new ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.GIFT_WRAP));
						menu.getSlot(2).set(new ItemStack(Items.GOLDEN_CARROT));
						menu.broadcastChanges();
						ItemStack result = menu.getSlot(0).getItem();
						wrapped = result.is(io.github.jcondedata.aliveworkplace.registry.ModItems.GIFT)
							&& io.github.jcondedata.aliveworkplace.story.Gifts.contents(result).is(Items.GOLDEN_CARROT);
					}
					Showcase.check(wrapped, "Gift Wrap and a Golden Carrot on the crafting grid make a Gift holding the carrot");
				}, 40),
				new Step("02_giving", -1, 0, (level, player) -> {
					Villager dara = subject;
					// Take the Gift off the grid as a shift-click does (that signs it), and give it to Dara.
					if (player.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu menu) {
						menu.quickMoveStack(player, 0);
					}
					player.closeContainer();
					ItemStack gift = ItemStack.EMPTY;
					for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
						if (player.getInventory().getItem(i).is(io.github.jcondedata.aliveworkplace.registry.ModItems.GIFT)) {
							gift = player.getInventory().removeItemNoUpdate(i);
						}
					}
					boolean signed = io.github.jcondedata.aliveworkplace.story.Gifts.from(gift).equals(player.getGameProfile().getName());
					player.setItemInHand(InteractionHand.MAIN_HAND, gift);
					// B130: given in survival: the filming camera is a creative player, who keeps what they give (as in vanilla)
					var mode = player.gameMode.getGameModeForPlayer();
					player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
					var result = io.github.jcondedata.aliveworkplace.story.Gifts.give(player, dara, player.getMainHandItem());
					player.setGameMode(mode);
					if (mode == net.minecraft.world.level.GameType.CREATIVE) {
						player.getAbilities().flying = true;
						player.onUpdateAbilities();
					}
					int expected = io.github.jcondedata.aliveworkplace.story.Tastes.Band.LOVED.points
						* (result.nameDay() ? io.github.jcondedata.aliveworkplace.story.Gifts.NAME_DAY_FACTOR : 1);
					Showcase.check(signed && result.outcome() == io.github.jcondedata.aliveworkplace.story.Gifts.Outcome.GIVEN
						&& result.band() == io.github.jcondedata.aliveworkplace.story.Tastes.Band.LOVED && result.change() == expected
						&& player.getMainHandItem().isEmpty() && result.line().getString().contains("Golden Carrot"),
						"the Gift is from the player, and Dara the farmer loved her Golden Carrot (+" + expected + "): " + result.line().getString()
							+ " (signed " + signed + ", " + result.outcome() + ", " + result.band() + ", change " + result.change() + ", name day " + result.nameDay()
							+ ", hand " + player.getMainHandItem() + ")");
				}, 40),
				new Step("03_hearts", -1, 0, (level, player) -> {
					Villager dara = subject;
					int points = io.github.jcondedata.aliveworkplace.story.Friendship.points(dara, player.getUUID());
					Component line = io.github.jcondedata.aliveworkplace.story.Friendship.lookLine(player);
					// A second gift the same day is turned down, with a line.
					player.setItemInHand(InteractionHand.MAIN_HAND, io.github.jcondedata.aliveworkplace.story.Gifts.wrap(new ItemStack(Items.CAKE), player.getGameProfile().getName()));
					var again = io.github.jcondedata.aliveworkplace.story.Gifts.give(player, dara, player.getMainHandItem());
					Showcase.check(points >= 335 && line != null && line.getString().startsWith("Dara ♥♥♥")
						&& again.outcome() == io.github.jcondedata.aliveworkplace.story.Gifts.Outcome.DAY_LIMIT
						&& player.getMainHandItem().is(io.github.jcondedata.aliveworkplace.registry.ModItems.GIFT),
						"her hearts went up in the action bar (" + (line == null ? null : line.getString()) + "), and a second gift the same day is handed back: "
							+ again.line().getString());
				}, 40),
				new Step("04_hall_name_day", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					boolean shown = player.containerMenu instanceof ChoiceMenu m && m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON)
						.getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream()
						.anyMatch(l -> l.getString().startsWith("Name day: "));
					Showcase.check(shown, "the hall's tooltip for Dara says when her name day is");
				}, 30)),
			(level, player) -> subject != null && io.github.jcondedata.aliveworkplace.story.Friendship.points(subject, player.getUUID()) >= 335));
		// A heart event (ROADMAP 31.7): at two hearts Dara, born in the village, walks up to the player, faces them and
		// tells where she comes from, line by line over her head and in grey in the chat; told, it's in the chronicle and
		// on her life story page (a shift-click on her in the hall's list). At ten hearts (31.8) she tells what she dreams
		// of and gives her keepsake, "Dara's Grandmother's Seeds".
		SCREENS.put("heart_event", new Screen("at two hearts Dara walked up and told where she comes from (it's in the chronicle and on her life story page); at ten she told her dream and gave her keepsake",
			new Vec3(1.5, -60, 6.5), new Vec3(1.5, -58.8, 3.5),
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				Villager dara = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 1), MobSpawnType.COMMAND);
				dara.setNoAi(true); // (she waits here until the scene begins)
				dara.setYRot(0);
				dara.setYHeadRot(0);
				dara.setYBodyRot(0);
				dara.setVillagerData(dara.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
				dara.setCustomName(Component.literal("Dara"));
				io.github.jcondedata.aliveworkplace.registry.ModAttachments.PARENTS.set(dara,
					new io.github.jcondedata.aliveworkplace.people.Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true));
				subject = dara;
				ScreenshotHarness.fileRecords(level, STATION, 1); // B130: the hall's record now, so Dara's hall is found
			},
			List.of(new Step("01_walks_up", -1, 0, (level, player) -> {
					// The hall's POI is only registered after the staging tick: look Dara's hall up again now (B72).
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					Villager dara = subject;
					dara.setNoAi(false);
					io.github.jcondedata.aliveworkplace.story.Friendship.add(dara, player, 200); // two hearts: she has something to tell
					var event = io.github.jcondedata.aliveworkplace.story.HeartEvents.pending(level, dara, player.getUUID());
					Showcase.check(event != null && event.id().getPath().equals("born_here"),
						"at two hearts Dara, born here, has \"Where I come from\" to tell: " + (event == null ? null : event.id()));
				}, 25),
				new Step("02_telling", -1, 0, null, 80),
				new Step("03_told", -1, 0, (level, player) -> {
					Villager dara = subject;
					var telling = io.github.jcondedata.aliveworkplace.story.HeartEvents.telling(dara);
					double off = Math.sqrt(dara.distanceToSqr(player));
					Showcase.check(telling != null && telling.said >= 1 && off <= io.github.jcondedata.aliveworkplace.story.HeartEvents.TALK_RANGE + 0.5,
						"Dara walked up to the player and began her story: " + (telling == null ? "not telling" : telling.said + " lines") + ", "
							+ String.format("%.1f", off) + " blocks off");
				}, 165),
				new Step("04_life_story", io.github.jcondedata.aliveworkplace.story.LifeStory.SECTIONS, 6, (level, player) -> {
					Villager dara = subject;
					String me = player.getGameProfile().getName();
					String village = io.github.jcondedata.aliveworkplace.hall.VillageHalls.name(level, STATION).getString();
					boolean told = io.github.jcondedata.aliveworkplace.story.Friendship.of(dara).bond(player.getUUID()).told().equals(List.of("aliveworkplace:born_here"))
						&& io.github.jcondedata.aliveworkplace.story.Friendship.points(dara, player.getUUID()) == 220;
					boolean chronicled = level.getBlockEntity(STATION) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall
						&& hall.chronicle().stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.FRIEND
							&& e.text().getString().equals("Dara told " + me + " about growing up in " + village));
					Showcase.check(told && chronicled, "told to the end: +20 friendship, and the chronicle says Dara told " + me + " about growing up in " + village);
					// Her life story: a shift-click on her in the hall's list, as the client sends it.
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					boolean page = false;
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.clicked(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
						page = m.icon(4).getHoverName().getString().equals("The life story of Dara")
							&& m.icon(io.github.jcondedata.aliveworkplace.story.LifeStory.SECTIONS).getHoverName().getString().equals("Born in " + village + " to Mira and Tomas");
					}
					Showcase.check(page, "a shift-click on Dara in the hall's list opens her life story, with what she told as its first line");
				}, 40),
				new Step("05_keepsake", -1, 0, (level, player) -> {
					// Ten hearts, the events between already told: she comes over and tells what she dreams of.
					player.closeContainer();
					Villager dara = subject;
					for (String told : List.of("aliveworkplace:work_land", "aliveworkplace:night_you", "aliveworkplace:love_parents")) {
						io.github.jcondedata.aliveworkplace.story.Friendship.told(dara, player, told);
					}
					io.github.jcondedata.aliveworkplace.story.Friendship.add(dara, player, 1000);
					dara.teleportTo(1.5, -60, 4.5);
					var event = io.github.jcondedata.aliveworkplace.story.HeartEvents.pending(level, dara, player.getUUID());
					Showcase.check(event != null && event.hearts() == 10 && !io.github.jcondedata.aliveworkplace.story.Keepsakes.given(dara, player),
						"at ten hearts Dara has what she dreams of to tell, and her keepsake still to give: " + (event == null ? null : event.id()));
				}, 320)), // B130: her four lines a LINE_EVERY (60 ticks) apart after she walks up: 230 ended at the third
			(level, player) -> {
				if (subject == null) {
					return false;
				}
				boolean told = io.github.jcondedata.aliveworkplace.story.Friendship.of(subject).bond(player.getUUID()).told().contains("aliveworkplace:born_here");
				ItemStack seeds = player.getInventory().items.stream().filter(s -> s.is(Items.TORCHFLOWER_SEEDS)).findFirst().orElse(ItemStack.EMPTY);
				boolean keepsake = io.github.jcondedata.aliveworkplace.story.Keepsakes.given(subject, player) && seeds.getCount() == 4
					&& seeds.getHoverName().getString().equals("Dara's Grandmother's Seeds");
				Showcase.check(keepsake, "her dream told at ten hearts, Dara the farmer gave her keepsake: 4 torchflower seeds named \"Dara's Grandmother's Seeds\" ("
					+ (seeds.isEmpty() ? "nothing" : seeds.getCount() + " " + seeds.getHoverName().getString()) + ")"
					+ (keepsake ? "" : "; told " + io.github.jcondedata.aliveworkplace.story.Friendship.of(subject).bond(player.getUUID()).told()
						+ ", telling " + (io.github.jcondedata.aliveworkplace.story.HeartEvents.telling(subject) == null ? "none"
							: io.github.jcondedata.aliveworkplace.story.HeartEvents.telling(subject).said + " lines")
						+ ", pending " + io.github.jcondedata.aliveworkplace.story.HeartEvents.pending(level, subject, player.getUUID())
						+ ", " + String.format("%.1f", Math.sqrt(subject.distanceToSqr(player))) + " blocks off, noAi " + subject.isNoAi()));
				return told && keepsake;
			}));
		// A personal request (ROADMAP 31.9): at three hearts Dara, a plains farmer, walks up and asks for a taste of home;
		// the player takes it with [I'll help], hands the pumpkin pie in from the journal's Personal tab, and she thanks them.
		SCREENS.put("personal_request", new Screen("at three hearts Dara walked up and asked for a taste of home; accepted, the pie was handed in from the journal's Personal tab and she said her thanks",
			new Vec3(1.5, -60, 6.5), new Vec3(1.5, -58.8, 3.5),
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				Villager dara = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 1), MobSpawnType.COMMAND);
				dara.setNoAi(true); // (she waits here until the scene begins)
				dara.setYRot(0);
				dara.setYHeadRot(0);
				dara.setYBodyRot(0);
				dara.setVillagerData(dara.getVillagerData().setType(net.minecraft.world.entity.npc.VillagerType.PLAINS).setProfession(VillagerProfession.FARMER).setLevel(2));
				dara.setCustomName(Component.literal("Dara"));
				subject = dara;
			},
			List.of(new Step("01_ask", -1, 0, (level, player) -> {
					// The hall's POI is only registered after the staging tick: look Dara's hall up again now (B72).
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					Villager dara = subject;
					dara.setNoAi(false);
					watchAsk(dara);
					// Three hearts, her two-heart story already told (so she comes over to ask, not to tell it).
					io.github.jcondedata.aliveworkplace.story.Friendship.told(dara, player, "aliveworkplace:before_hall");
					io.github.jcondedata.aliveworkplace.story.Friendship.add(dara, player, 300);
					var file = io.github.jcondedata.aliveworkplace.story.QuestFiles.get(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("personal/taste_of_home")).orElse(null);
					var quest = file == null || !(level.getBlockEntity(STATION) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall) ? null
						: io.github.jcondedata.aliveworkplace.story.PersonalRequests.resolve(level, STATION, hall,
							io.github.jcondedata.aliveworkplace.hall.VillageHalls.census(level, STATION), file, dara, player, net.minecraft.util.RandomSource.create(1));
					Showcase.check(quest != null, "at three hearts Dara, from the plains, has a taste of home to ask for");
					if (quest != null) {
						io.github.jcondedata.aliveworkplace.story.PersonalRequests.propose(level, STATION, dara, player, quest,
							io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level));
					}
				}, 90),
				new Step("02_accept", -1, 0, (level, player) -> {
					Villager dara = subject;
					var offer = io.github.jcondedata.aliveworkplace.story.PersonalRequests.offer(dara);
					// Her distance the tick she asked (B287): measured here, 90 ticks on, she had walked off again (5.5 blocks).
					double off = askedFrom;
					double now = Math.sqrt(dara.distanceToSqr(player));
					Showcase.check(offer != null && offer.asked && off >= 0 && off <= io.github.jcondedata.aliveworkplace.story.PersonalRequests.TALK_RANGE + 0.5,
						"Dara walked up to the player and asked: " + (offer == null ? "no offer" : offer.asked ? "asked" : "not asked yet") + ", "
							+ (off < 0 ? "no ask seen" : String.format("%.1f", off) + " blocks off when she asked") + " (" + String.format("%.1f", now) + " now)");
					if (offer != null) {
						// [I'll help], as the click in the chat runs it.
						level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "workplace quest accept " + offer.quest.id);
					}
					var quest = io.github.jcondedata.aliveworkplace.story.PersonalRequests.of(dara);
					Showcase.check(quest != null && quest.helpers.containsKey(player.getUUID()), "[I'll help] opened her request with the player as its helper");
				}, 50),
				new Step("03_hand_in", io.github.jcondedata.aliveworkplace.hall.QuestJournal.PERSONAL_ROW, 6, (level, player) -> {
					// She has asked: she waits beside the player from here. Walking on, she pushed the hovering camera back past
					// the hall's 8-block reach, which shut the journal before its click (B219); the camera goes back to its place.
					Villager dara = subject;
					dara.setNoAi(true);
					dara.moveTo(2.5, -60, 4.5, 27f, 0f);
					dara.setYHeadRot(27f);
					dara.setYBodyRot(27f);
					ScreenshotHarness.hoverLookingAt(player, new Vec3(1.5, -60, 6.5), new Vec3(1.5, -58.8, 3.5));
					// The pie in hand, the hall's quest journal on its Personal tab.
					player.getInventory().add(new ItemStack(Items.PUMPKIN_PIE));
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					boolean shown = false;
					if (player.containerMenu instanceof ChoiceMenu m) {
						io.github.jcondedata.aliveworkplace.hall.QuestJournal.render(m, level, STATION, player, io.github.jcondedata.aliveworkplace.hall.QuestJournal.Tab.PERSONAL);
						m.broadcastChanges();
						ItemStack icon = m.icon(io.github.jcondedata.aliveworkplace.hall.QuestJournal.PERSONAL_ROW);
						shown = icon.is(Items.PUMPKIN_PIE) && icon.getHoverName().getString().equals("A taste of home");
					}
					Showcase.check(shown, "the journal's Personal tab shows \"A taste of home\" as a Pumpkin Pie to hand in");				}, 40),
				new Step("04_thanks", -1, 0, (level, player) -> {
					boolean open = player.containerMenu instanceof ChoiceMenu;
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.QuestJournal.PERSONAL_ROW, player); // the click that hands it in
					}
					player.closeContainer();
					Villager dara = subject;
					String me = player.getGameProfile().getName();
					boolean done = io.github.jcondedata.aliveworkplace.story.PersonalRequests.of(dara) == null && player.getInventory().countItem(Items.PUMPKIN_PIE) == 0
						&& player.getInventory().countItem(Items.EMERALD) == 4;
					boolean chronicled = level.getBlockEntity(STATION) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall
						&& hall.chronicle().stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.FRIEND
							&& e.text().getString().equals(me + " helped Dara: A taste of home"));
					Showcase.check(done && chronicled, "the pie handed in: the request is done, 4 emeralds paid, and the chronicle says " + me + " helped Dara"
						+ (done && chronicled ? "" : "; journal " + (open ? "open" : "shut") + ", " + String.format("%.1f", Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(STATION))))
							+ " blocks from the hall"));
				}, 60)),
			(level, player) -> {
				if (subject == null) {
					return false;
				}
				int points = io.github.jcondedata.aliveworkplace.story.Friendship.points(subject, player.getUUID());
				boolean friends = points == 300 + io.github.jcondedata.aliveworkplace.story.Friendship.Favour.REQUEST.points
					+ io.github.jcondedata.aliveworkplace.story.Friendship.Favour.HAND_IN.points;
				Showcase.check(friends, "helping earned the request's 150 friendship and the hand-in's 10: " + points + " points with Dara");
				return friends && io.github.jcondedata.aliveworkplace.story.PersonalRequests.of(subject) == null;
			}));
		// A letter to family (ROADMAP 31.10): Dara asks the player to carry a letter to her relative Mira in Elmford, a
		// village 40 blocks east; the sealed letter goes there, Mira reads it and writes back, and the reply comes home.
		SCREENS.put("letter", new Screen("Dara asked for a letter carried to Mira in Elmford; the sealed letter went there, Mira wrote back, and the reply came home",
			new Vec3(1.5, -60, 5.5), new Vec3(1.5, -58.8, 2.5),
			(level, player) -> {
				io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 12; // two small villages in one picture's walk
				BlockPos elmford = new BlockPos(40, -60, 0);
				for (BlockPos hall : List.of(STATION, elmford)) {
					level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState()
						.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				}
				if (level.getBlockEntity(elmford) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall) {
					hall.setCustomName(Component.literal("Elmford"));
				}
				io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level).setWants(elmford, Component.literal("Elmford"), List.of());
				Villager dara = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 2), MobSpawnType.COMMAND);
				Villager mira = EntityType.VILLAGER.spawn(level, new BlockPos(41, -60, 2), MobSpawnType.COMMAND);
				for (Villager v : List.of(dara, mira)) {
					v.setNoAi(true);
					v.setYRot(0);
					v.setYHeadRot(0);
					v.setYBodyRot(0);
				}
				dara.setVillagerData(dara.getVillagerData().setType(net.minecraft.world.entity.npc.VillagerType.PLAINS).setProfession(VillagerProfession.FARMER).setLevel(2));
				dara.setCustomName(Component.literal("Dara"));
				mira.setVillagerData(mira.getVillagerData().setType(net.minecraft.world.entity.npc.VillagerType.PLAINS).setProfession(VillagerProfession.LIBRARIAN).setLevel(3));
				mira.setCustomName(Component.literal("Mira"));
				subject = dara;
				scholar = mira;
			},
			List.of(new Step("01_ask", -1, 0, (level, player) -> {
					// The halls' POIs are only registered after the staging tick: look the villagers' halls up again now (B72).
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					Villager dara = subject;
					io.github.jcondedata.aliveworkplace.story.Friendship.told(dara, player, "aliveworkplace:before_hall");
					io.github.jcondedata.aliveworkplace.story.Friendship.add(dara, player, 300);
					var file = io.github.jcondedata.aliveworkplace.story.QuestFiles.get(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("personal/letter_to_family")).orElse(null);
					var quest = file == null || !(level.getBlockEntity(STATION) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall) ? null
						: io.github.jcondedata.aliveworkplace.story.PersonalRequests.resolve(level, STATION, hall,
							io.github.jcondedata.aliveworkplace.hall.VillageHalls.census(level, STATION), file, dara, player, net.minecraft.util.RandomSource.create(1));
					Showcase.check(quest != null, "at three hearts Dara has a letter for Mira in Elmford to ask the player to carry");
					if (quest != null) {
						io.github.jcondedata.aliveworkplace.story.PersonalRequests.propose(level, STATION, dara, player, quest,
							io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level));
					}
				}, 60),
				new Step("02_letter", -1, 0, (level, player) -> {
					Villager dara = subject;
					var offer = io.github.jcondedata.aliveworkplace.story.PersonalRequests.offer(dara);
					Showcase.check(offer != null && offer.asked, "Dara asked, with [I'll help] and [Not now] in the chat");
					if (offer != null) {
						level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "workplace quest accept " + offer.quest.id);
					}
					var quest = io.github.jcondedata.aliveworkplace.story.PersonalRequests.of(dara);
					ItemStack letter = quest == null ? ItemStack.EMPTY : player.getInventory().items.stream()
						.filter(s -> s.is(Items.PAPER) && s.getHoverName().getString().equals("Sealed Letter to Mira")).findFirst().orElse(ItemStack.EMPTY);
					Showcase.check(!letter.isEmpty(), "[I'll help]: the player holds the Sealed Letter to Mira");
					if (!letter.isEmpty()) {
						ItemStack held = letter.copy();
						letter.setCount(0);
						player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
					}
				}, 50),
				new Step("03_delivered", -1, 0, (level, player) -> {
					// In Elmford, the letter given to Mira (the right-click).
					player.teleportTo(level, 41.5, -60, 5.5, 180f, 10f);
					Villager mira = scholar;
					boolean given = io.github.jcondedata.aliveworkplace.story.RequestErrands.use(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, mira);
					ItemStack reply = player.getInventory().items.stream()
						.filter(s -> s.is(Items.PAPER) && s.getHoverName().getString().equals("Reply from Mira")).findFirst().orElse(ItemStack.EMPTY);
					Showcase.check(given && !reply.isEmpty(), "in Elmford Mira read the letter and wrote a reply for Dara");
					if (!reply.isEmpty()) {
						ItemStack held = reply.copy();
						reply.setCount(0);
						player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
					}
				}, 60),
				new Step("04_reply", -1, 0, (level, player) -> {
					// Home again, the reply to Dara.
					player.teleportTo(level, 1.5, -60, 5.5, 180f, 10f);
					Villager dara = subject;
					boolean taken = io.github.jcondedata.aliveworkplace.story.RequestErrands.use(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, dara);
					String me = player.getGameProfile().getName();
					boolean done = taken && io.github.jcondedata.aliveworkplace.story.PersonalRequests.of(dara) == null && player.getInventory().countItem(Items.EMERALD) == 4;
					boolean chronicled = level.getBlockEntity(new BlockPos(40, -60, 0)) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity elmford
						&& elmford.chronicle().stream().anyMatch(e -> e.text().getString().startsWith(me + " brought Mira a letter from Dara"))
						&& level.getBlockEntity(STATION) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity home
						&& home.chronicle().stream().anyMatch(e -> e.text().getString().equals(me + " helped Dara: A letter to family"));
					Showcase.check(done && chronicled, "the reply home: the request is done, 4 emeralds paid, a line in both villages' chronicles");
				}, 60)),
			(level, player) -> {
				if (subject == null || scholar == null) {
					return false;
				}
				boolean friends = io.github.jcondedata.aliveworkplace.story.Friendship.points(scholar, player.getUUID()) > 0;
				Showcase.check(friends, "carrying the letter made a friend of Mira too");
				return friends && io.github.jcondedata.aliveworkplace.story.PersonalRequests.of(subject) == null;
			}));
		// Reputation and titles (ROADMAP 31.11): the player becomes a Hero of Thornholm (told to the whole server), their
		// next chat line carries the title, and the hall's name tag tooltip shows their standing and the best regarded.
		SCREENS.put("titles", new Screen("the player became a Hero of Thornholm, told to the whole server; their chat line carries the title and the hall's tooltip shows their standing",
			new Vec3(1.5, -60, 6.5), new Vec3(1.5, -58.8, 3.5),
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				if (level.getBlockEntity(STATION) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall) {
					hall.setCustomName(Component.literal("Thornholm"));
				}
			},
			List.of(new Step("01_hero", -1, 0, (level, player) -> {
					// Two more who made a name here before, then the deeds that make the player a Hero.
					io.github.jcondedata.aliveworkplace.story.Reputation.add(level, STATION, java.util.UUID.nameUUIDFromBytes("Mara".getBytes()), "Mara", 120);
					io.github.jcondedata.aliveworkplace.story.Reputation.add(level, STATION, java.util.UUID.nameUUIDFromBytes("Tobin".getBytes()), "Tobin", 35);
					var change = io.github.jcondedata.aliveworkplace.story.Reputation.add(level, STATION, player, 320);
					io.github.jcondedata.aliveworkplace.story.Reputation.honour(level, STATION, player.getUUID(), player.getGameProfile().getName(), "wayfinder");
					Showcase.check(change.announced() && change.now() == io.github.jcondedata.aliveworkplace.story.Reputation.Title.HERO,
						"320 standing made the player a Hero of Thornholm and the whole server was told: " + change);
				}, 60),
				new Step("02_chat", -1, 0, (level, player) -> {
					// A chat line as the server sends it on: through its chat decorator, which is ours.
					String said = "Good morning, Thornholm!";
					Component decorated = level.getServer().getChatDecorator().decorate(player, Component.literal(said));
					level.getServer().getPlayerList().broadcastChatMessage(
						net.minecraft.network.chat.PlayerChatMessage.unsigned(player.getUUID(), said).withUnsignedContent(decorated), player,
						net.minecraft.network.chat.ChatType.bind(net.minecraft.network.chat.ChatType.CHAT, player));
					Showcase.check(decorated.getString().equals("[Hero of Thornholm] " + said), "the chat line carries the title: " + decorated.getString());
				}, 60),
				new Step("03_tooltip", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.NAME, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					boolean shown = false;
					if (player.containerMenu instanceof ChoiceMenu m) {
						var lore = m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.NAME).getOrDefault(net.minecraft.core.component.DataComponents.LORE,
							net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
						String me = player.getGameProfile().getName();
						shown = lore.contains("Your standing: 320 (Hero)") && lore.contains("Your honors: Wayfinder") && lore.contains("1. " + me + ", Hero (320)")
							&& lore.contains("2. Mara, Friend (120)") && lore.contains("3. Tobin, Stranger (35)");
					}
					Showcase.check(shown, "the hall's name tag tooltip shows the player's standing, their honour and the three best regarded");
				}, 60)),
			(level, player) -> {
				player.closeContainer();
				boolean hero = io.github.jcondedata.aliveworkplace.story.Reputation.title(level, STATION, player.getUUID())
					== io.github.jcondedata.aliveworkplace.story.Reputation.Title.HERO;
				boolean chronicled = level.getBlockEntity(STATION) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall
					&& hall.chronicle().stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.TITLE
						&& e.text().getString().equals(player.getGameProfile().getName() + " became a Hero of the village"));
				Showcase.check(hero && chronicled, "the player holds the title and the chronicle has its TITLE line");
				return hero && chronicled;
			}));
		// My work (ROADMAP 31.8): at four hearts a villager tells of their work, one story per job family (the foal for
		// those who keep animals, the fish for the fisherman). One villager of each family in turn, at four hearts, says
		// the first line of theirs over their head and in the chat.
		SCREENS.put("heart_events_work", new Screen("at four hearts a villager of each job family began the story of their work",
			new Vec3(1.5, -60, 6.5), new Vec3(1.5, -58.8, 3.5),
			(level, player) -> {
				// B319: every teller has four hearts and a friend near, so the morning's request roll (one in four) could
				// pick the one just spawned, before their telling began, and a villager asking for help tells no story
				// (HeartEvents: not while PersonalRequests.offer). Twice that was Wren, the last: "not telling". Requests
				// are off for this scene, which is about the stories (the personal_request scenes show them).
				io.github.jcondedata.aliveworkplace.story.PersonalRequests.ENABLED = false;
				io.github.jcondedata.aliveworkplace.story.PersonalRequests.forget();
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
			},
			java.util.stream.Stream.of(
					new String[] {"01_building", "Dara", "aliveworkplace:builder", "work_building"},
					new String[] {"02_mining", "Bram", "aliveworkplace:miner", "work_mining"},
					new String[] {"03_land", "Edda", "minecraft:farmer", "work_land"},
					new String[] {"04_animals", "Finn", "minecraft:shepherd", "work_animals"},
					new String[] {"05_water", "Ilse", "minecraft:fisherman", "work_water"},
					new String[] {"06_kitchen", "Cora", "aliveworkplace:chef", "work_kitchen"},
					new String[] {"07_learning", "Odo", "minecraft:librarian", "work_learning"},
					new String[] {"08_healing", "Mira", "minecraft:cleric", "work_healing"},
					new String[] {"09_arms", "Tomas", "aliveworkplace:guard", "work_arms"},
					new String[] {"10_trade", "Corwin", "aliveworkplace:shopkeeper", "work_trade"},
					new String[] {"11_music", "Ana", "aliveworkplace:bard", "work_music"},
					new String[] {"12_pokemon", "Nell", "aliveworkplace:trainer", "work_pokemon"},
					new String[] {"13_no_trade", "Wren", "minecraft:none", "work_none"})
				.map(who -> new Step(who[0], -1, 0, (level, player) -> {
					// The hall's POI is only registered after the staging tick: look the hall up again now (B72).
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					if (subject != null) {
						subject.discard(); // the one before: their telling stops with them
					}
					Villager teller = EntityType.VILLAGER.spawn(level, new BlockPos(1, -60, 3), MobSpawnType.COMMAND);
					teller.setNoAi(true); // (they stand in front of the camera, facing it)
					teller.setYRot(0);
					teller.setYHeadRot(0);
					teller.setYBodyRot(0);
					teller.setVillagerData(teller.getVillagerData().setProfession(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
						.get(net.minecraft.resources.ResourceLocation.parse(who[2]))).setLevel(2));
					teller.setCustomName(Component.literal(who[1]));
					// Four hearts, "Where I come from" already told: their work is what they have to tell.
					io.github.jcondedata.aliveworkplace.story.Friendship.told(teller, player, "aliveworkplace:before_hall");
					io.github.jcondedata.aliveworkplace.story.Friendship.add(teller, player, 400);
					var event = io.github.jcondedata.aliveworkplace.story.HeartEvents.pending(level, teller, player.getUUID());
					Showcase.check(event != null && event.id().getPath().equals(who[3]),
						"at four hearts " + who[1] + " (" + who[2] + ") has " + who[3] + " to tell: " + (event == null ? null : event.id()));
					subject = teller;
				}, 40)).toList(),
			(level, player) -> {
				var telling = subject == null ? null : io.github.jcondedata.aliveworkplace.story.HeartEvents.telling(subject);
				var asking = subject == null ? null : io.github.jcondedata.aliveworkplace.story.PersonalRequests.offer(subject);
				Showcase.check(telling != null && telling.said >= 1 && telling.event.id().getPath().equals("work_none"),
					"the last of them, without a trade, is telling theirs: " + (telling == null ? "not telling" : telling.event.id() + ", " + telling.said + " lines")
						+ (telling == null && subject != null ? " (" + (asking != null ? "asking for help instead, " : "") + "doing "
							+ subject.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("nothing") + ", "
							+ String.format("%.1f", Math.sqrt(subject.distanceToSqr(player))) + " from the player)" : ""));
				return telling != null;
			}));
		// A story arc (ROADMAP 31.4): the chapter told in chat as it begins, the hall's Story tab (the first chapter ticked,
		// the second running with its quest) and the chronicle's STORY lines. The mod ships no arcs of its own yet, so the
		// scene reads a small one here, as a data pack would.
		SCREENS.put("story_arc", new Screen("a story arc began: its chapters told in chat, the Story tab with the first chapter ticked and the second running, and the chronicle",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString("""
					{"name": {"text": "The Long Winter"},
					 "chapters": [
					  {"name": {"text": "The first snow"},
					   "intro": [{"text": "Snow came early this year, and the woodpile behind the hall is thin."}],
					   "complete": []},
					  {"name": {"text": "Wood for the winter"},
					   "intro": [{"text": "The village asks for logs before the frost sets in."}],
					   "quests": [{"name": {"text": "Logs for the woodpile"}, "objectives": [{"type": "bring", "item": "minecraft:oak_log", "count": 32}]}],
					   "time_limit_days": 4},
					  {"name": {"text": "The frozen pass"}, "complete": []}]}
					""").getAsJsonObject();
				var arc = io.github.jcondedata.aliveworkplace.story.Arcs.read(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase/long_winter"), json);
				io.github.jcondedata.aliveworkplace.story.Arcs.add(arc);
				io.github.jcondedata.aliveworkplace.story.Arcs.stopAll(level, STATION);
				var started = io.github.jcondedata.aliveworkplace.story.Arcs.start(level, STATION, arc);
				Showcase.check(started != null && started.chapter == 1 && started.started, "the arc began and moved on to its second chapter");
			},
			List.of(new Step("01_story_announced", -1, 0, (level, player) ->
					Showcase.check(io.github.jcondedata.aliveworkplace.story.Arcs.running(level, STATION) != null, "the chapters were told in chat"), 40),
				new Step("02_story_tab", io.github.jcondedata.aliveworkplace.hall.QuestJournal.CHAPTER_ROW + 2, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUESTS, player);
						m.press(io.github.jcondedata.aliveworkplace.hall.QuestJournal.Tab.STORY.slot, player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.QuestJournal.CHAPTER_ROW + 1).is(Items.ENCHANTED_BOOK)
							&& m.icon(io.github.jcondedata.aliveworkplace.hall.QuestJournal.CHAPTER_ROW + 2).is(Items.WRITABLE_BOOK)
							&& m.icon(io.github.jcondedata.aliveworkplace.hall.QuestJournal.STORY_QUEST_SLOTS[0]).is(Items.OAK_LOG),
							"the Story tab ticks the first chapter and shows the second with its quest");
					}
				}, 30),
				new Step("03_story_chronicle", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.CHRONICLE, player);
					}
					Showcase.check(level.getBlockEntity(STATION) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity hall
						&& hall.chronicle().stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.STORY),
						"the chronicle keeps the story's chapters");
				}, 30)),
			(level, player) -> true));
		// Long Shifts proclaimed (ROADMAP 30.3): the builder's line in the hall's list says "long shifts" and 20% faster,
		// and the chronicle keeps the proclamation.
		SCREENS.put("long_shifts", new Screen("Long Shifts was proclaimed: the hall's list shows the \"long shifts\" mood and the chronicle keeps it",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				subject = worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				var shifts = io.github.jcondedata.aliveworkplace.hall.Edicts.find("long_shifts").orElseThrow();
				var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, STATION, player, shifts);
				Showcase.check(told.done(), "Long Shifts was proclaimed: " + told.message().getString());
				io.github.jcondedata.aliveworkplace.people.Moods.forget();
			},
			List.of(new Step("01_long_shifts_list", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON, 6, (level, player) -> {
					// The hall's POI is only registered after the staging tick, so the villager's hall was remembered as none
					// for 200 ticks (CivicEffects.HALL_TICKS): look it up again now (B72).
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					io.github.jcondedata.aliveworkplace.people.Moods.forget();
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					var mood = io.github.jcondedata.aliveworkplace.people.Moods.of(subject);
					Showcase.check(mood != null && mood.bad().stream().anyMatch(c -> c.getString().equals("long shifts")),
						"the builder's mood lists \"long shifts\": " + (mood == null ? "no mood" : mood.bad()));
					var pace = io.github.jcondedata.aliveworkplace.work.Pace.describe(subject);
					Showcase.check(pace != null && pace.getString().contains("the Long Shifts edict"),
						"the builder works faster under Long Shifts: " + (pace == null ? "usual pace" : pace.getString()));
				}, 30),
				new Step("02_long_shifts_chronicle", -1, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.CHRONICLE, player);
					}
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					Showcase.check(hall.chronicle().stream().anyMatch(e -> e.text().getString().equals("The edict Long Shifts was proclaimed")),
						"the chronicle keeps the proclamation");
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		// The Book of Edicts (ROADMAP 30.4): a Town with Long Shifts in force, two free slots and one locked for a City,
		// every edict below; at GUI scale 2, then at 4.
		SCREENS.put("edicts", new Screen("the Book of Edicts opened from the hall's lectern button, with Long Shifts in force, at GUI scales 2 and 4",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN);
				hall.setEdicts(List.of());
				var shifts = io.github.jcondedata.aliveworkplace.hall.Edicts.find("long_shifts").orElseThrow();
				var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, STATION, player, shifts);
				Showcase.check(told.done(), "Long Shifts was proclaimed: " + told.message().getString());
			},
			List.of(new Step("01_edicts_book", io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0], 6, (level, player) -> {
					Minecraft.getInstance().execute(() -> Minecraft.getInstance().options.guiScale().set(2));
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.BOOK).is(Items.LECTERN), "slot 9 is the Book of Edicts");
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.BOOK, player);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0]).is(Items.CLOCK), "Long Shifts sits in the first slot");
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[3]).is(Items.GRAY_DYE), "a Town's fourth slot is locked");
					}
				}, 30),
				new Step("02_edicts_scale4", io.github.jcondedata.aliveworkplace.hall.EdictBook.FIRST_EDICT, 6, (level, player) -> {
					// GUI scale 4 needs a window of at least 1280x960; the option only takes 4 once the window is that big.
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080));
					new Thread(() -> {
						try {
							Thread.sleep(400);
						} catch (InterruptedException ignored) {
						}
						mc.execute(() -> {
							mc.options.guiScale().set(4);
							mc.resizeDisplay();
						});
					}, "edicts-scale").start();
					io.github.jcondedata.aliveworkplace.hall.EdictBook.open(player, STATION);
				}, 40)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU)); // the hall's own screen (30.4a)
		// Reforms (ROADMAP 30.5): Long Shifts in force puts The Shift Bell's step on the quest page (the row below the daily
		// quests, a book and quill); its three steps handed in, fireworks go up over the hall and the chronicle keeps it.
		SCREENS.put("reform", new Screen("Long Shifts' reform step was on the quest page; its three steps handed in, fireworks went up over the hall and the chronicle kept the reform",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.HAMLET);
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
				var shifts = io.github.jcondedata.aliveworkplace.hall.Edicts.find("long_shifts").orElseThrow();
				var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, STATION, player, shifts);
				Showcase.check(told.done(), "Long Shifts was proclaimed: " + told.message().getString());
				player.getInventory().add(new ItemStack(Items.CLOCK, 24));
			},
			List.of(new Step("01_reform_step", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.reformSlots(1)[0], 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUESTS, player);
						ItemStack step = m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.reformSlots(1)[0]);
						Showcase.check(step.is(Items.WRITABLE_BOOK) && step.getHoverName().getString().equals("The Shift Bell, step 1 of 3"),
							"the quest page shows The Shift Bell's first step: " + step.getHoverName().getString());
					}
				}, 30),
				new Step("02_reform_fireworks", -1, 0, (level, player) -> {
					player.closeContainer();
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					String id = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("long_shifts").toString();
					ItemStack[] hand = {new ItemStack(Items.CLOCK, 24), new ItemStack(Items.GOLD_INGOT, 128), new ItemStack(Items.BREAD, 300)};
					player.getInventory().clearContent();
					for (int i = 0; i < hand.length; i++) {
						// Staged: the next step is due now rather than tomorrow morning.
						var progress = io.github.jcondedata.aliveworkplace.hall.Reforms.progress(hall, id);
						hall.setReforms(List.of(new io.github.jcondedata.aliveworkplace.hall.Reforms.Progress(id, progress.step(), false, 0)));
						io.github.jcondedata.aliveworkplace.hall.Reforms.round(level, STATION, hall);
						var shown = io.github.jcondedata.aliveworkplace.hall.Reforms.shown(hall);
						player.getInventory().add(hand[i].copy());
						if (!shown.isEmpty()) {
							io.github.jcondedata.aliveworkplace.hall.VillageQuests.handIn(player, STATION, shown.get(0).id());
						}
					}
					Showcase.check(io.github.jcondedata.aliveworkplace.hall.Reforms.reformed(hall, id), "Long Shifts was reformed: " + hall.reforms());
					Showcase.check(!level.getEntitiesOfClass(net.minecraft.world.entity.projectile.FireworkRocketEntity.class,
						new net.minecraft.world.phys.AABB(STATION).inflate(8)).isEmpty(), "fireworks went up over the hall");
					// Step back and look up at the sky over the hall.
					player.teleportTo(level, 0.5, -59, 14.5, 180f, -40f);
				}, 40),
				new Step("03_reform_chronicle", -1, 6, (level, player) -> {
					player.teleportTo(level, 2.5, -60, 4.5, 157f, 20f);
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.CHRONICLE, player);
					}
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					Showcase.check(hall.chronicle().stream().anyMatch(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.REFORM
						&& e.text().getString().equals("The edict Long Shifts was reformed: The Shift Bell")), "the chronicle keeps the reform");
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		// Free Bread (ROADMAP 30.6): everyone fed in the last day is 10 happier, so the builder's line in the hall's list
		// says "free bread"; the Book of Edicts tells its cost (the village eats 30% more) and its reform, The Common Granary.
		SCREENS.put("free_bread", new Screen("Free Bread was proclaimed: the hall's list shows the \"free bread\" mood and the Book tells its cost and reform",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.HAMLET);
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
				subject = worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION); // (new to the village: they ate before they came)
				var bread = io.github.jcondedata.aliveworkplace.hall.Edicts.find("free_bread").orElseThrow();
				var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, STATION, player, bread);
				Showcase.check(told.done(), "Free Bread was proclaimed: " + told.message().getString());
				io.github.jcondedata.aliveworkplace.people.Moods.forget();
			},
			List.of(new Step("01_free_bread_list", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_PERSON, 6, (level, player) -> {
					// The hall's POI is only registered after the staging tick, so the villager's hall was remembered as none
					// for 200 ticks (CivicEffects.HALL_TICKS): look it up again now (B72).
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					io.github.jcondedata.aliveworkplace.people.Moods.forget();
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					var mood = io.github.jcondedata.aliveworkplace.people.Moods.of(subject);
					Showcase.check(mood != null && mood.good().stream().anyMatch(c -> c.getString().equals("free bread")),
						"the fed builder's mood lists \"free bread\": " + (mood == null ? "no mood" : mood.good()));
				}, 30),
				new Step("02_free_bread_book", io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0], 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.EdictBook.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0]).is(Items.BREAD), "Free Bread sits in the first slot");
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		// Large Families (ROADMAP 30.6): up to two babies a day. A couple by three free beds and 48 meals in the store
		// have a baby, and half a day later (staged: the second birth is due now) another; the chronicle keeps both.
		SCREENS.put("large_families", new Screen("Large Families was proclaimed: two babies were born in one day and the chronicle kept both births",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.HAMLET);
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
				for (int x = -5; x <= -1; x += 2) {
					level.setBlockAndUpdate(new BlockPos(x, -60, -3), Blocks.RED_BED.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
					level.setBlockAndUpdate(new BlockPos(x, -60, -4), Blocks.RED_BED.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
				}
				place(level, new BlockPos(4, -60, -3), ModBlocks.STOREHOUSE);
				chest(level, new BlockPos(5, -60, -3), new ItemStack(Items.BREAD, 64));
				for (int x = -1; x <= 1; x += 2) {
					Villager parent = EntityType.VILLAGER.spawn(level, STATION.south(2).east(x), MobSpawnType.COMMAND);
					parent.setNoAi(true);
				}
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				var families = io.github.jcondedata.aliveworkplace.hall.Edicts.find("large_families").orElseThrow();
				var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, STATION, player, families);
				Showcase.check(told.done(), "Large Families was proclaimed: " + told.message().getString());
			},
			List.of(new Step("01_large_families_babies", -1, 0, (level, player) -> {
					player.closeContainer();
					var happy = new io.github.jcondedata.aliveworkplace.hall.VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);
					long gap = io.github.jcondedata.aliveworkplace.hall.VillageGrowth.every(level, STATION);
					Showcase.check(gap == io.github.jcondedata.aliveworkplace.hall.VillageGrowth.EVERY / 2, "babies come half a day apart: " + gap + " ticks");
					Villager first = io.github.jcondedata.aliveworkplace.hall.VillageGrowth.grow(level, STATION, happy, 0);
					// Staged: the first baby came half a day ago, so the second is due now.
					Villager second = io.github.jcondedata.aliveworkplace.hall.VillageGrowth.grow(level, STATION, happy, level.getGameTime() - gap);
					Showcase.check(first != null && second != null, "two babies were born in one day");
					for (Villager baby : new Villager[] {first, second}) {
						if (baby != null) {
							baby.setNoAi(true);
						}
					}
				}, 40),
				new Step("02_large_families_chronicle", -1, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.CHRONICLE, player);
					}
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					long births = hall.chronicle().stream().filter(e -> e.kind() == io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.BIRTH
						&& e.text().getString().startsWith("A baby was born to ")).count();
					Showcase.check(births >= 2, "the chronicle keeps both births: " + births);
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		// Open Gates (ROADMAP 30.7): inns take 4 guests (not 2) and up to two travellers arrive a morning. An innkeeper by
		// five free beds takes in two travellers one morning and two the next (the inn's own rounds), and is then full;
		// the Book of Edicts tells the boost, the bandits' cost and the reform, The Watchful Gate.
		SCREENS.put("open_gates", new Screen("Open Gates was proclaimed: the inn took in two travellers a morning and is full with four guests",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.HAMLET);
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
				ScreenshotHarness.fileRecords(level, STATION, 1); // the hall's record now, not in vanilla's next task, so the innkeeper finds her village
				for (int x = -4; x <= 4; x += 2) {
					level.setBlockAndUpdate(new BlockPos(x, -60, -5), Blocks.RED_BED.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
					level.setBlockAndUpdate(new BlockPos(x, -60, -6), Blocks.RED_BED.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
				}
				subject = worker(level, new BlockPos(-3, -60, -2), ModBlocks.INN_COUNTER, ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
				subject.setNoAi(true);
				var gates = io.github.jcondedata.aliveworkplace.hall.Edicts.find("open_gates").orElseThrow();
				var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, STATION, player, gates);
				Showcase.check(told.done(), "Open Gates was proclaimed: " + told.message().getString());
				io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
			},
			List.of(new Step("01_open_gates_inn", -1, 0, (level, player) -> {
					player.closeContainer();
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget(); // a village looked up before the hall's record was filed is kept 200 ticks
					BlockPos counter = new BlockPos(-3, -60, -2);
					long morning = (level.getDayTime() / 24000 + 1) * 24000 + 1000;
					// Two mornings of the inn's rounds: two travellers each.
					for (long day = 0; day < 2; day++) {
						level.setDayTime(morning + day * 24000);
						for (int round = 0; round < 3; round++) {
							io.github.jcondedata.aliveworkplace.inn.Innkeepers.tend(level, subject, counter);
						}
					}
					List<Villager> guests = io.github.jcondedata.aliveworkplace.inn.Innkeepers.guests(level, counter);
					// Held where they stand for the picture, side by side in front of the hall.
					for (int i = 0; i < guests.size(); i++) {
						Villager guest = guests.get(i);
						guest.setNoAi(true);
						guest.moveTo(-2.5 + 2 * i, -60, 1.5, 180f, 0f);
						guest.setYHeadRot(180f);
					}
					Showcase.check(guests.size() == 4, "the inn took in four guests over two mornings: " + guests.size());
					level.setDayTime(morning + 2 * 24000);
					Showcase.check(io.github.jcondedata.aliveworkplace.inn.Innkeepers.tend(level, subject, counter).equals("full"),
						"a fifth guest is turned away: the inn is full");
				}, 40),
				new Step("02_open_gates_book", io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0], 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.EdictBook.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0]).is(Items.OAK_FENCE_GATE), "Open Gates sits in the first slot");
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		// Festival Season (ROADMAP 30.8): a festival every 4 days, paid for by the treasury (3 emeralds and 1 for every 4
		// villagers: 5 for this village of 8). The hall's festival icon before (10 emeralds put by) and after the festival
		// morning's round took its 5, and the name tag's treasury line after.
		SCREENS.put("festival_season", new Screen("Festival Season was proclaimed: the festival morning took its 5 emeralds from the treasury",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.HAMLET);
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
				hall.setFestivalDay(-1);
				hall.setTreasury(1000); // 10 emeralds put by
				// The evening, so no round plans the festival before the pictures ask for it.
				level.setDayTime((level.getDayTime() / 24000) * 24000 + io.github.jcondedata.aliveworkplace.hall.Festivals.END + 500);
				for (int i = 0; i < 8; i++) {
					Villager v = EntityType.VILLAGER.spawn(level, new BlockPos(-4 + (i % 4) * 2, -60, -4 - (i / 4) * 2), MobSpawnType.COMMAND);
					v.setNoAi(true);
				}
				var season = io.github.jcondedata.aliveworkplace.hall.Edicts.find("festival_season").orElseThrow();
				var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, STATION, player, season);
				Showcase.check(told.done(), "Festival Season was proclaimed: " + told.message().getString());
				io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
			},
			List.of(new Step("01_festival_season_before", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FESTIVAL, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					Showcase.check(io.github.jcondedata.aliveworkplace.hall.Festivals.cost(hall, 8) == 5 && hall.treasury() == 1000,
						"before: a festival costs 5 emeralds, the treasury holds 10 (" + hall.treasury() + " hundredths)");
				}, 30),
				new Step("02_festival_season_after", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FESTIVAL, 6, (level, player) -> {
					player.closeContainer();
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					long day = io.github.jcondedata.aliveworkplace.hall.Festivals.nextDay(level, STATION, hall);
					level.setDayTime((day - 1) * 24000 + 1000); // that festival's morning
					io.github.jcondedata.aliveworkplace.hall.Festivals.round(level, STATION, hall,
						io.github.jcondedata.aliveworkplace.hall.VillageHalls.census(level, STATION).villagers());
					Showcase.check(hall.festivalDay() == day && hall.treasury() == 500, "the festival morning took 5 emeralds: festival day "
						+ hall.festivalDay() + " (expected " + day + "), treasury " + hall.treasury() + " hundredths");
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
				}, 30),
				new Step("03_festival_season_treasury", 0, 6, (level, player) ->
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION), 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		// Tithe (ROADMAP 30.8): a librarian's emerald prices are 10% higher (20 is 22, 9 is 10, 5 is 6, 4 stays 4), and a
		// tenth of what players pay goes into the treasury. Her trade screen without the edict, then with it.
		SCREENS.put("tithe", new Screen("Tithe was proclaimed: the librarian's 20-emerald trade costs 22 and her 4-emerald one still 4",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.HAMLET);
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
				subject = worker(level, new BlockPos(2, -60, -2), Blocks.LECTERN, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
				subject.setNoAi(true);
				subject.setVillagerData(subject.getVillagerData().setLevel(3));
				var offers = new net.minecraft.world.item.trading.MerchantOffers();
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 20),
					new ItemStack(Items.BOOKSHELF, 3), 12, 5, 0.05f));
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 9),
					new ItemStack(Items.CLOCK), 12, 5, 0.05f));
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 5),
					new ItemStack(Items.COMPASS), 12, 5, 0.05f));
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 4),
					new ItemStack(Items.LANTERN), 12, 1, 0.05f));
				subject.setOffers(offers);
				io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
			},
			List.of(new Step("01_tithe_without", -1, 0, (level, player) -> {
					subject.mobInteract(player, InteractionHand.MAIN_HAND);
					Showcase.check(subject.getOffers().get(0).getCostA().getCount() == 20, "without the Tithe: 20 emeralds, got "
						+ subject.getOffers().get(0).getCostA().getCount());
				}, 30),
				new Step("02_tithe_with", -1, 0, (level, player) -> {
					player.closeContainer();
					var tithe = io.github.jcondedata.aliveworkplace.hall.Edicts.find("tithe").orElseThrow();
					var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, STATION, player, tithe);
					Showcase.check(told.done(), "Tithe was proclaimed: " + told.message().getString());
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
					subject.mobInteract(player, InteractionHand.MAIN_HAND);
					var offers = subject.getOffers();
					Showcase.check(offers.get(0).getCostA().getCount() == 22 && offers.get(1).getCostA().getCount() == 10
						&& offers.get(2).getCostA().getCount() == 6 && offers.get(3).getCostA().getCount() == 4,
						"with the Tithe: 22, 10, 6 and 4 emeralds, got " + offers.get(0).getCostA().getCount() + ", " + offers.get(1).getCostA().getCount()
							+ ", " + offers.get(2).getCostA().getCount() + ", " + offers.get(3).getCostA().getCount());
				}, 30)),
			(level, player) -> player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu));
		// Curfew (ROADMAP 30.9): at dusk every grown villager but the guards goes to bed. Three villagers out in the street
		// by their cabins at dusk; the bell (Curfew) is proclaimed, and as the evening comes they go indoors and sleep,
		// the street empties and the guard keeps watch; then the Book of Edicts tells the boost, the cost and the reform.
		SCREENS.put("curfew", new Screen("Curfew was proclaimed: at dusk the villagers went to bed, the street emptied and the guard kept watch",
			new Vec3(0.5, -56.8, 8.5), new Vec3(0, -59.5, -3),
			(level, player) -> {
				level.setBlockAndUpdate(STATION.offset(-7, 0, 0), ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION.offset(-7, 0, 0));
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.VILLAGE);
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
				level.setDayTime((level.getDayTime() / 24000) * 24000 + 11000);
				curfewSleepers.clear();
				VillagerProfession[] jobs = {VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.FLETCHER};
				for (int i = 0; i < 3; i++) {
					int x = -4 + 4 * i;
					// A cabin: spruce walls round a bed, a slab roof, a lantern by the open front. Three blocks high inside, so a
					// villager standing on the bed's foot (a block up the path to its head) has room under the roof.
					for (int y = -60; y <= -58; y++) {
						for (int z = -7; z <= -4; z++) {
							level.setBlockAndUpdate(new BlockPos(x - 1, y, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
							level.setBlockAndUpdate(new BlockPos(x + 1, y, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
						}
						level.setBlockAndUpdate(new BlockPos(x, y, -7), Blocks.SPRUCE_PLANKS.defaultBlockState());
					}
					for (int dx = -1; dx <= 1; dx++) {
						for (int z = -7; z <= -4; z++) {
							level.setBlockAndUpdate(new BlockPos(x + dx, -57, z), Blocks.SPRUCE_SLAB.defaultBlockState());
						}
					}
					level.setBlockAndUpdate(new BlockPos(x + 1, -57, -3), Blocks.LANTERN.defaultBlockState());
					BlockPos head = new BlockPos(x, -60, -6);
					level.setBlockAndUpdate(head.south(), Blocks.RED_BED.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
					level.setBlockAndUpdate(head, Blocks.RED_BED.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
						.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
					Villager v = EntityType.VILLAGER.spawn(level, new BlockPos(x, -60, -1), MobSpawnType.COMMAND);
					v.setVillagerData(v.getVillagerData().setProfession(jobs[i]).setLevel(2));
					v.setVillagerXp(10);
					v.refreshBrain(level);
					ScreenshotHarness.fileRecords(level, head, 1); // the bed's record now, not in vanilla's next task, so it can be claimed
					level.getPoiManager().take(t -> t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), (t, p) -> p.equals(head), head, 4);
					v.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME, net.minecraft.core.GlobalPos.of(level.dimension(), head));
					curfewSleepers.add(v);
				}
				subject = worker(level, new BlockPos(6, -60, 2), Blocks.GRINDSTONE, net.minecraft.world.entity.ai.village.poi.PoiTypes.WEAPONSMITH, ModVillagers.GUARD);
				subject.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				var curfew = io.github.jcondedata.aliveworkplace.hall.Edicts.find("curfew").orElseThrow();
				ScreenshotHarness.fileRecords(level, STATION.offset(-7, 0, 0), 1); // the hall's record now, so the villagers find their village
				var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, STATION.offset(-7, 0, 0), player, curfew);
				Showcase.check(told.done(), "Curfew was proclaimed: " + told.message().getString());
				io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
			},
			List.of(new Step("01_curfew_evening", -1, 0, (level, player) -> {
					player.closeContainer();
					level.setDayTime((level.getDayTime() / 24000) * 24000 + 11800);
				}, 30),
				// The guard's night patrol picks random spots within 6 blocks of his post, cabin doorways too, and stands there a
				// few seconds: on GitHub (B72) he stood in front of a cabin with a villager still outside it at the check after
				// 200 ticks. 400 lets a villager held up by him reach the bed once he moves on; the check still wants all three.
				new Step("02_curfew_dusk", -1, 0, (level, player) ->
					level.setDayTime((level.getDayTime() / 24000) * 24000 + io.github.jcondedata.aliveworkplace.hall.Curfew.DUSK + 20), 400),
				new Step("03_curfew_book", io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0], 6, (level, player) -> {
					long asleep = curfewSleepers.stream().filter(io.github.jcondedata.aliveworkplace.hall.Curfew::asleepInBed).count();
					StringBuilder awake = new StringBuilder();
					for (Villager v : curfewSleepers) {
						if (!io.github.jcondedata.aliveworkplace.hall.Curfew.asleepInBed(v)) {
							awake.append(" (awake: ").append(v.getVillagerData().getProfession()).append(" at ").append(v.blockPosition().toShortString())
								.append(", resting ").append(v.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.REST))
								.append(", home ").append(v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME).map(h -> h.pos().toShortString()).orElse("none"))
								.append(", time ").append(level.getDayTime() % 24000).append(')');
						}
					}
					Showcase.check(asleep == 3, "every villager went to bed at dusk: " + asleep + " of 3 asleep" + awake);
					Showcase.check(!subject.isSleeping() && !io.github.jcondedata.aliveworkplace.hall.Curfew.keepsIn(subject), "the guard stayed on watch");
					player.teleportTo(-5.5, -60, 1.5);
					io.github.jcondedata.aliveworkplace.hall.EdictBook.open(player, STATION.offset(-7, 0, 0));
					if (player.containerMenu instanceof ChoiceMenu m) {
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0]).is(Items.BELL), "Curfew sits in the first slot");
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		// Conscription (ROADMAP 30.10): in a night raid every grown villager who isn't ill takes up a stone sword. A farmer,
		// a builder and a librarian in the street by the guard; the raiders come over the field, the three are handed the
		// militia's swords and beat them back beside the guard; then the Book of Edicts tells the boost, the cost and the
		// reform.
		SCREENS.put("conscription", new Screen("Conscription was proclaimed: in a night raid a farmer, a builder and a librarian took up swords and beat the raiders back beside the guard",
			new Vec3(0.5, -56.8, 8.5), new Vec3(0, -59.5, -3),
			(level, player) -> {
				BlockPos hallPos = STATION.offset(-7, 0, 0);
				level.setBlockAndUpdate(hallPos, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hallPos);
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.VILLAGE);
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
				hall.setRaidWorkUntil(0);
				level.setDayTime((level.getDayTime() / 24000) * 24000 + 14000);
				conscripts.clear();
				conscriptionRaiders.clear();
				conscriptsStruck.clear();
				watchConscriptBlows();
				VillagerProfession[] jobs = {VillagerProfession.FARMER, ModVillagers.BUILDER, VillagerProfession.LIBRARIAN};
				for (int i = 0; i < 3; i++) {
					Villager v = EntityType.VILLAGER.spawn(level, new BlockPos(-4 + 4 * i, -60, -1), MobSpawnType.COMMAND);
					v.setVillagerData(v.getVillagerData().setProfession(jobs[i]).setLevel(2));
					v.setVillagerXp(10);
					v.refreshBrain(level);
					conscripts.add(v);
				}
				// Lanterns along the street, so the night fight shows.
				for (int x = -6; x <= 6; x += 4) {
					level.setBlockAndUpdate(new BlockPos(x, -60, 1), Blocks.LANTERN.defaultBlockState());
				}
				subject = worker(level, new BlockPos(6, -60, 2), Blocks.GRINDSTONE, net.minecraft.world.entity.ai.village.poi.PoiTypes.WEAPONSMITH, ModVillagers.GUARD);
				subject.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				var conscription = io.github.jcondedata.aliveworkplace.hall.Edicts.find("conscription").orElseThrow();
				var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, hallPos, player, conscription);
				Showcase.check(told.done(), "Conscription was proclaimed: " + told.message().getString());
				io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
				io.github.jcondedata.aliveworkplace.hall.Conscription.forget();
			},
			List.of(new Step("01_conscription_raid", -1, 0, (level, player) -> {
					player.closeContainer();
					// The raiders come over the field: zombies of the village's raid, each a few steps in front of its
					// conscript (B296: from 7 blocks out the guard beside the librarian reached and killed his raider and
					// the builder's before the librarian landed a blow; every conscript fights at rest time, the
					// ConscriptionGameTests rest-time test shows it).
					for (int i = 0; i < 3; i++) {
						var zombie = EntityType.ZOMBIE.spawn(level, new BlockPos(-4 + 4 * i, -60, -4), MobSpawnType.COMMAND);
						zombie.addTag(io.github.jcondedata.aliveworkplace.guard.VillageRaids.TAG);
						zombie.setPersistenceRequired();
						zombie.setHealth(12f);
						zombie.setTarget(conscripts.get(i));
						conscriptionRaiders.add(zombie);
					}
					io.github.jcondedata.aliveworkplace.guard.VillageRaids.track(level, STATION.offset(-7, 0, 0), 3);
					io.github.jcondedata.aliveworkplace.hall.Conscription.forget();
					// The hall's POI is only registered after the staging tick, so the villagers' hall was remembered as none (B72).
					io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
				}, 40),
				new Step("02_conscription_fight", -1, 0, (level, player) -> {
					long armed = conscripts.stream().filter(v -> io.github.jcondedata.aliveworkplace.hall.Conscription.isMilitiaSword(v.getMainHandItem())).count();
					Showcase.check(armed == 3, "the three took up the militia's swords: " + armed + " of 3 (" + conscriptHands() + ")");
					io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[showcase] conscription fight starts: {}", conscriptionState());
				}, 160),
				new Step("03_conscription_book", io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0], 6, (level, player) -> {
					long struck = conscripts.stream().filter(conscriptsStruck::contains).count();
					Showcase.check(struck == 3, "the conscripts struck the raiders: " + struck + " of 3 (" + conscriptionState() + ")");
					Showcase.check(conscripts.stream().anyMatch(io.github.jcondedata.aliveworkplace.hall.Conscription::workStopped), "no work in the raid");
					player.teleportTo(-5.5, -60, 1.5);
					io.github.jcondedata.aliveworkplace.hall.EdictBook.open(player, STATION.offset(-7, 0, 0));
					if (player.containerMenu instanceof ChoiceMenu m) {
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0]).is(Items.STONE_SWORD), "Conscription sits in the first slot");
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		// Classes at the Village Hall (ROADMAP 34.6): a Peasant couple rises to Artisan at dawn (golden sparkles at their door,
		// a chime, the chat line), then the hall's Classes tab and page with the real ladder's needs counted.
		SCREENS.put("classes", new Screen("a Peasant couple rose to Artisan at dawn with golden sparkles and a chat line, and the hall's Classes page counts each class's needs",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				io.github.jcondedata.aliveworkplace.people.SocialClasses.ENABLED = true;
				classPeople.clear();
				String[] names = {"Odo", "Pia", "Ann", "Bo", "Cy"};
				for (int i = 0; i < names.length; i++) {
					Villager v = bornSceneVillager(level, new BlockPos(-2 + i, -60, 3), names[i], false);
					io.github.jcondedata.aliveworkplace.people.SocialClasses.seed(v, io.github.jcondedata.aliveworkplace.people.SocialClasses.get(
						io.github.jcondedata.aliveworkplace.AliveWorkplace.id("peasant")));
					// Odo, Pia and Ann eat a varied diet; Bo and Cy only bread.
					io.github.jcondedata.aliveworkplace.registry.ModAttachments.RECENT_MEALS.set(v, i < 3
						? List.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"), net.minecraft.resources.ResourceLocation.withDefaultNamespace("baked_potato"), net.minecraft.resources.ResourceLocation.withDefaultNamespace("cooked_cod"))
						: List.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"), net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"), net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread")));
					classPeople.add(v);
				}
				Villager odo = classPeople.get(0);
				Villager pia = classPeople.get(1);
				io.github.jcondedata.aliveworkplace.registry.ModAttachments.PARTNER.set(odo,
					new io.github.jcondedata.aliveworkplace.people.Couples.Partner(pia.getUUID(), pia.getDisplayName(), 1, true));
				io.github.jcondedata.aliveworkplace.registry.ModAttachments.PARTNER.set(pia,
					new io.github.jcondedata.aliveworkplace.people.Couples.Partner(odo.getUUID(), odo.getDisplayName(), 1, true));
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
			},
			List.of(new Step("01_classes_rise", -1, 0, (level, player) -> {
					// Dawn. The scene's Artisans need only a varied diet (the real ones also want a home, services and work
					// clothes, which take days to set up): the couple has had it two dawns running, and rises.
					level.setDayTime((level.getDayTime() / 24000 + 1) * 24000);
					var files = io.github.jcondedata.aliveworkplace.people.SocialClasses.files(level.getServer().getResourceManager());
					var real = new java.util.HashMap<>(files);
					files.put(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("artisan"), com.google.gson.JsonParser.parseString(
						"{\"tier\": 1, \"tax\": 1.5, \"name\": {\"translate\": \"class.aliveworkplace.artisan\"}, \"needs\": [{\"type\": \"diet\", \"kind\": \"varied\"}]}"));
					io.github.jcondedata.aliveworkplace.people.SocialClasses.load(files);
					long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
					List<Villager> couple = classPeople.subList(0, 2);
					// B188: the hall's own dawn round may already have counted the couple on the day before (on a slow machine it runs
					// between the setup and this step), and a household counted that day is skipped; fresh Peasants start the two dawns.
					for (Villager v : couple) {
						io.github.jcondedata.aliveworkplace.people.SocialClasses.seed(v, io.github.jcondedata.aliveworkplace.people.SocialClasses.get(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("peasant")));
					}
					io.github.jcondedata.aliveworkplace.people.SocialClasses.check(level, STATION, couple, today - 1, 8);
					var rise = io.github.jcondedata.aliveworkplace.people.SocialClasses.check(level, STATION, couple, today, 8);
					Showcase.check(rise.changes().size() == 1 && rise.changes().get(0).rose(), "the couple rose to Artisan at dawn: " + rise.changes());
					io.github.jcondedata.aliveworkplace.people.SocialClasses.load(real); // the real ladder for the page
				}, 12),
				new Step("02_classes_tab", -2, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					pickedSlot = io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.ClassesPage.PAGE);
					var counts = io.github.jcondedata.aliveworkplace.hall.ClassesPage.counts(level, STATION);
					Showcase.check(counts.values().stream().mapToInt(Integer::intValue).sum() == 4, "four households counted on the tab: " + counts.values());
				}, 30),
				new Step("03_classes_page", -2, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.ClassesPage.PAGE), player);
						pickedSlot = -1;
						for (int slot = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
							if (m.icon(slot).getHoverName().getString().equals("Burgher")) {
								pickedSlot = slot; // the Burgher button: the Artisan couple closest to rising, and what they lack
							}
						}
						Showcase.check(pickedSlot >= 0, "the Classes page has a Burgher button");
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		// The Wonders page and the Wonder Plan (ROADMAP 35.3): a Town with a Master builder; the hall's Wonders tab shows the
		// test folly's card with every line ticked; its plans are drawn up (32 emeralds) and the plan, placed north of the hall,
		// shows the whole tower as see-through blocks.
		SCREENS.put("wonders_page", new Screen("the hall's Wonders page showed the Test Folly's card with every requirement ticked and the cap; Draw up the plans took 32 emeralds and gave a Wonder Plan, which placed north of the hall shows the whole tower as see-through blocks",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getName().getString());
				holdWonderTown(level); // 35.3: the round would count a Hamlet
				hall.setTreasury(0);
				io.github.jcondedata.aliveworkplace.wonder.Wonders.LEGENDS = false; // the Master Architect's stand-in: a Master builder
				String[] names = {"Odo", "Pia"};
				for (int i = 0; i < names.length; i++) {
					Villager v = EntityType.VILLAGER.create(level);
					v.moveTo(-2.5 + i * 4, -60, 3.5, 180, 0);
					v.setNoAi(true);
					v.setCustomName(Component.literal(names[i]));
					v.setVillagerData(v.getVillagerData().setProfession(i == 0 ? io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER
						: net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(i == 0 ? 5 : 2));
					level.addFreshEntity(v);
				}
				player.getInventory().clearContent();
				player.getInventory().add(new ItemStack(Items.EMERALD, 32));
			},
			List.of(new Step("01_wonders_page", -2, 6, (level, player) -> {
					holdWonderTown(level);
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					var folly = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
					int index = new java.util.ArrayList<>(io.github.jcondedata.aliveworkplace.wonder.Wonders.all()).stream().map(w -> w.id()).toList().indexOf(folly);
					Showcase.check(index >= 0, "the Test Folly is on the list: " + io.github.jcondedata.aliveworkplace.wonder.Wonders.loaded().keySet());
					pickedSlot = -1;
					if (player.containerMenu instanceof ChoiceMenu m && index >= 0) {
						m.press(io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.wonder.WondersPage.PAGE), player);
						pickedSlot = io.github.jcondedata.aliveworkplace.wonder.WondersPage.slot(index);
						Showcase.check(m.icon(pickedSlot).getHoverName().getString().equals("Test Folly"), "the folly's card: " + m.icon(pickedSlot).getHoverName().getString());
						Showcase.check(io.github.jcondedata.aliveworkplace.wonder.WondersPage.cards(level, STATION).get(index).ready(), "every line of the folly's card ticked");
					}
				}, 40),
				new Step("02_wonder_plan_preview", -1, 0, (level, player) -> {
					holdWonderTown(level);
					var folly = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
					player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // the price really paid (free in creative); the hover below flies again
					var drawn = io.github.jcondedata.aliveworkplace.wonder.WonderPlans.draw(level, STATION, player, folly);
					Showcase.check(drawn.drawn() && player.getInventory().countItem(Items.EMERALD) == 0, "the plans drawn up for 32 emeralds: " + drawn.message().getString());
					player.closeContainer();
					player.getInventory().clearContent();
					ItemStack plan = drawn.plan().copy();
					var data = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(plan).orElseThrow();
					var placement = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.placementAt(level.dimension().location(), data.size().orElseThrow(),
						STATION.offset(0, 0, -6), net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
					var plans = io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem.plan(plan).orElseThrow();
					Showcase.check(io.github.jcondedata.aliveworkplace.wonder.WonderPlans.placeRefusal(level, plans, placement, data.size().get()).isEmpty(), "the folly may go north of the hall");
					plan.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, data.withPlacement(java.util.Optional.of(placement)));
					player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, plan);
					ScreenshotHarness.hoverLookingAt(player, new Vec3(13.5, -52, 12.5), new Vec3(0, -53, -10));
				}, 80)),
			(level, player) -> player.getMainHandItem().is(io.github.jcondedata.aliveworkplace.registry.ModItems.WONDER_PLAN)
				&& io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(player.getMainHandItem()).flatMap(d -> d.placement()).isPresent()));
		// The Foundation Stone (ROADMAP 35.4): a Town's builder at his bench, the folly's plan placed north of the hall; the
		// plan handed to him lays the Foundation Stone at the folly's front corner and stage I begins from the chests by the
		// stone; right-clicking the stone tells the stage, how far along it is and what the chests hold.
		SCREENS.put("wonder_site", new Screen("a Wonder Plan placed north of the hall was handed to the village's builder: the Foundation Stone (carved stone, brass plaque) went down at the folly's front corner, the start was announced in chat, and stage I, the Ground Story, began from the chests by the stone; right-clicking the stone told the stage, its progress and its material list",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getName().getString());
				holdWonderTown(level); // 35.3: the round would count a Hamlet
				level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILD_DELAY).set(2, level.getServer());
				BlockPos bench = STATION.offset(-4, 0, -2);
				level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
				Villager builder = EntityType.VILLAGER.create(level);
				builder.moveTo(bench.getX() + 1.5, bench.getY(), bench.getZ() + 0.5, 180, 0);
				builder.setCustomName(Component.literal("Odo"));
				builder.setVillagerData(builder.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER).setLevel(3));
				level.addFreshEntity(builder);
				io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, bench);
				wonderBuilder[0] = builder;
				var folly = WONDER_FOLLY;
				var wonder = io.github.jcondedata.aliveworkplace.wonder.Wonders.loaded().get(folly);
				Showcase.check(wonder != null, "the Test Folly is loaded");
				ItemStack plan = io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem.of(level, STATION, wonder, Component.literal("Testholm"));
				var data = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(plan).orElseThrow();
				var placement = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.placementAt(level.dimension().location(), data.size().orElseThrow(),
					STATION.offset(0, 0, -6), net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
				plan.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, data.withPlacement(java.util.Optional.of(placement)));
				player.getInventory().clearContent();
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, plan);
			},
			List.of(new Step("01_wonder_site_plan", -1, 0, (level, player) ->
					ScreenshotHarness.hoverLookingAt(player, new Vec3(9.5, -54, 6.5), new Vec3(-1, -57, -8)), 40),
				new Step("02_wonder_site_stone", -1, 0, (level, player) -> {
					holdWonderTown(level);
					var result = io.github.jcondedata.aliveworkplace.wonder.WonderStages.handOver(player, wonderBuilder[0], player.getMainHandItem());
					Showcase.check(result == net.minecraft.world.InteractionResult.SUCCESS, "the plan handed to Odo: " + result);
					var site = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION);
					Showcase.check(site.size() == 1 && site.get(0).stone().isPresent(), "the folly begun with its stone: " + site);
					BlockPos stone = site.get(0).stone().orElseThrow();
					Showcase.check(level.getBlockState(stone).is(ModBlocks.FOUNDATION_STONE), "the Foundation Stone went down at " + stone);
					// the stockpile: two chests beside the stone, with everything the folly takes
					var list = io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.checklist(level,
						new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData(io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints.idOf(WONDER_FOLLY),
							java.util.Optional.empty(), java.util.Optional.empty())).orElseThrow();
					BlockPos[] chests = {stone.east(2), stone.east(3)};
					{ // 35.7: every stage's goods in a chest of the stockpile, so each stage starts once it may
						BlockPos goods = stone.east(4).south(2);
						level.setBlockAndUpdate(goods, Blocks.CHEST.defaultBlockState());
						int g = 0;
						for (var stage : io.github.jcondedata.aliveworkplace.wonder.Wonders.loaded().get(WONDER_FOLLY).stages()) {
							for (var good : stage.goods()) {
								ItemStack one = io.github.jcondedata.aliveworkplace.wonder.WonderGoods.icon(good);
								for (int left = good.count(); left > 0; left -= one.getMaxStackSize()) {
									((net.minecraft.world.Container) level.getBlockEntity(goods)).setItem(g++, one.copyWithCount(Math.min(left, one.getMaxStackSize())));
								}
							}
						}
					}
					int c = 0;
					int slot = 0;
					for (var line : list.lines()) {
						for (int left = line.need(); left > 0 && c < chests.length; left -= line.item().getDefaultMaxStackSize()) {
							if (slot == 0) {
								level.setBlockAndUpdate(chests[c], Blocks.CHEST.defaultBlockState());
							}
							((net.minecraft.world.Container) level.getBlockEntity(chests[c])).setItem(slot, new ItemStack(line.item(), Math.min(left, line.item().getDefaultMaxStackSize())));
							if (++slot == 27) {
								slot = 0;
								c++;
							}
						}
					}
					ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(stone).add(3.5, 2.5, 4.5), Vec3.atCenterOf(stone));
				}, 120),
				new Step("03_wonder_site_status", -1, 0, (level, player) -> {
					BlockPos stone = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION).get(0).stone().orElseThrow();
					io.github.jcondedata.aliveworkplace.mc.Chat.system(player, io.github.jcondedata.aliveworkplace.wonder.WonderStages.statusText(level, stone));
					ScreenshotHarness.hoverLookingAt(player, new Vec3(9.5, -54, 6.5), new Vec3(-1, -58, -8));
				}, 300)),
			(level, player) -> {
				var sites = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION);
				if (sites.isEmpty() || sites.get(0).parts().isEmpty()) {
					return false;
				}
				var part = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).get(sites.get(0).parts().get(0));
				return sites.get(0).stage() == 1 && part != null && part.placed() > 0; // stage I under way
			}));
		// Crews (ROADMAP 35.5): ten builders of the village on the folly, each on a column of its own, led by the highest-level.
		SCREENS.put("wonder_crew", new Screen("ten builders of the village raised the folly together: the plan handed to one, every builder called from the stone, and each took a column of its own, never more than two layers above the lowest unfinished one; the stone named the lead and the crew of ten",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getName().getString());
				holdWonderTown(level); // 35.3: the round would count a Hamlet
				level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILD_DELAY).set(6, level.getServer());
				String[] names = {"Odo", "Pia", "Bram", "Ilse", "Tam", "Wren", "Edda", "Hob", "Mags", "Rolf"};
				crewBuilders.clear();
				for (int i = 0; i < names.length; i++) {
					BlockPos bench = STATION.offset(-9 + 2 * i, 0, 3);
					level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
					Villager builder = EntityType.VILLAGER.create(level);
					builder.moveTo(bench.getX() + 0.5, bench.getY(), bench.getZ() + 1.5, 180, 0);
					builder.setCustomName(Component.literal(names[i]));
					builder.setVillagerData(builder.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER).setLevel(2));
					level.addFreshEntity(builder);
					// B314: no gift by the UUID roll: a Night Owl (one villager in 240) idles through the scene's held day
					// and never comes to the crew
					io.github.jcondedata.aliveworkplace.legend.Gifted.set(builder, null);
					io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, bench);
					crewBuilders.add(builder);
				}
				var wonder = io.github.jcondedata.aliveworkplace.wonder.Wonders.loaded().get(WONDER_FOLLY);
				Showcase.check(wonder != null, "the Test Folly is loaded");
				ItemStack plan = io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem.of(level, STATION, wonder, Component.literal("Testholm"));
				var data = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(plan).orElseThrow();
				var placement = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.placementAt(level.dimension().location(), data.size().orElseThrow(),
					STATION.offset(0, 0, -6), net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
				plan.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, data.withPlacement(java.util.Optional.of(placement)));
				player.getInventory().clearContent();
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, plan);
				var result = io.github.jcondedata.aliveworkplace.wonder.WonderStages.handOver(player, crewBuilders.get(0), plan);
				Showcase.check(result == net.minecraft.world.InteractionResult.SUCCESS, "the plan handed to Odo: " + result);
				var site = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION).get(0);
				BlockPos stone = site.stone().orElseThrow();
				var list = io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.checklist(level,
					new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData(io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints.idOf(WONDER_FOLLY),
						java.util.Optional.empty(), java.util.Optional.empty())).orElseThrow();
				BlockPos[] chests = {stone.east(2), stone.east(3), stone.east(2).south(), stone.east(3).south()};
				{ // 35.7: every stage's goods in a chest of the stockpile, so each stage starts once it may
					BlockPos goods = stone.east(4).south(2);
					level.setBlockAndUpdate(goods, Blocks.CHEST.defaultBlockState());
					int g = 0;
					for (var stage : io.github.jcondedata.aliveworkplace.wonder.Wonders.loaded().get(WONDER_FOLLY).stages()) {
						for (var good : stage.goods()) {
							ItemStack one = io.github.jcondedata.aliveworkplace.wonder.WonderGoods.icon(good);
							for (int left = good.count(); left > 0; left -= one.getMaxStackSize()) {
								((net.minecraft.world.Container) level.getBlockEntity(goods)).setItem(g++, one.copyWithCount(Math.min(left, one.getMaxStackSize())));
							}
						}
					}
				}
				int c = 0;
				int slot = 0;
				for (var line : list.lines()) {
					for (int left = line.need() + 4; left > 0 && c < chests.length; left -= line.item().getDefaultMaxStackSize()) {
						if (slot == 0) {
							level.setBlockAndUpdate(chests[c], Blocks.CHEST.defaultBlockState());
						}
						((net.minecraft.world.Container) level.getBlockEntity(chests[c])).setItem(slot, new ItemStack(line.item(), Math.min(left, line.item().getDefaultMaxStackSize())));
						if (++slot == 27) {
							slot = 0;
							c++;
						}
					}
				}
				// the player calls from beside the stone (within its reach; the camera is farther off)
				player.teleportTo(stone.getX() + 1.5, stone.getY(), stone.getZ() + 2.5);
				Showcase.check(io.github.jcondedata.aliveworkplace.wonder.WonderCrews.call(player) == 1, "every builder called from the stone at " + stone.toShortString() + ", the player at " + player.blockPosition().toShortString());
			},
			List.of(new Step("01_wonder_crew_gathering", -1, 0, (level, player) ->
					ScreenshotHarness.hoverLookingAt(player, new Vec3(12.5, -52, 8.5), new Vec3(-1, -57, -8)), 200),
				new Step("02_wonder_crew_columns", -1, 0, (level, player) -> {
					var site = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION).get(0);
					var members = io.github.jcondedata.aliveworkplace.wonder.WonderCrews.crew(level, site);
					int crew = members.size();
					Showcase.check(crew == 10, "all ten builders on the crew: " + crew + (crew == 10 ? "" : crewMissing(level, site, members)));
					ScreenshotHarness.hoverLookingAt(player, new Vec3(10.5, -50, 2.5), new Vec3(-1, -57, -8));
				}, 400),
				new Step("03_wonder_crew_status", -1, 0, (level, player) -> {
					var site = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION).get(0);
					io.github.jcondedata.aliveworkplace.mc.Chat.system(player, io.github.jcondedata.aliveworkplace.wonder.WonderStages.statusText(level, site.stone().orElseThrow()));
					ScreenshotHarness.hoverLookingAt(player, new Vec3(9.5, -54, 6.5), new Vec3(-1, -58, -8));
				}, 200)),
			(level, player) -> {
				var sites = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION);
				if (sites.isEmpty()) {
					return false;
				}
				var site = sites.get(0);
				return io.github.jcondedata.aliveworkplace.wonder.WonderCrews.crew(level, site).size() >= 8 && site.called();
			}));
		// A Wonder's goods (ROADMAP 35.7): the folly's stage II waits for its three lines, the stone's screen half filled (the
		// flowers and one potion in the stockpile, the other potion handed in at the stone, the books short); the porter
		// carries the two books from the storehouse, and the stage is dedicated: the bell rings, the village gathers at the
		// stone, the Bard plays beside it and the guards stand in line.
		SCREENS.put("wonder_goods", new Screen("the folly's stage II waited for its three lines: the stone's screen showed the flowers and the potions in and the books short, \"no Librarian in the village\"; the porter carried the two books from the storehouse to the stockpile, and the stage was dedicated: the bell rang, the villagers off work gathered at the stone, the Bard played beside it, the guards stood in line, the goods were taken and the chronicle said so",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setDayTime(2000);
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getName().getString());
				holdWonderTown(level); // 35.3: the round would count a Hamlet
				var wonder = io.github.jcondedata.aliveworkplace.wonder.Wonders.loaded().get(WONDER_FOLLY);
				Showcase.check(wonder != null, "the Test Folly is loaded");
				ItemStack plan = io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem.of(level, STATION, wonder, Component.literal("Testholm"));
				var data = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(plan).orElseThrow();
				var placement = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.placementAt(level.dimension().location(), data.size().orElseThrow(),
					STATION.offset(0, 0, -6), net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
				BlockPos stone = io.github.jcondedata.aliveworkplace.wonder.WonderStages.stoneSpot(level, placement).orElseThrow();
				// stage I stands already
				level.getStructureManager().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly/stage_1_0"))
					.ifPresent(t -> t.placeInWorld(level, placement.origin(), placement.origin(),
						new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(placement.rotation()), level.random, 2));
				level.setBlockAndUpdate(stone, ModBlocks.FOUNDATION_STONE.defaultBlockState());
				var manager = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level);
				var site = manager.begin(WONDER_FOLLY, STATION, player.getUUID(), player.getName().getString(), io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level));
				manager.place(site.id(), placement, stone);
				manager.setStage(site.id(), 2);
				// the stockpile: the flowers and one potion of healing
				BlockPos pile = stone.east(2);
				level.setBlockAndUpdate(pile, Blocks.CHEST.defaultBlockState());
				var box = (net.minecraft.world.Container) level.getBlockEntity(pile);
				box.setItem(0, new ItemStack(Items.POPPY, 2));
				box.setItem(1, new ItemStack(Items.DANDELION, 2));
				box.setItem(2, net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING));
				// the storehouse and its porter (the two books come in once the stone's screen is read: the porter would
				// otherwise carry them over before the first shot)
				BlockPos storehouse = stone.east(9).south(5);
				level.setBlockAndUpdate(storehouse, ModBlocks.STOREHOUSE.defaultBlockState());
				level.setBlockAndUpdate(storehouse.east(), Blocks.CHEST.defaultBlockState());
				String[] names = {"Bram", "Tobin", "Ilse", "Rolf", "Mags"};
				net.minecraft.world.entity.npc.VillagerProfession[] jobs = {io.github.jcondedata.aliveworkplace.registry.ModVillagers.PORTER,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.BARD, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD,
					io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD, net.minecraft.world.entity.npc.VillagerProfession.NONE};
				BlockPos[] stations = {storehouse, stone.east(7).south(8), stone.east(11), stone.east(12).south(2), null};
				net.minecraft.world.level.block.Block[] blocks = {null, ModBlocks.MUSIC_STAND, ModBlocks.GUARD_POST, ModBlocks.GUARD_POST, null};
				for (int i = 0; i < names.length; i++) {
					Villager v = EntityType.VILLAGER.create(level);
					BlockPos at = stations[i] == null ? stone.east(6).south(10) : stations[i].south();
					v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180, 0);
					v.setCustomName(Component.literal(names[i]));
					level.addFreshEntity(v);
					if (stations[i] != null) {
						if (blocks[i] != null) {
							level.setBlockAndUpdate(stations[i], blocks[i].defaultBlockState());
						}
						var poi = i == 0 ? io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI
							: i == 1 ? io.github.jcondedata.aliveworkplace.registry.ModVillagers.MUSIC_STAND_POI : io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD_POST_POI;
						io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, v, stations[i], poi, jobs[i]);
					}
				}
				// the other potion, handed in at the stone
				player.getInventory().clearContent();
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING));
				Showcase.check(io.github.jcondedata.aliveworkplace.wonder.WonderGoods.handInAtStone(player, stone, player.getMainHandItem()), "the potion handed in at the stone");
			},
			List.of(new Step("01_wonder_goods_screen", -1, 0, (level, player) -> {
					var site = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION).get(0);
					BlockPos stone = site.stone().orElseThrow();
					var lines = io.github.jcondedata.aliveworkplace.wonder.WonderGoods.lines(level, site);
					Showcase.check(lines.size() == 3 && lines.get(0).full() && lines.get(1).full() && lines.get(2).in() == 0 && !site.started(),
						"the stone's lines half filled: flowers and potions in, the books short: " + lines + " (stage " + site.stage() + ", started " + site.started() + ", handed " + site.delivered() + ", stone " + stone.toShortString() + ")");
					io.github.jcondedata.aliveworkplace.mc.Chat.system(player, io.github.jcondedata.aliveworkplace.wonder.WonderStages.statusText(level, stone));
					// the two books reach the storehouse, for its porter to carry to the stockpile
					((net.minecraft.world.Container) level.getBlockEntity(stone.east(10).south(5))).setItem(0, new ItemStack(Items.BOOK, 2));
					ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(stone).add(5.5, 3.5, 6.5), Vec3.atCenterOf(stone));
				}, 60),
				new Step("02_wonder_goods_porter", -1, 0, (level, player) -> {
					BlockPos stone = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION).get(0).stone().orElseThrow();
					ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(stone).add(8.5, 5, 9.5), Vec3.atCenterOf(stone).add(4, 0, 2.5));
				}, 300),
				new Step("03_wonder_goods_dedication", -1, 0, (level, player) -> {
					BlockPos stone = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION).get(0).stone().orElseThrow();
					ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(stone).add(6.5, 4, 7.5), Vec3.atCenterOf(stone));
				}, 400)),
			(level, player) -> {
				var sites = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION);
				if (sites.isEmpty() || !sites.get(0).started()) {
					return false;
				}
				var porters = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(STATION).inflate(40),
					v -> v.getVillagerData().getProfession() == io.github.jcondedata.aliveworkplace.registry.ModVillagers.PORTER);
				return !porters.isEmpty() && io.github.jcondedata.aliveworkplace.registry.ModAttachments.ITEMS_CARRIED.getOrElse(porters.get(0), 0) >= 2
					&& io.github.jcondedata.aliveworkplace.wonder.Dedications.gathered(level, sites.get(0)) >= 2;
			}));
		// A Wonder's found goods (ROADMAP 35.7a, 35.7b): the Great Library's stage I asks for a Heart Pottery Sherd and a Dune
		// Armor Trim Template, found by exploring, and an Illuminated Book from the Print Shop. The sherd is in, the
		// Pathfinder settled in the village is searching for the template (day 5 of 9) and the book is short; then his
		// search ends and he brings the template, a little win on the way, told in chat and in the chronicle.
		SCREENS.put("wonder_finds", new Screen("the Great Library's stage I asked for things found by exploring, not stacks: the stone showed the Heart Pottery Sherd in, the Pathfinder searching for the Dune Armor Trim Template (day 5 of 9) and the Illuminated Book short; then the Pathfinder brought the template, chat and the chronicle said so, and the stone showed it brought by him",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setDayTime(2000);
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getName().getString());
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.TOWN);
				var library = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace", "great_library");
				Showcase.check(io.github.jcondedata.aliveworkplace.wonder.Wonders.loaded().containsKey(library), "the Great Library is loaded");
				var placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(),
					STATION.offset(-3, 0, -8), net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
				BlockPos stone = io.github.jcondedata.aliveworkplace.wonder.WonderStages.stoneSpot(level, placement).orElseThrow();
				level.setBlockAndUpdate(stone, ModBlocks.FOUNDATION_STONE.defaultBlockState());
				var manager = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level);
				var site = manager.begin(library, STATION, player.getUUID(), player.getName().getString(), io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level));
				manager.place(site.id(), placement, stone);
				// the stockpile: the sherd, brought home from the trail ruins
				BlockPos pile = stone.east(2);
				level.setBlockAndUpdate(pile, Blocks.CHEST.defaultBlockState());
				((net.minecraft.world.Container) level.getBlockEntity(pile)).setItem(0, new ItemStack(Items.HEART_POTTERY_SHERD));
				// the Pathfinder, settled by the stone, four days into his search
				var pathfinder = io.github.jcondedata.aliveworkplace.legend.Legends.get(
					net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace", "pathfinder")).orElseThrow();
				Villager him = EntityType.VILLAGER.create(level);
				BlockPos at = stone.east(1).south(2);
				him.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180, 0);
				him.setNoAi(true);
				level.addFreshEntity(him);
				io.github.jcondedata.aliveworkplace.legend.Legends.make(level, him, pathfinder, "found");
				io.github.jcondedata.aliveworkplace.wonder.WonderGoods.search(level, manager.site(site.id()).orElseThrow(),
					(int) (4 * io.github.jcondedata.aliveworkplace.wonder.WonderGoods.DAY));
			},
			List.of(new Step("01_wonder_finds_screen", -1, 0, (level, player) -> {
					var site = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION).get(0);
					BlockPos stone = site.stone().orElseThrow();
					var lines = io.github.jcondedata.aliveworkplace.wonder.WonderGoods.lines(level, site);
					Showcase.check(lines.size() == 3 && lines.get(0).full() && !lines.get(1).full() && lines.get(1).good().found() && !lines.get(2).full(),
						"the stone's lines: the sherd in, the template searched for, the book short: " + lines);
					io.github.jcondedata.aliveworkplace.mc.Chat.system(player, io.github.jcondedata.aliveworkplace.wonder.WonderStages.statusText(level, stone));
					ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(stone).add(4.5, 3, 5.5), Vec3.atCenterOf(stone).add(0, -3.5, 0)); // the stone and the Pathfinder's line above the chat (B141)
				}, 80),
				new Step("02_wonder_finds_brought", -1, 0, (level, player) -> {
					var manager = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level);
					var site = manager.in(STATION).get(0);
					BlockPos stone = site.stone().orElseThrow();
					io.github.jcondedata.aliveworkplace.wonder.WonderGoods.search(level, site, (int) (5 * io.github.jcondedata.aliveworkplace.wonder.WonderGoods.DAY));
					var now = manager.in(STATION).get(0);
					io.github.jcondedata.aliveworkplace.mc.Chat.system(player, io.github.jcondedata.aliveworkplace.wonder.WonderStages.statusText(level, stone));
					Showcase.check(io.github.jcondedata.aliveworkplace.wonder.WonderGoods.lines(level, now).get(1).brought(), "the Pathfinder brought the template");
					ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(stone).add(4.5, 3, 5.5), Vec3.atCenterOf(stone).add(0, -3.5, 0)); // the stone and the Pathfinder's line above the chat (B141)
				}, 80)),
			(level, player) -> {
				var sites = io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(level).in(STATION);
				return !sites.isEmpty() && io.github.jcondedata.aliveworkplace.wonder.WonderGoods.lines(level, sites.get(0)).get(1).brought();
			}));
		// Switching classes on (ROADMAP 34.22): a village from before 1.8 (a couple in a builder's cottage, three more without
		// a home), the hall's Classes tab before; then the hall's rounds seed them, the chronicle says so once, and the tab and
		// page show "Settling in: 7 days left" with what each class will need.
		SCREENS.put("class_switch_on", new Screen("a village saved before 1.8 found its places when classes were switched on: before, with classes still off, the hall's Classes tab had no one to count; the hall's rounds seeded everyone (the couple in a builder's cottage Artisans, the others Peasants), the chronicle said so once, and the tab and the Classes page said \"Settling in: 7 days left\" with each class's luxuries to come",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				io.github.jcondedata.aliveworkplace.people.SocialClasses.ENABLED = false; // a server still on 1.7: the switch is thrown in step 2
				classPeople.clear();
				// The couple's cottage: a builder's house (grade I) recorded up in the sky, out of the picture.
				net.minecraft.resources.ResourceLocation cottage = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_scene", "switch_on_cottage");
				BlockPos sky = STATION.above(150);
				level.getStructureManager().getOrCreate(cottage).fillFromWorld(level, sky.above(40), new net.minecraft.core.Vec3i(6, 5, 6), false, Blocks.AIR);
				io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(cottage,
					new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), sky,
						net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), java.util.UUID.randomUUID());
				io.github.jcondedata.aliveworkplace.people.ClassNeeds.services = (l, hall, home) -> java.util.Set.of(
					io.github.jcondedata.aliveworkplace.AliveWorkplace.id("chapel")); // a chapel reaches every home
				String[] names = {"Odo", "Pia", "Ann", "Bo", "Cy"};
				for (int i = 0; i < names.length; i++) {
					Villager v = bornSceneVillager(level, new BlockPos(-2 + i, -60, 3), names[i], false);
					io.github.jcondedata.aliveworkplace.registry.ModAttachments.LAST_MEAL.set(v, level.getGameTime());
					io.github.jcondedata.aliveworkplace.registry.ModAttachments.RECENT_MEALS.set(v, List.of(
						net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"), net.minecraft.resources.ResourceLocation.withDefaultNamespace("baked_potato"),
						net.minecraft.resources.ResourceLocation.withDefaultNamespace("cooked_cod")));
					classPeople.add(v);
				}
				Villager odo = classPeople.get(0);
				Villager pia = classPeople.get(1);
				io.github.jcondedata.aliveworkplace.registry.ModAttachments.PARTNER.set(odo,
					new io.github.jcondedata.aliveworkplace.people.Couples.Partner(pia.getUUID(), pia.getDisplayName(), 1, true));
				io.github.jcondedata.aliveworkplace.registry.ModAttachments.PARTNER.set(pia,
					new io.github.jcondedata.aliveworkplace.people.Couples.Partner(odo.getUUID(), odo.getDisplayName(), 1, true));
				// Their bed in the cottage: a HOME memory with no bed behind it houses nobody, so no home grade (B452).
				ScreenshotHarness.homeBed(level, sky.offset(2, 1, 2), Direction.NORTH, odo, pia);
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
			},
			List.of(new Step("01_class_switch_on_before", -2, 6, (level, player) -> {
					// Before: classes still off (as before the update; the hall's own round must not seed them yet), nobody has one.
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					Showcase.check(hall.classesVersion() == 0 && classPeople.stream().noneMatch(io.github.jcondedata.aliveworkplace.registry.ModAttachments.SOCIAL_CLASS::has),
						"the village starts unseeded");
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					pickedSlot = io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.ClassesPage.PAGE);
				}, 30),
				new Step("02_class_switch_on_tab", -2, 6, (level, player) -> {
					player.closeContainer();
					io.github.jcondedata.aliveworkplace.people.SocialClasses.ENABLED = true; // the update: classes on
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
					for (int round = 0; round < 5 && hall.classesVersion() == 0; round++) {
						io.github.jcondedata.aliveworkplace.people.ClassMigration.round(level, STATION, hall, today);
					}
					Showcase.check(hall.classesVersion() == 1, "every villager was seeded");
					var odoClass = io.github.jcondedata.aliveworkplace.registry.ModAttachments.SOCIAL_CLASS.get(classPeople.get(0));
					Showcase.check(odoClass != null && odoClass.getPath().equals("artisan"), "the couple in the cottage are Artisans: " + odoClass);
					long entries = hall.chronicle().stream().filter(e -> e.text().getString().contains("found their places")).count();
					Showcase.check(entries == 1, "one chronicle entry: " + entries);
					Showcase.check(io.github.jcondedata.aliveworkplace.people.ClassMigration.daysLeft(level, STATION, today) == 7, "settling in for 7 days");
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					pickedSlot = io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.ClassesPage.PAGE);
				}, 30),
				new Step("03_class_switch_on_page", -2, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.HallPages.slot(io.github.jcondedata.aliveworkplace.hall.ClassesPage.PAGE), player);
						pickedSlot = io.github.jcondedata.aliveworkplace.hall.ClassesPage.SETTLING_SLOT;
						Showcase.check(m.icon(pickedSlot).getHoverName().getString().equals("Settling in: 7 days left"),
							"the Classes page's clock: " + m.icon(pickedSlot).getHoverName().getString());
					}
					io.github.jcondedata.aliveworkplace.people.ClassNeeds.services = io.github.jcondedata.aliveworkplace.hall.Services.HOOK;
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		// Higher jobs need higher classes (ROADMAP 34.8): by a lectern in a village with a hall, Dara, a Peasant, is handed
		// the Scholar's paper and refused ("Dara is Peasant class; Scholar needs Burgher or better"); Bram, a Burgher, takes it.
		SCREENS.put("class_jobs", new Screen("a Peasant was refused the Scholar's paper (the job needs a Burgher), and a Burgher took it",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION.offset(-6, 0, -4), ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				level.setBlockAndUpdate(STATION, Blocks.LECTERN.defaultBlockState());
				io.github.jcondedata.aliveworkplace.people.SocialClasses.ENABLED = true;
				classPeople.clear();
				String[] names = {"Dara", "Bram"};
				String[] classes = {"peasant", "burgher"};
				for (int i = 0; i < names.length; i++) {
					Villager v = bornSceneVillager(level, new BlockPos(-1 + 2 * i, -60, 2), names[i], false);
					io.github.jcondedata.aliveworkplace.people.SocialClasses.seed(v, io.github.jcondedata.aliveworkplace.people.SocialClasses.get(
						io.github.jcondedata.aliveworkplace.AliveWorkplace.id(classes[i])));
					classPeople.add(v);
				}
			},
			List.of(new Step("01_class_jobs_refused", -1, 0, (level, player) -> {
					Villager dara = classPeople.get(0);
					player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(Items.PAPER));
					io.github.jcondedata.aliveworkplace.work.Stations.choose(player, dara, new net.minecraft.world.item.ItemStack(Items.PAPER));
					Showcase.check(dara.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE,
						"the Peasant was refused the Scholar's job: " + dara.getVillagerData().getProfession());
				}, 30),
				new Step("02_class_jobs_taken", -1, 0, (level, player) -> {
					Villager bram = classPeople.get(1);
					io.github.jcondedata.aliveworkplace.work.Stations.choose(player, bram, new net.minecraft.world.item.ItemStack(Items.PAPER));
					Showcase.check(bram.getVillagerData().getProfession() == ModVillagers.SCHOLAR,
						"the Burgher took the Scholar's job: " + bram.getVillagerData().getProfession());
				}, 40)),
			(level, player) -> classPeople.size() == 2 && classPeople.get(1).getVillagerData().getProfession() == ModVillagers.SCHOLAR));
		// The Noble's Ball (ROADMAP 34.7): a village with a Noble household holds a ball in place of every other festival.
		// The guests gather at the hall (no Manor yet), gold fireworks go up at dusk, the player is a Hero of the Village
		// for the night, and the hall's name tag shows the day's takings by class.
		SCREENS.put("noble_ball", new Screen("the village's Noble household turned the festival into a Noble's Ball: guests at the hall, gold fireworks at dusk, the player a Hero for the night, and the takings split by class",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				io.github.jcondedata.aliveworkplace.people.SocialClasses.ENABLED = true;
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setRank(io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.HAMLET);
				hall.setFestivalDay(-1);
				String[] names = {"Lady Wren", "Odo", "Pia", "Ann", "Bo", "Cy"};
				String[] classes = {"noble", "burgher", "artisan", "artisan", "peasant", "peasant"};
				for (int i = 0; i < names.length; i++) {
					Villager v = bornSceneVillager(level, new BlockPos(-3 + i, -60, 3), names[i], false);
					v.setVillagerData(v.getVillagerData().setProfession(i == 0 ? net.minecraft.world.entity.npc.VillagerProfession.NONE
						: i == 1 ? net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN : i < 4 ? ModVillagers.CARPENTER : net.minecraft.world.entity.npc.VillagerProfession.FARMER));
					if (i > 0) { // the five who work have their workstations: the census counts only those as workers, who pay the takings
						BlockPos station = new BlockPos(-3 + i, -60, 7);
						level.setBlockAndUpdate(station, (i == 1 ? net.minecraft.world.level.block.Blocks.LECTERN
							: i < 4 ? ModBlocks.CARPENTERS_BENCH : net.minecraft.world.level.block.Blocks.COMPOSTER).defaultBlockState());
						io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, v, station, i == 1 ? net.minecraft.world.entity.ai.village.poi.PoiTypes.LIBRARIAN
							: i < 4 ? ModVillagers.CARPENTERS_BENCH_POI : net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER, v.getVillagerData().getProfession());
					}
					io.github.jcondedata.aliveworkplace.people.SocialClasses.seed(v, io.github.jcondedata.aliveworkplace.people.SocialClasses.get(
						io.github.jcondedata.aliveworkplace.AliveWorkplace.id(classes[i])));
				}
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION); // counts the households by class
				// The evening before, so no round plans a festival before the pictures ask for one.
				level.setDayTime((level.getDayTime() / 24000) * 24000 + io.github.jcondedata.aliveworkplace.hall.Festivals.END + 500);
			},
			List.of(new Step("01_noble_ball_guests", -1, 0, (level, player) -> {
					var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
					long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level) + 1; // tomorrow (days count from 1: day d starts at (d - 1) * 24000)
					level.setDayTime((today - 1) * 24000 + io.github.jcondedata.aliveworkplace.hall.Festivals.START - 500);
					hall.setBallTurn(false);
					io.github.jcondedata.aliveworkplace.hall.Festivals.plan(level, STATION, hall, today, 0); // the first festival: a plain one
					boolean first = io.github.jcondedata.aliveworkplace.hall.NobleBalls.isBall(hall, today);
					hall.setFestivalDay(-1);
					io.github.jcondedata.aliveworkplace.hall.Festivals.plan(level, STATION, hall, today, 0); // the next: the ball
					Showcase.check(!first && io.github.jcondedata.aliveworkplace.hall.NobleBalls.isBall(hall, today), "every other festival is a Noble's Ball");
					level.setDayTime((today - 1) * 24000 + io.github.jcondedata.aliveworkplace.hall.Festivals.START + 200);
					io.github.jcondedata.aliveworkplace.hall.Festivals.round(level, STATION, hall, 6);
					var square = io.github.jcondedata.aliveworkplace.hall.Festivals.square(level, STATION);
					Showcase.check(square.equals(STATION), "the guests gather at the hall (no Manor): " + square);
					var hero = player.getEffect(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE);
					Showcase.check(hero != null && hero.getDuration() > io.github.jcondedata.aliveworkplace.hall.Festivals.END - io.github.jcondedata.aliveworkplace.hall.Festivals.START,
						"the player is a Hero of the Village for the night: " + (hero == null ? "none" : hero.getDuration() + " ticks"));
				}, 40),
				new Step("02_noble_ball_fireworks", -1, 0, (level, player) -> {
					long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
					level.setDayTime((today - 1) * 24000 + io.github.jcondedata.aliveworkplace.hall.Festivals.FIREWORKS + 100);
					for (int i = 0; i < 6; i++) {
						io.github.jcondedata.aliveworkplace.hall.Festivals.launch(level, STATION.offset(-4 + i * 2, 1, 4 + (i % 2) * 2),
							io.github.jcondedata.aliveworkplace.hall.NobleBalls.GOLD);
					}
					Villager lady = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(STATION).inflate(16),
						v -> v.getCustomName() != null && v.getCustomName().getString().equals("Lady Wren")).stream().findFirst().orElse(null);
					Showcase.check(lady != null && io.github.jcondedata.aliveworkplace.hall.Festivals.mood(level, lady) == io.github.jcondedata.aliveworkplace.hall.NobleBalls.MOOD,
						"the guests came to the ball: +15 mood");
				}, 50),
				new Step("03_noble_ball_takings", 0, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					var census = io.github.jcondedata.aliveworkplace.hall.VillageHalls.census(level, STATION);
					var lines = io.github.jcondedata.aliveworkplace.hall.Treasury.taxLines(level, STATION, census.workers(), census.jobless());
					Showcase.check(lines.size() == 5, "the name tag splits the takings by class: " + lines.size() + " lines");
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		SCREENS.put("hall_treasury", new Screen("the hall's treasury was collected, the village protected and its screen opened from a Village Ledger",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				guard(level, new BlockPos(4, -60, -3), new ItemStack(Items.IRON_SWORD));
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				hall.setOwner(player.getUUID(), player.getGameProfile().getName());
				hall.setTreasury(700); // 7 emeralds put by
				hall.setProtected(true);
			},
			// The name tag (slot 0) hovered: the treasury's 7 emeralds and the village protected by its owner.
			List.of(new Step("01_hall_treasury", 0, 6, (level, player) ->
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION), 30),
				new Step("02_hall_collected", 0, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(0, player); // a click on the name tag collects the treasury
					}
					Showcase.check(player.getInventory().countItem(Items.EMERALD) == 7, "the name tag paid out the treasury's 7 emeralds, got "
						+ player.getInventory().countItem(Items.EMERALD));
				}, 30),
				new Step("03_ledger_held", -1, 0, (level, player) -> {
					player.closeContainer();
					ItemStack ledger = new ItemStack(ModItems.VILLAGE_LEDGER);
					io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.bind(level, player, ledger, STATION);
					player.setItemInHand(InteractionHand.MAIN_HAND, ledger);
					// Walk off from the hall and turn away: the ledger opens its screen from anywhere nearby.
					player.teleportTo(level, 2.5, -59, 24.5, 0f, 10f);
					Showcase.check(ledger.get(io.github.jcondedata.aliveworkplace.registry.ModComponents.LEDGER) != null, "the ledger was bound to the hall");
				}, 30),
				new Step("04_ledger_opens", -1, 6, (level, player) ->
					ModItems.VILLAGE_LEDGER.use(level, player, InteractionHand.MAIN_HAND), 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU)); // the hall's own screen (30.4a)
		// The Defence page (32.3): the hall's guards icon opens it; a bandit camp stands out beyond the village.
		SCREENS.put("defence_page", new Screen("the hall's guards icon opened the Defence page: the bandit camp, its named chief, its strength and the last attacks",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				guard(level, new BlockPos(4, -60, -3), new ItemStack(Items.IRON_SWORD));
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				// The camp, out behind the hall; two attacks the village has already seen.
				var camp = io.github.jcondedata.aliveworkplace.guard.BanditCamps.found(level, STATION, new BlockPos(STATION.getX() + 26, -61, STATION.getZ() - 30));
				Showcase.check(camp != null, "a bandit camp stands by the village");
				long day = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
				var threats = io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level);
				threats.remember(STATION, new io.github.jcondedata.aliveworkplace.threat.ThreatData.Past(day - 4,
					io.github.jcondedata.aliveworkplace.threat.Threats.MONSTERS, 7, 7, false));
				threats.remember(STATION, new io.github.jcondedata.aliveworkplace.threat.ThreatData.Past(day - 1,
					io.github.jcondedata.aliveworkplace.threat.Threats.BANDITS, 5, 3, true));
			},
			// The captain's slot hovered: his name; then the strength.
			List.of(new Step("01_defence_page", io.github.jcondedata.aliveworkplace.hall.DefencePage.CAPTAIN, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.GUARDS, player);
						String captain = m.icon(io.github.jcondedata.aliveworkplace.hall.DefencePage.CAPTAIN).getHoverName().getString();
						Showcase.check(captain.startsWith("Chief ") && !captain.contains("aliveworkplace"), "the Defence page names the camp's chief: " + captain);
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.DefencePage.STRENGTH).getCount() == 6,
							"the Defence page counts the camp's strength of 6: " + m.icon(io.github.jcondedata.aliveworkplace.hall.DefencePage.STRENGTH).getCount());
					}
				}, 30),
				new Step("02_defence_strength", io.github.jcondedata.aliveworkplace.hall.DefencePage.STRENGTH, 6, null, 30),
				new Step("03_defence_attacks", io.github.jcondedata.aliveworkplace.hall.DefencePage.HISTORY[0], 6, null, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU
				&& m.icon(io.github.jcondedata.aliveworkplace.hall.DefencePage.LAIR).is(Items.CAMPFIRE)));
		// Raid dens (38.2, Cobblemon Raid Dens): a glowing den crystal by the hall and a beaten one up the valley; the
		// Defence page lists both, the village map marks them.
		SCREENS.put("raid_dens", new Screen("a raid den crystal glows by the village and another rests up the valley; the hall's Defense page lists both with their tier, type and state",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				guard(level, new BlockPos(4, -60, -3), new ItemStack(Items.IRON_SWORD));
				var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(io.github.jcondedata.aliveworkplace.guard.RaidDens.DEN);
				Showcase.check(block.isPresent(), "Cobblemon Raid Dens is loaded (-Pcobblemon=true)");
				BlockPos near = new BlockPos(-3, -60, -2);
				BlockPos far = new BlockPos(-20, -60, -60);
				// set down in loaded chunks after the hall, as in a real world: the game finds them by itself (B190)
				level.setBlockAndUpdate(near, denState(block.get(), "tier_three", "fire", true));
				level.setBlockAndUpdate(far, denState(block.get(), "tier_five", "water", false));
			},
			List.of(new Step("01_raid_den", -1, 0, (level, player) -> {
					denRolled(level, new BlockPos(-3, -60, -2), "tier_three", "fire");
					Showcase.check(io.github.jcondedata.aliveworkplace.guard.RaidDens.near(level, STATION).size() == 2,
						"the village knows both dens: " + io.github.jcondedata.aliveworkplace.guard.RaidDens.near(level, STATION));
				}, 30),
				new Step("02_raid_den_page", io.github.jcondedata.aliveworkplace.hall.DefencePage.DENS[0], 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.GUARDS, player);
						String den = m.icon(io.github.jcondedata.aliveworkplace.hall.DefencePage.DENS[0]).getHoverName().getString();
						Showcase.check(den.equals("Raid den, tier 3, Fire, active"), "the Defense page lists the den by the hall: " + den);
					}
				}, 30),
				new Step("03_raid_den_resting", io.github.jcondedata.aliveworkplace.hall.DefencePage.DENS[1], 6, null, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		// Raid helpers (38.1a): two of the village's trainers, hired on the Defence page, stand by the den as the player's
		// raid starts; each has cheered them on with a cheer item.
		SCREENS.put("den_helpers", new Screen("two of the village's trainers, hired on the hall's Defence page, stand by the glowing raid den as the player's raid starts; each cheered them on with one of the raid's cheer items",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setDayTime(3000);
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(io.github.jcondedata.aliveworkplace.guard.RaidDens.DEN);
				Showcase.check(block.isPresent(), "Cobblemon Raid Dens is loaded (-Pcobblemon=true)");
				// set down in a loaded chunk after the hall, as in a real world: the game finds it by itself (B190)
				level.setBlockAndUpdate(new BlockPos(-4, -60, -4), denState(block.get(), "tier_three", "fire", true));
				subject = picked(level, player, new BlockPos(1, -60, -5), ModBlocks.TRAINING_POST, Items.GOLD_BLOCK);
				worker(level, new BlockPos(5, -60, -5), ModBlocks.TRAINING_POST, ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
			},
			List.of(new Step("01_den_helpers_page", io.github.jcondedata.aliveworkplace.hall.DefencePage.HELPERS[0], 6, (level, player) -> {
					BlockPos den = new BlockPos(-4, -60, -4);
					denRolled(level, den, "tier_three", "fire");
					player.getInventory().add(new ItemStack(Items.EMERALD, 4 * io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.PRICE));
					io.github.jcondedata.aliveworkplace.work.Bank.EXTENSION.run(b -> b.add(player, 400L * io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.PRICE));
					String first = io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.hire(level, STATION, den, player).getString();
					String second = io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.hire(level, STATION, den, player).getString();
					Showcase.check(io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.hireOf(level.getServer(), player.getUUID())
						.map(h -> h.helpers.size() == 2).orElse(false), "two helpers hired: " + first + " / " + second);
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.GUARDS, player);
						String slot = m.icon(io.github.jcondedata.aliveworkplace.hall.DefencePage.HELPERS[0]).getHoverName().getString();
						Showcase.check(slot.equals("Hire a raid helper"), "the Defence page offers raid helpers by the den: " + slot);
					}
				}, 30),
				new Step("02_den_helpers_raid", -1, 0, (level, player) -> {
					player.closeContainer();
					BlockPos den = new BlockPos(-4, -60, -4);
					var hire = io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.hireOf(level.getServer(), player.getUUID()).orElse(null);
					Showcase.check(hire != null, "the hiring is there");
					if (hire != null) {
						// the raid joined by the den (Raid Dens' own statistic): the helpers cheer and take their places round it
						hire.lastNear = level.getGameTime();
						// the stat registry holds Raid Dens' own id instance
						var joined = net.minecraft.core.registries.BuiltInRegistries.CUSTOM_STAT.get(
							net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemonraiddens", io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.JOINED));
						Showcase.check(joined != null, "Raid Dens has its raids_joined statistic");
						if (joined != null) {
							player.awardStat(joined);
						}
						Showcase.check(hire.joined, "the helpers joined the raid");
						int i = 0;
						for (var helper : io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.helpers(level, hire)) {
							BlockPos at = den.offset(i++ == 0 ? 3 : 0, 0, i == 1 ? 0 : 3);
							helper.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5); // already there: the walk is the compat tests'
						}
					}
					int cheers = 0;
					for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
						var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(s).getItem());
						if (id.getNamespace().equals("cobblemonraiddens") && id.getPath().startsWith("cheer_")) {
							cheers += player.getInventory().getItem(s).getCount();
						}
					}
					Showcase.check(cheers == 2, "each helper cheered once: " + cheers + " cheer items");
				}, 60)),
			(level, player) -> true));
		// At peace (32.21): the owner set the village at peace on the Defence page; the bandit camp by it leaves at the next dawn.
		SCREENS.put("at_peace", new Screen("the hall's owner set the village at peace on the Defence page: no raids, camps or disasters come, and the bandit camp leaves at the next dawn",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				worker(level, new BlockPos(-4, -60, -3), ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI, ModVillagers.BUILDER);
				guard(level, new BlockPos(4, -60, -3), new ItemStack(Items.IRON_SWORD));
				io.github.jcondedata.aliveworkplace.hall.VillageNeeds.check(level, STATION);
				var camp = io.github.jcondedata.aliveworkplace.guard.BanditCamps.found(level, STATION, new BlockPos(STATION.getX() + 26, -61, STATION.getZ() - 30));
				Showcase.check(camp != null, "a bandit camp stands by the village");
				// The player owns the hall and sets the village at peace, as a click on the page does.
				String said = io.github.jcondedata.aliveworkplace.hall.AtPeace.toggle(level, STATION, player).getString();
				Showcase.check(level.getBlockEntity(STATION) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity e && e.atPeace(),
					"the village is at peace: " + said);
				Showcase.check(said.contains("is at peace") && said.contains("leaves at the next dawn"), "the chat says the camp leaves at dawn: " + said);
			},
			// The At peace slot hovered on the Defence page; then the hall's guards icon, which says so too.
			List.of(new Step("01_at_peace", io.github.jcondedata.aliveworkplace.hall.DefencePage.AT_PEACE, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.GUARDS, player);
						ItemStack peace = m.icon(io.github.jcondedata.aliveworkplace.hall.DefencePage.AT_PEACE);
						Showcase.check(peace.is(Items.WHITE_TULIP) && peace.getHoverName().getString().equals("At peace"),
							"the Defence page shows At peace: " + peace.getHoverName().getString());
					}
				}, 30),
				new Step("02_at_peace_guards", io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.GUARDS, 6, (level, player) -> {
					io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.open(player, STATION);
					if (player.containerMenu instanceof ChoiceMenu m) {
						var lore = m.icon(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.GUARDS).get(net.minecraft.core.component.DataComponents.LORE);
						Showcase.check(lore != null && lore.lines().stream().anyMatch(l -> l.getString().startsWith("At peace")),
							"the guards icon says the village is at peace");
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU));
		SCREENS.put("research_trees", new Screen("a Legend's research tree has its own tab on the research screen: levels done, one in progress, an exclusive pick taken",
			new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				// ROADMAP 29.11: the test tree as data, worked by a Legend of the scene's own who lives in the village.
				level.setBlockAndUpdate(STATION, ModBlocks.VILLAGE_HALL.defaultBlockState()
					.setValue(io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.FACING, Direction.SOUTH));
				net.minecraft.resources.ResourceLocation sageId = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_sage");
				io.github.jcondedata.aliveworkplace.legend.Legend sage = io.github.jcondedata.aliveworkplace.legend.Legends.read(sageId,
					com.google.gson.JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\", \"title\": \"entity.minecraft.villager.legend\","
						+ " \"lore\": \"entity.minecraft.villager.legend\"}").getAsJsonObject());
				io.github.jcondedata.aliveworkplace.legend.Legends.setForTest(Map.of(sageId, sage));
				String effect = "\"cost\": [{\"minecraft:paper\": 8, \"minecraft:book\": 2}], \"points\": 40";
				var tree = io.github.jcondedata.aliveworkplace.research.ResearchTrees.read(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("showcase_lore"),
					com.google.gson.JsonParser.parseString("{\"legend\": \"" + sageId + "\", \"icon\": \"minecraft:amethyst_shard\", \"name\": \"Test Lore\", \"topics\": ["
						+ "{\"id\": \"calm_minds\", \"icon\": \"minecraft:campfire\", \"name\": \"Calm Minds\", \"description\": \"Wellbeing 10% higher a level\", \"levels\": 2, " + effect
						+ ", \"effects\": [{\"type\": \"aliveworkplace:wellbeing\", \"percent\": 10}]},"
						+ "{\"id\": \"herb_lore\", \"icon\": \"minecraft:poppy\", \"name\": \"Herb Lore\", \"description\": \"Illness a fifth less likely and a day shorter a level\", \"levels\": 2, "
						+ effect + ", \"needs\": {\"calm_minds\": 1}, \"effects\": [{\"type\": \"aliveworkplace:illness\", \"percent\": -20, \"days\": -1}]},"
						+ "{\"id\": \"warding\", \"icon\": \"minecraft:obsidian\", \"name\": \"Warding\", \"description\": \"Night raids a fifth less likely\", " + effect
						+ ", \"effects\": [{\"type\": \"aliveworkplace:raid_chance\", \"factor\": 0.8}]},"
						+ "{\"id\": \"deep_memory\", \"icon\": \"minecraft:experience_bottle\", \"name\": \"Deep Memory\", \"description\": \"Every villager learns 15% faster\", " + effect
						+ ", \"effects\": [{\"type\": \"aliveworkplace:xp\", \"percent\": 15}]},"
						+ "{\"id\": \"fortune\", \"icon\": \"minecraft:rabbit_foot\", \"name\": \"Fortune\", \"description\": \"Two more luck on every loot roll\", " + effect
						+ ", \"effects\": [{\"type\": \"aliveworkplace:loot_luck\", \"luck\": 2}]},"
						+ "{\"id\": \"census\", \"icon\": \"minecraft:name_tag\", \"name\": \"Census\", \"description\": \"Opens when the village is large\", " + effect
						+ ", \"unlock\": {\"counter\": \"villagers\", \"at\": 30}},"
						+ "{\"id\": \"flame\", \"icon\": \"minecraft:soul_lantern\", \"name\": \"The Flame\", \"description\": \"Wellbeing never below 90%\", " + effect
						+ ", \"exclusive\": \"last_pick\", \"effects\": [{\"type\": \"aliveworkplace:wellbeing\", \"at_least\": 90}]},"
						+ "{\"id\": \"iron_pact\", \"icon\": \"minecraft:iron_block\", \"name\": \"The Iron Pact\", \"description\": \"Golems join the village\", " + effect
						+ ", \"exclusive\": \"last_pick\", \"effects\": [{\"type\": \"aliveworkplace:flag\", \"name\": \"iron_pact\"}]}]}"));
				io.github.jcondedata.aliveworkplace.research.ResearchTrees.set(List.of(tree));
				subject = EntityType.VILLAGER.spawn(level, STATION.south(2).east(2), MobSpawnType.COMMAND);
				subject.setNoAi(true);
				subject.setYRot(180);
				subject.setYHeadRot(180);
				subject.setCustomName(net.minecraft.network.chat.Component.literal("Old Wynn"));
				io.github.jcondedata.aliveworkplace.legend.Legends.make(level, subject, sage, "showcase");
				var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(STATION);
				var state = new io.github.jcondedata.aliveworkplace.research.Research.State(Map.of(tree.key() + "/calm_minds", 2, tree.key() + "/flame", 1,
					tree.key() + "/warding", 1), java.util.Optional.empty(), 0, false);
				state = io.github.jcondedata.aliveworkplace.research.ResearchTrees.choose(state, tree, tree.topic("herb_lore").orElseThrow());
				hall.setResearch(io.github.jcondedata.aliveworkplace.research.ResearchTrees.withProgress(state, tree, 40));
				level.setBlockAndUpdate(STATION.east(4).south(1), Blocks.LECTERN.defaultBlockState());
			},
			List.of(new Step("01_tree_tab", io.github.jcondedata.aliveworkplace.research.ResearchScreen.TOPIC_SLOTS[1], 6, (level, player) -> {
					// The hall placed in the setup is not yet in the POI index the tick she settles there, so her record has no hall
					// and no Legend tab shows; the village's own round (as for a Legend who settled before her hall) takes her in.
					io.github.jcondedata.aliveworkplace.legend.LegendSlots.round(level, STATION);
					io.github.jcondedata.aliveworkplace.research.ResearchScreen.openForLegend(player, subject);
					Showcase.check(player.containerMenu instanceof ChoiceMenu m
						&& m.icon(io.github.jcondedata.aliveworkplace.research.ResearchScreen.TAB_SLOTS[1]).is(Items.AMETHYST_SHARD)
						&& m.icon(io.github.jcondedata.aliveworkplace.research.ResearchScreen.TOPIC_SLOTS[1]).is(Items.POPPY), "the Legend's tree opened on its own tab");
				}, 40),
				new Step("02_tree_exclusive", io.github.jcondedata.aliveworkplace.research.ResearchScreen.TOPIC_SLOTS[7], 6, null, 40)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
		SCREENS.put("leader",new Screen("the Trainer Leader took the challenge and the battle started", new Vec3(-4.5, -57.5, 11.5),
			new Vec3(0.5, -59, 5.5),
			(level, player) -> {
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
				level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(),
					"gamerule doPokemonSpawning false");
				BlockPos podium = new BlockPos(0, -60, 6);
				subject = picked(level, player, podium, ModBlocks.TRAINING_POST, Items.GOLD_BLOCK);
				party(player, "snorlax level=100", "blissey level=100", "skarmory level=100", "tyranitar level=100", "dragonite level=100",
					"garchomp level=100");
			},
			List.of(new Step("01_leader", -1, 0, null, 40),
				new Step("02_leader_battle", -1, 0, (level, player) -> {
					player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
					player.getAbilities().flying = false;
					player.onUpdateAbilities();
					player.teleportTo(level, 0.5, -60, -0.5, 0, 10);
					io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(player, subject);
				}, 160)),
			(level, player) -> com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player) != null));
	}

	// --- Running them ----------------------------------------------------------------------------------------------

	private int tick;
	private int doneAt = -1;
	private int workShot;
	private Done done;
	private final AtomicBoolean isDone = new AtomicBoolean(false);
	private final AtomicBoolean ok = new AtomicBoolean(false);

	/**
	 * B231: writes one quad into Ponder's {@code ShadedBlockSbbBuilder} vertex by vertex, as Sodium does for a wrapped
	 * block model when shaders turn Flywheel off, on the render thread, and reads the mesh back; false if a vertex call
	 * throws ("only supports putBulkData!") or the mesh comes back empty.
	 */
	private static boolean contraptionBufferTakesVertices() {
		try {
			return Minecraft.getInstance().submit(() -> {
				try {
					Class<?> type = Class.forName("net.createmod.catnip.render.ShadedBlockSbbBuilder");
					Object builder = type.getMethod("create").invoke(null);
					type.getMethod("begin").invoke(builder);
					com.mojang.blaze3d.vertex.VertexConsumer consumer = (com.mojang.blaze3d.vertex.VertexConsumer) builder;
					for (int i = 0; i < 4; i++) {
						consumer.addVertex(i & 1, 0, i >> 1, 0xFFFFFFFF, 0, 0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0xF000F0, 0, 1, 0);
					}
					Object buffer = type.getMethod("end").invoke(builder);
					boolean empty = (boolean) buffer.getClass().getMethod("isEmpty").invoke(buffer);
					io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[showcase] B231: a quad written vertex by vertex went in ({})", empty ? "empty" : "4 vertices");
					return !empty;
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			}).get(10, java.util.concurrent.TimeUnit.SECONDS);
		} catch (Exception e) {
			io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.warn("[showcase] B231: a quad written vertex by vertex failed", e);
			return false;
		}
	}

	private static void command(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	/** Called every client tick while the scene runs. */
	void tick(Minecraft mc, MinecraftServer server, String scene) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				GameRules rules = level.getGameRules();
				rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
				rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(2500);
				level.setWeatherParameters(12000, 0, false, false);
			});
		}
		if (tick % 20 == 0) {
			// Slimes from the superflat world's slime chunks hop into the shots.
			server.execute(() -> {
				for (net.minecraft.world.entity.Entity e : server.overworld().getAllEntities()) {
					if (e instanceof net.minecraft.world.entity.monster.Slime) {
						e.discard();
					}
				}
			});
		}
		if (SCENES.containsKey(scene)) {
			job(mc, server, SCENES.get(scene));
		} else {
			screen(mc, server, SCREENS.get(scene));
		}
	}

	private void job(Minecraft mc, MinecraftServer server, Job job) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		// A still a scene's server side asked for at a moment it chose (35.6a: the builder up its scaffolding).
		for (String shot = SHOTS.poll(); shot != null; shot = SHOTS.poll()) {
			ScreenshotHarness.shot(mc, shot);
		}
		if (tick == 1) {
			mc.options.hideGui = true;
		}
		if (tick == 30) {
			server.execute(() -> {
				// Everything holds still until the "Start" still is taken, so it shows the set before any work.
				command(server, "tick freeze");
				ScreenshotHarness.hoverLookingAt(player, job.camera(), job.target());
				done = job.stage().stage(server.overworld(), player);
			});
		}
		if (openDuring != null) {
			// A screen to film while the job starts (only the Drop Box scene has one), as "04_dropbox_screen".
			if (tick == 100) {
				server.execute(() -> openDuring.accept(server.overworld(), player));
			}
			if (tick == 110) {
				mc.options.hideGui = false;
				ScreenshotHarness.pointAt(mc, 0, 3);
			}
			if (tick == 116) {
				ScreenshotHarness.shot(mc, "04_dropbox_screen");
				Showcase.check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen, "the Drop Box's screen opened");
			}
			if (tick == 120) {
				mc.setScreen(null);
				mc.options.hideGui = true;
				server.execute(() -> {
					ScreenshotHarness.hoverLookingAt(player, job.camera(), job.target());
					if (afterDuring != null) {
						afterDuring.accept(server.overworld(), player);
					}
				});
			}
		}
		if (tick == 90) {
			ScreenshotHarness.shot(mc, "01_start");
			server.execute(() -> command(server, "tick unfreeze"));
		}
		if (tick > 60 && tick % 10 == 0 && doneAt < 0) {
			if (isDone.get()) {
				doneAt = tick;
				ScreenshotHarness.shot(mc, String.format("work_%05d", tick)); // the moment it's done: at least one "at work" shot
			} else {
				server.execute(() -> isDone.set(done != null && done.test(server.overworld())));
				if (tick >= 130 && tick % 40 == 10) {
					ScreenshotHarness.shot(mc, String.format("work_%05d", tick));
				}
			}
		}
		if (doneAt > 0 && tick == doneAt + 40) {
			ScreenshotHarness.shot(mc, "03_done");
			Showcase.check(true, job.what());
			if (job.after() == null) {
				mc.stop();
			} else {
				mc.options.hideGui = false;
				server.execute(() -> job.after().open().accept(server.overworld(), player));
			}
		}
		if (doneAt > 0 && job.after() != null) {
			if (tick == doneAt + 80) {
				mc.getToasts().clear();
				if (job.after().slot() >= 0) {
					ScreenshotHarness.pointAt(mc, job.after().slot(), job.after().rows());
				}
			}
			if (tick == doneAt + 90) {
				ScreenshotHarness.shot(mc, job.after().shot());
				Showcase.check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
						|| mc.screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen // the printer's Gazette (34.11)
						|| job.after().rows() == 0 && mc.player != null && !mc.player.getMainHandItem().isEmpty(), // no screen: an item in hand (the scout's report, 32.12)
					"its screen opened (" + job.after().shot().substring(3).replace('_', ' ') + ")");
				mc.stop();
			}
		}
		if (tick >= job.maxTicks() && doneAt < 0) {
			ScreenshotHarness.shot(mc, "99_timeout");
			Showcase.check(false, job.what() + " (not within " + job.maxTicks() / 20 + " seconds" + (timeoutWhy == null ? "" : "; " + timeoutWhy) + ")");
			mc.stop();
		}
	}

	private void screen(Minecraft mc, MinecraftServer server, Screen screen) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		if (tick == 1) {
			mc.options.hideGui = false;
		}
		if (tick == 30) {
			server.execute(() -> {
				ScreenshotHarness.hoverLookingAt(player, screen.camera(), screen.target());
				screen.stage().accept(server.overworld(), player);
			});
		}
		int at = 100; // well after the terrain has loaded
		for (Step step : screen.steps()) {
			if (tick == at && step.before() != null) {
				server.execute(() -> step.before().accept(server.overworld(), player));
			}
			if (tick == at + step.hold() - 10) {
				mc.getToasts().clear();
				int slot = step.slot() == -2 ? pickedSlot : step.slot();
				if (slot >= 0) {
					ScreenshotHarness.pointAt(mc, slot, step.rows());
				}
			}
			if (tick == at + step.hold()) {
				ScreenshotHarness.shot(mc, step.shot());
			}
			at += step.hold() + 5;
		}
		if (tick == at) {
			server.execute(() -> ok.set(screen.ok().test(server.overworld(), player)));
		}
		if (tick == at + 5) {
			Showcase.check(ok.get(), screen.what());
			mc.stop();
		}
	}
}
