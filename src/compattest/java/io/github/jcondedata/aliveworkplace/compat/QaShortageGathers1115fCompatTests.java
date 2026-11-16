package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-b-1115-1237) past B1273, B1277, B1278 and B1279's fix with the pack (Create, Chipped, Cobblemon), written
 * from crafter.md: the shortage line names what a player gathers, goes further down a chain only when that uses
 * something that's there, never a Silk-Touch ore, another mod's decorative planks or a Create machine's output (crushed
 * ore, wheat flour, dough). If the chests DO hold the modded thing, it may be used. Boundaries from both sides (0 vs 1 raw
 * zinc, a slimeball or Create's dough there or not, Chipped planks or vanilla logs there), other chains of the same kind
 * (Create's crushed copper and gold, a lead's and magma cream's slimeball, the wood things a player makes from planks),
 * and the exact sentence through a builder and his Tinkerer.
 */
public class QaShortageGathers1115fCompatTests implements FabricGameTest {
	private static final String AREA = QaCreateRivals1115cCompatTests.AREA;
	private static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores"));

	/** Minecraft's metals from nothing with Create loaded: the raw metal a player mines, never Create's crushed ore. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fCVanillaMetalsFromNothingNameRawNotCrushed"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fCVanillaMetalsFromNothingNameRawNotCrushed")
	public void vanillaMetalsFromNothingNameRawNotCrushed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, String> wrong = new HashMap<>();
		Map<Item, Long> coal = Map.of(Items.COAL, 4L);
		QaCreateRivals1115cCompatTests.check(wrong, "a Block of Iron", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.IRON_BLOCK, 1, coal),
			Map.of(Items.RAW_IRON, 9));
		QaCreateRivals1115cCompatTests.check(wrong, "a Block of Copper", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.COPPER_BLOCK, 1, coal),
			Map.of(Items.RAW_COPPER, 9));
		QaCreateRivals1115cCompatTests.check(wrong, "a Block of Gold", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.GOLD_BLOCK, 1, coal),
			Map.of(Items.RAW_GOLD, 9));
		QaCreateRivals1115cCompatTests.check(wrong, "3 copper ingots", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.COPPER_INGOT, 3, coal),
			Map.of(Items.RAW_COPPER, 3));
		QaCreateRivals1115cCompatTests.check(wrong, "3 gold ingots", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.GOLD_INGOT, 3, coal),
			Map.of(Items.RAW_GOLD, 3));
		QaCreateRivals1115cCompatTests.check(wrong, "a bucket", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.BUCKET, 1, coal),
			Map.of(Items.RAW_IRON, 3));
		helper.assertTrue(wrong.isEmpty(), "with Create, metals from nothing: " + wrong);
		helper.succeed();
	}

	/** Create's zinc on both sides of each boundary: 0 or 1 raw zinc, 0 or 1 crushed raw zinc (that one's there, so used). */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fCZincNamesRawZincAndUsesCrushedOnlyWhenThere"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fCZincNamesRawZincAndUsesCrushedOnlyWhenThere")
	public void zincNamesRawZincAndUsesCrushedOnlyWhenThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item raw = QaCreateRivals1115cCompatTests.create("raw_zinc");
		Item crushed = QaCreateRivals1115cCompatTests.create("crushed_raw_zinc");
		Item ingot = QaCreateRivals1115cCompatTests.create("zinc_ingot");
		Item block = QaCreateRivals1115cCompatTests.create("zinc_block");
		Item nugget = QaCreateRivals1115cCompatTests.create("zinc_nugget");
		Map<String, String> wrong = new HashMap<>();
		Map<Item, Long> coal = Map.of(Items.COAL, 4L);
		QaCreateRivals1115cCompatTests.check(wrong, "a zinc block, no zinc", Crafting.lacking(level, Crafting.Kind.WORKSHOP, block, 1, coal), Map.of(raw, 9));
		QaCreateRivals1115cCompatTests.check(wrong, "a zinc block, 1 raw zinc", Crafting.lacking(level, Crafting.Kind.WORKSHOP, block, 1,
			Map.of(Items.COAL, 4L, raw, 1L)), Map.of(raw, 8));
		QaCreateRivals1115cCompatTests.check(wrong, "9 zinc nuggets, no zinc", Crafting.lacking(level, Crafting.Kind.WORKSHOP, nugget, 9, coal), Map.of(raw, 1));
		QaCreateRivals1115cCompatTests.check(wrong, "3 zinc ingots, 3 raw zinc", Crafting.lacking(level, Crafting.Kind.WORKSHOP, ingot, 3,
			Map.of(Items.COAL, 4L, raw, 3L)), Map.of());
		QaCreateRivals1115cCompatTests.check(wrong, "3 zinc ingots, 3 crushed raw zinc", Crafting.lacking(level, Crafting.Kind.WORKSHOP, ingot, 3,
			Map.of(Items.COAL, 4L, crushed, 3L)), Map.of());
		Map<Item, Integer> oneCrushed = Crafting.lacking(level, Crafting.Kind.WORKSHOP, ingot, 3, Map.of(Items.COAL, 4L, crushed, 1L));
		int total = oneCrushed.values().stream().mapToInt(Integer::intValue).sum();
		if (total != 2 || !java.util.Set.of(raw, crushed).containsAll(oneCrushed.keySet())) {
			wrong.put("3 zinc ingots, 1 crushed raw zinc", "lacks " + oneCrushed + ", expected the crushed one used and 2 zinc short");
		}
		helper.assertTrue(wrong.isEmpty(), "Create's zinc: " + wrong);
		helper.succeed();
	}

	/**
	 * A slimeball's only crafting way with Create is Create's dough: from nothing a sticky piston, a lead and a magma cream
	 * name the Slimeball, never Dough or Wheat Flour; with a slimeball there nothing is short; with Create's dough and a
	 * lime dye there (the player has the modded thing) it may be used.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fCSlimeballChainsNameTheSlimeballUnlessDoughIsThere"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fCSlimeballChainsNameTheSlimeballUnlessDoughIsThere")
	public void slimeballChainsNameTheSlimeballUnlessDoughIsThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item dough = QaCreateRivals1115cCompatTests.create("dough");
		Map<String, String> wrong = new HashMap<>();
		QaCreateRivals1115cCompatTests.check(wrong, "a sticky piston from a piston", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.STICKY_PISTON, 1,
			Map.of(Items.PISTON, 1L)), Map.of(Items.SLIME_BALL, 1));
		QaCreateRivals1115cCompatTests.check(wrong, "a sticky piston from a piston and a slimeball", Crafting.lacking(level, Crafting.Kind.WORKSHOP,
			Items.STICKY_PISTON, 1, Map.of(Items.PISTON, 1L, Items.SLIME_BALL, 1L)), Map.of());
		QaCreateRivals1115cCompatTests.check(wrong, "a sticky piston from a piston, dough and lime dye", Crafting.lacking(level, Crafting.Kind.WORKSHOP,
			Items.STICKY_PISTON, 1, Map.of(Items.PISTON, 1L, dough, 1L, Items.LIME_DYE, 1L)), Map.of());
		QaCreateRivals1115cCompatTests.check(wrong, "2 leads from 4 string", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.LEAD, 2,
			Map.of(Items.STRING, 4L)), Map.of(Items.SLIME_BALL, 1));
		QaCreateRivals1115cCompatTests.check(wrong, "a magma cream from blaze powder", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.MAGMA_CREAM, 1,
			Map.of(Items.BLAZE_POWDER, 1L)), Map.of(Items.SLIME_BALL, 1));
		for (Item item : List.of(Items.STICKY_PISTON, Items.LEAD, Items.MAGMA_CREAM, Items.SLIME_BLOCK)) {
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, item, 1, Map.of());
			if (!lack.containsKey(Items.SLIME_BALL) || QaCreateRivals1115cCompatTests.foreign(lack)) {
				wrong.put(item + " from nothing", "lacks " + lack + ", expected the Slimeball and no Create item");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "with Create, the slimeball's line: " + wrong);
		helper.succeed();
	}

	/** With Create loaded a book, an item frame and three bookshelves with no leather name Leather; 1 hide there, the rest of the hide. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fCLeatherOrHideByWhatIsThereWithThePack"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fCLeatherOrHideByWhatIsThereWithThePack")
	public void leatherOrHideByWhatIsThereWithThePack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, String> wrong = new HashMap<>();
		Map<Item, Long> shelves = Map.of(Items.SUGAR_CANE, 27L, Items.OAK_PLANKS, 18L);
		QaCreateRivals1115cCompatTests.check(wrong, "3 bookshelves, no leather", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOKSHELF, 3, shelves),
			Map.of(Items.LEATHER, 9));
		Map<Item, Long> oneLeather = new HashMap<>(shelves);
		oneLeather.put(Items.LEATHER, 1L);
		QaCreateRivals1115cCompatTests.check(wrong, "3 bookshelves, 1 leather", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOKSHELF, 3, oneLeather),
			Map.of(Items.LEATHER, 8));
		QaCreateRivals1115cCompatTests.check(wrong, "a book, 1 hide", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1,
			Map.of(Items.PAPER, 3L, Items.RABBIT_HIDE, 1L)), Map.of(Items.RABBIT_HIDE, 3));
		QaCreateRivals1115cCompatTests.check(wrong, "a leather cap from nothing", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.LEATHER_HELMET, 1,
			Map.of()), Map.of(Items.LEATHER, 5));
		helper.assertTrue(wrong.isEmpty(), "with the pack, leather: " + wrong);
		helper.succeed();
	}

	/**
	 * Other things a player makes from planks, from nothing with Chipped loaded: each names something, and never a Chipped,
	 * Create or Cobblemon item nor an ore. With Chipped's planks there (the player has them) or one oak log, sticks lack
	 * nothing.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fCWoodThingsNameNoChippedPlanksButMayUseThem"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fCWoodThingsNameNoChippedPlanksButMayUseThem")
	public void woodThingsNameNoChippedPlanksButMayUseThem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item chipped = null;
		for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
			if (chipped == null && BuiltInRegistries.ITEM.getKey(h.value()).getNamespace().equals("chipped")) {
				chipped = h.value();
			}
		}
		helper.assertTrue(chipped != null, "setup: no Chipped planks in the planks tag");
		List<String> wrong = new ArrayList<>();
		for (Item item : List.of(Items.STICK, Items.LADDER, Items.CRAFTING_TABLE, Items.BARREL, Items.BOWL, Items.OAK_SIGN, Items.ITEM_FRAME,
			Items.PAINTING, Items.TORCH, Items.WOODEN_PICKAXE, Items.TRIPWIRE_HOOK, Items.LEVER)) {
			for (Crafting.Kind kind : List.of(Crafting.Kind.CRAFTING, Crafting.Kind.WORKSHOP)) {
				Map<Item, Integer> lack = Crafting.lacking(level, kind, item, 1, Map.of());
				boolean bad = lack.isEmpty() || QaCreateRivals1115cCompatTests.foreign(lack)
					|| lack.keySet().stream().anyMatch(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("chipped") || i.builtInRegistryHolder().is(ORES));
				if (bad && !(kind == Crafting.Kind.WORKSHOP && lack.isEmpty() && CraftJobs.maker(level, item) != CraftJobs.Job.TINKERER)) {
					wrong.add(item + " (" + kind + ") lacks " + lack);
				}
			}
		}
		Map<Item, Integer> fromChipped = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.STICK, 4, Map.of(chipped, 2L));
		if (!fromChipped.isEmpty()) {
			wrong.add("4 sticks from 2 " + chipped + " lack " + fromChipped + ", expected nothing (the planks there may be used)");
		}
		Map<Item, Integer> fromLog = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.STICK, 4, Map.of(Items.OAK_LOG, 1L));
		if (!fromLog.isEmpty()) {
			wrong.add("4 sticks from 1 oak log lack " + fromLog + ", expected nothing");
		}
		helper.assertTrue(wrong.isEmpty(), "with Chipped, wood things: " + wrong);
		helper.succeed();
	}

	/**
	 * The sweep: every block of Minecraft's that a Minecraft crafting recipe makes, from nothing at the crafting table,
	 * with the whole pack loaded, one block a tick so each gets a whole plan's trials (its own batch). Its recipes are
	 * Minecraft's, so the line names only Minecraft's things (what a player gathers), never another mod's item reached by
	 * going down a way that uses nothing there, nor an ore.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '2400' '"qaShortGathers1115fSweep"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 2400, batch = "qaShortGathers1115fSweep")
	public void everyVanillaBlockFromNothingNamesOnlyVanillaThings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		List<String> nothing = new ArrayList<>();
		// Minecraft's blocks that Minecraft's own crafting recipes make (obsidian, say, has only Cobblemon's: not asked)
		java.util.Set<Item> made = new java.util.LinkedHashSet<>();
		for (var holder : level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
			Item out = holder.value().getResultItem(level.registryAccess()).getItem();
			if (holder.id().getNamespace().equals("minecraft") && BuiltInRegistries.ITEM.getKey(out).getNamespace().equals("minecraft")
				&& out instanceof net.minecraft.world.item.BlockItem) {
				made.add(out);
			}
		}
		java.util.Iterator<Item> next = made.iterator();
		helper.onEachTick(() -> {
			if (!next.hasNext()) {
				return;
			}
			Item item = next.next();
			String id = BuiltInRegistries.ITEM.getKey(item).getPath();
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, item, 1, Map.of());
			if (lack.isEmpty()) {
				nothing.add(id);
			}
			for (Item k : lack.keySet()) {
				if (!BuiltInRegistries.ITEM.getKey(k).getNamespace().equals("minecraft") || k.builtInRegistryHolder().is(ORES)) {
					wrong.add(id + " " + lack);
					break;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!next.hasNext(), "still asking");
			int asked = made.size();
			helper.assertTrue(asked > 300 && nothing.size() * 10 < asked, "setup: " + asked + " of Minecraft's blocks asked, " + nothing.size()
				+ " named nothing: " + nothing);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " of Minecraft's blocks from nothing name another mod's item or an ore: " + wrong);
			org.slf4j.LoggerFactory.getLogger("QaShortageGathers1115f").info("QaShortageGathers1115f sweep: {} blocks asked, {} named nothing: {}", asked,
				nothing.size(), nothing);
		});
	}

	/** Through the player's entry point: a builder short of 1 Sticky Piston, a piston and a coal in his chest: the exact sentence. */
	//$ gametest_ticks_batch AREA '1400' '"qaShortGathers1115fSticky"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaShortGathers1115fSticky")
	public void aStickyPistonShortReadsExactlyOneSlimeball(GameTestHelper helper) {
		QaCreateRivals1115cCompatTests.exactLine(helper, Blocks.STICKY_PISTON, "qa1115fsticky", "Can't be made yet: short of 1 Slimeball to make 1 Sticky Piston",
			new ItemStack(Items.PISTON, 1), new ItemStack(Items.COAL, 1));
	}

	/**
	 * Through the player's entry point: a builder short of a Block of Copper with a coal and no ore: the line names the Raw
	 * Copper, never Crushed Raw Copper, and the Tinkerer takes nothing.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaShortGathers1115fCopper"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaShortGathers1115fCopper")
	public void aCopperBlockShortNamesRawCopper(GameTestHelper helper) {
		helper.assertTrue(CraftJobs.maker(helper.getLevel(), Items.COPPER_BLOCK) == CraftJobs.Job.TINKERER,
			"setup: a Block of Copper's maker is " + CraftJobs.maker(helper.getLevel(), Items.COPPER_BLOCK) + ", expected the Tinkerer");
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = {new ItemStack(Items.COAL, 1)};
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.COPPER_BLOCK, 1, 1,
			"qa1115fcopper"), Blocks.CHEST.defaultBlockState(), QaCreateRivals1115cCompatTests.copies(items));
		Villager t = QaCreateRivals1115cCompatTests.tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of 9 Raw Copper") && !text.contains("Crushed"),
				"the builder's line: '" + text + "', expected it to start 'Can't be made yet: short of 9 Raw Copper' and name nothing Crushed; site "
					+ QaCreateRivals1115cCompatTests.site(helper, a));
			QaCreateRivals1115cCompatTests.tookNothing(helper, t, items);
			helper.succeed();
		});
	}
}
