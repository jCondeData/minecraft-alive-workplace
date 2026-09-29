package io.github.jcondedata.aliveworkplace.camp;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * The Settler's Wagon: right-click open ground with it and two settlers make camp there — a covered wagon with a chest
 * of supplies (building materials, food, two blueprints and a Village Hall), a Builder's Bench, a campfire and two
 * bedrolls. One settler takes the bench and is your builder from the start; the other is free to take any job. So a
 * village can begin anywhere, not only where the world put one.
 */
public class SettlersWagonItem extends Item {
	/** The camp's blueprint (placed at once, not built). */
	public static final ResourceLocation CAMP = AliveWorkplace.id("camp/settlers_camp");
	/** Most blocks in the way (trees, rock) before the spot counts as too cramped. */
	static final int MAX_IN_THE_WAY = 12;

	public SettlersWagonItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos clicked = context.getClickedPos().relative(context.getClickedFace());
		List<Villager> settlers = makeCamp(level, player, clicked);
		if (settlers.isEmpty()) {
			return InteractionResult.FAIL;
		}
		if (!player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		Chat.chat(player, Component.translatable("message.aliveworkplace.camp.made", settlers.get(0).getDisplayName(),
			settlers.get(1).getDisplayName()).withStyle(ChatFormatting.GREEN));
		return InteractionResult.CONSUME;
	}

	/**
	 * Sets up camp with its front at {@code front} (facing {@code player}): the camp, then two settlers, the first of them
	 * the builder. Empty (and a message to the player) if there's no room.
	 */
	public static List<Villager> makeCamp(ServerLevel level, ServerPlayer player, BlockPos front) {
		StructureTemplate template = level.getStructureManager().get(CAMP).orElse(null);
		if (template == null) {
			return List.of();
		}
		Vec3i size = template.getSize();
		BlueprintData.Placement placement = BlueprintItem.placementAt(Ids.of(level.dimension()), size, front,
			BlueprintItem.rotationFacing(player.getDirection().getOpposite()));
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(placement.rotation());
		BoundingBox box = template.getBoundingBox(settings, placement.origin());
		// Room for it: the space above the ground mostly open, and ground under most of it.
		int inTheWay = 0;
		int noGround = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(p);
			if (!state.isAir() && !state.canBeReplaced()) {
				inTheWay++;
			}
		}
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				if (level.getBlockState(new BlockPos(x, box.minY() - 1, z)).canBeReplaced()) {
					noGround++;
				}
			}
		}
		if (inTheWay > MAX_IN_THE_WAY || noGround > box.getXSpan() * box.getZSpan() / 3) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.camp.no_room").withStyle(ChatFormatting.YELLOW));
			return List.of();
		}
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(p).isAir()) {
				level.removeBlock(p, false); // the tall grass, a sapling, a stray rock
			}
		}
		template.placeInWorld(level, placement.origin(), placement.origin(), settings, level.getRandom(), 2);
		BlockPos bench = null;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(p).is(ModBlocks.BUILDERS_BENCH)) {
				bench = p.immutable();
			}
		}
		List<Villager> settlers = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			BlockPos spot = standingSpot(level, box, i);
			Villager settler = EntityType.VILLAGER.spawn(level, spot != null ? spot : box.getCenter(), MobSpawnType.EVENT);
			if (settler == null) {
				continue;
			}
			settler.setVillagerData(settler.getVillagerData().setProfession(VillagerProfession.NONE));
			settlers.add(settler);
		}
		if (settlers.size() < 2) {
			return settlers;
		}
		// The builder takes the bench once the world has it on its list of workstations (the next tick).
		Villager builder = settlers.get(0);
		BlockPos benchPos = bench;
		if (benchPos != null) {
			level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1, () -> {
				if (builder.isAlive() && level.getBlockState(benchPos).is(ModBlocks.BUILDERS_BENCH)) {
					Builders.employ(level, builder, benchPos);
					Friends.hire(player, builder);
				}
			}));
		}
		level.playSound(null, box.getCenter(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		return settlers;
	}

	@Nullable
	private static BlockPos standingSpot(ServerLevel level, BoundingBox box, int n) {
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX() - 1, box.minY(), box.minZ() - 1, box.maxX() + 1, box.minY() + 1, box.maxZ() + 1)) {
			if (Walker.canStand(level, p)) {
				spots.add(p.immutable());
			}
		}
		if (spots.isEmpty()) {
			return null;
		}
		BlockPos centre = box.getCenter();
		spots.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(centre)));
		return spots.get(Math.min(spots.size() - 1, n * 3));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.aliveworkplace.settlers_wagon").withStyle(ChatFormatting.GRAY));
	}
}
