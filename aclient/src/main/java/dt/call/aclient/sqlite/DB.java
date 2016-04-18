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
public class DB extends SQLiteOpenHelper
{
	private static final String dbname = "calldb";
	private static final String tableContacts = "contacts";
	private static final String colName = "name";
	private static final String colNick = "nickname";

	private static final String tableHistory = "history";
	private static final String colHistoryTs = "timestamp";
	private static final String colWho = "who";
	private static final String colType = "call_type";

	private static final String tableLogs = "logs";
	private static final String colId = "id";
	private static final String colLogTs = "timestamp";
	private static final String colTag = "tag";
	private static final String colMessage = "message";

	private final String mkhistory = "create table " + tableHistory + " " +
			"(" +
			colHistoryTs + " integer primary key, " +
			colWho + " text, " +
			colType + " integer " +
			")";
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

	public DB(Context c)
	{
		super(c, dbname, null, 1);
		appdb = getWritableDatabase();
	}

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		db.execSQL(mkcontacts);
		db.execSQL(mkhistory);
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

	public void insertHistory(History history)
	{
		ContentValues newHistory = new ContentValues();
		newHistory.put(colHistoryTs, history.getTimestamp());
		newHistory.put(colWho, history.getWho().getName());
		newHistory.put(colType, history.getType());
		appdb.insert(tableHistory, null, newHistory);
	}

	public ArrayList<History> getRecentHistory()
	{
		ArrayList<History> result = new ArrayList<History>();
		final String query = "select timestamp, who, nickname, call_type\n" +
				"from history left join contacts\n" +
				"on history.who = contacts.name\n" +
				"limit 20";
		Cursor cursor = appdb.rawQuery(query, null);
		cursor.moveToFirst();
		while(!cursor.isAfterLast())
		{
			long ts = cursor.getLong(cursor.getColumnIndex(colHistoryTs));
			int type = cursor.getInt(cursor.getColumnIndex(colType));
			String name = cursor.getString(cursor.getColumnIndex(colWho));
			String nickname = cursor.getString(cursor.getColumnIndex(colNick));
			Contact contact = new Contact(name, nickname);
			History history = new History(ts, contact, type);
			result.add(history);
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
			newLog.put(colMessage, log.getFullMessage());
			appdb.insert(tableLogs, null, newLog);
		}
	}

	public ArrayList<DBLog> getLogs()
	{
		ArrayList<DBLog> result = new ArrayList<DBLog>();
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
