package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1116-1238), with the compat pack, a bug past B1308 (B1334). crafter.md: a stone a player mines "is cut from
 * another form of it only when one is in the chest (B1308)". The pack's only way to cut andesite is Create's
 * {@code create:andesite_from_stone_types_andesite_stonecutting}, whose tag {@code #create:stone_types/andesite} holds
 * Create's andesite forms (cut andesite, its bricks, layered andesite, the pillar...) and andesite itself, but not
 * Minecraft's polished andesite: polishing is one way, for a player at a stonecutter too. So the Mason cuts andesite from
 * cut andesite in the chest, never from polished andesite; and with only polished andesite to hand, the builder's line
 * never reads "Nobody here makes: Andesite" beside the Mason who makes it (crafter.md: only what no job makes says that),
 * nor names andesite as its own making: the stone reads as gathered, as the Storehouse says.
 */
public class QaShortageLine1116bBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	private static Item cutAndesite() {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "cut_andesite"));
	}

	/** A real plan: 3 andesite cut from 3 of Create's cut andesite; none from polished andesite, which no recipe cuts back. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaShortLine1116bPolishedPlan"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaShortLine1116bPolishedPlan")
	public void threeCutAndesitePlanThreeAndesite(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			Crafting.freshPlanning(helper.getLevel());
			Item cut = cutAndesite();
			helper.assertTrue(cut != Items.AIR, "Create's cut andesite isn't registered");
			Crafting.Plan plan = Crafting.chain(helper.getLevel(), Crafting.Kind.STONECUTTING, Items.ANDESITE, 3, Map.of(cut, 3L));
			helper.assertTrue(plan != null, "no plan for 3 andesite from 3 cut andesite" + (Crafting.planningSpent(helper.getLevel())
				? " (the tick's planning ran out)" : ""));
			helper.assertTrue(plan.makes().getOrDefault(Items.ANDESITE, 0) == 3 && plan.takes().getOrDefault(cut, 0) == 3,
				"3 andesite from 3 cut andesite planned as " + plan);
			Crafting.freshPlanning(helper.getLevel());
			helper.assertTrue(Crafting.chain(helper.getLevel(), Crafting.Kind.STONECUTTING, Items.ANDESITE, 3, Map.of(Items.POLISHED_ANDESITE, 3L)) == null,
				"a plan cut andesite from polished andesite, which no recipe in the pack does");
			Crafting.freshPlanning(helper.getLevel());
			Map<Item, Integer> lacks = Crafting.lacking(helper.getLevel(), Crafting.Kind.STONECUTTING, Items.ANDESITE, 3, Map.of(Items.POLISHED_ANDESITE, 3L));
			helper.assertTrue(!lacks.containsKey(Items.ANDESITE), "3 polished andesite for 3 andesite named andesite itself: " + lacks);
			helper.succeed();
		});
	}

	/**
	 * Through the player's entry point: a builder short of 3 andesite with 3 cut andesite in his chest and a Mason at his
	 * stonecutter. The Mason cuts the andesite, and the builder's line never names andesite as its own making.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaShortLine1116bPolishedMason"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaShortLine1116bPolishedMason")
	public void theMasonCutsAndesiteFromCutAndesiteInTheChest(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.ANDESITE, 3, 1,
			"qa1116bcut"), Blocks.CHEST.defaultBlockState(), new ItemStack(cutAndesite(), 3));
		Villager mason = mason(helper);
		List<String> self = watch(helper, a);
		helper.succeedWhen(() -> {
			helper.assertTrue(self.isEmpty(), "the builder's line named andesite as its own making: " + self);
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0);
			helper.assertTrue(made >= 3, "the Mason cut " + made + " of 3 andesite from 3 cut andesite; line '"
				+ QaCrafterOwnModdedStore1111eCompatTests.line(helper, a) + "', site " + QaCreateRivals1115cCompatTests.site(helper, a));
		});
	}

	/**
	 * The same builder with 3 polished andesite instead (no recipe cuts it back): nothing is cut and the polished andesite
	 * stays, and his line, well past the 600 ticks "Nobody here makes" waits for, never reads "Nobody here makes: Andesite"
	 * beside the Mason who makes andesite, nor names andesite as its own making.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaShortLine1116bPolishedLine"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaShortLine1116bPolishedLine")
	public void polishedAndesiteBesideTheMasonNeverReadsNobodyMakes(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.ANDESITE, 3, 1,
			"qa1116bpolished"), Blocks.CHEST.defaultBlockState(), new ItemStack(Items.POLISHED_ANDESITE, 3));
		Villager mason = mason(helper);
		List<String> self = watch(helper, a);
		List<String> nobody = new ArrayList<>();
		helper.onEachTick(() -> {
			if (helper.getLevel().getGameTime() % 20 == 0) {
				String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
				if (text != null && text.contains("Nobody here makes")) {
					nobody.add(text);
				}
			}
		});
		helper.runAfterDelay(1400, () -> {
			helper.assertTrue(self.isEmpty(), "the builder's line named andesite as its own making: " + self);
			helper.assertTrue(nobody.isEmpty(), "the builder's line read 'Nobody here makes' beside the Mason: " + nobody.stream().findFirst().orElse(""));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) == 0, "the Mason made something of polished andesite");
			helper.succeed();
		});
	}

	private static Villager mason(GameTestHelper helper) {
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(station), PoiTypes.MASON, VillagerProfession.MASON);
		return mason;
	}

	/** The builder's lines that name andesite as its own making, checked every second. */
	private static List<String> watch(GameTestHelper helper, Asker a) {
		List<String> self = new ArrayList<>();
		helper.onEachTick(() -> {
			if (helper.getLevel().getGameTime() % 20 == 0) {
				String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
				if (text != null && text.contains("Andesite to make")) {
					self.add(text);
				}
			}
		});
		return self;
	}
}
