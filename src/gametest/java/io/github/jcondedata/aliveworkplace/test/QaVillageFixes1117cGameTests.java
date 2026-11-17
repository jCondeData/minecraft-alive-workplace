package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.TradeFairs;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.TradeFairPower;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.wonder.ObservatoryPowers;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1117-1253), from each bug's text, not the fix's code: six landed village fixes checked from the other sides
 * their first tests didn't try.
 * <ul>
 * <li>B1345: a builder's level-up unlocks in his Table's village, wherever he stands: in another village too; a builder
 * with no Table, by where he stands; a builder hired at a Table far from every hall, into the server-wide set.</li>
 * <li>B1348: a Chapel wedding through the request is chronicled once, still announced and still a marriage; a plain bell
 * wedding is still chronicled at the bell, once.</li>
 * <li>B1352: the lookups the first tests left out (the piglins, the hall's build pages) leave out a villager in the
 * square's corner; the villager count's circle from both sides of its edge.</li>
 * <li>B1356: an orphan hall point nearer than a real hall doesn't hide the real one (Reach, the raid dens, the fires,
 * VillageHalls.containing).</li>
 * <li>B1361/B1362: the Observatory's Coming days with no Market Square or markets off; a trade fair held late in the
 * morning sends its traders away by dusk.</li>
 * </ul>
 */
