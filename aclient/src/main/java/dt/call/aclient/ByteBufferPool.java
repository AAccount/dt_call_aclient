package dt.call.aclient;

//Created November 8, 2018 22:55:10

import java.util.ArrayList;

public class ByteBufferPool
{
	private ArrayList<byte[]> buffers = new ArrayList<byte[]>();
	private int size = 10;
	private int bufferSize;

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
			buffers.add(buffer);
		}
		size = size*2; //always double the buffer pool when it runs out
	}

	public byte[] getByteBuffer()
	{
		if(buffers.isEmpty())
		{
			generateBuffers();
		}
		byte[] buffer = buffers.get(0);
		buffers.remove(0);
		return buffer;
	}

	public void returnBuffer(byte[] buffer)
	{
		buffers.add(buffer);
	}
}
