package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * 23.3: one look at a builder says what its build is short of (the 3 biggest, with counts) and where it takes materials
 * from, under its progress. The test hut needs 25 cobblestone, 55 oak planks, a door and a torch.
 */
public class OverheadSupplyGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	/** 18+ blocks from the bench: its chest is the porter's, not one of the builder's own. */
	private static final BlockPos STOREHOUSE = new BlockPos(24, 2, 24);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);

	private record Setup(ServerLevel level, Villager builder, BuildSite site) {
		List<Component> more() {
			BuilderStatusSync.Status status = BuilderStatusSync.status(level, site, builder);
			return status == null ? List.of() : status.more();
		}
	}

	/** Short of three kinds: the biggest first with its count, then the chest it takes from and where its bench is. */
	//$ gametest_ticks_batch AREA '40' '"theOverheadNamesWhatABuildIsShortOf"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "theOverheadNamesWhatABuildIsShortOf")
	public void theOverheadNamesWhatABuildIsShortOf(GameTestHelper helper) {
		Leftovers.clear(helper);
		Setup s = setup(helper, new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 5));
		List<Component> more = s.more();
		helper.assertTrue(more.size() == 2, "expected the short-of and takes-from lines, got " + more);
		TranslatableContents missing = key(more.get(0), "message.aliveworkplace.overhead.missing");
		String list = ((Component) missing.getArgs()[0]).getString();
		helper.assertTrue(list.startsWith("50× Oak Planks"), "the biggest shortage (50 planks) isn't first: " + list);
		helper.assertTrue(list.contains("1× Oak Door") && list.contains("1× Torch"), "the door and torch aren't listed: " + list);
		helper.assertFalse(list.contains("Cobblestone"), "cobblestone is all there but listed: " + list);
		TranslatableContents from = key(more.get(1), "message.aliveworkplace.overhead.supplies");
		key((Component) from.getArgs()[0], "message.aliveworkplace.overhead.chest");
		BlockPos bench = helper.absolutePos(BENCH);
		helper.assertTrue(((Component) from.getArgs()[1]).getString().equals(bench.getX() + " " + bench.getY() + " " + bench.getZ()),
			"the bench's place isn't named: " + more.get(1).getString());
		helper.succeed();
	}

	/** More than 3 kinds short: the 3 biggest and "and N more". */
	//$ gametest_ticks_batch AREA '40' '"theOverheadNamesOnlyTheThreeBiggest"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "theOverheadNamesOnlyTheThreeBiggest")
	public void theOverheadNamesOnlyTheThreeBiggest(GameTestHelper helper) {
		Leftovers.clear(helper);
		Setup s = setup(helper);
		String line = s.more().get(0).getString();
		helper.assertTrue(line.startsWith("Short of: 55× Oak Planks, 25× Cobblestone, 1× "), "not the 3 biggest, biggest first: " + line);
		helper.assertTrue(line.endsWith(" and 1 more"), "the fourth kind isn't counted as 'and 1 more': " + line);
		helper.succeed();
	}

	/** Everything in the chests: it says so. Emptied, the line catches up within a few seconds. */
	//$ gametest_ticks_batch AREA '200' '"theOverheadFollowsTheChests"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "theOverheadFollowsTheChests")
	public void theOverheadFollowsTheChests(GameTestHelper helper) {
		Leftovers.clear(helper);
		Setup s = setup(helper, new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH));
		helper.setDayTime(13000); // evening: the builder doesn't take anything out meanwhile
		key(s.more().get(0), "message.aliveworkplace.overhead.has_all");
		((Container) helper.getBlockEntity(CHEST)).clearContent();
		helper.succeedWhen(() -> {
			Component line = s.more().get(0);
			helper.assertTrue(line.getContents() instanceof TranslatableContents t && t.getKey().equals("message.aliveworkplace.overhead.missing"),
				"still says " + line.getString() + " after the chest was emptied");
			helper.assertTrue(helper.getTick() <= BuilderStatusSync.SUPPLY_INTERVAL + 20, "took over " + BuilderStatusSync.SUPPLY_INTERVAL + " ticks");
		});
	}

	/** A porter's storehouse in the village (what the builder's own chests lack comes from there) is named, with its place. */
	//$ gametest_ticks_batch HUGE_AREA '40' '"theOverheadNamesTheStorehouse"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40, batch = "theOverheadNamesTheStorehouse")
	public void theOverheadNamesTheStorehouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.village(helper, 48); // village sharing, off in tests by default
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STOREHOUSE.east(), Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, STOREHOUSE.south());
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, porter, helper.absolutePos(STOREHOUSE),
			io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.PORTER);
		Setup s = setup(helper, new ItemStack(Items.COBBLESTONE, 25));
		Component line = s.more().get(1);
		TranslatableContents from = key(line, "message.aliveworkplace.overhead.supplies_storehouse");
		key((Component) from.getArgs()[0], "message.aliveworkplace.overhead.chest");
		BlockPos store = helper.absolutePos(STOREHOUSE);
		helper.assertTrue(line.getString().endsWith(", and the storehouse at " + store.getX() + " " + store.getY() + " " + store.getZ()),
			"reads: " + line.getString());
		helper.succeed();
	}

	/** No chests by the bench but a storehouse: it says it takes from the storehouse. */
	//$ gametest_ticks_batch HUGE_AREA '40' '"theOverheadSaysItTakesFromTheStorehouseOnly"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40, batch = "theOverheadSaysItTakesFromTheStorehouseOnly")
	public void theOverheadSaysItTakesFromTheStorehouseOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.village(helper, 48); // village sharing, off in tests by default
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STOREHOUSE.east(), Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, STOREHOUSE.south());
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, porter, helper.absolutePos(STOREHOUSE),
			io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.PORTER);
		Setup s = setup(helper);
		helper.setBlock(CHEST, Blocks.AIR);
		key(s.more().get(1), "message.aliveworkplace.overhead.storehouse_only");
		helper.succeed();
	}

	/** No chest by the bench: it says there are none, in red. */
	//$ gametest_ticks_batch AREA '40' '"theOverheadSaysWhenThereAreNoChests"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "theOverheadSaysWhenThereAreNoChests")
	public void theOverheadSaysWhenThereAreNoChests(GameTestHelper helper) {
		Leftovers.clear(helper);
		Setup s = setup(helper);
		helper.setBlock(CHEST, Blocks.AIR); // before the first look: nothing is worked out yet
		List<Component> more = s.more();
		helper.assertTrue(more.size() == 2, "expected the short-of and no-chests lines, got " + more);
		TranslatableContents none = key(more.get(1), "message.aliveworkplace.overhead.no_supplies");
		helper.assertTrue(more.get(1).getStyle().getColor() != null
			&& more.get(1).getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.RED)),
			"the no-chests line isn't red");
		helper.assertTrue(none.getArgs().length == 1, "no bench position");
		helper.succeed();
	}

	/** Free materials (the game rule): nothing to fetch, so no materials lines. */
	//$ gametest_ticks_batch AREA '40' '"theOverheadHasNoMaterialsLinesWhenTheyAreFree"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "theOverheadHasNoMaterialsLinesWhenTheyAreFree")
	public void theOverheadHasNoMaterialsLinesWhenTheyAreFree(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean was = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(was, level.getServer()));
		Setup s = setup(helper);
		helper.assertTrue(s.more().isEmpty(), "materials lines with free materials: " + s.more());
		helper.succeed();
	}

	private static Setup setup(GameTestHelper helper, ItemStack... chest) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container box = (Container) helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			box.setItem(i, chest[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(
			level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		return new Setup(level, builder, site);
	}

	private static TranslatableContents key(Component line, String key) {
		if (!(line.getContents() instanceof TranslatableContents t) || !t.getKey().equals(key)) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("expected " + key + ", got " + line.getString() + " (" + line.getContents() + ")");
		}
		return t;
	}
}
