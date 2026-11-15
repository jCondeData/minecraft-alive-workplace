package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B1273 (crafter.md, B408/B489: the line names what a player gathers): with Chipped loaded the planks tag also holds
 * Chipped's decorative planks, which no crafting recipe makes, and the line for a lantern's torch's sticks named
 * chipped:acacia_planks_mosaic. It must name the base game's wood (or bamboo), never a Chipped item.
 */
public class ChippedShortLineCompatTests implements FabricGameTest {
	private static final String AREA = QaCreateRivals1115cCompatTests.AREA;

	/** Setup and logic: the planks tag holds Chipped planks, and a lantern, torch, sticks or piston from nothing names none. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fromNothingTheLineNamesNoChippedPlanks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean chippedPlanks = false;
		for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
			chippedPlanks |= BuiltInRegistries.ITEM.getKey(h.value()).getNamespace().equals("chipped");
		}
		helper.assertTrue(chippedPlanks, "setup: no Chipped planks in the planks tag");
		List<String> wrong = new java.util.ArrayList<>();
		for (Item item : List.of(Items.LANTERN, Items.TORCH, Items.STICK, Items.PISTON)) {
			for (Crafting.Kind kind : List.of(Crafting.Kind.WORKSHOP, Crafting.Kind.CRAFTING)) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, item, 1, Map.of());
				if (lack.isEmpty() || lack.keySet().stream().anyMatch(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("chipped"))) {
					wrong.add(item + " (" + kind + ") lacks " + lack);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "from nothing, nothing named or a Chipped item named: " + wrong);
		helper.succeed();
	}

	/** Through the player's entry point: a builder short of a lantern with nothing but a coal: the line names no Chipped planks. */
	//$ gametest_ticks_batch AREA '1400' '"chippedShortLineLantern"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "chippedShortLineLantern")
	public void aLanternShortNamesNoChippedPlanks(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.COAL, 1)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.LANTERN, 1, 1, "chiplantern"),
			Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		Villager t = QaCreateRivals1115cCompatTests.tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("Raw Iron"), "the builder's line: '" + text + "', expected a line naming the Raw Iron; site "
				+ QaCreateRivals1115cCompatTests.site(helper, a));
			for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
				if (BuiltInRegistries.ITEM.getKey(h.value()).getNamespace().equals("chipped")) {
					String name = h.value().getDefaultInstance().getHoverName().getString();
					helper.assertTrue(!text.contains(name), "the builder's line: '" + text + "' names Chipped's " + name);
				}
			}
			QaCreateRivals1115cCompatTests.tookNothing(helper, t, items);
			helper.succeed();
		});
	}
}
