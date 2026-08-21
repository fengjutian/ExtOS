param(
    [string]$OutputDirectory = "build/sample"
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$pluginSource = Join-Path $repositoryRoot "app/src/main/assets/plugins/hello"
$resolvedOutput = Join-Path $repositoryRoot $OutputDirectory
$zipPath = Join-Path $resolvedOutput "hello.zip"
$packagePath = Join-Path $resolvedOutput "hello.ext"

New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $packagePath -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $pluginSource "*") -DestinationPath $zipPath
Move-Item -LiteralPath $zipPath -Destination $packagePath

Write-Output $packagePath
