/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.tithefarming;

import java.util.ArrayList;
import java.util.List;

/**
 * The plant tiles (region x, region y, planting order) of each lane pair.
 */
public final class TitheFarmLayouts
{
	private static final int[][] LANE_1_2 = {
		{35, 25, 15}, {40, 25, 16}, {35, 28, 17}, {40, 28, 18}, {35, 31, 19}, {40, 31, 20}, {35, 34, 1}, {40, 34, 2},
		{35, 40, 3}, {40, 40, 4}, {35, 43, 5}, {40, 43, 6}, {35, 46, 7}, {40, 46, 8}, {35, 49, 9}, {40, 49, 10},
		{45, 49, 11}, {45, 46, 12}, {45, 43, 13}, {45, 40, 14}};
	private static final int[][] LANE_2_3 = {
		{35, 31, -2}, {35, 28, -1}, {35, 25, 0}, {40, 25, 1}, {45, 25, 2}, {40, 28, 3}, {45, 28, 4}, {40, 31, 5},
		{45, 31, 6}, {40, 34, 7}, {45, 34, 8}, {40, 40, 9}, {45, 40, 10}, {40, 43, 11}, {45, 43, 12}, {40, 46, 13},
		{45, 46, 14}, {40, 49, 15}, {45, 49, 16}};
	private static final int[][] LANE_3_4 = {
		{40, 31, -2}, {40, 28, -1}, {40, 25, 0}, {45, 25, 1}, {50, 25, 2}, {45, 28, 3}, {50, 28, 4}, {45, 31, 5},
		{50, 31, 6}, {45, 34, 7}, {50, 34, 8}, {45, 40, 9}, {50, 40, 10}, {45, 43, 11}, {50, 43, 12}, {45, 46, 13},
		{50, 46, 14}, {45, 49, 15}, {50, 49, 16}};
	private static final int[][] LANE_4_5 = {
		{45, 31, 0}, {45, 28, 1}, {45, 25, 2}, {50, 25, 3}, {55, 25, 4}, {50, 28, 5}, {55, 28, 6}, {50, 31, 7},
		{55, 31, 8}, {50, 34, 9}, {55, 34, 10}, {50, 40, 11}, {55, 40, 12}, {50, 43, 13}, {55, 43, 14}, {50, 46, 15},
		{55, 46, 16}, {50, 49, 17}, {55, 49, 18}};

	private TitheFarmLayouts()
	{
	}

	/**
	 * The plants of a lane pair, sorted by planting order.
	 *
	 * @param lane a concrete lane pair (not {@link TitheFarmLanes#Randomize}, which has no plants)
	 * @return a new list of plants
	 */
	public static List<TitheFarmPlant> plants(TitheFarmLanes lane)
	{
		int[][] rows;
		switch (lane)
		{
			case LANE_1_2:
				rows = LANE_1_2;
				break;
			case LANE_2_3:
				rows = LANE_2_3;
				break;
			case LANE_3_4:
				rows = LANE_3_4;
				break;
			case LANE_4_5:
				rows = LANE_4_5;
				break;
			default:
				rows = new int[0][];
				break;
		}
		List<TitheFarmPlant> plants = new ArrayList<>();
		for (int[] row : rows)
		{
			plants.add(new TitheFarmPlant(row[0], row[1], row[2]));
		}
		plants.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
		return plants;
	}

	/**
	 * Picks a concrete lane pair.
	 *
	 * @param chosen the configured lane
	 * @param roll a value from 0 (inclusive) to 4 (exclusive), used only for {@link TitheFarmLanes#Randomize}
	 * @return the lane pair to plant
	 */
	public static TitheFarmLanes resolve(TitheFarmLanes chosen, int roll)
	{
		if (chosen != TitheFarmLanes.Randomize)
		{
			return chosen;
		}
		return TitheFarmLanes.values()[Math.floorMod(roll, TitheFarmLanes.values().length - 1)];
	}
}
