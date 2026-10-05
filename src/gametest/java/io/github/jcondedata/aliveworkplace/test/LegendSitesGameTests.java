package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
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
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 29.9, Legends found in the world: a camp is set down only for a player who owns (or is a friend of the owner of) a
 * qualifying Village Hall, and only once per structure start; the traveller is freed by a word, the prisoner by
 * breaking a bar (not by a meal or a word), the castaway by a cooked meal (not by a word or raw meat); the freed Legend
 * is a guest at the hall the next morning and not before; no lookups run with nobody online or with the switch off; the
 * record keeps used sites and captives through a save, and an old save loads with none. Structure starts are stood in
 * for by boxes in the test area and made-up keys ({@link LegendSites#offer} is what the per-player lookup calls). Every
 * roster and clock change is made and undone within one tick, as tests in other batches run beside these.
 */
public class LegendSitesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final BlockPos MIDDLE = new BlockPos(15, 1, 15);
	private static final long DAY = VillageNeeds.DAY;

	private static Legend legend(String site) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "found_" + site), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:cartographer\", \"title\": \"Found " + site + "\", \"lore\": \"the lore\","
				+ " \"names\": [\"Pip of the " + site + "\"], \"arrive\": [{\"way\": \"found\", \"site\": \"" + site + "\"}]}").getAsJsonObject());
	}

	/** The hall (owned by {@code owner}, on the list of villages), and everything the test made goes when it ends. */
	private static void setUp(GameTestHelper helper, ServerPlayer owner, List<String> keys) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setOwner(owner.getUUID(), owner.getName().getString());
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		Leftovers.after(helper, () -> {
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			keys.forEach(record::forgetSite);
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
	}

	/** Runs {@code body} with only {@code legends} loaded, then puts the roster and the clock back before the tick ends. */
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

	private static BlockPos hall(GameTestHelper helper) {
		return helper.absolutePos(HALL);
	}

	/** A camp of {@code site} set down in the middle of the area for the hall. */
	private static Villager camp(GameTestHelper helper, String site, Legend legend) {
		Villager v = LegendSites.place(helper.getLevel(), LegendSites.site(site), new LegendSites.Match(hall(helper), legend),
			helper.absolutePos(MIDDLE), RandomSource.create(29));
		helper.assertTrue(v != null, "the " + site + " camp is set down");
		return v;
	}

	private static boolean freed(GameTestHelper helper, Villager v) {
		return LegendRecord.get(helper.getLevel()).captive(v.getUUID()).map(LegendRecord.Captive::freed).orElse(false);
	}

	//$ gametest_ticks_batch AREA '100' '"legendSitesPlace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendSitesPlace")
	public void campOnlyForAQualifyingPlayerAndOnce(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		String portal = "test|minecraft:ruined_portal|" + helper.absolutePos(MIDDLE).asLong();
		String outpost = "test|minecraft:pillager_outpost|" + helper.absolutePos(MIDDLE).asLong();
		setUp(helper, owner, List.of(portal, outpost));
		ServerLevel level = helper.getLevel();
		// both stand at the site (mock players join at the world spawn, far beyond the hall's reach)
		BlockPos at = helper.absolutePos(MIDDLE.offset(0, 1, 6));
		owner.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		stranger.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		BlockPos a = helper.absolutePos(MIDDLE.above());
		BoundingBox anchor = new BoundingBox(a.getX() - 1, a.getY(), a.getZ() - 1, a.getX() + 1, a.getY() + 4, a.getZ() + 1);
		RandomSource random = RandomSource.create(29);
		staged(helper, List.of(legend("ruined_portal")), () -> {
			helper.assertTrue(LegendSites.offer(level, stranger, "ruined_portal", portal, anchor, anchor, random) == null,
				"no camp for a player who isn't the owner or a friend");
			helper.assertFalse(LegendRecord.get(level).siteUsed(portal), "the start stays free for someone who qualifies");
			helper.assertTrue(LegendSites.offer(level, owner, "outpost", outpost, anchor, anchor, random) == null,
				"no camp at a site no Legend's found way names");
			helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", owner.blockPosition()).isPresent(),
				"the owner qualifies (at " + owner.blockPosition() + ", captives " + LegendRecord.get(level).captives() + ")");
			Villager found = LegendSites.offer(level, owner, "ruined_portal", portal, anchor, anchor, random);
			helper.assertTrue(found != null, "the owner finds the traveller's camp");
			helper.assertTrue(LegendRecord.get(level).siteUsed(portal), "the structure start is marked in the record");
			helper.assertTrue(LegendSites.isCaptive(found) && found.isNoAi(), "the traveller waits at the camp");
			helper.assertTrue(found.getCustomName() != null && found.getCustomName().getString().contains("Pip"), "named from the Legend's names");
			helper.assertTrue(level.getBlockState(found.blockPosition().below()).isSolid(), "stands on the camp's ground");
			LegendRecord.get(level).forgetCaptive(found.getUUID());
			helper.assertTrue(LegendSites.offer(level, owner, "ruined_portal", portal, anchor, anchor, random) == null,
				"a structure start is used once");
			found.discard();
		});
		staged(helper, List.of(legend("ruined_portal")), () -> {
			io.github.jcondedata.aliveworkplace.build.Friends.get(level.getServer()).add(owner.getUUID(), stranger.getUUID(), "friend");
			try {
				String other = portal + "b";
				Villager found = LegendSites.offer(level, stranger, "ruined_portal", other, anchor, anchor, random);
				helper.assertTrue(found != null, "a friend of the owner finds one at another portal");
				LegendRecord.get(level).forgetSite(other);
				helper.assertTrue(LegendSites.offer(level, owner, "ruined_portal", portal + "c", anchor, anchor, random) == null,
					"not a second while the first is waiting");
			} finally {
				io.github.jcondedata.aliveworkplace.build.Friends.get(level.getServer()).remove(owner.getUUID(), stranger.getUUID());
			}
		});
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"legendSitesTraveller"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendSitesTraveller")
	public void travellerFreedByTalkAndGuestNextMorning(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner, List.of());
		ServerLevel level = helper.getLevel();
		Legend legend = legend("ruined_portal");
		staged(helper, List.of(legend), () -> {
			Villager traveller = camp(helper, "ruined_portal", legend);
			helper.assertTrue(level.getBlockState(traveller.blockPosition().below()).isSolid(), "the traveller stands in the camp");
			long day = Chronicle.day(level);
			helper.assertTrue(LegendSites.use(owner, traveller, InteractionHand.MAIN_HAND).consumesAction(), "the click is the traveller's");
			helper.assertTrue(freed(helper, traveller), "a word frees the traveller");
			helper.assertFalse(LegendSites.isCaptive(traveller) || traveller.isNoAi(), "free to walk off");
			// the same day's morning: not yet
			level.setDayTime((day - 1) * DAY + LegendGuests.MORNING_FROM + 100); // Chronicle.day counts from 1
			LegendSites.arrive(level, hall(helper));
			helper.assertTrue(LegendGuests.guest(level, hall(helper)) == null, "not a guest the same day");
			// the next morning: a guest at the hall
			level.setDayTime(day * DAY + LegendGuests.MORNING_FROM + 100);
			LegendSites.arrive(level, hall(helper));
			Villager guest = LegendGuests.guest(level, hall(helper));
			helper.assertTrue(guest != null, "a guest at the hall the next morning");
			LegendData data = ModAttachments.LEGEND.get(guest);
			helper.assertTrue(data.id().equals(legend.id()) && data.way().equals("found:ruined_portal"), "the found Legend, by the found way: " + data.way());
			helper.assertTrue(data.lastDay() == day + 1 + LegendGuests.STAY_DAYS - 1, "three days to settle");
			helper.assertTrue(traveller.isRemoved(), "the one who walked off is the guest now");
			helper.assertTrue(LegendRecord.get(level).captive(traveller.getUUID()).isEmpty(), "off the captives' list");
			((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setLegendGuests(LegendGuests.State.EMPTY);
		});
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"legendSitesPrisoner"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendSitesPrisoner")
	public void prisonerFreedByBreakingTheBarsNotByAMeal(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner, List.of());
		ServerLevel level = helper.getLevel();
		Legend legend = legend("outpost");
		staged(helper, List.of(legend), () -> {
			Villager prisoner = camp(helper, "outpost", legend);
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF, 3));
			LegendSites.use(owner, prisoner, InteractionHand.MAIN_HAND);
			helper.assertFalse(freed(helper, prisoner), "a meal doesn't open the cage");
			helper.assertTrue(owner.getMainHandItem().getCount() == 3, "and isn't taken");
			owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			LegendSites.use(owner, prisoner, InteractionHand.MAIN_HAND);
			helper.assertFalse(freed(helper, prisoner), "nor does a word");
			BlockPos bar = null;
			for (BlockPos p : BlockPos.betweenClosed(prisoner.blockPosition().offset(-3, -1, -3), prisoner.blockPosition().offset(3, 3, 3))) {
				if (level.getBlockState(p).is(Blocks.IRON_BARS)) {
					bar = p.immutable();
					break;
				}
			}
			helper.assertTrue(bar != null, "the cage has iron bars");
			helper.assertTrue(prisoner.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(bar)) < 3, "the prisoner is in the cage");
			owner.gameMode.destroyBlock(bar);
			helper.assertTrue(level.getBlockState(bar).isAir(), "the bar breaks");
			helper.assertTrue(freed(helper, prisoner), "breaking a bar frees the prisoner");
			helper.assertFalse(prisoner.isNoAi(), "free to walk off");
		});
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"legendSitesCastaway"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendSitesCastaway")
	public void castawayFreedByACookedMeal(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner, List.of());
		Legend legend = legend("shipwreck");
		staged(helper, List.of(legend), () -> {
			owner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // a creative player's meal isn't used up
			Villager castaway = camp(helper, "shipwreck", legend);
			LegendSites.use(owner, castaway, InteractionHand.MAIN_HAND);
			helper.assertFalse(freed(helper, castaway), "a word doesn't feed a castaway");
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF, 2));
			LegendSites.use(owner, castaway, InteractionHand.MAIN_HAND);
			helper.assertFalse(freed(helper, castaway), "raw meat isn't a meal");
			helper.assertTrue(owner.getMainHandItem().getCount() == 2, "and isn't taken");
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_COD, 2));
			LegendSites.use(owner, castaway, InteractionHand.MAIN_HAND);
			helper.assertTrue(freed(helper, castaway), "a cooked meal frees the castaway");
			helper.assertTrue(owner.getMainHandItem().getCount() == 1, "one meal is eaten");
		});
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"legendSitesLookups"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "legendSitesLookups")
	public void noLookupsWithNobodyOnlineOrSwitchedOff(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		setUp(helper, owner, List.of());
		staged(helper, List.of(legend("shipwreck")), () -> {
			long before = LegendSites.lookups;
			LegendSites.scan(List.of());
			helper.assertTrue(LegendSites.lookups == before, "no lookups with nobody online");
			LegendSites.scan(List.of(owner));
			helper.assertTrue(LegendSites.lookups - before == LegendSites.SITES.size(), "three lookups for one player in no structure: "
				+ (LegendSites.lookups - before));
			boolean was = LegendSites.ENABLED;
			try {
				LegendSites.ENABLED = false;
				long off = LegendSites.lookups;
				LegendSites.scan(List.of(owner));
				helper.assertTrue(LegendSites.lookups == off, "no lookups with legendSites off");
			} finally {
				LegendSites.ENABLED = was;
			}
		});
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"legendSitesSave"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "legendSitesSave")
	public void recordKeepsSitesAndCaptivesThroughASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LegendRecord old = LegendRecord.load(new CompoundTag(), level.registryAccess());
		helper.assertTrue(old.captives().isEmpty() && !old.siteUsed("x"), "an old save loads with no captives or used sites");
		LegendRecord record = new LegendRecord();
		record.useSite("test|minecraft:shipwreck|42");
		java.util.UUID who = java.util.UUID.randomUUID();
		record.putCaptive(new LegendRecord.Captive(who, ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "x"), "outpost",
			Optional.of(new BlockPos(1, 2, 3)), "minecraft:overworld", new BlockPos(0, 0, 0), new BlockPos(4, 4, 4), 7, 1234));
		LegendRecord back = LegendRecord.load(record.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		helper.assertTrue(back.siteUsed("test|minecraft:shipwreck|42"), "the used site is saved");
		LegendRecord.Captive c = back.captive(who).orElse(null);
		helper.assertTrue(c != null && c.site().equals("outpost") && c.freedDay() == 7 && c.hall().equals(Optional.of(new BlockPos(1, 2, 3)))
			&& c.holds(new BlockPos(4, 1, 0)) && !c.holds(new BlockPos(5, 1, 0)), "the captive is saved: " + c);
		helper.succeed();
	}
}
