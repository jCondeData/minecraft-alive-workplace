package io.github.jcondedata.aliveworkplace.mine;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * A Miner makes their own pickaxe (ROADMAP 36.11): with none in hand or in their chests, they make one at their
 * workstation from what they mined themselves, three of the head's material from their bag and their own chests, and two
 * sticks, from there or from the village store, or cut from two planks. The best they can make, at any level (36.11a,
 * B1202): stone from their own cobblestone, iron from three ingots and diamond from three diamonds, which may also come
 * from the village store or a village-mate's chests (the Smelter's ingots). A miner whose pickaxe is worse than one they
 * can make makes the better one too ({@link #upgrade}); the old one goes back to their chests. Nothing is taken unless
 * all of it is there. {@code minerCraftsPickaxe} in the config turns it off: then they wait for a pickaxe as before.
 */
public final class PickaxeCrafting {
	/** The config's {@code minerCraftsPickaxe}. */
	public static boolean ENABLED = true;
	/** Of the head's material in a pickaxe. */
	public static final int HEAD = 3;
	/** Sticks in a pickaxe. */
	public static final int STICKS = 2;
	/** Ticks at the workstation making it. */
	public static final int CRAFT_TICKS = 60;

	/**
	 * What a miner can make, best last, each from its head's materials, at any level (36.11a). {@code rank} is how
	 * good it is ({@link #rank}); {@code shared}: the head may come from the village store and village-mates' chests too
	 * (ingots and diamonds), not only from what the miner mined themselves (cobblestone).
	 */
	public enum Tier {
		STONE(Items.STONE_PICKAXE, 1, false, List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.BLACKSTONE)),
		IRON(Items.IRON_PICKAXE, 2, true, List.of(Items.IRON_INGOT)),
		DIAMOND(Items.DIAMOND_PICKAXE, 3, true, List.of(Items.DIAMOND));

		public final Item pickaxe;
		public final int rank;
		public final boolean shared;
		public final List<Item> heads;

		Tier(Item pickaxe, int rank, boolean shared, List<Item> heads) {
			this.pickaxe = pickaxe;
			this.rank = rank;
			this.shared = shared;
			this.heads = heads;
		}
	}

	/**
	 * How good a pickaxe is, by what it can harvest (so a modded one counts as well): 3 obsidian (diamond, netherite), 2
	 * diamond ore (iron), 1 iron ore (stone), 0 only stone (wood, gold); -1 no pickaxe at all.
	 */
	public static int rank(ItemStack pick) {
		if (pick.isEmpty() || !pick.is(ItemTags.PICKAXES)) {
			return -1;
		}
		if (pick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) {
			return 3;
		}
		if (pick.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())) {
			return 2;
		}
		return pick.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()) ? 1 : 0;
	}

	/** Raw iron and iron ore: what the miner digs that a furnace by their workstation turns into the ingots for an iron pickaxe. */
	public static boolean isIronOre(Item item) {
		return item == Items.RAW_IRON || item == Items.IRON_ORE || item == Items.DEEPSLATE_IRON_ORE;
	}

	/** The pickaxe a miner is about to make, and the head's material. */
	public record Plan(Tier tier, Item head) {
	}

	/** What a miner lacks to make one: {@code count} more of {@code item}. */
	public record Missing(Item item, int count) {
	}

	/** What a miner lacks for a better pickaxe ({@code tier}): {@code count} more of {@code item} (36.11a). */
	public record Upgrade(Tier tier, Item item, int count) {
	}

	/** The village store's containers that aren't the miner's own (sticks and planks may come from there). */
	static List<BlockPos> store(ServerLevel level, BlockPos bench, List<BlockPos> own) {
		return io.github.jcondedata.aliveworkplace.craft.LuxuryWork.store(level, bench).stream().filter(c -> !own.contains(c)).toList();
	}

	private static long have(ServerLevel level, List<BlockPos> containers, BuilderBag bag, Item item) {
		return bag.count(item) + SupplyContainers.count(level, containers, item);
	}

	private static boolean isPlanks(Item item) {
		return item.getDefaultInstance().is(ItemTags.PLANKS);
	}

	/** Planks in the bag and the containers, by kind. */
	private static java.util.Map<Item, Long> planks(ServerLevel level, List<BlockPos> containers, BuilderBag bag) {
		java.util.Map<Item, Long> out = new java.util.LinkedHashMap<>();
		for (ItemStack stack : bag.stacks()) {
			if (!stack.isEmpty() && isPlanks(stack.getItem())) {
				out.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
			}
		}
		SupplyContainers.contents(level, containers).forEach((item, n) -> {
			if (isPlanks(item)) {
				out.merge(item, n, Long::sum);
			}
		});
		return out;
	}

	private static List<BlockPos> both(List<BlockPos> own, List<BlockPos> store) {
		List<BlockPos> all = new ArrayList<>(own);
		all.addAll(store);
		return all;
	}

	/** Whether the sticks are there, or two planks to cut them from. */
	static boolean hasSticks(ServerLevel level, List<BlockPos> own, List<BlockPos> store, BuilderBag bag) {
		List<BlockPos> all = both(own, store);
		if (have(level, all, bag, Items.STICK) >= STICKS) {
			return true;
		}
		return planks(level, all, bag).values().stream().mapToLong(Long::longValue).sum() >= 2;
	}

	/** The best pickaxe {@code villager} can make from what's at hand, or null. */
	@Nullable
	public static Plan plan(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> own, BuilderBag bag) {
		return better(level, villager, bench, own, bag, -1);
	}

	/**
	 * The best pickaxe {@code villager} can make that is better than {@code current} (36.11a: an iron one as soon as
	 * they have the ingots, at any level), or null: none better, the materials aren't there, or the switch is off.
	 */
	@Nullable
	public static Plan upgrade(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> own, BuilderBag bag, ItemStack current) {
		return better(level, villager, bench, own, bag, rank(current));
	}

	@Nullable
	private static Plan better(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> own, BuilderBag bag, int above) {
		if (!ENABLED || above >= Tier.DIAMOND.rank || !hasSticks(level, own, store(level, bench, own), bag)) {
			return null;
		}
		return head(level, villager, bench, own, bag, above);
	}

	/**
	 * Where the head of {@code tier} may come from: the miner's own chests; for ingots and diamonds the village store
	 * and village-mates' chests too (where the Smelter keeps the ingots it made of the miner's raw iron), after them.
	 */
	static List<BlockPos> sources(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> own, Tier tier) {
		if (!tier.shared) {
			return own;
		}
		java.util.LinkedHashSet<BlockPos> all = new java.util.LinkedHashSet<>(own);
		all.addAll(store(level, bench, own));
		for (io.github.jcondedata.aliveworkplace.work.Village.Stash stash : io.github.jcondedata.aliveworkplace.work.Village.stashes(level, villager, bench, null)) {
			all.addAll(stash.chests());
		}
		return new ArrayList<>(all);
	}

	@Nullable
	private static Plan head(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> own, BuilderBag bag, int above) {
		Tier[] tiers = Tier.values();
		for (int i = tiers.length - 1; i >= 0; i--) {
			Tier tier = tiers[i];
			if (tier.rank <= above) {
				break;
			}
			List<BlockPos> from = null;
			for (Item head : tier.heads) {
				if (have(level, own, bag, head) >= HEAD) {
					return new Plan(tier, head);
				}
				if (tier.shared) {
					if (from == null) {
						from = sources(level, villager, bench, own, tier);
					}
					if (have(level, from, bag, head) >= HEAD) {
						return new Plan(tier, head);
					}
				}
			}
		}
		return null;
	}

	/**
	 * What {@code villager}, whose pickaxe is {@code current}, still lacks for an iron one (36.11a, the status line):
	 * the ingots first, then the sticks; null when their pickaxe is iron or better, nothing is missing, or the switch is off.
	 */
	@Nullable
	public static Upgrade upgradeMissing(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> own, BuilderBag bag, ItemStack current) {
		if (!ENABLED || rank(current) >= Tier.IRON.rank) {
			return null;
		}
		long ingots = have(level, sources(level, villager, bench, own, Tier.IRON), bag, Items.IRON_INGOT);
		if (ingots < HEAD) {
			return new Upgrade(Tier.IRON, Items.IRON_INGOT, (int) (HEAD - ingots));
		}
		List<BlockPos> store = store(level, bench, own);
		if (!hasSticks(level, own, store, bag)) {
			return new Upgrade(Tier.IRON, Items.STICK, (int) Math.max(1, STICKS - have(level, both(own, store), bag, Items.STICK)));
		}
		return null;
	}

	/** What {@code villager} still needs to make a pickaxe (stone's head first, then the sticks), or null if nothing. */
	@Nullable
	public static Missing missing(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> own, BuilderBag bag) {
		if (head(level, villager, bench, own, bag, -1) == null) {
			long best = 0;
			for (Item item : Tier.STONE.heads) {
				best = Math.max(best, have(level, own, bag, item));
			}
			return new Missing(Items.COBBLESTONE, (int) (HEAD - best));
		}
		List<BlockPos> store = store(level, bench, own);
		if (!hasSticks(level, own, store, bag)) {
			return new Missing(Items.STICK, (int) (STICKS - have(level, both(own, store), bag, Items.STICK)));
		}
		return null;
	}

	/** Takes {@code count} of {@code item}: the bag first, then the containers in order. Returns how many it got. */
	private static int take(ServerLevel level, List<BlockPos> containers, BuilderBag bag, Item item, int count) {
		int got = bag.remove(item, count);
		if (got < count) {
			got += SupplyContainers.extract(level, containers, item, count - got);
		}
		return got;
	}

	/**
	 * Makes the pickaxe: takes the head's material and the sticks (cutting planks into sticks if need be: the spare
	 * sticks go in the bag) and returns it; empty when the materials are no longer all there (nothing is taken then).
	 */
	public static ItemStack craft(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> own, BuilderBag bag) {
		return craft(level, villager, bench, own, bag, ItemStack.EMPTY);
	}

	/** As {@link #craft(ServerLevel, Villager, BlockPos, List, BuilderBag)}, only a pickaxe better than {@code current} (36.11a). */
	public static ItemStack craft(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> own, BuilderBag bag, ItemStack current) {
		Plan plan = better(level, villager, bench, own, bag, rank(current));
		if (plan == null) {
			return ItemStack.EMPTY;
		}
		List<BlockPos> all = both(own, store(level, bench, own));
		int sticks = take(level, all, bag, Items.STICK, STICKS);
		if (sticks < STICKS) {
			int planks = 0;
			for (Item kind : planks(level, all, bag).keySet()) {
				planks += take(level, all, bag, kind, 2 - planks);
				if (planks >= 2) {
					break;
				}
			}
			sticks += 4; // two planks make four sticks
			int spare = sticks - STICKS;
			if (spare > 0) {
				ItemStack rest = bag.add(new ItemStack(Items.STICK, spare));
				if (!rest.isEmpty()) {
					Miners.store(level, own, bench, rest);
				}
			}
		}
		take(level, sources(level, villager, bench, own, plan.tier()), bag, plan.head(), HEAD);
		return new ItemStack(plan.tier().pickaxe);
	}

	private PickaxeCrafting() {
	}
}
