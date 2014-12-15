package wifi;
 
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;
 
/**
 * A class for sending data, waiting for ACKs and handling retransmissions.
 * Important:  check how many bytes are actually being sent.
 * 
 * @author Dongni.W
 * @author Dennis Maguddayao 
 * @version 12.2.2014
 */
public class Sender2 implements Runnable {
    public enum State 
    {START_STATE, AWAIT_CHANNEL, AWAIT_BACKOFF, AWAIT_DIFS, AWAIT_ACK};
   
    private State currentState; 
        
        
    private RF theRF;
    private PrintWriter output; // The output stream we'll write to
    private ArrayBlockingQueue<byte[]> toSend; // +queue up outgoing data
    private ArrayBlockingQueue<byte[]> acks;
    private ArrayBlockingQueue<byte[]> beacon;
     
    private int debugL;
    private int bytesSent; //checks the bytes sent
    
    
    private int difs;
    private int wait;
    private Random rand;
    private int cWindow;
    private int retry; //keeps track of retries.  If it's 3, then we give up
     
    private byte[] ack;
    private long startTime;
    private long endTime;
    private boolean timedOut;
     
    private Packet helper;
     
    private Hashtable<Integer,Integer> sentTo; //create a Table to store which MAC Address's we sent to and the sequence Number for them
    
    byte[] beingSent; //current paket
    private Integer key; //destination mac address, -1 if bcast, 
    private short seq; //current sequence number
    boolean backoff;

    private int sSelect;//determines how fast.  If 0, choose random. Else choose max
    private Status stat;
    
    private int ACKTimeout; //timeout for waiting for an ACK.  
    private Clock clock;
     
 
    /**
     * The constructor of the class Sender
     * 
     * @param theRF
     * @param toSend
     */
    public Sender2(RF theRF, PrintWriter output,
            ArrayBlockingQueue<byte[]> toSend, ArrayBlockingQueue<byte[]> acks, int debugL, Status sta, Clock cl, ArrayBlockingQueue<byte[]> bea)
    {
        stat = sta;
        this.theRF = theRF;
        this.output = output;
        this.toSend = toSend;
        this.acks = acks;
        this.debugL = debugL;
        helper = new Packet();
        clock = cl;
        beacon = bea;
        ACKTimeout = 600 + theRF.aSIFSTime + theRF.aSlotTime; //found on the 802.11 PDF (page 834)(pg 932 on the pdf) //597 was the exact time for roundtrip.
        
        difs = theRF.aSIFSTime + (2* theRF.aSlotTime); //added the standardized DIFS to wait for at the beginning of sending.  This value is 500
        rand = new Random(); //use this for deciding the contension slot.  Better to initialize now than later
        cWindow = theRF.aCWmin; //sets up the contension window
        retry = 0;
        sentTo = new Hashtable<Integer,Integer>(20); //don't know if we need 20 slots.  Doesn't hurt though.

        sSelect = 0;
        currentState = State.START_STATE;
        
    }
 
    /**
     * Change the debug level if received such command from the user
     * 
     * @param newLevel
     */
    public void changeDebug(int newLevel) {
        debugL = newLevel;
    }


    /**
    * Change our exponential backoff.  0 is random, everything else is fixed
    *
    * @param select
    */
    public void changeSelect(int select)
    {
        sSelect = select;
    }
 
