package CastleSummerfall;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Objects;
import java.util.Random;

/**
 * @author @Corbanator this is a grouping of methods to randomly generate any
 *         given game object. A room, an interactable, a floor, an enemy, etc.
 *         It loads the options in from json files contained in the file
 *         specified by pathPath
 */
public class Generator {

    // This stores the location of the file which stores the location of all of
    // the other files, which allows for easily loading new files. Important for
    // easily adding mod support and just easily modifying the file structure.
    private static String pathPath = "../data/config/paths.json"; // TODO: make this work for binaries @yomas000

    // stores the boss being generated by the generator when it's generating a
    // floor.
    private static NPC boss;

    private static String readFile(String path) {
        // TODO: batch processing
        try {
            File file = new File(path);
            FileReader reader = new FileReader(file);
            int i;
            String data = "";
            while ((i = reader.read()) != -1) {
                data += (char) i;
            }
            reader.close();
            return data;
        } catch (Exception E) {
            return null;
        }
    }

    /**
     * This will generate a floor based on the size desired.
     *
     * @param xSize
     * @param ySize
     * @return Floor
     */
    public static Floor generateFloor(int xSize, int ySize) {

        Random rand = new Random();
        List<List<Room>> rooms = new ArrayList<>();

        // choose where the boss will be.
        int xBoss = rand.nextInt(xSize);
        int yBoss = rand.nextInt(ySize);

        // nested for loops to populate rows and columns/
        for (int i = 0; i < xSize; i++) {
            List<Room> column = new ArrayList<>();
            for (int j = 0; j < ySize; j++) {
                // determine if room is not on edge of map
                boolean southDoor = j != 0;
                boolean eastDoor = i != xSize - 1;

                column.add(generateRoom(southDoor, eastDoor, i == xBoss && j == yBoss));
            }
            rooms.add(column);
        }

        Floor result = new Floor(rooms);

        // choose how many enemies to add to the floor.
        int enemyFactor = (xSize * ySize) / 4;
        int enemyCount = rand.nextInt(enemyFactor) + enemyFactor;

        // add the enemies
        for (int i = 0; i < enemyCount; i++) {
            int x = rand.nextInt(xSize);
            int y = rand.nextInt(ySize);
            if (!(x == xBoss && y == yBoss)) {
                result.addNPC(generateEnemy(x, y, 0));
            }
        }

        // if there was a boss generated, set the boss' coordinates and then add it
        if (Objects.nonNull(boss)) {
            boss.setXCoord(xBoss);
            boss.setYCoord(yBoss);
            result.addNPC(boss);
        }

        return result;
    }

