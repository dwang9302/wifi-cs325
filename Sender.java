package wifi;

import java.util.concurrent.ArrayBlockingQueue;

//import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;

public class Sender implements Runnable
{
	//private ArrayBlockingQueue<byte[]> acks;
	private ArrayBlockingQueue<byte[]> toSend; //+queue up outgoing data
	private RF theRF;
	private int sequence;
	
	public Sender(RF theRF, ArrayBlockingQueue<byte[]> toSend) //,ArrayBlockingQueue<byte[]> acks)
	{
		this.theRF = theRF;
		this.toSend = toSend;
		//this.acks = acks;
		sequence = 0;
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
		
		//listen first
		while(theRF.inUse())
		{
			try {
				Thread.sleep(theRF.aSlotTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}  
	    }
		
		
		//set the sequence number 
		
		//try transmit
	    theRF.transmit(beingSent);
	    
	    //check for ack... if there's none, something went wrong
		//window
		//resend..recursive 
	    
	    sequence++;
	}
}
