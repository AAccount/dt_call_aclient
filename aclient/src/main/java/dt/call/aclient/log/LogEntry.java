package dt.call.aclient.log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Daniel on 2/23/19.
 */

public class LogEntry
{
	private final String message;
	private final String tag;
	private final Date now = new Date();
	private static final SimpleDateFormat shortFormat = new SimpleDateFormat("MMM dd;HH:mm", Locale.US);

	public LogEntry(String ctag, String cmessage)
	{
		tag = ctag;
		message = cmessage;
	}

	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder()
				.append(shortFormat.format(now))
				.append(" ")
				.append(tag)
				.append(": ")
				.append(message)
				.append("\n");
		return builder.toString();
	}
}
