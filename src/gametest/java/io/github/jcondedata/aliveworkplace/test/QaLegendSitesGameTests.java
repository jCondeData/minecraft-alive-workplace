package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for 29.9, Legends found in the world, written from the spec (ROADMAP 29.9 and 29.3, docs/wiki/legends.md), on what
 * LegendSitesGameTests leaves open: the 5-second check on the real server tick (and not every tick); the 1,500-block
 * reach from both sides, straight and diagonal; the nearest of two halls; an unowned hall, no village, and a player who
 * only lists the owner as a friend; 29.3's slot rules (Rare per village, Legendary per world in any dimension, Mythic
 * caps) and unmet conditions; a found Legend whose slot fills before the morning; a cage's bars freeing only their own
 * prisoner; a Legend who walked off arriving the next morning; the {@code legendSites} switch through the config; the
 * site tags holding every vanilla variant; and every camp sentence, rendered in American English with its arguments.
 * Each test has its own batch; roster and clock changes are undone within the tick they are made.
 */
public class QaLegendSitesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final BlockPos MIDDLE = new BlockPos(15, 1, 15);
	private static final long DAY = VillageNeeds.DAY;
	private static final String VILLAGE = "Oakvale";

	private static Legend legend(String name, String rarity, String site, String conditions) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_found_" + name), JsonParser.parseString(
			"{\"rarity\": \"" + rarity + "\", \"job\": \"minecraft:cartographer\", \"title\": \"Found " + name + "\", \"lore\": \"the lore\","
				+ " \"names\": [\"Wren of the " + site + "\"], \"conditions\": [" + conditions + "],"
				+ " \"arrive\": [{\"way\": \"found\", \"site\": \"" + site + "\"}]}").getAsJsonObject());
	}

	private static Legend rare(String site) {
		return legend(site, "rare", site, "");
	}

	/** A hall named Oakvale, owned by {@code owner}, on the villages' list; everything the test made goes when it ends. */
	private static BlockPos hall(GameTestHelper helper, BlockPos rel, ServerPlayer owner) {
		helper.setBlock(rel, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(rel);
		if (owner != null) {
			entity.setOwner(owner.getUUID(), owner.getName().getString());
		}
		entity.setCustomName(Component.literal(VILLAGE));
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(rel);
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		Leftovers.after(helper, () -> {
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
			LegendPowers.forget();
		});
		return hall;
	}

	private static void staged(GameTestHelper helper, List<Legend> legends, Runnable body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		try {
			Map<ResourceLocation, Legend> map = new java.util.LinkedHashMap<>();
			legends.forEach(l -> map.put(l.id(), l));
			Legends.setForTest(map);
			body.run();
		} finally {
			level.setDayTime(time);
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static QaStageFiveGameTests.Client survivor(GameTestHelper helper, String name) {
		return QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name));
	}

	/** A 3 by 3, 5-tall anchor (a portal, a tower) standing on {@code at}. */
	private static BoundingBox box(BlockPos at) {
		return new BoundingBox(at.getX() - 1, at.getY(), at.getZ() - 1, at.getX() + 1, at.getY() + 4, at.getZ() + 1);
	}

	private static boolean freed(GameTestHelper helper, Villager v) {
		return LegendRecord.get(helper.getLevel()).captive(v.getUUID()).map(LegendRecord.Captive::freed).orElse(false);
	}

	private static Villager camp(GameTestHelper helper, String site, BlockPos hall, Legend legend, BlockPos ground) {
		Villager v = LegendSites.place(helper.getLevel(), LegendSites.site(site), new LegendSites.Match(hall, legend),
			helper.absolutePos(ground), RandomSource.create(299));
		helper.assertTrue(v != null, "the " + site + " camp is set down");
		return v;
	}

	// ---- English, from en_us.json; a key it doesn't carry renders as itself (as the game shows a made-up name) ----

	private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s");
	private static JsonObject lang;

	private static JsonObject lang() {
		if (lang == null) {
			try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
					.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				throw new GameTestAssertException("can't read en_us.json: " + e);
			}
		}
		return lang;
	}

	static String english(Component text) {
		StringBuilder out = new StringBuilder();
		if (text.getContents() instanceof TranslatableContents t) {
			String pattern = lang().has(t.getKey()) ? lang().get(t.getKey()).getAsString() : t.getKey();
			Object[] args = t.getArgs();
			Matcher m = ARG.matcher(pattern);
			int next = 0;
			StringBuilder sb = new StringBuilder();
			while (m.find()) {
				int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
				if (index >= args.length) {
					throw new GameTestAssertException(t.getKey() + " wants argument " + (index + 1) + " of " + args.length + ": \"" + pattern + "\"");
				}
				Object a = args[index];
				m.appendReplacement(sb, Matcher.quoteReplacement(a instanceof Component c ? english(c) : String.valueOf(a)));
			}
			m.appendTail(sb);
			out.append(sb);
		} else if (text.getContents() instanceof PlainTextContents p) {
			out.append(p.text());
		}
		for (Component sibling : text.getSiblings()) {
			out.append(english(sibling));
		}
		return out.toString();
	}

	private static String said(GameTestHelper helper, List<Component> seen, String key) {
		Component c = QaStageFourGameTests.find(seen, key);
		helper.assertTrue(c != null, "not told " + key + "; saw " + QaStageFourGameTests.keys(seen));
		return english(c);
	}

	private static void assertSaid(GameTestHelper helper, List<Component> seen, String key, String expected) {
		String got = said(helper, seen, key);
		helper.assertTrue(got.equals(expected), key + ":\n  got      \"" + got + "\"\n  expected \"" + expected + "\"");
		helper.assertFalse(got.toLowerCase(java.util.Locale.ROOT).contains("traveller"), "British spelling in " + key + ": " + got);
	}

	// ---- the tests ----

	/**
	 * "Every 5 seconds per player": on the real server tick, the lookups come in bursts exactly 100 ticks apart (never on
	 * the ticks between), each burst at least the three site lookups for the one player standing here; a spectator and
	 * a dead player are not looked up for.
	 */
	//$ gametest_ticks_batch AREA '260' '"qaLegendSitesClock"'
	@GameTest(template = AREA, timeoutTicks = 260, batch = "qaLegendSitesClock")
	public void sitesAreCheckedEveryFiveSecondsNotEveryTick(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = survivor(helper, "qa299clock");
		ServerPlayer p = c.player();
		helper.assertTrue(LegendSites.ENABLED && Legends.ENABLED, "legendSites and legends are on by default");
		helper.assertTrue(Legends.all().stream().anyMatch(l -> !l.ways("found").isEmpty()), "the shipped roster has a Legend found at a site");
		List<long[]> bursts = new ArrayList<>(); // {server tick, lookups added}
		long[] last = {LegendSites.lookups};
		helper.onEachTick(() -> {
			long now = LegendSites.lookups;
			if (now != last[0]) {
				bursts.add(new long[] {helper.getLevel().getServer().getTickCount(), now - last[0]});
				last[0] = now;
			}
		});
		helper.runAfterDelay(240, () -> {
			helper.assertTrue(bursts.size() >= 2, "at least two checks in 240 ticks: " + bursts.size());
			for (int i = 1; i < bursts.size(); i++) {
				helper.assertTrue(bursts.get(i)[0] - bursts.get(i - 1)[0] == LegendSites.EVERY,
					"checks " + LegendSites.EVERY + " ticks (5 seconds) apart: at " + bursts.stream().map(b -> b[0]).toList());
			}
			helper.assertTrue(LegendSites.EVERY == 100, "5 seconds is 100 ticks: " + LegendSites.EVERY);
			for (long[] b : bursts) {
				helper.assertTrue(b[1] >= LegendSites.SITES.size(), "each check asks about all three sites for the player here: " + b[1]);
			}
			staged(helper, List.of(rare("ruined_portal")), () -> {
				long before = LegendSites.lookups;
				LegendSites.scan(List.of(p));
				helper.assertTrue(LegendSites.lookups - before == 3, "three lookups for one player standing in no site: " + (LegendSites.lookups - before));
				p.setGameMode(GameType.SPECTATOR);
				before = LegendSites.lookups;
				LegendSites.scan(List.of(p));
				helper.assertTrue(LegendSites.lookups == before, "a spectator finds nothing and costs nothing");
				p.setGameMode(GameType.SURVIVAL);
				p.setHealth(0f);
				before = LegendSites.lookups;
				LegendSites.scan(List.of(p));
				helper.assertTrue(LegendSites.lookups == before, "a dead player costs nothing");
				p.setHealth(20f);
			});
			helper.succeed();
		});
	}

	/**
	 * "A Village Hall within 1,500 blocks": a hall exactly 1,500 blocks off (straight, or 1,060 by 1,060 diagonally) is
	 * within; 1,501 (or 1,061 by 1,061) isn't. The nearest of two qualifying halls gets the Legend. A hall nobody owns, a
	 * player with no village, and a player who only put the owner on their own friends' list don't qualify.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLegendSitesReach"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLegendSitesReach")
	public void theHallMustBeWithin1500BlocksAndTheirs(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		BlockPos a = hall(helper, HALL, owner);
		staged(helper, List.of(rare("ruined_portal")), () -> {
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", a.offset(1500, 0, 0)).isPresent(), "1500 blocks off is within reach");
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", a.offset(0, 0, -1500)).isPresent(), "1500 blocks north is within reach");
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", a.offset(1501, 0, 0)).isEmpty(), "1501 blocks off is beyond reach");
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", a.offset(1060, 0, 1060)).isPresent(), "1060 by 1060 (1499 blocks) is within");
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", a.offset(1061, 0, 1061)).isEmpty(), "1061 by 1061 (1500.5 blocks) is beyond");
			helper.assertTrue(LegendSites.qualifying(level, other, "ruined_portal", a.offset(10, 0, 0)).isEmpty(), "a player with no village of their own finds nothing");
			Friends friends = Friends.get(level.getServer());
			friends.add(other.getUUID(), owner.getUUID(), "owner");
			try {
				helper.assertTrue(LegendSites.qualifying(level, other, "ruined_portal", a.offset(10, 0, 0)).isEmpty(),
					"listing the owner as one's own friend doesn't make one the owner's friend");
			} finally {
				friends.remove(other.getUUID(), owner.getUUID());
			}
			helper.assertTrue(LegendSites.qualifying(level, owner, "outpost", a).isEmpty(), "no Legend names the outpost: nothing there");
		});
		BlockPos b = hall(helper, new BlockPos(26, 2, 26), owner);
		BlockPos unowned = hall(helper, new BlockPos(26, 2, 2), null);
		staged(helper, List.of(rare("ruined_portal")), () -> {
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", b.offset(3, 0, 3)).map(LegendSites.Match::hall).equals(Optional.of(b)),
				"the nearer of two halls gets the Legend: " + LegendSites.qualifying(level, owner, "ruined_portal", b.offset(3, 0, 3)));
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", a.offset(-3, 0, -3)).map(LegendSites.Match::hall).equals(Optional.of(a)),
				"and from the other side, the other hall");
			helper.assertTrue(LegendSites.qualifying(level, other, "ruined_portal", unowned.offset(1, 0, 1)).isEmpty(), "a hall nobody owns is nobody's");
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", unowned.offset(1, 0, 1)).map(LegendSites.Match::hall).filter(h -> !h.equals(unowned)).isPresent(),
				"standing by an unowned hall, the owner's own nearest hall gets it");
		});
		helper.succeed();
	}

	/**
	 * 29.3's slots decide whether a site holds a Legend: a Rare one already settled in this village blocks it here but
	 * not for another village; a Legendary one settled anywhere (even in the Nether) blocks it everywhere; a Mythic one
	 * needs a rank with room (a fresh hall is a Hamlet: 0); a Legend whose conditions the village doesn't meet isn't
	 * found. Once the Rare one's slot frees, the site holds them again.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLegendSitesSlots"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLegendSitesSlots")
	public void slotRulesDecideWhetherASiteHoldsALegend(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlockPos hall = hall(helper, HALL, owner);
		BlockPos elsewhere = hall.offset(400, 0, 0);
		LegendRecord record = LegendRecord.get(level);
		Legend rare = rare("ruined_portal");
		Legend legendary = legend("legendary", "legendary", "outpost", "");
		Legend mythic = legend("mythic", "mythic", "shipwreck", "");
		Legend picky = legend("picky", "rare", "ruined_portal", "{\"type\": \"job_level\", \"job\": \"minecraft:cartographer\", \"level\": 5, \"count\": 3}");
		staged(helper, List.of(rare), () -> {
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", hall).isPresent(), "a free Rare slot: found");
			UUID here = UUID.randomUUID();
			record.settled(rare.id(), here, Level.OVERWORLD, Optional.of(hall), Rarity.RARE, "Wren", 1);
			try {
				helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", hall).isEmpty(), "the Rare one already lives in this village");
			} finally {
				record.forget(here);
			}
			UUID there = UUID.randomUUID();
			record.settled(rare.id(), there, Level.OVERWORLD, Optional.of(elsewhere), Rarity.RARE, "Wren", 1);
			try {
				helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", hall).isPresent(), "a Rare one in another village doesn't block this one");
			} finally {
				record.forget(there);
			}
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", hall).isPresent(), "the slot freed: found again");
		});
		staged(helper, List.of(legendary), () -> {
			helper.assertTrue(LegendSites.qualifying(level, owner, "outpost", hall).isPresent(), "a free Legendary slot: found");
			UUID nether = UUID.randomUUID();
			record.settled(legendary.id(), nether, Level.NETHER, Optional.of(new BlockPos(10, 70, 10)), Rarity.LEGENDARY, "Wren", 1);
			try {
				helper.assertTrue(LegendSites.qualifying(level, owner, "outpost", hall).isEmpty(), "a Legendary one in the Nether blocks it in the overworld");
			} finally {
				record.forget(nether);
			}
		});
		staged(helper, List.of(mythic), () -> helper.assertTrue(LegendSites.qualifying(level, owner, "shipwreck", hall).isEmpty(),
			"a Mythic one never comes to a Hamlet (cap 0)"));
		staged(helper, List.of(picky), () -> helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", hall).isEmpty(),
			"a Legend whose conditions the village doesn't meet isn't found"));
		helper.succeed();
	}

	/**
	 * The traveller freed, then the Rare slot fills (the same Legend settles in the village another way) before the next
	 * morning: the freed one doesn't come as a guest (29.3: asked before any arrival), and is no longer counted as
	 * waiting, so once the slot frees the Legend may be found again.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLegendSitesRefused"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLegendSitesRefused")
	public void aFoundLegendWhoseSlotFillsMeanwhileDoesNotCome(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlockPos hall = hall(helper, HALL, owner);
		Legend legend = rare("ruined_portal");
		staged(helper, List.of(legend), () -> {
			Villager traveller = camp(helper, "ruined_portal", hall, legend, MIDDLE);
			LegendSites.use(owner, traveller, InteractionHand.MAIN_HAND);
			helper.assertTrue(freed(helper, traveller), "a word frees the traveller");
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", hall).isEmpty(), "not found twice while on their way");
			long day = Chronicle.day(level);
			UUID settled = UUID.randomUUID();
			LegendRecord.get(level).settled(legend.id(), settled, Level.OVERWORLD, Optional.of(hall), Rarity.RARE, "Wren", day);
			try {
				level.setDayTime(day * DAY + LegendGuests.MORNING_FROM + 100);
				LegendSites.arrive(level, hall);
				Villager guest = LegendGuests.guest(level, hall);
				helper.assertTrue(guest == null, "a second Rare one of a kind came to the village: " + (guest == null ? "" : guest.getName().getString()));
				helper.assertTrue(LegendSites.waitingFor(level, legend.id()).isEmpty(), "still counted as waiting, so never found again");
			} finally {
				LegendRecord.get(level).forget(settled);
			}
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", hall).isPresent(), "the slot free again: the Legend may be found again");
			((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setLegendGuests(LegendGuests.State.EMPTY);
		});
		helper.succeed();
	}

	/**
	 * Breaking iron bars frees only the prisoner in that cage: bars just outside the cage, and bars in a traveller's
	 * camp, free nobody; a cage block that isn't a bar frees nobody; the cage's own bar does, with the thanks sentence
	 * naming the village. Clicked first, the prisoner says the bars hold them (through the real click packet).
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLegendSitesBars"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLegendSitesBars")
	public void onlyTheCagesOwnBarsFreeThePrisoner(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = survivor(helper, "qa299bars");
		ServerPlayer p = c.player();
		BlockPos hall = hall(helper, HALL, p);
		Legend outpost = rare("outpost");
		Legend portal = rare("ruined_portal");
		staged(helper, List.of(outpost, portal), () -> {
			Villager prisoner = camp(helper, "outpost", hall, outpost, MIDDLE);
			Villager traveller = camp(helper, "ruined_portal", hall, portal, new BlockPos(6, 1, 25));
			LegendRecord.Captive cage = LegendRecord.get(level).captive(prisoner.getUUID()).orElseThrow();
			LegendRecord.Captive tent = LegendRecord.get(level).captive(traveller.getUUID()).orElseThrow();
			helper.assertFalse(cage.holds(traveller.blockPosition()) || tent.holds(prisoner.blockPosition()), "the two camps don't overlap: " + cage + " / " + tent);
			QaStageSixGameTests.clickVillager(p, prisoner, c.seen());
			assertSaid(helper, c.seen(), "message.aliveworkplace.legend_site.caged",
				"Wren of the outpost grips the bars: \"Break them, quickly!\"");
			helper.assertFalse(freed(helper, prisoner), "a click doesn't open the cage");
			// a bar just outside the cage
			BlockPos outside = new BlockPos(cage.max().getX() + 1, cage.min().getY() + 1, cage.min().getZ());
			level.setBlockAndUpdate(outside, Blocks.IRON_BARS.defaultBlockState());
			p.gameMode.destroyBlock(outside);
			helper.assertTrue(level.getBlockState(outside).isAir(), "the outside bar broke");
			helper.assertFalse(freed(helper, prisoner), "a bar outside the cage freed the prisoner");
			// a bar inside the traveller's camp
			BlockPos inTent = new BlockPos(tent.min().getX(), tent.min().getY() + 1, tent.min().getZ());
			level.setBlockAndUpdate(inTent, Blocks.IRON_BARS.defaultBlockState());
			p.gameMode.destroyBlock(inTent);
			helper.assertFalse(freed(helper, prisoner) || freed(helper, traveller), "a bar in the traveller's camp freed someone");
			// a cage block that isn't a bar
			BlockPos bar = null;
			BlockPos other = null;
			for (BlockPos q : BlockPos.betweenClosed(cage.min(), cage.max())) {
				var state = level.getBlockState(q);
				if (bar == null && state.is(Blocks.IRON_BARS)) {
					bar = q.immutable();
				} else if (other == null && !state.isAir() && !state.is(Blocks.IRON_BARS) && !q.equals(prisoner.blockPosition().below())
					&& q.getY() > cage.min().getY()) {
					other = q.immutable();
				}
			}
			helper.assertTrue(bar != null, "the cage has bars");
			if (other != null) {
				p.gameMode.destroyBlock(other);
				helper.assertFalse(freed(helper, prisoner), "breaking " + other + " (not a bar) freed the prisoner");
			}
			c.seen().clear();
			p.gameMode.destroyBlock(bar);
			helper.assertTrue(freed(helper, prisoner), "the cage's own bar frees the prisoner");
			assertSaid(helper, c.seen(), "message.aliveworkplace.legend_site.thanks.outpost",
				"Wren of the outpost: \"Free at last, thank you! I'll find my way to " + VILLAGE + " and be at your hall by morning.\"");
			helper.assertFalse(freed(helper, traveller), "the traveller is still waiting");
		});
		helper.succeed();
	}

	/**
	 * Every camp sentence a player reads, rendered with its real arguments in American English: found at a portal and an
	 * outpost (through LegendSites.offer as the per-player check calls it, the site marked used), the traveller's and the
	 * castaway's thanks naming the village, the castaway's hunger. A wreck with no beach near it holds no camp and stays
	 * unused.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLegendSitesWords"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLegendSitesWords")
	public void everyCampSentenceInPlainAmericanEnglish(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = survivor(helper, "qa299words");
		ServerPlayer p = c.player();
		BlockPos hall = hall(helper, HALL, p);
		String portalKey = "qa|minecraft:ruined_portal|" + hall.asLong();
		String outpostKey = "qa|minecraft:pillager_outpost|" + hall.asLong();
		String wreckKey = "qa|minecraft:shipwreck|" + hall.asLong();
		Leftovers.after(helper, () -> List.of(portalKey, outpostKey, wreckKey).forEach(LegendRecord.get(level)::forgetSite));
		// flat ground: each camp goes to the anchor's east side (6 blocks east of it), so the three camps stay apart
		BlockPos portalAt = helper.absolutePos(new BlockPos(6, 2, 6));
		BlockPos outpostAt = helper.absolutePos(new BlockPos(6, 2, 22));
		RandomSource random = RandomSource.create(299);
		staged(helper, List.of(rare("ruined_portal"), rare("outpost"), rare("shipwreck")), () -> {
			p.moveTo(portalAt.getX() + 0.5, portalAt.getY(), portalAt.getZ() + 0.5);
			Villager traveller = LegendSites.offer(level, p, "ruined_portal", portalKey, box(portalAt), box(portalAt), random);
			helper.assertTrue(traveller != null, "the traveler's camp is found");
			helper.assertTrue(LegendRecord.get(level).siteUsed(portalKey), "the portal is marked used");
			assertSaid(helper, c.seen(), "message.aliveworkplace.legend_site.found.ruined_portal",
				"Beside the ruined portal, a traveler has made camp: Wren of the ruined_portal. Talk to them.");
			p.moveTo(outpostAt.getX() + 0.5, outpostAt.getY(), outpostAt.getZ() + 0.5);
			Villager prisoner = LegendSites.offer(level, p, "outpost", outpostKey, box(outpostAt), box(outpostAt), random);
			helper.assertTrue(prisoner != null, "the prisoner's cage is found");
			helper.assertTrue(LegendRecord.get(level).siteUsed(outpostKey), "the outpost is marked used");
			assertSaid(helper, c.seen(), "message.aliveworkplace.legend_site.found.outpost",
				"At the foot of the outpost's tower, a prisoner rattles an iron cage: Wren of the outpost. Break the bars to free them.");
			int floor = helper.absolutePos(MIDDLE).getY();
			if (Math.abs(floor - level.getSeaLevel()) > 6) {
				BlockPos wreckAt = helper.absolutePos(new BlockPos(24, 2, 15));
				p.moveTo(wreckAt.getX() + 0.5, wreckAt.getY(), wreckAt.getZ() + 0.5);
				helper.assertTrue(LegendSites.offer(level, p, "shipwreck", wreckKey, box(wreckAt), box(wreckAt), random) == null,
					"no beach near this wreck (ground at " + floor + ", sea at " + level.getSeaLevel() + "): no camp");
				helper.assertFalse(LegendRecord.get(level).siteUsed(wreckKey), "a wreck with no camp stays unused, for later");
			}
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, traveller, c.seen());
			assertSaid(helper, c.seen(), "message.aliveworkplace.legend_site.thanks.ruined_portal",
				"Wren of the ruined_portal: \"Well met! I've heard of " + VILLAGE + ". I'll be at your Village Hall in the morning.\"");
			Villager castaway = camp(helper, "shipwreck", hall, rare("shipwreck"), new BlockPos(25, 1, 6));
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			assertSaid(helper, c.seen(), "message.aliveworkplace.legend_site.hungry",
				"Wren of the shipwreck is too weak to walk: \"Food... something cooked, please.\"");
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BAKED_POTATO, 2));
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertTrue(freed(helper, castaway), "a baked potato is a cooked meal");
			assertSaid(helper, c.seen(), "message.aliveworkplace.legend_site.thanks.shipwreck",
				"Wren of the shipwreck: \"A hot meal, at last! I owe you my life. Look for me at " + VILLAGE + "'s hall in the morning.\"");
		});
		helper.succeed();
	}

	/**
	 * {@code legendSites} off in the config: a player's check makes no lookups, and standing at a site sets no camp down
	 * and leaves the site unused (no word in chat). Turned back on, the same site holds the camp. The switch is flipped
	 * and put back within one tick, as other batches' Legend tests run beside this one.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLegendSitesSwitch"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLegendSitesSwitch")
	public void theLegendSitesSwitchStopsChecksAndCamps(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = survivor(helper, "qa299switch");
		ServerPlayer p = c.player();
		BlockPos hall = hall(helper, HALL, p);
		String key = "qa|minecraft:ruined_portal|switch" + hall.asLong();
		Leftovers.after(helper, () -> LegendRecord.get(level).forgetSite(key));
		helper.assertTrue(WorkplaceConfig.parse("{}").legendSites, "on by default");
		BlockPos at = helper.absolutePos(MIDDLE.offset(0, 1, 0));
		p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		staged(helper, List.of(rare("ruined_portal")), () -> {
			try {
				WorkplaceConfig.parse("{\"legendSites\": false}").apply();
				helper.assertFalse(LegendSites.ENABLED, "the config switch reaches the feature");
				long before = LegendSites.lookups;
				LegendSites.scan(List.of(p));
				helper.assertTrue(LegendSites.lookups == before, "lookups with legendSites off: " + (LegendSites.lookups - before));
				helper.assertTrue(LegendSites.offer(level, p, "ruined_portal", key, box(at), box(at), RandomSource.create(299)) == null, "a camp with legendSites off");
				helper.assertFalse(LegendRecord.get(level).siteUsed(key), "the site was used up while off");
				helper.assertFalse(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.legend_site.found.ruined_portal"), "told of a camp while off");
			} finally {
				new WorkplaceConfig().apply();
			}
			helper.assertTrue(LegendSites.ENABLED, "back on");
			helper.assertTrue(LegendSites.offer(level, p, "ruined_portal", key, box(at), box(at), RandomSource.create(299)) != null, "back on, the site holds the camp");
		});
		helper.succeed();
	}

	/**
	 * Freed, the traveller walks off and is gone once nobody is within sight; the record keeps them on their way through
	 * a save; that evening they're not at the hall yet; the next morning they're a guest there, found at the portal.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLegendSitesWalked"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLegendSitesWalked")
	public void aLegendWhoWalkedOffArrivesTheNextMorning(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlockPos hall = hall(helper, HALL, owner);
		Legend legend = rare("ruined_portal");
		staged(helper, List.of(legend), () -> {
			Villager traveller = camp(helper, "ruined_portal", hall, legend, MIDDLE);
			owner.moveTo(traveller.getX() + 1, traveller.getY(), traveller.getZ());
			LegendSites.use(owner, traveller, InteractionHand.MAIN_HAND);
			helper.assertTrue(freed(helper, traveller), "freed");
			// they have walked on, out of everyone's sight (other batches' players may stand near this area)
			owner.moveTo(traveller.getX() + 200, traveller.getY(), traveller.getZ());
			traveller.teleportTo(traveller.getX(), level.getMaxBuildHeight() - 4, traveller.getZ());
			LegendSites.walk(level.getServer());
			helper.assertTrue(traveller.isRemoved(), "out of everyone's sight, the traveler has walked off");
			LegendRecord record = LegendRecord.get(level);
			LegendRecord back = LegendRecord.load(record.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(back.captive(traveller.getUUID()).map(LegendRecord.Captive::freed).orElse(false), "on their way, through a save");
			long day = Chronicle.day(level);
			level.setDayTime((day - 1) * DAY + LegendGuests.EVENING + 500);
			LegendSites.arrive(level, hall);
			helper.assertTrue(LegendGuests.guest(level, hall) == null, "not at the hall the same evening");
			level.setDayTime(day * DAY + LegendGuests.MORNING_FROM);
			LegendSites.arrive(level, hall);
			Villager guest = LegendGuests.guest(level, hall);
			helper.assertTrue(guest != null, "a guest at the hall the next morning, though they walked off");
			LegendData data = ModAttachments.LEGEND.get(guest);
			helper.assertTrue(data != null && data.id().equals(legend.id()) && data.way().equals("found:ruined_portal"), "the found Legend: " + data);
			helper.assertTrue(LegendSites.waitingFor(level, legend.id()).isEmpty(), "off the captives' list");
			((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setLegendGuests(LegendGuests.State.EMPTY);
			guest.discard();
		});
		helper.succeed();
	}

	/**
	 * The three site tags hold every vanilla structure of their kind (every ruined portal, both shipwrecks, the pillager
	 * outpost), and no vanilla structure is in two of them (a pack adds its own variants to these tags).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSiteTagsHoldEveryVanillaVariant(GameTestHelper helper) {
		var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
		Map<String, List<String>> expected = Map.of(
			"ruined_portal", List.of("ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_swamp",
				"ruined_portal_mountain", "ruined_portal_ocean", "ruined_portal_nether"),
			"outpost", List.of("pillager_outpost"),
			"shipwreck", List.of("shipwreck", "shipwreck_beached"));
		List<String> seen = new ArrayList<>();
		for (String site : LegendSites.SITES) {
			var tag = LegendSites.site(site).tag();
			helper.assertTrue(tag.location().equals(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "legend_sites/" + site)),
				"the tag is #aliveworkplace:legend_sites/" + site + ": " + tag.location());
			var set = registry.getTag(tag).orElse(null);
			helper.assertTrue(set != null, "the tag " + tag.location() + " loads");
			List<String> in = set.stream().map(h -> h.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString).orElse("?")).toList();
			for (String id : expected.get(site)) {
				helper.assertTrue(in.contains("minecraft:" + id), site + " tag misses minecraft:" + id + ": " + in);
			}
			for (String id : in) {
				helper.assertFalse(seen.contains(id), id + " is in two site tags");
				seen.add(id);
			}
		}
		helper.assertTrue(LegendSites.SITES.size() == 3, "three sites: " + LegendSites.SITES);
		helper.succeed();
	}
}
