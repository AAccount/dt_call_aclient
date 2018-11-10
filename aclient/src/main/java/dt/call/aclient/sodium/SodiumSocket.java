package dt.call.aclient.sodium;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.exceptions.SodiumException;
import com.goterl.lazycode.lazysodium.interfaces.Box;

import java.io.IOException;
import java.net.Socket;

import dt.call.aclient.Const;
import dt.call.aclient.Utils;

public class SodiumSocket
{
	private Socket socket;
	private byte[] tcpKey;
	private LazySodiumAndroid lazySodium = new LazySodiumAndroid(new SodiumAndroid());

	public SodiumSocket(String host, int port, byte[]hostPublicSodium) throws SodiumException, IOException
	{
		final byte[] tempPublic = new byte[Box.PUBLICKEYBYTES];
		final byte[] tempPrivate = new byte[Box.SECRETKEYBYTES];
		lazySodium.cryptoBoxKeypair(tempPublic, tempPrivate);

		//setup tcp connection
		socket = new Socket(host, port);
		socket.getOutputStream().write(tempPublic);

		//establish tcp symmetric encryption key
		final byte[] tempKeyResponse = new byte[Const.SIZE_COMMAND];
		final int read = socket.getInputStream().read(tempKeyResponse);
		final byte[] tempKeyResponseTrimmed = Utils.trimArray(tempKeyResponse, read);
		final byte[] tempKeyResponseDec = SodiumUtils.decryptionBuffers.getByteBuffer();
		final int tempKeyResponseDecLength = SodiumUtils.asymmetricDecrypt(tempKeyResponseTrimmed, hostPublicSodium, tempPrivate, tempKeyResponseDec);
		Utils.applyFiller(tempPrivate);
		if(tempKeyResponseDecLength == 0)
		{
			throw new SodiumException("sodium decryption of the TCP key failed");
		}
		tcpKey = Utils.trimArray(tempKeyResponseDec, tempKeyResponseDecLength);
		SodiumUtils.decryptionBuffers.returnBuffer(tempKeyResponseDec);
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
		final byte[] responseDecrypted = SodiumUtils.decryptionBuffers.getByteBuffer();
		final int responseDecryptedLength = SodiumUtils.symmetricDecrypt(rawEnc, length, tcpKey, responseDecrypted);

		if(length < 0 || responseDecryptedLength < 1)
		{
			throw new SodiumException("sodium socket read using symmetric decryption failed");
		}

		byte[] read = Utils.trimArray(responseDecrypted, responseDecryptedLength);
		SodiumUtils.decryptionBuffers.returnBuffer(responseDecrypted);
		return read;
	}

	public void write(String string) throws IOException, SodiumException
	{
		write(string.getBytes());
	}

	private void write(byte[] bytes) throws IOException, SodiumException
	{
		final byte[] output = SodiumUtils.encryptionBuffers.getByteBuffer();
		final int bytesencLength = SodiumUtils.symmetricEncrypt(bytes, bytes.length, tcpKey, output);
		if(bytesencLength == 0)
		{
			throw new SodiumException("sodium socket write using symmetric encryption failed");
		}
		socket.getOutputStream().write(output, 0, bytesencLength);
		SodiumUtils.encryptionBuffers.returnBuffer(output);
	}
}
