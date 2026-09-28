package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * A plan for a building. Right-click the ground to choose where it goes (it faces you),
 * sneak-right-click the ground to turn it, sneak-right-click the air to pick it back up,
 * then hand it to a Builder villager.
 */
public class BlueprintItem extends Item {
	public BlueprintItem(Properties properties) {
		super(properties);
	}

	public static ItemStack create(ResourceLocation structure, @Nullable Vec3i size) {
		ItemStack stack = new ItemStack(ModItems.BLUEPRINT);
		stack.set(ModComponents.BLUEPRINT, new BlueprintData(structure, Optional.ofNullable(size), Optional.empty()));
		return stack;
	}

	public static Optional<BlueprintData> data(ItemStack stack) {
		return Optional.ofNullable(stack.get(ModComponents.BLUEPRINT));
	}

	/** Template-space point that lands on the block the player clicks: front edge, centre, floor. */
	public static BlockPos anchor(Vec3i size) {
		return new BlockPos(size.getX() / 2, 0, 0);
	}

	/** Rotation that makes the blueprint's front (template north / low Z side) face {@code front}. */
	public static Rotation rotationFacing(Direction front) {
		return switch (front) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	public static BlueprintData.Placement placementAt(ResourceLocation dimension, Vec3i size, BlockPos anchorWorld, Rotation rotation) {
		BlockPos rotatedAnchor = StructureTemplate.transform(anchor(size), Mirror.NONE, rotation, BlockPos.ZERO);
		return new BlueprintData.Placement(dimension, anchorWorld.subtract(rotatedAnchor), rotation, Mirror.NONE);
	}

	public static BlockPos anchorWorld(BlueprintData.Placement placement, Vec3i size) {
		return placement.origin().offset(StructureTemplate.transform(anchor(size), placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	@Override
	public Component getName(ItemStack stack) {
		return data(stack)
			.map(d -> (Component) Component.translatable("item.aliveworkplace.blueprint.named", Blueprints.displayName(d.structure())))
			.orElseGet(() -> super.getName(stack));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Optional<BlueprintData> data = data(context.getItemInHand());
		if (player == null || data.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}

		BlueprintData current = data.get();
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, current.structure());
		if (blueprint.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.blueprint.unknown", current.structure().toString()).withStyle(ChatFormatting.RED), true);
			return InteractionResult.FAIL;
		}
		Vec3i size = blueprint.get().size();
		ResourceLocation dimension = level.dimension().location();

		BlueprintData.Placement placement;
		if (player.isShiftKeyDown() && current.placement().isPresent() && current.placement().get().dimension().equals(dimension)) {
			BlueprintData.Placement old = current.placement().get();
			placement = placementAt(dimension, size, anchorWorld(old, size), old.rotation().getRotated(Rotation.CLOCKWISE_90));
		} else {
			BlockPos clicked = context.getClickedPos().relative(context.getClickedFace());
			placement = placementAt(dimension, size, clicked, rotationFacing(player.getDirection().getOpposite()));
		}

		context.getItemInHand().set(ModComponents.BLUEPRINT, current.withSize(size).withPlacement(Optional.of(placement)));
		Direction front = placement.rotation().rotate(Direction.NORTH);
		player.displayClientMessage(Component.translatable(
			"message.aliveworkplace.blueprint.placed",
			Blueprints.displayName(current.structure()),
			Component.translatable("direction.aliveworkplace." + front.getSerializedName())
		), true);
		BlueprintOutline.show(level, player, placement, size);
		return InteractionResult.CONSUME;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Optional<BlueprintData> data = data(stack);
		if (player.isShiftKeyDown() && data.isPresent() && data.get().placement().isPresent()) {
			if (!level.isClientSide) {
				stack.set(ModComponents.BLUEPRINT, data.get().withPlacement(Optional.empty()));
				player.displayClientMessage(Component.translatable("message.aliveworkplace.blueprint.cleared"), true);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		if (!player.isShiftKeyDown() && data.isPresent()) {
			// Right-click the air: switch levelling the ground around this build on or off.
			if (!level.isClientSide) {
				boolean now = !data.get().levelGround();
				stack.set(ModComponents.BLUEPRINT, data.get().withLevelGround(now));
				player.displayClientMessage(Component.translatable(now ? "message.aliveworkplace.blueprint.level_on"
					: "message.aliveworkplace.blueprint.level_off").withStyle(now ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		return InteractionResultHolder.pass(stack);
	}

	/** Keeps the "still missing" list of a placed blueprint up to date (see {@code BlueprintSupplies}). */
	@Override
	public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
		if (level instanceof ServerLevel server && entity instanceof Player) {
			io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.tick(server, stack, slot);
		}
	}

	/** The "still missing" list changing isn't a new item: no re-equip bob in the hand. */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		data(stack).ifPresent(d -> {
			d.size().ifPresent(s -> tooltip.add(Component.translatable("tooltip.aliveworkplace.blueprint.size", s.getX(), s.getY(), s.getZ()).withStyle(ChatFormatting.GRAY)));
			if (!d.levelGround()) {
				tooltip.add(Component.translatable("tooltip.aliveworkplace.blueprint.no_levelling").withStyle(ChatFormatting.YELLOW));
			}
			if (d.placement().isPresent()) {
				BlockPos o = d.placement().get().origin();
				Direction front = d.placement().get().rotation().rotate(Direction.NORTH);
				tooltip.add(Component.translatable("tooltip.aliveworkplace.blueprint.placed", o.getX(), o.getY(), o.getZ(),
					Component.translatable("direction.aliveworkplace." + front.getSerializedName())).withStyle(ChatFormatting.AQUA));
				tooltip.add(Component.translatable("tooltip.aliveworkplace.blueprint.hand_over").withStyle(ChatFormatting.GRAY));
				tooltip.add(Component.translatable("tooltip.aliveworkplace.blueprint.deconstruct_hint").withStyle(ChatFormatting.DARK_GRAY));
			} else {
				tooltip.add(Component.translatable("tooltip.aliveworkplace.blueprint.place_hint").withStyle(ChatFormatting.GRAY));
			}
			if (flag.isAdvanced()) {
				tooltip.add(Component.literal(d.structure().toString()).withStyle(ChatFormatting.DARK_GRAY));
			}
		});
	}
}
