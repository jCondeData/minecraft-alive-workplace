package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.CaravanTrail;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/** The Lost Caravan's trail (ROADMAP 31.19): the wreck, the stockade and the five marks on uneven ground, their rules, follow_trail. */
public class CaravanTrailGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:tall_area";
	/** Where a build's middle goes on the test ground (grass at y 3). */
	private static final BlockPos MIDDLE = new BlockPos(15, 3, 15);
	/** A player's wall outside every footprint. */
	private static final BlockPos PLAYERS_WALL = new BlockPos(1, 4, 15);

	private static void begin(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int near = Arcs.NEAR;
		Arcs.NEAR = 0; // (the tests build the places themselves)
		Leftovers.after(helper, () -> Arcs.NEAR = near);
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2); // (the height map must find the ground)
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
		helper.setBlock(PLAYERS_WALL, Blocks.COBBLESTONE);
		helper.setBlock(PLAYERS_WALL.above(), Blocks.COBBLESTONE);
	}

	/** A two-step hump near the front left of the middle and a hole two deep near the back right. */
	private static void humpAndHole(GameTestHelper helper) {
		for (int x = 11; x <= 12; x++) {
			for (int z = 11; z <= 12; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 5, z), Blocks.GRASS_BLOCK);
			}
		}
		for (int x = 18; x <= 19; x++) {
			for (int z = 17; z <= 18; z++) {
				helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
			}
		}
	}

	private static ArcState.ArcPlace build(GameTestHelper helper, String template, BlockPos middle) {
		ServerLevel level = helper.getLevel();
		ArcState s = new ArcState("aliveworkplace:lost_caravan");
		ArcState.ArcPlace p = new ArcState.ArcPlace("piece", helper.absolutePos(middle), List.of(template), 0);
		s.places.add(p);
		helper.assertTrue(ArcEffects.build(level, s, p), "the build waited for ground already loaded: " + template);
		helper.assertTrue(p.placed == 1 && !p.skipped && p.origin != null, template + " wasn't built: placed " + p.placed + ", skipped " + p.skipped);
		return p;
	}

	/** Nothing floats and the hump is gone: under each column of the footprint the ground and the block below are solid, no earth above it. */
	private static void lies(GameTestHelper helper, BlockPos origin, int sizeX, int sizeZ) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < sizeX; x++) {
			for (int z = 0; z < sizeZ; z++) {
				BlockPos ground = origin.offset(x, 0, z);
				helper.assertFalse(level.getBlockState(ground).isAir(), "open ground under the build at " + helper.relativePos(ground));
				helper.assertFalse(level.getBlockState(ground.below()).isAir(), "a hole under the build at " + helper.relativePos(ground.below()));
				for (int y = 1; y <= 2; y++) {
					var state = level.getBlockState(ground.above(y));
					helper.assertFalse(state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK), "the hump still stands at " + helper.relativePos(ground.above(y)));
				}
			}
		}
		helper.assertBlockPresent(Blocks.COBBLESTONE, PLAYERS_WALL);
		helper.assertBlockPresent(Blocks.COBBLESTONE, PLAYERS_WALL.above());
	}

	private static void is(GameTestHelper helper, BlockPos origin, BlockPos local, Block block, String what) {
		helper.assertTrue(helper.getLevel().getBlockState(origin.offset(local)).is(block), what + ": " + helper.getLevel().getBlockState(origin.offset(local)));
	}

	/** The wreck on a hump and a hole: it lies on the ground at the middle's height, the hump cut away, the hole filled; the wagon, wheels and lantern stand. */
	//$ gametest_ticks_batch AREA '60' '"caravanWreckGround"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "caravanWreckGround")
	public void theWreckLiesOnUnevenGround(GameTestHelper helper) {
		begin(helper);
		humpAndHole(helper);
		ArcState.ArcPlace p = build(helper, CaravanTrail.WRECK.toString(), MIDDLE);
		helper.assertTrue(p.origin.equals(helper.absolutePos(new BlockPos(9, 3, 9))), "the wreck's corner: " + helper.relativePos(p.origin));
		lies(helper, p.origin, 13, 12);
		is(helper, p.origin, new BlockPos(6, 2, 5), Blocks.SPRUCE_PLANKS, "the wagon's floor");
		is(helper, p.origin, new BlockPos(2, 1, 5), Blocks.WHITE_WOOL, "the canvas");
		is(helper, p.origin, new BlockPos(7, 4, 3), Blocks.STRIPPED_DARK_OAK_LOG, "the upper wheel's hub");
		is(helper, p.origin, new BlockPos(10, 1, 7), Blocks.STRIPPED_DARK_OAK_LOG, "the broken wheel");
		is(helper, p.origin, new BlockPos(9, 1, 10), Blocks.LANTERN, "the lantern in the grass");
		is(helper, p.origin, new BlockPos(2, 1, 1), Blocks.COMPOSTER, "a spilled crate");
		helper.succeed();
	}

	/** The stockade on a hump and a hole: the palisade, the open gate, the fire, the loot chest, the cage with its iron gate and lever. */
	//$ gametest_ticks_batch AREA '60' '"raidersStockadeGround"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "raidersStockadeGround")
	public void theStockadeLiesOnUnevenGround(GameTestHelper helper) {
		begin(helper);
		humpAndHole(helper);
		ArcState.ArcPlace p = build(helper, CaravanTrail.STOCKADE.toString(), MIDDLE);
		helper.assertTrue(p.origin.equals(helper.absolutePos(new BlockPos(6, 3, 7))), "the stockade's corner: " + helper.relativePos(p.origin));
		lies(helper, p.origin, 19, 17);
		ServerLevel level = helper.getLevel();
		is(helper, p.origin, new BlockPos(0, 2, 6), Blocks.DARK_OAK_LOG, "the palisade");
		is(helper, p.origin, new BlockPos(8, 1, 0), Blocks.DARK_OAK_FENCE_GATE, "the gate");
		is(helper, p.origin, new BlockPos(9, 1, 8), Blocks.CAMPFIRE, "the fire");
		is(helper, p.origin, new BlockPos(4, 3, 12), Blocks.STRIPPED_DARK_OAK_LOG, "the tent's ridge");
		is(helper, p.origin, new BlockPos(13, 1, 10), Blocks.IRON_BARS, "the cage's bars");
		is(helper, p.origin, new BlockPos(15, 2, 9), Blocks.LEVER, "the cage's lever");
		BlockPos door = p.origin.offset(CaravanTrail.CAGE_DOOR);
		helper.assertTrue(level.getBlockState(door).is(Blocks.IRON_DOOR) && level.getBlockState(door.above()).is(Blocks.IRON_DOOR)
			&& !level.getBlockState(door).getValue(DoorBlock.OPEN), "the cage's iron gate isn't there and shut");
		ResourceKey<net.minecraft.world.level.storage.loot.LootTable> loot = ResourceKey.create(Registries.LOOT_TABLE, AliveWorkplace.id("chests/bandit_camp"));
		helper.assertTrue(level.getBlockEntity(p.origin.offset(CaravanTrail.LOOT_CHEST)) instanceof ChestBlockEntity chest && loot.equals(chest.getLootTable()),
			"the loot chest isn't the raiders'");
		helper.succeed();
	}

	/** The five marks in a row on ground a step up and down: each lies cleanly with what makes it a mark. */
	//$ gametest_ticks_batch AREA '60' '"trailMarksGround"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "trailMarksGround")
	public void theFiveMarksLieOnUnevenGround(GameTestHelper helper) {
		begin(helper);
		for (int x = 0; x < 30; x += 2) {
			for (int z = 12; z <= 18; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.GRASS_BLOCK); // a step up every other column
			}
		}
		helper.setBlock(new BlockPos(16, 3, 16), Blocks.AIR); // and a step down
		helper.setBlock(new BlockPos(16, 2, 16), Blocks.AIR);
		Block[][] marks = {{Blocks.LANTERN}, {Blocks.BARREL}, {Blocks.WHITE_BANNER, Blocks.SPRUCE_FENCE}, {Blocks.DIRT_PATH}, {Blocks.COMPOSTER, Blocks.TRIPWIRE_HOOK}};
		BlockPos[][] where = {{new BlockPos(0, 1, 2)}, {new BlockPos(1, 1, 1)}, {new BlockPos(1, 3, 1), new BlockPos(1, 1, 1)}, {new BlockPos(0, 0, 1)},
			{new BlockPos(1, 1, 1), new BlockPos(0, 1, 1)}};
		for (int i = 0; i < 5; i++) {
			ArcState.ArcPlace p = build(helper, CaravanTrail.MARKS.get(i).toString(), new BlockPos(3 + 6 * i, 3, 15));
			BlockPos ground = p.origin;
			helper.assertTrue(Math.abs(p.spot.getX() - helper.absolutePos(new BlockPos(3 + 6 * i, 3, 15)).getX()) <= 0, "mark " + (i + 1) + " moved: " + helper.relativePos(p.spot));
			for (int x = 0; x < 3; x++) {
				for (int z = 0; z < 3; z++) {
					helper.assertFalse(helper.getLevel().getBlockState(ground.offset(x, -1, z)).isAir(), "a hole under mark " + (i + 1));
					helper.assertFalse(helper.getLevel().getBlockState(ground.offset(x, 1, z)).is(Blocks.GRASS_BLOCK), "earth over mark " + (i + 1));
				}
			}
			for (int k = 0; k < marks[i].length; k++) {
				is(helper, ground, where[i][k], marks[i][k], "mark " + (i + 1));
			}
		}
		helper.succeed();
	}

	/** A player's block refuses the ground: cobblestone in the box, or a block the ledger says a player set there; the ground alone fits. */
	//$ gametest_ticks_batch AREA '40' '"caravanNeverOverPlayers"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "caravanNeverOverPlayers")
	public void neverOverAPlayersBlocks(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = helper.absolutePos(MIDDLE);
		BoundingBox box = new BoundingBox(ground.getX() - 9, ground.getY(), ground.getZ() - 8, ground.getX() + 9, ground.getY() + 8, ground.getZ() + 8);
		helper.assertTrue(CaravanTrail.fits(level, ground, box), "open grass doesn't fit");
		helper.setBlock(new BlockPos(20, 4, 20), Blocks.COBBLESTONE);
		helper.assertFalse(CaravanTrail.fits(level, ground, box), "it fits over a player's cobblestone");
		helper.setBlock(new BlockPos(20, 4, 20), Blocks.AIR);
		BlockPos earth = helper.absolutePos(new BlockPos(10, 3, 10));
		PlayerBuilt.get(level).mark(earth);
		try {
			helper.assertFalse(CaravanTrail.fits(level, ground, box), "it fits over earth a player set");
		} finally {
			PlayerBuilt.get(level).forget(BoundingBox.fromCorners(earth, earth));
		}
		helper.succeed();
	}

	/**
	 * The arc's rules: the wreck on the road a third of the way when fit ground (or not yet loaded), else 150 blocks out
	 * its way, or any way with no road; the stockade 120 to 180 on from it, away from the village; the marks evenly between.
	 */
	//$ gametest_ticks_batch AREA '40' '"caravanTrailRules"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "caravanTrailRules")
	public void theRulesPlaceTheTrail(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(3119L);
		BlockPos hall = helper.absolutePos(new BlockPos(2, 4, 2));
		BlockPos far = hall.offset(4000, 0, 0);
		// a road spot far off, not loaded: taken as it is (its ground is tested when it's built)
		BlockPos unloaded = hall.offset(1333, 0, 0);
		helper.assertTrue(CaravanTrail.road(level, hall, unloaded, far, 150, random).equals(unloaded), "the unloaded road spot wasn't kept");
		// a road spot under water: 150 out along the road instead
		BlockPos wet = helper.absolutePos(MIDDLE);
		for (int x = 5; x <= 25; x++) {
			for (int z = 5; z <= 25; z++) {
				helper.setBlock(new BlockPos(x, 3, z), Blocks.WATER);
			}
		}
		BlockPos fallback = CaravanTrail.road(level, hall, wet.offset(0, 1, 0), far, 150, random);
		helper.assertTrue(fallback.equals(hall.offset(150, 0, 0)), "not 150 out along the road: " + fallback.subtract(hall));
		BlockPos anyWay = CaravanTrail.road(level, hall, null, null, 150, random);
		double d = Math.sqrt(anyWay.distSqr(new BlockPos(hall.getX(), anyWay.getY(), hall.getZ())));
		helper.assertTrue(Math.abs(d - 150) <= 1.5, "with no road, not 150 out: " + d);
		// the stockade away from the village
		BlockPos wreck = hall.offset(150, 0, 0);
		BlockPos stockade = CaravanTrail.away(level, hall, wreck, 120, 180, random);
		double fromWreck = Math.sqrt(stockade.distSqr(wreck));
		helper.assertTrue(fromWreck >= 119 && fromWreck <= 181, "the stockade is " + fromWreck + " from the wreck");
		helper.assertTrue(stockade.distSqr(hall) > wreck.distSqr(hall), "the stockade isn't further from the village than the wreck: " + stockade.subtract(hall));
		// the marks by the arc's place effect: evenly between, one template each
		ArcState s = new ArcState("aliveworkplace:lost_caravan");
		s.places.add(new ArcState.ArcPlace("wreck", wreck, List.of(CaravanTrail.WRECK.toString()), 0));
		s.places.add(new ArcState.ArcPlace("stockade", wreck.offset(0, 0, 150), List.of(CaravanTrail.STOCKADE.toString()), 0));
		JsonObject rule = JsonParser.parseString("{\"between\": [\"wreck\", \"stockade\"]}").getAsJsonObject();
		for (int i = 0; i < 5; i++) {
			BlockPos mark = ArcEffects.spot(level, hall, s, rule, i, 5);
			helper.assertTrue(mark != null && mark.getX() == wreck.getX() && mark.getZ() == wreck.getZ() + 25 * (i + 1), "mark " + (i + 1) + ": " + mark);
		}
		helper.assertTrue(ArcEffects.spot(level, hall, s, JsonParser.parseString("{\"between\": [\"wreck\", \"nowhere\"]}").getAsJsonObject(), 0, 5) == null,
			"a mark between a place and nothing");
		helper.succeed();
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	/**
	 * {@code follow_trail} through a real arc: its chapter places the wreck, the stockade and five marks between them
	 * (12 blocks apart) and posts the quest; reaching mark 3 first counts nothing, nor mark 1 twice; the marks in order
	 * count one each and the fifth finishes it. The objective saves and reads back the same; the tracks point on south.
	 */
	//$ gametest_ticks_batch AREA '60' '"followTrailInOrder"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "followTrailInOrder")
	public void followTrailCountsTheMarksInOrderOnly(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(new BlockPos(2, 4, 2), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(2, 4, 2));
		Leftovers.after(helper, () -> Arcs.stopAll(level, hall));
		Arcs.stopAll(level, hall);
		((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).setLastQuestDay(Long.MAX_VALUE / 2);
		StringBuilder marks = new StringBuilder();
		for (int i = 0; i < 5; i++) {
			marks.append(i == 0 ? "" : ", ").append('"').append(CaravanTrail.MARKS.get(i)).append('"');
		}
		String json = "{\"name\": \"Trail test\", \"chapters\": [{\"name\": \"The tracks\", \"on_start\": ["
			+ "{\"type\": \"place\", \"key\": \"wreck\", \"template\": \"" + CaravanTrail.WRECK + "\", \"rule\": {\"offset\": [2, 2]}},"
			+ "{\"type\": \"place\", \"key\": \"stockade\", \"template\": \"" + CaravanTrail.STOCKADE + "\", \"rule\": {\"offset\": [2, 74]}},"
			+ "{\"type\": \"place\", \"key\": \"trail\", \"each\": [" + marks + "], \"rule\": {\"between\": [\"wreck\", \"stockade\"]}}],"
			+ "\"quests\": [{\"id\": \"aliveworkplace:arc/test_trail/follow\", \"name\": \"Follow\","
			+ " \"objectives\": [{\"type\": \"follow_trail\", \"trail\": \"trail\", \"then\": \"stockade\"}]}]}]}";
		Arcs.Arc arc = Arcs.read(AliveWorkplace.id("test_trail"), JsonParser.parseString(json).getAsJsonObject());
		Arcs.add(arc);
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null, "the test arc didn't start");
		List<ArcState.ArcPlace> placed = s.places.stream().filter(p -> p.key.startsWith("trail")).toList();
		helper.assertTrue(placed.size() == 5, "marks placed: " + placed.size());
		for (int i = 0; i < 5; i++) {
			helper.assertTrue(placed.get(i).stages.equals(List.of(CaravanTrail.MARKS.get(i).toString())) && placed.get(i).spot.getZ() == hall.getZ() + 14 + 12 * i,
				"mark " + (i + 1) + ": " + placed.get(i).stages + " at " + placed.get(i).spot.subtract(hall));
		}
		Quest quest = Stories.open(level, hall).stream().filter(q -> arc.id().toString().equals(q.arc)).findFirst().orElse(null);
		helper.assertTrue(quest != null && quest.objectives.get(0) instanceof CaravanTrail.FollowTrail t && t.need() == 5, "no follow_trail quest of 5 marks");
		Objectives.Objective o = quest.objectives.get(0);
		helper.assertTrue(Objectives.parse(o.json()).equals(o), "follow_trail doesn't read back the same: " + o.json());
		helper.assertTrue(key(CaravanTrail.direction(placed.get(0).spot, placed.get(1).spot)).equals("screen.aliveworkplace.hall.dir.s")
			&& key(CaravanTrail.direction(placed.get(1).spot, placed.get(0).spot)).equals("screen.aliveworkplace.hall.dir.n")
			&& key(CaravanTrail.direction(hall, hall.offset(10, 0, -10))).equals("screen.aliveworkplace.hall.dir.ne"), "the ways the tracks go");

		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		java.util.function.IntConsumer at = i -> {
			BlockPos m = placed.get(i).spot;
			me.teleportTo(m.getX() + 2.5, hall.getY(), m.getZ() + 0.5);
			CaravanTrail.check(me);
		};
		at.accept(2);
		helper.assertTrue(quest.progress[0] == 0, "mark 3 counted first: " + quest.progress[0]);
		at.accept(0);
		helper.assertTrue(quest.progress[0] == 1, "mark 1 didn't count: " + quest.progress[0]);
		at.accept(0);
		at.accept(2);
		helper.assertTrue(quest.progress[0] == 1, "mark 1 twice or mark 3 before 2 counted: " + quest.progress[0]);
		me.teleportTo(placed.get(1).spot.getX() + 9.5, hall.getY(), placed.get(1).spot.getZ() + 0.5);
		CaravanTrail.check(me);
		helper.assertTrue(quest.progress[0] == 1, "counted 9 blocks off mark 2");
		for (int i = 1; i < 4; i++) {
			at.accept(i);
			helper.assertTrue(quest.progress[0] == i + 1, "mark " + (i + 1) + " didn't count: " + quest.progress[0]);
		}
		at.accept(4);
		helper.assertTrue(Stories.open(level, hall).stream().noneMatch(q -> q.id.equals(quest.id)), "the quest is still open after the fifth mark");
		me.discard();
		helper.succeed();
	}
}
