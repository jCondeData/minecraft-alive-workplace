package io.github.jcondedata.aliveworkplace.mend;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A Weaponsmith's mending job in hand (B971), saved on the villager so a reload mid-job still puts the piece back where it
 * was found: {@code item} (as found) from {@code from}, mended with {@code units} of {@code material} out of
 * {@code materialChest}, and how far it's got ({@code phase}). The piece and the material are in the villager's bag,
 * which is saved too.
 */
public record MendingJob(ItemStack item, BlockPos from, Item material, int units, BlockPos materialChest, Phase phase) {
	/** Where a job is (TIDYING and IDLE are the smith's own, never saved with a job). */
	public enum Phase { IDLE, FETCHING, MATERIAL, MENDING, RETURNING, TIDYING }

	private static final Codec<Phase> PHASE = Codec.STRING.xmap(s -> {
		try {
			return Phase.valueOf(s);
		} catch (IllegalArgumentException e) {
			return Phase.RETURNING;
		}
	}, Phase::name);

	public static final Codec<MendingJob> CODEC = RecordCodecBuilder.create(i -> i.group(
		ItemStack.CODEC.fieldOf("item").forGetter(MendingJob::item),
		BlockPos.CODEC.fieldOf("from").forGetter(MendingJob::from),
		BuiltInRegistries.ITEM.byNameCodec().fieldOf("material").forGetter(MendingJob::material),
		Codec.INT.optionalFieldOf("units", 0).forGetter(MendingJob::units),
		BlockPos.CODEC.fieldOf("material_chest").forGetter(MendingJob::materialChest),
		PHASE.optionalFieldOf("phase", Phase.RETURNING).forGetter(MendingJob::phase)
	).apply(i, MendingJob::new));

	public MendingJob {
		from = from.immutable();
		materialChest = materialChest.immutable();
	}

	/** Equal when the same piece is mended from the same place the same way (an {@link ItemStack} has no equals of its own). */
	@Override
	public boolean equals(Object o) {
		return o instanceof MendingJob other && ItemStack.matches(item, other.item) && from.equals(other.from) && material == other.material
			&& units == other.units && materialChest.equals(other.materialChest) && phase == other.phase;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(item.getItem(), from, material, units, materialChest, phase);
	}

	public MendingJob withPhase(Phase next) {
		return next == phase ? this : new MendingJob(item, from, material, units, materialChest, next);
	}

	/** Whether {@code stack} is the piece being mended (never the material). */
	public boolean isThePiece(ItemStack stack) {
		return ItemStack.isSameItem(stack, item) && stack.isDamageableItem();
	}
}
