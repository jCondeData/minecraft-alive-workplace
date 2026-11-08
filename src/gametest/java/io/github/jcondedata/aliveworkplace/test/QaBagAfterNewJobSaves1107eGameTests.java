package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.DROP;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.GRINDSTONE;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.STOCK;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.bag;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.chest;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.damageIn;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.mended;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.onGround;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.weaponsmith;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.worn;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1107-1612, saves; the verification of B1007-B1009). B970 and B353: a Weaponsmith or a Carpenter who loses
 * his workstation while carrying what isn't his (a player's worn sword and its iron, a builder's logs) gives it back after
 * a while, to a chest or the ground, whatever job he takes next; a builder's request he held is free again. A novice (no
 * trades, as every worker is on his first day) loses his profession with the workstation, and a free lectern near by
 * makes him a Teacher or a Scholar (what B1007 and B1009 saw). These add a server
 * restart (the villager saved and reloaded) before the workstation goes, or after he has taken the new job and before he
 * has given anything back, and check that nothing is kept or duplicated. A control checks the fix isn't over-eager: an
 * experienced smith reloaded mid-carry, with a free lectern near, mends. The jobless villager is employed at the lectern
 * as soon as he loses his job (as vanilla would, a little later), so every run reaches the new-job case.
 */
public class QaBagAfterNewJobSaves1107eGameTests implements FabricGameTest {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos SMITH_LECTERN = new BlockPos(7, 2, 4);

