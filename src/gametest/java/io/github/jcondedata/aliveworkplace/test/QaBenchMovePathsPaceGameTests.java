package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1026-0338), from the builder guide and wiki:
 * <ul>
 * <li>"Stand next to the builder and sneak-right-click the other Blueprint Table with an empty hand: they work there from
 * now on, with their build, and the old table stays where it is, free for someone else" (wiki: the Builder nearest the
 * player within 6 blocks; the active and queued sites' bench follows);</li>
 * <li>"After a building they lay a dirt path from its door to the village bell or hall" (at most 96 blocks long);</li>
 * <li>"A Master builds two and a half times as fast as a Novice" (wiki: XP 1 per 5 blocks placed and 10 for finishing).</li>
 * </ul>
 * Every click goes through the player's own game mode (the main hand, then the off hand, as the client sends it).
 */
public class QaBenchMovePathsPaceGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-000000001026");
	private static final String MOVED = "message.aliveworkplace.builder.moved";

	// --- moving a builder to another table -------------------------------------------------------------------------

	/**
	 * Mid-build, the old table's chest empty and every material by the other table: a sneak-click there, standing by
	 * the builder, says so in chat (his name and the table's place), points the build and the queued build at it, and
	 * the status reads "Works at the table at x y z". After a server restart all of that still holds, and the hut gets
	 * built from the new table's chest; the old table still stands.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaMoveMidBuild"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaMoveMidBuild")
	public void aMoveMidBuildCarriesTheBuildAndQueueToTheNewTablesChests(GameTestHelper helper) {
		ServerLevel level = prepare(helper, 2);
		BlockPos oldTable = new BlockPos(2, 2, 2);
		BlockPos newTable = new BlockPos(26, 2, 2);
		BlockPos oldChest = new BlockPos(2, 2, 3);
		BlockPos newChest = new BlockPos(26, 2, 3);
		table(helper, oldTable);
		table(helper, newTable);
		helper.setBlock(oldChest, Blocks.CHEST);
		helper.setBlock(newChest, Blocks.CHEST);
		fill(helper.getBlockEntity(newChest), hutMaterials());
		Villager[] builder = {helper.spawn(EntityType.VILLAGER, oldTable.east())};
		Builders.employ(level, builder[0], helper.absolutePos(oldTable));
		BuildSite site = Builders.start(level, builder[0], null, TEST_HUT, placement(helper, new BlockPos(12, 2, 12)));
		BuildSite queued = Builders.enqueue(level, builder[0], null, TEST_HUT, placement(helper, new BlockPos(12, 2, 22)));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no test hut blueprint");
		}
		UUID siteId = site.id();
		UUID queuedId = queued.id();
		BlockPos newAbs = helper.absolutePos(newTable);
		String where = newAbs.getX() + " " + newAbs.getY() + " " + newAbs.getZ();
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(((Container) helper.getBlockEntity(newChest)).countItem(Items.OAK_PLANKS) == 55,
				"setup: he took planks from the other table's chest before the move");
			helper.assertTrue(BuildSiteManager.get(level).get(siteId) != null, "setup: built without any materials");
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
			player.moveTo(builder[0].getX(), builder[0].getY(), builder[0].getZ());
			player.setShiftKeyDown(true);
			click(level, player, newAbs);
			Component moved = QaStageFourGameTests.find(seen, MOVED);
			helper.assertTrue(moved != null, "no 'now works at this table' line: " + QaStageFourGameTests.keys(seen));
			QaStageFourGameTests.assertRendered(helper, moved, builder[0].getDisplayName().getString(), "(" + where + ")");
			helper.assertTrue(newAbs.equals(site.bench()), "the build still draws on " + site.bench());
			helper.assertTrue(newAbs.equals(queued.bench()), "the queued build still draws on " + queued.bench());
			String status = Builders.statusText(level, site, builder[0]).getString();
			helper.assertTrue(status.contains("Works at the table at " + where), "the status after the move: " + status);
			restart(helper, level, builder);
			BuildSite loaded = BuildSiteManager.get(level).get(siteId);
			BuildSite loadedQueued = BuildSiteManager.get(level).get(queuedId);
			helper.assertTrue(loaded != null && newAbs.equals(loaded.bench()), "after a restart the build draws on "
				+ (loaded == null ? "nothing" : loaded.bench()));
			helper.assertTrue(loadedQueued != null && newAbs.equals(loadedQueued.bench()), "after a restart the queued build draws on "
				+ (loadedQueued == null ? "nothing" : loadedQueued.bench()));
			helper.assertTrue(Builders.benchPos(builder[0]).filter(newAbs::equals).isPresent(), "after a restart he works at " + Builders.benchPos(builder[0]));
			helper.succeedWhen(() -> {
				helper.assertTrue(BuildSiteManager.get(level).get(siteId) == null, "still building after the move: "
					+ (BuildSiteManager.get(level).get(siteId) == null ? "" : BuildSiteManager.get(level).get(siteId).missing()));
				List<BlockPos> unfinished = plan.unfinished(level);
				helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the hut wrong");
				helper.assertTrue(((Container) helper.getBlockEntity(newChest)).countItem(Items.COBBLESTONE) == 0,
					"the new table's cobblestone wasn't used");
				helper.assertTrue(level.getBlockState(helper.absolutePos(oldTable)).is(ModBlocks.BLUEPRINT_TABLE), "the old table is gone");
			});
		});
	}

	/**
	 * "Within 6 blocks", from both sides of the player: a builder 7 blocks east isn't moved (the table opens instead),
	 * at 6 blocks east he is; then 7 blocks west he isn't moved to a third table, at 6 blocks west he is.
	 */
	//$ gametest_batch HUGE '"qaMoveReach"'
	@GameTest(template = HUGE, batch = "qaMoveReach")
	public void theSixBlockReachHoldsOnBothSides(GameTestHelper helper) {
		ServerLevel level = prepare(helper, 2);
		BlockPos first = new BlockPos(2, 2, 2);
		BlockPos second = new BlockPos(14, 2, 20);
		BlockPos third = new BlockPos(16, 2, 20);
		table(helper, first);
		table(helper, second);
		table(helper, third);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(20, 2, 10));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(first));
		BlockPos stand = new BlockPos(14, 2, 10);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(stand));
		player.moveTo(at.x, at.y, at.z);
		player.setShiftKeyDown(true);
		put(helper, builder, stand.east(7));
		click(level, player, helper.absolutePos(second));
		helper.assertTrue(Builders.benchPos(builder).filter(helper.absolutePos(first)::equals).isPresent(),
			"a builder 7 blocks east was moved to " + Builders.benchPos(builder));
		helper.assertFalse(QaStageFourGameTests.saw(seen, MOVED), "told a builder 7 blocks away moved");
		put(helper, builder, stand.east(6));
		click(level, player, helper.absolutePos(second));
		helper.assertTrue(Builders.benchPos(builder).filter(helper.absolutePos(second)::equals).isPresent(),
			"a builder 6 blocks east wasn't moved: " + Builders.benchPos(builder) + ", chat " + QaStageFourGameTests.keys(seen));
		put(helper, builder, stand.west(7));
		click(level, player, helper.absolutePos(third));
		helper.assertTrue(Builders.benchPos(builder).filter(helper.absolutePos(second)::equals).isPresent(),
			"a builder 7 blocks west was moved to " + Builders.benchPos(builder));
		put(helper, builder, stand.west(6));
		click(level, player, helper.absolutePos(third));
		helper.assertTrue(Builders.benchPos(builder).filter(helper.absolutePos(third)::equals).isPresent(),
			"a builder 6 blocks west wasn't moved: " + Builders.benchPos(builder));
		helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(first)) == 1
			&& level.getPoiManager().getFreeTickets(helper.absolutePos(second)) == 1, "a table he left isn't free again");
		helper.succeed();
	}

	/** Two builders by the player, the nearer spawned last: only the nearer one moves, the other keeps his table. */
	//$ gametest_batch HUGE '"qaMoveNearest"'
	@GameTest(template = HUGE, batch = "qaMoveNearest")
	public void onlyTheNearestOfTwoBuildersMoves(GameTestHelper helper) {
		ServerLevel level = prepare(helper, 2);
		BlockPos tableA = new BlockPos(2, 2, 2);
		BlockPos tableB = new BlockPos(6, 2, 2);
		BlockPos target = new BlockPos(14, 2, 24);
		table(helper, tableA);
		table(helper, tableB);
		table(helper, target);
		BlockPos stand = new BlockPos(14, 2, 12);
		Villager far = helper.spawn(EntityType.VILLAGER, stand.west(4));
		far.setNoAi(true);
		Builders.employ(level, far, helper.absolutePos(tableB));
		Villager near = helper.spawn(EntityType.VILLAGER, stand.east(2));
		near.setNoAi(true);
		Builders.employ(level, near, helper.absolutePos(tableA));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(stand));
		player.moveTo(at.x, at.y, at.z);
		player.setShiftKeyDown(true);
		click(level, player, helper.absolutePos(target));
		helper.assertTrue(Builders.benchPos(near).filter(helper.absolutePos(target)::equals).isPresent(),
			"the nearer builder works at " + Builders.benchPos(near));
		helper.assertTrue(Builders.benchPos(far).filter(helper.absolutePos(tableB)::equals).isPresent(),
			"the farther builder works at " + Builders.benchPos(far));
		Component moved = QaStageFourGameTests.find(seen, MOVED);
		helper.assertTrue(moved != null, "no moved line: " + QaStageFourGameTests.keys(seen));
		QaStageFourGameTests.assertRendered(helper, moved, near.getDisplayName().getString());
		helper.succeed();
	}

	/**
	 * A table another builder works at is refused: the player is told it's taken, nobody moves, the other builder keeps
	 * it and the first keeps his own.
	 */
	//$ gametest_batch HUGE '"qaMoveTaken"'
	@GameTest(template = HUGE, batch = "qaMoveTaken")
	public void aTakenTableIsRefused(GameTestHelper helper) {
		ServerLevel level = prepare(helper, 2);
		BlockPos mine = new BlockPos(2, 2, 2);
		BlockPos theirs = new BlockPos(26, 2, 26);
		table(helper, mine);
		table(helper, theirs);
		BlockPos stand = new BlockPos(6, 2, 6);
		Villager builder = helper.spawn(EntityType.VILLAGER, stand.east(2));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(mine));
		Villager other = helper.spawn(EntityType.VILLAGER, new BlockPos(25, 2, 25));
		other.setNoAi(true);
		Builders.employ(level, other, helper.absolutePos(theirs));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(stand));
		player.moveTo(at.x, at.y, at.z);
		player.setShiftKeyDown(true);
		click(level, player, helper.absolutePos(theirs));
		Component taken = QaStageFourGameTests.find(seen, "message.aliveworkplace.builder.table_taken");
		helper.assertTrue(taken != null, "not told the table is taken: " + QaStageFourGameTests.keys(seen));
		QaStageFourGameTests.assertRendered(helper, taken, "Another builder works at this table");
		helper.assertFalse(QaStageFourGameTests.saw(seen, MOVED), "told he moved to a taken table");
		helper.assertTrue(Builders.benchPos(builder).filter(helper.absolutePos(mine)::equals).isPresent(), "he works at " + Builders.benchPos(builder));
		helper.assertTrue(Builders.benchPos(other).filter(helper.absolutePos(theirs)::equals).isPresent(), "the other works at " + Builders.benchPos(other));
		helper.succeed();
	}

	/**
	 * Only a sneak-click with an empty hand moves him: holding a stick while sneaking, holding it in the off hand, or an
	 * empty hand without sneaking leaves him at his table.
	 */
	//$ gametest_batch HUGE '"qaMoveHands"'
	@GameTest(template = HUGE, batch = "qaMoveHands")
	public void onlyAnEmptyHandedSneakClickMoves(GameTestHelper helper) {
		ServerLevel level = prepare(helper, 2);
		BlockPos mine = new BlockPos(2, 2, 2);
		BlockPos other = new BlockPos(20, 2, 20);
		table(helper, mine);
		table(helper, other);
		BlockPos stand = new BlockPos(14, 2, 14);
		Villager builder = helper.spawn(EntityType.VILLAGER, stand.east(2));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(mine));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(stand));
		player.moveTo(at.x, at.y, at.z);
		BlockPos otherAbs = helper.absolutePos(other);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		click(level, player, otherAbs);
		helper.assertTrue(Builders.benchPos(builder).filter(helper.absolutePos(mine)::equals).isPresent(), "a stick in hand moved him");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
		click(level, player, otherAbs);
		helper.assertTrue(Builders.benchPos(builder).filter(helper.absolutePos(mine)::equals).isPresent(), "a stick in the off hand moved him");
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		player.setShiftKeyDown(false);
		click(level, player, otherAbs);
		helper.assertTrue(Builders.benchPos(builder).filter(helper.absolutePos(mine)::equals).isPresent(), "a click without sneaking moved him");
		helper.assertFalse(QaStageFourGameTests.saw(seen, MOVED), "told he moved: " + QaStageFourGameTests.keys(seen));
		// and the same player, empty-handed and sneaking, does move him (the setup is right)
		player.setShiftKeyDown(true);
		click(level, player, otherAbs);
		helper.assertTrue(Builders.benchPos(builder).filter(otherAbs::equals).isPresent(), "an empty-handed sneak-click didn't move him");
		helper.succeed();
	}

	/**
	 * With builder ownership on, a stranger's sneak-click is refused with the not_your_builder line (the builder's name,
	 * his employer's and the stranger's own), and the builder stays; with ownership off the same click moves him.
	 */
	//$ gametest_batch HUGE '"qaMoveStranger"'
	@GameTest(template = HUGE, batch = "qaMoveStranger")
	public void aStrangerCantMoveAHiredBuilderWhileOwnershipIsOn(GameTestHelper helper) {
		ServerLevel level = prepare(helper, 2);
		boolean was = level.getGameRules().getBoolean(ModGameRules.BUILDER_OWNERSHIP);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(was, level.getServer()));
		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(true, level.getServer());
		BlockPos mine = new BlockPos(2, 2, 2);
		BlockPos other = new BlockPos(20, 2, 20);
		table(helper, mine);
		table(helper, other);
		BlockPos stand = new BlockPos(14, 2, 14);
		Villager builder = helper.spawn(EntityType.VILLAGER, stand.east(2));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(mine));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(JESSE, "Jesse"));
		List<Component> seen = new ArrayList<>();
		ServerPlayer stranger = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(stand));
		stranger.moveTo(at.x, at.y, at.z);
		stranger.setShiftKeyDown(true);
		click(level, stranger, helper.absolutePos(other));
		Component refused = QaStageFourGameTests.find(seen, "message.aliveworkplace.not_your_builder");
		helper.assertTrue(refused != null, "the stranger wasn't refused: " + QaStageFourGameTests.keys(seen));
		QaStageFourGameTests.assertRendered(helper, refused, builder.getDisplayName().getString(), "Jesse", stranger.getGameProfile().getName());
		helper.assertTrue(Builders.benchPos(builder).filter(helper.absolutePos(mine)::equals).isPresent(), "a stranger moved him");
		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(false, level.getServer());
		click(level, stranger, helper.absolutePos(other));
		helper.assertTrue(Builders.benchPos(builder).filter(helper.absolutePos(other)::equals).isPresent(),
			"with ownership off the click didn't move him: " + QaStageFourGameTests.keys(seen));
		helper.succeed();
	}

	// --- paths ---------------------------------------------------------------------------------------------------------

	/**
	 * "To the village bell or hall": with no bell, only a Village Hall 14 blocks off, the finished hut gets a dirt path
	 * that ends by the hall.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaPathToHall"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaPathToHall")
	public void thePathLeadsToTheHallWhenThereIsNoBell(GameTestHelper helper) {
		ServerLevel level = prepare(helper, 2);
		pathsOn(helper);
		Leftovers.halls(helper);
		clearBells(helper);
		grass(helper);
		BlockPos hall = new BlockPos(22, 2, 22);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		ensurePoi(level, helper.absolutePos(hall));
		((VillageHallBlockEntity) helper.getBlockEntity(hall)).setOwner(JESSE, "Jesse");
		VillageHalls.forgetNearest();
		BlockPos hallAbs = helper.absolutePos(hall);
		Leftovers.after(helper, () -> {
			level.removeBlock(hallAbs, false);
			VillageHalls.forgetNearest();
		});
		Villager builder = bench(helper, new BlockPos(2, 2, 2), hutMaterials());
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement(helper, new BlockPos(8, 2, 8)));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building");
			helper.assertTrue(!ModAttachments.PATH.has(builder), "still laying the path: " + ModAttachments.PATH.getOrElse(builder, List.of()).size() + " to go");
			int path = 0;
			boolean byHall = false;
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.DIRT_PATH)) {
						path++;
						byHall |= Math.abs(x - hall.getX()) <= 4 && Math.abs(z - hall.getZ()) <= 4;
					}
				}
			}
			helper.assertTrue(path >= 6, "only " + path + " blocks of path");
			helper.assertTrue(byHall, "the " + path + " blocks of path don't reach the hall");
		});
	}

	/**
	 * "At most 96 blocks long", from the long side: the only way from the hut to the bell winds through a walled maze
	 * (well over 96 steps), so the path stops at 96 blocks of dirt path, never more, the plan included.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaPathCap"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaPathCap")
	public void aLongWayGetsAtMost96BlocksOfPath(GameTestHelper helper) {
		ServerLevel level = prepare(helper, 2);
		pathsOn(helper);
		Leftovers.halls(helper);
		clearBells(helper);
		grass(helper);
		// a 2-high wall all round, then six walls across with a 2-wide gap at alternate ends
		for (int i = 0; i < 30; i++) {
			wall(helper, i, 0);
			wall(helper, i, 29);
			wall(helper, 0, i);
			wall(helper, 29, i);
		}
		int[] rows = {9, 12, 15, 18, 21, 24};
		for (int r = 0; r < rows.length; r++) {
			boolean gapEast = r % 2 == 0;
			for (int x = 1; x <= 28; x++) {
				if (gapEast ? x >= 27 : x <= 2) {
					continue;
				}
				wall(helper, x, rows[r]);
			}
		}
		helper.setBlock(new BlockPos(27, 2, 27), Blocks.BELL);
		Villager builder = bench(helper, new BlockPos(3, 2, 3), hutMaterials());
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement(helper, new BlockPos(12, 2, 2)));
		int[] planned = {0};
		helper.succeedWhen(() -> {
			planned[0] = Math.max(planned[0], ModAttachments.PATH.getOrElse(builder, List.of()).size());
			helper.assertTrue(planned[0] <= Paths.MAX_LENGTH, planned[0] + " blocks of path planned");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building");
			helper.assertTrue(planned[0] > 0, "no path planned");
			helper.assertTrue(!ModAttachments.PATH.has(builder), "still laying the path: " + ModAttachments.PATH.getOrElse(builder, List.of()).size() + " to go");
			int path = 0;
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					path += helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.DIRT_PATH) ? 1 : 0;
				}
			}
			helper.assertTrue(path == 96, path + " blocks of dirt path for a way much longer than 96 (planned " + planned[0] + ")");
		});
	}

	// --- level speed and XP --------------------------------------------------------------------------------------------

	/**
	 * "A Master builds two and a half times as fast as a Novice", measured: two builders side by side on the same hut,
	 * one a Novice, one a Master, a 40-tick block delay (the Novice's 100%, the Master's 40%), well villages' pace. The
	 * typical (median) wait between two blocks placed is 40 ticks for the Novice and 16 for the Master.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaLevelPace"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaLevelPace")
	public void aMasterPlacesBlocksTwoAndAHalfTimesAsFastAsANovice(GameTestHelper helper) {
		ServerLevel level = prepare(helper, 40);
		traitsOff(helper);
		Villager novice = bench(helper, new BlockPos(2, 2, 2), hutMaterials());
		Villager master = bench(helper, new BlockPos(26, 2, 2), hutMaterials());
		master.setVillagerData(master.getVillagerData().setLevel(5));
		helper.assertTrue(novice.getVillagerData().getLevel() == 1 && master.getVillagerData().getLevel() == 5, "setup: levels");
		helper.assertTrue(Pace.factor(novice) == 1f && Pace.factor(master) == 1f, "setup: pace " + Pace.of(novice) + " / " + Pace.of(master));
		// the level's share alone: 40 ticks for a Novice, 16 for a Master (each pace 1, checked above)
		int noviceDelay = io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(level, novice);
		int masterDelay = io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(level, master);
		helper.assertTrue(noviceDelay == 40 && masterDelay == 16, "setup: delays " + noviceDelay + " / " + masterDelay);
		BuildSite noviceSite = Builders.start(level, novice, null, TEST_HUT, placement(helper, new BlockPos(4, 2, 12)));
		BuildSite masterSite = Builders.start(level, master, null, TEST_HUT, placement(helper, new BlockPos(20, 2, 12)));
		BuildPlan novicePlan = noviceSite.plan(level);
		BuildPlan masterPlan = masterSite.plan(level);
		if (novicePlan == null || masterPlan == null) {
			throw new GameTestAssertException("no test hut blueprint");
		}
		List<Long> noviceTimes = new ArrayList<>();
		List<Long> masterTimes = new ArrayList<>();
		int[] left = {novicePlan.unfinished(level).size(), masterPlan.unfinished(level).size()};
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			int n = novicePlan.unfinished(level).size();
			int m = masterPlan.unfinished(level).size();
			for (int i = n; i < left[0]; i++) {
				noviceTimes.add(now);
			}
			for (int i = m; i < left[1]; i++) {
				masterTimes.add(now);
			}
			left[0] = n;
			left[1] = m;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(noviceTimes.size() >= 25, "the novice has placed " + noviceTimes.size());
			int noviceGap = median(noviceTimes);
			int masterGap = median(masterTimes);
			helper.assertTrue(noviceGap >= 39 && noviceGap <= 42, "the Novice's typical wait between blocks is " + noviceGap + " ticks, not 40");
			helper.assertTrue(masterGap >= 15 && masterGap <= 18, "the Master's typical wait between blocks is " + masterGap + " ticks, not 16");
			float ratio = noviceGap / (float) masterGap;
			helper.assertTrue(ratio >= 2.3f && ratio <= 2.7f, "a Master is " + ratio + " times as fast as a Novice (novice " + noviceGap + ", master " + masterGap + ")");
			level.getServer().sendSystemMessage(Component.literal("[qa-level-pace] novice " + noviceGap + " ticks/block, master " + masterGap
				+ " ticks/block, " + ratio + "x"));
		});
	}

	/**
	 * XP "1 per 5 blocks placed and 10 for finishing a build": a Novice (no traits) finishing the hut has gained exactly
	 * a fifth of its blocks, rounded down, plus 10, and is an Apprentice.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaBuildXp"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaBuildXp")
	public void aFinishedBuildGivesAFifthOfItsBlocksPlusTenXp(GameTestHelper helper) {
		ServerLevel level = prepare(helper, 2);
		traitsOff(helper);
		Villager builder = bench(helper, new BlockPos(2, 2, 2), hutMaterials());
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement(helper, new BlockPos(6, 2, 6)));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no test hut blueprint");
		}
		int blocks = plan.unfinished(level).size();
		int xp0 = builder.getVillagerXp();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building");
			helper.assertTrue(plan.unfinished(level).isEmpty(), "the hut isn't whole");
			int gained = builder.getVillagerXp() - xp0;
			int placed = site.placedBy(builder.getUUID(), false);
			int expected = blocks / 5 + 10;
			helper.assertTrue(gained == expected, "gained " + gained + " XP for a " + blocks + "-block hut (he counts " + placed
				+ " placed), expected " + expected);
			helper.assertTrue(builder.getVillagerData().getLevel() == 2, "level " + builder.getVillagerData().getLevel() + " after " + gained + " XP");
		});
	}

	// --- helpers -------------------------------------------------------------------------------------------------------

	static ServerLevel prepare(GameTestHelper helper, int delay) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		// a Village Hall another batch left nearby gives these villagers a village (its mood, needs, edicts, research):
		// their pace would change with it (0.86 in a full run)
		Leftovers.halls(helper);
		VillageHalls.forgetNearest();
		io.github.jcondedata.aliveworkplace.people.Moods.forget();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int wasDelay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(wasDelay, level.getServer()));
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		return level;
	}

	private static void pathsOn(GameTestHelper helper) {
		boolean was = Paths.ENABLED;
		Paths.ENABLED = true;
		Leftovers.after(helper, () -> Paths.ENABLED = was);
	}

	static void traitsOff(GameTestHelper helper) {
		boolean was = Traits.ENABLED;
		Traits.ENABLED = false;
		Leftovers.after(helper, () -> Traits.ENABLED = was);
	}

	/** Bells another batch left beside this area (never cleared) would be nearer than this test's own. */
	private static void clearBells(GameTestHelper helper) {
		AABB box = helper.getBounds();
		for (BlockPos bell : helper.getLevel().getPoiManager().findAll(h -> h.is(PoiTypes.MEETING), p -> !box.contains(Vec3.atCenterOf(p)),
			BlockPos.containing(box.getCenter()), 96, PoiManager.Occupancy.ANY).toList()) {
			helper.getLevel().removeBlock(bell, false);
		}
	}

	private static void grass(GameTestHelper helper) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	private static void wall(GameTestHelper helper, int x, int z) {
		helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE_BRICKS);
		helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE_BRICKS);
	}

	/** A Blueprint Table at {@code rel}, with a chest south of it holding {@code items}, and a builder east of it. */
	static Villager bench(GameTestHelper helper, BlockPos rel, ItemStack... items) {
		table(helper, rel);
		helper.setBlock(rel.south(), Blocks.CHEST);
		fill(helper.getBlockEntity(rel.south()), items);
		Villager builder = helper.spawn(EntityType.VILLAGER, rel.east());
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(rel));
		return builder;
	}

	private static void table(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(helper.getLevel(), helper.absolutePos(rel));
	}

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	private static void put(GameTestHelper helper, Villager villager, BlockPos rel) {
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(rel));
		villager.moveTo(at.x, at.y, at.z);
	}

	/** A right-click on the top of {@code table} as the client sends it: the main hand, then the off hand. */
	private static void click(ServerLevel level, ServerPlayer player, BlockPos table) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(table).relative(Direction.UP, 0.5), Direction.UP, table, false);
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			InteractionResult result = player.gameMode.useItemOn(player, level, stack, hand, hit);
			if (result.consumesAction() || result == InteractionResult.FAIL) {
				return;
			}
			if (!stack.isEmpty() && player.gameMode.useItem(player, level, stack, hand).consumesAction()) {
				return;
			}
		}
	}

	static BlueprintData.Placement placement(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	/** A server restart: every build site and the builder saved and loaded again (the builder as a fresh entity). */
	private static void restart(GameTestHelper helper, ServerLevel level, Villager[] builder) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(builder[0].save(tag), "the builder wasn't saved");
		builder[0].discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		builder[0] = (Villager) loaded;
	}

	/** The median wait between consecutive placements, in ticks (blocks placed in the same tick count as a wait of 0). */
	static int median(List<Long> times) {
		List<Long> gaps = new ArrayList<>();
		for (int i = 1; i < times.size(); i++) {
			gaps.add(times.get(i) - times.get(i - 1));
		}
		if (gaps.isEmpty()) {
			return -1;
		}
		gaps.sort(Long::compare);
		return (int) (long) gaps.get(gaps.size() / 2);
	}

	static ItemStack[] hutMaterials() {
		return new ItemStack[] {
			new ItemStack(Items.COBBLESTONE, 25),
			new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)
		};
	}

	private static void fill(Object container, ItemStack... items) {
		for (int i = 0; i < items.length; i++) {
			((Container) container).setItem(i, items[i].copy());
		}
	}
}
