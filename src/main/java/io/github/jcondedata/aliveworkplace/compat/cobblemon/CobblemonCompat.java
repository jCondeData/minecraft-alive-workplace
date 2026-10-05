//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import io.github.jcondedata.aliveworkplace.fossil.FossilLab;
import io.github.jcondedata.aliveworkplace.nurse.PokemonHealing;
import io.github.jcondedata.aliveworkplace.orchard.PokemonFruit;
import io.github.jcondedata.aliveworkplace.ranch.DaycareDesk;
import io.github.jcondedata.aliveworkplace.trader.PokemonTrades;
import io.github.jcondedata.aliveworkplace.trainer.TrainerBattles;
import io.github.jcondedata.aliveworkplace.tutor.MoveLessons;
import io.github.jcondedata.aliveworkplace.work.PokemonPartners;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

// The Cobblemon integration: fills in the mod's Pokémon extension points (partners, fruit, fossils, the daycare,
// trainers, traders, tutors, the nurse) with the Cobblemon code in this package, and starts the trainers' battles.
public final class CobblemonCompat {
	// The Cobblemon versions this was tested with: 1.7.3 (the Cobbleverse pack pins it) and 1.8.x (-Pcobblemon18=true).
	public static final String TESTED = ">=1.7.3 <1.9";

	public static void init() {
		PokemonPartners.EXTENSION.register("cobblemon", new PokemonPartners() {
			@Override
			public List<Component> helpers(ServerLevel level, BlockPos center, int radius, Set<String> types, int max) {
				return CobblemonPartners.helpers(level, center, radius, types, max);
			}

			@Override
			public List<Fighter> fighters(ServerLevel level, BlockPos center, int radius, Set<String> types, int max) {
				return CobblemonPartners.fighters(level, center, radius, types, max);
			}

			@Override
			public void useMove(ServerLevel level, LivingEntity pokemon, LivingEntity target, String type) {
				CobblemonPartners.useMove(level, pokemon, target, type);
			}

			@Override
			public List<Entity> pastured(ServerLevel level, BlockPos center, int radius) {
				return CobblemonPartners.pastured(level, center, radius);
			}

			@Override
			public boolean matches(Entity entity, String properties) {
				return CobblemonPartners.matches(entity, properties);
			}

			@Override
			public boolean canBefriend(Entity entity) {
				return CobblemonPartners.canBefriend(entity);
			}

			@Override
			public boolean befriend(ServerLevel level, Entity entity, int amount) {
				return CobblemonPartners.befriend(level, entity, amount);
			}

			@Override
			public boolean isPastured(Entity entity) {
				return CobblemonPartners.isPastured(entity);
			}

			@Override
			public boolean canPerform(Entity entity) {
				return CobblemonPartners.canPerform(entity);
			}

			@Override
			public BlockPos reachable(Entity entity, BlockPos target) {
				return CobblemonPartners.reachable(entity, target);
			}

			@Override
			public double roamTop(Entity entity) {
				return CobblemonPartners.roamTop(entity);
			}

			@Override
			public boolean walkTo(Entity entity, BlockPos pos, double speed) {
				return CobblemonPartners.walkTo(entity, pos, speed);
			}

			@Override
			public void goHome(Entity entity) {
				CobblemonPartners.goHome(entity);
			}

			@Override
			public void animate(ServerLevel level, Entity entity, String animation) {
				CobblemonPartners.animate(level, entity, animation);
			}

			@Override
			public boolean effect(ServerLevel level, ResourceLocation id, net.minecraft.world.phys.Vec3 at) {
				return CobblemonPartners.effect(level, id, at);
			}
		});
		PokemonFruit.EXTENSION.register("cobblemon", new PokemonFruit() {
			@Override
			public boolean isRipe(BlockState state) {
				return CobblemonOrchard.isRipe(state);
			}

			@Override
			public boolean isSeed(ItemStack stack) {
				return CobblemonOrchard.isSeed(stack);
			}

			@Override
			public boolean needsFarmland(ItemStack seed) {
				return CobblemonOrchard.needsFarmland(seed);
			}

			@Override
			public boolean growsIntoTree(ItemStack seed) {
				return CobblemonOrchard.growsIntoTree(seed);
			}

			@Override
			public List<ItemStack> pick(ServerLevel level, BlockPos pos, Entity picker) {
				return CobblemonOrchard.pick(level, pos, picker);
			}
		});
		FossilLab.EXTENSION.register("cobblemon", new FossilLab() {
			@Override
			public boolean isFossil(ItemStack stack) {
				return CobblemonFossils.isFossil(stack);
			}

			@Override
			public ResourceLocation fossil(List<ItemStack> items) {
				return CobblemonFossils.fossil(items);
			}

			@Override
			public boolean partOfOne(List<ItemStack> items) {
				return CobblemonFossils.partOfOne(items);
			}

			@Override
			public Component revive(ServerPlayer player, ResourceLocation fossil) {
				return CobblemonFossils.revive(player, fossil);
			}
		});
		DaycareDesk.EXTENSION.register("cobblemon", new DaycareDesk() {
			@Override
			public void open(ServerPlayer player, Villager rancher) {
				CobblemonDaycare.open(player, rancher);
			}

			@Override
			public void returnAll(ServerLevel level, Villager rancher) {
				CobblemonDaycare.returnAll(level, rancher);
			}
		});
		TrainerBattles.EXTENSION.register("cobblemon", CobblemonTrainers::challenge);
		PokemonTrades.EXTENSION.register("cobblemon", CobblemonTraders::open);
		MoveLessons.EXTENSION.register("cobblemon", CobblemonTutors::open);
		PokemonHealing.EXTENSION.register("cobblemon", new PokemonHealing() {
			@Override
			public boolean inBattle(ServerPlayer player) {
				return CobblemonNurse.inBattle(player);
			}

			@Override
			public int healParty(ServerPlayer player) {
				return CobblemonNurse.healParty(player);
			}

			@Override
			public int healAtMachine(ServerPlayer player, ServerLevel level, BlockPos machine) {
				return CobblemonNurse.healAtMachine(player, level, machine);
			}

			@Override
			public void charge(ServerLevel level, BlockPos machine) {
				CobblemonNurse.charge(level, machine);
			}
		});
		CobblemonTrainers.init();
		io.github.jcondedata.aliveworkplace.berry.BerryChains.BerryData.EXTENSION.register("cobblemon", new CobblemonBerries()); // 28.9
		io.github.jcondedata.aliveworkplace.camp.CampCooks.Pot.EXTENSION.register("cobblemon", new CobblemonCampPot()); // 28.8
		io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.Snacks.EXTENSION.register("cobblemon", new CobblemonHabitat()); // 28.10
		io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.Breeding.EXTENSION.register("cobblemon", new CobblemonDaycareKeeper()); // 28.12
		io.github.jcondedata.aliveworkplace.cup.CupBattlers.EXTENSION.register("cobblemon", new CobblemonCupBouts()); // 28.18
		CobblemonCupBouts.init();
	}

	private CobblemonCompat() {
	}
}
//?}
