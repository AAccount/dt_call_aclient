package dt.call.aclient.background;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

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

public class BackgroundManager2
{
	private LinkedBlockingQueue<String> eventQ;
	private HashMap<Integer, ScheduledFuture> delayedEvents = new HashMap<>();
	private Thread backgroundThread;
	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);;
	private int serialCounter = 0;

	private static BackgroundManager2 instance = null;
	private static boolean alive = false;
	private static String tag = "BG2";

	private BackgroundManager2()
	{
		eventQ = new LinkedBlockingQueue<String>();
	}

	public synchronized static BackgroundManager2 getInstance()
	{
		if(instance == null)
		{
			instance = new BackgroundManager2();
			alive = true;
			instance.listen();
		}
		return instance;
	}

	private void listen()
	{
		backgroundThread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					while(alive) //copied straight from BackgroundManager
					{
						String action = eventQ.take();
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
		backgroundThread.setName("BG2-Listener");
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

	public static void stop()
	{
		alive = false;
		instance.backgroundThread.interrupt();
		instance.clearWaiting();
		instance = null;
	}

	public synchronized void clearWaiting()
	{
		eventQ.clear();
		for(Integer delayedEventSerial : delayedEvents.keySet())
		{
			delayedEvents.get(delayedEventSerial).cancel(true);
		}
		delayedEvents.clear();
	}

	public synchronized void addEvent(String event)
	{
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
		ScheduledFuture dealyedEvent = scheduler.schedule(new Runnable()
		{
			@Override
			public void run()
			{
				delayedEvents.remove(serialCounter); //when ran, remove it from the delayed events table
				addEvent(event);
			}
		}, seconds, TimeUnit.SECONDS);
		delayedEvents.put(serialCounter, dealyedEvent);
	}
}
