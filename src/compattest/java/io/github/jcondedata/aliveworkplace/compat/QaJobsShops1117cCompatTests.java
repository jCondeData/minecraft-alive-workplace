package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1117-1253), with the compat pack, written from the bugs' text: B1334 (a Mason cuts andesite only from what a recipe
 * cuts, and his builder's line never reads "Nobody here makes: Andesite"), B1346 and B1347 (the sentences that name the
 * CobbleDollar rate read the config file's rate, clamped, singular at 1 and plural above). B1290's neighbour (mixed
 * planks) and B1347's (Money.describe) are in QaJobsShops1117cBugCompatTests
 * (tests/jobs-shops-1117c, B1378 and B1379).
 */
public class QaJobsShops1117cCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	// --- B1334 ------------------------------------------------------------------------------------------------------------

	/**
	 * A mixed chest: a builder short of 3 andesite with 3 polished andesite and 1 of Create's cut andesite, a Mason at his
	 * stonecutter. He cuts exactly the 1 andesite a recipe makes, the polished andesite stays, and the builder's line never
	 * reads "Nobody here makes" beside him.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaJobsShops1117cMixedAndesite"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaJobsShops1117cMixedAndesite")
	public void aMasonCutsTheOneAndesiteARecipeMakesAndNeverReadsNobodyMakes(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Item cut = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "cut_andesite"));
		helper.assertTrue(cut != Items.AIR, "setup: Create's cut andesite isn't registered");
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.ANDESITE, 3, 1,
			"qa1117cmixed"), Blocks.CHEST.defaultBlockState(), new ItemStack(Items.POLISHED_ANDESITE, 3), new ItemStack(cut, 1));
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(station), PoiTypes.MASON, VillagerProfession.MASON);
		List<String> nobody = new ArrayList<>();
		helper.onEachTick(() -> {
			if (helper.getLevel().getGameTime() % 20 == 0) {
				String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
				if (text != null && (text.contains("Nobody here makes") || text.contains("Andesite to make"))) {
					nobody.add(text);
				}
			}
		});
		helper.runAfterDelay(1500, () -> {
			Container chest = helper.getBlockEntity(QaCreateRivals1115cCompatTests.CHEST);
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0);
			String state = "line '" + QaCrafterOwnModdedStore1111eCompatTests.line(helper, a) + "', site " + QaCreateRivals1115cCompatTests.site(helper, a)
				+ ", chest: " + chest.countItem(Items.POLISHED_ANDESITE) + " polished, " + chest.countItem(cut) + " cut";
			helper.assertTrue(nobody.isEmpty(), "the builder's line read: " + nobody.stream().findFirst().orElse("") + "; " + state);
			helper.assertTrue(made == 1, "the Mason cut " + made + " andesite, expected the 1 from the cut andesite; " + state);
			helper.assertTrue(chest.countItem(cut) == 0 && chest.countItem(Items.POLISHED_ANDESITE) == 3, "the chest after: " + state);
			helper.succeed();
		});
	}

	// --- B1346, B1347 -----------------------------------------------------------------------------------------------------

	/**
	 * The rate from the config file, through the player's hand: dollarsPerEmerald 2 and 10,000, then 0 (clamped to 1) and
	 * 50,000 (clamped to 10,000). The placed counter's welcome line names it; the treasury's hint is singular at 1 and
	 * plural at 2 and above, thousands marked. The default config brings 100 back.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaJobsShops1117cRate"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaJobsShops1117cRate")
	public void theConfigFilesRateReadsRightOnTheCounterAndTheTreasury(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaDaycareWaiting1115d3CompatTests.login(helper, seen, UUID.randomUUID(), "qaRate1117c");
		int[] rates = {2, 10_000, 0, 50_000};
		String[] shown = {"2", "10,000", "1", "10,000"};
		String[] hints = {
			"Click to give 2 CobbleDollars (shift-click: 128), or click with emeralds",
			"Click to give 10,000 CobbleDollars (shift-click: 640,000), or click with emeralds",
			"Click to give 1 CobbleDollar (shift-click: 64), or click with emeralds",
			"Click to give 10,000 CobbleDollars (shift-click: 640,000), or click with emeralds"};
		List<String> wrong = new ArrayList<>();
		try {
			for (int i = 0; i < rates.length; i++) {
				WorkplaceConfig.parse("{\"configVersion\": 5, \"dollarsPerEmerald\": " + rates[i] + "}").apply();
				seen.clear();
				BlockPos floor = helper.absolutePos(new BlockPos(1 + 2 * i, 1, 1));
				ItemStack stack = new ItemStack(ModBlocks.SHOP_COUNTER.asItem());
				player.setItemInHand(InteractionHand.MAIN_HAND, stack);
				stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
				helper.assertTrue(helper.getLevel().getBlockState(floor.above()).is(ModBlocks.SHOP_COUNTER), "the counter wasn't placed at " + rates[i]);
				if (!seen.equals(List.of(DollarRateTextCompatTests.placed(shown[i])))) {
					wrong.add("counter at dollarsPerEmerald " + rates[i] + ": " + seen);
				}
				String hint = io.github.jcondedata.aliveworkplace.realm.RealmTreasury.giveHint().getString();
				if (!hint.equals(hints[i])) {
					wrong.add("treasury at dollarsPerEmerald " + rates[i] + ": '" + hint + "'");
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(Money.DOLLARS_PER_EMERALD == 100, "the default config left the rate at " + Money.DOLLARS_PER_EMERALD);
		helper.assertTrue(wrong.isEmpty(), "the rate's sentences: " + wrong);
		helper.succeed();
	}
}
