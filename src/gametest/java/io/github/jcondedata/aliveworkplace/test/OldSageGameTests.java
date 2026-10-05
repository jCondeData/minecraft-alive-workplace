package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.explore.ExplorerWork;
import io.github.jcondedata.aliveworkplace.grave.UndertakerWork;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.OldSage;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchTree;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.research.TreeEffects;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * 29.14, the Old Sage: the hermit's hut is set down only for a village with 5 research levels (and only one at a
 * time); each of the eight riddles takes its answer and refuses every other riddle's, with a hint after two misses;
 * three right answers free the Sage, who is a guest at the hall the next morning; the Ancient Lore loads with its
 * exclusive last pick, and every topic's effect works where it acts.
 */
public class OldSageGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final BlockPos MIDDLE = new BlockPos(15, 1, 15);
	private static final long DAY = VillageNeeds.DAY;
	private static final ResourceLocation TREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "ancient_lore");
	/** One right answer per riddle, in order. */
	private static final List<ItemStack> ANSWERS = List.of(new ItemStack(Items.FILLED_MAP), new ItemStack(Items.COMPASS), new ItemStack(Items.CLOCK),
		new ItemStack(Items.BOOK), new ItemStack(Items.SPONGE), new ItemStack(Items.TORCH), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.EMERALD));

	private static BlockPos hall(GameTestHelper helper) {
		return helper.absolutePos(HALL);
	}

	private static VillageHallBlockEntity entity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	/** The hall, owned by {@code owner}, on the villages' list; what the test made goes when it ends. */
	private static void setUp(GameTestHelper helper, ServerPlayer owner) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		entity(helper).setOwner(owner.getUUID(), owner.getName().getString());
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				record.forgetCaptive(v.getUUID());
				v.discard();
			}
			for (LegendRecord.Captive c : record.captives()) {
				if (c.hall().equals(Optional.of(hall))) {
					record.forgetCaptive(c.villager());
				}
			}
			level.getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(16), g -> true).forEach(IronGolem::discard);
			OldSage.forget();
			Research.forget();
			CivicEffects.forget();
			LegendPowers.forget();
		});
	}

	/** Runs {@code body} with only the Old Sage loaded, then puts the roster and the clock back before the tick ends. */
	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Legend sage = Legends.get(OldSage.ID).orElse(null);
		helper.assertTrue(sage != null, "old_sage.json not loaded");
		try {
			Legends.setForTest(Map.of(sage.id(), sage));
			body.accept(sage);
		} finally {
			level.setDayTime(time);
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static ResearchTree tree(GameTestHelper helper) {
		ResearchTree tree = ResearchTrees.get(TREE).orElse(null);
		helper.assertTrue(tree != null, "ancient_lore not loaded: " + ResearchTrees.all().stream().map(ResearchTree::id).toList());
		return tree;
	}

	/** The hall's research: the Ancient Lore topics at the levels given. */
	private static void lore(GameTestHelper helper, Map<String, Integer> topics) {
		ResearchTree tree = tree(helper);
		Map<String, Integer> out = new HashMap<>();
		topics.forEach((k, v) -> out.put(ResearchTrees.levelKey(tree, tree.topic(k).orElseThrow()), v));
		entity(helper).setResearch(new Research.State(Map.copyOf(out), Optional.empty(), 0, false));
		CivicEffects.forget();
	}

	private static Villager sageAt(GameTestHelper helper, Legend legend) {
		Villager v = OldSage.placeHut(helper.getLevel(), hall(helper), helper.absolutePos(MIDDLE), RandomSource.create(14));
		helper.assertTrue(v != null, "the hut is set down");
		return v;
	}

	/** The hut comes only once the village has 5 research levels, holds the lectern and the Sage, and only one at a time. */
	//$ gametest_ticks_batch AREA '100' '"oldSageHut"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "oldSageHut")
	public void sageHutOnlyForQualifyingVillage(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner);
		ServerLevel level = helper.getLevel();
		staged(helper, legend -> {
			helper.assertFalse(OldSage.qualifies(level, hall(helper)), "a village with no research doesn't get the hut");
			entity(helper).setResearch(new Research.State(Map.of("swift_hands", 3, "hearth", 1), Optional.empty(), 0, false));
			helper.assertFalse(OldSage.qualifies(level, hall(helper)), "4 levels aren't enough");
			entity(helper).setResearch(new Research.State(Map.of("swift_hands", 3, "hearth", 2), Optional.empty(), 0, false));
			helper.assertTrue(OldSage.qualifies(level, hall(helper)), "5 levels qualify");
			Villager sage = sageAt(helper, legend);
			LegendRecord.Captive hut = LegendRecord.get(level).captive(sage.getUUID()).orElseThrow();
			helper.assertTrue(hut.site().equals(OldSage.SITE) && hut.hall().equals(Optional.of(hall(helper))), "a hermit_hut captive for this hall: " + hut);
			int lecterns = 0;
			int shelves = 0;
			for (BlockPos p : BlockPos.betweenClosed(hut.min(), hut.max())) {
				lecterns += level.getBlockState(p).is(Blocks.LECTERN) ? 1 : 0;
				shelves += level.getBlockState(p).is(Blocks.BOOKSHELF) ? 1 : 0;
			}
			helper.assertTrue(lecterns == 1 && shelves >= 8, "the hut's lectern and bookshelves: " + lecterns + ", " + shelves);
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).asked().size() == OldSage.ASKED
				&& ModAttachments.SAGE_RIDDLES.get(sage).asked().stream().distinct().count() == OldSage.ASKED, "three different riddles");
			helper.assertTrue(level.getBlockState(sage.blockPosition().below()).isSolid(), "the Sage stands on the hut's floor");
			helper.assertFalse(OldSage.qualifies(level, hall(helper)), "one hut at a time");
			// the site picker: 150-250 blocks out, or nowhere (the test world isn't loaded that far)
			BlockPos site = BanditCamps.site(level, hall(helper), OldSage.NEAR, OldSage.FAR, RandomSource.create(3));
			if (site != null) {
				double d = Math.sqrt(site.distToCenterSqr(hall(helper).getCenter().x, site.getY() + 0.5, hall(helper).getCenter().z));
				helper.assertTrue(d >= OldSage.NEAR - 2 && d <= OldSage.FAR + 2, "the site is " + d + " blocks out");
			}
		});
		helper.succeed();
	}

	/** Every riddle takes its answer and refuses the other seven riddles' answers, with a hint after two misses. */
	//$ gametest_ticks_batch AREA '100' '"oldSageRiddles"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "oldSageRiddles")
	public void eachRiddleTakesItsAnswer(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setGameMode(GameType.SURVIVAL);
		setUp(helper, owner);
		staged(helper, legend -> {
			Villager sage = sageAt(helper, legend);
			for (int n = 1; n <= OldSage.RIDDLES.size(); n++) {
				ModAttachments.SAGE_RIDDLES.set(sage, new OldSage.Riddles(List.of(n, n % 8 + 1, (n + 1) % 8 + 1), 0, 0));
				for (int other = 1; other <= ANSWERS.size(); other++) {
					if (other == n) {
						continue;
					}
					owner.setItemInHand(InteractionHand.MAIN_HAND, ANSWERS.get(other - 1).copy());
					helper.assertTrue(LegendSites.use(owner, sage, InteractionHand.MAIN_HAND).consumesAction(), "the click is the Sage's");
					helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).solved() == 0, "riddle " + n + " took the answer to " + other);
					helper.assertTrue(owner.getMainHandItem().getCount() == 1, "a wrong answer is kept");
				}
				helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).misses() == 7 && sage.getUnhappyCounter() > 0,
					"misses counted and a shake of the head: " + ModAttachments.SAGE_RIDDLES.get(sage));
				owner.setItemInHand(InteractionHand.MAIN_HAND, ANSWERS.get(n - 1).copy());
				LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
				OldSage.Riddles after = ModAttachments.SAGE_RIDDLES.get(sage);
				helper.assertTrue(after.solved() == 1 && after.misses() == 0, "riddle " + n + " refused its answer: " + after);
				helper.assertTrue(n == 7 ? owner.getMainHandItem().is(Items.BUCKET) || owner.getInventory().countItem(Items.BUCKET) == 1
					: owner.getMainHandItem().isEmpty(), "the answer is handed over (a bucket comes back): " + owner.getMainHandItem());
				owner.getInventory().clearContent();
			}
			// the hint: none after one miss, one after two
			ModAttachments.SAGE_RIDDLES.set(sage, new OldSage.Riddles(List.of(2, 3, 4), 0, 0));
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).misses() < OldSage.HINT_AFTER, "a hint after one miss");
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).misses() >= OldSage.HINT_AFTER, "no hint after two misses");
			helper.assertTrue(Component.translatable("message.aliveworkplace.sage.hint.2").getString().contains("needle"), "the compass hint is in the lang file");
			// an empty hand only hears the riddle
			owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).solved() == 0, "an empty hand answered");
		});
		helper.succeed();
	}

	/** Three right answers free the Sage; they are a guest at the hall the next morning, by the found way, and not before. */
	//$ gametest_ticks_batch AREA '100' '"oldSageGuest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "oldSageGuest")
	public void threeAnswersBringTheSageAsGuest(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setGameMode(GameType.SURVIVAL);
		setUp(helper, owner);
		ServerLevel level = helper.getLevel();
		staged(helper, legend -> {
			Villager sage = sageAt(helper, legend);
			ModAttachments.SAGE_RIDDLES.set(sage, new OldSage.Riddles(List.of(8, 1, 3), 0, 0));
			long day = Chronicle.day(level);
			for (int n : List.of(8, 1, 3)) {
				helper.assertFalse(LegendRecord.get(level).captive(sage.getUUID()).orElseThrow().freed(), "free before the third answer");
				owner.setItemInHand(InteractionHand.MAIN_HAND, ANSWERS.get(n - 1).copy());
				LegendSites.use(owner, sage, InteractionHand.MAIN_HAND);
			}
			helper.assertTrue(LegendRecord.get(level).captive(sage.getUUID()).orElseThrow().freed(), "three right answers free the Sage");
			helper.assertFalse(LegendSites.isCaptive(sage) || sage.isNoAi(), "free to walk off");
			level.setDayTime((day - 1) * DAY + LegendGuests.MORNING_FROM + 100);
			LegendSites.arrive(level, hall(helper));
			helper.assertTrue(LegendGuests.guest(level, hall(helper)) == null, "not a guest the same day");
			level.setDayTime(day * DAY + LegendGuests.MORNING_FROM + 100);
			LegendSites.arrive(level, hall(helper));
			Villager guest = LegendGuests.guest(level, hall(helper));
			helper.assertTrue(guest != null, "a guest at the hall the next morning");
			LegendData data = ModAttachments.LEGEND.get(guest);
			helper.assertTrue(data.id().equals(OldSage.ID) && data.way().equals("found:hermit_hut"), "the Sage, found at the hut: " + data.way());
			entity(helper).setLegendGuests(LegendGuests.State.EMPTY);
		});
		helper.succeed();
	}

	/** The Ancient Lore loads: the Old Sage's, nine topics, costs of paper, books, emeralds and shards; one last pick for good. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void ancientLoreLastPickIsExclusive(GameTestHelper helper) {
		ResearchTree tree = tree(helper);
		helper.assertTrue(tree.legend().equals(OldSage.ID) && tree.topics().size() == 9, "legend or topics: " + tree.topics().size());
		ResearchTree.Topic old = tree.topic("old_tongues").orElseThrow();
		helper.assertTrue(old.levels() == 1 && old.cost(1).keySet().containsAll(List.of(Items.PAPER, Items.BOOK, Items.EMERALD, Items.AMETHYST_SHARD)), "costs");
		for (String id : List.of("star_charts", "herb_lore", "deep_memory", "runes_of_warding", "old_harvests")) {
			helper.assertTrue(tree.topic(id).orElseThrow().levels() == 2, id + " has two levels");
		}
		ResearchTree.Topic flame = tree.topic("undying_flame").orElseThrow();
		helper.assertTrue(flame.cost(1).containsKey(Items.ECHO_SHARD) && flame.needs().size() == 6, "the last pick costs echo shards and needs every topic");
		helper.assertTrue(tree.rivals(flame).stream().map(ResearchTree.Topic::id).sorted().toList().equals(List.of("golden_age", "iron_pact")), "rivals");
		Map<String, Integer> taken = new HashMap<>();
		taken.put(ResearchTrees.levelKey(tree, tree.topic("golden_age").orElseThrow()), 1);
		Research.State state = new Research.State(taken, Optional.empty(), 0, false);
		helper.assertTrue(ResearchTrees.takenRival(state, tree, tree.topic("iron_pact").orElseThrow()).isPresent(), "a second last pick is refused");
		helper.assertTrue(ResearchTrees.takenRival(state, tree, tree.topic("golden_age").orElseThrow()).isEmpty(), "its own pick stands");
		helper.succeed();
	}

	/** Every topic's effect where it acts, at the levels the hall keeps; nothing without them. */
	//$ gametest_ticks_batch AREA '100' '"ancientLoreEffects"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "ancientLoreEffects")
	public void ancientLoreEffectsWork(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner);
		ServerLevel level = helper.getLevel();
		Villager explorer = job(helper, VillagerProfession.CARTOGRAPHER, 6);
		Villager nurse = job(helper, ModVillagers.NURSE, 7);
		Villager farmer = job(helper, VillagerProfession.FARMER, 8);
		Villager orchard = job(helper, ModVillagers.ORCHARD_KEEPER, 9);
		Villager librarian = job(helper, VillagerProfession.LIBRARIAN, 10);
		Villager undertaker = job(helper, ModVillagers.UNDERTAKER, 11);
		Villager innkeeper = job(helper, ModVillagers.INNKEEPER, 12);
		helper.setBlock(new BlockPos(10, 2, 4), Blocks.BARREL);
		helper.runAfterDelay(2, () -> {
			BlockPos hall = hall(helper);
			float raidBefore = VillageRaids.chance(level, hall, 30);
			int xpBefore = Traits.xp(librarian, 100, RandomSource.create(7));
			helper.assertTrue(ExplorerWork.starCharts(explorer) == 1 && ExplorerWork.mapEvery(explorer) == 3 && Pace.ANCIENT_LORE.factor().of(farmer) == 1f
				&& !TreeEffects.flag(nurse, "herb_lore") && !UndertakerWork.undyingFlame(undertaker), "effects with nothing researched");
			lore(helper, Map.of("old_tongues", 1, "star_charts", 1, "herb_lore", 2, "deep_memory", 2, "runes_of_warding", 2, "old_harvests", 2));
			// Old Tongues: every traveller a Journeyman at least, Gifted twice as often
			for (int i = 0; i < 8; i++) {
				Villager guest = Innkeepers.arrive(level, innkeeper, helper.absolutePos(new BlockPos(10, 2, 4)));
				helper.assertTrue(guest != null, "no traveller came");
				helper.assertTrue(ModAttachments.TRAVELLER.get(guest).level() >= 3, "an Apprentice traveller under Old Tongues");
				guest.discard();
			}
			int plain = gifts(level, false);
			int twice = gifts(level, true);
			helper.assertTrue(plain * 3 / 2 < twice && twice < plain * 3, "Gifted twice as often: " + plain + " vs " + twice);
			// Star Charts: 25% farther a level, a map every second trip at II
			helper.assertTrue(ExplorerWork.starCharts(explorer) == 1.25 && ExplorerWork.mapEvery(explorer) == 3, "Star Charts I");
			lore(helper, Map.of("old_tongues", 1, "star_charts", 2, "herb_lore", 2, "deep_memory", 2, "runes_of_warding", 2, "old_harvests", 2));
			helper.assertTrue(ExplorerWork.starCharts(explorer) == 1.5 && ExplorerWork.mapEvery(explorer) == 2, "Star Charts II");
			// Herb Lore: two days shorter, and flowers cure
			helper.assertTrue(Sickness.recovery(nurse) == Sickness.RECOVERY - 2 * VillageNeeds.DAY && TreeEffects.flag(nurse, "herb_lore"), "Herb Lore");
			// Deep Memory: 30% more XP at II
			int xp = Traits.xp(librarian, 100, RandomSource.create(7));
			helper.assertTrue(xp == xpBefore + 30, "Deep Memory xp " + xpBefore + " -> " + xp);
			// Runes of Warding: a fifth less likely a level
			float raid = VillageRaids.chance(level, hall, 30);
			helper.assertTrue(Math.abs(raid - raidBefore * 0.64f) < 1e-5f, "raids " + raidBefore + " -> " + raid);
			// Old Harvests: farmers and orchard keepers 20% faster at II, others not
			helper.assertTrue(Math.abs(Pace.ANCIENT_LORE.factor().of(farmer) - 1f / 1.2f) < 1e-5f && Math.abs(Pace.ANCIENT_LORE.factor().of(orchard) - 1f / 1.2f) < 1e-5f
				&& Pace.ANCIENT_LORE.factor().of(librarian) == 1f, "Old Harvests");
			helper.assertTrue(!UndertakerWork.undyingFlame(undertaker) && TreeEffects.wellbeingFloor(entity(helper)) == 0f
				&& !TreeEffects.flag(level, hall, "iron_pact"), "no last pick yet");
			helper.succeed();
		});
	}

	/** The three last picks: the Undying Flame, the Golden Age (wellbeing floor, longer festivals), the Iron Pact (golems, damage). */
	//$ gametest_ticks_batch AREA '100' '"ancientLoreLastPicks"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "ancientLoreLastPicks")
	public void ancientLoreLastPicksWork(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner);
		ServerLevel level = helper.getLevel();
		Villager undertaker = job(helper, ModVillagers.UNDERTAKER, 6);
		Villager reveller = job(helper, VillagerProfession.FARMER, 7);
		for (int i = 0; i < 7; i++) {
			job(helper, VillagerProfession.NITWIT, 8 + i);
		}
		helper.runAfterDelay(2, () -> {
			BlockPos hall = hall(helper);
			long today = Chronicle.day(level);
			ModAttachments.FESTIVAL_DAY.set(reveller, today - 3);
			helper.assertFalse(Festivals.enjoyedLately(level, reveller), "a plain festival is over after 2 days");
			// the Undying Flame
			lore(helper, Map.of("undying_flame", 1));
			helper.assertTrue(UndertakerWork.undyingFlame(undertaker) && !Festivals.enjoyedLately(level, reveller), "the Undying Flame");
			// the Golden Age
			lore(helper, Map.of("golden_age", 1));
			helper.assertTrue(VillageNeeds.count(level, hall).wellbeing() >= 0.6f - 1e-4f, "wellbeing below 60%: " + VillageNeeds.count(level, hall).wellbeing());
			helper.assertTrue(Festivals.enjoyedLately(level, reveller) && !UndertakerWork.undyingFlame(undertaker), "festival moods last twice as long");
			// the Iron Pact
			lore(helper, Map.of("iron_pact", 1));
			IronGolem golem = OldSage.pactGolem(level, hall);
			helper.assertTrue(golem != null, "a golem for 9 villagers");
			helper.assertTrue(OldSage.pactGolem(level, hall) == null, "only one golem per 8 villagers");
			helper.assertTrue(OldSage.damage(golem, 8f) == 6f, "the pact's factor for the golem: " + OldSage.damage(golem, 8f)
				+ " (hall " + VillageHalls.nearest(level, golem.blockPosition()) + ")");
			float before = golem.getHealth();
			golem.hurt(level.damageSources().generic(), 8f);
			helper.assertTrue(Math.abs(before - golem.getHealth() - 6f) < 1e-3f, "a quarter less damage: " + (before - golem.getHealth()));
			lore(helper, Map.of());
			before = golem.getHealth();
			golem.invulnerableTime = 0;
			golem.hurt(level.damageSources().generic(), 8f);
			helper.assertTrue(Math.abs(before - golem.getHealth() - 8f) < 1e-3f, "full damage without the pact: " + (before - golem.getHealth()));
			helper.succeed();
		});
	}

	/** How many of 600 travellers are Gifted, the usual way or twice as often (fixed dice). */
	private static int gifts(ServerLevel level, boolean twice) {
		int n = 0;
		RandomSource random = RandomSource.create(42);
		Villager probe = EntityType.VILLAGER.create(level);
		for (int i = 0; i < 600; i++) {
			Innkeepers.giftTraveller(probe, random, twice);
			n += io.github.jcondedata.aliveworkplace.legend.Gifted.of(probe) != null ? 1 : 0;
		}
		return n;
	}

	private static Villager job(GameTestHelper helper, VillagerProfession job, int x) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 8));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		return v;
	}
}
