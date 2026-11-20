package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.test.WonderBlueprintGameTests.Part;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * QA (lane-b, 2026-10-26): the Wonder designs of Milestone 35 (35.9, 35.11, 35.13, 35.15, 35.17, 35.19, 35.21, 35.23)
 * checked against what their roadmap items promise and no other test proves. Read from each Wonder's data file and its
 * stage parts as they ship (the same reader as {@link WonderBlueprintGameTests}):
 * <ul>
 * <li>its sizes and heights as the spec states them (within a block or two);</li>
 * <li>the features its spec names are there (bells, lantern room, enchanting table, grindstones, chests ...);</li>
 * <li>at the end of every stage, nothing hangs in the air unconnected to the ground (the village lives beside each
 * stage for days), and, built for real in the level, every block can stay where it is (vanilla's own rules: plants on
 * soil, lanterns hung, banners on a wall, nothing that falls over air);</li>
 * <li>every door has two clear blocks on both of its sides (a villager walks through), and is whole; every bed is whole;</li>
 * <li>nothing comes for free: no container, lectern, hive or composter holds anything;</li>
 * <li>the Lighthouse's and the Stadium's lamps light when their inverted daylight detectors switch on at night, and the
 * Library's enchanting table has its fifteen bookshelves (level 30 enchantments);</li>
 * <li>every name and stage title reads as the spec writes it, in American English.</li>
 * </ul>
 * The Stadium's file needs Cobblemon to load in game, but its parts read without it (its Healing Machine reads as air:
 * the compat StadiumCompatTests check that block).
 */
public class QaWonderDesignsGameTests implements FabricGameTest {
	static final String BATCH = "qa_wonder_designs";

	static final List<String> WONDERS = List.of("great_cathedral", "lighthouse", "great_library", "great_wall", "observatory",
		"hanging_gardens", "colossus", "stadium");

	@GameTestGenerator
	public Collection<TestFunction> designs() {
		List<TestFunction> out = new ArrayList<>();
		for (String id : WONDERS) {
			add(out, "qaWonderSizes_" + id, id, QaWonderDesignsGameTests::sizes);
			add(out, "qaWonderFeatures_" + id, id, QaWonderDesignsGameTests::features);
			add(out, "qaWonderStagesGrounded_" + id, id, QaWonderDesignsGameTests::grounded);
			add(out, "qaWonderStagesStand_" + id, id, QaWonderDesignsGameTests::stand); // the Library and the Gardens since B538, B539
			add(out, "qaWonderDoorways_" + id, id, QaWonderDesignsGameTests::doorways);
			add(out, "qaWonderNothingFree_" + id, id, QaWonderDesignsGameTests::nothingFree);
			add(out, "qaWonderNames_" + id, id, QaWonderDesignsGameTests::names);
			// the Observatory's too since B537 (its bed was half the wrong way round)
			add(out, "qaWonderBedsWhole_" + id, id, (h, w) -> h.assertTrue(brokenBeds(whole(h, w)).isEmpty(), w + ": " + brokenBeds(whole(h, w))));
		}
		add(out, "qaWonderLampsLightAtNight_lighthouse", "lighthouse", (h, id) -> lampsLight(h, id, 1, 8));
		add(out, "qaWonderLampsLightAtNight_stadium", "stadium", (h, id) -> lampsLight(h, id, 4, 4));
		add(out, "qaWonderEnchantingPower_great_library", "great_library", QaWonderDesignsGameTests::enchantingPower);
		return out;
	}

	static void add(List<TestFunction> out, String name, String id, BiConsumer<GameTestHelper, String> check) {
		out.add(new TestFunction(BATCH, name, FabricGameTest.EMPTY_STRUCTURE, 200, 0, true, helper -> {
			check.accept(helper, id);
			helper.succeed();
		}));
	}

	// --- the Wonder as its stages leave it ------------------------------------------------------------------------------

	static List<List<Part>> stages(GameTestHelper helper, String id) {
		return WonderBlueprintGameTests.stages(helper.getLevel().getServer(), id);
	}

	/** The blocks standing after each stage (index n: stages 1 to n + 1), from the Wonder's corner. */
	static List<Map<BlockPos, BlockState>> standing(List<List<Part>> stages) {
		List<Map<BlockPos, BlockState>> out = new ArrayList<>();
		Map<BlockPos, BlockState> now = new HashMap<>();
		for (List<Part> stage : stages) {
			for (Part p : stage) {
				for (Blueprint.Entry e : p.blueprint().blocks()) {
					BlockPos at = p.offset().offset(e.pos()).immutable();
					if (e.state().isAir()) {
						now.remove(at);
					} else {
						now.put(at, e.state());
					}
				}
			}
			out.add(new HashMap<>(now));
		}
		return out;
	}

	static Map<BlockPos, BlockState> whole(GameTestHelper helper, String id) {
		List<Map<BlockPos, BlockState>> s = standing(stages(helper, id));
		return s.get(s.size() - 1);
	}