    /**
     * This will generate a random Interactable to go in a room
     *
     * @param containerWeight what percentage of interactables should be containers
     * @return Interactable
     */
    // below this is all the interactable generation.
    public static Interactable generateInteractable(double containerWeight) {
        Random rand = new Random();
        Interactable result;
        double randNum = rand.nextDouble();

        String pathString = readFile(pathPath);

        if (Objects.nonNull(pathString)) {
            // almost the same code twice, but one is for containers and the other for
            // normal interactables

            List<InteractablePreset> interactables = new ArrayList<>();
            if (randNum < containerWeight) {
                // get the filepath to the containers
                String[] containerPaths = Parser.trimQuotes(Parser.parseArray("containers", pathString));

                // for each file, add the string for each container to the array of all
                // containers
                for (String path : containerPaths) {
                    interactables.addAll(Arrays.asList(Parser.parseArray("containers", readFile(path))).stream()
                            .map(n -> (ContainerPreset) PresetLoader.loadInteractablePreset(n))
                            .collect(Collectors.toList()));
                }
            } else {
                String[] interactablePaths = Parser.trimQuotes(Parser.parseArray("interactables", pathString));

                for (String path : interactablePaths) {
                    interactables.addAll(Arrays.asList(Parser.parseArray("interactables", readFile(path))).stream()
                            .map(PresetLoader::loadInteractablePreset).collect(Collectors.toList()));
                }
            }

            // add up the rarity of all items, then get a random number between 0
            // and that sum. this gets a random item, but weights it toward items
            // with higher rarities
            int choice = rand.nextInt(interactables.stream().map(n -> n.rarity).reduce(0, Integer::sum) + 1);
            for (InteractablePreset preset : interactables) {
                choice -= preset.rarity;
                if (choice <= 0) {
                    result = spinInteractable(preset);
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * takes in a preset, spins it up, then gives it the proper doors and
     * interactables, as well as returning either a boss or not a boos based on the
     * boss parameter.
     *
     * @param preset
     * @param interactableMin
     * @param interactableMax
     * @param southDoor
     * @param eastDoor
     * @param boss
     * @return Room
     */
    public static Room generateRoom(RoomPreset preset, int interactableMin, int interactableMax, boolean southDoor,
            boolean eastDoor, boolean boss) {

        // choose how many interactables to generate in the room.
        int range = interactableMax - interactableMin;
        Random rand = new Random();
        int loopCount;
        if (interactableMax > interactableMin) {
            loopCount = rand.nextInt(range) + interactableMin;
        } else {
            loopCount = interactableMin;
        }

        // spin the room from the preset
        Room result = spinRoom(preset, southDoor, eastDoor);

        // if it's not a boss room, add the room inventory. (Boss rooms don't have
        // interactables in them.)
        if (!boss) {
            for (int i = 0; i < loopCount; i++) {
                result.addItem(generateInteractable());
            }
        }

        return result;
    }

    /**
     * Generates a room with the default values for number of interactables and by
     * loading in all possible presets.
     *
     * @overload
     * @param southDoor
     * @param eastDoor
     * @param boss
     * @return Room
     */
    public static Room generateRoom(boolean southDoor, boolean eastDoor, boolean boss) { // read all the filepaths
        // get the paths to the room preset files
        String[] files = Parser.trimQuotes(Parser.parseArray("room-presets", readFile(pathPath)));

        // two arraylists, one for boss rooms, so that it can only make boss rooms
        // when told to.
        List<RoomPreset> presets = new ArrayList<>();
        List<RoomPreset> bossPresets = new ArrayList<>();

        // for each file, read what's in it and load all the roomPresets
        for (String file : files) {
            List<RoomPreset> toAdd = PresetLoader.loadRoomPresets(readFile(file));
            presets.addAll(toAdd.stream().filter(n -> Objects.isNull(n.boss)).collect(Collectors.toList()));
            bossPresets.addAll(toAdd.stream().filter(n -> Objects.nonNull(n.boss)).collect(Collectors.toList()));
        }

        // chooses a roompreset from the appropriate arraylist, then spins it up
        // with the default item sets
        Random rand = new Random();
        int choice;
        Room result;
        if (boss) {
            choice = bossPresets.size() > 1 ? rand.nextInt(bossPresets.size()) : 0;
            result = generateRoom(bossPresets.get(choice), 0, 0, southDoor, eastDoor, boss);
            // generate the stairs at the time of boss creation
            result.makeStairs();
        } else {
            result = generateRoom(presets.get(rand.nextInt(presets.size())), 1, 3, southDoor, eastDoor, boss);
        }

        return result;
    }

    /**
     * Makes a room based on a preset, but doesn't add any interactables to it.
     *
     * @param preset
     * @param southDoor
     * @param eastDoor
     * @return Room
     */
    public static Room spinRoom(RoomPreset preset, boolean southDoor, boolean eastDoor) {

        Random rand = new Random();

        // add all of the preset's normal interactables to the inventory
        List<Interactable> interactables = preset.interactables.stream().map(n -> spinInteractable(n))
                .collect(Collectors.toList());

        // choose one of the possible descriptions at random
        String description = preset.descriptions[rand.nextInt(preset.descriptions.length)];

        // add all of the description interactables to the descriptionInteractables
        List<Interactable> descriptionInteractables = preset.descriptionInteractables.stream()
                .map(n -> spinInteractable(n)).collect(Collectors.toList());

        // add doors
        Door doorSouth = southDoor ? new Door(true, false, false) : null;
        Door doorEast = eastDoor ? new Door(true, false, false) : null;

        // add the boss
        if (!Objects.isNull(preset.boss)) {
            boss = preset.boss;
        }

        // actually make the room
        return new Room(interactables, descriptionInteractables, description, doorSouth, doorEast);
    }

    /**
     * This generate a default Interactable with the default weight of 20% container
     * chance
     *
     * @return Interactable
     */
    public static Interactable generateInteractable() {
        return generateInteractable(.2);
    }

    /**
     * Make an actual interactable from a preset
     *
     * @param preset
     * @return Interactable
     */
    public static Interactable spinInteractable(InteractablePreset preset) {
        // return null if the preset is null, to avoid errors
        if (Objects.isNull(preset)) {
            return null;
        }

        Random rand = new Random();

        // don't return anything unless it at least has a description
        if (Objects.isNull(preset.descriptions)) {
            return null;
        }

        // choose a random description
        String description = preset.descriptions[rand.nextInt(preset.descriptions.length)];

        // add abilities, for future extensibility
        // randomly pick correct number of abilities
        List<Ability> abilities = preset.abilityOptions.stream().map(n -> {
            Collections.shuffle(n.options);
            return n.options.subList(0, n.number);
        }).flatMap(n -> n.stream()).collect(Collectors.toList());

        // if it's a container or weapon, do additional stuff.
        if (preset instanceof ContainerPreset) {
            return spinContainer((ContainerPreset) preset);
        }
        if (preset instanceof WeaponPreset) {
            return spinWeapon((WeaponPreset) preset);
        }
        return new Interactable(preset.name, description, preset.size, preset.weight, preset.canBePickedUp, abilities);
    }

    /**
     * Makes a container based on a containerPreset
     *
     * @param preset
     * @return Container
     */
    public static Container spinContainer(ContainerPreset preset) {

        Random rand = new Random();

        // choose a random description
        String description = preset.descriptions[rand.nextInt(preset.descriptions.length)];

        // add an inventory
        List<Interactable> inventory = new ArrayList<>();
        int loopCount = rand.nextInt(preset.maxItems - preset.minItems) + preset.minItems;
        for (int i = 0; i < loopCount; i++) {
            inventory.add(generateInteractable());
        }

        return new Container(preset.name, description, preset.size, preset.weight, preset.canBePickedUp, inventory,
                preset.inventorySize);
    }

    /**
     * Return a weapon based on a weaponPreset
     *
     * @param preset
     * @return Weapon
     */
    public static Weapon spinWeapon(WeaponPreset preset) {
        Random rand = new Random();
        String description = preset.descriptions[rand.nextInt(preset.descriptions.length)];

        // each section that looks like this is choosing either a single value or a
        // value from a range, depending on if there's a range.
        int pierce = preset.pierceRange == 0 ? preset.pierce : rand.nextInt(preset.pierceRange) + preset.pierce;

        // minimum damage
        int damage = preset.damageRange == 0 ? preset.damage : rand.nextInt(preset.damageRange) + preset.damage;

        // range in which damage can be
        int range = preset.rangeRange <= 0 ? preset.range : rand.nextInt(preset.rangeRange) + preset.range;

        return new Weapon(preset.size, preset.weight, preset.canBePickedUp, preset.name, description, pierce, damage,
                range);
    }

    /**
     * Make an NPC from a preset at the coordinates given.
     *
     * @param xCoord
     * @param yCoord
     * @param preset
     * @param challengeRating
     * @return NPC
     */

    public static NPC spinNPC(int xCoord, int yCoord, NPCPreset preset, int challengeRating) {
        // return null if the preset is null. Avoids errors
        if (Objects.isNull(preset)) {
            return null;
        }

        Random rand = new Random();
        NPCAlliance npcAlliance = preset.npcAlliance;

        // choose a random description
        String description = preset.descriptions[rand.nextInt(preset.descriptions.length)];

        // choose the stats from the possible ranges for each
        int AC = randomFromRange(preset.ACRange);
        int strength = randomFromRange(preset.strRange);
        int dexterity = randomFromRange(preset.dexRange);
        int constitution = randomFromRange(preset.conRange);
        int intelligence = randomFromRange(preset.intRange);
        int wisdom = randomFromRange(preset.wisRange);
        int charisma = randomFromRange(preset.chaRange);
        int noise = randomFromRange(preset.noiseRange);
        int shield = randomFromRange(preset.shieldRange);

        // choose a random name
        String name = preset.name[rand.nextInt(preset.name.length)];

        NPC result = new NPC(xCoord, yCoord, AC, strength, dexterity, constitution, intelligence, wisdom, charisma,
                noise, shield, name, npcAlliance, description);

        // Load the NPC's inventory
        preset.inventory.stream().map(n -> spinInteractable(n)).forEach(result::addInventory);

        return result;
    }

    /**
     * return a random number from a range, given by a length 2 integer array index
     * 0 is the min and index 1 is the max
     *
     * @param range
     * @return int
     */
    private static int randomFromRange(int[] range) {
        Random rand = new Random();
        return range[1] > range[0] ? rand.nextInt(range[1] - range[0]) + range[0] : range[0];
    }

    /**
     * generates a random enemy by choosing a preset from the files and puts it in
     * the proper coordinates.
     *
     * @param xCoord
     * @param yCoord
     * @param challenge
     * @return NPC
     */
    private static NPC generateEnemy(int xCoord, int yCoord, int challenge) {
        Random rand = new Random();
        String[] files = Parser.trimQuotes(Parser.parseArray("enemy-presets", readFile(pathPath)));

        // for each file, load in all the enemy presets
        try {
            List<String> enemyChoices = new ArrayList<>();
            for (String fileString : files) {
                enemyChoices.addAll(Arrays.asList(Parser.parseArray("enemy-presets", readFile(fileString))));
            }

            // choose one of the enemypresets, make it into an enemy, then return it.
            String choice = enemyChoices.get(rand.nextInt(enemyChoices.size()));
            return spinNPC(xCoord, yCoord, PresetLoader.loadNpcPreset(choice), 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
        // this should never happen.
        return null;
    }
}
