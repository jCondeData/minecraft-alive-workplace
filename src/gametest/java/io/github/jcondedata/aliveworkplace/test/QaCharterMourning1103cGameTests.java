package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * QA (lane c, 2026-11-03) verifying two fixes. B376 (30.17 Guild Charters): every rank boundary of "one guild per rank
 * above Hamlet", read through the player's real sneak-click with a charter: the exact refusal and grant lines, the
 * singular "1 guild" for a full Village. B380 (36.16 mourning): through the real death, real kin by id grieve (half
 * siblings, a renamed parent, a father, a partner) while namesake strangers with other ids don't; old records saved
 * without ids still match by a given name, as the fix decides.
 */
public class QaCharterMourning1103cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	// --- B376: the charter's rank boundaries, by the player's click -------------------------------------------------

	/**
	 * guilds.md: a Hamlet (the rank just below Village) refuses with the Hamlet line, the charter kept; raised to Village
	 * (the needed rank, with room) the same Master is chartered with the exact line and one charter used; a second Master
	 * of another trade in the now-full Village is refused "may have 1 guild" (singular), charter kept, nobody chartered.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaCm1103Village"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaCm1103Village")
	public void qaHamletRefusesVillageGrantsOnceThenSaysOneGuild(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager dara = QaStageSixGameTests.guildVillager(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		Villager bram = QaStageSixGameTests.guildVillager(helper, new BlockPos(8, 2, 6), "Bram", ModVillagers.MINER, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			String town = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
			QaStageSixGameTests.ready(hall, VillageRanks.Rank.HAMLET);
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCmV"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUILD_CHARTER, 3));

			said(helper, c, p, dara, "message.aliveworkplace.guild.hamlet", town + " is only a Hamlet: a village needs Village rank for a guild.");
			helper.assertTrue(p.getMainHandItem().getCount() == 3 && !ModAttachments.GUILD_MASTER.has(dara), "a Hamlet chartered Dara or took the charter");

			hall.setRank(VillageRanks.Rank.VILLAGE);
			String guild = Guilds.forTrade(dara).name().getString();
			said(helper, c, p, dara, "message.aliveworkplace.guild.chartered", "Dara is now the Guild Master of the " + guild + " in " + town + "!");
			helper.assertTrue(p.getMainHandItem().getCount() == 2 && ModAttachments.GUILD_MASTER.has(dara) && hall.guilds().size() == 1,
				"after the grant: " + p.getMainHandItem() + " guilds " + hall.guilds());

			said(helper, c, p, bram, "message.aliveworkplace.guild.full.one", town + " is a Village and may have 1 guild; it has them all.");
			helper.assertTrue(p.getMainHandItem().getCount() == 2 && !ModAttachments.GUILD_MASTER.has(bram) && hall.guilds().size() == 1,
				"a full Village took Bram's charter: " + p.getMainHandItem() + " guilds " + hall.guilds());
			helper.succeed();
		});
	}

	/**
	 * Above the needed rank: a Town takes two charters by click and refuses the third with "2 guilds"; raised to City the
	 * third is granted and the fourth refused with "3 guilds". Each refusal keeps the charter.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaCm1103TownCity"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaCm1103TownCity")
	public void qaTownHoldsTwoCityThreeByClick(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager dara = QaStageSixGameTests.guildVillager(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		Villager bram = QaStageSixGameTests.guildVillager(helper, new BlockPos(8, 2, 6), "Bram", ModVillagers.MINER, 5);
		Villager fenn = QaStageSixGameTests.guildVillager(helper, new BlockPos(10, 2, 6), "Fenn", ModVillagers.LUMBERJACK, 5);
		Villager gus = QaStageSixGameTests.guildVillager(helper, new BlockPos(12, 2, 6), "Gus", VillagerProfession.TOOLSMITH, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			String town = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
			QaStageSixGameTests.ready(hall, VillageRanks.Rank.TOWN);
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCmT"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUILD_CHARTER, 9));
			said(helper, c, p, dara, "message.aliveworkplace.guild.chartered", "Dara is now the Guild Master");
			said(helper, c, p, bram, "message.aliveworkplace.guild.chartered", "Bram is now the Guild Master");
			said(helper, c, p, fenn, "message.aliveworkplace.guild.full", town + " is a Town and may have 2 guilds; it has them all.");
			helper.assertTrue(p.getMainHandItem().getCount() == 7 && !ModAttachments.GUILD_MASTER.has(fenn), "a full Town took Fenn's charter");
			hall.setRank(VillageRanks.Rank.CITY);
			said(helper, c, p, fenn, "message.aliveworkplace.guild.chartered", "Fenn is now the Guild Master");
			said(helper, c, p, gus, "message.aliveworkplace.guild.full", town + " is a City and may have 3 guilds; it has them all.");
			helper.assertTrue(p.getMainHandItem().getCount() == 6 && hall.guilds().size() == 3 && !ModAttachments.GUILD_MASTER.has(gus),
				"after the City: " + p.getMainHandItem() + " guilds " + hall.guilds());
			helper.succeed();
		});
	}

	/**
	 * A Town with its two guilds that falls back to Village rank keeps them, and a new charter is refused in the singular
	 * for the Village's cap ("may have 1 guild"), though it holds two.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaCm1103Fallen"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaCm1103Fallen")
	public void qaATownFallenToVillageRefusesInTheSingular(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager dara = QaStageSixGameTests.guildVillager(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		Villager bram = QaStageSixGameTests.guildVillager(helper, new BlockPos(8, 2, 6), "Bram", ModVillagers.MINER, 5);
		Villager fenn = QaStageSixGameTests.guildVillager(helper, new BlockPos(10, 2, 6), "Fenn", ModVillagers.LUMBERJACK, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			String town = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
			QaStageSixGameTests.ready(hall, VillageRanks.Rank.TOWN);
			helper.assertTrue(Guilds.grant(level, dara).outcome() == Guilds.Outcome.GRANTED && Guilds.grant(level, bram).outcome() == Guilds.Outcome.GRANTED,
				"a Town's two");
			hall.setRank(VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCmF"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUILD_CHARTER, 1));
			said(helper, c, p, fenn, "message.aliveworkplace.guild.full.one", town + " is a Village and may have 1 guild; it has them all.");
			helper.assertTrue(p.getMainHandItem().getCount() == 1 && hall.guilds().size() == 2, "kept: " + p.getMainHandItem() + " guilds " + hall.guilds());
			helper.succeed();
		});
	}

	/** Clicks {@code v} with the charter and checks the line with {@code key} reads {@code text} (exactly, or as its start). */
	private static void said(GameTestHelper helper, QaStageFiveGameTests.Client c, ServerPlayer p, Villager v, String key, String text) {
		c.seen().clear();
		QaStageSixGameTests.clickVillager(p, v, c.seen());
		p.closeContainer();
		Component line = QaStageFourGameTests.find(c.seen(), key);
		helper.assertTrue(line != null, "clicking " + v.getName().getString() + ": no " + key + "; saw " + QaStageFourGameTests.keys(c.seen()));
		String got = line.getString();
		boolean whole = text.endsWith(".") || text.endsWith("!");
		helper.assertTrue(whole ? got.equals(text) : got.startsWith(text), "clicking " + v.getName().getString() + " said '" + got + "', expected '" + text + "'");
	}

	private static VillageHallBlockEntity village(GameTestHelper helper) {
		int perRank = Guilds.PER_RANK;
		Guilds.PER_RANK = 1;
		Leftovers.after(helper, () -> Guilds.PER_RANK = perRank);
		return QaStageSixGameTests.guildVillage(helper);
	}

	// --- B380: who mourns, through the real death -------------------------------------------------------------------

	/**
	 * Mira (mother Ada, father Tom, by id) dies. Her father Tom and her half-brother Pip (same mother's id, another
	 * father) grieve. Odo, whose parents are another Ada and another Tom (other ids), doesn't; nor does a living villager
	 * also named Ada who isn't her mother; nor Kit, whose mother is another Mira (her id isn't this Mira's).
	 */
	//$ gametest_ticks_batch BIG '100' '"qaCm1103Namesakes"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaCm1103Namesakes")
	public void qaNamesakesWithOtherIdsDontGrieveRealKinDo(GameTestHelper helper) {
		mourningVillage(helper);
		Villager mira = person(helper, new BlockPos(5, 2, 6), "Mira");
		Villager tom = person(helper, new BlockPos(7, 2, 6), "Tom");
		Villager pip = person(helper, new BlockPos(9, 2, 6), "Pip");
		Villager odo = person(helper, new BlockPos(5, 2, 8), "Odo");
		Villager ada = person(helper, new BlockPos(7, 2, 8), "Ada");
		Villager kit = person(helper, new BlockPos(9, 2, 8), "Kit");
		helper.runAfterDelay(5, () -> {
			long today = ready(helper, mira, tom, pip, odo, ada, kit);
			UUID realAda = UUID.randomUUID();
			ModAttachments.PARENTS.set(mira, parents("Ada", "Tom", realAda, tom.getUUID()));
			ModAttachments.PARENTS.set(pip, parents("Ada", "Bo", realAda, UUID.randomUUID()));
			ModAttachments.PARENTS.set(odo, parents("Ada", "Tom", UUID.randomUUID(), UUID.randomUUID()));
			ModAttachments.PARENTS.set(kit, parents("Mira", "Rob", UUID.randomUUID(), UUID.randomUUID()));
			mira.kill();
			grieves(helper, today, tom, "her father, by id");
			grieves(helper, today, pip, "her half-brother: the same mother's id");
			calm(helper, today, odo, "his Ada and Tom are other people (other ids)");
			calm(helper, today, ada, "a living Ada who isn't her mother (the record's id is someone else's)");
			calm(helper, today, kit, "Kit's mother is another Mira");
			helper.succeed();
		});
	}

	/**
	 * Bea was called Ada when Pip was born (his record says "Ada", with her id); renamed Bea, she dies: Pip still grieves.
	 * Odo's record names a "Bea" with another id: he doesn't.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaCm1103Renamed"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaCm1103Renamed")
	public void qaARenamedParentIsStillMournedByHerChild(GameTestHelper helper) {
		mourningVillage(helper);
		Villager bea = person(helper, new BlockPos(5, 2, 6), "Ada");
		Villager pip = person(helper, new BlockPos(7, 2, 6), "Pip");
		Villager odo = person(helper, new BlockPos(9, 2, 6), "Odo");
		helper.runAfterDelay(5, () -> {
			long today = ready(helper, bea, pip, odo);
			ModAttachments.PARENTS.set(pip, parents("Ada", "Tom", bea.getUUID(), UUID.randomUUID()));
			bea.setCustomName(Component.literal("Bea"));
			ModAttachments.PARENTS.set(odo, parents("Bea", "Tom", UUID.randomUUID(), UUID.randomUUID()));
			bea.kill();
			grieves(helper, today, pip, "his mother, renamed Bea, by id");
			calm(helper, today, odo, "his Bea is another villager (another id)");
			helper.succeed();
		});
	}

	/**
	 * Records saved before 34.21 have names only, and then a given name still counts (the fix's choice): Mira dies; Kit
	 * (mother "Mira", no id) grieves, as does Lin (mother "Ada", no ids, as on Mira's own old record), and Max, whose
	 * newer record has an Ada with an id that Mira's old record can't compare (names decide). Odo, mother "Ida", doesn't.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaCm1103OldRecords"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaCm1103OldRecords")
	public void qaOldRecordsWithoutIdsMatchByAGivenName(GameTestHelper helper) {
		mourningVillage(helper);
		Villager mira = person(helper, new BlockPos(5, 2, 6), "Mira");
		Villager kit = person(helper, new BlockPos(7, 2, 6), "Kit");
		Villager lin = person(helper, new BlockPos(9, 2, 6), "Lin");
		Villager max = person(helper, new BlockPos(5, 2, 8), "Max");
		Villager odo = person(helper, new BlockPos(7, 2, 8), "Odo");
		helper.runAfterDelay(5, () -> {
			long today = ready(helper, mira, kit, lin, max, odo);
			ModAttachments.PARENTS.set(mira, parents("Ada", "Tom", null, null));
			ModAttachments.PARENTS.set(kit, parents("Mira", "Rob", null, null));
			ModAttachments.PARENTS.set(lin, parents("Ada", "Ned", null, null));
			ModAttachments.PARENTS.set(max, parents("Ada", "Jon", UUID.randomUUID(), UUID.randomUUID()));
			ModAttachments.PARENTS.set(odo, parents("Ida", "Jon", null, null));
			mira.kill();
			grieves(helper, today, kit, "an old record's mother \"Mira\"");
			grieves(helper, today, lin, "both old records' mother \"Ada\"");
			grieves(helper, today, max, "Mira's old record has no id to compare, so the name decides");
			calm(helper, today, odo, "his old record's mother is Ida");
			helper.succeed();
		});
	}

	/**
	 * A couple: Mira dies married to Bo, who mourns (and works at the mourning pace) though no parent record ties them;
	 * Cy, with a record naming nobody in it, doesn't.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaCm1103Couple"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaCm1103Couple")
	public void qaAPartnerMourns(GameTestHelper helper) {
		mourningVillage(helper);
		Villager mira = person(helper, new BlockPos(5, 2, 6), "Mira");
		Villager bo = person(helper, new BlockPos(7, 2, 6), "Bo");
		Villager cy = person(helper, new BlockPos(9, 2, 6), "Cy");
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			long today = ready(helper, mira, bo, cy);
			ModAttachments.PARTNER.set(mira, new Couples.Partner(bo.getUUID(), bo.getDisplayName(), today - 10, true));
			ModAttachments.PARTNER.set(bo, new Couples.Partner(mira.getUUID(), mira.getDisplayName(), today - 10, true));
			ModAttachments.PARENTS.set(cy, parents("Bo", "Mira", UUID.randomUUID(), UUID.randomUUID()));
			mira.kill();
			helper.assertTrue(Mourning.mourning(level, bo) && Mourning.pace(bo) == Mourning.PACE, "Bo doesn't mourn his wife Mira");
			helper.assertTrue(!Mourning.mourning(level, cy) && Mourning.pace(cy) == 1f,
				"Cy mourns Mira: his record's \"Mira\" is someone else (" + ModAttachments.GRIEF.get(cy) + ")");
			helper.succeed();
		});
	}

	private static void mourningVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static long ready(GameTestHelper helper, Villager... all) {
		ServerLevel level = helper.getLevel();
		((VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL))).setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		long today = Chronicle.day(level);
		for (Villager v : all) {
			LifeStages.setAdultSince(v, today - 30);
		}
		return today;
	}

	private static Families.Parents parents(String mother, String father, UUID motherId, UUID fatherId) {
		return new Families.Parents(Component.literal(mother), Component.literal(father), "farmer", "farmer", true, false, false, motherId, fatherId);
	}

	private static void grieves(GameTestHelper helper, long today, Villager v, String why) {
		helper.assertTrue(Mourning.grieving(v, today) && Mourning.pace(v) == Mourning.PACE,
			v.getName().getString() + " doesn't grieve (" + why + "): " + ModAttachments.GRIEF.get(v));
	}

	private static void calm(GameTestHelper helper, long today, Villager v, String why) {
		helper.assertTrue(!Mourning.grieving(v, today) && Mourning.pace(v) == 1f,
			v.getName().getString() + " grieves a stranger (" + why + "): " + ModAttachments.GRIEF.get(v));
	}

	private static Villager person(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		return v;
	}
}
