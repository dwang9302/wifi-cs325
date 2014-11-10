package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;

/**
 * Use this layer as a starting point for your project code. See
 * {@link Dot11Interface} for more details on these routines.
 * 
 * @author richards
 * @author Dongni W.
 * @version 11.9.2014
 */
public class LinkLayer implements Dot11Interface {
	private RF theRF; // You'll need one of these eventually
	private short ourMAC; // Our MAC address
	private PrintWriter output; // The output stream we'll write to

	private Packet helper; // +helper for writing/reading packet (if mAddr could
							// be remembered?)
	private Receiver receiver; // +the class for listening to incoming data
	private Sender sender; // +the class for for sending outgoing data
	private ArrayBlockingQueue<byte[]> toSend; // +queue up outgoing data
	private ArrayBlockingQueue<byte[]> received; // +queue up received but not
													// yet forwarded data
	private ArrayBlockingQueue<byte[]> acks; // +queue up received acks

	private short seq; // sequence #
	private int debugLevel; // debug level: 0 - no diagnostic msg; 1 - with
							// diagnostic msg

	/**
	 * Constructor takes a MAC address and the PrintWriter to which our output
	 * will be written.
	 * 
	 * @param ourMAC
	 *            MAC address
	 * @param output
	 *            Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		this.ourMAC = ourMAC;
		this.output = output;
		theRF = new RF(null, null);
		helper = new Packet();
		debugLevel = 1;
		seq = 0;

		// thread
		toSend = new ArrayBlockingQueue<byte[]>(10);
		received = new ArrayBlockingQueue<byte[]>(20);
		acks = new ArrayBlockingQueue<byte[]>(5);

		this.sender = new Sender(theRF, this.output, toSend, debugLevel);// initialize
																			// the
																			// sender
		this.receiver = new Receiver(theRF, this.output, received, acks,
				debugLevel, this.ourMAC); // initialize the receiver
		new Thread(receiver).start();
		new Thread(sender).start();

		output.println("LinkLayer initialized with MAC address: " + this.ourMAC);
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send. See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {
		output.println("LinkLayer: Sending " + len + " bytes to " + dest);

		int dataLimit = RF.aMPDUMaximumLength - 10;

		// if length < aMPDUMAximumLength - 10, send
		if (len <= dataLimit) {
			// build the frame with data, sequence # = 0, retry bit = 0
			byte[] outPack = helper.createMessage("Data", false, ourMAC, dest,
					data, len, seq); // create packet
			toSend.add(outPack);// add to the outgoing queue
			seq++;
		} else // build multiple packets
		{
			int srcPos = 0;
			int lenEx = len;
			while (lenEx > dataLimit) {
				byte[] packetS = new byte[dataLimit];
				System.arraycopy(data, srcPos, packetS, 0, dataLimit);
				byte[] outPack = helper.createMessage("Data", false, ourMAC,
						dest, packetS, dataLimit, seq); // create packet
				toSend.add(outPack);// add to the outgoing queue
				seq++;
				srcPos = srcPos + dataLimit;
				lenEx = lenEx - dataLimit;
			}
			byte[] packetL = new byte[lenEx];
			System.arraycopy(data, srcPos, packetL, 0, lenEx);
			byte[] outPack = helper.createMessage("Data", false, ourMAC, dest,
					packetL, lenEx, seq); // create packet
			toSend.add(outPack);// add to the outgoing queue
		}
		output.println("toSend status: " + toSend.size());
		return len; // may return -1 when?
	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info
	 * into the Transmission object. See docs for full description.
	 */
	public int recv(Transmission t) {
		output.println("LinkLayer: Pretending to block on recv()");
		// +while(true); // <--- This is a REALLY bad way to wait. Sleep a
		// little each time through.

		byte[] dataR; // the packet received

		while (received.isEmpty()) // <---is it a good way to wait?
		{
			try {
				Thread.sleep(RF.aSlotTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		byte[] inPack = null;
		try {
			inPack = received.take(); // try to get the received data
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// write to transmission
		dataR = helper.checkData(inPack);
		t.setBuf(dataR);
		t.setDestAddr(ourMAC);
		t.setSourceAddr(helper.checkSource(inPack));
		return dataR.length;

	}

	/**
	 * Returns a current status code. See docs for full description.
	 */
	public int status() {
		output.println("LinkLayer: Faking a status() return value of 0");
		return 0;
	}

	/**
	 * Passes command info to your link layer. See docs for full description.
	 */
	public int command(int cmd, int val) {
		output.println("LinkLayer: Sending command " + cmd + " with value "
				+ val);
		// TODO if(cmd == 0) //Options and Settings
		if (cmd == 1) // Debug level
		{
			if (val == 0) // disable debugging output
				debugLevel = 0;
			else
				// enable debugging output
				debugLevel = 1; // only implemented one level for now

			sender.changeDebug(debugLevel);
			receiver.changeDebug(debugLevel);
		}
		// TODO if(cmd == 2) //Slot Selection
		// TODO if(cmd == 3) //Beacon interval
		return 0;
	}
}
