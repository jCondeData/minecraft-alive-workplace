package io.github.jcondedata.aliveworkplace.mechanic;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Create-powered builds (ROADMAP 39.4, design note M39, "The builds"): the Watermill, the Windmill and the Workshop
 * ({@link StarterBlueprints#CREATE_ONLY}). Made of Create's blocks, so they're offered at the Blueprint Table and in the
 * creative tab only with Create installed and the {@code createBuilds} switch on (its expansion, M39, open). A blueprint
 * already handed out and a site already started still load by id.
 */
public final class CreateBuilds {
	/** Config {@code createBuilds}. */
	public static volatile boolean ENABLED = true;

	private CreateBuilds() {
	}

	/** Whether {@code id} is one of the Create-powered builds. */
	public static boolean isCreateBuild(ResourceLocation id) {
		ResourceLocation base = BlueprintStyles.base(id);
		return StarterBlueprints.CREATE_ONLY.stream().anyMatch(e -> e.id().equals(base));
	}

	/** Whether {@code id} may be listed: any build that isn't Create's, or Create's while Create is installed and the switch is on. */
	public static boolean listed(ResourceLocation id) {
		return !isCreateBuild(id) || ENABLED && Platform.get().isModLoaded(Kinetics.CREATE);
	}

	/**
	 * A build just finished: on a Windmill, the builder starts each windmill bearing as a player would, with an empty
	 * hand (Create's bearing assembles its sails when used so, then saves {@code Running}, so it keeps turning after a
	 * reload). No Create class is used: the bearing is found by id and used through vanilla's {@code useItemOn}.
	 * The Watermill and the Workshop need nothing: their water is placed last, and the wheel turns as it falls.
	 */
	public static void finished(ServerLevel level, ResourceLocation structure, BlueprintData.Placement placement, LivingEntity builder) {
		if (!BlueprintStyles.base(structure).equals(StarterBlueprints.WINDMILL.id()) || !Platform.get().isModLoaded(Kinetics.CREATE)) {
			return;
		}
		net.minecraft.world.level.levelgen.structure.BoundingBox box = BlueprintOutline.bounds(placement, StarterBlueprints.WINDMILL.size());
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if ("windmill_bearing".equals(Kinetics.createId(state))) {
				startBearing(level, pos.immutable(), state, builder);
			}
		}
	}

	/** Uses the bearing at {@code pos} with an empty hand, as a player standing where the builder stands; false if there's no stand-in player. */
	static boolean startBearing(ServerLevel level, BlockPos pos, BlockState state, LivingEntity builder) {
		Player player = Platform.get().fakePlayer(level);
		if (player == null) {
			AliveWorkplace.LOG.debug("No stand-in player to start the windmill at {}", pos);
			return false;
		}
		player.moveTo(builder.getX(), builder.getY(), builder.getZ(), builder.getYRot(), builder.getXRot());
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setShiftKeyDown(false);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), state.getValue(BlockStateProperties.FACING), pos, false);
		ItemInteractionResult used = state.useItemOn(ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND, hit);
		if (used == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
			state.useWithoutItem(level, player, hit);
		}
		return true;
	}
}
