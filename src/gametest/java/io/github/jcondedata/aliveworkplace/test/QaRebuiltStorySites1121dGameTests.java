package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * QA, lane-d-1121-1908, ROADMAP 52.5's rebuilt story and legend sites other than the raider sites: the prisoner's cage
 * (its spot moved to (2, 3, 3), on the cart), the castaway's and the traveler's camps, the Founder's statue and the
 * Shift Bell. Written from the item, the blind briefs (tools/blueprints/briefs/*.md) and the wiki, through the code's own
 * entry points ({@link LegendSites#place}, {@link ReformStories#placeBell}, {@link Founder#stands}): every Legend stands
 * free and on its ground in each of the four turns on uneven land, the prisoner can't walk out and only its own bars
 * free it, the castaway and the traveler have a way out, nothing floats and every block survives where it landed, a
 * captive kept across a save is still caged and freed by its bar, the statue counts by its head in every turn, and the
 * bell takes the nearest true spot and rings by hand.
 */
public class QaRebuiltStorySites1121dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	/** Four camps' middles, one per turn, far enough apart that no two boxes (at most 10 wide) meet. */
	private static final List<BlockPos> MIDDLES = List.of(new BlockPos(6, 1, 6), new BlockPos(23, 1, 6), new BlockPos(6, 1, 23), new BlockPos(23, 1, 23));

	private static Legend legend(String site, int n) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1121d_" + site + "_" + n), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:cartographer\", \"title\": \"Found " + site + "\", \"lore\": \"the lore\","
				+ " \"names\": [\"Wren of the " + site + "\"], \"arrive\": [{\"way\": \"found\", \"site\": \"" + site + "\"}]}").getAsJsonObject());
	}

	/** The area emptied of what earlier batches built here, on a floor of {@code floor}. */
	private static void wipe(GameTestHelper helper, net.minecraft.world.level.block.Block floor) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), floor);
				for (int y = 2; y < 30; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	/** The owner's hall in the middle, and every captive, villager and record entry gone when the test ends. */
	static void setUp(GameTestHelper helper, ServerPlayer owner, net.minecraft.world.level.block.Block floor) {
		Leftovers.clear(helper);
		wipe(helper, floor);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setOwner(owner.getUUID(), owner.getName().getString());
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		Leftovers.after(helper, () -> {
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				record.forgetCaptive(v.getUUID());
				v.discard();
			}
			for (LegendRecord.Captive c : record.captives()) {
				if (c.hall().equals(Optional.of(hall))) {
					record.forgetCaptive(c.villager());
				}
			}
			LegendPowers.forget();
		});
	}

	/** A fixed seed whose first turn is {@code rotation} ({@link LegendSites#place} turns the camp with Rotation.getRandom). */
	private static RandomSource turned(Rotation rotation) {
		for (long seed = 0; seed < 1000; seed++) {
			long mixed = seed * 0x9E3779B97F4A7C15L + 12345L; // sequential small seeds all draw the same first turn
			if (Rotation.getRandom(RandomSource.create(mixed)) == rotation) {
				return RandomSource.create(mixed);
			}
		}
		throw new AssertionError("no seed turns " + rotation);
	}

	/** Uneven land round {@code middle}: a hump of 2 east, a dip of 1 west and south, a wall stub north (the middle stays). */
	private static void uneven(GameTestHelper helper, BlockPos middle) {
		helper.setBlock(middle.offset(2, 1, -1), Blocks.STONE);
		helper.setBlock(middle.offset(2, 1, 0), Blocks.STONE);
		helper.setBlock(middle.offset(2, 2, 0), Blocks.STONE);
		helper.setBlock(middle.offset(3, 1, 1), Blocks.DIRT);
		helper.setBlock(middle.offset(-2, 0, 1), Blocks.AIR);
		helper.setBlock(middle.offset(-3, 0, 1), Blocks.AIR);
		helper.setBlock(middle.offset(-1, 0, 2), Blocks.AIR);
		helper.setBlock(middle.offset(1, 0, 3), Blocks.AIR);
		helper.setBlock(middle.offset(0, 1, -3), Blocks.COBBLESTONE_WALL);
		helper.setBlock(middle.offset(0, 2, -3), Blocks.COBBLESTONE_WALL);
	}

	static BoundingBox box(LegendRecord.Captive c) {
		return BoundingBox.fromCorners(c.min(), c.max());
	}

	static LegendRecord.Captive captive(GameTestHelper helper, Villager v) {
		LegendRecord.Captive c = LegendRecord.get(helper.getLevel()).captive(v.getUUID()).orElse(null);
		helper.assertTrue(c != null, "the Legend is on the captives' list");
		return c;
	}

	private static boolean freed(GameTestHelper helper, Villager v) {
		return LegendRecord.get(helper.getLevel()).captive(v.getUUID()).map(LegendRecord.Captive::freed).orElse(false);
	}

	/** Stands free (no block in the body) and on something (not hanging in the air). */
	private static String standing(ServerLevel level, Villager v) {
		if (!level.noCollision(v, v.getBoundingBox().deflate(0.02))) {
			return "inside a block at " + v.blockPosition() + " (" + level.getBlockState(v.blockPosition()) + " / " + level.getBlockState(v.blockPosition().above()) + ")";
		}
		if (!level.getFluidState(v.blockPosition()).isEmpty() || !level.getFluidState(v.blockPosition().above()).isEmpty()) {
			return "in water at " + v.blockPosition();
		}
		if (level.noCollision(v, v.getBoundingBox().move(0, -0.05, 0))) {
			return "hanging in the air at " + v.position() + " over " + level.getBlockState(v.blockPosition().below());
		}
		return null;
	}

	private static VoxelShape shape(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p);
	}

	private static boolean clear(ServerLevel level, BlockPos p) {
		return shape(level, p).isEmpty();
	}

	/** A villager could stand with its feet in {@code p}: nothing above a carpet or slab's height there, and its head free. */
	private static boolean room(ServerLevel level, BlockPos p) {
		VoxelShape s = shape(level, p);
		return (s.isEmpty() || s.max(Direction.Axis.Y) <= 0.5) && clear(level, p.above());
	}

	/**
	 * Whether a villager at {@code from} can walk out of {@code box} on foot (sideways, down a drop, up a one-block step with
	 * room to jump): the way a freed Legend walks off, and the way a caged one must not have.
	 */
	private static boolean walksOut(ServerLevel level, BlockPos from, BoundingBox box) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		queue.add(from);
		seen.add(from);
		while (!queue.isEmpty()) {
			BlockPos cur = queue.poll();
			if (cur.getX() < box.minX() || cur.getX() > box.maxX() || cur.getZ() < box.minZ() || cur.getZ() > box.maxZ()) {
				return true;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = cur.relative(d);
				BlockPos to = null;
				if (room(level, n)) {
					to = n;
					for (int k = 0; k < 4 && clear(level, to.below()) && to.getY() > box.minY(); k++) {
						to = to.below();
					}
				} else if (shape(level, n).max(Direction.Axis.Y) <= 1.0 && room(level, n.above()) && clear(level, cur.above(2))) {
					to = n.above();
				}
				if (to != null && to.getY() <= box.maxY() + 2 && seen.add(to.immutable())) {
					queue.add(to.immutable());
				}
			}
		}
		return false;
	}

	/** Every non-air block in {@code box} not joined, face to face, to its bottom layer: what floats. */
	static List<String> floating(ServerLevel level, BoundingBox box) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockPos p = new BlockPos(x, box.minY(), z);
				if (!level.getBlockState(p).isAir() && seen.add(p)) {
					queue.add(p);
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos cur = queue.poll();
			for (Direction d : Direction.values()) {
				BlockPos n = cur.relative(d);
				if (box.isInside(n) && !level.getBlockState(n).isAir() && seen.add(n)) {
					queue.add(n);
				}
			}
		}
		List<String> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(p).isAir() && !seen.contains(p)) {
				out.add(level.getBlockState(p).getBlock() + "@" + p);
			}
		}
		return out;
	}

	/** Every block in {@code box} that couldn't stay where it is (it pops off at the first neighbour update). */
	private static List<String> unstable(ServerLevel level, BoundingBox box) {
		List<String> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(p);
			if (!s.isAir() && !s.canSurvive(level, p)) {
				out.add(s + "@" + p);
			}
		}
		return out;
	}

	/** The site's camp set down in each of the four turns on uneven land; its Legends, by turn. */
	static Map<Rotation, Villager> fourCamps(GameTestHelper helper, String site) {
		ServerLevel level = helper.getLevel();
		Map<Rotation, Villager> out = new HashMap<>();
		for (int i = 0; i < 4; i++) {
			Rotation r = Rotation.values()[i];
			BlockPos middle = MIDDLES.get(i);
			uneven(helper, middle);
			Villager v = LegendSites.place(level, LegendSites.site(site), new LegendSites.Match(helper.absolutePos(HALL), legend(site, i)),
				helper.absolutePos(middle), turned(r));
			helper.assertTrue(v != null, "the " + site + " camp is set down turned " + r);
			out.put(r, v);
		}
		return out;
	}

	/** What every camp must do in every turn: its Legend stands free on the ground, nothing floats, every block stays. */
	private static void campHolds(GameTestHelper helper, String site, Rotation r, Villager v, boolean floats) {
		ServerLevel level = helper.getLevel();
		BoundingBox box = box(captive(helper, v));
		String bad = standing(level, v);
		helper.assertTrue(bad == null, site + " turned " + r + ": the Legend is " + bad);
		helper.assertTrue(box.isInside(v.blockPosition()), site + " turned " + r + ": the Legend stands in the camp's box " + box + ", at " + v.blockPosition());
		if (floats) { // the castaway camp's own check is QaRebuiltStorySites1121dBugGameTests' (its torn sail floats)
			List<String> floating = floating(level, box);
			helper.assertTrue(floating.isEmpty(), site + " turned " + r + ": blocks float: " + floating);
		}
		List<String> pops = unstable(level, box);
		helper.assertTrue(pops.isEmpty(), site + " turned " + r + ": blocks that can't stay: " + pops);
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockPos ground = new BlockPos(x, box.minY(), z);
				helper.assertFalse(level.getBlockState(ground).isAir(), site + " turned " + r + ": a hole in the camp's ground at " + ground);
			}
		}
	}

	/**
	 * The rebuilt cage (52.5, the prisoner on the cart at (2, 3, 3)) in each turn on uneven land: the prisoner stands free
	 * in the cage and on its floor, can't walk out, still stands there unharmed 100 ticks on; a bar of their own cage is
	 * under 3 blocks away and breaking it frees them and nobody else; nothing floats and every block stays.
	 */
	//$ gametest_ticks_batch AREA '240' '"qaRebuiltCage1121d"'
	@GameTest(template = AREA, timeoutTicks = 240, batch = "qaRebuiltCage1121d")
	public void qaThePrisonerStandsCagedOnTheCartInEveryTurn(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner, Blocks.GRASS_BLOCK);
		ServerLevel level = helper.getLevel();
		Map<Rotation, Villager> camps = fourCamps(helper, "outpost");
		Map<Rotation, Vec3> at = new HashMap<>();
		for (Map.Entry<Rotation, Villager> e : camps.entrySet()) {
			Villager v = e.getValue();
			campHolds(helper, "cage", e.getKey(), v, true);
			helper.assertTrue(v.isNoAi() && LegendSites.isCaptive(v), "the prisoner waits with no AI");
			helper.assertFalse(walksOut(level, v.blockPosition(), box(captive(helper, v))),
				"cage turned " + e.getKey() + ": the prisoner at " + v.blockPosition() + " could walk out of the cage on foot");
			at.put(e.getKey(), v.position());
		}
		helper.runAfterDelay(100, () -> {
			owner.setGameMode(GameType.SURVIVAL);
			for (Rotation r : Rotation.values()) {
				Villager v = camps.get(r);
				helper.assertTrue(v.isAlive() && v.getHealth() >= v.getMaxHealth(), "cage turned " + r + ": the prisoner is hurt (" + v.getHealth() + ")");
				helper.assertTrue(v.position().distanceTo(at.get(r)) < 0.01, "cage turned " + r + ": the prisoner moved from " + at.get(r) + " to " + v.position());
				helper.assertFalse(freed(helper, v), "nobody is freed by waiting");
			}
			for (Rotation r : Rotation.values()) {
				Villager v = camps.get(r);
				BoundingBox box = box(captive(helper, v));
				BlockPos bar = null;
				double best = Double.MAX_VALUE;
				for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
					double d = v.position().distanceTo(Vec3.atCenterOf(p));
					if (level.getBlockState(p).is(Blocks.IRON_BARS) && d < best) {
						best = d;
						bar = p.immutable();
					}
				}
				helper.assertTrue(bar != null && best < 3, "cage turned " + r + ": the nearest iron bar of the cage is " + best + " away");
				owner.gameMode.destroyBlock(bar);
				helper.assertTrue(level.getBlockState(bar).isAir(), "the bar breaks");
				helper.assertTrue(freed(helper, v) && !v.isNoAi(), "cage turned " + r + ": breaking its bar frees the prisoner");
				for (Rotation o : Rotation.values()) {
					if (o.ordinal() > r.ordinal()) {
						helper.assertFalse(freed(helper, camps.get(o)), "breaking the bar of the cage turned " + r + " frees the prisoner turned " + o);
					}
				}
			}
			helper.succeed();
		});
	}

	/**
	 * The rebuilt castaway camp in each turn on an uneven beach: the castaway stands free on the ground, has a way out on
	 * foot (and so a player has a way in), nothing floats and every block stays; a cooked meal frees them.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRebuiltCastaway1121d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRebuiltCastaway1121d")
	public void qaTheCastawayStandsOnTheBeachWithAWayOutInEveryTurn(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner, Blocks.SAND);
		ServerLevel level = helper.getLevel();
		Map<Rotation, Villager> camps = fourCamps(helper, "shipwreck");
		for (Map.Entry<Rotation, Villager> e : camps.entrySet()) {
			Villager v = e.getValue();
			campHolds(helper, "castaway camp", e.getKey(), v, false);
			helper.assertTrue(walksOut(level, v.blockPosition(), box(captive(helper, v))),
				"castaway camp turned " + e.getKey() + ": no way out on foot from " + v.blockPosition());
		}
		helper.runAfterDelay(100, () -> {
			owner.setGameMode(GameType.SURVIVAL);
			for (Rotation r : Rotation.values()) {
				Villager v = camps.get(r);
				helper.assertTrue(v.isAlive() && v.getHealth() >= v.getMaxHealth(), "castaway camp turned " + r + ": the castaway is hurt (" + v.getHealth() + ")");
				owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_COD, 2));
				LegendSites.use(owner, v, InteractionHand.MAIN_HAND);
				helper.assertTrue(freed(helper, v) && !v.isNoAi(), "castaway camp turned " + r + ": a cooked meal frees them");
			}
			helper.succeed();
		});
	}

	/**
	 * The rebuilt traveler's camp in each turn on uneven ground: the traveler stands free on solid ground with a way out,
	 * nothing floats, every block stays, exactly one barrel with the map on it, and a word frees them.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRebuiltTraveller1121d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRebuiltTraveller1121d")
	public void qaTheTravelerStandsOnTheGroundWithAWayOutInEveryTurn(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner, Blocks.GRASS_BLOCK);
		ServerLevel level = helper.getLevel();
		Map<Rotation, Villager> camps = fourCamps(helper, "ruined_portal");
		for (Map.Entry<Rotation, Villager> e : camps.entrySet()) {
			Villager v = e.getValue();
			campHolds(helper, "traveler's camp", e.getKey(), v, true);
			helper.assertTrue(level.getBlockState(v.blockPosition().below()).isSolid(), "traveler's camp turned " + e.getKey() + ": stands on solid ground");
			helper.assertTrue(walksOut(level, v.blockPosition(), box(captive(helper, v))),
				"traveler's camp turned " + e.getKey() + ": no way out on foot from " + v.blockPosition());
		}
		helper.runAfterDelay(110, () -> {
			for (Rotation r : Rotation.values()) {
				Villager v = camps.get(r);
				BoundingBox box = box(captive(helper, v));
				List<BlockPos> barrels = new ArrayList<>();
				for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
					if (level.getBlockState(p).is(Blocks.BARREL)) {
						barrels.add(p.immutable());
					}
				}
				helper.assertTrue(barrels.size() == 1, "traveler's camp turned " + r + ": " + barrels.size() + " barrels");
				BlockPos above = barrels.get(0).above();
				List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(above).inflate(0.1),
					f -> f.blockPosition().equals(above) && f.getItem().is(Items.MAP) && f.survives());
				helper.assertTrue(frames.size() == 1, "traveler's camp turned " + r + ": the map on the barrel (" + frames.size() + " frames)");
				helper.assertTrue(v.isAlive() && v.getHealth() >= v.getMaxHealth(), "traveler's camp turned " + r + ": the traveler is hurt");
				LegendSites.use(owner, v, InteractionHand.MAIN_HAND);
				helper.assertTrue(freed(helper, v) && !v.isNoAi(), "traveler's camp turned " + r + ": a word frees them");
			}
			for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, helper.getBounds().inflate(4), f -> true)) {
				f.discard();
			}
			helper.succeed();
		});
	}

	/**
	 * Save and reload mid-story: a prisoner in the rebuilt cage is written out and read back (the villager and the record),
	 * and is still caged where they stood, free of blocks, unfreed by waiting; their own bar still frees them after it.
	 */
	//$ gametest_ticks_batch AREA '120' '"qaRebuiltCageReload1121d"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaRebuiltCageReload1121d")
	public void qaAPrisonerKeptAcrossASaveIsStillCagedAndFreedByTheirBar(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner, Blocks.GRASS_BLOCK);
		ServerLevel level = helper.getLevel();
		Villager before = LegendSites.place(level, LegendSites.site("outpost"), new LegendSites.Match(helper.absolutePos(HALL), legend("outpost", 9)),
			helper.absolutePos(new BlockPos(15, 1, 24)), turned(Rotation.CLOCKWISE_90));
		helper.assertTrue(before != null, "the cage is set down");
		UUID id = before.getUUID();
		Vec3 where = before.position();
		LegendRecord.Captive saved = captive(helper, before);
		LegendRecord back = LegendRecord.load(LegendRecord.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		LegendRecord.Captive loaded = back.captive(id).orElse(null);
		helper.assertTrue(loaded != null && loaded.equals(saved), "the record keeps the captive: " + loaded + " / " + saved);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(before.save(tag), "the prisoner is saved");
		before.discard(); // read back in the same tick: LegendSites.walk forgets a captive whose villager is gone
		Entity e = EntityType.loadEntityRecursive(tag, level, x -> x);
		helper.assertTrue(e instanceof Villager, "the prisoner loads");
		Villager v = (Villager) e;
		level.addFreshEntity(v);
		helper.assertTrue(level.getEntity(id) == v, "the same prisoner is back in the level");
		helper.assertTrue(v.isNoAi() && LegendSites.isCaptive(v) && LegendSites.waiting(v).isPresent(), "still a captive after the reload");
		helper.assertTrue(v.position().distanceTo(where) < 0.01, "still at the spot");
		String bad = standing(level, v);
		helper.assertTrue(bad == null, "after the reload the prisoner is " + bad);
		helper.assertFalse(walksOut(level, v.blockPosition(), box(saved)), "after the reload the prisoner could walk out");
		{
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(v.isAlive() && v.getHealth() >= v.getMaxHealth(), "the reloaded prisoner is hurt (" + v.getHealth() + ")");
				helper.assertFalse(freed(helper, v), "not freed by the reload");
				BlockPos bar = null;
				BoundingBox box = box(saved);
				for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
					if (level.getBlockState(p).is(Blocks.IRON_BARS) && (bar == null || v.position().distanceTo(Vec3.atCenterOf(p)) < v.position().distanceTo(Vec3.atCenterOf(bar)))) {
						bar = p.immutable();
					}
				}
				helper.assertTrue(bar != null, "the cage's bars are still there");
				owner.setGameMode(GameType.SURVIVAL);
				owner.gameMode.destroyBlock(bar);
				helper.assertTrue(freed(helper, v) && !v.isNoAi(), "the bar frees the reloaded prisoner");
				helper.succeed();
			});
		}
	}

	/**
	 * The rebuilt Founder's statue, placed as Founder.spot places it (front to the hall) in each of the four turns: the
	 * box the code measures is the box the template fills, both sides odd and at most 9; nothing floats and every block
	 * stays; no point of interest or container; finished, it stands; breaking a block off the middle column leaves it
	 * standing, knocking the three head cells out stops it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRebuiltStatue1121d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRebuiltStatue1121d")
	public void qaTheFoundersStatueCountsByItsHeadInEveryTurn(GameTestHelper helper) {
		Leftovers.clear(helper);
		wipe(helper, Blocks.GRASS_BLOCK);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Blueprint blueprint = BlueprintLibrary.get(level, Founder.STATUE).orElse(null);
		helper.assertTrue(blueprint != null, "the statue's blueprint loads");
		StructureTemplate template = level.getStructureManager().get(Founder.STATUE).orElse(null);
		helper.assertTrue(template != null, "the statue's template loads");
		var size = blueprint.size();
		helper.assertTrue(size.getX() % 2 == 1 && size.getZ() % 2 == 1 && size.getX() <= 9 && size.getZ() <= 9,
			"the statue's footprint is odd both ways and within 9 x 9: " + size);
		helper.assertTrue(size.equals(template.getSize()), "the blueprint and the template are the same size: " + size + " / " + template.getSize());
		List<BuildSiteManager.Finished> recorded = new ArrayList<>();
		Leftovers.after(helper, () -> {
			recorded.forEach(f -> BuildSiteManager.get(level).forgetFinished(f.placement()));
			Founder.forgetStands();
		});
		UUID owner = UUID.randomUUID();
		// north of the hall facing south, east facing west, south facing north, west facing east (as Founder.spot turns it)
		Map<Direction, BlockPos> anchors = new java.util.LinkedHashMap<>();
		anchors.put(Direction.SOUTH, new BlockPos(15, 2, 8));
		anchors.put(Direction.WEST, new BlockPos(22, 2, 15));
		anchors.put(Direction.NORTH, new BlockPos(15, 2, 22));
		anchors.put(Direction.EAST, new BlockPos(8, 2, 15));
		for (Map.Entry<Direction, BlockPos> a : anchors.entrySet()) {
			String turn = "the statue facing " + a.getKey();
			BlueprintData.Placement placement = BlueprintItem.placementAt(Ids.of(level.dimension()), size, helper.absolutePos(a.getValue()),
				BlueprintItem.rotationFacing(a.getKey()));
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(placement.rotation()).setMirror(placement.mirror());
			BoundingBox counted = BlueprintOutline.bounds(placement, size);
			BoundingBox filled = template.getBoundingBox(settings, placement.origin());
			helper.assertTrue(counted.equals(filled), turn + ": the box the code measures " + counted + " is the template's " + filled);
			helper.assertTrue(counted.minY() == helper.absolutePos(new BlockPos(0, 2, 0)).getY(), turn + ": it sits on the ground");
			template.placeInWorld(level, placement.origin(), placement.origin(), settings, RandomSource.create(7), 2);
			List<String> floats = floating(level, counted);
			helper.assertTrue(floats.isEmpty(), turn + ": blocks float: " + floats);
			List<String> pops = unstable(level, counted);
			helper.assertTrue(pops.isEmpty(), turn + ": blocks that can't stay: " + pops);
			for (BlockPos p : BlockPos.betweenClosed(counted.minX(), counted.minY(), counted.minZ(), counted.maxX(), counted.maxY(), counted.maxZ())) {
				BlockState s = level.getBlockState(p);
				helper.assertFalse(PoiTypes.forState(s).isPresent(), turn + ": a villager's point of interest in the statue: " + s);
				helper.assertFalse(level.getBlockEntity(p) instanceof Container, turn + ": a container in the statue: " + s);
			}
			// the head: the top three cells of the middle column
			BlockPos middle = counted.getCenter();
			helper.assertTrue(counted.getXSpan() % 2 == 1 && counted.getZSpan() % 2 == 1, turn + ": odd both ways once turned");
			List<BlockPos> head = List.of(new BlockPos(middle.getX(), counted.maxY(), middle.getZ()), new BlockPos(middle.getX(), counted.maxY() - 1, middle.getZ()),
				new BlockPos(middle.getX(), counted.maxY() - 2, middle.getZ()));
			helper.assertTrue(head.stream().anyMatch(p -> !level.getBlockState(p).isAir()), turn + ": the head is on the middle column in the top three layers");
			BuildSiteManager.get(level).recordFinished(Founder.STATUE, placement, owner);
			BuildSiteManager.Finished finished = new BuildSiteManager.Finished(Founder.STATUE, placement, owner);
			recorded.add(finished);
			Founder.forgetStands();
			helper.assertTrue(Founder.stands(level, hall), turn + ": the finished statue stands");
			// a block off the head: the plinth's corner
			BlockPos off = null;
			for (BlockPos p : BlockPos.betweenClosed(counted.minX(), counted.minY(), counted.minZ(), counted.maxX(), counted.maxY(), counted.maxZ())) {
				if (!level.getBlockState(p).isAir() && (p.getX() != middle.getX() || p.getZ() != middle.getZ())) {
					off = p.immutable();
					break;
				}
			}
			if (off != null) {
				level.setBlock(off, Blocks.AIR.defaultBlockState(), 2);
				Founder.forgetStands();
				helper.assertTrue(Founder.stands(level, hall), turn + ": a block broken off the base (" + off + ") doesn't topple it");
			}
			head.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 2));
			Founder.forgetStands();
			helper.assertFalse(Founder.stands(level, hall), turn + ": the head knocked off, it no longer stands");
			BuildSiteManager.get(level).forgetFinished(placement);
		}
		helper.succeed();
	}

	/**
	 * The rebuilt Shift Bell on planks with three patches of grass: a 3 x 3 with one cell a block high and a 3 x 3 with a
	 * block 5 above it don't take it; a 3 x 3 with a block 6 above it does, exactly there; nothing lands outside its 3 x 3
	 * x 6 or under it; exactly one bell, at (1, 3, 1), that stays, rings for the village and rings by hand from an open side.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRebuiltBell1121d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRebuiltBell1121d")
	public void qaTheShiftBellTakesTheNearestTrueSpotAndRings(GameTestHelper helper) {
		Leftovers.clear(helper);
		wipe(helper, Blocks.OAK_PLANKS);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		// A: ring 4 east, its far corner a block high
		BlockPos a = new BlockPos(19, 1, 15);
		// B: ring 7 west, a block 5 above its middle (inside the 6 of air the bell needs)
		BlockPos b = new BlockPos(8, 1, 15);
		// C: ring 9 south, a block 6 above its middle (just past it)
		BlockPos c = new BlockPos(15, 1, 24);
		for (BlockPos patch : List.of(a, b, c)) {
			for (int x = 0; x < 3; x++) {
				for (int z = 0; z < 3; z++) {
					helper.setBlock(patch.offset(x, 0, z), Blocks.GRASS_BLOCK);
				}
			}
		}
		helper.setBlock(a.offset(2, 1, 2), Blocks.DIRT);
		helper.setBlock(b.offset(1, 6, 1), Blocks.STONE);
		helper.setBlock(c.offset(1, 7, 1), Blocks.STONE);
		BlockPos origin = helper.absolutePos(c.above());
		BoundingBox box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 2, origin.getY() + 5, origin.getZ() + 2);
		Map<BlockPos, BlockState> around = new HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3, -2, -3), origin.offset(5, 8, 5))) {
			if (!box.isInside(p)) {
				around.put(p.immutable(), level.getBlockState(p));
			}
		}
		Map<BlockPos, BlockState> ab = new HashMap<>();
		for (BlockPos patch : List.of(a, b)) {
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(patch), helper.absolutePos(patch.offset(2, 7, 2)))) {
				ab.put(p.immutable(), level.getBlockState(p));
			}
		}
		StructureTemplate template = level.getStructureManager().get(ReformStories.BELL).orElse(null);
		helper.assertTrue(template != null, "the Shift Bell's template loads");
		helper.assertTrue(template.getSize().getX() <= 3 && template.getSize().getY() <= 6 && template.getSize().getZ() <= 3,
			"the Shift Bell fits 3 x 6 x 3: " + template.getSize());
		BlockPos bell = ReformStories.placeBell(level, hall);
		helper.assertTrue(bell != null, "the bell is raised");
		helper.assertTrue(bell.equals(origin.offset(1, 3, 1)), "on the nearest true 3 x 3 (south, past the high corner and the low block): " + bell
			+ ", expected " + origin.offset(1, 3, 1));
		ab.forEach((p, s) -> helper.assertTrue(level.getBlockState(p) == s, "the refused patches are left alone at " + p));
		around.forEach((p, s) -> helper.assertTrue(level.getBlockState(p) == s, "nothing lands outside the bell's 3 x 3 x 6 or under it: " + p + " is "
			+ level.getBlockState(p) + ", was " + s));
		helper.assertTrue(level.getBlockState(bell).is(Blocks.BELL), "a bell at (1, 3, 1): " + level.getBlockState(bell));
		int bells = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(p).is(Blocks.BELL)) {
				bells++;
			}
		}
		helper.assertTrue(bells == 1, "exactly one bell: " + bells);
		List<String> floats = floating(level, box);
		helper.assertTrue(floats.isEmpty(), "the bell's blocks float: " + floats);
		List<String> pops = unstable(level, box);
		helper.assertTrue(pops.isEmpty(), "the bell's blocks can't stay: " + pops);
		helper.assertTrue(ReformStories.ring(level, bell), "the village rings it");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(level.getBlockState(bell).is(Blocks.BELL), "the bell is still there");
			List<Direction> rang = new ArrayList<>();
			List<Direction> open = new ArrayList<>();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = bell.relative(d);
				if (!clear(level, side)) {
					continue;
				}
				open.add(d);
				if (level.getBlockEntity(bell) instanceof BellBlockEntity entity) {
					entity.shaking = false;
				}
				player.moveTo(bell.getX() + 0.5 + d.getStepX() * 2, bell.getY() - 1, bell.getZ() + 0.5 + d.getStepZ() * 2);
				Vec3 hit = Vec3.atCenterOf(bell).add(d.getStepX() * 0.5, 0, d.getStepZ() * 0.5);
				player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(hit, d, bell, false));
				if (level.getBlockEntity(bell) instanceof BellBlockEntity entity && entity.shaking) {
					rang.add(d);
				}
			}
			helper.assertFalse(rang.isEmpty(), "a player rings the bell by hand from an open side (open: " + open + ", "
				+ level.getBlockState(bell) + ")");
			helper.succeed();
		});
	}
}
