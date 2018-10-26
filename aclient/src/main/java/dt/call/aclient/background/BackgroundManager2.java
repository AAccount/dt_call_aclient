package dt.call.aclient.background;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;
import dt.call.aclient.background.async.HeartBeatAsync;
import dt.call.aclient.background.async.LoginAsync;

public class BackgroundManager2
{
	private LinkedBlockingQueue<String> eventQ;
	private LinkedBlockingQueue<ScheduledFuture> dealyedQ;
	private Thread backgroundThread;
	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	private static BackgroundManager2 instance = null;
	private static boolean alive = false;
	private static String tag = "EventManager";

	private BackgroundManager2()
	{
		eventQ = new LinkedBlockingQueue<String>();
		dealyedQ = new LinkedBlockingQueue<>();
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
						if(action.equals(Const.BROADCAST_HAS_INTERNET))
						{
							new Thread(new Runnable()
							{
								@Override
								public void run()
								{
									Utils.killSockets();
								}
							}).start();
							clearWaiting();
							Utils.logcat(Const.LOGD, tag, "internet was reconnected by manual detection");
							new LoginAsync().execute();
						}
						else if (action.equals(Const.BROADCAST_RELOGIN))
						{
							//set persistent notification as offline for now while reconnect is trying
							Utils.setNotification(R.string.state_popup_offline, R.color.material_grey, Vars.go2HomePending);

							//pending intents cancelled by command listener to prevent a timing problem where sockets are closed at the same
							//time a heart beat pending intent is fired.

							//if the network is dead then don't bother
							if(!Utils.hasInternet())
							{
								Utils.logcat(Const.LOGD, tag, "No internet detected from relogin");
								handleNoInternet();
								return;
							}

							new LoginAsync().execute();
						}
						else if(action.equals(Const.ALARM_ACTION_HEARTBEAT))
						{
							if (Vars.state == CallState.NONE && Utils.hasInternet())
							{
								//only send if there is internet and not in a call. heartbeat during a call will inject a random byte into
								//	the amr stream and cause a "frameshift mutation" of the amr data ==> turn amr into alien morse code
								Utils.logcat(Const.LOGD, tag, "sending heart beat");
								new HeartBeatAsync().execute();
							}
							else if(Vars.state != CallState.NONE)
							{
								//if there is a call (automatically means there is internet), don't let the heartbeat die out.
								//	keep scheduling and keep ignoring until it's all done
								addDelayedEvent(Const.ALARM_ACTION_HEARTBEAT, Const.STD_TIMEOUT/1000);
							}
							else if (!Utils.hasInternet())
							{
								Utils.logcat(Const.LOGW, tag, "no internet to send heart beat on");

								//heart beat is the ONLY action where the sockets could be live. get rid of them if there is no internet
								// command listener dead (dead by definition), login retry (login failed, no sockets)
								// login bg (failure will issue a retry, success means everything is ok) has_internet(coming from previously dead)
								new Thread(new Runnable()
								{
									@Override
									public void run()
									{
										Utils.killSockets();
									}
								}).start();
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
		instance = null;
	}

	public void clearWaiting()
	{
		eventQ.clear();
		for(ScheduledFuture dealyedEvent : dealyedQ)
		{
			dealyedEvent.cancel(true);
		}
		dealyedQ.clear();
	}

	public void addEvent(String event)
	{
		eventQ.add(event);
	}

	public void addDelayedEvent(final String event, int seconds)
	{
		ScheduledFuture dealyedEvent = scheduler.schedule(new Runnable()
		{
			@Override
			public void run()
			{
				addEvent(event);
			}
		}, seconds, TimeUnit.SECONDS);
		dealyedQ.add(dealyedEvent);
	}
}
