param([string]$RunId)

# 把 CI 编好的二进制同步到本地工作目录，让 Android Studio 直接构建出的 APK
# 和 CI 出的一致：
#   ksud               -> app/src/main/jniLibs/<abi>/libksud.so   (内含 kernelsu.ko)
#   dfroot-<kmi>.ko    -> app/src/main/cpp/dfroot/ko/             (DirtyFrag 引导模块)
#   ghostlock-extract  -> app/src/main/jniLibs/arm64-v8a/libextract.so (镜像解析)
# 这几样都不在 git 里，本地构建不会自动拿到，不同步就会出现
# "安装成功 / 临时 root 成功，但拿不到 root"。

$repoRoot = "d:\VS\Android\SukiSU-Ultra"
if (-not $RunId) { "usage: sync_ci_artifacts.ps1 -RunId <run-id>"; exit 1 }

Add-Type -Namespace SA -Name C -MemberDefinition @'
[StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
public struct CREDENTIAL { public int Flags; public int Type; public string TargetName; public string Comment; public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist; public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName; }
[DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool CredRead(string target, int type, int flags, out IntPtr cred);
[DllImport("advapi32.dll")] public static extern void CredFree(IntPtr cred);
'@

$p = [IntPtr]::Zero
if (-not [SA.C]::CredRead("GitHub - https://api.github.com/Whiteink666", 1, 0, [ref]$p)) { "no credential"; exit 1 }
$c = [System.Runtime.InteropServices.Marshal]::PtrToStructure($p, [type][SA.C+CREDENTIAL])
$b = New-Object byte[] $c.CredentialBlobSize
[System.Runtime.InteropServices.Marshal]::Copy($c.CredentialBlob, $b, 0, $c.CredentialBlobSize)
[SA.C]::CredFree($p)
$tok = [System.Text.Encoding]::UTF8.GetString($b)
$h = @{ Authorization = "Bearer $tok"; "User-Agent" = "ps" }

$arts = Invoke-RestMethod -Uri "https://api.github.com/repos/Whiteink666/kobianyi/actions/runs/$RunId/artifacts?per_page=100" -Headers $h -Proxy $null -TimeoutSec 30
"artifacts: $($arts.total_count)"

Add-Type -AssemblyName System.IO.Compression.FileSystem
$tmpRoot = Join-Path $env:TEMP "ci_sync"
if (Test-Path $tmpRoot) { Remove-Item $tmpRoot -Recurse -Force }
New-Item -ItemType Directory -Force -Path $tmpRoot | Out-Null

function Fetch($art) {
    $zip = Join-Path $tmpRoot "$($art.name).zip"
    $dir = Join-Path $tmpRoot $art.name
    Invoke-WebRequest -Uri $art.archive_download_url -Headers $h -OutFile $zip -Proxy $null -TimeoutSec 300
    [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $dir)
    Remove-Item $zip -Force
    return $dir
}

foreach ($art in $arts.artifacts) {
    $name = $art.name

    # 1) ksud ----------------------------------------------------------
    switch ($name) {
        "ksud-aarch64-linux-android" { $abi = "arm64-v8a" }
        "ksud-armv7-linux-androideabi" { $abi = "armeabi-v7a" }
        "ksud-x86_64-linux-android" { $abi = "x86_64" }
        default { $abi = $null }
    }
    if ($abi) {
        $dir = Fetch $art
        $src = Get-ChildItem $dir -Recurse -File -Filter "ksud" | Select-Object -First 1
        if ($src) {
            $dstDir = "$repoRoot\manager\app\src\main\jniLibs\$abi"
            New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
            Copy-Item $src.FullName "$dstDir\libksud.so" -Force
            "ksud      -> $abi  $([math]::Round((Get-Item "$dstDir\libksud.so").Length/1MB,2)) MB"
        }
        continue
    }

    # 2) dfroot-<kmi>-ko ----------------------------------------------
    if ($name -like "dfroot-*-ko") {
        $dir = Fetch $art
        $src = Get-ChildItem $dir -Recurse -File -Filter "*.ko" | Select-Object -First 1
        if ($src) {
            $dstDir = "$repoRoot\manager\app\src\main\cpp\dfroot\ko"
            New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
            Copy-Item $src.FullName (Join-Path $dstDir $src.Name) -Force
            "dfroot    -> $($src.Name)  $($src.Length) B"
        }
        continue
    }

    # 3) ghostlock-extract --------------------------------------------
    if ($name -like "ghostlock-extract*") {
        $dir = Fetch $art
        $src = Get-ChildItem $dir -Recurse -File -Filter "ghostlock-extract" | Select-Object -First 1
        if ($src) {
            $dstDir = "$repoRoot\manager\app\src\main\jniLibs\arm64-v8a"
            New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
            Copy-Item $src.FullName "$dstDir\libextract.so" -Force
            "extractor -> arm64-v8a\libextract.so  $([math]::Round((Get-Item "$dstDir\libextract.so").Length/1MB,2)) MB"
        }
        continue
    }
}

Remove-Item $tmpRoot -Recurse -Force
"done"
