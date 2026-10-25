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
 * QA (lane-c-1025-0953) on 27.10's homes and storage rules, from its text, on the sides StewardHomesRulesGameTests
 * doesn't stage: "homes_inn (4 short, a Town, no Inn yet)" is neither 3 short in a Town nor 4 short in a Village;
 * "storehouse_grow (its chests 80% full: Storehouse II, then III)" is not at 21 of 27 slots (78%), is at 22 (81%), still
 * is with Storehouse II built, and no longer is with Storehouse III. Each rule is ranked on its own, so the village's
 * other wishes can't crowd it out of the morning's eight.
 */
public class QaStewardHomesBoundariesGameTests {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static BlockPos hall(GameTestHelper helper, VillageRanks.Rank rank) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		Leftovers.finished(helper); // an earlier batch's finished buildings nearby would be this village's to upgrade
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
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

	private static void built(GameTestHelper helper, String blueprint, int x) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(),
			helper.absolutePos(new BlockPos(x, 2, 2)), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		sites.recordFinished(AliveWorkplace.id(blueprint), placement, UUID.randomUUID());
	}

	/** The wishes of the one shipped rule {@code rule}, ranked this morning. */
	private static List<StewardWishes.Wish> wishes(GameTestHelper helper, BlockPos hall, String rule) {
		ServerLevel level = helper.getLevel();
		List<StewardRules.Rule> ours = StewardRules.all().stream().filter(r -> r.id().getPath().equals(rule)).toList();
		helper.assertTrue(ours.size() == 1, "setup: the shipped rule " + rule + " isn't loaded");
		return StewardWishes.rank(ours, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, StewardWishes.day(level));
	}

	private static String facts(GameTestHelper helper, BlockPos hall, String json) {
		StewardRules.Rule rule = StewardRules.read(AliveWorkplace.id("test/qa_homes"), com.google.gson.JsonParser.parseString(
			"{\"when\": [" + json + "], \"do\": {\"assign_jobs\": {}}, \"priority\": 1, \"why\": \"x\"}"));
		StewardConditions.Check c = rule.when().get(0).test(StewardConditions.Facts.of(helper.getLevel(), hall));
		return c.held() + " " + c.value() + " (" + c.shown().getString() + ")";
	}

	/** A Town three beds short and a Village four short get no Inn; the Town four short does. */
	//$ gametest_ticks_batch AREA '40' '"qaHomesInnEdges"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaHomesInnEdges")
	public void theInnWaitsForATownAndFourBedsShort(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.TOWN);
		villagers(helper, 3);
		helper.assertTrue(wishes(helper, hall, "homes_inn").isEmpty(), "a Town three beds short wished for an Inn ("
			+ facts(helper, hall, "{\"beds_short\": {\"at_least\": 4}}") + ")");
		villagers(helper, 1);
		helper.assertTrue(!wishes(helper, hall, "homes_inn").isEmpty(), "a Town four beds short wished for no Inn ("
			+ facts(helper, hall, "{\"beds_short\": {\"at_least\": 4}}") + ")");
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setRank(VillageRanks.Rank.VILLAGE);
		helper.assertTrue(wishes(helper, hall, "homes_inn").isEmpty(), "a Village four beds short wished for an Inn");
		helper.succeed();
	}

	/**
	 * The Storehouse's chest 21 of 27 slots full: no growing; 22: Storehouse II wished for; with II built, at 22 still (to
	 * III); with III built, none (there is no IV).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaHomesStoreEdges"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaHomesStoreEdges")
	public void theStorehouseGrowsFromEightyPercentToIIIAndNoFurther(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 1);
		bed(helper, 20, 20);
		helper.setBlock(new BlockPos(16, 2, 4), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(17, 2, 4), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(17, 2, 4)));
		built(helper, "storehouse", 2);
		for (int i = 0; i < 21; i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		String full = "{\"store_full\": {\"share\": 0.8}}";
		helper.assertTrue(wishes(helper, hall, "storehouse_grow").isEmpty(), "21 of 27 slots grew the storehouse (" + facts(helper, hall, full) + ")");
		chest.setItem(21, new ItemStack(Items.COBBLESTONE, 1)); // a slot used is a slot used, full stack or not
		Optional<StewardWishes.Wish> grow = wishes(helper, hall, "storehouse_grow").stream().findFirst();
		helper.assertTrue(grow.isPresent() && grow.get().effect().kind() == StewardRules.Kind.UPGRADE, "22 of 27 slots: no growing (" + facts(helper, hall, full) + ")");
		built(helper, "storehouse_2", 2); // the upgrade, where the first one stood
		helper.assertTrue(!wishes(helper, hall, "storehouse_grow").isEmpty(), "with Storehouse II the full store doesn't grow on to III ("
			+ facts(helper, hall, "{\"upgrade_available\": {\"blueprint\": \"aliveworkplace:storehouse\"}}") + ")");
		built(helper, "storehouse_3", 2);
		helper.assertTrue(wishes(helper, hall, "storehouse_grow").isEmpty(), "with Storehouse III it still wants to grow ("
			+ facts(helper, hall, "{\"upgrade_available\": {\"blueprint\": \"aliveworkplace:storehouse\"}}") + ")");
		helper.succeed();
	}
}
