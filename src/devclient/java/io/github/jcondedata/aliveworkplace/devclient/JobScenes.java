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
	record Step(String shot, int slot, int rows, BiConsumer<ServerLevel, ServerPlayer> before, int hold) {
	}

	record Screen(String what, Vec3 camera, Vec3 target, BiConsumer<ServerLevel, ServerPlayer> stage, List<Step> steps,
				  BiPredicate<ServerLevel, ServerPlayer> ok) {
	}

	/** Villagers the scenes hand from staging to a later step (the scholar, the screen scenes' villager). */
	private static volatile Villager scholar;
	private static volatile Villager subject;
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
		io.github.jcondedata.aliveworkplace.work.Stations.choose(player, v, new ItemStack(item));
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

	static Item cobblemonItem(String id) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", id));
	}

	static void party(ServerPlayer player, String... specs) {
		var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String spec : specs) {
			party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(spec, " ", "=").create());
		}
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
		SCENES.put("bard", job("the bard played a record at the Music Stand", 1200, (level, player) -> {
			Villager b = picked(level, player, STATION, Blocks.JUKEBOX, Items.MUSIC_DISC_CAT);
			chest(level, chestPos(), new ItemStack(Items.MUSIC_DISC_CAT));
			return l -> io.github.jcondedata.aliveworkplace.bard.BardWork.playing(b).isPresent();
		}));
		SCENES.put("fossil", job("the fossil scientist revived a Kabuto from the Dome Fossil", 2400, (level, player) -> {
			Villager sci = picked(level, player, STATION, ModBlocks.TRAINING_POST, cobblemonItem("dome_fossil"));
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

	static {
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
		SCREENS.put("style_menu", new Screen("the style picker opened and dark oak was chosen", new Vec3(2.5, -58.4, 4.5), TARGET,
			(level, player) -> {
				player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.STONE_HOUSE.id(), StarterBlueprints.STONE_HOUSE.size()));
				io.github.jcondedata.aliveworkplace.blueprint.StylePicker.open(player, InteractionHand.MAIN_HAND);
			},
			List.of(step("01_style_screen", io.github.jcondedata.aliveworkplace.blueprint.StylePicker.slot(0), 6),
				new Step("02_style_chosen", io.github.jcondedata.aliveworkplace.blueprint.StylePicker.slot(3), 6, (level, player) -> {
					if (player.containerMenu instanceof ChoiceMenu m) {
						m.press(io.github.jcondedata.aliveworkplace.blueprint.StylePicker.slot(3), player);
					}
				}, 25)),
			(level, player) -> BlueprintItem.data(player.getMainHandItem()).map(d -> d.structure().equals(
				io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE.id(), "dark_oak"))).orElse(false)));
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
				player.openMenu(counter);
			},
			List.of(step("01_counter_screen", 0, 2),
				step("02_counter_price", io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity.COLUMNS + 1, 2),
				new Step("03_price_tag", 22, 6, (level, player) -> {
					Showcase.check(player.containerMenu instanceof ChestMenu m && m.getRowCount() == 2, "the Shop Counter's price list opened");
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
		SCREENS.put("leader", new Screen("the Trainer Leader took the challenge and the battle started", new Vec3(-4.5, -57.5, 11.5),
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
