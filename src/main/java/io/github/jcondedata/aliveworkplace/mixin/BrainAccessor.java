package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * When a brain last looked at its schedule: vanilla looks at most once a second, so a new brain given our longer hours
 * right after vanilla's own look (VillagerMixin) would keep vanilla's hours' activity for up to a second (B220).
 */
@Mixin(Brain.class)
public interface BrainAccessor {
	@Accessor("lastScheduleUpdate")
	void aliveworkplace$setLastScheduleUpdate(long gameTime);
}
