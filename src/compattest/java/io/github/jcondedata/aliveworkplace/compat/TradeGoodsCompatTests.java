package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * ROADMAP 33.3: with Cobblemon, all 28 trade goods load, the last three being Apricorns, Berries and Poké Balls, with
 * Cobblemon's items, their names and their prices (without Cobblemon they don't load: {@code TradeGoodsDataGameTests}).
 */
public class TradeGoodsCompatTests implements FabricGameTest {
	private static Item cobblemon(String item) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobblemon", item);
		return BuiltInRegistries.ITEM.containsKey(id) ? Lookup.value(BuiltInRegistries.ITEM, id) : Items.AIR;
	}

	private static TradeGoods.Good good(GameTestHelper helper, String id) {
		TradeGoods.Good g = TradeGoods.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", id));
		helper.assertTrue(g != null, id + " didn't load with Cobblemon");
		return g;
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCobblemonTradeGoodsLoadWithCobblemon(GameTestHelper helper) {
		List<String> ours = TradeGoods.all().stream().map(TradeGoods.Good::id).filter(i -> i.getNamespace().equals("aliveworkplace"))
			.map(ResourceLocation::getPath).toList();
		helper.assertTrue(ours.size() == 28 && ours.subList(25, 28).equals(List.of("apricorns", "berries", "poke_balls")), "goods: " + ours);

		TradeGoods.Good apricorns = good(helper, "apricorns");
		helper.assertTrue(apricorns.name().getString().equals("Apricorns") && apricorns.bundle() == 8 && apricorns.basePrice() == 100, "apricorns: " + apricorns);
		helper.assertTrue(apricorns.icon() == cobblemon("red_apricorn") && apricorns.matches(cobblemon("black_apricorn"))
			&& apricorns.matches(cobblemon("yellow_apricorn")) && !apricorns.matches(cobblemon("oran_berry")), "apricorns' items");

		TradeGoods.Good berries = good(helper, "berries");
		helper.assertTrue(berries.name().getString().equals("Berries") && berries.bundle() == 12 && berries.basePrice() == 100, "berries: " + berries);
		helper.assertTrue(berries.icon() == cobblemon("oran_berry") && berries.matches(cobblemon("sitrus_berry")) && !berries.matches(Items.SWEET_BERRIES)
			&& !berries.matches(cobblemon("red_apricorn")), "berries' items");

		TradeGoods.Good balls = good(helper, "poke_balls");
		helper.assertTrue(balls.name().getString().equals("Poké Balls") && balls.bundle() == 8 && balls.basePrice() == 100, "poké balls: " + balls);
		helper.assertTrue(balls.icon() == cobblemon("poke_ball") && balls.matches(cobblemon("great_ball")) && balls.matches(cobblemon("ultra_ball"))
			&& !balls.matches(cobblemon("master_ball")), "poké balls' items");
		helper.assertTrue(balls.makers().equals(List.of(ResourceLocation.parse("aliveworkplace:ball_smith")))
			&& balls.users().equals(List.of(ResourceLocation.parse("aliveworkplace:trainer"), ResourceLocation.parse("aliveworkplace:pokemon_trader"))),
			"poké balls' jobs");
		helper.succeed();
	}
}
