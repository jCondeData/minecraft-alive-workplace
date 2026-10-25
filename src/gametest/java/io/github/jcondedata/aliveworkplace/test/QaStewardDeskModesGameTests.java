package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * QA for ROADMAP 27.8 (the Steward's desk), from the spec: the three mode buttons pressed on the hall's screen (and Run
 * the village refused with {@code stewardSelfRun} off), an operator and a friend answering in a protected village (the
 * build still the hall's owner's), approving twice, the Steward or the hall gone before the answer, Run the village's one
 * morning line rendered with real names, the mode kept over save and reload, and the desk's sentences rendered with real
 * numbers (singular and plural).
 */
public class QaStewardDeskModesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	private static final ResourceLocation RULE = AliveWorkplace.id("test/qa_desk_modes");
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);

	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward, Villager builder) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	/** As StewardDeskGameTests' village, with the owner given (a player that hears what it's told). */
	private static Village village(GameTestHelper helper, ServerPlayer owner) {
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
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(level, hall, owner, steward, builder);
	}

	private static Village village(GameTestHelper helper) {
		return village(helper, helper.makeMockServerPlayerInLevel());
	}

	private static StewardWishes.Wish wish(long beds) {
		return new StewardWishes.Wish(RULE, new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.of(WELL), Optional.of("homes"),
			Optional.empty(), Optional.empty()), 50, "steward.aliveworkplace.why.beds", List.of(beds));
	}

	private static StewardDesk.Proposal propose(GameTestHelper helper, Village v, long beds) {
		return propose(helper, v, beds, SPOT, Mirror.NONE);
	}

	private static StewardDesk.Proposal propose(GameTestHelper helper, Village v, long beds, BlockPos spot, Mirror mirror) {
		Plots.Verdict verdict = Plots.check(v.level(), v.hall(), v.entity().plan(), "homes", WELL, helper.absolutePos(spot),
			Rotation.COUNTERCLOCKWISE_90, mirror);
		helper.assertTrue(verdict.plot().isPresent(), "setup: the spot doesn't fit: " + verdict);
		Plots.Plot plot = verdict.plot().get();
		Optional<StewardDesk.Proposal> p = StewardDesk.offer(v.level(), v.hall(), wish(beds), plot.blueprint(), plot.placement(), plot.zone(), false);
		helper.assertTrue(p.isPresent(), "setup: not proposed");
		return p.get();
	}

	private static List<BuildSite> sites(Village v) {
		return StewardDesk.openSites(v.level(), v.hall());
	}

	private static String lore(ItemStack stack) {
		ItemStack s = stack;
		var lore = s.get(DataComponents.LORE);
		if (lore == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (Component line : lore.lines()) {
			out.append(line.getString()).append('\n');
		}
		return out.toString();
	}

	/** The three mode buttons on the desk, pressed by the owner on the hall's screen; Run the village refused with stewardSelfRun off. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskModeButtons"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskModeButtons")
	public void theModeButtonsOnTheHallScreenSwitchHisMode(GameTestHelper helper) {
		Village v = village(helper);
		boolean before = StewardDesk.SELF_RUN;
		try {
			StewardDesk.SELF_RUN = true;
			propose(helper, v, 3);
			ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
			menu.press(VillageHallScreen.ADVICE, v.owner());
			helper.assertTrue(menu.icon(StewardDeskPage.STEWARD).is(ModItems.CITY_PLAN), "the page isn't the Steward's desk");
			helper.assertTrue(StewardDesk.mode(v.level(), v.hall()) == CityPlan.Mode.ASK, "a new Steward doesn't start asking: " + StewardDesk.mode(v.level(), v.hall()));
			menu.press(StewardDeskPage.RUN, v.owner());
			helper.assertTrue(StewardDesk.mode(v.level(), v.hall()) == CityPlan.Mode.RUN, "Run the village not chosen: " + StewardDesk.mode(v.level(), v.hall()));
			helper.assertTrue(Boolean.TRUE.equals(menu.icon(StewardDeskPage.RUN).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "the chosen mode isn't marked");
			menu.press(StewardDeskPage.REST, v.owner());
			helper.assertTrue(StewardDesk.mode(v.level(), v.hall()) == CityPlan.Mode.REST, "Rest not chosen");
			helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "proposals kept while resting");
			menu.press(StewardDeskPage.ASK, v.owner());
			helper.assertTrue(StewardDesk.mode(v.level(), v.hall()) == CityPlan.Mode.ASK, "Ask me first not chosen");
			StewardDesk.SELF_RUN = false;
			menu = VillageHallScreen.forTest(v.owner(), v.hall());
			menu.press(VillageHallScreen.ADVICE, v.owner());
			String runLore = lore(menu.icon(StewardDeskPage.RUN));
			helper.assertTrue(runLore.contains(net.minecraft.locale.Language.getInstance().getOrDefault("screen.aliveworkplace.desk.mode.run.off")),
				"Run the village doesn't say it's off: " + runLore);
			menu.press(StewardDeskPage.RUN, v.owner());
			helper.assertTrue(StewardDesk.mode(v.level(), v.hall()) == CityPlan.Mode.ASK, "Run the village chosen with stewardSelfRun off");
		} finally {
			StewardDesk.SELF_RUN = before;
		}
		helper.succeed();
	}

	/** An operator and the owner's friend may answer in a protected village; the build is still the hall's owner's. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskOperator"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskOperator")
	public void anOperatorOrAFriendApprovesForTheHallsOwner(GameTestHelper helper) {
		Village v = village(helper);
		v.entity().setProtected(true);
		StewardDesk.Proposal first = propose(helper, v, 3);
		ServerPlayer op = helper.makeMockServerPlayerInLevel();
		var ops = v.level().getServer().getPlayerList().getOps();
		ops.add(new net.minecraft.server.players.ServerOpListEntry(op.getGameProfile(), 4, false));
		Leftovers.after(helper, () -> ops.remove(op.getGameProfile()));
		helper.assertTrue(op.hasPermissions(2), "setup: not an operator");
		ChoiceMenu menu = VillageHallScreen.forTest(op, v.hall());
		StewardDeskPage.renderProposal(menu, v.level(), v.hall(), first.id());
		menu.press(StewardDeskPage.APPROVE, op);
		List<BuildSite> open = sites(v);
		helper.assertTrue(open.size() == 1, "the operator's approval made " + open.size() + " sites");
		helper.assertTrue(open.get(0).owner().equals(v.owner().getUUID()), "the operator's approval isn't owned by the hall's owner: " + open.get(0).owner());
		helper.assertTrue(StewardDesk.cancel(v.level(), v.hall(), op, open.get(0).id()), "the operator couldn't cancel");
		// a friend
		StewardDesk.Proposal second = propose(helper, v, 3);
		ServerPlayer friend = helper.makeMockServerPlayerInLevel();
		Friends friends = Friends.get(v.level().getServer());
		friends.add(v.owner().getUUID(), friend.getUUID(), "qa-friend");
		Leftovers.after(helper, () -> friends.remove(v.owner().getUUID(), friend.getUUID()));
		helper.assertTrue(StewardDesk.approve(v.level(), v.hall(), friend, second.id()).ok(), "the friend couldn't approve");
		helper.assertTrue(sites(v).size() == 1 && sites(v).get(0).owner().equals(v.owner().getUUID()), "the friend's build isn't the owner's: " + sites(v));
		helper.succeed();
	}

	/** A double click: the second Approve finds the proposal gone and starts nothing more. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskTwice"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskTwice")
	public void approvingTwiceStartsOneBuild(GameTestHelper helper) {
		Village v = village(helper);
		StewardDesk.Proposal p = propose(helper, v, 3);
		helper.assertTrue(StewardDesk.approve(v.level(), v.hall(), v.owner(), p.id()).ok(), "not approved");
		StewardDesk.Outcome again = StewardDesk.approve(v.level(), v.hall(), v.owner(), p.id());
		helper.assertTrue(again == StewardDesk.Outcome.GONE, "approved twice: " + again);
		helper.assertTrue(sites(v).size() == 1, "two clicks made " + sites(v).size() + " sites");
		helper.assertTrue(!StewardDesk.decline(v.level(), v.hall(), v.owner(), p.id()), "declined a proposal already approved");
		helper.succeed();
	}

	/** The Steward dies before the answer: the page isn't his desk, Approve starts nothing, and Run the village can't run. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskNoSteward"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskNoSteward")
	public void theStewardGoneStartsNothing(GameTestHelper helper) {
		Village v = village(helper);
		StewardDesk.Proposal p = propose(helper, v, 3);
		helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN) || !StewardDesk.SELF_RUN, "setup: Run refused");
		v.steward().discard();
		helper.assertTrue(Stewards.stewardOf(v.level(), v.hall()) == null, "setup: the Steward is still found");
		StewardDesk.Outcome outcome = StewardDesk.approve(v.level(), v.hall(), v.owner(), p.id());
		helper.assertTrue(outcome == StewardDesk.Outcome.NO_STEWARD, "approved with no Steward: " + outcome);
		helper.assertTrue(sites(v).isEmpty(), "a build started with no Steward");
		ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
		menu.press(VillageHallScreen.ADVICE, v.owner());
		helper.assertTrue(!menu.icon(StewardDeskPage.STEWARD).is(ModItems.CITY_PLAN) || !menu.icon(StewardDeskPage.FIRST_PROPOSAL).is(ModItems.BLUEPRINT),
			"the desk still shows with no Steward");
		helper.succeed();
	}

	/** The hall broken before the answer: Approve starts nothing. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskNoHall"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskNoHall")
	public void theHallGoneStartsNothing(GameTestHelper helper) {
		Village v = village(helper);
		StewardDesk.Proposal p = propose(helper, v, 3);
		ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
		StewardDeskPage.renderProposal(menu, v.level(), v.hall(), p.id());
		v.level().removeBlock(v.hall(), false);
		menu.press(StewardDeskPage.APPROVE, v.owner());
		StewardDesk.Outcome outcome = StewardDesk.approve(v.level(), v.hall(), v.owner(), p.id());
		helper.assertTrue(!outcome.ok(), "approved with the hall gone: " + outcome);
		helper.assertTrue(sites(v).isEmpty(), "a build started with the hall gone: " + sites(v).size());
		helper.succeed();
	}

	/**
	 * Run the village: one chat line to the owner listing what was started, with the Steward's and village's real names,
	 * and only once a morning (planning again the same day starts and says nothing more).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaDeskRanLine"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskRanLine")
	public void runTheVillageTellsTheOwnerOnceAMorning(GameTestHelper helper) {
		List<QaStewardDeskJobsGameTests.Seen> seen = new ArrayList<>();
		ServerPlayer owner = QaStewardDeskJobsGameTests.listener(helper, "QaRanOwner", seen);
		Village v = village(helper, owner);
		boolean before = StewardDesk.SELF_RUN;
		try {
			StewardDesk.SELF_RUN = true;
			helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), owner, CityPlan.Mode.RUN), "Run the village refused");
			propose(helper, v, 3);
			seen.clear();
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(sites(v).size() == 1, "Run the village started " + sites(v).size() + " builds");
			List<QaStewardDeskJobsGameTests.Seen> ran = QaStewardDeskJobsGameTests.lines(seen, "message.aliveworkplace.steward.desk.ran", false);
			helper.assertTrue(ran.size() == 1, "morning lines: " + QaStewardDeskJobsGameTests.keys(seen));
			String text = ran.get(0).text().getString();
			helper.assertTrue(text.startsWith("This morning in ") && text.contains(v.steward().getDisplayName().getString())
				&& text.endsWith("started: Well") && !text.contains("%"), "the line reads: " + text);
			// a second proposal on another spot (mirrored: not the same Well the same way within 24), the same morning
			propose(helper, v, 3, new BlockPos(22, 2, 13), Mirror.FRONT_BACK);
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(QaStewardDeskJobsGameTests.lines(seen, "message.aliveworkplace.steward.desk.ran", false).size() == 1, "told twice in a morning");
		} finally {
			StewardDesk.SELF_RUN = before;
		}
		helper.succeed();
	}

	/** The mode and an open proposal are kept over save and reload; the reloaded proposal can still be approved. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskModeReload"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskModeReload")
	public void theModeSurvivesSaveAndReload(GameTestHelper helper) {
		Village v = village(helper);
		StewardDesk.Proposal p = propose(helper, v, 3);
		helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.REST), "Rest refused");
		helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.ASK), "Ask refused");
		p = propose(helper, v, 3);
		boolean before = StewardDesk.SELF_RUN;
		try {
			StewardDesk.SELF_RUN = true;
			helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN), "Run refused");
		} finally {
			StewardDesk.SELF_RUN = before;
		}
		CompoundTag tag = v.entity().saveWithoutMetadata(v.level().registryAccess());
		v.entity().setStewardDesk(StewardDesk.State.EMPTY);
		v.entity().setPlan(v.entity().plan().withMode(CityPlan.Mode.ASK));
		v.entity().loadWithComponents(tag, v.level().registryAccess());
		helper.assertTrue(StewardDesk.mode(v.level(), v.hall()) == CityPlan.Mode.RUN, "the mode after reload: " + StewardDesk.mode(v.level(), v.hall()));
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).get(p.id()).isPresent(), "the proposal was lost");
		helper.assertTrue(StewardDesk.approve(v.level(), v.hall(), v.owner(), p.id()).ok(), "the reloaded proposal can't be approved");
		helper.succeed();
	}

	/** The desk's sentences with real numbers: one villager and three, where it is, the decline's days, the list's "and". */
	//$ gametest_ticks_batch AREA '40' '"qaDeskWords"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskWords")
	public void theDesksSentencesReadRight(GameTestHelper helper) {
		List<QaStewardDeskJobsGameTests.Seen> seen = new ArrayList<>();
		ServerPlayer owner = QaStewardDeskJobsGameTests.listener(helper, "QaWordsOwner", seen);
		Village v = village(helper, owner);
		StewardDesk.Proposal one = propose(helper, v, 1);
		helper.assertTrue(one.reason().getString().equals("1 villager has no bed"), "one: " + one.reason().getString());
		ChoiceMenu menu = VillageHallScreen.forTest(owner, v.hall());
		menu.press(VillageHallScreen.ADVICE, owner);
		String desk = lore(menu.icon(StewardDeskPage.FIRST_PROPOSAL));
		helper.assertTrue(desk.contains("Because 1 villager has no bed"), "the why line: " + desk);
		helper.assertTrue(desk.matches("(?s).*\\d+ blocks (east|north-east|south-east), in Homes 1.*"), "the where line: " + desk);
		helper.assertTrue(desk.contains("Built by "), "the builder line: " + desk);
		helper.assertTrue(!desk.contains("%") && !desk.contains("screen.aliveworkplace") && !desk.contains("steward.aliveworkplace"), "a raw key or %: " + desk);
		seen.clear();
		menu.press(StewardDeskPage.FIRST_PROPOSAL, owner);
		menu.press(StewardDeskPage.DECLINE, owner);
		List<String> said = seen.stream().map(s -> s.text().getString()).toList();
		helper.assertTrue(said.contains("Declined: the Well won't be proposed for 3 days"), "the decline said: " + said);
		// three, after the decline's 3 days
		long time = v.level().getDayTime();
		try {
			v.level().setDayTime(time + 3 * 24000L);
			StewardDesk.Proposal three = propose(helper, v, 3);
			helper.assertTrue(three.reason().getString().equals("3 villagers have no bed"), "three: " + three.reason().getString());
			seen.clear();
			ChoiceMenu again = VillageHallScreen.forTest(owner, v.hall());
			StewardDeskPage.renderProposal(again, v.level(), v.hall(), three.id());
			again.press(StewardDeskPage.APPROVE, owner);
			said = seen.stream().map(s -> s.text().getString()).toList();
			helper.assertTrue(said.contains("Approved: the Well starts now") || said.contains("Approved: the Well is next in its builder's queue"),
				"the approval said: " + said);
		} finally {
			v.level().setDayTime(time);
		}
		helper.assertTrue(StewardDesk.list(List.of(Component.literal("Well"))).getString().equals("Well"), "one name");
		helper.assertTrue(StewardDesk.list(List.of(Component.literal("Well"), Component.literal("Inn"))).getString().equals("Well and Inn"), "two names");
		helper.assertTrue(StewardDesk.list(List.of(Component.literal("Well"), Component.literal("Inn"), Component.literal("Clinic"))).getString()
			.equals("Well, Inn and Clinic"), "three names");
		helper.succeed();
	}

	/** The desk from a Village Ledger far from the hall: the owner opens What next?, picks the proposal and approves it. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskLedger"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskLedger")
	public void theDeskWorksFromTheVillageLedger(GameTestHelper helper) {
		Village v = village(helper);
		StewardDesk.Proposal p = propose(helper, v, 3);
		ItemStack ledger = new ItemStack(ModItems.VILLAGE_LEDGER);
		v.owner().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ledger);
		io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.bind(v.level(), v.owner(), ledger, v.hall());
		BlockPos far = helper.absolutePos(new BlockPos(26, 2, 28));
		v.owner().teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
		helper.assertTrue(v.owner().position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(v.hall())) > 16, "setup: not far from the hall");
		helper.assertTrue(ledger.use(v.level(), v.owner(), net.minecraft.world.InteractionHand.MAIN_HAND).getResult().consumesAction(), "the ledger didn't open");
		helper.assertTrue(v.owner().containerMenu instanceof ChoiceMenu, "no hall screen: " + v.owner().containerMenu);
		ChoiceMenu menu = (ChoiceMenu) v.owner().containerMenu;
		menu.press(VillageHallScreen.ADVICE, v.owner());
		helper.assertTrue(menu.icon(StewardDeskPage.STEWARD).is(ModItems.CITY_PLAN), "the Ledger's page isn't the Steward's desk");
		menu.press(StewardDeskPage.FIRST_PROPOSAL, v.owner());
		menu.press(StewardDeskPage.APPROVE, v.owner());
		v.owner().closeContainer();
		helper.assertTrue(sites(v).size() == 1 && sites(v).get(0).placement().equals(p.placement()), "approving from the Ledger made " + sites(v).size() + " sites");
		helper.succeed();
	}

	/** Show me: the outline glows for 30 seconds (600 ticks), and not after. */
	//$ gametest_ticks_batch AREA '700' '"qaDeskShow"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaDeskShow")
	public void showMeGlowsForThirtySeconds(GameTestHelper helper) {
		Village v = village(helper);
		StewardDesk.Proposal p = propose(helper, v, 3);
		ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
		StewardDeskPage.renderProposal(menu, v.level(), v.hall(), p.id());
		long start = v.level().getGameTime();
		menu.press(StewardDeskPage.SHOW, v.owner());
		helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.glowing(v.owner()), "Show me didn't light the outline");
		helper.onEachTick(() -> {
			long t = v.level().getGameTime() - start;
			boolean glowing = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.glowing(v.owner());
			if (t > 0 && t < 590) {
				helper.assertTrue(glowing, "the outline went out after " + t + " ticks");
			}
			if (t > 610) {
				helper.assertTrue(!glowing, "the outline still glows after " + t + " ticks");
				helper.succeed();
			}
		});
	}
}
