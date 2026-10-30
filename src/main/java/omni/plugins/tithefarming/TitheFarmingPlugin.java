/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.tithefarming;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import omni.osrs.action.OmniActionResult;
import omni.osrs.api.OmniContext;
import omni.osrs.api.OmniItem;
import omni.osrs.api.OmniLocalPlayer;
import omni.osrs.api.OmniObject;
import omni.osrs.api.OmniWidget;
import omni.osrs.event.OmniChatEvent;
import omni.osrs.event.OmniSubscribe;
import omni.osrs.model.OmniObjectSnapshot;
import omni.osrs.model.OmniSkill;
import omni.osrs.model.OmniTab;
import omni.osrs.model.OmniWorldPoint;
import omni.osrs.plugin.OmniOverlayBuilder;
import omni.osrs.plugin.OmniPlugin;
import omni.osrs.plugin.OmniPluginConfigChange;
import omni.osrs.plugin.OmniPluginRuntime;
import omni.osrs.plugin.OmniTickResult;

/**
 * Tithe Farm: takes seeds, enters the farm, plants, waters and harvests the chosen lanes, refills the watering cans,
 * stores the fruit in the sack and leaves when the seeds run out.
 *
 * <p>{@link #onTick()} runs once per game tick. Waits are tick counters or {@link OmniTickResult#waitUntil}, and a
 * world click is not sent again while the previous one is in flight. Breaks are scheduled centrally.
 */
public class TitheFarmingPlugin implements OmniPlugin
{
	private static final int FARM_DOOR = 27445;
	private static final int SEED_TABLE = 27430;
	private static final int FRUIT_SACK = 27431;
	private static final int WATER_BARREL = 5598;
	private static final int SEED_DIBBER = 5343;
	private static final int SPADE = 952;
	/** The Tithe Farm info widget, shown only inside the minigame. */
	private static final int MINIGAME_GROUP = 241;
	private static final int MINIGAME_COMPONENT = 2;
	private static final String FERTILISER = "gricoller's fertiliser";
	private static final int WATERING_CANS_AMOUNT = 8;
	private static final int DISTANCE_THRESHOLD_MINIMAP_WALK = 8;
	/** Objects further than this are walked to with the full pathfinder. */
	private static final int FAR_OBJECT_DISTANCE = 51;
	/** Run energy below which the bot rests, in hundredths of a percent. */
	private static final int LOW_RUN_ENERGY = 4000;
	/** Ticks to wait for the seed menu and amount prompt after clicking the seed table. */
	private static final int SEED_MENU_TICKS = 8;
	private static final String[] ENERGY_ITEMS = {"stamina potion", "super energy", "energy potion", "energy mix"};
	private static final Pattern PERCENT = Pattern.compile("(\\d+(\\.\\d+)?)%");

	private final OmniPluginRuntime runtime;
	private final OmniContext ctx;
	private final TitheFarmingConfig config;

	private List<TitheFarmPlant> plants = new ArrayList<>();
	private TitheFarmState state = TitheFarmState.STARTING;
	private TitheFarmMaterial activeSeed = TitheFarmMaterial.LOGAVANO_SEED;
	private boolean init = true;
	private boolean allPlanted;
	private boolean stopped;
	private volatile int gricollerCanCharges = -1;
	private int lastFruitCount = -1;
	private int fruits;
	private int pause;
	private int seedMenu;
	private String lastNote = "";
	private Instant startedAt = Instant.now();

	/** The last world click (door, patch, sack), so it is not sent again while the game catches up. */
	private CompletableFuture<OmniActionResult> inFlight;

	/**
	 * Starts a session, running at once.
	 *
	 * @param runtime the session's runtime
	 * @param config the config, already filled from the plugin's settings
	 */
	public TitheFarmingPlugin(OmniPluginRuntime runtime, TitheFarmingConfig config)
	{
		this.runtime = runtime;
		this.ctx = new OmniContext(runtime.game());
		this.config = config;
		runtime.log().info("Tithe farming script started");
		pushOverlay();
	}

	@Override
	public void close()
	{
		TitheFarmingOverlay.clear();
	}

