package tests;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.TreeMap;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import client.Client;

public class ClientTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testConstructor()
	{
		Client clientThreeNeighbors = new Client(60.0, "configThreeNeighbors");
		Client clientNoNeighbors = new Client(31.3, "configNoNeighbors");

		try {
			Client clientImproperFirstLine = 
					new Client(93.331, "configImproperFirstLine");
			fail();
		} catch (IllegalFormatException e) {

		}

		try {
			Client clientImproperNeighbor = 
					new Client(381.3, "configImproperNeighbor");
		} catch (IllegalFormatException e) {

		}

	}

	@Test
	public void testGetNeighborsFromConfig() {
		BufferedReader threeNeighborsReader = null;		
		BufferedReader noNeighborsReader = null;
		BufferedReader improperFirstLineReader = null;
		BufferedReader improperNeighborReader = null;
		try {
			threeNeighborsReader = 
					new BufferedReader(new FileReader("configThreeNeighbors"));
			noNeighborsReader = 
					new BufferedReader(new FileReader("configNoNeighbors"));
			improperFirstLineReader = 
					new BufferedReader(new FileReader("configImproperFirstLine"));
			improperNeighborReader =
					new BufferedReader(new FileReader("configImproperNeighbor"));
		} catch (FileNotFoundException e) {
			System.err.println("Could not open a config file when testing " + 
					"getNeighborsFromConfig.");
			e.printStackTrace();
			fail();
		}

		Map<String, Double[]> neighbors = new TreeMap<String, Double[]>();
		Client dummyClient = new Client(60.0, "configThreeNeighbors");

		// Test 3 neighbors:
		neighbors = dummyClient.getNeighborsFromConfig(threeNeighborsReader);
		assertTrue(neighbors.keySet().size() == 3);
		assertTrue(neighbors.keySet().contains("74.73.139.233"));
		assertTrue(neighbors.keySet().contains("74.73.139.231"));
		assertTrue(neighbors.keySet().contains("74.73.139.228"));
		Double[] neighbor1 = neighbors.get("74.73.139.233");
		assertTrue(neighbor1[0] == 7881);
		assertTrue(neighbor1[1] == 1.4);
		assertTrue(neighbor1[2] == 1);

		Double[] neighbor2 = neighbors.get("74.73.139.231");
		assertTrue(neighbor2[0] == 6661);
		assertTrue(neighbor2[1] == 2.3);
		assertTrue(neighbor2[2] == 1);
		
		Double[] neighbor3 = neighbors.get("74.73.139.228");
		assertTrue(neighbor3[0] == 3131);
		assertTrue(neighbor3[1] == 10.0);
		assertTrue(neighbor3[2] == 1);

		// Should return set with empty neighbors
		neighbors = dummyClient.getNeighborsFromConfig(noNeighborsReader);
		assertEquals(new TreeMap<String, Double[]>(), neighbors);
		assertTrue(neighbors.keySet().size() == 0);

		try {
			neighbors = dummyClient
					.getNeighborsFromConfig(improperFirstLineReader);
			neighbors = dummyClient
					.getNeighborsFromConfig(improperNeighborReader);
			fail();
		} catch (IllegalArgumentException e) {}
	}
	
	@Test
	public void testGetPortChunkSequence()
	{
		BufferedReader threeNeighborsReader = null;		
		BufferedReader improperFirstLineReader = null;
		try {
			threeNeighborsReader = 
					new BufferedReader(new FileReader("configThreeNeighbors"));
			improperFirstLineReader = 
					new BufferedReader(new FileReader("configImproperFirstLine"));
		} catch (FileNotFoundException e) {
			System.err.println("Could not open a config file when testing " + 
					"getPortChunkSequence.");
			e.printStackTrace();
			fail();
		}

		String[] portChunkSequence;
		Client dummyClient = new Client(60.0, "configThreeNeighbors");
		
		// Test configThreeNeighbors
		portChunkSequence = dummyClient
				.getPortChunkSequence(threeNeighborsReader);
		assertTrue(portChunkSequence.length == 3);
		assertEquals(portChunkSequence[0], "4200");
		assertEquals(portChunkSequence[1], "60");
		assertEquals(portChunkSequence[2], "chunk1");
		assertEquals(portChunkSequence[3], "1");
		
		// Test configImproperFirstLineFormatting throws exception
		try {
		portChunkSequence = dummyClient
				.getPortChunkSequence(improperFirstLineReader);
		fail();
		} catch (IllegalArgumentException e) {}
	}
}
