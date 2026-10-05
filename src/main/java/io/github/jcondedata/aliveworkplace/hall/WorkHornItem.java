package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The Work Horn (ROADMAP 30.11): held to the lips like a goat horn for {@link #HOLD_TICKS}, then it sounds and the
 * village round the player answers with a rush ({@link WorkHorn#blow}). Let go early, it doesn't sound.
 */
public class WorkHornItem extends Item {
	/** How long the horn is held up before it sounds (a goat horn's toot, shortened). */
	public static final int HOLD_TICKS = 30;
	/** A pause after it sounds, as a goat horn has. */
	public static final int COOLDOWN_TICKS = 100;

	public WorkHornItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(player.getItemInHand(hand));
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.TOOT_HORN;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return HOLD_TICKS;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (level instanceof ServerLevel server && entity instanceof Player player) {
			sound(server, player);
			WorkHorn.Result result = WorkHorn.blow(server, player, player.blockPosition());
			if (result.message() != null) {
				Chat.actionBar(player, result.message());
			}
			player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
		}
		return stack;
	}

	/** The horn's call: a goat horn's, a little lower. */
	public static void sound(ServerLevel level, Player player) {
		level.playSound(null, player.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.RECORDS, 16f, 0.9f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.aliveworkplace.work_horn").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.aliveworkplace.work_horn.once").withStyle(ChatFormatting.DARK_GRAY));
	}
}
