package dt.call.aclient.screens;

import android.os.Bundle;
import android.app.Activity;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

import dt.call.aclient.CallState;
import dt.call.aclient.R;
import dt.call.aclient.Vars;

public class CallMain extends Activity implements View.OnClickListener
{
	private FloatingActionButton end, mic, speaker;
	private boolean micEnabled, onSpeaker;
	private TextView status, callerid, time;
	private int min=0, sec=0;
	private Timer counter = new Timer();

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_call_main);

		end = (FloatingActionButton)findViewById(R.id.call_main_end_call);
		end.setOnClickListener(this);
		mic = (FloatingActionButton)findViewById(R.id.call_main_mic);
		mic.setOnClickListener(this);
		mic.setEnabled(false);
		speaker = (FloatingActionButton)findViewById(R.id.call_main_spk);
		speaker.setOnClickListener(this);
		speaker.setEnabled(false);
		status = (TextView)findViewById(R.id.call_main_status);
		callerid = (TextView)findViewById(R.id.call_main_callerid);
		time = (TextView)findViewById(R.id.call_main_time);

		//Start showing the counter for how long it's taking to answer the call or how long
		//	the call has been going for
		TimerTask counterTask = new TimerTask()
		{
			@Override
			public void run()
			{
				if(sec == 60)
				{
					min++;
					sec = 0;
					if(Vars.state == CallState.INIT)
					{
						//if the person hasn't answered after 60 seconds
						//	give up. it's probably not going to happen.
						giveUp();
					}
				}
				else
				{
					sec++;
				}
				time.setText(min + ":" + sec);
			}
		};
		counter.schedule(counterTask, 0, 1000);
	}

	@Override
	public void onClick(View v)
	{

	}

	private void giveUp()
	{
		counter.cancel();
	}
}
