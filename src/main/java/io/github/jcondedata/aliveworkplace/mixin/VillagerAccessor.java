package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets builders level up from work the same way vanilla villagers level up from trading. */
@Mixin(Villager.class)
public interface VillagerAccessor {
	/** Vanilla: level + 1 and new trades. */
	@Invoker("increaseMerchantCareer")
	void aliveworkplace$increaseMerchantCareer();

	/** Vanilla: the player's special prices (reputation, Hero of the Village) on every offer. */
	@Invoker("updateSpecialPrices")
	void aliveworkplace$updateSpecialPrices(net.minecraft.world.entity.player.Player player);
}
