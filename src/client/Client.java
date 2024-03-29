package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.Timer;

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
	private DatagramSocket socket = null;
	private int timeout;
	private Set<String> neighbors = new HashSet<String>();
	private Map<String, Map<String, Double>> distanceVector = null;
	private Object dvRTLock = new Object();
	private boolean isTest = false;
	private Map<String, String[]> routingTable = new TreeMap<String, String[]>();
	private Map<String, boolean[]> chunkTracker = null;
	private Map<String, byte[]> chunksReceived = new TreeMap<String, byte[]>();
	private Map<String, ClientDatagramSender> senders = new TreeMap<String, ClientDatagramSender>();
	private Map<String, Timer> timeoutTimers = new TreeMap<String, Timer>();
	private Timer updateTimer = new Timer("update");

	/**
	 * Constructor for Client object that sets isTest to false.
	 * 
	 * @param timeout
	 * @param configFile
	 */
	public Client(String configFile) {
		this.constructBasicClient(configFile, false);
	}

	/**
	 * Constructor for Client object that allows isTest to be false.
	 * 
	 * @param timeout
	 * @param configFile
	 * @param isTest
	 */
	public Client(String configFile, boolean isTest) {
		this.constructBasicClient(configFile, isTest);
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
			ClientDatagramSender helper = new ClientDatagramSender(neighbor);
			senders.put(neighbor, helper);
			distanceVector.put(neighbor, new TreeMap<String, Double>());
			distanceVector.get(neighbor).put(localClientID,
					Double.POSITIVE_INFINITY);
			for (String link : neighbors.keySet()) {
				distanceVector.get(neighbor)
						.put(link, Double.POSITIVE_INFINITY);
			}
		}

		distanceVector.put(localClientID, neighbors);
		for (String neighbor : neighbors.keySet()) {
			if ((distanceVector.get(localClientID).get(neighbor) != Double.POSITIVE_INFINITY)
					&& !neighbor.equals(localClientID)) {
				Timer timeoutTimer = new Timer(neighbor);
				TimerTask eraseLink = new ShutdownOldLinkTask(this, neighbor);
				timeoutTimer.schedule(eraseLink, (long) 3000 * this.timeout,
						(long) 3000 * timeout);
				timeoutTimers.put(neighbor, timeoutTimer);

			}

			TimerTask sendRouteUpdate = new SendNeighborRouteUpdateTask(this);
			updateTimer.schedule(sendRouteUpdate, (long) 1000 * this.timeout,
					(long) 1000 * this.timeout);

		}
		distanceVector.get(localClientID).put(localClientID, 0.0);
		ClientDatagramSender helper = new ClientDatagramSender(localClientID);
		senders.put(localClientID, helper);

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

			if ((oldRoutingEntry[0].equals(newDVSender) || weightToSenderToEntry <= localWeightToEntry)) {
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
			if (neighbor.equals(localClientID)) {
				continue;
			}
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

				if (neighbor.equals(localClientID)
						&& !destination.equals(localClientID)) {
				} else {
					routingTable.put(destination, newRoutingEntry);
				}
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

					if (!previousDestination.equals(localClientID)
							&& neighbor.equals(localClientID)) {

					} else {
						routingTable.put(previousDestination,
								evenNewerRoutingEntry);
					}
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
						if (localClientID.equals(newDVSender)
								&& !currentEntry.equals(localClientID)) {
							continue;
						}
						routingTable.put(entry, newRoutingEntry);
					} else if (currentEntry.equals(newDVSender)) {
						/*
						 * Skip iteration if we are looking at the sender's DV.
						 * It should not change because it is assumed that the
						 * DV received in __ROUTEUPDATE__ is the most
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
		 * later get ROUTEUPDATE commands, we'll update our routing table from
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
		if (args.length != 1 || args[0] == null || args[0].equals("")) {
			System.err.println("Usage: bfclient <config-file> ");
			System.exit(1);
		}

		/*
		 * Name of config file is the first and only arg
		 */
		String configFile = args[0].trim();

		Client client = new Client(configFile);

		try {
			// Open Datagram Socket, through which UDP Segments can be
			// sent.
			InetSocketAddress address = new InetSocketAddress(
					client.getIpAddress(), client.getReadPort());
			DatagramSocket socket = new DatagramSocket(address);
			client.setSocket(socket);

			Thread clientReaderThread = new Thread(new ClientReaderThread(
					client));
			clientReaderThread.start();

			System.out.println("You are now listening on the following "
					+ "IPAddress:Port channel: " + client.getLocalClientID());

			FileOutputStream fos = null;
			while (true) {
				byte[] data = new byte[64000];
				DatagramPacket packet = new DatagramPacket(data, data.length,
						address);
				socket.receive(packet);

				String fullMessage = new String(packet.getData(), 0,
						packet.getLength());

				String[] headerAndMessage = fullMessage.split("#");

				System.out.println("Received packet! Processing...");

				String header = headerAndMessage[0];

				String[] headerVals = header.split("%");
				headerVals[0] = headerVals[0].toLowerCase();

				if (headerVals[0].contains("routeupdate")) {
					/*
					 * If the received message if a __ROUTEUPDATE__ message,
					 * update your Distance Vector and routing tables, and send
					 * a route update to your neighbors.
					 */
					System.out.println("Received a __ROUTEUPDATE__ message");
					String message = headerAndMessage[1];
					String source = headerVals[2];

					Map<String, Double> otherDV = client
							.getDVFromRouteUpdateMessage(message);

					// Update DV and routing tables
					client.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
							source, otherDV);

					// Send __ROUTEUPDATE__ message to neighbors
					client.sendRouteUpdates();

				} else if (headerVals[0].contains("transfer")) {
					System.out.println("Received a __TRANSFER__ message");
					/*
					 * Otherwise, if message is a __TRANSFER__ message, then add
					 * ourselves to the current path, and check if we are the
					 * intended recipient. If we are the intended recipient,
					 * perform chunk logic. Otherwise, forward the message on to
					 * the intended recipient.
					 */

					String intendedRecipient = headerVals[1];
					String message = headerAndMessage[2];
					int chunkSequence = Integer.parseInt(headerVals[4]);

					if (intendedRecipient.equals(client.getLocalClientID())) {
						System.out.println(message);

						/*
						 * If we are the final recipient, the file contents are
						 * for us. If this completes the chunk we're waiting for
						 * (we have the other chunk sequence number), save the
						 * concatenated chunks to an output file. Otherwise,
						 * save current chunk if we don't have it yet, or ignore
						 * the packet if we do.
						 */
						System.out.println("Received __TRANSFER__ message as "
								+ "intended recipient. Printing status "
								+ "message.");
						String chunkAndPath[] = message.split("=");
						String chunk = chunkAndPath[0];

						client.printStatusMessageFromTransfer(message);
						String sender = client.updateChunkReceived(message,
								chunkSequence - 1);
						boolean[] chunkReceived = client.getChunkTracker().get(
								sender);

						if (chunkReceived[0] && chunkReceived[1]) {
							System.out.println("Both parts of the chunk from "
									+ sender + "have arrived!"
									+ " Saving to a file.");
							String fullChunk;
							if (chunkSequence == 0) {
								fullChunk = chunk;
								fullChunk += client.getChunksReceived()
										.get(sender).toString();
							} else {
								fullChunk = client.getChunksReceived()
										.get(sender).toString();
								fullChunk += chunk;
							}

							String chunkName = sender + "_chunk";
							File chunkFile = new File(chunkName);
							fos = new FileOutputStream(chunkFile);
							fos.write(fullChunk.getBytes());
							fos.close();

							System.out.println("You have succesfully received "
									+ "and saved chunk " + chunkName + ".");
						} else {
							System.out.println("Still waiting on the "
									+ "other chunk.");
						}

					} else {
						client.forwardTransferMessage(intendedRecipient,
								message, chunkSequence);
					}
				} else if (headerVals[0].contains("linkdown")) {
					System.out.println("Received a __LINKDOWN__ message");
					String neighbor = headerVals[1];
					String[] neighborArgs = neighbor.split(":");
					System.out.println("Linking down from " + neighbor);
					client.linkdown(neighborArgs[0],
							Integer.parseInt(neighborArgs[1]), true);
					if (client.getDistanceVector()
							.get(client.getLocalClientID()).get(neighbor) == Double.POSITIVE_INFINITY) {
						System.out.println("You are now disconnected from "
								+ neighbor);
					} else {
						System.out
								.println("There was a problem linking down from "
										+ neighbor);
					}
				} else if (headerVals[0].contains("linkup")) {
					System.out.println("Received a __LINKUP__ message");
					String neighbor = headerVals[1];
					Double newWeight = Double.parseDouble(headerVals[2]);

					String[] neighborArgs = neighbor.split(":");

					System.out.println("Trying to relink with " + neighbor);
					client.linkup(neighborArgs[0],
							Integer.parseInt(neighborArgs[1]), newWeight, true);
					if (client.getDistanceVector()
							.get(client.getLocalClientID()).get(neighbor) != Double.POSITIVE_INFINITY) {
						System.out.println("You have been reconnected to "
								+ neighbor + "!");
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
		}
	}

	/**
	 * Given a destination string parameter, create a header for the data that
	 * will be sent in the Datagram.
	 */
	public String createTransferStringHeader(String intendedRecipient,
			String destination, int chunkSequence) {
		String retStr = "__TRANSFER__" + "%" + intendedRecipient + "%"
				+ destination + "%" + localClientID + "%" + chunkSequence + "#";

		return retStr;
	}

	/**
	 * Given a destination string parameter, create a header for the data that
	 * will be sent in the Datagram.
	 */
	public String createRouteUpdateStringHeader(String destination) {
		String retStr = "__ROUTEUPDATE__" + "%" + destination + "%"
				+ localClientID + "#";

		return retStr;
	}

	/**
	 * Take the local distance vector, and transform it into a string to be
	 * passed into a __ROUTEUPDATE__ message. Entries are separated by the "~"
	 * character, and an entry is separated from its weight by the "="
	 * character.
	 * 
	 * @return String version of Distance Vector
	 */
	public String createDVStringForRouteUpdate(Map<String, Double> dv) {
		String retStr = "";

		for (String entry : dv.keySet()) {
			retStr += entry + "=" + dv.get(entry) + "~";
		}

		return retStr;
	}

	/**
	 * Given the body of a message from a __ROUTEUPDATE__ message, extract the
	 * new distance vector and transform it into a Map.
	 * 
	 * @param message
	 * @return
	 */
	public Map<String, Double> getDVFromRouteUpdateMessage(String message) {
		Map<String, Double> newDV = new TreeMap<String, Double>();

		String[] entries = message.split("~");
		for (String pairs : entries) {
			System.out.println(pairs);
			String[] pair = pairs.split("=");
			String entry = pair[0];
			Double weight = Double.parseDouble(pair[1]);

			newDV.put(entry, weight);
		}

		return newDV;
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
		if (ret.length != 4 && ret.length != 2) {
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
	public boolean linkdown(String linkIP, int linkPort, boolean recipient)
			throws IllegalArgumentException {
		synchronized (dvRTLock) {
			if (linkIP == null || linkIP.equals("") || linkPort <= 0) {
				throw new IllegalArgumentException();
			}

			String ipPort = linkIP + ":" + linkPort;

			if (timeoutTimers.keySet().contains(ipPort)) {
				timeoutTimers.get(ipPort).cancel();
				timeoutTimers.remove(ipPort);
			}

			if (!this.distanceVector.containsKey(ipPort)) {
				return false;
			}

			distanceVector.get(localClientID).put(ipPort,
					Double.POSITIVE_INFINITY);

			if (!recipient) {
				sendLinkDownMessage(ipPort);
			}

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
	public boolean linkup(String linkIP, int linkPort, double weight,
			boolean recipient) throws IllegalArgumentException {
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
				if (!recipient) {
					sendLinkUp(ipPort, weight);
				}
				Timer timeoutTimer = new Timer(ipPort);
				TimerTask eraseLink = new ShutdownOldLinkTask(this, ipPort);
				timeoutTimer.schedule(eraseLink, (long) 3000 * this.timeout,
						(long) 3000 * timeout);
				timeoutTimers.put(ipPort, timeoutTimer);

				neighbors.add(ipPort);
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

	public String updateChunkReceived(String message, int chunkNumber) {
		String[] chunkAndPath = message.split("=");

		String[] paths = chunkAndPath[1].split("@");

		if (!chunksReceived.keySet().contains(paths[0])) {
			chunksReceived.put(paths[0], chunkAndPath[0].getBytes());
			boolean[] sequenceNumbers = new boolean[2];
			sequenceNumbers[0] = false;
			sequenceNumbers[1] = false;
			sequenceNumbers[chunkNumber] = true;
			chunkTracker.put(paths[0], sequenceNumbers);
		} else {
			boolean[] sequenceNumbers = new boolean[2];
			sequenceNumbers[0] = true;
			sequenceNumbers[1] = true;
			chunkTracker.put(paths[0], sequenceNumbers);
		}

		return paths[0];
	}

	public String getStatusMessageFromTransfer(String message) {
		String[] chunkAndPath = message.split("=");

		String[] paths = chunkAndPath[1].split("@");

		String statusMessage = "Path traversed by __TRANSFER__ method:\n";
		for (int i = 0; i < paths.length; i++) {

			statusMessage += "Destination " + i + ": " + paths[i] + "\n";
		}

		statusMessage += "Time received: " + new Date() + "\n";
		statusMessage += "Size of chunk received: " + chunkAndPath[0].length();

		return statusMessage;
	}

	public void printStatusMessageFromTransfer(String message) {
		System.out.println(getStatusMessageFromTransfer(message));
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
	public void transfer(String destinationIP, int portNum) {
		String destination = destinationIP + ":" + portNum;
		if (!routingTable.keySet().contains(destination)) {
			System.err.println("You tried to send a chunk to a destination "
					+ "that does not exist: " + destination);
		}

		if (!routingTable.keySet().contains(destination)) {
			System.err.println(destination + " is not a destination in "
					+ "your routing table. Try another command.");
			return;
		}

		String[] routingEntry = new String[2];
		routingEntry = routingTable.get(destination);

		String nextHop = routingEntry[0];

		String message = createTransferStringHeader(destination, nextHop,
				sequenceNumber);

		message += chunk.toString() + "=";

		message += localClientID;

		sendTransferMessage(destination, nextHop, localClientID, message);
	}

	/**
	 * Get a set of String arguments from main(String[] args), forward a
	 * __TRANSFER__ message to its intended recipient.
	 * 
	 * @param message
	 */
	private void forwardTransferMessage(String intendedRecipient,
			String message, int chunkSequence) {
		String nextHop;
		String[] routingEntry = new String[2];
		routingEntry = routingTable.get(intendedRecipient);
		nextHop = routingEntry[0];

		sendTransferMessage(intendedRecipient, nextHop, localClientID, message,
				chunkSequence);
	}

	private void sendTransferMessage(String intendedRecipient, String nextHop,
			String lastHop, String message) {

		String sendMessage = createTransferStringHeader(intendedRecipient,
				nextHop, sequenceNumber);
		sendMessage += message;
		sendMessage += "@" + localClientID;
		System.out.println("Forwarding __TRANSFER__ message, destined for "
				+ intendedRecipient + ", by way of " + nextHop + " at "
				+ new Date() + ".");
		ClientDatagramSender helper = senders.get(nextHop);
		helper.sendPacketToNeighbor(sendMessage.getBytes());
	}

	private void sendTransferMessage(String intendedRecipient, String nextHop,
			String lastHop, String message, int chunkSequence) {

		String sendMessage = createTransferStringHeader(intendedRecipient,
				nextHop, chunkSequence);
		sendMessage += message;
		sendMessage += "@" + localClientID;
		System.out.println("Forwarding __TRANSFER__ message, destined for "
				+ intendedRecipient + ", by way of " + nextHop + " at "
				+ new Date() + ".");
		ClientDatagramSender helper = senders.get(nextHop);
		helper.sendPacketToNeighbor(sendMessage.getBytes());
	}

	/**
	 * Send a __ROUTEUPDATE__ message to each neighbor, with Poison Reverse
	 * being used in the Distance Vectors that are sent.
	 */
	public void sendRouteUpdates() {
		System.out.println("Sending __ROUTEUPDATE__ messages.");

		for (String neighbor : neighbors) {
			if (distanceVector.get(localClientID).get(neighbor) == Double.POSITIVE_INFINITY) {
				continue;
			} else {
				/*
				 * For each neighbor whose link is up, get a deeply copied,
				 * Poison-Reverse'd distance vector, create a new
				 * __ROUTEUPDATE__ message, and reset all updateTimers
				 */
				updateTimer.cancel();
				updateTimer = new Timer();
				TimerTask sendRouteUpdate = new SendNeighborRouteUpdateTask(
						this);
				updateTimer.schedule(sendRouteUpdate, (long) 1000
						* this.timeout, (long) 1000 * this.timeout);

				Map<String, Double> tempDV = poisonReversedDistanceVector(neighbor);

				String header = createRouteUpdateStringHeader(neighbor);
				String stringDV = createDVStringForRouteUpdate(tempDV);

				String message = header + stringDV;
				ClientDatagramSender sender = senders.get(neighbor);
				sender.sendPacketToNeighbor(message.getBytes());

				System.out.println("Sending __ROUTEUPDATE__ message to "
						+ neighbor + " at " + new Date() + ".");
			}
		}

	}

	/**
	 * Send a LinkDown message to recipient to notify them that the link between
	 * you has gone down.
	 * 
	 * @param recipient
	 */
	public void sendLinkDownMessage(String recipient) {
		System.out.println("Sending __LINKDOWN__ message to " + recipient);

		String linkDownMessage = "__LINKDOWN__" + "%" + localClientID + "#";

		ClientDatagramSender sender = senders.get(recipient);
		sender.sendPacketToNeighbor(linkDownMessage.getBytes());
	}

	/**
	 * Send a LinkUp message to a recipient to notify them that the link between
	 * you has been
	 * 
	 * @param recipient
	 * @param weight
	 */
	public void sendLinkUp(String recipient, double weight) {
		System.out.println("Sending __LINKUP__ message to " + recipient);

		String linkUpMessage = "__LINKUP__" + "%" + localClientID + "%"
				+ weight + "#";

		ClientDatagramSender sender = senders.get(recipient);
		sender.sendPacketToNeighbor(linkUpMessage.getBytes());
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
	private void constructBasicClient(String configFile, boolean isTest) {
		this.ipAddress = "0.0.0.0";

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
		this.timeout = Integer.parseInt(portChunkSequence[1]);
		if (portChunkSequence.length == 4) {
			this.chunkName = portChunkSequence[2];
			this.sequenceNumber = Integer.parseInt(portChunkSequence[3]);
		}
		this.chunk = getBytesFromChunkName();
		this.chunkTracker = new TreeMap<String, boolean[]>();
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
			FileInputStream input = new FileInputStream(chunkFile);
			input.read(retChunk, 0, retChunk.length);
			input.close();

		} catch (FileNotFoundException e) {
			System.err.println("I couldn't find chunk " + chunkName + ". "
					+ "Did you spell the file name correctly?");
			return null;
		} catch (IOException e) {
			System.err.println("There was reading your chunk file");
		}

		return retChunk;
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

	public DatagramSocket getSocket() {
		return socket;
	}

	public void setSocket(DatagramSocket socket) {
		this.socket = socket;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
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

	public byte[] getChunk() {
		return chunk;
	}

	public void setChunk(byte[] chunk) {
		this.chunk = chunk;
	}

	public Map<String, boolean[]> getChunkTracker() {
		return chunkTracker;
	}

	public String getChunkName() {
		return chunkName;
	}

	public Map<String, byte[]> getChunksReceived() {
		return chunksReceived;
	}
}