	@Override
	public void reset()
	{
		// A login, logout or world hop: drop what was read from the old scene and start the run over.
		inFlight = null;
		init = true;
		pause = 0;
		seedMenu = 0;
		lastFruitCount = -1;
	}

	@Override
	public String status()
	{
		return stopped ? "Stopped: missing items" : state.name();
	}

	@Override
	public boolean onConfigChanged(OmniPluginConfigChange change)
	{
		config.load(change.getCurrent());
		return true;
	}

	@Override
	public void overlay(OmniOverlayBuilder overlay)
	{
		TitheFarmingOverlay.render(overlay);
	}

	// ==================================================================
	//  Chat
	// ==================================================================

	@OmniSubscribe
	void onChat(OmniChatEvent event)
	{
		String message = event.getPlainMessage();
		if (message == null)
		{
			return;
		}
		if (message.contains("%"))
		{
			Matcher matcher = PERCENT.matcher(message);
			if (matcher.find())
			{
				gricollerCanCharges = (int) Float.parseFloat(matcher.group(1));
			}
		}
		else if (message.equalsIgnoreCase("Gricoller's can is already full."))
		{
			gricollerCanCharges = 100;
		}
		else if (message.equalsIgnoreCase("You don't have a suitable vessel of water for watering the plant."))
		{
			enterRefill();
		}
	}

	// ==================================================================
	//  Main loop
	// ==================================================================

	@Override
	public OmniTickResult onTick()
	{
		OmniTickResult result = OmniTickResult.next();
		try
		{
			result = serverTick();
		}
		catch (RuntimeException failure)
		{
			runtime.log().warn("Tithe farming tick failed: {}", String.valueOf(failure.getMessage()));
		}
		pushOverlay();
		return result;
	}

	private OmniTickResult serverTick()
	{
		if (!ctx.game.isAutomationReady() || stopped)
		{
			return OmniTickResult.next();
		}
		OmniLocalPlayer player = ctx.localPlayer.current();
		if (player == null || ctx.walker.isWalking() || busy())
		{
			return OmniTickResult.next();
		}
		if (pause > 0)
		{
			pause--;
			return OmniTickResult.next();
		}
		TitheFarmMaterial seed = TitheFarmMaterial.forLevel(ctx.skills.level(OmniSkill.FARMING));
		activeSeed = seed;
		trackFruit(seed);
		if (init)
		{
			state = TitheFarmState.STARTING;
			plants = new ArrayList<>();
			allPlanted = false;
			lastFruitCount = -1;
			fruits = 0;
			startedAt = Instant.now();
			trackFruit(seed);
			init = false;
			pause = 3;
			return OmniTickResult.next();
		}
		if (handleDialogue(seed))
		{
			return OmniTickResult.next();
		}

		boolean inMinigame = inMinigame();
		boolean hasSeed = ctx.inventory.contains(seed.getName());
		if (!inMinigame && state != TitheFarmState.TAKE_SEEDS)
		{
			// Outside the farm: get seeds, or go in with the ones held.
			state = TitheFarmState.TAKE_SEEDS;
		}
		if (!hasSeed && inMinigame)
		{
			state = TitheFarmState.LEAVE;
		}
		if (ctx.inventory.count(seed.getFruitId()) >= config.storeFruitThreshold())
		{
			depositSack(player, seed);
			return OmniTickResult.next();
		}

		OmniTickResult result = OmniTickResult.next();
		switch (state)
		{
			case LEAVE:
				if (!depositSack(player, seed))
				{
					result = leave();
				}
				break;
			case TAKE_SEEDS:
				result = takeSeeds(seed, inMinigame, hasSeed);
				break;
			case STARTING:
				starting();
				break;
			case RECHARING_RUN_ENERGY:
				validateRunEnergy();
				break;
			case REFILL_WATERCANS:
				result = refillWaterCans(player);
				break;
			case PLANTING_SEEDS:
			case HARVEST:
				result = coreLoop(player, seed);
				break;
			default:
				break;
		}
		if (config.enableDebugging() && plants.stream().anyMatch(plant -> plant.getGameObject() == null))
		{
			note("There is an empty plant gameobject!");
		}
		return result;
	}

