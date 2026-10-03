package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

/**
 * QA lane (qa-1003-1933): B21, B22 and B27/B26 from their specs.
 * <ul>
 * <li>B22: "a GameTest building stone_house from exactly plan.materials() finishes without waiting" (the material list
 * a player is shown covers the whole build), alone and with a helper builder (B21's crew).</li>
 * <li>B21: "a builder whose missing list is empty fetches and builds; if it really waits, the missing list says for
 * what", checked on every tick of those builds.</li>
 * <li>B27's other side: a Hall assignment that succeeds still lets go of the old block; B26's: a block taken back by
 * hand is let go of again when the worker moves on.</li>
 * </ul>
 */
public class QaB21B27GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HELPER_BENCH = new BlockPos(7, 2, 2);
	private static final BlockPos ORIGIN = new BlockPos(12, 2, 12);

	//$ gametest_ticks_batch HUGE '40000' '"qaB22StoneHouseAlone"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaB22StoneHouseAlone")
	public void qaB22TheStoneHouseBuiltFromExactlyItsMaterialListNeverWaits(GameTestHelper helper) {
		Leftovers.clear(helper);
		buildStoneHouse(helper, false);
	}

	//$ gametest_ticks_batch HUGE '40000' '"qaB21StoneHouseCrew"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaB21StoneHouseCrew")
	public void qaB21TheStoneHouseWithAHelperFromExactlyItsMaterialListNeverWaits(GameTestHelper helper) {
		Leftovers.clear(helper);
		buildStoneHouse(helper, true);
	}

	private void buildStoneHouse(GameTestHelper helper, boolean withHelper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(withHelper, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, StarterBlueprints.STONE_HOUSE.id(), placement);
		helper.assertTrue(site != null, "the stone house didn't start");
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no plan for the stone house");
		}
		// Exactly the material list the player is shown, nothing more.
		Map<Item, Integer> materials = plan.materials();
		List<ItemStack> stock = new ArrayList<>();
		int total = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue();
			total += left;
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				stock.add(new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		BlockPos[] barrels = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4),
			new BlockPos(1, 2, 5), new BlockPos(3, 2, 5), new BlockPos(4, 2, 5), new BlockPos(5, 2, 5)};
		helper.assertTrue(stock.size() <= barrels.length * 27, "test needs more barrels: " + stock.size() + " stacks");
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		Villager mate = null;
		if (withHelper) {
			helper.setBlock(HELPER_BENCH, ModBlocks.BUILDERS_BENCH);
			mate = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 3));
			Builders.employ(level, mate, helper.absolutePos(HELPER_BENCH));
		}
		Villager crewmate = mate;
		int needed = total;
		String[] waited = {null};
		String[] emptyWait = {null};
		boolean[] helped = {false};
		helper.onEachTick(() -> {
			if (site.isDone()) {
				return;
			}
			if (crewmate != null && site.helpers(level.getGameTime()).contains(crewmate.getUUID())) {
				helped[0] = true;
			}
			if (site.status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
				if (site.missing().isEmpty() && emptyWait[0] == null) {
					emptyWait[0] = "stage " + site.stage() + " at tick " + level.getGameTime();
				}
				if (waited[0] == null) {
					waited[0] = "stage " + site.stage() + ", missing " + site.missing();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(emptyWait[0] == null, "B21: the builder waited for materials with nothing on its missing list ("
				+ emptyWait[0] + ")");
			helper.assertTrue(waited[0] == null, "B22: the stone house, given exactly its material list (" + needed
				+ " items), waited for materials: " + waited[0]);
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null || site.isDone(),
				"still building: stage=" + site.stage() + " status=" + site.status() + " missing=" + site.missing());
			helper.assertTrue(plan.unfinished(level).isEmpty(), "the stone house isn't finished: " + plan.unfinished(level).size()
				+ " blocks left");
			if (withHelper) {
				helper.assertTrue(helped[0], "setup: the second builder never helped");
			}
			helper.assertTrue(plan.size() > 300, "setup: the stone house plan has only " + plan.size() + " blocks");
			System.out.println("[QA] stone house (helper=" + withHelper + ") built " + plan.size() + " blocks from exactly "
				+ needed + " items in " + helper.getTick() + " ticks without waiting");
		});
	}

	/** B27's other side: when the Hall's assignment succeeds, the worker's old block is let go of (free for others). */
	//$ gametest_ticks_batch AREA '100' '"qaB27HallSucceeds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB27HallSucceeds")
	public void qaB27ASuccessfulHallAssignmentLetsGoOfTheOldBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lectern = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos composter = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.LECTERN);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.COMPOSTER);
		Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3));
		worker.setNoAi(true);
		Jobs.employ(level, worker, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "setup: the librarian doesn't hold the lectern");
		var farmer = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.FARMER);
		var offered = new VillageHalls.FreeStation(composter, VillagerProfession.FARMER, farmer);

		helper.assertTrue(VillageHalls.assign(level, worker, offered), "the Hall couldn't give the librarian a free composter");
		helper.assertTrue(StationsSpecGameTests.job(worker) == VillagerProfession.FARMER
				&& StationsSpecGameTests.site(worker).equals(Optional.of(composter)),
			"after the assignment the worker is a " + StationsSpecGameTests.name(StationsSpecGameTests.job(worker)) + " at "
				+ StationsSpecGameTests.site(worker));
		helper.assertTrue(level.getPoiManager().getFreeTickets(composter) == 0, "the new farmer doesn't hold the composter");
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 1,
			"the old lectern is still held (" + level.getPoiManager().getFreeTickets(lectern) + " free) after the librarian moved on");
		// And another villager can now be hired at the lectern.
		Villager next = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 5));
		next.setNoAi(true);
		var librarian = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.LIBRARIAN);
		helper.assertTrue(VillageHalls.assign(level, next, new VillageHalls.FreeStation(lectern, VillagerProfession.LIBRARIAN, librarian)),
			"the Hall couldn't give the freed lectern to another villager");
		helper.succeed();
	}

	/**
	 * B26's other side: a worker takes back their replaced composter by hand (wheat), then the Hall moves them to a
	 * lectern. The composter must be free again (the place B26 took is let go of, not leaked).
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaB26RetakenThenMoved"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaB26RetakenThenMoved")
	public void qaB26ABlockRetakenByHandIsLetGoOfWhenTheWorkerMoves(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		BlockPos spot = new BlockPos(3, 2, 3);
		BlockPos at = helper.absolutePos(spot);
		BlockPos lectern = helper.absolutePos(new BlockPos(9, 2, 9));
		helper.setBlock(spot, Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(9, 2, 9), Blocks.LECTERN);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		StationsSpecGameTests.rightClick(player, first, new ItemStack(Items.SWEET_BERRIES), true);
		helper.assertTrue(StationsSpecGameTests.job(first) == ModVillagers.ORCHARD_KEEPER, "setup: the first isn't an orchard keeper");
		BlockPos away = helper.absolutePos(new BlockPos(25, 2, 25));
		first.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		helper.setBlock(spot, Blocks.AIR);
		helper.runAfterDelay(5, () -> {
			helper.setBlock(spot, Blocks.COMPOSTER);
			helper.runAfterDelay(2, () -> {
				BlockPos back = helper.absolutePos(new BlockPos(4, 2, 4));
				first.teleportTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
				StationsSpecGameTests.rightClick(player, first, new ItemStack(Items.WHEAT), true);
				helper.assertTrue(StationsSpecGameTests.site(first).equals(Optional.of(at)), "setup: wheat didn't put the first at the composter");
				helper.assertTrue(level.getPoiManager().getFreeTickets(at) == 0, "setup (B26): the first doesn't hold the composter");
				first.setNoAi(true);
				var librarian = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.LIBRARIAN);
				helper.assertTrue(VillageHalls.assign(level, first, new VillageHalls.FreeStation(lectern, VillagerProfession.LIBRARIAN, librarian)),
					"the Hall couldn't move the worker to a free lectern");
				helper.assertTrue(level.getPoiManager().getFreeTickets(at) == 1,
					"the composter the worker left is still held (" + level.getPoiManager().getFreeTickets(at)
						+ " free): nobody can work there now");
				helper.succeed();
			});
		});
	}
}
