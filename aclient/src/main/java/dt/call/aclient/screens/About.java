package dt.call.aclient.screens;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import dt.call.aclient.R;
/**
 * Created by Daniel on 07/03/2016
 */
public class About extends AppCompatActivity
{

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
	}
}
