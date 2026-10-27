package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.wonder.WonderCrews;
import io.github.jcondedata.aliveworkplace.wonder.WonderGoods;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-10-27) for ROADMAP 35.5 (crews) and 35.7 (goods), the gaps the earlier suites leave: the village's reach on a
 * diagonal and one block up (it's a sphere round the hall, not a square), a builder whose Blueprint Table is broken
 * before it could join never joins, a member whose table is broken mid-stage leaves the crew while one with a table
 * stays, and goods handed in at the stone against a stockpile that changes: only what's short is taken from the
 * player's hand, a full line takes nothing, goods taken back out of the stockpile make the stage wait again, and the
 * dedication takes exactly the goods (nothing duplicated or lost).
 */
public class QaWonderCrew1027GameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final BlockPos HALL = new BlockPos(2, 2, 27);

	/**
	 * The village reaches 10 blocks: a table 6 east and 8 north of the hall (exactly 10) joins the crew; one at the same
	 * spot but a block higher (just past 10) never does, not even when every builder is called.
	 */
	//$ gametest_ticks_batch TALL '100' '"qa1027Diagonal"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qa1027Diagonal")
	public void aTableDiagonallyAtTheReachJoinsAndOneBlockHigherNever(GameTestHelper helper) {
		QaWonderCrewGameTests.Village v = QaWonderCrewGameTests.village(helper, 1, 4);
		ServerLevel level = v.level();
		WonderSite site = QaWonderCrewGameTests.begin(helper, v, v.builders().get(0));
		helper.assertTrue(Builders.activeSite(level, v.builders().get(0)) != null, "the lead didn't start stage I");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 10;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		Villager beyond = QaWonderCrewGameTests.builder(helper, v, HALL.offset(6, 1, -8), HALL.offset(6, 0, -6));
		Villager at = QaWonderCrewGameTests.builder(helper, v, HALL.offset(8, 0, -6), HALL.offset(8, 0, -4));
		helper.assertTrue(Builders.benchPos(at).orElseThrow().distSqr(v.hall()) == 100 && Builders.benchPos(beyond).orElseThrow().distSqr(v.hall()) == 101,
			"the tables aren't at 10 and just past 10 blocks: " + Builders.benchPos(at).orElseThrow().distSqr(v.hall()) + ", "
				+ Builders.benchPos(beyond).orElseThrow().distSqr(v.hall()));
		for (int t = 0; t < 40; t++) {
			beyond.tickCount = t;
			helper.assertTrue(Builders.activeSite(level, beyond) == null, "a builder whose table is just past the village's reach (a block up) joined");
		}
		WonderCrews.callEveryone(level, site);
		for (int t = 0; t < 40; t++) {
			beyond.tickCount = t;
			helper.assertTrue(Builders.activeSite(level, beyond) == null, "a builder just past the reach came when every builder was called");
		}
		at.tickCount = 20;
		BuildSite joined = Builders.activeSite(level, at);
		helper.assertTrue(joined != null && site.id().equals(joined.wonder()), "a builder whose table is diagonally exactly at the reach didn't join: " + joined);
		List<UUID> crew = WonderCrews.crew(level, QaWonderCrewGameTests.now(level, site.id()));
		helper.assertTrue(crew.size() == 2 && crew.contains(at.getUUID()) && !crew.contains(beyond.getUUID()), "the crew isn't the lead and the diagonal builder: " + crew.size());
		helper.succeed();
	}

	/** A builder whose Blueprint Table is broken before the Wonder begins never joins its crew. */
	//$ gametest_ticks_batch TALL '300' '"qa1027NoTable"'
	@GameTest(template = TALL, timeoutTicks = 300, batch = "qa1027NoTable")
	public void aBuilderWhoseTableIsBrokenNeverJoins(GameTestHelper helper) {
		QaWonderCrewGameTests.Village v = QaWonderCrewGameTests.village(helper, 3, 4);
		ServerLevel level = v.level();
		Villager tableless = v.builders().get(1);
		Villager kept = v.builders().get(2);
		level.destroyBlock(v.benches().get(1), false);
		tableless.tickCount = 1;
		WonderSite site = QaWonderCrewGameTests.begin(helper, v, v.builders().get(0));
		UUID id = site.id();
		AtomicBoolean keptJoined = new AtomicBoolean(false);
		helper.onEachTick(() -> {
			WonderSite now = QaWonderCrewGameTests.now(level, id);
			if (now == null) {
				return;
			}
			List<UUID> crew = WonderCrews.crew(level, now);
			helper.assertTrue(!crew.contains(tableless.getUUID()), "a builder whose Blueprint Table was broken joined the crew at tick " + level.getGameTime());
			if (crew.contains(kept.getUUID())) {
				keptJoined.set(true);
			}
		});
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(keptJoined.get(), "the builder with a table never joined (so the test proves nothing)");
			var job = ModAttachments.BUILDER_JOB.get(tableless);
			helper.assertTrue(job == null || !job.helper(), "the tableless builder holds a crew job: " + job);
			helper.succeed();
		});
	}

	/**
	 * Mid-stage, one member's Blueprint Table is broken: within a few seconds it is off the crew, while the member whose
	 * table stands is still on it (unless the stage ended first).
	 */
	//$ gametest_ticks_batch TALL '2400' '"qa1027TableBroken"'
	@GameTest(template = TALL, timeoutTicks = 2400, batch = "qa1027TableBroken")
	public void aMemberWhoseTableIsBrokenMidStageLeavesTheCrew(GameTestHelper helper) {
		QaWonderCrewGameTests.Village v = QaWonderCrewGameTests.village(helper, 3, 12);
		ServerLevel level = v.level();
		Villager loser = v.builders().get(1);
		Villager kept = v.builders().get(2);
		WonderSite site = QaWonderCrewGameTests.begin(helper, v, v.builders().get(0));
		UUID id = site.id();
		AtomicLong brokeAt = new AtomicLong(-1);
		AtomicBoolean keptAfter = new AtomicBoolean(false);
		AtomicBoolean stageMoved = new AtomicBoolean(false);
		int[] stageAtBreak = {-1};
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			WonderSite now = QaWonderCrewGameTests.now(level, id);
			if (now == null) {
				return;
			}
			List<UUID> crew = WonderCrews.crew(level, now);
			if (brokeAt.get() < 0) {
				if (crew.contains(loser.getUUID()) && crew.contains(kept.getUUID())) {
					level.destroyBlock(v.benches().get(1), false);
					brokeAt.set(level.getGameTime());
					stageAtBreak[0] = now.stage();
				}
				return;
			}
			if (now.stage() != stageAtBreak[0] || now.done()) {
				stageMoved.set(true);
			}
			if (level.getGameTime() - brokeAt.get() > 300 && crew.contains(kept.getUUID())) {
				keptAfter.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokeAt.get() >= 0, "the crew was never the lead and both members");
			helper.assertTrue(level.getGameTime() - brokeAt.get() > 400, "waiting after the break");
			WonderSite now = QaWonderCrewGameTests.now(level, id);
			List<UUID> crew = now == null ? List.of() : WonderCrews.crew(level, now);
			helper.assertTrue(!crew.contains(loser.getUUID()), "a member whose Blueprint Table was broken " + (level.getGameTime() - brokeAt.get())
				+ " ticks ago is still on the crew");
			helper.assertTrue(keptAfter.get() || stageMoved.get(), "the member whose table stands left the crew too");
		});
	}

	// ---------------------------------------------------------------- goods handed in against a changing stockpile

	private static ItemStack healing(int n) {
		return PotionContents.createItemStack(Items.POTION, Potions.HEALING).copyWithCount(n);
	}

	private static int count(Container box, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).is(item)) {
				n += box.getItem(i).getCount();
			}
		}
		return n;
	}

	private static int handed(WonderSite site, int line) {
		return site.delivered().stream().filter(h -> h.line() == line).mapToInt(WonderSite.Handed::count).sum();
	}

	/**
	 * The folly's stage II waits for flowers, potions of healing and books. With one book in the stockpile, a player
	 * holding 64 books hands in at the stone: only what's short is taken; a second click takes nothing; the book taken
	 * back out of the stockpile makes the line short again and the stage waits; a hand-in fills it. With the rest in, the
	 * stage is dedicated and takes exactly its goods: the spare flower and the player's books and potion stay.
	 */
	//$ gametest_batch AREA '"qa1027Goods"'
	@GameTest(template = AREA, batch = "qa1027Goods")
	public void goodsHandedInAgainstAChangingStockpileAreTakenExactly(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos hallRel = new BlockPos(14, 2, 14);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallRel);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.FOUNDATION_STONE);
		helper.setBlock(new BlockPos(2, 2, 5), Blocks.CHEST);
		BlockPos stone = helper.absolutePos(new BlockPos(2, 2, 2));
		Container pile = helper.getBlockEntity(new BlockPos(2, 2, 5));
		WonderManager manager = WonderManager.get(level);
		WonderSite begun = manager.begin(FOLLY, helper.absolutePos(hallRel), owner.getUUID(), "Owner", Chronicle.day(level));
		UUID id = begun.id();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite s : List.copyOf(sites.all())) {
				if (id.equals(s.wonder())) {
					sites.remove(s.id());
				}
			}
			Requests.clearAt(level, stone);
			WonderManager.get(level).remove(id);
		});
		manager.place(id, new BlueprintData.Placement(Ids.of(level.dimension()), stone.offset(1, 0, 1), Rotation.NONE, Mirror.NONE), stone);
		manager.setStage(id, 2);
		WonderSite site = manager.site(id).orElseThrow();
		List<WonderGoods.Line> lines = WonderGoods.lines(level, site);
		helper.assertTrue(lines.size() == 3 && lines.get(2).need() >= 2, "stage II's lines: " + lines);
		int books = lines.get(2).need();
		int flowers = lines.get(0).need();
		int potions = lines.get(1).need();

		pile.setItem(0, new ItemStack(Items.BOOK, 1));
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK, 64));
		helper.assertTrue(WonderGoods.handInAtStone(owner, stone, owner.getMainHandItem()), "the books weren't handed in");
		helper.assertTrue(owner.getMainHandItem().getCount() == 64 - (books - 1) && handed(manager.site(id).orElseThrow(), 2) == books - 1,
			"with 1 book in the stockpile, " + (64 - owner.getMainHandItem().getCount()) + " of the player's were taken (" + (books - 1) + " short)");
		helper.assertTrue(WonderGoods.handInAtStone(owner, stone, owner.getMainHandItem()), "a full line's click wasn't answered");
		helper.assertTrue(owner.getMainHandItem().getCount() == 64 - (books - 1) && handed(manager.site(id).orElseThrow(), 2) == books - 1,
			"a click on a full line took books: " + owner.getMainHandItem().getCount() + " in hand");

		pile.setItem(0, ItemStack.EMPTY); // the stockpile's book taken back out
		pile.setItem(1, new ItemStack(Items.POPPY, flowers + 1));
		owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		ItemStack bookHand = owner.getMainHandItem();
		owner.setItemInHand(InteractionHand.MAIN_HAND, healing(potions + 1));
		helper.assertTrue(WonderGoods.handInAtStone(owner, stone, owner.getMainHandItem()), "the potions weren't handed in");
		helper.assertTrue(owner.getMainHandItem().getCount() == 1, "the potions taken from a hand of " + (potions + 1) + ": " + (potions + 1 - owner.getMainHandItem().getCount()));
		WonderStages.tick(level);
		WonderGoods.Line bookLine = WonderGoods.lines(level, manager.site(id).orElseThrow()).get(2);
		helper.assertTrue(!manager.site(id).orElseThrow().started() && !bookLine.full() && bookLine.left() == 1,
			"with the stockpile's book taken out the stage began or the line reads " + bookLine);

		ItemStack potionLeft = owner.getMainHandItem();
		owner.setItemInHand(InteractionHand.MAIN_HAND, bookHand);
		helper.assertTrue(WonderGoods.handInAtStone(owner, stone, owner.getMainHandItem()), "the last book wasn't handed in");
		helper.assertTrue(owner.getMainHandItem().getCount() == 64 - books, "books in hand after filling the line: " + owner.getMainHandItem().getCount());
		WonderStages.tick(level);
		WonderSite now = manager.site(id).orElseThrow();
		helper.assertTrue(now.started() && now.dedicated() && now.stage() == 2, "stage II wasn't dedicated with every line in: " + now);
		helper.assertTrue(count(pile, Items.POPPY) == 1 && count(pile, Items.BOOK) == 0 && count(pile, Items.POTION) == 0,
			"the dedication didn't take exactly the flowers: " + count(pile, Items.POPPY) + " poppies, " + count(pile, Items.BOOK) + " books left in the stockpile");
		helper.assertTrue(owner.getMainHandItem().getCount() == 64 - books && potionLeft.getCount() == 1,
			"the dedication touched the player's spare goods: " + owner.getMainHandItem().getCount() + " books, " + potionLeft.getCount() + " potion");
		WonderStages.tick(level);
		helper.assertTrue(count(pile, Items.POPPY) == 1 && manager.site(id).orElseThrow().parts().equals(now.parts()), "dedicated twice");
		helper.succeed();
	}
}
