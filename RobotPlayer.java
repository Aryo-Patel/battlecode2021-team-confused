package src;

import battlecode.common.*;
import battlecode.doc.RobotTypeTaglet;

import java.awt.image.ImageProducer;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Map;

/**
 * Politician rush player
 */
public class RobotPlayer {
    static RobotController rc;

    static final RobotType[] spawnableRobot = {
            RobotType.POLITICIAN,
            RobotType.SLANDERER,
            RobotType.MUCKRAKER,
    };

    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };
    static ArrayList<MapLocation> important_locs = new ArrayList<>(); // arraylist of critical locs to send troops, ordered by priority
    static ArrayList<Integer> bots_alive = new ArrayList<>(); // arraylist containing IDs of current bots
    static int turnCount; // tracks how far along the game we are
    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        System.out.println("I'm a " + rc.getType() + " and I just got created!");

        while (true) {
            // Try/catch blocks stop unhandled exceptions, which cause your robot to freeze
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You may rewrite this into your own control structure if you wish.
                switch (rc.getType()) {
                    case ENLIGHTENMENT_CENTER: runEnlightenmentCenter(); break;
                    case POLITICIAN:           runPolitician();          break;
                    case MUCKRAKER:            runMuckraker();           break;
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }

    /** Enlightenment centers spam politicians */
    static void runEnlightenmentCenter() throws GameActionException {
        /**
         * init build politicians to rush to neutral ECs + explore map
         */
        //init build 8 politicians to explore all 8 dirs
        if (turnCount == 0){
            RobotType initBuild = RobotType.POLITICIAN; // init robot used to explore the map, infl = 15
            for (int i = 0; i<8; i++){
                Direction d = directions[i];
                if (rc.canBuildRobot(initBuild, d, 15)){
                    rc.buildRobot(initBuild, d, 15);
                    int spawned_id = rc.senseRobotAtLocation(rc.getLocation().add(d)).getID();
                    bots_alive.add(spawned_id);
                }
            }
        }

        RobotType[] spawning_order = new RobotType[]{RobotType.POLITICIAN, RobotType.SLANDERER, RobotType.MUCKRAKER};
        int influence = rc.getInfluence();
        int[] partition_infl = new int[]{influence/8, influence/16, influence/8};
        RobotType to_build = spawning_order[turnCount%3];
        int infl = partition_infl[turnCount%3];

        for (Direction dir : Direction.values()) { // build a bot in a random direction
            if (rc.canBuildRobot(to_build, dir, infl)) {
                rc.buildRobot(to_build, dir, infl);
            } else {
                break;
            }
        }

        if (important_locs.size() > 0){
            sendLocation(important_locs.get(0)); // send out the first important loc
        }

        int bidAmount = rc.getInfluence()/8;
        if (rc.canBid(bidAmount))
            rc.bid(bidAmount);
    }


    static void runPolitician() throws GameActionException {

    }

    static int enlightenmentCenterId = -1;
    static MapLocation muckTarget = null;
    static void runMuckraker() throws GameActionException{
        if (enlightenmentCenterId == -1) {
            for (RobotInfo robot : rc.senseNearbyRobots(-1, rc.getTeam())) {
                if (robot.type == RobotType.ENLIGHTENMENT_CENTER)
                    enlightenmentCenterId = robot.ID; // find friendly EC's ID
            }
        }
        if (rc.canGetFlag(enlightenmentCenterId))
            muckTarget = getLocationFromFlag(rc.getFlag(enlightenmentCenterId));
        if (muckTarget != null)
            bug_pathing(muckTarget);
    }

    ////////////////////////////////////////////////////////////////////////////
    // COMMUNICATION

    static final int NBITS = 7; // using 14 bits to encode x + y coords
    static final int BITMASK = (1 << NBITS) - 1; // eq to 1111111, used for bit ops

    static void sendLocation(MapLocation location) throws GameActionException {
        /**
         * 128*x%128 + y%128 = encodedLoc
         * take a location, encode it, and set it as the rc's flag
         */
        int x = location.x, y = location.y;
        int encodedLocation = ((x & BITMASK) << NBITS) + (y & BITMASK);
        if (rc.canSetFlag(encodedLocation)) {
            rc.setFlag(encodedLocation);
        }
    }

    @SuppressWarnings("unused")
    static void sendLocation(MapLocation location, int extraInformation) throws GameActionException {
        /*
        encodedLocation = 128*(x % 128) + (y % 128) + extraInfo*128*128
        same thing, but may encode extra info
         */
        int x = location.x, y = location.y;
        int encodedLocation = (extraInformation << (2*NBITS)) + ((x & BITMASK) << NBITS) + (y & BITMASK);
        if (rc.canSetFlag(encodedLocation)) {
            rc.setFlag(encodedLocation);
        }
    }

    static MapLocation getLocationFromFlag(int flag) {
        /**
         *
         */
        int y = flag & BITMASK;
        int x = (flag >> NBITS) & BITMASK;
        // int extraInformation = flag >> (2*NBITS);

        MapLocation currentLocation = rc.getLocation();
        int offsetX128 = currentLocation.x >> NBITS; //
        int offsetY128 = currentLocation.y >> NBITS;
        MapLocation actualLocation = new MapLocation((offsetX128 << NBITS) + x, (offsetY128 << NBITS) + y);

        for (Point pt: new Point[]{new Point(1, 0), new Point(0, 1), new Point(-1, 0),
                new Point (0, -1)}){
            int dx = 0, dy = 0;
            if (pt.x < 0)
                dx = -(pt.x << NBITS);
            else if (pt.x > 0)
                dx = pt.x << NBITS;
            else if (pt.y < 0)
                dy = -(pt.y << NBITS);
            else if (pt.y > 0)
                dy = pt.y << NBITS;
            MapLocation alternative = actualLocation.translate(dx, dy);
            if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation))
                actualLocation = alternative;
        }
        return actualLocation;
    }


    ////////////////////////////////////////////////////////////////////////////
    // BASIC BUG - just follow the obstacle while it's in the way
    //             not the best bug, but works for "simple" obstacles
    //             for better bugs, think about Bug 2!

    static final double passabilityThreshold = 0.7;
    static Direction bugDirection = null;

    static boolean bug_pathing(MapLocation target) throws GameActionException{
        Direction targetDir = rc.getLocation().directionTo(target); // travel in straight line to target
        bugDirection = null;

        while (!rc.getLocation().equals(target)){ // while we haven't reached the target yet
            if (rc.isReady()){
                if (rc.canMove(targetDir) && rc.sensePassability(rc.getLocation().add(targetDir)) >= passabilityThreshold){
                    rc.move(targetDir); // takes one step along the straight line
                    bugDirection = null; // reset in case
                }

                else{ // we are blocked by an obstacle
                    double currentDist = rc.getLocation().distanceSquaredTo(target); // init dist from target before going around obstacle
                    if (bugDirection == null)
                        bugDirection = targetDir; // init bug dir to face the target
                    // move along obs until we're back on line to target
                    while (!bugDirection.equals(targetDir) || rc.getLocation().distanceSquaredTo(target) < currentDist){
                        for (int dindex = 0; dindex < 8; dindex ++){
                            if (rc.canMove(bugDirection) && rc.sensePassability(rc.getLocation().add(bugDirection)) >= passabilityThreshold) { // moving around obstacle
                                rc.move(bugDirection);
                                bugDirection = bugDirection.rotateLeft();
                                break;
                            }
                            bugDirection = bugDirection.rotateRight();
                        }
                    }

                }
            }
        }
        // we've reached the target, do something
        if (rc.canEmpower(1)) { // if we're a politician
            rc.empower(1);
        }

        for (RobotInfo bot: rc.senseNearbyRobots()){
            if (!bot.getTeam().equals(rc.getTeam())){
                if (rc.canExpose(bot.getLocation()))
                    rc.expose(bot.getLocation());
            }
        }
        if (rc.canExpose(target))
            rc.expose(target);
        return true;
    }

}
