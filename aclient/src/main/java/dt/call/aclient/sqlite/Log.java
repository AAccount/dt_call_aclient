package dt.call.aclient.sqlite;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Daniel on 4/6/16.
 */
public class Log
{
	private long timestamp;
	private String message;

	public Log(long cts, String cmsg)
	{
		timestamp = cts;
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

	public String getMessage()
	{
		return message;
	}

	@Override
	public String toString()
	{
		return getHumanReadableTimestamp() + ": " + message;
	}

	@Override
	public boolean equals(Object other)
	{
		if(!(other instanceof  Log))
		{
			return false;
		}

		Log cast = (Log)other;
		return ((timestamp == cast.timestamp) && (message.equals(cast.message)));
	}
}