    @Override
    public void run() {
        while (true) {
            switch (currentState)
            {
                case START_STATE: //waiting for data 
                     //diagnostic output
                	 if (!beacon.isEmpty()) //pretty much just wait until the the end of a transaction before you send a beacon
                	 {
                		 try
                		 {
                		 beingSent = beacon.take();
                		 output.println("Sending a beacon with time " + helper.checkBeaconTime(helper.checkData(beingSent)));
                		 theRF.transmit(beingSent);
                		 }
                		 catch (InterruptedException e)
                		 {
                			 stat.changeStat(Status.UNSPECIFIED_ERROR);
                        	 e.printStackTrace(); 
                		 }
                	 }
                	 else
                	 {	 
                		 //if(debugL > 0) output.println("Sender: Waiting for data to send.");
                     	timedOut = false;
                     	seq = 0; //default: bcast/initial seq #
                     	backoff = false;
                     	cWindow = theRF.aCWmin;
                     	retry = 0;
                     	if (toSend.isEmpty())
                     	{
                     		break;
                     	}
                     	try 
                     	{
                     		beingSent = toSend.take(); //get the packet
                     		//check for non-bcast, assign sequence number 
                     		key = Integer.valueOf(helper.checkDest(beingSent));
                     		if(key > -1) //not bcast
                     			assignSequence();
                     		currentState = State.AWAIT_CHANNEL; 
                     		if(debugL > 0) output.println("Try sending data");
                     	} catch (InterruptedException e) {
                    	 stat.changeStat(Status.UNSPECIFIED_ERROR);
                    	 e.printStackTrace();
                     	}
            		}
                     break;
                     
                case AWAIT_CHANNEL:
                	if(debugL > 0) output.println("Checking channel");
                	if(theRF.inUse())
                		backoff = true;
                	while(theRF.inUse()) 
                	{
                		try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
							stat.changeStat(Status.UNSPECIFIED_ERROR);
						}//artificial waiting
                	}	
                	if(debugL > 0) output.println("Channel Free");
                	currentState = State.AWAIT_DIFS;
                	break;
                	
                case AWAIT_DIFS:
                	if(debugL > 0) output.println("Waiting difs");
                	try {
            			Thread.sleep(difs);
            		} catch (InterruptedException e) {
            			stat.changeStat(Status.UNSPECIFIED_ERROR);
            			e.printStackTrace();
            		}
                	while(clock.getTime() % 50 != 0)
                	{
                    	try {
                			Thread.sleep(1);
                		} catch (InterruptedException e) {
                			stat.changeStat(Status.UNSPECIFIED_ERROR);
                			e.printStackTrace();
                		}
                	}
                    //add another loop so timing ends up that we end up with a time that ends with 50 ms
                	if(!theRF.inUse() && !backoff)
                	{
                		//try transmit
                       tryTransmit();
                	}
                	else if(theRF.inUse())
                	{
                		//the Await will make backoff = true. 
                		currentState = State.AWAIT_CHANNEL;
                	}
                	else //backoff = true
                	{
                		currentState = State.AWAIT_BACKOFF;
                	}
                	break;
                	
                case AWAIT_BACKOFF:
                	if(debugL > 0) output.println("Waiting exponential backoff");
                	wait = getExpTime();
                	while(wait != 0) //decrement when the channel is not busy
                    {
                         while (!theRF.inUse()) 
                         {
                             wait--;
                             if(wait == 0)
                                 break;
                         }
                     }
                	//try transmit
                    tryTransmit();
                	break;
                	
                case AWAIT_ACK:
                	 startTime = System.currentTimeMillis();
                     while(acks.isEmpty()) //create some artificial timeout time.  
                     {
                         if (System.currentTimeMillis() >= (startTime + ACKTimeout))
                         {
                             timedOut = true;
                             break; //escape the loop.  We timed out
                         }
                     }
                     if(!acks.isEmpty() && !timedOut) //we didn't time out
                     {
                        //Timing to see what would be a good timeout
                        endTime = System.currentTimeMillis();
                        output.println("Took " + (endTime - startTime) + " milliseconds to get an ACK");
                         try
                         {
                             ack = acks.take(); //it shouldn't block out too long.  We made sure that the array wasn't empty
                         } 
                         catch (InterruptedException e) 
                         {
                        	 stat.changeStat(Status.UNSPECIFIED_ERROR);
                             e.printStackTrace();
                         }
                     }
                     if(timedOut || helper.checkSequenceNo(ack) != helper.checkSequenceNo(beingSent))//timed out or we got a bad ACK (hopefully implemented that it's an ACK for a previous iteration.  We also didn't retry too much
                     {
                         retry++; //increase the retry value
                         if(debugL > 0) output.println("Retry number: " + retry);
                         backoff = true; //wait exponential backoff
                         if(retry < theRF.dot11RetryLimit)//we didn't use maximum retries
                         {
                        	 if(debugL > 0) output.println("Beginning retry");
                             beingSent = helper.createMessage("Data", true, helper.checkSource(beingSent), helper.checkDest(beingSent), helper.checkData(beingSent), helper.checkDataSize(beingSent), seq); //resend the data.
                             timedOut = false; //reset timedOut
                             currentState = State.AWAIT_CHANNEL; //start over, but don't fetch no data
                         }
                         else
                         {
                             //we give up.  Reset everything and tell the user that it didn't get sent.
                             stat.changeStat(Status.TX_FAILED);
                             sentTo.put(key,Integer.valueOf((seq - 1))); //reset the sequence value
                             if(debugL > 0) output.println("Data wasn't sent : exceed maximum retries");
                             currentState = State.START_STATE; //proceed to next packet
                         }
                     }
                     else //we're good, proceed to next packet
                     {
                    	 stat.changeStat(Status.TX_DELIVERED);
                    	 currentState = State.START_STATE;
                     }
                     break;
                     
                default:
                	if(debugL > 0) output.println("Unexpected state: update error code");
                	stat.changeStat(Status.UNSPECIFIED_ERROR);

            }
        }
    }

        /**
         * handle transmit
         * called by AWAIT_BACKOFF, AWAIT_DIFS cases.
         */
    	public void tryTransmit()
    	{
    		 bytesSent = theRF.transmit(beingSent);
    		 if(debugL > 0) output.println("Sending out: " + bytesSent + " bytes");
             if(key > -1)  //await ack only if its not bcast
             	currentState = State.AWAIT_ACK;
             else 
             	currentState = State.START_STATE; //bcast, go back to initial condition
    	}
    
    	/**
    	 * assign sequence number to the packet being sent
    	 * @param data
    	 * @return
    	 */
        public void assignSequence()
        {
        	if(sentTo.containsKey(key)) //checks sequence table
            {
        		//grabs the previous sequence number and adds 1;
        		seq = (short)(sentTo.get(key).intValue()); 
                seq++;
                
                //change sequence number : only change if it's different that the default value
            	beingSent = helper.changeSequenceNo(beingSent, seq);
                
                if(seq == 4096)
                {//roll back if reach the max
                	seq = 0;
                }
            }
        	//update: places the new current sequence into the array or make a new entry 
            sentTo.put(key, Integer.valueOf(seq)); 
            if(debugL > 0) output.println("Assigning sequence number: " + seq);      
        }
        
        /**
         * update the exponential back off time 
         * @return exponential back off time 
         */
        public int getExpTime()
        {
        	//update window size for retries
        	if(retry > 0)
        		cWindow*= 2;
        	if(cWindow > theRF.aCWmax) 
        		cWindow = theRF.aCWmax;
        	
        	//command slot selection
        	 if (sSelect != 0)
             {
        		 if(debugL > 0) 
             	{
             		output.println("The size of collision window is " + cWindow);
             		output.println("The slot count is " + cWindow);
             	}
                 return cWindow * theRF.aSlotTime;
             }
        	int window = (rand.nextInt(cWindow) + 1);
        	
        	//output msg
        	if(debugL > 0) 
        	{
        		output.println("The size of collision window is " + cWindow);
        		output.println("The slot count is " + window);
        	}
        	
        	return theRF.aSlotTime * window;
        }
     
}