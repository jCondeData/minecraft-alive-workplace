package io.github.jcondedata.aliveworkplace.mixin;

import java.util.Map;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What an axe strips into (log → stripped log; Fabric's registry adds modded woods to the same map). */
@Mixin(AxeItem.class)
public interface AxeItemAccessor {
	@Accessor("STRIPPABLES")
	static Map<Block, Block> aliveworkplace$strippables() {
		throw new AssertionError();
	}
}
