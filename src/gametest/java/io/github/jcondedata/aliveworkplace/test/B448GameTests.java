package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlock;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B448: the mod's own facing blocks (Village Hall, Storehouse, Mailbox, Shop Counter, Grave, and every other block of
 * ours with a horizontal facing) turn and mirror with a placed blueprint like vanilla's facing blocks, for every facing,
 * turn and mirror, and keep their other properties. The Grave is in no starter build, so the QA test's sweep of the
 * starter builds doesn't reach it; this checks every block of ours directly.
 */
public class B448GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Rotation TURN = Rotation.CLOCKWISE_90;
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyOwnFacingBlockTurnsAndMirrors(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		List<String> checked = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (!id.getNamespace().equals("aliveworkplace") || !block.defaultBlockState().hasProperty(HorizontalDirectionalBlock.FACING)) {
				continue;
			}
			checked.add(id.getPath());
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				BlockState drawn = block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
				for (Mirror mirror : Mirror.values()) {
					for (Rotation rotation : Rotation.values()) {
						Direction expected = rotation.rotate(mirror.mirror(facing));
						BlockState built = MaterialRules.transformed(drawn, mirror, rotation);
						if (!built.is(block) || built.getValue(HorizontalDirectionalBlock.FACING) != expected) {
							wrong.add(id.getPath() + " facing " + facing + " " + mirror + " " + rotation + ": " + built + ", expected " + expected);
						}
					}
				}
			}
		}
		for (Block block : List.<Block>of(ModBlocks.VILLAGE_HALL, ModBlocks.STOREHOUSE, ModBlocks.MAILBOX, ModBlocks.SHOP_COUNTER, ModBlocks.GRAVE)) {
			helper.assertTrue(checked.contains(BuiltInRegistries.BLOCK.getKey(block).getPath()), "not checked: " + block);
		}
		// turning keeps the block's other state: a mailbox with mail still has mail
		BlockState full = ModBlocks.MAILBOX.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
			.setValue(MailboxBlock.HAS_MAIL, true);
		BlockState turned = MaterialRules.transformed(full, Mirror.LEFT_RIGHT, Rotation.CLOCKWISE_90);
		helper.assertTrue(turned.getValue(MailboxBlock.HAS_MAIL), "a turned mailbox lost its mail flag: " + turned);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong, e.g. " + wrong.subList(0, Math.min(4, wrong.size())));
		helper.succeed();
	}

	/**
	 * A finished Storehouse built turned a quarter before this fix has its Storehouse block facing as drawn. After a
	 * restart it still counts as built: it isn't on the build's unfinished list nor in a repair, and the block, with its
	 * orders, stays as it is.
	 */
	//$ gametest_ticks_batch HUGE '100' '"b448Finished"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b448Finished")
	public void anOldFacingStorehouseInAFinishedTurnedBuildStandsAndKeepsItsData(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, StarterBlueprints.STOREHOUSE.id()).orElseThrow();
		BlueprintData.Placement placement = placement(helper, blueprint, new BlockPos(8, 2, 8));
		BlockPos store = null;
		Direction drawn = null;
		for (Blueprint.Entry e : blueprint.blocks()) {
			BlockPos at = placement.origin().offset(StructureTemplate.transform(e.pos(), Mirror.NONE, TURN, BlockPos.ZERO));
			if (e.state().is(ModBlocks.STOREHOUSE)) {
				store = at;
				drawn = e.state().getValue(HorizontalDirectionalBlock.FACING);
				level.setBlock(at, e.state(), 2); // as the builder placed it before B448: unturned
			} else {
				level.setBlock(at, MaterialRules.forPlacement(MaterialRules.transformed(e.state(), Mirror.NONE, TURN)), 2);
			}
		}
		helper.assertTrue(store != null, "the Storehouse blueprint has no Storehouse block");
		helper.assertTrue(TURN.rotate(drawn) != drawn, "the check needs a facing that turns");
		StorehouseBlockEntity entity = (StorehouseBlockEntity) level.getBlockEntity(store);
		entity.setOrders(Map.of(Items.WHEAT, 12));
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.STOREHOUSE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		restart(level);
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		BlockPos at = store;
		helper.assertTrue(!plan.unfinished(level).contains(at), "the old-facing Storehouse block counts as not built");
		BuildPlan repair = BuildPlan.repair(blueprint, placement, level, BuildSiteManager.get(level).gaveUp(placement));
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			helper.assertTrue(repair.steps(stage).stream().noneMatch(st -> st.pos().equals(at)), "a repair would redo the old-facing Storehouse block (" + stage + ")");
		}
		helper.assertTrue(BuildSiteManager.get(level).finishedAt(level, StarterBlueprints.STOREHOUSE.id(), at).isPresent(), "the finished Storehouse was forgotten");
		Direction was = drawn;
		helper.runAfterDelay(20, () -> {
			BlockState there = level.getBlockState(at);
			BlockEntity now = level.getBlockEntity(at);
			helper.assertTrue(there.is(ModBlocks.STOREHOUSE) && there.getValue(HorizontalDirectionalBlock.FACING) == was, "the Storehouse block changed: " + there);
			helper.assertTrue(now == entity && entity.orders().getOrDefault(Items.WHEAT, 0) == 12, "the Storehouse block lost its data: " + now);
			helper.succeed();
		});
	}

	/**
	 * A Storehouse going up turned a quarter, its Storehouse block already standing the old way (placed before this fix):
	 * after a restart mid-build, the builder finishes without breaking it, skipping it or losing its orders.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"b448MidBuild"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "b448MidBuild")
	public void anOldFacingStorehouseInABuildGoingUpIsKeptThroughARestart(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		Blueprint blueprint = BlueprintLibrary.get(level, StarterBlueprints.STOREHOUSE.id()).orElseThrow();
		BlueprintData.Placement placement = placement(helper, blueprint, new BlockPos(8, 2, 8));
		Blueprint.Entry entry = blueprint.blocks().stream().filter(e -> e.state().is(ModBlocks.STOREHOUSE)).findFirst().orElseThrow();
		BlockPos at = placement.origin().offset(StructureTemplate.transform(entry.pos(), Mirror.NONE, TURN, BlockPos.ZERO));
		level.setBlock(at, entry.state(), 3); // standing as the builder placed it before B448: unturned
		StorehouseBlockEntity entity = (StorehouseBlockEntity) level.getBlockEntity(at);
		entity.setOrders(Map.of(Items.WHEAT, 12));
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.STOREHOUSE.id(), placement);
		helper.assertTrue(site != null, "the builder didn't take the Storehouse");
		AtomicBoolean restarted = new AtomicBoolean();
		AtomicInteger skipped = new AtomicInteger();
		Direction drawn = entry.state().getValue(HorizontalDirectionalBlock.FACING);
		helper.onEachTick(() -> {
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			if (live == null) {
				return;
			}
			skipped.set(live.skipped());
			if (!restarted.get() && live.stage() == BuildPlan.Stage.STRUCTURE) {
				restarted.set(true);
				restart(level);
			}
		});
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			helper.assertTrue(live == null, "still building: " + (live == null ? "" : live.stage() + " " + live.status()));
			helper.assertTrue(restarted.get(), "the build never got to its walls");
			BlockState there = level.getBlockState(at);
			helper.assertTrue(there.is(ModBlocks.STOREHOUSE) && there.getValue(HorizontalDirectionalBlock.FACING) == drawn,
				"the old-facing Storehouse block was replaced: " + there);
			helper.assertTrue(level.getBlockEntity(at) == entity && entity.orders().getOrDefault(Items.WHEAT, 0) == 12,
				"the Storehouse block lost its data: " + level.getBlockEntity(at));
			helper.assertTrue(skipped.get() == 0, skipped.get() + " step(s) skipped: the old-facing Storehouse block wasn't taken as built");
		});
	}

	/** The corner {@code corner} (relative) is the turned build's north-west corner. */
	private static BlueprintData.Placement placement(GameTestHelper helper, Blueprint blueprint, BlockPos corner) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(corner);
		BoundingBox box = BuildPlan.create(blueprint, new BlueprintData.Placement(level.dimension().location(), at, TURN, Mirror.NONE)).bounds();
		BlockPos origin = at.offset(at.getX() - box.minX(), 0, at.getZ() - box.minZ());
		return new BlueprintData.Placement(level.dimension().location(), origin, TURN, Mirror.NONE);
	}

	/** The build sites read back from their save, as a restart does. */
	private static void restart(ServerLevel level) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
	}
}
