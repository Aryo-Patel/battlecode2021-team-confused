package philenaRobot;
import java.util.*;
import java.util.ArrayList; 
import java.util.Collections; 
import java.util.List; 
import battlecode.common.*;
import java.util.HashMap;
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
   /*  static int numberCreated = 0;
    static int politicianFlag = 0; */
    static void runEnlightenmentCenter() throws GameActionException {
        RobotType toBuild = RobotType.POLITICIAN;/* randomSpawnableRobotType(); */
        int influence = 50;
        for (Direction dir : directions) {
            if (rc.canBuildRobot(toBuild, dir, influence)) {
                rc.buildRobot(toBuild, dir, influence);
            } else {
                break;
            }
        }
/* // Make 10 slanderers, 1 muckraker, 1 politician surround ec 
// Make 10 muckrakers, 1 politician find other ec
        int influence = 50;
        RobotType toBuild = RobotType.POLITICIAN;
        if (numberCreated == 0){
            influence = 50;
            toBuild = RobotType.POLITICIAN; //randomSpawnableRobotType();
        } else if ((numberCreated) < 9) {
            influence = 3;
            toBuild = RobotType.SLANDERER;
        } else if ((numberCreated) == 10) {
            influence = 3;
            toBuild = RobotType.MUCKRAKER;
        } else if ((numberCreated) % 10 == 0) {
            influence = 50;
            toBuild = RobotType.POLITICIAN;
        } else {
            influence = 3;
            toBuild = RobotType.MUCKRAKER;
        }
        Direction dir = directions[numberCreated % 8];
        if (rc.canBuildRobot(toBuild, dir, influence)) {
            rc.buildRobot(toBuild, dir, influence);
            numberCreated ++;
        }  */

        // RobotInfo[] nearbyRobots = rc.senseNearbyRobots(40, rc.getTeam());
        // while (nearbyRobots.length
    }

    static void runPolitician() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        int actionRadius = rc.getType().actionRadiusSquared;
        RobotInfo[] attackable = rc.senseNearbyRobots(actionRadius, enemy);
        // for (RobotInfo robot: attackable) {
        //     if (robot.getType().equals(ENLIGHTENMENT_CENTER)){ //if nearby robot is an ec
        //         if (robot.getTeam() != rc.getTeam()) { //if the ec is neutral or enemy
        //             if (0.5*(robot.getConviction()) <= rc.getConviction() && rc.canEmpower(actionRadius)){ //if politician's conviction can overturn > half of the ec's conviction and it can empower, then empower
        //                 System.out.println("empowering...");
        //                 rc.empower(actionRadius);
        //                 System.out.println("empowered");
        //                 return;
        //             }
        //             return;
        //         }
        //     }
        // }
        // Integer[] conviction = new Integer[attackable.length];
        // for (int i = 0; i < attackable.length; i ++) {
        //     conviction[i] = new Integer(attackable[i].getConviction());
            
        // }
        // List<Integer> attackableList = new ArrayList<>(Arrays.asList(conviction));
        // int lowestConviction = Collections.min(attackableList);
        HashMap<MapLocation, Direction> shiftToDirection= new HashMap<MapLocation, Direction>();
        shiftToDirection.put(new MapLocation(0,1), Direction.NORTH);
        shiftToDirection.put(new MapLocation(1,1), Direction.NORTHEAST);
        shiftToDirection.put(new MapLocation(1,0), Direction.EAST);
        shiftToDirection.put(new MapLocation(1,-1), Direction.SOUTHEAST);
        shiftToDirection.put(new MapLocation(0,-1), Direction.SOUTH);
        shiftToDirection.put(new MapLocation(-1,-1), Direction.SOUTHWEST);
        shiftToDirection.put(new MapLocation(-1,0), Direction.WEST);
        shiftToDirection.put(new MapLocation(-1,1), Direction.NORTHWEST);

        int sensorRadius = (int) Math.sqrt(rc.getType().sensorRadiusSquared);
        MapLocation location = new MapLocation(rc.getLocation().x + sensorRadius, rc.getLocation().y);
        LinkedList<MapLocation> path = bfs(location);
        System.out.println("location: " + location);
        System.out.println("path: " + path);
        while (path.size() !=0) {
            
            if (attackable.length != 0  && rc.canEmpower(actionRadius)) { //checks if any robot can die/change teams
                System.out.println("empowering...");
                rc.empower(actionRadius);
                System.out.println("empowered");
                return;
            }
            MapLocation shift = new MapLocation(path.getFirst().x - rc.getLocation().x, path.getFirst().y - rc.getLocation().y);
            
            Direction nextStep = shiftToDirection.get(shift);
            System.out.println("next step: " + nextStep);
            if (tryMove(nextStep)){
                System.out.println("I moved!");
                path.removeFirst();
            }
            Clock.yield();  
        }
        
        }
       

    static void runSlanderer() throws GameActionException {
        //how to get location of politician
        /* for (Direction dir: directions) {
            if (canMove(dir) && )
        } */
        if (tryMove(randomDirection()))
            System.out.println("I moved!");
    }

    static void runMuckraker() throws GameActionException {
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
    static final int NBITS = 7;
    static final int BITMASK = 0b1111111; //(1 << NBITS) -1; inserts nbits many zeros after it in the binary representation
 
    static void sendLocation (int extraInformation) throws GameActionException {
        MapLocation location = rc.getLocation();
        int x = location.x, y = location.y;
        int encodedLocation = (x % 128)*128 + (y % 128) + extraInformation*128*128;
        if (rc.canSetFlag(encodedLocation)) {
            rc.setFlag(encodedLocation);
        }
    }
    
    static MapLocation getLocationFromFlag(int flag) {
        int y = flag % 128;
        int x = (flag/128) % 128;
        int extraInformation = flag / 128 / 128;
 
        MapLocation currentLocation = rc.getLocation();
        int offsetX128 = currentLocation.x / 128;
        int offsetY128 = currentLocation.y / 128;
        MapLocation actualLocation = new MapLocation(offsetX128*128 + x, offsetY128*128 + y);
 
        MapLocation alternative = actualLocation.translate(-128,0);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        alternative = actualLocation.translate(128,0);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        alternative = actualLocation.translate(0,128);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        alternative = actualLocation.translate(0,-128);
        if (rc.getLocation().distanceSquaredTo(alternative) < rc.getLocation().distanceSquaredTo(actualLocation)) {
            actualLocation = alternative;
        }
        return actualLocation;
    }
    static double passabilityThreshold = 0.3;
    
    static LinkedList bfs(MapLocation location) throws GameActionException{
        //so far it wont work if passability threshold is too high, like if you can never get to location unless u go through a cell with lower passability 
        MapLocation center = rc.getLocation();
        
        //init queue + parent hashmap (which serves as "seen")
        LinkedList<MapLocation> q = new LinkedList<MapLocation>();
        HashMap<MapLocation, MapLocation> parent = new HashMap<MapLocation, MapLocation>();

        //init with center
        parent.put(center, center);
        q.add(center);

        int[] adj = new int[]{-1,0,1};

        while (q.size() !=0) {
            MapLocation first = q.getFirst();
            for (int i: adj) {
                for (int j: adj){
                    MapLocation newLocation1 = new MapLocation(first.x + i, first.y + j);
                    
                    if (!(i==0 && j==0)){
                        //MapLocation shift = new MapLocation(i,j);
                        if (!parent.containsKey(newLocation1) && rc.canSenseLocation(newLocation1) && rc.sensePassability(newLocation1) >= passabilityThreshold) { //if you haven't seen this location before, and it's within the sensing radius
                            if (!rc.isLocationOccupied(newLocation1)){
                                parent.put(newLocation1, first); //then record the parent of this location
                                q.add(newLocation1); //and add this to the queue
                            }
                    }
                    }
                    
                    /* MapLocation newLocation2 = new MapLocation(first.x, first.y + i );
                    if (!parent.containsKey(newLocation2) && rc.canSenseLocation(newLocation2) && rc.sensePassability(newLocation2) >= passabilityThreshold) { //if you haven't seen this location before, and it's within the sensing radius
                        parent.put(newLocation2, first); //then record the parent of this location
                        q.add(newLocation2); //and add this to the queue
                    } */
    
                    if (newLocation1.equals(location)) { //if we reach the location, break
                        break;
                    }
                }
            }
            q.removeFirst();
        }

        if (!parent.containsKey(location)){

        }

        LinkedList<MapLocation> path = new LinkedList<MapLocation>();
        MapLocation prev = location;
        while (!prev.equals(center)){
            path.addFirst(prev);
            prev = parent.get(prev);
        }
        //a linked list with the location after center, ..., location
        return path;
        
        


    }
}