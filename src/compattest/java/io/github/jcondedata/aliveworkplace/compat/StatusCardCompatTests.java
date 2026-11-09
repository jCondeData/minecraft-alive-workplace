package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.fossil.FossilCard;
import io.github.jcondedata.aliveworkplace.fossil.FossilScientists;
import io.github.jcondedata.aliveworkplace.fossil.Revival;
import io.github.jcondedata.aliveworkplace.nurse.NurseCard;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * ROADMAP 43.3, with Cobblemon: the Nurse's card shows the party's health and its Heal heals it (and is grayed out with
 * the reason while her Healing Machine is busy); the Fossil Scientist's card shows each fossil's progress, and its Hand
 * over brings a finished one to the party; the card reads the same after a save and reload. Every click is the player's
 * own, through the server's packet handler.
 */
public class StatusCardCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** An empty-hand right-click on {@code villager} through the packet handler; the card it opened. */
	static StatusCardMenu open(GameTestHelper helper, ServerPlayer player, Villager villager) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setShiftKeyDown(false);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND));
		}
		helper.assertTrue(player.containerMenu instanceof StatusCardMenu, "no card opened: " + player.containerMenu.getClass().getSimpleName());
		return (StatusCardMenu) player.containerMenu;
	}

	/** The player clicks button {@code id} on the open card, as the client sends it. */
	static void press(ServerPlayer player, StatusCardMenu menu, int id) {
		player.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(menu.containerId, id));
	}

	static String text(StatusCard.Line line) {
		return line.label().getString() + ": " + line.value().getString();
	}

	// ------------------------------------------------------------------ the Nurse

	/**
	 * A nurse at a brewing stand; the player hurt with a hurt and a fainted Pokémon. The card shows "You" at 6 / 20 HP,
	 * each Pokémon's HP (orange) or "Fainted" (red) with a bar; Heal heals the player and the whole party, as a click did.
	 */
	//$ gametest_ticks_batch AREA '200' '"cardNurseParty"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cardNurseParty")
	public void nurseCardShowsThePartyAndHealHealsIt(GameTestHelper helper) {
		QaNurseMachine1102dCompatTests.clean(helper);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.BREWING_STAND);
		Villager nurse = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		nurse.setNoAi(true);
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(new BlockPos(2, 2, 2)), PoiTypes.CLERIC, ModVillagers.NURSE);
		nurse.setCustomName(Component.literal("Joy"));
		QaNurseMachine1102dCompatTests.Visitor v = QaNurseMachine1102dCompatTests.visitor(helper, "cardNurse");
		Pokemon hurt = QaNurseMachine1102dCompatTests.pokemon(v, "bulbasaur", 3);
		Pokemon fainted = QaNurseMachine1102dCompatTests.pokemon(v, "squirtle", 0);
		v.player().setHealth(6f);
		StatusCardMenu menu = open(helper, v.player(), nurse);
		StatusCard card = menu.card();
		helper.assertTrue(card.lines().size() == 3, "lines: " + card.lines().stream().map(StatusCardCompatTests::text).toList());
		helper.assertValueEqual(text(card.lines().get(0)), "You: 6 / 20 HP", "the player's line");
		helper.assertValueEqual(text(card.lines().get(1)), "Bulbasaur · Lv. 20: 3 / " + hurt.getMaxHealth() + " HP", "the hurt Pokémon's line");
		helper.assertTrue(card.lines().get(1).color() == StatusCard.ORANGE && card.lines().get(1).hasBar(), "the hurt line isn't orange with a bar");
		helper.assertValueEqual(text(card.lines().get(2)), "Squirtle · Lv. 20: Fainted", "the fainted Pokémon's line");
		helper.assertTrue(card.lines().get(2).color() == StatusCard.RED && card.lines().get(2).bar() == 0f, "the fainted line isn't red and empty");
		helper.assertValueEqual(card.heading().getString(), "Your health and your party", "the heading");
		helper.assertTrue(card.button(NurseCard.HEAL).usable(), "Heal is grayed out: " + card.button(NurseCard.HEAL).refusal().getString());

		press(v.player(), menu, NurseCard.HEAL);
		helper.assertTrue(v.player().containerMenu == v.player().inventoryMenu, "the card stayed open");
		helper.assertTrue(v.player().getHealth() == v.player().getMaxHealth(), "the player wasn't healed: " + v.player().getHealth());
		helper.assertTrue(hurt.isFullHealth() && fainted.isFullHealth() && !fainted.isFainted(), "the party wasn't healed");
		helper.assertTrue(QaNurseMachine1102dCompatTests.said(v, "healed your 2 Pokémon"), "her line: " + v.seen());

		// straight after, her card shows everyone full (green) and Heal grayed out with her wait
		StatusCard again = open(helper, v.player(), nurse).card();
		helper.assertValueEqual(text(again.lines().get(1)), "Bulbasaur · Lv. 20: " + hurt.getMaxHealth() + " / " + hurt.getMaxHealth() + " HP", "after");
		helper.assertTrue(again.lines().get(1).color() == StatusCard.GREEN, "a full Pokémon isn't green");
		helper.assertValueEqual(again.button(NurseCard.HEAL).refusal().getString(), "Joy just treated you. Come back in " + io.github.jcondedata.aliveworkplace.nurse.Nurses.secondsLeft(
			io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(io.github.jcondedata.aliveworkplace.nurse.Nurses.COOLDOWN, nurse)) + " seconds.", "the wait");
		helper.succeed();
	}

	/**
	 * A nurse at a Healing Machine busy with A's team: B's card grays Heal with "busy"; B presses it anyway (as an old
	 * client could) and is told the same and B's team is left alone.
	 */
	//$ gametest_ticks_batch AREA '200' '"cardNurseBusy"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cardNurseBusy")
	public void nurseCardGraysHealWhileHerMachineIsBusy(GameTestHelper helper) {
		QaNurseMachine1102dCompatTests.clean(helper);
		Villager nurse = QaNurseMachine1102dCompatTests.nurse(helper, QaNurseMachine1102dCompatTests.MACHINE, QaNurseMachine1102dCompatTests.NURSE_AT, 1);
		nurse.setCustomName(Component.literal("Joy"));
		QaNurseMachine1102dCompatTests.Visitor a = QaNurseMachine1102dCompatTests.visitor(helper, "cardNurseA");
		QaNurseMachine1102dCompatTests.Visitor b = QaNurseMachine1102dCompatTests.visitor(helper, "cardNurseB");
		QaNurseMachine1102dCompatTests.pokemon(a, "pikachu", 1);
		Pokemon theirs = QaNurseMachine1102dCompatTests.pokemon(b, "charmander", 2);
		HealingMachineBlockEntity machine = helper.getBlockEntity(QaNurseMachine1102dCompatTests.MACHINE);

		StatusCardMenu first = open(helper, a.player(), nurse);
		helper.assertTrue(first.card().subtitle().getString().contains("Healing Machine"), "the subtitle: " + first.card().subtitle().getString());
		press(a.player(), first, NurseCard.HEAL);
		helper.assertTrue(machine.isInUse(), "A's team didn't go in: " + a.seen());

		StatusCardMenu menu = open(helper, b.player(), nurse);
		StatusCard.Button heal = menu.card().button(NurseCard.HEAL);
		helper.assertValueEqual(heal.refusal().getString(), "Joy's Healing Machine is busy with another team. Just a moment, please!", "B's Heal");
		press(b.player(), menu, NurseCard.HEAL);
		helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "busy"), "B isn't told the machine is busy: " + b.seen());
		helper.assertFalse(theirs.isFullHealth(), "B's team was healed while the machine was busy");
		helper.assertTrue(a.player().getUUID().equals(machine.getCurrentUser()), "B's press took the machine from A");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the Fossil Scientist

	/**
	 * A Dome Fossil handed over (holding it, as before): the empty-hand card shows it with its progress and a bar, and Hand
	 * over grayed out ("Nothing of yours is ready yet"); once it's done the card says Ready and Hand over brings the Kabuto
	 * to the party and empties the queue. Close closes and does nothing else.
	 */
	//$ gametest_ticks_batch AREA '400' '"cardFossilProgress"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "cardFossilProgress")
	public void fossilCardShowsProgressAndHandOverDelivers(GameTestHelper helper) {
		Villager scientist = QaFossilScientist1102dCompatTests.scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaFossilScientist1102dCompatTests.player(helper, seen, UUID.randomUUID(), "card-fossil");
		QaFossilScientist1102dCompatTests.setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			QaFossilScientist1102dCompatTests.hold(player, new ItemStack(QaFossilScientist1102dCompatTests.cobblemon("dome_fossil")), ItemStack.EMPTY);
			QaFossilScientist1102dCompatTests.click(player, scientist, false, seen);
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "the fossil wasn't taken: " + QaFossilScientist1102dCompatTests.last(seen));
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "a fossil in hand opened the card instead of handing it over");
			// half done
			Revival r = FossilScientists.queue(scientist).get(0);
			ModAttachments.FOSSIL_REVIVALS.set(scientist, List.of(r.worked(r.total() / 2)));
			StatusCardMenu menu = open(helper, player, scientist);
			StatusCard card = menu.card();
			helper.assertTrue(card.lines().size() == 1, "lines: " + card.lines().stream().map(StatusCardCompatTests::text).toList());
			helper.assertValueEqual(text(card.lines().get(0)), "Dome Fossil: 50%", "the fossil's line");
			helper.assertTrue(Math.abs(card.lines().get(0).bar() - 0.5f) < 0.01f, "the bar: " + card.lines().get(0).bar());
			helper.assertTrue(card.lines().get(0).icon().is(QaFossilScientist1102dCompatTests.cobblemon("dome_fossil")), "the icon isn't the fossil");
			helper.assertValueEqual(card.subtitle().getString(), "A revival costs 800 CobbleDollars. Working on 1 of 3 fossils.", "the subtitle");
			helper.assertValueEqual(card.button(FossilCard.HAND_OVER).refusal().getString(),
				"Nothing of yours is ready yet. It comes to you by itself when it is.", "Hand over while it works");
			press(player, menu, StatusCardMenu.CLOSE);
			helper.assertTrue(player.containerMenu == player.inventoryMenu && FossilScientists.queue(scientist).size() == 1, "Close did more than close");

			// done (as her work leaves it when its owner was away): Ready, and Hand over brings the Kabuto
			ModAttachments.FOSSIL_REVIVALS.set(scientist, List.of(r.worked(r.total())));
			StatusCardMenu done = open(helper, player, scientist);
			helper.assertValueEqual(text(done.card().lines().get(0)), "Dome Fossil: Ready", "the finished line");
			helper.assertTrue(done.card().button(FossilCard.HAND_OVER).usable(), "Hand over is grayed out with a Pokémon ready");
			press(player, done, FossilCard.HAND_OVER);
			helper.assertTrue(QaFossilScientist1102dCompatTests.party(player).equals(List.of("kabuto")), "party: " + QaFossilScientist1102dCompatTests.party(player));
			helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "still queued");
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "the card stayed open");
			helper.succeed();
		});
	}

	/**
	 * Nothing handed over: the card shows the old hint with the price where the fossils go, Hand over grayed out with
	 * how to hand one over; pressed anyway, it answers as the empty-hand click did (the hint).
	 */
	//$ gametest_ticks_batch AREA '200' '"cardFossilEmpty"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cardFossilEmpty")
	public void fossilCardWithNothingHandedOver(GameTestHelper helper) {
		Villager scientist = QaFossilScientist1102dCompatTests.scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaFossilScientist1102dCompatTests.player(helper, seen, UUID.randomUUID(), "card-fossil-none");
		helper.runAfterDelay(2, () -> {
			StatusCardMenu menu = open(helper, player, scientist);
			StatusCard card = menu.card();
			helper.assertTrue(card.lines().isEmpty(), "lines with nothing handed over");
			helper.assertTrue(card.empty().getString().endsWith("a fossil to revive it, for 800 CobbleDollars."), "the empty line: " + card.empty().getString());
			helper.assertTrue(card.button(FossilCard.HAND_OVER).refusal().getString().startsWith("Hold a fossil and right-click "), "Hand over's reason: "
				+ card.button(FossilCard.HAND_OVER).refusal().getString());
			press(player, menu, FossilCard.HAND_OVER);
			helper.assertTrue(QaFossilScientist1102dCompatTests.last(seen).endsWith("a fossil to revive it, for 800 CobbleDollars."), "pressed: " + seen);
			helper.succeed();
		});
	}

	/** Saved and loaded mid-revival, the scientist's card shows the same progress, and another player's card shows none of it. */
	//$ gametest_ticks_batch AREA '200' '"cardFossilReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cardFossilReload")
	public void fossilCardAfterASaveAndReload(GameTestHelper helper) {
		Villager scientist = QaFossilScientist1102dCompatTests.scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaFossilScientist1102dCompatTests.player(helper, seen, UUID.randomUUID(), "card-fossil-reload");
		ServerPlayer other = QaFossilScientist1102dCompatTests.player(helper, new ArrayList<>(), UUID.randomUUID(), "card-fossil-other");
		QaFossilScientist1102dCompatTests.setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			QaFossilScientist1102dCompatTests.hold(player, new ItemStack(QaFossilScientist1102dCompatTests.cobblemon("dome_fossil")), ItemStack.EMPTY);
			QaFossilScientist1102dCompatTests.click(player, scientist, false, seen);
			Revival r = FossilScientists.queue(scientist).get(0);
			ModAttachments.FOSSIL_REVIVALS.set(scientist, List.of(r.worked(r.total() / 4)));
			CompoundTag tag = new CompoundTag();
			scientist.saveWithoutId(tag);
			scientist.discard();
			Villager copy = EntityType.VILLAGER.create(helper.getLevel());
			copy.load(tag);
			helper.assertTrue(helper.getLevel().addFreshEntity(copy), "couldn't put the loaded scientist back");
			StatusCard card = open(helper, player, copy).card();
			helper.assertValueEqual(text(card.lines().get(0)), "Dome Fossil: 25%", "after the reload");
			StatusCard theirs = open(helper, other, copy).card();
			helper.assertTrue(theirs.lines().isEmpty(), "another player sees this player's fossil");
			helper.assertTrue(theirs.subtitle().getString().contains("Working on 1 of 3"), "the queue count: " + theirs.subtitle().getString());
			helper.succeed();
		});
	}
}
