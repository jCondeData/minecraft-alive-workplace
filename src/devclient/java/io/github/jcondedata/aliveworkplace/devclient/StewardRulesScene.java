package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.BitSet;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=steward_rules (ROADMAP 27.6): a Village Hall with a Homes zone on its plan, a Builder and three villagers
 * without a bed, and a Steward. Back at the hall he ranks the day's wishes from his rules and says the first over his
 * head ("build a Stone House in a Homes zone: 4 villagers have no bed"); then {@code /workplace steward explain} lists
 * every rule in chat with its conditions' numbers, which held and which didn't.
 */
final class StewardRulesScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private int tick;
	private volatile Villager steward;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.options.chatHeightFocused().set(1.0);
			mc.options.chatWidth().set(1.0);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 140) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				StewardWishes.State state = StewardWishes.of(level, HALL);
				List<String> rules = state.wishes().stream().map(w -> w.rule().getPath()).toList();
				Showcase.check(state.day() == StewardWishes.day(level) && !rules.isEmpty() && rules.get(0).startsWith("homes_"),
					"back at the hall, the Steward ranked today's wishes from his rules, more homes first (" + rules + ")");
				Vec3 at = steward.position();
				ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), at.add(2.5, 1.6, 3.5), at.add(0, 1.6, 0));
			});
		}
		if (tick == 170) {
			ScreenshotHarness.shot(mc, "01_steward_wish");
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ScreenshotHarness.hover(player, Vec3.atCenterOf(HALL).add(0, 1.5, 4), 180, 20);
				server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "workplace steward explain");
				List<Component> lines = StewardWishes.explain(server.overworld(), HALL, StewardRules.all());
				long held = lines.stream().filter(l -> ruleShown(l, "command.aliveworkplace.steward.explain.held")).count();
				long notHeld = lines.stream().filter(l -> ruleShown(l, "command.aliveworkplace.steward.explain.not_held")).count();
				Showcase.check(held > 0 && notHeld > 0, "/workplace steward explain lists rules that held (" + held + ") and that didn't (" + notHeld + ")");
			});
		}
		if (tick == 190) {
			mc.setScreen(new ChatScreen(""));
		}
		if (tick == 210) {
			ScreenshotHarness.shot(mc, "02_steward_explain_end");
			mc.gui.getChat().scrollChat(1000); // to the top: the header and the first rules
		}
		if (tick == 230) {
			ScreenshotHarness.shot(mc, "03_steward_explain_top");
		}
		if (tick == 250) {
			mc.setScreen(null);
			mc.stop();
		}
	}

	private static boolean ruleShown(Component line, String mark) {
		return line.getContents() instanceof TranslatableContents t && t.getKey().equals("command.aliveworkplace.steward.explain.rule")
			&& t.getArgs().length > 0 && t.getArgs()[0] instanceof Component c && c.getContents() instanceof TranslatableContents m && m.getKey().equals(mark);
	}

	/** The hall with a Homes zone, a Builder at his table, three villagers without a bed and a Steward who has walked his rounds. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2000);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		BitSet cells = new BitSet();
		cells.set(CityPlan.cellAt(HALL, HALL.offset(-12, 0, -12)));
		hall.setPlan(CityPlan.EMPTY.addZone("homes", "Homes", "").paint(0, cells));
		BlockPos table = HALL.offset(6, 0, -3);
		level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
		Villager builder = EntityType.VILLAGER.spawn(level, table.south(), MobSpawnType.COMMAND);
		Builders.employ(level, builder, table);
		for (int i = 0; i < 3; i++) {
			EntityType.VILLAGER.spawn(level, HALL.offset(-4 + 2 * i, 0, -5), MobSpawnType.COMMAND);
		}
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, HALL);
		steward = EntityType.VILLAGER.spawn(level, HALL.south(2), MobSpawnType.COMMAND);
		steward.setVillagerData(steward.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER).setLevel(io.github.jcondedata.aliveworkplace.city.Stewards.MIN_BUILDER_LEVEL)); steward.setVillagerXp(70); // a seasoned Builder (27.1a)
		Stewards.appoint(player, steward, plan);
		ModAttachments.STEWARD_ROUND_DAY.set(steward, StewardWishes.day(level)); // his rounds are walked: straight to planning
		ScreenshotHarness.hover(player, Vec3.atCenterOf(HALL).add(4, 3, 6), 150, 25);
	}
}
