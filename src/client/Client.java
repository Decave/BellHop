package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
	private String localClientID;
	private File chunkFile;
	private int sequenceNumber;
	private DatagramChannel channel = null;
	private double timeout;
	private Set<String> neighbors = new HashSet<String>();
	private Map<String, Map<String, Double>> distanceVector = null;
	private Object distanceVectorLock = new Object();
	private boolean isTest = false;
	private Map<String, String[]> routingTable = new TreeMap<String, String[]>();
	private ByteBuffer writeBuffer = null;
	private byte[] chunkBytes = null;

	/**
	 * Constructor for Client object that sets isTest to false.
	 * 
	 * @param timeout
	 * @param configFile
	 */
	public Client(double timeout, String configFile) {
		this.constructBasicClient(timeout, configFile, false);
	}

	/**
	 * Constructor for Client object that allows isTest to be false.
	 * 
	 * @param timeout
	 * @param configFile
	 * @param isTest
	 */
	public Client(double timeout, String configFile, boolean isTest) {
		this.constructBasicClient(timeout, configFile, isTest);
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

		TreeMap<String, Double> neighborsWithWeights = new TreeMap<String, Double>();

		String next;
		String[] ipPortWeight;
		try {
			while ((next = reader.readLine()) != null) {
				// {IP:Port, weight}
				ipPortWeight = next.split(" ");
				if (ipPortWeight.length != 2 || !ipPortWeight[0].contains(":")) {
					throw new IllegalArgumentException();
				}

				neighborsWithWeights.put(ipPortWeight[0],
						Double.parseDouble(ipPortWeight[1]));
			}
		} catch (IOException e) {
			System.err.println("Error reading in config file");
			e.printStackTrace();
		}

		this.neighbors = neighborsWithWeights.keySet();

		return neighborsWithWeights;
	}

	/**
	 * Given a Map of <IPPort, weights> to neighbors (they are read in from the
	 * initial config file), create the initial DV. By default, set all the
	 * weights of your neighbors' DV entries to infinity.
	 * 
	 * @param neighbors
	 * 
	 * @return The newly created DV
	 */
	public Map<String, Map<String, Double>> createDVFromNeighbors(
			Map<String, Double> neighbors) {
		this.distanceVector = new TreeMap<String, Map<String, Double>>();

		for (String neighbor : neighbors.keySet()) {
			distanceVector.put(neighbor, new TreeMap<String, Double>());
			distanceVector.get(neighbor).put(localClientID,
					Double.POSITIVE_INFINITY);
			for (String link : neighbors.keySet()) {
				distanceVector.get(neighbor)
						.put(link, Double.POSITIVE_INFINITY);
			}
		}

		distanceVector.put(localClientID, neighbors);
		distanceVector.get(localClientID).put(localClientID, 0.0);

		return this.distanceVector;
	}

	/**
	 * Given another node's new Distance Vector, update our own DV and our
	 * routing table.
	 * 
	 * @param ipPort
	 * @param other
	 */
	public void updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
			String ipPort, Map<String, Double> other) {

		if (!distanceVector.containsKey(ipPort)) {
			/*
			 * First, account for case of when ipPort is not already an entry in
			 * the current distance vector
			 */

			for (String entry : distanceVector.keySet()) {
				/*
				 * Update the entries in our DV to include this new DV with a
				 * weight of infinity. Similarly, if the new DV doesn't have an
				 * entry pertaining to any of our neighbors, add it with a
				 * weight of infinity.
				 */
				if (!other.containsKey(entry)) {
					distanceVector.get(entry).put(ipPort,
							Double.POSITIVE_INFINITY);
				}
				distanceVector.get(entry).put(ipPort, Double.POSITIVE_INFINITY);
			}

		}

		for (String entry : other.keySet()) {
			/*
			 * Next, account for the case that an entry in the other is not
			 * included in our distance vector as well
			 */
			if (!distanceVector.keySet().contains(entry)) {
				distanceVector.put(entry, new TreeMap<String, Double>());
				for (String currentEntry : distanceVector.keySet()) {
					if (!currentEntry.equals(ipPort)) {
						distanceVector.get(currentEntry).put(entry,
								Double.POSITIVE_INFINITY);
					}
					distanceVector.get(entry).put(currentEntry,
							Double.POSITIVE_INFINITY);
				}
				distanceVector.get(entry).put(entry, Double.POSITIVE_INFINITY);
			}
		}

		/*
		 * Once we're here, we know that our distance vector is "square" (i.e.
		 * every entry has a weight for every other entry, even if it is
		 * infinity). Now, we adjust our current distance vector to account for
		 * the new distance vector's entries.
		 */
		distanceVector.put(ipPort, other);

		// Our distance to ipPort
		double weightToIPPort = distanceVector.get(localClientID).get(ipPort);
		Double newWeight;
		/*
		 * For every entry in our distance vector:
		 */
		for (String link : distanceVector.get(localClientID).keySet()) {
			// Distance from local Client to link
			double currentWeight = distanceVector.get(localClientID).get(link);

			// Distance from ipPort client to link
			double otherWeight = distanceVector.get(ipPort).get(link);

			// Distance from local client to IPPort + ipPort to link
			newWeight = otherWeight + weightToIPPort;
			String[] routingEntry = new String[2];

			/*
			 * If the routing table contains a destination to current key in the
			 * for loop, then adjust routing table and client's DV accordingly:
			 */
			if (routingTable.containsKey(link)) {

				// Current routing entry for link
				routingEntry = routingTable.get(link);

				/*
				 * If the next hop to that destination is the host who's DV has
				 * changed:
				 */
				if (routingEntry[0].equals(ipPort)) {
					/*
					 * Then update our distance vector's entry corresponding to
					 * link to automatically include newWeight, because the
					 * weight of that entry necessarily depends on ipPort.
					 * Similarly, update routing table's entry for link.
					 */
					distanceVector.get(localClientID).put(link, newWeight);
					routingEntry[1] = newWeight.toString();
					routingTable.put(link, routingEntry);
					/*
					 * Otherwise, though the routing table has a destination
					 * corresponding to link, the next hop is not ipPort.
					 */
				} else {

					/*
					 * Another possibility we must account for is if our DV with
					 * a new distance vector corresponds to our local client. If
					 * so, we must adjust the weight to this new link to reflect
					 * the new weight of next hop, because it is possible that
					 * we do not have a link to this node directly from the
					 * local client, or that we visit another neighbor first. Of
					 * course, if the next hop to the link is equal to itself,
					 * we skip it.
					 */
					if (ipPort.equals(localClientID)
							&& !routingEntry[0].equals(link)) {
						newWeight = distanceVector.get(localClientID).get(
								routingEntry[0])
								+ distanceVector.get(routingEntry[0]).get(link);
						routingEntry[1] = newWeight.toString();
						distanceVector.get(localClientID).put(link, newWeight);
						routingTable.put(link, routingEntry);
						continue;
					}
				}
			} else if (newWeight < currentWeight) {
				distanceVector.get(localClientID).put(link, newWeight);

				routingEntry[0] = ipPort;
				routingEntry[1] = newWeight.toString();
				routingTable.put(link, routingEntry);
			}
		}

		routingTable = updateRoutingTableFromCurrentDV();
	}

	public Map<String, String[]> updateRoutingTableFromCurrentDV() {
		/*
		 * Finally, have a nested for loop, where we iterate through each
		 * destination in the routing table, and check whether there is a better
		 * predecessor entry within our neighbors than what is already there.
		 */
		Double currentDestinationWeight;
		Double neighborToDestinationWeight;
		Double weightToNeighbor;
		Double newWeight;
		String[] currentDestinationEntry;
		String[] newDestinationEntry;
		for (String destination : routingTable.keySet()) {
			currentDestinationEntry = routingTable.get(destination);
			currentDestinationWeight = Double
					.parseDouble(currentDestinationEntry[1]);
			for (String neighbor : neighbors) {
				newDestinationEntry = new String[2];
				if (neighbors.equals(destination)) {
					continue;
				} else {
					neighborToDestinationWeight = distanceVector.get(neighbor)
							.get(destination);
					weightToNeighbor = distanceVector.get(localClientID).get(
							neighbor);
					if (neighborToDestinationWeight + weightToNeighbor < currentDestinationWeight) {
						newDestinationEntry[0] = neighbor;
						newWeight = weightToNeighbor
								+ neighborToDestinationWeight;
						currentDestinationEntry[1] = newWeight.toString();
						routingTable.put(destination, newDestinationEntry);
					}
				}
			}
		}

		return routingTable;
	}

	/**
	 * Create this Client's routing table from it's initial Distance Vector
	 */
	public Map<String, String[]> createRoutingTableInitialDV() {
		if (this.distanceVector == null
				|| this.distanceVector.keySet().size() < 1) {
			if (!isTest) {
				System.err
						.println("You have no neighbors, and therefore "
								+ "cannot build a routing table. It is possible that "
								+ "you tried to build a routing table before you had "
								+ "loaded your neighbors from the config file. Exiting "
								+ "program.");
			}
			return null;
		}

		/*
		 * For each neighbor in our DV, add a destination entry to the routing
		 * table, and make that neighbor the next hop. The weight to the
		 * neighbor is the weight given in the distance vector for our client.
		 * Remark that at this point, we expect each neighbor's DV to have
		 * weights of infinity, so we only use the weights from our DV. When we
		 * later get ROUTE_UPDATE commands, we'll update our routing table from
		 * all the DVs.
		 */
		String[] tableEntry;
		Double weightToEntry;
		for (String ipPort : this.distanceVector.get(localClientID).keySet()) {
			tableEntry = new String[2];
			tableEntry[0] = ipPort;

			if (ipPort.equals(localClientID)) {
				weightToEntry = 0.0;
			} else {
				weightToEntry = distanceVector.get(localClientID).get(ipPort);
			}
			tableEntry[1] = weightToEntry.toString();
			routingTable.put(ipPort, tableEntry);
		}

		return this.routingTable;
	}

	/**
	 * Given a distance vector of neighbors and weights, update the routing
	 * table.
	 * 
	 * @param distanceVector
	 *            A Map from IP:Port strings to weights, signifying a link to a
	 *            neighbor and the associated weight.
	 * @return A new routing table that has been updated from the given distance
	 *         vector.
	 */
	public Map<String, Map<String, Double>> updateRoutingTableFromDistanceVector(
			Map<String, Double> distanceVector) {
		throw new UnsupportedOperationException();
	}

	public static void main(String[] args) {
		if (args.length < 1 || args.length > 2
				|| !args[1].contains("--timeout=")) {
			System.err.println("Usage: bfclient <config-file> [--timeout]");
			System.exit(1);
		}

		/*
		 * Name of config file first arg, timeout value optional second value
		 */
		String configFile = args[0].trim();
		Double timeoutValue = Double.parseDouble(args[1].trim());

		ByteBuffer inputBuffer = ByteBuffer.allocate(1024);
		inputBuffer.clear();

		Client client = new Client(timeoutValue, configFile);

		Thread clientReaderThread = new Thread(new ClientReaderThread(client));
		clientReaderThread.start();
		try {
			while (true) {
				client.getChannel().receive(inputBuffer);

			}

		} catch (IOException e) {
			System.err.println("There was an error performing IO, either "
					+ "while listening on port " + client.getReadPort()
					+ ", or sending " + "a datagram. Closing the application"
					+ " now.");
			e.printStackTrace();
			System.exit(1);
		}
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

			distanceVector.get(localClientID).put(ipPort,
					Double.POSITIVE_INFINITY);
			updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
					localClientID, distanceVector.get(localClientID));
			return true;
		}
	}

	/**
	 * Given an IP address and port, if this Client has a neighbor in their
	 * distance vector with a key corresponding to IP:Port, and if the weight
	 * associated with that link is infinity, set the weight of that link to
	 * weight parameter.
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

			if (!this.distanceVector.get(localClientID).containsKey(ipPort)
					|| this.distanceVector.get(localClientID).get(ipPort) != Double.POSITIVE_INFINITY) {
				return false;
			} else {
				this.distanceVector.get(localClientID).put(ipPort, weight);
				updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
						localClientID, distanceVector.get(localClientID));
				return true;
			}
		}
	}

	/**
	 * Given a Client's routing table, print with a format of: <Current
	 * Time>Distance vector list is: Destination = <IPAddress:Port>, Cost =
	 * <Cost>, Link = <Next hop>
	 */
	public boolean showRt() {
		if (routingTable == null || routingTable.keySet().size() == 0) {
			return false;
		}

		System.out.println(createShowRtString());

		return true;
	}

	/**
	 * Create String that shows routing table. Used to test showRt boolean
	 * method.
	 * 
	 * @return String representation of routing table
	 */
	public String createShowRtString() {
		StringBuffer retStr = new StringBuffer();
		SimpleDateFormat dateTime = new SimpleDateFormat("HH:mm:ss");

		retStr.append("<Current time: ");
		if (!isTest) {
			retStr.append(dateTime.format(Calendar.getInstance().getTime()));
		} else {
			retStr.append("00:16:33");
		}
		retStr.append(">Distance vector list is:");
		String[] routingEntry = new String[2];

		for (String destination : routingTable.keySet()) {
			routingEntry = routingTable.get(destination);
			retStr.append("\nDestination = " + destination + ", ");
			retStr.append("Cost = " + routingEntry[1] + ", ");
			retStr.append("Link = (" + routingEntry[0] + ")");
		}

		return retStr.toString();
	}

	/**
	 * Given a destination IP and port number, print out the next hop in the
	 * path to the destination, and send the Datagram towards the destination,
	 * and to the next hop.
	 * 
	 * @param destinationIP
	 * 
	 * @param portNum
	 */
	public boolean transfer(String destinationIP, int portNum) {
		writeBuffer.clear();
		String destination = destinationIP + ":" + portNum;
		if (routingTable == null
				|| !routingTable.keySet().contains(destination)) {
			return false;
		}

		String transferString = createTransferStringHeader(destination);

		/*
		 * Transform transferString into array of bytes and load it into the
		 * writeBuffer
		 */
		writeBuffer.put(transferString.getBytes());
		writeBuffer.put(chunkBytes);
		writeBuffer.flip();

		try {
			channel.send(writeBuffer, new InetSocketAddress(destinationIP,
					portNum));
		} catch (IOException e) {
			System.err.println("There was an error sending chunks to "
					+ destination + ".");
			e.printStackTrace();
			return false;
		}
		
		return true;
	}

	/**
	 * Given a destination string parameter, create a header for the data that
	 * will be sent in the Datagram.
	 */
	public String createTransferStringHeader(String destination) {
		String retStr = "__TRANSFER__\n" + "Destination: " + destination + "\n"
				+ "Previous hop: " + localClientID + "\n";

		return retStr;
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
			boolean isTest) {
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

		try {

			reader = new BufferedReader(new FileReader(chunkFile));
			this.createChunkBytes(reader);
		} catch (FileNotFoundException e) {
			System.err.println("Chunk file not found. Could not "
					+ "instantiate Client object");
			e.printStackTrace();
		}

		this.isTest = isTest;

		// Open Datagram channel, through which UDP Segments can be
		// received and sent.
		try {
			this.channel = DatagramChannel.open();
			this.channel.socket().bind(new InetSocketAddress(readPort));
			channel.configureBlocking(false);
		} catch (IOException e) {
			if (!this.isTest) {
				System.err.println("There was an error opening up your "
						+ "read-only Datagram Channel on port " + readPort);
				e.printStackTrace();
			}
		}

		this.timeout = timeout;

		writeBuffer = ByteBuffer.allocate(1024);
		writeBuffer.clear();
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
		this.chunkFile = new File(portChunkSequence[2]);
		this.sequenceNumber = Integer.parseInt(portChunkSequence[3]);
		this.localClientID = this.ipAddress + ":" + this.readPort;
		this.distanceVector = createDVFromNeighbors(getNeighborsFromConfig(reader));
		this.routingTable = createRoutingTableInitialDV();
	}

	public void createChunkBytes(BufferedReader reader) {
		String fileContents = "";
		String currentLine = "";
		try {
			while ((currentLine = reader.readLine()) != null) {
				fileContents += currentLine;
			}
		} catch (IOException e) {
			System.err.println("There was an error reading from your "
					+ "chunk file. Could not fill chunkBytes from File.");
			e.printStackTrace();
		}

		this.chunkBytes = fileContents.getBytes();
	}

	/**
	 * Given an IP:Port, check whether the corresponding weight in a Client's
	 * distance vector (if the link exists), is equal to the weight parameter.
	 * This method checks our Client's DV. To check another Client's DV, the
	 * overloaded method distanceVectorHasWeight(String clientDV, String
	 * entryInDV, double weight) is used.
	 * 
	 * @param ipPort
	 * 
	 * @param weight
	 * 
	 * @return True if link exists and associated weight is equal to weight,
	 *         false otherwise.
	 */
	public boolean distanceVectorHasWeight(String ipPort, double weight) {
		if (!distanceVector.containsKey(ipPort)) {
			return false;
		} else {
			return distanceVector.get(localClientID).get(ipPort) == weight;
		}
	}

	/**
	 * Check whether clientDV's distance vector's entryInDV entry is equal to a
	 * given weight.
	 * 
	 * @param clientDV
	 * 
	 * @param entryInDV
	 * 
	 * @param weight
	 * 
	 * @return True if entry exists and is equal to the given weight, false
	 *         otherwise.
	 */
	public boolean distanceVectorHasWeight(String clientDV, String entryInDV,
			double weight) {
		if (!distanceVector.containsKey(clientDV)
				|| !distanceVector.get(clientDV).containsKey(entryInDV)) {
			return false;
		} else {
			return distanceVector.get(clientDV).get(entryInDV) == weight;
		}
	}

	/**
	 * Given an IP:Port, check whether a client's distance vector contains a
	 * corresponding link.
	 * 
	 * @param ipPort
	 * 
	 * @return True if Client's vector contains an entry corresponding to
	 *         ipPort, false otherwise.
	 */
	public boolean hasLink(String ipPort) {
		return distanceVector.containsKey(ipPort);
	}

	/**
	 * Close Client program, and exit with a (successful) status of 0.
	 */
	public boolean close() {
		if (isTest) {
			return true;
		} else {
			System.out.println("Exiting now...");
			System.exit(0);
			return true;
		}
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

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public DatagramChannel getChannel() {
		return channel;
	}

	public void setChannel(DatagramChannel channel) {
		this.channel = channel;
	}

	public double getTimeout() {
		return timeout;
	}

	public void setTimeout(double timeout) {
		this.timeout = timeout;
	}

	public Map<String, Map<String, Double>> getDistanceVector() {
		return distanceVector;
	}

	public void setDistanceVector(
			Map<String, Map<String, Double>> distanceVector) {
		this.distanceVector = distanceVector;
	}

	public Object getDistanceVectorLock() {
		return distanceVectorLock;
	}

	public void setDistanceVectorLock(Object distanceVectorLock) {
		this.distanceVectorLock = distanceVectorLock;
	}

	public boolean isTest() {
		return isTest;
	}

	public void setTest(boolean isTest) {
		this.isTest = isTest;
	}

	public Map<String, String[]> getRoutingTable() {
		return routingTable;
	}

	public void setRoutingTable(Map<String, String[]> routingTable) {
		this.routingTable = routingTable;
	}

	public String getLocalClientID() {
		return localClientID;
	}

	public void setLocalClientID(String localClientID) {
		this.localClientID = localClientID;
	}

	public Set<String> getNeighbors() {
		return neighbors;
	}

	public void setNeighbors(Set<String> neighbors) {
		this.neighbors = neighbors;
	}

	public ByteBuffer getWriteBuffer() {
		return writeBuffer;
	}

	public void setWriteBuffer(ByteBuffer writeBuffer) {
		this.writeBuffer = writeBuffer;
	}
}
