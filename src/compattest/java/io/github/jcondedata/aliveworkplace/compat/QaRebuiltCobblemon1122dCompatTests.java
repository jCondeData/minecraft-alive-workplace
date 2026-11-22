package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-22, lane-d-1122-0108): ROADMAP 50.12's rebuilt Cobblemon builds, written from their blind briefs
 * ({@code tools/blueprints/briefs/{trainers_house,ball_workshop,research_lab,habitat_garden,arena}.md}), not from the
 * designers' recipes, with the real Cobblemon loaded, so its blocks (the Fossil Analyzer, the Pasture Block, the Healing
 * Machines, the Saccharine tree) are real here where the main suite reads them as air (the Research Lab brief: "no test
 * will notice if it is missing or unreachable"). The Pokémon Center is {@link QaRebuiltPokemonCenter1121cCompatTests}'s.
 *
 * <p>The walk is a villager's (as in {@code QaRebuiltWorkshops1121bGameTests}): from the street in front (z &lt; 0), wooden
 * doors open, fence gates shut, never standing on a fence or wall, a step up, up to three down, ladders climbed; a
 * player's walk opens every door, trapdoor and gate. Also B1453: each village look of the Ball Workshop has a loft
 * ladder that holds and climbs to the loft.
 */
public class QaRebuiltCobblemon1122dCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final ResourceLocation FOSSIL_ANALYZER = ModVillagers.FOSSIL_ANALYZER_BLOCK;
	private static final ResourceLocation PASTURE = ModVillagers.PASTURE_BLOCK;
	private static final ResourceLocation HEALING_MACHINE = ResourceLocation.fromNamespaceAndPath("cobblemon", "healing_machine");
	private static final ResourceLocation SACCHARINE_LOG = ResourceLocation.fromNamespaceAndPath("cobblemon", "saccharine_log");

	// ---- the drawings, from the briefs (Cobblemon's blocks real) --------------------------------------------------------

	/**
	 * Trainer's House: exactly one Training Post and no other job site of any job (Cobblemon's own POIs included, which
	 * the main suite can't see), exactly one bed, its head walked to; the post beside a walked cell with room round it to
	 * battle (at least 3 walked cells within 4). Ball Workshop: exactly one smithing table and no other job site (no Ball
	 * Workbench), a chest within 8 across and 4 up or down of it, every chest walked up to, at least one bed walked to.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTrainersHouseAndBallWorkshopHaveTheirOneJobBlockBedAndChestInReach(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		Drawing trainer = drawing(helper, StarterBlueprints.TRAINERS_HOUSE);
		List<BlockPos> posts = trainer.where(BuiltInRegistries.BLOCK.getKey(ModBlocks.TRAINING_POST));
		if (posts.size() != 1) {
			problems.add("trainers_house: " + posts.size() + " training posts " + posts);
		}
		problems.addAll(trainer.otherJobSites(Set.of(BuiltInRegistries.BLOCK.getKey(ModBlocks.TRAINING_POST))));
		Set<BlockPos> walked = trainer.walk(false);
		for (BlockPos post : posts) {
			if (!beside(walked, post)) {
				problems.add("trainers_house: no walked cell beside the post " + post.toShortString());
			}
			long room = walked.stream().filter(p -> within(p, post, 4, 1)).count();
			if (room < 3) {
				problems.add("trainers_house: only " + room + " walked cells within 4 of the post, no room to battle");
			}
		}
		List<BlockPos> beds = trainer.bedHeads();
		if (beds.size() != 1) {
			problems.add("trainers_house: " + beds.size() + " beds " + beds + ", the brief says exactly one");
		}
		for (BlockPos head : beds) {
			if (!near(walked, head, 2)) {
				problems.add("trainers_house: the bed " + head.toShortString() + " can't be walked to");
			}
		}
		problems.addAll(trainer.chestsUnreached());

		Drawing ball = drawing(helper, StarterBlueprints.BALL_WORKSHOP);
		ResourceLocation smithing = BuiltInRegistries.BLOCK.getKey(Blocks.SMITHING_TABLE);
		List<BlockPos> tables = ball.where(smithing);
		if (tables.size() != 1) {
			problems.add("ball_workshop: " + tables.size() + " smithing tables " + tables);
		}
		problems.addAll(ball.otherJobSites(Set.of(smithing)));
		Set<BlockPos> walkedBall = ball.walk(false);
		List<BlockPos> chests = ball.chests();
		for (BlockPos table : tables) {
			if (!beside(walkedBall, table)) {
				problems.add("ball_workshop: no walked cell beside the smithing table " + table.toShortString());
			}
			if (chests.stream().noneMatch(c -> within(c, table, 8, 4))) {
				problems.add("ball_workshop: no chest within 8 across / 4 up or down of the smithing table " + table.toShortString() + " (chests " + chests + ")");
			}
		}
		List<BlockPos> ballBeds = ball.bedHeads();
		if (ballBeds.isEmpty()) {
			problems.add("ball_workshop: no bed");
		}
		for (BlockPos head : ballBeds) {
			if (!near(walkedBall, head, 2)) {
				problems.add("ball_workshop: the bed " + head.toShortString() + " can't be walked to");
			}
		}
		problems.addAll(ball.chestsUnreached());
		if (ball.blocks.values().stream().anyMatch(s -> s.is(Blocks.BARREL))) {
			problems.add("ball_workshop: has a barrel (a Fisherman's job block)");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Research Lab I, II and III: exactly one Fossil Analyzer in each, above the ground, in the same cell in all three (it
	 * has a block entity, so a moved one would be left standing); a cell a villager walks to from the street within 2.5
	 * blocks of it (where the scientist works); no other job site anywhere, cellar included (but the
	 * crafting table, a filed bug); every chest of I is a chest
	 * in the same cell in II, and every chest of each level is walked up to (a player, hatches open).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theResearchLabsKeepOneWalkableFossilAnalyzerInOneCell(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		BlockPos first = null;
		Drawing previous = null;
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.RESEARCH_LAB, StarterBlueprints.RESEARCH_LAB_2, StarterBlueprints.RESEARCH_LAB_3)) {
			Drawing d = drawing(helper, entry);
			String n = entry.id().getPath();
			List<BlockPos> analyzers = d.where(FOSSIL_ANALYZER);
			if (analyzers.size() != 1) {
				problems.add(n + ": " + analyzers.size() + " fossil analyzers " + analyzers + ", the brief says exactly one");
			}
			// (the brief's "no other workstation anywhere"; the Carpenter's crafting table went in B1468)
			problems.addAll(d.otherJobSites(Set.of(FOSSIL_ANALYZER)));
			Set<BlockPos> walked = d.walk(false);
			for (BlockPos a : analyzers) {
				if (a.getY() < 0) {
					problems.add(n + ": the analyzer " + a.toShortString() + " is below the ground");
				}
				if (first == null) {
					first = a;
				} else if (!first.equals(a)) {
					problems.add(n + ": the analyzer moved from " + first.toShortString() + " to " + a.toShortString());
				}
				boolean works = walked.stream().anyMatch(p -> Vec3.atBottomCenterOf(p).distanceTo(Vec3.atCenterOf(a)) <= 2.5);
				if (!works) {
					problems.add(n + ": no cell a villager walks to from the street within 2.5 blocks of the analyzer " + a.toShortString());
				}
			}
			problems.addAll(d.chestsUnreached());
			if (previous != null && entry == StarterBlueprints.RESEARCH_LAB_2) {
				for (BlockPos c : previous.chests()) {
					if (!(d.at(c).getBlock() instanceof ChestBlock)) {
						problems.add(n + ": I's chest " + c.toShortString() + " is " + d.at(c) + " in II");
					}
				}
			}
			previous = d;
		}
		helper.assertTrue(first != null, "(setup) no analyzer read at all");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Habitat Garden I and II: exactly one Pasture Block (job site), the same cell in both, a standing spot within 3 that a
	 * villager walks to; no brewing stand, Healing Machine or second pasture (each steals the honey bottle's job); a chest
	 * within 8 across and 4 up or down of the pasture (missing in the old garden) and every chest walked up to; the centre
	 * stone (mossy cobblestone, nothing above that needs it) at {@link StarterBlueprints#HABITAT_GARDEN_CENTRE} in both,
	 * walked up to; a Saccharine tree whose every log she reaches (within 4 across and up to 4 up of a walked cell); every
	 * leaf persistent; every water source held in on its sides and below.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHabitatGardensKeepTheirPastureCentreTreeAndAChestInReach(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		BlockPos firstPasture = null;
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.HABITAT_GARDEN, StarterBlueprints.HABITAT_GARDEN_2)) {
			Drawing d = drawing(helper, entry);
			String n = entry.id().getPath();
			List<BlockPos> pastures = d.jobSitesOf(PASTURE);
			if (pastures.size() != 1) {
				problems.add(n + ": " + pastures.size() + " pasture job sites " + pastures);
			}
			problems.addAll(d.otherJobSites(Set.of(PASTURE)));
			for (BlockPos p : d.blocks.keySet()) {
				BlockState s = d.at(p);
				if (s.is(Blocks.BREWING_STAND) || id(s).equals(HEALING_MACHINE)) {
					problems.add(n + ": a " + id(s).getPath() + " at " + p.toShortString() + " would steal the keeper's job");
				}
			}
			Set<BlockPos> walked = d.walk(false);
			List<BlockPos> chests = d.chests();
			for (BlockPos pasture : pastures) {
				if (firstPasture == null) {
					firstPasture = pasture;
				} else if (!firstPasture.equals(pasture)) {
					problems.add(n + ": the pasture moved from " + firstPasture.toShortString() + " to " + pasture.toShortString());
				}
				if (walked.stream().noneMatch(w -> within(w, pasture, 3, 1))) {
					problems.add(n + ": no walked standing spot within 3 of the pasture " + pasture.toShortString());
				}
				if (chests.stream().noneMatch(c -> within(c, pasture, 8, 4))) {
					problems.add(n + ": no chest within 8 across / 4 up or down of the pasture " + pasture.toShortString() + " (chests " + chests + ")");
				}
			}
			problems.addAll(d.chestsUnreached());
			BlockPos centre = StarterBlueprints.HABITAT_GARDEN_CENTRE;
			if (!d.at(centre).is(Blocks.MOSSY_COBBLESTONE)) {
				problems.add(n + ": the centre stone " + centre.toShortString() + " is " + d.at(centre));
			}
			BlockState onCentre = d.at(centre.above());
			if (!onCentre.isAir() && !onCentre.canBeReplaced()) {
				problems.add(n + ": " + onCentre + " stands on the centre stone");
			}
			if (walked.stream().noneMatch(w -> within(w, centre, 3, 1))) {
				problems.add(n + ": no walked cell within 3 of the centre stone " + centre.toShortString());
			}
			List<BlockPos> logs = d.where(SACCHARINE_LOG);
			if (logs.isEmpty()) {
				problems.add(n + ": no Saccharine log");
			}
			for (BlockPos log : logs) {
				boolean reach = walked.stream().anyMatch(w -> Math.abs(w.getX() - log.getX()) <= 4 && Math.abs(w.getZ() - log.getZ()) <= 4
					&& log.getY() - w.getY() <= 4 && log.getY() - w.getY() >= -2);
				if (!reach) {
					problems.add(n + ": the Saccharine log " + log.toShortString() + " is out of the keeper's reach");
				}
			}
			for (BlockPos p : d.blocks.keySet()) {
				BlockState s = d.at(p);
				if (s.getBlock() instanceof LeavesBlock && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
					problems.add(n + ": the leaves at " + p.toShortString() + " aren't persistent");
				}
				if (s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource()) {
					for (BlockPos side : List.of(p.north(), p.south(), p.east(), p.west(), p.below())) {
						BlockState o = d.at(side);
						if (o.getFluidState().is(Fluids.WATER)) {
							continue;
						}
						if (o.isAir() || o.canBeReplaced() || o.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() && !(o.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer)) {
							problems.add(n + ": the water " + p.toShortString() + " can flow out into " + side.toShortString() + " (" + o + ")");
						}
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Arena I-III: on foot from the front to the ring centre, both trainer's boxes, every seat (within 2.5), the notice
	 * board (within 2) and, in II and III, the fair lane; Arena III's two Healing Machines (one in each trainers' room)
	 * each walked to; no other job site but those two (a barrel would take a Fisherman in).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyArenaTiersSpotsAndHealingMachinesAreWalkedToFromTheFront(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int tier = 0;
		for (StarterBlueprints.Entry entry : StarterBlueprints.ARENAS) {
			tier++;
			Drawing d = drawing(helper, entry);
			String n = entry.id().getPath();
			Set<BlockPos> walked = d.walk(false);
			if (!near(walked, Arenas.RING_CENTRE.above(), 1) && !near(walked, Arenas.RING_CENTRE, 1)) {
				problems.add(n + ": the ring centre " + Arenas.RING_CENTRE.toShortString() + " isn't walked to");
			}
			for (BlockPos box : Arenas.BOXES) {
				if (!walked.contains(box) && !beside(walked, box)) {
					problems.add(n + ": the trainer's box " + box.toShortString() + " isn't walked to");
				}
			}
			List<BlockPos> lost = new ArrayList<>();
			for (BlockPos seat : Arenas.seats(tier)) {
				if (!near(walked, seat, 2)) {
					lost.add(seat);
				}
			}
			if (!lost.isEmpty()) {
				problems.add(n + ": " + lost.size() + " of " + Arenas.seats(tier).size() + " seats nobody walks to within 2.5, e.g. " + lost.stream().limit(4).toList());
			}
			if (!near(walked, Arenas.NOTICE_BOARD, 2)) {
				problems.add(n + ": the notice board " + Arenas.NOTICE_BOARD.toShortString() + " isn't walked up to");
			}
			if (tier >= 2 && !walked.contains(Arenas.FAIR_LANE)) {
				problems.add(n + ": the fair lane " + Arenas.FAIR_LANE.toShortString() + " isn't walked to");
			}
			List<BlockPos> machines = d.where(HEALING_MACHINE);
			if (machines.size() != (tier == 3 ? 2 : 0)) {
				problems.add(n + ": " + machines.size() + " healing machines " + machines);
			}
			for (BlockPos m : machines) {
				if (!beside(walked, m)) {
					problems.add(n + ": the healing machine " + m.toShortString() + " isn't walked to from the front");
				}
			}
			problems.addAll(d.otherJobSites(Set.of(HEALING_MACHINE)));
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- B1453: the village looks' loft ladders ---------------------------------------------------------------------------

	/**
	 * B1453: each of the five village looks of the Ball Workshop, placed as a village places it (entities left out, the
	 * street joint as the ground): every ladder holds where it was placed and after a block update (it hangs on a block that
	 * holds it), none is lost, and a player walks from the street in through the door and up the ladder onto the loft
	 * (a standable cell at or above the loft floor's top, y 10).
	 */
	//$ gametest_batch HUGE_AREA '"qa_cob1122d_village_ladders"'
	@GameTest(template = HUGE_AREA, batch = "qa_cob1122d_village_ladders")
	public void b1453EveryVillageBallWorkshopHasAClimbableLoftLadder(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(5, 1, 5));
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (String style : List.of("desert", "plains", "savanna", "snowy", "taiga")) {
			ResourceLocation file = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "village/" + style + "_ball_workshop");
			Optional<StructureTemplate> found = level.getStructureManager().get(file);
			helper.assertTrue(found.isPresent(), "(setup) no " + file);
			StructureTemplate template = found.get();
			var size = template.getSize();
			// clear the spot and lay a floor, then place it as a village does (no entities)
			for (BlockPos p : BlockPos.betweenClosed(origin.offset(-2, 0, -3), origin.offset(size.getX() + 1, size.getY() + 1, size.getZ() + 1))) {
				level.setBlock(p, p.getY() == origin.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
			}
			template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(true), RandomSource.create(1453L), 2 | 16);
			for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX(), size.getY(), size.getZ()))) {
				if (level.getBlockState(p).is(Blocks.JIGSAW)) {
					level.setBlock(p, p.getY() == origin.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
			// the rungs the file draws (placing a template drops a rung that can't hold, so read them from the file)
			List<BlockPos> ladders = new ArrayList<>();
			for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, new StructurePlaceSettings(), Blocks.LADDER)) {
				ladders.add(info.pos().immutable());
			}
			if (ladders.isEmpty()) {
				problems.add(style + ": no ladder at all");
			}
			for (BlockPos p : ladders) {
				BlockState s = level.getBlockState(p);
				if (!s.is(Blocks.LADDER)) {
					BlockState drawn = template.filterBlocks(origin, new StructurePlaceSettings(), Blocks.LADDER).stream().filter(i -> i.pos().equals(p))
						.findFirst().orElseThrow().state();
					problems.add(style + ": the ladder " + p.subtract(origin).toShortString() + " didn't stay when placed (" + s + "), it hangs on "
						+ level.getBlockState(p.relative(drawn.getValue(net.minecraft.world.level.block.LadderBlock.FACING).getOpposite())));
					checked++;
					continue;
				}
				if (!s.canSurvive(level, p)) {
					problems.add(style + ": the ladder " + p.subtract(origin).toShortString() + " can't hold on " + level.getBlockState(p.relative(s.getValue(
						net.minecraft.world.level.block.LadderBlock.FACING).getOpposite())));
				}
				BlockState updated = Block.updateFromNeighbourShapes(s, level, p);
				if (!updated.is(Blocks.LADDER)) {
					problems.add(style + ": the ladder " + p.subtract(origin).toShortString() + " falls off on a block update");
				}
				checked++;
			}
			int sx = size.getX();
			int sz = size.getZ();
			Function<BlockPos, BlockState> world = p -> level.getBlockState(origin.offset(p));
			Set<BlockPos> walked = walk(world, sx, sz, size.getY(), 1, true);
			boolean loft = walked.stream().anyMatch(p -> p.getY() >= 10 && p.getX() >= 0 && p.getX() < sx && p.getZ() >= 0 && p.getZ() < sz);
			if (!loft) {
				int top = walked.stream().filter(p -> p.getZ() >= 0).mapToInt(BlockPos::getY).max().orElse(-1);
				problems.add(style + ": a player can't climb from the street onto the loft (highest walked cell inside at y " + top + ")");
			}
		}
		helper.assertTrue(checked >= 5, "(setup) only " + checked + " ladders checked");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---- built by a builder with Cobblemon ------------------------------------------------------------------------------

	/**
	 * Research Lab I from bare ground, one builder, barrels holding exactly its materials: done with every block right
	 * (the Fossil Analyzer a real Cobblemon block now), then a jobless villager beside it, sneak-clicked with a dome fossil,
	 * becomes the Fossil Scientist at that very analyzer.
	 */
	//$ gametest_ticks_batch HUGE_AREA '20000' '"qa_cob1122d_lab_one"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20000, batch = "qa_cob1122d_lab_one")
	public void theResearchLabIIsBuiltWithCobblemonAndAScientistTakesItsAnalyzer(GameTestHelper helper) {
		buildThenTake(helper, StarterBlueprints.RESEARCH_LAB, FOSSIL_ANALYZER, cobblemon("dome_fossil"), ModVillagers.FOSSIL_SCIENTIST);
	}

	/**
	 * The Ball Workshop from bare ground with exactly its materials: done; a jobless villager by its smithing table,
	 * sneak-clicked with a red apricorn, becomes the Ball Smith there, and his supply search finds a chest (what he
	 * makes balls from and puts them in).
	 */
	//$ gametest_ticks_batch HUGE_AREA '20000' '"qa_cob1122d_ball"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20000, batch = "qa_cob1122d_ball")
	public void theBallWorkshopIsBuiltAndABallSmithFindsHisChests(GameTestHelper helper) {
		buildThenTake(helper, StarterBlueprints.BALL_WORKSHOP, BuiltInRegistries.BLOCK.getKey(Blocks.SMITHING_TABLE), cobblemon("red_apricorn"),
			ModVillagers.BALL_SMITH);
	}

	/**
	 * Research Lab I standing (built by the builder), a stack in each of its chests, upgraded to II: the analyzer stays
	 * the one in its cell (no second one, nothing left unbuilt), every chest keeps what was in it, and a Fossil Scientist
	 * takes the kept analyzer.
	 */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_cob1122d_lab_up"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_cob1122d_lab_up")
	public void theResearchLabIUpgradedToIIKeepsItsAnalyzerAndChests(GameTestHelper helper) {
		Stage s = stage(helper, StarterBlueprints.RESEARCH_LAB_2.id(), new BlockPos(5, 2, 5), true);
		ServerLevel level = s.level();
		BlockPos origin = s.placement().origin();
		UUID first = Builders.start(level, s.builder(), null, StarterBlueprints.RESEARCH_LAB.id(), s.placement()).id();
		UUID[] site = {null};
		BuildPlan[] plan = {null};
		List<BlockPos> chests = new ArrayList<>();
		BlockPos[] analyzer = {null};
		helper.onEachTick(() -> {
			if (site[0] != null || BuildSiteManager.get(level).get(first) != null) {
				return;
			}
			Blueprint one = BlueprintLibrary.get(level, StarterBlueprints.RESEARCH_LAB.id()).orElseThrow();
			for (Blueprint.Entry e : one.blocks()) {
				if (e.state().getBlock() instanceof ChestBlock) {
					chests.add(e.pos());
					helper.assertTrue(level.getBlockEntity(origin.offset(e.pos())) instanceof Container, "(setup) no chest built at " + e.pos().toShortString());
					((Container) level.getBlockEntity(origin.offset(e.pos()))).setItem(0, new ItemStack(Items.GLISTERING_MELON_SLICE, 7));
				}
				if (id(e.state()).equals(FOSSIL_ANALYZER)) {
					analyzer[0] = e.pos();
				}
			}
			helper.assertTrue(analyzer[0] != null && id(level.getBlockState(origin.offset(analyzer[0]))).equals(FOSSIL_ANALYZER),
				"(setup) the standing I has no analyzer at " + analyzer[0]);
			BuildSite started = Builders.start(level, s.builder(), null, StarterBlueprints.RESEARCH_LAB_2.id(), s.placement());
			plan[0] = started.plan(level);
			site[0] = started.id();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(site[0] != null, "(setup) Level I not standing yet");
			assertDone(helper, s, site[0], plan[0]);
			List<BlockPos> analyzers = built(level, origin, StarterBlueprints.RESEARCH_LAB_2.size(), FOSSIL_ANALYZER);
			helper.assertTrue(analyzers.equals(List.of(analyzer[0])), "after the upgrade the analyzers stand at " + analyzers + ", not only at I's "
				+ analyzer[0].toShortString());
			List<String> lost = new ArrayList<>();
			for (BlockPos c : chests) {
				if (!(level.getBlockEntity(origin.offset(c)) instanceof Container box) || box.countItem(Items.GLISTERING_MELON_SLICE) != 7) {
					lost.add(c.toShortString() + "=" + level.getBlockState(origin.offset(c)).getBlock());
				}
			}
			helper.assertTrue(lost.isEmpty(), "I's chests (7 glistering melon each) lost or emptied by the upgrade at " + lost);
			takes(helper, s.player(), origin.offset(analyzer[0]), cobblemon("dome_fossil"), ModVillagers.FOSSIL_SCIENTIST, "research_lab_2's kept analyzer");
		});
	}

	/**
	 * Research Lab I with free materials: a restart (sites and builder saved and loaded) mid-STRUCTURE, and the Fossil
	 * Analyzer broken (a player, a creeper) right after it went in: the lab still ends as drawn, with one analyzer back in
	 * its cell that a Fossil Scientist takes.
	 */
	//$ gametest_ticks_batch HUGE_AREA '20000' '"qa_cob1122d_lab_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 20000, batch = "qa_cob1122d_lab_reload")
	public void aResearchLabRestartedAndItsAnalyzerBrokenMidBuildStillEndsAsDrawn(GameTestHelper helper) {
		Stage s = stage(helper, StarterBlueprints.RESEARCH_LAB.id(), new BlockPos(9, 2, 7), true);
		ServerLevel level = s.level();
		Villager[] builder = {s.builder()};
		BuildSite started = Builders.start(level, builder[0], null, s.id(), s.placement());
		BuildPlan plan = started.plan(level);
		UUID site = started.id();
		BlockPos origin = s.placement().origin();
		BlockPos analyzer = plan.targets().entrySet().stream().filter(e -> id(e.getValue()).equals(FOSSIL_ANALYZER)).map(Map.Entry::getKey)
			.findFirst().orElseThrow(() -> new GameTestAssertException("research_lab's plan places no fossil analyzer"));
		boolean[] reloaded = {false};
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(level).get(site);
			if (now == null) {
				return;
			}
			if (!reloaded[0] && now.stage() == BuildPlan.Stage.STRUCTURE && now.cursor() >= 40) {
				reloaded[0] = true;
				BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()),
					level.registryAccess());
				level.getDataStorage().set(BuildSiteManager.NAME, loaded);
				net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
				helper.assertTrue(builder[0].save(tag), "the builder wasn't saved");
				builder[0].discard();
				net.minecraft.world.entity.Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
				level.addFreshEntity(back);
				builder[0] = (Villager) back;
			}
			if (!broken[0] && id(level.getBlockState(analyzer)).equals(FOSSIL_ANALYZER)) {
				broken[0] = true;
				level.destroyBlock(analyzer, false);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never restarted mid-STRUCTURE");
			helper.assertTrue(broken[0], "the analyzer never went in");
			assertDone(helper, s, site, plan);
			List<BlockPos> analyzers = built(level, origin, StarterBlueprints.RESEARCH_LAB.size(), FOSSIL_ANALYZER);
			helper.assertTrue(analyzers.equals(List.of(analyzer.subtract(origin))), "analyzers at " + analyzers + ", drawn at "
				+ analyzer.subtract(origin).toShortString());
			takes(helper, s.player(), analyzer, cobblemon("dome_fossil"), ModVillagers.FOSSIL_SCIENTIST, "the put-back analyzer");
		});
	}

	// ---- shared: builds ---------------------------------------------------------------------------------------------------

	record Stage(ServerLevel level, ResourceLocation id, BlueprintData.Placement placement, Villager builder, ServerPlayer player) {
	}

	private static void buildThenTake(GameTestHelper helper, StarterBlueprints.Entry entry, ResourceLocation jobBlock, Item item, VillagerProfession job) {
		Stage s = stage(helper, entry.id(), new BlockPos(9, 2, 7), false);
		BuildSite site = Builders.start(s.level(), s.builder(), null, s.id(), s.placement());
		BuildPlan plan = site.plan(s.level());
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(stock.size() <= 108, entry.id() + " needs " + stock.size() + " stacks, more than four barrels (108)");
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.succeedWhen(() -> {
			assertDone(helper, s, site.id(), plan);
			BlockPos origin = s.placement().origin();
			List<BlockPos> jobs = built(s.level(), origin, entry.size(), jobBlock);
			helper.assertTrue(jobs.size() == 1, entry.id() + ": " + jobs.size() + " " + jobBlock + " built " + jobs);
			BlockPos station = origin.offset(jobs.get(0));
			takes(helper, s.player(), station, item, job, entry.id() + "'s " + jobBlock.getPath());
			if (job == ModVillagers.BALL_SMITH) {
				List<BlockPos> supplies = SupplyContainers.find(s.level(), station, null);
				helper.assertTrue(!supplies.isEmpty(), entry.id() + ": the Ball Smith at " + jobs.get(0).toShortString() + " finds no chest to work from");
			}
		});
	}

	/** Cells (template) of {@code block} standing in the built box. */
	private static List<BlockPos> built(ServerLevel level, BlockPos origin, net.minecraft.core.Vec3i size, ResourceLocation block) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			if (id(level.getBlockState(p)).equals(block) && PoiTypes.forState(level.getBlockState(p)).isPresent()) {
				out.add(p.subtract(origin));
			}
		}
		return out;
	}

	/** The test area cleared of leftovers, a bench and a builder at x 2-3, rules set for the test and put back after it. */
	private static Stage stage(GameTestHelper helper, ResourceLocation id, BlockPos at, boolean free) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		QaRebuildCreate1117e3CompatTests.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(wasFree, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			BuildSiteManager.get(level).forgetFinished(placement);
			level.getServer().getPlayerList().remove(player);
		});
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(1, 2, 2), Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		return new Stage(level, id, placement, builder, player);
	}

	/** {@code site} is finished and nothing of {@code plan} is left undone. */
	private static void assertDone(GameTestHelper helper, Stage s, UUID site, BuildPlan plan) {
		BuildSite now = BuildSiteManager.get(s.level()).get(site);
		helper.assertTrue(now == null, s.id() + " still building: " + (now == null ? "" : now.stage() + " " + now.status() + " missing " + now.missing()));
		List<BlockPos> unfinished = new ArrayList<>(plan.unfinished(s.level()));
		helper.assertTrue(unfinished.isEmpty(), s.id() + ": " + unfinished.size() + " blocks not as drawn, e.g. " + unfinished.stream().limit(4)
			.map(p -> p.subtract(s.placement().origin()).toShortString() + "=" + s.level().getBlockState(p) + " (drawn " + plan.targets().get(p) + ")").toList());
	}

	/** A jobless villager on an open cell beside {@code station}, sneak-clicked with {@code item}, takes {@code job} at that very block. */
	private static Villager takes(GameTestHelper helper, ServerPlayer player, BlockPos station, Item item, VillagerProfession job, String what) {
		ServerLevel level = helper.getLevel();
		BlockPos stand = null;
		for (BlockPos p : List.of(station.north(), station.south(), station.east(), station.west(), station.north().below(), station.south().below(),
			station.east().below(), station.west().below())) {
			if (open(level, p) && open(level, p.above()) && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
				stand = p;
				break;
			}
		}
		helper.assertTrue(stand != null, what + ": no open cell beside it at " + station.toShortString());
		Villager villager = EntityType.VILLAGER.create(level);
		villager.moveTo(Vec3.atBottomCenterOf(stand));
		level.addFreshEntity(villager);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, true, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		player.setShiftKeyDown(false);
		VillagerProfession got = villager.getVillagerData().getProfession();
		helper.assertTrue(got == job, what + ": " + BuiltInRegistries.ITEM.getKey(item) + " gave " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(got)
			+ ", not " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(job));
		Optional<BlockPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
		helper.assertTrue(site.equals(Optional.of(station)), what + ": works at " + site + ", not " + station.toShortString());
		return villager;
	}

	private static boolean open(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	private static Item cobblemon(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
		if (item == Items.AIR) {
			throw new GameTestAssertException("no cobblemon:" + path);
		}
		return item;
	}

	private static ResourceLocation id(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock());
	}

	// ---- shared: drawings and the walk ------------------------------------------------------------------------------------

	/** A drawing's cells (a basement below y = 0, as the library loads it); outside them air above the ground, dirt below. */
	private record Drawing(String name, Blueprint blueprint, Map<BlockPos, BlockState> blocks) {
		BlockState at(BlockPos p) {
			BlockState s = blocks.get(p);
			return s != null ? s : p.getY() >= 0 ? Blocks.AIR.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		}

		List<BlockPos> where(ResourceLocation block) {
			return blocks.entrySet().stream().filter(e -> id(e.getValue()).equals(block)
				&& (!e.getValue().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
				|| e.getValue().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
				== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER))
				.map(Map.Entry::getKey).sorted().toList();
		}

		List<BlockPos> jobSitesOf(ResourceLocation block) {
			return blocks.entrySet().stream().filter(e -> id(e.getValue()).equals(block) && PoiTypes.forState(e.getValue()).isPresent())
				.map(Map.Entry::getKey).sorted().toList();
		}

		/** Every job site but {@code allowed} (POIs: not a bed, lightning rod, portal, lodestone or hive), named. */
		List<String> otherJobSites(Set<ResourceLocation> allowed) {
			List<String> out = new ArrayList<>();
			blocks.forEach((p, s) -> {
				if (allowed.contains(id(s))) {
					return;
				}
				if (PoiTypes.forState(s).filter(h -> !h.is(PoiTypes.HOME) && !h.is(PoiTypes.LIGHTNING_ROD) && !h.is(PoiTypes.NETHER_PORTAL)
					&& !h.is(PoiTypes.LODESTONE) && !h.is(PoiTypes.BEEHIVE) && !h.is(PoiTypes.BEE_NEST)).isPresent()) {
					out.add(name + ": another job site, " + id(s) + " at " + p.toShortString());
				}
			});
			out.sort(null);
			return out;
		}

		List<BlockPos> chests() {
			return blocks.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof ChestBlock).map(Map.Entry::getKey).sorted().toList();
		}

		List<BlockPos> bedHeads() {
			return blocks.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof BedBlock && e.getValue().getValue(BedBlock.PART) == BedPart.HEAD)
				.map(Map.Entry::getKey).sorted().toList();
		}

		/** Chests no player walks up to (within 2 across, 1 up or down), every door, trapdoor and gate opened. */
		List<String> chestsUnreached() {
			Set<BlockPos> walked = walk(true);
			List<String> out = new ArrayList<>();
			for (BlockPos c : chests()) {
				if (!near(walked, c, 2)) {
					out.add(name + ": the chest " + c.toShortString() + " can't be walked up to");
				}
			}
			return out;
		}

		Set<BlockPos> walk(boolean player) {
			return QaRebuiltCobblemon1122dCompatTests.walk(this::at, blueprint.size().getX(), blueprint.size().getZ(), blueprint.size().getY(), 0, player);
		}
	}

	private static Drawing drawing(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new GameTestAssertException("missing " + entry.id()));
		Map<BlockPos, BlockState> cells = new HashMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			cells.put(e.pos(), e.state());
		}
		helper.assertTrue(cells.values().stream().anyMatch(s -> id(s).getNamespace().equals("cobblemon")) || entry == StarterBlueprints.TRAINERS_HOUSE
			|| entry == StarterBlueprints.BALL_WORKSHOP || entry == StarterBlueprints.ARENA || entry == StarterBlueprints.ARENA_2,
			"(setup) " + entry.id() + " loaded with no Cobblemon block: is Cobblemon in this run?");
		return new Drawing(entry.id().getPath(), blueprint, cells);
	}

	static boolean within(BlockPos a, BlockPos b, int across, int vertical) {
		return Math.abs(a.getX() - b.getX()) <= across && Math.abs(a.getZ() - b.getZ()) <= across && Math.abs(a.getY() - b.getY()) <= vertical;
	}

	/** A villager's feet or head can be in it: a wooden door (he opens it), a ladder, or nothing over a fifth of a block tall. */
	private static boolean open(BlockState state, boolean player) {
		if (state.getBlock() instanceof DoorBlock && (player || !state.is(Blocks.IRON_DOOR))) {
			return true;
		}
		if (player && (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock)) {
			return true;
		}
		if (state.is(Blocks.LADDER)) {
			return true;
		}
		var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.2;
	}

	private static boolean noFloor(BlockState state) {
		return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof FenceGateBlock;
	}

	private static boolean standable(Function<BlockPos, BlockState> at, BlockPos feet, boolean player) {
		BlockState here = at.apply(feet);
		if (!open(here, player) || !open(at.apply(feet.above()), player) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockState below = at.apply(feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER) || (!open(below, false) && !noFloor(below) && below.getFluidState().isEmpty());
	}

	/** Every spot reached on foot from the street in front (z -2..-1 at {@code streetY}): level, a step up, up to three down, ladders. */
	private static Set<BlockPos> walk(Function<BlockPos, BlockState> at, int sx, int sz, int top, int streetY, boolean player) {
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (int x = -2; x <= sx + 1; x++) {
			for (int z = -2; z <= -1; z++) {
				BlockPos s = new BlockPos(x, streetY, z);
				if (standable(at, s, player) && reached.add(s)) {
					queue.add(s);
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			List<BlockPos> next = new ArrayList<>();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(d);
				next.add(side);
				if (open(at.apply(pos.above(2)), player)) {
					next.add(side.above());
				}
				for (int drop = 1; drop <= 3; drop++) {
					if (!open(at.apply(side.below(drop - 1)), player) || !open(at.apply(side.below(drop - 1).above()), player)) {
						break;
					}
					next.add(side.below(drop));
				}
			}
			// climbing needs a rung where the feet are (a missing rung is a fall, not a step)
			if (at.apply(pos).is(Blocks.LADDER) || at.apply(pos.above()).is(Blocks.LADDER) && !open(at.apply(pos.below()), false)) {
				next.add(pos.above());
			}
			if (at.apply(pos.below()).is(Blocks.LADDER)) {
				next.add(pos.below());
			}
			for (BlockPos nx : next) {
				if (nx.getX() < -2 || nx.getX() > sx + 1 || nx.getZ() < -2 || nx.getZ() > sz + 1 || nx.getY() < -8 || nx.getY() > top) {
					continue;
				}
				if (!reached.contains(nx) && standable(at, nx, player)) {
					reached.add(nx);
					queue.add(nx);
				}
			}
		}
		return reached;
	}

	/** A spot right beside {@code target} (one of its four sides, its level or the one below) was reached. */
	private static boolean beside(Set<BlockPos> reached, BlockPos target) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (reached.contains(target.relative(d)) || reached.contains(target.relative(d).below())) {
				return true;
			}
		}
		return false;
	}

	/** A reached spot within {@code across} blocks each way and one up or down of {@code target}. */
	private static boolean near(Set<BlockPos> reached, BlockPos target, int across) {
		for (BlockPos p : BlockPos.betweenClosed(target.offset(-across, -1, -across), target.offset(across, 1, across))) {
			if (reached.contains(p)) {
				return true;
			}
		}
		return false;
	}
}
