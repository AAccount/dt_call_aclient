package dt.call.aclient.sqlite;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dt.call.aclient.Const;

/**
 * Created by Daniel on 1/24/16.
 */
public class History
{
	private long timestamp;
	private Contact who;
	private int type;

	public History(long cts, Contact cwho, int ctype)
	{
		timestamp = cts;
		who = cwho;
		type = ctype;
	}

	public long getTimestamp()
	{
		return timestamp;
	}

	public Contact getWho()
	{
		return who;
	}

	public int getType()
	{
		return type;
	}

	public String toString()
	{
		Date date = new Date(timestamp);
		SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd yyyy HH:mm:ss.zzzz", Locale.US);
		String dateString = dateFormat.format(date);

		if(type == Const.CALLOUTGOING)
		{
			return "Outgoing call @ " + dateString + " to: " + who;
		}
		else if (type == Const.CALLINCOMING)
		{
			return "Incoming call @ " + dateString + " from: " + who;
		}
		else //if (type == Const.CALLMISSED)
		{
			return "Incoming call @ " + dateString + " from: " + who;
		}
	}
}
