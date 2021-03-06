package dt.call.aclient.pool;

//Created November 8, 2018 22:55:10

import java.util.Arrays;
import java.util.LinkedList;

public class ByteBufferPool
{
	private LinkedList<byte[]> buffers = new LinkedList<byte[]>();
	private int size = 10;
	private int bufferSize;
	private final Object bufferLock = new Object();

	public ByteBufferPool(int cbufferSize)
	{
		bufferSize = cbufferSize;
		generateBuffers();
	}

	private void generateBuffers()
	{
		for(int i=0; i<size; i++)
		{
			byte[] buffer = new byte[bufferSize];
			buffers.push(buffer);
		}
		size = size*2; //always double the buffer pool when it runs out
	}

	public byte[] getByteBuffer()
	{
		byte[] buffer;
		synchronized(bufferLock)
		{
			if(buffers.isEmpty())
			{
				generateBuffers();
			}
			buffer = buffers.pop();
			Arrays.fill(buffer, (byte) 0);
		}
		return buffer;
	}

	public void returnBuffer(byte[] buffer)
	{
		synchronized(bufferLock)
		{
			if(buffer != null)
			{
				buffers.push(buffer);
			}
		}
	}
}
