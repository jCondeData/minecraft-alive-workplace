package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * The eight reforms as stories (ROADMAP 31.22). Per reform: the edict in force starts its side arc (the ask, by a
 * villager of the fitting trade), talking to them puts M30's first step up as the arc's quest, the three steps are M30's
 * kinds and numbers one a day, the ending leaves its lasting thing, and the edict is reformed (boost kept, cost gone) in
 * that village only. With {@code storyArcs} off M30's own steps go up as before; reform arcs don't count against
 * {@code arcsAtOnce}.
 */
public class ReformStoryGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	/** Another village's hall, out of the first's reach (radius 16 in these tests). */
	private static final BlockPos OTHER = new BlockPos(25, 2, 25);
	private static final BlockPos ASKER = new BlockPos(8, 2, 6);

	//$ gametest_ticks_batch AREA '200' '"reformStoryShiftBell"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reformStoryShiftBell")
	public void theShiftBellFromTheToolsmithsAskToTheBellRinging(GameTestHelper helper) {
		// natural ground beside the hall for the bell
		for (int x = 9; x <= 13; x++) {
			for (int z = 3; z <= 9; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		story(helper, "long_shifts", "shift_bell", VillagerProfession.TOOLSMITH, (level, hall) -> {
			BlockPos bell = ReformStories.bell(level, hall);
			helper.assertTrue(bell != null && level.getBlockState(bell).is(Blocks.BELL), "no bell: " + bell);
			helper.assertTrue(ReformStories.natural(level.getBlockState(bell.below(4))), "not on natural ground: " + level.getBlockState(bell.below(4)));
			long dayStart = level.getDayTime() / VillageNeeds.DAY * VillageNeeds.DAY;
			int rings = ReformStories.RINGS.get();
			level.setDayTime(dayStart + 5000);
			ReformStories.tick(level);
			helper.assertTrue(ReformStories.RINGS.get() == rings, "rang mid-shift");
			level.setDayTime(dayStart + VillageNeeds.DAY + ReformStories.RING_START);
			ReformStories.tick(level);
			helper.assertTrue(ReformStories.RINGS.get() == rings + 1, "didn't ring the work day in");
			level.setDayTime(dayStart + VillageNeeds.DAY + ReformStories.RING_END);
			ReformStories.tick(level);
			helper.assertTrue(ReformStories.RINGS.get() == rings + 2, "didn't ring the work day out");
			helper.assertTrue(told(level, hall).contains("The Shift Bell was raised beside the hall of " + VillageHalls.name(level, hall).getString() + "."),
				"the chronicle: " + told(level, hall));
		});
	}

	//$ gametest_ticks_batch AREA '200' '"reformStoryGranary"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reformStoryGranary")
	public void theCommonGranaryEndsInAHarvestSupper(GameTestHelper helper) {
		boolean festivals = Festivals.ENABLED;
		Festivals.ENABLED = true; // off in GameTest runs (the config)
		Leftovers.after(helper, () -> Festivals.ENABLED = festivals);
		story(helper, "free_bread", "common_granary", VillagerProfession.FARMER, (level, hall) -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			long today = Chronicle.day(level);
			helper.assertTrue(entity.festivalDay() == today + 1, "no supper tomorrow: " + entity.festivalDay());
			helper.assertTrue(!ReformStories.supper(level, hall), "the supper is today");
			level.setDayTime(level.getDayTime() + VillageNeeds.DAY);
			helper.assertTrue(ReformStories.supper(level, hall), "tomorrow isn't the supper (no fireworks)");
		});
	}

	//$ gametest_ticks_batch AREA '200' '"reformStoryMidwives"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reformStoryMidwives")
	public void theMidwivesBringANurseAtJourneyman(GameTestHelper helper) {
		story(helper, "large_families", "midwives", ModVillagers.NURSE, (level, hall) -> {
			List<Villager> midwife = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.getCustomName() != null
				&& v.getCustomName().getString().equals("Midwife"));
			helper.assertTrue(midwife.size() == 1 && midwife.get(0).getVillagerData().getProfession() == ModVillagers.NURSE
				&& midwife.get(0).getVillagerData().getLevel() == 3, "the midwife: " + midwife);
		});
	}

	//$ gametest_ticks_batch AREA '200' '"reformStoryGate"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reformStoryGate")
	public void theWatchfulGateBringsAnOldSoldierInIron(GameTestHelper helper) {
		story(helper, "open_gates", "watchful_gate", ModVillagers.GUARD, (level, hall) -> {
			List<Villager> soldier = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.getCustomName() != null
				&& v.getCustomName().getString().equals("Old Soldier"));
			helper.assertTrue(soldier.size() == 1 && soldier.get(0).getVillagerData().getProfession() == ModVillagers.GUARD
				&& soldier.get(0).getVillagerData().getLevel() == 4 && soldier.get(0).getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
				"the old soldier: " + soldier);
		});
	}

	//$ gametest_ticks_batch AREA '200' '"reformStoryFund"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reformStoryFund")
	public void theFestivalFundMakesTheNextFestivalGrand(GameTestHelper helper) {
		story(helper, "festival_season", "festival_fund", ModVillagers.BARD, (level, hall) -> {
			Villager guest = helper.spawn(EntityType.VILLAGER, ASKER.east(2));
			guest.setNoAi(true);
			Festivals.feast(level, hall);
			helper.assertTrue(ReformStories.grand(level, hall), "the festival wasn't grand");
			long day = ModAttachments.FESTIVAL_DAY.get(guest);
			level.setDayTime(level.getDayTime() + (Festivals.MOOD_DAYS + 1) * VillageNeeds.DAY);
			helper.assertTrue(Chronicle.day(level) - day == Festivals.MOOD_DAYS + 1 && Festivals.enjoyedLately(level, guest),
				"the good mood didn't last a day longer");
			level.setDayTime(level.getDayTime() + VillageNeeds.DAY);
			helper.assertTrue(!Festivals.enjoyedLately(level, guest), "the good mood lasted on");
		});
	}

	//$ gametest_ticks_batch AREA '200' '"reformStoryLedger"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reformStoryLedger")
	public void theFairLedgerIsAWrittenBookOfTheTakings(GameTestHelper helper) {
		ReformStories.takings(helper.getLevel(), helper.absolutePos(HALL), Chronicle.day(helper.getLevel()) - 1, 250); // (yesterday: the hall's own round adds today's)
		story(helper, "tithe", "fair_ledger", VillagerProfession.LIBRARIAN, (level, hall) -> {
			ServerPlayer player = level.getServer().getPlayerList().getPlayers().stream().filter(p -> p.blockPosition().closerThan(hall, 4)).findFirst().orElseThrow();
			ItemStack book = ItemStack.EMPTY;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(Items.WRITTEN_BOOK)) {
					book = player.getInventory().getItem(i);
				}
			}
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			String village = VillageHalls.name(level, hall).getString();
			helper.assertTrue(content != null && book.getHoverName().getString().equals("The Fair Ledger of " + village), "the ledger: " + book);
			String page = content.pages().get(0).raw().getString();
			helper.assertTrue(page.contains("2.50 emeralds") && page.split("Day ").length == ReformStories.LEDGER_DAYS + 1, "the page: " + page);
		});
	}

	//$ gametest_ticks_batch AREA '200' '"reformStoryLamps"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reformStoryLamps")
	public void theLamplightersGiveTwoStreetLampsAndALamplitNight(GameTestHelper helper) {
		story(helper, "curfew", "lamplighters", ModVillagers.PORTER, (level, hall) -> {
			ServerPlayer player = level.getServer().getPlayerList().getPlayers().stream().filter(p -> p.blockPosition().closerThan(hall, 4)).findFirst().orElseThrow();
			int lamps = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack s = player.getInventory().getItem(i);
				if (s.getItem() instanceof BlueprintItem) {
					lamps += s.getCount();
				}
			}
			helper.assertTrue(lamps == 2, "street lamp blueprints: " + lamps);
			long dayStart = level.getDayTime() / VillageNeeds.DAY * VillageNeeds.DAY;
			level.setDayTime(dayStart + ReformStories.NIGHT + 100);
			ReformStories.tick(level);
			String line = "The first lamplit night in " + VillageHalls.name(level, hall).getString() + ": the streets glowed, and nobody hurried home.";
			helper.assertTrue(told(level, hall).contains(line), "the lamplit night: " + told(level, hall));
			ReformStories.tick(level);
			helper.assertTrue(told(level, hall).stream().filter(line::equals).count() == 1, "written twice");
		});
	}

	//$ gametest_ticks_batch AREA '200' '"reformStoryDrill"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reformStoryDrill")
	public void theMilitiaDrillSparsAtTheDummiesAtNoon(GameTestHelper helper) {
		helper.setBlock(new BlockPos(6, 2, 10), ModBlocks.TRAINING_DUMMY);
		story(helper, "conscription", "militia_drill", ModVillagers.GUARD, (level, hall) -> {
			Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 10));
			farmer.setNoAi(true);
			long dayStart = level.getDayTime() / VillageNeeds.DAY * VillageNeeds.DAY;
			int drilled = ReformStories.DRILLED.get();
			level.setDayTime(dayStart + VillageNeeds.DAY + ReformStories.DRILL_START + 20);
			ReformStories.tick(level);
			helper.assertTrue(ReformStories.DRILLED.get() > drilled, "nobody sparred at the dummy");
			level.setDayTime(dayStart + VillageNeeds.DAY + ReformStories.DRILL_END + 20);
			ReformStories.tick(level);
			String village = VillageHalls.name(level, hall).getString();
			helper.assertTrue(told(level, hall).stream().anyMatch(l -> l.startsWith("Drill day in " + village)), "the drill's line: " + told(level, hall));
		});
	}

	/** With storyArcs off, M30's own step goes up on the quest page; on, the story runs instead and it stays off. */
	//$ gametest_ticks_batch AREA '100' '"reformStoryOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "reformStoryOff")
	public void withStoryArcsOffTheStepsGoUpAsBefore(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager smith = asker(helper, VillagerProfession.TOOLSMITH);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper, HALL);
			String edict = AliveWorkplace.id("long_shifts").toString();
			entity.setEdicts(List.of(new Edicts.InForce(edict, Chronicle.day(level))));
			boolean on = Arcs.ENABLED;
			Arcs.ENABLED = false;
			try {
				autoRound(level, hall, entity);
			} finally {
				Arcs.ENABLED = on;
			}
			helper.assertTrue(Reforms.shown(entity).size() == 1 && Arcs.side(level, hall).isEmpty(), "M30's step didn't go up: " + Reforms.shown(entity));
			autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).size() == 1 && Reforms.shown(entity).isEmpty(), "the story didn't take over the step");
			helper.assertTrue(smith.getTags().contains(Arcs.TAG), "the smith wasn't asked");
			helper.succeed();
		});
	}

	/** Reform arcs are side arcs: they start with arcsAtOnce used up, and don't count against it. */
	//$ gametest_ticks_batch AREA '100' '"reformStorySide"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "reformStorySide")
	public void reformArcsDontCountAgainstArcsAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		asker(helper, ModVillagers.GUARD);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper, HALL);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("open_gates").toString(), Chronicle.day(level))));
			int atOnce = Arcs.AT_ONCE;
			Arcs.AT_ONCE = 0;
			try {
				int before = Arcs.runningCount(level.getServer());
				autoRound(level, hall, entity);
				helper.assertTrue(Arcs.side(level, hall).size() == 1, "the reform's story didn't start with arcsAtOnce used up");
				helper.assertTrue(Arcs.find("watchful_gate").orElseThrow().side() && Arcs.runningCount(level.getServer()) == before, "it counted");
				helper.assertTrue(Arcs.running(level, hall) == null, "it took the main arc's place");
			} finally {
				Arcs.AT_ONCE = atOnce;
			}
			helper.succeed();
		});
	}

	// --- the story, start to end ------------------------------------------------------------------------------------

	/**
	 * Runs the reform of {@code edictPath} as its story {@code arcPath} in a village with a {@code job} villager: the
	 * ask, the three steps (M30's), the ending ({@code ending} checks what it left), the edict reformed here only.
	 */
	private static void story(GameTestHelper helper, String edictPath, String arcPath, VillagerProfession job, BiConsumer<ServerLevel, BlockPos> ending) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(OTHER, ModBlocks.VILLAGE_HALL);
		Villager asker = asker(helper, job);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper, HALL);
			VillageHallBlockEntity other = ready(helper, OTHER);
			String edict = AliveWorkplace.id(edictPath).toString();
			Edicts.Edict file = Edicts.find(edictPath).orElseThrow();
			Reforms.Reform reform = file.reform().orElseThrow();
			helper.assertTrue(reform.arc().map(a -> a.equals("aliveworkplace:" + arcPath)).orElse(false), "the reform's arc: " + reform.arc());
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			long day = Chronicle.day(level) + 1;
			level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
			entity.setEdicts(List.of(new Edicts.InForce(edict, day)));
			other.setEdicts(List.of(new Edicts.InForce(edict, day)));

			// The ask: the edict in force starts the story; the villager of the trade asks; M30's step stays off the page.
			autoRound(level, hall, entity);
			ArcState s = Arcs.side(level, hall).stream().filter(a -> a.id.equals("aliveworkplace:" + arcPath)).findFirst().orElse(null);
			helper.assertTrue(s != null, "the story didn't start");
			helper.assertTrue(Reforms.shown(entity).isEmpty(), "M30's step is up beside the story");
			List<Quest> quests = arcQuests(level, hall);
			helper.assertTrue(quests.size() == 1 && quests.get(0).objectives.get(0) instanceof Objectives.Talk, "the ask: " + quests);
			helper.assertTrue(asker.getTags().contains(ArcEffects.roleTag("asker")), "the " + job + " wasn't the one asking");
			helper.assertTrue(!Arcs.chatter(level, hall).isEmpty(), "no chatter");
			helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, asker) == InteractionResult.SUCCESS, "talking didn't count");

			// The three steps: M30's kinds and numbers, one a day.
			for (int n = 0; n < reform.steps().size(); n++) {
				if (n > 0) {
					level.setDayTime((day + n - 1) * VillageNeeds.DAY + 1000);
					Arcs.round(level, hall, entity);
				}
				Reforms.Step step = reform.steps().get(n).resolve(false);
				quests = arcQuests(level, hall);
				helper.assertTrue(quests.size() == 1, "step " + (n + 1) + ": " + quests);
				Quest q = quests.get(0);
				helper.assertTrue(q.name.map(Component::getString).orElse("").equals(Reforms.title(new VillageQuests.ReformStep(edict, n)).getString()), "the step's name: " + q.name);
				if (step.kind() == VillageQuests.Kind.BRING) {
					helper.assertTrue(q.objectives.get(0) instanceof Objectives.Bring b && b.count() == step.count() && b.item().equals(step.item()),
						"step " + (n + 1) + " isn't M30's: " + q.objectives.get(0).json());
					give(player, BuiltInRegistries.ITEM.get(ResourceLocation.parse(step.item())), step.count());
					helper.assertTrue(Stories.handIn(player, hall, q.id) == step.count(), "not handed in: step " + (n + 1));
				} else {
					helper.assertTrue(q.objectives.get(0) instanceof Objectives.Kill k && k.count() == step.count(),
						"step " + (n + 1) + " isn't M30's: " + q.objectives.get(0).json());
					Zombie zombie = helper.spawn(EntityType.ZOMBIE, ASKER.west(4));
					for (int i = 0; i < step.count(); i++) {
						Stories.onKill(level, zombie, player);
					}
					zombie.discard();
				}
				if (n < reform.steps().size() - 1) {
					helper.assertTrue(!Reforms.reformed(entity, edict) && Arcs.side(level, hall).contains(s), "ended early at step " + (n + 1));
					helper.assertTrue(arcQuests(level, hall).isEmpty(), "the next step came the same day");
				}
			}

			// The ending: reformed here, for good; the other village not.
			helper.assertTrue(Arcs.side(level, hall).isEmpty(), "the story didn't end");
			helper.assertTrue(Reforms.reformed(entity, edict) && Reforms.progress(entity, edict).step() == reform.steps().size(), "not reformed");
			helper.assertTrue(!Reforms.reformed(other, edict), "reformed in the other village too");
			CivicEffects.forget();
			List<CivicEffects.Effect> here = CivicEffects.of(entity).all().stream().map(CivicEffects.Active::effect).toList();
			List<CivicEffects.Effect> there = CivicEffects.of(other).all().stream().map(CivicEffects.Active::effect).toList();
			helper.assertTrue(here.containsAll(file.boost()) && file.cost().stream().noneMatch(here::contains), "boost kept, cost gone: " + here);
			helper.assertTrue(there.containsAll(file.cost()), "the other village lost its cost: " + there);
			autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).isEmpty() && Reforms.shown(entity).isEmpty(), "it began again");
			String name = Arcs.find(arcPath).orElseThrow().name().getString();
			helper.assertTrue(told(level, hall).contains("The tale of " + name + " came to its end."), "the chronicle: " + told(level, hall));
			ending.accept(level, hall);
			helper.succeed();
		});
	}

	/** One hall round with arcs starting by themselves, as in play. */
	private static void autoRound(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		boolean auto = Arcs.AUTO;
		Arcs.AUTO = true;
		try {
			Reforms.round(level, hall, entity);
		} finally {
			Arcs.AUTO = auto;
		}
	}

	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos other = helper.absolutePos(OTHER);
		Leftovers.after(helper, () -> {
			Arcs.stopAll(level, hall);
			Arcs.stopAll(level, other);
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager asker(GameTestHelper helper, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, ASKER);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		v.setVillagerXp(20);
		return v;
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(abs);
		Arcs.stopAll(level, abs);
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		hall.setQuests(List.of());
		Stories.Data.get(level).remove(abs);
		hall.setLastQuestDay(Long.MAX_VALUE / 2);
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}

	private static void give(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
		int max = new ItemStack(item).getMaxStackSize();
		for (int left = count; left > 0; left -= max) {
			player.getInventory().add(new ItemStack(item, Math.min(max, left)));
		}
	}

	private static List<Quest> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	private static List<String> told(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().map(e -> e.text().getString()).map(Component::literal).map(Component::getString).toList();
	}
}
