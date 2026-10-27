package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.BuildJobs;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA for ROADMAP 36.6a (the owner's change to 36.6) and 36.6b: the Blueprint Table shows only what jobs a build brings
 * and its beds, "Jobs: Fisherman (1), Butcher/Chef (3)" (a slash for a shared job block, the number how many of that job
 * block the build holds), plus "Beds: N" when the build has beds. Covers what {@link TableJobsGameTests} leaves out:
 * every build the Table lists (styles and levels included) counted against its own template, a scanned build, a job
 * block whose job needs a mod that isn't there, a translated name, and the 36.6b example.
 */
public class QaTableJobsGameTests implements FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String AREA = "aliveworkplace_test:build_area";

	private static ResourceLocation id(VillagerProfession profession) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
	}

	private static List<String> shown(List<TablePayloads.JobBlock> jobs, int beds) {
		return BuildJobs.lines(jobs, beds).stream().map(Component::getString).toList();
	}

	/** What a build holds, counted here: job-site blocks by their point of interest, and beds (by their head). */
	private record Count(Map<Holder<PoiType>, Integer> pois, int beds) {
	}

	/** Every block that has a point of interest in some state, or is a bed: the only blocks a Jobs/Beds line can come from. */
	private static List<Block> relevantBlocks() {
		List<Block> out = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (block instanceof BedBlock || block.getStateDefinition().getPossibleStates().stream().anyMatch(s -> PoiTypes.forState(s).isPresent())) {
				out.add(block);
			}
		}
		return out;
	}

	/** Counts a shipped template with vanilla's own palette lookup ({@link StructureTemplate#filterBlocks}), not the mod's reader. */
	private static Count count(StructureTemplate template, List<Block> relevant) {
		Map<Holder<PoiType>, Integer> pois = new LinkedHashMap<>();
		int beds = 0;
		for (Block block : relevant) {
			for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block)) {
				beds += add(info.state(), pois);
			}
		}
		return new Count(pois, beds);
	}

	/** Counts a build that has no template of its own (a style, a Wonder) from its blocks. */
	private static Count count(Blueprint blueprint) {
		Map<Holder<PoiType>, Integer> pois = new LinkedHashMap<>();
		int beds = 0;
		for (Blueprint.Entry e : blueprint.blocks()) {
			beds += add(e.state(), pois);
		}
		return new Count(pois, beds);
	}

	private static int add(BlockState state, Map<Holder<PoiType>, Integer> pois) {
		if (state.getBlock() instanceof BedBlock) {
			return state.getValue(BedBlock.PART) == BedPart.HEAD ? 1 : 0;
		}
		PoiTypes.forState(state).ifPresent(h -> pois.merge(h, 1, Integer::sum));
		return 0;
	}

	/** The jobs a villager can take at {@code poi} in this game (by each job's own job site, switched-off jobs left out). */
	private static List<ResourceLocation> jobsAt(Holder<PoiType> poi) {
		List<ResourceLocation> out = new ArrayList<>();
		for (VillagerProfession p : BuiltInRegistries.VILLAGER_PROFESSION) {
			if (p != VillagerProfession.NONE && p != VillagerProfession.NITWIT && p.heldJobSite().test(poi) && Stations.available(p)) {
				out.add(id(p));
			}
		}
		return out;
	}

	/**
	 * What's wrong with what the Table sends for {@code id} against {@code count} (null: nothing): every job-site block
	 * is counted in the entry that names its jobs, every entry is made of the build's job blocks, beds match, and the
	 * lines read exactly "Jobs: A (n), B/C (m)" and "Beds: N" with every job's name translated.
	 */
	private static String check(ResourceLocation id, TablePayloads.Details details, Count count) {
		List<TablePayloads.JobBlock> jobs = details.jobs();
		int[] expected = new int[jobs.size()];
		for (Map.Entry<Holder<PoiType>, Integer> e : count.pois().entrySet()) {
			List<ResourceLocation> at = jobsAt(e.getKey());
			if (at.isEmpty()) {
				continue;
			}
			int found = -1;
			for (int i = 0; i < jobs.size() && found < 0; i++) {
				if (jobs.get(i).jobs().stream().anyMatch(at::contains)) {
					found = i;
				}
			}
			if (found < 0) {
				return e.getValue() + " job block(s) for " + at + " (" + e.getKey().unwrapKey().map(Object::toString).orElse("?") + ") aren't in " + jobs;
			}
			expected[found] += e.getValue();
		}
		for (int i = 0; i < jobs.size(); i++) {
			TablePayloads.JobBlock block = jobs.get(i);
			if (block.jobs().isEmpty() || block.count() <= 0 || block.count() != expected[i]) {
				return "entry " + block + " counted " + block.count() + " but the build holds " + expected[i] + " of its job block";
			}
		}
		if (details.beds() != count.beds()) {
			return "beds " + details.beds() + " but the build holds " + count.beds();
		}
		Language en = Language.getInstance();
		List<String> want = new ArrayList<>();
		if (!jobs.isEmpty()) {
			List<String> parts = new ArrayList<>();
			for (TablePayloads.JobBlock block : jobs) {
				List<String> names = new ArrayList<>();
				for (ResourceLocation job : block.jobs()) {
					VillagerProfession p = BuiltInRegistries.VILLAGER_PROFESSION.get(job);
					String key = "entity.minecraft.villager." + p.name();
					if (!en.has(key)) {
						return "the job " + job + " has no name in English (" + key + ")";
					}
					names.add(en.getOrDefault(key));
				}
				parts.add(String.join("/", names) + " (" + block.count() + ")");
			}
			want.add("Jobs: " + String.join(", ", parts));
		}
		if (count.beds() > 0) {
			want.add("Beds: " + count.beds());
		}
		List<String> lines = shown(jobs, details.beds());
		if (!lines.equals(want)) {
			return "the Table shows " + lines + ", the spec's format gives " + want;
		}
		return null;
	}

	/**
	 * Every build the Table lists (unlocks off, so all of them), every level of it, and every style it offers: the
	 * Table's details for it give a Jobs/Beds line that matches the job blocks and beds counted in its own template
	 * (a style's or a Wonder's from its blocks), with no exception. A few builds a tick.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '12000' '"qaTableJobsWalk"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 12000, batch = "qaTableJobsWalk")
	public void everyListedBuildShowsItsOwnJobsAndBeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		boolean was = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = false;
		Leftovers.after(helper, () -> BlueprintUnlocks.ENABLED = was);
		List<Block> relevant = relevantBlocks();
		Deque<ResourceLocation> queue = new ArrayDeque<>();
		TableServer.listing(level, null).forEach(e -> queue.add(e.id()));
		int listed = queue.size();
		List<String> wrong = new ArrayList<>();
		int[] done = {0, 0, 0, 0}; // builds, styled, with jobs, with beds
		Map<ResourceLocation, TablePayloads.Entry> entries = new HashMap<>();
		helper.onEachTick(() -> {
			BlueprintUnlocks.ENABLED = false;
			// The listing of this tick, so it and the details below are of the same switches (B570: a test that flips one
			// between ticks, such as physicians, changes both).
			entries.clear();
			TableServer.listing(level, null).forEach(e -> entries.put(e.id(), e));
			for (int n = 0; n < 3 && !queue.isEmpty(); n++) {
				ResourceLocation id = queue.poll();
				boolean styled = BlueprintStyles.parse(id).isPresent();
				try {
					Optional<TablePayloads.Details> details = TableServer.details(level, null, id);
					if (details.isEmpty()) {
						wrong.add(id + ": the Table sends no details");
						continue;
					}
					Blueprint blueprint = BlueprintLibrary.get(server, id).orElseThrow();
					Optional<StructureTemplate> template = styled || io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints.wonderOf(id).isPresent()
						|| io.github.jcondedata.aliveworkplace.world.VillagePieces.outsideOf(id).isPresent()
						? Optional.empty() : server.getStructureManager().get(id);
					Count count = template.map(t -> count(t, relevant)).orElseGet(() -> count(blueprint));
					String problem = check(id, details.get(), count);
					if (problem != null) {
						wrong.add(id + ": " + problem);
					}
					TablePayloads.Details again = TableServer.details(level, null, id).orElseThrow();
					if (!again.jobs().equals(details.get().jobs()) || again.beds() != details.get().beds()) {
						wrong.add(id + ": asked twice, the Table sent " + details.get().jobs() + " then " + again.jobs());
					}
					if (!styled) {
						List<ResourceLocation> flat = details.get().jobs().stream().flatMap(b -> b.jobs().stream()).distinct().toList();
						TablePayloads.Entry entry = entries.get(id);
						if (entry != null && !entry.jobs().equals(flat)) {
							wrong.add(id + ": the listing's jobs " + entry.jobs() + " aren't the details' " + flat);
						}
						for (TablePayloads.StyleChoice style : details.get().styles()) {
							queue.add(BlueprintStyles.styled(id, style.name()));
						}
					}
					done[styled ? 1 : 0]++;
					done[2] += details.get().jobs().isEmpty() ? 0 : 1;
					done[3] += details.get().beds() > 0 ? 1 : 0;
				} catch (RuntimeException e) {
					wrong.add(id + ": " + e);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(queue.isEmpty(), "still walking: " + queue.size() + " left");
			LOGGER.info("QA table jobs: walked {} listed builds and {} styles; {} show Jobs, {} show Beds; wrong: {}", done[0], done[1], done[2], done[3],
				wrong.size());
			wrong.stream().limit(40).forEach(w -> LOGGER.warn("QA table jobs: {}", w));
			helper.assertTrue(listed > 50 && done[0] == listed, "walked " + done[0] + " of " + listed + " listed builds");
			helper.assertTrue(done[1] > 0 && done[2] > 0 && done[3] > 0, "the walk saw no styles, jobs or beds: " + java.util.Arrays.toString(done));
			if (!wrong.isEmpty()) {
				throw new GameTestAssertException(wrong.size() + " builds show the wrong line, first: " + wrong.get(0));
			}
		});
	}

	/**
	 * B570: a job's config switch flipped after the Table listed a build (a reload turning physicians off, then on) reaches
	 * the listing as it does the details: for every listed build, the listing's jobs are the details' jobs after each flip.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaTableJobsFlip"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaTableJobsFlip")
	public void aSwitchFlippedAfterTheListingReachesTheListingToo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean unlocks = BlueprintUnlocks.ENABLED;
		boolean physicians = io.github.jcondedata.aliveworkplace.work.PhysicianWork.ENABLED;
		Leftovers.after(helper, () -> {
			BlueprintUnlocks.ENABLED = unlocks;
			io.github.jcondedata.aliveworkplace.work.PhysicianWork.ENABLED = physicians;
		});
		BlueprintUnlocks.ENABLED = false;
		int[] physicianBuilds = new int[2];
		for (boolean on : new boolean[] {true, false, true}) {
			io.github.jcondedata.aliveworkplace.work.PhysicianWork.ENABLED = on;
			int with = 0;
			for (TablePayloads.Entry entry : TableServer.listing(level, null)) {
				if (BlueprintStyles.parse(entry.id()).isPresent()) {
					continue;
				}
				Optional<TablePayloads.Details> details = TableServer.details(level, null, entry.id());
				if (details.isEmpty()) {
					continue;
				}
				List<ResourceLocation> flat = details.get().jobs().stream().flatMap(b -> b.jobs().stream()).distinct().toList();
				helper.assertTrue(entry.jobs().equals(flat), "physicians " + (on ? "on" : "off") + ": " + entry.id() + " is listed with " + entry.jobs()
					+ " but its details name " + flat);
				with += entry.jobs().contains(id(ModVillagers.PHYSICIAN)) ? 1 : 0;
			}
			physicianBuilds[on ? 0 : 1] = with;
		}
		boolean live = io.github.jcondedata.aliveworkplace.threat.Disasters.live();
		helper.assertTrue(physicianBuilds[1] == 0 && (!live || physicianBuilds[0] > 0),
			"builds listed with the Physician (on, off): " + java.util.Arrays.toString(physicianBuilds) + ", disasters live " + live);
		LOGGER.info("QA table jobs: with physicians on {} builds list the Physician, off {}", physicianBuilds[0], physicianBuilds[1]);
		helper.succeed();
	}

	/**
	 * 36.6b's own example: the Starter Cottage, picked at the Table, shows "Jobs: Fisherman (1), Carpenter (1)" and
	 * "Beds: 1" under its size, and its upgrade (a level) gets its own line from its own blocks.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStarterCottageShowsTheOwnersExample(GameTestHelper helper) {
		TablePayloads.Details cottage = TableServer.details(helper.getLevel(), null, StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
		List<String> lines = shown(cottage.jobs(), cottage.beds());
		helper.assertTrue(lines.equals(List.of("Jobs: Fisherman (1), Carpenter (1)", "Beds: 1")), "the Starter Cottage shows " + lines);
		helper.succeed();
	}

	/**
	 * A build a player scans with the Scan Tool (real clicks) is listed at the Table with the line of its own blocks:
	 * a barrel, two smokers and a bed give "Jobs: Fisherman (1), Butcher/Chef/Rancher (2)" and "Beds: 1"; a scanned
	 * shed of stone and a chest gives neither line.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaTableJobsScan"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaTableJobsScan")
	public void aScannedBuildShowsTheJobsAndBedsOfItsBlocks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		BlockPos base = new BlockPos(1, 2, 1);
		helper.setBlock(base, Blocks.BARREL);
		helper.setBlock(base.east(), Blocks.SMOKER);
		helper.setBlock(base.east(2), Blocks.SMOKER);
		BlockState bed = Blocks.CYAN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		helper.setBlock(base.south(), bed.setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(base.south(2), bed.setValue(BedBlock.PART, BedPart.HEAD));
		helper.setBlock(base.east(2).south(2), Blocks.STONE_BRICKS);
		ResourceLocation workshop = scan(helper, player, seen, base, base.east(2).south(2));

		BlockPos shed = new BlockPos(5, 2, 1);
		helper.setBlock(shed, Blocks.STONE);
		helper.setBlock(shed.east(), Blocks.CHEST);
		helper.setBlock(shed.south(), Blocks.OAK_PLANKS);
		ResourceLocation plain = scan(helper, player, seen, shed, shed.east().south());

		List<ResourceLocation> listed = TableServer.listing(level, null).stream().map(TablePayloads.Entry::id).toList();
		helper.assertTrue(listed.contains(workshop) && listed.contains(plain), "the Table doesn't list the scans " + workshop + ", " + plain);
		TablePayloads.Details w = TableServer.details(level, null, workshop).orElseThrow();
		List<String> lines = shown(w.jobs(), w.beds());
		helper.assertTrue(lines.equals(List.of("Jobs: Fisherman (1), Butcher/Chef/Rancher (2)", "Beds: 1")), "the scanned workshop shows " + lines);
		TablePayloads.Details p = TableServer.details(level, null, plain).orElseThrow();
		helper.assertTrue(shown(p.jobs(), p.beds()).isEmpty(), "the scanned shed shows " + shown(p.jobs(), p.beds()));
		LOGGER.info("QA table jobs: scan {} shows {}; scan {} shows nothing", workshop, lines, plain);
		helper.succeed();
	}

	private static ResourceLocation scan(GameTestHelper helper, ServerPlayer player, List<Component> seen, BlockPos from, BlockPos to) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, from);
		QaStageThreeGameTests.click(helper, player, to);
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		ResourceLocation id = null;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				id = BlueprintItem.data(s).orElseThrow().structure();
				s.setCount(0);
			}
		}
		if (id == null) {
			throw new GameTestAssertException("the scan gave no blueprint");
		}
		return id;
	}

	/**
	 * A job block whose job needs Cobblemon: without it (this suite) the Training Post and the Ball Workbench bring no
	 * job and show no line, and the composter and the smithing table don't name the Berry Breeder or the Ball Smith.
	 * With Cobblemon they are named.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aJobWhoseModIsMissingIsLeftOut(GameTestHelper helper) {
		boolean cobblemon = Platform.get().isModLoaded("cobblemon");
		BuildJobs.Summary posts = BuildJobs.of(row(List.of(ModBlocks.TRAINING_POST.defaultBlockState(), ModBlocks.BALL_WORKBENCH.defaultBlockState())));
		BuildJobs.Summary shared = BuildJobs.of(row(List.of(Blocks.COMPOSTER.defaultBlockState(), Blocks.SMITHING_TABLE.defaultBlockState())));
		List<ResourceLocation> named = shared.jobs().stream().flatMap(b -> b.jobs().stream()).toList();
		String text = String.join(" | ", shown(shared.jobs(), shared.beds()));
		if (!cobblemon) {
			helper.assertTrue(posts.jobs().isEmpty() && shown(posts.jobs(), posts.beds()).isEmpty(),
				"without Cobblemon a Training Post and a Ball Workbench show " + shown(posts.jobs(), posts.beds()));
			helper.assertTrue(!named.contains(id(ModVillagers.BERRY_BREEDER)) && !named.contains(id(ModVillagers.BALL_SMITH)),
				"without Cobblemon the composter and smithing table name " + named);
			helper.assertTrue(named.contains(id(VillagerProfession.FARMER)) && named.contains(id(VillagerProfession.TOOLSMITH)),
				"the composter and smithing table lost their own jobs: " + named);
			helper.assertTrue(!text.contains("Berry") && !text.contains("Ball"), "the line reads " + text);
		} else {
			helper.assertTrue(named.contains(id(ModVillagers.BALL_SMITH)), "with Cobblemon the smithing table doesn't name the Ball Smith: " + named);
		}
		LOGGER.info("QA table jobs: cobblemon={} posts={} shared='{}'", cobblemon, posts.jobs(), text);
		helper.succeed();
	}

	/**
	 * The lines are built from the language, not from English words: in another language the "Jobs:" word, the comma
	 * between job blocks, each job's name and the "Beds:" word are that language's, and three beds of different colours
	 * facing different ways count as 3.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theLinesReadInThePlayersLanguage(GameTestHelper helper) {
		List<BlockState> states = new ArrayList<>();
		states.add(Blocks.BARREL.defaultBlockState());
		states.add(Blocks.LECTERN.defaultBlockState());
		for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.WEST)) {
			Block colour = facing == Direction.NORTH ? Blocks.WHITE_BED : facing == Direction.EAST ? Blocks.BLACK_BED : Blocks.LIME_BED;
			BlockState bed = colour.defaultBlockState().setValue(BedBlock.FACING, facing);
			states.add(bed.setValue(BedBlock.PART, BedPart.HEAD));
			states.add(bed.setValue(BedBlock.PART, BedPart.FOOT));
		}
		states.add(Blocks.LECTERN.defaultBlockState());
		BuildJobs.Summary summary = BuildJobs.of(row(states));
		helper.assertTrue(summary.beds() == 3, "three beds count as " + summary.beds());
		List<String> english = shown(summary.jobs(), summary.beds());
		helper.assertTrue(english.equals(List.of("Jobs: Fisherman (1), Librarian/Scholar/Teacher (2)", "Beds: 3")), "in English: " + english);

		Map<String, String> french = Map.of(
			"screen.aliveworkplace.table.jobs", "Métiers : %s",
			"screen.aliveworkplace.table.jobs_separator", " ; ",
			"screen.aliveworkplace.table.beds", "Lits : %s",
			"entity.minecraft.villager.fisherman", "Pêcheur",
			"entity.minecraft.villager.librarian", "Bibliothécaire");
		Language real = Language.getInstance();
		List<String> translated;
		try {
			Language.inject(new Language() {
				@Override
				public String getOrDefault(String key, String fallback) {
					return french.getOrDefault(key, real.getOrDefault(key, fallback));
				}

				@Override
				public boolean has(String key) {
					return french.containsKey(key) || real.has(key);
				}

				@Override
				public boolean isDefaultRightToLeft() {
					return real.isDefaultRightToLeft();
				}

				@Override
				public FormattedCharSequence getVisualOrder(FormattedText text) {
					return real.getVisualOrder(text);
				}
			});
			translated = shown(summary.jobs(), summary.beds());
		} finally {
			Language.inject(real);
		}
		String scholar = real.getOrDefault("entity.minecraft.villager." + ModVillagers.SCHOLAR.name());
		String teacher = real.getOrDefault("entity.minecraft.villager." + ModVillagers.TEACHER.name());
		List<String> want = List.of("Métiers : Pêcheur (1) ; Bibliothécaire/" + scholar + "/" + teacher + " (2)", "Lits : 3");
		helper.assertTrue(translated.equals(want), "in French the Table shows " + translated + ", not " + want);
		helper.assertTrue(english.equals(shown(summary.jobs(), summary.beds())), "English didn't come back");
		helper.succeed();
	}

	private static Blueprint row(List<BlockState> states) {
		List<Blueprint.Entry> blocks = new ArrayList<>();
		for (int i = 0; i < states.size(); i++) {
			blocks.add(new Blueprint.Entry(new BlockPos(i, 0, 0), states.get(i), null));
		}
		return new Blueprint(AliveWorkplace.id("uploads/qa/table_jobs_row"), new Vec3i(states.size(), 1, 1), blocks);
	}
}
