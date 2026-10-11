package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.RealmResearch;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.research.TreeEffects;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 33.15, shared research: a member's guards hit harder with the capital's Drill, its screen says "from the
 * capital" and its rank doesn't count it; an idle member scholar adds points to the capital's topic; a realm topic from
 * the test data pack is chosen on the capital's Realm row, paid from the realm treasury and finished, and its effect
 * reaches the members; a real {@code /reload} keeps it, and the realm's research survives a save; broken files are
 * skipped with their path and reason. Each test is alone in its batch (realms are on only inside
 * {@link RealmGameTests#land}).
 */
public class RealmResearchGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	static final String SURVEY = "aliveworkplace_test:test_survey";

	static RealmData.Realm theRealm(RealmGameTests.Land land) {
		return land.data().realmOf(land.at(0));
	}

	/** The capital's Drill III reaches a member's guard (30% harder), shows "from the capital" there, and leaves its rank alone. */
	//$ gametest_ticks_batch AREA '100' '"realmResearchDrill"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "realmResearchDrill")
	public void aMembersGuardsUseTheCapitalsDrill(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		Leftovers.after(helper, Research::forget);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 5));
		Research.forget();
		float before = Guards.levelBonus(guard);
		int scoreBefore = VillageRanks.score(land.level(), land.hall(1), 5).research();
		land.entity(0).setResearch(new Research.State(Map.of("drill", 3), Optional.empty(), 0, false));
		Research.forget();
		float after = Guards.levelBonus(guard);
		helper.assertTrue(Math.abs(after - before * 1.3f) < 1e-4, "the capital's Drill III: " + before + " -> " + after);
		helper.assertTrue(Research.at(land.level(), land.hall(1)).level(Research.Topic.DRILL) == 3, "the member's Drill");
		helper.assertTrue(land.entity(1).research().totalLevels() == 0
			&& VillageRanks.score(land.level(), land.hall(1), 5).research() == scoreBefore, "shared levels counted toward the member's rank");
		// the member's screen says so; the capital's doesn't
		ChoiceMenu member = ResearchScreen.forTest(land.owner(), land.hall(1));
		ItemStack drill = member.icon(ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()]);
		helper.assertTrue(RealmGameTests.lore(drill).contains("Level 3 from the capital") && RealmGameTests.lore(drill).contains("Level 0 of 3"),
			"the member's Drill icon: " + RealmGameTests.lore(drill));
		ChoiceMenu capital = ResearchScreen.forTest(land.owner(), land.hall(0));
		helper.assertFalse(RealmGameTests.lore(capital.icon(ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()])).contains("Level 3 from the capital"),
			"the capital's own screen says from the capital");
		// a higher level of its own wins; outside the realm (or with realms off) nothing is shared
		land.entity(1).setResearch(new Research.State(Map.of("hearth", 2), Optional.empty(), 0, false));
		land.entity(0).setResearch(new Research.State(Map.of("drill", 3, "hearth", 1), Optional.empty(), 0, false));
		Research.forget();
		helper.assertTrue(Research.at(land.level(), land.hall(1)).level(Research.Topic.HEARTH) == 2, "the capital's lower Hearth won");
		Realms.ENABLED = false;
		Research.forget();
		helper.assertTrue(Math.abs(Guards.levelBonus(guard) - before) < 1e-4, "shared with realms off");
		Realms.ENABLED = true;
		Realms.leave(land.level(), land.hall(1), land.owner());
		Research.forget();
		helper.assertTrue(Math.abs(Guards.levelBonus(guard) - before) < 1e-4, "shared after leaving the realm");
		helper.succeed();
	}

	/** A member's scholar with nothing chosen at home works on the capital's paid-for topic. */
	//$ gametest_ticks_batch AREA '400' '"realmResearchHelp"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "realmResearchHelp")
	public void anIdleMemberScholarHelpsTheCapital(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		Leftovers.after(helper, Research::forget);
		helper.setDayTime(2000);
		land.entity(0).setResearch(new Research.State(Map.of(), Optional.of("drill"), 0, true));
		helper.setBlock(new BlockPos(14, 2, 5), ModBlocks.SCHOLARS_DESK);
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 5));
		Jobs.employ(land.level(), scholar, helper.absolutePos(new BlockPos(14, 2, 5)), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		helper.succeedWhen(() -> {
			Research.State capital = land.entity(0).research();
			helper.assertTrue(capital.currentTopic() == Research.Topic.DRILL && capital.progress() > 0, "the capital's Drill: " + capital);
			helper.assertTrue(land.entity(1).research().current().isEmpty() && land.entity(1).research().progress() == 0,
				"the member's own research moved: " + land.entity(1).research());
		});
	}

	/**
	 * A realm topic from the test data pack: the capital's owner chooses it on the Realm row (a stranger can't, a topic
	 * whose needs aren't done can't be), the capital's scholar takes paper and a book from the chest and 5 emeralds from
	 * the realm treasury, finishes it, and its effect (25% more XP) reaches a member's villagers.
	 */
	//$ gametest_ticks_batch AREA '600' '"realmResearchTopic"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "realmResearchTopic")
	public void aRealmTopicIsPaidFromTheTreasuryAndFinishes(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		Leftovers.after(helper, () -> {
			Research.forget();
			RealmResearch.forgetEffects();
		});
		helper.setDayTime(2000);
		RealmResearch.Topic survey = RealmResearch.get(SURVEY).orElse(null);
		helper.assertTrue(survey != null && survey.icon() == Items.SPYGLASS && survey.levels() == 2 && survey.emeralds(1) == 5
			&& survey.items(1).equals(Map.of(Items.PAPER, 2, Items.BOOK, 1)) && survey.points(2) == 80, "the test topic: " + survey);
		helper.assertTrue(RealmResearch.all().stream().map(RealmResearch.Topic::key).toList()
			.equals(List.of("common_law", "royal_physicians", "guild_exchange", "kings_peace", SURVEY)), "the topics: " + RealmResearch.all());
		RealmData.Realm realm = theRealm(land);
		land.data().putRealm(realm.withTreasury(5000, realm.ledger()));
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.SCHOLARS_DESK);
		helper.setBlock(new BlockPos(5, 2, 7), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(5, 2, 7));
		chest.setItem(0, new ItemStack(Items.PAPER, 4));
		chest.setItem(1, new ItemStack(Items.BOOK, 2));
		// the Realm row: only on the capital's screen
		ChoiceMenu menu = ResearchScreen.forTest(land.owner(), land.hall(0));
		ItemStack label = menu.icon(RealmResearch.LABEL);
		helper.assertTrue(label.is(Items.GOLDEN_HELMET) && label.getHoverName().getString().equals("Realm research")
			&& RealmGameTests.lore(label).contains("Nothing being researched: click a realm topic")
			&& RealmGameTests.lore(label).contains("The realm treasury holds 50.00 emeralds"), "the Realm row's label: " + RealmGameTests.lore(label));
		helper.assertTrue(ResearchScreen.forTest(land.owner(), land.hall(1)).icon(RealmResearch.LABEL).isEmpty(), "a Realm row on a member's screen");
		int slot = RealmResearch.SLOTS[4];
		ItemStack icon = menu.icon(slot);
		helper.assertTrue(icon.is(Items.SPYGLASS) && icon.getHoverName().getString().equals("Test Survey")
			&& RealmGameTests.lore(icon).contains("XP the realm's villagers earn: +25% a level")
			&& RealmGameTests.lore(icon).contains("Next level: 2× Paper, 1× Book, and 5 emeralds from the realm treasury")
			&& RealmGameTests.lore(icon).contains("Click: the capital's scholars research this next"), "the survey's icon: " + RealmGameTests.lore(icon));
		// a topic whose needs aren't done, and a stranger, are refused
		List<net.minecraft.network.chat.Component> heard = new ArrayList<>();
		ServerPlayer stranger = RealmGameTests.listener(helper, heard, "stranger");
		helper.assertTrue(RealmResearch.choose(land.level(), land.hall(0), stranger, survey).getString().equals("Only the capital's owner chooses the realm's research"),
			"a stranger chose");
		helper.assertTrue(RealmResearch.choose(land.level(), land.hall(0), land.owner(), RealmResearch.get("royal_physicians").orElseThrow()).getString()
			.equals("Royal Physicians can't be researched yet"), "Royal Physicians before Common Law");
		helper.assertTrue(RealmResearch.choose(land.level(), land.hall(1), land.owner(), survey).getString().equals(land.name(1) + " isn't the capital of a realm."),
			"chosen from a member");
		helper.assertTrue(theRealm(land).research().current().isEmpty(), "chosen by a refusal");
		menu.press(slot, land.owner());
		helper.assertTrue(theRealm(land).research().current().equals(Optional.of(SURVEY)), "not chosen: " + theRealm(land).research());
		helper.assertTrue(RealmGameTests.heard(land, "The capital's scholars will research Test Survey for the realm next"), "no chosen line: " + land.heard());
		helper.assertTrue(RealmGameTests.lore(menu.icon(slot)).contains("Being researched now")
			&& RealmGameTests.lore(menu.icon(RealmResearch.LABEL)).contains("Waiting for 2× Paper, 1× Book by a Scholar's Desk and 5 emeralds in the realm treasury"),
			"the row after choosing: " + RealmGameTests.lore(menu.icon(RealmResearch.LABEL)));
		BlockPos at = land.hall(0);
		land.owner().teleportTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 5));
		Jobs.employ(land.level(), scholar, helper.absolutePos(new BlockPos(5, 2, 5)), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		Villager member = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 5));
		String level1 = net.minecraft.network.chat.Component.translatable("research.aliveworkplace.level", "Test Survey",
			io.github.jcondedata.aliveworkplace.build.BuilderLevels.levelName(1)).getString();
		helper.succeedWhen(() -> {
			RealmData.Realm now = theRealm(land);
			helper.assertTrue(now.research().levels().getOrDefault(SURVEY, 0) == 1, "not finished: " + now.research());
			helper.assertTrue(now.treasury() == 4500, "the treasury: " + now.treasury());
			helper.assertTrue(now.ledger().get(now.ledger().size() - 1).reason().getString().equals("Research: " + level1), "the movement: " + now.ledger());
			helper.assertTrue(chest.countItem(Items.PAPER) == 2 && chest.countItem(Items.BOOK) == 1, "the chest: " + chest.countItem(Items.PAPER) + " paper");
			helper.assertTrue(land.entity(0).research().levels().isEmpty(), "the capital's own research changed: " + land.entity(0).research());
			helper.assertTrue(TreeEffects.xpPercent(member) == 25, "the member's XP bonus: " + TreeEffects.xpPercent(member));
			helper.assertTrue(RealmGameTests.heard(land, "Realm research in the Barony of " + land.name(0) + ": " + level1 + " is done. XP the realm's villagers earn: +25% a level"),
				"no done line: " + land.heard());
			helper.assertTrue(land.entity(0).chronicle().stream().anyMatch(e -> e.text().getString().startsWith(level1 + " was researched for the realm by")),
				"no chronicle line");
		});
	}

	/** Without emeralds in the treasury the capital's scholar waits and nothing is taken. */
	//$ gametest_ticks_batch AREA '200' '"realmResearchPoor"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "realmResearchPoor")
	public void anEmptyTreasuryWaits(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		helper.setDayTime(2000);
		RealmData.Realm realm = theRealm(land);
		land.data().putRealm(realm.withTreasury(400, realm.ledger()).withResearch(new Research.State(Map.of(), Optional.of(SURVEY), 0, false)));
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.SCHOLARS_DESK);
		helper.setBlock(new BlockPos(5, 2, 7), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(5, 2, 7));
		chest.setItem(0, new ItemStack(Items.PAPER, 4));
		chest.setItem(1, new ItemStack(Items.BOOK, 2));
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 5));
		Jobs.employ(land.level(), scholar, helper.absolutePos(new BlockPos(5, 2, 5)), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		helper.runAfterDelay(150, () -> {
			helper.assertFalse(theRealm(land).research().paid(), "paid with 4 emeralds of 5");
			helper.assertTrue(theRealm(land).treasury() == 400 && chest.countItem(Items.PAPER) == 4, "something was taken");
			var status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(scholar, land.level().getGameTime());
			helper.assertTrue(status != null && status.line().getString().equals("waiting for 5 emeralds in the realm treasury"), "the scholar's status: "
				+ (status == null ? null : status.line().getString()));
			helper.succeed();
		});
	}

	/** A real {@code /reload} keeps the realm topics and the realm's research; the realm's research survives a save (and an older realm loads with none). */
	//$ gametest_ticks_batch AREA '1200' '"realmResearchReload"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "realmResearchReload")
	public void aReloadKeepsIt(GameTestHelper helper) {
		RealmGameTests.Land land = RealmTreasuryGameTests.realm(helper);
		RealmData.Realm realm = theRealm(land);
		Research.State state = new Research.State(Map.of(SURVEY, 1), Optional.of("common_law"), 120, true);
		land.data().putRealm(realm.withResearch(state));
		Tag saved = RealmData.Realm.CODEC.encodeStart(NbtOps.INSTANCE, theRealm(land)).getOrThrow();
		RealmData.Realm back = RealmData.Realm.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(back.research().equals(state), "the realm's research after a save: " + back.research());
		net.minecraft.nbt.CompoundTag older = ((net.minecraft.nbt.CompoundTag) saved).copy();
		older.remove("research");
		helper.assertTrue(RealmData.Realm.CODEC.parse(NbtOps.INSTANCE, older).getOrThrow().research().equals(Research.State.EMPTY), "an older realm's research");
		MinecraftServer server = helper.getLevel().getServer();
		int generation = RealmResearch.generation();
		server.reloadResources(server.getPackRepository().getSelectedIds());
		helper.succeedWhen(() -> {
			helper.assertTrue(RealmResearch.generation() != generation, "the reload hasn't finished");
			helper.assertTrue(RealmResearch.get(SURVEY).isPresent() && RealmResearch.all().size() == 5, "the topics after the reload: " + RealmResearch.all());
			helper.assertTrue(theRealm(land).research().equals(state), "the realm's research after the reload: " + theRealm(land).research());
			ChoiceMenu menu = ResearchScreen.forTest(land.owner(), land.hall(0));
			helper.assertTrue(menu.icon(RealmResearch.SLOTS[4]).getCount() == 1
				&& RealmGameTests.lore(menu.icon(RealmResearch.SLOTS[0])).contains("Being researched now"), "the Realm row after the reload");
		});
	}

	/** A broken file is skipped with its path and the reason; the good ones load. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '20' '"realmResearchBroken"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 20, batch = "realmResearchBroken")
	public void brokenFilesAreSkipped(GameTestHelper helper) {
		List<RealmResearch.Topic> before = RealmResearch.all();
		Leftovers.after(helper, () -> RealmResearch.set(before));
		Map<ResourceLocation, Callable<Reader>> files = new LinkedHashMap<>();
		String good = "{\"name\": \"Good\", \"points\": 10, \"emeralds\": 2, \"effect\": {\"type\": \"wellbeing\", \"amount\": 5}}";
		files.put(ResourceLocation.fromNamespaceAndPath("t", "realm_research/good.json"), () -> new StringReader(good));
		files.put(ResourceLocation.fromNamespaceAndPath("t", "realm_research/odd_effect.json"),
			() -> new StringReader("{\"name\": \"Odd\", \"points\": 10, \"effect\": {\"type\": \"luck_of_the_irish\", \"amount\": 5}}"));
		files.put(ResourceLocation.fromNamespaceAndPath("t", "realm_research/no_points.json"),
			() -> new StringReader("{\"name\": \"No points\", \"effect\": {\"type\": \"xp\", \"amount\": 5}}"));
		files.put(ResourceLocation.fromNamespaceAndPath("t", "realm_research/not_json.json"), () -> new StringReader("{name: "));
		files.put(ResourceLocation.fromNamespaceAndPath("t", "realm_research/lonely.json"),
			() -> new StringReader("{\"name\": \"Lonely\", \"points\": 10, \"needs\": {\"t:nope\": 1}, \"effect\": {\"type\": \"xp\", \"amount\": 5}}"));
		List<String> problems = RealmResearch.load(files);
		helper.assertTrue(RealmResearch.all().stream().map(RealmResearch.Topic::key).toList().equals(List.of("t:good")), "loaded: " + RealmResearch.all());
		helper.assertTrue(problems.size() == 4, "problems: " + problems);
		helper.assertTrue(problems.stream().anyMatch(p -> p.startsWith("t:realm_research/odd_effect.json: ") && p.contains("unknown effect type 'luck_of_the_irish'"))
			&& problems.stream().anyMatch(p -> p.startsWith("t:realm_research/no_points.json: ") && p.contains("points"))
			&& problems.stream().anyMatch(p -> p.startsWith("t:realm_research/not_json.json: "))
			&& problems.stream().anyMatch(p -> p.equals("t:realm_research/lonely.json: needs unknown topic 't:nope'")), "the reasons: " + problems);
		helper.succeed();
	}
}