	static String name(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	static int count(Map<BlockPos, BlockState> blocks, Predicate<BlockState> what) {
		return (int) blocks.values().stream().filter(what).count();
	}

	static Predicate<BlockState> is(String... ids) {
		Set<String> set = Set.of(ids);
		return s -> set.contains(name(s));
	}

	/** {min x, min y, min z, max x, max y, max z} of {@code blocks} matching {@code what}. */
	static int[] box(Map<BlockPos, BlockState> blocks, Predicate<BlockPos> where) {
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (BlockPos p : blocks.keySet()) {
			if (where.test(p)) {
				b[0] = Math.min(b[0], p.getX());
				b[1] = Math.min(b[1], p.getY());
				b[2] = Math.min(b[2], p.getZ());
				b[3] = Math.max(b[3], p.getX());
				b[4] = Math.max(b[4], p.getY());
				b[5] = Math.max(b[5], p.getZ());
			}
		}
		return b;
	}

	static int[] boxOf(Map<BlockPos, BlockState> blocks, Predicate<BlockState> what) {
		return box(blocks, p -> what.test(blocks.get(p)));
	}

	static void near(List<String> wrong, String what, int actual, int spec, int tolerance) {
		if (Math.abs(actual - spec) > tolerance) {
			wrong.add(what + " is " + actual + ", not " + spec + (tolerance > 0 ? " (+-" + tolerance + ")" : ""));
		}
	}

	// --- sizes ----------------------------------------------------------------------------------------------------------

	/** The sizes the spec states: the whole's width, length and height, and the parts it measures out. */
	static void sizes(GameTestHelper helper, String id) {
		Map<BlockPos, BlockState> all = whole(helper, id);
		int[] b = box(all, p -> true);
		int w = b[3] - b[0] + 1;
		int h = b[4] + 1;
		int l = b[5] - b[2] + 1;
		List<String> wrong = new ArrayList<>();
		helper.assertTrue(b[1] == 0, id + " starts at y " + b[1] + ", not on the clicked block");
		switch (id) {
			case "great_cathedral" -> {
				// "about 27 wide (39 across the transepts) and 55 long, its two west towers 44 high under stone spires reaching 52"
				near(wrong, "its width across the transepts", w, 39, 2);
				near(wrong, "its length", l, 55, 2);
				near(wrong, "its spires' tops", b[4], 52, 1);
				int mid = (b[0] + b[3]) / 2;
				near(wrong, "the north-west tower's spire top", box(all, p -> p.getZ() <= 12 && p.getX() < mid)[4], 52, 1);
				near(wrong, "the south-west tower's spire top", box(all, p -> p.getZ() <= 12 && p.getX() > mid)[4], 52, 1);
				int nave = box(all, p -> p.getZ() == 25 && p.getY() == 2)[3] - box(all, p -> p.getZ() == 25 && p.getY() == 2)[0] + 1;
				near(wrong, "the nave and aisles' width (z 25)", nave, 27, 2);
			}
			case "lighthouse" -> {
				// 52.6, rebuilt (briefs/lighthouse.md, js/lighthouse/plan.mjs): "25 x 33 and 46 high: a round tower that
				// telescopes in from its foot (13 across) through its waist (11) to the pale shaft (9), a gallery on two corbel
				// courses at 37". The same kinds of check as the 35.11 pins: the plot, the tower's widest and narrowest measured
				// by its building stone round the sea lantern, the gallery's level.
				near(wrong, "its width", w, 25, 1);
				near(wrong, "its length", l, 33, 1);
				near(wrong, "its height", h, 46, 1);
				int[] lantern = boxOf(all, is("minecraft:sea_lantern"));
				int cx = lantern[0];
				int cz = lantern[2];
				Predicate<BlockState> stone = is("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:quartz_bricks");
				int low = Integer.MIN_VALUE;
				int high = Integer.MAX_VALUE;
				for (int y = 5; y <= 36; y++) { // over the bastion's deck (walked at y 4) to the shaft's top
					final int yy = y;
					int[] row = box(all, p -> p.getY() == yy && Math.abs(p.getX() - cx) <= 8 && Math.abs(p.getZ() - cz) <= 8 && stone.test(all.get(p)));
					if (row[0] != Integer.MAX_VALUE) {
						int across = Math.max(row[3] - row[0], row[5] - row[2]) + 1;
						low = Math.max(low, across);
						high = Math.min(high, across);
					}
				}
				near(wrong, "the tower's widest (its foot)", low, 13, 1);
				near(wrong, "the tower's narrowest (the shaft)", high, 9, 0);
				int gallery = -1;
				int widest = 0;
				for (int y = 30; y <= 42; y++) {
					final int yy = y;
					int[] row = box(all, p -> p.getY() == yy && Math.abs(p.getX() - cx) <= 9 && Math.abs(p.getZ() - cz) <= 9);
					if (row[0] != Integer.MAX_VALUE && row[3] - row[0] + 1 > widest) {
						widest = row[3] - row[0] + 1;
						gallery = y;
					}
				}
				near(wrong, "the gallery's level", gallery, 37, 1);
				near(wrong, "the gallery's width", widest, 13, 0);
				int[] post = boxOf(all, is("aliveworkplace:travel_post"));
				near(wrong, "the Travel Post's x (the plot's sea-side edge)", post[0], 24, 0);
			}
			case "great_library" -> {
				// 52.6, rebuilt (briefs/great_library.md, js/great_library/plan.mjs): "41 x 41 and 34 high", the brief kept the 35.13
				// size, so these pins stand
				near(wrong, "its width", w, 41, 1);
				near(wrong, "its length", l, 41, 1);
				near(wrong, "its height", h, 34, 1);
			}
			case "great_wall" -> {
				// 52.6, rebuilt (briefs/great_wall.md, js/great_wall/plan.mjs): "97 long; the runs 12 high and 7 thick at y 6; four
				// watchtowers 9 x 9, 18 high in stone under spruce hip caps; the Great Gate's middle at x 48"
				near(wrong, "its length", w, 97, 0);
				for (int[] run : new int[][] {{31, 36}, {79, 84}}) {
					int[] wall = box(all, p -> p.getX() >= run[0] && p.getX() <= run[1]);
					near(wrong, "the wall's height at x " + run[0] + "-" + run[1], wall[4] + 1, 12, 1);
					int thinnest = Integer.MAX_VALUE;
					for (int x = run[0]; x <= run[1]; x++) {
						final int xx = x;
						int[] mid = box(all, p -> p.getX() == xx && p.getY() == 6);
						thinnest = Math.min(thinnest, mid[5] - mid[2] + 1);
					}
					near(wrong, "the wall's thickness (at its thinnest, y 6) at x " + run[0] + "-" + run[1], thinnest, 7, 0);
				}
				for (int t : WonderBlueprintGameTests.WALL_TOWERS) {
					int[] stone = box(all, p -> p.getX() >= t && p.getX() <= t + 8 && (name(all.get(p)).contains("stone") || name(all.get(p)).contains("cobble")));
					near(wrong, "the tower at x " + t + "'s walls' height", stone[4] + 1, 18, 1);
					near(wrong, "the tower at x " + t + "'s width", stone[3] - stone[0] + 1, 9, 0);
					int roof = count(filter(all, p -> p.getX() >= t && p.getX() <= t + 8 && p.getY() > stone[4]), is("minecraft:spruce_stairs"));
					helper.assertTrue(roof >= 20, "the tower at x " + t + " has no roof (" + roof + " spruce stairs over its walls)");
				}
				int[] gate = boxOf(all, is("minecraft:spruce_fence_gate"));
				near(wrong, "the gateway's middle (its fence gates)", (gate[0] + gate[3]) / 2, 48, 1);
			}
			case "observatory" -> {
				// 52.6, rebuilt (briefs/observatory.md, js/observatory/plan.mjs): "a court about 31 x 31; a stair up the gnomon's
				// sloping edge from the court to the drum's door; a round drum 13 across, 15 high over its floor, under a dome
				// whose tinted shutter and telescope stand over it". The same kinds of check as the 35.17 pins: the plot, the
				// tower's width at y 20, its height over the floor of its lowest door, the tinted glass over its top.
				near(wrong, "its width", w, 31, 1);
				near(wrong, "its length", l, 31, 1);
				int[] glass = boxOf(all, is("minecraft:tinted_glass"));
				int[] tower = box(all, p -> p.getY() == 20);
				near(wrong, "the drum's width at y 20", tower[3] - tower[0] + 1, 13, 1);
				near(wrong, "the drum's depth at y 20", tower[5] - tower[2] + 1, 13, 1);
				int floor = box(all, p -> p.getX() >= tower[0] && p.getX() <= tower[3] && p.getZ() >= tower[2] && p.getZ() <= tower[5]
					&& all.get(p).getBlock() instanceof DoorBlock)[1] - 1;
				int top = box(all, p -> p.getY() > 10 && name(all.get(p)).contains("deepslate") && p.getX() >= tower[0] && p.getX() <= tower[3]
					&& p.getZ() >= tower[2] && p.getZ() <= tower[5])[4];
				near(wrong, "the drum's height over its floor", top - floor, 15, 2);
				helper.assertTrue(glass[1] > top - 2, "the dome's tinted shutter (y " + glass[1] + ") isn't over the drum (top " + top + ")");
				int[] stair = box(all, p -> p.getX() >= 12 && p.getX() <= 18 && p.getZ() <= tower[2] && is("minecraft:deepslate_brick_stairs").test(all.get(p)));
				near(wrong, "the gnomon stair's foot (z)", stair[2], 1, 1);
				near(wrong, "the gnomon stair's top, at the drum's door floor", stair[4], floor, 1);
			}
			case "hanging_gardens" -> {
				// 52.6, rebuilt (briefs/hanging_gardens.md, js/hanging_gardens/plan.mjs): "the Terraced Mount", a plot 47 x 44 and
				// 33 high; four terraces of mud brick 6 high each (45, 35, 27 and 19 wide, 47, 37, 29 and 21 with their piers), set
				// back toward the rear, so the front falls in steps (fronts at z 4, 14, 22 and 30) and the back rises nearly sheer
				// (backs at z 42, 41, 40 and 39). The same kinds of check as the 35.19 pins: the plot, each terrace's width in its
				// walls' stone, how it steps, and a village farm's chest and composter on each terrace's top.
				near(wrong, "its width", w, 47, 1);
				near(wrong, "its length", l, 44, 1);
				near(wrong, "its height", h, 33, 1);
				int[] across = {47, 37, 29, 21};
				int[] fronts = {4, 14, 22, 30};
				int[] backs = {42, 41, 40, 39};
				Predicate<BlockState> brick = is("minecraft:mud_bricks", "minecraft:packed_mud");
				for (int k = 0; k < 4; k++) {
					final int y = 6 * k + 3;
					int[] t = box(all, p -> p.getY() == y && brick.test(all.get(p)));
					near(wrong, "terrace " + (k + 1) + "'s width (y " + y + ")", t[3] - t[0] + 1, across[k], 1);
					int[] bay = box(all, p -> p.getY() == y && p.getX() >= 16 && p.getX() <= 18 && brick.test(all.get(p))); // the bay between the piers at x 15 and 19
					near(wrong, "terrace " + (k + 1) + "'s front (z, y " + y + ")", bay[2], fronts[k], 0);
					near(wrong, "terrace " + (k + 1) + "'s back (z, y " + y + ")", bay[5], backs[k], 0);
					final int top = 6 * k + 7;
					int chests = count(filter(all, p -> p.getY() == top), is("minecraft:chest"));
					int composters = count(filter(all, p -> p.getY() == top), is("minecraft:composter"));
					helper.assertTrue(chests == 1 && composters == 1, "terrace " + (k + 1) + "'s top (y " + top + ") has " + chests + " chests and "
						+ composters + " composters, not a village farm's one of each");
				}
			}
			case "colossus" -> {
				// "47 high ... on a 13-high stepped plinth with an arch through it (5 wide, 6 high) for the main road"
				near(wrong, "its height", h, 47, 0);
				int[] open = new int[b[3] + 1];
				for (int x = b[0]; x <= b[3]; x++) {
					for (int y = 0; y < 6; y++) {
						boolean through = true;
						for (int z = b[2]; z <= b[5] && through; z++) {
							through = !all.containsKey(new BlockPos(x, y, z));
						}
						if (through) {
							open[x]++;
						}
					}
				}
				int wide = 0;
				int tallest = 0;
				for (int x = b[0]; x <= b[3]; x++) {
					if (open[x] >= 5) {
						wide++;
					}
					tallest = Math.max(tallest, open[x]);
				}
				near(wrong, "the road arch's width (5 high and clear from front to back)", wide, 5, 0);
				near(wrong, "the road arch's height at its crown", tallest, 6, 0);
				int[] figure = box(all, p -> p.getY() >= 13 && name(all.get(p)).equals("minecraft:polished_diorite"));
				int plinth = -1;
				for (int y = 0; y < 20; y++) {
					final int yy = y;
					int[] layer = box(all, p -> p.getY() == yy);
					if (layer[3] - layer[0] + 1 >= 13) {
						plinth = y;
					}
				}
				near(wrong, "the stepped plinth's height (its layers 13 or more across)", plinth + 1, 13, 1);
				helper.assertTrue(figure[1] >= 12, "the figure starts at y " + figure[1]);
			}
			case "stadium" -> {
				// "about 47 x 37 and 16 high" (35.23a: "a little bigger") "a battle field 31 x 21 of grass with white lines"; the
				// 52.6 rebuild is 59 x 49 and 26 high, its field 31 x 23 inside the pitch wall with 167 chalk blocks at y 0
				helper.assertTrue(w >= 47 && w <= 60 && l >= 37 && l <= 50 && h >= 16 && h <= 26,
					"the Stadium is " + w + " x " + l + " and " + h + " high, not a little bigger than 47 x 37 and 16 high");
				int[] field = box(all, p -> p.getY() == 0 && (name(all.get(p)).equals("minecraft:grass_block") || name(all.get(p)).equals("minecraft:white_concrete")));
				int fw = Math.max(field[3] - field[0], field[5] - field[2]) + 1;
				int fl = Math.min(field[3] - field[0], field[5] - field[2]) + 1;
				helper.assertTrue(fw >= 31 && fl >= 21, "the field is " + fw + " x " + fl + ", smaller than 31 x 21");
				int lines = count(filter(all, p -> p.getY() == 0), is("minecraft:white_concrete"));
				helper.assertTrue(lines >= 40, "the field has " + lines + " blocks of white lines");
			}
			default -> throw new IllegalStateException(id);
		}
		helper.assertTrue(wrong.isEmpty(), id + ": " + wrong);
	}

	static Map<BlockPos, BlockState> filter(Map<BlockPos, BlockState> all, Predicate<BlockPos> where) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		all.forEach((p, s) -> {
			if (where.test(p)) {
				out.put(p, s);
			}
		});
		return out;
	}

