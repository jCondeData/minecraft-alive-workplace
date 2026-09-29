package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** A worker who dies leaves a grave; an Undertaker brings them back, job, level and name kept. */
public class GraveGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A named Journeyman builder dies: a grave with them in it; the undertaker brings them back with a golden apple. */
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "undertakerBringsAWorkerBack")
	public void undertakerBringsAWorkerBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 16));
		Jobs.employ(level, builder, helper.absolutePos(new BlockPos(3, 2, 3)), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		builder.setVillagerData(builder.getVillagerData().setLevel(3));
		builder.setVillagerXp(80);
		builder.setCustomName(Component.literal("Mira"));
		UUID id = builder.getUUID();
		builder.kill();
		List<BlockPos> graves = Graves.near(level, helper.absolutePos(new BlockPos(16, 2, 16)), 8);
		helper.assertTrue(graves.size() == 1, graves.size() + " graves");
		BlockPos grave = graves.get(0);
		helper.assertTrue(level.getBlockEntity(grave) instanceof GraveBlockEntity g && g.villagerLevel() == 3, "the grave doesn't hold a Journeyman");
		// The undertaker, with a golden apple in the chest by the table
		helper.setBlock(new BlockPos(11, 2, 5), ModBlocks.UNDERTAKERS_TABLE);
		helper.setBlock(new BlockPos(11, 2, 3), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(11, 2, 3));
		chest.setItem(0, new ItemStack(Items.GOLDEN_APPLE, 2));
		Villager undertaker = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 6));
		Jobs.employ(level, undertaker, helper.absolutePos(new BlockPos(11, 2, 5)), ModVillagers.UNDERTAKERS_TABLE_POI, ModVillagers.UNDERTAKER);
		helper.succeedWhen(() -> {
			helper.assertTrue(!level.getBlockState(grave).is(ModBlocks.GRAVE), "the grave is still there");
			Villager back = level.getEntity(id) instanceof Villager v ? v : null;
			helper.assertTrue(back != null && back.isAlive(), "Mira isn't back");
			helper.assertTrue(back.getVillagerData().getProfession() == ModVillagers.BUILDER && back.getVillagerData().getLevel() == 3,
				"back as " + back.getVillagerData());
			helper.assertTrue(back.getCustomName() != null && back.getCustomName().getString().equals("Mira"), "the name is lost");
			helper.assertTrue(chest.countItem(Items.GOLDEN_APPLE) == 1, "golden apples left: " + chest.countItem(Items.GOLDEN_APPLE));
			helper.assertTrue(undertaker.getAttachedOrElse(ModAttachments.VILLAGERS_REVIVED, 0) == 1, "revived: "
				+ undertaker.getAttachedOrElse(ModAttachments.VILLAGERS_REVIVED, 0));
		});
	}

	/** A villager without a job or a name leaves no grave; nor does a child. */
	@GameTest(template = AREA, timeoutTicks = 100)
	public void onlyWorkersLeaveGraves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager nobody = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 15));
		child.setAge(-24000);
		nobody.kill();
		child.kill();
		helper.assertTrue(Graves.near(level, helper.absolutePos(new BlockPos(10, 2, 10)), 16).isEmpty(), "a grave was left");
		helper.succeed();
	}
}
