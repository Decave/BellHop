package client;

import java.util.TimerTask;

public class ShutdownOldLinkTask extends TimerTask {

	private Client client;
	private String ip;
	private int portNum;
	
	public ShutdownOldLinkTask(Client client, String neighbor) {
		super();
		
		this.client = client;
		String[] neighborArgs = neighbor.split(":");
		
		this.ip = neighborArgs[0];
		this.portNum = Integer.parseInt(neighborArgs[1]);
	}
	
	/**
	 * When timeout (parameter of Client class) expires on the Timer registered 
	 * to this task, remove the link associated with neighbor.
	 */
	public void run() {
		client.linkdown(ip, portNum, false);
	}
}
