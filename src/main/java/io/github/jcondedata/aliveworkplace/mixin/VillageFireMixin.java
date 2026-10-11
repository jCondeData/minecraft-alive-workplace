package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The village fire's spread (ROADMAP 32.16): in a village the game's own fire burns, and spreads beside, only blocks the
 * builders placed ({@link VillageFire#mayBurn}), so a player's own build next to a burning house never catches; a fire
 * with nothing of the builders' left to burn goes out once it's old, instead of smouldering on a player's roof forever.
 */
@Mixin(FireBlock.class)
public abstract class VillageFireMixin {
	@Unique
	private static final ThreadLocal<BlockState> aliveworkplace$burning = new ThreadLocal<>();

	@Shadow
	private int getIgniteOdds(BlockState state) {
		throw new AssertionError();
	}

	@Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$onlyBuildersBlocksBurn(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		aliveworkplace$burning.remove();
		if (level instanceof ServerLevel server && VillageFire.guarded(server, pos)) {
			if (!VillageFire.mayBurn(server, pos)) {
				ci.cancel();
				return;
			}
			aliveworkplace$burning.set(level.getBlockState(pos));
		}
	}

	@Inject(method = "checkBurnOut", at = @At("RETURN"))
	private void aliveworkplace$countBurned(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		BlockState before = aliveworkplace$burning.get();
		aliveworkplace$burning.remove();
		if (before != null && level instanceof ServerLevel server && level.getBlockState(pos) != before) {
			VillageFire.burned(server, pos);
		}
	}

	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$onlyBesideBuildersBlocks(LevelReader reader, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (!(reader instanceof ServerLevel level) || !VillageFire.guarded(level, pos)) {
			return;
		}
		if (!reader.isEmptyBlock(pos)) {
			cir.setReturnValue(0);
			return;
		}
		int odds = 0;
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d).immutable();
			int o = getIgniteOdds(reader.getBlockState(n));
			if (o > odds && VillageFire.mayBurn(level, n)) {
				odds = o;
			}
		}
		cir.setReturnValue(odds);
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void aliveworkplace$burnsOut(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (state.getValue(FireBlock.AGE) >= FireBlock.MAX_AGE && random.nextInt(4) == 0 && VillageFire.guarded(level, pos)
			&& !level.getBlockState(pos.below()).is(level.dimensionType().infiniburn()) && !VillageFire.anyMayBurn(level, pos)) {
			level.removeBlock(pos, false);
			ci.cancel();
		}
	}
}
