[CmdletBinding()]
param(
    [switch]$Test,
    [switch]$RerunTasks,
    [string]$JdkHome
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousGradleHome = $env:GRADLE_USER_HOME
$previousJavaHome = $env:JAVA_HOME
$previousAndroidHome = $env:ANDROID_USER_HOME
$locationPushed = $false
$buildExit = 1

try {
    $wrapper = Join-Path $projectRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $wrapper) -or
        -not (Test-Path -LiteralPath (Join-Path $projectRoot 'settings.gradle.kts'))) {
        throw 'Project root must contain gradlew.bat and settings.gradle.kts.'
    }

    if (-not $JdkHome) {
        $candidates = @($previousJavaHome)
        if ($env:ProgramFiles) {
            $candidates += Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
        }
        $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($javaCommand) {
            $candidates += Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
        }
        $JdkHome = $candidates | Where-Object {
            $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin/java.exe'))
        } | Select-Object -First 1
    }
    if (-not $JdkHome -or -not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin/java.exe'))) {
        throw 'JDK not found. Set JAVA_HOME or pass -JdkHome with a compatible installed JDK.'
    }

    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JdkHome).Path
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle/codex-user-home'
    New-Item -ItemType Directory -Path $env:GRADLE_USER_HOME -Force | Out-Null
    $env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android-user-home'
    New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null
    # Keep the existing debug signing identity; never replace or print a keystore.
    $sourceAndroidHome = $previousAndroidHome
    if (-not $sourceAndroidHome -and $env:USERPROFILE) {
        $sourceAndroidHome = Join-Path $env:USERPROFILE '.android'
    }
    $localDebugKey = Join-Path $env:ANDROID_USER_HOME 'debug.keystore'
    if ($sourceAndroidHome -and -not (Test-Path -LiteralPath $localDebugKey)) {
        $sourceDebugKey = Join-Path $sourceAndroidHome 'debug.keystore'
        if (Test-Path -LiteralPath $sourceDebugKey) {
            Copy-Item -LiteralPath $sourceDebugKey -Destination $localDebugKey -ErrorAction Stop
        }
    }
    Push-Location -LiteralPath $projectRoot
    $locationPushed = $true

    $gradleArguments = @(':app:assembleDebug')
    if ($Test) { $gradleArguments += ':app:testDebugUnitTest' }
    if ($RerunTasks) { $gradleArguments += '--rerun-tasks' }
    # Avoid persistent Gradle/Kotlin daemons and Kotlin markers outside the workspace.
    $gradleArguments += '--no-daemon', '--console=plain', '-Pkotlin.compiler.execution.strategy=in-process'
    Write-Output 'Using project-local Gradle User Home: .gradle/codex-user-home'

    # Windows PowerShell represents native stderr as ErrorRecord even for warnings.
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    & $wrapper @gradleArguments 2>&1 | ForEach-Object {
        $line = $_.ToString().Replace($projectRoot, '<PROJECT>').Replace($projectRoot.Replace('\', '/'), '<PROJECT>')
        if ($env:USERPROFILE) {
            $line = $line.Replace($env:USERPROFILE, '<USER_HOME>').Replace($env:USERPROFILE.Replace('\', '/'), '<USER_HOME>')
        }
        Write-Output $line
    }
    $buildExit = $LASTEXITCODE
    if ($buildExit -ne 0) { Write-Output "Gradle failed (exit $buildExit)." }
} catch {
    $message = $_.Exception.Message.Replace($projectRoot, '<PROJECT>')
    if ($env:USERPROFILE) { $message = $message.Replace($env:USERPROFILE, '<USER_HOME>') }
    if ($JdkHome) { $message = $message.Replace($JdkHome, '<JDK>') }
    Write-Output ('Codex build setup failed: ' + $message)
    $buildExit = 1
} finally {
    if ($locationPushed) { Pop-Location }
    $env:GRADLE_USER_HOME = $previousGradleHome
    $env:JAVA_HOME = $previousJavaHome
    $env:ANDROID_USER_HOME = $previousAndroidHome
}

exit $buildExit
