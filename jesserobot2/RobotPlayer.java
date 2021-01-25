package jesserobot2;

import battlecode.common.*;
import scala.Int;

import java.util.*;
import java.lang.Math;

public strictfp class RobotPlayer{
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
    static int neutralEC = 28;

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
            add(neutralEC);
        }
    };
    static int ecID = 0;

    static int lastECFlag = 0;

    static String type;

    static Direction standardDirection;

    static ArrayList<Integer> spawnedRobots = new ArrayList<Integer>();

    static HashMap<MapLocation, Integer> enemyECs = new HashMap<MapLocation, Integer>();
    static HashMap<MapLocation, Integer> neutralECs = new HashMap<MapLocation, Integer>();

    static ArrayList<Boolean> bidsWon = new ArrayList<>();

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

    static double passThreshold = 0.3; // anything lower is considered an obstacle
    static Direction bugDir = null;
    static double prevDistToTarget = Integer.MAX_VALUE, currentDistToTarget = -1;

    static boolean tryBugPath(MapLocation target) throws GameActionException{
        /*
         * LH bug:
         * travel in straight line to target, go around obstacle if we're back on line,
         * then check if we're closer to obstacle than we were before hitting obstacle
         * if yes, continue along line, if no continue along obstacle
         * TODO: if close to target (det threshold), run more efficient pathing alg
         */
        Direction dir = rc.getLocation().directionTo(target);
        currentDistToTarget = rc.getLocation().distanceSquaredTo(target); // set every bug move
        if (rc.canMove(dir) && (rc.sensePassability(rc.getLocation().add(dir))) >= passThreshold
            && currentDistToTarget <= prevDistToTarget){
            rc.move(dir);
            bugDir = null;
        }
        else{ // blocked, move along left side of obstacle
            if (bugDir == null)
                bugDir = dir.rotateLeft();
            for (int i = 0; i < 8; ++i) {
                if (rc.canMove(bugDir) && rc.sensePassability(rc.getLocation().add(bugDir)) >= passThreshold) {
                    rc.move(bugDir);
                    bugDir = bugDir.rotateLeft();
                    break;
                }
                bugDir = bugDir.rotateRight();
            }
        }
        prevDistToTarget = currentDistToTarget;
        return true;
    }


    static boolean tryMoveInDirection(Direction dir) throws GameActionException {
        MapLocation center = rc.getLocation();

        double MinTurns = Double.POSITIVE_INFINITY;
        Direction bestDirection = null;

        for (Direction d: new Direction[]{dir, dir.rotateLeft(), dir.rotateRight()}){
            if (rc.canMove(d)){
                if ( 1.0/rc.sensePassability(center.add(d)) < MinTurns ) {
                    MinTurns = Math.floor(1.0/rc.sensePassability(center.add(d)));
                    bestDirection = d;
                }
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
        if (rc.canSenseLocation(center.add(standardDirection)))
            standardDirection = standardDirection;
        else if (rc.canSenseLocation(center.add(standardDirection.rotateRight().rotateRight())))
            standardDirection = standardDirection.rotateRight().rotateRight();
        else if (rc.canSenseLocation(center.add(standardDirection.rotateLeft().rotateLeft())))
            standardDirection = standardDirection.rotateLeft().rotateLeft();
        else if (rc.canSenseLocation(center.add(standardDirection.opposite().rotateLeft())))
            standardDirection = standardDirection.opposite().rotateLeft();

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

    static int influence_before_bid = -1, bidAmount = -1;
    static void runEnlightenmentCenter() throws GameActionException {
        System.out.println(rc.getRoundNum());
        int current_influence = rc.getInfluence();
        if (current_influence != -1){ // we have already made a bid
            if (current_influence == influence_before_bid - bidAmount) // we won previous bid
                bidAmount *= 1.1; // idk this is probably too high
            else if (current_influence == influence_before_bid - (bidAmount/2)) // highest EC bidder but lost bid
                bidAmount *= 0.9; //idk this is arbitrary, 10% decrease
        }

        // RobotType[] spawnOrder = {RobotType.SLANDERER, RobotType.MUCKRAKER, RobotType.MUCKRAKER, RobotType.POLITICIAN};
        // RobotType toBuild = spawnOrder[((rc.getRoundNum() - 1)/2) % 4];
        // int[] spawnInfluence = {rc.getInfluence()/12, rc.getInfluence()/24, rc.getInfluence()/24, rc.getInfluence()/8};
        // int influence = spawnInfluence[((rc.getRoundNum() - 1)/2) % 4];
        // for (int i = 0; i < 8; i++) {
        //     Direction dir = directions[(((rc.getRoundNum() - 1)/2%8) + i)%8];
        //     if (rc.canBuildRobot(toBuild, dir, influence)) {
        //         rc.buildRobot(toBuild, dir, influence);
        //         spawnedRobots.add(rc.senseRobotAtLocation(rc.getLocation().add(dir)).getID());
        //         break;
        //     }
        // }

        // updates hash maps of ECs based on displayed flags
        for (int robot : spawnedRobots) {
            if (rc.canGetFlag(robot)) {
                if (rc.getFlag(robot) / 128 / 128 / 32 == ownEC) {
                    if (enemyECs.containsKey(decodeLocation(rc.getFlag(robot)))) {
                        enemyECs.remove(decodeLocation(rc.getFlag(robot)));
                    } else if (neutralECs.containsKey(decodeLocation(rc.getFlag(robot)))) {
                        neutralECs.remove(decodeLocation(rc.getFlag(robot)));
                    }
                } else if (rc.getFlag(robot) / 128 / 128 / 32 == enemyEC) {
                    if (enemyECs.containsKey(decodeLocation(rc.getFlag(robot)))) {
                        enemyECs.replace(decodeLocation(rc.getFlag(robot)), (rc.getFlag(robot) / 128 / 128)%32);
                    } else if (neutralECs.containsKey(decodeLocation(rc.getFlag(robot)))) {
                        neutralECs.remove(decodeLocation(rc.getFlag(robot)));
                        enemyECs.put(decodeLocation(rc.getFlag(robot)), (rc.getFlag(robot) / 128 / 128)%32);
                    } else {
                        enemyECs.put(decodeLocation(rc.getFlag(robot)), (rc.getFlag(robot) / 128 / 128)%32);
                    }
                } else if (rc.getFlag(robot) / 128 / 128 / 32 == neutralEC) {
                    if (neutralECs.containsKey(decodeLocation(rc.getFlag(robot)))) {
                        neutralECs.replace(decodeLocation(rc.getFlag(robot)), (rc.getFlag(robot) / 128 / 128)%32);
                    } else {
                        neutralECs.put(decodeLocation(rc.getFlag(robot)), (rc.getFlag(robot) / 128 / 128)%32);
                    }
                }
            }
        }

        int mod8Turn = rc.getRoundNum() % 8;
        if (mod8Turn == 7 || mod8Turn == 0) {
            if (neutralECs.size() > 0) {
                int minConviction = 32;
                MapLocation minLocation = null;
                for (MapLocation ec : neutralECs.keySet()) {
                    if (neutralECs.get(ec) < minConviction) {
                        minConviction = neutralECs.get(ec);
                        minLocation = ec;
                    }
                }
                if (minLocation != null) {
                    sendLocation(neutralEC, minConviction, minLocation);
                    for (int i = 0; i < 8; i++) {
                        Direction dir = directions[(((rc.getRoundNum() - 1)/2%8) + i)%8];
                        if (rc.canBuildRobot(RobotType.POLITICIAN, dir, (int) Math.pow(2, minConviction+1))) {
                            rc.buildRobot(RobotType.POLITICIAN, dir, (int) Math.pow(2, minConviction+1));
                            spawnedRobots.add(rc.senseRobotAtLocation(rc.getLocation().add(dir)).getID());
                            break;
                        }
                    }
                } else {
                    sendLocation(teamID, enlightenmentCenterID, rc.getLocation());
                }
            } else {
                if (enemyECs.size() > 0) {
                    int minConviction = 32;
                    MapLocation minLocation = null;
                    for (MapLocation ec : enemyECs.keySet()) {
                        if (enemyECs.get(ec) < minConviction) {
                            minConviction = enemyECs.get(ec);
                            minLocation = ec;
                        }
                    }
                    if (minLocation != null) {
                        sendLocation(enemyEC, minConviction, minLocation);
                        for (int i = 0; i < 8; i++) {
                            Direction dir = directions[(((rc.getRoundNum() - 1)/2%8) + i)%8];
                            if (rc.canBuildRobot(RobotType.POLITICIAN, dir, rc.getInfluence()/4)) {
                                rc.buildRobot(RobotType.POLITICIAN, dir, rc.getInfluence()/4);
                                spawnedRobots.add(rc.senseRobotAtLocation(rc.getLocation().add(dir)).getID());
                                break;
                            }
                        }
                    } else {
                        sendLocation(teamID, enlightenmentCenterID, rc.getLocation());
                    }
                } else {
                    sendLocation(teamID, enlightenmentCenterID, rc.getLocation());
                }
                sendLocation(teamID, enlightenmentCenterID, rc.getLocation());
            }
        } else if (mod8Turn == 3 || mod8Turn == 4 || mod8Turn == 5 || mod8Turn == 6) {
            if (enemyECs.size() > 0) {
                int minConviction = 32;
                MapLocation minLocation = null;
                for (MapLocation ec : enemyECs.keySet()) {
                    if (enemyECs.get(ec) < minConviction) {
                        minConviction = enemyECs.get(ec);
                        minLocation = ec;
                    }
                }
                if (minLocation != null) {
                    sendLocation(enemyEC, minConviction, minLocation);
                } else {
                    sendLocation(teamID, enlightenmentCenterID, rc.getLocation());
                }
            } else {
                sendLocation(teamID, enlightenmentCenterID, rc.getLocation());
            }
            for (int i = 0; i < 8; i++) {
                Direction dir = directions[(((rc.getRoundNum() - 1)/2%8) + i)%8];
                if (rc.canBuildRobot(RobotType.MUCKRAKER, dir, 3)) {
                    rc.buildRobot(RobotType.MUCKRAKER, dir, 3);
                    spawnedRobots.add(rc.senseRobotAtLocation(rc.getLocation().add(dir)).getID());
                    break;
                }
            }
        } else if (mod8Turn == 1 || mod8Turn == 2) {
            sendLocation(teamID, enlightenmentCenterID, rc.getLocation());
            if (neutralECs.size() == 0) {
                for (int i = 0; i < 8; i++) {
                    Direction dir = directions[(((rc.getRoundNum() - 1)/2%8) + i)%8];
                    if (rc.canBuildRobot(RobotType.SLANDERER, dir, rc.getInfluence()/8)) {
                        rc.buildRobot(RobotType.SLANDERER, dir, rc.getInfluence()/8);
                        spawnedRobots.add(rc.senseRobotAtLocation(rc.getLocation().add(dir)).getID());
                        break;
                    }
                }
            } else {
                for (int i = 0; i < 8; i++) {
                    Direction dir = directions[(((rc.getRoundNum() - 1)/2%8) + i)%8];
                    if (rc.canBuildRobot(RobotType.SLANDERER, dir, rc.getInfluence()/24)) {
                        rc.buildRobot(RobotType.SLANDERER, dir, rc.getInfluence()/24);
                        spawnedRobots.add(rc.senseRobotAtLocation(rc.getLocation().add(dir)).getID());
                        break;
                    }
                }
            }
        }

        if (influence_before_bid == -1){ //first bid
            influence_before_bid = rc.getInfluence();
            bidAmount = rc.getInfluence()/48;
        }
        if (rc.canBid(bidAmount)) {
            rc.bid(bidAmount);
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
                    standardDirection = rc.getLocation().directionTo(robot.getLocation()).opposite();
                }
            }
        }

        int mod8Turn = rc.getRoundNum() % 8;

        if (mod8Turn == 7 || mod8Turn == 0) { // find neutral ECs (if any)
            if (rc.canGetFlag(ecID)) {
                lastECFlag = rc.getFlag(ecID);
            }
        }

        // empower enemy/neutral EC if any, otherwise empower enemy units
        if (affectable.length != 0 && rc.canEmpower(actionRadius)) {
            int damageDone = (int) (Math.floor(rc.getConviction() - GameConstants.EMPOWER_TAX) / affectable.length);
            int dealableDamage = 0;
            int killableUnits = 0;
            for (RobotInfo robot : affectable) {
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() != rc.getTeam()) {
                    rc.empower(actionRadius);
                    break;
                } else if (robot.getTeam() != rc.getTeam() && robot.getConviction() < damageDone) {
                    killableUnits++;
                    dealableDamage += damageDone;
                }
            }
            if (rc.canEmpower(actionRadius) && (dealableDamage > 0.7*rc.getConviction())) {
                rc.empower(actionRadius);
            }
        }

        if (lastECFlag / 128 / 128 / 32 == neutralEC) {
            tryMoveInDirection(rc.getLocation().directionTo(decodeLocation(lastECFlag)));
            //tryBugPath(decodeLocation(lastECFlag));
        }
        for (RobotInfo robot : nearbyRobots) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == Team.NEUTRAL) {
                sendLocation(neutralEC, log2(robot.getConviction()), robot.getLocation());
                tryMoveInDirection(rc.getLocation().directionTo(robot.getLocation()));
                //tryBugPath(decodeLocation(lastECFlag));
                break;
            } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == enemy) {
                sendLocation(enemyEC, log2(robot.getConviction()), robot.getLocation());
                break;
            } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == rc.getTeam()) {
                sendLocation(ownEC, log2(robot.getConviction()), robot.getLocation());
                break;
            }
            sendLocation(teamID, politicianID, rc.getLocation());
        }
        tryStandardMove();
    }

    static void runSlanderer() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        if (ecID == 0) { // finds nearby friendly EC + gets ID
            for (RobotInfo robot : nearbyRobots) {
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    ecID = robot.getID();
                    standardDirection = rc.getLocation().directionTo(robot.getLocation()).opposite();
                }
            }
        }

        int mod8Turn = rc.getRoundNum() % 8;

        if (mod8Turn == 1 || mod8Turn == 2) {
            if (rc.canGetFlag(ecID)) {
                lastECFlag = rc.getFlag(ecID);
            }
        }

        for (RobotInfo robot : nearbyRobots) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == Team.NEUTRAL) {
                sendLocation(neutralEC, log2(robot.getConviction()), robot.getLocation());
                standardDirection = rc.getLocation().directionTo(robot.getLocation()).opposite();
                tryStandardMove();
                break;
            } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == enemy) {
                sendLocation(enemyEC, log2(robot.getConviction()), robot.getLocation());
                break;
            } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == rc.getTeam()) {
                sendLocation(ownEC, log2(robot.getConviction()), robot.getLocation());
                break;
            } else {
                sendLocation(teamID, slandererID, rc.getLocation());
            }
        }

        RobotInfo nearestEnemyMuckraker = null;
        int nearestDistance = 10;

        for (RobotInfo robot : nearbyRobots) {
            if (robot.getType() == RobotType.MUCKRAKER && robot.getTeam() == enemy) {
                if (shortestDistance(rc.getLocation(), robot.getLocation()) < nearestDistance) {
                    nearestDistance = shortestDistance(rc.getLocation(), robot.getLocation());
                    nearestEnemyMuckraker = robot;
                }
            }
        }

        if (nearestEnemyMuckraker != null) {
            standardDirection = rc.getLocation().directionTo(nearestEnemyMuckraker.getLocation()).opposite();
        }

        if (tryStandardMove()) {
            System.out.println("I moved!");
        }
    }

    static void runMuckraker() throws GameActionException {
        System.out.println(rc.getRoundNum());
        Team enemy = rc.getTeam().opponent();
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        if (ecID == 0) {
            for (RobotInfo robot : nearbyRobots) {
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER) {
                    ecID = robot.getID();
                    standardDirection = rc.getLocation().directionTo(robot.getLocation()).opposite();
                }
            }
            if (rc.getRoundNum() % 8 == 4) {
                type = "muckraker-stupid";
            }
        }

        int mod8Turn = rc.getRoundNum() % 8;

        if (mod8Turn == 3 || mod8Turn == 4 || mod8Turn == 5 || mod8Turn == 6) {
            if (rc.canGetFlag(ecID)) {
                lastECFlag = rc.getFlag(ecID);
            }
        }

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

        for (RobotInfo robot : nearbyRobots) {
            if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == Team.NEUTRAL) {
                standardDirection = rc.getLocation().directionTo(robot.getLocation()).opposite();
                tryStandardMove();
                break;
            } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == enemy) {
                sendLocation(enemyEC, log2(robot.getConviction()), robot.getLocation());
                break;
            } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == rc.getTeam()) {
                sendLocation(ownEC, log2(robot.getConviction()), robot.getLocation());
                break;
            } else {
                sendLocation(teamID, slandererID, rc.getLocation());
            }
        }

        if (type == "muckraker-stupid") {
            if (tryStandardMove()) {
                standardDirection = standardDirection.rotateRight().rotateRight();
            }
            // RobotInfo awayFrom = null;
            // int nearestDistance = 10;

            // for (RobotInfo robot : nearbyRobots) {
            //     if ((robot.getType() == RobotType.MUCKRAKER || robot.getType() == RobotType.POLITICIAN) && robot.getTeam() == rc.getTeam()) {
            //         if (shortestDistance(rc.getLocation(), robot.getLocation()) < nearestDistance) {
            //             nearestDistance = shortestDistance(rc.getLocation(), robot.getLocation());
            //             awayFrom = robot;
            //         }
            //     }
            // }

            // if (awayFrom != null) {
            //     standardDirection = rc.getLocation().directionTo(awayFrom.getLocation()).opposite();
            // }

            // if (tryStandardMove()) {
            //     System.out.println("I moved!");
            // }
        } else {
            if (lastECFlag / 128 / 128 / 32 == enemyEC) {
                tryMoveInDirection(rc.getLocation().directionTo(decodeLocation(lastECFlag)));
                //tryBugPath(decodeLocation(lastECFlag));
            }
            for (RobotInfo robot : nearbyRobots) {
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == enemy) {
                    sendLocation(enemyEC, log2(robot.getConviction()), robot.getLocation());
                    tryMoveInDirection(rc.getLocation().directionTo(robot.getLocation()));
                    //tryBugPath(decodeLocation(lastECFlag));
                    break;
                } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == Team.NEUTRAL) {
                    sendLocation(neutralEC, log2(robot.getConviction()), robot.getLocation());
                    break;
                } else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == rc.getTeam()) {
                    sendLocation(ownEC, log2(robot.getConviction()), robot.getLocation());
                    break;
                }
                sendLocation(teamID, muckrakerID, rc.getLocation());
            }
            tryStandardMove();
        }
    }

    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

}