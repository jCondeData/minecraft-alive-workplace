package io.github.jcondedata.aliveworkplace.mechanic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A worker's trip to a Create machine (ROADMAP 39.3, saved on the worker as {@code machine_job}): which machine, by which
 * machine-work file, how far along ({@link Stage}) and what they carry. Every field is optional: a partial record loads
 * as a trip that goes on from its stage, and what's carried is never lost (it goes to the machine or back to the store).
 */
public record MachineJob(BlockPos machine, ResourceLocation file, Stage stage, List<ItemStack> carried) {
	/** Where the trip is: to the store for the input, to the machine with it, or back to the store with the output. */
	public enum Stage {
		FETCH, FEED, RETURN;

		static final Codec<Stage> CODEC = Codec.STRING.xmap(s -> {
			for (Stage st : values()) {
				if (st.name().equalsIgnoreCase(s)) {
					return st;
				}
			}
			return RETURN;
		}, s -> s.name().toLowerCase(java.util.Locale.ROOT));
	}

	public static final Codec<MachineJob> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.optionalFieldOf("machine", BlockPos.ZERO).forGetter(MachineJob::machine),
		ResourceLocation.CODEC.optionalFieldOf("file", ResourceLocation.fromNamespaceAndPath("aliveworkplace", "none")).forGetter(MachineJob::file),
		Stage.CODEC.optionalFieldOf("stage", Stage.RETURN).forGetter(MachineJob::stage),
		ItemStack.CODEC.listOf().optionalFieldOf("carried", List.of()).forGetter(MachineJob::carried)
	).apply(i, MachineJob::new));

	public MachineJob {
		carried = carried.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
	}

	/** Equal when the same trip carries the same stacks (an {@link ItemStack} has no equals of its own). */
	@Override
	public boolean equals(Object o) {
		return o instanceof MachineJob other && machine.equals(other.machine) && file.equals(other.file) && stage == other.stage
			&& ItemStack.listMatches(carried, other.carried);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(machine, file, stage, carried.size());
	}

	public MachineJob with(Stage next, List<ItemStack> nowCarried) {
		return new MachineJob(machine, file, next, nowCarried);
	}
}
