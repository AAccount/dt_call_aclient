package dt.call.aclient.sqlite;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Daniel on 4/6/16.
 */
public class DBLog implements Serializable
{
	private long timestamp;
	private String message;
	private String tag;
	private SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("EEEE MMMM dd, yyyy", Locale.US);
	private SimpleDateFormat timeOnlyFormat = new SimpleDateFormat("HH:mm:ss ZZZZ", Locale.US);
	private SimpleDateFormat shortFormat = new SimpleDateFormat("MMM dd @ HH:mm:ss", Locale.US);

	public DBLog(String ctag, String cmsg)
	{
		timestamp = System.currentTimeMillis();
		tag = ctag;
		message = cmsg;
	}

	public DBLog(long cts, String ctag, String cmsg)
	{
		timestamp = cts;
		tag = ctag;
		message = cmsg;
	}

	public long getTimestamp()
	{
		return timestamp;
	}

	public String getDateOnly()
	{
		return dateOnlyFormat.format(new Date(timestamp));
	}

	public String getTimeOnly()
	{
		return timeOnlyFormat.format(new Date(timestamp));
	}


	public String getHumanReadableTimestampShort()
	{
		return shortFormat.format(new Date(timestamp));
	}

	public String getMessage()
	{
		return message;
	}

	public String getTag()
	{
		return tag;
	}

	@Override
	public String toString()
	{
		return getDateOnly() + " @ " + getTimeOnly() + ": " + tag + "/" + message;
	}

	@Override
	public boolean equals(Object other)
	{
		if(!(other instanceof DBLog))
		{
			return false;
		}

		DBLog cast = (DBLog)other;
		return ((timestamp == cast.timestamp) && (message.equals(cast.message)));
	}
}