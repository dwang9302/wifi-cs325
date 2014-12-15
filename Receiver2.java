package wifi;
 
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.zip.CRC32;

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
    private Hashtable<Integer,Integer> recvFrom; //last recved sequence #
    
    private Status stat;
    private Clock cl;
    private long recvTime;
    
    private CRC32 checksum;
    private long csLocal;
    private byte[] recevied;
 
    /**
     * The constructor of class Recevier
     * 
     * @param theRF
     * @param data
     *            - queue for received data
     * @param acks
     *            - queue for received ACKs
     */
    public Receiver2(RF theRF, PrintWriter output,ArrayBlockingQueue<byte[]> data, 
    		ArrayBlockingQueue<byte[]> acks, int debugL, short ourMAC, Status sta, Clock cl) {
    	stat = sta;
    	this.cl = cl;
    	this.theRF = theRF;
        this.output = output;
        helper = new Packet();
        checksum = new CRC32();
 
        this.data = data;
        this.acks = acks;
 
        this.ourMAC = ourMAC;
        this.debugL = debugL;
        recvFrom = new Hashtable<Integer, Integer>(20);
        
        this.recevied = null;
         
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
        	if(debugL > 0) output.println("Receiver: waiting");
            recevied = theRF.receive();//block until a packet is received
            if(debugL > 0) output.println("Receiver: received something");
            //Address selectivity
            //if the message is not for us, ignore. 
            short dest = helper.checkDest(recevied);
            if(dest < 0) {
            	stat.changeStat(Status.BAD_ADDRESS); 
            }
            while( dest != ourMAC && dest != (short)(-1)) //destination is not our mac & it's not a broadcast.
            {
                recevied = theRF.receive(); //block until another good packet is received
                dest= helper.checkDest(recevied); //we need this otherwise we're stuck in an endless loop
            }   
            
            // check to see the frame type
            String type = helper.checkMessageType(recevied);
            switch(type) {
             	case "ACK":
             		acks.add(recevied); // store received ack into the acks, will be handled in the Sender.
             		break;
             		
             	case "Beacon":
             		recvTime = helper.checkBeaconTime(helper.checkData(recevied));
             		if(debugL > 0) output.println("Receiver: Beacon received from " + helper.checkSource(recevied) + " at "+ cl.getTime() + " with a time of " + recvTime);
             		recvTime = helper.checkBeaconTime(helper.checkData(recevied));
             		if(recvTime > cl.getTime()) {
             			if(debugL > 0) output.println("Updated time to from" + cl.getTime()+ " to "+ recvTime);
             			cl.changeTime(recvTime);
             		}
             		break;
             	case "Data":
             		//handle checksum
                    checksum.update(helper.checkData(recevied));
                    if(checksum.getValue() != helper.checkCS(recevied))//if the checksum does not match 
                    {
                    	output.println("wrong CRC");
                    }
             		if(debugL > 0) output.print("Receiver: Data received: " + recevied.length + ", aimed for " + dest + " at " + cl.getLocalTime() + "\n"); //use this to check how big the packets we send are
             		if(dest == -1) //it was a broadcast we received
                    	data.add(recevied);
                    else
                    {
                    	//check sender address
           
                 		short dataSource = helper.checkSource(recevied);
                        Integer key = Integer.valueOf(dataSource);
                    	short seq = -1; //last recv sequence number; default = -1: no-previous packet from this address
                    	//get last received sequence number from the table
                    	if(recvFrom.containsKey(key)) 
                    		seq = (short) recvFrom.get(key).intValue(); //grabs the previous sequence number 
                    	
                    	
                    	//check sequence number of current packet
                    	seqRecv = helper.checkSequenceNo(recevied); //received sequence number
                    	
                    	if(seqRecv < seq) //4095 case or connection lost
                    	{
                    		seq = -1; //reset 
                    	}
                    	
                    	if(seqRecv > seq) //packet expected
                    	{	
                    		data.add(recevied); //display
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
                        	stat.changeStat(Status.UNSPECIFIED_ERROR);
                            e.printStackTrace();
                        }
                    	
                        //always send ACK back
                        theRF.transmit(helper.createACK(dest, dataSource, seqRecv));
                        if (debugL == 1) {
                            output.println("Ack sent to " + dataSource + "with sequence # " + seqRecv);
                        }
                        break;
                    
                    }
             	
            }          
        }
    }
 
}