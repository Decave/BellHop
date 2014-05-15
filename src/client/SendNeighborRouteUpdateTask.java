package client;

import java.util.TimerTask;

public class SendNeighborRouteUpdateTask extends TimerTask {
	private Client client;
	
	public SendNeighborRouteUpdateTask(Client client) {
		super();
		
		this.client = client;
	}
	
	/**
	 * When timeout (parameter of Client class) expires on the Timer registered 
	 * to this task, remove the link associated with neighbor.
	 */
	public void run() {
		client.sendRouteUpdates();
	}

}
