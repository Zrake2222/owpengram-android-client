#Requires -Version 5.1
param(
    [string]$ServerHost,
    [int]$ServerPort = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir '..')).Path
$ConnectionsManagerFile = Join-Path $RepoRoot 'TMessagesProj\jni\tgnet\ConnectionsManager.cpp'
$DatacenterFile = Join-Path $RepoRoot 'TMessagesProj\jni\tgnet\Datacenter.h'
$BuildVarsFile = Join-Path $RepoRoot 'TMessagesProj\src\main\java\org\telegram\messenger\BuildVars.java'
$LocalPropertiesFile = Join-Path $RepoRoot 'local.properties'
$GradlewBat = Join-Path $RepoRoot 'gradlew.bat'
$ApkPath = Join-Path $RepoRoot 'TMessagesProj_App\build\outputs\apk\afat\debug\app.apk'

$TestApiId = '17349'
$TestApiHash = '344583e45741c457fe1862106095a5eb'

function Write-Step([string]$Text) { Write-Host "`n>> $Text" -ForegroundColor Yellow }
function Write-Ok([string]$Text)   { Write-Host "[OK] $Text" -ForegroundColor Green }
function Write-Err([string]$Text)   { Write-Host "[X] $Text" -ForegroundColor Red }

function Read-Line([string]$Prompt, [string]$Default) {
    $suffix = if ([string]::IsNullOrWhiteSpace($Default)) { '' } else { " [$Default]" }
    $value = Read-Host "$Prompt$suffix"
    if ([string]::IsNullOrWhiteSpace($value)) { return $Default }
    return $value.Trim()
}

function Patch-Server([string]$Address, [int]$Port) {
    $ip = $Address
    $portNum = $Port

    $content = Get-Content -Path $ConnectionsManagerFile -Raw -Encoding UTF8
    $pattern = 'TcpAddress\("([^"]*)",\s*(\d+),\s*0,\s*""\)'
    $newContent = [regex]::Replace($content, $pattern, {
        'TcpAddress("' + $ip + '", ' + $portNum + ', 0, "")'
    })
    [System.IO.File]::WriteAllText($ConnectionsManagerFile, $newContent, (New-Object System.Text.UTF8Encoding $false))

    $dcContent = Get-Content -Path $DatacenterFile -Raw -Encoding UTF8
    $portPattern = 'const int32_t \*defaultPorts = new int32_t\[4\] \{-1,\s*(\d+),\s*-1,\s*-1\};'
    $newDc = [regex]::Replace($dcContent, $portPattern, "const int32_t *defaultPorts = new int32_t[4] {-1, $portNum, -1, -1};")
    [System.IO.File]::WriteAllText($DatacenterFile, $newDc, (New-Object System.Text.UTF8Encoding $false))

    Write-Ok "Server patched: ${ip}:$portNum"
}

