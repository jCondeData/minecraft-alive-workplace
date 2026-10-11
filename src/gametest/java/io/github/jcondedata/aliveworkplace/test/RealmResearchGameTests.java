package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmResearch;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.research.ScholarWork;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Shared research (ROADMAP 33.15): a member's guards hit harder with the capital's Drill and its rank doesn't change; an
 * idle member scholar adds points to the capital's topic at half pace; a realm topic from the test datapack
 * ({@code aliveworkplace_test:test_census}) is chosen on the capital's Realm tab, waits while the treasury is short, is
 * paid from the realm treasury, finishes, gives every member its effect and survives a reload and a save; with realms off
 * nothing is shared and the tab is gone. Each test is alone in its batch (realms are on only inside
 * {@link RealmGameTests#land}).
 */
public class RealmResearchGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String TEST_TOPIC = "aliveworkplace_test:test_census";

	/** A Town capital (hall 0) and a Hamlet member (hall 1) in one realm, joined through the tab. */
	static RealmGameTests.Land realm(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		Research.forget();
		RealmResearch.forget();
		Leftovers.after(helper, () -> {
			Research.forget();
			RealmResearch.forget();
		});
		return land;
	}

	static List<String> lore(ItemStack stack) {
		return RealmGameTests.lore(stack);
	}

	/** A scholar at a desk two blocks from hall {@code i}, with a chest beside the desk. */
	record Desk(Villager scholar, BlockPos desk, Container chest) {
	}

	static Desk desk(GameTestHelper helper, RealmGameTests.Land land, int i) {
		BlockPos desk = land.hall(i).offset(0, 0, 2);
		BlockPos chest = land.hall(i).offset(1, 0, 2);
		land.level().setBlockAndUpdate(desk, ModBlocks.SCHOLARS_DESK.defaultBlockState());
		land.level().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		Villager scholar = villager(land, land.hall(i).offset(-1, 0, 1));
		Jobs.employ(land.level(), scholar, desk, ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		return new Desk(scholar, desk, (Container) land.level().getBlockEntity(chest));
	}

	/** A villager at {@code at} (world coordinates: next to a hall of the land). */
	static Villager villager(RealmGameTests.Land land, BlockPos at) {
		Villager villager = EntityType.VILLAGER.create(land.level());
		villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		land.level().addFreshEntity(villager);
		return villager;
	}

	/** The capital knows Drill III; the member's guard hits 30% harder, its own levels and rank don't change; the screen says "from the capital". */
	//$ gametest_ticks_batch AREA '100' '"realmResearchDrill"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmResearchDrill")
	public void membersGuardsUseTheCapitalsDrill(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper);
		Villager guard = villager(land, land.hall(1).offset(1, 0, 2));
		guard.setVillagerData(guard.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(1));
		float before = Guards.levelBonus(guard);
		VillageRanks.Score scoreBefore = VillageRanks.score(land.level(), land.hall(1), 5);
		land.entity(0).setResearch(new Research.State(Map.of("drill", 3), Optional.empty(), 0, false));
		Research.forget();
		float after = Guards.levelBonus(guard);
		helper.assertTrue(Math.abs(after - before * 1.3f) < 0.001f, "the capital's Drill III didn't reach the member's guard: " + before + " -> " + after);
		helper.assertTrue(Research.at(land.level(), land.hall(1)).level(Research.Topic.DRILL) == 3, "Research.at doesn't share: " + Research.at(land.level(), land.hall(1)));
		helper.assertTrue(land.entity(1).research().level(Research.Topic.DRILL) == 0, "the member's own levels changed: " + land.entity(1).research());
		VillageRanks.Score scoreAfter = VillageRanks.score(land.level(), land.hall(1), 5);
		helper.assertTrue(scoreAfter.research() == scoreBefore.research() && VillageRanks.rank(scoreAfter) == VillageRanks.rank(scoreBefore),
			"shared levels counted toward the rank: " + scoreBefore + " -> " + scoreAfter);
		// the member's research screen shows Drill III, from the capital, and won't take it up again
		ChoiceMenu menu = ResearchScreen.forTest(land.owner(), land.hall(1));
		ItemStack drill = menu.icon(ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()]);
		helper.assertTrue(lore(drill).contains("From the capital: Drill " + BuilderLevels.levelName(3).getString()) && lore(drill).contains("Fully researched"),
			"the member's Drill icon: " + drill + " " + lore(drill));
		menu.press(ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()], land.owner());
		helper.assertTrue(land.entity(1).research().currentTopic() == null, "Drill taken up at home though the capital knows it all");
		// the capital's own screen says nothing of the kind
		ItemStack capitalDrill = ResearchScreen.forTest(land.owner(), land.hall(0)).icon(ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()]);
		helper.assertTrue(lore(capitalDrill).stream().noneMatch(l -> l.startsWith("From the capital")), "the capital's own Drill: " + lore(capitalDrill));
		// the capital's round keeps its levels for when it isn't loaded
		Realms.round(land.level(), land.hall(0), land.entity(0), 5);
		RealmData.Realm realm = land.data().realmOf(land.at(0));
		helper.assertTrue(land.data().study(realm.id()).capital().equals(Map.of("drill", 3)), "the capital's levels weren't kept: " + land.data().study(realm.id()));
		// with realms off nothing is shared
		Realms.ENABLED = false;
		Research.forget();
		helper.assertTrue(Math.abs(Guards.levelBonus(guard) - before) < 0.001f, "shared with realms off: " + Guards.levelBonus(guard));
		helper.assertTrue(lore(ResearchScreen.forTest(land.owner(), land.hall(1)).icon(ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()]))
			.stream().noneMatch(l -> l.startsWith("From the capital")), "the screen says 'from the capital' with realms off");
		Realms.ENABLED = true;
		helper.succeed();
	}

	/** A member scholar with nothing chosen adds half their points to the capital's paid topic; with a topic of their own, they don't. */
	//$ gametest_ticks_batch AREA '100' '"realmResearchHelp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmResearchHelp")
	public void idleMemberScholarHelpsTheCapital(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper);
		Desk member = desk(helper, land, 1);
		land.entity(0).setResearch(Research.State.EMPTY.choose(Research.Topic.SWIFT_HANDS).paidFor());
		int full = ScholarWork.progress(member.scholar());
		new ScholarWork().work(land.level(), member.scholar(), member.desk(), true);
		int added = land.entity(0).research().progress();
		helper.assertTrue(added == Math.max(1, full / RealmResearch.HELP_SHARE) && added < full, "not half pace: " + added + " of " + full);
		helper.assertTrue(land.entity(1).research().currentTopic() == null, "the member's own research changed: " + land.entity(1).research());
		new ScholarWork().work(land.level(), member.scholar(), member.desk(), true);
		helper.assertTrue(land.entity(0).research().progress() == 2 * added, "a second round: " + land.entity(0).research().progress());
		// an unpaid capital topic gets no help
		land.entity(0).setResearch(Research.State.EMPTY.choose(Research.Topic.HEARTH));
		new ScholarWork().work(land.level(), member.scholar(), member.desk(), true);
		helper.assertTrue(land.entity(0).research().progress() == 0, "helped an unpaid topic: " + land.entity(0).research());
		// with a topic of their own they work at home
		land.entity(0).setResearch(Research.State.EMPTY.choose(Research.Topic.SWIFT_HANDS).paidFor());
		land.entity(1).setResearch(Research.State.EMPTY.choose(Research.Topic.HEARTH));
		new ScholarWork().work(land.level(), member.scholar(), member.desk(), true);
		helper.assertTrue(land.entity(0).research().progress() == 0, "helped the capital with work of their own: " + land.entity(0).research());
		// out of the realm, no help
		land.entity(1).setResearch(Research.State.EMPTY);
		Realms.leave(land.level(), land.hall(1), land.owner());
		new ScholarWork().work(land.level(), member.scholar(), member.desk(), true);
		helper.assertTrue(land.entity(0).research().progress() == 0, "helped after leaving the realm");
		helper.succeed();
	}

	/**
	 * The test datapack's realm topic on the capital's Realm tab: a stranger can't choose it, the owner can; the capital's
	 * scholar waits while the treasury is short, then pays the paper and book from the chest and 3 emeralds from the
	 * treasury, finishes it, and every member gets 2 more market traders; it survives a reload of the topics and a save of
	 * the realm file; with realms off the tab and the effect are gone.
	 */
	//$ gametest_ticks_batch AREA '100' '"realmResearchTopic"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmResearchTopic")
	public void aRealmTopicIsPaidFromTheTreasuryAndFinishes(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper);
		RealmResearch.Topic topic = RealmResearch.get(TEST_TOPIC).orElse(null);
		helper.assertTrue(topic != null, "the test topic isn't loaded: " + RealmResearch.all().stream().map(RealmResearch.Topic::key).toList());
		helper.assertTrue(RealmResearch.get("royal_roads").isPresent() && RealmResearch.get("granaries").isPresent(), "the mod's realm topics aren't loaded");
		int slot = ResearchScreen.TOPIC_SLOTS[new ArrayList<>(RealmResearch.all()).indexOf(topic)];
		// a member's screen has no Realm tab
		helper.assertTrue(ResearchScreen.forTest(land.owner(), land.hall(1)).icon(ResearchScreen.REALM_TAB).isEmpty(), "a member has the Realm tab");
		// a stranger opens the capital's tab but can't choose
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "stranger");
		ResearchScreen.View strangerView = new ResearchScreen.View();
		ChoiceMenu strangers = ResearchScreen.forTest(stranger, land.hall(0), strangerView);
		strangers.press(ResearchScreen.REALM_TAB, stranger);
		strangers.press(slot, stranger);
		RealmData.Realm realm = land.data().realmOf(land.at(0));
		helper.assertTrue(RealmResearch.state(land.level().getServer(), realm).current().isEmpty(), "a stranger chose the realm's research");
		// the owner chooses it
		ResearchScreen.View view = new ResearchScreen.View();
		ChoiceMenu menu = ResearchScreen.forTest(land.owner(), land.hall(0), view);
		ItemStack tab = menu.icon(ResearchScreen.REALM_TAB);
		helper.assertTrue(tab.is(Items.GOLDEN_HELMET) && tab.getHoverName().getString().equals("The realm's research"), "no Realm tab: " + tab);
		menu.press(ResearchScreen.REALM_TAB, land.owner());
		helper.assertTrue(view.realm(), "the Realm tab didn't open");
		ItemStack icon = menu.icon(slot);
		helper.assertTrue(icon.is(Items.AMETHYST_SHARD) && icon.getHoverName().getString().equals("Test Census")
			&& lore(icon).contains("Next level: 2× Paper, 1× Book, and 3 emeralds from the realm treasury"), "the topic's icon: " + icon + " " + lore(icon));
		menu.press(slot, land.owner());
		helper.assertTrue(RealmResearch.current(RealmResearch.state(land.level().getServer(), realm)) == topic, "not chosen");
		// the capital's scholar: the treasury is empty, so nothing is paid
		Desk capital = desk(helper, land, 0);
		capital.chest().setItem(0, new ItemStack(Items.PAPER, 2));
		capital.chest().setItem(1, new ItemStack(Items.BOOK, 1));
		new ScholarWork().work(land.level(), capital.scholar(), capital.desk(), true);
		helper.assertTrue(!RealmResearch.state(land.level().getServer(), realm).paid() && capital.chest().countItem(Items.PAPER) == 2,
			"paid with an empty treasury: " + RealmResearch.state(land.level().getServer(), realm));
		// missing paper: still not paid, even with the emeralds
		land.data().putRealm(land.data().realmOf(land.at(0)).withTreasury(1000, List.of()));
		capital.chest().setItem(0, ItemStack.EMPTY);
		new ScholarWork().work(land.level(), capital.scholar(), capital.desk(), true);
		helper.assertTrue(!RealmResearch.state(land.level().getServer(), realm).paid() && land.data().realmOf(land.at(0)).treasury() == 1000,
			"paid without the paper");
		capital.chest().setItem(0, new ItemStack(Items.PAPER, 2));
		new ScholarWork().work(land.level(), capital.scholar(), capital.desk(), true);
		RealmData.Realm paid = land.data().realmOf(land.at(0));
		helper.assertTrue(RealmResearch.state(land.level().getServer(), realm).paid(), "not paid: " + RealmResearch.state(land.level().getServer(), realm));
		helper.assertTrue(paid.treasury() == 700 && capital.chest().countItem(Items.PAPER) == 0 && capital.chest().countItem(Items.BOOK) == 0,
			"the price: treasury " + paid.treasury() + ", paper " + capital.chest().countItem(Items.PAPER) + ", books " + capital.chest().countItem(Items.BOOK));
		helper.assertTrue(!paid.ledger().isEmpty() && paid.ledger().get(paid.ledger().size() - 1).reason().getString().equals("Realm research: Test Census")
			&& paid.ledger().get(paid.ledger().size() - 1).cents() == -300, "no movement for it: " + paid.ledger());
		for (int i = 0; i < 10 && RealmResearch.level(RealmResearch.state(land.level().getServer(), realm), topic) == 0; i++) {
			new ScholarWork().work(land.level(), capital.scholar(), capital.desk(), true);
		}
		Research.State done = RealmResearch.state(land.level().getServer(), realm);
		helper.assertTrue(RealmResearch.level(done, topic) == 1 && done.current().isEmpty(), "not finished: " + done);
		helper.assertTrue(RealmGameTests.heard(land, "the Barony of " + land.name(0) + ": the realm's scholars finished Test Census " + BuilderLevels.levelName(1).getString() + ". Market days get 2 more traders a level"),
			"no chat line: " + land.heard().stream().map(c -> c.getString()).toList());
		// every member gets it
		helper.assertTrue(CivicEffects.of(land.level(), land.hall(1)).marketTraders() == 2 && CivicEffects.of(land.level(), land.hall(0)).marketTraders() == 2,
			"the effect: member " + CivicEffects.of(land.level(), land.hall(1)).marketTraders() + ", capital " + CivicEffects.of(land.level(), land.hall(0)).marketTraders());
		helper.assertTrue(land.entity(0).research().totalLevels() == 0, "the realm's level counted for the capital's rank");
		// a reload of the topics keeps it
		RealmResearch.load(land.level().getServer().getResourceManager().listResources(RealmResearch.FOLDER, p -> p.getPath().endsWith(".json")));
		RealmResearch.Topic reloaded = RealmResearch.get(TEST_TOPIC).orElse(null);
		helper.assertTrue(reloaded != null && RealmResearch.level(RealmResearch.state(land.level().getServer(), realm), reloaded) == 1,
			"lost on reload: " + RealmResearch.all());
		helper.assertTrue(CivicEffects.of(land.level(), land.hall(1)).marketTraders() == 2, "the effect lost on reload");
		// a save and load of the realm file keeps it
		CompoundTag tag = land.data().save(new CompoundTag(), land.level().registryAccess());
		RealmData back = RealmData.load(tag, land.level().registryAccess());
		helper.assertTrue(back.study(realm.id()).research().levels().getOrDefault(TEST_TOPIC, 0) == 1, "lost on save: " + back.study(realm.id()));
		// with realms off: no tab, no effect
		Realms.ENABLED = false;
		RealmResearch.forget();
		helper.assertTrue(ResearchScreen.forTest(land.owner(), land.hall(0)).icon(ResearchScreen.REALM_TAB).isEmpty(), "the Realm tab with realms off");
		helper.assertTrue(CivicEffects.of(land.level(), land.hall(1)).marketTraders() == 0, "the effect with realms off");
		Realms.ENABLED = true;
		RealmResearch.forget();
		helper.assertTrue(CivicEffects.of(land.level(), land.hall(1)).marketTraders() == 2, "the effect didn't come back");
		helper.succeed();
	}
}
