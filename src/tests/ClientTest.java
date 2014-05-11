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
	 * Contains the following neighbors:
	 * 74.73.139.233:7881 1.4
	 * 74.73.139.231:6661 2.3
	 * 74.73.139.228:3131 10.0
	 */
	protected String neighbor1 = "74.73.139.233:7881";
	protected String neighbor2 = "74.73.139.231:6661";
	protected String neighbor3 = "74.73.139.228:3131";
	protected String neighbor4 = "74.73.139.311:9931";
	protected File configThreeNeighbors = new File("configThreeNeighbors");
	protected File configNoNeighbors = new File("configNoNeighbors");
	protected File configImproperFirstLine = new File("configImproperFirstLine");
	protected File configImproperNeighbor = new File("configImproperNeighbor");
	protected Client clientThreeNeighbors = new Client(60.0,
			configThreeNeighbors.getAbsolutePath(), true);
	protected Client clientNoNeighbors = new Client(31.3, configNoNeighbors.getAbsolutePath(), true);

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
		/* Client neighbors:
		74.73.139.233:7881 1.4
		74.73.139.231:6661 2.3
		74.73.139.228:3131 10.0
		 */

		/*
		 * Test that method with incorrect parameters won't tear down links 
		 * or change weight, and return false.
		 */
		assertFalse(clientThreeNeighbors.linkdown("74.73.139.668", 3134));
		assertFalse(clientThreeNeighbors.linkdown("74.73.139.223", 7881));
		assertFalse(clientThreeNeighbors.linkdown("74.73.139.233", 6661));
		assertFalse(clientThreeNeighbors.hasLink("74.73.139.668:3134"));
		assertTrue(clientThreeNeighbors.hasLink(neighbor1));
		assertTrue(clientThreeNeighbors.hasLink(neighbor2));
		assertTrue(clientThreeNeighbors.hasLink(neighbor3));

		/*
		 * Test that client with no neighbors gets false when tearing link 
		 * down.
		 */
		assertFalse(clientNoNeighbors.linkdown("74.73.139.228", 3131));

		/*
		 * Test that correct linkdown changes weight and returns true
		 */
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor1, 1.4));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor2, 2.3));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor3, 10.0));
		assertTrue(clientThreeNeighbors.linkdown("74.73.139.228", 3131));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor1, 1.4));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor2, 2.3));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor3, Double.POSITIVE_INFINITY));
		assertTrue(clientThreeNeighbors.linkdown("74.73.139.233", 7881));
		assertTrue(clientThreeNeighbors.hasLink("74.73.139.233:7881"));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor1, Double.POSITIVE_INFINITY));
		assertTrue(clientThreeNeighbors.hasLink("74.73.139.231:6661"));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor2, 2.3));
		assertTrue(clientThreeNeighbors.hasLink("74.73.139.228:3131"));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor3, Double.POSITIVE_INFINITY));

		/*
		 * Reset clientThreeNeighbors
		 */
		clientThreeNeighbors.linkup("74.73.139.233", 7881, 1.4);
		clientThreeNeighbors.linkup("74.73.139.228", 3131, 10.0);
	}

	@Test
	public void testLinkup() {
		/* Client neighbors:
		74.73.139.233:7881 1.4
		74.73.139.231:6661 2.3
		74.73.139.228:3131 10.0
		 */

		/*
		 * First test that a client can't linkup a link that isn't down yet 
		 * or a link that isn't in the client's distance vector
		 */
		assertFalse(clientThreeNeighbors.linkup(neighbor1, 7881, 10.3));
		assertFalse(clientThreeNeighbors.linkup("74.73.139.553", 3, 9.1));

		/*
		 * Next, test that a downed link can be linkup'd with a new weight, 
		 * and assert that the new weight is correct.
		 */
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor1, 1.4));
		clientThreeNeighbors.linkdown("74.73.139.233", 7881);
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor1, Double.POSITIVE_INFINITY));
		assertTrue(clientThreeNeighbors.linkup("74.73.139.233", 7881, 8.911));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor1, 8.911));

		/*
		 * Test that a client with no neighbors can't linkup a link, even after
		 * it has been linked down, because it is not in the distance vector.
		 */
		clientNoNeighbors.linkdown("74.73.139.228", 3131);
		assertFalse(clientNoNeighbors.linkup("74.73.139.228", 3131, 9.31));

		/*
		 * Reset client (and test that a link can be downed and upped more 
		 * than once):
		 */
		clientThreeNeighbors.linkdown("74.73.139.233", 7881);
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor1, Double.POSITIVE_INFINITY));
		assertTrue(clientThreeNeighbors.linkup("74.73.139.233", 7881, 1.4));
		assertTrue(clientThreeNeighbors
				.distanceVectorHasWeight(neighbor1, 1.4));
	}
	
	@Test
	public void testCreateRoutingTableInitialDV() {
		/* Client neighbors:
		74.73.139.233:7881 1.4
		74.73.139.231:6661 2.3
		74.73.139.228:3131 10.0
		
		 * Should have routing table with entries:
		 * <neighbor1, <neighbor1, 1.4>>
		 * <neighbor2, <neighbor2, 2.3>>
		 * <neighbor3, <neighbor3, 10.0>>
		 */
		Map<String, Map<String, Double>> table1 = new TreeMap<String, Map<String, Double>>();
		table1 = clientThreeNeighbors.createRoutingTableInitialDV();
		assertTrue(table1.get(neighbor1) != null);
		assertTrue(table1.get(neighbor1).get(neighbor1) == 1.4);
		assertTrue(table1.get(neighbor2).get(neighbor2) == 2.3);
		assertTrue(table1.get(neighbor3).get(neighbor3) == 10.0);
	}
	
	@Test
	public void testUpdateDistanceVectorAndRoutingTableFromOtherDistanceVector() {
		/* 
		 * Initial routing table:
		 * 
		 * Should have routing table with entries:
		 * <neighbor1, <neighbor1, 1.4>>
		 * <neighbor2, <neighbor2, 2.3>>
		 * <neighbor3, <neighbor3, 10.0>>
		 * 
		 * Initial Distance Vector:
		 * <neighbor1, 1.4>
		 * <neighbor2, 2.3>
		 * <neighbor3, 10.0>
		 * 
		 * After update, should have the following:
		 * 
		 * New Routing Table:
		 * <neighbor1, <neighbor1, 1.4>>
		 * <neighbor2, <neighbor2, 2.3>>
		 * <neighbor3, <neighbor2, 6.0>>
		 * <neighbor4, <neighbor2, 8.3>>
		 * 
		 * with the following new Distance Vector:
		 * <neighbor1, 1.4>
		 * <neighbor2, 2.3>
		 * <neighbor3, 7.0>
		 * <neighbor4, 8.3>
		 * 
		 * and created from the distance vector:
		 * <neighbor1, 3.3>
		 * <my_ip, 2.3>
		 * <neighbor3, 4.7>
		 * <neighbor4, 6.0>
		 */
		Map<String, Map<String, Double>> otherDV = new TreeMap<String, Map<String, Double>>();
		otherDV.put(neighbor1, 3.3);
		otherDV.put(clientThreeNeighbors.getIpAddress() + ":" + clientThreeNeighbors.getReadPort(), 2.3);
		otherDV.put(neighbor3, 4.7);
		otherDV.put(neighbor4, 6.0);
		
		clientThreeNeighbors.updateDistanceVectorAndRoutingTableFromOtherDistanceVector(neighbor2, otherDV);
		Map<String, Map<String, Double>> newDV = clientThreeNeighbors.getDistanceVector();
		Map<String, Map<String, Double>> newRT = clientThreeNeighbors.getRoutingTable();
		/*
		 * Test that new DV has correct weights
		 */
		assertTrue(newDV.get(neighbor1) == 1.4);
		assertTrue(newDV.get(neighbor2) == 2.3);
		assertTrue(newDV.get(neighbor3) == 7.0);
		assertTrue(newDV.keySet().contains(neighbor4));
		assertTrue(newDV.get(neighbor4) == 8.3);
		
		/*
		 * Test that routing table has correct next hops and weights
		 */
		
		// First test next hops:
		assertTrue(newRT.get(neighbor1).keySet().contains(neighbor1));
		assertTrue(newRT.get(neighbor2).keySet().contains(neighbor2));
		// Assert that next hop has changed for getting to neighbor 3
		assertTrue(newRT.get(neighbor3).keySet().contains(neighbor2));
		assertFalse(newRT.get(neighbor3).keySet().contains(neighbor3));
		assertTrue(newRT.keySet().contains(neighbor4));
		assertTrue(newRT.get(neighbor4).keySet().contains(neighbor2));
		
		// Now test weights:
		assertEquals(newRT.get(neighbor1).get(neighbor1), newDV.get(neighbor1));
		assertEquals(newRT.get(neighbor2).get(neighbor2), newDV.get(neighbor2));
		assertEquals(newRT.get(neighbor3).get(neighbor2), newDV.get(neighbor3));
		assertEquals(newRT.get(neighbor4).get(neighbor2), newDV.get(neighbor4));
		
		/*
		 * Now, test that if a predecessor's link to a 
		 */
	}
}
