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
	private Sender2 sender; // +the class for for sending outgoing data
	private ArrayBlockingQueue<byte[]> toSend; // +queue up outgoing data
	private ArrayBlockingQueue<byte[]> received; // +queue up received but not
													// yet forwarded data
	private ArrayBlockingQueue<byte[]> acks; // +queue up received acks

	private int debugLevel; // debug level: 0 - no diagnostic msg; 1 - with
							// diagnostic msg

	private short beaconInterval; //number of seconds between each try of sending the beacon.  If -1, then don't send

	//TODO: figure out how to increment time within the LinkLayer
	private long time; //hold what we believe the time is right now

	private long check; //store the check for later
	
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
		time = System.currentTimeMillis(); //intializes the time

		// thread
		// drops after 4
		toSend = new ArrayBlockingQueue<byte[]>(4);
		received = new ArrayBlockingQueue<byte[]>(4);
		acks = new ArrayBlockingQueue<byte[]>(2); 

		this.sender = new Sender2(theRF, this.output, toSend, acks, debugLevel);// initialize
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

		//can't have too many unacked things to send
		if (toSend.length > 3)//if there is 4 unacked
		{
			output.println("Too many packets.  Not sending");
			return 0;
		}
		output.println("LinkLayer: Sending " + len + " bytes to " + dest);

		int dataLimit = RF.aMPDUMaximumLength - 10;
		short seq = 0; //placeholder

		//can't have too many unacked things to send
		if 
		
		if (len <= dataLimit) {
			// build the frame with data, sequence # = 0, retry bit = 0

			byte[] outPack = helper.createMessage("Data", false, ourMAC, dest,data, len, seq); // create packet
			toSend.add(outPack);// add to the outgoing queue
		} 
		else // build multiple packets
		{
			int srcPos = 0;
			int lenEx = len;
			while (lenEx > dataLimit) {
				byte[] packetS = new byte[dataLimit];
				System.arraycopy(data, srcPos, packetS, 0, dataLimit);
				byte[] outPack = helper.createMessage("Data", false, ourMAC,
						dest, packetS, dataLimit, seq); // create packet
				toSend.add(outPack);// add to the outgoing queue
				//seq++;
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
		return len; 
	}
	//TODO: fix the problem that happens when it sends and receives at the same time.
	/**
	 * Recv method blocks until data arrives, then writes it an address info
	 * into the Transmission object. See docs for full description.
	 */
	public int recv(Transmission t) {
		output.println("LinkLayer: Pretending to block on recv()");

		byte[] dataR; // the packet received

		while (received.isEmpty()) //isn't this redundant if we already have received.take()?
		{
			try {
				Thread.sleep(RF.aSlotTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		byte[] inPack = null;
		try {
			inPack = received.take(); // try to get the received data  //doesn't this block off until it receives something?
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		dataR = helper.checkData(inPack); //grabs the data

		if((helper.checkMessageType(inPack)).equals("Beacon")) //we got a beacon.
		{
			//TODO: Check the timing and change if it's larger than we expected
			check = helper.checkBeaconTime(dataR);
			if (check > time) //if the time expected is higher, go higher
			{
				time = check
			}
		}
		else
		{	// write to transmission
			t.setBuf(dataR);
			t.setDestAddr(ourMAC);
			t.setSourceAddr(helper.checkSource(inPack));
		}
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
		// TODO 
		if(cmd == 0) //Options and Settings
		{

		}
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
		if(cmd == 2) //Slot Selection
		{
			sender.changeSelect(val);
		}
		if(cmd == 3) //Beacon interval
		{
			beaconInterval = val;
		}
		return 0;
	}
}
