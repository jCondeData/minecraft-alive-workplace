package io.github.jcondedata.aliveworkplace.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * An entity's passengers, to put a villager at the oars of a boat with a player in it (the game always seats a player
 * first, and the first passenger steers; see {@code mc/Boats.atOars}); and its removal undone, for a raider another
 * mod's blocklist discarded before it was ever in the world (B208, {@code threat/HostileSpawns}).
 */
@Mixin(Entity.class)
public interface EntityAccessor {
	@Accessor("passengers")
	void aliveworkplace$setPassengers(ImmutableList<Entity> passengers);

	@Invoker("unsetRemoved")
	void aliveworkplace$unsetRemoved();
}
