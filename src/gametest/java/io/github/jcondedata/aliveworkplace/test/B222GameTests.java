package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.grave.Graveyards;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * B222: graves lying about the village go to its Graveyard. The Undertaker walks to a grave in the street and lays it in
 * the next free row slot of the Graveyard (everything in it kept), brings the villager back from there, keeps at it when
 * reloaded mid-move, leaves graves where they fall with {@code gravesToGraveyard} off, and without a Graveyard uses a
 * small plot near the hall.
 */
public class B222GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos YARD = new BlockPos(14, 2, 14);
	private static final BlockPos TABLE = new BlockPos(20, 2, 24);
	private static final BlockPos CHEST = new BlockPos(22, 2, 24);
	private static final BlockPos STREET = new BlockPos(4, 2, 25);

	/** A Journeyman Builder named Mira dies in the street: their grave, at {@code STREET}. */
	private static BlockPos mirasGrave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(2, 2, 12), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, STREET);
		Jobs.employ(level, builder, helper.absolutePos(new BlockPos(2, 2, 12)), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		builder.setVillagerData(builder.getVillagerData().setLevel(3));
		builder.setVillagerXp(80);
		builder.setCustomName(Component.literal("Mira"));
		builder.kill();
		List<BlockPos> graves = Graves.near(level, helper.absolutePos(STREET), 4);
		helper.assertTrue(graves.size() == 1, graves.size() + " graves in the street");
		return graves.get(0);
	}

	private static Villager undertaker(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.UNDERTAKERS_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager undertaker = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Jobs.employ(helper.getLevel(), undertaker, helper.absolutePos(TABLE), ModVillagers.UNDERTAKERS_TABLE_POI, ModVillagers.UNDERTAKER);
		return undertaker;
	}

	private static BlueprintData.Placement graveyard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(YARD), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GRAVEYARD.id(), placement, UUID.randomUUID());
		return placement;
	}

	/** The first row slot of the Graveyard at YARD: its own (1, 0, 6). */
	private static BlockPos firstSlot(GameTestHelper helper) {
		return helper.absolutePos(YARD.offset(1, 0, 6));
	}

	private static int gravesIn(GameTestHelper helper) {
		return Graves.near(helper.getLevel(), helper.absolutePos(new BlockPos(15, 2, 15)), 30).size();
	}

	/**
	 * A grave in the street, no golden apple yet: the Undertaker lays it in the Graveyard's first row slot, Mira inside as
	 * she was; then, with a golden apple in the chest, brings her back from there, name, job and level kept.
	 */
	//$ gametest_ticks_batch AREA '3000' '"b222GraveToGraveyard"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "b222GraveToGraveyard")
	public void strayGraveGoesToTheGraveyardAndIsRevivedThere(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		Graveyards.ENABLED = true; // (off in GameTests by default: WorkplaceConfig.apply)
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlueprintData.Placement placement = graveyard(helper);
		BlockPos street = mirasGrave(helper);
		CompoundTag was = ((GraveBlockEntity) level.getBlockEntity(street)).villager().copy();
		UUID mira = was.getUUID("UUID");
		Villager undertaker = undertaker(helper);
		Container chest = helper.getBlockEntity(CHEST);
		BlockPos slot = firstSlot(helper);
		boolean[] laid = {false};
		helper.succeedWhen(() -> {
			if (!laid[0]) {
				helper.assertTrue(!level.getBlockState(street).is(ModBlocks.GRAVE), "the grave is still in the street");
				helper.assertTrue(level.getBlockEntity(slot) instanceof GraveBlockEntity g && g.villager().equals(was)
					&& g.name().getString().equals("Mira") && g.villagerLevel() == 3, "Mira isn't in the first row slot, as she was");
				helper.assertTrue(gravesIn(helper) == 1, gravesIn(helper) + " graves: " + Graves.near(helper.getLevel(), helper.absolutePos(new BlockPos(15, 2, 15)), 30));
				laid[0] = true;
				chest.setItem(0, new ItemStack(Items.GOLDEN_APPLE));
			}
			Villager back = level.getEntity(mira) instanceof Villager v ? v : null;
			helper.assertTrue(back != null && back.isAlive(), "Mira isn't back from the graveyard");
			helper.assertTrue(back.getVillagerData().getProfession() == ModVillagers.BUILDER && back.getVillagerData().getLevel() == 3,
				"back as " + back.getVillagerData());
			helper.assertTrue(back.getCustomName() != null && back.getCustomName().getString().equals("Mira"), "the name is lost");
			helper.assertTrue(!level.getBlockState(slot).is(ModBlocks.GRAVE), "the graveyard grave is still there");
			BuildSiteManager.get(level).forgetFinished(placement);
			undertaker.discard();
			Graveyards.ENABLED = false;
		});
	}

	/**
	 * The Undertaker is saved and loaded again (a server restart) while on the way to the grave: the new one carries on, the
	 * grave reaches the Graveyard, there's still exactly one, and it saves and loads with everything in it.
	 */
	//$ gametest_ticks_batch AREA '2400' '"b222ReloadMidMove"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b222ReloadMidMove")
	public void reloadMidMoveKeepsTheGrave(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		Graveyards.ENABLED = true; // (off in GameTests by default: WorkplaceConfig.apply)
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlueprintData.Placement placement = graveyard(helper);
		BlockPos street = mirasGrave(helper);
		CompoundTag was = ((GraveBlockEntity) level.getBlockEntity(street)).villager().copy();
		Villager first = undertaker(helper);
		BlockPos slot = firstSlot(helper);
		helper.runAfterDelay(90, () -> {
			helper.assertTrue(level.getBlockState(street).is(ModBlocks.GRAVE), "moved before the reload: too early to test it");
			CompoundTag saved = new CompoundTag();
			first.saveWithoutId(saved);
			saved.putString("id", "minecraft:villager");
			first.discard();
			Entity again = EntityType.loadEntityRecursive(saved, level, e -> e);
			helper.assertTrue(again instanceof Villager, "the undertaker didn't load");
			again.setUUID(UUID.randomUUID());
			level.addFreshEntity(again);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!level.getBlockState(street).is(ModBlocks.GRAVE), "the grave is still in the street");
			helper.assertTrue(level.getBlockState(slot).is(ModBlocks.GRAVE), "no grave in the first row slot");
			helper.assertTrue(gravesIn(helper) == 1, gravesIn(helper) + " graves: " + Graves.near(helper.getLevel(), helper.absolutePos(new BlockPos(15, 2, 15)), 30));
			BlockEntity laid = level.getBlockEntity(slot);
			CompoundTag disk = laid.saveWithFullMetadata(level.registryAccess());
			BlockEntity loaded = BlockEntity.loadStatic(slot, level.getBlockState(slot), disk, level.registryAccess());
			helper.assertTrue(loaded instanceof GraveBlockEntity g && g.villager().equals(was) && g.name().getString().equals("Mira")
				&& g.villagerLevel() == 3, "the grave lost something saving and loading");
			BuildSiteManager.get(level).forgetFinished(placement);
			Graveyards.ENABLED = false;
		});
	}

	/** With gravesToGraveyard off, the grave stays in the street. */
	//$ gametest_ticks_batch AREA '700' '"b222SwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "b222SwitchOff")
	public void switchOffLeavesGravesWhereTheyFall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlueprintData.Placement placement = graveyard(helper);
		BlockPos street = mirasGrave(helper);
		undertaker(helper);
		io.github.jcondedata.aliveworkplace.WorkplaceConfig off = io.github.jcondedata.aliveworkplace.WorkplaceConfig.parse("{\"gravesToGraveyard\": false}");
		helper.assertTrue(!off.gravesToGraveyard && new io.github.jcondedata.aliveworkplace.WorkplaceConfig().gravesToGraveyard, "the switch: on by default, off in the file");
		Graveyards.ENABLED = off.gravesToGraveyard;
		helper.runAfterDelay(600, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			helper.assertTrue(level.getBlockState(street).is(ModBlocks.GRAVE), "the grave left the street with the switch off");
			helper.assertTrue(!level.getBlockState(firstSlot(helper)).is(ModBlocks.GRAVE), "a grave in the graveyard with the switch off");
			helper.succeed();
		});
	}

	/** A village with a hall and no Graveyard: the grave goes to the first slot of a small plot near the hall. */
	//$ gametest_ticks_batch AREA '2400' '"b222HallPlot"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b222HallPlot")
	public void noGraveyardMeansAPlotNearTheHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		Graveyards.ENABLED = true; // (off in GameTests by default: WorkplaceConfig.apply)
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(15, 2, 15));
		helper.setBlock(new BlockPos(15, 2, 15), ModBlocks.VILLAGE_HALL);
		BlockPos street = mirasGrave(helper);
		undertaker(helper);
		List<BlockPos> plot = Graveyards.plot(level, hall);
		helper.assertTrue(plot.size() == 12, "a plot of " + plot.size());
		helper.assertTrue(!plot.contains(street), "the street grave is on the plot");
		for (BlockPos p : plot) {
			int d = Math.max(Math.abs(p.getX() - hall.getX()), Math.abs(p.getZ() - hall.getZ()));
			helper.assertTrue(d <= 20 + 6, "a plot slot " + d + " blocks from the hall");
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(!level.getBlockState(street).is(ModBlocks.GRAVE), "the grave is still in the street");
			helper.assertTrue(level.getBlockEntity(plot.get(0)) instanceof GraveBlockEntity g && g.name().getString().equals("Mira"),
				"Mira isn't in the plot's first slot");
			helper.assertTrue(gravesIn(helper) == 1, gravesIn(helper) + " graves: " + Graves.near(helper.getLevel(), helper.absolutePos(new BlockPos(15, 2, 15)), 30));
			Graveyards.ENABLED = false;
		});
	}
}
