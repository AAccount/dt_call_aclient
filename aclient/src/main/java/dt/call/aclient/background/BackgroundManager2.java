package dt.call.aclient.background;

import android.app.IntentService;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.HeartBeatAsync;
import dt.call.aclient.background.async.LoginAsync;

public class BackgroundManager2 extends IntentService
{
	private LinkedBlockingQueue<String> eventQ;
	private HashMap<Integer, ScheduledFuture> delayedEvents = new HashMap<>();
	private Thread backgroundThread;
	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);;
	private int serialCounter = 0;

	private static boolean alive = false;
	private static String tag = "BG2";

	public BackgroundManager2()
	{
		super(tag);
		eventQ = new LinkedBlockingQueue<String>();
		alive = true;
		Utils.logcat(Const.LOGD, tag, "created bg2");
	}

	protected void onHandleIntent(@Nullable Intent intent)
	{
		Vars.bg2 = this;
		backgroundThread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "start bg2 thread");
				try
				{
					while(alive) //copied straight from BackgroundManager
					{
						String action = eventQ.take();
						Utils.logcat(Const.LOGD, tag, "bg2 listener got: "+action);
						if(action.equals(Const.EVENT_HAS_INTERNET))
						{
							Utils.killSockets();
							clearWaiting();
							Utils.logcat(Const.LOGD, tag, "internet was reconnected by manual detection");
							new LoginAsync().execute();
						}
						else if (action.equals(Const.EVENT_RELOGIN))
						{
							//set persistent notification as offline for now while reconnect is trying
							Utils.setNotification(R.string.state_popup_offline, R.color.material_grey, Vars.go2HomePending);

							//delayed events cancelled by command listener to prevent a timing problem where sockets are closed at the same
							//time a heart beat pending intent is fired.

							//if the network is dead then don't bother
							if(!Utils.hasInternet())
							{
								Utils.logcat(Const.LOGD, tag, "No internet detected from relogin");
								handleNoInternet();
								continue; //NEVER "return;" in the thread. it stops the thread
							}

							new LoginAsync().execute();
						}
						else if(action.equals(Const.EVENT_HEARTBEAT))
						{
							if (Utils.hasInternet())
							{
								Utils.logcat(Const.LOGD, tag, "sending heart beat");
								new HeartBeatAsync().execute();
							}
							else //if (!Utils.hasInternet())
							{
								Utils.logcat(Const.LOGW, tag, "no internet to send heart beat on");
								Utils.killSockets();
								handleNoInternet();
							}
						}
					}
				}
				catch (InterruptedException e)
				{
					System.out.println("thread interrupted. Exiting event manager");
				}
				clearWaiting();
			}
		});
		backgroundThread.setName(tag+"-Listener");
		backgroundThread.start();
	}

	private void handleNoInternet()
	{
		clearWaiting();

		//for android 7.0+ manually trigger a "connectivity action" when the internet comes back to sign on again
		if(Const.NEEDS_MANUAL_INTERNET_DETECTION)
		{
			ComponentName jobServiceReceiver = new ComponentName(Const.PACKAGE_NAME, JobServiceReceiver.class.getName());
			JobInfo.Builder builder = new JobInfo.Builder(1, jobServiceReceiver).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
			JobScheduler jobScheduler = (JobScheduler) Vars.applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
			int result = jobScheduler.schedule(builder.build());
			Utils.logcat(Const.LOGD, tag, "putting in a new job with status: " + result);
		}
	}

	public void stop()
	{
		alive = false;
		backgroundThread.interrupt();
		clearWaiting();
	}

	public synchronized void clearWaiting()
	{
		Utils.logcat(Const.LOGD, tag, "eventQ and delayedEvents cleared");
		eventQ.clear();
		for(Integer delayedEventSerial : delayedEvents.keySet())
		{
			delayedEvents.get(delayedEventSerial).cancel(true);
		}
		delayedEvents.clear();
	}

	public synchronized void addEvent(String event)
	{
		Utils.logcat(Const.LOGD, tag, "requesting " + event);
		try
		{
			eventQ.put(event);
		}
		catch (InterruptedException e)
		{
			Utils.dumpException(tag, e);
		}
	}

	public synchronized void addDelayedEvent(final String event, int seconds)
	{
		serialCounter++; //create a serial number for this delayed event
		ScheduledFuture delayedEvent = scheduler.schedule(new Runnable()
		{
			@Override
			public void run()
			{
				Utils.logcat(Const.LOGD, tag, "running scheduled event: " + event + "#"+serialCounter);
				delayedEvents.remove(serialCounter); //when ran, remove it from the delayed events table
				addEvent(event);
			}
		}, seconds, TimeUnit.SECONDS);
		delayedEvents.put(serialCounter, delayedEvent);
		Utils.logcat(Const.LOGD, tag, "scheduling " + event + " in " + seconds
				+" " + delayedEvent.getDelay(TimeUnit.MINUTES));

	}
}
