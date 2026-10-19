package io.github.jcondedata.aliveworkplace.test;

import com.mojang.serialization.JsonOps;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mechanic.Kinetics;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicRound;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 39.2 without Create (this suite's runtime has no Create; {@code MechanicCompatTests} has it): there's no
 * Mechanic profession and no toolbox job site, a villager saved as a Mechanic in a world Create was removed from loads
 * jobless, nothing of the work runs, and a saved round with fields missing loads as a fresh one.
 */
public class MechanicGameTests implements FabricGameTest {
	/** Without Create the job isn't registered at all: no profession, no POI, no Mechanic to lead a crew. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withoutCreateThereIsNoMechanic(GameTestHelper helper) {
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create"), "Create is loaded in the plain suite");
		helper.assertTrue(ModVillagers.MECHANIC == null, "a Mechanic profession without Create");
		helper.assertTrue(!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(AliveWorkplace.id("mechanic")), "aliveworkplace:mechanic is registered without Create");
		helper.assertTrue(!helper.getLevel().registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).containsKey(ModVillagers.TOOLBOX_ID),
			"the toolbox POI is registered without Create");
		helper.assertTrue(!ModVillagers.isMechanic(VillagerProfession.NONE) && !ModVillagers.isWorker(VillagerProfession.NONE), "NONE counts as a Mechanic");
		helper.assertTrue(MechanicWork.crewLead(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)).isEmpty(), "a crew lead without Create");
		for (var state : List.of(Blocks.CHEST.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.BARREL.defaultBlockState())) {
			helper.assertTrue(!Kinetics.isDrivePart(state) && !Kinetics.isMachine(state) && !Kinetics.keepsOwnItems(state) && !Kinetics.isToolbox(state),
				state + " taken for a Create block");
		}
		helper.succeed();
	}

	/**
	 * A belt segment the Mechanic puts back gets its place on the belt and its looks as saved, never the items on it, and
	 * its {@code Controller} moved from where the blueprint was saved to where the build stands (offset and turned with
	 * it), in whichever form it was saved (a list of three, or X, Y and Z).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aPutBackBeltSegmentNamesItsControllerWhereTheBuildStands(GameTestHelper helper) throws ReflectiveOperationException {
		java.lang.reflect.Method beltData = MechanicWork.class.getDeclaredMethod("beltData", CompoundTag.class, BlockPos.class,
			io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement.class);
		beltData.setAccessible(true);
		var placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(helper.getLevel().dimension().location(),
			new BlockPos(100, 70, 200), net.minecraft.world.level.block.Rotation.CLOCKWISE_90, net.minecraft.world.level.block.Mirror.NONE);
		BlockPos sourceOrigin = new BlockPos(10, 64, 8);
		BlockPos expected = placement.origin().offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
			new BlockPos(0, 0, 2), net.minecraft.world.level.block.Mirror.NONE, net.minecraft.world.level.block.Rotation.CLOCKWISE_90, BlockPos.ZERO));
		CompoundTag saved = new CompoundTag();
		saved.putInt("Index", 2);
		saved.putInt("Length", 3);
		saved.putBoolean("IsController", false);
		saved.putString("Casing", "ANDESITE");
		saved.put("Items", new CompoundTag());
		saved.putFloat("Speed", 16);
		saved.putIntArray("Controller", new int[] {10, 64, 10});
		CompoundTag out = (CompoundTag) beltData.invoke(null, saved, sourceOrigin, placement);
		helper.assertTrue(out != null && Nbt.getInt(out, "Index") == 2 && Nbt.getInt(out, "Length") == 3 && out.contains("IsController")
			&& "ANDESITE".equals(Nbt.getString(out, "Casing")), "the segment's place or looks weren't kept: " + out);
		helper.assertTrue(!out.contains("Items") && !out.contains("Speed"), "the segment keeps its items or speed: " + out);
		int[] named = Nbt.getIntArray(out, "Controller");
		helper.assertTrue(named.length == 3 && new BlockPos(named[0], named[1], named[2]).equals(expected),
			"the segment names " + java.util.Arrays.toString(named) + " as its controller, not " + expected.toShortString() + ": " + out);
		CompoundTag xyz = new CompoundTag();
		xyz.putInt("X", 10);
		xyz.putInt("Y", 64);
		xyz.putInt("Z", 10);
		saved.put("Controller", xyz);
		out = (CompoundTag) beltData.invoke(null, saved, sourceOrigin, placement);
		CompoundTag moved = Nbt.getCompound(out, "Controller");
		helper.assertTrue(new BlockPos(Nbt.getInt(moved, "X"), Nbt.getInt(moved, "Y"), Nbt.getInt(moved, "Z")).equals(expected) && moved.contains("X"),
			"a controller saved as X, Y and Z isn't moved to " + expected.toShortString() + ": " + out);
		helper.succeed();
	}

	/** A villager saved as a Mechanic (Create since removed) loads as a jobless villager, not a broken one. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSavedMechanicLoadsJoblessWithoutCreate(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		CompoundTag saved = new CompoundTag();
		villager.saveWithoutId(saved);
		CompoundTag data = saved.getCompound("VillagerData");
		data.put("profession", StringTag.valueOf("aliveworkplace:mechanic"));
		saved.put("VillagerData", data);
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(saved);
		helper.assertTrue(copy.getVillagerData().getProfession() == VillagerProfession.NONE,
			"a saved Mechanic loads as " + copy.getVillagerData().getProfession());
		copy.discard();
		villager.discard();
		helper.succeed();
	}

	/** The saved round: missing fields load as a fresh round, the next index wraps, and it goes round. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSavedRoundHasDefaults(GameTestHelper helper) {
		MechanicRound empty = MechanicRound.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow();
		helper.assertTrue(empty.equals(MechanicRound.EMPTY) && empty.current() == null, "an empty record loads as " + empty);
		MechanicRound round = new MechanicRound(List.of(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6)), 5);
		helper.assertTrue(round.next() == 1, "the next index doesn't wrap: " + round.next());
		helper.assertTrue(round.advance().atStart() && round.advance().current().equals(new BlockPos(1, 2, 3)), "the round doesn't go round");
		var json = MechanicRound.CODEC.encodeStart(JsonOps.INSTANCE, round).getOrThrow();
		helper.assertTrue(MechanicRound.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(round), "the round doesn't survive a save");
		helper.succeed();
	}

	/** Config {@code mechanics}: on in GameTests (M39's gate is open), and off switches the work off. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"mechanics_switch"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mechanics_switch")
	public void theMechanicsSwitch(GameTestHelper helper) {
		boolean before = MechanicWork.ENABLED;
		try {
			WorkplaceConfig config = new WorkplaceConfig();
			helper.assertTrue(config.mechanics, "mechanics is off by default in a GameTest");
			config.mechanics = false;
			config.apply();
			helper.assertTrue(!MechanicWork.ENABLED, "mechanics off didn't switch the work off");
			config.mechanics = true;
			config.apply();
			helper.assertTrue(MechanicWork.ENABLED, "mechanics on didn't switch the work on");
		} finally {
			new WorkplaceConfig().apply();
			MechanicWork.ENABLED = before;
		}
		helper.succeed();
	}
}
