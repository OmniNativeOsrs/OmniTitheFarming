/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.tithefarming;

import java.util.List;
import omni.osrs.model.OmniObjectSnapshot;
import omni.osrs.plugin.OmniOverlayBuilder;
import omni.osrs.plugin.OmniOverlayStyle;

/**
 * Info panel for the Tithe Farm bot. State is pushed in by the plugin each tick.
 */
public final class TitheFarmingOverlay
{
	private static volatile String state = "-";
	private static volatile String fruits = "0";
	private static volatile String fruitsPerHour = "0";
	private static volatile List<OmniObjectSnapshot> patches = List.of();
	private static volatile List<String> debugLines = List.of();

	private TitheFarmingOverlay()
	{
	}

	/**
	 * Writes the panel's rows.
	 *
	 * @param overlay the client's overlay builder
	 */
	public static void render(OmniOverlayBuilder overlay)
	{
		overlay.line("Script State", state)
			.line("Fruit per hour", fruitsPerHour)
			.line("Fruits farmed", fruits)
			.mainSkill("FARMING");
		for (OmniObjectSnapshot patch : patches)
		{
			overlay.highlightObject(patch, OmniOverlayStyle.INFO);
		}
		for (String line : debugLines)
		{
			overlay.line(null, line);
		}
	}

	public static void setState(String value)
	{
		state = value;
	}

	public static void setFruits(String value)
	{
		fruits = value;
	}

	public static void setFruitsPerHour(String value)
	{
		fruitsPerHour = value;
	}

	public static void setPatches(List<OmniObjectSnapshot> value)
	{
		patches = List.copyOf(value);
	}

	public static void setDebugLines(List<String> value)
	{
		debugLines = List.copyOf(value);
	}

	/** Clears everything the plugin pushed, when it stops. */
	public static void clear()
	{
		state = "-";
		fruits = "0";
		fruitsPerHour = "0";
		patches = List.of();
		debugLines = List.of();
	}
}
