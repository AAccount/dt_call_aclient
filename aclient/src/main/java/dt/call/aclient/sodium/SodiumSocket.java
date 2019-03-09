package dt.call.aclient.sodium;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.exceptions.SodiumException;
import com.goterl.lazycode.lazysodium.interfaces.Box;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;
import dt.call.aclient.Vars;

public class SodiumSocket
{
	private Socket socket;
	private byte[] tcpKey;
	private byte[] decryptionBuffer = new byte[Const.SIZE_COMMAND];
	private byte[] encryptionBuffer = new byte[Const.SIZE_COMMAND*2];//x2: to account for any overhead

	public SodiumSocket(String host, int port, byte[]hostPublicSodium) throws SodiumException, IOException
	{
		final byte[] tempPublic = new byte[Box.PUBLICKEYBYTES];
		final byte[] tempPrivate = new byte[Box.SECRETKEYBYTES];
		final LazySodiumAndroid lazySodium = new LazySodiumAndroid(new SodiumAndroid());
		lazySodium.cryptoBoxKeypair(tempPublic, tempPrivate);

		//setup tcp connection
		final byte[] encTempPublic = new byte[Box.SEALBYTES + Box.PUBLICKEYBYTES];
		lazySodium.cryptoBoxSeal(encTempPublic, tempPublic, tempPublic.length, Vars.serverPublicSodium);
		socket = new Socket(host, port);
		socket.getOutputStream().write(encTempPublic);

		//establish tcp symmetric encryption key
		final int read = socket.getInputStream().read(encryptionBuffer);
		final int tempKeyResponseDecLength = SodiumUtils.asymmetricDecrypt(encryptionBuffer, read, hostPublicSodium, tempPrivate, decryptionBuffer);
		Utils.applyFiller(tempPrivate);
		if(tempKeyResponseDecLength == 0)
		{
			throw new SodiumException("sodium decryption of the TCP key failed");
		}
		tcpKey = Utils.trimArray(decryptionBuffer, tempKeyResponseDecLength);
	}

	public byte[] getTcpKey()
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
		Utils.applyFiller(tcpKey);
		Utils.applyFiller(decryptionBuffer);
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
		Arrays.fill(decryptionBuffer, (byte)0);
		final int responseDecryptedLength = SodiumUtils.symmetricDecrypt(rawEnc, length, tcpKey, decryptionBuffer);

		if(length < 0 || responseDecryptedLength < 1)
		{
			throw new SodiumException("sodium socket read using symmetric decryption failed");
		}

		return Utils.trimArray(decryptionBuffer, responseDecryptedLength);
	}

	public void write(String string) throws IOException, SodiumException
	{
		write(string.getBytes());
	}

	private void write(byte[] bytes) throws IOException, SodiumException
	{
		Arrays.fill(encryptionBuffer, (byte)0);
		final int bytesencLength = SodiumUtils.symmetricEncrypt(bytes, bytes.length, tcpKey, encryptionBuffer);
		if(bytesencLength == 0)
		{
			throw new SodiumException("sodium socket write using symmetric encryption failed");
		}
		socket.getOutputStream().write(encryptionBuffer, 0, bytesencLength);
	}
}
