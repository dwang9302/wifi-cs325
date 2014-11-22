package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;

/**
 * A class for listening to incoming data, check and classify received data
 * 
 * @author Dongni W.
 * @version 11.9.2014
 */
public class Receiver implements Runnable {
	private RF theRF;
	private Packet helper;
	private PrintWriter output; // The output stream we'll write to

	private ArrayBlockingQueue<byte[]> data; // +queue up received data
	private ArrayBlockingQueue<byte[]> acks; // +queue up received ACKs

	private short ourMAC; // for address selectivity
	private int debugL;

	/**
	 * The constructor of class Recevier
	 * 
	 * @param theRF
	 * @param data
	 *            - queue for received data
	 * @param acks
	 *            - queue for received ACKs
	 */
	public Receiver(RF theRF, PrintWriter output,
			ArrayBlockingQueue<byte[]> data, ArrayBlockingQueue<byte[]> acks,
			int debugL, short ourMAC) {
		this.theRF = theRF;
		this.output = output;
		helper = new Packet();

		this.data = data;
		this.acks = acks;

		this.ourMAC = ourMAC;
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
		while (true) // keeps listening..
		{
			byte[] received = theRF.receive();

			// if the message is not for us, ignore
			if (helper.checkDest(received) != ourMAC) {
				received = theRF.receive();
			}

			// check to see the frame type
			if (helper.checkMessageType(received).equals("ACK"))// ACK
			{
				acks.add(received); // store it into the acks (sender may want  <--make sure that address selectivity is working
									// to check it )
			} else // DATA
			{
				// int sequenceExpected = 0;
				// make and send ack  <--implement the ACK system.  How do we make it known that they know an ACK for this was sent?  What if they time out and send again?  Make sure that we don't add the data again.
				// check sequence number,
				// if good: store it to data
				data.add(received);
				if (debugL == 1) {
					output.println("Data received");
				}
			}

			// sequenceExpected++;

		}

	}

}
