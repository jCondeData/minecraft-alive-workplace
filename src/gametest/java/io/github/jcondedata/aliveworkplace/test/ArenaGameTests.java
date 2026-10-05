package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 28.16, the Arena, in the plain suite (its Healing Machines load as air without Cobblemon): a builder finishes
 * each of its three tiers; {@link Arenas#find} finds the ring centre, the boxes, the seats, the fair lane, the
 * champion's pole and the notice board in every rotation and mirrored; the hall's "What next?" rule; the names.
 */
public class ArenaGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	/** Where the builds go in the huge area (30 x 30): its north-west corner; the bench and barrels east of them. */
	private static final BlockPos ORIGIN = new BlockPos(0, 2, 0);
	private static final BlockPos BENCH = new BlockPos(27, 2, 1);

	// --- a builder finishes each tier -----------------------------------------------------------------------------

	/** Arena (25 x 19): the ring, both boxes, the benches, the poles and the board, from bare ground. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"arena_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "arena_build")
	public void aBuilderBuildsTheArena(GameTestHelper helper) {
		build(helper, StarterBlueprints.ARENA);
	}

	/** Arena II (25 x 29): the stands, the gate arch, the lanterns and the fair lane. */
	//$ gametest_ticks_batch HUGE_AREA '45000' '"arena_2_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 45000, batch = "arena_2_build")
	public void aBuilderBuildsArenaII(GameTestHelper helper) {
		build(helper, StarterBlueprints.ARENA_2);
	}

	/** Arena III (25 x 29, so it fits the huge area): the grandstand, the trainers' rooms and the champion's pole. */
	//$ gametest_ticks_batch HUGE_AREA '60000' '"arena_3_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 60000, batch = "arena_3_build")
	public void aBuilderBuildsArenaIII(GameTestHelper helper) {
		build(helper, StarterBlueprints.ARENA_3);
	}

	/** A builder builds {@code entry} at {@link #ORIGIN} from barrels holding exactly what its plan needs. */
	private static void build(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(27, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BoundingBox box = BlueprintOutline.bounds(placement, entry.size());
		helper.assertTrue(box.getXSpan() <= 29 && box.getZSpan() <= 29, entry.id() + " is bigger than 29 x 29: " + box);
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(stock.size() <= 16 * 27, "more materials than the barrels hold: " + stock.size() + " stacks");
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(26 + i % 4, 2, 6 + 2 * (i / 4));
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " " + Math.round(site.progress(plan) * 100) + "% missing " + site.missing()
					+ " builder at " + helper.relativePos(builder.blockPosition()));
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(4)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			// (no check of site.skipped(): the build fills the area to its north and west edges, and the landscaping round
			// it reaches past them into the void outside the test area, where those steps are skipped)
			// The finished build is the village's Arena, its spots where they should be
			Arenas.Arena arena = Arenas.at(entry.id(), placement);
			assertSpots(helper, arena);
		});
	}

	// --- Arenas.find in every rotation, mirrored ------------------------------------------------------------------

	/**
	 * Every tier placed in each of the four rotations, plain and mirrored (front to back, as the Blueprint Table mirrors):
	 * {@link Arenas#find} gives the spots where the blocks really are — the centre spot under the ring centre, the
	 * daises under the boxes, every seat a stair with its back away from the ring, the lane under the fair lane, the log
	 * of the champion's pole under its gold cap, the notice board.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"arena_spots"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "arena_spots")
	public void arenasFindTheirSpotsInEveryRotationAndMirrored(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(15, 2, 15));
		forgetArenas(level, hall);
		helper.assertTrue(Arenas.find(level, hall).isEmpty(), "found an Arena before any was built");
		int checked = 0;
		for (StarterBlueprints.Entry entry : StarterBlueprints.ARENAS) {
			StructureTemplate template = level.getStructureManager().get(entry.id()).orElseThrow();
			for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK)) {
				for (Rotation rotation : Rotation.values()) {
					clearArea(helper);
					// Shift the origin so the turned build lies in the area from its (0, 0) corner
					BlueprintData.Placement first = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), rotation, mirror);
					BoundingBox turned = BlueprintOutline.bounds(first, entry.size());
					BlockPos corner = helper.absolutePos(ORIGIN);
					BlockPos origin = first.origin().offset(corner.getX() - turned.minX(), 0, corner.getZ() - turned.minZ());
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
					template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(rotation).setMirror(mirror),
						RandomSource.create(28_16L), 2);
					BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
					Optional<Arenas.Arena> found = Arenas.find(level, hall);
					helper.assertTrue(found.isPresent(), "no Arena found for " + entry.id() + " " + rotation + " " + mirror);
					helper.assertTrue(found.get().placement().equals(placement) && found.get().structure().equals(entry.id()),
						"found " + found.get().structure() + " at " + found.get().placement() + ", not " + entry.id() + " at " + placement);
					helper.assertTrue(found.get().tier() == Arenas.tier(entry.id()), "tier " + found.get().tier() + " for " + entry.id());
					assertSpots(helper, found.get());
					BuildSiteManager.get(level).forgetFinished(placement);
					checked++;
				}
			}
		}
		helper.assertTrue(checked == 24, "checked " + checked + " placements");
		clearArea(helper);
		helper.succeed();
	}

	/** The highest tier wins (an upgraded Arena is remembered finished at every tier), a styled Arena counts, and an Arena
	 * beyond the hall's reach or a building that isn't one doesn't. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"arena_spots"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "arena_spots")
	public void theVillagesArenaIsItsHighestTierWithinReach(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(0, 2, 0)).offset(0, 0, -200);
		forgetArenas(level, hall);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement near = new BlueprintData.Placement(level.dimension().location(), hall.offset(10, 0, 10), Rotation.CLOCKWISE_90, Mirror.NONE);
		BlueprintData.Placement far = new BlueprintData.Placement(level.dimension().location(), hall.offset(io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS + 20, 0, 0),
			Rotation.NONE, Mirror.NONE);
		List<BlueprintData.Placement> used = List.of(near, far);
		try {
			sites.recordFinished(StarterBlueprints.MARKET_SQUARE.id(), near, UUID.randomUUID());
			helper.assertTrue(Arenas.find(level, hall).isEmpty(), "a Market Square is no Arena");
			sites.recordFinished(StarterBlueprints.ARENA_3.id(), far, UUID.randomUUID());
			helper.assertTrue(Arenas.find(level, hall).isEmpty(), "found an Arena beyond the hall's reach");
			sites.recordFinished(StarterBlueprints.ARENA.id(), near, UUID.randomUUID());
			sites.recordFinished(StarterBlueprints.ARENA_2.id(), near, UUID.randomUUID());
			Arenas.Arena arena = Arenas.find(level, hall).orElseThrow(() -> new GameTestAssertException("no Arena found"));
			helper.assertTrue(arena.tier() == 2 && arena.seats().size() == 30, "found tier " + arena.tier() + " with " + arena.seats().size() + " seats");
			helper.assertTrue(arena.fairLane().isPresent() && arena.championPole().isEmpty(), "Arena II has a fair lane but no champion's pole");
			sites.forgetFinished(near);
			helper.assertTrue(Arenas.find(level, hall).isEmpty(), "forgotten, but still found");
			List<BlueprintStyles.Style> styles = BlueprintStyles.all();
			if (!styles.isEmpty()) {
				ResourceLocation styled = BlueprintStyles.styled(StarterBlueprints.ARENA.id(), styles.get(0).name());
				helper.assertTrue(Arenas.tier(styled) == 1, styled + " isn't an Arena");
				sites.recordFinished(styled, near, UUID.randomUUID());
				helper.assertTrue(Arenas.find(level, hall).map(Arenas.Arena::tier).orElse(0) == 1, "a styled Arena isn't found");
			}
		} finally {
			used.forEach(sites::forgetFinished);
		}
		helper.assertTrue(Arenas.seats(1).size() == 12 && Arenas.seats(2).size() == 30 && Arenas.seats(3).size() == 52,
			"seats: " + Arenas.seats(1).size() + ", " + Arenas.seats(2).size() + ", " + Arenas.seats(3).size());
		helper.succeed();
	}

	/** The spots of {@code arena} hold what the templates put there. */
	private static void assertSpots(GameTestHelper helper, Arenas.Arena arena) {
		ServerLevel level = helper.getLevel();
		String what = arena.structure() + " " + arena.placement().rotation() + " " + arena.placement().mirror();
		helper.assertTrue(level.getBlockState(arena.ring().below()).is(Blocks.WHITE_GLAZED_TERRACOTTA) && level.getBlockState(arena.ring()).isAir(),
			what + ": the ring centre at " + helper.relativePos(arena.ring()) + " stands on " + level.getBlockState(arena.ring().below()));
		helper.assertTrue(arena.boxes().size() == 2, what + ": " + arena.boxes().size() + " boxes");
		for (BlockPos box : arena.boxes()) {
			helper.assertTrue(level.getBlockState(box.below()).is(Blocks.POLISHED_ANDESITE) && level.getBlockState(box).isAir()
				&& level.getBlockState(box.below(2)).is(net.minecraft.tags.BlockTags.STONE_BRICKS),
				what + ": no dais under the box at " + helper.relativePos(box) + ": " + level.getBlockState(box.below()));
			helper.assertTrue(box.distManhattan(arena.ring()) == 11, what + ": the box at " + helper.relativePos(box) + " isn't at the ring's end");
		}
		int expected = arena.tier() == 1 ? 12 : arena.tier() == 2 ? 30 : 52;
		helper.assertTrue(arena.seats().size() == expected && new HashSet<>(arena.seats()).size() == expected,
			what + ": " + arena.seats().size() + " seats, not " + expected);
		Vec3 ring = Vec3.atCenterOf(arena.ring());
		for (BlockPos seat : arena.seats()) {
			BlockState state = level.getBlockState(seat);
			helper.assertTrue(state.getBlock() instanceof StairBlock, what + ": the seat at " + helper.relativePos(seat) + " is " + state);
			// A stair's facing is its high back: a seat's back is away from the ring, so the sitter looks at it.
			net.minecraft.core.Direction back = state.getValue(StairBlock.FACING);
			Vec3 toRing = ring.subtract(Vec3.atCenterOf(seat));
			helper.assertTrue(back.getStepX() * toRing.x + back.getStepZ() * toRing.z < 0,
				what + ": the seat at " + helper.relativePos(seat) + " has its back to the ring (" + back + ")");
			helper.assertTrue(level.getBlockState(seat.above()).isAir(), what + ": no room to sit at " + helper.relativePos(seat));
		}
		helper.assertTrue(arena.fairLane().isPresent() == (arena.tier() >= 2), what + ": fair lane " + arena.fairLane());
		arena.fairLane().ifPresent(lane -> {
			BlockState under = level.getBlockState(lane.below());
			helper.assertTrue((under.is(Blocks.COBBLESTONE) || under.is(Blocks.STONE) || under.is(Blocks.ANDESITE)) && level.getBlockState(lane).isAir(),
				what + ": the fair lane at " + helper.relativePos(lane) + " is on " + under);
		});
		helper.assertTrue(arena.championPole().isPresent() == (arena.tier() >= 3), what + ": champion's pole " + arena.championPole());
		arena.championPole().ifPresent(pole -> helper.assertTrue(level.getBlockState(pole).is(Blocks.STRIPPED_SPRUCE_LOG)
			&& level.getBlockState(pole.above()).is(Blocks.GOLD_BLOCK), what + ": the champion's pole at " + helper.relativePos(pole) + " is "
			+ level.getBlockState(pole)));
		helper.assertTrue(level.getBlockState(arena.noticeBoard()).is(Blocks.DARK_OAK_PLANKS),
			what + ": the notice board at " + helper.relativePos(arena.noticeBoard()) + " is " + level.getBlockState(arena.noticeBoard()));
	}

	private static void forgetArenas(ServerLevel level, BlockPos hall) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedNear(level, hall, io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS * 2)) {
			if (Arenas.tier(f.structure()) > 0) {
				sites.forgetFinished(f.placement());
			}
		}
	}

	private static void clearArea(GameTestHelper helper) {
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(29, 15, 29))) {
			helper.setBlock(p, Blocks.AIR);
		}
	}

	// --- the blueprints ------------------------------------------------------------------------------------------

	/** The three tiers are the sizes StarterBlueprints says, builders can place every block, and each keeps most of the one before. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theArenasBlueprintsAreBuildableUpgrades(GameTestHelper helper) {
		Blueprint before = null;
		for (StarterBlueprints.Entry entry : StarterBlueprints.ARENAS) {
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new GameTestAssertException("missing " + entry.id()));
			helper.assertTrue(blueprint.size().equals(entry.size()), entry.id() + " is " + blueprint.size() + ", not " + entry.size());
			helper.assertTrue(entry.size().getX() <= 29 && entry.size().getZ() <= 29, entry.id() + " is bigger than 29 x 29");
			List<String> unbuildable = blueprint.blocks().stream().map(Blueprint.Entry::state)
				.filter(st -> !st.isAir() && !MaterialRules.isSecondaryHalf(st) && MaterialRules.classify(st) == MaterialRules.Kind.SKIP)
				.map(BlockState::toString).distinct().toList();
			helper.assertTrue(unbuildable.isEmpty(), entry.id() + " has blocks builders can't place: " + unbuildable);
			helper.assertTrue(StarterBlueprints.COBBLEMON_ONLY.contains(entry), entry.id() + " isn't Cobblemon-only");
			if (before != null) {
				helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.baseOf(entry.id()).equals(Optional.of(before.id())),
					entry.id() + " doesn't upgrade " + before.id());
				Map<BlockPos, BlockState> up = new HashMap<>();
				blueprint.blocks().forEach(e -> up.put(e.pos(), e.state()));
				long solid = before.blocks().stream().filter(e -> !e.state().isAir()).count();
				long kept = before.blocks().stream().filter(e -> !e.state().isAir() && e.state().equals(up.get(e.pos()))).count();
				helper.assertTrue(kept >= solid * 0.6, entry.id() + " keeps only " + kept + " of " + before.id() + "'s " + solid + " blocks");
			}
			before = blueprint;
		}
		helper.succeed();
	}

	/** Without Cobblemon (this suite) the Arena isn't in the Blueprint Table, and Trainer Leaders don't sell it. */
	//$ gametest_batch AREA '"arena_trades"'
	@GameTest(template = AREA, batch = "arena_trades")
	public void withoutCobblemonThereIsNoArena(GameTestHelper helper) {
		List<ResourceLocation> listed = TableServer.listing(helper.getLevel().getServer()).stream().map(e -> e.id()).toList();
		for (StarterBlueprints.Entry entry : StarterBlueprints.ARENAS) {
			helper.assertFalse(listed.contains(entry.id()), entry.id() + " is in the Blueprint Table without Cobblemon");
		}
		Villager leader = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		for (VillagerTrades.ItemListing listing : leaderTrades(4)) {
			var offer = listing.getOffer(leader, RandomSource.create(1));
			helper.assertTrue(offer == null || !io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(offer.getResult()).map(d -> Arenas.tier(d.structure()) > 0).orElse(false),
				"a Trainer Leader sells the Arena without Cobblemon");
		}
		leader.discard();
		helper.succeed();
	}

	static List<VillagerTrades.ItemListing> leaderTrades(int level) {
		var byLevel = VillagerTrades.TRADES.get(ModVillagers.TRAINER_LEADER);
		return byLevel == null || byLevel.get(level) == null ? List.of() : List.of(byLevel.get(level));
	}

	// --- What next? ---------------------------------------------------------------------------------------------------

	/** "What next?" suggests an Arena to a Cobblemon village of Village rank or more with a Trainer Leader and no Arena. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHallSuggestsAnArena(GameTestHelper helper) {
		ResourceLocation cottage = StarterBlueprints.STARTER_COTTAGE.id();
		ResourceLocation center = StarterBlueprints.POKEMON_CENTER.id();
		helper.assertTrue(VillageAdvice.wantsArena(true, VillageRanks.Rank.VILLAGE, true, List.of(cottage, center)), "a Village with a Leader and no Arena");
		helper.assertTrue(VillageAdvice.wantsArena(true, VillageRanks.Rank.CITY, true, List.of()), "a City with a Leader and no Arena");
		helper.assertFalse(VillageAdvice.wantsArena(true, VillageRanks.Rank.HAMLET, true, List.of()), "a Hamlet is too small");
		helper.assertFalse(VillageAdvice.wantsArena(false, VillageRanks.Rank.TOWN, true, List.of()), "no Cobblemon, no Arena");
		helper.assertFalse(VillageAdvice.wantsArena(true, VillageRanks.Rank.TOWN, false, List.of()), "no Trainer Leader, no Arena");
		for (StarterBlueprints.Entry arena : StarterBlueprints.ARENAS) {
			helper.assertFalse(VillageAdvice.wantsArena(true, VillageRanks.Rank.TOWN, true, List.of(cottage, arena.id())), "it has " + arena.id());
		}
		List<BlueprintStyles.Style> styles = BlueprintStyles.all();
		if (!styles.isEmpty()) {
			ResourceLocation styled = BlueprintStyles.styled(StarterBlueprints.ARENA_3.id(), styles.get(0).name());
			helper.assertFalse(VillageAdvice.wantsArena(true, VillageRanks.Rank.TOWN, true, List.of(styled)), "it has " + styled);
		}
		helper.succeed();
	}

	/** The village has a Trainer Leader only when a grown-up Leader lives within the hall's reach: not a Trainer, not a child. */
	//$ gametest_batch AREA '"arena_leader"'
	@GameTest(template = AREA, batch = "arena_leader")
	public void theHallCountsTheTrainerLeader(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(8, 2, 8));
		Villager trainer = job(helper, new BlockPos(4, 2, 4), ModVillagers.TRAINER);
		Villager child = job(helper, new BlockPos(6, 2, 4), ModVillagers.TRAINER_LEADER);
		child.setAge(-24000);
		helper.assertFalse(VillageAdvice.hasTrainerLeader(level, hall), "a Trainer or a child counted as the Leader");
		Villager leader = job(helper, new BlockPos(10, 2, 10), ModVillagers.TRAINER_LEADER);
		helper.assertTrue(VillageAdvice.hasTrainerLeader(level, hall), "the Leader isn't counted");
		helper.assertFalse(VillageAdvice.hasTrainerLeader(level, hall.offset(io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS * 3, 0, 0)),
			"a Leader far away counted");
		List.of(trainer, child, leader).forEach(Villager::discard);
		helper.succeed();
	}

	private static Villager job(GameTestHelper helper, BlockPos at, VillagerProfession profession) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setVillagerData(v.getVillagerData().setProfession(profession).setLevel(4));
		v.setNoAi(true);
		return v;
	}

	/** Every name and sentence a player reads for the Arena is in English. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theArenaIsTranslated(GameTestHelper helper) {
		Language english = Language.getInstance();
		for (String key : List.of("blueprint.aliveworkplace.arena", "blueprint.aliveworkplace.arena_2", "blueprint.aliveworkplace.arena_3",
				"advice.aliveworkplace.arena", "advice.aliveworkplace.arena.how")) {
			helper.assertTrue(english.has(key), "no English for " + key);
		}
		helper.assertTrue(english.getOrDefault("blueprint.aliveworkplace.arena_3").equals("Arena III"), english.getOrDefault("blueprint.aliveworkplace.arena_3"));
		helper.succeed();
	}
}
