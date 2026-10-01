package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

/**
 * Tester, ROADMAP 21.1a round 3. Decision (1): our workers take a free block of their kind again by themselves, as
 * vanilla workers do; for beekeepers that's "the nearest beehive or bee nest nobody works at". Beehives and nests still
 * never take a jobless villager by themselves, and bees keep using them.
 */
public class StationsRetakeGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** A beekeeper made as the player makes one: sneak-right-clicked by {@code hive} holding a glass bottle. */
	private static Villager beekeeper(GameTestHelper helper, ServerPlayer player, BlockPos hive, BlockPos standing) {
		Villager v = helper.spawn(EntityType.VILLAGER, standing);
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.GLASS_BOTTLE), true);
		helper.assertTrue(StationsSpecGameTests.job(v) == ModVillagers.BEEKEEPER
				&& StationsSpecGameTests.site(v).equals(Optional.of(helper.absolutePos(hive))),
			"setup: a " + StationsSpecGameTests.name(StationsSpecGameTests.job(v)) + " at " + StationsSpecGameTests.site(v) + ", not a beekeeper at "
				+ helper.absolutePos(hive));
		return v;
	}

	private static String jobless(Villager v) {
		return StationsSpecGameTests.job(v) == VillagerProfession.NONE && StationsSpecGameTests.site(v).isEmpty() ? null
			: StationsSpecGameTests.name(StationsSpecGameTests.job(v)) + " at " + StationsSpecGameTests.site(v);
	}

	/**
	 * A beekeepers' yard is rebuilt: the player breaks all three beekeepers' hives and puts up two hives and a bee nest
	 * elsewhere in the yard. Each beekeeper takes one of the new ones by themselves, none shares, all stay beekeepers;
	 * a jobless villager standing among the new hives stays jobless.
	 */
	//$ gametest_ticks_batch HUGE '900' '"stationsRetakeYardRebuilt"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "stationsRetakeYardRebuilt")
	public void threeBeekeepersWhoseHivesMovedEachTakeANewOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		List<BlockPos> old = List.of(new BlockPos(3, 2, 3), new BlockPos(13, 2, 3), new BlockPos(23, 2, 3));
		List<Villager> keepers = new ArrayList<>();
		for (BlockPos hive : old) {
			helper.setBlock(hive, Blocks.BEEHIVE);
			keepers.add(beekeeper(helper, player, hive, hive.offset(1, 0, 1)));
		}
		for (BlockPos hive : old) {
			helper.setBlock(hive, Blocks.AIR);
		}
		List<BlockPos> fresh = List.of(new BlockPos(8, 2, 12), new BlockPos(13, 2, 14), new BlockPos(18, 2, 12));
		helper.setBlock(fresh.get(0), Blocks.BEEHIVE);
		helper.setBlock(fresh.get(1), Blocks.BEE_NEST);
		helper.setBlock(fresh.get(2), Blocks.BEEHIVE);
		Set<BlockPos> freshAbs = Set.of(helper.absolutePos(fresh.get(0)), helper.absolutePos(fresh.get(1)), helper.absolutePos(fresh.get(2)));
		Villager bystander = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 11));
		helper.runAfterDelay(700, () -> {
			List<Optional<BlockPos>> sites = keepers.stream().map(StationsSpecGameTests::site).toList();
			for (Villager k : keepers) {
				helper.assertTrue(StationsSpecGameTests.job(k) == ModVillagers.BEEKEEPER, "a beekeeper became a " + StationsSpecGameTests.name(StationsSpecGameTests.job(k)));
			}
			helper.assertTrue(sites.stream().allMatch(s -> s.isPresent() && freshAbs.contains(s.get())) && sites.stream().distinct().count() == 3,
				"each beekeeper should work at one of the new hives " + freshAbs + ", alone: " + sites);
			String b = jobless(bystander);
			helper.assertTrue(b == null, "the jobless villager among the new hives is now a " + b);
			helper.succeed();
		});
	}

	/**
	 * Two beekeepers, one of whose hive is broken; the only hive left near is the other's, who is close by. The one
	 * without a hive doesn't take it (they stay a beekeeper, waiting for a free one); the other keeps it; a jobless
	 * villager by it stays jobless.
	 */
	//$ gametest_ticks_batch HUGE '700' '"stationsRetakeNoSteal"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "stationsRetakeNoSteal")
	public void aBeekeeperWithoutAHiveLeavesAWorkedOneAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		BlockPos kept = new BlockPos(3, 2, 3);
		BlockPos broken = new BlockPos(15, 2, 3);
		helper.setBlock(kept, Blocks.BEEHIVE);
		helper.setBlock(broken, Blocks.BEEHIVE);
		Villager owner = beekeeper(helper, player, kept, new BlockPos(4, 2, 4));
		Villager loser = beekeeper(helper, player, broken, new BlockPos(16, 2, 4));
		Villager bystander = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 6));
		helper.setBlock(broken, Blocks.AIR);
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(StationsSpecGameTests.job(owner) == ModVillagers.BEEKEEPER
					&& StationsSpecGameTests.site(owner).equals(Optional.of(helper.absolutePos(kept))),
				"the beekeeper who kept their hive is a " + StationsSpecGameTests.name(StationsSpecGameTests.job(owner)) + " at " + StationsSpecGameTests.site(owner));
			helper.assertTrue(StationsSpecGameTests.site(loser).isEmpty(),
				"the beekeeper whose hive broke took " + StationsSpecGameTests.site(loser) + " (the other's hive is " + helper.absolutePos(kept) + ")");
			helper.assertTrue(StationsSpecGameTests.job(loser) == ModVillagers.BEEKEEPER,
				"the beekeeper whose hive broke is now a " + StationsSpecGameTests.name(StationsSpecGameTests.job(loser)));
			String b = jobless(bystander);
			helper.assertTrue(b == null, "the jobless villager by the hive is now a " + b);
			helper.succeed();
		});
	}

	/**
	 * At night, a beekeeper whose hive was broken (penned in, so where they stand is fixed) takes the nearer of two free
	 * ones, a bee nest 4 blocks away rather than a beehive 14 away; a bee looking for a home at night still finds that
	 * nest and goes in, although a beekeeper works at it.
	 */
	//$ gametest_ticks_batch HUGE '1400' '"stationsRetakeNight"'
	@GameTest(template = HUGE, timeoutTicks = 1400, batch = "stationsRetakeNight")
	public void atNightABeekeeperTakesTheNearestFreeNestAndBeesStillUseIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(18000);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		BlockPos centre = new BlockPos(5, 2, 5);
		BlockPos first = new BlockPos(5, 2, 6);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p = centre.offset(dx, 0, dz);
				if (!p.equals(centre)) {
					helper.setBlock(p, p.equals(first) ? Blocks.BEEHIVE : Blocks.OAK_FENCE);
				}
			}
		}
		Villager keeper = beekeeper(helper, player, first, centre);
		helper.setBlock(first, Blocks.OAK_FENCE);
		BlockPos nest = new BlockPos(5, 2, 9);
		BlockPos far = new BlockPos(5, 2, 19);
		helper.setBlock(nest, Blocks.BEE_NEST);
		helper.setBlock(far, Blocks.BEEHIVE);
		Bee bee = helper.spawn(EntityType.BEE, new BlockPos(8, 3, 10));
		helper.succeedWhen(() -> {
			helper.assertTrue(StationsSpecGameTests.job(keeper) == ModVillagers.BEEKEEPER
					&& StationsSpecGameTests.site(keeper).equals(Optional.of(helper.absolutePos(nest))),
				"the beekeeper works at " + StationsSpecGameTests.site(keeper) + " as " + StationsSpecGameTests.name(StationsSpecGameTests.job(keeper))
					+ "; the nest is " + helper.absolutePos(nest) + ", the far hive " + helper.absolutePos(far));
			BeehiveBlockEntity home = helper.getBlockEntity(nest);
			helper.assertTrue(home.getOccupantCount() >= 1, "the bee isn't in the nest (bee at " + bee.blockPosition() + ", removed " + bee.isRemoved()
				+ ", hive " + bee.getHivePos() + ")");
		});
	}

	/**
	 * Only beekeepers take a hive by themselves. A free beehive and a free bee nest, and by them a jobless villager and a
	 * farmer whose composter was broken (he keeps his job): after a while neither works at a hive (not even as an
	 * invisible job site, which would make the hive "taken"), the jobless one is still jobless, and the player can still
	 * make them a beekeeper there.
	 */
	//$ gametest_ticks_batch AREA '600' '"stationsRetakeOnlyBeekeepers"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "stationsRetakeOnlyBeekeepers")
	public void onlyBeekeepersTakeAFreeHiveByThemselves(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos hive = new BlockPos(3, 2, 3);
		BlockPos nest = new BlockPos(7, 2, 3);
		BlockPos composter = new BlockPos(5, 2, 8);
		helper.setBlock(hive, Blocks.BEEHIVE);
		helper.setBlock(nest, Blocks.BEE_NEST);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 4));
		Jobs.employ(helper.getLevel(), farmer, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
		helper.setBlock(composter, Blocks.AIR);
		Set<BlockPos> hives = Set.of(helper.absolutePos(hive), helper.absolutePos(nest));
		helper.runAfterDelay(400, () -> {
			String j = jobless(jobless);
			helper.assertTrue(j == null, "the jobless villager by the hives is now a " + j);
			helper.assertTrue(StationsSpecGameTests.job(farmer) == VillagerProfession.FARMER
					&& StationsSpecGameTests.site(farmer).filter(hives::contains).isEmpty(),
				"the farmer without a composter is a " + StationsSpecGameTests.name(StationsSpecGameTests.job(farmer)) + " at " + StationsSpecGameTests.site(farmer));
			BlockPos by = helper.absolutePos(new BlockPos(4, 2, 4));
			jobless.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5); // back by the hives (they wander)
			StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), jobless, new ItemStack(Items.GLASS_BOTTLE), true);
			helper.assertTrue(StationsSpecGameTests.job(jobless) == ModVillagers.BEEKEEPER
					&& StationsSpecGameTests.site(jobless).filter(hives::contains).isPresent(),
				"the jobless villager couldn't be made a beekeeper by the free hives: " + StationsSpecGameTests.name(StationsSpecGameTests.job(jobless))
					+ " at " + StationsSpecGameTests.site(jobless));
			helper.succeed();
		});
	}

	/**
	 * The player is told why: "The Orchard Keeper job needs a free Composter: someone already works at each one here."
	 * when the composter in reach is the farmer's; with the only (taken) composter just out of reach (5 blocks), the old "stand them next
	 * to one" instead.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theTakenMessageOnlyWhenATakenBlockIsInReach(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		ServerPlayer player = StationsSpecGameTests.listeningPlayer(helper, seen);
		BlockPos composter = new BlockPos(3, 2, 3);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 3));
		Jobs.employ(helper.getLevel(), farmer, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
		Villager near = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		StationsSpecGameTests.rightClick(player, near, new ItemStack(Items.SWEET_BERRIES), true);
		helper.assertTrue(seen.contains("The Orchard Keeper job needs a free Composter: someone already works at each one here."),
			"by the farmer's composter; saw: " + seen);
		helper.assertTrue(StationsSpecGameTests.job(near) == VillagerProfession.NONE, "the villager by the farmer's composter became a "
			+ StationsSpecGameTests.name(StationsSpecGameTests.job(near)));
		seen.clear();
		Villager far = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 3)); // 5 blocks: just out of reach (4.5)
		StationsSpecGameTests.rightClick(player, far, new ItemStack(Items.SWEET_BERRIES), true);
		helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("The Orchard Keeper job needs a Composter: stand them next to one")),
			"5 blocks from the only (taken) composter, just out of reach; saw: " + seen);
		helper.succeed();
	}
}
