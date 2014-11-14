package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;

/**
 * A class for sending data, waiting for ACKs and handling retransmissions.
 * Important:  check how many bytes are actually being sent.
 * 
 * @author Dongni.W
 * @version 11.9.2014
 */
public class Sender implements Runnable {
	private RF theRF;
	private PrintWriter output; // The output stream we'll write to
	// private ArrayBlockingQueue<byte[]> acks;
	private ArrayBlockingQueue<byte[]> toSend; // +queue up outgoing data

	private int debugL;
	private int bytesSent; //checks the bytes sent

	/**
	 * The constructor of the class Sender
	 * 
	 * @param theRF
	 * @param toSend
	 */
	public Sender(RF theRF, PrintWriter output,
			ArrayBlockingQueue<byte[]> toSend, int debugL) // ,ArrayBlockingQueue<byte[]>
															// acks)
	{
		this.theRF = theRF;
		this.output = output;
		this.toSend = toSend;
		// this.acks = acks;
		this.debugL = debugL;
	}

	/**
	 * Change the debug level if received such command from the user
	 * 
	 * @param newLevel
	 */
	public void changeDebug(int newLevel) {
		debugL = newLevel;
	}

	@Override
	public void run() {
		while (true) {
			byte[] beingSent = null;
			try {
				beingSent = toSend.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// listen first, wait if the channel is busy
			while (theRF.inUse()) {
				try {
					Thread.sleep(theRF.aSlotTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			// try transmit
			bytesSent = theRF.transmit(beingSent);
			System.out.println("The number of bytes sent: " + bytesSent); //testing the size

			// implement State switch
			// check for ack...
			// if there's a good ack
			if (debugL == 1) {
				output.println("Testing: Data sent");
			}

			// if there's no ack or only bad ones, something went wrong
			// window size ++
			// re-send
		}
	}
}
