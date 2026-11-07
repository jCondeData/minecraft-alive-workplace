package io.github.jcondedata.aliveworkplace.mend;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A Weaponsmith's mending job in hand, saved on the villager (B971) so the piece still goes back where it was found
 * after a chunk unload or a restart: {@code item} (as found) from the chest at {@code from}, mended with {@code units} of
 * {@code material} out of {@code materialChest}, and how far it's got ({@code phase}). The piece and the material are in
 * the villager's bag, which is saved too.
 */
public record MendJob(ItemStack item, BlockPos from, Item material, int units, BlockPos materialChest, Phase phase) {
	/** Where a job is: fetching the piece, fetching the material, at the grindstone, or taking the piece back. */
	public enum Phase { FETCHING, MATERIAL, MENDING, RETURNING }

	private static final Codec<Phase> PHASE = Codec.STRING.xmap(s -> {
		try {
			return Phase.valueOf(s);
		} catch (IllegalArgumentException e) {
			return Phase.RETURNING;
		}
	}, Phase::name);

	public static final Codec<MendJob> CODEC = RecordCodecBuilder.create(i -> i.group(
		ItemStack.CODEC.fieldOf("item").forGetter(MendJob::item),
		BlockPos.CODEC.fieldOf("from").forGetter(MendJob::from),
		BuiltInRegistries.ITEM.byNameCodec().fieldOf("material").forGetter(MendJob::material),
		Codec.INT.optionalFieldOf("units", 0).forGetter(MendJob::units),
		BlockPos.CODEC.fieldOf("material_chest").forGetter(MendJob::materialChest),
		PHASE.optionalFieldOf("phase", Phase.RETURNING).forGetter(MendJob::phase)
	).apply(i, MendJob::new));

	public MendJob {
		item = item.copy();
		from = from.immutable();
		materialChest = materialChest.immutable();
	}

	public MendJob withPhase(Phase next) {
		return next == phase ? this : new MendJob(item, from, material, units, materialChest, next);
	}
}
