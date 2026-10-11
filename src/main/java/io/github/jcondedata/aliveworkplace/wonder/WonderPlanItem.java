package io.github.jcondedata.aliveworkplace.wonder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StylePicker;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * The Wonder Plan (35.3): a rolled blueprint with a gold seal, drawn up on the hall's Wonders page. It holds the whole
 * Wonder's blueprint ({@link WonderBlueprints}) as an ordinary blueprint's data, so held and placed it shows the Wonder
 * as see-through blocks like any blueprint; and the Wonder and its village ({@link Plan}). Right-click the ground to
 * place it (facing you), sneak-right-click the ground to turn it, sneak-right-click the air to pick it up again or, not
 * placed, open the style screen, where it can only be mirrored (each Wonder keeps its palette). It goes down only where
 * {@link WonderPlans#placeRefusal} allows, and says why not otherwise.
 */
public class WonderPlanItem extends BlueprintItem {
	/** The Wonder, its village's hall, and the village's name when the plans were drawn. */
	public record Plan(ResourceLocation wonder, GlobalPos hall, Component village) {
		public static final Codec<Plan> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("wonder").forGetter(Plan::wonder),
			GlobalPos.CODEC.fieldOf("hall").forGetter(Plan::hall),
			ComponentSerialization.CODEC.fieldOf("village").forGetter(Plan::village)
		).apply(i, Plan::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Plan> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
	}

	public WonderPlanItem(Properties properties) {
		super(properties);
	}

	/** A plan of {@code wonder} for the village at {@code hall}, its size taken from the whole blueprint. */
	public static ItemStack of(ServerLevel level, BlockPos hall, Wonder wonder, Component village) {
		ItemStack stack = new ItemStack(ModItems.WONDER_PLAN);
		ResourceLocation whole = WonderBlueprints.idOf(wonder.id());
		Optional<Vec3i> size = BlueprintLibrary.get(level, whole).map(Blueprint::size);
		stack.set(ModComponents.BLUEPRINT, new BlueprintData(whole, size, Optional.empty()));
		stack.set(ModComponents.WONDER_PLAN, new Plan(wonder.id(), GlobalPos.of(level.dimension(), hall.immutable()), village));
		return stack;
	}

	public static Optional<Plan> plan(ItemStack stack) {
		return Optional.ofNullable(stack.get(ModComponents.WONDER_PLAN));
	}

	@Override
	public Component getName(ItemStack stack) {
		return plan(stack).map(p -> (Component) Component.translatable("item.aliveworkplace.wonder_plan.named", Wonders.name(p.wonder())))
			.orElseGet(() -> Component.translatable(getDescriptionId()));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		Optional<BlueprintData> data = data(stack);
		Optional<Plan> plan = plan(stack);
		if (player == null || data.isEmpty() || plan.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlueprintData current = data.get();
		Optional<Blueprint> blueprint = BlueprintLibrary.get(level, current.structure());
		if (blueprint.isEmpty()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.wonder_plan.unknown", Wonders.name(plan.get().wonder())).withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		Vec3i size = blueprint.get().size();
		ResourceLocation dimension = Ids.of(level.dimension());
		BlueprintData.Placement placement;
		if (player.isShiftKeyDown() && current.placement().isPresent() && current.placement().get().dimension().equals(dimension)) {
			BlueprintData.Placement old = current.placement().get();
			placement = placementAt(dimension, size, anchorWorld(old, size), old.rotation().getRotated(Rotation.CLOCKWISE_90), current.mirror());
		} else {
			BlockPos clicked = context.getClickedPos().relative(context.getClickedFace());
			placement = placementAt(dimension, size, clicked, rotationFacing(player.getDirection().getOpposite()), current.mirror());
		}
		Optional<Component> refusal = WonderPlans.placeRefusal(level, plan.get(), placement, size);
		if (refusal.isPresent()) {
			Chat.actionBar(player, refusal.get().copy().withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		stack.set(ModComponents.BLUEPRINT, current.withSize(size).withPlacement(Optional.of(placement)));
		Direction front = placement.rotation().rotate(Direction.NORTH);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.wonder_plan.placed", Wonders.name(plan.get().wonder()),
			Component.translatable("direction.aliveworkplace." + front.getSerializedName())).withStyle(ChatFormatting.GREEN));
		BlueprintOutline.show(level, player, placement, size);
		return InteractionResult.CONSUME;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Optional<BlueprintData> data = data(stack);
		InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		if (data.isEmpty()) {
			return InteractionResultHolder.pass(stack);
		}
		if (!player.isShiftKeyDown() && player.getItemInHand(other).is(net.minecraft.world.item.Items.WRITABLE_BOOK)) {
			if (level instanceof ServerLevel server) {
				writeMaterialList(server, player, data.get(), other); // the whole Wonder's material list (23.4)
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		if (player.isShiftKeyDown() && data.get().placement().isPresent()) {
			if (!level.isClientSide()) {
				stack.set(ModComponents.BLUEPRINT, data.get().withPlacement(Optional.empty()));
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.blueprint.cleared"));
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		if (player.isShiftKeyDown()) {
			if (player instanceof ServerPlayer server) {
				StylePicker.open(server, hand); // mirroring only: a Wonder keeps its palette
			}
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		return InteractionResultHolder.pass(stack); // a Wonder levels its own ground (its last stage), so no switch here
	}

	/**
	 * After the style screen mirrored a placed plan: if the mirrored Wonder may not stand there, the plan is picked up
	 * again and {@code player} told why; else {@code flipped} as it is.
	 */
	public static BlueprintData afterMirror(ServerLevel level, Player player, ItemStack stack, BlueprintData flipped) {
		Optional<Plan> plan = plan(stack);
		if (plan.isEmpty() || flipped.placement().isEmpty() || flipped.size().isEmpty()) {
			return flipped;
		}
		Optional<Component> refusal = WonderPlans.placeRefusal(level, plan.get(), flipped.placement().get(), flipped.size().get());
		if (refusal.isPresent()) {
			Chat.chat(player, refusal.get().copy().withStyle(ChatFormatting.RED));
			return flipped.withPlacement(Optional.empty());
		}
		return flipped;
	}

	/** Its stockpile and supplies come with the Foundation Stone (35.4), not from the chests round the player. */
	@Override
	public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Optional<Plan> plan = plan(stack);
		Optional<BlueprintData> data = data(stack);
		if (plan.isEmpty() || data.isEmpty()) {
			return;
		}
		tooltip.add(Component.translatable("tooltip.aliveworkplace.wonder_plan.village", plan.get().village()).withStyle(ChatFormatting.GOLD));
		BlueprintData d = data.get();
		d.size().ifPresent(s -> tooltip.add(Component.translatable("tooltip.aliveworkplace.blueprint.size", s.getX(), s.getY(), s.getZ()).withStyle(ChatFormatting.GRAY)));
		if (d.mirrored()) {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.blueprint.mirrored").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (d.placement().isPresent()) {
			BlockPos o = d.placement().get().origin();
			Direction front = d.placement().get().rotation().rotate(Direction.NORTH);
			tooltip.add(Component.translatable("tooltip.aliveworkplace.blueprint.placed", o.getX(), o.getY(), o.getZ(),
				Component.translatable("direction.aliveworkplace." + front.getSerializedName())).withStyle(ChatFormatting.AQUA));
			tooltip.add(Component.translatable("tooltip.aliveworkplace.wonder_plan.turn").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.wonder_plan.place", WonderPlans.REACH).withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("tooltip.aliveworkplace.wonder_plan.mirror").withStyle(ChatFormatting.DARK_GRAY));
		}
		tooltip.add(Component.translatable("tooltip.aliveworkplace.blueprint.list_hint").withStyle(ChatFormatting.DARK_GRAY));
		if (flag.isAdvanced()) {
			tooltip.add(Component.literal(d.structure().toString()).withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
