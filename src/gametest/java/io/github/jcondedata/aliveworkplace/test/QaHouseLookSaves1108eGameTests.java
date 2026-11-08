package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.PieceLooks;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.world.VillagePieces;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane-e-1108-1913), house looks across a save and a load (23.10a, B1051; docs/wiki/house-looks.md, "Changing your
 * mind" and the full queue): the world is saved and loaded between the pick and the change of mind, as on the owner's
 * server after a restart. Every build site, the hall and the builder come back from their saves. Setup:
 * {@link QaHouseLooks1108cGameTests} (a plains Guard House, a hall led by the player, one builder at his bench).
 */
public class QaHouseLookSaves1108eGameTests implements FabricGameTest {
	private static final String AREA = QaHouseLooks1108cGameTests.AREA;

	/** The builder, the hall and every build site as a save and a load of the world would bring them back. */
	static Villager reload(GameTestHelper helper, QaHouseLooks1108cGameTests.Village v) {
		ServerLevel level = v.level();
		BuildSiteManager sites = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, sites);
		BlockPos hall = v.hall();
		CompoundTag hallTag = level.getBlockEntity(hall).saveWithFullMetadata(level.registryAccess());
		level.removeBlockEntity(hall);
		BlockEntity back = BlockEntity.loadStatic(hall, level.getBlockState(hall), hallTag, level.registryAccess());
		helper.assertTrue(back instanceof VillageHallBlockEntity, "the hall didn't load: " + back);
		level.setBlockEntity(back);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(v.builder().save(tag), "the builder wasn't saved");
		v.builder().discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load: " + loaded);
		level.addFreshEntity(loaded);
		return (Villager) loaded;
	}

	static VillageHallBlockEntity hall(QaHouseLooks1108cGameTests.Village v) {
		return (VillageHallBlockEntity) v.level().getBlockEntity(v.hall());
	}

	/** The outside rebuilds open for the house, whatever their look. */
	static List<BuildSite> rebuilds(QaHouseLooks1108cGameTests.Village v, VillagePieces.Piece piece) {
		return BuildSiteManager.get(v.level()).all().stream().filter(s -> VillagePieces.isOutside(s.structure())
			&& s.placement().origin().equals(piece.placement().origin())).toList();
	}

	/**
	 * The desert outside waits as the builder's fifth queued build; the world is saved and loaded; the leader then picks
	 * taiga on the hall's page. The taiga rebuild takes the desert one's place: one rebuild for the house, the queue still
	 * five, the hall remembering taiga.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLookSavesQueueChange"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLookSavesQueueChange")
	public void theFifthWaitingLookChangedAfterARestartReplacesIt(GameTestHelper helper) {
		QaHouseLooks1108cGameTests.Village v = QaHouseLooks1108cGameTests.village(helper, true);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		QaHouseLooks1108cGameTests.busy(helper, v, Builders.MAX_QUEUE - 1);
		helper.assertTrue(PieceLooks.choose(level, v.hall(), v.owner(), found, "desert") == PieceLooks.Outcome.QUEUED, "setup: desert not queued");
		helper.assertTrue(Builders.queue(level, v.builder()).size() == Builders.MAX_QUEUE, "setup: queue " + Builders.queue(level, v.builder()).size());
		Villager builder = reload(helper, v);
		helper.assertTrue(Builders.queue(level, builder).size() == Builders.MAX_QUEUE, "the reload lost queued builds: " + Builders.queue(level, builder).size());
		VillagePieces.Piece again = QaHouseLooks1108cGameTests.piece(v);
		ChoiceMenu view = QaHouseLooks1108cGameTests.houseView(helper, v, v.owner());
		QaHouseLooks1108cGameTests.click(helper, view, v.owner(), "taiga");
		List<BuildSite> open = rebuilds(v, again);
		helper.assertTrue(open.size() == 1 && open.get(0).structure().equals(VillagePieces.outsideId("taiga", "guard_house")),
			"after the reload, changing the queued desert look to taiga left: " + open.stream().map(BuildSite::structure).toList());
		helper.assertTrue(Builders.queue(level, builder).size() == Builders.MAX_QUEUE, "the queue is " + Builders.queue(level, builder).size() + ", not 5");
		helper.assertTrue(PieceLooks.chosen(level, v.hall(), again).map(c -> c.look().equals("taiga")).orElse(false),
			"the hall remembers " + hall(v).pieceLooks());
		helper.succeed();
	}

	/**
	 * The desert pick is queued, then the world saved and loaded. The hall still remembers desert, the rebuild is still
	 * queued, and picking desert again says it is already being built (no second rebuild).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLookSavesSamePick"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLookSavesSamePick")
	public void aQueuedLookKeptOverARestartIsNotQueuedTwice(GameTestHelper helper) {
		QaHouseLooks1108cGameTests.Village v = QaHouseLooks1108cGameTests.village(helper, true);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		QaHouseLooks1108cGameTests.busy(helper, v, 1);
		helper.assertTrue(PieceLooks.choose(level, v.hall(), v.owner(), found, "desert") == PieceLooks.Outcome.QUEUED, "setup: desert not queued");
		Villager builder = reload(helper, v);
		VillagePieces.Piece again = QaHouseLooks1108cGameTests.piece(v);
		helper.assertTrue(PieceLooks.chosen(level, v.hall(), again).map(c -> c.look().equals("desert")).orElse(false),
			"the reload forgot the pick: " + hall(v).pieceLooks());
		PieceLooks.Outcome outcome = PieceLooks.choose(level, v.hall(), v.owner(), again, "desert");
		helper.assertTrue(outcome == PieceLooks.Outcome.BUILDING, "picking desert again after the reload: " + outcome);
		helper.assertTrue(rebuilds(v, again).size() == 1 && Builders.queue(level, builder).size() == 2,
			"rebuilds " + rebuilds(v, again).size() + ", queue " + Builders.queue(level, builder).size());
		helper.succeed();
	}

	/**
	 * B1050 across a restart: the leader picks the look the house already has (remembered, nothing built), the world is
	 * saved and loaded, and the house is then torn down. A pick says "That house isn't there any more." and nothing is
	 * put up on the empty lot.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLookSavesTornDown"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLookSavesTornDown")
	public void aRememberedLookOfAHouseTornDownAfterARestartIsGone(GameTestHelper helper) {
		QaHouseLooks1108cGameTests.Village v = QaHouseLooks1108cGameTests.village(helper, true);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		helper.assertTrue(PieceLooks.choose(level, v.hall(), v.owner(), found, "plains") == PieceLooks.Outcome.SAME, "setup: not SAME");
		Villager builder = reload(helper, v);
		helper.assertTrue(hall(v).pieceLooks().size() == 1, "the reload forgot the remembered look: " + hall(v).pieceLooks());
		QaHouseLooks1108cGameTests.tearDown(v);
		PieceLooks.Outcome outcome = PieceLooks.choose(level, v.hall(), v.owner(), found, "desert");
		String said = PieceLooks.message(level, v.hall(), found, "desert", outcome, v.owner()).getString();
		helper.assertTrue(outcome == PieceLooks.Outcome.GONE && said.equals("That house isn't there any more."), outcome + ": \"" + said + "\"");
		helper.assertTrue(rebuilds(v, found).isEmpty() && Builders.activeSite(level, builder) == null, "something was put up on the empty lot");
		helper.succeed();
	}

	/**
	 * The only builder's queue is full and the world is saved and loaded; a pick is refused with the full-queue line
	 * naming him, never the out-of-reach one, and nothing is queued.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLookSavesQueueFull"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLookSavesQueueFull")
	public void aFullQueueAfterARestartIsToldAsFull(GameTestHelper helper) {
		QaHouseLooks1108cGameTests.Village v = QaHouseLooks1108cGameTests.village(helper, true);
		ServerLevel level = v.level();
		QaHouseLooks1108cGameTests.busy(helper, v, Builders.MAX_QUEUE);
		Villager builder = reload(helper, v);
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		PieceLooks.Outcome outcome = PieceLooks.choose(level, v.hall(), v.owner(), found, "desert");
		String said = PieceLooks.message(level, v.hall(), found, "desert", outcome, v.owner()).getString();
		String want = builder.getDisplayName().getString() + " already has 5 blueprints lined up. Hire another builder, or wait.";
		helper.assertTrue(outcome == PieceLooks.Outcome.QUEUE_FULL && said.equals(want), "a full queue after the reload: " + outcome + ", \"" + said + "\"");
		helper.assertTrue(rebuilds(v, found).isEmpty() && Builders.queue(level, builder).size() == Builders.MAX_QUEUE,
			"something was queued: " + rebuilds(v, found).size() + " rebuilds, queue " + Builders.queue(level, builder).size());
		helper.succeed();
	}
}
