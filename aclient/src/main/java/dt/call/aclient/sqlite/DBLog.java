package dt.call.aclient.sqlite;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Daniel on 4/6/16.
 */
public class DBLog
{
	private long timestamp;
	private String message;
	private String tag;

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

	public String getHumanReadableTimestamp()
	{
		SimpleDateFormat formatter = new SimpleDateFormat("MMMM dd yyyy @ HH:mm:ss ZZZZ", Locale.US);
		return formatter.format(new Date(timestamp));
	}

	public String getHumanReadableTimestampShort()
	{
		SimpleDateFormat formatter = new SimpleDateFormat("HH:mm", Locale.US);
		return formatter.format(new Date(timestamp));
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
		return getHumanReadableTimestamp() + ": " + message;
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