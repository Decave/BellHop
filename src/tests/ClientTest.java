package tests;

import static org.junit.Assert.*;

import java.io.BufferedReader;
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

	protected String configLoadNormal = "./configLoadNormal";
	
	/*
	 * Contains the following neighbors:
	 * 74.73.139.233:7881 1.4
	 * 74.73.139.231:6661 2.3
	 * 74.73.139.228:3131 10.0
	 */
	protected String configThreeNeighbors = "./configThreeNeighbors";
	protected String configNoNeighbors = "./configNoNeighbors";
	protected String configImproperFirstLine = "./configImproperFirstLine";
	protected String configImproperNeighbor = "./configImproperNeighbor";
	protected Client clientThreeNeighbors = new Client(60.0,
			configThreeNeighbors, 39131, true);
	protected Client clientNoNeighbors = new Client(31.3, configNoNeighbors,
			43133, true);

	@Test
	public void testConstructor() {
		try {
			Client clientImproperFirstLine = new Client(93.331,
					configImproperFirstLine, 38813, true);
			fail();
		} catch (IllegalArgumentException e) {
		}

		try {
			Client clientImproperNeighbor = new Client(381.3,
					configImproperNeighbor, 19931, true);
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
		assertFalse(clientThreeNeighbors.linkdown("74.73.139.668", 3134));
		assertFalse(clientThreeNeighbors.linkdown("74.73.139.223", 7881));
		assertFalse(clientThreeNeighbors.linkdown("74.73.139.233", 6661));
		assertFalse(clientNoNeighbors.linkdown("74.73.139.228", 3131));
		
		assertTrue(clientThreeNeighbors
				.getDistanceVector()
				.get("74.73.139.233:7881")
				== 1.4);
		assertTrue(clientThreeNeighbors.linkdown("74.73.139.233", 7881));
		assertTrue(clientThreeNeighbors
				.getDistanceVector()
				.containsKey("74.73.139.233:7881"));
		assertTrue(clientThreeNeighbors
				.getDistanceVector()
				.get("74.73.139.233:7881")
				== Double.POSITIVE_INFINITY);
		assertTrue(clientThreeNeighbors
				.getDistanceVector()
				.containsKey("74.73.139.231:6661"));
		assertTrue(clientThreeNeighbors
				.getDistanceVector()
				.get("74.73.139.231:6661")
				== 2.3);
		assertTrue(clientThreeNeighbors
				.getDistanceVector()
				.containsKey("74.73.139.228:3131"));
		assertTrue(clientThreeNeighbors
				.getDistanceVector()
				.get("74.73.139.228:3131")
				== 10.0);
	}
}
