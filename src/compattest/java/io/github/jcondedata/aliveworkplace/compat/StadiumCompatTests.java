package io.github.jcondedata.aliveworkplace.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Stadium's design (ROADMAP 35.23) with Cobblemon installed: the design checks every Wonder's design meets
 * (WonderBlueprintGameTests, which runs without Cobblemon and so can't load the Stadium's file or its Healing Machine)
 * — its file loads, every part loads and is at most 48 a side, no stage clears an earlier stage's block, the block count
 * is within 10% of the roadmap's figure, no retired job block, no calcite, nothing a player can't get — plus its
 * requirements and goods as the roadmap lists them, its seats built in the village's banner colours, and (35.23a) the
 * insides a player walks through.
 */
public class StadiumCompatTests implements FabricGameTest {
	private static final ResourceLocation STADIUM = AliveWorkplace.id("stadium");
	/** The design's count (35.23 drew it at about 7,300; 35.23a made it bigger, with its insides: about 9,550). */
	private static final int BLOCKS = 9_550;
	private static final int PART_LIMIT = 48;
	private static final Set<Block> UNOBTAINABLE = Set.of(Blocks.BEDROCK, Blocks.BARRIER, Blocks.COMMAND_BLOCK, Blocks.STRUCTURE_BLOCK,
		Blocks.STRUCTURE_VOID, Blocks.JIGSAW, Blocks.SPAWNER, Blocks.REINFORCED_DEEPSLATE, Blocks.BUDDING_AMETHYST, Blocks.LIGHT,
		Blocks.INFESTED_STONE_BRICKS, Blocks.INFESTED_MOSSY_STONE_BRICKS, Blocks.INFESTED_CRACKED_STONE_BRICKS,
		Blocks.INFESTED_CHISELED_STONE_BRICKS, Blocks.PLAYER_HEAD, Blocks.PLAYER_WALL_HEAD, Blocks.DRAGON_EGG);

	/** The job blocks the stations plan retired (StationsSpecGameTests.gone, which this source set can't see). */
	private static List<Block> retired() {
		return List.of(ModBlocks.BUILDERS_BENCH, ModBlocks.MINERS_BENCH, ModBlocks.CHOPPING_BLOCK, ModBlocks.FRUIT_BASKET, ModBlocks.APIARY,
			ModBlocks.FLOWER_STAND, ModBlocks.SCHOLARS_DESK, ModBlocks.SIEVE, ModBlocks.TINKERS_BENCH, ModBlocks.COMPOST_BIN, ModBlocks.NETHER_BRAZIER,
			ModBlocks.UNDERTAKERS_TABLE, ModBlocks.INN_COUNTER, ModBlocks.TEACHERS_DESK, ModBlocks.FEED_TROUGH, ModBlocks.CARPENTERS_BENCH,
			ModBlocks.KITCHEN_STOVE, ModBlocks.POSTAL_DESK, ModBlocks.GUARD_POST, ModBlocks.NURSE_STATION, ModBlocks.MUSIC_STAND,
			ModBlocks.LEADERS_PODIUM, ModBlocks.TUTORS_DESK, ModBlocks.BALL_WORKBENCH, ModBlocks.TRADE_BOARD, ModBlocks.FOSSIL_LAB);
	}

	record Part(ResourceLocation id, BlockPos offset, Blueprint blueprint) {
	}

