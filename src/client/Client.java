package client;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.TreeMap;

/**
 * 
 * @author David Vernet Uni: dcv2105
 * 
 *         CSEE4119 Computer Networks Spring 2014 Term
 * 
 *         Client class that is uniquely identified by an <IP Address / Port
 *         Number> two tuple.
 */
public class Client {
	private String ipAddress;
	private int readPort;
	private int writePort;
	private String chunk;
	private int sequenceNumber;
	private DatagramSocket readSocket = null;
	private DatagramSocket writeSocket = null;
	private BufferedReader stdIn = null;
	private double timeout;
	private Map<String, Double> distanceVector = null;
	private Object distanceVectorLock = new Object();
	private boolean isTest = false;

	/**
	 * Constructor for client object
	 * 
	 * @param timeout
	 * @param configFile
	 * @param writePort
	 */
	public Client(double timeout, String configFile, int writePort) {
		this.constructBasicClient(timeout, configFile, writePort, isTest);
	}

	/**
	 * Constructor for client object
	 * 
	 * @param timeout
	 * @param configFile
	 * @param writePort
	 * @param isTest
	 */
	public Client(double timeout, String configFile, int writePort,
			boolean isTest) {
		this.constructBasicClient(timeout, configFile, writePort, isTest);
	}

	/**
	 * Read in values from a config file with the following format:
	 * 
	 * ipaddress1:port1 weight1 [ipaddress2:port2 weight2] [...]
	 * 
	 * Where ipaddresx:portx weightx represent x neighbor's IP address, port
	 * number, and the weight of that link. Construct and return a of the
	 * neighbors given in the config file.
	 * 
	 * @return Map from neighbors' IP addresses to links
	 */
	public TreeMap<String, Double> getNeighborsFromConfig(BufferedReader reader)
			throws IllegalArgumentException {
		if (reader == null) {
			throw new IllegalArgumentException();
		}

		TreeMap<String, Double> neighbors = new TreeMap<String, Double>();

		String next;
		String[] ipPortWeight;
		try {
			while ((next = reader.readLine()) != null) {
				// {IP:Port, weight}
				ipPortWeight = next.split(" ");
				if (ipPortWeight.length != 2 || !ipPortWeight[0].contains(":")) {
					throw new IllegalArgumentException();
				}

				neighbors.put(ipPortWeight[0],
						Double.parseDouble(ipPortWeight[1]));
			}
		} catch (IOException e) {
			System.err.println("Error reading in config file");
			e.printStackTrace();
		}

		return neighbors;
	}

	/**
	 * Read in values from the top line of a config file, with the following
	 * format:
	 * 
	 * localport timeout file_chunk_to_transfer file_sequence_number
	 * 
	 * @param reader
	 *            BufferedReader reading from configFile given as argument in
	 *            constructor.
	 * 
	 * @return Array of Strings representing: { Local Port, Timeout Value, Chunk
	 *         to transfer, Sequence Number }
	 */
	public String[] getPortChunkSequence(BufferedReader reader)
			throws IllegalArgumentException {
		if (reader == null) {
			throw new IllegalArgumentException();
		}

		String header = null;
		try {
			header = reader.readLine().trim();
		} catch (IOException e) {
			System.err.println("error reading in config file");
			e.printStackTrace();
		}
		if (header == null || header.equals("")) {
			throw new IllegalArgumentException();
		}

		/*
		 * If header is a non-empty string, split it into an array, and return
		 * if array is properly formatted (size 4).
		 */
		String[] ret = header.split(" ");
		if (ret.length != 4) {
			throw new IllegalArgumentException();
		} else {
			return ret;
		}
	}

	/**
	 * Given an IP address and port, if this Client has a neighbor in their
	 * distance vector with a key corresponding to IP:Port, set the weight of
	 * that link to Double.POSITIVE_INFINITY.
	 * 
	 * @param linkIP
	 * @param linkPort
	 * @return True if link exists and is dropped, false if it does not exist.
	 * @throws IllegalArgumentException
	 */
	public boolean linkdown(String linkIP, int linkPort)
			throws IllegalArgumentException {
		synchronized (this.distanceVectorLock) {
			if (linkIP == null || linkIP.equals("") || linkPort <= 0) {
				throw new IllegalArgumentException();
			}

			String ipPort = linkIP + ":" + linkPort;

			if (!this.distanceVector.containsKey(ipPort)) {
				return false;
			}

			distanceVector.put(ipPort, Double.POSITIVE_INFINITY);
			return true;
		}
	}

