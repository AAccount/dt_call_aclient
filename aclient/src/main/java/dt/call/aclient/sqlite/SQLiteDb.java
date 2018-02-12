package dt.call.aclient.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

import dt.call.aclient.Const;
import dt.call.aclient.Vars;

/**
 * Created by Daniel on 1/21/16.
 */
public class SQLiteDb extends SQLiteOpenHelper
{
	private static final String dbname = "calldb";
	private static final String tableContacts = "contacts";
	private static final String colName = "name";
	private static final String colNick = "nickname";

	private static final String tableLogs = "logs";
	private static final String colId = "id";
	private static final String colLogTs = "timestamp";
	private static final String colTag = "tag";
	private static final String colMessage = "message";

	private final String mkcontacts = "create table " + tableContacts + " " +
			"(" +
			colName +" text primary key," +
			colNick +" text" +
			")";
	//https://stackoverflow.com/questions/25562508/autoincrement-is-only-allowed-on-an-integer-primary-key-android
	private final String mklogs = "create table " + tableLogs + " " +
			"(" +
			colId + " integer primary key autoincrement," +
			colLogTs + " integer," +
			colTag + " text," +
			colMessage +" text" +
			")";
	private SQLiteDatabase appdb;
	private static SQLiteDb SQLiteDb = null;

	public static synchronized SQLiteDb getInstance(Context c)
	{//http://www.androiddesignpatterns.com/2012/05/correctly-managing-your-sqlite-database.html
		if(SQLiteDb == null)
		{
			SQLiteDb = new SQLiteDb(c);
		}
		return SQLiteDb;
	}

	private SQLiteDb(Context c)
	{
		super(c, dbname, null, 1);
		appdb = getWritableDatabase();
	}

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		db.execSQL(mkcontacts);
		db.execSQL(mklogs);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{

	}

	public void insertContact(Contact contact)
	{
		ContentValues newContact = new ContentValues();
		newContact.put(colName, contact.getName());
		newContact.put(colNick, contact.getNickname());
		appdb.insert(tableContacts, null, newContact);
	}

	public boolean contactExists(Contact contact)
	{
		String[] columnToReturn = {colName};
		String selection = colName + "=?";
		String[] selectionArgs = {contact.getName()};
		Cursor cursor = appdb.query(tableContacts, columnToReturn, selection, selectionArgs, null, null, null);
		if(cursor.moveToFirst())
		{
			cursor.close();
			return true;
		}
		else
		{
			cursor.close();
			return false;
		}
	}

	public void deleteContact(Contact contact)
	{
		appdb.delete(tableContacts, "name=?", new String[]{contact.getName()});
	}

	public void changeNickname(Contact contact)
	{
		ContentValues chNick = new ContentValues();
		chNick.put(colNick, contact.getNickname());
		appdb.update(tableContacts, chNick, "name=?", new String[]{contact.getName()});
	}

	public ArrayList<Contact> getContacts()
	{
		ArrayList<Contact> result = new ArrayList<Contact>();
		Cursor cursor = appdb.rawQuery("select * from contacts", null);
		cursor.moveToFirst();
		while(!cursor.isAfterLast())
		{
			String fromRowName = cursor.getString(cursor.getColumnIndex(colName));
			String fromRowNick = cursor.getString(cursor.getColumnIndex(colNick));
			Contact fromRow = new Contact(fromRowName, fromRowNick);
			result.add(fromRow);
			cursor.moveToNext();
		}
		cursor.close();
		return result;
	}

	public void insertLog(DBLog log)
	{
		if(Vars.SHOUDLOG) //only if there is a need for this, bother to do it
		{
			ContentValues newLog = new ContentValues();
			newLog.put(colLogTs, log.getTimestamp());
			newLog.put(colTag, log.getTag());
			newLog.put(colMessage, log.getMessage());
			appdb.insert(tableLogs, null, newLog);
		}
	}

	public ArrayList<DBLog> getLogs()
	{
		ArrayList<DBLog> result = new ArrayList<DBLog>();

		//clear out old logs. unlikely to be viewed and just slow down the log ui viewer
		final String idquery = "select " + colId + " from logs order by " + colId + " desc limit 1";
		Cursor idcursor = appdb.rawQuery(idquery, null);
		if(idcursor.moveToFirst())
		{
			long mostRecent = idcursor.getLong(0);
			Long tooOld = mostRecent - Const.LOG_LIMIT;
			if(tooOld > 0)
			{
				appdb.delete(tableLogs, colId+"<=?", new String[]{tooOld.toString()});
			}
		}
		idcursor.close();

		//now get the more recent logs
		final String query = "select * from logs order by " + colLogTs + " desc";
		Cursor cursor = appdb.rawQuery(query, null);
		cursor.moveToFirst();
		while(!cursor.isAfterLast())
		{
			long ts = cursor.getLong(cursor.getColumnIndex(colLogTs));
			String tag = cursor.getString(cursor.getColumnIndex(colTag));
			String message = cursor.getString(cursor.getColumnIndex(colMessage));
			DBLog entry = new DBLog(ts, tag, message);
			result.add(entry);
			cursor.moveToNext();
		}
		cursor.close();
		return result;
	}

	public void clearLogs()
	{
		final String drop = "drop table logs";
		appdb.execSQL(drop);
		appdb.execSQL(mklogs);
	}

	public void close()
	{
		appdb.close();
	}
}
