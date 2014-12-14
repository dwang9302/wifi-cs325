/**
* Holds the status of the Linklayer
*
*/

public class Status
{
	private int status

	public Status()
	{
		status = 1; //initial status
	}

	public void changeStat(int num)
	{
		status = num;
	}

	public int getStat()
	{
		return status;
	}
}