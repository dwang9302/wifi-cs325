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
     
    private int debugL;
    private int bytesSent; //checks the bytes sent
    
    
    private int difs;
    private int wait;
    private Random rand;
    private int cWindow;
    private int retry; //keeps track of retries.  If it's 3, then we give up
     
    private byte[] ack;
    private long startTime;
    private static long timeOut = 5000; //timeout after 5 seconds.  Figure out if this is good enough
    private boolean timedOut;
     
    private Packet helper;
     
    private Hashtable<Integer,Integer> sentTo; //create a Table to store which MAC Address's we sent to and the sequence Number for them
    
    byte[] beingSent; //current paket
    private Integer key; //destination mac address, -1 if bcast, 
    private short seq; //current sequence number
    boolean backoff;

    private int sSelect;//determines how fast.  If 0, choose random. Else choose max
     
 
    /**
     * The constructor of the class Sender
     * 
     * @param theRF
     * @param toSend
     */
    public Sender2(RF theRF, PrintWriter output,
            ArrayBlockingQueue<byte[]> toSend, ArrayBlockingQueue<byte[]> acks, int debugL)
    {
        this.theRF = theRF;
        this.output = output;
        this.toSend = toSend;
        this.acks = acks;
        this.debugL = debugL;
        helper = new Packet();
        
        difs = theRF.aSIFSTime + (2* theRF.aSlotTime); //added the standardized DIFS to wait for at the beginning of sending
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
                     output.println("Waiting for data");
                     timedOut = false;
                     seq = 0; //default: bcast/initial seq #
                     backoff = false;
                     cWindow = theRF.aCWmin;
                     retry = 0;
                     //wait = difs; //adds in the wait immediately

                     try 
                     {
                    	 beingSent = toSend.take(); //get the packet
                    	 //check for non-bcast, assign sequence number 
                    	 key = Integer.valueOf(helper.checkDest(beingSent));
                    	 if(key > -1) //not bcast
                    		 assignSequence();
                    	 currentState = State.AWAIT_CHANNEL; 
                    	 output.println("Sending data");
                     } catch (InterruptedException e) {
                    	 e.printStackTrace();
                     }
                     break;
                     
                case AWAIT_CHANNEL:
                	output.println("Checking channel");
                	if(theRF.inUse())
                		backoff = true;
                	while(theRF.inUse()) {
                		try {
							Thread.sleep(RF.aSIFSTime/4);//wait for idle channel
							output.println("Waiting idle");
						} catch (InterruptedException e) {
							e.printStackTrace();
						} 
                	}	
                	currentState = State.AWAIT_DIFS;
                	break;
                	
                case AWAIT_DIFS:
                	output.println("Waiting difs");
                	try {
            			Thread.sleep(difs);
            		} catch (InterruptedException e) {
            			e.printStackTrace();
            		}
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
                	output.println("Waiting exponential backoff");
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
                         if (System.currentTimeMillis() >= (startTime + timeOut))
                         {
                             timedOut = true;
                             break; //escape the loop.  We timed out
                         }
                     }
                     if(!acks.isEmpty() && !timedOut) //we didn't time out
                     {
                         try
                         {
                             ack = acks.take(); //it shouldn't block out too long.  We made sure that the array wasn't empty
                         } 
                         catch (InterruptedException e) 
                         {
                             e.printStackTrace();
                         }
                     }
                     if(timedOut || helper.checkSequenceNo(ack) != helper.checkSequenceNo(beingSent))//timed out or we got a bad ACK (hopefully implemented that it's an ACK for a previous iteration.  We also didn't retry too much
                     {
                         retry++; //increase the retry value
                         output.println("Retry number: " + retry);
                         if(retry < theRF.dot11RetryLimit)//we didn't use maximum retries
                         {
                             output.println("Beginning retry");
                             beingSent = helper.createMessage("Data", true, helper.checkSource(beingSent), helper.checkDest(beingSent), helper.checkData(beingSent), helper.checkDataSize(beingSent), seq); //resend the data.
                             timedOut = false; //reset timedOut
                             currentState = State.AWAIT_CHANNEL; //start over, but don't fetch no data
                         }
                         else
                         {
                             //we give up.  Reset everything and tell the user that it didn't get sent.
                             output.println("Data wasn't sent : exceed maximum retries d");
                             currentState = State.START_STATE; //proceed to next packet
                         }
                     }
                     else //we're good, proceed to next packet
                     {
                             currentState = State.START_STATE;
                     }
                     break;
                     
                default:
                	output.println("Unexpected state: update error code");

            }
        }
    }
                 
            
            //Worry about disconnect later..
                        
//                         //rollback the sequence number
           
//                         if(timedOut)
//                             sentTo.put(key,Integer.valueOf((seq - 1))); //even if this was sequence 0, the method above will grab -1 and make it 0 next time around)
//                         else
//                         {//bad ack
//                             output.println("Setting sequence number to " + Integer.valueOf(helper.checkSequenceNo(ack)));
//                             sentTo.put(key,Integer.valueOf(helper.checkSequenceNo(ack)-1));
//                         }
//                         }
                  
    
             
        /**
         * handle transmit
         * called by AWAIT_BACKOFF, AWAIT_DIFS cases.
         */
    	public void tryTransmit()
    	{
    		 bytesSent = theRF.transmit(beingSent);
             output.println("Sending out: " + bytesSent + " bytes");
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
            output.println("Sending sequence number: " + seq);      
        }
        
        /**
         * update the exponential back off time 
         * @return exponential back off time 
         */
        public int getExpTime()
        {
        	//update window size for retries
            if (sSelect != 0)
            {
                return theRF.aCWmax * theRF.aSlotTime;
            }
        	if(retry > 0)
        		cWindow*= 2;
        	if(cWindow > theRF.aCWmax) 
        		cWindow = theRF.aCWmax;
        
        	int window = (rand.nextInt(cWindow) + 1);
        	return theRF.aSlotTime * window;
        }
     
}