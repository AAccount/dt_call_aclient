package dt.call.aclient;

import android.support.test.runner.AndroidJUnit4;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import dt.call.aclient.sodium.SodiumUtils;

@RunWith(AndroidJUnit4.class)
public class SodiumSymmetricDecrypt
{
	private static final String TEST_MESSAGE = "symmetric testing testing 1 2 3 blah blah blah filler content";
	private LazySodiumAndroid lazySodium= new LazySodiumAndroid(new SodiumAndroid());

	private static final int BUFFER_SIZE = 4096;
	private byte[] encryptionBuffer = new byte[BUFFER_SIZE];
	private byte[] decryptionBuffer = new byte[BUFFER_SIZE];

	@Before
	public void setupKeys()
	{
		Vars.voiceSymmetricKey = lazySodium.randomBytesBuf(SecretBox.KEYBYTES);
	}

	//input[nonce|message length|encrypted]

	@Before
	public void clearBuffers()
	{
		Arrays.fill(encryptionBuffer, (byte)0);
		Arrays.fill(decryptionBuffer, (byte)0);
	}

	@Test
	public void ensureEmptyInputNothing()
	{
		final byte[] nothing = new byte[0];
		final int decryptedLength = SodiumUtils.symmetricDecrypt(nothing, Vars.voiceSymmetricKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureOnlyLengthAndNonceNothing()
	{
		final int nonceLength = SecretBox.NONCEBYTES;
		final int headerLength = Const.SIZEOF_INT + nonceLength;
		final int encryptedLength = SodiumUtils.symmetricEncrypt(TEST_MESSAGE.getBytes(), TEST_MESSAGE.length(), Vars.voiceSymmetricKey, encryptionBuffer);

		final int decryptedLength = SodiumUtils.symmetricDecrypt(encryptionBuffer, headerLength, Vars.voiceSymmetricKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureOnlyLengthNonceWithLargerThanSetupLengthNothing()
	{
		final int nonceLength = SecretBox.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.SIZEOF_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf(nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		for(int i = 0; i<Const.SIZEOF_INT; i++)
		{
			lengthAndNonce[nonceLength+i] = Byte.MAX_VALUE;
		}

		final int decryptedLength = SodiumUtils.symmetricDecrypt(lengthAndNonce, lengthAndNonce.length, Vars.voiceSymmetricKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureFakeCiphertextNothing()
	{
		final int fakeCiphertextLength = 10;
		final int nonceLength = SecretBox.NONCEBYTES;
		final byte[] nonce = lazySodium.randomBytesBuf(nonceLength);
		System.arraycopy(nonce, 0, encryptionBuffer, 0, nonceLength);

		encryptionBuffer[nonceLength+(Const.SIZEOF_INT -1)] = fakeCiphertextLength;

		final byte[] fakeCiphertext = lazySodium.randomBytesBuf(fakeCiphertextLength);
		System.arraycopy(fakeCiphertext, 0, encryptionBuffer, Const.SIZEOF_INT + nonceLength, fakeCiphertextLength);

		final int setupLength = nonceLength + Const.SIZEOF_INT + fakeCiphertextLength;
		final int decryptedLength = SodiumUtils.symmetricDecrypt(encryptionBuffer, setupLength, Vars.voiceSymmetricKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureTamperedNonceNothing()
	{
		final int encryptedLength = SodiumUtils.symmetricEncrypt(TEST_MESSAGE.getBytes(), TEST_MESSAGE.length(), Vars.voiceSymmetricKey, encryptionBuffer);
		encryptionBuffer[0]++;

		final int decryptedLength = SodiumUtils.symmetricDecrypt(encryptionBuffer, encryptedLength, Vars.voiceSymmetricKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureTamperedCiphertextNothing()
	{
		final int encryptedLength = SodiumUtils.symmetricEncrypt(TEST_MESSAGE.getBytes(), TEST_MESSAGE.length(), Vars.voiceSymmetricKey, encryptionBuffer);
		final int nonceLength = SecretBox.NONCEBYTES;
		encryptionBuffer[nonceLength + Const.SIZEOF_INT]++;

		final int decryptedLength = SodiumUtils.symmetricDecrypt(encryptionBuffer, encryptedLength, Vars.voiceSymmetricKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureSodiumSymmDecryptActuallyWorks()
	{
		final int encryptedLength = SodiumUtils.symmetricEncrypt(TEST_MESSAGE.getBytes(), TEST_MESSAGE.length(), Vars.voiceSymmetricKey, encryptionBuffer);

		final int decryptedLength = SodiumUtils.symmetricDecrypt(encryptionBuffer, encryptedLength, Vars.voiceSymmetricKey, decryptionBuffer);
		final String message = new String(decryptionBuffer, 0, decryptedLength);
		Assert.assertEquals(message, TEST_MESSAGE);
	}

	@Test
	public void ensureSodiumSymmEndurance()
	{
		final int SIMULATED_TOTAL_PACKETS = 90000;
		for(int i=0; i<SIMULATED_TOTAL_PACKETS; i++)
		{
			final int PACKETSIZE = 1200;
			final byte[] sampleVoice = lazySodium.randomBytesBuf(PACKETSIZE);
			final int encryptedLength = SodiumUtils.symmetricEncrypt(sampleVoice, sampleVoice.length, Vars.voiceSymmetricKey, encryptionBuffer);
			final int decryptedLength = SodiumUtils.symmetricDecrypt(encryptionBuffer, encryptedLength, Vars.voiceSymmetricKey, decryptionBuffer);
			final byte[] decryptedTrimmed = Utils.trimArray(decryptionBuffer, decryptedLength);
			Assert.assertArrayEquals(sampleVoice, decryptedTrimmed);
			Assert.assertTrue(decryptedLength == PACKETSIZE);
		}
	}

	@Test
	public void ensureWrongKeyNothing()
	{
		byte[] originalSymmetricKey = new byte[SecretBox.KEYBYTES];
		System.arraycopy(Vars.voiceSymmetricKey, 0, originalSymmetricKey, 0, SecretBox.KEYBYTES);
		Vars.voiceSymmetricKey = lazySodium.randomBytesBuf(SecretBox.KEYBYTES);

		final int wrongSetup = SodiumUtils.symmetricEncrypt(TEST_MESSAGE.getBytes(), TEST_MESSAGE.length(), Vars.voiceSymmetricKey, encryptionBuffer);

		System.arraycopy(originalSymmetricKey, 0, Vars.voiceSymmetricKey, 0, SecretBox.KEYBYTES);
		final int decryptedLength = SodiumUtils.symmetricDecrypt(encryptionBuffer, wrongSetup, Vars.voiceSymmetricKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}

	@Test
	public void ensureCompleteGarbageNothing()
	{
		final int decryptedLength = SodiumUtils.symmetricDecrypt(TEST_MESSAGE.getBytes(), TEST_MESSAGE.length(), Vars.voiceSymmetricKey, decryptionBuffer);
		Assert.assertTrue(decryptedLength == 0);
	}
}
