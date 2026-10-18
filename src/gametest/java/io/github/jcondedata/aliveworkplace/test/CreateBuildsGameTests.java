package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.Contraptions;
import io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds;
import io.github.jcondedata.aliveworkplace.mechanic.KineticParts;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 39.4 without Create: the Watermill, the Windmill and the Workshop aren't at the Blueprint Table (their blocks
 * don't exist), they still load by id (a blueprint already handed out keeps working), they belong to {@code createBuilds},
 * and their text reads. With Create: {@code CreateBuildsCompatTests}.
 */
public class CreateBuildsGameTests implements FabricGameTest {
	/** Without Create the three builds are hidden at the Table (and so in the creative tab), and a build of no mod isn't. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void hiddenWithoutCreate(GameTestHelper helper) {
		helper.assertTrue(!Platform.get().isModLoaded("create"), "this suite runs without Create");
		List<ResourceLocation> listed = BlueprintLibrary.list(helper.getLevel().getServer(), false);
		Language en = Language.getInstance();
		for (StarterBlueprints.Entry entry : StarterBlueprints.CREATE_ONLY) {
			helper.assertTrue(!listed.contains(entry.id()), entry.id() + " is listed without Create");
			helper.assertTrue(!CreateBuilds.listed(entry.id()) && CreateBuilds.isCreateBuild(entry.id()), entry.id() + " isn't held back");
			helper.assertTrue(BlueprintLibrary.list(helper.getLevel().getServer(), true).contains(entry.id()), entry.id() + " doesn't load by id");
			helper.assertTrue("createBuilds".equals(StarterBlueprints.expansionSwitch(entry.id())), entry.id() + " isn't behind createBuilds");
			String path = entry.id().getPath();
			helper.assertTrue(en.has("blueprint.aliveworkplace." + path) && en.has("blueprint_desc.aliveworkplace." + path), path + "'s text is missing");
		}
		helper.assertTrue(CreateBuilds.listed(StarterBlueprints.WELL.id()) && listed.contains(StarterBlueprints.WELL.id()),
			"a build of no mod is hidden");
		helper.assertTrue(en.has("aliveworkplace.config.createBuilds") && en.has("aliveworkplace.config.createBuilds.tooltip"),
			"the createBuilds switch has no text");
		helper.succeed();
	}

	/** The switch off hides them too (here they're hidden anyway); it's read back from the config on reload. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theSwitchHoldsThemBack(GameTestHelper helper) {
		boolean was = CreateBuilds.ENABLED;
		try {
			CreateBuilds.ENABLED = false;
			for (StarterBlueprints.Entry entry : StarterBlueprints.CREATE_ONLY) {
				helper.assertTrue(!CreateBuilds.listed(entry.id()), entry.id() + " listed with createBuilds off");
			}
			helper.assertTrue(CreateBuilds.listed(StarterBlueprints.WELL.id()), "createBuilds off hides a build of no mod");
		} finally {
			CreateBuilds.ENABLED = was;
		}
		helper.succeed();
	}
	/**
	 * 39.5 without Create: no kinetic parts are known, a new site builds in today's order, a plan keeps its order, nothing
	 * is checked; a site saved before 39.5 (no {@code kinetic_order}) loads in today's order, one saved after keeps its
	 * order and its passes, and a build-site save without turning checks loads with none.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void noContraptionsWithoutCreate(GameTestHelper helper) {
		helper.assertTrue(!KineticParts.any() && !Contraptions.active(), "kinetic parts known without Create");
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), new BlockPos(1, 2, 3),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "Jesse", StarterBlueprints.WORKSHOP.id(), placement);
		helper.assertTrue(!site.kineticOrder(), "a new site builds in a contraption's order without Create");
		BuildPlan plan = site.plan(helper.getLevel());
		helper.assertTrue(plan != null && plan.kineticOrdered() == plan && plan.kineticStart(0) == -1, "a plan is sorted without Create");
		CompoundTag saved = site.save();
		helper.assertTrue(!saved.contains("kinetic_order") && !saved.contains("kinetic_passes"), "a plain site saves 39.5's fields: " + saved);
		saved.putBoolean("kinetic_order", true);
		saved.putInt("kinetic_passes", 1);
		BuildSite back = BuildSite.load(saved);
		helper.assertTrue(back != null && back.kineticOrder() && back.kineticPasses() == 1, "39.5's fields don't load back");
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		BuildSiteManager empty = BuildSiteManager.load(new CompoundTag(), registries);
		helper.assertTrue(empty.turnChecks().isEmpty() && empty.notTurning(placement).isEmpty(), "an old save loads with turning checks");
		empty.scheduleTurnCheck(new BuildSiteManager.TurnCheck(StarterBlueprints.WORKSHOP.id(), placement, UUID.randomUUID(), "Bob",
			List.of(new BlockPos(1, 2, 3)), 40, 1));
		empty.recordFinished(StarterBlueprints.WELL.id(), placement, UUID.randomUUID());
		empty.setNotTurning(placement, List.of(new BlockPos(4, 5, 6)));
		BuildSiteManager reloaded = BuildSiteManager.load(empty.save(new CompoundTag(), registries), registries);
		helper.assertTrue(reloaded.turnChecks().size() == 1 && reloaded.turnChecks().get(0).tries() == 1
			&& reloaded.turnChecks().get(0).builder().equals("Bob"), "the turning check didn't survive a save: " + reloaded.turnChecks());
		helper.assertTrue(reloaded.notTurning(placement).equals(java.util.Optional.of(List.of(new BlockPos(4, 5, 6)))),
			"what didn't turn wasn't saved: " + reloaded.notTurning(placement));
		Language en = Language.getInstance();
		for (String key : List.of("contraption_turns", "contraption_turns.one", "contraption_stuck", "contraption_stuck.one", "contraption_part",
			"contraption_gone", "contraption_more")) {
			helper.assertTrue(en.has("message.aliveworkplace." + key), key + " has no text");
		}
		helper.succeed();
	}

	/**
	 * 39.6 without Create: a file in Create's uploaded schematics folder stays out of the library when a table opens, and
	 * the Schematic isn't taken for anything (there's no such item).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void noSchematicsWithoutCreate(GameTestHelper helper) throws java.io.IOException {
		net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
		java.nio.file.Path dir = io.github.jcondedata.aliveworkplace.table.CreateSchematics.uploadedFolder(server).resolve("gametest_no_create");
		ResourceLocation id = io.github.jcondedata.aliveworkplace.table.CreateSchematics.idFor("gametest_no_create", "hut.nbt");
		java.nio.file.Files.createDirectories(dir);
		java.nio.file.Path file = dir.resolve("hut.nbt");
		CompoundTag tag = io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles.toStructureNbt(new io.github.jcondedata.aliveworkplace.blueprint.Blueprint(
			id, new net.minecraft.core.Vec3i(1, 1, 1), List.of(new io.github.jcondedata.aliveworkplace.blueprint.Blueprint.Entry(BlockPos.ZERO,
				net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), null)), List.of()));
		net.minecraft.nbt.NbtIo.writeCompressed(tag, file);
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			helper.assertTrue(!io.github.jcondedata.aliveworkplace.table.CreateSchematics.active(), "Create's schematics are taken without Create");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.table.CreateSchematics.sync(server).isEmpty(), "a schematic was imported without Create");
			io.github.jcondedata.aliveworkplace.table.TableServer.open(player, helper.absolutePos(new BlockPos(1, 1, 1)));
			helper.assertTrue(BlueprintLibrary.get(server, id).isEmpty(), "the table imported a schematic without Create");
			helper.assertTrue(!io.github.jcondedata.aliveworkplace.table.CreateSchematics.isSchematic(new net.minecraft.world.item.ItemStack(
				net.minecraft.world.item.Items.PAPER)), "paper taken for a Schematic");
		} finally {
			server.getPlayerList().remove(player);
			java.nio.file.Files.deleteIfExists(file);
			java.nio.file.Files.deleteIfExists(dir);
		}
		helper.succeed();
	}
}
