package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.SiteList;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane b, 2026-10-24), from the specs of B226, B290 and B297-B299 ("a builder counts AND takes from the village
 * Storehouse whichever table he works from", "the status names the nearest Storehouse not counted and why"), not from
 * the code: the stock split between the bench's chest and the Storehouse, the Storehouse emptied after the builder counted
 * it and filled again, and the village's reach from both sides of its edge, with the sentence a player reads there.
 */
public class QaBuilderStockGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	static final BlockPos TABLE_CHEST = new BlockPos(2, 2, 3);
	static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final BlockPos[] STORE_CHESTS = {new BlockPos(23, 2, 26), new BlockPos(26, 2, 23)};
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-0000000b1024");

	record Builder(Villager villager, BuildSite site, BuildPlan plan) {
	}

	/**
	 * Part of the hut's cobblestone in the table's own chest and the rest in the Storehouse 34 blocks off: neither alone is
	 * enough, together they are. Nothing is "needed" on the status or the Book and Quill list, and the hut gets built from
	 * both, every cobblestone used.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaStockSplit"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaStockSplit")
	public void theStockSplitBetweenTheTableAndTheStorehouseBuildsTheHut(GameTestHelper helper) {
		ServerLevel level = setup(helper, STOREHOUSE, 15, 1);
		fill(helper.getBlockEntity(TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH), new ItemStack(Items.COBBLESTONE, 10));
		Builder b = builder(helper, HUT);
		Village.forget(b.villager());
		Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(b.site().missing().isEmpty(), "with 10 cobblestone by the table and 15 in the Storehouse the status still needs " + b.site().missing());
		for (BlueprintSupplies.Line line : SiteList.checklist(level, b.site(), b.villager()).lines()) {
			helper.assertTrue(line.toBring() == 0, "the Book and Quill list asks for " + line.toBring() + " " + line.item() + " that the table and the Storehouse hold between them");
		}
		helper.succeedWhen(() -> {
			assertBuilt(helper, level, b);
			int atTable = ((Container) helper.getBlockEntity(TABLE_CHEST)).countItem(Items.COBBLESTONE);
			helper.assertTrue(left(helper, Items.COBBLESTONE) == 0 && atTable == 0,
				"cobblestone left over after the hut: " + atTable + " by the table, " + left(helper, Items.COBBLESTONE) + " in the Storehouse");
		});
	}

	/**
	 * The builder counted the Storehouse's cobblestone, then a player took it all out before he fetched any: he doesn't build
	 * those blocks out of nothing (free materials off), he waits and says what he needs, and once the cobblestone is put
	 * back he finishes the hut.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaStockEmptied"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaStockEmptied")
	public void aStorehouseEmptiedAfterItWasCountedHoldsTheBuilderUntilItIsFilledAgain(GameTestHelper helper) {
		ServerLevel level = setup(helper, STOREHOUSE, 25, 1);
		fill(helper.getBlockEntity(TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		Builder b = builder(helper, HUT);
		Village.forget(b.villager());
		Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(b.site().missing().isEmpty(), "the full Storehouse wasn't counted: " + b.site().missing());
		Container store = (Container) helper.getBlockEntity(STORE_CHESTS[0]);
		store.setItem(0, ItemStack.EMPTY); // a player takes the cobblestone out
		store.setChanged();
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) != null, "the hut was finished with no cobblestone anywhere");
			java.util.Set<BlockPos> cobbleSpots = new java.util.HashSet<>();
			for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
				b.plan().steps(stage).stream().filter(s -> s.state().is(Blocks.COBBLESTONE)).forEach(s -> cobbleSpots.add(s.pos()));
			}
			long cobble = b.plan().unfinished(level).stream().filter(cobbleSpots::contains).count();
			helper.assertTrue(cobble > 0, "no cobblestone block of the hut is left to build at tick 900, with none in any chest");
			Village.forget(b.villager());
			Builders.statusText(level, b.site(), b.villager());
			helper.assertTrue(b.site().missing().containsKey(Items.COBBLESTONE),
				"waiting with no cobblestone, the status doesn't say he needs it: " + b.site().missing());
			store.setItem(0, new ItemStack(Items.COBBLESTONE, 25)); // put back
			store.setChanged();
		});
		helper.runAtTickTime(901, () -> helper.succeedWhen(() -> {
			assertBuilt(helper, level, b);
			helper.assertTrue(left(helper, Items.COBBLESTONE) < 25, "the cobblestone put back was never taken");
		}));
	}

	/** A Storehouse exactly at the village's reach from the table (20 blocks, a 20-block village) is counted. */
	//$ gametest_batch HUGE '"qaStockEdgeIn"'
	@GameTest(template = HUGE, batch = "qaStockEdgeIn")
	public void aStorehouseRightAtTheVillagesReachIsCounted(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(22, 2, 2); // 20 blocks from the table
		ServerLevel level = setup(helper, storehouse, 25, 1);
		Leftovers.village(helper, 20);
		fill(helper.getBlockEntity(TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		Builder b = builder(helper, HUT);
		Village.forget(b.villager());
		Component status = Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(b.site().missing().isEmpty(), "a Storehouse 20 blocks off in a 20-block village wasn't counted: needs " + b.site().missing());
		helper.assertTrue(find(status, "message.aliveworkplace.status.storehouse_far") == null, "the status calls a Storehouse at the reach too far: " + status.getString());
		helper.succeed();
	}

	/**
	 * With the village switched off (`villageRadius` 0: no sharing), a builder whose table's chest lacks the cobblestone
	 * doesn't count or take a Storehouse's: he says he needs it, and the Storehouse still holds all of it a while later.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaStockNoVillage"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaStockNoVillage")
	public void withNoVillageSharingTheStorehouseIsNotTaken(GameTestHelper helper) {
		ServerLevel level = setup(helper, STOREHOUSE, 25, 1);
		Leftovers.village(helper, 0);
		fill(helper.getBlockEntity(TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		Builder b = builder(helper, HUT);
		Village.forget(b.villager());
		Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(b.site().missing().containsKey(Items.COBBLESTONE), "with villageRadius 0 the Storehouse's cobblestone was counted: needs " + b.site().missing());
		helper.runAtTickTime(800, () -> {
			helper.assertTrue(left(helper, Items.COBBLESTONE) == 25, "with villageRadius 0 the builder took " + (25 - left(helper, Items.COBBLESTONE)) + " cobblestone from the Storehouse");
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) != null, "the hut was finished without the Storehouse's cobblestone");
			helper.succeed();
		});
	}

	/**
	 * The Storehouse block is broken right after the builder counted it: its chests are just chests again, not his and not
	 * the village's, so he waits for cobblestone and leaves them alone. Placed back (and Jesse's again), it feeds him and
	 * the hut gets built.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaStockBroken"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaStockBroken")
	public void aStorehouseBrokenMidJobStopsFeedingTheBuilderUntilPlacedBack(GameTestHelper helper) {
		ServerLevel level = setup(helper, STOREHOUSE, 25, 1);
		fill(helper.getBlockEntity(TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		Builder b = builder(helper, HUT);
		Village.forget(b.villager());
		Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(b.site().missing().isEmpty(), "the Storehouse wasn't counted before it was broken: " + b.site().missing());
		helper.destroyBlock(STOREHOUSE);
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(left(helper, Items.COBBLESTONE) == 25, "the builder took " + (25 - left(helper, Items.COBBLESTONE)) + " cobblestone from a broken Storehouse's chests");
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) != null, "the hut was finished with the Storehouse broken");
			helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
			ensurePoi(level, helper.absolutePos(STOREHOUSE));
			((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(JESSE, "Jesse");
			Village.forget(b.villager());
		});
		helper.runAtTickTime(901, () -> helper.succeedWhen(() -> {
			assertBuilt(helper, level, b);
			helper.assertTrue(left(helper, Items.COBBLESTONE) == 0, "the Storehouse placed back still holds " + left(helper, Items.COBBLESTONE) + " cobblestone");
		}));
	}

	/** A Storehouse kept in barrels, not chests, is counted and taken from just the same. */
	//$ gametest_ticks_batch HUGE '3000' '"qaStockBarrels"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaStockBarrels")
	public void aStorehouseOfBarrelsFeedsTheBuilder(GameTestHelper helper) {
		ServerLevel level = setup(helper, STOREHOUSE, 0, 0);
		for (BlockPos chest : STORE_CHESTS) {
			helper.setBlock(chest, Blocks.BARREL);
		}
		fill(helper.getBlockEntity(STORE_CHESTS[0]), new ItemStack(Items.COBBLESTONE, 25));
		fill(helper.getBlockEntity(STORE_CHESTS[1]), new ItemStack(Items.OAK_DOOR, 1));
		fill(helper.getBlockEntity(TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		Builder b = builder(helper, HUT);
		Village.forget(b.villager());
		Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(b.site().missing().isEmpty(), "a Storehouse of barrels wasn't counted: needs " + b.site().missing());
		helper.succeedWhen(() -> {
			assertBuilt(helper, level, b);
			helper.assertTrue(left(helper, Items.COBBLESTONE) == 0, "the barrels still hold " + left(helper, Items.COBBLESTONE) + " cobblestone");
		});
	}

	/**
	 * The Storehouse with its Porter at work there: the village knows it twice over (by the Porter's job and by its block),
	 * but its stock counts once. Five cobblestone short of the hut, the status still needs exactly those five.
	 */
	//$ gametest_batch HUGE '"qaStockOnce"'
	@GameTest(template = HUGE, batch = "qaStockOnce")
	public void aStorehouseWithItsPorterIsCountedOnce(GameTestHelper helper) {
		ServerLevel level = setup(helper, STOREHOUSE, 0, 1);
		Villager porter = helper.spawn(EntityType.VILLAGER, STOREHOUSE.south());
		io.github.jcondedata.aliveworkplace.store.Porters.employ(level, porter, helper.absolutePos(STOREHOUSE));
		fill(helper.getBlockEntity(TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		Builder b = builder(helper, HUT);
		int needed = 0;
		for (BuildPlan.Stage stage : new BuildPlan.Stage[] {BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION}) {
			needed += (int) b.plan().steps(stage).stream().filter(s -> s.state().is(Blocks.COBBLESTONE)).count();
		}
		helper.assertTrue(needed > 10, "the test hut has only " + needed + " cobblestone");
		fill(helper.getBlockEntity(STORE_CHESTS[0]), new ItemStack(Items.COBBLESTONE, needed - 5));
		Village.forget(b.villager());
		Village.forget(porter);
		Builders.statusText(level, b.site(), b.villager());
		Integer short_ = b.site().missing().get(Items.COBBLESTONE);
		helper.assertTrue(short_ != null && short_ == 5, "the hut takes " + needed + " cobblestone, the Storehouse with its Porter holds " + (needed - 5)
			+ ", yet the status needs " + short_ + " (the Storehouse counted twice?)");
		helper.succeed();
	}

	// --- helpers -----------------------------------------------------------------------------------------------------

	static Object arg(Object a) {
		return a instanceof Component c ? c.getString() : a;
	}

	static TranslatableContents find(Component text, String key) {
		if (text.getContents() instanceof TranslatableContents t) {
			if (t.getKey().equals(key)) {
				return t;
			}
			for (Object a : t.getArgs()) {
				if (a instanceof Component c) {
					TranslatableContents inner = find(c, key);
					if (inner != null) {
						return inner;
					}
				}
			}
		}
		for (Component sibling : text.getSiblings()) {
			TranslatableContents inner = find(sibling, key);
			if (inner != null) {
				return inner;
			}
		}
		return null;
	}

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	/** Jesse's Storehouse at {@code storehouse}, its chests holding the cobblestone and doors, and the table with its chest. */
	static ServerLevel setup(GameTestHelper helper, BlockPos storehouse, int cobble, int doors) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		Leftovers.village(helper, 48);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		ensurePoi(level, helper.absolutePos(storehouse));
		((StorehouseBlockEntity) helper.getBlockEntity(storehouse)).setOwner(JESSE, "Jesse");
		BlockPos[] chests = storehouse.equals(STOREHOUSE) ? STORE_CHESTS : new BlockPos[] {storehouse.east(), storehouse.south()};
		for (BlockPos chest : chests) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		fill(helper.getBlockEntity(chests[0]), new ItemStack(Items.COBBLESTONE, cobble));
		fill(helper.getBlockEntity(chests[1]), new ItemStack(Items.OAK_DOOR, doors));
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(TABLE));
		helper.setBlock(TABLE_CHEST, Blocks.CHEST);
		return level;
	}

	/** Jesse's builder at the table, building the test hut at {@code hut} for him. */
	static Builder builder(GameTestHelper helper, BlockPos hut) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, villager, helper.absolutePos(TABLE));
		ModAttachments.BUILDER_EMPLOYER.set(villager, new Employer(JESSE, "Jesse"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hut), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, JESSE, "Jesse", TEST_HUT, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		return new Builder(villager, site, plan);
	}

	private static void assertBuilt(GameTestHelper helper, ServerLevel level, Builder b) {
		helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building at " + b.site().bench() + ": stage=" + b.site().stage()
			+ " status=" + b.site().status() + " missing=" + b.site().missing());
		List<BlockPos> unfinished = b.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	/** What the building tests' Storehouse (at {@link #STOREHOUSE}) still holds. */
	private static int left(GameTestHelper helper, Item item) {
		int left = 0;
		for (BlockPos chest : STORE_CHESTS) {
			left += ((Container) helper.getBlockEntity(chest)).countItem(item);
		}
		return left;
	}

	static void fill(Object container, ItemStack... items) {
		for (int i = 0; i < items.length; i++) {
			((Container) container).setItem(i, items[i].copy());
		}
	}
}
