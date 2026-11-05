package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaWonderDesignsGameTests.box;
import static io.github.jcondedata.aliveworkplace.test.QaWonderDesignsGameTests.boxOf;
import static io.github.jcondedata.aliveworkplace.test.QaWonderDesignsGameTests.count;
import static io.github.jcondedata.aliveworkplace.test.QaWonderDesignsGameTests.is;
import static io.github.jcondedata.aliveworkplace.test.QaWonderDesignsGameTests.name;
import static io.github.jcondedata.aliveworkplace.test.QaWonderDesignsGameTests.whole;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * QA (lane-b-1105-0038) of the Wonder designs 35.19 (Hanging Gardens), 35.21 (Colossus), 35.23 and 35.23a (Stadium),
 * from their specs and the wiki's sentences, on the whole Wonder as its stages leave it (no builder: they are huge).
 * Beyond {@link QaWonderDesignsGameTests} and {@link WonderBlueprintGameTests}: a player walking in from outside reaches
 * every terrace's farm and the crown of the Gardens, and the Stadium's field, stands, boxes and covered concourse
 * (35.23a's interiors); the Gardens' water runs from the crown to the foot; the Colossus's copper can weather (none
 * waxed), its nose stands out past its eyes, its lantern is a lit cage raised high; the wiki's numbers; and every
 * sentence a player reads about them is in American English.
 */
public class QaWonderDesigns1105GameTests implements FabricGameTest {
	static final String BATCH = "qa_wonder_designs_1105";

	// --- walking the design ---------------------------------------------------------------------------------------------

	/** A block a player can stand on (the ground under the site counts: y -1 outside the blueprint's blocks). */
	static boolean support(Map<BlockPos, BlockState> all, BlockPos p) {
		BlockState s = all.get(p);
		if (s == null) {
			return p.getY() < 0;
		}
		VoxelShape shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 1.0;
	}

