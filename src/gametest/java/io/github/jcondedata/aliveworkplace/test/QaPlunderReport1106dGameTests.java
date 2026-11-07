package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Plunder;
import io.github.jcondedata.aliveworkplace.threat.SiegeReport;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-11-06): a mutation-driven gap hunt in {@code threat/Plunder} and {@code threat/SiegeReport}, tested
 * from ROADMAP 32.10 and 32.6 and the wiki pages piglins.md and sieges.md.
 * <ul>
 * <li>(32.10, "shown on its head when that is bare") a plunderer in an iron helmet takes the gold but keeps its helmet,
 *     shows nothing, and still wears the helmet after carrying the gold home at dawn; a bare-headed one shows one ingot,
 *     and its head is bare again once home;</li>
 * <li>(32.10, "carrying nothing yet") a piglin already carrying a few nuggets passes a second chest of gold and leaves
 *     it whole;</li>
 * <li>(piglins.md, "a piglin (not a hoglin)") a raiding hoglin ram beside a chest of gold takes nothing, while a piglin
 *     of the same raid beside another chest takes its gold;</li>
 * <li>(32.10, "a raider of a culture that names it") a raider of a culture without {@code plunder} beside a chest of
 *     gold takes nothing;</li>
 * <li>(32.10, "the siege report says it the morning after, or the chronicle when there was no siege") with a siege the
 *     raid's end leaves the count to the morning, the report tells the 12 gold once and spends the count; without a
 *     siege the raid's end tells it in the chronicle at once;</li>
 * <li>(32.6 and sieges.md's example) the report's sentence with real counts and plurals, rendered from en_us.json: one
 *     raider, a broken gate, a hero with 2 kills and one with 1 kill, more fallen than counted as come;</li>
 * <li>(sieges.md, "every death of a raider within the village's radius plus 64 blocks of a hall under siege ... to a
 *     guard and which, to a player, or to anything else") real deaths: a guard's two kills, a plain villager's and a
 *     player's; an untagged husk not counted; a siege too far off not counting a death until the village grows; and the
 *     nearest siege of two counting it.</li>
 * </ul>
 */
public class QaPlunderReport1106dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final ResourceLocation NO_PLUNDER = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_no_plunder_1106d");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final String REPORT_END = " gold was carried off from the village's chests.";

	private static Culture piglins(GameTestHelper helper) {
		Optional<Culture> found = Threats.get(PIGLINS);
		helper.assertTrue(found.isPresent(), "setup: expected piglin_incursion.json loaded");
		return found.get();
	}

	/** A village round {@link #HALL} of radius 20 (no hall block), everything put back when the test ends. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPlunder(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			Moods.forget();
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		ThreatData.get(level).forgetPlunder(hall);
		ThreatData.get(level).forgetSiege(hall);
		ThreatData.get(level).forgetPast(hall);
		return hall;
	}

	/** A raider of {@code culture}'s roster line for {@code entity}, as a raid makes it, standing still at {@code at}. */
	private static Mob raider(GameTestHelper helper, Culture culture, EntityType<?> entity, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(entity))).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "setup: no " + member.entity() + " made");
		BlockPos p = helper.absolutePos(at);
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		mob.addTag(VillageRaids.TAG);
		mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		level.addFreshEntityWithPassengers(mob);
		mob.setNoAi(true);
		return mob;
	}

	/** A chest at {@code at} holding {@code stack} in its first slot. */
	private static Container chest(GameTestHelper helper, BlockPos at, ItemStack stack) {
		helper.setBlock(at, Blocks.CHEST);
		Container box = helper.getBlockEntity(at);
		box.setItem(0, stack);
		return box;
	}

	/** A culture that is only the given tactics (read, not registered). */
	private static Culture culture(ResourceLocation id, String tactics) {
		return Culture.read(id, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [" + tactics + "]}").getAsJsonObject());
	}

	/** Registers {@code culture} beside the loaded ones until the test ends. */
	private static void register(GameTestHelper helper, Culture culture) {
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(culture.id(), culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
	}

	/**
	 * Two piglins of a raid, each beside a chest of 9 gold ingots: one in an iron helmet, one bare-headed. A plunder round
	 * later both carry the 9. The helmeted one still wears its iron helmet and isn't marked as showing plunder; the
	 * bare-headed one shows one ingot. Dawn: both go home, the hall counts 18 lost, the helmeted one still wears its
	 * helmet and the other's head is bare again.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaPlunderReportIron1106d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPlunderReportIron1106d")
	public void aPlundererInAnIronHelmetKeepsItThroughThePlunderAndHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		Container ironChest = chest(helper, new BlockPos(8, 2, 6), new ItemStack(Items.GOLD_INGOT, 9));
		Container bareChest = chest(helper, new BlockPos(8, 2, 24), new ItemStack(Items.GOLD_INGOT, 9));
		Mob helmeted = raider(helper, culture, EntityType.PIGLIN, new BlockPos(6, 2, 6));
		helmeted.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		Mob bare = raider(helper, culture, EntityType.PIGLIN, new BlockPos(6, 2, 24));
		VillageRaids.track(level, hall, culture, List.of(helmeted, bare));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Plunder.carried(helmeted).is(Items.GOLD_INGOT) && Plunder.carried(helmeted).getCount() == 9 && ironChest.getItem(0).isEmpty(),
				"setup: the helmeted piglin didn't take the 9 ingots: it carries " + Plunder.carried(helmeted));
			helper.assertTrue(Plunder.carried(bare).getCount() == 9 && bareChest.getItem(0).isEmpty(),
				"setup: the bare-headed piglin didn't take the 9 ingots: it carries " + Plunder.carried(bare));
			helper.assertTrue(helmeted.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && !helmeted.getTags().contains(Plunder.SHOWN_TAG),
				"the plunderer in an iron helmet wears " + helmeted.getItemBySlot(EquipmentSlot.HEAD) + " (shown tag " + helmeted.getTags().contains(Plunder.SHOWN_TAG)
					+ ") after plundering, expected its iron helmet and nothing shown");
			ItemStack shown = bare.getItemBySlot(EquipmentSlot.HEAD);
			helper.assertTrue(shown.is(Items.GOLD_INGOT) && shown.getCount() == 1 && bare.getTags().contains(Plunder.SHOWN_TAG),
				"the bare-headed plunderer shows " + shown + " on its head, expected one gold ingot");
			Threats.tactics(culture).forEach(t -> t.leave(level, hall, culture, helmeted));
			Threats.tactics(culture).forEach(t -> t.leave(level, hall, culture, bare));
			int lost = ThreatData.get(level).plundered(hall);
			helper.assertTrue(lost == 18, "after both went home the hall counts " + lost + " gold lost, expected 18");
			helper.assertTrue(helmeted.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
				"the helmeted plunderer's head after going home: " + helmeted.getItemBySlot(EquipmentSlot.HEAD) + ", expected its own iron helmet");
			helper.assertTrue(bare.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !bare.getTags().contains(Plunder.SHOWN_TAG),
				"the bare-headed plunderer still shows " + bare.getItemBySlot(EquipmentSlot.HEAD) + " after going home");
			helper.succeed();
		});
	}

	/**
	 * A raiding piglin already carrying 5 gold nuggets stands 2 blocks from a chest of 20 gold ingots: two plunder rounds
	 * later it still carries just its 5 nuggets and the chest holds its 20 ingots. A piglin carrying nothing beside another
	 * such chest takes its 20 (the rounds ran).
	 */
	//$ gametest_ticks_batch AREA '80' '"qaPlunderReportSecond1106d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPlunderReportSecond1106d")
	public void aPiglinAlreadyCarryingGoldPassesASecondChestAndLeavesIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		Container passed = chest(helper, new BlockPos(8, 2, 6), new ItemStack(Items.GOLD_INGOT, 20));
		Container taken = chest(helper, new BlockPos(8, 2, 24), new ItemStack(Items.GOLD_INGOT, 20));
		Mob laden = raider(helper, culture, EntityType.PIGLIN, new BlockPos(6, 2, 6));
		Plunder.carry(level, laden, new ItemStack(Items.GOLD_NUGGET, 5));
		Mob empty = raider(helper, culture, EntityType.PIGLIN, new BlockPos(6, 2, 24));
		VillageRaids.track(level, hall, culture, List.of(laden, empty));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Plunder.carried(empty).getCount() == 20 && taken.getItem(0).isEmpty(),
				"setup: the empty-handed piglin didn't plunder its chest: it carries " + Plunder.carried(empty));
			ItemStack carried = Plunder.carried(laden);
			helper.assertTrue(carried.is(Items.GOLD_NUGGET) && carried.getCount() == 5,
				"a piglin already carrying 5 nuggets now carries " + carried + " after passing a second chest");
			helper.assertTrue(passed.getItem(0).is(Items.GOLD_INGOT) && passed.getItem(0).getCount() == 20,
				"the second chest a laden piglin passed holds " + passed.getItem(0) + ", expected its 20 ingots");
			helper.succeed();
		});
	}

	/**
	 * Piglins.md: "a piglin (not a hoglin)". A raiding hoglin ram stands 2 blocks from a chest of 10 gold ingots, a
	 * piglin of the same raid 2 blocks from another: two rounds later the hoglin carries nothing and its chest holds its
	 * 10; the piglin took its 10.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaPlunderReportRam1106d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPlunderReportRam1106d")
	public void aHoglinRamBesideAChestOfGoldTakesNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		Container byRam = chest(helper, new BlockPos(9, 2, 6), new ItemStack(Items.GOLD_INGOT, 10));
		Container byPiglin = chest(helper, new BlockPos(8, 2, 24), new ItemStack(Items.GOLD_INGOT, 10));
		Mob hoglin = raider(helper, culture, EntityType.HOGLIN, new BlockPos(6, 2, 6));
		helper.assertTrue(Threats.role(hoglin) == Culture.Role.RAM, "setup: the raiding hoglin isn't a ram: " + Threats.role(hoglin));
		Mob piglin = raider(helper, culture, EntityType.PIGLIN, new BlockPos(6, 2, 24));
		VillageRaids.track(level, hall, culture, List.of(hoglin, piglin));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Plunder.carried(piglin).getCount() == 10 && byPiglin.getItem(0).isEmpty(),
				"setup: the piglin didn't plunder its chest: it carries " + Plunder.carried(piglin));
			helper.assertTrue(Plunder.carried(hoglin).isEmpty() && byRam.getItem(0).getCount() == 10,
				"a hoglin ram plundered: it carries " + Plunder.carried(hoglin) + ", its chest holds " + byRam.getItem(0));
			helper.succeed();
		});
	}

	/**
	 * 32.10: plunder is "a raider of a culture that names it". A raid of a culture whose tactics don't name plunder: its
	 * husk stands 2 blocks from a chest of 10 gold ingots, and two rounds later it carries nothing and the chest is whole.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaPlunderReportNoTactic1106d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPlunderReportNoTactic1106d")
	public void aRaiderOfACultureWithoutPlunderLeavesTheGold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = culture(NO_PLUNDER, "");
		register(helper, culture);
		Container box = chest(helper, new BlockPos(8, 2, 6), new ItemStack(Items.GOLD_INGOT, 10));
		Mob husk = raider(helper, culture, EntityType.HUSK, new BlockPos(6, 2, 6));
		VillageRaids.track(level, hall, culture, List.of(husk));
		helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: no raid on the village");
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Plunder.carried(husk).isEmpty() && box.getItem(0).getCount() == 10,
				"a raider of a culture without plunder took gold: it carries " + Plunder.carried(husk) + ", the chest holds " + box.getItem(0));
			helper.succeed();
		});
	}

	/**
	 * 32.10: "the siege report says it the morning after, or the chronicle when there was no siege". A village under
	 * siege lost 12 gold: the raid's end writes no plunder line and keeps the 12 for the morning; the morning's report
	 * (one SIEGE entry) ends "12 gold was carried off from the village's chests." and the count is spent. Then a raid
	 * without a siege loses 5: its end tells the chronicle at once and spends the count.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaPlunderReportSiege1106d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaPlunderReportSiege1106d")
	public void withASiegeTheGoldWaitsForTheMorningReportAndIsToldOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "setup: no Village Hall");
		Culture plunderers = culture(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_plunder_only_1106d"), "\"plunder\"");
		ThreatData data = ThreatData.get(level);
		ThreatData.Siege siege = new ThreatData.Siege(hall, level.getGameTime(), level.getDayTime() + 12000);
		siege.came = 4;
		data.beginSiege(siege);
		data.plunder(hall, 12);
		Threats.tactics(plunderers).forEach(t -> t.end(level, hall, plunderers, false));
		List<String> raids = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.RAID).map(e -> e.text().getString()).toList();
		helper.assertTrue(raids.stream().noneMatch(s -> s.contains("carried off")), "with a siege the raid's end told the plunder at once: " + raids);
		helper.assertTrue(data.plundered(hall) == 12, "with a siege the raid's end didn't keep the 12 for the morning: " + data.plundered(hall));
		SiegeReport.write(level, siege);
		List<String> reports = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.SIEGE).map(e -> e.text().getString()).toList();
		helper.assertTrue(reports.size() == 1 && reports.get(0).startsWith("The siege: 4 raiders came and 0 fell") && reports.get(0).endsWith(" No guard made a kill. 12" + REPORT_END),
			"the morning's report: " + reports);
		helper.assertTrue(data.plundered(hall) == 0, "the count wasn't spent once the report told it: " + data.plundered(hall));
		data.forgetSiege(hall);
		helper.assertTrue(data.siege(hall).isEmpty(), "setup: the siege is still on");
		data.plunder(hall, 5);
		Threats.tactics(plunderers).forEach(t -> t.end(level, hall, plunderers, false));
		List<String> after = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.RAID).map(e -> e.text().getString()).toList();
		helper.assertTrue(after.contains("The raiders carried off 5 gold from the village's chests"), "without a siege the chronicle: " + after);
		helper.assertTrue(data.plundered(hall) == 0, "without a siege the count wasn't spent once told: " + data.plundered(hall));
		helper.succeed();
	}

	/**
	 * The siege report's sentence, word for word from en_us.json, with real counts: one raider and nobody fell; seven came,
	 * six fell (3 to guards, 2 to players, 1 to a golem), the gate broken, the hero Brannoc with 2 kills over Aldred's 1;
	 * none counted as come but three fell (the report never says fewer came than fell), the hero Cyne with 1 kill.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaSiegeReportWords1106d"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaSiegeReportWords1106d")
	public void theSiegeReportReadsItsRealCountsAndPlurals(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		ThreatData.Siege one = new ThreatData.Siege(hall, 0, 0);
		one.came = 1;
		String a = SiegeReport.text(level, one).getString();
		helper.assertTrue(a.equals("The siege: 1 raider came and 0 fell, 0 to the guards and 0 to players. The gate held. No guard made a kill."), "one raider: \"" + a + "\"");
		ThreatData.Siege broken = new ThreatData.Siege(hall, 0, 0);
		broken.came = 7;
		broken.breached = true;
		broken.toGuards = 3;
		broken.toPlayers = 2;
		broken.toOthers = 1;
		UUID aldred = UUID.randomUUID();
		UUID brannoc = UUID.randomUUID();
		broken.kills.put(aldred, 1);
		broken.names.put(aldred, "Aldred");
		broken.kills.put(brannoc, 2);
		broken.names.put(brannoc, "Brannoc");
		String b = SiegeReport.text(level, broken).getString();
		helper.assertTrue(b.equals("The siege: 7 raiders came and 6 fell, 3 to the guards and 2 to players. The gate was broken. Hero of the night: Brannoc, with 2 kills."),
			"seven came, gate broken: \"" + b + "\"");
		ThreatData.Siege uncounted = new ThreatData.Siege(hall, 0, 0);
		uncounted.toGuards = 1;
		uncounted.toOthers = 2;
		UUID cyne = UUID.randomUUID();
		uncounted.kills.put(cyne, 1);
		uncounted.names.put(cyne, "Cyne");
		String c = SiegeReport.text(level, uncounted).getString();
		helper.assertTrue(c.equals("The siege: 3 raiders came and 3 fell, 1 to the guards and 0 to players. The gate held. Hero of the night: Cyne, with 1 kill."),
			"none counted as come, three fell: \"" + c + "\"");
		helper.succeed();
	}

	/** A guard of the village (a knight) at (x, 2, z), his post 2 south. */
	private static Villager guard(GameTestHelper helper, int x, int z, String name) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		v.setNoAi(true);
		v.setCustomName(net.minecraft.network.chat.Component.literal(name));
		return v;
	}

	/** A husk standing still at (x, 2, z), tagged as a raider when {@code raider}. */
	private static Mob husk(GameTestHelper helper, int x, int z, boolean raider) {
		ServerLevel level = helper.getLevel();
		Mob mob = EntityType.HUSK.create(level);
		BlockPos p = helper.absolutePos(new BlockPos(x, 2, z));
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		if (raider) {
			mob.addTag(VillageRaids.TAG);
		}
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	/**
	 * Sieges.md: "every death of a raider within the village's radius plus 64 blocks of a hall under siege is counted:
	 * to a guard and which, to a player, or to anything else". All in one tick, deaths by the real damage path:
	 * <ol>
	 * <li>a siege laid to a hall 70 blocks east only: with a village radius of 5 (reach 69) a raider's death here isn't
	 *     its; with radius 6 (reach exactly 70) the next one is;</li>
	 * <li>then a siege here too, the nearer of the two: guard Brannoc kills two raiders, a villager with no job one, a
	 *     player one, and the player kills an untagged husk; the near siege counts 2 to guards (Brannoc's 2, his name),
	 *     1 to players (the player among those who fought), 1 to others; the far one gets none of it.</li>
	 * </ol>
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSiegeReportDeaths1106d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSiegeReportDeaths1106d")
	public void aSiegeCountsEachRaidersDeathToWhoKilledItWithinItsReach(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		BlockPos farHall = hall.offset(70, 0, 0);
		ThreatData data = ThreatData.get(level);
		Leftovers.after(helper, () -> data.forgetSiege(farHall));
		data.forgetSiege(farHall);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Villager brannoc = guard(helper, 20, 20, "Brannoc");
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(24, 2, 20));
		farmer.setNoAi(true);
		Mob beyond = husk(helper, 15, 15, true); // (exactly 70 blocks west of the far hall)
		Mob within = husk(helper, 15, 15, true);
		List<Mob> raiders = List.of(husk(helper, 10, 8, true), husk(helper, 11, 8, true), husk(helper, 12, 8, true), husk(helper, 13, 8, true));
		Mob stranger = husk(helper, 14, 8, false);
		// 1. Only the far siege: reach 69, then exactly 70.
		ThreatData.Siege far = new ThreatData.Siege(farHall, level.getGameTime(), level.getDayTime() + 12000);
		data.beginSiege(far);
		helper.assertTrue(farHall.distSqr(beyond.blockPosition()) == 70 * 70 && farHall.distSqr(within.blockPosition()) == 70 * 70, "setup: the raider isn't 70 blocks from the far hall: "
			+ Math.sqrt(farHall.distSqr(beyond.blockPosition())));
		VillageHalls.RADIUS = 5;
		beyond.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(beyond.isDeadOrDying(), "setup: the raider didn't die");
		helper.assertTrue(far.toPlayers + far.toGuards + far.toOthers == 0 && far.players.isEmpty(),
			"a raider dying 70 blocks from a hall whose reach is 5 + 64 was counted by its siege: players " + far.toPlayers + ", others " + far.toOthers);
		VillageHalls.RADIUS = 6;
		within.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(far.toPlayers == 1 && far.players.contains(player.getUUID()),
			"a raider dying 70 blocks from a hall whose reach is exactly 6 + 64 wasn't counted to the player: players " + far.toPlayers + ", others " + far.toOthers);
		// 2. A siege here too, nearer.
		VillageHalls.RADIUS = 20;
		ThreatData.Siege near = new ThreatData.Siege(hall, level.getGameTime(), level.getDayTime() + 12000);
		data.beginSiege(near);
		raiders.get(0).hurt(level.damageSources().mobAttack(brannoc), 1000f);
		raiders.get(1).hurt(level.damageSources().mobAttack(brannoc), 1000f);
		raiders.get(2).hurt(level.damageSources().mobAttack(farmer), 1000f);
		raiders.get(3).hurt(level.damageSources().playerAttack(player), 1000f);
		stranger.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(raiders.stream().allMatch(Mob::isDeadOrDying) && stranger.isDeadOrDying(), "setup: not every husk died");
		helper.assertTrue(near.toGuards == 2 && near.toPlayers == 1 && near.toOthers == 1,
			"the siege counts to guards " + near.toGuards + ", players " + near.toPlayers + ", others " + near.toOthers + "; expected 2, 1, 1");
		helper.assertTrue(near.kills.size() == 1 && near.kills.getOrDefault(brannoc.getUUID(), 0) == 2 && "Brannoc".equals(near.names.get(brannoc.getUUID())),
			"the guards' kills: " + near.kills + " " + near.names + ", expected Brannoc's 2");
		helper.assertTrue(near.players.size() == 1 && near.players.contains(player.getUUID()), "the players who fought: " + near.players);
		helper.assertTrue(brannoc.getUUID().equals(SiegeReport.hero(near)), "the hero is " + SiegeReport.hero(near) + ", not Brannoc");
		helper.assertTrue(far.toPlayers == 1 && far.toGuards == 0 && far.toOthers == 0, "the far siege counted deaths nearer the other: players "
			+ far.toPlayers + ", guards " + far.toGuards + ", others " + far.toOthers);
		helper.succeed();
	}

	/**
	 * 32.6: the hero is "the guard with the most kills (the first to get there when two have as many)". Aldred kills,
	 * Brannoc kills, Aldred kills again (first to 2), then Brannoc kills again (2 as well): the report names Aldred, with
	 * 2 kills.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSiegeReportTie1106d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSiegeReportTie1106d")
	public void onATieTheHeroIsTheGuardWhoGotThereFirst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		ThreatData data = ThreatData.get(level);
		Villager aldred = guard(helper, 18, 20, "Aldred");
		Villager brannoc = guard(helper, 22, 20, "Brannoc");
		List<Mob> raiders = List.of(husk(helper, 10, 8, true), husk(helper, 11, 8, true), husk(helper, 12, 8, true), husk(helper, 13, 8, true));
		ThreatData.Siege siege = new ThreatData.Siege(hall, level.getGameTime(), level.getDayTime() + 12000);
		siege.came = 4;
		data.beginSiege(siege);
		raiders.get(0).hurt(level.damageSources().mobAttack(aldred), 1000f);
		raiders.get(1).hurt(level.damageSources().mobAttack(brannoc), 1000f);
		raiders.get(2).hurt(level.damageSources().mobAttack(aldred), 1000f);
		raiders.get(3).hurt(level.damageSources().mobAttack(brannoc), 1000f);
		helper.assertTrue(siege.toGuards == 4 && siege.kills.getOrDefault(aldred.getUUID(), 0) == 2 && siege.kills.getOrDefault(brannoc.getUUID(), 0) == 2,
			"setup: the kills " + siege.kills + ", to guards " + siege.toGuards);
		String report = SiegeReport.text(level, siege).getString();
		helper.assertTrue(aldred.getUUID().equals(SiegeReport.hero(siege)) && report.equals(
			"The siege: 4 raiders came and 4 fell, 4 to the guards and 0 to players. The gate held. Hero of the night: Aldred, with 2 kills."),
			"Aldred reached 2 kills first and Brannoc only tied him, yet the report reads: \"" + report + "\"");
		helper.succeed();
	}
}
