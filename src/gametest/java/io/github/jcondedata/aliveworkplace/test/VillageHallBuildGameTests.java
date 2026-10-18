package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Village Hall build (tools/blueprints/village_hall.py): three levels on the same spot, each with the Village Hall
 * block at its heart. A builder who finishes the first leaves a hall that works like one a player put down: the village
 * forms round it and it is the player's who handed over the blueprint, with its founding in the chronicle.
 */
public class VillageHallBuildGameTests implements FabricGameTest {
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 9);
	/** Where every level keeps the hall block, and the porch's bell. */
	private static final BlockPos HALL = new BlockPos(6, 1, 10);
	private static final BlockPos BELL = new BlockPos(6, 4, 0);
	private static final List<StarterBlueprints.Entry> LEVELS = List.of(StarterBlueprints.VILLAGE_HALL, StarterBlueprints.VILLAGE_HALL_2,
		StarterBlueprints.VILLAGE_HALL_3);

	/** Each level: the hall block (no saved data) at the same spot facing the door, the bell, upgrades in order, no expansion gate, a hall on the map, a name. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyLevelKeepsTheHallAtItsHeart(GameTestHelper helper) {
		for (StarterBlueprints.Entry entry : LEVELS) {
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow();
			List<Blueprint.Entry> halls = blueprint.blocks().stream().filter(e -> e.state().is(ModBlocks.VILLAGE_HALL)).toList();
			helper.assertTrue(halls.size() == 1, entry.id() + " has " + halls.size() + " Village Halls");
			Blueprint.Entry hall = halls.get(0);
			helper.assertTrue(hall.pos().equals(HALL) && hall.state().getValue(VillageHallBlock.FACING) == Direction.NORTH && hall.nbt() == null,
				entry.id() + "'s hall: " + hall);
			helper.assertTrue(blueprint.blocks().stream().anyMatch(e -> e.pos().equals(BELL) && e.state().is(Blocks.BELL)), entry.id() + " has no bell under the porch");
			helper.assertTrue(StarterBlueprints.ALL.contains(entry) && StarterBlueprints.offered(entry.id()) && StarterBlueprints.expansionSwitch(entry.id()) == null,
				entry.id() + " isn't offered from the start");
			helper.assertTrue(VillageMaps.kindOf(entry.id()).orElse(null) == VillageMaps.Kind.HALL, entry.id() + " is on the map as " + VillageMaps.kindOf(entry.id()));
			helper.assertTrue(net.minecraft.locale.Language.getInstance().has("blueprint.aliveworkplace." + entry.id().getPath()), "no name for " + entry.id());
		}
		helper.assertTrue(BlueprintUpgrades.upgradeOf(StarterBlueprints.VILLAGE_HALL.id()).equals(StarterBlueprints.VILLAGE_HALL_2.id())
			&& BlueprintUpgrades.upgradeOf(StarterBlueprints.VILLAGE_HALL_2.id()).equals(StarterBlueprints.VILLAGE_HALL_3.id()), "the upgrades");
		helper.succeed();
	}

	/** A builder handed Village Hall I by a player builds it whole; the hall it leaves is the village's, the player's, and founded. */
	//$ gametest_ticks_batch BIG_AREA '30000' '"village_hall_build"'
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "village_hall_build")
	public void aFinishedVillageHallWorksLikeAPlacedOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		// an earlier batch's Village Hall left outside its own area (one stood at 3, 2, -33 here after the 37.5/37.6 merge
		// reordered the batches) would already be the builder's village
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Blueprint blueprint = BlueprintLibrary.get(level, StarterBlueprints.VILLAGE_HALL.id()).orElseThrow();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		BlockPos[] barrels = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
		helper.assertTrue(stock.size() <= barrels.length * 27, "Village Hall I's materials don't fit four barrels: " + stock.size() + " stacks");
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BuildSite site = Builders.start(level, builder, player, StarterBlueprints.VILLAGE_HALL.id(), placement);
		BlockPos hall = helper.absolutePos(ORIGIN.offset(HALL));
		helper.assertFalse(VillageHalls.nearest(level, builder.blockPosition()).isPresent(), "a hall before the build");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + Math.round(site.progress(plan) * 100) + "%");
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) wrong after the build");
			BlockState state = level.getBlockState(hall);
			helper.assertTrue(state.is(ModBlocks.VILLAGE_HALL) && state.getValue(VillageHallBlock.FACING) == Direction.NORTH, "the hall block: " + state);
			helper.assertTrue(VillageHalls.nearest(level, builder.blockPosition()).filter(hall::equals).isPresent(),
				"the builder's village: " + VillageHalls.nearest(level, builder.blockPosition()));
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(entity != null && player.getUUID().equals(entity.owner()), "the hall's owner: " + (entity == null ? null : entity.owner()));
			helper.assertFalse(entity.chronicle().isEmpty(), "no founding in the chronicle");
		});
	}
}
