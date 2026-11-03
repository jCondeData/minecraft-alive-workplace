package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA lane E, 2026-11-03 (a mutant of MechanicWork.beltData, `sourceOrigin != null` to `== null`, survived the whole
 * compat suite): ROADMAP 39.2/39.5, "a belt's controller moved with the build". A three-segment belt saved where its
 * controller (the far end) stood at 12 64 10, built here and then all three segments gone: each segment the Mechanic
 * puts back names as its {@code Controller} the place where the controller segment now stands in this world, not the
 * place it stood where the blueprint was saved, plain and turned a quarter. (A flipped build is left out: the
 * builder leaves a mirrored belt's place data out for Create to work out, see the Notes, 2026-11-03.)
 */
public class QaMechanicBeltController1103eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos TOOLBOX = new BlockPos(2, 2, 2);
	private static final BlockPos LINE = new BlockPos(6, 2, 6);
	private static final AtomicInteger BLUEPRINTS = new AtomicInteger();

	private static ListTag ints(int... values) {
		ListTag list = new ListTag();
		for (int v : values) {
			list.add(net.minecraft.nbt.IntTag.valueOf(v));
		}
		return list;
	}

	private static void controllerGoesWithTheBuild(GameTestHelper helper, Rotation rotation, Mirror mirror) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedIn(level)) {
			if (f.structure().getPath().startsWith("compat_test/qa_e1103_belt_")) {
				sites.forgetFinished(f.placement());
			}
		}
		for (int x = 2; x <= 10; x++) {
			for (int z = 2; z <= 10; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR); // every segment gone, whichever way the build stands
			}
		}
		CompoundTag structure = new CompoundTag();
		structure.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		structure.put("size", ints(3, 1, 1));
		ListTag palette = new ListTag();
		palette.add(net.minecraft.nbt.NbtUtils.writeBlockState(MechanicCompatTests.create("belt").defaultBlockState()));
		structure.put("palette", palette);
		ListTag blocks = new ListTag();
		for (int x = 0; x < 3; x++) {
			CompoundTag data = new CompoundTag();
			data.putString("id", "create:belt");
			data.putBoolean("IsController", x == 2);
			data.putInt("Index", 2 - x);
			data.putInt("Length", 3);
			data.putIntArray("Controller", new int[] {12, 64, 10});
			CompoundTag block = new CompoundTag();
			block.put("pos", ints(x, 0, 0));
			block.putInt("state", 0);
			block.put("nbt", data);
			blocks.add(block);
		}
		structure.put("blocks", blocks);
		structure.put("entities", new ListTag());
		ResourceLocation id = AliveWorkplace.id("compat_test/qa_e1103_belt_" + BLUEPRINTS.incrementAndGet());
		level.getStructureManager().getOrCreate(id).load(BuiltInRegistries.BLOCK.asLookup(), structure);
		helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "setup: could not register the test blueprint");
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(LINE), rotation, mirror);
		sites.recordFinished(id, placement, UUID.randomUUID());
		try {
			List<MechanicWork.Part> belts = MechanicWork.missingParts(level, helper.absolutePos(TOOLBOX)).stream().filter(p -> p.structure().equals(id)).toList();
			helper.assertTrue(belts.size() == 3, rotation + " " + mirror + ": expected the 3 belt segments missing, found "
				+ belts.stream().map(p -> helper.relativePos(p.pos())).toList());
			List<MechanicWork.Part> controllers = belts.stream().filter(p -> p.nbt() != null && p.nbt().getBoolean("IsController")).toList();
			helper.assertTrue(controllers.size() == 1, rotation + " " + mirror + ": " + controllers.size() + " controller segments among "
				+ belts.stream().map(p -> helper.relativePos(p.pos()) + " " + p.nbt()).toList());
			BlockPos controller = controllers.get(0).pos();
			helper.assertFalse(controller.equals(new BlockPos(12, 64, 10)), "setup: the controller stands where it was saved");
			for (MechanicWork.Part part : belts) {
				int[] at = part.nbt() != null && part.nbt().get("Controller") instanceof IntArrayTag array ? array.getAsIntArray() : null;
				helper.assertTrue(at != null && at.length == 3 && new BlockPos(at[0], at[1], at[2]).equals(controller),
					rotation + " " + mirror + ": the segment going back at " + helper.relativePos(part.pos()) + " names its controller at "
					+ (at == null ? "nothing" : java.util.Arrays.toString(at)) + ", not where the controller segment stands, "
					+ controller.toShortString() + " (" + part.nbt() + ")");
			}
		} finally {
			sites.forgetFinished(placement);
		}
		helper.succeed();
	}

	//$ gametest_batch AREA '"qa_e1103_belt_plain"'
	@GameTest(template = AREA, batch = "qa_e1103_belt_plain")
	public void aGoneBeltsSegmentsNameTheControllerWhereTheBuildStands(GameTestHelper helper) {
		controllerGoesWithTheBuild(helper, Rotation.NONE, Mirror.NONE);
	}

	//$ gametest_batch AREA '"qa_e1103_belt_turned"'
	@GameTest(template = AREA, batch = "qa_e1103_belt_turned")
	public void aTurnedBuildsGoneBeltNamesItsTurnedController(GameTestHelper helper) {
		controllerGoesWithTheBuild(helper, Rotation.CLOCKWISE_90, Mirror.NONE);
	}
}
