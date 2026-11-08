package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-07): B966 and B972 across saves. The fix keeps a new saved gate state, {@code WALLED} (a solid
 * block put where a gate went), and turns a gate set back where one went into a standing gate with fresh hit points
 * (docs/wiki/sieges.md, "The way after the gate went"). The spec: a walled-up gateway "stays shut: rams don't break
 * walls", it is saved, old saves never have it and an unknown name loads as standing; a gate set back "is rammed again
 * (with fresh hit points)"; and only "a block nothing walks through" walls the way up, so a carpet in the gap doesn't.
 */
public class QaSiegeGatewaySaves1107eGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** The siege's record of the gate block at {@code pos}. */
	private static ThreatData.Gate gate(GameTestHelper helper, ThreatData.Siege siege, BlockPos pos) {
		return siege.gates.stream().filter(g -> g.pos.equals(pos)).findFirst()
			.orElseThrow(() -> new AssertionError("the siege has no gate block at " + pos + ": " + siege.gates.stream().map(g -> g.pos).toList()));
	}

	/** The siege's gates, for a failure message: where (in the build's coordinates), state and hit points. */
	private static String gates(SiegeGameTests.Yard yard, ThreatData.Siege siege) {
		return siege.gates.stream().map(g -> g.pos.subtract(yard.origin()).toShortString() + " " + g.state + " " + g.hp).toList().toString();
	}

	/**
	 * A save as it was written, with the gate block at {@code pos} of the siege round {@code hall} changed to have
	 * {@code state} and {@code hp}; read back into the level's data, as a restart loading that save would.
	 */
	private static void loadWith(ServerLevel level, BlockPos hall, BlockPos pos, String state, int hp) {
		ThreatData data = ThreatData.get(level);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		ListTag sieges = tag.getList("sieges", Tag.TAG_COMPOUND);
		boolean found = false;
		for (int i = 0; i < sieges.size(); i++) {
			CompoundTag siege = sieges.getCompound(i);
			if (siege.getLong("hall") != hall.asLong()) {
				continue;
			}
			ListTag gates = siege.getList("gates", Tag.TAG_COMPOUND);
			for (int j = 0; j < gates.size(); j++) {
				CompoundTag gate = gates.getCompound(j);
				if (gate.getLong("pos") == pos.asLong()) {
					gate.putString("state", state);
					gate.putInt("hp", hp);
					found = true;
				}
			}
		}
		if (!found) {
			throw new AssertionError("no saved gate block at " + pos + " in the siege of " + hall);
		}
		data.read(tag);
		Sieges.forget(); // the directors' memory goes in a restart too
	}

	/**
	 * The ram breaks the Palisade Gate's middle gate and cobblestone goes in the gap. Once the director has marked it
	 * walled up, the siege is saved and loaded: the gate block comes back WALLED with the same hit points, and the way
	 * counts shut, in the siege's record and in the world. For 15 seconds after (several ram blows' time) nothing strikes
	 * the cobblestone: it stands, stays WALLED, and the way stays shut.
	 */
	//$ gametest_ticks_batch AREA '1300' '"qa1107eWalledSave"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qa1107eWalledSave")
	public void aWalledUpGatewayStaysWalledAndShutAcrossASaveAndLoad(GameTestHelper helper) {
		SiegeGameTests.Yard yard = SiegeGameTests.yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		BlockPos middle = yard.at(3, 0, 1);
		helper.assertTrue(SiegeGameTests.isFenceGate(level, middle), "no gate at " + middle);
		Mob ram = SiegeGameTests.raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob pillager = SiegeGameTests.raider(helper, yard, Culture.Role.RANGED, 14.5, 25.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, pillager));
		long[] at = {-1, -1, 0}; // walled up, saved and loaded, the hit points then
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (at[0] < 0) {
				if (!SiegeGameTests.isFenceGate(level, middle)) {
					at[0] = tick;
					level.setBlockAndUpdate(middle, Blocks.COBBLESTONE.defaultBlockState()); // a player's hand
				} else if (tick > 500) {
					helper.fail("the ram never broke the middle gate: it has " + Sieges.hitPointsLeft(level, yard.hall(), middle));
				}
				return;
			}
			ThreatData.Siege siege = yard.siege(helper);
			ThreatData.Gate gate = gate(helper, siege, middle);
			if (at[1] < 0) {
				if (gate.state == ThreatData.GateState.WALLED) {
					at[2] = gate.hp;
					ThreatData.get(level).roundTrip(level.registryAccess());
					Sieges.forget();
					ThreatData.Siege after = yard.siege(helper);
					helper.assertTrue(after != siege, "the save and load gave the same siege object back");
					ThreatData.Gate loaded = gate(helper, after, middle);
					helper.assertTrue(loaded.state == ThreatData.GateState.WALLED, "the walled-up gate block came back from the save as " + loaded.state
						+ "; the siege's gates " + gates(yard, after));
					helper.assertTrue(loaded.hp == at[2], "the walled-up gate block's hit points after the load: " + loaded.hp + ", before " + at[2]);
					helper.assertTrue(!Sieges.open(after), "after the load the walled-up gateway counts open: the raiders are sent in through a wall; gates "
						+ gates(yard, after));
					helper.assertTrue(!Sieges.openNow(level, after), "after the load the walled-up gateway counts open to the ladder layers");
					at[1] = tick;
				} else if (tick - at[0] > 3 * Sieges.EVERY) {
					helper.fail((tick - at[0]) + " ticks after cobblestone went in the gap the gate block is still " + gate.state + "; gates " + gates(yard, siege));
				}
				return;
			}
			helper.assertTrue(level.getBlockState(middle).is(Blocks.COBBLESTONE), (tick - at[1]) + " ticks after the load the cobblestone in the gateway is "
				+ level.getBlockState(middle) + "; ram " + SiegeGameTests.state(helper, ram));
			helper.assertTrue(gate.state == ThreatData.GateState.WALLED, (tick - at[1]) + " ticks after the load the walled-up gate block is " + gate.state
				+ "; gates " + gates(yard, siege));
			helper.assertTrue(gate.hp == at[2], "the ram struck the wall after the load: its hit points went from " + at[2] + " to " + gate.hp);
			helper.assertTrue(!Sieges.open(siege), (tick - at[1]) + " ticks after the load the walled-up gateway counts open; gates " + gates(yard, siege));
			helper.assertTrue(!Sieges.openNow(level, siege), (tick - at[1]) + " ticks after the load the walled-up gateway counts open to the ladder layers");
			if (tick - at[1] > 300) {
				helper.succeed();
			}
		});
	}

	/**
	 * A siege's saved gate states, as old and new saves have them: {@code walled} in any case loads as WALLED, and
	 * {@code broken} as BROKEN; a name this version doesn't know, an empty one, a missing one and one of the wrong NBT
	 * type load as STANDING, without a crash, and every gate keeps its hit points and lane. A walled-up first way counts
	 * shut after the load, a broken one open.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qa1107eGateStates"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qa1107eGateStates")
	public void savedGateStatesLoadWhateverTheirNames(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ThreatData data = ThreatData.get(level);
		BlockPos hall = helper.absolutePos(new BlockPos(0, 40, 0));
		BlockPos other = hall.east(64);
		helper.assertTrue(data.siege(hall).isEmpty() && data.siege(other).isEmpty(), "a siege already laid where this test makes its own");
		String[] names = {"walled", "WALLED", "rubble", "", null, "int", "broken", "gone"};
		ThreatData.GateState[] expected = {ThreatData.GateState.WALLED, ThreatData.GateState.WALLED, ThreatData.GateState.STANDING,
			ThreatData.GateState.STANDING, ThreatData.GateState.STANDING, ThreatData.GateState.STANDING, ThreatData.GateState.BROKEN,
			ThreatData.GateState.GONE};
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		ListTag sieges = tag.getList("sieges", Tag.TAG_COMPOUND);
		// The first siege: its first way walled up, then every other name on the side ways.
		CompoundTag one = siege(hall);
		ListTag gates = new ListTag();
		for (int i = 0; i < names.length; i++) {
			CompoundTag b = new CompoundTag();
			b.putLong("pos", hall.south(3).east(i).asLong());
			b.putInt("hp", 10 + i);
			b.putInt("lane", i == 0 ? 0 : 1 + i % 2);
			b.putBoolean("high", false);
			b.putBoolean("dropped", false);
			if ("int".equals(names[i])) {
				b.putInt("state", 3);
			} else if (names[i] != null) {
				b.putString("state", names[i]);
			}
			gates.add(b);
		}
		one.put("gates", gates);
		sieges.add(one);
		// The second: its only first-way block broken, as every save before the fix has it.
		CompoundTag two = siege(other);
		ListTag broken = new ListTag();
		CompoundTag b = new CompoundTag();
		b.putLong("pos", other.south(3).asLong());
		b.putInt("hp", 0);
		b.putString("state", "broken");
		b.putInt("lane", 0);
		broken.add(b);
		two.put("gates", broken);
		sieges.add(two);
		tag.put("sieges", sieges);
		try {
			data.read(tag);
			ThreatData.Siege first = data.siege(hall).orElseThrow(() -> new AssertionError("the first siege didn't load"));
			helper.assertTrue(first.gates.size() == names.length, "the first siege loaded " + first.gates.size() + " gate blocks of " + names.length);
			for (int i = 0; i < names.length; i++) {
				ThreatData.Gate gate = first.gates.get(i);
				helper.assertTrue(gate.state == expected[i], "a gate block saved as " + (names[i] == null ? "(no state)" : "'" + names[i] + "'")
					+ " loaded as " + gate.state + ", not " + expected[i]);
				helper.assertTrue(gate.hp == 10 + i && gate.lane == (i == 0 ? 0 : 1 + i % 2), "a gate block saved as '" + names[i] + "' lost its hit points or lane: hp "
					+ gate.hp + ", lane " + gate.lane);
			}
			helper.assertTrue(!Sieges.open(first), "a siege whose first way was saved walled up counts it open after the load");
			ThreatData.Siege second = data.siege(other).orElseThrow(() -> new AssertionError("the second siege didn't load"));
			helper.assertTrue(second.gates.size() == 1 && second.gates.get(0).state == ThreatData.GateState.BROKEN,
				"an old save's broken gate block loaded as " + second.gates.stream().map(g -> g.state).toList());
			helper.assertTrue(Sieges.open(second), "an old save's siege with its first way broken counts it shut after the load");
			// Saved again, WALLED is written under a name the next load reads back as WALLED.
			data.roundTrip(level.registryAccess());
			ThreatData.Siege again = data.siege(hall).orElseThrow(() -> new AssertionError("the first siege didn't survive a second save and load"));
			helper.assertTrue(again.gates.get(0).state == ThreatData.GateState.WALLED, "WALLED came back from a second save and load as " + again.gates.get(0).state);
		} finally {
			data.forgetSiege(hall);
			data.forgetSiege(other);
		}
		helper.succeed();
	}

	/** A saved siege round {@code hall}, on, breached, its raiders outside to the south, as {@code ThreatData} writes one. */
	private static CompoundTag siege(BlockPos hall) {
		CompoundTag g = new CompoundTag();
		g.putLong("hall", hall.asLong());
		g.putLong("began", 0L);
		g.putLong("dawn", Long.MAX_VALUE / 4);
		g.putBoolean("over", false);
		g.putBoolean("breached", true);
		g.putLong("breach", hall.south(3).asLong());
		g.putInt("out", net.minecraft.core.Direction.SOUTH.get2DDataValue());
		return g;
	}

	/**
	 * An old save: the ram broke the middle gate and cobblestone went in the gap, but the save (as every one before the fix
	 * was) still says BROKEN. Loaded, the way counts shut in the world at once (the ladder layers' check), and once the
	 * director has looked again the gate block is WALLED and the way counts shut in the siege's record too; nothing strikes
	 * the cobblestone for 10 seconds after.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qa1107eOldSave"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qa1107eOldSave")
	public void anOldSaveWithTheGateBrokenUnderCobblestoneCountsShutAfterTheLoad(GameTestHelper helper) {
		SiegeGameTests.Yard yard = SiegeGameTests.yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		BlockPos middle = yard.at(3, 0, 1);
		helper.assertTrue(SiegeGameTests.isFenceGate(level, middle), "no gate at " + middle);
		Mob ram = SiegeGameTests.raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob pillager = SiegeGameTests.raider(helper, yard, Culture.Role.RANGED, 14.5, 25.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, pillager));
		long[] loaded = {-1, -1}; // when, when the director marked it walled
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (loaded[0] < 0) {
				if (!SiegeGameTests.isFenceGate(level, middle)) {
					level.setBlockAndUpdate(middle, Blocks.COBBLESTONE.defaultBlockState());
					loadWith(level, yard.hall(), middle, "broken", 0);
					ThreatData.Siege siege = yard.siege(helper);
					helper.assertTrue(gate(helper, siege, middle).state == ThreatData.GateState.BROKEN, "the old save's gate block loaded as "
						+ gate(helper, siege, middle).state);
					helper.assertTrue(!Sieges.openNow(level, siege), "right after loading an old save, a gateway walled up with cobblestone counts open to the ladder layers");
					loaded[0] = tick;
				} else if (tick > 500) {
					helper.fail("the ram never broke the middle gate: it has " + Sieges.hitPointsLeft(level, yard.hall(), middle));
				}
				return;
			}
			ThreatData.Siege siege = yard.siege(helper);
			ThreatData.Gate gate = gate(helper, siege, middle);
			helper.assertTrue(level.getBlockState(middle).is(Blocks.COBBLESTONE), (tick - loaded[0]) + " ticks after the load the cobblestone in the gateway is "
				+ level.getBlockState(middle) + "; ram " + SiegeGameTests.state(helper, ram));
			helper.assertTrue(gate.state != ThreatData.GateState.STANDING, "the cobblestone in the gateway counts as a standing gate: " + gates(yard, siege));
			if (loaded[1] < 0) {
				if (gate.state == ThreatData.GateState.WALLED) {
					loaded[1] = tick;
				} else if (tick - loaded[0] > 3 * Sieges.EVERY) {
					helper.fail((tick - loaded[0]) + " ticks after loading an old save the gate block under the cobblestone is still " + gate.state
						+ "; way " + (Sieges.open(siege) ? "open" : "shut") + "; gates " + gates(yard, siege));
				}
				return;
			}
			helper.assertTrue(gate.state == ThreatData.GateState.WALLED, "the walled-up gate block went back to " + gate.state + "; gates " + gates(yard, siege));
			helper.assertTrue(!Sieges.open(siege), "an old save's walled-up gateway counts open after the director looked: gates " + gates(yard, siege));
			helper.assertTrue(!Sieges.openNow(level, siege), "an old save's walled-up gateway counts open to the ladder layers");
			if (tick - loaded[1] > 200) {
				helper.succeed();
			}
		});
	}

	/**
	 * The ram breaks the middle gate; the same gate is set back, shut, and the world is saved and loaded before the
	 * director looks again (the save still says BROKEN, with no hit points). After the load the gate block stands again
	 * with fresh hit points (a whole fence gate's, less one blow at most), it is off the list of gates waiting for a
	 * builder, and the ram breaks it again (it is back on the list).
	 */
	//$ gametest_ticks_batch AREA '1400' '"qa1107eRegateSave"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa1107eRegateSave")
	public void aGateSetBackThenSavedBeforeTheDirectorLooksIsRammedAgainWithFreshHitPoints(GameTestHelper helper) {
		SiegeGameTests.Yard yard = SiegeGameTests.yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		BlockPos middle = yard.at(3, 0, 1);
		helper.assertTrue(SiegeGameTests.isFenceGate(level, middle), "no gate at " + middle);
		BlockState shut = level.getBlockState(middle);
		Mob ram = SiegeGameTests.raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram));
		long[] at = {-1, -1}; // set back and loaded, standing again
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (at[0] < 0) {
				if (!SiegeGameTests.isFenceGate(level, middle)) {
					helper.assertTrue(ThreatData.get(level).brokenGates(yard.hall()).contains(middle), "the broken gate isn't on the list waiting for a builder");
					level.setBlockAndUpdate(middle, shut); // a builder's repair, or a player's hand
					loadWith(level, yard.hall(), middle, "broken", 0);
					helper.assertTrue(gate(helper, yard.siege(helper), middle).state == ThreatData.GateState.BROKEN, "the saved gate block didn't load BROKEN");
					at[0] = tick;
				} else if (tick > 500) {
					helper.fail("the ram never broke the middle gate: it has " + Sieges.hitPointsLeft(level, yard.hall(), middle));
				}
				return;
			}
			ThreatData.Siege siege = yard.siege(helper);
			ThreatData.Gate gate = gate(helper, siege, middle);
			int fresh = Sieges.FENCE_GATE_HP * siege.hpFactor;
			if (at[1] < 0) {
				if (gate.state == ThreatData.GateState.STANDING) {
					helper.assertTrue(gate.hp >= fresh - Sieges.RAM_BLOW && gate.hp <= fresh, "the gate set back stands again with " + gate.hp
						+ " hit points, not a fresh gate's " + fresh);
					helper.assertTrue(!Sieges.open(siege), "the gate set back stands again but the way counts open: gates " + gates(yard, siege));
					helper.assertTrue(!ThreatData.get(level).brokenGates(yard.hall()).contains(middle) || !SiegeGameTests.isFenceGate(level, middle),
						"the gate set back is still on the list waiting for a builder");
					at[1] = tick;
				} else if (tick - at[0] > 3 * Sieges.EVERY) {
					helper.fail((tick - at[0]) + " ticks after the load the gate set back is still " + gate.state + "; gates " + gates(yard, siege));
				}
				return;
			}
			if (!SiegeGameTests.isFenceGate(level, middle)) {
				helper.assertTrue(ThreatData.get(level).brokenGates(yard.hall()).contains(middle), "the gate broken again isn't on the list waiting for a builder");
				helper.succeed();
			} else if (tick - at[1] > 700) {
				helper.fail("35 s after the gate set back stood again the ram hasn't broken it: it has " + gate.hp + " (" + gate.state + "); ram "
					+ SiegeGameTests.state(helper, ram) + "; gates " + gates(yard, siege));
			}
		});
	}

	/**
	 * A carpet in the gap where the ram broke the middle gate: something a raider walks over, not a wall (the wiki's walled
	 * up is "a block nothing walks through"). The gate block never counts as walled up or standing, the way counts open in
	 * the siege's record and in the world, and the pillager waiting behind gets through within 35 seconds.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qa1107eCarpet"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa1107eCarpet")
	public void aCarpetInTheGapLeavesTheWayOpen(GameTestHelper helper) {
		SiegeGameTests.Yard yard = SiegeGameTests.yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		BlockPos middle = yard.at(3, 0, 1);
		helper.assertTrue(SiegeGameTests.isFenceGate(level, middle), "no gate at " + middle);
		Mob ram = SiegeGameTests.raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob pillager = SiegeGameTests.raider(helper, yard, Culture.Role.RANGED, 14.5, 25.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, pillager));
		long[] at = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (at[0] < 0) {
				if (!SiegeGameTests.isFenceGate(level, middle)) {
					at[0] = tick;
					level.setBlockAndUpdate(middle, Blocks.RED_CARPET.defaultBlockState());
				} else if (tick > 500) {
					helper.fail("the ram never broke the middle gate: it has " + Sieges.hitPointsLeft(level, yard.hall(), middle));
				}
				return;
			}
			ThreatData.Siege siege = yard.siege(helper);
			ThreatData.Gate gate = gate(helper, siege, middle);
			helper.assertTrue(gate.state != ThreatData.GateState.WALLED && gate.state != ThreatData.GateState.STANDING,
				"a carpet in the gap counts as " + gate.state + "; gates " + gates(yard, siege));
			helper.assertTrue(Sieges.openNow(level, siege), "a carpet in the gap counts as shut to the ladder layers");
			if (tick - at[0] > Sieges.EVERY) {
				helper.assertTrue(Sieges.open(siege), "a carpet in the gap leaves the way counting shut: gates " + gates(yard, siege));
			}
			if (SiegeGameTests.through(helper, yard, pillager)) {
				helper.succeed();
			} else if (tick > at[0] + 700) {
				helper.fail("35 s after a carpet went in the gap the pillager is still outside: " + SiegeGameTests.state(helper, pillager)
					+ "; ram " + SiegeGameTests.state(helper, ram) + "; carpet " + level.getBlockState(middle).is(Blocks.RED_CARPET));
			}
		});
	}
}
