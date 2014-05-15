package client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import message.BellHopMessage;
import message.RouteUpdateMessage;
import message.TransferMessage;

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
	private byte[] chunk;
	private String chunkName;
	private int sequenceNumber;
	private DatagramChannel channel = null;
	private double timeout;
	private Set<String> neighbors = new HashSet<String>();
	private Map<String, Map<String, Double>> distanceVector = null;
	private Object dvRTLock = new Object();
	private boolean isTest = false;
	private Map<String, String[]> routingTable = new TreeMap<String, String[]>();
	private ByteBuffer writeBuffer = null;
	private ByteArrayOutputStream byteArrayOS = null;
	private ObjectOutputStream objectOS = null;
	private Map<String, boolean[]> chunkTracker = null;
	private Map<String, File> chunksReceived = new TreeMap<String, File>();

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

		this.neighbors = new HashSet<String>(neighborsWithWeights.keySet());

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
			String newDVSender, Map<String, Double> other) {

		synchronized (dvRTLock) {
			addNewDVEntriesToOtherDVs(newDVSender, other);

			if (newDVSender.equals(localClientID)) {
				for (String neighbor : neighbors) {
					distanceVector.get(neighbor).put(localClientID,
							other.get(neighbor));
				}
				distanceVector.put(localClientID, other);
			}

			/*
			 * Once we're here, we know that all of our distance vectors have
			 * the same width. Now, we adjust our current distance vector to
			 * account for the new distance vector's entries.
			 */
			updateLocalDVFromNewDV(newDVSender, other);
		}
	}

	/**
	 * Update our local distance vector with another distance vector of the same
	 * width.
	 * 
	 * In this method, we assume that the width of all DVs is the same (in other
	 * words, we assume that addNewDVEntriesToOtherDVs has been called).
	 * 
	 * @param newDVSender
	 * @param newDV
	 */
	public void updateLocalDVFromNewDV(String newDVSender,
			Map<String, Double> newDV) {
		/*
		 * Check to make sure that local DV have the same entries. Throw
		 * exception if they don't.
		 */
		if (!distanceVector.get(localClientID).keySet().equals(newDV.keySet())) {
			System.err.println("You tried calling updatedLocalDVFromNewDV "
					+ "with an invalid newDV map.");
			throw new IllegalArgumentException();
		}

		/*
		 * Iterate through each element of both DVs simultaneously. If any
		 * weight is smaller in an entry for newDV (assuming that entry is not
		 * newDVSender), check for whether new weight should be used.
		 */
		Double localWeightToEntry;
		Double localWeightToSender = distanceVector.get(localClientID).get(
				newDVSender);
		Double senderWeightToEntry;
		Double weightToSenderToEntry;
		for (String entry : distanceVector.get(localClientID).keySet()) {
			localWeightToEntry = distanceVector.get(localClientID).get(entry);
			senderWeightToEntry = newDV.get(entry);
			weightToSenderToEntry = localWeightToSender + senderWeightToEntry;
			String[] oldRoutingEntry = new String[2];
			oldRoutingEntry = routingTable.get(entry);

			if (oldRoutingEntry[0].equals(newDVSender)
					|| weightToSenderToEntry <= localWeightToEntry) {
				String[] newRoutingEntry = new String[2];
				newRoutingEntry[0] = newDVSender;
				newRoutingEntry[1] = weightToSenderToEntry.toString();
				distanceVector.get(localClientID).put(entry,
						weightToSenderToEntry);

				routingTable.put(entry, newRoutingEntry);
				findShorterPathAmongNeighbors(entry);
			}
		}
	}

	/**
	 * Iterate through neighbors, and see if any are a better match for a next
	 * hop to a destination. If so, change local DV and RT.
	 * 
	 * @param destination
	 */
	public void findShorterPathAmongNeighbors(String destination) {
		Double localWeightToDestination;
		Double localWeightToNeighbor;
		Double neighborWeightToDestination;
		Double weightToNeighborToDestination;

		for (String neighbor : neighbors) {
			localWeightToDestination = distanceVector.get(localClientID).get(
					destination);
			localWeightToNeighbor = distanceVector.get(localClientID).get(
					neighbor);
			neighborWeightToDestination = distanceVector.get(neighbor).get(
					destination);
			weightToNeighborToDestination = localWeightToNeighbor
					+ neighborWeightToDestination;

			if (weightToNeighborToDestination < localWeightToDestination) {
				String[] newRoutingEntry = new String[2];
				newRoutingEntry[0] = neighbor;
				newRoutingEntry[1] = weightToNeighborToDestination.toString();
				distanceVector.get(localClientID).put(destination,
						weightToNeighborToDestination);

				routingTable.put(destination, newRoutingEntry);

				/*
				 * For each destination that uses neighbor as a nextHop, we need
				 * to update the weight to that destination to include the new
				 * weight to neighbor (because the weight to neighbor has
				 * changed).
				 */
				for (String previousDestination : getDestinationsByNextHop(destination)) {
					String[] evenNewerRoutingEntry = new String[2];
					Double newCascadedWeight = weightToNeighborToDestination
							+ distanceVector.get(destination).get(
									previousDestination);
					distanceVector.get(localClientID).put(previousDestination,
							newCascadedWeight);
					evenNewerRoutingEntry[0] = neighbor;
					evenNewerRoutingEntry[1] = newCascadedWeight.toString();

					routingTable
							.put(previousDestination, evenNewerRoutingEntry);
				}
			}
		}

	}

	/**
	 * Get set containing all destinations from RT with a next hop of nextHop
	 */
	public Set<String> getDestinationsByNextHop(String nextHop) {
		Set<String> destinations = new HashSet<String>();

		for (String destination : routingTable.keySet()) {
			String[] routingEntry = routingTable.get(destination);
			if (routingEntry[0].equals(nextHop)) {
				destinations.add(destination);
			}
		}

		return destinations;
	}

	/**
	 * Add all nextHop clients
	 * 
	 * @return Set<String> containing all clients that are used as a next hop
	 */
	public Set<String> getNextHopClients() {
		Set<String> nextHops = new HashSet<String>();

		for (String destination : routingTable.keySet()) {
			String[] routingEntry = new String[2];
			routingEntry = routingTable.get(destination);
			nextHops.add(routingEntry[0]);
		}

		return nextHops;
	}

	/**
	 * Print out all the values of the local client's Distance Vector
	 */
	public void printLocalDistanceVector() {
		for (String neighbor : distanceVector.keySet()) {
			System.out.print(neighbor + "'s DV: | ");
			for (String entry : distanceVector.get(neighbor).keySet()) {
				System.out.print(entry + " => "
						+ distanceVector.get(neighbor).get(entry) + " | ");
			}

			System.out.println();
		}
	}

	/**
	 * Given a new distance vector, check it for entries that are new to our
	 * local DV, and add them accordingly.
	 */
	public void addNewDVEntriesToOtherDVs(String newDVSender,
			Map<String, Double> newDV) {

		// First set local distance vector to newDV so that there's no
		// discrepancy (especially in testing)
		distanceVector.put(newDVSender, newDV);

		/*
		 * Check all the entries in newDVSender's distance vector for any new
		 * entries that our distance vectors don't have. If they don't have it,
		 * add an entry in all the other DVs with a weight of infinity, except
		 */
		for (String entry : newDV.keySet()) {
			// If we don't have this entry in our DV...
			if (!distanceVector.get(localClientID).keySet().contains(entry)) {
				for (String currentEntry : distanceVector.keySet()) {
					if (currentEntry.equals(localClientID)) {
						/*
						 * If we are looking at our own distance vector, add
						 * entry and set the weight to our weight to newDV +
						 * newDV's weight to entry, and add it to the routing
						 * table.
						 */

						// Add entry to local DV
						Double weightToNewDVSender = distanceVector.get(
								localClientID).get(newDVSender);
						Double weightFromNewDVToEntry = newDV.get(entry);
						Double weightFromUsToNewEntry = weightToNewDVSender
								+ weightFromNewDVToEntry;
						distanceVector.get(localClientID).put(entry,
								weightFromUsToNewEntry);

						// Add entry to routing table
						String[] newRoutingEntry = new String[2];
						newRoutingEntry[0] = newDVSender;
						newRoutingEntry[1] = weightFromUsToNewEntry.toString();
						routingTable.put(entry, newRoutingEntry);
					} else if (currentEntry.equals(newDVSender)) {
						/*
						 * Skip iteration if we are looking at the sender's DV.
						 * It should not change because it is assumed that the
						 * DV received in __ROUTE-UPDATE__ is the most
						 * up-to-date.
						 */
						continue;
					} else {
						/*
						 * Otherwise, add an entry to the other DVs and put the
						 * weight to infinity.
						 */
						distanceVector.get(currentEntry).put(entry,
								Double.POSITIVE_INFINITY);
					}

				}
			}
		}

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

		// TODO: Add timer
		try {
			ByteArrayInputStream byteArrayIS = null;
			ObjectInputStream objectIS = null;
			BellHopMessage message;
			while (true) {
				inputBuffer.clear();
				client.getChannel().receive(inputBuffer);

				byteArrayIS = new ByteArrayInputStream(inputBuffer.array());
				objectIS = new ObjectInputStream(byteArrayIS);

				message = (BellHopMessage) objectIS.readObject();

				if (message.getMessageType().equals("__ROUTE-UPDATE__")) {
					/*
					 * If the received message if a __ROUTE-UPDATE__ message,
					 * update your Distance Vector and routing tables, and send
					 * a route update to your neighbors.
					 */
					String source = message.getPreviousHop();
					Map<String, Double> otherDV = ((RouteUpdateMessage) message)
							.getDistanceVector();

					// Update DV and routing tables
					client.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
							source, otherDV);

					// Send __ROUTE-UPDATE__ message to neighbors
					client.sendRouteUpdates();
				} else if (message.getMessageType().equals("__TRANSFER__")) {
					/*
					 * Otherwise, if message is a __TRANSFER__ message, then add
					 * ourselves to the current path, and check if we are the
					 * intended recipient. If we are the intended recipient,
					 * perform chunk logic. Otherwise, forward the message on to
					 * the intended recipient.
					 */
					String finalRecipient = ((TransferMessage) message)
							.getIntendedRecipient();

					// Add current localClientID to path of message.
					((TransferMessage) message).addToPath(client
							.getLocalClientID());

					if (finalRecipient.equals(client.getLocalClientID())) {
						/*
						 * If we are the final recipient, the file contents are
						 * for us. If this completes the chunk we're waiting for
						 * (we have the other chunk sequence number), save the
						 * concatenated chunks to an output file. Otherwise,
						 * save current chunk if we don't have it yet, or ignore
						 * the packet if we do.
						 * 
						 * TODO: Download chunk locally
						 */
						System.out.println("Received __TRANSFER__ message as "
								+ "intended recipient. Printing status "
								+ "message.");
						System.out.println(((TransferMessage) message)
								.getPathString());
						int sequenceNumber = ((TransferMessage) message)
								.getSequenceNumber() - 1;
						String chunkName = ((TransferMessage) message)
								.getChunkName();
						File chunk = ((TransferMessage) message).getChunk();
						if (!client.getChunkTracker().containsKey(chunkName)) {
							/*
							 * If we don't have a key in our Chunk tracker
							 * corresponding to the chunkName received in the
							 * transfer file, then add it to chunkTracker and
							 * chunksReceived.
							 */
							boolean[] chunkReceived = new boolean[2];
							chunkReceived[0] = false;
							chunkReceived[1] = false;
							chunkReceived[sequenceNumber] = true;
							client.getChunkTracker().put(chunkName,
									chunkReceived);
							client.getChunksReceived().put(chunkName, chunk);
						} else {
							boolean[] chunkReceived = client.getChunkTracker()
									.get(chunkName);
							/*
							 * If we've made it this far, one of the two boolean
							 * values in chunkTracker must be true. Therefore,
							 * if this sequence number corresponds to the false
							 * chunkTracker sequence number (for this chunk
							 * name), then we have received both chunks and can
							 * download to a file.
							 */
							if (!chunkReceived[sequenceNumber]) {
								System.out.println("Both parts of chunk "
										+ chunkName + "received!"
										+ " Saving to a file.");
								FileWriter fstream;
								OutputStream out;
								if (sequenceNumber == 1) {
									out = new BufferedOutputStream(
											new FileOutputStream(
													chunk.getName()));
									out.write(chunk);
								}
							} else {
								System.out.println("I already have chunk "
										+ chunkName + "'s part "
										+ sequenceNumber + "of this chunk."
										+ " Ignoring packet.");
							}
						}

					} else {
						client.forwardTransferMessage((TransferMessage) message);
					}
				}
			}

		} catch (IOException e) {
			System.err.println("There was an error performing IO, either "
					+ "while listening on port " + client.getReadPort()
					+ ", or sending " + "a datagram. Closing the application"
					+ " now.");
			e.printStackTrace();
			System.exit(1);
		} catch (ClassNotFoundException e) {
			System.err.println("You received a packet containing an object "
					+ "whose class could not be determined.");
			e.printStackTrace();
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
		synchronized (dvRTLock) {
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
		synchronized (dvRTLock) {
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

		if (!isTest) {
			System.out.println(createShowRtString());
		}

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
		String destination = destinationIP + ":" + portNum;
		if (!routingTable.keySet().contains(destination)) {
			System.err.println("You tried to send a chunk to a destination "
					+ "that does not exist: " + destination);
			return false;
		}

		String[] routingEntry = new String[2];
		routingEntry = routingTable.get(destination);

		/*
		 * Populate TransferMessage with: - previousHop (localClientID) -
		 * nextHop (routingEntry[0]) - destination - sequenceNumber - chunk
		 */
		TransferMessage transferMessage = new TransferMessage(localClientID,
				routingEntry[0], destination, sequenceNumber, chunk, chunkName);
		transferMessage.addToPath(localClientID);

		return sendTransferMessage(transferMessage);
	}

	/**
	 * Get a TransferMessage from main(String[] args) and forward it to its
	 * intended recipient.
	 * 
	 * @param message
	 */
	private void forwardTransferMessage(TransferMessage message) {
		message.setPreviousHop(localClientID);
		String[] routingEntry = new String[2];
		routingEntry = routingTable.get(message.getIntendedRecipient());
		message.setDestination(routingEntry[0]);
		message.setDate(new Date());

		sendTransferMessage(message);
	}

	private boolean sendTransferMessage(TransferMessage message) {
		System.out.println("Sending __TRANSFER__ message to "
				+ message.getDestination() + " at " + message.getDate() + ".");
		try {
			writeBuffer.clear();
			/*
			 * Put new TransferMessage into a ByteArrayOutputStream and put that
			 * into the writeBuffer to be sent.
			 */
			byteArrayOS = new ByteArrayOutputStream();
			objectOS = new ObjectOutputStream(byteArrayOS);
			objectOS.writeObject(message);
			objectOS.flush();

			writeBuffer.put(byteArrayOS.toByteArray());

			System.out.println(writeBuffer.toString());

			// Send __TRANSFER__ message
			String[] nextHop = message.getDestination().split(":");
			if (nextHop.length != 2) {
				System.err.println("You provided an improperly formatted "
						+ "destination address. Please try again.");
				return false;
			}

			channel.send(
					writeBuffer,
					new InetSocketAddress(nextHop[0], Integer
							.parseInt(nextHop[1])));
		} catch (IOException e) {
			System.err.println("There was an error sending a "
					+ "__TRANSFER__ message to " + message.getDestination()
					+ ".");
			e.printStackTrace();
			return false;
		}

		return true;
	}

	/**
	 * Send a __ROUTE-UPDATE__ message to each neighbor, with Poison Reverse
	 * being used in the Distance Vectors that are sent.
	 */
	public void sendRouteUpdates() {
		Map<String, Double> tempDV;
		String[] neighborIPPort = new String[2];
		System.out.println("Sending __ROUTE-UPDATE__ messages.");

		for (String neighbor : neighbors) {
			writeBuffer.clear();

			/*
			 * For each neighbor, get a deeply copied, Poison-Reverse'd distance
			 * vector and create a new RouteUpdateMessage object to send as a
			 * Serialized object.
			 */
			tempDV = poisonReversedDistanceVector(neighbor);
			neighborIPPort = neighbor.split(":");
			RouteUpdateMessage routeUpdate = new RouteUpdateMessage(
					localClientID, neighbor, tempDV);

			System.out.println("Sending __ROUTE-UPDATE__ message to "
					+ neighbor + " at " + routeUpdate.getDate() + ".");
			try {
				/*
				 * Put new RouteUpdateMessage into a ByteArrayOutputStream and
				 * put that into the writeBuffer to be sent.
				 */
				byteArrayOS = new ByteArrayOutputStream();
				objectOS = new ObjectOutputStream(byteArrayOS);
				objectOS.writeObject(routeUpdate);
				objectOS.flush();

				writeBuffer.put(byteArrayOS.toByteArray());

				// Send __ROUTE-UPDATE__ message
				channel.send(writeBuffer, new InetSocketAddress(
						neighborIPPort[0], Integer.parseInt(neighborIPPort[1])));
			} catch (IOException e) {
				System.err.println("There was an error sending a "
						+ "__ROUTE-UPDATE__ message to " + neighbor + ".");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Given a String destination, return our local client's distance vector
	 * with Poison Reverse.
	 * 
	 * @param neighbor
	 */
	public Map<String, Double> poisonReversedDistanceVector(String neighbor) {
		Map<String, Double> poisonReverseDV = new TreeMap<String, Double>(
				distanceVector.get(localClientID));

		Set<String> destinations = destinationsForNextHop(neighbor);

		/*
		 * Iterate through each entry in the distance vector, and if any of the
		 * entries are in destinations (if neighbor is a next hop for any of
		 * them), set their weight to infinity.
		 */
		for (String entry : poisonReverseDV.keySet()) {
			if (destinations.contains(entry)) {
				poisonReverseDV.put(entry, Double.POSITIVE_INFINITY);
			}
		}

		return poisonReverseDV;
	}

	/**
	 * Given a nextHop client, check which destinations it is the nextHop for
	 * 
	 * @param nextHop
	 * @return
	 */
	public Set<String> destinationsForNextHop(String nextHop) {
		Set<String> destinations = new HashSet<String>();

		String[] routingEntry = new String[2];

		/*
		 * Go through each destination in the routing table, so that we can look
		 * at its nextHop and see if it matches the String given as an argument.
		 */
		for (String destination : routingTable.keySet()) {
			routingEntry = routingTable.get(destination);

			/*
			 * If nextHop (given as argument) is the nextHop client for this
			 * route, add the destination to the set.
			 */
			if (routingEntry[0].equals(nextHop)) {
				destinations.add(destination);
			}
		}

		return destinations;
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

		writeBuffer = ByteBuffer.allocate(64000);
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
		this.chunkName = portChunkSequence[2];
		this.chunk = getBytesFromChunkName();
		this.chunkTracker = new TreeMap<String, boolean[]>();
		this.sequenceNumber = Integer.parseInt(portChunkSequence[3]);
		this.localClientID = this.ipAddress + ":" + this.readPort;
		this.distanceVector = createDVFromNeighbors(getNeighborsFromConfig(reader));
		this.routingTable = createRoutingTableInitialDV();
	}

	private byte[] getBytesFromChunkName() {
		if (chunkName == null || chunkName.equals("")) {
			System.err.println("Your chunk does not have a name. Therefore, "
					+ "I cannot find any files to send!");
			return null;
		}

		File chunkFile = new File(chunkName);
		byte[] retChunk = new byte[(int) chunkFile.length()];
		try {
			InputStream input = new BufferedInputStream(new FileInputStream(
					chunkFile));
			
			int bytesRead = 0;
			while (bytesRead < retChunk.length) {
				int bytesRemainingInChunk = retChunk.length - bytesRead;
			}

		} catch (FileNotFoundException e) {
			System.err.println("I couldn't find chunk " + chunkName + ". "
					+ "Did you spell the file name correctly?");
			return null;
		}
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

	public Object getdvRTLock() {
		return dvRTLock;
	}

	public void setdvRTLock(Object dvRTLock) {
		this.dvRTLock = dvRTLock;
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

	public File getChunk() {
		return chunk;
	}

	public void setChunk(File chunk) {
		this.chunk = chunk;
	}

	public Map<String, boolean[]> getChunkTracker() {
		return chunkTracker;
	}

	public String getChunkName() {
		return chunkName;
	}

	public Map<String, File> getChunksReceived() {
		return chunksReceived;
	}
}
