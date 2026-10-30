/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.tithefarming;

/**
 * The pair of farm lanes to plant, counted from the entrance.
 */
public enum TitheFarmLanes
{
	LANE_1_2("Lane one and two"),
	LANE_2_3("Lane two and three"),
	LANE_3_4("Lane three and four"),
	LANE_4_5("Lane four and five"),
	Randomize("Randomize");

	private final String label;

	TitheFarmLanes(String label)
	{
		this.label = label;
	}

	public String getName()
	{
		return label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
