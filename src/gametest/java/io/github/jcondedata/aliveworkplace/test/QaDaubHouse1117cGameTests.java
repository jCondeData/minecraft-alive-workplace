package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1117-0953) for the Daub House (ROADMAP 36.23, 36.23a-c, 49.2; docs/wiki/daub-house.md), written from the
 * spec and the wiki, covering what {@link DaubHouseGameTests} doesn't: the owner's three rules on every copy a player can
 * meet (the blueprint, its modded copy and the five village pieces), every furniture spot on its own block, each village
 * style in the materials the wiki names, the village piece's floor and front row, the Steward's rule from its other
 * sides, and the Blueprint Table's lock and the two ways the wiki says unlock it.
 */
public class QaDaubHouse1117cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String SMALL = "aliveworkplace_test:build_area";
	static final StarterBlueprints.Entry DAUB = StarterBlueprints.DAUB_HOUSE;
	static final BlockPos DOOR = new BlockPos(4, 1, 2);
	/** The kitchen's walls start at x = 8 (the wiki: "a low stone kitchen ... on the east side"). */
	static final int KITCHEN_X0 = 8;
	/** Kinds a short lamp or a candle may stand on (36.23b: "a short lamp should sit on a table or side table"). */
	static final Set<FurnitureSpot.Kind> TABLES = Set.of(FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.SIDE_TABLE, FurnitureSpot.Kind.NIGHTSTAND,
		FurnitureSpot.Kind.DESK, FurnitureSpot.Kind.COUNTER, FurnitureSpot.Kind.SHELF, FurnitureSpot.Kind.CUPBOARD);

	// --- helpers --------------------------------------------------------------------------------------------------

	/** A structure template's blocks by position (template coordinates). */
	static Map<BlockPos, BlockState> cells(StructureTemplate template) {
		CompoundTag tag = template.save(new CompoundTag());
		ListTag palette = tag.contains("palette") ? tag.getList("palette", Tag.TAG_COMPOUND)
			: tag.getList("palettes", Tag.TAG_LIST).getList(0);
		List<BlockState> byIndex = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			byIndex.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
		}
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompound(i);
			ListTag p = b.getList("pos", Tag.TAG_INT);
			out.put(new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)), byIndex.get(b.getInt("state")));
		}
		return out;
	}

	static Map<BlockPos, BlockState> cells(Blueprint b) {
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		for (Blueprint.Entry e : b.blocks()) {
			out.put(e.pos(), e.state());
		}
		return out;
	}

	static StructureTemplate template(GameTestHelper helper, ResourceLocation id) {
		return helper.getLevel().getStructureManager().get(id).orElseThrow(() -> new GameTestAssertException("no structure " + id));
	}

	static Blueprint blueprint(GameTestHelper helper) {
		return BlueprintLibrary.get(helper.getLevel(), DAUB.id()).orElseThrow(() -> new GameTestAssertException("no blueprint " + DAUB.id()));
	}

	/** Every copy of the Daub House a player can meet: the blueprint, its modded copy, the five village pieces. */
	static Map<String, Map<BlockPos, BlockState>> copies(GameTestHelper helper) {
		Map<String, Map<BlockPos, BlockState>> out = new LinkedHashMap<>();
		out.put("blueprint", cells(blueprint(helper)));
		out.put("modded copy", cells(template(helper, AliveWorkplace.id("modded/" + DAUB.id().getPath()))));
		for (String style : VillageHouses.STYLES) {
			out.put(style + " village piece", cells(template(helper, VillageHouses.home(style, DAUB.id().getPath()))));
		}
		return out;
	}

	static BlockState at(Map<BlockPos, BlockState> cells, BlockPos pos) {
		return cells.getOrDefault(pos, Blocks.AIR.defaultBlockState());
	}

	static String name(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	static Set<String> names(Map<BlockPos, BlockState> cells) {
		return cells.values().stream().map(QaDaubHouse1117cGameTests::name).collect(Collectors.toSet());
	}

	static boolean jobSite(BlockState state) {
		return PoiTypes.forState(state).filter(h -> BuiltInRegistries.VILLAGER_PROFESSION.stream()
			.anyMatch(p -> p != VillagerProfession.NONE && p.heldJobSite().test(h))).isPresent();
	}

	static BooleanProperty side(Direction d) {
		return switch (d) {
			case NORTH -> CrossCollisionBlock.NORTH;
			case SOUTH -> CrossCollisionBlock.SOUTH;
			case EAST -> CrossCollisionBlock.EAST;
			default -> CrossCollisionBlock.WEST;
		};
	}

	// --- 36.23b/c on every copy -----------------------------------------------------------------------------------

	/**
	 * The owner's three rules and the house's make-up hold on every copy a player can meet, not only the blueprint: every
	 * fence joins exactly as the game would join it (so the loft railing reads as one joined run of 5 with its corner
	 * turning), no short lamp or candle stands on a floor (each sits on a table-kind furniture piece, or hangs), every
	 * plate is on a kitchen counter or shelf and none is near the front door, 4 beds and at most one job block (36.23),
	 * the kitchen's crafting table.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theOwnersRulesHoldOnEveryCopy(GameTestHelper helper) {
		List<FurnitureSpot> spots = BlueprintFurniture.of(DAUB.id());
		Set<BlockPos> tables = spots.stream().filter(s -> TABLES.contains(s.kind())).flatMap(s -> s.positions().stream()).collect(Collectors.toSet());
		Set<BlockPos> counters = spots.stream().filter(s -> s.kind() == FurnitureSpot.Kind.COUNTER || s.kind() == FurnitureSpot.Kind.SHELF)
			.flatMap(s -> s.positions().stream()).collect(Collectors.toSet());
		Set<Block> plates = spots.stream().filter(s -> s.kind() == FurnitureSpot.Kind.CROCKERY).flatMap(s -> s.blocks().stream())
			.map(c -> c.state().getBlock()).collect(Collectors.toSet());
		helper.assertTrue(!plates.isEmpty() && !tables.isEmpty() && !counters.isEmpty(), "the Daub House's spots have no plates, tables or counters");
		int checked = 0;
		for (Map.Entry<String, Map<BlockPos, BlockState>> copy : copies(helper).entrySet()) {
			String what = copy.getKey();
			Map<BlockPos, BlockState> cells = copy.getValue();
			// fences join as the game joins them
			List<BlockPos> railing = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
				if (!(e.getValue().getBlock() instanceof FenceBlock fence)) {
					continue;
				}
				if (e.getKey().getY() >= 4) {
					railing.add(e.getKey());
				}
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockState next = at(cells, e.getKey().relative(d));
					boolean joins = fence.connectsTo(next, next.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, d.getOpposite()), d.getOpposite());
					helper.assertTrue(e.getValue().getValue(side(d)) == joins, what + ": the fence at " + e.getKey() + " is "
						+ (joins ? "not joined" : "joined") + " to its " + d + " neighbour " + next + " (it's " + e.getValue() + ")");
					checked++;
				}
			}
			// the loft railing is one joined run that turns a corner
			helper.assertTrue(railing.size() >= 3, what + ": the loft railing has " + railing.size() + " posts");
			Set<BlockPos> reached = new HashSet<>();
			ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(railing.get(0)));
			while (!todo.isEmpty()) {
				BlockPos p = todo.poll();
				if (!reached.add(p)) {
					continue;
				}
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (at(cells, p).getValue(side(d)) && railing.contains(p.relative(d))) {
						todo.add(p.relative(d));
					}
				}
			}
			helper.assertTrue(reached.size() == railing.size(), what + ": the loft railing falls apart: " + reached.size() + " of " + railing);
			boolean turns = railing.stream().anyMatch(p -> {
				BlockState s = at(cells, p);
				return (s.getValue(CrossCollisionBlock.NORTH) || s.getValue(CrossCollisionBlock.SOUTH))
					&& (s.getValue(CrossCollisionBlock.EAST) || s.getValue(CrossCollisionBlock.WEST));
			});
			helper.assertTrue(turns, what + ": the loft railing has no joined corner");

			// no short lamp or candle on a floor
			for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
				BlockState s = e.getValue();
				boolean standing = s.getBlock() instanceof LanternBlock && !s.getValue(LanternBlock.HANGING) || s.getBlock() instanceof CandleBlock;
				if (standing) {
					helper.assertTrue(tables.contains(e.getKey().below()), what + ": " + name(s) + " at " + e.getKey() + " stands on "
						+ at(cells, e.getKey().below()) + ", not on a table");
				}
			}
			// the plates: in the kitchen, on its counters or shelves, none near the door
			int plateCount = 0;
			for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
				if (!plates.contains(e.getValue().getBlock())) {
					continue;
				}
				plateCount++;
				BlockPos p = e.getKey();
				helper.assertTrue(p.getX() >= KITCHEN_X0, what + ": plates outside the kitchen at " + p);
				helper.assertTrue(counters.contains(p.below()), what + ": plates at " + p + " not on a counter or shelf but on " + at(cells, p.below()));
				helper.assertTrue(Math.abs(p.getX() - DOOR.getX()) + Math.abs(p.getZ() - DOOR.getZ()) > 3, what + ": plates by the front door at " + p);
			}
			helper.assertTrue(plateCount >= 1, what + ": no plates in the kitchen");
			// 4 beds, no job block
			long beds = cells.values().stream().filter(s -> s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD).count();
			helper.assertTrue(beds == 4, what + " has " + beds + " beds, not 4");
			// 36.23: "one job block at most, as village houses": only the kitchen's crafting table (a Carpenter's)
			List<BlockPos> jobs = cells.entrySet().stream().filter(e -> jobSite(e.getValue())).map(Map.Entry::getKey).toList();
			helper.assertTrue(jobs.size() <= 1, what + " has " + jobs.size() + " job blocks: " + jobs);
			for (BlockPos p : jobs) {
				helper.assertTrue(at(cells, p).is(Blocks.CRAFTING_TABLE) && p.getX() >= KITCHEN_X0, what + "'s job block is " + at(cells, p) + " at " + p);
			}
			BlockState door = at(cells, DOOR);
			helper.assertTrue(door.getBlock() instanceof DoorBlock, what + " has no front door at " + DOOR + ": " + door);
		}
		helper.assertTrue(checked > 0, "no fence checked");
		AliveWorkplace.LOG.info("QA Daub House: the owner's rules hold on 7 copies ({} fence sides)", checked);
		helper.succeed();
	}

	// --- furniture spots --------------------------------------------------------------------------------------------

	/**
	 * Every furniture spot of the blueprint and of each village piece is a real piece of that copy: each cell inside the
	 * 13 x 14 x 13 house, on a non-air block that is the spot's own block, no position claimed by two spots, each with
	 * what it stands on; and each village piece's spots still hold its four beds.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyFurnitureSpotIsOnItsOwnBlock(GameTestHelper helper) {
		Map<String, ResourceLocation> ids = new LinkedHashMap<>();
		ids.put("blueprint", DAUB.id());
		for (String style : VillageHouses.STYLES) {
			ids.put(style, VillageHouses.home(style, DAUB.id().getPath()));
		}
		int cellsChecked = 0;
		for (Map.Entry<String, ResourceLocation> e : ids.entrySet()) {
			String what = e.getKey();
			Map<BlockPos, BlockState> cells = what.equals("blueprint") ? cells(blueprint(helper)) : cells(template(helper, e.getValue()));
			List<FurnitureSpot> spots = BlueprintFurniture.of(e.getValue());
			helper.assertTrue(!spots.isEmpty(), what + " has no furniture spots");
			Map<BlockPos, FurnitureSpot.Kind> claimed = new HashMap<>();
			for (FurnitureSpot s : spots) {
				helper.assertTrue(s.support() == null || !s.support().isEmpty(), what + ": the " + s.kind() + " at " + s.pos() + " stands on nothing");
				for (FurnitureSpot.Cell c : s.blocks()) {
					BlockPos p = s.pos().offset(c.at());
					helper.assertTrue(p.getX() >= 0 && p.getY() >= 0 && p.getZ() >= 0 && p.getX() < 13 && p.getY() < 14 && p.getZ() < 13,
						what + ": the " + s.kind() + " spot reaches outside the house at " + p);
					BlockState real = at(cells, p);
					helper.assertTrue(!real.isAir() && real.getBlock() == c.state().getBlock(),
						what + ": the " + s.kind() + " spot at " + p + " says " + c.state() + " but the house has " + real);
					FurnitureSpot.Kind before = claimed.put(p, s.kind());
					helper.assertTrue(before == null, what + ": " + p + " is both a " + before + " and a " + s.kind());
					cellsChecked++;
				}
			}
			long bedSpots = spots.stream().filter(s -> s.kind() == FurnitureSpot.Kind.BED).count();
			helper.assertTrue(bedSpots == 4, what + " has " + bedSpots + " bed spots, not 4");
		}
		AliveWorkplace.LOG.info("QA Daub House: {} furniture spot cells on their blocks", cellsChecked);
		helper.succeed();
	}

	// --- village pieces ---------------------------------------------------------------------------------------------

	/** Each village style wears the materials docs/wiki/daub-house.md names (all but desert's walls, in the bug class). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void eachVillageStyleWearsTheWikisMaterials(GameTestHelper helper) {
		Map<String, Set<String>> named = new LinkedHashMap<>();
		for (String style : VillageHouses.STYLES) {
			named.put(style, names(cells(template(helper, VillageHouses.home(style, DAUB.id().getPath())))));
		}
		// plains: as drawn (mud-brick daub, dark oak, brick roof, a cobble plinth)
		expect(helper, "plains", named.get("plains"), List.of("mud_bricks", "dark_oak_log", "bricks", "brick_stairs", "cobblestone"), List.of());
		// desert: sandstone plinth, jungle wood, a red sandstone roof
		expect(helper, "desert", named.get("desert"), List.of("sandstone", "jungle_log", "jungle_door"),
			List.of("dark_oak_log", "spruce_log", "bricks", "brick_stairs", "cobblestone", "grass_block", "poppy"));
		helper.assertTrue(named.get("desert").stream().anyMatch(n -> n.contains("red_sandstone") && n.endsWith("_stairs")), "desert: no red sandstone roof");
		// savanna: white terracotta in an acacia frame, an acacia roof
		expect(helper, "savanna", named.get("savanna"), List.of("white_terracotta", "acacia_log", "acacia_stairs", "acacia_door"),
			List.of("mud_bricks", "dark_oak_log", "bricks", "brick_stairs", "spruce_log"));
		// snowy: calcite between stripped spruce, a dark oak roof on stone bricks
		expect(helper, "snowy", named.get("snowy"), List.of("calcite", "stripped_spruce_log", "dark_oak_stairs", "stone_bricks"),
			List.of("mud_bricks", "dark_oak_log", "bricks", "brick_stairs"));
		// taiga: spruce boards in a spruce frame, a dark oak roof on mossy cobble
		expect(helper, "taiga", named.get("taiga"), List.of("spruce_planks", "spruce_log", "dark_oak_stairs", "mossy_cobblestone"),
			List.of("mud_bricks", "dark_oak_log", "bricks", "brick_stairs"));
		helper.succeed();
	}

	static void expect(GameTestHelper helper, String style, Set<String> names, List<String> has, List<String> hasNot) {
		for (String n : has) {
			helper.assertTrue(names.contains(n), style + " Daub House has no " + n + ": " + new java.util.TreeSet<>(names));
		}
		for (String n : hasNot) {
			helper.assertTrue(!names.contains(n), style + " Daub House still has " + n);
		}
	}

	/**
	 * The village piece meets the street and keeps its floor: the street jigsaw at (4, 1, 0) points out of the front, two
	 * blocks before the door; the worker-pool jigsaw on the hall floor (4, 0, 5) turns back into a solid floor block once
	 * the villager spawns (no hole), and its own style's worker pool; the front row has no flower beds or grass left.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theVillagePieceMeetsTheStreetAndKeepsItsFloor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String style : VillageHouses.STYLES) {
			ResourceLocation id = VillageHouses.home(style, DAUB.id().getPath());
			StructureTemplate template = template(helper, id);
			Map<BlockPos, BlockState> cells = cells(template);
			Map<BlockPos, CompoundTag> nbt = new HashMap<>();
			CompoundTag saved = template.save(new CompoundTag());
			ListTag blocks = saved.getList("blocks", Tag.TAG_COMPOUND);
			for (int i = 0; i < blocks.size(); i++) {
				CompoundTag b = blocks.getCompound(i);
				if (b.contains("nbt")) {
					ListTag p = b.getList("pos", Tag.TAG_INT);
					nbt.put(new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)), b.getCompound("nbt"));
				}
			}
			BlockState street = at(cells, new BlockPos(4, 1, 0));
			helper.assertTrue(street.is(Blocks.JIGSAW), id + ": no jigsaw at (4, 1, 0): " + street);
			helper.assertTrue(street.getValue(net.minecraft.world.level.block.JigsawBlock.ORIENTATION).front() == Direction.NORTH,
				id + ": the street jigsaw doesn't face the street (north): " + street);
			helper.assertTrue(at(cells, DOOR).getBlock() instanceof DoorBlock, id + ": no door behind the street jigsaw");

			BlockPos workerAt = new BlockPos(4, 0, 5);
			BlockState worker = at(cells, workerAt);
			CompoundTag w = nbt.get(workerAt);
			helper.assertTrue(worker.is(Blocks.JIGSAW) && w != null, id + ": no worker jigsaw on the hall floor (4, 0, 5): " + worker);
			helper.assertTrue(w.getString("pool").equals("aliveworkplace:village/" + style + "/workers"), id + ": the worker jigsaw's pool is " + w.getString("pool"));
			BlockState floor;
			try {
				floor = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), w.getString("final_state"), false)
					.blockState();
			} catch (Exception ex) {
				throw new GameTestAssertException(id + ": the worker jigsaw's final state doesn't parse: " + w.getString("final_state"));
			}
			helper.assertTrue(!floor.isAir() && !floor.is(Blocks.STRUCTURE_VOID) && floor.isCollisionShapeFullBlock(level, BlockPos.ZERO),
				id + ": the hall floor at (4, 0, 5) turns into " + floor + " (a hole in the floor)");
			BlockState neighbour = at(cells, new BlockPos(5, 0, 5));
			helper.assertTrue(floor.getBlock() == neighbour.getBlock(), id + ": the hall floor at (4, 0, 5) becomes " + name(floor) + " beside " + name(neighbour));

			for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
				if (e.getKey().getZ() == 0 && e.getKey().getY() <= 2) {
					BlockState s = e.getValue();
					helper.assertTrue(!s.is(BlockTags.FLOWERS) && !s.is(Blocks.GRASS_BLOCK) && !s.is(Blocks.SAND) && !s.is(Blocks.DEAD_BUSH) && !s.is(Blocks.DIRT),
						id + ": the front row still has a flower bed: " + s + " at " + e.getKey());
				}
			}
		}
		helper.succeed();
	}

	// --- the Steward ---------------------------------------------------------------------------------------------

	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static BlockPos hall(GameTestHelper helper, VillageRanks.Rank rank) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> helper.getLevel().removeBlock(hall, false));
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setRank(rank);
		return hall;
	}

	private static void villagers(GameTestHelper helper, int from, int n) {
		for (int i = from; i < from + n; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + 2 * i, 2, 20));
			v.setNoAi(true);
		}
	}

	private static List<String> wishes(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		return StewardWishes.rank(StewardRules.all(), StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, StewardWishes.day(level))
			.stream().map(w -> w.rule().getPath()).toList();
	}

	/**
	 * "Three or more beds short": a Hamlet four and five beds short still plans the Daub House first, before the Stone
	 * House; a Town three short (above the Village rank) never plans one.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaDaubSteward1117c"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDaubSteward1117c")
	public void aHamletFourOrFiveShortStillPlansADaubHouseATownNever(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 0, 4);
		List<String> four = wishes(helper, hall);
		helper.assertTrue(four.contains("homes_house_t1_b"), "a Hamlet four beds short plans no Daub House: " + four);
		helper.assertTrue(!four.contains("homes_stone_house") || four.indexOf("homes_house_t1_b") < four.indexOf("homes_stone_house"),
			"four short, the Daub House isn't before the Stone House: " + four);
		villagers(helper, 4, 1);
		List<String> five = wishes(helper, hall);
		helper.assertTrue(five.contains("homes_house_t1_b"), "a Hamlet five beds short plans no Daub House: " + five);
		ServerLevel level = helper.getLevel();
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		List<String> town = wishes(helper, hall);
		helper.assertTrue(!town.contains("homes_house_t1_b"), "a Town five beds short plans a Daub House: " + town);
		helper.succeed();
	}

	/** A Hamlet with no villagers (no beds short) plans no Daub House: the rule needs the beds to be short. */
	//$ gametest_ticks_batch AREA '40' '"qaDaubStewardNone1117c"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDaubStewardNone1117c")
	public void aHamletWithNobodyShortPlansNoDaubHouse(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		List<String> none = wishes(helper, hall);
		helper.assertTrue(!none.contains("homes_house_t1_b"), "no beds short, a Daub House anyway: " + none);
		villagers(helper, 0, 1);
		List<String> one = wishes(helper, hall);
		helper.assertTrue(!one.contains("homes_house_t1_b"), "one bed short, a Daub House anyway: " + one);
		helper.succeed();
	}

	// --- the Blueprint Table -------------------------------------------------------------------------------------

	private static final BlockPos UNLOCK_HALL = new BlockPos(1, 2, 1);
	private static final BlockPos TABLE = new BlockPos(5, 2, 5);

	private static void unlockSetUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		boolean was = BlueprintUnlocks.ENABLED;
		int radius = VillageHalls.RADIUS;
		BlueprintUnlocks.ENABLED = true;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			BlueprintUnlocks.ENABLED = was;
			VillageHalls.RADIUS = radius;
		});
		helper.setBlock(UNLOCK_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		VillageHalls.forgetNearest();
	}

	/**
	 * The wiki: "Like most builds it starts locked" (not a "???", with a hint the player can read), and "a Blueprint of
	 * it used on the Table" unlocks it, through the player's own click on the Table.
	 */
	//$ gametest_ticks_batch SMALL '40' '"qaDaubUnlockItem1117c"'
	@GameTest(template = SMALL, timeoutTicks = 40, batch = "qaDaubUnlockItem1117c")
	public void theDaubHouseStartsLockedAndAFoundBlueprintUnlocksIt(GameTestHelper helper) {
		unlockSetUp(helper);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(UNLOCK_HALL);
			BlockPos table = helper.absolutePos(TABLE);
			helper.assertTrue(hall.equals(BlueprintUnlocks.village(level, table)), "the Table isn't in the test's village");
			helper.assertTrue(!BlueprintUnlocks.starting().contains(DAUB.id()), "the Daub House is in the starting set");
			helper.assertTrue(BlueprintUnlocks.state(level, hall, DAUB.id()) == BlueprintUnlocks.State.LOCKED,
				"the Daub House doesn't start locked: " + BlueprintUnlocks.state(level, hall, DAUB.id()));
			String hint = BlueprintUnlocks.hint(DAUB.id());
			helper.assertTrue(Language.getInstance().has(hint) && !hint.endsWith(".rare"), "its hint on the Table: " + hint);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				player.setGameMode(GameType.SURVIVAL);
				player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
				ItemStack found = BlueprintItem.create(DAUB.id(), DAUB.size());
				player.setItemInHand(InteractionHand.MAIN_HAND, found);
				player.gameMode.useItemOn(player, level, found, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
				helper.assertTrue(BlueprintUnlocks.state(level, hall, DAUB.id()) == BlueprintUnlocks.State.UNLOCKED,
					"a Daub House Blueprint used on the Table didn't unlock it");
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		});
	}

	/** The wiki: "a builder's level-up unlocks it": the level-ups' queue of locked builds reaches the Daub House. */
	//$ gametest_ticks_batch SMALL '40' '"qaDaubUnlockLevel1117c"'
	@GameTest(template = SMALL, timeoutTicks = 40, batch = "qaDaubUnlockLevel1117c")
	public void buildersLevelUpsReachTheDaubHouse(GameTestHelper helper) {
		unlockSetUp(helper);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(UNLOCK_HALL);
			int levelUps = 0;
			boolean reached = false;
			while (levelUps < 200 && !reached) {
				List<ResourceLocation> next = BlueprintUnlocks.nextForLevelUp(level, hall);
				if (next.isEmpty()) {
					break;
				}
				reached = next.contains(DAUB.id());
				for (ResourceLocation id : next) {
					BlueprintUnlocks.unlock(level, hall, id, List.of());
				}
				levelUps++;
			}
			helper.assertTrue(reached, "no builder's level-up ever unlocks the Daub House (" + levelUps + " level-ups)");
			helper.assertTrue(BlueprintUnlocks.state(level, hall, DAUB.id()) == BlueprintUnlocks.State.UNLOCKED, "the Daub House is still locked");
			AliveWorkplace.LOG.info("QA Daub House: unlocked by level-up number {}", levelUps);
			helper.succeed();
		});
	}
}
