param(
    [string]$BridgeRepository
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $BridgeRepository)
{
    $BridgeRepository = Join-Path $projectRoot '..\runelite-local-event-bridge'
}
$bridgeRepository = [System.IO.Path]::GetFullPath($BridgeRepository)
$targetRoot = Join-Path $projectRoot 'runelite-bridge-client'
$targetMain = Join-Path $targetRoot 'src\main\java\com\ashy0019\localeventbridge'
$targetTests = Join-Path $targetRoot 'src\test\java\com\ashy0019\localeventbridge'
$targetTestResources = Join-Path $targetRoot 'src\test\resources'
$targetUpstream = Join-Path $targetRoot 'upstream'

if (-not (Test-Path (Join-Path $bridgeRepository '.git') -PathType Container))
{
    throw "Not a Git repository: $bridgeRepository"
}

$sourceMain = Join-Path $bridgeRepository 'src\main\java\com\ashy0019\localeventbridge'
$sourceTests = Join-Path $bridgeRepository 'src\test\java\com\ashy0019\localeventbridge'
$sourceTestResources = Join-Path $bridgeRepository 'src\test\resources'

if (-not (Test-Path $sourceMain -PathType Container))
{
    throw "Local Event Bridge production source was not found: $sourceMain"
}

$commit = (& git -C $bridgeRepository rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or -not $commit)
{
    throw 'Unable to read the Local Event Bridge commit.'
}

$branch = (& git -C $bridgeRepository rev-parse --abbrev-ref HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or -not $branch)
{
    $branch = 'unknown'
}

$status = & git -C $bridgeRepository status --porcelain
if ($LASTEXITCODE -ne 0)
{
    throw 'Unable to inspect the Local Event Bridge working tree.'
}
if ($status)
{
    throw 'The Local Event Bridge repository has uncommitted changes. Commit or stash them before syncing so provenance remains exact.'
}

Write-Host "Syncing Local Event Bridge commit $commit..."

if (Test-Path $targetMain)
{
    Remove-Item -Recurse -Force $targetMain
}
New-Item -ItemType Directory -Force (Split-Path -Parent $targetMain) | Out-Null
Copy-Item $sourceMain $targetMain -Recurse

if (Test-Path $targetTests)
{
    Remove-Item -Recurse -Force $targetTests
}
if (Test-Path $sourceTests -PathType Container)
{
    New-Item -ItemType Directory -Force (Split-Path -Parent $targetTests) | Out-Null
    Copy-Item $sourceTests $targetTests -Recurse
}

if (Test-Path $targetTestResources)
{
    Remove-Item -Recurse -Force $targetTestResources
}
if (Test-Path $sourceTestResources -PathType Container)
{
    New-Item -ItemType Directory -Force $targetTestResources | Out-Null
    Copy-Item (Join-Path $sourceTestResources '*') $targetTestResources -Recurse
}

New-Item -ItemType Directory -Force $targetUpstream | Out-Null
Copy-Item (Join-Path $bridgeRepository 'LICENSE') (Join-Path $targetUpstream 'LOCAL-EVENT-BRIDGE-LICENSE.txt') -Force
Copy-Item (Join-Path $bridgeRepository 'runelite-plugin.properties') (Join-Path $targetUpstream 'runelite-plugin.properties') -Force
Copy-Item (Join-Path $bridgeRepository 'docs\local-event-protocol-v1.md') (Join-Path $targetUpstream 'local-event-protocol-v1.md') -Force

$provenance = @(
    'sourceRepository=https://github.com/ashy0019/runelite-local-event-bridge',
    "sourceCommit=$commit",
    "sourceBranch=$branch",
    'syncScript=sync-runelite-bridge.ps1'
) -join "`n"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText(
    (Join-Path $targetRoot 'BRIDGE-SOURCE.properties'),
    $provenance + "`n",
    $utf8NoBom
)

Write-Host 'Bridge source synchronized successfully.' -ForegroundColor Green
