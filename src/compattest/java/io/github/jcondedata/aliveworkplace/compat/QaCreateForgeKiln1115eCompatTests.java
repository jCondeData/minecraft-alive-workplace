package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, 2026-11-15), Create loaded (the owner's 2.3 pack): what a job's furnace plans and names short, read straight
 * off its recipes, beside Create's own ways to make the same things. Create fires iron, gold and copper ingots from its
 * Crushed Raw ores (a crushing wheel's output, no crafting recipe makes them) and fires glass and glass panes back out of
 * its Tiled and Framed Glass (B1196, B1255). The spec (crafter.md, B408/B489): "The line names what the chest is short of,
 * not some other way to make it": raw iron for the Tinkerer's bars, never a Crushed Raw Iron no villager can make; and
 * glassblower.md: panes from sand, the glass fired first. The plain suite's twins are
 * QaTinkererShortBugGameTests#theForgeNamesTheRawMetalShort and the Glassblower's line tests.
 */
public class QaCreateForgeKiln1115eCompatTests implements FabricGameTest {

	private static Item create(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
		if (item == Items.AIR) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("create:" + path + " isn't registered (is Create loaded?)");
		}
		return item;
	}

	/**
	 * The forge (Tinkerer) with Create, where Create adds no rival: a copper block and a gold plate name the raw metal short
	 * of what's there (B489's boundaries). Iron, where Create fires its Crushed Raw Iron too, is
	 * QaCreateForge1115eBugCompatTests (B-filed, on tests/create-forge-crushed-1115e).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withCreateTheForgeNamesTheRawCopperAndGold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Crafting.Kind kind = Crafting.Kind.WORKSHOP;
		create("crushed_raw_copper");
		Map<Item, Integer> copper = Crafting.lacking(level, kind, Items.COPPER_BLOCK, 1, Map.of(Items.RAW_COPPER, 5L));
		helper.assertTrue(copper.equals(Map.of(Items.RAW_COPPER, 4)), "a copper block from 5 raw copper lacks " + copper + ", expected {raw_copper=4}");
		Map<Item, Integer> plates = Crafting.lacking(level, kind, Items.LIGHT_WEIGHTED_PRESSURE_PLATE, 1, Map.of(Items.RAW_GOLD, 1L));
		helper.assertTrue(plates.equals(Map.of(Items.RAW_GOLD, 1)), "a gold pressure plate from 1 raw gold lacks " + plates + ", expected {raw_gold=1}");
		helper.succeed();
	}

	/** The forge plans bars from the raw iron that's there (six for 16 bars), and from a player's crushed ore if that's all there is. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withCreateTheForgeFiresWhatIsThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item crushed = create("crushed_raw_iron");
		Crafting.Plan raw = Crafting.plan(level, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Map.of(Items.RAW_IRON, 6L));
		helper.assertTrue(raw != null && raw.takes().equals(Map.of(Items.RAW_IRON, 6)), "16 bars from 6 raw iron: plan "
			+ (raw == null ? "none" : "takes " + raw.takes()) + ", expected it to take exactly 6 raw iron");
		Crafting.Plan both = Crafting.plan(level, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Map.of(Items.RAW_IRON, 6L, crushed, 6L));
		helper.assertTrue(both != null && both.takes().values().stream().mapToInt(Integer::intValue).sum() == 6
			&& both.takes().keySet().stream().allMatch(i -> i == Items.RAW_IRON || i == crushed), "16 bars from 6 raw iron and 6 crushed: plan "
			+ (both == null ? "none" : "takes " + both.takes()) + ", expected 6 ores in all");
		Crafting.Plan onlyCrushed = Crafting.plan(level, Crafting.Kind.WORKSHOP, Items.IRON_BARS, 16, Map.of(crushed, 6L));
		helper.assertTrue(onlyCrushed != null && onlyCrushed.takes().equals(Map.of(crushed, 6)), "16 bars from 6 crushed raw iron: plan "
			+ (onlyCrushed == null ? "none" : "takes " + onlyCrushed.takes()) + ", expected it to fire the 6 crushed ores");
		helper.succeed();
	}

	/**
	 * The Glassblower's kiln with Create (B1196, B1255): glass and panes from nothing name the sand, and with a player's Tiled
	 * Glass Panes on hand (and no sand) the panes may come back out of them, one for one, rather than "nothing to make".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withCreateTheKilnNamesTheSand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Crafting.Kind kind = Crafting.Kind.KILN;
		Map<Item, Integer> glass = Crafting.lacking(level, kind, Items.GLASS, 8, Map.of());
		helper.assertTrue(glass.equals(Map.of(Items.SAND, 8)), "8 glass from nothing lack " + glass + ", expected {sand=8}");
		Map<Item, Integer> panes = Crafting.lacking(level, kind, Items.GLASS_PANE, 16, Map.of());
		helper.assertTrue(panes.equals(Map.of(Items.SAND, 6)), "16 glass panes from nothing lack " + panes + ", expected {sand=6}");
		Map<Item, Integer> panesSome = Crafting.lacking(level, kind, Items.GLASS_PANE, 16, Map.of(Items.SAND, 4L));
		helper.assertTrue(panesSome.equals(Map.of(Items.SAND, 2)), "16 glass panes from 4 sand lack " + panesSome + ", expected {sand=2}");
		Crafting.Plan fromSand = Crafting.plan(level, kind, Items.GLASS_PANE, 16, Map.of(Items.SAND, 6L));
		helper.assertTrue(fromSand != null && fromSand.takes().equals(Map.of(Items.SAND, 6)), "16 panes from 6 sand: plan "
			+ (fromSand == null ? "none" : "takes " + fromSand.takes()) + ", expected exactly 6 sand");
		Item tiledPane = create("tiled_glass_pane");
		Crafting.Plan back = Crafting.plan(level, kind, Items.GLASS_PANE, 4, Map.of(tiledPane, 4L));
		helper.assertTrue(back != null && back.takes().equals(Map.of(tiledPane, 4)), "4 panes from 4 Tiled Glass Panes and no sand: plan "
			+ (back == null ? "none" : "takes " + back.takes()) + ", expected the 4 tiled panes fired back");
		helper.succeed();
	}
}
