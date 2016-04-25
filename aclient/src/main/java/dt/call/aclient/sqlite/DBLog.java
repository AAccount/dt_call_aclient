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
	private SimpleDateFormat longFormat = new SimpleDateFormat("MMMM dd, yyyy @ HH:mm:ss ZZZZ", Locale.US);
	private SimpleDateFormat shortFormat = new SimpleDateFormat("HH:mm", Locale.US);

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
		return longFormat.format(new Date(timestamp));
	}

	public String getHumanReadableTimestampShort()
	{
		return shortFormat.format(new Date(timestamp));
	}

	public String getShortMessage()
	{
		if(message.length() > 25)
		{
			return message.substring(0, 24) + "...";
		}
		return message;
	}

	public String getFullMessage()
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
		return getHumanReadableTimestamp() + ": " + tag + "/" + message;
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