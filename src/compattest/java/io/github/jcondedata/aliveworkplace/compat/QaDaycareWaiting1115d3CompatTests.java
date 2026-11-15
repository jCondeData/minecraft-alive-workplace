package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.*;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.WaitingPokemon;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane D, 2026-11-15, third pass): the Pokémon a daycare gives back at a keeper's or rancher's death while the
 * trainer's PC is full wait on the mod's side (B1287, B1295, B1296), from the spec (CHANGELOG, wiki "WaitingPokemon",
 * ROADMAP 28.12 "no Pokémon is ever lost or duplicated"): a pair that both wait comes one per freed place; another
 * trainer's room is never used; the trainer offline at the death is told nothing then and reminded with the count at
 * each login (once per login); a place freed while away is filled at the login; a party with room doesn't change where
 * it goes (the PC); the Pokémon keeps its data; the saved list keeps each trainer apart and skips broken entries.
 */
public class QaDaycareWaiting1115d3CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static final String WAITING_EEVEE = "Your PC is full: Eevee is waiting for you and will come to your PC as soon as a place in it is free.";
	static final String CAME_EEVEE = "Eevee came to your PC from the daycare.";
	static final String WAITING_CHARMANDER = DaycareWaitingCompatTests.WAITING_CHARMANDER;
	static final String CAME_CHARMANDER = DaycareWaitingCompatTests.CAME_CHARMANDER;

	static String reminder(int n) {
		return "Your PC is full: Pokémon from the daycare are waiting for a free place in it (" + n + ").";
	}

	/** A player logging in with {@code uuid} (a fresh login: Fabric's join event fires), whose chat lands in {@code seen}. */
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

	static void logout(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	/** How many copies of {@code id} the trainer has anywhere: party, PC, Cobblemon's overflow and our waiting list. */
	static int copies(GameTestHelper helper, UUID owner, UUID id) {
		var server = helper.getLevel().getServer();
		var access = helper.getLevel().registryAccess();
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(owner, access);
		int n = 0;
		for (Pokemon p : pc) {
			n += p.getUuid().equals(id) ? 1 : 0;
		}
		n += (int) pc.getBackupStore().getPokemon().stream().filter(p -> p.getUuid().equals(id)).count();
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(owner, access)) {
			n += p.getUuid().equals(id) ? 1 : 0;
		}
		return n + WaitingPokemon.count(server, owner, id);
	}

	static long lines(List<String> seen, String line) {
		return seen.stream().filter(line::equals).count();
	}

	static boolean anyDaycareLine(List<String> seen) {
		return seen.stream().anyMatch(s -> s.contains("daycare") || s.contains("Your PC is full"));
	}

	/**
	 * The keeper dies with the trainer's PC full and room in the party: both of the pair wait (not the party, not the
	 * overflow), one told line each. One place freed: exactly one comes, one line; the other still waits, and nobody is
	 * reminded mid-session. A second place: the other comes. Each is in the PC exactly once and the saved list is empty.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaDaycareWaiting1115d3PairOneByOne"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDaycareWaiting1115d3PairOneByOne")
	public void qaABothWaitingPairComesOnePerFreedPlace(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		PCStore pc = QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(player), helper.getLevel().registryAccess());
		helper.assertTrue(party(player).size() < 6, "setup: the party should have room");
		seen.clear();
		keeper.kill();
		var server = helper.getLevel().getServer();
		helper.runAfterDelay(5, () -> {
			for (UUID id : pair) {
				helper.assertValueEqual(WaitingPokemon.count(server, player.getUUID(), id), 1, "entries waiting for one of the pair");
				helper.assertTrue(!inParty(player, id) && !inPc(pc, id), "one of the pair went to the party or the PC: party " + inParty(player, id));
			}
			helper.assertTrue(pc.getBackupStore().getPokemon().isEmpty(), "something went to Cobblemon's overflow");
			helper.assertValueEqual(lines(seen, WAITING_EEVEE), 2L, "lines saying an Eevee waits: " + seen);
			helper.assertValueEqual(lines(seen, reminder(2)), 0L, "reminded in the same breath as told: " + seen);
			QaDaycareRoom1115d2CompatTests.freePc(pc, 1);
			helper.runAfterDelay(5, () -> {
				int in = (inPc(pc, pair[0]) ? 1 : 0) + (inPc(pc, pair[1]) ? 1 : 0);
				helper.assertValueEqual(in, 1, "of the pair in the PC after one freed place");
				UUID other = inPc(pc, pair[0]) ? pair[1] : pair[0];
				helper.assertValueEqual(WaitingPokemon.count(server, player.getUUID(), other), 1, "the other's entries waiting");
				helper.assertValueEqual(lines(seen, CAME_EEVEE), 1L, "lines saying an Eevee came: " + seen);
				helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("Your PC is full: Pokémon from the daycare")), "reminded mid-session: " + seen);
				QaDaycareRoom1115d2CompatTests.freePc(pc, 2);
				helper.runAfterDelay(5, () -> {
					for (UUID id : pair) {
						helper.assertValueEqual(copies(helper, player.getUUID(), id), 1, "copies of one of the pair (PC, party, overflow, waiting)");
						helper.assertTrue(inPc(pc, id), "one of the pair isn't in the PC");
					}
					helper.assertValueEqual(lines(seen, CAME_EEVEE), 2L, "lines saying an Eevee came: " + seen);
					helper.assertTrue(pc.getFirstAvailablePosition() != null, "the second freed place was used by something else");
					var access = helper.getLevel().registryAccess();
					WaitingPokemon reloaded = WaitingPokemon.load(WaitingPokemon.get(server).save(new CompoundTag(), access), access);
					helper.assertValueEqual(reloaded.waitingFor(player.getUUID(), access).size(), 0, "Pokémon still waiting in the world's saved list");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Trainer A's PC is full when the rancher dies, trainer B (online, plenty of room) never gets A's boarder and reads
	 * no line; A's boarder comes to A's PC once A has a place.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaDaycareWaiting1115d3TwoTrainers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDaycareWaiting1115d3TwoTrainers")
	public void qaAnotherTrainersRoomIsNeverUsed(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seenA = new ArrayList<>();
		List<String> seenB = new ArrayList<>();
		ServerPlayer a = player(helper, seenA, 0, TWO_EEVEE_AND_ONE, "charmander level=5");
		ServerPlayer b = player(helper, seenB, 0, TWO_EEVEE_AND_ONE);
		Pokemon kid = party(a).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(a, rancher, kid), "setup: not left");
		var access = helper.getLevel().registryAccess();
		PCStore pcA = QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(a), access);
		PCStore pcB = Cobblemon.INSTANCE.getStorage().getPC(b);
		helper.assertTrue(pcB.getFirstAvailablePosition() != null, "setup: B's PC should have room");
		seenA.clear();
		seenB.clear();
		rancher.kill();
		var server = helper.getLevel().getServer();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(!inPc(pcB, id) && !inParty(b, id), "A's boarder went to B");
			helper.assertValueEqual(WaitingPokemon.count(server, a.getUUID(), id), 1, "A's boarder's entries waiting for A");
			helper.assertValueEqual(WaitingPokemon.count(server, b.getUUID(), id), 0, "A's boarder's entries waiting for B");
			helper.assertTrue(!anyDaycareLine(seenB), "B read a daycare line: " + seenB);
			helper.assertValueEqual(lines(seenA, WAITING_CHARMANDER), 1L, "A's waiting lines: " + seenA);
			QaDaycareRoom1115d2CompatTests.freePc(pcA, 1);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(inPc(pcA, id), "not in A's PC after A freed a place");
				helper.assertTrue(!inPc(pcB, id), "in B's PC");
				helper.assertValueEqual(copies(helper, a.getUUID(), id), 1, "copies of A's boarder");
				helper.assertValueEqual(lines(seenA, CAME_CHARMANDER), 1L, "A's came lines: " + seenA);
				helper.assertTrue(!anyDaycareLine(seenB), "B read a daycare line: " + seenB);
				helper.succeed();
			});
		});
	}

	/**
	 * The keeper dies while her pair's trainer is offline with a full PC: nobody online reads a line (a bystander with a
	 * full PC and nothing waiting reads nothing, then or at login). At the trainer's login: one reminder with the count
	 * (2), not repeated tick after tick; at the next login, reminded again. Places freed: each comes with its line, and
	 * no reminder follows once nothing waits.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaDaycareWaiting1115d3OfflineLogin"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaDaycareWaiting1115d3OfflineLogin")
	public void qaAnOfflineTrainerIsRemindedOncePerLoginWithTheCount(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		var access = helper.getLevel().registryAccess();
		var server = helper.getLevel().getServer();
		UUID owner = UUID.randomUUID();
		QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(owner, access), access);
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		DaycareKeepers.setPairs(keeper, List.of(new DaycareKeepers.Pair(owner, "awayTrainer", mother.saveToNBT(access, new CompoundTag()),
			father.saveToNBT(access, new CompoundTag()), 0, DaycareKeepers.day(helper.getLevel()))));
		List<String> bystander = new ArrayList<>();
		ServerPlayer other = player(helper, bystander, 0, TWO_EEVEE_AND_ONE);
		QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(other), access);
		bystander.clear();
		keeper.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(WaitingPokemon.count(server, owner, mother.getUuid()), 1, "the mother's entries waiting");
			helper.assertValueEqual(WaitingPokemon.count(server, owner, father.getUuid()), 1, "the father's entries waiting");
			helper.assertTrue(!anyDaycareLine(bystander), "a bystander read a daycare line: " + bystander);
			List<String> seen = new ArrayList<>();
			ServerPlayer first = login(helper, seen, owner, "awayTrainer");
			helper.runAfterDelay(10, () -> {
				helper.assertValueEqual(lines(seen, reminder(2)), 1L, "reminders at the first login: " + seen);
				helper.assertValueEqual(lines(seen, WAITING_EEVEE), 0L, "told again as if it had just happened: " + seen);
				helper.assertValueEqual(lines(seen, CAME_EEVEE), 0L, "told one came with a full PC: " + seen);
				logout(helper, first);
				List<String> seen2 = new ArrayList<>();
				ServerPlayer second = login(helper, seen2, owner, "awayTrainer");
				helper.runAfterDelay(10, () -> {
					helper.assertValueEqual(lines(seen2, reminder(2)), 1L, "reminders at the second login: " + seen2);
					PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(second);
					QaDaycareRoom1115d2CompatTests.freePc(pc, 2);
					helper.runAfterDelay(5, () -> {
						helper.assertTrue(inPc(pc, mother.getUuid()) && inPc(pc, father.getUuid()), "not both in the PC once two places were free");
						helper.assertValueEqual(copies(helper, owner, mother.getUuid()), 1, "copies of the mother");
						helper.assertValueEqual(copies(helper, owner, father.getUuid()), 1, "copies of the father");
						helper.assertValueEqual(lines(seen2, CAME_EEVEE), 2L, "came lines: " + seen2);
						helper.assertValueEqual(lines(seen2, reminder(2)) + lines(seen2, reminder(1)) + lines(seen2, reminder(0)), 1L,
							"reminders after they came: " + seen2);
						helper.assertTrue(!anyDaycareLine(bystander), "a bystander read a daycare line: " + bystander);
						helper.succeed();
					});
				});
			});
		});
	}

	/**
	 * The rancher dies with the online trainer's PC full; the trainer logs out, a place is freed while away. At the next
	 * login the boarder comes into the PC with its line, once, and no "still waiting" reminder follows (nothing waits).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaDaycareWaiting1115d3FreedWhileAway"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDaycareWaiting1115d3FreedWhileAway")
	public void qaAPlaceFreedWhileAwayIsFilledAtLogin(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		var access = helper.getLevel().registryAccess();
		var server = helper.getLevel().getServer();
		UUID owner = UUID.randomUUID();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = login(helper, seen, owner, "awayRancher");
		Cobblemon.INSTANCE.getStorage().getParty(player).add(make("bulbasaur level=10"));
		Pokemon kid = make("charmander level=5");
		Cobblemon.INSTANCE.getStorage().getParty(player).add(kid);
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(player), access);
		seen.clear();
		rancher.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(lines(seen, WAITING_CHARMANDER), 1L, "waiting lines: " + seen);
			helper.assertValueEqual(WaitingPokemon.count(server, owner, kid.getUuid()), 1, "entries waiting");
			logout(helper, player);
			QaDaycareRoom1115d2CompatTests.freePc(Cobblemon.INSTANCE.getStorage().getPC(owner, access), 1);
			List<String> seen2 = new ArrayList<>();
			helper.runAfterDelay(3, () -> {
				ServerPlayer back = login(helper, seen2, owner, "awayRancher");
				helper.runAfterDelay(10, () -> {
					helper.assertTrue(inPc(Cobblemon.INSTANCE.getStorage().getPC(back), kid.getUuid()), "not in the PC after the login");
					helper.assertValueEqual(copies(helper, owner, kid.getUuid()), 1, "copies of the boarder");
					helper.assertValueEqual(lines(seen2, CAME_CHARMANDER), 1L, "came lines at the login: " + seen2);
					helper.assertTrue(seen2.stream().noneMatch(s -> s.startsWith("Your PC is full")), "told the PC is full after it came: " + seen2);
					helper.succeed();
				});
			});
		});
	}

	/**
	 * A boarder with a nickname, a held item, a shiny coat, a nature, a gender and a set IV waits and then comes: the
	 * same Pokémon, every one of those kept.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaDaycareWaiting1115d3KeepsData"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDaycareWaiting1115d3KeepsData")
	public void qaAWaitingPokemonKeepsItsData(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "charmander level=23 shiny=yes gender=female nature=modest");
		Pokemon kid = party(player).get(1);
		kid.setNickname(Component.literal("Ember"));
		kid.swapHeldItem(new ItemStack(Items.GOLDEN_APPLE), false, false);
		kid.getIvs().set(Stats.HP, 7);
		kid.getIvs().set(Stats.SPEED, 29);
		UUID id = kid.getUuid();
		String ability = kid.getAbility().getName();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		PCStore pc = QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(player), helper.getLevel().registryAccess());
		seen.clear();
		rancher.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(WaitingPokemon.count(helper.getLevel().getServer(), player.getUUID(), id), 1, "entries waiting");
			helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("Your PC is full: ") && s.contains("is waiting for you")), "not told it waits: " + seen);
			QaDaycareRoom1115d2CompatTests.freePc(pc, 1);
			helper.runAfterDelay(5, () -> {
				Pokemon back = null;
				for (Pokemon p : pc) {
					if (p.getUuid().equals(id)) {
						back = p;
					}
				}
				helper.assertTrue(back != null, "not in the PC once a place was free");
				Pokemon got = back;
				helper.assertValueEqual(got.getSpecies().getName(), "Charmander", "species");
				helper.assertTrue(got.getLevel() >= 23, "level dropped: " + got.getLevel());
				helper.assertTrue(got.getShiny(), "lost its shiny coat");
				helper.assertValueEqual(got.getGender().name(), "FEMALE", "gender");
				helper.assertValueEqual(got.getNature().getName().getPath(), "modest", "nature");
				helper.assertValueEqual(got.getNickname() == null ? null : got.getNickname().getString(), "Ember", "nickname");
				helper.assertTrue(got.heldItem().is(Items.GOLDEN_APPLE), "held item: " + got.heldItem());
				helper.assertValueEqual(got.getIvs().getOrDefault(Stats.HP), 7, "HP IV");
				helper.assertValueEqual(got.getIvs().getOrDefault(Stats.SPEED), 29, "Speed IV");
				helper.assertValueEqual(got.getAbility().getName(), ability, "ability");
				helper.assertValueEqual(copies(helper, player.getUUID(), id), 1, "copies");
				helper.succeed();
			});
		});
	}

	/**
	 * The saved list with two trainers (2 and 1) loads back with each trainer's own Pokémon, in order; saved and loaded
	 * twice it doesn't grow; entries without an owner or with no Pokémon are skipped without an error; an old world's
	 * empty data loads with nobody waiting.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareWaiting1115d3SavedList"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareWaiting1115d3SavedList")
	public void qaTheSavedListKeepsTrainersApartAndSkipsBrokenEntries(GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		Pokemon a1 = make("pidgey level=4");
		Pokemon a2 = make("rattata level=6");
		Pokemon b1 = make("squirtle level=9");
		WaitingPokemon data = new WaitingPokemon();
		data.add(a, "alpha", a1.saveToNBT(access, new CompoundTag()));
		data.add(b, "beta", b1.saveToNBT(access, new CompoundTag()));
		data.add(a, "alpha", a2.saveToNBT(access, new CompoundTag()));
		CompoundTag saved = data.save(new CompoundTag(), access);
		WaitingPokemon once = WaitingPokemon.load(saved, access);
		WaitingPokemon twice = WaitingPokemon.load(once.save(new CompoundTag(), access), access);
		for (WaitingPokemon w : List.of(once, twice)) {
			helper.assertValueEqual(w.size(), 3, "Pokémon waiting after a save and load");
			List<UUID> forA = w.waitingFor(a, access).stream().map(Pokemon::getUuid).toList();
			List<UUID> forB = w.waitingFor(b, access).stream().map(Pokemon::getUuid).toList();
			helper.assertValueEqual(forA, List.of(a1.getUuid(), a2.getUuid()), "alpha's Pokémon");
			helper.assertValueEqual(forB, List.of(b1.getUuid()), "beta's Pokémon");
		}
		// Broken entries next to a good one.
		ListTag trainers = saved.getList("Trainers", net.minecraft.nbt.Tag.TAG_COMPOUND).copy();
		CompoundTag noOwner = new CompoundTag();
		noOwner.putString("Name", "ghost");
		ListTag ghostList = new ListTag();
		ghostList.add(make("zubat level=3").saveToNBT(access, new CompoundTag()));
		noOwner.put("Pokemon", ghostList);
		CompoundTag noPokemon = new CompoundTag();
		noPokemon.putUUID("Owner", UUID.randomUUID());
		noPokemon.putString("Name", "empty");
		noPokemon.put("Pokemon", new ListTag());
		trainers.add(0, noOwner);
		trainers.add(noPokemon);
		CompoundTag broken = new CompoundTag();
		broken.put("Trainers", trainers);
		WaitingPokemon fromBroken = WaitingPokemon.load(broken, access);
		helper.assertValueEqual(fromBroken.size(), 3, "Pokémon waiting from a list with broken entries");
		helper.assertValueEqual(fromBroken.waitingFor(a, access).size(), 2, "alpha's Pokémon next to broken entries");
		helper.assertValueEqual(WaitingPokemon.load(new CompoundTag(), access).size(), 0, "Pokémon waiting in an old world");
		helper.assertValueEqual(new WaitingPokemon().waitingFor(UUID.randomUUID(), access).size(), 0, "Pokémon waiting for nobody");
		helper.succeed();
	}
}
