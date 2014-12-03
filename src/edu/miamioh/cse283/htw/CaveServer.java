import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * The CaveServer class takes the following command-line parameters:
 * 
 * <Hostname of CaveSystemServer> <port number of CaveSystemServer> <port number of this CaveServer>
 * 
 * E.g., "localhost 1234 2000" 
 */
public class CaveServer {

	/** Port base for this cave server. */
	protected int portBase;

	/** Socket for accepting connections from players. */
	protected ServerSocket clientSocket;

	/** Proxy to the CaveSystemServer. */
	protected CaveSystemServerProxy caveSystem;

	/** Random number generator (used to pick caves for players). */
	protected Random rng;

	/** Rooms in this CaveServer. */
	protected ArrayList<Room> rooms;
	
	protected int wumpusRoom;

	/** Constructor. */
	public CaveServer(CaveSystemServerProxy caveSystem, int portBase) {
		this.caveSystem = caveSystem;
		this.portBase = portBase;
		this.rng = new Random();

		// construct the rooms:
		rooms = new ArrayList<Room>();
		for(int i=0; i<20; ++i) {
			rooms.add(new Room());
			rooms.get(i).setIdNumber(i);
		}

		// connect them to each other:
		for(int i=0; i<20; ++i) {
			rooms.get(i).connectRoom(rooms.get((i+1)%20));
			rooms.get(i).connectRoom(rooms.get((i+2)%20));
			rooms.get(i).connectRoom(rooms.get((i+3)%20));
		}
		
		// assign the ladder
		rooms.get(rng.nextInt(20)).hasLadder = true;
		
		// assign bats
		for (int i = 0; i < 20; i++) {
			if (rng.nextInt(101) < 15)
				if (rooms.get(i).hasLadder == false)
					rooms.get(i).hasBats = true;
		}
		
		// assign pits
		for (int i = 0; i < 20; i++) {
			if (rng.nextInt(101) < 12)
				if (rooms.get(i).hasLadder == false)
					rooms.get(i).hasPit = true;
		}
		
		// assign the wumpus
		int wumpusRoom = rng.nextInt(20);
		rooms.get(wumpusRoom).hasWumpus = true;
	}

	/** Returns the port number to use for accepting client connections. */
	public int getClientPort() { return portBase; }

	/** Returns an initial room for a client. */
	public synchronized Room getInitialRoom() {
		return rooms.get(rng.nextInt(rooms.size()));
	}

	/** This is the thread that handles a single client connection. */
	public class ClientThread implements Runnable {
		/** This is our "client" (actually, a proxy to the network-connected client). */
		protected ClientProxy client;

		/** Notification messages. */
		protected ArrayList<String> notifications;

		/** Whether this player is alive. */
		protected boolean alive;
		
		protected int gold;
		protected int arrows;		

		/** Constructor. */
		public ClientThread(ClientProxy client) {
			this.client = client;
			this.notifications = new ArrayList<String>();
			this.alive = true;
			this.gold = 0;
			this.arrows = 3;
		}

		/** Returns true if there are notifications that should be sent to this client. */
		public synchronized boolean hasNotifications() {
			return !notifications.isEmpty();
		}

		/** Adds a message to the notifications. */
		public synchronized void addNotification(String msg) {
			notifications.add(msg);
		}

		/** Returns and resets notification messages. */
		public synchronized ArrayList<String> getNotifications() {
			ArrayList<String> t = notifications;
			notifications = new ArrayList<String>();
			return t;
		}

		/** Returns true if the player is alive. */
		public synchronized boolean isAlive() {
			return alive;
		}

		/** Kills this player. */
		public synchronized void kill() {
			alive = false;
		}

