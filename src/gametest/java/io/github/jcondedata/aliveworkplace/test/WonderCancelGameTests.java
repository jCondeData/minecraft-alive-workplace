package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
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
 * B462 (ROADMAP 35.4 / 35.7): {@code /workplace cancel} ends a Wonder whose stage waits for its goods (it has no build
 * sites then, so the Wonder's own id is taken, and the stone's screen offers it), handing back the plan and the goods
 * handed in for the stage not yet dedicated; with no goods in, just the plan; through a restart too; strangers refused.
 */
public class WonderCancelGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");

	/** A Town hall owned by {@code owner}, a stone, and the folly at stage III (white candles), waiting for its goods. */
	private static WonderSite waiting(GameTestHelper helper, ServerPlayer owner) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
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
		return manager.site(site.id()).orElseThrow();
	}

	private static void handInCandles(GameTestHelper helper, ServerPlayer player, int count) {
		ServerLevel level = helper.getLevel();
		BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 3));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_CANDLE, count));
		level.getBlockState(stone).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false));
	}

	private static void runCancel(ServerPlayer player, WonderSite site) {
		player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "workplace cancel " + site.id());
	}

	//$ gametest_batch AREA '"wonderCancelWaiting"'
	@GameTest(template = AREA, batch = "wonderCancelWaiting")
	public void workplaceCancelEndsAWonderWaitingForItsGoods(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "B462Owner");
		WonderSite site = waiting(helper, owner);
		handInCandles(helper, owner, 3);
		WonderSite now = WonderManager.get(level).site(site.id()).orElseThrow();
		helper.assertTrue(now.parts().isEmpty() && !now.started(), "setup: the stage isn't waiting for its goods");
		helper.assertTrue(owner.getInventory().countItem(Items.WHITE_CANDLE) == 0, "setup: the candles weren't handed in");
		// the stone's screen offers the Wonder's id for the command
		Component status = WonderStages.statusText(level, helper.absolutePos(new BlockPos(3, 2, 3)));
		String suggested = "/workplace cancel " + site.id();
		boolean offered = status.toFlatList().stream().anyMatch(c -> c.getStyle().getClickEvent() != null
			&& c.getStyle().getClickEvent().getAction() == ClickEvent.Action.SUGGEST_COMMAND && c.getStyle().getClickEvent().getValue().equals(suggested));
		helper.assertTrue(offered, "the stone's screen doesn't offer " + suggested + ": " + status.getString());
		helper.assertTrue(status.getString().contains("[Abandon this Wonder]"), "the stone's abandon link reads: " + status.getString());
		// a stranger is refused and the Wonder goes on
		List<Component> strangerSaw = new ArrayList<>();
		ServerPlayer stranger = WonderFinishedGameTests.listener(helper, strangerSaw, "B462Stranger");
		runCancel(stranger, site);
		helper.assertTrue(WonderManager.get(level).site(site.id()).isPresent(), "a stranger's /workplace cancel ended the Wonder");
		helper.assertTrue(stranger.getInventory().countItem(Items.WHITE_CANDLE) == 0 && stranger.getInventory().countItem(ModItems.WONDER_PLAN) == 0,
			"a stranger was given the goods or the plan");
		helper.assertTrue(strangerSaw.stream().map(Component::getString).anyMatch(s -> s.contains("Only the player who started")),
			"the stranger wasn't told why: " + strangerSaw.stream().map(Component::getString).toList());
		seen.clear();
		runCancel(owner, site);
		List<String> told = seen.stream().map(Component::getString).toList();
		helper.assertTrue(WonderManager.get(level).site(site.id()).isEmpty(), "/workplace cancel <the Wonder's id> left it going; told: " + told);
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1, "the plan didn't come back: " + told);
		helper.assertTrue(owner.getInventory().countItem(Items.WHITE_CANDLE) == 3, "the 3 handed-in candles didn't come back: "
			+ owner.getInventory().countItem(Items.WHITE_CANDLE));
		helper.assertTrue(told.stream().anyMatch(s -> s.equals("Canceled the Wonder. What's built stays, and its Wonder Plan has come back.")),
			"the command's line: " + told);
		helper.assertTrue(told.stream().noneMatch(s -> s.contains("No build with that id")), "told there's no build: " + told);
		helper.succeed();
	}

	//$ gametest_batch AREA '"wonderCancelNoGoods"'
	@GameTest(template = AREA, batch = "wonderCancelNoGoods")
	public void workplaceCancelEndsAWonderWithNoGoodsIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "B462NoGoods");
		WonderSite site = waiting(helper, owner);
		helper.assertTrue(site.delivered().isEmpty() && site.parts().isEmpty(), "setup: goods in or parts open");
		runCancel(owner, site);
		helper.assertTrue(WonderManager.get(level).site(site.id()).isEmpty(), "the Wonder with no goods in goes on: "
			+ seen.stream().map(Component::getString).toList());
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1, "the plan didn't come back");
		helper.assertTrue(owner.getInventory().countItem(Items.WHITE_CANDLE) == 0, "candles came from nowhere");
		// a second cancel finds nothing and gives nothing more
		runCancel(owner, site);
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1, "a second cancel gave another plan");
		helper.succeed();
	}

	//$ gametest_batch AREA '"wonderCancelReload"'
	@GameTest(template = AREA, batch = "wonderCancelReload")
	public void workplaceCancelAfterARestartHandsBackTheGoods(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "B462Reload");
		WonderSite site = waiting(helper, owner);
		handInCandles(helper, owner, 2);
		var registries = level.registryAccess();
		level.getDataStorage().set(WonderManager.NAME, WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries));
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		helper.assertTrue(WonderManager.get(level).site(site.id()).isPresent(), "setup: the Wonder didn't survive the restart");
		runCancel(owner, site);
		helper.assertTrue(WonderManager.get(level).site(site.id()).isEmpty(), "the Wonder goes on after the restart: "
			+ seen.stream().map(Component::getString).toList());
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1 && owner.getInventory().countItem(Items.WHITE_CANDLE) == 2,
			"after a restart the plan and 2 candles didn't come back: " + owner.getInventory().countItem(Items.WHITE_CANDLE));
		helper.succeed();
	}
}
