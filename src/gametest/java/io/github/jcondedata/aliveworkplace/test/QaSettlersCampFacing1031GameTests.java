package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (qa-lane-b-1031-1238) for B424 (and B590): "the Settler's Wagon camp's chest of supplies opens from beside the
 * wagon". Its tests place the camp's template at three turns (none, a half and a quarter clockwise) or call makeCamp
 * once; these go through the player's own entry point, the Settler's Wagon used on open ground, once for each way the
 * player can face (so the quarter counter-clockwise turn is covered too). Each time: the camp goes up, the player reads
 * the camp.made sentence as en_us.json words it, the wagon is used up, and the chest, clicked from a standing spot round
 * it, opens and holds the Village Hall and two blueprints.
 */
public class QaSettlersCampFacing1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos FRONT = new BlockPos(15, 2, 15);

	//$ gametest_ticks_batch HUGE '40' '"qaCampFacing1031N"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaCampFacing1031N")
	public void aWagonUsedFacingNorthMakesACampWhoseChestOpens(GameTestHelper helper) {
		camp(helper, Direction.NORTH);
	}

	//$ gametest_ticks_batch HUGE '40' '"qaCampFacing1031E"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaCampFacing1031E")
	public void aWagonUsedFacingEastMakesACampWhoseChestOpens(GameTestHelper helper) {
		camp(helper, Direction.EAST);
	}

	//$ gametest_ticks_batch HUGE '40' '"qaCampFacing1031S"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaCampFacing1031S")
	public void aWagonUsedFacingSouthMakesACampWhoseChestOpens(GameTestHelper helper) {
		camp(helper, Direction.SOUTH);
	}

	//$ gametest_ticks_batch HUGE '40' '"qaCampFacing1031W"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaCampFacing1031W")
	public void aWagonUsedFacingWestMakesACampWhoseChestOpens(GameTestHelper helper) {
		camp(helper, Direction.WEST);
	}

	private static void camp(GameTestHelper helper, Direction facing) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, heard, "qa-camp-" + facing.getName());
		BlockPos front = helper.absolutePos(FRONT);
		BlockPos stand = front.relative(facing.getOpposite(), 2);
		player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, facing.toYRot(), 30);
		helper.assertTrue(player.getDirection() == facing, "setup: the player faces " + player.getDirection() + ", not " + facing);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SETTLERS_WAGON));
		BlockPos ground = front.below();
		BlockHitResult click = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
		InteractionResult used = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, click);
		AliveWorkplace.LOG.info("QA B424 wagon facing {}: {}, heard {}", facing, used, heard.stream().map(Component::getString).toList());
		helper.assertTrue(used.consumesAction(), "using the wagon facing " + facing + " did nothing: " + used + ", heard "
			+ heard.stream().map(Component::getString).toList());
		helper.assertTrue(player.getMainHandItem().isEmpty(), "the wagon wasn't used up: " + player.getMainHandItem());
		Component made = heard.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("message.aliveworkplace.camp.made"))
			.findFirst().orElse(null);
		helper.assertTrue(made != null, "the player wasn't told the camp was made: " + heard.stream().map(Component::getString).toList());
		String said = made.getString();
		helper.assertTrue(said.equals(QaCityPlanGameTests.english(made)), "the camp line reads '" + said + "', en_us.json says '" + QaCityPlanGameTests.english(made) + "'");
		helper.assertTrue(said.contains(" have made camp. The chest in the wagon holds supplies, two blueprints and a Village Hall; ") && said.endsWith(" is your builder."),
			"the camp line: " + said);
		Rotation rotation = BlueprintItem.rotationFacing(facing.getOpposite());
		BoundingBox box = SettlersWagonItem.campBox(level, front, rotation);
		helper.assertTrue(box != null, "no camp template");
		BlockPos chest = null;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(p).is(Blocks.CHEST)) {
				helper.assertTrue(chest == null, "the camp has two chests");
				chest = p.immutable();
			}
		}
		helper.assertTrue(chest != null, "the camp facing " + facing + " has no chest in " + box);
		BlockHitResult hit = null;
		BlockPos from = null;
		for (BlockPos feet : BlockPos.betweenClosed(chest.offset(-5, -3, -5), chest.offset(5, 3, 5))) {
			if (QaCampReachGameTests.fits(level, feet)) {
				hit = aim(level, player, feet, chest);
				if (hit != null) {
					from = feet.immutable();
					break;
				}
			}
		}
		helper.assertTrue(hit != null, "facing " + facing + " no standing spot round the camp puts its chest at " + helper.relativePos(chest).toShortString()
			+ " under the crosshair");
		player.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0, 0);
		InteractionResult opened = player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(opened.consumesAction() && player.containerMenu instanceof ChestMenu, "facing " + facing + ", clicking the chest at "
			+ helper.relativePos(chest).toShortString() + " from " + helper.relativePos(from).toShortString() + " opened " + player.containerMenu + " (" + opened + ")");
		Container shown = ((ChestMenu) player.containerMenu).getContainer();
		boolean villageHall = false;
		int blueprints = 0;
		for (int i = 0; i < shown.getContainerSize(); i++) {
			ItemStack s = shown.getItem(i);
			villageHall |= s.is(ModBlocks.VILLAGE_HALL.asItem());
			if (s.getItem() instanceof BlueprintItem) {
				blueprints += s.getCount();
			}
		}
		player.closeContainer();
		helper.assertTrue(villageHall && blueprints == 2, "facing " + facing + " the chest holds hall " + villageHall + ", " + blueprints + " blueprints");
		helper.succeed();
	}

	/** A ray from the eyes of a player standing at {@code feet} that the game's own pick lands on {@code target}, or null. */
	private static BlockHitResult aim(ServerLevel level, ServerPlayer player, BlockPos feet, BlockPos target) {
		Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
		double reach = player.blockInteractionRange();
		for (double dx = 0.1; dx <= 0.9; dx += 0.4) {
			for (double dy = 0.1; dy <= 0.9; dy += 0.4) {
				for (double dz = 0.1; dz <= 0.9; dz += 0.4) {
					Vec3 to = new Vec3(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
					if (to.distanceTo(eye) > reach) {
						continue;
					}
					BlockHitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
					if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target)) {
						return hit;
					}
				}
			}
		}
		return null;
	}
}