	/** A cell a player's body passes through (doors and gates open; a carpet is walked on). */
	static boolean open(Map<BlockPos, BlockState> all, BlockPos p) {
		BlockState s = all.get(p);
		if (s == null) {
			return p.getY() >= 0;
		}
		if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock || s.getBlock() instanceof TrapDoorBlock) {
			return true;
		}
		if (name(s).equals("minecraft:lava")) {
			return false;
		}
		VoxelShape shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.1875;
	}

	static boolean standable(Map<BlockPos, BlockState> all, BlockPos p) {
		return support(all, p.below()) && open(all, p) && open(all, p.above());
	}

	/** Every cell a player can stand in, walking in from the ground round the site (steps of one, drops of three). */
	static Set<BlockPos> walk(Map<BlockPos, BlockState> all) {
		int[] b = box(all, p -> true);
		int x0 = b[0] - 2;
		int z0 = b[2] - 2;
		int x1 = b[3] + 2;
		int z1 = b[5] + 2;
		int y1 = b[4] + 2;
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x == x0 || x == x1 || z == z0 || z == z1) {
					BlockPos p = new BlockPos(x, 0, z);
					if (standable(all, p) && seen.add(p)) {
						todo.add(p);
					}
				}
			}
		}
		while (!todo.isEmpty()) {
			BlockPos at = todo.poll();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = at.relative(d);
				if (side.getX() < x0 || side.getX() > x1 || side.getZ() < z0 || side.getZ() > z1) {
					continue;
				}
				for (int dy = 1; dy >= -3; dy--) {
					BlockPos to = side.above(dy);
					if (to.getY() < 0 || to.getY() > y1) {
						continue;
					}
					// a jump up onto a full block needs headroom; a stair or a slab is walked up
					BlockState under = all.get(to.below());
					boolean step = under != null && (under.getBlock() instanceof StairBlock || under.getBlock() instanceof SlabBlock);
					if (dy == 1 && !step && !open(all, at.above(2))) {
						continue;
					}
					if (dy < 0) {
						// dropping: the cells passed on the way down must be open
						boolean clear = open(all, side) && open(all, side.above());
						for (int k = -1; k > dy && clear; k--) {
							clear = open(all, side.above(k));
						}
						if (!clear) {
							break;
						}
					}
					if (standable(all, to)) {
						if (seen.add(to)) {
							todo.add(to);
						}
						break;
					}
				}
			}
		}
		return seen;
	}

	/** Whether a walked cell is beside {@code target} (within 2 across, its own level or one off). */
	static boolean reaches(Set<BlockPos> walked, BlockPos target) {
		for (BlockPos p : walked) {
			if (Math.abs(p.getX() - target.getX()) <= 2 && Math.abs(p.getZ() - target.getZ()) <= 2 && Math.abs(p.getY() - target.getY()) <= 1) {
				return true;
			}
		}
		return false;
	}

	static List<BlockPos> where(Map<BlockPos, BlockState> all, String... ids) {
		List<BlockPos> out = new ArrayList<>();
		all.forEach((p, s) -> {
			if (is(ids).test(s)) {
				out.add(p);
			}
		});
		return out;
	}

	// --- the Hanging Gardens (35.19) ------------------------------------------------------------------------------------

	/** "a chest on each level (four village farms) ... a ramp spirals up": a player from the ground reaches all four and the crown. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE', batch = BATCH, timeoutTicks = 400
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 400)
	public void qaGardensRampReachesEveryFarmAndTheCrown(GameTestHelper helper) {
		Map<BlockPos, BlockState> all = whole(helper, "hanging_gardens");
		Set<BlockPos> walked = walk(all);
		List<BlockPos> chests = where(all, "minecraft:chest");
		List<String> wrong = new ArrayList<>();
		for (BlockPos c : chests) {
			if (!reaches(walked, c)) {
				wrong.add("the farm chest at " + c.toShortString());
			}
		}
		int top = walked.stream().mapToInt(BlockPos::getY).max().orElse(0);
		helper.assertTrue(chests.size() >= 4, "only " + chests.size() + " chests");
		helper.assertTrue(wrong.isEmpty(), "no walk from the ground to " + wrong);
		helper.assertTrue(top >= 24, "a player walks up to y " + top + " only, not the crown garden (the fourth terrace tops out at y 25)");
		helper.succeed();
	}

	/** "A cistern at the top pours a waterfall down one side into a pool": water on the crown, on every ledge between, and at the foot. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE', batch = BATCH, timeoutTicks = 400
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 400)
	public void qaGardensFallsRunFromTheCisternToThePool(GameTestHelper helper) {
		Map<BlockPos, BlockState> all = whole(helper, "hanging_gardens");
		List<BlockPos> water = where(all, "minecraft:water");
		int[] b = box(all, p -> true);
		int cx = (b[0] + b[3]) / 2;
		int cz = (b[2] + b[5]) / 2;
		boolean crown = water.stream().anyMatch(p -> p.getY() >= 24);
		// the pool at the foot: water low down, outside the first terrace's inner hall (within 4 of the edge)
		boolean pool = water.stream().anyMatch(p -> p.getY() <= 2
			&& (p.getX() - b[0] <= 4 || b[3] - p.getX() <= 4 || p.getZ() - b[2] <= 4 || b[5] - p.getZ() <= 4));
		helper.assertTrue(crown, "no cistern water on the crown (y 24 or more)");
		helper.assertTrue(pool, "no pool at the foot of the falls");
		// on one side: the falls' water on each ledge lies on the same side of the mountain
		List<String> ledges = new ArrayList<>();
		for (int k = 1; k <= 3; k++) {
			final int y = 6 * k + 1;
			List<BlockPos> ledge = water.stream().filter(p -> p.getY() >= y - 1 && p.getY() <= y + 1).toList();
			if (ledge.isEmpty()) {
				ledges.add("no water on ledge " + k + " (y " + y + ")");
			}
		}
		helper.assertTrue(ledges.isEmpty(), "the falls don't step down the ledges: " + ledges + " (center " + cx + ", " + cz + ")");
		helper.succeed();
	}

	/** The wiki: "on a 39 by 39 foot" (the first terrace; the whole site with the ramp's foot and the pool is 47 by 43), "31 high to its treetops". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE', batch = BATCH, timeoutTicks = 400
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 400)
	public void qaGardensFootAndHeightAsTheWikiSays(GameTestHelper helper) {
		Map<BlockPos, BlockState> all = whole(helper, "hanging_gardens");
		int[] b = box(all, p -> true);
		int w = b[3] - b[0] + 1;
		int l = b[5] - b[2] + 1;
		int h = b[4] + 1;
		int[] first = box(all, p -> p.getY() == 3 && name(all.get(p)).contains("sandstone") && !name(all.get(p)).contains("stairs"));
		int fw = Math.min(first[3] - first[0], first[5] - first[2]) + 1; // the ramp's balustrade runs out along the front
		helper.assertTrue(Math.abs(fw - 39) <= 1, "the first terrace is " + fw + " across, not 39");
		helper.assertTrue(Math.abs(w - 47) <= 1 && Math.abs(l - 43) <= 1 && Math.abs(h - 31) <= 1,
			"the Gardens' site is " + w + " x " + l + " and " + h + " high, not 47 x 43 and 31 high");
		helper.succeed();
	}

	// --- the Colossus (35.21) -------------------------------------------------------------------------------------------

	static final Pattern COPPER = Pattern.compile("minecraft:(waxed_)?((exposed|weathered|oxidized)_)?(cut_copper(_stairs|_slab)?|copper_block|chiseled_copper|copper_grate)");

	/** "a cloak of cut copper that weathers green over the weeks (about 250 copper blocks)" (wiki: about 370 with the lantern's). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE', batch = BATCH, timeoutTicks = 400
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 400)
	public void qaColossusCopperWeathers(GameTestHelper helper) {
		Map<BlockPos, BlockState> all = whole(helper, "colossus");
		int copper = 0;
		int waxed = 0;
		int aged = 0;
		for (BlockState s : all.values()) {
			Matcher m = COPPER.matcher(name(s));
			if (m.matches()) {
				copper++;
				waxed += m.group(1) != null ? 1 : 0;
				aged += m.group(2) != null ? 1 : 0;
			}
		}
		helper.assertTrue(waxed == 0, waxed + " of the Colossus's copper blocks are waxed, so they never weather green");
		helper.assertTrue(aged == 0, aged + " copper blocks start weathered (the cloak is new copper that weathers)");
		helper.assertTrue(copper >= 225 && Math.abs(copper - 370) <= 37, "the Colossus has " + copper + " copper blocks (spec about 250, wiki about 370)");
		helper.succeed();
	}

	/** "a robed villager, nose and all ... the face seen from the road": the nose stands out past the eyes (emerald pupils). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE', batch = BATCH, timeoutTicks = 400
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 400)
	public void qaColossusNoseStandsOutPastTheEyes(GameTestHelper helper) {
		Map<BlockPos, BlockState> all = whole(helper, "colossus");
		List<BlockPos> eyes = where(all, "minecraft:emerald_block");
		helper.assertTrue(eyes.size() >= 2, "the Colossus has " + eyes.size() + " emerald pupils, not two");
		int[] b = box(all, p -> true);
		int ey = eyes.get(0).getY();
		int ex = (eyes.stream().mapToInt(BlockPos::getX).min().getAsInt() + eyes.stream().mapToInt(BlockPos::getX).max().getAsInt()) / 2;
		int ez = eyes.get(0).getZ();
		helper.assertTrue(ey >= 34, "the eyes are at y " + ey + ", low for a 47-high statue");
		boolean frontLow = ez - b[2] < b[5] - ez; // the face looks toward the nearer side
		int step = frontLow ? -1 : 1;
		// the nose: blocks in front of the eye plane, on the face's middle, at or below the eyes
		int nose = 0;
		for (BlockPos p : all.keySet()) {
			if (Math.abs(p.getX() - ex) <= 1 && p.getY() <= ey && p.getY() >= ey - 8 && (p.getZ() - ez) * step >= 1 && (p.getZ() - ez) * step <= 3) {
				nose++;
			}
		}
		helper.assertTrue(nose >= 2, "no nose stands out in front of the eyes (" + nose + " blocks before the eye plane at z " + ez + ")");
		helper.succeed();
	}

	/** "the other raising a lantern; the lantern is a glass cage round glowstone, lit day and night"; the wiki: nine braziers, seven signs. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE', batch = BATCH, timeoutTicks = 400
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 400)
	public void qaColossusLanternCagedAndRaised(GameTestHelper helper) {
		Map<BlockPos, BlockState> all = whole(helper, "colossus");
		List<BlockPos> glow = where(all, "minecraft:glowstone");
		helper.assertTrue(!glow.isEmpty(), "no glowstone lantern");
		int[] eyes = boxOf(all, is("minecraft:emerald_block"));
		int low = glow.stream().mapToInt(BlockPos::getY).min().getAsInt();
		helper.assertTrue(low >= eyes[1], "the lantern (from y " + low + ") isn't raised to the head (eyes at y " + eyes[1] + ")");
		List<String> wrong = new ArrayList<>();
		for (BlockPos g : glow) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = g.relative(d);
				while (all.containsKey(n) && name(all.get(n)).equals("minecraft:glowstone")) {
					n = n.relative(d);
				}
				BlockState s = all.get(n);
				if (s == null || !(name(s).contains("glass"))) {
					wrong.add(g.toShortString() + " " + d + ": " + (s == null ? "open" : name(s)));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the lantern's glowstone isn't caged in glass: " + wrong);
		int braziers = count(all, is("minecraft:campfire"));
		int lit = (int) all.values().stream().filter(s -> name(s).equals("minecraft:campfire")
			&& s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)).count();
		helper.assertTrue(braziers == 9 && lit == 9, braziers + " braziers (" + lit + " lit), the wiki says nine");
		int signs = count(all, is("minecraft:dark_oak_wall_sign"));
		helper.assertTrue(signs == 7, "the Founder's plaque has " + signs + " dark oak signs, the wiki says seven");
		helper.succeed();
	}

	// --- the Stadium (35.23, 35.23a) ------------------------------------------------------------------------------------

	/**
	 * 35.23a "give it interiors to walk through like a real football stadium": from outside, a player walks in through
	 * the gate onto the field, up the stands, to both trainer boxes' Training Posts, the professor's lectern, the nurse's
	 * brewing stand, and along a covered concourse under the stands.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE', batch = BATCH, timeoutTicks = 400
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 400)
	public void qaStadiumInteriorsWalkThrough(GameTestHelper helper) {
		Map<BlockPos, BlockState> all = whole(helper, "stadium");
		Set<BlockPos> walked = walk(all);
		List<String> wrong = new ArrayList<>();
		// the field: inside its white edge lines (grass lies outside the bowl too, in the flower beds)
		int[] field = box(all, p -> p.getY() == 0 && name(all.get(p)).equals("minecraft:white_concrete"));
		int fx = (field[0] + field[3]) / 2;
		int fz = (field[2] + field[5]) / 2;
		if (!walked.contains(new BlockPos(fx, 1, fz))) {
			wrong.add("the field's center (" + fx + ", 1, " + fz + ")");
		}
		for (BlockPos post : where(all, "aliveworkplace:training_post")) {
			if (!reaches(walked, post)) {
				wrong.add("the Training Post at " + post.toShortString());
			}
		}
		for (String block : List.of("minecraft:lectern", "minecraft:brewing_stand")) {
			List<BlockPos> at = where(all, block);
			if (at.isEmpty() || at.stream().noneMatch(p -> reaches(walked, p))) {
				wrong.add("a " + block);
			}
		}
		int top = walked.stream().mapToInt(BlockPos::getY).max().orElse(0);
		if (top < 10) {
			wrong.add("the upper stands (walked only to y " + top + ")");
		}
		// covered: a roof 2 to 4 over the feet, inside the bowl's box but off the field; nine in ten such cells walked to
		// (2026-11-05: 548 of 562, the rest small pockets on the south side and under the covered stand), on all four sides
		int[] b = box(all, p -> true);
		int covered = 0;
		int coveredAnywhere = 0;
		Set<String> sides = new HashSet<>();
		Map<Integer, Integer> heights = new java.util.TreeMap<>();
		List<BlockPos> missed = new ArrayList<>();
		for (int x = b[0] + 1; x < b[3]; x++) {
			for (int z = b[2] + 1; z < b[5]; z++) {
				for (int y = 0; y <= b[4]; y++) {
					BlockPos p = new BlockPos(x, y, z);
					boolean onField = x >= field[0] && x <= field[3] && z >= field[2] && z <= field[5];
					if (onField || !standable(all, p)) {
						continue;
					}
					boolean roof = false;
					for (int k = 2; k <= 4 && !roof; k++) {
						BlockState s = all.get(p.above(k));
						roof = s != null && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
					}
					if (!roof) {
						continue;
					}
					coveredAnywhere++;
					if (walked.contains(p)) {
						covered++;
						heights.merge(y, 1, Integer::sum);
						sides.add(x < field[0] ? "west" : x > field[3] ? "east" : z < field[2] ? "north" : "south");
					} else if (missed.size() < 8) {
						missed.add(p);
					}
				}
			}
		}
		final int coveredAll = coveredAnywhere;
		if (covered < 100 || covered * 10 < coveredAll * 9 || sides.size() < 4) {
			wrong.add("a concourse under the stands (only " + covered + " of " + coveredAll + " covered cells walked, on the sides " + sides + ", at heights " + heights + "; missed e.g. " + missed + ")");
		}
		helper.assertTrue(wrong.isEmpty(), "from outside, no walk to " + wrong + " (" + walked.size() + " cells walked; lecterns at "
			+ where(all, "minecraft:lectern") + ", gate side cells at y 1: " + walked.stream().filter(p -> p.getY() == 1).count() + ")");
		helper.succeed();
	}

	/** The wiki: "an oval arena 53 by 43, its bowl 18 high (24 to the gate's banners)"; four towers of eight lamps round a detector. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE', batch = BATCH, timeoutTicks = 400
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 400)
	public void qaStadiumSizeAndFloodlightsAsTheWikiSays(GameTestHelper helper) {
		Map<BlockPos, BlockState> all = whole(helper, "stadium");
		int[] b = box(all, p -> true);
		int w = Math.max(b[3] - b[0], b[5] - b[2]) + 1;
		int l = Math.min(b[3] - b[0], b[5] - b[2]) + 1;
		int h = b[4] + 1;
		helper.assertTrue(Math.abs(w - 53) <= 1 && Math.abs(l - 43) <= 1 && Math.abs(h - 24) <= 1,
			"the Stadium is " + w + " x " + l + " and " + h + " high, the wiki says 53 x 43 and 24 to the banners");
		int lamps = count(all, is("minecraft:redstone_lamp"));
		int detectors = count(all, is("minecraft:daylight_detector"));
		helper.assertTrue(lamps == 32 && detectors == 4, lamps + " redstone lamps and " + detectors + " daylight detectors, not 4 towers of 8 round one");
		for (BlockPos d : where(all, "minecraft:daylight_detector")) {
			long near = where(all, "minecraft:redstone_lamp").stream().filter(p -> p.distManhattan(d) <= 4).count();
			helper.assertTrue(near >= 8, "the detector at " + d.toShortString() + " has " + near + " lamps round it");
		}
		helper.succeed();
	}

	// --- the words ------------------------------------------------------------------------------------------------------

	static final Pattern BRITISH = Pattern.compile("(?i)\\b(colour\\w*|centre\\w*|armour\\w*|jeweller\\w*|travell\\w*|cancell\\w*|grey\\w*|defence\\w*|"
		+ "favour\\w*|honour\\w*|metre\\w*|theatre\\w*|organis\\w*|realis\\w*|recognis\\w*|neighbour\\w*|harbour\\w*|programme\\w*|plough\\w*|mould\\w*)\\b");

	/** Every sentence a player reads about the three Wonders has its line, in American English. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE', batch = BATCH
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
	public void qaWonderWordsInAmericanEnglish(GameTestHelper helper) {
		JsonObject lang;
		try (InputStream in = QaWonderDesigns1105GameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("en_us.json doesn't read: " + e.getMessage());
		}
		List<String> wrong = new ArrayList<>();
		int seen = 0;
		for (String key : lang.keySet()) {
			String k = key.toLowerCase();
			if (!(k.contains("hanging_gardens") || k.contains("colossus") || k.contains("stadium") || k.contains("travellers"))) {
				continue;
			}
			seen++;
			String text = lang.get(key).getAsString();
			Matcher m = BRITISH.matcher(text);
			while (m.find()) {
				wrong.add(key + ": \"" + m.group() + "\"");
			}
			if (text.isBlank()) {
				wrong.add(key + " is empty");
			}
		}
		for (String w : List.of("hanging_gardens", "colossus", "stadium")) {
			for (String part : List.of("name", "description", "stage.1", "stage.2", "stage.3", "stage.4")) {
				if (!lang.has("wonder.aliveworkplace." + w + "." + part)) {
					wrong.add("no wonder.aliveworkplace." + w + "." + part);
				}
			}
		}
		helper.assertTrue(seen >= 18, "only " + seen + " lines about the three Wonders");
		helper.assertTrue(wrong.isEmpty(), "British or missing: " + wrong);
		helper.succeed();
	}
}