	/**
	 * Given an IP address and port, if this Client has a neighbor in their
	 * distance vector with a key corresponding to IP:Port, and if the weight 
	 * associated with that link is infinity, set the weight of that link 
	 * to weight parameter.
	 * 
	 * @param linkIP
	 * @param linkPort
	 * @param weight
	 * @return True if link exists and is down, false otherwise.
	 * @throws IllegalArgumentException
	 */
	public boolean linkup(String linkIP, int linkPort, double weight)
			throws IllegalArgumentException {
		synchronized (this.distanceVectorLock) {
			if (linkIP == null || linkIP.equals("") || linkPort <= 0
					|| weight < 0) {
				throw new IllegalArgumentException();
			}

			String ipPort = linkIP + ":" + linkPort;

			if (!this.distanceVector.containsKey(ipPort) || 
					this.distanceVector.get(ipPort) != 
					Double.POSITIVE_INFINITY) {
				return false;
			} else {
				this.distanceVector.put(linkIP, weight);
				return true;
			}
		}
	}

	/**
	 * Helper method to construct basic Client. This method applies to all
	 * constructors because it has the minimum requirements needed for a Client
	 * to operate.
	 * 
	 * @param timeout
	 * @param configFile
	 * @param writePort
	 * @param isTest
	 */
	private void constructBasicClient(double timeout, String configFile,
			int writePort, boolean isTest) {
		try {
			this.ipAddress = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			System.err.println("Your IP address could not be found. "
					+ "Is your machine running any routing protocols?");
			e.printStackTrace();
			System.exit(1);
		}

		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(configFile));
			this.configureClient(reader);
		} catch (FileNotFoundException e) {
			System.err.println("Config file not found. Could not "
					+ "instantiate Client object");
			e.printStackTrace();
		}

		this.isTest = isTest;

		// Open read-only Datagram Socket on port readPort
		try {
			this.readSocket = new DatagramSocket(readPort);
		} catch (SocketException e) {
			if (!isTest) {
				System.err.println("There was an error opening up your "
						+ "read-only Datagram Socket on port " + readPort);
				e.printStackTrace();
			}
		}

		// Open write-only Datagram Socket on port writePort
		this.writePort = writePort;
		try {
			this.writeSocket = new DatagramSocket(this.writePort);
		} catch (SocketException e) {
			if (!isTest) {
				System.err
				.println("There was an error opening up your "
						+ "write-only DatagramSocket on port "
						+ this.writePort);
				e.printStackTrace();
			}
		}

		this.stdIn = new BufferedReader(new InputStreamReader(System.in));
		this.timeout = timeout;
	}

	/**
	 * Perform operations to read from given config file, and populate Client
	 * fields accordingly.
	 * 
	 * @param reader
	 */
	private void configureClient(BufferedReader reader) {
		String[] portChunkSequence = getPortChunkSequence(reader);
		this.readPort = Integer.parseInt(portChunkSequence[0]);
		this.timeout = Double.parseDouble(portChunkSequence[1]);
		this.chunk = portChunkSequence[2];
		this.sequenceNumber = Integer.parseInt(portChunkSequence[3]);
		this.distanceVector = getNeighborsFromConfig(reader);
	}

	/**
	 * Given an IP:Port, check whether the corresponding weight in a Client's
	 * distance vector (if the link exists), is equal to the weight parameter. 
	 * @param ipPort
	 * 
	 * @param weight
	 * 
	 * @return True if link exists and associated weight is equal to weight, 
	 * false otherwise.
	 */
	public boolean distanceVectorHasWeight(String ipPort, double weight) {
		if (!distanceVector.containsKey(ipPort)) {
			return false;
		} else {
			return distanceVector.get(ipPort) == weight;
		}
	}

	/**
	 * Given an IP:Port, check whether a client's distance vector contains a
	 * corresponding link.
	 * 
	 * @param ipPort
	 * 
	 * @return True if Client's vector contains an entry corresponding to 
	 * ipPort, false otherwise.
	 */
	public boolean hasLink(String ipPort) {
		return distanceVector.containsKey(ipPort);
	}
	
	/**
	 * Close Client program, and exit with a (successful) status of 0.
	 */
	public void close() {
		System.out.println("Exiting now...");
		System.exit(0);
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public int getReadPort() {
		return readPort;
	}

	public void setReadPort(int readPort) {
		this.readPort = readPort;
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

	public double getTimeout() {
		return timeout;
	}

	public void setTimeout(double timeout) {
		this.timeout = timeout;
	}

	public Map<String, Double> getDistanceVector() {
		return distanceVector;
	}

	public void setDistanceVector(Map<String, Double> distanceVector) {
		this.distanceVector = distanceVector;
	}

	public Object getDistanceVectorLock() {
		return distanceVectorLock;
	}

	public void setDistanceVectorLock(Object distanceVectorLock) {
		this.distanceVectorLock = distanceVectorLock;
	}

	public int getWritePort() {
		return writePort;
	}

	public void setWritePort(int writePort) {
		this.writePort = writePort;
	}
}
