package tests;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import client.Client;

public class ClientTest {

	/*
	 * Contains the following neighbors: 74.73.139.233:7881 1.4
	 * 74.73.139.231:6661 2.3 74.73.139.228:3131 10.0
	 */
	protected String neighbor1 = "74.73.139.233:7881";
	protected String neighbor2 = "74.73.139.231:6661";
	protected String neighbor3 = "74.73.139.228:3131";
	protected String neighbor4 = "74.73.139.311:9931";
	protected File configThreeNeighbors = new File("configThreeNeighbors");
	protected File configNoNeighbors = new File("configNoNeighbors");
	protected File configImproperFirstLine = new File("configImproperFirstLine");
	protected File configImproperNeighbor = new File("configImproperNeighbor");
	protected File configNormal = new File("configLoadNormal");
	protected Client clientNormal = new Client(67.31,
			configNormal.getAbsolutePath(), true);
	protected Client clientThreeNeighbors = new Client(60.0,
			configThreeNeighbors.getAbsolutePath(), true);
	protected Client clientNoNeighbors = new Client(31.3,
			configNoNeighbors.getAbsolutePath(), true);
	protected static String fakeTime = "00:16:33";

	@Test
	public void testClientConstructor() {
		try {
			Client clientImproperFirstLine = new Client(93.331,
					configImproperFirstLine.getAbsolutePath(), true);
			fail();
		} catch (IllegalArgumentException e) {
		}

		try {
			Client clientImproperNeighbor = new Client(381.3,
					configImproperNeighbor.getAbsolutePath(), true);
			fail();
		} catch (IllegalArgumentException e) {
		}
		
		assertNotNull(clientThreeNeighbors.getChunkBytes());
		assertNotNull(clientNormal.getChunkBytes());
		assertNotNull(clientThreeNeighbors.getChunkFile());
		assertNotNull(clientNormal.getChunkFile());
	}

