package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.Porters;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B226: a Builder whose bench has no chests builds from the village Storehouse's chests some 30 blocks away (the owner's
 * overhead said "No chests by its bench" with everything in the Storehouse), also after a save and reload, and while the
 * Storehouse's porter is out of reach (asleep at home far off, in a chunk that isn't loaded).
 */
public class B226GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	/** About 34 blocks from the bench, with its chests around it. */
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final BlockPos[] STORE_CHESTS = {new BlockPos(23, 2, 26), new BlockPos(26, 2, 23), new BlockPos(23, 2, 23)};

	private record Setup(ServerLevel level, Villager builder, Villager porter, BuildSite site, BuildPlan plan) {
	}

	/** No chest by the bench, the hut's materials in the Storehouse's chests: it says so overhead, and builds the hut. */
	//$ gametest_ticks_batch HUGE '3000' '"b226Far"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b226Far")
	public void aBuilderWithNoChestsBuildsFromTheStorehouse(GameTestHelper helper) {
		Setup s = setup(helper);
		List<Component> more = BuilderStatusSync.status(s.level(), s.site(), s.builder()).more();
		helper.assertTrue(more.size() == 2 && more.get(1).getContents() instanceof TranslatableContents t
			&& t.getKey().equals("message.aliveworkplace.overhead.storehouse_only"), "the overhead doesn't name the storehouse: " + more);
		helper.succeedWhen(() -> assertBuiltFromStore(helper, s));
	}

	/** Saved and loaded mid-build (the builder and the porter both): it still finds the Storehouse and finishes. */
	//$ gametest_ticks_batch HUGE '3000' '"b226Reload"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b226Reload")
	public void aBuilderWithNoChestsBuildsFromTheStorehouseAfterAReload(GameTestHelper helper) {
		Setup first = setup(helper);
		Setup[] s = {first};
		helper.runAfterDelay(40, () -> {
			Villager builder = reload(first.level(), first.builder());
			Villager porter = reload(first.level(), first.porter());
			BuildSite site = BuildSiteManager.get(first.level()).get(first.site().id());
			s[0] = new Setup(first.level(), builder, porter, site == null ? first.site() : site, first.plan());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 40, "not reloaded yet");
			assertBuiltFromStore(helper, s[0]);
		});
	}

	/**
	 * The porter is out of reach (asleep at home beyond the village, its chunk unloaded: here gone from the world): the
	 * Storehouse is still the village's, and the builder still builds from it.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"b226PorterAway"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b226PorterAway")
	public void aBuilderBuildsFromTheStorehouseWhileItsPorterIsAway(GameTestHelper helper) {
		Setup s = setup(helper);
		s.porter().discard();
		io.github.jcondedata.aliveworkplace.work.Village.forget(s.builder());
		helper.succeedWhen(() -> assertBuiltFromStore(helper, s));
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}

	private static Setup setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.storehouses(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		for (BlockPos chest : STORE_CHESTS) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		fill(helper.getBlockEntity(STORE_CHESTS[0]), new ItemStack(Items.COBBLESTONE, 25));
		fill(helper.getBlockEntity(STORE_CHESTS[1]), new ItemStack(Items.OAK_PLANKS, 55));
		fill(helper.getBlockEntity(STORE_CHESTS[2]), new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH));
		Villager porter = helper.spawn(EntityType.VILLAGER, STOREHOUSE.south());
		Porters.employ(level, porter, helper.absolutePos(STOREHOUSE));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		return new Setup(level, builder, porter, site, plan);
	}

	private static void assertBuiltFromStore(GameTestHelper helper, Setup s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage=" + s.site().stage()
			+ " status=" + s.site().status() + " missing=" + s.site().missing());
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
		for (Item item : new Item[] {Items.COBBLESTONE, Items.OAK_PLANKS, Items.OAK_DOOR, Items.TORCH}) {
			int left = 0;
			for (BlockPos chest : STORE_CHESTS) {
				left += ((Container) helper.getBlockEntity(chest)).countItem(item);
			}
			helper.assertTrue(left == 0, "the storehouse still holds " + left + " " + item);
		}
	}

	private static void fill(Object container, ItemStack... items) {
		for (int i = 0; i < items.length; i++) {
			((Container) container).setItem(i, items[i].copy());
		}
	}
}
