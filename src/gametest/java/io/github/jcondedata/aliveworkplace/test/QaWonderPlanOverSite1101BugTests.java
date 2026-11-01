package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlans;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1101-0038), the Wonder Plan's ground (35.3: never over a building; B714: "like any build in progress"): a
 * Wonder Plan goes down over a house that is being built. WonderPlans.placeRefusal looks at finished buildings, at
 * Wonder build sites and (B714) at laid-out Wonders, but not at an ordinary build site, so the Wonder's stage I is laid
 * over a half-built house and two builders work the same ground. The player's own blueprint is refused there
 * ("overlaps another build in progress").
 */
public class QaWonderPlanOverSite1101BugTests {
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}

	private static String refusal(ServerLevel level, ItemStack plan, BlockPos at) {
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), size, at, Rotation.NONE);
		return WonderPlans.placeRefusal(level, WonderPlanItem.plan(plan).get(), placement, size)
			.map(c -> (c.getContents() instanceof TranslatableContents t ? t.getKey() : "") + " (" + c.getString() + ")").orElse("none");
	}

	/**
	 * A Town with every requirement of the test folly met. Control: the plan may go down on open ground 24 blocks out.
	 * Then the village's builder starts a hut there (a build site, nothing finished yet). Expected: the same plan is
	 * refused, as over a finished building ("It would stand over ..."), not laid over the hut going up.
	 */
	//$ gametest_batch SMALL '"qaWonderPlanOverSite1101"'
	@GameTest(template = SMALL, batch = "qaWonderPlanOverSite1101")
	public void qaAPlanIsRefusedOverAHouseBeingBuilt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean legends = Wonders.LEGENDS;
		Wonders.LEGENDS = false;
		Leftovers.after(helper, () -> Wonders.LEGENDS = legends);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.TOWN);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.wonderGround(helper, hall);
		BlockPos tableAt = new BlockPos(3, 2, 2);
		helper.setBlock(tableAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = villager(helper, new BlockPos(3, 2, 3), ModVillagers.BUILDER, 5);
		Builders.employ(level, builder, helper.absolutePos(tableAt));
		villager(helper, new BlockPos(13, 2, 3), VillagerProfession.FARMER, 1);
		villager(helper, new BlockPos(8, 2, 13), VillagerProfession.FARMER, 1);
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());

		ItemStack plan = WonderPlanItem.of(level, hall, folly, Component.literal("Testholm"));
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlockPos origin = hall.offset(-24, 0, -24);
		BlockPos centre = origin.offset(size.getX() / 2, 0, size.getZ() / 2);
		helper.assertTrue(refusal(level, plan, centre).equals("none"), "set-up: the open ground isn't free: " + refusal(level, plan, centre));

		BlueprintData.Placement hut = new BlueprintData.Placement(level.dimension().location(), centre.offset(-2, 0, -2), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, player.getUUID(), "Owner", HUT, hut);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		helper.assertTrue(BuildSiteManager.get(level).get(site.id()) != null && !site.isDone(), "set-up: the hut isn't going up");

		String over = refusal(level, plan, centre);
		helper.assertTrue(!over.equals("none"), "the Wonder Plan may go down over the Test Hut that is being built there (a build site at "
			+ hut.origin().subtract(hall).toShortString() + " from the hall); expected refused like over a building");
		helper.succeed();
	}
}
