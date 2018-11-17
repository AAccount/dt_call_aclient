package dt.call.aclient;

import android.support.test.runner.AndroidJUnit4;

import com.goterl.lazycode.lazysodium.interfaces.SecretBox;

import org.junit.Assert;
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
	private Thread fakeDTOperatorThread;
	private int txSeq = 0;
	private int SIMULATED_PACKETS = 100000;

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
		SIMULATED_PACKETS = 100;
		for(int i=0; i<ITERATIONS; i++)
		{
			System.out.println("simulating call #"+i);
			simulateVoiceCall();
		}
	}

	private void simulateVoiceCall()
	{

		Vars.voiceSymmetricKey = new byte[SecretBox.KEYBYTES];
		Random r = new Random();
		r.nextBytes(Vars.voiceSymmetricKey);

		DatagramPacketPool encoderPacketPool = new DatagramPacketPool(Vars.callServer, Vars.mediaPort);
		final byte[] packetBuffer = new byte[Const.SIZE_MEDIA];
		final short[] encoderWavBuffer = new short[WAVBUFFERSIZE];
		final byte[] encoderEncodedBuffer = new byte[WAVBUFFERSIZE];

		ByteBufferPool udpBufferPool = new ByteBufferPool(Const.SIZE_MAX_UDP);
		DatagramPacketPool decoderPacketPool = new DatagramPacketPool();
		final byte[] decoderEncbuffer = new byte[WAVBUFFERSIZE];
		final short[] decoderWavbuffer = new short[WAVBUFFERSIZE];
		final byte[] packetDecrypted = SodiumUtils.decryptionBuffers.getByteBuffer();

		Opus.init();
		for(int i=0; i<SIMULATED_PACKETS; i++)
		{
			Arrays.fill(encoderWavBuffer, (short)0);
			for(int j=0; j<encoderWavBuffer.length; j++)
			{
				encoderWavBuffer[j] = (short)r.nextInt(Short.MAX_VALUE+1);
			}

			Arrays.fill(encoderEncodedBuffer, (byte)0);
			final int encoderEncodeLength = Opus.encode(encoderWavBuffer, encoderEncodedBuffer);
			Assert.assertTrue(encoderEncodeLength > 0);

			//integer broken up as: 12,345,678: [12,34,56,78.....voice....]
			Arrays.fill(packetBuffer, (byte)0);
			final byte[] txSeqDisassembled = Utils.disassembleInt(txSeq, 4);
			System.arraycopy(txSeqDisassembled, 0, packetBuffer, 0, 4);
			txSeq++;
			System.arraycopy(encoderEncodedBuffer, 0 , packetBuffer, 4, encoderEncodeLength);

			final byte[] packetBufferEncrypted = SodiumUtils.encryptionBuffers.getByteBuffer();
			final int packetBufferEncryptedLength = SodiumUtils.symmetricEncrypt(packetBuffer, 4+encoderEncodeLength, Vars.voiceSymmetricKey, packetBufferEncrypted);
			Assert.assertTrue(packetBufferEncryptedLength > 0);

			final DatagramPacket sending = encoderPacketPool.getDatagramPacket();
			sending.setData(packetBufferEncrypted);
			sending.setLength(packetBufferEncryptedLength);

			//////////////////////////////////////////////////////////////////////////////
			final DatagramPacket received = decoderPacketPool.getDatagramPacket();
			received.setData(udpBufferPool.getByteBuffer());
			received.setLength(Const.SIZE_MAX_UDP);
			System.arraycopy(sending.getData(), 0, received.getData(), 0, packetBufferEncryptedLength);
			received.setLength(packetBufferEncryptedLength);

			Arrays.fill(packetDecrypted, (byte)0);
			final int packetDecLength = SodiumUtils.symmetricDecrypt(received.getData(), received.getLength(), Vars.voiceSymmetricKey, packetDecrypted);
			Assert.assertTrue(packetDecLength == (4+encoderEncodeLength));

			final byte[] sequenceBytes = new byte[4];
			System.arraycopy(packetDecrypted, 0, sequenceBytes, 0, 4);
			final int sequence = Utils.reassembleInt(sequenceBytes);
			Assert.assertTrue(sequence == (txSeq-1));

			//extract the opus chunk
			Arrays.fill(decoderEncbuffer, (byte)0);
			final int decoderEndoeLength = packetDecLength - 4;
			System.arraycopy(packetDecrypted, 4, decoderEncbuffer, 0, decoderEndoeLength);

			//decode opus chunk
			Arrays.fill(decoderWavbuffer, (short)0);
			final int frames = Opus.decode(decoderEncbuffer, decoderEndoeLength, decoderWavbuffer);
			Assert.assertTrue(frames == WAVBUFFERSIZE);

			if(i%1000 == 0)
			{
				System.out.println("iteration: " + i);
			}
			SodiumUtils.encryptionBuffers.returnBuffer(packetBufferEncrypted);
		}
	}
}
