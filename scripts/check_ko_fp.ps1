# 下载指定 run 的 kernelsu.ko，检查里面嵌的 manager 证书指纹
param(
    [string]$RunId,
    [string]$Kmi = "android14-6.1"
)

Add-Type -Namespace WK -Name C -MemberDefinition @'
[StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
public struct CREDENTIAL { public int Flags; public int Type; public string TargetName; public string Comment; public long LastWritten; public int CredentialBlobSize; public IntPtr CredentialBlob; public int Persist; public int AttributeCount; public IntPtr Attributes; public string TargetAlias; public string UserName; }
[DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] public static extern bool CredRead(string target, int type, int flags, out IntPtr cred);
[DllImport("advapi32.dll")] public static extern void CredFree(IntPtr cred);
'@

$p = [IntPtr]::Zero
if (-not [WK.C]::CredRead("GitHub - https://api.github.com/Whiteink666", 1, 0, [ref]$p)) { "no credential"; exit 1 }
$c = [System.Runtime.InteropServices.Marshal]::PtrToStructure($p, [type][WK.C+CREDENTIAL])
$b = New-Object byte[] $c.CredentialBlobSize
[System.Runtime.InteropServices.Marshal]::Copy($c.CredentialBlob, $b, 0, $c.CredentialBlobSize)
[WK.C]::CredFree($p)
$tok = [System.Text.Encoding]::UTF8.GetString($b)
$h = @{ Authorization = "Bearer $tok"; "User-Agent" = "ps" }

$a = Invoke-RestMethod -Uri "https://api.github.com/repos/Whiteink666/kobianyi/actions/runs/$RunId/artifacts?per_page=100" -Headers $h -Proxy $null -TimeoutSec 25
$ko = $a.artifacts | Where-Object { $_.name -eq "aarch64-$Kmi-lkm" }
if (-not $ko) { "artifact not found"; exit 1 }
"artifact=$($ko.name) size=$($ko.size_in_bytes)"

$tmp = Join-Path $env:TEMP "ko_fp.zip"
Invoke-WebRequest -Uri $ko.archive_download_url -Headers $h -OutFile $tmp -Proxy $null -TimeoutSec 120
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($tmp)
$out = Join-Path $env:TEMP "kernelsu_fp.ko"
[System.IO.Compression.ZipFileExtensions]::ExtractToFile($z.Entries[0], $out, $true)
$z.Dispose()
Remove-Item $tmp -Force

$s = [System.Text.Encoding]::ASCII.GetString([System.IO.File]::ReadAllBytes($out))
"ko size=$((Get-Item $out).Length)"
"Whiteink 87fc8bdc : $($s.Contains('87fc8bdc'))"
"old    0880c649   : $($s.Contains('0880c649'))"
