package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.DROP;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.GRINDSTONE;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.STOCK;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.bag;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.chest;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.damageIn;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.weaponsmith;
import static io.github.jcondedata.aliveworkplace.test.QaSmithScribe1105cGameTests.worn;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1107-1553) for B1007-B1009's fix, from the bugs' words: a novice (0 XP) Weaponsmith carrying your worn sword
 * and iron, or a novice Carpenter carrying a builder's logs, loses his job with his workstation (vanilla) and the items
 * and the request go back "whatever job he takes next", restart or not. The bug tests leave it to chance whether he
 * takes the free lectern; these make him take it the moment vanilla makes him jobless (as a Scholar or a Librarian, a
 * Librarian being a crafting job of its own), with a save and reload first, and check every sword, ingot and log is in a
 * chest or on the ground exactly once, the crafting job is let go, and the builder's request is free again: a second
 * Carpenter takes it before the first one's claim would have run out by itself, and the build is finished.
 */
public class QaBagNewJob1107cGameTests {
	private static final String SMITH_AREA = "aliveworkplace_test:big_area";
	private static final String CRAFT_AREA = QaCrafterFurnitureGameTests.AREA;
	private static final BlockPos SMITH_LECTERN = new BlockPos(7, 2, 4);
	private static final BlockPos BENCH = QaCrafterFurnitureGameTests.BENCH;
	private static final BlockPos CHEST = QaCrafterFurnitureGameTests.CHEST;
	private static final BlockPos STATION = QaCrafterFurnitureGameTests.STATION;
	private static final BlockPos SECOND_STATION = new BlockPos(14, 2, 12);
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);
	private static final BlockPos CRAFT_LECTERN = new BlockPos(11, 2, 4);
	/** How long a crafter's claim on a request keeps others off it by itself (CrafterWork.CLAIM_TICKS). */
	private static final long CLAIM_TICKS = 2400;

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** Every {@code item} a player can reach: in any container in the test area or lying on the ground near it. */
	static int reachable(GameTestHelper helper, Item item) {
		int n = 0;
		AABB box = helper.getBounds().inflate(8);
		BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
		BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			BlockEntity be = helper.getLevel().getBlockEntity(pos);
			if (be instanceof Container container) {
				n += container.countItem(item);
			}
		}
		return n + helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(item)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	/** Gives the jobless {@code v} the lectern at {@code lectern} as {@code job}, as vanilla does when he walks up to it. */
	static void takeLectern(GameTestHelper helper, Villager v, BlockPos lectern, VillagerProfession job) {
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(lectern), PoiTypes.LIBRARIAN, job);
	}

	/** Saves {@code v} and loads it as a new villager; {@code oldSave} first takes the crafting job's `profession` out. */
	static Villager reload(GameTestHelper helper, Villager v, boolean oldSave) {
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		if (oldSave) {
			helper.assertTrue(stripJobProfession(tag) == 1, "no saved crafting job with a profession found in " + tag);
		}
		v.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		helper.assertTrue(copy != null, "a villager to load into");
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		Leftovers.after(helper, copy::discard);
		return copy;
	}

	/** Removes `profession` from every saved compound that also has the crafting job's `claim` (a save from before B1009). */
	private static int stripJobProfession(Tag tag) {
		int n = 0;
		if (tag instanceof CompoundTag c) {
			if (c.contains("target") && c.contains("profession") && c.contains("deliver_to")) {
				c.remove("profession");
				n++;
			}
			for (String key : new ArrayList<>(c.getAllKeys())) {
				n += stripJobProfession(c.get(key));
			}
		} else if (tag instanceof ListTag list) {
			for (Tag t : list) {
				n += stripJobProfession(t);
			}
		}
		return n;
	}

	// --- The Weaponsmith (B1007, B1008) ---------------------------------------------------------------------------

	/** A novice smith saved and reloaded with the sword, then the grindstone broken: he takes the lectern as a Librarian. */
	//$ gametest_ticks_batch SMITH_AREA '2400' '"qa1107cSmithReloadLibrarian"'
	@GameTest(template = SMITH_AREA, timeoutTicks = 2400, batch = "qa1107cSmithReloadLibrarian")
	public void qaANoviceSmithReloadedWhoBecomesALibrarianGivesTheSwordBack1107c(GameTestHelper helper) {
		smith(helper, true, VillagerProfession.LIBRARIAN);
	}

	/** The same without the reload, as a Scholar: the bug test's case, made certain instead of left to vanilla. */
	//$ gametest_ticks_batch SMITH_AREA '2400' '"qa1107cSmithScholar"'
	@GameTest(template = SMITH_AREA, timeoutTicks = 2400, batch = "qa1107cSmithScholar")
	public void qaANoviceSmithWhoBecomesAScholarGivesTheSwordBack1107c(GameTestHelper helper) {
		smith(helper, false, ModVillagers.SCHOLAR);
	}

	static void smith(GameTestHelper helper, boolean restart, VillagerProfession next) {
		Villager first = weaponsmith(helper);
		first.setVillagerXp(0); // a novice: vanilla takes the job away with the grindstone
		helper.setBlock(SMITH_LECTERN, Blocks.LECTERN);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		Villager[] smith = {first};
		long[] brokenAt = {-1};
		long[] hiredAt = {-1};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0 && bag(smith[0]).count(Items.IRON_SWORD) == 1 && damageIn(drop, Items.IRON_SWORD) == -1) {
				if (restart) {
					smith[0] = reload(helper, smith[0], false);
				}
				helper.destroyBlock(GRINDSTONE);
				brokenAt[0] = helper.getTick();
			}
			if (brokenAt[0] >= 0 && hiredAt[0] < 0 && smith[0].getVillagerData().getProfession() == VillagerProfession.NONE) {
				takeLectern(helper, smith[0], SMITH_LECTERN, next);
				hiredAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0, "the novice smith never took the sword (bag " + bag(smith[0]).stacks() + ")");
			helper.assertTrue(hiredAt[0] >= 0, "vanilla never made the novice smith jobless after the grindstone went (job "
				+ smith[0].getVillagerData().getProfession() + ")");
			helper.assertTrue(helper.getTick() >= hiredAt[0] + 400, "letting him settle in his new job");
			int swords = reachable(helper, Items.IRON_SWORD);
			int iron = reachable(helper, Items.IRON_INGOT);
			VillagerProfession now = smith[0].getVillagerData().getProfession();
			helper.assertTrue(now == next, "the ex-smith should be a " + next + " now, is " + now);
			helper.assertTrue(swords == 1 && iron == 10 && bag(smith[0]).isEmpty(), (restart ? "reloaded, then " : "")
				+ "a novice smith lost his grindstone mid-job and became a " + now + ": swords a player can reach " + swords
				+ " (exactly 1), iron " + iron + " of 10; his bag holds " + bag(smith[0]).stacks());
		});
	}

	// --- The Carpenter (B1009) ------------------------------------------------------------------------------------

	/**
	 * A novice Carpenter carrying the builder's logs is saved and reloaded, then his bench broken; he takes the lectern as a
	 * Scholar. The logs come back, his crafting job goes, and the request is free: a second Carpenter takes it before the
	 * first one's claim would have run out on its own, and the barrels are made once.
	 */
	//$ gametest_ticks_batch CRAFT_AREA '7000' '"qa1107cCarpenterReloadScholar"'
	@GameTest(template = CRAFT_AREA, timeoutTicks = 7000, batch = "qa1107cCarpenterReloadScholar")
	public void qaANoviceCarpenterReloadedWhoBecomesAScholarFreesTheRequest1107c(GameTestHelper helper) {
		carpenter(helper, true, false, ModVillagers.SCHOLAR);
	}

	/**
	 * The same without the reload, as a Librarian. A Librarian is a crafting job too (books, at the lectern), and he carries
	 * on with the saved job and takes the barrels to the builder himself, or lets it go to the second Carpenter: either
	 * way the builder gets his three barrels once, and no log is left in a bag.
	 */
	//$ gametest_ticks_batch CRAFT_AREA '7000' '"qa1107cCarpenterLibrarian"'
	@GameTest(template = CRAFT_AREA, timeoutTicks = 7000, batch = "qa1107cCarpenterLibrarian")
	public void qaANoviceCarpenterWhoBecomesALibrarianStillServesTheBuilderOnce1107c(GameTestHelper helper) {
		carpenter(helper, false, false, VillagerProfession.LIBRARIAN);
	}

	/**
	 * An old save: the Carpenter's crafting job saved before B1009 (no `profession`), loaded, then his bench broken and
	 * he stays jobless. Nothing crashes, the old rule still lets the job go, and nothing is lost.
	 */
	//$ gametest_ticks_batch CRAFT_AREA '7000' '"qa1107cCarpenterOldSave"'
	@GameTest(template = CRAFT_AREA, timeoutTicks = 7000, batch = "qa1107cCarpenterOldSave")
	public void qaACarpentersJobFromAnOldSaveIsStillLetGo1107c(GameTestHelper helper) {
		carpenter(helper, true, true, null);
	}

	/**
	 * {@code restart}: saved and reloaded with the logs before the bench goes ({@code oldSave}: from before B1009);
	 * {@code next}: the job he's given at the lectern the moment vanilla makes him jobless, or none (no lectern).
	 */
	static void carpenter(GameTestHelper helper, boolean restart, boolean oldSave, VillagerProfession next) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL,
			new ItemStack(Items.OAK_LOG, 6));
		Villager[] first = {QaCrafterFurnitureGameTests.carpenter(helper, STATION)};
		first[0].setVillagerXp(0); // never traded: vanilla takes his profession away with the bench
		if (next != null) {
			helper.setBlock(CRAFT_LECTERN, Blocks.LECTERN);
		}
		long[] jobAt = {-1};
		long[] brokenAt = {-1};
		long[] hiredAt = {-1};
		long[] letGoAt = {-1};
		long[] secondJobAt = {-1};
		String[] claim = {null};
		Villager[] second = {null};
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(first[0]);
			if (jobAt[0] < 0 && job != null) {
				jobAt[0] = helper.getTick();
				claim[0] = job.claim();
			}
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(first[0]);
			if (brokenAt[0] < 0 && bag != null && bag.count(Items.OAK_LOG) > 0) {
				if (restart) {
					first[0] = reload(helper, first[0], oldSave);
					CrafterJob loaded = ModAttachments.CRAFTER_JOB.get(first[0]);
					helper.assertTrue(loaded != null && loaded.profession().isEmpty() == oldSave, "the job after the reload: " + loaded);
				}
				helper.setBlock(STATION, Blocks.AIR); // the player breaks the bench while he carries the wood
				brokenAt[0] = helper.getTick();
				// a second, experienced Carpenter: he can only take the request once the first lets it go
				second[0] = QaCrafterFurnitureGameTests.carpenter(helper, SECOND_STATION);
			}
			if (brokenAt[0] >= 0 && next != null && hiredAt[0] < 0
				&& first[0].getVillagerData().getProfession() == VillagerProfession.NONE) {
				takeLectern(helper, first[0], CRAFT_LECTERN, next);
				hiredAt[0] = helper.getTick();
			}
			if (brokenAt[0] >= 0 && letGoAt[0] < 0 && ModAttachments.CRAFTER_JOB.get(first[0]) == null) {
				letGoAt[0] = helper.getTick();
			}
			if (second[0] != null && secondJobAt[0] < 0 && ModAttachments.CRAFTER_JOB.get(second[0]) != null) {
				secondJobAt[0] = helper.getTick();
				helper.assertTrue(letGoAt[0] >= 0, "the second carpenter took a job while the first still held the request");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt[0] >= 0, "the novice carpenter never carried the logs");
			String who = (restart ? (oldSave ? "loaded from an old save, then " : "reloaded, then ") : "")
				+ "a novice carpenter lost his bench carrying the logs";
			if (next != null) {
				helper.assertTrue(hiredAt[0] >= 0, who + " but vanilla never made him jobless (job " + first[0].getVillagerData().getProfession() + ")");
			}
			boolean crafts = next == VillagerProfession.LIBRARIAN; // a crafting job of his own: he may finish the saved one
			helper.assertTrue(letGoAt[0] >= 0, who + " and is now " + first[0].getVillagerData().getProfession()
				+ ": his crafting job is still held " + (helper.getTick() - brokenAt[0]) + " ticks later, bag "
				+ ModAttachments.BUILDER_BAG.getOrCreate(first[0]).stacks() + " (logs a player can reach " + reachable(helper, Items.OAK_LOG) + ")");
			helper.assertTrue(crafts || secondJobAt[0] >= 0, who + " and is now " + first[0].getVillagerData().getProfession() + ": the second carpenter never took"
				+ " the builder's request (" + (helper.getTick() - brokenAt[0]) + " ticks after; job let go at +" + (letGoAt[0] - brokenAt[0])
				+ "; made by him " + ModAttachments.ITEMS_CRAFTED.getOrElse(first[0], 0) + ", by the second " + ModAttachments.ITEMS_CRAFTED.getOrElse(second[0], 0)
				+ "; site " + a.site().status() + " missing " + a.site().missing() + "; logs a player can reach " + reachable(helper, Items.OAK_LOG)
				+ ", barrels " + reachable(helper, Items.BARREL) + ", planks " + reachable(helper, Items.OAK_PLANKS) + "; his bag "
				+ ModAttachments.BUILDER_BAG.getOrCreate(first[0]).stacks() + ", job " + ModAttachments.CRAFTER_JOB.get(first[0]) + ")");
			helper.assertTrue(crafts || secondJobAt[0] < jobAt[0] + CLAIM_TICKS - 10, who + ": the request was only free again when the claim ran out ("
				+ (secondJobAt[0] - jobAt[0]) + " ticks after it was taken), so it was never let go");
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(second[0], 0) + ModAttachments.ITEMS_CRAFTED.getOrElse(first[0], 0);
			helper.assertTrue(made == 3, who + ": " + made + " barrels made in all, not 3");
			int logs = reachable(helper, Items.OAK_LOG);
			int spare = reachable(helper, Items.BARREL);
			helper.assertTrue(logs == 0 && spare == 0 && ModAttachments.BUILDER_BAG.getOrCreate(first[0]).isEmpty(), who
				+ ": after the build, logs left " + logs + ", spare barrels " + spare + ", his bag " + ModAttachments.BUILDER_BAG.getOrCreate(first[0]).stacks());
			if (next != null) {
				helper.assertTrue(first[0].getVillagerData().getProfession() == next, "the ex-carpenter should be a " + next + ", is "
					+ first[0].getVillagerData().getProfession());
			}
		});
	}
}
