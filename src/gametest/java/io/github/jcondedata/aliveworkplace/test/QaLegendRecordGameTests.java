package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSlots;
import io.github.jcondedata.aliveworkplace.legend.LegendText;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane d, 2026-10-25), written from the spec of 29.3 and 29.5: every cap from both sides (a different kind of Rare or
 * Legendary, other villages and dimensions, each rank's Mythic cap, a zombie or a grave still holding, a fallen Legend
 * freeing the slot), the record over a reload and with a broken or old entry, the 7-day fall counted once, and the
 * strike counted per day (never per round), the grace boundary, a need met between unmet days, a strike partly met,
 * the switches, and the sentences a player reads.
 */
public class QaLegendRecordGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String NS = "aliveworkplace_test";
	private static final ResourceLocation TEST = ResourceLocation.fromNamespaceAndPath(NS, "test_legend");
	private static final BlockPos SLOT_HALL = new BlockPos(15, 2, 15);
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final int SKY = 100;

	private static ResourceLocation id(String name) {
		return ResourceLocation.fromNamespaceAndPath(NS, name);
	}

	private static Legend legend(String name, String rarity) {
		return Legends.read(id(name), JsonParser.parseString(
			"{\"rarity\": \"" + rarity + "\", \"job\": \"aliveworkplace:builder\", \"title\": \"legend.aliveworkplace_test." + name + "\", \"lore\": \"l\"}").getAsJsonObject());
	}

	private static UUID uuid(String seed) {
		return UUID.nameUUIDFromBytes(("qa-record-" + seed).getBytes(StandardCharsets.UTF_8));
	}

	private static VillageHallBlockEntity slotHall(GameTestHelper helper) {
		helper.setBlock(SLOT_HALL, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) helper.getBlockEntity(SLOT_HALL);
	}

	/** Record entries the test made go when it ends, and the Mythic caps are put back. */
	private static List<UUID> recordSetUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		int[] caps = Legends.MYTHIC_CAP;
		Leftovers.after(helper, () -> {
			LegendRecord record = LegendRecord.get(helper.getLevel());
			made.forEach(record::forget);
			Legends.MYTHIC_CAP = caps;
			LegendPowers.forget();
		});
		return made;
	}

	private static String key(Component c) {
		return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : "";
	}

	// ---------------------------------------------------------------- 29.3: caps

	/** Rare: one of EACH per village: another kind is welcome; the holder doesn't block themselves; dead keeps, fallen frees. */
	//$ gametest_ticks_batch HUGE '100' '"qaRecordRare"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaRecordRare")
	public void rareIsPerKindAndFreedOnlyWhenFallen(GameTestHelper helper) {
		List<UUID> made = recordSetUp(helper);
		slotHall(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			LegendRecord record = LegendRecord.get(level);
			BlockPos a = helper.absolutePos(SLOT_HALL);
			Legend first = legend("qa_rare_a", "rare");
			Legend other = legend("qa_rare_b", "rare");
			UUID ada = uuid("rare-ada");
			made.add(ada);
			record.settled(first.id(), ada, level.dimension(), Optional.of(a), Rarity.RARE, "Ada", 1);
			helper.assertTrue(!Legends.canCome(level, a, first), "a second Rare of one kind allowed");
			helper.assertTrue(Legends.canCome(level, a, other), "a Rare of ANOTHER kind refused in the same village");
			helper.assertTrue(LegendSlots.whyNot(level, a, first, ada).isEmpty(), "the Rare who holds the slot is refused their own slot");
			Optional<Component> why = LegendSlots.whyNot(level, a, first, null);
			helper.assertTrue(why.map(QaLegendRecordGameTests::key).orElse("").equals("message.aliveworkplace.legend.refused.rare"), "refusal: " + why);

			// turned into a zombie villager: still holds; cured: still holds
			UUID zombie = uuid("rare-ada-zombie");
			made.add(zombie);
			record.turned(ada, zombie);
			helper.assertTrue(record.entry(zombie).map(LegendRecord.Entry::zombie).orElse(false) && !Legends.canCome(level, a, first), "a zombie Legend lost the slot");
			UUID cured = uuid("rare-ada-cured");
			made.add(cured);
			record.back(zombie, cured);
			helper.assertTrue(record.entry(zombie).isEmpty() && record.entry(cured).map(e -> e.holds() && !e.zombie()).orElse(false), "cure: " + record.entries());
			helper.assertTrue(!Legends.canCome(level, a, first), "a cured Legend lost the slot");

			// died with no grave on day 50: holds days 50..56, falls on 57
			record.died(cured, 50, Optional.empty());
			record.newDay(level.getServer(), 56);
			helper.assertTrue(!Legends.canCome(level, a, first), "slot freed after 6 days");
			record.newDay(level.getServer(), 57);
			helper.assertTrue(Legends.canCome(level, a, first), "slot still held 7 days after a death with no grave");
			helper.assertTrue(Legends.canCome(level, a, other), "the other kind refused");
			helper.succeed();
		});
	}

	/** Legendary: one of each per world (any dimension, any village or none); another Legendary kind is welcome; fallen frees. */
	//$ gametest_ticks_batch HUGE '100' '"qaRecordLegendary"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaRecordLegendary")
	public void legendaryIsPerWorldPerKind(GameTestHelper helper) {
		List<UUID> made = recordSetUp(helper);
		slotHall(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			ServerLevel nether = level.getServer().getLevel(Level.NETHER);
			LegendRecord record = LegendRecord.get(level);
			BlockPos a = helper.absolutePos(SLOT_HALL);
			Legend x = legend("qa_legendary_x", "legendary");
			Legend y = legend("qa_legendary_y", "legendary");
			UUID bo = uuid("legendary-bo");
			made.add(bo);
			// settled in a Nether village far away
			record.settled(x.id(), bo, Level.NETHER, Optional.of(new BlockPos(9000, 70, -9000)), Rarity.LEGENDARY, "Bo", 1);
			helper.assertTrue(!Legends.canCome(level, a, x), "a Legendary in the Nether doesn't block one in the Overworld");
			helper.assertTrue(!Legends.canCome(nether, a, x), "a second Legendary allowed in the Nether");
			helper.assertTrue(LegendSlots.whyNot(level, null, x, null).isPresent(), "a second Legendary allowed with no village");
			helper.assertTrue(LegendSlots.whyNot(level, a, x, null).map(QaLegendRecordGameTests::key).orElse("").equals("message.aliveworkplace.legend.refused.legendary"),
				"refusal key");
			helper.assertTrue(Legends.canCome(level, a, y), "a Legendary of another kind refused");
			// a grave holds the slot however long it stands
			record.died(bo, 10, Optional.of(new BlockPos(9000, 70, -9000)));
			record.newDay(level.getServer(), 100); // the grave's chunk isn't loaded: it's never found broken
			helper.assertTrue(record.entry(bo).orElseThrow().holds() && !Legends.canCome(level, a, x), "a Legendary in a grave lost the slot");
			helper.succeed();
		});
	}

	/** Mythic: each rank's cap from both sides (default 0/0/1/2), every kind counts together, only this village's count, fallen frees. */
	//$ gametest_ticks_batch HUGE '100' '"qaRecordMythic"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaRecordMythic")
	public void mythicCapAtEveryRankFromBothSides(GameTestHelper helper) {
		List<UUID> made = recordSetUp(helper);
		VillageHallBlockEntity entity = slotHall(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			ServerLevel nether = level.getServer().getLevel(Level.NETHER);
			LegendRecord record = LegendRecord.get(level);
			BlockPos a = helper.absolutePos(SLOT_HALL);
			Legends.MYTHIC_CAP = new WorkplaceConfig().mythicLegendCap.stream().mapToInt(Integer::intValue).toArray();
			Legend m1 = legend("qa_mythic_1", "mythic");
			Legend m2 = legend("qa_mythic_2", "mythic");
			Legend m3 = legend("qa_mythic_3", "mythic");

			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertTrue(!Legends.canCome(level, a, m1), "a Mythic allowed in a Hamlet (cap 0)");
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(!Legends.canCome(level, a, m1), "a Mythic allowed in a Village (cap 0)");
			helper.assertTrue(LegendSlots.whyNot(level, null, m1, null).isPresent(), "a Mythic allowed with no village");
			entity.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(Legends.canCome(level, a, m1), "a first Mythic refused in a Town (cap 1)");
			UUID one = uuid("mythic-1");
			made.add(one);
			record.settled(m1.id(), one, level.dimension(), Optional.of(a), Rarity.MYTHIC, "Cy", 1);
			// a different Mythic kind still counts against the cap
			helper.assertTrue(!Legends.canCome(level, a, m2), "a second Mythic (another kind) allowed in a Town");
			String text = LegendSlots.whyNot(level, a, m2, null).map(Component::getString).orElse("");
			helper.assertTrue(text.equals("A Town can hold 1 Mythic Legend, and this one has 1."), "refusal text (B504): " + text);
			// Mythics elsewhere don't count: another village, or the same spot in the Nether
			UUID far = uuid("mythic-far");
			UUID hell = uuid("mythic-nether");
			made.add(far);
			made.add(hell);
			record.settled(m2.id(), far, level.dimension(), Optional.of(a.offset(800, 0, 0)), Rarity.MYTHIC, "Far", 1);
			record.settled(m3.id(), hell, Level.NETHER, Optional.of(a), Rarity.MYTHIC, "Hell", 1);
			entity.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(Legends.canCome(level, a, m2), "a second Mythic refused in a City (cap 2), or other villages' Mythics counted");
			UUID two = uuid("mythic-2");
			made.add(two);
			record.settled(m2.id(), two, level.dimension(), Optional.of(a), Rarity.MYTHIC, "Di", 1);
			helper.assertTrue(!Legends.canCome(level, a, m3), "a third Mythic allowed in a City");
			String full = LegendSlots.whyNot(level, a, m3, null).map(Component::getString).orElse("");
			helper.assertTrue(full.equals("A City can hold 2 Mythic Legends, and this one has 2."), "refusal text at cap 2: " + full);
			helper.assertTrue(LegendSlots.whyNot(level, a, m3, two).isEmpty(), "a Mythic held against themselves");
			helper.assertTrue(!Legends.canCome(nether, a, m3) == (LegendSlots.mythicCap(VillageRanks.of(nether, a)) <= 1), "the Nether village counts the Overworld's");
			// a zombie Mythic keeps the slot; one fallen frees it
			UUID zombie = uuid("mythic-2-zombie");
			made.add(zombie);
			record.turned(two, zombie);
			helper.assertTrue(!Legends.canCome(level, a, m3), "a zombie Mythic lost their slot");
			record.died(zombie, 30, Optional.empty());
			record.newDay(level.getServer(), 36);
			helper.assertTrue(!Legends.canCome(level, a, m3), "a Mythic's slot freed after 6 days");
			record.newDay(level.getServer(), 37);
			helper.assertTrue(Legends.canCome(level, a, m3), "a fallen Mythic still counts against the cap");
			// fell back to a Village: keeps the one it has, takes no more
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(record.entry(one).orElseThrow().holds() && !Legends.canCome(level, a, m3), "a Village that fell back took a Mythic");

			// the caps follow the config: a Hamlet given 1; negatives clamp to 0
			Legends.MYTHIC_CAP = WorkplaceConfig.parse("{\"mythicLegendCap\": [2, 2, 2, 2]}").mythicLegendCap.stream().mapToInt(Integer::intValue).toArray();
			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertTrue(Legends.canCome(level, a, m3), "a Hamlet with cap 2 holding 1 refused a Mythic");
			helper.assertTrue(WorkplaceConfig.parse("{\"mythicLegendCap\": [-3, -1, 1, 2]}").mythicLegendCap.equals(List.of(0, 0, 1, 2)), "negative caps not clamped");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- 29.3: the record

	/** The record over a reload: every field, the slots it holds; one broken entry doesn't take the others with it; an old save. */
	//$ gametest_ticks_batch HUGE '100' '"qaRecordReload"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaRecordReload")
	public void recordReloadKeepsEveryEntryAndSlot(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos a = helper.absolutePos(SLOT_HALL);
			String dim = level.dimension().location().toString();
			LegendRecord record = new LegendRecord(); // a record of its own: the server's stays as it is
			record.settled(id("qa_m"), uuid("r-mythic"), level.dimension(), Optional.of(a), Rarity.MYTHIC, "Myth", 3);
			record.settled(id("qa_l"), uuid("r-legendary"), Level.NETHER, Optional.empty(), Rarity.LEGENDARY, "Leg", 4);
			record.settled(id("qa_r"), uuid("r-rare"), level.dimension(), Optional.of(a), Rarity.RARE, "Rar", 5);
			record.settled(id("qa_z"), uuid("r-zed"), level.dimension(), Optional.of(a), Rarity.RARE, "Zed", 5);
			record.died(uuid("r-legendary"), 9, Optional.of(new BlockPos(1, 2, 3).offset(200000, 0, 200000))); // never loaded: never found broken
			record.died(uuid("r-rare"), 10, Optional.empty());
			record.turned(uuid("r-zed"), uuid("r-zed-z"));
			record.newDay(level.getServer(), 17); // the Rare falls

			LegendRecord loaded = LegendRecord.load(record.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(loaded.entries().equals(record.entries()), "entries after reload: " + loaded.entries() + " vs " + record.entries());
			LegendRecord.Entry leg = loaded.entry(uuid("r-legendary")).orElseThrow();
			helper.assertTrue(leg.holds() && leg.grave().equals(Optional.of(new BlockPos(200001, 2, 200003))) && leg.dimension().equals("minecraft:the_nether"), "grave: " + leg);
			LegendRecord.Entry rare = loaded.entry(uuid("r-rare")).orElseThrow();
			helper.assertTrue(!rare.holds() && rare.fell() == 17 && rare.settled() == 5 && rare.name().equals("Rar"), "fallen: " + rare);
			helper.assertTrue(loaded.entry(uuid("r-zed-z")).map(LegendRecord.Entry::zombie).orElse(false), "zombie flag lost");
			helper.assertTrue(loaded.holding(e -> e.rarity() == Rarity.MYTHIC && e.in(dim, a)) == 1, "the Mythic's slot after reload");
			// saved twice over is still the same (no drift)
			LegendRecord twice = LegendRecord.load(loaded.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(twice.entries().equals(record.entries()), "a second reload drifted");

			// a save with one broken entry (no villager) and one unknown rarity: the good ones survive
			CompoundTag tag = record.save(new CompoundTag(), level.registryAccess());
			ListTag list = tag.getList("legends", 10);
			CompoundTag broken = new CompoundTag();
			broken.putString("id", "aliveworkplace_test:qa_broken");
			list.add(broken);
			CompoundTag odd = new CompoundTag();
			odd.putString("id", "aliveworkplace_test:qa_odd");
			odd.putIntArray("villager", UUIDUtil.uuidToIntArray(uuid("r-odd")));
			odd.putString("rarity", "ultra");
			list.add(odd);
			tag.put("legends", list);
			LegendRecord partly = LegendRecord.load(tag, level.registryAccess());
			helper.assertTrue(partly.entry(uuid("r-mythic")).isPresent() && partly.entry(uuid("r-legendary")).isPresent(),
				"one broken entry dropped the whole record: " + partly.entries());
			helper.assertTrue(partly.entry(uuid("r-odd")).map(e -> e.rarity() == Rarity.RARE && e.holds()).orElse(false), "unknown rarity: " + partly.entry(uuid("r-odd")));

			// an old save: nothing at all, or a record with none of the later lists
			helper.assertTrue(LegendRecord.load(new CompoundTag(), level.registryAccess()).entries().isEmpty(), "an empty save isn't an empty record");
			CompoundTag old = new CompoundTag();
			ListTag one = new ListTag();
			CompoundTag bare = new CompoundTag();
			bare.putString("id", TEST.toString());
			bare.putIntArray("villager", UUIDUtil.uuidToIntArray(uuid("r-old")));
			bare.putString("rarity", "mythic");
			bare.put("hall", net.minecraft.nbt.NbtUtils.writeBlockPos(a));
			one.add(bare);
			old.put("legends", one);
			LegendRecord oldRecord = LegendRecord.load(old, level.registryAccess());
			LegendRecord.Entry e = oldRecord.entry(uuid("r-old")).orElse(null);
			helper.assertTrue(e != null && e.holds() && !e.zombie() && e.grave().isEmpty() && e.gone() == -1 && e.fell() == -1, "old entry defaults: " + e);
			helper.assertTrue(oldRecord.captives().isEmpty() && !oldRecord.siteUsed("x"), "old save's later lists");
			helper.succeed();
		});
	}

	/** A Legend falls once (one chronicle line, returned once); a living Legend never falls; one brought back before day 7 doesn't. */
	//$ gametest_ticks_batch HUGE '100' '"qaRecordFall"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaRecordFall")
	public void aLegendFallsOnceAndOnlyWhenGone(GameTestHelper helper) {
		List<UUID> made = recordSetUp(helper);
		VillageHallBlockEntity entity = slotHall(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			LegendRecord record = LegendRecord.get(level);
			BlockPos a = helper.absolutePos(SLOT_HALL);
			UUID alive = uuid("fall-alive");
			UUID gone = uuid("fall-gone");
			UUID back = uuid("fall-back");
			UUID backAgain = uuid("fall-back-2");
			made.addAll(List.of(alive, gone, back, backAgain));
			record.settled(TEST, alive, level.dimension(), Optional.of(a), Rarity.RARE, "Liv", 1);
			record.settled(TEST, gone, level.dimension(), Optional.of(a.offset(300, 0, 0)), Rarity.RARE, "Gon", 1);
			record.settled(TEST, back, level.dimension(), Optional.of(a.offset(600, 0, 0)), Rarity.RARE, "Bak", 1);
			record.died(gone, 20, Optional.empty());
			record.died(back, 20, Optional.empty());
			record.back(back, backAgain); // brought back (a revive) on day 22
			record.newDay(level.getServer(), 22);
			for (int round = 0; round < 3; round++) { // several rounds on day 26: six days, still held
				List<LegendRecord.Entry> fell = record.newDay(level.getServer(), 26);
				helper.assertTrue(fell.stream().noneMatch(e -> e.villager().equals(gone)) && record.entry(gone).orElseThrow().holds(), "fell after 6 days: " + fell);
			}
			List<LegendRecord.Entry> first = record.newDay(level.getServer(), 27);
			helper.assertTrue(first.stream().filter(e -> e.villager().equals(gone)).count() == 1, "didn't fall on day 27 (7 days after day 20): " + first);
			List<LegendRecord.Entry> again = record.newDay(level.getServer(), 27);
			List<LegendRecord.Entry> later = record.newDay(level.getServer(), 40);
			helper.assertTrue(again.stream().noneMatch(e -> e.villager().equals(gone)) && later.stream().noneMatch(e -> e.villager().equals(gone)),
				"fell more than once: " + again + " / " + later);
			helper.assertTrue(record.entry(gone).orElseThrow().fell() == 27, "the day they fell moved: " + record.entry(gone));
			record.died(gone, 41, Optional.empty()); // dying again once fallen changes nothing
			helper.assertTrue(record.entry(gone).orElseThrow().fell() == 27 && record.entry(gone).orElseThrow().gone() == 20, "a fallen Legend died again: " + record.entry(gone));
			helper.assertTrue(record.entry(alive).orElseThrow().holds() && record.newDay(level.getServer(), 500).stream().noneMatch(e -> e.villager().equals(alive)),
				"a living Legend fell");
			helper.assertTrue(record.entry(backAgain).orElseThrow().holds() && record.entry(backAgain).orElseThrow().gone() == -1, "one brought back fell: " + record.entry(backAgain));
			long lines = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LEGEND && key(e.text()).equals("chronicle.aliveworkplace.legend.fallen")).count();
			helper.assertTrue(lines == 0, "a fallen line in a village whose Legend didn't fall: " + lines);
			helper.succeed();
		});
	}

	/** The fallen line in their own village's chronicle, once, and reading right. */
	//$ gametest_ticks_batch HUGE '100' '"qaRecordFallenLine"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaRecordFallenLine")
	public void theFallenLineIsWrittenOnce(GameTestHelper helper) {
		List<UUID> made = recordSetUp(helper);
		VillageHallBlockEntity entity = slotHall(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			LegendRecord record = LegendRecord.get(level);
			BlockPos a = helper.absolutePos(SLOT_HALL);
			UUID gone = uuid("line-gone");
			made.add(gone);
			record.settled(TEST, gone, level.dimension(), Optional.of(a), Rarity.RARE, "Gwen", 1);
			record.died(gone, 3, Optional.empty());
			record.newDay(level.getServer(), 10);
			record.newDay(level.getServer(), 10);
			record.newDay(level.getServer(), 11);
			List<String> lines = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LEGEND && key(e.text()).equals("chronicle.aliveworkplace.legend.fallen"))
				.map(e -> e.text().getString()).toList();
			String title = Legends.get(TEST).map(l -> l.titleText().getString()).orElse("?");
			helper.assertTrue(lines.equals(List.of("Gwen, the " + title + ", has fallen")), "fallen lines: " + lines);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- 29.5: needs and strikes

	private static Legend needsLegend() {
		return Legends.read(id("qa_mason"), JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \"qa_mason\", \"lore\": \"l\""
			+ ", \"powers\": [{\"type\": \"pace\", \"trades\": [\"minecraft:farmer\"], \"factor\": 2.0}]}").getAsJsonObject());
	}

	private static void needsSetUp(GameTestHelper helper, List<BuildSiteManager.Finished> homes) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			BuildSiteManager sites = BuildSiteManager.get(level);
			homes.forEach(f -> sites.forgetFinished(f.placement()));
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				record.forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
	}

	/** Needs on, moods off (the hall's wellbeing decides "a happy village"), only the test's Legend loaded; all put back within the tick. */
	private static void staged(GameTestHelper helper, Runnable body) {
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		boolean legends = Legends.ENABLED;
		Legend legend = needsLegend();
		try {
			LegendNeeds.ENABLED = true;
			Legends.ENABLED = true;
			Moods.ENABLED = false;
			Legends.setForTest(Map.of(legend.id(), legend));
			body.run();
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Legends.ENABLED = legends;
			Moods.forget();
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	/** The Legend made out of a new villager, settled in the test's village on Chronicle day {@code settled}. */
	private static Villager settle(GameTestHelper helper, long settled) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 10));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.MASON).setLevel(2));
		v.setCustomName(Component.literal("Ada"));
		Legends.make(helper.getLevel(), v, Legends.get(id("qa_mason")).orElseThrow(), "test");
		LegendData data = ModAttachments.LEGEND.get(v);
		ModAttachments.LEGEND.set(v, new LegendData(data.id(), data.name(), false, Optional.of(helper.absolutePos(HALL)), settled - 1, -1, Map.of(), -1, -1, "test"));
		return v;
	}

	/** Gives the Legend a tier III home of her own, high above the area. */
	private static void giveHome(GameTestHelper helper, Villager v, List<BuildSiteManager.Finished> homes) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2)).above(SKY);
		ResourceLocation path = id("qa_needs_manor_3");
		level.getStructureManager().getOrCreate(path).fillFromWorld(level, origin.above(40), new Vec3i(8, 5, 8), false, Blocks.AIR);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		UUID owner = UUID.randomUUID();
		BuildSiteManager.get(level).recordFinished(path, placement, owner);
		homes.add(new BuildSiteManager.Finished(path, placement, owner));
		Beds.home(v, origin.offset(1, 1, 1));
	}

	private static void wellbeing(GameTestHelper helper, float w) {
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 0, 0, w));
	}

	private static List<String> strikeLines(GameTestHelper helper, String key) {
		return ((VillageHallBlockEntity) helper.getBlockEntity(HALL)).chronicle().stream()
			.filter(e -> e.kind() == Chronicle.Kind.LEGEND && key(e.text()).equals(key)).map(e -> e.text().getString()).toList();
	}

	/** Many checks on one day count that day once: one unmet day and many rounds is no strike; the strike's chronicle line is written once. */
	//$ gametest_ticks_batch BIG '100' '"qaNeedsPerDay"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaNeedsPerDay")
	public void manyRoundsOnOneDayCountOneDay(GameTestHelper helper) {
		List<BuildSiteManager.Finished> homes = new ArrayList<>();
		needsSetUp(helper, homes);
		helper.runAfterDelay(2, () -> staged(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = settle(helper, 1);
			giveHome(helper, ada, homes);
			wellbeing(helper, 0.1f); // unhappy village: one need unmet
			LegendData data = null;
			for (int round = 0; round < 6; round++) {
				data = LegendNeeds.check(level, hall, ada, 10);
			}
			helper.assertTrue(!data.onStrike() && data.unmet().get(LegendText.HAPPY) == 1 && data.unmet().size() == 1, "six rounds on day 10: " + data);
			data = LegendNeeds.check(level, hall, ada, 11);
			helper.assertTrue(data.onStrike() && data.strikeSince() == 11 && data.unmet().get(LegendText.HAPPY) == 2, "day 11, two days running: " + data);
			for (int round = 0; round < 6; round++) {
				data = LegendNeeds.check(level, hall, ada, 11);
			}
			helper.assertTrue(data.onStrike() && data.strikeSince() == 11 && data.unmet().get(LegendText.HAPPY) == 2, "rounds on strike counted days: " + data);
			data = LegendNeeds.check(level, hall, ada, 12);
			helper.assertTrue(data.strikeSince() == 11 && data.unmet().get(LegendText.HAPPY) == 3, "day 12: " + data);
			helper.assertTrue(strikeLines(helper, "chronicle.aliveworkplace.legend.strike").equals(List.of("Ada, the qa_mason, went on strike: \"a happy village\"")),
				"strike lines: " + strikeLines(helper, "chronicle.aliveworkplace.legend.strike"));

			// the hall's real round, over and over on one day, out of grace: one unmet day before it is the only one counted
			long today = Chronicle.day(level);
			ModAttachments.LEGEND.set(ada, new LegendData(ModAttachments.LEGEND.get(ada).id(), "", false, Optional.of(hall), today - 30, -1, Map.of(), -1, -1, "test")
				.checkedOn(today - 1, Map.of(), -1, -1));
			for (int round = 0; round < 5; round++) {
				LegendNeeds.round(level, hall);
			}
			data = ModAttachments.LEGEND.get(ada);
			helper.assertTrue(!data.onStrike() && data.unmet().get(LegendText.HAPPY) == 1 && data.checked() == today, "five rounds today: " + data);
			helper.succeed();
		}));
	}

	/** A need met on the day between two unmet days resets the count: unmet, met, unmet ... never strikes. */
	//$ gametest_ticks_batch BIG '100' '"qaNeedsReset"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaNeedsReset")
	public void aNeedMetBetweenUnmetDaysNeverStrikes(GameTestHelper helper) {
		List<BuildSiteManager.Finished> homes = new ArrayList<>();
		needsSetUp(helper, homes);
		helper.runAfterDelay(2, () -> staged(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = settle(helper, 1);
			giveHome(helper, ada, homes);
			for (long day = 10; day <= 30; day++) {
				boolean unhappy = day % 2 == 0;
				wellbeing(helper, unhappy ? 0.1f : 0.9f);
				LegendData data = LegendNeeds.check(level, hall, ada, day);
				helper.assertTrue(!data.onStrike(), "day " + day + ": a strike with no need unmet two days running: " + data);
				helper.assertTrue(data.unmet().getOrDefault(LegendText.HAPPY, 0) == (unhappy ? 1 : 0), "day " + day + ": the count: " + data.unmet());
			}
			// wellbeing exactly 60% is happy; just under isn't
			wellbeing(helper, 0.6f);
			helper.assertTrue(LegendNeeds.happy(level, hall), "wellbeing 60% isn't a happy village");
			wellbeing(helper, 0.59f);
			helper.assertTrue(!LegendNeeds.happy(level, hall), "wellbeing 59% is a happy village");
			helper.assertTrue(strikeLines(helper, "chronicle.aliveworkplace.legend.strike").isEmpty(), "a strike in the chronicle");
			helper.succeed();
		}));
	}

	/** The 3-day grace, at its edge: settled on day 10, unmet from that day: days 10-12 no strike, day 13 a strike; first unmet on 12: strike on 13. */
	//$ gametest_ticks_batch BIG '100' '"qaNeedsGrace"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaNeedsGrace")
	public void theGraceEndsOnTheThirdDayAfterSettling(GameTestHelper helper) {
		List<BuildSiteManager.Finished> homes = new ArrayList<>();
		needsSetUp(helper, homes);
		helper.runAfterDelay(2, () -> staged(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = settle(helper, 10);
			giveHome(helper, ada, homes);
			wellbeing(helper, 0.1f);
			for (long day = 10; day <= 12; day++) {
				LegendData data = LegendNeeds.check(level, hall, ada, day);
				helper.assertTrue(!data.onStrike(), "day " + day + " in the grace: " + data);
			}
			LegendData data = LegendNeeds.check(level, hall, ada, 13);
			helper.assertTrue(data.onStrike() && data.strikeSince() == 13, "day 13, out of the grace with the need unmet 4 days: " + data);

			// settled again on day 20, unmet first on day 22 (met on 20 and 21): no strike on 22, a strike on 23
			ModAttachments.LEGEND.set(ada, new LegendData(data.id(), "", false, Optional.of(hall), 19, -1, Map.of(), -1, -1, "test"));
			wellbeing(helper, 0.9f);
			LegendNeeds.check(level, hall, ada, 20);
			LegendNeeds.check(level, hall, ada, 21);
			wellbeing(helper, 0.1f);
			data = LegendNeeds.check(level, hall, ada, 22);
			helper.assertTrue(!data.onStrike() && data.unmet().get(LegendText.HAPPY) == 1, "day 22: " + data);
			data = LegendNeeds.check(level, hall, ada, 23);
			helper.assertTrue(data.onStrike() && data.strikeSince() == 23, "day 23, unmet two days and out of the grace: " + data);
			helper.succeed();
		}));
	}

	/** A strike for two needs, one met: still on strike, wanting only the other; both met: back to work, said once; the hall shows red then nothing. */
	//$ gametest_ticks_batch BIG '100' '"qaNeedsPartly"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaNeedsPartly")
	public void aStrikePartlyMetGoesOn(GameTestHelper helper) {
		List<BuildSiteManager.Finished> homes = new ArrayList<>();
		needsSetUp(helper, homes);
		helper.runAfterDelay(2, () -> staged(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = settle(helper, 1);
			Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 10));
			farmer.setNoAi(true);
			farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
			wellbeing(helper, 0.1f); // no home, unhappy village
			LegendNeeds.check(level, hall, ada, 10);
			LegendData data = LegendNeeds.check(level, hall, ada, 11);
			Legend legend = Legends.of(ada).orElseThrow();
			helper.assertTrue(data.onStrike() && LegendNeeds.wants(legend, data).getString().equals("a home of my own and a happy village"),
				"strike for two: " + data + " wants " + LegendNeeds.wants(legend, data).getString());
			helper.assertTrue(LegendPowers.pace(farmer) == 1f, "her pace power works on strike");
			// day 12: the village is happy, she still has no home
			wellbeing(helper, 0.9f);
			data = LegendNeeds.check(level, hall, ada, 12);
			helper.assertTrue(data.onStrike() && LegendNeeds.wants(legend, data).getString().equals("a home of my own"), "one need met ended the strike: " + data);
			helper.assertTrue(LegendNeeds.overhead(legend, data).getString().equals("On strike: a home of my own"), "overhead: " + LegendNeeds.overhead(legend, data).getString());
			List<String> hallLines = LegendText.hallLines(ada).stream().map(Component::getString).toList();
			helper.assertTrue(hallLines.contains("On strike since day 11") && hallLines.contains("Wants: a home of my own"), "hall lines: " + hallLines);
			helper.assertTrue(strikeLines(helper, "chronicle.aliveworkplace.legend.strike_over").isEmpty(), "back to work with a need unmet");
			// later that day she gets a home: the next round, the same day, she's back
			giveHome(helper, ada, homes);
			data = LegendNeeds.check(level, hall, ada, 12);
			helper.assertTrue(!data.onStrike() && data.unmet().isEmpty(), "all met, still on strike: " + data);
			helper.assertTrue(LegendPowers.pace(farmer) == 2f, "her power didn't come back");
			LegendNeeds.check(level, hall, ada, 12);
			LegendNeeds.check(level, hall, ada, 13);
			helper.assertTrue(strikeLines(helper, "chronicle.aliveworkplace.legend.strike_over").equals(List.of("Ada, the qa_mason, has all they asked for and went back to work")),
				"back-to-work lines: " + strikeLines(helper, "chronicle.aliveworkplace.legend.strike_over"));
			helper.assertTrue(LegendText.hallLines(ada).stream().noneMatch(l -> l.getString().startsWith("On strike") || l.getString().startsWith("Wants")),
				"the hall still shows a strike");
			helper.succeed();
		}));
	}

	/** The switches: legendNeeds off, the overdue Legend's round neither counts nor strikes; Legends off, no arrival and no strike. */
	//$ gametest_ticks_batch BIG '100' '"qaNeedsSwitches"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaNeedsSwitches")
	public void theSwitchesStopEveryStrike(GameTestHelper helper) {
		List<BuildSiteManager.Finished> homes = new ArrayList<>();
		needsSetUp(helper, homes);
		helper.runAfterDelay(2, () -> staged(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager ada = settle(helper, 1);
			long today = Chronicle.day(level);
			wellbeing(helper, 0.1f);
			LegendData before = new LegendData(ModAttachments.LEGEND.get(ada).id(), "", false, Optional.of(hall), today - 30, -1, Map.of(), -1, -1, "test")
				.checkedOn(today - 1, Map.of(LegendText.HOME, 5, LegendText.HAPPY, 5), -1, -1);
			ModAttachments.LEGEND.set(ada, before);
			LegendNeeds.ENABLED = false;
			LegendNeeds.round(level, hall);
			LegendData data = ModAttachments.LEGEND.get(ada);
			helper.assertTrue(!data.onStrike() && !LegendNeeds.striking(ada), "needs off and a strike began: " + data);
			helper.assertTrue(strikeLines(helper, "chronicle.aliveworkplace.legend.strike").isEmpty(), "needs off: a strike in the chronicle");
			// Legends off: canCome says no (with its sentence), and a striking Legend isn't
			LegendNeeds.ENABLED = true;
			ModAttachments.LEGEND.set(ada, before.checkedOn(today - 1, Map.of(LegendText.HOME, 5), today - 1, -1));
			helper.assertTrue(LegendNeeds.striking(ada), "staged strike");
			Legends.ENABLED = false;
			helper.assertTrue(!LegendNeeds.striking(ada), "Legends off and she still pickets");
			helper.assertTrue(!Legends.canCome(level, hall, legend("qa_off_rare", "rare")), "Legends off and a Rare may come");
			String off = LegendSlots.whyNot(level, hall, legend("qa_off_rare", "rare"), null).map(QaLegendRecordGameTests::key).orElse("");
			helper.assertTrue(off.equals("message.aliveworkplace.legend.off") && Language.getInstance().has(off), "Legends off refusal: " + off);
			LegendNeeds.round(level, hall);
			helper.assertTrue(ModAttachments.LEGEND.get(ada).checked() == today - 1, "Legends off and the round checked her needs");
			helper.succeed();
		}));
	}

	/** Every sentence of the record and the strikes a player reads, with real arguments. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void recordAndStrikeSentencesRead(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> missing = new ArrayList<>();
		for (String k : List.of("message.aliveworkplace.legend.refused.rare", "message.aliveworkplace.legend.refused.legendary", "message.aliveworkplace.legend.refused.mythic",
			"message.aliveworkplace.legend.off", "chronicle.aliveworkplace.legend.fallen", "chronicle.aliveworkplace.legend.joined", "chronicle.aliveworkplace.legend.strike",
			"chronicle.aliveworkplace.legend.strike_over", "legend.aliveworkplace.strike", "legend.aliveworkplace.strike_wants", "legend.aliveworkplace.overhead.strike",
			"legend.aliveworkplace.comma", "legend.aliveworkplace.and")) {
			if (!lang.has(k)) {
				missing.add(k);
			}
		}
		helper.assertTrue(missing.isEmpty(), "missing sentences: " + missing);
		String strike = Component.translatable("legend.aliveworkplace.strike", 12).getString();
		helper.assertTrue(strike.equals("On strike since day 12"), "strike line: " + strike);
		String wants = Component.translatable("legend.aliveworkplace.strike_wants", Component.translatable("legend.aliveworkplace.want.home")).getString();
		helper.assertTrue(wants.equals("Wants: a home of my own"), "wants: " + wants);
		String rare = Component.translatable("message.aliveworkplace.legend.refused.rare", "Seer").getString();
		helper.assertTrue(rare.startsWith("The Seer already lives in this village"), "rare refusal: " + rare);
		helper.assertTrue(Chronicle.Kind.valueOf("LEGEND") != null, "no LEGEND chronicle kind");
		helper.succeed();
	}
}