	// --- features -------------------------------------------------------------------------------------------------------

	/** What the spec names, as "block id or ids" -> the least there must be (a description says what it is). */
	record Feature(String what, int least, String... ids) {
	}

	static List<Feature> features(String id) {
		return switch (id) {
			case "great_cathedral" -> List.of(new Feature("three bells", 3, "minecraft:bell"),
				new Feature("a rose window's white glass", 1, "minecraft:white_stained_glass_pane", "minecraft:white_stained_glass"),
				new Feature("a rose window's light blue glass", 1, "minecraft:light_blue_stained_glass_pane", "minecraft:light_blue_stained_glass"),
				new Feature("a rose window's yellow glass", 1, "minecraft:yellow_stained_glass_pane", "minecraft:yellow_stained_glass"),
				new Feature("a rose window's red glass", 1, "minecraft:red_stained_glass_pane", "minecraft:red_stained_glass"),
				new Feature("pews (dark oak stairs)", 10, "minecraft:dark_oak_stairs"),
				new Feature("an altar of gold", 1, "minecraft:gold_block"), new Feature("candles", 1, "minecraft:white_candle", "minecraft:candle"),
				new Feature("banners on the piers", 2, "minecraft:red_wall_banner", "minecraft:white_wall_banner", "minecraft:blue_wall_banner"),
				new Feature("lecterns in three side chapels", 3, "minecraft:lectern"),
				new Feature("a sacristy's brewing stand", 1, "minecraft:brewing_stand"),
				new Feature("polished andesite piers", 50, "minecraft:polished_andesite"),
				new Feature("a deepslate-tile roof", 200, "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tiles"),
				new Feature("cracked and mossy stone bricks", 50, "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks"));
			case "lighthouse" -> List.of( // 52.6: the rebuilt Lighthouse's named parts (js/lighthouse/plan.mjs)
				new Feature("the Lantern's sea lantern on its pedestal", 1, "minecraft:sea_lantern"),
				new Feature("the Hood's eave of night lamps", 24, "minecraft:redstone_lamp"),
				new Feature("a daylight detector over each lamp", 24, "minecraft:daylight_detector"),
				new Feature("a lightning rod on the Hood", 1, "minecraft:lightning_rod"),
				new Feature("the Lantern's posts and the Hood's stepped roof of copper", 100, "minecraft:waxed_oxidized_cut_copper",
					"minecraft:waxed_oxidized_cut_copper_stairs", "minecraft:waxed_oxidized_cut_copper_slab", "minecraft:waxed_oxidized_copper"),
				new Feature("the Shaft's pale quartz bricks", 250, "minecraft:quartz_bricks"),
				new Feature("dark string courses and window sills", 100, "minecraft:polished_andesite", "minecraft:polished_andesite_slab", "minecraft:polished_andesite_stairs"),
				new Feature("mossy sea stone on the Mole, the Bastion and the Foot", 300, "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"),
				new Feature("rip-rap round the Bastion", 50, "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:andesite", "minecraft:stone"),
				new Feature("the Gallery's iron rail", 30, "minecraft:iron_bars"),
				new Feature("the winding stair", 30, "minecraft:spruce_stairs"),
				new Feature("the Cottage's slate roof", 40, "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tiles"),
				new Feature("the keeper's bed in the Watch room", 2, "minecraft:red_bed", "minecraft:white_bed", "minecraft:blue_bed", "minecraft:brown_bed"),
				new Feature("a barrel for a fisherman on the Fish Stage", 1, "minecraft:barrel"),
				new Feature("the Fish Stage's drying rack", 1, "minecraft:dried_kelp_block"),
				new Feature("a Travel Post at the Ferry Jetty's end", 1, "aliveworkplace:travel_post"),
				new Feature("the Jetty, the Landing and the Fish Stage of spruce", 50, "minecraft:spruce_log", "minecraft:spruce_planks", "minecraft:spruce_fence"));
			case "great_library" -> List.of( // 52.6: the rebuilt Great Library's named parts (js/great_library/plan.mjs, "the Scroll Rotunda")
				new Feature("the enchanting table at the Rotunda's heart", 1, "minecraft:enchanting_table"),
				new Feature("a lectern at the Rotunda's center and one in each wing's stacks", 5, "minecraft:lectern"),
				new Feature("the Map Room's cartography table", 1, "minecraft:cartography_table"),
				new Feature("the Rotunda's wall of books and the Stacks' and Galleries' shelves", 1500, "minecraft:bookshelf"),
				new Feature("the ribbed copper dome and the wings' copper roofs", 1000, "minecraft:copper_block", "minecraft:cut_copper", "minecraft:cut_copper_stairs"),
				new Feature("the quartz portico", 100, "minecraft:quartz_block", "minecraft:quartz_pillar", "minecraft:chiseled_quartz_block", "minecraft:smooth_quartz"),
				new Feature("the portico's six quartz columns", 30, "minecraft:quartz_pillar"),
				new Feature("pale walls of polished diorite and smooth stone", 4000, "minecraft:polished_diorite", "minecraft:smooth_stone"),
				new Feature("the stone-brick podium and its steps", 1000, "minecraft:stone_bricks", "minecraft:stone_brick_stairs"),
				new Feature("dark oak floors", 400, "minecraft:dark_oak_planks"),
				new Feature("the helix walk of dark oak slabs", 100, "minecraft:dark_oak_slab"),
				new Feature("the drum's spiral of glass and the dome's lantern", 200, "minecraft:glass"),
				new Feature("a tree in each corner garden", 40, "minecraft:oak_leaves"));
			case "great_wall" -> List.of( // 52.6: the rebuilt Great Wall's named parts (js/great_wall/plan.mjs, "the Pass Gate")
				new Feature("the Great Gate's portcullis and the towers' barred loops", 6, "minecraft:iron_bars"),
				new Feature("the Great Gate's inner leaves", 2, "minecraft:spruce_fence_gate", "minecraft:oak_fence_gate"),
				new Feature("the Barbican's outer gate", 2, "minecraft:dark_oak_fence_gate"),
				new Feature("a signal fire on each tower's Crown", 4, "minecraft:campfire"),
				new Feature("a grindstone in each tower's guard room", 4, "minecraft:grindstone"),
				new Feature("banners on the towers and the gate", 4, "minecraft:blue_wall_banner", "minecraft:red_wall_banner", "minecraft:white_wall_banner"),
				new Feature("the curtain's dark battered foot", 100, "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:deepslate_brick_stairs"),
				new Feature("aged stone on the curtain and towers", 50, "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_cobblestone"),
				new Feature("the towers' spruce hip caps", 100, "minecraft:spruce_stairs"),
				new Feature("the Gate Pavilion's tile roofs", 100, "minecraft:deepslate_tile_stairs"),
				new Feature("a ladder up each guard room to its Crown", 4, "minecraft:ladder"),
				new Feature("the Horse Stairs up the village face", 1, "minecraft:stone_brick_stairs", "minecraft:spruce_stairs", "minecraft:cobblestone_stairs"));
			case "observatory" -> List.of( // 52.6: the rebuilt Observatory's named parts (js/observatory/plan.mjs)
				new Feature("the dome's tinted shutter and lens", 20, "minecraft:tinted_glass"),
				new Feature("a dome and telescope of cut copper", 300, "minecraft:cut_copper"),
				new Feature("the Chart Hall's cartography table", 1, "minecraft:cartography_table"),
				new Feature("lecterns in the Chart Hall and the Observing Room", 3, "minecraft:lectern"),
				new Feature("gold noon marks and telescope bands", 12, "minecraft:gold_block"),
				new Feature("chandeliers hung on chains", 6, "minecraft:chain"),
				new Feature("an amethyst star in the Chart Hall's floor", 8, "minecraft:amethyst_block"),
				new Feature("deepslate bricks and tiles", 2000, "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:deepslate_tiles"),
				new Feature("polished blackstone plinths and courses", 300, "minecraft:polished_blackstone", "minecraft:polished_blackstone_bricks"),
				new Feature("the quadrants' smooth stone scales", 30, "minecraft:smooth_stone_slab", "minecraft:smooth_stone"),
				new Feature("the drum's glazed windows", 100, "minecraft:glass", "minecraft:glass_pane"),
				new Feature("the Chart Hall's books", 20, "minecraft:bookshelf"));
			case "hanging_gardens" -> List.of( // 52.6: the rebuilt Gardens' named parts (js/hanging_gardens/plan.mjs)
				new Feature("a chest at each terrace's farm corner", 4, "minecraft:chest"),
				new Feature("a composter at each terrace's farm corner", 4, "minecraft:composter"),
				new Feature("wheat", 1, "minecraft:wheat"), new Feature("carrots", 1, "minecraft:carrots"),
				new Feature("beetroots", 1, "minecraft:beetroots"),
				new Feature("the Shaft, the Cistern, the pools and the fields' water", 20, "minecraft:water"),
				new Feature("lily pads on the Reflecting Pools", 1, "minecraft:lily_pad"),
				new Feature("glow berries on the Pergola", 1, "minecraft:cave_vines", "minecraft:cave_vines_plant"),
				new Feature("the Hanging Curtains' vines", 100, "minecraft:vine"),
				new Feature("the Hanging Curtains' leaves", 200, "minecraft:azalea_leaves", "minecraft:flowering_azalea_leaves", "minecraft:oak_leaves"),
				new Feature("flowering azaleas", 1, "minecraft:flowering_azalea", "minecraft:flowering_azalea_leaves"),
				new Feature("palms of jungle wood with dates", 20, "minecraft:jungle_log"), new Feature("the palms' dates", 1, "minecraft:cocoa"),
				new Feature("the Cypress Walk", 10, "minecraft:spruce_log"),
				new Feature("the Gallery's orangery of potted azaleas", 20, "minecraft:potted_azalea_bush", "minecraft:potted_flowering_azalea_bush"),
				new Feature("the Vaults' nursery of potted saplings", 20, "minecraft:potted_oak_sapling", "minecraft:potted_birch_sapling",
					"minecraft:potted_jungle_sapling", "minecraft:potted_acacia_sapling", "minecraft:potted_cherry_sapling"),
				new Feature("the Vaults' seed store of hay", 10, "minecraft:hay_block"),
				new Feature("lanterns hung in the halls and on the piers", 30, "minecraft:lantern"),
				new Feature("the arcades' glazed windows", 50, "minecraft:glass_pane"),
				new Feature("terrace walls of mud brick", 3000, "minecraft:mud_bricks", "minecraft:packed_mud"),
				new Feature("a baked-brick plinth", 300, "minecraft:bricks", "minecraft:brick_stairs"),
				new Feature("sandstone dressings, floors and parapets", 1000, "minecraft:smooth_sandstone", "minecraft:cut_sandstone", "minecraft:chiseled_sandstone"),
				new Feature("the frieze of blue glazed tiles under every cornice", 200, "minecraft:blue_glazed_terracotta"));
			case "colossus" -> List.of(new Feature("a lantern of glowstone", 1, "minecraft:glowstone"),
				new Feature("the lantern's glass cage", 4, "minecraft:glass_pane", "minecraft:glass"),
				new Feature("braziers round the plinth", 4, "minecraft:campfire"),
				new Feature("the Founder's plaque, a row of signs", 3, "minecraft:dark_oak_wall_sign", "minecraft:oak_wall_sign", "minecraft:spruce_wall_sign"),
				new Feature("a figure of polished diorite and smooth stone", 500, "minecraft:polished_diorite", "minecraft:smooth_stone"),
				new Feature("a cloak of cut copper", 150, "minecraft:cut_copper", "minecraft:cut_copper_stairs", "minecraft:cut_copper_slab"));
			case "stadium" -> List.of( // 52.6: the rebuilt Stadium's named parts (js/stadium/plan.mjs, "the Stadium Bowl")
				new Feature("two Training Posts", 2, "aliveworkplace:training_post"),
				new Feature("the Nurse's Booth's brewing stand", 1, "minecraft:brewing_stand"),
				new Feature("four floodlights' daylight detectors", 4, "minecraft:daylight_detector"),
				new Feature("the floodlights' redstone lamps", 32, "minecraft:redstone_lamp"),
				new Feature("the floodlight masts' iron", 40, "minecraft:iron_block", "minecraft:iron_bars"),
				new Feature("red seats", 300, "minecraft:red_carpet", "minecraft:red_wool", "minecraft:red_terracotta"),
				new Feature("white seats", 300, "minecraft:white_carpet", "minecraft:white_wool", "minecraft:white_terracotta"),
				new Feature("banners on the Gate's pylons", 4, "minecraft:red_banner", "minecraft:red_wall_banner", "minecraft:white_wall_banner"),
				new Feature("a grass field", 400, "minecraft:grass_block"),
				new Feature("the field's chalk lines", 150, "minecraft:white_concrete"),
				new Feature("the pitch wall of polished blackstone", 200, "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_brick_slab"),
				new Feature("the Outer Wall's brick", 2500, "minecraft:bricks"),
				new Feature("quartz pilasters, piers and dressings", 700, "minecraft:quartz_pillar", "minecraft:smooth_quartz", "minecraft:chiseled_quartz_block"),
				new Feature("the Covered Stand's and the pavilions' copper roofs", 400, "minecraft:waxed_oxidized_cut_copper",
					"minecraft:waxed_oxidized_cut_copper_slab", "minecraft:waxed_oxidized_cut_copper_stairs"),
				new Feature("the Scoreboard's black faces", 60, "minecraft:black_concrete"),
				new Feature("the Concourse's hanging lanterns", 17, "minecraft:lantern"),
				new Feature("doors into the Concourse and the pavilions", 20, "minecraft:spruce_door"),
				new Feature("the Outer Wall's windows", 60, "minecraft:glass_pane"),
				new Feature("the red crosses of the Nurse's Booth", 10, "minecraft:red_concrete"),
				new Feature("the Refreshment Room's cakes", 2, "minecraft:cake"),
				new Feature("the changing rooms' lockers", 16, "minecraft:spruce_trapdoor"),
				new Feature("spruce benches", 20, "minecraft:spruce_slab", "minecraft:spruce_log", "minecraft:spruce_planks"));
			default -> throw new IllegalStateException(id);
		};
	}

