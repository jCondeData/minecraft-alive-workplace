package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.DrinkItem;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.threat.Plague;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The Remedy (ROADMAP 32.18): the Physician's cure, crafted from a glass bottle, a glistering melon slice, a honey
 * bottle and a fermented spider eye. Given to a villager (right-click, or by a Physician) it cures any illness, hexes and
 * the plague included, and keeps them well for ten days ({@link Plague#cure}); a player who drinks it loses poison,
 * wither, hunger and nausea, and gets the bottle back.
 */
public class RemedyItem extends DrinkItem {
	public RemedyItem(Properties properties) {
		super(properties);
	}

	/** Its properties: drinkable at any time, no food in it, 16 to a stack. */
	public static Properties properties() {
		return DrinkItem.drink(0, 0f, null);
	}

	public static boolean isRemedy(ItemStack stack) {
		return stack.getItem() instanceof RemedyItem;
	}

	public static void init() {
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> give(player, level, hand, entity));
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!level.isClientSide) {
			entity.removeEffect(MobEffects.POISON);
			entity.removeEffect(MobEffects.WITHER);
			entity.removeEffect(MobEffects.HUNGER);
			entity.removeEffect(MobEffects.CONFUSION);
		}
		return super.finishUsingItem(stack, level, entity);
	}

	/** A player gives a villager the Remedy in their hand: cured and kept well, one used up, the bottle back. */
	public static InteractionResult give(Player player, Level level, InteractionHand hand, Entity entity) {
		ItemStack stack = player.getItemInHand(hand);
		if (!isRemedy(stack) || !(entity instanceof Villager villager) || !villager.isAlive()) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		boolean wasIll = Plague.cure(server, villager);
		Chat.actionBar(player, Component.translatable(wasIll ? "message.aliveworkplace.remedy.cured" : "message.aliveworkplace.remedy.kept_well",
			villager.getDisplayName()).withStyle(ChatFormatting.GREEN));
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			if (!player.getInventory().add(bottle)) {
				player.drop(bottle, false);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
