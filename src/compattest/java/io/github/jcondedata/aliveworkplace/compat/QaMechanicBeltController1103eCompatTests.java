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
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane E, 2026-11-03, reworked for B669 (2026-11-18): ROADMAP 39.2/39.5, "a belt goes back with the build". A
 * three-segment belt in a blueprint, built here plain and turned a quarter, then all three segments gone: the Mechanic
 * sees the two pulley shafts to put back first, each where the belt's end stands in this world and turning on the
 * belt's axis there; once they stand, the whole belt is one part between them, for one belt connector, never three
 * segments (B669: a lone segment is broken by Create into a belt connector from nothing). The old checks of each
 * segment's moved {@code Controller} went with the segment-by-segment mending: Create works a laid belt's data out.
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

	/** A three-segment belt along x, facing east, saved as a blueprint (its start at x = 0). */
	static ResourceLocation beltBlueprint(ServerLevel level, String prefix, AtomicInteger counter) {
		CompoundTag structure = new CompoundTag();
		structure.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		structure.put("size", ints(3, 1, 1));
		ListTag palette = new ListTag();
		BlockState belt = MechanicCompatTests.with(MechanicCompatTests.create("belt").defaultBlockState(), "facing", "east");
		for (String part : List.of("start", "middle", "end")) {
			palette.add(net.minecraft.nbt.NbtUtils.writeBlockState(MechanicCompatTests.with(belt, "part", part)));
		}
		structure.put("palette", palette);
		ListTag blocks = new ListTag();
		for (int x = 0; x < 3; x++) {
			CompoundTag data = new CompoundTag();
			data.putString("id", "create:belt");
			data.putBoolean("IsController", x == 0);
			data.putInt("Index", x);
			data.putInt("Length", 3);
			data.putIntArray("Controller", new int[] {10, 64, 10});
			CompoundTag block = new CompoundTag();
			block.put("pos", ints(x, 0, 0));
			block.putInt("state", x);
			block.put("nbt", data);
			blocks.add(block);
		}
		structure.put("blocks", blocks);
		structure.put("entities", new ListTag());
		ResourceLocation id = AliveWorkplace.id(prefix + counter.incrementAndGet());
		level.getStructureManager().getOrCreate(id).load(BuiltInRegistries.BLOCK.asLookup(), structure);
		return id;
	}

	private static void beltGoesBackWithTheBuild(GameTestHelper helper, Rotation rotation, Mirror mirror) {
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
		ResourceLocation id = beltBlueprint(level, "compat_test/qa_e1103_belt_", BLUEPRINTS);
		helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "setup: could not register the test blueprint");
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(LINE), rotation, mirror);
		sites.recordFinished(id, placement, UUID.randomUUID());
		BlockPos start = placement.origin().offset(StructureTemplate.transform(BlockPos.ZERO, mirror, rotation, BlockPos.ZERO));
		BlockPos end = placement.origin().offset(StructureTemplate.transform(new BlockPos(2, 0, 0), mirror, rotation, BlockPos.ZERO));
		String axis = rotation == Rotation.NONE || rotation == Rotation.CLOCKWISE_180 ? "z" : "x";
		try {
			List<MechanicWork.Part> parts = MechanicWork.missingParts(level, helper.absolutePos(TOOLBOX)).stream().filter(p -> p.structure().equals(id)).toList();
			helper.assertTrue(parts.size() == 2 && parts.stream().noneMatch(MechanicWork.Part::belt)
				&& parts.stream().allMatch(p -> p.cost() == MechanicCompatTests.createItem("shaft"))
				&& parts.stream().map(MechanicWork.Part::pos).toList().containsAll(List.of(start, end)),
				rotation + " " + mirror + ": expected the belt's two pulley shafts first, at " + helper.relativePos(start) + " and "
				+ helper.relativePos(end) + ", found " + parts.stream().map(p -> helper.relativePos(p.pos()) + " " + p.state() + " for " + p.cost()).toList());
			for (MechanicWork.Part p : parts) {
				helper.assertTrue(p.state().toString().contains("axis=" + axis), rotation + ": the pulley at " + helper.relativePos(p.pos())
					+ " doesn't turn on the belt's axis " + axis + ": " + p.state());
				level.setBlockAndUpdate(p.pos(), p.state());
			}
			parts = MechanicWork.missingParts(level, helper.absolutePos(TOOLBOX)).stream().filter(p -> p.structure().equals(id)).toList();
			helper.assertTrue(parts.size() == 1 && parts.get(0).belt() && parts.get(0).cost() == MechanicCompatTests.createItem("belt_connector")
				&& parts.get(0).pos().equals(start) && end.equals(parts.get(0).end()),
				rotation + " " + mirror + ": with both pulleys back, expected one belt from " + helper.relativePos(start) + " to "
				+ helper.relativePos(end) + " for one belt connector, found "
				+ parts.stream().map(p -> helper.relativePos(p.pos()) + (p.belt() ? " to " + helper.relativePos(p.end()) : "") + " for " + p.cost()).toList());
		} finally {
			sites.forgetFinished(placement);
		}
		helper.succeed();
	}

	//$ gametest_batch AREA '"qa_e1103_belt_plain"'
	@GameTest(template = AREA, batch = "qa_e1103_belt_plain")
	public void aGoneBeltGoesBackBetweenItsPulleysWhereTheBuildStands(GameTestHelper helper) {
		beltGoesBackWithTheBuild(helper, Rotation.NONE, Mirror.NONE);
	}

	//$ gametest_batch AREA '"qa_e1103_belt_turned"'
	@GameTest(template = AREA, batch = "qa_e1103_belt_turned")
	public void aTurnedBuildsGoneBeltGoesBackBetweenItsTurnedPulleys(GameTestHelper helper) {
		beltGoesBackWithTheBuild(helper, Rotation.CLOCKWISE_90, Mirror.NONE);
	}
}
