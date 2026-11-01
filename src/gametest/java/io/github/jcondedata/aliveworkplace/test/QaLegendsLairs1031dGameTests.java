package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.AtPeace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSlots;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import static io.github.jcondedata.aliveworkplace.test.QaThreatLevels1031dGameTests.always;
import static io.github.jcondedata.aliveworkplace.test.QaThreatLevels1031dGameTests.at;
import static io.github.jcondedata.aliveworkplace.test.QaThreatLevels1031dGameTests.camp;
import static io.github.jcondedata.aliveworkplace.test.QaThreatLevels1031dGameTests.entity;
import static io.github.jcondedata.aliveworkplace.test.QaThreatLevels1031dGameTests.village;

/**
 * QA (lane d, 2026-10-31), written from the specs of three landed fixes:
 * <ul>
 *   <li>B504 (29.3): the Mythic refusal counts "1 Mythic Legend" when the rank's cap is 1, "Mythic Legends" otherwise
 *   (0, 2, 3), read through the operator's {@code /workplace legend make}, including a City fallen back to a Town that
 *   still holds 2 (the cap, not the count held, picks the form);</li>
 *   <li>B631 (29.4): every line that puts a Legend's "name, title" mid-sentence closes the title with a comma, with a
 *   real Legend's title ("Ada Stonewright, Master Architect, has downed tools."), and the "%s, the %s" announcements
 *   read as English;</li>
 *   <li>B728 (32.21): a camp set down by a quiet village stands until the next dawn: every round that day keeps it, the
 *   first round of the next day sends it away once (one chat line, one chronicle line), peace set on a later day counts
 *   from that day, peace lifted before dawn keeps the camp, and the same at threat level 0.</li>
 * </ul>
 */
