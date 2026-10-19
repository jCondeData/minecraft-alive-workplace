package io.github.jcondedata.aliveworkplace.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A level's entity manager, so a raider or bandit that another mod's blocklist refused at {@code addEntity} can be put
 * in the world directly (B208: Cobbleverse's MobsBeGone refuses pillagers, zombies and the rest; see
 * {@code threat/HostileSpawns}).
 */
@Mixin(ServerLevel.class)
public interface ServerLevelEntitiesAccessor {
	@Accessor("entityManager")
	PersistentEntitySectionManager<Entity> aliveworkplace$entities();
}
