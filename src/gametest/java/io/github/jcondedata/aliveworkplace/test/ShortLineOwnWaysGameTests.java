package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * B1289 in the base game alone (its fix is for the pack's mods, ShortLineOwnWaysCompatTests): what the same shortage lines
 * read without them stays as it was. A smoker with the furnace's cobblestone there lacks 4 logs; a chest minecart lacks the
 * Minecart itself and a furnace minecart its furnace's cobblestone too; a slimeball, only unpacked from its block, has no
 * way to be made at all.
 */
public class ShortLineOwnWaysGameTests implements FreshPlanning {

	private static Map<Item, Integer> lack(GameTestHelper helper, Item item, Map<Item, Long> chest) {
		return Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, item, 1, chest);
	}

	/** The furnace's 8 cobblestone there: exactly 4 of one of the base game's logs short. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSmokerFromCobblestoneStillLacksFourLogs(GameTestHelper helper) {
		Map<Item, Integer> lack = lack(helper, Items.SMOKER, Map.of(Items.COBBLESTONE, 8L));
		Item log = lack.isEmpty() ? Items.AIR : lack.keySet().iterator().next();
		helper.assertTrue(lack.size() == 1 && lack.get(log) == 4 && log.builtInRegistryHolder().is(ItemTags.LOGS)
			&& BuiltInRegistries.ITEM.getKey(log).getNamespace().equals("minecraft"),
			"a smoker from 8 cobblestone lacks " + lack + ", expected 4 of one log");
		helper.succeed();
	}

	/** A chest there: the chest minecart lacks 1 Minecart and nothing else. A furnace minecart from nothing names cobblestone and the Minecart. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void minecartsStillLackTheMinecartItself(GameTestHelper helper) {
		Map<Item, Integer> chest = lack(helper, Items.CHEST_MINECART, Map.of(Items.CHEST, 1L));
		helper.assertTrue(chest.equals(Map.of(Items.MINECART, 1)), "a chest minecart with a chest there lacks " + chest + ", expected {minecart=1}");
		FreshPlanning.fresh(helper);
		Map<Item, Integer> furnace = lack(helper, Items.FURNACE_MINECART, Map.of());
		helper.assertTrue(furnace.equals(Map.of(Items.MINECART, 1, Items.COBBLESTONE, 8)),
			"a furnace minecart from nothing lacks " + furnace + ", expected {minecart=1, cobblestone=8}");
		helper.succeed();
	}

	/** A slimeball from nothing: no recipe makes it (its block only unpacks), so nothing is named; a sticky piston still lacks 1 Slimeball. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSlimeballStillHasNoWay(GameTestHelper helper) {
		Map<Item, Integer> ball = lack(helper, Items.SLIME_BALL, Map.of());
		helper.assertTrue(ball.isEmpty() && !Crafting.planningSpent(helper.getLevel()), "a slimeball from nothing lacks " + ball + ", expected nothing named");
		FreshPlanning.fresh(helper);
		Map<Item, Integer> piston = lack(helper, Items.STICKY_PISTON, Map.of(Items.PISTON, 1L));
		helper.assertTrue(piston.equals(Map.of(Items.SLIME_BALL, 1)), "a sticky piston with a piston there lacks " + piston + ", expected {slime_ball=1}");
		helper.succeed();
	}
}
