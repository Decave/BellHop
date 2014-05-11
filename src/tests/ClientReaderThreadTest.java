package tests;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import client.Client;
import client.ClientReaderThread;

public class ClientReaderThreadTest {

	/*
	 * Contains the following neighbors:
	 * 74.73.139.233:7881 1.4
	 * 74.73.139.231:6661 2.3
	 * 74.73.139.228:3131 10.0
	 */	
	protected File configThreeNeighbors = new File(
			"src/tests/configThreeNeighbors");
	protected Client clientThreeNeighbors = new Client(60.0,
			configThreeNeighbors.getAbsolutePath(), true);
	protected ClientReaderThread realThread = new ClientReaderThread(
			clientThreeNeighbors);

	@Test
	public void testClientReaderThreadConstructor() {
		ClientReaderThread testConstructThread = new ClientReaderThread(
				clientThreeNeighbors);
		assertTrue(testConstructThread.getMainClient() == clientThreeNeighbors);
	}

	@Test
	public void testProcessInput() {
		// Assert correct commands return true;
		assertTrue(realThread.processInput("linkdown 74.73.139.233 7881"));
		assertTrue(realThread.processInput("linkup 74.73.139.233 7881 3.8"));
		assertTrue(realThread.processInput("close"));
		assertTrue(realThread.processInput("showrt"));
		assertTrue(realThread.processInput("transfer 83.881.67.311 8831"));
		
		// Assert incorrect commands return false 
		assertFalse(realThread.processInput(null));
		assertFalse(realThread.processInput(""));
		assertFalse(realThread.processInput("A random string"));
		assertFalse(realThread.processInput("String of length more than 4"));
		assertFalse(realThread.processInput("linkdown 74.73.139.233:7881"));
		assertFalse(realThread.processInput("linkup 74.73.139.233:7881 3.8"));
		
		// Even though this is a valid command, it should return false because 
		// the link is not down when linkup is being called.
		assertFalse(realThread.processInput("linkup 74.73.139.233 7881 3.8"));
		assertFalse(realThread.processInput("close 74.73.139.233"));
		assertFalse(realThread.processInput("showrt k"));
		assertFalse(realThread.processInput("transfer 83.881.67.311:8831"));
	}

}
