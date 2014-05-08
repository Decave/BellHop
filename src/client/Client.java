package client;

import java.io.BufferedReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;
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
	private String ipAddress;
	private int localPort;
	private String chunk;
	private int sequenceNumber;
	private DatagramSocket readSocket = null;
	private DatagramSocket writeSocket = null;
	private Map<String, Double[]> neighbors = null;
	private double timeout;
	private Map<String, Double[]> distanceVector = null;
	private Object distanceVectorLock = new Object();

	public Client(double timeout, String configFile) {
		try {
			this.ipAddress = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			System.err.println("Your IP address could not be found. " + 
					"Is your machine running any routing protocols?");
			e.printStackTrace();
			System.exit(1);
		}
		
		BufferedReader reader = null;
		String[] portChunkSequence = getPortChunkSequence(reader);
		this.localPort = Integer.parseInt(portChunkSequence[0]);
		this.chunk = portChunkSequence[1];
		this.sequenceNumber = Integer.parseInt(portChunkSequence[2]);
		this.neighbors = getNeighborsFromConfig(reader);


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

		this.timeout = timeout;

		this.distanceVector = 
				new TreeMap<String, Double[]>(this.neighbors);
	}

	/**
	 * Read in values from a config file with the following format:
	 * 
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
	public Map<String, Double[]> getNeighborsFromConfig(BufferedReader reader) {
		return null;
	}
	
	/**
	 * Read in values from the top line of a config file, with the following
	 * format:
	 * 
	 * 		localport timeout file_chunk_to_transfer file_sequence_number
	 * 
	 * @param reader BufferedReader reading from configFile given as argument
	 * in constructor.
	 * 
	 * @return Array of Strings representing:
	 * { Local Port, Timeout Value, Chunk to transfer, Sequence Number } 
	 */
	public String[] getPortChunkSequence(BufferedReader reader) {
		return null;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public int getLocalPort() {
		return localPort;
	}

	public void setLocalPort(int localPort) {
		this.localPort = localPort;
	}

	public String getChunk() {
		return chunk;
	}

	public void setChunk(String chunk) {
		this.chunk = chunk;
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public DatagramSocket getReadSocket() {
		return readSocket;
	}

	public void setReadSocket(DatagramSocket readSocket) {
		this.readSocket = readSocket;
	}

	public DatagramSocket getWriteSocket() {
		return writeSocket;
	}

	public void setWriteSocket(DatagramSocket writeSocket) {
		this.writeSocket = writeSocket;
	}

	public Map<String, Double[]> getNeighbors() {
		return neighbors;
	}

	public void setNeighbors(Map<String, Double[]> neighbors) {
		this.neighbors = neighbors;
	}

	public double getTimeout() {
		return timeout;
	}

	public void setTimeout(double timeout) {
		this.timeout = timeout;
	}

	public Map<String, Double[]> getDistanceVector() {
		return distanceVector;
	}

	public void setDistanceVector(Map<String, Double[]> distanceVector) {
		this.distanceVector = distanceVector;
	}

	public Object getDistanceVectorLock() {
		return distanceVectorLock;
	}

	public void setDistanceVectorLock(Object distanceVectorLock) {
		this.distanceVectorLock = distanceVectorLock;
	}
}