		/** Play the game with this client.
		 */
		public void run() {
			try {
				// the first time a player connects, send a welcome message:
				ArrayList<String> welcome = new ArrayList<String>();
				welcome.add("Welcome!");
				welcome.add("Commands: \n1. (m)ove <Number> \n2. (s)hoot <Number> \n3. (p)ickup \n4. (cl)imb");
				welcome.add("Grab the gold and escape! Beware the wumpus!\n");
				client.sendNotifications(welcome);

				// Put the player in an initial room and send them their initial
				// sensory information:
				Room r = getInitialRoom();
				r.enterRoom(client);
				client.sendSenses(r.getSensed());

				// while the player is alive, listen for commands from the player
				// and for activities elsewhere in the cave:
				try {
					while(true) {					
						// poll, waiting for input from client or other notifications:
						while(!client.ready() && !hasNotifications() && isAlive()) {
							try { Thread.sleep(50);	} catch (InterruptedException ex) {	}
						}

						// if there are notifications, send them:
						if(hasNotifications()) {
							client.sendNotifications(getNotifications());
						}

						// if the player is dead, send the DIED message and break:
						if(!isAlive()) {
							client.died();
							break;
						}

						// if the player did something, respond to it:
						if(client.ready()) {
							String line = client.nextLine().trim();

							if(line.startsWith(Protocol.MOVE_ACTION)) {
								// move the player: split out the room number, move the player, etc.
								// client has to leave the room: r.leaveRoom(client)
								// and enter the new room: newRoom.enterRoom(client)
								// send the client new senses here: client.sendSenses(r.getSensed());
								
								String[] action = line.split(" ");
								int roomNumber = Integer.parseInt(action[2]);
								
								ArrayList<String> response = new ArrayList<String> ();
								if (r.getRoom(roomNumber) != null) {
									r.leaveRoom(client);
									r = r.getRoom(roomNumber);
									r.enterRoom(client);
									client.sendSenses(r.getSensed());
									
									if (r.hasPit) {
										
										response.add("You've fallen into a pit and died. Nice job.");
										client.sendNotifications(response);
										r.leaveRoom(client);
										client.died();
									}
									
									else if (r.hasBats) {
										
										response.add("You've been teleported to a random room by pesky bats!");
										client.sendNotifications(response);
										
										int randomRoom = rng.nextInt(20);
										r.leaveRoom(client);
										r = rooms.get(randomRoom);
										r.enterRoom(client);
										
										client.sendSenses(r.getSensed());
										
									}
									
									if (r.hasWumpus) {
										
										response.add("You have been killed by the Wumpus.");
										client.sendNotifications(response);
										
										r.gold += gold;
										gold = 0;
										r.arrows += arrows;
										arrows = 0;
										r.leaveRoom(client);
										client.died();
									}
									
									int newWumpRoom;
									do {
										newWumpRoom = rng.nextInt(20);
									} while (rooms.get(wumpusRoom).getRoom(newWumpRoom) == null);
									
									rooms.get(wumpusRoom).hasWumpus = false;
									rooms.get(newWumpRoom).hasWumpus = true;
									wumpusRoom = newWumpRoom;				
									
								}
								
								else {
									response.add("You can't move to that room!");
									
									client.sendNotifications(response);
								}

							} else if(line.startsWith(Protocol.SHOOT_ACTION)) {
								// shoot an arrow: split out the room number into which the arrow
								// is to be shot, and then send an arrow into the right series of
								// rooms.
								
								String[] action = line.split(" ");
								int roomNumber = Integer.parseInt(action[2]);
								ArrayList<String> response = new ArrayList<String> ();
								
								if (arrows > 0) {
									if (r.getRoom(roomNumber) != null) {
										r.getRoom(roomNumber).arrowInFlight += 1;
										arrows -= 1;
										response.add("Shots fired!");
										
										if (r.getRoom(roomNumber).hasWumpus && r.getRoom(roomNumber).arrowInFlight > 0) {
											response.add("You've killed the wumpus!");
											client.sendNotifications(response);
											
											r.getRoom(roomNumber).hasWumpus = false;
											r.getRoom(roomNumber).gold += 500;
											r.getRoom(roomNumber).arrows +=1;
											r.getRoom(roomNumber).arrowInFlight -= 1;
											
										}
										
										if (!r.getRoom(roomNumber).players.isEmpty()) {
											for (ClientProxy c : r.getRoom(roomNumber).players) {
												response.add("You were shot by another player. You are dead.");
												c.sendNotifications(response);
												r.getRoom(roomNumber).leaveRoom(c);
												c.died();
											}
											
										}
											
									} else 
										response.add("Invalid room!");
										
									client.sendNotifications(response);
								} else {
									response.add("You don't have any arrows!");
									client.sendNotifications(response);
								}
								
								if (r.getRoom(roomNumber) != null)
									r.getRoom(roomNumber).arrowInFlight -= 1;
								
							} else if(line.startsWith(Protocol.PICKUP_ACTION)) {
								// pickup gold / arrows.
								ArrayList<String> pickupStuff = new ArrayList<String> ();
								
								if (r.gold > 0) {
									gold += r.gold;
									pickupStuff.add("You found " + r.gold + " gold!");
									r.gold = 0;
								}
								
								if (r.arrows > 0) {
									arrows += r.arrows;
									pickupStuff.add("You found " + r.arrows + " arrows!");
									r.arrows = 0;
								}
								
								if (pickupStuff.isEmpty())
									pickupStuff.add("Nothing to pick up.");
								
								client.sendNotifications(pickupStuff);
								
							} else if(line.startsWith(Protocol.CLIMB_ACTION)) {
								// climb the ladder, if the player is in a room with a ladder.
								// send a notification telling the player his score
								// and some kind of congratulations, and then kill
								// the player to end the game -- call kill(), above.
								
								if (!r.hasLadder) {
									ArrayList<String> noLadder = new ArrayList<String> ();
									noLadder.add("There isn't a ladder in here!");
									client.sendNotifications(noLadder);
								}
								
								else {
									ArrayList<String> endGameMsg = new ArrayList<String> ();
									endGameMsg.add("\nCongratulations! You escaped!");
									endGameMsg.add("\nYou collected "  + gold + " gold.");
									endGameMsg.add("\nGoodbye!");
									client.sendNotifications(endGameMsg);
									r.leaveRoom(client);
									kill();
								}
								
							} else if(line.startsWith(Protocol.QUIT)) {
								// no response: drop gold and arrows, and break.
								r.gold += gold;
								gold = 0;
								r.arrows += arrows;
								arrows = 0;
								
								break;

							} else {
								// invalid response; send the client some kind of error message
								// (as a notificiation).
								ArrayList<String> invalidInput = new ArrayList<String> ();
								invalidInput.add("Invalid command.");
								client.sendNotifications(invalidInput);
								
							}
						}
					}
				} finally {
					// make sure the client leaves whichever room they're in,
					// and close the client's socket: 
					
					r.leaveRoom(client);
					client.close();
				}
			} catch(Exception ex) {
				// If an exception is thrown, we can't fix it here -- Crash.
				ex.printStackTrace();
				System.exit(1);
			}
		}
	}

