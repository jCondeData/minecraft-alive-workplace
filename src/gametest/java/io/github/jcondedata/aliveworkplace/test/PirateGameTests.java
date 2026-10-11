package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Conditions;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drowned pirates (ROADMAP 32.8), on a sea built in the test area: a strip of beach along the north (z 0 to 3, its top
 * at y 7) with the hall on it, and to the south a pool six deep (water y 2 to 7) walled with glass. The Pirate Ship
 * anchors on the water with its bow to the village and its hull in the sea; the crew wades ashore at the beach nearest
 * it; at dawn they go back into the sea; when Captain Silas Brine falls the ship burns and sinks, every block of it but
 * one the test put on its deck, and its chest is left on a raft; a restart mid-sinking; the switches.
 */
public class PirateGameTests {
	private static final String AREA = "aliveworkplace_test:tall_area";
	private static final BlockPos HALL = new BlockPos(1, 8, 1);
	/** The column the ship anchors in: its middle (the ship lies from x 2 to 28, z 10 to 18, its bow west). */
	private static final BlockPos SHIP = new BlockPos(15, 7, 14);
	/** The beach nearest the ship, and where the raft goes (past the bow there's no water, so off its north side). */
	private static final BlockPos BEACH = new BlockPos(15, 8, 3);
	private static final BlockPos RAFT = new BlockPos(15, 8, 7);
	private static final long DAY = 24000;
	private static final ResourceLocation PIRATES = AliveWorkplace.id("drowned_pirates");

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/**
	 * The beach and the sea, a Town hall on the beach in a beach biome with a village radius of 16 and four villagers;
	 * what the test changes goes when it ends.
	 */
	private static BlockPos coast(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Set<String> off = Threats.DISABLED;
		boolean open = Expansions.openForTests;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		VillageHalls.RADIUS = 16;
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y <= 7; y++) {
					BlockState block = z <= 3 ? (y == 7 ? Blocks.SAND : Blocks.STONE).defaultBlockState()
						: x == 0 || x == 29 || z == 29 ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState();
					helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), block, 2);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, hall, "minecraft:beach");
		Conditions.forgetCoasts();
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG))
				.forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Threats.DISABLED = off;
			Expansions.openForTests = open;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			fill(level, hall, old);
			Conditions.forgetCoasts();
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(3 + i, 8, 1));
		}
		return hall;
	}

	private static void rank(ServerLevel level, BlockPos hall, VillageRanks.Rank rank) {
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(rank);
	}

	private static void fill(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	private static Culture pirates(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(PIRATES);
		helper.assertTrue(culture.isPresent(), "drowned_pirates.json isn't loaded");
		return culture.get();
	}

	/** How often, in 400 rolls, the pirates are the ones that come. */
	private static int pirateCamps(ServerLevel level, BlockPos hall) {
		int n = 0;
		for (int i = 0; i < 400; i++) {
			if (Lairs.comer(level, hall, true, RandomSource.create(2000L + i)).equals(Optional.of(PIRATES))) {
				n++;
			}
		}
		return n;
	}

	private static Lairs.Lair anchor(GameTestHelper helper, BlockPos hall) {
		Lairs.Lair lair = Lairs.found(helper.getLevel(), hall, helper.absolutePos(SHIP), PIRATES);
		helper.assertTrue(lair != null && lair.culture().equals(PIRATES), "no pirate ship: " + lair);
		return lair;
	}

	private static double horizontal(BlockPos a, double x, double z) {
		double dx = a.getX() + 0.5 - x;
		double dz = a.getZ() + 0.5 - z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * A coastal Town: pirates come three times in four (against the bandits); the ship anchors on the water with its bow
	 * to the village, its hull in the sea and its hold dry, the sea round it untouched; Captain Silas Brine in his coat in
	 * his cabin, his crew of 8 (skeleton gunners with Flame bows and black caps); the loot chest. A raid from it: the crew
	 * appears in the water off the beach nearest the ship; a save and load keeps the ship; at dawn they go back into the sea.
	 */
	//$ gametest_ticks_batch AREA '100' '"piratesAnchor"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "piratesAnchor")
	public void aShipAnchorsAndItsCrewWadesAshore(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			Culture culture = pirates(helper);
			helper.assertTrue(culture.arrival() == Culture.Arrival.SHORE && culture.hours() == Culture.Hours.NIGHT && culture.where().coast(), "the culture: " + culture);
			int camps = pirateCamps(level, hall);
			helper.assertTrue(camps > 260 && camps < 340, "pirates in 400 lairs by a coastal Town: " + camps);

			at(level, 80, 13000);
			Lairs.Lair lair = anchor(helper, hall);
			Optional<Ships.Placed> placed = Ships.of(level, hall);
			helper.assertTrue(placed.isPresent() && placed.get().waterTop() == helper.absolutePos(SHIP).getY(), "the ship isn't on the water: " + placed);
			helper.assertTrue(placed.get().rotation() == Rotation.COUNTERCLOCKWISE_90, "its bow isn't to the village (west): " + placed.get().rotation());
			// Every block of the ship stands; its hull goes down into the sea, its hold is dry, the sea round it stays.
			Map<BlockPos, BlockState> blocks = Ships.blocks(level, placed.get());
			int hull = 0;
			int hold = 0;
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				BlockState now = level.getBlockState(e.getKey());
				if (e.getValue().isAir()) {
					helper.assertTrue(now.isAir(), "the hold isn't dry at " + e.getKey() + ": " + now);
					hold++;
				} else {
					helper.assertTrue(MaterialRules.matches(now, e.getValue()), "a ship block is missing at " + e.getKey() + ": " + now + " for " + e.getValue());
					if (e.getKey().getY() < placed.get().waterTop()) {
						hull++;
					}
				}
			}
			helper.assertTrue(blocks.size() > 900 && hull > 60 && hold > 30, "the ship: " + blocks.size() + " blocks, " + hull + " under water, a hold of " + hold);
			for (BlockPos sea : List.of(new BlockPos(15, 7, 9), new BlockPos(15, 5, 19), new BlockPos(1, 6, 14), new BlockPos(28, 2, 14))) {
				helper.assertTrue(level.getFluidState(helper.absolutePos(sea)).is(FluidTags.WATER), "the sea is gone at " + sea);
			}
			// Captain Silas Brine, in his coat, in his cabin; his crew.
			Mob captain = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(captain != null && captain.getType() == EntityType.DROWNED && captain.getCustomName().getString().equals("Captain Silas Brine")
				&& captain.isCustomNameVisible() && Lairs.named(lair), "the captain: " + captain);
			ItemStack coat = captain.getItemBySlot(EquipmentSlot.CHEST);
			helper.assertTrue(coat.is(Items.LEATHER_CHESTPLATE) && coat.has(DataComponents.DYED_COLOR) && captain.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.TRIDENT)
				&& captain.getMaxHealth() >= 60, "his coat, trident and health: " + coat + " " + captain.getMaxHealth());
			helper.assertTrue(captain.blockPosition().getY() == placed.get().waterTop() + 3 && captain.distanceToSqr(lair.pos().getCenter()) > 6 * 6,
				"he isn't on deck in his cabin at the stern: " + captain.blockPosition() + ", the deck's middle " + lair.pos());
			List<Mob> men = new ArrayList<>(Lairs.band(level, lair));
			men.removeIf(m -> m.getUUID().equals(lair.captain()));
			helper.assertTrue(men.size() == 8 && men.stream().allMatch(m -> m.getType() == EntityType.DROWNED || m.getType() == EntityType.SKELETON),
				"his crew: " + men);
			Mob gunner = men.stream().filter(m -> m.getType() == EntityType.SKELETON).findFirst().orElse(null);
			helper.assertTrue(gunner != null, "no gunner in the crew");
			ItemStack bow = gunner.getItemBySlot(EquipmentSlot.MAINHAND);
			ItemStack cap = gunner.getItemBySlot(EquipmentSlot.HEAD);
			int flame = bow.getEnchantments().getLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FLAME));
			helper.assertTrue(bow.is(Items.BOW) && flame == 1 && cap.is(Items.LEATHER_HELMET) && cap.has(DataComponents.UNBREAKABLE) && cap.has(DataComponents.DYED_COLOR),
				"the gunner's Flame bow and black cap: " + bow + " " + cap);
			// The loot chest, in the captain's cabin.
			boolean loot = false;
			for (BlockPos p : blocks.keySet()) {
				if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null
					&& chest.getLootTable().location().equals(AliveWorkplace.id("chests/pirate_ship"))) {
					loot = true;
				}
			}
			helper.assertTrue(loot, "no chest of the pirate ship's loot");

			// The raid: the crew wades ashore at the beach nearest the ship.
			BlockPos beach = Ships.beach(level, hall, lair.pos());
			helper.assertTrue(helper.absolutePos(BEACH).equals(beach), "the beach nearest the ship: " + beach + ", not " + helper.absolutePos(BEACH));
			at(level, 80, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, culture, RandomSource.create(5L));
			helper.assertTrue(raid != null, "no raid");
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == 8, "the raiders: " + raiders);
			for (Mob m : raiders) {
				helper.assertTrue(horizontal(beach, m.getX(), m.getZ()) <= 5 && m.getZ() > beach.getZ(),
					"a pirate came ashore away from the beach nearest the ship: " + m.blockPosition());
			}
			Lairs.Lair out = Lairs.near(level, hall).orElseThrow();
			helper.assertTrue(out.out() == 8, "the crew isn't out: " + out);
			// Saved and loaded: the lair and the ship.
			Lairs.Data.get(level).roundTrip(level.registryAccess());
			Ships.Data.get(level).roundTrip(level.registryAccess());
			helper.assertTrue(Lairs.near(level, hall).equals(Optional.of(out)) && Ships.of(level, hall).equals(placed), "after a save and load: " + Ships.of(level, hall));
			// At dawn they go back into the sea, and those alive rejoin the ship.
			raiders.get(0).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			ThreatData.get(level).begin(new ThreatData.Under(hall, PIRATES, 8, level.getGameTime() - 2000));
			at(level, 80, 22500);
			VillageRaids.tick(level, hall, 52, 0, 80, d -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty() && VillageRaids.raiders(level, hall).isEmpty(), "the pirates didn't go back into the sea at dawn");
			Lairs.Lair home = Lairs.near(level, hall).orElseThrow();
			helper.assertTrue(home.strength() == 7 && home.out() == 0, "after the raid: " + home);
			helper.succeed();
		});
	}

	/**
	 * Captain Silas Brine falls: his chest is put on a log raft on the water at once, and the ship burns and sinks over
	 * ten seconds, top down; then every block of it is gone (what was under the waterline is sea again) but the block the
	 * test put on its deck. The chronicle remembers it.
	 */
	//$ gametest_ticks_batch AREA '320' '"piratesSink"'
	@GameTest(template = AREA, timeoutTicks = 320, batch = "piratesSink")
	public void theCaptainFallsAndTheShipSinks(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			at(level, 90, 13000);
			Lairs.Lair lair = anchor(helper, hall);
			Ships.Placed ship = Ships.of(level, hall).orElseThrow();
			Map<BlockPos, BlockState> blocks = Ships.blocks(level, ship);
			BlockPos onDeck = lair.pos(); // the middle of the deck: air in the blueprint
			helper.assertTrue(level.getBlockState(onDeck).isAir() && !blocks.containsKey(onDeck), "the deck's middle isn't open: " + level.getBlockState(onDeck));
			level.setBlockAndUpdate(onDeck, Blocks.GOLD_BLOCK.defaultBlockState());
			Mob captain = (Mob) level.getEntity(lair.captain());
			captain.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && Lairs.resting(level, hall) == Lairs.REST_DAYS, "his death didn't break the lair");
			helper.assertTrue(Ships.of(level, hall).isEmpty() && Ships.sinking(level).size() == 1, "the ship isn't sinking");
			// The chest, on a raft of logs on the water.
			BlockPos raft = helper.absolutePos(RAFT);
			helper.assertTrue(level.getBlockEntity(raft) instanceof ChestBlockEntity chest && chest.getLootTable() != null
				&& chest.getLootTable().location().equals(AliveWorkplace.id("chests/pirate_ship")), "no loot chest on the raft at " + raft + ": " + level.getBlockState(raft));
			helper.assertTrue(level.getBlockState(raft.below()).is(Blocks.SPRUCE_LOG) && level.getBlockState(raft.below().east()).is(Blocks.SPRUCE_LOG)
				&& level.getFluidState(raft.below().below()).is(FluidTags.WATER), "the raft isn't on the water: " + level.getBlockState(raft.below()));
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			String last = entity.chronicle().get(entity.chronicle().size() - 1).text().getString();
			helper.assertTrue(last.equals("Captain Silas Brine fell and his pirate ship burned and sank"), "the chronicle: " + last);
			// Halfway: the masts are down, the hull still floats.
			helper.runAfterDelay(Ships.SINK_TICKS / 2, () -> {
				int top = blocks.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
				BlockPos masthead = blocks.keySet().stream().filter(p -> p.getY() == top && !blocks.get(p).isAir()).findFirst().orElseThrow();
				helper.assertTrue(level.getBlockState(masthead).isAir(), "the masthead still stands halfway: " + level.getBlockState(masthead));
				helper.assertTrue(blocks.entrySet().stream().anyMatch(e -> e.getKey().getY() < ship.waterTop() && !e.getValue().isAir()
					&& MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue())), "the hull sank all at once");
			});
			helper.runAfterDelay(Ships.SINK_TICKS + 10, () -> {
				for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
					BlockState now = level.getBlockState(e.getKey());
					helper.assertFalse(!e.getValue().isAir() && MaterialRules.matches(now, e.getValue()), "a ship block is left at " + e.getKey() + ": " + now);
					if (e.getKey().getY() <= ship.waterTop()) {
						helper.assertTrue(level.getFluidState(e.getKey()).is(FluidTags.WATER), "no sea where the hull was at " + e.getKey() + ": " + now);
					}
				}
				helper.assertTrue(level.getBlockState(onDeck).is(Blocks.GOLD_BLOCK), "the block put on the deck went with the ship");
				helper.assertTrue(level.getBlockEntity(raft) instanceof ChestBlockEntity, "the raft's chest went with the ship");
				helper.assertTrue(Ships.sinking(level).isEmpty(), "the ship is still sinking");
				helper.succeed();
			});
		});
	}

	/**
	 * A restart halfway through the sinking: it carries on and finishes. A chest a player took before the captain fell:
	 * no raft, and the ship still sinks. The lines a player reads. The switches: the config's {@code drowned_pirates} off,
	 * Milestone 32 closed, a Village, and a Town inland get no pirates.
	 */
	//$ gametest_ticks_batch AREA '320' '"piratesRestart"'
	@GameTest(template = AREA, timeoutTicks = 320, batch = "piratesRestart")
	public void aSinkingShipCarriesOnAfterARestartAndTheSwitches(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			rank(level, hall, VillageRanks.Rank.TOWN);
			at(level, 100, 13000);
			Lairs.Lair lair = anchor(helper, hall);
			Ships.Placed ship = Ships.of(level, hall).orElseThrow();
			Map<BlockPos, BlockState> blocks = Ships.blocks(level, ship);
			// A player took the loot chest before the captain fell: no raft.
			for (BlockPos p : blocks.keySet()) {
				if (level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
					chest.setLootTable(null);
					level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				}
			}
			((Mob) level.getEntity(lair.captain())).hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(level.getBlockState(helper.absolutePos(RAFT)).isAir() && !level.getBlockState(helper.absolutePos(RAFT).below()).is(Blocks.SPRUCE_LOG),
				"a raft without a chest");
			helper.runAfterDelay(60, () -> {
				// A restart: the sinking is read back from the save and goes on from where it was.
				Ships.Data.get(level).roundTrip(level.registryAccess());
				helper.assertTrue(Ships.sinking(level).size() == 1 && Ships.sinking(level).get(0).ship().equals(ship), "the sinking was lost in a restart");
			});
			helper.runAfterDelay(Ships.SINK_TICKS + 10, () -> {
				for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
					helper.assertFalse(!e.getValue().isAir() && MaterialRules.matches(level.getBlockState(e.getKey()), e.getValue()),
						"a ship block is left at " + e.getKey() + " after the restart");
				}
				helper.assertTrue(Ships.sinking(level).isEmpty(), "the ship is still sinking");

				// The lines a player reads.
				for (String key : List.of("message.aliveworkplace.pirates.camp_named", "message.aliveworkplace.pirates.broken_named",
					"message.aliveworkplace.pirates.raid_named", "chronicle.aliveworkplace.pirates.broken_by_named", "chronicle.aliveworkplace.pirates.camp_named",
					"threat.aliveworkplace.drowned_pirates.name", "threat.aliveworkplace.drowned_pirates.lair", "entity.aliveworkplace.drowned_pirate")) {
					helper.assertTrue(Language.getInstance().has(key), "no text for " + key);
				}
				helper.assertTrue(Lairs.lairName(PIRATES).getString().equals("Pirate ship") && Lairs.cultureName(PIRATES).getString().equals("Drowned pirates"),
					"the names: " + Lairs.lairName(PIRATES).getString());

				// The switches.
				Lairs.forget(level);
				Threats.DISABLED = Set.of("drowned_pirates");
				helper.assertTrue(pirateCamps(level, hall) == 0, "pirates switched off in the config come");
				Threats.DISABLED = Set.of();
				Expansions.openForTests = false;
				helper.assertTrue(pirateCamps(level, hall) == 0, "pirates come with Milestone 32 closed");
				Expansions.openForTests = true;
				rank(level, hall, VillageRanks.Rank.VILLAGE);
				helper.assertTrue(pirateCamps(level, hall) == 0, "pirates come to a Village");
				rank(level, hall, VillageRanks.Rank.TOWN);
				helper.assertTrue(pirateCamps(level, hall) > 0, "no pirates by a coastal Town");
				fill(level, hall, "minecraft:plains");
				helper.assertTrue(pirateCamps(level, hall) > 0, "the coast was looked at again the same day");
				Conditions.forgetCoasts();
				helper.assertTrue(pirateCamps(level, hall) == 0 || Conditions.coast(level, hall), "pirates come to a Town inland");
				helper.succeed();
			});
		});
	}

	/**
	 * Where a ship may anchor: the water's depth and the sea under all of it as it would lie, bow to the village; not
	 * over the beach, not on water too shallow for it.
	 */
	//$ gametest_ticks_batch AREA '40' '"piratesSite"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "piratesSite")
	public void whereAShipMayAnchor(GameTestHelper helper) {
		BlockPos hall = coast(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Culture culture = pirates(helper);
			Culture.Sea sea = culture.lair().get().sea().orElseThrow();
			helper.assertTrue(sea.waterline() == 2 && sea.near() == 40 && sea.far() == 70 && sea.depth() == 5 && sea.sinks(), "the ship's sea: " + sea);
			var template = level.getStructureManager().get(AliveWorkplace.id("camp/pirate_ship")).orElseThrow();
			helper.assertTrue(template.getSize().getX() == 9 && template.getSize().getY() == 20 && template.getSize().getZ() == 27, "the ship's size: " + template.getSize());
			BlockPos top = Ships.waterTop(level, helper.absolutePos(SHIP));
			helper.assertTrue(top.equals(helper.absolutePos(SHIP)) && Ships.depth(level, top) == 6, "the sea's top and depth: " + top + " " + Ships.depth(level, top));
			helper.assertTrue(Ships.fits(level, top, hall, sea, template), "the open sea doesn't fit the ship");
			BlockPos inshore = Ships.waterTop(level, helper.absolutePos(new BlockPos(15, 7, 6)));
			helper.assertFalse(Ships.fits(level, inshore, hall, sea, template), "a ship fits over the beach");
			helper.assertFalse(Ships.fits(level, top, hall, new Culture.Sea(2, 40, 70, 7, true), template), "a ship fits on water too shallow for it");
			helper.assertTrue(Ships.facing(top, hall) == Rotation.COUNTERCLOCKWISE_90 && Ships.facing(hall, top) == Rotation.CLOCKWISE_90
				&& Ships.facing(top, top.north(5)) == Rotation.NONE && Ships.facing(top, top.south(5)) == Rotation.CLOCKWISE_180, "the bow's turn");
			helper.succeed();
		});
	}
}
