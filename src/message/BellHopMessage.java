package message;

import java.io.Serializable;
import java.util.Date;

/**
 * BellHop abstract for getting the type of message being sent between
 * Clients, as well as the sending and destination client, and the 
 * Date that the message was composed (not sent).
 * 
 * @author David Vernet
 *
 */
public abstract class BellHopMessage implements Serializable {

	private String previousHop;
	private String destination;
	private Date date;
	
	private static final long serialVersionUID = 13877183454918L;

	public BellHopMessage(String previousHop, String destination) {
		this.previousHop = previousHop;
		this.destination = destination;
	}
	
	/**
	 * Get sender of packet
	 * 
	 * @return String Sender of __ROUTE-UPDATE__ message
	 */
	public String getPreviousHop() {
		return previousHop;
	}

	/**
	 * Get destination of packet
	 * 
	 * @return String Recipient of __ROUTE-UPDATE__ message
	 */
	public String getDestination() {
		return destination;
	}

	/**
	 * Get date and time at which message was sent 
	 * 
	 * @return Date
	 */
	public Date getDate() {
		return date;
	}
	
	/**
	 * Get Serial Version UID of serialized object
	 * 
	 * @return long
	 */
	public static long getSerialversionuid() {
		return serialVersionUID;
	}
	
	/**
	 * Get type of message being sent, in String form
	 * 
	 * @return String
	 */
	public abstract String getMessageType();
}