	/** Runs the CaveSystemServer. */
	public void run() {
		try {
			// first thing we need to do is register this CaveServer
			// with the CaveSystemServer:
			clientSocket = new ServerSocket(getClientPort());
			caveSystem.register(clientSocket);
			System.out.println("CaveServer registered");

			// then, loop forever accepting Client connections:
			while(true) {
				ClientProxy client = new ClientProxy(clientSocket.accept());
				System.out.println("Client connected");
				(new Thread(new ClientThread(client))).start();
			}
		} catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	/** Main method (run the CaveServer). */
	public static void main(String[] args) {
		try {
			InetAddress addr=InetAddress.getByName("localhost");
			int cssPortBase=1234;
			int cavePortBase=2000;

			if(args.length > 0) {
				addr = InetAddress.getByName(args[0]);
				cssPortBase = Integer.parseInt(args[1]);
				cavePortBase = Integer.parseInt(args[2]);
			}

			// first, we need our proxy object to the CaveSystemServer:
			CaveSystemServerProxy caveSystem = new CaveSystemServerProxy(new Socket(addr, cssPortBase+1));

			// now construct this cave server, and run it:
			CaveServer cs = new CaveServer(caveSystem, cavePortBase);
			cs.run();
		} catch(IOException ex) {
			ex.printStackTrace();
		}
	}
}
