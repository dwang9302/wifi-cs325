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
	private Receiver2 receiver; // +the class for listening to incoming data
	private Sender2 sender; // +the class for for sending outgoing data
	private ArrayBlockingQueue<byte[]> toSend; // +queue up outgoing data
	private ArrayBlockingQueue<byte[]> received; // +queue up received but not
													// yet forwarded data
	private ArrayBlockingQueue<byte[]> beacon; //stores a single beacon to send out
	private ArrayBlockingQueue<byte[]> acks; // +queue up received acks

	private int debugLevel; // debug level: 0 - no diagnostic msg; 1 - with
							// diagnostic msg

	private short beaconInterval; //number of seconds between each try of sending the beacon.  If -1, then don't send

	private int sSelect; //boolean of random "0" or not

	private int status; //holds the status to run checks
	private Status stat; //status class to hold what status we're on
	
	private Clock clock; //clock class to hold time
	
	
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
		beaconInterval = 5;
		status = 1;
		stat = new Status();
		debugLevel = 1;
		
		beacon = new ArrayBlockingQueue<byte[]>(1);
		theRF = new RF(null, null);
		//check if RF fails
		clock = new Clock(stat, beaconInterval, ourMAC, beacon);
		if(theRF == null)
			stat.changeStat(Status.RF_INIT_FAILED);
		else output.println("RF layer initialized at " + clock.getLocalTime());
		
		//check mac
		this.ourMAC = ourMAC;
		if(ourMAC < 0)
			stat.changeStat(Status.BAD_MAC_ADDRESS); //
		
		this.output = output;
		helper = new Packet();
		
		sSelect = 0; //start off with random	
		// thread
		// drops after 4
		toSend = new ArrayBlockingQueue<byte[]>(4);
		received = new ArrayBlockingQueue<byte[]>(4);
		acks = new ArrayBlockingQueue<byte[]>(2); 

		
		this.sender = new Sender2(theRF, this.output, toSend, acks, debugLevel, stat,clock, beacon);// initialize
																			// the
																			// sender
		this.receiver = new Receiver2(theRF, this.output, received, acks,
				debugLevel, this.ourMAC, stat, clock); // initialize the receiver
		new Thread(receiver).start();
		new Thread(sender).start();
		new Thread(clock).start();

		output.println("LinkLayer initialized with MAC address: " + this.ourMAC + " at " + clock.getLocalTime());
		output.println("Send command 0 to see a list of supported commands");
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send. See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {

		//can't have too many unacked things to send
		if (toSend.size() > 3)//if there is 4 unacked
		{
			stat.changeStat(Status.INSUFFICIENT_BUFFER_SPACE);
			if(debugLevel > 0) output.println("Too many packets.  Not sending");
			return 0;
		}
		
		if(len < 0)
		{
			stat.changeStat(Status.BAD_BUF_SIZE);
			if(debugLevel > 0) output.println("Illegal buffer size");
			return 0;
		}
			
		if(debugLevel > 0) output.println("LinkLayer: Sending " + len + " bytes to " + dest + " at " + clock.getLocalTime());

		int dataLimit = RF.aMPDUMaximumLength - 10;
		short seq = 0; //placeholder

		//can't have too many unacked things to send
		//if 
		
		if (len <= dataLimit) { 
			// build the frame with data, sequence # = 0, retry bit = 0
			byte[] outPack = helper.createMessage("Data", false, ourMAC, dest,data, len, seq); // create packet
			toSend.add(outPack);// add to the outgoing queue
		} 
		else // build multiple packets.  case where send too large items
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
		if(debugLevel > 0) output.println("Finished the sending routine. Packet in buffer: " + toSend.size() + " at " +clock.getLocalTime() );
		return len; 
	}
	/**
	 * Recv method blocks until data arrives, then writes it an address info
	 * into the Transmission object. See docs for full description.
	 */
	public int recv(Transmission t) {
		byte[] dataR; // the packet received

		while (received.isEmpty()) //isn't this redundant if we already have received.take()?
		{
			try {
				Thread.sleep(RF.aSlotTime);
			} catch (InterruptedException e) {
				stat.changeStat(Status.UNSPECIFIED_ERROR);
				e.printStackTrace();
				return 0;
			}
		}

		byte[] inPack = null;
		try {
			inPack = received.take(); // try to get the received data  //doesn't this block off until it receives something?
		
		} catch (InterruptedException e) {
			stat.changeStat(Status.UNSPECIFIED_ERROR);
			e.printStackTrace();
			return 0;
		}

		dataR = helper.checkData(inPack); //grabs the data

		if(!(helper.checkMessageType(inPack)).equals("Beacon")) //Beacons are handled in receiver.  So we ignore beacons
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

		/*
		1	SUCCESS	Initial value if 802_init is successful
		2	UNSPECIFIED_ERROR	General error code
		3	RF_INIT_FAILED	Attempt to initialize RF layer failed
		4	TX_DELIVERED	Last transmission was acknowledged
		5	TX_FAILED	Last transmission was abandoned after unsuccessful delivery attempts
		6	BAD_BUF_SIZE	Buffer size was negative
		7	BAD_ADDRESS	Pointer to a buffer or address was NULL
		8	BAD_MAC_ADDRESS	Illegal MAC address was specified
		9	ILLEGAL_ARGUMENT	One or more arguments are invalid
		10	INSUFFICIENT_BUFFER_SPACE	Outgoing transmission rejected due to insufficient buffer space
		*/
		output.println("LinkLayer: Faking a status() return value of 0");
		status = stat.getStat();
		return status;
	}

	/**
	 * Passes command info to your link layer. See docs for full description.
	 */
	public int command(int cmd, int val) {
		output.println("LinkLayer: Sending command " + cmd + " with value "
				+ val);
		if(cmd == 0) //Options and Settings
		{
			output.println("Command 0: Options and settings");
			output.println("Command 1: Debug level");
			output.println(": passing 0 to disable all debugging ouput; ");
			output.println(": passing anything other than 0 to enable all debugging ouput.");
			if (debugLevel == 0)
			{
				output.println("Current setting: Diagnostic is turned off. \n");
			}
			else
			{
				output.println("Current setting: Diagnostic is turned on. \n");
			}
			output.println("Command 2: Slot selection");
			output.println(": passing 0 to select slots randomly; ");
			output.println(": passing anything other than 0 to select maxCW. ");
			if (sSelect == 0)
			{
				output.println("Current setting: Slot Time chosen at random. \n");
			}
			else
			{
				output.println("Current setting: Max slot time chosen. \n");
			}
			output.println("Command 3: Beacon interval ");
			output.println(": passing -1 to disable sending beacon frames;");
			output.println(": passing positive short value to set the desired number of seconds between beacon transmission.");
			
			if (beaconInterval != -1)
			{
				output.println("Current setting: Delay in beacon level: " + beaconInterval + " seconds. \n");
			}
			else
			{
				output.println("Current setting: Beacon disabled. \n");
			}
		}
		else if (cmd == 1) // Debug level
		{
			if (val == 0) // disable debugging output
				debugLevel = 0;
			else
				// enable debugging output
				debugLevel = 1; // only implemented one level for now

			sender.changeDebug(debugLevel);
			receiver.changeDebug(debugLevel);
		}
		else if(cmd == 2) //Slot Selection
		{
			if (val == 0)
			{
				sSelect = 0;
				sender.changeSelect(sSelect);
			}
			else
			{
				sSelect = 1;
				sender.changeSelect(sSelect);
			}
		}
		else if(cmd == 3) //Beacon interval
		{
			if (val == -1)
			{
				beaconInterval = -1;
			}
			else if (val < 0)
			{
				stat.changeStat(Status.ILLEGAL_ARGUMENT);
				output.println("Illegal argument: could accept -1 or positive short values");
			}
			else
			{
				beaconInterval = (short) val;
				clock.changeInterval(beaconInterval);
			}
		}
		else 
			stat.changeStat(Status.ILLEGAL_ARGUMENT);
		return 0;
	}
}
