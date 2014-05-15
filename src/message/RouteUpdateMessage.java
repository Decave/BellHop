package message;

import java.io.Serializable;
import java.util.Map;

public class RouteUpdateMessage extends BellHopMessage implements Serializable {
	private Map<String, Double> distanceVector;
	
	private static final long serialVersionUID = 133143199831381L;
		
	/**
	 * Create a RouteUpdateMessage
	 * 
	 * @param previousHop
	 *            Sending Client represented as an IpAddress:Port String
	 * 
	 * @param destination
	 *            Receiving Client represented as an IpAddress:Port String
	 * 
	 * @param distanceVector
	 *            Map<String, Double> containing the destination Client's weight
	 *            to each other IpAddress:Port client it knows of
	 */
	public RouteUpdateMessage(String previousHop, String destination,
			Map<String, Double> distanceVector) {
		super(previousHop, destination);
		this.distanceVector = distanceVector;
	}
	
	public String getMessageType() {
		return "__ROUTE-UPDATE__";
	}

	/**
	 * Get distance vector associated with __ROUTE-UPDATE__ message
	 * 
	 * @return Map<String, Double> destination's distance vector
	 */
	public Map<String, Double> getDistanceVector() {
		return distanceVector;
	}
	
	/**
	 * Get Serial Version UID of serialized object
	 * 
	 * @return long
	 */
	public static long getSerialversionuid() {
		return serialVersionUID;
	}
}