	private static JsonObject file(MinecraftServer server) {
		ResourceLocation path = AliveWorkplace.id("wonders/stadium.json");
		var resource = server.getResourceManager().getResource(path).orElseThrow(() -> new GameTestAssertException("no data file " + path));
		try (Reader reader = resource.openAsReader()) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException(path + " doesn't read: " + e.getMessage());
		}
	}

	private static List<List<Part>> stages(MinecraftServer server) {
		List<List<Part>> out = new ArrayList<>();
		for (JsonElement stage : file(server).getAsJsonArray("stages")) {
			List<Part> parts = new ArrayList<>();
			for (JsonElement p : stage.getAsJsonObject().getAsJsonArray("parts")) {
				ResourceLocation part = ResourceLocation.parse(p.getAsJsonObject().get("part").getAsString());
				JsonArray o = p.getAsJsonObject().getAsJsonArray("offset");
				Blueprint blueprint = BlueprintLibrary.get(server, part).orElseThrow(() -> new GameTestAssertException("part " + part + " doesn't load"));
				parts.add(new Part(part, new BlockPos(o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt()), blueprint));
			}
			out.add(parts);
		}
		return out;
	}

	/** The file loads with Cobblemon: Town, the Pokémon Professor or 25 pastured Pokémon of 10 types, its found goods and workshop luxuries (35.7a), its powers. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stadiumFileLoads(GameTestHelper helper) {
		Wonder stadium = Wonders.loaded().get(STADIUM);
		helper.assertTrue(stadium != null, "the Stadium isn't loaded: " + Wonders.loaded().keySet() + ", skipped: " + Wonders.problems());
		helper.assertTrue(stadium.villageColours(), "its seats don't take the village's colours");
		helper.assertTrue(stadium.stages().size() == 4 && stadium.stages().size() == stages(helper.getLevel().getServer()).size(),
			"stages: " + stadium.stages().size());
		JsonObject json = file(helper.getLevel().getServer());
		String requirements = json.getAsJsonArray("requirements").toString();
		helper.assertTrue(requirements.contains("\"rank\":\"town\"") && requirements.contains("aliveworkplace:pokemon_professor")
			&& requirements.contains("\"pastured_pokemon\",\"count\":25,\"types\":10"), "requirements: " + requirements);
		List<String> goods = new ArrayList<>();
		for (Wonder.Stage stage : stadium.stages()) {
			List<String> line = new ArrayList<>();
			for (Wonder.Good g : stage.goods()) {
				line.add(g.count() + " " + g.item().map(ResourceLocation::getPath).orElseGet(() -> "#" + g.tag().orElseThrow().getPath())
					+ " " + g.job().getPath());
			}
			goods.add(String.join(", ", line));
		}
		List<String> expected = List.of(
			"1 diamond_horse_armor cartographer, 1 coast_armor_trim_smithing_template cartographer, 2 great_ball ball_smith",
			"1 trident cartographer, 1 music_disc_pigstep cartographer, 1 netherite_upgrade_smithing_template cartographer, 2 gold_circlet jeweller",
			"1 totem_of_undying cartographer, 1 ominous_trial_key cartographer, 1 flow_armor_trim_smithing_template cartographer, 2 ultra_ball ball_smith",
			"1 nether_star cartographer, 1 heavy_core cartographer, 1 enchanted_golden_apple cartographer, 2 noble_robes tailor");
		helper.assertTrue(goods.equals(expected), "goods: " + goods);
		helper.assertTrue(stadium.powers().stream().map(Wonder.Power::type).toList().equals(List.of("stadium_days", "tournament_host")),
			"powers: " + stadium.powers());
		helper.succeed();
	}

	/** Every part loads, holds blocks and is at most 48 a side; its names have their lines; none is in the Blueprint Table. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stadiumPartsLoad(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> wrong = new ArrayList<>();
		List<List<Part>> stages = stages(server);
		for (int n = 0; n < stages.size(); n++) {
			for (Part p : stages.get(n)) {
				var size = p.blueprint().size();
				if (size.getX() > PART_LIMIT || size.getY() > PART_LIMIT || size.getZ() > PART_LIMIT) {
					wrong.add(p.id() + " is " + size);
				}
				if (p.blueprint().solidBlockCount() == 0) {
					wrong.add(p.id() + " is empty");
				}
				if (!p.id().getPath().startsWith("wonder/stadium/")) {
					wrong.add(p.id() + " isn't in wonder/stadium");
				}
			}
		}
		JsonObject file = file(server);
		List<String> keys = new ArrayList<>(List.of(file.get("name").getAsString(), file.get("description").getAsString()));
		file.getAsJsonArray("stages").forEach(s -> keys.add(s.getAsJsonObject().get("name").getAsString()));
		keys.stream().filter(k -> !Language.getInstance().has(k)).forEach(k -> wrong.add("no text for " + k));
		BlueprintLibrary.list(server, false).stream().filter(id -> id.getPath().startsWith("wonder/")).forEach(id -> wrong.add(id + " is in the table"));
		helper.assertTrue(wrong.isEmpty(), "stadium: " + wrong);
		helper.succeed();
	}

	/** No stage clears an earlier stage's block; the count is within 10% of the figure. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stadiumStagesOnlyAddAndCount(GameTestHelper helper) {
		List<List<Part>> stages = stages(helper.getLevel().getServer());
		Map<BlockPos, Integer> built = new HashMap<>();
		List<String> wrong = new ArrayList<>();
		int blocks = 0;
		for (int n = 0; n < stages.size(); n++) {
			Map<BlockPos, Integer> mine = new HashMap<>();
			for (Part p : stages.get(n)) {
				blocks += p.blueprint().solidBlockCount();
				for (Blueprint.Entry e : p.blueprint().blocks()) {
					BlockPos at = p.offset().offset(e.pos());
					if (e.state().isAir()) {
						if (built.containsKey(at) && wrong.size() < 10) {
							wrong.add(p.id() + " clears " + at.toShortString());
						}
					} else {
						mine.put(at, n + 1);
					}
				}
			}
			built.putAll(mine);
		}
		helper.assertTrue(wrong.isEmpty(), "stadium: " + wrong);
		helper.assertTrue(Math.abs(blocks - BLOCKS) <= BLOCKS / 10, "the Stadium has " + blocks + " blocks, not within 10% of " + BLOCKS);
		helper.succeed();
	}

	/** No retired job block, no calcite, nothing a player can't get; and it has its Healing Machine and Training Posts. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stadiumMaterials(GameTestHelper helper) {
		Set<String> wrong = new TreeSet<>();
		Map<String, Integer> named = new HashMap<>();
		for (List<Part> stage : stages(helper.getLevel().getServer())) {
			for (Part p : stage) {
				for (Blueprint.Entry e : p.blueprint().blocks()) {
					BlockState state = e.state();
					if (state.isAir()) {
						continue;
					}
					Block block = state.getBlock();
					String name = BuiltInRegistries.BLOCK.getKey(block).toString();
					named.merge(name, 1, Integer::sum);
					if (retired().contains(block)) {
						wrong.add(name + " (a retired job block)");
					} else if (block == Blocks.CALCITE) {
						wrong.add(name);
					} else if (UNOBTAINABLE.contains(block) || block.asItem() == Items.AIR) {
						wrong.add(name + " (no player can get it)");
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the Stadium uses " + wrong);
		helper.assertTrue(named.getOrDefault("cobblemon:healing_machine", 0) == 1 && named.getOrDefault("minecraft:brewing_stand", 0) == 1,
			"the nurse's booth: " + named.get("cobblemon:healing_machine") + " healing machines, " + named.get("minecraft:brewing_stand") + " brewing stands");
		helper.assertTrue(named.getOrDefault("aliveworkplace:training_post", 0) == 2, "training posts: " + named.get("aliveworkplace:training_post"));
		helper.assertTrue(named.getOrDefault("minecraft:daylight_detector", 0) == 4 && named.getOrDefault("minecraft:redstone_lamp", 0) == 32,
			"floodlights: " + named.get("minecraft:daylight_detector") + " detectors, " + named.get("minecraft:redstone_lamp") + " lamps");
		helper.succeed();
	}

	/** The whole Stadium as its stages' parts build it: each block (not air) by its place in the Wonder. */
	private static Map<BlockPos, BlockState> built(MinecraftServer server) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (List<Part> stage : stages(server)) {
			for (Part p : stage) {
				for (Blueprint.Entry e : p.blueprint().blocks()) {
					if (!e.state().isAir()) {
						out.put(p.offset().offset(e.pos()), e.state());
					}
				}
			}
		}
		return out;
	}

	/** What a player walks through: air, carpets, doors, banners, flowers, redstone wire. */
	private static boolean passable(BlockState state) {
		return state.isAir() || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.DOORS) || state.is(BlockTags.BANNERS)
			|| state.is(BlockTags.SMALL_FLOWERS) || state.is(Blocks.REDSTONE_WIRE);
	}

	/**
	 * 35.23a: the insides, like a real football ground. A concourse three high runs round under the stands (cells with a
	 * floor, three of air and a roof), an upper corridor runs over it, the players' tunnel runs straight and roofed from
	 * the back wall to the pitch, and walking a block up or down at a time from the paving outside the south wall a
	 * player reaches the pitch, both changing rooms, the concourse, the press room and the professor's box, a seat on
	 * the second row (through a vomitory), the top rows (up the aisles) and the ends' top rows (up their stairwells).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stadiumInsideIsWalkable(GameTestHelper helper) {
		Map<BlockPos, BlockState> built = built(helper.getLevel().getServer());
		java.util.function.Function<BlockPos, BlockState> at = p -> built.getOrDefault(p, Blocks.AIR.defaultBlockState());
		List<String> wrong = new ArrayList<>();
		int covered = 0;
		int upper = 0;
		for (int x = 0; x < 55; x++) {
			for (int z = 0; z < 45; z++) {
				int fx = x;
				int fz = z;
				java.util.function.IntPredicate air = y -> at.apply(new BlockPos(fx, y, fz)).isAir();
				if (!air.test(0) && air.test(1) && air.test(2) && air.test(3) && !air.test(4)) {
					covered++;
				}
				if (!air.test(4) && air.test(5) && air.test(6) && !air.test(8)) {
					upper++;
				}
			}
		}
		if (covered < 400) {
			wrong.add("only " + covered + " covered cells three high (the concourse, the rooms and the tunnels)");
		}
		if (upper < 80) {
			wrong.add("only " + upper + " cells of upper corridor");
		}
		for (int z = 34; z <= 42; z++) {
			BlockPos floor = new BlockPos(27, 0, z);
			if (at.apply(floor).isAir() || !at.apply(floor.above()).isAir() || !at.apply(floor.above(2)).isAir()
				|| (z >= 35 && at.apply(floor.above(4)).isAir())) {
				wrong.add("the players' tunnel at z " + z);
			}
		}
		java.util.function.Predicate<BlockPos> stand = p -> passable(at.apply(p)) && passable(at.apply(p.above()))
			&& !passable(at.apply(p.below())) && !at.apply(p.below()).is(Blocks.LANTERN);
		BlockPos start = new BlockPos(27, 1, 44);
		Set<BlockPos> seen = new java.util.HashSet<>(List.of(start));
		java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>(List.of(start));
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos q = p.relative(d).above(dy);
					if (q.getX() < -1 || q.getX() > 55 || q.getZ() < -1 || q.getZ() > 45 || q.getY() < 1 || q.getY() > 23) {
						continue;
					}
					if (dy == 1 && !passable(at.apply(p.above(2)))) {
						continue; // no head room to step up
					}
					if (!seen.contains(q) && stand.test(q)) {
						seen.add(q);
						todo.add(q);
					}
				}
			}
		}
		Map<String, BlockPos> places = new java.util.LinkedHashMap<>();
		places.put("the pitch", new BlockPos(27, 1, 22));
		places.put("the home changing room", new BlockPos(22, 1, 39));
		places.put("the away changing room", new BlockPos(32, 1, 39));
		places.put("the concourse by the gate", new BlockPos(21, 1, 4));
		places.put("the press room", new BlockPos(27, 5, 40));
		places.put("the professor's box", new BlockPos(26, 5, 36));
		places.put("a second-row seat (north)", new BlockPos(18, 6, 7));
		places.put("a top-row seat (north)", new BlockPos(20, 14, 3));
		places.put("a top-row seat (south)", new BlockPos(20, 14, 41));
		places.put("the west end's top row", new BlockPos(5, 10, 22));
		places.put("the east end's top row", new BlockPos(49, 10, 22));
		places.forEach((name, p) -> {
			if (!seen.contains(p)) {
				wrong.add("can't walk to " + name + " " + p.toShortString());
			}
		});
		helper.assertTrue(wrong.isEmpty(), "inside the Stadium: " + wrong + " (" + seen.size() + " places reached)");
		helper.succeed();
	}

	/**
	 * A village with a Village Banner (blue with a red stripe) gets its Stadium's stages as blueprints in blue and red:
	 * the red seats blue, the white ones red, the field's white lines still white; a village without one, red and white.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"stadium_colours"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "stadium_colours")
	public void stadiumSeatsTakeTheVillagesColours(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hallRel = new BlockPos(0, 1, 0);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallRel);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		var patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		entity.setColours(new VillageBanners.Colours(DyeColor.BLUE,
			new BannerPatternLayers.Builder().add(patterns.getOrThrow(BannerPatterns.STRIPE_MIDDLE), DyeColor.RED).build()));
		WonderManager manager = WonderManager.get(level);
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<UUID> wonders = new ArrayList<>();
		List<BuildSite> made = new ArrayList<>();
		try {
			BlockPos far = new BlockPos(6100, 70, 6000);
			WonderSite coloured = manager.begin(STADIUM, hall, UUID.randomUUID(), "Jesse", 1);
			wonders.add(coloured.id());
			manager.setStage(coloured.id(), 2);
			List<BuildSite> blue = WonderStages.start(level, coloured.id(),
				new BlueprintData.Placement(Ids.of(level.dimension()), far, Rotation.NONE, Mirror.NONE), far, null);
			made.addAll(blue);
			helper.assertTrue(!blue.isEmpty(), "stage II has no sites");
			Map<Block, Integer> count = new HashMap<>();
			for (BuildSite site : blue) {
				helper.assertTrue(site.structure().getPath().startsWith("styled/colours_blue_red/aliveworkplace/wonder/stadium/"),
					"stage II's part: " + site.structure());
				Blueprint bp = BlueprintLibrary.get(level, site.structure()).orElseThrow(() -> new GameTestAssertException(site.structure() + " doesn't load"));
				bp.blocks().forEach(e -> count.merge(e.state().getBlock(), 1, Integer::sum));
			}
			helper.assertTrue(count.getOrDefault(Blocks.BLUE_CARPET, 0) > 100 && count.getOrDefault(Blocks.RED_CARPET, 0) > 100
				&& !count.containsKey(Blocks.WHITE_CARPET), "stage II's seats: " + count.get(Blocks.BLUE_CARPET) + " blue, "
				+ count.get(Blocks.RED_CARPET) + " red, " + count.get(Blocks.WHITE_CARPET) + " white");

			BlockPos far2 = new BlockPos(6300, 70, 6000);
			manager.setStage(coloured.id(), 1);
			List<BuildSite> field = WonderStages.start(level, coloured.id(),
				new BlueprintData.Placement(Ids.of(level.dimension()), far2, Rotation.NONE, Mirror.NONE), far2, null);
			made.addAll(field);
			Map<Block, Integer> lines = new HashMap<>();
			for (BuildSite site : field) {
				BlueprintLibrary.get(level, site.structure()).orElseThrow().blocks().forEach(e -> lines.merge(e.state().getBlock(), 1, Integer::sum));
			}
			helper.assertTrue(lines.getOrDefault(Blocks.WHITE_CONCRETE, 0) > 50, "the field's lines: " + lines.get(Blocks.WHITE_CONCRETE));

			entity.setColours(null);
			WonderSite plain = manager.begin(STADIUM, hall, UUID.randomUUID(), "Jesse", 1);
			wonders.add(plain.id());
			BlockPos far3 = new BlockPos(6500, 70, 6000);
			List<BuildSite> red = WonderStages.start(level, plain.id(),
				new BlueprintData.Placement(Ids.of(level.dimension()), far3, Rotation.NONE, Mirror.NONE), far3, null);
			made.addAll(red);
			helper.assertTrue(red.stream().allMatch(s -> s.structure().getPath().startsWith("wonder/stadium/")),
				"without colours: " + red.stream().map(BuildSite::structure).toList());
		} finally {
			made.forEach(s -> sites.remove(s.id()));
			wonders.forEach(manager::remove);
			entity.setColours(null);
			level.removeBlock(hall, false);
		}
		helper.succeed();
	}
}
