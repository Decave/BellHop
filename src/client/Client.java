package client;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 
 * @author David Vernet
 * Uni: dcv2105
 * 
 * CSEE4119 Computer Networks
 * Spring 2014 Term
 * 
 * Client class that is uniquely identified by an <IP Address / Port Number>
 * two tuple.
 */
public class Client {
	private int localPort;
	private DatagramSocket readSocket = null;
	private DatagramSocket writeSocket = null;
	private Map<InetAddress, Double[]> neighbors = null;
	private double timeout;
	private Map<InetAddress, Double[]> distanceVector = null;
	private Object distanceVectorLock = new Object();

	public Client(int localPort, double timeout) {
		this.localPort = localPort;

		// Open read-only Datagram Socket on port localPort
		try {
			this.readSocket = new DatagramSocket(localPort);
		} catch (SocketException e) {
			System.err.println("There was an error opening up your "
					+ "read-only Datagram Socket on port " + localPort);
			e.printStackTrace();
			System.exit(1);
		}

		// Open write-only Datagram Socket on port localPort
		try {
			this.writeSocket = new DatagramSocket(localPort);
		} catch (SocketException e) {
			System.err.println("There was an error opening up your "
					+ "write-only DatagramSocket on port" + localPort);
			e.printStackTrace();
			System.exit(1);
		}

		this.neighbors = getNeighborsFromConfig();

		this.timeout = timeout;

		this.distanceVector = 
				new TreeMap<InetAddress, Double[]>(this.neighbors);
	}

	/**
	 * Read in values from a config file with the following format:
	 * 
	 * 		localport timeout file_chunk_to_transfer file_sequence_number
	 * 		ipaddress1:port1 weight1
	 * 		[ipaddress2:port2 weight2]
	 * 		[...]
	 * 
	 * Where ipaddresx:portx weightx represent x neighbor's IP address, 
	 * port number, and the weight of that link. Construct and return a
	 * of the neighbors given in the config file.
	 * 
	 * @return Map from neighbors' IP addresses to links
	 */
	public Map<InetAddress, Double[]> getNeighborsFromConfig() {
		throw new UnsupportedOperationException();
	}
}
