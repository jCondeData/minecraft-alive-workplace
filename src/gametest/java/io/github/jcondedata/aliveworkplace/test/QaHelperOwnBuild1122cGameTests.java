package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.Renewals;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderCrews;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane C (2026-11-22), B789 from its spec: the Steward gave an old house's renewal take-down to a builder who was
 * lending a hand on another builder's build, and it sat at STARTING with nothing taken down for as long as the helped
 * build stayed open. Expected (ROADMAP B789, CHANGELOG, docs/wiki/steward.md): an old house's renewal take-down, and any
 * Steward build, starts and finishes like any build even when the chosen builder was helping elsewhere. A helper counts
 * as idle when the Steward picks a builder; a helper's approved build starts at once (outcome STARTED) and he leaves the
 * crew for it; a world saved with a helper's own build queued behind the build he helps heals on load; a Wonder's crew
 * is the boundary: a crew member is not taken off (35.5).
 * <p>
 * Each test: its own batch, {@code Leftovers.clear} first (through {@link RenewalGameTests#village} or here), villagers at
 * y = 2. The second builder's table is FARTHER from the hall than the lead's, so the Steward choosing him proves he
 * counted as idle (a busy-equal tie goes to the nearest, the lead).
 */
public class QaHelperOwnBuild1122cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation WELL = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "well");
	/** The helped build (5x4x5), south of the Homes zone and clear of the old house. */
	private static final BlockPos HUT_AT = new BlockPos(22, 2, 24);
	/** The lead's chest by the village's Blueprint Table (2,2,24; RenewalGameTests.village), 10 from the hall (2,2,14). */
	private static final BlockPos LEAD_CHEST = new BlockPos(3, 2, 25);
	/** The second builder's table, 14 from the hall: farther than the lead's. */
	private static final BlockPos MATE_TABLE = new BlockPos(2, 2, 28);
	private static final BlockPos MATE_CHEST = new BlockPos(3, 2, 28);
	private static final BlockPos MATE_CHEST_2 = new BlockPos(1, 2, 28);

	private record Scene(RenewalGameTests.Village v, Villager mate, UUID hut, StewardDesk.Proposal proposal) {
	}

	/** The village with the lead (Bram) on the hut, the mate (Wren) helping there, and (with {@code renewal}) the old house's renewal on the desk. */
	private static Scene scene(GameTestHelper helper, boolean stocked, boolean renewal) {
		RenewalGameTests.Village v = RenewalGameTests.village(helper, "", true);
		ServerLevel level = v.level();
		boolean helps = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(helps, level.getServer()));
		v.builder().setCustomName(Component.literal("Bram"));
		StewardDesk.Proposal proposal = null;
		if (renewal) {
			BoundingBox old = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
			OldHouses.House house = RenewalGameTests.measure(helper, v, RenewalGameTests.beds(level, old).get(0));
			proposal = RenewalGameTests.propose(helper, v, house);
		}
		BuildSite hut = Builders.start(level, v.builder(), v.owner(), HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_AT), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = hut.plan(level);
		helper.assertTrue(plan != null, "setup: no test hut blueprint");
		if (stocked) {
			Container chest = helper.getBlockEntity(LEAD_CHEST);
			int slot = 0;
			for (Map.Entry<Item, Integer> need : plan.materials().entrySet()) {
				chest.setItem(slot++, new ItemStack(need.getKey(), Math.min(64, need.getValue())));
			}
		}
		helper.setBlock(MATE_TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager mate = helper.spawn(EntityType.VILLAGER, MATE_TABLE.east().east());
		mate.setCustomName(Component.literal("Wren"));
		Builders.employ(level, mate, helper.absolutePos(MATE_TABLE));
		BuildSite joined = Builders.recruit(level, mate);
		helper.assertTrue(joined == hut, "setup: the second builder didn't join the hut as its helper: " + joined);
		return new Scene(v, mate, hut.id(), proposal);
	}

	private static BuildSite live(ServerLevel level, UUID id) {
		return BuildSiteManager.get(level).get(id);
	}

	private static String state(ServerLevel level, UUID id) {
		BuildSite s = live(level, id);
		return s == null ? "gone" : s.stage() + " " + s.status() + ", queued " + s.isQueued() + ", " + s.placed() + " placed";
	}

	/** The renewal moved on from the take-down to the rebuild: the old house is down. */
	private static boolean takenDown(RenewalGameTests.Village v) {
		List<Renewals.Renewal> active = Renewals.active(v.level(), v.hall());
		BuildSite now = active.isEmpty() ? null : live(v.level(), active.get(0).site());
		return now != null && !now.isDeconstruction();
	}

	/** Approves the renewal as the Steward himself would (Run the village); the take-down must be the mate's, started at once. */
	private static UUID approveTakeDown(GameTestHelper helper, Scene s, Villager mate) {
		ServerLevel level = s.v().level();
		StewardDesk.Outcome outcome = StewardDesk.approve(level, s.v().hall(), null, s.proposal().id());
		BuildSite down = RenewalGameTests.site(helper, s.v());
		helper.assertTrue(down.isDeconstruction(), "setup: the renewal's first site isn't the take-down");
		helper.assertTrue(mate.getUUID().equals(down.builder()),
			"the Steward didn't give the take-down to the helper (who counts as idle) but to " + down.builder() + " (lead " + s.v().builder().getUUID() + ")");
		helper.assertTrue(outcome == StewardDesk.Outcome.STARTED, "a helper's take-down should start at once (STARTED), got " + outcome);
		helper.assertTrue(!down.isQueued(), "the helper's take-down is queued instead of started");
		helper.assertTrue(!Builders.isHelping(mate), "the helper is still on the crew after his own take-down started");
		BuilderJob job = ModAttachments.BUILDER_JOB.get(mate);
		helper.assertTrue(job != null && job.siteId().equals(down.id()), "the helper's job isn't his take-down: " + job);
		BuildSite hut = live(level, s.hut());
		helper.assertTrue(hut != null && s.v().builder().getUUID().equals(hut.builder()) && !hut.isQueued(), "the hut isn't its lead's open build any more: " + state(level, s.hut()));
		return down.id();
	}

	// --------------------------------------------------------------------------------------------- the take-down, live

	/**
	 * The happy path with everything stocked: the helper picked for the take-down starts at once, takes blocks down (the
	 * site leaves STARTING with blocks done) and finishes, handing the plot to the rebuild; the hut he helped keeps its lead
	 * and goes on to the end. The chronicle's sentence names him as the one who takes the old house down.
	 */
	//$ gametest_ticks_batch AREA '8000' '"qaHelperTakeDownLive"'
	@GameTest(template = AREA, timeoutTicks = 8000, batch = "qaHelperTakeDownLive")
	public void aHelperPickedForATakeDownTakesItDownWhileTheHutGoesOn(GameTestHelper helper) {
		Scene s = scene(helper, true, true);
		ServerLevel level = s.v().level();
		UUID down = approveTakeDown(helper, s, s.mate());
		String line = s.v().entity().chronicle().stream().map(Chronicle.Entry::text)
			.filter(t -> t.getContents() instanceof TranslatableContents tc && tc.getKey().equals("chronicle.aliveworkplace.plans_renew"))
			.map(Component::getString).reduce((a, b) -> b).orElse(null);
		helper.assertTrue(line != null, "the chronicle has no renewal line");
		helper.assertTrue(line.contains("Wren takes it down and builds it again") && !line.contains("Bram") && !line.contains("%"),
			"the chronicle's renewal line doesn't name the helper as the one taking it down: " + line);
		AtomicInteger mostDone = new AtomicInteger();
		AtomicBoolean leftStarting = new AtomicBoolean();
		helper.onEachTick(() -> {
			BuildSite d = live(level, down);
			if (d != null) {
				mostDone.accumulateAndGet(d.placed(), Math::max);
				if (d.status() != BuildSite.Status.STARTING) {
					leftStarting.set(true);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(live(level, s.hut()) == null, "the hut the helper left isn't finished yet: " + state(level, s.hut()));
			helper.assertTrue(takenDown(s.v()), "the old house isn't taken down yet: take-down " + state(level, down)
				+ ", its builder at " + helper.relativePos(s.mate().blockPosition()) + (Builders.isHelping(s.mate()) ? ", helping" : ""));
			helper.assertTrue(leftStarting.get() && mostDone.get() > 0,
				"the take-down ended without ever being worked: most done " + mostDone.get() + ", left STARTING " + leftStarting.get());
		});
	}

	// ------------------------------------------------------------------------------------- the lead waits for materials

	/**
	 * The bug's own shape: the hut's lead stands WAITING_FOR_MATERIALS (nothing in his chest) with the mate helping, and
	 * only then the Steward approves. The take-down is the mate's, starts at once and is finished while the hut still waits.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaHelperTakeDownLeadWaits"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaHelperTakeDownLeadWaits")
	public void aLeadWaitingForMaterialsDoesNotHoldTheHelpersTakeDown(GameTestHelper helper) {
		Scene s = scene(helper, false, true);
		ServerLevel level = s.v().level();
		AtomicReference<UUID> down = new AtomicReference<>();
		helper.succeedWhen(() -> {
			if (down.get() == null) {
				BuildSite hut = live(level, s.hut());
				helper.assertTrue(hut != null && hut.status() == BuildSite.Status.WAITING_FOR_MATERIALS && Builders.isHelping(s.mate()),
					"waiting for the hut's lead to wait for materials with the mate helping: hut " + state(level, s.hut()) + ", helping " + Builders.isHelping(s.mate()));
				down.set(approveTakeDown(helper, s, s.mate()));
			}
			helper.assertTrue(takenDown(s.v()), "the old house isn't taken down while the hut's lead waits: take-down " + state(level, down.get())
				+ ", helper " + (Builders.isHelping(s.mate()) ? "helping" : "not helping"));
			BuildSite hut = live(level, s.hut());
			helper.assertTrue(hut != null && s.v().builder().getUUID().equals(hut.builder()), "the waiting hut lost its lead: " + state(level, s.hut()));
		});
	}

	// --------------------------------------------------------------------------------------------------- saved worlds

	/**
	 * A world saved the way every version before the fix left it (the take-down queued for the helper, his job still the
	 * hut), then a real restart: every site and the helper saved and loaded (a fresh entity). He takes the old house down.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaHelperTakeDownReload"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaHelperTakeDownReload")
	public void aHelperSavedWithHisTakeDownQueuedTakesItUpAfterARestart(GameTestHelper helper) {
		Scene s = scene(helper, false, true);
		ServerLevel level = s.v().level();
		// the old state, built by hand: the Steward's enqueue while he helps
		StewardDesk.Proposal p = s.proposal();
		Optional<StewardDesk.Builder> picked = StewardDesk.builderFor(level, s.v().hall(), p, true);
		helper.assertTrue(picked.isPresent() && picked.get().villager() == s.mate(), "setup: the Steward wouldn't pick the helper");
		StewardDesk.approve(level, s.v().hall(), null, p.id());
		BuildSite down = RenewalGameTests.site(helper, s.v());
		UUID id = down.id();
		BuildSite hut = live(level, s.hut());
		down.setQueued(true);
		ModAttachments.BUILDER_JOB.set(s.mate(), BuilderJob.helping(hut));
		hut.seen(s.mate().getUUID(), level.getGameTime());
		helper.assertTrue(down.isQueued() && Builders.isHelping(s.mate()), "setup: not the saved state");
		// the restart
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(s.mate().save(tag), "the helper wasn't saved");
		s.mate().discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the helper didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		Villager mate = (Villager) loaded;
		BuildSite reloaded = live(level, id);
		helper.assertTrue(reloaded != null && reloaded.isQueued() && reloaded.isDeconstruction() && mate.getUUID().equals(reloaded.builder()),
			"setup: the queued take-down didn't survive the restart: " + state(level, id));
		helper.assertTrue(Builders.isHelping(mate), "setup: the helper's job didn't survive the restart");
		helper.succeedWhen(() -> {
			helper.assertTrue(takenDown(s.v()), "after the restart the old house isn't taken down: take-down " + state(level, id)
				+ ", helper at " + helper.relativePos(mate.blockPosition()) + (Builders.isHelping(mate) ? ", still helping" : ", not helping"));
			BuildSite h = live(level, s.hut());
			helper.assertTrue(h != null && s.v().builder().getUUID().equals(h.builder()), "the hut lost its lead: " + state(level, s.hut()));
		});
	}

	// ------------------------------------------------------------------------------------------ who the Steward picks

	/**
	 * Both sides of pickBuilder's boundary. A helper (farther from the hall) beats a lead with one build: he counts as idle,
	 * nothing to build first; asking does not take him off the crew. A builder with a build of his own and one queued (2)
	 * loses to the lead (1), whose build comes first. A Steward home approved for a helper starts at once.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHelperPickBuilder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHelperPickBuilder")
	public void theStewardCountsAHelperAsIdleButABuilderWithAQueueAsBusy(GameTestHelper helper) {
		Scene s = scene(helper, false, true);
		ServerLevel level = s.v().level();
		Optional<StewardDesk.Builder> first = StewardDesk.builderFor(level, s.v().hall(), s.proposal(), true);
		helper.assertTrue(first.isPresent() && first.get().villager() == s.mate(),
			"a helper should count as idle and beat the lead with a build: picked " + first.map(b -> b.villager().getName().getString()));
		helper.assertTrue(first.get().after() == null, "the helper has nothing to build first, but the desk says after " + first.get().after());
		helper.assertTrue(Builders.isHelping(s.mate()) && ModAttachments.BUILDER_JOB.get(s.mate()).siteId().equals(s.hut()),
			"asking who would build took the helper off the crew");
		helper.assertTrue(!Builders.hasOwnBuild(level, s.mate()) && Builders.hasOwnBuild(level, s.v().builder()),
			"hasOwnBuild: helper " + Builders.hasOwnBuild(level, s.mate()) + ", lead " + Builders.hasOwnBuild(level, s.v().builder()));
		// the other side: the mate with a build of his own and one queued
		List<UUID> extra = new ArrayList<>();
		Leftovers.after(helper, () -> extra.forEach(id -> BuildSiteManager.get(level).remove(id)));
		BuildSite own = Builders.start(level, s.mate(), s.v().owner(), HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(22, 2, 0)), Rotation.NONE, Mirror.NONE));
		extra.add(own.id());
		BuildSite next = Builders.enqueue(level, s.mate(), s.v().owner(), HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(16, 2, 0)), Rotation.NONE, Mirror.NONE));
		extra.add(next.id());
		helper.assertTrue(Builders.hasOwnBuild(level, s.mate()), "a builder on his own build doesn't count as having one");
		Optional<StewardDesk.Builder> second = StewardDesk.builderFor(level, s.v().hall(), s.proposal(), true);
		helper.assertTrue(second.isPresent() && second.get().villager() == s.v().builder(),
			"with 2 builds the mate should lose to the lead with 1: picked " + second.map(b -> b.villager().getName().getString()));
		helper.assertTrue(second.get().after() != null && second.get().after().id().equals(s.hut()), "the lead's build to come first should be the hut: " + second.get().after());
		helper.succeed();
	}

	/**
	 * Any Steward build, not only a renewal: a home (a well) approved while the chosen builder helps on the hut starts at
	 * once (STARTED), he leaves the crew, and with its materials by his table he builds it to the end.
	 */
	//$ gametest_ticks_batch AREA '8000' '"qaHelperStewardHome"'
	@GameTest(template = AREA, timeoutTicks = 8000, batch = "qaHelperStewardHome")
	public void aStewardHomeGivenToAHelperStartsAtOnceAndIsFinished(GameTestHelper helper) {
		Scene s = scene(helper, true, false); // (the hut stocked too: its lead takes nothing from the well's chest)
		ServerLevel level = s.v().level();
		BlueprintData.Placement at = new BlueprintData.Placement(io.github.jcondedata.aliveworkplace.mc.Ids.of(level.dimension()),
			helper.absolutePos(new BlockPos(14, 2, 8)), Rotation.NONE, Mirror.NONE);
		Optional<StewardDesk.Proposal> proposal = StewardDesk.offer(level, s.v().hall(), new io.github.jcondedata.aliveworkplace.city.StewardWishes.Wish(
				ResourceLocation.fromNamespaceAndPath("aliveworkplace", "test/qa_b789_home"), new io.github.jcondedata.aliveworkplace.city.StewardRules.Effect(
				io.github.jcondedata.aliveworkplace.city.StewardRules.Kind.BUILD, Optional.of(WELL), Optional.of("homes"), Optional.empty(),
				Optional.empty()), 50, "steward.aliveworkplace.why.renew", List.of()),
			WELL, at, "Homes 1", false);
		helper.assertTrue(proposal.isPresent(), "setup: the home wasn't proposed");
		// the well's materials in a chest by the helper's own table
		BuildSite probe = BuildSiteManager.get(level).create(s.v().owner().getUUID(), "Owner", WELL, at);
		BuildPlan plan = probe.plan(level);
		BuildSiteManager.get(level).remove(probe.id());
		helper.assertTrue(plan != null, "setup: no well blueprint");
		// what it is built of and decorated with, a few spare of each (what the foundation fills under it comes on top)
		Map<Item, Integer> stock = new java.util.LinkedHashMap<>(plan.materials());
		Builders.decorationNeed(level, plan).forEach((item, n) -> stock.merge(item, n, Math::max));
		List<ItemStack> stacks = new ArrayList<>();
		stock.forEach((item, n) -> {
			int left = n + 8;
			while (left > 0) {
				int take = Math.min(left, item.getDefaultMaxStackSize());
				stacks.add(new ItemStack(item, take));
				left -= take;
			}
		});
		int slot = 0;
		for (BlockPos at2 : List.of(MATE_CHEST, MATE_CHEST_2)) {
			helper.setBlock(at2, Blocks.CHEST);
			Container chest = helper.getBlockEntity(at2);
			for (int i = 0; i < chest.getContainerSize() && slot < stacks.size(); i++) {
				chest.setItem(i, stacks.get(slot++));
			}
		}
		helper.assertTrue(slot == stacks.size(), "setup: the well's materials don't fit two chests: " + stacks.size() + " stacks");
		StewardDesk.Outcome outcome = StewardDesk.approve(level, s.v().hall(), null, proposal.get().id());
		BuildSite well = BuildSiteManager.get(level).all().stream().filter(b -> b.structure().equals(WELL) && b.placement().equals(at)).findFirst().orElse(null);
		helper.assertTrue(well != null, "the home's site wasn't made: outcome " + outcome);
		UUID id = well.id();
		Entity got = well.builder() == null ? null : level.getEntity(well.builder());
		helper.assertTrue(s.mate().getUUID().equals(well.builder()), "the home went to " + (got == null ? well.builder() : got.getName().getString()
			+ " at " + helper.relativePos(got.blockPosition())) + ", not the helper Wren (who counts as idle); lead " + s.v().builder().getUUID()
			+ ", outcome " + outcome + ", hut " + state(level, s.hut()) + ", Wren helping " + Builders.isHelping(s.mate()));
		helper.assertTrue(outcome == StewardDesk.Outcome.STARTED && !well.isQueued(), "a helper's home should start at once: " + outcome + ", queued " + well.isQueued());
		helper.assertTrue(!Builders.isHelping(s.mate()), "the helper is still on the crew after his home started");
		helper.succeedWhen(() -> {
			helper.assertTrue(live(level, id) == null, "the home isn't finished yet: " + state(level, id) + ", missing " + (live(level, id) == null ? "" : live(level, id).missing()));
			helper.assertTrue(BuildSiteManager.get(level).finishedIn(level).stream().anyMatch(f -> f.placement().equals(at)), "the well isn't recorded as built");
			BuildSite hut = live(level, s.hut());
			helper.assertTrue(hut == null || s.v().builder().getUUID().equals(hut.builder()), "the hut lost its lead: " + state(level, s.hut()));
		});
	}

	// ----------------------------------------------------------------------------------------- a Wonder's crew (35.5)

	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final BlockPos W_HALL = new BlockPos(2, 2, 27);
	private static final BlockPos W_MIDDLE = new BlockPos(14, 2, 14);

	/**
	 * The boundary from the other side: a builder with a build of his own queued, called onto a Wonder's crew, is NOT a
	 * helper lending a hand. Asking for his site keeps him on the Wonder's part, his own build stays queued, and the
	 * Steward counts him as busy (hasOwnBuild); a hundred ticks of work later he is still on the crew.
	 */
	//$ gametest_ticks_batch TALL '300' '"qaHelperWonderCrew"'
	@GameTest(template = TALL, timeoutTicks = 300, batch = "qaHelperWonderCrew")
	public void aWondersCrewIsNotTakenOffForAQueuedBuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean helps = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(4, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(helps, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		});
		helper.setBlock(W_HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = helper.getBlockEntity(W_HALL);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		List<Villager> builders = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			BlockPos bench = new BlockPos(5 + 2 * i, 2, 28);
			helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
			Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(5 + 2 * i, 2, 26));
			Builders.employ(level, b, helper.absolutePos(bench));
			builders.add(b);
		}
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(level, helper.absolutePos(W_HALL), folly, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos origin = helper.absolutePos(W_MIDDLE).subtract(StructureTemplate.transform(new BlockPos(4, 0, 4), Mirror.NONE, Rotation.NONE, BlockPos.ZERO));
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE))));
		BlueprintData.Placement where = BlueprintItem.data(plan).flatMap(BlueprintData::placement).orElseThrow();
		WonderGoodsKit.stockAll(level, FOLLY, where);
		helper.assertTrue(WonderStages.handOver(owner, builders.get(0), plan) == InteractionResult.SUCCESS, "setup: the folly's plan wasn't handed over");
		BuildSite stageOne = Builders.activeSite(level, builders.get(0));
		helper.assertTrue(stageOne != null && stageOne.wonder() != null, "setup: the lead didn't start stage I: " + stageOne);
		WonderSite wonder = WonderManager.get(level).in(helper.absolutePos(W_HALL)).get(0);
		UUID wonderId = wonder.id();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (wonderId.equals(site.wonder())) {
					sites.remove(site.id());
				}
			}
			WonderManager.get(level).remove(wonderId);
		});
		Villager crew = builders.get(1);
		BuildSite own = Builders.enqueue(level, crew, owner, HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, Mirror.NONE));
		UUID ownId = own.id();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(ownId));
		BlockPos stone = wonder.stone().orElseThrow();
		owner.teleportTo(stone.getX() + 0.5, stone.getY() + 1, stone.getZ() + 0.5);
		helper.assertTrue(WonderCrews.call(owner) == 1, "setup: the owner's call wasn't made");
		BuildSite part = WonderCrews.join(level, crew);
		helper.assertTrue(part != null && wonderId.equals(part.wonder()) && Builders.isHelping(crew), "setup: the called builder didn't join the crew: " + part);
		UUID partId = part.id();
		BuildSite asked = Builders.activeSite(level, crew);
		helper.assertTrue(asked != null && asked.id().equals(partId), "a Wonder's crew member was taken off the crew for his queued build: now on " + asked);
		helper.assertTrue(Builders.isHelping(crew) && live(level, ownId) != null && live(level, ownId).isQueued(),
			"his own build should wait while the Wonder's crew holds him: helping " + Builders.isHelping(crew) + ", own " + state(level, ownId));
		helper.assertTrue(Builders.hasOwnBuild(level, crew), "the Steward should count a Wonder's crew member as busy (hasOwnBuild false)");
		helper.runAfterDelay(100, () -> {
			BuilderJob job = ModAttachments.BUILDER_JOB.get(crew);
			helper.assertTrue(job != null && job.helper() && job.siteId().equals(partId),
				"after 100 ticks the crew member left the Wonder's part: job " + job + ", own " + state(level, ownId));
			helper.assertTrue(live(level, ownId) != null && live(level, ownId).isQueued(), "his own build started while he is on the crew: " + state(level, ownId));
			helper.succeed();
		});
	}
}
