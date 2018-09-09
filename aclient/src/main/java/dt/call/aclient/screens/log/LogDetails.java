package dt.call.aclient.screens.log;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import dt.call.aclient.Const;
import dt.call.aclient.R;
import dt.call.aclient.sqlite.DBLog;

/**
 * Created by Daniel on 4/10/16.
 */
public class LogDetails extends AppCompatActivity
{

	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_log_details);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		DBLog log = (DBLog)getIntent().getSerializableExtra(Const.EXTRA_LOG);

		TextView date = findViewById(R.id.log_details_value_date);
		date.setText(" " + log.getDateOnly());
		TextView time = findViewById(R.id.log_details_value_time);
		time.setText(" " + log.getTimeOnly());
		TextView tag = findViewById(R.id.log_details_value_tag);
		tag.setText(" " + log.getTag());
		TextView message = findViewById(R.id.log_details_value_message);
		message.setText(log.getMessage());
	}
}
