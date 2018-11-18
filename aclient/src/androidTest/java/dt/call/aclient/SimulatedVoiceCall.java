package dt.call.aclient;

import android.support.test.runner.AndroidJUnit4;

import com.goterl.lazycode.lazysodium.interfaces.SecretBox;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.Random;

import dt.call.aclient.codec.Opus;
import dt.call.aclient.pool.ByteBufferPool;
import dt.call.aclient.pool.DatagramPacketPool;
import dt.call.aclient.sodium.SodiumUtils;

@RunWith(AndroidJUnit4.class)
public class SimulatedVoiceCall
{
	private static final int WAVBUFFERSIZE = Opus.getWavFrameSize();
	private int txSeq = 0;
	private int SIMULATED_PACKETS = 100000;
	private Random r = new Random();

	@Before
	public void setupSodiumKey()
	{
		//doesn't have to be secure. just need something for testing
		Vars.voiceSymmetricKey = new byte[SecretBox.KEYBYTES];
		r.nextBytes(Vars.voiceSymmetricKey);
	}

	@Test
	public void simulateLongCall()
	{
		SIMULATED_PACKETS = 100000;
		simulateVoiceCall();
	}

	@Test
	public void simulateManyCalls()
	{
		final int ITERATIONS = 100;
		SIMULATED_PACKETS = 1000;
		for(int i=0; i<ITERATIONS; i++)
		{
			System.out.println("simulating call #"+i);
			txSeq = 0;
			simulateVoiceCall();
		}
	}

	private void simulateVoiceCall()
	{
		DatagramPacketPool ENCpacketPool = new DatagramPacketPool(Vars.callServer, Vars.mediaPort);
		final byte[] ENCpacketBuffer = new byte[Const.SIZE_MEDIA];
		final short[] ENCWavBuffer = new short[WAVBUFFERSIZE];
		final byte[] ENCencodedBuffer = new byte[WAVBUFFERSIZE];

		DatagramPacketPool DECpacketPool = new DatagramPacketPool();
		final byte[] DECencbuffer = new byte[WAVBUFFERSIZE];
		final short[] DECwavbuffer = new short[WAVBUFFERSIZE];
		final byte[] packetDecrypted = new byte[Const.SIZE_MAX_UDP];

		Opus.init();
		for(int i=0; i<SIMULATED_PACKETS; i++)
		{
			Arrays.fill(ENCWavBuffer, (short)0);
			for(int j=0; j<ENCWavBuffer.length; j++)
			{
				ENCWavBuffer[j] = (short)r.nextInt(Short.MAX_VALUE+1);
			}

			Arrays.fill(ENCencodedBuffer, (byte)0);
			final int ENCencodeLength = Opus.encode(ENCWavBuffer, ENCencodedBuffer);
			Assert.assertTrue(ENCencodeLength > 0);

			Arrays.fill(ENCpacketBuffer, (byte)0);
			final byte[] txSeqDisassembled = Utils.disassembleInt(txSeq);
			System.arraycopy(txSeqDisassembled, 0, ENCpacketBuffer, 0, Const.SIZEOF_INT);
			txSeq++;
			System.arraycopy(ENCencodedBuffer, 0 , ENCpacketBuffer, Const.SIZEOF_INT, ENCencodeLength);

			final DatagramPacket ENCpacket = ENCpacketPool.getDatagramPacket();
			final byte[] packetBufferEncrypted = ENCpacket.getData();
			final int packetBufferEncryptedLength = SodiumUtils.symmetricEncrypt(ENCpacketBuffer, Const.SIZEOF_INT+ENCencodeLength, Vars.voiceSymmetricKey, packetBufferEncrypted);
			Assert.assertTrue(packetBufferEncryptedLength > 0);

			ENCpacket.setLength(packetBufferEncryptedLength);

			//////////////////////////////////////////////////////////////////////////////
			final DatagramPacket DECreceived = DECpacketPool.getDatagramPacket();
			System.arraycopy(ENCpacket.getData(), 0, DECreceived.getData(), 0, packetBufferEncryptedLength);
			DECreceived.setLength(packetBufferEncryptedLength);

			Arrays.fill(packetDecrypted, (byte)0);
			final int packetDecLength = SodiumUtils.symmetricDecrypt(DECreceived.getData(), DECreceived.getLength(), Vars.voiceSymmetricKey, packetDecrypted);
			DECpacketPool.returnDatagramPacket(DECreceived);
			Assert.assertTrue(packetDecLength == (Const.SIZEOF_INT+ENCencodeLength));

			//(original code not in CallMain.java: check encoder pre-encrypted and decoder post-encrypted contents)
			byte[] DECdecryptedTrimmed = Utils.trimArray(packetDecrypted, packetDecLength);
			byte[] ENCpacketBufferTrimmed = Utils.trimArray(ENCpacketBuffer, Const.SIZEOF_INT+ENCencodeLength);
			Assert.assertArrayEquals(DECdecryptedTrimmed, ENCpacketBufferTrimmed);

			final byte[] sequenceBytes = new byte[Const.SIZEOF_INT];
			System.arraycopy(packetDecrypted, 0, sequenceBytes, 0, Const.SIZEOF_INT);
			final int sequence = Utils.reassembleInt(sequenceBytes);
			Assert.assertTrue(sequence == (txSeq-1));

			//extract the opus chunk
			Arrays.fill(DECencbuffer, (byte)0);
			final int DECencodedLength = packetDecLength - Const.SIZEOF_INT;
			System.arraycopy(packetDecrypted, Const.SIZEOF_INT, DECencbuffer, 0, DECencodedLength);

			//decode opus chunk
			Arrays.fill(DECwavbuffer, (short)0);
			final int frames = Opus.decode(DECencbuffer, DECencodedLength, DECwavbuffer);
			Assert.assertTrue(frames == WAVBUFFERSIZE);

			if(i%1000 == 0)
			{
				System.out.println("iteration: " + i);
			}

			ENCpacketPool.returnDatagramPacket(ENCpacket);
		}
	}
}
