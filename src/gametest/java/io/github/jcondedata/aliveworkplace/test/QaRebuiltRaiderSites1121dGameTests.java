package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import io.github.jcondedata.aliveworkplace.story.CaravanTrail;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane d, 2026-11-21) of 52.5's rebuilt raider sites, written from the briefs ({@code tools/blueprints/briefs/}
 * bandit_camp, raiders_stockade, bandit_fort, caravan_wreck) and the wiki (lairs, lost-caravan, bandit-king), not from
 * the code: what the existing tests and QaThreatSites1121dGameTests leave out. A new camp is a working lair (chief and
 * band on their spots and in no block, one loot chest with the culture's loot, open sky over its one fire); a King's camp
 * in every turn flies its war banner and has ground for a bounty band and pole; a camp of the old drawing comes down
 * across a reload but leaves a player's block and his own filled chest, spills its own chests and gives the land back; the
 * stockade's guards land on the ground and it has one chest and one iron door; every turn of the fort keeps its men, King
 * and horn bandits out of walls and its keep's treasure and postern working; the wreck carries no loot or mob and stays
 * inside the road's check.
 */
public class QaRebuiltRaiderSites1121dGameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos MIDDLE = new BlockPos(15, 3, 15);
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(15, 1, 15);

	/** The camp's spots (brief, "Must have"): the fire, the chief, the band, the bounty pole. */
	private static final BlockPos FIRE = new BlockPos(7, 1, 6);
	private static final BlockPos CHIEF = new BlockPos(7, 1, 9);
	private static final List<BlockPos> BAND = List.of(new BlockPos(4, 1, 7), new BlockPos(10, 1, 7), new BlockPos(7, 1, 3), new BlockPos(6, 1, 4),
		new BlockPos(8, 1, 4), new BlockPos(3, 1, 6), new BlockPos(11, 1, 6), new BlockPos(8, 1, 3));
	private static final BlockPos POLE = new BlockPos(9, 1, 6);
	private static final ResourceKey<LootTable> CAMP_LOOT = ResourceKey.create(Registries.LOOT_TABLE, AliveWorkplace.id("chests/bandit_camp"));
	/** The drawing on the owner's server before 52.5. */
	private static final String OLD = "v0.150.0";

	/** The fort's spots (bandit_fort brief, "Fixed spots the Java reads"). */
	private static final BlockPos YARD = new BlockPos(14, 1, 14);
	private static final List<BlockPos> YARD_MEN = List.of(new BlockPos(12, 1, 8), new BlockPos(16, 1, 8), new BlockPos(11, 1, 14), new BlockPos(17, 1, 14));
	private static final List<BlockPos> TOWER_MEN = List.of(new BlockPos(2, 6, 2), new BlockPos(26, 6, 2));
	private static final BlockPos THRONE = new BlockPos(14, 1, 21);

	// ---------- shared ----------

	/** Grass at helper y 3 over dirt, nothing above it, and no barrier roof (the height map must find the ground). */
	private static void flatGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 29; y >= 4; y--) {
					BlockPos p = new BlockPos(x, y, z);
					if (!helper.getBlockState(p).isAir()) {
						level.removeBlockEntity(helper.absolutePos(p));
						level.setBlock(helper.absolutePos(p), Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				}
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	private static void arcsNear(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int near = Arcs.NEAR;
		Arcs.NEAR = 0; // (the tests build the places themselves)
		Leftovers.after(helper, () -> Arcs.NEAR = near);
	}

	private static StructurePlaceSettings turn(int r) {
		return new StructurePlaceSettings().setRotation(Rotation.values()[r]);
	}

	private static BlockPos cell(BlockPos origin, int r, BlockPos local) {
		return origin.offset(StructureTemplate.calculateRelativePosition(turn(r), local));
	}

	private static boolean free(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty();
	}

	/** Something a mob stands on: a block with a sturdy top, or one nearly full (a dirt path, a farmland edge). */
	private static boolean floor(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		var shape = s.getCollisionShape(level, p);
		return s.isFaceSturdy(level, p, Direction.UP) || (!shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.9);
	}

	/** A cell a mob may stand in: nothing with collision, or only a floor covering such as a carpet (the brief: "air or a carpet"). */
	private static boolean thin(ServerLevel level, BlockPos p) {
		var shape = level.getBlockState(p).getCollisionShape(level, p);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.1875;
	}

	/** A man standing at {@code p}: feet and head free, a solid floor under him. */
	private static boolean standable(ServerLevel level, BlockPos p) {
		return free(level, p) && free(level, p.above()) && floor(level, p.below());
	}

	/** Is any of the mob's body in a block (the cells its box covers)? */
	private static boolean inBlock(ServerLevel level, Mob mob) {
		AABB b = mob.getBoundingBox().deflate(0.05);
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(b.minX, b.minY, b.minZ), BlockPos.containing(b.maxX, b.maxY, b.maxZ))) {
			if (!thin(level, p) && level.getBlockState(p).getCollisionShape(level, p).bounds().move(p).intersects(b)) {
				return true;
			}
		}
		return false;
	}

	private static Blueprint drawing(ServerLevel level, ResourceLocation id) {
		StructureTemplate template = level.getStructureManager().get(id).orElseThrow(() -> new IllegalStateException("no template " + id));
		return Blueprint.fromTemplate(id, template, Lookup.lookup(BuiltInRegistries.BLOCK));
	}

	private static String rel(GameTestHelper helper, BlockPos p) {
		return helper.relativePos(p).toShortString();
	}

	// ---------- the Bandit Camp as a lair ----------

	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		BanditCamps.forget(level);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
		});
		return hall;
	}

	private static BlockPos campOrigin(BlockPos middle, int r) {
		return middle.subtract(StructureTemplate.calculateRelativePosition(turn(r), FIRE));
	}

	private static BlockPos at(BlockPos middle, int r, BlockPos local) {
		return cell(campOrigin(middle, r), r, local);
	}

	private static List<Blueprint.Entry> standing(Blueprint d) {
		return d.blocks().stream().filter(e -> e.pos().getY() >= 1 && !e.state().isAir()).toList();
	}

	private static BlockState turned(Blueprint.Entry e, int r) {
		return e.state().mirror(Mirror.NONE).rotate(Rotation.values()[r]);
	}

	private static int asDrawn(ServerLevel level, Blueprint d, BlockPos middle, int r) {
		int n = 0;
		for (Blueprint.Entry e : standing(d)) {
			if (MaterialRules.matches(level.getBlockState(at(middle, r, e.pos())), turned(e, r))) {
				n++;
			}
		}
		return n;
	}

	private static int turnOf(GameTestHelper helper, Blueprint d, BlockPos middle) {
		for (int r = 0; r < 4; r++) {
			if (asDrawn(helper.getLevel(), d, middle, r) == standing(d).size()) {
				return r;
			}
		}
		throw new IllegalStateException("today's camp doesn't stand whole round " + helper.relativePos(middle));
	}

	/** Every container round a camp's fire (9 across, the camp's height) that carries a loot table. */
	private static List<BlockPos> lootContainers(ServerLevel level, BlockPos fire) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(fire.offset(-9, -2, -9), fire.offset(9, 11, 9))) {
			if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity c && c.getLootTable() != null) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	private static int campfires(ServerLevel level, BlockPos fire) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(fire.offset(-9, -2, -9), fire.offset(9, 11, 9))) {
			if (level.getBlockState(p).getBlock() instanceof CampfireBlock) {
				n++;
			}
		}
		return n;
	}

	/**
	 * A camp the bandits make with today's drawing (the player's real path: {@code BanditCamps.found}): its chief stands on
	 * his spot and every man of the band on one of the band's spots, none of them in a block and all within the lair's 14
	 * blocks of the fire; the fire is the only campfire within 9 and has open sky over it (the old-world sweep finds it by
	 * that); exactly one container carries a loot table, a plain chest within 8 of the fire at the camp's y 1 to 4, and
	 * its table is the bandits' ("the chest in the chief's tent is yours"); the second chest has none.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaNewCampLair1121d"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaNewCampLair1121d")
	public void qaANewCampIsAWorkingLair(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp was made");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			BlockPos fire = lair.pos();
			Blueprint today = drawing(level, Lairs.BANDIT_CAMP);
			int r = turnOf(helper, today, fire);
			List<String> problems = new ArrayList<>();
			helper.assertTrue(level.getBlockState(fire).getBlock() instanceof CampfireBlock && level.getBlockState(fire).getValue(CampfireBlock.LIT),
				"turn " + r + ": the lair's middle isn't a lit campfire: " + level.getBlockState(fire));
			if (campfires(level, fire) != 1) {
				problems.add(campfires(level, fire) + " campfires within 9 of the fire, not 1");
			}
			for (int y = 1; y <= 11; y++) {
				if (!level.getBlockState(fire.above(y)).getCollisionShape(level, fire.above(y)).isEmpty()) {
					problems.add("a block over the fire at +" + y + ": " + level.getBlockState(fire.above(y)));
				}
			}
			if (!(level.getEntity(lair.captain()) instanceof Mob chief)) {
				throw new IllegalStateException("turn " + r + ": the lair has no chief");
			} else {
				BlockPos spot = at(fire, r, CHIEF);
				if (!chief.blockPosition().equals(spot)) {
					problems.add("the chief stands at " + rel(helper, chief.blockPosition()) + ", not on his spot " + rel(helper, spot));
				}
				if (inBlock(level, chief)) {
					problems.add("the chief is in a block at " + rel(helper, chief.blockPosition()));
				}
			}
			Set<BlockPos> spots = new HashSet<>();
			for (BlockPos b : BAND) {
				spots.add(at(fire, r, b));
			}
			List<Mob> band = Lairs.band(level, lair).stream().filter(m -> !m.getUUID().equals(lair.captain())).toList();
			if (band.isEmpty()) {
				problems.add("no band stands at the camp");
			}
			for (Mob m : band) {
				if (!spots.contains(m.blockPosition())) {
					problems.add("a man of the band stands at " + rel(helper, m.blockPosition()) + ", on none of the band's spots");
				}
				if (inBlock(level, m)) {
					problems.add("a man of the band is in a block at " + rel(helper, m.blockPosition()) + ": " + level.getBlockState(m.blockPosition()));
				}
				if (Math.sqrt(m.blockPosition().distSqr(fire)) > 14) {
					problems.add("a man of the band stands " + Math.round(Math.sqrt(m.blockPosition().distSqr(fire))) + " from the fire");
				}
			}
			List<BlockPos> loot = lootContainers(level, fire);
			if (loot.size() != 1) {
				problems.add(loot.size() + " containers carry a loot table, not 1: " + loot.stream().map(p -> rel(helper, p)).toList());
			} else {
				BlockPos c = loot.get(0);
				int dy = c.getY() - fire.getY() + 1;
				if (!level.getBlockState(c).is(Blocks.CHEST) || Math.abs(c.getX() - fire.getX()) > 8 || Math.abs(c.getZ() - fire.getZ()) > 8 || dy < 1 || dy > 4) {
					problems.add("the loot container is " + level.getBlockState(c) + " at " + rel(helper, c) + " (y " + dy + "), not a plain chest within 8 of the fire at y 1-4");
				}
				if (!(level.getBlockEntity(c) instanceof ChestBlockEntity chest) || !CAMP_LOOT.equals(chest.getLootTable())) {
					problems.add("the loot chest's table isn't the bandits': " + (level.getBlockEntity(c) instanceof ChestBlockEntity ch ? ch.getLootTable() : "none"));
				}
			}
			int chests = 0;
			for (BlockPos p : BlockPos.betweenClosed(fire.offset(-9, -1, -9), fire.offset(9, 6, 9))) {
				if (level.getBlockState(p).is(Blocks.CHEST)) {
					chests++;
				}
			}
			if (chests != 2) {
				problems.add(chests + " chests round the fire, not 2");
			}
			helper.assertTrue(problems.isEmpty(), "turn " + r + ": " + String.join("; ", problems));
			helper.succeed();
		});
	}

	/**
	 * The Bandit King's camp, set down by his arc in each of the four turns it may get on flat grass: the arc raises his war
	 * banner beside it (with none, chapter 1 can't end), the file's own loot chest holds the bandits' table (no lair pass
	 * fills it), and a bounty's hideout of the same drawing has open ground round the fire where its band of six is stood,
	 * its captain's spot open and ground for his banner pole within 3 of the pole spot.
	 */
	//$ gametest_ticks_batch TALL '120' '"qaKingCampTurns1121d"'
	@GameTest(template = TALL, timeoutTicks = 120, batch = "qaKingCampTurns1121d")
	public void qaTheKingsCampFliesItsBannerInEveryTurn(GameTestHelper helper) {
		arcsNear(helper);
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		for (int r = 0; r < 4; r++) {
			flatGround(helper);
			ArcState s = new ArcState(BanditKingStory.ARC.toString());
			ArcState.ArcPlace p = new ArcState.ArcPlace("first_camp", helper.absolutePos(MIDDLE), List.of(Lairs.BANDIT_CAMP.toString()), r);
			s.places.add(p);
			helper.assertTrue(ArcEffects.build(level, s, p) && p.placed == 1 && !p.skipped && p.origin != null, "turn " + r + ": the King's camp wasn't built");
			BlockPos fire = cell(p.origin, r, FIRE);
			if (!(level.getBlockState(fire).getBlock() instanceof CampfireBlock)) {
				problems.add("turn " + r + ": no fire at the camp's middle: " + level.getBlockState(fire));
				continue;
			}
			BlockPos banner = null;
			for (BlockPos q : BlockPos.betweenClosed(fire.offset(-7, -3, -7), fire.offset(7, 4, 7))) {
				if (BanditKingStory.isWarBanner(level, q)) {
					banner = q.immutable();
				}
			}
			if (banner == null) {
				problems.add("turn " + r + ": no war banner was raised by the camp");
			}
			List<BlockPos> loot = lootContainers(level, fire);
			if (loot.size() != 1 || !(level.getBlockEntity(loot.get(0)) instanceof ChestBlockEntity chest) || !CAMP_LOOT.equals(chest.getLootTable())) {
				problems.add("turn " + r + ": the camp's loot chest doesn't carry the bandits' table: " + loot.stream().map(q -> rel(helper, q)).toList());
			}
			if (!standable(level, cell(p.origin, r, CHIEF))) {
				problems.add("turn " + r + ": the captain's spot isn't open ground: " + level.getBlockState(cell(p.origin, r, CHIEF)));
			}
			for (int i = 0; i < 6; i++) {
				double angle = i * 2.399963;
				BlockPos man = fire.offset((int) Math.round(Math.cos(angle) * 3), 0, (int) Math.round(Math.sin(angle) * 3));
				if (!standable(level, man)) {
					problems.add("turn " + r + ": the bounty band's ground " + (man.getX() - fire.getX()) + "," + (man.getZ() - fire.getZ())
						+ " from the fire isn't open (" + level.getBlockState(man) + " on " + level.getBlockState(man.below()) + ")");
				}
			}
			BlockPos pole = cell(p.origin, r, POLE);
			boolean poleGround = false;
			for (BlockPos q : BlockPos.betweenClosed(pole.offset(-3, -1, -3), pole.offset(3, 2, 3))) {
				if (level.getBlockState(q).canBeReplaced() && level.getBlockState(q.above()).canBeReplaced() && floor(level, q.below())) {
					poleGround = true;
					break;
				}
			}
			if (!poleGround) {
				problems.add("turn " + r + ": no ground for the bounty captain's banner pole within 3 of " + rel(helper, pole));
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A camp of the old drawing (as on the owner's server before 52.5), its two chests full of a raider's takings, a
	 * player's gold block in place of one of its wool blocks and the player's own chest of diamonds set in the camp. Its
	 * chief falls, and the game is saved and loaded halfway down. All of the old camp comes down; the player's gold block
	 * and his chest, diamonds still in it, stay; the camp's own chests' items lie on the land, none lost; the camp's ground
	 * at the fire is the land's again.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaOldCampPlayers1121d"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaOldCampPlayers1121d")
	public void qaAnOldCampLeavesThePlayersBlocksAcrossAReload(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			BlockState land = level.getBlockState(helper.absolutePos(CAMP));
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp was made");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			BlockPos middle = lair.pos();
			Blueprint today = drawing(level, Lairs.BANDIT_CAMP);
			int r = turnOf(helper, today, middle);
			Blueprint old = Refreshes.olderDrawing(level.getServer(), Lairs.BANDIT_CAMP, OLD).orElseThrow();
			int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
			for (Blueprint.Entry e : standing(today)) {
				level.removeBlockEntity(at(middle, r, e.pos()));
				level.setBlock(at(middle, r, e.pos()), Blocks.AIR.defaultBlockState(), flags);
			}
			for (Blueprint.Entry e : old.blocks()) {
				if (!e.state().isAir()) {
					level.setBlock(at(middle, r, e.pos()), turned(e, r), flags);
				}
			}
			int whole = standing(old).size();
			helper.assertTrue(asDrawn(level, old, middle, r) == whole, "setup: the old camp doesn't stand whole");
			int campChests = 0;
			for (Blueprint.Entry e : standing(old)) {
				if (e.state().is(Blocks.CHEST) && level.getBlockEntity(at(middle, r, e.pos())) instanceof Container c) {
					c.clearContent();
					c.setItem(0, new ItemStack(Items.EMERALD, 9));
					campChests++;
				}
			}
			helper.assertTrue(campChests == 2, "setup: the old camp has " + campChests + " chests");
			Blueprint.Entry wool = standing(old).stream().filter(e -> e.state().is(BlockTags.WOOL)).findFirst().orElseThrow();
			BlockPos gold = at(middle, r, wool.pos());
			level.setBlock(gold, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
			BlockPos own = at(middle, r, BAND.get(2));
			helper.assertTrue(level.getBlockState(own).isAir(), "setup: the band's spot " + BAND.get(2) + " isn't open in the old camp: " + level.getBlockState(own));
			level.setBlock(own, Blocks.CHEST.defaultBlockState(), 3);
			helper.assertTrue(level.getBlockEntity(own) instanceof Container, "setup: the player's chest has no contents");
			((Container) level.getBlockEntity(own)).setItem(0, new ItemStack(Items.DIAMOND, 3));
			if (level.getEntity(lair.captain()) instanceof LivingEntity chief) {
				chief.kill();
			}
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && CampCleanup.clearing(level).size() == 1, "the old camp isn't coming down: " + CampCleanup.clearing(level));
			helper.runAfterDelay(CampCleanup.DELAY + CampCleanup.CLEAR_TICKS / 2, () -> {
				Lairs.Data.get(level).roundTrip(level.registryAccess());
				helper.assertTrue(CampCleanup.clearing(level).size() == 1 && OLD.equals(CampCleanup.clearing(level).get(0).drawing()),
					"after a reload halfway the camp isn't known as the old drawing: " + CampCleanup.clearing(level));
				helper.runAfterDelay(CampCleanup.CLEAR_TICKS / 2 + 15, () -> {
					List<String> problems = new ArrayList<>();
					int left = 0;
					for (Blueprint.Entry e : standing(old)) {
						if (!e.pos().equals(wool.pos()) && MaterialRules.matches(level.getBlockState(at(middle, r, e.pos())), turned(e, r))) {
							left++;
						}
					}
					if (left > 0) {
						problems.add(left + " of the old camp's " + whole + " blocks still stand");
					}
					if (!level.getBlockState(gold).is(Blocks.GOLD_BLOCK)) {
						problems.add("the player's gold block went: " + level.getBlockState(gold));
					}
					if (!(level.getBlockEntity(own) instanceof Container c) || c.countItem(Items.DIAMOND) != 3) {
						problems.add("the player's own chest of diamonds went or was emptied: " + level.getBlockState(own));
					}
					AABB around = new AABB(middle).inflate(12);
					int emeralds = level.getEntitiesOfClass(ItemEntity.class, around, i -> i.getItem().is(Items.EMERALD)).stream().mapToInt(i -> i.getItem().getCount()).sum();
					int diamonds = level.getEntitiesOfClass(ItemEntity.class, around, i -> i.getItem().is(Items.DIAMOND)).stream().mapToInt(i -> i.getItem().getCount()).sum();
					if (emeralds != 18) {
						problems.add(emeralds + " of the camp chests' 18 emeralds lie on the land");
					}
					if (diamonds != 0) {
						problems.add(diamonds + " of the player's diamonds were spilled");
					}
					if (!level.getBlockState(middle.below()).equals(land)) {
						problems.add("the camp's ground isn't back to the land's: " + level.getBlockState(middle.below()) + ", was " + land);
					}
					if (!CampCleanup.clearing(level).isEmpty()) {
						problems.add("the clearing never ended");
					}
					helper.assertTrue(problems.isEmpty(), "turn " + r + ": " + String.join("; ", problems));
					helper.succeed();
				});
			});
		});
	}

	// ---------- the Raiders' Stockade ----------

	/**
	 * The stockade set down turned a quarter: each of the five guards spawns on the top block of a column within 3 of its
	 * middle, so no roof or canopy may stand over those 49 columns (a guard would land on it); and the story finds its
	 * chest and cage door as the nearest of their kind, so there is one plain chest, one iron door and one lever.
	 */
	//$ gametest_ticks_batch TALL '60' '"qaStockadeGuards1121d"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaStockadeGuards1121d")
	public void qaTheStockadesGuardsLandOnTheGround(GameTestHelper helper) {
		arcsNear(helper);
		flatGround(helper);
		ServerLevel level = helper.getLevel();
		int r = 1;
		ArcState s = new ArcState("aliveworkplace:lost_caravan");
		ArcState.ArcPlace p = new ArcState.ArcPlace("piece", helper.absolutePos(MIDDLE), List.of(CaravanTrail.STOCKADE.toString()), r);
		s.places.add(p);
		helper.assertTrue(ArcEffects.build(level, s, p) && p.placed == 1 && !p.skipped && p.origin != null, "the stockade wasn't built");
		List<String> problems = new ArrayList<>();
		BlockPos middle = p.spot;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.offset(dx, 0, dz));
				if (top.getY() > middle.getY() + 1) {
					problems.add("a guard lands " + (top.getY() - middle.getY() + 1) + " up at " + dx + "," + dz + " (on " + level.getBlockState(top.below()) + ")");
				}
			}
		}
		int chests = 0;
		int doors = 0;
		int levers = 0;
		BlockPos a = cell(p.origin, r, new BlockPos(0, 0, 0));
		BlockPos b = cell(p.origin, r, new BlockPos(18, 13, 16));
		for (BlockPos q : BlockPos.betweenClosed(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()))) {
			BlockState st = level.getBlockState(q);
			if (st.is(Blocks.CHEST)) {
				chests++;
			} else if (st.is(Blocks.IRON_DOOR) && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				doors++;
			} else if (st.is(Blocks.LEVER)) {
				levers++;
			}
		}
		if (chests != 1 || doors != 1 || levers != 1) {
			problems.add(chests + " plain chests, " + doors + " iron doors, " + levers + " levers (one of each)");
		}
		if (!(level.getBlockEntity(cell(p.origin, r, CaravanTrail.LOOT_CHEST)) instanceof ChestBlockEntity chest) || !CAMP_LOOT.equals(chest.getLootTable())) {
			problems.add("turned, the stockade's chest doesn't carry the bandits' loot");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems.subList(0, Math.min(10, problems.size()))) + (problems.size() > 10 ? " (" + problems.size() + " in all)" : ""));
		helper.succeed();
	}

	// ---------- the Bandit King's fort ----------

	/** Uneven ground: grass at y 3, a two-step hill and a hole two deep, set so a turned fort takes them in elsewhere. */
	private static void fortGround(GameTestHelper helper) {
		flatGround(helper);
		for (int x = 6; x <= 9; x++) {
			for (int z = 20; z <= 23; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 5, z), Blocks.GRASS_BLOCK);
			}
		}
		for (int x = 20; x <= 23; x++) {
			for (int z = 5; z <= 8; z++) {
				helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
			}
		}
	}

	/**
	 * Every stage of the fort, set down in turn {@code r} on uneven ground: after I the ground under all 29 x 29 columns
	 * holds and four men stand on the yard spots in no block; after II a pillager stands on each tower deck, on a floor, in
	 * no block; after III the King stands before his throne in no block, the 3 x 3 before the keep's door is open yard,
	 * the treasure chest holds the King's loot, the postern is a shut door that opens both halves; and at half health the
	 * King's horn calls four bandits, none of them inside a block.
	 */
	private static void fortTurn(GameTestHelper helper, int r) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(16), m -> m.getTags().contains(BanditFort.TAG)).forEach(Entity::discard));
		fortGround(helper);
		Rotation rotation = Rotation.values()[r];
		BanditFort.Fort fort = BanditFort.at(helper.absolutePos(MIDDLE), rotation);
		List<String> problems = new ArrayList<>();
		helper.assertTrue(BanditFort.place(level, fort, 0, false), "stage I missing");
		for (int x = 0; x < 29; x++) {
			for (int z = 0; z < 29; z++) {
				BlockPos g = fort.at(new BlockPos(x, 0, z));
				if (level.getBlockState(g).isAir() || level.getBlockState(g.below()).isAir()) {
					problems.add("open ground under (" + x + ", " + z + ")");
				}
			}
		}
		Set<BlockPos> yardSpots = new HashSet<>();
		YARD_MEN.forEach(c -> yardSpots.add(fort.at(c)));
		List<Mob> men = BanditFort.men(level, fort.yard(), 24);
		if (men.size() != 4) {
			problems.add("after I, " + men.size() + " men, not 4");
		}
		for (Mob m : men) {
			if (!yardSpots.contains(m.blockPosition()) || inBlock(level, m) || !floor(level, m.blockPosition().below())) {
				problems.add("after I, a man at " + rel(helper, m.blockPosition()) + " isn't standing free on a yard spot (" + level.getBlockState(m.blockPosition()) + ")");
			}
		}
		helper.assertTrue(BanditFort.place(level, fort, 1, false), "stage II missing");
		for (BlockPos c : TOWER_MEN) {
			BlockPos w = fort.at(c);
			List<Mob> on = level.getEntitiesOfClass(Mob.class, new AABB(w), m -> m instanceof Pillager && m.getTags().contains(BanditFort.TAG));
			if (on.isEmpty() || !floor(level, w.below()) || on.stream().anyMatch(m -> inBlock(level, m))) {
				problems.add("after II, no pillager stands free on the tower deck " + c + " (" + level.getBlockState(w) + " on " + level.getBlockState(w.below()) + ")");
			}
		}
		for (Mob m : BanditFort.men(level, fort.yard(), 24)) {
			if (inBlock(level, m)) {
				problems.add("after II, a man is in a block at " + rel(helper, m.blockPosition()) + ": " + level.getBlockState(m.blockPosition()));
			}
		}
		helper.assertTrue(BanditFort.place(level, fort, 2, false), "stage III missing");
		List<Mob> kings = BanditFort.men(level, fort.yard(), 30).stream().filter(m -> m.getTags().contains(BanditKing.TAG)).toList();
		helper.assertTrue(kings.size() == 1, "after III, " + kings.size() + " Kings");
		Mob king = kings.get(0);
		if (!king.blockPosition().equals(fort.at(THRONE)) || inBlock(level, king) || !floor(level, fort.at(THRONE).below())) {
			problems.add("the King isn't standing free before his throne: at " + rel(helper, king.blockPosition()) + " (" + level.getBlockState(king.blockPosition()) + ")");
		}
		for (Mob m : BanditFort.men(level, fort.yard(), 30)) {
			if (inBlock(level, m)) {
				problems.add("after III, a man is in a block at " + rel(helper, m.blockPosition()) + ": " + level.getBlockState(m.blockPosition()));
			}
		}
		for (int x = 13; x <= 15; x++) {
			for (int z = 14; z <= 16; z++) {
				BlockPos w = fort.at(new BlockPos(x, 1, z));
				if (!standable(level, w)) {
					problems.add("before the keep's door (" + x + ", 1, " + z + ") isn't open yard: " + level.getBlockState(w) + " / " + level.getBlockState(w.above()) + " on " + level.getBlockState(w.below()));
				}
			}
		}
		if (!free(level, fort.at(YARD)) || !free(level, fort.at(YARD).above())) {
			problems.add("the yard's middle isn't open");
		}
		if (!(level.getBlockEntity(fort.at(BanditFort.TREASURE)) instanceof ChestBlockEntity chest) || !BanditFort.LOOT.equals(chest.getLootTable())) {
			problems.add("the treasure chest doesn't hold the King's loot");
		}
		BlockPos postern = fort.at(BanditFort.POSTERN);
		BlockState lo = level.getBlockState(postern);
		BlockState hi = level.getBlockState(postern.above());
		if (!(lo.getBlock() instanceof DoorBlock) || !(hi.getBlock() instanceof DoorBlock) || lo.getValue(DoorBlock.OPEN) || hi.getValue(DoorBlock.OPEN)) {
			problems.add("the postern isn't a shut door: " + lo + " / " + hi);
		} else {
			BanditFort.setPostern(level, fort, true);
			if (!level.getBlockState(postern).getValue(DoorBlock.OPEN) || !level.getBlockState(postern.above()).getValue(DoorBlock.OPEN)) {
				problems.add("the postern didn't open both halves");
			}
		}
		king.setHealth(king.getMaxHealth() / 2 - 1);
		BanditFort.tick(level);
		List<Mob> called = level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(8), m -> m.isAlive() && m.getTags().contains(BanditKing.CALLED_TAG));
		if (called.size() != BanditKing.CALLED) {
			problems.add("the horn called " + called.size() + " bandits, not " + BanditKing.CALLED);
		}
		for (Mob m : called) {
			if (inBlock(level, m)) {
				problems.add("a bandit the horn called stands in a block at " + rel(helper, m.blockPosition()) + ": " + level.getBlockState(m.blockPosition()));
			}
		}
		helper.assertTrue(problems.isEmpty(), "turn " + r + ": " + String.join("; ", problems.subList(0, Math.min(10, problems.size()))) + (problems.size() > 10 ? " (" + problems.size() + " in all)" : ""));
		helper.succeed();
	}

	//$ gametest_ticks_batch TALL '60' '"qaFortTurn1_1121d"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaFortTurn1_1121d")
	public void qaTheFortWorksTurnedOnce(GameTestHelper helper) {
		fortTurn(helper, 1);
	}

	//$ gametest_ticks_batch TALL '60' '"qaFortTurn2_1121d"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaFortTurn2_1121d")
	public void qaTheFortWorksTurnedTwice(GameTestHelper helper) {
		fortTurn(helper, 2);
	}

	//$ gametest_ticks_batch TALL '60' '"qaFortTurn3_1121d"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaFortTurn3_1121d")
	public void qaTheFortWorksTurnedThrice(GameTestHelper helper) {
		fortTurn(helper, 3);
	}

	// ---------- the Caravan Wreck ----------

	private static boolean holdsALantern(BlockState below) {
		Block b = below.getBlock();
		return b instanceof SlabBlock || b instanceof FenceBlock || b instanceof WallBlock || b instanceof ChainBlock || b instanceof TrapDoorBlock
			|| b instanceof StairBlock || below.is(Blocks.LIGHTNING_ROD) || below.is(Blocks.END_ROD) || below.is(Blocks.BARREL) || below.is(Blocks.COMPOSTER)
			|| below.is(Blocks.CHEST) || below.is(BlockTags.FENCES) || below.is(BlockTags.WALLS);
	}

	/**
	 * The wreck's file (caravan_wreck brief): no mob or other entity in it (nothing is spawned there and nothing would be
	 * cleared), no container carries a loot table (the lost goods are in the stockade's chest), it covers at least 78
	 * columns but no more than the road check's 13 x 13 x 7 box, its ground layer saves no air, and no lantern stands on
	 * the bare ground (each hangs or stands on a slab, post, rod or piece of furniture).
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void qaTheWrecksFileHoldsNoLootNoMobAndFitsTheRoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(CaravanTrail.WRECK).orElseThrow();
		List<String> problems = new ArrayList<>();
		var size = template.getSize();
		if (size.getX() * size.getZ() < 78 || size.getX() > 13 || size.getZ() > 13 || size.getY() > 7) {
			problems.add("the wreck is " + size.getX() + " x " + size.getY() + " x " + size.getZ() + ": not 78 columns to 13 x 7 x 13");
		}
		net.minecraft.nbt.CompoundTag tag = template.save(new net.minecraft.nbt.CompoundTag());
		int entities = tag.getList("entities", 10).size();
		if (entities > 0) {
			problems.add(entities + " entities in the file");
		}
		Blueprint d = drawing(level, CaravanTrail.WRECK);
		java.util.Map<BlockPos, BlockState> cells = new java.util.HashMap<>();
		d.blocks().forEach(e -> cells.put(e.pos(), e.state()));
		for (Blueprint.Entry e : d.blocks()) {
			if (e.pos().getY() == 0 && e.state().isAir()) {
				problems.add("air saved in the ground layer at " + e.pos().toShortString());
			}
			if (e.state().getBlock() instanceof LanternBlock && !e.state().getValue(LanternBlock.HANGING)) {
				BlockState below = cells.getOrDefault(e.pos().below(), Blocks.AIR.defaultBlockState());
				if (!holdsALantern(below)) {
					problems.add("a lantern stands on " + BuiltInRegistries.BLOCK.getKey(below.getBlock()).getPath() + " at " + e.pos().toShortString());
				}
			}
		}
		net.minecraft.nbt.ListTag blocks = tag.getList("blocks", 10);
		for (int i = 0; i < blocks.size(); i++) {
			net.minecraft.nbt.CompoundTag b = blocks.getCompound(i);
			if (b.contains("nbt") && b.getCompound("nbt").contains("LootTable")) {
				problems.add("a container carries a loot table at " + b.getList("pos", 3));
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems.subList(0, Math.min(10, problems.size()))));
		helper.succeed();
	}
}
