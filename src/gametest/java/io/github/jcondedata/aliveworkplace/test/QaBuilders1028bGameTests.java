package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1028-0338, round 2) from the specs of B590 (the Settler's Wagon camp's chest of supplies opens where it
 * stands) and B258 (the walk to a picked workstation ends when the villager rests, so an attic bed is slept in). Here:
 * the camp made facing each way, and the other side of B258's boundary (by day the walk to a picked workstation still
 * makes the villager take the job).
 */
public class QaBuilders1028bGameTests {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * The player uses the wagon facing {@code facing}: the camp's chest has nothing on it that keeps its lid shut, a
	 * player's click on it opens it, and it shows the Village Hall and two blueprints.
	 */
	private static void campChestOpens(GameTestHelper helper, Direction facing) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.setYRot(facing.toYRot());
			helper.assertTrue(player.getDirection() == facing, "setup: the player faces " + player.getDirection());
			List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(new BlockPos(9, 2, 9)));
			helper.assertTrue(settlers.size() == 2, "no camp was made facing " + facing + ": " + settlers.size() + " settlers");
			BlockPos chest = null;
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(21, 10, 21)))) {
				if (level.getBlockState(p).is(Blocks.CHEST)) {
					helper.assertTrue(chest == null, "two chests in the camp: " + chest + " and " + p);
					chest = p.immutable();
				}
			}
			helper.assertTrue(chest != null, "the camp facing " + facing + " has no chest");
			helper.assertFalse(ChestBlock.isChestBlockedAt(level, chest),
				"facing " + facing + ", the camp's chest is blocked: above it " + level.getBlockState(chest.above()));
			BlockPos at = chest;
			player.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0, 90);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.4, 0), Direction.UP, at, false);
			InteractionResult result = player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
			helper.assertTrue(result.consumesAction() && player.containerMenu instanceof ChestMenu,
				"facing " + facing + ", clicking the chest opened nothing: " + result + " " + player.containerMenu);
			Container shown = ((ChestMenu) player.containerMenu).getContainer();
			boolean hall = false;
			int blueprints = 0;
			for (int i = 0; i < shown.getContainerSize(); i++) {
				ItemStack s = shown.getItem(i);
				hall |= s.is(ModBlocks.VILLAGE_HALL.asItem());
				if (s.getItem() instanceof BlueprintItem) {
					blueprints += s.getCount();
				}
			}
			helper.assertTrue(hall && blueprints >= 2, "facing " + facing + ", the chest shows hall " + hall + ", " + blueprints + " blueprints");
			player.closeContainer();
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"qaB590North"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaB590North")
	public void qaB590TheCampMadeFacingNorthHasAChestThatOpens(GameTestHelper helper) {
		campChestOpens(helper, Direction.NORTH);
	}

	//$ gametest_ticks_batch AREA '40' '"qaB590East"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaB590East")
	public void qaB590TheCampMadeFacingEastHasAChestThatOpens(GameTestHelper helper) {
		campChestOpens(helper, Direction.EAST);
	}

	//$ gametest_ticks_batch AREA '40' '"qaB590South"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaB590South")
	public void qaB590TheCampMadeFacingSouthHasAChestThatOpens(GameTestHelper helper) {
		campChestOpens(helper, Direction.SOUTH);
	}

	//$ gametest_ticks_batch AREA '40' '"qaB590West"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaB590West")
	public void qaB590TheCampMadeFacingWestHasAChestThatOpens(GameTestHelper helper) {
		campChestOpens(helper, Direction.WEST);
	}

	/**
	 * B258's other side: the walk to a picked workstation now ends when the villager rests, but by day it must still
	 * run. A jobless villager who picked a barrel at noon walks to it and becomes the Fisherman there.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaB258Day"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaB258Day")
	public void qaB258ByDayAPickedWorkstationIsStillTaken(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.onEachTick(() -> helper.setDayTime(2000));
		BlockPos barrelAt = new BlockPos(12, 2, 9);
		helper.setBlock(barrelAt, Blocks.BARREL);
		BlockPos barrel = helper.absolutePos(barrelAt);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 9));
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
		helper.runAfterDelay(2, () -> {
			level.getPoiManager().take(h -> h.is(PoiTypes.FISHERMAN), (h, p) -> p.equals(barrel), barrel, 1);
			villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), barrel));
		});
		helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.FISHERMAN
				&& villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(barrel)).orElse(false),
			"by day the villager didn't take the picked barrel: " + villager.getVillagerData().getProfession() + " at "
				+ villager.blockPosition().toShortString() + ", job site " + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
				+ ", activity " + villager.getBrain().getActiveNonCoreActivity()));
	}
}
