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
	& $gradleWrapper clean test shadowJar
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

	if (Test-Path $packageRoot)
	{
		Remove-Item -Recurse -Force $packageRoot
	}
	New-Item -ItemType Directory -Force $appFilesDirectory | Out-Null
	New-Item -ItemType Directory -Force $licensesDirectory | Out-Null
	New-Item -ItemType Directory -Force $distributionDirectory | Out-Null

	Copy-Item $jarPath (Join-Path $appFilesDirectory 'hapticscape-client.jar')
	Copy-Item (Join-Path $projectRoot 'LICENSE') (Join-Path $licensesDirectory 'HapticScape.txt')
	Copy-Item (Join-Path $projectRoot 'licenses\RUNELITE-LICENSE.txt') (Join-Path $licensesDirectory 'RuneLite.txt')
	Copy-Item (Join-Path $projectRoot 'FRIEND-SETUP.md') (Join-Path $appDirectory 'README-FIRST.md')

	$launcherSource = Join-Path $projectRoot 'launcher\HapticScapeLauncher.cs'
	$launcherPath = Join-Path $appDirectory 'HapticScape.exe'
	$cscArguments = @(
		'/nologo',
		'/target:winexe',
		'/optimize+',
		'/platform:anycpu',
		'/reference:System.dll',
		'/reference:System.Windows.Forms.dll',
		"/out:$launcherPath",
		$launcherSource
	)

	$iconPath = Join-Path $projectRoot 'hapticscape.ico'
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

	switch ($env:PROCESSOR_ARCHITECTURE)
	{
		'ARM64' { $architecture = 'arm64' }
		'AMD64' { $architecture = 'x64' }
		default { $architecture = ([string] $env:PROCESSOR_ARCHITECTURE).ToLowerInvariant() }
	}

	$zipPath = Join-Path $distributionDirectory "HapticScape-Windows-$architecture-$Version.zip"
	if (Test-Path $zipPath)
	{
		Remove-Item -Force $zipPath
	}

	Write-Host 'Compressing the distributable bundle...'
	Compress-Archive -Path $appDirectory -DestinationPath $zipPath -CompressionLevel Optimal

	Write-Host ''
	Write-Host 'Package created successfully:' -ForegroundColor Green
	Write-Host $zipPath
	Write-Host ''
	Write-Host 'Test build\windows-package\HapticScape\HapticScape.exe before sharing the ZIP.'
}
finally
{
	Pop-Location
}
