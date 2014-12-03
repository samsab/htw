import java.util.*;

public class Room {
	
	/** Players currently in this room. */
	protected ArrayList<ClientProxy> players;

	/** Rooms that this room is connected to. */
	protected ArrayList<Room> connected;

	/** ID number of this room. */
	protected int roomId;
	
	protected boolean hasBats;
	protected boolean hasPit;
	protected boolean hasWumpus;
	protected boolean hasLadder;
	protected int gold;
	protected int arrows;
	protected int arrowInFlight;
	
	
	/** Constructor. */
	public Room() {
		players = new ArrayList<ClientProxy>();
		connected = new ArrayList<Room>();
		hasBats = false;
		hasPit = false;
		hasWumpus = false;
		gold = 0;
		arrows = 0;
		arrowInFlight = 0;
	}
	
	/** Set this room's id number. */
	public void setIdNumber(int n) {
		roomId = n;
	}

	/** Get this room's id number. */
	public int getIdNumber() {
		return roomId;
	}
	
	/** Connect room r to this room (bidirectional). */
	public void connectRoom(Room r) {
		connected.add(r);
		r.connected.add(r);
	}
	
	/** Called when a player enters this room. */
	public synchronized void enterRoom(ClientProxy c) {
		players.add(c);
	}
	
	/** Called when a player leaves this room. */
	public synchronized void leaveRoom(ClientProxy c) {
		players.remove(c);
	}

	/** Returns a connected Room (if room is valid), otherwise returns null. */
	public Room getRoom(int room) {
		for(Room r: connected) {
			if(r.getIdNumber() == room) {
				return r;
			}
		}
		return null;
	}
	
	/** Returns a string describing what a player sees in this room. */
	public synchronized ArrayList<String> getSensed() {
		ArrayList<String> msg = new ArrayList<String>();
		msg.add("\nYou are in room " + getIdNumber());
		
		// tell the player what rooms he/she sees
		String t = "You see tunnels to rooms ";
		int c = 0;
		for(Room r : connected) {
			++c;
			if(c == connected.size()) {
				if (this != r)
					t = t.concat("and " + r.getIdNumber() + ".");
 			} else {
 				if (this != r)
 					t = t.concat("" + r.getIdNumber() + ", ");
 			}
		}
		
		// tells the player if this room has the ladder
		if (this.hasLadder)
			t = t.concat("\nYou've found the ladder!");
		
		// tells the player what is in their room
		if (this.hasBats)
			t = t.concat("\nThere are bats in this room.");
		if (this.hasPit)
			t = t.concat("\nThere isn't a floor in here.");
		if (this.hasWumpus)
			t = t.concat("\nThere is a wumpus in here.");
		
		// tells the player if there is gold or arrows in his/her room
		if (this.gold > 0)
			t = t.concat("\nSomething glimmers around your feet.");
		if (this.arrows > 0)
			t = t.concat("\nSomething poked your big toe.");
		
		
		// player senses what is in adjacent rooms
		for (Room r : connected) {
			if (r != this) {	
				if (r.hasBats)
					t = t.concat("\nYou hear flapping!");
				if (r.hasPit)
					t = t.concat("\nYou feel a breeze.");
				if (r.hasWumpus)
					t = t.concat("\nYou smell something foul.");
				if (!r.players.isEmpty())
					t = t.concat("\nYou feel another presence.");
			}
		}
		
		msg.add(t);
		return msg;
	}
}