	// ==================================================================
	//  States
	// ==================================================================

	private void starting()
	{
		if (!ctx.movement.isRunEnabled())
		{
			send(ctx.movement.setRunEnabled(true));
		}
		if (!ctx.tabs.isOpen(OmniTab.INVENTORY))
		{
			send(ctx.tabs.open(OmniTab.INVENTORY));
		}
		TitheFarmLanes lane = TitheFarmLayouts.resolve(config.lanes(), ThreadLocalRandom.current().nextInt(4));
		plants = TitheFarmLayouts.plants(lane);
		refreshPlants();
		if (!validateInventory())
		{
			return;
		}
		OmniItem fertiliser = ctx.inventory.query().withName(FERTILISER).first();
		if (fertiliser != null)
		{
			send(fertiliser.drop());
		}
		validateRunEnergy();
		if (state != TitheFarmState.RECHARING_RUN_ENERGY)
		{
			enterRefill();
		}
	}

	private boolean validateInventory()
	{
		boolean tools = ctx.inventory.contains(SEED_DIBBER) && ctx.inventory.contains(SPADE);
		if (!tools)
		{
			stop("You need a seed dibber and a spade in your inventory!");
			return false;
		}
		long cans = ctx.inventory.query().where(item -> startsWith(item.getName(), "watering can")).list().stream()
			.mapToLong(OmniItem::getQuantity).sum();
		if (cans < WATERING_CANS_AMOUNT && !ctx.inventory.contains(TitheFarmMaterial.GRICOLLERS_CAN))
		{
			stop("You need at least 8 watering can(8) or a Gricoller's can!");
			return false;
		}
		return true;
	}

	private void validateRunEnergy()
	{
		if (ctx.movement.runEnergy() < LOW_RUN_ENERGY && hasAllEmptyPatches()
			&& state != TitheFarmState.RECHARING_RUN_ENERGY)
		{
			state = TitheFarmState.RECHARING_RUN_ENERGY;
			runtime.log().info("Recharging run energy...");
			useRestoreEnergyItem();
		}
		else if (state == TitheFarmState.RECHARING_RUN_ENERGY && ctx.movement.runEnergy() >= LOW_RUN_ENERGY)
		{
			state = TitheFarmState.STARTING;
		}
	}

	private void useRestoreEnergyItem()
	{
		OmniItem potion = ctx.inventory.query()
			.where(item -> startsWithAny(item.getName(), ENERGY_ITEMS) && item.hasAction("Drink")).first();
		if (potion != null)
		{
			send(potion.drink());
		}
	}

	private OmniTickResult takeSeeds(TitheFarmMaterial seed, boolean inMinigame, boolean hasSeed)
	{
		if (inMinigame)
		{
			state = TitheFarmState.STARTING;
			return OmniTickResult.next();
		}
		if (hasSeed)
		{
			// Seeds already held are kept and taken into the farm.
			seedMenu = 0;
			if (interactObject(FARM_DOOR))
			{
				return OmniTickResult.waitUntil(this::inMinigame, 10);
			}
			return OmniTickResult.next();
		}
		if (seedMenu > 0)
		{
			// The seed menu or the amount prompt is on its way and handleDialogue answers it.
			seedMenu--;
			return OmniTickResult.next();
		}
		if (interactObject(SEED_TABLE))
		{
			seedMenu = SEED_MENU_TICKS;
		}
		return OmniTickResult.next();
	}

