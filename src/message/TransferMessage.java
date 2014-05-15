package message;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.Iterator;

/**
 * TransferMessage class that is responsible for transporting a chunk file to an
 * intended recipient.
 * 
 * @author David C. Vernet
 * 
 */
public class TransferMessage extends BellHopMessage implements Serializable {

	private static final long serialVersionUID = 731817381341355L;

	/**
	 * Sequence number of chunk in transit
	 */
	private final int SEQUENCE_NUMBER;

	/**
	 * Final intended recipient of chunk
	 */
	private final String INTENDED_RECIPIENT;

	/**
	 * File to be transferred to INTENDED_RECIPIENT recipient
	 */
	private final File CHUNK;

	/**
	 * Name of CHUNK
	 */
	private final String CHUNK_NAME;

	/**
	 * Construct __TRANSFER__ message object
	 * 
	 * @param previousHop
	 * @param destination
	 * @param intendedRecipient
	 * @param sequenceNumber
	 * @param chunk
	 */
	public TransferMessage(String previousHop, String destination,
			String intendedRecipient, int sequenceNumber, File chunk,
			String chunkName) {
		super(previousHop, destination);
		this.INTENDED_RECIPIENT = intendedRecipient;
		this.SEQUENCE_NUMBER = sequenceNumber;
		this.CHUNK = chunk;
		setDate(new Date());
		this.CHUNK_NAME = chunkName;
	}

	/**
	 * Return __TRANSFER__ message type
	 */
	public String getMessageType() {
		return "__TRANSFER__";
	}

	/**
	 * Get Serial Version UID of object for Serialization
	 * 
	 * @return long
	 */
	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	/**
	 * Get ipAddress:Port string of Client who is the intended final recipient
	 * of the packet.
	 * 
	 * @return
	 */
	public String getIntendedRecipient() {
		return INTENDED_RECIPIENT;
	}

	/**
	 * Get CHUNK / file contents. Do not allow to overwrite new file for
	 * security reasons (an intermediary Client should only need to / be allowed
	 * to forward the original file).
	 * 
	 * @return
	 */
	public File getChunk() {
		return CHUNK;
	}
	
	/**
	 * Get CHUNK_NAME
	 * 
	 * @return
	 */
	public String getChunkName() {
		return CHUNK_NAME;
	}

	/**
	 * Get SEQUENCE_NUMBER associated with chunk file.
	 * 
	 * @return
	 */
	public int getSequenceNumber() {
		return SEQUENCE_NUMBER;
	}

	/**
	 * Get __TRANSFER__ message, including size of chunk, timestamp, and a list
	 * of destinations that the chunk visited on its path to its intended
	 * recipient.
	 * 
	 * @return
	 */
	public String getPathString() {
		String retStr = "__TRANSFER__ Message\n\n";
		retStr += "Size of chunk: " + CHUNK.length() + "\n";
		retStr += "Time: " + getDate() + "\n";
		retStr += "Path:\n";
		Iterator<String> pathIterator = getPath().iterator();
		while (pathIterator.hasNext()) {
			retStr += pathIterator.next() + "\n";
		}

		return retStr;
	}

	/**
	 * Equals method for TransferMessage class
	 */
	public boolean equals(Object otherMessage) {
		if (this == otherMessage) {
			return true;
		} else if (!(otherMessage instanceof TransferMessage)
				|| otherMessage == null) {
			return false;
		}

		return this.CHUNK.equals(((TransferMessage) otherMessage).getChunk());
	}
}
