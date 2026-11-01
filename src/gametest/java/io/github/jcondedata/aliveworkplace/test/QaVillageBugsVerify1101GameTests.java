package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.LegendsPage;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.test.QaPactsRealmEdictsGameTests.Two;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane C, 2026-11-01): verifying village bugs from their specs, past the cases each bug test checked.
 * B472: the Steward's raid condition lines read in the singular at one day, both forms. B589: the Founder halves a
 * colony wait but never rounds a real wait down to none. B595: "more than 2,048 blocks" means 2,048 itself is in range.
 * B630: a settled Legend counts as living here, a guest beside them doesn't, and one who is both counts once.
 */
public class QaVillageBugsVerify1101GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** B472: "Last raid: 1 day ago", "2 days ago"; never raided: "in the last 1 day" and "in the last 7 days". */
	//$ gametest_ticks_batch HUGE '100' '"qaRaidLineWords1101"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaRaidLineWords1101")
	public void qaTheRaidConditionReadsInTheSingularAtOneDay(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		BlockPos at = new BlockPos(3, 2, 3);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(at);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(at);
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		String never1 = new StewardConditions.RaidedWithin(1).test(facts).shown().getString();
		helper.assertTrue(never1.equals("Never raided (wants a raid in the last 1 day)"), "never raided, 1 day: " + never1);
		String never7 = new StewardConditions.RaidedWithin(7).test(facts).shown().getString();
		helper.assertTrue(never7.equals("Never raided (wants a raid in the last 7 days)"), "never raided, 7 days: " + never7);
		long today = Chronicle.day(level);
		entity.setLastRaidDay(today - 1);
		String one = new StewardConditions.RaidedWithin(7).test(StewardConditions.Facts.of(level, hall)).shown().getString();
		helper.assertTrue(one.equals("Last raid: 1 day ago (fewer than 7)"), "a raid yesterday: " + one);
		entity.setLastRaidDay(today - 2);
		String two = new StewardConditions.RaidedWithin(7).test(StewardConditions.Facts.of(level, hall)).shown().getString();
		helper.assertTrue(two.equals("Last raid: 2 days ago (fewer than 7)"), "a raid two days ago: " + two);
		helper.succeed();
	}

	/** B589 from both sides: with the Founder a 1-day wait stays 1 (never 0), 2 becomes 1, 7 becomes 4, 0 stays 0. */
	//$ gametest_ticks_batch HUGE '100' '"qaFounderHalves1101"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFounderHalves1101")
	public void qaTheFounderHalvesARealWaitButNeverToNone(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int cooldown = Colonies.COOLDOWN_DAYS;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			Colonies.COOLDOWN_DAYS = cooldown;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			RealmData.get(level.getServer()).clearColonies();
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> true)) {
				LegendRecord.get(level).forget(v.getUUID());
				v.discard();
			}
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		BlockPos at = new BlockPos(3, 2, 3);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(at)).setRank(VillageRanks.Rank.CITY);
		BlockPos hall = helper.absolutePos(at);
		int[] usual = {0, 1, 2, 7};
		for (int days : usual) {
			Colonies.COOLDOWN_DAYS = days;
			helper.assertTrue(Colonies.cooldownDays(level, hall) == days, "without the Founder a " + days + "-day wait is " + Colonies.cooldownDays(level, hall));
		}
		Legends.reload(level.getServer().getResourceManager());
		Legend founder = Legends.get(Founder.ID).orElseThrow();
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		map.put(Founder.ID, founder);
		Legends.setForTest(map);
		Villager him = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 6));
		him.setNoAi(true);
		Legends.make(level, him, founder, "test", Optional.of(hall));
		LegendPowers.forget();
		int[] halved = {0, 1, 1, 4};
		for (int i = 0; i < usual.length; i++) {
			Colonies.COOLDOWN_DAYS = usual[i];
			int got = Colonies.cooldownDays(level, hall);
			helper.assertTrue(got == halved[i], "with the Founder a " + usual[i] + "-day wait became " + got + ", wanted " + halved[i]);
		}
		helper.succeed();
	}

	/** B595's boundary: "more than 2,048 blocks" — a village exactly 2,048 off may be offered a pact, 2,049 is refused. */
	//$ gametest_ticks_batch HUGE '100' '"qaPactRangeEdge1101"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPactRangeEdge1101")
	public void qaAVillageExactlyAtCaravanRangeMayBeOfferedAPact(GameTestHelper helper) {
		Two two = QaPactsRealmEdictsGameTests.two(helper);
		Land land = two.land();
		ServerLevel level = land.level();
		BlockPos edge = land.hall(0).offset(Caravans.RANGE, 0, 0);
		BlockPos past = land.hall(0).offset(0, 0, Caravans.RANGE + 1);
		Caravans.Data caravans = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			caravans.remove(edge);
			caravans.remove(past);
		});
		neighbor(land, two, edge, "Edgeford");
		neighbor(land, two, past, "Pastmoor");
		Pacts.Result in = Pacts.offer(level, land.hall(0), edge, false, land.owner());
		helper.assertTrue(in.ok(), "a village exactly " + Caravans.RANGE + " blocks off was refused: " + in.message().getString());
		Pacts.Result out = Pacts.offer(level, land.hall(0), past, false, land.owner());
		helper.assertTrue(!out.ok() && out.message().getString().equals("Pastmoor is more than 2,048 blocks away, beyond caravan range."),
			"a village " + (Caravans.RANGE + 1) + " blocks off: " + out.message().getString());
		helper.succeed();
	}

	private static void neighbor(Land land, Two two, BlockPos pos, String name) {
		ServerLevel level = land.level();
		Caravans.Data.get(level).setWants(pos, Component.literal(name), List.of());
		land.data().putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), pos), Component.literal(name), Optional.of(two.them().getUUID()),
			"pact-neighbor", "village", 5, 50, level.getServer().overworld().getGameTime()));
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/**
	 * B630 from the other side: a Legend settled here counts as living here; a guest standing beside them doesn't (1);
	 * and a settled Legend whose data still says "guest" is counted once, not twice.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaLegendsLivingCount1101"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaLegendsLivingCount1101")
	public void qaASettledLegendCountsAndAGuestBesideThemDoesnt(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.after(helper, () -> {
			for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				LegendRecord.get(helper.getLevel()).forget(e.getUUID());
				e.discard();
			}
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		});
		BlockPos at = new BlockPos(15, 2, 15);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(at);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_settler_1101");
			ResourceLocation other = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_visitor_1101");
			Legend settler = Legends.read(id, JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\", \"title\": \"qa_settler\", \"lore\": \"l\"}")
				.getAsJsonObject());
			Legend visitor = Legends.read(other, JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\", \"title\": \"qa_visitor\", \"lore\": \"l\"}")
				.getAsJsonObject());
			Legends.setForTest(Map.of(id, settler, other, visitor));
			long today = Chronicle.day(level);
			Villager ada = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 14));
			ada.setNoAi(true);
			ada.setVillagerData(ada.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
			ada.setCustomName(Component.literal("Ada"));
			ModAttachments.LEGEND.set(ada, new LegendData(id, "", false, Optional.of(hall), today, -1, Map.of(), -1, -1, "settled", -1));
			LegendRecord.get(level).settled(id, ada.getUUID(), level.dimension(), Optional.of(hall), Rarity.RARE, "Ada", today);
			Villager gil = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 14));
			gil.setNoAi(true);
			gil.setVillagerData(gil.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
			gil.setCustomName(Component.literal("Gil"));
			ModAttachments.LEGEND.set(gil, new LegendData(other, "", true, Optional.empty(), 1, 9, Map.of(), -1, -1, "visit", -1));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			int tab = HallPages.slot(LegendsPage.PAGE);
			List<String> both = lore(VillageHallScreen.forTest(player, hall).icon(tab));
			helper.assertTrue(both.contains("Legends living here: 1"), "a settled Legend and a guest: " + both);
			// Ada's data says "guest" though she is on the record as settled here: still one Legend living here.
			ModAttachments.LEGEND.set(ada, new LegendData(id, "", true, Optional.of(hall), today, 9, Map.of(), -1, -1, "visit", -1));
			List<String> once = lore(VillageHallScreen.forTest(player, hall).icon(tab));
			helper.assertTrue(once.contains("Legends living here: 1"), "a settled Legend still marked a guest: " + once);
			helper.succeed();
		});
	}

	/**
	 * Whose side a hauler works for (B627, the nightly survivor at HaulerGolems.sideOf): only a Golem Smith, else a
	 * village worker. A jobless villager standing by the storehouse is neither: alone, nobody's side is taken; beside an
	 * Armorer, the Armorer's is. A baby is never the side either.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaHaulerSide1101"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaHaulerSide1101")
	public void qaAJoblessVillagerByTheStorehouseIsNobodysSide(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = io.github.jcondedata.aliveworkplace.work.Village.RADIUS; // 0 under GameTests
		io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 8;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.work.Village.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(15, 2, 15));
		level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(center).inflate(io.github.jcondedata.aliveworkplace.work.Village.RADIUS + 2))
			.forEach(Villager::discard);
		Villager idle = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 15));
		idle.setNoAi(true);
		idle.setVillagerData(idle.getVillagerData().setProfession(VillagerProfession.NONE));
		Villager baby = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 15));
		baby.setNoAi(true);
		baby.setAge(-24000);
		helper.runAfterDelay(2, () -> {
			int seen = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(center).inflate(io.github.jcondedata.aliveworkplace.work.Village.RADIUS)).size();
			helper.assertTrue(seen == 2, "setup: " + seen + " villagers by the storehouse, wanted the jobless one and the baby");
			Villager side = io.github.jcondedata.aliveworkplace.store.HaulerGolems.sideOf(level, center);
			helper.assertTrue(side == null, "a jobless villager and a baby by the storehouse were taken as the hauler's side: " + side);
			Villager armorer = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 17));
			armorer.setNoAi(true);
			armorer.setVillagerData(armorer.getVillagerData().setProfession(VillagerProfession.ARMORER).setLevel(2));
			helper.runAfterDelay(2, () -> {
				Villager got = io.github.jcondedata.aliveworkplace.store.HaulerGolems.sideOf(level, center);
				helper.assertTrue(got == armorer, "beside a jobless villager the Armorer wasn't the side: " + got + " (" + armorer.getVillagerData().getProfession() + ")");
				idle.discard();
				baby.discard();
				armorer.discard();
				helper.succeed();
			});
		});
	}
}
