[CmdletBinding()]
param(
    [switch]$Clean,
    [string]$JavaHome
)

function Get-ScopedEnvironmentVariable {
    param([string]$Name)

    foreach ($scope in @('Process', 'User', 'Machine')) {
        $value = [Environment]::GetEnvironmentVariable($Name, $scope)
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value
        }
    }
    return $null
}

function Expand-EnvironmentValue {
    param([string]$Value)

    $result = $Value
    for ($iteration = 0; $iteration -lt 5; $iteration++) {
        $expanded = [regex]::Replace($result, '%([^%]+)%', {
            param($match)
            $replacement = Get-ScopedEnvironmentVariable $match.Groups[1].Value
            if ([string]::IsNullOrWhiteSpace($replacement)) {
                return $match.Value
            }
            return $replacement
        })
        if ($expanded -eq $result) {
            break
        }
        $result = $expanded
    }
    return [Environment]::ExpandEnvironmentVariables($result)
}

function Resolve-JavaInstallation {
    param([string]$Value)

    $candidate = (Expand-EnvironmentValue $Value).Trim().Trim('"')
    $candidate = $candidate.TrimEnd([char[]]@('\', '/'))

    if ([IO.Path]::GetFileName($candidate) -ieq 'java.exe') {
        $javaExe = $candidate
        $resolvedJavaHome = Split-Path -Parent (Split-Path -Parent $candidate)
    } elseif (([IO.Path]::GetFileName($candidate) -ieq 'bin') -and
              (Test-Path -LiteralPath (Join-Path $candidate 'java.exe') -PathType Leaf)) {
        $javaExe = Join-Path $candidate 'java.exe'
        $resolvedJavaHome = Split-Path -Parent $candidate
    } else {
        $resolvedJavaHome = $candidate
        $javaExe = Join-Path $resolvedJavaHome 'bin\java.exe'
    }

    if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
        throw "Invalid Java path; java.exe was not found: $javaExe"
    }

    return @{
        Home = (Resolve-Path -LiteralPath $resolvedJavaHome).Path.TrimEnd([char[]]@('\', '/'))
        Exe = (Resolve-Path -LiteralPath $javaExe).Path
    }
}

if (-not $PSBoundParameters.ContainsKey('JavaHome')) {
    $JavaHome = Read-Host 'Custom JAVA_HOME (press Enter for auto-detection)'
}

$javaSource = $null
$selectedPath = $null
if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
    $selectedPath = $JavaHome
    $javaSource = 'custom input'
} else {
    $selectedPath = Get-ScopedEnvironmentVariable 'JAVA_17_HOME'
    if (-not [string]::IsNullOrWhiteSpace($selectedPath)) {
        $javaSource = 'JAVA_17_HOME'
    } else {
        $selectedPath = Get-ScopedEnvironmentVariable 'JAVA_HOME'
        if (-not [string]::IsNullOrWhiteSpace($selectedPath)) {
            $javaSource = 'JAVA_HOME'
        } else {
            $javaCommand = Get-Command 'java.exe' -CommandType Application -ErrorAction SilentlyContinue |
                    Select-Object -First 1
            if ($null -ne $javaCommand) {
                $selectedPath = $javaCommand.Source
                $javaSource = 'java from PATH'
            }
        }
    }
}

if ([string]::IsNullOrWhiteSpace($selectedPath)) {
    throw 'No Java installation was found. Enter a custom path or configure JAVA_17_HOME, JAVA_HOME, or PATH.'
}

$java = Resolve-JavaInstallation $selectedPath
$javaVersion = (& $java.Exe -version 2>&1 | Select-Object -First 1).ToString()
if ($javaVersion -notmatch 'version "([0-9]+)') {
    throw "Unable to determine the Java version from: $javaVersion"
}
if ([int]$Matches[1] -ne 17) {
    throw "Forge 1.20.1 requires Java 17, but the selected runtime reports: $javaVersion"
}

$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:Path
try {
    $env:JAVA_HOME = $java.Home
    $env:Path = "$(Join-Path $java.Home 'bin');$previousPath"
    Write-Host "[DyDanmaku] Java source: $javaSource"
    Write-Host "[DyDanmaku] JAVA_HOME=$env:JAVA_HOME"
    Write-Host "[DyDanmaku] $javaVersion"

    if ($Clean) {
        & "$PSScriptRoot\gradlew.bat" clean build --no-daemon --console=plain
    } else {
        & "$PSScriptRoot\gradlew.bat" build --no-daemon --console=plain
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Forge 1.20.1 build failed with exit code $LASTEXITCODE."
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:Path = $previousPath
}