public class QaVillageFixes1117cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String BUILD = "aliveworkplace_test:build_area";

	// --- B1345: a builder's level-up unlocks in his Table's village ------------------------------------------------

	/**
	 * The village A builder (Table 4 blocks from hall A) levels up while standing beside another village's hall B, 35 blocks
	 * away: the three builds unlock in A, not in B and not in the server-wide set; a player by A's Table is told, a player
	 * by hall B (where he stands) isn't.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaVf1117cLvlOtherVillage"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVf1117cLvlOtherVillage")
	public void aBuilderLevelingUpInAnotherVillageUnlocksInHisTablesVillage(GameTestHelper helper) {
		QaLevelUpVillage1116bGameTests.setUp(helper);
		BlockPos hallARel = new BlockPos(2, 2, 2);
		BlockPos tableRel = new BlockPos(6, 2, 2);
		BlockPos hallBRel = new BlockPos(27, 2, 27);
		QaBlueprintUnlocksGameTests.village(helper, hallARel, tableRel);
		helper.setBlock(hallBRel, ModBlocks.VILLAGE_HALL);
		Villager v = QaLevelUpVillage1116bGameTests.builder(helper, new BlockPos(25, 2, 27));
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hallA = helper.absolutePos(hallARel);
			BlockPos hallB = helper.absolutePos(hallBRel);
			helper.assertTrue(hallA.equals(BlueprintUnlocks.village(level, helper.absolutePos(tableRel))), "setup: the Table isn't in village A");
			helper.assertTrue(hallB.equals(BlueprintUnlocks.village(level, v.blockPosition())), "setup: the builder doesn't stand in village B");
			Set<ResourceLocation> wild = Set.copyOf(BlueprintUnlocks.unlocked(level, null));
			Set<ResourceLocation> inB = Set.copyOf(BlueprintUnlocks.unlocked(level, hallB));
			List<ResourceLocation> next = BlueprintUnlocks.nextForLevelUp(level, hallA);
			helper.assertTrue(next.size() == BlueprintUnlocks.PER_LEVEL, "setup: a level-up would unlock " + next);
			QaStageFiveGameTests.Client byTable = QaBlueprintUnlocksGameTests.client(helper, tableRel);
			QaStageFiveGameTests.Client byB = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("qaVfB"));
			byB.player().moveTo(hallB.getX() + 1.5, hallB.getY(), hallB.getZ() + 0.5, 0f, 0f);
			byTable.seen().clear();
			byB.seen().clear();
			QaLevelUpVillage1116bGameTests.hireAndLevel(helper, v, tableRel);
			for (ResourceLocation id : next) {
				helper.assertTrue(BlueprintUnlocks.state(level, hallA, id) == BlueprintUnlocks.State.UNLOCKED,
					"his Table's village didn't get " + id + ": " + BlueprintUnlocks.state(level, hallA, id));
				String line = "The village's library has a new blueprint: " + Blueprints.displayName(id).getString();
				helper.assertTrue(QaLevelUpVillage1116bGameTests.texts(byTable.seen()).contains(line),
					"a player by his Table wasn't told '" + line + "', saw " + QaLevelUpVillage1116bGameTests.texts(byTable.seen()));
				helper.assertFalse(QaLevelUpVillage1116bGameTests.texts(byB.seen()).contains(line),
					"a player in village B, where the builder only stood, was told '" + line + "'");
			}
			helper.assertTrue(BlueprintUnlocks.unlocked(level, hallB).equals(inB),
				"village B, where he only stood, changed from " + inB + " to " + BlueprintUnlocks.unlocked(level, hallB));
			helper.assertTrue(BlueprintUnlocks.unlocked(level, null).equals(wild),
				"the server-wide set changed from " + wild + " to " + BlueprintUnlocks.unlocked(level, null));
			LOGGER.info("qa village fixes 1117c: builder of A leveled up in B; {} unlocked in A only", next);
			helper.succeed();
		});
	}

	/**
	 * A builder with no Table at all (never hired, so where he stands is all there is) leveling up beside the hall: the
	 * village gets the three builds, the server-wide set doesn't change.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaVf1117cLvlNoTable"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVf1117cLvlNoTable")
	public void aBuilderWithNoTableLevelingUpInTheVillageUnlocksThere(GameTestHelper helper) {
		QaLevelUpVillage1116bGameTests.setUp(helper);
		BlockPos hallRel = new BlockPos(15, 2, 15);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		Villager v = QaLevelUpVillage1116bGameTests.builder(helper, new BlockPos(19, 2, 15));
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallRel);
			helper.assertTrue(Builders.benchPos(v).isEmpty(), "setup: the builder has a Table: " + Builders.benchPos(v));
			Set<ResourceLocation> wild = Set.copyOf(BlueprintUnlocks.unlocked(level, null));
			List<ResourceLocation> next = BlueprintUnlocks.nextForLevelUp(level, hall);
			BuilderLevels.addXp(level, v, 30, null);
			helper.assertTrue(v.getVillagerData().getLevel() == 2, "setup: the builder didn't level up: " + v.getVillagerData().getLevel());
			for (ResourceLocation id : next) {
				helper.assertTrue(BlueprintUnlocks.state(level, hall, id) == BlueprintUnlocks.State.UNLOCKED,
					"a builder with no Table leveled up in the village and it didn't get " + id + ": " + BlueprintUnlocks.state(level, hall, id));
			}
			helper.assertTrue(BlueprintUnlocks.unlocked(level, null).equals(wild),
				"the server-wide set changed from " + wild + " to " + BlueprintUnlocks.unlocked(level, null));
			helper.succeed();
		});
	}

	/**
	 * A builder hired at a Table far from every hall (its library is the server-wide set) levels up standing beside a
	 * hall: the builds go into the server-wide set his Table reads, and the village he only stands in doesn't change.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaVf1117cLvlWildTable"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVf1117cLvlWildTable")
	public void aBuilderOfAWildTableLevelingUpInAVillageUnlocksInTheServerWideSet(GameTestHelper helper) {
		QaLevelUpVillage1116bGameTests.setUp(helper);
		BlockPos tableRel = new BlockPos(2, 2, 2);
		BlockPos hallRel = new BlockPos(27, 2, 27);
		helper.setBlock(tableRel, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		Villager v = QaLevelUpVillage1116bGameTests.builder(helper, new BlockPos(25, 2, 27));
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallRel);
			helper.assertTrue(BlueprintUnlocks.village(level, helper.absolutePos(tableRel)) == null, "setup: a hall is in reach of the Table");
			Set<ResourceLocation> inVillage = Set.copyOf(BlueprintUnlocks.unlocked(level, hall));
			List<ResourceLocation> next = BlueprintUnlocks.nextForLevelUp(level, null);
			helper.assertTrue(next.size() == BlueprintUnlocks.PER_LEVEL, "setup: a level-up would unlock " + next);
			QaLevelUpVillage1116bGameTests.hireAndLevel(helper, v, tableRel);
			for (ResourceLocation id : next) {
				helper.assertTrue(BlueprintUnlocks.state(level, null, id) == BlueprintUnlocks.State.UNLOCKED,
					"the server-wide set his Table reads didn't get " + id + ": " + BlueprintUnlocks.state(level, null, id));
			}
			helper.assertTrue(BlueprintUnlocks.unlocked(level, hall).equals(inVillage),
				"the village he only stood in changed from " + inVillage + " to " + BlueprintUnlocks.unlocked(level, hall));
			helper.succeed();
		});
	}

	// --- B1348: one wedding, one chronicle line ----------------------------------------------------------------------

	/**
	 * The chapel_wedding request done: Dara and Odo are married to each other, the village still hears the wedding bells
	 * (once), and the chronicle has exactly the Chapel line, also after the bell's next round.
	 */
	//$ gametest_ticks_batch BUILD '80' '"qaVf1117cChapelOnce"'
	@GameTest(template = BUILD, timeoutTicks = 80, batch = "qaVf1117cChapelOnce")
	public void aChapelWeddingIsAnnouncedAndChronicledOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(PersonalRequestGameTests.HALL);
			Villager[] couple = QaRequestFixes1116cGameTests.courting(helper, 3);
			Villager dara = couple[0];
			Villager odo = couple[1];
			List<Component> heard = new ArrayList<>();
			ServerPlayer me = QaRequestFixes1116cGameTests.listener(helper, heard, "qaVfChapel", new BlockPos(5, 2, 6), dara, 300);
			PersonalRequestGameTests.accepted(helper, "chapel_wedding", dara, me);
			QaRequestFixes1116cGameTests.chapelFinished(helper);
			heard.clear();
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			helper.assertTrue(PersonalRequests.of(dara) == null, "setup: the request is still open after the Chapel");
			helper.assertTrue(Couples.partner(dara).married() && Couples.partner(dara).id().equals(odo.getUUID())
				&& Couples.partner(odo).married() && Couples.partner(odo).id().equals(dara.getUUID()), "Dara and Odo aren't married to each other");
			List<String> bells = QaRequestFixes1116cGameTests.strings(heard).stream().filter(s -> s.startsWith("Wedding bells in ")).distinct().toList();
			helper.assertTrue(bells.size() == 1 && bells.get(0).endsWith(": Dara and Odo are married!"),
				"the village's wedding announcement for the Chapel wedding: " + QaRequestFixes1116cGameTests.strings(heard));
			String chapel = "Dara and Odo were married in the new Chapel, and the whole village sat down to a feast";
			List<String> weddings = PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING);
			helper.assertTrue(weddings.equals(List.of(chapel)), "the chronicle's weddings after the Chapel wedding: " + weddings);
			level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 7000);
			Couples.round(level, hall);
			Couples.round(level, hall);
			weddings = PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING);
			helper.assertTrue(weddings.equals(List.of(chapel)), "the chronicle's weddings after the bell's next rounds: " + weddings);
			helper.succeed();
		});
	}

	/**
	 * The other side: a couple marrying at the bell after 5 days' courting, with no request, is still chronicled "married
	 * at the bell", once (also after another round), and the village hears the bells.
	 */
	//$ gametest_ticks_batch BUILD '80' '"qaVf1117cBellOnce"'
	@GameTest(template = BUILD, timeoutTicks = 80, batch = "qaVf1117cBellOnce")
	public void aBellWeddingIsStillChronicledAtTheBellOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(PersonalRequestGameTests.HALL);
			Villager[] couple = QaRequestFixes1116cGameTests.courting(helper, 5);
			List<Component> heard = new ArrayList<>();
			QaRequestFixes1116cGameTests.listener(helper, heard, "qaVfBell", new BlockPos(5, 2, 6), couple[0], 0);
			level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 7000);
			Couples.round(level, hall);
			helper.assertTrue(Couples.partner(couple[0]).married() && Couples.partner(couple[1]).married(), "setup: not married at the bell at 5 days");
			Couples.round(level, hall);
			List<String> weddings = PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING);
			helper.assertTrue(weddings.equals(List.of("Dara and Odo were married at the bell")), "the chronicle's weddings for a bell wedding: " + weddings);
			helper.assertTrue(QaRequestFixes1116cGameTests.strings(heard).stream().anyMatch(s -> s.startsWith("Wedding bells in ") && s.endsWith(": Dara and Odo are married!")),
				"no wedding bells heard: " + QaRequestFixes1116cGameTests.strings(heard));
			helper.succeed();
		});
	}

	// --- B1352: the village's circle, not the square's corners --------------------------------------------------------

	private static final BlockPos CIRCLE_HALL = new BlockPos(13, 2, 13);

	/** A Village Hall at {@link #CIRCLE_HALL} with a 16-block village, nothing near it from earlier batches; put back after. */
	private static BlockPos circleVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		helper.setBlock(CIRCLE_HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(CIRCLE_HALL);
	}

	private static Villager builderAt(GameTestHelper helper, BlockPos at, String name) {
		Villager v = QaVillageCircleGameTests.at(helper, at, name);
		BlockPos bench = at.offset(1, 0, 0);
		helper.getLevel().setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
		Builders.employ(helper.getLevel(), v, bench);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		v.setVillagerXp(1);
		return v;
	}

	private static Object call(Class<?> owner, String name, Class<?>[] types, Object... args) {
		try {
			Method m = owner.getDeclaredMethod(name, types);
			m.setAccessible(true);
			return m.invoke(null, args);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("couldn't call " + owner.getSimpleName() + "." + name + ": " + e);
		}
	}

	/**
	 * The villager count's circle from both sides of its edge: 16 blocks east and 16 south of the hall (on the rim) are
	 * counted; 16 east and 1 south (257 > 256) and 12 by 11 (265) are not, though all four stand in the square.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVf1117cCircleEdge"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVf1117cCircleEdge")
	public void theVillagerCountTakesTheCirclesRimNotPastIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = circleVillage(helper);
		List<Villager> all = new ArrayList<>();
		for (int[] d : new int[][] {{16, 0}, {0, 16}, {16, 1}, {12, 11}}) {
			BlockPos at = hall.offset(d[0], 0, d[1]);
			helper.assertTrue(VillageHalls.area(hall).contains(at.getCenter()), "setup: " + at + " isn't in the square");
			all.add(QaVillageCircleGameTests.at(helper, at, "V" + d[0] + "_" + d[1]));
		}
		int count = ResearchTrees.count(level, hall, "villagers");
		all.forEach(Villager::discard);
		helper.assertTrue(count == 2, "the villagers counter is " + count + ", not the 2 on the circle's rim (16,0 and 0,16; not 16,1 or 12,11)");
		helper.succeed();
	}

	/**
	 * A raiding piglin right beside a villager in the square's corner (outside the village) doesn't go for him: it heads
	 * for the village's own villager by the hall instead, more than its sight away.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVf1117cPiglin"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVf1117cPiglin")
	public void aRaidingPiglinLeavesTheSquaresCornerAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = circleVillage(helper);
		Villager in = QaVillageCircleGameTests.at(helper, hall.offset(-2, 0, -2), "Target");
		BlockPos cornerAt = hall.offset(14, 0, 14);
		helper.assertTrue(VillageHalls.area(hall).contains(cornerAt.getCenter()) && !VillageHalls.contains(hall, cornerAt), "setup: the corner");
		Villager out = QaVillageCircleGameTests.at(helper, cornerAt, "Stranger");
		Piglin piglin = EntityType.PIGLIN.create(level);
		piglin.moveTo(cornerAt.getX() + 1.5, cornerAt.getY(), cornerAt.getZ() + 0.5, 0, 0);
		piglin.setImmuneToZombification(true);
		piglin.setNoAi(true);
		level.addFreshEntity(piglin);
		helper.assertTrue(piglin.distanceTo(in) > 16, "setup: the village's villager is in the piglin's sight: " + piglin.distanceTo(in));
		call(io.github.jcondedata.aliveworkplace.threat.Piglins.class, "steer", new Class<?>[] {ServerLevel.class, BlockPos.class, Mob.class}, level, hall, piglin);
		Object target = call(io.github.jcondedata.aliveworkplace.threat.Piglins.class, "fighting", new Class<?>[] {Mob.class}, piglin);
		var walk = piglin.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
		// across only: the walk target is put on the ground's surface (the heightmap), whatever the test floor's height
		double before = Math.hypot(piglin.getX() - in.getX(), piglin.getZ() - in.getZ());
		double after = walk.map(w -> Math.hypot(w.getTarget().currentBlockPosition().getX() + 0.5 - in.getX(),
			w.getTarget().currentBlockPosition().getZ() + 0.5 - in.getZ())).orElse(Double.MAX_VALUE);
		String why = "target " + target + ", walk " + walk.map(w -> w.getTarget().currentBlockPosition().toString()).orElse("none");
		piglin.discard();
		in.discard();
		out.discard();
		helper.assertTrue(target != out, "the raiding piglin went for the villager in the square's corner, outside the village (" + why + ")");
		helper.assertTrue(after < before - 6, "the piglin isn't heading for the village's villager: " + before + " across before, its walk target "
			+ after + " (" + why + ")");
		helper.succeed();
	}

	/**
	 * The hall's build pages (UpgradesPage) give orders only to the village's builders: one in the square's corner isn't
	 * among them, and isn't named as "another player's builder" either; the one by the hall is.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVf1117cUpgradesPage"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVf1117cUpgradesPage")
	@SuppressWarnings("unchecked")
	public void theBuildPagesLeaveOutABuilderInTheSquaresCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = circleVillage(helper);
		Class<?> page = io.github.jcondedata.aliveworkplace.hall.UpgradesPage.class;
		Class<?>[] types = {ServerLevel.class, BlockPos.class, BlockPos.class, Predicate.class};
		Predicate<Villager> anyone = v -> true;
		Villager out = builderAt(helper, hall.offset(14, 0, 14), "Stranger");
		List<Villager> found = (List<Villager>) call(page, "builders", types, level, hall, hall, anyone);
		helper.assertTrue(!found.contains(out), "the builder in the square's corner, outside the village, is on the build pages");
		Villager in = builderAt(helper, hall.offset(2, 0, 2), "Mason");
		found = (List<Villager>) call(page, "builders", types, level, hall, hall, anyone);
		helper.assertTrue(found.contains(in) && !found.contains(out), "the build pages' builders: " + found.stream().map(v -> v.getName().getString()).toList());

		// another player's builder: only the village's one is named
		boolean rule = level.getGameRules().getBoolean(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILDER_OWNERSHIP);
		level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILDER_OWNERSHIP).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILDER_OWNERSHIP)
			.set(rule, level.getServer()));
		QaStageFiveGameTests.Client me = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaVfUp"));
		me.player().moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5, 0f, 0f);
		io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_EMPLOYER.set(out,
			new io.github.jcondedata.aliveworkplace.build.Employer(UUID.randomUUID(), "Outsider"));
		helper.assertFalse(io.github.jcondedata.aliveworkplace.build.Friends.mayCommand(me.player(), out), "setup: the player may command another's builder");
		Class<?>[] emp = {ServerLevel.class, BlockPos.class, ServerPlayer.class};
		String named = (String) call(page, "othersEmployer", emp, level, hall, me.player());
		helper.assertTrue(named.isEmpty(), "the build pages name the employer of a builder outside the village: '" + named + "'");
		io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_EMPLOYER.set(in,
			new io.github.jcondedata.aliveworkplace.build.Employer(UUID.randomUUID(), "Neighbor"));
		named = (String) call(page, "othersEmployer", emp, level, hall, me.player());
		helper.assertTrue(named.equals("Neighbor"), "the village's builder's employer isn't named: '" + named + "'");
		in.discard();
		out.discard();
		helper.succeed();
	}

	// --- B1356: a hall point with no hall is no village, and doesn't hide a real one ----------------------------------

	private static final BlockPos REAL_AT = new BlockPos(4, 2, 15);
	private static final BlockPos ORPHAN_AT = new BlockPos(14, 2, 15);

	/**
	 * With radius 12, a real hall at x=4 and an orphan point (no hall block, loaded chunk) at x=14: a spot at x=12, 8
	 * from the real hall and 2 from the orphan, is the real hall's for every lookup (Reach.village, a raid den's nearest
	 * hall, the fire's village, VillageHalls.containing); a spot at x=17, 3 from the orphan and 13 from the real hall, is
	 * in no village.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaVf1117cOrphanNearer"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaVf1117cOrphanNearer")
	public void aNearerHallPointWithNoHallDoesntHideTheRealHall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 12;
		BlockPos centre = helper.absolutePos(new BlockPos(15, 2, 15));
		for (BlockPos old : level.getPoiManager().findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), p -> true, centre, 64, PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(old, false);
			if (level.getPoiManager().getType(old).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent()) {
				level.getPoiManager().remove(old);
			}
		}
		BlockPos orphan = helper.absolutePos(ORPHAN_AT);
		VillageFire.forgetHalls();
		Leftovers.after(helper, () -> {
			if (!(level.getBlockEntity(orphan) instanceof VillageHallBlockEntity)
				&& level.getPoiManager().getType(orphan).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent()) {
				level.getPoiManager().remove(orphan);
			}
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			VillageFire.forgetHalls();
		});
		helper.setBlock(REAL_AT, ModBlocks.VILLAGE_HALL);
		BlockPos real = helper.absolutePos(REAL_AT);
		helper.assertTrue(level.getBlockState(orphan).isAir(), "setup: the orphan point's block isn't air");
		level.getPoiManager().add(orphan, BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(ModVillagers.VILLAGE_HALL_POI));
		VillageHalls.forgetNearest();
		helper.assertTrue(level.getPoiManager().getType(orphan).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent(), "setup: no orphan point");
		helper.assertTrue(level.getPoiManager().getType(real).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent(), "setup: the real hall has no point");

		BlockPos between = real.offset(8, 0, 0);
		helper.assertTrue(Reach.village(level, between).equals(Optional.of(real)), "a work block 8 from the real hall and 2 from the orphan point is in the village of "
			+ Reach.village(level, between));
		helper.assertTrue(RaidDens.hallNear(level, between).equals(Optional.of(real)), "a raid den 2 from the orphan point and 8 from the real hall has the hall "
			+ RaidDens.hallNear(level, between));
		helper.assertTrue(VillageFire.hall(level, between).equals(Optional.of(real)), "a fire 8 from the real hall and 2 from the orphan point is in the village of "
			+ VillageFire.hall(level, between));
		helper.assertTrue(VillageHalls.containing(level, between).equals(Optional.of(real)), "VillageHalls.containing gives "
			+ VillageHalls.containing(level, between));
		BlockPos past = real.offset(13, 0, 0);
		helper.assertTrue(Reach.village(level, past).isEmpty(), "a work block 3 from the orphan point, past the real hall's radius, is in the village of "
			+ Reach.village(level, past));
		helper.assertTrue(VillageHalls.containing(level, past).isEmpty(), "a spot 3 from the orphan point is contained by " + VillageHalls.containing(level, past));
		helper.succeed();
	}

	// --- B1361 and B1362: no market foretold that won't come; traders gone by dusk ------------------------------------

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/**
	 * The Observatory's Coming days list (B1361's other half): no market day with no Market Square, the market day the
	 * Gazette and the Seer tell (MarketDays.nextDay) once a square is finished, and none again with market days off.
	 */
	//$ gametest_batch BUILD '"qaVf1117cObservatoryMarket"'
	@GameTest(template = BUILD, batch = "qaVf1117cObservatoryMarket")
	public void theObservatoryListsNoMarketDayWithoutAMarket(GameTestHelper helper) {
		BlockPos hall = (BlockPos) call(ObservatoryPowerGameTests.class, "observatory", new Class<?>[] {GameTestHelper.class}, helper);
		ServerLevel level = helper.getLevel();
		Leftovers.finished(helper);
		boolean markets = MarketDays.ENABLED;
		MarketDays.ENABLED = true;
		Leftovers.after(helper, () -> MarketDays.ENABLED = markets);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(ObservatoryPowers.foretells(level, hall), "setup: the Observatory doesn't foretell");
		helper.assertTrue(MarketDays.square(level, hall).isEmpty(), "setup: a Market Square before one was built");
		List<ObservatoryPowers.Coming> coming = ObservatoryPowers.comingDays(level, hall);
		helper.assertTrue(coming.stream().noneMatch(c -> key(c.what()).equals("screen.aliveworkplace.coming.market")),
			"the Coming days list a market day for a village with no Market Square: " + coming.stream().map(c -> key(c.what()) + "@" + c.day()).toList());
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(4, 2, 4)), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.MARKET_SQUARE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		helper.assertTrue(MarketDays.square(level, hall).isPresent(), "setup: the finished Market Square isn't the village's");
		long day = MarketDays.nextDay(level, hall, entity.lastMarketDay());
		coming = ObservatoryPowers.comingDays(level, hall);
		List<ObservatoryPowers.Coming> markets1 = coming.stream().filter(c -> key(c.what()).equals("screen.aliveworkplace.coming.market")).toList();
		helper.assertTrue(markets1.size() == 1 && markets1.get(0).day() == day && markets1.get(0).where().isPresent(),
			"with a Market Square the Coming days don't list the market on day " + day + " at the square: " + coming.stream().map(c -> key(c.what()) + "@" + c.day()).toList());
		helper.assertValueEqual(markets1.get(0).what().getString(), Component.translatable("screen.aliveworkplace.coming.market").getString(), "the market's line");
		MarketDays.ENABLED = false;
		coming = ObservatoryPowers.comingDays(level, hall);
		helper.assertTrue(coming.stream().noneMatch(c -> key(c.what()).equals("screen.aliveworkplace.coming.market")),
			"the Coming days list a market day with market days off: " + coming.stream().map(c -> key(c.what()) + "@" + c.day()).toList());
		helper.succeed();
	}

	/**
	 * The Gazette dates only a market that will come (B1361), on the market day itself: at 500 (before market time) and
	 * at 5000 before the round, "today"; once this morning's market is held, or at 7000 with none held (market time
	 * over, so none comes today), the next one: "in 7 days (day N+7)".
	 */
	//$ gametest_batch BIG '"qaVf1117cGazetteMarketDay"'
	@GameTest(template = BIG, batch = "qaVf1117cGazetteMarketDay")
	public void theGazetteOnMarketDayTellsTodayOnlyWhileTheMarketIsToCome(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean markets = MarketDays.ENABLED;
		long dayTime = level.getDayTime();
		VillageHalls.RADIUS = 32;
		MarketDays.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			MarketDays.ENABLED = markets;
			level.setDayTime(dayTime);
		});
		BlockPos hallRel = new BlockPos(20, 2, 20);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallRel);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.MARKET_SQUARE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		long day = 80;
		while (!MarketDays.isMarketDay(hall, day)) {
			day++;
		}
		String later = "Next market day: in 7 days (day " + (day + 7) + ")";
		for (int at : new int[] {500, 5000, 7000}) {
			level.setDayTime((day - 1) * 24000L + at);
			entity.setLastMarketDay(-1);
			helper.assertTrue(Chronicle.day(level) == day, "setup: today is day " + Chronicle.day(level) + ", not the market day " + day);
			String calendar = calendar(level, hall);
			String expected = at < 6000 ? "Next market day: today" : later;
			helper.assertTrue(calendar.contains(expected), "on market day at " + at + " with no market held yet the Gazette says: "
				+ calendar.replace('\n', ' ') + " (expected '" + expected + "')");
		}
		level.setDayTime((day - 1) * 24000L + 1000);
		entity.setLastMarketDay(-1);
		helper.assertTrue(MarketDays.tick(level, hall, entity), "setup: no market at 1000 on market day");
		level.getEntitiesOfClass(WanderingTrader.class, new AABB(hall).inflate(40), WanderingTrader::isAlive).forEach(WanderingTrader::discard);
		String held = calendar(level, hall);
		helper.assertTrue(held.contains(later), "after this morning's market the Gazette says: " + held.replace('\n', ' ') + " (expected '" + later + "')");
		helper.succeed();
	}

	private static String calendar(ServerLevel level, BlockPos hall) {
		List<Component> pages = io.github.jcondedata.aliveworkplace.printer.Gazette.gazettePages(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall));
		return pages.stream().map(Component::getString).filter(p -> p.startsWith("THE CALENDAR")).findFirst().orElse("(no calendar page)");
	}

	/**
	 * A trade fair (the Festival Cup's or a legend's, same traders as a market) held late in the morning, at 5000: its
	 * traders leave by dusk (12000), as the market's do since B1362; one held at 1000 keeps the usual stay.
	 */
	//$ gametest_batch BIG '"qaVf1117cFairDusk"'
	@GameTest(template = BIG, batch = "qaVf1117cFairDusk")
	public void aTradeFairHeldLateInTheMorningLeavesByDusk(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long dayTime = level.getDayTime();
		VillageHalls.RADIUS = 32;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.setDayTime(dayTime);
		});
		BlockPos hallRel = new BlockPos(20, 2, 20);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallRel);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		Map<Integer, List<Integer>> stays = new HashMap<>();
		for (int start : new int[] {1000, 5000, 5999}) {
			level.setDayTime(80 * 24000L + start);
			List<WanderingTrader> came = TradeFairs.hold(level, hall, new TradeFairPower(10, 3, 10));
			helper.assertTrue(!came.isEmpty(), "setup: no fair traders at " + start);
			stays.put(start, came.stream().map(WanderingTrader::getDespawnDelay).toList());
			came.forEach(WanderingTrader::discard);
			TradeFairs.takeDownBunting(level, entity);
			level.getEntitiesOfClass(FireworkRocketEntity.class, new AABB(hall).inflate(40)).forEach(FireworkRocketEntity::discard);
		}
		for (Map.Entry<Integer, List<Integer>> e : stays.entrySet()) {
			for (int stay : e.getValue()) {
				helper.assertTrue(stay > 0 && e.getKey() + stay <= 12000,
					"a fair trader who came at " + e.getKey() + " stays " + stay + ", until " + (e.getKey() + stay) + " (dusk is 12000)");
			}
		}
		helper.assertTrue(stays.get(1000).stream().allMatch(s -> s == MarketDays.STAY), "a fair at 1000 doesn't keep the usual stay: " + stays.get(1000));
		helper.succeed();
	}
}
