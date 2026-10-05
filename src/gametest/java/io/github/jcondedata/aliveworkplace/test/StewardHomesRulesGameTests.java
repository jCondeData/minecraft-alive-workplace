package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * ROADMAP 27.10: the shipped rules for homes and storage. Each in a village staged to need it: the Steward wishes for
 * that build in the right zone, and with beds short and an upgradable Stone House the upgrade comes before a new house.
 */
public class StewardHomesRulesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static BlockPos hall(GameTestHelper helper, VillageRanks.Rank rank) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		// The staged village (beds, villagers, a rank) is well kept: gone with the test, so no later batch's villagers nearby
		// count as its people and get its pace.
		Leftovers.after(helper, () -> helper.getLevel().removeBlock(hall, false));
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setRank(rank);
		return hall;
	}

	private static void villagers(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3 + 2 * i, 2, 20));
			v.setNoAi(true);
		}
	}

	private static void bed(GameTestHelper helper, int x, int z) {
		helper.setBlock(new BlockPos(x, 2, z), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(x, 2, z + 1), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	/** A finished building of the village (forgotten after the test). */
	private static void built(GameTestHelper helper, String blueprint, int x) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(),
			helper.absolutePos(new BlockPos(x, 2, 2)), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		sites.recordFinished(AliveWorkplace.id(blueprint), placement, UUID.randomUUID());
	}

	private static List<StewardWishes.Wish> wishes(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		return StewardWishes.rank(StewardRules.all(), StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, StewardWishes.day(level));
	}

	private static List<String> ids(List<StewardWishes.Wish> wishes) {
		return wishes.stream().map(w -> w.rule().getPath()).toList();
	}

	/** The wish of that rule builds that blueprint in that kind of zone. */
	private static StewardWishes.Wish builds(GameTestHelper helper, List<StewardWishes.Wish> wishes, String rule, String blueprint, String zone) {
		Optional<StewardWishes.Wish> wish = wishes.stream().filter(w -> w.rule().getPath().equals(rule)).findFirst();
		helper.assertTrue(wish.isPresent(), "no " + rule + " wish: " + ids(wishes));
		StewardRules.Effect e = wish.get().effect();
		helper.assertTrue(e.kind() == StewardRules.Kind.BUILD && e.blueprint().equals(Optional.of(AliveWorkplace.id(blueprint)))
			&& e.zone().equals(Optional.of(zone)), rule + " doesn't build a " + blueprint + " in " + zone + ": " + e);
		helper.assertTrue(StewardWishes.plotFor(wish.get()).map(r -> r.zoneKind().equals(zone)).orElse(false), rule + ": no plot asked in " + zone);
		return wish.get();
	}

	private static void absent(GameTestHelper helper, List<StewardWishes.Wish> wishes, String... rules) {
		for (String rule : rules) {
			helper.assertTrue(!ids(wishes).contains(rule), rule + " shouldn't hold: " + ids(wishes));
		}
	}

	//$ gametest_ticks_batch AREA '40' '"stewardHomesCottage"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardHomesCottage")
	public void aHamletOneBedShortGetsAStarterCottage(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 1);
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		builds(helper, wishes, "homes_starter_cottage", "starter_cottage", "homes");
		absent(helper, wishes, "homes_stone_house", "homes_terrace", "homes_inn", "homes_upgrade");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardHomesStone"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardHomesStone")
	public void twoBedsShortGetAStoneHouse(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 2);
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		builds(helper, wishes, "homes_stone_house", "stone_house", "homes");
		absent(helper, wishes, "homes_starter_cottage", "homes_terrace", "homes_inn");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardHomesTerrace"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardHomesTerrace")
	public void aVillageThreeBedsShortGetsATerraceFirst(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.VILLAGE);
		villagers(helper, 3);
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		builds(helper, wishes, "homes_terrace", "terrace", "homes");
		helper.assertTrue(ids(wishes).indexOf("homes_terrace") < ids(wishes).indexOf("homes_stone_house"), "the terrace isn't first: " + ids(wishes));
		absent(helper, wishes, "homes_inn");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardHomesInn"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardHomesInn")
	public void aTownFourBedsShortGetsAnInnInTheMarket(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.TOWN);
		villagers(helper, 4);
		builds(helper, wishes(helper, hall), "homes_inn", "inn", "market");
		built(helper, "inn", 2);
		absent(helper, wishes(helper, hall), "homes_inn");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardHomesUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardHomesUpgrade")
	public void bedsShortUpgradeAStoneHouseBeforeANewHouse(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 2);
		bed(helper, 20, 20);
		bed(helper, 22, 20);
		villagers(helper, 2); // 4 villagers, 2 beds: two short
		built(helper, "stone_house", 2);
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		Optional<StewardWishes.Wish> up = wishes.stream().filter(w -> w.rule().getPath().equals("homes_upgrade")).findFirst();
		helper.assertTrue(up.isPresent() && up.get().effect().kind() == StewardRules.Kind.UPGRADE && up.get().effect().addsBeds(),
			"no upgrade of a home: " + ids(wishes));
		absent(helper, wishes, "homes_starter_cottage", "homes_stone_house", "homes_terrace", "homes_inn");
		// Stone House II has 3 beds to I's 2: the beds come from the blueprints
		ServerLevel level = helper.getLevel();
		helper.assertTrue(StewardConditions.beds(level, AliveWorkplace.id("stone_house")) == 2
			&& StewardConditions.beds(level, AliveWorkplace.id("stone_house_2")) == 3, "the blueprints' beds aren't counted");
		// at the top tier there's nothing to upgrade: a new house again
		built(helper, "stone_house_3", 2);
		wishes = wishes(helper, hall);
		absent(helper, wishes, "homes_upgrade");
		builds(helper, wishes, "homes_stone_house", "stone_house", "homes");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardHomesCottageTop"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardHomesCottageTop")
	public void anUpgradeWithNoMoreBedsIsNoAnswerToBedsShort(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 1);
		built(helper, "starter_cottage_2", 2); // III has the same 2 beds as II
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		absent(helper, wishes, "homes_upgrade");
		builds(helper, wishes, "homes_starter_cottage", "starter_cottage", "homes");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardHomesBetter"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardHomesBetter")
	public void noBedsShortButPlainHomesGetUpgraded(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		for (int i = 0; i < 3; i++) {
			bed(helper, 18 + 2 * i, 20);
		}
		villagers(helper, 3);
		built(helper, "stone_house", 2);
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		Optional<StewardWishes.Wish> better = wishes.stream().filter(w -> w.rule().getPath().equals("homes_better")).findFirst();
		helper.assertTrue(better.isPresent() && better.get().effect().kind() == StewardRules.Kind.UPGRADE, "no better homes: " + ids(wishes));
		absent(helper, wishes, "homes_upgrade", "homes_stone_house", "homes_starter_cottage");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardStorehouse"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardStorehouse")
	public void threeVillagersAndNoStorehouseGetOneInTheMarket(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 3);
		builds(helper, wishes(helper, hall), "storehouse", "storehouse", "market");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardStorehouseGrow"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardStorehouseGrow")
	public void aStorehouseEightyPercentFullGrows(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 1);
		bed(helper, 20, 20);
		helper.setBlock(new BlockPos(16, 2, 4), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(17, 2, 4), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(17, 2, 4)));
		built(helper, "storehouse", 2);
		absent(helper, wishes(helper, hall), "storehouse_grow", "storehouse");
		for (int i = 0; i < 22; i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64)); // 22 of 27: 81%
		}
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		Optional<StewardWishes.Wish> grow = wishes.stream().filter(w -> w.rule().getPath().equals("storehouse_grow")).findFirst();
		helper.assertTrue(grow.isPresent() && grow.get().effect().kind() == StewardRules.Kind.UPGRADE
			&& grow.get().effect().blueprint().equals(Optional.of(AliveWorkplace.id("storehouse"))), "the storehouse doesn't grow: " + ids(wishes));
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardMarketStall"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardMarketStall")
	public void aVillageWithNoStallGetsOneInTheMarket(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 1);
		bed(helper, 20, 20);
		absent(helper, wishes(helper, hall), "market_stall");
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setRank(VillageRanks.Rank.VILLAGE);
		builds(helper, wishes(helper, hall), "market_stall", "market_stall", "market");
		built(helper, "market_stall_2", 2);
		absent(helper, wishes(helper, hall), "market_stall");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardBerryFarm"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardBerryFarm")
	public void foodShortGetsABerryFarmInTheFarms(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 1);
		bed(helper, 20, 20);
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		builds(helper, wishes, "food_berry_farm", "berry_farm", "farms");
		absent(helper, wishes, "food_ranch");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRanch"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRanch")
	public void aVillageShortOfFoodWithABerryFarmGetsARanch(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.VILLAGE);
		villagers(helper, 1);
		bed(helper, 20, 20);
		built(helper, "berry_farm", 2);
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		builds(helper, wishes, "food_ranch", "ranch", "farms");
		absent(helper, wishes, "food_berry_farm");
		helper.succeed();
	}
}
