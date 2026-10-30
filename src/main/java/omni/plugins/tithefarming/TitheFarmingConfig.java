/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.tithefarming;

import java.util.ArrayList;
import java.util.List;
import omni.osrs.plugin.OmniPluginSetting;
import omni.osrs.plugin.OmniPluginValues;

/**
 * The plugin's settings, read by the logic through the accessors ({@code config.x()}).
 */
public class TitheFarmingConfig
{
	private static final String SCRIPT = "Script Settings";
	private static final String DEBUG = "Debug Settings";

	private int storeFruitThreshold = 100;
	private TitheFarmLanes lanes = TitheFarmLanes.LANE_1_2;
	private int gricollerCanRefillThreshold = 30;
	private int sleepAfterPlantingSeed = 2000;
	private int sleepAfterWateringSeed = 2000;
	private int sleepAfterHarvestingSeed = 2000;
	private boolean enableDebugging;
	private boolean enableOverlay;

	/**
	 * The settings the client shows, one per config field.
	 *
	 * @return the settings
	 */
	public static List<OmniPluginSetting> settings()
	{
		List<OmniPluginSetting> settings = new ArrayList<>();
		settings.add(OmniPluginSetting.info("guide", "Guide",
			"Start at the entrance near the table to get seeds. Carry 8 Watering can(8) or the Gricoller's can, a "
				+ "seed dibber and a spade (stamina potions optional). Needs 34 Farming.").inGroup(SCRIPT));
		settings.add(OmniPluginSetting.number("storeFruitThreshold", "Store fruit threshold",
			"Amount of fruits to have in your inventory before storing them in the sack.", 100, 1, 10000)
			.inGroup(SCRIPT));
		settings.add(OmniPluginSetting.choice("lanes", "Tithe farm lanes",
			"Choose a lane starting from the entrance.", TitheFarmLanes.class, TitheFarmLanes.LANE_1_2)
			.inGroup(SCRIPT));
		settings.add(OmniPluginSetting.slider("gricollerCanRefillThreshold", "Gricoller's can refill threshold",
			"Percentage before refilling the Gricoller's can.", 30, 0, 100, 1, "%").inGroup(SCRIPT));
		settings.add(OmniPluginSetting.number("sleepAfterPlantingSeed", "Sleep after planting seed",
			"Longest wait (ms) for a planted seed to show up - changing this value might result in unexpected "
				+ "behavior.", 2000, 0, 10000).inGroup(SCRIPT));
		settings.add(OmniPluginSetting.number("sleepAfterWateringSeed", "Sleep after watering seed",
			"Longest wait (ms) for a watered plant to update - changing this value might result in unexpected "
				+ "behavior.", 2000, 0, 10000).inGroup(SCRIPT));
		settings.add(OmniPluginSetting.number("sleepAfterHarvestingSeed", "Sleep after harvesting seed",
			"Longest wait (ms) for a harvested patch to clear - changing this value might result in unexpected "
				+ "behavior.", 2000, 0, 10000).inGroup(SCRIPT));
		settings.add(OmniPluginSetting.toggle("enableDebugging", "Enable Debug",
			"Show each plant's decision flags in the overlay and log empty patches.", false).inGroup(DEBUG));
		settings.add(OmniPluginSetting.toggle("enableOverlay", "Enable Overlay",
			"Highlight the plant patches in the world.", false).inGroup(DEBUG));
		return settings;
	}

	/**
	 * A config filled from the plugin's settings.
	 *
	 * @param values the settings
	 * @return the config
	 */
	public static TitheFarmingConfig from(OmniPluginValues values)
	{
		TitheFarmingConfig config = new TitheFarmingConfig();
		config.load(values);
		return config;
	}

	/**
	 * Reads every setting present in {@code values} into this config (a live settings change).
	 *
	 * @param values the settings
	 */
	public void load(OmniPluginValues values)
	{
		storeFruitThreshold = integer(values, "storeFruitThreshold", storeFruitThreshold);
		if (values.contains("lanes"))
		{
			lanes = values.getEnum("lanes", TitheFarmLanes.class);
		}
		gricollerCanRefillThreshold = integer(values, "gricollerCanRefillThreshold", gricollerCanRefillThreshold);
		sleepAfterPlantingSeed = integer(values, "sleepAfterPlantingSeed", sleepAfterPlantingSeed);
		sleepAfterWateringSeed = integer(values, "sleepAfterWateringSeed", sleepAfterWateringSeed);
		sleepAfterHarvestingSeed = integer(values, "sleepAfterHarvestingSeed", sleepAfterHarvestingSeed);
		enableDebugging = values.contains("enableDebugging") ? values.getBoolean("enableDebugging") : enableDebugging;
		enableOverlay = values.contains("enableOverlay") ? values.getBoolean("enableOverlay") : enableOverlay;
	}

	private static int integer(OmniPluginValues values, String key, int fallback)
	{
		return values.contains(key) ? values.getInt(key) : fallback;
	}

	public int storeFruitThreshold()
	{
		return Math.max(1, storeFruitThreshold);
	}

	public TitheFarmLanes lanes()
	{
		return lanes;
	}

	public int gricollerCanRefillThreshold()
	{
		return gricollerCanRefillThreshold;
	}

	public int sleepAfterPlantingSeed()
	{
		return Math.max(0, sleepAfterPlantingSeed);
	}

	public int sleepAfterWateringSeed()
	{
		return Math.max(0, sleepAfterWateringSeed);
	}

	public int sleepAfterHarvestingSeed()
	{
		return Math.max(0, sleepAfterHarvestingSeed);
	}

	public boolean enableDebugging()
	{
		return enableDebugging;
	}

	public boolean enableOverlay()
	{
		return enableOverlay;
	}
}
