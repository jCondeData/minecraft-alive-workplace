package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendCommand;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSlots;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * 29.3, rarities and caps: who may come where, the Mythic caps from the config, the grave and the cure keeping a slot,
 * the 7-day fall, the record surviving a reload, a hall placed again, and who hears the announcements.
 */
public class LegendSlotsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_legend");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static Legend legend(String name, String rarity) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name), JsonParser.parseString(
			"{\"rarity\": \"" + rarity + "\", \"job\": \"aliveworkplace:builder\", \"title\": \"legend.aliveworkplace_test." + name + "\", \"lore\": \"l\"}").getAsJsonObject());
	}

	private static UUID uuid(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)); // fixed, so a run is the same every time
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) helper.getBlockEntity(at);
	}

	private static Villager villager(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		return v;
	}

	/** Every record entry a test leaves, and every Legend villager, goes with it. */
	private static void cleanUp(GameTestHelper helper, List<UUID> extra) {
		Leftovers.after(helper, () -> {
			LegendRecord record = LegendRecord.get(helper.getLevel());
			extra.forEach(record::forget);
			for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				record.forget(e.getUUID());
				e.discard();
			}
			LegendPowers.forget();
		});
	}

	/** One of each Rare per village, Legendary per world; Mythic by the village's rank, the caps from the config. */
	//$ gametest_ticks_batch AREA '100' '"legendRarityRules"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendRarityRules")
	public void legendRarityRules(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		int[] caps = Legends.MYTHIC_CAP;
		Leftovers.after(helper, () -> Legends.MYTHIC_CAP = caps);
		VillageHallBlockEntity entity = hall(helper, HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			ServerLevel nether = level.getServer().getLevel(Level.NETHER);
			LegendRecord record = LegendRecord.get(level);
			BlockPos a = helper.absolutePos(HALL);
			BlockPos b = a.offset(500, 0, 0); // another village
			Legend rare = Legends.get(TEST).orElseThrow();
			Legend legendary = legend("slots_legendary", "legendary");
			Legend mythic = legend("slots_mythic", "mythic");

			helper.assertTrue(Legends.canCome(level, a, rare), "a first Rare refused");
			made.add(uuid("rare1"));
			record.settled(rare.id(), uuid("rare1"), level.dimension(), Optional.of(a), Rarity.RARE, "Ada", 1);
			helper.assertTrue(!Legends.canCome(level, a, rare), "a second Rare of one kind allowed in one village");
			helper.assertTrue(Legends.canCome(level, b, rare), "a Rare refused in another village");
			helper.assertTrue(Legends.canCome(nether, a, rare), "a Rare refused in a Nether village at the same spot");

			// the command (the player's way in) refuses too, and says why
			Villager v = villager(helper, new BlockPos(13, 2, 13));
			List<Component> said = new ArrayList<>();
			helper.assertTrue(LegendCommand.make(source(helper, new BlockPos(13, 2, 14), said), TEST) == 0 && !ModAttachments.LEGEND.has(v), "the command made a second Rare");
			helper.assertTrue(said.stream().anyMatch(c -> key(c).equals("message.aliveworkplace.legend.refused.rare")), "refusal: " + said);

			helper.assertTrue(Legends.canCome(level, b, legendary), "a first Legendary refused");
			made.add(uuid("legendary1"));
			record.settled(legendary.id(), uuid("legendary1"), level.dimension(), Optional.of(b), Rarity.LEGENDARY, "Bo", 1);
			helper.assertTrue(!Legends.canCome(level, a, legendary) && !Legends.canCome(level, b, legendary), "a second Legendary allowed in this dimension");
			helper.assertTrue(!Legends.canCome(nether, a, legendary), "a second Legendary allowed in the Nether");

			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(!Legends.canCome(level, a, mythic), "a Mythic allowed in a Village");
			helper.assertTrue(LegendSlots.whyNot(level, a, mythic, null).map(LegendSlotsGameTests::key).orElse("").equals("message.aliveworkplace.legend.refused.mythic"), "Mythic refusal");
			entity.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(Legends.canCome(level, a, mythic), "a first Mythic refused in a City");
			made.add(uuid("mythic1"));
			record.settled(mythic.id(), uuid("mythic1"), level.dimension(), Optional.of(a), Rarity.MYTHIC, "Cy", 1);
			helper.assertTrue(Legends.canCome(level, a, mythic), "a second Mythic refused in a City");
			made.add(uuid("mythic2"));
			record.settled(mythic.id(), uuid("mythic2"), level.dimension(), Optional.of(a), Rarity.MYTHIC, "Di", 1);
			helper.assertTrue(!Legends.canCome(level, a, mythic), "a third Mythic allowed in a City");
			helper.assertTrue(Legends.canCome(level, b, mythic) == (LegendSlots.mythicCap(VillageRanks.of(level, b)) > 0), "another village's Mythics counted here");
			entity.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(record.holding(e -> e.rarity() == Rarity.MYTHIC && e.in(level.dimension().location().toString(), a)) == 2,
				"a village that fell back a rank lost its Mythics");
			helper.assertTrue(!Legends.canCome(level, a, mythic), "a Town over its cap took another Mythic");

			// the caps follow the config: a City of 3 takes a third; a short list is filled, a big number clamped
			WorkplaceConfig config = WorkplaceConfig.parse("{\"mythicLegendCap\": [0, 0, 1, 3]}");
			helper.assertTrue(config.mythicLegendCap.equals(List.of(0, 0, 1, 3)), "config list: " + config.mythicLegendCap);
			Legends.MYTHIC_CAP = config.mythicLegendCap.stream().mapToInt(Integer::intValue).toArray();
			entity.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(Legends.canCome(level, a, mythic), "a third Mythic refused with a City cap of 3");
			helper.assertTrue(WorkplaceConfig.parse("{\"mythicLegendCap\": [2, 99]}").mythicLegendCap.equals(List.of(2, 10, 1, 2)), "short or big list");
			helper.assertTrue(new WorkplaceConfig().mythicLegendCap.equals(List.of(0, 0, 1, 2)), "default caps");
			helper.assertTrue(WorkplaceConfig.parse("{}").mythicLegendCap.equals(List.of(0, 0, 1, 2)), "missing caps");
			Legends.MYTHIC_CAP = new int[] {0, 0, 0, 0};
			helper.assertTrue(!Legends.canCome(level, a, mythic), "a Mythic with every cap 0");
			helper.succeed();
		});
	}

	/** Killed with a grave, a Legend keeps their slot and the Undertaker's revive brings back the same Legend. */
	//$ gametest_ticks_batch AREA '100' '"legendGraveRevive"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendGraveRevive")
	public void legendGraveRevive(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		boolean graves = Graves.ENABLED;
		Graves.ENABLED = true;
		Leftovers.after(helper, () -> Graves.ENABLED = graves);
		hall(helper, HALL);
		Villager v = villager(helper, new BlockPos(6, 2, 6));
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			List<Component> said = new ArrayList<>();
			helper.assertTrue(LegendCommand.make(source(helper, new BlockPos(6, 2, 7), said), TEST) == 1, "make: " + said);
			UUID was = v.getUUID();
			made.add(was);
			LegendRecord record = LegendRecord.get(level);
			v.kill();
			LegendRecord.Entry dead = record.entry(was).orElse(null);
			helper.assertTrue(dead != null && dead.holds() && dead.grave().isPresent(), "after death: " + dead);
			BlockPos grave = dead.grave().get();
			helper.assertTrue(level.getBlockEntity(grave) instanceof GraveBlockEntity, "no grave at " + grave);
			record.newDay(level.getServer(), Chronicle.day(level) + 30);
			helper.assertTrue(record.entry(was).map(LegendRecord.Entry::holds).orElse(false), "a Legend in a grave lost their slot");
			helper.assertTrue(!Legends.canCome(level, helper.absolutePos(HALL), Legends.get(TEST).orElseThrow()), "a dead Legend's slot was free");
			Villager back = Graves.revive(level, grave);
			helper.assertTrue(back != null, "not revived");
			made.add(back.getUUID());
			LegendData data = ModAttachments.LEGEND.get(back);
			helper.assertTrue(data != null && data.id().equals(TEST) && Legends.of(back).isPresent(), "revived not as the Legend: " + data);
			LegendRecord.Entry entry = record.entry(back.getUUID()).orElse(null);
			helper.assertTrue(entry != null && entry.holds() && entry.grave().isEmpty() && entry.gone() == -1, "record after revive: " + entry);
			helper.succeed();
		});
	}

	/** A Legend turned into a zombie villager keeps their slot, and cured is the Legend again. */
	//$ gametest_ticks_batch AREA '200' '"legendZombieCure"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "legendZombieCure")
	public void legendZombieCure(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		hall(helper, HALL);
		Villager v = villager(helper, new BlockPos(6, 2, 6));
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			List<Component> said = new ArrayList<>();
			helper.assertTrue(LegendCommand.make(source(helper, new BlockPos(6, 2, 7), said), TEST) == 1, "make: " + said);
			made.add(v.getUUID());
			ZombieVillager zombie = v.convertTo(EntityType.ZOMBIE_VILLAGER, false); // what a zombie does to a villager it kills
			helper.assertTrue(zombie != null, "no zombie");
			made.add(zombie.getUUID());
			helper.assertTrue(ModAttachments.LEGEND.has(zombie), "the zombie villager isn't the Legend");
			LegendRecord.Entry entry = LegendRecord.get(level).entry(zombie.getUUID()).orElse(null);
			helper.assertTrue(entry != null && entry.holds() && entry.zombie(), "record as a zombie: " + entry);
			helper.assertTrue(!Legends.canCome(level, helper.absolutePos(HALL), Legends.get(TEST).orElseThrow()), "a zombie Legend's slot was free");
			try { // the cure (a golden apple on a weakened zombie villager) with its wait cut to a tick
				var start = ZombieVillager.class.getDeclaredMethod("startConverting", UUID.class, int.class);
				start.setAccessible(true);
				start.invoke(zombie, null, 1);
			} catch (ReflectiveOperationException e) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("can't start the cure: " + e);
			}
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(zombie.isRemoved(), "not cured");
				List<Villager> cured = level.getEntitiesOfClass(Villager.class, helper.getBounds(), ModAttachments.LEGEND::has);
				helper.assertTrue(cured.size() == 1, "cured Legends: " + cured.size());
				Villager back = cured.get(0);
				made.add(back.getUUID());
				helper.assertTrue(Legends.of(back).map(Legend::id).equals(Optional.of(TEST)), "cured, not the Legend");
				LegendRecord.Entry after = LegendRecord.get(level).entry(back.getUUID()).orElse(null);
				helper.assertTrue(after != null && after.holds() && !after.zombie(), "record after the cure: " + after);
				helper.succeed();
			});
		});
	}

	/** Dead with no grave, or with their grave broken, a Legend's slot frees after 7 days; the record survives a reload. */
	//$ gametest_ticks_batch AREA '100' '"legendFallsAfterSevenDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendFallsAfterSevenDays")
	public void legendFallsAfterSevenDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		boolean graves = Graves.ENABLED;
		Leftovers.after(helper, () -> Graves.ENABLED = graves);
		int radius = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 16; // the halls of the tests beside this one stay out of reach
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = radius);
		VillageHallBlockEntity entity = hall(helper, HALL);
		Villager first = villager(helper, new BlockPos(6, 2, 6));
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			LegendRecord record = LegendRecord.get(level);
			BlockPos hall = helper.absolutePos(HALL);
			Legend rare = Legends.get(TEST).orElseThrow();
			List<Component> said = new ArrayList<>();
			Graves.ENABLED = false;
			helper.assertTrue(LegendCommand.make(source(helper, new BlockPos(6, 2, 7), said), TEST) == 1, "make: " + said);
			UUID firstId = first.getUUID();
			made.add(firstId);
			long today = Chronicle.day(level);
			first.kill();
			LegendRecord.Entry dead = record.entry(firstId).orElseThrow();
			helper.assertTrue(dead.holds() && dead.grave().isEmpty() && dead.gone() == today, "dead with no grave: " + dead);
			record.newDay(level.getServer(), today + 6);
			helper.assertTrue(record.entry(firstId).orElseThrow().holds() && !Legends.canCome(level, hall, rare), "the slot freed before 7 days");
			List<LegendRecord.Entry> fell = record.newDay(level.getServer(), today + 7);
			helper.assertTrue(fell.size() >= 1 && !record.entry(firstId).orElseThrow().holds() && record.entry(firstId).orElseThrow().fell() == today + 7,
				"not fallen after 7 days: " + record.entry(firstId));
			helper.assertTrue(Legends.canCome(level, hall, rare), "a fallen Legend's slot still held");
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.LEGEND && key(e.text()).equals("chronicle.aliveworkplace.legend.fallen")),
				"no 'fallen' in the chronicle");

			// with a grave, then the grave broken: 7 days from the day it's found broken
			Graves.ENABLED = true;
			Villager second = villager(helper, new BlockPos(8, 2, 8));
			helper.assertTrue(LegendCommand.make(source(helper, new BlockPos(8, 2, 9), said), TEST) == 1, "make again: " + said);
			UUID secondId = second.getUUID();
			made.add(secondId);
			second.kill();
			BlockPos grave = record.entry(secondId).orElseThrow().grave().orElse(null);
			helper.assertTrue(grave != null, "no grave");
			record.newDay(level.getServer(), today + 20);
			helper.assertTrue(record.entry(secondId).orElseThrow().holds(), "a grave's Legend fell");
			level.setBlockAndUpdate(grave, Blocks.AIR.defaultBlockState());
			record.newDay(level.getServer(), today + 21);
			LegendRecord.Entry broken = record.entry(secondId).orElseThrow();
			helper.assertTrue(broken.holds() && broken.grave().isEmpty() && broken.gone() == today + 21, "grave broken: " + broken);
			record.newDay(level.getServer(), today + 28);
			helper.assertTrue(!record.entry(secondId).orElseThrow().holds(), "not fallen 7 days after the grave broke");

			// the record survives a reload, and an old entry with only its id and villager reads with defaults
			CompoundTag tag = record.save(new CompoundTag(), level.registryAccess());
			LegendRecord loaded = LegendRecord.load(tag, level.registryAccess());
			helper.assertTrue(loaded.entries().equals(record.entries()), "record after reload differs");
			CompoundTag old = new CompoundTag();
			net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
			CompoundTag one = new CompoundTag();
			one.putString("id", TEST.toString());
			one.putIntArray("villager", net.minecraft.core.UUIDUtil.uuidToIntArray(uuid("old")));
			list.add(one);
			old.put("legends", list);
			LegendRecord.Entry bare = LegendRecord.load(old, level.registryAccess()).entry(uuid("old")).orElse(null);
			helper.assertTrue(bare != null && bare.holds() && bare.rarity() == Rarity.RARE && bare.hall().isEmpty() && bare.dimension().equals("minecraft:overworld"),
				"defaults: " + bare);
			helper.succeed();
		});
	}

	/** A hall broken and placed again: its Legends join it at its next round. */
	//$ gametest_ticks_batch AREA '100' '"legendHallRejoin"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendHallRejoin")
	public void legendHallRejoin(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		int radius = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 16; // the halls of the tests beside this one stay out of reach
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = radius);
		hall(helper, HALL);
		Villager v = villager(helper, new BlockPos(6, 2, 6));
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			List<Component> said = new ArrayList<>();
			helper.assertTrue(LegendCommand.make(source(helper, new BlockPos(6, 2, 7), said), TEST) == 1, "make: " + said);
			made.add(v.getUUID());
			helper.assertTrue(ModAttachments.LEGEND.get(v).hall().equals(Optional.of(helper.absolutePos(HALL))), "not in the test's village at first");
			helper.setBlock(HALL, Blocks.AIR);
			BlockPos moved = new BlockPos(10, 2, 10);
			VillageHallBlockEntity entity = hall(helper, moved);
			helper.runAfterDelay(3, () -> {
				BlockPos at = helper.absolutePos(moved);
				LegendSlots.round(level, at);
				LegendData data = ModAttachments.LEGEND.get(v);
				helper.assertTrue(data != null && data.hall().equals(Optional.of(at)), "didn't join the new hall: " + data);
				helper.assertTrue(LegendRecord.get(level).entry(v.getUUID()).map(LegendRecord.Entry::hall).equals(Optional.of(Optional.of(at))), "record hall");
				helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.LEGEND && key(e.text()).equals("chronicle.aliveworkplace.legend.joined")),
					"no 'joined' line");
				helper.succeed();
			});
		});
	}

	/** A Mythic announcement reaches a player 5,000 blocks away (gold, with the direction); a Rare one doesn't, but reaches the owner. */
	//$ gametest_ticks_batch AREA '100' '"legendAnnouncements"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendAnnouncements")
	public void legendAnnouncements(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleanUp(helper, List.of());
		VillageHallBlockEntity entity = hall(helper, HALL);
		Villager v = villager(helper, new BlockPos(6, 2, 6));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Legend rare = Legends.get(TEST).orElseThrow();
			Legend mythic = legend("slots_mythic", "mythic");
			player.setPos(hall.getX() + 5000, hall.getY(), hall.getZ());
			helper.assertTrue(LegendSlots.announce(level, hall, v, mythic, false).contains(player), "a Mythic didn't reach a player 5,000 blocks away");
			helper.assertTrue(!LegendSlots.announce(level, hall, v, rare, false).contains(player), "a Rare reached a player 5,000 blocks away");
			helper.assertTrue(!LegendSlots.audience(level, hall, hall, Rarity.LEGENDARY).contains(player), "a Legendary reached a player 5,000 blocks away");
			entity.setOwner(player.getUUID(), player.getName().getString());
			helper.assertTrue(LegendSlots.announce(level, hall, v, rare, true).contains(player), "a Rare didn't reach the hall's owner far away");
			entity.setOwner(null, "");
			player.setPos(hall.getX() + 3, hall.getY(), hall.getZ());
			helper.assertTrue(LegendSlots.audience(level, hall, hall, Rarity.RARE).contains(player), "a Rare didn't reach a player in the village");

			Component m = LegendSlots.message(level, hall, hall, Component.literal("Ada"), mythic, false);
			helper.assertTrue(key(m).equals("message.aliveworkplace.legend.mythic.settled") && m.getStyle().getColor() != null
				&& m.getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.GOLD)), "Mythic message: " + m);
			Object[] args = ((TranslatableContents) m.getContents()).getArgs();
			helper.assertTrue(args.length == 5 && args[4] instanceof Component dir && key(dir).startsWith("screen.aliveworkplace.hall.dir."), "direction: " + m);
			helper.assertTrue(key(LegendSlots.message(level, hall, hall, Component.literal("Ada"), rare, true)).equals("message.aliveworkplace.legend.guest"), "Rare guest");
			helper.assertTrue(key(LegendSlots.message(level, null, hall, Component.literal("Ada"), rare, false)).equals("message.aliveworkplace.legend.settled"), "Rare settled");
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.LEGEND && key(e.text()).equals("chronicle.aliveworkplace.legend.settled")),
				"no chronicle line");
			helper.assertTrue(Chronicle.Kind.LEGEND.icon == Items.NETHER_STAR, "chronicle icon");

			Language lang = Language.getInstance();
			List<String> missing = new ArrayList<>();
			for (String k : List.of("message.aliveworkplace.legend.settled", "message.aliveworkplace.legend.guest", "message.aliveworkplace.legend.mythic.settled",
					"message.aliveworkplace.legend.mythic.guest", "message.aliveworkplace.legend.the_wilds", "message.aliveworkplace.legend.refused.rare",
					"message.aliveworkplace.legend.refused.legendary", "message.aliveworkplace.legend.refused.mythic", "chronicle.aliveworkplace.legend.settled",
					"chronicle.aliveworkplace.legend.guest", "chronicle.aliveworkplace.legend.fallen", "chronicle.aliveworkplace.legend.joined")) {
				if (!lang.has(k)) {
					missing.add(k);
				}
			}
			helper.assertTrue(missing.isEmpty(), "missing sentences: " + missing);
			helper.succeed();
		});
	}

	/** The lang key of a message (failures come wrapped in a red empty component). */
	private static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return c.getSiblings().isEmpty() ? "" : key(c.getSiblings().get(0));
	}

	/** An op's command source at {@code pos} that keeps what it is told. */
	private static CommandSourceStack source(GameTestHelper helper, BlockPos pos, List<Component> said) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message);
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
		return new CommandSourceStack(out, Vec3.atCenterOf(helper.absolutePos(pos)), Vec2.ZERO, helper.getLevel(), 4, "test", Component.literal("test"),
			helper.getLevel().getServer(), null);
	}
}
