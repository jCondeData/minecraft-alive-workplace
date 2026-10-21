package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Whole-chain crafting for the jobs that craft for the builders (ROADMAP 37.14a, the retired Crafter's planning moved
 * into them): the Carpenter, the Mason, the Librarian's scribe, the Tinkerer, the Toolsmith, the Dyer and the
 * Furnicrafter each plan their own kind of thing all the way down (logs into planks into slabs into a barrel), check every
 * step against the stock before a thing is taken, make it a bagful at a time, step after step, and save the job on the
 * villager ({@link CrafterJob}) so a chain half made carries on after a restart. What they can't make for want of a raw
 * material is said, over their head and in the builder's own line ({@link #builderLack}). Behind {@code Expansions.M37}
 * and config {@code crafters}; off, each job makes what it can in one go, as before 2.1.
 */
public final class Chains {
	/** Config {@code crafters} (and Milestone 37 open): the jobs plan whole chains. */
	public static boolean ENABLED = true;
	/** How long what a job found short stays in a builder's line, unless found again. */
	static final long LACK_TICKS = 2400;

	private record Lack(Component line, long until) {
	}

	/** What the crafting jobs last found short for each builder's site, by builder. */
	private static final Map<UUID, Lack> BUILDER_LACKS = new HashMap<>();

	/**
	 * {@code kind}'s whole chain for {@code count} of {@code item} out of {@code usable}: as many as fit a bag and there are
	 * makings for (all of them, half, down to one). Null if not even one can be made (or the tick's planning is spent).
	 */
	@Nullable
	public static Crafting.Plan plan(ServerLevel level, Crafting.Kind kind, Item item, int count, Map<Item, Long> usable) {
		for (int n = Math.min(Math.max(1, count), Crafting.MAX_CRAFTS); n >= 1; n /= 2) {
			Crafting.Plan plan = Crafting.chain(level, kind, item, n, usable);
			if (plan == null && Crafting.planningSpent(level)) {
				return null; // asked again next time
			}
			if (CrafterWork.fits(plan)) {
				return plan;
			}
		}
		return null;
	}

	/** "short of 2 Oak Log to make 2 Barrel", or null if nothing raw is short (or no recipe of {@code kind} makes it). */
	@Nullable
	public static Component lack(ServerLevel level, Crafting.Kind kind, Item item, int count, Map<Item, Long> usable) {
		if (Crafting.planningSpent(level)) {
			return null;
		}
		Map<Item, Integer> short_ = Crafting.lacking(level, kind, item, Math.max(1, count), usable);
		return short_.isEmpty() ? null : lackLine(short_, Math.max(1, count), item);
	}

	/** "short of 2 Oak Log, 1 Stick to make 4 Barrel". */
	public static Component lackLine(Map<Item, Integer> lack, int count, Item target) {
		MutableComponent list = Component.empty();
		boolean first = true;
		for (Map.Entry<Item, Integer> e : lack.entrySet()) {
			if (!first) {
				list.append(", ");
			}
			first = false;
			list.append(Component.translatable("message.aliveworkplace.chain.lack_item", e.getValue(), e.getKey().getDescription()));
		}
		return Component.translatable("message.aliveworkplace.chain.lacks", list, count, target.getDescription());
	}

	/** Notes in {@code builder}'s line that the crafting jobs are short of something to make what they're waiting for. */
	static void noteLack(Villager builder, Component line, long now) {
		synchronized (BUILDER_LACKS) {
			BUILDER_LACKS.put(builder.getUUID(), new Lack(line, now + LACK_TICKS));
		}
	}

	/** Takes the note off {@code builder}'s line (a job found the makings after all). */
	static void clearLack(Villager builder) {
		synchronized (BUILDER_LACKS) {
			BUILDER_LACKS.remove(builder.getUUID());
		}
	}

	/** What the crafting jobs last found short for {@code builder}'s build ("short of 2 Oak Log to make 2 Barrel"), or null. */
	@Nullable
	public static Component builderLack(Villager builder, long now) {
		synchronized (BUILDER_LACKS) {
			Lack lack = BUILDER_LACKS.get(builder.getUUID());
			if (lack == null || lack.until() < now) {
				BUILDER_LACKS.remove(builder.getUUID());
				return null;
			}
			return lack.line();
		}
	}

	/**
	 * Takes one step's makings out of the bag and puts what it makes in; returns what didn't fit, or null (nothing changed)
	 * if the makings aren't all there.
	 */
	@Nullable
	static List<ItemStack> makeStep(BuilderBag bag, Crafting.Step step) {
		for (Map.Entry<Item, Integer> e : step.in().entrySet()) {
			if (bag.count(e.getKey()) < e.getValue() * step.times()) {
				return null;
			}
		}
		List<ItemStack> extra = new ArrayList<>();
		step.in().forEach((item, n) -> {
			bag.remove(item, n * step.times());
			Item rest = item.getCraftingRemainingItem();
			if (rest != null) {
				int over = bag.addAll(rest, n * step.times());
				if (over > 0) {
					extra.add(new ItemStack(rest, over));
				}
			}
		});
		int over = bag.addAll(step.out().getItem(), step.out().getCount() * step.times());
		if (over > 0) {
			extra.add(new ItemStack(step.out().getItem(), over));
		}
		return extra;
	}

	private Chains() {
	}
}
