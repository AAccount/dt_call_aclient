package dt.call.aclient;

import android.support.test.runner.AndroidJUnit4;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SodiumSymmetricDecrypt
{
	private static final String TEST_MESSAGE = "symmetric testing testing 1 2 3 blah blah blah filler content";
	private LazySodiumAndroid lazySodium= new LazySodiumAndroid(new SodiumAndroid());

	@Before
	public void setupKeys()
	{
		Vars.sodiumSymmetricKey = lazySodium.randomBytesBuf(SecretBox.KEYBYTES);
	}

	//input[nonce|message length|encrypted]

	@Test
	public void ensureEmptyInputNull()
	{
		final byte[] nothing = new byte[0];
		final byte[] decrypted = Utils.sodiumSymDecrypt(nothing, Vars.sodiumSymmetricKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthAndNonceNull()
	{
		final int nonceLength = SecretBox.NONCEBYTES;
		final int headerLength = Const.JAVA_MAX_PRECISION_INT + nonceLength;
		final byte[] wholeSetup = Utils.sodiumSymEncrypt(TEST_MESSAGE.getBytes(), Vars.sodiumSymmetricKey);
		byte[] lengthAndNonce = new byte[headerLength];
		System.arraycopy(wholeSetup, 0, lengthAndNonce, 0, headerLength);

		final byte[] decrypted = Utils.sodiumSymDecrypt(lengthAndNonce, Vars.sodiumSymmetricKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthNonceWithRiggedLengthNull()
	{
		final int nonceLength = SecretBox.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf(nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		//length = 100
		lengthAndNonce[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = 100;

		final byte[] decrypted = Utils.sodiumSymDecrypt(lengthAndNonce, Vars.sodiumSymmetricKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthNonceWithNegativeLengthNull()
	{
		final int nonceLength = SecretBox.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf(nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		//length = 100
		lengthAndNonce[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = -100;

		final byte[] decrypted = Utils.sodiumSymDecrypt(lengthAndNonce, Vars.sodiumSymmetricKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureOnlyLengthNonceWithLargerThanIntLengthNull()
	{
		final int nonceLength = SecretBox.NONCEBYTES;
		byte[] lengthAndNonce = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength];
		final byte[] nonce = lazySodium.randomBytesBuf(nonceLength);
		System.arraycopy(nonce, 0, lengthAndNonce, 0, nonceLength);

		for(int i=0; i<Const.JAVA_MAX_PRECISION_INT; i++)
		{
			lengthAndNonce[nonceLength+i] = Byte.MAX_VALUE;
		}

		final byte[] decrypted = Utils.sodiumSymDecrypt(lengthAndNonce, Vars.sodiumSymmetricKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureFakeCiphertextNull()
	{
		final int fakeCiphertextLength = 10;
		final int nonceLength = SecretBox.NONCEBYTES;
		byte[] setup = new byte[Const.JAVA_MAX_PRECISION_INT + nonceLength + fakeCiphertextLength];
		final byte[] nonce = lazySodium.randomBytesBuf(nonceLength);
		System.arraycopy(nonce, 0, setup, 0, nonceLength);

		setup[nonceLength+(Const.JAVA_MAX_PRECISION_INT-1)] = fakeCiphertextLength;

		final byte[] fakeCiphertext = lazySodium.randomBytesBuf(fakeCiphertextLength);
		System.arraycopy(fakeCiphertext, 0, setup, Const.JAVA_MAX_PRECISION_INT + nonceLength, fakeCiphertextLength);

		final byte[] decrypted = Utils.sodiumSymDecrypt(setup, Vars.sodiumSymmetricKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureTamperedNonceNull()
	{
		byte[] setup = Utils.sodiumSymEncrypt(TEST_MESSAGE.getBytes(), Vars.sodiumSymmetricKey);
		setup[0]++;

		final byte[] decrypted = Utils.sodiumSymDecrypt(setup, Vars.sodiumSymmetricKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureTamperedCiphertextNull()
	{
		byte[] setup = Utils.sodiumSymEncrypt(TEST_MESSAGE.getBytes(), Vars.sodiumSymmetricKey);
		final int nonceLength = SecretBox.NONCEBYTES;
		setup[nonceLength + Const.JAVA_MAX_PRECISION_INT]++;

		final byte[] decrypted = Utils.sodiumSymDecrypt(setup, Vars.sodiumSymmetricKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureTrimmedLengthNoCrash()
	{
		byte[] setup = Utils.sodiumSymEncrypt(TEST_MESSAGE.getBytes(), Vars.sodiumSymmetricKey);
		final int nonceLength = SecretBox.NONCEBYTES;
		byte endLengthByte = setup[nonceLength + Const.JAVA_MAX_PRECISION_INT - 1];
		endLengthByte = (byte)((int)endLengthByte / 2);
		setup[nonceLength + Const.JAVA_MAX_PRECISION_INT - 1] = endLengthByte;

		final byte[] decrypted = Utils.sodiumSymDecrypt(setup, Vars.sodiumSymmetricKey);
		assertTrue(decrypted.length == endLengthByte);
		//it has no way of knowing what the original length was, the best
		//it can do is just trim the output to whatever length you say
	}

	@Test
	public void ensureSodiumSymmDecryptActuallyWorks()
	{
		final byte[] setup = Utils.sodiumSymEncrypt(TEST_MESSAGE.getBytes(), Vars.sodiumSymmetricKey);

		final byte[] decrypted = Utils.sodiumSymDecrypt(setup, Vars.sodiumSymmetricKey);
		final String message = new String(decrypted);
		assertTrue(message.equals(TEST_MESSAGE));
	}

	@Test
	public void ensureSodiumSymmEndurance()
	{
		final int SIMULATED_TOTAL_PACKETS = 90000;
		for(int i=0; i<SIMULATED_TOTAL_PACKETS; i++)
		{
			final int PACKETSIZE = 1200;
			final byte[] sampleVoice = lazySodium.randomBytesBuf(PACKETSIZE);
			final byte[] setup = Utils.sodiumSymEncrypt(sampleVoice, Vars.sodiumSymmetricKey);
			final byte[] decrypted = Utils.sodiumSymDecrypt(setup, Vars.sodiumSymmetricKey);
			assertTrue(Arrays.equals(decrypted, sampleVoice));
		}
	}

	@Test
	public void ensureWrongKeyNull()
	{
		byte[] originalSymmetricKey = new byte[SecretBox.KEYBYTES];
		System.arraycopy(Vars.sodiumSymmetricKey, 0, originalSymmetricKey, 0, SecretBox.KEYBYTES);
		Vars.sodiumSymmetricKey = lazySodium.randomBytesBuf(SecretBox.KEYBYTES);

		final byte[] wrongSetup = Utils.sodiumSymEncrypt(TEST_MESSAGE.getBytes(), Vars.sodiumSymmetricKey);

		System.arraycopy(originalSymmetricKey, 0, Vars.sodiumSymmetricKey, 0, SecretBox.KEYBYTES);
		final byte[] decrypted = Utils.sodiumSymDecrypt(wrongSetup, Vars.sodiumSymmetricKey);
		assertNull(decrypted);
	}

	@Test
	public void ensureCompleteGarbageNull()
	{
		final byte[] nothing = Utils.sodiumSymDecrypt(TEST_MESSAGE.getBytes(), Vars.sodiumSymmetricKey);
		assertNull(nothing);
	}
}
