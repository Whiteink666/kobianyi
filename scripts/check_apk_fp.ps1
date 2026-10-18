# 下载指定 run 的 Manager APK，用 apksigner 校验签名证书指纹
param([string]$RunId, [switch]$List)

Add-Type -Namespace AK -Name C -MemberDefinition @'
[StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
public struct CREDENTIAL { public int Flags; public int Type; public string TargetName; public string Comment; public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist; public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName; }
[DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool CredRead(string target, int type, int flags, out IntPtr cred);
[DllImport("advapi32.dll")] public static extern void CredFree(IntPtr cred);
'@

$p = [IntPtr]::Zero
if (-not [AK.C]::CredRead("GitHub - https://api.github.com/Whiteink666", 1, 0, [ref]$p)) { "no credential"; exit 1 }
$c = [System.Runtime.InteropServices.Marshal]::PtrToStructure($p, [type][AK.C+CREDENTIAL])
$b = New-Object byte[] $c.CredentialBlobSize
[System.Runtime.InteropServices.Marshal]::Copy($c.CredentialBlob, $b, 0, $c.CredentialBlobSize)
[AK.C]::CredFree($p)
$tok = [System.Text.Encoding]::UTF8.GetString($b)
$h = @{ Authorization = "Bearer $tok"; "User-Agent" = "ps" }

$a = Invoke-RestMethod -Uri "https://api.github.com/repos/Whiteink666/kobianyi/actions/runs/$RunId/artifacts?per_page=100" -Headers $h -Proxy $null -TimeoutSec 25
if ($List) { $a.artifacts | ForEach-Object { "$($_.name) size=$($_.size_in_bytes)" }; exit 0 }
$apk = $a.artifacts | Where-Object { $_.name -match "apk|manager" -and $_.name -notmatch "mappings|spoof|ksud|symbol" } | Select-Object -First 1
if (-not $apk) { "no apk artifact; available:"; $a.artifacts | ForEach-Object { $_.name }; exit 1 }
"artifact=$($apk.name) size=$($apk.size_in_bytes)"

$tmp = Join-Path $env:TEMP "apk_fp.zip"
Invoke-WebRequest -Uri $apk.archive_download_url -Headers $h -OutFile $tmp -Proxy $null -TimeoutSec 180
$dir = Join-Path $env:TEMP "apk_fp"
if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($tmp, $dir)
Remove-Item $tmp -Force

$target = Get-ChildItem $dir -Recurse -File -Filter *.apk | Select-Object -First 1
if (-not $target) { "no apk inside artifact"; exit 1 }
"apk=$($target.Name)"

$signer = Get-ChildItem "E:\Android\Sdk\build-tools" -Directory | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName "apksigner.bat" } | Where-Object { Test-Path $_ } | Select-Object -First 1
"apksigner=$signer"
& $signer verify --print-certs $target.FullName 2>&1 | Select-String -Pattern "SHA-256|DN:|verifies|WARNING" | ForEach-Object { $_.Line.Trim() }
