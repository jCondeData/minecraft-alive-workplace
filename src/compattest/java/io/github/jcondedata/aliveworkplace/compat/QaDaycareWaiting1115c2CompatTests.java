package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.*;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.WaitingPokemon;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane C, 2026-11-15, after B1295/B1296/B1287's fix): the daycare's waiting list from the spec (CHANGELOG, wiki
 * "Daycare"): at a keeper's or rancher's death with the trainer's PC full the Pokémon wait on our side, saved with the
 * world, never Cobblemon's overflow; an online trainer is told where they are; while online each comes into the PC as
 * soon as a place is free, with a line; at login they come once there's room, with a reminder while some still wait.
 * Never lost, never duplicated. Several waiting with one slot freed, delivered once across a restart and a relog, the
 * Pokémon's own state through a restart, two trainers, an offline trainer, and the reminder not repeated.
 */
public class QaDaycareWaiting1115c2CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static String waitingLine(String name) {
		return "Your PC is full: " + name + " is waiting for you and will come to your PC as soon as a place in it is free.";
	}

	static String cameLine(String name) {
		return name + " came to your PC from the daycare.";
	}

	static String reminder(int n) {
		return "Your PC is full: Pokémon from the daycare are waiting for a free place in it (" + n + ").";
	}

	// ---- shared ----

	/** A mock trainer with a fixed id joining the server (a login), as TutorListMenuCompatTests.player does. */
	static ServerPlayer login(GameTestHelper helper, List<String> seen, UUID uuid, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(uuid, name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message.getString());
				super.displayClientMessage(message, overlay);
			}

			@Override
			public void sendSystemMessage(Component message) {
				seen.add(message.getString());
				super.sendSystemMessage(message);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		PartnerShowsCompatTests.after(helper, () -> {
			if (level.getServer().getPlayerList().getPlayer(uuid) == player) {
				level.getServer().getPlayerList().remove(player);
			}
		});
		return player;
	}

	static void logout(ServerPlayer player) {
		player.server.getPlayerList().remove(player);
	}

	static PCStore pc(GameTestHelper helper, UUID owner) {
		return Cobblemon.INSTANCE.getStorage().getPC(owner, helper.getLevel().registryAccess());
	}

	static int inPcCount(PCStore pc, UUID id) {
		int n = 0;
		for (Pokemon p : pc) {
			n += p.getUuid().equals(id) ? 1 : 0;
		}
		return n;
	}

	static int waiting(GameTestHelper helper, UUID owner) {
		return WaitingPokemon.get(helper.getLevel().getServer()).waitingFor(owner, helper.getLevel().registryAccess()).size();
	}

	/** Frees one box slot, never one of {@code keep}. */
	static void freeOne(PCStore pc, Collection<UUID> keep) {
		for (Pokemon p : pc) {
			if (!keep.contains(p.getUuid())) {
				pc.remove(p);
				return;
			}
		}
		throw new IllegalStateException("nothing to free");
	}

	/**
	 * A restart of the world's waiting list: the world's data saved to disk as an autosave or a stop does (only what is
	 * marked changed is written), then read back from that file into a fresh list.
	 */
	static void restartWaitingList(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		var access = helper.getLevel().registryAccess();
		server.overworld().getDataStorage().save();
		java.nio.file.Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data")
			.resolve(WaitingPokemon.NAME + ".dat");
		CompoundTag data = new CompoundTag();
		try {
			if (java.nio.file.Files.exists(file)) {
				data = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("data");
			}
		} catch (java.io.IOException e) {
			throw new IllegalStateException("could not read " + file, e);
		}
		server.overworld().getDataStorage().set(WaitingPokemon.NAME, WaitingPokemon.load(data, access));
	}

	static long count(List<String> seen, String line) {
		return seen.stream().filter(line::equals).count();
	}

	static long reminders(List<String> seen) {
		return seen.stream().filter(s -> s.startsWith("Your PC is full: Pokémon from the daycare")).count();
	}

	/** A rancher with a daycare beside the keeper's pasture (rancher(helper) clears the area, so not both from there). */
	static Villager secondRancher(GameTestHelper helper) {
		BlockPos trough = new BlockPos(6, 2, 1);
		helper.setBlock(trough, ModBlocks.FEED_TROUGH);
		Villager rancher = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		Jobs.employ(helper.getLevel(), rancher, helper.absolutePos(trough), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		rancher.setNoAi(true);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.ranch.Daycare.keepsDaycare(rancher), "setup: not a rancher with a daycare");
		return rancher;
	}

	// ---- tests ----

	/**
	 * Three wait (the keeper's pair and the rancher's boarder) and the party has room: none goes to the party; one freed
	 * slot brings exactly one, and nothing more comes while no other slot is free; the next freed slot brings the next.
	 * Each Pokémon exists exactly once all along.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaDaycareWaiting1115c2Three"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaDaycareWaiting1115c2Three")
	public void qaThreeWaitingEachFreedSlotBringsExactlyOne(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		Villager rancher = secondRancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20",
			"charmander level=5");
		UUID[] pair = leavePair(helper, keeper, player);
		Pokemon kid = party(player).get(1);
		helper.assertTrue(kid.getSpecies().getName().equals("Charmander"), "setup: not the Charmander");
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		List<UUID> ids = List.of(pair[0], pair[1], kid.getUuid());
		PCStore pc = QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(player), helper.getLevel().registryAccess());
		seen.clear();
		keeper.kill();
		rancher.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(waiting(helper, player.getUUID()), 3, "Pokémon waiting after both deaths");
			for (UUID id : ids) {
				helper.assertTrue(inPcCount(pc, id) == 0 && !inParty(player, id), "went to the PC or the (roomy) party with the PC full: " + id);
			}
			helper.assertValueEqual(count(seen, waitingLine("Eevee")), 2L, "told about each Eevee: " + seen);
			helper.assertValueEqual(count(seen, waitingLine("Charmander")), 1L, "told about the Charmander: " + seen);
			helper.assertTrue(pc.getBackupStore().getPokemon().isEmpty(), "something went to Cobblemon's overflow");
			helper.getLevel().getServer().overworld().getDataStorage().save(); // an autosave while all three wait
			freeOne(pc, ids);
			helper.runAfterDelay(10, () -> {
				long inPc = ids.stream().filter(id -> inPcCount(pc, id) == 1).count();
				helper.assertValueEqual(inPc, 1L, "Pokémon in the PC after one slot was freed");
				helper.assertValueEqual(waiting(helper, player.getUUID()), 2, "Pokémon still waiting after one slot was freed");
				helper.assertValueEqual(seen.stream().filter(s -> s.endsWith(" came to your PC from the daycare.")).count(), 1L, "arrival lines: " + seen);
				// a restart now: the one that came must not be back in the list (no copy), the two others still wait
				restartWaitingList(helper);
				helper.assertValueEqual(waiting(helper, player.getUUID()), 2, "Pokémon waiting after a restart following one arrival");
				for (UUID id : ids) {
					helper.assertValueEqual(inPcCount(pc, id) + WaitingPokemon.count(helper.getLevel().getServer(), player.getUUID(), id), 1,
						"copies after the restart of " + id);
				}
				freeOne(pc, ids);
				helper.runAfterDelay(10, () -> {
					helper.assertValueEqual(ids.stream().filter(id -> inPcCount(pc, id) == 1).count(), 2L, "Pokémon in the PC after a second slot");
					helper.assertValueEqual(waiting(helper, player.getUUID()), 1, "Pokémon still waiting after a second slot");
					for (UUID id : ids) {
						int copies = inPcCount(pc, id) + WaitingPokemon.count(helper.getLevel().getServer(), player.getUUID(), id) + (inParty(player, id) ? 1 : 0);
						helper.assertValueEqual(copies, 1, "copies of " + id);
					}
					helper.assertValueEqual(seen.stream().filter(s -> s.endsWith(" came to your PC from the daycare.")).count(), 2L, "arrival lines: " + seen);
					helper.succeed();
				});
			});
		});
	}

	/**
	 * After the boarder came into the PC, a restart of the world's data and a relog bring no second copy, no second
	 * arrival line and no reminder.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaDaycareWaiting1115c2Once"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDaycareWaiting1115c2Once")
	public void qaDeliveredOnceThroughARestartAndARelog(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		UUID uuid = UUID.randomUUID();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = login(helper, seen, uuid, "qaOnce");
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(make("bulbasaur level=10"));
		Pokemon kid = make("charmander level=5");
		party.add(kid);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		PCStore pc = QaDaycareRoom1115cBugCompatTests.fillWholePc(pc(helper, uuid), helper.getLevel().registryAccess());
		rancher.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(waiting(helper, uuid), 1, "setup: waiting");
			// more room than waiting: three slots for one Pokémon
			freeOne(pc, List.of(id));
			freeOne(pc, List.of(id));
			freeOne(pc, List.of(id));
			helper.runAfterDelay(5, () -> {
				helper.assertValueEqual(WaitingPokemon.get(helper.getLevel().getServer()).waitingFor(uuid, helper.getLevel().registryAccess()).size(), 0,
					"still waiting with room to spare");
				helper.assertTrue(pc.getFirstAvailablePosition() != null, "the two other freed slots were taken");
				helper.assertValueEqual(inPcCount(pc, id), 1, "copies in the PC after the freed slot");
				restartWaitingList(helper);
				logout(player);
				List<String> again = new ArrayList<>();
				login(helper, again, uuid, "qaOnce");
				helper.runAfterDelay(20, () -> {
					PCStore now = pc(helper, uuid);
					helper.assertValueEqual(inPcCount(now, id), 1, "copies in the PC after a restart and a relog");
					helper.assertValueEqual(waiting(helper, uuid), 0, "still waiting after it came");
					helper.assertValueEqual(again.stream().filter(s -> s.contains("daycare")).count(), 0L, "daycare lines after the relog: " + again);
					helper.succeed();
				});
			});
		});
	}

	/**
	 * The boarder's own state through a restart while it waits: nickname, held item, IVs, shiny, gender, species and
	 * trainer. The lines use its nickname; the relog with the PC still full reminds once (1) and it stays.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaDaycareWaiting1115c2State"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaDaycareWaiting1115c2State")
	public void qaAWaitingPokemonKeepsItsStateThroughARestart(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		UUID uuid = UUID.randomUUID();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = login(helper, seen, uuid, "qaState");
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(make("bulbasaur level=10"));
		Pokemon kid = make("charmander level=17 shiny=yes gender=female");
		kid.setNickname(Component.literal("Ember"));
		kid.swapHeldItem(new ItemStack(Items.GOLDEN_APPLE), false, false);
		Stat[] stats = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
		int[] ivs = {31, 0, 17, 5, 29, 12};
		for (int i = 0; i < stats.length; i++) {
			kid.getIvs().set(stats[i], ivs[i]);
		}
		party.add(kid);
		UUID id = kid.getUuid();
		Object trainer = kid.getOriginalTrainer();
		String ability = kid.getAbility().getName();
		String nature = kid.getNature().getName().toString();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		QaDaycareRoom1115cBugCompatTests.fillWholePc(pc(helper, uuid), helper.getLevel().registryAccess());
		seen.clear();
		rancher.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(count(seen, waitingLine("Ember")), 1L, "the waiting line by its nickname: " + seen);
			restartWaitingList(helper);
			logout(player);
			List<String> again = new ArrayList<>();
			login(helper, again, uuid, "qaState");
			helper.runAfterDelay(30, () -> {
				helper.assertValueEqual(count(again, reminder(1)), 1L, "the login reminder with the PC still full: " + again);
				helper.assertValueEqual(reminders(again), 1L, "reminders over 30 ticks: " + again);
				helper.assertValueEqual(waiting(helper, uuid), 1, "waiting after the relog to a full PC");
				PCStore pc = pc(helper, uuid);
				freeOne(pc, List.of(id));
				helper.runAfterDelay(5, () -> {
					helper.assertValueEqual(inPcCount(pc, id), 1, "in the PC once a slot was free");
					Pokemon back = null;
					for (Pokemon p : pc) {
						if (p.getUuid().equals(id)) {
							back = p;
						}
					}
					helper.assertTrue(back.getSpecies().getName().equals("Charmander") && back.getLevel() >= 17, "species/level: "
						+ back.getSpecies().getName() + " " + back.getLevel());
					helper.assertTrue(back.getNickname() != null && back.getNickname().getString().equals("Ember"), "nickname: " + back.getNickname());
					helper.assertTrue(back.heldItem().is(Items.GOLDEN_APPLE), "held item: " + back.heldItem());
					for (int i = 0; i < stats.length; i++) {
						helper.assertValueEqual(back.getIvs().getOrDefault(stats[i]), ivs[i], "IV " + stats[i]);
					}
					helper.assertTrue(back.getShiny(), "no longer shiny");
					helper.assertTrue(back.getGender().name().equals("FEMALE"), "gender: " + back.getGender());
					helper.assertTrue(Objects.equals(back.getOriginalTrainer(), trainer), "trainer: " + back.getOriginalTrainer() + " was " + trainer);
					helper.assertTrue(back.getAbility().getName().equals(ability) && back.getNature().getName().toString().equals(nature), "ability/nature");
					helper.assertValueEqual(count(again, cameLine("Ember")), 1L, "the arrival line by its nickname: " + again);
					helper.succeed();
				});
			});
		});
	}

	/** Two trainers' boarders wait: a slot freed in one's PC brings only their own; the other's still waits for theirs. */
	//$ gametest_ticks_batch AREA '300' '"qaDaycareWaiting1115c2Two"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaDaycareWaiting1115c2Two")
	public void qaTwoTrainersEachGetOnlyTheirOwn(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seenA = new ArrayList<>();
		List<String> seenB = new ArrayList<>();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		ServerPlayer playerA = login(helper, seenA, a, "qaTrainerA");
		ServerPlayer playerB = login(helper, seenB, b, "qaTrainerB");
		Cobblemon.INSTANCE.getStorage().getParty(playerA).add(make("bulbasaur level=10"));
		Cobblemon.INSTANCE.getStorage().getParty(playerB).add(make("bulbasaur level=10"));
		Pokemon kidA = make("charmander level=5");
		Pokemon kidB = make("squirtle level=5");
		Cobblemon.INSTANCE.getStorage().getParty(playerA).add(kidA);
		Cobblemon.INSTANCE.getStorage().getParty(playerB).add(kidB);
		helper.assertTrue(CobblemonDaycare.leave(playerA, rancher, kidA) && CobblemonDaycare.leave(playerB, rancher, kidB), "setup: not left");
		var access = helper.getLevel().registryAccess();
		PCStore pcA = QaDaycareRoom1115cBugCompatTests.fillWholePc(pc(helper, a), access);
		PCStore pcB = QaDaycareRoom1115cBugCompatTests.fillWholePc(pc(helper, b), access);
		seenA.clear();
		seenB.clear();
		rancher.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(WaitingPokemon.count(helper.getLevel().getServer(), a, kidA.getUuid()), 1, "A's boarder waiting for A");
			helper.assertValueEqual(WaitingPokemon.count(helper.getLevel().getServer(), b, kidB.getUuid()), 1, "B's boarder waiting for B");
			helper.assertTrue(seenA.contains(waitingLine("Charmander")) && !seenA.contains(waitingLine("Squirtle")), "A's lines: " + seenA);
			helper.assertTrue(seenB.contains(waitingLine("Squirtle")) && !seenB.contains(waitingLine("Charmander")), "B's lines: " + seenB);
			freeOne(pcB, List.of(kidA.getUuid(), kidB.getUuid()));
			helper.runAfterDelay(10, () -> {
				helper.assertValueEqual(inPcCount(pcB, kidB.getUuid()), 1, "B's boarder in B's PC");
				helper.assertValueEqual(inPcCount(pcB, kidA.getUuid()) + inPcCount(pcA, kidA.getUuid()), 0, "A's boarder came with no room in A's PC");
				helper.assertValueEqual(WaitingPokemon.count(helper.getLevel().getServer(), a, kidA.getUuid()), 1, "A's boarder still waiting");
				helper.assertTrue(seenB.contains(cameLine("Squirtle")) && !seenA.contains(cameLine("Squirtle")) && !seenA.contains(cameLine("Charmander")),
					"lines A " + seenA + ", B " + seenB);
				freeOne(pcA, List.of(kidA.getUuid(), kidB.getUuid()));
				helper.runAfterDelay(10, () -> {
					helper.assertValueEqual(inPcCount(pcA, kidA.getUuid()), 1, "A's boarder in A's PC");
					helper.assertValueEqual(inPcCount(pcA, kidB.getUuid()) + inPcCount(pcB, kidA.getUuid()), 0, "a boarder in the wrong PC");
					helper.assertValueEqual(waiting(helper, a) + waiting(helper, b), 0, "still waiting");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * The trainer is offline when the keeper dies with the PC full: both of the pair wait. A slot is freed while they're
	 * away (no loss, no copy); at login one comes, with its line, and the reminder counts the one left, shown once.
	 * The next freed slot brings the other.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaDaycareWaiting1115c2Offline"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaDaycareWaiting1115c2Offline")
	public void qaAnOfflineTrainersPairComesAtLoginAsRoomAllows(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		UUID uuid = UUID.randomUUID();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = login(helper, seen, uuid, "qaAway");
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String spec : new String[] {TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20"}) {
			party.add(make(spec));
		}
		UUID[] pair = leavePair(helper, keeper, player);
		List<UUID> ids = List.of(pair[0], pair[1]);
		QaDaycareRoom1115cBugCompatTests.fillWholePc(pc(helper, uuid), helper.getLevel().registryAccess());
		logout(player);
		seen.clear();
		keeper.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(waiting(helper, uuid), 2, "the pair waiting for an offline trainer");
			helper.assertTrue(seen.isEmpty(), "lines to a trainer who is away: " + seen);
			freeOne(pc(helper, uuid), ids);
			helper.runAfterDelay(10, () -> {
				PCStore away = pc(helper, uuid);
				for (UUID id : ids) {
					helper.assertValueEqual(inPcCount(away, id) + WaitingPokemon.count(helper.getLevel().getServer(), uuid, id), 1, "copies while away of " + id);
				}
				List<String> back = new ArrayList<>();
				login(helper, back, uuid, "qaAway");
				helper.runAfterDelay(30, () -> {
					PCStore pc = pc(helper, uuid);
					helper.assertValueEqual(ids.stream().filter(id -> inPcCount(pc, id) == 1).count(), 1L, "of the pair in the PC after the login");
					helper.assertValueEqual(waiting(helper, uuid), 1, "still waiting after the login");
					helper.assertValueEqual(count(back, cameLine("Eevee")), 1L, "arrival lines at login: " + back);
					helper.assertValueEqual(count(back, reminder(1)), 1L, "the reminder for the one left: " + back);
					helper.assertValueEqual(reminders(back), 1L, "reminders over 30 ticks: " + back);
					freeOne(pc, ids);
					helper.runAfterDelay(5, () -> {
						for (UUID id : ids) {
							helper.assertValueEqual(inPcCount(pc, id), 1, "in the PC after the second slot: " + id);
						}
						helper.assertValueEqual(waiting(helper, uuid), 0, "still waiting");
						helper.assertValueEqual(count(back, cameLine("Eevee")), 2L, "arrival lines: " + back);
						helper.assertValueEqual(reminders(back), 1L, "reminders: " + back);
						helper.succeed();
					});
				});
			});
		});
	}

	/**
	 * Online at the death with the PC full: told once about each of the pair and not reminded while staying online; the
	 * next login with the PC still full reminds once with (2); then two slots at once bring both.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaDaycareWaiting1115c2Remind"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaDaycareWaiting1115c2Remind")
	public void qaTheLoginReminderCountsTwoAndIsNotRepeated(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		UUID uuid = UUID.randomUUID();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = login(helper, seen, uuid, "qaRemind");
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String spec : new String[] {TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20"}) {
			party.add(make(spec));
		}
		UUID[] pair = leavePair(helper, keeper, player);
		List<UUID> ids = List.of(pair[0], pair[1]);
		QaDaycareRoom1115cBugCompatTests.fillWholePc(pc(helper, uuid), helper.getLevel().registryAccess());
		seen.clear();
		keeper.kill();
		helper.runAfterDelay(40, () -> {
			helper.assertValueEqual(count(seen, waitingLine("Eevee")), 2L, "waiting lines over 40 ticks: " + seen);
			helper.assertValueEqual(reminders(seen), 0L, "reminded while online right after being told: " + seen);
			logout(player);
			List<String> back = new ArrayList<>();
			login(helper, back, uuid, "qaRemind");
			helper.runAfterDelay(40, () -> {
				helper.assertValueEqual(count(back, reminder(2)), 1L, "the login reminder (2): " + back);
				helper.assertValueEqual(reminders(back), 1L, "reminders over 40 ticks: " + back);
				helper.assertValueEqual(waiting(helper, uuid), 2, "waiting with the PC full");
				PCStore pc = pc(helper, uuid);
				freeOne(pc, ids);
				freeOne(pc, ids);
				helper.runAfterDelay(5, () -> {
					for (UUID id : ids) {
						helper.assertValueEqual(inPcCount(pc, id), 1, "in the PC: " + id);
					}
					helper.assertValueEqual(waiting(helper, uuid), 0, "still waiting");
					helper.assertValueEqual(count(back, cameLine("Eevee")), 2L, "arrival lines: " + back);
					helper.succeed();
				});
			});
		});
	}
}
