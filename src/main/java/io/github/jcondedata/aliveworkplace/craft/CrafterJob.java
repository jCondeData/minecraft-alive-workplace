package io.github.jcondedata.aliveworkplace.craft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * A Crafter's job in hand (ROADMAP 37.14), saved on the villager so a chain half made carries on after a restart: what's
 * made for whom ({@code deliverTo}, the chests by the asker's workstation, outside {@code area}), the chain's steps,
 * what it takes from the stores ({@code takes}, fetched from {@code sources}), how far it's got ({@code phase} and
 * {@code step}, the next step to make) and the request it answers ({@code claim}). The ingredients and what's made so far
 * are in the villager's bag, which is saved too.
 */
public record CrafterJob(BlockPos deliverTo, Optional<BoundingBox> area, Item target, int count, List<Crafting.Step> steps,
						 Map<Item, Integer> takes, List<BlockPos> sources, String claim, Phase phase, int step) {
	/** Where a job is: fetching the makings, at the bench (step by step), or taking it to the asker. */
	public enum Phase { FETCHING, CRAFTING, DELIVERING }

	private static final Codec<Item> ITEM = BuiltInRegistries.ITEM.byNameCodec();
	private static final Codec<Map<Item, Integer>> ITEMS = Codec.unboundedMap(ITEM, Codec.INT);
	static final Codec<Crafting.Step> STEP = RecordCodecBuilder.create(i -> i.group(
		ITEMS.fieldOf("in").forGetter(Crafting.Step::in),
		ItemStack.CODEC.fieldOf("out").forGetter(Crafting.Step::out),
		Codec.INT.fieldOf("times").forGetter(Crafting.Step::times)
	).apply(i, Crafting.Step::new));
	private static final Codec<Phase> PHASE = Codec.STRING.xmap(s -> {
		try {
			return Phase.valueOf(s);
		} catch (IllegalArgumentException e) {
			return Phase.FETCHING;
		}
	}, Phase::name);

	public static final Codec<CrafterJob> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.fieldOf("deliver_to").forGetter(CrafterJob::deliverTo),
		BoundingBox.CODEC.optionalFieldOf("area").forGetter(CrafterJob::area),
		ITEM.fieldOf("target").forGetter(CrafterJob::target),
		Codec.INT.optionalFieldOf("count", 0).forGetter(CrafterJob::count),
		STEP.listOf().optionalFieldOf("steps", List.of()).forGetter(CrafterJob::steps),
		ITEMS.optionalFieldOf("takes", Map.of()).forGetter(CrafterJob::takes),
		BlockPos.CODEC.listOf().optionalFieldOf("sources", List.of()).forGetter(CrafterJob::sources),
		Codec.STRING.optionalFieldOf("claim", "").forGetter(CrafterJob::claim),
		PHASE.optionalFieldOf("phase", Phase.FETCHING).forGetter(CrafterJob::phase),
		Codec.INT.optionalFieldOf("step", 0).forGetter(CrafterJob::step)
	).apply(i, CrafterJob::new));

	public CrafterJob {
		steps = List.copyOf(steps);
		takes = new LinkedHashMap<>(takes);
		sources = List.copyOf(sources);
	}

	/** A new job for {@code plan}, fetching first. */
	public static CrafterJob of(BlockPos deliverTo, @Nullable BoundingBox area, Crafting.Plan plan, List<BlockPos> sources, String claim) {
		return new CrafterJob(deliverTo.immutable(), Optional.ofNullable(area), plan.target(), plan.count(), new ArrayList<>(plan.steps()),
			plan.takes(), sources.stream().map(BlockPos::immutable).toList(), claim, Phase.FETCHING, 0);
	}

	public CrafterJob withPhase(Phase next) {
		return new CrafterJob(deliverTo, area, target, count, steps, takes, sources, claim, next, step);
	}

	public CrafterJob nextStep() {
		return new CrafterJob(deliverTo, area, target, count, steps, takes, sources, claim, phase, step + 1);
	}

	@Nullable
	public BoundingBox areaOrNull() {
		return area.orElse(null);
	}
}
