package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Furnicrafter's shift at CobbleFurnies' Furnicrafter (ROADMAP 38.12): a crafter like the Carpenter, who makes the
 * furniture pieces ({@link FurnitureRecipes}) a builder nearby is waiting for (a furnished home's chairs, tables and
 * lamps: 37.x) or a storehouse's stock order asks for, from what the builder's chests and the village's store hold. A
 * material that's short is made first with the crafting table's recipes (sticks from planks). Without a furniture mod
 * there's no Furnicrafter; config {@code furnicrafters} off, those hired stand idle.
 */
public class FurnicrafterWork extends CrafterWork {
	/** Config switch {@code furnicrafters} (behind M38's gate). */
	public static boolean ENABLED = true;

	@Nullable
	private MinecraftServer server;

	public FurnicrafterWork() {
		super(Crafting.Kind.CRAFTING, "furnicrafter", "crafter", true);
	}

	/** Whether {@code villager} is a grown-up Furnicrafter (never without CobbleFurnies). */
	public static boolean isFurnicrafter(Villager villager) {
		return !villager.isBaby() && ModVillagers.isFurnicrafter(villager.getVillagerData().getProfession());
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return ENABLED && isFurnicrafter(villager) && super.checkExtraStartConditions(level, villager);
	}

	@Override
	@Nullable
	protected Job choose(ServerLevel level, Villager villager, BlockPos station) {
		server = level.getServer();
		return super.choose(level, villager, station);
	}

	@Override
	protected boolean wants(Item item) {
		return server != null && FurnitureRecipes.makes(server, item);
	}

	@Override
	@Nullable
	protected Crafting.Plan planFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		return plan(level, item, count, usable);
	}

	/**
	 * What's short of a piece (37.14a): by its first recipe, each material not in the chests, or what that material is
	 * made from with the crafting table's recipes (logs for the planks), for the builder's shortage line.
	 */
	@Override
	protected Map<Item, Integer> lackFor(ServerLevel level, Item item, int count, Map<Item, Long> usable) {
		List<FurnitureRecipes.Recipe> recipes = FurnitureRecipes.making(level.getServer(), item);
		if (recipes.isEmpty()) {
			return Map.of();
		}
		FurnitureRecipes.Recipe recipe = recipes.get(0);
		int times = Math.max(1, (Math.min(count, Crafting.MAX_CRAFTS) + Math.max(1, recipe.result().getCount()) - 1) / Math.max(1, recipe.result().getCount()));
		Map<Item, Integer> lack = new LinkedHashMap<>();
		for (Map.Entry<Item, Integer> material : recipe.materials().entrySet()) {
			long short_ = (long) material.getValue() * times - usable.getOrDefault(material.getKey(), 0L);
			if (short_ <= 0) {
				continue;
			}
			Map<Item, Integer> under = Crafting.recipesFor(level, Crafting.Kind.CRAFTING, material.getKey()).isEmpty() ? Map.of()
				: Crafting.lacking(level, Crafting.Kind.CRAFTING, material.getKey(), (int) short_, usable);
			if (under.isEmpty()) {
				lack.merge(material.getKey(), (int) short_, Integer::sum);
			} else {
				under.forEach((k, n) -> lack.merge(k, n, Integer::sum));
			}
		}
		return lack;
	}

	@Override
	protected void workEffects(ServerLevel level, BlockPos station) {
		level.playSound(null, station, level.random.nextBoolean() ? SoundEvents.WOOD_HIT : SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f,
			0.9f + level.random.nextFloat() * 0.2f);
	}

	@Override
	protected String partnerCue() {
		return "craft";
	}

	@Override
	@Nullable
	protected Component idleLine(Villager villager) {
		return Component.translatable("message.aliveworkplace.furnicrafter.state.idle");
	}

	/**
	 * How to make {@code count} of furniture piece {@code target} from {@code usable}: as many as there are materials for
	 * (all of them, or half, down to one), by the first of its recipes that can be made; short materials made first with
	 * the crafting table's recipes. Null if not even one can be made.
	 */
	@Nullable
	public static Crafting.Plan plan(ServerLevel level, Item target, int count, Map<Item, Long> usable) {
		for (FurnitureRecipes.Recipe recipe : FurnitureRecipes.making(level.getServer(), target)) {
			int per = Math.max(1, recipe.result().getCount());
			for (int times = Math.max(1, (count + per - 1) / per); times >= 1; times /= 2) {
				Crafting.Plan plan = plan(level, recipe, times, usable);
				if (plan != null) {
					return plan;
				}
			}
		}
		return null;
	}

	/** {@code recipe} made {@code times} times from {@code usable}, or null if the materials can't all be had. */
	@Nullable
	static Crafting.Plan plan(ServerLevel level, FurnitureRecipes.Recipe recipe, int times, Map<Item, Long> usable) {
		Map<Item, Long> pool = new HashMap<>(usable);
		Map<Item, Integer> takes = new LinkedHashMap<>();
		Map<Item, Integer> makes = new LinkedHashMap<>();
		List<Crafting.Step> steps = new ArrayList<>();
		Map<Item, Integer> shortOf = new LinkedHashMap<>();
		// what's there first, for every material; then what's short is made from what's left
		for (Map.Entry<Item, Integer> material : recipe.materials().entrySet()) {
			int need = material.getValue() * times;
			int use = (int) Math.min(need, pool.getOrDefault(material.getKey(), 0L));
			if (use > 0) {
				takes.merge(material.getKey(), use, Integer::sum);
				pool.merge(material.getKey(), (long) -use, Long::sum);
			}
			if (need > use) {
				shortOf.put(material.getKey(), need - use);
			}
		}
		for (Map.Entry<Item, Integer> missing : shortOf.entrySet()) {
			Crafting.Plan sub = Crafting.plan(level, Crafting.Kind.CRAFTING, missing.getKey(), missing.getValue(), positive(pool));
			if (sub == null || sub.count() < missing.getValue()) {
				return null;
			}
			sub.takes().forEach((item, n) -> {
				takes.merge(item, n, Integer::sum);
				pool.merge(item, (long) -n, Long::sum);
			});
			steps.addAll(sub.steps());
			sub.makes().forEach((item, n) -> {
				int left = item == missing.getKey() ? n - missing.getValue() : n; // the rest is used for the piece
				if (left > 0) {
					makes.merge(item, left, Integer::sum);
				}
			});
		}
		ItemStack out = recipe.result().copyWithCount(Math.max(1, recipe.result().getCount()));
		steps.add(new Crafting.Step(new LinkedHashMap<>(recipe.materials()), out, times));
		makes.merge(out.getItem(), out.getCount() * times, Integer::sum);
		return new Crafting.Plan(out.getItem(), makes.get(out.getItem()), steps, takes, makes);
	}

	private static Map<Item, Long> positive(Map<Item, Long> pool) {
		Map<Item, Long> out = new HashMap<>();
		pool.forEach((item, n) -> {
			if (n > 0) {
				out.put(item, n);
			}
		});
		return out;
	}
}