public class QaLegendsLairs1031dGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos LEGEND_HALL = new BlockPos(15, 2, 15);
	private static final String LEFT_CHRONICLE = "packed up and left the village in peace";

	// ---------------------------------------------------------------- helpers

	private static CommandSourceStack op(ServerLevel level, BlockPos at, List<String> said) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		return new CommandSourceStack(out, Vec3.atCenterOf(at), Vec2.ZERO, level, 2, "qa", Component.literal("qa"), level.getServer(), null);
	}

	private static int run(ServerLevel level, CommandSourceStack source, String command) {
		try {
			return level.getServer().getCommands().getDispatcher().execute(command, source);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new IllegalStateException(command + ": " + e.getMessage());
		}
	}

	/** A logged-in player standing at {@code at} whose chat lines are kept in {@code heard}. */
	private static ServerPlayer listener(GameTestHelper helper, BlockPos at, List<String> heard) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "QaListener"), false);
		ServerPlayer p = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				if (!overlay) {
					heard.add(message.getString());
				}
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, p, cookie);
		Leftovers.after(helper, () -> server.getPlayerList().remove(p));
		p.setGameMode(GameType.CREATIVE);
		p.teleportTo(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0f, 0f);
		return p;
	}

	private static long count(List<String> lines, String part) {
		return lines.stream().filter(l -> l.contains(part)).count();
	}

	private static long chronicled(VillageHallBlockEntity entity, String part) {
		return entity.chronicle().stream().filter(e -> e.text().getString().contains(part)).count();
	}

	private static String leftLine(ServerLevel level, BlockPos hall, Lairs.Lair lair) {
		return "The raiders of the " + Lairs.lairName(lair.culture()).getString() + " have packed up and gone: "
			+ VillageHalls.name(level, hall).getString() + " is left in peace.";
	}

	private static UUID uuid(String seed) {
		return UUID.nameUUIDFromBytes(("qa-1031d-legend-" + seed).getBytes(StandardCharsets.UTF_8));
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** Forgets every Legend and record entry the test made, and puts the caps and switch back. */
	private static void legendCleanUp(GameTestHelper helper, List<UUID> made) {
		int[] caps = Legends.MYTHIC_CAP;
		Leftovers.after(helper, () -> {
			Legends.ENABLED = true;
			Legends.MYTHIC_CAP = caps;
			LegendRecord record = LegendRecord.get(helper.getLevel());
			made.forEach(record::forget);
			for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				record.forget(e.getUUID());
				e.discard();
			}
			LegendPowers.forget();
		});
	}

	private static Legend real(String path) {
		return Legends.get(AliveWorkplace.id(path)).orElseThrow(() -> new IllegalStateException("no Legend " + path + " in the real roster"));
	}

	// ---------------------------------------------------------------- B504: the Mythic refusal through the command

	/**
	 * 29.3: a village holds at most mythicLegendCap Mythic Legends for its rank (default Hamlet 0, Village 0, Town 1,
	 * City 2), and one that falls back keeps those it has. The operator's /workplace legend make reads the refusal:
	 * singular exactly when the cap is 1, plural for 0, 2 and a config cap of 3; a City fallen back to a Town that holds 2
	 * still reads "1 Mythic Legend" (the cap picks the form, not the count). Below the cap, the command makes the Legend.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1031dMythicCommand"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031dMythicCommand")
	public void mythicRefusalCountsTheCapThroughTheCommand(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		legendCleanUp(helper, made);
		helper.setBlock(LEGEND_HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(LEGEND_HALL);
			VillageHallBlockEntity entity = entity(helper, hall);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			Legends.ENABLED = true;
			Legends.MYTHIC_CAP = new WorkplaceConfig().mythicLegendCap.stream().mapToInt(Integer::intValue).toArray();
			Legend founder = real("founder");
			helper.assertTrue(founder.rarity() == Rarity.MYTHIC, "the Founder isn't Mythic");
			Villager cy = villager(helper, LEGEND_HALL.offset(0, 0, 3), "Cy");
			List<String> said = new ArrayList<>();
			CommandSourceStack source = op(level, cy.blockPosition(), said);
			String cmd = "workplace legend make aliveworkplace:founder";
			LegendRecord record = LegendRecord.get(level);

			Map<String, String> wanted = new LinkedHashMap<>();
			entity.setRank(VillageRanks.Rank.HAMLET);
			said.clear();
			int r = run(level, source, cmd);
			wanted.put("Hamlet, cap 0", "A Hamlet can hold 0 Mythic Legends, and this one has 0.");
			helper.assertTrue(r == 0 && said.equals(List.of(wanted.get("Hamlet, cap 0"))), "Hamlet refusal: " + r + " " + said);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			said.clear();
			r = run(level, source, cmd);
			helper.assertTrue(r == 0 && said.equals(List.of("A Village can hold 0 Mythic Legends, and this one has 0.")), "Village refusal: " + r + " " + said);
			helper.assertTrue(!ModAttachments.LEGEND.has(cy), "a refused Mythic was made anyway");

			// Town, cap 1, holding 1: singular
			entity.setRank(VillageRanks.Rank.TOWN);
			UUID one = uuid("one");
			made.add(one);
			record.settled(founder.id(), one, level.dimension(), Optional.of(hall), Rarity.MYTHIC, "Di", 1);
			said.clear();
			r = run(level, source, cmd);
			helper.assertTrue(r == 0 && said.equals(List.of("A Town can hold 1 Mythic Legend, and this one has 1.")), "Town refusal (B504): " + r + " " + said);

			// City, cap 2, holding 2: plural
			entity.setRank(VillageRanks.Rank.CITY);
			UUID two = uuid("two");
			made.add(two);
			record.settled(founder.id(), two, level.dimension(), Optional.of(hall), Rarity.MYTHIC, "Ed", 1);
			said.clear();
			r = run(level, source, cmd);
			helper.assertTrue(r == 0 && said.equals(List.of("A City can hold 2 Mythic Legends, and this one has 2.")), "City refusal: " + r + " " + said);

			// Fell back to a Town, keeps both: the cap (1) picks the singular, though it holds 2
			entity.setRank(VillageRanks.Rank.TOWN);
			said.clear();
			r = run(level, source, cmd);
			helper.assertTrue(r == 0 && said.equals(List.of("A Town can hold 1 Mythic Legend, and this one has 2.")), "fallen-back Town refusal: " + r + " " + said);

			// A config cap of 3 for a City, holding 3: plural
			Legends.MYTHIC_CAP = WorkplaceConfig.parse("{\"mythicLegendCap\": [0, 0, 1, 3]}").mythicLegendCap.stream().mapToInt(Integer::intValue).toArray();
			entity.setRank(VillageRanks.Rank.CITY);
			UUID three = uuid("three");
			made.add(three);
			record.settled(founder.id(), three, level.dimension(), Optional.of(hall), Rarity.MYTHIC, "Fa", 1);
			said.clear();
			r = run(level, source, cmd);
			helper.assertTrue(r == 0 && said.equals(List.of("A City can hold 3 Mythic Legends, and this one has 3.")), "City cap 3 refusal: " + r + " " + said);
			helper.assertTrue(!ModAttachments.LEGEND.has(cy), "a refused Mythic was made anyway");

			// A config cap of 1 for a City: singular again, whatever the rank
			Legends.MYTHIC_CAP = WorkplaceConfig.parse("{\"mythicLegendCap\": [0, 0, 1, 1]}").mythicLegendCap.stream().mapToInt(Integer::intValue).toArray();
			said.clear();
			r = run(level, source, cmd);
			helper.assertTrue(r == 0 && said.equals(List.of("A City can hold 1 Mythic Legend, and this one has 3.")), "City cap 1 refusal: " + r + " " + said);

			// Room again (the record forgets two): the command makes the Founder
			record.forget(two);
			record.forget(three);
			record.forget(one);
			said.clear();
			r = run(level, source, cmd);
			helper.assertTrue(r == 1 && ModAttachments.LEGEND.has(cy), "with room, the command didn't make the Mythic: " + r + " " + said);
			helper.assertTrue(said.stream().noneMatch(s -> s.contains("can hold")), "a refusal with room: " + said);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- B631: Legend lines with a real title

	/**
	 * 29.4 chatter (B631): with real Legends from the roster, every Legend topic a villager speaks reads as English: the
	 * strike and guest lines close the title with a comma, the "among us" and guest lines end the name and title cleanly,
	 * and the Legend speaks of their own title. Exact sentences.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1031dLegendChatter"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1031dLegendChatter")
	public void legendChatterReadsAsEnglishWithRealTitles(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		legendCleanUp(helper, made);
		helper.setBlock(LEGEND_HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(LEGEND_HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			Legends.ENABLED = true;
			Villager bo = villager(helper, new BlockPos(13, 2, 13), "Bo");
			Villager ada = villager(helper, new BlockPos(16, 2, 17), "Ada");
			Legends.make(level, ada, real("master_architect"), "test");
			ada.setCustomName(Component.literal("Ada Stonewright"));
			LegendData a = ModAttachments.LEGEND.get(ada);
			ModAttachments.LEGEND.set(ada, new LegendData(a.id(), a.name(), false, a.hall(), a.since(), a.lastDay(), Map.of("luxury", 3), 6, a.lastLuxury(), a.way()));
			Villager gil = villager(helper, new BlockPos(18, 2, 17), "Gil Saltmarsh");
			ModAttachments.LEGEND.set(gil, new LegendData(real("grand_chef").id(), "", true, Optional.empty(), 1, 9, Map.of(), -1, -1, "visit"));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Map<String, String> said = new LinkedHashMap<>();
			for (int i = 0; i < 4000 && said.size() < 4; i++) {
				Component line = Chatter.line(level, bo, hall, player);
				if (line != null && line.getContents() instanceof TranslatableContents t && t.getKey().startsWith("chatter.aliveworkplace.legend_")) {
					said.putIfAbsent(t.getKey(), line.getString());
				}
			}
			Map<String, String> wanted = new LinkedHashMap<>();
			wanted.put("chatter.aliveworkplace.legend_strike.0", "Ada Stonewright, Master Architect, has downed tools. They won't work till they get what they want.");
			wanted.put("chatter.aliveworkplace.legend_guest.0", "Gil Saltmarsh, Grand Chef, is staying with us! Do you think they'll settle?");
			wanted.put("chatter.aliveworkplace.legend_guest.1", "A Legend as our guest: Gil Saltmarsh, Grand Chef. Let's give them everything they need.");
			for (Map.Entry<String, String> w : wanted.entrySet()) {
				helper.assertTrue(w.getValue().equals(said.get(w.getKey())), w.getKey() + ": \"" + said.get(w.getKey()) + "\", wanted \"" + w.getValue() + "\"");
			}
			for (String line : said.values()) {
				helper.assertTrue(!line.contains(",,") && !line.contains(" ,") && !line.contains("%"), "a broken Legend line: " + line);
			}

			// Ada's strike ends: Bo speaks of a Legend among us, in both lines
			LegendData s = ModAttachments.LEGEND.get(ada);
			ModAttachments.LEGEND.set(ada, new LegendData(s.id(), s.name(), false, s.hall(), s.since(), s.lastDay(), Map.of(), -1, s.lastLuxury(), s.way()));
			gil.discard();
			Map<String, String> here = new LinkedHashMap<>();
			String self = null;
			for (int i = 0; i < 4000 && (here.size() < 2 || self == null); i++) {
				Component line = Chatter.line(level, bo, hall, player);
				if (line != null && line.getContents() instanceof TranslatableContents t && t.getKey().startsWith("chatter.aliveworkplace.legend_here")) {
					here.putIfAbsent(t.getKey(), line.getString());
				}
				Component own = self == null ? Chatter.line(level, ada, hall, player) : null;
				if (own != null && own.getContents() instanceof TranslatableContents t && t.getKey().equals("chatter.aliveworkplace.legend_self.0")) {
					self = own.getString();
				}
			}
			helper.assertTrue("We have a Legend among us: Ada Stonewright, Master Architect!".equals(here.get("chatter.aliveworkplace.legend_here.0")),
				"legend_here.0: " + here.get("chatter.aliveworkplace.legend_here.0"));
			helper.assertTrue("Have you met Ada Stonewright, Master Architect? The whole village is proud.".equals(here.get("chatter.aliveworkplace.legend_here.1")),
				"legend_here.1: " + here.get("chatter.aliveworkplace.legend_here.1"));
			helper.assertTrue("Yes, I am the Master Architect. Don't let it go to your head; I try not to.".equals(self), "legend_self.0: " + self);
			helper.succeed();
		});
	}

	/**
	 * 29.3 announcements (the "%s, the %s" lines B631's comma rule covers): a Rare guest and settler read with commas round
	 * "the title", the Mythic one opens "A Mythic Legend! Name, the Founder, has come to ..." and the chronicle line
	 * written when a Legend is announced reads the same way. Exact sentences.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1031dLegendAnnounce"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031dLegendAnnounce")
	public void legendAnnouncementsPutCommasRoundTheTitle(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		legendCleanUp(helper, made);
		helper.setBlock(LEGEND_HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(LEGEND_HALL);
			VillageHallBlockEntity entity = entity(helper, hall);
			String village = VillageHalls.name(level, hall).getString();
			Component ada = Component.literal("Ada Stonewright");
			Legend chef = real("grand_chef");
			Legend founder = real("founder");
			String guest = LegendSlots.message(level, hall, hall, ada, chef, true).getString();
			helper.assertTrue(guest.equals("Ada Stonewright, the Grand Chef (a Rare Legend), has come to " + village + " as a guest."), "Rare guest: " + guest);
			String settled = LegendSlots.message(level, hall, hall, ada, chef, false).getString();
			helper.assertTrue(settled.equals("Ada Stonewright, the Grand Chef (a Rare Legend), has settled in " + village + "."), "Rare settled: " + settled);
			String mythic = LegendSlots.message(level, hall, hall, ada, founder, true).getString();
			helper.assertTrue(mythic.startsWith("A Mythic Legend! Ada Stonewright, the Founder, has come to " + village + " as a guest, ")
				&& mythic.matches(".* \\d+ blocks [a-z -]+ of spawn\\.") && !mythic.contains("%"), "Mythic guest: " + mythic);

			Villager v = villager(helper, LEGEND_HALL.offset(2, 0, 2), "Ada Stonewright");
			made.add(v.getUUID());
			LegendSlots.announce(level, hall, v, chef, false);
			List<Chronicle.Entry> chronicle = entity.chronicle();
			String last = chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
			helper.assertTrue(last.endsWith("Ada Stonewright, the Grand Chef (a Rare Legend), settled in the village"), "the chronicle: " + last);
			v.discard();
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- B728: a quiet village's camp stands until dawn

	/**
	 * At peace since day 70, a camp set down on day 75 stands through every hall round that day (morning to the last tick),
	 * leaves at day 76's first round, and leaves once: one chat line to the player in the village, one chronicle line, and
	 * no more on the later rounds.
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1031dQuietSameDayRounds"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1031dQuietSameDayRounds")
	public void everyRoundOnTheDayKeepsTheCampAndTheDawnSendsItOnce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		List<String> heard = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			listener(helper, hall, heard);
			at(level, 70, 6000);
			entity.setAtPeace(true, 70);
			at(level, 75, 1000);
			Lairs.Lair lair = camp(helper, hall);
			long before = chronicled(entity, LEFT_CHRONICLE);
			for (long time : new long[] {1000, 1600, 6000, 12000, 18000, 23400, 23999}) {
				at(level, 75, time);
				Lairs.round(level, hall, true, always());
				helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp set down on day 75 left at the round at tick " + time + " of day 75");
			}
			helper.assertTrue(count(heard, "packed up and gone") == 0, "a 'left' line on day 75: " + heard);
			at(level, 76, 0);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands at day 76's first round");
			for (long time : new long[] {600, 6000, 23999}) {
				at(level, 76, time);
				Lairs.round(level, hall, true, always());
			}
			at(level, 77, 0);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a camp came back to a village at peace");
			String line = leftLine(level, hall, lair);
			helper.assertTrue(count(heard, "packed up and gone") == 1 && heard.contains(line), "the 'left' chat line, wanted once \"" + line + "\": " + heard);
			helper.assertTrue(chronicled(entity, LEFT_CHRONICLE) - before == 1, "the chronicle's 'left' line, wanted once: " + (chronicled(entity, LEFT_CHRONICLE) - before));
			helper.succeed();
		});
	}

	/** Set down at the very end of day 75 (tick 23990) by a village at peace since day 70: it leaves at day 76's first round, once. */
	//$ gametest_ticks_batch BIG '60' '"qa1031dQuietLateCamp"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1031dQuietLateCamp")
	public void aCampSetDownLateLeavesAtTheVeryNextDawnOnce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		List<String> heard = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			listener(helper, hall, heard);
			at(level, 70, 6000);
			entity.setAtPeace(true, 70);
			at(level, 75, 23990);
			Lairs.Lair lair = camp(helper, hall);
			helper.assertTrue(lair.day() == 75, "the camp's day: " + lair.day());
			long before = chronicled(entity, LEFT_CHRONICLE);
			Lairs.round(level, hall, true, always());
			at(level, 75, 23999);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp set down at tick 23990 of day 75 left before the day turned");
			at(level, 76, 1);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the late camp still stands at day 76's first round (it should leave at the next dawn, not a day later)");
			at(level, 76, 601);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(count(heard, "packed up and gone") == 1 && heard.contains(leftLine(level, hall, lair)), "the 'left' chat line, wanted once: " + heard);
			helper.assertTrue(chronicled(entity, LEFT_CHRONICLE) - before == 1, "the chronicle's 'left' line, wanted once");
			helper.succeed();
		});
	}

	/**
	 * A camp set down on day 75 by a village that isn't quiet; the owner sets it At peace on day 77 (through the Defence
	 * page's toggle). Peace counts from day 77: the camp stands all of day 77 and leaves at day 78's dawn, not at once.
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1031dPeaceTwoDaysLater"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1031dPeaceTwoDaysLater")
	public void peaceSetTwoDaysAfterTheCampSendsItTheDawnAfterPeace(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(owner));
		helper.runAfterDelay(2, () -> {
			at(level, 75, 6000);
			camp(helper, hall);
			Lairs.round(level, hall, true, always());
			at(level, 76, 6000);
			Lairs.round(level, hall, true, always());
			at(level, 77, 6000);
			AtPeace.toggle(level, hall, owner);
			helper.assertTrue(entity.atPeace() && entity.peaceSince() == 77, "not at peace since day 77: " + entity.atPeace() + " " + entity.peaceSince());
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left on day 77, the day peace was set (the camp's day 75 counted?)");
			at(level, 77, 23999);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left before day 77 was over");
			at(level, 78, 0);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands at day 78's dawn, the first after peace");
			helper.succeed();
		});
	}

	/**
	 * At peace since day 70, a camp set down on day 75; the owner lifts peace before the day is over. The camp no longer
	 * leaves: it stands at day 76's dawn and on day 77.
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1031dPeaceLiftedLateCamp"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1031dPeaceLiftedLateCamp")
	public void peaceLiftedBeforeDawnKeepsACampSetDownAfterPeace(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(owner));
		helper.runAfterDelay(2, () -> {
			at(level, 70, 6000);
			entity.setAtPeace(true, 70);
			at(level, 75, 6000);
			camp(helper, hall);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left on the day it was set down");
			at(level, 75, 23995);
			AtPeace.toggle(level, hall, owner);
			helper.assertTrue(!entity.atPeace(), "peace not lifted");
			at(level, 76, 0);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left at dawn though peace was lifted before it");
			at(level, 77, 6000);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left on day 77 with the village no longer at peace");
			helper.succeed();
		});
	}

	/**
	 * Threat level 0 (quiet since an earlier camp left on day 81): a camp set down on day 85 stands through every round of
	 * day 85 and leaves at day 86's dawn. Level raised before a dawn keeps a camp; set back to 0 on day 91, the village is
	 * quiet from day 91, so a camp from day 90 stands all of day 91 and leaves at day 92's dawn. One 'left' line per camp.
	 */
	//$ gametest_ticks_batch BIG '80' '"qa1031dLevelZeroCamps"'
	@GameTest(template = BIG, timeoutTicks = 80, batch = "qa1031dLevelZeroCamps")
	public void atLevelZeroACampSetDownLaterStandsUntilTheNextDawn(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = entity(helper, hall);
		List<String> heard = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			listener(helper, hall, heard);
			WorkplaceConfig.parse("{\"threatLevel\": 1}").apply();
			at(level, 79, 6000);
			Lairs.round(level, hall, true, always()); // not quiet: no level-0 day remembered from an earlier test
			long before = chronicled(entity, LEFT_CHRONICLE);

			// the first camp, standing when the level goes to 0, leaves at the next dawn
			at(level, 80, 13000);
			camp(helper, hall);
			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the first camp left on day 80, the day the level went to 0");
			at(level, 81, 100);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the first camp didn't leave at day 81's dawn at level 0");

			// a camp set down on day 85 at level 0: every round of day 85 keeps it, day 86's first sends it
			at(level, 85, 6000);
			Lairs.round(level, hall, true, always());
			camp(helper, hall);
			for (long time : new long[] {6000, 6600, 12000, 23999}) {
				at(level, 85, time);
				Lairs.round(level, hall, true, always());
				helper.assertTrue(Lairs.near(level, hall).isPresent(), "at level 0, the camp set down on day 85 left at tick " + time + " of day 85");
			}
			at(level, 86, 0);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "at level 0, the camp from day 85 still stands at day 86's dawn");
			at(level, 86, 600);
			Lairs.round(level, hall, true, always());

			// a camp from day 90; the level goes up before dawn and back to 0 on day 91
			at(level, 90, 6000);
			camp(helper, hall);
			Lairs.round(level, hall, true, always());
			at(level, 90, 20000);
			WorkplaceConfig.parse("{\"threatLevel\": 1}").apply();
			Lairs.round(level, hall, true, always());
			at(level, 91, 0);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left at dawn though the level was raised before it");
			at(level, 91, 6000);
			WorkplaceConfig.parse("{\"threatLevel\": 0}").apply();
			Lairs.round(level, hall, true, always());
			at(level, 91, 23999);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left on day 91, the day the level went back to 0 (an old level-0 day counted?)");
			at(level, 92, 0);
			Lairs.round(level, hall, true, always());
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands at day 92's dawn, the first after the level went back to 0");
			helper.assertTrue(count(heard, "packed up and gone") == 3, "wanted one 'left' line per camp (3): " + heard);
			helper.assertTrue(chronicled(entity, LEFT_CHRONICLE) - before == 3, "wanted 3 chronicle 'left' lines: " + (chronicled(entity, LEFT_CHRONICLE) - before));
			helper.succeed();
		});
	}
}
