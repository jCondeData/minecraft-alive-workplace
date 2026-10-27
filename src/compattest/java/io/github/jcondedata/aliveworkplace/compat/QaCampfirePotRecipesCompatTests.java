package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.CobblemonRecipeTypes;
import io.github.jcondedata.aliveworkplace.craft.Chefs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane d, 2026-10-27) of ROADMAP 38.11 with the real Cobblemon, from the item's Done when ("every campfire pot recipe
 * the installed mods register can be cooked (found by recipe type, not a fixed list), the cook only makes what the store
 * has ingredients for"), filling what the other campfire pot tests leave out:
 * <ul>
 * <li>RECIPE by recipe, not dish by dish: for every recipe of Cobblemon's two pot types in the recipe manager (plain
 * results), the Chef's kitchen planner makes its dish from exactly that recipe's makings (except a recipe whose makings
 * are all dishes he keeps, which the wiki says is not his), and with one making short it
 * never plans to take more than the store holds;</li>
 * <li>the Kitchen Stove broken while he carries the makings: nothing cooked after it is gone, no making lost;</li>
 * <li>a player taking the rest of the makings while he fetches: no dish, and what he took is not lost.</li>
 * </ul>
 * Strawberry Sweet (red dye, a honey bottle, sugar) is the dish for the job tests: no making of another pot recipe.
 */
public class QaCampfirePotRecipesCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos OTHER_CHEST = new BlockPos(5, 2, 2);
	private static final BlockPos CHEF_AT = new BlockPos(3, 2, 3);

	private static Item sweet() {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "strawberry_sweet"));
		if (item == Items.AIR) {
			throw new AssertionError("no cobblemon:strawberry_sweet");
		}
		return item;
	}

	private static Container chest(GameTestHelper helper, BlockPos at, ItemStack... items) {
		helper.setBlock(at, Blocks.CHEST);
		Container chest = helper.getBlockEntity(at);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i]);
		}
		return chest;
	}

	private static Villager chef(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.KITCHEN_STOVE);
		Villager chef = helper.spawn(EntityType.VILLAGER, CHEF_AT);
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(STATION), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		return chef;
	}

	/** Every {@code item} there is: in the chests, in the chef's bag and inventory, and on the ground near the stove. */
	private static int everywhere(GameTestHelper helper, Villager chef, Item item, Container... chests) {
		int n = 0;
		for (Container chest : chests) {
			n += chest.countItem(item);
		}
		n += ModAttachments.BUILDER_BAG.getOrCreate(chef).count(item) + chef.getInventory().countItem(item);
		AABB near = new AABB(helper.absolutePos(STATION)).inflate(12);
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, near)) {
			if (drop.getItem().is(item)) {
				n += drop.getItem().getCount();
			}
		}
		return n;
	}

	/** A pot recipe of the recipe manager: its id, its plain result and its makings (one item per ingredient). */
	private record PotCase(ResourceLocation id, ItemStack result, Map<Item, Long> makings) {
	}

	/** Every recipe of Cobblemon's two Campfire Pot types with a plain result, read from the recipe manager itself. */
	private static List<PotCase> potCases(GameTestHelper helper, List<String> unusable, List<String> dishesOnly) {
		// the dishes he keeps (the wiki: a recipe whose makings are all of these is not his, B435/B459)
		java.util.Set<Item> kept = new java.util.HashSet<>(Chefs.menu(helper.getLevel()));
		kept.addAll(Chefs.potDishes(helper.getLevel()));
		List<RecipeHolder<?>> holders = new ArrayList<>();
		var manager = helper.getLevel().getRecipeManager();
		holders.addAll(manager.getAllRecipesFor(CobblemonRecipeTypes.INSTANCE.getCOOKING_POT_COOKING()));
		holders.addAll(manager.getAllRecipesFor(CobblemonRecipeTypes.INSTANCE.getCOOKING_POT_SHAPELESS()));
		List<PotCase> out = new ArrayList<>();
		for (RecipeHolder<?> holder : holders) {
			ItemStack result = holder.value().getResultItem(helper.getLevel().registryAccess());
			if (result.isEmpty() || !result.getComponentsPatch().isEmpty()) {
				continue; // a result with its own components is left out (the wiki)
			}
			Map<Item, Long> makings = new LinkedHashMap<>();
			boolean usable = true;
			for (Ingredient ingredient : holder.value().getIngredients()) {
				if (ingredient.isEmpty()) {
					continue;
				}
				ItemStack[] options = ingredient.getItems();
				if (options.length == 0 || options[0].isEmpty()) {
					usable = false; // an empty tag: nobody can cook it
					break;
				}
				makings.merge(options[0].getItem(), 1L, Long::sum);
			}
			if (usable && !makings.isEmpty() && kept.containsAll(makings.keySet())) {
				dishesOnly.add(holder.id().toString());
			} else if (usable && !makings.isEmpty()) {
				out.add(new PotCase(holder.id(), result, makings));
			} else {
				unusable.add(holder.id().toString());
			}
		}
		return out;
	}

	/** The plan's takes never exceed what {@code usable} holds, and take nothing it doesn't hold. */
	private static String overTakes(Crafting.Plan plan, Map<Item, Long> usable) {
		for (Map.Entry<Item, Integer> e : plan.takes().entrySet()) {
			if (e.getValue() > usable.getOrDefault(e.getKey(), 0L)) {
				return "takes " + e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()) + " of " + usable.getOrDefault(e.getKey(), 0L);
			}
		}
		return null;
	}

	/**
	 * The Done when, recipe by recipe: each Campfire Pot recipe the recipe manager holds (by type, compared with nothing
	 * fixed) can be cooked by the Chef's kitchen from exactly its makings, making at least its result's count; one making
	 * short, he never plans to take what the store doesn't hold. One recipe a tick, so no tick's planning budget runs out.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '1200' '"qa_pot_every_recipe"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "qa_pot_every_recipe")
	public void qaEveryPotRecipeIsCookableFromExactlyItsMakings(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<String> unusable = new ArrayList<>();
		List<String> dishesOnly = new ArrayList<>();
		List<PotCase> cases = potCases(helper, unusable, dishesOnly);
		helper.assertTrue(cases.size() >= 80, "only " + cases.size() + " Campfire Pot recipes with plain results in the recipe manager");
		boolean pack = cases.stream().anyMatch(c -> c.id().getNamespace().equals("aliveworkplace_compat"));
		helper.assertTrue(pack, "the test pack's own pot recipe (a data pack's) isn't in the recipe manager");
		var shapedType = CobblemonRecipeTypes.INSTANCE.getCOOKING_POT_COOKING();
		long shaped = cases.stream().filter(c -> helper.getLevel().getRecipeManager().byKey(c.id())
			.map(h -> h.value().getType() == shapedType).orElse(false)).count();
		helper.assertTrue(shaped > 0, "no shaped (cobblemon:cooking_pot) recipe among the cases; unusable: " + unusable);
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA 38.11: {} pot recipes ({} shaped), {} with no makings: {}; {} of only kept dishes: {}",
			cases.size(), shaped, unusable.size(), unusable, dishesOnly.size(), dishesOnly);
		helper.assertTrue(dishesOnly.size() < 20, dishesOnly.size() + " pot recipes count as only kept dishes: " + dishesOnly);
		List<String> failures = new ArrayList<>();
		int[] next = {0};
		int[] retries = {0};
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0]) {
				return;
			}
			if (next[0] >= cases.size()) {
				done[0] = true;
				if (!failures.isEmpty()) {
					helper.fail(failures.size() + " of " + cases.size() + " pot recipes fail: " + String.join("; ", failures.subList(0, Math.min(12, failures.size()))));
				} else {
					helper.succeed();
				}
				return;
			}
			if (Crafting.planningSpent(helper.getLevel())) {
				return; // another crafter used this tick's planning: try next tick
			}
			PotCase c = cases.get(next[0]);
			Item dish = c.result().getItem();
			Crafting.Plan exact = Crafting.plan(helper.getLevel(), Crafting.Kind.KITCHEN, dish, 1, c.makings());
			if (exact == null && Crafting.planningSpent(helper.getLevel()) && retries[0]++ < 20) {
				return; // spent mid-plan: the same recipe again next tick
			}
			retries[0] = 0;
			String name = c.id() + " (" + BuiltInRegistries.ITEM.getKey(dish) + " from " + c.makings() + ")";
			if (exact == null) {
				failures.add(name + ": no plan from exactly its makings");
			} else {
				String over = overTakes(exact, c.makings());
				if (over != null) {
					failures.add(name + ": " + over);
				} else if (exact.count() < c.result().getCount()) {
					failures.add(name + ": makes " + exact.count() + ", the recipe makes " + c.result().getCount());
				}
			}
			Map<Item, Long> shortOne = new LinkedHashMap<>(c.makings());
			Item first = shortOne.keySet().iterator().next();
			shortOne.compute(first, (k, v) -> v - 1 <= 0 ? null : v - 1);
			Crafting.Plan cut = Crafting.plan(helper.getLevel(), Crafting.Kind.KITCHEN, dish, 1, shortOne);
			if (cut != null) {
				String over = overTakes(cut, shortOne);
				if (over != null) {
					failures.add(name + " one " + BuiltInRegistries.ITEM.getKey(first) + " short: " + over);
				}
			}
			next[0]++;
		});
	}

	/**
	 * The stove broken while the Chef carries a Strawberry Sweet's makings: nothing is cooked once it is gone, and every
	 * red dye, honey bottle and sugar is still somewhere (chest, his bag, the ground) or in a sweet cooked before.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_pot_stove_broken"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_pot_stove_broken")
	public void qaTheStoveBrokenMidJobCooksNothingAndLosesNoMakings(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item sweet = sweet();
		Container chest = chest(helper, CHEST, new ItemStack(Items.RED_DYE, 2), new ItemStack(Items.HONEY_BOTTLE, 2), new ItemStack(Items.SUGAR, 2));
		Villager chef = chef(helper);
		long[] brokenAt = {-1};
		int[] cookedThen = {0};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0 && ModAttachments.BUILDER_BAG.getOrCreate(chef).count(Items.RED_DYE) > 0) {
				helper.getLevel().destroyBlock(helper.absolutePos(STATION), false);
				brokenAt[0] = helper.getLevel().getGameTime();
				cookedThen[0] = ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0);
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(brokenAt[0] >= 0, "the Chef never fetched the red dye"))
			.thenIdle(800)
			.thenExecute(() -> {
				helper.assertTrue(helper.getBlockState(STATION).isAir(), "the stove is back");
				int sweets = everywhere(helper, chef, sweet, chest);
				int dye = everywhere(helper, chef, Items.RED_DYE, chest);
				int sugar = everywhere(helper, chef, Items.SUGAR, chest);
				helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) == cookedThen[0] && sweets == 0,
					"cooked after the stove was broken: " + sweets + " sweets, crafted " + cookedThen[0] + " -> " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0));
				int honey = everywhere(helper, chef, Items.HONEY_BOTTLE, chest);
				helper.assertTrue(dye + sweets == 2 && sugar + sweets == 2 && honey + sweets == 2, "makings lost with the stove broken: " + dye + " red dye, "
					+ sugar + " sugar, " + honey + " honey bottles, " + sweets + " sweets (2 of each to start); bag " + ModAttachments.BUILDER_BAG.getOrCreate(chef).stacks());
			})
			.thenSucceed();
	}

	/**
	 * Red dye and a honey bottle in one chest, the sugar in another: once the Chef has taken from one chest, a player
	 * empties both. He cooks no sweet without its makings, and the makings he took are not lost.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_pot_makings_taken"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_pot_makings_taken")
	public void qaMakingsTakenMidFetchCookNoSweetAndLoseNothing(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item sweet = sweet();
		Container one = chest(helper, CHEST, new ItemStack(Items.RED_DYE, 1), new ItemStack(Items.HONEY_BOTTLE, 1));
		Container two = chest(helper, OTHER_CHEST, new ItemStack(Items.SUGAR, 1));
		Villager chef = chef(helper);
		boolean[] taken = {false};
		int[] removed = new int[3]; // red dye, honey bottle, sugar a "player" took out
		helper.onEachTick(() -> {
			if (taken[0] || ModAttachments.BUILDER_BAG.getOrCreate(chef).isEmpty()) {
				return;
			}
			taken[0] = true;
			removed[0] = one.countItem(Items.RED_DYE) + two.countItem(Items.RED_DYE);
			removed[1] = one.countItem(Items.HONEY_BOTTLE) + two.countItem(Items.HONEY_BOTTLE);
			removed[2] = one.countItem(Items.SUGAR) + two.countItem(Items.SUGAR);
			one.clearContent();
			two.clearContent();
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(taken[0], "the Chef never fetched anything"))
			.thenExecute(() -> helper.assertTrue(removed[0] + removed[1] + removed[2] > 0,
				"setup: he fetched every making from both chests at once, so none could be taken mid-fetch"))
			.thenIdle(1000)
			.thenExecute(() -> {
				int sweets = everywhere(helper, chef, sweet, one, two);
				helper.assertTrue(sweets == 0 && ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) == 0,
					sweets + " sweets cooked without all the makings (crafted " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) + ")");
				int dye = everywhere(helper, chef, Items.RED_DYE, one, two);
				int sugar = everywhere(helper, chef, Items.SUGAR, one, two);
				int honey = everywhere(helper, chef, Items.HONEY_BOTTLE, one, two);
				helper.assertTrue(dye == 1 - removed[0] && honey == 1 - removed[1] && sugar == 1 - removed[2], "the makings he took are lost: "
					+ dye + " red dye (expected " + (1 - removed[0]) + "), " + honey + " honey bottles (expected " + (1 - removed[1]) + "), "
					+ sugar + " sugar (expected " + (1 - removed[2]) + "); bag " + ModAttachments.BUILDER_BAG.getOrCreate(chef).stacks());
			})
			.thenSucceed();
	}
}