	private OmniTickResult refillWaterCans(OmniLocalPlayer player)
	{
		if (player.isMoving() || player.isAnimating())
		{
			return OmniTickResult.next();
		}
		if (ctx.inventory.contains(TitheFarmMaterial.GRICOLLERS_CAN))
		{
			OmniItem can = ctx.inventory.query().withId(TitheFarmMaterial.GRICOLLERS_CAN).first();
			if (gricollerCanCharges == -1)
			{
				// Ask the can; onChat reads the percentage from its message.
				if (can != null)
				{
					send(can.interact("Check"));
				}
				return OmniTickResult.waitUntil(() -> gricollerCanCharges != -1, 4);
			}
			if (gricollerCanCharges >= config.gricollerCanRefillThreshold())
			{
				state = TitheFarmState.PLANTING_SEEDS;
				return OmniTickResult.next();
			}
			OmniObject barrel = waterBarrel();
			if (can != null && barrel != null && nearBarrel(barrel, player))
			{
				send(can.useOn(barrel));
				gricollerCanCharges = -1;
				pause = 3;
			}
			return OmniTickResult.next();
		}
		OmniItem toFill = firstHeld(TitheFarmMaterial.CANS_TO_FILL);
		if (toFill == null)
		{
			state = TitheFarmState.PLANTING_SEEDS;
			return OmniTickResult.next();
		}
		OmniObject barrel = waterBarrel();
		if (barrel != null && nearBarrel(barrel, player))
		{
			send(toFill.useOn(barrel));
			return OmniTickResult.waitUntil(
				() -> ctx.inventory.count(TitheFarmMaterial.FULL_WATERING_CAN) >= WATERING_CANS_AMOUNT, 100);
		}
		return OmniTickResult.next();
	}

	private OmniObject waterBarrel()
	{
		OmniObject barrel = ctx.objects.query().withId(WATER_BARREL).nearest();
		return barrel != null ? barrel : ctx.objects.query().withName("Water barrel").nearest();
	}

	/** Walks towards the barrel until it is close; true once it is. */
	private boolean nearBarrel(OmniObject barrel, OmniLocalPlayer player)
	{
		OmniWorldPoint barrelLoc = barrel.getLocation();
		if (barrelLoc.distanceTo(player.getLocation()) > DISTANCE_THRESHOLD_MINIMAP_WALK)
		{
			send(ctx.movement.stepTowards(barrelLoc, 1));
			return false;
		}
		return true;
	}

	private OmniTickResult coreLoop(OmniLocalPlayer player, TitheFarmMaterial seed)
	{
		if (player.isMoving() || player.isAnimating())
		{
			return OmniTickResult.next();
		}
		refreshPlants();
		TitheFarmPlant plant = null;
		if (state != TitheFarmState.HARVEST)
		{
			if (!allPlanted)
			{
				plant = firstPlant(p -> p.isEmptyPatchOrSeedling(state));
			}
			if (plant == null)
			{
				plant = firstPlant(TitheFarmPlant::isStage1);
				if (plant == null)
				{
					plant = firstPlant(TitheFarmPlant::isStage2);
				}
			}
		}
		if (state == TitheFarmState.HARVEST && hasAllEmptyPatches())
		{
			state = TitheFarmState.STARTING;
			allPlanted = false;
		}
		if (!plants.isEmpty() && plants.stream().allMatch(p -> p.getGameObject() != null)
			&& plants.stream().noneMatch(TitheFarmPlant::isEmptyPatch))
		{
			allPlanted = true;
		}
		if (plant == null && plants.stream().anyMatch(p -> p.isValidToHarvest(seed)))
		{
			state = TitheFarmState.HARVEST;
			plant = firstPlant(p -> p.isValidToHarvest(seed));
		}
		if (plant == null)
		{
			return OmniTickResult.next();
		}

		OmniWorldPoint here = player.getLocation();
		OmniObject patch = plant.getGameObject();
		if (patch == null || patch.getLocation().distanceTo(here) > DISTANCE_THRESHOLD_MINIMAP_WALK)
		{
			OmniWorldPoint target = OmniWorldPoint.fromRegion(here.getRegionId(), plant.getRegionX(),
				plant.getRegionY(), here.getPlane());
			send(ctx.movement.stepTowards(target, 1));
			return OmniTickResult.next();
		}

		TitheFarmPlant current = plant;
		if (plant.isEmptyPatch() && !allPlanted)
		{
			OmniItem seeds = ctx.inventory.query().withName(seed.getName()).first();
			if (seeds != null)
			{
				send(seeds.useOn(patch));
				// The seedling then needs water; the next tick picks it up as "valid to water".
				return OmniTickResult.waitUntil(() -> watch(current, p -> !p.isEmptyPatch()),
					ticks(config.sleepAfterPlantingSeed()));
			}
		}
		else if (plant.isValidToWater(seed, state))
		{
			send(patch.interact("Water"));
			return OmniTickResult.waitUntil(() -> watch(current, TitheFarmPlant::isWatered),
				ticks(config.sleepAfterWateringSeed()));
		}
		else if (plant.isValidToHarvest(seed))
		{
			send(patch.interact("Harvest"));
			return OmniTickResult.waitUntil(() -> watch(current, TitheFarmPlant::isEmptyPatch),
				ticks(config.sleepAfterHarvestingSeed()));
		}
		return OmniTickResult.next();
	}

