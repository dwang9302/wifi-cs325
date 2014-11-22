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
	private int lastSeq; 
	//Dongni: This may need to be a table that records all last received sequence numbers from different sources
	//since different senders may have same/different sequence numbers. Which data structure would be ideal?

	/**
	 * The constructor of class Recevier
	 * 
	 * @param theRF
	 * @param data
	 *            - queue for received data
	 * @param acks
	 *            - queue for received ACKs
	 */
	public Receiver(RF theRF, PrintWriter output,ArrayBlockingQueue<byte[]> data, ArrayBlockingQueue<byte[]> acks, int debugL, short ourMAC) {
		this.theRF = theRF;
		this.output = output;
		helper = new Packet();

		this.data = data;
		this.acks = acks;

		this.ourMAC = ourMAC;
		this.debugL = debugL;
		//lastSeq = 0; //expected sequence number starts at 0
		
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
		while (true) // keeps listening
		{
			byte[] received = theRF.receive();//block until a packet is received

			//Address selectivity
			//if the message is not for us, ignore. Dongni: here. <--make sure that address selectivity is working
			short dest = helper.checkDest(received);
			while( dest != ourMAC && dest != (short)(-1)) //destination is not our mac & it's not a broadcast.
			{
				received = theRF.receive(); //block until another good packet is received
			}

			// check to see the frame type
			String type = helper.checkMessageType(received);
			if (type.equals("ACK"))// ACK
			{
				acks.add(received); // store received ack into the acks, will be handled in the Sender.
									
			}
			else // DATA
			{
				// int sequenceExpected = 0;
				// make and send ack  <--implement the ACK system.  
				//How do we make it known that they know an ACK for this was sent?  What if they time out and send again?  Make sure that we don't add the data again.
				//Dongni: we are going to add the data only if it has our expected sequence number (>last one received from the same source), which would be updated after the data is added.
				//:If they send again, they will be using the same sequence number with retry bit = 1 --> we should send back an ack (?tell them to stop) without adding the data.
				//: Retry bit = 0 || retry bit = 1 and expected sequence #  --> add
				//: Retry bit = 1 but not expected sequence # --> discard
				
				data.add(received);
				if (debugL == 1) {
					output.println("Data received");
				}
				
				//Expected next packet with the right sequence number (may from different sources.)
				//expectedSeq++;
				
			}
			//later beacon

			

		}

	}

}
