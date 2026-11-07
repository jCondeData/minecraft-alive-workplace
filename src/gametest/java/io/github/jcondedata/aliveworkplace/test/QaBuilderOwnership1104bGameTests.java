package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.PieceLooks;
import io.github.jcondedata.aliveworkplace.hall.UpgradesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import io.github.jcondedata.aliveworkplace.world.VillagePieces;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane-a 2026-11-04, B880), written from the spec: builder.md "Who may order": "With workplaceBuilderOwnership on, a
 * Builder takes orders only from the player who hired it, their friends (/workplace friend add|remove|list) and
 * operators. That includes orders from the Village Hall: the Upgrades page and a house's new outside look go only to a
 * Builder the clicking player may command"; village-hall.md: "(one they hired, a friend's, or one nobody hired) ... if
 * the only Builders near belong to someone else, nothing is ordered and the bar says whose they are and that they can
 * add the player with /workplace friend add".
 * <p>
 * Not repeated from UpgradesPageGameTests / PieceLooksGameTests (B880's own): the plain refusal, a friend added, the
 * player's own idle Builder over a nearer foreign idle one on the Upgrades page, ownership off on the Upgrades page.
 * Here, all through the hall screen's own buttons: a friend removed with /workplace friend remove, an operator who is
 * nobody's friend, a friendship the wrong way round, the refused click leaving the foreign Builder's work as it was, the
 * player's own busy Builder queueing the upgrade rather than a nearer idle foreign one, a Builder nobody hired, and on
 * the House looks page: the player's own farther Builder, a refused look leaving a foreign rebuild running (with the
 * exact sentence), and ownership off.
 */
public class QaBuilderOwnership1104bGameTests implements FabricGameTest {
	// --- the Upgrades page (the layout of UpgradesPageGameTests) ---------------------------------------------------
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation HUT_2 = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut_2");
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos U_HALL = new BlockPos(15, 2, 11);
	private static final BlockPos HUT_ORIGIN = new BlockPos(3, 2, 3);
	private static final BlockPos U_BENCH = new BlockPos(13, 2, 3);
	private static final BlockPos U_CHEST = new BlockPos(13, 2, 5);
	private static final BlockPos U_BUILDER = new BlockPos(12, 2, 4);
	/** A second Builder's bench and spot, farther from the hut than the first. */
	private static final BlockPos FAR_BENCH = new BlockPos(17, 2, 3);
	private static final BlockPos FAR_BUILDER = new BlockPos(17, 2, 5);

	private record Hall(ServerLevel level, BlockPos hall, BlueprintData.Placement hut, Villager builder) {
	}

	/** The hall, a finished Test Hut, and a Builder at his bench (nobody's yet) with lanterns in his chest. */
	private static Hall hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement hut = placement(helper, HUT_ORIGIN);
		helper.setBlock(U_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(HUT_ORIGIN);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		sites.recordFinished(HUT, hut, UUID.randomUUID());
		helper.setBlock(U_BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(U_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(U_CHEST);
		chest.setItem(0, new ItemStack(Items.LANTERN, 4));
		Villager builder = helper.spawn(EntityType.VILLAGER, U_BUILDER);
		Builders.employ(level, builder, helper.absolutePos(U_BENCH));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			sites.forgetFinished(hut);
			clearSites(helper);
		});
		return new Hall(level, helper.absolutePos(U_HALL), hut, builder);
	}

	/** Removes every site whose placement is inside this test's area (the hut's, and the pillars the tests start). */
	private static void clearSites(GameTestHelper helper) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		var box = helper.getBounds();
		for (BuildSite site : List.copyOf(sites.all())) {
			BlockPos o = site.placement().origin();
			if (box.contains(o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5)) {
				sites.remove(site.id());
			}
		}
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	/** The rule on or off for this test, put back afterwards. */
	private static void ownership(GameTestHelper helper, boolean on) {
		ServerLevel level = helper.getLevel();
		boolean before = level.getGameRules().getBoolean(ModGameRules.BUILDER_OWNERSHIP);
		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(on, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(before, level.getServer()));
	}

	/** A survival player who is no operator (else the check would prove nothing). */
	private static QaStageFiveGameTests.Client survivor(GameTestHelper helper, String name) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name));
		helper.assertFalse(c.player().hasPermissions(2), "setup: " + c.player().getGameProfile().getName() + " is an operator");
		return c;
	}

	private static String name(ServerPlayer p) {
		return p.getGameProfile().getName();
	}

	/** {@code who} clicks the hut's row on the hall's Upgrades page, through the hall's own screen. */
	private static void clickHut(GameTestHelper helper, Hall h, ServerPlayer who) {
		ChoiceMenu menu = VillageHallScreen.forTest(who, h.hall());
		int tab = HallPages.slot(UpgradesPage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW && menu.icon(tab).is(Items.BRICKS), "the Upgrades tab: " + tab + " " + menu.icon(tab));
		menu.press(tab, who);
		helper.assertTrue(menu.icon(4).is(Items.BRICKS), "not the Upgrades page: " + menu.icon(4));
		helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.BRICKS), "the hut's row: " + menu.icon(VillageHallScreen.FIRST_ROW));
		menu.press(VillageHallScreen.FIRST_ROW, who);
	}

	/** The open site on the hut (its level II), if any. */
	private static Optional<BuildSite> upgradeSite(Hall h) {
		return BuildSiteManager.get(h.level()).all().stream().filter(s -> s.placement().equals(h.hut()) && !s.isDone()).findFirst();
	}

	private static List<String> said(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** The refusal the spec asks for, with the employer and the clicking player filled in. */
	private static String notYours(String employer, String clicker) {
		return Component.translatable("message.aliveworkplace.upgrades.not_yours", Blueprints.displayName(HUT_2), employer, clicker).getString();
	}

	/**
	 * "/workplace friend add|remove": the hirer adds the hall's owner with /workplace friend add, then takes him off again
	 * with /workplace friend remove. The owner's click on the hut's row is refused again: no site, the Builder idle, and
	 * the bar names the hirer and the friend-add fix.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaOwn1104bFriendRemoved"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaOwn1104bFriendRemoved")
	public void aFriendRemovedByCommandIsRefusedAgainAtTheHall(GameTestHelper helper) {
		Hall h = hall(helper);
		ownership(helper, true);
		QaStageFiveGameTests.Client leader = survivor(helper, "qo4lead");
		QaStageFiveGameTests.Client hirer = survivor(helper, "qo4hire");
		QaStageSixGameTests.profileCache(helper, leader.player(), hirer.player());
		Friends friends = Friends.get(h.level().getServer());
		Leftovers.after(helper, () -> friends.remove(hirer.player().getUUID(), leader.player().getUUID()));
		helper.runAfterDelay(2, () -> {
			Friends.hire(hirer.player(), h.builder());
			((VillageHallBlockEntity) h.level().getBlockEntity(h.hall())).setOwner(leader.player().getUUID(), name(leader.player()));
			QaStageSixGameTests.command(hirer, "/workplace friend add " + name(leader.player()));
			helper.assertTrue(QaStageFourGameTests.saw(hirer.seen(), "command.aliveworkplace.friend.added"), "friend add: " + said(hirer.seen()));
			helper.assertTrue(friends.mayDirect(hirer.player().getUUID(), leader.player().getUUID()), "the add didn't take");
			QaStageSixGameTests.command(hirer, "/workplace friend remove " + name(leader.player()));
			helper.assertTrue(QaStageFourGameTests.saw(hirer.seen(), "command.aliveworkplace.friend.removed"), "friend remove: " + said(hirer.seen()));
			leader.seen().clear();
			clickHut(helper, h, leader.player());
			helper.assertTrue(upgradeSite(h).isEmpty(), "a removed friend's upgrade went to the hirer's Builder: " + upgradeSite(h).map(BuildSite::ownerName));
			helper.assertTrue(Builders.activeSite(h.level(), h.builder()) == null && Builders.queue(h.level(), h.builder()).isEmpty(), "the Builder got work");
			String expected = notYours(name(hirer.player()), name(leader.player()));
			helper.assertTrue(said(leader.seen()).contains(expected), "not told (" + expected + "): " + said(leader.seen()));
			helper.succeed();
		});
	}

	/**
	 * "and operators": an operator who neither owns the hall, nor hired the Builder, nor is anyone's friend clicks the hut's
	 * row. The hirer's Builder takes the upgrade.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaOwn1104bOperator"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaOwn1104bOperator")
	public void anOperatorWhoIsNobodysFriendSendsAHiredBuilderFromTheHall(GameTestHelper helper) {
		Hall h = hall(helper);
		ownership(helper, true);
		QaStageFiveGameTests.Client leader = survivor(helper, "qo4owner");
		QaStageFiveGameTests.Client hirer = survivor(helper, "qo4hirer");
		QaStageFiveGameTests.Client op = survivor(helper, "qo4op");
		var players = h.level().getServer().getPlayerList();
		players.getOps().add(new net.minecraft.server.players.ServerOpListEntry(op.player().getGameProfile(), 4, false));
		Leftovers.after(helper, () -> players.deop(op.player().getGameProfile()));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(op.player().hasPermissions(2), "setup: the operator isn't one");
			Friends.hire(hirer.player(), h.builder());
			((VillageHallBlockEntity) h.level().getBlockEntity(h.hall())).setOwner(leader.player().getUUID(), name(leader.player()));
			Friends friends = Friends.get(h.level().getServer());
			helper.assertFalse(friends.mayDirect(hirer.player().getUUID(), op.player().getUUID())
				|| friends.mayDirect(leader.player().getUUID(), op.player().getUUID()), "setup: the operator is someone's friend");
			clickHut(helper, h, op.player());
			BuildSite site = upgradeSite(h).orElse(null);
			helper.assertTrue(site != null && site.structure().equals(HUT_2) && h.builder().getUUID().equals(site.builder()),
				"the operator's order wasn't taken: " + site + "; said " + said(op.seen()));
			helper.assertFalse(QaStageFourGameTests.saw(op.seen(), "message.aliveworkplace.upgrades.not_yours"), "the operator was refused: " + said(op.seen()));
			helper.succeed();
		});
	}

	/**
	 * "their friends" are the friends the HIRER names. The hall's owner adding the hirer as his own friend gives the owner
	 * nothing over the hirer's Builder: the click is refused, nothing is ordered.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaOwn1104bWrongWayRound"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaOwn1104bWrongWayRound")
	public void aFriendshipTheWrongWayRoundDoesNotLetTheHallOwnerOrderTheHirersBuilder(GameTestHelper helper) {
		Hall h = hall(helper);
		ownership(helper, true);
		QaStageFiveGameTests.Client leader = survivor(helper, "qo4wlead");
		QaStageFiveGameTests.Client hirer = survivor(helper, "qo4whire");
		Friends friends = Friends.get(h.level().getServer());
		Leftovers.after(helper, () -> friends.remove(leader.player().getUUID(), hirer.player().getUUID()));
		helper.runAfterDelay(2, () -> {
			Friends.hire(hirer.player(), h.builder());
			((VillageHallBlockEntity) h.level().getBlockEntity(h.hall())).setOwner(leader.player().getUUID(), name(leader.player()));
			friends.add(leader.player().getUUID(), hirer.player().getUUID(), name(hirer.player()));
			leader.seen().clear();
			clickHut(helper, h, leader.player());
			helper.assertTrue(upgradeSite(h).isEmpty(), "the owner's friend's Builder took the owner's order: " + upgradeSite(h).map(BuildSite::builder));
			String expected = notYours(name(hirer.player()), name(leader.player()));
			helper.assertTrue(said(leader.seen()).contains(expected), "not told (" + expected + "): " + said(leader.seen()));
			helper.succeed();
		});
	}

	/**
	 * The refused click leaves the other player's Builder exactly as it was: his own build (a pillar) still his active
	 * site, his one queued build still the only one in his queue, and no new site anywhere.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaOwn1104bRefusedLeavesWork"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaOwn1104bRefusedLeavesWork")
	public void aRefusedOrderLeavesTheOtherPlayersBusyBuilderAsItWas(GameTestHelper helper) {
		Hall h = hall(helper);
		ownership(helper, true);
		QaStageFiveGameTests.Client leader = survivor(helper, "qo4blead");
		QaStageFiveGameTests.Client hirer = survivor(helper, "qo4bhire");
		helper.runAfterDelay(2, () -> {
			Friends.hire(hirer.player(), h.builder());
			((VillageHallBlockEntity) h.level().getBlockEntity(h.hall())).setOwner(leader.player().getUUID(), name(leader.player()));
			BuildSite own = Builders.start(h.level(), h.builder(), hirer.player(), PILLAR, placement(helper, new BlockPos(17, 2, 17)));
			BuildSite queued = Builders.enqueue(h.level(), h.builder(), hirer.player(), PILLAR, placement(helper, new BlockPos(19, 2, 17)));
			int before = BuildSiteManager.get(h.level()).all().size();
			leader.seen().clear();
			clickHut(helper, h, leader.player());
			helper.assertTrue(upgradeSite(h).isEmpty(), "the busy foreign Builder was given the upgrade: " + upgradeSite(h).map(BuildSite::builder));
			BuildSite active = Builders.activeSite(h.level(), h.builder());
			helper.assertTrue(active != null && active.id().equals(own.id()), "his own build isn't his active site any more: " + active);
			List<BuildSite> queue = Builders.queue(h.level(), h.builder());
			helper.assertTrue(queue.size() == 1 && queue.get(0).id().equals(queued.id()), "his queue changed: " + queue.stream().map(BuildSite::structure).toList());
			helper.assertTrue(BuildSiteManager.get(h.level()).all().size() == before, "a site was made: " + before + " -> " + BuildSiteManager.get(h.level()).all().size());
			helper.assertTrue(QaStageFourGameTests.saw(leader.seen(), "message.aliveworkplace.upgrades.not_yours"), "not refused as not his: " + said(leader.seen()));
			helper.succeed();
		});
	}

	/**
	 * The player's own Builder, farther from the hut and busy with his own pillar, gets the upgrade in his queue; the
	 * other player's Builder, nearer and idle, is never sent.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaOwn1104bOwnBusyQueues"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaOwn1104bOwnBusyQueues")
	public void theOwnBusyBuilderQueuesTheUpgradeRatherThanANearerIdleForeignOne(GameTestHelper helper) {
		Hall h = hall(helper);
		ownership(helper, true);
		QaStageFiveGameTests.Client leader = survivor(helper, "qo4qlead");
		QaStageFiveGameTests.Client hirer = survivor(helper, "qo4qhire");
		helper.setBlock(FAR_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager mine = helper.spawn(EntityType.VILLAGER, FAR_BUILDER);
		Builders.employ(h.level(), mine, helper.absolutePos(FAR_BENCH));
		helper.runAfterDelay(2, () -> {
			Friends.hire(hirer.player(), h.builder());
			Friends.hire(leader.player(), mine);
			((VillageHallBlockEntity) h.level().getBlockEntity(h.hall())).setOwner(leader.player().getUUID(), name(leader.player()));
			BuildSite busy = Builders.start(h.level(), mine, leader.player(), PILLAR, placement(helper, new BlockPos(17, 2, 17)));
			BlockPos centre = h.hut().origin().offset(2, 0, 2);
			helper.assertTrue(h.builder().distanceToSqr(centre.getX(), centre.getY(), centre.getZ()) < mine.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()),
				"setup: the foreign Builder isn't the nearer one");
			leader.seen().clear();
			clickHut(helper, h, leader.player());
			BuildSite site = upgradeSite(h).orElse(null);
			helper.assertTrue(site != null && mine.getUUID().equals(site.builder()), "not sent to the player's own Builder: "
				+ (site == null ? null : site.builder()) + "; said " + said(leader.seen()));
			helper.assertTrue(site.isQueued() && Builders.activeSite(h.level(), mine).id().equals(busy.id()), "not queued behind his pillar");
			helper.assertTrue(Builders.activeSite(h.level(), h.builder()) == null && Builders.queue(h.level(), h.builder()).isEmpty(),
				"the other player's Builder got work");
			helper.assertTrue(QaStageFourGameTests.saw(leader.seen(), "message.aliveworkplace.upgrades.queued"), "not told it's queued: " + said(leader.seen()));
			helper.succeed();
		});
	}

	/** "or one nobody hired": with ownership on, a Builder no player hired takes the hall owner's upgrade. */
	//$ gametest_ticks_batch BIG '100' '"qaOwn1104bUnhired"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaOwn1104bUnhired")
	public void aBuilderNobodyHiredTakesTheHallOwnersUpgrade(GameTestHelper helper) {
		Hall h = hall(helper);
		ownership(helper, true);
		QaStageFiveGameTests.Client leader = survivor(helper, "qo4ulead");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(h.builder()) == null, "setup: the Builder is hired");
			((VillageHallBlockEntity) h.level().getBlockEntity(h.hall())).setOwner(leader.player().getUUID(), name(leader.player()));
			clickHut(helper, h, leader.player());
			BuildSite site = upgradeSite(h).orElse(null);
			helper.assertTrue(site != null && site.structure().equals(HUT_2) && h.builder().getUUID().equals(site.builder()),
				"an unhired Builder didn't take the order: " + site + "; said " + said(leader.seen()));
			helper.succeed();
		});
	}

	// --- the House looks page (the layout of PieceLooksGameTests) --------------------------------------------------
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos L_HALL = new BlockPos(2, 2, 26);
	private static final BlockPos L_BENCH = new BlockPos(4, 2, 6);
	private static final BlockPos L_BUILDER = new BlockPos(5, 2, 8);
	private static final BlockPos HOUSE = new BlockPos(13, 2, 10);
	/** A second Builder right by the house (its middle is at about 17, 2, 15). */
	private static final BlockPos NEAR_BENCH = new BlockPos(25, 2, 18);
	private static final BlockPos NEAR_BUILDER = new BlockPos(24, 2, 16);

	private record Town(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, BlueprintData.Placement placement, Villager builder) {
	}

	/** A plains Guard House as the village generator places it, a hall, and a builder at his bench (nobody's yet). */
	private static Town town(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		StructureTemplate template = level.getStructureManager().get(VillagePieces.template("plains", "guard_house")).orElseThrow();
		template.placeInWorld(level, placement.origin(), placement.origin(), new StructurePlaceSettings().setIgnoreEntities(true)
			.addProcessor(JigsawReplacementProcessor.INSTANCE), RandomSource.create(7), 2);
		helper.setBlock(L_HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(L_HALL));
		helper.setBlock(L_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, L_BUILDER);
		Builders.employ(level, builder, helper.absolutePos(L_BENCH));
		Leftovers.after(helper, () -> clearSites(helper));
		return new Town(level, helper.absolutePos(L_HALL), entity, placement, builder);
	}

	private static VillagePieces.Piece house(GameTestHelper helper, Town t) {
		return PieceLooks.pieces(t.level(), t.hall()).stream().filter(p -> p.placement().origin().equals(t.placement().origin())).findFirst()
			.orElseThrow(() -> new GameTestAssertException("the house isn't found"));
	}

	private static String itemName(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** {@code who} opens the hall's House looks page (its Builds button), the Guard House on it, and clicks {@code look}. */
	private static void chooseLook(GameTestHelper helper, Town t, ServerPlayer who, String look) {
		ChoiceMenu menu = VillageHallScreen.forTest(who, t.hall());
		menu.press(VillageHallScreen.BUILDS, who);
		helper.assertTrue(menu.icon(4).is(Items.OAK_DOOR), "not the House looks page: " + menu.icon(4));
		BlockPos o = t.placement().origin();
		String at = "At " + o.getX() + ", " + o.getY() + ", " + o.getZ();
		int slot = -1;
		for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE && slot < 0; i++) {
			if (itemName(menu.icon(i)).equals("Guard House") && lore(menu.icon(i)).contains(at)) {
				slot = i;
			}
		}
		helper.assertTrue(slot >= 0, "the page doesn't list the Guard House " + at);
		menu.press(slot, who);
		int button = PieceLooks.FIRST_LOOK + VillageHouses.STYLES.indexOf(look);
		helper.assertTrue(itemName(menu.icon(button)).equals(VillagePieces.styleName(look).getString() + " outside"), "the look's button: " + itemName(menu.icon(button)));
		menu.press(button, who);
	}

	/**
	 * House looks: the leader's own builder, farther from the house, rebuilds it; another player's builder standing right
	 * by the house (benched there) is not sent.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaOwn1104bLookOwnFarther"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaOwn1104bLookOwnFarther")
	public void aLookGoesToThePlayersOwnFartherBuilderNotANearerForeignOne(GameTestHelper helper) {
		Town t = town(helper);
		ownership(helper, true);
		QaStageFiveGameTests.Client leader = survivor(helper, "qo4llead");
		QaStageFiveGameTests.Client hirer = survivor(helper, "qo4lhire");
		helper.setBlock(NEAR_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager foreign = helper.spawn(EntityType.VILLAGER, NEAR_BUILDER);
		Builders.employ(t.level(), foreign, helper.absolutePos(NEAR_BENCH));
		Friends.hire(leader.player(), t.builder());
		Friends.hire(hirer.player(), foreign);
		t.entity().setOwner(leader.player().getUUID(), name(leader.player()));
		VillagePieces.Piece piece = house(helper, t);
		BlockPos centre = t.placement().origin().offset(4, 0, 5);
		helper.assertTrue(foreign.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()) < t.builder().distanceToSqr(centre.getX(), centre.getY(), centre.getZ()),
			"setup: the foreign builder isn't the nearer one");
		chooseLook(helper, t, leader.player(), "desert");
		BuildSite site = PieceLooks.rebuild(t.level(), piece);
		helper.assertTrue(site != null && t.builder().getUUID().equals(site.builder()), "not the leader's own builder: "
			+ (site == null ? null : site.builder()) + "; said " + said(leader.seen()));
		helper.assertTrue(Builders.activeSite(t.level(), foreign) == null && Builders.queue(t.level(), foreign).isEmpty(), "the other player's builder got work");
		helper.succeed();
	}

	/**
	 * House looks: another player's builder is rebuilding the house as desert (the only builder there). The leader picks the
	 * snowy outside: refused, the desert rebuild is not called off or touched, the hall doesn't keep "snowy", and the bar
	 * reads the spec's sentence with the hirer's and the leader's names.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaOwn1104bLookRefusedKeepsRebuild"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaOwn1104bLookRefusedKeepsRebuild")
	public void aRefusedLookLeavesAnotherPlayersRebuildRunning(GameTestHelper helper) {
		Town t = town(helper);
		ownership(helper, true);
		QaStageFiveGameTests.Client leader = survivor(helper, "qo4rlead");
		QaStageFiveGameTests.Client hirer = survivor(helper, "qo4rhire");
		Friends.hire(hirer.player(), t.builder());
		t.entity().setOwner(leader.player().getUUID(), name(leader.player()));
		VillagePieces.Piece piece = house(helper, t);
		BuildSite desert = Builders.start(t.level(), t.builder(), hirer.player(), VillagePieces.outsideId("desert", piece.house()), piece.placement());
		List<PieceLooks.Choice> kept = List.copyOf(t.entity().pieceLooks());
		leader.seen().clear();
		chooseLook(helper, t, leader.player(), "snowy");
		BuildSite now = PieceLooks.rebuild(t.level(), piece);
		helper.assertTrue(now != null && now.id().equals(desert.id()) && now.structure().equals(VillagePieces.outsideId("desert", piece.house())),
			"the other player's desert rebuild was changed or called off: " + (now == null ? null : now.structure()));
		BuildSite active = Builders.activeSite(t.level(), t.builder());
		helper.assertTrue(active != null && active.id().equals(desert.id()), "the builder's job changed: " + active);
		helper.assertTrue(t.entity().pieceLooks().equals(kept), "the hall kept the refused look: " + t.entity().pieceLooks());
		String expected = "No builder of yours can rebuild the Guard House: the village's builders work for " + name(hirer.player())
			+ ". Hire one of your own, or ask them to run /workplace friend add " + name(leader.player()) + ".";
		helper.assertTrue(said(leader.seen()).contains(expected), "not told (" + expected + "): " + said(leader.seen()));
		helper.succeed();
	}

	/** House looks with ownership off: another player's builder (the only one) takes the leader's pick. */
	//$ gametest_ticks_batch HUGE '200' '"qaOwn1104bLookOwnershipOff"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaOwn1104bLookOwnershipOff")
	public void withOwnershipOffAnotherPlayersBuilderTakesALook(GameTestHelper helper) {
		Town t = town(helper);
		ownership(helper, false);
		QaStageFiveGameTests.Client leader = survivor(helper, "qo4olead");
		QaStageFiveGameTests.Client hirer = survivor(helper, "qo4ohire");
		Friends.hire(hirer.player(), t.builder());
		t.entity().setOwner(leader.player().getUUID(), name(leader.player()));
		VillagePieces.Piece piece = house(helper, t);
		chooseLook(helper, t, leader.player(), "desert");
		BuildSite site = PieceLooks.rebuild(t.level(), piece);
		helper.assertTrue(site != null && t.builder().getUUID().equals(site.builder()), "ownership off, yet the builder wasn't sent: "
			+ site + "; said " + said(leader.seen()));
		helper.succeed();
	}
}
