package wifi;

import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;

/**
 * A class for sending data, waiting for ACKs and handling retransmissions.
 * 
 * @author Dongni.W
 * @version 11.8.2014
 */
public class Sender implements Runnable
{
	//private ArrayBlockingQueue<byte[]> acks;
	private ArrayBlockingQueue<byte[]> toSend; //+queue up outgoing data
	private RF theRF;
	
	public Sender(RF theRF, ArrayBlockingQueue<byte[]> toSend) //,ArrayBlockingQueue<byte[]> acks)
	{
		this.theRF = theRF;
		this.toSend = toSend;
		//this.acks = acks;
		
	}

	@Override
	public void run() 
	{
		byte[] beingSent = null;
		try {
			beingSent = toSend.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} 
		
		//listen first, wait if the channel is busy
		while(theRF.inUse())
		{
			try {
				Thread.sleep(theRF.aSlotTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}  
	    }
		
		//try transmit
	    theRF.transmit(beingSent);
	    
	    //implement State switch ... 
	    //check for ack... if there's none, something went wrong
		//window size ++
		//re-send
	}
}
