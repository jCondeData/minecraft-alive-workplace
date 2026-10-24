package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * QA for ROADMAP 37.14a and the wiki/guide claim (docs/wiki/crafter.md): "the Crafter's Workbench can't be crafted,
 * isn't in the creative tab or any trade". Written from the spec, not the code. The builder's shortfall filled by a
 * Carpenter and the build finishing is already asserted by JobChainGameTests (assertBuilt: site gone, every block right).
 */
public class QaCrafterWorkbenchGameTests {
	private static final String ID = "aliveworkplace:crafters_workbench";

	private static Item workbench() {
		return ModBlocks.CRAFTERS_WORKBENCH.asItem();
	}

	/** No recipe of any type the server loaded makes the Crafter's Workbench. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noRecipeMakesTheCraftersWorkbench(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(workbench() != net.minecraft.world.item.Items.AIR, "the workbench has no item");
		List<String> makers = new ArrayList<>();
		int seen = 0;
		for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
			seen++;
			ItemStack out = holder.value().getResultItem(level.registryAccess());
			if (out.is(workbench())) {
				makers.add(holder.id().toString());
			}
		}
		helper.assertTrue(seen > 100, "only " + seen + " recipes loaded: the check saw nothing");
		helper.assertTrue(makers.isEmpty(), "recipes still make the Crafter's Workbench: " + makers);
		helper.succeed();
	}

	/** None of the mod's creative tabs shows it (every tab under our namespace, built as the client would). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noCreativeTabShowsTheCraftersWorkbench(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess());
		int ours = 0;
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<net.minecraft.resources.ResourceKey<CreativeModeTab>, CreativeModeTab> e : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
			if (!e.getKey().location().getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue;
			}
			ours++;
			CreativeModeTab tab = e.getValue();
			tab.buildContents(params);
			boolean shown = tab.getDisplayItems().stream().anyMatch(s -> s.is(workbench()))
				|| tab.getSearchTabDisplayItems().stream().anyMatch(s -> s.is(workbench()));
			if (shown) {
				wrong.add(e.getKey().location().toString());
			}
		}
		helper.assertTrue(ours > 0, "the mod has no creative tab to check");
		helper.assertTrue(wrong.isEmpty(), "the Crafter's Workbench is still in creative tab(s) " + wrong);
		helper.succeed();
	}

	/**
	 * No villager or wandering-trader trade offers it, bought or sold: every listing of every profession and level is
	 * rolled 16 times with fixed seeds on a real villager (treasure maps skipped: they search the world for a structure).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noTradeOffersTheCraftersWorkbench(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = EntityType.VILLAGER.create(level);
		helper.assertTrue(villager != null, "no villager made");
		villager.moveTo(helper.absolutePos(new BlockPos(0, 2, 0)).getCenter());
		List<String> wrong = new ArrayList<>();
		int rolled = 0;
		for (Map.Entry<VillagerProfession, it.unimi.dsi.fastutil.ints.Int2ObjectMap<VillagerTrades.ItemListing[]>> e : VillagerTrades.TRADES.entrySet()) {
			villager.setVillagerData(villager.getVillagerData().setProfession(e.getKey()));
			for (it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> lvl : e.getValue().int2ObjectEntrySet()) {
				rolled += roll(villager, lvl.getValue(), e.getKey() + " level " + lvl.getIntKey(), wrong);
			}
		}
		for (it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> lvl : VillagerTrades.WANDERING_TRADER_TRADES.int2ObjectEntrySet()) {
			rolled += roll(villager, lvl.getValue(), "wandering trader tier " + lvl.getIntKey(), wrong);
		}
		villager.discard();
		helper.assertTrue(rolled > 100, "only " + rolled + " offers rolled: the check saw nothing");
		helper.assertTrue(wrong.isEmpty(), "trades still offer the Crafter's Workbench: " + wrong);
		helper.succeed();
	}

	private static int roll(Villager villager, VillagerTrades.ItemListing[] listings, String where, List<String> wrong) {
		int rolled = 0;
		for (VillagerTrades.ItemListing listing : listings) {
			if (listing instanceof VillagerTrades.TreasureMapForEmeralds) {
				continue;
			}
			for (int seed = 0; seed < 16; seed++) {
				MerchantOffer offer;
				try {
					offer = listing.getOffer(villager, RandomSource.create(seed));
				} catch (RuntimeException ex) {
					continue; // a listing that needs more than a plain villager isn't ours to judge here
				}
				if (offer == null) {
					continue;
				}
				rolled++;
				if (offer.getResult().is(workbench()) || offer.getCostA().is(workbench()) || offer.getCostB().is(workbench())) {
					wrong.add(where + ": " + offer.getCostA() + " + " + offer.getCostB() + " -> " + offer.getResult());
					break;
				}
			}
		}
		return rolled;
	}

	/**
	 * No shop, cost or offer list in the loaded data names it: every JSON file in the data packs' folders (loot tables,
	 * tags, advancements and worldgen left out, as they don't offer anything) is searched for its id.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noDataListOffersTheCraftersWorkbench(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> folders = List.of("trade_goods", "services", "bounties", "luxuries", "luxury_recipes", "camp_menu", "guilds",
			"quests", "blueprint_unlocks", "gifted", "edicts", "realm_research", "research_trees", "steward_rules", "steward_renewal",
			"furnishings", "homes", "wonders", "legends", "arcs", "heart_events", "villager_tastes", "partner_shows", "cups",
			"crafter_benches", "furniture_equivalents", "blueprint_furniture", "recipe", "kinetic_parts", "machine_work", "tonics");
		List<String> wrong = new ArrayList<>();
		int read = 0;
		for (String folder : folders) {
			Map<ResourceLocation, Resource> files = level.getServer().getResourceManager().listResources(folder, p -> p.getPath().endsWith(".json"));
			for (Map.Entry<ResourceLocation, Resource> f : files.entrySet()) {
				try (BufferedReader reader = f.getValue().openAsReader()) {
					read++;
					String text = reader.lines().collect(Collectors.joining("\n"));
					if (text.contains(ID)) {
						wrong.add(f.getKey().toString());
					}
				} catch (IOException ex) {
					wrong.add(f.getKey() + " unreadable: " + ex.getMessage());
				}
			}
		}
		helper.assertTrue(read > 50, "only " + read + " data files read: the check saw nothing");
		helper.assertTrue(wrong.isEmpty(), "data files still name the Crafter's Workbench: " + wrong);
		helper.succeed();
	}
}
