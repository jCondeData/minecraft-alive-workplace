package io.github.jcondedata.aliveworkplace.berry;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The Berry Breeder's planning (ROADMAP 28.9): which berry mutations, in which order, make a goal berry from the berries
 * the village already has. A berry plant next to a plant of another kind may grow a third kind (Cobblemon's
 * {@code mutations}); the chain is the fewest generations that gets there, each step planting two berries the village
 * has (or will have by then).
 */
public final class BerryChains {
	/** Two parent berries planted side by side may grow {@code result}. */
	public record Mutation(ResourceLocation a, ResourceLocation b, ResourceLocation result) {
		public boolean parents(Set<ResourceLocation> have) {
			return have.contains(a) && have.contains(b);
		}
	}

	/** Every berry mutation the berry data knows (Cobblemon's own, and data packs'). Filled in by {@code compat/cobblemon}. */
	public interface BerryData {
		Extension<BerryData> EXTENSION = new Extension<>("berry mutations");

		/** Every berry's id. */
		List<ResourceLocation> berries();

		/** Every mutation, each pair once. */
		List<Mutation> mutations();
	}

	/**
	 * The steps that make {@code goal} from {@code have}, parents first; empty when the village has it already; nothing
	 * when no chain of mutations reaches it. Of the ways there, the one with the fewest generations, and within a
	 * generation the parents with the smallest ids (so the plan is the same every time).
	 */
	public static Optional<List<Mutation>> plan(Set<ResourceLocation> have, Collection<Mutation> mutations, ResourceLocation goal) {
		if (have.contains(goal)) {
			return Optional.of(List.of());
		}
		List<Mutation> sorted = new ArrayList<>(mutations);
		sorted.sort(Comparator.comparing((Mutation m) -> m.result().toString()).thenComparing(m -> m.a().toString())
			.thenComparing(m -> m.b().toString()));
		Set<ResourceLocation> reached = new HashSet<>(have);
		Map<ResourceLocation, Mutation> madeBy = new HashMap<>();
		boolean grew = true;
		while (grew && !reached.contains(goal)) {
			grew = false;
			Set<ResourceLocation> generation = new HashSet<>(reached);
			for (Mutation m : sorted) {
				if (m.parents(generation) && !reached.contains(m.result())) {
					reached.add(m.result());
					madeBy.put(m.result(), m);
					grew = true;
				}
			}
		}
		if (!reached.contains(goal)) {
			return Optional.empty();
		}
		LinkedHashSet<Mutation> steps = new LinkedHashSet<>();
		addSteps(goal, madeBy, steps);
		return Optional.of(List.copyOf(steps));
	}

	private static void addSteps(ResourceLocation berry, Map<ResourceLocation, Mutation> madeBy, LinkedHashSet<Mutation> steps) {
		Mutation m = madeBy.get(berry);
		if (m == null || steps.contains(m)) {
			return;
		}
		addSteps(m.a(), madeBy, steps);
		addSteps(m.b(), madeBy, steps);
		steps.add(m);
	}

	/** The next step towards {@code goal}: the first of {@link #plan}, or nothing (already there, or out of reach). */
	public static Optional<Mutation> next(Set<ResourceLocation> have, Collection<Mutation> mutations, ResourceLocation goal) {
		return plan(have, mutations, goal).flatMap(steps -> steps.stream().findFirst());
	}

	/** For the berry book: the pairs that make {@code berry}, in id order. */
	public static List<Mutation> madeFrom(Collection<Mutation> mutations, ResourceLocation berry) {
		return mutations.stream().filter(m -> m.result().equals(berry))
			.sorted(Comparator.comparing((Mutation m) -> m.a().toString()).thenComparing(m -> m.b().toString())).toList();
	}

	private BerryChains() {
	}
}
