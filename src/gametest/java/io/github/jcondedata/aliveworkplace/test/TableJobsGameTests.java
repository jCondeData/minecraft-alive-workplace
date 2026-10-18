package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.BuildJobs;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.tailor.Tailors;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * ROADMAP 36.6a (the owner's change to 36.6): the Blueprint Table shows no description, only what jobs a build brings,
 * "Jobs: Fisherman (1), Butcher/Chef/Rancher (3)" (a slash for a shared job block, the number how many of that block the
 * build holds), and "Beds: N" when it has beds.
 */
public class TableJobsGameTests implements FabricGameTest {
	private static final ResourceLocation TEST_ID = AliveWorkplace.id("uploads/test/jobs_hut");

	/** A build of {@code states} in a row along x, each on its own block. */
	private static Blueprint build(List<BlockState> states) {
		List<Blueprint.Entry> blocks = new ArrayList<>();
		for (int i = 0; i < states.size(); i++) {
			blocks.add(new Blueprint.Entry(new BlockPos(i, 0, 0), states.get(i), null));
		}
		return new Blueprint(TEST_ID, new Vec3i(Math.max(1, states.size()), 1, 1), blocks);
	}

	/** A bed's two halves. */
	private static List<BlockState> bed() {
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
		return List.of(bed.setValue(BedBlock.PART, BedPart.FOOT), bed.setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static List<String> shown(BuildJobs.Summary summary) {
		return BuildJobs.lines(summary.jobs(), summary.beds()).stream().map(Component::getString).toList();
	}

	private static ResourceLocation id(VillagerProfession profession) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
	}

	/** A barrel, three smokers and two beds: the owner's example, one entry per kind of job block, shared jobs by a slash. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void sharedJobBlocksAreCountedAndJoined(GameTestHelper helper) {
		List<BlockState> states = new ArrayList<>();
		states.add(Blocks.STONE_BRICKS.defaultBlockState());
		states.add(Blocks.BARREL.defaultBlockState());
		states.add(Blocks.SMOKER.defaultBlockState());
		states.addAll(bed());
		states.add(Blocks.SMOKER.defaultBlockState());
		states.add(Blocks.OAK_PLANKS.defaultBlockState());
		states.add(Blocks.SMOKER.defaultBlockState());
		states.addAll(bed());
		BuildJobs.Summary summary = BuildJobs.of(build(states));
		helper.assertTrue(summary.jobs().equals(List.of(
			new TablePayloads.JobBlock(List.of(id(VillagerProfession.FISHERMAN)), 1),
			new TablePayloads.JobBlock(List.of(id(VillagerProfession.BUTCHER), id(ModVillagers.CHEF), id(ModVillagers.RANCHER)), 3))),
			"the job blocks are " + summary.jobs());
		helper.assertTrue(summary.beds() == 2, "a bed counts once, by its head: " + summary.beds());
		List<String> lines = shown(summary);
		helper.assertTrue(lines.equals(List.of("Jobs: Fisherman (1), Butcher/Chef/Rancher (3)", "Beds: 2")), "the Table shows " + lines);
		helper.succeed();
	}

	/** Beds and no job block: only "Beds: N"; a build with neither shows nothing (no "Jobs:" with nothing after it). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bedsOnlyAndNeither(GameTestHelper helper) {
		List<BlockState> home = new ArrayList<>(bed());
		home.add(Blocks.CHEST.defaultBlockState());
		home.add(Blocks.FURNACE.defaultBlockState());
		List<String> lines = shown(BuildJobs.of(build(home)));
		helper.assertTrue(lines.equals(List.of("Beds: 1")), "a home shows " + lines);
		BuildJobs.Summary none = BuildJobs.of(build(List.of(Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
			Blocks.BELL.defaultBlockState(), Blocks.OAK_STAIRS.defaultBlockState())));
		helper.assertTrue(none.jobs().isEmpty() && none.beds() == 0 && shown(none).isEmpty(), "a wall shows " + shown(none));
		helper.assertTrue(shown(BuildJobs.Summary.NONE).isEmpty(), "an empty build shows a line");
		helper.succeed();
	}

	/** A job behind a switch that is off isn't offered: the loom names only the Shepherd while config tailors is off. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSwitchedOffJobIsLeftOut(GameTestHelper helper) {
		Blueprint looms = build(List.of(Blocks.LOOM.defaultBlockState(), Blocks.LOOM.defaultBlockState()));
		boolean was = Tailors.ENABLED;
		try {
			Tailors.ENABLED = true;
			List<String> on = shown(BuildJobs.of(looms));
			helper.assertTrue(on.equals(List.of("Jobs: Shepherd/Tailor (2)")), "with tailors on the looms show " + on);
			Tailors.ENABLED = false;
			List<String> off = shown(BuildJobs.of(looms));
			helper.assertTrue(off.equals(List.of("Jobs: Shepherd (2)")), "with tailors off the looms show " + off);
		} finally {
			Tailors.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * What the Table sends when a player picks a build (its details, over the wire): the Fishers' Hut's barrel brings a
	 * Fisherman, the Starter Cottage has beds, and the lines read back the same after the packet's round trip.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTableSendsAShippedBuildsJobsAndBeds(GameTestHelper helper) {
		TablePayloads.Details hut = TableServer.details(helper.getLevel(), null, StarterBlueprints.FISHERS_HUT.id()).orElseThrow();
		helper.assertTrue(hut.jobs().stream().anyMatch(b -> b.jobs().contains(id(VillagerProfession.FISHERMAN)) && b.count() >= 1),
			"the Fishers' Hut brings no Fisherman: " + hut.jobs());
		TablePayloads.Details cottage = TableServer.details(helper.getLevel(), null, StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
		helper.assertTrue(cottage.beds() > 0, "the Starter Cottage has no beds");
		helper.assertTrue(BuildJobs.lines(cottage.jobs(), cottage.beds()).stream().map(Component::getString).anyMatch(l -> l.equals("Beds: " + cottage.beds())),
			"the cottage's lines are " + BuildJobs.lines(cottage.jobs(), cottage.beds()));
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		TablePayloads.Details.CODEC.encode(buf, hut);
		TablePayloads.Details back = TablePayloads.Details.CODEC.decode(buf);
		helper.assertTrue(back.equals(hut) && buf.readableBytes() == 0, "the details changed on the wire");
		helper.succeed();
	}

	/** Every sentence the lines use is in English, and the old description lines are gone. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theLinesAreInEnglish(GameTestHelper helper) {
		Language en = Language.getInstance();
		for (String key : List.of("screen.aliveworkplace.table.jobs", "screen.aliveworkplace.table.job_block",
			"screen.aliveworkplace.table.jobs_separator", "screen.aliveworkplace.table.beds")) {
			helper.assertTrue(en.has(key), key + " has no English");
		}
		helper.assertTrue(!en.has("blueprint_desc.aliveworkplace.starter_cottage"), "the description lines are still there");
		helper.succeed();
	}
}