	@Test
	public void testGetNeighborsFromConfig() {
		BufferedReader threeNeighborsReader = null;
		BufferedReader noNeighborsReader = null;
		BufferedReader improperFirstLineReader = null;
		BufferedReader improperNeighborReader = null;
		try {
			threeNeighborsReader = new BufferedReader(new FileReader(
					configThreeNeighbors));
			noNeighborsReader = new BufferedReader(new FileReader(
					configNoNeighbors));
			improperFirstLineReader = new BufferedReader(new FileReader(
					configImproperFirstLine));
			improperNeighborReader = new BufferedReader(new FileReader(
					configImproperNeighbor));
		} catch (FileNotFoundException e) {
			System.err.println("Could not open a config file when testing "
					+ "Client constructors.");
			e.printStackTrace();
			fail();
		}

		Map<String, Double> neighbors = new TreeMap<String, Double>();

		// Test 3 neighbors:
		// Skip a line because of header:
		try {
			threeNeighborsReader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
		neighbors = clientThreeNeighbors
				.getNeighborsFromConfig(threeNeighborsReader);
		assertTrue(neighbors.keySet().size() == 3);
		assertTrue(neighbors.keySet().contains("74.73.139.233:7881"));
		assertTrue(neighbors.keySet().contains("74.73.139.231:6661"));
		assertTrue(neighbors.keySet().contains("74.73.139.228:3131"));
		Double neighbor1Weight = neighbors.get("74.73.139.233:7881");
		assertTrue(neighbor1Weight == 1.4);

		Double neighbor2Weight = neighbors.get("74.73.139.231:6661");
		assertTrue(neighbor2Weight == 2.3);

		Double neighbor3Weight = neighbors.get("74.73.139.228:3131");
		assertTrue(neighbor3Weight == 10.0);

		// Should return set with empty neighbors
		// Skip a line because of header
		try {
			noNeighborsReader.readLine();
		} catch (IOException e1) {
			e1.printStackTrace();
			fail();
		}
		neighbors = clientThreeNeighbors
				.getNeighborsFromConfig(noNeighborsReader);
		assertEquals(new TreeMap<String, Double>(), neighbors);
		assertTrue(neighbors.keySet().size() == 0);

		try {
			neighbors = clientThreeNeighbors
					.getNeighborsFromConfig(improperFirstLineReader);
			neighbors = clientThreeNeighbors
					.getNeighborsFromConfig(improperNeighborReader);
			fail();
		} catch (IllegalArgumentException e) {
		}
	}

	@Test
	public void testGetPortChunkSequence() {
		BufferedReader threeNeighborsReader = null;
		BufferedReader improperFirstLineReader = null;
		try {
			threeNeighborsReader = new BufferedReader(new FileReader(
					configThreeNeighbors));
			improperFirstLineReader = new BufferedReader(new FileReader(
					configImproperFirstLine));
		} catch (FileNotFoundException e) {
			System.err.println("Could not open a config file when testing "
					+ "getPortChunkSequence.");
			e.printStackTrace();
			fail();
		}

		String[] portChunkSequence;

		// Test configThreeNeighbors
		portChunkSequence = clientThreeNeighbors
				.getPortChunkSequence(threeNeighborsReader);
		assertTrue(portChunkSequence.length == 4);
		assertEquals(portChunkSequence[0], "4400");
		assertEquals(portChunkSequence[1], "60");
		assertEquals(portChunkSequence[2], "chunk1");
		assertEquals(portChunkSequence[3], "1");

		// Test configImproperFirstLineFormatting throws exception
		try {
			portChunkSequence = clientThreeNeighbors
					.getPortChunkSequence(improperFirstLineReader);
			fail();
		} catch (IllegalArgumentException e) {
		}
	}

	@Test
	public void testLinkdown() {
		/*
		 * Client neighbors: 74.73.139.233:7881 1.4 74.73.139.231:6661 2.3
		 * 74.73.139.228:3131 10.0
		 */

		/*
		 * Test that method with incorrect parameters won't tear down links or
		 * change weight, and return false.
		 */
		assertFalse(clientThreeNeighbors.linkdown("74.73.139.668", 3134));
		assertFalse(clientThreeNeighbors.linkdown("74.73.139.223", 7881));
		assertFalse(clientThreeNeighbors.linkdown("74.73.139.233", 6661));
		assertFalse(clientThreeNeighbors.hasLink("74.73.139.668:3134"));
		assertTrue(clientThreeNeighbors.hasLink(neighbor1));
		assertTrue(clientThreeNeighbors.hasLink(neighbor2));
		assertTrue(clientThreeNeighbors.hasLink(neighbor3));

		/*
		 * Test that client with no neighbors gets false when tearing link down.
		 */
		assertFalse(clientNoNeighbors.linkdown("74.73.139.228", 3131));

		/*
		 * Test that correct linkdown changes weight and returns true
		 */
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor1, 1.4));
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor2, 2.3));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor3, 10.0));
		assertTrue(clientThreeNeighbors.linkdown("74.73.139.228", 3131));
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor1, 1.4));
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor2, 2.3));
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor3,
				Double.POSITIVE_INFINITY));
		assertTrue(clientThreeNeighbors.linkdown("74.73.139.233", 7881));
		assertTrue(clientThreeNeighbors.hasLink("74.73.139.233:7881"));
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor1,
				Double.POSITIVE_INFINITY));
		assertTrue(clientThreeNeighbors.hasLink("74.73.139.231:6661"));
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor2, 2.3));
		assertTrue(clientThreeNeighbors.hasLink("74.73.139.228:3131"));
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor3,
				Double.POSITIVE_INFINITY));

		/*
		 * Reset clientThreeNeighbors
		 */
		clientThreeNeighbors.linkup("74.73.139.233", 7881, 1.4);
		clientThreeNeighbors.linkup("74.73.139.228", 3131, 10.0);
	}

	@Test
	public void testLinkup() {
		/*
		 * Client neighbors: 74.73.139.233:7881 1.4 74.73.139.231:6661 2.3
		 * 74.73.139.228:3131 10.0
		 */

		/*
		 * First test that a client can't linkup a link that isn't down yet or a
		 * link that isn't in the client's distance vector
		 */
		assertFalse(clientThreeNeighbors.linkup(neighbor1, 7881, 10.3));
		assertFalse(clientThreeNeighbors.linkup("74.73.139.553", 3, 9.1));

		/*
		 * Next, test that a downed link can be linkup'd with a new weight, and
		 * assert that the new weight is correct.
		 */
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor1, 1.4));
		clientThreeNeighbors.linkdown("74.73.139.233", 7881);
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor1,
				Double.POSITIVE_INFINITY));
		assertTrue(clientThreeNeighbors.linkup("74.73.139.233", 7881, 8.911));
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor1,
				8.911));

		/*
		 * Test that a client with no neighbors can't linkup a link, even after
		 * it has been linked down, because it is not in the distance vector.
		 */
		clientNoNeighbors.linkdown("74.73.139.228", 3131);
		assertFalse(clientNoNeighbors.linkup("74.73.139.228", 3131, 9.31));

		/*
		 * Reset client (and test that a link can be downed and upped more than
		 * once):
		 */
		clientThreeNeighbors.linkdown("74.73.139.233", 7881);
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor1,
				Double.POSITIVE_INFINITY));
		assertTrue(clientThreeNeighbors.linkup("74.73.139.233", 7881, 1.4));
		assertTrue(clientThreeNeighbors.distanceVectorHasWeight(neighbor1, 1.4));
	}

	@Test
	public void testCreateDVFromNeighbors() {
		/*
		 * Expected DV:
		 * <me, 0>, <neighbor1, 1.4>
		 * <me, infinity>, <neighbor1, infinity>
		 */
		Map<String, Double> neighbors = new TreeMap<String, Double>();
		neighbors.put(neighbor1, 1.4);

		Map<String, Map<String, Double>> retDV = clientNormal.createDVFromNeighbors(neighbors);
		String normalID = clientNormal.getLocalClientID();
		assertTrue(retDV.containsKey(normalID));
		assertTrue(retDV.get(normalID).containsKey(normalID));
		assertTrue(retDV.get(normalID).containsKey(neighbor1));
		assertTrue(retDV.get(normalID).get(normalID) == 0.0);
		assertTrue(retDV.get(normalID).get(neighbor1) == 1.4);

		assertTrue(retDV.containsKey(neighbor1));
		assertTrue(retDV.get(neighbor1).containsKey(neighbor1));
		assertTrue(retDV.get(neighbor1).containsKey(normalID));
		assertTrue(retDV.get(neighbor1).get(normalID) == Double.POSITIVE_INFINITY);
		assertTrue(retDV.get(neighbor1).get(neighbor1) == Double.POSITIVE_INFINITY);
	}

	@Test
	public void testCreateRoutingTableInitialDV() {
		/*
		 * Client neighbors: 
		 * 74.73.139.233:7881 1.4 
		 * 74.73.139.231:6661 2.3
		 * 74.73.139.228:3131 10.0
		 * 
		 * Should have routing table with entries: 
		 * <neighbor1, { neighbor1, 1.4 }> 
		 * <neighbor2, { neighbor2, 2.3 }> 
		 * <neighbor3, { neighbor3, 10.0 }>
		 */
		Map<String, String[]> table1 = new TreeMap<String, String[]>();
		table1 = clientThreeNeighbors.createRoutingTableInitialDV();

		String clientID = clientThreeNeighbors.getLocalClientID();
		assertTrue(table1.get(clientID) != null);
		String[] localEntry = table1.get(clientID);
		assertEquals(localEntry[0], clientID);
		assertTrue(Double.parseDouble(localEntry[1]) == 0.0);

		assertTrue(table1.get(neighbor1) != null);
		String[] neighbor1Entry = table1.get(neighbor1);
		assertEquals(neighbor1Entry[0], neighbor1);
		assertTrue(Double.parseDouble(neighbor1Entry[1]) == 1.4);

		assertTrue(table1.get(neighbor2) != null);
		String[] neighbor2Entry = table1.get(neighbor2);
		assertEquals(neighbor2Entry[0], neighbor2);
		assertTrue(Double.parseDouble(neighbor2Entry[1]) == 2.3);

		assertTrue(table1.get(neighbor3) != null);
		String[] neighbor3Entry = table1.get(neighbor3);
		assertEquals(neighbor3Entry[0], neighbor3);
		assertTrue(Double.parseDouble(neighbor3Entry[1]) == 10.0);
	}

	@Test
	public void testUpdateDistanceVectorAndRoutingTableFromOtherDistanceVector() {
		/*
		 * 74.73.139.233:7881 1.4
		 * 
		 * Initial DV:
		 * <me, 0>, <neighbor1, 1.4>
		 * <me, infinity>, <neighbor1, infinity>
		 * 
		 * Initial routing table:
		 * <me, { me, 0 } >
		 * <neighbor1, { neighbor1, 1.4 } >
		 * 
		 * After update, should have the following:
		 * 
		 * new Distance Vector:
		 * <me, 0>, <neighbor1, 1.4>, <neighbor2, 4.0>
		 * <me, 1.4>, <neighbor1, 0>, <neighbor2, 2.6>
		 * <me, infinity>, <neighbor1, infinity>, <neighbor2, infinity>
		 * 
		 * New Routing Table: 
		 * <me, { me, 0 } >
		 * <neighbor1, { neighbor1, 1.4 } >
		 * <neighbor2, { neighbor1, 4 } >
		 * 
		 * and created from the distance vector:
		 * <me, infinity>, neighbor1, infinity>, <neighbor2, infinity>
		 * <me, 1.4>, <neighbor1, 0>, <neighbor2, 2.6>
		 * <me, infinity>, <neighbor1, infinity>, <neighbor2, infinity>
		 */
		Map<String, Map<String, Double>> otherDV = new TreeMap<String, Map<String, Double>>();
		String normalID = clientNormal.getLocalClientID();
		otherDV.put(normalID, new TreeMap<String, Double>());
		otherDV.get(normalID).put(normalID, Double.POSITIVE_INFINITY);
		otherDV.get(normalID).put(neighbor1, Double.POSITIVE_INFINITY);
		otherDV.get(normalID).put(neighbor2, Double.POSITIVE_INFINITY);

		otherDV.put(neighbor1, new TreeMap<String, Double>());
		otherDV.get(neighbor1).put(normalID, 1.4);
		otherDV.get(neighbor1).put(neighbor1, 0.0);
		otherDV.get(neighbor1).put(neighbor2, 2.6);

		otherDV.put(neighbor2, new TreeMap<String, Double>());
		otherDV.get(neighbor2).put(normalID, Double.POSITIVE_INFINITY);
		otherDV.get(neighbor2).put(neighbor1, Double.POSITIVE_INFINITY);
		otherDV.get(neighbor2).put(neighbor2, Double.POSITIVE_INFINITY);
		clientNormal
		.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
				neighbor1, otherDV.get(neighbor1));

		Map<String, Map<String, Double>> newDV = clientNormal
				.getDistanceVector();
		Map<String, String[]> newRT = clientNormal
				.getRoutingTable();
		/*
		 * Test that new DV has correct weights
		 */
		assertTrue(newDV.get(normalID).get(normalID) == 0.0);
		assertTrue(newDV.get(normalID).get(neighbor1) == 1.4);
		assertTrue(newDV.get(normalID).containsKey(neighbor2));
		assertTrue(newDV.get(normalID).get(neighbor2) == 4.0);

		/*
		 * Test that routing table has correct next hops and weights
		 */

		// First test next hops:
		String[] normalEntry = newRT.get(normalID);
		String[] neighbor1Entry = newRT.get(neighbor1);
		String[] neighbor2Entry = newRT.get(neighbor2);

		assertEquals(normalEntry[0], normalID);
		assertEquals(neighbor1Entry[0], neighbor1);
		assertEquals(neighbor2Entry[0], neighbor1);

		// Now test weights:
		assertTrue(Double.parseDouble(normalEntry[1]) == 0.0);
		assertTrue(Double.parseDouble(neighbor1Entry[1]) == 1.4);
		assertTrue(Double.parseDouble(neighbor2Entry[1]) == 4.0);

		/*
		 * Now, test that if a predecessor's link to an entry changes, that
		 * is reflected in the new routing table.
		 */
		otherDV.get(neighbor1).put(neighbor2, 13.6);
		clientNormal
		.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
				neighbor1, otherDV.get(neighbor1));

		newDV = clientNormal
				.getDistanceVector();
		newRT = clientNormal
				.getRoutingTable();
		neighbor2Entry = newRT.get(neighbor2);

		assertTrue(newDV.get(normalID).get(normalID) == 0.0);
		assertTrue(newDV.get(normalID).get(neighbor1) == 1.4);
		assertTrue(newDV.get(normalID).containsKey(neighbor2));
		assertTrue(newDV.get(normalID).get(neighbor2) == 15.0);
		assertTrue(Double.parseDouble(neighbor2Entry[1]) == 15.0);

		clientNormal.getDistanceVector().get(normalID).put(neighbor1, 13.4);
		clientNormal
		.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
				normalID, clientNormal.getDistanceVector().get(normalID));

		newDV = clientNormal
				.getDistanceVector();
		newRT = clientNormal
				.getRoutingTable();
		neighbor2Entry = newRT.get(neighbor2);
		assertTrue(newDV.get(normalID).get(normalID) == 0.0);
		assertTrue(newDV.get(normalID).get(neighbor1) == 13.4);
		assertTrue(newDV.get(normalID).get(neighbor2) == 27.0);
		assertTrue(Double.parseDouble(neighbor2Entry[1]) == 27.0);
	}

	@Test
	public void testCreateShowRtString() {
		String threeNeighborsRoutingTable = 
				"<Current time: " + ClientTest.fakeTime + ">Distance vector list is:\n"
						+ "Destination = 127.0.1.1:4400, Cost = 0.0, Link = (127.0.1.1:4400)\n"
						+ "Destination = 74.73.139.228:3131, Cost = 10.0, Link = (74.73.139.228:3131)\n"
						+ "Destination = 74.73.139.231:6661, Cost = 2.3, Link = (74.73.139.231:6661)\n"
						+ "Destination = 74.73.139.233:7881, Cost = 1.4, Link = (74.73.139.233:7881)";

		assertEquals(threeNeighborsRoutingTable, clientThreeNeighbors.createShowRtString());
	}
}
