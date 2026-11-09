[CmdletBinding()]
param([string]$JdkHome, [switch]$Test)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
$previousAndroidHome = $env:ANDROID_USER_HOME
$buildExit = 1
try {
    if (-not $JdkHome) { $JdkHome = $previousJavaHome }
    if (-not $JdkHome) { $JdkHome = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr' }
    if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin/java.exe'))) { throw 'JDK not found; pass -JdkHome.' }
    $env:JAVA_HOME = $JdkHome
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle/codex-user-home'
    $env:ANDROID_USER_HOME = Join-Path $env:GRADLE_USER_HOME 'android-user-home'
    New-Item -ItemType Directory -Force $env:ANDROID_USER_HOME | Out-Null
    $ErrorActionPreference = 'Continue'
    $gradleArguments = @('-p', $projectRoot, '-PnavHostPoc=true', ':nav-host-poc:assembleDebug', '--no-daemon', '--console=plain', '-Pkotlin.compiler.execution.strategy=in-process')
    if ($Test) { $gradleArguments += ':nav-host-poc:testDebugUnitTest' }
    & (Join-Path $projectRoot 'gradlew.bat') @gradleArguments 2>&1 | ForEach-Object {
        $line = $_.ToString().Replace($projectRoot, '<PROJECT>').Replace($projectRoot.Replace('\', '/'), '<PROJECT>')
        if ($env:USERPROFILE) { $line = $line.Replace($env:USERPROFILE, '<USER_HOME>').Replace($env:USERPROFILE.Replace('\', '/'), '<USER_HOME>') }
        Write-Output $line
    }
    $buildExit = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleHome
    $env:ANDROID_USER_HOME = $previousAndroidHome
}
exit $buildExit
