package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
 * SCENE=steward_desk (ROADMAP 27.8): a Village Hall with a Homes zone east of it, a Builder at his table and a Steward
 * with three proposals on his desk (a Well, a Park Bench and a Stone House, each with why, where, materials and builder).
 * The hall's "What next?" page is his desk; a proposal's Show me lights its outline in the world; Approve starts the
 * build and the builder sets off.
 */
final class StewardDeskScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private static final List<ResourceLocation> BLUEPRINTS = List.of(AliveWorkplace.id("well"), AliveWorkplace.id("park_bench"),
		AliveWorkplace.id("stone_house"));
	private int tick;
	private volatile Villager builder;
	private volatile StewardDesk.Proposal first;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 60) {
			server.execute(() -> openDesk(server));
		}
		if (tick == 80) {
			ScreenshotHarness.pointAt(mc, StewardDeskPage.FIRST_PROPOSAL);
		}
		if (tick == 100) {
			ScreenshotHarness.shot(mc, "01_steward_desk");
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(StewardDeskPage.FIRST_PROPOSAL, player);
				}
			});
		}
		if (tick == 115) {
			ScreenshotHarness.pointAt(mc, StewardDeskPage.APPROVE);
		}
		if (tick == 130) {
			ScreenshotHarness.shot(mc, "02_steward_proposal");
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(StewardDeskPage.SHOW, player); // closes the screen: the outline glows
				}
				Showcase.check(BlueprintOutline.glowing(player), "Show me lit the proposal's outline in the world");
				if (first == null) {
					return;
				}
				Vec3 at = Vec3.atCenterOf(first.placement().origin());
				ScreenshotHarness.hoverLookingAt(player, at.add(-6, 9, 10), at);
			});
		}
		if (tick == 175) {
			ScreenshotHarness.shot(mc, "03_steward_show_me");
			server.execute(() -> {
				openDesk(server);
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(StewardDeskPage.FIRST_PROPOSAL, player);
					menu.press(StewardDeskPage.APPROVE, player);
				}
				List<BuildSite> open = StewardDesk.openSites(server.overworld(), HALL);
				Showcase.check(open.size() == 1 && builder.getUUID().equals(open.get(0).builder()),
					"approving started the build for the village's builder (" + open.size() + " open)");
				player.closeContainer();
				if (first == null) {
					return;
				}
				Vec3 at = Vec3.atCenterOf(first.placement().origin());
				ScreenshotHarness.hoverLookingAt(player, at.add(-8, 10, 12), at);
			});
		}
		if (tick == 380) {
			ScreenshotHarness.shot(mc, "04_builder_sets_off");
			server.execute(() -> Showcase.check(Builders.activeSite(server.overworld(), builder) != null,
				"the builder is on the Steward's build"));
		}
		if (tick == 400) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static void openDesk(MinecraftServer server) {
		ServerPlayer player = player(server);
		VillageHallScreen.open(player, HALL);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(VillageHallScreen.ADVICE, player);
			Showcase.check(menu.icon(StewardDeskPage.STEWARD).is(ModItems.CITY_PLAN)
				&& menu.icon(StewardDeskPage.FIRST_PROPOSAL + 2).is(ModItems.BLUEPRINT), "the What next? page is the Steward's desk, with three proposals");
		}
	}

	/** The hall (the player's) with a Homes zone east of it, a Builder at his table, a Steward and three proposals. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2000);
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 40; x++) {
			for (int z = -16; z <= 16; z++) {
				cells.set(CityPlan.cellAt(HALL, HALL.offset(x, 0, z)));
			}
		}
		hall.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		BlockPos table = HALL.offset(3, 0, -5);
		level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
		builder = EntityType.VILLAGER.spawn(level, table.south(), MobSpawnType.COMMAND);
		Builders.employ(level, builder, table);
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, HALL);
		Villager steward = EntityType.VILLAGER.spawn(level, HALL.south(2), MobSpawnType.COMMAND);
		Stewards.appoint(player, steward, plan);
		ModAttachments.STEWARD_ROUND_DAY.set(steward, StewardWishes.day(level));
		String[] whys = {"steward.aliveworkplace.why.beds", "steward.aliveworkplace.why.beds", "steward.aliveworkplace.why.beds"};
		for (int i = 0; i < BLUEPRINTS.size(); i++) {
			ResourceLocation blueprint = BLUEPRINTS.get(i);
			Plots.Search search = Plots.search(level, HALL, hall.plan(), new Plots.Request(List.of(blueprint), "homes", i * 3));
			search.finish();
			Optional<Plots.Plot> plot = search.result();
			if (plot.isEmpty()) {
				continue;
			}
			StewardWishes.Wish wish = new StewardWishes.Wish(AliveWorkplace.id("showcase/desk_" + i), new StewardRules.Effect(StewardRules.Kind.BUILD,
				Optional.of(blueprint), Optional.of("homes"), Optional.empty(), Optional.empty()), 50, whys[i], List.of(3L));
			Optional<StewardDesk.Proposal> p = StewardDesk.offer(level, HALL, wish, plot.get().blueprint(), plot.get().placement(), plot.get().zone(), false);
			if (i == 0 && p.isPresent()) {
				first = p.get();
			}
		}
		ScreenshotHarness.hover(player, Vec3.atCenterOf(HALL).add(-3, 2, 4), 200, 20);
	}
}
