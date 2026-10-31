package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-10-31) for 31.15's treasure chest, from its spec: "16 to 32 emeralds, 4 to 8 gold ingots, 1 to 3
 * diamonds, an enchanted iron sword or crossbow, and the King's Crown (the golden helmet, Protection III and Unbreaking
 * III, named)". Over 400 fixed rolls every count stays inside its range and both ends of each range turn up; the crown
 * is there every time with exactly those enchantments and its name. (The weapon is QaBanditKing1028GameTests'.)
 */
public class QaBanditKingLoot1031GameTests implements FabricGameTest {
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTreasureCountsAndCrownAsTheSpecSays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 1, 0))))
			.create(LootContextParamSets.CHEST);
		var table = level.getServer().reloadableRegistries().getLootTable(BanditFort.LOOT);
		var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		var protection = enchantments.getHolderOrThrow(Enchantments.PROTECTION);
		var unbreaking = enchantments.getHolderOrThrow(Enchantments.UNBREAKING);
		String crownName = Language.getInstance().getOrDefault("item.aliveworkplace.kings_crown");
		helper.assertTrue(crownName.equals("The King's Crown"), "the crown's name reads '" + crownName + "'");
		int[][] seen = {{99, 0}, {99, 0}, {99, 0}}; // min, max seen of emeralds, gold, diamonds
		Item[] kinds = {Items.EMERALD, Items.GOLD_INGOT, Items.DIAMOND};
		int[][] range = {{16, 32}, {4, 8}, {1, 3}};
		for (int i = 0; i < 400; i++) {
			List<ItemStack> items = table.getRandomItems(params, 31_015L + i);
			for (int k = 0; k < kinds.length; k++) {
				Item kind = kinds[k];
				int n = items.stream().filter(s -> s.is(kind)).mapToInt(ItemStack::getCount).sum();
				helper.assertTrue(n >= range[k][0] && n <= range[k][1], "roll " + i + ": " + n + " " + kind + ", the spec says " + range[k][0] + " to " + range[k][1]);
				seen[k][0] = Math.min(seen[k][0], n);
				seen[k][1] = Math.max(seen[k][1], n);
			}
			List<ItemStack> crowns = items.stream().filter(s -> s.is(Items.GOLDEN_HELMET)).toList();
			helper.assertTrue(crowns.size() == 1 && crowns.get(0).getCount() == 1, "roll " + i + ": crowns " + crowns);
			ItemStack crown = crowns.get(0);
			var ench = EnchantmentHelper.getEnchantmentsForCrafting(crown);
			helper.assertTrue(ench.getLevel(protection) == 3 && ench.getLevel(unbreaking) == 3 && ench.size() == 2,
				"roll " + i + ": the crown's enchantments " + ench);
			helper.assertTrue(crown.getHoverName().getString().equals(crownName), "roll " + i + ": the crown is called '" + crown.getHoverName().getString() + "'");
		}
		for (int k = 0; k < kinds.length; k++) {
			helper.assertTrue(seen[k][0] == range[k][0] && seen[k][1] == range[k][1],
				kinds[k] + " over 400 rolls ran " + seen[k][0] + " to " + seen[k][1] + ", the spec says " + range[k][0] + " to " + range[k][1]);
		}
		helper.succeed();
	}
}
