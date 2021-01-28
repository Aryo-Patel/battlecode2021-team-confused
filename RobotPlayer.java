package finalBot;

import battlecode.common.*;

import java.util.*;
import java.lang.Math;

public strictfp class RobotPlayer{
    static RobotController rc;

    static final Direction[] directions = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};

    static int turnCount;

    // flag bits 1-5 -- team
    static int teamID = 31, enemyEC = 30, ownEC = 29, neutralEC = 28;

    static ArrayList<Integer> teamIDs = new ArrayList<>(Arrays.asList(teamID, enemyEC, ownEC));

    // flag bits 6-10
    static int enlightenmentCenterID = 31, politicianID = 30, slandererID = 29, muckrakerID = 28;

    static int ecID = 0, lastECFlag = 0;

    static String type;

    static Direction standardDirection; // dir spawned in, walk away from EC spawned by

    static ArrayList<Integer> spawnedRobots = new ArrayList<>(); // all IDs of bots our rc has spawned

    static HashMap<MapLocation, Integer> enemyECs = new HashMap<>(); // map location of enemy EC ->
    static HashMap<MapLocation, Integer> neutralECs = new HashMap<>();


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

        int[] dx = new int[]{128, 0, 0, -128}, dy = new int[]{0, -128, 128, 0};
        for (int i = 0; i < dx.length; i++){
            MapLocation alternative = actualLocation.translate(dx[i], dy[i]);
            if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation))
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
         */
        Direction dir = rc.getLocation().directionTo(target);
        currentDistToTarget = rc.getLocation().distanceSquaredTo(target); // set every bug move
        if (rc.canMove(dir) && (rc.sensePassability(rc.getLocation().add(dir))) >= passThreshold && currentDistToTarget <= prevDistToTarget){
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

        return tryMoveInDirection(standardDirection);
    }

    static boolean isOnTeam(RobotInfo robot) throws GameActionException {
        int extraInfo = rc.getFlag(robot.getID()) / 128 / 128 / 32;
        return teamIDs.contains(extraInfo) && robot.getTeam() == rc.getTeam();
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

    static void spawnRobots(RobotType robotType, int influence) throws GameActionException{
        for (int i = 0; i < 8; i++) {
            Direction dir = directions[(((rc.getRoundNum() - 1)/2%8) + i)%8];
            if (rc.canBuildRobot(robotType, dir, influence)) {
                rc.buildRobot(robotType, dir, influence);
                spawnedRobots.add(rc.senseRobotAtLocation(rc.getLocation().add(dir)).getID());
                break;
            }
        }
    }

    static HashMap<MapLocation, Integer> findMinBuilding(HashMap<MapLocation, Integer> queueEC){
        int minConviction = 32;
        MapLocation minLocation = null;
        for (MapLocation ec: queueEC.keySet()){
            if (queueEC.get(ec) < minConviction)
                minConviction = queueEC.get(ec); minLocation = ec;
        }
        MapLocation finalMinLocation = minLocation;
        int finalMinConviction = minConviction;
        return new HashMap<MapLocation, Integer>(){{
            put(finalMinLocation, finalMinConviction);
        }};
    }

    static int influence_before_bid = -1, bidAmount = -1;
    static void runEnlightenmentCenter() throws GameActionException {
        System.out.println(rc.getRoundNum());
        int current_influence = rc.getInfluence();
        if (influence_before_bid != -1){ // we have already made a bid
            if (current_influence == influence_before_bid - bidAmount) // we won previous bid
                bidAmount *= 1.1;
            else if (current_influence == influence_before_bid - (bidAmount/2)) // highest team EC bidder but lost bid
                bidAmount *= 0.9; //this is arbitrary, 10% decrease
        }

        // updates hash maps of ECs based on flags of spawned bots
        for (int robot : spawnedRobots) {
            if (rc.canGetFlag(robot)) {
                int flag = rc.getFlag(robot), team = flag / 128 / 128 /32;
                MapLocation robotLoc = decodeLocation(flag);
                if (team == ownEC) {
                    if (enemyECs.remove(robotLoc) == null) // will return null if loc not present in enemyECs
                        neutralECs.remove(robotLoc);
                } else if (team == enemyEC) {
                    neutralECs.remove(robotLoc);
                    enemyECs.put(robotLoc, (flag / 128 / 128)%32); // if already exists, will get replaced by new val
                } else if (team == neutralEC)
                    neutralECs.put(robotLoc, (flag / 128 / 128)%32);
            }
        }

        int mod8Turn = rc.getRoundNum() % 8;
        if (mod8Turn == 7 || mod8Turn == 0) {
            RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
            int totalConviction = 0;
            int totalEnemyRobots = 0;
            int totalRobots = 1;
            for (RobotInfo robot : nearbyRobots) {
                if (robot.getTeam() == rc.getTeam().opponent()) {
                    totalConviction += robot.getConviction();
                    totalEnemyRobots++;
                }
                totalRobots++;
            }

            if (totalConviction !=0)
                spawnRobots(RobotType.POLITICIAN, GameConstants.EMPOWER_TAX + totalConviction*totalRobots/totalEnemyRobots);


            boolean setFlagToTargetEC = false;
            for (HashMap<MapLocation, Integer> queueEC: new HashMap[]{neutralECs, enemyECs}){
                if (queueEC.size() >0){
                    Map.Entry<MapLocation, Integer> ECInfo= findMinBuilding(queueEC).entrySet().iterator().next();
                    if (ECInfo.getKey() != null){
                        int team = (queueEC == neutralECs) ? neutralEC: enemyEC; // shorthand if else statement
                        sendLocation(team, ECInfo.getValue(), ECInfo.getKey()); // set flag to targeted neutral EC + send a politician over there
                        if (queueEC.equals(neutralECs))
                            spawnRobots(RobotType.POLITICIAN, (int) Math.pow(2, ECInfo.getValue()+1));
                        else
                            spawnRobots(RobotType.POLITICIAN, rc.getInfluence()/4);
                        setFlagToTargetEC = true;
                    }
                    break;
                }
            }
            if (!setFlagToTargetEC)
                sendLocation(teamID, enlightenmentCenterID, rc.getLocation()); // set default flag if didn't set to neutral or enemy EC
        }

        else if (mod8Turn == 3 || mod8Turn == 4 || mod8Turn == 5 || mod8Turn == 6) { // send mucks to lowest conviction enemy EC
            boolean setFlagToTargetEC = false;
            Map.Entry<MapLocation, Integer> ECInfo= findMinBuilding(enemyECs).entrySet().iterator().next();
            if (ECInfo.getKey() != null){
                sendLocation(enemyEC, ECInfo.getValue(), ECInfo.getKey());
                setFlagToTargetEC = true;
            }
            if (!setFlagToTargetEC)
                sendLocation(teamID, enlightenmentCenterID, rc.getLocation());
            spawnRobots(RobotType.MUCKRAKER, 1);
        }

        else if (mod8Turn == 1 || mod8Turn == 2) { // send slanderers
            sendLocation(teamID, enlightenmentCenterID, rc.getLocation());
            if (neutralECs.size() == 0)
                spawnRobots(RobotType.SLANDERER, rc.getInfluence()/8);
            else
                spawnRobots(RobotType.SLANDERER, rc.getInfluence()/24);
        }

        if (influence_before_bid == -1){ // first bid
            influence_before_bid = rc.getInfluence();
            bidAmount = rc.getInfluence()/48;
        }
        if (rc.canBid(bidAmount)) {
            rc.bid(bidAmount);
        }
    }

    static void findNearbyEC(RobotType robotType){
        if (ecID == 0){
            for (RobotInfo robot: rc.senseNearbyRobots()){
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER){
                    ecID = robot.getID();
                    standardDirection = rc.getLocation().directionTo(robot.getLocation()).opposite();
                }
            }
            if (robotType == RobotType.MUCKRAKER && rc.getRoundNum()%8 == 4)
                type = "muckraker-stupid";
        }
    }

    static void runPolitician() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        int actionRadius = rc.getType().actionRadiusSquared;
        RobotInfo[] affectable = rc.senseNearbyRobots(actionRadius), nearbyRobots = rc.senseNearbyRobots();
        findNearbyEC(RobotType.POLITICIAN);

        int mod8Turn = rc.getRoundNum() % 8;

        if (mod8Turn == 7 || mod8Turn == 0) { // find neutral ECs (if any)
            if (rc.canGetFlag(ecID))
                lastECFlag = rc.getFlag(ecID);
        }

        // empower enemy/neutral EC if any, otherwise empower enemy units
        if (affectable.length != 0 && rc.canEmpower(actionRadius)) {
            int damageDone = (int) (Math.floor(rc.getConviction() - GameConstants.EMPOWER_TAX) / affectable.length);
            int dealableDamage = 0, killableUnits = 0;
            for (RobotInfo robot : affectable) {
                if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() != rc.getTeam()) {
                    boolean empower = true;
                    if (shortestDistance(rc.getLocation(), robot.getLocation()) > 1) {
                        if (tryMoveInDirection(rc.getLocation().directionTo(robot.getLocation())))
                            break;
                        else
                            if (rc.canEmpower(actionRadius)) rc.empower(actionRadius);
                    }
                    else
                        if (rc.canEmpower(actionRadius)) rc.empower(actionRadius);
                    break;
                }
                else if (robot.getTeam() != rc.getTeam() && robot.getConviction() < damageDone) {
                    killableUnits++;
                    dealableDamage += damageDone;
                }
            }
            if (rc.canEmpower(actionRadius) && ((dealableDamage > 0.7*rc.getConviction()) || (killableUnits > 5)))
                rc.empower(actionRadius);
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
            }
            else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == enemy) {
                sendLocation(enemyEC, log2(robot.getConviction()), robot.getLocation());
                break;
            }
            else if (robot.getType() == RobotType.ENLIGHTENMENT_CENTER && robot.getTeam() == rc.getTeam()) {
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
        findNearbyEC(RobotType.SLANDERER);

        int mod8Turn = rc.getRoundNum() % 8;

        if (mod8Turn == 1 || mod8Turn == 2) {
            if (rc.canGetFlag(ecID))
                lastECFlag = rc.getFlag(ecID);
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
            }
            else
                sendLocation(teamID, slandererID, rc.getLocation());
        }

        RobotInfo nearestEnemyMuckraker = null, nearestEnemy = null;
        int nearestMuckrakerDistance = 10, nearestDistance = 10;

        for (RobotInfo robot : nearbyRobots) {
            if (robot.getTeam() == enemy) {
                int shortestDist = shortestDistance(rc.getLocation(), robot.getLocation());
                if (shortestDist < nearestDistance)
                    nearestDistance = shortestDist; nearestEnemy = robot;
                if (shortestDist < nearestMuckrakerDistance && robot.getType() == RobotType.MUCKRAKER )
                    nearestEnemyMuckraker = robot; nearestMuckrakerDistance = shortestDist;
            }
        }

        if (nearestEnemyMuckraker != null)
            standardDirection = rc.getLocation().directionTo(nearestEnemyMuckraker.getLocation()).opposite();
        else if (nearestEnemy != null)
            standardDirection = rc.getLocation().directionTo(nearestEnemy.getLocation()).opposite();

        if (tryStandardMove())
            System.out.println("I moved!");
    }

    static void runMuckraker() throws GameActionException {
        System.out.println(rc.getRoundNum());
        Team enemy = rc.getTeam().opponent();
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        findNearbyEC(RobotType.MUCKRAKER);

        int mod8Turn = rc.getRoundNum() % 8;

        if (mod8Turn == 3 || mod8Turn == 4 || mod8Turn == 5 || mod8Turn == 6) {
            if (rc.canGetFlag(ecID))
                lastECFlag = rc.getFlag(ecID);
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
            }
            else
                sendLocation(teamID, slandererID, rc.getLocation());
        }

        if (type.equals("muckraker-stupid")){
            if (tryStandardMove())
                standardDirection = standardDirection.rotateRight().rotateRight();
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


}