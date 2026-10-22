package io.github.jcondedata.aliveworkplace.mixin;

import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.work.BedLadders;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A free bed up a ladder can be claimed (B304). Vanilla claims a bed only when it finds a path to it, and its paths have
 * no ladders, so a villager without a bed never got one of the Terrace's (each bedroom is up a ladder) however many stood
 * free. When vanilla's path to the beds it found falls short, {@link BedLadders#pathUpALadder} looks for one of them the
 * villager can walk to the foot of a ladder for and climb to; {@code BedLadders} then takes them up at bedtime.
 */
@Mixin(AcquirePoi.class)
abstract class AcquirePoiMixin {
	@Inject(method = "findPathToPois", at = @At("RETURN"), cancellable = true)
	private static void aliveworkplace$bedUpALadder(Mob mob, Set<Pair<Holder<PoiType>, BlockPos>> pois, CallbackInfoReturnable<Path> cir) {
		Path path = cir.getReturnValue();
		if ((path == null || !path.canReach()) && mob instanceof Villager villager) {
			Path up = BedLadders.pathUpALadder(villager, pois);
			if (up != null) {
				cir.setReturnValue(up);
			}
		}
	}
}
