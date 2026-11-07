package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Diet;
import io.github.jcondedata.aliveworkplace.people.Moods;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1107-0353), the villagers' diet from its spec (Diet's contract, the varied-diet feature): a villager
 * remembers their last five meals from the store; three or more kinds among them is a varied diet (mood up), the same
 * food every time a dull one (mood down), two kinds plain (neither); fewer than three meals counts neither way. At a
 * meal they take something they haven't had lately if the store has it, and still eat what's there when it hasn't.
 * Every meal goes through the village store ({@link VillageNeeds#eat}), the way villagers eat in play. Existing tests
 * covered only three-of-a-kind and three-different; these cover the boundaries, the five-meal window, the fallback
 * and a save.
 */
public class QaDietLaneC1107GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Two meals of bread are too few to call it anything: no varied-diet or same-food reason, kind UNKNOWN. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaTwoMealsAreTooFewToJudge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Container chest = chest(helper, new BlockPos(2, 2, 2), new ItemStack(Items.BREAD, 8));
		List<BlockPos> store = List.of(helper.absolutePos(new BlockPos(2, 2, 2)));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		eat(helper, level, villager, store, 2);
		helper.assertTrue(chest.countItem(Items.BREAD) == 6, "two meals should take two bread, " + chest.countItem(Items.BREAD) + " left");
		helper.assertTrue(Diet.of(villager) == Diet.Kind.UNKNOWN, "two meals judged " + Diet.of(villager));
		Moods.Mood mood = Moods.work(level, villager);
		helper.assertFalse(has(mood.bad(), "same_food"), "two breads already called the same food: " + mood.bad());
		helper.assertFalse(has(mood.good(), "varied_diet"), "two breads called varied: " + mood.good());
		eat(helper, level, villager, store, 1);
		helper.assertTrue(Diet.of(villager) == Diet.Kind.SAME, "the third bread should make it the same food, not " + Diet.of(villager));
		helper.succeed();
	}

	/** Two kinds over three or more meals is plain: neither a better nor a worse mood, and the mood sits between them. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaTwoKindsArePlainBetweenVariedAndSame(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager plain = fedFrom(helper, level, new BlockPos(2, 2, 2), new BlockPos(4, 2, 2), 4, new ItemStack(Items.BREAD, 8), new ItemStack(Items.BAKED_POTATO, 8));
		Villager varied = fedFrom(helper, level, new BlockPos(2, 2, 6), new BlockPos(4, 2, 6), 4,
			new ItemStack(Items.BREAD, 8), new ItemStack(Items.BAKED_POTATO, 8), new ItemStack(Items.COOKED_COD, 8));
		Villager same = fedFrom(helper, level, new BlockPos(2, 2, 10), new BlockPos(4, 2, 10), 4, new ItemStack(Items.BREAD, 8));
		helper.assertTrue(Diet.of(plain) == Diet.Kind.PLAIN, "bread and potatoes judged " + Diet.of(plain));
		helper.assertTrue(Diet.of(varied) == Diet.Kind.VARIED, "three kinds judged " + Diet.of(varied));
		helper.assertTrue(Diet.of(same) == Diet.Kind.SAME, "bread only judged " + Diet.of(same));
		Moods.Mood p = Moods.work(level, plain);
		Moods.Mood v = Moods.work(level, varied);
		Moods.Mood s = Moods.work(level, same);
		helper.assertFalse(has(p.good(), "varied_diet") || has(p.bad(), "same_food"), "a plain diet gave a diet reason: " + p);
		helper.assertTrue(v.score() > p.score() && p.score() > s.score(),
			"moods should go varied > plain > same, got " + v.score() + ", " + p.score() + ", " + s.score());
		helper.succeed();
	}

	/**
	 * Only the last five meals count: bread, potato, cod, then bread three times is still varied (potato, cod, bread x3);
	 * a fourth bread forgets the potato (plain); a fifth forgets the cod (the same food every time).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaOnlyTheLastFiveMealsCount(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = fedFrom(helper, level, new BlockPos(2, 2, 2), new BlockPos(5, 2, 5), 3,
			new ItemStack(Items.BREAD, 1), new ItemStack(Items.BAKED_POTATO, 1), new ItemStack(Items.COOKED_COD, 1));
		helper.assertTrue(Diet.of(villager) == Diet.Kind.VARIED, "one of each judged " + Diet.of(villager));
		Container bread = chest(helper, new BlockPos(8, 2, 2), new ItemStack(Items.BREAD, 10));
		List<BlockPos> breadStore = List.of(helper.absolutePos(new BlockPos(8, 2, 2)));
		eat(helper, level, villager, breadStore, 3);
		helper.assertTrue(Diet.of(villager) == Diet.Kind.VARIED, "potato, cod and three breads (the last five) judged " + Diet.of(villager));
		eat(helper, level, villager, breadStore, 1);
		helper.assertTrue(Diet.of(villager) == Diet.Kind.PLAIN, "cod and four breads judged " + Diet.of(villager));
		eat(helper, level, villager, breadStore, 1);
		helper.assertTrue(Diet.of(villager) == Diet.Kind.SAME, "five breads judged " + Diet.of(villager));
		helper.assertTrue(bread.countItem(Items.BREAD) == 5, "five meals should take five bread, " + bread.countItem(Items.BREAD) + " left");
		helper.succeed();
	}

	/**
	 * A villager who had bread and potatoes lately takes the one cooked cod in the store though bread comes first and
	 * there's plenty of it; with only bread left they still eat bread rather than go hungry.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaSomethingNotHadLatelyFirstThenWhateverIsThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = fedFrom(helper, level, new BlockPos(2, 2, 2), new BlockPos(5, 2, 5), 2,
			new ItemStack(Items.BREAD, 1), new ItemStack(Items.BAKED_POTATO, 1));
		Container store = chest(helper, new BlockPos(8, 2, 2), new ItemStack(Items.BREAD, 10), new ItemStack(Items.BAKED_POTATO, 10), new ItemStack(Items.COOKED_COD, 1));
		List<BlockPos> at = List.of(helper.absolutePos(new BlockPos(8, 2, 2)));
		eat(helper, level, villager, at, 1);
		helper.assertTrue(store.countItem(Items.COOKED_COD) == 0 && store.countItem(Items.BREAD) == 10 && store.countItem(Items.BAKED_POTATO) == 10,
			"the meal should be the cod they hadn't had: bread " + store.countItem(Items.BREAD) + ", potato " + store.countItem(Items.BAKED_POTATO)
				+ ", cod " + store.countItem(Items.COOKED_COD));
		helper.assertTrue(Diet.of(villager) == Diet.Kind.VARIED, "bread, potato, cod judged " + Diet.of(villager));
		Container onlyBread = chest(helper, new BlockPos(8, 2, 8), new ItemStack(Items.BREAD, 2));
		helper.assertTrue(VillageNeeds.eat(level, villager, List.of(helper.absolutePos(new BlockPos(8, 2, 8)))),
			"a villager who had bread lately went hungry beside a chest of bread");
		helper.assertTrue(onlyBread.countItem(Items.BREAD) == 1, "the bread meal took " + (2 - onlyBread.countItem(Items.BREAD)));
		Container empty = chest(helper, new BlockPos(2, 2, 8));
		helper.assertFalse(VillageNeeds.eat(level, villager, List.of(helper.absolutePos(new BlockPos(2, 2, 8)))), "ate from an empty chest");
		helper.assertTrue(empty.isEmpty(), "the empty chest changed");
		helper.succeed();
	}

	/** The remembered meals are saved with the villager: a varied diet is still varied after a save and load. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaTheDietSurvivesASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = fedFrom(helper, level, new BlockPos(2, 2, 2), new BlockPos(5, 2, 5), 4,
			new ItemStack(Items.BREAD, 2), new ItemStack(Items.BAKED_POTATO, 1), new ItemStack(Items.COOKED_COD, 1));
		helper.assertTrue(Diet.of(villager) == Diet.Kind.VARIED, "before the save: " + Diet.of(villager));
		Villager copy = reload(level, villager);
		helper.assertTrue(Diet.of(copy) == Diet.Kind.VARIED, "after the save: " + Diet.of(copy));
		Container bread = chest(helper, new BlockPos(8, 2, 2), new ItemStack(Items.BREAD, 5));
		eat(helper, level, copy, List.of(helper.absolutePos(new BlockPos(8, 2, 2))), 1);
		// remembered after the load: bread, potato, cod, bread (4 meals, the first meal was bread) + bread = still 3 kinds
		helper.assertTrue(Diet.of(copy) == Diet.Kind.VARIED, "one more bread after the load judged " + Diet.of(copy));
		helper.assertTrue(bread.countItem(Items.BREAD) == 4, "the meal after the load took " + (5 - bread.countItem(Items.BREAD)));
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------------------

	private static Container chest(GameTestHelper helper, BlockPos pos, ItemStack... stacks) {
		helper.setBlock(pos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(pos);
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		return chest;
	}

	/** A villager at {@code at} who ate {@code meals} meals from a chest at {@code chestPos} holding {@code stacks}. */
	private static Villager fedFrom(GameTestHelper helper, ServerLevel level, BlockPos chestPos, BlockPos at, int meals, ItemStack... stacks) {
		chest(helper, chestPos, stacks);
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		eat(helper, level, villager, List.of(helper.absolutePos(chestPos)), meals);
		return villager;
	}

	private static void eat(GameTestHelper helper, ServerLevel level, Villager villager, List<BlockPos> store, int meals) {
		for (int i = 0; i < meals; i++) {
			if (!VillageNeeds.eat(level, villager, store)) {
				throw new GameTestAssertException("nothing to eat at meal " + (i + 1) + " of " + meals);
			}
		}
	}

	private static boolean has(List<Component> reasons, String key) {
		return reasons.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().endsWith(key));
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		return copy;
	}
}
