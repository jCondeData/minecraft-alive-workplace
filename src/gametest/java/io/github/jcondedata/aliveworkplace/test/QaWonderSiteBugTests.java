package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA bug tests (ROADMAP 35.4 / 35.7), NOT registered: they fail on today's code. Each names the bug.
 *
 * 35.4: "Sneak-breaking the stone (owner or op) or /workplace cancel ends the Wonder"; 35.7: "Cancelling hands back the
 * goods of a stage not yet dedicated". While a stage waits for its goods it has no build sites, so there is no id for
 * /workplace cancel: the command only takes a build site's id, and the Wonder's own id answers "No build with that id."
 */
public class QaWonderSiteBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");

	//$ gametest_batch AREA '"qaWonderCancelWaiting"'
	@GameTest(template = AREA, batch = "qaWonderCancelWaiting")
	public void workplaceCancelCannotEndAWonderWaitingForItsGoods(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "QaBugOwner");
		BlockPos hallRel = new BlockPos(14, 2, 14);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallRel);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.FOUNDATION_STONE);
		BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 3));
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, helper.absolutePos(hallRel), owner.getUUID(), "Owner", Chronicle.day(level));
		Leftovers.after(helper, () -> WonderManager.get(level).remove(site.id()));
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), stone.offset(1, 0, 1), Rotation.NONE, Mirror.NONE), stone);
		manager.setStage(site.id(), 3);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_CANDLE, 3));
		level.getBlockState(stone).useItemOn(owner.getMainHandItem(), level, owner, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false));
		helper.assertTrue(manager.site(site.id()).orElseThrow().parts().isEmpty(), "setup: the stage has parts");
		seen.clear();
		level.getServer().getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "workplace cancel " + site.id());
		helper.assertTrue(WonderManager.get(level).site(site.id()).isEmpty(), "/workplace cancel <the Wonder's id> left a Wonder waiting for its goods going; told: "
			+ seen.stream().map(Component::getString).toList());
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1 && owner.getInventory().countItem(Items.WHITE_CANDLE) == 3,
			"the plan and the 3 handed-in candles didn't come back");
		helper.succeed();
	}
}
