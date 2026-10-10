package io.github.jcondedata.aliveworkplace.printer;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.craft.LuxuryWork;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Printer's shift (ROADMAP 34.11): the luxury workshop engine at the cartography table. While printing, pages turn
 * and scraps of paper fly off the press. The Gazette and the Illuminated Book are written from the village's hall as
 * they're finished ({@link Gazette}); books are only kept in stock while the village has a Scholar. Nothing at all with
 * config {@code printers} off.
 */
public class PrinterWork extends LuxuryWork {
	/** How many times a Printer has worked the press since the server started (tests read it). */
	public static int pulls;
	/** Where the press stands (the shift's station): the hall nearest it is the one the papers are written from. */
	@Nullable
	private BlockPos press;

	public PrinterWork() {
		super("printer");
	}

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new PrinterWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return Printers.ENABLED && super.checkExtraStartConditions(level, villager);
	}

	@Nullable
	@Override
	protected Job choose(ServerLevel level, Villager villager, BlockPos station) {
		press = station.immutable();
		return super.choose(level, villager, station);
	}

	/** Books are only kept in stock while the village has a Scholar to read them (the store's orders are still filled). */
	@Override
	protected boolean stocks(ServerLevel level, Villager villager, BlockPos station, Item good) {
		return good != Items.BOOK || hasScholar(level, station);
	}

	/** Whether the village round {@code station} (its hall's, else the press's own surroundings) has a Scholar. */
	public static boolean hasScholar(ServerLevel level, BlockPos station) {
		BlockPos centre = VillageHalls.nearest(level, station).orElse(station);
		return !level.getEntitiesOfClass(Villager.class, new AABB(centre).inflate(VillageHalls.RADIUS),
			v -> v.isAlive() && !v.isBaby() && v.getVillagerData().getProfession() == ModVillagers.SCHOLAR).isEmpty();
	}

	/** The papers are written as they come off the press, from the hall that day. */
	@Override
	protected ItemStack finished(ServerLevel level, ItemStack stack) {
		ItemStack out = super.finished(level, stack);
		if (press != null && (out.is(ModItems.GAZETTE) || out.is(ModItems.ILLUMINATED_BOOK))) {
			Gazette.write(level, press, out);
		}
		return out;
	}

	/** The store's count: with it the papers already written (this week's Gazettes; older ones are old news). */
	@Override
	protected Map<Item, Long> held(ServerLevel level, List<BlockPos> chests) {
		Map<Item, Long> out = super.held(level, chests);
		long today = Gazette.today(level);
		for (BlockPos chest : chests) {
			for (ItemStack s : SupplyContainers.peekMatching(level, chest, s -> Gazette.written(s)
				&& (s.is(ModItems.ILLUMINATED_BOOK) || s.is(ModItems.GAZETTE) && Gazette.thisWeek(s, today)))) {
				out.merge(s.getItem(), (long) s.getCount(), Long::sum);
			}
		}
		return out;
	}

	@Override
	protected void workEffects(ServerLevel level, BlockPos station) {
		pulls++;
		level.playSound(null, station, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), station.getX() + 0.5, station.getY() + 1.05,
			station.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0.02);
	}
}
