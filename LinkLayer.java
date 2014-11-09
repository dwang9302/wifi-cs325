package wifi;
import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;

/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author richards
 * @author Dongni W.
 */
public class LinkLayer implements Dot11Interface {
   private RF theRF;           // You'll need one of these eventually
   private short ourMAC;       // Our MAC address
   private PrintWriter output; // The output stream we'll write to
   
   private Packet helper; //+helper for writing/reading packet (if mAddr could be remembered?)
   private Receiver receiver; //+the class for listening to incoming data
   private Sender sender; //+the class for for sending outgoing data 
   private ArrayBlockingQueue<byte[]> toSend; //+queue up outgoing data
   private ArrayBlockingQueue<byte[]> received; //+queue up received but not yet forwarded data
   //private ArrayBlockingQueue<byte[]> acks; //+queue up received acks
   
   /**
    * Constructor takes a MAC address and the PrintWriter to which our output will
    * be written.
    * @param ourMAC  MAC address
    * @param output  Output stream associated with GUI
    */
   public LinkLayer(short ourMAC, PrintWriter output) {
      this.ourMAC = ourMAC;
      this.output = output;      
      theRF = new RF(null, null);
      output.println("LinkLayer: Constructor ran.");
      
      this.sender = new Sender(theRF, toSend);//initialize the sender
      this.receiver = new Receiver(theRF, received); //initialize the receiver 
      helper = new Packet();
   }

   /**
    * Send method takes a destination, a buffer (array) of data, and the number
    * of bytes to send.  See docs for full description.
    */
   public int send(short dest, byte[] data, int len) {
      output.println("LinkLayer: Sending "+len+" bytes to "+dest);
      //build the frame with data, sequence # = 0, retry bit = 0
      short seq = 0; //handled in sender;
      byte[] outPack = helper.createMessage("Data", false, ourMAC, dest, data, len, seq); //create packet
      toSend.add(outPack);//add to the outgoing queue
      
      return len; //may return -1 when? 
   }

   /**
    * Recv method blocks until data arrives, then writes it an address info into
    * the Transmission object.  See docs for full description.
    */
   public int recv(Transmission t) {
      output.println("LinkLayer: Pretending to block on recv()");
      //+while(true); // <--- This is a REALLY bad way to wait.  Sleep a little each time through.  
      new Thread(receiver).start();
      byte[] dataR; //the packet received
      while(true) 
      {
    	  while(received.isEmpty()) //<---is it a good way to wait?
    	  {
    		  try {
    			  Thread.sleep(RF.aSlotTime);
    		  } catch (InterruptedException e) {
    			  e.printStackTrace();
    		  }	
    	  }
    	  
      byte[] inPack = null;
	  try {
		inPack = received.take(); //try to get the received data 
	  } catch (InterruptedException e) {
		e.printStackTrace();
	  }
	  
	  ///////////////////////////////////not sure how transmission works...? when to return?
	  //write to transmission 
	  dataR = helper.checkData(inPack);
	  t.setBuf(dataR);
      t.setDestAddr(ourMAC);
      t.setSourceAddr(helper.checkSource(inPack));  
      //return dataR.length;
      }   
   }

   /**
    * Returns a current status code.  See docs for full description.
    */
   public int status() {
      output.println("LinkLayer: Faking a status() return value of 0");
      return 0;
   }

   /**
    * Passes command info to your link layer.  See docs for full description.
    */
   public int command(int cmd, int val) {
      output.println("LinkLayer: Sending command "+cmd+" with value "+val);
      return 0;
   }
}
