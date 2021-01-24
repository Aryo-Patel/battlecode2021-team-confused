package jesserobot2;
import battlecode.common.*;
import java.util.*;
import java.lang.Math;

public strictfp class RobotPlayer {
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

    static int turnCount;

    static ArrayList<Direction> moveSequence = new ArrayList<Direction>();

    // static int detectionRadius = (int)Math.ceil(Math.sqrt(rc.getType().detectionRadiusSquared));

    static int detectionRadius = 6;

    static int primeTeam = 1019;
    static int primeCenter = 1021;
    static int ecID = 0;

    static ArrayList<Integer> spawnedRobots = new ArrayList<Integer>();

    static void sendLocation(int extraInfo, MapLocation location) throws GameActionException {
        int x = location.x, y = location.y;
        int encodedLocation = extraInfo*128*128 + (x % 128) * 128 + (y % 128);
        if (rc.canSetFlag(encodedLocation)) {
            rc.setFlag(encodedLocation);
        }
    }

    static MapLocation decodeLocation(int encodedLocation) throws GameActionException {
        int y = encodedLocation % 128;
        int x = (encodedLocation / 128) % 128;
        int extraInfo = encodedLocation / 128 / 128;

        MapLocation currentLocation = rc.getLocation();
        int offsetX128 = currentLocation.x / 128;
        int offsetY128 = currentLocation.y / 128;
        MapLocation actualLocation = new MapLocation(offsetX128 * 128 + x, offsetY128 * 128 + y);

        MapLocation alternative = actualLocation.translate(-128, 0);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        alternative = actualLocation.translate(128, 0);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        alternative = actualLocation.translate(0, -128);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        alternative = actualLocation.translate(0,128);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        return actualLocation;
    }

    static ArrayList<MapLocation> getVisibleLocations() throws GameActionException {
        ArrayList<MapLocation> visibleLocations = new ArrayList<MapLocation>();
        MapLocation center = rc.getLocation();
        int centerX = center.x;
        int centerY = center.y;
        for (int x = centerX - detectionRadius; x < centerX + detectionRadius +1; x++) {
            for (int y = centerY - detectionRadius; y < centerY + detectionRadius + 1; y++) {
                MapLocation location = new MapLocation(x, y);
                if (rc.canDetectLocation(location)) {
                    visibleLocations.add(location);
                }
            }
        }
        return visibleLocations;
    }

    static double getExpectedTurnWait() throws GameActionException {
        double totalTurnWait = 0.0;
        double numSquares = 0.0;
        MapLocation center = rc.getLocation();
        int centerX = center.x;
        int centerY = center.y;
        for (int x = centerX - detectionRadius; x < centerX + detectionRadius +1; x++) {
            for (int y = centerY - detectionRadius; y < centerY + detectionRadius + 1; y++) {
                MapLocation location = new MapLocation(x, y);
                if (rc.canDetectLocation(location)) {
                    totalTurnWait = totalTurnWait + 1.0/rc.sensePassability(location);
                    numSquares = numSquares + 1.0;
                }
            }
        }
        return totalTurnWait/numSquares;

    }


    //weird compressing of passability (doesn't account for if there are any units in these locations)
    static double expectedDirectionCost(Direction dir) throws GameActionException {
        MapLocation center = rc.getLocation();
        center = center.add(dir).add(dir);

        double totalTurnWait = 0.0;
        double numSquares = 0.0;

        if (rc.canDetectLocation(center)) {
            totalTurnWait = 1.0/rc.sensePassability(center);
            numSquares = 1.0;
        }

        for (Direction direction : directions) {
            if (rc.canDetectLocation(center.add(direction))) {
                totalTurnWait = totalTurnWait + 1.0/rc.sensePassability(center.add(direction));
                numSquares = numSquares + 1.0;
            }
        }

        return totalTurnWait/numSquares;
    }

    static int shortestDistance(MapLocation loc1, MapLocation loc2) throws GameActionException {
        int offsetX = Math.abs(loc1.x - loc2.x);
        int offsetY = Math.abs(loc1.y - loc2.y);
        return Math.min(offsetX, offsetY);
    }

    static void moveInDirection(Direction dir) throws GameActionException {
        // double expectedTurnWait = getExpectedTurnWait();
        // System.out.println(Clock.getBytecodeNum());
        // MapLocation center = rc.getLocation();

        // double MinTurns = Double.POSITIVE_INFINITY;
        // Direction bestDirection = null;

        // MapLocation target = center;
        // while (rc.canDetectLocation(target)) {
        //     target.add(dir);
        // }
        // target.add(dir.opposite());

        // MapLocation newLocation;
        // double totalExpectedTurns;
        // System.out.println(Clock.getBytecodeNum());

        // for (Direction direction : directions) {
        //     newLocation = center.add(direction);
        //     totalExpectedTurns = (1.0/rc.sensePassability(newLocation)) + expectedTurnWait * shortestDistance(newLocation, target);
        //     if ( totalExpectedTurns < MinTurns ) {
        //         MinTurns = totalExpectedTurns;
        //         bestDirection = direction;
        //     }
        // }

        MapLocation center = rc.getLocation();

        double MinTurns = Double.POSITIVE_INFINITY;
        Direction bestDirection = null;

        if (rc.canMove(dir)) {
            if ( 1.0/rc.sensePassability(center.add(dir)) < MinTurns ) {
                MinTurns = 1.0/rc.sensePassability(center.add(dir));
                bestDirection = dir;
            }
        }

        if (rc.canMove(dir.rotateLeft())) {
            if ( 1.0/rc.sensePassability(center.add(dir.rotateLeft())) < MinTurns ) {
                MinTurns = 1.0/rc.sensePassability(center.add(dir.rotateLeft()));
                bestDirection = dir.rotateLeft();
            }
        }

        if (rc.canMove(dir.rotateRight())) {
            if ( 1.0/rc.sensePassability(center.add(dir.rotateRight())) < MinTurns ) {
                MinTurns = 1.0/rc.sensePassability(center.add(dir.rotateRight()));
                bestDirection = dir.rotateRight();
            }
        }

        if (bestDirection != null) {
            if (rc.canMove(bestDirection)) {
                rc.move(bestDirection);
            }
        }

    }

    static void moveTowards(MapLocation target) throws GameActionException {

        double averageTurnWait = getExpectedTurnWait();

        MapLocation center = rc.getLocation();

        double MinTurns = Double.POSITIVE_INFINITY;
        Direction bestDirection = null;

        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                MapLocation newLocation = center.add(dir);
                double expectedCost = 1.0/rc.sensePassability(center.add(dir)) + expectedDirectionCost(dir) * shortestDistance(newLocation, target);
                if (expectedCost < MinTurns) {
                    MinTurns = expectedCost;
                    bestDirection = dir;
                }
            }
        }

        if (bestDirection != null) {
            if (rc.canMove(bestDirection)) {
                rc.move(bestDirection);
            }
        }
    }

    static boolean isOnTeam(RobotInfo robot) throws GameActionException {
        int extraInfo = rc.getFlag(robot.getID());
        if (extraInfo == primeCenter || extraInfo == primeTeam) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        turnCount = 0;

        System.out.println("I'm a " + rc.getType() + " and I just got created!");
        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to freeze
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You may rewrite this into your own control structure if you wish.
                System.out.println("I'm a " + rc.getType() + "! Location " + rc.getLocation());
                switch (rc.getType()) {
                    case ENLIGHTENMENT_CENTER: runEnlightenmentCenter(); break;
                    case POLITICIAN:           runPolitician();          break;
                    case SLANDERER:            runSlanderer();           break;
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

    static void runEnlightenmentCenter() throws GameActionException {
        RobotType[] spawnOrder = {RobotType.SLANDERER, RobotType.MUCKRAKER, RobotType.MUCKRAKER, RobotType.POLITICIAN};
        RobotType toBuild = spawnOrder[((turnCount - 1)/2) % 4];
        int influence = rc.getInfluence()/10;
        for (int i = 0; i < 8; i++) {
            Direction dir = directions[(((turnCount - 1)/2%8) + i)%8];
            System.out.println(toBuild);
            System.out.println(dir);
            System.out.println(influence);
            System.out.println(rc.canBuildRobot(toBuild, dir, influence));
            if (rc.canBuildRobot(toBuild, dir, influence)) {
                rc.buildRobot(toBuild, dir, influence);
                spawnedRobots.add(rc.senseRobotAtLocation(rc.getLocation().add(dir)).getID());
                break;
            }
        }
        for (int robot : spawnedRobots) {
            try {
                if (rc.getFlag(robot) / 128 / 128 == primeCenter) {
                    rc.setFlag(rc.getFlag(robot));
                    break;
                }
                sendLocation(primeTeam, rc.getLocation());
            } catch (Exception e) {
                
            }
        }
    }

    static void runPolitician() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        int actionRadius = rc.getType().actionRadiusSquared;
        RobotInfo[] affectable = rc.senseNearbyRobots(actionRadius);

        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();

        if (ecID == 0) {
            for (RobotInfo robot : nearbyRobots) {
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    ecID = robot.getID();
                }
            }
        }

        try {
            if (rc.getFlag(ecID) / 128 / 128 == primeCenter) {
                moveInDirection(rc.getLocation().directionTo(decodeLocation(rc.getFlag(ecID))));
                for (RobotInfo robot : nearbyRobots) {
                    if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() != rc.getTeam()) {
                        sendLocation(primeCenter, robot.getLocation());
                        tryMove(rc.getLocation().directionTo(robot.getLocation()));
                        break;
                    }
                    sendLocation(primeTeam, rc.getLocation());
                }
            } else {
                for (RobotInfo robot : nearbyRobots) {
                    if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() != rc.getTeam()) {
                        sendLocation(primeCenter, robot.getLocation());
                        tryMove(rc.getLocation().directionTo(robot.getLocation()));
                        break;
                    }
                    sendLocation(primeTeam, rc.getLocation());
                }
                tryMove(randomDirection());
            }
    
            if (affectable.length != 0 && rc.canEmpower(actionRadius)) {
                for (RobotInfo robot : affectable) {
                    if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() != rc.getTeam()) {
                        rc.empower(actionRadius);
                    }
                }
            }
        } catch (Exception e) {

        }
    }

    static void runSlanderer() throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        if (ecID == 0) {
            for (RobotInfo robot : nearbyRobots) {
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    ecID = robot.getID();
                }
            }
        }

        if (tryMove(randomDirection()))
            System.out.println("I moved!");
    }

    static void runMuckraker() throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        if (ecID == 0) {
            for (RobotInfo robot : nearbyRobots) {
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    ecID = robot.getID();
                }
            }
        }

        Team enemy = rc.getTeam().opponent();
        int actionRadius = rc.getType().actionRadiusSquared;
        for (RobotInfo robot : rc.senseNearbyRobots(actionRadius, enemy)) {
            if (robot.type.canBeExposed()) {
                // It's a slanderer... go get them!
                if (rc.canExpose(robot.location)) {
                    System.out.println("e x p o s e d");
                    rc.expose(robot.location);
                    return;
                }
            }
        }
        if (tryMove(randomDirection()))
            System.out.println("I moved!");
    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random spawnable RobotType
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnableRobotType() {
        return spawnableRobot[(int) (Math.random() * spawnableRobot.length)];
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }
}
