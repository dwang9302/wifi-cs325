package wifi;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;

/**
 * A class for listening to incoming data, check and classify received data
 * 
 * @author Dongni W.
 * @version 11.8.2014
 */
public class Receiver implements Runnable
{
	private ArrayBlockingQueue<byte[]> data; //+queue up received data
	private ArrayBlockingQueue<byte[]> acks; //+queue up received ACKs
	private RF theRF;
	private Packet helper;
	private short ourMAC; //for address selectivity
	
	/**
	 * The constructor of class Recevier
	 * @param theRF
	 * @param data - queue for received data
	 * @param acks - queue for received ACKs
	 */
	public Receiver(short ourMAC, RF theRF, ArrayBlockingQueue<byte[]> data, ArrayBlockingQueue<byte[]> acks)
	{
		this.ourMAC = ourMAC;
		this.theRF = theRF;
		this.data = data;
		this.acks = acks;
		helper = new Packet();
	}
	
	@Override
	public void run() 
	{
		while(true) //keeps listening..
		{
			byte[] received = theRF.receive();
			
			//if the message is not for us, ignore
			if(helper.checkDest(received) != ourMAC)
			{
				received = theRF.receive();
			}
				
			//check to see the frame type 
			if(helper.checkMessageType(received).equals("ACK"))//ACK
			{
				acks.add(received); //store it into the acks (sender may want to check it )
			}
			else //DATA 
			{
				//int sequenceExpected = 0;
				//make and send ack
				//check sequence number,
				//if good: store it to data
				data.add(received);
			}
			
			//sequenceExpected++;
	
		}
		
	}

}
