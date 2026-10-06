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

	/**
	 * B68: the traveller's camp's map hangs on its barrel. The frame in legend/traveller_camp.nbt keeps the block it hangs
	 * on (TileX/Y/Z), and placing the template moves that to where the frame lands, so it loads attached to the barrel (no
	 * "Block-attached entity at invalid position" in the log), whichever way the camp is turned.
	 */
	//$ gametest_ticks_batch AREA '140' '"legendSitesCampFrame"'
	@GameTest(template = AREA, timeoutTicks = 140, batch = "legendSitesCampFrame")
	public void travellersMapHangsOnItsBarrelEveryWayRound(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = level.getStructureManager()
			.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "legend/traveller_camp")).orElse(null);
		helper.assertTrue(template != null, "the traveller's camp template loads");
		CompoundTag saved = template.save(new CompoundTag());
		net.minecraft.nbt.ListTag entities = saved.getList("entities", net.minecraft.nbt.Tag.TAG_COMPOUND);
		helper.assertTrue(entities.size() == 1 && entities.getCompound(0).getCompound("nbt").getInt("TileY") == 2
			&& entities.getCompound(0).getCompound("nbt").contains("TileX"), "the camp's frame is saved with the block it hangs on: " + entities);
		// vanilla logs this when a frame's saved block is more than 16 blocks from where it is set down
		List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
		var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("legendSitesCampFrame", null, null, true,
			org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
			@Override
			public void append(org.apache.logging.log4j.core.LogEvent event) {
				String message = event.getMessage().getFormattedMessage();
				if (message.contains("Block-attached entity at invalid position")) {
					errors.add(message);
				}
			}
		};
		var root = ((org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false))
			.getConfiguration().getRootLogger();
		appender.start();
		root.addAppender(appender, null, null);
		List<BoundingBox> boxes = new ArrayList<>();
		Villager traveller;
		try {
			// the camp four ways round, one in each corner of the area
			net.minecraft.world.level.block.Rotation[] rotations = net.minecraft.world.level.block.Rotation.values();
			BlockPos[] corners = {new BlockPos(2, 2, 2), new BlockPos(20, 2, 2), new BlockPos(2, 2, 20), new BlockPos(20, 2, 20)};
			for (int i = 0; i < rotations.length; i++) {
				var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(rotations[i]);
				BlockPos origin = template.getZeroPositionWithTransform(helper.absolutePos(corners[i]), net.minecraft.world.level.block.Mirror.NONE, rotations[i]);
				template.placeInWorld(level, origin, origin, settings, RandomSource.create(29), 2);
				boxes.add(template.getBoundingBox(settings, origin));
			}
			// and through the player's own way in: a Legend found beside a ruined portal
			traveller = camp(helper, "ruined_portal", legend("ruined_portal"));
		} finally {
			root.removeAppender("legendSitesCampFrame");
			appender.stop();
		}
		// a frame that isn't hung on a block pops off on its next survival check (every 100 ticks), dropping the map
		helper.runAfterDelay(120, () -> {
			LegendRecord.get(level).forgetCaptive(traveller.getUUID());
			traveller.discard();
			helper.assertTrue(errors.isEmpty(), "no frame is set down at an invalid position: " + errors);
			List<net.minecraft.world.entity.decoration.ItemFrame> frames = level.getEntitiesOfClass(
				net.minecraft.world.entity.decoration.ItemFrame.class, helper.getBounds(), f -> true);
			helper.assertTrue(frames.size() == 5, "five camps, five frames: " + frames.stream().map(f -> f.getPos().toString()).toList());
			for (BoundingBox box : boxes) {
				List<BlockPos> barrels = new ArrayList<>();
				BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).forEach(p -> {
					if (level.getBlockState(p).is(Blocks.BARREL)) {
						barrels.add(p.immutable());
					}
				});
				helper.assertTrue(barrels.size() == 1, "one barrel in the camp at " + box + ": " + barrels);
				BlockPos above = barrels.get(0).above();
				helper.assertTrue(frames.stream().anyMatch(f -> f.getPos().equals(above) && f.blockPosition().equals(above)
						&& f.getDirection() == net.minecraft.core.Direction.UP && f.getItem().is(Items.MAP) && f.survives()),
					"the map lies on the barrel at " + above + ": " + frames.stream().map(f -> f.getPos() + " " + f.getDirection() + " " + f.getItem()).toList());
			}
			helper.succeed();
		});
	}
}
