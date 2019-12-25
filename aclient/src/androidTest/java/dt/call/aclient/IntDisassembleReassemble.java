package dt.call.aclient;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class IntDisassembleReassemble
{
	@Test
	public void testDiassemblyReassembly()
	{
		for(int i=0; i<Integer.MAX_VALUE; i++)
		{
			final byte[] disaasemble = Utils.disassembleInt(i);
			final int reassemble = Utils.reassembleInt(disaasemble);
			Assert.assertEquals(i, reassemble);

			if(i%1000000 == 0)
			{
				System.out.println("disassemble/reassemble int iteration: "+i);
			}
		}
	}
}
