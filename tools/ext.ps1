param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CliArguments
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $repositoryRoot "gradlew.bat"

if (Test-Path -LiteralPath $wrapper) {
    & $wrapper :cli:run --args=($CliArguments -join " ")
    exit $LASTEXITCODE
}

$gradle = Get-Command gradle -ErrorAction SilentlyContinue
if ($null -eq $gradle) {
    throw "JDK 17 and Gradle are required. Generate the Gradle wrapper before using the ExtOS CLI."
}

& $gradle.Source -p $repositoryRoot :cli:run --args=($CliArguments -join " ")
exit $LASTEXITCODE
