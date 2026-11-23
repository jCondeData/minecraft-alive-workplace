package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.card.JobCards;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane C, 2026-11-23), written from docs/wiki/job-status-cards.md: what the card promises that no other test
 * checks. A builder's "Skipped" and "Next up" lines; an idle builder's and an idle miner's line in the chat's own
 * words; the experience line's green bar and the green "Needs: Nothing"; every word on all seven jobs' cards resolved,
 * with no raw placeholder and American spelling; every text key the page lists present; and a builder's card after its
 * villager is saved and loaded. Every card is opened by a sneak-right-click with an empty hand, as a player's client
 * sends it.
 */
public class QaJobCards1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 3);
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	record Setup(Villager villager, ServerPlayer player, List<Component> seen) {
	}

	/** A worker at a station at {@code station}, standing one block over, named {@code name}. */
	private static Villager employ(GameTestHelper helper, Block block, net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi,
								   VillagerProfession job, String name, BlockPos station) {
		helper.setBlock(station, block);
		Villager v = helper.spawn(EntityType.VILLAGER, station.east());
		v.setNoAi(true);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(station), poi, job);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static Setup start(GameTestHelper helper, Block block, net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi,
							   VillagerProfession job, String name) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		Villager v = employ(helper, block, poi, job, name, new BlockPos(3, 2, 3));
		List<Component> seen = new ArrayList<>();
		return new Setup(v, QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL), seen);
	}

	private static StatusCard open(GameTestHelper helper, ServerPlayer player, Villager villager, List<Component> seen) {
		return JobCardGameTests.open(helper, new JobCardGameTests.Setup(villager, player, seen)).card();
	}

	/**
	 * A builder on a hut with 4 blocks given up and another hut queued after it: under Their work, after the build's
	 * line, "Skipped: 4" and "Next up: <the queued build>", then Experience.
	 */
	//$ gametest_batch AREA '"qaJobCards1123cQueue"'
	@GameTest(template = AREA, batch = "qaJobCards1123cQueue")
	public void qaBuilderCardShowsSkippedAndNextUp(GameTestHelper helper) {
		Setup s = start(helper, ModBlocks.BUILDERS_BENCH, ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER, "Mason");
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, s.villager(), s.player(), TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(12, 1, 12)), Rotation.NONE, Mirror.NONE));
		BuildSite queued = Builders.enqueue(level, s.villager(), s.player(), TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 1, 12)), Rotation.NONE, Mirror.NONE));
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).remove(site.id());
			BuildSiteManager.get(level).remove(queued.id());
		});
		site.leftBroken(4);
		StatusCard card = open(helper, s.player(), s.villager(), s.seen());
		List<String> lines = JobCardGameTests.lines(card);
		helper.assertTrue(lines.contains("Skipped: 4"), "no \"Skipped: 4\" line: " + lines);
		String next = "Next up: " + Blueprints.siteName(level, queued).getString();
		helper.assertTrue(lines.contains(next), "no \"" + next + "\" line: " + lines);
		helper.assertTrue(lines.indexOf("Skipped: 4") > 0 && lines.indexOf(next) < lines.indexOf(lines.stream()
			.filter(l -> l.startsWith("Experience: ")).findFirst().orElse("")), "the lines out of order: " + lines);
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * A builder with his bench and nothing to build, and a miner with her blast furnace and no quarry: each card's gray
	 * line is the chat's own ("Mason has no building job. Hand them a placed blueprint." and "Gimli has no quarry. Hand
	 * them a marked Quarry Marker."), and neither shows a site line.
	 */
	//$ gametest_batch AREA '"qaJobCards1123cIdle"'
	@GameTest(template = AREA, batch = "qaJobCards1123cIdle")
	public void qaIdleBuilderAndMinerSayWhatTheChatSaid(GameTestHelper helper) {
		Setup s = start(helper, ModBlocks.BUILDERS_BENCH, ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER, "Mason");
		StatusCard builder = open(helper, s.player(), s.villager(), s.seen());
		helper.assertTrue(builder.subtitle().getString().startsWith("Mason has no building job. Hand them a placed blueprint."),
			"the idle builder's line: " + builder.subtitle().getString());
		helper.assertTrue(JobCardGameTests.lines(builder).get(0).startsWith("Experience: "), "an idle builder shows a site: " + JobCardGameTests.lines(builder));
		s.player().closeContainer();
		Villager gimli = employ(helper, ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER, "Gimli", new BlockPos(3, 2, 10));
		StatusCard miner = open(helper, s.player(), gimli, s.seen());
		helper.assertValueEqual(miner.tag().getString(), "Miner · Novice", "the miner's tag");
		helper.assertTrue(miner.subtitle().getString().startsWith("Gimli has no quarry. Hand them a marked Quarry Marker."),
			"the idle miner's line: " + miner.subtitle().getString());
		helper.assertTrue(JobCardGameTests.lines(miner).get(0).startsWith("Experience: "), "an idle miner shows a quarry: " + JobCardGameTests.lines(miner));
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * An Apprentice postman with 40 XP: "Experience: 40 / 70 XP" in green with a bar part-filled; "Needs: Nothing" in
	 * green. A Master: "Experience: Top level", green.
	 */
	//$ gametest_batch AREA '"qaJobCards1123cColors"'
	@GameTest(template = AREA, batch = "qaJobCards1123cColors")
	public void qaExperienceBarAndNeedsNothingAreGreen(GameTestHelper helper) {
		Setup s = start(helper, ModBlocks.MAILBOX, ModVillagers.MAILBOX_POI, ModVillagers.POSTMAN, "Pat");
		s.villager().setVillagerData(s.villager().getVillagerData().setLevel(2));
		s.villager().setVillagerXp(40);
		StatusCard card = open(helper, s.player(), s.villager(), s.seen());
		helper.assertValueEqual(card.tag().getString(), "Postman · Apprentice", "the tag");
		StatusCard.Line xp = card.lines().stream().filter(l -> l.label().getString().equals("Experience")).findFirst().orElse(null);
		helper.assertTrue(xp != null, "no experience line: " + JobCardGameTests.lines(card));
		helper.assertValueEqual(xp.value().getString(), "40 / 70 XP", "the experience value");
		helper.assertTrue(xp.color() == StatusCard.GREEN && xp.hasBar() && xp.bar() > 0f && xp.bar() < 1f,
			"the experience line isn't green with a part-filled bar: color " + Integer.toHexString(xp.color()) + ", bar " + xp.bar());
		StatusCard.Line needs = card.lines().get(card.lines().size() - 1);
		helper.assertValueEqual(needs.label().getString() + ": " + needs.value().getString(), "Needs: Nothing", "the last line");
		helper.assertTrue(needs.color() == StatusCard.GREEN, "Needs: Nothing isn't green: " + Integer.toHexString(needs.color()));
		s.player().closeContainer();
		s.villager().setVillagerData(s.villager().getVillagerData().setLevel(5));
		StatusCard master = open(helper, s.player(), s.villager(), s.seen());
		StatusCard.Line top = master.lines().stream().filter(l -> l.label().getString().equals("Experience")).findFirst().orElse(null);
		helper.assertTrue(top != null && top.value().getString().equals("Top level") && top.color() == StatusCard.GREEN,
			"a Master's experience line: " + JobCardGameTests.lines(master));
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * All seven jobs' cards, each with something to show: every word a player reads (title, tag, gray line, heading,
	 * each line's label and value, each button's label and reason, the hint) is resolved text, with no raw key, no %s or
	 * %d, and American spelling.
	 */
	//$ gametest_batch AREA '"qaJobCards1123cWords"'
	@GameTest(template = AREA, batch = "qaJobCards1123cWords")
	public void qaEveryCardsWordsAreResolvedAndAmerican(GameTestHelper helper) {
		Setup s = start(helper, ModBlocks.BUILDERS_BENCH, ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER, "Mason");
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, s.villager(), s.player(), TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(12, 1, 12)), Rotation.NONE, Mirror.NONE));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		List<Villager> all = new ArrayList<>();
		all.add(s.villager());
		all.add(employ(helper, ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER, "Gimli", new BlockPos(1, 2, 7)));
		Villager finn = employ(helper, Blocks.BARREL, PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN, "Finn", new BlockPos(1, 2, 10));
		ModAttachments.FISHER_JOB.set(finn, true);
		ModAttachments.FISH_CAUGHT.set(finn, 1);
		all.add(finn);
		BlockPos o = helper.absolutePos(new BlockPos(12, 1, 2));
		BoundingBox box = new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + 4, o.getY() + 1, o.getZ() + 3);
		Villager bram = employ(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER, "Bram", new BlockPos(5, 2, 7));
		ModAttachments.FARM_FIELD.set(bram, new FieldJob(box));
		all.add(bram);
		Villager jack = employ(helper, ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK, "Jack", new BlockPos(5, 2, 10));
		ModAttachments.TREE_FARM.set(jack, new FieldJob(box));
		all.add(jack);
		Villager pomona = employ(helper, ModBlocks.FRUIT_BASKET, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER, "Pomona", new BlockPos(1, 2, 14));
		ModAttachments.ORCHARD.set(pomona, new FieldJob(box, true));
		all.add(pomona);
		Villager pat = employ(helper, ModBlocks.MAILBOX, ModVillagers.MAILBOX_POI, ModVillagers.POSTMAN, "Pat", new BlockPos(5, 2, 14));
		ModAttachments.COURIER_ROUTES.set(pat, List.of(new RouteData(Optional.of(helper.absolutePos(new BlockPos(1, 2, 1))),
			Optional.of(helper.absolutePos(new BlockPos(1, 2, 8))), List.of())));
		all.add(pat);
		List<String> bad = new ArrayList<>();
		for (Villager v : all) {
			StatusCard card = open(helper, s.player(), v, s.seen());
			List<String> words = new ArrayList<>(List.of(card.title().getString(), card.tag().getString(), card.subtitle().getString(),
				card.heading().getString(), card.hint().getString()));
			words.addAll(JobCardGameTests.lines(card));
			for (StatusCard.Button b : card.buttons()) {
				words.add(b.label().getString());
				if (b.refusal() != null) {
					words.add(b.refusal().getString());
				}
			}
			for (String w : words) {
				String lower = w.toLowerCase(java.util.Locale.ROOT);
				if (w.contains("%s") || w.contains("%d") || w.matches(".*%\\d\\$.*") || w.contains("aliveworkplace.") || w.contains("{")
					|| lower.matches(".*\\b(colour|armour|grey|cancelled|defence|jeweller|centre|behaviour|neighbour)\\b.*")) {
					bad.add(v.getName().getString() + ": " + w);
				}
			}
			helper.assertTrue(card.lines().size() >= 2, v.getName().getString() + "'s card has too few lines: " + JobCardGameTests.lines(card));
			s.player().closeContainer();
		}
		helper.assertTrue(bad.isEmpty(), "unresolved or British words on the cards: " + bad);
		helper.succeed();
	}

	/** Every text key the page lists under New text (screen.aliveworkplace.card.job.*) is in the English language file, and none is left as %s alone. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryJobCardKeyTheWikiNamesExists(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (String k : List.of("heading", "percent", "skipped", "next", "quarry", "mined", "fish", "field", "tree_farm", "orchard", "size",
			"harvested", "felled", "planted", "picked", "delivered", "routes", "experience", "xp", "top_level", "needs", "needs_nothing", "need",
			"need_count", "need_more", "hall", "chat", "no_hall", "hint")) {
			String key = "screen.aliveworkplace.card.job." + k;
			if (!Language.getInstance().has(key) || Language.getInstance().getOrDefault(key).isBlank()) {
				missing.add(key);
			}
		}
		helper.assertTrue(missing.isEmpty(), "missing card text: " + missing);
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.card.job.need_more", 5).getString(), "and 5 more", "need_more");
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.card.job.percent", 42).getString(), "42%", "percent");
		helper.succeed();
	}

	/** A builder saved and loaded mid-build: the card still names his build with its progress and bar, and his level. */
	//$ gametest_batch AREA '"qaJobCards1123cReload"'
	@GameTest(template = AREA, batch = "qaJobCards1123cReload")
	public void qaBuilderCardAfterASaveAndReload(GameTestHelper helper) {
		Setup s = start(helper, ModBlocks.BUILDERS_BENCH, ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER, "Mason");
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, s.villager(), s.player(), TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(12, 1, 12)), Rotation.NONE, Mirror.NONE));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		s.villager().setVillagerData(s.villager().getVillagerData().setLevel(2));
		s.villager().setVillagerXp(40);
		List<String> before = JobCardGameTests.lines(open(helper, s.player(), s.villager(), s.seen()));
		s.player().closeContainer();
		net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
		s.villager().saveWithoutId(saved);
		s.villager().discard();
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(saved);
		helper.assertTrue(level.addFreshEntity(copy), "couldn't put the loaded builder back");
		StatusCard card = open(helper, s.player(), copy, s.seen());
		helper.assertValueEqual(card.tag().getString(), "Builder · Apprentice", "the tag after a reload");
		List<String> after = JobCardGameTests.lines(card);
		helper.assertValueEqual(after.get(0), Blueprints.siteName(level, site).getString() + ": 0%", "the build's line after a reload");
		helper.assertTrue(card.lines().get(0).hasBar(), "the build's line lost its bar");
		helper.assertValueEqual(after, before, "the card's lines before and after a reload");
		s.player().closeContainer();
		helper.succeed();
	}
}
