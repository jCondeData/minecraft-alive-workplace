package io.github.jcondedata.aliveworkplace.mc;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A bottled drink a player drinks like a honey bottle, and gets the glass bottle back (the Vintner's cider and wines,
 * ROADMAP 34.9). Here because the food builder, the use duration's signature and the bottle that comes back all change
 * between versions (1.21.2 moves the bottle to a use-remainder component).
 */
public class DrinkItem extends Item {
	public static final int DRINK_TICKS = 32;

	public DrinkItem(Properties properties) {
		super(properties);
	}

	/** Item properties for a drink: {@code nutrition} hunger and, if given, an effect (always drinkable, 16 to a stack). */
	public static Properties drink(int nutrition, float saturation, MobEffectInstance effect) {
		FoodProperties.Builder food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).alwaysEdible();
		if (effect != null) {
			food.effect(effect, 1.0f);
		}
		return new Properties().stacksTo(16).food(food.build());
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack drunk = stack.copyWithCount(1);
		ItemStack rest = super.finishUsingItem(stack, level, entity); // eats: hunger, effects, one used up
		if (entity instanceof ServerPlayer player) {
			CriteriaTriggers.CONSUME_ITEM.trigger(player, drunk);
			player.awardStat(Stats.ITEM_USED.get(this));
		}
		if (rest.isEmpty()) {
			return new ItemStack(Items.GLASS_BOTTLE);
		}
		if (entity instanceof Player player && !player.hasInfiniteMaterials()) {
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			if (!player.getInventory().add(bottle)) {
				player.drop(bottle, false);
			}
		}
		return rest;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return DRINK_TICKS;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	@Override
	public SoundEvent getDrinkingSound() {
		return SoundEvents.GENERIC_DRINK;
	}

	@Override
	public SoundEvent getEatingSound() {
		return SoundEvents.GENERIC_DRINK;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return ItemUtils.startUsingInstantly(level, player, hand);
	}
}
