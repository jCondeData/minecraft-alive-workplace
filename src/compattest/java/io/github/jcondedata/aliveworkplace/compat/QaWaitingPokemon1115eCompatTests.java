package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.*;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.WaitingPokemon;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane E, 2026-11-15): the daycare's waiting list (B1295, B1296, B1287; ROADMAP 28.12 "if she dies, the pairs go to
 * their trainers' PCs", "no Pokémon lost"). From the spec, beyond DaycareWaitingCompatTests: every field of every
 * Pokémon and every trainer kept through the world file's own compressed save and load; a waiting pair handed over one
 * freed slot at a time, never duplicated; an offline trainer's pair waits for them and nobody else, with one reminder at
 * login; broken or old world data loads without error; never Cobblemon's PC overflow; the exact sentences.
 */
public class QaWaitingPokemon1115eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final Stats[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};

	static String waitingLine(String name) {
		return "Your PC is full: " + name + " is waiting for you and will come to your PC as soon as a place in it is free.";
	}

	static String cameLine(String name) {
		return name + " came to your PC from the daycare.";
	}

	static String stillWaitingLine(int n) {
		return n == 1 ? "Your PC is full: a Pokémon from the daycare is waiting for a free place in it." // B1309: one reads in the singular
			: "Your PC is full: Pokémon from the daycare are waiting for a free place in it (" + n + ").";
	}

	/** A player with this exact UUID joins (a trainer logging in), messages recorded. */
	static ServerPlayer login(GameTestHelper helper, UUID uuid, String name, List<String> seen) {
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

	static int timesInPc(PCStore pc, UUID id) {
		int n = 0;
		for (Pokemon p : pc) {
			if (p.getUuid().equals(id)) {
				n++;
			}
		}
		return n;
	}

	static int waiting(GameTestHelper helper, UUID owner, UUID id) {
		return WaitingPokemon.count(helper.getLevel().getServer(), owner, id);
	}

	static long count(List<String> seen, String line) {
		return seen.stream().filter(line::equals).count();
	}

	static boolean anyDaycareLine(List<String> seen) {
		return seen.stream().anyMatch(s -> s.contains("daycare") || s.contains("Your PC is full"));
	}

	/** Frees one PC slot that holds none of {@code keep}. */
	static void freeOneSlot(PCStore pc, UUID... keep) {
		for (Pokemon p : pc) {
			if (!List.of(keep).contains(p.getUuid())) {
				pc.remove(p);
				return;
			}
		}
	}

	static CompoundTag throughTheWorldFile(CompoundTag tag) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(tag, out);
			return NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray()), NbtAccounter.unlimitedHeap());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Two trainers, three Pokémon (a shiny, nicknamed Pikachu with set IVs, nature, gender, ability and a held item), saved
	 * and loaded through the world file's compressed NBT: each comes back whole, to its own trainer only.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWaitingPokemon1115eFields"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWaitingPokemon1115eFields")
	public void qaEveryFieldOfEveryTrainersPokemonSurvivesASaveAndLoad(GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		UUID ash = UUID.randomUUID();
		UUID misty = UUID.randomUUID();
		Pokemon pikachu = make("pikachu level=33 shiny=true gender=female nature=modest");
		pikachu.setNickname(Component.literal("Sparky"));
		int[] ivs = {1, 30, 7, 31, 0, 19};
		for (int i = 0; i < STATS.length; i++) {
			pikachu.getIvs().set(STATS[i], ivs[i]);
		}
		pikachu.swapHeldItem(new ItemStack(Items.GOLDEN_APPLE), false, false);
		String ability = pikachu.getAbility().getName();
		Pokemon eevee = make("eevee level=20 gender=male");
		Pokemon staryu = make("staryu level=12");
		WaitingPokemon before = new WaitingPokemon();
		before.add(ash, "Ash", pikachu.saveToNBT(access, new CompoundTag()));
		before.add(misty, "Misty", staryu.saveToNBT(access, new CompoundTag()));
		before.add(ash, "Ash", eevee.saveToNBT(access, new CompoundTag()));
		helper.assertTrue(before.isDirty(), "the list isn't marked to be saved after a Pokémon was added");
		WaitingPokemon loaded = WaitingPokemon.load(throughTheWorldFile(before.save(new CompoundTag(), access)), access);

		helper.assertValueEqual(loaded.size(), 3, "Pokémon waiting after the reload");
		List<Pokemon> ashs = loaded.waitingFor(ash, access);
		List<Pokemon> mistys = loaded.waitingFor(misty, access);
		helper.assertValueEqual(ashs.size(), 2, "Ash's Pokémon after the reload");
		helper.assertValueEqual(mistys.size(), 1, "Misty's Pokémon after the reload");
		helper.assertTrue(mistys.get(0).getUuid().equals(staryu.getUuid()), "Misty got someone else's Pokémon: " + mistys.get(0).getSpecies().getName());
		helper.assertTrue(ashs.stream().noneMatch(p -> p.getUuid().equals(staryu.getUuid())), "Ash got Misty's Staryu");
		Pokemon back = ashs.stream().filter(p -> p.getUuid().equals(pikachu.getUuid())).findFirst().orElse(null);
		helper.assertTrue(back != null, "the Pikachu is gone after the reload");
		helper.assertTrue(ashs.stream().anyMatch(p -> p.getUuid().equals(eevee.getUuid()) && p.getLevel() == 20), "the Eevee is gone or changed");
		helper.assertValueEqual(back.getSpecies().getName(), "Pikachu", "species");
		helper.assertValueEqual(back.getLevel(), 33, "level");
		helper.assertTrue(back.getShiny(), "no longer shiny");
		helper.assertValueEqual(back.getNickname() == null ? "" : back.getNickname().getString(), "Sparky", "nickname");
		helper.assertValueEqual(back.getGender(), pikachu.getGender(), "gender");
		helper.assertValueEqual(back.getNature().getName(), pikachu.getNature().getName(), "nature");
		helper.assertValueEqual(back.getAbility().getName(), ability, "ability");
		helper.assertTrue(back.heldItem().is(Items.GOLDEN_APPLE), "held item: " + back.heldItem());
		for (int i = 0; i < STATS.length; i++) {
			helper.assertValueEqual(back.getIvs().getOrDefault(STATS[i]), ivs[i], "IV " + STATS[i]);
		}
		helper.assertValueEqual(back.getExperience(), pikachu.getExperience(), "experience");
		// saved twice in a row (two autosaves): nothing doubled, nothing lost
		WaitingPokemon twice = WaitingPokemon.load(throughTheWorldFile(loaded.save(new CompoundTag(), access)), access);
		helper.assertValueEqual(twice.size(), 3, "Pokémon waiting after a second save and load");
		helper.succeed();
	}

	/** World data from before the fix, or damaged: loads without an error, with nobody (or only the readable trainers) waiting. */
	//$ gametest_ticks_batch AREA '100' '"qaWaitingPokemon1115eOldData"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWaitingPokemon1115eOldData")
	public void qaOldOrDamagedWorldDataLoadsWithoutAnError(GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		helper.assertValueEqual(WaitingPokemon.load(new CompoundTag(), access).size(), 0, "a world without the data");
		CompoundTag wrongType = new CompoundTag();
		wrongType.put("Trainers", StringTag.valueOf("nonsense"));
		helper.assertValueEqual(WaitingPokemon.load(wrongType, access).size(), 0, "Trainers of the wrong type");

		UUID kept = UUID.randomUUID();
		Pokemon pidgey = make("pidgey level=4");
		ListTag trainers = new ListTag();
		CompoundTag noOwner = new CompoundTag();
		noOwner.putString("Name", "nobody");
		ListTag one = new ListTag();
		one.add(make("rattata level=2").saveToNBT(access, new CompoundTag()));
		noOwner.put("Pokemon", one);
		trainers.add(noOwner);
		CompoundTag empty = new CompoundTag();
		empty.putUUID("Owner", UUID.randomUUID());
		empty.put("Pokemon", new ListTag());
		trainers.add(empty);
		CompoundTag good = new CompoundTag();
		good.putUUID("Owner", kept);
		good.putString("Name", "Brock");
		ListTag goods = new ListTag();
		goods.add(pidgey.saveToNBT(access, new CompoundTag()));
		good.put("Pokemon", goods);
		trainers.add(good);
		CompoundTag tag = new CompoundTag();
		tag.put("Trainers", trainers);
		WaitingPokemon loaded = WaitingPokemon.load(tag, access);
		helper.assertValueEqual(loaded.size(), 1, "Pokémon waiting from the damaged data (only Brock's Pidgey is readable)");
		helper.assertTrue(loaded.waitingFor(kept, access).stream().anyMatch(p -> p.getUuid().equals(pidgey.getUuid())), "Brock's Pidgey lost");
		helper.assertTrue(WaitingPokemon.get(helper.getLevel().getServer()) != null, "the world's list can't be opened");
		helper.succeed();
	}

	/**
	 * The keeper dies while the trainer (online) has a whole full PC: both of the pair wait, each named in its own line
	 * (a nickname as the player gave it). A freed slot takes exactly one, the other keeps waiting, neither is ever in
	 * two places; the next freed slot takes the other. Never Cobblemon's overflow.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaWaitingPokemon1115eOneSlot"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWaitingPokemon1115eOneSlot")
	public void qaAWaitingPairTakesOneFreedSlotAtATimeWithoutDuplicates(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		party(player).get(1).setNickname(Component.literal("Sparky"));
		UUID[] pair = leavePair(helper, keeper, player);
		UUID me = player.getUUID();
		PCStore pc = QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(player), helper.getLevel().registryAccess());
		seen.clear();
		keeper.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "she still holds them");
			helper.assertTrue(waiting(helper, me, pair[0]) == 1 && waiting(helper, me, pair[1]) == 1,
				"not both waiting once (mother " + waiting(helper, me, pair[0]) + ", father " + waiting(helper, me, pair[1]) + ")");
			helper.assertTrue(timesInPc(pc, pair[0]) == 0 && timesInPc(pc, pair[1]) == 0 && !inParty(player, pair[0]) && !inParty(player, pair[1]),
				"in the PC or party while waiting");
			helper.assertTrue(pc.getBackupStore().getPokemon().isEmpty(), "something went to Cobblemon's PC overflow");
			helper.assertValueEqual(count(seen, waitingLine("Sparky")), 1L, "lines naming the nicknamed mother: " + seen);
			helper.assertValueEqual(count(seen, waitingLine("Eevee")), 1L, "lines naming the father: " + seen);
			helper.assertTrue(seen.stream().noneMatch(s -> s.endsWith("came to your PC from the daycare.")), "told one came while the PC is full: " + seen);
			freeOneSlot(pc, pair);
			helper.runAfterDelay(10, () -> {
				int m = timesInPc(pc, pair[0]);
				int f = timesInPc(pc, pair[1]);
				helper.assertTrue(m + f == 1, "one freed slot, but in the PC: mother " + m + ", father " + f);
				helper.assertTrue(m + waiting(helper, me, pair[0]) == 1 && f + waiting(helper, me, pair[1]) == 1,
					"duplicated or lost (mother PC " + m + " waiting " + waiting(helper, me, pair[0]) + ", father PC " + f + " waiting " + waiting(helper, me, pair[1]) + ")");
				helper.assertTrue(pc.getFirstAvailablePosition() == null, "the PC isn't full again");
				String came = cameLine(m == 1 ? "Sparky" : "Eevee");
				helper.assertValueEqual(count(seen, came), 1L, "lines saying '" + came + "': " + seen);
				helper.assertValueEqual(seen.stream().filter(s -> s.endsWith("came to your PC from the daycare.")).count(), 1L, "came lines: " + seen);
				helper.assertTrue(count(seen, stillWaitingLine(1)) <= 1, "reminded every tick: " + seen);
				freeOneSlot(pc, pair);
				helper.runAfterDelay(10, () -> {
					helper.assertTrue(timesInPc(pc, pair[0]) == 1 && timesInPc(pc, pair[1]) == 1,
						"after the second freed slot: mother " + timesInPc(pc, pair[0]) + ", father " + timesInPc(pc, pair[1]));
					helper.assertTrue(waiting(helper, me, pair[0]) == 0 && waiting(helper, me, pair[1]) == 0, "still waiting after both came");
					helper.assertValueEqual(seen.stream().filter(s -> s.endsWith("came to your PC from the daycare.")).count(), 2L, "came lines: " + seen);
					helper.assertTrue(pc.getBackupStore().getPokemon().isEmpty(), "something went to Cobblemon's PC overflow");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * The keeper dies while the pair's trainer is offline with a full PC, and another player is online with room: the pair
	 * waits for its own trainer, nothing goes to the other player, who hears nothing. At login (PC still full) the trainer
	 * is reminded once how many wait; each freed slot then takes one.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaWaitingPokemon1115eOffline"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWaitingPokemon1115eOffline")
	public void qaAnOfflineTrainersPairWaitsForThemAndNobodyElse(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		var access = helper.getLevel().registryAccess();
		UUID owner = UUID.randomUUID();
		PCStore ownerPc = QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(owner, access), access);
		List<String> bystanderSeen = new ArrayList<>();
		ServerPlayer bystander = player(helper, bystanderSeen, 0, TWO_EEVEE_AND_ONE);
		PCStore bystanderPc = Cobblemon.INSTANCE.getStorage().getPC(bystander);
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		UUID[] pair = {mother.getUuid(), father.getUuid()};
		DaycareKeepers.setPairs(keeper, List.of(new DaycareKeepers.Pair(owner, "away", mother.saveToNBT(access, new CompoundTag()),
			father.saveToNBT(access, new CompoundTag()), 0, DaycareKeepers.day(helper.getLevel()))));
		bystanderSeen.clear();
		keeper.kill();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "she still holds them");
			helper.assertTrue(waiting(helper, owner, pair[0]) == 1 && waiting(helper, owner, pair[1]) == 1,
				"not both waiting for their offline trainer (mother " + waiting(helper, owner, pair[0]) + ", father " + waiting(helper, owner, pair[1]) + ")");
			helper.assertTrue(waiting(helper, bystander.getUUID(), pair[0]) == 0 && waiting(helper, bystander.getUUID(), pair[1]) == 0, "waiting for the wrong player");
			helper.assertTrue(timesInPc(bystanderPc, pair[0]) == 0 && timesInPc(bystanderPc, pair[1]) == 0 && !inParty(bystander, pair[0])
				&& !inParty(bystander, pair[1]), "given to another player");
			helper.assertTrue(!anyDaycareLine(bystanderSeen), "another player was told: " + bystanderSeen);
			helper.assertTrue(timesInPc(ownerPc, pair[0]) == 0 && ownerPc.getBackupStore().getPokemon().isEmpty(), "in the full PC or its overflow");
			List<String> seen = new ArrayList<>();
			ServerPlayer trainer = login(helper, owner, "away", seen);
			helper.runAfterDelay(10, () -> {
				PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(trainer);
				helper.assertValueEqual(count(seen, stillWaitingLine(2)), 1L, "reminders at login with the PC still full: " + seen);
				helper.assertTrue(timesInPc(pc, pair[0]) == 0 && timesInPc(pc, pair[1]) == 0, "came into a full PC");
				freeOneSlot(pc, pair);
				helper.runAfterDelay(10, () -> {
					int m = timesInPc(pc, pair[0]);
					int f = timesInPc(pc, pair[1]);
					helper.assertTrue(m + f == 1 && m + waiting(helper, owner, pair[0]) == 1 && f + waiting(helper, owner, pair[1]) == 1,
						"one freed slot after login: PC mother " + m + ", father " + f + "; waiting " + waiting(helper, owner, pair[0]) + ", " + waiting(helper, owner, pair[1]));
					helper.assertValueEqual(count(seen, cameLine("Eevee")), 1L, "came lines: " + seen);
					freeOneSlot(pc, pair);
					helper.runAfterDelay(10, () -> {
						helper.assertTrue(timesInPc(pc, pair[0]) == 1 && timesInPc(pc, pair[1]) == 1, "not both in the trainer's PC in the end");
						helper.assertTrue(waiting(helper, owner, pair[0]) == 0 && waiting(helper, owner, pair[1]) == 0, "still waiting after both came");
						helper.assertValueEqual(count(seen, cameLine("Eevee")), 2L, "came lines: " + seen);
						helper.assertValueEqual(count(seen, stillWaitingLine(2)) + count(seen, stillWaitingLine(1)), 1L, "reminders in all: " + seen);
						helper.assertTrue(timesInPc(bystanderPc, pair[0]) == 0 && !anyDaycareLine(bystanderSeen), "the other player got something: " + bystanderSeen);
						helper.assertTrue(pc.getBackupStore().getPokemon().isEmpty(), "something went to Cobblemon's PC overflow");
						helper.succeed();
					});
				});
			});
		});
	}
}
