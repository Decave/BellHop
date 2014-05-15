package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class ClientDatagramSender {
	private InetSocketAddress neighbor;
	private DatagramSocket socket;

	public ClientDatagramSender(String neighbor) {
		String[] neighborArray = neighbor.split(":");
		String ip = neighborArray[0];
		int port = Integer.parseInt(neighborArray[1]);

		this.neighbor = new InetSocketAddress(ip, port);
		try {
			// Make Datagram send on free port
			this.socket = new DatagramSocket(0);
		} catch (SocketException e) {
			System.err.println("There was an error having a "
					+ "ClientDatagramThread listen on a DatagramSocket"
					+ " for neighbor " + neighbor);
			e.printStackTrace();
		}
	}

	public void sendPacketToNeighbor(byte[] data) {
		try {
			DatagramPacket packet = new DatagramPacket(data, data.length,
					neighbor);
			socket.send(packet);
		} catch (SocketException e) {
			System.err.println("There was an error sending a message to "
					+ "neighbor " + neighbor.getAddress() + ":"
					+ neighbor.getPort());
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("There was an error sending a message to "
					+ "neighbor " + neighbor.getAddress() + ":"
					+ neighbor.getPort());
			e.printStackTrace();
		}
	}
}
