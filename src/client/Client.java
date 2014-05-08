package client;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
	private double timeout;
	private Map<String, Double[]> distanceVector = null;
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

		// Open read-only Datagram Socket on port readPort
		try {
			this.readSocket = new DatagramSocket(readPort);
		} catch (SocketException e) {
			System.err.println("There was an error opening up your "
					+ "read-only Datagram Socket on port " + readPort);
			e.printStackTrace();
		}

		// Open write-only Datagram Socket on port writePort
		this.writePort = writePort;
		try {
			this.writeSocket = new DatagramSocket(this.writePort);
		} catch (SocketException e) {
			System.err.println("There was an error opening up your "
					+ "write-only DatagramSocket on port " + this.writePort);
			e.printStackTrace();
		}

		this.timeout = timeout;
	}

	/**
	 * Constructor for client object
	 * 
	 * @param timeout
	 * @param configFile
	 * @param writePort
	 * @param test
	 */
	public Client(double timeout, String configFile, int writePort, boolean isTest) {
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

		// See if Client being instantiated in a test environment.
		// If so, suppress some of the error output
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

		this.timeout = timeout;
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
	public TreeMap<String, Double[]> getNeighborsFromConfig(
			BufferedReader reader) throws IllegalArgumentException {
		if (reader == null) {
			throw new IllegalArgumentException();
		}

		TreeMap<String, Double[]> neighbors = new TreeMap<String, Double[]>();

		String next;
		String[] ipPortWeight;
		String[] ipPort;
		Double port;
		Double weight;
		try {
			while ((next = reader.readLine()) != null) {
				// {IP:Port, weight}
				ipPortWeight = next.split(" ");
				if (ipPortWeight.length != 2) {
					throw new IllegalArgumentException();
				}

				// {IP, Port}
				ipPort = ipPortWeight[0].split(":");

				if (ipPort.length != 2) {
					throw new IllegalArgumentException();
				}

				port = Double.parseDouble(ipPort[1]);
				weight = Double.parseDouble(ipPortWeight[1]);
				Double[] portWeight = new Double[3];
				portWeight[0] = port;
				portWeight[1] = weight;
				portWeight[2] = 1.0; // Link up marker
				neighbors.put(ipPort[0], portWeight);
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

	private void configureClient(BufferedReader reader) {
		String[] portChunkSequence = getPortChunkSequence(reader);
		this.readPort = Integer.parseInt(portChunkSequence[0]);
		this.timeout = Double.parseDouble(portChunkSequence[1]);
		this.chunk = portChunkSequence[2];
		this.sequenceNumber = Integer.parseInt(portChunkSequence[3]);
		this.distanceVector = getNeighborsFromConfig(reader);
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

	public int getWritePort() {
		return writePort;
	}

	public void setWritePort(int writePort) {
		this.writePort = writePort;
	}
}
