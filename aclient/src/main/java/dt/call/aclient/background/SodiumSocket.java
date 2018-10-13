package dt.call.aclient.background;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.exceptions.SodiumException;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;
import com.goterl.lazycode.lazysodium.utils.KeyPair;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;

public class SodiumSocket
{
	private Socket socket;
	private byte[] tcpKey;
	LazySodiumAndroid lazySodium = new LazySodiumAndroid(new SodiumAndroid());

	public SodiumSocket(String host, int port, byte[]hostPublicSodium) throws SodiumException, IOException
	{
		final KeyPair tempKeys = lazySodium.cryptoBoxKeypair();
		final byte[] tempPublic = tempKeys.getPublicKey().getAsBytes();
		final byte[] tempPrivate = tempKeys.getSecretKey().getAsBytes();

		//setup tcp connection
		socket = new Socket(host, port);
		socket.getOutputStream().write(tempPublic);

		//establish tcp symmetric encryption key
		final byte[] tempKeyResponse = new byte[Const.SIZE_COMMAND];
		final int read = socket.getInputStream().read(tempKeyResponse);
		final byte[] tempKeyResponseTrimmed = Utils.trimArray(tempKeyResponse, read);
		final byte[] tempKeyResponseDec = Utils.sodiumAsymDecrypt(tempKeyResponseTrimmed, hostPublicSodium, tempPrivate);
		if(tempKeyResponseDec == null)
		{
			throw new SodiumException("sodium decryption of the TCP key failed");
		}
		tcpKey = tempKeyResponseDec;

	}

	byte[] getTcpKey()
	{
		return tcpKey;
	}

	public void close()
	{
		try
		{
			socket.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		byte[] filler =  lazySodium.randomBytesBuf(SecretBox.KEYBYTES);
		System.arraycopy(filler, 0, tcpKey, 0, SecretBox.KEYBYTES);
	}

	public String readString(int max) throws IOException, SodiumException
	{
		final byte[] rawBytes = read(max);
		return new String(rawBytes, 0, rawBytes.length);
	}

	private byte[] read(int max) throws IOException, SodiumException
	{
		final byte[] rawEnc = new byte[max];
		final int length = socket.getInputStream().read(rawEnc);
		final byte[] response = Utils.sodiumSymDecrypt(Utils.trimArray(rawEnc, length), tcpKey);

		//on the off chance the socket crapped out right from the get go, now you'll know
		if(length < 0)
		{
			throw new SodiumException("sodium socket read using symmetric decryption failed");
		}
		return response;
	}

	public void write(String string) throws IOException, SodiumException
	{
		write(string.getBytes());
	}

	private void write(byte[] bytes) throws IOException, SodiumException
	{
		final byte[] bytesenc = Utils.sodiumSymEncrypt(bytes, tcpKey);
		if(bytesenc == null)
		{
			throw new SodiumException("sodium socket write using symmetric encryption failed");
		}
		socket.getOutputStream().write(bytesenc);
	}
}
