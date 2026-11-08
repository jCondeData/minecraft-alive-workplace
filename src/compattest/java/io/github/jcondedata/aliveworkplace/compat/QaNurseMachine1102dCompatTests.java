package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * QA for the Nurse at a Healing Machine (README "Nurses", ROADMAP 28.7), written from the spec. A real logged-in player
 * right-clicks the nurse through the server's packet handler, and every line she says is recorded. Covers fainted and
 * hurt Pokémon healed free in the machine's own time, clicks while the machine is busy (the same player and another),
 * the machine broken or the nurse killed mid-heal (no Pokémon lost, the player can heal again), a healthy team, the
 * once-a-minute cooldown (per player, shorter for a Master), battles, and the {@code nurseHealingMachine} switch.
 */
public class QaNurseMachine1102dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	static final BlockPos MACHINE = new BlockPos(2, 2, 2);
	static final BlockPos NURSE_AT = new BlockPos(3, 2, 3);
	private static final BlockPos MACHINE_2 = new BlockPos(6, 2, 2);
	private static final BlockPos NURSE_2_AT = new BlockPos(6, 2, 4);

	record Visitor(ServerPlayer player, PlayerPartyStore party, List<String> seen) {
	}

	private static Block machineBlock() {
		return BuiltInRegistries.BLOCK.get(ModVillagers.HEALING_MACHINE_BLOCK);
	}

	static void clean(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		helper.setDayTime(2000);
		Nurses.resetCooldowns();
	}

	/** A nurse employed at a Healing Machine at {@code machine}, standing still at {@code at}. */
	static Villager nurse(GameTestHelper helper, BlockPos machine, BlockPos at, int level) {
		helper.setBlock(machine, machineBlock());
		Villager nurse = helper.spawn(EntityType.VILLAGER, at);
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(machine), ModVillagers.HEALING_MACHINE_POI, ModVillagers.NURSE);
		nurse.setVillagerData(nurse.getVillagerData().setLevel(level));
		nurse.setNoAi(true);
		helper.assertTrue(Nurses.isNurse(nurse), "setup: not a nurse");
		helper.assertTrue(helper.absolutePos(machine).equals(Nurses.machine(helper.getLevel(), nurse)), "setup: she doesn't work at the machine");
		return nurse;
	}

	/** A logged-in survival player whose chat and action-bar lines land in {@code seen}; logged out when the test ends. */
	static Visitor visitor(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		UUID uuid = UUID.randomUUID();
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
		return new Visitor(player, Cobblemon.INSTANCE.getStorage().getParty(player), seen);
	}

	/** Adds a level 20 {@code species} at {@code health} (0: fainted, -1: full). */
	static Pokemon pokemon(Visitor v, String species, int health) {
		Pokemon pokemon = PokemonProperties.Companion.parse(species + " level=20", " ", "=").create();
		v.party().add(pokemon);
		if (health >= 0) {
			pokemon.setCurrentHealth(health);
		}
		return pokemon;
	}

	/** A client's right-click with an empty hand on {@code villager}, through the server's packet handler. */
	static void rightClick(Visitor v, Villager villager) {
		ServerPlayer player = v.player();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		int before = v.seen().size();
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (v.seen().size() == before && player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND));
		}
		// 43.3: an empty-hand click opens the villager's card; its first button does what the click did, pressed as the client sends it
		if (player.containerMenu instanceof io.github.jcondedata.aliveworkplace.card.StatusCardMenu card) {
			player.connection.handleContainerButtonClick(new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(
				card.containerId, card.card().buttons().get(0).id()));
		}
	}

	static boolean said(Visitor v, String part) {
		return v.seen().stream().anyMatch(line -> line.contains(part));
	}

	private static String hp(Pokemon pokemon) {
		return pokemon.getSpecies().getName() + " " + pokemon.getCurrentHealth() + "/" + pokemon.getMaxHealth()
			+ (pokemon.isFainted() ? " (fainted)" : "");
	}

	private static void assertAllFull(GameTestHelper helper, List<Pokemon> team) {
		for (Pokemon pokemon : team) {
			helper.assertTrue(pokemon.isFullHealth() && !pokemon.isFainted(), "not healed: " + hp(pokemon));
		}
	}

	private static void assertParty(GameTestHelper helper, Visitor v, List<Pokemon> team) {
		List<UUID> now = new ArrayList<>();
		for (Pokemon pokemon : v.party()) {
			now.add(pokemon.getUuid());
		}
		helper.assertValueEqual(now.size(), team.size(), "Pokémon in the party (" + now + ")");
		for (Pokemon pokemon : team) {
			helper.assertTrue(now.contains(pokemon.getUuid()), pokemon.getSpecies().getName() + " left the party");
		}
	}

	/** No Poké Ball (or Pokémon item) lies on the ground near the test. */
	private static void assertNothingDropped(GameTestHelper helper) {
		List<ItemEntity> balls = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8),
			e -> BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath().contains("ball"));
		helper.assertTrue(balls.isEmpty(), "Poké Balls on the ground: " + balls.stream().map(e -> e.getItem().toString()).toList());
	}

	/**
	 * Spec: a fainted and a hurt Pokémon (and a healthy one) go in the machine on a right-click; it shows in use, nothing
	 * is healed at once (its own heal time), and afterwards all three are full and none fainted. Free: no money, no
	 * emeralds. She says so.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaNurseMachineFainted"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaNurseMachineFainted")
	public void qaFaintedAndHurtPokemonHealFreeInTheMachine(GameTestHelper helper) {
		clean(helper);
		Villager nurse = nurse(helper, MACHINE, NURSE_AT, 1);
		Visitor v = visitor(helper, "qaNurseA");
		Pokemon fainted = pokemon(v, "pikachu", 0);
		Pokemon hurt = pokemon(v, "bulbasaur", 3);
		Pokemon well = pokemon(v, "eevee", -1);
		List<Pokemon> team = List.of(fainted, hurt, well);
		helper.assertTrue(fainted.isFainted(), "setup: pikachu isn't fainted");
		CobbleDollarsBank.add(v.player(), 500 - CobbleDollarsBank.balance(v.player()));
		v.player().getInventory().setItem(20, new ItemStack(Items.EMERALD, 10));
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		machine.setHealingCharge(0);
		v.player().setHealth(6);
		v.player().addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 2400));
		v.player().addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 2400));

		rightClick(v, nurse);
		helper.assertTrue(v.player().getHealth() == v.player().getMaxHealth(), "the player's health wasn't restored: " + v.player().getHealth());
		helper.assertFalse(v.player().hasEffect(net.minecraft.world.effect.MobEffects.POISON), "poison not cleared");
		helper.assertTrue(v.player().hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED), "a good effect (Speed) was taken away");
		helper.assertTrue(machine.isInUse(), "the machine isn't running; she said " + v.seen());
		helper.assertTrue(v.player().getUUID().equals(machine.getCurrentUser()), "the machine runs for someone else");
		helper.assertTrue(said(v, "put your 2 Pokémon in the Healing Machine"), "her line: " + v.seen());
		helper.assertTrue(fainted.isFainted(), "healed at once: the machine's heal time was skipped (" + hp(fainted) + ")");
		helper.assertValueEqual(CobbleDollarsBank.balance(v.player()), 500L, "CobbleDollars after the heal");
		helper.assertValueEqual(v.player().getInventory().countItem(Items.EMERALD), 10, "emeralds after the heal");
		helper.succeedWhen(() -> {
			helper.assertFalse(machine.isInUse(), "the machine is still running");
			assertAllFull(helper, team);
			assertParty(helper, v, team);
			helper.assertValueEqual(CobbleDollarsBank.balance(v.player()), 500L, "CobbleDollars after the machine");
			assertNothingDropped(helper);
		});
	}

	/**
	 * Spec: clicking again while the machine heals my own team doesn't start it again or reset its time, and tells me to
	 * wait; another player's click while it's busy says it's busy and leaves their team alone, and once it's free their
	 * team goes in and is healed (the busy click didn't spend their minute).
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaNurseMachineBusy"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaNurseMachineBusy")
	public void qaClicksWhileTheMachineIsBusy(GameTestHelper helper) {
		clean(helper);
		Villager nurse = nurse(helper, MACHINE, NURSE_AT, 1);
		Visitor a = visitor(helper, "qaNurseA");
		Visitor b = visitor(helper, "qaNurseB");
		List<Pokemon> teamA = List.of(pokemon(a, "pikachu", 1), pokemon(a, "squirtle", 0));
		List<Pokemon> teamB = List.of(pokemon(b, "charmander", 2));
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);

		rightClick(a, nurse);
		helper.assertTrue(machine.isInUse(), "A's team didn't go in: " + a.seen());
		int left = machine.getHealTimeLeft();
		a.seen().clear();
		rightClick(a, nurse);
		helper.assertTrue(a.player().getUUID().equals(machine.getCurrentUser()), "the machine changed hands on A's second click");
		helper.assertTrue(machine.getHealTimeLeft() <= left, "A's second click restarted the machine: " + left + " -> " + machine.getHealTimeLeft());
		helper.assertFalse(a.seen().isEmpty(), "A's second click got no answer");
		helper.assertFalse(said(a, "put your"), "A's team was put in again: " + a.seen());

		rightClick(b, nurse);
		helper.assertTrue(said(b, "busy"), "B isn't told the machine is busy: " + b.seen());
		helper.assertTrue(a.player().getUUID().equals(machine.getCurrentUser()), "B's click took the machine from A");
		helper.assertFalse(teamB.get(0).isFullHealth(), "B's team was healed while the machine was busy");
		helper.assertFalse(teamA.get(1).isFullHealth(), "A's team healed at once on a second click");

		// once free, B tries again (no minute later): B's team goes in and is healed
		helper.succeedWhen(() -> {
			if (!machine.isInUse() && teamA.get(1).isFullHealth() && !said(b, "put your")) {
				b.seen().clear();
				rightClick(b, nurse);
				helper.assertTrue(said(b, "put your 1 Pokémon in the Healing Machine"), "B, once the machine was free: " + b.seen());
			}
			helper.assertTrue(said(b, "put your"), "B hasn't had a turn yet");
			helper.assertFalse(machine.isInUse(), "B's heal still running");
			assertAllFull(helper, teamB);
			assertAllFull(helper, teamA);
			assertParty(helper, b, teamB);
			assertParty(helper, a, teamA);
		});
	}

	/**
	 * Clicking again while my own team is in the machine: she doesn't say it's busy with "another team" (it's my own
	 * minute she means), and the team isn't put in twice.
	 */
	//$ gametest_batch AREA '"qaNurseMachineOwnTeam"'
	@GameTest(template = AREA, batch = "qaNurseMachineOwnTeam")
	public void qaASecondClickIsNotToldAnotherTeam(GameTestHelper helper) {
		clean(helper);
		Villager nurse = nurse(helper, MACHINE, NURSE_AT, 1);
		Visitor v = visitor(helper, "qaNurseA");
		pokemon(v, "pikachu", 1);
		rightClick(v, nurse);
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		helper.assertTrue(machine.isInUse(), "setup: the team didn't go in: " + v.seen());
		v.seen().clear();
		rightClick(v, nurse);
		helper.assertFalse(said(v, "another team"), "with my own team in the machine she says " + v.seen());
		helper.assertTrue(said(v, "Come back in"), "with my own team in the machine she says " + v.seen());
		helper.succeed();
	}

	/**
	 * Nurses level up from their work ("less as they level up"): putting a single hurt Pokémon in the machine, for a
	 * player at full health, earns her experience.
	 */
	//$ gametest_batch AREA '"qaNurseMachineXp"'
	@GameTest(template = AREA, batch = "qaNurseMachineXp")
	public void qaHealingOnePokemonEarnsHerExperience(GameTestHelper helper) {
		clean(helper);
		Villager nurse = nurse(helper, MACHINE, NURSE_AT, 1);
		Visitor v = visitor(helper, "qaNurseA");
		pokemon(v, "pikachu", 1);
		int before = nurse.getVillagerXp();
		helper.assertTrue(v.player().getHealth() == v.player().getMaxHealth(), "setup: the player is hurt");
		rightClick(v, nurse);
		helper.assertTrue(said(v, "put your 1 Pokémon in the Healing Machine"), "her line: " + v.seen());
		helper.assertTrue(nurse.getVillagerXp() > before, "no experience for healing a Pokémon: " + before + " -> " + nurse.getVillagerXp());
		helper.succeed();
	}

	/**
	 * The machine broken mid-heal: the team stays in the party (nothing lost, dropped or doubled), and with a new machine
	 * and a minute gone the player can heal there (the broken machine doesn't keep the team "already healing").
	 */
	//$ gametest_ticks_batch AREA '800' '"qaNurseMachineBroken"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaNurseMachineBroken")
	public void qaMachineBrokenMidHealLosesNothing(GameTestHelper helper) {
		clean(helper);
		Villager nurse = nurse(helper, MACHINE, NURSE_AT, 1);
		Visitor v = visitor(helper, "qaNurseA");
		List<Pokemon> team = List.of(pokemon(v, "pikachu", 0), pokemon(v, "bulbasaur", 4));
		rightClick(v, nurse);
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		helper.assertTrue(machine.isInUse(), "setup: the team didn't go in: " + v.seen());
		helper.runAfterDelay(5, () -> {
			helper.getLevel().destroyBlock(helper.absolutePos(MACHINE), true);
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(MACHINE)).isAir(), "setup: machine not broken");
		});
		helper.runAfterDelay(10, () -> {
			assertParty(helper, v, team);
			assertNothingDropped(helper);
			Villager second = nurse(helper, MACHINE_2, NURSE_2_AT, 1);
			Nurses.resetCooldowns(); // a minute later
			v.seen().clear();
			rightClick(v, second);
			HealingMachineBlockEntity other = helper.getBlockEntity(MACHINE_2);
			helper.assertTrue(other.isInUse() || team.stream().allMatch(Pokemon::isFullHealth),
				"after the first machine was broken mid-heal, the team can't be healed: " + v.seen());
			helper.succeedWhen(() -> {
				assertAllFull(helper, team);
				assertParty(helper, v, team);
				assertNothingDropped(helper);
			});
		});
	}

	/** The nurse killed mid-heal: the machine still finishes, the team is healed and stays in the party. */
	//$ gametest_ticks_batch AREA '800' '"qaNurseMachineNurseGone"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaNurseMachineNurseGone")
	public void qaNurseKilledMidHealTheMachineFinishes(GameTestHelper helper) {
		clean(helper);
		Villager nurse = nurse(helper, MACHINE, NURSE_AT, 1);
		Visitor v = visitor(helper, "qaNurseA");
		List<Pokemon> team = List.of(pokemon(v, "pikachu", 0), pokemon(v, "squirtle", 1));
		rightClick(v, nurse);
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		helper.assertTrue(machine.isInUse(), "setup: the team didn't go in: " + v.seen());
		nurse.kill();
		helper.succeedWhen(() -> {
			helper.assertFalse(machine.isInUse(), "the machine is still running");
			assertAllFull(helper, team);
			assertParty(helper, v, team);
			assertNothingDropped(helper);
		});
	}

	/**
	 * A healthy player with a healthy team: the machine isn't started, the team is untouched, and she still answers.
	 * (Whether this click spends the player's minute is a spec question; not asserted.)
	 */
	//$ gametest_batch AREA '"qaNurseMachineHealthy"'
	@GameTest(template = AREA, batch = "qaNurseMachineHealthy")
	public void qaHealthyTeamDoesNotStartTheMachine(GameTestHelper helper) {
		clean(helper);
		Villager nurse = nurse(helper, MACHINE, NURSE_AT, 1);
		Visitor v = visitor(helper, "qaNurseA");
		List<Pokemon> team = List.of(pokemon(v, "pikachu", -1), pokemon(v, "eevee", -1));
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		rightClick(v, nurse);
		helper.assertFalse(machine.isInUse(), "a healthy team was put in the machine");
		helper.assertFalse(said(v, "put your"), "she claims she put a healthy team in: " + v.seen());
		helper.assertTrue(said(v, "patched you up. Take care out there!") && !said(v, "Pokémon"),
			"with nothing to heal she says " + v.seen());
		assertAllFull(helper, team);
		assertParty(helper, v, team);
		// record: does the no-op click spend the minute?
		team.get(0).setCurrentHealth(1);
		v.seen().clear();
		rightClick(v, nurse);
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA nurse: after a no-op click, a hurt team's click within the minute says {} (machine in use: {})",
			v.seen(), machine.isInUse());
		helper.succeed();
	}

	/**
	 * Once a minute per player: a second heal 30 seconds later is refused for the same player (nothing healed, told how
	 * long), but another player is healed at once.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaNurseMachineCooldown"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaNurseMachineCooldown")
	public void qaTheMinuteIsPerPlayer(GameTestHelper helper) {
		clean(helper);
		Villager nurse = nurse(helper, MACHINE, NURSE_AT, 1);
		Visitor a = visitor(helper, "qaNurseA");
		Visitor b = visitor(helper, "qaNurseB");
		Pokemon pa = pokemon(a, "pikachu", 1);
		Pokemon pb = pokemon(b, "squirtle", 1);
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		rightClick(a, nurse);
		helper.assertTrue(machine.isInUse(), "setup: A's team didn't go in: " + a.seen());
		helper.runAfterDelay(600, () -> {
			helper.assertFalse(machine.isInUse(), "the machine took 30 seconds");
			helper.assertTrue(pa.isFullHealth(), "setup: A's team not healed: " + hp(pa));
			pa.setCurrentHealth(1);
			a.player().setHealth(5);
			a.seen().clear();
			rightClick(a, nurse);
			helper.assertFalse(machine.isInUse(), "A's team went in again 30 seconds later");
			helper.assertValueEqual(pa.getCurrentHealth(), 1, "A's Pokémon HP after a refused click");
			helper.assertTrue(a.player().getHealth() == 5f, "A's health restored within the minute: " + a.player().getHealth());
			helper.assertTrue(said(a, "Come back in"), "A isn't told to come back: " + a.seen());
			rightClick(b, nurse);
			helper.assertTrue(machine.isInUse() && b.player().getUUID().equals(machine.getCurrentUser()), "B was refused for A's minute: " + b.seen());
			helper.succeedWhen(() -> {
				helper.assertTrue(pb.isFullHealth(), "B's team: " + hp(pb));
				helper.assertValueEqual(pa.getCurrentHealth(), 1, "A's Pokémon HP");
			});
		});
	}

	/**
	 * Less as they level up: 35 seconds after a heal, a Novice nurse still refuses the same player, a Master nurse
	 * treats them (their team goes in her machine).
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaNurseMachineMaster"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaNurseMachineMaster")
	public void qaAMasterNurseHasAShorterMinute(GameTestHelper helper) {
		clean(helper);
		Villager novice = nurse(helper, MACHINE, NURSE_AT, 1);
		Villager master = nurse(helper, MACHINE_2, NURSE_2_AT, 5);
		Visitor v = visitor(helper, "qaNurseA");
		Pokemon pokemon = pokemon(v, "pikachu", 1);
		HealingMachineBlockEntity noviceMachine = helper.getBlockEntity(MACHINE);
		HealingMachineBlockEntity masterMachine = helper.getBlockEntity(MACHINE_2);
		rightClick(v, novice);
		helper.assertTrue(noviceMachine.isInUse(), "setup: the team didn't go in: " + v.seen());
		helper.runAfterDelay(700, () -> {
			helper.assertTrue(pokemon.isFullHealth(), "setup: not healed: " + hp(pokemon));
			pokemon.setCurrentHealth(1);
			v.seen().clear();
			rightClick(v, novice);
			helper.assertFalse(noviceMachine.isInUse(), "a Novice treated again after 35 seconds");
			helper.assertTrue(said(v, "Come back in"), "the Novice's refusal: " + v.seen());
			v.seen().clear();
			rightClick(v, master);
			helper.assertTrue(masterMachine.isInUse(), "a Master refused after 35 seconds: " + v.seen());
			helper.succeedWhen(() -> helper.assertTrue(pokemon.isFullHealth(), "not healed by the Master: " + hp(pokemon)));
		});
	}

	/** In a battle: refused ("Finish your battle first."), the machine isn't started and nothing is healed. */
	//$ gametest_ticks_batch AREA '400' '"qaNurseMachineBattle"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaNurseMachineBattle")
	public void qaNoHealingInABattle(GameTestHelper helper) {
		clean(helper);
		Villager nurse = nurse(helper, MACHINE, NURSE_AT, 1);
		Visitor v = visitor(helper, "qaNurseA");
		Pokemon lead = pokemon(v, "bulbasaur", 10);
		Pokemon second = pokemon(v, "pikachu", 0);
		BlockPos post = new BlockPos(5, 2, 5);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 4));
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		trainer.setNoAi(true);
		io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(v.player(), trainer);
		var battle = com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(v.player());
		helper.assertTrue(battle != null, "setup: no battle started");
		try {
			second.setCurrentHealth(1); // a battle against a Trainer heals the team as it starts
			v.player().setHealth(5);
			int leadHp = lead.getCurrentHealth();
			int secondHp = second.getCurrentHealth();
			boolean secondFainted = second.isFainted();
			io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA nurse: once the battle started, the fainted pikachu is {}", hp(second));
			v.seen().clear();
			rightClick(v, nurse);
			HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
			helper.assertTrue(said(v, "Finish your battle first."), "not told why: " + v.seen());
			helper.assertFalse(machine.isInUse(), "the machine started during a battle");
			helper.assertValueEqual(lead.getCurrentHealth(), leadHp, "the lead's HP");
			helper.assertValueEqual(second.getCurrentHealth(), secondHp, "the second Pokémon's HP");
			helper.assertValueEqual(second.isFainted(), secondFainted, "the second Pokémon fainted");
			helper.assertTrue(v.player().getHealth() == 5f, "the player was healed during a battle");
		} finally {
			io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.finishForTest(battle, false);
		}
		helper.succeed();
	}

	/**
	 * {@code nurseHealingMachine} off: a right-click heals the team by hand at once (as at a brewing stand) and the machine
	 * stays idle; switched back on, the next heal (a minute later) goes in the machine again.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaNurseMachineSwitch"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaNurseMachineSwitch")
	public void qaTheSwitchOffHealsByHandAndBackOnUsesTheMachine(GameTestHelper helper) {
		clean(helper);
		PartnerShowsCompatTests.after(helper, () -> Nurses.HEALING_MACHINE = true);
		Villager nurse = nurse(helper, MACHINE, NURSE_AT, 1);
		Visitor v = visitor(helper, "qaNurseA");
		List<Pokemon> team = List.of(pokemon(v, "pikachu", 0), pokemon(v, "eevee", 2));
		HealingMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
		Nurses.HEALING_MACHINE = false;
		rightClick(v, nurse);
		helper.assertFalse(machine.isInUse(), "the machine ran with the switch off");
		assertAllFull(helper, team);
		helper.assertTrue(said(v, "healed your 2 Pokémon"), "her line with the switch off: " + v.seen());

		Nurses.HEALING_MACHINE = true;
		Nurses.resetCooldowns(); // a minute later
		team.get(0).setCurrentHealth(0);
		v.seen().clear();
		rightClick(v, nurse);
		helper.assertTrue(machine.isInUse(), "switched back on, the machine wasn't used: " + v.seen());
		helper.assertTrue(team.get(0).isFainted(), "healed by hand with the switch back on");
		helper.succeedWhen(() -> {
			helper.assertFalse(machine.isInUse(), "the machine is still running");
			assertAllFull(helper, team);
		});
	}
}
