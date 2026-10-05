package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.StewardWork;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 27.8: the Steward's desk. A village on flat grass with a hall, a Blueprint Table and its builder, a Steward
 * appointed with the City Plan by the hall's owner, and a Homes zone east of the hall; a Well proposed nearest the hall.
 * Approving through the hall's "What next?" page starts the build for that builder, owned by the hall's owner; a
 * declined proposal stays away 3 days; proposals lapse; a stranger can't answer; Run the village starts builds by itself
 * (not with {@code stewardSelfRun} off); Rest plans nothing; proposals survive save and reload.
 */
public class StewardDeskGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	private static final ResourceLocation RULE = AliveWorkplace.id("test/desk_well");
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);

	/** The village: its pieces, for the tests. */
	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward, Villager builder) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Villager steward = helper.spawn(EntityType.VILLAGER, HALL.south(2));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(level, hall, owner, steward, builder);
	}

	private static StewardWishes.Wish wish(ResourceLocation rule) {
		return new StewardWishes.Wish(rule, new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.of(WELL), Optional.of("homes"),
			Optional.empty(), Optional.empty()), 50, "steward.aliveworkplace.why.beds", List.of(3L));
	}

	/** The Well proposed at {@link #SPOT} (a plot the 27.7 check accepts). */
	private static StewardDesk.Proposal propose(GameTestHelper helper, Village v, ResourceLocation rule) {
		Plots.Verdict verdict = Plots.check(v.level(), v.hall(), v.entity().plan(), "homes", WELL, helper.absolutePos(SPOT),
			Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		helper.assertTrue(verdict.plot().isPresent(), "setup: the spot doesn't fit: " + verdict);
		Plots.Plot plot = verdict.plot().get();
		Optional<StewardDesk.Proposal> p = StewardDesk.offer(v.level(), v.hall(), wish(rule), plot.blueprint(), plot.placement(), plot.zone(), false);
		helper.assertTrue(p.isPresent(), "setup: not proposed");
		return p.get();
	}

	private static List<BuildSite> sites(Village v) {
		return StewardDesk.openSites(v.level(), v.hall());
	}

	/** The desk through the hall's screen: the "What next?" button, then the first proposal, then Approve. */
	//$ gametest_ticks_batch AREA '40' '"deskApprove"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "deskApprove")
	public void approvingOnTheDeskStartsTheBuildForTheHallsOwner(GameTestHelper helper) {
		Village v = village(helper);
		StewardDesk.Proposal proposal = propose(helper, v, RULE);
		ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
		menu.press(VillageHallScreen.ADVICE, v.owner());
		helper.assertTrue(menu.icon(StewardDeskPage.STEWARD).is(ModItems.CITY_PLAN), "the page isn't the Steward's desk");
		helper.assertTrue(menu.icon(StewardDeskPage.FIRST_PROPOSAL).is(ModItems.BLUEPRINT), "no proposal on the desk");
		String lore = String.valueOf(menu.icon(StewardDeskPage.FIRST_PROPOSAL).get(net.minecraft.core.component.DataComponents.LORE));
		helper.assertTrue(lore.contains("Built by") || lore.contains("screen.aliveworkplace.desk.builder"), "no builder named: " + lore);
		menu.press(StewardDeskPage.FIRST_PROPOSAL, v.owner());
		helper.assertTrue(menu.icon(StewardDeskPage.APPROVE).is(net.minecraft.world.item.Items.EMERALD), "no Approve button");
		menu.press(StewardDeskPage.SHOW, v.owner());
		helper.assertTrue(BlueprintOutline.glowing(v.owner()), "Show me didn't light the outline");
		menu.press(StewardDeskPage.APPROVE, v.owner());
		List<BuildSite> open = sites(v);
		helper.assertTrue(open.size() == 1, "approving made " + open.size() + " sites");
		BuildSite site = open.get(0);
		helper.assertTrue(site.structure().equals(proposal.blueprint()) && site.placement().equals(proposal.placement()), "not the proposal's blueprint and spot");
		helper.assertTrue(site.owner().equals(v.owner().getUUID()), "not owned by the hall's owner");
		helper.assertTrue(v.builder().getUUID().equals(site.builder()), "not the village's builder");
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "the proposal is still on the desk");
		helper.assertTrue(StewardWishes.of(v.level(), v.hall()).used(RULE).times() == 1, "the wish wasn't counted as carried out");
		helper.assertTrue(v.entity().chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.PLANS), "no Plans line in the chronicle");
		// Cancel from the desk: the site goes.
		helper.assertTrue(StewardDesk.cancel(v.level(), v.hall(), v.owner(), site.id()), "cancel refused");
		helper.assertTrue(sites(v).isEmpty(), "the cancelled site is still open");
		helper.succeed();
	}

	/** A declined proposal stays away for 3 days, then may come back; one nobody answers lapses after 3 days. */
	//$ gametest_ticks_batch AREA '40' '"deskDecline"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "deskDecline")
	public void aDeclinedProposalStaysAwayThreeDaysAndOldOnesLapse(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		long time = level.getDayTime();
		try {
			StewardDesk.Proposal proposal = propose(helper, v, RULE);
			ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
			StewardDeskPage.renderProposal(menu, level, v.hall(), proposal.id());
			menu.press(StewardDeskPage.DECLINE, v.owner());
			helper.assertTrue(StewardDesk.of(level, v.hall()).proposals().isEmpty(), "declined, still there");
			Plots.Plot plot = Plots.check(level, v.hall(), v.entity().plan(), "homes", WELL, helper.absolutePos(SPOT),
				Rotation.COUNTERCLOCKWISE_90, Mirror.NONE).plot().orElseThrow();
			helper.assertTrue(StewardDesk.offer(level, v.hall(), wish(RULE), plot.blueprint(), plot.placement(), "Homes 1", false).isEmpty(),
				"proposed again the same day");
			level.setDayTime(time + 2 * 24000L);
			helper.assertTrue(StewardDesk.offer(level, v.hall(), wish(RULE), plot.blueprint(), plot.placement(), "Homes 1", false).isEmpty(),
				"proposed again 2 days later");
			level.setDayTime(time + 3 * 24000L);
			helper.assertTrue(StewardDesk.offer(level, v.hall(), wish(RULE), plot.blueprint(), plot.placement(), "Homes 1", false).isPresent(),
				"not proposed again after 3 days");
			level.setDayTime(time + 5 * 24000L);
			StewardDesk.lapse(level, v.hall());
			helper.assertTrue(StewardDesk.of(level, v.hall()).proposals().size() == 1, "lapsed after 2 days");
			level.setDayTime(time + 6 * 24000L);
			StewardDesk.lapse(level, v.hall());
			helper.assertTrue(StewardDesk.of(level, v.hall()).proposals().isEmpty(), "not lapsed after 3 days");
		} finally {
			level.setDayTime(time);
		}
		helper.succeed();
	}

	/** In a protected village a stranger can't approve, decline or change the mode. */
	//$ gametest_ticks_batch AREA '40' '"deskStranger"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "deskStranger")
	public void aStrangerCantApproveInAProtectedVillage(GameTestHelper helper) {
		Village v = village(helper);
		v.entity().setProtected(true);
		StewardDesk.Proposal proposal = propose(helper, v, RULE);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(!stranger.getUUID().equals(v.owner().getUUID()), "setup: the same player");
		ChoiceMenu menu = VillageHallScreen.forTest(stranger, v.hall());
		StewardDeskPage.renderProposal(menu, v.level(), v.hall(), proposal.id());
		menu.press(StewardDeskPage.APPROVE, stranger);
		menu.press(StewardDeskPage.DECLINE, stranger);
		helper.assertTrue(sites(v).isEmpty(), "a stranger started a build");
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).get(proposal.id()).isPresent(), "a stranger declined it");
		helper.assertTrue(StewardDesk.approve(v.level(), v.hall(), stranger, proposal.id()) == StewardDesk.Outcome.NOT_ALLOWED, "not refused");
		helper.assertTrue(!StewardDesk.setMode(v.level(), v.hall(), stranger, CityPlan.Mode.RUN), "a stranger changed the mode");
		helper.succeed();
	}

	/** Run the village: his morning planning starts the proposal with no click, once a day; with stewardSelfRun off he keeps asking. */
	//$ gametest_ticks_batch AREA '40' '"deskRun"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "deskRun")
	public void runTheVillageStartsBuildsWithNoClickUnlessTheConfigSaysAsk(GameTestHelper helper) {
		Village v = village(helper);
		boolean before = StewardDesk.SELF_RUN;
		try {
			StewardDesk.SELF_RUN = false;
			helper.assertTrue(!StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN), "Run the village chosen with the config off");
			v.entity().setPlan(v.entity().plan().withMode(CityPlan.Mode.RUN)); // a hall saved in Run the village before the config went off
			propose(helper, v, RULE);
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(sites(v).isEmpty(), "started a build with stewardSelfRun off");
			StewardDesk.SELF_RUN = true;
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(sites(v).size() == 1, "Run the village started " + sites(v).size() + " builds");
			helper.assertTrue(StewardDesk.of(v.level(), v.hall()).ran() == StewardWishes.day(v.level()), "the morning's run not noted");
		} finally {
			StewardDesk.SELF_RUN = before;
		}
		helper.succeed();
	}

	/** Rest: he plans nothing (no wishes ranked, the desk cleared, his line says so). */
	//$ gametest_ticks_batch AREA '40' '"deskRest"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "deskRest")
	public void restPlansNothing(GameTestHelper helper) {
		Village v = village(helper);
		propose(helper, v, RULE);
		helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.REST), "Rest refused");
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "proposals kept while resting");
		String line = StewardWork.PLANNER.plan(v.level(), v.steward(), v.hall()).getString();
		helper.assertTrue(StewardWishes.of(v.level(), v.hall()).day() == -1, "wishes ranked while resting");
		helper.assertTrue(line.equals(Language.getInstance().getOrDefault("message.aliveworkplace.steward.state.resting")), "his line: " + line);
		StewardDesk.plan(v.level(), v.steward(), v.hall());
		helper.assertTrue(sites(v).isEmpty() && StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "planned while resting");
		helper.succeed();
	}

	/** Another spot finds the next plot that fits; another style keeps the spot; both survive save and reload, with the declines. */
	//$ gametest_ticks_batch AREA '200' '"deskSpot"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "deskSpot")
	public void anotherSpotAnotherStyleAndSaveReload(GameTestHelper helper) {
		Village v = village(helper);
		StewardDesk.Proposal proposal = propose(helper, v, RULE);
		StewardDesk.Proposal other = propose(helper, v, AliveWorkplace.id("test/desk_other"));
		StewardDesk.decline(v.level(), v.hall(), v.owner(), other.id());
		boolean styled = StewardDesk.anotherStyle(v.level(), v.hall(), v.owner(), proposal.id());
		StewardDesk.Proposal now = StewardDesk.of(v.level(), v.hall()).get(proposal.id()).orElseThrow();
		if (!BlueprintStyles.all().isEmpty()) {
			helper.assertTrue(styled, "no other style for the Well");
			helper.assertTrue(BlueprintStyles.base(now.blueprint()).equals(WELL) && !now.blueprint().equals(proposal.blueprint())
				&& now.placement().equals(proposal.placement()), "another style: " + now);
		}
		helper.assertTrue(StewardDesk.anotherSpot(v.level(), v.hall(), v.owner(), proposal.id()), "another spot refused");
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).get(proposal.id()).orElseThrow().searching(), "not searching");
		helper.succeedWhen(() -> {
			StewardDesk.resolve(v.level(), v.hall());
			StewardDesk.Proposal moved = StewardDesk.of(v.level(), v.hall()).get(proposal.id()).orElseThrow();
			helper.assertTrue(!moved.searching(), "still searching");
			helper.assertTrue(!moved.placement().equals(proposal.placement()), "the same spot again");
			// Save and reload: the proposal (moved, styled) and the decline are kept.
			StewardDesk.State saved = StewardDesk.of(v.level(), v.hall());
			CompoundTag tag = v.entity().saveWithoutMetadata(v.level().registryAccess());
			v.entity().setStewardDesk(StewardDesk.State.EMPTY);
			v.entity().loadWithComponents(tag, v.level().registryAccess());
			helper.assertTrue(StewardDesk.of(v.level(), v.hall()).equals(saved), "not the same after reload: " + StewardDesk.of(v.level(), v.hall()));
			helper.assertTrue(saved.declined().containsKey(AliveWorkplace.id("test/desk_other").toString()), "the decline was lost");
		});
	}

	/** Every sentence the desk shows is in en_us.json. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyDeskSentenceIsTranslated(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> keys = new ArrayList<>(List.of("chronicle.aliveworkplace.plans", "chronicle.aliveworkplace.plans_upgrade",
			"message.aliveworkplace.steward.state.resting", "message.aliveworkplace.steward.desk.new", "message.aliveworkplace.steward.desk.ran",
			"message.aliveworkplace.steward.desk.and", "message.aliveworkplace.steward.desk.none_started", "message.aliveworkplace.steward.desk.approved_all",
			"message.aliveworkplace.steward.desk.approved", "message.aliveworkplace.steward.desk.queued", "message.aliveworkplace.steward.desk.declined",
			"message.aliveworkplace.steward.desk.cancelled", "message.aliveworkplace.steward.desk.not_allowed", "message.aliveworkplace.steward.desk.mode",
			"message.aliveworkplace.steward.desk.shown", "message.aliveworkplace.steward.desk.spot", "message.aliveworkplace.steward.desk.style",
			"message.aliveworkplace.steward.desk.no_style", "aliveworkplace.config.stewardSelfRun", "aliveworkplace.config.stewardSelfRun.tooltip"));
		for (StewardDesk.Outcome o : StewardDesk.Outcome.values()) {
			if (!o.ok()) {
				keys.add("message.aliveworkplace.steward.desk.outcome." + o.name().toLowerCase(java.util.Locale.ROOT));
			}
		}
		for (CityPlan.Mode m : CityPlan.Mode.values()) {
			keys.add("screen.aliveworkplace.desk.mode." + m.getSerializedName());
			keys.add("screen.aliveworkplace.desk.mode." + m.getSerializedName() + ".name");
		}
		for (String k : List.of("title", "level", "open", "mode.chosen", "mode.choose", "mode.run.off", "approve_all", "approve_all_hint", "open_hint",
			"resting", "resting_hint", "no_proposals", "no_proposals_hint", "site_idle", "site", "site_queued", "cancel_hint", "upgrade", "why",
			"searching", "where", "needs", "need", "need_all", "no_builder", "builder", "builder_after", "approve", "approve_hint", "decline",
			"decline_hint", "show", "show_hint", "spot", "spot_hint", "style", "style_hint")) {
			keys.add("screen.aliveworkplace.desk." + k);
		}
		for (String key : keys) {
			helper.assertTrue(lang.has(key), "untranslated: " + key);
			helper.assertTrue(!lang.getOrDefault(key).contains("(s)"), "\"(s)\" in " + key);
		}
		helper.succeed();
	}
}
