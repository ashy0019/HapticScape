package com.ashy0019.hapticscape.music;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.DoubleConsumer;

public final class MusicSignalAnalyzer
{
	static final int FFT_SIZE = 2_048;
	private static final int HOP_SIZE = FFT_SIZE / 2;
	private static final double SILENCE_RMS = 0.0015;
	private static final double BASS_MINIMUM_HZ = 40.0;
	private static final double BASS_MAXIMUM_HZ = 220.0;
	private static final double FLUX_MAXIMUM_HZ = 2_000.0;

	private final DoubleConsumer levelListener;
	private final float[] sampleWindow = new float[FFT_SIZE];
	private final RealFft fft = new RealFft(FFT_SIZE);
	private double[] previousMagnitudes = new double[FFT_SIZE / 2 + 1];
	private int bufferedSamples;
	private int sampleRate;
	private double bassBaseline;
	private double fluxBaseline;
	private double smoothedLevel;
	private volatile MusicResponse response = MusicResponse.RHYTHMIC;

	public MusicSignalAnalyzer(DoubleConsumer levelListener)
	{
		this.levelListener = Objects.requireNonNull(levelListener, "levelListener");
	}

	public void setResponse(MusicResponse response)
	{
		this.response = Objects.requireNonNull(response, "response");
	}

	public void accept(float[] samples, int incomingSampleRate)
	{
		Objects.requireNonNull(samples, "samples");
		if (incomingSampleRate <= 0)
		{
			throw new IllegalArgumentException("Sample rate must be positive");
		}
		if (sampleRate != incomingSampleRate)
		{
			reset(incomingSampleRate);
		}

		int sourceOffset = 0;
		while (sourceOffset < samples.length)
		{
			int copied = Math.min(samples.length - sourceOffset, FFT_SIZE - bufferedSamples);
			System.arraycopy(samples, sourceOffset, sampleWindow, bufferedSamples, copied);
			bufferedSamples += copied;
			sourceOffset += copied;
			if (bufferedSamples == FFT_SIZE)
			{
				analyzeWindow();
				System.arraycopy(sampleWindow, HOP_SIZE, sampleWindow, 0, HOP_SIZE);
				bufferedSamples = HOP_SIZE;
			}
		}
	}

	public void clear()
	{
		reset(sampleRate);
		levelListener.accept(0.0);
	}

	private void analyzeWindow()
	{
		double rms = calculateRms();
		if (rms < SILENCE_RMS)
		{
			previousMagnitudes = new double[previousMagnitudes.length];
			smoothedLevel = response.smooth(smoothedLevel, 0.0);
			levelListener.accept(smoothedLevel < 0.01 ? 0.0 : smoothedLevel);
			return;
		}

		double[] magnitudes = fft.magnitudes(sampleWindow);
		int bassStart = frequencyBin(BASS_MINIMUM_HZ);
		int bassEnd = frequencyBin(BASS_MAXIMUM_HZ);
		int fluxEnd = frequencyBin(FLUX_MAXIMUM_HZ);
		double bass = bandRootMeanSquare(magnitudes, bassStart, bassEnd);
		double flux = positiveSpectralFlux(magnitudes, bassStart, fluxEnd);

		if (bassBaseline == 0.0)
		{
			bassBaseline = bass;
		}
		if (fluxBaseline == 0.0)
		{
			fluxBaseline = Math.max(flux, 0.0001);
		}

		double bassRatio = bass / Math.max(0.0001, bassBaseline);
		double fluxRatio = flux / Math.max(0.0001, fluxBaseline);
		double normalizedBass = clamp((bassRatio - 0.55) / 1.45);
		double normalizedOnset = clamp((fluxRatio - 1.15) / 3.0);
		double target = response.combine(normalizedBass, normalizedOnset);
		smoothedLevel = response.smooth(smoothedLevel, target);
		levelListener.accept(smoothedLevel);

		bassBaseline += (bass - bassBaseline) * (bass < bassBaseline ? 0.08 : 0.012);
		fluxBaseline += (flux - fluxBaseline) * (flux < fluxBaseline ? 0.08 : 0.025);
		previousMagnitudes = magnitudes;
	}

	private double calculateRms()
	{
		double energy = 0.0;
		for (float sample : sampleWindow)
		{
			energy += sample * sample;
		}
		return Math.sqrt(energy / sampleWindow.length);
	}

	private int frequencyBin(double frequency)
	{
		return Math.max(1, Math.min(
			FFT_SIZE / 2,
			(int) Math.ceil(frequency * FFT_SIZE / sampleRate)
		));
	}

	private static double bandRootMeanSquare(double[] magnitudes, int start, int end)
	{
		double energy = 0.0;
		int count = Math.max(1, end - start + 1);
		for (int index = start; index <= end; index++)
		{
			energy += magnitudes[index] * magnitudes[index];
		}
		return Math.sqrt(energy / count);
	}

	private double positiveSpectralFlux(double[] magnitudes, int start, int end)
	{
		double flux = 0.0;
		int count = Math.max(1, end - start + 1);
		for (int index = start; index <= end; index++)
		{
			flux += Math.max(0.0, magnitudes[index] - previousMagnitudes[index]);
		}
		return flux / count;
	}

	private void reset(int newSampleRate)
	{
		sampleRate = newSampleRate;
		bufferedSamples = 0;
		bassBaseline = 0.0;
		fluxBaseline = 0.0;
		smoothedLevel = 0.0;
		Arrays.fill(sampleWindow, 0.0f);
		previousMagnitudes = new double[previousMagnitudes.length];
	}

	private static double clamp(double value)
	{
		return Math.max(0.0, Math.min(1.0, value));
	}
}
