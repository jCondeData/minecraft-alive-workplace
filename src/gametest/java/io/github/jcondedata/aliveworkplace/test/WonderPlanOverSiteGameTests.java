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
 * B748: a Wonder Plan is refused over an ordinary house being built, saying which build stands there, and the ground is
 * free again once that build is canceled (finished, it's refused as a finished building, 35.3).
 */
public class WonderPlanOverSiteGameTests {
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	private record Town(ServerLevel level, ServerPlayer player, Villager builder, ItemStack plan, BlockPos centre) {
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}

	/** The refusal's key and words, or "none". */
	private static String refusal(ServerLevel level, ItemStack plan, BlockPos at) {
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), size, at, Rotation.NONE);
		return WonderPlans.placeRefusal(level, WonderPlanItem.plan(plan).get(), placement, size)
			.map(c -> (c.getContents() instanceof TranslatableContents t ? t.getKey() : "") + " (" + c.getString() + ")").orElse("none");
	}

	private static Town town(GameTestHelper helper) {
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
		BlockPos centre = hall.offset(-24, 0, -24).offset(size.getX() / 2, 0, size.getZ() / 2);
		helper.assertTrue(refusal(level, plan, centre).equals("none"), "set-up: the open ground isn't free: " + refusal(level, plan, centre));
		return new Town(level, player, builder, plan, centre);
	}

	private static BuildSite hut(GameTestHelper helper, Town t) {
		BlueprintData.Placement hut = new BlueprintData.Placement(t.level().dimension().location(), t.centre().offset(-2, 0, -2), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(t.level(), t.builder(), t.player().getUUID(), "Owner", HUT, hut);
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(t.level()).remove(site.id());
			BuildSiteManager.get(t.level()).forgetFinished(hut);
		});
		return site;
	}

	/** Refused over the hut going up, naming it; once the hut is canceled the plan goes down there again. */
	//$ gametest_batch SMALL '"wonderPlanOverSite"'
	@GameTest(template = SMALL, batch = "wonderPlanOverSite")
	public void aPlanIsRefusedOverAHouseGoingUpAndFreeOnceCanceled(GameTestHelper helper) {
		Town t = town(helper);
		BuildSite site = hut(helper, t);
		String over = refusal(t.level(), t.plan(), t.centre());
		helper.assertTrue(over.startsWith("message.aliveworkplace.wonder_plan.over_site ") && over.contains("Test Hut"),
			"expected refused over the Test Hut in progress, got " + over);
		Builders.cancel(t.level(), site);
		helper.assertTrue(BuildSiteManager.get(t.level()).get(site.id()) == null, "set-up: the hut wasn't canceled");
		String after = refusal(t.level(), t.plan(), t.centre());
		helper.assertTrue(after.equals("none"), "the hut was canceled, but the plan is still refused: " + after);
		helper.succeed();
	}

	/** Once the hut is finished, the plan is refused as over a finished building, never as over a build in progress. */
	//$ gametest_batch SMALL '"wonderPlanOverSiteDone"'
	@GameTest(template = SMALL, batch = "wonderPlanOverSiteDone")
	public void aPlanOverAFinishedHouseSaysItIsFinished(GameTestHelper helper) {
		Town t = town(helper);
		BuildSite site = hut(helper, t);
		BuildSiteManager manager = BuildSiteManager.get(t.level());
		manager.recordFinished(site.structure(), site.placement(), t.player().getUUID());
		manager.remove(site.id());
		String over = refusal(t.level(), t.plan(), t.centre());
		helper.assertTrue(over.startsWith("message.aliveworkplace.wonder_plan.over_building ") && over.contains("Test Hut"),
			"expected refused over the finished Test Hut, got " + over);
		helper.succeed();
	}
}
