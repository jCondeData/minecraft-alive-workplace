package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Turns something built in the world into a blueprint, in survival: right-click one corner block, then the opposite
 * corner (the box shows while it's held); sneak-right-click the air to save it — for a Blank Blueprint from the
 * inventory, the player gets the blueprint, and it's in the Blueprint Table for everyone. Rename the tool in an anvil to
 * name the build ("Cozy Cabin", then "Cozy Cabin 2" for its upgrade). Sneak-right-click a block to start over. The
 * low-Z side (north) is the front, the lowest layer the one that sits on the ground; a builder can turn it when placing.
 */
public class ScanToolItem extends Item {
	/** Largest side of a scan. */
	public static int MAX_SIDE = 48;

	public ScanToolItem(Properties properties) {
		super(properties.component(ModComponents.SCAN, FieldData.EMPTY));
	}

	public static FieldData data(ItemStack stack) {
		return stack.getOrDefault(ModComponents.SCAN, FieldData.EMPTY);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		if (context.getLevel().isClientSide || player == null) {
			return InteractionResult.SUCCESS;
		}
		FieldData data = data(stack);
		BlockPos pos = context.getClickedPos();
		var dim = context.getLevel().dimension().location();
		if (player.isShiftKeyDown()) {
			stack.set(ModComponents.SCAN, FieldData.EMPTY);
			player.displayClientMessage(Component.translatable("message.aliveworkplace.scan.reset"), true);
			return InteractionResult.SUCCESS;
		}
		if (data.first().isEmpty() || data.isComplete() || !data.dimension().map(dim::equals).orElse(false)) {
			stack.set(ModComponents.SCAN, new FieldData(Optional.of(dim), Optional.of(pos), Optional.empty()));
			player.displayClientMessage(Component.translatable("message.aliveworkplace.scan.first", pos.getX(), pos.getY(), pos.getZ()), true);
			return InteractionResult.SUCCESS;
		}
		FieldData done = new FieldData(Optional.of(dim), data.first(), Optional.of(pos));
		BoundingBox box = done.area().orElseThrow();
		if (!fits(box)) {
			player.displayClientMessage(tooBig(box).withStyle(ChatFormatting.RED), true);
			return InteractionResult.FAIL;
		}
		stack.set(ModComponents.SCAN, done);
		player.displayClientMessage(Component.translatable("message.aliveworkplace.scan.marked", box.getXSpan(), box.getYSpan(), box.getZSpan()), false);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown()) {
			return InteractionResultHolder.pass(stack);
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			FieldData data = data(stack);
			if (!data.isComplete() || !data.dimension().get().equals(level.dimension().location())) {
				player.displayClientMessage(Component.translatable("message.aliveworkplace.scan.unmarked").withStyle(ChatFormatting.YELLOW), true);
			} else {
				String name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : player.getGameProfile().getName() + "s_build";
				Component result = save(serverLevel, serverPlayer, data.area().get(), name);
				player.displayClientMessage(result, false);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	static boolean fits(BoundingBox box) {
		return box.getXSpan() <= MAX_SIDE && box.getYSpan() <= MAX_SIDE && box.getZSpan() <= MAX_SIDE;
	}

	private static net.minecraft.network.chat.MutableComponent tooBig(BoundingBox box) {
		return Component.translatable("message.aliveworkplace.scan.too_big", box.getXSpan(), box.getYSpan(), box.getZSpan(), MAX_SIDE);
	}

	/**
	 * Saves what's in {@code box} as a blueprint called {@code name} (under {@code scans/<player>/}), for a Blank Blueprint
	 * from {@code player}'s inventory (none needed in creative), and gives them the blueprint. Returns what to tell them.
	 */
	public static Component save(ServerLevel level, ServerPlayer player, BoundingBox box, String name) {
		if (!fits(box)) {
			return tooBig(box).withStyle(ChatFormatting.RED);
		}
		boolean empty = true;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(pos).isAir()) {
				empty = false;
				break;
			}
		}
		if (empty) {
			return Component.translatable("message.aliveworkplace.scan.empty").withStyle(ChatFormatting.YELLOW);
		}
		int blank = blankSlot(player);
		if (blank < 0 && !player.getAbilities().instabuild) {
			return Component.translatable("message.aliveworkplace.scan.no_blank").withStyle(ChatFormatting.YELLOW);
		}
		StructureTemplateManager manager = level.getServer().getStructureManager();
		ResourceLocation id = BlueprintImporter.uniqueId(manager, "scans/" + BlueprintImporter.sanitizeFolder(player.getGameProfile().getName()), name + ".nbt");
		Vec3i size = new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan());
		StructureTemplate template = manager.getOrCreate(id);
		template.fillFromWorld(level, new BlockPos(box.minX(), box.minY(), box.minZ()), size, false, Blocks.STRUCTURE_VOID);
		template.setAuthor(player.getGameProfile().getName());
		if (!manager.save(id)) {
			manager.remove(id);
			return Component.translatable("message.aliveworkplace.scan.failed").withStyle(ChatFormatting.RED);
		}
		if (blank >= 0 && !player.getAbilities().instabuild) {
			player.getInventory().getItem(blank).shrink(1);
		}
		ItemStack blueprint = BlueprintItem.create(id, size);
		if (!player.getInventory().add(blueprint)) {
			player.drop(blueprint, false);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
		AliveWorkplace.LOG.info("{} scanned blueprint {} ({}x{}x{})", player.getGameProfile().getName(), id, size.getX(), size.getY(), size.getZ());
		return Component.translatable("message.aliveworkplace.scan.saved", Blueprints.displayName(id), size.getX(), size.getY(), size.getZ())
			.withStyle(ChatFormatting.GREEN);
	}

	private static int blankSlot(Player player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.BLANK_BLUEPRINT)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		FieldData data = data(stack);
		Optional<BoundingBox> area = data.area();
		if (area.isPresent()) {
			BoundingBox box = area.get();
			tooltip.add(Component.translatable("tooltip.aliveworkplace.scan.area", box.getXSpan(), box.getYSpan(), box.getZSpan()).withStyle(ChatFormatting.AQUA));
			tooltip.add(Component.translatable("tooltip.aliveworkplace.scan.save").withStyle(ChatFormatting.GRAY));
		} else if (data.first().isPresent()) {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.scan.second").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.scan.first").withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("tooltip.aliveworkplace.scan.name").withStyle(ChatFormatting.DARK_GRAY));
	}
}
