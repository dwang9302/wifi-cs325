package wifi;
 
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;
import wifi.Sender2.State;
 
/**
 * A class for listening to incoming data, check and classify received data
 * 
 * @author Dongni W.
 * @author Dennis Maguddayao 
 * @version 11.22.2014
 */
public class Receiver2 implements Runnable {
	        
	
    private RF theRF;
    private Packet helper;
    private PrintWriter output; // The output stream we'll write to
    private short seqRecv;
 
    private ArrayBlockingQueue<byte[]> data; // +queue up received data
    private ArrayBlockingQueue<byte[]> acks; // +queue up received ACKs
 
    private short ourMAC; // for address selectivity
    private int debugL;
    private int lastSeq; 
    private Hashtable<Integer,Integer> recvFrom; //last recved sequence #
 
    /**
     * The constructor of class Recevier
     * 
     * @param theRF
     * @param data
     *            - queue for received data
     * @param acks
     *            - queue for received ACKs
     */
    public Receiver2(RF theRF, PrintWriter output,ArrayBlockingQueue<byte[]> data, ArrayBlockingQueue<byte[]> acks, int debugL, short ourMAC) {
        this.theRF = theRF;
        this.output = output;
        helper = new Packet();
 
        this.data = data;
        this.acks = acks;
 
        this.ourMAC = ourMAC;
        this.debugL = debugL;
        //lastSeq = 0; //expected sequence number starts at 0
        recvFrom = new Hashtable<Integer, Integer>(20);
         
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
            //if the message is not for us, ignore. 
            short dest = helper.checkDest(received);
            while( dest != ourMAC && dest != (short)(-1)) //destination is not our mac & it's not a broadcast.
            {
                received = theRF.receive(); //block until another good packet is received
                dest= helper.checkDest(received); //we need this otherwise we're stuck in an endless loop
            }
            output.print("Data received: " + received.length + ", aimed for " + dest); //use this to check how big the packets we send are
            
            // check to see the frame type
            String type = helper.checkMessageType(received);
            switch(type) {
             	case "ACK":
             		acks.add(received); // store received ack into the acks, will be handled in the Sender.
             		break;
             		
             	case "Beacon":
             		//TODO clock sync
             		break;
             		
             	case "Data":
             		//check destination address
             		short dataSource = helper.checkSource(received);
                    Integer key = Integer.valueOf(dataSource);
                    if(key == -1) //it was a broadcast we received
                    	data.add(received);
                    else
                    {
                    	short seq = -1; //last recv sequence number; default = -1: no-previous packet from this address
                    	//get last received sequence number from the table
                    	if(recvFrom.containsKey(key)) 
                    		seq = (short) recvFrom.get(key).intValue(); //grabs the previous sequence number 
                    	
                    	
                    	//check sequence number of current packet
                    	seqRecv = helper.checkSequenceNo(received); //received sequence number
                    	
                    	if(seqRecv < seq) //4095 case or connection lost
                    	{
                    		seq = -1; //reset 
                    	}
                    	
                    	if(seqRecv > seq) //packet expected
                    	{	
                    		data.add(received); //display
                    		recvFrom.put(key, Integer.valueOf(seqRecv));//update sequence #
                    		
                    		if(seqRecv > (seq + 1)) //may be due to connection loss
                    			output.println("Receiver detected a gap in sequence number");		
                    	}
                    	//else (seq == seqRecv) duplicate packets, only send ack back
                    		
                    	//wait sifs
                        try {
                            Thread.sleep(theRF.aSIFSTime);
                        } catch (InterruptedException e) 
                        {
                            e.printStackTrace();
                        }
                    	
                        //always send ACK back
                        theRF.transmit(helper.createACK(dest, dataSource, seqRecv));
                        if (debugL == 1) {
                            output.println("Ack sent to " + dataSource + "with sequence #" + seq);
                        }
                        break;
                    
                    }
             	
            }          
        }
    }
 
}