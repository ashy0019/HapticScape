package com.ashy0019.hapticscape.update;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.function.Consumer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class UpdateCheckService implements AutoCloseable
{
	private static final String LATEST_RELEASE_URL =
		"https://api.github.com/repos/ashy0019/HapticScape/releases/latest";

	private final OkHttpClient httpClient;
	private final Gson gson;
	private volatile Call activeCall;

	public UpdateCheckService(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	public void check(String installedVersion, Consumer<UpdateCheckResult> listener)
	{
		Call previous = activeCall;
		if (previous != null)
		{
			previous.cancel();
		}

		Request request = new Request.Builder()
			.url(LATEST_RELEASE_URL)
			.header("Accept", "application/vnd.github+json")
			.header("User-Agent", "HapticScape-Updater")
			.build();
		Call call = httpClient.newCall(request);
		activeCall = call;
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call failedCall, IOException exception)
			{
				if (!failedCall.isCanceled())
				{
					listener.accept(UpdateCheckResult.failure("Update check failed"));
				}
			}

			@Override
			public void onResponse(Call responseCall, Response response)
			{
				try (Response closeableResponse = response)
				{
					if (!closeableResponse.isSuccessful() || closeableResponse.body() == null)
					{
						listener.accept(UpdateCheckResult.failure(
							"GitHub returned HTTP " + closeableResponse.code()));
						return;
					}
					LatestRelease latest = gson.fromJson(
						closeableResponse.body().charStream(),
						LatestRelease.class);
					if (latest == null || latest.tagName == null || latest.draft || latest.prerelease)
					{
						listener.accept(UpdateCheckResult.failure("GitHub returned invalid release data"));
						return;
					}
					String version = stripPrefix(latest.tagName);
					listener.accept(UpdateCheckResult.success(
						version,
						UpdateVersion.isNewer(version, installedVersion)));
				}
				catch (Exception exception)
				{
					listener.accept(UpdateCheckResult.failure("GitHub returned invalid release data"));
				}
			}
		});
	}

	@Override
	public void close()
	{
		Call call = activeCall;
		activeCall = null;
		if (call != null)
		{
			call.cancel();
		}
	}

	private static String stripPrefix(String value)
	{
		return value.startsWith("v") || value.startsWith("V")
			? value.substring(1)
			: value;
	}

	private static final class LatestRelease
	{
		@com.google.gson.annotations.SerializedName("tag_name")
		private String tagName;
		private boolean draft;
		private boolean prerelease;
	}
}
