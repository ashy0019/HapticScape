package com.ashy0019.hapticscape.music;

final class RealFft
{
	private final int size;
	private final double[] real;
	private final double[] imaginary;
	private final double[] window;
	private final double windowScale;

	RealFft(int size)
	{
		if (size < 2 || Integer.bitCount(size) != 1)
		{
			throw new IllegalArgumentException("FFT size must be a power of two");
		}
		this.size = size;
		this.real = new double[size];
		this.imaginary = new double[size];
		this.window = new double[size];

		double windowSum = 0.0;
		for (int index = 0; index < size; index++)
		{
			window[index] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * index / (size - 1));
			windowSum += window[index];
		}
		windowScale = 2.0 / windowSum;
	}

	double[] magnitudes(float[] samples)
	{
		if (samples.length != size)
		{
			throw new IllegalArgumentException("Expected exactly " + size + " samples");
		}

		for (int index = 0; index < size; index++)
		{
			real[index] = samples[index] * window[index];
			imaginary[index] = 0.0;
		}
		transform();

		double[] magnitudes = new double[size / 2 + 1];
		for (int index = 0; index < magnitudes.length; index++)
		{
			magnitudes[index] = Math.hypot(real[index], imaginary[index]) * windowScale;
		}
		return magnitudes;
	}

	private void transform()
	{
		for (int index = 1, reversed = 0; index < size; index++)
		{
			int bit = size >> 1;
			while ((reversed & bit) != 0)
			{
				reversed ^= bit;
				bit >>= 1;
			}
			reversed ^= bit;
			if (index < reversed)
			{
				swap(real, index, reversed);
				swap(imaginary, index, reversed);
			}
		}

		for (int length = 2; length <= size; length <<= 1)
		{
			double angle = -2.0 * Math.PI / length;
			double rootReal = Math.cos(angle);
			double rootImaginary = Math.sin(angle);
			for (int start = 0; start < size; start += length)
			{
				double twiddleReal = 1.0;
				double twiddleImaginary = 0.0;
				for (int offset = 0; offset < length / 2; offset++)
				{
					int even = start + offset;
					int odd = even + length / 2;
					double oddReal = real[odd] * twiddleReal
						- imaginary[odd] * twiddleImaginary;
					double oddImaginary = real[odd] * twiddleImaginary
						+ imaginary[odd] * twiddleReal;
					double evenReal = real[even];
					double evenImaginary = imaginary[even];
					real[even] = evenReal + oddReal;
					imaginary[even] = evenImaginary + oddImaginary;
					real[odd] = evenReal - oddReal;
					imaginary[odd] = evenImaginary - oddImaginary;

					double nextReal = twiddleReal * rootReal
						- twiddleImaginary * rootImaginary;
					twiddleImaginary = twiddleReal * rootImaginary
						+ twiddleImaginary * rootReal;
					twiddleReal = nextReal;
				}
			}
		}
	}

	private static void swap(double[] values, int left, int right)
	{
		double temporary = values[left];
		values[left] = values[right];
		values[right] = temporary;
	}
}
