package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BenchMoves;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-04, from the specs): B290, moving a builder to another Blueprint Table ("their build and queued builds
 * draw on its chests from then on", the old table "stays where it is, free"), and 36.7, digging the cellar of a build
 * finished before its blueprint had one ("its builder digs and fits out only the cellar"; offered only for a build whose
 * blueprint has a basement). Fills the gaps the B290, QaBenchMovePathsPace, Cellar and QaRepairCellar tests leave: both
 * tables stocked at once, the old table broken after the move, a round trip, containers above and in the cellar, and
 * builds with no basement.
 */
public class QaBenchMovesCellars1104bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos TABLE_CHEST = new BlockPos(2, 2, 3);
	private static final BlockPos SECOND = new BlockPos(20, 2, 4);
	private static final BlockPos SECOND_CHEST = new BlockPos(20, 2, 5);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-000000001104");

	private record Hut(ServerLevel level, Villager builder, BuildSite site, BuildPlan plan, Map<Item, Integer> need) {
	}

	// --- B290 ----------------------------------------------------------------------------------------------------------

	/**
	 * The new table's chest holds everything the hut takes, the old one's all but 45 planks. Moved mid-STRUCTURE by a sneak-click next to him, the builder
	 * finishes from the new table's chest: the old table's chest isn't touched from the move on, and every material is
	 * accounted for (what's left in both chests and in his pockets is exactly one stock: nothing lost, nothing doubled).
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qa1104b_both"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1104b_both")
	public void aMoveMidStructureLeavesTheOldTablesChestUntouched(GameTestHelper helper) {
		Hut h = hut(helper, true);
		BlockPos secondAbs = helper.absolutePos(SECOND);
		Map<Item, Integer> oldAtMove = new HashMap<>();
		Map<Item, Integer> start = new HashMap<>();
		for (Item item : h.need().keySet()) {
			start.put(item, count(helper, TABLE_CHEST, item) + count(helper, SECOND_CHEST, item));
		}
		helper.assertTrue(h.need().getOrDefault(Items.OAK_PLANKS, 0) > 45, "precondition: the hut takes " + h.need() );
		helper.onEachTick(() -> {
			if (oldAtMove.isEmpty() && h.site().stage() == BuildPlan.Stage.STRUCTURE && h.site().placed() >= 4) {
				sneakClickBy(helper, h, secondAbs);
				helper.assertTrue(Builders.benchPos(h.builder()).filter(secondAbs::equals).isPresent(), "the sneak-click didn't move him");
				helper.assertTrue(secondAbs.equals(h.site().bench()), "the build still draws on " + h.site().bench());
				for (Item item : h.need().keySet()) {
					oldAtMove.put(item, count(helper, TABLE_CHEST, item));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(oldAtMove.isEmpty(), "never moved mid-STRUCTURE");
			helper.assertTrue(BuildSiteManager.get(h.level()).get(h.site().id()) == null, "still building: stage=" + h.site().stage()
				+ " status=" + h.site().status() + " missing=" + h.site().missing());
			helper.assertTrue(h.plan().unfinished(h.level()).isEmpty(), "the hut isn't finished as drawn");
			int fromNew = h.need().entrySet().stream().mapToInt(e -> e.getValue() - count(helper, SECOND_CHEST, e.getKey())).sum();
			helper.assertTrue(fromNew > 0, "nothing came from the new table's chest");
			for (Map.Entry<Item, Integer> e : h.need().entrySet()) {
				Item item = e.getKey();
				int old = count(helper, TABLE_CHEST, item);
				int moved = count(helper, SECOND_CHEST, item);
				int pockets = h.builder().getInventory().countItem(item);
				helper.assertTrue(old == oldAtMove.get(item), "the old table's chest changed after the move: " + item + " "
					+ oldAtMove.get(item) + " -> " + old);
				int stocked = start.get(item) - e.getValue();
				helper.assertTrue(old + moved + pockets == stocked, item + ": " + old + " in the old chest + " + moved
					+ " in the new + " + pockets + " carried, not the " + stocked + " left over after building (lost or doubled)");
			}
		});
	}

	/**
	 * Moved mid-build, then the old table is broken: he keeps the new one and the build finishes from its chest (the old
	 * table's chest is empty, so nothing could come from it).
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qa1104b_broken"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1104b_broken")
	public void theOldTableBrokenAfterTheMoveDoesntStopTheBuild(GameTestHelper helper) {
		Hut h = hut(helper, false);
		BlockPos secondAbs = helper.absolutePos(SECOND);
		boolean[] moved = {false};
		helper.onEachTick(() -> {
			if (!moved[0] && h.site().stage() == BuildPlan.Stage.STRUCTURE) {
				moved[0] = true;
				sneakClickBy(helper, h, secondAbs);
				helper.assertTrue(Builders.benchPos(h.builder()).filter(secondAbs::equals).isPresent(), "the sneak-click didn't move him");
				h.level().destroyBlock(helper.absolutePos(TABLE), false);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(moved[0], "never moved");
			helper.assertTrue(BuildSiteManager.get(h.level()).get(h.site().id()) == null, "still building: stage=" + h.site().stage()
				+ " status=" + h.site().status() + " missing=" + h.site().missing());
			helper.assertTrue(h.plan().unfinished(h.level()).isEmpty(), "the hut isn't finished as drawn");
			helper.assertTrue(Builders.benchPos(h.builder()).filter(secondAbs::equals).isPresent(),
				"after the build he works at " + Builders.benchPos(h.builder()));
		});
	}

	/**
	 * There and back: moved to the second table and then to the first again, he works at the first, the second is free
	 * again, his build and his queued build draw on the first, and his status names it.
	 */
	//$ gametest_batch HUGE '"qa1104b_back"'
	@GameTest(template = HUGE, batch = "qa1104b_back")
	public void aMoveThereAndBackLeavesEverythingAtTheFirstTable(GameTestHelper helper) {
		Hut h = hut(helper, false);
		ServerLevel level = h.level();
		BlockPos firstAbs = helper.absolutePos(TABLE);
		BlockPos secondAbs = helper.absolutePos(SECOND);
		BuildSite queued = Builders.enqueue(level, h.builder(), JESSE, "Jesse", TEST_HUT, new BlueprintData.Placement(
			level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 18)), Rotation.NONE, Mirror.NONE));
		sneakClickBy(helper, h, secondAbs);
		helper.assertTrue(Builders.benchPos(h.builder()).filter(secondAbs::equals).isPresent(), "not moved there");
		helper.assertTrue(secondAbs.equals(queued.bench()), "the queued build didn't follow there: " + queued.bench());
		sneakClickBy(helper, h, firstAbs);
		helper.assertTrue(Builders.benchPos(h.builder()).filter(firstAbs::equals).isPresent(), "not moved back: " + Builders.benchPos(h.builder()));
		helper.assertTrue(firstAbs.equals(h.site().bench()), "the build draws on " + h.site().bench());
		helper.assertTrue(firstAbs.equals(queued.bench()), "the queued build draws on " + queued.bench());
		helper.assertTrue(level.getPoiManager().getFreeTickets(secondAbs) == 1, "the second table isn't free again");
		helper.assertTrue(level.getPoiManager().getFreeTickets(firstAbs) == 0, "the first table isn't his again");
		String where = firstAbs.getX() + " " + firstAbs.getY() + " " + firstAbs.getZ();
		helper.assertTrue(Builders.statusText(level, h.site(), h.builder()).getString().contains(where),
			"the status doesn't name the first table: " + Builders.statusText(level, h.site(), h.builder()).getString());
		helper.assertTrue(level.getBlockState(firstAbs).is(ModBlocks.BLUEPRINT_TABLE) && level.getBlockState(secondAbs).is(ModBlocks.BLUEPRINT_TABLE),
			"a table was broken");
		helper.succeed();
	}

	// --- 36.7 ----------------------------------------------------------------------------------------------------------

	private static final int GROUND = 6;
	private static final BlockPos BENCH = new BlockPos(2, GROUND + 1, 2);
	private static final BlockPos CHEST = new BlockPos(2, GROUND + 1, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, GROUND + 1, 3);
	private static final BlockPos ORIGIN = new BlockPos(9, GROUND + 1, 9);

	/**
	 * "Its builder digs and fits out only the cellar": every container of the Smithy above ground (and a chest the player
	 * put in the room over the cellar) keeps exactly what the player stored in it, and the room's chest stays put.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qa1104b_cellar_chests"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1104b_cellar_chests")
	public void aCellarDigLeavesTheContainersAboveGroundAndTheirContents(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		ResourceLocation id = StarterBlueprints.SMITHY.id();
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, ORIGIN);
		// the player's own chest in the room over the cellar, and a stack in every container the Smithy has above ground
		BlockPos room = roomSpot(blueprint);
		BlockPos roomChest = helper.absolutePos(ORIGIN).offset(room);
		List<BlockPos> boxes = new ArrayList<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			BlockPos world = helper.absolutePos(ORIGIN).offset(e.pos());
			if (e.pos().getY() >= 0 && level.getBlockEntity(world) instanceof Container) {
				boxes.add(world);
			}
		}
		helper.assertFalse(boxes.isEmpty(), "precondition: the Smithy has no container above ground");
		level.setBlock(roomChest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		boxes.add(roomChest);
		for (int i = 0; i < boxes.size(); i++) {
			Container box = (Container) level.getBlockEntity(boxes.get(i));
			box.setItem(0, new ItemStack(Items.DIAMOND, 1 + i));
		}
		helper.assertTrue(Cellars.needsDigging(level, id, placement), "precondition: the old Smithy doesn't need its cellar dug");
		BuildSite site = startDig(helper, id, placement);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still digging: stage=" + site.stage()
				+ " status=" + site.status() + " missing=" + site.missing());
			helper.assertFalse(Cellars.needsDigging(level, id, placement), "the cellar still needs digging");
			for (int i = 0; i < boxes.size(); i++) {
				BlockPos at = boxes.get(i);
				helper.assertTrue(level.getBlockEntity(at) instanceof Container, "the container at "
					+ at.subtract(placement.origin()).toShortString() + " is gone: " + level.getBlockState(at));
				Container box = (Container) level.getBlockEntity(at);
				helper.assertTrue(box.countItem(Items.DIAMOND) == 1 + i, "the container at " + at.subtract(placement.origin()).toShortString()
					+ " holds " + box.countItem(Items.DIAMOND) + " diamonds, not " + (1 + i));
			}
		});
	}

	/**
	 * A chest the player already put in the part of the cellar he dug by hand (the rest still ground, so more than half is
	 * solid and the dig is offered): builders never break containers while clearing, so it stands with what it holds.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qa1104b_cellar_inside"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1104b_cellar_inside")
	public void aChestInTheHalfDugCellarSurvivesTheDig(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		ResourceLocation id = StarterBlueprints.SMITHY.id();
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, ORIGIN);
		// the cellar's open spots on its lowest open layer, one of them a chest the player put down after digging a pocket
		Blueprint.Entry spot = blueprint.blocks().stream()
			.filter(e -> e.pos().getY() < 0 && e.state().isAir())
			.filter(e -> blueprint.blocks().stream().anyMatch(b -> b.pos().equals(e.pos().below()) && !b.state().isAir() && b.pos().getY() < 0))
			.findFirst().orElseThrow(() -> new GameTestAssertException("no floor spot in the Smithy's cellar"));
		BlockPos chest = placement.origin().offset(spot.pos());
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.EMERALD, 9));
		helper.assertTrue(Cellars.needsDigging(level, id, placement), "precondition: with one chest in it the cellar isn't mostly ground");
		BuildSite site = startDig(helper, id, placement);
		helper.succeedWhen(() -> {
			helper.assertTrue(level.getBlockEntity(chest) instanceof Container c && c.countItem(Items.EMERALD) == 9,
				"the player's chest in the cellar was broken or emptied: " + level.getBlockState(chest));
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still digging: stage=" + site.stage()
				+ " status=" + site.status() + " missing=" + site.missing());
		});
	}

	/**
	 * "A finished build whose blueprint now has a basement": a finished Level II Inn and Farmstead (no basement; their
	 * Level IIIs have one, but that is an upgrade, not a dig) and a finished Market Stall, all on solid ground, are never
	 * offered "Dig the cellar", never in the hall's advice, and their own blueprint over them isn't a cellar dig.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1104b_cellar_none"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1104b_cellar_none")
	public void aBuildWithNoBasementIsNeverOfferedACellar(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		helper.setDayTime(2000);
		List<StarterBlueprints.Entry> flat = List.of(StarterBlueprints.INN_2, StarterBlueprints.FARMSTEAD_2, StarterBlueprints.MARKET_STALL);
		List<BlueprintData.Placement> placements = new ArrayList<>();
		for (int i = 0; i < flat.size(); i++) {
			StarterBlueprints.Entry entry = flat.get(i);
			helper.assertTrue(BlueprintLibrary.get(level, entry.id()).orElseThrow().basement() == 0, "precondition: " + entry.id() + " has a basement");
			BlueprintData.Placement p = new BlueprintData.Placement(level.dimension().location(),
				helper.absolutePos(new BlockPos(2 + i * 9, GROUND + 1, 2)), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(entry.id(), p, UUID.randomUUID());
			placements.add(p);
			helper.assertFalse(Cellars.needsDigging(level, entry.id(), p), entry.id() + " with ground under it needs a cellar dug");
			helper.assertFalse(Cellars.isCellarDig(level, entry.id(), p), entry.id() + "'s own blueprint over it is a cellar dig");
		}
		helper.assertTrue(Cellars.toDig(level, helper.absolutePos(new BlockPos(14, GROUND + 1, 14)), 64).isEmpty(),
			"the hall's advice lists a cellar to dig: " + Cellars.toDig(level, helper.absolutePos(new BlockPos(14, GROUND + 1, 14)), 64));
		helper.setBlock(new BlockPos(14, GROUND + 1, 24), ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(15, GROUND + 1, 24));
		Builders.employ(level, villager, helper.absolutePos(new BlockPos(14, GROUND + 1, 24)));
		helper.assertTrue(Cellars.offer(level, villager).isEmpty(), "the builder offers to dig a cellar under a build with none");
		helper.assertTrue(villager.getOffers().stream().noneMatch(o -> Cellars.isJob(o.getResult())), "a \"Dig the cellar\" trade is on sale");
		helper.succeed();
	}

	// --- helpers -----------------------------------------------------------------------------------------------------

	/** The test hut, its builder at the first table with a chest of everything; the second table's chest stocked too or empty. */
	private static Hut hut(GameTestHelper helper, boolean secondStocked) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(TABLE_CHEST, Blocks.CHEST);
		helper.setBlock(SECOND, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(SECOND_CHEST, Blocks.CHEST);
		ensurePoi(level, helper.absolutePos(TABLE));
		ensurePoi(level, helper.absolutePos(SECOND));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, JESSE, "Jesse", TEST_HUT, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		Map<Item, Integer> need = new HashMap<>(plan.materials());
		fill(helper, TABLE_CHEST, need);
		if (secondStocked) {
			// the old table's chest is 45 planks short: the rest of the planks can only come from the new table's chest
			Container old = helper.getBlockEntity(TABLE_CHEST);
			old.clearContent();
			Map<Item, Integer> part = new HashMap<>(need);
			part.put(Items.OAK_PLANKS, Math.max(1, need.getOrDefault(Items.OAK_PLANKS, 0) - 45));
			fill(helper, TABLE_CHEST, part);
			fill(helper, SECOND_CHEST, need);
		} else {
			// the old table's chest is empty: all of it waits by the second table
			((Container) helper.getBlockEntity(TABLE_CHEST)).clearContent();
			fill(helper, SECOND_CHEST, need);
		}
		return new Hut(level, builder, site, plan, need);
	}

	/** A player sneaking with an empty hand, standing by the builder, right-clicks the table at {@code table}. */
	private static void sneakClickBy(GameTestHelper helper, Hut h, BlockPos table) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 by = h.builder().position();
		player.moveTo(by.x, by.y, by.z);
		player.setShiftKeyDown(true);
		h.level().getBlockState(table).useWithoutItem(h.level(), player, new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
		player.discard();
	}

	private static void fill(GameTestHelper helper, BlockPos chest, Map<Item, Integer> need) {
		Container box = helper.getBlockEntity(chest);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : need.entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				box.setItem(slot++, new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
	}

	private static int count(GameTestHelper helper, BlockPos chest, Item item) {
		return ((Container) helper.getBlockEntity(chest)).countItem(item);
	}

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	private static void ground(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				for (int y = 2; y <= GROUND; y++) {
					helper.setBlock(new BlockPos(x, y, z), y < 4 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK);
				}
			}
		}
	}

	/** {@code entry} standing finished at {@code originRel} as built before 26.15: its basement ground, the hatch floored over. */
	private static BlueprintData.Placement oldBuild(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos originRel) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(originRel), Rotation.NONE, Mirror.NONE);
		BlockPos file = helper.absolutePos(originRel).below(entry.basement());
		level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, file, file, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
		Set<Long> columns = columns(blueprint);
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (Blueprint.Entry e : blueprint.blocks()) {
			BlockPos world = helper.absolutePos(originRel).offset(e.pos());
			if (e.pos().getY() < 0) {
				level.removeBlockEntity(world);
				level.setBlock(world, (e.pos().getY() >= -2 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), flags);
			} else if (e.pos().getY() == 0 && columns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()))
					&& (e.state().is(BlockTags.TRAPDOORS) || e.state().is(Blocks.LADDER) || e.state().isAir())) {
				level.setBlock(world, Blocks.COBBLESTONE.defaultBlockState(), flags);
			}
		}
		BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
		return placement;
	}

	private static Set<Long> columns(Blueprint blueprint) {
		Set<Long> columns = new HashSet<>();
		blueprint.blocks().stream().filter(e -> e.pos().getY() < 0).forEach(e -> columns.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
		return columns;
	}

	/** A spot of the room over the cellar (the drawing's air at y = 1, not on the way down). */
	private static BlockPos roomSpot(Blueprint blueprint) {
		Set<Long> columns = columns(blueprint);
		Set<Long> wayDown = new HashSet<>();
		blueprint.blocks().stream().filter(e -> e.pos().getY() >= 0 && (e.state().is(Blocks.LADDER) || e.state().is(BlockTags.TRAPDOORS)))
			.forEach(e -> wayDown.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
		return blueprint.blocks().stream()
			.filter(e -> e.pos().getY() == 1 && e.state().isAir())
			.filter(e -> columns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())) && !wayDown.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())))
			.reduce((a, b) -> b).orElseThrow(() -> new GameTestAssertException("no room over the cellar")).pos();
	}

	/** The village's builder, given the "Dig the cellar" blueprint (lined up over the build), stocked with what the cellar takes. */
	private static BuildSite startDig(GameTestHelper helper, ResourceLocation id, BlueprintData.Placement placement) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		List<BuildSiteManager.Finished> offered = Cellars.offer(level, villager);
		helper.assertTrue(offered.stream().anyMatch(f -> f.placement().equals(placement)), "the builder doesn't offer the dig: " + offered);
		Blueprint cellar = Cellars.cellarOnly(BlueprintLibrary.get(level, id).orElseThrow());
		Map<Item, Integer> need = new HashMap<>(BuildPlan.create(cellar, placement).materials());
		List<BlockPos> stores = new ArrayList<>(List.of(CHEST, CHEST.east(), CHEST.east(2), CHEST.east(3)));
		int slot = 0;
		for (Map.Entry<Item, Integer> e : need.entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				BlockPos at = stores.get(slot / 27);
				if (!(level.getBlockEntity(helper.absolutePos(at)) instanceof Container)) {
					helper.setBlock(at, Blocks.BARREL);
				}
				((Container) helper.getBlockEntity(at)).setItem(slot % 27, new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
				slot++;
			}
		}
		BuildSite site = Builders.start(level, villager, null, id, placement);
		helper.assertTrue(site.isCellarOnly(), "the job didn't start a cellar dig");
		return site;
	}
}