function Patch-Api {
    $content = Get-Content -Path $BuildVarsFile -Raw -Encoding UTF8
    $content = [regex]::Replace($content, 'public static int APP_ID = \d+;', "public static int APP_ID = $TestApiId;")
    $content = [regex]::Replace($content, 'public static String APP_HASH = "[^"]*";', "public static String APP_HASH = `"$TestApiHash`";")
    [System.IO.File]::WriteAllText($BuildVarsFile, $content, (New-Object System.Text.UTF8Encoding $false))
    Write-Ok "API patched (test credentials)"
}

function Find-AndroidSdk {
    foreach ($path in @($env:OWPNG_ANDROID_SDK, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($path -and (Test-Path $path)) { return (Resolve-Path $path).Path }
    }
    $default = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $default) { return (Resolve-Path $default).Path }
    return $null
}

function Ensure-LocalProperties([string]$SdkPath) {
    $escaped = $SdkPath -replace '\\', '\\'
    $line = "sdk.dir=$escaped"
    if (Test-Path $LocalPropertiesFile) {
        $content = Get-Content -Path $LocalPropertiesFile -Raw -Encoding UTF8
        if ($content -match '(?m)^sdk\.dir=.*$') {
            $updated = [regex]::Replace($content, '(?m)^sdk\.dir=.*$', $line)
            [System.IO.File]::WriteAllText($LocalPropertiesFile, $updated, (New-Object System.Text.UTF8Encoding $false))
            return
        }
    }
    [System.IO.File]::WriteAllText($LocalPropertiesFile, "$line`r`n", (New-Object System.Text.UTF8Encoding $false))
}

function Invoke-Gradle([string]$Task, [string]$Label) {
    $logDir = Join-Path $RepoRoot 'logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $logFile = Join-Path $logDir ("{0}-{1}.log" -f $Label, (Get-Date -Format 'yyyyMMdd-HHmmss'))

    $escapedWd = $RepoRoot.Replace('"', '""')
    $escapedLog = $logFile.Replace('"', '""')
    $escapedGradlew = $GradlewBat.Replace('"', '""')
    $full = "cd /d `"$escapedWd`" && call `"$escapedGradlew`" $Task >> `"$escapedLog`" 2>&1"

    Write-Host "gradle: $Task" -ForegroundColor DarkGray
    Write-Host "log: $logFile" -ForegroundColor DarkGray

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    $psi.Arguments = "/c `"$full`""
    $psi.WorkingDirectory = $RepoRoot
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    $null = $process.Start()

    $offset = 0L
    $lastBeat = [datetime]::UtcNow
    while (-not $process.HasExited) {
        if (Test-Path $logFile) {
            $stream = [System.IO.File]::Open($logFile, 'Open', 'Read', 'ReadWrite')
            try {
                $stream.Seek($offset, 'Begin') | Out-Null
                $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
                while (-not $reader.EndOfStream) {
                    $line = $reader.ReadLine()
                    if (-not [string]::IsNullOrWhiteSpace($line)) { Write-Host $line }
                }
                $offset = $stream.Position
            }
            finally { $stream.Dispose() }
        }
        if (([datetime]::UtcNow - $lastBeat).TotalSeconds -ge 30) {
            $lastBeat = [datetime]::UtcNow
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $Label - still running..." -ForegroundColor Cyan
        }
        Start-Sleep -Milliseconds 500
    }

    if ($process.ExitCode -ne 0) {
        throw "Gradle failed (exit $($process.ExitCode)). Log: $logFile"
    }
    Write-Ok "Finished: $Label"
}

try {
    Write-Host ''
    Write-Host 'OwpenGram Android build' -ForegroundColor Cyan
    Write-Host "Repo: $RepoRoot"
    Write-Host 'Tip: run build-android.cmd from cmd or PowerShell (not Git Bash).' -ForegroundColor DarkGray
    Write-Host ''

    if ([string]::IsNullOrWhiteSpace($ServerHost)) {
        $ServerHost = Read-Line 'Server IP' '192.168.100.10'
    }
    if ($ServerPort -le 0) {
        $portRaw = Read-Line 'MTProto port' '10443'
        if (-not [int]::TryParse($portRaw, [ref]$ServerPort)) {
            throw 'Port must be a number.'
        }
    }

    Write-Step "Patch server -> ${ServerHost}:$ServerPort"
    Patch-Server -Address $ServerHost -Port $ServerPort
    Patch-Api

    Write-Step 'Check tools'
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Missing git in PATH' }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Missing java in PATH (JDK 17+)' }
    if (-not (Test-Path $GradlewBat)) { throw "Missing: $GradlewBat" }

    $sdk = Find-AndroidSdk
    if (-not $sdk) {
        throw 'Android SDK not found. Install Android Studio or set ANDROID_HOME.'
    }
    Ensure-LocalProperties -SdkPath $sdk
    Write-Ok "SDK: $sdk"

    Write-Step 'Git submodules'
    Push-Location $RepoRoot
    try {
        $env:GIT_TERMINAL_PROMPT = '0'
        & git -c advice.detachedHead=false submodule update --init --recursive --quiet
        if ($LASTEXITCODE -ne 0) { throw "git submodule failed ($LASTEXITCODE)" }
    }
    finally { Pop-Location }
    Write-Ok 'Submodules OK'

    Write-Step 'Gradle assembleAfatDebug'
    Invoke-Gradle -Task ':TMessagesProj_App:assembleAfatDebug' -Label 'assemble'

    if (Test-Path $ApkPath) {
        Write-Ok "APK: $ApkPath"
    }
    else {
        throw "Build finished but APK not found: $ApkPath"
    }
}
catch {
    Write-Err $_.Exception.Message
    exit 1
}
