package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ClientReaderThread implements Runnable {

	private Client mainClient;
	private BufferedReader stdIn;
	public static final Set<String> AVAILABLE_COMMANDS = new HashSet<String>(
			Arrays.asList("linkdown", "linkup", "showrt", "close", "transfer"));
	private static final String INVALID_COMMAND = "Invalid command, please "
			+ "try again.";

	public ClientReaderThread(Client mainClient) {
		this.mainClient = mainClient;
		this.stdIn = new BufferedReader(new InputStreamReader(System.in));
	}

	/**
	 * Start ClientReaderThread, and have it listen continuously to stdIn. For
	 * each command it receives from stdIn, perform the appropriate actions.
	 */
	public void run() {
		String command;

		try {
			while ((command = stdIn.readLine()) != null) {
				processInput(command);
			}
		} catch (IOException e) {
			System.err.println("There was an error while listening on "
					+ "the command line. Exiting program.");
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Given a String input representing a command given by a user at the 
	 * terminal, process the command. Each command must be "registered" in 
	 * the AVAILABLE_COMMANDS static final set of this class. In the future, 
	 * this class could be improved by having AVAILABLE_COMMANDS be an object
	 * of type Map<String, Callable>, where Callable is the method that 
	 * runs the command.
	 * 
	 * @param command Terminal command provided by user.
	 */
	public boolean processInput(String command) {
		if (command == null || command.equals("")) {
			// Check corner cases for command string without split
			System.out.println(INVALID_COMMAND);
			return false;
		} else {
			// Check corner cases for command string with split, using the set
			// of available commands to check for validity
			String[] commandWords = command.split(" ");
			commandWords[0] = commandWords[0].toLowerCase();
			if (!AVAILABLE_COMMANDS.contains(commandWords[0])
					|| commandWords.length > 4) {
				System.out.println(INVALID_COMMAND);
				return false;
			}

			if (commandWords.length == 1) {
				/*
				 * Supports commands: (1) showrt (2) close
				 */
				if (commandWords[0].equals("showrt")) {
					return mainClient.showRt();
				} else if (commandWords[0].equals("close")) {
					mainClient.close();
					return true;
				} else {
					System.out.println(INVALID_COMMAND);
					return false;
				}
			} else if (commandWords.length == 2) {
				/*
				 * As of yet, no commands of length 2 are supported
				 */
				System.out.println(INVALID_COMMAND);
				return false;
			} else if (commandWords.length == 3) {
				/*
				 * Supports commands: (1) linkdown {ip_address port} (2)
				 * transfer {destination_ip_address port}
				 */
				if (commandWords[0].equals("linkdown")) {
					return mainClient.linkdown(commandWords[1],
							Integer.parseInt(commandWords[2]));
				} else if (commandWords[0].equals("transfer")) {
					return mainClient.transfer(commandWords[1],
							Integer.parseInt(commandWords[2]));
				} else {
					System.out.println(INVALID_COMMAND);
					return false;
				}
			} else {
				/*
				 * Supports commands: (1) linkup {ip_address port weight}
				 */
				if (commandWords[0].equals("linkup")) {
					return mainClient.linkup(commandWords[1],
							Integer.parseInt(commandWords[2]),
							Double.parseDouble(commandWords[3]));
				} else {
					System.out.println(INVALID_COMMAND);
					return false;
				}
			}
		}
	}

	public Client getMainClient() {
		return mainClient;
	}

	public void setMainClient(Client mainClient) {
		this.mainClient = mainClient;
	}

	public BufferedReader getStdIn() {
		return stdIn;
	}

	public void setStdIn(BufferedReader stdIn) {
		this.stdIn = stdIn;
	}
}