	private static final String CRAFT_AREA = QaCrafterFurnitureGameTests.AREA;
	private static final BlockPos BENCH = QaCrafterFurnitureGameTests.BENCH;
	private static final BlockPos CHEST = QaCrafterFurnitureGameTests.CHEST;
	private static final BlockPos STATION = QaCrafterFurnitureGameTests.STATION;
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);
	private static final BlockPos CRAFT_LECTERN = new BlockPos(11, 2, 4);

	// --- the smith ----------------------------------------------------------------------------------------------------

	/**
	 * A novice smith saved and reloaded with the worn sword in his bag; then the grindstone is broken and he becomes a
	 * Teacher at the lectern near by. The sword and all 10 iron are back in a chest or on the ground, his bag is empty, and
	 * there is exactly one sword in the world.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1107eSmithReloadThenLectern"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1107eSmithReloadThenLectern")
	public void qaANoviceSmithReloadedThenMadeATeacherGivesTheSwordBack1107e(GameTestHelper helper) {
		smithRun(helper, true, false, ModVillagers.TEACHER);
	}

	/**
	 * A novice smith carrying the sword loses the grindstone, becomes a Scholar, and is saved and reloaded at once
	 * (the in-memory countdown and carrier set are gone): the sword and the iron still come out of his bag.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1107eSmithLecternThenReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1107eSmithLecternThenReload")
	public void qaANoviceSmithReloadedAsAScholarGivesTheSwordBack1107e(GameTestHelper helper) {
		smithRun(helper, false, true, ModVillagers.SCHOLAR);
	}

	private static void smithRun(GameTestHelper helper, boolean reloadBefore, boolean reloadAfter, VillagerProfession next) {
		Villager first = weaponsmith(helper);
		first.setVillagerXp(0); // never traded: vanilla takes the profession away with the grindstone
		helper.setBlock(SMITH_LECTERN, Blocks.LECTERN);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		Villager[] smith = {first};
		long[] brokenAt = {-1};
		long[] librarianAt = {-1};
		String[] atReload = {"not reloaded"};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0) {
				if (bag(smith[0]).count(Items.IRON_SWORD) == 1 && damageIn(drop, Items.IRON_SWORD) == -1) {
					if (reloadBefore) {
						smith[0] = reload(helper, smith[0]);
						atReload[0] = "reloaded with " + bag(smith[0]).stacks();
					}
					helper.destroyBlock(GRINDSTONE);
					brokenAt[0] = helper.getTick();
				}
				return;
			}
			if (librarianAt[0] < 0) {
				VillagerProfession now = smith[0].getVillagerData().getProfession();
				if (now == VillagerProfession.NONE) {
					Jobs.employ(helper.getLevel(), smith[0], helper.absolutePos(SMITH_LECTERN), PoiTypes.LIBRARIAN, next);
				}
				if (smith[0].getVillagerData().getProfession() != VillagerProfession.WEAPONSMITH
					&& smith[0].getVillagerData().getProfession() != VillagerProfession.NONE) {
					librarianAt[0] = helper.getTick();
					if (reloadAfter) {
						String before = String.valueOf(bag(smith[0]).stacks());
						smith[0] = reload(helper, smith[0]);
						atReload[0] = "reloaded as " + smith[0].getVillagerData().getProfession() + " " + (helper.getTick() - brokenAt[0])
							+ " ticks after the grindstone broke, bag " + before;
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0, "the novice smith never took the sword (bag " + bag(smith[0]).stacks() + ")");
			helper.assertTrue(librarianAt[0] >= 0, "the ex-smith never took another job: " + smith[0].getVillagerData().getProfession());
			helper.assertTrue(helper.getTick() >= librarianAt[0] + 600, "letting him react");
			int swords = drop.countItem(Items.IRON_SWORD) + stock.countItem(Items.IRON_SWORD) + onGround(helper, Items.IRON_SWORD);
			int iron = drop.countItem(Items.IRON_INGOT) + stock.countItem(Items.IRON_INGOT) + onGround(helper, Items.IRON_INGOT);
			int everywhere = swords + inBags(helper, Items.IRON_SWORD);
			helper.assertTrue(swords == 1 && iron == 10 && everywhere == 1 && bag(smith[0]).isEmpty(), "a novice smith (" + atReload[0]
				+ ") lost his grindstone and is now " + smith[0].getVillagerData().getProfession() + ": swords a player can reach " + swords
				+ ", swords in the world " + everywhere + ", iron " + iron + " of 10; his bag holds " + bag(smith[0]).stacks());
			LOG.info("qa1107e smith ({}): broken at {}, new job {} at {}, checked at {}, swords {} iron {}", atReload[0], brokenAt[0],
				smith[0].getVillagerData().getProfession(), librarianAt[0], helper.getTick(), swords, iron);
		});
	}

	/**
	 * The control: an experienced smith (he has traded, so he keeps his profession) is saved and reloaded with the worn
	 * sword in his bag, his grindstone untouched and a free lectern near by. He is still a Weaponsmith and mends the sword
	 * (the saved mending job marking him as a carrier doesn't make him drop it), and the sword never lands on the ground.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1107eExperiencedSmithReloadMends"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1107eExperiencedSmithReloadMends")
	public void qaAnExperiencedSmithReloadedMidCarryStillMends1107e(GameTestHelper helper) {
		Villager first = weaponsmith(helper);
		first.setVillagerXp(10);
		helper.setBlock(SMITH_LECTERN, Blocks.LECTERN);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		Villager[] smith = {first};
		boolean[] reloaded = {false};
		int[] groundSeen = {0};
		helper.onEachTick(() -> {
			if (!reloaded[0] && bag(smith[0]).count(Items.IRON_SWORD) == 1 && damageIn(drop, Items.IRON_SWORD) == -1) {
				smith[0] = reload(helper, smith[0]);
				reloaded[0] = true;
			}
			groundSeen[0] = Math.max(groundSeen[0], onGround(helper, Items.IRON_SWORD));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the smith never took the sword (bag " + bag(smith[0]).stacks() + ")");
			int swords = drop.countItem(Items.IRON_SWORD) + stock.countItem(Items.IRON_SWORD) + onGround(helper, Items.IRON_SWORD)
				+ inBags(helper, Items.IRON_SWORD);
			boolean fixed = damageIn(drop, Items.IRON_SWORD) == 0 || damageIn(stock, Items.IRON_SWORD) == 0;
			helper.assertTrue(fixed && mended(smith[0]) == 1 && swords == 1 && groundSeen[0] == 0
				&& smith[0].getVillagerData().getProfession() == VillagerProfession.WEAPONSMITH,
				"an experienced smith reloaded mid-carry by his own grindstone: now " + smith[0].getVillagerData().getProfession()
					+ ", mended " + mended(smith[0]) + ", sword damage in the drop-off chest " + damageIn(drop, Items.IRON_SWORD)
					+ ", swords in the world " + swords + ", most swords seen on the ground " + groundSeen[0] + ", bag " + bag(smith[0]).stacks());
			LOG.info("qa1107e experienced smith reloaded: mended {} by {}", mended(smith[0]), helper.getTick());
		});
	}

	// --- the carpenter ------------------------------------------------------------------------------------------------

	/**
	 * A novice Carpenter saved and reloaded while he carries a builder's logs; then the bench is broken and he becomes a
	 * Scholar. His crafting job is let go, the logs leave his bag, and the request is free again: a new Carpenter at a
	 * new bench takes it and the builder's barrels get built.
	 */
	//$ gametest_ticks_batch CRAFT_AREA '9000' '"qa1107eCarpenterReloadThenLectern"'
	@GameTest(template = CRAFT_AREA, timeoutTicks = 9000, batch = "qa1107eCarpenterReloadThenLectern")
	public void qaANoviceCarpenterReloadedThenMadeAScholarLetsTheRequestGo1107e(GameTestHelper helper) {
		carpenterRun(helper, true, false, ModVillagers.SCHOLAR);
	}

	/**
	 * A novice Carpenter carrying the logs loses the bench, becomes a Teacher, and is saved and reloaded at once (the
	 * profession that took the job must come back from the save): the job is still let go, the logs given back, and the
	 * barrels built by a new Carpenter.
	 */
	//$ gametest_ticks_batch CRAFT_AREA '9000' '"qa1107eCarpenterLecternThenReload"'
	@GameTest(template = CRAFT_AREA, timeoutTicks = 9000, batch = "qa1107eCarpenterLecternThenReload")
	public void qaANoviceCarpenterReloadedAsATeacherLetsTheRequestGo1107e(GameTestHelper helper) {
		carpenterRun(helper, false, true, ModVillagers.TEACHER);
	}

	private static void carpenterRun(GameTestHelper helper, boolean reloadBefore, boolean reloadAfter, VillagerProfession next) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL,
			new ItemStack(Items.OAK_LOG, 6));
		Villager first = QaCrafterFurnitureGameTests.carpenter(helper, STATION);
		first.setVillagerXp(0); // never traded: vanilla takes his profession away with the bench
		helper.setBlock(CRAFT_LECTERN, Blocks.LECTERN);
		Villager[] carpenter = {first};
		long[] brokenAt = {-1};
		long[] librarianAt = {-1};
		long[] letGoAt = {-1};
		String[] atReload = {"not reloaded"};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0) {
				BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter[0]);
				if (bag != null && !bag.isEmpty()) {
					if (reloadBefore) {
						carpenter[0] = reloadCarpenter(helper, carpenter[0]);
						atReload[0] = "reloaded carrying " + ModAttachments.BUILDER_BAG.get(carpenter[0]).stacks();
					}
					helper.setBlock(STATION, Blocks.AIR); // the player breaks the bench while he carries the wood
					brokenAt[0] = helper.getTick();
				}
				return;
			}
			if (librarianAt[0] < 0) {
				if (carpenter[0].getVillagerData().getProfession() == VillagerProfession.NONE) {
					Jobs.employ(helper.getLevel(), carpenter[0], helper.absolutePos(CRAFT_LECTERN), PoiTypes.LIBRARIAN, next);
				}
				VillagerProfession now = carpenter[0].getVillagerData().getProfession();
				if (now != VillagerProfession.NONE && now != ModVillagers.CARPENTER) {
					librarianAt[0] = helper.getTick();
					if (reloadAfter) {
						BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter[0]);
						String before = (bag == null ? "nothing" : String.valueOf(bag.stacks())) + ", job "
							+ (ModAttachments.CRAFTER_JOB.get(carpenter[0]) == null ? "gone" : "held");
						carpenter[0] = reloadCarpenter(helper, carpenter[0]);
						atReload[0] = "reloaded as " + now + " " + (helper.getTick() - brokenAt[0]) + " ticks after the bench broke, carrying " + before;
					}
				}
				return;
			}
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter[0]);
			if (letGoAt[0] < 0 && ModAttachments.CRAFTER_JOB.get(carpenter[0]) == null && (bag == null || bag.isEmpty())) {
				letGoAt[0] = helper.getTick();
				// The request is free again: a new Carpenter at a new bench (the old spot) takes it up.
				QaCrafterFurnitureGameTests.carpenter(helper, STATION);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0, "the carpenter never carried anything");
			helper.assertTrue(librarianAt[0] >= 0, "the ex-carpenter never took another job: " + carpenter[0].getVillagerData().getProfession());
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter[0]);
			boolean job = ModAttachments.CRAFTER_JOB.get(carpenter[0]) != null;
			helper.assertTrue(letGoAt[0] >= 0, "a novice carpenter (" + atReload[0] + ") lost his bench while carrying wood and is now "
				+ carpenter[0].getVillagerData().getProfession() + ", " + (helper.getTick() - librarianAt[0]) + " ticks in his new job: crafting job "
				+ (job ? "still held" : "let go") + ", bag " + (bag == null ? "none" : bag.stacks()) + " (logs on the ground "
				+ QaCrafterFurnitureGameTests.loose(helper, Items.OAK_LOG) + ", planks " + QaCrafterFurnitureGameTests.loose(helper, Items.OAK_PLANKS) + ")");
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			LOG.info("qa1107e carpenter ({}): broken at {}, new job {} at {}, let go at {}, built by {}", atReload[0], brokenAt[0],
				carpenter[0].getVillagerData().getProfession(), librarianAt[0], letGoAt[0], helper.getTick());
			helper.assertTrue(bag == null || bag.isEmpty(), "the ex-carpenter took something again: " + (bag == null ? "nothing" : bag.stacks()));
		});
	}

	private static Villager reloadCarpenter(GameTestHelper helper, Villager villager) {
		Villager copy = QaCrafterFurnitureGameTests.reload(helper.getLevel(), villager);
		Leftovers.after(helper, copy::discard);
		return copy;
	}

	// --- helpers ------------------------------------------------------------------------------------------------------

	/** {@code item} in the bags of every villager in and around the test area. */
	private static int inBags(GameTestHelper helper, Item item) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8)).stream()
			.mapToInt(v -> {
				BuilderBag bag = ModAttachments.BUILDER_BAG.get(v);
				return bag == null ? 0 : bag.count(item);
			}).sum();
	}
}
