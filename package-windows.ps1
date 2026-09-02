param(
	[ValidatePattern('^[0-9]+(?:\.[0-9]+){0,3}$')]
	[string] $Version = '1.0.0'
)

$ErrorActionPreference = 'Stop'

if ($env:OS -ne 'Windows_NT')
{
	throw 'This packaging script must be run on Windows.'
}

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleWrapper = Join-Path $projectRoot 'gradlew.bat'

$cscCandidates = @(
	(Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'),
	(Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe')
)
$cscPath = $cscCandidates | Where-Object { Test-Path $_ -PathType Leaf } | Select-Object -First 1

if (-not $cscPath)
{
	throw 'The Windows .NET Framework C# compiler was not found. Enable .NET Framework 4.x in Windows Features and try again.'
}

Push-Location $projectRoot
try
{
	Write-Host 'Running tests and building the HapticScape client...'
	& $gradleWrapper "-PappVersion=$Version" clean test verifyClientJar collectRuntimeLicenses
	if ($LASTEXITCODE -ne 0)
	{
		throw "Gradle failed with exit code $LASTEXITCODE."
	}

	$jarPath = Join-Path $projectRoot 'build\libs\hapticscape-client.jar'
	if (-not (Test-Path $jarPath -PathType Leaf))
	{
		throw "The expected client JAR was not created: $jarPath"
	}

	$packageRoot = Join-Path $projectRoot 'build\windows-package'
	$appDirectory = Join-Path $packageRoot 'HapticScape'
	$appFilesDirectory = Join-Path $appDirectory 'app'
	$licensesDirectory = Join-Path $appFilesDirectory 'licenses'
	$distributionDirectory = Join-Path $projectRoot 'build\distribution'
	$nativeTestDirectory = Join-Path $projectRoot 'build\native-tests'

	switch ($env:PROCESSOR_ARCHITECTURE)
	{
		'ARM64' { $architecture = 'arm64' }
		'AMD64' { $architecture = 'x64' }
		default { $architecture = ([string] $env:PROCESSOR_ARCHITECTURE).ToLowerInvariant() }
	}

	if (Test-Path $packageRoot)
	{
		Remove-Item -Recurse -Force $packageRoot
	}
	New-Item -ItemType Directory -Force $appFilesDirectory | Out-Null
	New-Item -ItemType Directory -Force $licensesDirectory | Out-Null
	New-Item -ItemType Directory -Force $distributionDirectory | Out-Null
	New-Item -ItemType Directory -Force $nativeTestDirectory | Out-Null

	Copy-Item $jarPath (Join-Path $appFilesDirectory 'hapticscape-client.jar')
	Copy-Item (Join-Path $projectRoot 'LICENSE') (Join-Path $licensesDirectory 'HapticScape.txt')
	Copy-Item (Join-Path $projectRoot 'licenses\*') $licensesDirectory -Recurse
	Copy-Item (Join-Path $projectRoot 'build\generated\runtime-licenses') (Join-Path $licensesDirectory 'resolved-artifacts') -Recurse
	Copy-Item (Join-Path $projectRoot 'FRIEND-SETUP.md') (Join-Path $appDirectory 'README-FIRST.md')

	$releaseManifest = @{
		version = $Version
		architecture = $architecture
		repository = 'ashy0019/HapticScape'
	} | ConvertTo-Json -Compress
	$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
	[System.IO.File]::WriteAllText(
		(Join-Path $appFilesDirectory 'release.json'),
		$releaseManifest,
		$utf8NoBom
	)

	$updateCoreSource = Join-Path $projectRoot 'launcher\UpdateCore.cs'
	$nativeTestSource = Join-Path $projectRoot 'launcher-tests\UpdateCoreTests.cs'
	$nativeTestPath = Join-Path $nativeTestDirectory 'HapticScapeUpdateCoreTests.exe'
	$nativeReferences = @(
		'/reference:System.dll',
		'/reference:System.Web.Extensions.dll',
		'/reference:System.IO.Compression.dll',
		'/reference:System.IO.Compression.FileSystem.dll'
	)
	$nativeTestArguments = @(
		'/nologo',
		'/target:exe',
		'/optimize+',
		'/platform:anycpu',
		"/out:$nativeTestPath"
	) + $nativeReferences + @($updateCoreSource, $nativeTestSource)

	Write-Host 'Compiling and running native updater tests...'
	& $cscPath @nativeTestArguments
	if ($LASTEXITCODE -ne 0)
	{
		throw "The updater test compiler failed with exit code $LASTEXITCODE."
	}
	& $nativeTestPath
	if ($LASTEXITCODE -ne 0)
	{
		throw "The updater tests failed with exit code $LASTEXITCODE."
	}

	$updaterSource = Join-Path $projectRoot 'launcher\HapticScapeUpdater.cs'
	$updaterPath = Join-Path $appFilesDirectory 'HapticScapeUpdater.exe'
	$updaterArguments = @(
		'/nologo',
		'/target:winexe',
		'/optimize+',
		'/platform:anycpu',
		'/reference:System.dll',
		'/reference:System.Windows.Forms.dll',
		"/out:$updaterPath",
		$updaterSource
	)

	$iconPath = Join-Path $projectRoot 'hapticscape.ico'
	if (Test-Path $iconPath -PathType Leaf)
	{
		$updaterArguments += "/win32icon:$iconPath"
	}

	Write-Host 'Creating HapticScapeUpdater.exe...'
	& $cscPath @updaterArguments
	if ($LASTEXITCODE -ne 0)
	{
		throw "The Windows updater compiler failed with exit code $LASTEXITCODE."
	}

	$launcherSource = Join-Path $projectRoot 'launcher\HapticScapeLauncher.cs'
	$launcherPath = Join-Path $appDirectory 'HapticScape.exe'
	$cscArguments = @(
		'/nologo',
		'/target:winexe',
		'/optimize+',
		'/platform:anycpu',
		'/reference:System.Drawing.dll',
		'/reference:System.Windows.Forms.dll',
		"/out:$launcherPath"
	) + $nativeReferences + @($updateCoreSource, $launcherSource)

	if (Test-Path $iconPath -PathType Leaf)
	{
		$cscArguments += "/win32icon:$iconPath"
	}

	Write-Host 'Creating HapticScape.exe...'
	& $cscPath @cscArguments
	if ($LASTEXITCODE -ne 0)
	{
		throw "The Windows launcher compiler failed with exit code $LASTEXITCODE."
	}

	$zipPath = Join-Path $distributionDirectory "HapticScape-Windows-$architecture-$Version.zip"
	$checksumPath = "$zipPath.sha256"
	if (Test-Path $zipPath)
	{
		Remove-Item -Force $zipPath
	}
	if (Test-Path $checksumPath)
	{
		Remove-Item -Force $checksumPath
	}

	Write-Host 'Compressing the distributable bundle...'
	Compress-Archive -Path $appDirectory -DestinationPath $zipPath -CompressionLevel Optimal
	$hash = (Get-FileHash -Algorithm SHA256 $zipPath).Hash.ToLowerInvariant()
	[System.IO.File]::WriteAllText(
		$checksumPath,
		"$hash *$(Split-Path -Leaf $zipPath)`r`n",
		[System.Text.Encoding]::ASCII
	)

	Write-Host ''
	Write-Host 'Package created successfully:' -ForegroundColor Green
	Write-Host $zipPath
	Write-Host $checksumPath
	Write-Host ''
	Write-Host 'Test build\windows-package\HapticScape\HapticScape.exe before sharing the ZIP.'
}
finally
{
	Pop-Location
}