	static void features(GameTestHelper helper, String id) {
		Map<BlockPos, BlockState> all = whole(helper, id);
		List<String> wrong = new ArrayList<>();
		for (Feature f : features(id)) {
			int n = count(all, is(f.ids()));
			if (n < f.least()) {
				wrong.add(f.what() + ": " + n + " of " + String.join("/", f.ids()) + ", fewer than " + f.least());
			}
		}
		helper.assertTrue(wrong.isEmpty(), id + " is missing what its spec names: " + wrong);
	}

	// --- nothing floating -----------------------------------------------------------------------------------------------

	/**
	 * After every stage, every block joins the ground (y 0) through blocks touching it, edges and corners counted (a
	 * roof's stairs step up corner to corner): no piece of a stage hangs in the air until a later stage holds it up.
	 */
	static void grounded(GameTestHelper helper, String id) {
		List<Map<BlockPos, BlockState>> after = standing(stages(helper, id));
		List<String> wrong = new ArrayList<>();
		for (int n = 0; n < after.size(); n++) {
			Map<BlockPos, BlockState> now = after.get(n);
			Set<BlockPos> seen = new HashSet<>();
			ArrayDeque<BlockPos> todo = new ArrayDeque<>();
			now.keySet().stream().filter(p -> p.getY() == 0).forEach(p -> {
				seen.add(p);
				todo.add(p);
			});
			while (!todo.isEmpty()) {
				BlockPos p = todo.poll();
				for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
					if (now.containsKey(q) && !seen.contains(q)) {
						BlockPos k = q.immutable();
						seen.add(k);
						todo.add(k);
					}
				}
			}
			List<BlockPos> loose = now.keySet().stream().filter(p -> !seen.contains(p)).sorted().toList();
			if (!loose.isEmpty()) {
				wrong.add("after stage " + (n + 1) + ", " + loose.size() + " blocks hang in the air, e.g. "
					+ loose.stream().limit(3).map(p -> name(now.get(p)) + " at " + p.toShortString()).toList());
			}
		}
		helper.assertTrue(wrong.isEmpty(), id + ": " + wrong);
	}

	/** Blocks placed for real far over the test, without updates to their neighbours, and taken away again. */
	static final class Sky implements AutoCloseable {
		static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		final ServerLevel level;
		final BlockPos base;
		final Set<BlockPos> touched = new HashSet<>();
		final int[] box;

		/** Over the test, with stone a block under the whole Wonder's footprint (the ground it is built on). */
		Sky(GameTestHelper helper, Map<BlockPos, BlockState> footprint) {
			level = helper.getLevel();
			BlockPos corner = helper.absolutePos(BlockPos.ZERO);
			base = new BlockPos(corner.getX(), level.getMaxBuildHeight() - 64, corner.getZ());
			box = box(footprint, p -> true);
			// B559: an empty-structure test loads only the chunks round its own small area, and a Wonder is far wider. A
			// block placed in an unloaded chunk loads that chunk alone, so the Stadium's carved pumpkin (its golem check
			// reads the blocks round it) read a neighbour in a chunk not loaded and threw, once the batch layout put the
			// test by a chunk edge. Load every chunk the Wonder (and a margin) stands in first.
			for (int cx = (base.getX() + box[0] - 3) >> 4; cx <= (base.getX() + box[3] + 3) >> 4; cx++) {
				for (int cz = (base.getZ() + box[2] - 3) >> 4; cz <= (base.getZ() + box[5] + 3) >> 4; cz++) {
					level.getChunk(cx, cz);
				}
			}
			for (int x = box[0]; x <= box[3]; x++) {
				for (int z = box[2]; z <= box[5]; z++) {
					set(new BlockPos(x, -1, z), Blocks.STONE.defaultBlockState());
				}
			}
		}

		void set(BlockPos rel, BlockState state) {
			BlockPos at = base.offset(rel);
			level.setBlock(at, state, FLAGS);
			touched.add(at.immutable());
		}

		BlockPos at(BlockPos rel) {
			return base.offset(rel);
		}

		BlockState get(BlockPos rel) {
			return level.getBlockState(base.offset(rel));
		}

		@Override
		public void close() {
			for (BlockPos p : touched) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
			}
			AABB area = new AABB(base.getX() + box[0] - 2, base.getY() - 3, base.getZ() + box[2] - 2, base.getX() + box[3] + 3,
				base.getY() + box[4] + 3, base.getZ() + box[5] + 3);
			level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
		}
	}

	/**
	 * Built for real stage by stage (on stone, as on a cleared site), every block of every stage so far can stay where it
	 * is by the game's own rules (plants on soil, lanterns and bells hung from something, banners and ladders on a wall,
	 * crops on farmland, lily pads on water), and nothing that falls (sand, gravel, concrete powder) stands over air.
	 */
	static void stand(GameTestHelper helper, String id) {
		List<Map<BlockPos, BlockState>> after = standing(stages(helper, id));
		List<String> wrong = new ArrayList<>();
		try (Sky sky = new Sky(helper, after.get(after.size() - 1))) {
			Map<BlockPos, BlockState> placed = new HashMap<>();
			for (int n = 0; n < after.size(); n++) {
				Map<BlockPos, BlockState> now = after.get(n);
				for (BlockPos p : placed.keySet()) {
					if (!now.containsKey(p)) {
						sky.set(p, Blocks.AIR.defaultBlockState());
					}
				}
				now.forEach(sky::set);
				placed = now;
				Map<String, List<String>> bad = new TreeMap<>();
				for (Map.Entry<BlockPos, BlockState> e : now.entrySet()) {
					BlockState state = e.getValue();
					BlockPos at = sky.at(e.getKey());
					boolean falls = state.getBlock() instanceof FallingBlock && FallingBlock.isFree(sky.get(e.getKey().below()));
					if (falls || !state.canSurvive(sky.level, at)) {
						List<String> list = bad.computeIfAbsent(name(state), k -> new ArrayList<>());
						list.add(e.getKey().toShortString());
					}
				}
				final int stage = n + 1;
				bad.forEach((block, where) -> wrong.add("stage " + stage + ": " + where.size() + " " + block + " can't stay, e.g. at "
					+ where.subList(0, Math.min(3, where.size()))));
			}
		}
		helper.assertTrue(wrong.isEmpty(), id + ": " + wrong);
	}

	// --- doorways, doors and beds ---------------------------------------------------------------------------------------

	/** Nothing a villager walks into: no collision, or a carpet's or pressure plate's sliver underfoot. */
	static boolean clear(Sky sky, BlockPos rel, boolean feet) {
		BlockState s = sky.get(rel);
		if (s.getBlock() instanceof DoorBlock) {
			return true;
		}
		VoxelShape shape = s.getCollisionShape(sky.level, sky.at(rel));
		return shape.isEmpty() || feet && shape.max(Direction.Axis.Y) <= 0.0625 + 1e-6;
	}

	/**
	 * Every door is whole (its upper half over it, the same door facing the same way) and has two clear blocks on both
	 * of its sides, so a villager can walk through it.
	 */
	static void doorways(GameTestHelper helper, String id) {
		Map<BlockPos, BlockState> all = whole(helper, id);
		List<String> wrong = new ArrayList<>();
		int doors = 0;
		try (Sky sky = new Sky(helper, all)) {
			all.forEach(sky::set);
			for (Map.Entry<BlockPos, BlockState> e : all.entrySet()) {
				BlockState s = e.getValue();
				if (!(s.getBlock() instanceof DoorBlock) || s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
					continue;
				}
				doors++;
				BlockPos p = e.getKey();
				BlockState up = all.get(p.above());
				if (up == null || up.getBlock() != s.getBlock() || up.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
					|| up.getValue(DoorBlock.FACING) != s.getValue(DoorBlock.FACING)) {
					wrong.add(name(s) + " at " + p.toShortString() + " has no upper half");
				}
				Direction facing = s.getValue(DoorBlock.FACING);
				for (Direction side : List.of(facing, facing.getOpposite())) {
					BlockPos q = p.relative(side);
					if (!clear(sky, q, true) || !clear(sky, q.above(), false)) {
						wrong.add(name(s) + " at " + p.toShortString() + ": " + name(sky.get(q)) + " / " + name(sky.get(q.above())) + " on its "
							+ side.getName() + " side");
					}
				}
			}
		}
		for (Map.Entry<BlockPos, BlockState> e : all.entrySet()) {
			BlockState s = e.getValue();
			if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
				BlockState low = all.get(e.getKey().below());
				if (low == null || low.getBlock() != s.getBlock()) {
					wrong.add(name(s) + " at " + e.getKey().toShortString() + " has no lower half");
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), id + "'s doorways (" + doors + " doors): " + wrong);
	}

	/** Every bed's foot has its head where it points, the same bed facing the same way, and every head its foot. */
	static List<String> brokenBeds(Map<BlockPos, BlockState> all) {
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : all.entrySet()) {
			BlockState s = e.getValue();
			if (!(s.getBlock() instanceof BedBlock)) {
				continue;
			}
			Direction facing = s.getValue(BedBlock.FACING);
			boolean foot = s.getValue(BedBlock.PART) == BedPart.FOOT;
			BlockPos other = foot ? e.getKey().relative(facing) : e.getKey().relative(facing.getOpposite());
			BlockState o = all.get(other);
			if (o == null || o.getBlock() != s.getBlock() || o.getValue(BedBlock.FACING) != facing || o.getValue(BedBlock.PART) == s.getValue(BedBlock.PART)) {
				wrong.add(name(s) + "'s " + (foot ? "foot" : "head") + " at " + e.getKey().toShortString() + " facing " + facing.getName() + " has no "
					+ (foot ? "head" : "foot") + " at " + other.toShortString() + " (" + (o == null ? "air" : name(o)) + " there)");
			}
		}
		return wrong;
	}

	// --- nothing for free -----------------------------------------------------------------------------------------------

	/**
	 * The builder copies no container's contents, and the blueprint has none to copy: no block's saved data holds items,
	 * no lectern a book, no chiseled bookshelf a book, no hive honey, no composter compost, no brewing stand bottles.
	 */
	static void nothingFree(GameTestHelper helper, String id) {
		List<String> wrong = new ArrayList<>();
		for (List<Part> stage : stages(helper, id)) {
			for (Part p : stage) {
				for (Blueprint.Entry e : p.blueprint().blocks()) {
					BlockState s = e.state();
					CompoundTag tag = e.nbt();
					String at = p.id().getPath() + " " + e.pos().toShortString();
					if (tag != null) {
						for (String key : List.of("Items", "Item", "Book", "RecordItem", "item", "bees", "honey_level")) {
							if (tag.contains(key) && !(tag.get(key) instanceof net.minecraft.nbt.ListTag list && list.isEmpty())) {
								wrong.add(name(s) + " at " + at + " holds " + key);
							}
						}
					}
					if (s.hasProperty(BlockStateProperties.HAS_BOOK) && s.getValue(BlockStateProperties.HAS_BOOK)) {
						wrong.add("a lectern with a book at " + at);
					}
					if (s.hasProperty(BlockStateProperties.LEVEL_HONEY) && s.getValue(BlockStateProperties.LEVEL_HONEY) > 0) {
						wrong.add("a hive with honey at " + at);
					}
					if (s.hasProperty(BlockStateProperties.LEVEL_COMPOSTER) && s.getValue(BlockStateProperties.LEVEL_COMPOSTER) > 0) {
						wrong.add("a composter with compost at " + at);
					}
					for (var prop : List.of(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED, BlockStateProperties.CHISELED_BOOKSHELF_SLOT_1_OCCUPIED,
						BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED, BlockStateProperties.CHISELED_BOOKSHELF_SLOT_3_OCCUPIED,
						BlockStateProperties.CHISELED_BOOKSHELF_SLOT_4_OCCUPIED, BlockStateProperties.CHISELED_BOOKSHELF_SLOT_5_OCCUPIED,
						BlockStateProperties.HAS_BOTTLE_0, BlockStateProperties.HAS_BOTTLE_1, BlockStateProperties.HAS_BOTTLE_2)) {
						if (s.hasProperty(prop) && s.getValue(prop)) {
							wrong.add(name(s) + " shows a " + prop.getName() + " at " + at);
						}
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), id + " gives things away: " + wrong);
	}

	// --- night lights and the enchanting table --------------------------------------------------------------------------

	/**
	 * Built for real, with every daylight detector inverted (it lights at night) and switched to night's full signal, at
	 * least {@code lamps} redstone lamps light and none stays dark.
	 */
	static void lampsLight(GameTestHelper helper, String id, int detectors, int lamps) {
		Map<BlockPos, BlockState> all = whole(helper, id);
		List<BlockPos> found = all.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof DaylightDetectorBlock).map(Map.Entry::getKey).toList();
		helper.assertTrue(found.size() >= detectors, id + " has " + found.size() + " daylight detectors, not " + detectors);
		for (BlockPos p : found) {
			helper.assertTrue(all.get(p).getValue(DaylightDetectorBlock.INVERTED), id + "'s daylight detector at " + p.toShortString()
				+ " isn't inverted: it would light its lamps by day, not by night");
		}
		List<BlockPos> lampsAt = all.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof RedstoneLampBlock).map(Map.Entry::getKey).toList();
		helper.assertTrue(lampsAt.size() >= lamps, id + " has " + lampsAt.size() + " redstone lamps");
		List<String> dark = new ArrayList<>();
		try (Sky sky = new Sky(helper, all)) {
			all.forEach(sky::set);
			for (BlockPos p : lampsAt) {
				helper.assertTrue(!sky.get(p).getValue(RedstoneLampBlock.LIT), id + "'s lamp at " + p.toShortString() + " is lit before night");
			}
			for (BlockPos p : found) {
				sky.level.setBlock(sky.at(p), sky.get(p).setValue(DaylightDetectorBlock.POWER, 15), Block.UPDATE_ALL);
			}
			for (BlockPos p : lampsAt) {
				if (!(sky.get(p).getBlock() instanceof RedstoneLampBlock) || !sky.get(p).getValue(RedstoneLampBlock.LIT)) {
					dark.add(p.toShortString());
				}
			}
		}
		helper.assertTrue(dark.isEmpty(), id + ": " + dark.size() + " of " + lampsAt.size() + " lamps stay dark at night: " + dark);
	}

	/** "An Enchanting Table ringed by fifteen bookshelves": built for real, the table counts fifteen (level 30 enchanting). */
	static void enchantingPower(GameTestHelper helper, String id) {
		Map<BlockPos, BlockState> all = whole(helper, id);
		List<BlockPos> tables = all.entrySet().stream().filter(e -> e.getValue().is(Blocks.ENCHANTING_TABLE)).map(Map.Entry::getKey).toList();
		helper.assertTrue(tables.size() == 1, id + " has " + tables.size() + " enchanting tables");
		long shelves;
		try (Sky sky = new Sky(helper, all)) {
			all.forEach(sky::set);
			BlockPos table = sky.at(tables.get(0));
			shelves = EnchantingTableBlock.BOOKSHELF_OFFSETS.stream().filter(o -> EnchantingTableBlock.isValidBookShelf(sky.level, table, o)).count();
		}
		helper.assertTrue(shelves >= 15, id + "'s enchanting table at " + tables.get(0).toShortString() + " counts " + shelves + " bookshelves, not 15");
	}

	// --- names ----------------------------------------------------------------------------------------------------------

	/** Each Wonder's name and its stages' titles, as the roadmap writes them. */
	static final Map<String, List<String>> TITLES = new LinkedHashMap<>();

	static {
		TITLES.put("great_cathedral", List.of("Great Cathedral", "The Foundations and the West Front", "The Nave", "The Transepts and the Choir",
			"The Towers and the Rose Window"));
		TITLES.put("lighthouse", List.of("Lighthouse", "The Quay, the Mole and the Bastion", "The Keeper's Cottage and the Ferry Landing", "The Shaft and the Gallery",
			"The Lantern and the Hood"));
		TITLES.put("great_library", List.of("Great Library", "The Stacks", "The Galleries", "The Rotunda", "The Dome and the Portico"));
		TITLES.put("great_wall", List.of("Great Wall", "The Great Gate", "The West Run", "The East Run", "The Walk and the Towers' Crowns"));
		TITLES.put("observatory", List.of("Observatory", "The Star Terrace and the Chart Hall", "The Great Gnomon and the Quadrants", "The Drum and the Observing Room", "The Copper Dome and the Great Telescope"));
		TITLES.put("hanging_gardens", List.of("Hanging Gardens", "The Vaults and the Lower Terrace", "The Gallery and the Middle Terrace",
			"The Upper Terrace and its Hall", "The Crown Garden and the Water Pavilion")); // 52.6: the rebuilt stages
		TITLES.put("colossus", List.of("Colossus", "The Plinth and the Road Arch", "The Robes", "The Arms and the Cloak", "The Head and the Lantern"));
		// 35.23: "Grand Stadium" only if M28 shipped a smaller Stadium; it shipped the Arena (ROADMAP Notes), so "Stadium"
		TITLES.put("stadium", List.of("Stadium", "The Field", "The Lower Stands", "The Upper Stands and the Covered Stand", "The Gate and the Floodlights"));
	}

	/** British spellings the owner asked to keep out of player text (2026-10-26). */
	static final Pattern BRITISH = Pattern.compile("(?i)\\b(colour\\w*|centre\\w*|defence|armour\\w*|jeweller\\w*|cancelled|grey\\w*|theatre|harbour"
		+ "|favour\\w*|honour\\w*|metres?|organis\\w*|neighbour\\w*|travell\\w*|labour|plough\\w*|storeys?|sombre|lustre|mould\\w*|whilst|amongst)\\b");

	static void names(GameTestHelper helper, String id) {
		var file = WonderBlueprintGameTests.file(helper.getLevel().getServer(), id);
		Language lang = Language.getInstance();
		List<String> wrong = new ArrayList<>();
		List<String> keys = new ArrayList<>(List.of(file.get("name").getAsString()));
		file.getAsJsonArray("stages").forEach(s -> keys.add(s.getAsJsonObject().get("name").getAsString()));
		List<String> titles = TITLES.get(id);
		helper.assertTrue(keys.size() == titles.size(), id + " has " + (keys.size() - 1) + " stages, the spec " + (titles.size() - 1));
		for (int i = 0; i < keys.size(); i++) {
			String text = lang.getOrDefault(keys.get(i));
			if (!text.equals(titles.get(i))) {
				wrong.add(keys.get(i) + " reads \"" + text + "\", not \"" + titles.get(i) + "\"");
			}
		}
		String description = lang.getOrDefault(file.get("description").getAsString());
		helper.assertTrue(!description.equals(file.get("description").getAsString()), id + " has no description line");
		for (String text : List.of(description, String.join(" ", keys.stream().map(lang::getOrDefault).toList()))) {
			Matcher m = BRITISH.matcher(text);
			while (m.find()) {
				wrong.add("British \"" + m.group() + "\" in \"" + text + "\"");
			}
			if (text.contains("%") || text.contains("  ") || !text.equals(text.strip())) {
				wrong.add("stray formatting in \"" + text + "\"");
			}
		}
		helper.assertTrue(description.endsWith("."), id + "'s description doesn't end a sentence: " + description);
		helper.assertTrue(wrong.isEmpty(), id + ": " + wrong);
	}
}
