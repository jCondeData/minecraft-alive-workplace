package io.github.jcondedata.aliveworkplace.fish;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Hiring vanilla Fishermen: hand one a fishing rod and they fish the water near their barrel for you,
 * putting the catch in the barrel and chests next to it. Their vanilla routine is paused while they work.
 */
public final class Fishers {
	public static void init() {
		// Saved fishermen load their job after the brain is built: give them the longer shift now.
		Platform.get().onEntityLoad((entity, level) -> {
			if (entity instanceof Villager villager && isHired(villager) && isFisherman(villager)
				&& villager.getBrain().getSchedule() != ModVillagers.BUILDER_SCHEDULE) {
				villager.refreshBrain(level);
			}
		});
	}

	public static boolean isFisherman(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.FISHERMAN;
	}

	public static boolean isHired(Villager villager) {
		return ModAttachments.FISHER_JOB.getOrElse(villager, false);
	}

	/** Vanilla fisherman behaviour runs when not hired, or when there is no rod or no water to fish. */
	public static boolean vanillaMayRun(Villager villager) {
		return !isHired(villager) || FisherWork.isResting(villager);
	}

	/** Player right-clicked a fisherman while holding a fishing rod. */
	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack rod) {
		ServerLevel level = Players.level(player);
		if (!Friends.mayCommand(player, villager)) {
			Employer employer = ModAttachments.BUILDER_EMPLOYER.get(villager);
			tell(player, Component.translatable("message.aliveworkplace.not_your_builder", villager.getDisplayName(),
				employer != null ? employer.name() : "?", player.getGameProfile().getName()), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		if (isHired(villager)) {
			tell(player, Component.translatable("message.aliveworkplace.fisher.busy", villager.getDisplayName()), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		Optional<BlockPos> barrel = Builders.benchPos(villager);
		if (barrel.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.fisher.no_barrel"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		if (FisherWork.findWater(level, barrel.get(), barrel.get(), new HashSet<>()) == null) {
			tell(player, Component.translatable("message.aliveworkplace.fisher.no_water", FisherWork.RADIUS), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		Friends.hire(player, villager);
		start(level, villager, player.getAbilities().instabuild ? rod.copyWithCount(1) : rod.split(1));
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		tell(player, Component.translatable("message.aliveworkplace.fisher.started", villager.getDisplayName(), SupplyContainers.RADIUS),
			ChatFormatting.GREEN);
		return InteractionResult.SUCCESS;
	}

	/** Hires the fisherman with {@code rod} in hand. Also used by tests. */
	public static void start(ServerLevel level, Villager villager, ItemStack rod) {
		ItemStack old = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!old.isEmpty()) {
			ModAttachments.BUILDER_BAG.getOrCreate(villager).add(old);
		}
		villager.setItemSlot(EquipmentSlot.MAINHAND, rod);
		ModAttachments.FISHER_JOB.set(villager, true);
		villager.refreshBrain(level);
	}

	/** Lets the fisherman go: the catch and the rod go into the barrel and chests. */
	public static void release(ServerLevel level, Villager villager) {
		if (!isHired(villager)) {
			return;
		}
		BlockPos barrel = Builders.benchPos(villager).orElse(villager.blockPosition());
		List<BlockPos> supplies = SupplyContainers.find(level, barrel, null);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		for (ItemStack stack : bag.takeAll()) {
			store(level, supplies, barrel, stack);
		}
		ItemStack rod = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!rod.isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			store(level, supplies, barrel, rod);
		}
		ModAttachments.FISHER_JOB.remove(villager);
		villager.refreshBrain(level);
	}

	public static void sendStatus(Player player, Villager villager) {
		MutableComponent text = Component.empty();
		text.append(Component.translatable("message.aliveworkplace.fisher.header", villager.getDisplayName(),
			ModAttachments.FISH_CAUGHT.getOrElse(villager, 0)).withStyle(ChatFormatting.GOLD));
		text.append(Component.literal("\n  "));
		text.append(Component.translatable(FisherWork.isResting(villager) ? "message.aliveworkplace.fisher.resting"
			: "message.aliveworkplace.fisher.state.fishing").withStyle(ChatFormatting.GRAY));
		text.append(Component.literal("\n  "));
		text.append(BuilderLevels.describe(villager).copy().withStyle(ChatFormatting.DARK_AQUA));
		String stop = "/workplace cancel " + villager.getUUID();
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.fisher.stop").withStyle(style -> style
			.withColor(ChatFormatting.RED).withUnderlined(true)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, stop))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.fisher.stop_hover")))));
		Chat.system(player, text);
	}

	private static void store(ServerLevel level, List<BlockPos> supplies, BlockPos near, ItemStack stack) {
		ItemStack rest = SupplyContainers.insert(level, supplies, stack);
		if (!rest.isEmpty()) {
			ItemEntity item = new ItemEntity(level, near.getX() + 0.5, near.getY() + 1.1, near.getZ() + 0.5, rest);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	private static void tell(@Nullable Player player, Component message, ChatFormatting color) {
		if (player != null) {
			Chat.system(player, message.copy().withStyle(color));
		}
	}

	private Fishers() {
	}
}
