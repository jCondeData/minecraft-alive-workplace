package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.UUID;

/**
 * QA (lane C, 2026-11-04, second run) for the villager and job fixes B147, B201, B222 and B224, from what each report expected
 * a player to see, through the game's own tick, on the far side of what the fixes' tests cover:
 * <ul>
 *   <li>B147: cooked fish waiting while the barrel is brim-full is never dropped or lost, and is fetched once room frees;</li>
 *   <li>B201: the block a jobless villager was walking to, let go when they're given another, is really taken by the next
 *   jobless villager (the report's "drops out of the free lists");</li>
 *   <li>B224: a pit of plain stone ends up as cobblestone in the chest, topped onto the stack already there, with nothing
 *   left lying on the ground;</li>
 *   <li>B222: with something to revive with in the chest, a street grave is brought back where it lies and never laid in
 *   the Graveyard first (graveyards.md: "Reviving comes first").</li>
 * </ul>
 */
public class QaVillagerFixes1104c2GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String BUILD = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- B147 ----

	private static final BlockPos BARREL = new BlockPos(2, 2, 2);
	private static final BlockPos FURNACE = new BlockPos(2, 2, 4);

	private static long groundItems(GameTestHelper helper, Item item) {
		long n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/**
	 * B147 (the fisherman "collects his smelted output and stores it like his catch"): five cooked cod done in the furnace
	 * while his barrel is full to the brim with cobblestone. For half a minute, nowhere to put them: no cooked cod may land
	 * on the ground and none may vanish (furnace, barrel and his hands together hold five every tick). A player then takes
	 * one stack out of the barrel: he fetches the five and they are in the barrel, the furnace's output empty.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qa1104c2FullBarrel"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qa1104c2FullBarrel")
	public void qaCookedFishWaitsOutAFullBarrelAndIsFetchedWhenRoomFrees(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(FURNACE, Blocks.FURNACE);
		Container barrel = helper.getBlockEntity(BARREL);
		for (int i = 0; i < barrel.getContainerSize(); i++) {
			barrel.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(FURNACE);
		furnace.setItem(2, new ItemStack(Items.COOKED_COD, 5));
		Villager fisher = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		Jobs.employ(level, fisher, helper.absolutePos(BARREL), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		Fishers.start(level, fisher, new ItemStack(Items.FISHING_ROD));
		boolean[] freed = {false};
		helper.onEachTick(() -> {
			long onGround = groundItems(helper, Items.COOKED_COD);
			helper.assertTrue(onGround == 0, onGround + " cooked cod dropped on the ground");
			long held = barrel.countItem(Items.COOKED_COD) + furnace.getItem(2).getCount() + fisher.getInventory().countItem(Items.COOKED_COD)
				+ (fisher.getMainHandItem().is(Items.COOKED_COD) ? fisher.getMainHandItem().getCount() : 0);
			if (!freed[0]) {
				// while the barrel is full the cod can only be in the furnace or with him
				helper.assertTrue(held == 5, "cooked cod went missing while the barrel was full: " + held + " of 5");
				helper.assertTrue(barrel.countItem(Items.COBBLESTONE) == 64L * barrel.getContainerSize(), "the full barrel's cobblestone was moved");
			}
		});
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(barrel.countItem(Items.COOKED_COD) == 0, "cooked cod in a barrel with no room");
			barrel.setItem(13, ItemStack.EMPTY); // the player takes a stack out
			freed[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(freed[0], "the barrel is still full");
			helper.assertTrue(barrel.countItem(Items.COOKED_COD) == 5, barrel.countItem(Items.COOKED_COD) + " cooked cod in the barrel, expected 5");
			helper.assertTrue(furnace.getItem(2).isEmpty(), "still in the furnace: " + furnace.getItem(2));
		});
	}

	// ---- B201 ----

	private static final BlockPos LECTERN = new BlockPos(3, 2, 3);
	private static final BlockPos COMPOSTER = new BlockPos(6, 2, 3);

	/**
	 * B201 ("that block stays held for nobody and drops out of the free lists"; wiki: "lets it go, so another villager can
	 * take it"): a jobless villager on the way to a lectern is made the Orchard Keeper with sweet berries; a second jobless
	 * villager arriving afterwards then takes the lectern by himself, through vanilla's own job search, and the Orchard
	 * Keeper keeps their composter.
	 */
	//$ gametest_ticks_batch BIG '1600' '"qa1104c2LetGoTaken"'
	@GameTest(template = BIG, timeoutTicks = 1600, batch = "qa1104c2LetGoTaken")
	public void qaTheBlockLetGoIsTakenByTheNextJoblessVillager(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		PoiManager poi = level.getPoiManager();
		helper.setBlock(LECTERN, Blocks.LECTERN);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		BlockPos lectern = helper.absolutePos(LECTERN);
		BlockPos composter = helper.absolutePos(COMPOSTER);
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 5));
		Villager[] next = {null};
		helper.runAfterDelay(2, () -> {
			// what vanilla's AcquirePoi does when a jobless villager picks a block to walk to
			keeper.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
			helper.assertTrue(poi.take(h -> true, (h, p) -> p.equals(lectern), lectern, 1).isPresent(), "setup: the lectern couldn't be held");
			keeper.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), lectern));
			StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), keeper, new ItemStack(Items.SWEET_BERRIES), true);
			helper.assertTrue(keeper.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER, "setup: not made the Orchard Keeper: "
				+ keeper.getVillagerData().getProfession());
			next[0] = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 6));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(next[0] != null, "the second villager isn't here yet");
			GlobalPos site = next[0].getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
			helper.assertTrue(site != null && site.pos().equals(lectern), "the second jobless villager hasn't taken the lectern that was let go (job site "
				+ site + ", walking to " + next[0].getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE) + ", lectern's free places "
				+ poi.getFreeTickets(lectern) + ")");
			helper.assertTrue(next[0].getVillagerData().getProfession() != VillagerProfession.NONE, "took the lectern but has no job");
			helper.assertTrue(keeper.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER
				&& composter.equals(keeper.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
				"the Orchard Keeper lost their composter: " + keeper.getVillagerData().getProfession());
		});
	}

	// ---- B224 ----

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos CHEST2 = new BlockPos(1, 2, 4);
	private static final BlockPos MIN = new BlockPos(6, 2, 6);
	private static final BlockPos MAX = new BlockPos(9, 4, 9);

	/**
	 * B224 ("a Miner mines a pit of stone: the chest holds the cobblestone"): a 4 x 3 x 4 pit of plain stone, 48 blocks,
	 * the chest already holding a part stack of 10 cobblestone and the pickaxe. At the end the chests hold 58
	 * cobblestone (the 48 dug on top of the 10), no stone or cobblestone lies on the ground, and the pit is empty.
	 */
	//$ gametest_ticks_batch BUILD '4000' '"qa1104c2StonePit"'
	@GameTest(template = BUILD, timeoutTicks = 4000, batch = "qa1104c2StonePit")
	public void qaAPitOfStoneEndsUpAsCobblestoneOnTheChestsStack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		for (BlockPos p : BlockPos.betweenClosed(MIN, MAX)) {
			helper.setBlock(p, Blocks.STONE);
		}
		helper.setBlock(BENCH, Blocks.BLAST_FURNACE);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(CHEST2, Blocks.BARREL);
		Container chest = helper.getBlockEntity(CHEST);
		Container barrel = helper.getBlockEntity(CHEST2);
		chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
		chest.setItem(5, new ItemStack(Items.COBBLESTONE, 10));
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Miners.employ(level, miner, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_BAG.getOrCreate(miner).setSlots(BuilderBag.SLOTS);
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(MIN), helper.absolutePos(MAX));
		QuarrySite site = Miners.start(level, miner, null, box, box.getYSpan());
		site.setStairs(false);
		helper.succeedWhen(() -> {
			helper.assertTrue(QuarrySiteManager.get(level).get(site.id()) == null, "still digging: " + Math.round(site.progress() * 100) + "% (" + site.status() + ")");
			long cobble = chest.countItem(Items.COBBLESTONE) + barrel.countItem(Items.COBBLESTONE);
			helper.assertTrue(cobble == 58, "the chests hold " + cobble + " cobblestone, expected the 10 there plus 48 dug");
			helper.assertTrue(chest.countItem(Items.STONE) + barrel.countItem(Items.STONE) == 0, "smooth stone in the chests: dug stone should drop cobblestone");
			long onGround = groundItems(helper, Items.COBBLESTONE) + groundItems(helper, Items.STONE);
			helper.assertTrue(onGround == 0, onGround + " stone or cobblestone lying on the ground");
			for (BlockPos p : BlockPos.betweenClosed(MIN, MAX)) {
				helper.assertTrue(!helper.getBlockState(p).is(Blocks.STONE), "stone left in the pit at " + p);
			}
		});
	}

	// ---- B222 ----

	/**
	 * B222 ("revival still working"; graveyards.md: "Reviving comes first. When it can't revive (nothing in the chest), it
	 * takes the nearest grave ... and moves it"): Ada dies in the street of a village with a Graveyard and the Undertaker's
	 * chest already holds a golden apple. She is brought back (name kept), the apple used, and her grave never reaches the
	 * Graveyard's first row slot on the way: no grave is left anywhere.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1104c2ReviveFirst"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1104c2ReviveFirst")
	public void qaWithAnAppleInTheChestAStreetGraveIsRevivedNotMoved(GameTestHelper helper) {
		QaVillageReachGraves1101GameTests.graveVillage(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos street = QaVillageReachGraves1101GameTests.dies(helper, new BlockPos(4, 2, 9), "Ada", 0);
		UUID ada = ((GraveBlockEntity) level.getBlockEntity(street)).villager().getUUID("UUID");
		QaVillageReachGraves1101GameTests.undertaker(helper);
		Container chest = helper.getBlockEntity(new BlockPos(10, 2, 8));
		chest.setItem(0, new ItemStack(Items.GOLDEN_APPLE));
		BlockPos first = QaVillageReachGraves1101GameTests.slot(helper, 1);
		helper.onEachTick(() -> helper.assertTrue(!level.getBlockState(first).is(ModBlocks.GRAVE),
			"the grave was laid in the Graveyard though a golden apple was waiting in the chest"));
		helper.succeedWhen(() -> {
			Villager back = level.getEntity(ada) instanceof Villager v ? v : null;
			helper.assertTrue(back != null && back.isAlive(), "Ada isn't back");
			helper.assertTrue(back.getCustomName() != null && back.getCustomName().getString().equals("Ada"), "Ada's name is lost");
			helper.assertTrue(chest.countItem(Items.GOLDEN_APPLE) == 0, "the golden apple wasn't used");
			int left = Graves.near(level, helper.absolutePos(new BlockPos(15, 2, 15)), 30).size();
			helper.assertTrue(left == 0, left + " graves left");
		});
	}
}
