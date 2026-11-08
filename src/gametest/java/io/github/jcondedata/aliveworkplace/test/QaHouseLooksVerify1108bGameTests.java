package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.PieceLooks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.world.VillagePieces;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-lane-b-1108-1837), verifying B1050 and B1051 from their specs (ROADMAP; docs/wiki/house-looks.md "Changing
 * your mind", "Remembered", the player's lines). Every pick goes through the hall's House looks page as the leader
 * clicks it, and the line he reads above the hotbar is captured and compared word for word.
 * <ul>
 * <li>B1050: a torn-down house whose look was remembered (and the hall saved and loaded since) is gone, nothing starts
 * and no material is taken; a house whose outside is stripped but whose room stands is still there; a torn-down house
 * with an open rebuild can still be changed.</li>
 * <li>B1051: the 5th waiting build's look is changed in place (the queue stays at 5, the old rebuild is gone); the same
 * look again is "already being rebuilt"; a full queue is told as full, in the builder's name; an idle builder out of reach
 * doesn't change that; a second builder with room in his queue takes the pick.</li>
 * </ul>
 * Setup: {@link QaHouseLooks1108cGameTests#village} (a plains Guard House, a hall, a builder at his bench, three chests).
 */
public class QaHouseLooksVerify1108bGameTests implements FabricGameTest {
	private static final String AREA = QaHouseLooks1108cGameTests.AREA;
	private static final String GONE = "That house isn't there any more.";

	/** The village, led by a player whose action-bar lines land in {@code seen}. */
	private static QaHouseLooks1108cGameTests.Village village(GameTestHelper helper, List<Component> seen) {
		QaHouseLooks1108cGameTests.Village v = QaHouseLooks1108cGameTests.village(helper, false);
		ServerPlayer leader = WonderFinishedGameTests.listener(helper, seen, "VerifyLeader");
		v.entity().setOwner(leader.getUUID(), leader.getGameProfile().getName());
		return new QaHouseLooks1108cGameTests.Village(v.level(), v.hall(), v.entity(), leader, v.placement(), v.builder(), v.template());
	}

	/** Opens the Guard House's view and clicks {@code look}; returns the line the leader read. */
	private static String pick(GameTestHelper helper, QaHouseLooks1108cGameTests.Village v, List<Component> seen, String look) {
		ChoiceMenu view = QaHouseLooks1108cGameTests.houseView(helper, v, v.owner());
		return click(helper, v, view, seen, look);
	}

	private static String click(GameTestHelper helper, QaHouseLooks1108cGameTests.Village v, ChoiceMenu view, List<Component> seen, String look) {
		seen.clear();
		QaHouseLooks1108cGameTests.click(helper, view, v.owner(), look);
		helper.assertTrue(!seen.isEmpty(), "the click on " + look + " said nothing");
		return seen.get(seen.size() - 1).getString();
	}

	private static String builderName(QaHouseLooks1108cGameTests.Village v) {
		return v.builder().getDisplayName().getString();
	}

	/** Every open build site whose structure is {@code look}'s Guard House outside at the house's spot. */
	private static List<BuildSite> rebuilds(QaHouseLooks1108cGameTests.Village v, String look) {
		ResourceLocation id = VillagePieces.outsideId(look, "guard_house");
		return BuildSiteManager.get(v.level()).all().stream()
			.filter(s -> s.structure().equals(id) && s.placement().origin().equals(v.placement().origin())).toList();
	}

	/** {@code waiting + 1} builds handed to {@code builder} (the first he is on, the rest waiting), at spots above row {@code z}. */
	private static void busy(GameTestHelper helper, QaHouseLooks1108cGameTests.Village v, Villager builder, int waiting, int z) {
		for (int i = 0; i <= waiting; i++) {
			BlueprintData.Placement elsewhere = new BlueprintData.Placement(v.level().dimension().location(),
				helper.absolutePos(new BlockPos(2 + i * 3, 2, z)).above(40), Rotation.NONE, Mirror.NONE);
			BuildSite s = i == 0
				? Builders.start(v.level(), builder, v.owner(), VillagePieces.outsideId("snowy", "clinic"), elsewhere)
				: Builders.enqueue(v.level(), builder, v.owner(), VillagePieces.outsideId("snowy", "clinic"), elsewhere);
			helper.assertTrue(s != null, "setup: build " + i + " not handed to the builder");
		}
		helper.assertTrue(Builders.queue(v.level(), builder).size() == waiting, "setup: waiting " + Builders.queue(v.level(), builder).size());
	}

	/** A second builder of the village, at his own bench a few blocks from the first, removed with his sites after the test. */
	private static Villager secondBuilder(GameTestHelper helper, QaHouseLooks1108cGameTests.Village v) {
		BlockPos bench = new BlockPos(26, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(26, 2, 6));
		Builders.employ(v.level(), second, helper.absolutePos(bench));
		Leftovers.after(helper, () -> {
			for (BuildSite site : List.copyOf(BuildSiteManager.get(v.level()).all())) {
				if (second.getUUID().equals(site.builder())) {
					BuildSiteManager.get(v.level()).remove(site.id());
				}
			}
		});
		return second;
	}

	private static int items(GameTestHelper helper) {
		int n = 0;
		for (BlockPos c : QaHouseLooks1108cGameTests.CHESTS) {
			Container chest = (Container) helper.getBlockEntity(c);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	// ---- B1050 ------------------------------------------------------------------------------------------------------

	/**
	 * The leader picks the outside the house has (refused as "already has", remembered); the hall is saved and loaded; the
	 * house is torn down. The desert pick says "That house isn't there any more.", starts nothing, and over the next 200
	 * ticks the builder takes nothing from the stocked chests and puts nothing on the empty lot.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaLooksVerifyTornReload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLooksVerifyTornReload")
	public void aTornDownHouseWhoseRememberedLookWasSavedIsGone(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		QaHouseLooks1108cGameTests.Village v = village(helper, seen);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		String said = pick(helper, v, seen, "plains");
		helper.assertTrue(said.equals("The Guard House already has the Plains outside."), "picking its own outside said: " + said);
		helper.assertTrue(v.entity().pieceLooks().size() == 1, "setup: the choice isn't remembered: " + v.entity().pieceLooks());
		// the hall saved and loaded, as on a server restart
		CompoundTag tag = v.entity().saveWithFullMetadata(level.registryAccess());
		v.entity().loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(v.entity().pieceLooks().size() == 1, "the remembered choice didn't survive the reload: " + v.entity().pieceLooks());
		QaHouseLooks1108cGameTests.stock(helper, v, "desert");
		int stocked = items(helper);
		ChoiceMenu view = QaHouseLooks1108cGameTests.houseView(helper, v, v.owner());
		QaHouseLooks1108cGameTests.tearDown(v);
		said = click(helper, v, view, seen, "desert");
		helper.assertTrue(said.equals(GONE), "the torn-down house's pick said: " + said);
		helper.assertTrue(PieceLooks.rebuild(level, found) == null && rebuilds(v, "desert").isEmpty(), "a rebuild was opened on the empty lot");
		helper.assertTrue(Builders.activeSite(level, v.builder()) == null && Builders.queue(level, v.builder()).isEmpty(), "the builder took a job");
		var size = v.template().getSize();
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(items(helper) == stocked, "materials were taken: " + stocked + " stocked, " + items(helper) + " left");
			for (BlockPos local : BlockPos.betweenClosed(0, 0, 0, size.getX() - 1, size.getY() - 1, size.getZ() - 1)) {
				BlockPos at = QaHouseLooks1108cGameTests.world(v.placement(), local);
				helper.assertTrue(level.getBlockState(at).isAir(), "a block went up on the empty lot at " + at + ": " + level.getBlockState(at));
			}
			helper.succeed();
		});
	}

	/**
	 * Half rebuilt is still there: the house's whole plains outside is taken off (as when a rebuild has taken the old pieces
	 * out) but its room, job block and chest stand. With its look remembered, a desert pick starts the rebuild.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLooksVerifyRoomStands"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksVerifyRoomStands")
	public void aHouseStrippedOfItsOutsideWithItsRoomStandingIsStillThere(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		QaHouseLooks1108cGameTests.Village v = village(helper, seen);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		ChoiceMenu view = QaHouseLooks1108cGameTests.houseView(helper, v, v.owner());
		click(helper, v, view, seen, "plains");
		helper.assertTrue(v.entity().pieceLooks().size() == 1, "setup: the choice isn't remembered");
		Blueprint outside = BlueprintLibrary.get(level, VillagePieces.outsideId("plains", "guard_house"))
			.orElseThrow(() -> new GameTestAssertException("no plains outside"));
		int stripped = 0;
		for (Blueprint.Entry e : outside.blocks()) {
			if (!e.state().isAir()) {
				BlockPos at = QaHouseLooks1108cGameTests.world(v.placement(), e.pos());
				if (!level.getBlockState(at).isAir()) {
					level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
					stripped++;
				}
			}
		}
		helper.assertTrue(stripped > 0, "setup: nothing of the outside was taken off");
		helper.assertTrue(VillagePieces.at(level, v.placement()).isEmpty(), "setup: the house is still recognized by an outside");
		helper.assertTrue(level.getBlockState(QaHouseLooks1108cGameTests.world(v.placement(), QaHouseLooks1108cGameTests.GRINDSTONE)).is(Blocks.GRINDSTONE),
			"setup: the job block went with the outside");
		String said = click(helper, v, view, seen, "desert");
		helper.assertTrue(said.equals(builderName(v) + " is rebuilding the Guard House with the Desert outside."),
			"a house with its room standing (" + stripped + " outside blocks off) said: " + said);
		BuildSite site = PieceLooks.rebuild(level, found);
		helper.assertTrue(site != null && site.structure().equals(VillagePieces.outsideId("desert", "guard_house")), "no desert rebuild: " + site);
		helper.succeed();
	}

	/**
	 * A desert rebuild is open when the house is torn down: part of the house (its rebuild) still stands, so changing the
	 * pick to taiga calls the desert one off and opens the taiga one.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLooksVerifyTornOpen"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksVerifyTornOpen")
	public void aTornDownHouseWithAnOpenRebuildCanStillBeChanged(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		QaHouseLooks1108cGameTests.Village v = village(helper, seen);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		ChoiceMenu view = QaHouseLooks1108cGameTests.houseView(helper, v, v.owner());
		String said = click(helper, v, view, seen, "desert");
		helper.assertTrue(said.equals(builderName(v) + " is rebuilding the Guard House with the Desert outside."), "setup: desert said: " + said);
		QaHouseLooks1108cGameTests.tearDown(v);
		said = click(helper, v, view, seen, "taiga");
		helper.assertTrue(said.equals(builderName(v) + " is rebuilding the Guard House with the Taiga outside."),
			"changing an open rebuild of a torn-down house said: " + said);
		helper.assertTrue(rebuilds(v, "desert").isEmpty(), "the desert rebuild wasn't called off");
		BuildSite site = PieceLooks.rebuild(level, found);
		helper.assertTrue(site != null && site.structure().equals(VillagePieces.outsideId("taiga", "guard_house")), "the rebuild is " + site);
		helper.succeed();
	}

	// ---- B1051 ------------------------------------------------------------------------------------------------------

	/**
	 * Four builds waiting, the desert pick waits as the 5th; through the page the leader changes it to taiga. He reads that
	 * the builder will rebuild it with the taiga outside, the queue stays at 5, the desert rebuild is gone, and the builder's
	 * current build is untouched.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLooksVerifyFifthChange"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksVerifyFifthChange")
	public void changingTheFifthWaitingBuildThroughThePageReplacesIt(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		QaHouseLooks1108cGameTests.Village v = village(helper, seen);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		busy(helper, v, v.builder(), Builders.MAX_QUEUE - 1, 2);
		BuildSite current = Builders.activeSite(level, v.builder());
		ChoiceMenu view = QaHouseLooks1108cGameTests.houseView(helper, v, v.owner());
		String said = click(helper, v, view, seen, "desert");
		helper.assertTrue(said.equals(builderName(v) + " will rebuild the Guard House with the Desert outside after their current builds."), "desert said: " + said);
		helper.assertTrue(Builders.queue(level, v.builder()).size() == Builders.MAX_QUEUE, "setup: waiting " + Builders.queue(level, v.builder()).size());
		said = click(helper, v, view, seen, "taiga");
		helper.assertTrue(said.equals(builderName(v) + " will rebuild the Guard House with the Taiga outside after their current builds."),
			"changing the 5th waiting build said: " + said);
		helper.assertTrue(Builders.queue(level, v.builder()).size() == Builders.MAX_QUEUE, "the queue is now " + Builders.queue(level, v.builder()).size());
		helper.assertTrue(rebuilds(v, "desert").isEmpty(), "the desert rebuild is still open: " + rebuilds(v, "desert").size());
		BuildSite site = PieceLooks.rebuild(level, found);
		helper.assertTrue(site != null && site.isQueued() && site.structure().equals(VillagePieces.outsideId("taiga", "guard_house")), "the house's rebuild: " + site);
		helper.assertTrue(Builders.activeSite(level, v.builder()) == current, "the builder's current build changed");
		helper.assertTrue(v.entity().pieceLooks().size() == 1 && v.entity().pieceLooks().get(0).look().equals("taiga"), "remembered: " + v.entity().pieceLooks());
		helper.succeed();
	}

	/** The 5th waiting build is the desert one; picking desert again is "already being rebuilt" and changes nothing. */
	//$ gametest_ticks_batch AREA '200' '"qaLooksVerifyFifthSame"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksVerifyFifthSame")
	public void theSameLookForTheFifthWaitingBuildIsAlreadyBeingRebuilt(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		QaHouseLooks1108cGameTests.Village v = village(helper, seen);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		busy(helper, v, v.builder(), Builders.MAX_QUEUE - 1, 2);
		ChoiceMenu view = QaHouseLooks1108cGameTests.houseView(helper, v, v.owner());
		click(helper, v, view, seen, "desert");
		BuildSite first = PieceLooks.rebuild(level, found);
		helper.assertTrue(first != null, "setup: desert not queued");
		String said = click(helper, v, view, seen, "desert");
		helper.assertTrue(said.equals("The Guard House is already being rebuilt with the Desert outside."), "desert again said: " + said);
		BuildSite after = PieceLooks.rebuild(level, found);
		helper.assertTrue(after != null && after.id().equals(first.id()), "the waiting rebuild was replaced");
		helper.assertTrue(Builders.queue(level, v.builder()).size() == Builders.MAX_QUEUE, "waiting " + Builders.queue(level, v.builder()).size());
		helper.succeed();
	}

	/**
	 * The only builder has five builds waiting. The pick reads, word for word, the full-queue line in the builder's name
	 * ("<Builder> already has 5 blueprints lined up. Hire another builder, or wait."), and nothing is queued.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLooksVerifyFull"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksVerifyFull")
	public void aFullQueueReadsTheFullQueueLine(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		QaHouseLooks1108cGameTests.Village v = village(helper, seen);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		busy(helper, v, v.builder(), Builders.MAX_QUEUE, 2);
		String said = pick(helper, v, seen, "desert");
		String expected = Component.translatable("message.aliveworkplace.queue.full", builderName(v), Builders.MAX_QUEUE).getString();
		helper.assertTrue(expected.equals(builderName(v) + " already has 5 blueprints lined up. Hire another builder, or wait."), "the lang line changed: " + expected);
		helper.assertTrue(said.equals(expected), "a full queue said: " + said);
		helper.assertTrue(PieceLooks.rebuild(level, found) == null, "a sixth build was queued");
		helper.assertTrue(Builders.queue(level, v.builder()).size() == Builders.MAX_QUEUE, "waiting " + Builders.queue(level, v.builder()).size());
		helper.assertTrue(v.entity().pieceLooks().isEmpty(), "a refused pick was remembered: " + v.entity().pieceLooks());
		helper.succeed();
	}

	/**
	 * The near builder's queue is full, and the village's only other builder is idle but his bench is 200 blocks away. The
	 * builders of the player's that reach the house are all full, so the pick reads the full-queue line, not the reach line.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLooksVerifyFullFar"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksVerifyFullFar")
	public void anIdleBuilderOutOfReachDoesNotHideAFullQueue(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		QaHouseLooks1108cGameTests.Village v = village(helper, seen);
		ServerLevel level = v.level();
		busy(helper, v, v.builder(), Builders.MAX_QUEUE, 2);
		BlockPos farBench = helper.absolutePos(new BlockPos(13, 2, 10)).east(200);
		Villager far = helper.spawn(EntityType.VILLAGER, new BlockPos(26, 2, 6));
		level.setBlock(farBench, ModBlocks.BUILDERS_BENCH.defaultBlockState(), 3);
		Leftovers.after(helper, () -> level.setBlock(farBench, Blocks.AIR.defaultBlockState(), 3));
		Builders.employ(level, far, farBench);
		String said = pick(helper, v, seen, "desert");
		helper.assertTrue(said.equals(builderName(v) + " already has 5 blueprints lined up. Hire another builder, or wait."),
			"a full near queue with an idle far builder said: " + said);
		helper.assertTrue(Builders.activeSite(level, far) == null, "the far builder took the house");
		helper.succeed();
	}

	/** One builder's queue is full, another's has four waiting: the pick waits as the second builder's 5th build. */
	//$ gametest_ticks_batch AREA '200' '"qaLooksVerifySecond"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksVerifySecond")
	public void aSecondBuilderWithRoomTakesThePickWhenTheFirstIsFull(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		QaHouseLooks1108cGameTests.Village v = village(helper, seen);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		Villager second = secondBuilder(helper, v);
		busy(helper, v, v.builder(), Builders.MAX_QUEUE, 2);
		busy(helper, v, second, Builders.MAX_QUEUE - 1, 24);
		String said = pick(helper, v, seen, "desert");
		helper.assertTrue(said.equals(second.getDisplayName().getString() + " will rebuild the Guard House with the Desert outside after their current builds."),
			"with one builder full and one with room, the pick said: " + said);
		BuildSite site = PieceLooks.rebuild(level, found);
		helper.assertTrue(site != null && second.getUUID().equals(site.builder()), "the rebuild isn't the second builder's: " + site);
		helper.assertTrue(Builders.queue(level, second).size() == Builders.MAX_QUEUE && Builders.queue(level, v.builder()).size() == Builders.MAX_QUEUE,
			"queues " + Builders.queue(level, v.builder()).size() + " and " + Builders.queue(level, second).size());
		helper.succeed();
	}
}
