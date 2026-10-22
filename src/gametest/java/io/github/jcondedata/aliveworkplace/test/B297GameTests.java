package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
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
 * B297, B298 and B299, the owner's layout: a Storehouse he placed himself, with vanilla chests round it and a Blueprint
 * Table of its own one block from them, and the Builder's Workshop's table some 31 blocks off. With a builder at the
 * Storehouse's table, the far builder counted its chests as that builder's own (their reserve for his builds), so he
 * neither counted nor took the Storehouse's stone. Every builder, at either table, now counts and takes it, and when a
 * Storehouse isn't counted the status says why.
 */
public class B297GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** The Builder's Workshop's table, with its own chest. */
	private static final BlockPos FAR_TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos FAR_CHEST = new BlockPos(2, 2, 3);
	private static final BlockPos FAR_HUT = new BlockPos(6, 2, 6);
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final BlockPos[] STORE_CHESTS = {new BlockPos(23, 2, 26), new BlockPos(26, 2, 23), new BlockPos(23, 2, 23)};
	/** The Storehouse's own table, one block from its chests, nearer the far table than the Storehouse block is. */
	private static final BlockPos STORE_TABLE = new BlockPos(24, 2, 24);
	private static final BlockPos NEAR_HUT = new BlockPos(14, 2, 18);
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-000000000297");

	private record Builder(Villager villager, BuildSite site, BuildPlan plan) {
	}

	/**
	 * The owner's case (B298): a builder at each table, both his. The far one's overhead line, Book and Quill list and
	 * status count the Storehouse's stone and door (they were "needed": the near builder's builds reserved them), the near
	 * one counts them as his own, and both huts get built from the Storehouse.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"b297Both"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "b297Both")
	public void bothBuildersCountAndTakeTheStorehouseWithItsOwnTable(GameTestHelper helper) {
		ServerLevel level = setup(helper, 50, 2, 55, 1);
		Builder far = builder(helper, FAR_TABLE, FAR_HUT);
		Builder near = builder(helper, STORE_TABLE, NEAR_HUT);
		Village.forget(far.villager());
		Village.forget(near.villager());
		assertCountsTheStore(helper, level, far, true);
		assertCountsTheStore(helper, level, near, false);
		helper.succeedWhen(() -> {
			assertBuilt(helper, level, far);
			assertBuilt(helper, level, near);
			for (Item item : new Item[] {Items.COBBLESTONE, Items.OAK_DOOR}) {
				helper.assertTrue(left(helper, item) == 0, "the storehouse still holds " + left(helper, item) + " " + item);
			}
		});
	}

	/**
	 * B299: the far builder alone, the Storehouse's table free. He counts and takes the Storehouse's stone from the
	 * workshop table, without breaking it first; after a save and reload of the Storehouse he still does.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"b297Far"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b297Far")
	public void aBuilderAtTheWorkshopTableTakesFromAStorehouseWithAFreeTable(GameTestHelper helper) {
		ServerLevel level = setup(helper, 25, 1, 0, 0);
		Builder far = builder(helper, FAR_TABLE, FAR_HUT);
		// the Storehouse saved and loaded (a server restart): still his
		StorehouseBlockEntity store = (StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE);
		net.minecraft.nbt.CompoundTag tag = store.saveWithFullMetadata(level.registryAccess());
		store.loadWithComponents(tag, level.registryAccess());
		Village.forget(far.villager());
		assertCountsTheStore(helper, level, far, true);
		helper.succeedWhen(() -> {
			assertBuilt(helper, level, far);
			helper.assertTrue(left(helper, Items.COBBLESTONE) == 0, "the storehouse still holds " + left(helper, Items.COBBLESTONE) + " cobblestone");
		});
	}

	/**
	 * The village's own builder (nobody hired him: a Builder's Workshop's, or the Steward's) on a village build, at the
	 * workshop table, and the Storehouse the player placed, with no Village Hall a player owns: the player's Storehouse is
	 * that village's store, so he counts and takes it. Before, he counted only his table's chests.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"b297Village"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b297Village")
	public void theVillagesOwnBuilderCountsThePlayersStorehouse(GameTestHelper helper) {
		ServerLevel level = setup(helper, 25, 1, 0, 0);
		Builder far = builder(helper, FAR_TABLE, FAR_HUT, false);
		helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(far.villager()) == null && far.site().ownerName().isEmpty(), "not the village's own builder and build");
		Village.forget(far.villager());
		assertCountsTheStore(helper, level, far, true);
		helper.succeedWhen(() -> {
			assertBuilt(helper, level, far);
			helper.assertTrue(left(helper, Items.COBBLESTONE) == 0, "the storehouse still holds " + left(helper, Items.COBBLESTONE) + " cobblestone");
		});
	}

	/** A Storehouse beyond the village's reach isn't counted, and the status says it's too far, and how far. */
	//$ gametest_batch HUGE '"b297TooFar"'
	@GameTest(template = HUGE, batch = "b297TooFar")
	public void theStatusSaysAStorehouseIsTooFar(GameTestHelper helper) {
		ServerLevel level = setup(helper, 25, 1, 0, 0);
		Leftovers.village(helper, 20); // the workshop table is 34 blocks from the Storehouse
		Builder far = builder(helper, FAR_TABLE, FAR_HUT);
		Village.forget(far.villager());
		Component status = Builders.statusText(level, far.site(), far.villager());
		helper.assertTrue(!far.site().missing().isEmpty(), "the Storehouse 34 blocks away was counted with a 20-block village");
		helper.assertTrue(has(status, "message.aliveworkplace.status.storehouse_far"), "the status doesn't say the Storehouse is too far: " + status.getString());
		helper.succeed();
	}

	/** Someone else's Storehouse isn't counted, and the status says it isn't his village's. */
	//$ gametest_batch HUGE '"b297Stranger"'
	@GameTest(template = HUGE, batch = "b297Stranger")
	public void theStatusSaysAStrangersStorehouseIsntHis(GameTestHelper helper) {
		ServerLevel level = setup(helper, 25, 1, 0, 0);
		((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(UUID.fromString("5f1b2c3d-0000-4000-8000-000000000298"), "Stranger");
		Builder far = builder(helper, FAR_TABLE, FAR_HUT);
		Village.forget(far.villager());
		Component status = Builders.statusText(level, far.site(), far.villager());
		helper.assertTrue(!far.site().missing().isEmpty(), "a stranger's Storehouse was counted");
		helper.assertTrue(has(status, "message.aliveworkplace.status.storehouse_not_shared"), "the status doesn't say whose it is: " + status.getString());
		helper.succeed();
	}

	/** A Storehouse with no chest he can read round it: the status says so. */
	//$ gametest_batch HUGE '"b297NoChests"'
	@GameTest(template = HUGE, batch = "b297NoChests")
	public void theStatusSaysAStorehouseHasNoChests(GameTestHelper helper) {
		ServerLevel level = setup(helper, 25, 1, 0, 0);
		for (BlockPos chest : STORE_CHESTS) {
			helper.setBlock(chest, Blocks.AIR);
		}
		Builder far = builder(helper, FAR_TABLE, FAR_HUT);
		Village.forget(far.villager());
		Component status = Builders.statusText(level, far.site(), far.villager());
		helper.assertTrue(has(status, "message.aliveworkplace.status.storehouse_no_chests"), "the status doesn't say the Storehouse has no chests: " + status.getString());
		helper.succeed();
	}

	// --- helpers -----------------------------------------------------------------------------------------------------

	private static boolean has(Component text, String key) {
		if (text.getContents() instanceof TranslatableContents t) {
			if (t.getKey().equals(key)) {
				return true;
			}
			for (Object a : t.getArgs()) {
				if (a instanceof Component c && has(c, key)) {
					return true;
				}
			}
		}
		for (Component sibling : text.getSiblings()) {
			if (has(sibling, key)) {
				return true;
			}
		}
		return false;
	}

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	/** The Storehouse Jesse placed, its chests holding the given stock, its own table, and the workshop table's chest. */
	private static ServerLevel setup(GameTestHelper helper, int cobble, int doors, int planks, int torches) {
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
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		ensurePoi(level, helper.absolutePos(STOREHOUSE));
		((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(JESSE, "Jesse");
		for (BlockPos chest : STORE_CHESTS) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		fill(helper.getBlockEntity(STORE_CHESTS[0]), new ItemStack(Items.COBBLESTONE, cobble));
		fill(helper.getBlockEntity(STORE_CHESTS[2]), new ItemStack(Items.OAK_DOOR, doors));
		if (planks > 0) {
			fill(helper.getBlockEntity(STORE_CHESTS[1]), new ItemStack(Items.OAK_PLANKS, Math.min(64, planks)), new ItemStack(Items.TORCH, torches));
		}
		helper.setBlock(STORE_TABLE, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(STORE_TABLE));
		helper.setBlock(FAR_TABLE, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(FAR_TABLE));
		helper.setBlock(FAR_CHEST, Blocks.CHEST);
		fill(helper.getBlockEntity(FAR_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		return level;
	}

	/** Jesse's builder at {@code table}, building the test hut at {@code hut} for him. */
	private static Builder builder(GameTestHelper helper, BlockPos table, BlockPos hut) {
		return builder(helper, table, hut, true);
	}

	/** A builder at {@code table} building the test hut: Jesse's, for him, or the village's own on a village build. */
	private static Builder builder(GameTestHelper helper, BlockPos table, BlockPos hut, boolean jesses) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, table.east());
		Builders.employ(level, villager, helper.absolutePos(table));
		if (jesses) {
			ModAttachments.BUILDER_EMPLOYER.set(villager, new Employer(JESSE, "Jesse"));
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hut), Rotation.NONE, Mirror.NONE);
		BuildSite site = jesses ? Builders.start(level, villager, JESSE, "Jesse", TEST_HUT, placement)
			: Builders.start(level, villager, null, TEST_HUT, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		return new Builder(villager, site, plan);
	}

	/** The overhead's "Short of" line, the Book and Quill list and the status count what the Storehouse holds. */
	private static void assertCountsTheStore(GameTestHelper helper, ServerLevel level, Builder b, boolean namesStorehouse) {
		String who = namesStorehouse ? "the workshop builder" : "the Storehouse's builder";
		List<Component> more = BuilderStatusSync.status(level, b.site(), b.villager()).more();
		helper.assertTrue(!more.isEmpty() && more.get(0).getContents() instanceof TranslatableContents t
			&& t.getKey().equals("message.aliveworkplace.overhead.has_all"), who + "'s overhead doesn't count the Storehouse: " + more);
		if (namesStorehouse) {
			helper.assertTrue(more.size() == 2 && more.get(1).getContents() instanceof TranslatableContents t2
				&& t2.getKey().equals("message.aliveworkplace.overhead.supplies_storehouse"), who + "'s overhead doesn't name the storehouse: " + more);
		}
		for (BlueprintSupplies.Line line : SiteList.checklist(level, b.site(), b.villager()).lines()) {
			helper.assertTrue(line.toBring() == 0, who + "'s Book and Quill list asks for " + line.toBring() + " " + line.item());
		}
		Component status = Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(b.site().missing().isEmpty(), who + "'s status says he needs " + b.site().missing());
		helper.assertTrue(!has(status, "message.aliveworkplace.status.storehouse_far") && !has(status, "message.aliveworkplace.status.storehouse_not_shared")
			&& !has(status, "message.aliveworkplace.status.storehouse_no_chests"), who + "'s status says a counted Storehouse isn't: " + status.getString());
	}

	private static void assertBuilt(GameTestHelper helper, ServerLevel level, Builder b) {
		helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building at " + b.site().bench() + ": stage=" + b.site().stage()
			+ " status=" + b.site().status() + " missing=" + b.site().missing());
		List<BlockPos> unfinished = b.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	private static int left(GameTestHelper helper, Item item) {
		int left = 0;
		for (BlockPos chest : STORE_CHESTS) {
			left += ((Container) helper.getBlockEntity(chest)).countItem(item);
		}
		return left;
	}

	private static void fill(Object container, ItemStack... items) {
		for (int i = 0; i < items.length; i++) {
			((Container) container).setItem(i, items[i].copy());
		}
	}
}
