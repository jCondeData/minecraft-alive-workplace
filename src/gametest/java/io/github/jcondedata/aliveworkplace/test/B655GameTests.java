package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialLedger;
import io.github.jcondedata.aliveworkplace.command.Soak;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B655: the soak's {@link MaterialLedger} hears of a crafting job's conversions. A Carpenter crafting ladders for a
 * waiting builder (planks to sticks to ladders) and a Mason firing clay into brick stairs (with the kiln's coal burnt)
 * used to show as items off ("ladder +3, oak_planks -4, stick +1") in the city soak; with the ledger on, a fair
 * conversion now adds up to "none". Each test has its own batch: the ledger and the jobs' claims are shared.
 */
public class B655GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);

	/** A Carpenter makes the builder's three ladders from four planks (two stick crafts, one ladder craft): nothing off. */
	//$ gametest_ticks_batch AREA '3600' '"b655Ladders"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "b655Ladders")
	public void aCarpentersLaddersForABuilderAreNotItemsOff(GameTestHelper helper) {
		prepare(helper);
		ItemStack[] stock = {new ItemStack(Items.OAK_PLANKS, 4)};
		BuildSite site = asker(helper, Blocks.LADDER, stock);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		MaterialLedger.start();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(site.id()) == null, "still building: " + site.status() + " missing=" + site.missing());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "the carpenter's job isn't over yet");
			helper.assertTrue(MaterialLedger.gained().getOrDefault(Items.LADDER, 0) >= 3, "the ledger should have heard of the 3 ladders made: " + MaterialLedger.gained());
			assertNothingOff(helper, stock, carpenter);
		});
	}

	/** A Mason fires twelve clay into bricks with two coal, lays and cuts them into the builder's brick stairs: nothing off. */
	//$ gametest_ticks_batch AREA '3600' '"b655Kiln"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "b655Kiln")
	public void aMasonsKilnStairsForABuilderAreNotItemsOff(GameTestHelper helper) {
		prepare(helper);
		ItemStack[] stock = {new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.COAL, 2)};
		BuildSite site = asker(helper, Blocks.BRICK_STAIRS, stock);
		helper.setBlock(STATION, Blocks.STONECUTTER);
		helper.setBlock(STATION.east(), Blocks.FURNACE);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		MaterialLedger.start();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(site.id()) == null, "still building: " + site.status() + " missing=" + site.missing());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(mason) == null, "the mason's job isn't over yet");
			helper.assertTrue(MaterialLedger.used().getOrDefault(Items.COAL, 0) > 0, "the ledger should have heard of the kiln's coal: " + MaterialLedger.used());
			assertNothingOff(helper, stock, mason);
		});
	}

	/** The ledger off: a crafter's making counts nothing (it costs nothing outside a soak). */
	//$ gametest_ticks_batch AREA '3600' '"b655Off"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "b655Off")
	public void aCraftWithTheLedgerOffCountsNothing(GameTestHelper helper) {
		prepare(helper);
		MaterialLedger.start();
		MaterialLedger.stop();
		BuildSite site = asker(helper, Blocks.LADDER, new ItemStack(Items.OAK_PLANKS, 4));
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(site.id()) == null, "still building: " + site.status());
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) > 0, "the carpenter made nothing");
			helper.assertTrue(MaterialLedger.used().isEmpty() && MaterialLedger.gained().isEmpty() && MaterialLedger.dropped().isEmpty(),
				"the ledger counted with the soak off: used " + MaterialLedger.used() + ", gained " + MaterialLedger.gained());
		});
	}

	// --- helpers ---------------------------------------------------------------------------------------------------

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
	}

	/** Stocked + gained − used (and eaten) − dropped must be what's in the chest and the two villagers' bags. */
	private static void assertNothingOff(GameTestHelper helper, ItemStack[] stock, Villager crafter) {
		Map<Item, Integer> stocked = new HashMap<>();
		for (ItemStack s : stock) {
			stocked.merge(s.getItem(), s.getCount(), Integer::sum);
		}
		Map<Item, Integer> left = new HashMap<>();
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack s = chest.getItem(i);
			if (!s.isEmpty()) {
				left.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4))) {
			for (ItemStack s : ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()) {
				if (!s.isEmpty()) {
					left.merge(s.getItem(), s.getCount(), Integer::sum);
				}
			}
		}
		Map<Item, Integer> used = new HashMap<>(MaterialLedger.used());
		MaterialLedger.eaten().forEach((item, n) -> used.merge(item, n, Integer::sum));
		String off = Soak.itemsOff(stocked, MaterialLedger.gained(), used, MaterialLedger.dropped(), left);
		MaterialLedger.stop();
		helper.assertTrue(off.equals("none"), "items off: " + off + " (used " + MaterialLedger.used() + ", gained " + MaterialLedger.gained()
			+ ", dropped " + MaterialLedger.dropped() + ", left " + left + ")");
	}

	/** A builder at the bench with {@code items} in the chest, building a row of three {@code block}. */
	private static BuildSite asker(GameTestHelper helper, Block block, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ORIGIN.offset(x, 0, 1), Blocks.STONE); // a wall behind (a ladder hangs on it), left standing
			helper.setBlock(ORIGIN.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"b655_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ORIGIN), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ORIGIN.offset(x, 0, 0), Blocks.AIR);
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return site;
	}
}
