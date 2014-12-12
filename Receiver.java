package wifi;
 
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;
 
/**
 * A class for listening to incoming data, check and classify received data
 * 
 * @author Dongni W.
 * @author Dennis Maguddayao 
 * @version 11.22.2014
 */
public class Receiver implements Runnable {
    private RF theRF;
    private Packet helper;
    private PrintWriter output; // The output stream we'll write to
    private short seqRecv;
 
    private ArrayBlockingQueue<byte[]> data; // +queue up received data
    private ArrayBlockingQueue<byte[]> acks; // +queue up received ACKs
 
    private short ourMAC; // for address selectivity
    private int debugL;
    private int lastSeq; 
    private Hashtable<Integer,Integer> recvFrom;
 
    /**
     * The constructor of class Recevier
     * 
     * @param theRF
     * @param data
     *            - queue for received data
     * @param acks
     *            - queue for received ACKs
     */
    public Receiver(RF theRF, PrintWriter output,ArrayBlockingQueue<byte[]> data, ArrayBlockingQueue<byte[]> acks, int debugL, short ourMAC) {
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
             
            //output.print("Data received: " + received.length); //use this to check how big the packets we send are
            // check to see the frame type
            String type = helper.checkMessageType(received);
            if (type.equals("Beacon"))
            {
                //add it immediately to the receiver to parse to system time
                data.add(received);

            }
            else if (type.equals("ACK"))// ACK
            {
                acks.add(received); // store received ack into the acks, will be handled in the Sender.                         
            }
            else if(data.length !=4) //limits so that we're only sending a maximum of 4 packets up to the layer above.  So if we're at max size, stop.  Can't exceed 4.  Ignore it.  Beacon ignores this rule though.
            {//Data
                short source = helper.checkSource(received);
                Integer key = Integer.valueOf(source);
                short seq; //expected seqence number
                if(dest > -1) //not a broadcast
                {
                    if(recvFrom.containsKey(key)) //checks 
                    {
                        seq = (short) recvFrom.get(key).intValue(); //grabs the previous sequence number and adds 1;
                         
                        seq++; //maybe should not update the expected seq this early... but will use it for now.
                        if(seq == 4096)
                        {
                            seq = 0;
                        }
                        recvFrom.put(key,Integer.valueOf(seq)); //places the new current sequence into the array
                    }
                    else //the destination wasn't a key before.  Add this to the hashtable
                    {
                        seq = 0;
                        recvFrom.put(key,0); //places the new current sequence into the array
                    }   
                 
                    seqRecv = helper.checkSequenceNo(received); //received sequence number
                 
                    if(seqRecv != seq) 
                    {   
                        if(seqRecv < seq) //case where they're behind (probably resending data).  Change the sequence we expect to be what they want, but don't display the data
                        {
                            if (seqRecv < seq -1) //this isn't a repeat data.  Connection probably lost on other side
                            {
                                data.add(received);
                            }
                            recvFrom.put(key, Integer.valueOf(seqRecv));
                            seq = seqRecv; //
                        }
                        else //case of lost connection at some point. Our sequence number is lower than expected 
                        {
                            recvFrom.put(key,Integer.valueOf(seq-1));
                        }
                        /*if(seqRecv != seq)//detect a gap
                        {
                            output.println("Receiver detected a gap in sequence number");
                            recvFrom.put(key,Integer.valueOf(seqRecv+1));
                        }*/
                    }
                    else //we did get what we expected.
                    {
                        data.add(received); //queue up for transmission
                    }
                 
                    //send back ack
 
                 
                    if (debugL == 1) 
                    {
                        output.println("Data received");
                    }
                    try 
                    {
                        Thread.sleep(theRF.aSIFSTime); //wait SIFS before sending
                    } 
                    catch (InterruptedException e) 
                    {
                        e.printStackTrace();
                    }
                 
                    theRF.transmit(helper.createACK(dest, source, seq));
                 
                    if (debugL == 1) 
                    {
                        output.println("Ack sent to " + source + "with sequence #" + seq);
                    }
                }
                
                else //it was a broadcast we received.  We don't care about sequence #s or ACKS for this
                {
                    data.add(received);
                }
                
            } 
            //later beacon
        }
 
    }
 
}