	// ==================================================================
	//  Sack, doors and dialogue
	// ==================================================================

	/** Stores the fruit in the sack; true while there was fruit to store (the tick is spent on it). */
	private boolean depositSack(OmniLocalPlayer player, TitheFarmMaterial seed)
	{
		if (!ctx.inventory.contains(seed.getFruitId()))
		{
			return false;
		}
		note("Storing fruits into sack for experience...");
		OmniObject sack = ctx.objects.query().withId(FRUIT_SACK).nearest();
		if (sack == null)
		{
			note("Sack not found in scene");
			return true;
		}
		if (player.isMoving() || player.isAnimating())
		{
			return true;
		}
		OmniWorldPoint sackLoc = sack.getLocation();
		if (sackLoc.distanceTo(player.getLocation()) > DISTANCE_THRESHOLD_MINIMAP_WALK)
		{
			send(ctx.movement.stepTowards(sackLoc, 1));
			return true;
		}
		String action = defaultAction(sack);
		if (action != null)
		{
			send(sack.interact(action));
			pause = 2;
		}
		return true;
	}

	private OmniTickResult leave()
	{
		if (interactObject(FARM_DOOR))
		{
			return OmniTickResult.waitUntil(() -> !ctx.inventory.contains(FERTILISER), 13);
		}
		return OmniTickResult.next();
	}

	/**
	 * Clicks through dialogue. While the seed table is open it picks the seed and enters a random amount; any other
	 * option menu gets option 3.
	 *
	 * @return whether the tick was spent on a dialogue
	 */
	private boolean handleDialogue(TitheFarmMaterial seed)
	{
		if (seedMenu > 0)
		{
			if (ctx.input.isNumberOpen())
			{
				send(ctx.input.enterNumber(ThreadLocalRandom.current().nextInt(1000, 10001)));
				seedMenu = 4;
				return true;
			}
			Optional<String> option = ctx.dialogue.findOptionContaining(seed.getName());
			if (option.isPresent())
			{
				send(ctx.dialogue.select(option.get()));
				return true;
			}
		}
		if (ctx.dialogue.canContinue())
		{
			send(ctx.dialogue.clickContinue());
			return true;
		}
		List<OmniWidget> options = ctx.dialogue.options();
		if (options.size() >= 3)
		{
			send(ctx.dialogue.select(options.get(2).getText()));
			return true;
		}
		return false;
	}

	/** Clicks the object's first real action or walks to it when it is far away. */
	private boolean interactObject(int id)
	{
		OmniObject object = ctx.objects.query().withId(id).nearest();
		if (object == null)
		{
			note("Object id " + id + " not in scene");
			return false;
		}
		OmniLocalPlayer player = ctx.localPlayer.current();
		if (player != null && object.getLocation().distanceTo(player.getLocation()) > FAR_OBJECT_DISTANCE)
		{
			note("Object id " + id + " is far away, walking...");
			ctx.walker.walkAsync(object.getLocation(), 1);
			return false;
		}
		String action = defaultAction(object);
		if (action == null)
		{
			return false;
		}
		send(object.interact(action));
		return true;
	}

	private static String defaultAction(OmniObject object)
	{
		for (String action : object.getActions())
		{
			if (action != null && !action.isBlank() && !action.equalsIgnoreCase("Examine"))
			{
				return action;
			}
		}
		return null;
	}

