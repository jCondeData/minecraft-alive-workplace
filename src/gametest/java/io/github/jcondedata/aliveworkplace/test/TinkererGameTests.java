package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.TinkererWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Tinkerers: redstone and iron parts from raw ore, and iron golems mended. */
public class TinkererGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** The workshop's recipes: ore fired into ingots on the way to a hopper; worn tools are never melted down. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void workshopRecipesFireOreButNotTools(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var hopper = Crafting.plan(level, Crafting.Kind.WORKSHOP, Items.HOPPER, 1, Map.of(Items.RAW_IRON, 5L, Items.OAK_PLANKS, 8L));
		helper.assertTrue(hopper != null && hopper.count() == 1, "no hopper from raw iron and planks");
		helper.assertTrue(hopper.takes().equals(Map.of(Items.RAW_IRON, 5, Items.OAK_PLANKS, 8)), "took " + hopper.takes());
		helper.assertTrue(hopper.steps().stream().filter(step -> Crafting.isFired(level, step)).mapToInt(Crafting.Step::times).sum() == 5,
			"five ore should be fired: " + hopper.steps());
		helper.assertTrue(Crafting.plan(level, Crafting.Kind.CRAFTING, Items.HOPPER, 1, Map.of(Items.RAW_IRON, 5L, Items.OAK_PLANKS, 8L)) == null,
			"a carpenter can't fire ore");
		helper.assertTrue(Crafting.plan(level, Crafting.Kind.WORKSHOP, Items.IRON_NUGGET, 9, Map.of(Items.IRON_SWORD, 1L)) == null, "melted a sword down");
		// A lantern three steps down: ore into an ingot, into nuggets, into the lantern.
		var lantern = Crafting.plan(level, Crafting.Kind.WORKSHOP, Items.LANTERN, 1, Map.of(Items.RAW_IRON, 1L, Items.TORCH, 1L));
		helper.assertTrue(lantern != null && lantern.count() == 1, "no lantern from raw iron");
		helper.assertTrue(new ItemStack(Items.PISTON).is(TinkererWork.TINKERING) && !new ItemStack(Items.OAK_DOOR).is(TinkererWork.TINKERING),
			"the tinkering tag should have pistons and not doors");
		helper.succeed();
	}

	/** A badly hurt iron golem: the tinkerer fetches iron ingots and patches it up, one at a time. */
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "tinkerer_golem")
	public void tinkererMendsTheIronGolem(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.TINKERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
		Villager tinkerer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), tinkerer, helper.absolutePos(BENCH), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(9, 2, 9));
		golem.setNoAi(true);
		golem.setHealth(30f);
		helper.succeedWhen(() -> {
			helper.assertTrue(golem.getHealth() >= golem.getMaxHealth(), "the golem's at " + golem.getHealth());
			helper.assertTrue(tinkerer.getAttachedOrElse(ModAttachments.GOLEM_REPAIRS, 0) == 3, "patched " + tinkerer.getAttachedOrElse(ModAttachments.GOLEM_REPAIRS, 0));
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 2, "two ingots should be left, not " + chest.countItem(Items.IRON_INGOT));
			golem.discard();
		});
	}
}
