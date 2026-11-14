package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * The Trainer screen (ROADMAP 43.1): an empty hand on a Trainer or Trainer Leader opens it instead of starting the
 * battle at once. The Village Hall's look: who the trainer is, the player's record against them, today's line and the
 * rules on the left; the reward, the team (the half the player hasn't met hidden until they win once), the player's
 * party and Challenge, Trade and Close on the right. Challenge is grayed out with the reason a click would have given.
 */
public final class TrainerScreens {
	/**
	 * The buttons' labels, in the order the screen lays them out: Challenge, Trade, Close ({@link TrainerMenu#CHALLENGE},
	 * {@code TRADE}, {@code CLOSE}). The client's TrainerScreen fits them with {@code StatusCard.buttonRow} (B1131), and
	 * TrainerScreenButtonsGameTests checks that layout with these labels.
	 */
	public static final String[] BUTTON_KEYS = {"screen.aliveworkplace.trainer.button.challenge",
		"screen.aliveworkplace.trainer.button.trade", "screen.aliveworkplace.trainer.button.close"};

	/** Opens the screen on {@code trainer} for {@code player}. */
	public static void open(ServerPlayer player, Villager trainer) {
		TrainerView view = view(player, trainer);
		Platform.get().openMenu(player, new SimpleMenuProvider((id, inventory, p) -> new TrainerMenu(id, inventory, view, trainer),
			Trainers.title(trainer)), view);
	}

	/** What the screen shows {@code player} about {@code trainer} right now. */
	public static TrainerView view(ServerPlayer player, Villager trainer) {
		ServerLevel level = (ServerLevel) trainer.level();
		Component name = trainer.getDisplayName();
		boolean leader = Trainers.isLeader(trainer);
		Component title = leader ? Component.translatable("message.aliveworkplace.trainer.leader_title")
			: Component.translatable("screen.aliveworkplace.trainer.trainer");
		Component levelName = BuilderLevels.levelName(Trainers.tier(trainer));
		Component rank = io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(level, trainer.blockPosition())
			.map(hall -> (Component) Component.translatable("screen.aliveworkplace.trainer.rank_village", levelName,
				io.github.jcondedata.aliveworkplace.hall.VillageHalls.name(level, hall)))
			.orElse(Component.translatable("screen.aliveworkplace.trainer.rank", levelName));
		long[] record = Trainers.record(player.getUUID(), trainer);
		long day = level.getDayTime() / 24000L;
		boolean paid = Trainers.paidToday(player.getUUID(), trainer);
		Component today;
		int todayColor;
		if (leader && Trainers.challengedToday(player, trainer)) {
			today = Component.translatable(paid ? "screen.aliveworkplace.trainer.today.won" : "screen.aliveworkplace.trainer.today.challenged");
			todayColor = paid ? TrainerView.GREEN : TrainerView.ORANGE;
		} else if (paid) {
			today = Component.translatable("screen.aliveworkplace.trainer.today.won");
			todayColor = TrainerView.GREEN;
		} else if (record[2] == day) {
			today = Component.translatable("screen.aliveworkplace.trainer.today.battled");
			todayColor = TrainerView.GOLD;
		} else {
			today = Component.translatable("screen.aliveworkplace.trainer.today.none");
			todayColor = TrainerView.GOLD;
		}
		TrainerView.Sides sides = TrainerBattles.EXTENSION.call(battles -> battles.sides(player, trainer), TrainerView.Sides.NONE);
		Component rules = !Trainers.COBBLEMON ? Component.translatable("screen.aliveworkplace.trainer.rules")
			: sides.levelCap() > 0 ? Component.translatable("screen.aliveworkplace.trainer.rules.cap", sides.levelCap())
			: Component.translatable("screen.aliveworkplace.trainer.rules.no_cap");
		int[] prize = Trainers.prize(trainer);
		Component reward = paid ? Component.translatable("screen.aliveworkplace.trainer.reward.paid", name)
			: Component.translatable("screen.aliveworkplace.trainer.reward", io.github.jcondedata.aliveworkplace.work.Money.describe(prize[0], prize[1]));
		List<TrainerView.TeamSlot> team = new ArrayList<>();
		int shown = shown(record[0] > 0, sides.team().size());
		for (int i = 0; i < sides.team().size(); i++) {
			team.add(i < shown ? sides.team().get(i) : new TrainerView.TeamSlot(ItemStack.EMPTY, 0, false));
		}
		Component teamHint = shown < team.size() ? Component.translatable("screen.aliveworkplace.trainer.team.hidden", name) : Component.empty();
		Component partyNote = Trainers.COBBLEMON && sides.party().isEmpty() ? Component.translatable("screen.aliveworkplace.trainer.party.none")
			: Component.empty();
		Component refusal = Trainers.refusal(player, trainer);
		return new TrainerView(trainer.getId(), name, title, rank,
			Component.translatable("screen.aliveworkplace.trainer.record_line", record[0], record[1]), today, todayColor, rules, reward,
			Component.translatable("screen.aliveworkplace.trainer.team", name), List.copyOf(team), teamHint, sides.party(), partyNote,
			refusal == null ? Component.empty() : refusal, !trainer.isSleeping() && !trainer.isBaby() && !trainer.getOffers().isEmpty());
	}

	/**
	 * How many of a team of {@code size} the player sees: all once they've beaten the trainer, else the first half (rounded
	 * up), the ones a trainer leads with.
	 */
	public static int shown(boolean beaten, int size) {
		return beaten ? size : (size + 1) / 2;
	}

	private TrainerScreens() {
	}
}
