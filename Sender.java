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
 * @version 11.9.2014
 */
public class Sender implements Runnable {
	public enum State {
		START_STATE, AWAIT_CHANNEL, AWAIT_BACKOFF, AWAIT_DIFS, AWAIT_ACK
	};
	
	private RF theRF;
	private PrintWriter output; // The output stream we'll write to
	// private ArrayBlockingQueue<byte[]> acks;
	private ArrayBlockingQueue<byte[]> toSend; // +queue up outgoing data
	private ArrayBlockingQueue<byte[]> acks;
	
	private int debugL;
	private int bytesSent; //checks the bytes sent
	private State currentState;
	private int difs;
	private int wait;
	private Random rand;
	private int cWindow;
	private int retry; //keeps track of retries.  If it's 3, then we give up
	
	private byte[] ack;
	private long timing;
	private static long timeOut = 5000; //timeout after 5 seconds.  Figure out if this is good enough
	private boolean timedOut;
	
	private Packet helper;
	
	private Hashtable<Integer,Integer> sentTo; //create a Table to store which MAC Address's we sent to and the sequence Number for them
	private Integer key;
	private short seq;
	

	/**
	 * The constructor of the class Sender
	 * 
	 * @param theRF
	 * @param toSend
	 */
	public Sender(RF theRF, PrintWriter output,
			ArrayBlockingQueue<byte[]> toSend, ArrayBlockingQueue<byte[]> acks, int debugL) // ,ArrayBlockingQueue<byte[]>
															// acks)
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
		while (true) {
			timedOut = false;
			byte[] beingSent = null;
			wait = difs; //adds in the wait immediately
			try {
				beingSent = toSend.take(); 
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			//assigns a sequence number
			key = Integer.valueOf(helper.checkDest(beingSent));
			if (key.intValue() > -1) //checks to make sure this isn't the broadcast.  Otherwise we shouldn't care about the sequence
			{
				if(sentTo.containsKey(key)) //checks 
				{
					seq = (short)(sentTo.get(key).intValue()); //grabs the previous sequence number and adds 1;
					seq++;
					if(seq == 4096)
					{
						seq = 0;
					}
					sentTo.put(key, Integer.valueOf(seq)); //places the new current sequence into the array
				}
				else //the destination wasn't a key before.  Add this to the hashtable
				{
					seq = 0;
					sentTo.put(key, 0);//easier to do this than casting the short into an Integer
				}
				beingSent = helper.changeSequenceNo(beingSent, seq); //add the appropriate sequence number
			}
			

			// listen first, wait if the channel is busy.  Start with DIFS.  Also checks if this was a retry.  If not, then we're fine
			while (!theRF.inUse() && wait != 0 && !helper.checkRetry(beingSent))
			{
				retry = 0;// can do this in a better way.  Will work on that tomorrow
				wait--; //this is the case where no one is using the channel during the entire time of DIFS
				//exits when either the connection is in Use or if we're done waiting
			}

			if(wait != 0) //we exited the loop early.  Guess we gotta wait now...with even more time
			{
				if(!helper.checkRetry(beingSent)) //it's not a retry.  Wait for DIFS.
				{
					while(wait != 0) //theRF was in use once during DIFS.  Go into the part with a contension window
					{
						while (!theRF.inUse()) //TheRF isn't in use.  Decrement the counter
						{
							wait--;
						}
					}
				}
				//we finished DIFS, now time for the 'exponential backoff'
				wait = (rand.nextInt(cWindow) + 1) * theRF.aSlotTime;
				//now we wait again
				while(wait != 0)
				{
					while (!theRF.inUse()) 
					{
						wait--;
					}
				}
				//now we're done waiting.  It's our turn now...hopefully
			}


			// try transmit
			bytesSent = theRF.transmit(beingSent);

			// implement State switch
			// check for ack...
			if (helper.checkDest(beingSent) != (short) (-1)) //we didn't send this to broadcast
			{


				timing = System.currentTimeMillis();
				while(acks.isEmpty()) //create some artificial timeout time.  
				{
					if (System.currentTimeMillis() >= (timing + timeOut))
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
					retry++ //increase the retry value
					if(retry < theRF.dot11RetryLimit)//we didn't use maximum retries
					{
						toSend.add(helper.createMessage("Data", true, helper.checkSource(beingSent), helper.checkDest(beingSent), helper.checkData(beingSent), helper.checkDataSize(beingSent), seq)); //resend the data.
						cWindow = cWindow * 2; //increase the window size
					}
					else
					{
						//we give up.  Reset everything and tell the user that it didn't get sent.
						output.println("Error: data wasn't sent");
						cWindow = theRF.aCWmin;
						retry = 0;
					}
					//rollback the sequence number
					sentTo.put(key,Integer.valueOf((seq - 1))); //even if this was sequence 0, the method above will grab -1 and make it 0 next time around)
				}
			
				else //we're good.  Reset all values for this send
				{
					cWindow = theRF.aCWmin;
					retry = 0;
				}
			}
				// if there's a good ack
			if (debugL == 1) {
				if(bytesSent > 0)
					output.println("Testing: Data sent"+ "The number of bytes sent: " + bytesSent);
				else 
					output.println("Testing: Data not sent");
			}
			
			
			// if there's no ack or only bad ones (more than likely an older one was sent back), something went wrong
			// window size ++
			// re-send from theBackOff.  (Maybe something like toSend.add(helper.createMessage("Data", True, helper.checkSource(beingSent), helper.checkDest(beingSent), helper.checkData(beingSent), helper.checkDataLength(beingSent), helper.checkSequenceNo(beingSent))))
			//retry++

			//If something is on bcast, we shouldn't care if we get ACKs.  Worry about this later
		}
	}
//	
	//TODO: HANDLE EVENT :COUTNER.. 
//	switch (myState)
//    {
//        case START_STATE:
			//IF..ELSE IF.. ELSE...
//            break;
//            
//        case  XXX
//            break;
//            
//        case ...
	//..................
//            
//        default:
//            System.out.println("Unexpected state!");
//    }
	
}
