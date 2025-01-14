# 오늘의 급식 - Java 빌드 스크립트
#
#   .\build.ps1            컴파일 + 실행 가능한 jar 생성
#   .\build.ps1 -Package   위에 더해 JRE 를 번들한 app-image 생성 (Java 없는 PC 용)
#
# JDK 21 기준. java.lang.foreign 이 21 에서는 preview 라 --enable-preview 가 필요하다.

param(
    [switch]$Package
)

$ErrorActionPreference = "Stop"

$here    = $PSScriptRoot
$root    = Split-Path -Parent $here
$classes = Join-Path $here "build\classes"
$dist    = Join-Path $here "build\dist"
$jar     = Join-Path $dist "meal.jar"

# PATH 의 javac 는 JDK 전체가 아니라 java/javac 만 있는 shim 폴더인 경우가 많다.
# jar / jpackage 가 실제로 들어 있는 bin 을 찾는다.
$javac = (Get-Command javac).Source

$candidates = @()
if ($env:JAVA_HOME) {
    $candidates += (Join-Path $env:JAVA_HOME "bin")
    $candidates += (Get-ChildItem -Directory $env:JAVA_HOME -ErrorAction SilentlyContinue |
                    ForEach-Object { Join-Path $_.FullName "bin" })
}
$candidates += (Split-Path -Parent $javac)
$candidates += (Get-ChildItem -Directory "C:\Program Files\Java" -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending |
                ForEach-Object { Join-Path $_.FullName "bin" })

$jdkBin = $candidates | Where-Object { $_ -and (Test-Path (Join-Path $_ "jar.exe")) } | Select-Object -First 1
if (-not $jdkBin) {
    throw "jar.exe 가 있는 JDK bin 을 찾지 못했습니다. JAVA_HOME 을 JDK 경로로 설정하세요."
}

$javac       = Join-Path $jdkBin "javac.exe"
$jarExe      = Join-Path $jdkBin "jar.exe"
$jpackageExe = Join-Path $jdkBin "jpackage.exe"
Write-Host "JDK: $jdkBin"

function Invoke-Checked {
    param([string]$Exe, [string[]]$Arguments)
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$(Split-Path -Leaf $Exe) 실패 (exit $LASTEXITCODE)"
    }
}

if (Test-Path (Join-Path $here "build")) {
    Remove-Item -Recurse -Force (Join-Path $here "build")
}
New-Item -ItemType Directory -Force $classes | Out-Null
New-Item -ItemType Directory -Force $dist    | Out-Null

Write-Host "[1/4] 컴파일"
$sources = Get-ChildItem -Recurse -Filter *.java (Join-Path $here "src") | ForEach-Object { $_.FullName }
Invoke-Checked $javac (@("--release", "21", "--enable-preview", "-encoding", "UTF-8", "-d", $classes) + $sources)

# 배경 이미지는 리포 루트의 것을 그대로 jar 에 넣는다 (사본을 따로 두지 않기 위해).
Copy-Item (Join-Path $root "base.png") (Join-Path $classes "base.png")

Write-Host "[2/4] jar 생성"
Invoke-Checked $jarExe @("--create", "--file", $jar, "--main-class", "meal.Main", "-C", $classes, ".")
Write-Host "  -> $jar"
Write-Host "  실행: java --enable-preview --enable-native-access=ALL-UNNAMED -jar `"$jar`""

if (-not $Package) { return }

Write-Host "[3/4] app-image 생성 (JRE 번들)"
Invoke-Checked $jpackageExe @(
    "--type", "app-image",
    "--name", "MealWallpaper",
    "--app-version", "1.0",
    "--input", $dist,
    "--main-jar", (Split-Path -Leaf $jar),
    "--main-class", "meal.Main",
    "--java-options", "--enable-preview",
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--add-modules", "java.base,java.desktop,java.net.http",
    "--jlink-options", "--strip-debug --no-header-files --no-man-pages --compress=zip-6",
    "--dest", (Join-Path $here "build\image")
)
Write-Host "  -> $(Join-Path $here 'build\image\MealWallpaper\MealWallpaper.exe')"

Write-Host "[4/4] release 압축"
$release = Join-Path $root "release"
New-Item -ItemType Directory -Force $release | Out-Null
$zip = Join-Path $release "MealWallpaper-1.0-win-x64.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path (Join-Path $here "build\image\MealWallpaper") -DestinationPath $zip
Write-Host "  -> $zip ($('{0:N1} MB' -f ((Get-Item $zip).Length / 1MB)))"
Write-Host "  압축을 풀고 MealWallpaper.exe 를 실행하면 된다. Java 설치 불필요."