	// ==================================================================
	//  Helpers
	// ==================================================================

	private void enterRefill()
	{
		gricollerCanCharges = -1;
		state = TitheFarmState.REFILL_WATERCANS;
	}

	private boolean inMinigame()
	{
		return ctx.widgets.isVisible(MINIGAME_GROUP, MINIGAME_COMPONENT);
	}

	private boolean hasAllEmptyPatches()
	{
		return plants.stream().allMatch(TitheFarmPlant::isEmptyPatch);
	}

	/** Re-reads each plant's patch object from the scene. */
	private void refreshPlants()
	{
		List<OmniObject> patches = ctx.objects.query().where(o -> TitheFarmPatches.isPatchId(o.getId())).list();
		for (TitheFarmPlant plant : plants)
		{
			OmniObject found = null;
			for (OmniObject patch : patches)
			{
				if (plant.covers(patch.getLocation()))
				{
					found = patch;
					break;
				}
			}
			plant.setGameObject(found);
		}
	}

	/** Refreshes the scene and tests one plant, for a {@link OmniTickResult#waitUntil} condition. */
	private boolean watch(TitheFarmPlant plant, Predicate<TitheFarmPlant> test)
	{
		refreshPlants();
		return test.test(plant);
	}

	private TitheFarmPlant firstPlant(Predicate<TitheFarmPlant> test)
	{
		for (TitheFarmPlant plant : plants)
		{
			if (test.test(plant))
			{
				return plant;
			}
		}
		return null;
	}

	private OmniItem firstHeld(int[] ids)
	{
		for (int id : ids)
		{
			OmniItem item = ctx.inventory.query().withId(id).first();
			if (item != null)
			{
				return item;
			}
		}
		return null;
	}

	/** Counts fruit gained since the run started (fruit stored in the sack is not subtracted). */
	private void trackFruit(TitheFarmMaterial seed)
	{
		int now = (int) ctx.inventory.count(seed.getFruitId());
		if (lastFruitCount >= 0 && now > lastFruitCount)
		{
			fruits += now - lastFruitCount;
		}
		lastFruitCount = now;
	}

	private static int ticks(int millis)
	{
		return Math.max(1, (millis + 599) / 600);
	}

	private static boolean startsWith(String name, String prefix)
	{
		return name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix);
	}

	private static boolean startsWithAny(String name, String... prefixes)
	{
		for (String prefix : prefixes)
		{
			if (startsWith(name, prefix))
			{
				return true;
			}
		}
		return false;
	}

	private void stop(String reason)
	{
		stopped = true;
		runtime.log().warn("Tithe farming stopped: {}", reason);
	}

	private void note(String message)
	{
		if (!message.equals(lastNote))
		{
			lastNote = message;
			runtime.log().info("{}", message);
		}
	}

	private boolean busy()
	{
		return inFlight != null && !inFlight.isDone();
	}

	private void send(CompletableFuture<OmniActionResult> action)
	{
		inFlight = action;
	}

	private void pushOverlay()
	{
		TitheFarmingOverlay.setState(state.toString());
		TitheFarmingOverlay.setFruits(String.valueOf(fruits));
		long seconds = Math.max(1, Duration.between(startedAt, Instant.now()).getSeconds());
		TitheFarmingOverlay.setFruitsPerHour(String.valueOf(fruits * 3600L / seconds));

		List<OmniObjectSnapshot> highlights = new ArrayList<>();
		List<String> debug = new ArrayList<>();
		for (TitheFarmPlant plant : plants)
		{
			OmniObject object = plant.getGameObject();
			if (object == null)
			{
				continue;
			}
			if (config.enableOverlay())
			{
				highlights.add(object.snapshot());
			}
			if (config.enableDebugging())
			{
				debug.add("plant " + plant.getIndex() + ": p=" + plant.isEmptyPatchOrSeedling(state) + " h="
					+ plant.isValidToHarvest(activeSeed) + " w=" + plant.isValidToWater(activeSeed, state));
			}
		}
		TitheFarmingOverlay.setPatches(highlights);
		TitheFarmingOverlay.setDebugLines(debug);
	}
}
