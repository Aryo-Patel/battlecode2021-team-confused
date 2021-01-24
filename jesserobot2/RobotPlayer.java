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

    // first five binary digits
    static int teamID = 31;
    static int enemyEC = 30;
    static int ownEC = 29;

    // binary digits 6-10
    static int enlightenmentCenterID = 31;
    static int politicianID = 30;
    static int slandererID = 29;
    static int muckrakerID = 28;

    static ArrayList<Integer> teamIDs = new ArrayList<Integer>() {
        {
            add(teamID);
            add(enemyEC);
            add(ownEC);
        }
    };
    static int ecID = 0;

    static Direction standardDirection;

    static ArrayList<Integer> spawnedRobots = new ArrayList<Integer>();

    static TreeMap<MapLocation, Integer> queueEC = new TreeMap<MapLocation, Integer>();

    static int log2(int N) {
        return (int) (Math.log(N) / Math.log(2));
    }

    static void sendLocation(int extraInfo1, int extraInfo2, MapLocation location) throws GameActionException {
        int x = location.x, y = location.y;
        int encodedLocation = extraInfo1*128*128*32 + extraInfo2*128*128 + (x % 128) * 128 + (y % 128);
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

    static int shortestDistance(MapLocation loc1, MapLocation loc2) throws GameActionException {
        int offsetX = Math.abs(loc1.x - loc2.x);
        int offsetY = Math.abs(loc1.y - loc2.y);
        return Math.min(offsetX, offsetY);
    }

    static boolean tryMoveInDirection(Direction dir) throws GameActionException {
        MapLocation center = rc.getLocation();

        double MinTurns = Double.POSITIVE_INFINITY;
        Direction bestDirection = null;

        if (rc.canMove(dir)) {
            if ( 1.0/rc.sensePassability(center.add(dir)) < MinTurns ) {
                MinTurns = Math.floor(1.0/rc.sensePassability(center.add(dir)));
                bestDirection = dir;
            }
        }

        if (rc.canMove(dir.rotateLeft())) {
            if ( 1.0/rc.sensePassability(center.add(dir.rotateLeft())) < MinTurns ) {
                MinTurns = Math.floor(1.0/rc.sensePassability(center.add(dir.rotateLeft())));
                bestDirection = dir.rotateLeft();
            }
        }

        if (rc.canMove(dir.rotateRight())) {
            if ( 1.0/rc.sensePassability(center.add(dir.rotateRight())) < MinTurns ) {
                MinTurns = Math.floor(1.0/rc.sensePassability(center.add(dir.rotateRight())));
                bestDirection = dir.rotateRight();
            }
        }

        if (bestDirection != null) {
            if (rc.canMove(bestDirection)) {
                rc.move(bestDirection);
                return true;
            }
        } else {
            for (Direction alt: directions) {
                if (rc.canMove(alt)) {
                    rc.move(alt);
                    break;
                }
            }
        }
        return false;

    }

    static boolean tryStandardMove() throws GameActionException {
        MapLocation center = rc.getLocation();
        if (rc.canSenseLocation(center.add(standardDirection))) {
            standardDirection = standardDirection;
        } else if (rc.canSenseLocation(center.add(standardDirection.rotateRight().rotateRight()))) {
            standardDirection = standardDirection.rotateRight().rotateRight();
        } else if (rc.canSenseLocation(center.add(standardDirection.rotateLeft().rotateLeft()))) {
            standardDirection = standardDirection.rotateLeft().rotateLeft();
        } else if (rc.canSenseLocation(center.add(standardDirection.opposite().rotateLeft()))) {
            standardDirection = standardDirection.opposite().rotateLeft();
        }

        if (tryMoveInDirection(standardDirection)) {
            return true;
        } else {
            return false;
        }
    }

    static boolean isOnTeam(RobotInfo robot) throws GameActionException {
        int extraInfo = rc.getFlag(robot.getID()) / 128 / 128 / 32;
        if (teamIDs.contains(extraInfo) && robot.getTeam() == rc.getTeam()) {
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
        int[] spawnInfluence = {rc.getInfluence()/8, rc.getInfluence()/12, rc.getInfluence()/12, rc.getInfluence()/4};
        int influence = spawnInfluence[((turnCount - 1)/2) % 4];
        for (int i = 0; i < 8; i++) {
            Direction dir = directions[(((turnCount - 1)/2%8) + i)%8];
            if (rc.canBuildRobot(toBuild, dir, influence)) {
                rc.buildRobot(toBuild, dir, influence);
                spawnedRobots.add(rc.senseRobotAtLocation(rc.getLocation().add(dir)).getID());
                break;
            }
        }
        for (int robot : spawnedRobots) {
            try {
                if (rc.getFlag(robot) / 128 / 128 / 32 == ownEC) {
                    if (queueEC.containsKey(decodeLocation(rc.getFlag(robot)))) {
                        queueEC.remove(decodeLocation(rc.getFlag(robot)));
                    } else {

                    }
                } else if (rc.getFlag(robot) / 128 / 128 / 32 == enemyEC) {
                    if (queueEC.containsKey(decodeLocation(rc.getFlag(robot)))) {
                        queueEC.replace(decodeLocation(rc.getFlag(robot)), (rc.getFlag(robot) / 128 / 128)%32);
                    } else {
                        queueEC.put(decodeLocation(rc.getFlag(robot)), (rc.getFlag(robot) / 128 / 128)%32);
                    }
                }
            } catch (Exception e) {
                
            }
        }
        if (queueEC.size() > 0) {
            int minConviction = 32;
            MapLocation minLocation = null;
            for (MapLocation ec : queueEC.keySet()) {
                if (queueEC.get(ec) < minConviction) {
                    minConviction = queueEC.get(ec);
                    minLocation = ec;
                }
            }
            sendLocation(enemyEC, minConviction, minLocation);
        } else {
            sendLocation(teamID, enlightenmentCenterID, rc.getLocation());
        }
        int bidAmount = (int) Math.floor(rc.getInfluence()/24);
        if (rc.canBid(bidAmount)) {
            rc.bid(bidAmount);
        }
        System.out.println(queueEC);
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
                    standardDirection = rc.getLocation().directionTo(robot.getLocation()).opposite();
                }
            }
        }

        if (affectable.length != 0 && rc.canEmpower(actionRadius)) {
            for (RobotInfo robot : affectable) {
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() != rc.getTeam()) {
                    rc.empower(actionRadius);
                }
            }
        }

        try {
            if (rc.getFlag(ecID) / 128 / 128 / 32 == enemyEC) {
                tryMoveInDirection(rc.getLocation().directionTo(decodeLocation(rc.getFlag(ecID))));
                for (RobotInfo robot : nearbyRobots) {
                    if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() != rc.getTeam()) {
                        sendLocation(enemyEC, log2(robot.getConviction()), robot.getLocation());
                        tryMoveInDirection(rc.getLocation().directionTo(robot.getLocation()));
                        break;
                    } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == rc.getTeam()) {
                        sendLocation(ownEC, log2(robot.getConviction()), robot.getLocation());
                    } else {
                        sendLocation(teamID, politicianID, rc.getLocation());
                    }
                }
            } else {
                for (RobotInfo robot : nearbyRobots) {
                    if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() != rc.getTeam()) {
                        sendLocation(enemyEC, log2(robot.getConviction()), robot.getLocation());
                        tryMoveInDirection(rc.getLocation().directionTo(robot.getLocation()));
                        break;
                    } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == rc.getTeam()) {
                        sendLocation(ownEC, log2(robot.getConviction()), robot.getLocation());
                        break;
                    }
                    sendLocation(teamID, politicianID, rc.getLocation());
                }
                tryStandardMove();
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
                    standardDirection = rc.getLocation().directionTo(robot.getLocation()).opposite();
                }
            }
        }

        for (RobotInfo robot : nearbyRobots) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() != rc.getTeam()) {
                sendLocation(enemyEC, log2(robot.getConviction()), robot.getLocation());
                break;
            } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == rc.getTeam()) {
                sendLocation(ownEC, log2(robot.getConviction()), robot.getLocation());
                break;
            } else {
                sendLocation(teamID, slandererID, rc.getLocation());
            }
        }

        if (tryStandardMove())
            System.out.println("I moved!");
    }

    static void runMuckraker() throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        if (ecID == 0) {
            for (RobotInfo robot : nearbyRobots) {
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    ecID = robot.getID();
                    standardDirection = rc.getLocation().directionTo(robot.getLocation()).opposite();
                }
            }
        }

        for (RobotInfo robot : nearbyRobots) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() != rc.getTeam()) {
                sendLocation(enemyEC, log2(robot.getConviction()), robot.getLocation());
                break;
            } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == rc.getTeam()) {
                sendLocation(ownEC, log2(robot.getConviction()), robot.getLocation());
                break;
            } else {
                sendLocation(teamID, muckrakerID, rc.getLocation());
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
        if (tryStandardMove())
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
