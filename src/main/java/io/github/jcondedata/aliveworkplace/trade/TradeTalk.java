package io.github.jcondedata.aliveworkplace.trade;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * What villagers say about their village's trade (ROADMAP 33.4; the lines are {@code people/Chatter}'s): a good the
 * village is known for that fetches more in a village on its routes ({@link #SELLS_WELL}, "Our Timber sells well in
 * Ashford"), a good it's known for that's cheaper at home than its base price ({@link #GLUT}, "We've more Wool than we
 * can use"), and a good that's dear ({@link #DEAR}: one it's short of that costs more than its base, or any at a fifth
 * over it). Nothing with the economy off or before the hall's first count.
 */
public final class TradeTalk {
	public static final String SELLS_WELL = "trade_sells_well";
	public static final String GLUT = "trade_glut";
	public static final String DEAR = "trade_dear";

	/** A topic and what its lines name: the good, and for {@link #SELLS_WELL} the other village. */
	public record Line(String topic, Object[] args) {
	}

	/** Whether {@code topic} is one of these. */
	public static boolean is(String topic) {
		return topic.equals(SELLS_WELL) || topic.equals(GLUT) || topic.equals(DEAR);
	}

	/** What there is to say in the village round {@code hall} today, a line for each topic that holds. */
	public static List<Line> lines(ServerLevel level, BlockPos hall) {
		List<Line> out = new ArrayList<>();
		if (!Economy.ENABLED) {
			return out;
		}
		Caravans.Data data = Caravans.Data.get(level);
		Market market = data.market(hall);
		if (market.prices().isEmpty()) {
			return out;
		}
		TradeGoods.Good sells = null;
		TradePage.Partner where = null;
		int gain = 0;
		TradeGoods.Good glut = null;
		double cheapness = 1;
		for (ResourceLocation id : market.knownFor()) {
			TradeGoods.Good good = TradeGoods.get(id);
			Market.Price price = market.prices().get(id);
			if (good == null || price == null) {
				continue;
			}
			for (BlockPos other : data.partners(hall)) {
				TradePage.Partner p = TradePage.partner(data, other, id);
				if (p != null && p.cents() - price.cents() > gain) {
					gain = p.cents() - price.cents();
					sells = good;
					where = p;
				}
			}
			double ratio = price.cents() / (double) good.basePrice();
			if (ratio < cheapness) {
				cheapness = ratio;
				glut = good;
			}
		}
		if (sells != null) {
			out.add(new Line(SELLS_WELL, new Object[]{sells.name(), where.name()}));
		}
		if (glut != null) {
			out.add(new Line(GLUT, new Object[]{glut.name()}));
		}
		TradeGoods.Good dear = null;
		double dearness = 1;
		for (TradeGoods.Good good : TradeGoods.all()) {
			Market.Price price = market.prices().get(good.id());
			if (price == null) {
				continue;
			}
			double ratio = price.cents() / (double) good.basePrice();
			if (ratio > dearness && (ratio >= 1.2 || market.shortOf().contains(good.id()))) {
				dearness = ratio;
				dear = good;
			}
		}
		if (dear != null) {
			out.add(new Line(DEAR, new Object[]{dear.name()}));
		}
		return out;
	}

	/** {@code topic}'s line for the village today, or null when it doesn't hold. */
	@Nullable
	public static Line line(ServerLevel level, BlockPos hall, String topic) {
		for (Line line : lines(level, hall)) {
			if (line.topic().equals(topic)) {
				return line;
			}
		}
		return null;
	}

	private TradeTalk() {
	}
}
