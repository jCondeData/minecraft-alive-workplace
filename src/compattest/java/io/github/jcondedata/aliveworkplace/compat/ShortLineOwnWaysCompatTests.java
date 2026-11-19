package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B1289, with the compat suite's mods (Create, Cobblemon, Chipped, Supplementaries, Tom's Storage...): the crafters' shortage
 * line past B1273/B1277/B1267's own examples (found by lane E's sweep, 2026-11-15). Every item a job makes (CraftJobs.maker),
 * read from an empty chest the way that job reads a builder's shortage line, against crafter.md: "Of two ways that fit as
 * well, the one from the thing's own mod comes first"; "Of an ingredient's options the base game's come first ... never
 * Chipped's decorative planks that no crafting recipe makes". A smoker names Minecraft's logs, not Cobblemon's Apricorn
 * Logs; Tom's Storage's things name no Chipped plank; Minecraft's chest, hopper, TNT and furnace minecarts name no Minecart
 * Contraption of Create's; a slimeball itself is never read down to Create's Wheat Flour.
 */
public class ShortLineOwnWaysCompatTests implements FreshPlanning {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/**
	 * Every job-made item from nothing (one tick's planning at a time, so a spent budget never reads as "nothing short"): no
	 * line names Chipped's decorative planks for another mod's thing; no line for a thing outside Cobblemon names a modded log
	 * of another mod (the logs tag holds oak); a Minecraft thing with a Minecraft recipe names only Minecraft's items.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '20000' '"shortLineOwnWays"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 20000, batch = "shortLineOwnWays")
	public void everyJobsLineFromNothingNamesTheBaseGamesOwnThings(GameTestHelper helper) {
		QaShortLineSweep1115e3CompatTests.sweep(helper, ShortLineOwnWaysCompatTests::judge);
	}

	/**
	 * The rules above, and B1277's: Wheat Flour only a millstone makes is named only for Create's own things. Right, so not
	 * flagged: a Chipped thing a recipe makes (Sophisticated Storage's Chipped upgrades take that Chipped table), and another
	 * mod's log for the thing of that very wood (Supplementaries' Apricorn way sign takes Apricorn Logs).
	 */
	static String judge(ServerLevel level, QaShortLineSweep1115e3CompatTests.Case c, Map<Item, Integer> lack) {
		String target = QaShortLineSweep1115e3CompatTests.ns(c.item());
		String path = BuiltInRegistries.ITEM.getKey(c.item()).getPath();
		boolean vanillaWay = Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), c.kind(), c.item()).stream()
			.anyMatch(h -> Recipes.id(h).getNamespace().equals("minecraft"));
		for (Item k : lack.keySet()) {
			String n = QaShortLineSweep1115e3CompatTests.ns(k);
			ResourceLocation key = BuiltInRegistries.ITEM.getKey(k);
			String id = key.toString();
			String wood = key.getPath().replace("_log", "").replace("_stem", "");
			if (n.equals("chipped") && !target.equals("chipped") && !QaShortLineSweep1115e3CompatTests.has(level, Crafting.Kind.CRAFTING, k)
					|| k.builtInRegistryHolder().is(ItemTags.LOGS) && !n.equals("minecraft") && !n.equals(target) && !path.contains(wood)
					|| target.equals("minecraft") && vanillaWay && !n.equals("minecraft")
					|| id.equals("create:wheat_flour") && !target.equals("create")) {
				return "names " + id;
			}
		}
		return null;
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
	}

	private static Map<Item, Integer> fromNothing(GameTestHelper helper, Item item) {
		ServerLevel level = helper.getLevel();
		CraftJobs.Job job = CraftJobs.maker(level, item);
		helper.assertTrue(job != null, "setup: no job makes " + BuiltInRegistries.ITEM.getKey(item));
		Crafting.freshPlanning(level);
		return Crafting.lacking(level, QaShortLineSweep1115e3CompatTests.kindOf(level, job, item), item, 1, Map.of());
	}

	/** The furnace's 8 cobblestone there: exactly 4 of one of Minecraft's logs short, never Apricorn Logs (or 8 through wood). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSmokerFromCobblestoneLacksFourOfMinecraftsLogs(GameTestHelper helper) {
		Map<Item, Integer> lack = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.SMOKER, 1, Map.of(Items.COBBLESTONE, 8L));
		Item log = lack.isEmpty() ? Items.AIR : lack.keySet().iterator().next();
		helper.assertTrue(lack.size() == 1 && lack.get(log) == 4 && log.builtInRegistryHolder().is(ItemTags.LOGS)
			&& QaShortLineSweep1115e3CompatTests.ns(log).equals("minecraft"),
			"a smoker from 8 cobblestone lacks " + QaShortLineSweep1115e3CompatTests.named(lack) + ", expected 4 of one Minecraft log");
		helper.succeed();
	}

	/**
	 * Minecraft's chest, hopper, TNT and furnace minecarts from nothing name nothing of Create's (its Minecart Contraption
	 * recipes make a minecart and a furnace minecart too); a chest minecart is short of 1 Minecart itself.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void minecartsFromNothingNameNoMinecartContraption(GameTestHelper helper) {
		helper.assertTrue(item("create:minecart_contraption") != Items.AIR, "setup: Create's Minecart Contraption isn't registered");
		Map<String, String> wrong = new LinkedHashMap<>();
		for (Item cart : new Item[] {Items.CHEST_MINECART, Items.HOPPER_MINECART, Items.TNT_MINECART, Items.FURNACE_MINECART}) {
			Map<Item, Integer> lack = fromNothing(helper, cart);
			if (lack.isEmpty() || lack.keySet().stream().anyMatch(k -> !QaShortLineSweep1115e3CompatTests.ns(k).equals("minecraft"))) {
				wrong.put(BuiltInRegistries.ITEM.getKey(cart).toString(), QaShortLineSweep1115e3CompatTests.named(lack));
			}
		}
		helper.assertTrue(wrong.isEmpty(), "minecarts from nothing name another mod's thing (or nothing): " + wrong);
		Map<Item, Integer> chest = fromNothing(helper, Items.CHEST_MINECART);
		helper.assertTrue(chest.getOrDefault(Items.MINECART, 0) == 1,
			"a chest minecart from nothing lacks " + QaShortLineSweep1115e3CompatTests.named(chest) + ", expected 1 Minecart in it");
		helper.succeed();
	}

	/** What's there still leads: with a chest and a Minecart Contraption in the chests the chest minecart lacks nothing. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aMinecartContraptionThereIsStillUsed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Crafting.Kind kind = QaShortLineSweep1115e3CompatTests.kindOf(level, CraftJobs.maker(level, Items.CHEST_MINECART), Items.CHEST_MINECART);
		Map<Item, Long> there = Map.of(Items.CHEST, 1L, item("create:minecart_contraption"), 1L);
		Crafting.Plan plan = Crafting.shortfall(level, kind, Items.CHEST_MINECART, 1, there);
		helper.assertTrue(plan != null && Crafting.lacks(plan).isEmpty(), "with a chest and a Minecart Contraption there a chest minecart lacks "
			+ (plan == null ? "no plan" : QaShortLineSweep1115e3CompatTests.named(Crafting.lacks(plan))) + ", expected nothing");
		helper.assertTrue(Crafting.chain(level, kind, Items.CHEST_MINECART, 1, there) != null, "no plan makes the chest minecart from them");
		helper.succeed();
	}

	/**
	 * A slimeball asked for itself is never read down Create's Dough to the Wheat Flour only a millstone makes; as a sticky
	 * piston's ingredient it is still the Slimeball that's short (B1277).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSlimeballItselfIsNotReadDownToWheatFlour(GameTestHelper helper) {
		Item flour = item("create:wheat_flour");
		helper.assertTrue(flour != Items.AIR, "setup: Create's Wheat Flour isn't registered");
		Map<Item, Integer> lack = fromNothing(helper, Items.SLIME_BALL);
		helper.assertTrue(lack.keySet().stream().allMatch(k -> QaShortLineSweep1115e3CompatTests.ns(k).equals("minecraft")),
			"a slimeball from nothing lacks " + QaShortLineSweep1115e3CompatTests.named(lack) + ", expected nothing of another mod's");
		Map<Item, Integer> piston = fromNothing(helper, Items.STICKY_PISTON);
		helper.assertTrue(piston.getOrDefault(Items.SLIME_BALL, 0) == 1 && !piston.containsKey(flour),
			"a sticky piston from nothing lacks " + QaShortLineSweep1115e3CompatTests.named(piston) + ", expected 1 Slimeball in it");
		helper.succeed();
	}

	/** Tom's Storage's things (terminals, connectors) from nothing name no Chipped plank: their planks are Minecraft's, down to logs. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tomsStorageThingsNameNoChippedPlank(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, String> wrong = new LinkedHashMap<>();
		int read = 0;
		for (Item thing : BuiltInRegistries.ITEM) {
			CraftJobs.Job job = CraftJobs.maker(level, thing);
			if (job == null || !QaShortLineSweep1115e3CompatTests.ns(thing).equals("toms_storage")) {
				continue;
			}
			Crafting.Kind kind = QaShortLineSweep1115e3CompatTests.kindOf(level, job, thing);
			if (!QaShortLineSweep1115e3CompatTests.has(level, kind, thing)) {
				continue;
			}
			Crafting.freshPlanning(level);
			Map<Item, Integer> lack = Crafting.lacking(level, kind, thing, 1, Map.of());
			read++;
			if (lack.isEmpty() || lack.keySet().stream().anyMatch(k -> QaShortLineSweep1115e3CompatTests.ns(k).equals("chipped"))) {
				wrong.put(BuiltInRegistries.ITEM.getKey(thing).toString(), QaShortLineSweep1115e3CompatTests.named(lack));
			}
		}
		helper.assertTrue(read >= 5, "setup: only " + read + " of Tom's Storage's things have a job and a recipe");
		helper.assertTrue(wrong.isEmpty(), "Tom's Storage's things name a Chipped plank (or nothing): " + wrong);
		helper.succeed();
	}

	/**
	 * Through the player's entry point: a builder short of 1 Smoker with the furnace's 8 cobblestone in his chest, a Carpenter
	 * at his bench. The line names Minecraft's logs (oak), never Cobblemon's Apricorn Logs; nothing taken.
	 */
	//$ gametest_ticks_batch AREA '1400' '"shortLineOwnWaysSmoker"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "shortLineOwnWaysSmoker")
	public void aBuilderShortOfASmokerReadsOakLogsNotApricornLogs(GameTestHelper helper) {
		helper.assertTrue(CraftJobs.maker(helper.getLevel(), Items.SMOKER) == CraftJobs.Job.CARPENTER,
			"setup: a smoker's maker is " + CraftJobs.maker(helper.getLevel(), Items.SMOKER) + ", expected the Carpenter");
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.COBBLESTONE, 8)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.SMOKER, 1, 1, "sweepsmoker"),
			Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(station), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("Oak Log") && !text.contains("Apricorn"),
				"the builder's line: '" + text + "', expected Oak Logs named (never Apricorn Logs); site " + QaCreateRivals1115cCompatTests.site(helper, a));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "the Carpenter took something");
			helper.succeed();
		});
	}
}
