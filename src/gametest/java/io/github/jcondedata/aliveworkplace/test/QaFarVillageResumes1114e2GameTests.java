package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.hall.Banquets;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.AABB;

/**
 * Lane E QA (1114e, second pass): B1221, B1222, B1224 and B1225 checked from their decisions, not their code. A far village
 * (its hall's chunk not entity-ticking) keeps what it had, loads nothing, and picks up where it left off once someone is
 * back (a forced chunk stands for the returning player: it makes the hall's chunk entity-ticking, as a player near it does).
 */
public class QaFarVillageResumes1114e2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/** One step of a test's script: true when it's done and the next may run (it is asked again each tick till then). */
	private interface Step {
		boolean run();
	}

	/** Runs the steps in order, a step a tick at most once it's done, and succeeds after the last. */
	private static void script(GameTestHelper helper, Step... steps) {
		int[] at = {0};
		helper.onEachTick(() -> {
			while (at[0] < steps.length && steps[at[0]].run()) {
				at[0]++;
			}
			if (at[0] >= steps.length) {
				helper.succeed();
			}
		});
	}

	/** A step that holds {@code ticks} ticks, checking {@code each} every tick. */
	private static Step hold(int ticks, Runnable each) {
		int[] n = {0};
		return () -> {
			each.run();
			return ++n[0] >= ticks;
		};
	}

	/** Someone comes back: the chunks of {@code halls} are forced (cleared when the test ends). */
	private static Step comeBack(GameTestHelper helper, BlockPos... halls) {
		ServerLevel level = helper.getLevel();
		for (BlockPos h : halls) {
			ChunkPos c = new ChunkPos(h);
			Leftovers.after(helper, () -> level.setChunkForced(c.x, c.z, false));
		}
		return () -> {
			for (BlockPos h : halls) {
				ChunkPos c = new ChunkPos(h);
				level.setChunkForced(c.x, c.z, true);
			}
			return true;
		};
	}

	/** Waits (at most {@code max} ticks) until every one of {@code halls} is entity-ticking. */
	private static Step ticking(GameTestHelper helper, int max, BlockPos... halls) {
		ServerLevel level = helper.getLevel();
		int[] n = {0};
		return () -> {
			for (BlockPos h : halls) {
				if (!level.isPositionEntityTicking(h)) {
					helper.assertTrue(++n[0] < max, "the forced chunk at " + h + " isn't entity-ticking after " + max + " ticks");
					return false;
				}
			}
			return true;
		};
	}

	/** Removes the hall at {@code hall} after the test, if its chunk is loaded then. */
	private static void removeHallAfter(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static Set<BlockPos> on(Class<?> type, ServerLevel level) {
		try {
			var field = type.getDeclaredField("ON");
			field.setAccessible(true);
			Map<ResourceKey<Level>, Set<BlockPos>> map = (Map<ResourceKey<Level>, Set<BlockPos>>) field.get(null);
			return map.getOrDefault(level.dimension(), Set.of());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A hall at the far spot {@code hall}, with a Homes zone that renews old houses, surveyed while its chunk is loaded. */
	private static OldHouses.Survey surveyedHall(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		BitSet cells = new BitSet();
		for (int x = 2; x <= 5; x++) {
			for (int z = 0; z <= 3; z++) {
				cells.set(CityPlan.cellAt(hall, hall.offset(x, 0, z)));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells).editZone(0, "homes", "Homes 1", "", true));
		OldHouses.forget(level, hall);
		Optional<OldHouses.Survey> survey = OldHouses.request(level, hall);
		helper.assertTrue(survey.isPresent(), "setup: no survey for the renew zone");
		return survey.get();
	}

	// ---- B1221: old-house surveys ----------------------------------------------------------------------------------

	/**
	 * B1221's decision, "a far survey is kept, not dropped": a finished survey of a village everyone left stays while its
	 * hall's chunk is unloaded (and loads nothing), and once someone is back it is still there, and it is the one the
	 * Steward's next request uses (not surveyed again from nothing).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1114e2SurveyKept"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1114e2SurveyKept")
	public void aFarSurveyIsKeptAndUsedOnceSomeoneIsBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8100, -8100);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		Leftovers.after(helper, () -> OldHouses.forget(level, hall));
		removeHallAfter(helper, hall);
		OldHouses.Survey survey = surveyedHall(helper, hall);

		script(helper,
			() -> survey.done() && !loaded(level, hall), // the survey finishes, and everyone has left
			hold(100, () -> {
				helper.assertTrue(!loaded(level, hall), "something loaded the far surveyed hall's chunk again");
				helper.assertTrue(OldHouses.result(level, hall).isPresent(), "the far hall's finished survey was dropped while nobody was there");
			}),
			comeBack(helper, hall),
			ticking(helper, 1000, hall),
			hold(40, () -> helper.assertTrue(OldHouses.result(level, hall).isPresent(), "the survey was dropped once someone was back, though the hall stands")),
			() -> {
				Optional<OldHouses.Survey> again = OldHouses.request(level, hall);
				helper.assertTrue(again.isPresent(), "the Steward's request found no survey for the renew zone once back");
				helper.assertTrue(again.get() == survey, "the kept survey wasn't used: the Steward's request started a new one once someone was back");
				helper.assertTrue(again.get().done(), "the kept survey is no longer finished");
				return true;
			});
	}

	/**
	 * B1221: a far village's hall is broken while nobody is near (here, in a chunk loaded only for that, not
	 * entity-ticking): its survey is kept until the chunk is entity-ticking again, and is dropped once it is.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1114e2SurveyHallBroken"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1114e2SurveyHallBroken")
	public void aFarHallBrokenWhileAwayHasItsSurveyDroppedOnceBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8300, -8100);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		Leftovers.after(helper, () -> OldHouses.forget(level, hall));
		removeHallAfter(helper, hall);
		OldHouses.Survey survey = surveyedHall(helper, hall);

		script(helper,
			() -> survey.done() && !loaded(level, hall),
			() -> {
				level.getChunk(hall); // loaded (not entity-ticking) for a moment, and the hall broken
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
				helper.assertTrue(!level.isPositionEntityTicking(hall), "setup: the far chunk is entity-ticking");
				return true;
			},
			comeBack(helper, hall),
			ticking(helper, 1000, hall),
			hold(5, () -> { }),
			() -> {
				helper.assertTrue(OldHouses.result(level, hall).isEmpty(), "the survey of a hall broken while away is still kept once someone is back");
				return true;
			});
	}

	// ---- B1222: legend sites ---------------------------------------------------------------------------------------

	/**
	 * B1222's decision, "a hall whose chunk isn't entity-ticking is skipped": a site check while the player's only village
	 * is unloaded offers nothing and loads nothing; once the hall is entity-ticking again, the same site, checked the same
	 * way, qualifies for that hall (the skip never disqualifies it for good).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1114e2LegendSiteBack"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1114e2LegendSiteBack")
	public void aLegendSiteSkipsAFarHallThenQualifiesOnceItIsBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 8500, -8100);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Caravans.Data caravans = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			caravans.remove(hall);
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		removeHallAfter(helper, hall);
		Legend legend = Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1114e2_found_portal"), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:cartographer\", \"title\": \"Found portal\", \"lore\": \"the lore\","
				+ " \"names\": [\"Wren of the portal\"], \"conditions\": [], \"arrive\": [{\"way\": \"found\", \"site\": \"ruined_portal\"}]}").getAsJsonObject());
		Legends.setForTest(Map.of(legend.id(), legend));

		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getName().getString());
		entity.setCustomName(Component.literal("Farvale"));
		caravans.setWants(hall, Component.literal("Farvale"), List.of());
		BlockPos site = hall.offset(300, 0, 0);

		script(helper,
			() -> !loaded(level, hall),
			() -> {
				helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", site).isEmpty(), "a site offered a Legend for a village whose hall isn't loaded");
				helper.assertTrue(!loaded(level, hall), "the site check loaded the far hall's chunk");
				helper.assertTrue(LegendSites.qualifying(level, owner, "ruined_portal", site).isEmpty() && !loaded(level, hall), "a second check differed");
				return true;
			},
			comeBack(helper, hall),
			ticking(helper, 1000, hall),
			() -> {
				Optional<LegendSites.Match> match = LegendSites.qualifying(level, owner, "ruined_portal", site);
				helper.assertTrue(match.isPresent(), "once the village is loaded again the same site doesn't qualify (the skip disqualified it)");
				helper.assertTrue(match.get().hall().equals(hall) && match.get().legend().id().equals(legend.id()), "the match: " + match.get());
				return true;
			});
	}

	// ---- B1224: festivals and banquets -----------------------------------------------------------------------------

	/** A far festival and a far banquet, both on (their halls' rounds ran) at START + 500 today. */
	private static void festivalAndBanquet(GameTestHelper helper, BlockPos festival, BlockPos banquet) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		removeHallAfter(helper, festival);
		removeHallAfter(helper, banquet);
		level.setDayTime(Math.floorDiv(time, 24000L) * 24000L + Festivals.START + 500);
		level.getChunk(festival);
		level.setBlock(festival, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		VillageHallBlockEntity f = (VillageHallBlockEntity) level.getBlockEntity(festival);
		f.setFestivalDay(Chronicle.day(level));
		Festivals.round(level, festival, f, 0);
		level.getChunk(banquet);
		level.setBlock(banquet, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		VillageHallBlockEntity b = (VillageHallBlockEntity) level.getBlockEntity(banquet);
		b.setBanquet(Chronicle.day(level), true);
		Banquets.round(level, banquet, b);
		helper.assertTrue(Festivals.isOn(level, festival) && on(Festivals.class, level).contains(festival), "setup: the festival isn't on");
		helper.assertTrue(Banquets.isOn(level, banquet) && on(Banquets.class, level).contains(banquet), "setup: the banquet isn't on");
	}

	/** A player in creative (a festival goer who never comes to harm) at {@code at}. */
	private static ServerPlayer goer(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.CREATIVE);
		player.teleportTo(helper.getLevel(), at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0f, 0f);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/**
	 * B1224's decision: a festival (and a banquet) on when everyone leaves stays in the list without loading its hall;
	 * when someone is back before dusk it is still on, and the festival goes on (a player at it gets the festival's
	 * standing, from the festival tick).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1114e2FestivalBackBeforeDusk"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1114e2FestivalBackBeforeDusk")
	public void aFarFestivalIsStillOnWhenSomeoneIsBackBeforeDusk(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos festival = far(helper, 8100, 8500);
		BlockPos banquet = far(helper, 8300, 8500);
		helper.assertTrue(!loaded(level, festival) && !loaded(level, banquet), "setup: the far chunks are already loaded");
		festivalAndBanquet(helper, festival, banquet);
		ServerPlayer[] player = {null};
		int[] waited = {0};

		script(helper,
			() -> !loaded(level, festival) && !loaded(level, banquet),
			hold(100, () -> {
				helper.assertTrue(!loaded(level, festival) && !loaded(level, banquet), "something loaded a far festival or banquet hall again");
				helper.assertTrue(on(Festivals.class, level).contains(festival), "the far festival left the list while nobody was there");
				helper.assertTrue(on(Banquets.class, level).contains(banquet), "the far banquet left the list while nobody was there");
			}),
			comeBack(helper, festival, banquet),
			ticking(helper, 1000, festival, banquet),
			() -> {
				player[0] = goer(helper, festival.offset(2, 0, 2));
				return true;
			},
			() -> {
				helper.assertTrue(Math.floorMod(level.getDayTime(), 24000L) < Festivals.END, "setup: dusk came");
				helper.assertTrue(++waited[0] < 300, "the festival didn't go on once someone was back: no festival standing in 300 ticks ("
					+ Reputation.points(level, festival, player[0].getUUID()) + ")");
				return Reputation.points(level, festival, player[0].getUUID()) >= Reputation.FESTIVAL;
			},
			hold(90, () -> {
				helper.assertTrue(Festivals.isOn(level, festival) && on(Festivals.class, level).contains(festival), "the festival is off before dusk once back");
				helper.assertTrue(Banquets.isOn(level, banquet) && on(Banquets.class, level).contains(banquet), "the banquet is off before dusk once back");
			}),
			() -> {
				helper.assertTrue(Reputation.points(level, festival, player[0].getUUID()) == Reputation.FESTIVAL,
					"the festival's standing was given more than once in a day: " + Reputation.points(level, festival, player[0].getUUID()));
				return true;
			});
	}

	/**
	 * B1224's decision: a festival and a banquet on when everyone leaves, and dusk comes while nobody is there: they stay in
	 * the list (nothing loads the halls), and once someone is back they are dropped, with no gathering standing, no
	 * fireworks, after their end.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1114e2FestivalBackAfterDusk"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1114e2FestivalBackAfterDusk")
	public void aFarFestivalOverWhileAwayIsDroppedOnceSomeoneIsBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos festival = far(helper, 8500, 8500);
		BlockPos banquet = far(helper, 8700, 8500);
		helper.assertTrue(!loaded(level, festival) && !loaded(level, banquet), "setup: the far chunks are already loaded");
		festivalAndBanquet(helper, festival, banquet);
		ServerPlayer[] player = {null};

		script(helper,
			() -> !loaded(level, festival) && !loaded(level, banquet),
			() -> {
				level.setDayTime(Math.floorDiv(level.getDayTime(), 24000L) * 24000L + Festivals.END + 200); // dusk came and went
				return true;
			},
			hold(100, () -> {
				helper.assertTrue(!loaded(level, festival) && !loaded(level, banquet), "something loaded a far festival or banquet hall after dusk");
				helper.assertTrue(on(Festivals.class, level).contains(festival) && on(Banquets.class, level).contains(banquet),
					"a far festival or banquet left the list while nobody was there");
			}),
			comeBack(helper, festival, banquet),
			ticking(helper, 1000, festival, banquet),
			() -> {
				player[0] = goer(helper, festival.offset(2, 0, 2));
				return true;
			},
			hold(120, () -> { }),
			() -> {
				helper.assertTrue(!on(Festivals.class, level).contains(festival), "the festival over while away is still in the list once someone is back");
				helper.assertTrue(!on(Banquets.class, level).contains(banquet), "the banquet over while away is still in the list once someone is back");
				helper.assertTrue(Reputation.points(level, festival, player[0].getUUID()) == 0,
					"a festival over before anyone came back still gave its standing: " + Reputation.points(level, festival, player[0].getUUID()));
				List<FireworkRocketEntity> rockets = level.getEntitiesOfClass(FireworkRocketEntity.class, new AABB(festival).inflate(64));
				helper.assertTrue(rockets.isEmpty(), "fireworks went up for a festival over before anyone came back: " + rockets.size());
				return true;
			});
	}

	// ---- B1225: sieges ---------------------------------------------------------------------------------------------

	private static void cleanSiege(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		ThreatData data = ThreatData.get(level);
		Leftovers.after(helper, () -> {
			data.forgetSiege(hall);
			data.end(hall);
			data.forgetPast(hall);
			data.forgetClock(hall);
			Sieges.forget();
			Ladders.forget();
			SandRamps.forget();
			BattleStations.forget();
		});
	}

	/**
	 * B1225's decision, "a far siege stands still and loads nothing", across a restart: a siege on a far village, with a
	 * gate, a ladder and a sand ramp of its own, is saved and read back (the directors' memory forgotten, as a restart
	 * does); for 120 ticks after, nothing loads its hall's, gate's, ladder's or sand's chunks, and the siege is as it was.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa1114e2SiegeStandsStill"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa1114e2SiegeStandsStill")
	public void aFarSiegeStandsStillAndLoadsNothingAfterARestart(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -8100, 8100);
		BlockPos gate = hall.offset(40, 0, 0);
		BlockPos ladder = hall.offset(-40, 0, 8);
		BlockPos sand = hall.offset(0, 0, 40);
		BlockPos foot = sand.north();
		List<BlockPos> spots = List.of(hall, gate, ladder, sand);
		for (BlockPos p : spots) {
			helper.assertTrue(!loaded(level, p), "setup: a far chunk is already loaded: " + p);
		}
		ThreatData data = ThreatData.get(level);
		cleanSiege(helper, hall);
		data.begin(new ThreatData.Under(hall, Threats.MONSTERS, 4, level.getGameTime()));
		ThreatData.Siege siege = new ThreatData.Siege(hall, level.getGameTime(), level.getDayTime() + 12000);
		siege.came = 4;
		siege.breach = gate;
		siege.gates.add(new ThreatData.Gate(gate, 100, 0, false, false));
		siege.ladders.add(ladder);
		siege.laddered = true;
		siege.sand.add(sand);
		siege.rampFeet.add(foot);
		siege.rampDirs.add(Direction.SOUTH.get3DDataValue());
		siege.ramped = true;
		data.beginSiege(siege);
		data.roundTrip(level.registryAccess()); // a restart: the saved siege comes back, the directors remember nothing
		Sieges.forget();
		Ladders.forget();
		SandRamps.forget();
		BattleStations.forget();
		helper.assertTrue(data.siege(hall).isPresent(), "setup: the siege didn't come back from the save");

		script(helper,
			hold(120, () -> {
				for (BlockPos p : spots) {
					helper.assertTrue(!loaded(level, p), "the far siege loaded a chunk after the restart: " + p);
				}
				ThreatData.Siege back = data.siege(hall).orElse(null);
				helper.assertTrue(back != null, "the far siege was dropped while nobody was there");
				helper.assertTrue(!back.over, "the far siege ended while nobody was there");
				helper.assertTrue(data.raid(hall).isPresent(), "the far raid ended while nobody was there");
				helper.assertTrue(back.gates.size() == 1 && back.gates.get(0).hp == 100 && back.gates.get(0).state == ThreatData.GateState.STANDING,
					"the far gate changed while nobody was there");
				helper.assertTrue(back.ladders.equals(List.of(ladder)) && back.sand.equals(List.of(sand)) && back.rampFeet.equals(List.of(foot)),
					"the far siege's ladders or sand were taken away or added to while nobody was there: " + back.ladders + " " + back.sand);
			}));
	}

	/** A far siege whose raid has nobody left, with one ladder standing at the hall, dawn at {@code dawn}. */
	private static BlockPos farSiege(GameTestHelper helper, BlockPos hall, long dawn) {
		ServerLevel level = helper.getLevel();
		ThreatData data = ThreatData.get(level);
		cleanSiege(helper, hall);
		removeHallAfter(helper, hall);
		BlockPos ladder = hall.offset(3, 1, 0);
		Leftovers.after(helper, () -> {
			if (loaded(level, ladder)) {
				level.setBlock(ladder, Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(ladder.south(), Blocks.AIR.defaultBlockState(), 3);
			}
		});
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		level.setBlock(ladder.south(), Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(ladder, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH), 3);
		helper.assertTrue(level.getBlockState(ladder).is(Blocks.LADDER), "setup: the ladder didn't stay");
		data.begin(new ThreatData.Under(hall, Threats.MONSTERS, 4, level.getGameTime()));
		ThreatData.Siege siege = new ThreatData.Siege(hall, level.getGameTime(), dawn);
		siege.came = 4;
		siege.ladders.add(ladder);
		siege.laddered = true;
		data.beginSiege(siege);
		return ladder;
	}

	/**
	 * B1225's decision: a far siege whose dawn passed while nobody was there stands still (raid on, ladder up), and once
	 * someone is back the hall's tick ends the raid (it goes into the village's past attacks) and the director lifts the
	 * siege at once: the ladder comes away.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1114e2SiegeLiftedAtDawn"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1114e2SiegeLiftedAtDawn")
	public void aFarSiegePastItsDawnIsLiftedOnceSomeoneIsBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -8300, 8100);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		ThreatData data = ThreatData.get(level);
		BlockPos ladder = farSiege(helper, hall, level.getDayTime() - 100);
		int[] waited = {0};

		script(helper,
			() -> !loaded(level, hall),
			hold(60, () -> {
				helper.assertTrue(!loaded(level, hall), "something loaded the far besieged hall's chunk");
				helper.assertTrue(data.siege(hall).isPresent() && !data.siege(hall).get().over && data.raid(hall).isPresent(),
					"the far siege moved on while nobody was there");
				helper.assertTrue(data.siege(hall).get().ladders.equals(List.of(ladder)), "the far ladder was taken off the siege while nobody was there");
			}),
			comeBack(helper, hall),
			ticking(helper, 1000, hall),
			() -> {
				helper.assertTrue(++waited[0] < 500, "once someone was back the siege past its dawn wasn't lifted in 500 ticks: raid "
					+ data.raid(hall).isPresent() + ", siege " + data.siege(hall).map(s -> "over=" + s.over).orElse("gone"));
				return data.siege(hall).isEmpty();
			},
			() -> {
				helper.assertTrue(data.raid(hall).isEmpty(), "the siege was lifted but its raid is still on");
				helper.assertTrue(data.past(hall).size() == 1, "the raid that ended once back isn't in the village's past attacks: " + data.past(hall));
				helper.assertTrue(!level.getBlockState(ladder).is(Blocks.LADDER), "the siege's ladder still stands after it was lifted");
				return true;
			});
	}

	/**
	 * B1225's decision, the other side: a far siege whose dawn hasn't come when someone is back has its raid ended by the
	 * hall's tick (nobody of it is left), but is not lifted before its dawn; when its dawn comes, it is (not stuck).
	 */
	//$ gametest_ticks_batch AREA '2600' '"qa1114e2SiegeWaitsForDawn"'
	@GameTest(template = AREA, timeoutTicks = 2600, batch = "qa1114e2SiegeWaitsForDawn")
	public void aFarSiegeBeforeItsDawnWaitsForItOnceSomeoneIsBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -8500, 8100);
		helper.assertTrue(!loaded(level, hall), "setup: the far chunk is already loaded");
		ThreatData data = ThreatData.get(level);
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		long dawn = time + 3000;
		BlockPos ladder = farSiege(helper, hall, dawn);
		int[] waited = {0};

		script(helper,
			() -> !loaded(level, hall),
			hold(60, () -> {
				helper.assertTrue(!loaded(level, hall), "something loaded the far besieged hall's chunk");
				helper.assertTrue(data.siege(hall).isPresent() && !data.siege(hall).get().over && data.raid(hall).isPresent(),
					"the far siege moved on while nobody was there");
			}),
			comeBack(helper, hall),
			ticking(helper, 1000, hall),
			() -> {
				helper.assertTrue(++waited[0] < 500, "once someone was back the raid with nobody left didn't end in 500 ticks");
				return data.raid(hall).isEmpty();
			},
			() -> {
				helper.assertTrue(++waited[0] < 560, "the siege isn't over within 60 ticks of its raid ending");
				return data.siege(hall).map(s -> s.over).orElse(true);
			},
			hold(60, () -> {
				helper.assertTrue(data.siege(hall).isPresent(), "the siege was lifted before its dawn");
				helper.assertTrue(data.siege(hall).get().over, "the siege isn't over though its raid ended");
				helper.assertTrue(!level.getBlockState(ladder).is(Blocks.LADDER), "the ladder still stands though the raid is over");
			}),
			() -> {
				level.setDayTime(dawn); // dawn comes
				waited[0] = 0;
				return true;
			},
			() -> {
				helper.assertTrue(++waited[0] < 100, "the siege wasn't lifted within 100 ticks of its dawn (stuck)");
				return data.siege(hall).isEmpty();
			});
	}
}
