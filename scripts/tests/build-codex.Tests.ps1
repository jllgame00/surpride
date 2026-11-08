# Run with Windows PowerShell; fixtures remain in the ignored project cache.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$source = Join-Path $projectRoot 'scripts/build-codex.ps1'
if (-not (Test-Path -LiteralPath $source)) {
    throw 'Build script is not implemented yet.'
}

$fixture = Join-Path $projectRoot ('.gradle/codex-script-tests/' + [guid]::NewGuid() + '/fixture project')
New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts'), (Join-Path $fixture 'jdk/bin'), (Join-Path $fixture 'android-source') -Force | Out-Null
$fixtureAndroidHome = Join-Path $fixture 'android-source'
Set-Content -LiteralPath (Join-Path $fixtureAndroidHome 'debug.keystore') -Value 'fixture signing identity' -Encoding ASCII
Copy-Item -LiteralPath $source -Destination (Join-Path $fixture 'scripts/build-codex.ps1')
Set-Content -LiteralPath (Join-Path $fixture 'settings.gradle.kts') -Value '// fixture' -Encoding ASCII
Set-Content -LiteralPath (Join-Path $fixture 'jdk/bin/java.exe') -Value 'fixture marker; never executed' -Encoding ASCII
Set-Content -LiteralPath (Join-Path $fixture 'gradlew.bat') -Encoding ASCII -Value @'
@echo off
set GRADLE_USER_HOME > "%CODEX_BUILD_TEST_TRACE%"
set JAVA_HOME >> "%CODEX_BUILD_TEST_TRACE%"
set ANDROID_USER_HOME >> "%CODEX_BUILD_TEST_TRACE%"
echo %* >> "%CODEX_BUILD_TEST_TRACE%"
exit /b %CODEX_BUILD_TEST_EXIT%
'@

$previousGradle = $env:GRADLE_USER_HOME
$previousJava = $env:JAVA_HOME
$previousAndroid = $env:ANDROID_USER_HOME
$previousTrace = $env:CODEX_BUILD_TEST_TRACE
$previousExit = $env:CODEX_BUILD_TEST_EXIT
$previousLocation = (Get-Location).Path
try {
    # Explicit prior values must survive success and native failure.
    $env:GRADLE_USER_HOME = 'prior-gradle-home'
    $env:JAVA_HOME = 'prior-java-home'
    $env:ANDROID_USER_HOME = $fixtureAndroidHome
    $env:CODEX_BUILD_TEST_TRACE = Join-Path $fixture 'trace.txt'
    $env:CODEX_BUILD_TEST_EXIT = '0'
    & (Join-Path $fixture 'scripts/build-codex.ps1') -Test -RerunTasks -JdkHome (Join-Path $fixture 'jdk')
    if ($LASTEXITCODE -ne 0) { throw 'Success exit code was not preserved.' }
    $trace = Get-Content -LiteralPath $env:CODEX_BUILD_TEST_TRACE -Raw
    if (-not $trace.Contains("GRADLE_USER_HOME=$(Join-Path $fixture '.gradle/codex-user-home')")) { throw 'Cache is not isolated to the detected project.' }
    if (-not $trace.Contains("ANDROID_USER_HOME=$(Join-Path $fixture '.gradle/codex-user-home/android-user-home')")) { throw 'Android user home is not isolated to the detected project.' }
    if (-not $trace.Contains(':app:assembleDebug') -or -not $trace.Contains(':app:testDebugUnitTest')) { throw 'Build/test tasks were not forwarded.' }
    if (-not $trace.Contains('-Pkotlin.compiler.execution.strategy=in-process')) { throw 'Kotlin isolation option is missing.' }
    if (-not $trace.Contains('--rerun-tasks')) { throw 'Fresh compilation option was not forwarded.' }
    $localDebugKey = Join-Path $fixture '.gradle/codex-user-home/android-user-home/debug.keystore'
    if ((Get-Content -LiteralPath $localDebugKey -Raw).Trim() -ne 'fixture signing identity') { throw 'Existing debug identity was not reused.' }
    Set-Content -LiteralPath $localDebugKey -Value 'preserve existing local key' -Encoding ASCII
    if ($env:GRADLE_USER_HOME -ne 'prior-gradle-home' -or $env:JAVA_HOME -ne 'prior-java-home' -or $env:ANDROID_USER_HOME -ne $fixtureAndroidHome) { throw 'Caller environment changed on success.' }
    if ((Get-Location).Path -ne $previousLocation) { throw 'Caller location changed.' }
    'PASS: root detection, local cache, optional tests, and environment restoration'

    $env:CODEX_BUILD_TEST_EXIT = '37'
    & (Join-Path $fixture 'scripts/build-codex.ps1') -JdkHome (Join-Path $fixture 'jdk')
    if ($LASTEXITCODE -ne 37) { throw 'Native failure exit code was not preserved.' }
    if ($env:GRADLE_USER_HOME -ne 'prior-gradle-home' -or $env:JAVA_HOME -ne 'prior-java-home' -or $env:ANDROID_USER_HOME -ne $fixtureAndroidHome) { throw 'Caller environment changed on failure.' }
    if ((Get-Content -LiteralPath $localDebugKey -Raw).Trim() -ne 'preserve existing local key') { throw 'Existing local debug key was overwritten.' }
    $trace = Get-Content -LiteralPath $env:CODEX_BUILD_TEST_TRACE -Raw
    if ($trace.Contains(':app:testDebugUnitTest')) { throw 'Tests ran without -Test.' }
    'PASS: build-only mode and native failure propagation'

    $env:GRADLE_USER_HOME = $null
    $env:JAVA_HOME = $null
    $env:ANDROID_USER_HOME = $null
    $env:CODEX_BUILD_TEST_EXIT = '0'
    & (Join-Path $fixture 'scripts/build-codex.ps1') -JdkHome (Join-Path $fixture 'jdk')
    if ($LASTEXITCODE -ne 0 -or $null -ne $env:GRADLE_USER_HOME -or $null -ne $env:JAVA_HOME -or $null -ne $env:ANDROID_USER_HOME) { throw 'Initially unset environment was not restored.' }
    'PASS: initially unset environment restoration'

    & (Join-Path $fixture 'scripts/build-codex.ps1') -JdkHome (Join-Path $fixture 'missing-jdk')
    if ($LASTEXITCODE -ne 1 -or $null -ne $env:GRADLE_USER_HOME -or $null -ne $env:JAVA_HOME -or $null -ne $env:ANDROID_USER_HOME) { throw 'Setup failure did not return 1 and preserve the environment.' }
    'PASS: setup failure exit and environment restoration'
} finally {
    $env:GRADLE_USER_HOME = $previousGradle
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_USER_HOME = $previousAndroid
    $env:CODEX_BUILD_TEST_TRACE = $previousTrace
    $env:CODEX_BUILD_TEST_EXIT = $previousExit
}
