package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA 2026-10-28, 36.21 "Refresh to the latest look" with B661/B662 (the Clinic's pre-B639 drawing wasn't shipped in
 * {@code blueprint_history/}, so the owner's live Clinic with its slab-and-carpet cots was never offered the refresh).
 * From the player's side, data-wide: every older drawing the mod ships that differs from today's drawing must be
 * recognised when a building stands as it drew it (offered a refresh); a pre-B639 Clinic pressed through the hall's
 * Repairs page ends as a fresh build of today's Clinic with real beds; a Clinic built from today's drawing isn't offered one.
 */
public class QaRefreshHistory1028GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String DIR = "blueprint_history";
	/** The huge area's size on each side, and the lowest helper y a block may go (y = 1 is the floor). */
	private static final int AREA = 30, BASE = 2;
	/** The Clinic's drawing before B639 redrew its ward with real beds (as 0.151.0 shipped it). */
	private static final String PRE_B639 = "2f10c0ccc";
	private static final ResourceLocation CLINIC = AliveWorkplace.id("clinic");
	private static final List<ResourceLocation> CLINICS = List.of(CLINIC, AliveWorkplace.id("village/desert_clinic"),
		AliveWorkplace.id("village/plains_clinic"), AliveWorkplace.id("village/savanna_clinic"), AliveWorkplace.id("village/snowy_clinic"),
		AliveWorkplace.id("village/taiga_clinic"));

	private record Drawing(ResourceLocation structure, String key) {
		@Override
		public String toString() {
			return structure + "@" + key;
		}
	}

	/** Every older drawing the server loaded, read from the resources as {@code Refreshes} reads them. */
	private static List<Drawing> shipped(MinecraftServer server) {
		TreeSet<String> seen = new TreeSet<>();
		List<Drawing> out = new ArrayList<>();
		for (ResourceLocation file : server.getResourceManager().listResources(DIR, id -> id.getPath().endsWith(".nbt")).keySet()) {
			String path = file.getPath().substring(DIR.length() + 1, file.getPath().length() - ".nbt".length());
			int slash = path.lastIndexOf('/');
			if (slash <= 0 || !seen.add(file.toString())) {
				continue;
			}
			out.add(new Drawing(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(0, slash)), path.substring(slash + 1)));
		}
		out.sort(java.util.Comparator.comparing(Drawing::toString));
		return out;
	}

	/** Today's drawing of {@code structure} as a new vanilla build has it (what {@code Refreshes} compares with). */
	private static Optional<Blueprint> today(ServerLevel level, ResourceLocation structure) {
		return BlueprintLibrary.get(level, structure)
			.map(b -> Refreshes.refreshedFurniture(level, structure, FurnitureChoice.VANILLA).apply(b, structure));
	}

	private static boolean same(BlockState a, BlockState b) {
		boolean airA = a == null || a.isAir();
		boolean airB = b == null || b.isAir();
		if (airA || airB) {
			return airA && airB;
		}
		return MaterialRules.matches(a, b);
	}

	/** Whether the two plans put anything different anywhere. */
	private static boolean differ(Map<BlockPos, BlockState> was, Map<BlockPos, BlockState> now) {
		Set<BlockPos> spots = new HashSet<>(was.keySet());
		spots.addAll(now.keySet());
		for (BlockPos pos : spots) {
			if (!same(was.get(pos), now.get(pos))) {
				return true;
			}
		}
		return false;
	}

	/** {min x, min y, min z, max x, max y, max z} of the plans' spots, relative to {@code origin}. */
	private static int[] extent(BlockPos origin, Map<BlockPos, BlockState> a, Map<BlockPos, BlockState> b) {
		int[] e = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (Map<BlockPos, BlockState> m : List.of(a, b)) {
			for (BlockPos p : m.keySet()) {
				int x = p.getX() - origin.getX(), y = p.getY() - origin.getY(), z = p.getZ() - origin.getZ();
				e[0] = Math.min(e[0], x);
				e[1] = Math.min(e[1], y);
				e[2] = Math.min(e[2], z);
				e[3] = Math.max(e[3], x);
				e[4] = Math.max(e[4], y);
				e[5] = Math.max(e[5], z);
			}
		}
		return e;
	}

	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/**
	 * Every shipped older drawing that differs from today's: a building standing exactly as it drew it, recorded
	 * finished, is offered "Refresh to the latest look". One drawing per tick, each placed, checked and cleared away
	 * again within its tick (so nothing falls, flows or is left behind); every failure named in one message.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaRefreshHistoryAll"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaRefreshHistoryAll")
	public void qaEveryShippedOlderDrawingIsOfferedARefresh(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		List<Drawing> drawings = shipped(level.getServer());
		helper.assertTrue(drawings.size() > 100, "the server lists only " + drawings.size() + " older drawings under " + DIR);
		// the area emptied above its floor
		for (int x = 0; x < AREA; x++) {
			for (int z = 0; z < AREA; z++) {
				for (int y = BASE; y < AREA; y++) {
					BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
					if (!level.getBlockState(p).isAir()) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
					}
				}
			}
		}
		UUID owner = UUID.randomUUID();
		List<BlueprintData.Placement> used = new ArrayList<>();
		Leftovers.after(helper, () -> used.forEach(p -> BuildSiteManager.get(level).forgetFinished(p)));
		List<String> failures = new ArrayList<>();
		List<String> tooBig = new ArrayList<>();
		List<String> modded = new ArrayList<>();
		List<String> noBlueprint = new ArrayList<>();
		List<String> unchanged = new ArrayList<>();
		List<String> otherFrom = new ArrayList<>();
		Set<ResourceLocation> checkedStructures = new HashSet<>();
		int[] checked = {0};
		BlockPos zero = helper.absolutePos(BlockPos.ZERO);
		for (int i = 0; i < drawings.size(); i++) {
			Drawing d = drawings.get(i);
			helper.runAfterDelay(2 + i, () -> {
				try {
					if (d.structure().getPath().startsWith(io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary.PREFIX)) {
						modded.add(d.toString()); // a modded library copy's history: its mods aren't in this run
						return;
					}
					Optional<Blueprint> old = Refreshes.olderDrawing(level.getServer(), d.structure(), d.key());
					if (old.isEmpty()) {
						failures.add(d + ": listed but not loaded by Refreshes");
						return;
					}
					Optional<Blueprint> now = today(level, d.structure());
					if (now.isEmpty()) {
						noBlueprint.add(d.toString());
						return;
					}
					Blueprint asBuilt = FurnitureChoice.VANILLA.applyAsBuilt(old.get());
					BlueprintData.Placement probe = new BlueprintData.Placement(level.dimension().location(), zero, Rotation.NONE, Mirror.NONE);
					int[] e = extent(zero, BuildPlan.create(asBuilt, probe).targets(), BuildPlan.create(now.get(), probe).targets());
					int sx = e[3] - e[0] + 1, sy = e[4] - e[1] + 1, sz = e[5] - e[2] + 1;
					if (sx > AREA || sz > AREA || sy > AREA - BASE) {
						tooBig.add(d + " (" + sx + "x" + sy + "x" + sz + ")");
						return;
					}
					BlockPos origin = helper.absolutePos(new BlockPos(-e[0], BASE - e[1], -e[2]));
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
					Map<BlockPos, BlockState> was = BuildPlan.create(asBuilt, placement).targets();
					Map<BlockPos, BlockState> want = BuildPlan.create(now.get(), placement).targets();
					if (!differ(was, want)) {
						unchanged.add(d.toString());
						return;
					}
					for (Map.Entry<BlockPos, BlockState> b : was.entrySet()) {
						level.setBlock(b.getKey(), b.getValue(), FLAGS);
					}
					BuildSiteManager manager = BuildSiteManager.get(level);
					manager.recordFinished(d.structure(), placement, owner);
					used.add(placement);
					try {
						BuildSiteManager.Finished f = manager.finishedIn(level).stream().filter(b -> b.placement().equals(placement)).findFirst()
							.orElse(new BuildSiteManager.Finished(d.structure(), placement, owner));
						Optional<Refreshes.Change> change = Refreshes.change(level, f);
						checked[0]++;
						checkedStructures.add(d.structure());
						if (change.isEmpty()) {
							failures.add(d.toString());
						} else if (!d.key().equals(change.get().from())) {
							otherFrom.add(d + " taken as " + change.get().from());
						}
					} finally {
						manager.forgetFinished(placement);
						for (BlockPos p : was.keySet()) {
							level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
						}
					}
				} catch (RuntimeException ex) {
					failures.add(d + ": " + ex);
				}
			});
		}
		helper.runAfterDelay(4 + drawings.size(), () -> {
			AliveWorkplace.LOG.info("QaRefreshHistory: {} older drawings listed, {} checked, {} unchanged from today ({}), {} too big for the area ({}), "
					+ "{} modded copies skipped, {} with no blueprint today ({}), {} recognised as another drawing ({})",
				drawings.size(), checked[0], unchanged.size(), unchanged, tooBig.size(), tooBig, modded.size(), noBlueprint.size(), noBlueprint,
				otherFrom.size(), otherFrom);
			List<ResourceLocation> missingClinics = CLINICS.stream().filter(c -> !checkedStructures.contains(c)).toList();
			helper.assertTrue(missingClinics.isEmpty(), "no older drawing of " + missingClinics + " was checked (" + checked[0] + " checked in all)");
			helper.assertTrue(failures.isEmpty(), failures.size() + " of " + checked[0] + " shipped older drawings: a building standing as it "
				+ "drew it is offered no 'Refresh to the latest look': " + failures);
			helper.succeed();
		});
	}

	// ---- through the hall --------------------------------------------------------------------------------------------

	private static final BlockPos HOUSE = new BlockPos(9, 2, 9);

	private record Stage(ServerLevel level, ServerPlayer owner, ResourceLocation structure, BlueprintData.Placement placement, BlockPos hall) {
	}

	/** {@code drawing} standing at {@link #HOUSE} recorded as a finished {@code structure}, a hall, and (with {@code builder}) a Builder with stores. */
	private static Stage stage(GameTestHelper helper, ResourceLocation structure, Blueprint drawing, boolean builder) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32;
		helper.setDayTime(2000);
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getServer().getPlayerList().remove(owner);
		});
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(drawing, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), FLAGS);
		}
		BlockPos hall = new BlockPos(1, 2, 1);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		if (builder) {
			for (BlockPos c : List.of(new BlockPos(1, 2, 26), new BlockPos(1, 2, 28), new BlockPos(5, 2, 26), new BlockPos(5, 2, 28))) {
				helper.setBlock(c, Blocks.CHEST);
			}
			helper.setBlock(new BlockPos(3, 2, 26), ModBlocks.BUILDERS_BENCH);
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 24));
			Builders.employ(level, v, helper.absolutePos(new BlockPos(3, 2, 26)));
			ModAttachments.BUILDER_EMPLOYER.set(v, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		}
		BuildSiteManager.get(level).recordFinished(structure, placement, owner.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Stage(level, owner, structure, placement, helper.absolutePos(hall));
	}

	/** The slot of the build's Refresh entry on the hall's Repairs page as the owner opens it, or -1 when there is none. */
	private static int refreshEntry(ChoiceMenu menu) {
		for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE; i++) {
			if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
				return i;
			}
		}
		return -1;
	}

	private static Optional<BuildSiteManager.Finished> finished(Stage s) {
		return BuildSiteManager.get(s.level()).finishedIn(s.level()).stream().filter(b -> b.placement().equals(s.placement())).findFirst();
	}

	/**
	 * A Clinic standing as its pre-B639 drawing drew it (one bed, slab-and-carpet cots): the owner presses Refresh on the
	 * Repairs page, and the refresh's plan carried out leaves a fresh build of today's Clinic, real beds and no cots.
	 */
	private static void oldClinicRefreshedToRealBeds(GameTestHelper helper, ResourceLocation structure) {
		ServerLevel level = helper.getLevel();
		Blueprint old = Refreshes.olderDrawing(level.getServer(), structure, PRE_B639)
			.orElseThrow(() -> new IllegalStateException(structure + "'s pre-B639 drawing " + PRE_B639 + " isn't shipped in " + DIR));
		Blueprint asBuilt = FurnitureChoice.VANILLA.applyAsBuilt(old);
		Stage s = stage(helper, structure, asBuilt, true);
		helper.runAfterDelay(2, () -> {
			Blueprint current = today(level, structure).orElseThrow();
			Map<BlockPos, BlockState> was = BuildPlan.create(asBuilt, s.placement()).targets();
			Map<BlockPos, BlockState> now = BuildPlan.create(current, s.placement()).targets();
			long oldBeds = was.values().stream().filter(b -> b.getBlock() instanceof BedBlock).count();
			long newBeds = now.values().stream().filter(b -> b.getBlock() instanceof BedBlock).count();
			helper.assertTrue(newBeds > oldBeds, "setup: today's " + structure + " has no more bed blocks (" + newBeds + ") than its pre-B639 drawing (" + oldBeds + ")");
			ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), s.hall());
			menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
			int slot = refreshEntry(menu);
			helper.assertTrue(slot >= 0, "a " + structure + " built before B639 (cots) has no 'Refresh to the latest look' on the hall's Repairs page");
			menu.press(slot, s.owner());
			menu.press(RepairsPage.REFRESH, s.owner());
			BuildSite site = Refreshes.siteOf(level, s.placement());
			helper.assertTrue(site != null, "no Builder took the " + structure + "'s refresh up");
			helper.assertTrue(PRE_B639.equals(site.refreshFrom()), "the " + structure + " was refreshed from drawing " + site.refreshFrom()
				+ ", but it stands as " + PRE_B639 + " drew it");
			BuildPlan plan = site.plan(level);
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					level.setBlock(step.pos(), step.state(), FLAGS);
					if (step.state().getBlock() instanceof BedBlock) {
						// (as the Builder puts a bed: both halves at once)
						BlockPos other = step.pos().relative(BedBlock.getConnectedDirection(step.state()));
						if (now.containsKey(other)) {
							level.setBlock(other, now.get(other), FLAGS);
						}
					}
				}
			}
			List<String> left = RefreshMatchesFreshBuildGameTests.differences(level, s.placement(), asBuilt, current);
			helper.assertTrue(left.isEmpty(), "the refreshed " + structure + ": " + left.size() + " spots differ from a fresh build: "
				+ left.subList(0, Math.min(10, left.size())));
			List<String> noBed = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : now.entrySet()) {
				if (e.getValue().getBlock() instanceof BedBlock && !(level.getBlockState(e.getKey()).getBlock() instanceof BedBlock)) {
					noBed.add(e.getKey().toShortString() + " " + level.getBlockState(e.getKey()).getBlock().getDescriptionId());
				}
			}
			helper.assertTrue(noBed.isEmpty(), "the refreshed " + structure + " has no real bed where today's drawing has one: " + noBed);
			List<String> cots = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : was.entrySet()) {
				boolean cot = e.getValue().getBlock() instanceof SlabBlock || e.getValue().getBlock() instanceof CarpetBlock;
				if (cot && !same(e.getValue(), now.get(e.getKey())) && same(level.getBlockState(e.getKey()), e.getValue())) {
					cots.add(e.getKey().toShortString() + " " + e.getValue().getBlock().getDescriptionId());
				}
			}
			helper.assertTrue(cots.isEmpty(), "the refreshed " + structure + " still has the old drawing's slabs or carpets: " + cots);
			Optional<BuildSiteManager.Finished> f = finished(s);
			helper.assertTrue(f.isPresent(), "the refreshed " + structure + " is no longer recorded as a finished building");
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE '400' '"qaRefreshHistoryClinic"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaRefreshHistoryClinic")
	public void qaAPreB639ClinicIsRefreshedToRealBedsFromTheHall(GameTestHelper helper) {
		oldClinicRefreshedToRealBeds(helper, CLINIC);
	}

	//$ gametest_ticks_batch HUGE '400' '"qaRefreshHistorySnowyClinic"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaRefreshHistorySnowyClinic")
	public void qaAPreB639SnowyClinicIsRefreshedToRealBedsFromTheHall(GameTestHelper helper) {
		oldClinicRefreshedToRealBeds(helper, AliveWorkplace.id("village/snowy_clinic"));
	}

	//$ gametest_ticks_batch HUGE '400' '"qaRefreshHistoryDesertClinic"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaRefreshHistoryDesertClinic")
	public void qaAPreB639DesertClinicIsRefreshedToRealBedsFromTheHall(GameTestHelper helper) {
		oldClinicRefreshedToRealBeds(helper, AliveWorkplace.id("village/desert_clinic"));
	}

	/** A Clinic built from today's drawing: nothing to refresh, and no Refresh entry on the hall's Repairs page. */
	private static void todaysClinicIsNotOffered(GameTestHelper helper, ResourceLocation structure) {
		ServerLevel level = helper.getLevel();
		Blueprint current = today(level, structure).orElseThrow(() -> new IllegalStateException("no blueprint " + structure));
		Stage s = stage(helper, structure, current, false);
		helper.runAfterDelay(2, () -> {
			BuildSiteManager.Finished f = finished(s).orElseThrow(() -> new IllegalStateException("the Clinic isn't recorded finished"));
			Optional<Refreshes.Change> change = Refreshes.change(level, f);
			helper.assertTrue(change.isEmpty(), "a " + structure + " built from today's drawing is offered a refresh from "
				+ change.map(c -> c.from() + " (" + c.total() + " pieces)").orElse(""));
			ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), s.hall());
			menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
			int slot = refreshEntry(menu);
			helper.assertTrue(slot < 0, "the hall's Repairs page shows a Refresh entry for a " + structure + " built from today's drawing: "
				+ (slot >= 0 ? menu.icon(slot).getHoverName().getString() : ""));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE '200' '"qaRefreshHistoryTodayClinic"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaRefreshHistoryTodayClinic")
	public void qaAClinicBuiltFromTodaysDrawingIsNotOfferedARefresh(GameTestHelper helper) {
		todaysClinicIsNotOffered(helper, CLINIC);
	}

	//$ gametest_ticks_batch HUGE '200' '"qaRefreshHistoryTodayDesertClinic"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaRefreshHistoryTodayDesertClinic")
	public void qaADesertClinicBuiltFromTodaysDrawingIsNotOfferedARefresh(GameTestHelper helper) {
		todaysClinicIsNotOffered(helper, AliveWorkplace.id("village/desert_clinic"));
	}
}
