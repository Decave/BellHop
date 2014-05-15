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

@SuppressWarnings("unused")
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
	protected String clientNormalID = clientNormal.getLocalClientID();
	protected Client clientThreeNeighbors = new Client(60.0,
			configThreeNeighbors.getAbsolutePath(), true);
	protected String clientThreeID = clientThreeNeighbors.getLocalClientID();
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

		assertNotNull(clientThreeNeighbors.getChunk());
		assertNotNull(clientNormal.getChunk());
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
		 * Expected DV: <me, 0>, <neighbor1, 1.4> <me, infinity>, <neighbor1,
		 * infinity>
		 */
		Map<String, Double> neighbors = new TreeMap<String, Double>();
		neighbors.put(neighbor1, 1.4);

		Map<String, Map<String, Double>> retDV = clientNormal
				.createDVFromNeighbors(neighbors);
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
		 * Client neighbors: 74.73.139.233:7881 1.4 74.73.139.231:6661 2.3
		 * 74.73.139.228:3131 10.0
		 * 
		 * Should have routing table with entries: <neighbor1, { neighbor1, 1.4
		 * }> <neighbor2, { neighbor2, 2.3 }> <neighbor3, { neighbor3, 10.0 }>
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
		 * After function call:
		 * 
		 * new Distance Vector: <me, 0>, <neighbor1, 1.4>, <neighbor2, 4.0> <me,
		 * 1.4>, <neighbor1, 0>, <neighbor2, 2.6>
		 * 
		 * New Routing Table:
		 * 
		 * <me, { me, 0 } > <neighbor1, { neighbor1, 1.4 } > <neighbor2, {
		 * neighbor1, 4 } >
		 * 
		 * and created from the distance vector: <me, 1.4>, <neighbor1, 0>,
		 * <neighbor2, 2.6>
		 */
		Map<String, Double> otherDV = giveClientNormalDVAndRTWithNewNeighbor();

		Map<String, Map<String, Double>> newDV = clientNormal
				.getDistanceVector();
		Map<String, String[]> newRT = clientNormal.getRoutingTable();

		/* ************************************************
		 * Tests for adding neighbor2 from neighbor1's DV:
		 * ***********************************************
		 */
		
		
		/*
		 * Test that new DV has correct weights
		 */
		// First for local client:
		assertTrue(newDV.get(clientNormalID).get(clientNormalID) == 0.0);
		assertTrue(newDV.get(clientNormalID).get(neighbor1) == 1.4);
		assertTrue(newDV.get(clientNormalID).containsKey(neighbor2));
		assertTrue(newDV.get(clientNormalID).get(neighbor2) == 4.0);
		// Then for neighbor1:
		assertTrue(newDV.get(neighbor1).get(neighbor1) == 0.0);
		assertTrue(newDV.get(neighbor1).get(clientNormalID) == 1.4);
		assertTrue(newDV.get(neighbor1).containsKey(neighbor2));
		assertTrue(newDV.get(neighbor1).get(neighbor2) == 2.6);
		// And assert that DV doesn't have key for neighbor2:
		assertFalse(newDV.keySet().contains(neighbor2));

		/*
		 * Test that routing table has correct next hops and weights
		 */
		// First test next hops:
		String[] normalEntry = newRT.get(clientNormalID);
		String[] neighbor1Entry = newRT.get(neighbor1);
		String[] neighbor2Entry = newRT.get(neighbor2);

		assertEquals(normalEntry[0], clientNormalID);
		assertEquals(neighbor1Entry[0], neighbor1);
		assertEquals(neighbor2Entry[0], neighbor1);

		// Now test weights:
		assertTrue(Double.parseDouble(normalEntry[1]) == 0.0);
		assertTrue(Double.parseDouble(neighbor1Entry[1]) == 1.4);
		assertTrue(Double.parseDouble(neighbor2Entry[1]) == 4.0);

		/* ******************************************
		 * Test that if neighbor1's weight changes, so does local DV & RT
		 * (because neighbor1 is our next hop to neighbor2)
		 * ****************************************
		 */
		
		// Configure new DV with different weight for neighbor2:
		otherDV.put(neighbor2, 13.6);
		clientNormal
				.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
						neighbor1, otherDV);

		newDV = clientNormal.getDistanceVector();
		newRT = clientNormal.getRoutingTable();
		neighbor2Entry = newRT.get(neighbor2);

		// Test that weights in DV are correct
		assertTrue(newDV.get(clientNormalID).get(clientNormalID) == 0.0);
		assertTrue(newDV.get(clientNormalID).get(neighbor1) == 1.4);
		assertTrue(newDV.get(clientNormalID).containsKey(neighbor2));
		assertTrue(newDV.get(clientNormalID).get(neighbor2) == 15.0);

		// Test that weight in RT is correct:
		assertTrue(Double.parseDouble(neighbor2Entry[1]) == 15.0);

		// Test that predecessor is still neighbor1:
		assertEquals(neighbor2Entry[0], neighbor1);

		/* ****************************************************
		 * Test that changing local weight to neighbor1 also affect's
		 * neighbor2's weight because neighbor1 is predecessor.
		 * ***************************************************
		 */
		otherDV = new TreeMap<String, Double>();
		otherDV.put(clientNormalID, 0.0);
		otherDV.put(neighbor2, 27.0);
		otherDV.put(neighbor1, 13.4);
		
		clientNormal.printLocalDistanceVector();
		System.out.println("\n" + clientNormal.createShowRtString());
		
		clientNormal
				.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
						clientNormalID, otherDV);
		
		System.out.println("\n\n");
		clientNormal.printLocalDistanceVector();
		System.out.println("\n" + clientNormal.createShowRtString());

		newDV = clientNormal.getDistanceVector();
		newRT = clientNormal.getRoutingTable();
		neighbor2Entry = newRT.get(neighbor2);

		// Test that weights in local DV are correct:
		assertTrue(newDV.get(clientNormalID).get(clientNormalID) == 0.0);
		assertTrue(newDV.get(clientNormalID).get(neighbor1) == 13.4);
		assertTrue(newDV.get(clientNormalID).get(neighbor2) == 27.0);

		// Test that weights in neighbor1's DV are correct:
		assertTrue(newDV.get(neighbor1).get(clientNormalID) == 13.4);
		assertTrue(newDV.get(neighbor1).get(neighbor1) == 0.0);
		assertTrue(newDV.get(neighbor1).get(neighbor2) == 13.6);

		assertTrue(Double.parseDouble(neighbor2Entry[1]) == 27.0);

		/* **************************************************
		 * Test that when a neighbor obtains a shorter path * to another
		 * neighbor than a direct link, that the * shorter path is used *
		 * *************************************************
		 */

		/*
		 * Start by changing neighbor1 (port 7881)'s DV by giving it a shorter
		 * path to neighbor3
		 */
		Map<String, Double> dvCloserTo3 = new TreeMap<String, Double>();
		dvCloserTo3.put(neighbor1, 0.0);
		dvCloserTo3.put(neighbor2, 3.1);
		dvCloserTo3.put(clientThreeID, 1.4);
		dvCloserTo3.put(neighbor3, 1.0);
		clientThreeNeighbors
				.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
						neighbor1, dvCloserTo3);
		newDV = clientThreeNeighbors.getDistanceVector();
		newRT = clientThreeNeighbors.getRoutingTable();
		String[] neighbor3Entry = newRT.get(neighbor3);

		// Test that local DV has correct weights
		assertTrue(newDV.get(clientThreeID).get(clientThreeID) == 0.0);
		assertTrue(newDV.get(clientThreeID).get(neighbor1) == 1.4);
		assertTrue(newDV.get(clientThreeID).get(neighbor2) == 2.3);
		assertTrue(newDV.get(clientThreeID).get(neighbor3) == 2.4);

		// Test that neighbor1's DV has correct weights
		assertTrue(newDV.get(neighbor1).get(neighbor1) == 0.0);
		assertTrue(newDV.get(neighbor1).get(neighbor2) == 3.1);
		assertTrue(newDV.get(neighbor1).get(clientThreeID) == 1.4);
		assertTrue(newDV.get(neighbor1).get(neighbor3) == 1.0);

		// Test that RT has correct nextHop and weights
		assertEquals(neighbor3Entry[0], neighbor1);
		assertTrue(Double.parseDouble(neighbor3Entry[1]) == 2.4);
	}

	@Test
	public void testFindShorterPathAmongNeighbors() {
		/* **************************************************
		 * Test that when a neighbor obtains a shorter path * to another
		 * neighbor than a direct link, that the * shorter path is used *
		 * *************************************************
		 */

		/*
		 * Start by changing neighbor1 (port 7881)'s DV by giving it a shorter
		 * path to neighbor3
		 */
		// Start by giving correct DV for clientThree
		Map<String, Double> dvFurtherFrom1 = new TreeMap<String, Double>();
		dvFurtherFrom1.put(clientThreeID, 0.0);
		dvFurtherFrom1.put(neighbor1, 5.4);
		dvFurtherFrom1.put(neighbor3, 10.0);
		dvFurtherFrom1.put(neighbor2, 2.3);
		clientThreeNeighbors.getDistanceVector().put(clientThreeID,
				dvFurtherFrom1);
		String[] routingEntry = new String[2];
		routingEntry[0] = neighbor1;
		routingEntry[1] = dvFurtherFrom1.get(neighbor1).toString();
		clientThreeNeighbors.getRoutingTable().put(neighbor1, routingEntry);

		Map<String, Double> dvCloserTo3 = new TreeMap<String, Double>();
		dvCloserTo3.put(neighbor1, 0.0);
		dvCloserTo3.put(neighbor2, 3.1);
		dvCloserTo3.put(clientThreeID, 5.4);
		dvCloserTo3.put(neighbor3, 1.0);
		clientThreeNeighbors.getDistanceVector().put(neighbor1, dvCloserTo3);
		clientThreeNeighbors.findShorterPathAmongNeighbors(neighbor3);

		Map<String, Double> newDV = clientThreeNeighbors.getDistanceVector()
				.get(clientThreeID);
		Map<String, String[]> newRT = clientThreeNeighbors.getRoutingTable();
		String[] neighbor1Entry = newRT.get(neighbor1);
		String[] neighbor2Entry = newRT.get(neighbor2);
		String[] neighbor3Entry = newRT.get(neighbor3);

		// Test that localDV has correct weights
		assertTrue(newDV.get(neighbor3) == 6.4);
		assertTrue(newDV.get(neighbor1) == 5.4);
		assertTrue(newDV.get(neighbor2) == 2.3);

		// Test that RT has correct next hops:
		assertEquals(neighbor3Entry[0], neighbor1);
		assertTrue(Double.parseDouble(neighbor3Entry[1]) == 6.4);
		assertEquals(neighbor1Entry[0], neighbor1);
		assertTrue(Double.parseDouble(neighbor1Entry[1]) == 5.4);
		assertEquals(neighbor2Entry[0], neighbor2);
		assertTrue(Double.parseDouble(neighbor2Entry[1]) == 2.3);

		/*
		 * Now test that shortening neighbor2's link to neighbor1 correctly
		 * updates neighbor1's weight in local DV, and neighbor1's entry in RT.
		 * After shortening, neighbor1's weight in local DV should be 
		 * weightToNeighbor2 + neighbor2's weight to neighbor1 and next entry
		 * in RT should be neighbor1
		 */
		Map<String, Double> dvCloserTo1 = new TreeMap<String, Double>();
		dvCloserTo1.put(neighbor2, 0.0);
		dvCloserTo1.put(neighbor3, 7.6);
		dvCloserTo1.put(clientThreeID, 2.3);
		dvCloserTo1.put(neighbor1, 1.0);
		clientThreeNeighbors.getDistanceVector().put(neighbor2, dvCloserTo1);
				
		clientThreeNeighbors.findShorterPathAmongNeighbors(neighbor1);
		
		newDV = clientThreeNeighbors.getDistanceVector().get(clientThreeID);
		newRT = clientThreeNeighbors.getRoutingTable();
		neighbor1Entry = newRT.get(neighbor1);
		neighbor2Entry = newRT.get(neighbor2);
		neighbor3Entry = newRT.get(neighbor3);

		// Test that localDV has correct weights
		assertTrue(newDV.get(neighbor3) == 4.3);
		assertTrue(newDV.get(neighbor1) == 3.3);
		assertTrue(newDV.get(neighbor2) == 2.3);

		// Test that RT has correct next hops:
		assertEquals(neighbor3Entry[0], neighbor2);
		assertTrue(Double.parseDouble(neighbor3Entry[1]) == 4.3);
		assertEquals(neighbor1Entry[0], neighbor2);
		assertTrue(Double.parseDouble(neighbor1Entry[1]) == 3.3);
		assertEquals(neighbor2Entry[0], neighbor2);
		assertTrue(Double.parseDouble(neighbor2Entry[1]) == 2.3);

	}

	@Test
	public void testCreateShowRtString() {
		String threeNeighborsRoutingTable = "<Current time: "
				+ ClientTest.fakeTime
				+ ">Distance vector list is:\n"
				+ "Destination = 127.0.1.1:4400, Cost = 0.0, Link = (127.0.1.1:4400)\n"
				+ "Destination = 74.73.139.228:3131, Cost = 10.0, Link = (74.73.139.228:3131)\n"
				+ "Destination = 74.73.139.231:6661, Cost = 2.3, Link = (74.73.139.231:6661)\n"
				+ "Destination = 74.73.139.233:7881, Cost = 1.4, Link = (74.73.139.233:7881)";

		assertEquals(threeNeighborsRoutingTable,
				clientThreeNeighbors.createShowRtString());
	}

	@Test
	public void testAddNewDVEntriesToOtherDVs() {
		/*
		 * Test for adding in one new entry
		 */
		Map<String, Double> newDVNeighbor2 = new TreeMap<String, Double>();
		newDVNeighbor2.put(clientNormalID, 1.4);
		newDVNeighbor2.put(neighbor2, 3.6);
		newDVNeighbor2.put(neighbor1, 0.0);
		clientNormal.addNewDVEntriesToOtherDVs(neighbor1, newDVNeighbor2);

		String[] routingEntry = new String[2];
		Map<String, Map<String, Double>> newDV = clientNormal
				.getDistanceVector();

		// First test that clientDV's values are as expected
		Map<String, Double> clientDV = newDV.get(clientNormalID);
		Map<String, String[]> clientRT = clientNormal.getRoutingTable();
		assertTrue(clientDV.get(clientNormalID) == 0.0); // 0.0 weight to
		// himself
		assertTrue(clientDV.get(neighbor1) == 1.4); // same weight to neighbor1
		assertTrue(clientDV.get(neighbor2) == 5.0); // Correct weight to
		// neighbor2
		routingEntry = clientRT.get(neighbor2);
		assertEquals(routingEntry[0], neighbor1); // Correct nextHop to
		// neighbor2
		assertTrue(Double.parseDouble(routingEntry[1]) == 5.0); // Correct
		// weight to
		// neighbor 2
		routingEntry = clientRT.get(neighbor1);
		assertEquals(routingEntry[0], neighbor1); // Correct nextHop to
		// neighbor1
		assertTrue(Double.parseDouble(routingEntry[1]) == 1.4); // Correct
		// weight to
		// neighbor1

		// Next test that neighbor1's DV hasn't changed
		Map<String, Double> neighbor1DV = newDV.get(neighbor1);
		assertTrue(neighbor1DV.get(neighbor1) == 0.0); // Neighbor 1's weight is
		// correct
		assertTrue(neighbor1DV.get(clientNormalID) == 1.4);
		assertTrue(neighbor1DV.get(neighbor2) == 3.6);

		/*
		 * Now assert that adding in an old entry doesn't change anything
		 */
	}

	@Test
	public void testGetPoisonReversedDistanceVector() {
		/*
		 * After call to giveClientNormal..., should have:
		 * 
		 * New Distance Vector: <me, 0>, <neighbor1, 1.4>, <neighbor2, 4.0> <me,
		 * 1.4>, <neighbor1, 0>, <neighbor2, 2.6> <me, infinity>, <neighbor1,
		 * infinity>, <neighbor2, infinity>
		 * 
		 * New Routing Table: <me, { me, 0 } > <neighbor1, { neighbor1, 1.4 } >
		 * <neighbor2, { neighbor1, 4 } >
		 */
		Map<String, Double> otherDV = giveClientNormalDVAndRTWithNewNeighbor();

		Map<String, Double> poisonReverseDV = clientNormal
				.poisonReversedDistanceVector(neighbor1);
		assertTrue(poisonReverseDV.get(neighbor2) == Double.POSITIVE_INFINITY);
		String[] routingEntry = clientNormal.getRoutingTable().get(neighbor2);
		assertEquals(routingEntry[0], neighbor1);
		assertTrue(Double.parseDouble(routingEntry[1]) == clientNormal
				.getDistanceVector().get(clientNormal.getLocalClientID())
				.get(neighbor2));

		otherDV.put(neighbor4, 3.19);
		clientNormal
				.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
						neighbor1, otherDV);

		poisonReverseDV = clientNormal.poisonReversedDistanceVector(neighbor1);
		routingEntry = clientNormal.getRoutingTable().get(neighbor2);
		assertEquals(routingEntry[0], neighbor1);
		assertTrue(Double.parseDouble(routingEntry[1]) == clientNormal
				.getDistanceVector().get(clientNormal.getLocalClientID())
				.get(neighbor2));

		routingEntry = clientNormal.getRoutingTable().get(neighbor4);
		assertEquals(routingEntry[0], neighbor1);
		assertTrue(Double.parseDouble(routingEntry[1]) == clientNormal
				.getDistanceVector().get(clientNormal.getLocalClientID())
				.get(neighbor4));
	}

	/*
	 * Client normal's:
	 * 
	 * Initial DV: <me, 0>, <neighbor1, 1.4> <me, infinity>, <neighbor1,
	 * infinity>
	 * 
	 * Initial routing table: <me, { me, 0 } > <neighbor1, { neighbor1, 1.4 } >
	 * 
	 * After update, should have the following:
	 * 
	 * new Distance Vector: <me, 0>, <neighbor1, 1.4>, <neighbor2, 4.0> <me,
	 * 1.4>, <neighbor1, 0>, <neighbor2, 2.6>
	 * 
	 * New Routing Table:
	 * 
	 * <me, { me, 0 } > <neighbor1, { neighbor1, 1.4 } > <neighbor2, {
	 * neighbor1, 4 } >
	 * 
	 * and created from the distance vector: <me, 1.4>, <neighbor1, 0>,
	 * <neighbor2, 2.6>
	 */
	private Map<String, Double> giveClientNormalDVAndRTWithNewNeighbor() {
		Map<String, Double> otherDV = new TreeMap<String, Double>();

		otherDV.put(clientNormalID, 1.4);
		otherDV.put(neighbor1, 0.0);
		otherDV.put(neighbor2, 2.6);

		clientNormal
				.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(
						neighbor1, otherDV);

		return new TreeMap<String, Double>(otherDV);
	}
}
