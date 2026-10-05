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

	/** One step of a screen scene: optionally act on the server, then point at {@code slot} (-1: nowhere) and shoot. */
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

	record Step(String shot, int slot, int rows, BiConsumer<ServerLevel, ServerPlayer> before, int hold) {
	}

	record Screen(String what, Vec3 camera, Vec3 target, BiConsumer<ServerLevel, ServerPlayer> stage, List<Step> steps,
				  BiPredicate<ServerLevel, ServerPlayer> ok) {
	}

	/** Villagers the scenes hand from staging to a later step (the scholar, the screen scenes' villager). */
	private static volatile Villager scholar;
	private static volatile Villager subject;
	/** curfew: the villagers who go to bed at dusk. */
	private static final List<Villager> curfewSleepers = new java.util.concurrent.CopyOnWriteArrayList<>();
	/** conscription: the villagers called up, and the raiders they beat back. */
	private static final List<Villager> conscripts = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static final List<net.minecraft.world.entity.monster.Zombie> conscriptionRaiders = new java.util.concurrent.CopyOnWriteArrayList<>();
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
		SCENES.put("sifter", job("the sifter sifted gravel into loot", 1800, (level, player) -> {
			Villager v = picked(level, player, STATION, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3), Items.GRAVEL);
			chest(level, chestPos(), new ItemStack(Items.GRAVEL, 40));
			return l -> n(ModAttachments.BLOCKS_SIFTED, v) >= 3;
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
			io.github.jcondedata.aliveworkplace.city.Stewards.appoint(player, v, cityPlan);
			return l -> io.github.jcondedata.aliveworkplace.city.StewardWork.roundDone(l, v)
				&& v.distanceToSqr(STATION.getCenter()) < 16 && !io.github.jcondedata.aliveworkplace.city.StewardWork.holdsPlan(v);
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
			return l -> sheep.stream().allMatch(net.minecraft.world.entity.animal.Sheep::isSheared) && n(ModAttachments.ANIMALS_SHEARED, s) >= 2;
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
		SCENES.put("habitat_keeper", job("the habitat keeper set out a snack, slathered the log and spotted a shiny Eevee", 2400, (level, player) -> {
			// ROADMAP 28.10: Cobblemon's Pasture Block picked with a honey bottle; Poké Snacks and a honey bottle in the
			// chest; a lure spot marked behind the pasture, a Saccharine log beside it, and a shiny wild Eevee nearby. She
			// sets the snack out, slathers the log and tells the village of the Eevee in chat.
			net.minecraft.world.level.block.Block pasture = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
				io.github.jcondedata.aliveworkplace.registry.ModVillagers.PASTURE_BLOCK);
			BlockState bottom = pastureHalf(ScreenshotHarness.standing(pasture.defaultBlockState()), "bottom");
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
			com.cobblemon.mod.common.entity.pokemon.PokemonEntity eevee = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion
				.parse("eevee shiny=yes level=12", " ", "=").createEntity(level);
			eevee.setPos(STATION.getX() + 4.5, STATION.getY(), STATION.getZ() - 4.5);
			eevee.setNoAi(true);
			level.addFreshEntity(eevee);
			return l -> io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.snacks() != null
				&& io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.snacks().isSnackBlock(l.getBlockState(spot))
				&& io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.isSlathered(l.getBlockState(log))
				&& !io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.sightings(keeper).isEmpty();
		}));
		SCENES.put("berry_breeder", new Job("the berry breeder bred a Lum Berry from Oran and Cheri", 2400, CAMERA, TARGET, (level, player) -> {
			// ROADMAP 28.9: a composter picked with an Oran Berry; Oran, Cheri and Surprise Mulch in the chest; two rows of
			// farmland east of the composter (x 2 and 3), so the parents go in side by side; a hall to note the find.
			Villager breeder = picked(level, player, STATION, Blocks.COMPOSTER, cobblemonItem("oran_berry"));
			Container c = chest(level, chestPos(), new ItemStack(cobblemonItem("oran_berry"), 6), new ItemStack(cobblemonItem("cheri_berry"), 6),
				new ItemStack(cobblemonItem("surprise_mulch"), 6));
			place(level, new BlockPos(-4, -60, -3), ModBlocks.VILLAGE_HALL);
			for (int x = 2; x <= 3; x++) {
				for (int z = -2; z <= 0; z++) {
					level.setBlockAndUpdate(new BlockPos(x, -61, z), Blocks.FARMLAND.defaultBlockState());
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
				for (int x = 2; x <= 3; x++) {
					for (int z = -2; z <= 0; z++) {
						planted += data.plantOf(l.getBlockState(new BlockPos(x, -60, z))) != null ? 1 : 0;
					}
				}
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
			return l -> drop.isEmpty() && n(ModAttachments.ITEMS_CARRIED, porter) >= 1 && store.countItem(Items.BREAD) >= 1;
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
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
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
					io.github.jcondedata.aliveworkplace.legend.LegendGuests.tend(level, STATION);
					var data = io.github.jcondedata.aliveworkplace.registry.ModAttachments.LEGEND.get(subject);
					Showcase.check(data != null && data.settled(), "she settled once the home was ready");
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
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
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
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
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
					// A cabin: spruce walls round a bed, a slab roof, a lantern by the open front.
					for (int y = -60; y <= -59; y++) {
						for (int z = -7; z <= -4; z++) {
							level.setBlockAndUpdate(new BlockPos(x - 1, y, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
							level.setBlockAndUpdate(new BlockPos(x + 1, y, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
						}
						level.setBlockAndUpdate(new BlockPos(x, y, -7), Blocks.SPRUCE_PLANKS.defaultBlockState());
					}
					for (int dx = -1; dx <= 1; dx++) {
						for (int z = -7; z <= -4; z++) {
							level.setBlockAndUpdate(new BlockPos(x + dx, -58, z), Blocks.SPRUCE_SLAB.defaultBlockState());
						}
					}
					level.setBlockAndUpdate(new BlockPos(x + 1, -58, -3), Blocks.LANTERN.defaultBlockState());
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
					level.getPoiManager().take(t -> t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), (t, p) -> p.equals(head), head, 4);
					v.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME, net.minecraft.core.GlobalPos.of(level.dimension(), head));
					curfewSleepers.add(v);
				}
				subject = worker(level, new BlockPos(6, -60, 2), Blocks.GRINDSTONE, net.minecraft.world.entity.ai.village.poi.PoiTypes.WEAPONSMITH, ModVillagers.GUARD);
				subject.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				var curfew = io.github.jcondedata.aliveworkplace.hall.Edicts.find("curfew").orElseThrow();
				var told = io.github.jcondedata.aliveworkplace.hall.Edicts.proclaim(level, STATION.offset(-7, 0, 0), player, curfew);
				Showcase.check(told.done(), "Curfew was proclaimed: " + told.message().getString());
				io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget();
			},
			List.of(new Step("01_curfew_evening", -1, 0, (level, player) -> {
					player.closeContainer();
					level.setDayTime((level.getDayTime() / 24000) * 24000 + 11800);
				}, 30),
				new Step("02_curfew_dusk", -1, 0, (level, player) ->
					level.setDayTime((level.getDayTime() / 24000) * 24000 + io.github.jcondedata.aliveworkplace.hall.Curfew.DUSK + 20), 200),
				new Step("03_curfew_book", io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0], 6, (level, player) -> {
					long asleep = curfewSleepers.stream().filter(io.github.jcondedata.aliveworkplace.hall.Curfew::asleepInBed).count();
					Showcase.check(asleep == 3, "every villager went to bed at dusk: " + asleep + " of 3 asleep");
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
					// The raiders come over the field: zombies of the village's raid.
					for (int i = 0; i < 3; i++) {
						var zombie = EntityType.ZOMBIE.spawn(level, new BlockPos(-4 + 4 * i, -60, -8), MobSpawnType.COMMAND);
						zombie.addTag(io.github.jcondedata.aliveworkplace.guard.VillageRaids.TAG);
						zombie.setPersistenceRequired();
						zombie.setHealth(12f);
						zombie.setTarget(conscripts.get(i));
						conscriptionRaiders.add(zombie);
					}
					io.github.jcondedata.aliveworkplace.guard.VillageRaids.track(level, STATION.offset(-7, 0, 0), 3);
					io.github.jcondedata.aliveworkplace.hall.Conscription.forget();
				}, 40),
				new Step("02_conscription_fight", -1, 0, (level, player) -> {
					long armed = conscripts.stream().filter(v -> io.github.jcondedata.aliveworkplace.hall.Conscription.isMilitiaSword(v.getMainHandItem())).count();
					Showcase.check(armed == 3, "the three took up the militia's swords: " + armed + " of 3");
				}, 160),
				new Step("03_conscription_book", io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0], 6, (level, player) -> {
					long struck = conscriptionRaiders.stream().filter(z -> z.getLastHurtByMob() instanceof Villager v
						&& !io.github.jcondedata.aliveworkplace.guard.Guards.isGuard(v)).count();
					Showcase.check(struck >= 2, "the conscripts struck the raiders: " + struck + " of 3");
					Showcase.check(conscripts.stream().anyMatch(io.github.jcondedata.aliveworkplace.hall.Conscription::workStopped), "no work in the raid");
					player.teleportTo(-5.5, -60, 1.5);
					io.github.jcondedata.aliveworkplace.hall.EdictBook.open(player, STATION.offset(-7, 0, 0));
					if (player.containerMenu instanceof ChoiceMenu m) {
						Showcase.check(m.icon(io.github.jcondedata.aliveworkplace.hall.EdictBook.SLOTS[0]).is(Items.STONE_SWORD), "Conscription sits in the first slot");
					}
				}, 30)),
			(level, player) -> player.containerMenu instanceof ChoiceMenu));
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
				ScreenshotHarness.pointAt(mc, job.after().slot(), job.after().rows());
			}
			if (tick == doneAt + 90) {
				ScreenshotHarness.shot(mc, job.after().shot());
				Showcase.check(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>,
					"its screen opened (" + job.after().shot().substring(3).replace('_', ' ') + ")");
				mc.stop();
			}
		}
		if (tick >= job.maxTicks() && doneAt < 0) {
			ScreenshotHarness.shot(mc, "99_timeout");
			Showcase.check(false, job.what() + " (not within " + job.maxTicks() / 20 + " seconds)");
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
