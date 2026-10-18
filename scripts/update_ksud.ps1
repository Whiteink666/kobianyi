param([string]$RunId)

# 从指定 CI run 下载 ksud 产物，覆盖 manager/app/src/main/jniLibs 下的 libksud.so
# 本地（Android Studio）构建不会自动拿 CI 的 ksud，而内核信任锚变了之后
# 旧 ksud 里嵌的 kernelsu.ko 仍信任旧证书，会导致"安装成功但拿不到 root"。
$repoRoot = "d:\VS\Android\SukiSU-Ultra"
if (-not $RunId) { "usage: update_ksud.ps1 -RunId <run-id>"; exit 1 }
"run id = $RunId"

Add-Type -Namespace UK -Name C -MemberDefinition @'
[StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
public struct CREDENTIAL { public int Flags; public int Type; public string TargetName; public string Comment; public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist; public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName; }
[DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool CredRead(string target, int type, int flags, out IntPtr cred);
[DllImport("advapi32.dll")] public static extern void CredFree(IntPtr cred);
'@

$p = [IntPtr]::Zero
if (-not [UK.C]::CredRead("GitHub - https://api.github.com/Whiteink666", 1, 0, [ref]$p)) { "no credential"; exit 1 }
$c = [System.Runtime.InteropServices.Marshal]::PtrToStructure($p, [type][UK.C+CREDENTIAL])
$b = New-Object byte[] $c.CredentialBlobSize
[System.Runtime.InteropServices.Marshal]::Copy($c.CredentialBlob, $b, 0, $c.CredentialBlobSize)
[UK.C]::CredFree($p)
$tok = [System.Text.Encoding]::UTF8.GetString($b)
$h = @{ Authorization = "Bearer $tok"; "User-Agent" = "ps" }

$a = Invoke-RestMethod -Uri "https://api.github.com/repos/Whiteink666/kobianyi/actions/runs/$RunId/artifacts?per_page=100" -Headers $h -Proxy $null -TimeoutSec 30

$map = @{
    "ksud-aarch64-linux-android"     = "arm64-v8a"
    "ksud-armv7-linux-androideabi"   = "armeabi-v7a"
    "ksud-x86_64-linux-android"      = "x86_64"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$tmpRoot = Join-Path $env:TEMP "ksud_update"
if (Test-Path $tmpRoot) { Remove-Item $tmpRoot -Recurse -Force }
New-Item -ItemType Directory -Force -Path $tmpRoot | Out-Null

foreach ($art in $a.artifacts) {
    $abi = $map[$art.name]
    if (-not $abi) { continue }
    $zip = Join-Path $tmpRoot "$($art.name).zip"
    "downloading $($art.name) ($([math]::Round($art.size_in_bytes/1MB,1)) MB)"
    Invoke-WebRequest -Uri $art.archive_download_url -Headers $h -OutFile $zip -Proxy $null -TimeoutSec 300
    $dir = Join-Path $tmpRoot $art.name
    [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $dir)
    Remove-Item $zip -Force

    $src = Get-ChildItem $dir -Recurse -File -Filter "ksud" | Select-Object -First 1
    if (-not $src) { "!! ksud binary not found in $($art.name)"; continue }

    $dstDir = "$repoRoot\manager\app\src\main\jniLibs\$abi"
    New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
    $dst = Join-Path $dstDir "libksud.so"
    Copy-Item $src.FullName $dst -Force
    "  -> $abi\libksud.so  $([math]::Round((Get-Item $dst).Length/1MB,2)) MB  $((Get-Item $dst).LastWriteTime)"
}

Remove-Item $tmpRoot -Recurse -Force
"done"
