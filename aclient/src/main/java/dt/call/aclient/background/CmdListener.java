package dt.call.aclient.background;

import android.app.IntentService;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.cert.CertificateException;

import dt.call.aclient.CallState;
import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/19/16.
 */
public class CmdListener extends IntentService
{
	private boolean inputValid = false; //causes the thread to stop whether for technical or paranoia
	private BufferedReader txtin;
	private static final String tag = "CmdListener";

	public CmdListener()
	{
		super(tag);
		try
		{
			txtin = new BufferedReader(new InputStreamReader(Vars.commandSocket.getInputStream()));
			inputValid = true;
		}
		catch (IOException e)
		{
			Utils.logcat(Const.LOGE, tag, "problems getting input reader of command socket: " + e.getStackTrace());
			notifyDead();
		}
	}

	@Override
	protected void onHandleIntent(Intent workIntent)
	{
		//TODO: make sure this doesn't start until you've logged in.
		//	don't want this to catch the login resposne

		Utils.logcat(Const.LOGD, tag, "command listener INTENT SERVICE started");
		while(inputValid)
		{
			//responses from the server command connection will always be in text format
			//timestamp|ring|notavailable|tried_to_call
			//timestamp|ring|available|tried_to_call
			//timestamp|ring|incoming|trying_to_call
			//timestamp|ring|busy|tried_to_call
			//timestamp|lookup|who|exists
			//timestamp|resp|login|sessionid
			//timestamp|call|start|with
			//timestamp|call|reject|by
			//timestamp|call|end|by
			//timestamp|call|drop|sessionid

			try
			{//the async magic here... it will patiently wait until something comes in

				String fromServer = txtin.readLine();
				String[] respContents = fromServer.split("\\|");
				Utils.logcat(Const.LOGD, tag, "Server response raw: " + fromServer);

				//check for properly formatted command
				if(respContents.length != 4)
				{
					Utils.logcat(Const.LOGW, tag, "invalid server response");
					continue;
				}

				//verify timestamp
				long ts = Long.valueOf(respContents[0]);
				if(!Utils.validTS(ts))
				{
					Utils.logcat(Const.LOGW, tag, "Rejecting server response for bad timestamp");
					continue;
				}

				//loook at what the server is telling the call simulator to do
				String serverCommand = respContents[1];
				if(serverCommand.equals("ring"))
				{
					String subCommand = respContents[2];
					String involved = respContents[3];
					if(subCommand.equals("notavailable"))
					{
						if(involved.equals(Vars.callWith))
						{
							Utils.logcat(Const.LOGD, tag, Vars.callWith + " isn't online to talk with right now");
							Vars.callWith = Const.nobody;
							Vars.state = CallState.NONE;
							notifyCanInit(false);
						}
						else
						{
							Utils.logcat(Const.LOGW, tag, "Erroneous user n/a for call from: " + involved + " instead of: " + Vars.callWith);
						}
					}
					else if(subCommand.equals("available"))
					{
						if(involved.equals(Vars.callWith))
						{
							Utils.logcat(Const.LOGD, tag, Vars.callWith + " is online. Ringing him/her now");
							Vars.state = CallState.INIT;
							notifyCanInit(true);
							//if the person is online, the server will ring him
						}
						else
						{
							Utils.logcat(Const.LOGW, tag, "Erroneous user available from: " + involved + " instead of: " + Vars.callWith);
						}
					}
					else if(subCommand.equals("incoming"))
					{
						Utils.logcat(Const.LOGD, tag, "Incoming call from: " + involved);
						Vars.state = CallState.INIT;
						Vars.callWith = involved;
						//TODO: launch incoming call screen
					}
					else if(subCommand.equals("busy"))
					{
						Utils.logcat(Const.LOGD, tag, involved + " is already in a call");
						Vars.state = CallState.NONE;
						Vars.callWith = Const.nobody;
						notifyCanInit(false);
					}
					else
					{
						Utils.logcat(Const.LOGW, tag, "Unknown server RING command: " + fromServer);
					}
				}
				else if (serverCommand.equals("call"))
				{
					String subCommand = respContents[2];
					String involved = respContents[3];
					if(subCommand.equals("start"))
					{
						if(involved.equals(Vars.callWith))
						{
							Utils.logcat(Const.LOGD, tag, Vars.callWith + " picked up. Start talking.");
							Vars.state = CallState.INCALL;

							Utils.logcat(Const.LOGD, tag, "Starting media listener");
							//TODO: start media reader intent service

							Utils.logcat(Const.LOGD, tag, "Starting media writer");
							//TODO: start media write intent service

							//TODO: broadcast intent to the call screen to switch from ringing to talking
						}
						else
						{
							Utils.logcat(Const.LOGW, tag, "Erroneous start call with: " + involved + " instead of: " + Vars.callWith);
						}
					}
					else if(subCommand.equals("reject") || subCommand.equals("end"))
					{
						if(involved.equals(Vars.callWith))
						{
							Utils.logcat(Const.LOGD, tag, Vars.callWith + " rejected your call.");
							Vars.callWith = Const.nobody;
							Vars.state = CallState.NONE;
							//TODO: go back to the home screen
						}
						else
						{
							Utils.logcat(Const.LOGW, tag, "Erroneous call rejected/end with: " + involved + " instead of " + Vars.callWith);
						}

						if(subCommand.equals("end"))
						{//need for force stop the media read thread if it's an end
							//there is no way to kill the thread but to stop the socket to cause an exception
							//	restart after the exception
							Vars.mediaSocket.close();
							try
							{
								Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.expectedCertDump);
								String associateMedia = Utils.getTimestamp() + "|" + Vars.sessionid;
								Vars.mediaSocket.getOutputStream().write(associateMedia.getBytes());
							}
							catch (CertificateException c)
							{
								Utils.logcat(Const.LOGE, tag, "Tring to reestablish media port but somehow the certificate is wrong");
								inputValid = false;
							}
						}
					}
					else if(subCommand.equals("drop"))
					{
						long servSession = Long.valueOf(respContents[3]);
						if(servSession == Vars.sessionid)
						{
							Utils.logcat(Const.LOGD, tag, "Call with " + Vars.callWith + " was dropped");
							Vars.callWith = Const.nobody;
							Vars.state = CallState.NONE;
							//TODO: broadscast intent to call screen

							//there is no way to kill the thread but to stop the socket to cause an exception
							//	restart after the exception
							Vars.mediaSocket.close();
							try
							{
								Vars.mediaSocket = Utils.mkSocket(Vars.serverAddress, Vars.mediaPort, Vars.expectedCertDump);
								String associateMedia = Utils.getTimestamp() + "|" + Vars.sessionid;
								Vars.mediaSocket.getOutputStream().write(associateMedia.getBytes());
							}
							catch (CertificateException c)
							{
								Utils.logcat(Const.LOGE, tag, "trying to reassociate media after dropped call but suddenly the server's cert is invalid");
								inputValid = false;
							}
						}
					}
					else
					{
						Utils.logcat(Const.LOGD, tag, "Erroneous call command: " + fromServer);
					}
				}
				else if(serverCommand.equals("lookup"))
				{
					String who = respContents[2];
					String status = respContents[3];
					Utils.logcat(Const.LOGD, tag, "Lookup of: " + who + " --> " + status);

					Intent lookupStatus = new Intent(Const.NOTIFYHOME);
					lookupStatus.putExtra(Const.TYPE, Const.TYPELOOKUP);
					lookupStatus.putExtra(Const.LOOKUPNAME, who);
					lookupStatus.putExtra(Const.LOOKUPRESULT, status);
					sendBroadcast(lookupStatus);
				}
				else
				{
					Utils.logcat(Const.LOGW, tag, "Unknown command/response: " + fromServer);
				}

			}
			catch (IOException e)
			{
				Utils.logcat(Const.LOGE, tag, "Command socket closed...");
				inputValid = false;
			}
			catch(NumberFormatException n)
			{
				Utils.logcat(Const.LOGE, tag, "string --> # error: " + n.getStackTrace());
			}
			catch(NullPointerException n)
			{
				Utils.logcat(Const.LOGE, tag, "Command socket terminated from the server");
				inputValid = false;
			}
		}
		//TODO: notify someone that the command listener has stopped
		//TODO: figure out if there is internet first before you tell BackgroundManager that command listener died
		notifyDead();
	}

	private void notifyCanInit(boolean canInit)
	{
		Utils.logcat(Const.LOGD, tag, "broadcasting type_init intent to home with status: " + canInit);
		Intent initStatus = new Intent(Const.NOTIFYHOME);
		initStatus.putExtra(Const.TYPE, Const.TYPEINIT);
		initStatus.putExtra(Const.CANINIT, canInit);
		sendBroadcast(initStatus);
	}

	private void notifyDead()
	{
		Utils.logcat(Const.LOGE, tag, "broadcasting dead command listner");
		synchronized (Vars.cmdListenerLock)
		{
			Vars.cmdListenerRunning = false;
		}
		Intent deadBroadcast = new Intent(Const.CMDDEAD);
		sendBroadcast(deadBroadcast);
	}
}
